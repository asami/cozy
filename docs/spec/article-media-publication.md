# Article Media Publication and BoK Integration Specification

## Contract Basis

This specification governs Cozy Phase 26 implementation. Its SmartDox input is
the accepted closed SmartDox Phase 1 commit
`fa21316973416c24bca7f8e366d65572c72720b7`, integrated in development through
`org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`. A public/non-SNAPSHOT SmartDox
release is not required to start Phase 26.

## SmartDox Input Surface

A Cozy producer emits an `article-media-publication` record only with the
accepted SmartDox fields:

```json
{
  "type": "article-media-publication",
  "article": { "identity": "development-process/example" },
  "variants": {
    "ja": {
      "infographic": {
        "public_path": "/ja/development-process/images/example.png",
        "media_type": "image/png",
        "alt": "詳細インフォグラフィック"
      },
      "video": {
        "presentation": "site-hosted",
        "status": "published",
        "content_url": "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"
      }
    }
  }
}
```

`type`, `article.identity`, and exact canonical locale `variants` are required.
An infographic has required `public_path` and optional `media_type` and `alt`.
A video has required `presentation` and `status`, plus optional `provider`,
`watch_url`, and `content_url`. No Cozy-owned integrity field is permitted in
this record. A published `site-hosted` video requires `content_url`; a published
external link requires `watch_url`, as defined by SmartDox.

The SmartDox video status enum is exactly `draft`, `published`, or
`withdrawn`. A watch-only `external-link` is valid accepted SmartDox input and
has no Cozy artifact correlation. A draft or withdrawn video with no
`content_url` is also valid and has no integrity record. If a nonprojectable
site-hosted video nevertheless has `content_url`, it is path-bearing and must
correlate. The Cozy producer/validator must emit only these accepted SmartDox
fields and must never serialize integrity fields into this record; this
specification does not require SmartDox to reject unknown producer fields.

## Registry Container and Key Paths

Both record surfaces are entries in the existing configured
`src/main/publication` bundle format, never loose files discovered from a
directory. A producer upserts these exact entry paths using deterministic field
ordering:

- `metadata/article-media/<articleIdentity>.json` contains one strict SmartDox
  record per normalized `articleIdentity`; its locale variants are sorted by
  exact canonical locale tag.
- `metadata/article-media-integrity/<articleIdentity>/<locale>/<role>.json`
  contains exactly one Cozy integrity record per normalized tuple, with `role`
  exactly `infographic` or `video`.

`articleIdentity` is the accepted normalized non-empty SmartDox site-relative
identity, `locale` is its exact canonical tag, and `role` is the fixed enum.
They form both key and path. Before path construction, empty, `.`, `..`, or
leading-slash segments fail. Duplicate normalized identity/locale/role keys
across configured bundles fail. `cozy bok build` enumerates only configured
publication bundle entries through the existing registry loader; it does not
inspect arbitrary directories.

## Cozy Integrity Surface

Cozy serializes the separate association/integrity projection as
`cozy.article-media-integrity.v1`. Each record is keyed uniquely by normalized
`(articleIdentity, locale, role)`, where `role` is `infographic` or `video`.

```json
{
  "schema": "cozy.article-media-integrity.v1",
  "articleIdentity": "development-process/example",
  "locale": "ja",
  "role": "video",
  "artifact": { "identity": "example-video", "version": "1.0.0" },
  "publicPath": "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4",
  "repositoryPath": "video/example-video/1.0.0/example-video-1.0.0.mp4",
  "mediaType": "video/mp4",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "provenance": {
    "kind": "video-publication",
    "videoManifest": "metadata/video/example-video/1.0.0/manifest.json",
    "repositoryRegistry": "metadata/artifacts/repository/example-video.json"
  },
  "publicationState": "published"
}
```

The example digest is illustrative and syntactically valid; it does not claim
to be the digest of a checked-in artifact.

The record must carry artifact identity and version, site-visible public path,
repository path, media type where applicable, SHA-256, provenance/registry
reference, and validation-relevant publication state. The required top-level
field set is exactly `schema`, `articleIdentity`, `locale`, `role`, `artifact`,
`publicPath`, `repositoryPath`, `mediaType`, `sha256`, `provenance`, and
`publicationState`; `schema` is exactly `cozy.article-media-integrity.v1`.
`artifact.identity` and `artifact.version` are non-empty, and `sha256` is
exactly 64 lowercase hexadecimal characters with no prefix. All identities and
paths are source-relative or publication-relative. Machine absolute paths are
invalid. The registry location introduced by Phase 26 is only this
association/integrity projection; it must not duplicate existing video
manifests.

`publicationState` is Cozy-owned and exactly `registered`, `published`, or
`withdrawn`; it is not derived from or serialized as SmartDox status.
`registered` means explicit metadata and integrity evidence exist, but the
artifact is not admitted to production; preview may diagnose and omit it.
`published` means the artifact exists inside the configured repository, its
selected evidence paths, version, and hash match, and it is eligible for
production staging. `withdrawn` means the artifact is intentionally unavailable
for production and cannot support a projected published site-hosted video.

`publicPath` is a SmartDox-accepted root-relative site-visible URI beginning
with exactly one `/`. `repositoryPath` is always relative to the configured
artifact repository root. It resolves under that root and must remain there
after normalization and real-path/symlink checks; lexical and symlink escapes
fail before production staging. Existing video `warehousePath` values include
`repository/`; their canonical resolution first strips that configured
repository prefix, yielding `repositoryPath` beneath the configured root, while
`publicPath` retains `/repository/...` for the staged URL.

`provenance` is role-discriminated. A video record requires
`kind: video-publication`, registry-relative `videoManifest`, and registry-
relative `repositoryRegistry`. An infographic record requires
`kind: media-package`, a project-relative `descriptor`, exact `resourceId`, and
a project-relative `buildManifest`. Neither provenance form permits an absolute
path.

