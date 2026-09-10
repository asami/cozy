# Document Project Design

## Status and authority

This document records the stable design intent for the Document Project v2
authoring boundary in Phase 45 and the native execution / accepted-evidence boundary in Phases 56 and 56.1.
It is design, not an executable implementation or a replacement for the Phase
checklists, which remain the progress ledger.
The normative behavior contract is [Document Project
Specification](../spec/document-project.md).

This design promotes only the frozen boundary identified by the non-normative
planning inputs: the [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md).

## Operational envelope and semantic authority

Document Project is the operational envelope for a coherent document-production
effort. Its authored v2 descriptor selects a reusable workflow, one registered
workflow profile, language, workspace kind, Content Core reference, optional
Work Products, and semantic identity hooks; the
workflow owns the profile's Work Products, deliverable dispositions, criteria,
gates, operations, and provider bindings.  It is not a new semantic source for
any medium.

Content Core is the separately authored shared semantic authority.  It carries
the meaning that must stay aligned across representations, while each medium
retains its own expression authority.  In particular, this design preserves:

- SmartDox article source as article-expression authority;
- editable infographic SVG as infographic-expression authority;
- Visual Page and Slide sources as presentation-expression authority;
- Storyboard and video sources as audiovisual-expression authority; and
- existing receipts and review-state documents as the authorities established
  by their own contracts.

The [Media Package specification](../spec/media-package.md), [Media Package
operation design](media-package-operation.md), [Visual Page specification](../spec/visual-page.md),
and [Visual Page design](visual-page.md) remain read-only retained authorities.
Document Project may bind or reference their results, but does not reinterpret,
replace, migrate, or expand them.

## Workflow model

The model deliberately separates reusable definition, project view, and
historical evidence:

- A **Workflow Definition** is the reusable logical-operation DAG with its
  roles, criteria, dependencies, and gates.  A project resolves it by identity;
  it never copies the DAG into a private editable descriptor.
- A **Workflow Instance Snapshot** is a derived, non-authoritative view for one
  Document Project.  It is reconstructed from authored authorities and accepted
  evidence, and may be cached only as a disposable projection.
- An **Operation Attempt** is append-only evidence of one attempt to perform a
  declared logical operation.  It is historical evidence, not mutable project
  status and not an acceptance decision by itself.

Work Products make the edges of that model visible.  Their closed role
vocabulary is `authority`, `plan`, `candidate`, `review-projection`,
`site-deliverable`, `deliverable`, and `receipt`. Static disposition remains
`required`, `optional`, or `disabled`; effective selection is instead exactly
`required`, `active-optional`, `inactive-optional`, or `profile-disabled`.
Inactive optional and profile-disabled Work Products are visibly
nonparticipating and never completion.

## Phase 45 v2 authoring boundary

The v2 descriptor has exactly `schema`, `id`, `workflow`, `profile`,
`language`, `workspace`, `contentCore`, `activeOptionalWorkProducts`, and
`semanticScope`. The optional selection names only statically optional Work
Products in the selected profile. Resolution always keeps the immutable
workflow order: required products participate, explicitly selected optional
products participate, unselected optional products are nonparticipating, and
profile-disabled products remain disabled. This same resolved selection drives
plan rows, operation admission, evidence order, state, and projection
capability diagnostics.

`semanticScope` is an identity-only locally authored catalog of locale variants.
It does not cause directory discovery, another repository lookup, locale
synchronization, currentness inference, or human alignment recording. The self
variant is present exactly once. This makes future identity relationships
explicit without making them operational in Phase 45.

`article-review-html` is a selectable `review-projection` Work Product with
the `article.render-review` logical operation, review criterion, gate, and
evidence reference. It depends on Content Core, article source, and Visual Page
IR. P45-03 admits a selected `review --kind article` projection at
`target/document-project/article-review.html`; it is deterministic,
self-contained, HTML-escaped, and read-only. It shows article
structure/narrative, accepted Content Core correspondence, one ordered semantic
row per authored Visual Page, declared terminology and media placement (or a
clear no-declaration result), the infographic relationship, and direct current
input identities. Its Visual Page review is a reader-facing page model, not a
source-field table: each page keeps its declared `id` or a deterministic ordinal
fallback, title, reader text, visual/relationship summary, bounded article
structure summary, page count/current-page state, and self-contained keyboard
navigation. The v2 descriptor has no accepted Phase-41 Projection selector;
the review reports that absence and retains Visual Page only as semantic input.
It does not receive Phase-41 page-flow evidence, invoke a renderer or provider,
or alter authored sources. P45-04 retains exclusive ownership of dashboard
redesign and action selection.

P45-04's Dashboard is deliberately user-first. Its primary surface projects
current production stage, latest observed change, prioritized blockers, pending
review, current deliverables, recommended next action, eligible safe actions,
and optional deliverable selection with paired English/Japanese headings. The
surface is derived only from the current snapshot and immutable Workflow order:
required and active-optional products participate, while inactive optional and
profile-disabled products remain nonparticipating. A first blocked/failed
participating product determines the stage and recommendation; otherwise the
stage is a current/ready summary. Latest change prefers the first stale
participating product, then the first missing one, preserving the exact stored
reason and never inventing time or history.

