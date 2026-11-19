# Software Configuration Management: Branching, Quality Gates, Baseline Evidence

SE431 Milestone 2. Owner: Mahvash Imran (2023294). Repository:
https://github.com/bareerasultan1/Mess-Management-System (branch `Billing&PaymentSystem`).
All figures below were taken from the repository at commit `6b2a6fc` (2026-11-19).

## 1. Strategy promised in Milestone 1

`main` holds stable reviewed code; `develop` integrates finished features; `feature/*` branches are used
by members, merged into `develop` through pull requests with peer review, then into `main`. Tested
approved versions are tagged as baselines. Squash-merging is avoided so the red-green-refactor commit
trail survives (SE423 requirement).

## 2. What the repository actually shows

| Aspect | Planned | Actual (honest status) |
| --- | --- | --- |
| Long-lived branches | `main`, `develop` | only `Billing&PaymentSystem` exists on GitHub; `main` and `develop` are **not yet created** |
| Feature branch naming | `feature/<name>` | the module branch is named `Billing&PaymentSystem` (created at Milestone 1 by the team, kept to avoid breaking the shared link) |
| Pull requests with review | required | **not yet used**: all work was pushed directly to the module branch |
| Baseline tags | one per milestone | **none yet** |
| Commit trail | red-green-refactor | present, see section 3 |
| Automated build on push | CI | present (`.github/workflows/ci.yml`), first GitHub run to be confirmed by the team |

These gaps are recorded as risk R-08 and as actions in section 6. They are not hidden because the
guidelines grade SCM evidence "followed in the actual repository".

## 3. Commit evidence (on this branch since the Day 1 baseline `9ceb58a`)

| Measure | Value |
| --- | --- |
| Commits since the baseline | 47 (55 in the whole branch history) |
| Commits by author | Mahvash Imran 52 of 55 in the branch history; the other 3 are the repository's first commits by bareerasultan1 |
| Test-first trail | 17 `[RED]` commits, 16 `[GREEN]` commits, 4 `[REFACTOR]` commits |
| Commit message convention | Conventional Commits: 20 `test`, 14 `feat`, 6 `refactor`, 4 `fix`, 2 `docs`, 1 `build` (+ later commits) |
| Active days | 2026-11-14 to 2026-11-19 |
| Source / test size | 3,798 / 3,859 non-blank lines (tests are slightly larger than production code) |

The complete list of red and green commits is in the appendix. Each `[RED]` commit contains a test that
failed when it was committed (verified by running the tests before committing); the matching
`[GREEN]` commit makes it pass.

Individual attribution: commits are authored under the member's own name and e-mail. Commit messages do
not carry tool co-author lines; use of AI assistance is disclosed in `docs/AI_USAGE_LOG_M2.md`, as the
guidelines require.

## 4. Quality gates

| Gate | Where enforced | Threshold |
| --- | --- | --- |
| All automated tests pass | `mvn verify`, GitHub Actions on every push and pull request | 322 tests, 0 failures |
| Line coverage | JaCoCo check in `pom.xml` (`coverage-gate`) | fails below 60% (actual 93.4%) |
| Static analysis | SpotBugs check in `pom.xml` (`spotbugs-gate`) | fails on any finding that is not a documented exclusion |
| Style and complexity | Checkstyle (`mvn checkstyle:checkstyle`), reported | 0 findings now; becomes a hard gate in M3 |
| Peer review | pull request template `.github/pull_request_template.md` | one reviewer who is not the author |

The coverage gate was shown to work: with the threshold temporarily set to 99% the build failed with
"lines covered ratio is 0.91, but expected minimum is 0.99" and the original value was restored.

## 5. Baseline management and one history rewrite

* A baseline is a tagged commit that passes every gate. The proposed tag for this milestone is
  `v0.2.0-m2-billing`; it should be created after the pull request is merged (action in section 6).
* **History rewrite, disclosed.** On 2026-11-14 the 19 commits then on the branch were rewritten once
  (`git filter-branch`) to remove tool co-author lines from their messages, and the branch was
  force-pushed with `--force-with-lease`. File contents were byte-identical before and after
  (a `git diff` between the old and new tips was empty). This happened before any pull request or tag
  existed, so no reviewed or tagged baseline was affected. A local backup tag of the old history
  exists. Rewriting shared history is not repeated; from now on only normal pushes are used.

