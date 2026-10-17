# Component Repository Publication

## Purpose

Cozy publishes the CNCF Component Repository discovery index together with the
existing detailed CAR and SAR catalogs. The canonical public resource is:

```text
repository/catalog/index.json
```

The index is a discovery summary. Detailed versions and runtime requirements
remain in `repository/catalog/car/<artifact>.yaml` and
`repository/catalog/sar/<artifact>.yaml`.

## Publication Contract

`cozy publish-car` and `cozy publish-sar` load and validate an existing index
before changing it. Publication replaces only the entry identified by
`(kind, artifactId)` and preserves every unrelated CAR and SAR entry. An empty
release catalog removes that identity from the index.

Entries are ordered by their canonical identity. Publication reads the existing
index and detailed catalogs, checks the project catalog, resolves the selected
archive and CAR sidecars, and constructs the updated catalog, index and Maven
metadata before writing final output. A prebuilt CAR and required generation
provenance are admitted from their real selected paths; publication does not
create snapshot inputs. Generated temporary CARs remain ordinary build products
and are cleaned up after publication.

Cozy copies the selected archive directly to its final path with overwrite.
It writes or deletes the source catalog
unless local publication owns only the warehouse catalog, then the public catalog
and Maven metadata. CAR CML, JSON metadata, optional YAML metadata and declared
knowledge sidecar follow in that order. Empty catalogs remove their discovery
identity. The updated actual detailed catalogs and archive paths are validated
before UTF-8 JSON is written directly to `index.json` last.

This order is ordinary output sequencing. It provides no transaction, atomic
visibility, lost-update protection or preservation of prior output. There are
no index locks, candidate overlays, backups or rollback. An I/O or later catalog
validation failure can leave partially written final output and is reported as
failure; recovery is regeneration. Generated build-archive cleanup preserves a
primary publication error, attaching cleanup errors as suppressed errors, or
reports cleanup failure after otherwise successful output.

Before the updated index is written, Cozy verifies each entry against its
detailed catalog:

- kind and artifact identity agree;
- the catalog path is a safe canonical relative path;
- lifecycle status agrees;
- recommended, latest stable, and latest snapshot selectors agree.
- CAR coordinates and namespace/id agree, and the selected archive is a regular
  file;
- SAR versions are single path segments and select their canonical contained
  regular archive paths.

Index validation reads actual catalog paths and checks selected archive presence.
Catalog versions carry exactly `version`, `channel`, `status`, `component`,
`publishedAt`, `file` and `runtime`. Catalog parsing, rendering and CAR admission
use these semantic fields; repository publication emits no archive SHA sidecar.
Canonical CAR version shapes retain ordinary unknown-field rejection. Shared
carrier/runtime/subcomponent contracts and external **E715-01..05** reconciliation
remain OPEN, including their actual required SHA values and existing helper.

An invalid existing index fails publication instead of being silently repaired
or replaced. Publication does not crawl the repository to reconstruct a
missing index.

## Local Validation

Use the network-free repository lint command against a warehouse root:

```sh
cozy lint repository <warehouse-root>
cozy lint repository <warehouse-root> --format json
```

The command validates `repository/catalog/index.json` and every referenced
detailed catalog. A missing index is a failure for this explicit lint command.
Known-artifact catalog resolution without an index remains a consumer
compatibility behavior; it does not make global discovery available.

When a CAR project contains `repository/catalog/index.json`, integrated
`cozy lint car` includes the same repository consistency check.

## S26 implementation status — 2026-10-09

P715-02-S26-REPOSITORY-PUBLICATION-DIRECT is
**implemented-focused-validated-unreviewed** under the
[S26 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s26-implementation-manifest.json).
The initial editing handoff on 2026-10-09 was
**implemented-unvalidated-unreviewed**. Actual focused validation is recorded
below; independent review and acceptance remain OPEN. Existing index, CAR/SAR publisher and
namespace publication specifications remain the selected behavior documentation;
protection-only candidate, tamper and recovery scenarios are removed. Independent Step/full-Phase assurance and acceptance remain OPEN.

