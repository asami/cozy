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
`render.status` is `completed`, `render.qa.status` is
`technical-and-visual-qa-passed`, and its `render.sha256` equals the source
MP4 SHA-256. `render.sha256` is required to be a JSON string matching exactly
`[0-9a-f]{64}`: no null, whitespace, prefix, uppercase character, alternate
length, or other interpretation is accepted. WIP does not require or serialize YouTube; listening review is
evidence-only and non-gating.

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
two-root and registry locks before replacement.

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
destination. Under both root locks and the registry lock, it revalidates all
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
revalidates original registry, root, destination bytes, and identities before
reporting failure. A rollback failure is distinct and never reported as
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
non-regular inputs, stale SHA, identity drift, and cross-filesystem non-atomic
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
