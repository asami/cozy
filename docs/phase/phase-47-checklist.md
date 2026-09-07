# Phase 47 Checklist: CML Composite StateMachine and Workflow Modeling

This checklist is the authoritative Phase 47 progress ledger. It is
non-normative and records progress only; the phase contract remains
`docs/phase/phase-47.md`.

Phase Status: IN_PROGRESS

Scope: CSM-01 and CSM-02 are the completed slices recorded here. Phase 47.1, Phase
47.2, successor work, and `asami/goldenport-cncf` are outside this ledger
entry. The factual evidence for CSM-01 is the
[current-model inventory](../notes/cml-composite-statemachine-workflow-inventory.md);
the accepted semantic authority for CSM-02 is [CML Composite StateMachine
Semantics](../design/cml-composite-statemachine.md).

## CSM-01: Current-model fact inventory and canonical ledger

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47 current CML model inventory
- Update rule: update this block from the factual inventory and the Phase 47
  work ledger.

- [x] Record the implemented `STATEMACHINE` / `STATE-MACHINE` grammar and
      normalized model entry points.
- [x] Record top-level and entity-local StateMachine selection behavior.
- [x] Record nested state hierarchy, composite naming, and named history
      projection behavior.
- [x] Record transition, event, guard, effect, and one-string `ACTION`
      projection behavior, including whitespace and duplicate-line behavior.
- [x] Record Component StateMachine definition and transition-rule metadata
      plus the diagram projection path.
- [x] Record only source-identity and source-location observations supported by
      the cited Cozy source and design evidence.
- [x] Record current parser, projection, generation, and history-runtime test
      coverage.
- [x] Complete a bounded search for CML `WORKFLOW` parser/modeler syntax and
      distinguish unrelated UI, BoK, publication, and process terminology.

## CSM-02: Composite StateMachine semantics

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47 Composite StateMachine semantics
- Update rule: mark DONE only when the checklist item below is checked; the
  [accepted CML Composite StateMachine semantics](../design/cml-composite-statemachine.md)
  provides supporting evidence.

- [x] Define constituent-machine binding, role/identity, state configuration,
      derivation rules, derived transition semantics, and projection semantics.

## CSM-03: Static composite analysis

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 static composite analysis
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [ ] Define rule completeness/exclusivity, reachable configurations,
      impossible/dead states, redundant rules, and derived transition-graph
      analysis.

## CSM-04: Action algebra and composition model

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 action algebra and composition model
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [ ] Define one typed logical action model for constituent and composite
      actions, including a composable program representation for later CNCF
      interpretation.

## CSM-05: Workflow specialization analysis

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 Workflow specialization analysis
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [ ] Test candidate Workflow-only requirements against the Composite
      StateMachine model and retain only mandatory residual semantics.

## CSM-06: Grammar and validation

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 grammar and validation
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [ ] Add or refine CML syntax and semantic validation for the accepted
      Composite StateMachine and any justified Workflow specialization.

## CSM-07: SimpleModeler generation

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 SimpleModeler generation
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [ ] Generate stable definitions, constituent bindings, rule IR, derived-model
      metadata, action programs, source identities, and ABI metadata as defined
      by the accepted model.

## CSM-08: CNCF metadata/bootstrap contract

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 CNCF metadata/bootstrap contract
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [ ] Align generated output with the CNCF Phase 64 ComponentFactory and
      runtime-admission boundary.

## CSM-09: Visualization/projection metadata

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 visualization/projection metadata
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [ ] Preserve composite and constituent state, rule matches, derived
      transitions, action placement, and review metadata for the accepted model.

## CSM-10: Cross-repository acceptance

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 cross-repository acceptance
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [ ] Accept a real multi-constituent CML model through Cozy, generated
      metadata, and the explicitly admitted CNCF runtime boundary.
