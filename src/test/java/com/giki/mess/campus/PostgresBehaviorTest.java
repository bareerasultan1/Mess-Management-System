package com.giki.mess.campus;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<T>> results=new ArrayList<>();
            for(int i=0;i<2;i++) {
                int index=i;
                results.add(pool.submit(()->{ready.await(5,TimeUnit.SECONDS);return action.apply(index);}));
            }
            return List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS));
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
        race(i->{while(delivery.runOne()) {} return i;});
        assertThat(count("select count(*) from campus_inbox")).isEqualTo(262);
        assertThat(db.sql("select kind from campus_event").query(String.class).single()).isEqualTo("SPECIAL_MEAL");
        db.sql("update campus_event set cursor_id=null,state='READY'").update();
        race(i->{while(delivery.runOne()) {} return i;});
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
