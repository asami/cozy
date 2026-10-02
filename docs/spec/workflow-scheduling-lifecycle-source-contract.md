# Workflow Scheduling and Lifecycle Source Contract

Semantic role: Stable Semantics (normative)
Current status: accepted with the S67.1 local Step commit; source validation and independent review passed
Owner: Cozy Workflow source contract, Phase 67 parent
Update rule: update with the paired executable specification when accepted source semantics change; acceptance status requires parent evidence.
Phase: [67](../phase/phase-67.md), S67.1
Updated: 2026-10-02

## Ownership and grammar

A structural `WORKFLOW` definition may declare one direct `LIFECYCLE` section
and one direct `WAIT` section alongside its established
`COMPOSITE-STATEMACHINE`, API/SPI and
[invocation policy](workflow-retry-timeout-source-contract.md) sections.
Either section requires a valid CSM, even when the new section is empty.
Missing CSM never demotes a lifecycle declaration to narrative content.

```cml
### LIFECYCLE
deadline-millis = 60000
cancellation = COOPERATIVE

### WAIT
#### payment-timer
state = Pending
delay-millis = 250

#### completion-signal
state = Complete
signal = payment.completed
```

These sections belong directly to the Workflow definition. The complete
valid example is
[`workflow-lifecycle-policy.cml`](../../src/test/resources/modeler/workflow-lifecycle-policy.cml).
Document-root, Workflow-root, CSM, nested and other Workflow-section placement
is rejected. Reserved direct fields `deadline-millis`, `cancellation` and
`delay-millis` are rejected outside their owned locations, including
descendants and non-Workflow model roots. Existing `state` and `signal`
vocabulary retains its meaning in other model contexts.

Structural section positions are distinguished from existing opaque identity
headings. Ordinary Workflow, CSM, StateMachine, State, constituent, Action,
derivation and API/SPI/invocation entry identities may be named `Wait` or
`Lifecycle`. A Workflow-root child with such a name remains a definition
when it has a direct VERSION or CSM. Below existing identity entries,
misplaced new lifecycle structural headings are rejected. Other model
divisions retain their own structural vocabulary; the new parser checks
reserved direct fields recursively without globally reserving identity names.

Keys use the existing case/separator-insensitive normalization: lowercase
letters and digits are retained. Duplicate aliases are duplicates, with no
precedence or override. Closed fields, empty values and nested declarations
are checked before metadata is constructed. Existing execution-binding
vocabulary remains prohibited; these declarations introduce no provider,
script, orchestration, continuation or runtime binding grammar.

## Lifecycle policy

`LIFECYCLE` contains direct fields only, with no child sections. Its closed
field set is `deadline-millis` and `cancellation`. At least one field is
required. Empty sections, unknown fields, repeated scalar aliases and empty
values are syntax errors.

`deadline-millis` is relative to Workflow start and accepts unsigned base-10
integers from 1 through `Long.MaxValue` (9223372036854775807).
`cancellation` accepts only the exact trimmed token `COOPERATIVE`.
Case folding applies to keys, not cancellation values. No implicit deadline
or cancellation mode is manufactured.

## Wait declarations

`WAIT` has no direct fields and contains one or more ordered named entries.
Each entry has no nested children and exactly one direct nonempty `state`,
plus exactly one of `delay-millis` or `signal`. Its closed field set contains
only those three keys. Neither trigger, both triggers, duplicate aliases,
unknown or empty fields and nested content are syntax errors.

The trimmed declared State spelling resolves under the existing normalized
key to exactly one member of the CSM's `.states`. A constituent State name is
not a substitute for a CSM State. Missing or ambiguous references are
rejected. The declaration retains both spelling (`state`) and the resolved
canonical CSM State name (`stateIdentity`). Wait entry identities and resolved
canonical target States must each be unique under the same key. Entry order
is source order.

`delay-millis` accepts unsigned base-10 integers from 0 through
`Long.MaxValue`, relative to each entry into the target State. Zero is valid.
`signal` is a trimmed nonempty opaque logical signal identity; there is no
invented external registry, resolution rule or case normalization for its
value. Timer and Signal are closed, mutually exclusive trigger variants.

All numeric values use `[0-9]+` after trimming. Leading zeros are admitted.
Quoted, negative, plus-signed, decimal, exponent, unit-suffixed, empty and
overflow values are rejected. Range checking precedes lossless `Long`
conversion; values never truncate or wrap.

## Immutable IR, absence and correlation

`WorkflowDefinition` appends `lifecyclePolicy: Option[WorkflowLifecyclePolicy]`
defaulting to `None` and `waits: Vector[WorkflowWaitDeclaration]` defaulting
to `Vector.empty` after `invocationPolicies`. Old constructor calls remain
source compatible. Lifecycle-free and existing invocation-policy source
retain their established semantics.

`WorkflowLifecyclePolicy` retains optional `deadlineMillis`, optional
`WorkflowCancellationMode.Cooperative` (canonical `COOPERATIVE`) and the
lifecycle section's `WorkflowSourceIdentity`. A present policy has at least
one declared value; absent fields remain `None`.

`WorkflowWaitDeclaration` retains `identity`, declared `state`, canonical
`stateIdentity`, `WorkflowWaitTrigger.Timer(delayMillis)` or
`.Signal(identity)`, and the entry's `WorkflowSourceIdentity`. Source lines
come directly from `LogicalSection.location`, consistently with invocation
policies. Root/definition source correlation, Workflow version, CSM States
and wait identities provide static producer correlation. Run/continuation
IDs, due timestamps, revisions and stored history belong to runtime.

## Producer boundary and executable specifications

Normalization changes no CSM State, transition, logical Action, API/SPI or
invocation policy. It starts no timer, delivers no signal, cancels no run and
creates no durable storage. Public suspend/resume operations are not required
by this source contract. S67.2 owns v3 JSON/Scala/bootstrap projection; CNCF
Phase 82 owns receiver admission and scheduling, signals, cooperative
cancellation, persistence and runtime suspension/resumption.

[`WorkflowLifecycleCmlSpec`](../../src/test/scala/cozy/modeler/WorkflowLifecycleCmlSpec.scala)
specifies public ModelBuilder admission, old constructor defaults, optional
absence, policy-only/wait-only/combined declarations, both trigger variants,
order/source correlation, aliases and lossless bounds, strict failures,
uniqueness, reference validity, nesting, ownership and execution vocabulary.
The paired
[lowering design](../design/workflow-scheduling-lifecycle-source-lowering.md)
documents the normalization boundary. Validation and independent acceptance
are parent-owned and pending.
