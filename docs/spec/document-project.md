# Document Project Specification

## Status and scope

This is the normative Phase 45 contract for the Document Project v2 authoring
boundary. Its stable design intent is [Document Project
Design](../design/document-project.md). The Phase 45 checklist is a progress
ledger, not a behavior contract.

The [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md)
are read-only, non-normative planning inputs.  They do not add behavior beyond
this specification.

## Contract identities

A conforming future implementation MUST preserve the following distinct
contract identities and roles:

| Identity | Required role |
| --- | --- |
| `cozy.document-project.v2` | Authored closed selection descriptor: identity, workflow, profile, language, workspace, Content Core reference, optional Work Product selection, and semantic identity hooks. |
| `cozy.document-workflow.v2` | Reusable logical-operation DAG with Work Product roles, criteria, dependencies, gates, and provider bindings. |
| `cozy.document-project-evidence.v2` | Optional retained evidence binding for exactly the selected Work Products. |
| `cozy.document-project-state.v2` | Derived Workflow Instance Snapshot; it is never writable authority. |
| `cozy.document-operation-attempt.v1` | Append-only evidence for one attempted declared logical operation. |
| `cozy.content-core.v1` | Minimal, separately authored shared semantic authority. |

A Document Project MUST be the operational envelope, not a replacement for
Content Core.  Content Core MUST remain the shared semantic authority; it MUST
NOT become a mutable workflow-status record, rendering configuration, or
delivery declaration.

A project MUST resolve its selected `cozy.document-workflow.v2` definition by
identity.  It MUST NOT copy the reusable DAG into the authored descriptor for
private modification.  A `cozy.document-project-state.v2` snapshot MUST be
derived from the relevant authored authorities and evidence.  A cache of that
snapshot MUST NOT become a second authority.  An Operation Attempt MUST remain
inspectable after failure or supersession and MUST NOT itself make a Work
Product accepted or current.

Document Project v2 is a replacement authored contract. No v1 descriptor,
workflow, evidence, or state reader, migration, fallback, or compatibility
branch is retained. A retired descriptor is rejected before an action writes a
state cache, evidence, output, or other project file.

## Initial authored descriptor grammar

The only initial Document Project descriptor is `document-project.yaml`.  It
MUST have exactly these top-level keys and no others:

| Key | Required value or closed shape |
| --- | --- |
| `schema` | Exactly `cozy.document-project.v2`. |
| `id` | A slug matching `[a-z0-9][a-z0-9._-]*`. |
| `workflow` | An object with exactly `schema` and `id`; `schema` is exactly `cozy.document-workflow.v2` and `id` is exactly `document-production`. |
| `profile` | Exactly one of `standard`, `standard-video`, `bok`, `bok-video`, `simplemodeling-org`, or `simplemodeling-org-video`; the last two are hidden from public scaffold/help selection. |
| `language` | A lowercase BCP-47-shaped tag matching `[a-z]{2,8}(?:-[a-z0-9]{1,8})*`. |
| `workspace` | An object with exactly `kind`, whose value is exactly `directory` or `bok`. |
| `contentCore` | Exactly the normalized relative path `content/core-<language>.yaml`, where `<language>` is the descriptor `language`. |
| `activeOptionalWorkProducts` | A deterministic duplicate-free sequence of ids whose static disposition in the selected profile is exactly `optional`. Required and profile-disabled ids are rejected. |
| `semanticScope` | An object with exactly `id` and `localeVariants`, as defined below. |

`contentCore` MUST NOT be absolute and MUST NOT contain a `.` or `..` path
segment.  `workflow` is a reference to the reusable definition and MUST NOT be
a copied DAG.  `workspace.kind: bok` binds only workspace kind; it MUST NOT
imply hosting discovery, registration, source projection, or an external
action.  No unknown or additional descriptor key is admitted by this kernel or
by DP42-02.

`profile` selects a closed registered binding inside the referenced
`cozy.document-workflow.v2` definition. The exact descriptor-resolvable
profiles are `standard`, `standard-video`, `bok`, `bok-video`,
`simplemodeling-org`, and `simplemodeling-org-video`; the last two are hidden
from public scaffold/help selection.  `standard` and `bok` are no-video
profiles, while `standard-video` and `bok-video` are video profiles.  DP42-02 MUST close and validate the
workflow-owned profile-to-Work-Product, deliverable-disposition,
criteria/gate, operation, and provider-binding model.  DP42-02 MUST NOT add a
field to `cozy.document-project.v2`.

`semanticScope.id` is a stable slug. `semanticScope.localeVariants` is a
non-empty deterministic sequence. Every entry has exactly `project`,
`language`, `contentCore`, and `workProducts`: `project` is a stable project
slug; `language` is a lowercase BCP-47-shaped tag; `contentCore` is a non-empty
stable identity; and `workProducts` is a duplicate-free sequence of exactly
`{id, identity}` pairs. Each pair names a declared v2 Work Product and has a
non-empty stable identity. The descriptor's own `(id, language, Core id)`
occurs exactly once. These are identity hooks only: admission does not read
another project, scan directories, synchronize locales, infer currentness, or
record alignment decisions.

Scaffold emits a self-contained v2 descriptor with
`activeOptionalWorkProducts: []` and exactly one matching self locale variant
whose `workProducts` is empty.

The exact Core file is `content/core-<language>.yaml`.  It MUST have exactly
`schema`, `id`, `language`, and `accepted`, with these values:

