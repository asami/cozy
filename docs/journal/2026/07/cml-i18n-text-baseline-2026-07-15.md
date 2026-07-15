# CML I18nText Baseline

Date: 2026-07-15

## Context

`I18nText` wraps `I18nString` and is accepted by `ContentBody` and
`ContentAttributes.Builder`. Its generated specification contained only
pending placeholders, while the content conversion selected one display
string from the localized value.

## Confirmed Behavior

Executable coverage now confirms that `I18nText`:

- constructs plain text as one root-locale entry;
- keeps plain single-entry encoding concise;
- round-trips ordered locale/value entries through the shared structured JSON
  codec.

## Identified Boundary

`ContentBody` and `ContentAttributes.Builder` currently convert `I18nText`
through `displayMessage` and store one `String`. That conversion loses the
other locale entries and cannot satisfy the natural-I18N requirement for
datastore, API, form, and Help surfaces.

This slice does not turn the collapse into an accepted specification. A later
design slice must decide whether `ContentBody` owns `I18nText` directly or
introduces another locale-preserving body representation, including the
corresponding `Record` and generated-surface contract.
