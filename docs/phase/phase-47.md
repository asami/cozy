# Phase 47 - CML Composite StateMachine and Workflow Modeling

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
generation, identity, diagnostics, action semantics, and CNCF integration.
Workflow-specific constructs are introduced only when concrete evidence shows
that they cannot be represented cleanly as general Composite StateMachine
semantics.

## Selected Direction

- Composite StateMachine is the primary new CML abstraction.
- Workflow is modeled as a specialization/profile of Composite StateMachine,
  not as a parallel independent language.
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
- Constituent and composite transitions may both have actions. Actions at both
  abstraction levels coexist and are composed in deterministic causal order.
- CML action declarations denote typed logical effects/programs; they are not
  interpreted as arbitrary executable code at the model layer.
- Generated action programs should target a typed action algebra suitable for
  Free Monad composition and CNCF interpreter execution.
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
| CSM-01 | Current-model inventory | Existing StateMachine grammar/model/generation, action/effect representation, and any current Workflow syntax/implementation are inventoried without conflating unrelated uses of the word workflow. | planned |
| CSM-02 | Composite StateMachine semantics | Constituent-machine binding, role/identity, state configuration, derivation rules, derived transition semantics, and projection semantics are defined. | planned |
| CSM-03 | Static composite analysis | Rule completeness/exclusivity, reachable configurations, impossible/dead states, redundant rules, and derived transition graph analysis are defined. | planned |
| CSM-04 | Action algebra and composition model | Constituent/composite actions share one typed logical action model that can be composed as a Free program and interpreted later by CNCF. | planned |
| CSM-05 | Workflow specialization analysis | Candidate Workflow-only requirements are tested against the composite model and only mandatory residual semantics are retained. | planned |
| CSM-06 | Grammar and validation | CML syntax and semantic validation are added/refined for Composite StateMachine, derivation rules, actions, and Workflow specialization. | planned |
| CSM-07 | SimpleModeler generation | Stable typed definitions, constituent bindings, rule IR, derived-model metadata, action algebra/programs, source identities, and ABI metadata are generated deterministically. | planned |
| CSM-08 | CNCF metadata/bootstrap contract | Generated output is aligned with CNCF Phase 64 ComponentFactory/runtime admission and interpreter boundaries. | planned |
| CSM-09 | Visualization/projection metadata | Composite/constituent state, rule matches, derived transitions, and action placement are preserved for meta APIs, CBD Support, BoK visualization, and diagnostics. | planned |
| CSM-10 | Cross-repository acceptance | A real CML model with multiple constituent StateMachines, derived composite states, lower/upper actions, and a Workflow specialization passes Cozy -> generated metadata -> CNCF runtime acceptance. | planned |

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

The preferred model is to treat constituent state as authoritative and derive
higher-level composite state from the current state configuration.

Conceptually:

```text
StateConfiguration(
  order    = Accepted,
  payment  = Authorized,
  shipment = Waiting
)
       |
       v
CompositeStateRule
       |
       v
ReadyToShip
```

A composite state rule is a pure predicate over role-qualified constituent
state values. The exact CML syntax remains open, but the semantic contract must
support deterministic evaluation and static analysis.

For each reachable configuration the rule system should ideally produce exactly
one composite state:

```text
0 matches  -> UnmappedConfiguration
1 match    -> valid CompositeState
2+ matches -> AmbiguousConfiguration
```

Partial mappings may be permitted only when explicitly modeled; they must not
silently become an unknown/default state.

## Static Analysis

Because constituent StateMachines are finite models, Cozy should derive or
symbolically analyze the reachable configuration space rather than treating
composite rules as opaque runtime predicates.

Required analysis includes, where tractable:

- rule coverage;
- rule overlap/ambiguity;
- impossible rules;
- redundant/subsumed rules;
- reachable versus unreachable composite states;
- dead composite states/configurations;
- unexpected composite transitions;
- constituent transitions that leave the composite state unchanged;
- composite transitions that can arise from multiple distinct constituent
  transitions; and
- model-size/complexity diagnostics when explicit configuration enumeration is
  not practical.

The derived composite transition graph is itself an important model artifact:

```text
constituent transition graphs
        +
composite-state derivation rules
        |
        v
reachable configuration graph
        |
        v
derived composite transition graph
```

This graph should be available to generation, visualization, review, and CNCF
metadata rather than recomputed independently by each consumer.

## Action Model

Actions may exist at both the constituent and composite levels.

Example:

```text
Payment.Pending -> Payment.Authorized
  action: recordAuthorization

OrderFulfillment.WaitingForPayment -> ReadyToShip
  action: requestShipment
```

The two actions represent different abstraction levels and may coexist.

CML should model actions as logical typed action operations/programs, not as
arbitrary embedded runtime code. SimpleModeler should generate a typed action
algebra / program representation that can be composed and interpreted by CNCF.

The intended semantic shape is:

```text
constituent transition actions
        then
composite derived-transition actions
        |
        v
composed Action Program
```

The preferred implementation direction is a Free Monad (or equivalent free
program representation) over a typed action algebra. Exact Scala library/API
selection is implementation detail; the semantic requirements are:

- composition is explicit and deterministic;
- programs are inspectable before execution;
- lower- and upper-level actions use the same composition mechanism;
- interpreters can differ for production, test, simulation, review, or
  visualization;
- effect classification can separate local transactional effects from
  after-commit/external effects; and
- static review can identify duplicate/conflicting effects where the action
  algebra carries sufficient semantic identity.

CML should not embed transaction policy into the action syntax. It should emit
typed model meaning that CNCF can classify and interpret under its UnitOfWork
and after-commit rules.

## Composite StateMachine Questions

The phase must resolve or explicitly defer:

- how constituent StateMachines are declared or referenced;
- role-qualified binding when the same machine type appears more than once;
- ownership versus reference/coordination semantics;
- exact state-rule syntax and rule evaluation semantics;
- whether exactly-one rule matching is mandatory for every reachable
  configuration;
- how constituent committed transitions produce a new configuration;
- how a configuration change produces zero or one derived composite transition;
- whether composite transitions reuse exactly the existing transition model;
- execution ordering of constituent and composite actions;
- action algebra shape and generated representation;
- how local transactional versus after-commit effects are identified without
  embedding provider logic in CML;
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
