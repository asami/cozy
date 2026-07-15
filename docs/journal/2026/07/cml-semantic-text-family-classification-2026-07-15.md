# CML Semantic Text Family Classification

date=2026-07-15
phase=16
status=decision

## Context

Phase 16 established executable runtime baselines for `Name`, `Text`, the
shared `I18nString` codec, and the existing `I18nLabel`, `I18nTitle`,
`I18nBrief`, `I18nSummary`, `I18nDescription`, `I18nText`, and `I18nMessage`
wrappers. Those individual baselines did not yet state how the wrappers relate
to CML semantic field roles or to `DescriptiveAttributes`.

## Decision

CML semantic text is selected by domain role, not by choosing between scalar
and I18N storage shapes. User-visible descriptive roles use locale-aware
runtime families by default, while stable names, identifiers, protocol values,
hashes, and tokens remain nonlocalized by their domain contract.

`DescriptiveAttributes` is the integration point for descriptive metadata:

- `headline` and `brief` use `I18nBrief`;
- `summary`, `lead`, `abstract`, and `remarks` use `I18nSummary`;
- `description` uses `I18nDescription`;
- `tooltip` uses `I18nLabel`.

The stored fields preserve their full `I18nString` values. `effectiveHeadline`,
`effectiveBrief`, `effectiveSummary`, `effectiveDescription`, and
`effectiveTooltip` are display-selection rules only. A fallback does not
rewrite a stored field, collapse locale entries, or prove that the source and
target roles should share one length range.

`I18nText` is localized plain narrative text and is not a replacement for the
single-document `ContentBody` contract. `I18nMessage` remains a legacy runtime
exception until Phase 16 decides its canonical codec and range. These open
items do not block recording the semantic family classification.

## Remaining Work

- define minimum and maximum lengths and normalization per semantic role;
- decide whether headline and brief need distinct ranges despite sharing
  `I18nBrief`;
- decide whether summary-family fields need distinct ranges despite sharing
  `I18nSummary`;
- define the canonical CML `message` contract and migration from legacy
  `I18nMessage` behavior;
- project accepted constraints consistently to metadata, datastore, forms,
  REST/OpenAPI, and Help.
