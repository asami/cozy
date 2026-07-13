# Phase 15 Checklist

This checklist is the authoritative state ledger for Phase 15: CAR Project
Metadata Centralization.

## CP15-01: Metadata Ownership

Status: DONE

- [x] `project.yaml` owns organization, project/component identity, component
  version, Scala version, direct build dependencies, descriptor metadata, and
  runtime compatibility.
- [x] Exact CNCF development dependency and CNCF runtime minimum are separate
  fields.
- [x] `project/plugins.sbt` is documented as the bootstrap exception.

## CP15-02: CAR Scaffold

Status: DONE

- [x] `car` scaffold emits `project/ProjectYamlBuild.scala`.
- [x] Generated `build.sbt` projects metadata and dependencies from
  `project.yaml`.
- [x] Generated CAR projects use Scala 3.3.8.
- [x] Cozy uses development version `0.3.0-SNAPSHOT` for the Scala 3.3.8
  scaffold contract.
- [x] Generated projects use published `sbt-cozy 0.1.14` by default.
- [x] Generated `build.sbt` does not emit `BuildVersion.scala`.
- [x] Generated source trees omit `component-descriptor.json`; CAR packaging
  derives it from the current `project.yaml` identity.
- [x] Generated `build.sbt` leaves standard CAR publication delegation to
  `sbt-cozy`.

## CP15-03: Command And Layout Consistency

Status: DONE

- [x] `cozy init component` generates the centralized metadata contract.
- [x] `cozy car-sbt-project` generates `project.yaml` as well as SBT files.
- [x] `car-sar` root build consumes `component/project.yaml`.

## CP15-04: Documentation

Status: DONE

- [x] Strategy records Phase 15 and the metadata-ownership direction.
- [x] Design and specification documents define the stable contract.
- [x] README and CAR publication guidance describe generated project ownership.

## CP15-05: Verification

Status: DONE

- [x] `ModelerScaffoldSpec` passes.
- [x] A generated CAR project loads organization, name, version, Scala version,
  and dependencies through SBT.
- [x] Full `sbt --batch test` passes in Cozy.
- [x] `git diff --check` passes and Phase 15 evidence is recorded.

## Post-Closure Maintenance: CAR Documentation Lint

Status: DONE

- [x] Keep Phase 15 closed.
- [x] Add reference manual, user guide, and generated-Help description checks
      to integrated CAR lint.
- [x] Treat `src/main/car/manual/*` as the canonical packaged manual source.
- [x] Keep framework-owned CLI, Help, Manual, OpenAPI, and MCP navigation in
      CNCF generated Help instead of requiring duplicated CML route prose.
- [x] Verify strict lint blocks incomplete documentation while ordinary lint
      reports documentation debt as warnings.
- [x] Run focused and full Cozy specifications.
