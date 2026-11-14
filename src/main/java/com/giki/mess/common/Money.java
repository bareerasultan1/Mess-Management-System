package com.giki.mess.common;

/**
 * VALUE OBJECT: Money
 *
 * An immutable amount of PKR stored as a whole number of paisa (1 PKR = 100 paisa).
 *
 * WHY NOT double?
 * ───────────────
 * Computers store double in binary, so 0.1 + 0.2 = 0.30000000000000004.
 * For a billing system that is a real bug (a bill off by a paisa fails reconciliation).
 * Integers are exact, and a long can be added atomically, so Money also keeps
 * the concurrency work on StudentLedger simple.
 *
 * Rounding rule (one place only): percentages are rounded HALF_UP to the nearest paisa.
 */
public record Money(long paisa) implements Comparable<Money> {

    public static final Money ZERO = new Money(0L);

    /** Creates Money from whole rupees, e.g. Money.pkr(120) = 120.00 PKR. */
    public static Money pkr(long rupees) {
        return new Money(Math.multiplyExact(rupees, 100L));
    }

    /** Creates Money from paisa, e.g. Money.ofPaisa(12050) = 120.50 PKR. */
    public static Money ofPaisa(long paisa) {
        return new Money(paisa);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(paisa, other.paisa));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(paisa, other.paisa));
    }

    /** Multiplies by a whole number, e.g. rate * number of days. */
    public Money times(long factor) {
        return new Money(Math.multiplyExact(paisa, factor));
    }

    /** Returns percent% of this amount, rounded HALF_UP to the nearest paisa. */
    public Money percent(int percent) {
        long scaled = Math.multiplyExact(paisa, (long) percent);
        long result = (scaled + (scaled >= 0 ? 50 : -50)) / 100;
        return new Money(result);
    }

    /** Returns this amount, or zero if it is negative. */
    public Money floorAtZero() {
        return paisa < 0 ? ZERO : this;
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public boolean isZero() {
        return paisa == 0;
    }

    public boolean isPositive() {
        return paisa > 0;
    }

    public boolean isNegative() {
        return paisa < 0;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(paisa, other.paisa);
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    /** Display value in PKR (for logs and printing only, never for arithmetic). */
    public double toPkr() {
        return paisa / 100.0;
    }

    @Override
    public String toString() {
        return String.format("%d.%02d PKR", paisa / 100, Math.abs(paisa % 100));
    }
}
