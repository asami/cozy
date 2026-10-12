# Phase 50.1 Checklist: Complete Logical UI Target-Readiness and Static Web

Status: IN_PROGRESS; split from Phase 50 on 2026-10-08. All 59 original outcomes
below retain their IDs and wording and remain open. This is their sole full
completion ledger. Phase 50's [narrow main-route ledger](phase-50-checklist.md)
supplies partial evidence only; at the split, no completed history existed to move.

Current status: IN_PROGRESS
Owner: parent execution task `/root`; explicit acceptance remains with the human reviewer.
Update rule: Update progress from admitted evidence; check an outcome only when
its full evidence and required acceptance gates are satisfied, retaining every
original ID and wording.

E3 candidate implementation, structural/Web projections and browser proof are
implemented. CPB-P501-E3-001 recovery repair passed parent-admitted focused
validation and actual run007: 21 cases, 1067 observations, 23 parent-inspected
images, 12 pointer recoveries from focused input and five focused-recovery
resize walks. Historical run006 PASS remains retained. Independent focused
Step re-review passed and was accepted in review ledger revision 3, resolving
CPB-P501-E3-001; the [accepted re-review checkpoint](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-recovery-rereview-checkpoint-001.json)
records that historical closure. The latest independent integrity focused review
PASS was accepted in review ledger revision 5. S50.1.1/E3 was manually committed
at `84ab85a0984a0286507d63aca906c9a88c923f9b`, as recorded in the
[Step commit checkpoint 002](../../.codex-workflow/phases/PHASE-50.1/p501-s5011-step-acceptance-commit-002.json).
The narrow formatting integrity delta removes blank provenance separator spaces
in the current comparison; all 21 producer tests passed. Run007 remains the
interaction proof, predates that formatting change and is not a new browser
observation of the current comparison bytes. Actual attributed human feedback
「修正不要」 was received on 2026-10-13; the
[feedback record](../evidence/phase-50.1/readiness-candidate-001/feedback.md)
documents the separate no-source-change disposition. On 2026-10-13, the human
user of owning conversation `01a0ff01-4772-76b2-bba1-e2542f0a9647` explicitly said:

> この候補を最終受入れする。Phase 50.1を最後まで続けて。コミットは引き続き親による手動ローカルコミットで。

