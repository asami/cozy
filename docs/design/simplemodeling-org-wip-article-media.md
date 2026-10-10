# SimpleModeling.org Project Configuration and WIP Article Media Design

## Purpose and authority

This document promotes the project-configuration and Phase 28.1 WIP
registration portions of the `simplemodeling.org` handoff into stable design
authority. It defines the
responsibilities, boundaries, rationale, and invariants that the implementation
contract in [`docs/spec/simplemodeling-org-wip-article-media.md`](../spec/simplemodeling-org-wip-article-media.md)
must satisfy.

The design is intentionally narrower than the surrounding work ledger. Phase
28 owns configuration and profile resolution. Phase 28.1 now normatively owns
the reusable provider-neutral WIP registration contract; implementation remains
in its downstream slices. Its approved successors own the remaining
operational work:

- [Phase 28](../phase/phase-28.md) owns project discovery, configuration
  layering, and profile selection.
- [Phase 28.1](../phase/phase-28.1.md) owns disposable WIP artifact staging and
  provider-neutral local registration, as specified below.
- [Phase 28.2](../phase/phase-28.2.md) owns the
  `simplemodeling-org` Part 5 `runweb-wip` integration and rendered-card
  regression.

The [Phase 28 checklist](../phase/phase-28-checklist.md) remains a progress
ledger, not design authority. The chronological
[configuration handoff](../journal/2026/08/simplemodeling-org-project-configuration-handoff-2026-08-12.md)
is source evidence, not a normative replacement for this document or its
companion specification. This design preserves the accepted responsibilities
from [Phase 27](../phase/phase-27.md),
[`smartdox-site-media-registration` design](smartdox-site-media-registration.md),
and the [Phase 26 article-media design](article-media-publication.md).

## The special site boundary

`SimpleModeling.org` is the special `smartdox-site` case. SmartDox builds the
site directly; Cozy supplies and, through the accepted Phase 27 command,
registers article media into the SmartDox publication bundle. It is not a Cozy
BoK and must not be made one merely because its media packages use Cozy.

Standard BoK repository and publication behavior is unchanged. A standard BoK
continues through its normal Cozy BoK path. It never enters the
`smartdox-site` registration path through a source type, a profile name, a
credit choice, a repository name, or a filesystem location.

The project configuration gives deeply nested media and video packages a
shared policy context. It does not infer an article, turn every media resource
into a registration candidate, or mutate a SmartDox registry. Association,
resource opt-in, evidence validation, locking, merge, and provider-neutral
serialization remain the explicit Phase 27/26 contracts.

## Three independent concerns

The following concerns are deliberately independent and have different
selectors and owners:

| Concern | Selector | Responsibility |
| --- | --- | --- |
| Site-registration workflow | Explicit top-level `articleMedia.publicationProfile`, discovered `project.kind: smartdox-site`, and resource-level `articleMedia` opt-in | Decide whether an explicitly associated resource may participate in the SmartDox site-registration boundary. |
| Media publication destination | The selected media publication profile and its resource publication mapping | Resolve the repository/site-local destination of a media artifact. |
| Audiovisual attribution | Explicit `credits.profile`, otherwise `video.credits.default-profile` | Select credit text, identities, and license presentation for a video. |

No selector in one row authorizes a responsibility in another row. In
particular, `credits.profile` is never a standard-BoK versus SmartDox-site
selector, and a publication destination never supplies an article association.

## Shared project context

Media profile resolution, video-credit discovery, and site binding consume one
shared project-context/root resolution result. They do not each walk ancestors
independently. The shared result carries, conceptually, the discovered project
root, the project configuration layer set, normalized path boundaries, and
provenance for selected values. Subsystems may derive their own effective
profile from this result, but they must not replace it with a second ancestor
walk or a repository scan.

Discovery starts at the descriptor/package directory and walks lexical parents
to the filesystem root looking for the nearest valid direct marker
`conf/cozy/config.yaml`. Every traversed directory and every marker component
must be a direct, non-symlink filesystem entry. A marker is a direct regular
file. A normalized or ambiguous path, a symlink substitution, an unsafe root,
or an unsafe containment result fails closed. Discovery never consults Git.
When no valid marker exists, the shared context records the absence and the
legacy package-local or standalone behavior remains in force.

The context is established once and then passed to media planning/inspection,
video credit resolution, and site binding. Diagnostics may expose its project
root and configuration provenance to operators. Public SmartDox records must
not expose host paths, hashes, or internal provenance.

