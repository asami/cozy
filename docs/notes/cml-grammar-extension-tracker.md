# CML Grammar Extension Tracker

status=active-note
updated_at=2026-04-30
target=/Users/asami/src/dev2025/cozy

## 1. Purpose

This note records the current extension status of the active CML grammar work.

It is a management note for the latest known state.
It is intentionally non-normative.

Normative or latest-contract material belongs in:

- `docs/design/cml-grammar.md`
- `docs/notes/cml-grammar-latest.md`

History, rationale, and handoff context belong in:

- `docs/journal/...`

## 2. Current Focus

The current grammar extension focus is to make CML usable as a literate model
for executable component specification, especially around:

- `COMPONENT`
- `SERVICE`
- `OPERATION`
- `RELATIONSHIP`
- user-facing Help/OpenAPI/comment generation

The immediate objective is not to freeze a final standard grammar.
The objective is to keep the grammar executable, useful, and incrementally
refinable while Cozy/CNCF are still evolving.

## 3. Current Extension Status

### 3.1 USE CASE under COMPONENT

Implemented as a provisional grammar extension.

Supported items:

- `SUMMARY`
- `DESCRIPTION`
- `ACTOR`
- `PRIMARY ACTOR`
- `SECONDARY ACTOR`
- `SUPPORTING ACTOR`
- `STAKEHOLDER`
- `GOAL`
- `PRECONDITION`
- `POSTCONDITION`
- `SCENARIO`

Intent:

- capture literate executable specification at component boundary
- preserve actor/stakeholder context with executable model metadata

Forward direction:

- this grammar is expected to converge with a future domain-wide `USE CASE`
  grammar rather than remain a component-only special form
- grammar refinement should therefore keep the underlying use-case structure
  reusable across `DOMAIN`, `COMPONENT`, and `SERVICE`

### 3.2 USE CASE under SERVICE

Implemented as a provisional grammar extension with the same core structure as
`COMPONENT` use cases.

Intent:

- describe service-level use intent separately from operation contract details
- feed generated Help/comment metadata

### 3.3 OPERATION Contract Sections

Implemented as a provisional grammar extension.

Supported items:

- `PRECONDITION`
- `POSTCONDITION`
- `RULE`

Interpretation guideline:

- `PRECONDITION` and `POSTCONDITION` describe operation contract semantics
- `RULE` is for execution/business rules that are not best expressed as type,
  multiplicity, or simple attribute constraints

`RULE` is intentionally broader than invariant-only semantics.

### 3.4 RELATIONSHIP Sections

Implemented as a provisional grammar extension for Entity relationships.

Supported relationship kinds:

- `association`
- `aggregation`
- `composition`

Supported storage modes:

- `association-record`
- `child-parent-id-field`
- `embedded-value-object`

For `composition` with `child-parent-id-field`, `PARENT ID FIELD` is required.
Operation `CHILD ENTITY BINDING` may reference a relationship and Cozy expands
the relationship into generated CNCF child Entity binding metadata before
Scala generation.

For `composition` with `embedded-value-object`, `TARGET` must reference a
`VALUE` definition and `VALUE FIELD` is required. The value field must exist on
the source Entity and use the target VALUE type. Cozy emits relationship
metadata with `targetModelKind = "value"` and does not expand the relationship
into child Entity binding metadata.

SmartDox relationship sections intentionally enforce the authoring rule that a
section heading is followed by one blank line before its content or child
sections.

### 3.5 SCENARIO Step Splitting

Implemented parser behavior:

- numbered steps such as `1.`, `2.`, `3.` are split into distinct steps
- when SmartDox or other upstream formatting collapses multiple step sentences
  into a single line, sentence-boundary splitting is used as a recovery rule

Current recovery heuristic:

- split at sentence boundaries such as `".The ..."`

This is a practical recovery rule, not a final language design.

## 4. Active Design Rules

### 4.1 USE CASE owns actor semantics

Actor-oriented sections are intentionally scoped to `USE CASE`, not to
`OPERATION`.

Current rule:

- `ACTOR`
- `PRIMARY ACTOR`
- `SECONDARY ACTOR`
- `SUPPORTING ACTOR`
- `STAKEHOLDER`

belong to `USE CASE`.

Rationale:

- actor/stakeholder semantics are usage-context concepts
- operation-level grammar should remain focused on executable contract

### 4.2 OPERATION should stay contract-centric

Current preferred direction:

- `PRECONDITION`
- `POSTCONDITION`
- `RULE`

belong to `OPERATION`.

Current non-goal:

- do not duplicate use-case semantics at operation level unless a clear runtime
  or generator need emerges

## 5. Pending / Open Extension Items

### 5.1 SCENARIO step model refinement

The current step splitting is good enough for practical help output, but it is
still heuristic.

Open questions:

- whether `SCENARIO` should gain an explicit `STEP` subgrammar
- whether ordered and unordered steps should be distinguished
- whether step metadata such as `EXPECTED`, `ALT`, or `EXCEPTION` should be
  modeled later

### 5.2 Structured scenario preservation

Current generated metadata preserves scenario names and step vectors.

Possible future refinement:

- richer scenario model for Help/OpenAPI generation
- alternate and exception flow distinctions

### 5.3 Standardization boundary

These extensions are intentionally provisional.

Future work is expected to decide:

- what becomes standard CML
- what remains literate-model metadata
- what should become stricter executable grammar

An important part of that future work is to unify the current component-level
use-case syntax with a domain-level use-case grammar, while continuing
incremental grammar improvement rather than freezing the current local form too
early.

## 6. Working Policy

Until the grammar is frozen:

- prefer executable usefulness over premature formalization
- keep parser/modeler/generator behavior deterministic
- preserve narrative structure when it helps generated artifacts
- avoid adding grammar that forces CNCF/runtime semantics too early

## 7. Related Files

- `/Users/asami/src/dev2025/cozy/docs/notes/cml-grammar-latest.md`
- `/Users/asami/src/dev2025/cozy/docs/design/cml-grammar.md`
- `/Users/asami/src/dev2025/cozy/docs/design/cml-grammar-draft.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/04/cml-literate-grammar-extension-progress-2026-04-06.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/cml-component-subsystem-grammar-handoff.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/cml-component-subsystem-grammar-note-handoff.md`
