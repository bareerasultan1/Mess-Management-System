# AI Usage Log: Milestone 2 (Billing and Payment modules)

Required by Joint Project Guidelines section 9. Owner: Mahvash Imran (2023294).
Complete the "team review" notes yourself before submission.

| ID | Tool and model | Purpose | Prompt / activity summary | AI material adopted | Modifications made | Verification performed |
| --- | --- | --- | --- | --- | --- | --- |
| AI-M2-001 | Gemini / Antigravity | Milestone 2 roadmap | Generated the 7-day plan from the M1 documents and M2 rubric | 7-day roadmap, TDD sequence, SQA test matrix | Tailored to GIKI mess rules and the repository branch | Checked against the joint guidelines |
| AI-M2-002 | Gemini / Antigravity | Pattern skeletons | Scaffolded `IPaymentProcessor` and `AttendanceObserver` interfaces | Interface outlines | Added validation, exceptions and locks | Unit tests |
| AI-M2-003 | Gemini / Antigravity | CFG and complexity | Derived cyclomatic complexity for the billing calculation | CFG node structure, basis paths | Recomputed against the real code | Matching JUnit tests |
| AI-M2-004 | Claude (Claude Code, Sonnet 5.5) | Review of roadmap vs. guidelines | Read the guidelines, M1 reports and roadmap; listed gaps and inconsistencies | List of gaps (money as double, 50,000 vs 100,000 limit, missing reports and AI log) | Decisions on rules taken by the owner | Cross-checked against the guideline PDFs |
| AI-M2-005 | Claude (Claude Code, Sonnet 5.5) | Billing engine (TDD) | Implemented `Money`, `BillingServiceImpl`, strategies, itemized invoice with red/green commits | Code and tests | Business rules chosen by owner (80% rebate, 5% fine, 100 PKR admin charge) | 67 passing tests at that point, JaCoCo report |
| AI-M2-006 | Claude (Claude Code, Sonnet 5.5) | Payment engine (TDD) | Implemented factory, three simulated gateways with HMAC-SHA256, `PaymentServiceImpl` | Code and tests | Gateways are labelled simulated; limits and expiry rules fixed by owner | 104 passing tests |
| AI-M2-007 | Claude (Claude Code, Sonnet 5.5) | Concurrency and Observer (TDD) | Locked ledger reads, `IdempotencyManager`, thread-safe `Bill`, `AttendanceSubject`/`BillingObserver`, stress tests, thread-safety document | Code, tests, `docs/THREAD_SAFETY_STRATEGY.md` | Owner to review and be able to explain every lock choice | Stress tests repeated 25 times without failure; failing-first runs showed the original races (10 of 10 duplicate callbacks settled; bill lost payments) |
| AI-M2-008 | Claude (Claude Code, Sonnet 5.5) | Persistence, validation, integration (TDD) | Ledger/bill reconciliation, SQLite schema and repositories with a shared contract test, boundary validator, structured API errors and masked audit log, composition root, end-to-end test, JaCoCo gate, CI workflow | Code, tests, `docs/PERSISTENCE_AND_BOUNDARIES.md`, README section | Owner chooses limits (50,000 PKR, id format) and must be able to explain the identity map and transactions | 235 passing tests, 91% line coverage, gate shown to fail at a 99% threshold, end-to-end restart test |

Team review of AI output is still pending: each member must be able to explain the code attributed to them (guidelines section 9).
