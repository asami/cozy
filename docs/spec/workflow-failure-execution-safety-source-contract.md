# Workflow Failure and Execution Safety Source Contract

Semantic role: Stable Semantics (normative)
Current status: frozen S68.1 source semantics; implementation and executable specifications are authored with focused source validation passed. Independent review and Step commit remain pending.
Owner: Cozy Workflow source contract, Phase 68 parent.
Update rule: change this contract together with its paired executable specification and lowering design; record acceptance only from parent-owned validation, review and commit evidence.
Phase: [68](../phase/phase-68.md), S68.1 SOURCE.
Updated: 2026-10-02.

## Direct structural ownership

Each structural `WORKFLOW` definition may declare at most one direct
`FAILURE-POLICY` and at most one direct `EXECUTION-SAFETY` section alongside
its CSM, API/SPI, invocation, lifecycle and wait sections. Either new section
requires a valid `COMPOSITE-STATEMACHINE`, even when the section is empty.
Malformed structural metadata is never demoted to narrative content.
Normalized case/separator aliases count as the same section or field key.

```cml
### FAILURE-POLICY
#### capture-failure
participant = capture-payment-capability
default-classification = PERMANENT
##### transient.payment
classification = RETRYABLE
##### payment.rejected
classification = PERMANENT

### EXECUTION-SAFETY
logical-execution-identity-ref = order-run
duplicate-protection = REQUIRED
idempotency-key-ref = order-start
```

The [complete fixture](../../src/test/resources/modeler/workflow-failure-execution-safety-policy.cml)
also declares actual Service/Operation input and result contracts, invocation
retry and timeout, lifecycle and waits, and an Action with a separate required
idempotency key and compensation metadata.

Document-root and Workflow-root placement is invalid. The new sections cannot
appear as structural children inside CSM, other Workflow structural sections,
failure rules or ordinary identity entries. Ownership is determined by position:
Workflow definitions, CSM definitions and established State/Action/constituent/
derivation/API/SPI/invocation/policy/wait entry positions remain opaque identities,
including names `FailurePolicy`, `ExecutionSafety`, `Wait` and `Lifecycle`.
Workflow-root children with direct VERSION or CSM retain their definition role.
A failure-rule heading is always an opaque identity, even when spelled exactly
like any structural or execution-binding word. Its children are not identities.

`default-classification`, `logical-execution-identity-ref`, `duplicate-protection`
and `idempotency-key-ref` are reserved direct fields recursively across all
model divisions. They are rejected outside their exact owned positions.
`classification` is owned by failure rules in Workflow/CSM contexts; its generic
meaning remains available in unrelated divisions. Existing lifecycle and
invocation field ownership remains in force, with `action`/`participant` admitted
as legitimate failure-policy targets only at the entry position. Closed fields
and nesting admit no provider, script, orchestration, continuation, runtime
binding or retry-extension grammar.

## Failure policy and classification

`FAILURE-POLICY` has no direct fields and contains at least one ordered named
policy entry. Every entry has exactly one nonempty direct `action` or
`participant` target and exactly one direct `default-classification`. Those
are its complete direct field set. A default-only policy is valid.

Target semantics match the existing invocation policy. The target's kind and
trimmed spelling are retained separately from the canonical logical Action
identity. Action aliases use the existing case/separator normalization.
Participant aliases resolve through exactly one `REQUIRED-OPERATION` capability.
Only OPERATION or JUDGMENT Actions qualify; ADMISSION, provider identities,
unknown targets and ambiguous targets fail admission. Policy identities are
unique under the existing normalized key. At most one policy may resolve to
each canonical Action, including Action/Participant aliases of that target.

Each entry contains zero or more ordered failure-rule identity headings.
Every rule contains exactly one direct nonempty `classification` and no other
field or child section. Policy/rule order and each actual source heading line
are preserved. Empty headings, empty/repeated/unknown fields and deeper nesting
are syntax errors. Duplicate rule identities are rejected by exact trimmed
string within one policy; the same identity may occur on different targets.

`default-classification` and `classification` accept only the exact trimmed
tokens `RETRYABLE` or `PERMANENT`. Values never case-fold. A logical failure
identity is trimmed, retained verbatim and matched case-sensitively by the
future consumer. It is neither a CNCF exception class nor a catalog reference;
Cozy performs no local lookup. `payment.rejected` and `Payment.Rejected` are
distinct rules.

