# Black-Box Test Design: Equivalence Partitioning and Boundary Value Analysis

SE431 Milestone 2. Owner: Mahvash Imran (2023294), Billing and Online Payment modules.
Executable form: `src/test/java/com/giki/mess/sqa/BlackBoxBillingPaymentTest.java`
(54 cases, every case id `BB-xxx-nn` appears in the test display name and in `RTM_v2.md`).

**Technique.** For each input the valid and invalid values are split into equivalence classes
(EP: one representative per class is enough because the system treats every member alike), and the
values on and next to every class boundary are tested (BVA: faults cluster at boundaries).
Expected results come from the stated business rules, not from running the code.

**Result of the last run:** 54 of 54 cases passed (commit `6b2a6fc`, 2026-11-19).

## 1. Payment amount (requirement FR-003, test group PY-01)

Input: amount in PKR entered by the student, through `BillingPaymentApi.startPayment`.
Rule: more than 0.00 and at most 50,000.00 PKR, at most 2 decimals, and not more than the amount
still owed on the bill (the test bill owes 60,000 PKR so that the upper limit is the active rule).

| Class | Range | Valid? | Expected |
| --- | --- | --- | --- |
| A1 | amount <= 0 | no | `INPUT_INVALID` |
| A2 | 0.01 <= amount <= 50,000.00 | yes | payment accepted |
| A3 | amount > 50,000.00 | no | `INPUT_INVALID` |
| A4 | more than 2 decimal places | no | `INPUT_INVALID` |

| Case | Amount | Class / boundary | Expected |
| --- | --- | --- | --- |
| BB-AMT-01 | -50.00 | A1 | INPUT_INVALID |
| BB-AMT-02 | -0.01 | BVA just below zero | INPUT_INVALID |
| BB-AMT-03 | 0.00 | BVA zero | INPUT_INVALID |
| BB-AMT-04 | 0.01 | BVA smallest valid | accepted |
| BB-AMT-05 | 0.02 | BVA above lower bound | accepted |
| BB-AMT-06 | 2,500.00 | A2 typical | accepted |
| BB-AMT-07 | 49,999.99 | BVA just below upper bound | accepted |
| BB-AMT-08 | 50,000.00 | BVA upper bound | accepted |
| BB-AMT-09 | 50,000.01 | BVA just above upper bound | INPUT_INVALID |
| BB-AMT-10 | 55,000.00 | A3 | INPUT_INVALID |
| BB-AMT-11 | 10.005 | A4 | INPUT_INVALID |

## 2. Consecutive absence days D (requirement FR-002, test group BL-01)

Input: the dates on which the student registered an absence. Rule: only a run of 3 or more
consecutive days earns a rebate; each counted day is credited 80% of the daily rate; at most 20 days
count. Daily rate 400 PKR (breakfast 100 + lunch 150 + dinner 150), so one counted day = 320 PKR.
The student's meals fall on days 25 to 30 so absence days never collide with scans.

| Class | D | Expected rebate |
| --- | --- | --- |
| B1 | 0 to 2 | 0 |
| B2 | 3 to 20 | D x 320 PKR |
| B3 | more than 20 | capped at 20 x 320 = 6,400 PKR |

The roadmap's extra class "D < 0 or D > 31 rejected" cannot be expressed at this layer: absences are
a set of calendar dates inside one month, so a negative or impossible count cannot be entered.
Invalid dates are filtered out (dates outside the billing month, dates on which the student scanned a meal).

| Case | D | Boundary | Expected rebate (PKR) |
| --- | --- | --- | --- |
| BB-ABS-01 | 0 | B1 | 0 |
| BB-ABS-02 | 1 | B1 | 0 |
| BB-ABS-03 | 2 | BVA below threshold | 0 |
| BB-ABS-04 | 3 | BVA threshold | 960 |
| BB-ABS-05 | 4 | BVA above threshold | 1,280 |
| BB-ABS-06 | 10 | B2 typical | 3,200 |
| BB-ABS-07 | 19 | BVA below cap | 6,080 |
| BB-ABS-08 | 20 | BVA cap | 6,400 |
| BB-ABS-09 | 21 | BVA above cap | 6,400 |
| BB-ABS-10 | 24 | B3 | 6,400 |

## 3. Evaluation date and the late fine (FR-002, BL-01)

Rule: the bill for November is due on 10 December 2026. A fine of 5% of the amount due applies only
when the bill is evaluated after the due date. The test bill is 2,000 PKR.

