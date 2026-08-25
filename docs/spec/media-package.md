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

`cozy.media.v1` remains the descriptor schema. A descriptor may add one strict optional `receipt` block. It declares evidence only; it neither selects nor invokes a presentation renderer.

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

The receipt input-set digest is SHA-256 of compact recursively key-sorted JSON containing the exact Cozy/optional producer identity and sorted evidence records (`id`, `role`, `path`, `normalization`, `sha256`). It contains no timestamp, mtime, absolute path, or output bytes. An infographic SVG is source evidence and a PNG is output evidence. PPTX, individual slide PNGs, and a slide montage are each separately declared resources; templates are normally explicit inputs, and normalized slide IR is normally an explicit `structured-document` input.

Every newly accepted resource entry in `target/cozy-media/manifest.json` retains the legacy `schema`, `knowledge`, and resource `id`/`path`/`sha256` fields, and adds a `receipt` with schema `cozy.media.receipt.v2`, `inputSetSha256`, sorted `inputs`, `producer`, operation `{name:"cozy media build"}` plus selected target/effective profile when present, UTC ISO-8601 `acceptedAt`, and `{status:"valid",findings:[]}` verification. The output SHA-256 remains the exact accepted output-file digest. Receipt evidence is written only after selected-resource structural verification; dry run and failing work do not write it.

Freshness is content-based, not mtime-based. A resource is Current only when its output exists, its v2 receipt is well formed, the manifest output path/hash match, and the full producer/input evidence recomputes to the recorded input-set digest. `copy` and `svg-to-png` rebuild when this evidence is absent or mismatched; `video-project` delegates when mismatched; matching video evidence is Current. A target build replaces only selected manifest entries and preserves unselected entries and their prior evidence without re-accepting them. The full input identity is captured again after selected work; a change rejects the build without fresh evidence.

`prebuilt` uses explicit adoption. The first explicit build with no prior entry may establish a receipt baseline for its current direct regular output. When a prior v1 or v2 entry has the same output hash but inputs have changed, Cozy rejects it as stale rather than blessing it. A changed prebuilt output hash may be accepted as an externally refreshed output. Missing prebuilt source remains MissingSource.

## Commands

- `cozy media inspect <media-file>` describes identity, resources, and resolved paths.
- `cozy media plan <media-file> [--target <id>] [--profile <name>]` reports build and publication actions without changing files.
- `cozy media build <media-file> [--target <id>] [--dry-run]` performs deterministic resource builds.
- `cozy media verify <media-file> [--target <id>] [--profile <name>]` checks sources, outputs, dimensions, publication equality, and current v2 receipt evidence.
- `cozy media publish <media-file> --profile <name> [--target <id>] [--dry-run]` rejects stale or v1-only evidence before copying verified outputs to profile destinations.

Initial build kinds are `svg-to-png`, `copy`, `prebuilt`, and `video-project`. A `prebuilt` resource is generated by a separate deterministic producer and is verified and published without being rebuilt. Video resources normally use `prebuilt` after `cozy video` rendering, or `video-project` when final assembly can be delegated directly. A `video-project` resource must still declare the package-level `output` path so `verify` and `publish` can check the generated media artifact.

## Source Control Boundary

Authored descriptors, briefs, scripts, SVG masters, and publication PNG inputs may be versioned. Audio, renderer caches, intermediate videos, and final large video artifacts must be generated outside the source package or below ignored target directories.
