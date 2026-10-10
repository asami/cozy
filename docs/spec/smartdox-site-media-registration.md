# SmartDox Site Media Registration Specification

## Contract Basis

This specification is the authoritative implementation contract for Cozy Phase
40.1 normal site-media registration. It reuses the accepted SmartDox Phase 9
`article-media-publication` record, the Cozy Phase 26 publication-bundle
ownership/locking/snapshot/atomic-replacement rules, and the Phase 40 PDF
receipt/currentness boundary. It does not extend the SmartDox schema or alter
PDF renderer, receipt, or review-state semantics.

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
are exactly `role`, `publicPath`, `mediaType`, `alt`, `production`, and `label`,
with the role-specific required/optional combinations stated below.

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
  - id: article-pdf-ja
    kind: document
    language: ja
    source: target/article-ja.pdf
    build: prebuilt
    articleMedia:
      role: article_pdf
      publicPath: /ja/development-process/pdf/part-5/article.pdf
      mediaType: application/pdf
      label: 記事 PDF
  - id: summary-pdf-en
    kind: document
    language: en
    source: target/summary-en.pdf
    build: prebuilt
    articleMedia:
      role: summary_slides_pdf
      publicPath: /en/development-process/pdf/part-5/summary.pdf
      mediaType: application/pdf
```

`articleIdentity` and `publicationProfile` are both required non-empty strings.
`articleIdentity` is the explicit normalized SmartDox site-relative identity.
It is independent of `knowledge.id`; no package/path/name inference is
permitted. `development-process/part-5` above is a test-fixture identity, not a
future article title. `publicationProfile` selects the existing
descriptor/profile and resource-publication evidence; it is never inferred.

The nested value `resources[*].articleMedia.role` is exactly `infographic`,
`video`, `article_pdf`, or `summary_slides_pdf`. Its mapping is exact:
`infographic` requires `kind: infographic`, `video` requires `kind: video`, and
both PDF roles require `kind: document`; every other combination fails.
Existing
top-level `resources[*].role` (for example `article-summary`) is the
media-package production role. It is independent of, and is not used for,
site-media registration. An infographic requires explicit `publicPath`; optional
`mediaType` and `alt` are emitted only when explicit. A video requires explicit
descriptor-root-relative `production`; infographic blocks must not contain
`production`, and video blocks must not contain `publicPath`, `mediaType`, or
`alt`. A PDF requires explicit `publicPath` and `mediaType: application/pdf`,
may carry only an optional nonblank exact `label`, and must use exact canonical
`ja` or `en` resource language. `article_pdf` maps only to `articlePdf`, and
`summary_slides_pdf` maps only to `summarySlidesPdf` in the SmartDox variant.

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
cozy media register-site <media-file> --publication <dir> [--target <resource-id>] [--site-root <dir> --site-config <file>] [--dry-run]
```

`--publication` is required and names the SmartDox publication-bundle root.
`--site-root` and `--site-config` are an optional inseparable pair. When
supplied, they select the same validated SmartDox site context used by a
site-aware media build: both paths must be direct non-symlink authorities, the
configuration must belong to the supplied site root, and the selected source
must belong to that site. Registration reconstructs its media plan with this
context, so receipt currentness includes the canonical site configuration and
localized document route rather than accepting a context-free substitute.
The `register-site-wip` command accepts the same paired options and applies the
same receipt-currentness reconstruction before its local WIP transaction.
`--dry-run` executes the complete read-only preflight and reports deterministic
candidate `id`, exact locale, and `resources[*].articleMedia.role` values, followed by
the same plan a non-dry run would register. It fails on malformed blocks, an
invalid target, or zero candidates; there is no successful no-op. It performs
no bundle creation/replacement or other mutation.

The command reads current Phase 40 PDF output, receipt-v2, and PDF
review-state evidence; it does not generate, copy, or publish PDFs. It does not
generate or copy other media, upload video, build/deploy a site, invoke `cozy
bok`, scan an arbitrary target/site/repository tree, or use implicit host
fallback. It does not enumerate BoK descriptors. Skills call this supported
command rather than writing bundle JSON.

## Candidate Validation

All requested candidates are validated before mutation. A duplicate normalized
`(articleIdentity, locale, resources[*].articleMedia.role)` among them fails
preflight. Any malformed or changed evidence, invalid path, symlink/root alias,
ownership conflict, or concurrent snapshot change fails without mutation.

### Unified Evidence Snapshot and Revalidation

Complete candidate preflight records an internal evidence snapshot that is
never serialized to the provider-neutral SmartDox record. The snapshot includes
the descriptor's normalized and real path, fileKey, size, modification time,
exact bytes and parsed top-level and resource `articleMedia` declarations, the selected publication-profile
definition and resolved real root, the selected resource publication mapping
and resolved normalized destination, and, for each selected candidate, the
following evidence:

- an infographic destination's normalized and real path, fileKey, size, and
  modification time; or
- a video's production normalized and real path, fileKey, size, and
  modification time, together with its
  parsed required fields and validated URL; or
- a PDF candidate's resolved direct output (or source for a prebuilt
  resource), with normalized and real path, fileKey, size, and modification time,
  together with current `cozy.media.receipt.v2` and
  `cozy.media.pdf-review-state.v1` evidence.

