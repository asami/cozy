# CML Workflow and CNCF Runtime Coordination

Date: 2026-09-05
Status: cross-repository design coordination

## Context

CNCF Phase 63 treats StateMachine as a complete CML-to-runtime integration:

```text
CML StateMachine
 -> parse / normalize
 -> SimpleModeler generation
 -> typed generated definition
 -> ComponentFactory bootstrap
 -> CNCF StateMachine runtime
```

CML now includes Workflow as a modeling construct. CNCF Phase 64 is therefore
being revised so Workflow follows the same pipeline instead of being modeled
primarily as a CNCF-owned WorkflowDefinition with CML attached as a binding
surface.

## Decision

CML is the canonical declaration surface for built-in Workflow semantics.

The intended path is:

```text
CML Workflow
 -> Cozy parse / semantic normalization
 -> SimpleModeler generation
 -> typed generated Workflow definition / metadata
 -> ComponentFactory automatic bootstrap
 -> CNCF Workflow runtime
 -> WorkflowInstance
 -> Operation / Job
```

CNCF owns execution behavior, not a second Workflow modeling language.

## StateMachine continuity

Workflow integration should deliberately resemble the existing/planned
StateMachine integration.

```text
CML
 +-- StateMachine -> generated definition -> CNCF StateMachine runtime
 |
 +-- Workflow     -> generated definition -> CNCF Workflow runtime
```

The generated contracts differ because the model semantics differ, but the
source-authority, generation, ABI, bootstrap, and acceptance architecture
should be continuous.

## Cozy responsibilities to inventory/freeze

Before CNCF Phase 64 freezes its canonical Workflow runtime model, Cozy should
inventory the currently accepted CML Workflow implementation and document:

- Workflow declaration identity and versioning;
- trigger/entry constructs;
- step/activity identity and ordering;
- conditions/predicates;
- StateMachine/transition references and bindings;
- Operation references;
- terminal outcomes;
- any wait/event/timer or other constructs already accepted by CML;
- source-location diagnostics;
- semantic validation and unknown-reference rejection;
- current SimpleModeler generation behavior;
- generated metadata/provider surface;
- ComponentFactory-facing ABI/version requirements.

The inventory should distinguish implemented CML semantics from proposed syntax
or runtime-only legacy behavior.

## Generation rule

Runtime behavior must not depend on coincidental name matching.

Where CML relates a StateMachine transition to a Workflow entry or a Workflow
step to an Operation, generation should preserve explicit stable references.
Required executable conditions should be emitted as typed/closed generated
contracts rather than arbitrary runtime expression strings.

Unknown or unsupported required semantics should fail parsing, normalization,
generation, or runtime admission at the appropriate boundary rather than
silently degrade.

## Runtime boundary

CNCF is expected to own:

- WorkflowInstance lifecycle and persistence;
- trigger admission and duplicate-delivery handling;
- durable progression and recovery;
- concurrency/idempotency;
- generic Operation invocation;
- JobEngine linkage for asynchronous execution;
- authorization/context propagation;
- observability and correlation.

CML/Cozy owns what the Workflow means structurally. CNCF owns how an admitted
generated Workflow executes reliably.

## End-to-end acceptance

The representative acceptance must begin with real CML, for example a
SalesOrder/SalesStatus/SalesOrderWorkflow model, and cross Cozy/SimpleModeler
before CNCF execution.

A hand-written CNCF WorkflowDefinition or manually injected provider may remain
useful for focused tests but must not be accepted as evidence that the CML
Workflow integration works.

## Follow-up

Cozy should create or adjust its implementation phase after the CML Workflow
inventory identifies the exact parser/modeler/generator gaps required by CNCF
Phase 64.

The CNCF side is tracked by `goldenport-cncf/docs/phase/phase-64.md` and its
checklist. The two repositories should use one representative CML fixture and
record exact revisions for cross-repository acceptance.
