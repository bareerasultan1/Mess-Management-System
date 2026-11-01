package com.giki.mess.repository;

import com.giki.mess.billing.model.StudentLedger;
import java.util.Optional;

/**
 * LEDGER REPOSITORY INTERFACE
 *
 * Manages persistence of student financial accounts.
 * Extends the generic IRepository with a studentId-based lookup.
 *
 * Note: findById() here would take a ledger's internal UUID,
 * but in practice we always look up ledgers by studentId — so we add that method.
 */
public interface ILedgerRepository extends IRepository<StudentLedger, String> {

    /**
     * Finds the active ledger for a given student registration number.
     *
     * @param studentId The student's registration number (e.g. "2023294")
     * @return Optional<StudentLedger> — empty if student has no ledger account yet
     */
    Optional<StudentLedger> findByStudentId(String studentId);
}
