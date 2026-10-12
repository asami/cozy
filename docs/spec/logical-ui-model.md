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
  "inputIdentity": "sha256:<64-lowercase-hex>",
  "componentSurfaces": [],
  "componentBindings": [],
  "useCases": {},
  "identity": "sha256:<64-lowercase-hex>"
}
```

The codec MUST reject a missing, unknown, duplicate, malformed, mistyped, or
wrong-version field before it exposes a candidate. It MUST reject an
`inputIdentity` or `identity` that differs from the normalized canonical value.
JSON object order is transport-only; canonical emission always uses the order
above. Array ordering is normalized as defined below before identity creation.

The candidate `identity` is the SHA-256 digest of canonical logical content:
`schema`, `version`, `componentSurfaces`, `componentBindings`, and `useCases`.
The self `identity`, codec `kind`, and transport formatting are not part of that
digest. `inputIdentity` is a distinct SHA-256 identity over that normalized
logical content under the `consumed-input` role.

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
normalizes the supplied `CandidateInput` before calculating identity.
`CozyLogicalUi.accept` requires both a candidate and an `AcceptanceDecision`.
The decision has nonempty `decisionId` and a `candidateIdentity` exactly equal
to that candidate. Its `decisionIdentity` is SHA-256 of the canonical
acceptance-decision content. An accepted identity is SHA-256 of the candidate,
consumed-input, and decision identities under the `accepted` role, and MUST
differ from the candidate identity.

Raw authoring prompts, serialized codec bytes, generated HTML, route tables,
target code, and caches are not fields of `CandidateInput` or acceptance. They
cannot create accepted authority or affect semantic identities.

## 5. Executable specification

`src/test/scala/cozy/ui/CozyLogicalUiSpec.scala` is the executable
specification for canonical admission, closed-export and malformed-coordinate
rejection, three-layer realization validation, candidate/accepted separation,
canonical identity plus strict codec roundtrip, and non-authority boundaries.
It includes a ScalaCheck ordering property in addition to Given/When/Then
scenarios.

## 6. UseCase-to-Screen projection

`cozy.usecase-screen-projection.v1` is a typed projection pinned to the exact
`LogicalUiCandidate.identity` supplied to `CozyLogicalUi.project`. It is
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
regions, mappings, usages, feedback, and Aggregate boundaries before computing
a canonical identity from the exact candidate identity and normalized
projection content. It performs no reachability analysis, pattern selection,
constraint or StateMachine interpretation, HTML/receipt generation,
serialization, or target realization.

## 7. Typed semantic projection

`cozy.logical-ui-semantics.v1` is a package-local, typed projection over one
already-normalized `LogicalUiProjection`. `CozyLogicalUiSemantics.project`
requires an explicit `projectionIdentity` equal to the supplied projection's
exact identity. It is separate from the candidate and
`cozy.usecase-screen-projection.v1` identities, has no JSON transport or
acceptance envelope, and does not discover CML or private Component source.

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
and hashes canonical content containing the exact projection identity. Duplicate
or omitted screen, region, interaction, component, constraint, feedback, or
transition identities fail closed under `LUI43_SEMANTICS_*` diagnostics.
Equivalent permutations therefore produce identical canonical content and
semantic identity. The executable specification is
`src/test/scala/cozy/ui/CozyLogicalUiSemanticsSpec.scala`.

## 8. Read-only review HTML and currentness

`cozy.logical-ui-review.v1` is a typed, package-local review projection over
exactly one `AcceptedLogicalUi`, its `LogicalUiProjection`, and the semantic
projection bound to that projection. `CozyLogicalUiReview.render` rejects
missing values and every reused-identity mismatch before rendering. It does
not construct or change candidate/accepted authority, normalize semantics,
evaluate a constraint, execute an Operation or transition, or perform target
realization.

The result contains deterministic UTF-8, self-contained accessible HTML, typed
review diagnostics, and a `ReviewReceipt`. The receipt has schema
`cozy.logical-ui-review.v1`, version `1`, and the closed renderer profile
`cozy.logical-ui-review.renderer.v1`. Its canonical identity binds, in a fixed
order, the accepted identity, candidate identity, consumed-input identity,
the candidate's exact three-layer UseCase identity, normalized catalog
identity, projection identity, semantic projection identity, renderer profile,
and SHA-256 identity of the emitted HTML bytes. Receipt identity is distinct
from every reused authority identity and is not emitted into the HTML.
The UseCase and catalog identities are SHA-256 values over local canonical JSON
objects and arrays with fully JSON-escaped dynamic layer and ID strings; no
delimiter-derived encoding is used, so valid delimiter-, quote-, and
control-character-bearing IDs remain injectively distinct.

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
the renderer execute behavior or alter semantic acceptance. Identity and
currentness errors fail closed with `LogicalUiError` diagnostics, including
`LUI43_REVIEW_IDENTITY_MISMATCH` and
`LUI43_REVIEW_CURRENTNESS_INVALID`.

`verifyCurrent` recomputes the tuple and requires exact receipt fields,
identity, and HTML bytes. `write` verifies currentness first and accepts only
an existing non-symlink parent directory and a regular-or-absent non-symlink
`*.html` target whose `target.getParent` is exactly the supplied parent path as
written. It rejects `.`/`..` and every intermediate component, including
symlink-plus-`..` traversal, before temporary output creation or replacement.
It writes only the HTML through a same-parent temporary file and an atomic move.
Rejected validation or staging leaves an existing target unchanged; receipts
are not persisted to a second file.

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

## 10. Candidate preview and currentness

`CozyLogicalUiReview.renderCandidate(candidate, projection, semantics)` admits
one genuine `LogicalUiCandidate`, its `LogicalUiProjection`, and its bound
`LogicalUiSemanticProjection` without constructing an `AcceptanceDecision` or
accepted authority. Candidate preview is not acceptance. The package-local API
is:

```scala
def renderCandidate(
  candidate: LogicalUiCandidate,
  projection: LogicalUiProjection,
  semantics: LogicalUiSemanticProjection
): Either[LogicalUiError, LogicalUiCandidateReview]

def verifyCandidateCurrent(
  candidate: LogicalUiCandidate,
  projection: LogicalUiProjection,
  semantics: LogicalUiSemanticProjection,
  reviewValue: LogicalUiCandidateReview
): Either[LogicalUiError, Unit]

