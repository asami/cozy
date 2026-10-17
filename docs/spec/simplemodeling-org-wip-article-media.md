# SimpleModeling.org Project Configuration and WIP Article Media Specification

## Contract basis

This is the authoritative Phase 28/28.1 contract for project discovery,
configuration layering, profile resolution, and provider-neutral WIP article
media registration. Its responsibility rationale is
[`docs/design/simplemodeling-org-wip-article-media.md`](../design/simplemodeling-org-wip-article-media.md).

The contract reuses, without weakening, the
[Phase 27 phase contract](../phase/phase-27.md),
[SmartDox site-registration specification](smartdox-site-media-registration.md),
[Phase 26 article-media design](../design/article-media-publication.md), and
[Phase 26 article-media specification](../spec/article-media-publication.md).
The [Phase 28](../phase/phase-28.md),
[Phase 28.1](../phase/phase-28.1.md), and
[Phase 28.2](../phase/phase-28.2.md) documents describe work ownership and
successors; they are not a substitute for this contract. The journal handoff
is source evidence only.

The words **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative. This
documentation-only Slice freezes behavior and acceptance cases for both
configuration and the Phase 28.1 WIP contract; it does not claim product
implementation, test execution, validation, or completion.

## 1. Canonical project discovery

### 1.1 Marker and start point

The canonical project marker is the exact relative path:

```text
conf/cozy/config.yaml
```

Given a media descriptor, video descriptor, or package, discovery MUST start at
its package directory. The starting directory and each lexical parent visited
on the way to the filesystem root MUST be a direct, non-symlink directory. The
resolver MUST inspect the exact marker at each candidate ancestor and MUST walk
lexical parents only; it MUST NOT consult Git, repository metadata, generated
output, or a broad target/site scan.

`conf`, `cozy`, and `config.yaml` at a candidate marker MUST be direct entries:
the first two MUST be direct directories and the last MUST be a direct regular
file, all non-symlink. A candidate marker with a symlink component, an unsafe
normalization, an ambiguous identity, or an escaping containment result is
invalid and fails closed. The resolver MUST NOT silently select a different
ancestor after an unsafe marker is encountered.

The nearest valid marker wins. Its project root is the ancestor directory that
contains `conf/cozy`. The walk stops at the filesystem root. If no valid marker
exists, discovery reports absence and preserves legacy package-local and
standalone behavior; it does not manufacture a project root.

The same result MUST be reused by media profile resolution, video credit
resolution, and SmartDox site binding. Those consumers MUST NOT perform
independent ancestor walks.

### 1.2 Path safety

Before a discovered context is used, the resolver MUST normalize lexical paths,
verify direct-entry status, and check containment. Canonical-real checks MUST
also reject symlink substitution where the existing contract requires a real
identity. An unsafe or ambiguous descriptor/package path, marker, project root,
or selected profile root fails closed. Only an already-supported explicit
`rootEnv`/external-root selection may lie outside project containment; it MUST
still pass the existing direct-entry, non-symlink, normalized, canonical-real,
and boundary checks. Ordinary `root` values and every other escaping root MUST
fail closed. No rule in this specification infers or broadens an external,
Docker-mount, or publication root.

## 2. Canonical project configuration grammar

The project configuration grammar is exactly the following handoff grammar;
no additional schema field is introduced by this contract:

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

The canonical SimpleModeling values are:

| Key | Canonical value |
| --- | --- |
| `project.id` | `simplemodeling-org` |
| `project.kind` | `smartdox-site` |
| `media.publication-profiles.simplemodeling-org.root` | `.` |
| `media.publication-profiles.simplemodeling-org.site-kind` | `smartdox` |
| `video.credits.default-profile` | `simplemodeling-org` |

The `media.publication-profiles` map is keyed by profile ID. Project/package
configuration profile entries supply configured location and `site-kind` data.
The existing descriptor-local profile schema remains limited to its supported
location fields, `root` and `rootEnv`; it does not gain a `site-kind` field.
Credit files retain the established `cozy.video.credits.v1` schema and are
found below the layer's `video/credit-profiles` directory; this project
configuration does not inline or redefine that credit schema.

## 3. Layer precedence and provenance

### 3.1 Effective order

Configuration layers MUST be applied in this exact order, from lowest to
highest precedence:

```text
built-in
  < user ~/.cozy
  < project conf/cozy
  < project .cozy
  < package conf/cozy
  < package .cozy
  < explicit descriptor/CLI selection
```

The project layers are rooted at the discovered project root. Package layers
are rooted at the package/descriptor directory. A layer that is absent has no
values and does not cause a failure by itself. Within one layer, duplicate
publication-profile IDs or duplicate credit-profile IDs MUST fail; filesystem
enumeration order MUST NOT choose a winner. Across layers, later layers
override an exact ID or value deterministically.

An explicit descriptor or CLI selection chooses the ID over defaults and
merged configuration definitions. In particular, a package configuration
profile wins over a project configuration profile with the same ID, and an
explicit `credits.profile` wins over `video.credits.default-profile`. After a
configuration publication profile is selected, a same-ID descriptor-local
profile overlays only its supported `root`/`rootEnv` location fields. The
configuration profile's `site-kind` and provenance remain effective; a
descriptor-local profile cannot replace or invent them. The explicit
top-level `articleMedia.publicationProfile` is required for site registration
and MUST never be defaulted from a project profile, source type, path, or
repository name.