| Key | Required value |
| --- | --- |
| `schema` | Exactly `cozy.content-core.v1`. |
| `id` | Exactly `<project-id>:core:<language>`. |
| `language` | Exactly the descriptor `language`. |
| `accepted` | An ordered sequence, which MAY be empty in a freshly scaffolded package.  Each entry is an object with exactly `id` and `text`; `id` matches `[a-z0-9][a-z0-9._-]*` and is unique inside the Core, and `text` is a non-empty trimmed UTF-8 semantic statement. |

This Core is a minimal semantic authority.  Only an explicitly accepted review
MAY add or replace an `accepted` entry.  It MUST contain no mutable status,
rendering configuration, delivery declaration, raw prompt or response, review
decision, provider binding, artifact-local source, claims/evidence/term/relation
vocabulary, external reference, or generic map or extension field.  AI
assistance is a future admitted operation only: a raw response is provenance,
never authority; eventual Phase 42.1 attempt/review evidence MUST retain
provider, model, request, and response identities and explicit review
acceptance before any Core write-back.  That evidence MUST NOT make the Core
into mutable status or a receipt.  Structured semantic vocabulary and
automation remain out of scope.  The Phase 42.1 evidence/attempt contract
below does not add a Core field or make attempt evidence a review authority.

The closed descriptor and Core fields above are permanently closed and are not
expandable.  DP42-02 closes only the workflow-owned Work Product, provider-binding,
deliverable-disposition, criteria/gate, and operation model.

## Retained authorities and Work Products

The closed Work Product role vocabulary is exactly `authority`, `plan`,
`candidate`, `review-projection`, `site-deliverable`, `deliverable`, and
`receipt`. A role MUST be
declared by the workflow or project binding; it MUST NOT be inferred from a
filename, extension, or output directory.

DP42-02 closes one immutable in-code `document-production` definition.  It
resolves `standard`, `standard-video`, `bok`, `bok-video`, and the hidden
`simplemodeling-org` and `simplemodeling-org-video` profiles and is selected by the existing
descriptor identity; it MUST NOT be serialized into, copied by, or extended
through `cozy.document-project.v2`. Before plan projection or declared-
operation admission, the definition MUST reject duplicate ids, empty required
metadata, unknown producer, consumer, criterion, dependency, gate,
evidence-reference, or provider-binding references, Work Product dependency
cycles, and profile bindings outside the closed definition.

The stable initial Work Product table is:

| Work Product id | Role | no-video profile | video profile |
| --- | --- | --- | --- |
| `content-core-candidate` | candidate | optional | optional |
| `content-core` | authority | required | required |
| `core-review-html` | review-projection | optional | optional |
| `article-source` | authority | required | required |
| `article-html` | site-deliverable | required | required |
| `article-review-html` | review-projection | optional | optional |
| `article-pdf` | deliverable | required | required |
| `visual-pages` | authority | required | required |
| `slide-review-html` | review-projection | optional | optional |
| `summary-slides-pdf` | deliverable | optional | optional |
| `infographic-svg` | authority | required | required |
| `infographic-png` | deliverable | optional | optional |
| `video-storyboard` | plan | disabled with the selected profile reason | required |
| `video-review` | review-projection | disabled with the selected profile reason | required |
| `video-deliverable` | deliverable | disabled with the selected profile reason | required |
| `explanation-structure-review-html` | review-projection | optional | optional |
| `video-logical-chart-html` | review-projection | disabled with the selected profile reason | optional |
| `operation-receipt-evidence` | receipt | optional | optional |

`article-html` is a required Site deliverable, generated by the Cozy Site
feature from the SmartDox article source. It is intentionally separate from
the ordinary PDF, slide, infographic, and video deliverable branches.

`article-review-html` is a first-class optional review-projection Work Product.
Its stable logical operation is `article.render-review` through the stable
review-projection provider binding. Its criterion, gate, and evidence reference
are declared with dependencies on Content Core, article source, and Visual Page
IR. It is contract-only in Phase 45: it does not generate HTML, add a review
CLI kind, alter dashboard UX, or invoke a provider.

`infographic-svg` is the required editable final infographic artifact and is
reviewed directly as that artifact; Document Project MUST NOT introduce a
separate infographic review HTML merely to duplicate it.

`standard` and `bok` are no-video profiles; `standard-video` and `bok-video`
are video profiles. `simplemodeling-org` and `simplemodeling-org-video` have,
respectively, the same current matrices as `bok` and `bok-video`, but are
registered hidden profiles: descriptor admission, inspection, planning,
verification, review, feedback reflection, and operation admission resolve
them, while public help and `scaffold` do not offer them. Profile describes
which Work Products participate; `workspace` remains the independent directory
or BoK operating context.

Every Work Product MUST directly declare a producer and consumer logical-
operation reference, criterion reference, Work Product dependency reference,
gate reference, and evidence-reference.  Every logical operation MUST have a
stable id and exactly one static provider binding.  The initial operation set
is `content-core.compose`, `content-core.review`, `article.compose`,
`article.publish-site`, `article.render-review`, `article.render-pdf`, `summary-slides.render-pdf`,
`infographic.compose`, `infographic.render-png`, `video.compose-storyboard`,
`video.render-review`, `video.render-deliverable`,
`content-core.render-review`, Visual Page author/review and PDF render
operations, infographic operations, the three video operations, slide and
video logical-chart review operations, and `operation-receipt.record`.
Their provider bindings are fixed Cozy, SmartDox, Visual Page, infographic,
video, Phase-41 Explanation Structure Review, or receipt-adapter identities;
they are neither descriptor fields nor provider discovery or execution.

