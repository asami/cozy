# Phase 50: Flutter UI Projection

Status: PLANNED

Plan date: 2026-09-06

Predecessor: Phase 43 Logical UI Model closure. Operationally independent from Document Project Phases 48/49.

## Goal

Introduce the first target-specific Flutter projection boundary from an accepted
Logical UI Model while preserving the Logical UI authority as platform-neutral.
Freeze a versioned Flutter Target Policy and first-class Flutter UI Plan, then
generate deterministic Flutter base source with enough Web responsive proof to
inspect the projection.

Guiding principle:

```text
Mobile-first semantics, Web-first iteration.
```

## P50-01: Flutter Target Policy

- Freeze the versioned policy identity consumed with an accepted Logical UI Model.
- Own target choices that must not leak into Logical UI: theme/design tokens,
  responsive window classes and thresholds, navigation strategy, feedback
  presentation/timing, concrete target conventions, and admitted widget-role
  mapping policy.
- Initial window classes are `compact`, `medium`, and `expanded`.
- Initial form-factor vocabulary is `phone`, `foldable`, `tablet`, and
  `desktop`, but layout decisions are based on available window class rather
  than commercial device names.

## P50-02: Flutter UI Plan

- Freeze a versioned target projection IR between Logical UI and Dart source.
- Represent application shell, navigation, page, region, layout role,
  control/widget role, display/edit binding, Operation/action binding,
  UI-local state, validation/feedback binding, responsive arrangement,
  localization, accessibility, and provenance.
- Prefer semantic control roles such as `primary-action` over concrete Flutter
  widget-class identities where the renderer can safely choose the class.
- Bind exact accepted Logical UI and Flutter Target Policy identities.
- Preserve reverse provenance from each target plan node to Logical UI,
  UI UseCase Step, and public Component identity.

## P50-03: Deterministic Flutter Base Projection

- Generate deterministic Flutter/Dart base source from the Flutter UI Plan.
- Keep generated source under generator ownership.
- Do not merge arbitrary human edits into generated files.
- Provide explicit seams for later typed developer-owned completion without
  implementing the full completion catalog in this Phase.
- Run `dart format`, `flutter analyze`, and focused generator/widget tests on
  the representative fixture.

## P50-04: Minimum Responsive Web Proof

- Use Flutter Web as the minimum projection-inspection target.
- Render representative Logical Screens at compact, medium, and expanded
  viewport classes.
- Prove that responsive decisions come from the Policy/Plan rather than
  browser-specific ad hoc code.
- Retain deterministic evidence for input identities, plan identity, generated
  source identity, and responsive coverage.
- Web proof does not claim iOS/Android platform acceptance.

## Representative Driver

Reuse the accepted Phase 43 SalesOrder fixture and prove:

```text
Order list
  -> select
Order detail
  -> confirm
  -> loading
  -> success | conflict | unavailable
```

The driver must include validation and a lifecycle/StateMachine-sensitive
public action.

## Exclusions

- full scaffold/inspect/plan/generate development UX;
- multi-screen side-by-side review tooling beyond the minimum responsive proof;
- complete developer-owned extension hooks;
- iOS/Android simulator or device acceptance;
- mobile packaging/signing;
- final Web/Desktop product acceptance;
- Presentation Subcomponent CAR productization;
- Component Repository publication;
- App Store/Google Play distribution; and
- any Flutter-specific mutation of accepted Logical UI authority.

## Completion Criteria

Phase 50 completes when one accepted Logical UI plus one exact Flutter Target
Policy deterministically produces a validated Flutter UI Plan and Flutter base
source; the representative SalesOrder flow is inspectable through compact,
medium, and expanded Flutter Web proofs; provenance/currentness is retained;
and focused format/analyze/test evidence passes without claiming mobile product
acceptance.

## Successor

Phase 51 owns the Web-first development loop, responsive review ergonomics,
regeneration, typed developer completion, and normal development operations.

## References

- `docs/phase/phase-50-checklist.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-flutter-mobile-first-web-first-development-direction.md`
- `docs/phase/phase-43.md`
- `docs/design/logical-ui-model.md`
