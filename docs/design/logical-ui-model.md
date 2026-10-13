# Logical UI Model Design

Document role: normative design for the Cozy-owned Logical UI authority kernel.
The executable contract is [`docs/spec/logical-ui-model.md`](../spec/logical-ui-model.md).
This design owns `cozy.logical-ui.v1` only; it does not define screen projection,
HTML rendering, target-framework generation, or CML/CNCF source semantics.

## 1. Authority boundary

Logical UI v1 is a consumer of a public Component surface and of three explicit
UseCase identities. It never creates a parallel Entity, Aggregate, Operation,
or UseCase model.

```text
identity-pinned public Component surface + Business/System/UI UseCase references
  -> normalized Logical UI candidate
  -> explicit acceptance decision with a nonempty trimmed decision ID
  -> accepted Logical UI authority

raw prompts / codec bytes / review HTML / route tables / target code / caches
  -> non-authoritative projections or transport only
```

The candidate is reviewable semantic input, not accepted authority. An
`AcceptanceDecision` is the sole feedback-decision form in v1. Acceptance
consumes the actual normalized candidate and explicit decision and retains both.
The decision ID records the acceptance fact without certifying artifact bytes.

## 2. Component public-surface binding

Every Component reference is the exact triple `(namespace, id, version)`. Its
canonical qualified name is literally `namespace.id`; version remains part of
the identity-pinned coordinate. The v1 IR never derives a local name, searches
private CML, or resolves a nearest version.

`ComponentSurface` records an exact coordinate and its closed vector of public
opaque export IDs. `ComponentBinding` repeats the exact coordinate and one
export ID. A binding is admitted only when an equal coordinate is declared in
the candidate input and that surface explicitly lists the export ID. Opaque
export IDs are compared exactly and are not parsed into a guessed model path.

## 3. Three-layer UseCase boundary

`Business`, `System`, and `UI` are distinct v1 layers. A candidate contains one
nonempty, pairwise-distinct identity for each layer and exactly these explicit
realization relations:

```text
Business UseCase -> System UseCase -> UI UseCase
```

The relations are values, not inheritance or a name convention. A direct
Business-to-UI link, a reversed relation, a missing layer identity, a duplicate
identity, or any extra v1 relation is rejected. LUI43-02 later owns mappings
from UI UseCase steps to screens; this kernel owns no screen, route, widget,
or target interaction.

## 4. Normalization and canonical content

Candidate construction first validates the closed public-surface and UseCase
inputs, then sorts Component surfaces by full coordinate, export IDs
lexicographically, bindings by coordinate plus export ID, and realization
relations by source/target layer and identity. Canonical logical content and
candidate JSON use declared field orders. Equivalent vector orderings produce
the same normalized typed input and canonical serialization. The candidate
JSON retains its original schema and version and contains only schema, version,
kind, component surfaces, component bindings, and UseCases.

## 5. Intentional non-goals

The kernel does not expose all Component vocabulary; it merely admits exact
public references for later use. It has no CML grammar, source reader, HTML
renderer, screen composition, projection policy, Widget/Flutter abstraction,
or automatic acceptance. LUI43-02 through LUI43-05 own those later concerns.

## 6. UseCase-to-Screen projection boundary

LUI43-02 introduces `cozy.usecase-screen-projection.v1` as a typed projection
over the committed candidate kernel. `CozyLogicalUi.project` takes a
`LogicalUiCandidate` and typed projection input. The projection retains the candidate as an
explicit authority dependency while remaining deliberately separate from
`cozy.logical-ui.v1` candidate JSON and `CozyLogicalUiCodec`.

The projection's catalog is a vector of opaque three-layer `UseCaseLayers`
identities and must include the exact candidate value. `UiUseCaseStep` is an
exact UI-layer reference plus a nonempty ID and one closed path: normal,
alternative, exception, or system-only. Mappings are explicit joins from
steps to concrete screen interactions. They permit one-to-many and
many-to-one coverage, screen reuse across UI UseCases, and unmapped
system-only steps. Every non-system step is covered exactly by at least one
mapping, and duplicate mappings fail closed.

The logical screen is semantic rather than visual. It has a unique identity,
purpose values, one Entity/Aggregate/View/Workflow subject, a rooted region tree with
parent and sibling order, named interactions, feedback states, and logical
navigation. Interaction kinds, Component roles, and feedback states are closed
typed alternatives. Every mapping must justify its screen and interaction;
every candidate binding selected by the projection must be used by a subject,
interaction usage, or mutation action. All binding references are exact public
candidate bindings, so this projection cannot perform source lookup, private
name parsing, export inference, or fallback version resolution.