### 3.2 Root interpretation

`root` in a project or package configuration `media.publication-profiles`
entry is resolved relative to that configuration context. Existing
descriptor-local `profiles` location fields remain relative to the descriptor
root. The two bases MUST NOT be conflated.

The existing explicit external-root/`rootEnv` compatibility is retained only
where already supported by the relevant profile contract. Only that explicit
selection may lie outside project containment, and it MUST still satisfy the
existing direct-entry, non-symlink, normalized, canonical-real, and boundary
checks. Ordinary `root` values and all other escaping roots MUST fail closed.
A resolver MUST NOT infer an environment variable, silently permit an
external root, or broaden the existing contract. Every selected root is
normalized and checked for the required lexical and canonical-real
containment.

### 3.3 Provenance and unknown values

For each selected publication or credit profile, the effective context MUST
retain:

- the selected ID;
- the effective layer and source file;
- the selection layer/file when selection came from a default; and
- the resolved project root and project configuration marker when present.

If a requested credit or publication profile is unknown, the diagnostic MUST
name the requested ID and enumerate every searched layer, including absent
layers and the source/provenance of definitions that were considered. It MUST
not silently fall back to a different profile or locale.

## 4. Video credit resolution

Credit profile files MUST be resolved from each searched layer's direct
`video/credit-profiles` directory, with the layer order in Section 3. An
explicit `credits.profile` in the video descriptor wins over
`video.credits.default-profile`. If the descriptor omits `credits.profile`, a
configured default may select it. A package-local profile can intentionally
override a project profile with the same ID. Existing standalone video
projects that have only package-local profiles MUST continue to resolve them.

Credit resolution MUST report selected profile provenance in operator-facing
diagnostics. It MUST NOT select or authorize the site-registration workflow;
`credits.profile: simplemodeling-org` is attribution only.

Credit selection is exact-ID selection. Duplicate IDs within a layer fail,
unknown IDs enumerate all searched layers, and missing profiles preserve the
existing no-fallback behavior.

## 5. Media publication-profile resolution

Project/package configuration `media.publication-profiles` are reusable
definitions. Descriptor-local media profiles merge over configuration
definitions as field-aware composites; when an ID occurs in both, the
descriptor-local definition overlays only its supported `root`/`rootEnv`
location fields. The selected configuration definition supplies `site-kind`
and retains its provenance. A descriptor-only profile remains valid for legacy
publication/standalone behavior, but because no configured site kind exists it
MUST NOT authorize `smartdox-site` registration. Existing descriptor profile
grammar and publication mappings remain valid, including package-local and
standalone media packages.

The top-level media descriptor MUST carry an explicit, non-empty
`articleMedia.publicationProfile` to enter SmartDox site registration. The
resolver MUST NOT supply a default for that field. The selected ID MUST resolve
to an effective profile, and each candidate resource MUST carry its own valid
resource-level `articleMedia` block and matching publication mapping. A profile
definition, publication destination, or project configuration alone does not
opt a resource in.

Project profile roots use the project-relative rule in Section 3.2; descriptor
profile roots use descriptor-relative resolution. A resource's explicit
publication mapping remains the source of its destination. No destination may
be inferred from a filename, directory, generated site, target tree, or
repository name.

## 6. SmartDox registration boundary

The special workflow is selected only when all independent conditions hold:

1. The descriptor has a complete top-level `articleMedia` object with explicit
   `articleIdentity` and `publicationProfile`.
2. The shared project context discovers `project.kind: smartdox-site`, and the
   same-ID effective configuration publication profile supplies
   `site-kind: smartdox` with its configuration provenance. A descriptor-local
   profile may overlay only `root`/`rootEnv` location fields and cannot supply
   site kind.
3. Each participating resource has its own complete resource-level
   `articleMedia` opt-in and matching publication mapping.

The following matrix is normative:

| Condition present | Registration authorized? |
| --- | --- |
| Project kind alone | No. |
| Source type alone | No. |
| Descriptor-only publication profile | No; it remains valid for legacy publication/standalone behavior, but has no configured site kind. |
| Configured publication profile without explicit association/resource opt-in | No. |
| Same-ID descriptor location overlay plus configured `site-kind: smartdox` | Potentially yes, after Phase 27 validation and transaction checks. |
| `credits.profile` alone | No. |
| Path or repository name alone | No. |
| No top-level `articleMedia` | No. |
| Explicit association + discovered `project.kind: smartdox-site` + same-ID configured `site-kind: smartdox` profile + resource opt-in | Potentially yes, after Phase 27 validation and transaction checks. |

The top-level association MUST remain explicit; article identity MUST NOT be
inferred from `knowledge.id`, source type, paths, filenames, package names,
repository names, target contents, or SmartDox output. `register-site` remains
an explicit command. Resources without a valid resource-level block are not
registered, and no scan may discover implicit candidates.

The public result MUST remain the provider-neutral Phase 26/27 SmartDox
`article-media-publication` schema. It MUST NOT serialize host paths, hashes,
source paths, layer names, configuration paths, or other internal provenance.
The Phase 27 lock, complete preflight, atomic merge, and evidence-revalidation
rules remain in force.

