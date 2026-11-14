package com.giki.mess.repository.persistence;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.repository.IBillRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory bill storage for tests (thread-safe map keyed by billId). */
public class InMemoryBillRepository implements IBillRepository {

    private final ConcurrentMap<String, Bill> store = new ConcurrentHashMap<>();

    @Override
    public Bill save(Bill bill) {
        store.put(bill.getBillId(), bill);
        return bill;
    }

    @Override
    public Optional<Bill> findById(String billId) {
        return Optional.ofNullable(store.get(billId));
    }

    @Override
    public List<Bill> findAll() {
        return List.copyOf(new ArrayList<>(store.values()));
    }

    @Override
    public void deleteById(String billId) {
        store.remove(billId);
    }

    @Override
    public Optional<Bill> findByStudentAndMonth(String studentId, int month, int year) {
        return store.values().stream()
            .filter(b -> b.getStudentId().equals(studentId)
                && b.getMonth() == month && b.getYear() == year)
            .findFirst();
    }

    @Override
    public List<Bill> findOverdueBills() {
        LocalDate today = LocalDate.now();
        return store.values().stream()
            .filter(b -> b.getPaymentStatus() != PaymentStatus.PAID
                && b.getDueDate().isBefore(today))
            .toList();
    }

    @Override
    public List<Bill> findByStudentId(String studentId) {
        return store.values().stream()
            .filter(b -> b.getStudentId().equals(studentId))
            .sorted(Comparator.comparingInt((Bill b) -> b.getYear() * 100 + b.getMonth()).reversed())
            .toList();
    }
}
