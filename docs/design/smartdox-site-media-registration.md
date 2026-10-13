# SmartDox Site Media Registration Design

## Purpose

Phase 27 defines a normal `cozy.media.v1` package boundary for registering
article media in a SmartDox publication bundle. `simplemodeling.org` is a
special BoK: SmartDox builds its site directly, rather than receiving a Cozy
BoK build. Article source discovery remains normal SmartDox work; media
registration is an additional, explicit operation after accepted media exists.

The implementation-facing contract is
`docs/spec/smartdox-site-media-registration.md`. This document fixes the
responsibility and lifecycle boundaries that the specification implements.

## Responsibilities

SmartDox owns the provider-neutral article-media schema/model, registry
validation, and article/Notice site projection. It may expose or use a generic
safe registry writer internally. That facility does not make it the primary
user-facing package orchestrator.

Cozy owns the user-facing normal-media-package command
`cozy media register-site`. It understands explicit `cozy.media.v1` resources,
their publication-profile evidence, accepted external-video production
evidence, and the already accepted Phase 40 PDF evidence. Skills invoke
supported product commands after their respective accepted outputs exist; they
never hand-edit publication bundles. A future standalone registration skill
may orchestrate the command.

For site-aware resources, `register-site` and `register-site-wip` accept the
same paired `--site-root` and `--site-config` authority as build and publish.
The commands pass the resolved `CozyMediaSiteContext` into registration-plan
and receipt-currentness reconstruction. A missing half, unsafe path, changed
site configuration, or changed localized route therefore fails closed; an
unchanged authoring descriptor can register its accepted current artifact
without a compatibility receipt.

The command produces provider-neutral SmartDox registry input. A later `dox
site` consumes that input. Registration neither creates the article source nor
changes SmartDox's ordinary source discovery.

## Explicit Association Boundary

The descriptor opts into site registration through a top-level `articleMedia`
block for this command. It supplies an explicit `articleIdentity`, independent
of `knowledge.id`, and a selected `publicationProfile`. Each participating
resource separately opts in through its own `articleMedia` block. Resources
without that block are untouched. Any present top-level or resource
`articleMedia` block is a non-null object with exactly the canonical keys;
aliases, unknown keys, null/partial blocks, and empty required strings fail
preflight. If the top-level block is absent, `register-site` fails because no
site association is declared. Without `--target`, all and only complete
resource blocks are candidates; absent blocks are ignored but zero candidates
is an error. With `--target`, the named resource must exist and contain a
complete valid block; a target never opts a resource in. These failures happen
before publication mutation.

The nested `resources[*].articleMedia.role` is the registration role and maps
exactly to the resource kind: `infographic` to `kind: infographic`, `video` to
`kind: video`, and either `article_pdf` or `summary_slides_pdf` to
`kind: document`; other combinations fail. PDF candidates additionally
require canonical `ja` or `en` locale, explicit site-visible `publicPath`,
exact `mediaType: application/pdf`, and an optional exact nonblank `label`.
The two PDF roles map directly to SmartDox `Variant.articlePdf` and
`Variant.summarySlidesPdf`, respectively. Existing top-level
`resources[*].role` (such as `article-summary`) remains the independent
media-package production role and is not consulted for registration.

This is a declared association, not an inference from a package name,
filesystem path, generated site, target tree, or host behavior. The selected
profile is the only evidence used to validate an infographic's published
destination. A PDF is admitted only from its already resolved Cozy media
resource: its resolved output, or its source when `build: prebuilt`, must be a
direct current PDF output with current `cozy.media.receipt.v2` and
`cozy.media.pdf-review-state.v1` evidence. The explicitly declared resources
are selected; the caller may narrow those declarations with `--target`, which
never creates an association.

## Registry and Transaction Boundary

Phase 27 reuses Phase 26's configured publication-bundle container and its
single real-publication-root lock. The strict SmartDox record belongs at
`metadata/article-media/<articleIdentity>.json`; all recognized strict and
Cozy-integrity entries beneath one article identity form the Phase 26 ownership
unit. The operation reuses the one discovered owner, otherwise the canonical
`article-media` owner, and fails rather than resolving multiple owners.

Registration is a deterministic exact-key merge by `(articleIdentity, locale,
resources[*].articleMedia.role)`. It preserves other
`resources[*].articleMedia.role` values/locales, unrelated articles, generic
bundle entries, and existing Cozy integrity entries. It performs complete requested
candidate preflight before mutation, holds the lock from snapshot through
atomic bundle replacement, and revalidates the registry snapshot and every
selected candidate's original evidence immediately before replacement. Any
descriptor/profile/resource mapping, descriptor bytes, or media normalized/real
path, fileKey, size, modification time, or parsed declarations/status/URL drift
aborts before mutation; all candidates pass together, with no
partial registration. Internal file metadata and path identities remain
transactional and are never emitted in the provider-neutral SmartDox record.

