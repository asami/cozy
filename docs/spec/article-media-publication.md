# Article Media Publication and BoK Integration Specification

## 2026-10-09 S19 Video publication direct output amendment

P715-02-S19-VIDEO-PUBLICATION-DIRECT is **implemented-focused-validated-unreviewed**
under the [frozen S19 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-implementation-manifest.json).
Independent review and acceptance remain OPEN.
This current amendment supersedes the earlier Video producer observation,
mtime/seal and repeated source-admission promises, destination force/hash reuse,
staged/fsync/atomic/hard-link installation and Video SHA metadata promises below.
The dated decisions and results remain history. Shared publication configuration
and the otherwise unused Project publication config argument no longer carry
`force`; direct `publish-video` and BoK `publish`, `publish-video`,
`publish-projects` and `update-publication` CLI/help omit `--force`.
Publication requests use the existing strict parser; undeclared options fail.
Unrelated bibliography and `force-project-files` behavior remains in its own scope.

Initial package, descriptor, article, scripts/steps, required assets, path
containment and direct non-symlink source admission remain normal input checks.
Optional source absence remains supported. One resolved plan selects the workspace
and final repository path. The actual synthesize/render/build/RDF pipeline,
encoding, narration, providers, voice identity and credits retain their meanings.
Publication metadata keeps actual source/output paths, kinds, versions, catalog,
RDF, captions, transcript and factual size; none proves unchanged source or output.
The main rendered artifact and present optional sidecars are copied in their
existing deterministic order directly to selected destinations with overwrite.
The callback runs once before the first copy. Ordinary direct source, shared
repository parent, unique destination and direct destination checks remain.
Article-media publication consumes its selected plans inside the ordinary registry
transaction, preparing registration after rendering and copying artifacts before
the registry write. Ordinary I/O failures report failure and may leave already
copied outputs; prior-output preservation and rollback are not promised.

The [S19 combined actual focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-focused-validation-result.json) records
102 passing tests in 8 completed suites: six boundary suites contributed 79,
the CozyVideoSpec publication group 5, and the actual
`cozy.bok.CozyBokSpec` publication filter 18. Failed, aborted, canceled, ignored
and pending counts are all zero. Both invocations ended with SBT and wrapper
exit 0 and serial lock release.
The [first coverage-gap record](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-focused-validation-failure01.json) retains 84 passing tests
in 7 suites: the incorrect `cozy.CozyBokSpec` selector omitted the planned BoK
consumer. Its failed coverage admission is not a failed test. The
[selector correction basis](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-supplemental-validation-selection-basis.json) identifies the actual package;
the [supplemental result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-supplemental-focused-validation-result.json) covers only the missing 18-test
publication filter. The original passing groups were not repeated.

Compiler warnings, errors and fixture diagnostics are zero. Three runtime Logback
duplicate `logback.xml` warning lines remain in unchanged kaleidox/SmartDox
dependencies and were also observed in the actual dated S18 log. They are retained
as `HYG-P715-S19-LOGBACK-001`, pending independent Step classification; no
diagnostic suppression or all-warnings-zero claim is made.
The [M0 placement admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-doc-placement-fix-admission.json) records restoration of normal
typed aggregate verification: the Phase plan's top-level status, dependency and
aggregate metadata precede its first H2; its S19 section remains after that
metadata and before Goal.

The [S18 actual focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s18-repair03-focused-validation-result.json) remains dated evidence: 155 successes
in 10 completed suites, zero failures. It is not S19 validation or acceptance.
Remaining ProjectPublisher SHA and original R715-06/08/09/10/11/13/14/15 duties,
MediaReview, MediaWarehouse/modeler and E715-01..04 external consumers remain OPEN.
The [dated S14 native result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s14-factual-projection.json) does not establish current hearing
or final native relevance. Parent source assessment records changes to publication
entry, copy, metadata and shared force grammar; existing source resolution,
workspace copy and synthesize/render/build/RDF implementations preserve generation
behavior. Existing ProfileRender, Video/publication and supplemental BoK suites
exercise the changed entry and output consumers. No new S19 audible playback or
final whole-Phase native acceptance is recorded. Current human hearing and final
JA34 generation/assembly/end-card/EOF obligations remain OPEN.
Mandatory independent M2 cycles 6/7/8/10, whole-Step and full-Phase review,
acceptance, exact local commits and the separate release remain OPEN.
Repository-full validation and broader integration remain deferred-not-run to
PHASE-71.7. This amendment changes no Stage Status triple or closure checkbox.


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

## 2026-10-09 Phase 71.5 Context/Site/WIP direct-input and copy amendment

