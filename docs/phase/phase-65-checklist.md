# Phase 65 Checklist: CBD Support Dart Model and API Code Generation

Status: IN_PROGRESS
phase=[Phase 65](phase-65.md)

S65.1A contract ready for parent validation and independent review:
[specification](../spec/dart-model-api-generation.md) and
[design/source inventory](../design/dart-model-api-generation.md).
Independent acceptance and the parent's manual Step gate are pending; all
eight original items below remain unchecked. This note records documentary
progress and does not claim generation, executable validation or live acceptance.

| Internal Step | Original checklist group | Original Phase outcomes | Documentary evidence locator |
| --- | --- | --- | --- |
| S65.1 / S65.1A | DART-01 | DART-01, DART-02 | Spec sections 1–12; design canonical inventory and consumer boundary; pending independent acceptance. |
| S65.2 | DART-02 | DART-03, DART-04, DART-05 | Spec sections 3–8 and DA-01..12; executable evidence planned. |
| S65.3 | DART-03 | DART-06, DART-07 | Spec sections 8–11 and DA-13..19; package/API/compatibility evidence planned. |
| S65.4 | DART-04 | DART-08, DART-09 | Spec real CBD acceptance and DA-20..21; design acceptance route; live consumer/reuse/release evidence planned. |

## DART-01: Inventory and mapping

- [ ] Inventory canonical Cozy model/operation IR and the Textus CBD Support client-contract boundary.
- [ ] Freeze deterministic null-safe Dart type, naming, package, and import mappings.

## DART-02: Model and serialization generation

- [ ] Generate immutable typed Dart models, enums, and closed variants where source closure exists.
- [ ] Generate JSON codecs that preserve nullability, discriminators, and representative round trips.

## DART-03: API boundary and package

- [ ] Generate typed request/response/error DTOs and operation interfaces from admitted operation metadata.
- [ ] Emit an analyzer-clean, consumable Dart package deterministically.

## DART-04: CBD Support acceptance

- [ ] Prove a real Textus CBD Support consumer path reuses generated contracts without a shadow model.
- [ ] Complete focused validation, review, release closure, and reproducible evidence for this Phase only.
