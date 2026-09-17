# Phase 54: CBD Support Semantic Capability Foundation

Status: PLANNED

Plan date: 2026-09-07
Reconciled: 2026-09-17
Primary downstream consumer: Textus CBD Support
Successor: [Phase 54.1](phase-54.1.md)

## Goal

Establish the stable, source-attributed, versioned semantic-metadata foundation
that Textus CBD Support uses to construct downstream semantic views without
reparsing CML source or reconstructing meaning from display names.

Cozy owns CML/model transformation, semantic IR, stable identity, and the
published contract. Textus CBD Support owns its views and reports insufficient
supplier semantics as explicit gaps. This Phase does not render a Dashboard,
edit SimpleModeling.org, or implement CNCF runtime semantics.

## Semantic-strength rule

```text
CBD Support view requirement
  -> required semantic capability
  -> CML / Cozy semantic IR
  -> versioned published contract
  -> faithful downstream projection
```

Every claimed capability must be classified as guaranteed, faithfully
derivable from admitted semantic IR, partially represented, or missing. Missing
meaning remains explicit absence; generators and consumers must not invent
lifecycle, actor, causal, terminology, or grouping semantics.

## Reconciled execution sequence

The 2026-09-09 execution split remains the delivery structure. The GitHub
semantic-capability model is the requirement baseline. Each child is planned;
no implementation, validation, or acceptance evidence has been moved.

| Phase | Closure result | GitHub capability coverage | Estimate |
| --- | --- | --- | --- |
| 54 | Capability inventory, stable ID, source provenance, explicit absence, compatibility inventory, and a versioned consumer-neutral publication foundation. | Inventory and identity | 7–8 h |
| 54.1 | Faithful Structure metadata. | Structure | 6–8 h |
| 54.2 | Faithful Classification and independent powertype dimensions. | Classification | 4–6 h |
| 54.3 | Faithful Workflow and StateMachine metadata with dynamic/cross-reference and admitted causal links. | Workflow and StateMachine | 6–8 h |
| 54.4 | Faithful Use Case and Actor metadata with stable cross-view handoff. | Use Case and Actor | 4–6 h |
| 54.5 | Faithful Terminology / BoK references and absence handling. | Terminology / BoK | 4–6 h |
| 54.6 | Faithful Event Storming causal traversal on admitted semantics. | Event Storming | 4–6 h |
| 54.7 | Versioned semantic-strength publication contract and CBD Support-oriented consumer fixtures. | Contract and fixtures | 4–6 h |

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-54-01 | Inventory semantic IR and generated metadata for every planned CBD Support view capability; classify each capability without guessing. | planned |
| MMD-54-02 | Freeze stable model-element IDs, source provenance, explicit absence, and cross-reference vocabulary. | planned |
| MMD-54-03 | Define the versioned, consumer-neutral semantic-metadata envelope and compatibility policy that later child projections extend. | planned |
| MMD-54-04 | Produce the foundation handoff for Structure, Classification, dynamic, Use Case, Terminology, Event Storming, and final fixture children. | planned |

## Closure criteria

- CBD Support can address the published foundation without parsing CML source
  or guessing identities from names.
- The foundation makes unsupported semantic strength explicit rather than
  synthesizing a policy.
- The publication envelope preserves source attribution and compatibility for
  later child extensions.
- The matching checklist, focused validation, review, and release closure for
  this foundation child are complete; all detailed semantic projections remain
  separately planned.

## Non-goals

- Rendering a CBD Support view or accepting that external consumer.
- Publishing Structure, Classification, Workflow, StateMachine, Use Case,
  Terminology, or Event Storming projection detail before their child phases.
- Editing, building, registering, publishing, deploying, uploading, or
  pushing SimpleModeling.org.
- CNCF runtime lifecycle enforcement or workflow execution.

## References

- [Phase 54 checklist](phase-54-checklist.md)
- [Phase 54.1](phase-54.1.md)
- [CBD Support semantic-metadata direction](../notes/cml-analysis-view-semantic-metadata.md)
- [Component Dashboard model metadata note](../notes/component-dashboard-model-metadata.md)
- [SimpleModeling model integration boundary](../design/simplemodeling-model-integration-boundary.md)
