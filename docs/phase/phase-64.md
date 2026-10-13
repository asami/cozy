# Phase 64 - Capability Model IR and CNCF Projection

Status: in-progress
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
| CAP-64-01 | Freeze the Application/Component Capability IR, identity/reference model, and semantic-reference boundary against Phase 54 and CNCF Phase 78. | closed |
| CAP-64-02 | Admit CML syntax and parser/metamodel support for Application and Component Capability without Use Case Group containment semantics. | closed |
| CAP-64-03 | Implement semantic validation and source-correlated diagnostics, including Goal/Use Case, Capability, realization, and admitted Glossary/BoK references. | closed |
| CAP-64-04 | Generate the versioned CNCF Component Capability projection with deterministic provenance while retaining Application Capability as the application-management IR. | planned |
| CAP-64-05 | Prove CNCF admission and cbd-support consumption with a real fixture covering Use Case-to-Capability discovery evidence and terminology grounding. | planned |

## CAP-64-01 acceptance

The conceptual IR contract was accepted by independent protected Step review
at GPT-6.1 Sol high on 2026-10-05, with no blockers, Hygiene, or Development
Candidates. Specification rules R1-R12, all conceptual carrier fields, and
expected examples E1-E12 cover the four CAP-64-01 checklist requirements.
Parent static validation passed 34 checks and 32 local links/anchors, including
`git diff --check`.

This acceptance freezes semantic records and references. Actual Scala IR,
syntax/parser/metamodel, diagnostics, generation, and real producer/admission/
consumer proof remain CAP-64-02 through CAP-64-05. It supplies no executable
or Textus CBD Support CAP-11-01 acceptance. Phase 64 remains in progress.

## CAP-64-02 acceptance

The initial authored grammar now uses a separate top-level CAPABILITY-MODEL
and direct Application, Component and declaration containers. Private immutable
Scala IR and the callable SourceInput normalization entry retain every R3
carrier, explicit provided/required direction, all three realization kinds and
per-document source authority/path/revisions. Two real CML resources and
C64P01–25 executable cases, including active ScalaCheck properties, are authored.
The [specification](../spec/cml-capability-model.md#initial-authored-cml-grammar)
owns exact grammar and source rules; the
[design](../design/cml-capability-model.md#original-cml-documents-and-normalization-responsibilities)
maps the eight implementation modules.

The 16-file Step and 39-path supporting contract/consumer boundary passed
independent protected Step review at GPT-6.1 Sol high on 2026-10-05, with no
current Step blockers or Hygiene. All four CAP-64-02 checklist criteria are
complete. Real-parser acceptance passed 25/25 cases on subject revision 7
after surplus terminal blank lines were removed from the two CML fixtures.
The parent accepted this exact spacing-only amendment under the original
independent review; all nonterminal fixture content and expectations remain
unchanged. The retained IR/Foundation/legacy accumulator passed 68/68 cases
on revision 6, including two properties requiring 100 active and zero
discarded samples. Its unchanged suites and consumed inputs remain relevant;
the original result retains revision 6. Both focused executions compiled
their selected main/Test inputs and released the serial lock. Parent
mechanical and diff checks passed.

One nonblocking upstream parser Development Candidate is tracked by the
coordinating Phase 11. Equivalent JSON Unicode escapes preserve decoded
quote/backslash IDs through actual Kaleidox parsing; this evidence does not
claim support for every equivalent escaped-quote spelling or an upstream
parser repair. Phase 64 remains in progress and full validation remains a
separate release obligation.

Parsed results prove no semantic graph admission: duplicate IDs or
declaration keys, Local target resolution, dependency cycles, repeated-key
agreement, version compatibility and target ABI checks remain CAP-64-03.
CAP-64-04 owns generation against CNCF's accepted v1 contract and Phase 63.1
multi-CML provenance. CAP-64-05 owns real producer/admission/consumer evidence.
These authored parser inputs are not generated CNCF resources. CNCF target
adapters/discovery, CBD CAP-11-01 and Phase closure are not established here.

## CAP-64-03 acceptance

The private finite validation entry and eight responsibility modules are authored
for original normalized documents plus explicitly supplied model, vocabulary,
historical Capability and executable target contract facts. The
[specification](../spec/cml-capability-model.md#private-finite-semantic-validation)
owns the exact scope, all-or-nothing diagnostic, Local/External resolution,
declaration/realization tuple, finite dependency-cycle and directed compatibility
contracts. The
[design](../design/cml-capability-model.md#finite-semantic-validation-responsibilities)
maps the modules and normalization-to-validation-to-future-generation pipeline.

C64V01–05 are authored in
[CmlCapabilityValidationCmlSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationCmlSpec.scala)
using the two unchanged real parser resources and separately supplied scope facts.
C64V06–24 are authored in
[CmlCapabilityValidationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationSpec.scala);
C64V25–32 are authored in
[CmlCapabilityValidationGraphSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationGraphSpec.scala),
including two active ScalaCheck properties requiring at least 100 successes and
zero discards. Given/When/Then setup, action and expectation clauses accompany
every behavior.

CAP-64-03 acceptance (2026-10-14): the required focused validation and independent
protected Step review are accepted. All four CAP-64-03 checklist criteria are DONE.

These finite synthetic facts establish no actual target extraction, generated
CNCF resource or real producer/admission/CBD proof. CAP-64-04 and CAP-64-05 remain
OPEN, CBD CAP-11-01 remains incomplete and Phase 64 remains in progress. The
coordinating Phase 11 retains primary tracking of DEV-P11-COZY64-CAP02-001 for
separate upstream escaped-quote parser work; this Step changes no parser or
journal ownership.

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

- [Capability Model design](../design/cml-capability-model.md)
- [Capability Model specification](../spec/cml-capability-model.md)
- [Development note](../notes/capability-model-ir-support-proposal.md)
- [Development item journal](../journal/2026/09/2026-09-16-capability-model-ir-development-item.md)
- [Phase 54](phase-54.md)
- [Phase 54.5](phase-54.5.md)
- [Phase 64 Checklist](phase-64-checklist.md)
