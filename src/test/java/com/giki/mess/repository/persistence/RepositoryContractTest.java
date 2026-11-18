package com.giki.mess.repository.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.BillItem;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.payment.exception.DuplicateTransactionException;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.payment.model.TransactionStatus;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import com.giki.mess.repository.IPaymentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One behavioural contract that EVERY repository implementation must satisfy
 * (Liskov Substitution): the in-memory test doubles and the PostgreSQL repositories run the
 * identical tests, so services behave the same on either.
 */
abstract class RepositoryContractTest {

    protected ILedgerRepository ledgers;
    protected IBillRepository bills;
    protected IPaymentRepository payments;

    protected abstract void createRepositories() throws Exception;

    /** Releases connections after each test (nothing to do for the in-memory version). */
    protected void closeRepositories() throws Exception {
    }

    @AfterEach
    void tearDownRepositories() throws Exception {
        closeRepositories();
    }

    @BeforeEach
    void setUpRepositories() throws Exception {
        createRepositories();
        ledgers.save(new StudentLedger("2023294", StudentCategory.REGULAR));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    protected Bill bill(String id, int month, int year, LocalDate due) {
        BillItem lunch = new BillItem("E-" + id + "-1", id, "2023294",
            LocalDateTime.of(year, month, 3, 13, 0), MealType.LUNCH, Money.pkr(150));
        BillItem dinner = new BillItem("E-" + id + "-2", id, "2023294",
            LocalDateTime.of(year, month, 3, 20, 0), MealType.DINNER, Money.pkr(150));
        return new Bill(id, "2023294", month, year, due, Money.pkr(300), Money.pkr(50),
            Money.pkr(10), Money.pkr(20), List.of(lunch, dinner));
    }

    protected PaymentTransaction tx(String id, String billId, String key) {
        return new PaymentTransaction(id, "2023294", billId, Money.pkr(100),
            PaymentChannel.JAZZCASH, key);
    }

    // ── ledger ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ledger: saved state (balance, meal count, status) is read back")
    void ledger_roundTrip() {
        StudentLedger l = ledgers.findByStudentId("2023294").orElseThrow();
        l.debitAmount(Money.pkr(11_000));
        l.suspendIfOverdue();
        ledgers.save(l);

        StudentLedger back = ledgers.findByStudentId("2023294").orElseThrow();
        assertThat(back.getBalance()).isEqualTo(Money.pkr(11_000));
        assertThat(back.getUnbilledMealCount()).isEqualTo(1);
        assertThat(back.getStatus()).isEqualTo(StudentLedger.AccountStatus.SUSPENDED);
        assertThat(back.getCategory()).isEqualTo(StudentCategory.REGULAR);
    }

    @Test
    @DisplayName("ledger: unknown id is empty, findAll lists, deleteById removes")
    void ledger_findAndDelete() {
        assertThat(ledgers.findByStudentId("2023999")).isEmpty();
        ledgers.save(new StudentLedger("2023111", StudentCategory.SCHOLAR));
        assertThat(ledgers.findAll()).hasSize(2);

        ledgers.deleteById("2023111");

        assertThat(ledgers.findById("2023111")).isEmpty();
        assertThat(ledgers.findAll()).hasSize(1);
    }

    // ── bill ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bill: amounts, status and itemised lines are read back")
    void bill_roundTrip() {
        Bill b = bill("B1", 11, 2026, LocalDate.of(2026, 12, 10));
        b.applyPartialPayment(Money.pkr(100));
        bills.save(b);

        Bill back = bills.findById("B1").orElseThrow();
        assertThat(back.getBaseAmount()).isEqualTo(Money.pkr(300));
        assertThat(back.getRebateAmount()).isEqualTo(Money.pkr(50));
        assertThat(back.getFineAmount()).isEqualTo(Money.pkr(10));
        assertThat(back.getDiscountAmount()).isEqualTo(Money.pkr(20));
        assertThat(back.getNetPayable()).isEqualTo(Money.pkr(140));   // 300-50+10-20 = 240, minus 100 paid
        assertThat(back.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(back.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(back.getItems()).hasSize(2);
        assertThat(back.getItems()).extracting(BillItem::getMealType)
            .containsExactlyInAnyOrder(MealType.LUNCH, MealType.DINNER);
    }

    @Test
    @DisplayName("bill: saving again after a payment updates the stored status and balance")
    void bill_resaveUpdates() {
        Bill b = bill("B1", 11, 2026, LocalDate.of(2026, 12, 10));
        bills.save(b);
        b.markAsPaid();
        bills.save(b);

        Bill back = bills.findById("B1").orElseThrow();
        assertThat(back.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(back.getNetPayable()).isEqualTo(Money.ZERO);
        assertThat(bills.findAll()).hasSize(1);
        assertThat(back.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("bill: lookup by student and month, student history newest first")
    void bill_queries() {
        bills.save(bill("B-OLD", 1, 2026, LocalDate.of(2026, 2, 10)));
        bills.save(bill("B-NEW", 2, 2026, LocalDate.of(2026, 3, 10)));

        assertThat(bills.findByStudentAndMonth("2023294", 2, 2026)).isPresent();
        assertThat(bills.findByStudentAndMonth("2023294", 3, 2026)).isEmpty();
        assertThat(bills.findByStudentId("2023294")).extracting(Bill::getBillId)
            .containsExactly("B-NEW", "B-OLD");
    }

    @Test
    @DisplayName("bill: overdue query returns unpaid bills past their due date only")
    void bill_overdueQuery() {
        Bill past = bill("B-PAST", 1, 2021, LocalDate.of(2021, 2, 10));
        Bill future = bill("B-FUTURE", 2, 2021, LocalDate.of(2999, 3, 10));
        Bill paidPast = bill("B-PAID", 3, 2021, LocalDate.of(2021, 4, 10));
        paidPast.markAsPaid();
        bills.save(past);
        bills.save(future);
        bills.save(paidPast);

        assertThat(bills.findOverdueBills()).extracting(Bill::getBillId).containsExactly("B-PAST");
    }

    @Test
    @DisplayName("bill: deleteById removes the bill")
    void bill_delete() {
        bills.save(bill("B1", 11, 2026, LocalDate.of(2026, 12, 10)));
        bills.deleteById("B1");
        assertThat(bills.findById("B1")).isEmpty();
    }

    // ── payment transactions ──────────────────────────────────────────────────

    @Test
    @DisplayName("payment: every field and status transition is read back")
    void payment_roundTrip() {
        bills.save(bill("B1", 11, 2026, LocalDate.of(2026, 12, 10)));
        PaymentTransaction t = tx("TX-1", "B1", "KEY-1");
        payments.save(t);
        t.markSuccess();
        t.markReconciled();
        payments.save(t);

        PaymentTransaction back = payments.findById("TX-1").orElseThrow();
        assertThat(back.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
        assertThat(back.getAmount()).isEqualTo(Money.pkr(100));
        assertThat(back.getChannel()).isEqualTo(PaymentChannel.JAZZCASH);
        assertThat(back.getIdempotencyKey()).isEqualTo("KEY-1");
        assertThat(back.getCreatedAt()).isNotNull();
        assertThat(payments.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("payment: find by idempotency key and by bill")
    void payment_queries() {
        bills.save(bill("B1", 11, 2026, LocalDate.of(2026, 12, 10)));
        bills.save(bill("B2", 12, 2026, LocalDate.of(2027, 1, 10)));
        payments.save(tx("TX-1", "B1", "KEY-1"));
        payments.save(tx("TX-2", "B1", "KEY-2"));
        payments.save(tx("TX-3", "B2", "KEY-3"));

        assertThat(payments.findByIdempotencyKey("KEY-2")).isPresent();
        assertThat(payments.findByIdempotencyKey("NOPE")).isEmpty();
        assertThat(payments.findByBillId("B1")).hasSize(2);
        payments.deleteById("TX-3");
        assertThat(payments.findById("TX-3")).isEmpty();
    }

    @Test
    @DisplayName("payment: a second transaction with the same idempotency key is refused")
    void payment_duplicateKeyRefused() {
        bills.save(bill("B1", 11, 2026, LocalDate.of(2026, 12, 10)));
        payments.save(tx("TX-1", "B1", "SAME-KEY"));

        assertThatExceptionOfType(DuplicateTransactionException.class)
            .isThrownBy(() -> payments.save(tx("TX-2", "B1", "SAME-KEY")));
        assertThat(payments.findAll()).hasSize(1);
    }
}
