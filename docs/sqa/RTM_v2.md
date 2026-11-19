# Requirements Traceability Matrix, version 2 (Milestone 2)

SE431. Owner of this part: Mahvash Imran (2023294). Requirement ids are those of the submitted
Milestone 1 SQA report. RTM v1 listed every requirement as "Planned"; this version records what the
Milestone 2 build really implements and how each item was verified.
Scope: Billing (FR-002), Online Payment (FR-003) and the shared requirements as far as they concern
these modules. Attendance, menu, notification and feedback rows belong to the teammates and are not
repeated here. Build: commit `6b2a6fc`, 322 tests, 0 failures, 93.4% line coverage.

Status words: **Verified** = implemented and checked by tests and by review. **Partly verified** = a
defined part is verified, the rest is stated. **Planned** = scheduled for Milestone 3.

## 1. Requirement to code to test

| Req | Requirement (short) | Implemented in | Verified by (tests) | Test group | Status |
| --- | --- | --- | --- | --- | --- |
| FR-002 | Itemized bills from accepted meals with tariffs, absence rebate, fine and discount rules | `BillingServiceImpl`, `TariffConfiguration`, strategies in `billing.strategy`, `Bill`, `BillItem`, `StudentLedger`, `Money` | `BillingServiceTest`, `BillingServiceInvoiceAndValidationTest`, `BillingServiceSafetyTest`, `BillingLedgerReconciliationTest`, `InspectionDefectsBillingTest`; black-box `BB-ABS`, `BB-FIN`, `BB-CAT`, `BB-ID`; white-box `BasisPathBillingTest`; `BillingPaymentEndToEndIntegrationTest` | BL-01 | **Verified** |
| FR-003 | Support online payment channels; reconcile verified outcomes; record a successful transaction within 5 s | `PaymentProcessorFactory`, `JazzCashProcessor`, `EasyPaisaProcessor`, `BankTransferProcessor`, `PaymentServiceImpl`, `PaymentTransaction` | `PaymentProcessorFactoryTest`, `PaymentProcessorsTest`, `PaymentServiceTest`, `PaymentTransactionStateMachineTest`; black-box `BB-AMT`, `BB-CBK`; white-box `BasisPathPaymentTest`; end-to-end test including outage and recovery | PY-01 | **Verified with simulated gateways** (no live provider) |
| QA-01 | Exact monetary totals; duplicate scans or callback replays cannot create duplicate charges or settlements | `Money` (exact paisa), `IdempotencyManager`, `BillingServiceImpl.recordMealSwipe`, UNIQUE constraints in `schema.sql` | `MoneyTest`, `IdempotencyManagerTest`, `IdempotencyConcurrencyTest`, `BillingObserverTest` (replayed event billed once), `InspectionDefectsBillingTest` (DEF-01), `PostgresPersistenceTest` | BL-01, CT-01 | **Verified** for payments (also after a restart); scan replay protected in memory only (DEF-10) |
| QA-02 | Successful payment recorded within 5 s; application and provider latency measured separately | `PaymentServiceImpl.settlePayment` (target configurable, a slower settlement is logged) | `BasisPathPaymentTest` path 6 and 7, end-to-end test | PY-01 | **Partly verified**: application time only, simulated provider (declared mode); provider latency cannot be measured |
| QA-05 | Reject unverified payment confirmations; redact secrets and private data in logs | `AbstractSignedProcessor` (HMAC-SHA256, constant-time compare, 15 min expiry), `LogRedactor`, `BillingPaymentApi` audit log, `DatabaseConfig` (password from environment only) | `PaymentProcessorsTest`, `SignatureEncodingTest`, black-box `BB-CBK`, `BillingPaymentApiTest.auditTrailMasksStudentIds`, `DatabaseConfigTest` | PY-01 | **Partly verified**: payment confirmations and log redaction. Role-based access to staff actions is outside these modules |
| QA-07 | At least 60% core-logic coverage; maintainability | JaCoCo gate, Checkstyle complexity <= 10, SpotBugs gate | `mvn verify` (93.4% line, 77.9% branch); `STATIC_ANALYSIS_REPORT.md`; `WHITE_BOX_ANALYSIS.md` | all | **Verified** for these modules (target >= 85% application coverage also met) |
| QA-10 | Event ids, status meanings and amounts preserved across module boundaries; replays leave the same final records | `ScanEvent` (event id), `PaymentCallback` cross-checked against the stored request, `ItemizedInvoice` | `BillingObserverTest`, `BillingPaymentApiTest`, end-to-end test | CT-01 | **Partly verified**: Billing and Payment side only; the Attendance-to-Billing contract needs the teammate's module |
| QA-CONC-01 | Parallel scans and retried payment events produce one billable record and one settlement per unique event | `StudentLedger` lock, `Bill` synchronization, `IdempotencyManager`, `AttendanceSubject` | `ConcurrentLedgerAndPaymentTest` (50 threads; 200 scans plus a payment; 100 simultaneous payments), `IdempotencyConcurrencyTest` (10 duplicates, 1 settles), `StudentLedgerLockingTest`, `PostgresPersistenceTest.concurrentSaves_*` | CT-01 | **Verified** for Billing and Payment; Attendance side with Bareera |
| SE423 3.1 (6) | Persistent data layer with a defined schema | `schema.sql`, `Postgres*Repository`, `PostgresDatabase` | `RepositoryContractTest` (same tests run on in-memory and PostgreSQL), `PostgresPersistenceTest` | BL-01, PY-01 | **Verified** |

