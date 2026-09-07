# Phase 52: Flutter Web-first Development Loop

Status: PLANNED

Plan date: 2026-09-06

Predecessor: Phase 51 Flutter Target Projection closure.

## Goal

Turn the Phase 51 Flutter projection kernel into a normal development loop with
Web as the primary rapid iteration/review surface while keeping iPhone/Android
as the later primary product acceptance targets.

Guiding principle:

```text
Mobile-first semantics, Web-first iteration.
```

## P52-01: Development operations

Provide normal Cozy operations for the Flutter application surface:

- scaffold;
- inspect;
- plan;
- generate;
- preview;
- test; and
- verify.

Build/package operations belong to Phase 53 except for development-only builds
needed by tests.

## P52-02: Web-first responsive review

- Use Flutter Web for fast target implementation review.
- Provide compact, medium, and expanded preview modes.
- Provide side-by-side responsive review for the same Logical Screen where useful.
- Keep viewport/profile selection deterministic and Policy-driven.
- Diagnose overflow, unreachable required content, missing feedback/navigation,
  stale target-plan/source identity, and responsive coverage gaps.
- Preserve distinction between Phase 50 Progressive Static Web semantic review
  and Phase 52 Flutter Web implementation review.

The two Web surfaces have different purposes:

```text
Phase 50 Progressive Static Web
  = inspect Logical UI behavior before target implementation

Phase 52 Flutter Web
  = inspect the generated Flutter realization
```

## P52-03: Regeneration and source ownership

- Regenerate generator-owned Flutter base source deterministically.
- Keep developer-owned completion code separate from generated files.
- Freeze a typed initial completion-hook contract.
- Candidate hook roles include custom region/value rendering, action
  interception, and platform/application adapters.
- Reject incompatible/stale completion bindings explicitly.
- Never implement regeneration by merging arbitrary human edits into generated files.

## P52-04: Developer feedback/provenance loop

Preserve navigability:

```text
Flutter element/source
  -> Target UI Plan node
  -> accepted Logical UI screen/interaction
  -> UI UseCase Step
  -> public Component/Operation/constraint
```

Use inspect/review evidence so a developer or Codex task can identify whether a
defect belongs to target Policy, target Plan, accepted Logical UI, or underlying
Component/use-case authority.

## P52-05: Development validation

Run through normal Cozy operations:

- `dart format`;
- `flutter analyze`;
- Flutter unit tests;
- Flutter widget tests;
- compact/medium/expanded Web tests; and
- SalesOrder navigation/action/feedback regression tests.

Prove deterministic regeneration with unchanged inputs where required.

## Representative Driver

Use the Phase 51 SalesOrder Flutter projection:

```text
inspect/plan
  -> generate
  -> Flutter Web responsive review
  -> developer completion/feedback where admitted
  -> regenerate
  -> analyze/test/verify
```

## Exclusions

- changing Phase 50 accepted Logical UI authority;
- treating Flutter Web behavior as iPhone/Android acceptance;
- final iOS/Android build/sign/package verification;
- real fold/unfold platform acceptance;
- Presentation Subcomponent CAR productization;
- Component Repository publication;
- App Store/Google Play distribution; and
- mandatory Desktop product acceptance.

## Completion Criteria

Phase 52 completes when a developer can repeatedly scaffold/inspect/plan,
generate, preview, review, extend through typed owned surfaces, regenerate,
analyze, test, and verify the representative Flutter application through a
Web-first development loop while source ownership, provenance, identities, and
currentness remain sound.

## Successor

Phase 53 owns iPhone/Android multi-size/form-factor acceptance and Presentation
Subcomponent productization.

## References

- `docs/phase/phase-52-checklist.md`
- `docs/phase/phase-51.md`
- `docs/phase/phase-50.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-flutter-mobile-first-web-first-development-direction.md`
