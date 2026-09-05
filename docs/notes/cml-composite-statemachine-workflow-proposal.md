# CML Composite StateMachine and Workflow Proposal

Status: draft / non-normative
Date: 2026-09-05
Target: Cozy Phase 33 / CNCF Phase 64

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
OrderFulfillment Composite StateMachine
  WaitingForPayment -> WaitingForShipment
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
 -> SimpleModeler generation
 -> typed generated definitions/metadata
 -> ComponentFactory bootstrap
 -> CNCF composite/workflow runtime
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
  states/configuration
  transitions
  triggers
  predicates/actions
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

## Visualization and Review

The model must support both collapsed and expanded views.

Collapsed:

```text
OrderFulfillment
  Started -> Processing -> Completed
```

Expanded:

```text
Order
  Draft -> Submitted

Payment
  Pending -> Authorized

Shipment
  Waiting -> Shipped
```

Cross-links should show which constituent transition causes or satisfies a
higher-level composite transition. This metadata will be useful to CBD Support
and Textus BoK model visualization.

## Open Questions

1. Exact top-level/embedded CML syntax for Composite StateMachine.
2. Whether Workflow remains a keyword/declared specialization or a metadata
   profile on Composite StateMachine.
3. Constituent ownership/reference syntax.
4. Composite state configuration representation.
5. Nested Composite StateMachine rules.
6. Constituent committed-transition binding syntax.
7. Operation invocation representation and its relationship to existing
   action/effect semantics.
8. Which candidate semantics, if any, remain uniquely mandatory for Workflow.
9. Versioning/ABI compatibility with existing `STATEMACHINE` generation.

## Governing Rule

> Maximize reuse of Composite StateMachine semantics; introduce
> Workflow-specific semantics only when they are required for Workflow to exist
> and cannot be expressed cleanly as general Composite StateMachine behavior.
