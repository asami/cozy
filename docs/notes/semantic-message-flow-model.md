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


## Confirmed notation grammar

The following grammar is confirmed for the reference Message Flow Diagram.

### One relationship, one line

A semantic relationship is rendered with exactly one line. Control flow and data flow of the same relationship are encoded at the endpoints of that line; they are never split into parallel control/data lines.

Multiple lines between the same components are allowed only when they represent genuinely different semantic relationships, not request/response details of one relationship.

### Endpoint symbols

Endpoint symbols are independent of the line itself.

- **filled arrow**: control flow and data flow arrive at that endpoint
- **open line arrow**: control flow only arrives at that endpoint
- **hollow arrow**: data flow only arrives at that endpoint
- **no arrow**: neither control nor data arrives at that endpoint

For example, a human operating a dashboard while reading information from it is represented by one relationship with:

- open control arrow at the dashboard endpoint,
- hollow data arrow at the Human endpoint.

The existence of a technical request/response exchange does not make a relationship semantically bidirectional.

### Interaction attributes

Interaction attributes modify the same relationship line.

- synchronous: solid line
- asynchronous: dashed line
- continuation-IoC: circled `I` at the center of the same solid or dashed line
- color: supplementary information only; semantics must remain complete in monochrome

Color is never the sole carrier of semantics. Synchronous/asynchronous and normal/Continuation-IoC are orthogonal, so asynchronous IoC is represented by a dashed line with the circled `I` marker.

### Continuation / IoC

A Continuation/IoC relationship represents the complete stable interaction pattern, including initial command, continuation callbacks/responses, and final end/result.

The diagram shows the architectural delegation direction, for example:

- `textus-orchestrator -> Dots`
- `textus-orchestrator -> OpenClaw`
- `sm-workflow -> Codex`

Physical continuation callbacks in the reverse direction are protocol details and do not create additional architectural Message Flow lines.

### Review interpretation

Message Flow expresses the primary logical control and data relationship, not all physical messages. This preserves review value: otherwise ordinary request/response behavior would make most relationships appear bidirectional and obscure responsibility boundaries.


### Drawing-tool constraint

The reference notation MUST be reproducible with ordinary Draw Editor primitives: straight line, dashed line, standard arrowheads, circle, and text. It must not depend on custom SVG-only geometry. Arrowheads must remain distinct from the relationship line and must not overlap component shapes.

## Reference assets

- `semantic-message-flow-grammar-v4.svg`: precise grammar/reference notation.
- `semantic-message-flow-infographic-v4.svg`: explanatory infographic using the same deterministic symbols.
- `semantic-message-flow-overall-architecture.svg`: architecture example using the notation.

The grammar SVG is authoritative for the reference rendering. The infographic is explanatory and must not introduce additional symbols.
