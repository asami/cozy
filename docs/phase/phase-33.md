# Phase 33: Declared Cozy Runtime Selection for CAR Publication

Status: DONE

Plan date: 2026-08-21

## Goal

Make CAR publication select the exact Cozy runtime declared by the CAR's
`project.yaml` (`build.cozyVersion`), without requiring a temporary
project-local `.cozy` runtime override.

## Boundary and invariants

- Cover CAR `publish`, `publishLocal`, `cozyPublishCar`, and
  `cozyPublishLocalCar` paths that delegate through `sbt-cozy` and Cozy
  Launcher.
- Treat `project.yaml build.cozyVersion` as the selected runtime coordinate for
  a CAR publication; local or ambient `.cozy` defaults must neither be required
  nor silently override it.
- Preserve `.cozy` as an optional source of non-runtime local configuration and
  retain its behavior for non-CAR workflows.
- Keep CAR identity, generated source, archive bytes, publication catalog, and
  warehouse layout outside this Phase unless a focused runtime-selection proof
  requires test fixtures for them.
- Do not weaken generation-provenance or CNCF descriptor compatibility checks
  to accommodate a wrongly selected runtime.

## Stages

### RT33-01: Runtime-Selection Contract

Stage Status:

- Current status: DONE
- Owner: Cozy Launcher and sbt-cozy
- Update rule: mark work complete only from the Phase 33 checklist.
- Checklist basis: `RT33-01`

Establish one verifiable selection contract from CAR `project.yaml` through the
delegate command to the executing Cozy runtime, including clear failure when
the requested runtime cannot be selected.

### RT33-02: CAR Publication Acceptance

Stage Status:

- Current status: DONE
- Owner: Cozy, sbt-cozy, and Cozy Launcher
- Update rule: mark work complete only from the Phase 33 checklist.
- Checklist basis: `RT33-02`

Prove release and development CAR publication use the declared runtime with no
temporary `.cozy` version setting, and retain the existing compatibility gates.

## Completion criteria

- A generated CAR's `build.cozyVersion` reaches every publish delegate path as
  the runtime-selection input.
- Ambient and project-local `.cozy` version defaults cannot displace that
  declared Cozy coordinate during CAR publication.
- The selected runtime reports the declared coordinate, or publication fails
  before generation, packaging, or warehouse mutation with a diagnostic that
  identifies the mismatch.
- Focused cross-repository evidence covers `publish` and `publishLocal` for
  both a development coordinate and an admitted release coordinate.
- No descriptor, provenance, archive, or catalog validation is weakened.

## Dependencies and handoff

- The observed launcher-selection mismatch is recorded in
  `docs/journal/2026/08/entity-revision-generator-downstream-acceptance-transfer-2026-08-03.md`.
- Phase 25 transferred one downstream acceptance to CBD Support Phase 8
  `P8-61`; that transfer remains intact. This Phase owns the generalized CAR
  publication runtime-selection contract and must coordinate its launcher work
  rather than modify CBD Support as a workaround.
- The runtime-selection source path crosses the Cozy scaffold, `sbt-cozy`, and
  `cozy-launcher`. The launcher already contains an unreleased deterministic
  `project.yaml build.cozyVersion` selection contract; Phase 33 must prove its
  use from CAR publication and coordinate any required release or bridge
  change.
- The user selected the runtime-selection-only scope. A CNCF-owned Scala/Java
  compatibility profile is excluded from this Phase and must not be introduced
  as a project-local substitute in a CAR or `.cozy` file.

## Deferred Hygiene

- `HYG-P33-CLOSURE-001`: `sbt-cozy` has pre-existing qualified-private
  `CarPublicationCoordinate` and component-API helper names that retain
  private-style naming. This is outside the runtime-selection boundary and is
  not repaired by Phase 33.
- `HYG-P33-CLOSURE-002`: the pre-existing `sbt-cozy`
  `CozyGenerationProvenanceIntegrationSpec` fails against the current Cozy
  bridge because its request omits the now-required `--component-version`.
  The spec predates Phase 33 and no runtime-selection repair is authorized;
  its owning compatibility boundary must supply an updated accepted fixture.
  This does not leave Phase 33 provenance evidence open: the current Cozy
  bridge and provenance checks passed in `BridgeContractSpec` and
  `Phase51Cv05GenerationProvenanceSpec`.
- `HYG-P33-CLOSURE-003`: `CozyArchivePackagerCv06Spec` has pre-existing
  indentation drift in a `which` subsection. It is presentation-only,
  unchanged by this Phase, and belongs to a separate hygiene-only change.

## Closure evidence

- `CB-P33-003` was corrected in the archive-admission executable
  specification: it now requires the generated scaffold to omit the retired
  `cozyDelegateCommand` and retain `cozyDelegateProjectDir := None`, while
  preserving the CAR-owned `project.yaml build.cozyVersion` assertion and all
  archive-admission checks. Focused validation
  `64828-20260821T024826Z` passed 11 of 11 tests; the focused re-review found
  no Current Boundary Blocker.
- The accepted cross-repository coordinates are CAR runtime
  `0.3.1-SNAPSHOT` from `project.yaml` and `sbt-cozy`
  `0.1.17-SNAPSHOT`. The scripted fixture recorded the former for both
  `publish` and `publishLocal` without a project-local `.cozy` directory.
- Final full review found no Current Boundary Blocker. The final full suites
  for Cozy and `sbt-cozy` are the acceptance gate executed immediately before
  this Phase's repository-local release commits.

## References

- `docs/phase/phase-33-checklist.md`
- `docs/phase/phase-25.md`
- `docs/journal/2026/08/entity-revision-generator-downstream-acceptance-transfer-2026-08-03.md`
- `src/main/scala/cozy/scaffold/CozyScaffold.scala`
