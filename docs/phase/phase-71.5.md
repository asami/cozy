# Phase 71.5: Management Hash and Artifact Protection Removal

status=in-progress
depends_on=phase-71.4.md
execution_priority=after_phase_71.4_closure
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-71.7
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4","PHASE-71.5","PHASE-71.6","PHASE-71.7"]

Status: in-progress, P715-01 inventory implementation pending acceptance
Planned at: 2026-10-03
Primary owner: Cozy
Predecessor: [Phase 71.4](phase-71.4.md).
Planned successor: [Phase 71.6](phase-71.6.md), per-tool execution and video
encoder selection, only after this Phase closes.
Completion ledger: [Phase 71.5 Checklist](phase-71.5-checklist.md).
Policy: [Artifact protection removal](../design/artifact-protection-removal.md).
Selected work list: [Phase 71.5 remaining removal work list](../design/phase-71.5-removal-work-list.md).

## Goal

Remove remaining management hashes and all Cozy-owned tamper/contamination and generated-artifact protection,
including publication/distribution integrity exceptions. Delete the behavior,
not just hash calls; introduce no replacement protection mechanism.

## Execution order and entry

After the user's Phase 75 priority (75 → [75.1](phase-75.1.md), as split on
2026-10-06), complete Phase 71.4's video implementation
and representative real generation/playback, then start Phase 71.5 on that accepted tree.
Keep all removals already implemented in Phase 71.4 and their actual results;
do not revert, reimplement or repeat accepted reviews merely to relocate work.
Unfinished management-hash removal and its remaining inventory move here.

Completion requires the selected removals and passing existing Executable
Specifications for retained behavior and the same working representative native
route after removal, with normal review/commits. Broader integration and
repository-full validation are deferred to Phase 71.7.
Neither this inventory nor this implementation is a Phase 71.4 closure condition.
Do not reopen predecessors or rewrite their historical acceptance evidence.
No successor is started by this planning update or by a predecessor closure.
Phase 71.6 owns its new configuration and mixed pipeline; Phase 71.7 owns the benchmark;
none of that work is part of this removal Phase or required for its closure.

## Execution plan — 2026-10-08

Use the accepted 71.4 tree and the existing removal policy. P715-01..06 remain
the ownership/checklist IDs; the following batches group connected implementation
and existing Specs rather than creating new review gates.

1. **Bound the work and external edges once (P715-01).** Reuse the existing
   inventory and accepted removals. Include unstarted 71.4 S08 as remaining
   Document Project work, replacing its historical distribution-retention
   instruction with the current policy. Identify the CNCF carrier and sbt-cozy
   descriptor readers before changing their producer fields. Record concrete
   separately owned changes early; do not discover them only at release.
2. **Keep the main route working while removing protection (P715-02/03/04/05).**
   Start with document/media/video generation, currentness and evidence callers
   on the actual 71.4 route. Include directly shared
   model/codec/filesystem changes in the same compile-valid batch, even if they
   span these labels. Delete protection-only fields/operations/expectations
   together, retaining format/domain/path behavior. Do not implement replacement
   protection or a compatibility layer merely to make staged deletion easier.
   Run the same representative through normal generation, final assembly and
   audible playback after these connected removals, before expanding to the
   remaining publication/export/CAR/SAR readers. Fix route regressions in this
   batch, not by handing a broken route to 71.7. Then complete the rest of the
   finite list, keeping that route usable; recheck only where later edits affect
   it. A concrete changed external reader needs its affected connection check.
3. **Close the finite removal scope (P715-06 and release).** Use affected existing
   Specs for retained behavior and review the actual diff against the selected
   list. Complete required Step/Phase reviews and commits at the existing
   boundaries; shared validation/review evidence may cover multiple tracking
   items where it actually does. Do not add another whole-codebase absence audit,
   new removal-proof test suite or full packaging matrix before closure. Include
   the actual post-removal main-route result in ordinary Phase acceptance;
   do not add a review after each deletion or native run.

If an external reader blocks one boundary, preserve that explicit open item
and its required separate ownership while continuing independent compile-valid
removals. Do not fabricate a digest, silently retain protection or claim the
whole Phase closed. Reuse prior actual results for unaffected inputs; a relevant
producer change needs its affected consumer checks. P717-02 receives remaining
generation/adoption/publication/packaging breadth and P717-04 the aggregate suite.
The same route and inputs handed over by 71.4 are reused; no new generic test
harness, file-copy comparison or unchanged-content certificate is introduced.

## Scope and owned Steps

### P715-01: Bounded removal work list and consumer boundary

