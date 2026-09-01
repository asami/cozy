# Document Project Design

## Status and authority

This document records the stable design intent for the Document Project
boundary in Phase 42.1, extending [Phase 42](../phase/phase-42.md).  It is design, not an
executable implementation or a replacement for the [Phase 42
checklist](../phase/phase-42-checklist.md), which remains the progress ledger.
The normative behavior contract is [Document Project
Specification](../spec/document-project.md).

This design promotes only the frozen boundary identified by the non-normative
planning inputs: the [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md).

## Operational envelope and semantic authority

Document Project is the operational envelope for a coherent document-production
effort.  Its authored descriptor selects a reusable workflow, one registered
workflow profile, language, workspace kind, and Content Core reference; the
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
`site-deliverable`, `deliverable`, and `receipt`. Branch selection distinguishes `required`,
`optional`, and `disabled`; an omitted branch has a visible reason rather than
being treated as completed work.

## Phase 42.1 evidence and attempt boundary

This design records the Phase 42.1 evidence boundary.  DP42-03B implements the
disposable snapshot cache; DP42-03C implements recorded single-operation
dispatch and append-only attempt persistence; DP42-03D binds optional retained
evidence to the existing project without reopening the descriptor kernel.

The `cozy.document-project-state.v1` Workflow Instance Snapshot is disposable
derived YAML at `<project>/target/document-project/state.yaml`.  It is never
authored authority and cannot be edited as a state override.  Removing
`<project>/target/document-project` and inspecting unchanged admitted project
inputs reconstructs the identical snapshot.

Successful `inspect` and `verify` regenerate the cache only after their
descriptor, Core, and command-specific source validation succeeds.  Its
canonical UTF-8 YAML keys are ordered `schema`, `project`, `profile`,
`workspace`, `sources`, `evidence`, and `workProducts`.  The fixed-order `sources` entries
contain only project-relative direct authored paths and lowercase hexadecimal
SHA-256 content identities: descriptor, exact Core, `index.dox`, infographic
SVG, Visual Page source, review README, and directly present storyboard for a
video profile.  No absolute path, timestamp, random value, discovery order,
generated output or receipt content is serialized.  `evidence` separately
records only optional sidecar and retained-attempt project-relative path and
SHA-256 identities; it is not authority.

