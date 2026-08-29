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
PDF destination, and its closed configuration is:

```yaml
articlePdf:
  latexFormat: business # standard | business
  renderer:
    name: smartdox-pdf
    version: 2.4.18-SNAPSHOT
    command: [smartdox]
```

The renderer command is the configured exact argv tokens followed without a
shell by `<source> --output <staged-output> --locale <ja|en> --latex-format
<standard|business>`. Cozy accepts only a zero-exit direct regular `%PDF-`
staged file, rechecks full receipt input identity, and atomically replaces the
declared output only then. Failure, invalid output, or an input race preserves
the previous output and creates no fresh receipt. Existing receipt v2 input
evidence captures the descriptor and source configuration; its resource entry
continues to hold the accepted output hash without a schema change.

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

Cozy automatically records raw-byte SHA-256 evidence for the descriptor, knowledge source, every declared resource `source` and `project`, and the selected effective publication-profile configuration source when there is one. Authored receipt inputs are added to that set. Paths use `/` descriptor-relative spelling and never serialize machine-absolute paths. `bytes` means exact file bytes. `structured-document` parses the supported structured document, recursively orders object keys by String ordering while preserving array and scalar semantics, serializes compact UTF-8 JSON, then hashes those bytes. Thus whitespace and object-key ordering changes do not change structured-document identity, but semantic changes do.

The receipt input-set digest is SHA-256 of compact recursively key-sorted JSON containing the exact Cozy/optional producer identity and sorted evidence records (`id`, `role`, `path`, `normalization`, `sha256`). It contains no timestamp, mtime, absolute path, or output bytes. An infographic SVG is source evidence and a PNG is output evidence. PPTX is the presentation resource output; slide PNGs and a slide montage are subordinate artifacts. Templates are normally explicit inputs, and normalized slide IR is normally an explicit `structured-document` input.

Every newly accepted resource entry in `target/cozy-media/manifest.json` retains the legacy `schema`, `knowledge`, and resource `id`/`path`/`sha256` fields, and adds a `receipt` with schema `cozy.media.receipt.v2`, `inputSetSha256`, sorted `inputs`, `producer`, operation `{name:"cozy media build"}` plus selected target/effective profile when present, UTC ISO-8601 `acceptedAt`, and `{status:"valid",findings:[]}` verification. The output SHA-256 remains the exact accepted output-file digest. Cozy prepares selected review states and this receipt before any acceptance write, installs states first, and makes the receipt the final visibility record; in-process failure restores prior bytes/existence. Dry run and failing work do not expose a fresh receipt.

Freshness is content-based, not mtime-based. A resource is Current only when its output exists, its v2 receipt is well formed, the manifest output path/hash match, and the full producer/input evidence recomputes to the recorded input-set digest. `copy` and `svg-to-png` rebuild when this evidence is absent or mismatched; `video-project` delegates when mismatched; matching video evidence is Current. A target build replaces only selected manifest entries and preserves unselected entries and their prior evidence without re-accepting them. The full input identity is captured again after selected work; a change rejects the build without fresh evidence.

`prebuilt` uses explicit adoption. The first explicit build with no prior entry may establish a receipt baseline for its current direct regular output. When a prior v1 or v2 entry has the same output hash but inputs have changed, Cozy rejects it as stale rather than blessing it. A changed prebuilt output hash may be accepted as an externally refreshed output. Missing prebuilt source remains MissingSource.

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

The renderer writes `cozy.presentation.render.v1` with exact top-level `schema`, `target`, `profile`, `renderer`, `slideIrSha256`, `templateSha256`, `pptx`, `slides`, and `montage`. The profile is exactly `business`. All serialized artifact paths are descriptor-relative forward-slash paths. Cozy recomputes each hash; securely parses `[Content_Types].xml`, `ppt/presentation.xml`, presentation and per-slide relationships; derives relationship-ordered contiguous slide parts; and binds each slide's embedded media to that slide's exact assets. It rejects hostile XML, an exit failure, incomplete output, malformed manifest, input race, path traversal, overlap, or any missing/extra/duplicate evidence.

`target/cozy-media/manifest.json` retains its `cozy.media.v1` top-level shape. A resource may add `artifacts`, whose records are exactly `id`, `role`, `path`, `sha256`, and optional `sourceSha256`. Presentation evidence includes slide PNGs, montage, renderer manifest, and the Cozy review manifest; slide/montage source hashes are the PPTX SHA-256. Legacy resources omit `artifacts` rather than writing an empty field.

After renderer verification Cozy deterministically writes `cozy.media.presentation-review.v1` before receipt acceptance. Every field is reconstructed from current trusted descriptor, IR, renderer, artifacts, article PDF, and infographic identities and exactly compared during verification. It proves structural/cross-artifact freshness only; AI/Codex remains responsible for semantic comparison and Cozy never self-approves semantic alignment. `cozy.media.presentation-review.scaffold.v1` is a scaffold-only exact marker with `schema`, `target`, `knowledge`, `language`, and `status:not-rendered`; it carries no acceptance evidence and is replaced by this verified manifest before receipt acceptance.