For an observed logical failure identity, the exact declared rule wins;
otherwise the explicit default applies. `PERMANENT` disallows technical retry.
`RETRYABLE` permits eligibility only inside an independently declared existing
retry budget. It creates no retry declaration, changes no maximum-attempts,
fixed delay or timeout, and guarantees no execution safety. Failure policy
without INVOCATION-POLICY is valid and manufactures no retries. Runtime maps
observations and timeouts to logical failure identities and enforces the result.
Backoff remains the existing fixed retry delay; no additional mode, exponential
backoff or jitter extension is introduced.

## Symbolic logical execution and duplicate protection

`EXECUTION-SAFETY` has direct fields only and no child sections. Its complete
field set is `logical-execution-identity-ref`, `duplicate-protection` and
`idempotency-key-ref`. The logical reference and protection mode are mandatory
and nonempty. References are trimmed and retained verbatim.

`logical-execution-identity-ref` names a runtime-supplied logical execution
identity. It is a symbolic reference, never an actual run ID, physical attempt
ID, computed key, expression, registry lookup or clock-based identity.
`duplicate-protection` is exactly `REQUIRED` or `NOT_REQUIRED`. REQUIRED
requires exactly one nonempty `idempotency-key-ref`, an opaque stable symbolic
reference to a runtime-supplied idempotency key. NOT_REQUIRED prohibits that
field even when empty. There is no implicit protection mode or key.

REQUIRED requests admission-level duplicate protection for a logical Workflow
execution. Runtime must reject admission if it cannot honor that protection;
it cannot silently weaken the declaration. Logical execution identity and the
resolved key must remain stable through retry/redelivery of the same execution
and distinguish a new logical execution. Independent physical attempt
correlation is required for timeout and late-completion handling.

Workflow admission protection and `ActionMetadata.idempotency` are independent
contracts. Workflow NOT_REQUIRED with Action REQUIRED is valid. Workflow metadata
does not replace, infer, enable or disable Action idempotency, compensation or
UnitOfWork semantics. Existing Action metadata is retained exactly.

## Immutable IR and producer boundary

`WorkflowDefinition` appends `failurePolicies: Vector[WorkflowFailurePolicy] =
Vector.empty` and `executionSafetyPolicy: Option[WorkflowExecutionSafetyPolicy] =
None` after `waits`. All old fields, ordering, defaults and source constructor
calls remain compatible; this does not promise binary case-class or unapply
compatibility. Absent sections stay absent.

The immutable failure classification variants have canonical values RETRYABLE
and PERMANENT. A policy retains identity, declared target, canonical Action,
explicit default, ordered rules and source correlation. A rule retains opaque
identity, classification and source correlation. Duplicate protection is
NotRequired or Required(idempotencyKeyRef); the safety policy retains the logical
identity reference, protection variant and section source correlation.

`CompositeStateMachineCml.definitions` and `.workflowDefinitions` share ownership
admission before ModelBuilder and Modeler extraction feed the producer consumers
ScalaGenerator and ModelGenerationTarget. CLI/library category rejection remains
the established source boundary. Normalization introduces no evaluator or
executor and changes no CSM, Action, API/SPI, invocation, lifecycle or wait data.

S68.2 plans deterministic v4 JSON/Scala/bootstrap projection, both public producer
routes, lossless string handling, exact legacy output compatibility and actual
generated Scala 3.3.8 consumer compilation. S68.1 does not implement v4 generation.
S68.3 plans the reproducible CNCF Phase 83 handoff. CNCF83 owns execution,
observation mapping, retry dispatch, storage, key composition, atomicity, replay
results and attempt correlation. Cozy source metadata supplies neither runtime
mechanisms nor proof of duplicate protection.

## Executable acceptance

The [executable source specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyCmlSpec.scala)
uses real CML and ModelBuilder admission for optional declarations, constructor
compatibility, preservation, target resolution, exact opaque identities, source
order/lines, symbolic references, key pairing, all closed field sets, canonical
tokens, structural ownership, invalid nesting, duplicates and forbidden execution
vocabulary. The paired [lowering design](../design/workflow-failure-execution-safety-source-lowering.md)
records the frozen normalization mechanism.

Parent validation runs the new suite together with WorkflowLifecycleCmlSpec,
WorkflowRuntimeControlCmlSpec, StateMachineApiSpiSpec, WorkflowCmlSpec and
CompositeStateMachineCmlSpec through serialized SBT, followed by `git diff --check`.
P68-S681-VAL-005 passed the six-suite accumulator: 353/353 tests across six
completed suites, with zero aborted suites. Test/compile and parent
`git diff --check` succeeded. Acceptance, independent review and Step commit
remain pending; no Phase closure is claimed.