`required`, `optional`, and `disabled` are distinct static dispositions.  The
video Work Products in a no-video profile MUST expose its exact selected
`profile <id> disables video branch` reason; no Work Product in a video branch
MAY expose a disabled reason. Criteria, gates, and evidence
references are stable model links.  They MUST NOT be used as mutable lifecycle,
status, receipt/currentness, or dashboard fields.

Document Project MUST preserve, rather than reinterpret, the authorities
defined by the [Media Package specification](media-package.md), [Media Package
operation design](../design/media-package-operation.md), [Visual Page
specification](visual-page.md), and [Visual Page design](../design/visual-page.md).
SmartDox article source, editable infographic SVG, Visual Page/Slide sources,
Storyboard/video sources, existing receipts, and existing review-state
contracts remain their respective authorities.  This baseline does not migrate,
replace, broaden, or certify compatibility with any of those legacy contracts.

## Branches and lifecycle views

Every artifact branch MUST have exactly one declared disposition:

- `required` means its applicable criteria and gates participate in the
  project's completion view.
- `optional` means it is selectable and visible but does not participate until
  its id is selected by `activeOptionalWorkProducts` for the project.
- `disabled` means it is omitted and MUST expose an exact visible reason.

The derived selection vocabulary is exactly `required`, `active-optional`,
`inactive-optional`, and `profile-disabled`. Required and active optional Work
Products participate. Inactive optional Work Products are visibly
nonparticipating, use `coverage: not-applicable`, `currentness:
nonparticipating`, and `readiness: not-selected`, and do not consume or delete
existing evidence or artifacts. Profile-disabled Work Products use
`currentness: not-applicable` and `readiness: omitted` with their profile
reason. Neither nonparticipating class contributes to applicable completion.

An omitted or inactive branch MUST NOT be counted as completed work. Completion coverage,
currentness, review, readiness, and labels such as core or artifact lifecycle
states MUST be derived views over declared Work Products and evidence.  They
MUST NOT be writable project status, manually asserted percentages, or an
autonomous acceptance decision.  `complete`, `current`, and `accepted` MUST
remain distinct views.

Dashboard HTML and review HTML MUST be generated read-only projections.  They
MUST NOT become semantic, workflow, renderer, receipt, or status authority and
MUST NOT write back into authored sources.  Phase 42.1 Slice DP42-04A defines
their public purpose-oriented commands and deterministic output contract below.

## Phase 42.1 evidence and attempt contract

This section is the normative Phase 42.1 evidence boundary.  DP42-03B
implements the disposable snapshot cache described here, DP42-03C implements
recorded single-operation dispatch and append-only attempt persistence, and
DP42-03D adds the bounded evidence sidecar and dependency-derived stale state
below.

`cozy.document-project-state.v2` MUST be a disposable derived YAML snapshot at
`<project>/target/document-project/state.yaml`.  It is neither an authored
authority nor a state override and MUST NOT be edited to alter project state.
Deleting `<project>/target/document-project` and inspecting unchanged admitted
project inputs MUST reconstruct an identical snapshot.

Successful `inspect` and `verify` MUST regenerate the snapshot after all
descriptor, Core, and command-specific source validation succeeds.  The
canonical UTF-8 YAML serialization MUST contain, in this order, `schema`,
`project`, `profile`, `workspace`, `sources`, `evidence`, `criteria`, and
`workProducts`.  `schema` is
exactly `cozy.document-project-state.v2`; the three identity values are taken
from the admitted descriptor.  `sources` is a fixed-order list of direct,
project-relative authored inputs, each with only `path` and its lowercase
hexadecimal `sha256` content identity.  The admitted source order is the
descriptor, the exact Core, `index.dox`, editable infographic SVG, Visual Page
source, review README, and the storyboard when directly present for a video
profile.  The serialization MUST contain no absolute path, timestamp, random
value, directory-discovery order, generated output, receipt, or attempt.

`criteria` MUST follow Workflow Definition criterion order.  It MUST contain
scalar `satisfied`, the count whose derived coverage is `satisfied`, and
`total`, the count whose derived coverage is not `not-applicable`, followed by
ordered `missing` and `notApplicable` lists.  Each list item MUST contain an
`id` and a double-quoted `reason`; a missing criterion uses its derived reason
or `criterion <id> is missing`, while a not-applicable criterion uses its
declared profile reason or `criterion <id> is not applicable`.  It is
evidence-derived state only, never a writable percentage or acceptance
authority.

`workProducts` MUST follow the closed `document-production` Work Product order.
Each entry MUST contain `id`, `role`, `disposition`, `selection`, `criterion`, `coverage`,
`currentness`, `review`, and `readiness`, using only the DP42-03A vocabularies.
The source-backed Core, article, infographic, and activated storyboard
projections may be `satisfied`/`current`; absent output or receipt-dependent
evidence remains `missing`, `blocked`, and `pending`.  A disabled standard
video entry MUST be `coverage: not-applicable` and `readiness: omitted`, with
`reason: profile standard disables video branch`; it MUST never be treated as
complete.  DP42-03C implements immutable append-only attempts and recorded
dispatch only.  DP42-03D supplies the optional retained receipt, review, and
dependency evidence used to derive `stale` without timestamp inference.

