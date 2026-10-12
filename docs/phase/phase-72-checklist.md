# Phase 72 checklist: CML Failure Model

This ledger belongs only to Phase 72. Authored artifacts are recorded separately
from acceptance. The parent owns validation, independent review and manual local
commits. No stage closure is implied by the presence of source files.

## S72.1 SOURCE

Stage Status:
- Current status: CLOSED
- Owner: Phase 72 parent with bounded SOURCE implementation editor
- Update rule: Record artifact presence when authored; record acceptance only from parent evidence.
- Closure basis: All S721 items below.

- [x] S721-01 Source specification and design authored in `docs/spec/failure-model-source-contract.md` and `docs/design/failure-model-source-lowering.md`.
- [x] S721-02 Immutable source contract and original-tree normalizer authored in `FailureModel.scala` and `FailureModelCml.scala`; ModelBuilder admission hook authored.
- [x] S721-03 Real local single-user/single-writer fixture and Given/When/Then plus ScalaCheck executable scenarios authored in `failure-model.cml` and `FailureModelCmlSpec.scala`.
- [x] S721-04 Selected focused SOURCE validation accepted by parent: three suites, 97/97 tests; SBT and wrapper exit 0, serial lock released.
- [x] S721-05 Independent PLAN-selected SOURCE acceptance review closed: initial protected review followed by independent focused repair closure; CPB-P72-S721-001 and CPB-P72-S721-002 resolved, no new blockers.
- [x] S721-06 Parent manual local SOURCE Step commit recorded: `6c15b65cd83181fdf61aa8674c9a847f79751805`; ABI baseline receipt ties successful terminal four-route generation and released serial lock to this accepted SOURCE commit.

## S72.2 ABI

Stage Status:
- Current status: CLOSED
- Owner: Phase 72 parent
- Update rule: Begin after SOURCE acceptance; record generated-contract and consumer evidence.
- Closure basis: All S722 items below.

- [x] S722-01 Additive ordered source-correlated generated Failure Model metadata authored in `FailureModelAbiGenerator.scala`; exact contract/design in `docs/spec/failure-model-abi-contract.md` and `docs/design/failure-model-abi-lowering.md`; real normal/value/mixed Given/When/Then scenarios and typed consumer materialization authored in `FailureModelAbiGenerationSpec.scala`.
- [x] S722-02 No-declaration compatibility and explicit library-target policy specified and implemented with trailing defaulted callers; independent parent-owned `failure-model-legacy-baseline.json` admitted before ABI editing from SOURCE commit `6c15b65cd83181fdf61aa8674c9a847f79751805`, four routes/80 files. Full-byte comparison, library rejection, old callers, escaping/optional provenance and seeded 100-sample executable scenarios accepted through the validation/review evidence below.
- [x] S722-03 Focused validation accepted: six suites, 105/105 tests; real Scala 3.3.8 generated-consumer groups normal/value/mixed (2/2/5 sources) compiled successfully; SBT and wrapper exit 0, serial locks released.
- [x] S722-04 Independent PLAN-selected protected ABI acceptance review PASS; complete producer/caller/generated-output boundary accepted with zero current blockers.
- [x] S722-05 Parent manual local ABI Step commit recorded: `1d9c80775e74f7d0ba0dd60f1cf4730ef84c3843`.

## S72.3 HANDOFF

Stage Status:
- Current status: CLOSED
- Owner: Phase 72 parent
- Update rule: Begin after ABI acceptance; record recipient-ready authority and examples.
- Closure basis: All S723 items below.

- [x] S723-01 [Authored CNCF91 recipient handoff](phase-72-cncf-handoff.md) records canonical type, validation and runtime resolution responsibility with accepted producer commits, actual fixture oracle and reproduction/acceptance instructions; receiver integration remains pending.
- [x] S723-02 [Authored sm-workflow handoff](phase-72-cncf-handoff.md#sm-workflow-guidance-after-receiver-acceptance) records recipient-resolved implementation constraints and effective-owner OUT_OF_SCOPE semantics; CNCF acceptance is a prerequisite and workflow/AI wiring remains unverified.
- [x] S723-03 Parent class-D static validation accepted; independent PLAN-selected HANDOFF Step review PASS with zero blockers and no new findings.
- [x] S723-04 Parent manual local HANDOFF Step commit recorded: `783a48552de5fa6a4b34a8dfe9c1184f005de62f`.

The parent materialized both accepted nonblocking maintenance records in the
[hygiene follow-up](../journal/2026/10/2026-10-03-phase-72-hygiene-follow-up.md).
They remain OPEN for separate maintenance, with no program repair in HANDOFF.

## Final Phase acceptance and release

Stage Status:
- Current status: CLOSED
- Owner: Phase 72 parent
- Update rule: Update only against accepted Step and final gate evidence for the attributable PLAN epoch.
- Closure basis: All P72 items below and every Step acceptance item above.

- [x] P72-01 Sole P72-FULL-REVIEW-001 revision1 for P72-PLAN-E1 passed; zero current blockers.
- [x] P72-02 Ordinary final full Cozy validation accepted.
- [x] P72-03 Phase/checklist/index/strategy and accepted journal closure synchronized.
- [x] P72-04 Actionable CNCF91 and sm-workflow recipient handoff accepted through HANDOFF and sole full review; receiver integration remains recipient-owned.
- [x] P72-05 Distinct final parent manual local release commit recorded.

Final validation: P72-FINAL-VAL-001 passed: 2,688 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 186 suites, 0 aborted; SBT/wrapper exit 0 and `lock=released`. The nine cancellations match the previously accepted baseline; the affected specifications are unchanged from the original Phase 72 base. All 1,557 declared tracked validation inputs and Gitlinks remain unchanged. The containing distinct parent manual local release commits this canonical closure and both exact accepted Hygiene records; no Development Candidate or empty journal is added.
