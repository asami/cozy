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

## Cross-Contract Finding

`ContentBody` and `ContentAttributes.Builder` currently convert `I18nText`
through `displayMessage` and store one `String`. The initial audit treated this
as an unresolved locale-preservation defect. A cross-check against CNCF SD-01B
showed that this interpretation was incorrect: the accepted content contract
keeps `ContentBody` as one document body and reserves rich multilingual bodies
for the future SmartDox Textus profile.

## Decision

`I18nText` is the runtime baseline for localized plain narrative text. It is
not the multilingual representation of `ContentBody`. The current conversion
to `ContentBody` is a display projection or compatibility input and must not be
described as canonical multilingual storage.

Phase 16 still needs to decide whether those conversion overloads should remain
available or be deprecated to prevent accidental storage assumptions. That
decision does not change the established SD-01B document-body boundary.

The same cross-check found a stale SimpleModeler alias: `derived=content`
returned `Option[I18nText]` from an `Option[ContentBody]` source and also emitted
a locale overload. The generator now returns `Option[ContentBody]` and omits
the locale overload, so generated entity APIs follow the established boundary.