The immutable local plan retains the descriptor's exact bytes and interpreted
metadata, plus each admitted file's normalized/real path, fileKey, size, and
modification time. Direct-file snapshots compare attributes before, between,
and after two transient reads, check both lengths, and compare the read arrays
directly. This consistency check introduces no stored fingerprint. Registration
re-resolves and reparses the admitted paths under the same lock. Normal
freshness uses existence, modification time, and declared inputs; detection of
every same-size payload rewrite with deliberately restored modification time
is not part of this contract.

Site-local infographics, watch-only external videos, and the two accepted PDF
references are strict SmartDox input. They create no
`cozy.article-media-integrity.v1` entry: unlike Phase 26 artifact-path-bearing
media, their record has no Cozy artifact correlation. PDF strict records carry
only `public_path`, `media_type`, and optional `label`; no Cozy path, hash,
receipt, renderer, or integrity data is emitted.

## Media Evidence Boundary

An infographic is eligible only when its named profile exists both in the
descriptor and selected resource publications, and its resolved destination is
an existing direct regular non-symlink file beneath the bound profile root. Its
public path is a separately explicit accepted SmartDox root-relative path; it
is never derived from that filesystem destination. Infographic and video
evidence are both captured during complete preflight and revalidated under the
same publication-root lock immediately before replacement; the specification
defines the evidence snapshot and comparison contract.

A video is eligible only when its explicit descriptor-root-relative `production`
path resolves to a direct regular non-symlink JSON file with no symlink
traversal. The production file is exact-path JSON evidence; the normative
specification fixes its object-root grammar, required statuses, accepted
YouTube URL forms, and
unchanged URL emission. Its production category/article compose the exact
declared article identity and its language equals the resource language.
Rendering requires completed technical and visual QA, and the external
publication requires a published YouTube URL. Human listening review may remain
pending; it is evidence only and is never serialized as accepted listening.

An `article_pdf` or `summary_slides_pdf` is eligible only from the resolved
Cozy resource's direct output (or source for a prebuilt resource), after
reusing the current Phase 40 receipt-v2 and PDF review-state APIs. The binding
captures that direct output's normalized/real path, fileKey, size, and
modification time in its immutable
plan. Revalidation rereads the same resolved output and requires both current
Phase 40 evidence documents again under the publication-root lock immediately
before replacement. Registration consumes no renderer, receipt, or review
state fields beyond this currentness gate. Both normal and WIP paths consume
ordinary receipt and selected-PDF review-state APIs with no fallback methods.
Copy/prebuilt producer inputs are direct source and explicitly named site-video
production, while full declaration validation and operation-race snapshots
remain required. Known stored fields load directly; arbitrary extra properties
are ignored. Selected known PDF metadata is compared against the declaration
and accepted entry, and unrelated PDF staleness does not veto that selection.
No accepted primary-output/state digest is compared; timestamp-preserved valid
rewrites have no digest-detection promise. Currentness diagnostics identify the
resource, actual failing path, producer and next target build. Presentation
artifact/renderer hashes remain unfinished S03 work. Existing distribution
integrity, registry locks and rollback behavior remain unchanged.

## Lifecycle and Exclusions

Infographic workflows register only after the accepted published infographic
exists. Video workflows register only after accepted published external-video
production metadata exists. PDF workflows register only after the current
Phase 40 PDF output, receipt-v2 evidence, and PDF review-state evidence exist.
Registration reads that evidence; it does not generate, copy, or publish PDFs.
The command may plan without mutation, but it does not generate/copy media,
upload a video, build/deploy a site, invoke `cozy bok`, scan arbitrary
target/site/repository trees, or fall back to host behavior.

This boundary does not introduce `cozy bok publish-media`, a Cozy BoK,
descriptor enumeration, BoK build/repository/staging coupling, site/artifact
deployment coupling, media generation, remote publication, YouTube mutation,
or a SmartDox Phase 9 schema/projection extension. PDF renderer and receipt
semantics remain owned by Phase 40 and are outside this registration scope.

## Acceptance Direction

The implementation acceptance uses a dedicated synthetic Part 5 normal-media
package fixture, not a Part 4 artifact or output. Its distinct `knowledge.id`
and `articleIdentity` must exercise existing `kind: infographic` resources
with an independent top-level `resources[*].role` (such as
`article-summary`), JA/EN infographic and published external-video records,
and JA/EN `article_pdf` and `summary_slides_pdf` records backed by current
Phase 40 evidence. Acceptance must prove exact PDF role mapping and strict
JSON fields (including optional labels), deterministic candidate output,
target selection, dry-run immutability, exact-locale output, preservation,
duplicate/failure atomicity, and refusal before mutation for missing, stale,
currentness-invalid, or role-incompatible PDFs. It must also state that the
fixture registration reads current evidence and performs no PDF generation,
copy, or publication. `development-process/part-5` is only a fixture identity
and does not prescribe a future Part 5 editorial title.

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
