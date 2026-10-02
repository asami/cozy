# Workflow Scheduling and Lifecycle ABI Lowering

Semantic role: Design Notes (non-normative)
Current status: S67.2 producer accepted by local Step commit 43af3f1cfe3fd37ae922cfe984b94c8fd6147452; P67-S672-VAL-007 passed 195 tests, P67-S672-VAL-009 compiled ten Scala 3.3.8 sources, and independent review P67-S672-REVIEW-001 passed
Owner: Cozy Workflow producer lowering, Phase 67 parent
Update rule: update when lowering changes, keeping the paired source and ABI contracts authoritative; acceptance status requires parent evidence.
Phase: [67](../phase/phase-67.md), S67.2
Updated: 2026-10-02

## One collection-wide producer decision

The [ABI contract](../spec/workflow-scheduling-lifecycle-abi-contract.md)
extends the accepted
[source contract](../spec/workflow-scheduling-lifecycle-source-contract.md)
without changing normalization. The immutable
[`WorkflowDefinition`](../../src/main/scala/cozy/modeler/CompositeStateMachineDefinition.scala)
and [lifecycle types](../../src/main/scala/cozy/modeler/WorkflowLifecyclePolicy.scala)
already retain optional policy, ordered waits, resolved State names and source
locations. The generator consumes these values directly.

[`StateMachineWorkflowAbiGenerator`](../../src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala)
selects v3 if any collection member has lifecycle policy or waits. That
selection also enables all established invocation declarations, including
empty vectors. Otherwise it retains the v2 selection for invocation-bearing
collections and v1 for metadata-free or empty collections. Selection precedes
shared ABI, bootstrap, member and canonical JSON rendering so a mixed
collection cannot have incompatible member schemas.

[`ScalaGenerator`](../../src/main/scala/cozy/modeler/ScalaGenerator.scala)
already uses the shared projection for both public `modeler-scala` and
`modeler-scala-value` routes. It needs no caller or CLI change. Product paths,
package and object naming remain the existing contract. Bootstrap references
use collection order and direct `componentFactoryMetadata` values.

## Closed declarations and exact serialization

V3 adds CancellationMode with Cooperative, LifecyclePolicy, closed Timer and
Signal WaitTrigger variants, and WaitDeclaration. They retain the same
semantic distinction as source IR rather than introducing execution behavior.
WorkflowDescriptor appends defaulted lifecycle policy and waits after the
defaulted invocation vector. Existing seven- and eight-argument source
constructor clients can therefore compile against the new declaration.

Lifecycle serialization independently maps optional deadline and cancellation,
with the original source identity. Wait serialization independently retains
entry identity, declared State spelling, resolved canonical State identity,
closed trigger and source identity. Neither conversion resolves aliases again
nor substitutes the canonical name for source spelling.

The canonical sidecar remains compact and deterministic. V3 appends invocation
policies, lifecycle policy and waits after required SPI. Lifecycle order is
deadline, cancellation, source; wait order is identity, declared State,
canonical State, trigger, source. Timer and Signal carry kind first, followed
by their distinct payload. Optional values map to JSON null or Scala None;
vectors map to empty arrays or Vector.empty. A sourceless programmatic model
keeps null/None line identities.

Long values render directly as decimal strings into JSON numbers and decimal
Scala literals with `L`. This avoids floating-point precision loss at
Long.MaxValue. All strings pass through the established shared escaping
helper, including signal and wait strings outside the CML heading grammar.
No runtime defaults, deadline coercion or cancellation mode inference occurs.

The v1/v2 branches append no lifecycle text. Their existing state, Action,
operation, SPI, source, invocation, context, provider, continuation and
execution contracts remain intact. The
[v1 ABI contract](../spec/statemachine-workflow-generated-abi-contract.md)
and [v2 ABI contract](../spec/workflow-retry-timeout-abi-contract.md)
remain the compatibility authorities for those branches.

## Observable producer evidence

The paired
[`WorkflowLifecycleAbiGenerationSpec`](../../src/test/scala/cozy/modeler/WorkflowLifecycleAbiGenerationSpec.scala)
uses the actual
[tracked CML](../../src/test/resources/modeler/workflow-lifecycle-policy.cml)
through both public routes twice and compares all four ABI products with the
canonical projection. Located source checks retain Workflow version,
root/definition, lifecycle and wait correlation. Source variants describe
deadline-only, cancellation-only, lifecycle-only and wait-only meanings without
invocation policies, including exact optional absence, zero and Long maxima.
Alias and programmatic edge scenarios preserve spelling, escaped strings and
absent source identities.

The checked-in four-product v2 baseline is copied from original private
Phase67 capture bytes without regeneration. Repeated public v2 outputs are
compared as byte vectors with that independent fixture. Existing v1 hash-pin
specifications continue to describe v1 preservation. Empty input retains the
exact original JSON/bootstrap and legacy shared ABI.

A mixed collection starts with actual parsed legacy, policy and lifecycle
fixtures, then copies only test identities and edge values. It describes all
six complete structured projections and repeated determinism. Its shared ABI
equals the actual normal public v3 ABI. The scenario writes actual Realm
strings and canonical JSON into
`target/test-generated/workflow-lifecycle-compile`, preserving stable ordered
member identities and source paths for parent compilation. The normal public
`LifecycleWorkflowStateMachineWorkflow1.scala` remains under
`target/test-generated/workflow-lifecycle-normal`.

The adjacent typed consumer probe constructs old seven- and eight-argument
WorkflowDescriptor clients, reads the three metadata fields, exhaustively
matches Timer/Signal and Cooperative, and reads typed Long and signal data.
It is a test consumer fixture authored by the specification, not a product of
Cozy generation. The parent owns running specifications and the real Scala
3.3.8 compiler against these actual files. P67-S672-VAL-007 passed 195 tests;
P67-S672-VAL-009 successfully compiled all ten sources. Independent producer
review P67-S672-REVIEW-001 and comprehensive P67-FULL-REVIEW-001 passed.

## Static correlation and runtime ownership

Workflow version, source identities, State and wait identities can correlate
durable runtime records with source declarations. The producer does not create
such records. Relative deadline/delay semantics stay distinct from actual due
timestamps. Run/continuation IDs, revisions, history, scheduling, delivery,
cancellation, receiver admission and durable suspension/resumption belong to
CNCF Phase 82. Existing context and continuation types keep their contracts;
this projection adds no provider selection, runtime adapter or CML execution
grammar. Runtime and receiver completion require separate CNCF evidence.
