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

WIP production admission requires the actual selected production JSON object,
normalized category/article identity, exact locale, completed render, and
technical-and-visual QA, together with the required production and resolved
output paths. It explicitly adopts the valid supplied MP4 without a local
`render.sha256` gate; ordinary JSON loading ignores extra properties without
dedicated hash compatibility processing. Ordinary local `FileEvidence` observes
normalized lexical/real paths, size, actual `FileTime` modification time, and
fileKey, with direct regular non-symlink checks and coherent two-read
bytes/attributes. Exact descriptor and selected production JSON bytes are
transient observations for revalidation during that operation, including
formatting rewrites that preserve size, time, and file identity. They supply
no persistent identity or freshness token and no PDF digest. The explicit
source and optional installed artifact digests apply only to the selected MP4
and its canonical integrity record, staged copy, backup, installation, and
restoration. Full MP4 bytes are not retained across the command.

The existing locked WIP transaction preserves registry-last replacement,
verified copy/backup/restore bytes, and operation cleanup. On rollback, complete
original registry equality includes actual bundle bytes. Unchanged root,
source, production, context, candidate, variant, and integrity evidence remains
mandatory, as does before/restored optional destination digest equality.
Restoration recomputes ordinary direct-path planning and accepts the actual
destination inode and modification time installed by atomic backup restoration;
it does not fabricate the old fileKey. This restoration-only predicate does not
relax strict pre-mutation plan revalidation or installed-artifact admission.
A successful rollback reports the original application failure; invalid
restoration remains a distinct rollback failure. PDF receipt and selected
review-state currentness, reuse-only roles, and WIP production isolation remain
unchanged.

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
by scanning. Its JSON object, schema, knowledge identity, resource objects,
unique selected `resourceId`, and exact selected build path must match the
descriptor. The actual `CozyMedia.build` receipt is admitted through
`CozyMediaReceipt.requireCurrent` with the selected profile: actual declared
producer inputs, file existence, independent validity, and modification times
determine currentness. For a copy resource, a newer declared copy source fails
with the actual source, output, and next build operation; a newer knowledge
document alone does not invalidate unchanged copy-source/output evidence. The
selected manifest entry supplies no local SHA, freshness hash, or approval
identity.

Preparation checks the actual direct regular operation source PNG against
`PreparedPublication.sourceSha256` and uses that verified value in the named
artifact-integrity record. Completion checks the installed destination PNG
against that prepared integrity SHA before exposing the strict role. Projection
first admits the semantic current manifest, then compares the actual selected
build PNG bytes with the actual published destination bytes and records their
matching SHA-256. Actual build/published PNG disagreement fails. These checks
protect publication artifact integrity and do not determine local currentness
or approval. `publicPath` is the selected explicit publication-profile destination
normalized as a site-visible path. `repositoryPath` is that destination
relative to the configured artifact repository root. The selected Phase 26
profile root must be that configured repository and containment must hold.
Infographic `publicationState` derives from the explicit publish result,
existence, actual artifact integrity, and containment.

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
containment, semantic manifest attribution/currentness, actual artifact
integrity, ownership, or snapshot checks. This specifies safe
staged/replace behavior and does not claim multi-file artifact atomicity.

Each operation then replaces only `(articleIdentity, locale, role)`, preserves
unaffected roles/locales for that article and every unrelated/generic entry,
reconstructs the complete canonical strict article record, revalidates the
complete transient bundle-byte snapshot, and atomically replaces the owner bundle.
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
load through policy evaluation, effective-context materialization, source-byte
revalidation, and immutable snapshot installation. The registry observation is
an in-memory map from admitted bundle name to the exact immutable bytes read
and parsed by this operation. Projections preserve that observation; planned
metadata does not manufacture persisted proof. Complete-map comparison includes
an initially empty registry, bundle additions/removals, same-size rewrites with
preserved modification times, and whitespace-only rewrites. The observation is
never serialized or used as approval, freshness, equivalence, or cache evidence.
Optional empty expectations at existing upsert/replace interfaces still mean no
caller-supplied expectation; those operations capture their own actual snapshot.
Strict locked snapshot validation compares even empty maps.

Each invocation, including empty and byte-identical repeated inputs, builds a
fresh context in a unique `.work-` directory beneath
`target/cozy-bok/article-media/<canonical-strategy>/snapshots`. The completed
sibling is named `snapshot-` followed by that work directory's unique suffix,
with the effective metadata in its direct `publication` directory. These names
allocate filesystem paths only; their suffixes carry no semantic identity,
freshness, approval, equivalence, or cache-key meaning and are never serialized
into product metadata.

