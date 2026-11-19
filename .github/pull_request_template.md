## What this change does

<!-- One or two sentences. Link the requirement ids (FR-002, FR-003, QA-xx) in docs/sqa/RTM_v2.md. -->

## Quality checklist (all must be ticked before merging)

- [ ] Tests were written first: the history shows a `[RED]` commit before the `[GREEN]` commit
- [ ] `mvn clean verify` passes locally (tests, 60% coverage gate, SpotBugs gate)
- [ ] The GitHub `CI` check is green
- [ ] `mvn checkstyle:checkstyle` reports no new findings
- [ ] The RTM (`docs/sqa/RTM_v2.md`) and risk register were updated if behaviour or risk changed
- [ ] Any AI assistance is recorded in `docs/AI_USAGE_LOG_M2.md`
- [ ] A reviewer who is **not** the author has approved this pull request
- [ ] Merged with a merge commit, not squashed (the red-green history must be kept)

## Reviewer notes

<!-- Defects found, questions, decisions. This conversation is part of the SCM evidence. -->
