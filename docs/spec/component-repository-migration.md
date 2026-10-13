# Component repository v1 migration

Cozy migrates a legacy `cncf.component-repository-index.v1` warehouse while
publishing a component. The warehouse owner must first supply
`.cozy/component-repository-migration.v1.json`:

```json
{
  "schema": "cozy.component-repository-migration.v1",
  "cars": [
    { "artifactId": "example-component", "namespace": "org.example", "id": "Example" }
  ]
}
```

The mapping is exact: every indexed legacy CAR has one mapping and every mapping
is used. Cozy rejects duplicate or unknown JSON fields, duplicate artifact or
component identities, invalid canonical identities, and absent or incomplete
mappings. It never infers a namespace or id from an artifact id, archive,
descriptor, CML, package, or path.

Cozy checks the complete mapping before processing mapped releases. Legacy
catalog and archive sources must be safe contained regular files without
symlinks. For each mapped CAR release, Cozy copies the selected legacy archive
directly to its canonical namespace/id/version path, overwriting an existing
regular destination. Existing non-regular or symlink destinations fail with the
ordinary migration collision diagnostic. No destination bytes are read for
reuse or equality admission; stored checksums and legacy SHA sidecars are not
admission inputs.

Cozy writes the schema-2 catalog directly with canonical coordinate and file
projections. Each version carries exactly `version`, `channel`, `status`,
`component`, `publishedAt`, `file` and `runtime`; migration emits no archive SHA
sidecar. Release selectors, status, aliases, tags, terms, channel, publication
time and runtime retain their meanings.
Optional regular non-symlink CML and model-metadata sources are copied directly
beside that canonical catalog, with the same ordinary destination conditions.
Legacy catalog/archive URLs and bytes are never changed or deleted; they remain
readable but are no longer indexed. Existing SAR index entries and catalogs remain
unchanged.

After canonical output is written, Cozy validates actual detailed catalogs and
selected archive presence, then writes the v2 index directly last. This ordering
provides no index visibility or atomicity guarantee. Migration has no locks,
candidates, snapshots, backups, rollback or byte-based reuse. An ordinary
failure may leave partial canonical output and is reported through the existing
migration error shape. No retry or idempotence guarantee is made.

After conversion returns its v2 index, ordinary CAR publication continues. If
that later operation fails, already written migration output may remain. Legacy
inputs and unrelated SAR entries retain their existing meanings.

## S26 implementation status — 2026-10-09

P715-02-S26-REPOSITORY-PUBLICATION-DIRECT is
**implemented-focused-validated-unreviewed** under the
[S26 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s26-implementation-manifest.json).
The initial editing handoff on 2026-10-09 was
**implemented-unvalidated-unreviewed**. Actual focused validation is recorded
below; independent review and acceptance remain OPEN. The existing
[CAR publisher specification](../../src/test/scala/cozy/CozyCarPublisherSpec.scala)
retains successful mapped conversion and absent/incomplete/duplicate mapping
behavior; its checksum-mismatch and byte-collision protection case is removed.
Independent review remains OPEN. Shared catalog,
carrier/runtime and **E715-01..05** obligations remain unfinished.

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
