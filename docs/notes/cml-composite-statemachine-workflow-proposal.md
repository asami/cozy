# CML Composite StateMachine and Workflow Proposal

Status: draft / non-normative
Date: 2026-09-05
Target: Cozy Phase 47 / CNCF Phase 64

## Purpose

Define the CML modeling direction for Composite StateMachine and Workflow while
preserving continuity with the existing StateMachine model and generated CNCF
integration.

## Core Principle

```text
StateMachine
  +-- local/simple StateMachine
  +-- Composite StateMachine
        +-- Workflow
             + workflow-specific mandatory semantics only
```

Workflow should not duplicate StateMachine concepts under new names. Composite
StateMachine is the first-class extension point. Workflow is introduced only as
a specialization/profile after the general composite structure has been used as
far as possible.

## Constituent StateMachines

A Composite StateMachine coordinates one or more constituent StateMachines.
Each constituent needs stable role-qualified identity because the same machine
type may appear more than once.

Conceptually:

```text
CompositeStateMachine
  constituents:
    order    : SalesOrderStateMachine
    payment  : PaymentStateMachine
    shipment : ShipmentStateMachine
```

The exact CML syntax remains open. The semantic model should distinguish:

- referenced/coordinated constituent machine;
- role within the composite;
- subject binding when required;
- constituent definition identity;
- constituent runtime occurrence/instance identity where applicable.

Reference/coordination should be the default semantic interpretation unless CML
explicitly models ownership/composition of the underlying subject.

## Higher-Level Machine Boundary

A Composite StateMachine presents one higher-level machine boundary while
preserving lower-level visibility.

```text
macroscopic view:
  OrderFulfillment = one StateMachine

microscopic view:
  OrderStateMachine
  PaymentStateMachine
  ShipmentStateMachine
```

This is useful for execution, visualization, and model review. CML metadata
should therefore preserve both the composite identity and constituent machine
structure.

## Derived Composite State

The preferred direction is to derive higher-level composite state from the
current role-qualified constituent state configuration.

```text
StateConfiguration
  order    = Accepted
  payment  = Authorized
  shipment = Waiting
      |
      v
CompositeStateRule
      |
      v
ReadyToShip
```

This keeps constituent StateMachines as the source of truth and avoids an
independent mutable business-status value that must be synchronized with them.

Conceptually:

```text
CompositeState = derive(constituent state configuration)
```

Rules are pure typed predicates over constituent states. They must be preserved
as model IR rather than opaque runtime callbacks.

A useful default validation contract is:

```text
0 matching rules  -> UnmappedConfiguration
1 matching rule   -> valid derived CompositeState
2+ matching rules -> AmbiguousConfiguration
```

Whether explicitly partial composites are allowed remains a design decision,
but absence of a mapping must never silently produce an arbitrary default.

## Derived Transition Graph

A constituent committed transition changes the constituent configuration. The
new configuration is re-evaluated against the composite-state rules.

```text
before configuration
  -> constituent transition commits
  -> after configuration
  -> derive old/new composite state
  -> derived composite transition if state changed
```

Therefore a Composite StateMachine transition graph can be derived from:

```text
constituent transition graphs
        +
composite-state rules
        |
        v
reachable configuration graph
        |
        v
derived composite transition graph
```

The derived graph should be a first-class analysis/projection artifact. It can
expose unexpected transitions and support visualization/review without forcing
CNCF or downstream components to independently reconstruct the same semantics.

## Static Rule Analysis

Because ordinary CML StateMachines use finite state vocabularies, Cozy can
analyze the composite rule system before runtime. Exact enumeration is ideal
for manageable configuration spaces; symbolic/structural analysis may be used
when the cartesian space becomes large.

Candidate findings include:

- uncovered reachable configurations;
- overlapping/ambiguous rules;
- impossible rules;
- redundant or subsumed rules;
- unreachable composite states;
- dead composite states;
- unexpected derived composite transitions;
- transitions that intentionally leave the composite state unchanged;
- multiple constituent transitions producing the same composite transition;
- excessive configuration-space complexity.

