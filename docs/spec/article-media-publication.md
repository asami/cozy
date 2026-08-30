# Article Media Publication and BoK Integration Specification

## Contract Basis

This specification governs the Cozy Phase 26 base implementation and the
explicitly additive Phase 28.1 `wip-site-video` integrity extension. Its
SmartDox input is the accepted closed SmartDox Phase 9 contract, integrated in
development through `org.smartdox:smartdox_2.12:2.4.18-SNAPSHOT`. A
public/non-SNAPSHOT SmartDox release is not required to start Phase 26.

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
      "article_pdf": {
        "public_path": "/ja/development-process/pdf/example-article.pdf",
        "media_type": "application/pdf",
        "label": "記事 PDF"
      },
      "summary_slides_pdf": {
        "public_path": "/ja/development-process/pdf/example-summary.pdf",
        "media_type": "application/pdf",
        "label": "要約スライド PDF"
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

### Localized PDF Roles

SmartDox Phase 9 already defines the direct `article_pdf` and
`summary_slides_pdf` variant fields consumed here. Each present field is the
accepted `PublishMetadata.PdfDocumentReference` shape: required site-visible
`public_path`, required `media_type` exactly `application/pdf`, and optional
nonblank `label`. The enclosing key is the exact canonical locale variant;
Cozy performs no filename inference, locale fallback, or role merging.

These PDF fields remain strict provider metadata only. They never carry a
generic `role`, artifact identity, hash, provenance, repository path, or Cozy
integrity value. `cozy.article-media-integrity.v1` continues to apply only to
the existing infographic and video roles. This section consumes the accepted
SmartDox Phase 9 contract and does not redefine or implement the SmartDox
schema or projection.

## Registry Container and Key Paths

For the Phase 26 repository-backed `media-package` and `video-publication`
producers, both record surfaces are entries in the existing configured
`src/main/publication` bundle format, never loose files discovered from a
directory. A Phase 26 producer upserts these exact entry paths using
deterministic field ordering:

- `metadata/article-media/<articleIdentity>.json` contains one strict SmartDox
  record per normalized `articleIdentity`; its locale variants are sorted by
  exact canonical locale tag.
- `metadata/article-media-integrity/<articleIdentity>/<locale>/<role>.json`
  contains exactly one Cozy integrity record per normalized Phase 26 producer
  tuple, with `role` exactly `infographic` or `video`.

`articleIdentity` is the accepted normalized non-empty SmartDox site-relative
identity, `locale` is its exact canonical tag, and `role` is the fixed enum.
They form both key and path. Before path construction, empty, `.`, `..`, or
leading-slash segments fail. Duplicate normalized identity/locale/role keys
across configured bundles fail. `cozy bok build` enumerates only configured
publication bundle entries through the existing registry loader; it does not
inspect arbitrary directories. Phase 27 site-local infographic and Phase 28.1
WIP reuse of that infographic strict record create no new integrity entry and
are outside these Phase 26 producer requirements.

PDF roles are preserved in the strict article entry at their direct
`article_pdf` and `summary_slides_pdf` fields. They do not create an integrity
entry or alter the integrity key space.

All recognized strict and integrity entries for one normalized
`articleIdentity` form one ownership unit. Under the real-publication-root lock,
a producer collects distinct owner bundle names from both the strict entry path
and the complete integrity prefix. More than one owner fails before mutation;
exactly one owner is reused even when it contains integrity-only state. With no
owner, the canonical owner is `article-media`: reuse valid existing
`article-media.json`, or create an empty canonical `publication-bundle` named
`article-media` inside the same locked transaction before its atomic
replacement. Later video/infographic roles and locales reuse that owner. A
producer never implicitly moves an article between bundles, and generic or
unrelated owner entries remain preserved.

## Explicit Producer Inputs and Commands

`cozy bok publish-media <project-dir> [--publication <dir>] [--repository
<dir>] [--version <version>] [--force]` is the explicit infographic producer.
It enumerates only the exact media descriptor filenames recognized by the
`cozy.media.v1` decoder below the configured BoK source: exactly `media.yaml`,
`media.yml`, `media.json`, `media.conf`, and `media.xml`, consistent with
`docs/spec/media-package.md`. It searches package directories at or below that
source without following symlink directories. A candidate is a direct regular
non-symlink file; more than one eligible basename in one directory is a
deterministic command-wide preflight collision. The candidate content must still
decode exactly as `cozy.media.v1`. For a selected resource it publishes only its
explicit `detailed-infographic` resource through the uniquely selected declared
publication profile whose root and configured artifact repository are both
existing directories and have equal normalized absolute lexical identities and
equal canonical real identities. A symlink or lexical alias thus does not
match. Zero or multiple matching profile names fail before publication or
mutation. It then creates or updates article-media records. It never infers an
article identity, locale, role, destination, or descriptor from a directory,
filename convention, target output, repository content, or SmartDox output.

Only a resource with a selected destination in that profile participates. Its
`knowledge.id` is the article identity; its `language` is the exact locale; and
its role must literally be `detailed-infographic`. Optional strict SmartDox
`alt` is omitted unless it is already explicitly representable in the existing
media schema; this contract does not extend that schema. `--version` is required
for every selected infographic article-media resource. Its absence fails before
any mutation. `cozy bok update-publication` and `cozy bok publish` include this
operation alongside their video and project operations. Dry-run remains owned
by one-stop `publish`, not by this command unless an existing command contract
already provides it.

A `.video` descriptor opts into strict article-media output only with its
optional nested `publish.articleMedia` block. Its canonical YAML fields are
`articleIdentity`, `locale`, and `status`; all three are required together.
The block requests a site-hosted strict SmartDox video variant. `status` is
exactly the SmartDox value `draft`, `published`, or `withdrawn`, and is never
derived from Cozy `publicationState`. Its `content_url` derives only from
validated repository evidence. Existing top-level `article` source path,
`locale`, `publish.module`, and `publish.publicPath` neither opt in nor supply
the strict association. Existing descriptors without this block remain
legacy-only and byte/behavior compatible. Decoder aliases, if any, are an
implementation compatibility detail; the canonical serialized contract is the
camelCase form above.

Descriptor enumeration is producer-time explicit-package enumeration only.
Ordinary `cozy bok build` never enumerates descriptors.

## Cozy Integrity Surface

Cozy serializes the separate association/integrity projection as
`cozy.article-media-integrity.v1`. Each record is keyed uniquely by normalized
`(articleIdentity, locale, role)`, where `role` is `infographic` or `video`.
PDF roles are intentionally outside this integrity surface.

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
paths are source-relative or publication-relative except the explicitly scoped
Phase 28.1 `wip-site-video` website-root-relative `repositoryPath`. Machine
absolute paths are invalid. The registry location introduced by Phase 26 is only this
association/integrity projection; it must not duplicate existing video
manifests.

`publicationState` is Cozy-owned and exactly `registered`, `published`, or
`withdrawn`; it is not derived from or serialized as SmartDox status. For the
existing `video-publication` and `media-package` provenance kinds,
`registered` means explicit metadata and integrity evidence exist but the
artifact is not admitted to production; preview may diagnose and omit it;
`published` means the artifact exists inside the configured repository, its
selected evidence paths, version, and hash match, and it is eligible for
production staging; and `withdrawn` means it is intentionally unavailable for
production and cannot support a projected published site-hosted video. The sole
`wip-site-video` exception has the Phase 28.1 disposable installation meaning
specified below and MUST NOT be treated as production-eligible
artifact-repository evidence.

`publicPath` is a SmartDox-accepted root-relative site-visible URI beginning
with exactly one `/`. For `video-publication` and `media-package`,
`repositoryPath` is relative to the configured artifact repository root. It
resolves under that root and must remain there after normalization and
real-path/symlink checks; lexical and symlink escapes fail before production
staging. Existing video `warehousePath` values include
`repository/`; their canonical resolution first strips that configured
repository prefix, yielding `repositoryPath` beneath the configured root, while
`publicPath` retains `/repository/...` for the staged URL.

`provenance` is role-discriminated. A `video-publication` video record requires
`kind: video-publication`, registry-relative `videoManifest`, and registry-
relative `repositoryRegistry`. An infographic record requires
`kind: media-package`, a project-relative `descriptor`, exact `resourceId`, and
a project-relative `buildManifest`. Neither provenance form permits an absolute
path. The additive Phase 28.1 video alternative is exactly
`kind: wip-site-video` with only project-root-relative normalized `descriptor`,
exact `resourceId`, and project-root-relative normalized `production` besides
`kind`; it changes neither the schema name nor any top-level field. Its
`artifact.identity` is exactly `resourceId`; `artifact.version` is the exact
64-character source SHA-256 as an opaque content-addressed WIP version;
`publicPath` equals the strict `content_url`; `repositoryPath` is the website-
root-relative destination; `mediaType` is `video/mp4`; and the source, staged,
and installed destination digests are equal to `sha256`. For this provenance
only, `repositoryPath` does not mean artifact-repository-relative and
`publicationState: published` means installed in the disposable WIP tree.
The strict SmartDox record remains free of these values and is correlated by
the ordinary role-update semantics; no `SiteRoleUpdate` exception is defined.

### Video-publication Mapping

For `video-publication`, the selected evidence is exactly
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

### Media-package Infographic Mapping

For `media-package`, select exactly one explicit `cozy.media.v1` resource whose `knowledge.id`
equals `articleIdentity`, whose `language` canonicalizes exactly to `locale`,
and whose `role` is literally `detailed-infographic`. Its resource id is
`artifact.identity`. Initial Phase 26 infographic support is PNG only:
`kind=image`, a publication destination ending in `.png`, and
`mediaType=image/png`; another format fails as unsupported until a later
specification extension.

`artifact.version` is the non-empty explicit Phase 26 publication-operation
version from `cozy bok publish-media --version`, `cozy bok update-publication
--version`, or one-stop `cozy bok publish --version`. Its absence fails before
mutation because `cozy.media.v1` has no version field. `buildManifest` is the
explicitly resolved
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

For Phase 26 repository-backed `media-package` and `video-publication`
producers, every Cozy-produced infographic and every Cozy-produced site-hosted
video with `content_url` must select exactly one integrity record using its
normalized `(articleIdentity, locale, role)`. The SmartDox infographic
`public_path` or site-hosted video `content_url` must exactly equal that
record's `publicPath`. No correlation/equality is applied to external-link
`watch_url` or URL-less draft/withdrawn forms. For `video-publication` and
`media-package`, a published site-hosted SmartDox video may project only when its correlated Cozy
`publicationState` is `published`; `registered` and `withdrawn` cannot support
that projection. `wip-site-video` is governed instead by Phase 28.1 disposable
website installation and is never production staging evidence. Duplicate
normalized keys, an invalid or ambiguous identity, an escaping path, a path
outside the configured artifact repository for existing provenance kinds, and
hash or version mismatch fail deterministically before production staging. Path
escape and identity conflict fail under every strategy.

Only explicit descriptors and registered metadata are inputs. Neither producer
nor build may scan `target`, work directories, generated site trees, arbitrary
repository directories, or SmartDox discovery output. Producer enumeration is
allowed only for exact decoder-recognized descriptor filenames beneath the
configured BoK source. `cozy bok publish-video` and `cozy bok publish-media`
create or update records.

Registration is a role-local exact-key merge. The producer holds the same
real-publication-root lock from complete snapshot/preflight through artifact
publication and atomic registry replacement, so no other article-media producer
or build can interleave. Before any artifact or registry mutation it enumerates
the complete command candidate set and validates every descriptor, selected
resource, normalized key, non-empty explicit version, uniquely matching
profile, source/output/build manifest, destination and containment, current
destination/force rule, and complete registry ownership/snapshot. It groups the
whole command by normalized `(articleIdentity, locale, infographic)` and fails
deterministically on every duplicate, including one across descriptors or
resources.

After complete preflight, artifact publication completes before registry
replacement; a publication failure leaves the registry unchanged. An absent
destination is published. An existing destination with a SHA equal to the
validated output is idempotent and receives no byte replacement, although its
registry may be refreshed. A differing SHA fails without `--force`; with
`--force` it is replaced only by a staged same-filesystem atomic move after
complete preflight. `--force` never bypasses schema, identity, uniqueness,
containment, manifest/SHA, ownership, or snapshot checks. This specifies safe
staged/replace behavior and does not claim multi-file artifact atomicity.

Each operation then replaces only `(articleIdentity, locale, role)`, preserves
unaffected roles/locales for that article and every unrelated/generic entry,
reconstructs the complete canonical strict article record, revalidates the
complete bundle-digest snapshot, and atomically replaces the owner bundle.
Malformed, duplicate, conflicting, or changed snapshot state fails before
mutation. An operation must not lose a concurrent update. One-stop
`cozy bok publish --dry-run` executes the identical non-mutating enumeration and
preflight, including duplicate/version/profile/manifest/destination/current
ownership/force checks, reports planned candidates, and performs no copying,
bundle creation or replacement, staging, or upload.

Ordinary `cozy bok build` accepts exactly the canonical strategy values `draft`,
`work-in-progress`, `production-preview`, and `production`. CLI `wip` and
`preview` normalize before policy selection. `draft`, `work-in-progress`, and
`production-preview` select Preview; `production` selects Production. Any other
value fails before registry load, path construction, or an external command.
Build loads only configured registry bundles and the configured repository root.
It holds the same real-publication-root lock as producers from complete snapshot
load and digest capture through policy evaluation, effective-context
materialization, source-digest revalidation, and immutable snapshot installation.
Build materializes the effective context in a unique sibling work directory and
computes `contextDigest` as exactly 64 lowercase hexadecimal SHA-256 over the
canonical strategy and the complete effective publication context. Define
`U64(n)` as exactly eight octets containing the non-negative integer `n` in
unsigned 64-bit big-endian/network byte order; every length below counts
octets, never characters. The canonical strategy and every normalized
publication-root-relative bundle filename (using `/` separators) are encoded
as their exact UTF-8 octets. Bundle files are sorted by unsigned lexicographic
comparison of their UTF-8 filename octets. The SHA-256 input is exactly:

```
U64(strategyByteLength) || strategyBytes || U64(fileCount) ||
for each sorted file:
  U64(filenameByteLength) || filenameBytes ||
  U64(contentByteLength) || exactFileBytes
```

There are no delimiters, terminators, platform newline conversions, text
re-encodings, filesystem metadata, directory entries, or absolute paths in
this input; `fileCount` counts framed regular bundle files. `contextDigest` is
the lowercase hexadecimal SHA-256 result over exactly that octet sequence.
Preview omissions are included because the effective output is hashed.

The final context path is exactly
`target/cozy-bok/article-media/<canonical-strategy>/snapshots/<contextDigest>/publication`.
The context copies all unaffected generic/legacy entries, rewrites strict
article entries to omit exactly Preview-policy omitted roles, and removes empty
variants/entries. Build verifies the source digest again before atomically
installing the completed snapshot directory. If the digest path already exists,
it verifies the complete relative-file set and bytes against `contextDigest` and
reuses that directory without mutation; a mismatch fails deterministically.
Concurrent producer/build work therefore serializes. The shared registry lock
is released only after the immutable snapshot is fully installed. Every pinned
SmartDox, Dox, Antora, and site consumer receives that exact digest-addressed
path as `-publication`, with configured `-publication.repository` behavior
unchanged. The build invocation leases the snapshot for all consumers and does
not replace or remove it while any consumer runs. Installed snapshots remain a
target cache; any future cleanup is separately coordinated and may remove only
snapshots without an active lease, and this specification defines no cleanup
CLI.

The digest-addressed snapshot solves publication-context handoff lifetime only;
artifact and repository admission remain protected and validated by the existing
producer and policy contracts. Production policy failure occurs before any
external build command. Build performs no renderer, transcoder, media
publication, target discovery, generated-site scan, arbitrary repository scan,
descriptor enumeration, or locale fallback. Media-free registry/build behavior
remains unchanged apart from this deterministic snapshot handoff path.

Pinned SmartDox owns exact-locale article and global/category Notice projection;
Cozy acceptance observes it without reimplementation. The stage workflow puts
website output at the staging root and repository artifacts at
`<staging-root>/repository`; registered public paths and staged bytes must
therefore resolve below one URL root and have matching SHA-256 values. No-media
articles/BoKs and legacy `.video`/`VideoPublication` behavior remain unchanged.

## Phase 26 Policy Matrix

| Condition | Preview | Production |
| --- | --- | --- |
| No association | ordinary article | ordinary article |
| Exact canonical locale absent | no cross-locale fallback | no cross-locale fallback |
| Optional infographic has no registered record | diagnostic/omission | omission |
| Registered infographic is missing or stale | diagnostic/omission | fail before staging |
| Registered `video-publication` or `media-package` site-hosted published video is missing/stale or not Cozy-published | diagnostic; no player | fail before staging |
| SmartDox draft or withdrawn video without `content_url` | valid; no integrity record or projection/player/link | valid; no integrity record or projection/player/link |
| External-link video with only `watch_url` | accepted pass-through; no integrity record | accepted pass-through; no integrity record |
| Invalid/ambiguous identity, duplicate key, escaped path, outside repository for existing provenance kinds, hash/version mismatch | deterministic failure where identity conflict or path escape always fails | deterministic failure before staging |

This matrix governs the Phase 26 repository-backed provenance kinds;
`wip-site-video` is governed exclusively by Phase 28.1 and is not production
eligible. The production omission rule applies only when no infographic is registered.
It never permits a registered-but-missing or stale infographic.

## Compatibility and Required Evidence

The existing SmartDox `VideoPublication` and `.video` path remain compatible.
An exact-locale native record is a complete variant and wins over compatibility
input. Cozy must not extend SmartDox's producer schema, implicitly merge native
and legacy records, or apply locale fallback. The accepted SmartDox Phase 9
PDF roles remain direct strict fields under this same exact-locale rule; Cozy
does not add SmartDox schema or projection behavior for them.

Executable specifications added during implementation must demonstrate:

- strict Cozy producer/validator SmartDox field emission without serialized
  integrity extensions;
- conditional exact-key correlation and public-path equality for path-bearing
  media, plus valid watch-only and URL-less nonprojectable forms;
- deterministic bundle entry paths, upsert/ordering, duplicate rejection,
  state derivation, provenance variants, configured-root containment, complete
  article ownership selection (including integrity-only and conflict cases), and
  owner-preserving role/locale merge;
- exact descriptor basename/direct-file/symlink and same-directory collision
  rejection; full-command candidate duplicate preflight; explicit version and
  unique lexical-and-real-root profile selection;
- absent/equal/differing destination SHA behavior, `--force` staged replacement,
  non-mutating one-stop dry-run, and a failed publication that leaves registry
  state unchanged;
- descriptor/registered-metadata-only production with no scan or heavy build
  work, plus canonical strategy/alias selection, unsupported-strategy rejection,
  locked snapshot/digest consistency, content-addressed immutable snapshot
  installation and reuse, and immutable effective-context handoff; concurrent
  same-strategy builds must show that build A keeps consuming its installed
  snapshot while build B publishes a different effective state at a different
  digest path, and same-content reuse must show no mutation;
- at least one fixed golden framing vector and expected `contextDigest` whose
  exact octet sequence is exercised by both the producer and verifier, in
  addition to the concurrent-build and same-content-reuse evidence above;
- every policy-matrix outcome, including preview diagnostics and production
  staging failures; and
- preserved `.video`/`VideoPublication` compatibility plus unchanged
  media-free articles and BoKs.

These requirements preserve the publication boundaries accepted in Phases 9,
10, and 19.
