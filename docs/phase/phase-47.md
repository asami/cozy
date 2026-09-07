# Phase 47 - CML Composite StateMachine and Workflow Modeling

Status: in-progress
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
generation, identity, diagnostics, action semantics, and CNCF integration.
Workflow-specific constructs are introduced only when concrete evidence shows
that they cannot be represented cleanly as general Composite StateMachine
semantics.

## Selected Direction

- Composite StateMachine is the primary new CML abstraction.
- Workflow is modeled as a specialization/profile of Composite StateMachine,
  not as a parallel independent language.
- The accepted CSM-02 semantic contract is recorded in
  [CML Composite StateMachine Semantics](../design/cml-composite-statemachine.md),
  which is the authority for constituent bindings, state configuration,
  derivation, derived transitions, and the future projection boundary.
- The accepted CSM-03 static-analysis contract is recorded in
  [CML Composite StateMachine Static Analysis](../design/cml-composite-statemachine-static-analysis.md),
  which is the authority for normalized analysis input, configuration-domain,
  may-reachability, rule, liveness, and derived-graph semantics.
- The accepted CSM-04 action contract is recorded in [CML Composite
  StateMachine Action Algebra](../design/cml-composite-statemachine-action-algebra.md),
  which is the authority for the shared typed logical Action model,
  ActionOccurrences, and causal ActionProgram composition.
- Existing StateMachine concepts such as State, Transition, Trigger/Event,
  Guard/Predicate, Action/Effect, hierarchy/history, and identity are reused
  where semantically valid.
- A Composite StateMachine may coordinate multiple constituent StateMachines
  while presenting one higher-level machine boundary.
- Higher-level composite state should, where possible, be derived from the
  constituent state configuration by explicit rules rather than maintained as
  a second independent mutable business state.
- Composite-state derivation rules must be statically analyzable for coverage,
  overlap/ambiguity, impossibility, reachability, dead configurations, and the
  derived composite transition graph.
- Constituent StateMachines retain explicit identity and role; composition must
  not accidentally imply ownership when only coordination/reference is meant.
- The accepted CSM-05 [Workflow specialization
  classification](../design/cml-workflow-specialization.md) records zero
  mandatory Workflow-only residual semantics; Workflow remains a
  specialization/profile of Composite StateMachine rather than a parallel
  language.
- Every candidate Workflow feature is classified as existing StateMachine,
  general Composite StateMachine, mandatory Workflow specialization, or runtime
  policy/infrastructure.
- CML remains the semantic authority; CNCF consumes generated typed contracts.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CSM-01 | Current-model inventory | Existing StateMachine grammar/model/generation, action/effect representation, and any current Workflow syntax/implementation are inventoried without conflating unrelated uses of the word workflow. | completed |
| CSM-02 | Composite StateMachine semantics | [Accepted semantic contract](../design/cml-composite-statemachine.md) defines constituent-machine binding, role/identity, state configuration, derivation rules, derived transition semantics, and the future projection boundary. | completed |
| CSM-03 | Static composite analysis | [Accepted static-analysis contract](../design/cml-composite-statemachine-static-analysis.md) defines normalized analysis input, rule completeness/exclusivity, reachability status, impossible/dead states, redundancy findings, and derived transition-graph provenance. | completed |
| CSM-04 | Action algebra and composition model | [Accepted action-algebra contract](../design/cml-composite-statemachine-action-algebra.md) defines one shared typed logical Action model, ActionOccurrences, and causal ActionProgram composition. | completed |
| CSM-05 | Workflow specialization analysis | [Accepted classification](../design/cml-workflow-specialization.md) tests candidate requirements against the composite model and retains zero mandatory Workflow-only residual semantics. | completed |
| CSM-06 | Grammar and validation | [Accepted grammar and validation](../design/cml-composite-statemachine-grammar-validation.md) adds normalized Composite StateMachine syntax and semantic validation without admitting a Workflow language. | completed |
| CSM-07 | SimpleModeler generation | Stable typed definitions, constituent bindings, rule IR, derived-model metadata, action algebra/programs, source identities, and ABI metadata are generated deterministically. | planned |
| CSM-08 | CNCF metadata/bootstrap contract | Generated output is aligned with CNCF Phase 64 ComponentFactory/runtime admission and interpreter boundaries. | planned |
| CSM-09 | Visualization/projection metadata | Composite/constituent state, rule matches, derived transitions, and action placement are preserved for meta APIs, CBD Support, BoK visualization, and diagnostics. | planned |
| CSM-10 | Cross-repository acceptance | A real CML model with multiple constituent StateMachines, derived composite states, lower/upper actions, and a Workflow specialization passes Cozy -> generated metadata -> CNCF runtime acceptance. | planned |

