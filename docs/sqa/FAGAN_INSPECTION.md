# Formal (Fagan-Style) Code Inspection: Billing and Payment Core

SE431 Milestone 2. Author of the inspected code: Mahvash Imran (2023294).

> **STATUS: the preparation stage is done and the inspection meeting is NOT yet held.**
> Everything marked "done" below was produced from the real code. Section 7 (the meeting record) is
> a form that must be filled in during the actual meeting with the external inspector from the paired
> team. The course requires that meeting; no names, dates or findings may be written into section 7
> unless it really took place. The preparation findings in section 5 are labelled as such.

## 1. Artifacts inspected

| Artifact | Non-blank lines | Why |
| --- | --- | --- |
| `payment/service/PaymentServiceImpl.java` | 189 | money moves here (settlement, duplicate protection) |
| `payment/factory/PaymentProcessorFactory.java` | 35 | Factory Method pattern |
| `billing/model/StudentLedger.java` | 255 | the thread-safe money account |
| `billing/service/BillingServiceImpl.java` | 224 | the billing rules |
| `payment/gateway/AbstractSignedProcessor.java` | 123 | callback signature checking (security) |
| `payment/model/PaymentTransaction.java` | 160 | payment state |

Total 986 lines (line counts at commit `0d2b506`). The author's preparation review was done on the
code of commit `99fa5fe`, before fixes DEF-01 to DEF-04; the inspectors should read commit `0d2b506`
or later.

## 2. Process (Fagan stages) and status

| Stage | Content | Status |
| --- | --- | --- |
| 1. Planning | scope, roles, checklist, entry criteria (this document) | done |
| 2. Overview | the author explains the design to the inspectors (15 minutes) | **to do**, part of the meeting |
| 3. Preparation | every inspector reads the code alone with the checklist (section 4) | author done (section 5); **other inspectors to do** |
| 4. Inspection meeting | the reader paraphrases the code, inspectors raise defects, the recorder logs them; no fixing during the meeting | **to do** (section 7) |
| 5. Rework | the author fixes logged defects | in progress: DEF-01 to DEF-04 and DEF-09 fixed, others deferred with reasons |
| 6. Follow-up | the moderator verifies each fix and closes the CAPA item | **to do** (section 8) |

Entry criteria (all met): code compiles, `mvn verify` passes, static analysis clean.
Exit criteria: every Major or Critical defect fixed or formally deferred with an owner and a milestone.

## 3. Roles

Roles are proposed; confirm them in the meeting.

| Role | Person | Notes |
| --- | --- | --- |
| Author | Mahvash Imran (2023294) | wrote the code, explains it in the overview, does not moderate |
| Moderator | Zouhair Azam Khan (2023785) | runs the meeting, verifies the follow-up |
| Reader | Bareera Sultan (2023158) | paraphrases the code line by line |
| Recorder | one of the above (decide on the day) | writes every defect into section 7 |
| **External inspector** | **[name, registration number and team of the paired-team student: TO BE FILLED]** | required by the course guidelines for Milestone 2 |

## 4. Inspection checklist

| # | Check | Preparation result (author) |
| --- | --- | --- |
| 1 | Boundary and input checks: every public method validates its arguments | ok; validation also at the API boundary |
| 2 | Exception leaks: no stack trace or internal class name reaches a user | ok (`ApiResult`); DEF-05 shows an exception path that leaves partial state |
| 3 | Thread safety: every shared mutable field has a documented lock | ok after DEF-01; `Bill` and `StudentLedger` locked; see DEF-07 |
| 4 | Resource leaks: connections and locks always released | ok (try-with-resources, `finally` unlock) |
| 5 | Null safety: no unchecked null dereference | ok after static analysis (DEF-09) |
| 6 | Money handling: exact arithmetic, no floating point | ok (`Money` in paisa); DEF-08 note |
| 7 | State machine: only legal status changes | failed before DEF-02, ok after |
| 8 | Security: callbacks authenticated, secrets and personal data not logged | HMAC-SHA256, constant-time compare; DEF-03 fixed; ids masked in logs |
| 9 | Idempotency: a repeated message changes nothing | ok for payments; DEF-10 for scan events after a restart |
| 10 | Business rules wired in: every documented rule is actually executed | failed before DEF-04, ok after |
| 11 | SOLID: dependencies are interfaces; classes have one responsibility | ok (constructor injection, repository interfaces) |
| 12 | Logging: useful, no personal data | ok |

## 5. Preparation findings and defect log

Found by the author while preparing, with AI assistance (Claude), by reading the code against the
checklist and by running the static analysers. They are **candidate findings to be confirmed,
reclassified or rejected by the inspectors in the meeting**; the meeting adds its own findings in section 7.

Severity scale: **Critical** money lost or security bypassed in normal use. **Major** wrong result or
corrupted state in a realistic scenario. **Minor** limited impact or hardening. **Info** observation.