Build compares each configured source bundle's bytes with its original
observation before copying. It preserves unaffected generic/legacy entries,
omits exactly policy-selected Preview strict roles, and removes empty strict
variants/entries. After those transformations, it captures the complete direct
filename-to-immutable-byte map and requires exactly the original admitted
filenames and direct regular files. It revalidates the original registry map
under the lock, compares the effective bytes again, requires an absent final
target, and atomically installs this operation's completed directory. A target
collision or unsupported/failed atomic move fails without reusing, replacing,
or removing another context. Installed directories, filenames, and bytes are
verified before the consumer. Failure cleans only this operation's work or its
newly installed failed directory.

The shared lock is released only after installation. Every pinned SmartDox,
Dox, Antora, and site consumer receives the fresh `-publication` path, with
configured `-publication.repository` behavior unchanged. The invocation leases
its context across consumers and releases the lease in `finally`, including
consumer failure. A producer update and later materialization cannot alter an
earlier consumer's installed bytes. Earlier contexts, even edited or containing
extra files, are never consulted when admitting a new context and remain
untouched. There is no context cache, persistent index, content addressing, or
old-context cleanup operation.

Phase 71.3 supersedes the former registry digest observation, context digest
framing/reuse guarantees, and local infographic manifest SHA requirement;
accepted provider schemas remain unchanged.
Artifact and repository admission retain their existing producer and policy
contracts, including actual published-byte mismatch rejection. Production
rejects missing, invalid, or unavailable required published artifacts before
any consumer. Build does not render, transcode, publish, discover media, scan
generated-site/arbitrary repository paths, enumerate descriptors, or perform
locale fallback. Media-free behavior retains the same policy and handoff
semantics with a fresh context path.

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
- actual hash-free infographic build receipt admission through projection and
  prepare/complete, newer declared copy-input rejection before artifact or
  registry mutation with actionable source/output/build diagnostics, generated
  knowledge-only timestamp changes with unchanged copy PNG integrity, and
  actual build/published PNG mismatch rejection;
- descriptor/registered-metadata-only production with no scan or heavy build
  work, plus canonical strategy/alias selection, unsupported-strategy rejection,
  complete transient byte-observation consistency and fresh immutable context
  installation; concurrent builds must show that an earlier consumer retains
  its installed bytes while a later consumer obtains updated source bytes;
- distinct same-content and empty contexts with equal effective bytes, released
  leases, and unchanged earlier bytes/attributes; earlier edited/extra-file
  contexts are ignored and preserved;
- complete bundle-set addition/removal and initially empty snapshot conflicts,
  same-size timestamp-preserved source races, and equivalent JSON formatting
  rewrites that reject before mutation/install/consumer invocation;
- generated valid configured-bundle write orders preserve exact filenames and
  copied bytes under independently reset property trials;
- every policy-matrix outcome, including preview diagnostics and production
  staging failures; and
- preserved `.video`/`VideoPublication` compatibility plus unchanged
  media-free articles and BoKs.

These requirements preserve the publication boundaries accepted in Phases 9,
10, and 19.

## Phase 75 canonical existing-artifact deployment (2026-10-06)

`cozy media deploy <media.yaml> --profile <id> [--warehouse-root <absolute-directory>] [--target <resource-id>]
[--dry-run]` places explicitly declared, already produced artifacts in a local
warehouse. This additive command does not build, render, transcode, publish
remotely, register site metadata, generate receipts or compute hashes. The
historical Phase 26/28.1 publication and Phase 71 contracts above remain intact.
Site context (`--site-root` / `--site-config`) is unsupported for deployment.

The existing `cozy.media.v1` descriptor must have unique resource IDs, explicit
`articleMedia.articleIdentity` and the selected `profiles.<id>` declaration.
Project-configured profiles are not fallback inputs. Profile `rootEnv` takes
precedence over `root`: its named environment value must be nonblank and exact;
a missing value fails rather than falling back. Otherwise `root` is required.
A relative root resolves against the descriptor parent; the normalized absolute
root must already exist as a direct non-symlink directory, with no symlink
ancestors. Reusable code and public metadata carry no machine-specific root.

