# Logical UI Model Design

Document role: normative design for the Cozy-owned Logical UI authority kernel.
The executable contract is [`docs/spec/logical-ui-model.md`](../spec/logical-ui-model.md).
This design owns the Cozy Logical UI authority kernel and its documented screen
projection, semantic projection, and read-only review extensions. It does not
own target-framework generation or CML/CNCF source semantics.

## 1. Authority boundary

Logical UI v1 is a consumer of a public Component surface and of three explicit
UseCase identities. It never creates a parallel Entity, Aggregate, Operation,
or UseCase model.

```text
identity-pinned public Component surface + Business/System/UI UseCase references
  -> normalized Logical UI candidate
  -> explicit acceptance decision bound to the exact candidate identity
  -> accepted Logical UI authority

raw prompts / codec bytes / review HTML / route tables / target code / caches
  -> non-authoritative projections or transport only
```

The candidate is reviewable semantic input, not accepted authority. An
`AcceptanceDecision` is the sole feedback-decision form in v1 and must name one
exact candidate identity. The accepted value is constructed only when that
binding validates. A candidate identity, canonical consumed-input identity,
feedback-decision identity, and accepted identity have separate semantic roles.

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

## 4. Normalization and identity

Candidate construction first validates the closed public-surface and UseCase
inputs, then sorts Component surfaces by full coordinate, export IDs
lexicographically, bindings by coordinate plus export ID, and realization
relations by source/target layer and identity. The canonical logical content is
UTF-8 JSON in a declared field order. The candidate semantic identity is its
`sha256:<64-lowercase-hex>` digest. Equivalent vector ordering therefore cannot
change the candidate or consumed-input identity.

The candidate document includes an `inputIdentity` derived from the normalized
logical content under the `consumed-input` role. The decision identity derives
from the decision ID and candidate identity under the `acceptance-decision`
role. The accepted identity derives from the candidate, consumed-input, and
decision identities under the `accepted` role. No timestamp, cache, file path,
codec whitespace, HTML, or renderer state participates in any of these hashes.

## 5. Intentional non-goals

The kernel does not expose all Component vocabulary; it merely admits exact
public references for later use. It has no CML grammar, source reader, HTML
renderer, screen composition, projection policy, Widget/Flutter abstraction,
or automatic acceptance. LUI43-02 through LUI43-05 own those later concerns.

## 6. UseCase-to-Screen projection boundary

LUI43-02 introduces `cozy.usecase-screen-projection.v1` as a typed projection
over the committed candidate kernel. `CozyLogicalUi.project` takes a
`LogicalUiCandidate` and a projection input whose candidate identity must equal
that candidate's exact `identity`. The projection retains the candidate as an
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

The normalized projection identity includes the exact candidate identity and
canonical projection content. This identity is deterministic under catalog,
step, screen, interaction, mapping, usage, feedback, region, and boundary
permutations. No renderer, HTML, receipt, route, widget, codec, acceptance,
pattern, constraint, reachability, or target-framework concern is introduced.

## 7. Pattern, constraint, and state semantics

LUI43-03 adds `CozyLogicalUiSemantics` as a distinct package-local semantic
projection. It consumes the exact identity of one committed screen projection
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

Canonical semantic content includes the exact screen-projection identity and
uses structural tuple ordering for all opaque identities. No delimiter-derived
identity key is used. Thus vector permutations cannot alter the semantic
identity, while duplicate, missing, incompatible, or unadmitted values fail
closed with stable `LUI43_SEMANTICS_*` diagnostics.

## 8. Review projection and receipt boundary

`CozyLogicalUiReview` is deliberately below the accepted Logical UI and
semantic authorities. It receives the exact accepted candidate, normalized
screen projection, and semantic projection as an identity-pinned tuple. It is
read-only with respect to those values: the renderer does not create
acceptance, evaluate DbC or validation, run Operations or StateMachine
transitions, infer Workflow state, or select target widgets/routes.

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
omissions visible but do not become acceptance decisions. Receipt identity is
kept out of HTML so a review page cannot be mistaken for its currentness
authority.