| ID | Artifact and place | Defect | Severity | Disposition | CAPA and evidence |
| --- | --- | --- | --- | --- | --- |
| DEF-01 | `BillingServiceImpl.recordMealSwipe` | If the ledger debit succeeded but saving it failed, the scan id was forgotten, so a retry billed the same meal twice | Major | **Fixed** | the id is kept once the debit happened. Test `def01_retryAfterSaveFailure_doesNotDoubleBill`. RED `17edbe3`, fix `6848750` |
| DEF-02 | `PaymentTransaction.markXxx` | any status could be set from any status (for example RECONCILED without SUCCESS, or FAILED turned into SUCCESS) | Major | **Fixed** | legal transitions only (INITIATED to SUCCESS, FAILED or DUPLICATE; SUCCESS to RECONCILED), otherwise `IllegalStateException`. `PaymentTransactionStateMachineTest`. `6848750` |
| DEF-03 | `AbstractSignedProcessor.sign` | the signed text joined fields with `\|`, so text could be moved between neighbouring fields without changing the signature | Minor (security hardening) | **Fixed** | each field is length-prefixed. `SignatureEncodingTest`. `6848750` |
| DEF-04 | `StudentLedger.suspendIfOverdue` | the business rule "owing more than 10,000 PKR suspends the account" existed but nothing called it | Minor | **Fixed** | called when a bill is generated. `def04_*` tests. `6848750` |
| DEF-05 | `PaymentServiceImpl.settlePayment` | ledger save, bill save and transaction save are three separate writes; a failure between them leaves the ledger credited but the bill or transaction unchanged, and the claimed key blocks a retry | Major | **Deferred** to Milestone 3 | needs one database transaction across the three records (unit of work). Owner Mahvash. Risk R-09. Mitigation now: the in-memory repositories and the happy path are tested; the failure window is small |
| DEF-06 | `settlePayment`, status check | a SUCCESS callback that arrives after a FAILED one for the same payment is treated as a duplicate and ignored | Minor | **Deferred / accepted** | real gateways send one final outcome per payment; revisit when real provider semantics are known (M3). Owner Mahvash |
| DEF-07 | `calculateMonthlyBill` against `settlePayment` | the bill status is read, a new bill is built and saved; a payment applied in between would be overwritten by the new UNPAID bill | Major | **Deferred** to Milestone 3 | needs an optimistic version column or a per-bill lock. Owner Mahvash. Risk R-10. Mitigation now: bills with a payment are never recalculated; the race window is the length of one calculation |
| DEF-08 | `StudentLedger.creditAmount` | an overpayment is silently floored at zero, so the excess is not recorded anywhere | Info | **Accepted** | `initiatePayment` already refuses an amount above what the bill owes, so an overpayment cannot normally occur |
| DEF-09 | several (static analysis) | mutable collections exposed by `ItemizedInvoice`; unsynchronized write in `Bill.restore`; dead null check; possible null dereference in the database layer | Minor | **Fixed** | see `STATIC_ANALYSIS_REPORT.md` section 4 |
| DEF-10 | `BillingServiceImpl.recordedScanEvents` | the replay guard for meal scans is in memory only; after a restart a replayed scan event would be billed again | Minor | **Deferred** to Milestone 3 | add a table with a UNIQUE event id. Owner Mahvash. Risk R-02 residual |

Counts: 10 candidate defects. Major 4 (DEF-01 and DEF-02 fixed; DEF-05 and DEF-07 deferred).
Minor 5 (DEF-03, DEF-04 and DEF-09 fixed; DEF-06 and DEF-10 deferred). Info 1 (DEF-08, accepted).
Status: 5 fixed, 4 deferred, 1 accepted. Defect density from the author's preparation alone:
10 defects in 986 lines = 10.1 per KLOC.

## 6. Checklist for the other inspectors (preparation)

Read the six files in section 1 with the checklist in section 4. Write each suspected defect as
"file, line, what is wrong, why it matters". Bring the list to the meeting. Typical preparation time:
60 to 90 minutes. Record the time you spent in section 7.

## 7. Inspection meeting record (TO BE COMPLETED DURING THE MEETING)

| Field | Entry |
| --- | --- |
| Date, place, duration | |
| Moderator, author, reader, recorder | |
| External inspector (name, registration number, team) | |
| Preparation time per inspector | |
| Lines inspected | |

Findings raised in the meeting (one row per defect):

| ID | File and line | Description | Raised by | Severity | Decision (fix / defer / reject) |
| --- | --- | --- | --- | --- | --- |
| INS-01 | | | | | |
| INS-02 | | | | | |
| INS-03 | | | | | |

Meeting totals: defects found ___ , Major ___ , Minor ___ , preparation hours ___ , meeting hours ___ .
Outcome: accepted / accepted after rework / re-inspect. Signatures: moderator ____ author ____ external inspector ____

## 8. CAPA tracking

| Defect | Corrective action | Owner | Due | Status | Verified by (evidence) |
| --- | --- | --- | --- | --- | --- |
| DEF-01 | keep scan id after debit | Mahvash | done 2026-11-19 | Closed | regression test passes; moderator to confirm |
| DEF-02 | enforce transitions | Mahvash | done 2026-11-19 | Closed | `PaymentTransactionStateMachineTest`; moderator to confirm |
| DEF-03 | length-prefixed signature | Mahvash | done 2026-11-19 | Closed | `SignatureEncodingTest`; moderator to confirm |
| DEF-04 | call suspension rule | Mahvash | done 2026-11-19 | Closed | `def04_*` tests; moderator to confirm |
| DEF-05 | one database transaction for settlement | Mahvash | Milestone 3 | Open (deferred) | |
| DEF-06 | define out-of-order outcome handling | Mahvash | Milestone 3 | Open (deferred) | |
| DEF-07 | optimistic version on bills | Mahvash | Milestone 3 | Open (deferred) | |
| DEF-08 | none | | | Accepted | |
| DEF-09 | see static analysis | Mahvash | done 2026-11-19 | Closed | `STATIC_ANALYSIS_REPORT.md` |
| DEF-10 | scan event table with UNIQUE id | Mahvash | Milestone 3 | Open (deferred) | |
| INS-xx | (from the meeting) | | | | |

"Closed" by the author means fixed and tested; it becomes closed for the course when the moderator
has verified it in the follow-up stage.