Only resources declaring `publications.<id>` participate. A target must name an
existing resource mapped to that profile; an empty selection fails. Unmapped
resources and auxiliary assets are not discovered or deployed. Identity is
exactly `category/article`, two segments matching
`[A-Za-z0-9_-][A-Za-z0-9._-]*`, excluding `.` and `..`. The article segment is
the logical source filename stem (for a Document Project, its logical name,
not physical `index`); category/article is shared across JA and EN. Locale is
explicitly `ja` or `en`, and role comes only from resource `articleMedia.role`.

| Role | Required kind / format | Canonical basename |
| --- | --- | --- |
| `article_pdf` | `document`, `application/pdf`, PDF source filename | `article.pdf` |
| `summary_slides_pdf` | `document`, `application/pdf`, PDF source filename | `slides.pdf` |
| `video` | `video`, MP4 source filename | `video.mp4` |
| `infographic` | `infographic`, SVG / PNG / JPG / JPEG / WebP filename and corresponding `image/svg+xml` / `image/png` / `image/jpeg` / `image/webp` | `infographic.<actual lowercase extension>` |

The literal publication coordinate must equal
`media/<locale>/<category>/<article>/<basename>`; malformed absolute paths,
traversal, backslashes, query/fragment suffixes and alternate coordinates fail,
rather than being normalized into success. Non-video `articleMedia.publicPath`
must equal `/` plus that coordinate. The full path identifies article, locale
and role without identity-bearing basenames, version segments or reverse lookup.
Optional adjacent metadata can supply title, article URL or source association;
it is not a placement prerequisite and this command does not create it or a
parallel registry. Video's warehouse path is a master-backup URL only: site
video links retain their separately declared YouTube watch URLs. Deployment
does not read, infer or modify video production records.

A declared `output` always supplies the selected source, even when missing.
It never falls back to a manuscript or another source/locale. Without output,
one explicit `source` is allowed only for `build: prebuilt` or `build: copy`.
Source declarations must be relative to the descriptor parent; existing
relative paths outside that parent remain supported. Symlink leaves/ancestors,
directories, nonregular files and unreadable present files fail. An absent
source is `Unavailable`, not a publication claim. Bytes are copied in their
declared format without conversion or substitution.

All selected coordinates, sources, destinations and conflicts are preflighted
before writes. Duplicate destinations fail. Destination paths stay beneath the
selected root; existing ancestors must be direct directories and an existing
destination a direct regular file, without symlink traversal. Identical existing
bytes can be `Reused` by ordinary streaming equality; different bytes fail the
whole preflight. Originals and separately preserved historical editions remain
retained. There is no overwrite/force option, automatic version/backup path,
receipt, hash certificate, lock, retry, rollback or multi-file atomicity promise.

Dry-run reports actual source/destination/public paths with `Planned` for
available sources and `Unavailable` for absent ones, creating nothing. Normal
deployment creates only missing selected parent directories and copies without
replacement: `Created` and `Reused` describe local placement only. Missing
resources remain `Unavailable` while independently valid resources progress;
the result explicitly reports a partial outcome. Native I/O failures propagate,
including failures after earlier copies, without a fabricated all-resource
success or rollback claim. Local retrieval, Git exclusion, scoped media upload
and actual remote URL retrieval require separate evidence. The intended public
mapping is `/media/<locale>/<category>/<article>/<file>`, independently of HTML
`/en/` routing and CAR `/repository/` upload mapping. Deployed payloads for all
four roles stay outside GitHub; source/master tracking remains project-owned.

Executable behavior is specified by `cozy.media.CozyMediaWarehouseSpec`:
four-role JA/EN dispatcher placement, selected targets, profile/rootEnv and
descriptor admission, missing-output precedence and partial outcomes, dry-run,
byte reuse/conflicts, malformed mappings and symlink rejection, and preservation
of production video metadata with no process-runner use.

### Canonical warehouse site registration

The additive `--warehouse-root <absolute-directory>` or
`--warehouse-root=<absolute-directory>` input selects an existing direct
absolute normalized warehouse directory. Deploy accepts it through its own
`DeployConfig`; the generic media command configuration and other media
routes do not accept it. Register-site accepts it only together with
`--warehouse-profile`, and rejects root-only legacy requests or mixed site
contexts before historical evidence capture or registry mutation.

