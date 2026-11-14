package com.giki.mess.payment.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The atomic gate against duplicate settlements (risk R-02, QA-CONC-01). */
@DisplayName("IdempotencyManager")
class IdempotencyManagerTest {

    private static TransactionRecord record(String tx) {
        return new TransactionRecord(tx, Instant.parse("2026-11-20T10:00:00Z"));
    }

    @Test
    @DisplayName("the first request for a key wins, the second is refused")
    void firstWins_secondRefused() {
        IdempotencyManager m = new IdempotencyManager();

        assertThat(m.tryAcquire("K1", record("TX-1"))).isTrue();
        assertThat(m.tryAcquire("K1", record("TX-2"))).isFalse();
        assertThat(m.find("K1")).contains(record("TX-1"));
    }

    @Test
    @DisplayName("different keys do not interfere")
    void differentKeys_independent() {
        IdempotencyManager m = new IdempotencyManager();

        assertThat(m.tryAcquire("K1", record("TX-1"))).isTrue();
        assertThat(m.tryAcquire("K2", record("TX-2"))).isTrue();
        assertThat(m.find("K3")).isEmpty();
    }

    @Test
    @DisplayName("releasing the winning record lets the key be used again (failed attempt)")
    void release_allowsRetry() {
        IdempotencyManager m = new IdempotencyManager();
        TransactionRecord first = record("TX-1");
        m.tryAcquire("K1", first);

        m.release("K1", first);

        assertThat(m.tryAcquire("K1", record("TX-2"))).isTrue();
    }

    @Test
    @DisplayName("a loser cannot release the winner's key")
    void release_byLoser_isIgnored() {
        IdempotencyManager m = new IdempotencyManager();
        m.tryAcquire("K1", record("TX-1"));

        m.release("K1", record("TX-2"));

        assertThat(m.find("K1")).contains(record("TX-1"));
    }

    @Test
    @DisplayName("null or blank key and null record are rejected")
    void invalidInput_rejected() {
        IdempotencyManager m = new IdempotencyManager();

        assertThatIllegalArgumentException().isThrownBy(() -> m.tryAcquire(null, record("T")));
        assertThatIllegalArgumentException().isThrownBy(() -> m.tryAcquire(" ", record("T")));
        assertThatIllegalArgumentException().isThrownBy(() -> m.tryAcquire("K", null));
    }

    @Test
    @DisplayName("200 threads race for one key: exactly one wins")
    void race_exactlyOneWinner() throws Exception {
        IdempotencyManager m = new IdempotencyManager();
        int threads = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String tx = "TX-" + i;
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return m.tryAcquire("SAME-KEY", record(tx));
            }));
        }
        ready.await();
        go.countDown();
        int winners = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                winners++;
            }
        }
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
    }
}
