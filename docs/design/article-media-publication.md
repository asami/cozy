# Article Media Publication and BoK Integration Design

## Purpose

This document governs the Phase 26 Cozy-owned association and integrity base
for optional article media in a BoK, plus the explicitly additive Phase 28.1
`wip-site-video` extension. It consumes, but does not extend, the accepted
SmartDox article-media publication contract from closed Phase 9 through the
development integration coordinate `org.smartdox:smartdox_2.12:2.4.18-SNAPSHOT`.

SmartDox Phase 9 is closed and its accepted PDF roles are part of the input
contract; public/non-SNAPSHOT publication is not a Phase 26 start gate. The
admitted SmartDox repository may be used for the corresponding development
`publishLocal` integration under repository rules.

## Responsibilities and Sources of Truth

SmartDox owns the provider-neutral `article-media-publication` input and its
article/Notice projection. A native record contains only the accepted fields:

```json
{
  "type": "article-media-publication",
  "article": { "identity": "development-process/example" },
  "variants": {
    "en": {
      "infographic": {
        "public_path": "/en/development-process/images/example.png",
        "media_type": "image/png",
        "alt": "Detailed infographic"
      },
      "video": {
        "presentation": "site-hosted",
        "status": "published",
        "provider": "cozy",
        "content_url": "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"
      }
    }
  }
}
```

The canonical locale variant keys are exact canonical SmartDox locale tags (for
example, `en` and `ja`). The infographic has `public_path` and optional
`media_type` and `alt`; the video has `presentation`, `status`, and optional
`provider`, `watch_url`, and `content_url`. It carries no Cozy hash, version,
artifact identity, repository path, provenance, or registry fields.

Cozy owns the separate `cozy.article-media-integrity.v1` association/integrity
projection. It is the validation source of truth for Cozy path-bearing media,
not an extension of the SmartDox schema. Existing `video-publication` evidence
remains owned by `metadata/video/<name>/<version>/manifest.json` and
`metadata/artifacts/repository/<name>.json` from `CozyVideoPublisher`; Phase
26 references that evidence and does not redefine it. An infographic instead
comes from an explicit `cozy.media.v1` descriptor/resource and its generated
manifest/publication result, never directory discovery.

The Phase 26 BoK base has two explicit producer operations. `cozy bok
publish-video` remains the repository-backed video producer. `cozy bok
publish-media <project-dir> [--publication
<dir>] [--repository <dir>] [--version <version>] [--force]` is the
infographic producer. It enumerates only the decoder-recognized exact media
descriptor filenames `media.yaml`, `media.yml`, `media.json`, `media.conf`, and
`media.xml` beneath the configured BoK source. It does not follow symlink
directories; a candidate is a direct regular non-symlink file and two eligible
basenames in one directory are a command-wide collision. It publishes selected
explicit `detailed-infographic` resources through a uniquely selected declared
publication profile. Both the profile root and configured artifact repository
must be existing directories whose normalized absolute lexical identities and
canonical real identities are both equal; a lexical or symlink alias therefore
does not match. Zero or multiple matching profiles fail before publication or
mutation. It then registers their article-media state. `update-publication` and
one-stop `publish` include that media operation with their existing video and
project operations. One-stop publish owns dry-run semantics; `publish-media`
introduces no separate dry-run contract.

### SmartDox Phase 9 PDF Roles

SmartDox Phase 9 defines two independent optional locale-variant roles,
`article_pdf` and `summary_slides_pdf`. Each role uses the accepted
`PublishMetadata.PdfDocumentReference` shape: a site-visible `public_path`,
the exact `application/pdf` `media_type`, and an optional nonblank `label`.
The role is selected only by its direct field in its exact canonical locale
variant; Cozy performs no filename inference, locale fallback, or cross-locale
merge.