The receipt binds all reused authority identities plus the closed renderer
profile and emitted-byte hash under `cozy.logical-ui-review.v1`. Its identity
is a separate deterministic value. UseCase and catalog receipt identities use
local canonical JSON object/array framing with fully JSON-escaped dynamic
strings, preserving injective identity for valid delimiter-, quote-, and
control-character-bearing IDs. Recomputing the same normalized tuple must
produce equal HTML and receipt bytes; changing accepted/projection/semantic
inputs, receipt fields, renderer evidence, or HTML bytes makes currentness fail
closed.

The only filesystem operation is an explicit `write` of the already validated
HTML. It requires an existing non-symlink parent and a regular-or-absent
non-symlink `*.html` target whose parent path is exactly the supplied parent
path as written. The direct-child equality gate rejects `.`/`..` and every
intermediate component, including symlink-plus-`..` traversal, before it
creates a temporary file or replaces output. It stages only an admitted target
in that parent and uses an atomic move. It never stores a receipt beside the
page, reads an HTML page as authority, or publishes/registers an artifact.
Renderer changes therefore remain a non-authoritative review concern and
cannot stale Logical UI semantic authority by mutation.

## 9. Additive candidate preview boundary

`renderCandidate` takes the genuine declared candidate, its normalized screen
projection, and its bound semantic projection. It adds an inspection entry
without calling `accept`, creating a decision, or wrapping a candidate in
synthetic accepted authority. The exact candidate/input identities and canonical
content match the projected candidate, and the semantic projection binds the
exact screen projection identity. Factories retain normalization responsibility.

`LogicalUiCandidateReview` retains its private declared tuple and exposes
immutable HTML, a separate `CandidateReviewReceipt`, diagnostics and UTF-8
bytes. Observable-field copies retain that tuple. The separate receipt has
schema `cozy.logical-ui-candidate-review.v1`, version `1`, renderer profile
`cozy.logical-ui-candidate-review.renderer.v1`, and only the four reused
candidate, consumed-input, projection and semantic identities. Screen and
semantic extensions need their own bindings because candidate identity does
not include those layers. No hash or additional identity is introduced.

The accepted and candidate entries share the structural section builders and
informational diagnostics. The common HTML builder receives the candidate and
an explicit overview. Accepted review passes its existing overview unchanged;
candidate preview shows `Candidate preview`, `candidate (not accepted)`, the
candidate schema/profile and four identities, omitting accepted identity and
decision evidence. Both paths use the established escaping and inline CSS
without execution surfaces or remote resources.

`verifyCandidateCurrent` recomputes all HTML, receipt and diagnostics from the
supplied tuple. `writeCandidate` verifies the retained tuple before sharing the
existing direct-parent validation and atomic UTF-8 HTML writer. Identity and
currentness failures use the distinct `LUI50_CANDIDATE_REVIEW_*` diagnostics;
filesystem output failures retain `LUI43_REVIEW_OUTPUT_INVALID`. The accepted
API, receipt arity, HTML, diagnostics, output behavior and legacy identities
remain compatible. The declared failure model adds no concurrency, retries,
checksums, backup, snapshot, or interference mechanisms.

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

The receipt reuses the four source identities from `CandidateReviewReceipt`
and serializes the complete declaration and normalized public binding set in
fixed order. It adds no accepted identity or new hash. Fixture identity alone
cannot bind changed data or flow. The private-constructor preview retains all
declared inputs through observable-field copies. Currentness regenerates app,
aligned review, complete receipt/JSON and diagnostics and compares every
observable. Scenario null/contract errors are distinct from currentness and
filesystem errors, each with precise paths.

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