## 6. Actions needed to match the Milestone 1 strategy (team)

1. Create `develop` from the current state and make it the integration branch.
2. In GitHub settings protect `develop` and `main`: require a pull request, one approving review,
   and the `CI` status check to pass. (This is a repository setting only an admin can change.)
3. Open a pull request `Billing&PaymentSystem` into `develop`, reviewed by Zouhair or Bareera, using
   a merge commit (no squash). Keep the review conversation as evidence.
4. After the merge, tag the baseline: `git tag -a v0.2.0-m2-billing -m "Milestone 2 baseline"` and push the tag.
5. Check the Actions tab: the `CI` run must be green.

## Appendix: red-green-refactor commits (hash, subject)

```
091db30 test(billing): [RED] assert monthly base bill calculation matches attendance days
ca74001 feat(billing): [GREEN] implement base bill calculation for regular meal sessions
3b38274 refactor(billing): [REFACTOR] extract meal rate constants into TariffConfiguration
ccd6538 test(billing): [RED] assert absence rebate credited only for absences >= 3 days
efed601 feat(billing): [GREEN] implement absence rebate calculation rule
d47e8c8 test(billing): [RED] test late fee penalties and strategy-based tariff calculations
b2356a2 feat(billing): [GREEN] implement Strategy pattern for polymorphic tariff calculations and late fines
b62d010 test(billing): [RED] itemized invoice, zero-meal administrative charge and input validation
8aad920 test(billing): [RED] guard paid bills from recalculation, ignore absences contradicted by scans
9aada8e fix(billing): [GREEN] keep paid bills intact on recalculation, ignore contradicted absences
c50c1b1 test(payment): [RED] PaymentProcessorFactory selects gateway per channel
4190211 feat(payment): [GREEN] implement PaymentProcessorFactory following Factory Method pattern
ea94ca8 test(payment): [RED] callback signature, tamper, expiry and outage tests
02994cf feat(payment): [GREEN] simulated gateways with HMAC-SHA256 callback verification
7b8380f test(payment): [RED] settlement, partial payment, forged/duplicate callback, gateway timeout
75a9612 feat(payment): [GREEN] payment initiation and settlement
769f559 test(concurrency): [RED] ledger reads must wait for the balance lock
138582d fix(concurrency): [GREEN] protect StudentLedger reads with the balance lock
8446ee4 test(concurrency): [RED] IdempotencyManager and 10 concurrent duplicate callbacks
540a414 feat(concurrency): [GREEN] IdempotencyManager atomic gate
5cee8a4 test(observer): [RED] asynchronous AttendanceSubject, BillingObserver
ea0e359 feat(observer): [GREEN] AttendanceSubject, BillingObserver, idempotent recordMealSwipe
9ee76ea test(concurrency): [RED] 50-thread ledger, 100 concurrent payments (Bill loses updates)
fa08b7a fix(concurrency): [GREEN] make Bill thread-safe with atomic applyPayment
a1e05b0 test(billing): [RED] bill generation must reconcile ledger exactly once
23ca419 feat(billing): [GREEN] reconcile bill adjustments into the ledger
b98efdc test(persistence): [RED] repository contract, restart durability, rollback, constraints
54da79f feat(persistence): [GREEN] database repositories with durable unique constraints
f1b5779 test(validation): [RED] boundary validator and API structured-error behaviour
d263a4e feat(validation): [GREEN] boundary validator, structured API errors, masked audit logging
ee966ae test(persistence): [RED] contract and end-to-end tests against PostgreSQL
515be76 refactor(persistence): [GREEN] replace the previous database with PostgreSQL
75916ad test(sqa): [RED] regression tests for inspection defects DEF-01 to DEF-04
c2f5b83 fix(sqa): [GREEN] resolve inspection defects DEF-01 to DEF-04
ab6aff2 refactor(billing): [REFACTOR] split BillingServiceImpl methods (complexity <= 10)
c2a483b refactor(quality): [REFACTOR] resolve Checkstyle and SpotBugs findings
b1191b7 refactor(payment): [REFACTOR] extract requireMatchesOriginalRequest from settlePayment
```
