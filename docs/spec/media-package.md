# Media Package Specification

## Purpose

A Media Package binds reproducible media representations to one BoK knowledge unit without coupling the source package to a particular website, archive directory, or publication service.

## Descriptor

The descriptor file is named `media.yaml`, `media.yml`, `media.json`, `media.conf`, or `media.xml`. The initial schema identifier is `cozy.media.v1`.

```yaml
schema: cozy.media.v1
knowledge:
  id: development-process/example
  type: documentmodel:KnowledgeUnit
  source: ../../../doxsite/development-process/example.dox
languages: [ja, en]
profiles:
  simplemodeling-org:
    root: ../../../../..
resources:
  - id: web-ja
    kind: image
    language: ja
    role: web-summary
    source: infographic/web-ja.svg
    output: infographic/web-ja.png
    build: svg-to-png
    width: 1600
    height: 900
    publications:
      simplemodeling-org: src/main/doxsite/development-process/images/example/summary-ja.png
```

Paths are resolved relative to the descriptor. Absolute publication paths are rejected. Machine-specific archive roots belong in profile configuration and not in a Media Package.

### Article PDF resources

`articleMedia.role` has two additive SmartDox PDF discriminators: exactly
`article_pdf` and `summary_slides_pdf`. Each is a `kind: document` resource
with `language: ja` or `en`, a direct explicit site-visible `articleMedia.publicPath`,
and `articleMedia.mediaType: application/pdf`; `articleMedia.label` is optional
but, when present, is a nonblank exact value. Cozy never infers a PDF role,
public path, or locale from a filename or path.

Only `build: article-pdf` is implemented in this step. Its `source` is exactly
the descriptor `knowledge.source`, its `output` is a declared direct regular
PDF destination contained by the descriptor root after normalization with no
existing symlinked ancestor below that root, and its closed configuration has exactly `latexFormat`,
`infographic`, and `renderer`:

```yaml
articlePdf:
  latexFormat: business # standard | business
  infographic: infographic-ja
  renderer:
    name: smartdox-pdf
    version: 2.4.18-SNAPSHOT
    command: [smartdox]
```

`infographic` is a nonblank exact resource ID. It names a distinct declared
resource with `kind: infographic`, `articleMedia.role: infographic`, a source,
and the same required `ja` or `en` locale as the article-PDF resource. That
resolved source must be a direct regular non-symlink file. This is an explicit
article-PDF authority binding, not an additional renderer operand.

The renderer command is the configured exact argv tokens followed without a
shell by `<source> --output <staged-output> --locale <ja|en> --latex-format
<standard|business>`. Cozy never passes the infographic path to the renderer.
It accepts only a zero-exit direct regular `%PDF-` staged file, revalidates the
bound infographic source and full receipt input identity, and atomically
replaces the declared output only then. The existing receipt-v2 automatic
source evidence therefore includes the selected infographic authority without
a receipt schema change. Failure, invalid output, or an article/infographic
input race preserves the previous output and creates no fresh receipt. Its
resource entry records the accepted relative output path and receipt evidence. An absolute output
is rejected during descriptor path resolution; an output whose normalized
effective destination traverses or otherwise escapes the descriptor root is
rejected with the stable Article PDF output-escapes-descriptor-root diagnostic;
and an output with an existing symlinked ancestor below that root is rejected
by Article-PDF descriptor validation. Each rejection occurs before renderer
invocation, staging, or output replacement.

`summary_slides_pdf` is a declared public-resource role only in this step.
Its Visual Page/slide-IR conversion, page verification, and any internal PPTX
handling are a later Phase 40 handoff; this route neither converts nor exposes
a public PPTX.

### Receipt Evidence

`cozy.media.v1` remains the descriptor schema. Additive presentation fields preserve every legacy descriptor. A descriptor may add one strict optional `receipt` block and a profile may add a strict optional `presentation` block:

