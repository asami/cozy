# CML Grammar

status=implemented-baseline
updated_at=2026-04-01
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
  - active latest grammar spec under implementation
- `docs/journal/...`
  - discussion history and handoff notes

## Current Baseline

At the current stage, the implemented CML grammar baseline is represented by
the accepted phase contracts already reflected in Cozy parser/modeler behavior.

Typical established areas include:

- top-level structural sections
  - `ENTITY`
  - `VALUE`
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

## Active Change Policy

When grammar is still being discussed or actively implemented:

- keep the active contract in `docs/notes/cml-grammar-latest.md`
- keep discussion history in `docs/journal`
- move stabilized results here only after they are no longer in active flux

## Current Note

The `OPERATION` input/output refactoring is still active and therefore belongs
to:

- `docs/notes/cml-grammar-latest.md`
- related `docs/journal` discussion notes

It is intentionally not frozen into this baseline yet.