CSM-01 record: [Phase 47 checklist](phase-47-checklist.md) and [current-model inventory](../notes/cml-composite-statemachine-workflow-inventory.md).

## Required Inventory

CSM-01 must distinguish the actual CML model from documentation/publication
workflows elsewhere in Cozy.

Inventory at least:

- current `STATEMACHINE` grammar and semantic model;
- transition, state, event/trigger, guard, action/effect, hierarchy/history;
- current action representation and any named-action/provider binding;
- state-machine generated definitions and CNCF bridge metadata;
- any existing `WORKFLOW` parser/modeler syntax or code;
- any Workflow terminology that currently means BoK/publication/process tooling
  rather than CML model semantics;
- model-element identity/source-location handling;
- current ComponentFactory-facing generated surface.

## Composite State Derivation Model

CSM-02 freezes the document-level derivation contract in the [accepted CML
Composite StateMachine semantics](../design/cml-composite-statemachine.md).
The phase document does not duplicate that authority or define CML syntax.

## Static Analysis

The accepted static-analysis semantics are defined in the [CML Composite
StateMachine Static Analysis](../design/cml-composite-statemachine-static-analysis.md)
authority. Later consumers use that one analysis contract rather than
recomputing independent meanings; implementation mechanics remain deferred.

## Action Model

The accepted [CML Composite StateMachine Action
Algebra](../design/cml-composite-statemachine-action-algebra.md) is the sole
authority for shared typed Actions, provenance-preserving occurrences, and
logical causal ActionProgram composition. It leaves syntax, generated IR/ABI,
CNCF interpretation, visualization metadata, and transaction policy at their
named later boundaries.

## Composite StateMachine Questions

CSM-02 and CSM-03's accepted decisions are recorded in the [CML Composite
StateMachine semantics](../design/cml-composite-statemachine.md) and [CML
Composite StateMachine Static Analysis](../design/cml-composite-statemachine-static-analysis.md).
Remaining questions are intentionally reserved for CSM-06 through CSM-10:
grammar and validation, generation, CNCF integration, visualization, and
cross-repository acceptance.

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
- constituent state configuration identity/schema;
- composite state derivation rules as typed pure IR;
- derived composite state/transition metadata where statically available;
- transition/trigger identity;
- explicit constituent-transition relation;
- typed action algebra/program representation;
- lower/composite action provenance and deterministic ordering;
- Workflow specialization marker/metadata when applicable;
- Operation references when part of the action algebra/model;
- source locations;
- ABI/version information.

Required runtime behavior must not depend on inferred name matching, opaque
callbacks, or raw expression strings when a typed model reference/IR exists.

## Acceptance

- A CML Composite StateMachine with multiple constituent machines parses and
  normalizes deterministically.
- Constituent identity/role survives generation.
- Composite states are derived from constituent configuration using explicit
  typed rules.
- Ambiguous and uncovered reachable configurations are detected according to
  the accepted rule policy.
- A derived composite transition graph can be produced from constituent
  transitions plus state rules.
- Constituent and composite actions coexist and preserve deterministic causal
  ordering.
- Actions generate as one typed composable program representation suitable for
  a CNCF Free/interpreter runtime.
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
- Arbitrary embedded Scala/scripts as CML action semantics.
- Provider/transaction APIs exposed to generated action programs.
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
