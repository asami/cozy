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
resource entry continues to hold the accepted output hash. An absolute output
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

There is no receipt input-set digest. Generation reuse uses existing declared
input files and their modification times through `CozyFileUpdatePolicy`, not
earlier receipt/model equality. Unknown/newer inputs conservatively select the
real producer. Active build/publication commands observe file paths and FileTime
in memory to reject input races; those observations are not serialized proofs.
An explicit prebuilt accepts its valid supplied output even when copying preserved
mtime. Unrelated resource sources are not inferred as generation dependencies.

Receipt-v2 no longer emits or requires `inputSetSha256` or per-input `sha256`.
Historical properties with those names are ignored without hash parsing, comparison
or re-emission. Producer metadata remains informative, not a historical content gate.
The unchanged outer output/artifact SHA fields protect the actual distribution
candidate during verify/publication, never local reuse. Prepared publication also
retains actual source/destination/temporary-copy integrity and safe atomic install;
its descriptor/input-set digests are removed in favor of active-command observations.

Every newly accepted resource entry in `target/cozy-media/manifest.json` retains the legacy `schema`, `knowledge`, and resource `id`/`path`/`sha256` fields, and adds a `receipt` with schema `cozy.media.receipt.v2`, sorted non-hash `inputs`, `producer`, operation `{name:"cozy media build"}` plus selected target/effective profile when present, UTC ISO-8601 `acceptedAt`, and `{status:"valid",findings:[]}` verification. The output SHA-256 remains the exact accepted output-file digest. Cozy prepares selected review states and this receipt before any acceptance write, installs states first, and makes the receipt the final visibility record; in-process failure restores prior bytes/existence. Dry run and failing work do not expose a fresh receipt.

Local generation freshness and distribution integrity are separate boundaries.
Generation selects reuse from declared-file existence and modification times;
verify/publication additionally require accepted output/artifact integrity.
A target build replaces only selected manifest entries and preserves unselected
entries without re-accepting them. Active-command input observations are captured
again after selected work; a change rejects acceptance. Review/artifact and
independent video hash layers outside this receipt migration remain separate
obligations; this contract does not imply Phase/Step acceptance.

### PDF review currentness state

`target/cozy-media/pdf-review-state.json` is a package-owned, receipt-derived
currentness document with the exact closed schema
`cozy.media.pdf-review-state.v1`. It has no descriptor configuration and is
prepared from the same merged candidate `cozy.media.v1` manifest that a normal
`cozy media build` atomically accepts. Acceptance installs it before the final
receipt through the existing rollback-safe receipt transaction. An absent state
target is created only when the candidate has at least one current qualifying
PDF resource; an existing target must be a direct regular non-symlink file.

Its exact ordered object is `schema`, `knowledge`, and `resources`. Resources
are ordered by `(role,id)` and each is exactly `id`, `role`, `language`,
`publicPath`, `mediaType`, `output`, and `sha256`; a
direct `build: summary-slides-pdf` `summary_slides_pdf` entry additionally has
`rendererManifestSha256`. Entries
are projections only of current `kind: document` resources whose
`articleMedia.role` is exactly `article_pdf` or `summary_slides_pdf`; `language`
is the exact `ja` or `en` locale, `mediaType` is exactly `application/pdf`, and
all paths are descriptor-relative forward-slash spellings. The state binds the
knowledge identity, accepted resource output path/hash, and public path without
serializing a source-machine path. Historical resource `inputSetSha256`
properties are ignored; no input-set digest is emitted or compared.

For a direct `build: summary-slides-pdf` summary-slides entry Cozy first
requires its current dependency receipts and the existing
`CozyMediaSummarySlidesPdf.verifyStructural` proof. Its renderer-manifest byte
SHA-256 is then bound in this state; page count, source page order, and asset
evidence remain exclusively validated by that structural proof. A prebuilt
`summary_slides_pdf` instead retains ordinary receipt-derived role, path, hash,
and currentness evidence without a `rendererManifestSha256` field or direct
summary dependency/structural verification. Exact reconstruction rejects a
missing, malformed, stale, altered, or unexpected state entry, changed
output/locale/public path/media type/receipt, or changed direct-summary renderer
evidence. A stale retained summary is omitted by a target article-PDF refresh
rather than blessed, and a later valid summary refresh may restore both roles.