Inventory Cozy source/resources, tests, design/spec, help, configuration and
scaffolds. Include every Phase 71 retained-integrity row and hash-free protection
such as copy comparisons, snapshots, race checks, rollback and atomic install.
Identify concrete callers, supporting fields and independent product semantics.
Use the actual Phase 71.4 tree and existing inventories/results to list concrete
remaining removals and callers once. Include unfinished management-hash work
transferred from P710-04-INTEGRATION. Reuse completed work. Review the selected
changes against this finite list; do not add a repeated all-codebase absence
proof, new comparison ledger or expanding quality gate as a closure condition.

Record concrete external contract dependencies before producer deletion,
including the known CNCF carrier field requirement described in the policy.
No preservation exception or external mutation is authorized by this inventory.

### P715-02: Generated/intermediate protection removal

Delete protection from document, media, video, UI, modeler, currentness, review,
receipt and provenance paths. Reconcile protection-only supporting models,
fields, codecs, diagnostics and callers in compile-valid changes. Preserve
actual source/semantic/production facts only for their independent product use.

### P715-03: Publication/distribution protection removal

Delete Cozy-owned protection from publication, WIP, repositories, CAR/SAR,
subcomponent releases, export and distribution. Remove all former retained
integrity mechanisms, including supporting metadata and admission checks.
Publication placement does not grant an exception. Reconcile Cozy-owned readers
and use the policy's concrete external-consumer stop boundary where necessary.

### P715-04: Protection-oriented filesystem machinery removal

Remove artifact-protection backup/restore/rollback, atomic replacement, fsync,
locks, CAS and post-operation/repeated byte verification. Use ordinary
read/transform/write/copy operations and ordinary failure reporting. Preserve
selected path/ownership boundaries; do not claim generated-output rollback or
unchanged prior outputs on failure. Do not alter Git or shared execution locks.

### P715-05: Contract and executable-specification simplification

Update affected design/spec/help, configuration and scaffolds with each change.
Keep existing Executable Specifications for retained behavior. Delete or amend
only expectations whose protection behavior is intentionally removed. Run the
affected existing generation, adoption, packaging, format/semantic selection
and I/O failure specifications; fix failures of retained behavior rather than
deleting those assertions to obtain a pass.
Valid selected artifact edits do not trigger tamper/mismatch rejection.
Standard PNG/ZIP codecs still produce interoperable files.
Do not add specifications proving hash absence, absence of tamper detection or
unchanged inputs. Do not introduce new test suites, verification infrastructure
or broader coverage requirements for this removal-only Phase. Ordinary review
of the removal diff and existing specifications is sufficient.

### P715-06: Selected removal completion and integration handoff

Review the completed changes against P715-01's concrete work list and the
no-replacement policy. Do not restart discovery after each passing batch.
Record actual representative generation/final-build/playback after removal
under P715-06. Remaining adoption, task-private publication and CAR/SAR packaging
breadth transfers to P717-02; do not defer a defect in the representative route.
New unrelated feature/quality requests remain separate work.

## Validation and independent closure

Each implementation batch consumes the inventory and keeps its directly
affected Cozy callers compile-valid. Run the affected existing Executable
Specifications for retained behavior. Record actual command/process results and
independent acceptance review; do not treat the plan or an audit search as
execution proof.

After selected removals, passing existing specifications and the working real
representative route, complete the
normal independent review and Phase release commit. No additional removal-proof
tests, final whole-codebase audit cycle, broad native matrix or standalone full
suite is required here. The one aggregate full suite belongs to Phase 71.7;
its result remains deferred-not-run. Concrete unresolved external contract items
remain open and prevent an unqualified all-removal closure claim.

## Non-goals and ownership

Only Cozy is the mutation repository. No external source, skill, CNCF,
sbt-cozy, production site, warehouse, remote publication, deployment or GitHub
write is authorized. Real project drivers use task-private copies/outputs.
Do not redesign storage/publication, invent a lifecycle/receipt/provenance
framework, strengthen protection before deleting it or preserve protection
compatibility. Ordinary format, path and domain validation are not removal
targets merely because they reject invalid data.

## References

- [Phase 71.4](phase-71.4.md)
- [Phase 71.7 integration acceptance](phase-71.7.md)
- [Historical Phase 71 digest-purpose inventory](../design/phase-71-digest-purpose-inventory.md)
- [Current file update contract](../spec/file-update-management.md)
- [GitHub plan adoption and sequencing](../journal/2026/10/2026-10-03-phase-71.5-removal-plan-adoption.md)
