# Entity Revision Generator Downstream Acceptance Transfer

date=2026-08-03
phase=Phase 25
status=transferred

## Decision

Cozy Phase 25 closes the source-level generator alignment implemented in
commit `2a2bced` after the original downstream driver-CAR acceptance attempt
identified a launcher-selection issue outside the Cozy generator contract.

CBD Support's generated `build.sbt` correctly requests `cozy --runtime
0.3.0-SNAPSHOT`, but the active Cozy launcher configuration selects the
development checkout `/Users/asami/src/dev2025/cozy` first. The executed
runtime was `0.3.1-SNAPSHOT`; it expected CNCF `0.5.1` and rejected the
CBD-generated `0.5.1-SNAPSHOT` descriptor with
`CNCF_DESCRIPTOR_TARGET_MISMATCH`.

## Handoff

CBD Support Phase 8 `P8-61` owns deterministic runtime selection and the
remaining `cozyGenerate`, compile, and
`ReviewDiagnosisPersistenceSpec` acceptance. This preserves the descriptor
contract and does not treat an execution-environment mismatch as a request to
restore retired Entity token APIs.

## Preserved evidence

- Cozy `0.3.0-SNAPSHOT` source JAR contains the same `Modeler.scala` as
  commit `2a2bced`.
- The Phase 25 generator executable specifications had already passed before
  the downstream acceptance attempt.
- The clean CBD Support worktree stayed source-clean after the failed
  generation attempt.