```yaml
receipt:
  inputs:
    - id: slide-ir
      role: slide-ir
      path: slides/normalized.json
      normalization: structured-document
    - id: presentation-template
      role: template
      path: slides/template.pptx
      normalization: bytes
  producer:
    profile: article-media-v2
    renderer:
      name: approved-presentation-renderer
      version: 1.2.3
```

The only receipt keys are optional `inputs` and optional `producer`. Each input has exactly `id`, `role`, `path`, and `normalization`; IDs and roles are non-empty trimmed identities, paths are descriptor-relative direct regular non-symlink files when evidence is captured, and normalization is exactly `bytes` or `structured-document`. Input IDs are unique. `cozy:` is reserved for Cozy automatic evidence and an authored input must not use that prefix. A producer, when present, has exactly `profile` and `renderer`; the profile and the renderer `name` and `version` are required exact identities.

Cozy records non-hash input metadata for the descriptor, knowledge source, declared
resource source/project and effective profile. Authored receipt inputs join that
metadata. Paths remain descriptor-relative; input evidence has only `id`, `role`,
`path`, `normalization`. `structured-document` still validates supported syntax,
but neither normalization mode computes a content identity.

Generation reuse uses producer input files and their actual FileTime through
`CozyFileUpdatePolicy`. A `copy` consumes only its direct resolved source.
A `prebuilt` consumes that source and, for a site-video resource, its explicitly
named `articleMedia.production` file; their times and the output time must not
exceed `acceptedAt`. Knowledge, unrelated sources, receipt declarations,
profiles and site configuration remain package-wide informative evidence and
current-command validation inputs, rather than copy/prebuilt conversion inputs.
Other producers retain their existing input selection in this Slice.
Active build/publication commands validate every declared input and observe
paths and FileTime in memory before and after work to reject input races.
Those observations are not serialized as another dependency model.

Every newly accepted entry in `target/cozy-media/manifest.json` has `id`,
relative `path`, a `cozy.media.receipt.v2` receipt and optional presentation
`artifacts`. The manifest remains `cozy.media.v1`. The receipt has sorted input
metadata, producer metadata, the build operation with selected target/profile,
UTC ISO-8601 `acceptedAt`, and `{status:"valid",findings:[]}` verification.
Stored manifests, entries, receipts and input evidence load their known typed
fields directly, ignoring arbitrary extra stored properties without parsing or
re-emitting them. Descriptor configuration remains closed and strict. Known
schemas, types, IDs, ordering, verification, UTC timestamps and paths remain
validated. No accepted primary-output digest is emitted or compared.

`current` is a read-only producer-freshness Boolean. `evidenceCurrent` requires
matching knowledge/resource/output identity, a valid receipt, the selected
automatic source/project path mappings and the paired site context/path mappings
when required by the accepted receipt. It does not compare whole historical
input sets. `currentnessFailure` combines those gates and reports the concrete
reason, media receipt currentness stage, ID, actual failing input/output or
manifest path, producer kind, and `cozy media build <descriptor> --target <id>`.
Verification and receipt preflight use that diagnostic through `requireCurrent`.
PDF output headers are validated for generated PDFs and document resources with
`article_pdf` or `summary_slides_pdf` roles, including copy outputs.
A valid byte rewrite with deliberately preserved timestamps has no promise of
digest detection.

Acceptance prepares review states and receipts before writing, installs states
first and makes the receipt the final visibility record. In-process failure
restores prior bytes/existence; dry run and failed work expose no fresh receipt.
Target builds preserve unselected entries without reaccepting them. Explicit
prebuilt build adopts valid output even when the read-only plan reports current.
Real source-to-staged-to-destination publication byte integrity, safe paths and
atomic installation remain required. Presentation subordinate `Artifact`
records contain only `id`, `role`, and `path`. Current artifact evidence compares
these typed ordered records with the actual structurally verified generation;
it uses no persisted output or source content identity.

### PDF review currentness state

