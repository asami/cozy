# CML I18nTitle Baseline

Date: 2026-07-15

## Context

CML16-07 uses the existing `title = I18nTitle` behavior as the first
locale-aware semantic text baseline. The previous `simplemodeling-lib`
`I18nTitleSpec` was pending, so the intended single/multi-locale behavior was
not mechanically protected.

## Confirmed Behavior

Executable coverage now confirms that:

- a plain title constructs one locale entry without requiring a hand-built
  locale container;
- a title with multiple locale entries uses the structured string codec and
  round-trips every entry;
- locale lookup selects an effective display value using the existing
  fallback order;
- display fallback does not collapse or mutate the stored entry vector.

This confirms one `I18nTitle` type for both single-locale and multi-locale
values. It does not yet define title length, allowed locales, duplicate locale
handling, or generator/datastore/API projection; those remain later CML16-07
work.
