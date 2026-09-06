# Phase 51: Flutter Target Projection

Status: PLANNED

Plan date: 2026-09-06

Predecessor: Phase 50 Logical UI Target-Readiness and Progressive Static Web closure.

## Goal

Introduce the first Flutter-specific projection boundary from the exact accepted
target-ready Logical UI produced by Phase 50. Freeze a versioned Flutter Target
Policy and target-specific Target UI Plan, then generate deterministic Flutter
base source without redefining Logical UI semantics.

The core path is:

```text
Accepted target-ready Logical UI
        +
Flutter Target Policy
        |
        v
Target UI Plan (target=flutter)
        |
        v
Deterministic Flutter base source
```

## P51-01: Flutter Target Policy

Freeze versioned target choices that must remain below Logical UI, including:

- theme/design-token family;
- concrete responsive thresholds for compact/medium/expanded;
- Flutter navigation realization strategy;
- feedback presentation/timing;
- concrete target conventions;
- admitted widget-role mapping policy; and
- target/platform adaptation policy.

## P51-02: Target UI Plan

Freeze a versioned target-specific declarative IR between Logical UI and Dart.
Use a generic target-plan identity with `target=flutter` rather than creating a
second abstract UI authority.

Represent at least:

- application shell realization;
- navigation and target route identity;
- page/region layout realization;
- semantic control roles and target bindings;
- display/edit binding;
- public Operation/action binding;
- target UI-local state;
- validation/feedback presentation binding;
- responsive arrangement derived from Phase 50 intent plus target Policy;
- localization/accessibility realization;
- target adaptation metadata; and
- reverse provenance to Logical UI, UI UseCase Step, and Component authority.

Prefer semantic roles over concrete widget classes where the renderer can make
the final choice.

## P51-03: Deterministic Flutter source projection

- Generate deterministic generator-owned Dart/Flutter base source.
- Do not merge arbitrary human edits into generated files.
- Keep explicit seams for later typed developer-owned completion.
- Select concrete widgets only under renderer/Policy rules.
- Preserve exact Logical UI, Policy, Plan, and generated-source identities.
- Run `dart format`, `flutter analyze`, and focused generator/widget tests.

## P51-04: Projection equivalence proof

Use the same SalesOrder semantics already exercised through the Phase 50
Progressive Static Web projection and prove that the Flutter projection retains:

- screen/navigation coverage;
- interaction/action bindings;
- validation and feedback paths;
- compact/medium/expanded responsive intent; and
- use-case/Component provenance.

A minimal Flutter Web execution may be used to inspect target output, but this
Phase does not own the general development-loop UX and does not treat Flutter
Web as mobile acceptance.

## Exclusions

- changing accepted Logical UI semantics;
- adding another abstract UI model;
- full scaffold/inspect/plan/generate/preview UX;
- full developer completion catalog and regeneration workflow;
- iOS/Android product acceptance;
- CAR/product packaging;
- publication/deployment/distribution.

## Completion Criteria

Phase 51 completes when one exact accepted target-ready Logical UI plus one
versioned Flutter Target Policy deterministically produces a valid Target UI
Plan and Flutter base source, retains Phase 50 semantic/behavioral coverage and
provenance, and passes focused format/analyze/test evidence without semantic
drift.

## Successor

Phase 52 owns the normal Flutter development loop: operations, Web-first rapid
iteration, responsive review, typed developer completion, regeneration, and
verification.

## References

- `docs/phase/phase-51-checklist.md`
- `docs/phase/phase-50.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-flutter-mobile-first-web-first-development-direction.md`