## Configuration grammar and layers

The project YAML grammar is intentionally limited to the handoff's existing
keys; this design does not invent a schema field:

```yaml
project:
  id: simplemodeling-org
  kind: smartdox-site

media:
  publication-profiles:
    simplemodeling-org:
      root: .
      site-kind: smartdox

video:
  credits:
    default-profile: simplemodeling-org
```

The canonical SimpleModeling values are project id `simplemodeling-org`, kind
`smartdox-site`, publication-profile id `simplemodeling-org`, project profile
root `.`, site-kind `smartdox`, and default credit profile
`simplemodeling-org`. The shared credit profile itself remains in the
layer-specific `video/credit-profiles` directory and retains its established
`cozy.video.credits.v1` schema.

`site-kind` is a project/package configuration field. The existing
descriptor-local publication-profile schema remains limited to its supported
location fields, `root` and `rootEnv`; this design does not add a descriptor
`site-kind` field or invent another schema. A configuration publication
profile therefore supplies the site kind, configured location, and its
provenance.

Effective configuration is deterministic. Lower layers are read first and
later layers replace an exact ID or value:

```text
built-in
  < user ~/.cozy
  < project conf/cozy
  < project .cozy
  < package conf/cozy
  < package .cozy
  < explicit descriptor/CLI selection
```

Duplicate profile IDs within one layer are an error, not an ordering choice.
The source layer and source file of every selected publication or credit
profile are retained as provenance. A package configuration profile
intentionally overrides a project configuration profile with the same ID.
An explicit descriptor or CLI selection chooses the ID over defaults and
merged definitions; an `articleMedia.publicationProfile` is never silently
defaulted. The effective publication profile is field-aware: a same-ID
descriptor-local profile overlays only the supported location fields
(`root`/`rootEnv`) on the selected configuration profile. The configuration
profile's `site-kind` and its provenance remain effective; a descriptor-local
profile cannot replace or invent them.

Project or package configuration publication-profile `root` values are
relative to the discovered project or package context. Existing
descriptor-local `profiles` location fields remain descriptor-relative.
Existing explicit `rootEnv`/external-root compatibility is retained only
where that contract already supports it; only that already-supported explicit
selection may lie outside project containment. It must still pass the existing
direct-entry, non-symlink, normalized, canonical-real, and boundary checks.
Ordinary `root` values and every other escaping root fail closed. This design
does not infer, widen, or silently enable an external root.

Credit profile files are resolved from each layer's
`video/credit-profiles` directory. An explicit `credits.profile` wins over
`video.credits.default-profile`. Package-local and standalone profiles remain
valid, and an unknown profile reports every searched layer and its provenance
attempts. Credit selection never chooses site registration.

Descriptor-local media profiles merge over project/package configuration
`media.publication-profiles` as field-aware composites. An exact descriptor ID
overlays only its supported `root`/`rootEnv` location fields; the selected
configuration profile supplies the site kind and retains its provenance. A
descriptor-only profile remains valid for legacy publication/standalone
behavior, but it has no configured site kind and therefore cannot authorize
`smartdox-site` registration. A resource still needs its own valid
`articleMedia` block and matching publication mapping. A top-level
`articleMedia.publicationProfile` must name the effective selected profile;
configuration presence alone does not opt in a resource.

## Association and evidence boundary

The Phase 27 association remains explicit and exact:

1. The descriptor has a complete top-level `articleMedia` association with an
   explicit `articleIdentity` and `publicationProfile`.
2. The discovered project is `project.kind: smartdox-site`, and the same-ID
   effective configuration publication profile supplies `site-kind: smartdox`
   with its configuration provenance. A descriptor-local profile may overlay
   only `root`/`rootEnv` location fields and cannot supply site kind.
3. Each candidate resource independently opts in with a complete resource
   `articleMedia` block and a matching publication mapping.

Missing association or resource opt-in means no registration. A selected
profile resolves a destination; it does not select an article identity or
register every resource. Article identity is never inferred from
`knowledge.id`, source type, paths, filenames, repository names, target trees,
or generated site output.

Video registration retains the Phase 27/26 production gate: the exact
production evidence must show completed rendering, technical-and-visual QA,
and an accepted published YouTube URL. Human listening review may remain
pending; it is evidence-only, non-gating, and never serialized as accepted
listening. A WIP local video does not require YouTube publication, but WIP is
owned exclusively by Phase 28.1 and its Part 5 integration by Phase 28.2. WIP
state must not promote or spoof production evidence.

