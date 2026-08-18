# CNCF-originated Compatibility Naming Hygiene Follow-up

status=open
date=2026-08-19
source_repository=cloud-native-component-framework

This is Cozy's owning implementation record for a nonblocking naming item
found during the CNCF Phase 57.4 review. It does not change CAR publication,
CML source-selection, or Phase closure behavior.

## HYG-P57.4-001 — CML source-result public-field migration

- Status: OPEN
- Source record:
  `cloud-native-component-framework:docs/journal/2026/08/2026-08-14-phase-57.4-hygiene-follow-up.md`.
- Repository/path: `cozy`; `src/main/scala/cozy/archive/CarCmlSourceResolver.scala`,
  `Resolved.projectrelativepath`.
- Evidence: the field is consumed by Cozy publication, review, and lint paths,
  with direct executable evidence in
  `src/test/scala/cozy/archive/CarCmlSourceResolverSpec.scala`.
- Risk: package/public source compatibility and naming consistency; no
  demonstrated publication behavior defect.
- Boundary: make `projectRelativePath` canonical, migrate Cozy callers, and
  retain an explicit deprecated accessor and named-argument compatibility only
  where the `Resolved` surface requires it. Preserve the resolved path value,
  CML source precedence, CML metadata, and published output.
- Required validation: run the direct CML source resolver specification, review
  every `Resolved` caller, and run the full Cozy validation selected by the
  later implementation task.
- Non-goals: CML source-selection redesign, CAR metadata format changes,
  repository publication changes, or unrelated naming cleanup.

## Development Candidate Triage

Candidate Triage: COMPLETED
Canonical ID: DEV-001
Source ID: HYG-P57.4-001
Disposition: NEW_PHASE
Strategy Record: docs/strategy/cozy-development-strategy.md#9-development-item-status
Target Phase: docs/phase/phase-32.md
Triaged On: 2026-08-19

## Resolution status

Status: IMPLEMENTED; validation and commit evidence remain pending parent closure.

The user explicitly authorized this non-compatible source-API migration. No
deprecated accessor, constructor, or named-argument compatibility aliases were
introduced.
