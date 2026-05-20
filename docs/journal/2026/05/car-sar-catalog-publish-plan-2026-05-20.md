# CAR/SAR Catalog and Publish Flow Plan

Date: 2026-05-20

This journal records the development items needed to make CAR/SAR repository
publication usable by `textus` without project-local configuration. The immediate
driver is `textus-semantic-integration-engine`. Version `0.1.0` is already
published and can start with an explicit version:

```sh
textus server textus-semantic-integration-engine:0.1.0
```

The CS work now targets the next development line, `0.1.1-SNAPSHOT`. CS-16 is
limited to reflecting the latest CAR/SAR catalog and packaging specification in
the `textus-semantic-integration-engine` sources. Publishing and end-to-end
confirmation are deferred until the `0.1.1` release.

The versionless form is expected to work only after the release publication
flow has produced the catalog and derived metadata:

```sh
textus server textus-semantic-integration-engine
```

## Problem

`cozyDistributeCAR` currently copies the CAR file into the warehouse:

```text
repository/car/<artifact>/<version>/<artifact>-<version>.car
```

It does not create:

```text
repository/car/<artifact>/maven-metadata.xml
```

`textus` uses that metadata for versionless CAR/SAR resolution. As a result,
explicit versions can work while default/latest resolution fails.

The metadata also needs a durable source of truth. Rebuilding it only from the
directory tree is useful as a recovery path, but it does not give enough
operational control for `recommended`, `deprecated`, `disabled`, aliases, or
future compatibility policy.

## Catalog Direction

Use Git-managed source catalogs as the operational metadata source of truth.
The source paths do not have to mirror warehouse paths exactly.

Source catalog:

```text
src/main/catalog/cncf.yaml
src/main/catalog/car/*.yaml
src/main/catalog/sar/*.yaml
```

Warehouse/public catalog:

```text
repository/catalog/runtime/cncf.yaml
repository/catalog/car/*.yaml
repository/catalog/sar/*.yaml
```

Compatibility and resolver outputs:

```text
repository/textus/runtime-catalog.yaml
repository/car/<artifact>/maven-metadata.xml
repository/sar/<artifact>/maven-metadata.xml
```

`maven-metadata.xml` is a derived compatibility artifact, not the source of
truth.

The catalog source also avoids treating the repository directory tree as the
only history. The directory tree remains useful for recovery checks, but status
changes such as `deprecated` or `disabled`, aliases, and recommended-version
selection must be represented in the catalog.

## CAR/SAR Catalog Shape

Initial CAR catalog example:

```yaml
schemaVersion: 1
kind: car
artifactId: textus-semantic-integration-engine
recommended: 0.1.0
latestStable: 0.1.0
latestSnapshot:
status: active
aliases: []
versions:
  - version: 0.1.0
    channel: stable
    status: active
    component: textus-semantic-integration-engine
    publishedAt: 2026-05-19T20:46:08+09:00
    file: repository/car/textus-semantic-integration-engine/0.1.0/textus-semantic-integration-engine-0.1.0.car
    runtime:
      cncf: ">=0.4.8"
    checksum:
      sha256:
```

The current canonical artifact name remains
`textus-semantic-integration-engine`. `textus-sie` can be introduced later as an
alias or as a renamed artifact, but it is not part of the immediate fix.

## Component Dependency Simplification

The SIE driver currently repeats many libraries in both `build.sbt` and
`project.yaml`. The intended steady state is simpler:

- `build.sbt` should depend directly on the CNCF runtime artifact and test
  libraries needed by the project.
- The CAR dependency manifest should normally declare only the CNCF runtime
  requirement.
- Transitive libraries supplied through CNCF should not be re-declared as
  component shared dependencies unless the component truly owns a runtime
  library requirement outside CNCF.

For `textus-semantic-integration-engine`, the desired direction is:

```scala
libraryDependencies += "org.goldenport" %% "goldenport-cncf" % cncfVersion
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % Test
```

Additional direct dependencies may remain only if compilation proves they are
not supplied by CNCF or are intentionally component-owned. Test-only helper
libraries should not appear in the CAR runtime dependency manifest.

The CAR manifest should move from enumerating CNCF transitive dependencies:

