# Composite StateMachine First Workflow Modeling Direction

Date: 2026-09-05
Status: design decision

## Context

CML already has StateMachine integration experience extending through generated
metadata to CNCF runtime. Workflow has now been introduced as a CML modeling
concern, and the first coordination decision was to give it the same CML-first
source-to-runtime path rather than a CNCF-owned Workflow language.

Further discussion showed that many concepts initially described as Workflow
features are already StateMachine concepts. Activity/action, trigger/event,
guard/predicate, transition, and related behavior should not be duplicated only
because the containing model is called Workflow.

The next question was whether Workflow should be viewed as coordination of
multiple independent StateMachines or as one higher-level machine composed from
multiple constituent StateMachines. The latter gives stronger continuity with
CML and supports both a macroscopic single-machine view and a microscopic
multi-machine view.

## Decision

Introduce Composite StateMachine as the primary CML extension and treat Workflow
as a specialization/profile of Composite StateMachine.

```text
StateMachine
  +-- local/simple StateMachine
  +-- Composite StateMachine
        +-- Workflow
```

The exact Workflow-only semantic set is intentionally not fixed yet.

The governing rule is:

> Maximize reuse of Composite StateMachine semantics; introduce
> Workflow-specific semantics only when they are required for Workflow to exist
> and cannot be expressed cleanly as general Composite StateMachine behavior.

## Consequences for CML design

CML should first define how multiple constituent StateMachines are composed or
coordinated:

- stable constituent-machine identity;
- role-qualified binding;
- reference versus ownership semantics;
- higher-level composite state/configuration;
- mapping from constituent committed transitions to composite progression;
- reuse of State/Transition/Trigger/Guard/Action semantics;
- nesting rules;
- generated identity/source metadata;
- collapsed/expanded visualization metadata.

Only after that model works should a Workflow specialization add any residual
mandatory semantics.

## Workflow feature classification

Candidate Workflow features such as process-instance identity, correlation,
durable waiting, pending work, completion/cancellation, and process history are
not automatically Workflow-only.

Each must be tested against four categories:

1. existing StateMachine semantics;
2. general Composite StateMachine semantics;
3. mandatory Workflow specialization;
4. runtime policy/infrastructure or specialist-engine concern.

This prevents CML from drifting into a second BPM/workflow language beside its
StateMachine model.

## CNCF continuity

The intended runtime path remains continuous with StateMachine:

```text
CML Composite StateMachine / Workflow
 -> Cozy parse/normalize
 -> SimpleModeler generation
 -> generated typed model
 -> ComponentFactory bootstrap
 -> CNCF composite/workflow runtime
```

Constituent domain mutations continue to use their own StateMachine transition
boundary. The composite/workflow runtime coordinates committed outcomes and
uses normal Operation/Job invocation when further work is required.

## Work tracking

The Cozy work is planned as Phase 33:

`docs/phase/phase-33.md`

The active design proposal is:

`docs/notes/cml-composite-statemachine-workflow-proposal.md`

CNCF Phase 64 is being refined in parallel to consume this model rather than
invent its own independent Workflow semantics.
