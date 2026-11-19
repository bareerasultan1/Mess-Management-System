package com.giki.mess.billing.service;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.exception.BillNotFoundException;
import com.giki.mess.billing.exception.InvalidTariffException;
import com.giki.mess.billing.exception.LedgerNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.BillItem;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.strategy.BillingCalculationStrategy;
import com.giki.mess.common.Money;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The billing engine (FR-002): turns a student's meal scans and registered absences into a
 * monthly bill, keeps the student ledger in line with it, and records meal scans as they arrive.
 * It depends only on interfaces (repositories, attendance data, tariff strategies).
 */
public class BillingServiceImpl implements BillingService {

    private static final Logger LOG = LoggerFactory.getLogger(BillingServiceImpl.class);

    private final ILedgerRepository ledgers;
    private final IBillRepository bills;
    private final AttendanceDataSource attendance;
    private final TariffConfiguration tariff;
    private final Map<StudentCategory, BillingCalculationStrategy> strategies;

    /**
     * Scan events already billed. Thread-safe set, in memory only; a database table with a
     * UNIQUE event id is a planned improvement.
     */
    private final Set<String> recordedScanEvents = ConcurrentHashMap.newKeySet();

    /** Wires the service to its collaborators (all supplied from outside: dependency injection). */
    public BillingServiceImpl(ILedgerRepository ledgers, IBillRepository bills,
                              AttendanceDataSource attendance, TariffConfiguration tariff,
                              Map<StudentCategory, BillingCalculationStrategy> strategies) {
        this.tariff = tariff;
        this.strategies = strategies;
        this.ledgers = ledgers;
        this.bills = bills;
        this.attendance = attendance;
    }

    @Override
    public Bill calculateMonthlyBill(String studentId, int month, int year, LocalDate asOfDate) {
        requireValidBillRequest(studentId, month, asOfDate);
        StudentLedger ledger = findLedger(studentId);
        BillingCalculationStrategy strategy = strategyFor(ledger);

        // A bill that already received a payment must never be recalculated or overwritten.
        Optional<Bill> existing = bills.findByStudentAndMonth(studentId, month, year);
        if (existing.isPresent() && hasReceivedPayment(existing.get())) {
            return existing.get();
        }

        String billId = "BILL-" + studentId + "-" + year + "-" + month;
        List<BillItem> items = new ArrayList<>();
        Set<LocalDate> daysWithMeals = new HashSet<>();
        Money base = Money.ZERO;
        for (MealRecord meal : attendance.mealsFor(studentId, month, year)) {
            daysWithMeals.add(meal.scanTime().toLocalDate());
            Money rate = strategy.rateFor(meal.mealType());
            items.add(new BillItem(meal.eventId(), billId, studentId,
                meal.scanTime(), meal.mealType(), rate));
            base = base.plus(rate);
        }
        if (items.isEmpty()) {
            base = tariff.administrativeCharge();   // minimal charge when no meals were eaten
        }

        Money rebate = calculateRebate(
            attendance.registeredAbsences(studentId, month, year), daysWithMeals, month, year, strategy);
        Money mealCostAfterRebate = base.minus(rebate).floorAtZero();
        Money discount = strategy.discountOn(mealCostAfterRebate);

        LocalDate dueDate = LocalDate.of(year, month, 1).plusMonths(1)
            .withDayOfMonth(tariff.dueDayOfNextMonth());
        Money fine = Money.ZERO;
        if (asOfDate.isAfter(dueDate)) {
            fine = mealCostAfterRebate.minus(discount).percent(tariff.lateFinePercent());
        }

        Bill bill = new Bill(billId, studentId, month, year, dueDate,
            base, rebate, fine, discount, items);
        applyBillToLedger(ledger, bill, existing);
        return bills.save(bill);
    }

