# Persistence, Validation and Error Handling: Billing and Payment Modules

Owner: Mahvash Imran (2023294). Covers SE423 minimum requirements 5 (defensive programming,
structured exceptions) and 6 (persistent data layer with a defined schema).

## 1. Persistence layer

**Technology:** PostgreSQL (server) accessed through JDBC (`org.postgresql:postgresql`) with a
HikariCP connection pool. Schema: `src/main/resources/schema.sql`, executed at every start-up
(`CREATE ... IF NOT EXISTS`, so it is safe to repeat).

| Table | Purpose | Key constraints |
| --- | --- | --- |
| `student_ledgers` | running money account per student | PK `student_id`; `balance_paisa >= 0`; status and category are checked lists |
| `bills` | monthly bill snapshot | PK `bill_id`; FK to ledger; **UNIQUE (student, month, year)**; `month` 1-12; amounts `>= 0` |
| `bill_items` | one row per billed meal (audit trail) | PK (`bill_id`, `item_id`); FK to bill with `ON DELETE CASCADE`; `rate_paisa > 0` |
| `payment_transactions` | one row per payment attempt | PK `transaction_id`; **UNIQUE `idempotency_key`** (`uq_payment_idempotency`); `0 < amount <= 50,000.00 PKR`; FKs to ledger and bill |

Money is stored as whole **paisa** in `BIGINT` columns, so no rounding error can occur. Dates are
`DATE`, moments are `TIMESTAMP`.

**Connection settings** are never written in code. `DatabaseConfig.fromEnvironment()` reads
`MESS_DB_URL` (default `jdbc:postgresql://localhost:5432/mess_db`), `MESS_DB_USER` (default
`mess_user`) and `MESS_DB_PASSWORD` (required). `toString()` hides the password.

**Design (Dependency Inversion):** services depend on `ILedgerRepository`, `IBillRepository` and
`IPaymentRepository`. Two implementations exist: in-memory (fast unit tests) and PostgreSQL. One
abstract test, `RepositoryContractTest`, runs against both, so they behave identically.

**How the tests get a database:** the tests start a real PostgreSQL inside the test run
(`io.zonky.test:embedded-postgres`, PostgreSQL 14 binaries), so `mvn verify` needs no installed
server, password or Docker, on any machine and in GitHub Actions. The application itself always
talks to a normal PostgreSQL server.

**Important details**
- *Transactions:* a bill and its items are saved in one database transaction (all or nothing).
  Test `failedBillSave_rollsBackEverything` proves no partial rows remain.
- *Identity map:* each repository returns ONE object per id inside the process. This keeps the
  object's lock meaningful (two threads share one `StudentLedger`, not two private copies).
  `save()` takes the snapshot inside a per-id lock together with the SQL write, so an older
  snapshot can never overwrite a newer one. Test `concurrentSaves_noLostUpdateInDatabase`
  (20 threads x 10 saves) reads the total back through a brand-new connection pool.
- *Durable idempotency:* the database refuses a second payment with the same key
  (`uq_payment_idempotency`, SQL state 23505), even after a restart when the in-memory
  `IdempotencyManager` is empty. The repository reports it as `DuplicateTransactionException`.
- *Constraints are the last line of defence:* test `checkConstraintRefusesNegativeBalance` shows
  the database rejects an impossible row even if application code tried to store it.
- *Fail fast:* `PostgresDatabase.connect()` connects immediately. A wrong password, a stopped
  server or a missing database gives `PersistenceException` "Could not connect to the database at
  ... Check that PostgreSQL is running and that MESS_DB_URL, MESS_DB_USER and MESS_DB_PASSWORD are
  correct." instead of a hang.

## 2. Boundary validation (`BillingInputValidator`)

Whitelist validation at the module boundary, before any service runs:

| Input | Accepted | Rejected examples |
| --- | --- | --- |
| Student id | exactly 7 digits starting `20` (`2023294`) | `202329`, `20232945`, `1023294`, `20A3294`, `' OR 1=1 --` |
| Payment amount | 0.01 to 50,000.00 PKR, at most 2 decimals | `0`, `-0.01`, `50000.01`, `10.005`, null |
| Month / year | 1-12 / 2020-2100 | `0`, `13` / `2019`, `2101` |
| Bill id | `BILL-<studentId>-<year>-<month>` | `B1` |
| Payment reference (idempotency key) | 1-64 chars of letters, digits, `.` `_` `-` | spaces, empty, 65 chars |
| Channel | `JAZZCASH`, `EASYPAISA`, `BANK_TRANSFER` (any case) | `paypal` |

These classes and boundaries are exactly the equivalence classes and boundary values used in the
SE431 black-box tests. All SQL uses prepared statements, so input is never concatenated into SQL.

## 3. Structured error handling (`BillingPaymentApi`)

The boundary never throws to its caller. Every outcome is an `ApiResult` that holds either data or
`ErrorInfo(code, message)`:

| Code | Cause | Message shown (no stack trace, no class names) |
| --- | --- | --- |
| `INPUT_INVALID` | validation failed | the specific rule, e.g. "Student ID must be 7 digits ..." |
| `LEDGER_NOT_FOUND` | unknown student | "No mess account was found for this student." |
| `BILL_NOT_FOUND` | unknown bill | "No bill was found with that number." |
| `AMOUNT_EXCEEDS_DUE` / `BILL_ALREADY_PAID` / `BILL_OWNER_MISMATCH` | payment rule broken | plain-language sentence |
| `DUPLICATE_PAYMENT` | payment reference reused | "This payment was already submitted." |
| `GATEWAY_UNAVAILABLE` | gateway timeout | "... You have not been charged. Please try again later." |
| `INVALID_CALLBACK` | forged, expired or mismatched webhook | "The payment confirmation was rejected." |
| `STORAGE_ERROR` / `INTERNAL_ERROR` | unexpected failure | generic apology; full details only in the log |

A duplicate gateway webhook is **not** an error for the gateway: it gets
`DUPLICATE_ACKNOWLEDGED` (success) and nothing is credited twice.

## 4. Audit trail

Logger `com.giki.mess.audit` writes one line per boundary call (action, result code, **masked**
student id such as `202****`) to the console and `logs/billing_audit.log` (daily rolling, 30 days).
Test `auditTrailMasksStudentIds` proves full student ids never appear in the audit log (QA-05).

## 5. Ledger and bill agree

The ledger is debited per scan, but a bill also contains rebates, discounts, fines and the
administrative charge. Generating a bill applies only the *difference* to the ledger
(`StudentLedger.adjustBalance`), so recalculating a bill never counts an adjustment twice, and a
fully paid bill leaves a zero ledger (verified by the end-to-end test).

## 6. Known limits

- The replay guard for meal-scan events (`recordedScanEvents`) is in memory only. A restart would
  forget which scan events were already billed; a database table with a UNIQUE `event_id` is the
  next improvement.
- The ledger credit and the bill update are each atomic but not one database transaction.
- The identity map assumes ONE running copy of the application. Several application servers
  against one database would need database-level locking (`SELECT ... FOR UPDATE`) instead.
- The tests run on an embedded PostgreSQL 14; the manual setup guide targets PostgreSQL 17. Only
  standard SQL is used, so both behave the same.
- Gateways are simulated sandboxes; no live JazzCash/EasyPaisa/bank connection exists (risk R-03).