Cozy's package-private registry preserves these accepted PDF references as
direct strict fields during load, canonicalization, exact-role merge, and
build-context copying. PDF roles are strict provider metadata only: they do
not enter `cozy.article-media-integrity.v1`, and that integrity projection
remains limited to infographic and site-hosted video. This is a consumer-side
preservation boundary; Cozy does not implement or claim a SmartDox schema or
projection change.

## Correlation and Lifecycle

For the Phase 26 repository-backed `media-package` and `video-publication`
producers, each Cozy-produced infographic has `public_path` and exactly one
Cozy integrity record selected by the normalized `(articleIdentity, locale,
infographic)` key; each Cozy-produced site-hosted video that has `content_url`
likewise has exactly one record under `(articleIdentity, locale, video)`. The
corresponding `public_path` or `content_url` equals the integrity record's
site-visible `publicPath`; no equality or integrity rule applies to a watch-
only external link or a URL-less nonprojectable video. The correlation is
exact-locale only: neither surface may substitute a different locale.

Phase 28.1 WIP registration preserves this same ordinary correlation. Its
strict SmartDox video remains exactly `site-hosted`, `published`, and
`content_url` and contains no integrity evidence. Separately, the compatible
`cozy.article-media-integrity.v1` provenance union adds `wip-site-video` with
project-root-relative normalized `descriptor`, exact `resourceId`, and
project-root-relative normalized `production`. It records the resource ID as
artifact identity, the exact 64-character source SHA-256 as opaque artifact
version, `content_url` as `publicPath`, the website-root-relative destination
as `repositoryPath`, `video/mp4`, equal source/staged/destination digests, and
`published` only after installation in the disposable WIP tree. This narrow
meaning of `repositoryPath` and `published` applies only to `wip-site-video`;
the existing video-publication and media-package alternatives remain unchanged.
Phase 27 site-local infographic and Phase 28.1 WIP reuse of that infographic
strict record create no new integrity entry and are outside those Phase 26
producer requirements.

An accepted SmartDox external-link video with only `watch_url` passes through
the SmartDox boundary without a Cozy artifact record. SmartDox statuses are
exactly `draft`, `published`, and `withdrawn`. A draft or withdrawn video with
no `content_url` is valid and has no integrity correlation; one that carries
`content_url` remains path-bearing and must correlate. Cozy's independent
`publicationState` expresses artifact admission and must not be conflated with
SmartDox status.

For the Phase 26 BoK base producers, the existing configured
`src/main/publication` bundle is the only registry container. A SmartDox entry
is upserted at
`metadata/article-media/<articleIdentity>.json`, one normalized article identity
per entry with canonical locale variants sorted deterministically. A Cozy
integrity entry is upserted at
`metadata/article-media-integrity/<articleIdentity>/<locale>/<role>.json`, one
normalized tuple per Phase 26 producer entry. Both Phase 26 producers serialize
deterministic field order; duplicate normalized keys across configured bundles
fail. `cozy bok build` uses the existing registry loader to enumerate configured
publication entries only.

Producer registration is a role-local exact-key merge, not a replacement of an
article or a bundle. Under the existing digest and lock protocol, an operation
holds the same real-publication-root lock from complete snapshot/preflight
through artifact publication and atomic registry replacement. All recognized
strict and integrity entries for one normalized article identity are one
ownership unit. Before mutation, the operation collects distinct owning bundle
names over the strict path and the entire integrity prefix: more than one fails;
one is reused, including integrity-only state; none selects canonical
`article-media`. In the no-owner case it reuses a valid `article-media.json` or
creates an empty canonical `publication-bundle` named `article-media` inside
that locked transaction before atomic replacement. Later roles/locales reuse
that owner; an article is never moved between bundles implicitly, and unrelated
entries in the owner remain preserved.