`review/state.yaml` uses `cozy.media.review-state.v1` and exact keys `schema`, `current`, `last_aligned`, `selected_authority`, and `sync`. Snapshots use exact `inputSetSha256`, `reviewManifestSha256`, `artifactSetSha256`, and UTC ISO-8601 `acceptedAt`. A successful presentation build refreshes only `current`; it preserves `last_aligned` and `selected_authority`, and is `aligned` only when all three identities still equal `last_aligned`. `cozy media review align` explicitly records the human/AI-selected authority. Ordinary media verification and slide verification require an aligned state.

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
exactly one supplied resolved catalog matching that pair and its full identity;
a mixed pair, unresolved catalog, or mismatch fails closed. It never permits
the untagged legacy shape or `--slide-ir` inference. Its `profile` remains the
exact existing template/renderer object. Its fixed renderer argv is the legacy
fixed argv with `--slide-ir <source>` replaced by `--visual-page-set <source>
--catalog <catalog> --binding <binding>`.

The renderer manifest is `cozy.presentation.render.v2` with exact
canonical top-level fields `schema`, `target`, `profile`, `renderer`,
`visualPageSetSha256`, `catalogSha256`, `bindingSha256`, `templateSha256`,
`pptx`, `slides`, and `montage`. The first three identity fields are canonical
semantic 64-hex identities; template and generated artifacts use raw byte
SHA-256. Every `slides` entry is exactly `id`, `path`, `sha256`, `pptxSha256`,
and `assets`; its assets are exact `{id,sha256}` values in page asset-ID order.
The deterministic review manifest is `cozy.media.presentation-review.v2` and
contains reconstructed VisualPageSet, catalog, binding, template, PPTX,
slides, assets, and montage evidence without approving itself. Existing
`cozy.media.receipt.v2` and `cozy.media.review-state.v1` schema shapes remain
unchanged. The route requires explicit VisualPageSet/catalog structured-
document and binding/template byte receipt inputs. Changed Visual Page,
catalog, binding, template, renderer, or asset input stales its output/evidence;
no legacy descriptor gains new behavior.

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
approved Storyboard, literal screen references, VisualPageSet/catalog/binding,
selected assets, and effective renderers before accepting it as an input.
Neither a stale presentation review nor a stale, malformed, unapproved, or
wrong-schema Storyboard package is a Cross-media input.

The build output is the canonical `cozy.media.cross-review.v1` object with
this ordered top-level shape:

```text
schema, target, presentationReviewIdentity, storyboardEvidenceIdentity,
storyboardHandoffIdentity, storyboardIdentity, visualPageSetIdentity,
catalogIdentity, bindingIdentity, slides, visualPages, verification
```

The leading identity values are SHA-256 identities of the respective current
evidence bytes or declared Storyboard identity. `visualPageSetIdentity`,
`catalogIdentity`, and `bindingIdentity` are the canonical semantic identities
already used by the v2 presentation review and Storyboard proof. `slides` is
the ordered presentation review projection `{id,sha256}`. `visualPages` is
ordered by Storyboard scene and contains exactly
`{sceneId,pageId,logicalIdentity,visualPageIdentity,assets}`; each asset is
the exact `{id,sha256}` pair. Every selected Storyboard page must occur once
in the presentation page set and its page assets must equal that presentation
page's assets. The presentation review's page-set/catalog/binding identities
must equal the Storyboard proof values; mismatch, duplicate page/scene,
missing slide, missing asset, or extra/unknown field rejects.

`verification` is exactly
`{status:"valid",semanticApproval:"not-recorded",visualApproval:"not-recorded",audiovisualApproval:"not-recorded"}`.
It is deterministic structural evidence only: it neither accepts article
meaning, visual quality, nor audiovisual quality. A later change to any input
causes `cross-review verify` to reject the saved object through exact current
reconstruction. `--save` and `--cross-review` are direct regular non-symlink
JSON files below the Media Package root, with normalized descriptor-relative
spelling; `--save` must designate an absent output path and never overwrites
an input or evidence file; Cross-media Review never follows an unsafe path.

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

Initial build kinds are `svg-to-png`, `copy`, `prebuilt`, `video-project`, and `presentation`. A `prebuilt` resource is generated by a separate deterministic producer and is verified and published without being rebuilt. Video resources normally use `prebuilt` after `cozy video` rendering, or `video-project` when final assembly can be delegated directly. A `video-project` resource must still declare the package-level `output` path so `verify` and `publish` can check the generated media artifact. Cozy is an asset/schema/verifier/receipt orchestrator, not a PowerPoint layout engine; article `.dox` and its PDF, editable infographic SVG, and semantic slide IR remain their respective sources of truth while PPTX is generated distribution output only.

## Source Control Boundary

Authored descriptors, briefs, scripts, SVG masters, and publication PNG inputs may be versioned. Audio, renderer caches, intermediate videos, and final large video artifacts must be generated outside the source package or below ignored target directories.
