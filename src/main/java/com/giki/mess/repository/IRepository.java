package com.giki.mess.repository;

import java.util.List;
import java.util.Optional;

/**
 * GENERIC REPOSITORY INTERFACE
 *
 * WHY DO WE HAVE A REPOSITORY LAYER?  (Answers the "why are we using this pattern?" question)
 * ────────────────────────────────────────────────────────────────────────────────────────────
 * The repository pattern separates "business logic" from "how data is stored."
 *
 * Without it, your BillingService would look like:
 *   Connection conn = DriverManager.getConnection("jdbc:sqlite:mess.db");
 *   Statement stmt = conn.createStatement();
 *   ResultSet rs = stmt.executeQuery("SELECT * FROM bills WHERE ...");
 *   // ... 50 lines of JDBC boilerplate inside your business logic
 *
 * Problems with that:
 *   1. Testing is IMPOSSIBLE — you cannot run unit tests without a real database
 *   2. If you switch from SQLite to MySQL, you must rewrite BillingService
 *   3. SQL is mixed with business rules — code is unreadable
 *
 * With this interface:
 *   - BillingService only sees: repository.save(bill), repository.findById(id)
 *   - For TESTS: you use a simple InMemoryRepository (just a HashMap, no database)
 *   - For PRODUCTION: you swap in a SQLiteRepository — BillingService doesn't care
 *
 * This is the "D" in SOLID — Dependency Inversion Principle.
 * (BillingService depends on this INTERFACE, not a concrete database class)
 *
 * @param <T>  The entity type (Bill, StudentLedger, PaymentTransaction, etc.)
 * @param <ID> The primary key type (usually String for UUID-based IDs)
 */
public interface IRepository<T, ID> {

    /**
     * Saves a new entity or updates an existing one.
     *
     * @param entity The entity to persist
     * @return The saved entity (may have generated fields like timestamps)
     */
    T save(T entity);

    /**
     * Finds an entity by its primary key.
     *
     * Returns Optional (not null) because:
     *   - Returning null forces callers to do null checks or risk NullPointerException
     *   - Optional forces the caller to explicitly handle the "not found" case
     *   - This is a modern Java best practice for lookups that may return nothing
     *
     * @param id The primary key
     * @return Optional containing the entity if found, or Optional.empty() if not
     */
    Optional<T> findById(ID id);

    /**
     * Returns all entities in storage.
     * (Use carefully — for large datasets, prefer filtered queries)
     *
     * @return Unmodifiable list of all entities
     */
    List<T> findAll();

    /**
     * Deletes the entity with the given ID.
     * Does nothing if the ID does not exist (no exception thrown).
     *
     * @param id The primary key of the entity to delete
     */
    void deleteById(ID id);
}
