# Phase 25 Checklist

This checklist is the authoritative progress ledger for Phase 25: Entity
Revision Generator Alignment.

## ER25-01: Contract and Generation Update

Status: DONE

- [x] Record the retired token source shapes and the canonical SimpleEntity
      managed-revision replacement.
- [x] Generate standard SimpleEntity read responses through its embedded
      revision-aware record projection without a manual field upsert.
- [x] Remove `cncfRevision` from generated SimpleEntity save and update request
      models and parameters.
- [x] Generate ordinary SimpleEntity saves and updates without application-side
      `EntityRevision` reconstruction.
- [x] Preserve the declared operation names while removing the obsolete
      revision request contract.
- [x] Update generated-source Executable Specifications for read, save, and
      update operations.
- [x] Prove generated SimpleEntity source contains neither
      `EntityMutationExpectation`, `snapshot.token`, nor a revision request
      parameter.

## ER25-02: Downstream Regeneration Acceptance

Status: DONE — downstream execution acceptance transferred to CBD Support
Phase 8 `P8-61`; it is not represented as a successful Cozy-side regeneration.

- [x] Confirm the development-only Cozy `0.3.0-SNAPSHOT` source artifact and
      its `Modeler.scala` identity against the Phase 25 implementation commit.
- [x] Attempt the declared CBD Support generation in a clean worktree and
      record the effective launcher runtime and descriptor mismatch without
      weakening the descriptor contract.
- [x] Transfer regenerated-CAR compilation and
      `ReviewDiagnosisPersistenceSpec` acceptance to CBD Support Phase 8
      `P8-61`; no successful downstream acceptance is claimed here.

## ER25-03: Review and Handoff

Status: DONE

- [x] Complete a read-only Cozy review after implementation.
- [x] Fix all actionable findings, including generated-source and executable
      specification debt.
- [x] Complete a clean re-review.
- [x] Run `git diff --check` in each modified repository.
- [x] Commit the corrected Cozy development work with required version updates.
- [x] Record the development source coordinate and the downstream execution
      evidence, including its transfer rather than a false success claim.
- [x] Track the remaining acceptance as CBD Support Phase 8 `P8-61` while
      preserving independently deferred human confirmation P8-60.

Phase 25 is closed from this completed ledger. Successful CBD Support
regeneration, compilation, and SQLite persistence acceptance remain explicit
P8-61 work and are not evidence supplied by this phase.
