# SmartDox Site Media Registration Specification

## Contract Basis

This specification is the authoritative implementation contract for Cozy Phase
27 site-media registration. It reuses the accepted SmartDox Phase 1
`article-media-publication` record and Cozy Phase 26 publication-bundle
ownership, locking, snapshot, and atomic-replacement rules. It does not extend
the SmartDox schema.

`simplemodeling.org` is a special BoK whose site SmartDox builds directly. It
is not a Cozy BoK. SmartDox owns the provider-neutral schema/model, validation,
and site projection; Cozy owns normal `cozy.media.v1` package orchestration.

## Descriptor Contract

The following blocks use canonical camelCase serialized names. Any present
`articleMedia` block (top-level or resource-level) MUST be a non-null object
whose keys are exactly the canonical allowed keys; aliases and unknown keys,
`null`, partial blocks, and empty required strings fail descriptor/preflight
validation rather than being ignored. The top-level allowed keys are exactly
`articleIdentity` and `publicationProfile`. The resource-level allowed keys
are exactly `role`, `publicPath`, `mediaType`, `alt`, and `production`, with
the role-specific required/optional combinations stated below.

```yaml
articleMedia:
  articleIdentity: development-process/part-5
  publicationProfile: simplemodeling-org
resources:
  - id: summary-ja
    kind: infographic
    role: article-summary
    language: ja
    articleMedia:
      role: infographic
      publicPath: /ja/development-process/images/part-5/summary.png
      mediaType: image/png
      alt: 要約インフォグラフィック
  - id: video-ja
    kind: video
    role: article-video
    language: ja
    articleMedia:
      role: video
      production: video/ja/production.json
```

`articleIdentity` and `publicationProfile` are both required non-empty strings.
`articleIdentity` is the explicit normalized SmartDox site-relative identity.
It is independent of `knowledge.id`; no package/path/name inference is
permitted. `development-process/part-5` above is a test-fixture identity, not a
future article title. `publicationProfile` selects the existing
descriptor/profile and resource-publication evidence; it is never inferred.

The nested value `resources[*].articleMedia.role` is exactly `infographic` or
`video`. Its mapping is exact: `infographic` requires `kind: infographic`, and
`video` requires `kind: video`; every other combination fails. Existing
top-level `resources[*].role` (for example `article-summary`) is the
media-package production role. It is independent of, and is not used for,
site-media registration. An infographic requires explicit `publicPath`; optional
`mediaType` and `alt` are emitted only when explicit. A video requires explicit
descriptor-root-relative `production`; infographic blocks must not contain
`production`, and video blocks must not contain `publicPath`, `mediaType`, or
`alt`.

The top-level `articleMedia` block is the only site association declaration. If
it is absent, `register-site` fails before mutation. With no `--target`, all and
only resources containing complete valid `articleMedia` blocks are candidates;
resources without a block are ignored, but zero candidates is an error. With
`--target`, the target must name exactly one existing resource and that resource
must contain a complete valid block; an absent or invalid block fails, and a
target never opts a resource in. All malformed blocks and candidate-selection
errors fail before publication mutation.

## Command

```text
cozy media register-site <media-file> --publication <dir> [--target <resource-id>] [--dry-run]
```

`--publication` is required and names the SmartDox publication-bundle root.
`--dry-run` executes the complete read-only preflight and reports deterministic
candidate `id`, exact locale, and `resources[*].articleMedia.role` values, followed by
the same plan a non-dry run would register. It fails on malformed blocks, an
invalid target, or zero candidates; there is no successful no-op. It performs
no bundle creation/replacement or other mutation.

The command does not generate or copy media, upload video, build/deploy a
site, invoke `cozy bok`, scan an arbitrary target/site/repository tree, or use
implicit host fallback. It does not enumerate BoK descriptors. Skills call this
supported command rather than writing bundle JSON.

## Candidate Validation

All requested candidates are validated before mutation. A duplicate normalized
`(articleIdentity, locale, resources[*].articleMedia.role)` among them fails
preflight. Any malformed or changed evidence, invalid path, symlink/root alias,
ownership conflict, or concurrent snapshot change fails without mutation.

### Unified Evidence Snapshot and Revalidation

Complete candidate preflight records an internal evidence snapshot that is
never serialized to the provider-neutral SmartDox record. The snapshot includes
the exact descriptor file identity and content digest, its parsed top-level and
resource `articleMedia` declarations, the selected publication-profile
definition and resolved real root, the selected resource publication mapping
and resolved normalized destination, and, for each selected candidate, the
following evidence:

- an infographic destination's direct-file real identity and SHA-256 (and its
  deterministic size when available); or
- a video's production direct-file identity and SHA-256, together with its
  parsed required fields and validated URL.