Workflow is a closed public Component role and a Logical Screen pattern source
peer of Entity, Aggregate, and View. A Workflow subject uses the same exact
public `ComponentBinding` admission and canonical subject identity rules as
those peers. It does not introduce a Workflow renderer, executor, source
reader, local authority, client-side state machine, or a client-side replacement
for server Workflow, authorization, or observability. Public `Operation`
remains the executable server-side request boundary, and Workflow never becomes
an Aggregate mutation-root substitute.

Aggregate boundaries are explicit values containing an Aggregate reference,
root, members, and public Operation bindings. Each public Operation binding is
owned by exactly one boundary, so the same binding cannot authorize mutations
in multiple boundaries; boundaries may not overlap. A mutation is valid only
on a boundary root through one of that boundary's public Operations and only
when the invocation also carries the exact Operation-role usage. Direct child
mutation is therefore rejected even when a public Operation exists. The
projection does not infer Operation semantics or StateMachine meaning.

The projection retains the actual candidate and normalized input. Canonical
projection content contains the typed input alone and is deterministic under
catalog, step, screen, interaction, mapping, usage, feedback, region, and
boundary permutations. This layer introduces no renderer, HTML, route, widget,
codec, acceptance, pattern, constraint, reachability, or target-framework concern.

## 7. Pattern, constraint, and state semantics

LUI43-03 adds `CozyLogicalUiSemantics` as a distinct package-local semantic
projection. It consumes the actual normalized screen projection
and an explicit typed input. This layer classifies Logical UI meaning without
expanding the candidate kernel, transport codec, acceptance authority, or
source-reader boundary.

The closed catalogs intentionally remain small: Purpose is Browse, Inspect,
Edit, or Confirm; Display is Collection, Detail, Form, or Status; Interaction
Pattern is Navigate, Select, Input, Command, or Observe; validation authority
is LocalDeterministic, ContextDependent, or ServerAuthoritative; and UI
interaction state is Idle, Pending, Succeeded, or Failed. The patterns
correspond respectively to Navigation, Selection, Input, Invocation, and
Entry/Query/Observation/Feedback interactions. They do not select widgets,
routes, CSS, or a target framework.

Semantic screen records are complete bindings over the screen projection:
regions, interactions, and UI interaction states are each covered exactly once.
Component semantic records reuse exact public bindings and roles already
admitted by the projection. Multiplicity belongs only to Value and Datatype
meaning. Powertype variant IDs are opaque. Constraint identities are the
whole `(constraintId, detailCode)` pair, and categories are checked against
their Component roles. Operation DbC is represented by distinct precondition
and postcondition declarations. Validation authority is classification only;
the UI layer neither evaluates constraints nor replaces server authority.

State domains are intentionally not coercible: a domain StateMachine uses
`DomainStateReference`, CNCF Workflow uses `WorkflowStateReference`, and the
local lifecycle uses `UiInteractionState`. A transition action is an explicit
admission record that binds a non-system UI step and an existing mapping to the
same public Operation and StateMachine usages. It carries from/to domain states
but performs no transition and infers no Workflow state.

Canonical semantic content uses the normalized typed input and structural
tuple ordering for opaque domain identities. Vector permutations produce the
same normalized input and canonical representation; duplicate, missing,
incompatible, or unadmitted values fail closed with stable
`LUI43_SEMANTICS_*` diagnostics.

## 8. Read-only review and direct HTML output

`CozyLogicalUiReview` receives supplied accepted, screen-projection, and semantic
values. Its ordinary required-value checks identify null accepted, projection,
semantics, candidates, and decision with `LUI43_REVIEW_INPUT_INVALID` and the
actual missing-input path. Domain admission belongs to the respective
constructors. Review introduces no tuple equality, currentness, or revalidation
protocol. The renderer does not create acceptance, evaluate DbC or validation,
run Operations or StateMachine transitions, infer Workflow state, or select
target widgets/routes.

