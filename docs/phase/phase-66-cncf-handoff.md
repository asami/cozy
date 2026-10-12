# Phase66 Cozy Producer Delivery and CNCF Integration Handoff

Current status: Cozy Phase66 producer delivery COMPLETE; CNCF integration and remaining validation pending
Owner: Cozy CML/generated producer; CNCF owns consumer integration, runtime and CNCF validation
Update rule: preserve accepted producer/fixture evidence and record CNCF-owned follow-up separately
Updated: 2026-10-02

## Accepted predecessor authority

[Phase66](phase-66.md) and its [checklist](phase-66-checklist.md) record COMPLETE
Cozy producer delivery under the user-selected 2026-10-02 ownership boundary.
S66.1 was accepted at `358ae6d1a233557b3e45087558ecff89e420f4af`, parent
`97bf6c1e641bb245cc425341523f2c31cbcda636`, review `P66-S661-REVIEW-001`
PASS, validation `P66-S661A-VAL-002` 166 passed / 0 failed.
The accepted [source contract](../spec/workflow-retry-timeout-source-contract.md)
is unchanged.

S66.2 is COMMITTED and accepted at `e3262754023e0afc42bb5fcd3aeecf1cca0ab78e`,
parent `358ae6d1a233557b3e45087558ecff89e420f4af`:
`P66-S662-REVIEW-001` PASS; `P66-S662A-VAL-001` 152 passed / 0 failed;
`P66-S662A-VAL-002` three actual generated Scala 3.3.8 files compiled into
93 classes, SBT exit 0, lock released. These are producer receipts, not S66.3
or Phase acceptance by themselves. Complete Phase closure is recorded below.
See the accepted [v2 producer contract](../spec/workflow-retry-timeout-abi-contract.md)
and unchanged [Phase62 v1 contract](../spec/statemachine-workflow-generated-abi-contract.md).

## Producer input and exact byte attribution

The tracked reproducible inputs and generator at the S66.2 commit are:

| Producer path | SHA-256 |
| --- | --- |
| [src/test/resources/modeler/workflow-invocation-policy.cml](../../src/test/resources/modeler/workflow-invocation-policy.cml) | `4b61b539f6beae2ffb1c92c48e93350aa62ed43b741f0d41a16d83d852ac812a` |
| [src/test/resources/modeler/workflow-invocation-policy-legacy.cml](../../src/test/resources/modeler/workflow-invocation-policy-legacy.cml) | `c1bb5acb021b14de4935c329a1e4c7982c77845f0cdd98f80cd9077583e8e795` |
| [src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala](../../src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala) | `fbc5cd5649165d521c225f38f3e98d4477f4a7759432b882f9c0d9c9e5080186` |

Both public `modeler-scala` and `modeler-scala-value` routes emitted identical
bytes at S66.2. Their representative regeneration roots are respectively
`target/test-generated/workflow-invocation-policy-normal` and
`target/test-generated/workflow-invocation-policy-value` under Cozy.
Within those roots, JSON is `target/cozy/statemachine-workflow-abi.json`;
Scala is under
`target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/`.
Legacy roots are `target/test-generated/workflow-invocation-policy-legacy-modeler-scala`
and `target/test-generated/workflow-invocation-policy-legacy-modeler-scala-value`;
legacy JSON uses the same relative JSON path. These target directories explain
regeneration; the following tracked CNCF artifacts are the consumed fixtures.

