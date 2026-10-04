# Phase 72: CML Failure Model

Status: COMPLETE at the Cozy producer boundary.

## Goal

Add CML authoring and generation support for Execution Model-derived Failure Models.

## Scope

- Add Failure Model declarations to Component, Service, and Operation.
- Support inherited defaults from Execution Model.
- Support explicit IN_SCOPE / OUT_OF_SCOPE refinement.
- Generate metadata sufficient for deterministic ResolvedFailureModel construction.
- Preserve traceability from CML declarations to CAR development metadata.
- Make the resolved model available to downstream implementation workflows.
- Add validation for contradictory or invalid refinements.
- Add examples including local single-user/single-writer components.

## Integration

CNCF Phase 91 defines the model and resolution semantics. sm-workflow consumes the resolved result as an AI implementation constraint.

## Steps and acceptance

The [Phase 72 checklist](phase-72-checklist.md) records closure evidence. The
original completion criterion below remains the Phase contract.

| Step | Slice | Observable outcome |
|---|---|---|
| S72.1 | SOURCE | Strict authored grammar, immutable defaults/deltas, source identities and deterministic effective development projection; actual local executable examples |
| S72.2 | ABI | Additive generated metadata and recipient compilation, with no-declaration compatibility and explicit library policy |
| S72.3 | HANDOFF | Actionable CNCF91 and sm-workflow handoff using the accepted producer/recipient boundary |

The SOURCE [spec](../spec/failure-model-source-contract.md) and
[design](../design/failure-model-source-lowering.md) fix authoring and lowering.
S72.1 is CLOSED on parent manual local SOURCE commit
`6c15b65cd83181fdf61aa8674c9a847f79751805`, following accepted focused validation
and independent repair closure. The ABI
[spec](../spec/failure-model-abi-contract.md) and
[design](../design/failure-model-abi-lowering.md) record the accepted additive
`cozy.failure-model.v1` sidecar/self-contained Scala metadata, trailing defaulted
callers, explicit library rejection and generated-consumer scenarios. S72.2
focused validation passed 105/105 tests across six suites; actual Scala 3.3.8
normal/value/mixed generated-consumer groups (2/2/5 sources) compiled successfully.
Independent protected ABI review is PASS with zero current blockers. Its parent
manual local ABI Step commit is `1d9c80775e74f7d0ba0dd60f1cf4730ef84c3843`,
closing S72.2. S72.3 is CLOSED at HANDOFF commit
`783a48552de5fa6a4b34a8dfe9c1184f005de62f`: the
[CNCF91 and sm-workflow handoff](phase-72-cncf-handoff.md) is accepted with
the actual fixture oracle and recipient reproduction/acceptance instructions.
Its parent static validation is accepted and independent selected Step review
is PASS with zero blockers. The sole Phase-wide review is also PASS;
Final full validation passed; the containing distinct parent manual local release commit closes Cozy Phase 72.
receiver/runtime integration and workflow/AI wiring are
recipient-owned prerequisites, not completed Cozy work.
The parent admitted the four-route legacy baseline before ABI
editing; its read-only fixture retains all 80 original generated files.
Cozy's effective contract is development metadata for comparison. CNCF owns the
canonical runtime ResolvedFailureModel and recipient validation; sm-workflow
consumes that resolved result. Execution Model names imply no failure defaults,
locks, retries, rollback, hashes, durability or distributed guarantees.

Each dependency-ready Step requires focused validation, independent selected
acceptance and a parent manual local commit. Final Phase closure requires exactly
one comprehensive review per attributable PLAN epoch, ordinary full Cozy
validation, canonical Phase/checklist/index/strategy and journal closure, recipient
handoff and a distinct parent manual local release commit. These gates remain
pending until evidence is recorded in the checklist.

Accepted nonblocking maintenance is recorded in the
[hygiene follow-up](../journal/2026/10/2026-10-03-phase-72-hygiene-follow-up.md)
and remains outside this Phase's implementation boundary.

## Completion

Executable examples demonstrate that a CML component can select an Execution Model, refine failures at Component/Service/Operation scopes, and produce the expected effective failure contract.

## Final validation and closure

P72-FINAL-VAL-001 passed: 2,688 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 186 suites, 0 aborted; SBT/wrapper exit 0 and `lock=released`. Nine cancellations retain the existing four opt-in Docker Remotion integrations and five CV/SP ownership scenarios; the affected specifications are byte-identical to the original Phase 72 base.

All three Steps have independent accepted reviews and parent manual local commits. The sole comprehensive high review P72-FULL-REVIEW-001 revision 1 for P72-PLAN-E1 passed with zero current blockers. This factual closure preserves its normative source/ABI contracts, executable examples and receiver obligations. All 1,557 declared tracked validation inputs and Gitlinks remain unchanged; the original native full-suite receipt is reused for these documentary result projections. Version 0.3.3-SNAPSHOT remains unchanged.

The containing distinct parent manual local release commit synchronizes the Phase, checklist, Phase index, strategy, source/ABI status, recipient handoff and both exact accepted Hygiene records HYG-P72-S721-001 and HYG-P72-FULL-001. Those records remain OPEN for separate maintenance; no Development Candidate or empty journal is created. CNCF91 owns canonical recipient types, validation and runtime resolution; sm-workflow wiring depends on receiver acceptance. Cozy producer work is complete. No successor Phase is started.
