package com.giki.mess.repository.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("In-memory repositories")
class InMemoryRepositoriesTest {

    @Test
    @DisplayName("ledger save then findByStudentId returns the same ledger")
    void ledger_saveAndFind() {
        InMemoryLedgerRepository repo = new InMemoryLedgerRepository();
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        repo.save(ledger);

        assertThat(repo.findByStudentId("2023294")).containsSame(ledger);
        assertThat(repo.findByStudentId("2023999")).isEmpty();
        assertThat(repo.findAll()).hasSize(1);
        repo.deleteById("2023294");
        assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("bill lookup by student+month and overdue query work")
    void bill_queries() {
        InMemoryBillRepository repo = new InMemoryBillRepository();
        Bill old = new Bill("B1", "2023294", 1, 2026, LocalDate.of(2026, 2, 10),
            Money.pkr(100), Money.ZERO, Money.ZERO, Money.ZERO, List.of());
        Bill later = new Bill("B2", "2023294", 2, 2026, LocalDate.of(2999, 3, 10),
            Money.pkr(100), Money.ZERO, Money.ZERO, Money.ZERO, List.of());
        repo.save(old);
        repo.save(later);

        assertThat(repo.findByStudentAndMonth("2023294", 1, 2026)).containsSame(old);
        assertThat(repo.findOverdueBills()).containsExactly(old);
        assertThat(repo.findByStudentId("2023294")).containsExactly(later, old);
        assertThat(repo.findById("B2")).containsSame(later);
        repo.deleteById("B1");
        assertThat(repo.findAll()).containsExactly(later);
    }
}
