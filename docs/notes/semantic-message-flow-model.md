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
- interaction pattern: normal / continuation-IoC
- protocol / message type where known
- annotations and provenance

These dimensions are orthogonal. In particular, synchronous/asynchronous and normal/continuation-IoC are different axes.

### Continuation / IoC interaction pattern

Continuation/IoC is modeled as a property of the relationship as a whole, not as one exceptional message inside the relationship.

A typical interaction has a protocol sequence such as:

```text
controller -> worker : initial command / goal
worker -> controller : continuation request(s)
controller -> worker : continuation response(s)
worker -> controller : end / final result
```

The individual messages and their physical call directions belong to the protocol/sequence level. The Message Flow model intentionally abstracts them into one stable interaction relationship.

For example:

- `textus-orchestrator -> Dots`
- `textus-orchestrator -> OpenClaw`
- `sm-workflow -> Codex`

may each be represented as one `continuation-IoC` relationship even though the worker physically calls the controller during continuation and returns the final response to the initial command.

The normative control direction of that relationship expresses the architectural delegation direction: the controller delegates a unit of work and its internal reasoning/execution control to the worker. It must not be reversed merely because a continuation callback is physically initiated by the worker.

This abstraction keeps Message Flow at the component-interaction level rather than turning it into a sequence diagram.

## Diagram notation

The Message Flow Diagram is informative/reference notation derived from the model, not the source of truth.

One relationship is rendered as exactly one line. Control and data must not be split into separate parallel lines.

Endpoint symbols encode flow semantics:

- filled arrow: control + data
- line/open arrow: control only
- hollow arrow: data only

Line treatment may supplement interaction semantics. A `continuation-IoC` relationship should be indicated on the same single relationship line, with a circled `I` marker at the line center; color may be used only as a secondary aid for synchronous, asynchronous, and IoC distinctions. Initial command, continuation callback/response, and final end/result are not rendered as separate relationship lines in the architectural Message Flow view.

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
