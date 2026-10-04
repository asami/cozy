# CML Capability Model Design

This document describes the stable structure and responsibility boundaries of
Capability Model IR. The [Capability Model specification](../spec/cml-capability-model.md)
owns normative semantics and conceptual carriers. [Phase 64](../phase/phase-64.md)
and its [checklist](../phase/phase-64-checklist.md) own progress and acceptance.

## Application and Component kinds with common Foundation identities

Application Capability provides the application structure and management axis.
Component Capability describes architectural meaning available for Component
provided/required declarations. Separate record families keep these
responsibilities visible while sharing the existing qualified model-element
identity domain. Versioned references retain the family distinction.

The [Semantic Foundation](../spec/cml-semantic-foundation.md) supplies common
identities and reference semantics. The normative kind and identity boundaries
are [R1](../spec/cml-capability-model.md#r1-capability-kinds-and-non-instantiated-meaning)
and [R2](../spec/cml-capability-model.md#r2-exact-qualified-identity-and-contract-version).
Runtime Component identity remains owned by CNCF's Component Identity
Specification / Phase 56; the model's Component references and runtime
Component identity occupy separate domains.

## Definitions, declarations, and the relationship graph

The conceptual envelope has separate Application definitions, Component
definitions, Component declarations, and supplied compatibility data.
Definitions express meaning; declarations bind architectural meaning to an
exact Component and direction. Relationship edges retain their own attribution
instead of becoming flattened endpoint lists.

Application demand and discovery are separate many-to-many associations.
Application-to-Component mappings provide an architectural continuation without
choosing a provider. Realization edges continue from a particular Component
declaration to an executable target contract. Application dependency edges have
their own logical requirement role, independent of demand and grounding.

The complete structure is specified in
[R3](../spec/cml-capability-model.md#r3-complete-conceptual-carriers), with
demand/discovery in [R4](../spec/cml-capability-model.md#r4-demand-and-discovery-model-links),
declarations in [R5](../spec/cml-capability-model.md#r5-component-definitions-and-directional-declarations),
and mapping/realization in [R6](../spec/cml-capability-model.md#r6-application-mappings-and-realization-edges).
[R10](../spec/cml-capability-model.md#r10-validation-dependency-cycles-and-directed-compatibility)
defines the dependency and compatibility boundary.

## Independent revision-bearing sources and the Foundation boundary

Capability definitions and edges carry explicit source revisions through
`CapabilitySource`. Edge origins distinguish declared facts from supplied
derivation rules and ordered input sources. Independent attribution allows a
relationship revision to remain visible alongside the definition it connects.

Foundation's legacy source attribution remains in its existing domain.
Capability's revision-bearing source and origin carriers support this new
contract without rewriting Foundation or peer metadata APIs. Importing a
Foundation reference requires explicit source revision data alongside the
existing fact. The source and legacy boundary is specified in
[R7](../spec/cml-capability-model.md#r7-independent-revision-bearing-source-and-origin).

## Grounding, model, realization, and traceability domains

Grounding uses Foundation model or Term targets and the supplied vocabulary
relation/profile domains. The
[Terminology Metadata specification](../spec/cml-terminology-metadata.md)
provides the local context for exact vocabulary authority and literal reference
facts. Grounding retains context and labels as supplied descriptive information.

Model links describe demand or discovery; realization binds executable target
contracts; Specification and Evidence links describe independent traceability.
These distinct roles let consumers follow explicit relationships while retaining
Local/External boundaries and supplied gaps at each edge. Their normative
semantics are [R8](../spec/cml-capability-model.md#r8-grounding-and-vocabulary-authority)
and [R9](../spec/cml-capability-model.md#r9-presence-boundaries-and-independent-traceability).

## Cozy production and CNCF/CBD handoff

Cozy owns CML-facing parsing, normalization, validation, diagnostics, IR, and
deterministic multi-CML production. CNCF Phase 78 owns external Component
Capability semantics, concrete projection/codec/ABI admission, target binding,
and discovery. Textus CBD Support Phase 11 owns ingestion, catalog, and queries
over admitted upstream facts.

The generator separates upstream Application management data from the Component
projection. Its generation context carries independent producer, projection,
ABI, Component/package, and source-revision domains. CNCF's Component Capability
Specification R1-R10 is the external authority; CAP-78-02 supplies the concrete
external contract consumed by Cozy CAP-64-04. The handoff semantics are
[R11](../spec/cml-capability-model.md#r11-generation-domains-and-external-ownership).

The [R12 ownership table](../spec/cml-capability-model.md#r12-implementation-and-proof-boundary)
assigns syntax and actual IR to CAP-64-02, validation to CAP-64-03, generation to
CAP-64-04, and real producer/admission/consumer proof to CAP-64-05. It separates
this conceptual contract from the implementation and executable evidence needed
for the downstream ingestion boundary.