`target/cozy-media/pdf-review-state.json` is a package-owned, receipt-derived
currentness document with the exact closed schema
`cozy.media.pdf-review-state.v1`. It has no descriptor configuration and is
prepared from the same merged candidate `cozy.media.v1` manifest that a normal
`cozy media build` atomically accepts. Acceptance installs it before the final
receipt through the existing rollback-safe receipt transaction. An absent state
target is created only when the candidate has at least one current qualifying
PDF resource; an existing target must be a direct regular non-symlink file.

The saved object has `schema`, `knowledge`, and `resources`, ordered by
`(role,id)`. Each resource saves only `id`, `role`, `language`, `publicPath`,
`mediaType` and relative `output`. Known typed fields load directly with valid
schema/knowledge, role and locale enums, `application/pdf`, safe paths and unique
IDs; arbitrary extra stored properties are ignored. No whole-JSON comparison or
state/output/renderer digest is used.

`requireCurrent(plan, resources)` checks only the selected qualifying PDFs.
It requires their ordinary receipt currentness, a valid direct state file and
matching knowledge, then matches each selected ID's known metadata to the
resolved resource and accepted entry. A stale unselected Japanese PDF cannot
veto unchanged selected English verification or publication preparation.
`current(plan)` checks all qualifying PDFs through the same logic and does not
gate a smaller selection. Missing or altered selected metadata reports PDF
review currentness, resource ID, actual state/output path, producer kind and the
next build operation.

Direct `summary-slides-pdf` resources require current dependency receipts and
the nonhash page count/order/image/renderer structural verifier specified below.
This direct renderer subsystem is migrated under S07.
Prebuilt summaries require their ordinary receipt and known state metadata;
they do not opt into direct structural verification. Candidate acceptance uses
the merged manifest without requiring prior on-disk receipts or freshness
against just-generated outputs. A stale unselected direct summary is omitted
from a target article refresh rather than accepted again.

Normal verify and publication preflight require this state only for selected
qualifying PDFs. Presentation-only slide build remains independent. The state
performs no registration, publication or semantic/visual/audiovisual approval;
existing presentation/video review and cross-review schemas are unchanged.

`prebuilt` uses explicit adoption of its current regular-file output, retaining
the consumer's existing safe in-root symlink support. A prior
receipt/input mismatch is not an adoption veto, even when output bytes and mtime
are unchanged. A normal explicit build records current input evidence for the
selected resource without regenerating that external output. Planning remains
read-only and distinguishes Current from AdoptPrebuilt; missing source remains
MissingSource. Target builds do not reaccept unselected resources.

Site-bound article media retains current file/descriptor validation independently
of historical receipt equality. Article and summary PDF outputs require a PDF
header. Infographic outputs declared as `image/svg+xml`, or with an `.svg`
public path and no media type, require well-formed XML with an SVG root;
other infographic outputs retain the PNG header check. SVG parsing does not
resolve external entities. This generic adoption does not change the separate
Phase 26 PNG-only publication contract. A video requires a direct
non-symlink MP4 path and direct `articleMedia.production` JSON with matching
article identity and language, completed render and passed technical-and-visual
QA. These known identity/status/QA fields are loaded directly; arbitrary extra
production properties do not veto adoption. Updated valid production metadata
is allowed. Real distribution byte integrity remains a separate consumer contract. No previous/current receipt equality, duplicated
production-metadata equality, or unchanged `final-summary-slides-pdf` proof is
required for adoption. Historical source-authority/video-manifest proof fields
are not adoption prerequisites. Missing or malformed required inputs, invalid paths or
formats, and invalid current video production fail without replacing prior
acceptance. Publication input-race and destination-integrity checks are unchanged.

### Presentation

Presentation resources use `kind: presentation` and `build: presentation`. They require a language, versioned slide IR source, PPTX output, a publication entry for the selected presentation profile, and this exact nested resource object:

```yaml
presentation:
  profile: business
  slideImages: target/cozy-media/slides
  montage: target/cozy-media/montage.png
  rendererManifest: target/cozy-media/renderer-manifest.json
  reviewManifest: target/cozy-media/review-manifest.json
  reviewState: review/state.yaml
  articlePdf: article-pdf-ja
  infographic: infographic-ja
```

