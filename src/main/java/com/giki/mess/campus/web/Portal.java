package com.giki.mess.campus.web;

import com.giki.mess.campus.menu.Menus;
import com.giki.mess.campus.alerts.Inbox;
import com.giki.mess.campus.feedback.Feedback;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class Portal {
    private final Menus menus;
    private final Inbox inbox;
    private final Feedback cases;
    private final Environment env;
    Portal(Menus menus, Inbox inbox, Feedback cases, Environment env) {
        this.menus=menus; this.inbox=inbox; this.cases=cases; this.env=env;
    }
    record Draft(LocalDate day,String meal,String dishes,boolean special) {}
    record Edit(Long version,String dishes,boolean special) {}
    record Revision(Long version) {}
    record Submission(String kind,String body) {}
    record Tracking(UUID ref) {}
    record Move(String from,String to) {}
    record Rating(UUID menu,int score) {}
    @GetMapping("/session") Map<String,Object> session(Authentication auth,CsrfToken token) {
        return Map.of("name",auth==null?"":auth.getName(),"roles",auth==null?List.of():auth.getAuthorities().stream()
            .map(r->r.getAuthority()).toList(),"csrf",token.getToken(),"demo",env.acceptsProfiles(Profiles.of("demo")));
    }
    @GetMapping("/student/menus") List<Menus.Menu> browse(@RequestParam(required=false) LocalDate day,
            @RequestParam(required=false) String meal,@RequestParam(defaultValue="0") int page) {
        return menus.list(day,meal,false,page);
    }
    @GetMapping("/staff/menus") List<Menus.Menu> manage(@RequestParam(required=false) LocalDate day,
            @RequestParam(required=false) String meal,@RequestParam(defaultValue="0") int page) {
        return menus.list(day,meal,true,page);
    }
    @PostMapping("/staff/menus") Map<String,UUID> create(@RequestBody Draft form) {
        return Map.of("id",menus.create(form.day(),form.meal(),form.dishes(),form.special()));
    }
    private long version(Long value) {
        if (value == null || value<0) throw new IllegalArgumentException("Supply the displayed menu version.");
        return value;
    }
    @PutMapping("/staff/menus/{id}") void edit(@PathVariable UUID id,@RequestBody Edit form) {
        menus.edit(id,version(form.version()),form.dishes(),form.special());
    }
    @PostMapping("/staff/menus/{id}/publish") Map<String,UUID> publish(@PathVariable UUID id,@RequestBody Revision form) {
        return Map.of("event",menus.publish(id,version(form.version())));
    }
    @GetMapping("/student/inbox") List<Inbox.Item> inbox(Principal me,@RequestParam(defaultValue="0") int page) {
        return inbox.forStudent(me.getName(),page);
    }
    @PostMapping("/student/feedback") Tracking submit(@RequestBody Submission form) {
        return new Tracking(cases.submit(form.kind(),form.body()));
    }
    @PostMapping("/track") Feedback.Progress track(@RequestBody Tracking form) {
        return cases.track(form.ref());
    }
    @PostMapping("/student/ratings") void rate(@RequestBody Rating form) { cases.rate(form.menu(),form.score()); }
    @GetMapping("/staff/cases") List<Feedback.StaffCase> cases(@RequestParam(defaultValue="0") int page) { return cases.list(false,page); }
    @PatchMapping("/staff/cases/{id}") void move(@PathVariable UUID id,@RequestBody Move form) { cases.move(id,form.from(),form.to()); }
    @GetMapping("/admin/escalations") List<Feedback.StaffCase> escalations(@RequestParam(defaultValue="0") int page) { return cases.list(true,page); }
}
