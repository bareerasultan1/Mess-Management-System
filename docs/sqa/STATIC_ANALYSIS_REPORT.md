# Static Analysis Report

SE431 Milestone 2. Owner: Mahvash Imran (2023294). Code analysed: the whole production source of the
Billing and Online Payment modules, `src/main/java` (about 3,800 non-blank lines, 77 classes).
Snapshot: commit `6b2a6fc`, 2026-11-19.

## 1. Tools and configuration

| Tool | Version | Configuration | Run with |
| --- | --- | --- | --- |
| Checkstyle (style, readability, complexity) | 9.3 through `maven-checkstyle-plugin` 3.3.1 | `config/checkstyle.xml` | `mvn checkstyle:checkstyle` |
| SpotBugs (likely bugs in the compiled code) | SpotBugs 4.8.x through `spotbugs-maven-plugin` 4.8.6.2, effort Max, threshold Medium | `config/spotbugs-exclude.xml` | `mvn compile spotbugs:check` |
| JaCoCo (coverage) | 0.8.12 | `pom.xml` | `mvn verify` |

`config/checkstyle.xml` is Checkstyle's own `google_checks.xml` with three documented deviations:
4-space indentation (the project convention, Google uses 2), 120-column lines (Google uses 100), and one
added rule, `CyclomaticComplexity <= 10` per method (maintainability target of the M1 quality plan).
With the unmodified Google ruleset the first run reported **2,077** findings, of which 1,812 were only
indentation, so that ruleset was judged unsuitable for this code base before any triage.

SonarQube was not used (no server available); Checkstyle and SpotBugs are real analysers, as the
guidelines require ("a real static-analysis tool").

## 2. Results at a glance

| | First run | After corrective action |
| --- | --- | --- |
| Checkstyle (tailored ruleset) | 217 findings | 0 reported, 7 documented suppressions |
| SpotBugs | 23 findings | 0 reported, 7 documented exclusions |
| Triage | | Checkstyle: 210 fixed, 7 justified, 0 deferred. SpotBugs: 16 fixed, 7 justified, 0 deferred |

Tests were run after every batch of changes; all stayed green. The final build is 322 tests,
0 failures, 93.4% line coverage, 77.9% branch coverage.

## 3. Checkstyle triage (217 findings)

| Rule | Count | Disposition | Corrective action |
| --- | --- | --- | --- |
| JavadocParagraph | 77 | Fixed | `<p>` tags added to multi-paragraph comments |
| MissingJavadocMethod | 28 | Fixed | one-sentence Javadoc added to each public method or constructor |
| LeftCurly / RightCurlyAlone | 27 + 27 | Fixed | one-line getters in the Day 1 model classes reformatted |
| EmptyLineSeparator | 23 | Fixed | blank lines between members |
| CyclomaticComplexity (> 10) | 10 | Fixed | long methods split (see section 5); validation chains moved into `Guard` helpers |
| LineLength (> 120) | 8 | Fixed | SQL strings and conditions wrapped |
| SummaryJavadoc | 4 | Fixed | missing full stops, one single-line Javadoc rewritten |
| AtclauseOrder | 3 | Fixed | `Optional<Bill>` in `@return` was parsed as HTML; now `{@code Optional<Bill>}` (a real Javadoc defect) |
| MissingJavadocType | 1 | Fixed | class comment added to `BillingServiceImpl` |
| RequireEmptyLineBeforeBlockTagGroup, SingleLineJavadoc | 1 + 1 | Fixed | |
| AbbreviationAsWordInName | 5 | **Justified** | `IRepository`, `IBillRepository`, `ILedgerRepository`, `IPaymentRepository`, `IPaymentProcessor`: the `I` prefix for interfaces is the naming approved in the Milestone 1 design document and used consistently. Suppressed with `@SuppressWarnings("checkstyle:abbreviationaswordinname")` and a comment on each interface. |
| InterfaceTypeParameterName | 1 | **Justified** | `IRepository<T, ID>`: `ID` is the conventional name of the primary-key type; the rule wants `K`. Suppressed on that interface. |
| VariableDeclarationUsageDistance | 1 | **Justified** | `startedAt` in `settlePayment` must be captured at method entry to time the whole settlement, so it is declared far from its use. Suppressed with a comment. |

