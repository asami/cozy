# CML Name Baseline

Date: 2026-07-15

## Context

CML16-07 uses `name = Name` as the nonlocalized counterpart to the
locale-aware `title = I18nTitle` baseline. Existing `NameSpec` covered only one
interior value and the upper overflow case, leaving the complete range and
failure model implicit.

## Confirmed Behavior

Executable coverage now confirms that:

- `Name` is a stable nonlocalized semantic text value;
- the generic accepted length range is 1 through 256 characters;
- the minimum and maximum boundary values preserve their exact source text;
- empty and over-maximum values fail through `Consequence` when parsed.

This establishes the two baseline axes used by the next catalog audit:
nonlocalized `Name` and locale-aware `I18nTitle`. It does not imply that login
names, identifiers, labels, descriptions, or messages should reuse either
contract without their own domain and range analysis.