Authority: [Phase 71.5](../phase/phase-71.5.md),
`P715-02-S15-CONTEXT-SITE-WIP-DIRECT`, under
[artifact protection removal](../design/artifact-protection-removal.md).
This amendment supersedes the earlier promises for Context directory/file
size, modification-time and stable-file-key admission; WIP/Site file metadata,
repeated byte/attribute observations and whole-plan revalidation; SiteCommand's
final selected-evidence check; and WIP physical-copy locks, staging, fsync,
backup, atomic destination replacement, digest verification and restoration.
Earlier accepted text and S13 records remain dated history; S13's single
configuration read and project-marker reuse continue.

Context directory and file evidence now carries normalized lexical and real
paths only. WIP uses the existing Context project-root value directly.
Selected descriptor and production inputs are read once for ordinary parsing;
infographic and PDF path admission does not read content merely to retain
identity evidence. Initial direct-file/directory checks, lexical/real
containment, nearest project discovery, the six native configuration formats,
layer precedence, profiles and provenance, selected resource/role/locale
semantics, production QA and accepted YouTube URL grammar remain. Ordinary PDF
receipt and selected review-state `requireCurrent` behavior remains. JVM
`user.home` coordination concerns the shared property and fixtures, and remains.
SiteCommand consumes its initial binding plan through the existing Registry
APIs; their required removed-binding callback is the literal `() => ()`.

WIP dry-run returns the initially admitted plan without replanning or copy I/O.
Non-dry WIP copies only selected videos directly from their admitted source
paths, sorted by destination path, before the existing Registry replacement.
The current initial Fresh policy copies without replacement; ExactRepeat uses
`REPLACE_EXISTING`. Infographic and PDF candidates are reuse-only. Initial
root breadth, overlap, external-publication and selected-path predicates
remain. The existing `afterinstall` callback runs after each direct copy;
Registry-result completeness remains an ordinary semantic requirement.
An I/O or callback failure propagates and cannot return success. Copied files
may remain after failure; WIP provides no destination rollback or preservation
guarantee, and regeneration is the recovery operation.

Shared Registry/Compiler root metadata, stable keys, locks, snapshot checks,
atomic metadata replacement and Registry bundle restoration are still actual
unfinished Phase 71.5 `R715-12/14` obligations. WIP `DirectoryEvidence` still
captures real stable keys for those consumers. Initial source/installed MP4
digests, canonical integrity records and the initial Fresh/ExactRepeat policy
also remain unfinished connected work. They are not a waiver, compatibility
adapter or fake key/hash. Removing WIP copy protection does not remove or
promise anything about the shared Registry's own restoration behavior.
All-removal, Step and Phase closure are not claimed.

The affected existing Executable Specifications are amended in place for
ordinary behavior; protection-only scenarios are removed. S15 is
implemented-unvalidated-unreviewed. The parent-selected eight-suite command,
actual native-result relevance assessment and independent acceptance remain
pending. Current human hearing stays OPEN; earlier native decode, end-card and
player-EOF observations do not establish hearing. Repository-full SBT and
broader integration remain deferred-not-run to Phase 71.7.

This article-media amendment changes only the WIP orchestration promises above.
The Context/Site changes are recorded here as connected responsibility edges;
their dedicated specifications and designs carry their own amended contracts.
Other publisher, distribution and warehouse routes remain unchanged unfinished
finite work; no general publication transaction or integrity removal is claimed.

## S15 actual-result continuation — 2026-10-09

Current status: `P715-02-S15-CONTEXT-SITE-WIP-DIRECT` is
implemented-focused-validated-unreviewed. This continuation preserves the
initial implemented-unvalidated record as dated history. The parent-admitted
selected focused command completed all eight existing suites with zero aborted
suites: 124 tests succeeded, with zero failed, canceled, ignored or pending
tests. SBT reported zero warnings and zero errors; the hostile-XML diagnostic
count was zero. SBT and wrapper exits were 0, and the shared serial lock was
released. These are actual selected behavior results, recorded in the
[focused-validation result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s15-focused-validation-result.json)
and [factual projection](../../.codex-workflow/phases/PHASE-71.5/p715-02-s15-factual-projection.json).

The parent admitted the actual S15 diff and native relevance: the Context
metadata and WIP/Site input/copy changes preserve normal video rendering and
assembly, and the selected Context/Media/Credits behavior passed. Relevant S14
JA34 render/build, decode, visible unmuted playback, opaque end card, player EOF
and cleanup remain dated evidence. No post-S15 native execution or actual
human hearing is claimed. Current hearing and final whole-Phase native
relevance assessment remain OPEN.

Shared Registry/Compiler/root-key protection and initial WIP canonical
integrity/Fresh/ExactRepeat admission remain unfinished `R715-12/14` work.
All remaining finite removal items, `E715-01..04`, and Step/Phase independent
reviews, acceptance, commits and release obligations remain OPEN. The focused
result closes none of those obligations and supplies no preservation waiver.
Repository-full SBT and broader integration remain deferred-not-run to Phase
71.7. No closure checkbox is changed.

