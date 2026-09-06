# Logical UI / Flutter Mobile-first, Web-first Development Direction

Date: 2026-09-06

## Context

The main product objective is Flutter-based iPhone and Android applications that
work across multiple screen sizes and form factors. Web and Desktop are
secondary product targets.

Phase 43 is already complete and owns the platform-neutral Logical UI Model plus
a deterministic structural Review HTML. It intentionally excludes HTML
application and Flutter target generation.

Discussion of the successor plan identified a better boundary: before Flutter
code is generated, the Logical UI should be checked for target-readiness and
should be executable as a Progressive Static Web App so reviewers can walk
through use cases and responsive behavior directly from the IR.

## Decision

Keep the working principle:

```text
Mobile-first semantics, Web-first iteration.
```

But separate the two uses of Web:

1. **Phase 50 Progressive Static Web** reviews Logical UI semantics and behavior
   before target implementation.
2. **Phase 52 Flutter Web** reviews the generated Flutter implementation during
   the development loop.

These are sibling review surfaces at different abstraction levels and must not
be conflated.

## Phase 50 decision

Phase 50 no longer generates Flutter code.

It extends the Phase 43 Logical UI IR only where later target generation exposes
missing platform-neutral UI semantics. The rule is that a new IR concept must
remain meaningful for another target such as React or native iOS/Android. Flutter
widget names, Material/Cupertino types, Dart concepts, concrete target packages,
and target state-management choices do not belong in Logical UI.

Likely additions include responsive window-class intent, form-factor hints,
adaptive-region constraints, navigation persistence intent, accessibility/focus
semantics, localization identity, input intent, semantic control roles, and
target-projection provenance/currentness hooks.

From the same Logical UI candidate, Phase 50 provides:

```text
Structural Review HTML
  -> what is defined?

Progressive Static Web App
  -> does the UI behave naturally through the use case?
```

The Progressive Static Web App uses fixture/mock state and supports navigation,
selection/input, local validation, simulated action invocation, loading,
success/conflict/unavailable/failure, retry/recovery, and compact/medium/
expanded responsive review. Generated Web files remain projections only;
feedback revises the Logical UI candidate and explicit acceptance binds the
exact target-ready Logical UI identity.

## Flutter boundary

Flutter-specific work starts in Phase 51:

```text
Accepted target-ready Logical UI
  + Flutter Target Policy
      -> Target UI Plan(target=flutter)
      -> deterministic Flutter source
```

Target UI Plan is a target-specific implementation plan, not another abstract
UI authority.

Phase 52 then owns the normal development loop: scaffold, inspect, plan,
generate, Flutter Web preview, test, verify, typed developer completion,
regeneration, and provenance-driven feedback.

## Mobile acceptance boundary

Phase 53 owns real iPhone/iOS and Android acceptance. Browser resizing is not
mobile acceptance. Required profile direction includes phone compact/medium,
Android foldable folded/compact and unfolded/expanded, and admitted tablet-like
expanded configurations.

Platform validation covers safe areas, software keyboard, touch targets,
back/gesture behavior, modal/sheet behavior, orientation/window changes,
fold/unfold, lifecycle behavior, and admitted native integration.

Phase 53 also owns selected builds, Presentation Subcomponent/CAR packaging,
and clean-consumer verification. App Store/Google Play and Component Repository
publication remain separate authorized downstream operations.

## Revised development sequence

```text
Phase 43 COMPLETE
  Logical UI authority + structural Review HTML
        |
        v
Phase 50
  target-neutral Logical UI extensions
  structural Review HTML alignment
  Progressive Static Web App
  interactive/responsive feedback and acceptance
        |
        v
Phase 51
  Flutter Target Policy
  Target UI Plan(target=flutter)
  deterministic Flutter source
        |
        v
Phase 52
  Web-first Flutter development loop
  responsive implementation review
  completion/regeneration/test/verify
        |
        v
Phase 53
  iPhone/Android multi-size/form-factor acceptance
  builds + Presentation Subcomponent/CAR
  clean consumer verification
```

## Technical record

Detailed proposal:

`docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
