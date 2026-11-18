package com.giki.mess.campus;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CampusApp {
    public static void main(String[] args) { SpringApplication.run(CampusApp.class, args); }
    @Bean Clock campusClock(@Value("${mess.zone}") String zone) { return Clock.system(ZoneId.of(zone)); }
}