## 2026-10-09 Phase 71.5 Registry/Compiler direct I/O amendment

Authority: [Phase 71.5](../phase/phase-71.5.md),
`P715-02-S16-REGISTRY-COMPILER-DIRECT`, under
[artifact protection removal](../design/artifact-protection-removal.md).
This amendment supersedes the shared Registry/Compiler promises for root
file-key seals, monitor/file-lock exclusion, complete raw-byte snapshots,
revalidation/CAS, fsync/atomic metadata replacement and exact WIP owner-bundle
restoration. It also supersedes legacy BuildContext source revalidation,
immutable-context proofs and staging-to-target atomic installation. Earlier
accepted text, S13/S14 evidence and the S15 implementation and actual-result
continuations remain dated history. S15's literal site callback and remaining
WIP directory-key description are superseded here.

Registry `Snapshot(entries,bundleNames)` now carries ordinary semantic entries
and sorted configured names, including configured empty bundles. Entries-only
fixtures default to no configured names; metadata projection retains the
original configured names. Each Registry input is parsed normally without
retaining its bytes. JSON/schema/key/path/duplicate checks, strict role/locale
and PDF semantics, configured owner selection and selected bundle existence
remain initial admission requirements. General publication retains root
creation and supported alias canonicalization; existing Site/WIP transactions
retain their initial existing direct canonical-root admission. WIP directory
evidence carries only path and identity, with no file-key prerequisite.

Registry transactions retain active callback lifetime and one-merge state.
There is no thread-confinement, filesystem exclusion or pre/post root/byte
proof. Read-only planning uses its initial semantic snapshot and creates
nothing. `mergeSite` takes only its role updates. WIP's actual `beforeReplace`
copy and `afterReplace` result callbacks, and publication's actual artifact
admission callback, retain their operation ordering. Initial owner/role
conflicts fail before artifact admission. Metadata updates follow current
semantic read/transform/direct-write behavior in deterministic selected order;
normal ownership/unknown-field parsing is independent of byte immutability.
Compiler writes canonical bundle JSON directly to the selected target, or
performs its selected delete. Current-site wrappers and generic entry fields,
publication/project/sample/source metadata, attribution/sourceCommit and
descriptive SourceFile size remain ordinary product data. Compiler staging
that assembles metadata remains ordinary assembly.

Legacy BuildContext creates a fresh
`target/cozy-bok/article-media/<strategy>/snapshots/snapshot-<unique>/publication`
directory directly and copies the configured selected bundles by name, applying
the existing policy omissions. Actual copies preserve raw wrapper and generic
fields. The four strategies and aliases, required repository selection,
output layout and consumer invocation remain. The canonical warehouse branch
is unchanged. `activeLeaseCount` measures actual consumer lifetime; its
synchronized bookkeeping and global JVM `user.home` fixture coordination do
not provide artifact exclusion or immutability.

Ordinary parsing, materialization, copy, callback and I/O failures propagate and
cannot return success. A failure can leave partial selected metadata or the
new invocation's generated context; no atomic replacement, cleanup or
restoration guarantee applies. Recovery is regeneration. Configured source
files, prior/unselected contexts and unrelated outputs are outside these
writes. S15's sorted direct selected video copies and registry-last result
ordering remain.

The article-media integrity schema/codecs/digest admission and concrete
Association/Policy/VideoEvidence/VideoRegistration/InfographicEvidence readers
remain actual unfinished `R715-12` work. WIP source/installed SHA,
content-addressed versions and initial Fresh/ExactRepeat integrity admission,
Video/Media artifact destination SHA/force/locks/staging/rollback and infographic
evidence revalidation remain connected removal requirements. Other finite
`R715-*` rows and `E715-01..04` remain OPEN; these are not retained exceptions,
waivers or compatibility scaffolds. This shared-boundary removal closes none
of them wholesale.

S16 status is **implemented-unvalidated-unreviewed**. The existing scenarios
are amended in place; protection-only cases are removed. The parent-selected
17-suite serialized validation, actual post-S16 native relevance assessment
and independent Step/Phase acceptance remain pending. S15's actual 124-test
result and S14's actual JA34 render/build/decode, visible unmuted playback,
opaque end card, player EOF and cleanup remain dated evidence. Current human
hearing and final Phase relevance remain OPEN. Reviews, acceptance, commits
and release are OPEN. Repository-full SBT and broader integration remain
deferred-not-run to Phase 71.7. No validation, hearing or closure result is
invented by this amendment.

## S16 actual-result continuation — 2026-10-09