### 6.1 Production and WIP evidence

Production video registration requires the existing Phase 27 evidence:

- `render.status` is `completed`;
- `render.qa.status` is `technical-and-visual-qa-passed`; and
- `youtube.status` is `published` with an accepted YouTube URL.

`render.listeningReview` may remain pending. It is evidence-only, non-gating,
and MUST NOT be serialized as accepted listening. A WIP local video does not
need YouTube publication. The normative Phase 28.1 WIP contract is in Section
6.2; its implementation remains in downstream slices. SimpleModeling.org Part
5 `runweb-wip` integration/regression belongs exclusively to
[Phase 28.2](../phase/phase-28.2.md). WIP state MUST NOT be promoted or
presented as production evidence.

Standard BoK behavior remains normal repository/publication behavior and never
uses this `smartdox-site` boundary.

## 6.2 Phase 28.1 WIP command and two-root boundary

Phase 28.1 freezes a second, provider-neutral command. It does not change the
Phase 27 `register-site` grammar or its accepted external-YouTube behavior.
The exact WIP grammar is:

```text
cozy media register-site-wip <media-file> --publication <publication-root> --website <website-root> [--target <resource-id>] [--dry-run]
```

`<media-file>` is exactly one descriptor. `--publication` and `--website` are
both required and name two independent roots:

- the publication root is the existing direct registry/publication root read by
  Dox before rendering; and
- the website root is the disposable tree containing final site bytes.

Neither root is inferred from the other, from a profile, or from a project
path. Parsing is strict: duplicate, unknown, missing, or extra options fail
before mutation. Equals and separated forms follow the existing `register-site`
conventions. `--profile` is unsupported. A value beginning with `-` is
accepted only with its equals form. A root is never created by this command.

## 6.3 WIP candidate selection and evidence

WIP candidate selection requires all four independent selectors:

1. a complete explicit top-level `articleMedia` association;
2. discovered `project.kind: smartdox-site`;
3. the same-ID effective configured publication profile has
   `site-kind: smartdox`; and
4. a complete exact resource-level `articleMedia` opt-in.

Without `--target`, all and only opted-in resources are candidates. With
`--target`, exactly one resource ID is selected; it must already exist and be
opted in, and the target never creates an association. Zero candidates,
invalid or ambiguous candidates, and duplicate normalized
`(articleIdentity, locale, role)` keys fail before mutation. Locale equality is
exact; no fallback or opposite-locale substitution is permitted. Output and
plan order MUST be ascending exact normalized `resourceId` after selection; an
exact target yields its one entry. Install order remains sorted by site-relative
path.

### Infographic evidence

An infographic reuses the Phase 27 validated site-public `publicPath` and its
selected mapped destination evidence. The WIP operation does not copy an
infographic into the website root. The existing profile/resource mapping and
direct regular non-symlink checks remain mandatory.

### Local-video evidence

A local-video candidate MUST have `kind: video`, nested role `video`, an exact
resource-language match, and a present output. Output resolution MUST use the
existing Cozy media output contract. This specification does not invent or
require a `production.json` artifact path. The resolved output source is a
normalized direct regular non-symlink MP4, and its SHA-256 is captured.

The associated production JSON remains the identity/language/render/QA
authority. Its normalized `category/article` identity MUST equal the explicit
article identity, its language MUST equal the resource language,
`render.status` MUST be `completed`, `render.qa.status` MUST be
`technical-and-visual-qa-passed`, and `render.sha256` MUST equal the source MP4
SHA-256. `render.sha256` MUST be a `JsString` matching exactly
`[0-9a-f]{64}`; missing, null, whitespace, prefixes, uppercase characters,
wrong lengths, non-string values, and digest mismatch fail before mutation.
WIP does not require, read, or serialize a YouTube URL. Any listening
review value remains non-gating evidence only.

### PDF WIP evidence and strict-only reuse

WIP also accepts the two Phase 40 PDF roles, `article_pdf` and
`summary_slides_pdf`, but only as strict reuse candidates. The resolved
resource MUST have `kind: document`, canonical language `ja` or `en`, an exact
site-visible `articleMedia.publicPath`, `mediaType: application/pdf`, and an
optional exact nonblank `label`. The direct output is the resolved output, or
the source only when `build: prebuilt`; it MUST be a current direct regular
non-symlink file. The binding MUST require both current
`cozy.media.receipt.v2` and current `cozy.media.pdf-review-state.v1` evidence
using the unchanged Phase 40 contracts, and MUST repeat that check under the
existing WIP root and registry locks before replacement.

The strict candidate maps `article_pdf` only to `articlePdf` and
`summary_slides_pdf` only to `summarySlidesPdf`, preserving exactly its public
path, `application/pdf`, and optional label. PDF evidence is validation input
only: WIP MUST NOT copy, install, construct a destination or parent, create a
backup or rollback item, mutate the website root, or create a PDF integrity
record. Existing infographic reuse and local-video staging/install/rollback
with its separate video integrity record remain unchanged.

## 6.4 WIP path and public record

