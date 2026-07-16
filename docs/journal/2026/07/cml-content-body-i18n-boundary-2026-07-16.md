# CML ContentBody and I18nText Boundary

date=2026-07-16
phase=16
status=decided

## Context

`ContentBody` represents one document body. `I18nText` preserves multiple
locale entries. The shared and SimpleModeling content readers still accepted
`I18nText` by calling display fallback and storing only the selected text.

## Decision

Compatibility is not retained. `ContentBody` readers and
`ContentAttributes.Builder` accept explicit `ContentBody` or string document
input, but reject `I18nText`. A caller that intentionally creates a document
from localized narrative must select a locale explicitly before crossing this
boundary.

This keeps locale selection in presentation/application logic and prevents a
storage conversion from silently discarding every non-selected locale entry.
Generated `derived=content` access already returns `ContentBody`, so no
generator compatibility path is required.
