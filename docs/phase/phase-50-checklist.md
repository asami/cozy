# Phase 50 Checklist: Flutter UI Projection

## P50-01 Flutter Target Policy

- [ ] Freeze versioned Flutter Target Policy schema and identity.
- [ ] Keep theme/design tokens, navigation strategy, responsive thresholds,
  feedback presentation, and target implementation choices out of Logical UI.
- [ ] Admit `compact`, `medium`, and `expanded` window classes.
- [ ] Admit `phone`, `foldable`, `tablet`, and `desktop` form-factor metadata
  without using commercial device names as semantic identities.
- [ ] Add Executable Specifications for deterministic policy loading and stale
  identity rejection.

## P50-02 Flutter UI Plan

- [ ] Freeze a versioned Flutter UI Plan schema.
- [ ] Represent shell, navigation, pages, regions, layout roles, semantic
  control roles, data/edit bindings, action bindings, UI-local state,
  validation/feedback, responsive arrangement, localization, accessibility,
  and provenance.
- [ ] Bind exact accepted Logical UI and target Policy identities.
- [ ] Preserve reverse provenance to Logical UI screen/interaction, UI UseCase
  Step, and Component identity.
- [ ] Reject target plans that redefine server/domain authority or bypass
  Aggregate/Operation boundaries.
- [ ] Add currentness and deterministic plan identity evidence.

## P50-03 Flutter Base Generation

- [ ] Generate deterministic Dart/Flutter base source from the plan.
- [ ] Keep generated files fully generator-owned.
- [ ] Provide explicit future completion seams; do not merge arbitrary human
  edits into generated files.
- [ ] Ensure concrete widget selection follows Policy/renderer rules rather
  than contaminating Logical UI.
- [ ] Run `dart format`.
- [ ] Run `flutter analyze`.
- [ ] Add focused generator and widget tests.

## P50-04 Minimum Responsive Web Proof

- [ ] Generate/run a Flutter Web proof for the SalesOrder fixture.
- [ ] Exercise compact viewport.
- [ ] Exercise medium viewport.
- [ ] Exercise expanded viewport.
- [ ] Prove responsive layout decisions are driven by target Policy/UI Plan.
- [ ] Retain exact Logical UI, Policy, Plan, and generated-source identities.
- [ ] Do not claim Web proof as iOS/Android acceptance.

## Representative Driver

- [ ] Order list renders and supports logical selection.
- [ ] Order detail renders admitted Component data.
- [ ] Confirm action binds the admitted public Operation.
- [ ] Loading state is represented.
- [ ] Success feedback is represented.
- [ ] Conflict feedback is represented.
- [ ] Unavailable/service-failure feedback is represented.
- [ ] Validation binding is represented.
- [ ] A lifecycle/StateMachine-sensitive action is represented without copying
  business state authority into the client.

## Closure

- [ ] Focused Executable Specifications pass.
- [ ] No Flutter-specific value has leaked into accepted Logical UI authority.
- [ ] Deterministic identities/currentness/provenance are complete.
- [ ] Independent Phase review has no Current Phase Blocker.
- [ ] Full serialized Cozy validation passes.
- [ ] Closure documentation explicitly leaves development-loop, mobile
  acceptance, CAR productization, publication, and official distribution to
  successor phases.
