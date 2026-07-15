# CML I18nBrief Baseline

Date: 2026-07-15

## Context

`DescriptiveAttributes` currently uses `I18nBrief` for both `headline` and
`brief`. The runtime type wraps `I18nString`, but its generated specification
contained only pending placeholders.

## Confirmed Behavior

Executable coverage now confirms that `I18nBrief`:

- constructs plain text as one root-locale entry;
- keeps plain single-entry encoding concise;
- round-trips ordered locale/value entries through the shared structured JSON
  codec;
- participates in effective headline and brief selection through
  `DescriptiveAttributes`;
- preserves every locale entry while selecting an effective display value.

## Decision Boundary

This shared runtime behavior does not decide whether CML headline and brief use
one predefined type or separate constrained types. Their normalization,
empty-value, minimum-length, and maximum-length contracts remain open.
