# Risk Register, version 2 (Milestone 2 update)

SE431. Risks owned by Mahvash Imran (2023294). Method as in the Milestone 1 report: probability P and
impact I on a 1 to 5 scale, priority = P x I (1-4 Low, 5-9 Medium, 10-25 High). A planned mitigation does
not reduce exposure; a residual rating is given only where the mitigation is evidenced.
Evidence base: commit `0d2b506`, 322 tests, inspection preparation, static analysis, SCM review.
Entries owned by Bareera and Zouhair (R-01, R-04, R-05, R-06, R-07) are for them to update.

## 1. Updated risks

| ID | Risk | M1 rating | Status at M2 | Evidence | Residual (P x I) |
| --- | --- | --- | --- | --- | --- |
| R-02 | Duplicate financial records: repeated or concurrent scans or callbacks create duplicate charges or settlements | P3 x I5 = 15 High | **Mitigated, residual Medium** | Before the fix, 10 simultaneous duplicate callbacks all settled (test showed 10 of 10). `IdempotencyManager` plus a UNIQUE `idempotency_key` in PostgreSQL: now exactly 1 settles and 9 are refused (`IdempotencyConcurrencyTest`, repeated 25 times). A replay after an application restart is stopped by the stored status and by the database constraint (end-to-end restart test). 100 simultaneous payments on one bill lose no update (`ConcurrentLedgerAndPaymentTest`). Remaining gap: replayed *scan events* are remembered in memory only (DEF-10), and settlement is not one database transaction (DEF-05). | P2 x I4 = **8 Medium** |
| R-03 | Payment integration access: sandbox or credentials may be unavailable | P4 x I4 = 16 High | **Mitigated in design, residual Medium** | Gateways sit behind `IPaymentProcessor` and `PaymentProcessorFactory`; three simulated processors with HMAC-signed callbacks, expiry, tamper and outage handling are tested (`PaymentProcessorsTest`, outage and recovery tests). They are labelled as simulations everywhere, as the M1 report requires. The risk that live provider access is unavailable remains true (still likely) but a real provider can replace a simulator by writing one class. No claim is made about real-provider performance or behaviour. | P4 x I2 = **8 Medium** |
| R-08 | Integration and evidence gaps: inconsistent contracts, missing TDD history or late documents weaken integration and individual evidence | P3 x I4 = 12 High | **Partly mitigated, still Medium** | 17 `[RED]` and 16 `[GREEN]` commits, conventional messages, CI workflow, PR template, AI usage log. Not done yet: no pull request, no `develop` branch, no tag, GitHub CI run unconfirmed, the cross-team inspection meeting not held, Attendance-to-Billing contract not integrated with the teammate's module. | P2 x I4 = **8 Medium** (after the actions in `SCM_QUALITY_GATES.md` section 6) |

## 2. New risks found in Milestone 2

| ID | Risk | P | I | Priority | Source | Mitigation | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-09 | A failure between the three writes of a settlement (ledger, bill, transaction) leaves the ledger credited but the bill or transaction stale, and the claimed key blocks a retry (DEF-05) | 2 | 4 | 8 Medium | inspection preparation | one database transaction around the three writes (unit of work) in M3; until then a manual reconciliation query | Mahvash | Open |
| R-10 | A payment applied while the same bill is being recalculated can be overwritten by the new UNPAID bill (DEF-07) | 2 | 4 | 8 Medium | inspection preparation | optimistic version column or per-bill lock in M3; bills that already received a payment are never recalculated | Mahvash | Open |
| R-11 | The mandatory cross-team Fagan inspection meeting is not held in time, costing the SE431 inspection criterion | 3 | 4 | 12 High | schedule review | book the meeting with the paired team now; the preparation, checklist and defect log are ready | Zouhair (moderator) | Open |
| R-12 | The shared module branch is rewritten again or merged by squash, destroying the test-first commit trail | 2 | 3 | 6 Medium | SCM review | no rewriting of shared history (one disclosed rewrite already happened, see `SCM_QUALITY_GATES.md`); merge commits only; protected branches | Mahvash | Open |
| R-13 | Embedded test database (PostgreSQL 14) differs from the server used in the demo (PostgreSQL 17) | 1 | 2 | 2 Low | persistence review | only standard SQL is used; the demo runs the same schema on the real server (manual step VAL-04) | Mahvash | Open |

## 3. Risk exposure summary

| | M1 | M2 |
| --- | --- | --- |
| High (10-25) among Mahvash's risks | R-02 (15), R-03 (16), R-08 (12) | R-11 (12) |
| Medium (5-9) | none | R-02, R-03, R-08, R-09, R-10, R-12 |
| Low (1-4) | none | R-13 |

Next review: Milestone 3 (Week 13), with execution results, GQM data and the closure status of
R-09, R-10 and DEF-10.