### Actual S26 focused validation — 2026-10-09

The [actual S26 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s26-focused-validation-result.json), following the
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s26-parent-edit-admission.json), records **74 successes in 6 completed suites** for
**P715-02-S26-REPOSITORY-PUBLICATION-DIRECT**. The first sequential group passed
**35 tests in 3 suites** (`cozy.CozyCarPublisherSpec`, `cozy.CozySarPublisherSpec`
and `cozy.archive.Phase56NamespaceQualifiedCarPublicationSpec`); the second passed
**39 tests in 3 suites** (`cozy.archive.ComponentRepositoryIndexSpec`,
`cozy.RepositoryArtifactCatalogSpec` and `cozy.bok.CozyBokComponentRepositorySpec`).
Failed, canceled, ignored, pending and aborted counts are **zero**. The two
`testOnly` tasks included their normal implicit Main/Test compilation. Compiler
warning/error lines, runtime warning/error lines and fatal fixture diagnostics
are **zero for this invocation**. Existing Hygiene and prior results retain their
attribution; no suppression or whole-Phase warning-free claim is made.

Registered serial invocation `4d8e25f1-085d-44a1-b33b-5a2c1ca7a4d3` completed
with terminal **SBT/wrapper exits 0/0** and the **shared serial lock released**.
These accepted focused results establish the selected existing specification
behavior. Independent review and final acceptance remain OPEN.

The parent assessment limits S26 to direct repository publication/index/migration
and the CAR compatibility caller. No narration, frame, synthesis, render,
assembly, audio, credit or end-card producer changed. Prior S14 native results
and **S25's 93 successes in 3 suites** remain dated and attributed; they do not
establish acceptance of the changed pipeline or current audible hearing. Final
current **JA34 native generation/assembly/audible hearing/end-card/EOF and
whole-Phase route acceptance remain OPEN**.

Original **R715-10 catalog model/hash/integrityKey/checksum/SHA sidecar emission
remains unfinished**. Shared carrier/runtime shapes and **E715-01..05 remain
OPEN**, without removal closure or compatibility claims. Other finite duties,
independent **M2 cycles 6/7/8/10**, whole-Step/full-Phase review and acceptance,
exact local Step commits and the distinct release remain **OPEN**.
Repository-full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. Aggregate metadata, Stage Status triples, closure checkboxes,
original row numbering and prior refusal/review/native evidence retain their
state. This factual projection runs no new validation and performs no review,
workflow transition, staging or commit; no Slice, Step or Phase acceptance is
claimed.


## S27 repository catalog without digests — 2026-10-09

P715-02-S27-REPOSITORY-CATALOG-WITHOUT-DIGESTS is currently
**implemented-focused-validated-unreviewed** under the
[S27 frozen manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s27-implementation-manifest.json).
The initial 2026-10-09 implementation handoff was
**implemented-unvalidated-unreviewed**; that dated status remains history.
The catalog model, public facade, YAML/JSON projections, renderer and CAR
admission now use exactly `version`, `channel`, `status`, `component`,
`publishedAt`, `file` and `runtime`. Canonical CAR allowed fields omit the retired
checksum/integrity fields; generic SAR/legacy parsing remains generic. CAR/SAR
publication and mapped migration copy archives directly and emit no repository
SHA sidecars. Ordinary coordinates, schema/runtime, selectors, status, selected
paths, source/sidecar preflight, metadata/index-last sequencing, generated-build
cleanup and I/O failure behavior retain their meanings. Existing unselected
sidecar files are not removed.

The six existing Catalog, CAR/SAR publisher, ComponentRepositoryIndex, Phase56
namespace publication and BoK component-repository specifications retain their
ordinary behavior clauses. Only retired digest/sidecar clauses and fixtures are
removed; no whole case or suite is added or deleted. The existing SAR varied
payload property retains at least fifty publication trials. The initial
2026-10-09 handoff left parent-selected six-suite focused validation, diff
admission and independent review pending. Actual S27 focused validation and
parent edit admission are recorded below; independent review remains OPEN.
S26's actual 74 successes in 6 suites remain dated S26 evidence.

