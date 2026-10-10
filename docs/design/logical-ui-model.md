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
