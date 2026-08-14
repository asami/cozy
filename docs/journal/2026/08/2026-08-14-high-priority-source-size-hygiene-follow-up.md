# High-priority Source-size Hygiene Follow-up

status=task-handoff
date=2026-08-14
classification=existing-hygiene-debt

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