Phase 28.1 accepts exactly two normalized article-identity segments,
`<category>/<article>`; every other segment count fails before path
construction. For that identity and locale `<locale>`, the exact site-visible
content URL is:

```text
/<locale>/<category>/videos/<article>.mp4
```

The website destination is that path relative to `website-root`, with the
leading slash removed. Article and locale MUST already satisfy existing
normalization. No raw resource ID or input filename may participate in path
construction.

The public local-video resource is exactly:

```text
presentation: site-hosted
status: published
content_url: /<locale>/<category>/videos/<article>.mp4
```

The record MUST omit `provider`, `watch_url`, and every host path, hash,
provenance, configuration, staging, or other internal-evidence field. Here,
`published` means available in the disposable WIP website tree; it does not
mean YouTube publication, production promotion, or deployment.

The strict record is paired through ordinary correlated role-update semantics
with exactly one separate `cozy.article-media-integrity.v1` video record; it
does not define a `SiteRoleUpdate` exception. That record has
`provenance.kind: wip-site-video` and exactly the provenance fields `kind`,
project-root-relative normalized `descriptor`, exact `resourceId`, and
project-root-relative normalized `production`. `artifact.identity` is exactly
`resourceId`; `artifact.version` is the exact source SHA-256 as an opaque
64-character content-addressed WIP version; `publicPath` equals `content_url`;
`repositoryPath` is the website-root-relative destination; `mediaType` is
`video/mp4`; source, staged, and installed destination `sha256` values are
equal; and `publicationState` is `published` only after installation. For this
provenance only, `repositoryPath` means website-root-relative and `published`
means installed in the disposable WIP tree. WIP infographic behavior remains
the Phase 27 site-visible/no-new-integrity behavior.

Before staging or registry mutation, the command MUST inspect existing exact
`(articleIdentity, locale, video)` strict and integrity state. It is admissible
only when both are absent, OR when the existing pair is canonical after current
full evidence revalidation. A canonical strict video is exactly
`presentation=site-hosted`, `status=published`, absent `provider`, absent
`watch_url`, and the deterministic WIP `content_url`. It MUST have exactly one
canonical integrity record for that normalized tuple: schema
`cozy.article-media-integrity.v1`, role `video`, provenance exactly
`wip-site-video` consisting only of its kind and the exact normalized
descriptor, resource ID, and production; artifact identity equal to the selected resource ID; artifact
version equal to the current source SHA-256; public path equal to that content
URL; repository path equal to the exact site-relative destination; `video/mp4`;
SHA-256 equal to the current source digest; and `publicationState=published`.
The installed destination MUST exist as a direct non-symlink regular file whose
digest equals that current source digest. Any absent, extra, malformed, stale,
or mismatched field, record, or destination MUST fail before staging or
registry mutation. This permits repeat WIP replacement while never overwriting
production; infographic preservation remains Phase 27 behavior. This is
semantic registry-state isolation, not a new marker or root schema.

PDF strict records are independent role replacements in the same owner bundle.
Their receipt, review-state, output, renderer, hash, host-path, and other
internal evidence MUST NOT be serialized. A PDF-only WIP registration changes
only the selected strict registry fields; it does not create website bytes,
integrity correlation, staging artifacts, backups, rollback entries, or
production state.

## 6.5 Two-root transaction and rollback

For non-dry execution, the command MUST acquire direct regular non-symlink
`.cozy-article-media-wip.lock` files in both roots in canonical root-identity
lexical order before acquiring the nested existing publication-registry lock;
it MUST release them in reverse order. The lock files are durable coordination
artifacts, validated before use and excluded from candidate/output state; a
pre-existing invalid lock fails. Dry-run MUST acquire or create no lock and
relies on captured/revalidated read-only evidence.

Complete preflight MUST capture coherent evidence for the descriptor,
discovered project configuration and profile, selected resource mappings,
infographic destination, PDF output and current receipt/review-state evidence,
production JSON, source MP4, publication registry snapshot/root, website root,
and each selected video destination. Immediately before mutation, under both
root locks and the registry lock, the command MUST revalidate every item and
build the canonical correlated strict-plus-integrity registry plan. A stale
descriptor, profile, mapping, PDF evidence, registry snapshot, production
JSON, source MP4, video destination identity/bytes, or root identity fails
before install.

For each selected MP4, it MUST create a same-filesystem sibling temp, copy,
fsync, and verify its digest, then create and verify a sibling backup for each
existing destination. It MUST install video destinations through
same-filesystem atomic replacement in sorted site-relative-path order, validate
installed bytes, replace the single selected owner registry bundle last
through the existing atomic registry operation, validate registry and video
destinations while locked, and remove backups and temps. PDF candidates
contribute no staging or destination mutation. One descriptor replaces exactly
one owner bundle.

After any mutation failure, if registry replacement occurred, rollback MUST
first atomically restore exact original owner-bundle bytes (or remove a newly
created canonical bundle), then restore every pre-existing video destination
and remove every new destination in reverse install order, then remove temps
and backups. It MUST revalidate original registry/root/destination bytes and
identities before reporting failure. Rollback failure is surfaced distinctly;
no application exception or rollback failure may produce a successful partial
state.

## 6.6 WIP root and path safety