## 2. Test groups

| Group | Test classes |
| --- | --- |
| BL-01 (billing) | `BillingServiceTest`, `BillingServiceInvoiceAndValidationTest`, `BillingServiceSafetyTest`, `BillingLedgerReconciliationTest`, `InspectionDefectsBillingTest`, `BillTest`, `BillApplyPaymentTest`, `StudentLedgerTest`, black-box `BB-ABS/FIN/CAT/ID`, `BasisPathBillingTest` |
| PY-01 (payment) | `PaymentProcessorFactoryTest`, `PaymentProcessorsTest`, `PaymentServiceTest`, `PaymentTransactionTest`, `PaymentTransactionStateMachineTest`, `SignatureEncodingTest`, black-box `BB-AMT/CBK`, `BasisPathPaymentTest`, `BillingPaymentApiTest` |
| CT-01 (concurrency) | `ConcurrentLedgerAndPaymentTest`, `IdempotencyConcurrencyTest`, `IdempotencyManagerTest`, `StudentLedgerLockingTest`, `AttendanceSubjectTest`, `BillingObserverTest` |
| Integration | `BillingPaymentEndToEndIntegrationTest`, `RepositoryContractTest` (run on in-memory and on PostgreSQL), `PostgresPersistenceTest` |
| Whole suite | 322 tests, 0 failures |

Test counts by package (Maven report): api 54, billing 64, common 8, concurrency 20, observer 13,
payment 57, repository 35, sqa 67, util 2, root 2.

## 3. Defects and corrective actions linked to requirements

| Defect | Requirement | Status |
| --- | --- | --- |
| DEF-01 double billing on retry | QA-01, QA-CONC-01 | Fixed |
| DEF-02 illegal payment transitions | FR-003 | Fixed |
| DEF-03 ambiguous signature encoding | QA-05 | Fixed |
| DEF-04 suspension rule not enforced | FR-002 | Fixed |
| DEF-05 settlement not one transaction | FR-003, QA-01 | Deferred to M3 |
| DEF-06 late SUCCESS after FAILED ignored | FR-003 | Deferred / accepted |
| DEF-07 bill recalculation against a payment | FR-002, FR-003 | Deferred to M3 |
| DEF-08 overpayment floor | FR-003 | Accepted |
| DEF-09 static analysis findings | QA-07 | Fixed |
| DEF-10 scan replay guard in memory only | QA-01, QA-CONC-01 | Deferred to M3 |

Details: `FAGAN_INSPECTION.md`.

## 4. Changes since RTM v1

* Every row moved from "Planned" to an explicit verification status with the evidence listed.
* Statuses are deliberately not "100% verified" where a limit exists (simulated gateways, Attendance
  side, provider latency, staff roles). The limits are stated in the row.
* Added row for the persistence requirement of the construction course.