`RepositoryArtifactPublisher.sha256` remains because the four actual
`SubcomponentReleasePackaging` call sites and required Component knowledge
carrier declaration fixtures still use real SHA values. This is unfinished
original **R715-09/11 and E715-01/03/04** work, not an accepted protection
exception or compatibility adapter. Required carrier/runtime/descriptor/
subcomponent/review contracts and all **E715-01..05 remain OPEN**; no external
repository or unsupported producer-only contract is changed.

This catalog distribution change edits no narration, frame, synthesis, render,
assembly, audio, credit or end-card producer. Final current **JA34 generation,
assembly, audible hearing, end-card and EOF** obligations remain OPEN, as do
other original finite work, independent **M2 cycles 6/7/8/10**, whole-Step and
full-Phase review/acceptance, exact local Step commits and the distinct release.
Repository-full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. Original authority/base/epoch, repair counts, aggregate metadata,
Stage Status triples, closure checkboxes, row numbering and prior refusal,
review and native evidence retain their state. No Slice, Step or Phase acceptance
is claimed.

### Actual S27 focused validation — 2026-10-09

P715-02-S27-REPOSITORY-CATALOG-WITHOUT-DIGESTS is currently
**implemented-focused-validated-unreviewed**. The
[actual S27 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s27-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s27-parent-edit-admission.json)
record **74 successes across six completed suites**: the first sequential group
had 35 successes in three suites and the second had 39 in three suites. Failed,
canceled, ignored and pending tests and aborted suites were all zero. Main and
Test compilation was implicit in this invocation; compiler warnings and errors,
fatal fixture diagnostics and runtime warning lines were zero for this invocation.
This records the selected invocation only; whole-Phase warning disposition remains
subject to independent assurance.

Serial invocation `e0fe6161-f0bf-48b1-996a-d592d8a75388` completed with SBT and
wrapper exit codes **0/0**, and the shared serial lock was released. The selected
**R715-10 catalog model/hash/integrityKey/checksum and repository SHA sidecar
emission** removal is focused-validated-unreviewed. Whole R715-10 and Phase closure
remain OPEN. The required real `RepositoryArtifactPublisher.sha256` helper,
carrier/runtime/descriptor/subcomponent/review contracts, remaining original
finite work and **E715-01..05** remain OPEN.

Parent edit admission is source inspection, not independent review or acceptance.
Independent **M2 cycles 6/7/8/10**, whole-Step and full-Phase review/acceptance,
exact local Step commits and the distinct release remain OPEN. Seven catalog and
distribution owners changed; no narration, frame, synthesis, render, assembly,
audio, credit or end-card producer changed. Prior native evidence remains dated;
final current **JA34 native generation, assembly, audible hearing, end-card and
EOF** remain OPEN. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. Prior S26's actual 74 successes in six suites,
initial S27 handoff, refusal/review/native history and original stage and aggregate
metadata retain their dated attribution and state. No Slice, Step or Phase
acceptance is claimed.

## S31 subcomponent release direct output amendment — 2026-10-09

**P715-02-S31-SUBCOMPONENT-RELEASE-DIRECT** is currently
**implemented-focused-validated-unreviewed**. Its initial 2026-10-09 editing
handoff was **implemented-unvalidated-unreviewed**; actual focused validation
and parent edit admission are recorded below. Independent review and acceptance
remain OPEN.

Under [P715-02-S31-SUBCOMPONENT-RELEASE-DIRECT](../../.codex-workflow/phases/PHASE-71.5/p715-02-s31-implementation-manifest.json),
Cozy's own `cozy.subcomponent-release.v1` manifest has exactly
`schemaVersion`, `publishedAt`, `parent`, `children` and `composition`.
Parent and child references have exactly `archivePath`, `canonicalId` and
`coordinate`; coordinates retain `namespace`, `id` and `version`. The
composition reference has exactly `archivePath`, `schema` and `membershipKind`.
These references describe selected semantic payloads and paths. Own release,
payload and composition references carry no local SHA/signature proof, and
there is no release-integrity document or admission marker protocol.