Both roots and each exact destination parent MUST already exist as normalized
direct non-symlink directories with stable canonical identity. The command
MUST create neither roots nor parents. It MUST reject this exact predicate:
filesystem root; user home; discovered project root or any ancestor of it;
equal publication/website roots; or either root ancestor/descendant of the
other. A website root may be a descendant of the project root. A publication
root may be an external direct root only when all direct-entry, canonical,
non-symlink, and containment checks pass. It also rejects symlink ancestors or
destinations, path escape, non-regular input, stale SHA, identity drift, and
cross-filesystem staging that cannot provide atomic replacement. It performs no
network, upload, publish, deploy, or site-build operation.

## 6.7 WIP compatibility and phase ownership

Phase 27 `register-site` external-YouTube behavior and standard BoK
repository/publication behavior remain unchanged. Phase 28.2 alone wires
`runweb-wip` and the Part 5 repository fixtures. The Phase 28.1 contract is
normative now; implementation remains downstream and must not be described as
still outside authority. WIP records never promote or spoof production
evidence.

## 6.8 Phase 28.1 executable acceptance matrix

The following rows are executable specifications for S2-B/S2-C. They are
documentation only in this Slice; no Scala tests are added here.

### CLI and candidate selection

| Case | Setup | Expected result |
| --- | --- | --- |
| Exact grammar | One descriptor, two existing roots, optional target/dry-run | Command parses exactly the frozen grammar and reports a deterministic plan. |
| Missing/extra/duplicate/unknown option | Omit either root, add a positional/unknown option, repeat an option, or pass `--profile` | Fail before lock, temp, directory, video, or registry mutation. |
| Equals/separated forms | Exercise existing separated and equals forms; use a leading-dash value once in each form | Existing convention is accepted; leading-dash value is accepted only with equals. |
| Explicit association gate | Omit top-level association, project kind, configured same-ID `site-kind`, or resource opt-in | Zero/unauthorized candidates; fail before mutation. |
| All or exact target | Multiple opted-in resources with and without `--target` | No target selects all opted-in resources in ascending exact normalized `resourceId` plan/output order; target selects its one exact existing opted-in resource. |
| Zero/invalid/ambiguous/duplicate | No candidates, malformed block, duplicate normalized locale/role, or ambiguous target | Fail before mutation. |
| Exact locale | JA and EN resources plus an absent locale or opposite-locale output | Exact locale succeeds only; no fallback or opposite-locale substitution. |

### Candidate and media evidence

| Case | Setup | Expected result |
| --- | --- | --- |
| Infographic reuse | Valid Phase 27 site-public `publicPath` and mapped destination | Record reuses the evidence; no infographic is copied into website root. |
| Video kind/role/language/output | Resource is not `kind: video`, role is not `video`, language differs, or output is absent | Candidate fails before mutation. |
| Existing output contract | Output resolves through the existing Cozy media output contract | Source MP4 is selected without inventing a `production.json` artifact path. |
| `render.sha256` grammar | Missing/null/non-string, uppercase, prefix, whitespace, wrong length, or source mismatch | Fail before mutation; only exact lowercase 64-character `JsString` is accepted. |
| MP4 hardening | MP4 is non-regular, symlinked, unnormalized, stale, or SHA differs from production `render.sha256` | Fail before mutation. |
| Production gate | Production JSON has identity/language mismatch, incomplete render, or failed technical/visual QA | Fail before mutation. |
| YouTube/listening exclusion | YouTube is absent or listening review is pending | WIP remains eligible; neither is read/serialized as a requirement. |
| PDF acceptance boundary | `article_pdf` or `summary_slides_pdf` has document kind, canonical JA/EN, exact public path, `application/pdf`, optional exact label, direct current output, current receipt, and current PDF review state | Candidate is admitted as strict reuse only; no PDF integrity record is created. |
| PDF evidence failure | PDF output is missing/stale, receipt or review-state evidence is missing/stale, role/kind/media type/locale is incompatible, or public path/label is non-exact | Fail before registry or website mutation. |

### Path and serialization

| Case | Setup | Expected result |
| --- | --- | --- |
| Deterministic path | Identity `development-process/object-modeling`, locale `ja` | Content URL is `/ja/development-process/videos/object-modeling.mp4`; destination removes only the leading slash. |
| Identity decomposition | One, three, or more normalized identity segments | Fail before path construction; WIP accepts exactly `<category>/<article>`. |
| Unsafe identity/locale | Raw or unnormalized identity/locale | Fail; no resource ID or filename fallback. |
| Public record | Successful local-video registration | Exactly `presentation=site-hosted`, `status=published`, `content_url`; no provider/watch URL/host path/hash/provenance. |
| PDF public record | Successful PDF registration | Exact `article_pdf -> articlePdf` or `summary_slides_pdf -> summarySlidesPdf` with public path, `application/pdf`, and optional label only; no evidence fields. |
| PDF reuse exclusion | PDF-only WIP registration | No website output, copy/install, destination/parent, backup/rollback item, or PDF integrity entry is created. |
| Published meaning | Inspect WIP record and disposable tree | Published denotes disposable-tree availability, not YouTube or production promotion. |
| Integrity correlation | Successful WIP video and infographic registration | Video emits the separate correlated `wip-site-video` integrity record; infographic remains Phase 27 site-visible with no new integrity. |
| Exact video state: fresh | Both exact video strict/integrity records absent | WIP may stage and register its correlated pair. |
| Exact video state: repeat WIP | Canonical strict record is only site-hosted/published with absent provider/watch URL and deterministic WIP URL; exactly one v1/video `wip-site-video` integrity has current descriptor/resourceId/production, resource ID/current-source-SHA identity/version/digest, exact public/repository paths, `video/mp4`, and published state; installed direct non-symlink destination digest matches current source | WIP may replace its own exact pair only after current full evidence revalidation. |
| Exact video state: production | Existing external-link/watch URL, or site-hosted production state with `video-publication` integrity | Fail before staging or registry mutation; WIP never overwrites production. |
| Exact video state: malformed/mixed | Any absent/extra/malformed/stale/mismatched strict or integrity field, record, or installed destination digest, including a non-canonical status/provider/watch URL/path/provenance/artifact/state | Fail before staging or registry mutation. |

