package com.giki.mess.campus.feedback;

import com.giki.mess.campus.persistence.CaseRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.giki.mess.campus.domain.*;

@Service
public class Feedback {
    public record Progress(String status, boolean escalated, OffsetDateTime created, OffsetDateTime resolved) {}
    public record StaffCase(UUID ref, String kind, String body, String status, OffsetDateTime created, OffsetDateTime escalated) {}
    private final CaseRepository store;
    private final Clock clock;
    private final Duration threshold;
    public Feedback(CaseRepository store, Clock clock, @Value("${mess.escalation}") Duration threshold) {
        if (threshold.isZero() || threshold.isNegative()) throw new IllegalArgumentException("Threshold must be positive.");
        this.store=store; this.clock=clock; this.threshold=threshold;
    }
    public UUID submit(String kind, String body) {
        if (!"COMPLAINT".equals(kind) && !"FEEDBACK".equals(kind)) throw new IllegalArgumentException("Choose a submission type.");
        UUID ref = UUID.randomUUID();
        store.insert(ref,kind,Rules.text(body,2000),clock.instant());
        return ref;
    }
    public Progress track(UUID ref) {
        return store.progress(ref).orElseThrow(NoSuchElementException::new);
    }
    public List<StaffCase> list(boolean escalated, int page) {
        return store.list(escalated,Rules.offset(page));
    }
    public void move(UUID ref, String from, String to) {
        if (from == null || !Rules.transition(from,to)) throw new IllegalArgumentException("Invalid status transition.");
        if (!store.move(ref,from,to,clock.instant())) throw new Conflict();
    }
    @Transactional
    public int escalate() {
        var now = clock.instant();
        return store.escalate(now.minus(threshold),now);
    }
    public void rate(UUID menu, int value) {
        if (!store.rate(menu,Rules.rating(value))) throw new IllegalArgumentException("Choose a published meal.");
    }
}
