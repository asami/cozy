# Phase 51: Flutter Web-first Development Loop

Status: PLANNED

Plan date: 2026-09-06

Predecessor: Phase 50 Flutter UI Projection closure.

## Goal

Turn the Phase 50 projection kernel into a usable Flutter development loop with
Flutter Web as the primary rapid iteration and responsive-review surface while
preserving iPhone/Android as the eventual primary product acceptance targets.

## P51-01: Development Operations

Provide normal Cozy operations for the generated Flutter application surface:

- scaffold;
- inspect;
- plan;
- generate;
- preview;
- test; and
- verify.

Build/package operations belong to Phase 52 unless a minimal development-only
build is required to execute this Phase's tests.

## P51-02: Responsive Web Review

- Provide explicit compact, medium, and expanded preview modes.
- Provide a responsive review surface that can show the same Logical Screen in
  multiple viewport frames side by side.
- Make viewport/profile selection deterministic and Policy-driven.
- Expose overflow, unreachable content, missing feedback, navigation mismatch,
  stale target-plan/source identity, and responsive-coverage diagnostics.
- Keep review output read-only and non-authoritative.

## P51-03: Regeneration and Ownership

- Regenerate generator-owned Flutter base source deterministically.
- Preserve developer-owned completion code without merging developer edits into
  generated files.
- Freeze a typed initial completion-hook contract sufficient for the
  representative driver.
- Candidate hook roles include custom region/value rendering, action
  interception, and platform/application adapters.
- Detect incompatible stale completion bindings explicitly.

## P51-04: Developer Feedback Loop

- Preserve source/provenance navigation from generated Flutter surface back to
  Flutter UI Plan, Logical UI, UI UseCase Step, and Component authority.
- Make inspect/review outputs suitable for a developer or Codex task to locate
  the semantic source of a generated UI defect.
- Record deterministic verification evidence after regeneration.

## P51-05: Development Validation

- Run `dart format`, `flutter analyze`, Flutter unit/widget tests, and responsive
  Web tests through normal Cozy operations.
- Add representative navigation/action/feedback tests for SalesOrder.
- Prove repeated generation with unchanged inputs is byte/digest stable where
  the contract requires it.

## Representative Driver

Use the Phase 50 SalesOrder projection and exercise its development cycle:

```text
inspect/plan
  -> generate
  -> Web responsive review
  -> developer completion/feedback where admitted
  -> regenerate
  -> analyze/test/verify
```

The review must compare compact, medium, and expanded layouts and retain
provenance back to the accepted Logical UI.

## Exclusions

- claiming Web behavior as iPhone/Android acceptance;
- final iOS/Android build/sign/package verification;
- real fold/unfold platform acceptance;
- Presentation Subcomponent CAR productization;
- Component Repository publication;
- official App Store/Google Play distribution; and
- Desktop product acceptance except development preview where convenient.

## Completion Criteria

Phase 51 completes when a developer can repeatedly inspect, generate, preview,
review, extend through typed owned surfaces, regenerate, analyze, test, and
verify the representative Flutter application with compact/medium/expanded Web
review while generated ownership, provenance, identities, and currentness stay
sound.

## Successor

Phase 52 owns mobile-first acceptance across iPhone/Android size/form-factor
profiles and Presentation Subcomponent productization.

## References

- `docs/phase/phase-51-checklist.md`
- `docs/phase/phase-50.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-flutter-mobile-first-web-first-development-direction.md`
