# Phase 50: Logical UI Target-Readiness and Progressive Static Web

Status: PLANNED

Plan date: 2026-09-06

Predecessor: Phase 43 Logical UI Model closure.

## Goal

Extend the accepted Phase 43 Logical UI IR only where later target generation
requires additional platform-neutral UI semantics, then provide two projections
from that same IR:

1. the existing deterministic structural Review HTML; and
2. a Progressive Static Web App that lets a reviewer execute navigation,
   interaction, validation, feedback, and responsive use-case walkthroughs
   without a real application backend.

Phase 50 does **not** generate Flutter code and does not introduce a
Flutter-specific IR. Its output is a target-ready accepted Logical UI authority
that Phase 51 can consume for Flutter target projection.

Guiding boundary:

```text
Phase 43 accepted Logical UI
        |
        v
Phase 50 target-ready Logical UI
   |                    |
   v                    v
Review HTML     Progressive Static Web App
                           |
                           v
                    human review / feedback
                           |
                           v
                 accepted target-ready Logical UI
                           |
                           v
                        Phase 51
                    Flutter projection
```

## P50-01: Target-readiness IR extension

Review the Phase 43 IR against requirements discovered from iPhone/Android,
responsive Web, and later Flutter realization. Add only semantics that remain
valid for another implementation target such as React or native UI.

Candidate additions include:

- responsive/window-class intent: `compact`, `medium`, `expanded`;
- form-factor hints where semantically useful: `phone`, `foldable`, `tablet`,
  `desktop`;
- region adaptation and layout constraints without pixel coordinates;
- primary/secondary/persistent navigation intent;
- UI-local interaction-state semantics not already represented;
- focus order and accessibility semantic roles;
- localizable text/source identities;
- keyboard/input intent where target-neutral;
- semantic action/control roles such as primary action, selection, field,
  navigation destination, and feedback region; and
- target-projection provenance hooks/currentness evidence.

Do not add Flutter widget names, Material/Cupertino types, Dart concepts,
browser URLs, CSS coordinates, or target state-management libraries.

Decision rule:

```text
If the value remains meaningful for React or native iOS/Android, it may belong
in Logical UI. If it only has meaning in Flutter, it belongs to Phase 51+.
```

## P50-02: Structural Review HTML alignment

Extend the existing Logical UI Review HTML only as needed to expose the new
IR semantics, responsive intent, accessibility/localization information, and
currentness. It remains deterministic, read-only, self-contained, and
non-authoritative.

The Review HTML answers primarily:

> What UI semantics and bindings are defined?

## P50-03: Progressive Static Web projection

Generate a deterministic static Web application from the Logical UI IR for
behavioral review. It is an executable review projection, not target product
code and not semantic authority.

It must support, from fixtures/mock state:

- screen navigation and reachability;
- selection and input;
- admitted interaction patterns;
- local-deterministic validation;
- action invocation simulation without claiming server execution;
- loading, success, validation-failed, conflict, unavailable, and operation-
  failed feedback paths as admitted by the Logical UI;
- UI UseCase walkthroughs including alternatives/retry paths; and
- deterministic reset/replay of fixture state.

The app must not fabricate server-authoritative outcomes or duplicate domain
StateMachine/Workflow authority. Fixture transitions are review simulation only.

The Progressive Static Web projection answers primarily:

> Does the accepted UI behavior make sense when a user walks through the use case?

## P50-04: Responsive interactive review

Use the Progressive Static Web projection to review the same Logical Screen and
flow under `compact`, `medium`, and `expanded` window classes.

Provide at least:

- one viewport at a time;
- side-by-side responsive review for the same screen where practical;
- deterministic viewport/profile selection;
- navigation and interaction preserved across admitted responsive variants;
- diagnostics for overflow, unreachable required content, missing actions,
  missing feedback, focus-order problems, and inconsistent responsive intent.

Web responsive review is evidence for Logical UI behavior and target-readiness.
It is not proof of iOS/Android platform behavior.

## P50-05: Review-feedback and acceptance loop

Preserve the Phase 43 candidate/accepted authority model.

```text
Logical UI candidate
  -> Review HTML + Progressive Static Web
  -> developer/domain review
  -> feedback
  -> revised candidate
  -> explicit acceptance
```

Generated Web files never become authority. The accepted target-ready Logical
UI identity is the only semantic input handed to Phase 51.

## Representative Driver

Reuse the Phase 43 SalesOrder fixture and prove an interactive review flow:

```text
Order list
  -> select
Order detail
  -> confirm
  -> loading
  -> success | conflict | unavailable
  -> retry/recover where admitted
```

Exercise compact, medium, and expanded responsive variants and retain exact
coverage/provenance back to UI UseCase Steps and Component identities.

## Exclusions

- Flutter Target Policy or Flutter UI Plan;
- Dart/Flutter source generation;
- Flutter Web product generation;
- target widget selection;
- target state-management libraries;
- real backend/API execution;
- iOS/Android simulator/device acceptance;
- CAR/product packaging;
- publication/deployment/distribution; and
- autonomous acceptance of generated UI semantics.

## Completion Criteria

Phase 50 completes when the Phase 43 Logical UI contract is extended only with
necessary target-neutral semantics; both structural Review HTML and a
deterministic Progressive Static Web App project the same candidate/accepted IR;
the SalesOrder UI UseCase can be interactively reviewed under compact, medium,
and expanded configurations; feedback can revise the candidate; and one exact
target-ready Logical UI identity is explicitly accepted for successor target
projection.

## Successor

Phase 51 consumes only the accepted target-ready Logical UI and introduces the
first Flutter-specific boundary: Flutter Target Policy, Target UI Plan, and
deterministic Flutter source projection.

## References

- `docs/phase/phase-50-checklist.md`
- `docs/phase/phase-43.md`
- `docs/design/logical-ui-model.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-flutter-mobile-first-web-first-development-direction.md`