The recommended and eligible actions are displays of existing contracts only.
Review products use their existing `review --kind` command, Content Core start
uses the explicit `content-core candidate <project> <dialogue>` form, and all
other products use the existing `run --operation <id> --dry-run` preview. The
required `content-core` blocker recommends that Content Core candidate-start
form even when the optional `content-core-candidate` Work Product is inactive.
The generic `content-core.compose` operation is not displayed in the primary action
surface or its recommended and eligible safe-action previews. This is an
action-surface-only prohibition: secondary Work Product details retain each
canonical producer operation, including `content-core.compose` for
`content-core-candidate`. These are selected-by-contract previews, not
runtime-readiness proof, and the Dashboard does not execute them. Optional
selection explains the closed descriptor `activeOptionalWorkProducts`
authoring contract; generation does not write the descriptor.

Internal Workflow, provider, gate, criterion, receipt, attempt, hash, and
responsibility material remains available under one secondary-diagnostics
`details` surface. This preserves the diagnostic graph without making it the
ordinary user's first task. The projection remains deterministic,
self-contained, escaped, read-only, provider-neutral, and outside command
schema, descriptor/evidence schema, runtime execution, and external delivery.

For default Article and Video review output only, P45-03 writes a deterministic
local generated-review receipt beside the default HTML under
`target/document-project/`. It binds the fixed review kind, descriptor id and
profile, default-output SHA-256, and fixed-order direct input identities used by
that review. This disposable receipt is neither authored authority nor a
renderer, provider, production, or external-media receipt; it does not modify
the evidence-sidecar schema. An explicit `--save` writes only the requested
HTML and never replaces that default local receipt. State derives
`missing`/`current`/`stale` for `article-review-html` and `video-review` from
the receipt and fixed default output; changed or missing receipt inputs or
default output are stale, while human review remains pending.

V2 replaces the earlier authored descriptor, workflow, evidence, and state
contract identities. There is no reader, migration, legacy evidence/state
fallback, or compatibility branch for the retired contract.

## Historical Phase 42.1 evidence and attempt boundary

This design records the Phase 42.1 evidence boundary.  DP42-03B implements the
disposable snapshot cache; DP42-03C implements recorded single-operation
dispatch and append-only attempt persistence; DP42-03D binds optional retained
evidence to the existing project without reopening the descriptor kernel.

The `cozy.document-project-state.v2` Workflow Instance Snapshot is disposable
derived YAML at `<project>/target/document-project/state.yaml`.  It is never
authored authority and cannot be edited as a state override.  Removing
`<project>/target/document-project` and inspecting unchanged admitted project
inputs reconstructs the identical snapshot.

Successful `inspect` and `verify` regenerate the cache only after their
descriptor, Core, and command-specific source validation succeeds.  Its
canonical UTF-8 YAML keys are ordered `schema`, `project`, `profile`,
`workspace`, `sources`, `evidence`, `criteria`, and `workProducts`.  The fixed-order `sources` entries
contain only project-relative direct authored paths and lowercase hexadecimal
SHA-256 content identities: descriptor, exact Core, `index.dox`, infographic
SVG, Visual Page source, review README, and directly present storyboard for a
video profile.  No absolute path, timestamp, random value, discovery order,
generated output or receipt content is serialized.  `evidence` separately
records only optional sidecar and retained-attempt project-relative path and
SHA-256 identities; it is not authority.

The fixed-order Work Product projection contains `id`, `role`, `disposition`,
`selection`, `criterion`, `coverage`, `currentness`, `review`, and `readiness`. It uses only
the closed status vocabularies.  Existing source assets may be
`satisfied`/`current`; absent output or receipt-dependent evidence remains
`missing`/`blocked`/`pending`.  Standard's disabled video products explicitly
use `coverage: not-applicable`, `readiness: omitted`, and the profile reason
`profile standard disables video branch`, never completion. Historically,
DP42-03C implemented immutable append-only attempts and recorded dispatch
only. DP42-03D
adds the optional sidecar evidence that derives retained receipt currentness
and dependency-driven `stale` state without changing media receipt contracts.

The intervening `criteria` projection is immutable evidence-derived state in
Workflow Definition criterion order.  It records scalar `satisfied` and
`total` counts, then ordered `missing` and `notApplicable` criterion lists
with each criterion id and its derived reason.  It is neither a writable
percentage nor an acceptance authority.  Dashboard Criterion coverage presents
the same snapshot as `<satisfied>/<total> applicable criteria satisfied` with
one accessible criterion, coverage, and reason table.

A `cozy.document-operation-attempt.v1` Operation Attempt is durable,
append-only evidence at `<project>/evidence/attempts/<attempt-id>.yaml`; it is
never scaffolded.  Existing attempt bytes are never overwritten, so failures
and superseded attempts remain inspectable.  Its existence alone does not make
a Work Product current, complete, reviewed, or accepted.  Canonical UTF-8
attempt YAML has exactly the ordered top-level keys `schema`, `id`,
`operation`, `provider`, `profile`, `inputs`, `outcome`, `diagnostics`,
`outputs`, and `receipt`.  It records the newly generated stable id, declared
operation, frozen provider binding, selected profile, fixed-order direct
project-relative source identities with lowercase SHA-256 values,
`outcome: recorded`, one diagnostic stating that provider execution is
deferred and the dispatch was recorded only, `outputs: []`, and `receipt:
none`. The dispatch invokes no provider, creates no output or receipt, writes
no Core or state cache, and infers no downstream operation. A completed
dialogue remains provenance until the distinct human acceptance record replaces
the Content Core.