```yaml
packaging:
  kind: car
  car:
    source_dir: src/main/car
    include_dependencies: false
    dependencies:
      provided:
        - org.goldenport:goldenport-cncf_3:0.4.8
        - org.simplemodeling:simplemodeling-model_3:0.1.7
      shared:
        - ...
```

to the minimal runtime contract:

```yaml
packaging:
  kind: car
  car:
    dependencies:
      provided:
        - org.goldenport:goldenport-cncf_3:0.4.8
```

`source_dir: src/main/car` and `include_dependencies: false` should become cozy
defaults for normal CAR packaging, so ordinary CAR projects do not need to
repeat them.

## Runtime Compatibility Policy

The catalog should represent the runtime compatibility contract, not only exact
dependency coordinates. A CAR can say that it requires CNCF `0.4.8` or later,
while still excluding specific incompatible versions if necessary.

Candidate catalog shape:

```yaml
runtime:
  cncf:
    minimum: 0.4.8
    maximum:
    excluded:
      - 0.4.9
    tested:
      - 0.4.8
```

This should not replace the generated component dependency manifest immediately.
For v1, the manifest can still contain the concrete provided coordinate used by
CNCF dependency loading:

```yaml
dependencies:
  provided:
    - org.goldenport:goldenport-cncf_3:0.4.8
```

The catalog carries operational compatibility. The dependency manifest carries
runtime classpath/dependency loading intent. Later, cozy can generate the
manifest's concrete `provided` coordinate from a simpler project setting such as
`cncf.version: 0.4.8`.

## Task Naming

New task names should use `Car` / `Sar`, not all-caps `CAR` / `SAR`.

Preferred task surface:

```text
cozyBuildCar
cozyBuildSar
cozyPublishCar
cozyPublishSar
cozyDistributeCar
cozyDistributeSar
```

Legacy names may remain as aliases during migration:

```text
cozyBuildCAR
cozyBuildSAR
cozyPublishCAR
cozyPublishSAR
cozyDistributeCAR
cozyDistributeSAR
```

Documentation, tests, and new scripts should use the `Car` / `Sar` names.

## Publish Flow

The user-facing operation should be one task:

```sh
sbt cozyPublishCar
```

Internally it can be split into smaller steps:

```text
cozyPublishCar
  -> cozyBuildCar
  -> cozyUpdateCarCatalog
  -> cozyPublishCarArtifact
  -> cozyPublishCarCatalog
  -> cozyPublishCarMetadata
```

Responsibilities:

- `cozyBuildCar`: build the CAR archive.
- `cozyUpdateCarCatalog`: update `src/main/catalog/car/<artifact>.yaml`.
- `cozyPublishCarArtifact`: copy the CAR to
  `repository/car/<artifact>/<version>/<artifact>-<version>.car`.
- `cozyPublishCarCatalog`: copy/transform the source catalog into
  `repository/catalog/car/<artifact>.yaml`.
- `cozyPublishCarMetadata`: generate
  `repository/car/<artifact>/maven-metadata.xml` from the catalog.

`cozyPublishSar` should follow the same structure for SAR artifacts.

## Repository Responsibilities

`cozy` owns the catalog and publish policy. `sbt-cozy` adapts that policy into
sbt tasks, and `textus` consumes the published repository metadata at runtime.

### cozy

`cozy` owns the catalog schema and publication semantics.

- Define CAR/SAR catalog schema.
- Define normal CAR packaging defaults, including `source_dir: src/main/car`
  and `include_dependencies: false`.
- Define how simplified CNCF runtime requirements generate CAR dependency
  manifest entries.
- Read and update `src/main/catalog/car/*.yaml` and
  `src/main/catalog/sar/*.yaml`.
- Publish catalog files to `repository/catalog/car` and
  `repository/catalog/sar`.
- Generate CAR/SAR `maven-metadata.xml`.
- Keep `repository/textus/runtime-catalog.yaml` as a compatibility copy while
  moving the canonical CNCF runtime catalog toward
  `repository/catalog/runtime/cncf.yaml`.
- Provide `Car` / `Sar` task naming semantics for downstream adapters.

### sbt-cozy

`sbt-cozy` should remain an sbt adapter. It should not duplicate catalog schema
or publication policy.

- Add `cozyBuildCar`, `cozyBuildSar`, `cozyPublishCar`, `cozyPublishSar`,
  `cozyDistributeCar`, and `cozyDistributeSar` tasks.