`cozy.document-operation-attempt.v1` MUST be durable append-only evidence at
`<project>/evidence/attempts/<attempt-id>.yaml`.  Attempts MUST NOT be
scaffolded.  Existing attempt bytes MUST NOT be overwritten; failures and
superseded attempts MUST remain inspectable.  The existence of an attempt MUST
NOT by itself make a Work Product current, complete, reviewed, or accepted.
Each attempt MUST be UTF-8 YAML with exactly these ordered top-level keys:
`schema`, `id`, `operation`, `provider`, `profile`, `inputs`, `outcome`,
`diagnostics`, `outputs`, and `receipt`.  `schema` MUST be exactly
`cozy.document-operation-attempt.v1`; `id` MUST be a newly generated stable
attempt identifier; `operation` and `provider` MUST be the declared operation
and its frozen workflow provider binding; and `profile` MUST be copied from
the descriptor.  `inputs` MUST be the fixed-order direct project-relative
descriptor, Core, and initial authored source identities, each containing only
`path` and a lowercase hexadecimal SHA-256 `sha256`.  A recorded dispatch MUST
use `outcome: recorded`, one diagnostic that provider execution is deferred and
the dispatch was recorded only, `outputs: []`, and `receipt: none`.  It MUST
not invoke a provider, generate a deliverable, create a receipt, write back
the Core, update the disposable snapshot, or infer downstream work.  Accepted
review evidence MUST remain separate from an attempt and MUST be present before
a Content Core write-back.

DP42-03C MUST publish an eligible attempt by writing a same-directory
temporary file and moving it with `ATOMIC_MOVE` without replacement.  The
`evidence` and `evidence/attempts` directories and every attempt file MUST be
direct, non-symlinked project entries.  Attempts are never scaffolded.  A
collision or publication failure MUST preserve every existing attempt byte,
leave no partial attempt file, and return a stable path/evidence diagnostic.

The snapshot MUST independently represent all of the following derived views:

- coverage for each criterion as `satisfied`, `missing`, or `not-applicable`;
- currentness as `missing`, `current`, `stale`, `failed`, `nonparticipating`,
  or `not-applicable`, derived from
  declared identities rather than timestamps;
- review as `pending`, `accepted`, `rejected`, `stale`, or `not-applicable`; and
- readiness as `blocked`, `ready`, `running`, `succeeded`, `failed`,
  `not-selected`, or `omitted`.

`omitted` MUST expose its declared profile reason and MUST NOT be counted as
completion.  Reconstruction MUST use only the closed `document-production`
Work Product definition; descriptor and Core identities; retained source,
receipt, review, and attempt evidence; and declared producer, consumer, and
dependency identities.  It MUST NOT infer state from filenames or timestamps.
A changed dependency MUST make every declared consumer `stale`; consequently,
a changed shared infographic MUST make its declared article, slides, and video
consumers `stale`.

`inspect` and `verify` remain non-authoritative.  An implementation of either
command MAY write only the disposable snapshot cache above; it MUST NOT write
an attempt, receipt, acceptance, authored source, registry, workspace
integration, aggregate build, publication, deployment, upload, or downstream
operation.  DP42-03C `run` admits exactly one declared, registered operation
that produces a selected Work Product and creates exactly one append-only
attempt, or, with `--dry-run`, reports the selected operation, provider, and
profile without creating any cache, evidence, output, or other file.  It MUST
NOT infer downstream execution.  The closed descriptor fields, common workflow
DAG, profiles, Work Product roles, criteria, gates, operation IDs, provider
bindings, retained media/SmartDox/Visual Page/Phase-41 authorities, and public
command grammar remain otherwise unchanged. Retired v1 compatibility is not a
behavior of this contract.

## DP42-03D evidence sidecar and evidence-derived state

DP42-03D adds one optional, unscaffolded, direct non-symlink sidecar at
`<project>/evidence/document-project.yaml`.  Its schema is exactly
`cozy.document-project-evidence.v2`; it is an evidence binding, not a field or
extension of the closed `cozy.document-project.v2` descriptor. Its top-level
keys are ordered exactly `schema`, `project`, `publicSource`, and `products`.
`project` equals the admitted descriptor id.

`publicSource` has exactly `kind`, `identity`, `path`, `sha256`, and
`mediaDescriptor`.  `kind` is exactly `smartdox`; `identity` is a non-empty
exact identity; `path` is exactly `index.dox`; and `sha256` is the lowercase
SHA-256 of current `index.dox` bytes.  `mediaDescriptor` is a direct,
project-relative, non-symlink media descriptor whose explicit
`articleMedia.articleIdentity` equals `publicSource.identity`.  This is an
explicit safe source mapping only.  It MUST NOT discover a host or identity,
register a site, or expose Content Core, raw media, review material, receipt
content, or target files as a public source.

`products` lists exactly every selected `document-production` Work Product in
the immutable workflow order. Each item has exactly `id`, `evidence`, and
`review`; its id equals the selected Work Product at that position. `evidence` is one
of these closed alternatives:

- `kind: none`, with no other field;
- `kind: source` or `kind: artifact`, each with exactly a direct,
  project-relative non-symlink `path` and lowercase `sha256`;
- `kind: receipt`, with exactly direct project-local `mediaDescriptor` and
  non-empty `resourceId`; or
- `kind: receipt-set`, with no other field, only for
  `operation-receipt-evidence` and derived solely from declared current receipt
  evidence.

Source evidence names only declared Document Project authored authorities.
Artifact evidence names only direct project-contained output evidence.  No
identity may be absolute, traverse a path segment, use a symbolic link, name a
directory, depend on a glob, use mtime/timestamp inference, or be discovered
by scanning an output tree.  A source/artifact hash mismatch is derived as
`stale` when the referenced output remains present, and absence is `missing`.
Receipt currentness is determined solely through existing Cozy Media and
`cozy.media.receipt.v2` currentness logic; this contract neither changes nor
wraps that media schema.

