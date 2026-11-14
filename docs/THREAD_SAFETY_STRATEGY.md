# Thread-Safety Strategy: Billing and Payment Modules

Owner: Mahvash Imran (2023294). Requirements: QA-CONC-01, risk R-02, test group CT-01.

## 1. Why concurrency matters here

Several things can touch the same student's money at the same moment:

- a meal scan (Attendance publishes an event, Billing debits the ledger),
- a payment callback from JazzCash/EasyPaisa/bank (credits the ledger, updates the bill),
- the same payment callback arriving two or more times (gateways retry on bad networks).

Without protection two threads can read the same old balance and both write a new one, so one
update is lost (a *race condition*). For a billing system that means wrong money.

## 2. What is shared and what protects it

| Shared mutable state | Where | Protection | Why this choice |
| --- | --- | --- | --- |
| Student balance, meal count, status | `StudentLedger` | One fair `ReentrantLock` (`balanceLock`) around every write **and every read** | Reads need the lock too, otherwise a thread may see a stale value; `snapshot()` returns balance, count and status from one moment |
| Bill amount owed and payment status | `Bill` | `synchronized` methods; `applyPayment` compares and updates in one atomic step | "Is the payment >= balance?" and "update the balance" must not be separated, or two payments lose each other's update |
| Which payment keys were already settled | `IdempotencyManager` | `ConcurrentHashMap.putIfAbsent` | One atomic operation: exactly one thread wins a key, all others are turned away before touching money |
| Which scan events were already billed | `BillingServiceImpl.recordedScanEvents` | `ConcurrentHashMap.newKeySet().add()` (atomic) | A replayed scan must be billed once |
| Registered observers | `AttendanceSubject` | `CopyOnWriteArrayList` | Safe to register while events are being published |
| Scan event, payment callback, money | `ScanEvent`, records, `Money` | **Immutability** (Java records, `final` fields) | An object that never changes needs no lock |

Money is an immutable value (`Money`, whole paisa in a `long`), so no floating-point rounding
error can appear and a `Money` object can be shared freely between threads.

## 3. Lock ordering (deadlock avoidance)

A thread never holds more than one of these locks at a time:

1. `StudentLedger.balanceLock` is taken and released inside one ledger method.
2. `Bill`'s monitor is taken and released inside one bill method.
3. `PaymentServiceImpl.settlePayment` calls the ledger and the bill **one after the other**, never
   nested, so no thread waits for a second lock while holding the first. Without a "hold one,
   wait for another" cycle a deadlock cannot form.

`ReentrantLock` is always released in a `finally` block, so an exception cannot leave a ledger
locked forever.

## 4. Idempotent settlement (risk R-02)

`PaymentServiceImpl.settlePayment` does, in this order:

1. find the transaction and check the callback matches the original request (student, bill, amount),
2. verify the gateway signature and freshness (HMAC-SHA256, 15 minutes),
3. `IdempotencyManager.tryAcquire(key)`: exactly one concurrent copy continues, the rest throw
   `DuplicateTransactionException` (acknowledged without any change),
4. look up the bill and ledger; if a lookup fails nothing has changed yet, so the key is released
   and the callback can be retried,
5. credit the ledger, apply the payment to the bill, mark the transaction RECONCILED.

A second guard (`status != INITIATED`) protects after a restart when the in-memory gate is empty.
The SQLite layer will add a `UNIQUE` constraint on `idempotency_key` as the durable guard.

## 5. Asynchronous scan handling (Observer pattern)

`AttendanceSubject.publish()` runs each observer on a worker thread and returns immediately. A
slow or failing billing database therefore cannot freeze the mess-gate turnstile. An exception in
one observer is logged and contained; other observers still run.

## 6. Evidence (tests)

| Test | What it proves |
| --- | --- |
| `StudentLedgerLockingTest` | every ledger read waits while the lock is held by another thread |
| `IdempotencyManagerTest` | 200 threads race for one key: exactly 1 winner |
| `IdempotencyConcurrencyTest` | 10 simultaneous identical callbacks: 1 settles, 9 rejected, ledger credited once. Before the fix all 10 settled |
| `ConcurrentLedgerAndPaymentTest` | 50 threads on one ledger lose no update; 200 scans plus a payment give the exact balance; 100 simultaneous payments on one bill end exactly PAID. Before `Bill` was made thread-safe this test lost payments (bill ended with 400 to 1,200 PKR still owing) |
| `AttendanceSubjectTest` | publish returns while an observer is blocked; a throwing observer is contained |
| `BillingObserverTest` | a replayed scan event is billed once |

Each concurrency test is repeated 5 times per run and the whole suite was run 5 times in a row
without a failure.

## 7. Known limits

- `IdempotencyManager` and the replayed-scan set are in memory. After a restart only the
  transaction status check and (later) the database `UNIQUE` constraints protect against replays.
- The ledger credit and the bill update are each atomic but not one single transaction. The SQLite
  layer will wrap them in one database transaction.
- `StudentLedger.creditAmount` floors the balance at zero, so the "balance = start + debits -
  credits" invariant holds only while credits do not exceed what is owed (the stress tests start
  from a large balance for that reason).
