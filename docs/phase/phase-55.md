# Phase 55 - Dart Model and API Code Generation

Status: PLANNED
planned_at=2026-09-15
consumer=KnowledgeHub Phase 1 / Textus Flutter Core / NICT Editing Studio App
relationship=Complements Phase 50-53 Flutter UI projection roadmap

## Purpose

Add a general Dart target to Cozy that projects canonical model / operation IR into compilable, null-safe Dart model and API boundary code.

Phase 55 intentionally precedes broad Flutter widget generation for the KnowledgeHub consumer. It establishes model continuity first so that Scala/CNCF server contracts and Dart/Flutter client contracts are derived from the same modeling source where applicable.

`asami/textus-flutter-core` is treated as a reusable Flutter client foundation rather than a mobile application product, leaving iPhone / Android / Web / Desktop projection open.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| DART-01 | IR and target inventory | Existing Cozy IR, Scala/codegen targets, operation metadata, Phase 50-53 UI IR, and consumer contracts are inventoried; no shadow Dart IR is introduced unnecessarily. | planned |
| DART-02 | Dart type mapping contract | Required/optional scalars, identifiers, collections, enum, closed variants, values/entities, naming, and package/import rules map deterministically to Dart. | planned |
| DART-03 | Immutable model generation | Representative IR emits typed Dart classes with final fields, constructors, equality strategy decision, and no domain `dynamic` maps. | planned |
| DART-04 | Enum and sealed variant generation | Closed enumerations and sum/variant types emit Dart constructs supporting exhaustive handling where the source model guarantees closure. | planned |
| DART-05 | JSON codec generation | Generated encode/decode preserves nullability, discriminator, field naming, and representative round-trip semantics. | planned |
| DART-06 | API DTO and operation contract generation | Request/response/error/result DTOs and typed operation interfaces are generated from canonical operation metadata where available. | planned |
| DART-07 | Dart package emission | Generator emits a consumable Dart/Flutter package layout with deterministic files/imports and analyzer-clean output. | planned |
| DART-08 | KnowledgeHub consumer acceptance | Textus Flutter Core or NICT Editing Studio consumes generated representative contracts against the KnowledgeHub Phase 1 boundary. | planned |
| DART-09 | Flutter roadmap integration handoff | Reuse points with Logical UI / Phase 50-53 are documented; duplicate model generation in future Flutter UI output is prohibited. | planned |

## Planning rule

Each subphase should remain within approximately six hours of focused implementation once prerequisites are available. Split a stage before implementation if inventory shows it exceeds that size.

## Detailed requirements

### DART-01 — Inventory

Inspect at least:

- current model/code generation architecture
- canonical IR used by Scala or other targets
- operation/API metadata
- Phase 50 Logical UI model
- Phase 51-53 Flutter projection plans
- nullability representation
- enum / generalization / trait / powertype representation relevant to client contracts
- KnowledgeHub Phase 1 candidate shared contracts

### DART-02 — Type mapping

Freeze deterministic mappings before broad generator implementation.

Examples:

```text
required string -> String
optional string -> String?
required int -> int
optional int -> int?
boolean -> bool
closed enum -> enum
closed variants -> sealed hierarchy
collection -> typed List<T> or declared projection
```

Avoid `dynamic` except at explicitly untyped external boundaries.

### DART-03 — Immutable model

Prefer generated value-oriented Dart:

- `final` class where extension is not intended
- `final` fields
- `const` constructor where semantically valid
- explicit required/optional constructor parameters

Do not introduce mutable setters merely for serializer convenience.

### DART-04 — Closed variants

Where Cozy IR knows a type family is closed, preserve that knowledge so Dart switch/pattern handling can benefit from exhaustiveness.

Do not claim closure for open extension points.

### DART-05 — JSON codecs

At minimum test:

- required fields
- optional/null fields
- nested values
- collections
- enum
- sealed variant discriminator
- unknown/forward compatibility policy decision

### DART-06 — API contracts

Generate typed boundaries, not necessarily the concrete transport implementation.

```text
abstract interface class KnowledgeHubClient {
  Future<...> operation(...);
}
```

Exact shape follows the existing Cozy operation IR rather than a hard-coded KnowledgeHub client.

### DART-07 — Package emission

Generated output should be directly consumable by a Dart/Flutter project and validate with the Dart analyzer/compiler.

### DART-08 — Consumer acceptance

Use a small but real KnowledgeHub Phase 1 contract. Acceptance must cross generated code into either:

- `asami/textus-flutter-core`, or
- `KnowledgeHubProject/nict-editing-studio-app`

and match the server-side declared contract.

Consumer-specific names must not be embedded in generator implementation.

### DART-09 — UI integration handoff

Document how future Flutter UI generation references generated model/API code instead of re-emitting separate UI-local data classes.

## Acceptance

- Cozy emits compilable/analyzer-clean Dart from representative canonical IR.
- required/optional semantics map correctly to Dart null safety.
- generated models use explicit typed fields rather than domain `dynamic` maps.
- enum and closed variants preserve available exhaustiveness semantics.
- JSON round-trip tests pass for representative structures.
- typed request/response/API contracts are generated where operation metadata exists.
- output can be packaged and consumed by a Flutter project.
- a KnowledgeHub Phase 1 consumer uses at least one generated real contract.
- Phase 50-53 Flutter UI work can reuse the generated model/API layer without a parallel shadow model.

## Non-Goals

- full Flutter widget generation.
- Flutter state management framework selection.
- navigation/router generation.
- camera or platform plugin generation.
- local database generation.
- synchronization implementation.
- KnowledgeHub-specific handwritten client behavior.
- arbitrary Dart source merge/round-trip editing.

## References

- `docs/notes/dart-model-api-code-generation-provisional-specification.md`
- `docs/journal/2026/09/2026-09-15-dart-code-generation-knowledgehub-consumer.md`
- `docs/phase/phase-50.md`
- `docs/phase/phase-51.md`
- `docs/phase/phase-52.md`
- `docs/phase/phase-53.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