The review HTML is a deterministic inspection surface, not an application.
It uses escaped text, stable normalized ordering, an inline stylesheet, and
semantic headings/tables for accessible reading. Its sections expose the
three-layer UseCase realization, catalog/step coverage, navigation graph and
reachability, screen composition, exact public Component bindings, pattern and
UI-state classifications, constraints and feedback identities, and explicit
transition admission evidence. Each screen explicitly shows logical primary
purpose, logical secondary purposes, and semantic Purpose. Transition evidence
separately shows domain state, optional Workflow ID/state ID evidence (or a
deterministic missing representation), and UI state. Review diagnostics make
omissions visible but do not become acceptance decisions. The overview includes
the schema, renderer profile, and escaped explicit acceptance decision ID as
ordinary review facts. The review value retains HTML and typed diagnostics;
its byte accessor performs genuine UTF-8 encoding.

The only filesystem operation is an explicit direct `write` of rendered HTML.
It requires a nonnull review, existing non-symlink parent, and regular-or-absent
non-symlink `*.html` target whose parent path is exactly the supplied parent as
written. The direct-child admission rejects `.`/`..` and intermediate symbolic
links, including symlink-plus-`..` traversal, before writing. After this single
admission it writes the selected target directly with create/write/truncate
options. Ordinary I/O, security, and runtime failures report HTML-write errors;
a late failure may leave partial generated output and recovery is regeneration.
It never reads an HTML page as authority or publishes/registers an artifact.
Renderer changes remain a read-only review concern.

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

## 9. Additive candidate preview boundary

Rendering consumes selected typed declarations directly. Schema and renderer metadata describe production; explicit decision/reviewer attribution describes acceptance. No artifact hash, currentness API or regenerated-artifact equality gate is retained. Writers validate inputs and paths, then write UTF-8 directly; ordinary failures report errors and recovery is regeneration.

## 10. Compact fixture reuse

Phase43 candidate/projection/semantic modeling and Phase74's existing public
vocabulary suffice for the compact normal SalesOrder flow. This increment needs
no core, codec, runtime vocabulary or selection changes. Medium and exhaustive
missing-IR work remain owned by Phase50.1; runtime integration is outside this
contract.

The shared test-owned `CozyLogicalUiSalesOrderFixtures` extracts the existing
Phase43 model construction, retaining its logical values, Workflow subject,
constraints, feedback and normal `confirm` mappings. It composes existing pure
factories into a candidate tuple; accepted specification construction keeps its
explicit original acceptance decision. Equivalent reordering includes candidate
surface exports/bindings/realizations and projection/semantic vectors.

The immutable `DemoOrder` and `CompactSuccessFixture` add repeatable initial and
simulated result values for S50.2: `SO-001`, `Sales order SO-001`, `100.00`, and
the exact public StateMachine binding in domain `draft` and `confirmed` states.
The result's distinct UI state is `SucceededInteractionState`. The declared
action remains the existing normal `confirm` / `order-detail` / `invoke`
transition admission using the public Operation and StateMachine. Consuming
these records executes no Operation, Workflow or domain transition and creates
no accepted authority.

