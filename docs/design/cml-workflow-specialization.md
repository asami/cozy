# CML Workflow Specialization Classification

status=accepted
phase=47
slice=CSM-05
updated_at=2026-09-07

## Authority and scope

This document is the accepted Phase 47 CSM-05 authority for classifying
candidate Workflow requirements against the accepted [CML Composite
StateMachine Semantics](cml-composite-statemachine.md) and [CML Composite
StateMachine Action Algebra](cml-composite-statemachine-action-algebra.md).
It freezes a semantic classification only. It does not add CML syntax, parser
or validation behavior, generated output, ABI, runtime admission, visualization,
an executable specification, or an external-repository contract.

Workflow is a specialization/profile of Composite StateMachine, not a parallel
language or a duplicate set of State, Transition, Trigger, Guard, Action, or
identity semantics.

## Ordered classification rule

Test every candidate in this order:

1. existing StateMachine semantic;
2. general Composite StateMachine semantic;
3. mandatory Workflow specialization; or
4. runtime policy/infrastructure or specialist-engine concern.

When a candidate has both model and runtime facets, classify each facet at its
own boundary. Do not force those facets into one artificial classification.

## Frozen candidate matrix

| Candidate | CML semantic classification | Runtime or specialist boundary |
| --- | --- | --- |
| Process-instance identity | Model definition, role, and configuration identity are general Composite StateMachine semantics. | Runtime occurrence identifiers and instance routing are runtime concerns. |
| Multi-subject correlation | Role-qualified optional subject references are general Composite StateMachine semantics. | Instance matching and routing are runtime concerns. |
| Durable wait/progression | State and Transition express waiting and progression. | Durable storage, timers, retry, and scheduling are runtime concerns. |
| Pending work | Logical intent is represented by the CSM-04 ActionProgram. | Queues, jobs, dispatch, and pending operational state are runtime concerns. |
| Completion/cancellation | Final or derived States and explicit transitions are existing or general StateMachine semantics. | Lifecycle coordination and job cancellation are runtime concerns. |
| Process history | Existing named history and CSM-02/CSM-04 causal provenance are model semantics. | Durable audit retention, query, and observability are runtime concerns. |
| Human work | No Workflow-only CML semantic is admitted. | Runtime policy or specialist-engine concern. |
| Connectors | No Workflow-only CML semantic is admitted. | Runtime policy or specialist-engine concern. |
| Compensation | No Workflow-only CML semantic is admitted. | Runtime policy or specialist-engine concern. |
| Rich scheduling | No Workflow-only CML semantic is admitted. | Runtime policy or specialist-engine concern. |

The CSM-02 constituent role, optional subject, configuration, and derived
transition contract remains unchanged. The CSM-04 ActionProgram remains pure
logical causal meaning; it does not admit queues, dispatch, physical execution,
transactions, retry, compensation, or recovery semantics.

## Accepted result and downstream boundaries

```text
mandatory_workflow_residual_semantics = []
```

No implicit Workflow syntax or profile language is introduced. A future
non-semantic profile or presentation marker may be preserved only when another
authority supplies it; it must not change model semantics, syntax, validation,
or runtime admission.

This classification does not decide downstream work:

- CSM-06 alone decides grammar and validation;
- CSM-07 alone decides generated IR, ABI, and any optional marker;
- CSM-08 alone decides runtime admission;
- CSM-09 alone decides visualization; and
- CSM-10 alone decides cross-repository proof.

## Explicit deferrals

The following remain outside CSM-05:

- CML source syntax, a `WORKFLOW` keyword, or profile syntax;
- parser and validation implementation;
- generated representation, ABI, and optional marker design;
- runtime persistence, routing, queues, jobs, dispatch, timers, retries,
  scheduling, cancellation, audit retention, query, or observability;
- transaction, reversibility, compensation, recovery, connectors, and
  specialist-engine implementation;
- visualization; and
- external repositories or cross-repository acceptance.

The current absence of CML `WORKFLOW` modeler syntax is inventory evidence
only. It does not decide whether a future syntax is warranted.
