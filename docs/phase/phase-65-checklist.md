# Phase 65 Checklist: CBD Support Dart Model and API Code Generation

Status: PLANNED
phase=[Phase 65](phase-65.md)

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