Current status: `P715-02-S16-REGISTRY-COMPILER-DIRECT` is
**implemented-focused-validated-unreviewed**. The initial implementation and
pending-validation records above remain dated history. The parent-admitted
final focused run completed all 17 existing suites: 278 tests succeeded
(207 + 71 across the two selected task groups), with zero failed, aborted,
canceled, ignored or pending cases. Warnings, errors and fixture diagnostics
were zero. SBT and wrapper exits were 0, and the shared serial lock was released.
Actual evidence: [final focused-validation result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s16-repair01-focused-validation-result.json) and [parent factual projection](../../.codex-workflow/phases/PHASE-71.5/p715-02-s16-factual-projection.json).

The [initial focused-validation failure](../../.codex-workflow/phases/PHASE-71.5/p715-02-s16-focused-validation-failure01.json) remains history: 278 successes and one failure across 17 suites,
plus the Warehouse Fixture outer-reference warning. The separately executed
[frozen repair manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s16-fix-manifest01.json) and [parent fix admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s16-parent-fix-admission01.json) retired only the obsolete Registry-lock-dependent
VideoCommand concurrent-force scenario, its dedicated `BlockingPublishingRunner`
and dead imports. Warehouse Fixture moved to its owning object with unchanged
four Path fields and remaining behavior bodies. There was no production
change, warning suppression, replacement lock or weakening of ordinary
acceptance. The actual changed inventory is now 25 paths within 29 admitted
paths: the original 28 plus the causally necessary existing VideoCommandSpec
repair path. Earlier 24-of-28 editing records remain initial history.

Five completed repair histories are retained. Cycle 6 was consumed by this
actual repair and focused validation; its mandatory independent M2 review
remains **OPEN**. It is not accepted, and no counters are reset. Unfinished
P715-02 has no Step-review predecessor yet; this continuation invents no
review, predecessor or acceptance lineage.

The parent's actual eight-production-file diff and connected 17-suite admission
classify S16 as publication-metadata/legacy-context work preserving S14's JA
speech, VideoRuntime, credits, rendering and final assembly. S14's actual
34-scene generation/build/decode, visible unmuted playback, opaque end card,
player EOF and cleanup remain dated relevant evidence only. No post-S16 native
execution or current human hearing is claimed. Current hearing and final
whole-Phase native relevance remain **OPEN**.

Original article-media digest schemas/readers, WIP source/installed SHA,
content-addressed version and initial Fresh/ExactRepeat integrity policy,
connected artifact destination protections, other finite `R715-*` work and
`E715-01..04` external obligations remain **OPEN**. Step/Phase independent
reviews, acceptance, commits and release remain **OPEN**. Repository-full SBT
and broader integration remain deferred-not-run to Phase 71.7. No closure
checkbox is changed. This continuation projects admitted facts only; it runs
no validation, review or commit and changes no source, test or control record.

## 2026-10-09 S17 article-media association without digests

This is the current contract for `P715-02-S17-ARTICLE-MEDIA-ASSOCIATION-DIGEST-REMOVAL`.
It supersedes the earlier article-association SHA, source/installed byte equality,
WIP content-derived version, Fresh/ExactRepeat admission and infographic
post-install evidence re-preparation requirements. Earlier declarations and
actual-result records remain dated history. The original Phase 71.5 authority,
`fc640ec` base and P715-02 accumulator continue unchanged.

`CozyArticleMediaIntegrity.Input` and `Record` describe an ordinary semantic
association: article identity, exact locale and role, public/repository path,
media type, actual producer provenance and publication state. Their `artifact`
is `Option[Artifact]`. `VideoPublication` and `MediaPackage` require
`Some(Artifact(identity, version))` with genuine declared, normalized name/version
metadata. `WipSiteVideo` requires `None`; its JSON contains no artifact envelope
or invented version. Its selected resource ID remains in its existing
provenance. The existing `cozy.article-media-integrity.v1` schema and
`metadata/article-media-integrity/...` namespace identify this metadata only.
They provide no content integrity guarantee. Association SHA fields, acquisition,
codec requirements and comparisons are removed. Known-field checking and normal
canonical format/semantic roundtrip remain; no alternate schema, legacy digest
decoder, adapter or substitute identity is introduced.

Video evidence retains selected manifest/registry schema, declared name/version,
status, the unique selected MP4 record, provider-neutral URL and ordinary staged
or installed direct path/containment. Registration relates that evidence to the
actual selected publication result. Infographic evidence retains the initially
parsed descriptor, actual build receipt and declared dependencies, selected
resource/profile/locale, source PNG format, declared output and result/destination
relation. The command completes that initially prepared evidence against the
actual CozyMedia result without hashing or post-install re-preparation/equality.
Before-first-install and after-artifact-before-merge callbacks retain their actual
operation/failure order; registry merge follows the artifact results.

