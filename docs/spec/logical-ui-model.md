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
