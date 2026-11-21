# Digital Mess Management System

## Mobile UI preview

Open `preview.html` in a browser to try the landing page, demo login, dashboard, Mess In/Out screen, and profile. Use `ali` / `Mess@1234`. This standalone design preview simulates login and scans in browser memory; use the Java console and PostgreSQL setup below to test real authentication and attendance persistence.

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

### Windows / VS Code

Install JDK 17+, Maven 3.9+, and the [PostgreSQL Windows installer](https://www.postgresql.org/download/windows/). Keep the server and command-line tools, set a password for `postgres`, and note the port (default `5432`). Stack Builder is optional. Add PostgreSQL's `bin` directory to PATH. Open the folder containing `pom.xml` in VS Code with the Extension Pack for Java installed and run these commands in a new PowerShell terminal:

```powershell
java -version
mvn -version
psql -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE mess_db;'
psql -h localhost -U postgres -d mess_db -v ON_ERROR_STOP=1 -f db/schema.sql
$env:DB_USER = 'postgres'
$credential = Get-Credential -UserName $env:DB_USER -Message 'Local PostgreSQL password'
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
Remove-Variable credential
mvn test
mvn compile exec:java '-Dexec.args=--check-db'
mvn compile exec:java '-Dexec.args=--seed'
mvn compile exec:java
Remove-Item Env:DB_PASSWORD
```

Skip database creation if `mess_db` already exists. For a different port or database name, set `DB_URL` to the corresponding JDBC URL. Seed demo users only into a throwaway local database. Do not commit real credentials, database exports, or student records.

**PostgreSQL smoke test:** During a meal window, log in as demo user `admin` and scan `RFID 04A3B2C1`. Expect `IN`. Scan again within five seconds to check duplicate-scan rejection, then wait at least five seconds from the accepted scan and scan again. Expect `OUT`. A fourth scan at least five seconds later should reject a second entry for that meal. Use a fresh demo database if that student has already entered the same meal today.

Verify the saved rows independently in a second VS Code terminal:

```powershell
psql -h localhost -U postgres -d mess_db -c 'SELECT scan_type, meal_type, scan_date FROM scan_logs JOIN users ON users.id = scan_logs.user_id WHERE username = ''ali'' ORDER BY scanned_at;'
```

### Bash

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

If this folder came from a ZIP and your team already has a GitHub repository, clone that repository first and apply the module changes to a feature branch there. Do not push a separate Git history over your team's default branch. For a standalone project, initialize Git here and connect it to a new, empty GitHub repository instead.

```bash
git checkout -b bareera/module1-attendance
git add . && git commit -m "..."      # commit in small steps under your own name
git push -u origin bareera/module1-attendance
```

## Not included yet

Billing, inventory and complaint modules, the CI pipeline, a REST/API layer, account lockout after failed logins.
