# Cozy Requirement Modeling Strategy

- date: 2026-04-06
- status: draft

---

## Purpose

This note records the current implementation strategy for requirement-oriented
modeling in Cozy.

The goal is to clarify how Cozy should evolve its CML grammar and modeler
behavior so that requirement descriptions become executable literate models,
without over-freezing business-model semantics too early.

---

## Strategic Position

Cozy should not treat `USE CASE` as the whole requirement model.

Instead, Cozy should gradually support a requirement-model layer composed of
multiple modeling elements, including at least:

- `VISION`
- `REQUIREMENT`
- `QUALITY`
- `CONSTRAINT`
- `CAPABILITY`
- `USE CASE`
- `ACTOR`
- `ROLE`
- `RULE`

Among them, `USE CASE` is currently the most implementation-ready focal point,
but it must be positioned as one part of the wider requirement model.

`CAPABILITY` should also be treated as a first-class requirement-model concept,
not only as a future execution artifact.

---

## Current Development Priority

The current priority is:

1. strengthen requirement-model description
2. keep it executable as literate model input
3. postpone broader business-modeling formalization

In practical terms:

- business modeling is not the current first target
- requirement-model syntax and structure should be improved first
- generated Help/comment output should be used as the main immediate payoff

---

## Current Scope of Implementation

### Already implemented

Cozy currently supports requirement-oriented grammar mainly around `USE CASE`.

Implemented areas include:

- top-level `# USE CASE`
- `USE CASE` under `COMPONENT`
- `USE CASE` under `SERVICE`
- `SCENARIO`
- `STEP`
- `ALTERNATE`
- `EXCEPTION`
- `PRECONDITION`
- `POSTCONDITION`
- `RULE`

These are already flowing through parser/modeler/generator/help output.

### Already existing but not yet integrated under one requirement strategy

Cozy/Kaleidox already contain model areas such as:

- `VISION`
- `REQUIREMENT`
- `ACTOR`
- `ROLE`

However, they are not yet reorganized as one coherent requirement-modeling
strategy connected to the current literate executable `USE CASE` work.

That integration remains a future design and implementation task.

---

## Working Interpretation

The intended requirement-model structure is currently understood as follows.

### VISION

`VISION` describes why the system or domain exists and what direction or value
it should realize.

It is also natural to allow `GOAL` and `CONTEXT` as closely related items in
the same neighborhood.

At the current stage, `CONTEXT` should first be understood as narrative
background rather than as a fully specialized diagrammatic model.

### REQUIREMENT

`REQUIREMENT` describes what is needed, constrained, or expected.

At the current strategic interpretation, it is also the preferred place for
high-level requirement organization such as MoSCoW classification.

### QUALITY

`QUALITY` describes non-functional requirements and quality attributes.

It should be suitable for defining concrete development-facing quality demands
such as performance, availability, security, usability, auditability, and
operability expectations.

### CONSTRAINT

`CONSTRAINT` describes explicit restrictions on solution choices, platforms,
technologies, or adoption boundaries.

Typical examples include:

- operating system restrictions
- database restrictions
- deployment restrictions
- mandated protocol or integration choices

### RULE

`RULE` is recognized as important, but it is currently treated as a deferred
design area rather than an immediately stabilized top-level requirement-model
construct.

The working strategy is:

- examine `DomainRule` first in the analysis-model context
- build usage and classification knowledge there
- only later decide how top-level `RULE` should be positioned in the
  requirement-model layer

### USE CASE

`USE CASE` describes externally observable interactions between actors and the
system.

### CAPABILITY

`CAPABILITY` describes what the system is able to provide as a reusable ability
or functional unit.

At the current phase, it should be introduced first with basic descriptive
items, in parallel with `USE CASE`, before deeper `ACTION` semantics are added.

### RULE

`RULE` describes constraints or operational/business rules that must hold and
that are not always best expressed purely as data schema.

### ACTOR / ROLE

`ACTOR` and `ROLE` define participating viewpoints and responsibility contexts
around requirement descriptions.