Historically, DP42-03C created `evidence/attempts` only for an eligible normal
run; the
directories and files must be direct, non-symlinked project entries.  Each
attempt is written to a same-directory temporary file and published with
`ATOMIC_MOVE` without replacement.  Collisions or publication failures leave
existing bytes untouched, remove the temporary file, and return a stable
path/evidence diagnostic.  `--dry-run` performs the same admission without
creating evidence or a cache.

The snapshot independently derives coverage (`satisfied`, `missing`, and
`not-applicable` criteria), currentness (`missing`, `current`, `stale`,
`failed`, `nonparticipating`, or `not-applicable`) from declared identities
rather than timestamps, review (`pending`, `accepted`, `rejected`, `stale`, or
`not-applicable`), and readiness (`blocked`, `ready`, `running`, `succeeded`,
`failed`, `not-selected`, or `omitted`). `omitted` is visible with its
declared profile reason and is not completion.

DP42-03D adds a portable sidecar at
`<project>/evidence/document-project.yaml`, never scaffolded and always direct
and non-symlinked.  Its closed schema is
`cozy.document-project-evidence.v2`, with ordered `schema`, `project`,
`publicSource`, and `products` keys.  It binds the admitted descriptor id to a
safe SmartDox source mapping and a fixed, selected-Work-Product-order evidence
list.  It does not add a descriptor field, command grammar, workflow feature,
or media schema.

The safe source mapping is deliberately narrow: explicit SmartDox identity,
`index.dox`, its current SHA-256, and one direct project-local media descriptor
whose explicit `articleMedia.articleIdentity` agrees.  It intentionally does
not discover a host, infer a filename identity, register a site, or surface
Content Core, raw media, review/receipt content, or targets.  Product evidence
is a closed `none`, source/artifact path-and-hash, media-receipt, or
operation-receipt-set alternative.  Source evidence is declared authored
authority only; artifact evidence is direct project-contained output only;
receipt currentness delegates without modification to the established Cozy
Media receipt logic.

The optional Document Project evidence sidecar carries no Content Core dialogue
or acceptance alternative.  It uses only `review: {kind: none}`.  Content Core
dialogue, feedback, and human acceptance are separate append-only records below
`evidence/content-core/`, so the sidecar cannot become an AI/provider or human
decision authority.

One shared v2 state model derives cache and dashboard rows from the sidecar
when present, otherwise using source-derived missing/pending behavior without a
legacy schema or fallback.
It independently derives coverage, currentness, review, readiness, and exact
reason.  Hash or current-receipt mismatches are stale while underlying output
exists; absence is missing; a valid retained failed attempt is failed only
without current product evidence.  Declared dependency edges propagate stale
status to every consumer, including shared infographic consumers, without any
timestamp ordering.  Attempts remain historical and never manufacture a
success result.

Reconstruction uses the closed `document-production` Work Product definition,
descriptor and Core identities, retained source/receipt/review/attempt evidence,
and declared producer/consumer/dependency identities.  It does not infer state
from filenames or timestamps.  A changed dependency makes every declared
consumer stale; a changed shared infographic therefore stales its declared
article, slides, and video consumers.

`inspect` and `verify` remain non-authoritative: they may write only that
disposable snapshot cache, never an attempt, receipt, acceptance, authored
source, registry, workspace integration, aggregate build, publication,
deployment, upload, or downstream operation. The historical Phase 42.1 `run`
dispatch created one append-only attempt for a selected operation, while its
`--dry-run` reported the same selected operation, provider, and profile without
persistence. It did not infer downstream execution. Phase 56 supersedes that
record-only normal-run behavior below. This historical boundary
does not alter the closed descriptor fields, common workflow DAG, profiles,
Work Product roles, criteria, gates, operation IDs, provider bindings, retained
media/SmartDox/Visual Page/Phase-41 authorities, or public command grammar.
It has no retired-contract compatibility behavior.

## Closed DP42-02 workflow definition

DP42-02 closes the initial reusable `document-production` definition in code.
It is immutable, resolves public `standard`, `standard-video`, `bok`, and
`bok-video`, plus hidden `simplemodeling-org` and `simplemodeling-org-video`, and is not
serialized into, copied by, or editable through a Document Project descriptor.
The definition validates itself before a plan projection or operation-admission
lookup: identifiers are unique; producer, consumer, criterion, dependency,
gate, evidence-reference, and provider-binding references are closed; product
dependencies are acyclic; required metadata is non-empty; and every profile
binds only the closed Work Product set.

The definition's stable Work Products are:

| Work Product id | Role | no-video | video |
| --- | --- | --- | --- |
| `content-core-candidate` | candidate | optional | optional |
| `content-core` | authority | required | required |
| `core-review-html` | review-projection | optional | optional |
| `presentation-semantics` | authority | required | required |
| `article-source` | authority | required | required |
| `article-html` | site-deliverable | required | required |
| `article-review-html` | review-projection | optional | optional |
| `article-pdf` | deliverable | required | required |
| `visual-pages` | authority | required | required |
| `slide-review-html` | review-projection | optional | optional |
| `summary-slides-pdf` | deliverable | optional | optional |
| `infographic-svg` | authority | required | required |
| `infographic-png` | deliverable | optional | optional |
| `video-storyboard`, `video-review`, `video-deliverable` | plan/review/deliverable | disabled with profile reason | required |
| `explanation-structure-review-html` | review-projection | optional | optional |
| `video-logical-chart-html` | review-projection | disabled with profile reason | optional |
| `presentation-confirmation-html` | review-projection | optional | optional |
| `operation-receipt-evidence` | receipt | optional | optional |