`package-subcomponent-release` and `publish-subcomponent-release` no longer
accept `--integrity`; no ignored flag, compatibility alias or schema-version
replacement is introduced. Packaging admits selected CAR/ABI inputs, project
coordinates, required/optional membership and canonical composition, then
writes the semantic manifest, unchanged composition bytes and actual CAR
payloads directly to the selected final archive. Sorted stored ZIP entries
retain standard CRC, size and time fields. Publication reads the selected
release once, parses its manifest, composition and payloads, checks project
coordinates and canonical `publishedAt`, and writes its bytes directly with
overwrite to:

```text
repository/subcomponent-release/<groupPath>/<mavenArtifactId>/<version>/<mavenArtifactId>-<version>.release.car
```

Source/output nonoverlap, safe archive paths, exact path sets, duplicate JSON
field rejection, coordinate/ABI/schema parsing, composition classification,
roles, logical resources, runtime/technology and provenance meanings remain.
No ordinary CAR/SAR catalog, discovery index or runtime activation changes.
There is no local hash calculation/comparison, post-output reread, lock, CAS,
protected install, temporary staging, snapshot, backup, rollback or prior-output
preservation guarantee. Actual I/O failures propagate as operation failures;
partial final output requires regeneration. Existing unselected legacy files
and authored inputs are not swept or deleted.

The copied RSC02 `cncf.component-subcomponent-composition.v1` contract remains
unchanged, including required supplied artifact `sha256` and `signature`
fields and their format parsing. Those fields are not compared with local CAR
payload bytes. Actual external CNCF
`ComponentSubcomponentComposition.scala` still requires `coordinate`,
`sha256`, `signature`, `repository` and `physicalPath`. This is unfinished
**E715-04**, requiring separately authorized consumer reconciliation, not a
compatibility exception or removal closure. All **E715-01..05 remain OPEN**.

The [existing release Executable Specification](../../src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala)
retains seven semantic scenarios and real invalid membership/composition/
release-manifest variants. Only the two own integrity/admission protocol cases
are retired. The deterministic case retains repeated actual package equality,
independent semantic and nested payload expectations, and adds fifty generated
child-input permutation trials through the actual command, checking canonical
ZIP order and payload content. At the initial 2026-10-09
**implemented-unvalidated-unreviewed** handoff, selected three-suite validation,
parent edit admission and independent review were pending. Actual validation
and parent edit admission are now recorded below; independent review remains
OPEN. S30's actual **49 successes in
four suites** remain S30 evidence. No Slice, Step or Phase acceptance is claimed.

### Actual S31 focused validation — 2026-10-09

**P715-02-S31-SUBCOMPONENT-RELEASE-DIRECT** is currently
**implemented-focused-validated-unreviewed**. The
[actual S31 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s31-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s31-parent-edit-admission.json)
record **27 successes across three completed suites**. The first sequential
`testOnly` group completed **7 successes in one suite**,
`cozy.archive.SubcomponentReleasePackagingSpec`; the second completed
**20 successes in two suites**, `cozy.archive.CozyCarRuntimeManifestSpec` and
`cozy.runtime.CozySbtBridgeMultiProvenanceSpec`. Failed, canceled, ignored and
pending tests and aborted suites were all **zero**. Normal implicit Main/Test
compilation compiled **two main Scala sources and one Test Scala source**.
Compiler warnings and errors, runtime Logback warnings, unclassified runtime
warnings, errors and fatal fixture diagnostics were all **zero for this
invocation**. This is a result for the selected invocation, not a whole-Phase
warning-free claim; existing warning dispositions retain their scope.

