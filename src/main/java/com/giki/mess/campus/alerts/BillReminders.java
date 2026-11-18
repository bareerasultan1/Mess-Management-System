package com.giki.mess.campus.alerts;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import com.giki.mess.campus.contract.*;
import com.giki.mess.campus.domain.Rules;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local subscription to issued/settled facts. No access to the billing ledger. */
@Service
public class BillReminders {
    private final JdbcClient db;
    private final Clock clock;
    private final EventPublisher events;
    public BillReminders(JdbcClient db, Clock clock, EventPublisher events) {
        this.db=db; this.clock=clock; this.events=events;
    }
    @Transactional
    public void issued(AlertEvent issued, String billId, LocalDate due) {
        if (issued.kind()!=AlertEvent.Kind.BILL || due == null) throw new IllegalArgumentException("Expected an issued bill and due date.");
        events.publish(issued);
        // A settlement tombstone wins even when it arrived before the issue.
        db.sql("""
            insert into campus_reminder(bill_id,recipient,due_day,next_at) values(?,?,?,?)
            on conflict (bill_id) do nothing
            """).params(Rules.text(billId,100),issued.recipient(),due,Timestamp.from(due.atStartOfDay(clock.getZone()).toInstant())).update();
    }
    public void settled(String billId) {
        db.sql("""
            insert into campus_reminder(bill_id,next_at) values(?,null)
            on conflict (bill_id) do update set next_at=null
            """).param(Rules.text(billId,100)).update();
    }
    private record Reminder(String billId,String recipient,LocalDate dueDay) {}
    @Transactional
    public int tick() {
        Clock tick = Clock.fixed(clock.instant(),clock.getZone());
        var rows=db.sql("""
            select bill_id,recipient,due_day from campus_reminder where next_at<=?
            order by next_at,bill_id limit 32 for update skip locked
            """).param(Timestamp.from(tick.instant())).query(Reminder.class).list();
        for (var row : rows) {
            var kind=Rules.dueKind(row.dueDay(),tick).orElseThrow();
            boolean due=kind==AlertEvent.Kind.DUE;
            events.publish(new AlertEvent(UUID.randomUUID(),tick.instant(),kind,row.recipient(),
                due ? "Your bill is due today." : "Your bill is overdue."));
            Timestamp next=due ? Timestamp.from(row.dueDay().plusDays(1).atStartOfDay(tick.getZone()).toInstant()) : null;
            db.sql("update campus_reminder set next_at=? where bill_id=?").param(next).param(row.billId()).update();
        }
        return rows.size();
    }
}