`required`, `optional`, and `disabled` are visibly different dispositions.
No-video profiles expose their selected `profile <id> disables video branch`
reason; video profiles activate the reusable branch without a disabled reason.
`bok` and `simplemodeling-org` share the current no-video matrix, and their
`-video` variants share the current video matrix. `simplemodeling-org` is
registered for descriptor resolution but intentionally absent from public
scaffold/help selection. This profile concern is independent of `workspace`,
which selects operating context such as `directory` or `bok`.

`article-html` is a required `site-deliverable`: Cozy Site generates it from
the SmartDox article source. It is outside the ordinary PDF, slide,
infographic, and video deliverable grouping.

The required editable `infographic-svg` is itself the final infographic
artifact and review target. It does not have a parallel review HTML.

Each Work Product declares its producer and consumer logical operations, its
criteria, Work Product dependencies, gate, and evidence reference.  Each
logical operation has a stable id and one static provider binding.  The initial
bindings are `content-core.compose`/`content-core.review`, article compose,
Site publication, article review, and PDF render operations, `summary-slides.render-pdf`, infographic compose and PNG
render operations, the three video operations, slide and video logical-chart
review operations, and `operation-receipt.record`. Their
providers identify the fixed Cozy, SmartDox, Visual Page, infographic, video,
Phase-41 Explanation Structure Review, or receipt adapter responsibility; they
do not discover a provider or execute an adapter in DP42-02.  Criteria, gates,
and evidence references are static identity links, not completion, currentness,
review, receipt, or lifecycle fields.

### Phase 49 presentation-semantics authority

Phase 49 extends the closed code-owned definition without adding a descriptor
field or a second semantic model. `presentation-semantics` is required for all
six registered profiles. Its stable producer is `presentation.author`, bound
only to `cozy-presentation-semantics`; it consumes and directly depends on
`content-core`, with criterion `presentation-semantics-validated`, gate
`presentation-semantics-validation`, and evidence reference
`presentation-semantics-reference`.

The authority is consumed directly by `article.compose`,
`visual-pages.author`, `video.compose-storyboard`,
`presentation.render-confirmation`, and `operation-receipt.record`.
`article-source`, `visual-pages`, and `video-storyboard` therefore directly
depend on it. Workflow order records the authority after `content-core` and
before these semantic products and `presentation-confirmation-html`; it is an
immutable Workflow Definition edge, never a descriptor DAG.

`presentation-confirmation-html` is separately optional for all six profiles.
Its producer is `presentation.render-confirmation`, bound only to
`cozy-presentation-confirmation`; it consumes and directly depends only on
`presentation-semantics`, with criterion
`presentation-confirmation-rendered`, gate `presentation-confirmation`, and
evidence reference `presentation-confirmation-reference`. The separate
`operation-receipt.record` declaration consumes both presentation products,
but this is not receipt creation or semantic success.

The closed semantic-authority state vocabulary is `missing`,
`authoring-incomplete`, `invalid`, `current`, and `stale`. Missing means that
the direct authoring surface is absent. Authoring-incomplete means the direct
scaffolded source cannot pass the existing strict Phase-46 validator; invalid
means an authored candidate reached strict loading and failed `DP-SEM-*`.
Current means strict validation produced the accepted typed semantic identity
for current direct Core bytes. Stale means an earlier accepted semantic or
confirmation identity no longer agrees with bound Core or semantic inputs.
The first three states never create an accepted semantic identity, coverage
success, or confirmation receipt. No receipt can substitute for strict
validation or coverage.

Article review remains an article-expression projection produced by
`article.render-review`. It is not an alias, predecessor, or alternate output
for the shared Story Flow, Explanation Structure, and article/slide/video
mapping confirmation represented by `presentation-confirmation-html`.

Phase 49 freezes this static contract only. Phase 49.1 owns strict loading and
the `verify`, `inspect`, `plan`, and Dashboard state surfaces; Phase 49.2 owns
the parser route, Phase-46.1 adapter, confirmation publication, and receipt;
Phase 49.3 owns stale propagation and Article-9-shaped operational acceptance.
No renderer, receipt writer, CLI parser/help, dashboard, confirmation output,
or semantic/descriptor schema behavior changes in Phase 49.

`plan` resolves this definition read-only. It emits deterministic `required`,
`active-optional`, `inactive-optional`, and `profile-disabled` Work Product
lines, plus `blocked` and `eligible` logical-operation lines. `eligible` means
that an operation produces a selected Work Product, not that it is runtime-ready.
The command creates no
target, dashboard, state, attempt, receipt, registry, delivery, or output file.

Before Phase 56, `run` validated the descriptor, Core, and command-admitted
initial sources, then resolved its declared operation before applying
reserved-form and selected Work Product admission. An unreserved eligible run
recorded one attempt without invoking its provider or creating output, receipt,
Core write-back, state cache, or downstream work. Its `--dry-run` reported the
selected operation, provider, and profile without creating evidence. This
historical behavior is superseded by the native result boundary below. An
unknown, inactive-optional, or profile-disabled operation rejects with
`DP-OP-001`; malformed or unsafe input retains its earlier diagnostic
precedence and neither rejection creates evidence.

`presentation.render-confirmation` remains a declared workflow identity, but
generic `run` is not an alternate confirmation route. Once its declaration is
resolved, generic dry-run and recording requests reject with `DP-OP-001`
before optional-product participation, provider or attempt handling, output,
receipt, or state behavior. The stable diagnostic reserves it for
`document-project review --kind presentation`.

