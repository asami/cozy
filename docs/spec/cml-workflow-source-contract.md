# CML WORKFLOW Source Contract

status=accepted
phase=62
slice=WFL-62-01A
updated_at=2026-10-02

## Scope

`WORKFLOW` is a first-class top-level CML source root. It is a source-model
contract only: it does not declare a workflow run, persistence identity,
provider, engine, execution mode, REST surface, or generated projection.
Phase66 adds optional source invocation-policy metadata as specified in
[Workflow Retry and Timeout Source Contract](workflow-retry-timeout-source-contract.md).
That declaration does not execute retries or timeouts.

Every structural Workflow lowers to one existing
`CompositeStateMachineDefinition`. The Composite StateMachine remains the
single owner of state, constituent, derivation, transition, and logical Action
semantics.

## Literate Model classification

A `WORKFLOW` source uses the CML Literate Model's concurrent layers:

- Structural DSL: `COMPOSITE-STATEMACHINE`, `REQUIRED-OPERATION`, the established
  provided `OPERATION` section, and Phase66's optional `INVOCATION-POLICY`.
- Direct metadata DSL: one direct `version` value on each structural Workflow
  definition.
- Narrative prose: ordinary prose and nonstructural headings, which remain
  non-executable and cannot create or override workflow semantics.

A direct child of a `WORKFLOW` root is a structural Workflow definition only
when it has a direct `COMPOSITE-STATEMACHINE` child. A child with only prose or
nonstructural headings is narrative and does not create a Workflow definition.
A direct `REQUIRED-OPERATION`, provided `OPERATION`, or `INVOCATION-POLICY`
child without that structural sibling is malformed structural syntax and is
rejected; it is never reclassified as narrative or silently discarded.

## Canonical source form

```cml
# WORKFLOW

## OrderProgress

version = workflow-v1

This text is narrative context only.

### Overview

The heading is narrative and is not an executable model section.

### COMPOSITE-STATEMACHINE

#### CONSTITUENT

##### payment
state-machine = PaymentLifecycle
subject = payment
subject-type = PaymentCommand

#### STATE

##### Pending

#### DERIVATION

##### pending
state = Pending
when = payment.Awaiting

### REQUIRED-OPERATION

#### capture-payment-capability
action = capture-payment
```

The `COMPOSITE-STATEMACHINE` child has the established CSM grammar. Its
Workflow definition name is used as the lowered CSM identity; the child does
not introduce a second state-machine identity or semantic model.

## Version metadata

Each structural Workflow definition requires exactly one direct, nonempty
`version` metadata value. Its value is opaque source metadata. This contract
does not apply SemVer parsing, comparison, ordering, compatibility policy, or
release semantics.

`VERSION` as a nested heading is not a substitute for the direct metadata
value. Missing, duplicate, and empty direct values are rejected.

## Required capability boundary

An optional `REQUIRED-OPERATION` section declares capability requirements. Its
entry heading is a unique capability identity and its one direct `action`
field refers to a unique Action declared by the lowered Composite StateMachine.
The referred Action must be an `OPERATION` or `JUDGMENT` Action. An `ADMISSION`
Action retains deterministic local StateMachine semantics and cannot satisfy
this invocation capability boundary.

The normalized mapping carries only:

- capability identity;
- reference to the existing declared logical Action; and
- entry source identity.

It does not select a provider, supply an `InvocationBinding`, choose direct or
orchestrated invocation, create a Continuation, or prescribe control or
runtime behavior. A declared Action may be the target of at most one required
capability mapping. Phase66's `INVOCATION-POLICY` may refer to that capability
as a Participant target, retaining its declared identity and the mapped
canonical Action; it still does not bind a provider.

## Validation

The normalizer rejects:

- duplicate `COMPOSITE-STATEMACHINE` children or multiple
  `REQUIRED-OPERATION` sections;
- misplaced direct CSM structural sections such as `STATE` or `ACTION`;
- missing, duplicate, or empty direct `version` metadata;
- unknown Actions, Actions outside `OPERATION`/`JUDGMENT`, duplicate capability identities,
  and duplicate Action mappings;
- nested or undeclared structural content in a required-operation entry; and
- `INVOCATION-BINDING`, `ORCHESTRATION`, `CONTINUATION`, `PROVIDER`, `SCRIPT`,
  `RETRY`, `EXECUTION`, `CONTROL`, `ACTION-EXECUTION`, `IMPLEMENTATION`,
  `BINDING`, or `RUNTIME` vocabulary authored as Workflow grammar.

Narrative prose may discuss those concepts as prose, but it cannot be shaped
as direct Workflow metadata or structural headings to declare executable
semantics.

The Phase66 exception is exactly one optional direct `INVOCATION-POLICY`
section on a Workflow definition. It declares retry and per-attempt timeout
metadata for an Action or required capability. Its full grammar and rejection
rules are in the linked canonical source contract. Direct policy fields on
other Workflow structures and policy sections inside the CSM are rejected.
The existing `RETRY` heading and execution-binding vocabulary bans remain.

## Source correlation and compatibility

Normalization retains separate source identities for the `WORKFLOW` root and
its structural definition. The lowered `CompositeStateMachineDefinition`
retains its established structural-source identity. Existing direct
`COMPOSITE-STATEMACHINE` sources remain a distinct source form and retain
their behavior and generated output unchanged.
