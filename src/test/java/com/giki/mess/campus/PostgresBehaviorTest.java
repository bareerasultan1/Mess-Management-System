package com.giki.mess.campus;

import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import com.giki.mess.campus.alerts.*;
import com.giki.mess.campus.contract.*;
import com.giki.mess.campus.domain.Conflict;
import com.giki.mess.campus.feedback.Feedback;
import com.giki.mess.campus.menu.Menus;
import static org.assertj.core.api.Assertions.*;

class PostgresBehaviorTest extends PostgresSuite {
    @Autowired Menus menus;
    @Autowired Feedback feedback;
    @Autowired Delivery delivery;
    @Autowired EventPublisher events;
    @Autowired BillReminders bills;
    @Autowired TransactionTemplate tx;
    private final LocalDate day=LocalDate.of(2026,11,18);

    private static <T> List<T> race(IntFunction<T> action) throws Exception {
        var ready=new CyclicBarrier(2);
        var pool=Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon(true).factory());
        try {
            List<Future<T>> results=new ArrayList<>();
            for(int i=0;i<2;i++) {
                int index=i;
                results.add(pool.submit(()->{ready.await(5,TimeUnit.SECONDS);return action.apply(index);}));
            }
            return List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5,TimeUnit.SECONDS)) throw new AssertionError("Race workers did not terminate");
        }
    }
    private int count(String sql) { return db.sql(sql).query(Integer.class).single(); }
    @Test void twoEditorsCannotLoseChanges() throws Exception {
        var id=menus.create(day,"DINNER","Daal",false);
        assertThat(race(i->{
            try {menus.edit(id,0,"Recipe "+i,false);return true;}
            catch(Conflict e){return false;}
        })).containsExactlyInAnyOrder(true,false);
        assertThat(menus.list(day,"DINNER",true,0).getFirst().version()).isEqualTo(1);
        assertThat(count("select count(*) from campus_event")).isZero();
    }
    @Test void waitingEditorRechecksVersionAfterTheOwnerCommits() throws Exception {
        var id=menus.create(day,"DINNER","Original",false);
        menus.publish(id,0);
        var locked=new CompletableFuture<Integer>();
        var waiter=new CompletableFuture<Integer>();

        var outcomes=race(i->{
            if (i==0) {
                return tx.execute(s->{
                    menus.edit(id,1,"Winning edit",false);
                    int holder=count("select pg_backend_pid()");
                    locked.complete(holder);
                    int waitingPid=waiter.orTimeout(5,TimeUnit.SECONDS).join();
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    do {
                        if (db.sql("select ? = any(pg_blocking_pids(?))").params(holder,waitingPid)
                                .query(Boolean.class).single()) return "holder committed";
                    } while (System.nanoTime()<deadline);
                    throw new AssertionError("PostgreSQL never reported the expected blocking dependency");
                });
            }
            locked.orTimeout(5,TimeUnit.SECONDS).join();
            try {
                tx.executeWithoutResult(s->{
                    assertThat(menus.list(day,"DINNER",false,0).getFirst().dishes()).isEqualTo("Original");
                    waiter.complete(count("select pg_backend_pid()"));
                    menus.edit(id,1,"Outdated edit",false);
                });
                return "stale edit accepted";
            } catch (Conflict stale) { return "stale edit rejected"; }
        });

        assertThat(outcomes).containsExactlyInAnyOrder("holder committed","stale edit rejected");
        var saved=menus.list(day,"DINNER",false,0).getFirst();
        assertThat(saved.dishes()).isEqualTo("Winning edit");
        assertThat(saved.version()).isEqualTo(2);
        assertThat(count("select count(*) from campus_event")).isEqualTo(2);
    }

    @Test void deliberateOppositeLockOrderDetectsOneDeadlockVictim() throws Exception {
        var one=menus.create(day,"BREAKFAST","Fixture A",false);
        var two=menus.create(day,"LUNCH","Fixture B",false);
        var firstLocks=new CyclicBarrier(2);
        var results=race(i->{
            try {
                tx.executeWithoutResult(s->{
                    db.sql("set local statement_timeout='8s'").update();
                    lockFixtureMenu(i==0 ? one : two);
                    try { firstLocks.await(5,TimeUnit.SECONDS); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                    catch (BrokenBarrierException | TimeoutException ex) { throw new AssertionError(ex); }
                    lockFixtureMenu(i==0 ? two : one);
                });
                return "committed";
            } catch (DataAccessException ex) {
                for (Throwable cause=ex;cause!=null;cause=cause.getCause()) {
                    if (cause instanceof SQLException sql && "40P01".equals(sql.getSQLState())) return "40P01";
                }
                throw ex;
            }
        });
        assertThat(results).containsExactlyInAnyOrder("committed","40P01");
        tx.executeWithoutResult(s->{
            assertThat(db.sql("select id from campus_menu where id in (?,?) order by id for update nowait")
                .params(one,two).query(UUID.class).list()).hasSize(2);
        });
    }
    private void lockFixtureMenu(UUID id) {
        db.sql("select id from campus_menu where id=? for update").param(id).query(UUID.class).single();
    }
    private void drainTestFanout() {
        // 262 recipients need three successful page claims. Fail if progress stops.
        for (int i=0;i<4;i++) if (!delivery.runOne()) return;
        throw new AssertionError("The finite recipient fixture did not drain within four polls");
    }

    @Test void rollbackLeavesDraftAndNoEvent() {
        var id=menus.create(day,"LUNCH","Daal",false);
        tx.executeWithoutResult(t->{menus.publish(id,0);t.setRollbackOnly();});
        assertThat(menus.list(day,"LUNCH",false,0)).isEmpty();
        assertThat(delivery.runOne()).isFalse();
    }
    @Test void twoWorkersFanoutAcrossPagesAndReplay() throws Exception {
        db.sql("insert into campus_student(id,enrolled_at) select 'batch-'||n,'-infinity' from generate_series(1,260) n").update();
        var id=menus.create(day,"LUNCH","Fruit",true);
        menus.publish(id,0);
        race(i->{drainTestFanout();return i;});
        assertThat(count("select count(*) from campus_inbox")).isEqualTo(262);
        assertThat(db.sql("select kind from campus_event").query(String.class).single()).isEqualTo("SPECIAL_MEAL");
        db.sql("update campus_event set cursor_id=null,state='READY'").update();
        race(i->{drainTestFanout();return i;});
        assertThat(count("select count(*) from campus_inbox")).isEqualTo(262);
    }
    @Test void retryAfterMissingRecipientAndPoisonLimit() {
        var first=BillingAdapter.translate(new BillingAdapter.Notice(UUID.randomUUID(),clock.instant(),"late","BILL_ISSUED"));
        events.publish(first);events.publish(first);
        assertThat(delivery.runOne()).isTrue();
        assertThat(delivery.runOne()).isFalse();
        assertThat(count("select attempts from campus_event")).isOne();
        db.sql("insert into campus_student values ('late')").update();
        clock.advance(Duration.ofSeconds(1));
        assertThat(delivery.runOne()).isTrue();
        assertThat(count("select count(*) from campus_inbox")).isOne();
        var poison=BillingAdapter.translate(new BillingAdapter.Notice(UUID.randomUUID(),clock.instant(),"absent","BILL_ISSUED"));
        events.publish(poison);
        for(int i=0;i<5;i++){assertThat(delivery.runOne()).isTrue();clock.advance(Duration.ofSeconds(16));}
        assertThat(delivery.runOne()).isFalse();
        assertThat(count("select count(*) from campus_event where attempts=5 and state='DEAD'")).isOne();
        assertThatThrownBy(()->events.publish(new AlertEvent(first.id(),first.occurred(),first.kind(),"student","Different")))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundaryAndOverlappingEscalationJobs() throws Exception {
        UUID open=feedback.submit("COMPLAINT","Private fixture");
        UUID closed=feedback.submit("COMPLAINT","Another fixture");
        UUID resolved=feedback.submit("COMPLAINT","Other fixture");
        UUID note=feedback.submit("FEEDBACK","Fixture");
        for(var id:List.of(closed,resolved)){feedback.move(id,"OPEN","IN_REVIEW");feedback.move(id,"IN_REVIEW","RESOLVED");}
        feedback.move(closed,"RESOLVED","CLOSED");
        assertThatThrownBy(()->feedback.move(open,"OPEN","CLOSED")).isInstanceOf(IllegalArgumentException.class);
        clock.advance(Duration.ofHours(48).minusMillis(1));
        assertThat(feedback.escalate()).isZero();
        clock.advance(Duration.ofMillis(1));
        assertThat(race(i->feedback.escalate())).containsExactlyInAnyOrder(0,1);
        assertThat(feedback.escalate()).isZero();
        assertThat(feedback.list(true,0)).extracting(Feedback.StaffCase::ref).containsExactly(open);
        for(var id:List.of(closed,resolved,note)) assertThat(feedback.track(id).escalated()).isFalse();
    }
    @Test void dueAndOverdueOnceWithSettlement() {
        var e=BillingAdapter.translate(new BillingAdapter.Notice(UUID.randomUUID(),clock.instant(),"student","BILL_ISSUED"));
        bills.issued(e,"bill-A",day.plusDays(1));bills.issued(e,"bill-A",day.plusDays(1));
        assertThat(bills.tick()).isZero();
        clock.advance(Duration.ofDays(1));
        assertThat(bills.tick()).isOne();assertThat(bills.tick()).isZero();
        clock.advance(Duration.ofDays(1));
        assertThat(bills.tick()).isOne();assertThat(bills.tick()).isZero();
        assertThat(db.sql("select kind from campus_event").query(String.class).list()).containsExactlyInAnyOrder("BILL","DUE","OVERDUE");
        bills.settled("bill-B");
        var other=BillingAdapter.translate(new BillingAdapter.Notice(UUID.randomUUID(),clock.instant(),"student","BILL_ISSUED"));
        bills.issued(other,"bill-B",day);
        assertThat(bills.tick()).isZero();
    }
}