Complete preflight precedes every artifact or registry mutation. It enumerates
the full command candidate set and validates each descriptor, selected resource,
normalized key, non-empty explicit version, unique profile, source/output/build
manifest, destination/containment, destination SHA/force rule, and current
ownership/snapshot. It deterministically rejects a duplicate normalized
`(articleIdentity, locale, infographic)` across all selected descriptors or
resources. After preflight, artifact publication completes before replacement;
a publication failure leaves the registry unchanged. An absent destination is
published; an equal SHA is idempotent without byte replacement and may refresh
the registry; a differing SHA fails without `--force` and replaces through a
staged same-filesystem atomic move with `--force`. Force never bypasses any
validation. One-stop `publish --dry-run` performs this same non-mutating
preflight and reports planned candidates without copying, bundle replacement or
creation, staging, or upload. This is safe staged/replace behavior, not a claim
of multi-file artifact atomicity.

For the existing `video-publication` and `media-package` producers, the
operation then replaces only its `(articleIdentity, locale, role)`,
preserves all unrelated/generic entries and all other article roles/locales,
reconstructs the complete strict SmartDox article record, revalidates the
complete bundle-digest snapshot, and atomically replaces the owner bundle.
Malformed, duplicate, conflicting, or changed snapshot state fails before
mutation; a producer never loses a concurrent update.

`cozy bok publish-video` and `cozy bok publish-media` produce or update Cozy
integrity records only after explicit descriptor and registered metadata
processing. A video opts in only through its optional nested `.video`
`publish.articleMedia` block; its canonical fields are `articleIdentity`,
`locale`, and `status`, and the block is all-or-nothing. It requests a strict
site-hosted SmartDox variant. Its `content_url` derives only from validated
repository evidence; `status` is the SmartDox enum `draft`, `published`, or
`withdrawn`, never Cozy `publicationState`. Existing top-level `article`,
`locale`, `publish.module`, and `publish.publicPath` do not opt in or supply
that association. A descriptor without the block remains legacy-only with
unchanged behavior.

An infographic opts in only through an explicit `cozy.media.v1` descriptor:
`knowledge.id` supplies the article identity, resource `language` supplies the
exact locale, the role is literally `detailed-infographic`, and the resource
must have a selected publication-profile destination. There is no filename,
directory, or SmartDox-discovery inference. Strict `alt` remains omitted unless
the existing media schema represents it explicitly.

Ordinary `cozy bok build` reads only configured registry bundles and the
configured repository root. Canonical build strategies are exactly `draft`,
`work-in-progress`, `production-preview`, and `production`; CLI `wip` and
`preview` normalize before policy selection. The first three select Preview and
`production` selects Production. Any other strategy fails before registry load,
path construction, or an external command. Under the same real-publication-root
lock used by producers, build holds one complete snapshot from load/digest
capture through policy evaluation, effective-context materialization,
source-digest revalidation, and immutable snapshot installation. It materializes
the effective context in a unique sibling work directory and computes an exact
lowercase-hexadecimal `contextDigest` over the canonical strategy and the
complete effective publication context. Define `U64(n)` as exactly eight
octets containing the non-negative integer `n` in unsigned 64-bit
big-endian/network byte order; every length below counts octets, never
characters. Encode the canonical strategy and every normalized
publication-root-relative bundle filename (using `/` separators) as their
exact UTF-8 octets. Sort bundle files by unsigned lexicographic comparison of
their UTF-8 filename octets. The SHA-256 input is exactly

```
U64(strategyByteLength) || strategyBytes || U64(fileCount) ||
for each sorted file:
  U64(filenameByteLength) || filenameBytes ||
  U64(contentByteLength) || exactFileBytes
```

There are no delimiters, terminators, platform newline conversions, text
re-encodings, filesystem metadata, directory entries, or absolute paths in
this input; `fileCount` counts framed regular bundle files. The
`contextDigest` is the lowercase hexadecimal SHA-256 result over exactly that
octet sequence. Preview omissions are therefore part of the digest. The final
context path is exactly
`target/cozy-bok/article-media/{canonical-strategy}/snapshots/{contextDigest}/publication`.