The sole canonical coverage row is `P50-COMPACT-NORMAL-001` in
[the specification, section 11](../spec/logical-ui-model.md#11-shared-salesorder-compact-success-contract),
fixture `sales-order-compact-v1`, profile compact, and simulated success. It
binds normal `confirm` to `order-list/entry`, `order-list/to-detail`,
`order-detail/select`, `order-detail/invoke`, and `order-detail/observe`, exact
SalesOrder public bindings, and P50-MAIN-02/03/04/05/06/07. S50.2 extends
that same row with the compact contract and actual browser evidence. Compact
normal-flow browser validation passed; full target-ready acceptance remains
pending, and the
original full-outcome checklist rows remain open in Phase50.1.

`CozyLogicalUiCandidateReviewSpec` provides executable coverage of this additive
preview and fixture contract alongside the unchanged explicit accepted flow.
Target/framework/browser fields remain outside Logical UI semantic IR.

## 11. Compact Web projection and declared simulation

`CozyLogicalUiCompactWeb` is a package-private target projection over the
existing candidate, screen projection, semantic projection and complete
declared `CompactScenario`. It consumes the candidate review's tuple admission
and diagnostics before binding the scenario. It introduces no second logical,
domain or server authority. The public core IR, vocabulary, selection, codecs,
runtime and both accepted/candidate review APIs retain their responsibilities.

The contract is deliberately one compact normal flow: Browse/Collection list
entry, source navigation to a distinct Confirm/Detail screen, source Powertype
selection, public Operation/StateMachine invocation and source observation.
Every source interaction maps to the exact existing normal UI step. The
matching source transition action binds the declared initial/result domain
references and separate succeeded UI state. Binding the transition evidence
does not execute that action. Workflow state remains in the source structural
review rather than being conflated with either displayed state. The test-owned
`compactWebScenario` translates `compactSuccess` without changing its original
values or the Phase43 acceptance decision/assertions.

Rendering consumes selected typed declarations directly. Schema and renderer metadata describe production; explicit decision/reviewer attribution describes acceptance. No artifact hash, currentness API or regenerated-artifact equality gate is retained. Writers validate inputs and paths, then write UTF-8 directly; ordinary failures report errors and recovery is regeneration.

The app carries source provenance in data attributes and embeds its full
receipt as inert JSON. Fixed client code consumes declared values with
`textContent`. HTML escaping and HTML-safe JSON escapes prevent opaque labels
or amounts from creating markup or executable code. A compact single-column
flow uses target selection context, then NavigateToDetail to another logical
screen and encoded location hash. Selecting the exact source Powertype variant
enables confirmation; confirmation displays the supplied successful record,
domain state and separate UI state, reveals `Simulated success`, and disables
the command. Candidate/simulation labels explain the assurance to the user.
No provider, backend, real form, storage, timer, retry or simulation engine is
introduced. Broader profile/outcome/replay/recovery behavior remains successor
work.

The structural page reuses the unchanged candidate renderer and inserts one
read-only scenario section immediately before its closing main. The declaration
includes fixture/coverage/profile, exact UI step/flow, initial/result records
and distinct states, public command/selection bindings and Powertype variant.
It has no interaction surface. App and review share the same receipt inputs.

`CozyLogicalUiCompactWebSpec` is the executable contract and retains one exact
production-writer SalesOrder bundle under `target/phase50-compact-preview` for
actual browser acceptance. Its generation and the browser entrypoints are
specified in [specification section 12](../spec/logical-ui-model.md#12-compact-candidate-web-target).
The executable Node ESM driver receives five explicit canonical paths, uses
the selected installed Playwright package and Google Chrome, owns an isolated
temporary profile/context/browser plus one loopback ephemeral HTTP server,
and exposes only the two generated HTML files to GET requests. It has bounded
actions and a 180-second overall timeout. It records actual DOM facts against
the complete source receipt through six stages/screenshots, rejects external
or mutating requests, and treats browser errors as failures. Cleanup waits for
in-flight startup before closing acquired resources; all normal, error, signal
and timeout paths await owned context/browser/server teardown and profile
removal. The parent owns execution and evidence inspection.

Portable observations retain full source receipt, observed states/control
bindings, browser version/viewport, relative screenshot names, network/error
facts and actual result. Native run paths, PID and ephemeral URL stay in
private target lifecycle receipts. Durable retention occurs only after actual
successful parent-supplied validation and screenshot inspection. Evidence
links extend the sole `P50-COMPACT-NORMAL-001` row; the row records actual
compact normal-flow browser PASS and retained evidence. Broad target-ready/profile/
outcome assurance and the full suite remain owned by Phase50.1, without
starting successor execution here.

## 12. Complete readiness composition

Rendering consumes selected typed declarations directly. Schema and renderer metadata describe production; explicit decision/reviewer attribution describes acceptance. No artifact hash, currentness API or regenerated-artifact equality gate is retained. Writers validate inputs and paths, then write UTF-8 directly; ordinary failures report errors and recovery is regeneration.

Closed Scala 2 alternatives describe primary/secondary regions, control roles,
text/decimal input meaning, required/ASCII local predicates and retry/edit/
entry recoveries. Profiles reuse the public Selection object's sole window
and placement vocabulary, including additive MediumIntent. Every projected
region and interaction joins by structural screen/local identity, with one
field per Input control and positive unique explicit global focus ranks only
on interactive controls. Navigation persistence and region persistence mean
availability; CSS geometry and viewport breakpoints remain projection-owned.
Global window intent is sufficient for this fixture; form-factor hints have
no independently useful semantics and are explicitly not applicable.

Text sources retain opaque source attribution and nonblank padded display
content. Local rules join exact existing constraint pairs, local Value/Datatype
authority and declared feedback associations; no context/server or Operation
condition is evaluated. Outcomes join exact mapped source steps and feedback
and keep UI lifecycle, admitted domain state and optional Workflow evidence
separate. Recovery declarations admit visible source interactions without
adding an executor, retry policy or domain operation. Reset/replay belong to
later deterministic Web fixture controls.

Admission is sequential and fail-closed: source roots and consumed semantic
identity, declaration vector shape, text/source identity, profiles and complete
region coverage, control compatibility/focus/coverage, exact field/rule joins,
then mapped outcome/state/recovery evidence. Null vectors, elements, Option
containers and `Some(null)` are checked before use. New diagnostics use stable
`LUI501_*` codes and exact declaration paths. Source factory diagnostics retain
their existing semantics.

The SalesOrder fixture is additive: legacy helpers/compact data are unchanged,
while ready helpers add recover mappings, exact datatype feedback association,
three profiles, all seven controls, order-reference rules/text sources and all
seven feedback outcomes. The one complete fixture identity is
`sales-order-target-ready-v1`; no profile/outcome copies exist. Fixture data
retain SO-001, Sales order SO-001, 100.00 and draft/confirmed source states.
At the IR slice, the sole specification section 11 matrix preserved historical
compact proof and marked complete target/profile/outcome rows planned. The
[current section 11 matrix](../spec/logical-ui-model.md#11-shared-salesorder-compact-success-contract)
now records the complete E3 candidate browser proof; [section 13](#13-shared-complete-review-and-one-web-interpreter)
and [specification section 14](../spec/logical-ui-model.md#14-complete-structural-and-progressive-static-web-producers)
record the complete structural/Web producers and their browser evidence.

> この候補を最終受入れする。Phase 50.1を最後まで続けて。コミットは引き続き親による手動ローカルコミットで。

## 13. Shared complete review and one Web interpreter

`CozyLogicalUiReadinessReview` owns the shared complete binding, explicit
scenario admission, normalized coverage, source flow and structural receipt.
`CozyLogicalUiReadinessWeb` consumes that result for one interactive app,
one local comparison shell and the sole five-file writer. Both remain
package-local projections over existing readiness factories. The paired
contract is [specification section 14](../spec/logical-ui-model.md#14-complete-structural-and-progressive-static-web-producers),
with [ReadinessReviewSpec](../../src/test/scala/cozy/ui/CozyLogicalUiReadinessReviewSpec.scala)
and [ReadinessWebSpec](../../src/test/scala/cozy/ui/CozyLogicalUiReadinessWebSpec.scala).
The existing kernel, codecs, Selection, Review and CompactWeb producers and
all legacy APIs remain their original authorities.

Candidate and genuine complete accepted entrypoints rebuild the same exact
source chain. Accepted mode re-admits its explicit complete decision and
retains accepted/decision/reviewer identities; it never synthesizes a legacy
accepted value. Full canonical kernel, projection, semantic and readiness
declarations are retained in `SourceInputs` and parsed as receipt subtrees.
The common binding separates complete candidate/consumed input from kernel
candidate/input and exact projection/semantic identities. SourceFlow adds
normal operation/state machine/powertype/variant, from/to state and separate
UI/Workflow evidence. This is product provenance, without management hashes,
output hashes, timestamps, machine paths or unchanged-file certificates.

The explicit scenario reuses CompactScenario records and source flow.
CompactWeb.render supplies pure exact source admission, independent of its
earlier compact browser observation. The shared producer requires all seven
feedback categories and every profile/outcome pair exactly once, with unique
trimmed IDs and structural normalization. It rejects foreign source joins,
contradictory failure domain states and metadata that this bounded one-field,
seven-control, list/detail driver cannot realize. One primary list region,
one primary and one secondary detail region share consistent roles across
profiles; navigation and region persistence agree with the supported window
intent. No new domain model, StateMachine/Workflow record or fallback source
meaning is introduced.

Rendering consumes selected typed declarations directly. Schema and renderer metadata describe production; explicit decision/reviewer attribution describes acceptance. No artifact hash, currentness API or regenerated-artifact equality gate is retained. Writers validate inputs and paths, then write UTF-8 directly; ordinary failures report errors and recovery is regeneration.

One fixed JavaScript interpreter consumes the inert complete WebReceipt.
HTML-safe JSON escaping prevents closing-script or delimiter-bearing text
from entering code; dynamic source display uses textContent/value. Source
attributes and natural native button/input/select ordering preserve exact
source identities, roles, labels, steps, component bindings and global focus
rank. The only local predicates are admitted RequiredTextRule before
AsciiIdentifierRule. No context, server, aggregate or operation condition is
evaluated. Live feedback, pending busy state and field ARIA are target
realization rather than additions to target-neutral IR.

The interpreter holds target UI selection, input, active screen, variant,
outcome, pending/completed flags and local error. The source command starts
the declared loading outcome only after local input validation and declared
order/variant selection. A separate labeled fixture completion selects exact
terminal declaration data, with no timer, network or domain execution.
Recovery controls come only from active source outcome bindings. Input edit
retains domain and focuses the admitted field; invocation retry preserves
selection/input and starts loading; entry retry and return-to-entry use
declared source entry. Reset and replay are review fixture helpers, outside
logical rank and authority, restoring exact source initial data and retaining
invalid initial input as diagnosed review data. Successful record display
retains ID/label/amount; Workflow evidence is visible separately and inert.

Viewport width alone selects compact (<600), medium (600..1023) or expanded
(>=1024). One shared DOM becomes one active compact content screen, persistent
medium navigation with active content, or simultaneously available expanded
regions. Unselected expanded detail controls are disabled. Resize preserves
source input and target selection/outcome state and moves hidden source focus
to an available declared counterpart. minmax/min-width/wrapping/focus CSS
supports opaque content. Structured runtime diagnostics inspect actual
geometry, reachable content, declared actions, feedback, navigation, source
rank and responsive intent; generated markup or a hardcoded PASS cannot
replace later browser inspection. The comparison page uses three differently
sized titled iframes of the same index.html, with local independent interaction
and common complete provenance. Its own horizontal scrolling is intentional.

The current admitted run007 exercised all 21 source coverage pairs and every declared
recovery across compact/medium/expanded, actual local invalid input, normal
loading/completion, duplicate invocation, native keyboard/focus, reset/replay,
state-preserving viewport changes and boundary widths, structural review and
independent comparison frames. Its 1067 observations retain provenance, actual
geometry/availability, ARIA and diagnostics; the parent inspected all 23
images. Twelve separately prepared pointer recoveries begin with actual focused
editable input across the four failure outcomes and three profiles. Five focused
source-recovery resize walks, including empty entry retry, retain actual focus,
connection, availability and semantics through profile transitions and boundary
widths. The historical run006 PASS retains 21 cases, 778 observations and
23 images locally; it omitted those two transitions.
The [CPB-P501-E3-001 repair](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-review-fix-manifest-001.json)
keeps the same recovery nodes/listeners while the exact active outcome reference
is unchanged, rebuilding only on outcome change or clearing. Parent-admitted
focused validation and run007 passed; independent focused re-review passed and
was accepted in review ledger revision 3, resolving CPB-P501-E3-001 as recorded
in the [accepted re-review checkpoint](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-recovery-rereview-checkpoint-001.json).
The latest independent integrity focused review PASS was accepted in review
ledger revision 5, and S50.1.1/E3 was manually committed at
`84ab85a0984a0286507d63aca906c9a88c923f9b` in
[Step commit checkpoint 002](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-step-acceptance-commit-002.json).
The narrow formatting integrity delta removes blank provenance separator spaces
in the current comparison; all 21 producer tests passed. Run007 remains the
interaction proof, predates that formatting change and is not a new browser
observation of the current comparison bytes.
[The portable candidate review](../evidence/phase-50.1/readiness-candidate-001/browser-review.md)
links the regenerated three HTML/two complete receipts, observed data and images,
with relative screenshot references and scope limits. Private session/PID/temp/
endpoint records remain local; portable copies are product-review material.
The sole [specification section 11 matrix](../spec/logical-ui-model.md#11-shared-salesorder-compact-success-contract)
records these 21 browser cases and preserves the historical P50 compact row.
Actual attributed human feedback 「修正不要」 received on 2026-10-13 and its
applicable no-source-change disposition are documented in the
[feedback record](../evidence/phase-50.1/readiness-candidate-001/feedback.md).
The separate actual final acceptance is recorded in section 12 and
[acceptance.json](../evidence/phase-50.1/readiness-accepted-001/acceptance.json).
It authorizes the same complete inputs; no producer or fixture supplies consent.

## October 13 integration with Phase 71.5

The completed Phase 50/50.1 candidate review, readiness admission, explicit acceptance and compact/medium/expanded Web functions remain available. `TargetReadyAcceptanceDecision(decisionId, reviewerId)` is applied to the selected complete candidate passed to `accept`. `CompleteBinding` retains that optional decision; source IR, domain bindings, scenarios, diagnostics and output names remain ordinary production metadata. `CandidateReviewReceipt` contains schema, version and renderer profile. Artifact hashes, currentness comparisons and atomic temporary-file installation are removed without a replacement freshness mechanism. Required declarations, domain joins, null inputs, output containment and ordinary I/O failures remain checked. Historical Phase acceptance records and generated evidence retain their dated implementation meaning.