Policy keeps initial lexical/real containment, direct regular selected artifacts,
role, locale, canonical metadata and publication state. Published availability
opens and closes the selected file normally to retain permission and I/O failures,
without reading a content identity. A missing Published artifact is omitted with
an unavailable diagnostic in Preview and fails in Production. Permission,
non-regular and escaping paths remain hard failures. WIP exclusion, Registered
and Withdrawn policy, optional infographic and URL-less/external forms remain.

WIP planning parses the selected production JSON for article/locale/resource
facts and keeps descriptor, receipt, declared dependencies and root/path ownership.
A present destination must be a direct contained regular generated file. An
initial absent registry video/association pair permits creation. A complete
existing provider-neutral site-hosted WIP pair permits refresh only for the same
article/locale/public destination/descriptor/resource/production mapping. Partial,
non-WIP, external, production-owned, conflicting path/mapping and duplicate
selections fail normally. This is a Unit ownership check, independent of bytes,
hashes, versions or full-record equality. Every selected WIP video is copied
directly with `REPLACE_EXISTING`, followed by its afterinstall callback and the
registry-last merge/result. Dry-run performs no installation. Missing inputs,
copy, parse, callback and registry I/O failures propagate and may leave partial
selected generated outputs; recovery is regeneration.

S17 is **implemented-unvalidated-unreviewed**. Existing executable scenarios are
amended in place, including ordinary copy-output byte-vector expectations; only
the frozen digest/content-equality cases or sub-flows are retired. No new suite,
scenario, harness or removal-proof test is added. The parent-selected 19 existing
suites and whole-S17 diff/Given/When/Then admission remain pending. The exact
allocation, union of changed paths and actual clauses are recorded in the
[S17 journal](../journal/2026/10/2026-10-09-phase-71.5-article-media-association-without-digests.md).

Actual Media `PreparedPublication.sourceSha256`, destination disposition/force,
reconstruction/staging/fsync/atomic transaction and remaining Media review byte
checks, plus VideoPublisher metadata SHA and destination force/hash/staging/fsync/
atomic admission and concrete callers, remain **OPEN** for the next original
producer/destination unit. They are unfinished removal work, not retained
exceptions or compatibility promises. Other finite `R715-*`, remaining
MediaWarehouse/modeler responsibilities and `E715-01..04` remain **OPEN**.

S16's actual 278 successes across 17 suites, zero warnings/errors/diagnostics,
terminal exits 0 and released shared serial lock remain dated evidence. Five
completed repair histories are preserved; consumed cycle 6 is validated but its
mandatory independent M2 assurance is **OPEN**, without a fabricated Step-review
predecessor, reset or waiver. S14's actual JA34 generation/assembly/decode,
visible unmuted playback/end card/EOF and cleanup remain dated evidence whose
post-S17 relevance belongs to the parent. Current human hearing and final
whole-Phase native relevance remain **OPEN**. Step/Phase independent reviews,
acceptance, commits and release remain **OPEN**. Repository-full SBT and broader
integration are deferred-not-run to Phase 71.7. This amendment claims no S17
validation, review, hearing, native execution or closure result.


## S17 actual-result continuation — 2026-10-09

S17 is **implemented-focused-validated-unreviewed**. This continuation records
later parent evidence under the original S17 implementation authority; the earlier
unvalidated handoff remains historical. The association contract removes digest
fields, uses genuine `Some(Artifact)` for non-WIP records and `None` for WIP,
retains ordinary semantic ownership, and directly refreshes every selected WIP
output before updating the registry last. Preview/Production retain ordinary
permission and file IO behavior.

The [actual final focused-validation result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s17-repair02-focused-validation-result.json) records **325 tests
succeeded across 19 completed suites**, with **0 failures, canceled, ignored,
pending or aborted**. Its two groups succeeded with 227 and 98 tests respectively.
Warnings, errors and fixture diagnostics are all zero. SBT and wrapper exits are
both 0, and the shared serial lock was released. The
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s17-repair02-parent-edit-admission.json) and
[factual projection record](../../.codex-workflow/phases/PHASE-71.5/p715-02-s17-factual-projection.json) retain the supporting boundary
and history. These results establish focused validation, with independent review
and acceptance still pending.