### Two-root safety and atomic rollback/drift

| Case | Setup | Expected result |
| --- | --- | --- |
| Rejected-root predicate | Filesystem root, user home, discovered project root/ancestor, equal roots, or publication/website ancestor-descendant overlap | Fail; website descendant of project is permitted; an external direct publication root is permitted only after all exact safety checks. |
| Destination-parent prerequisite | Website root or one exact destination parent is missing, aliased, or symlinked | Fail; command creates no parent. |
| Destination safety | Symlink ancestor/destination, escape, or non-atomic cross-filesystem staging | Fail before install. |
| Lock order | Cross-paired or swapped publication/website roots | Non-dry locks both `.cozy-article-media-wip.lock` files by canonical identity, then registry lock; release is reverse. |
| Coherent preflight | Change descriptor/config/profile/mapping/production/source/registry/root after preflight and before commit | Immediate revalidation detects drift; no mutation. |
| Atomic success | Multiple selected videos and registry replacement | Copy/fsync/verify temps, back up existing destinations, install sorted destinations, validate bytes, replace owner bundle last, validate while locked, then clean up. |
| Rollback | Inject install or registry replacement failure | Restore registry first when replaced, restore/remove destinations in reverse install order, then remove temps/backups and revalidate original bytes/identities; surface rollback failure distinctly. |

### Dry-run, repeatability, and exclusions

| Case | Setup | Expected result |
| --- | --- | --- |
| Dry-run parity | Run dry and non-dry with identical stable evidence | Plans are byte-for-byte deterministic; dry-run creates no lock/temp/directory/video/registry bytes. |
| Repeatability | Repeat successful planning/registration against unchanged evidence | Same ascending normalized-resourceId candidate/plan order, paths, records, and registry result. |
| Production exclusion | Production record has accepted YouTube evidence or pending listening | Phase 27 production behavior is unchanged; WIP does not rewrite or promote it. |
| Standard BoK exclusion | Standard BoK media/profile data without the four WIP selectors | Normal repository/publication path remains unchanged; no WIP registry or website mutation. |
| Network/build exclusion | Observe command execution | No network, upload, publish, deploy, site build, or runweb operation occurs. |

## 7. Diagnostics and public serialization

`cozy media plan`, `cozy media inspect`, and `cozy video inspect` diagnostics
MUST include, when a project context is discovered:

- the normalized project root;
- the selected project configuration marker;
- the effective configuration layer and source for each selected profile;
- the selected publication-profile ID;
- the selected credit-profile ID; and
- the configured site kind.

When no project marker exists, diagnostics MUST state that the legacy
package/standalone context is being used. Unknown-profile diagnostics MUST
include all searched layers as specified in Section 3.3. These diagnostics are
operator-facing and may contain host paths. Public SmartDox records MUST NOT
contain those paths, hashes, or provenance values.

## 8. Security and negative guarantees

The implementation MUST satisfy all of the following:

- follow no symlink while discovering project roots, marker components,
  profile directories, or selected roots;
- require direct regular marker/configuration files and direct directories
  where the relevant layer contract requires them;
- normalize paths and enforce the required lexical and canonical-real
  containment against the selected boundary; only an already-supported
  explicit `rootEnv`/external-root selection may be outside project
  containment, and it MUST still satisfy direct-entry, non-symlink, normalized,
  canonical-real, and boundary checks;
- fail closed for unsafe, ambiguous, missing-required, ordinary, or otherwise
  escaping roots;
- stop ancestor discovery at the filesystem root;
- make no Git dependency;
- make no root-selection or Docker-mount change beyond the exact WIP predicate
  in Section 6.6;
- scan no target tree, generated site, arbitrary repository, or opposite
  locale;
- provide no locale fallback;
- perform no automatic registration;
- perform no network, upload, deployment, or site-build operation; and
- preserve existing Phase 27 atomicity and revalidation rather than weakening
  them through configuration discovery.

## 9. Compatibility matrix

