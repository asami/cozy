# Flutter UI Projection and Responsive Preview Specification Proposal

Date: 2026-09-06

Status: specification proposal; non-normative; planning input for successor phases after Phase 43

## Purpose

Define the technical direction for generating Flutter user interfaces from the
accepted platform-neutral Logical UI Model while keeping iPhone and Android as
the primary product targets and using Flutter Web as the primary rapid
iteration and review surface.

The guiding principle is:

```text
Mobile-first semantics, Web-first iteration.
```

The product objective is reliable iPhone and Android applications across
multiple screen sizes and form factors. Web is deliberately used first in the
development loop because it provides fast startup, easy viewport resizing,
direct screen access, and automation-friendly inspection. Web preview does not
replace mobile acceptance.

## Existing authority boundary

Phase 43 already establishes:

```text
Application Core
  + Business/System/UI UseCases
  + public Component vocabulary
      -> accepted Logical UI Model
```

The Logical UI Model remains target-neutral and must not contain Flutter widget
names, Material implementation details, browser routes, concrete pixel
coordinates, or target state-management choices.

Flutter generation consumes only an accepted Logical UI identity plus an exact
versioned target Policy.

## Projection pipeline

The required pipeline is:

```text
Accepted Logical UI Model
        +
Flutter Target Policy
        |
        v
Flutter UI Plan
        |
        v
Deterministic Flutter base source
        +
Typed developer-owned completion surface
```

Logical UI must not be compiled directly to ad hoc Dart code without the
intermediate Flutter UI Plan.

## Flutter UI Plan

The Flutter UI Plan is a target-specific but still declarative projection IR.
It records target decisions without becoming hand-authored application
business authority.

The first version should model at least:

- application shell role;
- navigation structure and route identity;
- page identity;
- region containment and ordering;
- layout role;
- control/widget role rather than concrete widget class where possible;
- display binding and edit binding;
- Operation/action binding;
- UI-local interaction state;
- validation and feedback presentation binding;
- responsive arrangement;
- theme/design-token references;
- localization identity;
- accessibility semantic role, label source, reading order, and focus intent;
- provenance from plan node to Logical UI screen/interaction/use-case step and
  public Component identity; and
- exact input identities and currentness.

The plan should prefer semantic roles such as `primary-action`, `field`,
`detail-value`, `collection-item`, `navigation-destination`, or
`feedback-region` over hard-coding `FilledButton`, `TextFormField`, or other
specific widget classes. The renderer may then select the concrete Flutter
widget under the admitted target Policy.

## Four-layer separation

The implementation must keep these layers distinct:

| Layer | Owns |
| --- | --- |
| Logical UI | Screen purpose, semantic composition, interaction intent, Component bindings, UseCase coverage. |
| Flutter Target Policy | Theme family, design tokens, responsive breakpoints/window classes, navigation strategy, feedback timing/presentation, target implementation choices. |
| Flutter UI Plan | Target page/region/control roles, responsive arrangement, concrete target bindings, UI-local state and provenance. |
| Flutter renderer | Concrete Dart structure and widget-class selection consistent with the plan and Policy. |

Target-specific choices must not leak upward into Logical UI.

## Mobile-first responsive model

Do not model individual devices as semantic identities. Separate physical/form
factor characteristics from the responsive window class used for layout.

Initial closed form-factor vocabulary:

```text
phone
foldable
tablet
desktop
```

Initial closed window-class vocabulary:

```text
compact
medium
expanded
```

A device may move between classes during execution. For example, a foldable may
be `foldable + compact` when folded and `foldable + expanded` when unfolded.
An iPhone or Android phone may also move between compact and medium depending on
orientation, system settings, or available application width.

The target Policy maps window class to layout strategy. Initial candidate
strategies include:

```text
compact  -> single-pane
medium   -> single-pane + optional secondary region
expanded -> two-pane or persistent secondary/navigation region
```

Exact thresholds belong to versioned Policy, not the accepted Logical UI.

## Web-first responsive preview

Flutter Web is the primary development preview target.

