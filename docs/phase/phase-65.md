# Phase 65: CBD Support Dart Model and API Code Generation

Status: IN_PROGRESS

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
| DART-01 | Inventory Cozy IR, existing generation targets, operation metadata, and CBD Support consumer contracts without creating a shadow Dart IR. | completed in accepted S65.1 |
| DART-02 | Freeze deterministic Dart mappings for required/optional values, identifiers, collections, enums, variants, naming, packages, and imports. | completed in accepted S65.1 |
| DART-03 | Generate immutable, typed Dart model classes with explicit required/optional constructor semantics. | completed in accepted S65.2 |
| DART-04 | Generate enums and sealed variants wherever the source guarantees closure. | completed in accepted S65.2 |
| DART-05 | Generate JSON codecs that preserve nullability, discriminators, names, and round-trip semantics. | completed in accepted S65.2 |
| DART-06 | Generate typed request/response/error DTOs and operation interfaces from canonical operation metadata. | completed in accepted S65.3 |
| DART-07 | Emit deterministic, consumable Dart package layouts with analyzer-clean output. | completed in accepted S65.3 |
| DART-08 | Prove one real Textus CBD Support consumer path uses generated contracts. | planned |
| DART-09 | Document reuse by later CBD Support UI work and prohibit a parallel shadow model. | planned |

## Current Step and evidence correspondence

S65.1 accepted the [normative contract](../spec/dart-model-api-generation.md)
and [source/design inventory](../design/dart-model-api-generation.md) at native
commit `f4a6e3b0f41fd929fba085664ae958330b82cff0`, after independent review and
M0 closure. DART-01/02 and the first two original checklist items are complete.

S65.2A provides the in-memory data-stage generator, immutable models,
classification representations and technical JSON helpers. The
[data-interface specification](../../src/test/scala/cozy/modeler/DartModelGenerationSpec.scala)
and [emitted-runtime specification](../../src/test/scala/cozy/modeler/DartModelRuntimeSpec.scala)
provide executable evidence for DA-01..12. Main and Test compilation passed,
and `testOnly cozy.modeler.DartModelGenerationSpec cozy.modeler.DartModelRuntimeSpec`
completed both suites with 21/21 tests passed, no skips or aborts, in post-repair
validation attempt 006. Actual emitted Dart SDK analysis/runtime and compiler
contract checks succeeded. Independent focused re-review 006 accepted the
complete Step after resolving `CPB-P65-S652-001`, the primitive member-name
collision admission defect. DART-03..05 and both model/serialization checklist
items are complete. S65.2 is accepted at native commit
`716d208ce8505746ac03a3cc3606299f5c4e10e4`.

S65.3A authors the pure [package entry](../../src/main/scala/cozy/modeler/DartPackageGenerator.scala),
canonical [operation selection](../../src/main/scala/cozy/modeler/DartOperationProjection.scala),
[API renderer](../../src/main/scala/cozy/modeler/DartApiRenderer.scala) and explicit
`modeler dart` facade. The [generation specification](../../src/test/scala/cozy/modeler/DartPackageGenerationSpec.scala),
[independent SDK consumer specification](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala)
and [CLI specification](../../src/test/scala/cozy/modeler/DartModelerServiceSpec.scala)
are authored evidence for DA-13..19, including opt-in qualified canonical
bindings and per-service source leaf ownership. The representative fixture is
[canonical CML](../../src/test/resources/modeler/dart-api.cml), and its
[separate Dart consumer](../../src/test/resources/modeler/dart-api-consumer.dart)
imports generated contracts without authoring DTOs. Focused validation attempt
004 passed Main and Test compilation and all twelve suites: 195/195 tests
succeeded, with zero failures or aborted/canceled/ignored/pending tests. SBT
and wrapper exited 0, and the serial lock was released. Actual SDK evidence
contains sixteen direct command reports across the typed positive consumer,
intended negative type diagnostics and all-void package scenarios; the negative
analyzer was intentionally rejected. Dart SDK 3.13.4 exercised `@dart=3.0` source
mode; this is not evidence from a separately installed Dart 3.0 SDK.
The first complete independent protected Step review 001 returned PASS, with
no Current Boundary Blockers, Hygiene or Development Candidates. It accepted
the malformed canonical-model error guard and valid-empty/CLI conformance.
S65.3 is independently accepted for its local Step commit.
DART-06/07 and both API/package checklist items are complete.
The two real CBD/Phase closure checklist items remain unchecked. Live real CBD/S65.4,
repository-full validation, full Phase review and final release remain later
unexecuted obligations.

| Internal Step | Checklist group | Phase outcomes | Evidence locator / closure basis |
| --- | --- | --- | --- |
| S65.1: contract inventory and mapping; Slice S65.1A | DART-01 | DART-01, DART-02 | Spec sections 1–12 and acceptance matrix; design canonical inventory and CBD route; accepted native f4a6e3b after independent review and M0 closure. |
| S65.2: immutable models and codecs; Slice S65.2A | DART-02 | DART-03, DART-04, DART-05 | Accepted native 716d208; spec sections 3–8 and DA-01..12; both Dart model suites passed 21/21 in attempt 006 with actual SDK/runtime/compiler evidence; independent focused re-review 006 accepted CPB-P65-S652-001 closure. |
| S65.3: operation/package entry; Slice S65.3A | DART-03 | DART-06, DART-07 | Spec sections 8–11 and DA-13..19; package/API/CLI implementation and three executable specifications; focused attempt 004 and actual SDK consumer scenarios passed as detailed above; first complete independent protected Step review 001 PASS; accepted for local Step commit. |
| S65.4: real CBD consumption and handoff | DART-04 | DART-08, DART-09 | Spec real CBD acceptance and DA-20..21; design frozen consumer route; live independent Dart consumption, reuse evidence and Phase closure planned. |

The Phase outcomes and checklist groups retain their separate original
numbering. Runtime prerequisites are recorded in the design; they do not
constitute completion evidence or remove the real CBD acceptance obligation.

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
- [Dart model/API generation specification](../spec/dart-model-api-generation.md)
- [Dart model/API generation design](../design/dart-model-api-generation.md)
- [Dart model/API generation proposal](../notes/dart-model-api-code-generation-provisional-specification.md)
- [Dart consumer planning record](../journal/2026/09/2026-09-15-dart-code-generation-knowledgehub-consumer.md)
- [Phase 54](phase-54.md)
