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

## Commands

- `cozy media inspect <media-file>` describes identity, resources, and resolved paths.
- `cozy media plan <media-file> [--target <id>] [--profile <name>]` reports build and publication actions without changing files.
- `cozy media build <media-file> [--target <id>] [--dry-run]` performs deterministic resource builds, including configured business presentation and receipt acceptance.
- `cozy media verify <media-file> [--target <id>] [--profile <name>]` checks sources, outputs, dimensions, publication equality, and current v2 receipt evidence.
- `cozy media publish <media-file> --profile <name> [--target <id>] [--dry-run]` rejects stale or v1-only evidence before copying verified outputs to profile destinations.
- `cozy media slide validate|plan|verify <media-file> [--target <id>] [--profile business]` selects business presentation resources only.
- `cozy media slide build <media-file> [--target <id>] [--profile business] [--dry-run]` renders business presentation resources only.
- `cozy media review align <media-file> --target <presentation-id> --authority <article|slide-ir>` records an explicit semantic-alignment decision after deterministic verification.
- `cozy media scaffold article <slug> --profile business --language <tag> --save <dir>` atomically creates a new source package and never merges or overwrites a destination.

Initial build kinds are `svg-to-png`, `copy`, `prebuilt`, `video-project`, and `presentation`. A `prebuilt` resource is generated by a separate deterministic producer and is verified and published without being rebuilt. Video resources normally use `prebuilt` after `cozy video` rendering, or `video-project` when final assembly can be delegated directly. A `video-project` resource must still declare the package-level `output` path so `verify` and `publish` can check the generated media artifact. Cozy is an asset/schema/verifier/receipt orchestrator, not a PowerPoint layout engine; article `.dox` and its PDF, editable infographic SVG, and semantic slide IR remain their respective sources of truth while PPTX is generated distribution output only.

## Source Control Boundary

Authored descriptors, briefs, scripts, SVG masters, and publication PNG inputs may be versioned. Audio, renderer caches, intermediate videos, and final large video artifacts must be generated outside the source package or below ignored target directories.