`review` is either exactly `kind: none`, or a `kind: core-dialogue` record for
`content-core` only.  The latter has exact non-empty `provider` and `model`,
direct request and response `{path, sha256}` identities, and exactly one
disposition branch.  `accepted` contains exactly `acceptedAuthority`, whose
identity is the current descriptor Content Core path and SHA-256; `rejected`
contains exactly a non-empty `rejectionReason`.  Provider/model identities are
retained human-acceptance evidence only: no provider is executed and no AI
output becomes autonomous authority.  A changed Core makes accepted review
evidence `stale`; missing or changed request/response evidence likewise makes
the review `stale`.

The shared derived model is the sole source for both the disposable state
snapshot and the dashboard. For every row it independently derives coverage
(`satisfied`, `missing`, `not-applicable`), currentness (`missing`, `current`,
`stale`, `failed`, `nonparticipating`, `not-applicable`), review (`pending`,
`accepted`, `rejected`, `stale`, `not-applicable`), readiness (`blocked`,
`ready`, `failed`, `not-selected`, `omitted`), and a precise reason.
Disabled entries remain `not-applicable`/`omitted` with their selected profile
reason.  A stale dependency propagates to every declared consumer, including
the shared infographic's article, slide, and video consumers, without any
timestamp ordering.  A valid failed retained attempt produces `failed` only
when that Work Product has no current declared product evidence; retained
attempts otherwise remain historical and never imply success.

The canonical state YAML is `cozy.document-project-state.v2`. In addition
to fixed-order authored `sources`, it has a distinct deterministic `evidence`
section for the optional sidecar and retained attempt path/SHA-256 identities,
then immutable `criteria` in Workflow Definition criterion order, followed by
fixed-order `workProducts`.  It contains no absolute path,
timestamp, random value, generated receipt content, or authority override.
Deleting the cache and inspecting unchanged inputs reconstructs byte-identical
bytes.  `inspect` and `verify` write only this disposable cache and never write
durable evidence.

The dashboard is a distinct user-facing projection that derives the identical
shared model without writing the state cache. Its workflow and state tables are
secondary diagnostic projections, not dashboard authority or the Phase 45.1
dashboard redesign. It shows providers, gates, coverage/currentness/review/readiness,
exact reason, and user-facing next action; it keeps retained attempts separate
from current product state. It MUST include a Criterion coverage section stating
`<satisfied>/<total> applicable criteria satisfied` and one accessible table
with criterion, coverage, and reason from the same snapshot.  When present, it shows only the safe public-source mapping.
It visibly labels Project production, workspace integration, aggregate build,
and external delivery as read-only, non-invoked responsibilities.  It remains
self-contained, deterministic, HTML-escaped, and read-only; no external call,
provider execution, registry/site discovery, aggregate build, publication,
deployment, upload, or Article 8 behavior is permitted.

Sidecar absence uses the same v2 source-derived missing/pending projection;
it does not admit a legacy schema or fallback.
This slice does not alter the descriptor, CLI/help grammar, workflow DAG,
profiles, media sources, media receipt/review schemas, external repository
registration, workspace integration, aggregate build, publication, deployment,
or upload.

## Public command grammar and boundaries

The complete public Document Project command grammar is:

```text
cozy document-project inspect <project>
cozy document-project plan <project>
cozy document-project dashboard <project> [--save <dashboard.html>]
cozy document-project review <project> --kind core|slides|video|slide-logical-chart|video-logical-chart [--save <review.html>]
cozy document-project reflect-feedback <project> <feedback>
cozy document-project verify <project>
cozy document-project run <project> --operation <logical-operation> [--dry-run]
cozy document-project scaffold <slug> --profile standard|standard-video|bok|bok-video --language <tag> --workspace directory|bok --save <parent>
```

After the CLI and Phase gates, `<project>` MUST be an existing direct
non-symlink directory whose name ends in `.dox`; it MUST NOT be an arbitrary
descriptor filename.  Within an admitted project package,
`document-project.yaml`, the `content/` directory, and the `contentCore` file
MUST each be a direct regular non-symlink entry.  The lexically exact relative
Core path MUST resolve from that package without escaping it.  A failure of any
of these path-admission requirements MUST reject with `DP-PATH-001` before
descriptor or Core parsing.

When `verify` checks the initial authored source paths, `index.dox`,
`infographic/infographic.svg`, `presentation/visual-pages.yaml`, and
`review/README.md` MUST each be direct regular non-symlink entries contained in
the admitted package.  `video/storyboard.md` has the same requirement only for
any video profile. Their semantic contents and workflow validation
remain deferred.  `dashboard` accepts an optional `--save <dashboard.html>`;
without it, the output MUST be written to the deterministic project-local
`target/document-project/project-dashboard.html`.  An explicit external save
path is used exactly as requested; a Project-internal save path is admitted
only under the projection boundary below. `review` requires one of `core`, `slides`, `video`,
`slide-logical-chart`, or `video-logical-chart` and accepts
the same optional save path; its defaults are
`target/document-project/core-review.html`,
`target/document-project/slides-review.html`,
`target/document-project/video-review.html`, and
`target/document-project/slide-logical-chart-review.html` and
`target/document-project/video-logical-chart-review.html`, respectively. Review
never accepts or exposes a logical-operation identifier.  The command namespace MUST
remain distinct from existing software Project knowledge-package and `cozy
media` commands.  No compatibility alias or ambiguous dispatch is permitted.

