# SE431 Milestone 2: Quality Assurance Report (Billing and Online Payment)

Project: Digital Mess Management System, GIKI. Team: Bareera Sultan (2023158), Mahvash Imran
(2023294), Zouhair Azam Khan (2023785). Supervisor: Dr. Mian Muaz Razaq.
This report covers the modules owned by Mahvash Imran. Repository:
https://github.com/bareerasultan1/Mess-Management-System, branch `Billing&PaymentSystem`.
Evidence snapshot: commit `0d2b506`, 2026-11-19.

The quality activities below were applied to the same build that Construction (SE423) produced in this
milestone cycle, as the guidelines require.

## 1. Evidence snapshot

| Measure | Value |
| --- | --- |
| Automated tests | 322, 0 failures (`mvn clean verify`) |
| Line / branch coverage (JaCoCo) | 93.4% / 77.9% (course floor 60%, enforced by the build) |
| Black-box cases | 54 (EP and BVA), 3 of 3 injected faults detected |
| White-box tests | 13, 100% line and 100% branch coverage of the two analysed methods |
| Concurrency tests | 20 (50 threads, 100 simultaneous payments, 10 simultaneous duplicate callbacks) |
| Checkstyle | 217 findings reduced to 0 (7 justified suppressions); untailored Google rules gave 2,077 |
| SpotBugs | 23 findings reduced to 0 (16 fixed, 7 justified exclusions); enforced by the build |
| Largest method complexity | 10 (limit 10), measured on all 93 methods |
| Defects found in preparation review | 10: 5 fixed, 4 deferred with owner and milestone, 1 accepted |
| Test-first trail | 17 `[RED]`, 16 `[GREEN]`, 4 `[REFACTOR]` commits |

## 2. Rubric criteria and where the evidence is

| SE431 Milestone 2 criterion (weight) | Document | Status |
| --- | --- | --- |
| Formal (Fagan) inspection report (20) | `FAGAN_INSPECTION.md` | Package, roles, checklist, defect log and CAPA table are done. **The inspection meeting with an external inspector still has to be held and recorded in section 7 of that document.** |
| Static analysis report (15) | `STATIC_ANALYSIS_REPORT.md` | Done. Real tools, every finding triaged as fixed, deferred or justified, corrective actions listed |
| V&V plan, IEEE 1012 (15) | `VV_PLAN_IEEE1012.md` | Done. Verification and validation separated, tied to requirement ids, with owners, timing and evidence. VAL-04 (manual run on the real PostgreSQL) is for the team to perform |
| Black-box test design (15) | `BLACK_BOX_TEST_DESIGN.md` and `BlackBoxBillingPaymentTest` | Done. EP and BVA on six real inputs, traceable to FR-002, FR-003, QA-05 |
| White-box test design (15) | `WHITE_BOX_ANALYSIS.md`, `BasisPathBillingTest`, `BasisPathPaymentTest` | Done. Two CFGs, V(G) 6 and 7 confirmed by a tool, basis-path tests, coverage evidence |
| SCM quality-gate evidence (10) | `SCM_QUALITY_GATES.md`, `.github/pull_request_template.md`, `ci.yml` | Partly done. Gates, CI and commit evidence exist. **No `develop` branch, pull request or tag exists yet** (actions listed in section 6 of that document) |
| RTM, risk and defect/CAPA update (10) | `RTM_v2.md`, `RISK_REGISTER_v2.md`, CAPA table | Done for the Billing and Payment rows; teammates update their own rows |

## 3. What the team still has to do before submission

1. **Hold the Fagan inspection meeting** with an inspector from the paired team, fill in
   `FAGAN_INSPECTION.md` section 7 (names, date, findings, signatures), and update the CAPA table.
   Nothing in that section is pre-filled on purpose.
2. **Create `develop`, open the pull request, get a review and tag `v0.2.0-m2-billing`**; protect the
   branches on GitHub (steps in `SCM_QUALITY_GATES.md`). Confirm the first GitHub Actions run is green.
3. **Run the demo once on the real PostgreSQL** (README, section "Run against PostgreSQL") and look at the tables in
   pgAdmin (activity VAL-04).
4. **Every member submits the confidential peer evaluation**, including a self-evaluation (guidelines section 7).
5. **Review the AI usage log** (`docs/AI_USAGE_LOG_M2.md`) and be ready to explain each item; the guidelines
   reduce individual marks for work that cannot be explained.
6. **Export the reports to PDF** for the Teams submission (the guidelines ask for PDF plus the repository link).
7. Bareera and Zouhair update their own RTM rows and risks (R-01, R-04 to R-07).

## 4. Honest limitations

* The payment gateways are simulations. Verification of signature checking, expiry and duplicate
  handling is real; behaviour of the real providers is not claimed.
* The Attendance module belongs to a teammate. The shared requirement QA-CONC-01 is verified here for
  the Billing and Payment side only.
* The inspection findings in `FAGAN_INSPECTION.md` section 5 come from the author's preparation review
  with AI assistance; the inspectors may confirm, change or reject them.
* The mutation check was three injected faults, not a full mutation-testing tool run.
* Three design weaknesses are deferred to Milestone 3 and recorded as risks (R-09, R-10) and as
  DEF-05, DEF-07, DEF-10.

## 5. Reproduce every number

```bash
mvn clean verify                           # 322 tests, coverage gate, SpotBugs gate
mvn checkstyle:checkstyle                  # style and complexity report
mvn clean test jacoco:report -Dtest='BasisPath*Test'    # branch coverage of the two analysed methods
```

## 6. Index of quality documents (`docs/sqa/`)

`SQA_M2_REPORT.md` (this file), `FAGAN_INSPECTION.md`, `STATIC_ANALYSIS_REPORT.md`,
`VV_PLAN_IEEE1012.md`, `BLACK_BOX_TEST_DESIGN.md`, `WHITE_BOX_ANALYSIS.md`, `SCM_QUALITY_GATES.md`,
`RTM_v2.md`, `RISK_REGISTER_v2.md`. Related: `docs/THREAD_SAFETY_STRATEGY.md`,
`docs/PERSISTENCE_AND_BOUNDARIES.md`, `docs/AI_USAGE_LOG_M2.md`.
