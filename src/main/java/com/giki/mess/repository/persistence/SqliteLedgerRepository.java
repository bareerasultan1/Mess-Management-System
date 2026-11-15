package com.giki.mess.repository.persistence;

import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.repository.ILedgerRepository;
import java.util.List;
import java.util.Optional;

/** Skeleton: behaviour is added test-first. */
public class SqliteLedgerRepository implements ILedgerRepository {

    public SqliteLedgerRepository(SqliteDatabase db) {
    }

    @Override
    public StudentLedger save(StudentLedger entity) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public Optional<StudentLedger> findById(String id) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public List<StudentLedger> findAll() {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public void deleteById(String id) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public Optional<StudentLedger> findByStudentId(String studentId) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }
}