def writeCandidate(
  reviewValue: LogicalUiCandidateReview,
  parent: Path,
  target: Path
): Either[LogicalUiError, Unit]
```

`LogicalUiCandidateReview` exposes immutable `html`, `receipt`, `diagnostics`,
and UTF-8 `htmlBytes`. Its `copy(html, receipt, diagnostics)` retains the
private declared candidate/projection/semantics tuple. `CandidateReviewReceipt`
has exactly these fields, in order:

```scala
final case class CandidateReviewReceipt(
  schema: String, version: Int, rendererProfile: String,
  candidateIdentity: String, consumedInputIdentity: String,
  projectionIdentity: String, semanticProjectionIdentity: String
)
```

Schema is `cozy.logical-ui-candidate-review.v1`, version is `1`, and renderer
profile is `cozy.logical-ui-candidate-review.renderer.v1`. The four identity
fields equal the supplied candidate identity, candidate consumed-input
identity, screen projection identity, and semantic projection identity. There
is no accepted identity, decision, HTML hash, receipt hash, or additional
identity. Candidate identity alone does not bind screen or semantic extensions.

Admission requires three non-null inputs, a projected candidate, and nonempty
canonical identities. Candidate identity, consumed-input identity, and
canonical content must equal those of `projection.candidate` exactly, and
`semantics.projectionIdentity` must equal `projection.identity`. Factories own
normalization and semantic validation. Failure is a structured
`LUI50_CANDIDATE_REVIEW_IDENTITY_MISMATCH` with the precise path and reason;
the renderer performs no normalization or source lookup.

The overview displays `Candidate preview`, status `candidate (not accepted)`,
schema/profile and all four bound identities. Accepted identity and decision
evidence are absent. The other sections and informational diagnostics use the
same structural builders as accepted review in section 8. Dynamic text is
escaped; output is self-contained read-only HTML with inline CSS, without
scripts, forms, remote resources, route execution, or an acceptance action.
The accepted APIs, receipt fields, HTML and identities retain their section 8
contract.

`verifyCandidateCurrent` renders the supplied tuple and compares complete HTML,
receipt and diagnostics. Null review, invalid tuple, or any stale observable
returns `LUI50_CANDIDATE_REVIEW_CURRENTNESS_INVALID` with its pertinent path
and reason. This is product currentness, not workflow integrity control.
`writeCandidate` first verifies the result against its stored declared tuple,
then shares section 8's direct-parent, traversal/symlink, HTML suffix and
same-parent atomic UTF-8 write boundary. Shared path/write failures retain
`LUI43_REVIEW_OUTPUT_INVALID`; rejected stale or invalid output preserves prior
bytes. Only HTML is installed, without a receipt side file. The failure model
covers tuple/currentness and the existing explicit output failures; it adds no
locks, retries, backups, snapshots, checksums, or interference detection.

The executable specification is
`src/test/scala/cozy/ui/CozyLogicalUiCandidateReviewSpec.scala`. It consumes the
shared SalesOrder fixture, exercises factory-admitted foreign and changed
tuples, result-field currentness, at least twenty reordered constructions,
escaped purpose text, and direct writer admission/preservation.

## 11. Shared SalesOrder compact success contract

`CozyLogicalUiSalesOrderFixtures` is a test-owned, target-neutral immutable
fixture shared by the Phase43 accepted specification and candidate preview.
Its pure `candidate(reversed)` composes existing candidate, screen projection,
and semantic factories with `Either` and never accepts a candidate. The
Phase43 acceptance specification explicitly calls
`accept(candidate, AcceptanceDecision("accepted-sales-order", candidate.identity))`.
`projectionInput(candidate, reversed)` and `semanticsInput(projection, reversed)`
expose the same logical values and mappings. Reordered constructions permute
candidate exports/bindings/realizations, screen vectors, and semantic vectors
without changing canonical meaning.

The existing Phase43 IR and Phase74 public Purpose, Display and Interaction
Pattern vocabulary already represent exact public bindings, the normal UI
step, list/detail screens, navigation, selection, command, transition admission,
and distinct UI success. They suffice for this compact contract. No core IR,
codec, runtime, or vocabulary expansion is required. Medium and exhaustive
missing-IR work remain owned by Phase50.1.

`compactSuccess` has fixture ID `sales-order-compact-v1` and coverage ID
`P50-COMPACT-NORMAL-001`. Its `DemoOrder` initial/result records both contain
order ID `SO-001`, label `Sales order SO-001`, and amount `100.00`. They differ
only in `DomainStateReference(stateMachineBinding, "draft")` versus
`DomainStateReference(stateMachineBinding, "confirmed")`.
`successUiState` is the distinct `SucceededInteractionState`. This is a
repeatable simulated result and executes no mutation, Operation, Workflow,
domain transition, or server success authority. Its action is the existing
normal `confirm` step at `order-detail/invoke` in the shared semantic tuple.

The exact public coordinate for all fixture bindings is
`org.example.sales.SalesOrder@1.0.0`. The binding set used by the canonical row
is `Entity:#entity`, `Aggregate:#aggregate`, `Service:#service`,
`Operation:#operation`, `Value:#value`, `Datatype:#datatype`, `View:#view`,
`Workflow:#workflow`, `Powertype:#powertype`, and `StateMachine:#state-machine`,
where each `#export` is bound to that exact coordinate. Invocation uses the
public Operation and StateMachine bindings and the Aggregate root mutation
admission already present in Phase43.

