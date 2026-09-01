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
