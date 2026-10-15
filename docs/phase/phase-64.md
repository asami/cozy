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

## Package metadata amendment — 2026-10-15

Current corrected work: **CAP-64-PKG01**. This replaces the earlier
Application-owned package assumption; it does not create another parallel item.

The [Component Usage contract](../spec/component-usage-capability.md) replaces
ApplicationCapability with the separate inductive ComponentUsageCapability model.
Add explicit Component ownership and partition generated records by that owner.
Remove Application source/IR/wire names, `applicationOutput` and the withdrawn
`applicationPackage` proposal. Extend the existing per-Component package selection
with Usage references; generate fixed Usage paths beside Component Capability.
Update source grammar, private request, generated APIs, codecs and fixtures.

Execution order: settle the corrected typed model and owner/source mapping ->
Cozy source/IR/request and CNCF Directory acquisition -> CBD ingestion/catalog/query
through one real Directory path -> normal CAR and parity -> Phase 11.1 Web/MCP.
Migrate affected producer/consumer call sites together. Delete old symbols and
use compilation to expose missed callers; do not retain aliases, deprecated
forwarders, dual readers or automatic old-format conversion. This is pre-release
work, so update CML/JSON fixtures and regenerate outputs instead of maintaining
compatibility. Existing Component provided/required compatibility semantics are
unchanged; they are not compatibility with the removed Application model.

Earlier accepted Steps keep their historical evidence. The current amendment
is accepted at the Step boundary: implementation, Step validation and independent
protected Step review are complete. The local Step commit is being prepared.
It is required before the owning Phase closes. The coordinated plan covers
the revised semantic/ownership/wire scope; the earlier transport-only estimate
does not establish its acceptance. Each owner retains its existing
review/full-validation duties; CBD aggregate full validation remains Phase 11.1.
Phase full review, full validation, local release and main integration remain pending.

## Purpose

Add first-class Capability Model IR support to Cozy and generate the admitted
CNCF Component Capability projection. Capability remains non-instantiated;
Cozy preserves model meaning and provenance while executable realizations stay
in Operation, Workflow, and StateMachine contracts.

The reconciled SimpleModeling direction treats Use Case and Capability as
complementary models with different responsibilities:

```text
Use Case              = discovery / understanding model
Component Usage Capability = inductive Component usage model
Component Capability   = architectural capability model
```

Component Usage Capability is discovered inductively from concrete Use Cases and
other requirement evidence, then provides an axis for Component usage
understanding and evidence-backed mapping to explicit Component contracts. It is
not a renamed Use Case Group and is not contained by a Use Case hierarchy.

## Capability / Glossary semantic axis

Usage Capability describes Component use; Component Capability describes its
provided/required contracts. Glossary / BoK
references ground what the terms used by that Capability mean.

