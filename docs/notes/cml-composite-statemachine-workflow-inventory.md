# CML Composite StateMachine and Workflow Current-model Inventory

Status: non-normative factual baseline

Date: 2026-09-07

Scope: Phase 47 / CSM-01 only. This note records the current Cozy evidence; it
does not add CML syntax, Composite StateMachine semantics, Workflow semantics,
or a generation/runtime design. The mutation repository is Cozy. Phase 47.1,
Phase 47.2, successor work, and `asami/goldenport-cncf` are excluded.

## Evidence boundary

The primary design baseline is
[`docs/design/cml-grammar.md`](../design/cml-grammar.md). It lists
`STATEMACHINE` among the implemented top-level structural sections and
describes the document as an implemented grammar baseline. The StateMachine
projection and bridge evidence is in these existing Cozy sources:

- [`src/main/scala/cozy/modeler/Modeler.scala`](../../src/main/scala/cozy/modeler/Modeler.scala), especially
  `generateStateMachineDiagram`, `_select_state_machine`,
  `_project_state_machine`, `_states`, `requireNamedHistoryField`, and
  `_normalize_transition_action`;
- [`src/main/scala/cozy/modeler/ModelStateMachineProjector.scala`](../../src/main/scala/cozy/modeler/ModelStateMachineProjector.scala),
  especially `projectStateMachine`, `_build_state_map`,
  `_build_state_machine_transitions`, `_all_states`, `_all_transitions`,
  `_transition_plan`, `_state_machine_definitions`, and
  `_transition_rules`;
- [`src/main/scala/cozy/modeler/ModelComponentProjector.scala`](../../src/main/scala/cozy/modeler/ModelComponentProjector.scala),
  especially `_make_component`;
- [`src/main/scala/cozy/modeler/StateMachineDiagramGenerator.scala`](../../src/main/scala/cozy/modeler/StateMachineDiagramGenerator.scala),
  especially `generate`; and
- [`src/test/scala/cozy/modeler/ModelerStateMachineProjectionSpec.scala`](../../src/test/scala/cozy/modeler/ModelerStateMachineProjectionSpec.scala),
  the direct projection specification.

The surrounding direction is recorded in
[`docs/design/powertype-statemachine-generation-and-cncf-integration.md`](../design/powertype-statemachine-generation-and-cncf-integration.md),
[`docs/journal/2026/09/2026-09-05-composite-statemachine-workflow-modeling-direction.md`](../journal/2026/09/2026-09-05-composite-statemachine-workflow-modeling-direction.md),
and [`docs/notes/cml-composite-statemachine-workflow-proposal.md`](cml-composite-statemachine-workflow-proposal.md).
Those documents describe the proposed Composite StateMachine direction; they
are not evidence that the proposed extension already exists.

## Grammar and normalized model entry points

`docs/design/cml-grammar.md` identifies `STATEMACHINE` as an implemented CML
top-level section. The parser-facing executable evidence is
[`src/test/scala/cozy/modeler/KaleidoxCmlParsingSpec.scala`](../../src/test/scala/cozy/modeler/KaleidoxCmlParsingSpec.scala):

- the `kaleidox accepts STATE-MACHINE top-level alias` example reads a
  `StateMachineModel` through `model.takeStateMachineModel` and observes the
  normalized `Draft` and `Published` states and values;
- the `kaleidox parses STATEMACHINE from .cml` example does the same for a
  literate `.cml` source and confirms that reserved/free narrative sections do
  not become StateMachine classes.

`Modeler.ModelBuilder.apply` obtains `p.takeStateMachineModel`; `_build` maps
`stateMachine.classes.values` through
`ModelStateMachineProjector.projectStateMachine`, and includes those projected
machines in the `SimpleModel`. Thus the current Cozy boundary consumes the
normalized Kaleidox `StateMachineModel` / `StateMachineClass` / `StateMachineRule`
objects; this Slice does not change the parser or those model types.

## Top-level and entity-local selection

`Modeler._select_state_machine(model, name)` first asks
`model.stateMachineModel.getClass(name)`. Only when that exact top-level lookup
does not resolve does it inspect the entity model:

- `Entity.StateMachine` selects a named machine under the named entity;
- a bare `Entity` selects its only StateMachine;
- a bare entity with multiple machines raises a syntax error naming the
  candidates; and
- an entity with no machine, or an unqualified unknown name, remains unresolved.

`ModelerStateMachineProjectionSpec` covers all four selection cases, including
the precedence of an exact top-level name over an entity-local candidate.

