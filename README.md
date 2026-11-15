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
Java 22 and Maven 3.9+. No database server is needed (embedded SQLite).

### Build, test and coverage
```bash
mvn clean verify
```
`verify` compiles, runs all tests and **fails if line coverage drops below 60%**. Open
`target/site/jacoco/index.html` for the coverage report. A GitHub Actions workflow
(`.github/workflows/ci.yml`) runs the same command on every push and pull request.

### Package layout (`src/main/java/com/giki/mess`)
| Package | Contents |
| --- | --- |
| `billing.model / service / strategy / config / exception` | ledger, bill, billing engine, **Strategy** tariffs, rates, billing errors |
| `payment.model / gateway / factory / service / idempotency / exception` | simulated gateways, **Factory Method**, settlement, atomic duplicate guard |
| `observer` | **Observer** pattern: `AttendanceSubject`, `BillingObserver`, `ScanEvent` |
| `repository`, `repository.persistence` | storage interfaces, in-memory and SQLite implementations |
| `api` | boundary: input validation, structured errors, audit logging |
| `common`, `util` | `Money` (exact paisa), log masking |
| `BillingPaymentModule` | composition root that wires everything together |

### Design patterns
Factory Method (`PaymentProcessorFactory`), Observer (`AttendanceSubject` / `BillingObserver`),
Strategy (`BillingCalculationStrategy`).

### Documents (`docs/`)
`THREAD_SAFETY_STRATEGY.md`, `PERSISTENCE_AND_BOUNDARIES.md`, `AI_USAGE_LOG_M2.md`.

### Notes
Payment gateways are **simulated sandboxes** (HMAC-signed callbacks); no live bank connection is used.
The Attendance module (Bareera) plugs in through `AttendanceDataSource` and `AttendanceSubject`.