---

## Planned Evolution Path

The current implementation path should proceed in stages.

### Stage 1. Stabilize USE CASE as executable requirement syntax

This stage is already underway.

Goals:

- stabilize `USE CASE` grammar
- preserve structure through parser/modeler/generator
- expose structured requirement information in Help/comment output

### Stage 2. Add USE CASE relationships

The next major requirement-modeling target should be:

- `INCLUDE`
- `EXTEND`
- `GENERALIZE`
- `REALIZATION`

At first, these should be introduced as structured references and metadata.
They do not need full runtime semantics immediately.

In parallel, Cozy should begin enabling top-level `CAPABILITY` descriptions with
basic requirement-oriented fields such as:

- `SUMMARY`
- `DESCRIPTION`
- actor-related items
- `GOAL`
- `PRECONDITION`
- `POSTCONDITION`

In the same broader requirement-modeling direction, Cozy should also prepare for
top-level:

- `QUALITY`
- `CONSTRAINT`

so that non-functional demands and explicit restrictions do not need to be
misfiled under `REQUIREMENT` or `CAPABILITY`.

By contrast, `RULE` should remain under investigation for now.
It should not be stabilized too early until the `DomainRule` viewpoint in the
analysis model becomes clearer through practical use.

### Stage 3. Reorganize top-level requirement concepts

After `USE CASE` relationships become stable, Cozy should align:

- `VISION`
- `REQUIREMENT`
- `QUALITY`
- `CONSTRAINT`
- `USE CASE`
- `ACTOR`
- `ROLE`

into one coherent requirement-model layer.

### Stage 4. Revisit business modeling

Only after requirement-model description becomes sufficiently stable should Cozy
expand broader business-modeling formalization in earnest.

---

## Domain-Level Direction

The current `COMPONENT` and `SERVICE` use-case grammar should not remain a
local special case forever.

The direction is:

- treat top-level `USE CASE` as domain-level requirement grammar
- treat top-level `CAPABILITY` as a parallel requirement-model concept
- treat `COMPONENT` and `SERVICE` use cases as scoped specializations or
  concretizations
- move gradually toward a shared use-case model across requirement layers

This means current implementation should favor reusable AST and metadata
structures rather than component-specific special handling.

---

## Context Modeling Direction

`CONTEXT` is needed, but it should not be frozen too early into one single
specialized model.

The recommended interpretation is:

- `CONTEXT` near `VISION` is a narrative context description
- system-structure context and DDD context relationships should later become
  specialized models

The likely future split includes at least:

- system context
- domain context map

The first is about external actors, systems, boundaries, and integrations.

The second is about bounded contexts and their semantic relationships.

Both are needed, but they should not be prematurely collapsed into a single
fixed syntax.

---

## OpenAPI Strategy

Requirement-model structures should not be forced into OpenAPI if there is no
natural OpenAPI representation.

Therefore:

- requirement-oriented structured metadata should primarily target Help and
  generated comments
- OpenAPI output should remain conservative
- only naturally mappable information should be projected into OpenAPI

This avoids degrading OpenAPI readability with excessive narrative expansion.

---

## Non-Goals for the Current Phase

The current phase should avoid the following:

- over-formalizing business-model semantics too early
- introducing premature runtime meaning for every requirement construct
- forcing all requirement metadata into OpenAPI
- freezing final standard grammar before implementation experience accumulates

---

## Practical Rule for Ongoing Work

Until the requirement-model layer matures further, Cozy should follow this rule:

- prioritize executable usefulness
- preserve structure
- keep parser/modeler behavior deterministic
- document strategy in journal/notes
- evolve grammar incrementally from real component use

---

## Related Notes

- `/Users/asami/src/dev2025/cozy/docs/journal/2026/04/cml-requirement-syntax-note.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/04/cml-literate-grammar-extension-progress-2026-04-06.md`
- `/Users/asami/src/dev2025/cozy/docs/notes/cml-grammar-extension-tracker.md`