The effective context preserves unaffected generic/legacy entries, removes
exactly Preview-policy-omitted strict roles, and removes empty strict
variants/entries. The computed digest is verified again before installation;
the work directory is atomically installed as the final snapshot directory.
An already-installed digest path is never mutated or replaced: build verifies
its complete relative-file set and bytes against `contextDigest` and reuses it,
or fails deterministically on mismatch. Concurrent producer/build work
serializes under the shared lock. The lock is released only after the immutable
snapshot is fully installed. Every pinned SmartDox, Dox, Antora, and site
consumer receives that exact digest-addressed `-publication` path, together
with the configured `-publication.repository` value. The build invocation
leases that snapshot for all of its consumers; no build operation replaces or
removes it while a consumer runs. Installed snapshots remain a target cache.
Any future cleanup is a separately coordinated operation that may remove only
snapshots without an active lease; Phase 26 defines no cleanup CLI.

Production policy failure occurs before an external build command. The
digest-addressed snapshot solves only the publication-context handoff lifetime;
artifact and repository admission remain protected and validated by the
existing producer and policy contracts. Build never renders, transcodes,
publishes media, enumerates descriptors, scans target/generated-site/arbitrary-
repository paths, or applies locale fallback. Media-free behavior is unchanged
apart from the deterministic snapshot handoff path.

## Invariants and Boundaries

- Serialized identities are source-relative or publication-relative; an
  absolute machine path is never serialized.
- For `video-publication` and `media-package`, a path must remain inside the
  configured artifact repository and a declared site-visible public path.
  `wip-site-video` instead uses its Phase 28.1 website-root-relative
  destination and is never production-eligible artifact-repository evidence.
  Identity conflict and path escape fail in every strategy.
- No association leaves ordinary articles and BoKs unchanged in preview and
  production.
- A SmartDox draft or withdrawn video remains valid but produces no article
  projection, player, or link. For `video-publication` and `media-package`, a
  published site-hosted video may project only with correlated Cozy state
  `published`; a `wip-site-video` is governed only by Phase 28.1 disposable
  website installation and is not production staging evidence.
- Normalized identities, locale tags, and roles form registry keys and paths.
  Empty, dot, dot-dot, or leading-slash segments are rejected before path
  construction. For `video-publication` and `media-package`, `repositoryPath`
  is relative to the configured artifact root; resolution and real-path checks
  reject lexical or symlink escapes. `wip-site-video` alone has the Phase 28.1
  website-root-relative exception.
- SmartDox `VideoPublication` compatibility remains exact: existing `.video`
  behavior continues; a complete exact-locale native article-media variant wins
  without producer-side schema extension, implicit merge, or locale fallback.
- Pinned SmartDox owns exact-locale article and global/category Notice
  projection; Cozy observes that result rather than reimplementing it. Staging
  keeps website output at the staging root and repository artifacts at
  `<staging-root>/repository`, so registered public paths and staged bytes
  resolve below one public URL root and their SHA-256 values must match.
- A same-strategy concurrent build that has installed and leased its snapshot
  consumes only that digest-addressed snapshot even when another build publishes
  a different effective state at another digest path. Identical effective
  content reuses the existing digest path without mutation; a different byte
  set never replaces it.
- Phase 26 composes the Phase 9 video registry, Phase 10 build boundary, and
  Phase 19 media descriptor boundary without reopening those closed phases.

## Lifecycle

This design fixes stable Phase 26-base and additive Phase 28.1-WIP responsibility
and boundary decisions. The
implementation-facing serialized contract and validation policies are
authoritative in `docs/spec/article-media-publication.md`. Phase 26 status and
checklist progress remain in the Phase 26 ledger, and Phase 28.1 WIP status
remains in the Phase 28.1 ledger; exploratory history remains non-normative in
the associated note and journal.
