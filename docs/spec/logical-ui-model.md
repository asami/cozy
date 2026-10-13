# Logical UI Model v1 Specification

Document role: normative behavior contract for `cozy.logical-ui.v1`.
The ownership and layering rationale is in
[`docs/design/logical-ui-model.md`](../design/logical-ui-model.md).

実行証跡の保存先: `macmini:~/backup/journal/2026/10/cozy/COZY-SCALA2-SYNC-20261013-001/COZY-EVIDENCE-CLEANUP-20261013-003/`。同期完了が2026-10-13の場合の原則保持期限: 2026-11-12（完了から30日）。同期中は期限なしで保持し、期限到来だけで自動削除しません。

「ローカル保管」と記した元のリポジトリ相対パスは、保存先の `original-tree/<元の相対パス>` に対応します。大きな JSON・HTML は `.gz` 付きで保存し、`gzip -dc` で元の記録を読み取れます。別ホストではこの保存先の可用性を確認してください。

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

## 10. Candidate preview and output

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

Rendering consumes selected typed declarations directly. Schema and renderer metadata describe production; explicit decision/reviewer attribution describes acceptance. No artifact hash, currentness API or regenerated-artifact equality gate is retained. Writers validate inputs and paths, then write UTF-8 directly; ordinary failures report errors and recovery is regeneration.

