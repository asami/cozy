# Flutter Mobile-first / Web-first Development Direction

Date: 2026-09-06

## Context

The main product objective for Flutter generation is iPhone and Android
applications that work across multiple screen sizes and form factors. Web and
Desktop are secondary product targets.

At the same time, Flutter Web is attractive as the primary development and
review surface because it starts quickly, is automation-friendly, supports easy
viewport resizing, and can show the same generated screen under several
responsive widths without requiring a simulator for every edit.

This leads to the working principle:

```text
Mobile-first semantics, Web-first iteration.
```

## Decision

Keep iPhone/iOS and Android as the primary acceptance targets. Use Flutter Web
from the first projection phase as a development preview and responsive-review
surface, not as the semantic or product authority.

The accepted Phase 43 Logical UI Model stays platform-neutral. Flutter-specific
choices enter through a versioned Flutter Target Policy and a target-specific
Flutter UI Plan.

The generation path is therefore:

```text
Accepted Logical UI Model
  + Flutter Target Policy
      -> Flutter UI Plan
      -> deterministic Flutter base source
      -> typed developer-owned completion
```

Do not compile Logical UI directly into ad hoc Dart code without the
intermediate plan.

## Responsive direction

Responsive behavior is not modeled by commercial device names. Separate form
factor from layout window class.

Initial form factors:

- phone;
- foldable;
- tablet; and
- desktop.

Initial window classes:

- compact;
- medium; and
- expanded.

The same device may move between classes, especially foldables and orientation
changes. Exact thresholds belong to Flutter Target Policy.

Web preview should exercise the same logical screen at compact, medium, and
expanded widths. A side-by-side responsive review mode is desirable so a human
or Codex task can compare layouts quickly.

## Acceptance boundary

Web preview is sufficient for rapid inspection of responsive composition,
content overflow, hierarchy, navigation structure, and most generated widget
composition. It is not sufficient acceptance evidence for safe areas,
software keyboards, touch ergonomics, platform back/gesture behavior, native
modals, fold transitions, native integration, or real-device performance.

Primary mobile acceptance should eventually include at least:

- phone + compact;
- larger phone / medium where admitted;
- Android foldable folded + compact;
- Android foldable unfolded + expanded; and
- an expanded tablet-like configuration where the profile admits it.

## Development plan split

The earlier broad Flutter successor scope is too large for one Phase. Split it
into:

1. Flutter UI Projection;
2. Flutter Web-first Development Loop; and
3. Mobile-first Presentation Productization.

The first phase owns the target Policy/Plan boundary and deterministic source
projection. The second owns fast development, responsive preview, regeneration,
developer completion, analyze/test/verify, and review. The third owns mobile
acceptance and Presentation Subcomponent productization.

Official store distribution and Component Repository publication remain
separate downstream authorized operations.

## Technical record

Detailed proposal:

`docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
