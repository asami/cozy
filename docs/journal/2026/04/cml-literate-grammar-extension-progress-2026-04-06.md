# CML Literate Grammar Extension Progress

- date: 2026-04-06
- status: draft

---

## Summary

This note records the recent extension work around literate CML grammar for
component/service/use-case oriented modeling.

The work was driven by `textus-user-account` as an executable literate-model
consumer, with downstream confirmation through generated Help output on CNCF.

---

## Implemented in This Round

### 1. USE CASE under COMPONENT

Added provisional grammar support for `USE CASE` sections under `COMPONENT`.

Supported fields:

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

### 2. USE CASE under SERVICE

Added the same provisional structure for `USE CASE` sections under `SERVICE`.

This allowed service-level literate intent to be modeled independently from
operation-level executable contract.

### 3. OPERATION contract sections

Added provisional grammar support for:

- `PRECONDITION`
- `POSTCONDITION`
- `RULE`

The working interpretation is:

- `PRECONDITION` / `POSTCONDITION` express operation contract semantics
- `RULE` is broader than invariants and is used for execution/business rules
  that should not be forced into attribute/type syntax

### 4. Actor semantics kept inside USE CASE

The decision for this round was to keep:

- `ACTOR`
- `PRIMARY ACTOR`
- `SECONDARY ACTOR`
- `SUPPORTING ACTOR`
- `STAKEHOLDER`

inside `USE CASE` only.

They were intentionally not extended into `OPERATION`, because actor semantics
are usage-context semantics rather than operation-contract semantics.

### 4.5 Component USE CASE is a precursor to domain USE CASE grammar

The current `COMPONENT`-level `USE CASE` grammar is not intended to remain an
isolated local syntax forever.

The working direction is:

- continue grammar refinement incrementally
- treat the current component/service use-case structure as a precursor
- converge later toward a domain-wide `USE CASE` grammar shared across model
  layers

This means the current implementation should be read as a practical step toward
shared use-case grammar, not as the final scope boundary of the feature.

### 5. Structured projection to generated/help metadata

The use-case metadata now flows through:

- `kaleidox`
- `cozy`
- `simple-modeler`
- `cloud-native-component-framework`

Component help and service help now expose structured use-case metadata.

### 6. SCENARIO step splitting refinement

The initial implementation preserved numbered steps when clearly separated in
the source.

However, actual help output showed that SmartDox formatting could collapse
multiple steps into a single line, producing help output such as:

- `A user submits ...The service creates ...The user can later ...`

To recover from this, parser-side scenario step splitting was refined so that:

- numbered steps are still split first
- if numbering is not recoverable, sentence-boundary splitting is applied as a
  practical fallback

This is explicitly a practical recovery rule, not a final formal grammar.

---

## Why This Direction Was Chosen

At this stage, Cozy/CNCF are still evolving.

The goal was therefore:

- to keep the grammar executable
- to improve literate specification value immediately
- to avoid over-freezing semantics too early

This is why the implementation currently favors:

- structured metadata preservation
- deterministic parsing/generation
- practical help/comment/OpenAPI usefulness

over strict final standardization.

---

## Observed Result in textus-user-account

`textus-user-account` now acts as a realistic literate-model consumer for:

- component-level use cases
- service-level use cases
- operation-level contract text
- scenario-to-help output

After the scenario step splitting refinement, `structuredUseCases.scenarios.steps`
in help output became readable as proper step vectors instead of collapsed text.

---

## Remaining Open Questions

The following remain intentionally open:

### 1. Should SCENARIO get explicit STEP grammar?

Current implementation uses text-body recovery and numbered-step splitting.

Possible later direction:

- `STEP`
- `ALTERNATE`
- `EXCEPTION`

or another explicit scenario subgrammar.

### 2. How much of this becomes standard CML?

Not all provisional literate extensions should necessarily become permanent
standard grammar.

Later work should distinguish:

- standard executable grammar
- structured literate metadata
- generator/runtime-only annotations

It should also determine how the current component/service use-case grammar is
lifted into a domain-level common grammar without losing the practical gains
already achieved in generated Help/comment output.

### 3. How far should Help/OpenAPI structure go?

The current projection is already useful, but further refinement is possible in:

- scenario rendering
- operation help
- OpenAPI description shaping

---

## Related Files

- `/Users/asami/src/dev2025/cozy/docs/notes/cml-grammar-extension-tracker.md`
- `/Users/asami/src/dev2025/cozy/docs/notes/cml-grammar-latest.md`
- `/Users/asami/src/dev2025/cozy/docs/design/cml-grammar.md`