- Keep all-caps legacy task aliases for compatibility.
- Delegate catalog and metadata behavior to `cozy`.
- Pass sbt-derived facts only, such as project directory, main jar, library jars,
  SPI jars, name, version, and build identity.
- Do not expand CNCF transitive dependencies into CAR manifest content.
- Avoid parsing or generating CAR/SAR catalog content in sbt-cozy.

### textus

`textus` should first remain compatible with generated `maven-metadata.xml`.

- Continue versionless CAR/SAR resolution through
  `repository/<kind>/<artifact>/maven-metadata.xml`.
- Later, add direct catalog resolution from `repository/catalog/car/*.yaml` and
  `repository/catalog/sar/*.yaml`.
- Keep explicit-version resolution working with canonical
  `textus server textus-semantic-integration-engine:0.1.0`.
- Keep legacy `artifact@version` accepted as compatibility input while new docs
  and scripts use `artifact:version`.
- After catalog support exists, support richer selectors such as recommended,
  latest stable, disabled rejection, deprecated warning, and aliases.

`textus` should treat the catalog as runtime-resolution input, not as mutable
state. It may cache remote catalogs, but publication history and lifecycle
status remain in the source catalog managed by cozy/CNCF release workflows.

## Progress Ledger

This ledger uses stable ids, status, decision/current position, next action, and
dependency notes. It is not a release checklist; it is a working design ledger
for coordinating cozy, sbt-cozy, and textus.

