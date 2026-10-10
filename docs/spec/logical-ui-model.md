# Logical UI Model v1 Specification

Document role: normative behavior contract for `cozy.logical-ui.v1`.
The ownership and layering rationale is in
[`docs/design/logical-ui-model.md`](../design/logical-ui-model.md).

## 1. Candidate document

The deterministic codec accepts and emits one closed candidate object with this
exact field set and canonical field order:

```json
{
  "schema": "cozy.logical-ui.v1",
  "version": 1,
  "kind": "candidate",
  "componentSurfaces": [],
  "componentBindings": [],
  "useCases": {}
}
```

The codec MUST reject a missing, unknown, duplicate, malformed, mistyped, or
wrong-version field before it exposes a candidate. JSON object order is
transport-only; canonical emission always uses the order above. Array ordering
is normalized as defined below before canonical serialization.

A candidate retains normalized `CandidateInput`, canonical logical content,
and canonical candidate JSON. Canonical logical content contains `schema`,
`version`, `componentSurfaces`, `componentBindings`, and `useCases`.

## 2. Exact Component admission

A Component coordinate is exactly:

```json
{"namespace":"org.example.sales","id":"SalesOrder","version":"1.0.0"}
```

`namespace` is dot-separated canonical segments, `id` is one canonical segment,
and `version` is a nonempty trimmed identity value. `namespace.id` is the only
qualified Component name. A Component surface is exactly:

```json
{
  "component": {"namespace":"org.example.sales","id":"SalesOrder","version":"1.0.0"},
  "exportIds": ["order-summary"]
}
```

and a binding is exactly:

```json
{
  "component": {"namespace":"org.example.sales","id":"SalesOrder","version":"1.0.0"},
  "exportId": "order-summary"
}
```

At least one unique surface and binding are required. A coordinate may occur in
a binding only when it equals a declared surface coordinate including version.
An `exportId` is an opaque, nonempty, trimmed identifier and may occur only
when exactly listed by that matching surface. No source lookup, local name,
unexported element, fallback version, or inferred export is admissible.
Surfaces sort by full coordinate, export IDs lexicographically, and bindings by
full coordinate plus export ID; duplicates fail rather than being deduplicated.

## 3. UseCase layers

`useCases` is exactly:

```json
{
  "business": {"layer":"business","id":"business-confirm-sales-order"},
  "system": {"layer":"system","id":"system-confirm-sales-order"},
  "ui": {"layer":"ui","id":"ui-confirm-sales-order"},
  "realizations": [
    {
      "source": {"layer":"business","id":"business-confirm-sales-order"},
      "target": {"layer":"system","id":"system-confirm-sales-order"}
    },
    {
      "source": {"layer":"system","id":"system-confirm-sales-order"},
      "target": {"layer":"ui","id":"ui-confirm-sales-order"}
    }
  ]
}
```

All three layer identities MUST be nonempty and pairwise distinct. The candidate
MUST contain exactly the two shown realization relations, after which they are
sorted by source and target. Wrong-layer, missing, reversed, duplicate, direct
Business-to-UI, and additional links fail closed. This is a semantic identity
boundary, not an inheritance or a name-resolution rule.

## 4. Candidate and accepted authority

`CozyLogicalUi.candidate` is the only candidate constructor. It validates and
normalizes the supplied `CandidateInput`. `CozyLogicalUi.accept` requires a
nonnull candidate and an explicit nonnull `AcceptanceDecision` whose
`decisionId` is nonempty and exactly trimmed. The accepted value retains the
actual candidate and decision. The decision ID records the explicit acceptance
fact; it does not certify the bytes or currentness of another artifact.

Raw authoring prompts, serialized codec bytes, generated HTML, route tables,
target code, and caches are not fields of `CandidateInput` or acceptance. They
cannot create accepted authority.

## 5. Executable specification

`src/test/scala/cozy/ui/CozyLogicalUiSpec.scala` is the executable
specification for canonical admission, closed-export and malformed-coordinate
rejection, three-layer realization validation, candidate/accepted separation,
normalized canonical serialization plus strict codec roundtrip, and non-authority boundaries.
It includes a ScalaCheck ordering property in addition to Given/When/Then
scenarios.