Generated dashboard and review destinations MUST be admitted as direct,
non-symlink regular files or absent destinations.  For an absent destination,
the nearest existing parent directory MUST be direct and non-symlinked; only
missing parent components beneath it MAY be created, and they MUST be direct
directories.  Higher pre-existing ancestry is not inspected for this
admission.  Existing regular destinations MAY be replaced only by a
same-directory temporary file moved with `ATOMIC_MOVE`; symlinks, non-direct
destinations, and non-directory nearest existing parents MUST reject with
`DP-PATH-001`.  An implementation MUST NOT fall back to direct writing or a
non-atomic move.  The only generated write for each successful command is its
selected HTML projection.

After normalization, a destination that is inside the admitted Project package
MUST be under `<project>/target/document-project/` and its filename MUST end
exactly in `.html`.  Any other Project-internal `--save` destination MUST
reject with `DP-PATH-001` before parent creation, temporary output, or
publication.  This boundary protects the descriptor, Content Core, article,
Visual Page, infographic, review, video, target state, evidence, and every
other Project-owned path from projection replacement.  An explicit destination
outside the Project retains exact-path behavior, subject to the direct
non-symlink parent and atomic-publication rules above.

`reflect-feedback` consumes one direct, non-symlink structured input file whose
name ends in `.json`, `.yaml`, or `.yml` (case-insensitive); any other suffix is
rejected with `DP-CLI-001` before parsing.  It accepts JSON and YAML and is the
only command in this boundary that may reflect an accepted feedback item into
an authored authority.  JSON and YAML use one common object schema; validation
never derives feedback semantics from the filename suffix.  The complete batch
grammar is:

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
  - target: video
    replacement: proposed storyboard
    applicability: not-applicable
    disposition: not-applicable
    notApplicableReason: profile does not use video
