# Verification and Validation Plan (IEEE 1012 structure)

SE431 Milestone 2. Owner: Mahvash Imran (2023294). Scope: Billing (FR-002) and Online Payment
(FR-003) modules plus the shared concurrency requirement QA-CONC-01. References: IEEE 1012,
IEEE 730 (SQAP outline in the Milestone 1 report), ISO/IEC 25010, Milestone 1 SQA report (requirement ids),
Joint Project Guidelines (sections 3.2 and 6.2).

## 1. Purpose and definitions

* **Verification** answers "are we building the product right?": do the work products conform to
  their specification and to the standards (reviews, static analysis, unit and white-box tests).
* **Validation** answers "are we building the right product?": does the running system satisfy the
  student and mess-staff need (end-to-end scenarios, financial consistency checks, behaviour with a
  payment provider).

## 2. Integrity level

IEEE 1012 rates software by the consequence of failure. A wrong bill or a double charge causes
financial loss and loss of trust but no danger to people, so the Billing and Payment modules are
rated **integrity level 3** (major consequence, no safety impact). Level 3 calls for independent
review of code (the inspection with an external inspector), full unit and integration testing,
static analysis and traceability from requirements to tests, all of which this plan contains.

## 3. Organisation and responsibilities

| Role | Person | Responsibility |
| --- | --- | --- |
| V&V lead for Billing and Payment | Mahvash Imran | plans and runs the activities below, keeps the RTM and defect log |
| Independent reviewers | Zouhair Azam Khan, Bareera Sultan | review the inspection, the RTM, the test design |
| External inspector | student from the paired team | takes part in the Fagan inspection (Milestone 2 requirement) |
| Attendance module counterpart | Bareera Sultan | co-owner of QA-CONC-01 (scan replay on the attendance side) |

## 4. V&V activities, tied to requirements

Status: **Done** with evidence in the repository, **Planned M3** for the next milestone.

### 4.1 Verification

| ID | Activity | Requirements | Evidence | When | Status |
| --- | --- | --- | --- | --- | --- |
| V-01 | Test-first development (red-green-refactor) with a visible commit trail | FR-002, FR-003, QA-CONC-01 | 17 `[RED]`, 16 `[GREEN]` and 4 `[REFACTOR]` commits; see `SCM_QUALITY_GATES.md` | M2 | Done |
| V-02 | Unit and integration tests (JUnit 5, Mockito, AssertJ) | FR-002, FR-003, QA-01, QA-05 | 322 tests, 0 failures | M2 | Done |
| V-03 | Black-box test design (EP and BVA) | FR-002, FR-003, QA-05 | `BLACK_BOX_TEST_DESIGN.md`, 54 cases | M2 | Done |
| V-04 | White-box test design (CFG, V(G), basis paths) | FR-002, FR-003, QA-CONC-01 | `WHITE_BOX_ANALYSIS.md`, 13 tests, 100% branch coverage of both methods | M2 | Done |
| V-05 | Concurrency stress tests (50 threads, 100 simultaneous payments, 10 duplicate callbacks) | QA-CONC-01, R-02 | `ConcurrentLedgerAndPaymentTest`, `IdempotencyConcurrencyTest` | M2 | Done |
| V-06 | Static analysis (Checkstyle, SpotBugs) with triage | QA-07 | `STATIC_ANALYSIS_REPORT.md`; SpotBugs and coverage are build gates | M2 | Done |
| V-07 | Formal Fagan inspection with an external inspector | all | `FAGAN_INSPECTION.md` (preparation done, meeting pending) | M2 | **In progress** |
| V-08 | Coverage measurement against the 60% floor | QA-07 | JaCoCo: 93.4% line, 77.9% branch; `mvn verify` fails below 60% | M2 | Done |
| V-09 | Requirements traceability review | all | `RTM_v2.md` | M2, M3 | Done for M2 |
| V-10 | Mutation spot-check of the black-box suite | FR-002, FR-003 | 3 of 3 injected faults caught (`BLACK_BOX_TEST_DESIGN.md` section 7) | M2 | Done |
| V-11 | Repeat inspection of changed code, CAPA closure audit | all | CAPA table | M3 | Planned M3 |
| V-12 | Make Checkstyle a build gate; add a mutation-testing tool run | QA-07 | | M3 | Planned M3 |

### 4.2 Validation

| ID | Activity | Requirements | Evidence | When | Status |
| --- | --- | --- | --- | --- | --- |
| VAL-01 | End-to-end scenario: 75 scans through the observer, bill with rebate, JazzCash payment, signed callback, duplicate callback, application restart | FR-002, FR-003, QA-01, QA-CONC-01 | `BillingPaymentEndToEndIntegrationTest` on a real PostgreSQL | M2 | Done |
| VAL-02 | Fault scenario: gateway outage leaves the account untouched, retry after recovery succeeds | FR-003 (R-03) | `outageThenRecovery` test, `BillingPaymentApiTest` | M2 | Done |
| VAL-03 | Financial consistency audit: after a paid month the ledger balance is zero and the bill is PAID; the ledger equals the bill after rebates, discounts and fines | FR-002, FR-003 | `BillingLedgerReconciliationTest`, end-to-end test, `DemoRunner` | M2 | Done |
| VAL-04 | Manual walk-through of the demo on the real PostgreSQL server with pgAdmin, comparing the invoice with a hand calculation | FR-002 | README section "Run against PostgreSQL"; run by the team before the demo | M2 | **Team to perform** |
| VAL-05 | Payment provider sandbox test with a real provider | FR-003, R-03 | not possible: no sandbox credentials; gateways are simulated and labelled as such | M3 if access appears | Planned, limited |
| VAL-06 | Transaction-time measurement (successful payment recorded within 5 s) | QA-02 | settlement time is checked in code and in tests with a configurable target; provider latency cannot be measured with a simulator | M2 / M3 | Partly done |
| VAL-07 | Goal-Question-Metric data collection with real project data | all | | M3 | Planned M3 |

## 5. Entry and exit criteria for the milestone

* Entry: the build passes (`mvn clean verify`), static analysis shows no unexplained findings.
* Exit for Milestone 2: V-01 to V-10 done; V-07 meeting held and its defects logged; every Major
  defect fixed or deferred with an owner; RTM v2 and risk register updated.

## 6. Anomaly reporting

Every defect or anomaly found by any activity is entered in the defect and CAPA table of
`FAGAN_INSPECTION.md` with a severity, an owner, a corrective action and the evidence that it was
verified. Severity scale: Critical, Major, Minor, Info.

## 7. Schedule

| Milestone | V&V focus |
| --- | --- |
| M2 (Week 10) | everything marked Done above, plus the inspection meeting and VAL-04 |
| M3 (Week 13) | execution reports with real coverage numbers, GQM measurements, CAPA closure audit, repeated inspection, Checkstyle gate |

## 8. Limitations stated openly

* The payment gateways are simulations. They prove our verification of signatures, expiry and
  duplicates, not the behaviour of the real providers.
* Requirements about availability (QA-04), capacity (QA-03), backups (QA-08) and usability (QA-09)
  belong to the whole system and are not verified by this module's tests.
* The Attendance module is owned by a teammate; QA-CONC-01 is verified here only for the Billing and
  Payment side.
