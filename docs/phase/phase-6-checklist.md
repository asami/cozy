# Phase 6 Checklist

This checklist is the authoritative progress tracker for Component Repository
Publication and Scaffolding work from 2026-05-21 onward.

The original design history remains in
`docs/journal/2026/05/car-sar-catalog-publish-plan-2026-05-20.md`.

## CS-01: Explicit-Version Startup Baseline

Status: DONE

- [x] Confirm `textus server textus-semantic-integration-engine:0.1.0` starts.
- [x] Keep legacy `artifact@version` as compatibility input.
- [x] Use explicit-version startup as the baseline while versionless metadata is
      fixed.

## CS-02: Missing Versionless Metadata

Status: DONE

- [x] Confirm versionless startup fails without
      `repository/car/textus-semantic-integration-engine/maven-metadata.xml`.
- [x] Decide that Maven metadata should be derived from CAR/SAR catalog state.

## CS-03: Canonical Artifact Name

Status: DONE

- [x] Keep `textus-semantic-integration-engine` as the canonical artifact and
      component name for the immediate flow.
- [x] Defer `textus-sie` alias or rename handling.

## CS-04: Catalog Responsibility Split

Status: DONE

- [x] Separate source catalog, warehouse publication, and runtime consumption
      responsibilities.
- [x] Keep Cozy as the catalog/publication policy owner.
- [x] Keep sbt-cozy as an sbt adapter.
- [x] Keep Textus as the runtime metadata consumer.

## CS-05: Catalog Path Policy

Status: DONE

- [x] Use `src/main/catalog` as source catalog root.
- [x] Use `repository/catalog` as warehouse/public catalog root.
- [x] Do not require source paths to mirror warehouse paths exactly.

## CS-06: Car/Sar Task Naming Policy

Status: DONE

- [x] Use `Car` / `Sar` spelling for new task names.
- [x] Keep legacy all-caps `CAR` / `SAR` aliases for compatibility.
- [x] Update new docs and scripts to prefer `cozyBuildCar`,
      `cozyPublishCar`, `cozyBuildSar`, and `cozyPublishSar`.

## CS-07: Simplified CNCF Runtime Requirement

Status: DONE

- [x] Add runtime compatibility contract with `minimum`, `maximum`,
      `excluded`, and `tested`.
- [x] Treat component dependency manifests as component-owned dependency
      declarations, not CNCF transitive dependency lists.
- [x] Use CNCF runtime catalog base-provided coordinates to detect
      shared/local overlap when available.
- [x] Validate packaged CAR runtime metadata against resolved CNCF runtime
      version for `minimum`, `maximum`, `excluded`, and `tested` when runtime
      descriptor data is available.

## CS-08: SIE Build Dependency Simplification

Status: DONE

- [x] Simplify SIE direct build dependencies to `goldenport-cncf` plus
      ScalaTest.
- [x] Remove redundant direct dependencies and overrides from the SIE build.
- [x] Use the simplified build as the downstream scaffold target.

## CS-08B: Generated sbt Scaffold Dependency Simplification

Status: DONE

- [x] Update Cozy scaffold templates to emit the minimal dependency shape.
- [x] Update simple-modeler `build.sbt` content generation to match.
- [x] Add tests rejecting the old broad dependency list.

## CS-09: SIE CAR Manifest Simplification

Status: DONE

- [x] Update SIE `project.yaml` to declare only CNCF runtime compatibility.
- [x] Confirm no component-owned dependencies means no
      `component-dependencies.yaml` is expected.

## CS-10: CAR Packaging Defaults

Status: DONE

- [x] Default project-dir CAR packaging to `src/main/car`.
- [x] Default dependency embedding to disabled.
- [x] Let ordinary CAR projects omit `source_dir` and `include_dependencies`.

## CS-11: CAR/SAR Catalog Schema

Status: DONE

- [x] Add CAR/SAR catalog model.
- [x] Add deterministic YAML rendering.
- [x] Validate versions, selectors, status, channel, runtime compatibility, and
      archive extension.

## CS-12: `publish-car` Flow

Status: DONE

- [x] Add Cozy `publish-car` command.
- [x] Publish prebuilt or temporary built CAR archive.
- [x] Update source CAR catalog.
- [x] Publish warehouse CAR catalog.
- [x] Leave Maven metadata generation to CS-15.

## CS-13: `publish-sar` Flow

Status: DONE

