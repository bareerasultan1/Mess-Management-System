package com.giki.mess.repository.persistence;

import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.repository.ILedgerRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory ledger storage (a thread-safe map). Used by unit tests and the
 * end-to-end test so business logic can be verified without a database.
 * Because it implements ILedgerRepository, the services cannot tell it apart
 * from the PostgreSQL version (Dependency Inversion).
 */
public class InMemoryLedgerRepository implements ILedgerRepository {

    private final ConcurrentMap<String, StudentLedger> store = new ConcurrentHashMap<>();

    @Override
    public StudentLedger save(StudentLedger ledger) {
        store.put(ledger.getStudentId(), ledger);
        return ledger;
    }

    @Override
    public Optional<StudentLedger> findById(String studentId) {
        return Optional.ofNullable(store.get(studentId));
    }

    @Override
    public Optional<StudentLedger> findByStudentId(String studentId) {
        return findById(studentId);
    }

    @Override
    public List<StudentLedger> findAll() {
        return List.copyOf(new ArrayList<>(store.values()));
    }

    @Override
    public void deleteById(String studentId) {
        store.remove(studentId);
    }
}