## Nested state hierarchy and history

`Modeler._states` and `ModelStateMachineProjector._build_state_map` construct
`MState` values for `StateClass` entries and recursively construct composite
states for `StateMachineRule.statemachines`. Each nested rule must have a
nonempty name through `Modeler._require_composite_state_name`; an unnamed
composite raises a syntax error. Nested states are placed in a composite
state's `subStateMap`.

Transitions are built recursively for state-local and rule-level call/global
transition collections. A `NamedHistoryTransitionTo` targets a named composite
history state. A bare `HistoryTransitionTo` is rejected because the current
projection requires a named composite. `Modeler.requireNamedHistoryField`
checks nested rules as well as the root and requires `HISTORY-FIELD` when a
named history transition is present.

For the Component bridge, `_history_composites` records each direct nested
composite of the current rule, its direct leaf state names, and first-leaf
fallback. `_history_field_name`
requires the configured history field to be an entity attribute. Transition
rules carry the history composite, direct leaves, fallback, and expected history
record writes where the current projection computes them.

## Transition, event, guard, action, and effect handling

`ModelStateMachineProjector._all_transitions` recursively flattens the current
model into transition definitions. It preserves state-local call transitions,
state-local global transitions, rule-level call transitions, rule-level global
transitions, followed by nested rules. `_validate_state_machine` checks that
transition targets are declared (except the initialization sentinel), named
history targets identify declared composites, each transition has an event
name, and referenced events are declared when the model declares events.

An event is taken from an `EventNameGuard` when present, otherwise from the
transition event name. Component trigger metadata maps event names such as
`save`, `create`, and `update` to the current Save/Update trigger vocabulary;
call transitions otherwise map to Update and other transitions to Save.

Guards are projected through `_guard_for_rule`: unconditional and event-name
guards do not become a separate Component rule guard; CML expressions become a
reference or expression after normalization; and `AndGuard` / `OrGuard`
expressions are joined with `&&` / `||`. The legacy diagram projection creates
an `MGuard` mark from the corresponding guard text.

The current effect path is an `Activity` on `Transition`. The legacy and
Component-facing projections both call `_normalize_transition_action` and
preserve the resulting value as an `MAction` or a `RuleAction` in the
transition plan. State entry and exit activities are also represented in the
Component `RulePlan`; the current plan has separate `exit`, `transition`, and
`entry` slots.

## One-string transition `ACTION` projection

`Modeler._normalize_transition_action(activity, machinename)` is the current
normalization boundary:

| Current input | Current result |
| --- | --- |
| `Activity.Empty` | no transition action metadata |
| an opaque or rendered activity whose content has no nonempty line | no transition action metadata |
| exactly one nonempty line | one trimmed `String`, projected as one action/script value |
| two or more nonempty lines | syntax error: transition `ACTION` must contain exactly one nonempty action line |

The helper splits on line boundaries, trims each line, and filters empty lines.
It does not deduplicate lines. Therefore two identical nonempty action lines are
still rejected as multiple lines. A whitespace-only line is filtered out. The
one resulting string is not parsed into an action algebra and is not resolved to
a provider or named action by this current projection.

The direct specification covers one action through the legacy MStateMachine,
the modern `ModelStateMachineProjector`, and Component transition metadata;
whitespace-only action input; and rejection of multiple action lines.

## Component StateMachine definition and transition-rule metadata

`ModelStateMachineProjector._state_machine_definitions` currently derives
definitions from entity-local StateMachines. Each
`MComponent.StateMachineDefinition` contains the machine name, distinct stable
flattened state names, declared/referenced event names, the optional history
field name, and history-composite metadata.

`_transition_rules` emits one `MComponent.StateMachineTransitionRule` per
flattened transition. The current fields include entity collection name,
trigger, event name, machine name, state field name, source state/name value,
target state/name value, fixed priority and declaration order at the local
construction point, normalized guard, exit/transition/entry plan, and history
metadata including expected record writes. The outer
`_state_machine_transition_rules` assigns stable vector declaration order.

`ModelRuntimeDefinitionProjector.stateMachineDefinitions` delegates to the
StateMachine projector. `ModelComponentProjector._make_component` places both
the transition rules and definitions into `MComponent.Core`. This is the
current ComponentFactory-facing generated metadata surface recorded by Cozy;
it is not a Composite StateMachine definition or a Workflow contract.

Top-level StateMachines are included in the value-side `SimpleModel` by
`Modeler.ModelBuilder`, while the Component definitions and transition rules
described above are collected from entity-bound machines. The current evidence
does not establish a dedicated Component metadata channel for an independent
top-level machine.