```

The top-level object MUST contain exactly `reason` and a non-empty `changes`
array.  Each item MUST contain exactly `target`, `replacement`,
`applicability`, and `disposition`, plus exactly one conditional reason:
`rejectionReason` for an applicable rejection or `notApplicableReason` for a
not-applicable item.  Targets MUST be unique and drawn from `core`, `article`,
`slides`, `infographic`, and `video`.  `applicability: applicable` permits
only `accepted` or `rejected`; `not-applicable` permits only the
`not-applicable` disposition.  Every item retains its replacement proposal,
including rejected and not-applicable items, so a later amended batch can
accept it.  A Core replacement is exactly `{"accepted":[{"id":"...","text":"..."}]}`;
other replacements are non-empty strings.  Core entry IDs MUST be unique and
its entry text MUST satisfy the closed Core grammar.

The target mappings are `core` to the descriptor `contentCore` path, `article`
to `index.dox`, `slides` to `presentation/visual-pages.yaml`, `infographic`
to `infographic/infographic.svg`, and `video` to `video/storyboard.md`.  An
accepted Core replacement preserves the existing Core envelope
(`schema`, `id`, and `language`) and is revalidated before publication.  For
the `standard` profile, a `video` item MUST be present as
`not-applicable` with its required reason; an applicable video item is
rejected with `DP-OP-001`.  Video profiles admit the active storyboard mapping.

The entire batch, every replacement, the profile rule, and every selected
accepted authority path MUST validate before any write.  Accepted authorities
MUST already be direct, non-symlink regular files with existing direct parent
directories.  Each accepted source is replaced through a same-directory
temporary file and `ATOMIC_MOVE`; there is no direct-write or non-atomic
fallback and no parent creation.  Rejected and not-applicable items never
write an authority.  Malformed combinations and missing/extra item fields
reject with `DP-CLI-001`; malformed JSON/YAML syntax also rejects with
`DP-CLI-001`.  Malformed replacements and duplicate Core IDs reject with
`DP-DESC-001`, missing operands with `DP-CLI-002`, and unsafe input or source
paths with `DP-PATH-001`.

Successful output begins with `Cozy Document Project Feedback Reflection`,
identifies the project, and emits in input order exactly one of
`reflected: <target> <path>`, `rejected: <target> — <rejectionReason>`, or
`not-applicable: <target> — <notApplicableReason>`.  It MUST NOT echo the
batch reason or any replacement.  The command creates no feedback
record, receipt, attempt, state cache, provider run, review, or auxiliary
evidence.  This write boundary is distinct from `review`: review is a
read-only projection and never accepts, persists, or reflects feedback.

- `inspect` MUST inspect and report a derived project view without mutating
  authored authority, durable evidence, registration, or delivery.  The
  successful command MUST regenerate only the disposable snapshot cache
  specified above, and MUST identify its project-relative location in output.
- `plan` MUST resolve the closed reusable definition and report deterministic
  `required`, `active-optional`, `inactive-optional`, and `profile-disabled`
  Work Product lines and `blocked` and `eligible` logical-operation lines
  without mutating authored authority, project state, evidence, registration,
  or delivery. `eligible` means only that an operation produces a selected Work
  Product; it is not runtime readiness. For `standard`, the profile-disabled
  video lines MUST include exactly `profile standard disables video branch`; for
  `standard-video`, that text MUST be absent.
- `dashboard` MUST generate a deterministic, self-contained, read-only HTML
  projection.  With no `--save`, it MUST use
  `target/document-project/project-dashboard.html`; with `--save`, it MUST use
  exactly the requested path when external, or the requested `.html` path under
  `target/document-project/` when Project-internal.  Any other Project-internal
  destination MUST reject with `DP-PATH-001` before creating a parent or
  temporary output. Its Workflow view MUST show the four selection states,
  Work Products, provider bindings, and gates. Its Work Product matrix MUST
  show coverage, currentness, review, readiness, and nonparticipating/blocking reasons.
  Its Work Product details MUST show dependencies, producer and consumer
  operations, evidence references, and a user-facing next action. It MUST make
  the current project state and the next required or useful action clear without
  requiring the reader to interpret a logical-operation identifier. Disabled video
  status MUST remain visibly profile-disabled, never complete. Core and slide review,
  Slide Logical Chart, and Video Logical Chart are distinct `review-projection`
  Work Products. The slide chart derives from Content Core and Visual Page IR;
  the video chart derives from those inputs plus storyboard IR and is disabled
  for no-video profiles. Current snapshot data
  MUST be distinguished from retained historical attempts; initial attempts
  have no receipt or currentness authority.  It MUST execute no provider and
  persist no authority, candidate, feedback, acceptance, receipt, deliverable,
  workspace, build, publication, deployment, or upload state. The Dashboard
  MUST link the required final infographic SVG for direct review; its href is
  relative to the selected dashboard output parent. It links a default review
  HTML only after that HTML exists. Until each review-projection Work Product's
  corresponding default HTML exists, its dashboard coverage/currentness/
  readiness MUST be `missing`/`missing`/`blocked`, with exactly
  `source or retained evidence is not present` when any declared source
  prerequisite is absent, or exactly `default review HTML is not generated`
  when all declared source prerequisites are present; it MUST also show a
  user-facing generate action. After the default HTML exists, the dashboard may
  show `satisfied`/`current`/`ready`. An explicit disabled binding reason MUST
  take precedence over these blocked reasons.
- `review` MUST generate a deterministic, self-contained, read-only HTML
  projection at the kind-specific default or exact optional external save path;
  an optional Project-internal save path MUST be under
  `target/document-project/` and end exactly in `.html`, with all other
  Project-internal destinations rejected by `DP-PATH-001` before any parent or
  temporary output is created.  Core
  review MUST present accepted Core entries and explicitly mark candidate,
  feedback, and acceptance as non-authoritative and not yet persisted. Slide
  review MUST present Visual Page IR. Video review and Video Logical Chart MUST
  be admitted only for video profiles using the `DP-OP-001` disabled
  operation/profile diagnostic otherwise, and MUST present storyboard and
  Visual Page source projections when active. Slide Logical Chart MUST
  visualize current Content Core and Visual Page IR. Video Logical Chart MUST
  additionally visualize storyboard IR. Each chart MUST state that it is not an authority, provider run, receipt,
  state cache, feedback record, or write-back mechanism.  All supplied values
  MUST be HTML-escaped.  Review MUST claim no provider execution, candidate or
  feedback persistence, acceptance, receipt, or deliverable.  It MUST not
  modify Content Core, accepted entries, storyboard, Visual Pages, attempts,
  receipts, or state cache.
- `verify` MUST inspect declared project material for conformance without
  mutating authored authority, durable evidence, registration, or delivery.
  After successful validation it MUST regenerate only the disposable snapshot
  cache specified above and MUST identify its project-relative location in
  output.  Failed validation MUST create no new cache.
- `run --operation` MUST validate the descriptor, Core, and all command-admitted
  initial authored sources before any evidence write.  It MUST admit exactly
  one declared logical operation that produces a selected Work Product. A normal
  eligible run MUST record one append-only attempt and MUST NOT invoke a
  provider, generate a deliverable or receipt, write the Core or state cache,
  or infer downstream work.  `--dry-run` uses the same admission and reports
  the operation, provider, and profile without creating an attempt or cache.
  An unknown operation or a declared operation for an inactive optional or
  profile-disabled Work Product MUST reject with `DP-OP-001`; malformed or unsafe project input retains its
  earlier descriptor/path diagnostic precedence.  Rejection MUST create no
  attempt, receipt, generated state, dashboard, registry, delivery, or output
  file, and MUST not infer downstream publication, workspace-wide execution,
  aggregate build, registration, deployment, or upload.
- `scaffold` MUST create only the authored initial package described below.  It
  MUST NOT register, build, publish, deploy, upload, or create delivery
  evidence implicitly.

Project production, workspace integration, aggregate build, and external
delivery MUST remain separate responsibilities.  A project command MUST NOT
silently cross from one responsibility into another.

## Scaffold boundary

`scaffold` MUST atomically create a profile-sensitive `<parent>/<slug>.dox/`
authored package.  An unsafe, outside-parent, symlink, or non-direct scaffold
parent or destination is a `DP-PATH-001` rejection.  Once those path checks
pass, an existing destination, an absent or non-real parent, or inability to
complete the required atomic move is a `DP-SCAFFOLD-001` rejection.  It MUST
NOT merge or overwrite, and MUST NOT substitute fallback copy for the required
atomic move.  It MUST resolve the common workflow by identity rather than copy
the workflow DAG.

The `standard` profile MUST create exactly these authored paths:

```text
<slug>.dox/
  document-project.yaml
  index.dox
  content/
    core-<language>.yaml
  infographic/
    infographic.svg
  presentation/
    visual-pages.yaml
  review/
    README.md