The fixed-order Work Product projection contains `id`, `role`, `disposition`,
`criterion`, `coverage`, `currentness`, `review`, and `readiness`.  It uses only
the closed status vocabularies.  Existing source assets may be
`satisfied`/`current`; absent output or receipt-dependent evidence remains
`missing`/`blocked`/`pending`.  Standard's disabled video products explicitly
use `coverage: not-applicable`, `readiness: omitted`, and the profile reason
`profile standard disables video branch`, never completion.  DP42-03C
implements immutable append-only attempts and recorded dispatch only.  DP42-03D
adds the optional sidecar evidence that derives retained receipt currentness
and dependency-driven `stale` state without changing media receipt contracts.

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
none`.  The dispatch invokes no provider, creates no output or receipt, writes
no Core or state cache, and infers no downstream operation.  Accepted review
evidence is separate from an attempt and remains required before a Content Core
write-back.

DP42-03C creates `evidence/attempts` only for an eligible normal run; the
directories and files must be direct, non-symlinked project entries.  Each
attempt is written to a same-directory temporary file and published with
`ATOMIC_MOVE` without replacement.  Collisions or publication failures leave
existing bytes untouched, remove the temporary file, and return a stable
path/evidence diagnostic.  `--dry-run` performs the same admission without
creating evidence or a cache.

The snapshot independently derives coverage (`satisfied`, `missing`, and
`not-applicable` criteria), currentness (`missing`, `current`, `stale`, or
`failed`) from declared identities rather than timestamps, review (`pending`,
`accepted`, `rejected`, or `stale`), and readiness (`blocked`, `ready`,
`running`, `succeeded`, `failed`, or `omitted`).  `omitted` is visible with its
declared profile reason and is not completion.

DP42-03D adds a portable sidecar at
`<project>/evidence/document-project.yaml`, never scaffolded and always direct
and non-symlinked.  Its closed schema is
`cozy.document-project-evidence.v1`, with ordered `schema`, `project`,
`publicSource`, and `products` keys.  It binds the admitted descriptor id to a
safe SmartDox source mapping and a fixed, enabled-Work-Product-order evidence
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

Content Core alone may carry a core-dialogue review record.  Its provider,
model, request, response, and accepted-or-rejected human disposition are
retained evidence, not an invoked provider or an autonomous authority.  An
accepted branch identifies the current descriptor Core bytes; an authority or
request/response change makes the review stale.

One shared state model derives cache and dashboard rows from the sidecar when
present, otherwise preserving legacy source-derived missing/pending behavior.
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
deployment, upload, or downstream operation.  DP42-03C `run` dispatches
exactly one declared registered operation enabled for the selected profile and
creates one append-only attempt, while `--dry-run` reports the same selected
operation, provider, and profile without persistence.  It does not infer
downstream execution.  This boundary
does not alter the closed descriptor fields, common workflow DAG, profiles,
Work Product roles, criteria, gates, operation IDs, provider bindings, retained
media/SmartDox/Visual Page/Phase-41 authorities, public command grammar, or
Phase-42 compatibility behavior.

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

The definition's stable initial Work Products are:

| Work Product id | Role | no-video | video |
| --- | --- | --- | --- |
| `content-core-candidate` | candidate | optional | optional |
| `content-core` | authority | required | required |
| `core-review-html` | review-projection | optional | optional |
| `article-source` | authority | required | required |
| `article-html` | site-deliverable | required | required |
| `article-pdf` | deliverable | required | required |
| `visual-pages` | authority | required | required |
| `slide-review-html` | review-projection | optional | optional |
| `summary-slides-pdf` | deliverable | optional | optional |
| `infographic-svg` | authority | required | required |
| `infographic-png` | deliverable | optional | optional |
| `video-storyboard`, `video-review`, `video-deliverable` | plan/review/deliverable | disabled with profile reason | required |
| `explanation-structure-review-html` | review-projection | optional | optional |
| `video-logical-chart-html` | review-projection | disabled with profile reason | optional |
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
Site publication, and PDF render operations, `summary-slides.render-pdf`, infographic compose and PNG
render operations, the three video operations, slide and video logical-chart
review operations, and `operation-receipt.record`. Their
providers identify the fixed Cozy, SmartDox, Visual Page, infographic, video,
Phase-41 Explanation Structure Review, or receipt adapter responsibility; they
do not discover a provider or execute an adapter in DP42-02.  Criteria, gates,
and evidence references are static identity links, not completion, currentness,
review, receipt, or lifecycle fields.

`plan` resolves this definition read-only.  It emits deterministic static
`active` and `omitted` Work Product lines, plus `blocked` and `eligible`
logical-operation lines.  `eligible` means that an operation is declared for
the selected profile, not that it is runtime-ready.  In Phase 42 every such
operation is simultaneously blocked from execution because execution and
Operation Attempts are reserved for Phase 42.1.  The command creates no
target, dashboard, state, attempt, receipt, registry, delivery, or output file.

`run` validates the descriptor, Core, and command-admitted initial sources,
then admits exactly one declared operation enabled by the selected profile.  A
normal eligible run records one attempt without invoking its provider or
creating output, receipt, Core write-back, state cache, or downstream work.
`--dry-run` reports the selected operation, provider, and profile without
creating evidence.  An unknown or profile-disabled operation rejects with
`DP-OP-001`; malformed or unsafe input retains its earlier diagnostic
precedence and neither rejection creates evidence.

## Initial authored kernel

The initial Document Project package has one authored descriptor,
`document-project.yaml`, and one separately authored Content Core at
`content/core-<language>.yaml`.  The descriptor is deliberately a small,
closed selection boundary.  Its only selection inputs are identity, workflow,
profile, language, workspace, and Content Core reference.  It does not embed a
private workflow graph, artifact-local source, delivery declaration, provider
binding, or mutable lifecycle state.

The descriptor's `workflow` field is an identity reference to the reusable
`document-production` Workflow Definition.  Its `profile` field selects one
closed registered binding within that Workflow Definition: `standard`,
`standard-video`, `bok`, `bok-video`, `simplemodeling-org`, or
`simplemodeling-org-video`.  The `simplemodeling-org` forms are hidden from
public scaffold/help selection; `standard` and `bok` are no-video profiles,
while `standard-video` and `bok-video` are video profiles.  DP42-02 closes and validates the workflow-owned
profile-to-Work-Product, deliverable-disposition, criteria/gate, operation,
and provider-binding model; it MUST NOT add a field to the closed
`cozy.document-project.v1` descriptor.  The `workspace` field chooses only
`directory` or `bok`; the latter is a kind selection, not hosting discovery,
registration, source projection, or an external action.  A relative Core path
keeps the Core inside the package without making the descriptor a source of
semantic meaning.

Content Core is intentionally minimal at this boundary.  Its closed grammar
binds a project and language and carries its accepted shared semantic
statements as one ordered `accepted` sequence.  Each entry has only its stable
`id` and non-empty trimmed UTF-8 `text`; entry identities are unique inside the
Core.  Thus the Core is an actual minimal semantic authority rather than an
identity-only placeholder.  Only an explicitly accepted review may add or
replace an accepted entry.  Raw AI output remains provenance outside the Core.
The Core contains neither status nor renderer/delivery/provider fields.  The
Phase 42.1 evidence/attempt contract records provider, model, request, and
response identities and acceptance, but it does not make the Core mutable
status or a receipt.  Structured semantic vocabulary and automation remain out
of scope.  The evidence/attempt boundary does not add a Core field or make
attempt evidence a review authority.

## Command and scaffold boundary

`cozy document-project` has a distinct public namespace, deliberately
separate from the existing software Project knowledge-package commands and
`cozy media`.  Its project operands name existing `*.dox/` package directories,
not arbitrary descriptor files.  `inspect`, `plan`, and `verify` are derived
inspections; successful `inspect` and `verify` regenerate only the disposable
state cache specified above.  Phase 42.1 implements
`dashboard <project> [--save <dashboard.html>]` and
`review <project> --kind core|slides|video|slide-logical-chart|video-logical-chart [--save <review.html>]` as deterministic,
self-contained, read-only HTML projections.  The dashboard default is
`target/document-project/project-dashboard.html`; review defaults are
`target/document-project/core-review.html`,
`target/document-project/slides-review.html`,
`target/document-project/video-review.html`,
`target/document-project/slide-logical-chart-review.html`, and
`target/document-project/video-logical-chart-review.html`. An explicit `--save` path is
used exactly as requested when external or valid under the project-local
projection boundary, and review exposes no logical-operation ID.  The
Core review, Slide review, Video review, Slide Logical Chart, and Video Logical
Chart are separate purpose-oriented projections. The slide chart projects Core
and Visual Page IR; the video chart additionally projects storyboard IR and is
available only for a video profile. These
commands do not execute providers, persist candidate/feedback/acceptance/
receipt state, or modify authored authorities.  The historical Phase-42
`DP-PHASE-001` dashboard rejection is retained only as compatibility history
and is not emitted by Phase 42.1.

The exact Phase 42.1 public forms are:

```text
cozy document-project dashboard <project> [--save <dashboard.html>]
cozy document-project review <project> --kind core|slides|video|slide-logical-chart|video-logical-chart [--save <review.html>]
cozy document-project reflect-feedback <project> <feedback>
```

Dashboard defaults to `target/document-project/project-dashboard.html`; Core,
slide, video, Slide Logical Chart, and Video Logical Chart review default to
`target/document-project/core-review.html`,
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
deployment, upload, remote calls, or Article 8 work.
Dashboard content has accessible Workflow, Work Product matrix, and Work
Product details tables covering dispositions, providers, gates, coverage,
currentness, review, readiness, dependencies, producer/consumer operations,
evidence references, and a user-facing next action. It is the place to see the
current Project state and the next required or useful action without needing to
interpret a logical-operation identifier. It distinguishes the current
snapshot from retained attempts, whose initial records have no receipt or
currentness authority.  Core review presents accepted Core entries and marks
candidate/feedback/acceptance as non-authoritative and not yet persisted.
Review-projection Work Products remain `missing`/`missing`/`blocked` until the
corresponding default review HTML exists; if any declared source prerequisite
is absent, the reason is exactly `source or retained evidence is not present`,
and if all declared source prerequisites exist, the reason is exactly
`default review HTML is not generated`. Once that output exists, the dashboard
may show `satisfied`/`current`/`ready` and a no-action current result. An
explicit disabled binding reason takes precedence over these blocked reasons.
The dashboard represents Core review, Slide review, Video review, Slide Logical
Chart, and Video Logical Chart as distinct review projections. Slide Logical
Chart derives from Core and Visual Page IR. Video Logical Chart additionally
derives from storyboard IR and is visibly omitted in no-video profiles. Active
video review presents storyboard and Visual Page source projections; disabled
video remains visibly omitted. Dashboard links the required final infographic
SVG for direct review, while review HTML links appear after their corresponding
default outputs exist. Every dashboard href is resolved from the project-relative
default target and relativized from the selected dashboard output parent. These
projections are not an authority, provider run,
receipt, state cache, feedback record, or write-back mechanism.  Values are
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

## Feedback reflection boundary

Phase 42.1 DP42-04B defines `reflect-feedback <project> <feedback>` as a
bounded authority-reflection command.  Its feedback path must be a direct,
non-symlink regular file whose name ends in `.json`, `.yaml`, or `.yml`
(case-insensitive); any other suffix is rejected with `DP-CLI-001` before
parsing.  Its direct JSON or YAML structured batch uses one common object
schema and keeps the original replacement proposal on every item while making
applicability and disposition explicit.  Format decoding may follow the
`.json`, `.yaml`, or `.yml` suffix, but feedback semantics never do.  Malformed
JSON/YAML syntax is feedback input grammar and rejects with `DP-CLI-001`;
malformed replacements and duplicate Core IDs remain `DP-DESC-001`.
Applicable items are either accepted or rejected; a
rejected item requires `rejectionReason`.  A not-applicable item has the
matching `not-applicable` disposition and requires `notApplicableReason`.  The
batch reason and both conditional reasons are non-empty trimmed strings,
targets are unique among `core`, `article`, `slides`, `infographic`, and
`video`, and the replacement is a Core `{accepted: [...]}` object for `core`
or a non-empty full-source string for every other target.

The common batch schema is format-neutral:

```yaml
reason: nonempty trimmed batch reason
changes:
  - target: core
    replacement:
      accepted:
        - id: claim-1
          text: ...
    applicability: applicable
    disposition: accepted
  - target: article
    replacement: proposed full source
    applicability: applicable
    disposition: rejected
    rejectionReason: nonempty trimmed reason
