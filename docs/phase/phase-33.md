# Phase 33 - CML Composite StateMachine and Workflow Modeling

Status: planned
Planned at: 2026-09-05
Depends on: Phase 32 where applicable
Cross-repository consumer: `asami/goldenport-cncf` Phase 64

## Purpose

Extend CML StateMachine modeling with Composite StateMachine support and express
Workflow as a specialization/profile of Composite StateMachine wherever
possible.

The governing model is:

```text
StateMachine
  +-- local/simple StateMachine
  +-- Composite StateMachine
        +-- Workflow
             + workflow-specific mandatory semantics only
```

The phase must maximize reuse of existing StateMachine grammar, semantic model,
generation, identity, diagnostics, and CNCF integration. Workflow-specific
constructs are introduced only when concrete evidence shows that they cannot be
represented cleanly as general Composite StateMachine semantics.

## Selected Direction

- Composite StateMachine is the primary new CML abstraction.
- Workflow is modeled as a specialization/profile of Composite StateMachine,
  not as a parallel independent language.
- Existing StateMachine concepts such as State, Transition, Trigger/Event,
  Guard/Predicate, Action/Effect, hierarchy/history, and identity are reused
  where semantically valid.
- A Composite StateMachine may coordinate multiple constituent StateMachines
  while presenting one higher-level machine boundary.
- Constituent StateMachines retain explicit identity and role; composition must
  not accidentally imply ownership when only coordination/reference is meant.
- The exact Workflow-only mandatory semantic set remains open until the
  Composite StateMachine model is exercised.
- Every candidate Workflow feature is classified as existing StateMachine,
  general Composite StateMachine, mandatory Workflow specialization, or runtime
  policy/infrastructure.
- CML remains the semantic authority; CNCF consumes generated typed contracts.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CSM-01 | Current-model inventory | Existing StateMachine grammar/model/generation and any current Workflow syntax/implementation are inventoried without conflating unrelated uses of the word workflow. | planned |
| CSM-02 | Composite StateMachine semantics | Constituent-machine binding, role/identity, composite state/configuration, transition/trigger coordination, and projection semantics are defined. | planned |
| CSM-03 | Workflow specialization analysis | Candidate Workflow-only requirements are tested against the composite model and only mandatory residual semantics are retained. | planned |
| CSM-04 | Grammar and validation | CML syntax and semantic validation are added/refined for Composite StateMachine and Workflow specialization. | planned |
| CSM-05 | SimpleModeler generation | Stable typed definitions, constituent bindings, source identities, and ABI metadata are generated deterministically. | planned |
| CSM-06 | CNCF metadata/bootstrap contract | Generated output is aligned with CNCF Phase 64 ComponentFactory/runtime admission. | planned |
| CSM-07 | Visualization/projection metadata | Composite and constituent machine structure is preserved for meta APIs, CBD Support, BoK visualization, and diagnostics. | planned |
| CSM-08 | Cross-repository acceptance | A real CML model with multiple constituent StateMachines and a Workflow specialization passes Cozy -> generated metadata -> CNCF runtime acceptance. | planned |

## Required Inventory

CSM-01 must distinguish the actual CML model from documentation/publication
workflows elsewhere in Cozy.

Inventory at least:

- current `STATEMACHINE` grammar and semantic model;
- transition, state, event/trigger, guard, action/effect, hierarchy/history;
- state-machine generated definitions and CNCF bridge metadata;
- any existing `WORKFLOW` parser/modeler syntax or code;
- any Workflow terminology that currently means BoK/publication/process tooling
  rather than CML model semantics;
- model-element identity/source-location handling;
- current ComponentFactory-facing generated surface.

## Composite StateMachine Questions

The phase must resolve or explicitly defer:

- how constituent StateMachines are declared or referenced;
- role-qualified binding when the same machine type appears more than once;
- ownership versus reference/coordination semantics;
- how higher-level composite states/configurations relate to constituent states;
- how constituent committed transitions become composite triggers;
- whether composite transitions reuse exactly the existing transition model;
- how guards/actions/effects are reused without duplicate semantics;
- how nested Composite StateMachines are represented;
- how stable identity/version/source location is preserved through generation;
- how the model is projected for diagrams and review.

## Workflow Specialization Rule

Do not assume that commonly seen workflow-system features are Workflow-specific.

For each candidate feature, answer in order:

1. Is it already a StateMachine concept?
2. If not, is it useful as a general Composite StateMachine concept?
3. If not, is it mandatory for something to qualify as Workflow?
4. If not, is it merely CNCF execution/runtime policy?
5. Otherwise defer it to an external/specialist workflow engine.

Candidate concerns include process-instance identity, multi-subject correlation,
durable wait/progression, pending work, completion/cancellation, and history.

## Generation Contract

The generated representation must preserve at least:

- composite machine id/version;
- constituent machine refs and roles;
- subject/model refs where part of semantics;
- composite state/configuration identity;
- transition/trigger identity;
- explicit constituent-transition binding;
- reused predicate/action representation;
- Workflow specialization marker/metadata when applicable;
- Operation references when part of the model;
- source locations;
- ABI/version information.

Required runtime behavior must not depend on inferred name matching or raw
strings when a typed model reference exists.

## Acceptance

- A CML Composite StateMachine with multiple constituent machines parses and
  normalizes deterministically.
- Constituent identity/role survives generation.
- Workflow can be represented primarily by the Composite StateMachine model.
- Every Workflow-specific construct has explicit justification as mandatory and
  non-generalizable.
- StateMachine semantics are not duplicated under Workflow-specific names.
- Generated definitions are accepted by CNCF Phase 64 through normal
  ComponentFactory bootstrap.
- Cross-repository acceptance starts from real CML source.
- The same model remains usable for visualization and review metadata.

## Non-Goals

- A full BPMN language.
- A generic DAG/workflow language independent of StateMachine.
- Adding parallelism, human tasks, compensation, connectors, or rich scheduling
  merely because workflow products commonly provide them.
- Runtime persistence/retry/recovery implementation owned by CNCF.
- Direct domain-state mutation semantics that bypass constituent StateMachine
  authority.

## References

- `docs/notes/cml-composite-statemachine-workflow-proposal.md`
- `docs/journal/2026/09/2026-09-05-composite-statemachine-workflow-modeling-direction.md`
- `docs/design/cml-grammar.md`
- `docs/design/powertype-statemachine-generation-and-cncf-integration.md`
- `asami/goldenport-cncf/docs/phase/phase-64.md`