The original terminal scope mismatch and 22 partial edits remain preserved. The
initial S17 validation failed at two BoK `Option[Artifact]` accessors, with two
compile errors and zero tests executed. The actual pre-edit header mismatch
refused the edit without a delta. Corrected header governance then admitted the
two accessor changes and current header. The deployed minimal shared-skill
recovery 17 resolved the exact LF-only native-origin pre-render refusal; the
original refused child remains do-not-resume, and its raw records remain retained.
The subsequent actual execution completed 19 suites with 324 successes and one
failure from an obsolete WIP metadata SHA expectation. The admitted second repair
removed exactly two stale protection expectations from the existing WIP command
specification and updated its current header, preserving the ordinary copied-byte,
provenance, path, dry-run and registry checks without replacement proof assertions.
The final 325/19 result above follows that admitted change. The raw failures,
both repair manifests and actual reports remain historical evidence.

The five accepted repair histories remain unchanged. Consumed cycles **6, 7 and
8** are validated and still require mandatory **independent M2 assurance: OPEN**;
the consumed counter is 8, without reset or waiver. Cycle 6 retains
`P715-S16-VALIDATION-CONCURRENCY-001` and `P715-S16-OUTER-REFERENCE-001` as
validated, awaiting independent M2 assurance. The typed ledger contains only the
P715-01 Step review; P715-02 remains unfinished and has no Step-review predecessor.
Parent validation or source reports do not fabricate that predecessor or become
accepted repair history.

The parent's actual relevance assessment records that S17 changes publication
associations and consumer availability. These 19 suites exercise publication,
bilingual DoxSite, staging and command callers. This unit changed no speech,
storyboard, render-template, audio assembly or end-card source. S14's actual JA34
generation, assembly, decode, visible unmuted playback, end-card, EOF and cleanup
remain dated evidence. No current post-S17 native execution or hearing is claimed.
The same representative's current-native relevance, final whole-Phase native
relevance and current human hearing remain **OPEN**.

Original producer/destination removal remains **OPEN**: VideoPublisher repository
and video SHA emission, destination force/hash/staging/fsync/atomic admission and
concrete callers; Media `PreparedPublication.sourceSha256`, destination state,
disposition, force/hash/reconstruction/staging/fsync/atomic transaction and
remaining Media review byte checks. Remaining finite `R715-06/08/09/10/11/13/14/15`
work, including MediaWarehouse/modeler responsibilities, and separately owned
`E715-01..04` external-reader duties remain **OPEN**. S17 closes neither R715-12
nor R715-14 wholesale. Independent Step and full-Phase reviews, Step/Phase
acceptance, commits and release remain **OPEN**. Repository-full SBT and broader
integration remain **deferred-not-run to Phase 71.7**. No checklist box is closed,
and no accepted Step, Phase, native or hearing result is inferred by this factual
continuation.

## S18 Media direct-publication amendment — 2026-10-09

Status: **implemented-focused-validated-unreviewed** under `P715-02-S18-MEDIA-PUBLICATION-DIRECT`.
The original implementation handoff on 2026-10-09 recorded
implemented-unvalidated-unreviewed with ten-suite validation pending; that former
state is retained as history. The actual later result is recorded below.
This dated amendment supersedes earlier Media publication source/destination
hashes, prepared-input/currentness seals, force/reuse dispositions, temporary
files, fsync, atomic installation and generated-output protection promises,
including the earlier `publish-media --force` grammar. Earlier observations and
acceptance records remain historical evidence.

Initial selected descriptor/resource/profile and normalized direct-path
admission remains. Public PDF preparation still requires its selected ordinary
receipt and PDF review state; infographic preparation still admits the actual
PNG, declared build manifest, receipt/dependencies, locale, version and provenance.
These are initial format and semantic admissions. Commit uses the already
selected typed vector and current in-memory effective profile, without rereading
the descriptor or reconstructing a saved publication plan. It validates unique
normalized destinations and direct roots/parents, invokes the before-first-install
callback once, then copies each selected direct regular source in deterministic
order with `Files.copy(..., REPLACE_EXISTING)`. Actual destination existence
reports factual `Created` or `Replaced`; existing destinations receive the selected
output through ordinary replacement.

Direct Media dry-run retains identifying text and changes no destination or
absent local profile root. Real legacy publication may create its selected local
root; externally selected roots must already exist. Explicit BoK `publish-media`
still rejects dry-run and rejects `--force` through the existing strict
declared-request builder, reporting `Too many arguments: --force`. `PublicationConfig` and the BoK manifest no longer carry
`mediaForce`. The remaining `force` setting belongs to actual Video behavior.
Infographic commands keep initial complete candidate/registry admission,
semantic invocation matching, both existing callbacks, actual artifact results
and the registry-last merge. Ordinary directory/symlink errors retain path
diagnostics; an actual copy IOException reports Media publication failure.
A failed direct copy may leave earlier or partial output, with no success,
rollback or prior-output guarantee. Recovery is regeneration.