    private static void requireValidBillRequest(String studentId, int month, LocalDate asOfDate) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("studentId cannot be null or blank");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12, got: " + month);
        }
        if (asOfDate == null) {
            throw new IllegalArgumentException("asOfDate cannot be null");
        }
    }

    private StudentLedger findLedger(String studentId) {
        return ledgers.findByStudentId(studentId)
            .orElseThrow(() -> new LedgerNotFoundException(studentId));
    }

    private BillingCalculationStrategy strategyFor(StudentLedger ledger) {
        BillingCalculationStrategy strategy = strategies.get(ledger.getCategory());
        if (strategy == null) {
            throw new InvalidTariffException("No tariff strategy for category " + ledger.getCategory());
        }
        return strategy;
    }

    private static boolean hasReceivedPayment(Bill bill) {
        PaymentStatus status = bill.getPaymentStatus();
        return status == PaymentStatus.PAID || status == PaymentStatus.PARTIALLY_PAID;
    }

    /**
     * Brings the ledger in line with the bill. Only the CHANGE since the previous version of this
     * bill is applied, so recalculating never counts a rebate or fine twice.
     */
    private void applyBillToLedger(StudentLedger ledger, Bill bill, Optional<Bill> previous) {
        Money previousAdjustment = previous.map(BillingServiceImpl::adjustmentOf).orElse(Money.ZERO);
        ledger.adjustBalance(adjustmentOf(bill).minus(previousAdjustment));
        ledger.suspendIfOverdue();            // business rule: owing more than 10,000 PKR blocks mess access
        ledgers.save(ledger);
    }

    /** What the bill adds to (or removes from) the per-meal debits already on the ledger. */
    private static Money adjustmentOf(Bill bill) {
        Money scanned = Money.ZERO;
        for (BillItem item : bill.getItems()) {
            scanned = scanned.plus(item.getRateApplied());
        }
        return bill.getNetPayable().minus(scanned);
    }

    @Override
    public void recordMealSwipe(String studentId, MealType mealType,
                                LocalDateTime scanTime, String eventId) {
        requireValidSwipe(studentId, mealType, scanTime, eventId);
        // Atomic replay guard: add() returns false if another thread already recorded this scan.
        if (!recordedScanEvents.add(eventId)) {
            LOG.info("Replayed scan event ignored: event={}", eventId);
            return;
        }
        boolean debited = false;
        try {
            StudentLedger ledger = findLedger(studentId);
            ledger.debitAmount(strategyFor(ledger).rateFor(mealType));
            debited = true;
            ledgers.save(ledger);
        } catch (RuntimeException e) {
            if (!debited) {
                recordedScanEvents.remove(eventId);   // nothing was billed, so allow a retry
            }
            // If the debit already happened the event stays recorded: a retry must not bill the
            // meal twice, and the next successful save of this ledger stores the debit.
            throw e;
        }
    }

    private static void requireValidSwipe(String studentId, MealType mealType,
                                          LocalDateTime scanTime, String eventId) {
        if (studentId == null || studentId.isBlank() || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("studentId and eventId cannot be null or blank");
        }
        if (mealType == null || scanTime == null) {
            throw new IllegalArgumentException("mealType and scanTime cannot be null");
        }
    }

    @Override
    public ItemizedInvoice generateItemizedInvoice(String billId) {
        Bill bill = bills.findById(billId).orElseThrow(() -> new BillNotFoundException(billId));
        Map<MealType, Integer> counts = new EnumMap<>(MealType.class);
        for (MealType type : MealType.values()) {
            counts.put(type, 0);
        }
        Set<LocalDate> days = new HashSet<>();
        for (BillItem item : bill.getItems()) {
            counts.merge(item.getMealType(), 1, Integer::sum);
            days.add(item.getScanTime().toLocalDate());
        }
        return new ItemizedInvoice(bill.getBillId(), bill.getStudentId(), bill.getMonth(),
            bill.getYear(), days.size(), counts, bill.getBaseAmount(), bill.getRebateAmount(),
            bill.getDiscountAmount(), bill.getFineAmount(), bill.getNetPayable(), bill.getItems());
    }

    /**
     * Rebate rule: only runs of at least N consecutive registered absence days count
     * (N = threshold, default 3). Each counted day is credited rebatePercent of the daily
     * rate. The number of counted days is capped (default 20).
     */
    private Money calculateRebate(Set<LocalDate> absences, Set<LocalDate> daysWithMeals,
                                  int month, int year, BillingCalculationStrategy strategy) {
        TreeSet<LocalDate> days = new TreeSet<>();
        for (LocalDate d : absences) {
            if (d.getMonthValue() == month && d.getYear() == year && !daysWithMeals.contains(d)) {
                days.add(d);
            }
        }
        int counted = Math.min(countQualifyingDays(days), tariff.maxRebateDays());
        Money dailyRate = Money.ZERO;
        for (MealType type : MealType.values()) {
            dailyRate = dailyRate.plus(strategy.rateFor(type));
        }
        return dailyRate.percent(tariff.rebatePercent()).times(counted);
    }

    /** Counts days that belong to a run of at least the threshold number of consecutive days. */
    private int countQualifyingDays(TreeSet<LocalDate> sortedAbsenceDays) {
        int qualifyingDays = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : sortedAbsenceDays) {
            run = (previous != null && previous.plusDays(1).equals(day)) ? run + 1 : 1;
            if (run == tariff.absenceThresholdDays()) {
                qualifyingDays += run;          // the whole run starts counting
            } else if (run > tariff.absenceThresholdDays()) {
                qualifyingDays++;               // each further day in the run
            }
            previous = day;
        }
        return qualifyingDays;
    }
}
