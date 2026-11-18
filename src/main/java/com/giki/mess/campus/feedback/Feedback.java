package com.giki.mess.campus.feedback;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.giki.mess.campus.domain.*;

@Service
public class Feedback {
    public record Progress(String status, boolean escalated, OffsetDateTime created, OffsetDateTime resolved) {}
    public record StaffCase(UUID ref, String kind, String body, String status, OffsetDateTime created, OffsetDateTime escalated) {}
    private final JdbcClient db;
    private final Clock clock;
    private final Duration threshold;
    public Feedback(JdbcClient db, Clock clock, @Value("${mess.escalation}") Duration threshold) {
        if (threshold.isZero() || threshold.isNegative()) throw new IllegalArgumentException("Threshold must be positive.");
        this.db=db; this.clock=clock; this.threshold=threshold;
    }
    public UUID submit(String kind, String body) {
        if (!"COMPLAINT".equals(kind) && !"FEEDBACK".equals(kind)) throw new IllegalArgumentException("Choose a submission type.");
        UUID ref = UUID.randomUUID();
        db.sql("insert into campus_case(ref,kind,body,created) values (?,?,?,?)")
            .params(ref,kind,Rules.text(body,2000),Timestamp.from(clock.instant())).update();
        return ref;
    }
    public Progress track(UUID ref) {
        return db.sql("select status, escalated is not null as escalated, created, resolved from campus_case where ref=?")
            .param(ref).query(Progress.class).optional().orElseThrow(NoSuchElementException::new);
    }
    public List<StaffCase> list(boolean escalated, int page) {
        String filter = escalated ? " where escalated is not null " : " ";
        return db.sql("select ref,kind,body,status,created,escalated from campus_case" + filter +
            "order by created desc, ref limit 20 offset ?").param(Rules.offset(page)).query(StaffCase.class).list();
    }
    public void move(UUID ref, String from, String to) {
        if (from == null || !Rules.transition(from,to)) throw new IllegalArgumentException("Invalid status transition.");
        int n = db.sql("""
            update campus_case set status=?,resolved=case when ?='RESOLVED' then ? else resolved end
            where ref=? and status=?
            """).params(to,to,Timestamp.from(clock.instant()),ref,from).update();
        if (n == 0) throw new Conflict();
    }
    @Transactional
    public int escalate() {
        var now = clock.instant();
        return db.sql("""
            with eligible as (
                select ref from campus_case where kind='COMPLAINT' and escalated is null
                  and status in ('OPEN','IN_REVIEW') and created<=?
                order by created,ref limit 64 for update skip locked
            )
            update campus_case c set escalated=? from eligible where c.ref=eligible.ref
            """).params(Timestamp.from(now.minus(threshold)),Timestamp.from(now)).update();
    }
    public void rate(UUID menu, int value) {
        int n = db.sql("""
            insert into campus_rating(id,menu_id,score) select ?,id,? from campus_menu where id=? and published
            """).params(UUID.randomUUID(),Rules.rating(value),menu).update();
        if (n == 0) throw new IllegalArgumentException("Choose a published meal.");
    }
}
