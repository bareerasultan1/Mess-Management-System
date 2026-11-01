package com.giki.mess.repository;

import com.giki.mess.billing.model.Bill;
import java.util.List;
import java.util.Optional;

/**
 * BILLING REPOSITORY INTERFACE
 *
 * Extends the generic IRepository with bill-specific query methods.
 *
 * Notice:
 *   - findById(String billId)  — from the generic interface
 *   - findByStudentAndMonth()  — specific to bills (we need this for billing logic)
 *   - findOverdueBills()       — specific query used by the scheduler
 *
 * We add ONLY the methods we actually need (Interface Segregation Principle — ISP).
 * No massive interface full of methods no one uses.
 */
public interface IBillRepository extends IRepository<Bill, String> {

    /**
     * Finds the bill for a specific student in a specific month and year.
     * Used by BillingService to check if a bill was already generated
     * (prevents generating duplicate bills for the same period).
     *
     * @param studentId The student's registration number
     * @param month     Month number (1–12)
     * @param year      Year (e.g. 2026)
     * @return Optional<Bill> — empty if no bill found for that period
     */
    Optional<Bill> findByStudentAndMonth(String studentId, int month, int year);

    /**
     * Returns all unpaid bills past their due date.
     * Used by a scheduled job that marks overdue bills and sends reminders.
     *
     * @return List of all overdue bills across all students
     */
    List<Bill> findOverdueBills();

    /**
     * Returns all bills for a specific student (payment history).
     * Used by the student portal to show their billing history.
     *
     * @param studentId The student's registration number
     * @return List of all bills for this student, newest first
     */
    List<Bill> findByStudentId(String studentId);
}
