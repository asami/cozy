# Phase 50 Checklist: Logical UI Target-Readiness and Progressive Static Web

## Target-readiness IR

- [ ] Review Phase 43 Logical UI against later iPhone/Android and Flutter needs.
- [ ] Add only target-neutral missing semantics.
- [ ] Admit responsive/window-class intent for compact/medium/expanded.
- [ ] Admit form-factor hints only where semantically useful.
- [ ] Add region adaptation/layout constraints without target coordinates.
- [ ] Add accessibility/focus semantics as needed.
- [ ] Add localization/text source identity as needed.
- [ ] Add target-neutral keyboard/input and semantic control roles as needed.
- [ ] Add target-projection provenance/currentness hooks.
- [ ] Reject Flutter/Dart/Material/Cupertino/widget-specific vocabulary.
- [ ] Preserve exact candidate/accepted Logical UI identity semantics.

## Structural Review HTML

- [ ] Expose all new IR semantics in the deterministic Review HTML.
- [ ] Expose responsive intent and diagnostics.
- [ ] Expose accessibility/localization information.
- [ ] Preserve read-only, self-contained, deterministic behavior.
- [ ] Bind exact candidate/accepted input identity and output receipt.

## Progressive Static Web

- [ ] Generate a deterministic static Web application from Logical UI.
- [ ] Support screen navigation/reachability.
- [ ] Support selection and input.
- [ ] Support admitted interaction patterns.
- [ ] Support local-deterministic validation.
- [ ] Simulate admitted action invocation without claiming server execution.
- [ ] Support loading/success/conflict/unavailable/operation-failed paths.
- [ ] Support retry/recovery paths where admitted.
- [ ] Support deterministic fixture reset/replay.
- [ ] Do not duplicate domain StateMachine, Workflow, authorization, or server
  authority in client fixtures.

## Responsive interactive review

- [ ] Run compact preview.
- [ ] Run medium preview.
- [ ] Run expanded preview.
- [ ] Provide side-by-side review where practical.
- [ ] Diagnose overflow and unreachable required content.
- [ ] Diagnose missing actions/feedback/navigation.
- [ ] Diagnose focus-order and responsive-intent inconsistencies.
- [ ] Keep responsive review as evidence, not semantic authority.

## Feedback and acceptance

- [ ] Review candidate through Review HTML.
- [ ] Review candidate through Progressive Static Web walkthrough.
- [ ] Record explicit reviewer feedback.
- [ ] Revise candidate without treating generated Web files as source.
- [ ] Explicitly accept one exact target-ready Logical UI identity.

## Representative driver

- [ ] Exercise SalesOrder list -> detail navigation.
- [ ] Exercise confirm interaction.
- [ ] Exercise loading state.
- [ ] Exercise success state.
- [ ] Exercise conflict state.
- [ ] Exercise unavailable/service-failure state.
- [ ] Exercise validation and admitted retry/recovery.
- [ ] Preserve UI UseCase and Component provenance.
- [ ] Exercise compact/medium/expanded variants.

## Closure

- [ ] Focused Executable Specifications pass.
- [ ] No Flutter-specific value has leaked into Logical UI authority.
- [ ] Review HTML and Progressive Static Web project the same IR identity.
- [ ] Deterministic identities/currentness/provenance are complete.
- [ ] Independent Phase review has no Current Phase Blocker.
- [ ] Full serialized Cozy validation passes.
- [ ] Closure explicitly leaves Flutter policy/plan/source generation to Phase 51.
