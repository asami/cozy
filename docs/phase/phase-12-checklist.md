# Phase 12 Checklist

This checklist is the authoritative progress tracker for KnowledgeHub BoK
operational onboarding from 2026-06-20 onward.

## BK12-01: Phase 12 Documentation

Status: DONE

- [x] Add `docs/phase/phase-12.md`.
- [x] Add `docs/phase/phase-12-checklist.md`.
- [x] Add Phase 12 to `docs/strategy/cozy-development-strategy.md`.
- [x] Set `docs/phase/README.md` current phase to Phase 12.
- [x] Record Phase 12 as "KnowledgeHub BoK Operational Onboarding".

## BK12-02: KnowledgeHub BoK Source Onboarding

Status: DONE

- [x] Confirm source files are intentionally tracked.
- [x] Confirm generated directories remain ignored.
- [x] Align `README.md` with actual KnowledgeHub BoK operation.
- [x] Align `STRUCTURE.md` with actual `concept/` and `technology/` categories.
- [x] Confirm `src/main/doxsite` contains the expected source categories,
      glossary, history, manual, RDF seeds, and CSS assets.
- [x] Remove legacy generated `src/main/website` from the canonical source tree.

## BK12-03: BoK Config Alignment

Status: DONE

- [x] Update KnowledgeHub `conf/cozy/config.yaml` to current Cozy BoK conventions.
- [x] Keep `.cozy/config.yaml` out of git as a local sensitive settings file.
- [x] Use canonical Docker image `ghcr.io/asami/cozy-toolchain:latest`.
- [x] Confirm `bok.source` matches `src/main/doxsite`.
- [x] Confirm `bok.website`, `bok.antora`, and `bok.doxsite` match generated
      output locations.
- [x] Keep stage and upload workflows explicit and project-owned.

## BK12-04: Local Cozy Command Synchronization

Status: DONE

- [x] Identify why PATH `cozy` still uses older launcher behavior.
- [x] Define or update the local command synchronization procedure.
- [x] Ensure `cozy bok publish` is available outside `sbt run`.
- [x] Document the operational command path used by KnowledgeHub BoK.
- [x] Keep `.cozy/launcher.yaml` out of git as a local sensitive launcher file.

## BK12-05: BoK Build Operational Verification

Status: OPEN

- [ ] Run `cozy bok build /Users/asami/src/Project2026/bok-knowlegehub` from the
      normal operational entrypoint.
- [ ] Confirm `website.d`, `doxsite.d`, and `antora.d` are reproducible.
- [ ] Confirm generated outputs remain untracked.
- [ ] Record build diagnostics and usability gaps.

## BK12-06: Publish Dry-Run Operational Verification

Status: OPEN

- [ ] Run `cozy bok publish /Users/asami/src/Project2026/bok-knowlegehub --dry-run`
      from the normal operational entrypoint.
- [ ] Confirm dry-run output is useful for operation planning.
- [ ] If upload workflow is missing, decide whether current preflight behavior is
      acceptable.
- [ ] Record any required Cozy improvement for dry-run usability.

## BK12-07: Scaffold / Document Drift Diagnostics

Status: OPEN

- [ ] Identify scaffold documentation drift found in KnowledgeHub operation.
- [ ] Decide whether drift should be fixed in scaffold generation, diagnostics,
      or project documentation.
- [ ] Add or update tests if Cozy scaffold behavior changes.
- [ ] Record residual drift as follow-up work when not fixed in Phase 12.

## BK12-08: Stage And Upload Workflow Readiness

Status: OPEN

- [ ] Define the KnowledgeHub stage workflow command shape.
- [ ] Define the KnowledgeHub upload workflow command shape.
- [ ] Keep upload provider implementation out of Cozy.
- [ ] Confirm `bok.workflow.stage.command` failure diagnostics are actionable.
- [ ] Confirm `bok.workflow.upload.command` failure diagnostics are actionable.
- [ ] Confirm production publish cannot silently skip upload.

## BK12-09: Development Findings And Follow-Up Backlog

Status: OPEN

- [ ] Record operational findings from KnowledgeHub use.
- [ ] Separate immediate Phase 12 fixes from future-phase candidates.
- [ ] Track local command, config, scaffold, dry-run, stage, and upload workflow gaps.
- [ ] Keep findings tied to reproducible BoK operation commands.

## BK12-10: Phase Closure

Status: OPEN

- [ ] Confirm all BK12 items are complete or explicitly deferred.
- [ ] Confirm KnowledgeHub BoK build verification passes.
- [ ] Confirm dry-run verification passes or is documented as a known issue.
- [ ] Update `docs/phase/phase-12.md` closure section.
- [ ] Set `docs/phase/README.md` active phase to none or the next phase.
- [ ] Mark Phase 12 closed in strategy.
