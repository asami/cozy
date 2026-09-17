# Phase 54: CBD Support Semantic Capability Foundation

Status: PLANNED

Plan date: 2026-09-07
Reconciled: 2026-09-18
Primary downstream consumer: Textus CBD Support
Capability IR consumer: Phase 64
Successor: [Phase 54.1](phase-54.1.md)

## Goal

Establish the stable, source-attributed, versioned semantic-metadata foundation
that Textus CBD Support and later Capability Model IR use to construct semantic
views and references without reparsing CML source or reconstructing meaning from
display names.

Cozy owns CML/model transformation, semantic IR, stable identity, and the
published contract. Textus CBD Support owns its views and reports insufficient
supplier semantics as explicit gaps. Phase 64 owns first-class Application /
Component Capability IR and consumes the semantic-reference foundation defined
here. This Phase does not render a Dashboard, edit SimpleModeling.org, or
implement CNCF runtime semantics.

## Semantic-strength rule

```text
Model / view requirement
  -> required semantic capability
  -> CML / Cozy semantic IR
  -> versioned published contract
  -> faithful downstream projection / reference
```

Every claimed semantic capability must be classified as guaranteed, faithfully
derivable from admitted semantic IR, partially represented, or missing. Missing
meaning remains explicit absence; generators and consumers must not invent
lifecycle, actor, causal, terminology, grouping, or ontology semantics.

## Bottom-up semantic-grounding rule

The semantic foundation is not a project to construct a complete ontology.
Ontology-like concepts are used as a direction-setting discipline for knowledge
accumulated from actual modeling and development work.

```text
Documents / Knowledge / Use Cases / Models / Capabilities
                         |
                         v
                  Concept discovery
                         |
                         v
                  Glossary / BoK
                         |
       Definition / Alias / Relation / Context / Evidence
                         |
                         v
              stable semantic references
                         |
             +-----------+-----------+
             |                       |
             v                       v
        CBD Support             Phase 64 Capability IR
```

Glossary / BoK acts as a semantic hub. Stable concepts and relations are
promoted when justified by practical evidence; formal ontology completeness is
neither required nor inferred.

Capability and Glossary form complementary axes:

```text
Capability = what the Application / Component can do
Glossary   = what the concepts used by that Capability mean
```

Phase 54 therefore must make it possible for later model elements, especially
Application Capability in Phase 64, to retain stable terminology/domain-concept
references with provenance and explicit absence handling.

## Reconciled execution sequence

The 2026-09-09 execution split remains the delivery structure. The GitHub
semantic-capability model is the requirement baseline. Each child is planned;
no implementation, validation, or acceptance evidence has been moved.

| Phase | Closure result | GitHub capability coverage | Estimate |
| --- | --- | --- | --- |
| 54 | Capability inventory, stable ID, source provenance, explicit absence, compatibility inventory, semantic-reference vocabulary, and a versioned consumer-neutral publication foundation. | Inventory and identity | 7–8 h |
| 54.1 | Faithful Structure metadata. | Structure | 6–8 h |
| 54.2 | Faithful Classification and independent powertype dimensions. | Classification | 4–6 h |
| 54.3 | Faithful Workflow and StateMachine metadata with dynamic/cross-reference and admitted causal links. | Workflow and StateMachine | 6–8 h |
| 54.4 | Faithful Use Case and Actor metadata with stable cross-view handoff and references suitable for Capability linkage. | Use Case and Actor | 4–6 h |
| 54.5 | Faithful Terminology / BoK references, context/provenance, and absence handling usable as semantic grounding by later Capability IR. | Terminology / BoK | 4–6 h |
| 54.6 | Faithful Event Storming causal traversal on admitted semantics. | Event Storming | 4–6 h |
| 54.7 | Versioned semantic-strength publication contract and CBD Support/Capability-oriented consumer fixtures. | Contract and fixtures | 4–6 h |

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-54-01 | Inventory semantic IR and generated metadata for every planned CBD Support/model capability; classify each capability without guessing. | planned |
| MMD-54-02 | Freeze stable model-element IDs, source provenance, explicit absence, cross-reference vocabulary, and the common semantic-reference shape used by later Capability IR. | planned |
| MMD-54-03 | Define the versioned, consumer-neutral semantic-metadata envelope and compatibility policy that later child projections and Phase 64 references extend/consume. | planned |
| MMD-54-04 | Produce the foundation handoff for Structure, Classification, dynamic, Use Case, Terminology, Event Storming, final fixtures, and Phase 64 Capability grounding. | planned |

## Closure criteria

- CBD Support and later Cozy model IR can address the published foundation
  without parsing CML source or guessing identities from names.
- Glossary / BoK terms can be referenced with stable identity, context/source
  provenance where admitted, and explicit absence semantics.
- The foundation makes unsupported semantic strength explicit rather than
  synthesizing a policy or ontology relation.
- Phase 64 can consume the same identity/reference vocabulary for Capability
  semantic grounding rather than defining a parallel term identity system.
- The publication envelope preserves source attribution and compatibility for
  later child extensions.
- The matching checklist, focused validation, review, and release closure for
  this foundation child are complete; all detailed semantic projections remain
  separately planned.

## Non-goals

- Building or proving a complete formal ontology.
- Inferring broader/narrower, sameAs, causal, lifecycle, or grouping relations
  that are not supported by admitted evidence.
- Rendering a CBD Support view or accepting that external consumer.
- Publishing Structure, Classification, Workflow, StateMachine, Use Case,
  Terminology, or Event Storming projection detail before their child phases.
- Editing, building, registering, publishing, deploying, uploading, or
  pushing SimpleModeling.org.
- CNCF runtime lifecycle enforcement or workflow execution.

## References

- [Phase 54 checklist](phase-54-checklist.md)
- [Phase 54.1](phase-54.1.md)
- [Phase 54.5](phase-54.5.md)
- [Phase 64](phase-64.md)
- [CBD Support semantic-metadata direction](../notes/cml-analysis-view-semantic-metadata.md)
- [Component Dashboard model metadata note](../notes/component-dashboard-model-metadata.md)
- [SimpleModeling model integration boundary](../design/simplemodeling-model-integration-boundary.md)