This state adds PDF review/currentness only; `CozyMediaReceipt.current` remains
the authoritative receipt-currentness check. Normal `cozy media verify` and
publication preflight require the state only when their selected resources are
qualifying PDFs. `cozy media slide build` remains presentation-only and neither
writes nor requires it. The state is not a receipt and does not change the
`cozy.media.v1`, `cozy.media.receipt.v2`, `cozy.media.review-state.v1`, or
`cozy.media.cross-review.v1` shapes. It performs no registration, publishing,
SmartDox action, PPTX exposure, semantic approval, visual approval, or
audiovisual approval.

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
QA. When production declares a render SHA-256, it must match the current
output. That optional digest connects a current external QA
result to the actual video; it is not an old-receipt comparison. Updated valid
production metadata is allowed. No previous/current receipt equality, duplicated
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

The renderer writes `cozy.presentation.render.v1` with exact top-level `schema`, `target`, `profile`, `renderer`, `pptx`, `slides`, and `montage`. The profile is exactly `business`. Each slide's assets require only ordered unique `{id}` values; ordinary extra asset fields are ignored, including malformed obsolete metadata, while wrong, missing, ill-typed, duplicate, or reordered IDs reject. All serialized artifact paths are descriptor-relative forward-slash paths. Historical `slideIrSha256`/`templateSha256` values are ignored without parsing or calculation. Cozy checks actual output integrity; securely parses `[Content_Types].xml`, `ppt/presentation.xml`, presentation and per-slide relationships; derives relationship-ordered contiguous slide parts; and binds each slide's embedded media to that slide's safe direct selected asset files. Actual bytes must agree as a multiset, with equal cardinality and one-for-one byte consumption preserving duplicate-content multiplicity; media or ZIP order is not authority. All existing relationship, slide-order, XML, linkage, and packaging gates remain. It rejects hostile XML, an exit failure, incomplete output, malformed manifest, input race, path traversal, overlap, or any missing/extra/duplicate evidence.

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

Slide-IR renderer evidence is ordered as
`schema,target,profile,renderer,pdf,slides,montage[,pptx]`
under `cozy.summary-slides.render.v1`; Visual Page evidence is ordered as
`schema,target,profile,renderer,pdf,slides,montage[,pptx]`
under `cozy.summary-slides.render.v2`. Historical `slideIrSha256` and
`templateSha256` are ignored in both contracts without hash processing.
Obsolete `visualPageSetSha256`,
`catalogSha256`, `visualPageSet`, `catalog`, and `binding` properties are
filtered before ordered-field validation. They may be missing or hold any JSON
value and are neither interpreted nor compared with current semantic inputs.
Actual current PageSet/catalog/Binding loading remains required for rendering. `pdf` is exactly `{sha256,pageCount}`
without a path. Slide-IR slide entries are exactly
`{id,title,path,sha256,pdfSha256,assets}`; Visual Page slide entries are exactly
`{id,path,sha256,pdfSha256,assets}`; every asset requires only `{id}` in
ordered unique authority-ID order. Ordinary extra asset fields are ignored;
wrong, missing, ill-typed, duplicate, or reordered IDs reject. The montage is
exactly `{path,sha256,pdfSha256}`. An explicitly requested PPTX
is only `{path,sha256}` in renderer evidence and is an internal sidecar, never
a public resource or registration target. Visual Page assets resolve safely
from the actual PageSet parent and their media types and actual bytes must
agree with the selected safe media resources; legacy selected resources likewise
remain safe direct files. PDF header/page count, PNG, path, staging, and
input-race gates remain. A material disagreement fails before replacing prior
PDF/receipt bytes. Building either the prebuilt or
direct route does not imply BoK publication or SmartDox registration; `cozy
media publish` and later registration operations remain separate consumers.

`target/cozy-media/manifest.json` retains its `cozy.media.v1` top-level shape. A resource may add `artifacts`, whose records are exactly `id`, `role`, `path`, `sha256`, and optional `sourceSha256`. Presentation evidence includes slide PNGs, montage, renderer manifest, and the Cozy review manifest; slide/montage source hashes are the PPTX SHA-256. Legacy resources omit `artifacts` rather than writing an empty field.