The referenced article PDF is a declared non-presentation document resource with a publication for `business`; the infographic is a declared non-presentation image resource and, when it declares a language, uses the presentation language. Neither dependency may be the presentation itself. The profile object is exactly:

```yaml
presentation:
  template: presentation/template/business.pptx
  renderer:
    name: approved-presentation-renderer
    version: 1.2.3
    command: [approved-presentation-renderer]
```

`template` resolves descriptor-relative and must be a direct regular non-symlink file. `command` is a nonempty vector of nonempty exact argv tokens. Cozy invokes it without a shell, in the descriptor root, with the configured tokens followed by `render --media <absolute descriptor> --target <id> --profile <profile> --slide-ir <absolute source> --template <absolute template> --pptx <absolute output> --slide-images <absolute directory> --montage <absolute path> --manifest <absolute renderer-manifest>`.

The slide semantic source is YAML or JSON with schema `cozy.slide-ir.v1`. Its exact top-level keys are `schema`, `knowledge`, `language`, and `slides`; slide keys are `id`, `title`, `layout`, and `elements`; element keys are `role` plus exactly one of `text` or `asset`. Layouts are `title`, `section`, `content`, `comparison`, `figure`, and `summary`. Roles are `title`, `subtitle`, `body`, `bullet`, `figure`, `caption`, `source`, `footer`, and `logo`. `figure`/`logo` use only assets; all other roles use only text. Slide IDs match `[a-z0-9][a-z0-9._-]*`, are unique, and each slide has exactly one title element equal to its title. Inputs reject empty or untrimmed identities, coordinates, unknown fields, missing assets, and `TODO`, `TBD`, `PLACEHOLDER`, `FIXME`, or `{{...}}` text. The IR knowledge/language match the descriptor knowledge/presentation language and it must reference the configured infographic.

The renderer writes `cozy.presentation.render.v1` with required top-level
`schema,target,profile,renderer,pptx,slides,montage`. The profile is exactly
`business`; renderer requires `name,version`; PPTX and montage require `path`;
each slide requires `id,title,path,assets`, with ordered unique `{id}` assets.
Readers select the known fields positively at every level and ignore arbitrary
unspecified metadata, without a named historical-field mask. Missing, ill-typed,
wrong, duplicate, or reordered authority IDs still reject. All serialized paths
are descriptor-relative forward-slash paths. No Presentation input, template,
output, slide, montage, article-PDF, infographic, renderer or review digest is
calculated or required. Cozy checks actual output integrity; securely parses
`[Content_Types].xml`, `ppt/presentation.xml`, presentation and per-slide
relationships; derives relationship-ordered contiguous slide parts; and binds
each slide's embedded media to safe direct selected asset files. Actual bytes
must agree as a multiset, with equal cardinality and one-for-one byte consumption
preserving duplicate-content multiplicity; media or ZIP order is not authority.
These transient structural checks preserve every existing relationship,
slide-order, XML, linkage and packaging gate. Hostile XML, renderer failure,
incomplete output, malformed required evidence, input race, unsafe paths and
overlap still reject. Authored IR and descriptor configuration stay closed.

### Summary-slides PDF

A `summary_slides_pdf` document resource may remain `build: prebuilt` for an
already-produced direct PDF. Its only new generation route is
`build: summary-slides-pdf`; it requires a `summarySlidesPdf` object with the
closed common keys `contract`, `profile`, `slideImages`, `montage`,
`rendererManifest`, `articlePdf`, and `infographic`, plus optional `pptx`.
`slide-ir-v1` uses the common keys; `visual-page-v1` additionally requires
`catalog` and `binding`. The profile is exactly `business`, `articlePdf` and
`infographic` are current declared resources, and the source is respectively a
current Slide-IR or Visual Page authority. The direct route invokes the existing
presentation renderer/template with a staged `--pdf` path, validates the PDF
header and actual PDFBox page count, and atomically installs the primary PDF
only after renderer evidence and input-race checks pass.