[The exact acceptance decision](../evidence/phase-50.1/readiness-accepted-001/acceptance.json)
binds complete candidate `sha256:dcb64b218d5921c65be0f1be7b1483565446dfd9fc3dbefd215c202e83f813b1`
and consumed input `sha256:dd1f3b249795e3857bdcb19a72abc2cc205edee8e4d736f8f2db42a2dd08c0a1`.
No semantic source revision is requested. Candidate receipts retain null acceptance.
[The accepted package](../evidence/phase-50.1/readiness-accepted-001/README.md)
retains the generated native four-role declaration export and both current accepted
projections. Two producer suites passed all 21 tests; accepted-binding browser
run002 passed six normal/loading cases, 107 observations and eight parent-inspected
images. Actual native/browser admission passed with cleanup complete. Whole E4
Step review/manual local commit, full Phase review, final full
suite, closure ledger and distinct release remain outstanding.
The [sole coverage matrix](../spec/logical-ui-model.md#11-shared-salesorder-compact-success-contract)
records current candidate proof; all 59 original outcomes below remain unchecked.

The [Phase 50.1 execution sequence](phase-50.1.md#execution-sequence) owns E3/E4.
E3 completes remaining PREP-01/03–04, IR, both projections and responsive
branches. E4 completes exact acceptance binding PREP-02, feedback, complete
driver coverage and closure. Reuse predecessor E1/E2 evidence through the one
coverage matrix; never mark broad rows complete from compact success alone.
Full serialized Cozy validation is final-only / sbt-full-suite, owned here for
PHASE-50 and PHASE-50.1. Both members retain their own focused validation,
independent full Phase review, Step acceptance, closure ledger and release.

## Execution preparation

- [ ] P50-PREP-01: Record Phase 43/74 reuse and gaps, medium-intent disposition,
      and the necessary/conditional disposition of every IR extension.
- [ ] P50-PREP-02: Freeze candidate review without synthetic acceptance and
      the exact target-ready acceptance/input binding in design/spec.
- [ ] P50-PREP-03: Define one SalesOrder fixture, output/entry boundary and
      coverage matrix linking checklist IDs, UI steps, outcomes and profiles.
- [ ] P50-PREP-04: Allocate focused suites, actual browser evidence, per-batch
      review and final full validation without duplicating their responsibilities.

Evidence rule: check a row only when its outcome is proved. For an existing
conditional item (“as needed”, “where practical” or equivalent), retain the row
and record the explicit applicability decision and rationale. Do not silently
waive a required behavior. Historical closure evidence does not mark new work
complete. One evidence record may satisfy multiple explicitly mapped rows.

## Target-readiness IR

- [ ] P50-01-01: Review Phase 43 Logical UI against later iPhone/Android and Flutter needs.
- [ ] P50-01-02: Add only target-neutral missing semantics.
- [ ] P50-01-03: Admit responsive/window-class intent for compact/medium/expanded.
- [ ] P50-01-04: Admit form-factor hints only where semantically useful.
- [ ] P50-01-05: Add region adaptation/layout constraints without target coordinates.
- [ ] P50-01-06: Add accessibility/focus semantics as needed.
- [ ] P50-01-07: Add localization/text source identity as needed.
- [ ] P50-01-08: Add target-neutral keyboard/input and semantic control roles as needed.
- [ ] P50-01-09: Add target-projection provenance/currentness hooks.
- [ ] P50-01-10: Reject Flutter/Dart/Material/Cupertino/widget-specific vocabulary.
- [ ] P50-01-11: Preserve exact candidate/accepted Logical UI identity semantics.

## Structural Review HTML

- [ ] P50-02-01: Expose all new IR semantics in the deterministic Review HTML.
- [ ] P50-02-02: Expose responsive intent and diagnostics.
- [ ] P50-02-03: Expose accessibility/localization information.
- [ ] P50-02-04: Preserve read-only, self-contained, deterministic behavior.
- [ ] P50-02-05: Bind exact candidate/accepted input identity and output receipt.

## Progressive Static Web

- [ ] P50-03-01: Generate a deterministic static Web application from Logical UI.
- [ ] P50-03-02: Support screen navigation/reachability.
- [ ] P50-03-03: Support selection and input.
- [ ] P50-03-04: Support admitted interaction patterns.
- [ ] P50-03-05: Support local-deterministic validation.
- [ ] P50-03-06: Simulate admitted action invocation without claiming server execution.
- [ ] P50-03-07: Support loading/success/conflict/unavailable/operation-failed paths.
- [ ] P50-03-08: Support retry/recovery paths where admitted.
- [ ] P50-03-09: Support deterministic fixture reset/replay.
- [ ] P50-03-10: Do not duplicate domain StateMachine, Workflow, authorization, or server
  authority in client fixtures.

## Responsive interactive review

- [ ] P50-04-01: Run compact preview.
- [ ] P50-04-02: Run medium preview.
- [ ] P50-04-03: Run expanded preview.
- [ ] P50-04-04: Provide side-by-side review where practical.
- [ ] P50-04-05: Diagnose overflow and unreachable required content.
- [ ] P50-04-06: Diagnose missing actions/feedback/navigation.
- [ ] P50-04-07: Diagnose focus-order and responsive-intent inconsistencies.
- [ ] P50-04-08: Keep responsive review as evidence, not semantic authority.

## Feedback and acceptance

- [ ] P50-05-01: Review candidate through Review HTML.
- [ ] P50-05-02: Review candidate through Progressive Static Web walkthrough.
- [ ] P50-05-03: Record explicit reviewer feedback.
- [ ] P50-05-04: Revise candidate without treating generated Web files as source.
- [ ] P50-05-05: Explicitly accept one exact target-ready Logical UI identity.

## Representative driver

- [ ] P50-DR-01: Exercise SalesOrder list -> detail navigation.
- [ ] P50-DR-02: Exercise confirm interaction.
- [ ] P50-DR-03: Exercise loading state.
- [ ] P50-DR-04: Exercise success state.
- [ ] P50-DR-05: Exercise conflict state.
- [ ] P50-DR-06: Exercise unavailable/service-failure state.
- [ ] P50-DR-07: Exercise validation and admitted retry/recovery.
- [ ] P50-DR-08: Preserve UI UseCase and Component provenance.
- [ ] P50-DR-09: Exercise compact/medium/expanded variants.

## Closure

- [ ] P50-CLOSE-01: Focused Executable Specifications pass.
- [ ] P50-CLOSE-02: No Flutter-specific value has leaked into Logical UI authority.
- [ ] P50-CLOSE-03: Review HTML and Progressive Static Web project the same IR identity.
- [ ] P50-CLOSE-04: Deterministic identities/currentness/provenance are complete.
- [ ] P50-CLOSE-05: Independent Phase review has no Current Phase Blocker.
- [ ] P50-CLOSE-06: Full serialized Cozy validation passes.
- [ ] P50-CLOSE-07: Closure explicitly leaves Flutter policy/plan/source generation to Phase 51.
