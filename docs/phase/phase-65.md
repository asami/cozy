# Phase 65: CBD Support Dart Model and API Code Generation

Status: PLANNED

Plan date: 2026-09-15
Rebased from the GitHub Phase 55 planning record: 2026-09-17
Primary downstream consumer: Textus CBD Support

## Purpose

Add a general Dart target to Cozy that projects canonical model and operation
IR into compilable, null-safe Dart model and API-boundary code for Textus CBD
Support. The target lets the CBD Support client use the same modeled contracts
as its Scala/CNCF-side counterparts where those contracts are available.

This is a reusable client-contract foundation, not a Flutter widget generator
or a CBD Support application implementation. Consumer-specific names and
handwritten transport behavior remain outside generator implementation.

The linked Dart proposal and KnowledgeHub journal are retained as the imported
GitHub planning input. This Phase supersedes their active Phase 55 assignment
and KnowledgeHub-specific initial-consumer choice: the current planned target
is Textus CBD Support, while completed local Phase 55 remains the site-context
media-registration closure.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| DART-01 | Inventory Cozy IR, existing generation targets, operation metadata, and CBD Support consumer contracts without creating a shadow Dart IR. | planned |
| DART-02 | Freeze deterministic Dart mappings for required/optional values, identifiers, collections, enums, variants, naming, packages, and imports. | planned |
| DART-03 | Generate immutable, typed Dart model classes with explicit required/optional constructor semantics. | planned |
| DART-04 | Generate enums and sealed variants wherever the source guarantees closure. | planned |
| DART-05 | Generate JSON codecs that preserve nullability, discriminators, names, and round-trip semantics. | planned |
| DART-06 | Generate typed request/response/error DTOs and operation interfaces from canonical operation metadata. | planned |
| DART-07 | Emit deterministic, consumable Dart package layouts with analyzer-clean output. | planned |
| DART-08 | Prove one real Textus CBD Support consumer path uses generated contracts. | planned |
| DART-09 | Document reuse by later CBD Support UI work and prohibit a parallel shadow model. | planned |

## Required boundaries

- Required and optional source values map to Dart null safety; domain models do
  not degrade into untyped `dynamic` maps.
- Closed source variants retain available exhaustiveness information; open
  extension points are not falsely declared closed.
- Generated API interfaces are typed boundaries, not a required concrete
  transport implementation.
- Phase 54 semantic metadata remains the supplier for rich CBD Support
  semantics; this Phase projects admitted model and operation contracts into
  Dart and must not invent missing semantics.

## Acceptance

- Cozy emits compilable, analyzer-clean Dart from representative canonical IR.
- Type mappings, JSON round trips, enum/variant handling, and typed API DTOs
  preserve the available source semantics.
- The generated package is consumable by a Textus CBD Support client path.
- Later CBD Support UI work reuses the generated model/API layer instead of
  creating a parallel client-local model.

## Non-goals

- Flutter widget generation, state-management, navigation, camera/platform
  plugins, local database work, or synchronization implementation.
- CBD Support-specific handwritten client behavior or arbitrary Dart source
  merge/round-trip editing.
- Replacing Phase 54 as the authority for semantic metadata.

## References

- [Phase 65 checklist](phase-65-checklist.md)
- [Dart model/API generation proposal](../notes/dart-model-api-code-generation-provisional-specification.md)
- [Dart consumer planning record](../journal/2026/09/2026-09-15-dart-code-generation-knowledgehub-consumer.md)
- [Phase 54](phase-54.md)