Both renderer schemas require the relative root field order
`schema,target,profile,renderer,pdf,slides,montage[,pptx]`:
`cozy.summary-slides.render.v1` for Slide-IR and
`cozy.summary-slides.render.v2` for Visual Page. Readers positively select
required known fields and ignore arbitrary extra object metadata at every
level, without a named legacy mask or content identity. Missing, null or
ill-typed required fields reject. Renderer `name,version` must exactly match
the current profile. `pdf` requires only a positive integer `pageCount`, with
no required path, and must agree with both the actual PDFBox page count and
the current authority's page count. Slide-IR slides require
`id,title,path,assets`; Visual Page slides require `id,path,assets`. Slide/page
IDs must follow the unique authority order; v1 titles must match. Each asset
requires a typed exact `id` in unique authority order. The montage requires
`path`. A configured PPTX requires `path`; an unconfigured `pptx` field is
forbidden even though arbitrary other metadata is ignored. Authored
`summarySlidesPdf` configuration retains its closed grammar.

Actual current Slide-IR/PageSet/catalog/Binding loading remains required.
The primary output must be a direct regular PDF with a PDF header and a
successful PDFBox parse. Slide images must be safe direct regular files below
the configured image directory with the existing PNG signature, and the
montage must use its configured safe direct PNG path. The optional PPTX is an
internal direct-file sidecar at the configured path; this contract adds no
OOXML or render-equivalence promise and no public resource or registration
identity. Visual Page assets resolve safely from the actual PageSet parent;
media types and raw asset bytes must agree with the selected declared safe
resources. This is current structural QA and no persisted material identity.
Input/output collisions, unsafe descriptor-relative spellings, missing direct
inputs and symlink paths continue to reject.

Rendering compares sorted operation-local Path/FileTime observations over
its exact current input set before and after the renderer, and also retains
the current receipt-input FileTime comparison. These observations are
transient; no timestamps, byte fingerprints or hashes are serialized as
renderer identity. Changed observed times or missing/unsafe inputs reject
before installation. Valid edits preserving timestamps are admissible and
have no historical-content-detection guarantee; malformed PDFs, wrong actual
page counts and invalid required current structure still reject at that time.

The staged primary is installed by same-parent ATOMIC_MOVE only after all
checks succeed. Modeled pre-install failures preserve the actual prior primary
PDF bytes and receipt and clean the staged primary. Renderer manifests, PNGs,
montage and optional PPTX remain direct outputs: an invoked renderer's failure
has no sidecar atomicity or rollback guarantee. Building either the prebuilt
or direct route does not imply BoK publication or SmartDox registration.

The SummarySlidesPdf renderer subsystem is migrated under S07. ProducerSource
and BoK metadata, generated rsync, final named-integrity audit, EnglishPDF and
complete isolated-site proofs remain separately unfinished. This migration
claims no Phase inventory, Step or Phase closure, or integrity exemption.

`target/cozy-media/manifest.json` retains its `cozy.media.v1` top-level shape.
A resource may add `artifacts`, whose emitted records contain exactly
`id,role,path`. Ordinary readers require these typed fields and ignore arbitrary
extras. Presentation evidence includes slide PNGs, montage, renderer manifest
and the Cozy review manifest. Non-Presentation resources omit `artifacts` rather
than writing an empty field. Safe paths, unique identities and roles, ordered
artifact correspondence, producer/dependency admission and actual modification
time/generation-companion currentness remain required.

After renderer verification Cozy deterministically writes
`cozy.media.presentation-review.v1` before receipt acceptance. Its required
fields are `schema,target,knowledge,language,renderer,slideIr,template,pptx,
slides,assets,montage,articlePdf,infographic,verification`. `slideIr`, `template`
and `pptx` contain `{path}`; slides contain `{id,title,path}`; material assets
are ID-sorted `{id}`; montage contains `{id,role,path}`; article PDF and
infographic contain `{id}`. Renderer retains `name,version` and verification
retains `status:valid,findings:[]`. Verification reconstructs those required
semantics recursively, selecting only known fields from stored objects and
preserving array order. Wrong titles, paths, assets, renderer or status reject
even at an unchanged modification time. Arbitrary extras are ignored at every
object without parsing or re-emitting them. No historical byte identity replaces
removed hashes. AI/Codex remains responsible for semantic comparison; Cozy never
self-approves alignment. The scaffold-only
`cozy.media.presentation-review.scaffold.v1` marker retains its closed
`schema,target,knowledge,language,status:not-rendered` grammar and is replaced
by verified evidence before receipt acceptance.