```text
Use Cases / Goals
      |
      | discovery / induction
      v
Component Usage Capability ---- uses concepts ----> Glossary / BoK
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
| CAP-64-PKG01 | Generate Component-owned Usage metadata with the corrected model/source/wire contract. | closed |
| CAP-64-01 | Freeze the Application/Component Capability IR, identity/reference model, and semantic-reference boundary against Phase 54 and CNCF Phase 78. | closed |
| CAP-64-02 | Admit CML syntax and parser/metamodel support for Application and Component Capability without Use Case Group containment semantics. | closed |
| CAP-64-03 | Implement semantic validation and source-correlated diagnostics, including Goal/Use Case, Capability, realization, and admitted Glossary/BoK references. | closed |
| CAP-64-04 | Generate the versioned CNCF Component Capability projection with deterministic provenance while retaining Component Usage Capability as the Component usage IR. | closed |
| CAP-64-05 | Prove CNCF admission and cbd-support consumption with a real fixture covering Use Case-to-Capability discovery evidence and terminology grounding. | closed |

The earlier Step acceptance notes below retain their historical pending states.
Their CAP-64-05 and CBD CAP-11-01 status statements are superseded only for
current acceptance by [CAP-64-05 acceptance — 2026-10-15](#cap-64-05-acceptance--2026-10-15).

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
maps the modules and normalization-to-validation-to-generation pipeline.

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
CNCF resource or real producer/admission/CBD proof. CAP-64-04 is accepted;
CAP-64-05 remains OPEN,
CBD CAP-11-01 remains incomplete and Phase 64 remains in progress. The
coordinating Phase 11 retains primary tracking of DEV-P11-COZY64-CAP02-001 for
separate upstream escaped-quote parser work; this Step changes no parser or
journal ownership.

## CAP-64-04 acceptance

Stage Status:
- Current status: DONE
- Owner: Cozy generator/provenance owner
- Update rule: DONE records accepted selected focused validation, independent
  protected Step review and independent focused closure.
- Closure basis: All four CAP-64-04 items in the
  [checklist](phase-64-checklist.md#cap-64-04-cncf-projection-generation)
  are DONE against C64G01–33 coverage and accepted validation/review/closure.

Eight private modules author the actual normalization/validation/generation
composition, exact Component-only R11 snapshots, explicit source/Component/output
selection, pure segmented Scala carriers and bounded publication. The
[specification](../spec/cml-capability-model.md#private-deterministic-projection-generation)
owns the frozen contract and
[design](../design/cml-capability-model.md#deterministic-generation-and-existing-provenance-responsibilities)
maps its responsibilities.

[CmlCapabilityGenerationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityGenerationSpec.scala)
authors C64G01–20 and the C64G33 compatibility-tuple regression, including two
active properties requiring at least 100 successes and zero discards.
[CmlCapabilityGenerationProvenanceSpec](../../src/test/scala/cozy/modeler/CmlCapabilityGenerationProvenanceSpec.scala)
authors C64G21–32: actual generated-carrier compilation/pure accessor behavior,
selected output boundaries and existing V1/V2 aggregation/rebinding/conflict
contracts. All cases have adjacent Given/When/Then clauses.

CAP-64-04 acceptance (2026-10-14): selected focused validation, independent
protected Step review and independent focused closure are accepted. All four
CAP-64-04 checklist criteria are DONE.

CAP-64-05 still owns normal producer composition, actual source/target
acquisition, Scala 3 CNCF decoding/admission/binding/package/discovery and CBD
no-reparse consumer proof. CNCF CAP-78-03/04/05, CBD CAP-11 and separate Cozy
Phase review/full validation/local release remain unfinished. Main integration
follows that participant closure with current main conflict and combined
validation handling. These authored core specifications supply no CLI hookup,
target activation, canonical CAR resource or downstream acceptance claim.

## CAP-64-05 acceptance — 2026-10-15

All four original CAP-64-05 criteria are accepted after focused validation,
independent whole-Step review and retained correction closure. Four normal real
selected CML products establish generated Scala 3 pure accessors, public CNCF
canonical Directory admission and ordinary CAR discovery, complete independent
Application, provided/required and native Operation/Workflow/StateMachine
objects, and source-free CBD intake without CML reparsing. Current/history
distinctions and typed failures remain retained.

Exact independent source, Cozy, CNCF ABI and CBD revision records and review
evidence are retained locally, without attachment here. This Step is accepted;
no Step commit is claimed. All five Cozy Steps are now accepted. Separate
Phase 64 full validation, full review, local release and main integration remain
owed. CNCF Phase 78 CAP-78-05 and its own Phase closure, and primary CBD
CAP-11-02/03, remain separate obligations. ForwardUseCase positive production
remains not yet admitted and unexercised; Application links do not establish
inverse UseCase demand.

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

## CAP-64-05 normal production prerequisite

Historical authoring and compatibility notes as of 2026-10-14 follow unchanged.
Their pending CAP-64-05 and CBD CAP-11-01 states are superseded only for current
Step acceptance by [CAP-64-05 acceptance — 2026-10-15](#cap-64-05-acceptance--2026-10-15);
participant Phase closure and main integration obligations remain outstanding.

Stage Status:
- Current status: IN_PROGRESS
- Owner: Cozy Phase 64 with CNCF Phase 78 and CBD Support Phase 11
- Update rule: S01 authors the normal producer/publication prerequisite; all four
  original criteria remain unchecked until actual producer/admission/consumer
  proof and independent whole-Step acceptance.

The [R13 normal production contract](../spec/cml-capability-model.md#r13-normal-production-publication-and-application-handoff)
and [module responsibilities](../design/cml-capability-model.md#normal-source-production-and-complete-publication-responsibilities)
specify strict opt-in CLI acquisition, original core composition, canonical
Component index/resources, full lossless Application JSON and explicit normal
package-car selection. C64I01–16, C64B01–12 and C64A01–08 are authored in four
semantic suites; selected parent validation and independent review are pending.

This prerequisite supplies no whole CAP-64-05 acceptance. Actual CNCF Scala 3
decoding, native target binding, package/Factory discovery and CBD ingestion
without CML reparsing remain separately owned proof. R4 forbids inverse UseCase
demand inference; its missing upstream projection remains explicitly unavailable.
All prior Step acceptance, failures and repair history remain retained. Separate
Cozy/CNCF/CBD Phase full validation, independent Phase review and local releases
remain owed. Cozy main integration follows completed Phase 64 on its dedicated
branch and fresh combined validation; no push or remote publication is implied.


Compatibility authority — 2026-10-14: S02 consumes the closed canonical Cozy
Phase 71.5 contract (`docs/phase/phase-71.5.md`, closed 2026-10-10) as the selected
input for SourceInput, exact known V1/V2 inputs and path inventory, direct
Directory/CAR metadata reads and three-field ComponentKnowledge declarations.
Readable content changes remain admissible; authoring collisions and missing
selected paths retain their actual failure contracts. This compatibility input
supplies no Phase 64 acceptance. All four CAP-64-05 criteria remain OPEN and
unchecked; real producer/native/CBD proof, participant Phase closure and later
Cozy main integration remain owed.

S03 authoring — 2026-10-14: Normal Directory publication adds one canonical
schema-3 root descriptor per explicitly selected bundle through the existing CAR
serializer and exact Component release codec. Complete batch preflight rejects
unsafe/different selected leaves and every alternative canonical descriptor slot.
[C64D01–08](../spec/cml-capability-model.md#r13-normal-production-publication-and-application-handoff)
are authored real CLI/prepare/publish specifications; parent validation and
independent acceptance remain pending. The first four accepted Steps and all
four unchecked CAP-64-05 criteria remain unchanged. Actual CNCF/CBD proof,
independent participant full validation/review/local releases and completed
Phase 64 before eventual Cozy main integration remain owed.