| Source product relative to regeneration root | Owned tracked CNCF destination | SHA-256 |
| --- | --- | --- |
| Normal `target/cozy/statemachine-workflow-abi.json` | [src/test/resources/workflow/invocation-policy-producer-abi.json](../../../goldenport-cncf/src/test/resources/workflow/invocation-policy-producer-abi.json) | `bbbca0dad63e18ce4f12def1c42edd7d94ede03a3b7b2736b8028b2a6bb422d1` |
| Legacy `target/cozy/statemachine-workflow-abi.json` | [src/test/resources/workflow/invocation-policy-legacy-producer-abi.json](../../../goldenport-cncf/src/test/resources/workflow/invocation-policy-legacy-producer-abi.json) | `c1ec6d672e76f060b1047880e50db38627ea54f80b8833e1f7a590c06ea79626` |
| Normal Scala root `StateMachineWorkflowAbi.scala` | [src/test/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala](../../../goldenport-cncf/src/test/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala) | `a4e33514d6962ed8946df789c92ecf3ad9643bb58d8fb69db94bfd1cb26f0626` |
| Normal Scala root `StateMachineWorkflowComponentFactoryBootstrap.scala` | [src/test/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala](../../../goldenport-cncf/src/test/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala) | `c0acadc0ef83e5dbcd875d38ac5172a5a3b97d8d08a1b8de7918d96c9dbab483` |
| Normal Scala root `InvocationControlledWorkflowStateMachineWorkflow1.scala` | [src/test/scala/domain/statemachine/workflow/InvocationControlledWorkflowStateMachineWorkflow1.scala](../../../goldenport-cncf/src/test/scala/domain/statemachine/workflow/InvocationControlledWorkflowStateMachineWorkflow1.scala) | `54029bac67b33ce3790319247fabd3766e3ba8bf81b205f404c0627a6435cc52` |

All five artifacts were copied without reserialization, newline changes,
formatting, headers, package changes or regeneration. Before/after copy hashes
match the accepted producer inputs. Shared RULE.md excludes generated contract
bytes from handwritten source cleanup. No producer CML parser or CML copy is
added to CNCF. All three generated Scala sources retain
`domain.statemachine.workflow` and are ordinary Test source inputs.

## Implemented consumer surface and specification mapping

[WorkflowInvocationPolicyAbi](../../../goldenport-cncf/src/main/scala/org/goldenport/cncf/workflow/WorkflowInvocationPolicyAbi.scala)
provides `parseC(rawJson: String, sourceResource: String): Consequence[Artifact]`
and `admitC(artifact: Artifact): Consequence[Artifact]`. The canonical
[consumer contract](../../../goldenport-cncf/docs/spec/workflow-invocation-policy-abi-contract.md)
records its complete public value and diagnostic shapes.

The receiver accepts closed root `schemaVersion`/`workflows` JSON with only
`cozy.cml.statemachine-workflow-abi.v1` or `.v2`. v1 forbids policy fields and
admits an empty collection. v2 requires explicit policy vectors on every member
and at least one nonempty vector across the collection. Declaration order and
all prior source/state/Action/Operation/Required SPI fields are preserved;
metadata-free members of mixed v2 collections retain empty vectors. There is
no new workflow-count or collection identity restriction.

Nested records reject missing/extra keys, including nullable fields. Required
strings are nonblank; optional input/result/binding fields and source lines are
explicit null or valid values. Numeric tokens are unquoted unsigned decimal
JSON integers with no fractional, exponent, string, negative or overflow
acceptance: attempts 1..Int.MaxValue; delay 0..Long.MaxValue; timeout
1..Long.MaxValue; source line positive Int or null. Retry requires both fields;
timeout is independently optional; each policy has at least one control.
There are no defaults, unit conversions or backoff transformations.

Typed targets retain declared ACTION/PARTICIPANT identity spelling. Aliases
resolve through lower-case/alphanumeric comparison to one Action or Required
SPI capability. Canonical policy `actionIdentity` must match exactly; Participant
never names a provider. Eligible Actions are OPERATION and JUDGMENT; targeted
ADMISSION fails while untargeted ADMISSION remains represented. State, Action,
capability, policy and resolved policy Action duplicates fail. Operation source
equals its owning Action source; Required SPI canonical Action and full Operation
(including source) equal the declared Action while capability-entry source stays
separate. No runtime Required SPI metadata is supplied.

The representative fixture is `InvocationControlledWorkflow`, version
`workflow-invocation-policy-v2`, root/definition lines 25/27, states 43/45,
Actions and Operations 61/67, capability entries 75/79. Ordered policies are
`capture-control` (ACTION `capture-payment`, 3 attempts, delay 250, timeout 30000,
source 85) and `check-control` (PARTICIPANT `check-payment-capability`, canonical
`check-payment`, retry None, timeout Long.MaxValue, source 92). Legacy
`OrderProgress` / `workflow-v1` has no policy field and retains its independent
pre-v2 producer hash.