The public SmartDox record remains provider-neutral and contains only the
accepted Phase 26/27 schema. Host paths, hashes, source-layer names, and
other internal provenance are diagnostics/evidence, never serialized public
fields.

## Deterministic selectors

The boundary is summarized by this matrix. “No” means that condition alone is
insufficient and must not trigger registration:

| Condition | Selects site-registration workflow? | Why |
| --- | --- | --- |
| `project.kind: smartdox-site` alone | No | Project identity does not associate an article or a resource. |
| Source type alone (`source-type: smartdox`) | No | Source type describes a source, not registration consent. |
| Descriptor-only publication profile | No | It remains valid for legacy publication/standalone behavior, but has no configured site kind and cannot authorize site registration. |
| Configured publication profile without explicit association/resource opt-in | No | A destination definition is not an association or resource opt-in. |
| Same-ID descriptor location overlay + configured `site-kind: smartdox` | Potentially yes, subject to Phase 27 evidence and transaction checks | The descriptor contributes only supported location fields; configured site kind and provenance remain effective. |
| `credits.profile` alone | No | Credits select audiovisual attribution only. |
| Path or repository name alone | No | Filesystem naming is not explicit article identity. |
| No top-level `articleMedia` | No | There is no explicit site association. |
| Explicit association + discovered `project.kind: smartdox-site` + same-ID configured `site-kind: smartdox` profile + resource opt-in | Yes, subject to Phase 27 evidence and transaction checks | All independent selectors are present. |

`register-site` remains explicit. No target/site scan, registry edit by hand,
or implicit registration is part of this design.

## Phase 28.1 WIP contract

Phase 28.1 is now normative for reusable provider-neutral WIP registration.
Implementation is deferred to its downstream slices, but the following
contract is frozen here and in the companion specification. It does not alter
the already-accepted Phase 27 `register-site` behavior.

### Two independent roots and command grammar

The command is exactly:

```text
cozy media register-site-wip <media-file> --publication <publication-root> --website <website-root> [--target <resource-id>] [--dry-run]
```

`<media-file>` is one descriptor and both roots are required. The publication
root is the existing direct registry/publication root read by Dox before
rendering; the website root is the disposable tree containing final site
bytes. Neither root is inferred from the other. Parsing is strict: duplicate,
unknown, missing, and extra options fail before mutation. Equals and separated
forms follow existing `register-site` conventions. `--profile` is unsupported.
A value beginning with `-` is accepted only through its equals form. The
command performs no root creation, network, upload, publication, deployment,
or site build.

### Deterministic candidates and evidence

Candidate selection is the ordered intersection of four independent selectors:
explicit top-level association; discovered `project.kind: smartdox-site`;
same-ID configured `site-kind: smartdox`; and exact resource-level opt-in.
Without `--target`, every opted-in candidate is selected; with `--target`, one
exact target is selected. Zero, invalid, ambiguous, or duplicate locale/role
candidates fail before mutation. Locale matching is exact and has no fallback
or opposite-locale substitution. The selected candidate output and plan order
is ascending exact normalized `resourceId`; an exact target produces its one
entry. Destination installation remains sorted by site-relative path.

An infographic reuses the Phase 27 validated site-public `publicPath` and the
selected mapped destination evidence. Phase 28.1 does not copy an infographic
into the website root.

A local-video resource MUST be `kind: video`, have role `video`, and have an
exact-language match and a present output. Output resolution uses the existing
Cozy media output contract; it MUST NOT invent a `production.json` artifact
path. The resolved output is a normalized direct regular, non-symlink MP4.
Its production JSON remains the identity, language, render, and QA authority:
`render.status` is `completed` and `render.qa.status` is
`technical-and-visual-qa-passed`. WIP explicitly adopts the selected supplied
MP4; production JSON has no local render-digest admission gate. Ordinary JSON
loading ignores extra properties without dedicated legacy hash processing.
WIP does not require or serialize YouTube; listening review is evidence-only
and non-gating.

### Phase 40 PDF WIP registration

