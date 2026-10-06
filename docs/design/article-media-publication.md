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
article or a bundle. Under the existing complete byte-observation and lock protocol, an operation
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
complete transient bundle-byte snapshot, and atomically replaces the owner bundle.
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

Infographic evidence separates semantic build admission from publication
artifact integrity. The explicit descriptor's `target/cozy-media/manifest.json`
must attribute the schema, knowledge identity, unique selected resource, and
exact build path. Its actual build receipt is admitted through
`CozyMediaReceipt.requireCurrent` with the selected profile. Currentness follows
actual declared producer inputs, existence, independent validity, and
modification times; a copy resource depends on its declared copy source and
output, so changing only knowledge age does not invalidate that PNG. A truly
newer declared source fails with its real source/output and next build operation.
The selected entry has no local SHA or replacement freshness/approval identity.

The prepared form verifies the actual direct regular operation source PNG
against `PreparedPublication.sourceSha256` and uses that verified SHA in
`cozy.article-media-integrity.v1`. Completion verifies installed destination
bytes against the prepared integrity value before exposing the strict role.
Projection admits the semantic current manifest and compares actual selected
build PNG bytes with actual published destination bytes, using their matching
SHA-256 in the integrity record. A real build/published byte mismatch remains
an integrity failure. This purpose does not extend those digests into local
currentness or approval. Explicit version, profile/destination mapping,
provenance, path containment, PNG validity, preflight ordering, force rules,
and atomic artifact/registry publication retain their existing responsibilities.

Ordinary `cozy bok build` reads only configured registry bundles and the
configured repository root. Canonical build strategies are exactly `draft`,
`work-in-progress`, `production-preview`, and `production`; CLI `wip` and
`preview` normalize before policy selection. The first three select Preview and
`production` selects Production. Any other strategy fails before registry load,
path construction, or an external command. Under the same real-publication-root
lock used by producers, build holds one complete transient observation from
actual bundle reads through policy evaluation, source-byte revalidation, and
immutable context installation. The registry retains exact immutable bytes
keyed by admitted bundle name. Complete-map comparison preserves set changes,
empty-registry conflicts, and raw-byte changes even when decoded metadata,
file size, and modification times are unchanged. Projections retain the original
observation; this is operation-local transaction state, never persisted
approval/currentness evidence or a replacement identity token.

Each invocation uses a fresh unique `.work-` directory under
`target/cozy-bok/article-media/{canonical-strategy}/snapshots`, including empty
and byte-identical inputs. Its final sibling is `snapshot-` plus the work
allocation's unique suffix. These paths allocate directories; they carry no
freshness, approval, equivalence, persistence, or cache-key semantics and are
never serialized into product metadata.

Before copying, actual source bundle bytes must equal the captured registry
observation. The effective context preserves generic/legacy entries, removes
exactly policy-omitted strict roles, and removes empty strict variants/entries.
After policy transformation, the complete direct filename-to-immutable-byte map
is captured with exactly the admitted filenames. Under the existing shared
lock, build revalidates the original registry bytes, compares effective bytes
again, and atomically moves the completed work directory to an absent target.
Target collisions and unsupported/failed atomic moves fail without touching an
earlier context. Installed direct directories, file set, and bytes are verified
before any consumer. Failure cleanup owns only the operation's work or newly
installed failed directory.

The registry lock is released after installation. Every pinned SmartDox, Dox,
Antora, and site consumer receives that fresh `-publication` path with the
configured `-publication.repository` value. Lease accounting spans the consumer
and releases in `finally`. Earlier consumers retain their installed bytes when
later producer operations and builds run. Older contexts are never inspected
to admit a new invocation, even if edited or containing extra files; they
remain untouched. There is no content-addressed reuse, context cache, persistent
index, or cleanup of old contexts.

Phase 71.3 replaces these specific local registry/context digest mechanisms
and the local infographic manifest SHA requirement without reopening accepted
provider schemas. Published artifact integrity,
version/provenance, strict PDF roles, policy omissions/diagnostics, and repository
path admission remain. Production policy rejects genuinely missing, invalid,
or unavailable required published artifacts before invoking any consumer.
Build does not render, transcode, publish or discover media, enumerate
descriptors, scan generated-site/arbitrary repository paths, or apply locale
fallback. Media-free behavior retains its policy with a fresh immutable handoff.

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
- A same-strategy concurrent build consumes its own installed and leased context
  even when another build publishes a different effective state. Identical
  effective content also allocates a distinct context; earlier contexts remain
  untouched, and no later build consults or replaces them.
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

## Phase 75 canonical deployment adapter (2026-10-06)

`CozyMediaWarehouse` owns one additive declaration-to-local-placement boundary,
exposed by `cozy media deploy <media.yaml> --profile <id> [--warehouse-root <absolute-directory>] [--target
<resource-id>] [--dry-run]`. It decodes `CozyMedia.Descriptor` through the
existing structured-document decoder directly. It does not enter legacy
`resolvePlan`, receipt/currentness, build, publication transaction, site
registration or hashing flows. Existing CLI routes and parser semantics remain
compatible; supplied site context is rejected by this placement boundary.