The development environment should support one logical screen rendered in
multiple configured viewport classes, including at minimum compact, medium,
and expanded.

Two preview modes are useful:

1. one viewport at a time with an explicit requested window class; and
2. a responsive review page showing multiple viewport frames side by side for
   the same Logical Screen.

The responsive review surface is evidence and diagnostics only; it is not UI
authority. It should make it easy to identify defects such as content overflow,
region collapse, poor hierarchy, inappropriate navigation, or medium-width
layout failure.

Web preview can prove responsive layout semantics and most generated widget
structure. It cannot prove mobile platform behavior such as safe areas,
software keyboard interaction, touch ergonomics, system back/gesture behavior,
native modal behavior, platform integration, fold transitions, or real-device
performance.

## Mobile acceptance matrix

Primary acceptance targets are iPhone/iOS and Android.

The initial acceptance matrix should be profile-based rather than tied to exact
commercial model names. It should cover at least:

- phone + compact;
- phone + medium/large where admitted;
- Android foldable folded/compact;
- Android foldable unfolded/expanded; and
- at least one larger tablet-like/expanded configuration when the application
  profile admits it.

Web and Desktop are secondary product targets. Web participates from the first
projection phase as a development/review surface; Desktop product acceptance is
post-mobile unless a specific application profile requires it earlier.

## Generated and developer-owned code

Generated source and human completion code must have separate ownership.

Recommended rule:

```text
generated/   -> Cozy owns and may replace deterministically
extension/   -> developer owns
```

Do not implement regeneration by three-way-merging arbitrary developer edits
inside generated files.

Typed completion hooks may include concepts such as:

- ScreenExtension;
- CustomRegion;
- ValueRenderer;
- ActionInterceptor;
- PlatformAdapter; and
- application-specific integration adapters.

The exact hook catalog is a later contract decision but must preserve the
ownership separation.

## Provenance and review

Every generated interactive or display surface should remain traceable:

```text
Flutter element
  -> Flutter UI Plan node
  -> Logical UI screen/interaction
  -> UI UseCase Step
  -> Component element / Operation / constraint
```

This provenance is required for future textus-cbd-support review of generated
UI, generated-code diagnostics, stale propagation, and developer feedback.

## Representative driver

Continue using the Phase 43 SalesOrder fixture.

The first Flutter driver should prove at least:

```text
Order list
  -> select
Order detail
  -> confirm action
  -> loading
  -> success | conflict | unavailable
```

It should include validation and one lifecycle/StateMachine-sensitive action.

## Validation expectations

The projection sequence should support deterministic evidence for:

- exact accepted Logical UI input identity;
- exact target Policy identity;
- Flutter UI Plan identity;
- deterministic generated-source identity;
- responsive coverage across compact/medium/expanded;
- source/provenance coverage;
- `dart format`;
- `flutter analyze`;
- focused Flutter/widget tests; and
- mobile-specific validation in later phases.

## Development-phase split

The implementation should be divided into three successor phases:

1. **Flutter UI Projection** — freeze Flutter Target Policy and Flutter UI Plan,
   generate deterministic Flutter base source, and provide the minimum Web
   responsive proof needed to inspect the projection.
2. **Flutter Web-first Development Loop** — scaffold/inspect/plan/generate,
   responsive preview, regeneration, developer-owned completion hooks,
   analyze/test/verify, and developer review workflow.
3. **Mobile-first Presentation Productization** — iPhone/Android simulator and
   device-class acceptance, multi-platform build as admitted, artifact
   inventory, Presentation Subcomponent/CAR, provenance/manual/security
   evidence, and clean-consumer verification.

Official App Store/Google Play distribution and Component Repository
publication remain separate downstream authorized operations unless an
explicit later phase chooses to own one of them.

## Non-goals of the first projection phase

- App Store or Google Play publication;
- final CAR productization;
- complete Desktop support;
- treating Web behavior as proof of iOS/Android behavior;
- embedding Flutter widgets in Logical UI;
- arbitrary developer edits to generated files;
- autonomous acceptance of generated UI; or
- deriving screens directly from the domain model without accepted UI UseCase
  and Logical UI authority.
