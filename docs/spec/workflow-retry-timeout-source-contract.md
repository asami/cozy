# Workflow Retry and Timeout Source Contract

Status: accepted; S66.1 COMMITTED
Phase: [66](../phase/phase-66.md)
Updated: 2026-10-02

S66.1 acceptance: commit `358ae6d1a233557b3e45087558ecff89e420f4af`,
review `P66-S661-REVIEW-001` PASS, validation `P66-S661A-VAL-002`
(166 passed / 0 failed). Source grammar and normalization remain unchanged.

## Ownership and canonical grammar

One optional direct `INVOCATION-POLICY` section belongs to each structural
`WORKFLOW` definition, alongside `COMPOSITE-STATEMACHINE`,
`REQUIRED-OPERATION` and provided `OPERATION`.

```cml
# WORKFLOW
## OrderProgress
version = workflow-v1

### COMPOSITE-STATEMACHINE
... established CSM declaration ...

### REQUIRED-OPERATION
#### capture-payment-capability
action = capture-payment

### INVOCATION-POLICY
#### capture-control
action = capture-payment
maximum-attempts = 3
fixed-retry-delay-millis = 250
execution-timeout-millis = 30000
```

The CSM placeholder above must be replaced with a valid existing CSM
declaration. Alternatively replace the single policy target field with
`participant = capture-payment-capability`.

The section occurs at most once and contains at least one entry. Its presence
is structural even if the required CSM is missing: such a Workflow is rejected
and never demoted to narrative. Entry headings are nonempty policy identities.
Identities are unique under the existing case/separator-insensitive identifier
normalization. The only allowed direct entry fields are `action`,
`participant`, `maximum-attempts`, `fixed-retry-delay-millis` and
`execution-timeout-millis`. Repeated scalars, unknown fields, fields directly
on the policy section, and nested structural content are syntax errors.

## Targets and logical invocation identity

Each entry has exactly one direct nonempty target: `action` or `participant`.
Neither, both, duplicates and unresolved targets are syntax errors.

An Action target resolves to one existing CSM logical Action of kind
`OPERATION` or `JUDGMENT`. A Participant target is an existing
`REQUIRED-OPERATION` capability identity. It resolves through the already
normalized capability mapping to that same eligible Action; it is never a
provider name or runtime placement. `ADMISSION` is deterministic local
StateMachine semantics and cannot receive invocation retry or timeout policy.

At most one policy may resolve to each logical Action. Action aliases and
Action/Participant aliases resolving to the same canonical Action are
duplicates. There is no precedence or override mechanism.

## Retry and per-attempt timeout values

| Field | Range | Meaning |
| --- | --- | --- |
| `maximum-attempts` | 1 through `Int.MaxValue` (2147483647) | Total attempts including the initial attempt |
| `fixed-retry-delay-millis` | 0 through `Long.MaxValue` (9223372036854775807) | Fixed delay before each later attempt |
| `execution-timeout-millis` | 1 through `Long.MaxValue` (9223372036854775807) | Bound on each Action/Participant attempt |

All values are unsigned base-10 integers. Whitespace is trimmed and leading
zeros preserve numeric meaning. Negative and plus-signed values, fractions,
scientific notation, unit suffixes, empty values and overflow are syntax
errors. Conversion must never truncate, wrap or manufacture defaults.

`maximum-attempts` and `fixed-retry-delay-millis` are an inseparable pair.
Both absent means no retry declaration; exactly one present is invalid,
including when timeout is also present. One attempt and zero delay are valid.
Timeout may occur independently. Each policy contains retry, timeout or both;
a target-only entry is invalid. Timeout is per attempt, never a Workflow
deadline or aggregate timeout. Delay introduces no timer or state expansion.

## Absence, correlation and placement

An omitted section yields an empty policy vector. Omitted retry or timeout
within a valid entry remains `None`. Normalization synthesizes no defaults.
The immutable source model retains entry order, policy identity, declared
target kind and spelling, resolved canonical Action identity, typed values and
the policy entry's source location.

Policy sections are legal only directly under a Workflow definition, never
inside CSM structure, Actions, required/provided-operation sections or their
entries. Direct policy declarations on those other structures are rejected;
the existing required-operation `action` mapping and CSM occurrence `action`
references retain their established meaning. Provider/runtime/script/
orchestration fields and `RETRY` headings remain banned as executable grammar.

## Source-only boundary and compatibility

`WorkflowDefinition.invocationPolicies` is an appended field defaulting to
`Vector.empty`, retaining old constructor source compatibility. Policies are
Workflow wrapper metadata; states, transitions, logical Action kinds,
required/provided operations and direct CSM sources retain their meaning.
The lowering design is documented in
[Workflow Retry and Timeout Source Lowering](../design/workflow-retry-timeout-source-lowering.md).

S66.1 does not project policy metadata into generated ABI or execute it.
S66.2 owns optional v2 Workflow ABI/bootstrap for policy-bearing inputs;
see the frozen [generated ABI contract](workflow-retry-timeout-abi-contract.md).
Metadata-free inputs retain byte-identical v1 output. S66.3 owns CNCF metadata
admission and a real CML-agnostic consumer fixture. CNCF Phase81 owns runtime
enforcement. Deadline, Timer/Wait, Cancellation, FailurePolicy, general
idempotency, backoff/jitter and dedicated Iteration are outside this contract.

## Executable specifications

`WorkflowRuntimeControlCmlSpec` exercises real CML through ModelBuilder and
typed normalization: ordered source identities, Action/Participant resolution,
absence, numeric limits and invalid syntax, uniqueness, malformed structure,
wrong placement, deterministic CSM preservation and Judgment/Admission
eligibility. S66.1 focused validation and independent Step acceptance are
complete with the receipts above; S66.2 generated-product validation and
acceptance remain pending.