The parent-selected ten existing suites completed with **155 successes** and
**zero failures, aborted suites, canceled, ignored or pending tests**. Warnings,
errors and fixture diagnostics are zero. SBT and wrapper exits are 0 and the
shared serial lock was released. Independent review and acceptance remain pending. VideoPublisher
force/hash/staging/fsync/atomic behavior and producer metadata remain original
unfinished next-unit work. Remaining finite removals, `E715-01..04` external
readers, current human hearing, independent M2 assurance for cycles 6/7/8/10,
Step/Phase reviews, acceptance, local commits and release remain **OPEN**.
Repository-full SBT and broader integration remain **deferred-not-run to Phase
71.7**. This amendment uses the original work list and introduces no new schema,
version, compatibility adapter, removal-proof test or absence audit.

## S18 actual-result continuation — 2026-10-09

S18 is **implemented-focused-validated-unreviewed**. The parent-admitted
[final focused-validation result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s18-repair03-focused-validation-result.json) records **155 successful tests
across ten completed suites**: the first seven suites passed 131 tests and the
second three passed 24. There are **zero failures, aborted suites, canceled,
ignored or pending tests**, and **zero warnings, errors or fixture diagnostics**.
SBT and wrapper exits are both **0**, and the shared serial lock was **released**.
The [raw terminal log](/private/tmp/skill.cncf.d/a-6db5d345-ad86-4db6-93b2-33ec9f816dfc/88215-20261008T222603Z.log) and
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s18-repair03-parent-edit-admission.json) retain the supporting execution and
admission facts. This document-only continuation performs no source, Executable
Specification or control edit and runs no validation, review or commit.

The [first S18 failed result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s18-focused-validation-failure01.json) remains historical: its first
seven-suite run reported **130 successes and one failure**, with zero warnings.
The original failure and all repair/refusal histories remain preserved. The
already completed strict-parser repair uses the existing `buildStrict` API for
publish-media and the same existing force-rejection scenario observes
`org.goldenport.context.FaultException` with `Too many arguments: --force`.
The journal's corrected strict-parser requirement row, actual force-rejection
clauses and source/GWT reference 111 remain unchanged by this projection.

Cycle **9** restored the original English executable-specification grouping;
parent exact inspection plus the final ten-suite validation admit that **M1**
repair. Cycle **10**'s strict-parser source and existing FaultException assertion
are focused-validated, while **independent M2 assurance remains OPEN** together
with cycles **6/7/8**. Focused test success does not replace independent M2
assurance, fabricate a P715-02 Step-review predecessor or close the Step/Phase.
No per-Slice review or commit is claimed and no checklist box is closed.

Original VideoPublisher producer/destination force/hash/staging/fsync/atomic
work, remaining Media review byte checks, finite MediaWarehouse/modeler and other
removals, and separately owned **E715-01..04** external-reader duties remain
**OPEN**. Current human hearing and final whole-Phase native relevance remain
**OPEN**. S18 selects no speech/storyboard/renderer/assembly/end-card change;
dated S14 JA34 evidence supplies no new native execution or hearing result.
Independent Step/Phase reviews, acceptance, local commits and distinct release
remain **OPEN** under the unchanged original full Goal, base, authority and plan
epoch. Repository-full SBT and broader integration remain **deferred-not-run to
Phase 71.7**. Earlier S17 and S18 handoffs remain dated history; this actual
focused result grants no product, Step, Phase, all-removal or hearing acceptance.

## 2026-10-09 S32 build-input and warehouse-context direct lifecycle amendment

Authority: [Phase 71.5](../phase/phase-71.5.md),
`P715-02-S32-BUILD-INPUT-DIRECT-LIFECYCLE`, under
[artifact protection removal](../design/artifact-protection-removal.md).
This amendment supersedes the Phase 75.1 canonical warehouse branch's
failed-materialization directory rollback guarantee. Earlier guarantees and
accepted results remain dated history.

After actual semantic admission, canonical materialization allocates one fresh
`target/cozy-bok/article-media/<strategy>/warehouse-contexts/context-*/publication`
directory and writes the selected transformed bundles directly in their
existing order. A write failure propagates the actual original exception,
including an `IOException`, with any earlier written bundle or partial work
still present. It returns no successful Result on failure. Recovery is
regeneration; there is no rollback, retry or replacement protection. Source
registry and warehouse inputs remain owned by their producers, while prior
independent contexts and unrelated outputs remain outside this invocation's
writes.

Strict current-site parsing, canonical coordinate and MIME checks, warehouse
path/direct-file availability, role/locale omissions, metadata and wrapper
semantics, external YouTube projection, legacy policy evaluation, diagnostics,
strategy selection and actual consumer lease lifetime retain their existing
meanings. No source registry mutation or semantic-context redesign is added.
The connected [BoK ownership specification](bok-build-target-ownership.md)
and [design](../design/bok-build-target-ownership.md) retain actual bibliography
cache and configured SIE inputs in place during cleanup, replacing their
former physical stash/restore transport. Existing source-root and target-root
admission remain.

