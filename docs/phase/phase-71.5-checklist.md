# Phase 71.5 Checklist

Phase status: IN-PROGRESS
Updated: 2026-10-08
Ledger for: [Phase 71.5](phase-71.5.md)
Primary owner: Cozy
Predecessor: [Phase 71.4](phase-71.4.md), closed before entry
Repository-full validation: deferred-not-run to PHASE-71.7

P715-01 inventory in progress; no product removal accepted. Include unfinished management-hash removal from
71.4; preserve removals and actual results already obtained there. Completion
is selected removal plus passing existing Executable Specifications for retained
behavior, the working representative route after removal, normal review and commits. Add no removal-proof specs, new test suites
or broader coverage gates. Remaining native integration breadth/full validation belongs to 71.7.
`[>]` means transferred, not passed and not a Phase 71.5 exit condition.

## P715-01: Bounded removal work list and consumer boundary

Execution mapping (2026-10-08): the [plan](phase-71.5.md#execution-plan--2026-10-08)
uses P715-01 once to bound remaining callers and external-reader dependencies,
including unstarted 71.4 S08. Implement P715-02/03/04/05 in connected consumer
batches: first remove protection on the 71.4 route and actually generate/play
its final video; then complete remaining publication/packaging callers, P715-06
and existing closure. Resolve route regressions before expanding. S08's historical distribution
exception is not retained. Rows do not create per-helper review cycles; Step/
Phase acceptance still applies, and all states below remain unchanged.

Stage Status:

- Current status: IN-PROGRESS
- Owner: Cozy
- Update rule: close only against the following accepted inventory outcomes

Selected work list: [Phase 71.5 remaining removal work list](../design/phase-71.5-removal-work-list.md).

- [ ] Existing inventories and actual remaining callers define a finite removal list, including management hashes unfinished in 71.4, retained-integrity rows and non-hash protection. Completed removals/results are reused.
- [ ] Each target identifies the removed behavior, its callers/supporting contracts and any independently meaningful product behavior; the inventory grants no protection exception.
- [ ] Concrete external reader requirements, including the CNCF carrier `sha256` requirement, have an exact producer/consumer disposition; no unsupported producer edit or unauthorized external change is made.
- [ ] Independent Step acceptance review and the exact Step commit record this boundary.

## P715-02: Generated/intermediate protection removal

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only against the following accepted product outcomes

- [ ] Generated/intermediate tamper/integrity/race protection and protection-only models, fields, codecs, diagnostics, configuration and scaffolds are removed with their Cozy-owned callers.
- [ ] Generation/adoption/reuse and actual semantic/production facts work without replacement artifact identities or immutability gates; recovery is regeneration.
- [ ] Affected existing Executable Specifications and compile/consumer coverage succeed; independent Step acceptance and the exact Step commit are recorded.

## P715-03: Publication/distribution protection removal

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only against the following accepted product outcomes

- [ ] Cozy-owned publication/WIP/repository/export/CAR/SAR/subcomponent integrity mechanisms and supporting protection evidence are deleted, including former Phase 71 exceptions.
- [ ] Remaining publication/packaging semantics work with reconciled callers and schemas; no signatures, alternate checksums, snapshots, metadata identities or other protection substitutions are introduced.
- [ ] No concrete external consumer incompatibility is hidden, faked or relabeled as compatible; affected obligations stay open until separately owned reconciliation is actually established.
- [ ] Affected existing Executable Specifications and compile/consumer coverage succeed; independent Step acceptance and the exact Step commit are recorded.

## P715-04: Protection-oriented filesystem machinery removal

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only against the following accepted product outcomes

- [ ] Artifact-protection backup/restore/rollback, atomic replacement/fsync/staging/lock/CAS, repeated reads and copy/install/post-operation byte verification are removed without a replacement protocol.
- [ ] Ordinary selected read/write/copy and I/O failure reporting work; malformed paths are rejected and unselected/authored destinations remain outside the selected mutation scope, without artifact-integrity machinery.
- [ ] Product contracts no longer promise rollback, atomic replacement or prior-generated-output preservation on failure; Git and shared execution locks are unchanged.
- [ ] Affected existing Executable Specifications succeed; independent Step acceptance and the exact Step commit are recorded.

## P715-05: Contract and test simplification

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only against the following reconciled contracts and behavior evidence

- [ ] Design/spec/help/configuration/scaffolds no longer promise removed protection; tests or expectations asserting those guarantees are deleted or amended, not translated into equivalent non-hash protection.
- [ ] Existing generation/adoption/rebuild/packaging/selection and ordinary failure specifications for retained behavior pass. Failures of retained behavior are repaired, not hidden by removing assertions.
- [ ] Standard PNG/ZIP behavior remains interoperable without an additional Cozy protection wrapper.
- [ ] Ordinary diff review checks the selected removal list. No new specs proving hash absence, absence of tamper detection or unchanged inputs, and no new test infrastructure or expanded coverage gates are added.
- [ ] Independent Step acceptance and the exact Step commit record the simplified contracts.

## P715-06: Selected removal completion and integration handoff

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close against the selected removal list and working main route; remaining integration is owned by Phase 71.7

- [ ] P715-01 selected removals and supporting contract updates are complete. Do not restart a whole-codebase discovery/absence-proof cycle after passing batches.
- [ ] No publication/distribution protection exception or alternate implementation of the removed semantics remains.
- [ ] The representative 71.4 route actually generates and assembles video after removal, with audible narration and correct final-card/player EOF. Fix regressions here; recheck only when later changes affect this result.
- [>] Remaining generation/adoption, task-private publication and CAR/SAR packaging coverage is consolidated into P717-02.
- [ ] Independent Step acceptance and the exact Step commit record selected removal completion and its integration handoff.

## Closure

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only when all owned Steps and the following release ledger are satisfied

- [ ] RELEASE-71.5-HANDOFF: Accepted Phase 71.4 ancestry and all owned Step outcomes are recorded; no concrete external contract obligation or unpersisted accepted finding remains unresolved.
- [ ] RELEASE-71.5-REVIEW: Independent Phase review checks semantic substitution and reports zero current Phase blockers.
- [ ] RELEASE-71.5-VALIDATION: Affected existing Executable Specifications for retained behavior pass. Repository-full SBT remains deferred-not-run to P717-04; the representative post-removal native route must pass; no extra removal-proof tests are prerequisites.
- [ ] RELEASE-71.5-AUDIT: The finite removal list is completed and reviewed without replacement protection. Standard codec/domain behavior is retained; no additional exhaustive audit loop is required.
- [ ] RELEASE-71.5-COMMIT: A distinct Phase release commit records the final simplified contract and closure ledger; no successor starts automatically.
