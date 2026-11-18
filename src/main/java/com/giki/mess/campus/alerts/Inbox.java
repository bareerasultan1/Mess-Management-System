package com.giki.mess.campus.alerts;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import com.giki.mess.campus.domain.Rules;

@Service
public class Inbox {
    public record Item(UUID id, String kind, String summary, OffsetDateTime delivered) {}
    private final JdbcClient db;
    public Inbox(JdbcClient db) { this.db=db; }
    public List<Item> forStudent(String principal, int page) {
        return db.sql("""
            select e.id,e.kind,e.summary,i.delivered from campus_inbox i join campus_event e on e.id=i.event_id
            where i.recipient=? order by i.delivered desc,i.event_id limit 20 offset ?
            """).params(principal,Rules.offset(page)).query(Item.class).list();
    }
}