Serial invocation `7eb79b82-c90d-464a-bc5f-a009e6a74341` completed with SBT and
wrapper exit codes **0/0**, and the shared serial lock was **released**.
S31's local direct package/warehouse output and semantic release contract are
focused-validated only. The initial **implemented-unvalidated-unreviewed**
editing handoff and its pending-suite record remain dated 2026-10-09 history.
Parent edit admission records source inspection; it is not independent review
or Step/Phase acceptance. S30's actual **49 successes in four completed suites**
retain their separate S30 attribution.

Original **R715-11** remains unfinished at the required copied external RSC02
composition contract: actual required supplied SHA/signature fields and format
parsing remain unchanged, with **E715-04** and all **E715-01..05 OPEN**.
Other original finite/runtime/carrier/descriptor/review and external-consumer
obligations remain OPEN. There is no external reconciliation, compatibility
exception, schema bump, fake digest or producer-only removal closure.

The supplied native assessment covers only local subcomponent package and
warehouse output, with no JA34 narration, synthesis, render, assembly or credit
producer change. Final current **JA34 generation, assembly, audible hearing,
end-card and EOF** remain **OPEN**. Independent **M2 cycles 6/7/8/10**,
whole-Step/full-Phase reviews and acceptance, exact local Step commits and the
distinct release remain **OPEN**. Repository-full SBT and broader integration
remain **deferred-not-run to PHASE-71.7**.

Original base/authority/epoch, repair counters, finite rows, aggregate metadata,
Stage Status triples, closure checkboxes and all prior refusal/review/repair/
native history retain their state. This factual projection runs no new
validation and performs no self-review, staging, commit or workflow transition.
No Slice, Step, Phase, native or external-consumer acceptance is claimed.

## S48 composition/resource protection removal — 2026-10-10

P715-02-S48-COMPOSITION-RESOURCE-PROTECTION is
**implemented-unvalidated-unreviewed** in exactly 36 frozen paths, including
23 Scala files, with Cozy and the user-admitted CNCF participant as the two
mutation roots. Original Phase 71.5 artifact-protection removal and the actual
external-libraries direction 「外部ライブラリも修正して」 govern this finite E715-04
change; the completed S43 informed five-file approval is retained history.

Composition artifact SHA/signature fields and their Cozy/CNCF parser, codec,
resource provenance, authorization request, lifecycle key/diagnostic, projection,
and direct callers are removed together. Explicit role/repository grants,
parent/release/member identity, coordinate agreement, required payloads, safe
paths/archive ambiguity, ordinary I/O failures, non-disclosure, and operational
lifecycle ownership/concurrency remain. Independently authored knowledge
entry SHA-256/matchingDigest format and same-document consistency remain; only
the resource-artifact SHA coupling is removed. S45 remains
parent-classified-unreviewed. Existing affected GWT/should specifications are
adapted, with sole-protection cases deleted and mixed ordinary cases retained.

Current E715-04 implementation is **implemented-unvalidated-unreviewed**; whole
E715-04 remains **OPEN** for actual connected/cross-binary admission and the
original accumulated review obligations. E715-05 target.digest, coordinator
requestDigest cache, report/attestation/baseline and other actual protection
consumers remain **OPEN**. S43 approval/refusals and 39 passes, S44/S45 purpose
evidence, S46 actual 59 passes, S47 actual 27 suites/95 passes, native bounded
guard/EOF corrections, and seven execution-owner ledger records remain history.
No S48 tests, runtime media, review, acceptance, Step/Phase closure, or commits
are claimed. Original independent Step/full-Phase reviews, exact local Step
commits, distinct release, warnings, and pending M2 cycles 6/7/8/10/11/13 remain
**OPEN**. Full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. Original Goal, four bases, authority/binding, PLAN epoch, review
ledger revision 5, repair counters 14 / 0, and seven stage statuses are unchanged.

The single scoped uv-managed external edit request/raw result is retained at
`/private/tmp/p715-02-s48-composition-resource-native-evidence.json`; the
manifest-owned external-contract reconciliation journal records its actual
outcome. Parent owns diff verification, focused validation, review, and
transitions.

