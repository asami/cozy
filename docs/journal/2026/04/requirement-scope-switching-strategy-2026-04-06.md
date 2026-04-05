/*
 * @since   Apr.  6, 2026
 * @version Apr.  6, 2026
 * @author  ASAMI, Tomoharu
 */

# Requirement Scope Switching Strategy

## Policy

Requirement-model elements are handled with explicit scope levels:

- `System`
- `Subsystem`
- `Component`

The current top-level CML requirement elements are interpreted as subsystem-scoped by default.

## Default Interpretation

When requirement-model elements are written at CML top level, they are treated as belonging to an implicit subsystem.

The current target set is:

- `VISION`
- `USE CASE`
- `CAPABILITY`
- `QUALITY`
- `CONSTRAINT`

This implicit subsystem is a modeling device for scope control. It prevents top-level requirement descriptions from being mixed into component-local metadata.

## Implementation Direction

The switching work follows these rules:

1. Top-level requirement elements are no longer copied into component definition metadata.
2. Top-level requirement elements are copied into subsystem definition metadata.
3. If no explicit subsystem definition exists, an implicit subsystem definition is synthesized.
4. Component-local requirement descriptions remain under `COMPONENT` and `SERVICE`.

## Projection Consequence

This scope change implies the following output behavior:

- `component help` shows component-scoped requirement information only.
- top-level requirement information does not appear in `component help`.
- top-level requirement information is exposed through subsystem-level help and related projections.

## Current Note

This is a scope-correction step rather than a final subsystem grammar design.

The immediate objective is to align projection behavior with the intended requirement-model scope before introducing richer subsystem-specific requirement syntax.
