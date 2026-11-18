package com.giki.mess.repository.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.BillItem;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.repository.DatabaseConfig;
import com.giki.mess.repository.PersistenceException;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Properties only a real database has: durability, rollback, constraints, concurrent writes. */
@DisplayName("PostgreSQL persistence")
class PostgresPersistenceTest {

    private DatabaseConfig config;
    private final List<PostgresDatabase> opened = new ArrayList<>();

    @BeforeEach
    void setUp() {
        config = TestPostgres.freshDatabase();
    }

    @AfterEach
    void tearDown() {
        opened.forEach(PostgresDatabase::close);
    }

    /** Opens a connection pool to the current test database (call again to simulate a restart). */
    private PostgresDatabase connect() {
        PostgresDatabase db = PostgresDatabase.connect(config);
        db.initialize();
        opened.add(db);
        return db;
    }

    private long count(PostgresDatabase db, String table) throws Exception {
        return db.read(c -> {
            try (var st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    @Test
    @DisplayName("tables, keys and constraints from schema.sql exist")
    void schemaIsCreated() throws Exception {
        PostgresDatabase db = connect();

        List<String> tables = db.read(c -> {
            List<String> found = new ArrayList<>();
            try (var st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1")) {
                while (rs.next()) {
                    found.add(rs.getString(1));
                }
            }
            return found;
        });

        assertThat(tables).containsExactly("bill_items", "bills", "payment_transactions", "student_ledgers");
    }

    @Test
    @DisplayName("initialize() can run twice without error (safe at every start-up)")
    void initializeIsRepeatable() {
        PostgresDatabase db = connect();
        db.initialize();
        db.initialize();
    }

    @Test
    @DisplayName("data survives an application restart (new connections on the same database)")
    void survivesRestart() {
        PostgresLedgerRepository ledgers1 = new PostgresLedgerRepository(connect());
        StudentLedger l = new StudentLedger("2023294", StudentCategory.SCHOLAR);
        l.debitAmount(Money.pkr(750));
        ledgers1.save(l);

        PostgresLedgerRepository ledgers2 = new PostgresLedgerRepository(connect());
        StudentLedger back = ledgers2.findByStudentId("2023294").orElseThrow();

        assertThat(back).isNotSameAs(l);
        assertThat(back.getBalance()).isEqualTo(Money.pkr(750));
        assertThat(back.getCategory()).isEqualTo(StudentCategory.SCHOLAR);
    }

    @Test
    @DisplayName("a bill whose student has no ledger is rejected and leaves no partial rows")
    void failedBillSave_rollsBackEverything() throws Exception {
        PostgresDatabase db = connect();
        PostgresBillRepository bills = new PostgresBillRepository(db);
        BillItem item = new BillItem("E1", "B1", "2023999", LocalDateTime.of(2026, 11, 3, 13, 0),
            MealType.LUNCH, Money.pkr(150));
        Bill orphan = new Bill("B1", "2023999", 11, 2026, LocalDate.of(2026, 12, 10),
            Money.pkr(150), Money.ZERO, Money.ZERO, Money.ZERO, List.of(item));

        assertThatThrownBy(() -> bills.save(orphan)).isInstanceOf(RuntimeException.class);

        assertThat(count(db, "bills")).isZero();
        assertThat(count(db, "bill_items")).isZero();
    }

    @Test
    @DisplayName("the database itself refuses two bills for the same student and month")
    void uniqueBillPerStudentAndMonth() {
        PostgresDatabase db = connect();
        new PostgresLedgerRepository(db).save(new StudentLedger("2023294", StudentCategory.REGULAR));
        PostgresBillRepository bills = new PostgresBillRepository(db);
        bills.save(new Bill("B1", "2023294", 11, 2026, LocalDate.of(2026, 12, 10),
            Money.pkr(100), Money.ZERO, Money.ZERO, Money.ZERO, List.of()));

        assertThatThrownBy(() -> bills.save(new Bill("B-OTHER", "2023294", 11, 2026,
            LocalDate.of(2026, 12, 10), Money.pkr(100), Money.ZERO, Money.ZERO, Money.ZERO, List.of())))
            .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("the database refuses a negative balance even if the application tried to store one")
    void checkConstraintRefusesNegativeBalance() {
        PostgresDatabase db = connect();

        assertThatThrownBy(() -> db.inTransaction(c -> {
            try (var st = c.createStatement()) {
                st.execute("INSERT INTO student_ledgers VALUES ('2023294','REGULAR',-5,0,'ACTIVE',now())");
            }
            return null;
        })).isInstanceOf(java.sql.SQLException.class);
    }

    @Test
    @DisplayName("concurrent debits saved through the repository leave the exact total in the database")
    void concurrentSaves_noLostUpdateInDatabase() throws Exception {
        PostgresLedgerRepository repo = new PostgresLedgerRepository(connect());
        repo.save(new StudentLedger("2023294", StudentCategory.REGULAR));
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < 10; i++) {
                    StudentLedger l = repo.findByStudentId("2023294").orElseThrow();
                    l.debitAmount(Money.pkr(10));
                    repo.save(l);
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // read through a brand-new pool and repository to prove what is stored, not what is cached
        StudentLedger fromDisk = new PostgresLedgerRepository(connect())
            .findByStudentId("2023294").orElseThrow();
        assertThat(fromDisk.getBalance()).isEqualTo(Money.pkr(2_000));   // 20 * 10 * 10
        assertThat(fromDisk.getUnbilledMealCount()).isEqualTo(200);
    }

    @Test
    @DisplayName("an unreachable database gives a clear PersistenceException instead of hanging")
    void unreachableDatabase_failsFastWithClearMessage() {
        DatabaseConfig nowhere = new DatabaseConfig(
            "jdbc:postgresql://localhost:1/mess_db?connectTimeout=2", "nobody", "x");

        assertThatExceptionOfType(PersistenceException.class)
            .isThrownBy(() -> PostgresDatabase.connect(nowhere))
            .withMessageContaining("Could not connect to the database");
    }
}
