package com.giki.mess.billing.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.giki.mess.common.Money;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reads must use the same lock as writes, otherwise a thread can see a stale or half-updated
 * value (visibility problem). The test holds the ledger lock and proves every read waits.
 */
@DisplayName("StudentLedger: reads are lock-protected")
class StudentLedgerLockingTest {

    private static ReentrantLock lockOf(StudentLedger ledger) throws Exception {
        Field f = StudentLedger.class.getDeclaredField("balanceLock");
        f.setAccessible(true);
        return (ReentrantLock) f.get(ledger);
    }

    @Test
    @DisplayName("getBalance, getUnbilledMealCount, getStatus, isActive and snapshot wait for the lock")
    void reads_waitForLock() throws Exception {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        List<Callable<Object>> reads = List.of(
            ledger::getBalance,
            ledger::getUnbilledMealCount,
            ledger::getStatus,
            ledger::isActive,
            ledger::snapshot);
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            for (Callable<Object> read : reads) {
                ReentrantLock lock = lockOf(ledger);
                lock.lock();
                Future<Object> result = pool.submit(read);
                assertThatThrownBy(() -> result.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);   // blocked while the lock is held
                lock.unlock();
                result.get(2, TimeUnit.SECONDS);              // completes once released
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("snapshot returns balance, meal count and status from one moment")
    void snapshot_isConsistent() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(150));
        ledger.debitAmount(Money.pkr(100));

        LedgerSnapshot s = ledger.snapshot();

        assertThat(s.balance()).isEqualTo(Money.pkr(250));
        assertThat(s.unbilledMealCount()).isEqualTo(2);
        assertThat(s.status()).isEqualTo(StudentLedger.AccountStatus.ACTIVE);
    }
}