After renderer verification Cozy deterministically writes `cozy.media.presentation-review.v1` before receipt acceptance. Its material assets are ID-sorted `{id}` values; no material hash is reconstructed. Source and template entries carry path metadata only; historical nested `sha256` properties are ignored. Other fields are reconstructed from current trusted descriptor, IR, renderer, artifacts, article PDF, and infographic identities and compared during verification. It proves structural/cross-artifact freshness only; AI/Codex remains responsible for semantic comparison and Cozy never self-approves semantic alignment. `cozy.media.presentation-review.scaffold.v1` is a scaffold-only exact marker with `schema`, `target`, `knowledge`, `language`, and `status:not-rendered`; it carries no acceptance evidence and is replaced by this verified manifest before receipt acceptance.

`review/state.yaml` uses `cozy.media.review-state.v1` and exact keys `schema`, `current`, `last_aligned`, `selected_authority`, and `sync`. Snapshots contain only UTC ISO-8601 `acceptedAt`. Historical `inputSetSha256`, `reviewManifestSha256`, and `artifactSetSha256` properties are ignored regardless of their values and are never re-emitted. A successful presentation build refreshes `current` with the verified review manifest's modification time (the last-written generation boundary); it preserves `last_aligned` and `selected_authority`. Alignment remains valid only when the generation is no newer than the explicit alignment time. Unchanged reuse preserves alignment; regeneration after alignment becomes stale even when bytes are identical. `cozy media review align` records the current human/AI decision time and selected authority. Ordinary media verification and slide verification require an aligned state and a review manifest no newer than `current`. Actual output integrity and structural verification remain separate; no aggregate content identity is calculated for alignment.

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

The renderer manifest is `cozy.presentation.render.v2` with ordered fields
`schema,target,profile,renderer,pptx,slides,montage`, after
ignoring the same obsolete input/proof properties as summary-PDF v2. They may
hold any JSON value and do not independently veto processing. Actual named
PageSet/catalog/Binding validity, required page IDs/order/assets, output paths,
PPTX relationship/media structure, PNG validity, and current declared-byte
agreement remain. Invalid actual input or failed generation preserves prior
primary output.

The deterministic `cozy.media.presentation-review.v2` wrappers for
`visualPageSet`, `catalog`, and `binding` contain exactly `{path}`, not
hashes or copied values. Private evidence contains only `(id,assets)` pages
and `(reviewPath,pages)`. No complete semantic JSON replacement gate exists.
Renderer argv is unchanged and external renderer implementation/acceptance is
not claimed. Existing receipt input-set, template/output/local-artifact hashes,
review-manifest reconstruction of remaining fields, and review-state currentness
remain unfinished later same-Phase mechanisms, not integrity exemptions or
closure claims. Actual descriptor/source/catalog/Binding files remain required.

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
`{sceneId,pageId,assets}` with `{id,mediaType}` assets. DM14 deletes root
PageSet/catalog/Binding proofs and per-page logical/visual proofs. Legacy removed
hash or replacement-value properties are ignored for any JSON value and are not
emitted. This includes historical `presentationReviewIdentity`,
`storyboardEvidenceIdentity`, `storyboardHandoffIdentity`, `storyboardIdentity`,
and per-slide `sha256`. CrossReview computes no digest and performs no duplicate
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

Initial build kinds are `svg-to-png`, `copy`, `prebuilt`, `video-project`, `presentation`, `article-pdf`, and `summary-slides-pdf`. A `prebuilt` resource is generated by a separate deterministic producer and is verified and published without being rebuilt. Video resources normally use `prebuilt` after `cozy video` rendering, or `video-project` when final assembly can be delegated directly. A `video-project` resource must still declare the package-level `output` path so `verify` and `publish` can check the generated media artifact. Cozy is an asset/schema/verifier/receipt orchestrator, not a PowerPoint layout engine; article `.dox` and its PDF, editable infographic SVG, and semantic slide IR remain their respective sources of truth while a PPTX is only an explicitly requested internal presentation sidecar.

## Source Control Boundary

Authored descriptors, briefs, scripts, SVG masters, and publication PNG inputs may be versioned. Audio, renderer caches, intermediate videos, and final large video artifacts must be generated outside the source package or below ignored target directories.
