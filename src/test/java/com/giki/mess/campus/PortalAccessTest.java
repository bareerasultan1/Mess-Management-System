package com.giki.mess.campus;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import com.giki.mess.campus.alerts.Delivery;
import com.giki.mess.campus.contract.*;
import com.giki.mess.campus.feedback.Feedback;
import com.giki.mess.campus.menu.Menus;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class PortalAccessTest extends PostgresSuite {
    @Autowired MockMvc http;
    @Autowired Menus menus;
    @Autowired Feedback feedback;
    @Autowired EventPublisher events;
    @Autowired Delivery delivery;
    @Test void rolesCsrfAndDraftVisibility() throws Exception {
        menus.create(LocalDate.of(2026,11,18),"LUNCH","Draft only",false);
        http.perform(get("/api/student/menus")).andExpect(status().isUnauthorized());
        http.perform(get("/api/student/menus").with(user("student").roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        http.perform(get("/api/staff/menus").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        http.perform(get("/api/staff/menus").with(user("staff").roles("STAFF"))).andExpect(jsonPath("$[0].dishes").value("Draft only"));
        http.perform(post("/api/staff/menus").with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        http.perform(post("/api/staff/menus").with(csrf()).with(user("staff").roles("STAFF")).contentType("application/json")
            .content("{\"day\":\"2026-11-19\",\"meal\":\"LUNCH\",\"dishes\":\" \"}")).andExpect(status().isBadRequest());
        http.perform(get("/api/admin/escalations").with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        http.perform(get("/api/admin/escalations").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }
    @Test void publicTrackingAndStudentInboxAreMinimal() throws Exception {
        var ref=feedback.submit("COMPLAINT","Private fixture text");
        http.perform(post("/api/track").with(csrf()).contentType("application/json").content("{\"ref\":\""+ref+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.body").doesNotExist()).andExpect(jsonPath("$.recipient").doesNotExist())
            .andExpect(content().string(not(containsString("Private fixture text"))));
        events.publish(BillingAdapter.translate(new BillingAdapter.Notice(UUID.randomUUID(),clock.instant(),"student","BILL_ISSUED")));
        delivery.runOne();
        http.perform(get("/api/student/inbox?recipient=student").with(user("student2").roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        http.perform(get("/api/student/inbox").with(user("student").roles("STUDENT"))).andExpect(jsonPath("$[0].kind").value("BILL"));
    }
}