The existing [CozyArticleMediaWarehouseBuildContextSpec](../../src/test/scala/cozy/publication/CozyArticleMediaWarehouseBuildContextSpec.scala)
retains all twelve cases. Its real second-bundle write failure now specifies
that the first written bundle remains and equals the expected semantic bundle,
the second write was attempted, and the exact original exception reaches the
caller. Source, warehouse, unrelated data and the distinct prior context retain
their existing ownership assertions. The changed scenario uses adjacent
Given/When/Then, `should` matchers and a bounded semantic `which` group. No new
proof suite, case or harness is introduced.

**P715-02-S32-BUILD-INPUT-DIRECT-LIFECYCLE** is currently
**implemented-focused-validated-unreviewed**. The initial 2026-10-09 editing
handoff recorded **implemented-unvalidated-unreviewed**. Parent-selected
`cozy.bok.CozyBokBuildTargetSpec`,
`cozy.publication.CozyArticleMediaWarehouseBuildContextSpec`,
`cozy.publication.CozyArticleMediaBuildHandoffSpec` and
`cozy.bok.CozyBokBibliographySpec` were **pending** at that initial handoff,
as were parent admission and independent review. Actual focused validation and
parent admission are recorded below; independent review remains OPEN. S31's actual **27 successes in three completed suites**
remain separate dated evidence. This amendment selects original
**R715-12/13/14/15** only at this local build lifecycle; their other finite
remainders and **E715-01..05** remain **OPEN**. Final current **JA34 generation,
assembly, audible hearing, end-card and EOF**, independent **M2 cycles 6/7/8/10**,
whole-Step/full-Phase review and acceptance, exact local Step commits and the
distinct release remain **OPEN**. Repository-full SBT and broader integration
remain **deferred-not-run to PHASE-71.7**.

### Actual S32 focused validation — 2026-10-09

**P715-02-S32-BUILD-INPUT-DIRECT-LIFECYCLE** is currently
**implemented-focused-validated-unreviewed**. The [actual S32 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s32-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s32-parent-edit-admission.json) record **48 successes across four completed suites**.
The first sequential `testOnly` group completed **16 successes in two suites**:
`cozy.bok.CozyBokBuildTargetSpec` and
`cozy.publication.CozyArticleMediaWarehouseBuildContextSpec`.
The second completed **32 successes in two suites**:
`cozy.publication.CozyArticleMediaBuildHandoffSpec` and
`cozy.bok.CozyBokBibliographySpec`. Failed, canceled, ignored and pending tests
and aborted suites were all **zero**. Normal implicit Main compilation reported
completed batches of **five and thirty-one Scala sources**; Test compilation
completed **two Scala sources**.

Compiler warnings and errors, unclassified runtime warnings, errors and fixture
diagnostics were **zero for this invocation**. Three actual runtime Logback
warning lines reported duplicate `logback.xml` resources from
`org.goldenport/kaleidox_2.12/0.6.20-SNAPSHOT` and
`org.smartdox/smartdox_2.12/2.4.19-SNAPSHOT`. These are retained under the existing
**HYG-P715-S19-LOGBACK-001**, pending independent Step classification; no warning
is suppressed. This selected result is not a whole-Phase warning-free claim.
Existing diagnostic and warning dispositions retain their original scope.

Serial invocation `4088733d-6159-438c-a708-e9085036de58` completed with SBT and
wrapper exit codes **0/0**, and the shared serial lock was **released**.
The connected BoK build-input and generated warehouse-work failure lifecycle
is focused-validated only. The initial **implemented-unvalidated-unreviewed**
editing handoff and pending-suite record remain dated 2026-10-09 history.
Parent edit admission records source inspection; it is not independent review
or Step/Phase acceptance. S31's actual **27 successes in three completed suites**
retain their separate dated attribution.

Other original **R715-12/13/14/15** remainders, all **E715-01..05** and other
original finite/runtime/carrier/descriptor/review obligations remain **OPEN**.
The supplied native assessment concerns local BoK build inputs and effective
publication materialization, with no JA34 narration, synthesis, render,
assembly or credit producer change. Final current **JA34 generation, assembly,
audible hearing, end-card and EOF** remain **OPEN**. Independent **M2 cycles
6/7/8/10**, whole-Step/full-Phase review and acceptance, exact local Step
commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**.

Original base/authority/epoch, repair counters, finite rows, aggregate metadata,
Stage Status triples, closure checkboxes and all prior refusal/review/repair/
native history retain their state. This factual projection runs no new
validation and performs no self-review, staging, commit or workflow transition.
No Slice, Step, Phase, native or external-consumer acceptance is claimed.
