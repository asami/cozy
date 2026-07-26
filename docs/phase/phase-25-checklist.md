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

Status: IN PROGRESS

- [x] Set or confirm a next-development Cozy SNAPSHOT coordinate before local
      publication.
- [x] Run focused Cozy generator tests and compile the Cozy runtime.
- [ ] Publish only the corrected Cozy development SNAPSHOT locally.
- [ ] Regenerate CBD Support with the corrected Cozy runtime and current CNCF
      development SNAPSHOT.
- [ ] Compile CBD Support without retired Entity token API failures.
- [ ] Run `ReviewDiagnosisPersistenceSpec`, including SQLite-backed P8-45
      history/retention coverage.

## ER25-03: Review and Handoff

Status: IN PROGRESS

- [ ] Complete a read-only Cozy review after implementation.
- [ ] Fix all actionable findings, including generated-source and executable
      specification debt.
- [ ] Complete a clean re-review.
- [x] Run `git diff --check` in each modified repository.
- [x] Commit the corrected Cozy development work with required version updates.
- [ ] Record the published coordinate and downstream validation evidence.
- [ ] Return CBD Support Phase 8 to REVIEW_FIX without closing its required
      human confirmation stage P8-60.