WIP accepts `article_pdf` and `summary_slides_pdf` only as strict reuse
candidates when the resolved resource is a `document` in canonical `ja` or
`en`, its `articleMedia.publicPath` is an exact site-visible path, its media
type is exactly `application/pdf`, and its optional label is already an exact
nonblank value. The candidate output is the existing resolved output, or the
source only for a `prebuilt` resource. That direct regular non-symlink output
must have current `cozy.media.receipt.v2` and
`cozy.media.pdf-review-state.v1` evidence from the Phase 40 acceptance
boundary. The WIP binding revalidates those exact conditions under the existing
two-root and registry locks before replacement. The WIP path uses ordinary
receipt and selected-PDF review-state currentness without a site-only fallback.
Copy consumes its direct source; prebuilt consumes its direct source and any
explicitly named site-video production file. Package knowledge and unrelated
inputs remain current-command validation/race inputs, not copy/prebuilt
conversion inputs. Known state/receipt fields load directly and arbitrary stored
extras are ignored. Only selected PDF metadata is compared with its resolved
resource and accepted entry; stale unselected PDFs cannot veto unchanged English
selection. Failures name the stage, resource, actual source/output/state or
manifest path, producer and next target build. No accepted-output/state digest
is used, and valid timestamp-preserved rewrites have no digest-detection promise.
Presentation artifacts and renderer hashes remain unfinished S03 work. Real
distribution byte integrity, locking and rollback remain unchanged.

The resulting strict variant maps `article_pdf` only to `articlePdf` and
`summary_slides_pdf` only to `summarySlidesPdf`, retaining exactly the public
path, `application/pdf`, and optional label. PDF candidates are reuse-only:
they perform no copy, installation, destination or parent construction,
backup, rollback entry, website-root mutation, or PDF integrity correlation.
They do not alter `CozyMediaReceipt`, `CozyMediaPdfReviewState`, or the
`CozyArticleMediaIntegrity` contract. Existing video staging/install/rollback
and its correlated WIP integrity record, and existing infographic reuse,
remain unchanged.

### Site path and public record

Phase 28.1 accepts an article identity only when it has exactly two normalized
segments, `<category>/<article>`; every other segment count fails before path
construction. For that identity and locale `<locale>`, the exact content URL
is `/<locale>/<category>/videos/<article>.mp4`. The destination is the
website-root-relative path with the leading slash removed. Article and locale
MUST already satisfy existing normalization. No raw resource ID or filename
participates in destination construction.

Each local-video record is exactly `presentation=site-hosted`,
`status=published`, and `content_url=<exact path>`. It omits provider,
`watch_url`, every host path/hash/provenance field, and all other internal
evidence. “Published” means available in this disposable WIP tree; it does not
mean YouTube publication or production promotion.

The strict record remains free of integrity evidence, but it has exactly one
ordinary correlated `cozy.article-media-integrity.v1` video record. That
record uses additive `wip-site-video` provenance: its normalized
project-root-relative `descriptor`, exact `resourceId`, and `production` are
the required provenance fields; artifact identity is that resource ID; artifact
version is the opaque 64-character source SHA-256; `publicPath` is the exact
content URL; `repositoryPath` is the website-root-relative destination;
`mediaType` is `video/mp4`; and source, staged, and installed destination
digests are equal. For this provenance only, `repositoryPath` has that website
root meaning and `published` means installed in the disposable WIP tree.

Before staging or registry mutation, WIP inspects existing exact
`(articleIdentity, locale, video)` strict and integrity state. It is admissible
only when both records are absent, or when the existing pair is canonical after
current full evidence revalidation. A canonical strict video is exactly
`presentation=site-hosted`, `status=published`, absent `provider`, absent
`watch_url`, and the deterministic WIP `content_url`. It has exactly one
canonical integrity record for that normalized tuple: schema
`cozy.article-media-integrity.v1`, role `video`, provenance exactly
`wip-site-video` consisting only of its kind and the exact normalized
descriptor, resource ID, and production; artifact identity equal to the selected resource ID; artifact
version equal to the current source SHA-256; public path equal to that content
URL; repository path equal to the exact site-relative destination; `video/mp4`;
SHA-256 equal to the current source digest; and `publicationState=published`.
The installed destination must exist as a direct non-symlink regular file whose
digest equals that current source digest. Any absent, extra, malformed, stale,
or mismatched field, record, or destination fails. This permits repeat WIP
replacement but never overwrites production; infographic preservation remains
the existing Phase 27 behavior. It is semantic registry-state isolation, not a
new production marker or root schema.

PDF strict records are independent role replacements in the same owner bundle;
their current receipt/review-state evidence is validation input only and is
never serialized. A PDF WIP registration does not create a website output,
correlated integrity entry, staging artifact, backup, rollback item, or
production-state transition.

### Two-root transaction