### Video Mapping

The selected evidence is exactly
`metadata/video/<name>/<version>/manifest.json` and
`metadata/artifacts/repository/<name>.json`. `artifact.identity` is the video
name and `artifact.version` is the selected video version. `publicPath` is the
root-relative projection of the manifest's normalized repository-relative
`artifact.repositoryPublicPath`: prepend exactly one `/` to its raw
`repository/...` value with no other rewrite. The strict SmartDox `content_url`
is byte-identical to that `publicPath`. Existing `artifact.sitePublicPath`
remains legacy `VideoPublication`/descriptor alias evidence and is never
substituted into the strict record. `repositoryPath` is the manifest's
`artifact.warehousePath` after the configured-root conversion defined above.
`mediaType` is `video/mp4` for the selected MP4. `sha256` is the
manifest artifact SHA. In the repository registry `artifact.files[]`, select
exactly one entry whose selected artifact/version matches the video, whose
`type` is `video`, and whose `warehousePath` equals the manifest
`artifact.warehousePath`; missing or ambiguous selection fails. The manifest
artifact SHA, that selected registry entry SHA, and the actual selected file
bytes must be equal. Video `publicationState` derives from that evidence,
containment, and availability, never SmartDox status.

### Infographic Mapping

Select exactly one explicit `cozy.media.v1` resource whose `knowledge.id`
equals `articleIdentity`, whose `language` canonicalizes exactly to `locale`,
and whose `role` is literally `detailed-infographic`. Its resource id is
`artifact.identity`. Initial Phase 26 infographic support is PNG only:
`kind=image`, a publication destination ending in `.png`, and
`mediaType=image/png`; another format fails as unsupported until a later
specification extension.

`artifact.version` is the non-empty explicit Phase 26 publication-operation
version from `cozy bok update-publication` or `cozy bok publish --version`.
Its absence is an integrity-projection error because `cozy.media.v1` has no
version field. `buildManifest` is the explicitly resolved
`target/cozy-media/manifest.json` produced for that descriptor, not discovered
by scanning; the selected entry must match `resourceId` and provide its
lowercase SHA-256. That entry SHA, the published destination file bytes SHA,
and the integrity-record `sha256` must be equal; a missing value or mismatch
fails. `publicPath` is the selected explicit publication-profile destination
normalized as a site-visible path. `repositoryPath` is that destination
relative to the configured artifact repository root. The selected Phase 26
profile root must be that configured repository and containment must hold.
Infographic `publicationState` derives from the explicit publish result,
existence, hash, and containment.

## Correlation and Operations

Every Cozy-produced infographic and every Cozy-produced site-hosted video with
`content_url` must select exactly one integrity record using its normalized
`(articleIdentity, locale, role)`. The SmartDox infographic `public_path` or
site-hosted video `content_url` must exactly equal that record's `publicPath`.
No correlation/equality is applied to external-link `watch_url` or URL-less
draft/withdrawn forms. A published site-hosted SmartDox video may project only
when its correlated Cozy `publicationState` is `published`; `registered` and
`withdrawn` cannot support that projection. Duplicate normalized keys, an
invalid or ambiguous identity, an escaping path, a path outside the configured
repository, and hash or version mismatch fail deterministically before
production staging. Path escape and identity conflict fail under every strategy.

Only explicit descriptors and registered metadata are inputs. Neither producer
nor build may scan `target`, work directories, generated site trees, arbitrary
repository directories, or SmartDox discovery output. `cozy bok publish-video`
and media publication create/update records. Ordinary `cozy bok build` reads
the registry and passes existing publication/repository context to SmartDox;
it performs no rendering or transcoding.

## Policy Matrix

| Condition | Preview | Production |
| --- | --- | --- |
| No association | ordinary article | ordinary article |
| Exact canonical locale absent | no cross-locale fallback | no cross-locale fallback |
| Optional infographic has no registered record | diagnostic/omission | omission |
| Registered infographic is missing or stale | diagnostic/omission | fail before staging |
| Registered site-hosted published video is missing/stale or not Cozy-published | diagnostic; no player | fail before staging |
| SmartDox draft or withdrawn video without `content_url` | valid; no integrity record or projection/player/link | valid; no integrity record or projection/player/link |
| External-link video with only `watch_url` | accepted pass-through; no integrity record | accepted pass-through; no integrity record |
| Invalid/ambiguous identity, duplicate key, escaped path, outside repository, hash/version mismatch | deterministic failure where identity conflict or path escape always fails | deterministic failure before staging |

The production omission rule applies only when no infographic is registered.
It never permits a registered-but-missing or stale infographic.

## Compatibility and Required Evidence

The existing SmartDox `VideoPublication` and `.video` path remain compatible.
An exact-locale native record is a complete variant and wins over compatibility
input. Cozy must not extend SmartDox's producer schema, implicitly merge native
and legacy records, or apply locale fallback.

Executable specifications added during implementation must demonstrate:

- strict Cozy producer/validator SmartDox field emission without serialized
  integrity extensions;
- conditional exact-key correlation and public-path equality for path-bearing
  media, plus valid watch-only and URL-less nonprojectable forms;
- deterministic bundle entry paths, upsert/ordering, duplicate rejection,
  state derivation, provenance variants, and configured-root containment;
- descriptor/registered-metadata-only production with no scan or heavy build
  work;
- every policy-matrix outcome, including preview diagnostics and production
  staging failures; and
- preserved `.video`/`VideoPublication` compatibility plus unchanged
  media-free articles and BoKs.

These requirements preserve the publication boundaries accepted in Phases 9,
10, and 19.
