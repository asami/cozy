# Workflow Failure and Execution Safety Source Lowering

Semantic role: Stable Semantics (frozen design)
Current status: S68.1 source lowering authored; focused source validation passed. Independent review and Step commit remain pending. v4 generation remains planned S68.2 work.
Owner: Cozy Workflow source lowering, Phase 68 parent.
Update rule: update when lowering changes together with the authoritative source contract and executable specification; acceptance requires parent evidence.
Phase: [68](../phase/phase-68.md), S68.1 SOURCE.
Updated: 2026-10-02.

## Boundary and immutable extension

The [source contract](../spec/workflow-failure-execution-safety-source-contract.md)
freezes source admission and runtime meanings. `WorkflowExecutionSafetyPolicy.scala`
supplies immutable classification, rule, policy and duplicate-protection types.
`WorkflowDefinition` appends failurePolicies and executionSafetyPolicy after waits,
with empty/None defaults. Old constructor fields and named parameter labels are
preserved. No binary case-class/unapply compatibility promise is made.

The source preserves declared target kind/spelling separately from canonical
Action identity; opaque failure identity strings separately from normalized
policy identity uniqueness; symbolic logical execution/key references separately
from actual runtime IDs and resolved values. The IR evaluates none of these.

## Cohesive ownership traversal

Both existing CompositeStateMachineCml extraction entry points delegate metadata
ownership admission to package-private `WorkflowExecutionSafetyPolicyCml` before
existing definition normalization. The complete prior lifecycle ownership helper
group (sets and traversal only) moves to this cohesive helper and gains the
failure/safety ownership roles. WorkflowLifecyclePolicyCml, invocation normalization,
CSM and Action parsing remain in their established responsibilities.

The traversal recognizes Workflow definitions by existing direct VERSION/CSM
shape, structural CSM containers and established identity entry positions. New
section spellings in those identity positions remain legal opaque names. Failure
entries and their rule headings are additional identity positions: even exact
LIFECYCLE/WAIT or FAILURE-POLICY/EXECUTION-SAFETY rule names are retained. Below a
rule or ordinary entry, structural metadata is misplaced. The normalizers enforce
their closed child grammar as well.

Lifecycle fields retain their recursive ownership checks. Four new dedicated
field keys are recursively reserved across model roots. Generic classification
is checked within Workflow/CSM contexts and permitted in unrelated divisions.
The existing invocation ownership traversal exempts only the direct FAILURE-POLICY
container needed for legitimate policy target fields; the failure normalizer's
closed entry/rule field sets then enforce those positions. Other invocation
ownership rejections remain unchanged.

The integration removes the complete old ownership group from CompositeStateMachineCml
rather than adding an unrelated parser extraction. Its source must remain no
larger than the original 1,097 lines. This bounds new parser growth without
claiming the historical parser-size Hygiene resolved.

## Normalization sequence and failures

CompositeStateMachineCml classifies the new direct Workflow sections as structural,
requires a valid CSM for either declaration and rejects normalized duplicate
section aliases. Existing CSM, Actions and API/SPI resolution complete before
failure and execution-safety normalization receives the resolved Actions,
required capabilities and the established direct-field reader.

The reader retains raw duplicate scalar fields; the helper normalizes key names,
checks allowed fields and duplicate aliases before building maps. Empty values
fail. Failures use the existing `RAISE.syntaxErrorFault` source admission boundary.
Closed fields and child depth reject provider/runtime execution grammar.

Failure entries retain source order. Action resolution follows existing
case/separator keys; Participant resolution requires exactly one existing required
capability. Only OPERATION and JUDGMENT qualify. Canonical target uniqueness is
checked independently from normalized policy identity uniqueness. Rules retain
source order and exact trimmed identity strings; exact duplicates fail only
within their policy. Classification values match canonical trimmed tokens
without case normalization. Entry and rule source lines come from the actual
LogicalSection.location.

Execution safety has no children. It requires a logical identity reference and
an exact duplicate protection token. REQUIRED constructs Required with one
nonempty key reference; NOT_REQUIRED constructs NotRequired and rejects any
declared key. Source correlation comes from the actual section heading.

## Consumer meaning and independent contracts

At runtime, exact failure rule matching wins over an explicit default.
PERMANENT forbids technical retry, while RETRYABLE only permits eligibility
within a separately declared existing invocation budget. Defaults never create
retry declarations. Maximum attempts, fixed retry delay and timeout remain
unchanged; no backoff mode or jitter extension is added.

Logical execution identity and idempotency key references denote symbolic
runtime-supplied values. CNCF83 must maintain stable resolved values for the same
execution under retry/redelivery, distinguish new logical executions and keep
independent attempt correlation for timeouts and late completion. REQUIRED
protection must fail runtime admission if unsupported. Cozy supplies declarations
without storage, atomicity, computed keys, dedup dispatch or replay machinery.

Action idempotency, transaction and compensation metadata are copied unchanged.
Workflow NOT_REQUIRED with Action REQUIRED remains valid. Neither declaration
level infers or modifies the other, and no UnitOfWork semantics are changed.

## Producer follow-through and acceptance

ModelBuilder and Modeler workflow extraction inherit this normalized source model
for ScalaGenerator and ModelGenerationTarget. Both source extraction routes,
standalone CSM and unrelated model divisions share ownership checks; existing
CLI/library category admission remains active.

The [executable specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyCmlSpec.scala)
constructs real source variants from the complete fixture and the established
candidate/admission fixture. Tables retain the full malformed-input and ownership
matrix while grouping observable contracts below approximately 80 behavior
examples. All scenarios place semantic Given/When/Then adjacent to setup,
admission and should-matchers. The parent owns the selected six-suite accumulator,
compilation, diff checking and fresh source acceptance review.

S68.2 will separately implement and prove v4 producer projection, lossless
JSON/Scala/bootstrap, exact legacy outputs, both public generator routes and
generated Scala 3.3.8 typed-consumer compilation. S68.3 will publish the same-Phase
handoff record to CNCF83. Neither is claimed implemented or accepted by this source
Step. CNCF runtime completion is a recipient responsibility, not a pending Cozy
completion requirement.