Rule diagnostics should include the involved constituent roles/states and
source locations so they can be surfaced by Cozy, CBD Support, and model-review
tools.

## Reuse of Existing StateMachine Semantics

Before adding new syntax, reuse existing semantics where possible:

- State
- Transition
- Trigger/Event
- Guard/Predicate
- Action/Effect
- initial/final semantics
- hierarchy/history already admitted by CML
- transition identity and priority/order
- source location and diagnostics

For example, Activity must not be introduced as Workflow-specific merely
because workflow engines use that word if existing StateMachine action/effect
semantics already express the required model meaning.

## Actions at Multiple Levels

Constituent and composite transitions may both declare actions.

Example:

```text
Payment.Pending -> Payment.Authorized
  action: recordAuthorization

OrderFulfillment.WaitingForPayment -> ReadyToShip
  action: requestShipment
```

These actions are not duplicates merely because they arise from one causal
chain. The lower-level action expresses behavior of the constituent machine;
the upper-level action expresses behavior of the higher-level composite
transition.

The model must preserve action provenance and deterministic causal order.
The default ordering candidate is:

```text
constituent transition/action
  -> constituent commit
  -> composite-state re-evaluation
  -> derived composite transition/action
```

Exact transaction/after-commit execution rules remain CNCF responsibilities.

## Typed Action Algebra and Free Program

CML action declarations should denote logical actions, not arbitrary embedded
Scala/functions or provider handles.

SimpleModeler should generate a typed action algebra / program IR that can be
composed independently of execution.

Conceptually:

```text
CML Action
   |
   v
Typed ActionOp algebra
   |
   v
Free Action Program
   |
   v
Interpreter
```

A constituent action program and a composite action program can then be
composed:

```text
constituentProgram *> compositeProgram
```

The implementation is expected to use a Free Monad or an equivalent free
program representation. The specification depends on the semantics, not on one
particular Scala library.

Required properties are:

- action composition is pure and inspectable before interpretation;
- ordering is explicit and deterministic;
- the same program can have production, test, simulation, review, or
  visualization interpreters;
- action operations carry enough typed identity for static analysis where
  possible;
- opaque callbacks/scripts are not the canonical generated contract;
- domain/provider handles are not embedded in the model IR.

The action algebra may be factored into reusable families such as Entity,
Event, Operation, Job, or Runtime actions. The exact coproduct/sum encoding is
an implementation decision for SimpleModeler/CNCF.

## Effect Planning Boundary

A free action program does not imply that all operations belong to one datastore
transaction.

CNCF should analyze/interpret the generated program under its execution
boundaries, conceptually separating:

```text
local / UnitOfWork-admitted effects
        +
after-commit / external effects
```

For example, an Entity mutation may participate in the local UnitOfWork while
an Operation invocation or Job submission may be planned for after commit.

CML should express the logical action, not provider transaction mechanics. If an
action's semantic class constrains execution phase, that class should be typed
in the action algebra rather than encoded as an arbitrary runtime string.

## Action Review

Because generated action programs are inspectable, static/model review can
potentially detect:

- duplicate logical external effects at constituent and composite levels;
- conflicting mutations to the same logical target;
- an external effect placed in a local-only execution phase;
- non-idempotent logical operations on retryable paths;
- action ordering cycles or unsupported composition;
- actions with no interpreter/provider binding.

These checks are secondary to state-rule correctness but are an important
reason not to model actions as opaque functions.

## Composite Coordination

The design must determine how a constituent committed transition participates
in the composite machine.

Conceptually:

```text
PaymentStateMachine
  Pending -> Authorized
       |
       | committed transition
       v
configuration changes
       |
       v
OrderFulfillment Composite StateMachine
  WaitingForPayment -> ReadyToShip
```