Under the same real-publication-root transaction/lock, after registry snapshot
revalidation and immediately before atomic registry replacement, the command
re-reads, re-resolves, and re-parses every selected candidate from
its original admitted paths and compares the result with its preflight
snapshot. It re-reads descriptor, profile, and resource declarations, and
rechecks no symlink traversal, direct-regular-file status, root containment,
and every semantic validation rule. Any descriptor, profile,
resource mapping, `publicPath`/`production` metadata, descriptor bytes, fileKey,
size, modification time, path identity, status, URL, disappearance, symlink substitution, or other
validation drift aborts before mutation. All candidates must pass this final
revalidation; no partial write is allowed. Internal file metadata and path
identities never appear in the SmartDox record, and strict-only registration
still creates no Cozy-integrity entry.

Each direct-file snapshot reads attributes before, between, and after two
transient byte reads. All observations must agree on fileKey, size, and
modification time, both read lengths must match the observed size, and the two
byte arrays must agree directly. This checks consistency during a read; it
does not store a payload fingerprint. Normal freshness uses existence,
modification time, and declared inputs. Revalidation does not promise to detect
every same-size payload rewrite whose modification time is deliberately restored.
PDF selection calls ordinary receipt and PDF review-state `requireCurrent`
APIs. Copy/prebuilt freshness uses direct source (and explicitly named site-video
production where applicable), rather than knowledge or package-wide inputs.
The selected PDF state checks known typed metadata only; stale unselected PDFs
cannot veto the selected PDF. Stored extras are ignored generically. No site-only
retained-output/state fallback or accepted-output/state digest is used.
Failures name the currentness stage, resource ID, actual input/output/state or
manifest path, producer and the next target build. Valid timestamp-preserved
rewrites have no digest-detection promise. Current-command input validation and
race checks are retained; presentation artifacts/renderer hashes remain
unfinished S03 work. Distribution source/staged/destination byte integrity is
unchanged.

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

### PDF

For an `article_pdf` or `summary_slides_pdf` candidate:

- the resource `kind` is exactly `document` and its language is exactly
  canonical `ja` or `en`;
- `publicPath` is an explicit accepted SmartDox root-relative path;
- `mediaType` is exactly `application/pdf`, and `label` is either absent or a
  nonblank exact string;
- the candidate comes from its already resolved Cozy media resource. Its
  `resolved.output` is the accepted output, or `resolved.source` when its
  `build` is `prebuilt`; that path must be a direct regular non-symlink file;
- the output has current `cozy.media.receipt.v2` evidence and current
  `cozy.media.pdf-review-state.v1` evidence, checked through the existing
  Phase 40 APIs; and
- the role maps exactly: `article_pdf` becomes `Variant.articlePdf`, while
  `summary_slides_pdf` becomes `Variant.summarySlidesPdf`.

The emitted PDF reference contains exactly `public_path`, `media_type`, and
optional `label`. It contains no Cozy path, hash, receipt, renderer, or
integrity fields. The output evidence is internal to the immutable plan and is
re-read, re-resolved, checked for matching file metadata, and currentness-checked during final
revalidation under the publication-root lock. Registration does not generate,
copy, or publish the PDF, and does not change the Phase 40 renderer, receipt,
or review-state semantics.

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
SmartDox Phase 9 field names (`public_path`, `media_type`, `watch_url`, and so on), and
contains no Cozy path, hash, version, provenance, or registry metadata.

Under one real publication-root lock, registration captures the complete
snapshot and identifies ownership from both the strict path and complete Phase
26 integrity prefix. One owner is reused; if no owner exists the canonical
`article-media` owner is used; multiple owners fail. It merges only the requested
exact `resources[*].articleMedia.role`/locale keys, deterministically,
preserving other locales/roles,
existing Cozy integrity records, generic entries, and unrelated articles.
After all preflight succeeds it revalidates the registry snapshot and every
candidate through the unified evidence contract above, then atomically replaces
the bundle. Strict-only site registration creates no
`cozy.article-media-integrity.v1` record for an infographic, an external-link
video, `article_pdf`, or `summary_slides_pdf`.

## Output and Required Acceptance

The command reports the selected descriptor, publication root, article
identity, and deterministic candidate IDs, exact locales, and
`resources[*].articleMedia.role` plan or results, including both PDF roles. The
result is
SmartDox registry input that a later `dox site` reads; article creation itself
needs no media registration command.

Executable acceptance must use a dedicated synthetic Part 5 normal-media
package fixture with distinct `knowledge.id` and `articleIdentity`, existing
`kind: infographic` resources carrying an independent top-level
`resources[*].role` (such as `article-summary`), JA/EN infographics, and JA/EN
published external-video production records, and JA/EN `article_pdf` and
`summary_slides_pdf` records with current Phase 40 evidence. It must prove
successful normal registration, exact strict PDF JSON fields with optional
label semantics, role mapping, exact-locale output, target selection, dry-run
immutability, existing infographic/video and unrelated-state preservation,
duplicate/failure atomicity, and refusal before mutation for missing, stale,
currentness-invalid, or role-incompatible PDFs. A deterministic test seam may
change admitted evidence between preflight and final revalidation. The fixture
registration reads current Phase 40 evidence and performs no PDF generation,
copy, or publication; SmartDox schema/projection and PDF renderer/receipt
semantics remain outside this scope. This fixture does not prescribe a future
Part 5 editorial title.

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
