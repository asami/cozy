# Workflow Semantic Outcome Source Contract

Semantic role: Stable Semantics (normative).
Current status: COMPLETE at the Cozy producer boundary. All three Steps and sole high P69-FULL-REVIEW-001 revision1 for P69-PLAN-E1 accepted, zero CPB/HYG/DEV. P69-FINAL-VAL-001 passed: 2,635 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 184 suites, 0 aborted; SBT/wrapper exit0 and `lock=released`. The distinct local release commit containing this record completes ordinary Phase69 closure. CNCF84 owns receiver integration, runtime execution/history and enforcement.
Owner: Cozy Workflow source contract, Phase 69 parent.
Update rule: change together with the executable specification and frozen lowering design; acceptance requires parent validation, independent review and commit evidence.
Updated: 2026-10-03.

## Owned source declaration

A Workflow definition may contain at most one direct `SEMANTIC-OUTCOME`
section alongside CSM, API/SPI, invocation, lifecycle, wait, failure and safety
sections. It requires a valid `COMPOSITE-STATEMACHINE`, even when empty.
An explicitly empty outcome container is invalid; absence is valid and means
an empty declaration vector. Case and separator aliases normalize section and
field keys by the established lowercase letter/digit matching.

The container has no direct fields and contains one or more ordered opaque
named entry headings. Entries have direct scalar fields only and no children.
Their complete field set is exactly one of `action` or `participant`, and
exactly one nonempty `outcome`, `role`, `from`, `to` and `on`. There are no
defaults. Unknown, missing, empty or repeated fields, alias duplicates, a
second target, empty/duplicate entry identities and nested content fail through
`RAISE.syntaxErrorFault`. Raw duplicate fields must survive until admission;
constructing a map before duplicate checking is prohibited.

```cml
### SEMANTIC-OUTCOME
#### revision-needed
participant = review-capability
outcome = NEEDS_REVISION
role = review
from = Reviewing
to = Revising
on = needsRevision
```

## Closed meaning and target resolution

The only outcome values are the exact trimmed tokens `NEEDS_INPUT`,
`NEEDS_REVISION` and `REJECTED`. Values never case-fold. NeedsInput means
missing semantic input; NeedsRevision requests a revised candidate; Rejected
records semantic rejection. Success, approval, failure, retry, cancellation
and timeout variants are not outcomes in this contract.

An Action target resolves by the existing normalized key to exactly one
declared Action. A Participant target resolves through exactly one existing
`REQUIRED-OPERATION` capability. Only OPERATION and JUDGMENT Actions qualify;
ADMISSION is deterministic local semantics and cannot be targeted. Unknown or
ambiguous identities and provider identities fail. The declared target kind
and trimmed spelling remain separate from canonical `actionIdentity`.

Entry identities are unique by normalized key. Exactly one declaration may
exist for each canonical (Action, outcome) pair, including Action/Participant
aliases of that same Action. Identical and conflicting duplicates both fail.
A target may declare any subset of the three values; distinct targets may
declare the same value. No complete runtime result enumeration or invocation
retry declaration is required.

## Existing StateMachine edge reference