## Initial authored kernel

The initial Document Project package has one authored descriptor,
`document-project.yaml`, and one separately authored Content Core at
`content/core-<language>.yaml`.  The descriptor is deliberately a small,
closed selection boundary.  Its only selection inputs are identity, workflow,
profile, language, workspace, Content Core reference,
`activeOptionalWorkProducts`, and `semanticScope`.  It does not embed a private
workflow graph, artifact-local source, delivery declaration, provider binding,
or mutable lifecycle state.

The descriptor's `workflow` field is an identity reference to the reusable
`document-production` Workflow Definition.  Its `profile` field selects one
closed registered binding within that Workflow Definition: `standard`,
`standard-video`, `bok`, `bok-video`, `simplemodeling-org`, or
`simplemodeling-org-video`.  The `simplemodeling-org` forms are hidden from
public scaffold/help selection; `standard` and `bok` are no-video profiles,
while `standard-video` and `bok-video` are video profiles.  DP42-02 closes and validates the workflow-owned
profile-to-Work-Product, deliverable-disposition, criteria/gate, operation,
and provider-binding model; it MUST NOT add a field outside the closed
`cozy.document-project.v2` descriptor. The `workspace` field chooses only
`directory` or `bok`; the latter is a kind selection, not hosting discovery,
registration, source projection, or an external action.  A relative Core path
keeps the Core inside the package without making the descriptor a source of
semantic meaning.

Content Core is intentionally minimal at this boundary.  Its closed grammar
binds a project and language and carries its accepted shared semantic
statements as one ordered `accepted` sequence.  Each entry has only its stable
`id` and non-empty trimmed UTF-8 `text`; entry identities are unique inside the
Core.  Thus the Core is an actual minimal semantic authority rather than an
identity-only placeholder.  Only an explicitly accepted human decision may add
or replace an accepted entry.  Raw AI dialogue remains provenance outside the
Core. The Core contains neither status nor renderer/delivery/provider fields.
The P45-02 evidence boundary retains provider, model, source, idea, request,
response, candidate, feedback, and acceptance identities without making a
provider invocation, mutable Core status, or receipt. Acceptance uses an
evidence-first recoverable visibility protocol: immutable evidence is durable
before the only Core move; its declared direct Core prior/result hashes classify
the record as pending, applied, or historical; and only the same human decision
can resume pending evidence without adding another record. It adds no schema,
version, provider, remote workflow, or compatibility branch. Structured
semantic vocabulary and automation remain out of scope.

## Command and scaffold boundary

`cozy document-project` has a distinct public namespace, deliberately
separate from the existing software Project knowledge-package commands and
`cozy media`.  Its project operands name existing `*.dox/` package directories,
not arbitrary descriptor files.  `inspect`, `plan`, and `verify` are derived
inspections; successful `inspect` and `verify` regenerate only the disposable
state cache specified above.  Phase 42.1 implements
`dashboard <project> [--save <dashboard.html>]` and
`review <project> --kind core|article|slides|video|slide-logical-chart|video-logical-chart [--save <review.html>]` as deterministic,
self-contained, read-only HTML projections.  The dashboard default is
`target/document-project/project-dashboard.html`; review defaults are
`target/document-project/core-review.html`,
`target/document-project/article-review.html`,
`target/document-project/slides-review.html`,
`target/document-project/video-review.html`,
`target/document-project/slide-logical-chart-review.html`, and
`target/document-project/video-logical-chart-review.html`. An explicit `--save` path is
used exactly as requested when external or valid under the project-local
projection boundary, and review exposes no logical-operation ID.  The
Core review, Article review, Slide review, Video review, Slide Logical Chart, and Video Logical
Chart are separate purpose-oriented projections. Article review is available
only for selected `article-review-html`; it semantically projects article
structure/narrative, accepted Content Core correspondence, Visual Page flow,
declared terminology/media placement, the infographic relationship, and direct
current input identities without
becoming site HTML, a dashboard, or raw Dox/YAML presentation. The slide chart projects Core
and Visual Page IR; the video chart additionally projects storyboard IR and is
available only for a video profile. These
commands do not execute providers, persist candidate/feedback/acceptance or
production-receipt state, or modify authored authorities. Default Article and
Video review additionally persist only their disposable local generated-review
receipt; explicit `--save` does not replace it. The historical Phase-42
`DP-PHASE-001` dashboard rejection is retained only as compatibility history
and is not emitted by Phase 42.1.

The exact public forms are:

```text
cozy document-project dashboard <project> [--save <dashboard.html>]
cozy document-project review <project> --kind core|article|slides|video|slide-logical-chart|video-logical-chart [--save <review.html>]
cozy document-project review <project> --kind presentation [--save <confirmation.html>]
cozy document-project content-core candidate <project> <dialogue>
cozy document-project content-core feedback <project> <candidate-id> <feedback>
cozy document-project content-core accept <project> <candidate-id> <acceptance>
```

