# Phase 71 completion boundaries and integrated acceptance

Date: 2026-10-05 (Asia/Tokyo)
Status: user-directed planning revision; no product execution or closure claimed

## Decision

The user observed that 71.4 kept accumulating removal, tests and acceptance work.
They selected the following completion boundaries and explicitly requested their
reflection in the affected Phase documents. Phase 75 has started per user report;
its priority and scope are unchanged.

| Phase | Product completion boundary |
| --- | --- |
| [71.4](../../../phase/phase-71.4.md) | Video-ending/narration implementation and corresponding passing Executable Specifications. Keep existing removals; removal completion is not an exit condition. |
| [71.5](../../../phase/phase-71.5.md) | Remaining management-hash and artifact-protection removal; existing Executable Specifications for retained behavior pass. |
| [71.6](../../../phase/phase-71.6.md) | Per-tool execution/encoder and mixed-pipeline implementation, with corresponding passing Executable Specifications. |
| [71.7](../../../phase/phase-71.7.md) | Real integration, listening/player acceptance, existing native benchmark and one aggregate repository-full suite. |

Normal reviews and commits remain for each revised scope. Reuse accepted work;
do not undo completed removals, repeat accepted reviews just for relocation,
reset Goals/bases/PLAN epochs or erase failures/repair history. No completion
boxes are checked by this planning change. The execution task owns runtime state.

71.5 does not add absence/protection-removal proof tests, new test infrastructure
or broader coverage demands. Keep retained-behavior Specs; delete or amend only
obsolete protection expectations. A failing retained function must be repaired,
not hidden by removing its Spec. Review the finite removal work list normally;
do not repeatedly restart exhaustive discovery after passing batches.

## Ownership transfer

P710-04-INTEGRATION's remaining-removal inventory moves to P715-01/06; its real
media/site acceptance and P710-F04 listening/player work move to P717-01/02.
P715-06 real packaging/generation acceptance moves to P717-02. P716-04 and native
hardware/mixed acceptance move to P717-03. The still pending 71–71.4 aggregate
suite and planned 71.5/71.6 standalone suites consolidate into one P717-04 suite.
Current declarations across 71–71.7 name PHASE-71.7; historical committed
handoffs/results are preserved and cannot be mistaken for actual full passes.

Sequence after Phase 75: 71.4 -> 71.5 -> 71.6 -> 71.7. Phase 75 remains outside
the aggregate chain. 71.7 uses finite existing scenarios and fixes actual
integration defects without reopening completed Phases. New unrelated work
stays separate. Use ordinary Git changes, execution/test logs and review;
introduce no hashes, comparison copies or unchanged-input proofs.

## Change boundary

Only planning documents, checklists, navigation/strategy and the referenced
removal-policy validation/transition text are changed. No product source,
Executable Specification implementation, workflow runtime ledger, Goal, commit,
deployment or running Phase 75 task is changed by this update.
