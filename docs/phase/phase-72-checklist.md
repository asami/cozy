# Phase 72 checklist: CML Failure Model

This ledger belongs only to Phase 72. Authored artifacts are recorded separately
from acceptance. The parent owns validation, independent review and manual local
commits. No stage closure is implied by the presence of source files.

## S72.1 SOURCE

Stage Status:
- Current status: IN_PROGRESS
- Owner: Phase 72 parent with bounded SOURCE implementation editor
- Update rule: Record artifact presence when authored; record acceptance only from parent evidence.
- Closure basis: All S721 items below.

- [x] S721-01 Source specification and design authored in `docs/spec/failure-model-source-contract.md` and `docs/design/failure-model-source-lowering.md`.
- [x] S721-02 Immutable source contract and original-tree normalizer authored in `FailureModel.scala` and `FailureModelCml.scala`; ModelBuilder admission hook authored.
- [x] S721-03 Real local single-user/single-writer fixture and Given/When/Then plus ScalaCheck executable scenarios authored in `failure-model.cml` and `FailureModelCmlSpec.scala`.
- [x] S721-04 Selected focused SOURCE validation accepted by parent: three suites, 97/97 tests; SBT and wrapper exit 0, serial lock released.
- [x] S721-05 Independent PLAN-selected SOURCE acceptance review closed: initial protected review followed by independent focused repair closure; CPB-P72-S721-001 and CPB-P72-S721-002 resolved, no new blockers.
- [ ] S721-06 Parent manual local SOURCE Step commit recorded.

## S72.2 ABI

Stage Status:
- Current status: OPEN
- Owner: Phase 72 parent
- Update rule: Begin after SOURCE acceptance; record generated-contract and consumer evidence.
- Closure basis: All S722 items below.

- [ ] S722-01 Additive ordered source-correlated generated Failure Model metadata authored.
- [ ] S722-02 No-declaration compatibility and explicit library-target policy specified and implemented.
- [ ] S722-03 Focused validation and genuine generated-consumer compilation accepted.
- [ ] S722-04 Independent PLAN-selected ABI acceptance review closed.
- [ ] S722-05 Parent manual local ABI Step commit recorded.

## S72.3 HANDOFF

Stage Status:
- Current status: OPEN
- Owner: Phase 72 parent
- Update rule: Begin after ABI acceptance; record recipient-ready authority and examples.
- Closure basis: All S723 items below.

- [ ] S723-01 CNCF91 recipient handoff records canonical type, validation and runtime resolution responsibility with producer comparison evidence.
- [ ] S723-02 sm-workflow handoff records recipient-resolved implementation constraints and authored OUT_OF_SCOPE semantics.
- [ ] S723-03 Focused handoff validation and independent selected acceptance closed.
- [ ] S723-04 Parent manual local HANDOFF Step commit recorded.

## Final Phase acceptance and release

Stage Status:
- Current status: OPEN
- Owner: Phase 72 parent
- Update rule: Update only against accepted Step and final gate evidence for the attributable PLAN epoch.
- Closure basis: All P72 items below and every Step acceptance item above.

- [ ] P72-01 Exactly one comprehensive Phase review for the attributable PLAN epoch closed.
- [ ] P72-02 Ordinary final full Cozy validation accepted.
- [ ] P72-03 Phase/checklist/index/strategy and accepted journal closure synchronized.
- [ ] P72-04 Actionable CNCF91 and sm-workflow recipient handoff accepted.
- [ ] P72-05 Distinct final parent manual local release commit recorded.