`review/state.yaml` uses `cozy.media.review-state.v1` and exact keys `schema`, `current`, `last_aligned`, `selected_authority`, and `sync`. Snapshots require only UTC ISO-8601 `acceptedAt`; arbitrary snapshot extras are ignored and never re-emitted. The top-level state grammar stays closed. A successful presentation build refreshes `current` with the verified review manifest's modification time (the last-written generation boundary); it preserves `last_aligned` and `selected_authority`. Alignment remains valid only when the generation is no newer than the explicit alignment time. Unchanged reuse preserves alignment; regeneration after alignment becomes stale even when bytes are identical. `cozy media review align` records the current human/AI decision time and selected authority. Ordinary media verification and slide verification require an aligned state and a review manifest no newer than `current`. Actual output integrity and structural verification remain separate; no aggregate content identity is calculated for alignment.

### Visual Page coexistence boundary

This accepted presentation contract remains the `cozy.slide-ir.v1` source,
`--slide-ir` renderer argument, and `cozy.presentation.render.v1` receipt
route. It is unchanged by the implemented discriminated contract in
[`visual-page.md`](visual-page.md); no existing `cozy.media.v1` presentation
resource, receipt, or review state silently opts into that route.

The preceding legacy `presentation` object is an untagged closed object with
exactly `profile`, `slideImages`, `montage`, `rendererManifest`,
`reviewManifest`, `reviewState`, `articlePdf`, and `infographic`. It never
permits `contract`, `catalog`, or `binding` and never infers a new route from
its source or profile.

The following separately closed discriminated Visual Page presentation object
is implemented. Its resource `source` remains required and is
the VisualPageSet input; all legacy artifact and dependency fields retain their
existing meanings.

```yaml
presentation:
  contract: visual-page-v1
  profile: business
  catalog: presentation/catalog.json
  binding: presentation/binding.json
  slideImages: target/cozy-media/slides
  montage: target/cozy-media/montage.png
  rendererManifest: target/cozy-media/renderer-manifest.json
  reviewManifest: target/cozy-media/review-manifest.json
  reviewState: review/state.yaml
  articlePdf: article-pdf-ja
  infographic: infographic-ja
```

This object is closed with exactly the shown keys; `contract` is exactly
`visual-page-v1`, and `source`, `catalog`, and `binding` are safe descriptor-
relative direct regular non-symlink files under the descriptor root. Each
rejects an empty, absolute, traversal, URI-like, control-character, or
backslash path; normalization changes; symlink escapes; and a missing or
nonregular resolved file. `source` must parse as exactly
`cozy.visual-page-set.v1` with integer version `1`. Its VisualPageSet pages
must declare one shared catalog `{id, revision}` pair, and resolution accepts
exactly one supplied resolved catalog matching that pair;
a mixed pair, unresolved catalog, or mismatch fails closed. It never permits
the untagged legacy shape or `--slide-ir` inference. Its `profile` remains the
exact existing template/renderer object. Its fixed renderer argv is the legacy
fixed argv with `--slide-ir <source>` replaced by `--visual-page-set <source>
--catalog <catalog> --binding <binding>`.