Both commands share `CozyMediaWarehouse.requireWarehouseRoot` and the same
warehouse planner. Null, empty, trim-altered, relative, dot/dotdot,
missing-directory, file and symlink-leaf/ancestor roots fail before writes.
Spaces within an exact absolute path are retained. Repeated options, including
mixed separated/equals forms, fail. An explicit root replaces only root
resolution: the requested descriptor profile, exact mappings, identity,
locale, role, MIME, direct paths and complete no-overwrite preflight remain
required. Explicit roots do not require the profile's environment variable;
an absent override preserves rootEnv-over-root precedence and existing
relative-root semantics. No workstation default or descriptor rewrite is added.

Retained `plan` and `deploy` APIs use the existing profile behavior.
`planAtRoot` and `deployAtRoot` accept the same validated direct Path root;
`deployCommand` routes the optional override without duplicating placement.
The nine examples in `cozy.media.CozyMediaWarehouseRootSpec` specify native
JA/EN four-role placement and registration, option isolation, compatibility,
partial/dry-run outcomes, root rejection and preservation. Parent validation
`phase75-09c2-validation-1` executed all nine plus 202 affected examples:
211 passed across ten suites, with zero failures/cancellations/aborts,
SBT/wrapper exit 0 and `lock=released`. The typed current-subject reader
accepted the result. Evidence: `/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-75/validation-COZY-75-09C2.json`;
log `/private/tmp/skill.cncf.d/a-a0e2761a-a0e5-4472-8c82-c331cc1f52dc/48680-20261006T023443Z.log`.

The parent then executed the 11 selected actual descriptors with the explicit
root: 38 Created, zero Reused and 17 Unavailable across 55 mappings. All 38
created files were opened and copy content confirmed, with MIME/size and
preserved-location reading recorded. All 55 coordinates are Git ignored and
tracked `media/` is empty. These are local deployment/retrieval results, not
visual QA, immutable seals or public availability. Exact native result/stdout
locators and retrieval observations are in
`/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-75/placement-retrieval-COZY-75-09C2.json`;
full native receipts are in `placement-evidence-COZY-75-09C2.json` in the same
directory. Independent 09C acceptance, later 09D scoped upload/public retrieval/
production registration and Step09/Phase75 closure remain pending. Earlier
191/18-test receipts and original preservation/gap/edition dispositions remain.

Phase75 retains its own final-only full SBT suite after all Step commits,
including native integration 11+7+9 without cancellation, one full Phase
review and release. Phase75.1 has not started.

`cozy media register-site <media-file> --publication <existing-directory>
--warehouse-profile <id> [--warehouse-root <absolute-directory>] [--target <resource-id>] [--dry-run]` explicitly
selects canonical registration. `--warehouse-profile=<id>` is equivalent.
Duplicate or missing profile values fail. Warehouse mode rejects any
`--site-root` or `--site-config` value and never falls back to the historical
site binding. `--profile` remains unsupported by register-site; omitting the
new option retains the historical command and its evidence contracts.

The existing warehouse plan validates the explicit profile/root environment,
resource IDs, exact article/locale/role coordinates and direct paths. The
descriptor's top-level `articleMedia.publicationProfile` must exactly equal
the requested warehouse profile. Each selected resource is associated by its
unique ID, and repeated selected article/locale/role keys fail. No project
discovery, configured SmartDox profile, producer invocation or source
substitution is required.

Non-video registration reads availability at the actual canonical destination.
An absent destination is `Unavailable`, even if an old generated output is
present. A direct regular readable canonical file can register even when the
original producer output, receipt or QA file is absent. Present unreadable,
nonregular or symlinked input fails. The warehouse plan's ordinary refusal of
differing existing source/destination bytes remains a no-overwrite check, not
a currentness certificate. Infographic MIME/alt and both PDF labels remain
explicit strict metadata; article and summary-slide PDFs stay separate.

Video uses only its separately declared `articleMedia.production` relative
path beneath the descriptor parent. Absolute/traversing paths and symlink
leaves/ancestors fail. A missing record is `Unavailable`; a present malformed
or nonobject JSON record fails. Exact category/article and language must
match the selected tuple. Correct but unfinished render, QA or YouTube states
are `Unavailable`. Published evidence requires render `completed`, QA
`technical-and-visual-qa-passed`, YouTube `published` and a clean exact HTTPS
`youtu.be/<id>` or `www.youtube.com/watch?v=<id>` URL. Optional `videoId`, if
present, must equal the URL ID. Registration produces only an external-link,
published, provider `youtube`, `watch_url` tuple. It requires no MP4 placement,
upload or network operation and never registers a warehouse MP4 URL as a watch
URL.

