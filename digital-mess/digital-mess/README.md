# Digital Mess Management System

Milestone 2 - SE423. This branch contains **Module 1: Automated Attendance Engine** (owner: Bareera Sultan),
limited to **user login** and **mess in / out scanning**. Billing, inventory and complaints belong to the other modules.

## What it does

- **Login** - username + password, passwords stored as PBKDF2 hashes, same error for every failure cause.
- **Mess in / out** - a terminal scan (RFID card or fingerprint) toggles a student between IN and OUT.

Scan rules (`AttendanceTracker`):

1. The card/fingerprint must belong to an **active student**.
2. Two scans within **5 seconds** are a double scan and are rejected.
3. If the student's last scan **today** was IN, this scan is **OUT** (same meal).
4. Otherwise it is an **IN**: only inside a meal window, and only **once per meal per day**.

Default meal windows (Asia/Karachi, start inclusive, end exclusive): breakfast 07:00-09:30, lunch 12:00-14:30, dinner 19:00-21:30.

## Requirements

- JDK 17 or newer, Maven 3.9+
- PostgreSQL 14+

## Setup

```bash
# 1. create the database and tables
createdb -U postgres mess_db
psql -U postgres -d mess_db -f db/schema.sql

# 2. connection settings (defaults shown)
export DB_URL=jdbc:postgresql://localhost:5432/mess_db
export DB_USER=postgres
export DB_PASSWORD=your_password

# 3. create demo users (admin, staff1, ali, sara - password Mess@1234)
mvn compile exec:java -Dexec.args="--seed"

# 4. run the console demo
mvn compile exec:java
```

Demo session: log in, then type `RFID 04A3B2C1` (Ali) or `BIO FP-1023` (Sara). The first scan inside a meal window is an
entry, the next one (after 5 s) is an exit. Outside a meal window an entry is rejected.

## Tests and coverage

```bash
mvn test                 # runs all unit tests
# coverage report: target/site/jacoco/index.html
```

Tests use in-memory repositories, so they do not need PostgreSQL.

## Project layout

```
db/schema.sql                       tables, indexes, unique constraint
src/main/java/com/mess/
  domain/        User (abstract) + StudentUser/StaffUser/AdminUser, UserFactory, ScanLog, enums
  adapter/       IAttendanceReader, BiometricDeviceAdapter, RawScan, ScanEvent   (Adapter pattern)
  observer/      AttendanceSubject, AttendanceObserver, AttendanceRecordedEvent  (Observer pattern)
  repository/    IUserRepository, IScanLogRepository
  repository/jdbc/  PostgreSQL implementations using plain JDBC
  service/       AuthService, PasswordHasher, AttendanceTracker, MealSchedule
  exception/     ValidationException, AuthenticationException, ScanRejectedException, DataAccessException
  Main.java      console demo
src/test/java/com/mess/          unit tests + in-memory repositories (support/)
```

## Design notes (link to the M1 proposal)

| Proposal item | Where it is in the code |
|---|---|
| Adapter pattern | `BiometricDeviceAdapter` turns raw RFID / fingerprint payloads into `ScanEvent` |
| Observer pattern | `AttendanceSubject` publishes `AttendanceRecordedEvent` to observers on a thread pool |
| SRP | `AttendanceTracker` only validates scans; auth and hashing are in `AuthService` / `PasswordHasher` |
| LSP | `StudentUser`, `StaffUser`, `AdminUser` can all be used as `User` |
| DIP | services depend on `IUserRepository` / `IScanLogRepository`, JDBC classes are plugged in from `Main` |
| Repository interfaces | `IUserRepository`, `IScanLogRepository` (specific interfaces instead of a generic `IRepository<T>`) |

The Factory Method pattern (payment processors) belongs to Module 2 and is not part of this branch.

## Thread safety strategy

- **Per-student lock.** "Read last scan -> decide IN/OUT -> save" must be atomic for one student. `AttendanceTracker`
  keeps a `ConcurrentHashMap<studentId, ReentrantLock>`; different students never block each other.
- **Database backstop.** The unique partial index `uq_one_entry_per_meal` prevents a second IN for the same meal and day
  even if two application instances race. The resulting `DuplicateEntryException` is turned into a rejected scan.
- **Asynchronous observers.** `AttendanceSubject` notifies observers on a 2-thread pool using a `CopyOnWriteArrayList`,
  so a slow or failing observer never delays or fails a scan.
- **Immutability.** Domain objects, `ScanLog`, `ScanEvent`, `MealSchedule` are immutable; JDBC repositories hold no state
  (one connection per call).
- **Test.** `AttendanceTrackerTest.manyThreadsScanningTheSameStudentCreateOnlyOneEntry` fires 20 threads at the same
  student and expects exactly 1 accepted scan.

## Error handling

All application errors extend `MessException`. Input is validated at the boundaries (adapter, `AuthService.register/login`),
business-rule refusals use `ScanRejectedException` with a `Reason`, and `SQLException` is wrapped in `DataAccessException`
so callers never see JDBC types.

## Branch workflow

```bash
git checkout -b bareera/module1-attendance
git add . && git commit -m "..."      # commit in small steps under your own name
git push -u origin bareera/module1-attendance
```

## Not included yet

Billing, inventory and complaint modules, the CI pipeline, a REST/API layer, account lockout after failed logins.