| Compatibility surface | Required behavior |
| --- | --- |
| Standard Cozy BoK build/publication | Unchanged; standard BoKs do not route through `smartdox-site`. |
| Standalone media package with descriptor-local profiles | Continues to resolve descriptor-relative profiles without a project marker. |
| Package-local configuration media profiles | Continue to override project definitions by exact ID; descriptor-local overlays remain location-only. |
| Package-local video credit profiles | Continue to resolve from package `video/credit-profiles`. |
| Explicit video `credits.profile` | Wins over defaults and controls attribution only. |
| Existing command syntax (`media build`, `publish`, `verify`, `register-site`) | Unchanged. |
| Existing host and Docker video argv | Unchanged. |
| Provider-neutral Phase 27 registry schema | Unchanged; no internal provenance fields are added. |
| Phase 27 atomicity and evidence revalidation | Unchanged and still mandatory. |
| Project-local `.cozy` | Remains an untracked layer with the exact precedence in Section 3.1. |
| Missing project marker | Legacy package/standalone behavior is preserved. |

## 10. Executable acceptance matrix

The following cases are the executable acceptance contract for the Phase 28
configuration and Phase 28.1 WIP implementations. They are deliberately expressed as behavior cases;
this documentation-only Slice does not create Scala tests.

### 10.1 Project discovery and layering

| Case | Fixture/setup | Expected result |
| --- | --- | --- |
| Nearest marker | A descriptor nested beneath two direct `conf/cozy/config.yaml` markers | The nearest valid marker supplies the project root and provenance. |
| Filesystem-root termination | No marker exists through the filesystem root | Discovery reports absence and preserves legacy package/standalone behavior. |
| Symlinked ancestor | A traversed directory is a symlink | Discovery fails closed; it does not follow or select another root. |
| Symlinked marker | `conf`, `cozy`, or `config.yaml` is a symlink | Discovery fails closed. |
| Ambiguous/unsafe path | Normalization or containment cannot produce one safe root | Discovery fails closed. |
| No Git dependency | Fixture is copied without `.git` metadata | The same marker and context are selected. |
| Exact layer override | Same profile ID appears in project and package configuration layers | Package configuration supplies the effective site kind/location; source layer is reported. |
| Full precedence | Definitions/defaults exist at built-in, user, project `conf`, project `.cozy`, package `conf`, package `.cozy`, and explicit selection layers | Effective values follow the exact order in Section 3.1. |
| Duplicate one-layer ID | One layer defines one profile ID twice | The layer fails before selection; no filesystem order chooses a value. |
| Unknown profile | Requested profile is absent | Diagnostic names the ID and enumerates every searched layer and provenance. |
| Root bases | Project profile root is `.` and descriptor profile root is a relative path | Project root and descriptor root are used respectively; they are not conflated. |
| Existing external root | An already-supported explicit `rootEnv`/external-root selection is present | It may lie outside project containment only under that existing contract, and still passes direct-entry, non-symlink, normalized, canonical-real, and boundary checks; absent explicit support is not inferred. |
| Ordinary escaping root | A normal `root` or unsupported external-root selection escapes project containment | Resolution fails closed. |

### 10.2 Video credits

| Case | Fixture/setup | Expected result |
| --- | --- | --- |
| Explicit credit | Video has `credits.profile` and a different configured default | Explicit profile wins. |
| Project default | Video omits `credits.profile`; project config has `video.credits.default-profile` | Default selects the named profile with project provenance. |
| Package override | Package configuration profile and project profile share an ID | Package configuration profile wins and its source is reported. |
| Standalone compatibility | No project marker; package has a credit profile | Package-local profile resolves as before. |
| Credit/site separation | Only `credits.profile: simplemodeling-org` is present | Credits may resolve; SmartDox registration remains unauthorized. |
| Credit duplicate/unknown | Duplicate ID in one layer or unknown requested ID | Duplicate fails; unknown diagnostics enumerate searched layers. |

### 10.3 Media publication profiles

| Case | Fixture/setup | Expected result |
| --- | --- | --- |
| Project profile | Project config defines `media.publication-profiles.simplemodeling-org` | Nested package can resolve the profile using project-root-relative `root`. |
| Descriptor override | Configuration defines the same ID with `site-kind: smartdox`; descriptor `profiles` defines location fields for that ID | Effective profile retains configured site kind and provenance while overlaying only descriptor-supported `root`/`rootEnv` location fields. |
| Descriptor-only compatibility | No configuration profile exists; descriptor `profiles` defines an ID | Descriptor-relative publication/standalone resolution remains valid, but SmartDox registration is unauthorized because no configured site kind exists. |
| Missing/wrong configured site kind | The selected ID has no configured site kind or has a value other than `smartdox` | SmartDox registration is rejected even when descriptor location fields and association are present. |
| Explicit association | Top-level `articleMedia.publicationProfile` names an effective profile | Selection succeeds only if profile and resource mapping validate. |
| No default | Top-level association omits `publicationProfile` | Registration is rejected; no default is supplied. |
| Resource opt-in | One resource lacks its own `articleMedia` block | That resource is not a candidate; the project is not auto-registered. |
| Mapping mismatch | Resource has no matching publication mapping | Candidate validation fails before mutation. |
| Profile alone | Profile exists but top-level association is absent | No registration. |

### 10.4 SmartDox boundary and evidence

