package com.giki.mess.campus.persistence;

import java.sql.Timestamp;
import com.giki.mess.campus.contract.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresOutbox implements EventPublisher {
    private final JdbcClient db;
    public PostgresOutbox(JdbcClient db) { this.db = db; }

    @Transactional
    public void publish(AlertEvent e) {
        var time = Timestamp.from(e.occurred().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        int written = db.sql(Queries.INSERT_EVENT).param(e.id()).param(time).param(e.kind().name()).param(e.recipient()).param(e.summary()).param(time).update();
        if (written == 0 && !db.sql("""
            select exists(select 1 from campus_event where id=? and occurred=? and kind=?
              and recipient is not distinct from cast(? as varchar) and summary=?)
            """).param(e.id()).param(time).param(e.kind().name()).param(e.recipient()).param(e.summary()).query(Boolean.class).single())
            throw new IllegalArgumentException("Event ID already belongs to another payload.");
    }
}