def writeCandidate(
  reviewValue: LogicalUiCandidateReview,
  parent: Path,
  target: Path
): Either[LogicalUiError, Unit]
```

`LogicalUiCandidateReview` exposes `html`, `receipt`, `diagnostics`,
and UTF-8 `htmlBytes`. Its `copy(html, receipt, diagnostics)` retains the
private declared candidate/projection/semantics tuple. `CandidateReviewReceipt`
has exactly these fields, in order:

The overview displays `Candidate preview`, status `candidate (not accepted)`,
schema/profile and schema/profile and source declarations. Acceptance decision
evidence are absent. The other sections and informational diagnostics use the
same structural builders as accepted review in section 8. Dynamic text is
escaped; output is self-contained read-only HTML with inline CSS, without
scripts, forms, remote resources, route execution, or an acceptance action.
The accepted review API and HTML use their section 8
contract.

The executable specification is
`src/test/scala/cozy/ui/CozyLogicalUiCandidateReviewSpec.scala`. It consumes the
shared SalesOrder fixture, exercises factory-admitted foreign and changed
tuples,  at least twenty reordered constructions,
escaped purpose text, and direct writer admission/preservation.

## 11. Shared SalesOrder compact success contract

`CozyLogicalUiSalesOrderFixtures` is a test-owned, target-neutral immutable
fixture shared by the Phase43 accepted specification and candidate preview.
Its pure `candidate(reversed)` composes existing candidate, screen projection,
and semantic factories with `Either` and never accepts a candidate. The
Phase43 acceptance specification explicitly calls
`accept(candidate, AcceptanceDecision("accepted-sales-order"))`.
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
| P50-COMPACT-NORMAL-001 | sales-order-compact-v1 | normal `confirm` | `order-list/entry`, `order-list/to-detail`, `order-detail/select`, `order-detail/invoke`, `order-detail/observe` | SalesOrder exact public binding set declared above; command `#operation` / `#state-machine`, selection `#powertype` variant `awaiting-confirmation` | compact, NavigateToDetail | simulated success: domain draft to confirmed, UI succeeded | P50-MAIN-02/03/04/05/06/07 | selected eight suites: 74/74 passed; actual compact browser PASS, Chrome 154.0.8037.94 at 390 × 844, parent inspected six screenshots; retained app（ローカル保管: `docs/evidence/phase-50/compact/index.html`）, structural review（ローカル保管: `docs/evidence/phase-50/compact/review.html`）, complete receipt（ローカル保管: `docs/evidence/phase-50/compact/compact-preview-receipt.json`）, portable observations（ローカル保管: `docs/evidence/phase-50/compact/browser-observations.json`）, six screenshots and evidence scope（ローカル保管: `docs/evidence/phase-50/compact/README.md`）; partial compact normal simulation only, broader target/profile/outcome and aggregate full-suite assurance pending Phase50.1; independent Step reviews `P50-S5011-STEP-REVIEW-001@1` / `P50-S5021-STEP-REVIEW-001@1` and sole Phase review `P50-PHASE-FULL-REVIEW-001@1` PASS; narrow increment closure is bound to [the distinct local release and native audit](../phase/phase-50-closure-ledger.md) |
| P501-COMPACT-NORMAL-001 | sales-order-target-ready-v1 | normal confirm | list entry/navigation; detail selection/invoke/feedback/observe | same exact SalesOrder public binding set | compact | normal: confirmed/succeeded | complete readiness/profile/normal | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/compact-normal.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); current accepted case image（ローカル保管: `docs/evidence/phase-50.1/readiness-accepted-001/screenshots/compact-normal.png`）; [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-COMPACT-LOADING-001 | sales-order-target-ready-v1 | normal confirm | detail invoke/feedback | same exact SalesOrder public binding set | compact | loading: draft/pending | loading/accessibility | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/compact-loading.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); current accepted case image（ローカル保管: `docs/evidence/phase-50.1/readiness-accepted-001/screenshots/compact-loading.png`）; [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-COMPACT-EMPTY-001 | sales-order-target-ready-v1 | normal confirm; alternative recover | list entry; recover list entry | same exact SalesOrder public binding set | compact | empty: draft/idle; retry entry | empty/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/compact-empty.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-COMPACT-VALIDATION-001 | sales-order-target-ready-v1 | alternative enter/recover | detail input/feedback; recover input | same exact SalesOrder public binding set | compact | validation-failed: draft/failed; edit input | input/local validation/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/compact-validation-failed.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-COMPACT-CONFLICT-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover input | same exact SalesOrder public binding set | compact | conflict: draft/failed; edit input | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/compact-conflict.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-COMPACT-UNAVAILABLE-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | compact | unavailable: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/compact-unavailable.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-COMPACT-OPERATION-FAILED-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | compact | operation-failed: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 390 × 844; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/compact-operation-failed.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-MEDIUM-NORMAL-001 | sales-order-target-ready-v1 | normal confirm | list entry/navigation; detail selection/invoke/feedback/observe | same exact SalesOrder public binding set | medium | normal: confirmed/succeeded | complete readiness/profile/normal | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/medium-normal.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); current accepted case image（ローカル保管: `docs/evidence/phase-50.1/readiness-accepted-001/screenshots/medium-normal.png`）; [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-MEDIUM-LOADING-001 | sales-order-target-ready-v1 | normal confirm | detail invoke/feedback | same exact SalesOrder public binding set | medium | loading: draft/pending | loading/accessibility | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/medium-loading.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); current accepted case image（ローカル保管: `docs/evidence/phase-50.1/readiness-accepted-001/screenshots/medium-loading.png`）; [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-MEDIUM-EMPTY-001 | sales-order-target-ready-v1 | normal confirm; alternative recover | list entry; recover list entry | same exact SalesOrder public binding set | medium | empty: draft/idle; retry entry | empty/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/medium-empty.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-MEDIUM-VALIDATION-001 | sales-order-target-ready-v1 | alternative enter/recover | detail input/feedback; recover input | same exact SalesOrder public binding set | medium | validation-failed: draft/failed; edit input | input/local validation/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/medium-validation-failed.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-MEDIUM-CONFLICT-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover input | same exact SalesOrder public binding set | medium | conflict: draft/failed; edit input | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/medium-conflict.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-MEDIUM-UNAVAILABLE-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | medium | unavailable: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/medium-unavailable.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-MEDIUM-OPERATION-FAILED-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | medium | operation-failed: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 834 × 1112; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/medium-operation-failed.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-EXPANDED-NORMAL-001 | sales-order-target-ready-v1 | normal confirm | list entry/navigation; detail selection/invoke/feedback/observe | same exact SalesOrder public binding set | expanded | normal: confirmed/succeeded | complete readiness/profile/normal | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-normal.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); current accepted case image（ローカル保管: `docs/evidence/phase-50.1/readiness-accepted-001/screenshots/expanded-normal.png`）; [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-EXPANDED-LOADING-001 | sales-order-target-ready-v1 | normal confirm | detail invoke/feedback | same exact SalesOrder public binding set | expanded | loading: draft/pending | loading/accessibility | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-loading.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; historical candidate/null receipt retained; native accepted generation and actual accepted-binding run002 PASS (six observed cases, 107 observations, eight parent-inspected images); current accepted case image（ローカル保管: `docs/evidence/phase-50.1/readiness-accepted-001/screenshots/expanded-loading.png`）; [accepted observations and scope](../evidence/phase-50.1/readiness-accepted-001/README.md) |
| P501-EXPANDED-EMPTY-001 | sales-order-target-ready-v1 | normal confirm; alternative recover | list entry; recover list entry | same exact SalesOrder public binding set | expanded | empty: draft/idle; retry entry | empty/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-empty.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-EXPANDED-VALIDATION-001 | sales-order-target-ready-v1 | alternative enter/recover | detail input/feedback; recover input | same exact SalesOrder public binding set | expanded | validation-failed: draft/failed; edit input | input/local validation/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-validation-failed.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-EXPANDED-CONFLICT-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover input | same exact SalesOrder public binding set | expanded | conflict: draft/failed; edit input | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-conflict.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-EXPANDED-UNAVAILABLE-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | expanded | unavailable: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-unavailable.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |
| P501-EXPANDED-OPERATION-FAILED-001 | sales-order-target-ready-v1 | exception failure; alternative recover | detail invoke/feedback; recover invoke | same exact SalesOrder public binding set | expanded | operation-failed: draft/failed; retry invoke | failure/recovery | actual browser PASS, run007, Chrome 154.0.8037.94 at 1440 × 900; parent inspected case image（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/screenshots/expanded-operation-failed.png`）; actual observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）; candidate, acceptance pending |

All complete-fixture rows share `sales-order-target-ready-v1`, the exact
SalesOrder public binding set above, and one complete input across every
profile and outcome. All 21 P501 rows now retain actual candidate browser
simulation PASS and individually linked case evidence from the admitted
run007; the portable review（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-review.md`）
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

Rendering consumes selected typed declarations directly. Schema and renderer metadata describe production; explicit decision/reviewer attribution describes acceptance. No artifact hash, currentness API or regenerated-artifact equality gate is retained. Writers validate inputs and paths, then write UTF-8 directly; ordinary failures report errors and recovery is regeneration.

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

Rendering consumes selected typed declarations directly. Schema and renderer metadata describe production; explicit decision/reviewer attribution describes acceptance. No artifact hash, currentness API or regenerated-artifact equality gate is retained. Writers validate inputs and paths, then write UTF-8 directly; ordinary failures report errors and recovery is regeneration.

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

Rendering consumes selected typed declarations directly. Schema and renderer metadata describe production; explicit decision/reviewer attribution describes acceptance. No artifact hash, currentness API or regenerated-artifact equality gate is retained. Writers validate inputs and paths, then write UTF-8 directly; ordinary failures report errors and recovery is regeneration.

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
app（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/site/index.html`）,
structural review（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/site/review.html`）,
comparison（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/site/compare.html`）,
complete Web receipt（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/evidence/readiness-web-receipt.json`）,
complete review receipt（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/evidence/readiness-review-receipt.json`）
and portable observations（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-observations.json`）
share the same complete candidate; section 11 owns the sole coverage matrix.
The browser review（ローカル保管: `docs/evidence/phase-50.1/readiness-candidate-001/browser-review.md`）
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

## October 13 integration with Phase 71.5

The completed Phase 50/50.1 candidate review, readiness admission, explicit acceptance and compact/medium/expanded Web functions remain available. `TargetReadyAcceptanceDecision(decisionId, reviewerId)` is applied to the selected complete candidate passed to `accept`. `CompleteBinding` retains that optional decision; source IR, domain bindings, scenarios, diagnostics and output names remain ordinary production metadata. `CandidateReviewReceipt` contains schema, version and renderer profile. Artifact hashes, currentness comparisons and atomic temporary-file installation are removed without a replacement freshness mechanism. Required declarations, domain joins, null inputs, output containment and ordinary I/O failures remain checked. Historical Phase acceptance records and generated evidence retain their dated implementation meaning.
