# CML I18nSummary Baseline

Date: 2026-07-15

## Context

`DescriptiveAttributes` currently uses `I18nSummary` for `summary`, `lead`,
`abstract`, and `remarks`. The runtime type wraps `I18nString`, but its generated
specification contained only pending placeholders.

## Confirmed Behavior

Executable coverage now confirms that `I18nSummary`:

- constructs plain text as one root-locale entry;
- keeps plain single-entry encoding concise;
- round-trips ordered locale/value entries through the shared structured JSON
  codec;
- participates in effective summary fallback through `DescriptiveAttributes`;
- preserves every locale entry when a localized `lead` supplies the effective
  summary.

## Decision Boundary

The shared runtime wrapper does not decide whether summary, lead, abstract, and
remarks use one canonical CML type or distinct constrained types. Their
normalization, empty-value, minimum-length, and maximum-length contracts remain
open.
