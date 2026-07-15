# CML I18nDescription Baseline

Date: 2026-07-15

## Context

Description is a user-visible semantic text family under the Phase 16 catalog
audit. The current `I18nDescription` runtime type wraps `I18nString`, but its
generated specification contained only pending placeholders.

## Confirmed Behavior

Executable coverage now confirms that `I18nDescription`:

- constructs plain text as one root-locale entry;
- keeps plain single-entry encoding concise;
- round-trips ordered locale/value entries through the shared structured JSON
  codec;
- participates in `DescriptiveAttributes.effectiveDescription` and
  `effectiveDescriptionString` selection;
- selects an effective locale through the preserved `I18nString` without
  changing stored entries.

## Decision Boundary

The shared wrapper behavior is accepted as runtime evidence for the CML text
catalog. It does not yet establish `description` as a canonical CML field type
or define description-specific normalization, empty-value, multiline,
minimum-length, or maximum-length policy.