```

The `standard-video` and `bok-video` profiles MUST create exactly the
`standard` paths plus `video/storyboard.md`; `bok` creates the same paths as
`standard`. `simplemodeling-org` profiles are registered but hidden and MUST
NOT be offered by scaffold or public help. Omitted branches MUST
NOT receive fake completed artifacts.  Scaffold MUST preserve regular
`index.dox` article-expression authority and MUST NOT perform SmartDox host
discovery or source projection.  It MUST write no `target/`, generated state,
receipt, approval, Operation Attempt, dashboard, registry, publication,
deployment, upload, or delivery evidence.

The scaffolded Core MUST initialize `accepted: []`.  It is therefore an actual
minimal semantic authority from creation, not merely an identity placeholder.

## Validation, diagnostics, and success output

A rejected command or input MUST report exactly one stable error token and one
human-readable cause.  The ordered diagnostic gates below are authoritative:
the first matching gate is the sole emitted token, and every later gate runs
only after all preceding gates have passed.  A token MUST NOT be combined with
another token.  This Slice does not prescribe an exception class or exit code.

| Order | Token | First matching rejection condition |
| --- | --- | --- |
| 1 | `DP-CLI-001` | Unknown command or subcommand, unsupported option, or extra or otherwise invalid command grammar for a command form. |
| 2 | `DP-CLI-002` | A known syntactically valid command form lacks a required positional argument or required option/value. |
| 3 | `DP-PHASE-001` | Historical Phase-42-only behavior: a syntactically complete dashboard request was rejected before path resolution.  Phase 42.1 no longer emits this token for dashboard. |
| 4 | `DP-PATH-001` | A command whose preceding gates passed has an unsafe, outside-package, symlink, or non-direct path, including a project, descriptor/Core source, verified authored source, or scaffold parent/destination path that fails path admission. |
| 5 | `DP-SCAFFOLD-001` | `scaffold` has safe paths, but its destination already exists, its parent is absent or non-real, or the required atomic move cannot be completed. |
| 6 | `DP-DESC-001` | An inspected, planned, verified, or run project has a missing, unreadable, or malformed descriptor or Core after path admission, or a `reflect-feedback` request has a malformed replacement or an accepted Content Core source that is missing, unreadable, or malformed. |
| 7 | `DP-DESC-002` | A successfully parsed descriptor or Core has an unknown field or unsupported or invalid closed value or relationship after descriptor admission. |
| 8 | `DP-OP-001` | A path- and descriptor-valid `run` request names an unknown or undeclared logical operation, or a declared logical operation disabled by the selected profile, or a path- and descriptor-valid `reflect-feedback` request declares a video mapping that is disabled by the selected profile. |

Successful `inspect`, `plan`, `verify`, and `scaffold` output MUST begin with,
respectively, `Cozy Document Project Inspect`, `Cozy Document Project Plan`,
`Cozy Document Project Verify`, and `Cozy Document Project Scaffold`.  Each
such success output MUST identify the project and schema.  Scaffold success
output MUST also identify package, workflow, profile, and workspace.  A
successful dashboard MUST begin with `Cozy Document Project Dashboard` and a
successful review MUST begin with `Cozy Document Project Core Review`,
`Cozy Document Project Slide Review`, `Cozy Document Project Video Review`,
`Cozy Document Project Slide Logical Chart`, or `Cozy Document Project Video Logical Chart`;
each MUST identify the project, profile, schema, and selected output.  Dashboard
and review HTML MUST be UTF-8,
self-contained, deterministic for unchanged inputs, structurally accessible
with headings and tables, and HTML-escape authored or descriptor values.

## Non-goals and deliberate deferrals

This baseline MUST NOT be read as authorizing autonomous acceptance, mutable
progress/status authority, a scheduler, daemon, arbitrary command execution,
dashboard write-back, implicit registration/build/publish/deploy/upload,
migration, or Phase 41 expansion.

The closed `cozy.document-project.v2` descriptor fields are not deferred or
expandable. Phase 45 closes the workflow-owned Work Product, provider-binding,
deliverable-disposition, criteria/gate, evidence-reference, and operation
model only as the static in-code definition specified above.  SmartDox source
projection and host discovery, and external receipt schema changes remain
deferred.  Exactly one standalone local `standard-video`/`directory` driver
and one isolated non-Article-8 `bok`/`bok` driver are accepted only through the
bounded local command scenarios in this contract; that acceptance does not
admit host discovery, registration, external receipts, Article 8, migration,
aggregate build, publication, deployment, or upload.  DP42-03B implements the deterministic disposable
state reconstruction specified above, DP42-03C implements recorded append-only
attempts, and DP42-03D implements sidecar-bound receipt/currentness and
evidence-based stale propagation; later Phase 42.1 Slices own the remaining
capabilities.  They consume the closed
DP42-02 definition and MUST NOT expand descriptor fields or redefine profiles,
Work Product roles, criteria, gates, operations, or provider bindings.  This
specification MUST NOT be represented as implementing, accepting, or proving
compatibility for those deferred matters.

## Related authorities

- Stable design: [Document Project Design](../design/document-project.md)
- Phase plan: [Phase 42](../phase/phase-42.md)
- Progress ledger: [Phase 42 checklist](../phase/phase-42-checklist.md)
- Retained media contract: [Media Package specification](media-package.md)
  and [Media Package operation design](../design/media-package-operation.md)
- Retained Visual Page contract: [Visual Page specification](visual-page.md)
  and [Visual Page design](../design/visual-page.md)
- Planning input only: [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
  and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md)