| Id | Topic | Status | Decision / Current Position | Next Action | Depends On |
| --- | --- | --- | --- | --- | --- |
| CS-01 | Explicit-version startup | done | `textus server textus-semantic-integration-engine:0.1.0` starts, so CAR body, dependency loading, and CNCF startup are minimally valid. Legacy `@` spelling remains compatibility input. | Use this as the baseline smoke while versionless resolution is fixed. | none |
| CS-02 | Missing versionless metadata | done | Versionless startup fails because `repository/car/textus-semantic-integration-engine/maven-metadata.xml` is missing. | Generate metadata from CAR catalog during publish. | CS-05, CS-07 |
| CS-03 | Canonical artifact name | decided | Keep `textus-semantic-integration-engine` as the CAR artifact/component name for now. | Do not rename to `textus-sie` in the immediate fix. | none |
| CS-04 | Catalog responsibility split | decided | Source catalog, warehouse publication, and runtime consumption have separate owners and outputs. | Preserve this split in cozy, sbt-cozy, and textus implementation. | none |
| CS-05 | Catalog path policy | decided | Source catalog uses `src/main/catalog`; warehouse uses `repository/catalog`. Paths do not have to mirror exactly. | Implement source-to-warehouse mapping in cozy. | CS-04 |
| CS-06 | Task naming policy | decided | New task names use `Car` / `Sar`; legacy all-caps `CAR` / `SAR` names may remain as aliases. | Update cozy/sbt-cozy docs and task surfaces. | none |
| CS-07 | Simplified CNCF runtime requirement | done | CAR/SAR projects declare `runtime.cncf.minimum` compatibility, while component dependency manifests keep only component-owned dependencies. CNCF base-provided modules live in `repository/textus/runtime-catalog.yaml`; cozy rejects `shared/local` overlaps when catalog data is available. | Use this contract in SIE cleanup. | CS-04 |
| CS-08 | SIE build dependency simplification | done | SIE now compiles and builds CAR with direct `goldenport-cncf` plus ScalaTest only; redundant direct dependencies and overrides were removed from `build.sbt`. | Use the simplified build as the downstream scaffold target. | CS-07 |
| CS-08B | Generated sbt scaffold dependency simplification | done | Cozy and simple-modeler `build.sbt` scaffold templates now emit direct `goldenport-cncf` plus ScalaTest only, with tests rejecting the old broad dependency list. | Start CS-09 SIE CAR manifest simplification. | CS-07, CS-08 |
| CS-09 | SIE CAR manifest simplification | done | SIE `project.yaml` now declares only CNCF runtime compatibility; it has no component-owned CAR dependencies, so no `component-dependencies.yaml` is expected. | Start CS-10 CAR packaging defaults so `source_dir` and `include_dependencies` can be omitted by ordinary CAR projects. | CS-07, CS-08, CS-08B |
| CS-10 | CAR packaging defaults | done | Project-dir CAR packaging defaults to `src/main/car` and dependency embedding disabled; SIE now omits `source_dir` and `include_dependencies`. | Start CS-11 CAR/SAR catalog schema and metadata generation. | CS-07, CS-09 |
| CS-11 | CAR/SAR catalog schema | done | Cozy now has a CAR/SAR catalog model, deterministic YAML render, source-path validation, and schema checks for versions, selectors, status, channel, runtime compatibility, and archive extension. | Start CS-12 `cozyPublishCar` flow; derived `maven-metadata.xml` remains CS-15. | CS-04, CS-05, CS-07, CS-10 |
| CS-12 | `publish-car` flow | done | Cozy now has a `publish-car` command that publishes a prebuilt or temporary built CAR, updates the source CAR catalog, and publishes the catalog to the warehouse without generating Maven metadata. | Start CS-13 `publish-sar` flow. | CS-10, CS-11 |
| CS-13 | `publish-sar` flow | done | Cozy now has a `publish-sar` command that publishes a prebuilt or temporary built SAR, updates the source SAR catalog, and publishes the catalog to the warehouse without generating Maven metadata. | Start CS-14 sbt-cozy task bridge. | CS-11, CS-12 |
| CS-14 | sbt-cozy task bridge | done | sbt-cozy now exposes canonical `cozyBuildCar/Sar`, `cozyPublishCar/Sar`, and `cozyDistributeCar/Sar` tasks, keeps legacy all-caps aliases, and delegates publish to Cozy `publish-car` / `publish-sar` through sbt-bridge v1. | Start CS-15 derived Maven metadata generation from CAR/SAR catalogs. | CS-06, CS-12, CS-13 |
| CS-15 | Derived Maven metadata | done | Cozy publish now generates CAR/SAR `maven-metadata.xml` from the catalog as compatibility output. `recommended` drives `<latest>`, `latestStable` drives `<release>`, disabled versions are excluded, and deprecated versions remain explicit. | Start CS-16 SIE 0.1.1-SNAPSHOT spec reflection. | CS-11, CS-12 |
| CS-16 | SIE 0.1.1-SNAPSHOT spec reflection | done | `textus-semantic-integration-engine` stays on `0.1.1-SNAPSHOT`, uses `sbt-cozy 0.1.8-SNAPSHOT` for canonical `Car` task names during development, keeps the simplified direct build dependencies and runtime compatibility-only `project.yaml`, and now has a source CAR catalog for the already published `0.1.0` entry. Release publication is not part of CS-16. | Use the local CAR shape as the development baseline; publish and versionless startup verification remain CS-18. | CS-07, CS-10, CS-15 |
| CS-17 | textus artifact syntax and catalog resolution | done | Textus now uses `artifact:version` as the canonical explicit-version syntax, keeps `artifact@version` as compatibility input, and rejects mixed `:` / `@` artifact version spellings. CAR/SAR direct catalog resolution is still deferred. | Keep metadata-based startup stable; direct CAR/SAR catalog resolution remains a future follow-up. | CS-11, CS-16 |
| CS-18 | SIE 0.1.1 release publication verification | future | The 0.1.1 release should publish the CAR, warehouse catalog, and derived Maven metadata, then prove versionless `textus server textus-semantic-integration-engine`. | Run `cozyPublishCar`, upload/sync warehouse output, and verify versionless startup during the 0.1.1 release. | CS-15, CS-16, CS-17 |

## Immediate Next Step

Start CS-18 when the `textus-semantic-integration-engine` `0.1.1` release is
ready. Publish the CAR, warehouse catalog, and derived Maven metadata, then
prove versionless `textus server textus-semantic-integration-engine`.

The publish flow must eventually prove these outputs are produced from catalog
state:

```text
repository/car/textus-semantic-integration-engine/0.1.1/textus-semantic-integration-engine-0.1.1.car
repository/catalog/car/textus-semantic-integration-engine.yaml
repository/car/textus-semantic-integration-engine/maven-metadata.xml
```

Until that release, keep using `0.1.1-SNAPSHOT` for feature development and
validate the local generated CAR shape without treating it as public repository
confirmation.
