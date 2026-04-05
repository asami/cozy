# CML Top-Level Concept Framework

- date: 2026-04-06
- status: draft

---

## Purpose

This note records the current strategy for stabilizing the top-level concept
framework of CML.

The immediate goal is not to fully formalize executable scenario semantics.
The immediate goal is to clarify and stabilize the top-level modeling concepts
that CML should expose as first-class sections.

---

## Current Decision

At this phase, Cozy should prioritize stabilization of top-level concepts over
deepening `Step = Action` semantics.

In other words:

- the top-level concept framework should be clarified first
- executable action semantics for scenario steps can be deferred

This is a deliberate sequencing decision.

---

## Why This Order

If step/action semantics are deepened too early, the design risks collapsing
into execution-oriented detail before the modeling space itself is properly
organized.

The top-level concept framework must be clear first, because it determines:

- what kinds of models CML is for
- how requirement-oriented descriptions are separated from execution-oriented
  descriptions
- how future grammar extensions should be placed

Without that framework, local grammar extensions may become inconsistent or too
tightly coupled to the current runtime implementation.

---

## Strategic Position

CML should be understood as a language spanning multiple top-level modeling
domains, not only one local DSL centered on entities or operations.

The top-level concept framework should therefore be treated as the stable outer
shape of the language.

Within that framework, individual subgrammars can continue to evolve.

---

## Candidate Top-Level Concepts

The following top-level concepts are currently relevant to the direction of
Cozy.

### Requirement-Oriented Concepts

- `VISION`
- `REQUIREMENT`
- `QUALITY`
- `CONSTRAINT`
- `USE CASE`
- `ACTOR`
- `ROLE`
- `RULE`

These concepts are primarily about intent, constraints, interaction, and
requirement structure.

Within this group:

- `REQUIREMENT` should remain suitable for high-level requirement organization
  such as MoSCoW classification
- `QUALITY` should capture non-functional requirements and quality attributes
- `CONSTRAINT` should capture explicit adoption/technology/platform restrictions
  such as mandated operating systems, databases, or deployment policies

`VISION` should also be allowed to carry nearby requirement-shaping items such
as `GOAL` and narrative `CONTEXT`.

Here, `CONTEXT` should initially be treated as descriptive background, not yet
as a fully specialized context-diagram or context-map model.

`RULE` should remain in this conceptual neighborhood, but it should not yet be
frozen as a top-level requirement construct prematurely.

The current direction is to examine `DomainRule` first at the analysis-model
level, build practical usage knowledge there, and only later reconsider whether
and how a top-level requirement-model `RULE` should be introduced.

### Structural / Design Concepts

- `COMPONENT`
- `COMPONENTLET`
- `EXTENSIONPOINT`
- `SUBSYSTEM`
- `ENTITY`
- `VALUE`
- `EVENT`
- `OPERATION`
- `COMMAND`
- `QUERY`
- `POWERTYPE`
- `STATEMACHINE`

These concepts are primarily about structural modeling and executable design.

### Future Execution-Oriented Concepts

- `CAPABILITY`
- `ACTION`

These are promising and strategically important, but they should not be used to
drive the overall top-level concept framework prematurely.

At the same time, `CAPABILITY` should not be treated as merely a runtime-side
concept.

The current direction is:

- treat `CAPABILITY` as a requirement-model concept that can stand alongside
  `USE CASE`
- allow basic descriptive items first
- defer deeper `ACTION`-oriented execution semantics

So, at the current phase:

- `CAPABILITY` is a serious top-level candidate for the requirement-model layer
- `ACTION` remains a later-phase execution-oriented concept

---

## Immediate Policy

The top-level concept framework should be fixed conceptually before going deeper
into executable scenario semantics such as `Step = Action`.

Therefore:

- `USE CASE` should continue to evolve as requirement syntax
- requirement-model concepts should be organized at the top level
- `CAPABILITY` and `ACTION` may be studied, but they are not the immediate
  center of grammar stabilization

---

## Working Interpretation of Priority

### First Priority

Stabilize the top-level concept space of CML.

This includes:

- clarifying which top-level sections exist
- grouping them conceptually
- clarifying how requirement-oriented concepts relate to structural concepts

### Second Priority

Strengthen requirement-oriented grammar, especially around:

- `USE CASE`
- `SCENARIO`
- `INCLUDE`
- `EXTEND`
- `GENERALIZE`
- `REALIZATION`

### Later Priority

Deepen executable scenario semantics, including:

- `Step = Action`
- `CAPABILITY`
- `ACTION`
- action typing and execution binding

More precisely:

- basic requirement-oriented `CAPABILITY` description should be allowed earlier
- deeper `CAPABILITY`/`ACTION` execution semantics should come later

In parallel, context modeling should evolve carefully:

- narrative `CONTEXT` can be supported earlier
- specialized system-context and domain-context-map models should be separated
  later

---

## Practical Consequence for Ongoing Development

For current Cozy work, this means:

- continue documenting and implementing top-level requirement-model concepts
- continue improving `USE CASE` grammar
- do not rush into fully executable action semantics for scenario steps
- treat capability/action design as important, but deferred

This allows the language architecture to mature before committing to a more
specific execution DSL.

---

## Non-Goal of the Current Phase

The current phase is not trying to answer all of the following yet:

- whether every scenario step must become an `Action`
- how `Action` maps to `Command` / `Query`
- how runtime binding should work
- how capability/action semantics should affect the full grammar

Those are later-phase questions.

---

## Summary

The current Cozy strategy is:

- stabilize the top-level concept framework first
- treat requirement-model concepts as the current center of grammar evolution
- introduce `CAPABILITY` first as a requirement-model concept with basic
  descriptive items
- postpone deeper `Step = Action` formalization until the language-level
  conceptual framework is sufficiently stable

---

## Related Notes

- `/Users/asami/src/dev2025/cozy/docs/journal/2026/04/cml-capability-action-note.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/04/cml-requirement-syntax-note.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/04/cozy-requirement-modeling-strategy-2026-04-06.md`