## 6. UseCase-to-Screen projection

`cozy.usecase-screen-projection.v1` is a typed projection constructed with the
actual `LogicalUiCandidate` supplied to `CozyLogicalUi.project`. It is
deliberately separate from the already committed `cozy.logical-ui.v1`
candidate JSON and `CozyLogicalUiCodec`; it has no JSON transport or acceptance
envelope in this slice.

The projection admits a catalog of opaque `UseCaseLayers` values. The catalog
is nonempty, contains the candidate's exact three-layer value, and every
catalog UI UseCase has at least one declared `UiUseCaseStep`. A step contains
the exact UI-layer `UseCaseReference`, a nonempty step ID, and exactly one of
the closed paths `normal`, `alternative`, `exception`, or `system-only`.
`ScreenInteractionMapping` joins a step to a concrete screen interaction.
Normal, alternative, and exception steps have one or more mappings; a
system-only step has none. A mapping may be shared by several steps and a
screen may be reused by several catalog UI UseCases. Duplicate mappings fail
closed.

`LogicalScreen` has a globally unique ID, a nonempty primary purpose and
unique optional secondary purposes, one `Entity`, `Aggregate`, `View`, or
`Workflow`
subject, a nonempty semantic region tree with one root, existing parents, and
deterministic sibling order, named interactions, feedback states, and logical
navigation endpoints. Interaction kinds are closed to `entry`, `input`,
`query`, `selection`, `invocation`, `observation`, `feedback`, and
`navigation`. Navigation interactions require a valid endpoint to an existing
screen and non-navigation interactions cannot carry an endpoint. Feedback is
closed to `normal`, `loading`, `empty`, `unavailable`, `validation-failed`,
`conflict`, and `operation-failed`; every screen includes exactly one normal
state.

Component roles are closed to `Entity`, `Aggregate`, `Service`, `Operation`,
`Value`, `Datatype`, `View`, `Workflow`, `Powertype`, and `StateMachine`. Every
role-bearing reference is an exact existing public `ComponentBinding` from
`candidate.input.componentBindings`; no parallel Component declaration,
unexported binding, or unused candidate binding is admitted. Every screen and
interaction must be justified by a mapping, and every selected candidate
binding must be used by a subject, interaction usage, or mutation action.

An `AggregateBoundary` explicitly names an Aggregate, root, members, and
public Operation bindings. Each public Operation binding is owned by exactly
one boundary; the same binding MUST NOT authorize mutations in multiple
boundaries. Boundaries have no overlap and contain their root. Every mutation
action names both a target and a public Operation. A mutating invocation must
contain that same binding as an explicit `Operation` role usage. A direct child
member cannot be a mutation target; a root mutation must use one of its
boundary's declared public Operations. Unknown or unexported references,
malformed regions/navigation, invalid paths/kinds/feedback, and missing
Operation bindings fail closed with stable `LUI43_` diagnostics.

`Workflow` is a public Component role and a Logical Screen pattern-source peer
of `Entity`, `Aggregate`, and `View`. Its screen subject uses the same exact
public `ComponentBinding` admission and canonical subject identity rules as
those peers. It adds no parallel Workflow declaration, CML reader, source
lookup, renderer, route, widget, client state machine, Workflow runner, or
Workflow execution API. It adds no Workflow-specific constraint category,
multiplicity, or StateMachine rule. A public `Operation` remains the executable
server-side request boundary; Workflow is never an Aggregate mutation-root
substitute or a client-side replacement for server Workflow, authorization, or
observability.

Projection construction normalizes catalogs, steps, screens, interactions,
regions, mappings, usages, feedback, and Aggregate boundaries. It retains the
actual candidate and normalized projection input and exposes canonical content
of that input. It performs no reachability analysis, pattern selection,
constraint or StateMachine interpretation, HTML generation, serialization, or
target realization.

## 7. Typed semantic projection

`cozy.logical-ui-semantics.v1` is a package-local, typed projection over an
already-normalized `LogicalUiProjection`. `CozyLogicalUiSemantics.project`
consumes the actual screen projection and explicit typed semantic input. Its
result retains normalized input and canonical content. It has no JSON
transport or acceptance envelope and does not discover CML or private
Component source.

