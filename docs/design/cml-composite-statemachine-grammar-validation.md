# CML Composite StateMachine Grammar and Validation

status=accepted
phase=47
slice=CSM-06
updated_at=2026-09-07

## Authority and scope

This is the accepted CSM-06 authority for the CML grammar and normalizing
semantic validation of the Composite StateMachine surface. It realizes the
meaning frozen by [CSM-02](cml-composite-statemachine.md), its finite flat
state analysis boundary from CSM-03, and the logical-action vocabulary frozen
by [CSM-04](cml-composite-statemachine-action-algebra.md). Cozy validates this
surface during ordinary `Modeler.ModelBuilder` construction and deliberately
retains no Composite StateMachine model, generated IR, ABI, or runtime object.

The canonical root is `COMPOSITE-STATEMACHINE`; CML case and punctuation
normalization applies to its headings and references. `WORKFLOW` is not an
admitted CML root, keyword, profile, marker, or parallel vocabulary in this
slice. CSM-05 established `mandatory_workflow_residual_semantics = []`.

## Canonical surface

Each named definition contains exactly one `CONSTITUENT`, `STATE`, and
`DERIVATION` section. It may contain one each of `INITIAL`, `ACTION`,
`CONSTITUENT-ACTION`, and `DERIVED-ACTION`.

```cml
# COMPOSITE-STATEMACHINE

## OrderProgress

### CONSTITUENT

#### payment
state-machine = PaymentLifecycle
subject = payment
subject-type = PaymentCommand

#### fulfillment
state-machine = FulfillmentLifecycle

### STATE

#### Pending

#### Complete

### DERIVATION

#### pending
state = Pending
when = payment.Awaiting, fulfillment.Waiting

#### complete
state = Complete
when = payment.Paid, fulfillment.Shipped

### INITIAL

payment.Awaiting, fulfillment.Waiting

### ACTION

#### capture-payment
kind = OPERATION
operation = capturePayment
input = payment.subject

### CONSTITUENT-ACTION

#### payment-captured
role = payment
from = Awaiting
to = Paid
on = captured
placement = transition
action = capture-payment

### DERIVED-ACTION

#### order-completed
from = Pending
to = Complete
action = capture-payment
```

A constituent identity is its unique role. It references `STATE-MACHINE`, and
an optional `SUBJECT` requires `SUBJECT-TYPE`. The referenced machine is
selected through the existing Modeler StateMachine selection rule, and must
have a nonempty finite direct state universe. Hierarchical constituent machines
are rejected: CSM-03 hierarchy lowering remains deferred rather than gaining
new nested-composite syntax.

Every derivation has a stable section identity, one declared composite `STATE`,
and `WHEN` mappings written as `<role>.<state>`. A mapping contains exactly one
known state for every declared role. `INITIAL`, when present, uses the same
complete mapping form. With an initial mapping the validator traverses the
finite direct transition product and rejects reachable uncovered or ambiguous
configurations. Without it, the validator makes no exact reachability claim;
it never chooses a derivation by order, priority, default, or fallback.

`ACTION` declares a global logical identity only. `kind = OPERATION` and
`operation = <CML Operation>` resolve a unique normalized CML Operation. An
optional `input = <role>.subject` requires that typed constituent subject and
must match the Operation input type; absence and presence must likewise agree
with whether the Operation declares an input. This grammar admits no raw
expression, script, provider, transaction, retry, or compensation syntax.

`CONSTITUENT-ACTION` carries its own stable occurrence identity, `ROLE`,
`FROM`, `TO`, `ON`, `PLACEMENT`, and `ACTION`. `PLACEMENT` is exactly `exit`,
`transition`, or `entry`; the role/from/to/on tuple resolves a constituent
transition. Equal actions are not deduplicated. `DERIVED-ACTION` has a stable
identity, distinct declared composite `FROM` and `TO`, and an `ACTION`; its
placement is fixed as `derived-transition` and is not authored separately.

The future producer-side v1 metadata contract is frozen separately by the
[CML Action Producer Metadata](cml-action-producer-metadata.md) authority, with
the producer-only compensation association defined by the accepted [CML Action
Compensation Handler Binding](cml-action-compensation-handler-binding.md)
authority. That contract is additive and is not part of this currently
implemented grammar: Actions with no Phase 47.1 metadata remain valid, while
the future `EFFECT`, `TRANSACTION`, `IDEMPOTENCY`, `IDEMPOTENCY-KEY`, and
`COMPENSATION-HANDLER` surface is not currently parsed or accepted. The current
exclusion of transaction, retry, and compensation metadata remains in force
until ACTX-04 implements parser/static validation and binding resolution.

## Diagnostics and preservation

The normalizer diagnoses duplicate roles, unknown StateMachines, missing or
duplicate role mappings, unknown state mappings, invalid initial mappings,
reachable uncovered/ambiguous derivations, invalid Operation/action bindings,
unknown action occurrences, unresolved constituent transitions, and supplied
`WORKFLOW` roots. These are validation diagnostics, never declaration-order
resolution rules.

CSM-02 constituent identity, configuration, derivation, and derived-transition
meaning are unchanged. CSM-03's hierarchy-lowering and broader static-analysis
semantics remain bounded as stated above. CSM-04's logical Actions and
provenance-preserving occurrences are validated only; no ActionProgram is
created or interpreted.

## Explicit deferrals

CSM-07 owns generated definition IR and ABI. CSM-08 owns CNCF bootstrap and
runtime admission. CSM-09 owns visualization and projection metadata. CSM-10
owns cross-repository acceptance. ACTX-02 owns the future producer-side
metadata meaning referenced above; ACTX-03 owns the accepted producer-only
compensation association, while ACTX-04 owns its later parser/static
validation and binding resolution and ACTX-05 owns deterministic generation.
This slice also does not decide runtime execution, transaction, recovery,
provider integration, visualization, or a Workflow language.
