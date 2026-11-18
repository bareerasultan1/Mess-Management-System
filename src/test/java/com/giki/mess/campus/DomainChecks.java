package com.giki.mess.campus;

import java.time.*;
import java.util.UUID;
import com.giki.mess.campus.domain.Rules;
import com.giki.mess.campus.contract.AlertEvent;
import com.giki.mess.campus.contract.BillingAdapter;

/** Same checks run with plain Java or through JUnit. */
public final class DomainChecks {
    private static int checks;
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Failed check " + (checks + 1));
        checks++;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true); return; }
        throw new AssertionError("Expected validation failure");
    }
    public static void main(String... args) {
        checks = 0;
        for (int value : new int[]{1, 3, 5}) check(Rules.rating(value) == value);
        for (int value : new int[]{-1, 0, 6}) rejects(() -> Rules.rating(value));
        check(Rules.text("  Daal  ", 4).equals("Daal"));
        rejects(() -> Rules.text("  ", 10));
        rejects(() -> Rules.text("large", 4));
        rejects(() -> Rules.text(null, 4));
        check(Rules.transition("OPEN","IN_REVIEW"));
        check(Rules.transition("IN_REVIEW","RESOLVED"));
        check(Rules.transition("RESOLVED","CLOSED"));
        check(!Rules.transition("CLOSED","OPEN"));
        check(!Rules.transition("OPEN","RESOLVED"));
        check(Rules.meal("LUNCH").equals("LUNCH"));
        rejects(() -> Rules.meal("SNACK"));
        var zone = ZoneId.of("Asia/Karachi");
        var due = LocalDate.of(2026,11,18);
        var midnight = due.atStartOfDay(zone).toInstant();
        check(Rules.dueKind(due, Clock.fixed(midnight.minusNanos(1),zone)).isEmpty());
        check(Rules.dueKind(due, Clock.fixed(midnight,zone)).orElseThrow() == AlertEvent.Kind.DUE);
        check(Rules.dueKind(due, Clock.fixed(midnight.plusSeconds(86400).minusNanos(1),zone)).orElseThrow() == AlertEvent.Kind.DUE);
        check(Rules.dueKind(due, Clock.fixed(midnight.plusSeconds(86400),zone)).orElseThrow() == AlertEvent.Kind.OVERDUE);
        var id = UUID.randomUUID();
        var wire = new BillingAdapter.Notice(id, midnight, "student", "BILL_ISSUED");
        var event = BillingAdapter.translate(wire);
        check(event.id().equals(id) && event.occurred().equals(midnight));
        check(event.kind() == AlertEvent.Kind.BILL && event.recipient().equals("student"));
        rejects(() -> BillingAdapter.translate(new BillingAdapter.Notice(id,midnight,"student","PAID")));
        rejects(() -> new AlertEvent(id,midnight,AlertEvent.Kind.BILL,null,"Bill available"));
        System.out.println("PASS " + checks + " domain and translation contract checks");
    }
}