The semantic input covers every projected `LogicalScreen` exactly once. Each
screen has one closed Purpose (`browse`, `inspect`, `edit`, or `confirm`), an
exact display binding for every region (`collection`, `detail`, `form`, or
`status`), one exact Interaction Pattern binding for every interaction
(`navigate`, `select`, `input`, `command`, or `observe`), and one closed UI
interaction state (`idle`, `pending`, `succeeded`, or `failed`) for every
interaction. Pattern compatibility is closed: navigation, selection, input,
invocation, and entry/query/observation/feedback interactions admit only their
corresponding `navigate`, `select`, `input`, `command`, and `observe` patterns.
These values classify intent; they are not routes, widgets, CSS, layout, or
target-framework instructions.

Component semantic bindings repeat exact public `ComponentBinding` identities
already admitted by the screen projection and attach a closed role. Value and
Datatype roles require exactly one closed Multiplicity (`exactly-one`,
`zero-or-one`, `one-or-more`, or `zero-or-more`). Powertype roles carry an
opaque nonempty variant ID. StateMachine roles carry explicit
`DomainStateReference` values. Constraint declarations use the exact typed pair
`(constraintId, detailCode)`, one category (`datatype`, `value`,
`aggregate-invariant`, `operation-precondition`, or `operation-postcondition`),
the compatible exact binding, and one closed validation authority
(`local-deterministic`, `context-dependent`, or `server-authoritative`). Every
Operation semantic binding has separate precondition and postcondition
identities. Feedback associations reference the whole declared pair and never
reconstruct a detail code from a copied string.

A `Workflow` semantic binding records only its exact public role and binding
admitted by the Logical Screen projection. It carries no multiplicity,
constraint category, Domain StateMachine state, inferred Workflow transition,
or client-owned Workflow state semantics.

`DomainStateReference`, `WorkflowStateReference`, and `UiInteractionState` are
different typed domains. A StateMachine transition action is admission evidence
only: it must name a non-system declared UI UseCase step, an exact mapped screen
interaction, matching public `Operation` and `StateMachine` usages, and
explicit domain state references. If the mapped interaction has a mutation,
its operation must equal the action's operation. The projection never executes
a transition, calls a server, evaluates a constraint, infers Workflow state,
or changes feedback wording.

The semantic projection sorts every input vector by structural identity keys
and exposes canonical content of the normalized typed input. Duplicate or
omitted screen, region, interaction, component, constraint, feedback, or
transition identities fail closed under `LUI43_SEMANTICS_*` diagnostics.
Equivalent permutations produce identical normalized input and canonical
content. The executable specification is
`src/test/scala/cozy/ui/CozyLogicalUiSemanticsSpec.scala`.

## 8. Read-only review HTML and direct output

`cozy.logical-ui-review.v1` is a typed, package-local review projection over
supplied `AcceptedLogicalUi`, `LogicalUiProjection`, and semantic projection
values. `CozyLogicalUiReview.render` requires nonnull accepted, projection,
semantics, accepted/projected candidate, and acceptance decision values. A
missing value returns `LogicalUiError` with `LUI43_REVIEW_INPUT_INVALID` at the
actual missing-input path. Each construction boundary owns its domain
validation; review performs no cross-stage currentness certification. It does
not construct or change accepted authority, normalize semantics, evaluate a
constraint, execute an Operation or transition, or perform target realization.

The result contains deterministic UTF-8, self-contained accessible HTML and
typed review diagnostics. Its `htmlBytes` accessor encodes the HTML as UTF-8.
The overview shows schema `cozy.logical-ui-review.v1`, the renderer profile
`cozy.logical-ui-review.renderer.v1`, and the escaped explicit acceptance
`decisionId` as a review fact.

The HTML shows Business/System/UI realization and catalog membership, every
UseCase step and screen-interaction mapping (including system-only steps),
navigation edges and reachability, per-screen purpose/subject/regions,
Display mappings, interaction Patterns and UI lifecycle states, feedback,
exact Component bindings and multiplicity/Powertype/StateMachine data,
constraint `(constraintId, detailCode)` pairs and validation authority,
feedback associations, and StateMachine transition admission evidence. Each
screen labels logical primary purpose, logical secondary purposes, and semantic
Purpose separately. Transition evidence keeps domain state and UI state
separate and includes optional Workflow ID/state ID evidence or a deterministic
missing representation. Dynamic text is escaped, all ordering uses already
normalized values or injective structural keys, and CSS is inline. No external
resource, script, form/action, route, widget, or target-framework contract is
included.

