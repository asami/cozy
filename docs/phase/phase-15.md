# Phase 15: CAR Project Metadata Centralization

Status: closed

Start date: 2026-07-14

## Goal

Make Cozy-generated CAR projects independently buildable with `project.yaml` as
the source of CAR identity, Scala version, exact development dependencies,
descriptor metadata, and runtime compatibility.

## Stage 15.1: Scaffold Contract

Stage Status:

- State: closed
- Checklist basis: `CP15-01` through `CP15-05`
- Current evidence: focused and full Cozy specifications pass; generated SBT
  metadata load passes
- Remaining boundary: none for the metadata-centralization contract

Scope:

- align `init component` and `car-sbt-project`;
- align `car` and `car-sar` layouts;
- generate Scala 3.3.8 CAR projects;
- advance the Cozy development version to `0.3.0-SNAPSHOT` for the new
  scaffold contract;
- keep exact development versions separate from runtime requirements;
- remove project-local build metadata generation and standard publish task
  duplication;
- derive the packaged component descriptor from `project.yaml` instead of
  generating a second source copy of CAR identity;
- publish the stable design and scaffold specification.

## Closure Condition

Phase 15 closes when every item in `phase-15-checklist.md` is checked, focused
and full Cozy tests pass, and at least one generated CAR project loads its SBT
identity and dependencies from `project.yaml`.

## Verification Evidence

Verified on 2026-07-14:

- Cozy reports development version `0.3.0-SNAPSHOT`.
- Generated projects use published `sbt-cozy 0.1.14` by default.
- `ModelerScaffoldSpec` passed 14 tests with no failures.
- Full `sbt --batch test` passed 490 tests with no failures.
- A generated `textus-knowledge-editor` CAR project loaded organization, name,
  component version, Scala 3.3.8, CNCF 0.5.0, and ScalaTest 3.2.10 from
  `project.yaml`.
- The generated project reported `cozyPackaging = car` and
  `cozyWireStandardPublishTasks = true`.
- A generated `car-sar` root build loaded component and subsystem settings from
  `component/project.yaml`, including Scala 3.3.8 and the exact dependencies.
- CAR-only and CAR+SAR scaffolds omit source `component-descriptor.json`; the
  descriptor is generated during packaging from the current project identity.
- `git diff --check` passed.

The generated sample's full CAR compile also exposed a pre-existing operation
generator issue: generated aggregate operations can refer to an unqualified
entity type such as `Notice`. That generator defect is independent of metadata
ownership and is deferred to the model-driven scaffold follow-up.

## Post-Closure Maintenance

Verified on 2026-07-14 without reopening Phase 15:

- integrated CAR lint includes a documentation category;
- deterministic checks cover the packaged reference manual, user guide, and
  component/service/operation descriptions used by generated Help;
- `src/main/car/manual/*` is the canonical source-managed CAR manual subtree;
- CNCF generated Help owns CLI, Help, Manual, OpenAPI, and MCP route navigation,
  rather than requiring route URLs to be duplicated in CML prose;
- Cozy full tests passed with 495 successful tests.

## References

- `docs/phase/phase-15-checklist.md`
- `docs/design/car-project-metadata-ownership.md`
- `docs/spec/car-project-scaffold.md`
- `docs/notes/publication-bridge/car-component-project.md`
- `docs/design/car-documentation-lint.md`