| Case | Evaluated on | Class / boundary | Expected fine (PKR) |
| --- | --- | --- | --- |
| BB-FIN-01 | 2026-11-30 | before the due date | 0 |
| BB-FIN-02 | 2026-12-09 | BVA day before | 0 |
| BB-FIN-03 | 2026-12-10 | BVA the due date itself | 0 |
| BB-FIN-04 | 2026-12-11 | BVA day after | 100 |
| BB-FIN-05 | 2027-02-01 | long overdue (flat, not compounding) | 100 |

## 4. Student category (FR-002, BL-01)

One full day of meals (standard price 400 PKR). Classes: one per tariff strategy.

| Case | Category | Expected net payable (PKR) |
| --- | --- | --- |
| BB-CAT-01 | REGULAR | 400 |
| BB-CAT-02 | SUBSIDIZED (60% of rates) | 240 |
| BB-CAT-03 | SCHOLAR (25% off) | 300 |
| BB-CAT-04 | FACULTY_GUEST (120% of rates) | 480 |

## 5. Payment callback (FR-003 / PY-01, security requirement QA-05)

Inputs: the signature of a gateway callback and its age. Classes: correctly signed and fresh |
forged signature | expired. The age limit is 15 minutes; up to 60 seconds in the future is tolerated
for clock differences.

| Case | Input | Class / boundary | Accepted? |
| --- | --- | --- | --- |
| BB-CBK-01 | age 0 s | valid, fresh | yes |
| BB-CBK-02 | age 899 s | BVA 14 min 59 s | yes |
| BB-CBK-03 | age 900 s | BVA exactly 15 min | yes |
| BB-CBK-04 | age 901 s | BVA 15 min 1 s | no |
| BB-CBK-05 | age 7,200 s | expired | no |
| BB-CBK-06 | 30 s in the future | clock skew tolerance | yes |
| BB-CBK-07 | 61 s in the future | BVA beyond tolerance | no |
| BB-CBK-08 | signature `deadbeef` | forged | no |
| BB-CBK-09 | empty signature | forged | no |
| BB-CBK-10 | 64 zeros | forged | no |

## 6. Student id and month (FR-002)

Valid student id: exactly 7 digits starting with `20`. Valid month: 1 to 12.

| Case | Input | Class / boundary | Valid? |
| --- | --- | --- | --- |
| BB-ID-01 | 2023294 | valid | yes |
| BB-ID-02 | 202329 | BVA 6 digits | no |
| BB-ID-03 | 2023294 | BVA 7 digits | yes |
| BB-ID-04 | 20232945 | BVA 8 digits | no |
| BB-ID-05 | 1023294 | wrong prefix | no |
| BB-ID-06 | 20A3294 | non-digit | no |
| BB-ID-07 | blank | blank | no |
| BB-ID-08 | month -1 | invalid | no |
| BB-ID-09 | month 0 | BVA below | no |
| BB-ID-10 | month 1 | BVA lower bound | yes |
| BB-ID-11 | month 2 | above lower bound | yes |
| BB-ID-12 | month 11 | below upper bound | yes |
| BB-ID-13 | month 12 | BVA upper bound | yes |
| BB-ID-14 | month 13 | BVA above | no |

## 7. Do these tests really detect faults? (mutation spot-check)

Three deliberate faults were injected one at a time into the production code and the black-box suite
was run. Each was detected and then removed (`git checkout`):

| Injected fault | File | Detected by |
| --- | --- | --- |
| rebate cap changed from 20 to 21 days | `TariffConfiguration.defaults()` | BB-ABS-09 and BB-ABS-10 |
| callback age limit changed from 15 to 16 minutes | `AbstractSignedProcessor` | BB-CBK-04 |
| payment limit changed from 50,000 to 50,001 PKR | `PaymentTransaction.MAX_AMOUNT` | BB-AMT-09 |

3 of 3 injected faults were caught. This is a spot-check, not a full mutation-testing run.

## 8. Traceability

| Input | Cases | Requirement | Test group |
| --- | --- | --- | --- |
| payment amount | BB-AMT-01 to 11 | FR-003 | PY-01 |
| absence days | BB-ABS-01 to 10 | FR-002 | BL-01 |
| evaluation date | BB-FIN-01 to 05 | FR-002 | BL-01 |
| student category | BB-CAT-01 to 04 | FR-002 | BL-01 |
| callback signature and age | BB-CBK-01 to 10 | FR-003, QA-05 | PY-01 |
| student id, month | BB-ID-01 to 14 | FR-002 | BL-01 |

The full requirement-to-code-to-test table is in `RTM_v2.md`.
