# Semantic Message Flow model ownership

## Status

Planning note. Cozy owns the normative Semantic Message Flow model because it owns CML and the component-model language surface.

## Position

Semantic Message Flow is not a primary dynamic design notation. Dynamic behavior remains specified deductively by Workflow and State Machine models. Interaction/sequence-style views are scenario-oriented and are not used as the primary deductive design model.

Message Flow is a complementary, cross-cutting projection over component relationships. It is useful for architecture and responsibility review, data-flow review, integration review, and AI-control review.

## Normative model

Base the model on UML component relationships where practical, but do not constrain the semantics to what the UML metamodel can express directly. CML may extend the relationship semantics, conceptually as a profile/stereotype over Dependency/Association/Connector.

A message-flow relationship should be representable as structured data. Candidate semantic dimensions include:

- source and target component/port
- logical control direction
- data direction
- communication timing: synchronous / asynchronous
- control style: normal / continuation-IoC
- protocol / message type where known
- annotations and provenance

These dimensions are orthogonal. In particular, synchronous/asynchronous and normal/IoC are different axes.

For IoC, the normative control direction is the *logical* control direction, not the physical API-call direction. Continuation Protocol may physically be invoked by an orchestrator while logical control is delegated to the reasoning agent.

## Diagram notation

The Message Flow Diagram is informative/reference notation derived from the model, not the source of truth.

One relationship is rendered as exactly one line. Control and data must not be split into separate parallel lines.

Endpoint symbols encode flow semantics:

- filled arrow: control + data
- line/open arrow: control only
- hollow arrow: data only

Line treatment may supplement interaction semantics. IoC should be indicated on the same line, with a circled `I` marker at the line center; color may be used only as a secondary aid for synchronous, asynchronous, and IoC distinctions.

The exact rendering specification should remain subordinate to the semantic model.

## SAR / CAR projection

Cozy should define how CML/SAR/CAR component information can produce a normalized Message Flow Model and a deterministic message-flow component diagram.

Desired pipeline:

```text
CML / SAR / CAR
    -> Component relationships
    -> Semantic Message Flow Model
    -> deterministic diagram / SVG
```

The structured model and deterministic diagram are intended to be consumed by review tools. AI may decorate a generated SVG into an infographic, but must not change component identities, relationships, endpoint semantics, directions, or interaction attributes.

## Responsibility boundary

- Cozy: normative CML/message-flow semantics, projection rules, reference notation.
- CNCF/runtime: runtime facts required to populate protocol/control information when applicable.
- textus-cbd-support: consumes the model for review, lint, comparison, review views, and infographic preparation.
- SimpleModeling.org articles: explanatory/informative material, not the normative source.

## Follow-up

Define a future Cozy phase for the metamodel, CML surface, SAR/CAR projection, deterministic renderer contract, and compatibility with existing component-model generation. Do not make Message Flow a replacement for Workflow or State Machine.
