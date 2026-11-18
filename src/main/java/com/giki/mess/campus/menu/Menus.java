package com.giki.mess.campus.menu;

import com.giki.mess.campus.persistence.Queries;
import com.giki.mess.campus.contract.*;
import com.giki.mess.campus.domain.Rules;
import com.giki.mess.campus.domain.Conflict;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Menus {
    public record Menu(UUID id, LocalDate day, String meal, String dishes, boolean special, boolean published, long version) {}
    private final JdbcClient db;
    private final EventPublisher events;
    private final Clock clock;

    public Menus(JdbcClient db, EventPublisher events, Clock clock) {
        this.db = db; this.events = events; this.clock = clock;
    }
    @Transactional
    public UUID create(LocalDate day, String meal, String dishes, boolean special) {
        if (day == null) throw new IllegalArgumentException("Choose a date.");
        UUID id = UUID.randomUUID();
        db.sql("insert into campus_menu(id,day,meal,dishes,special) values (?,?,?,?,?)")
            .params(id, day, Rules.meal(meal), Rules.text(dishes,500), special).update();
        return id;
    }
    @Transactional
    public void edit(UUID id, long version, String dishes, boolean special) {
        Menu saved = db.sql(Queries.EDIT_MENU).params(Rules.text(dishes,500), special, id, version).query(Menu.class).optional().orElseThrow(Conflict::new);
        if (saved.published()) announce(saved);
    }
    @Transactional
    public UUID publish(UUID id, long version) {
        Menu saved = db.sql(Queries.PUBLISH_MENU).params(id,version).query(Menu.class).optional().orElseThrow(Conflict::new);
        return announce(saved);
    }
    private UUID announce(Menu menu) {
        UUID id = UUID.randomUUID();
        var kind = menu.special() ? AlertEvent.Kind.SPECIAL_MEAL : AlertEvent.Kind.MENU;
        events.publish(new AlertEvent(id, clock.instant(), kind, null, menu.day() + " " + menu.meal() + " menu updated."));
        return id;
    }
    public List<Menu> list(LocalDate day, String meal, boolean staff, int page) {
        var query = new StringBuilder("select * from campus_menu where true");
        if (!staff) query.append(" and published");
        if (day != null) query.append(" and day=:day");
        if (meal != null) query.append(" and meal=:meal");
        var sql = db.sql(query + " order by day desc,meal limit 20 offset :offset").param("offset", Rules.offset(page));
        if (day != null) sql = sql.param("day",day);
        if (meal != null) sql = sql.param("meal",Rules.meal(meal));
        return sql.query(Menu.class).list();
    }
}
