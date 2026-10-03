# Workflow Semantic Outcome ABI Lowering

Semantic role: Stable Semantics (frozen design).
Current status: COMPLETE at the Cozy producer boundary. All three Steps and sole high P69-FULL-REVIEW-001 revision1 for P69-PLAN-E1 accepted, zero CPB/HYG/DEV. P69-FINAL-VAL-001 passed: 2,635 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 184 suites, 0 aborted; SBT/wrapper exit0 and `lock=released`. The distinct local release commit containing this record completes ordinary Phase69 closure. CNCF84 owns receiver integration, runtime execution/history and enforcement.
Owner: Cozy generated Workflow ABI, Phase69 parent.
Update rule: maintain with the normative ABI contract and executable specification; parent evidence alone changes acceptance status.
Updated: 2026-10-03.

## Settled additive producer boundary

The [normative ABI contract](../spec/workflow-semantic-outcome-abi-contract.md)
consumes the accepted [source contract](../spec/workflow-semantic-outcome-source-contract.md)
and its [source lowering design](workflow-semantic-outcome-source-lowering.md).
[Phase69](../phase/phase-69.md) and its [checklist](../phase/phase-69-checklist.md)
record accepted S69.1 SOURCE, S69.2 ABI and the
[CNCF84 handoff](../phase/phase-69-cncf-handoff.md). The sole full review passed;
final full suite passed; the containing distinct local release completes closure.

`StateMachineWorkflowAbiGenerator` remains the sole projection owner. One
collection predicate tests nonempty `semanticOutcomes`. It selects v5 and enables
all existing safety, lifecycle and invocation tiers. Otherwise the unchanged
v4 → v3 → v2 → v1 selection applies, including v1 for empty input. Shared VERSION,
JSON, every descriptor and bootstrap receive the same predicate. No new parser,
IR mutation, helper file, receiver implementation or architectural abstraction
is required. Existing normal/value Modeler routes retain their integration.

Only the v5 branch appends the Scala3 closed `SemanticOutcome` enum and immutable
transition/declaration types after safety types. Only v5 appends the defaulted
trailing outcome vector to WorkflowDescriptor. Original declarations, constructor
labels/defaults and formatting branches remain intact, preserving original
absence outputs. Old positional 7–12 and named v4 source construction use the
new empty default; new 13/named construction explicitly supplies declarations.
Binary case-class/unapply compatibility and caller schema-value rewriting are
outside this contract.

## Direct lossless projection

Private snake_case helpers render the closed outcome alternative, immutable
transition and declaration. They copy declared Action/Participant kind/spelling,
canonical Action, actual normalized role/edge and original qualified machine
reference. They neither resolve aliases nor query a graph. Source line absence
remains None/null. Declaration vectors retain order, including single/subset,
multiple Action and authored self-loop cases. Existing literal encoding carries
controls, Unicode and opaque names; old Int/Long bounds and nullable metadata
remain exact.

The JSON helper appends the ordered array after executionSafetyPolicy, with
identity, target(kind/identity), actionIdentity, closed outcome token,
transition(role/stateMachine/from/to/on), then source(line). Members with no
outcomes receive empty vectors under v5. Collection order and source order
remain independent and deterministic. The top-level sidecar remains
schemaVersion/workflows at the existing target/cozy path.

Member metadata continues to wrap its actual generated workflow. Bootstrap
continues to reference member.componentFactoryMetadata directly in collection
order. This retains typed descriptor identity without string lookup, binding
selection or factory instantiation. Retry/failure/lifecycle/wait, ActionExecution,
Continuation, API/SPI, CandidateAdmission, Action idempotency and UnitOfWork
meanings are not transformed by semantic rejection.

## Original compatibility and real compiler subjects

The [independent v4 provenance](../../src/test/resources/modeler/workflow-semantic-outcome-v4-baseline/provenance.md)
records original producer `de3b03530b163ede8c11440093b1a71c0a9fb034`, actual tracked
safety CML and original route products retained before Phase69 edits. The four
resources are exact copied original bytes; they are never synthesized from the
new generator. Existing v1 pins and v2/v3 baseline suites remain unchanged.

The [interface executable specification](../../src/test/scala/cozy/modeler/WorkflowSemanticOutcomeAbiGenerationSpec.scala)
authors the frozen twelve semantic families with adjacent Given/When/Then and
independently decoded complete records. Meaningful ScalaCheck sampling runs
in-memory with sixty selected successes, covering nonempty opaque strings,
all three outcome alternatives and both target kinds without per-sample IO.

Both actual public routes run twice and write ABI, bootstrap, ReviewWorkflow
member, canonical JSON and the typed consumer probe to the normal/value groups.
The mixed group saves actual Realm products for the exact ordered Legacy,
Policy, Lifecycle, Safety, Review, OutcomeOnly and OutcomeEdge members. The
first four come from old CML, Review from accepted source, OutcomeOnly from real
outcome-only source and OutcomeEdge from a bounded immutable IR copy carrying
escaped fields, source nulls and inherited numeric bounds. Its actual generated
descriptor is a compiler subject. The probe exercises old7–12/named constructors,
new13/named construction, all enum and target patterns, every transition/source
reader, ordered descriptors and bootstrap metadata.

The standard Scala directory suffix under each
`target/test-generated/workflow-semantic-outcome-{normal,value,compile}/` group
contains respectively four, four and ten compiler sources. Each canonical JSON
uses the group's standard target/cozy location. P69-S692-VAL-001 passed
101/101 across eight suites including native Scala2.12.18 Test/compile, with
zero failed/aborted/canceled/ignored/pending. P69-S692-VAL-002 compiled these
actual Scala3.3.8 normal/value/mixed sources4/4/10 sequentially in one invocation,
with distinct .class/.tasty products and three separate class/analysis directories
under target/phase69-generated-compile. Matching scala3-library_3,
dependencyOverrides Nil, normal shared caches and registered serialization
were used; both records report native SBT/wrapper exit0 and lock=released.
No handwritten substitute ABI supplies compilation.

## Ownership and acceptance

S69.1 is accepted at `8f066d2c475b4b2e58818ccde2dd8bded180e658` with actual
389/389 seven-suite validation P69-S691-VAL-001, whole twelve-path authoring and
preflight, and P69-S691-REVIEW-001 revision 1 protected high PASS. S69.2 is
accepted at `4f673a881f00d605fff6902e03499c7568a2a1e1` after the actual
behavior/compiler evidence above and P69-S692-REVIEW-001 revision1 protected
high PASS. Four original v4 byte vectors and unchanged v1-v3 suites support
compatibility. The [S69.3 handoff](../phase/phase-69-cncf-handoff.md) is accepted
after static validation, independent medium review and manual Step commit
`f9d1e5a766279b808ee95ce3409a5eda5dfd642f`. The sole full Phase review passed;
final full suite passed; the containing distinct local release completes closure.

CNCF84 owns decoder/bootstrap integration, semantic result/event association,
guards/current-state/concurrency, runtime history, diagnostics and enforcement.
No runtime result, event or transition execution is inferred by Cozy. Rejected
is terminal only through the authored graph. Supplied evidence needs no new
Iteration, counter/budget, termination/escalation policy or scheduler. Existing
Phase67 Hygiene remains separately unresolved. Producer Step acceptance
neither closes final Phase gates nor promises receiver completion.
