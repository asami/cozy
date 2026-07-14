# CML I18nMessage Legacy Baseline

Date: 2026-07-15

## Context

The Phase 16 wrapper audit found that `I18nLabel`, `I18nTitle`, `I18nBrief`,
`I18nSummary`, `I18nDescription`, and `I18nText` wrap `I18nString`, while
`I18nMessage` directly owns locale/value entries. Its existing specification
was pending and did not expose this compatibility boundary.

## Confirmed Behavior

Executable coverage now confirms that the current `I18nMessage`:

- constructs plain text as one root-locale entry;
- selects root, English, and Japanese values in that fixed order;
- returns the first entry when none of those preferred locales exists;
- retains its direct `NonEmptyVector[(Locale, String)]` representation.

## Decision Boundary

`I18nMessage` does not currently use the shared `I18nString` codec and does not
provide requested-locale selection. Phase 16 must decide whether canonical CML
`message` aligns with the shared wrapper family and how legacy callers migrate.
This slice records the current behavior without changing the public model.
