package com.giki.mess.campus.persistence;

import com.giki.mess.campus.contract.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresInbox implements AlertSubscriber {
    private final JdbcClient db;
    private final Clock clock;
    public PostgresInbox(JdbcClient db, Clock clock) { this.db = db; this.clock = clock; }

    public String deliver(AlertEvent e, String after) {
        var now = Timestamp.from(clock.instant());
        if (e.recipient() != null) {
            db.sql(Queries.DELIVER_TARGET)
                .params(e.id(), e.recipient(), now).update();
            return null;
        }
        // Keep cursor movement independent of how many inserts survived replay.
        List<String> page = db.sql(Queries.DELIVER_PAGE).params(after == null ? "" : after, Timestamp.from(e.occurred()), e.id(), now).query(String.class).list();
        return page.size() == 128 ? page.getLast() : null;
    }
}