Non-dry execution first acquires a direct regular non-symlink
`.cozy-article-media-wip.lock` in each root in canonical root-identity lexical
order, then acquires the nested existing publication-registry lock; release is
the reverse order. These durable coordination artifacts are validated before
use and excluded from candidate and output state. A pre-existing invalid lock
fails. Dry-run creates and acquires no lock and instead relies on captured and
revalidated read-only evidence.

Preflight MUST capture coherent evidence for descriptor and project
configuration/profile, resource mapping, infographic destination, PDF output
and current Phase 40 receipt/review-state evidence, production JSON, source
MP4, publication-registry snapshot/root, website root, and every video
destination. Ordinary local file observations are normalized lexical and real
paths, size, actual `FileTime` modification time, and stable fileKey, with
direct regular non-symlink checks and coherent two-read bytes/attributes.
The plan also retains the exact transient descriptor bytes and selected
production JSON bytes for same-operation revalidation. Even an equivalent
production JSON rewrite with unchanged size, modification time, and fileKey
invalidates that operation's byte observation. No generic file snapshot hash,
PDF hash, persisted observation identity, or command-wide MP4 byte retention
is introduced. Explicit `sourceArtifactSha256` and optional
`installedArtifactSha256` protect only the actual selected MP4 publication
record and its named copy, backup, installation, and restoration operations.
Under both root locks and the registry lock, it revalidates all
evidence, builds the canonical correlated strict-plus-integrity plan, creates
same-filesystem sibling temps only for video candidates, copies, fsyncs, and
verifies their digests, then creates verified sibling backups only for
existing video destinations. It installs video destinations by same-filesystem
atomic replacement in sorted site-relative-path order, validates installed
bytes, atomically replaces the single selected owner registry bundle last,
validates registry and video destinations while locked, and then removes
backups and temps. PDF candidates contribute no staging or destination
mutation. One descriptor replaces exactly one owner bundle.

On failure after any mutation, if registry replacement occurred, rollback first
atomically restores its exact original owner-bundle bytes (or removes a newly
created canonical bundle), then restores pre-existing destinations and removes
new destinations in reverse install order, then removes temps and backups. It
revalidates the complete original registry (including its actual bundle bytes),
unchanged root/source/production/context and candidate evidence, and each
original/restored optional destination artifact digest before reporting failure.
The restoration-only predicate recomputes ordinary planning and admits the
actual direct destination identity and modification time after an atomic
backup move; it does not require that backup inode to equal the former
destination fileKey. Strict pre-mutation revalidation still requires the
original full plan, and installed-byte validation remains strict. Successful
rollback reports the original application failure. A rollback failure is distinct and never reported as
success; no application exception may return success with partial state.

### Root and path safety

Both roots and every exact destination parent MUST already exist as normalized
direct non-symlink directories with stable canonical identity; the command
creates no parent. Reject the filesystem root; user home; the discovered
project root or any ancestor; equal publication and website roots; and either
root being an ancestor or descendant of the other. A website root may be a
descendant of the project root, while an external direct publication root may
be admitted only when all direct-entry, canonical, non-symlink, and containment
checks pass. Also reject symlink ancestors or destinations, path escape,
non-regular inputs, selected MP4 artifact-integrity mismatch, identity drift, and cross-filesystem non-atomic
staging. The command performs no network/upload/publish/deploy/site-build
operation.

Phase 27 external YouTube `register-site` behavior and standard BoK
repository/publication behavior remain unchanged. Phase 28.2 alone wires
`runweb-wip` and Part 5 repository fixtures.

## Security and invariants

- Ancestor and profile resolution follows no symlink. Marker files are direct
  regular files and marker/configuration directories are direct directories.
- Required normalized lexical and existing canonical-real containment checks
  against the selected boundary both hold. Only an already-supported explicit
  `rootEnv`/external-root selection may lie outside project containment, and it
  remains subject to direct-entry, non-symlink, normalized, canonical-real,
  and boundary checks.
  Ordinary roots and all other escaping roots fail closed; no external root is
  inferred or broadened.
- Discovery stops at the filesystem root and has no Git dependency.
- No broad repository, target, generated-site, Docker-mount, or locale scan is
  introduced. No locale fallback is introduced.
- No network, upload, deployment, generation, or registry mutation occurs in
  Phase 28 configuration/profile resolution.
- Explicit association, exact article identity, matching profile, and resource
  opt-in remain mandatory. Missing or ambiguous values fail closed.
