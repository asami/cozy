# High-priority Source-size Hygiene Follow-up

status=task-handoff
date=2026-08-14
classification=existing-hygiene-debt
triage-status=COMPLETED

This journal records source-size debt found after the 2026-08-14 Cozy source
hygiene sweep. It does not change generated behavior, public APIs, or phase
closure claims.

The shared source-size rule treats an ordinary hand-written source file larger
than 1,000 lines as debt requiring a recorded split disposition or documented
exception. A file larger than 2,000 lines is high-priority source-size debt.
No split disposition or exception was found for the entries below.

## Open High-priority Items

### COZY-HYG-SIZE-001: Split `CozyBok.scala`

- Affected source: `src/main/scala/cozy/bok/CozyBok.scala`
- Measured size: 17,167 lines.
- Threshold: high-priority source-size debt (more than 2,000 lines).
- Required next action: inventory independent responsibilities, select a
  behavior-preserving split boundary, record the split disposition, and
  validate focused plus full affected suites.

### COZY-HYG-SIZE-002: Split `CozyVideo.scala`

- Affected source: `src/main/scala/cozy/video/CozyVideo.scala`
- Measured size: 6,102 lines.
- Threshold: high-priority source-size debt (more than 2,000 lines).
- Required next action: inventory independent responsibilities, select a
  behavior-preserving split boundary, record the split disposition, and
  validate focused plus full affected suites.

### COZY-HYG-SIZE-003: Split `Modeler.scala`

- Affected source: `src/main/scala/cozy/modeler/Modeler.scala`
- Measured size: 4,386 lines.
- Threshold: high-priority source-size debt (more than 2,000 lines).
- Required next action: apply the staged, behavior-preserving split handoff
  below. The StateMachine TODOs remain a separate functional follow-up and
  must not be hidden by this structural work.

## `Modeler.scala` Staged Split Handoff

### Goal

Split `Modeler.scala` by responsibility without changing generation
specifications, public APIs, accepted/rejected CML, diagnostics, generated
files, or generation order.

### Current Structure

- `Modeler` class (approximately lines 58--460): public Kaleidox-to-Scala and
  diagram-generation facade.
- `Modeler` companion: `Explain`, `Help`, `Linkage`, and public data types.
- `RelationshipCml` (approximately lines 644--887): `RELATIONSHIP` section
  and Operation-binding CML reading and validation.
- `ModelBuilder` (approximately lines 888--4248): type projection,
  StateMachine, Component/Subsystem, Service/Operation, and runtime-definition
  generation.

### Intended File Boundaries

```text
Modeler.scala
ModelerRelationshipCml.scala
ModelBuildContext.scala
ModelTypeProjector.scala
ModelStateMachineProjector.scala
ModelServiceOperationProjector.scala
ModelComponentProjector.scala
ModelRuntimeDefinitionProjector.scala
```

- `Modeler.scala` retains the public facade, public types, and existing
  `Modeler.ModelBuilder` entry point.
- `ModelerRelationshipCml.scala` owns Relationship definitions and Operation
  binding CML syntax/validation.
- `ModelTypeProjector.scala` owns Entity, Value, Datatype, Powertype,
  attribute-type, and relationship projection.
- `ModelStateMachineProjector.scala` owns StateMachine, transition, and
  initial-state normalization projection.
- `ModelServiceOperationProjector.scala` owns Service/Operation, input/output
  type, and Service/Operation consistency projection.
- `ModelComponentProjector.scala` owns Component completion, Service skeleton,
  and Component/Subsystem definitions.
- `ModelRuntimeDefinitionProjector.scala` owns Event, Aggregate, View, runtime
  Operation, and view-facing definitions.

### Invariants

- Preserve the public `Modeler` and `Modeler.ModelBuilder` names, call shapes,
  and return values.
- Preserve CML acceptance/rejection, diagnostic text, generated files, and
  generation order.
- Do not create a universal mutable context. Share only immutable input and
  the smallest required indexes.
- Keep every Projector package-private; do not create a new public API.
- Use `ModelerScalaGenerationSpec` as the primary compatibility gate.

### Recommended Order

1. Move `RelationshipCml`.
2. Extract StateMachine projection.
3. Extract type, attribute, and relationship projection.
4. Extract Service/Operation projection.
5. Extract Component/Subsystem and runtime-definition projection.
6. Reduce `ModelBuilder` to a thin assembly facade.

At each stage, run focused generation specifications and the affected full
suite, and verify that the diff is structural only.