The three-file writer first verifies retained currentness, then preflights
both distinct existing canonical absolute parents and every direct destination.
It rejects traversal, all symlink components and directory/non-regular targets
before writing. Each UTF-8 installation uses an owned same-parent temporary
file and atomic move. Rejected inputs or paths preserve prior bytes; an I/O
failure after an individual move may leave a partial installation. This is
per-file atomic installation with temporary cleanup, without group transaction
or rollback semantics. Unrelated files stay untouched.

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

`CozyLogicalUiReadiness` is one package-local target-neutral extension over the
existing kernel candidate, screen projection and semantic projection. Its
complete immutable candidate is private-constructor and consumes the exact
factory-produced source chain plus typed readiness metadata. Existing pure
factories are reused for source normalization; exact chain identities and
canonical kernel content are checked before traversing readiness metadata.
No renderer dependency, second domain model, codec change or runtime schema
field is introduced. The paired contract is
[specification section 13](../spec/logical-ui-model.md#13-complete-target-ready-logical-ui-contract).

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

One small fixed-order Play JSON serializer writes the full normalized input
and all source identities. Structural tuple sorting avoids delimiter-derived
keys; JSON escaping protects opaque strings while preserving their value.
Product hash roles distinguish complete candidate and consumed input. Exact
attributed acceptance adds decision and accepted identities; private accepted
construction requires a valid decision naming that complete candidate.
`verifyCurrent` recomposes authoritative input and compares complete identity,
content and consumed-input role. Source or metadata changes stale the old
value under an unchanged legacy kernel; permutation alone remains current.
Management certificates and target output bytes are outside logical content.

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

Readiness executable specifications pair Given/When/Then with observable
admission, precise structured failures, independent input permutation PBT,
opaque JSON framing, source/metadata currentness and explicit attributed
fixture decisions. The legacy accumulator remains the compatibility proof.
No fixture decision authenticates human acceptance. At the IR slice,
structural/Web projection receipts and actual browser evidence belonged to
later slices; their current E3 candidate proof is recorded in section 13 and
the sole specification section 11 matrix. Independent E3 Step review is accepted,
with the latest integrity focused review PASS accepted in review ledger revision 5.
S50.1.1/E3 was manually committed at
`84ab85a0984a0286507d63aca906c9a88c923f9b`; the
[Step commit checkpoint 002](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-step-acceptance-commit-002.json)
records that status. Actual attributed human feedback 「修正不要」 was received
on 2026-10-13; the [feedback record](../evidence/phase-50.1/readiness-candidate-001/feedback.md)
documents the separate no-source-change disposition. On 2026-10-13, the human
user of owning conversation `01a0ff01-4772-76b2-bba1-e2542f0a9647` separately said:

> この候補を最終受入れする。Phase 50.1を最後まで続けて。コミットは引き続き親による手動ローカルコミットで。

[The actual acceptance input](../evidence/phase-50.1/readiness-accepted-001/acceptance.json)
binds complete candidate `sha256:dcb64b218d5921c65be0f1be7b1483565446dfd9fc3dbefd215c202e83f813b1`
and consumed input `sha256:dd1f3b249795e3857bdcb19a72abc2cc205edee8e4d736f8f2db42a2dd08c0a1`.
No semantic source revision or personal reviewer name is added. Candidate
receipts remain null; [the accepted package](../evidence/phase-50.1/readiness-accepted-001/README.md)
retains the generated native accepted projection/export. Both producer suites
passed 21 tests, and accepted-binding run002 passed six normal/loading cases,
107 observations and eight parent-inspected images; parent native/browser
admission passed with cleanup complete. Whole E4 Step review/manual local
commit, full Phase review, final full suite and distinct release/closure remain
pending. Neither the historical IR slice nor browser success closes these gates.

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

The structural page inserts complete authority and readable metadata tables
ahead of a separately identified kernel candidate overview from the unchanged
Review producer. It retains all source diagnostics and read-only source
sections. Escaped text/JSON and preserved display whitespace carry opaque
source values without executable markup. ReviewReceipt's fixed-order JSON
is embedded whole in WebReceipt, with simulated=true and five exact output
names. Candidate optional acceptance fields are consistently null; accepted
mode carries actual complete attribution. Both private previews retain
original authority/mode/scenario through observable-only copies. Currentness
always recomposes supplied complete values and compares all output strings,
typed receipts and diagnostics; declaration reordering alone is equivalent.

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

The writer recomposes retained authority before output preflight. Both
existing absolute canonical parents and all five direct destinations are
checked for traversal, symlinks and non-regular target kinds before any write.
All UTF-8 sibling temporaries are staged first; each file is installed with
atomic replacement. IOException/SecurityException become structured output
failures; owned temporaries alone are cleaned. Atomicity is per file, without
rollback, cross-file transaction or non-atomic fallback. Unrelated files are
preserved. One executable scenario retains the production candidate bundle
under target/phase501-readiness-preview for actual browser observation; other
writer scenarios create and clean only their fresh owned target fixtures.

Independent ScalaCheck permutations exercise complete source/readiness and
coverage ordering, while behavior scenarios cover exact receipt content,
every typed profile/outcome and recovery, complete attribution/currentness,
opaque safe data and all five writer targets. These specifications provide
typed projection/generation contracts. The paired actual browser specification
is [run-readiness-browser.mjs](../../scripts/test/phase501/run-readiness-browser.mjs),
consuming the full production receipt through five absolute path flags.
It uses a supervised exclusive output directory, one GET-only loopback server
for the three HTML resources and a fresh owned Chrome profile, with awaited
startup/interruption cleanup and terminal evidence. Browser observations stay
outside Logical UI authority.

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

[RenderAcceptedReadiness.scala](../../scripts/test/phase501/RenderAcceptedReadiness.scala)
is a thin Test-classpath adapter, separate from the Cozy CLI and semantic codec.
It reads the actual decision, recomposes the single ready candidate/scenario,
guards exact candidate/consumed identities before mutation and calls native
currentness, acceptance and both accepted render/currentness factories. Both
complete bindings/declarations and structural receipts must agree; deterministic
Web rerender is checked before the unchanged native five-file writer runs.
The structural receipt's existing `binding` and `sourceInputs` are exported as
the only top-level fields in `accepted-logical-ui.json`, supplying Phase 51's
approved four-role declarations. Native accepted/decision identities remain
factory-owned. Single-writer local generation adds no hashes/locks/retries/
rollback/certificate or acceptance acquisition.

The same browser driver accepts an optional sixth absolute `--accepted-input`
pair for that canonical regular export outside site/output. Its existing five
pairs retain candidate/null/full-21 behavior. Accepted-binding compares exact
receipt/export binding and four declarations, frozen six source identities,
distinct candidate/consumed/decision/accepted roles and trimmed attribution.
The walk uses existing DOM/reset/navigation/normal helpers for six normal/loading
cases at the three declared viewports. Visible Complete source provenance is
opened through its summary and recorded with text, geometry and parsed native
accepted/decision/reviewer values, without document overflow. Structural
accepted labeling and independent comparison frames retain complete attribution
and normal flow. Six case images plus structural/completed-frame comparison
images and explicit `proofMode`/`proofScope` describe bounded coverage; unchanged
failure/recovery/reset/replay/resize reuse E3 run007. The GET-only owned
server/Chrome/profile/startup/interruption/cleanup lifecycle is unchanged.

The [accepted package](../evidence/phase-50.1/readiness-accepted-001/README.md)
retains actual generated artifacts and bounded accepted-binding run002 proof:
six normal/loading cases, 107 observations and eight parent-inspected images.
Native generation/currentness/determinism and both producer suites (21 tests)
passed; parent native/browser admission and owned cleanup are complete. Whole E4
Step review/manual local commit, full Phase review,
final full suite and distinct release/closure remain pending.
