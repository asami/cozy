# Article Media Publication and BoK Integration Design

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

## Phase 75.1 canonical consumption ownership (2026-10-07)

P751-02B freezes one canonical/legacy build compatibility boundary. Warehouse
planning and current build consumption share the stable
`CozyMediaWarehouse.canonicalPublicPath` coordinate owner and its private
basename/path functions. Deployment retains all existing kind/MIME/source,
declared-coordinate, destination and no-overwrite checks.

After existing root/strategy normalization, `CozyArticleMediaBuildContext`
reads current site state through the registry and obtains its canonical
projection. Literal `/media/` infographic/article-PDF/summary-slide-PDF
references select `CozyArticleMediaWarehouseBuildContext`; malformed selected
references fail without fallback. The helper is a stable object containing its
typed Result/Diagnostic and private internal models. Its direct file inspection
uses the configured warehouse store root, exact lexical coordinates, supported
MIME and no-symlink readable regular-file admission. It makes no bytes/hash or
currentness claim, and requires no skill-managed lock or parallel exclusion.

The registry remains the single strict native parser. Current-site admission
rejects unknown native metadata/article/variant/role fields before helper
materialization. The helper's legacy semantic adapter removes only canonical
infographics, enumerates the actual remaining legacy image/site-video keys,
selects their integrity and evaluates unchanged policy. It constructs
`PublishMetadata.Entry` with existing Circe conversion and uses the native
publication producer to preserve surviving recognized references, labels,
alt text and video tuples. Canonical historical integrity remains separately
retained; policy omissions and WIP integrity exclusion affect only effective
output. This is no broad migration of the old policy.

The final transformation rebuilds admitted native metadata through
`CozyArticleMediaPublication.produce`, preserving original validated bundle
containers, headers, entry wrappers, provenance and generic data. It never
merges unknown native fields. Registry-admitted bundle names exclude the exact
framework contract, rather than introducing filesystem discovery heuristics.
The configured registry and warehouse are never mutated or recertified.

One fresh direct target directory holds complete transformed bundle JSON.
Failed materialization cleans only that newly owned directory without following
symlinks. There is no snapshot-byte comparison, multi-file atomicity, retry,
receipt or shared cache. The unchanged Context and consumer lease hand its
publication path to the normal SmartDox generator. Without a selected canonical
nonvideo reference, the original transaction/materialization/source-revalidation
route remains. No media placement, rendering, deployment or network operation
belongs to this helper. The new
[executable specification](../../src/test/scala/cozy/publication/CozyArticleMediaWarehouseBuildContextSpec.scala)
is authored; parent validation and independent review remain pending.

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
The connected [BoK ownership specification](../spec/bok-build-target-ownership.md)
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
