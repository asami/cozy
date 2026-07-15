# CML I18nLabel Baseline

Date: 2026-07-15

## Context

Label is the first remaining semantic wrapper audited after the shared
`I18nString` baseline. Unlike legacy `I18nMessage`, `I18nLabel` already wraps
the shared locale-aware value and is used by schema and model metadata.

## Confirmed Behavior

Executable coverage now confirms that `I18nLabel`:

- constructs plain text as one root-locale entry;
- keeps plain single-entry encoding concise;
- round-trips ordered locale/value entries through the shared structured JSON
  codec;
- preserves the underlying `I18nString` value at the wrapper boundary.

## Decision

`I18nLabel` is accepted as the existing locale-aware runtime label baseline.
This does not yet expose `label` as a canonical CML field datatype or decide
normalization, empty-value, minimum-length, or maximum-length policy. Those
remain catalog decisions before driver migration.
