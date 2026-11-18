package com.giki.mess.campus.web;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpStatus;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
class Security {
    @Bean SecurityFilterChain campusSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a
            .requestMatchers("/","/index.html","/app.js","/campus.css","/api/session","/api/track","/error").permitAll()
            .requestMatchers("/api/student/**").hasRole("STUDENT")
            .requestMatchers("/api/staff/**").hasRole("STAFF")
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().denyAll())
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                new AntPathRequestMatcher("/api/**")))
            .formLogin(f -> f.permitAll().defaultSuccessUrl("/",true)).logout(l -> l.permitAll().logoutSuccessUrl("/"))
            .csrf(withDefaults()).build();
    }
    @Bean @Profile("demo")
    UserDetailsService demoAccounts(@Value("${MESS_DEMO_PASSWORD}") String password) {
        if (password.isBlank()) throw new IllegalArgumentException("Set MESS_DEMO_PASSWORD.");
        var users = new InMemoryUserDetailsManager();
        var encoder = org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String hash = encoder.encode(password);
        for (String name : new String[]{"student","student2","staff","admin"}) {
            String role = name.startsWith("student") ? "STUDENT" : name.toUpperCase(java.util.Locale.ROOT);
            users.createUser(User.withUsername(name).password(hash).roles(role).build());
        }
        return users;
    }
    @Bean @Profile("!demo")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(UserDetailsService.class)
    UserDetailsService closedUntilIntegrated() { return new InMemoryUserDetailsManager(); }
}