The sole confirmation kind is `presentation`; there is no alias or alternative
`run` form. It is admitted through the live parser and help as
`document-project review <project> --kind presentation [--save <confirmation.html>]`.
The route is available only while the optional
`presentation-confirmation-html` Work Product participates in the resolved
workflow through its declared `presentation.render-confirmation` producer;
unselected or profile-disabled participation rejects with `DP-OP-001` before
semantics, projection, output, or receipt behavior. It then requires current
typed Presentation Semantics, projects and renders through the fixed cross-media
adapter, and verifies coverage before it publishes.
Without `--save`, its exact destinations are
`target/document-project/presentation-confirmation.html` and
`target/document-project/presentation-confirmation.receipt.yaml`, published
atomically through the existing safe output behavior with the canonical typed
receipt. With `--save`, it publishes only the requested confirmation HTML and
does not create or replace the default receipt. Strict-currentness or coverage
failure writes neither file. This remains distinct from Article review and does
not invoke Article or Video generated-review receipt behavior.

Dashboard defaults to `target/document-project/project-dashboard.html`; Core,
Article, slide, video, Slide Logical Chart, and Video Logical Chart review default to
`target/document-project/core-review.html`,
`target/document-project/article-review.html`,
`target/document-project/slides-review.html`,
`target/document-project/video-review.html`, and
`target/document-project/slide-logical-chart-review.html` and
`target/document-project/video-logical-chart-review.html`. Each selected output is
published through direct non-symlink parent admission and a same-directory
atomic temporary-file move, with no direct-write fallback.
`run` dispatches one declared operation; and `scaffold` is the only command
that creates authored sources.  No compatibility alias or ambiguous dispatch
is permitted in this kernel.

Canonical package source traversal is direct and non-symlinked.  An admitted
package is itself a direct non-symlink directory, and its descriptor, `content/`
directory, Core, and initial authored sources are direct entries contained in
that package.  A lexical Core path is resolved only from that admitted package
and cannot escape it through a symlink-based path.  This is a contract boundary
for package admission, not a filesystem implementation prescription.

Scaffold offers `standard`, `standard-video`, `bok`, and `bok-video`; the two
video forms add only `video/storyboard.md`. `simplemodeling-org` forms remain
hidden but descriptor-resolvable. It preserves
the regular `index.dox` article-expression authority and does not perform
SmartDox host discovery or source projection.  Creation is all-or-nothing: a
new package is moved atomically into an existing real parent directory only
when the destination and its relevant ancestry are safe, absent, and
non-symlinked.  It neither merges nor overwrites, and it does not substitute a
copy fallback for the atomic move.

The created skeleton contains no generated `target/`, state, receipt,
approval, Operation Attempt, dashboard, registry, publication, deployment,
upload, or fake completed deliverable.  Those are evidence or delivery
concerns, never scaffolded authority.

## Projections and delivery boundaries

Phase 42.1 dashboard and review HTML are generated, read-only projections.
The dashboard consumes the same evidence-derived model as the disposable state
cache without creating that cache.  It presents each provider, gate, derived
status, reason, and next action, keeps retained attempts distinct from current
state, and renders a safe public-source mapping only when the optional sidecar
binds one.  It visibly separates Project production from workspace integration,
aggregate build, and external delivery as read-only, non-invoked
responsibilities.  Its self-contained escaped HTML is never a route to host
discovery, registry/site registration, aggregate execution, publication,
deployment, upload, remote calls, or Article 8 behavior outside the Phase 45.2
private alignment and read-only source-projection boundary.
Dashboard content has accessible Workflow, Work Product matrix, Criterion
coverage, and Work Product details tables covering dispositions, providers, gates, coverage,
currentness, review, readiness, dependencies, producer/consumer operations,
evidence references, and a user-facing next action. It is the place to see the
current Project state and the next required or useful action without needing to
interpret a logical-operation identifier. It distinguishes the current
snapshot from retained attempts. Historical v1 records have no receipt or
currentness authority; a valid accepted native v2 record derives Article review
currentness from its exact retained identities. Core review presents accepted Core entries and marks
candidate/feedback/acceptance as non-authoritative and not yet persisted.
Review-projection Work Products remain `missing`/`missing`/`blocked` until the
corresponding default review HTML exists; if any declared source prerequisite
is absent, the reason is exactly `source or retained evidence is not present`,
and if all declared source prerequisites exist, the reason is exactly
`default review HTML is not generated`. Article and Video review require their
local generated-review receipt as well: a missing receipt is missing and a
changed or missing default output/input is stale. Each Logical Chart instead
creates no receipt and requires none. Its fixed default HTML is current only when a
regular, non-symlink output's UTF-8 bytes exactly equal the deterministic
projection recomputed from the current project-local IR; a differing output is
stale with the exact reason `generated logical chart output does not match
current project IR`. Once the Article or Video default output and receipt, or a
Logical Chart default output, is current, the dashboard may show
`satisfied`/`current`/`ready` and a no-action current result. Native Article
review uses its accepted v2 evidence rather than the disposable generated-review
receipt for that derived currentness. An
explicit disabled binding reason takes precedence over these blocked reasons.
The dashboard represents Core review, Slide review, Video review, Slide Logical
Chart, and Video Logical Chart as distinct review projections. Slide Logical
Chart derives from Core and Visual Page IR. Video Logical Chart additionally
derives from storyboard IR and is visibly omitted in no-video profiles. Active
video review consumes the typed CozyVideo Storyboard result to project ordered
semantic scenes—intent/role, narration, speaker/pronunciation, visible
screen/diagram/asset references, timing, transition, and direction—rather than
raw Storyboard or Visual Page source tables. It records verified direct source
and infographic identities, while renderer, receipt, and rendered-frame inputs
remain explicitly unavailable unless evidence is admitted; it never claims a
rendered video or receipt without that evidence. Invalid typed Storyboard input
fails through the Document Project diagnostic boundary before publication.
Article review likewise explicitly distinguishes unavailable Phase-41 page-flow
evidence and renderer/production-receipt inputs from verified identities. It
reports that the v2 descriptor has no accepted Phase-41 Projection selector;
it does not invent one. It presents each authored Visual Page as a navigable
reader-facing page model with its stable id (or ordinal fallback), title,
reader-facing text, visual/relationship summary, and bounded article structure
summary, never raw YAML, prompts, renderer coordinates, or debug internals.
It reports terminology and media placement only when explicitly declared by the
article; it otherwise reports them as unavailable. Video review reports infographic use only when a
typed scene's exact `diagram-refs` or `asset-refs` member equals the current
`infographic/infographic.svg` source path, never from its SHA-256 identity
alone. Disabled video
remains visibly omitted. Dashboard links the required final infographic
SVG for direct review, while review HTML links appear after their corresponding
default outputs exist. Every dashboard href is resolved from the project-relative
default target and relativized from the selected dashboard output parent. These
projections are not an authority, provider run, production receipt, state cache,
feedback record, or write-back mechanism. Default Article and Video review
receipts are disposable generated-output evidence only. Values are
HTML-escaped and the projections are self-contained and deterministic.  Their destinations require
the nearest existing parent directory to be direct and non-symlinked; only
missing descendants beneath it may be created as direct directories, and
higher pre-existing ancestry is not inspected for this publication admission.
A present destination must be a direct, non-symlink regular file; publication
uses a same-directory atomic temporary-file move, with no direct-write or
non-atomic fallback.  After normalization, any destination inside the admitted
Project package is permitted only beneath `<project>/target/document-project/`
and only when its filename ends exactly in `.html`; all other Project-internal
destinations reject with `DP-PATH-001` before parent creation, temporary output,
or publication.  This protects descriptor, Content Core, article, Visual Page,
infographic, review, video, target state, evidence, and other Project-owned
paths from projection replacement.  Explicit destinations outside the Project
retain exact-path behavior under the direct-parent and atomic-publication
rules.  They are never semantic, workflow, renderer, receipt, or status
authority and never write back into authored sources.