Under the same real-publication-root transaction/lock, after registry snapshot
revalidation and immediately before atomic registry replacement, the command
re-reads, re-resolves, re-hashes, and re-parses every selected candidate from
its original admitted paths and compares the result with its preflight
snapshot. It re-reads descriptor, profile, and resource declarations, and
rechecks no symlink traversal, direct-regular-file status, root containment,
and every semantic validation rule. Any descriptor, profile,
resource mapping, `publicPath`/`production` metadata, destination bytes or path
identity, status, URL, disappearance, symlink substitution, or other
validation drift aborts before mutation. All candidates must pass this final
revalidation; no partial write is allowed. Internal evidence hashes and path
identities never appear in the SmartDox record, and strict-only registration
still creates no Cozy-integrity entry.

### Infographic

For an infographic candidate:

- the selected `publicationProfile` must exist in the descriptor and in the
  selected resource's publications;
- its resolved published destination must be an existing direct regular,
  non-symlink file below the selected bound profile root; and
- `publicPath` must use accepted SmartDox root-relative syntax and is never
  derived from the destination path.

The emitted strict variant uses `public_path`, and includes `media_type` and
`alt` only when the corresponding camelCase descriptor fields were explicit.

### Video

For a video candidate:

- `production` is a descriptor-root-relative path that resolves to an existing
  direct regular non-symlink file; the path itself and every traversed
  component must not be a symlink. Its initial and final checks use the unified
  evidence snapshot and revalidation contract above;
- the file is parsed as JSON whose root is an object. Unknown extra fields are
  allowed, no schema marker is required, and the following exact paths and
  types are required: top-level `category`, `article`, and `language` are
  non-empty strings; normalized `category + "/" + article` equals the exact
  top-level `articleIdentity`; `language` equals the exact resource language;
  `render.status` is the string `completed`; `render.qa.status` is the string
  `technical-and-visual-qa-passed`; `youtube.status` is the string `published`;
  and `youtube.videoUrl` is a string;
- when present, `render.listeningReview` is evidence only. A pending review is
  allowed, but it must not be serialized as accepted listening evidence; and
- `youtube.videoUrl` is a clean HTTPS YouTube URL in exactly one of these forms:
  `https://youtu.be/<video-id>` (the current intended shortened form) or
  `https://www.youtube.com/watch?v=<video-id>`. `<video-id>` is non-empty and
  contains only ASCII letters, digits, `_`, or `-`. There is no user-info,
  fragment, non-default port, extra path segment, or extra query parameter.
  The original validated URL is emitted unchanged.

The emitted strict video is exactly `presentation: external-link`,
`status: published`, `provider: youtube`, and `watch_url` from `videoUrl`.
No local MP4 or Cozy-integrity correlation is required for this watch-only
external video. A pending human `listeningReview` does not invalidate these
accepted prerequisites and must not be represented as accepted listening
evidence.

## Registry Mutation

The command writes only the existing Phase 26 strict record path:

```text
metadata/article-media/<articleIdentity>.json
```

Locale is exact canonical SmartDox locale; no fallback is allowed. The public
record is one SmartDox `article-media-publication` record using its existing
Phase 1 field names (`public_path`, `media_type`, `watch_url`, and so on), and
contains no Cozy path, hash, version, provenance, or registry metadata.

Under one real publication-root lock, registration captures the complete
snapshot and identifies ownership from both the strict path and complete Phase
26 integrity prefix. One owner is reused; no owner uses the canonical
`article-media` owner; multiple owners fail. It merges only the requested
exact `resources[*].articleMedia.role`/locale keys, deterministically,
preserving other locales/roles,
existing Cozy integrity records, generic entries, and unrelated articles.
After all preflight succeeds it revalidates the registry snapshot and every
candidate through the unified evidence contract above, then atomically replaces
the bundle. Strict-only site registration creates no
`cozy.article-media-integrity.v1` record for either an infographic or an
external-link video.

## Output and Required Acceptance

The command reports the selected descriptor, publication root, article
identity, and deterministic candidate IDs, exact locales, and
`resources[*].articleMedia.role` plan or results. The result is
SmartDox registry input that a later `dox site` reads; article creation itself
needs no media registration command.

Executable acceptance must use a dedicated synthetic Part 5 normal-media
package fixture with distinct `knowledge.id` and `articleIdentity`, existing
`kind: infographic` resources carrying an independent top-level
`resources[*].role` (such as `article-summary`), JA/EN infographics, and JA/EN
published external-video production records. It must
prove successful registration, dry-run immutability, exact-locale registry
output, unrelated-entry/integrity preservation, duplicate/failure atomicity,
actual pinned SmartDox/Dox article and Notice projection, and rejection before
mutation when infographic descriptor/profile/destination evidence drifts or a
video production file drifts (a deterministic test seam is allowed). This
fixture does not prescribe a future Part 5 editorial title.