## Scope Boundary

This task record does not authorize mechanical splitting during an unrelated
review-fix. Each item requires a frozen task boundary and an explicit split
disposition before implementation.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-SIZE-001
Handoff Journal: cozy:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-08-19

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-SIZE-002
Handoff Journal: cozy:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-08-19

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-SIZE-003
Handoff Journal: cozy:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-08-19

## `COZY-HYG-SIZE-003 Split Disposition`

- status=validation-complete
- Structural boundary: `Modeler.scala`, `ModelerRelationshipCml.scala`,
  `ModelBuildContext.scala`, `ModelTypeProjector.scala`,
  `ModelStateMachineProjector.scala`, `ModelServiceOperationProjector.scala`,
  `ModelComponentProjector.scala`, `ModelRuntimeDefinitionProjector.scala`,
  and this journal entry.
- Invariant: no public API or behavior changes; existing `ModelerScalaGenerationSpec`
  remains the primary compatibility gate.
- StateMachine TODOs remain a separate functional item and are not implemented
  by this structural extraction.

### Validation Record

- Focused compatibility gate: `testOnly cozy.modeler.ModelerScalaGenerationSpec`
  passed on 2026-08-14 (28 tests).
- Final Cozy suite: `test` passed on 2026-08-14 (1,303 tests across 94 suites;
  7 existing cancellations).
- The resulting files are all below the 1,000-line source-size threshold:
  `Modeler.scala` (748), `ModelerRelationshipCml.scala` (383),
  `ModelBuildContext.scala` (34), `ModelTypeProjector.scala` (673),
  `ModelStateMachineProjector.scala` (607),
  `ModelServiceOperationProjector.scala` (923),
  `ModelComponentProjector.scala` (559), and
  `ModelRuntimeDefinitionProjector.scala` (950).

## `COZY-HYG-SIZE-002 Split Disposition`

- status=implementation-complete
- Structural boundary and roles: `src/main/scala/cozy/video/CozyVideo.scala`
  retains the private[cozy] API facade and delegation singleton;
  `CozyVideoConfig.scala` owns command configurations, CLI parameters, and
  property normalization; `CozyVideoModel.scala` owns project, script, part,
  scene, replay, planning, and result data models; `CozyVideoTools.scala`
  owns tool modes, statuses, registry/probe/providers, and narration provider
  contracts; `CozyVideoCommand.scala` owns dispatch, operation orchestration,
  and load helpers; `CozyVideoRuntime.scala` owns process runners, execution
  command/path support, and runtime data; `CozyVideoNarration.scala` owns
  synthesis, provider choice, WAV I/O, and audio manifests;
  `CozyVideoToolValidation.scala` owns tool admission and dependency
  diagnostics; `CozyVideoTranscription.scala` owns ffmpeg/whisper
  transcription; `CozyVideoReviewEvidence.scala` owns review-evidence and
  frame/extract validation; `CozyVideoBuildReplay.scala` owns project build,
  ffmpeg/ffprobe, replay workspace/commands/manifests; `CozyVideoRdf.scala`
  owns RDF graph/manifest helpers; `CozyVideoRenderWorkspace.scala` owns
  render targets, workspaces, assets, character-dialogue validation, and
  execution; `CozyVideoRenderTemplates.scala` owns renderer properties and
  embedded Remotion/simple-java2d templates; `CozyVideoPlanning.scala` owns
  plans, parts, artifacts, commands, and dry-run construction; and
  `CozyVideoPresentation.scala` owns textual result projections.
- Invariants: preserve every established `CozyVideo.X` type/companion shape,
  named parameter, overload, default, diagnostic, literal, JSON field,
  process argument, filesystem/network behavior, serialization shape, and
  initialization order; do not edit callers or executable specifications;
  keep all resulting split sources below 1,000 lines; and introduce no new
  production API, dependency, schema, persistence, lifecycle, CLI, or
  configuration behavior.
## HP-001 Ledger Closure

### HYG-SIZE-001

Hygiene Status: RESOLVED
Resolution Batch: cozy:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: Cozy `sbt --batch test`, invocation `33578-20260818T224807Z`, 1,337 succeeded and 0 failed.
Acceptance Commit: reported externally after commit

### HYG-SIZE-002

Hygiene Status: RESOLVED
Resolution Batch: cozy:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: Cozy `sbt --batch test`, invocation `33578-20260818T224807Z`, 1,337 succeeded and 0 failed.
Acceptance Commit: reported externally after commit