## Content Core candidate and acceptance boundary

P45-02 replaces the former multi-authority write-back path with three explicit
Content Core forms. `candidate` receives a completed direct JSON/YAML dialogue
bundle for the declared `content-core.compose` operation. It records a
succeeded candidate or a failed operation attempt but never calls a provider
and never changes the Core. `feedback` writes immutable human
`changes-requested` or `rejected` evidence for an extant candidate. A later
succeeded candidate may supersede a candidate that has changes-requested
feedback. `accept` is the only Core write path: it accepts a nonterminal
candidate only on an explicit human `accepted` decision and atomically replaces
the existing direct Core entries.

The three forms are exactly `content-core candidate <project> <dialogue>`,
`content-core feedback <project> <candidate-id> <feedback>`, and
`content-core accept <project> <candidate-id> <acceptance>`. The dialogue is
the closed `cozy.content-core-dialogue.v1` document with source, idea,
provider, model, request, response, outcome, diagnostics, and—only for a
succeeded outcome—a closed Core candidate plus one optional earlier candidate
id. A failed dialogue has diagnostics but no candidate or supersession.
Feedback and acceptance are separately closed
`cozy.content-core-feedback.v1` and `cozy.content-core-acceptance.v1`
documents. Feedback names its exact candidate and human reviewer, then records
only `changes-requested` feedback or a `rejected` reason. Acceptance names its
exact candidate and human reviewer with the sole decision `accepted`.

Every input has a closed JSON/YAML schema and is admitted as a direct,
non-symlink regular file. Candidate/attempt, feedback, and acceptance records
are append-only, direct project-local files below `evidence/content-core/`.
They retain raw source, idea, request, response, provider/model identity,
diagnostics, candidate/result Core identity, reviewer identity, decision, and
any direct supersession link. The evidence is not public source, a render
input, a receipt, a dashboard projection, or an external provider command.
No accepted/rejected/superseded decision can be rewritten; a changed Core makes
the old acceptance record historical because it binds the resulting Core hash.

The forms use one project-local direct regular coordination file at
`<project>/.content-core.lock`. It is deliberately neither evidence nor
authored semantic authority and adds no state record. After input admission,
the non-waiting exclusive lock encloses all Content Core state observation,
evidence-directory creation and append, Core hash validation, and Core
replacement. A busy or same-JVM-overlapping lock is a retryable `DP-OP-001`
with no evidence or Core mutation; an unsafe or unusable lock path is
`DP-PATH-001`. The evidence-first acceptance/recovery protocol remains inside
that critical section: exact pending acceptance resumes, an exact resulting
hash is idempotently accepted, and a third hash remains historical and
terminal.

Generic `run` remains available for unrelated declared operations. For
`content-core.compose`, descriptor admission is followed by the ordinary
authored sources and, for a video profile, direct `video/storyboard.md`
admission. Any missing or symlinked required source returns only `DP-PATH-001`
without an attempt or Content Core evidence; otherwise generic compose rejects
with the explicit-content-core `DP-OP-001` diagnostic so a recorded-only
generic attempt cannot be mistaken for a candidate result. This adds no
schema/version, remote/provider, or compatibility behavior.