The explicit descriptor profile owns the warehouse root. `rootEnv` overrides
`root` and a missing/blank environment value fails; a relative `root` resolves
against the descriptor parent. The existing direct directory is required before
planning. No project-config fallback or workstation root is embedded in reusable
code. The stable singleton contains the small deployment plan/result and status
models; those values report paths and observed local placement, and are not
persisted acceptance records.

The explicit article identity, resource locale and articleMedia role determine
`media/<ja|en>/<category>/<article>/`, with `article.pdf`, `slides.pdf`,
`video.mp4` and `infographic.<actual lowercase extension>`. JA/EN share the
logical category/article names; a Document Project's name, rather than its
physical `index.dox`, identifies the article. Both PDF roles remain distinct.
Declared format/MIME and literal publication/public-path equality are validated
without converting bytes. Full paths carry the association; optional adjacent
metadata needs no new schema, reverse lookup or parallel source of truth.

Planning selects only explicitly mapped resources, resolves the declared output
before any fallback source, and performs complete mapping, direct-path and byte
conflict checks before mutation. A no-output source is admissible only for
prebuilt/copy. A missing output/source has an explicit unavailable disposition;
it never causes a render, manuscript substitution, cross-locale reuse or all-
published claim. Streaming equality permits reuse without computing hashes or
manufacturing approval/currentness evidence.

The no-version layout relies on preservation of originals and distinct editions
under the existing policy. Deployment never overwrites: differing existing
bytes reject preflight, identical bytes remain in place, and absent destinations
are copied without replacement after creating selected parents. There is no
lock/retry/rollback protocol or multi-file atomicity claim; native I/O failures
remain visible. Dry-run uses the same preflight and reports planned/unavailable
paths without writes. A partial result explicitly distinguishes placed resources
from absent ones.

Video placement preserves an MP4 master backup; the warehouse public path does
not replace a site's production YouTube watch URL, and production files are not
read or changed. Site declaration/consumed-registry migration, warehouse Git
exclusion and media upload mapping, real artifact retrieval and scoped YouTube
publication are later Step09 consumer obligations. Local placement is separate
from remote availability. The normative four-role contract is in the dated
Phase 75 section of the existing [specification](../spec/article-media-publication.md),
paired with `CozyMediaWarehouseSpec`; historical Phase 26/28.1/71 decisions are
preserved above.

### Explicit canonical registration boundary

COZY-75-09C2 adds a command-specific optional `--warehouse-root` input to
deploy and opted-in warehouse registration. `DeployConfig` wraps the unchanged
generic CommandConfig; only the deploy dispatcher creates it. SiteCommand
and WarehouseSiteBinding retain prior field order and defaults, adding a
trailing optional Path. SiteCommand rejects root-only and mixed-context
requests before entering historical evidence capture.

One direct-root resolver checks exact absolute normalized existing directories
and every symlink component before planning. The explicit-root entry and the
legacy entry share the existing layout and full-batch preflight; both deploy
entries share one copy executor. Root selection alone changes: an explicit
root bypasses environment/profile root resolution while preserving profile
existence, declaration semantics, availability and no-overwrite checks.
Absent overrides retain rootEnv/root behavior. The registration binding calls
the same explicit-root planner and retains the existing strict metadata,
YouTube evidence and registry owner writer. No schema, identity, concurrency
or legacy transaction change is introduced. Nine grouped native business
examples in `CozyMediaWarehouseRootSpec` passed parent validation together with
202 affected examples: 211 PASS across ten suites, zero failed/canceled/aborted,
SBT/wrapper exit 0, lock released and typed current-subject acceptance.
The registered runner `/root/sbt_receipt_eec63e180d124ef58c80`
(`gpt-6-luna/medium`) used `phase75-09c2-validation-1`.
Evidence: `/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-75/validation-COZY-75-09C2.json`;
log `/private/tmp/skill.cncf.d/a-a0e2761a-a0e5-4472-8c82-c331cc1f52dc/48680-20261006T023443Z.log`.

All 11 selected native placement commands subsequently terminated at exit 0,
without stderr: 38 Created, zero Reused and 17 Unavailable. The parent opened
all 38 copies and confirmed ordinary copy content, MIME/size and preserved
locations. All 55 coordinates remain Git ignored and tracked media is empty.
Exact result/stdout paths and observations are in
`/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-75/placement-retrieval-COZY-75-09C2.json`,
paired with `placement-evidence-COZY-75-09C2.json` and
`operational-observations-COZY-75-09C2.json` in that directory.
Local retrieval proves neither visual QA nor remote availability and supplies
no immutable seal. No source substitution, overwrite, producer/global-env/
descriptor/launcher/helper edit or remote operation occurred. Independent 09C
review and later 09D/public registration remain pending; Step09/Phase75 remain
open. Earlier 191/18-test receipts and protected editions/gaps retain their
historical dispositions.

