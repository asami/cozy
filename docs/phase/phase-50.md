# Phase 50: Logical UI Candidate Preview and Compact Static Web

status=planned
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-50.1
aggregate_validation_sequence=["PHASE-50","PHASE-50.1"]

Plan date: 2026-09-06

Execution plan updated: 2026-10-08 (main-route-first optimization, then split;
planning only, implementation remains unstarted).

Predecessor: accepted Phase 43 Logical UI Model closure; reuse the accepted
Phase 74 vocabulary and compact runtime fixture without requiring CNCF/Android
integration. Successor: [Phase 50.1](phase-50.1.md), then unchanged Phase 51.

## Goal

Provide a genuine Logical UI candidate preview and prove its generated compact
Progressive Static Web main route: list -> select -> detail -> confirm ->
simulated success. Keep deterministic structural Review HTML aligned to the
exact same declared inputs. This is an independently accepted producer
increment; its closure does not assert full target-readiness or semantic
acceptance of the candidate for Flutter projection.

Candidate/accepted authority, target neutrality, provenance and absence of real
backend/domain execution apply from the first increment. Reuse accepted Phase
43/74 assets. A complete IR inventory, every Review HTML section and the full
outcome/window-class matrix are not prerequisites for the first working route.
Only Phase 50.1 closes the complete original contract and hands an explicitly
accepted target-ready Logical UI identity to Phase 51.

## Execution sequence

| Step / batch | Scope | Observable exit |
| --- | --- | --- |
| S50.1 / E1 | Minimum main-route contract | Genuine candidate preview, exact consumed candidate/screen/semantic input binding, one SalesOrder initial state and explicit simulated success; only missing compact-route IR. Freeze design/spec before implementation. |
| S50.2 / E2 | Actual compact main route and aligned structural HTML | Candidate -> generated app -> actual browser list/select/detail/confirm/success, deterministic outputs and source trace; focused acceptance and independent Phase closure. |

### E1: decide only what the compact main route requires

- Reuse Phase 43 Component bindings, UseCase coverage, screen composition,
  constraints, UI-state separation, diagnostics and SalesOrder semantics.
  Extract shared test fixture construction only when both projections need it;
  preserve the existing acceptance assertions rather than cloning the fixture.
- Reuse Phase 74 `CozyLogicalUiVocabulary`. Inspect
  `CozyLogicalUiSelection` for compact/expanded alignment; its current closed
  intent set has no medium value. Keep compact behavior for the first flow;
  decide medium semantics and the relationship to the public runtime contract
  in E3 before adding those values. Do not copy the runtime
  model into a second semantic authority or require CNCF/Android integration.
- The current `CozyLogicalUiReview.render` requires `AcceptedLogicalUi`.
  Freeze a genuine candidate-review route before E2; never manufacture an
  acceptance decision merely to preview a candidate. Preserve the distinction
  between candidate review and explicitly accepted review.
- Phase 43 screen/semantic projections are separately bound to the candidate.
  Bind the exact inputs used by the first preview and preserve candidate versus
  accepted status. Extend that binding with each E3 semantic addition and freeze
  the complete target-ready acceptance contract before E4 acceptance/Phase 51
  handoff. Do not invent an acceptance decision to obtain a preview or assume
  candidate identity alone covers every extension. Do not expand legacy
  hash-based management as a planning shortcut.
- Identify only the missing IR necessary for the compact main route now.
  Fix its initial fixture state, explicit simulated success outcome, review
  entry/output boundary and trace to admitted UI steps. Use the same declared
  initial fixture for repeatable checks. No live backend or client domain
  executor is introduced.

## Validation and closure

Use one SalesOrder coverage matrix linking UI steps, outcomes, profiles,
checklist IDs and evidence. Start with compact success and carry this same
matrix to Phase 50.1. The distinct narrow [main-route ledger](phase-50-checklist.md)
is the sole Phase 50 completion authority. The 59 original full-outcome IDs
remain verbatim and open exactly once in the [Phase 50.1 ledger](phase-50.1-checklist.md);
Phase 50 evidence is partial coverage, never premature closure of broad rows.

- E1 selects affected `CozyLogicalUiSpec`, `CozyLogicalUiSemanticsSpec`,
  `CozyLogicalUiRuntimeSpec`, `cozyruntime.CozyLogicalUiRuntimeAcceptanceSpec`
  and new candidate contract specs. Historical Phase 74 tests are predecessor
  evidence, not a new result.
- E2 selects `CozyLogicalUiReviewSpec`, `CozyLogicalUiPhase43AcceptanceSpec`
  and new projection/interaction specs; actually walk the generated compact app
  in a browser. Static HTML inspection alone cannot prove interaction.
- Resolve exact new suite names/browser tooling at implementation. Every Step
  requires focused validation, proportionate review, acceptance and its own
  commit; one independent full Phase review with zero Current Phase Blockers,
  a closure ledger and a distinct local release remain required.
- This member is `aggregate-deferred`: record
  `repository_full_suite=deferred-not-run`. Omit only the full serialized Cozy
  suite; focused/browser/review obligations remain. Phase 50.1 owns the one
  aggregate full suite for PHASE-50 and PHASE-50.1. Closure retains pending
  aggregate assurance and cannot claim ordinary full-validation assurance.

