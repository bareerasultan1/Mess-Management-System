package com.giki.mess.campus.alerts;

import com.giki.mess.campus.persistence.Queries;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import com.giki.mess.campus.contract.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Delivery {
    private record Claim(AlertEvent event, String cursor, int attempts) {}
    private static final Logger LOG = LoggerFactory.getLogger(Delivery.class);
    private final JdbcClient db;
    private final AlertSubscriber inbox;
    private final Clock clock;
    private final TransactionTemplate outer, savepoint;
    public Delivery(JdbcClient db, AlertSubscriber inbox, Clock clock, PlatformTransactionManager manager) {
        this.db = db; this.inbox = inbox; this.clock = clock;
        outer = new TransactionTemplate(manager);
        savepoint = new TransactionTemplate(manager);
        savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
    public boolean runOne() {
        return Boolean.TRUE.equals(outer.execute(tx -> {
            var found = db.sql(Queries.CLAIM_EVENT).param(Timestamp.from(clock.instant())).query((r,n) -> new Claim(
                    new AlertEvent(r.getObject("id",UUID.class), r.getTimestamp("occurred").toInstant(),
                        AlertEvent.Kind.valueOf(r.getString("kind")),r.getString("recipient"),r.getString("summary")),
                    r.getString("cursor_id"),r.getInt("attempts"))).optional();
            if (found.isEmpty()) return false;
            Claim c = found.get();
            String cursor;
            try {
                cursor = savepoint.execute(tx2 -> inbox.deliver(c.event(),c.cursor()));
            } catch (RuntimeException failure) {
                int count = c.attempts()+1;
                String state = count >= 5 ? "DEAD" : "READY";
                db.sql("update campus_event set attempts=?,state=?,available=? where id=?")
                    .params(count,state,Timestamp.from(clock.instant().plusSeconds(1L << (count-1))),c.event().id()).update();
                LOG.warn("Delivery {} attempt {} state {}",c.event().id(),count,state);
                return true;
            }
            db.sql("update campus_event set cursor_id=?,state=?,attempts=0 where id=?")
                .param(cursor).param(cursor == null ? "DONE" : "READY").param(c.event().id()).update();
            return true;
        }));
    }
}