Phase75's own final-only full SBT suite follows all Step commits and enables
native integration 11+7+9 without cancellation, followed by one full Phase
review and release. Phase75.1 remains unstarted.

The same `media register-site` operation gains an explicit
`--warehouse-profile` selection with separated and equals forms. Its trailing
optional configuration field defaults to absent. Dispatch selects the new
branch before historical root-evidence capture. Any supplied site context is
rejected in that branch; there is no fallback or project-profile discovery.
The historical no-option path, callback seam and `--profile` rejection remain
owned by the existing implementation.

`CozyArticleMediaWarehouseSiteBinding` owns semantic selection for this path.
Its stable object contains Config/Row/Plan and Planned/Registered/Unavailable
status values. It calls the accepted warehouse planner, checks the requested
profile against the descriptor association and matches selected resource IDs
back to explicit descriptor metadata. Non-video candidates use canonical
direct readable destination files. Missing old outputs do not block placed
files, and old outputs do not substitute for missing placement. Video
candidates instead read declared current production JSON beneath the
descriptor parent and validate identity, locale, publication states and clean
YouTube URLs, including optional videoId agreement. No producer, upload or
MP4 prerequisite enters this association boundary.

The existing registry remains the single schema/parser/owner namespace.
`readCurrentSite` factors publication-bundle parsing and entry decoding from
the historical loader, excluding only the actual typed unrelated framework
contract in a mixed publication directory. Its semantic CurrentSiteState
includes empty owner bundles. `mergeCurrentSite` reuses site update
normalization, strict exact-role merging and entry-based owner/conflict
helpers. It admits empty updates as a truthful no-op without changing the
historical empty-update rejection. Integrity-only ownership is honored and
split owners fail. Existing integrity records remain historical evidence;
canonical references do not invoke the historical strict-integrity
hash/public-path correlation gate.

The frozen execution model is local single-user/single-writer current
read/transform/write. Complete semantic preflight precedes writes. The narrow
package-private `replaceMetadataCurrent` primitive validates the direct
canonical root/target and requested paths/keys, loads the current owner (or
creates only the canonical article-media default), retains provenance,
unknown semantic fields and unselected entries, checks current collisions and
uses the existing atomic bundle writer. Its entry-field preservation is
selected only by this route. No legacy locked replacement, snapshot/hash
comparison, fileKey unchanged proof, retry, backup or rollback is invoked.
Later native I/O errors are observable and can follow an earlier owner write;
one-file atomicity does not imply a whole-registry transaction.

The command reports each selected row, its public path or actual watch URL,
and a partial outcome when any role is unavailable. Dry-run exposes Planned
rows and projected semantic state without storage outputs; real registration
reports Registered for available roles only. Local placement, registry
references and public URL retrieval are distinct outcomes. This Slice authors
fixture-only integration in `CozyArticleMediaWarehouseSiteCommandSpec` and
does not establish actual production placement, upload or retrieval. Legacy
Phase26/28.1/71 decisions above retain their separate contracts.

### Warehouse upload responsibility

The warehouse's `upload-media.sh` owns one explicit selected non-video file
publication boundary. It resolves source files from its actual script directory,
validates the complete literal coordinate selection and direct readable file
paths, then performs one exact ordered AWS copy per file with fixed MIME.
Category/article token admission is intentionally the settled upload grammar
`[A-Za-z0-9_-]+`; the accepted Cozy placement grammar is unchanged. No fallback
root, broad selection, source freshness seal or deployment redesign is introduced.

After every real copy succeeds, the existing warehouse CloudFront helper owns
distribution selection and AWS invalidation. The adapter passes the exact
selected public paths, preserving the distribution override while admitting no
broad path override. Dry-run uses AWS simulation only and never invalidates.
Explicit suppression leaves copies enabled. Native copy or invalidation failure
remains observable with no retry or rollback; prior successful copies can remain
visible. This is local single-user execution with no added management lock,
transaction, hashes or repeated unchanged-source checks.

The actual selected script/helper are inputs to the native upload business spec;
test setup copies them into task-local `target/` fixtures beside fake media and
an AWS recording stub. Copying is fixture construction, not an unchanged-artifact
certificate. The stable companion models fixture paths, selected files and
observed calls. Seven GWT scenarios observe exact argv and outcomes without
network, real placement, historical workflow records or source-text assertions.

Warehouse Git ignores `/media/` for all roles. CAR `upload.sh`, the shared helper
and repository metadata keep their existing responsibilities. Local master
preservation, canonical placement, remote non-video publication, YouTube links,
registry writing and public retrieval retain separate evidence requirements.
The initial 09C record contains all 55 declared mappings and existing preserved
copies with placement pending; actual receipts belong to the parent operation.