| Coverage ID | Fixture | UI step | Screen interactions in order | Exact public bindings | Profile | Outcome | Phase50 requirements | Evidence status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P50-COMPACT-NORMAL-001 | sales-order-compact-v1 | normal `confirm` | `order-list/entry`, `order-list/to-detail`, `order-detail/select`, `order-detail/invoke`, `order-detail/observe` | SalesOrder exact public binding set declared above; command `#operation` / `#state-machine`, selection `#powertype` variant `awaiting-confirmation` | compact, NavigateToDetail | simulated success: domain draft to confirmed, UI succeeded | P50-MAIN-02/03/04/05/06/07 | selected eight suites: 74/74 passed; actual compact browser PASS, Chrome 154.0.8037.94 at 390 × 844, parent inspected six screenshots; retained [app](../evidence/phase-50/compact/index.html), [structural review](../evidence/phase-50/compact/review.html), [complete receipt](../evidence/phase-50/compact/compact-preview-receipt.json), [portable observations](../evidence/phase-50/compact/browser-observations.json), [six screenshots and evidence scope](../evidence/phase-50/compact/README.md); partial compact normal simulation only, broader target/profile/outcome and aggregate full-suite assurance pending Phase50.1; independent Step reviews `P50-S5011-STEP-REVIEW-001@1` / `P50-S5021-STEP-REVIEW-001@1` and sole Phase review `P50-PHASE-FULL-REVIEW-001@1` PASS; narrow increment closure is bound to [the distinct local release and native audit](../phase/phase-50-closure-ledger.md) |
| P501-COMPACT-NORMAL-001 | sales-order-target-ready-v1 | normal confirm | list entry/navigation; detail selection/invoke/feedback/observe | same exact SalesOrder public binding set | compact | normal: confirmed/succeeded | complete readiness/profile/normal | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/compact-normal.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); [current accepted case image](../evidence/phase-50.1/readiness-accepted-001/screenshots/compact-normal.png); [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-COMPACT-LOADING-001 | sales-order-target-ready-v1 | normal confirm | detail invoke/feedback | same exact SalesOrder public binding set | compact | loading: draft/pending | loading/accessibility | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/compact-loading.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); [current accepted case image](../evidence/phase-50.1/readiness-accepted-001/screenshots/compact-loading.png); [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-COMPACT-EMPTY-001 | sales-order-target-ready-v1 | normal confirm; alternative recover | list entry; recover list entry | same exact SalesOrder public binding set | compact | empty: draft/idle; retry entry | empty/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/compact-empty.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-COMPACT-VALIDATION-001 | sales-order-target-ready-v1 | alternative enter/recover | detail input/feedback; recover input | same exact SalesOrder public binding set | compact | validation-failed: draft/failed; edit input | input/local validation/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/compact-validation-failed.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-COMPACT-CONFLICT-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover input | same exact SalesOrder public binding set | compact | conflict: draft/failed; edit input | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/compact-conflict.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-COMPACT-UNAVAILABLE-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | compact | unavailable: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/compact-unavailable.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-COMPACT-OPERATION-FAILED-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | compact | operation-failed: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/compact-operation-failed.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-MEDIUM-NORMAL-001 | sales-order-target-ready-v1 | normal confirm | list entry/navigation; detail selection/invoke/feedback/observe | same exact SalesOrder public binding set | medium | normal: confirmed/succeeded | complete readiness/profile/normal | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/medium-normal.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); [current accepted case image](../evidence/phase-50.1/readiness-accepted-001/screenshots/medium-normal.png); [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-MEDIUM-LOADING-001 | sales-order-target-ready-v1 | normal confirm | detail invoke/feedback | same exact SalesOrder public binding set | medium | loading: draft/pending | loading/accessibility | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/medium-loading.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); [current accepted case image](../evidence/phase-50.1/readiness-accepted-001/screenshots/medium-loading.png); [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-MEDIUM-EMPTY-001 | sales-order-target-ready-v1 | normal confirm; alternative recover | list entry; recover list entry | same exact SalesOrder public binding set | medium | empty: draft/idle; retry entry | empty/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/medium-empty.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-MEDIUM-VALIDATION-001 | sales-order-target-ready-v1 | alternative enter/recover | detail input/feedback; recover input | same exact SalesOrder public binding set | medium | validation-failed: draft/failed; edit input | input/local validation/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/medium-validation-failed.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-MEDIUM-CONFLICT-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover input | same exact SalesOrder public binding set | medium | conflict: draft/failed; edit input | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/medium-conflict.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-MEDIUM-UNAVAILABLE-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | medium | unavailable: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/medium-unavailable.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-MEDIUM-OPERATION-FAILED-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | medium | operation-failed: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/medium-operation-failed.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-EXPANDED-NORMAL-001 | sales-order-target-ready-v1 | normal confirm | list entry/navigation; detail selection/invoke/feedback/observe | same exact SalesOrder public binding set | expanded | normal: confirmed/succeeded | complete readiness/profile/normal | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-normal.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); [current accepted case image](../evidence/phase-50.1/readiness-accepted-001/screenshots/expanded-normal.png); [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-EXPANDED-LOADING-001 | sales-order-target-ready-v1 | normal confirm | detail invoke/feedback | same exact SalesOrder public binding set | expanded | loading: draft/pending | loading/accessibility | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-loading.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); [current accepted case image](../evidence/phase-50.1/readiness-accepted-001/screenshots/expanded-loading.png); [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-EXPANDED-EMPTY-001 | sales-order-target-ready-v1 | normal confirm; alternative recover | list entry; recover list entry | same exact SalesOrder public binding set | expanded | empty: draft/idle; retry entry | empty/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-empty.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-EXPANDED-VALIDATION-001 | sales-order-target-ready-v1 | alternative enter/recover | detail input/feedback; recover input | same exact SalesOrder public binding set | expanded | validation-failed: draft/failed; edit input | input/local validation/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-validation-failed.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-EXPANDED-CONFLICT-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover input | same exact SalesOrder public binding set | expanded | conflict: draft/failed; edit input | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-conflict.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-EXPANDED-UNAVAILABLE-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | expanded | unavailable: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-unavailable.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |
| P501-EXPANDED-OPERATION-FAILED-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | expanded | operation-failed: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected [case image](../evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-operation-failed.png); [actual observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json); candidate, acceptance pending |

All complete-fixture rows share `sales-order-target-ready-v1`, the exact
SalesOrder public binding set above, and one complete input across every
profile and outcome. All 21 P501 rows now retain actual candidate browser
simulation PASS and individually linked case evidence from the admitted
run007; [the portable review](../evidence/phase-50.1/readiness-candidate-001/browser-review.md)
records binding, executed scope and limits. IR admission and browser success
do not establish human acceptance. Failure rows include mapped
`recover` interactions, and validation uses `enter/input` plus exact local
constraint feedback. Expanded placement updates the detail region; compact and
medium placement navigates. Medium and expanded declare persistent navigation.

This is the single canonical coverage matrix retaining historical S50.2
compact evidence and the 21 S50.1.1 complete-candidate browser cases. It does
not derive human target-ready acceptance from browser success. The separate
actual final acceptance and pending six-case accepted projection proof are
recorded in [the accepted package](../evidence/phase-50.1/readiness-accepted-001/README.md).
Only the six normal/loading rows add this bounded pending evidence reference;
the other 15 rows retain candidate E3 run007 evidence. The original
full-outcome checklist rows remain open in Phase50.1; no second coverage
matrix or target/framework/browser fields are added to Logical UI semantic IR.

## 12. Compact candidate Web target

`CozyLogicalUiCompactWeb.render(candidate, projection, semantics, scenario)`
first consumes `CozyLogicalUiReview.renderCandidate` for the exact source tuple,
its precise errors and diagnostics. It then binds a complete `CompactScenario`
to the existing normal UI step, distinct list/detail screens and five typed,
step-mapped source interactions. List requires typed Browse/Collection and
detail Confirm/Detail. Navigation targets detail. Selection derives its sole
Powertype usage and explicit semantic variant; invocation derives the matching
transition action's exact public Operation and StateMachine usages and mutation.
Initial/result domain references equal that action's `from`/`to`; success UI
state equals both the action's UI state and `SucceededInteractionState`.
Workflow, domain and UI state remain separate source values.

`compactWebScenario` translates the existing `compactSuccess` records and
normal confirm flow without changing any value or accepting the candidate.
Fixture/coverage/flow identifiers are non-null, nonempty and trimmed; records,
domain references and success UI state are required. Label and amount are
nonempty opaque presentation text. Initial and result retain the same order
ID, label and amount. Invalid declarations report
`LUI50_WEB_CONTRACT_INVALID` with the specific scenario path and reason.

The private-constructor `CompactWebPreview` retains the complete declared
tuple and exposes immutable `html`, `reviewHtml`, `receipt`, `receiptJson`,
diagnostics and UTF-8 HTML byte vectors. Its observable-field `copy` retains
the original inputs. The receipt schema is `cozy.logical-ui-compact-web.v1`,
version `1`, renderer `cozy.logical-ui-compact-web.renderer.v1`, profile
`compact`, placement `navigate-to-detail`, and `simulated=true`. It reuses
the candidate review's four source identities and carries the complete
scenario, normalized public binding set, exact derived command/selection
bindings and variant. Outputs are ordered `index.html`, `review.html`,
`compact-preview-receipt.json`. Fixed-order JSON includes coordinates, exports,
UI step/path, flow IDs, both records/domain references and separate UI state;
it contains no machine path, port, timestamp or acceptance assertion.

The app is one self-contained accessible single-column page. At 390 × 844,
the list initially displays the supplied order and draft domain state, with
detail hidden and Open detail disabled. Select order records target UI context
and enables navigation. Open detail hides the list, shows the distinct detail
screen, and sets the encoded logical screen hash and active-screen attribute.
The confirmation select has a placeholder and the exact derived Powertype
variant; Confirm order is enabled only for that variant. Confirmation displays
the supplied successful record and distinct succeeded UI state, reveals
`Simulated success`, and disables the command. It executes no Operation,
StateMachine, Workflow or domain transition. Candidate and simulated assurance
labels accompany the flow; source identities stay in machine attributes and
the linked read-only review. Reset/replay/error/input behavior remains pending.

The frozen DOM boundary is `main#compact-preview` with four source identities,
fixture/coverage/profile/placement, active screen and selected order attributes;
`#order-list-screen`, `#order-detail-screen`, `#select-order`, `#selected-order`,
`#open-detail`, `#confirmation-option`, `#confirm-order`, `#domain-state`,
`#ui-state`, and `#simulated-result`. Source regions/controls carry interaction
and UI step IDs. Selection carries `data-powertype-binding`; command carries
`data-operation-binding` and `data-state-machine-binding`, each encoded as
`canonicalIdentity#exportId`. Domain and UI outputs retain separate state
attributes. The list's selection context introduces no new source interaction.

Dynamic markup and attributes are HTML escaped. The inert `application/json`
element contains the full receipt, escaping literal `<`, `>`, `&`, U+2028 and
U+2029 as Unicode escapes. One fixed script uses `textContent` and declared
values; arbitrary records are never interpolated into executable JavaScript.
There are no external assets, provider/domain calls, storage, forms, backend,
acceptance actions, retries or timers in the app. Structural `reviewHtml`
retains all candidate review sections/diagnostics and inserts a read-only
`Compact simulated scenario` section immediately before the closing main.
This section binds the same declaration and exact public command/selection
bindings; it adds no script, form or action to the structural renderer.

`verifyCurrent` re-renders all supplied inputs and compares all five observable
values. A changed record or flow under the same fixture ID is stale. Null
preview, invalid current inputs and changed observables report
`LUI50_WEB_CURRENTNESS_INVALID` at the precise input or result path.
`write(preview, siteParent, receiptParent)` checks the retained tuple first,
then preflights both existing canonical absolute distinct parents and all three
direct destinations. Traversal, intermediate/target symlinks, missing/relative
parents and non-regular targets report `LUI50_WEB_OUTPUT_INVALID` before any
write. Prior bytes survive contract/currentness/path rejection. Same-parent
temporary UTF-8 files and atomic moves install each output and owned temporary
files are cleaned up. An I/O failure after one installation does not provide
group rollback; unrelated files are preserved. The S50.1 candidate writer
continues to write only its existing direct HTML output.

`CozyLogicalUiCompactWebSpec` covers genuine/full alignment, invalid tuples and
declarations, changed-source/currentness behavior, at least twenty ScalaCheck
reordered constructions, hostile opaque text round trips, and production
writer success/preflight rejection. Its meaningful generation scenario uses
the production writer and retains
`target/phase50-compact-preview/site/{index.html,review.html}` and
`target/phase50-compact-preview/evidence/compact-preview-receipt.json` for
actual browser validation. The parent runs the selected suites through the
serialized SBT runner.

The actual browser entry is the executable Node ESM driver
`scripts/test/phase50/run-compact-browser.mjs` with exactly
`--site <absolute site directory> --receipt <absolute receipt file>
--evidence <absolute owned run directory> --playwright <absolute installed package>
--browser <absolute installed Chrome executable>`. Its parent-owned session
uses the selected installed package, isolated temporary Chrome profile and
390 × 844 viewport, and serves only GET of the two generated pages at an owned
127.0.0.1 ephemeral port. Readiness checks actual generated bytes/source data.
External requests, HTTP mutations, page errors and console errors fail the
run. Bounded actions walk list, selection, distinct detail, confirmation ready,
simulated success and aligned structural review against the full receipt,
retaining six screenshots and literal observations. Portable
`browser-observations.json` has schema `cozy.logical-ui-compact-browser.v1`,
full source receipt, browser/viewport, ordered observed states, control/binding
facts, relative screenshots, network/errors and actual PASS/FAIL; native PID,
base URL and lifecycle facts stay in target-only `server-process.json` and
`browser-result.json`. All exit, signal and 180-second timeout paths await
owned context/browser/server cleanup. The single section 11 row records the
actual compact normal-flow browser PASS and durable evidence links; broad
target/profile/outcome coverage and the repository full suite remain pending
Phase50.1. That row is
the normative coverage record.

## 13. Complete target-ready Logical UI contract

`CozyLogicalUiReadiness` composes the existing factory-produced kernel
candidate, exact screen projection, exact semantic projection and one typed
`ReadinessInput` into `cozy.logical-ui-target-ready.v1`, version `1`.
It is package-local, like the existing kernel. Legacy schema, codec fields,
factory signatures, identities and accepted behavior remain unchanged.
There is no new transport codec or runtime model field.

The complete input consists of `semanticProjectionIdentity`, `profiles`,
`controls`, `fields`, `textSources` and `outcomes`. The same input is shared
by compact, medium and expanded views; it is not a per-profile authority.
`candidate(kernelCandidate, projection, semantics, input)` returns
`Either[LogicalUiError, TargetReadyCandidate]`. The private-constructor value
exposes `identity`, `inputIdentity`, `kernelCandidate`, `projection`,
`semantics`, normalized `input` and `canonicalContent`. Root and exact chain
identity disagreement is rejected before metadata traversal. Pure existing
factories retain kernel, projection and semantic admission responsibility.

`ProfileIntent(windowClass, detailPlacement, persistentNavigation, regions)`
uses the existing `AdaptiveIntent` and `DetailPlacement`. Exactly one compact,
medium and expanded declaration is required. Compact/medium navigate to
detail; expanded updates the detail region. Each `RegionIntent` references
one exact projected `RegionRef(screenId, regionId)`, has a closed
`PrimaryRegion` or `SecondaryRegion` role and declares semantic persistence.
Every projected region occurs exactly once per profile; every screen has a
primary region. Persistence means availability, not fixed positioning.
The fixture's compact/medium regions are nonpersistent, with list-root and
detail-root primary and detail-body secondary; expanded makes all three
persistent. Compact navigation is nonpersistent; medium/expanded navigation
is persistent. Form-factor-specific hints are not applicable: global window
intent supplies all distinct meaning admitted by this contract.

`ControlSemantics(control, role, labelTextId, focusOrder)` covers every exact
`InteractionRef(screenId, interactionId)` once. Entry, Navigation, Input,
Selection, Invocation, Observation/Query and Feedback interactions correspond
to Entry, Navigation, Input, Selection, Command, Observation and Feedback
control roles. Navigation/Input/Selection/Command require a positive globally
unique focus rank; other controls have `None`. The fixture ranks are list
navigation 1, detail input 2, selection 3 and invocation 4.
No DOM, tabindex, ARIA, widget or geometry belongs to this authority.

`TextSource(textId, sourceIdentity, defaultText)` provides unique trimmed text
IDs, trimmed opaque source identities and nonblank display text. Supplied
display whitespace is preserved byte-for-byte. Every metadata label/message
joins a declared text source exactly. Source identity participates in complete
identity. Errors remain structured and language-neutral; no localization
framework, catalog or service is introduced.

`InputField(fieldId, control, valueBinding, datatypeBinding, labelTextId,
inputIntent, required, initialValue, localRules)` covers each Input interaction
once. Value and Datatype bindings must match both that interaction's exact
usages and existing semantic role bindings. `TextInputIntent` and
`DecimalInputIntent` express keyboard/input meaning only. Initial text is
nonnull and opaque, including empty or invalid review values. Each
`LocalRule(constraint, ruleKind, feedbackId, messageTextId)` binds the exact
`ConstraintIdentity(constraintId, detailCode)` of a local deterministic Value
or Datatype declaration for that field, a declared screen feedback state and
the exact semantic feedback association. Rules are unique by constraint pair
and kind. Required fields declare `RequiredTextRule`; its later target
interpretation is nonblank text. `AsciiIdentifierRule` means at least one
uppercase ASCII letter, digit or hyphen, with every character in that set.
The factory evaluates neither predicate nor any domain constraint. Context,
server, Aggregate and Operation declarations cannot become local field rules.
The fixture adds the exact datatype/validation-failed association and both
predicates for `order-reference`, label `Order reference`, initial `SO-001`.
Its messages are `Enter an order reference` and
`Use uppercase letters, digits and hyphen`.

`OutcomeBinding(outcomeId, step, trigger, feedbackControl, feedbackState,
uiState, domainState, workflowState, recoveries, messageTextId)` describes
visible simulated UI evidence. Both interactions join exact source step
mappings; feedback belongs to its feedback screen. Normal maps to succeeded,
loading to pending, empty to idle and validation-failed/conflict/unavailable/
operation-failed to failed. Domain state must be admitted StateMachine data;
optional Workflow state equals source transition evidence for the trigger.
Workflow evidence is never advanced or executed. The SalesOrder declaration
uses confirmed only for normal success and draft otherwise, retaining the
separate source Workflow awaiting evidence on invocation outcomes.

`RecoveryBinding(kind, step, target)` joins an exact mapped source step and
interaction. `RetryInteraction` targets the same Entry or Invocation trigger;
`EditInput` targets Input; `ReturnToEntry` targets Entry. Empty recovery vectors
are valid. The fixture uses `recover` for empty/entry retry, conflict/input
edit, validation/input edit and unavailable or operation-failed/invocation
retry. Success/loading have no recovery. No retry policy, backoff, timer or
server execution is specified. Reset/replay are later deterministic Web
fixture controls, not logical domain recovery operations.

All unordered declaration vectors normalize by structural keys. Explicit
focus ranks and text bytes remain unchanged. Fixed-order canonical JSON binds
schema/version, kernel candidate/consumed-input, projection and semantic
identities and every readiness declaration. Escaped JSON framing preserves
opaque Unicode, quote, control and delimiter-bearing values injectively.
`CozyLogicalUiProjection.identity` supplies product identity; candidate and
consumed-input roles are distinct. No management hashes, generated file bytes,
paths, timestamps, HTML or CSS occur in complete logical content.

`verifyCurrent(value, kernelCandidate, projection, semantics, input)` rebuilds
the complete chain and compares candidate identity, consumed-input identity
and content. Equivalent reordering is current. Any changed source, label,
profile, local rule, field or outcome invalidates the old complete value,
even if its legacy candidate is unchanged. Generated files cannot revise
authoritative input.

`accept(completeCandidate, TargetReadyAcceptanceDecision(decisionId,
candidateIdentity, reviewerId))` returns
`Either[LogicalUiError, AcceptedTargetReadyLogicalUi]`. IDs are nonnull,
nonempty and exactly trimmed. Candidate identity equals the exact complete
candidate, never the kernel-only identity. Decision identity binds that
candidate plus decision/reviewer attribution; accepted identity also binds
complete consumed input. The accepted private-constructor value exposes
`identity`, `candidate` and `decision`. This API validates data, not human
consent. Fixture construction and generation never accept implicitly; all
specification decisions are explicit fixtures, not final Phase acceptance.

Malformed null roots, vectors, elements, references or Option containers return
stable `LogicalUiError` codes and dotted/indexed input paths without null
exceptions. The `LUI501_` categories are `CHAIN_INVALID`, `INPUT_INVALID`,
`ELEMENT_INVALID`, `DUPLICATE_INVALID`, `PROFILE_INVALID`, `REGION_INVALID`,
`CONTROL_INVALID`, `FOCUS_INVALID`, `TEXT_INVALID`, `TEXT_REFERENCE_INVALID`,
`FIELD_INVALID`, `RULE_INVALID`, `SOURCE_INVALID`, `OUTCOME_INVALID`,
`RECOVERY_INVALID`, `CURRENTNESS_INVALID` and `ACCEPTANCE_INVALID`.
Executable specifications are `CozyLogicalUiReadinessSpec` and the additive
three-intent scenario in `CozyLogicalUiRuntimeSpec`.

At the IR slice, structural readiness HTML, one Progressive Static Web
simulation and browser evidence for the then-planned section 11 rows were
subsequent work. The current complete E3 candidate structural/Web producers
and browser proof are recorded in [section 14](#14-complete-structural-and-progressive-static-web-producers)
and [design section 13](../design/logical-ui-model.md#13-shared-complete-review-and-one-web-interpreter),
with the sole current coverage matrix in [section 11](#11-shared-salesorder-compact-success-contract).
Projections must bind complete/kernel/input/projection/semantic identities and
full declarations, not fixture ID alone. Target breakpoints, routes, DOM/ARIA,
buttons and reset/replay realization stay in those projections. The admitted
browser evidence includes normal and admitted failure/recovery flows in
compact, medium and expanded, keyboard/focus and visible content/overflow,
viewport switching and retained input/selection. The IR slice alone did not
close E3, Phase50.1 or the 59 original outcome obligations. Independent E3 Step
review is accepted, with the latest integrity focused review PASS accepted in
review ledger revision 5. S50.1.1/E3 was manually committed at
`84ab85a0984a0286507d63aca906c9a88c923f9b`; the
[Step commit checkpoint 002](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-step-acceptance-commit-002.json)
records that status. Actual attributed human feedback 「修正不要」 was received
on 2026-10-13; the [feedback record](../evidence/phase-50.1/readiness-candidate-001/feedback.md)
documents the applicable no-source-change disposition and retains the current
complete candidate without semantic source revision. On 2026-10-13, the human
user of owning conversation `01a0ff01-4772-76b2-bba1-e2542f0a9647` separately said:

> この候補を最終受入れする。Phase 50.1を最後まで続けて。コミットは引き続き親による手動ローカルコミットで。

[The actual acceptance input](../evidence/phase-50.1/readiness-accepted-001/acceptance.json)
binds complete candidate `sha256:dcb64b218d5921c65be0f1be7b1483565446dfd9fc3dbefd215c202e83f813b1`
and consumed input `sha256:dd1f3b249795e3857bdcb19a72abc2cc205edee8e4d736f8f2db42a2dd08c0a1`.
The separate [accepted package](../evidence/phase-50.1/readiness-accepted-001/README.md)
is generated from the same four authoritative source declarations; historical
candidate receipts remain candidate/null. Two producer suites passed 21 tests;
accepted-binding run002 passed six normal/loading cases, 107 observations and
eight parent-inspected images, with native/browser admission and cleanup complete.
Whole E4 Step review/manual local commit, full Phase review, final full
suite and distinct release/closure remain pending. The 59 original outcome
obligations remain outstanding.

## 14. Complete structural and Progressive Static Web producers

`CozyLogicalUiReadinessReview` and `CozyLogicalUiReadinessWeb` are package-local
pure projections of the complete readiness authority. Each exposes
`renderCandidate(completeCandidate, scenario)` and `renderAccepted(accepted,
scenario)`, returning a structured `Either[LogicalUiError, ...Preview]`.
The candidate entry does not accept. The accepted entry consumes a genuine
`AcceptedTargetReadyLogicalUi`, reconstructs its complete source chain and
re-admits its exact attributed decision through the readiness factory.
No legacy `AcceptedLogicalUi` is constructed and no fixture decision proves
human consent. Null, foreign and unsupported declarations fail with precise
`LUI501_REVIEW_*` or `LUI501_WEB_*` paths.

The common `CoverageCase(coverageId, windowClass, outcomeId)` and
`ReadinessScenario(normal, coverage)` reuse the existing `CompactScenario`,
`CompactFlow` and `OrderRecord`. The normal declaration names the sole shared
`sales-order-target-ready-v1` fixture and its compact normal coverage case.
Every admitted profile/outcome pair appears exactly once, with unique trimmed
coverage IDs, no null, missing, duplicate or foreign value. Coverage normalizes
by structural profile/outcome key. Existing CompactWeb admission supplies
exact normal source step, navigation, Powertype/variant and public
Operation/StateMachine from/to/succeeded evidence; using that factory does
not grant its historical browser evidence to a revised complete input.
The bounded driver requires one of each seven control roles, natural global
focus ranks 1/2/3/4, one labeled input, one list region and two detail regions
with consistent primary/secondary roles, and all seven outcome categories.
Normal/loading join the normal command, invocation failures join that same
command, empty joins Entry and validation joins the input. Normal retains the
successful source state; all other outcomes retain the initial domain state.
Recovery targets remain exactly factory-admitted source interactions.

`CompleteBinding` carries `candidateIdentity`, `consumedInputIdentity`,
`kernelCandidateIdentity`, `kernelInputIdentity`, `projectionIdentity`,
`semanticProjectionIdentity`, optional `acceptedIdentity`, optional
`decisionIdentity` and optional explicit decision. `SourceInputs` retains
`kernelContent`, `projectionContent`, `semanticContent`, `readinessContent`
from existing canonical APIs. Receipt JSON includes these as complete parsed
JSON subtrees, including every profile, control, field, rule, text, outcome
and recovery. SourceFlow keeps exact Operation/StateMachine/Powertype/variant,
from/to domain references, success UI and separate optional Workflow evidence.
These are product provenance, without output hashes, timestamps, absolute
paths or workflow certificates.

`ReviewReceipt` has schema `cozy.logical-ui-target-ready-review.v1`, version
`1`, renderer `cozy.logical-ui-target-ready-review.renderer.v1`, the common
binding, full normalized scenario, source declarations and flow, sorted source
diagnostics and outputs `review.html`, `readiness-review-receipt.json`.
`WebReceipt` has schema `cozy.logical-ui-target-ready-web.v1`, version `1`,
renderer `cozy.logical-ui-target-ready-web.renderer.v1`, that complete review
receipt, `simulated=true` and outputs, in order: `index.html`, `review.html`,
`compare.html`, `readiness-review-receipt.json`, `readiness-web-receipt.json`.
Serialization uses fixed field order and escaped structural data. Candidate
accepted/decision fields are consistently JSON null; accepted mode retains
actual complete decision/reviewer attribution and identities.

The structural preview exposes `html`, `receipt`, `receiptJson`, `diagnostics`
and UTF-8 `htmlBytes`. The Web preview exposes `html`, `reviewHtml`,
`compareHtml`, `receipt`, `receiptJson`, `reviewReceiptJson`, `diagnostics` and
UTF-8 byte accessors for its five output strings. Private constructors and
observable-only `copy` preserve the original candidate/accepted/scenario.
`verifyCandidateCurrent` and `verifyAcceptedCurrent` rerender supplied complete
authority and compare every observable, mode, declaration and decision.
Input, source, label, rule, profile, outcome, record, coverage or reviewer
revisions invalidate prior output even under the same kernel or fixture ID;
equivalent declaration order is current. No separate unchanged-file
certificate or management digest substitutes for recomposition.

Structural HTML reuses the unchanged candidate source renderer, with a
prominent complete authority overview and distinct `Kernel candidate evidence`
heading. Labeled tables expose all readiness metadata, normal records/flow,
every coverage case and exact source flow. Full canonical declarations are
escaped read-only details. Padded text preserves its supplied value.
No script, form, controller or external asset occurs on the structural page.

`index.html` contains one inert `application/json#readiness-data` WebReceipt
and one fixed inline interpreter. Embedded data escapes `<`, `>`, `&`, U+2028
and U+2029; source text is rendered with `textContent` or native `value`.
No executable source interpolation, eval, source innerHTML, fetch, XHR,
remote asset, storage, service worker or backend/domain/Workflow execution is
introduced. Source identity attributes cover screens, regions, interactions,
roles, focus, labels, fields, Value/Datatype, constraints, components, steps,
outcomes and recoveries. The source navigation selects the declared record
and opens detail in one native action, recording order/resource and source
UseCase provenance, then focusing the input. Natural DOM focus order is
navigation/input/selection/command, without positive tabindex. The input
preserves initial text, text/decimal inputmode, source label and
aria-required/invalid/describedby; feedback is live and pending is busy.

Only declared RequiredTextRule (nonblank first) and AsciiIdentifierRule
(uppercase ASCII letters, digits and hyphen) execute locally, on blur or
invocation, with exact constraint/feedback/message attribution. Invalid text
keeps the command enabled after declared record and variant selection so
invocation can expose the local error. Pending and completed disable repeated
invocation. Valid confirmation displays the exact loading outcome first;
the separately labeled `complete-confirmation` fixture button produces the
menu-selected normal/conflict/unavailable/operation-failed terminal outcome.
The `fixture-outcome` menu also permits empty, validation-failed and loading
inspection. No timer or random completion exists. Terminal state/messages
come from the payload, keep order ID/label/amount and change only the declared
domain reference. Source Workflow awaiting is shown separately and never
advanced.

Recovery buttons derive solely from the active outcome's exact kind/step/
target. EditInput clears failure to idle, retains domain, selects normal as
the next simulated result and focuses input. Invocation retry keeps input
and selection and walks pending then fixture-completed normal; Entry retry
restores list/initial record and reachable navigation. Admitted ReturnToEntry
selects the list view without a domain action. `reset-fixture` restores exact
initial text, no selected order/variant, initial domain/idle UI, list view,
normal fixture choice and no result/error/recovery/pending. `replay-fixture`
uses those same reset inputs and walks declared order navigation, variant,
loading and normal deterministically. Invalid revised initial input is
preserved and diagnosed instead of replaced. Repeated replay has the same
semantic state. These helpers are explicitly fixture controls outside logical
focus ranking and semantic authority.

Actual `window.innerWidth` selects compact below 600, medium from 600 through
1023 and expanded at 1024 or greater. Compact shows one active screen; medium
retains navigation and one active content screen; expanded keeps list/detail
regions available with unselected detail controls disabled. Source field,
order, variant, outcome, pending and recovery state survive viewport changes.
When visibility hides a focused source control, an available declared
counterpart receives focus; otherwise current focus is retained. CSS uses
minmax(0,1fr), min-width:0, wrapped opaque content and visible focus rings.
Runtime geometry/control inspection exposes code/path/reason diagnostics in
`preview-diagnostics`: `LUI501_WEB_OVERFLOW`,
`REQUIRED_CONTENT_UNREACHABLE`, `ACTION_MISSING`, `FEEDBACK_MISSING`,
`NAVIGATION_MISSING`, `FOCUS_ORDER_INCONSISTENT`,
`RESPONSIVE_INTENT_INCONSISTENT` (all with the LUI501_WEB_ prefix).
They assess actual geometry, active content and source controls while
respecting legitimate initial/pending disabled states. Actual candidate browser
evidence inspects them alongside independent DOM geometry and control checks;
diagnostics alone do not establish PASS.

`compare.html` is a local shell with three titled interactive iframes of
the same `index.html`: 390 × 844, 834 × 1112 and 1440 × 900. Each starts from
the same declared list fixture and exact provenance. The comparison shell
may intentionally scroll horizontally; each app viewport retains its own
diagnostics. No cross-frame controller or per-profile source copy exists.

`Web.write(preview, siteParent, receiptParent)` is the sole five-file writer.
It recomposes captured original authority/mode/scenario and rejects stale
observables before any destination mutation. Both distinct parents must be
existing canonical absolute normalized nonsymlink directories, without
traversal or symlink components. All five direct targets are preflighted as
absent or regular nonsymlink files before any temporary creation or writing.
Owned UTF-8 sibling temporaries are staged and installed by per-file atomic
replacement, without a non-atomic fallback or cross-file transaction promise.
Structured IOException/SecurityException failures retain precise output
paths; only owned temporaries are cleaned and unrelated files are preserved.

Executable contracts are
[CozyLogicalUiReadinessReviewSpec](../../src/test/scala/cozy/ui/CozyLogicalUiReadinessReviewSpec.scala)
and [CozyLogicalUiReadinessWebSpec](../../src/test/scala/cozy/ui/CozyLogicalUiReadinessWebSpec.scala).
Their explicit accepted values are specification fixtures. The Web writer
scenario retains candidate output under `target/phase501-readiness-preview/site`
and receipts under its `evidence` sibling. The bounded executable browser
specification is [run-readiness-browser.mjs](../../scripts/test/phase501/run-readiness-browser.mjs),
with required `--site`, `--receipt`, `--evidence`, `--playwright` and `--browser`
absolute flag/value pairs and optional `--accepted-input`. Five pairs retain
candidate-full mode with null accepted fields and all 21 cases. The sixth pair
selects accepted-binding mode and points to the canonical regular declaration
export outside the served site and output. It admits exactly `binding` and
`sourceInputs`, requires the frozen six source identities, all four full
declarations equal to the native receipt, distinct candidate/consumed/decision/
accepted roles and trimmed native decision/reviewer attribution. This checks
projection relationships; the actual human decision is recorded above.
A supervised exclusive run directory, GET-only
three-resource loopback server and invocation-owned Chrome profile bound its
actual execution; success/failure/interruption await owned cleanup.

[RenderAcceptedReadiness.scala](../../scripts/test/phase501/RenderAcceptedReadiness.scala)
is a thin reproducible Test-classpath proof adapter with two absolute arguments:
the actual acceptance JSON and an output parent. It recomposes the existing
`readyCandidate()` and `readinessWebScenario()`, requires the decision's exact
candidate/consumed identities before any output mutation and uses native
`verifyCurrent`, `TargetReadyAcceptanceDecision`, `accept`, both `renderAccepted`
and `verifyAcceptedCurrent` entrypoints. Complete binding/source declarations
and structural receipts must agree; Web observables must rerender deterministically.
The unchanged native `Web.write` generates three HTML and two receipt files in
`site`/`evidence`; `accepted-logical-ui.json` exports exactly the structural
receipt's `binding` and `sourceInputs`. It supplies approved complete declarations
for Phase 51 without a new semantic schema, transport decoder or CLI. Accepted
and decision identities come from native values, never independent calculation.
Native failure code/path/reason remains attributable and prevents success.

Accepted-binding browser mode is bounded to six normal/loading cases at
390 × 844, 834 × 1112 and 1440 × 900, with six case images plus structural and
completed comparison images. It retains embedded native receipts, keyboard
navigation/input/variant/focus, pending duplicate-disabled invocation and normal
completion. It opens Complete source provenance through the native summary and
records visible text, geometry and parsed accepted/decision/reviewer provenance
without document overflow. Structural accepted attribution and all three
independent comparison frames bind the same complete inputs. Portable/terminal
`proofMode` and `proofScope` distinguish this six-case proof from the still
declared 21-case source coverage. Unchanged failure/recovery/reset/replay/resize
reuse admitted E3 run007. No interpreter invocation or lifecycle change is added.
The actual accepted-binding run002 passed all six selected cases and retained
107 observations and eight parent-inspected images. Native generation/currentness/
determinism and both producer suites (21 tests) passed; the parent admitted
the actual export, both accepted projections and browser evidence with owned
cleanup complete. [The accepted package](../evidence/phase-50.1/readiness-accepted-001/README.md)
retains those products. This six-case proof does not rerun the other 15 declared
cases or failure/recovery/reset/replay/resize transitions.

The current admitted run007 supplied 21 individually attributed cases, 1067 observations
and 23 parent-inspected images, covering all declared recoveries, actual local
validation, keyboard/focus, reset/two semantic replays, state-preserving resize
including 599/600/1023/1024 boundaries, structural review and three independent
comparison frames. Source provenance, geometry/availability, ARIA and current
diagnostics accompany observed states. It additionally records 12 normal
pointer recoveries from focused editable input across the four editable failure
outcomes and three profiles, plus five focused source-recovery resize walks,
including empty entry retry, through all profile transitions and boundary widths.
The historical run006 PASS retains its 21 cases, 778 observations and 23 images
in local history; its coverage omitted these two transitions.
The [CPB-P501-E3-001 repair](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-review-fix-manifest-001.json)
retains recovery nodes/listeners when the exact active outcome is unchanged.
Parent-admitted focused validation and run007 passed; independent focused
re-review passed and was accepted in review ledger revision 3, resolving
CPB-P501-E3-001 as recorded in the [accepted re-review checkpoint](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-recovery-rereview-checkpoint-001.json).
The latest independent integrity focused review PASS was accepted in review
ledger revision 5, and S50.1.1/E3 was manually committed at
`84ab85a0984a0286507d63aca906c9a88c923f9b` in
[Step commit checkpoint 002](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-step-acceptance-commit-002.json).
The narrow formatting integrity delta removes blank provenance separator spaces
in the current comparison; all 21 producer tests passed. Run007 remains the
interaction proof, predates that formatting change and is not a new browser
observation of the current comparison bytes. The retained
[app](../evidence/phase-50.1/readiness-candidate-001/site/index.html),
[structural review](../evidence/phase-50.1/readiness-candidate-001/site/review.html),
[comparison](../evidence/phase-50.1/readiness-candidate-001/site/compare.html),
[complete Web receipt](../evidence/phase-50.1/readiness-candidate-001/evidence/readiness-web-receipt.json),
[complete review receipt](../evidence/phase-50.1/readiness-candidate-001/evidence/readiness-review-receipt.json)
and [portable observations](../evidence/phase-50.1/readiness-candidate-001/browser-observations.json)
share the same complete candidate; section 11 owns the sole coverage matrix.
The [browser review](../evidence/phase-50.1/readiness-candidate-001/browser-review.md)
records versions, relative screenshots, limits and retained prior-run history.
Portable copies supply product review rather than host-specific management
proof. Actual attributed human feedback 「修正不要」 received on 2026-10-13 and
its applicable no-source-change disposition are documented in the
[feedback record](../evidence/phase-50.1/readiness-candidate-001/feedback.md).
The separate exact human acceptance is recorded in section 13 and
[acceptance.json](../evidence/phase-50.1/readiness-accepted-001/acceptance.json).
Native accepted projection/export and bounded six-case browser admission passed
and their real products are retained in
[the accepted package](../evidence/phase-50.1/readiness-accepted-001/README.md).
Whole E4 Step review/parent manual local commit, full Phase review, final
full-suite validation and distinct release/closure remain pending.
