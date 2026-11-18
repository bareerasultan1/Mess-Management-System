package com.giki.mess.campus.web;

import java.time.*;
import java.util.*;
import com.giki.mess.campus.alerts.BillReminders;
import com.giki.mess.campus.contract.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("demo")
@RequestMapping("/api/staff/fixtures")
class DemoFixtures {
    private final EventPublisher events;
    private final BillReminders bills;
    private final JdbcClient db;
    private final Clock clock;
    DemoFixtures(EventPublisher events,BillReminders bills,JdbcClient db,Clock clock) {
        this.events=events; this.bills=bills; this.db=db; this.clock=clock;
    }
    record Fixture(String kind) {}
    @PostMapping Map<String,UUID> exercise(@RequestBody Fixture fixture) {
        var id=UUID.randomUUID();
        switch (fixture.kind()==null ? "" : fixture.kind()) {
            case "ANNOUNCEMENT" -> events.publish(new AlertEvent(id,clock.instant(),AlertEvent.Kind.ANNOUNCEMENT,null,"Demo announcement: dining hall opens at noon."));
            case "BILL","DUE","OVERDUE" -> {
                int offset=switch(fixture.kind()) { case "DUE" -> 0; case "OVERDUE" -> -1; default -> 1; };
                var notice=new BillingAdapter.Notice(id,clock.instant(),"student","BILL_ISSUED");
                bills.issued(BillingAdapter.translate(notice),id.toString(),LocalDate.now(clock).plusDays(offset));
            }
            default -> throw new IllegalArgumentException("Unknown fixture.");
        }
        return Map.of("event",id);
    }
    @PostMapping("/{id}/replay") void replay(@PathVariable UUID id) {
        int n=db.sql("update campus_event set state='READY',cursor_id=null,attempts=0,available=? where id=? and state='DONE'")
            .params(java.sql.Timestamp.from(clock.instant()),id).update();
        if(n==0) throw new com.giki.mess.campus.domain.Conflict();
    }
}