| Case | Fixture/setup | Expected result |
| --- | --- | --- |
| Standard BoK | A normal BoK has media/profile data but no explicit site association | Normal BoK path remains unchanged; no SmartDox registration. |
| Source type only | `knowledge.source-type: smartdox` without association | No registration. |
| Path/name only | Paths or repository name resemble `simplemodeling-org` | No registration. |
| Explicit complete boundary | Explicit top-level association, discovered `smartdox-site`, same-ID effective configuration profile with `site-kind: smartdox`, and resource opt-in all exist | Candidate may proceed to Phase 27 validation; any descriptor-local overlay contributes location fields only. |
| Production gate | Video evidence has completed render, technical-and-visual QA, published YouTube URL | Production eligibility is retained. |
| Listening pending | `render.listeningReview` is pending while production prerequisites pass | Eligibility remains; pending listening is evidence-only and non-gating. |
| Production incomplete | Technical/visual QA or published YouTube evidence is absent | Production registration is rejected/omitted under Phase 27 policy. |
| WIP video | Local MP4 has no YouTube URL | It is admitted only by the normative Phase 28.1 WIP contract in Sections 6.2–6.8; Phase 28.2 supplies integration only. |
| Public serialization | Effective context has host paths, hashes, and provenance | Diagnostics may show them; SmartDox public records do not. |

### 10.5 SimpleModeling.org Part 5 fixture

The required cross-repository fixture is the
`development-process/object-modeling` package in the SimpleModeling.org
fixture repository. Relative fixture paths are:

```text
src/main/doxsite/development-process/object-modeling.dox
src/main/media/development-process/object-modeling/media.yaml
src/main/media/development-process/object-modeling/video-ja.yaml
src/main/media/development-process/object-modeling/video-en.yaml
```

The fixture cases MUST cover:

1. discovery of the repository marker `conf/cozy/config.yaml` from the deeply
   nested media and video descriptors, without Git;
2. project-root-relative resolution of the shared
   `simplemodeling-org` publication profile and shared credit profile;
3. explicit `credits.profile` in the JA and EN video descriptors, with credit
   provenance reported independently of registration;
4. field-aware descriptor-local media profile overlay (location-only
   `root`/`rootEnv`) over configured profile site-kind/provenance, plus explicit
   top-level and resource-level `articleMedia` requirements;
5. rejection when only `source-type: smartdox`, a profile definition, a path,
   a repository name, or `credits.profile` is present;
6. acceptance only when explicit article association, discovered
   `project.kind: smartdox-site`, same-ID effective configuration publication
   profile with `site-kind: smartdox`, and resource opt-in are all present;
7. Phase 27 production evidence behavior for the JA and EN production records,
   including technical/visual QA, published YouTube, and pending listening as
   non-gating evidence;
8. exact-locale behavior with no opposite-locale fallback; and
9. diagnostics containing project root/configuration, effective layer/source,
   selected publication and credit profiles, and site kind while generated
   provider-neutral records contain none of that internal provenance.

The Phase 28.1 WIP staging and local registry mutation contract is normative in
Sections 6.2–6.8; its product implementation is downstream Phase 28.1 work.
`runweb-wip` wiring and rendered-introduction-card acceptance remain Phase
28.2 work and MUST not be pulled into Phase 28 configuration implementation.

## 11. Non-goals and stop boundary

This contract does not authorize:

- Scala/product/test/config changes in this documentation Slice;
- implementation of WIP artifact staging or provider-neutral registry mutation
  in this documentation Slice (the normative contract is Sections 6.2–6.8);
- `runweb-wip` integration or Part 5 migration;
- additional lifecycle, ledger, README, strategy, or journal changes outside
  the separately frozen Phase 28 workflow; current admitted accumulator
  convergence is governed by Phase 28;
- direct SmartDox registry edits, target/generated scans, or inferred
  registration;
- network, upload, deploy, SBT, Cozy/Dox/runtime commands, or generated
  artifacts; or
- WIP-to-production evidence/state promotion.

Phase 28 closes before Phase 28.1 implementation starts, and Phase 28.2 starts
only after Phase 28.1 closes. Any behavior requiring another schema field,
public API, repository, or unresolved authority decision is outside this
contract and requires a separately frozen change.

## 2026-10-09 Phase 71.5 shared-context input amendment

Authority: [Phase 71.5](../phase/phase-71.5.md),
`P715-02-S13-CONTEXT-DESCRIPTOR-READ`. Each selected configuration file is
read once after existing initial direct-file and lexical/real-path admission.
The initial project-marker read is reused by its configuration layer. There
is no between-read interference guarantee or whole-context revalidation
contract. This bounded amendment supersedes only those input protection
promises; the earlier accepted contract remains recorded above.

Nearest lexical marker discovery, the exact six supported configuration
candidates and their order, native parsing, profile grammar, layer precedence,
source provenance, selected-root containment, and ordinary read/parse failures
remain. Shared JVM `user.home` coordination also remains.

Original Context directory/file metadata and initial stable-key admission
remain unfinished coupled removal work under finite `R715-12/14`, together
with WIP/SiteBinding/transaction protection. S13 does not close Context or
globally rewrite those still-unreconciled contracts. Existing retained behavior
is specified by [CozyProjectContextSpec](../../src/test/scala/cozy/config/CozyProjectContextSpec.scala).
The [S13 journal](../journal/2026/10/2026-10-09-phase-71.5-context-descriptor-read.md)
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
