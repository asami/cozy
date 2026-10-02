# Phase66 S66.3 CNCF Invocation Metadata Handoff

Current status: S66.3 Step accepted; full Phase closure pending
Owner: Cozy source/generated producer; goldenport-cncf metadata receiver and consumer fixture
Update rule: parent records validation, review, commit and acceptance for the complete two-repository accumulator
Updated: 2026-10-02

## Accepted predecessor authority

[Phase66](phase-66.md) and its [checklist](phase-66-checklist.md) remain in-progress.
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
or Phase acceptance. Final full validation, full Phase review and release remain
pending. See the accepted [v2 producer contract](../spec/workflow-retry-timeout-abi-contract.md)
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
This change records accepted S66.3 work; its local commits are attributable
through the Cozy/CNCF Git histories. Full Phase review, final full suites,
release commit and Phase acceptance remain pending.

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
excluded. S66.3 Step acceptance is recorded above. Final full suites, full
Phase review, release and Phase acceptance remain pending.
