# Logical UI Target-Readiness, Progressive Static Web, and Flutter Projection Proposal

Date: 2026-09-06

Status: specification proposal; non-normative; planning input for Phases 50+

## Purpose

Refine the post-Phase-43 UI generation plan so that Logical UI remains the only
platform-neutral UI authority, becomes executable as a Progressive Static Web
review application, and is extended only with target-neutral semantics needed
for later iPhone/Android/Flutter realization.

The product objective remains reliable iPhone and Android applications across
multiple screen sizes and form factors. Web is used early for fast review and
development, but Web behavior is not mobile product acceptance.

Guiding principle:

```text
Mobile-first semantics, Web-first iteration.
```

## Existing authority

Phase 43 already establishes the abstract UI model:

```text
Application Core
  + Business/System/UI UseCases
  + public Component vocabulary
      -> Logical UI candidate
      -> structural Review HTML
      -> accepted Logical UI Model
```

Logical UI owns UI meaning: screen purpose, semantic regions/composition,
interaction/navigation intent, feedback, validation bindings, Component/use-case
coverage, and currentness. It must not be replaced by a second abstract
responsive or Flutter-independent UI model.

## Revised pipeline

The post-Phase-43 sequence is:

```text
Phase 43 accepted Logical UI
        |
        v
Phase 50 target-ready Logical UI
  + target-neutral responsive/accessibility/localization semantics as needed
        |
        +--> structural Review HTML
        |
        `--> Progressive Static Web App
               -> interactive/responsive human review
               -> feedback/revision
               -> exact accepted target-ready Logical UI
                        |
                        v
Phase 51 Flutter Target Policy + Target UI Plan(target=flutter)
                        |
                        v
              deterministic Flutter source
                        |
                        v
Phase 52 Web-first Flutter development loop
                        |
                        v
Phase 53 iPhone/Android acceptance + CAR productization
```

## Phase 50 IR-extension rule

Flutter requirements are useful as a completeness test for Logical UI, but
Flutter concepts must not leak into Logical UI.

Add a missing concept to Logical UI only when it remains meaningful for another
implementation target such as React or native iOS/Android.

Likely target-neutral additions include:

- compact/medium/expanded window-class intent;
- phone/foldable/tablet/desktop hints where semantically useful;
- adaptive-region and layout constraints without coordinates;
- navigation persistence/priority intent;
- UI-local interaction states;
- semantic action/control roles;
- accessibility roles, label/focus/reading-order intent;
- localizable text/source identity;
- target-neutral keyboard/input intent; and
- provenance/currentness hooks required by target projection.

Do not admit into Logical UI:

- Flutter widget classes;
- Material/Cupertino implementation identities;
- Dart types;
- Flutter state-management choices;
- CSS/browser route syntax;
- concrete pixel coordinates; or
- target package/library choices.

## Two Phase-50 review projections

The same Logical UI candidate is projected in two complementary ways.

### Structural Review HTML

Read-only and deterministic. It answers:

```text
What semantics, bindings, coverage, constraints, and responsive intent exist?
```

### Progressive Static Web App

A deterministic executable review projection driven by fixture/mock state. It
answers:

```text
Does this UI behave naturally when the reviewer walks through the use case?
```

It should support screen navigation, selection/input, local validation,
interaction patterns, simulated invocation, loading/success/conflict/
unavailable/failure states, retry/recovery, reset/replay, and responsive
compact/medium/expanded walkthroughs.

It must not call itself a target product implementation, duplicate server
StateMachine/Workflow authority, or fabricate real server acceptance.

Generated Web assets are never authority. Feedback changes the Logical UI
candidate, then both projections are regenerated.

## Responsive model

Do not model commercial device names as semantic identities.

Initial window classes:

```text
compact
medium
expanded
```

Initial form-factor vocabulary where useful:

```text
phone
foldable
tablet
desktop
```

A foldable can move from `foldable + compact` to `foldable + expanded` without
changing application identity. Exact target thresholds do not belong to Logical
UI; they enter through target Policy in Phase 51.

Phase 50 should support one viewport at a time and, where useful, side-by-side
responsive review of the same screen.

## Phase 51 target boundary

Flutter first appears in Phase 51:

```text
Accepted target-ready Logical UI
  + Flutter Target Policy
      -> Target UI Plan(target=flutter)
      -> deterministic generator-owned Dart/Flutter source
```

Target UI Plan is target-specific projection IR, not a second abstract UI model.
It owns Flutter realization decisions such as shell/navigation realization,
page/region layout, target route identity, semantic control-to-widget mapping,
target UI-local state realization, responsive arrangement, target adaptation,
and provenance.

Prefer semantic roles in the plan and allow the renderer/Policy to choose a
concrete widget where possible.

## Phase 52 development loop

Phase 52 turns the projection kernel into normal development operations:

- scaffold;
- inspect;
- plan;
- generate;
- preview;
- test;
- verify;
- typed developer-owned completion; and
- deterministic regeneration.

Flutter Web is the rapid implementation-review surface here. This must remain
conceptually distinct from the Phase 50 Progressive Static Web App:

```text
Phase 50 Progressive Static Web = review Logical UI semantics/behavior
Phase 52 Flutter Web            = review Flutter implementation
```

Generated source and developer completion code have separate ownership. Do not
three-way merge arbitrary developer edits into generated files.

Recommended ownership:

```text
generated/ -> Cozy owns and may replace deterministically
extension/ -> developer owns
```

## Phase 53 mobile acceptance

Primary product acceptance is iPhone/iOS and Android.

Initial profile-based coverage should include:

- iOS phone + compact;
- admitted larger iOS phone/medium;
- Android phone + compact;
- admitted larger Android phone/medium;
- Android foldable folded + compact;
- Android foldable unfolded + expanded; and
- one admitted tablet-like expanded configuration.

Platform validation covers safe areas, software keyboard, touch targets,
back/gesture behavior, modal/sheet behavior, orientation/window changes,
fold/unfold, relevant lifecycle behavior, and admitted native integration.

Web and Desktop remain secondary product targets unless a profile promotes them.

## Provenance

Every target element should remain traceable:

```text
Flutter element/source
  -> Target UI Plan node
  -> accepted Logical UI screen/interaction
  -> UI UseCase Step
  -> public Component/Operation/constraint
```

This is needed for textus-cbd-support review, stale propagation, diagnostics,
and developer feedback.

## Representative driver

Continue the Phase 43 SalesOrder fixture through all phases:

```text
Order list
  -> select
Order detail
  -> confirm
  -> loading
  -> success | conflict | unavailable
  -> retry/recover where admitted
```

Phase 50 proves the behavior in Progressive Static Web. Phase 51 proves target
projection equivalence. Phase 52 proves the development/regeneration loop.
Phase 53 proves real mobile profiles and product packaging.

## Development-phase split

1. **Phase 50 — Logical UI Target-Readiness and Progressive Static Web**
   Extend only target-neutral IR gaps and provide structural + executable Web
   review projections with feedback/acceptance.
2. **Phase 51 — Flutter Target Projection**
   Freeze Flutter Target Policy and Target UI Plan, then generate deterministic
   Flutter base source.
3. **Phase 52 — Flutter Web-first Development Loop**
   Provide normal development operations, Flutter Web responsive review,
   regeneration, developer completion, analyze/test/verify, and provenance.
4. **Phase 53 — Mobile-first Presentation Acceptance and Productization**
   Validate iPhone/Android sizes/form factors, build selected artifacts, create
   the Presentation Subcomponent/CAR, and run clean-consumer verification.

Official App Store/Google Play distribution and Component Repository
publication remain separate downstream authorized operations.