`CozyArticleMediaRegistry.readCurrentSite` reads each configured direct JSON
file as current semantic input and reuses the existing publication-bundle
structure, entry and strict metadata parsers. Only
`cncf-framework-publication.json` with exact
`schemaVersion=cncf.framework-publication.v1` and
`kind=framework-publication-contract`, without bundle-shaped fields, is
excluded as a known unrelated contract. It is retained untouched and owns no
media. Unknown/missing bundle types, malformed JSON, unsafe entry paths,
duplicate keys/owners and bundle-shaped contract impostors fail. This
exclusion is confined to the current-site route; legacy loading is unchanged.

`CurrentSiteState(entries,bundleNames)` and
`CurrentSiteMergeResult(bundlePaths,entryPaths,state)` carry semantic data,
never copied source bytes or persisted proof. All ownership, duplicate and
exact-role plans are completed before the first replacement. Strict and
integrity entries jointly identify the existing article owner, including
integrity-only owners; split ownership fails. A new identity uses only the
canonical `article-media.json` default. Available selected roles replace only
their exact locale/role fields; unavailable roles, other locales/articles,
generic metadata, owner provenance and unknown semantic container/entry fields
remain. Existing integrity metadata is historical separately owned evidence:
it is preserved without synthesizing records or requiring its hash/public path
to correlate with a migrated strict reference.

Canonical registration assumes a local single user and single writer. The
current compiler primitive reads the selected owner, transforms the requested
strict paths and calls the existing atomic one-file writer. It uses no shared
monitor/file lock, immutable snapshot comparison, receipt, hash, fileKey seal,
retry, backup or rollback. Native later I/O failure propagates, possibly after
earlier owners have been replaced; there is no all-bundle atomicity promise.
This does not remove those mechanisms from historical routes.

Dry-run returns planned semantic paths/state and `Planned` rows without
creating directories, bundles, locks, receipts or snapshots. Real success
reports `Registered` for available rows. Every selected row reports its
resource, article identity, locale, role and canonical public path or actual
YouTube URL; unavailable rows include a reason. The outcome is `complete`
only when every selected role is available, otherwise `partial`. An
all-unavailable selection is a valid zero-write partial result. Placement,
registry registration and verified public availability require separate
evidence; registration alone establishes neither deployment nor URL retrieval.

The fifteen semantic scenarios and native-main/parser round trip are authored
in `cozy.publication.CozyArticleMediaWarehouseSiteCommandSpec`. Step09's actual
production registration remains pending 09C placement and 09D public
verification; fixture authoring does not close Step09 or Phase75.

### Exact selected warehouse upload

The warehouse `upload-media.sh` entry point accepts optional literal first
`--dry-run` followed by at least one exact relative
`media/<ja|en>/<category>/<article>/<filename>` coordinate. Category and article
are nonempty `[A-Za-z0-9_-]+` tokens. Filenames are exactly `infographic.png`,
`infographic.jpg`, `infographic.jpeg`, `infographic.svg`, `infographic.webp`,
`article.pdf` or `slides.pdf`. Absolute paths, whitespace, traversal, empty
segments, wildcards, unknown locales, duplicates, extra nesting, video,
CAR/repository coordinates, arbitrary destinations and other options fail.

All arguments and selected readable direct regular files are preflighted before
any AWS call. Symlink leaves and directory components below the actual script
directory fail. The source is script-relative independently of caller cwd.
Each selected file receives one ordered `aws s3 cp` to
`s3://simplemodeling.org/<coordinate>` with fixed `image/png`, `image/jpeg`,
`image/svg+xml`, `image/webp` or `application/pdf`. Dry-run adds only AWS
`--dryrun` to each copy and never invalidates. Only after all real copies succeed
does the existing script-relative CloudFront helper receive exact selected
`/<coordinate>` paths in order. `CLOUDFRONT_INVALIDATE=false` suppresses it;
the existing distribution override is retained. A broad legacy invalidation
path override cannot select paths for this operation.

