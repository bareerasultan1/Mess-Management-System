package com.giki.mess.campus.persistence;

import com.giki.mess.campus.feedback.Feedback.Progress;
import com.giki.mess.campus.feedback.Feedback.StaffCase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRepository {
    private final JdbcClient db;
    public CaseRepository(JdbcClient db) { this.db = db; }

    public void insert(UUID ref, String kind, String text, Instant created) {
        db.sql("insert into campus_case(ref,kind,body,created) values (?,?,?,?)")
            .params(ref,kind,text,Timestamp.from(created)).update();
    }
    public Optional<Progress> progress(UUID ref) {
        return db.sql("select status, escalated is not null as escalated, created, resolved from campus_case where ref=?")
            .param(ref).query(Progress.class).optional();
    }
    public List<StaffCase> list(boolean escalated, int offset) {
        String filter = escalated ? " where escalated is not null " : " ";
        return db.sql("select ref,kind,body,status,created,escalated from campus_case" + filter +
            "order by created desc, ref limit 20 offset ?").param(offset).query(StaffCase.class).list();
    }
    public boolean move(UUID ref, String from, String to, Instant at) {
        return db.sql(Queries.MOVE_CASE).params(to,to,Timestamp.from(at),ref,from).update() == 1;
    }
    public int escalate(Instant cutoff, Instant now) {
        return db.sql(Queries.ESCALATE_CASES).params(Timestamp.from(cutoff),Timestamp.from(now)).update();
    }
    public boolean rate(UUID menu, int score) {
        return db.sql(Queries.RATE_MEAL).params(UUID.randomUUID(),score,menu).update() == 1;
    }
}