Review diagnostics are informational and deterministic. They can identify
coverage/reference issues, unreachable screens, Aggregate mutation boundary
discrepancies, missing StateMachine action evidence, missing Powertype
variants, and missing expected failure feedback. These conditions do not make
the renderer execute behavior or alter semantic acceptance.

`write` requires a nonnull review and admits the selected output path once. It
accepts only an existing non-symlink parent directory and a regular-or-absent
non-symlink `*.html` target whose `target.getParent` is exactly the supplied
parent path as written. It rejects `.`/`..` and intermediate symbolic-link
components, including symlink-plus-`..` traversal, before writing. It writes
UTF-8 HTML directly to the selected target with create, write, and truncate
options. The render-then-write overload uses the same path admission.
`IOException`, `SecurityException`, and `RuntimeException` become ordinary
`LUI43_REVIEW_OUTPUT_INVALID` HTML-write diagnostics. Prewrite path rejection
leaves the target untouched; a late write failure may leave partial generated
output. Recovery is regeneration.

## 9. Provisional runtime instances

`cozy.logical-ui-runtime.v1` is a separate, versioned provisional runtime
contract for List/Detail Display Model instances. It reuses the exact public
`Purpose`, `Display`, and `InteractionPattern` singleton vocabulary extracted
from the package-local semantic projection; it does not change candidate,
acceptance, codec, projection, or review authority. Its target-neutral roles,
typed display values, exact contract/provenance admission, and pure adaptive
selection are specified in
[`logical-ui-runtime-contract.md`](logical-ui-runtime-contract.md). CNCF Phase
96 may consume that local provisional contract before a separate Android mock
integration acceptance is completed.

## S23 implementation and validation facts — 2026-10-09

P715-02-S23-LOGICAL-UI-DIRECT-REVIEW is
**implemented-focused-validated-unreviewed**, retaining original Phase 71.4
base `fc640ec25e443d00c22b2c26b437a205d39910eb`. Its initial editing handoff
on 2026-10-09 was **implemented-unvalidated-unreviewed**. The facts below
record selected execution evidence; the normative behavior above retains its
meaning, and independent review and acceptance remain OPEN.

### Actual S23 focused validation — 2026-10-09

The [actual S23 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s23-focused-validation-result.json), following the
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s23-parent-edit-admission.json), records **50 successes in 6 completed
suites**. Two sequential groups passed: 3 tests in the Phase43 SalesOrder
acceptance suite, then 47 tests in the existing LogicalUi, Semantics, Review,
Runtime, and `cozyruntime` List/Detail acceptance suites. Failed, canceled,
ignored, pending, and aborted counts are zero. The initial registered runner
route completed with SBT/wrapper exits 0/0 and shared serial lock release.
Compiler warnings, errors, fixture diagnostics, and runtime Logback warning
lines are zero for this invocation. Existing `HYG-P715-S19-LOGBACK-001` remains
pending its original Step classification; no suppression or whole-Phase
warning-free claim is made.

Parent assessment confines the changed source to LogicalUI typed values,
review, and direct HTML output, with existing runtime List/Detail consumers
covered. It identifies no JA34 narration/synthesis/frame/render/assembly/audio/
credit/end-card producer edge. No new native generation or hearing is claimed;
S14 native and S22 142/6 remain dated evidence only. CAR-provider request/evidence
digests in original R715-06, all remaining finite duties and E715-01..04,
final current JA34 generation/assembly/hearing/end-card/EOF, independent M2
cycles 6/7/8/10, whole-Step/full-Phase assurance and acceptance, exact local
commits, and distinct release remain OPEN. Repository-full SBT and broader
integration remain **deferred-not-run to PHASE-71.7**. Stage Status triples,
checkboxes, aggregate metadata, and prior dated history retain their status.
