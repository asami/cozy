# Phase 75 split proposal

Recorded: 2026-10-06. Parent-owned, documentation-only partition decision.
Non-normative evidence; the Phase plans/checklists own current work state.
This proposal was frozen before document edits. No product execution is claimed.

```json
{
  "source_phase": "PHASE-75",
  "execution_mode": "apply",
  "split_full_test_policy": "each-phase",
  "split_full_validation_method": "sbt-full-suite",
  "invocation_authority": {
    "task_id": "/root",
    "source": "[$cncf-split-phase](/Users/asami/src/development-workstation/common/codex/skills/cncf-split-phase/SKILL.md) 75",
    "timing_opt_in": "full testの方針にして。"
  },
  "source_goal_and_current_status": "none / no-goal-pre-entry; Phase 75 in-progress preservation history retained",
  "split_planning_capability_result": "PROCEED",
  "split_planning_profile_evidence_and_current_selection": {
    "model": "gpt-6.1-sol",
    "reasoning_effort": "high",
    "source": "same-task user-selected configuration",
    "user_text": "gpt-6.1 sol highにしてみた。\nもう一度調査して。",
    "runtime_observation": "not directly observable",
    "selected_record": "none requested"
  },
  "split_planning_demand_and_direct_tool_observations": {
    "demand": "bounded-settled",
    "basis": "Owner-selected warehouse convention and two delivery outcomes; only partition and validation timing, not product architecture, are decided here.",
    "tools": [
      "collaboration.spawn_agent",
      "collaboration.followup_task",
      "collaboration.wait_agent"
    ],
    "observation": "present as direct tools; no probe spawned",
    "plan_owner_fields": {
      "plan_owner_lease": "not_applicable",
      "plan_owner_state": "not_applicable",
      "plan_owner_action": "not_applicable",
      "plan_owner_logical_role": "not_applicable",
      "plan_owner_agent_type": "not_applicable",
      "plan_owner_agent_id": "not_applicable",
      "plan_owner_scope": "not_applicable",
      "plan_owner_reason": "not_applicable",
      "plan_owner_valid_while": "not_applicable"
    }
  },
  "gate_estimate_and_critical_path": {
    "pre_split_outcome": "SPLIT_REQUIRED",
    "entry_reference": "f1c8a2bc-c530-4d97-a1fb-f53b2c063d15 revision 1",
    "expected_minutes": 720,
    "upper_minutes": 1080,
    "critical_path": "preservation/associations -> warehouse contract/migration/placement/public retrieval -> article cards/page ownership; producer effective-content evidence -> exact dependency refresh -> consumer/site verification"
  },
  "packing_target_and_ceiling": {
    "target_minutes": 360,
    "ceiling_minutes": 480
  },
  "estimate_calibration_evidence": "No comparable planned/actual timing evidence. Bottom-up estimates, low confidence; no observed ratio or multiplier invented. Reestimated closure overhead after each-phase opt-in.",
  "split_reasons": [
    "time-bound"
  ],
  "completed_history_that_must_remain": [
    "2026-09-30 renumbering from uncommitted local 74",
    "2026-10-05 priority and actual 71.4 pause",
    "28,266 recovery copies / 11,851,725,488 bytes / zero copy errors",
    "341 video / 201 PDF / 97 actual slide retrieval checks and 12 Office companion classification",
    "owner warehouse and path decisions on 2026-10-06; all checked ledger entries and canonical journals"
  ],
  "child_phase_identities_in_order": [
    "PHASE-75",
    "PHASE-75.1"
  ],
  "scope_and_closure_result_per_child": {
    "PHASE-75": {
      "scope": [
        "COZY-75-10",
        "COZY-75-09"
      ],
      "closure": "Selected historical/current artifacts preserved or explicitly owner-dispositioned; reusable four-role warehouse contract and executable mapping, migrated declarations, local and remote retrieval, YouTube URLs, untracked payloads; independent full validation/review/release. Does not wait for successor site fixes."
    },
    "PHASE-75.1": {
      "scope": [
        "SDX-75-01",
        "SDX-75-02",
        "SDX-75-03",
        "SDX-75-04",
        "COZY-75-04",
        "COZY-75-05",
        "COZY-75-06",
        "COZY-75-07",
        "COZY-75-08"
      ],
      "closure": "Effective-content/Markdown/include producer and exact consumer compatibility, Kroki/CSS/Arcadia regressions, JA/EN media-card and intended page browser evidence; independent full validation/review/release."
    }
  },
  "step_slice_and_validation_ownership_per_child": {
    "PHASE-75": "Preserve existing 10/09 IDs and completed entries. S01 select scope/resolve preservation; S02 freeze/promote contract and implement mappings; S03 bounded placement/publication/declaration migration; S04 focused and full validation, independent review and release.",
    "PHASE-75.1": "Keep original SDX/COZY IDs, no completed work moved. S01 trace card bindings using predecessor URLs; S02 effective-content producer and exact refresh; S03 site adapter/page fixes and browser checks; S04 focused and full validation of Cozy and admitted changed producer repositories, independent review and release."
  },
  "dependency_and_human_gate_order": "PHASE-75 -> PHASE-75.1; keep former later numeric phases unchanged. Resolve only missing final editions, gap disposition, YouTube channel/visibility or destination with owner before affected writes. Concrete producer prerequisite discovered in 75 is recorded/resolved, never silently substituted or imported from successor. Each phase freezes its exact participating roots and native validation coverage at execution entry.",
  "per_child_estimate_and_packing_fit": {
    "PHASE-75": {
      "expected_minutes": 390,
      "uncertainty_range_minutes": [
        300,
        600
      ],
      "components": {
        "preservation_associations": 90,
        "contract_mapping_specs": 120,
        "placement_publication_declarations": 90,
        "validation_review_release": 90
      },
      "fit": "6.5h expected, near 6h target, <=8h ceiling; upper-bound risk does not itself trigger splitting"
    },
    "PHASE-75.1": {
      "expected_minutes": 450,
      "uncertainty_range_minutes": [
        360,
        660
      ],
      "components": {
        "producer_and_exact_consumer": 150,
        "cards_and_site_adapter_page_fixes": 180,
        "browser_full_validation_review_release": 120
      },
      "fit": "7.5h expected, <=8h ceiling"
    }
  },
  "merge_attempts_for_every_sub_4h_child": "No selected child below 4h. Rejected three-way alternative: producer/consumer candidate ~3.5h plus site/cards candidate ~4h; merge is coherent and ~7.5h <=8h, therefore mandatory.",
  "rebalance_attempts_for_every_sub_5h_child": "No selected child below 5h. Reviewed moving card rendering into first child; rejected because it couples first closure to unsettled producer/CSS/page-generation prerequisites. Declaration/URL migration stays in 75; card rendering/browser acceptance in 75.1.",
  "adjacent_merge_structural_rejection_evidence": "Merging selected children restores the complete original scope: 12h pre-split, 14h including selected each-phase overhead; exceeds 8h irrespective of model cost. No authority/semantic conflict or cheaper-profile claim used.",
  "profile_cost_only_rejection_forbidden": true,
  "incoming_semantic_handoffs_per_child": {
    "PHASE-75": [],
    "PHASE-75.1": []
  },
  "overhead_and_tradeoff_evidence": "Estimated +120min over pre-split 720min: extra full-validation/review/release and handoff preparation. 840min total expected. No monetary/token savings claimed; benefit is an independently closable warehouse delivery within 8h. No further micro-phases.",
  "estimated_range_and_recommended_parent_profile_per_child": {
    "PHASE-75": "390min, 300–600min uncertainty; gpt-6.1-sol/high, protected-decision",
    "PHASE-75.1": "450min, 360–660min uncertainty; gpt-6.1-sol/high, open-ended-discovery"
  },
  "agent_reasoning_mode_policy_per_child": "standard by default; consider pro only at eligible launch with supported interface and frozen quality-first evidence",
  "reasoning_cost_optimization_result": "No reasoning-cost-isolation reason: both containers need high parent reasoning until their own decision/diagnosis handoff. Settled downstream worker scope can use Sol medium without a new Phase. Qualitative policy, no fabricated prices or multipliers.",
  "expensive_reasoning_kernels_and_owning_children": {
    "PHASE-75": "Freeze four-role adapter/declaration migration and edition-preserving update boundary in existing design/spec; actual missing article/edition and destination decisions remain explicit owner inputs.",
    "PHASE-75.1": "Freeze effective-content producer API/physical-logical/include/Markdown interaction; diagnose actual card binding and page-composition regressions."
  },
  "lower_cost_scope_and_recommended_profiles": "After in-child frozen handoffs, bounded mapping/parser/adapter/spec implementations: gpt-6.1-sol/medium; exact inventory/commands use their registered low/medium runners. Routine work stays with the atomic acceptance surface rather than micro-phase overhead.",
  "profile_transition_handoffs": "No parent-profile transition between children. 75 provides accepted role/article/locale declarations, actual public URLs, preservation and gap dispositions; 75.1 consumes them. Ordinary dependency, not a genuine semantic split edge.",
  "profile_distribution_and_tradeoffs": "Both parents Sol high; no routine max/xhigh upgrade or price saving claimed. Medium frozen implementation work remains within the same independent delivery.",
  "files_to_create": [
    "docs/phase/phase-75.1.md",
    "docs/phase/phase-75.1-checklist.md",
    "docs/notes/phase-75-split-proposal.md"
  ],
  "files_to_update": [
    "docs/phase/phase-75.md",
    "docs/phase/phase-75-checklist.md",
    "docs/phase/README.md",
    "docs/strategy/cozy-development-strategy.md",
    "docs/phase/phase-71.4.md",
    "docs/phase/phase-71.5.md",
    "docs/phase/phase-71.7.md"
  ],
  "references_that_must_not_change": "Canonical journals, completed IDs/results/approvals, Phase 74 identity/closure, Phase 71 technical scope and aggregate owner 71.7, later Phase numbers, artifact versions/SNAPSHOT coordinates and product code.",
  "collision_scan_result": "No phase-75.1/75.2 authoritative document, title or index reference found. There is no phase-76 document; do not fabricate a successor or shift later identities. Sequence inserted directly after retained 75 in the Phase index and strategy.",
  "non_goals": "No product edits, SBT/runtime tests, deployment/publication, Git stage/commit/push, Goal operation, child start, historical status reset, hash controls or successor renumbering."
}
```

