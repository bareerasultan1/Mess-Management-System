package com.giki.mess.campus;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.giki.mess.campus.menu.Menus;
import com.giki.mess.campus.alerts.Delivery;

import static org.assertj.core.api.Assertions.*;

class MenuSliceTest extends PostgresSuite {
    @Autowired Menus menus;
    @Autowired Delivery delivery;

    @Test void publishedMenuSurvivesReplayWithOneAlertPerStudent() {
        var day = LocalDate.of(2190, 1, 1);
        var id = menus.create(day, "LUNCH", "Chana pulao", false);
        assertThat(menus.list(day, "LUNCH", false, 0)).isEmpty();
        UUID event = menus.publish(id, 0);
        assertThat(menus.list(day, "LUNCH", false, 0)).hasSize(1);
        while (delivery.runOne()) { /* drain committed work */ }
        db.sql("update campus_event set state='READY' where id=?").param(event).update();
        assertThat(delivery.runOne()).isTrue();
        assertThat(db.sql("select count(*) from campus_inbox where event_id=? and recipient='student'").param(event).query(Integer.class).single()).isOne();
    }
}
