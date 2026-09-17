# Phase 64 - Capability Model IR and CNCF Projection

Status: planned
Planned at: 2026-09-16
Reconciled: 2026-09-18
Development item: DEV-032
Primary owner: Cozy
Predecessor: Phase 63.1 (after Phase 63 contract closure)
Semantic foundation: Phase 54 / 54.5
Upstream contract: CNCF Phase 78
Downstream consumer: Textus CBD Support Phase 11

## Purpose

Add first-class Capability Model IR support to Cozy and generate the admitted
CNCF Component Capability projection. Capability remains non-instantiated;
Cozy preserves model meaning and provenance while executable realizations stay
in Operation, Workflow, and StateMachine contracts.

The reconciled SimpleModeling direction treats Use Case and Capability as
complementary models with different responsibilities:

```text
Use Case              = discovery / understanding model
Application Capability = application structure / development-management model
Component Capability   = architectural capability model
```

Application Capability is discovered inductively from concrete Use Cases and
other requirement evidence, then becomes a stable axis for application
structure, planning, architecture, verification, and development status. It is
not a renamed Use Case Group and is not contained by a Use Case hierarchy.

## Capability / Glossary semantic axis

Capability describes what the Application or Component can do. Glossary / BoK
references ground what the terms used by that Capability mean.

```text
Use Cases / Goals
      |
      | discovery / induction
      v
Application Capability ---- uses concepts ----> Glossary / BoK
      |
      v
Component Capability
      |
      v
Operation / Workflow / StateMachine
```

Phase 64 must preserve stable references from Capability declarations to
admitted terminology/domain concepts where such references exist. It must not
require a complete ontology. Semantic relations are admitted bottom-up from
actual modeling and development evidence; missing semantics remain explicit.

Phase 54 / 54.5 supplies the stable semantic-reference and Terminology / BoK
foundation. Phase 64 consumes that foundation rather than inventing a parallel
term identity model.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| CAP-64-01 | Freeze the Application/Component Capability IR, identity/reference model, and semantic-reference boundary against Phase 54 and CNCF Phase 78. | planned |
| CAP-64-02 | Admit CML syntax and parser/metamodel support for Application and Component Capability without Use Case Group containment semantics. | planned |
| CAP-64-03 | Implement semantic validation and source-correlated diagnostics, including Goal/Use Case, Capability, realization, and admitted Glossary/BoK references. | planned |
| CAP-64-04 | Generate the versioned CNCF Component Capability projection with deterministic provenance while retaining Application Capability as the application-management IR. | planned |
| CAP-64-05 | Prove CNCF admission and cbd-support consumption with a real fixture covering Use Case-to-Capability discovery evidence and terminology grounding. | planned |

## Required behavior

- Application Capability can be discovered/derived from multiple Use Cases and
  requirement evidence; no `Use Case Group -> Capability` containment or
  one-to-one derivation is required.
- Use Case and Application Capability are many-to-many: a Use Case may require
  several Capabilities and a Capability may support several Use Cases.
- Application Capability is representable independently of any particular Use
  Case so it can serve as the stable application structure / management axis.
- Component Capability is discoverable from scenarios and architecture evidence
  without embedding executable procedure.
- Capability may carry stable references to Goal, Use Case, Domain Concept,
  Glossary / BoK term, Use Case Slice, and realization evidence as admitted by
  the surrounding model contracts.
- Stable qualified identity and source location survive normalization and
  generation.
- Provided/required direction and realization references are explicit.
- Duplicate, missing, ambiguous, cyclic, and incompatible references fail with
  structured, source-correlated diagnostics.
- Realization declarations contain mappings, not ordering, retry,
  compensation, authorization, or runtime state.
- Generated output matches CNCF's admitted ABI/version and participates in
  multi-CML provenance without enumeration-order selection.
- Ontology completeness is never a validation requirement; Cozy validates only
  semantic relations actually admitted by the model/contracts.

## View / model boundary

Capability Map and Use Case Map are views over admitted model relationships,
not reasons to introduce competing containment models.

- Capability Map is the primary application structure / management view.
- Use Case Map remains available as an Actor / Goal / Interaction-oriented
  derived view.
- Use Case Group is not required as the structural parent of Capability. If it
  remains for another purpose, that purpose must be explicit and independent of
  Capability identity.

## Completion conditions

- Design/specification freeze Capability IR, syntax, normalization, validation,
  semantic grounding, generation, and compatibility boundaries.
- Executable specifications cover accepted Application/Component Capability,
  many-to-many Use Case references, admitted terminology references,
  provided/required references, realization mapping, and each required
  rejection boundary.
- A real CML fixture produces deterministic CNCF-admissible metadata with exact
  source provenance and stable terminology/domain references where present.
- CNCF Phase 78 admits the fixture and Textus CBD Support Phase 11 consumes it
  without CML reparsing, name-derived identity, or invented semantic relations.
- Focused validation and review evidence are recorded without claiming
  downstream Phase closure.

## Non-goals

- Capability runtime instances or persistence.
- Executing Capability directly.
- Defining CNCF runtime semantics or cbd-support presentation.
- Building a complete ontology or requiring formal ontology axioms.
- Treating provisional working notation as pre-approved final CML syntax.
- Collapsing Capability, Availability, Authorization, Permission, and Guard.

## References

- [Development note](../notes/capability-model-ir-support-proposal.md)
- [Development item journal](../journal/2026/09/2026-09-16-capability-model-ir-development-item.md)
- [Phase 54](phase-54.md)
- [Phase 54.5](phase-54.5.md)
- [Phase 64 Checklist](phase-64-checklist.md)