The renderer manifest is `cozy.presentation.render.v2` with required relative
field order `schema,target,profile,renderer,pptx,slides,montage`. Renderer is
`{name,version}`, PPTX and montage are `{path}`, slides require ordered
`id,path,assets`, and assets require `{id}`. Positive selection of known fields
ignores arbitrary extras at all those levels. Actual named
PageSet/catalog/Binding validity, required page IDs/order/assets, output paths,
PPTX relationship/media structure, PNG validity, and current declared-byte
agreement remain. Renderer files are direct outputs; this contract promises no
new atomic transaction for those primary files. Invalid required renderer
evidence rejects before replacing accepted review/receipt records; acceptance
installation preserves prior receipt and review-state bytes on its modeled
failure paths.

The deterministic `cozy.media.presentation-review.v2` wrappers for
`visualPageSet`, `catalog`, `binding`, `template`, and `pptx` contain `{path}`.
Its required root fields are `schema,target,knowledge,language,renderer,
visualPageSet,catalog,binding,template,pptx,slides,assets,montage,articlePdf,
infographic,verification` in their existing relative order. Slides are
`{id,path}`; assets are `{id,path,mediaType}`; the other records use the same
nonhash grammar as v1. Recursive positive known-field projection verifies
actual required values and array order, including nested paths and media types.
Private evidence contains only `(id,assets)` pages
and `(reviewPath,pages)`. No complete semantic JSON replacement gate exists.
Renderer argv is unchanged and external renderer implementation/acceptance is
not claimed. Artifact/review readers emit no hashes or complete semantic copies.
Timestamp-preserved valid edits are admissible and are not promised historical
content detection. Actual descriptor/source/catalog/Binding files remain
required. The SummarySlidesPdf renderer subsystem is migrated under S07.
Publisher/rsync, the final named-integrity audit,
English-PDF and complete isolated-site drivers remain pending; this contract
claims no Step or Phase closure.

### Cross-media review

The closed Cross-media Review route proves that one current normalized Visual
Page is the common semantic input of an already verified Visual Page
presentation and an already verified Storyboard v2 visual-page review. It is
not a renderer, a video build, a publication route, or an approval operation.
Its exact commands are:

```text
cozy media cross-review build <media-file> --target <presentation-id> --video-project <project-file> --save <output.json>
cozy media cross-review verify <media-file> --target <presentation-id> --video-project <project-file> --cross-review <input.json>
```

`target` selects exactly one current `presentation` resource whose closed
`presentation` object has `contract: visual-page-v1`. The route requires that
resource's current receipt and deterministic
`cozy.media.presentation-review.v2` manifest, but it does not require or
create a semantic-alignment approval. `video-project` must have a current
Storyboard v2 review-evidence/handoff package; Cozy revalidates the project's
declared current Storyboard, literal screen references, VisualPageSet/catalog/binding,
selected assets, and effective renderers before accepting it as an input.
Neither a stale presentation review nor a malformed, missing-input, or
wrong-schema Storyboard package is a Cross-media input.

The build output is the canonical `cozy.media.cross-review.v1` object:

```text
schema,target,slides,visualPages,verification
```

Slides contain `{id}`; visual pages contain
`{sceneId,pageId,assets}` with `{id,mediaType}` assets. Ordinary readers select
known fields at Presentation wrappers/slides, Storyboard evidence/handoff and
visual-page entries, and aggregate root/slide/page/asset/verification objects.
Unspecified properties of any JSON type are ignored generically, preserving
existing relative known-key order. Generated output is deterministic and omits
hashes and copied PageSet/catalog/Binding proofs. CrossReview computes no digest and performs no duplicate
Storyboard identity check; the video-owned review-input API validates actual inputs
and record structure without digest-based approval or historical whole-payload
gates. Current per-side page IDs, declared ordered unique asset IDs/media types,
and safe actual bytes are resolved by the existing operation; no complete
catalog/page/Binding correspondence gate replaces the deletion.

Verification checks actual saved schema, required fields, IDs, uniqueness, and
references rather than whole-record equality with reconstructed current JSON.
Build constructs once, validates inputs before writing, and may atomically
replace an existing safe direct regular aggregate. Paths remain contained,
normalized, and non-symlinked; actual inputs and participating review evidence
must never be overwritten. Failures preserve prior aggregate bytes.

