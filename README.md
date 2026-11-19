# Digital Mess-Management-System (GIKI) - SE432 Milestone 1

## Project Description
An automated backend system for managing GIKI mess attendance, student billing, Mess in and out, Online Menu, Complain and Feedback.

## Team Members & Responsibilities:
* **Bareera Sultan (2023158):** Attendence / Mess In and out Module
* **Mahvash Imran (2023294):** Biling Module
* **Zouhair Azam (2023789):** Online Menu, Complain Module

---

## Billing and Online Payment modules (Mahvash Imran, branch `Billing&PaymentSystem`)

### Requirements
Java 22 and Maven 3.9+. Running the tests needs nothing else (they start their own embedded PostgreSQL).
Running the demo needs a PostgreSQL server (see "Run against PostgreSQL" below).

### Build, test and coverage
```bash
mvn clean verify
```
`verify` compiles, runs all tests and **fails if line coverage drops below 60%**. Open
`target/site/jacoco/index.html` for the coverage report. A GitHub Actions workflow
(`.github/workflows/ci.yml`) runs the same command on every push and pull request.

### Run against PostgreSQL
1. Install PostgreSQL 17 (installer includes **pgAdmin 4** and **psql**).
2. Create the database and a user. In **psql** (as the `postgres` superuser):
   ```sql
   CREATE USER mess_user WITH PASSWORD 'choose-a-password';
   CREATE DATABASE mess_db OWNER mess_user;
   ```
3. Tell the program how to connect (PowerShell; the password is never stored in the code):
   ```powershell
   $env:MESS_DB_PASSWORD = "choose-a-password"
   # optional: $env:MESS_DB_URL, $env:MESS_DB_USER (defaults: jdbc:postgresql://localhost:5432/mess_db, mess_user)
   ```
4. Run the demo. It creates the tables (from `schema.sql`) and runs one student's month:
   ```bash
   mvn -q compile exec:java
   ```
5. Open pgAdmin (or psql) and inspect `student_ledgers`, `bills`, `bill_items`, `payment_transactions`.

### Package layout (`src/main/java/com/giki/mess`)
| Package | Contents |
| --- | --- |
| `billing.model / service / strategy / config / exception` | ledger, bill, billing engine, **Strategy** tariffs, rates, billing errors |
| `payment.model / gateway / factory / service / idempotency / exception` | simulated gateways, **Factory Method**, settlement, atomic duplicate guard |
| `observer` | **Observer** pattern: `AttendanceSubject`, `BillingObserver`, `ScanEvent` |
| `repository`, `repository.persistence` | storage interfaces, in-memory and PostgreSQL implementations |
| `api` | boundary: input validation, structured errors, audit logging |
| `common`, `util` | `Money` (exact paisa), log masking |
| `BillingPaymentModule`, `DemoRunner` | composition root that wires everything together; demo against a real database |

### Design patterns
Factory Method (`PaymentProcessorFactory`), Observer (`AttendanceSubject` / `BillingObserver`),
Strategy (`BillingCalculationStrategy`).

### Documents (`docs/`)
`THREAD_SAFETY_STRATEGY.md`, `PERSISTENCE_AND_BOUNDARIES.md`, `AI_USAGE_LOG_M2.md`.
Quality assurance (SE431): `docs/sqa/SQA_M2_REPORT.md` is the index (inspection, static analysis, V&V plan,
black-box and white-box test design, SCM gates, RTM v2, risk register).

### Notes
Payment gateways are **simulated sandboxes** (HMAC-signed callbacks); no live bank connection is used.
The Attendance module (Bareera) plugs in through `AttendanceDataSource` and `AttendanceSubject`.
