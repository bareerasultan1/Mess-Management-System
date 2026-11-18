package com.giki.mess.campus;

import java.sql.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties="mess.scheduling=false")
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Import(PostgresSuite.TimeConfig.class)
abstract class PostgresSuite {
    static final Instant START = Instant.parse("2026-11-17T19:00:00Z");
    @Autowired JdbcClient db;
    @Autowired TestClock clock;
    static class TestClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(START);
        public ZoneId getZone() { return ZoneId.of("Asia/Karachi"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        public Instant instant() { return time.get(); }
        void advance(Duration interval) { time.updateAndGet(i -> i.plus(interval)); }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry settings) throws SQLException {
        String url = System.getenv("MESS_TEST_DB_URL");
        if (url == null || !url.startsWith("jdbc:postgresql:")) throw new IllegalStateException("Set MESS_TEST_DB_URL to a PostgreSQL test database.");
        String user=System.getenv().getOrDefault("MESS_DB_USER","campus");
        String pass=System.getenv().getOrDefault("MESS_DB_PASSWORD","");
        String schema="campus_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,user,pass); var s=c.createStatement()) { s.execute("create schema "+schema); }
        settings.add("spring.datasource.url",()->url);
        settings.add("spring.datasource.username",()->user);
        settings.add("spring.datasource.password",()->pass);
        settings.add("spring.datasource.hikari.schema",()->schema);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try(var c=DriverManager.getConnection(url,user,pass); var s=c.createStatement()) { s.execute("drop schema "+schema+" cascade"); }
            catch(SQLException ex) { System.err.println("Test schema cleanup pending: "+schema); }
        }));
    }
    @BeforeEach void cleanPrivateSchema() {
        db.sql("truncate campus_inbox,campus_event,campus_rating,campus_menu,campus_case,campus_reminder,campus_student").update();
        db.sql("insert into campus_student(id,enrolled_at) values('student','-infinity'),('student2','-infinity')").update();
        clock.time.set(START);
    }
}
