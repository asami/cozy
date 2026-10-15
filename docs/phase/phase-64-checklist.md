# Phase 64 Checklist: Capability Model IR and CNCF Projection

Status: CLOSED on 2026-10-15
Phase: [Phase 64](phase-64.md)
Development item: DEV-032

Current Phase closure: all six declared Steps have accepted local commits;
the independent whole-Phase review and current configured full Cozy validation
are accepted. The [Phase closure](phase-64.md#phase-closure--2026-10-15)
records the current ComponentUsage amendment, retained historical qualifications,
nonblocking follow-ups and separate main integration obligation. The final
local release binds `cozy64-phase-closure-20261015-01@1`.

The earlier Step acceptance notes retain their historical facts and pending
states. Their CAP-64-05 and CBD CAP-11-01 status statements are superseded only
for current Step acceptance by
[CAP-64-05 acceptance — 2026-10-15](phase-64.md#cap-64-05-acceptance--2026-10-15).

## CAP-64-PKG01: Component Usage model and Component-owned Directory/CAR

Stage Status:
- Current status: DONE
- Owner: Cozy generation/model owner
- Update rule: corrected scope replaces the Application-owned package proposal;
  preserve prior Step acceptance and require focused new-boundary evidence.
- Closure basis: Current Component-owned source/IR/wire, normal Directory/CAR
  generation, focused Step validation and independent protected Step review are accepted.

- [x] Generate Component-owned Usage metadata with the corrected model/source/wire contract.
- [x] Keep inductive Usage and deductive Component Capability separate; preserve
      explicit Component ownership, source/derivation evidence and many-to-many
      mappings. Usage observations do not create PROVIDED/REQUIRED contracts.
- [x] Delete old Application types/APIs/fields without aliases or forwarders;
      compile affected callers/generated code so stale typed references fail.
      Update CML/JSON/fixtures together; no dual reader, converter or fallback.
- [x] Prove one normal generated Directory route without requiring a CAR or loose
      JSON; prove multi-Component owner partitioning and external references.
- [x] Prove equivalent semantic results from that Directory and normal CAR,
      with config and separate capability namespaces coexisting.
- [x] Preserve optional absence and actual upstream failures; reuse lower-layer
      guarantees. Complete focused owner review/validation and record actual
      versions without claiming old separate-file proof accepts this change.


## CAP-64-01: IR and contract alignment

Stage Status:
- Current status: DONE
- Owner: Cozy model/generation owner
- Update rule: Mark DONE only after the IR and identity/reference boundary are
  accepted against CNCF Phase 78.
- Closure basis: All four items below are checked against the conceptual
  design/spec contract and independent protected Step review.

Contract documents: [Capability Model design](../design/cml-capability-model.md)
and [Capability Model specification](../spec/cml-capability-model.md).

- [x] Define Application Capability and Component Capability IR records.
- [x] Define stable qualified identity, scope, source, and version rules.
- [x] Define Use Case, Scenario, Component, Operation, Workflow, and
      StateMachine references.
- [x] Preserve the non-instantiated model boundary and separate Availability,
      Authorization, Permission, and Guard.

Acceptance evidence (2026-10-05):
- Specification R1-R12, every conceptual carrier field, and expected examples
  E1-E12 passed independent protected Step review at GPT-6.1 Sol high with no
  blockers, Hygiene, or Development Candidates.
- Parent static validation passed: four owned documents, 34 checks, 32 local
  links/anchors, and `git diff --check`.
- This Step defines conceptual IR records and semantic boundaries. Actual
  Scala IR/parser, executable diagnostics, generation, and real producer/
  admission/consumer proof remain CAP-64-02 through CAP-64-05. Expected examples
  are not executed tests or fixtures; CBD CAP-11-01 remains incomplete.

## CAP-64-02: CML syntax and normalization

Stage Status:
- Current status: DONE
- Owner: Cozy CML parser/metamodel owner
- Update rule: Mark DONE only after syntax is promoted into design/spec and
  parser/metamodel executable specifications pass.
- Closure basis: All four items below are checked against the promoted
  syntax/source contract, real-parser executable proof and independent
  protected Step review.

- [x] Resolve provisional keyword choices against existing CML syntax.
- [x] Parse and normalize Application and Component Capability declarations.
- [x] Represent provided/required direction and realization mapping explicitly.
- [x] Preserve cross-file and multi-CML source identity deterministically.

Acceptance evidence (2026-10-05):

- Exact initial syntax is promoted into the
  [specification](../spec/cml-capability-model.md#initial-authored-cml-grammar)
  and private module responsibilities into the
  [design](../design/cml-capability-model.md#original-cml-documents-and-normalization-responsibilities).
- Private R3 carriers and SourceInput normalization retain distinct
  Application/Component definitions, full directional declarations, three
  realization kinds, independent revisions and per-document envelopes.
- The 16-file Step and 39-path contract/consumer boundary passed independent
  protected Step review at GPT-6.1 Sol high, with no current Step blockers or
  Hygiene. One nonblocking upstream parser Development Candidate is tracked
  separately by the coordinating Phase 11.
- Real-parser acceptance passed 25/25 cases on subject revision 7 after
  surplus terminal blank lines were removed from the two CML fixtures. The
  parent accepted this exact spacing-only amendment under the original
  independent review; all nonterminal fixture content and expectations remain
  unchanged. The retained IR/Foundation/legacy accumulator passed 68/68 cases
  on revision 6, including two properties requiring 100 active and zero
  discarded samples. Its unchanged suites and consumed inputs remain relevant;
  the original result retains revision 6. Both focused executions compiled
  their selected main/Test inputs and completed with released serial locks.
  Parent mechanical and diff checks passed.
- The quote/backslash property retains decoded literal IDs through equivalent
  JSON Unicode escapes and actual Kaleidox parsing. It does not assert every
  equivalent escaped-quote spelling is accepted by the upstream parser.
- Parsed syntax/source facts are not semantic admission or generated CNCF
  output. CAP-64-03 through CAP-64-05 and CBD CAP-11-01 remain incomplete.

## CAP-64-03: Validation and diagnostics

Stage Status:
- Current status: DONE
- Owner: Cozy validation owner
- Update rule: Mark DONE only after every admitted invalid boundary has
  source-correlated executable specification coverage, the selected focused
  validation passes and independent protected Step acceptance is recorded.
- Closure basis: Exact private finite scope/reference/declaration/target/graph
  contracts, C64V01–32 executable behavior coverage, focused validation and
  independent acceptance; authored implementation alone does not close the Step.

- [x] Reject missing, duplicate, ambiguous, cyclic, and incompatible
      Capability references.
- [x] Reject procedure/order, retry, compensation, authorization, and runtime
      state embedded in Capability realization.
- [x] Reject unresolved or wrong-kind realization targets.
- [x] Produce structured diagnostics with exact source identity/location.

Acceptance evidence (2026-10-14):

- Eight private validation modules preserve original documents and explicit
  scope facts, reject semantic contradictions with source-correlated diagnostics,
  bind supplied target tuples and expose direct compatibility without execution.
- [CmlCapabilityValidationCmlSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationCmlSpec.scala)
  authors C64V01–05: real original CML normalization/validation, independent
  relationships and rejected syntax/acquisition outcomes.
- [CmlCapabilityValidationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationSpec.scala)
  authors C64V06–24: roles/vocabularies, definition/declaration candidates, exact
  targets, malformed source facts, Presence/origin and deterministic diagnostics.
- [CmlCapabilityValidationGraphSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationGraphSpec.scala)
  authors C64V25–32: finite Local cycles, direct compatibility and two active
  properties requiring at least 100 successes with zero discards.
- The [exact 32-case map](../spec/cml-capability-model.md#authored-executable-semantic-validation-cases)
  records each observable obligation. Required focused validation, the retained
  regression accumulator and independent protected Step review are accepted.
  All four checklist items are checked and Current status is DONE.
- Supplied synthetic scope facts are not actual acquisition/extraction,
  generated producer/CNCF admission/CBD evidence or Phase closure. CAP-64-04
  is DONE and CAP-64-05 remains OPEN. DEV-P11-COZY64-CAP02-001 retains
  coordinating Phase 11 ownership.

## CAP-64-04: CNCF projection generation

Stage Status:
- Current status: DONE
- Owner: Cozy generator/provenance owner
- Update rule: Mark DONE only after generated output matches the admitted CNCF
  ABI, selected focused validation passes, independent protected Step review
  accepts deterministic multi-CML generation and local Step acceptance is recorded.
- Closure basis: All four requirements below are checked against C64G01–33
  behavior coverage, accepted focused validation, independent protected Step
  review and independent focused closure.

- [x] Generate provided and required Capability projections.
- [x] Generate explicit realization references and traceability links.
- [x] Bind generated ABI version and exact source provenance.
- [x] Prove deterministic output and conflict handling with the completed
      Phase 63.1 multi-CML provenance bridge behavior.

Acceptance (2026-10-14):

- Eight private modules implement actual normalization/validation composition,
  exact per-document/Component R11 snapshots and complete explicit output selection.
- [CmlCapabilityGenerationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityGenerationSpec.scala)
  authors C64G01–20, the C64G33 compatibility-tuple regression and two properties
  requiring at least 100 successes and zero discards; original
  Validated/Application data remain retained upstream.
- [CmlCapabilityGenerationProvenanceSpec](../../src/test/scala/cozy/modeler/CmlCapabilityGenerationProvenanceSpec.scala)
  authors C64G21–32 with actual carrier compilation/pure accessor behavior,
  bounded publication and existing V1/V2 product provenance APIs.
- The [case map](../spec/cml-capability-model.md#authored-executable-generation-cases)
  states every observable obligation. Selected focused validation, independent
  protected Step review and independent focused closure are accepted. All four
  checklist criteria are DONE.
- CAP-64-05, CNCF/CBD proof, separate Cozy Phase full validation/review/local
  release and subsequent main integration remain unfinished.

## CAP-64-05: Cross-repository acceptance

Stage Status:
- Current status: DONE
- Owner: Cozy Phase 64 with CNCF Phase 78 and CBD Support Phase 11
- Update rule: Mark DONE only after one real fixture completes the producer,
  admission, and consumer path with reproducible evidence.
- Closure basis: All four original requirements below are accepted against actual
  producer, CNCF admission and CBD consumer evidence with independent revisions,
  focused validation, independent whole-Step review and retained correction closure.

- [x] Produce a real Capability CML fixture.
- [x] Admit the generated projection through CNCF Phase 78.
- [x] Consume it through Textus CBD Support Phase 11 without CML reparsing.
- [x] Record exact source, Cozy, CNCF ABI, and cbd-support revisions and review
      evidence.

Acceptance (2026-10-15): all four original criteria are accepted as recorded in
[Phase 64](phase-64.md#cap-64-05-acceptance--2026-10-15). All five Cozy Steps are
accepted; separate Phase full validation, full review, local release and main
integration remain outstanding. No Step commit is claimed.

Historical S01/S02/S03 authoring and compatibility notes as of 2026-10-14 follow
unchanged. Their pending states are superseded by current Step acceptance only;
participant Phase closure and main integration obligations remain outstanding.

S01 normal producer/publication is authored against
[R13](../spec/cml-capability-model.md#r13-normal-production-publication-and-application-handoff)
with C64I01–16, C64B01–12 and C64A01–08. Parent focused validation and independent
current-Step conformance review remain pending. All four original requirements
above stay unchecked until actual CNCF native admission/discovery and CBD
no-reparse consumption with exact independent revisions complete. S01 alone
authorizes no Step commit or whole-Step closure. Participant Phase full
validation/review/local release and subsequent Cozy main integration remain owed.


Compatibility authority — 2026-10-14: S02 consumes the closed canonical Cozy
Phase 71.5 contract (`docs/phase/phase-71.5.md`, closed 2026-10-10) as the selected
input for SourceInput, exact known V1/V2 inputs and path inventory, direct
Directory/CAR metadata reads and three-field ComponentKnowledge declarations.
Readable content changes remain admissible; authoring collisions and missing
selected paths retain their actual failure contracts. This compatibility input
supplies no Phase 64 acceptance. All four CAP-64-05 criteria remain OPEN and
unchecked; real producer/native/CBD proof, participant Phase closure and later
Cozy main integration remain owed.

S03 authoring — 2026-10-14: The normal selected Directory descriptor uses the
existing CAR schema-3 serializer and exact selected Component release. Whole
batch preflight covers its leaf/collisions and alternate canonical slots.
[C64D01–08](../spec/cml-capability-model.md#r13-normal-production-publication-and-application-handoff)
are authored with real CLI/prepare/publish inputs; parent validation and
independent acceptance remain pending. The first four accepted Steps and all
four CAP-64-05 boxes retain their status. Actual native CNCF/CBD proof and each
independent participant full validation/review/local release remain owed before
completed Phase 64 is integrated into Cozy main.