## S48 completed focused validation — 2026-10-10

S48 is **implemented-focused-validated-unreviewed** in the original exact
36 paths / 23 Scala files / two mutation roots (Cozy and CNCF). The scoped edit
chunk `028377` exited **0**; its actual request/result remains at
[/private/tmp/p715-02-s48-composition-resource-native-evidence.json](/private/tmp/p715-02-s48-composition-resource-native-evidence.json).
Explicit role/repository grants, coordinates and parent/release/member identity,
runtime/ABI/required files, safe paths/archive ambiguity, ordinary I/O,
non-disclosure and lifecycle ownership/concurrency/cancellation remain.
Independently authored knowledge metadata and its document digest contracts
remain; S45 stays **parent-classified-unreviewed**.

The [Cozy result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s48-validation-01-result.json)
records invocation `98d399e8-949d-457c-a774-8f9f13b22587`, **1 completed suite /
7 passed**, with its [actual log](/private/tmp/skill.cncf.d/a-4c36f4b1-5ec6-40fd-9cab-496927773d1d/22292-20261009T165510Z.log).
The [CNCF result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s48-validation-02-result.json)
records invocation `1b0caf0a-db44-4b38-a5b0-6a981aa2a39c`, **13 completed suites /
128 passed**, with its [actual log](/private/tmp/skill.cncf.d/a-6365fda2-b107-4543-bc68-f58e6054cf01/23330-20261009T165644Z.log).
Total: **14 completed suites / 135 passed**; failed, aborted, canceled, ignored
and pending counts are **0**. Both runs ended with **SBT 0 / wrapper 0 / shared
serial lock released**. Each supplied result has empty warning/error lists;
no `[warn]` was observed in these invocations. Historical task-graph,
deprecation, unused-setting, dependency-cycle, SNAPSHOT, SLF4J and E092 warning
obligations remain **OPEN**, without suppression or warning-free closure.

The [second-runner assessment](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s48-second-runner-submission-assessment.json)
retains the actual object-submission procedural deviation: the runner spread
the object and explicitly reset existing permission/prefix fields rather than
using the prescribed unchanged-object cell. Actual argument values equal the
retained request; parent typed result admission accepted the terminal receipt,
current subject, log, summary and released lock. [Native session 21375 evidence](/private/tmp/p715-s48-sbt-session-21375-evidence.json)
remains history, with no value/authority/scope/result mismatch, platform denial,
replay or skill repair. This is not independent review or product failure.

Parent mechanical preflight first failed because its helper required inline
`should` where existing `_assert_non_content` specification vocabulary is
permitted; the corrected helper passed without product edits. Native failures
and corrections remain historical. The [separate spec-prose admission](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s48-spec-prose-fix-admission.json)
records native chunk `342e95` exit **0**: only the existing E5 scenario
display-name lost the obsolete “digest” word. No body/assertion/fixture/header
changed; actual **128** CNCF behavior successes are retained without rerun.
Its [native evidence](/private/tmp/p715-02-s48-spec-prose-native-evidence.json)
retains the actual request/result.

This section supersedes the earlier pending S48 status while preserving all
prior sections and history. Native separate-root focused runs do not prove
connected/cross-binary interoperability, final media, whole-Step or Phase
acceptance. Whole **E715-04 OPEN** retains connected/cross-binary admission and
accumulated review; **E715-05 OPEN** retains target.digest, coordinator
requestDigest cache, report/attestation/baseline and actual protection consumers.
Warnings, pending M2 cycles **6/7/8/10/11/13**, original independent Step/full-Phase
reviews, exact local Step commits and the distinct release remain **OPEN**.
Full SBT and broader integration remain **deferred-not-run to PHASE-71.7**.
The same Goal, four bases, authority/binding, PLAN epoch, review ledger revision
**5**, repair counters **14 / 0**, stage triples and checkboxes retain their
state. Parent owns diff verification, validation, review and transitions;
this factual projection claims no closure, acceptance or commit.
