Literate Model Specification (Latest, Cozy)
============================================

status=frozen-snapshot
updated_at=2026-03-25
category=specification

# 1. Purpose

This document updates the previous concept memo into an implementation-aligned
specification snapshot for Cozy CML.

It defines how one CML document is interpreted as:

- executable structural DSL
- machine-consumable metadata DSL
- human/AI narrative

This specification is aligned with the current parser/generator contract in:

- `/Users/asami/src/dev2025/cozy/docs/notes/cml-grammar-latest.md`

# 2. Literate Model Definition

A Literate Model is a single source artifact where structure, metadata, and
narrative coexist in one document and are consumed by different interpreters.

- Structural interpreter: builds domain AST and generation inputs.
- Metadata interpreter: builds behavioral and integration metadata.
- Narrative interpreter: preserves semantics and rationale for humans/AI.

# 3. Meta-Grammar

CML supports multiple equivalent syntactic forms:

- section structure (heading-based)
- dl list
- table
- hocon (section body)
- yaml (section body)

These forms normalize to the same AST shape where semantics are equivalent.

Keyword convention in specification text:

- grammar keywords are written in uppercase (`ENTITY`, `EVENT`, `OPERATION`, ...)
- current parser matching is case-insensitive unless explicitly restricted

# 4. Section Classification

## 4.1 Structural DSL Sections

Structural sections define executable model structure.

Current accepted top-level structural sections:

- `ENTITY`
- `EVENT` (top-level/global)
- `OPERATION`
- `COMMAND` (operation input value type definition)
- `QUERY` (operation input value type definition)
- `COMPONENT`
- `COMPONENTLET`
- `EXTENSIONPOINT`
- `SUBSYSTEM`

Entity-scoped structural sections include:

- `ATTRIBUTE`
- `FEATURES`
- `STATEMACHINE`
- `AGGREGATE`
- `VIEW`
- `EVENT` (entity-scoped event definitions)

## 4.2 Metadata DSL Sections

Metadata sections modify generation/runtime metadata without defining a new
top-level model node.

Representative metadata keys/sections currently in active use:

- `TYPE`, `INPUT`, `OUTPUT`, `PARAMETER` (operation)
- `CATEGORY`, `KIND`, `SELECTOR`, `ACTION_NAME`, `PRIORITY` (event)
- `COORDINATE`, `EXTENSIONBINDING`, `CONFIG` (component/subsystem)
- attribute options:
  - `db_column_name`
  - `db_column_type`
  - `external_name`

## 4.3 Narrative Sections

Narrative sections provide design intent and explanatory context.
They are not direct code-generation instructions.

Typical narrative headings:

- `Overview`, `Background`, `Notes`, `Rationale`, `Usage`, `NonGoals`

# 5. Interpretation Pipeline

The practical interpretation model is:

1. Parse document structure and normalize equivalent syntax forms.
2. Classify sections into structural/metadata/narrative roles.
3. Build structural AST from structural sections.
4. Apply metadata overlays to structural nodes.
5. Keep narrative content as non-executable context.
6. Run deterministic validation.
7. Generate artifacts consumed by Cozy/CNCF runtime.

# 6. Determinism and Validation

The Literate Model contract requires deterministic behavior for equivalent input.
Current concrete rules include:

- `STATEMACHINE`
  - missing `ON` -> failure
  - unknown transition target -> failure
  - undeclared event reference (when event list exists) -> failure
  - conflict resolution: priority first, then declaration order
- `OPERATION`
  - kind/input mismatch (`COMMAND` vs query-value input, `QUERY` vs command-value input) -> failure
  - dual definition (`INPUT` + `PARAMETER`) must be field-consistent
- `COMPONENT` / `SUBSYSTEM`
  - invalid coordinate format -> failure
  - deterministic normalized records

# 7. Runtime-Oriented Mapping

Current major mapping points:

- `ENTITY` -> generated entity value types and service/action scaffolding
- `STATEMACHINE` -> `stateMachineTransitionRules`
- `EVENT` -> `eventReceptionDefinitions`
- `OPERATION` -> `operationDefinitions`
- `COMPONENT`/`SUBSYSTEM` -> `componentDefinitionRecords`/`subsystemDefinitionRecords`

Notes:

- `EVENT` is defined via `EVENT` sections (not `EventReception` section names).
- Entity-scoped and top-level `EVENT` definitions are merged.
- `COMPONENT` is optional; when omitted, a default component is generated.

# 8. Authoring Constraints

- Keep structural declarations in structural sections.
- Put generation-affecting options in metadata sections/fields.
- Keep narrative close to related structure, but avoid duplication.
- For `FEATURES`, use a standalone heading (`### FEATURES`) and place
  `EXTENDS = [...]` in body text.

# 9. Scope Boundary

This document is a latest implemented snapshot, not a future proposal bucket.

- Accepted grammar and behavior belong here and in `cml-grammar-latest.md`.
- Experimental grammar proposals belong in:
  - `/Users/asami/src/dev2025/cozy/docs/design/cml-grammar-draft.md`

# 10. Verification Snapshot

Implementation alignment was confirmed against current Cozy tests, including:

- `cozy.modeler.ModelerGenerationSpec` (state machine/event/operation/component-subsystem coverage)

This document should be maintained as an implementation-aligned specification snapshot.

# 11. Address Literate Extension Addendum (Partially Implemented)

status=partially-implemented
added_at=2026-03-24
updated_at=2026-03-25

This addendum captures implementation work needed to realize the intended
Literate Model authoring style (rich narrative + executable structure) in a
single CML source such as `address.cml`.

## 11.1 Authoring Principle Update

When structural and narrative intents conflict, the model should preserve:

1. executable determinism for structural sections
2. narrative richness for human/AI interpretation

without forcing narrative text into pseudo-structural syntax.

## 11.2 Proposed Structural Compatibility

- introduce `VALUE` compatibility alias at top-level
- normalize to `ENTITY(kind=VALUE)` in AST
- keep generator semantics deterministic and equivalent to explicit `ENTITY`

## 11.3 Metadata Enrichment (Implemented)

At attribute level, normalize optional constraint metadata:

- `min_length`
- `max_length`
- `pattern`
- `format`

These keys may be written in section-body YAML/table/dl forms and map to
`record.v2.Constraint` in the current implementation:

- `min_length` -> `CMinLength`
- `max_length` -> `CMaxLength`
- `pattern` -> `CRegex`
- `format` -> `CFormat` (`email` / `uuid` / `uri` / `url`)

Compatibility note:
- when running with an older `goldenport-record` that does not provide
  `CFormat`, format constraints are emitted as equivalent `CRegex`.

## 11.4 Classification Transparency

For literate authoring feedback, parser/modeler should expose classification
results of each section as:

- structural
- metadata
- narrative

with section path and normalized target node.

## 11.5 Scope and Compatibility

- Section `11.3` is implemented and aligned with Cozy latest grammar.
- Sections `11.2` and `11.4` remain proposal-track; sections 1-10 remain
  normative for the frozen snapshot.
