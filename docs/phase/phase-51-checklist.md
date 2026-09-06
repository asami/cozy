# Phase 51 Checklist: Flutter Target Projection

## Flutter Target Policy

- [ ] Freeze versioned Flutter Target Policy schema and identity.
- [ ] Keep theme/design tokens and concrete target choices below Logical UI.
- [ ] Map Phase 50 compact/medium/expanded intent to concrete Flutter thresholds.
- [ ] Freeze Flutter navigation and feedback realization strategy.
- [ ] Freeze admitted widget-role mapping and platform adaptation policy.
- [ ] Reject stale or incompatible Policy identities.

## Target UI Plan

- [ ] Freeze versioned Target UI Plan with `target=flutter`.
- [ ] Represent shell, navigation, pages, regions, layout realization, semantic
  controls, data/edit bindings, action bindings, target UI-local state,
  validation/feedback, responsive arrangement, localization/accessibility, and
  target adaptation.
- [ ] Bind exact accepted Phase 50 Logical UI and target Policy identities.
- [ ] Preserve reverse provenance to Logical UI screen/interaction, UI UseCase
  Step, and Component/Operation/constraint identity.
- [ ] Reject plans that redefine domain/server authority or bypass Aggregate
  Operation boundaries.
- [ ] Add deterministic plan identity/currentness evidence.

## Flutter source projection

- [ ] Generate deterministic Dart/Flutter base source from the Target UI Plan.
- [ ] Keep generated files fully generator-owned.
- [ ] Provide explicit future completion seams without arbitrary edit merging.
- [ ] Select concrete widgets only under Policy/renderer rules.
- [ ] Run `dart format`.
- [ ] Run `flutter analyze`.
- [ ] Add focused generator/widget tests.

## Equivalence with Phase 50 behavior

- [ ] Preserve SalesOrder screen/navigation coverage.
- [ ] Preserve confirm action binding to public Operation.
- [ ] Preserve validation and loading/success/conflict/unavailable feedback.
- [ ] Preserve compact/medium/expanded responsive intent.
- [ ] Preserve UI UseCase and Component provenance.
- [ ] Use minimal Flutter Web execution only as target-output inspection where useful.
- [ ] Do not claim Flutter Web as iOS/Android acceptance.

## Closure

- [ ] No Flutter-specific value has leaked upward into accepted Logical UI.
- [ ] Exact Logical UI/Policy/Plan/source identities are retained.
- [ ] Focused Executable Specifications pass.
- [ ] Independent Phase review has no Current Phase Blocker.
- [ ] Full serialized Cozy validation passes.
- [ ] Closure leaves normal development-loop UX to Phase 52.