The composite layer must react to admitted/committed constituent transitions,
not infer successful state changes from mutable fields or bypass local
StateMachine enforcement.

## Workflow Specialization

Workflow is a specialization of Composite StateMachine only when some mandatory
semantics remain after generalization.

Candidate questions include:

- Does Workflow require an independent process-instance identity beyond a
  general CompositeStateMachineInstance?
- Is correlation across multiple subjects general composite semantics?
- Is durable waiting general composite semantics or specifically Workflow?
- Is pending work part of model semantics or CNCF runtime state?
- Are process completion/cancellation distinct from final-state semantics?
- Is process-oriented history a model concept or runtime/observability concern?

No candidate is automatically accepted as Workflow-specific.

## Classification Rule

Every candidate feature must be classified in this order:

```text
existing StateMachine semantic?
    yes -> reuse
    no
     |
     v
general Composite StateMachine semantic?
    yes -> add to Composite StateMachine
    no
     |
     v
mandatory for Workflow identity?
    yes -> add to Workflow specialization
    no
     |
     v
runtime policy / specialist-engine concern
```

## CML to CNCF Contract

The intended end-to-end architecture is:

```text
CML Composite StateMachine / Workflow
 -> parse / validate
 -> normalize semantic model
 -> static composite analysis
 -> SimpleModeler generation
 -> typed generated definitions + Action Program
 -> ComponentFactory bootstrap
 -> CNCF composite/workflow runtime + Action Interpreter
```

CNCF must not invent missing semantic structure. Unsupported required model
semantics should fail at parsing, generation, ABI admission, or runtime
admission as appropriate.

## Generated Metadata

The generated contract should preserve:

```text
CompositeMachineDefinition
  id
  version
  constituentBindings[]
  configurationSchema
  compositeStateRules[]
  derivedStates[]
  derivedTransitions[]
  actionPrograms[]
  workflowSpecialization?
  sourceLocation
```

A `ConstituentMachineBinding` should minimally preserve:

```text
role
machineRef
subjectRef?
sourceLocation
```

Definition references and runtime occurrence references must remain distinct.

Action metadata should preserve whether an action originated on a constituent or
composite transition and the logical action identity needed by the generated
program/interpreter contract.

## Visualization and Review

The model must support both collapsed and expanded views.

Collapsed:

```text
OrderFulfillment
  Started -> WaitingForPayment -> ReadyToShip -> Completed
```

Expanded:

```text
Order
  Draft -> Submitted -> Accepted

Payment
  Pending -> Authorized -> Paid

Shipment
  Waiting -> Preparing -> Shipped
```

Cross-links should show which constituent transition/configuration changes
produce a higher-level composite transition, which rule establishes each
composite state, and which actions are attached at each level.

Review overlays can show rule-coverage/ambiguity findings and action-analysis
findings directly on the model.

## Open Questions

1. Exact top-level/embedded CML syntax for Composite StateMachine.
2. Whether Workflow remains a keyword/declared specialization or a metadata
   profile on Composite StateMachine.
3. Constituent ownership/reference syntax.
4. Exact rule grammar for derived composite states.
5. Total versus explicitly partial rule-mapping policy.
6. Configuration-space analysis limits and symbolic fallback.
7. Nested Composite StateMachine rules.
8. Exact action algebra and generated Free-program representation.
9. Deterministic ordering when multiple constituent transitions or composite
   transitions are involved.
10. How logical action classification maps to CNCF UnitOfWork versus
    after-commit interpretation.
11. Which candidate semantics, if any, remain uniquely mandatory for Workflow.
12. Versioning/ABI compatibility with existing `STATEMACHINE` generation.

## Governing Rule

> Maximize reuse of Composite StateMachine semantics; introduce
> Workflow-specific semantics only when they are required for Workflow to exist
> and cannot be expressed cleanly as general Composite StateMachine behavior.

For actions:

> Model actions as typed composable programs; execute them only through an
> interpreter that owns runtime effects.