`verification` is exactly
`{status:"valid",semanticApproval:"not-recorded",visualApproval:"not-recorded",audiovisualApproval:"not-recorded"}`.
This records no semantic, visual, or audiovisual approval and invokes no renderer.
Media receipt admission and non-review artifact integrity remain separate
responsibilities. Removed historical snapshots cannot
serve as substitute local currentness proof.

## Commands

### Video-project production

For a `video-project`, package `output` must equal the Video-resolved final
output. Both current actions and delegated generation enter Video's normal
Media producer, so reusable MP4s still receive canonical current credit
companions. Video owns the actual dependency closure and per-part rendering
decision under [file update management](file-update-management.md). Package
knowledge/descriptor/site/publication inputs are independently validated and
observed for races without implicitly rerendering unchanged Video inputs.
Only explicitly authored receipt inputs are extra Video producer operands.

Newer render inputs or missing/invalid credit semantics run the selected
Remotion/simple-java2d renderer before final assembly. Valid existing parts may
be assembled when only the final is missing; unchanged builds refresh the
three credit companions without rewriting MP4s. Required profile/audio/asset
failures remain actionable failures. Credit verification checks selected
domain fields, obligations, presentation and required Markdown attribution,
allowing harmless extras and descriptive path relocation. Native Media build
uses final mode; standalone Video assembly and explicit confirmation/review
remain separately owned. Selected generated-output backups preserve prior
bytes/mtimes/existence on modeled in-process producer failure.

That preservation extent is Video production and its declared producer inputs,
including explicit receipt operands. Media's later package-wide race checks
and receipt acceptance retain their existing behavior; generated Video output
rollback across that later package acceptance is not claimed.

- `cozy media inspect <media-file>` describes identity, resources, and resolved paths.
- `cozy media plan <media-file> [--target <id>] [--profile <name>]` reports build and publication actions without changing files.
- `cozy media build <media-file> [--target <id>] [--dry-run]` performs deterministic resource builds, including configured business presentation and receipt acceptance.
- `cozy media verify <media-file> [--target <id>] [--profile <name>]` checks sources, outputs, dimensions, publication equality, and current v2 receipt evidence.
- `cozy media publish <media-file> --profile <name> [--target <id>] [--dry-run]` rejects stale or v1-only evidence before copying verified outputs to profile destinations.
- `cozy media slide validate|plan|verify <media-file> [--target <id>] [--profile business]` selects business presentation resources only.
- `cozy media slide build <media-file> [--target <id>] [--profile business] [--dry-run]` renders business presentation resources only.
- `cozy media cross-review build|verify <media-file> --target <presentation-id> --video-project <project-file> --save <output.json>|--cross-review <input.json>` creates or exactly verifies structural Cross-media Review evidence without performing a render, publication, or acceptance decision.
- `cozy media review align <media-file> --target <presentation-id> --authority <article|slide-ir>` records an explicit semantic-alignment decision after deterministic verification.
- `cozy media scaffold article <slug> --profile business --language <tag> --save <dir>` atomically creates a new source package and never merges or overwrites a destination.

Initial build kinds are `svg-to-png`, `copy`, `prebuilt`, `video-project`, `presentation`, `article-pdf`, and `summary-slides-pdf`. A `prebuilt` resource is generated by a separate deterministic producer and is verified and published without being rebuilt. Video resources use `prebuilt` after separately owned video production, or `video-project` for normal Video-owned rendering/reuse and final assembly. A `video-project` resource must declare the same package-level `output` path as Video resolves so `verify` and `publish` can check the generated media artifact. Cozy is an asset/schema/verifier/receipt orchestrator, not a PowerPoint layout engine; article `.dox` and its PDF, editable infographic SVG, and semantic slide IR remain their respective sources of truth while a PPTX is only an explicitly requested internal presentation sidecar.

## Source Control Boundary

Authored descriptors, briefs, scripts, SVG masters, and publication PNG inputs may be versioned. Audio, renderer caches, intermediate videos, and final large video artifacts must be generated outside the source package or below ignored target directories.
