# Workflow Scheduling and Lifecycle Source Lowering

Semantic role: Design Notes (non-normative)
Current status: accepted with the S67.1 local Step commit; source validation and independent review passed
Owner: Cozy Workflow source lowering, Phase 67 parent
Update rule: update when source lowering changes, keeping the source contract authoritative; acceptance status requires parent evidence.
Phase: [67](../phase/phase-67.md), S67.1
Updated: 2026-10-02

## Workflow wrapper ownership

The [source contract](../spec/workflow-scheduling-lifecycle-source-contract.md)
adds immutable metadata to `WorkflowDefinition`, beside the established
[invocation-policy lowering](workflow-retry-timeout-source-lowering.md).
Trailing constructor defaults preserve existing source calls:
`lifecyclePolicy = None`, `waits = Vector.empty`. Absent source stays absent;
the normalizer constructs no policy defaults or waits.

The public types live in `WorkflowLifecyclePolicy.scala`.
`WorkflowCancellationMode` is closed with `Cooperative`, canonically
`COOPERATIVE`. `WorkflowWaitTrigger` is closed with `Timer(delayMillis)` and
`Signal(identity)`. Policies and wait declarations retain their source
identities. A wait retains its declared State spelling separately from the
canonical CSM State name, preserving source intent and resolved correlation.

## Normalization sequence

`CompositeStateMachineCml` checks section/field ownership across model roots,
classifies direct Workflow lifecycle sections as structural, requires a CSM
when either is present, and rejects duplicate sections. The established CSM
normalization and validation completes before lifecycle metadata is created.
Existing REQUIRED-OPERATION, provided OPERATION and INVOCATION-POLICY behavior
is retained.

Ownership traversal distinguishes structural keys from opaque identities.
Workflow children with VERSION or CSM are definition identities even when
named WAIT/LIFECYCLE. CSM definitions and their constituent/State/Action/
derivation entries, and API/SPI/invocation entries, retain ordinary names.
New lifecycle headings below those entries are misplaced structural content.
Standalone CSM roots use the same roles; other model roots receive recursive
reserved-field checks while their existing parsers own structural vocabulary.

The package-private `WorkflowLifecyclePolicyCml` receives the same existing
direct-field extractor used by invocation policies. The extractor retains
raw duplicate scalar declarations instead of relying on a map that could
drop duplicates. The lifecycle normalizer applies closed field sets, checks
duplicates under the existing case/alphanumeric key, requires nonempty
values and rejects nested content. Cancellation compares the trimmed token
exactly. Unsigned decimal values are parsed as `BigInt`, range checked and
converted losslessly to `Long`.

Wait normalization keeps named entries in source order. It resolves a State
against the already validated `CompositeStateMachineDefinition.states`,
requires exactly one candidate, selects exactly one Timer or Signal trigger,
and checks unique entry identities and canonical target States. Signal
values remain opaque logical names. Lifecycle section and wait entry source
lines use `LogicalSection.location` directly. Malformed declarations fail
through the established `RAISE.syntaxErrorFault` / public ModelBuilder
boundary; no invalid metadata is silently omitted.

## CSM and runtime separation

Deadline is relative to Workflow start; timer delay is relative to each
entry into a target State. Those meanings describe source metadata without
expanding CSM states, transitions or Actions. Invocation timeout remains a
per-attempt declaration and is distinct from Workflow deadline. Existing
invocation policy/API/SPI declarations coexist without changing their
meaning.

Workflow identity/version, root/definition source correlation, State identity,
wait identity and entry source location supply static durable-wait
correlation. Per-run and continuation IDs, due timestamps, mutable revisions
and history are runtime data. The source IR makes no persistence or delivery
claim and requires no public suspend/resume operation.

## Subsequent producer and receiver work

S67.1 establishes strict source admission and immutable declarations.
S67.2 owns deterministic v3 JSON, Scala and bootstrap generation and exact
v1/v2 compatibility. This Step changes no generator. CNCF Phase 82 owns v3
admission, scheduling, signal delivery, cooperative cancellation and durable
suspension/resumption. Runtime implementation and CNCF full-suite completion
are outside Cozy's producer acceptance gates.