Produce a durable successor handoff containing the frozen preview contract and
Executable Specifications, declared candidate/screen/semantic fixture inputs,
both compact projections, actual browser evidence, partial canonical coverage
matrix, native local release and explicit full-suite deferral. Phase 50.1
extends these same assets rather than introducing another semantic authority.

Route SBT through the registered serialized runner during implementation.
Commits remain parent manual local commits under the user's instruction. Use
the normal checkout; local worktree operations remain suspended. No Phase
execution, browser walkthrough, test or commit is performed by this split.

## Non-goals

Detailed input/local validation, loading/failure/recovery/reset/replay,
medium/expanded adaptation, remaining metadata/diagnostics, feedback/revision
and complete target-ready acceptance are Phase 50.1 work. Flutter policy,
Dart/Flutter source generation, real backend execution, platform acceptance,
CAR packaging, publication, deployment and autonomous semantic acceptance
remain outside this producer sequence.

## Phase Plan Gate

Phase Plan Gate: PROCEED
- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: Phase 43 planned 6–8h and Phase 59 planned 7–8h have no admitted actual duration. Phase 65 planned 12h, observed 16.72h (ratio 1.394), is only partially comparable because it includes a real MCP path. No global multiplier or numerical adjustment is justified; retain medium confidence and contract/browser/repair uncertainty.
- planning_demand: protected-decision
- recommended_parent_profile: gpt-6.1-sol / high
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: genuine candidate preview while existing render requires AcceptedLogicalUi; binding the exact minimal candidate/screen/semantic inputs
- frozen_profile_transition_handoff: none; consumes accepted Phase 43/74 foundations
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6.5h expected, 5.5–8.5h uncertainty range; 0.5h above the 6h target and below the 8h expected ceiling. The upper bound is risk evidence, not an additional split trigger. E1 2.5h + E2 3.5h + extra first-Phase review/closure 0.5h.
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none; no child below 4h
- rebalance_attempts_for_every_sub_5h_child: none; no child below 5h
- adjacent_merge_structural_rejection_evidence: merging 50 and 50.1 restores the original 12h expected E1–E4 critical path, exceeding 8h. A third ordinary container would average approximately 4.33h with no semantic boundary or unavoidable balanced remainder; keep contract-to-observable-behavior closure coherent.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: split expected total 13h versus 12h unsplit; approximately 0.5h additional first-Phase review/closure and 0.5h handoff/aggregate accounting. One full suite remains; no time/token saving is claimed. Benefit: two independently closable units below the expected ceiling.
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when supported and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 50 on 2026-10-08

Both containers require protected decisions, so high is the least compatible
GPT-6.1 Sol parent effort. Settled projection, fixture and Executable
Specification work can use medium implementation workers after the owning
contract is frozen. Routine work stays coupled to its contract's actual
browser evidence and independent closure; separate routine tails would be
short fragments without independent acceptance. No parent profile transition
or reasoning-cost-isolation saving is asserted.

## Dated split decision and preserved history

Applied 2026-10-08 by the explicit `$cncf-split-phase 50` invocation:
`PHASE-50 -> PHASE-50.1 -> PHASE-51`. The original 2026-09-06 plan date and
2026-10-08 optimization remain provenance. No Phase 50 Step, Slice, commit or
acceptance had completed; none is moved or fabricated. Accepted Phase 43/74
history is unchanged. The [optimization journal](../journal/2026/10/2026-10-08-phase-50-execution-order-optimization.md)
records the earlier unsplit E1–E4 arrangement and main-route-first rationale.
This dated split supersedes its no-child/one-Phase allocation while retaining
all product requirements and ordering.

Pre-split gate evidence (2026-10-08): `P50-ENTRY-PLANNING-001` revision 1 and
`P50-ENTRY-001` revision 1 returned `SPLIT_REQUIRED`, with 12h expected,
10–16h range, 6h target and 8h ceiling. `split_reasons=[time-bound]`.
The current structural gate above is PROCEED; execution-task capability still
requires re-evaluation. No Goal was created at pre-entry or by this split.

Both children expect 6.5h (5.5–8.5h range), use gpt-6.1-sol/high with standard
parent mode, and retain coupled decision/implementation/assurance scope. Added
review/release/handoff overhead is approximately 1h overall; total-time and
reasoning-cost savings are not claimed. Incoming semantic handoffs are exactly
`PHASE-50: []` and `PHASE-50.1: []`; the normal local release/serial dependency
is not a special semantic boundary. There is no parent profile transition.
The exact ordinary frozen handoff is described in Validation and closure.

The split defaults to final-only / sbt-full-suite / no bootstrap. Phase 50.1
owns aggregate validation for both members. This supersedes the tentative
pre-apply proposal's exhaustive-E1, medium-successor and per-member-full-test
assumptions; the current main-route-first plan retains medium and final
acceptance decisions in E3/E4. All 59 original full outcomes have one owner,
Phase 50.1. The new P50-MAIN rows describe narrower deliverables only.

## References

- [Main-route checklist](phase-50-checklist.md)
- [Complete target-ready successor](phase-50.1.md)
- [Accepted Logical UI predecessor](phase-43.md)
- [Logical UI design](../design/logical-ui-model.md)
- [Original non-normative target projection proposal](../notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md)