### HYG-SIZE-003

Hygiene Status: RESOLVED
Resolution Batch: cozy:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: Cozy `sbt --batch test`, invocation `33578-20260818T224807Z`, 1,337 succeeded and 0 failed.
Acceptance Commit: reported externally after commit

- Primary focused compatibility gate:
  `testOnly cozy.video.CozyVideoSpec cozy.video.CozyVideoAssetsSpec cozy.video.CozyVideoCreditsSpec cozy.video.CozyVideoEffectsSpec cozy.video.CozyVideoNarrationSpec cozy.video.CozyVideoProfileRenderSpec cozy.video.CozyVideoPublisherAdmissionSpec cozy.video.CozyVideoRemotionIntegrationSpec cozy.video.CozyVideoScaffoldSpec`

## `COZY-HYG-SIZE-001 Split Disposition`

- status=implementation-complete
- Structural boundary and responsibility: `CozyBok.scala` is the private[cozy]
  facade and direct-delegation surface; `CozyBokConfig.scala` and
  `CozyBokBuildConfig.scala` own public configuration, policy, runner, and CLI
  config factories; `CozyBokModel.scala` owns internal BoK/site/dashboard,
  repository, term, scenario, and serialization models; `CozyBokCommand.scala`
  owns CLI dispatch and entry operations; `CozyBokBibliography.scala` and
  `CozyBokBibliographyRdf.scala` own bibliography cache/fetch, alias, fragment,
  Turtle, and JSON-LD synchronization; `CozyBokBuild.scala` and
  `CozyBokPublication.scala` own build/workflow/backup and publication
  orchestration; `CozyBokSiteBuild.scala`, `CozyBokSitePages.scala`, and
  `CozyBokProjectPages.scala` own site and knowledge/project page projections;
  `CozyBokRepositoryMetadata.scala`, `CozyBokRepositoryCatalog.scala`, and
  `CozyBokRepositoryPages.scala` own CAR/SAR catalog discovery, metadata,
  diagnostics, and pages; `CozyBokSieMetadata.scala` owns SIE/RDF metadata;
  `CozyBokGlossaryPages.scala`, `CozyBokRdfPages.scala`,
  `CozyBokRdfViewer.scala`, `CozyBokGlossaryWorkflow.scala`,
  `CozyBokGlossaryAnalysis.scala`, `CozyBokTagPages.scala`,
  `CozyBokBibliographyPages.scala`, `CozyBokScenarioTermHub.scala`, and
  `CozyBokLocalizedGlossary.scala` own semantic glossary/RDF/tag/bibliography/
  scenario/term projections; and `CozyBokHtmlPages.scala`,
  `CozyBokSiteDocument.scala`, `CozyBokDashboardCore.scala`,
  `CozyBokDashboardAnalysis.scala`, `CozyBokMetadata.scala`,
  `CozyBokFileSupport.scala`, `CozyBokUiAssets.scala`,
  `CozyBokProjectResolution.scala`, and `CozyBokScaffold.scala` own HTML,
  dashboard, metadata, file/resource UI, root/config resolution, and scaffold
  helpers.
- Invariants: preserve every established `CozyBok.X` type/companion shape,
  named parameter, overload, default, diagnostic, literal, process argument,
  filesystem/path policy, serialization shape, and initialization order through
  the `CozyBokImplementation` singleton; `_default_ui_css()` reads
  `src/main/resources/cozy/bok/default-ui.css` with byte-identical UTF-8 output
  and keeps the `css/site.css` ZIP fallback; no callers/specifications or new
  API/behavior/dependency/schema/persistence/lifecycle changes.
- Every resulting CozyBok Scala source is below 1,000 lines. Existing
  `CozyBokProjectPublisher.scala` and the listed oversized specifications remain
  Existing Debt (Separate Follow-up).
- Primary focused compatibility gate:
  `testOnly cozy.bok.CozyBokSpec cozy.CozyBokDashboardSpec cozy.CozyBokKnowledgeSourceSpec cozy.CozyBokMonoKotoSpec cozy.bok.CozyBokProjectSpec cozy.CozyBokScenarioSpec cozy.bok.CozyBokTagSpec cozy.bok.CozyBokTermHubSpec cozy.CozyBokTermTypeSpec cozy.bok.CozyBokComponentRepositorySpec cozy.bok.CozyBokBibliographySpec cozy.bok.scenario.CozyBokScenarioModelSpec`