| Observable contract | Authored specification group |
| --- | --- |
| Actual v2 bytes, complete facts, source positions, compiled descriptor/bootstrap and independent v1 | retain actual producer facts |
| v1/v2 policy shapes, empty and mixed collection order | interpret versioned collections |
| Numeric endpoints, at least fifty ScalaCheck round trips, invalid tokens, absence/source null | interpret canonical integers and absence |
| Closed objects, malformed/duplicate JSON, required/optional strings and kinds | enforce the closed wire shape |
| Alias spelling, exact binding, duplicates, Operation/source equality, Judgment/Admission | bind declared targets and producer correlations |
| Copied facts, equality, exact whitespace bytes, null safety and deterministic typed diagnostics | readmit immutable facts and transport diagnostics |

These groups are in the actual
[WorkflowInvocationPolicyAbiSpec](../../../goldenport-cncf/src/test/scala/org/goldenport/cncf/workflow/WorkflowInvocationPolicyAbiSpec.scala).
They consume checked-in producer JSON and compare compiled generated values;
bounded variants mutate that actual fixture. They do not inspect Markdown,
source text or phase closure. Consumer validation and review closure are recorded below.

`sha256` hashes exact original UTF-8 JSON bytes. `admitC` reparses original bytes
and caller label and requires equality across every immutable artifact field.
Copied divergent facts fail; malformed changed bytes propagate parse failure.
Null inputs fail through Consequence. Typed stable codes 001..008 cover JSON,
shape, schema, value, duplicate, target, eligibility and definition mismatch;
render uses sorted context without raw JSON or unstable parser text. Resource
labels are preserved caller correlation only, never attested CML provenance.
The checked-in fixture/commit/hash attribution above is the producer evidence.

## S66.3 accepted evidence

S66.3 Step acceptance: normal CNCF receipt `P66-S663A-VAL-005` records
40 passed / 0 failed across three suites, SBT exit 0, wrapper exit 0 and
`lock=released`. The accumulator includes the 21 invocation-policy behaviors,
existing closed-v1/Candidate compatibility, compiled real generated Scala values,
and all five exact producer copies.

Independent protected focused review `P66-S663-REVIEW-001` found no functional
decoder defect and one action-placement authoring blocker. The three prescribed
`When`/`Then` relocations retain every request and expectation; parent-verified
`P66-S663-M0-WAIVER-001` closes that blocker using exact final bytes and
whitespace-diff evidence. The passing tests precede this mechanical arrangement
change; no post-repair test execution or second review is claimed.
S66.3 is committed in CNCF `f7cf5bc04c11b9f74e09d61b8199452c5edb3275`
and Cozy `a8a78a23492a7aed40a6cb11239fcdaa23afa240`.

## Cozy Phase closure

Phase66 is COMPLETE at the Cozy CML and generated ABI producer-delivery
boundary, following the user's 2026-10-02 instruction. Cozy supplies the
Retry / Timeout declarations, strict source validation, deterministic lossless
v2 JSON/Scala/bootstrap metadata, metadata-free v1 compatibility and the
documented consumer handoff. CNCF owns connecting these products to its
ComponentFactory/runtime, execution enforcement and remaining CNCF validation.

All prior Step commits are preserved: Cozy S66.1
`358ae6d1a233557b3e45087558ecff89e420f4af`, S66.2
`e3262754023e0afc42bb5fcd3aeecf1cca0ab78e`, and the historical S66.3
fixture/receiver evidence in Cozy `a8a78a23492a7aed40a6cb11239fcdaa23afa240`
and CNCF `f7cf5bc04c11b9f74e09d61b8199452c5edb3275`. S66.3 proves the
pure metadata receiver and generated fixtures; production runtime integration
remains CNCF-owned work.

Exactly one independent full review, `P66-FULL-REVIEW-E1-001`, covered the
25-file accumulator. Its producer-specification authoring blocker was closed
by `P66-PHASE-E1-C1-M0-WAIVER-001`: six action/IO relocations in four existing
scenarios, preserving requests, expectations and fixtures. Normal final Cozy
`sbt --batch test`, `P66-FINAL-COZY-VAL-001`, passed after this repair:
2,367 succeeded / 0 failed / 9 canceled; 178 suites completed, none aborted;
SBT and wrapper exit 0, lock released. Subsequent closure edits affect
documentation only and preserve that tested program and specification.

