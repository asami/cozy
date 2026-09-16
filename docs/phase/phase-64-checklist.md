# Phase 64 Checklist: Capability Model IR and CNCF Projection

Status: planned
Phase: [Phase 64](phase-64.md)
Development item: DEV-032

## CAP-64-01: IR and contract alignment

Stage Status:
- Current status: OPEN
- Owner: Cozy model/generation owner
- Update rule: Mark DONE only after the IR and identity/reference boundary are
  accepted against CNCF Phase 78.

- [ ] Define Application Capability and Component Capability IR records.
- [ ] Define stable qualified identity, scope, source, and version rules.
- [ ] Define Use Case, Scenario, Component, Operation, Workflow, and
      StateMachine references.
- [ ] Preserve the non-instantiated model boundary and separate Availability,
      Authorization, Permission, and Guard.

## CAP-64-02: CML syntax and normalization

Stage Status:
- Current status: OPEN
- Owner: Cozy CML parser/metamodel owner
- Update rule: Mark DONE only after syntax is promoted into design/spec and
  parser/metamodel executable specifications pass.

- [ ] Resolve provisional keyword choices against existing CML syntax.
- [ ] Parse and normalize Application and Component Capability declarations.
- [ ] Represent provided/required direction and realization mapping explicitly.
- [ ] Preserve cross-file and multi-CML source identity deterministically.

## CAP-64-03: Validation and diagnostics

Stage Status:
- Current status: OPEN
- Owner: Cozy validation owner
- Update rule: Mark DONE only after every admitted invalid boundary has
  source-correlated executable specification coverage.

- [ ] Reject missing, duplicate, ambiguous, cyclic, and incompatible
      Capability references.
- [ ] Reject procedure/order, retry, compensation, authorization, and runtime
      state embedded in Capability realization.
- [ ] Reject unresolved or wrong-kind realization targets.
- [ ] Produce structured diagnostics with exact source identity/location.

## CAP-64-04: CNCF projection generation

Stage Status:
- Current status: OPEN
- Owner: Cozy generator/provenance owner
- Update rule: Mark DONE only after generated output matches the admitted CNCF
  ABI and is deterministic under multi-CML generation.

- [ ] Generate provided and required Capability projections.
- [ ] Generate explicit realization references and traceability links.
- [ ] Bind generated ABI version and exact source provenance.
- [ ] Prove deterministic output and conflict handling with the completed
      Phase 63.1 multi-CML provenance bridge behavior.

## CAP-64-05: Cross-repository acceptance

Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 64 with CNCF Phase 78 and CBD Support Phase 11
- Update rule: Mark DONE only after one real fixture completes the producer,
  admission, and consumer path with reproducible evidence.

- [ ] Produce a real Capability CML fixture.
- [ ] Admit the generated projection through CNCF Phase 78.
- [ ] Consume it through Textus CBD Support Phase 11 without CML reparsing.
- [ ] Record exact source, Cozy, CNCF ABI, and cbd-support revisions and review
      evidence.
