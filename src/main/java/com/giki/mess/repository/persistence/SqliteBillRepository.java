package com.giki.mess.repository.persistence;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.repository.IBillRepository;
import java.util.List;
import java.util.Optional;

/** Skeleton: behaviour is added test-first. */
public class SqliteBillRepository implements IBillRepository {

    public SqliteBillRepository(SqliteDatabase db) {
    }

    @Override
    public Bill save(Bill entity) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public Optional<Bill> findById(String id) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public List<Bill> findAll() {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public void deleteById(String id) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public Optional<Bill> findByStudentAndMonth(String studentId, int month, int year) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public List<Bill> findOverdueBills() {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public List<Bill> findByStudentId(String studentId) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }
}
