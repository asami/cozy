# CML Grammar

status=implemented-baseline
updated_at=2026-07-16
target=/Users/asami/src/dev2025/cozy

## Purpose

This document is the design-side baseline for CML grammar that is already
implemented in Cozy.

Its role is to describe the established grammar foundation after a feature
has moved out of active grammar design.

Role:

- implemented grammar baseline
- stable design reference
- not the place for active in-flight grammar changes

## Relationship To Other Documents

- `docs/design/cml-grammar.md`
  - implemented grammar baseline
- `docs/notes/cml-grammar-latest.md`
  - active grammar work that has not yet entered the accepted baseline
- `docs/spec/cml-value-datatype-contract.md`
  - accepted Phase 16 behavior contract
- `docs/journal/...`
  - discussion history and handoff notes

## Current Baseline

At the current stage, the implemented CML grammar baseline is represented by
the accepted phase contracts already reflected in Cozy parser/modeler behavior.

Typical established areas include:

- top-level structural sections
  - `ENTITY`
  - `VALUE`
  - `DATATYPE`
  - `POWERTYPE`
  - `STATEMACHINE`
  - `EVENT`
  - `OPERATION`
  - `COMMAND`
  - `QUERY`
  - `COMPONENT`
  - `COMPONENTLET`
  - `EXTENSIONPOINT`
  - `SUBSYSTEM`
- literate model interpretation
  - structural layer
  - metadata layer
  - narrative layer
- accepted entity/value/event/component grammar
- accepted aggregate/view first-line grammar

## Phase 16 Accepted Operation Grammar

A reusable operation input is authored as a top-level `VALUE` with an explicit
input role:

```cml
# VALUE

## SearchItems
- input-kind :: QUERY

### ATTRIBUTE
| name | type | multiplicity |
|------|------|--------------|
| text | text | ?            |
```

An operation may reference that Value with `INPUT/TYPE`. A one-use input may
instead define `INPUT/ATTRIBUTE` or a named `INPUT/VALUE` plus `ATTRIBUTE`.
The enclosing operation supplies the input kind for local Values.

Output uses the corresponding Result grammar: `OUTPUT/ATTRIBUTE`, a named
`OUTPUT/VALUE`, or `OUTPUT/TYPE` referencing a declared or CNCF predefined
Result. A scalar output type is not a Result.

Referenced and local definitions are mutually exclusive in each direction.
Empty input/output sections and operation/input kind mismatch are invalid.

## Phase 16 Accepted Datatype Grammar

A plain `DATATYPE` is a nominal scalar. Its underlying attribute and typed
domain constraints are retained:

```cml
# DATATYPE

## LoginName

### ATTRIBUTE
| name  | type   | multiplicity | min-length | max-length | pattern  |
|-------|--------|--------------|------------|------------|----------|
| value | string | 1            | 5          | 12         | ^user.+$ |
```

A Datatype with multiple meaningful attributes is structured. A closed
vocabulary uses `POWERTYPE`; lifecycle state and owned transitions use an
entity `STATEMACHINE`.

Text length uses `min-length` and `max-length`. Numeric `min` and `max` do not
mean text length. Web metadata projects accepted domain constraints but does
not define them.

The semantic predefined baseline includes nonlocalized `name`, locale-aware
`title`, and locale-aware `text`. One `title` or `text` type accepts both one
locale entry and multiple locale entries. Length constraints on locale-aware
text apply independently to every entry.

## Active Change Policy

When grammar is still being discussed or actively implemented:

- keep the active contract in `docs/notes/cml-grammar-latest.md`
- keep discussion history in `docs/journal`
- move stabilized results here only after they are no longer in active flux

## Remaining Active Grammar Work

`docs/notes/cml-grammar-latest.md` continues to hold grammar work that is not
part of this accepted baseline. In particular, unresolved locale-set policy,
secret redaction, driver-specific normalization, and semantic text ranges are
not implied by the Phase 16 baseline above.
