package com.giki.mess.campus.menu;

import com.giki.mess.campus.persistence.MenuRepository;
import com.giki.mess.campus.contract.*;
import com.giki.mess.campus.domain.Rules;
import com.giki.mess.campus.domain.Conflict;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Menus {
    public record Menu(UUID id, LocalDate day, String meal, String dishes, boolean special, boolean published, long version) {}
    private final MenuRepository store;
    private final EventPublisher events;
    private final Clock clock;

    public Menus(MenuRepository store, EventPublisher events, Clock clock) {
        this.store = store; this.events = events; this.clock = clock;
    }
    @Transactional
    public UUID create(LocalDate day, String meal, String dishes, boolean special) {
        if (day == null) throw new IllegalArgumentException("Choose a date.");
        UUID id = UUID.randomUUID();
        store.insert(id,day,Rules.meal(meal),Rules.text(dishes,500),special);
        return id;
    }
    @Transactional
    public void edit(UUID id, long version, String dishes, boolean special) {
        Menu saved = store.edit(id,version,Rules.text(dishes,500),special).orElseThrow(Conflict::new);
        if (saved.published()) announce(saved);
    }
    @Transactional
    public UUID publish(UUID id, long version) {
        Menu saved = store.publish(id,version).orElseThrow(Conflict::new);
        return announce(saved);
    }
    private UUID announce(Menu menu) {
        UUID id = UUID.randomUUID();
        var kind = menu.special() ? AlertEvent.Kind.SPECIAL_MEAL : AlertEvent.Kind.MENU;
        events.publish(new AlertEvent(id, clock.instant(), kind, null, menu.day() + " " + menu.meal() + " menu updated."));
        return id;
    }
    public List<Menu> list(LocalDate day, String meal, boolean staff, int page) {
        return store.list(day,meal == null ? null : Rules.meal(meal),staff,Rules.offset(page));
    }
}
