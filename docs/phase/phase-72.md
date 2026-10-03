# Phase 72: CML Failure Model

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
[design](../design/failure-model-abi-lowering.md) record the authored additive
`cozy.failure-model.v1` sidecar/self-contained Scala metadata, trailing defaulted
callers, explicit library rejection and generated-consumer scenarios. S72.2
focused validation passed 105/105 tests across six suites; actual Scala 3.3.8
normal/value/mixed generated-consumer groups (2/2/5 sources) compiled successfully.
Independent protected ABI review is PASS with zero current blockers. Its parent
manual local Step commit remains pending. The parent admitted the four-route legacy baseline before ABI
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

## Completion

Executable examples demonstrate that a CML component can select an Execution Model, refine failures at Component/Service/Operation scopes, and produce the expected effective failure contract.
