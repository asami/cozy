# Phase 65 Checklist: CBD Support Dart Model and API Code Generation

Status: IN_PROGRESS
phase=[Phase 65](phase-65.md)

S65.1 contract and source inventory accepted at native commit
`f4a6e3b0f41fd929fba085664ae958330b82cff0`, after independent review and M0 closure:
[specification](../spec/dart-model-api-generation.md) and
[design/source inventory](../design/dart-model-api-generation.md).
The first four original items below are complete. S65.2A model/JSON implementation
and the [data-interface specification](../../src/test/scala/cozy/modeler/DartModelGenerationSpec.scala)
and [emitted-runtime specification](../../src/test/scala/cozy/modeler/DartModelRuntimeSpec.scala)
provide executable evidence: Main and Test compilation passed, and
`testOnly cozy.modeler.DartModelGenerationSpec cozy.modeler.DartModelRuntimeSpec`
completed both suites with 21/21 tests passed, no skips or aborts, in post-repair
validation attempt 006. Actual emitted Dart SDK analysis/runtime and compiler
contract checks succeeded. Independent focused re-review 006 accepted the
complete Step after resolving `CPB-P65-S652-001`, the primitive member-name
collision admission defect. S65.2 is accepted for its local Step commit.
The remaining four items stay unchecked; package/API, live CBD acceptance, full repository validation and
Phase closure remain later obligations.

| Internal Step | Original checklist group | Original Phase outcomes | Documentary evidence locator |
| --- | --- | --- | --- |
| S65.1 / S65.1A | DART-01 | DART-01, DART-02 | Spec sections 1–12; design canonical inventory and consumer boundary; accepted native f4a6e3b after independent review and M0 closure. |
| S65.2 / S65.2A | DART-02 | DART-03, DART-04, DART-05 | Spec sections 3–8 and DA-01..12; both executable specs passed 21/21 in attempt 006 with actual emitted Dart SDK analysis/runtime and compiler contract checks; independent focused re-review 006 accepted the complete Step and resolved CPB-P65-S652-001. |
| S65.3 | DART-03 | DART-06, DART-07 | Spec sections 8–11 and DA-13..19; package/API/compatibility evidence planned. |
| S65.4 | DART-04 | DART-08, DART-09 | Spec real CBD acceptance and DA-20..21; design acceptance route; live consumer/reuse/release evidence planned. |

## DART-01: Inventory and mapping

- [x] Inventory canonical Cozy model/operation IR and the Textus CBD Support client-contract boundary.
- [x] Freeze deterministic null-safe Dart type, naming, package, and import mappings.

## DART-02: Model and serialization generation

- [x] Generate immutable typed Dart models, enums, and closed variants where source closure exists.
- [x] Generate JSON codecs that preserve nullability, discriminators, and representative round trips.

## DART-03: API boundary and package

- [ ] Generate typed request/response/error DTOs and operation interfaces from admitted operation metadata.
- [ ] Emit an analyzer-clean, consumable Dart package deterministically.

## DART-04: CBD Support acceptance

- [ ] Prove a real Textus CBD Support consumer path reuses generated contracts without a shadow model.
- [ ] Complete focused validation, review, release closure, and reproducible evidence for this Phase only.