Project production stops at the project boundary.  Workspace integration,
aggregate build, and external delivery are separate operations and separate
responsibilities.  A project result therefore does not imply registration,
workspace-wide build, publication, deployment, upload, or external acceptance.

## Deliberate exclusions

This boundary excludes autonomous acceptance; mutable progress or status
authority; scheduler, daemon, and arbitrary-command execution; dashboard
write-back; implicit registration, build, publish, deploy, or upload; and
migration. P45-03 admits only the bounded article and video review projections;
P45-04 retains exclusive ownership of dashboard redesign and action selection.
P45.2 admits private content alignment and the Article 8 read-only normal
`index.dox` source projection; delivery, publication, deployment, upload,
external registration, and other external action remain excluded. It is
additive to the retained authorities above and does not retrofit existing
article or media packages.

## Deferred implementation boundary

The companion specification freezes the initial descriptor/Core grammar,
public command signatures, validation diagnostics, success headings, atomic
scaffold contents, and the DP42-02 reusable workflow definition above.  The
descriptor and Core grammars are permanently closed and are not expandable by
a later Phase.  DP42-02 MUST NOT add descriptor fields, canonical workflow
serialization, or identity calculation beyond the closed in-code Work Product
identities and references.

Phase 42.1 accepts exactly one standalone local `standard-video`/`directory`
driver and one isolated non-Article-8 `bok`/`bok` driver.  That bounded local
acceptance does not admit host discovery, registration, external receipts,
Article 8, migration, aggregate build, publication, deployment, or upload;
those and any external receipt-schema work remain deferred.
DP42-04A implements executable dashboard and review projections as described
above.  DP42-03B implements deterministic disposable state reconstruction,
DP42-03C implements recorded append-only attempts, and DP42-03D implements
sidecar-bound receipt/currentness plus evidence-based stale propagation; the
remaining capabilities are later Slices.  These projections consume the
  closed DP42-02 definition without
redefining profiles, Work Product roles, criteria, gates, operations, or
provider bindings.  SmartDox source projection and host discovery are also
outside this kernel.  No deferred capability is accepted or claimed by this
design.

## Phase 56 / 56.1 native provider and accepted-evidence boundary

Phase 56 replaces only the normal generic `run` path's historical record-only
dispatch. The public grammar remains `cozy document-project run <project>
--operation <logical-operation> [--dry-run]`, but it now resolves the declared
logical operation, provider binding, participating prerequisite Work Products,
direct authoritative inputs, and bounded output destination before it invokes a
provider.

The workflow declares one native provider contract in this Phase:
`article.render-review` through `cozy-review-projection`, with exactly one
output identity, `article-review-html`, at
`target/document-project/article-review.html` with media type `text/html`.
Before provider invocation, its direct authoritative inputs are admitted as
Content Core, `index.dox`, `presentation/visual-pages.yaml`, and the directly
consumed `infographic/infographic.svg`; the infographic is admitted at this
boundary even though it is not a declared Work Product prerequisite. The
provider invokes the existing deterministic article-review HTML projection as
an implementation detail. It is not an adapter for `cozy-article-media` or any
publication-preparation workflow.

A native provider result is one of `executed`, `blocked`, or `failed`.
`executed` carries at least one typed output identity/path/media type/SHA-256,
diagnostics, and an in-memory receipt. The receipt identity is the canonical
native identity and its value deterministically binds operation, output path,
media type, and SHA-256. A standalone receipt file is deliberately not an
authority. Empty or malformed output/diagnostic/receipt data is failure, never
execution success. Known bindings without a native provider return a typed
`blocked` result before output or evidence side effects.

Phase 56.1 owns the private accepted-evidence closure after the Phase 56
admission and provider boundary. It captures the exact ordered direct input
identities immediately before invocation and revalidates them with the declared
output identity/path/media type, direct non-symlink output bytes, SHA-256, and
receipt before accepting an executed result. An input changed during execution
therefore cannot produce accepted evidence.

The only durable acceptance is one append-only
`cozy.document-operation-attempt.v2` file per admitted execution attempt.
Accepted records retain ordered direct inputs, declared output identity/path/
mediaType/SHA-256, diagnostics, and the canonical receipt; failed records
retain diagnostics but no outputs or receipt. Writer and reader are strict,
and the writer uses safe direct evidence directories, a same-directory
temporary file, and non-replacing `ATOMIC_MOVE`. The snapshot derives Article
review currentness from valid accepted v2 identities, so a source or output
change is stale and a later accepted run recovers currentness. A v2 failure is
history, not success. v1 attempts remain historical records with their existing
reader and state behavior.

`--dry-run` performs the same resolution and admission without invoking a
provider or creating a target, evidence, attempt, receipt file, or currentness
state. The unavailable-provider block remains non-executed and creates no
attempt. Phase 56.2 exclusively owns closed executable state and verification
policy. Phase 57 exclusively owns publication export. These boundaries do not
authorize a compatibility adapter, receipt adoption, delivery, deployment, or
another native provider.

## Related authorities

- Normative contract: [Document Project Specification](../spec/document-project.md)
- Native execution phase: [Phase 56](../phase/phase-56.md)
- Accepted-evidence phase: [Phase 56.1](../phase/phase-56.1.md)
- Historical phase: [Phase 42](../phase/phase-42.md)
- Progress ledgers: [Phase 56 checklist](../phase/phase-56-checklist.md) and [Phase 42 checklist](../phase/phase-42-checklist.md)
- Planning input only: [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
  and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md)
