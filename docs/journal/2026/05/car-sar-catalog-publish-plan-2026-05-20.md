# CAR/SAR Catalog and Publish Flow Plan

Date: 2026-05-20

This journal records the development items needed to make CAR/SAR repository
publication usable by `textus` without project-local configuration. The immediate
driver is `textus-semantic-integration-engine`, which can start with an explicit
version:

```sh
textus server textus-semantic-integration-engine@0.1.0
```

The versionless form currently fails because the CAR body is published, but the
repository metadata used for version discovery is missing:

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
- Keep explicit-version resolution working:
  `textus server textus-semantic-integration-engine@0.1.0`.
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
| CS-01 | Explicit-version startup | done | `textus server textus-semantic-integration-engine@0.1.0` starts, so CAR body, dependency loading, and CNCF startup are minimally valid. | Use this as the baseline smoke while versionless resolution is fixed. | none |
| CS-02 | Missing versionless metadata | done | Versionless startup fails because `repository/car/textus-semantic-integration-engine/maven-metadata.xml` is missing. | Generate metadata from CAR catalog during publish. | CS-05, CS-07 |
| CS-03 | Canonical artifact name | decided | Keep `textus-semantic-integration-engine` as the CAR artifact/component name for now. | Do not rename to `textus-sie` in the immediate fix. | none |
| CS-04 | Catalog responsibility split | decided | Source catalog, warehouse publication, and runtime consumption have separate owners and outputs. | Preserve this split in cozy, sbt-cozy, and textus implementation. | none |
| CS-05 | Catalog path policy | decided | Source catalog uses `src/main/catalog`; warehouse uses `repository/catalog`. Paths do not have to mirror exactly. | Implement source-to-warehouse mapping in cozy. | CS-04 |
| CS-06 | Task naming policy | decided | New task names use `Car` / `Sar`; legacy all-caps `CAR` / `SAR` names may remain as aliases. | Update cozy/sbt-cozy docs and task surfaces. | none |
| CS-07 | Simplified CNCF runtime requirement | done | CAR/SAR projects declare `runtime.cncf.minimum` compatibility, while component dependency manifests keep only component-owned dependencies. CNCF base-provided modules live in `repository/textus/runtime-catalog.yaml`; cozy rejects `shared/local` overlaps when catalog data is available. | Use this contract in SIE cleanup. | CS-04 |
| CS-08 | SIE build dependency simplification | open | SIE should compile with direct `goldenport-cncf` plus test dependencies unless specific component-owned libraries prove necessary. | Remove redundant direct dependencies/overrides and validate `sbt test cozyBuildCar`. | CS-07 |
| CS-09 | SIE CAR manifest simplification | open | SIE `project.yaml` should reduce to the CNCF provided runtime requirement unless additional runtime dependencies are truly component-owned. | Simplify `packaging.car.dependencies` and validate CAR root `component-dependencies.yaml`. | CS-07, CS-08 |
| CS-10 | CAR packaging defaults | open | Normal CAR packaging should default to `source_dir: src/main/car` and `include_dependencies: false`. | Move these defaults into cozy so projects can omit them. | CS-07 |
| CS-11 | CAR/SAR catalog schema | open | Need YAML schema for artifact history, status, recommended/latest, aliases, runtime compatibility, and files. | Implement reader/writer/validator in cozy. | CS-04, CS-05, CS-07 |
| CS-12 | `cozyPublishCar` flow | open | Public workflow should be one task; internal subtasks may build, update catalog, publish artifact, publish catalog, and generate metadata. | Implement in cozy first. | CS-10, CS-11 |
| CS-13 | `cozyPublishSar` flow | open | SAR should follow the same lifecycle as CAR. | Implement after or alongside CAR flow. | CS-11, CS-12 |
| CS-14 | sbt-cozy task bridge | open | sbt-cozy remains an adapter and must not own catalog schema or metadata policy. | Add `Car` / `Sar` tasks and delegate to cozy. Keep legacy aliases. | CS-06, CS-12, CS-13 |
| CS-15 | Derived Maven metadata | open | `maven-metadata.xml` is compatibility output, not source truth. | Generate `repository/car/<artifact>/maven-metadata.xml` and SAR equivalent from catalog. | CS-11, CS-12 |
| CS-16 | SIE CAR metadata publication | open | `textus-semantic-integration-engine` needs catalog and metadata publication to support versionless startup. | Publish catalog + metadata, then verify `textus server textus-semantic-integration-engine`. | CS-12, CS-15 |
| CS-17 | textus catalog resolution | future | Immediate fix can keep `maven-metadata.xml`; direct CAR/SAR catalog resolution is later. | Add catalog-aware resolution after publish flow is stable. | CS-11, CS-16 |

## Immediate Next Step

Restart from CS-07. First define the simplified dependency/runtime
compatibility model so the SIE CAR does not preserve redundant CNCF transitive
dependency declarations. Then simplify the SIE build and CAR manifest before
implementing `cozyPublishCar` / `cozyPublishSar` in cozy.

The publish flow must eventually prove these outputs are produced from catalog
state:

```text
repository/car/textus-semantic-integration-engine/0.1.0/textus-semantic-integration-engine-0.1.0.car
repository/catalog/car/textus-semantic-integration-engine.yaml
repository/car/textus-semantic-integration-engine/maven-metadata.xml
```

Then expose the new task surface through sbt-cozy and validate the SIE publish
path.