The actual CNCF full run, `P66-FINAL-CNCF-VAL-001`, had 4,240 succeeded /
1 failed. The audit-viewer Job-list failure is recorded in the
[CNCF handoff](phase-66-cncf-handoff.md#cncf-owned-connection-and-validation-work)
for CNCF follow-up. CNCF full-suite success and runtime connection are outside
the user-selected Cozy closure boundary.

The [Hygiene journal](../journal/2026/10/2026-10-02-phase-66-hygiene-follow-up.md)
preserves two accepted nonblocking records exactly. There are no accepted
Development Candidates or unpersisted review records. This producer closure
is finalized by the distinct local Cozy Phase release commit.

## CNCF-owned connection and validation work

The user instructed on 2026-10-02 that Cozy finish by supplying its CML
functionality and CNCF take over the connection work. The delivered input is
the Workflow-owned `INVOCATION-POLICY` section, illustrated by the tracked
[real CML fixture](../../src/test/resources/modeler/workflow-invocation-policy.cml):
`capture-control` targets ACTION `capture-payment` with maximum attempts 3,
fixed retry delay 250 ms and execution timeout 30000 ms; `check-control`
targets PARTICIPANT `check-payment-capability` with timeout Long.MaxValue.
Retry includes the initial attempt, delay applies before each later attempt,
and timeout applies to each attempt. Producer grammar, numeric bounds and
target validation are defined by the accepted source and ABI contracts above.

CNCF can regenerate through Cozy's public `modeler-scala` or
`modeler-scala-value` routes and consume the JSON, generated descriptor,
Workflow wrapper and ComponentFactory bootstrap paths recorded above.
Policy-bearing collections emit v2; metadata-free collections retain the
existing byte-identical v1 products. Historical S66.3 supplies `parseC` /
`admitC` and five checked-in generated fixtures as reusable receiver evidence.

CNCF follow-up owns:

1. Connect the admitted v2 artifact and generated descriptor/bootstrap to the
   actual Workflow ComponentFactory/runtime entry, retaining closed-v1 and
   CandidateWorkflowAbi compatibility. Use the delivered metadata without
   re-parsing CML or reconstructing declaration semantics.
2. Complete Phase80 WorkflowExecutionProtocolRuntime prerequisites, then
   implement Phase81 retry/timeout enforcement, durable attempt/history and
   restart/late-completion handling described below.
3. Validate the real producer products through the connected consumer and
   runtime, including legacy/mixed inputs and execution outcomes, and accept
   that integration in CNCF independently of Cozy's producer closure.
4. Resolve and rerun CNCF's remaining repository-wide validation. The actual
   final run `P66-FINAL-CNCF-VAL-001` completed 556 suites (none aborted),
   with 4,240 passed / 1 failed / 13 canceled / 1 ignored / 46 pending;
   SBT and wrapper exit 1, lock released. Its failure was
   `StaticFormAppRendererAuthorizationSpec.scala:848`, audit-viewer access
   to the production admin Job list: HTTP 404 instead of expected 200.
   This is existing CNCF Job/HTTP behavior from Phase69.6; Phase69.7 owns
   its deferred aggregate validation. It remains unresolved CNCF work and
   is separate from the passing Cozy producer validation.

This handoff closes Cozy's supply responsibility. CNCF integration and full
validation need their own completion evidence; the existing pure receiver
and fixture Step do not establish production runtime adoption.

## Pending runtime ownership

[CNCF Phase81](../../../goldenport-cncf/docs/phase/phase-81.md), after Phase80
WorkflowExecutionProtocolRuntime, owns ComponentFactory/runtime adoption and
execution enforcement: maximum attempts including initial, fixed delay before
each later attempt, per-attempt timeout, durable attempt/retry readiness,
outcome/history/diagnostics, restart-safe retry/timeout and minimum protection
against late completion causing duplicate transitions. Consumers receive
admitted Artifact/Workflow/InvocationPolicy values and never reparse CML.
Existing closed v1 GeneratedWorkflowAbi and additive CandidateWorkflowAbi stay
separate; the generated v2 bootstrap wrapper is metadata, not runtime adoption.

Deadline, general Timer/Wait, Cancellation, FailurePolicy, general idempotency,
backoff/jitter, dedicated Iteration, Phase71/75, publication and pushes remain
excluded. Cozy producer-delivery closure is recorded above; CNCF connection,
execution enforcement and remaining CNCF validation follow the separate handoff.
