# Phase 64 - Capability Model IR and CNCF Projection

Status: planned
Planned at: 2026-09-16
Development item: DEV-032
Primary owner: Cozy
Predecessor: Phase 63.1 (after Phase 63 contract closure)
Upstream contract: CNCF Phase 78
Downstream consumer: Textus CBD Support Phase 11

## Purpose

Add first-class Capability Model IR support to Cozy and generate the admitted
CNCF Component Capability projection. Capability remains non-instantiated;
Cozy preserves model meaning and provenance while executable realizations stay
in Operation, Workflow, and StateMachine contracts.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| CAP-64-01 | Freeze the Capability IR and identity/reference model against CNCF Phase 78. | planned |
| CAP-64-02 | Admit CML syntax and parser/metamodel support for Application and Component Capability. | planned |
| CAP-64-03 | Implement semantic validation and source-correlated diagnostics. | planned |
| CAP-64-04 | Generate the versioned CNCF Component Capability projection with deterministic provenance. | planned |
| CAP-64-05 | Prove CNCF admission and cbd-support consumption with a real fixture. | planned |

## Required behavior

- Application Capability is derivable from Use Case groups and Component
  Capability from scenarios without embedding executable procedure.
- Stable qualified identity and source location survive normalization and
  generation.
- Provided/required direction and realization references are explicit.
- Duplicate, missing, ambiguous, cyclic, and incompatible references fail with
  structured, source-correlated diagnostics.
- Realization declarations contain mappings, not ordering, retry,
  compensation, authorization, or runtime state.
- Generated output matches CNCF's admitted ABI/version and participates in
  multi-CML provenance without enumeration-order selection.

## Completion conditions

- Design/specification freeze Capability IR, syntax, normalization, validation,
  generation, and compatibility boundaries.
- Executable specifications cover accepted Application/Component Capability,
  provided/required references, realization mapping, and each required
  rejection boundary.
- A real CML fixture produces deterministic CNCF-admissible metadata with exact
  source provenance.
- CNCF Phase 78 admits the fixture and Textus CBD Support Phase 11 consumes it
  without CML reparsing or name-derived identity.
- Focused validation and review evidence are recorded without claiming
  downstream Phase closure.

## Non-goals

- Capability runtime instances or persistence.
- Executing Capability directly.
- Defining CNCF runtime semantics or cbd-support presentation.
- Treating provisional working notation as pre-approved final CML syntax.
- Collapsing Capability, Availability, Authorization, Permission, and Guard.

## References

- [Development note](../notes/capability-model-ir-support-proposal.md)
- [Development item journal](../journal/2026/09/2026-09-16-capability-model-ir-development-item.md)
- [Phase 64 Checklist](phase-64-checklist.md)