`role` resolves to one existing CSM constituent. `from` and `to` resolve to
actual names in that constituent's already validated flat finite state universe.
The normalizer receives the actual StateMachineClass already resolved by CSM
admission on the same model, including Entity-qualified source references.
It examines exactly the resolved source state's `transitions.call ++
transitions.global`, matching a `NameTransitionTo` target and `getEventName`
against declared `to`/`on` with established key matching. Exactly one declared
matching transition must exist.

The IR stores actual role, actual from/to state names and actual matched event
name. It stores the trimmed source StateMachine reference used by constituent
resolution, preserving Entity qualification verbatim. Unknown role, state or
event, wrong target, reversed/nonexistent edge and ambiguous matching edges
fail. Explicit self-loops are valid. No edge is inferred from composite states,
DERIVED-ACTION, CONSTITUENT-ACTION, a same-named event or source string shape.
No attached transition Action is required. Guards, transition Actions and
placement are untouched. The reference grants no exemption from receiver
guards, current-state admission or concurrency checks.

## Structural position and compatibility

Structural SEMANTIC-OUTCOME is prohibited outside its direct Workflow-definition
position across model roots. Position determines meaning: Workflow/CSM
definition names and established State, Action, constituent, derivation,
API/SPI, invocation, policy and wait entry headings remain opaque identities,
including `SemanticOutcome`. A Workflow-root child with direct VERSION or CSM
remains a definition; a bare outcome structural container at the root is invalid.
Ordinary Service, Operation and StateMachine identity names remain legal.

Within Workflow/CSM contexts, generic `outcome` is legal only on an outcome
entry. Unrelated divisions retain their generic outcome fields. Existing
`role`, `from`, `to`, `on` and `action` meanings remain legal in their owners;
these words are not new global reservations. Closed container and entry shape
also prevents nested misplaced metadata. Prior invocation, lifecycle, failure
and execution-safety ownership checks all remain active. Outcome entry headings
may be `Lifecycle`, `Wait`, `FailurePolicy` or `ExecutionSafety` as opaque names.
Both CSM and Workflow extraction routes perform ownership admission first.

## Immutable IR

`WorkflowSemanticOutcome` is a sealed abstract class with public
`canonicalValue: String`; its companion contains NeedsInput, NeedsRevision and
Rejected. `WorkflowSemanticOutcomeTransition(role, stateMachine, from, to, on)`
and `WorkflowSemanticOutcomeDeclaration(identity, target, actionIdentity,
outcome, transition, source)` are immutable equality-bearing values.
`source` retains the actual entry heading line.

`WorkflowDefinition` appends
`semanticOutcomes: Vector[WorkflowSemanticOutcomeDeclaration] = Vector.empty`
after executionSafetyPolicy. All prior constructor fields, labels, order and
defaults remain unchanged, including every positional arity through Phase 68
and named calls. This promises source compatibility, not binary case-class or
unapply compatibility. Source order, deterministic equality and repeated
normalization are preserved. Transient resolved evidence adds no public CSM
field and changes no existing serialized CSM value.

## Producer and runtime ownership

This is declarative producer metadata. A future receiver may associate the
named Action's semantic result with the explicitly referenced ordinary graph
edge. Cozy emits no event and performs no execution, suspension, retry,
runtime admission rejection or Workflow state mutation. Rejected is separate
from PERMANENT/RETRYABLE and terminal only when the authored graph makes it so.
It changes no invocation budget, wait, lifecycle, technical failure,
ActionExecution Completed/Suspended/Failed, CandidateAdmission, ActionMetadata,
idempotency, UnitOfWork or provider/binding contract. CNCF Phase 84 owns
receiver integration, result mapping, event admission, runtime history and
enforcement. AI review and Human input are consumer binding examples in prose,
not provider execution grammar.

Ordinary graph edges express review → revision → review, input → review,
approval/rejection and authored escalation states. Phase 69 and the actual
[receiver Phase 84 plan](../../../goldenport-cncf/docs/phase/phase-84.md)
supplies no concrete runtime reason these edges are
insufficient. The sm-workflow checkout is unavailable in this workspace; this
is a verification limitation, not evidence of a stale reference. No Iteration
type, iteration counter/budget, termination/escalation field, policy, scheduler
or synthetic retry is introduced. Such entry fields fail closed admission;
future concrete evidence belongs to separately owned scope.

## Working specification and acceptance

The [complete review-loop fixture](../../src/test/resources/modeler/workflow-semantic-outcome.cml)
declares real typed Service operations, all six ordinary graph edges and
complete derivations with INITIAL review.Reviewing. Outcome order is
NeedsRevision, NeedsInput, Rejected. Revision/input return edges need no outcome.
Independent retry (3/250/30000), lifecycle (60000/COOPERATIVE), input signal wait,
failure policy (PERMANENT default, transient.review RETRYABLE) and NOT_REQUIRED
Workflow execution safety demonstrate coexistence.

The [interface executable specification](../../src/test/scala/cozy/modeler/WorkflowSemanticOutcomeCmlSpec.scala)
uses real ModelBuilder/Modeler source admission for normalization, compatibility,
closed fields/tokens, targets, actual graph edges, Entity-qualified resolution,
ownership and opaque names. Bounded generated aliases check normalized results.
The [frozen lowering design](../design/workflow-semantic-outcome-source-lowering.md)
and [Phase 69](../phase/phase-69.md)/[checklist](../phase/phase-69-checklist.md)
record accepted producer evidence. S69.1 SOURCE is accepted at
`8f066d2c475b4b2e58818ccde2dd8bded180e658`: P69-S691-VAL-001 passed
389/389 across seven suites including native Scala2.12.18 Test/compile;
P69-S691-REVIEW-001 revision1 protected high review PASS. S69.2 ABI is accepted
at `4f673a881f00d605fff6902e03499c7568a2a1e1`: P69-S692-VAL-001 passed
101/101 across eight suites; P69-S692-VAL-002 compiled actual Scala3.3.8
normal/value/mixed sources4/4/10 with distinct class/tasty/analysis products;
P69-S692-REVIEW-001 revision1 protected high review PASS. Both focused
accumulators reported zero failed/aborted/canceled/ignored/pending and native
exit0/lock=released. The [CNCF84 handoff](../phase/phase-69-cncf-handoff.md)
is accepted after static validation, independent medium review and manual
Step commit `f9d1e5a766279b808ee95ce3409a5eda5dfd642f`. The sole full Phase
review passed. Ordinary final full test passed; the containing distinct local release completes
Cozy producer closure. Receiver runtime execution remains CNCF84-owned.