A copy failure stops subsequent uploads and invalidation without retry; prior
uploaded objects remain a visible partial operation. Invalidation failure
returns nonzero after completed uploads. There is no sync, recursion, delete,
ACL/credential/bucket-policy modification, lock, hash/freshness certificate,
retry or rollback. Local availability, dry-run simulation, completed upload,
invalidation submission and verified public retrieval are separate outcomes.
Video masters remain local backups and site links remain declared YouTube watch
URLs. All deployed media are ignored by warehouse Git; existing CAR upload and
source/master ownership remain unchanged.

`CozyMediaWarehouseUploadSpec` supplies seven native business scenarios against
the actual selected warehouse programs and fixture-only AWS. External execution
requires `cozy.media.warehouse.integration=true` and explicit absolute existing
direct `cozy.media.warehouse.uploadRoot`; default cancellation is not acceptance.
The paired warehouse operator guide is `docs/article-media-deployment.md` in
the warehouse repository. Placement and remote availability still require actual
operational evidence; this authoring does not close Step09 or Phase75.

## Phase 75.1 canonical warehouse build context (2026-10-07)

P751-02B adds current semantic build consumption for strict nonvideo references
whose literal URI starts with `/media/`. The configured repository root is the
warehouse store root: on the selected workstation it is
`/Users/asami/src/maven-repository`, so
`/media/ja/development-process/example/article.pdf` resolves to
`/Users/asami/src/maven-repository/media/ja/development-process/example/article.pdf`.
It never resolves through the CAR `repository/media` subtree.

The single coordinate owner is `CozyMediaWarehouse.canonicalPublicPath`.
Identity is exactly the existing category/article grammar, locale is `ja` or
`en`, and basenames are `article.pdf`, `slides.pdf`, or
`infographic.{svg,png,jpg,jpeg,webp}`. References must equal that literal path:
no URI scheme, authority, query, fragment, case normalization, percent-decoding,
dot-segment normalization, traversal or source aliases can make an input valid.
PDF MIME is `application/pdf`; an optional infographic MIME must match its
extension. Malformed `/media/` references enter this branch and fail.

The warehouse root must be an existing direct canonical directory. Each exact
destination and its existing ancestors are inspected without reading artifact
bytes. An ordinary missing file or ancestor produces a typed `Unavailable`
diagnostic carrying exact article identity, locale, role and public path, and
omits only that role in both preview and production. Existing symlink,
non-directory ancestor, nonregular file or unreadable media fails before the
consumer or effective directory allocation. Other roles/locales survive;
empty variants and media-free native article entries are removed. Published
declared external YouTube tuples remain available without an MP4 prerequisite;
SmartDox applies its normal draft/withdrawn status filtering.

`CozyArticleMediaRegistry.readCurrentSite` is the mandatory admission gate.
Native metadata, article, variant and recognized role fields retain the
existing closed schema: every unknown native field remains invalid. The helper
uses the admitted projection and `CozyArticleMediaPublication.produce` for
complete effective native metadata. It retains admitted extra bundle/container,
publication-header and entry-wrapper fields, provenance, path/key and generic
non-native metadata from validated bundle objects. This preservation does not
admit unknown native fields. Only the exactly typed unrelated framework
contract is excluded, using the registry's admitted bundle names.

For the legacy policy view only, canonical infographic references are removed;
all other references, including external videos and PDFs, remain. Only actual
remaining legacy infographic and site-hosted-video keys select integrity
records for unchanged policy evaluation. Historical same-key canonical
integrity records are preserved in output and source but are not dependencies
of canonical availability. Policy role omissions and excluded WIP integrity
apply only to effective output. With no canonical nonvideo reference, the
original transaction, source revalidation, policy and materialization route
remains in use.

Each canonical invocation writes complete transformed bundles into one fresh
direct `target/cozy-bok/article-media/<strategy>/warehouse-contexts/context-*/publication`
directory and uses the existing consumer lease. Materialization failure owns
cleanup only of its newly allocated directory. Originals, historical integrity,
warehouse files and prior contexts remain owned by their existing operations.
This route establishes direct availability, not integrity or currentness proof;
it introduces no lock, hash, byte snapshot, retry, receipt or shared cache.
It performs no generation, media copying, placement, deployment or upload.

Executable scenarios are authored in
[CozyArticleMediaWarehouseBuildContextSpec](../../src/test/scala/cozy/publication/CozyArticleMediaWarehouseBuildContextSpec.scala).
Focused representative and compatibility validation, independent review, real
article-card/site-native/browser evidence and Step/Phase acceptance remain
pending; authorship does not establish those results.
