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
| P50-COMPACT-NORMAL-001 | sales-order-compact-v1 | normal `confirm` | `order-list/entry`, `order-list/to-detail`, `order-detail/select`, `order-detail/invoke`, `order-detail/observe` | SalesOrder exact public binding set declared above; command `#operation` / `#state-machine`, selection `#powertype` variant `awaiting-confirmation` | compact, NavigateToDetail | simulated success: domain draft to confirmed, UI succeeded | P50-MAIN-02/03/04/05/06/07 | selected eight suites: 74/74 passed; actual compact browser PASS, Chrome 154.0.8037.94 at 390 × 844, parent inspected six screenshots; retained [app](../evidence/phase-50/compact/index.html), [structural review](../evidence/phase-50/compact/review.html), [complete receipt](../evidence/phase-50/compact/compact-preview-receipt.json), [portable observations](../evidence/phase-50/compact/browser-observations.json), [six screenshots and evidence scope](../evidence/phase-50/compact/README.md); partial compact normal simulation only, broader target/profile/outcome and aggregate full-suite assurance pending Phase50.1; independent Step/Phase acceptance pending |

This is the single canonical coverage row extended with actual S50.2
compact normal-flow browser evidence. It does not claim full target-ready
acceptance. The original
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