## Diagram projection

`Modeler.generateStateMachineDiagram` selects a top-level or entity-local
StateMachine, projects it through `_project_state_machine`, and passes the
result to `_make_diagram`. `StateMachineDiagramGenerator.generate` delegates
to SimpleModeler's UML `StateMachineDiagramGenerator.makeStateMachineDiagramSvg`
and wraps the SVG as an `SImage`.

The existing projection preserves current nested state maps, transitions,
guards, history states, and one-string action values for that diagram input.
It does not derive a multi-constituent composite graph.

## Identity and source-location observations

The current StateMachine projection uses the normalized machine name as its
visible identity: `_project_state_machine` creates `MDomainStateMachine` with
`p.name`, and `ModelStateMachineProjector._statemachine` uses
`Description.name(p.name)` with the fixed package `domain.statemachine`.
Component definition and rule metadata likewise use `sm.name` as
`name` / `machineName`.

The cited StateMachine projection methods do not read or emit a separate
StateMachine version, constituent role, generated identity, or per-element
source-location field. The parser and projection examples in the current
executable specifications use `withoutLocation` / `parseWitoutLocation`, so
those examples do not provide source-location coverage. This is a bounded
observation about the cited current paths, not a claim about every field in the
external Kaleidox model or a decision about future identity/source metadata.

## Current test coverage

The current evidence covers:

- parser acceptance and normalized top-level StateMachine values in
  `KaleidoxCmlParsingSpec`;
- top-level/entity-local selection, transition action projection,
  whitespace-only and repeated action-line behavior, unnamed nested composite
  rejection, and diagram package selection in
  `ModelerStateMachineProjectionSpec`;
- value-mode StateMachine projection and generated Scala topology/value/event
  preservation in `ModelerValueGenerationSpec`; and
- generated Component history definitions, flattened state values, named
  history targets, and expected history writes in
  `src/sbt-test/cozy/state-machine-history-runtime/verify/GeneratedStateMachineHistoryRuntimeSpec.scala`.

These tests do not establish Composite StateMachine constituent binding,
derived composite-state rules, derived composite transitions, a CML Workflow
grammar, or a typed action algebra. No executable specification is changed by
this documentation-only Slice.

## Bounded CML `WORKFLOW` search and terminology separation

A bounded case-insensitive search over the current Cozy modeler implementation,
modeler specifications, and modeler resources (`src/main/scala/cozy/modeler`,
`src/test/scala/cozy/modeler`, and `src/test/resources/modeler`) found no
`WORKFLOW` parser/modeler syntax or implementation. The current grammar
baseline's top-level list likewise names `STATEMACHINE` but not `WORKFLOW`.
This result is limited to that search boundary and does not inspect external
repositories or invent a negative claim about future syntax.

The word `Workflow` does occur in unrelated current Cozy surfaces. For example,
`src/main/scala/cozy/ui/CozyLogicalUi.scala` defines `WorkflowRole` for the
Logical UI model, and the Phase 43 UI contract keeps that role as a
non-executing pattern source distinct from domain StateMachine authority.
Phase 11 and related strategy/journal material use Workflow for BoK
publication, upload, or process tooling. Those UI, BoK, publication, and
process meanings are not evidence of a CML Composite StateMachine or CML
`WORKFLOW` syntax.

## Phase-47-documented open questions and non-claims

The following remain open exactly at the Phase 47 boundary:

- how constituent StateMachines are declared or referenced;
- role-qualified binding when the same machine type appears more than once;
- ownership versus reference/coordination semantics;
- exact state-rule syntax and rule evaluation semantics;
- whether exactly-one rule matching is mandatory for every reachable
  configuration;
- how constituent committed transitions produce a new configuration;
- how a configuration change produces zero or one derived composite
  transition;
- whether composite transitions reuse exactly the existing transition model;
- execution ordering of constituent and composite actions;
- action algebra shape and generated representation;
- how local transactional versus after-commit effects are identified without
  embedding provider logic in CML;
- how nested Composite StateMachines are represented;
- how stable identity/version/source location is preserved through generation;
  and
- how the model is projected for diagrams and review.

This inventory does not claim that Composite StateMachine or CML `WORKFLOW`
syntax exists, does not add an action algebra, and does not define transaction,
compensation, recovery, compilation, or testability semantics. It does not
change Scala, the parser, the generated ABI, ComponentFactory behavior, CNCF
runtime behavior, or any cross-repository consumer.