- Layer order and provenance are deterministic; duplicate IDs within one layer
  fail rather than depending on filesystem order.
- Standard BoK behavior, standalone packages, package-local credit profiles,
  explicit credits, existing command syntax, and host/Docker argv remain
  compatible.
- Existing Phase 27 lock, atomicity, and evidence-revalidation guarantees are
  unchanged. A context resolver cannot weaken the registration transaction.

## Rejected approaches

The following alternatives are explicitly rejected:

- selecting the workflow by source type;
- selecting it because a descriptor contains a profile;
- selecting it from paths or a repository name;
- treating `credits.profile` as a site selector;
- scanning target or generated-site trees;
- editing a SmartDox registry directly;
- implicitly registering every resource in a `smartdox-site` project; and
- promoting WIP state to production state.

These rejections preserve explicit association and the provider-neutral Phase
27/26 boundaries.

## Compatibility and phase ownership

| Existing behavior or responsibility | Design decision |
| --- | --- |
| Standard BoK build/publication | Unchanged; never routed through `smartdox-site`. |
| Standalone/package-local media profiles | Descriptor-relative resolution remains supported. |
| Package-local credit profiles | Continue to override project definitions by exact ID. |
| Explicit video `credits.profile` | Wins over defaults and affects attribution only. |
| Existing command syntax and host/Docker video argv | Unchanged. |
| Provider-neutral Phase 27 registry | SmartDox records stay schema-compatible and integrity-free; the additive Cozy integrity provenance remains separate. |
| Phase 27 atomicity/revalidation | Retained; configuration provenance is part of preflight evidence, not a bypass. |
| WIP staging/registration | Normative Phase 28.1 contract above; implementation remains in downstream slices. |
| Part 5 `runweb-wip` integration/regression | Phase 28.2 only. |

Phase 28's implementation scope stops at configuration and profile resolution.
Phase 28.1's contract above is nevertheless normative now; its implementation,
Part 5 migration, `runweb-wip` integration, source changes, and generated
artifacts remain downstream work.

## Acceptance direction

The companion specification freezes executable acceptance for project-root
discovery, layer precedence/provenance, shared credit resolution, media profile
merging, the SmartDox registration boundary, and the Phase 28.1 WIP CLI,
candidate/media evidence, path/serialization, two-root safety, atomic
rollback/drift, dry-run parity/repeatability, and production/standard-BoK
exclusions. Those acceptance cases are documentation of externally testable
behavior; this Slice does not add Scala tests or claim that any implementation
or validation has completed.

## 2026-10-09 Phase 71.5 shared-context input amendment

Authority: [Phase 71.5](../phase/phase-71.5.md),
`P715-02-S13-CONTEXT-DESCRIPTOR-READ`. Shared configuration resolution uses one
actual read of each selected configuration after initial direct-file and
lexical/real-path admission, reusing the initial project-marker input in its
layer. Repeated byte/attribute comparisons, the between-read mutation hook,
and the production-unused whole-context revalidation API are removed without
a replacement proof. Earlier accepted contracts remain dated history; this
amendment supersedes only their between-read and whole-context revalidation
promises.

The same nearest lexical project discovery, exact six candidates/order,
native parsers, profile grammar, layer precedence, source provenance,
selected-root containment, ordinary I/O/parse failures, and JVM `user.home`
coordination remain. Existing [CozyProjectContextSpec](../../src/test/scala/cozy/config/CozyProjectContextSpec.scala)
retains those semantic scenarios.

Original Context directory/file metadata and initial stable-key admission
remain coupled to WIP consumers. Their removal and WIP/SiteBinding/transaction
protection remain OPEN finite `R715-12/14` obligations, rather than an accepted
preservation exception or a completed Context removal. Their existing contract
is not globally rewritten here. The [S13 journal](../journal/2026/10/2026-10-09-phase-71.5-context-descriptor-read.md)
preserves its initial implemented-unvalidated progress and pending
parent-selected focused validation. No Step or Phase acceptance is claimed.

2026-10-09 actual-result continuation: S13 is
implemented-focused-validated-unreviewed. The selected five existing suites
completed with zero aborted suites: 104 tests succeeded, with zero failed,
canceled, ignored or pending tests. SBT reported zero warnings and zero errors;
SBT and wrapper exits were 0 and the terminal shared serial lock was released.
No hostile-XML diagnostic lines occurred in this selected command. This result
does not supply native, Step or Phase acceptance; all recorded open obligations
remain OPEN.

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