- [x] Add Cozy `publish-sar` command.
- [x] Publish prebuilt or temporary built SAR archive.
- [x] Update source SAR catalog.
- [x] Publish warehouse SAR catalog.
- [x] Leave Maven metadata generation to CS-15.

## CS-14: sbt-cozy Task Bridge

Status: DONE

- [x] Add canonical `cozyBuildCar/Sar` tasks.
- [x] Add canonical `cozyPublishCar/Sar` tasks.
- [x] Add canonical `cozyDistributeCar/Sar` tasks.
- [x] Keep legacy all-caps aliases.
- [x] Delegate publish to Cozy `publish-car` / `publish-sar`.
- [x] Let bridge generation use the consuming sbt project directory as the base
      for Cozy generation version defaults.

## CS-15: Derived Maven Metadata

Status: DONE

- [x] Generate CAR/SAR `maven-metadata.xml` from catalog as compatibility
      output.
- [x] Use `recommended` for `<latest>` when present.
- [x] Use `latestStable` for `<release>` when present.
- [x] Exclude disabled versions and keep deprecated versions explicit.

## CS-16: SIE 0.1.1-SNAPSHOT Spec Reflection

Status: DONE

- [x] Keep SIE on `0.1.1-SNAPSHOT` during development.
- [x] Use `sbt-cozy 0.1.8-SNAPSHOT` for canonical `Car` task names.
- [x] Keep simplified direct build dependencies.
- [x] Keep runtime compatibility-only `project.yaml`.
- [x] Add source CAR catalog for the already published `0.1.0` entry.

## CS-17: Textus Artifact Version Syntax

Status: DONE

- [x] Make `artifact:version` the canonical explicit-version syntax.
- [x] Keep `artifact@version` as compatibility input.
- [x] Reject mixed `:` / `@` artifact version spellings.
- [x] Defer direct CAR/SAR catalog resolution.

## CS-17R: Textus Metadata-Selected Version Propagation

Status: DONE

- [x] Resolve concrete version from Maven metadata for versionless CAR/SAR
      startup.
- [x] Pass the selected version to CNCF as
      `--textus.component.version=<resolved>`.
- [x] Use this fixed Textus launcher for final public verification.

## CS-18: SIE 0.1.1 Public Publication Verification

Status: DONE

- [x] Produce local 0.1.1 CAR with `cozyPublishCar`.
- [x] Update local source and warehouse CAR catalogs.
- [x] Generate local derived `maven-metadata.xml` with
      `latest/release=0.1.1`.
- [x] Confirm the public 0.1.1 CAR artifact is reachable from the public
      repository.
- [x] Confirm the public CAR `maven-metadata.xml` endpoint is reachable.
- [x] Close public versionless runtime verification as a release-ops smoke
      check outside the Cozy implementation phase.

Verification notes:

- `curl -I https://www.simplemodeling.org/repository/car/textus-semantic-integration-engine/0.1.1/textus-semantic-integration-engine-0.1.1.car`
  returned `HTTP/2 200`.
- `curl -I https://www.simplemodeling.org/repository/car/textus-semantic-integration-engine/maven-metadata.xml`
  returned `HTTP/2 200`.
- Full public runtime startup remains an operational smoke target, not an open
  Cozy implementation item.

## CS-19: Cozy Component Init Scaffolding Contract

Status: DONE

- [x] Define `cozy init component` command semantics.
- [x] Accept package name, artifact name, component name, component kind,
      version, organization, and display metadata through config and/or CLI.
- [x] Prefer definition-file input with CLI overrides.
- [x] Generate consistent `build.sbt`, `project.yaml`, component descriptor,
      package directory, CAR/SAR source directory, and optional Web entry
      skeleton.
- [x] Keep sbt-cozy out of initialization semantics.
- [x] Record that `/Users/asami/src/dev2026/textus-knowledge-editor`
      initialization uses the Cozy init path.

Completion notes:

- Added `cozy init component --save=<dir> [--config=<file>]` as the user-facing
  entrypoint for configured component project initialization.
- Config-file values are read first and CLI values override them
  deterministically.
- CML-specific package and component class names can be supplied explicitly as
  `cml.package` and `cml.component.name`; the older `project.scalaPackage` and
  `project.component.className` keys remain compatible aliases.
- `--kind=car` and `--kind=car-sar` reuse the existing Cozy scaffold
  materialization path.
- Existing `car-sbt-project` behavior remains compatible.
- CS-18 is closed for Phase 6. Public artifact and metadata reachability were
  confirmed; full public runtime startup remains a release-ops smoke target.
