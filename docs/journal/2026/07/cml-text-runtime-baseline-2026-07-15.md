# CML Text Runtime Baseline

Date: 2026-07-15

## Context

CML16-07 next audits `string`, `text`, label, description, message, and related
families against the established nonlocalized `Name` and locale-aware
`I18nTitle` axes. The existing simplemodeling-lib `TextSpec` was pending, so
the actual runtime contract was not recorded as executable evidence.

## Confirmed Runtime Behavior

Executable coverage now confirms that the current `Text` implementation:

- is nonlocalized;
- accepts Scala string lengths from 0 through 8192;
- preserves printable source text exactly;
- rejects over-maximum input through `Consequence`;
- rejects newline and other non-printable control characters.

## Decision Boundary

This is evidence about the current runtime implementation, not acceptance of
`Text` as the final CML narrative-text type. In particular, rejecting newline
input conflicts with the likely needs of multiline body text. Phase 16 must
decide whether CML `text` denotes this printable scalar contract, a multiline
contract, a locale-aware contract, or more than one semantically distinct
predefined type before changing driver models.