Total: 210 fixed + 7 justified = 217.

## 4. SpotBugs triage (23 findings)

| Pattern | Where | Count | Disposition | Action / reason |
| --- | --- | --- | --- | --- |
| CT_CONSTRUCTOR_THROW | `Bill`, `BillItem`, `StudentLedger`, `PaymentTransaction` (2), `AttendanceSubject`, `BillingObserver` | 7 | Fixed | classes whose constructors validate and throw (a half-built object could be revived by a malicious subclass finalizer) are now `final` |
| CT_CONSTRUCTOR_THROW | `AbstractSignedProcessor` | 1 | **Justified** | abstract base class cannot be `final`; all fields are final, the hierarchy is the three project gateways, no finalizer exists |
| EI_EXPOSE_REP / EI_EXPOSE_REP2 | `ItemizedInvoice` (4) | 4 | Fixed (real) | the invoice record exposed a mutable `Map` and `List`; it now stores immutable copies |
| EI_EXPOSE_REP | `BillingPaymentModule` getters | 4 | **Justified** | the composition root exists to hand out the wired collaborators |
| EI_EXPOSE_REP2 | `BillingPaymentApi`, `BillingServiceImpl` constructors | 2 | **Justified** | constructor injection stores the collaborator it is given (dependency inversion); these are services, not data |
| IS2_INCONSISTENT_SYNC | `Bill.restore` | 2 | Fixed (real) | two synchronized fields were written without the lock when a bill was loaded; now written inside `synchronized (bill)` |
| RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE | `calculateMonthlyBill` | 1 | Fixed (real) | `asOfDate != null` was always true because the argument was already validated: dead code removed |
| DM_NEXTINT_VIA_NEXTDOUBLE | `DemoRunner` | 1 | Fixed | `ThreadLocalRandom.nextInt` instead of `Math.random()` |
| NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `PostgresPaymentRepository.isIdempotencyViolation` | 1 | Fixed (real) | `getServerErrorMessage()` was called twice, the second time possibly on a null result; now read once |

Total: 16 fixed + 7 justified = 23. The seven justified items are the entries of
`config/spotbugs-exclude.xml`, each with its reason. `mvn verify` runs `spotbugs:check`, so a new
finding that is not a listed exclusion fails the build.

During the clean-up a first attempt to move null checks into a helper (`Guard.require`) made SpotBugs
report four new NP_NULL warnings, because it cannot see that the helper throws. Those checks were
restored to plain `if` statements; the analyser's feedback changed the design, which is the purpose
of running it.

## 5. Findings that led to design changes

* **Complexity.** Ten methods exceeded V(G) = 10. `BillingServiceImpl.calculateMonthlyBill` (14),
  `recordMealSwipe` (11), `calculateRebate` (11), `PaymentServiceImpl.initiatePayment` (14),
  `settlePayment` (10) and `BillingPaymentApi.toErrorInfo` (15) were split into small named methods;
  five constructors that only validate arguments now use the `Guard` helper. All 255 tests that existed at the time
  (the black-box and basis-path suites were added afterwards) stayed green after each step.
* **Real bugs found by the tools** (not only style): the mutable invoice collections, the unsynchronized
  write in `Bill.restore`, the dead null check, the possible null dereference in the database layer,
  and the unparsed generic types in Javadoc.

## 6. Open items

* Checkstyle is reported but not yet a build gate (its severity is "warning"). Making it a gate is a
  Milestone 3 option now that the count is zero.
* Static analysis cannot find the design weaknesses recorded as DEF-05, DEF-07 and DEF-10 in
  `FAGAN_INSPECTION.md`; those need review and concurrency testing, not a tool.

## 7. Reproduce

```bash
mvn checkstyle:checkstyle            # report: target/checkstyle-result.xml
mvn compile spotbugs:spotbugs        # report: target/spotbugsXml.xml
mvn clean verify                     # tests + coverage gate + SpotBugs gate
```