```

The closed mappings are `core` -> descriptor `contentCore`, `article` ->
`index.dox`, `slides` -> `presentation/visual-pages.yaml`, `infographic` ->
`infographic/infographic.svg`, and `video` -> `video/storyboard.md`.  Core
reflection preserves and revalidates the current Core envelope.  The standard
profile requires a video item marked not-applicable with its reason; an
applicable video item is an operation/profile rejection.  Video profiles may
reflect their active storyboard source.

Reflection validates the complete batch and all accepted source paths before
writing.  Only accepted direct, non-symlink authority files are replaced,
using a same-directory temporary file and `ATOMIC_MOVE`, with no parent
creation or fallback.  Rejected and not-applicable items leave their
authorities unchanged.  Success reports each item disposition and mapped
path or reason without echoing the batch reason or replacement.  No feedback
record, receipt, attempt, state, provider execution, review output, or other
evidence is created.  The command therefore remains a write boundary for
explicitly accepted authorities, while dashboard and review remain
read-only projections.

Project production stops at the project boundary.  Workspace integration,
aggregate build, and external delivery are separate operations and separate
responsibilities.  A project result therefore does not imply registration,
workspace-wide build, publication, deployment, upload, or external acceptance.

## Deliberate exclusions

This boundary excludes autonomous acceptance; mutable progress or status
authority; scheduler, daemon, and arbitrary-command execution; dashboard
write-back; implicit registration, build, publish, deploy, or upload;
migration; and any Phase 41 expansion.  It is additive to the retained
authorities above and does not retrofit existing article or media packages.

## Deferred implementation boundary

The companion specification freezes the initial descriptor/Core grammar,
public command signatures, validation diagnostics, success headings, atomic
scaffold contents, and the DP42-02 reusable workflow definition above.  The
descriptor and Core grammars are permanently closed and are not expandable by
a later Phase.  DP42-02 MUST NOT add descriptor fields, canonical workflow
serialization, or identity calculation beyond the closed in-code Work Product
identities and references.

Phase 42.1 retains driver acceptance and any external receipt-schema work.
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

## Related authorities

- Normative contract: [Document Project Specification](../spec/document-project.md)
- Phase plan: [Phase 42](../phase/phase-42.md)
- Progress ledger: [Phase 42 checklist](../phase/phase-42-checklist.md)
- Planning input only: [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
  and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md)
