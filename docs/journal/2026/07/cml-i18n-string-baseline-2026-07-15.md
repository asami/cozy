# CML I18nString Baseline

Date: 2026-07-15

## Context

`I18nString` is the shared runtime value beneath locale-aware title, label,
brief, summary, description, text, and message wrappers. Its existing spec was
pending, so Phase 16 previously depended on `I18nTitleSpec` alone for evidence
about shared storage and codec behavior.

## Confirmed Behavior

Executable coverage now confirms that:

- plain decoding creates one entry tagged with the execution locale;
- structured JSON round-trips ordered locale/value entries without collapse;
- effective language fallback does not mutate stored entries;
- plain text beginning with `{` is escaped during encoding and restored as
  plain text during decoding rather than interpreted as structured JSON.

## Remaining Decisions

This baseline does not decide duplicate-locale rejection, locale-tag
normalization, default-locale policy, allowed-locale constraints, or per-entry
text ranges. It also does not make every I18N wrapper a canonical CML type.
Those decisions remain part of the semantic family audit before driver source
changes.