## Static validation result

Documentation-only validation completed on 2026-10-06:

- The required `plan-split` and `validate-split` helper calls returned
  `status=accepted` for `PHASE-75 PHASE-75.1`, with
  `--full-test-each-phase --validation-method sbt-full-suite` and the exact
  Cozy repository root. Existing `status=in-progress` is retained in 75;
  new 75.1 is `status=planned`. Neither has aggregate/deferred declarations.
- All 11 original work IDs have one work-outline and one stage-ledger owner:
  10/09 in 75; SDX-01–04 and COZY-04–08 in 75.1. Every original checklist
  clause remains in one ledger; all five actual checked records remain in 75.
  The original historical priority/handoff text remains in 75 and is explicitly
  labeled pre-split. No completion or test result was manufactured.
- Each child has one Phase document, checklist, index entry, strategy entry and
  current structural `PROCEED` gate. The sequence is 75 then 75.1; no conflicting
  prior child was present and no later numeric Phase was renamed. Original
  `SPLIT_REQUIRED` is explicitly dated pre-split evidence, not the current gate.
- Both expected durations exceed five hours and are below eight hours. No
  selected short child exists. The coherent sub-four-hour producer alternative
  was merged with site work; merging the final two containers would exceed
  eight hours. Genuine incoming semantic edges are empty for both children;
  ordinary declaration/URL and in-child producer handoffs are named explicitly.
- All 139 local document-link targets inspected across the selected planning
  set exist. Current priority/consumer references name the moved 75.1 work.
  Original journals, historical pause prose and unrelated technical exclusions
  still naming unsplit Phase 75 are retained historical/umbrella references,
  not a second current work owner.
- Tracked planning-file `git diff --check` and untracked-file
  `git diff --no-index --check /dev/null <file>` checks produce no whitespace
  diagnostics. For no-index checks, exit 1 denotes the expected new-file diff,
  not a whitespace failure. Existing untracked 71.5/71.7 plans receive only
  the priority/consumer reference corrections and are checked separately.

No SBT, full test, runtime validation, independent product review, publication,
deployment, Goal operation, child execution, stage, commit or push was performed.
Existing journals and Phase 71 technical/aggregate-validation ownership remain.
The planning diff is uncommitted and may be frozen at Phase 75 entry, then saved
in its normal acceptance/management-data commit. No prerequisite split commit
is required.
