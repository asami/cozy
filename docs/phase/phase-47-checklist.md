# Phase 47 Checklist: CML Composite StateMachine and Workflow Modeling

This checklist is the authoritative Phase 47 progress ledger. It is
non-normative and records progress only; the phase contract remains
`docs/phase/phase-47.md`.

Phase Status: IN_PROGRESS

Scope: CSM-01 through CSM-08 are the completed slices recorded here. Phase 47.1, Phase
47.2, successor work, and `asami/goldenport-cncf` are outside this ledger
entry. The factual evidence for CSM-01 is the
[current-model inventory](../notes/cml-composite-statemachine-workflow-inventory.md);
the accepted semantic authority for CSM-02 is [CML Composite StateMachine
Semantics](../design/cml-composite-statemachine.md); the accepted static-analysis
authority for CSM-03 is [CML Composite StateMachine Static Analysis](../design/cml-composite-statemachine-static-analysis.md).
The accepted action-algebra authority for CSM-04 is [CML Composite StateMachine
Action Algebra](../design/cml-composite-statemachine-action-algebra.md).
The accepted Workflow-specialization authority for CSM-05 is [CML Workflow
Specialization Classification](../design/cml-workflow-specialization.md).
The accepted CSM-06 grammar-and-validation authority is [CML Composite
StateMachine Grammar and Validation](../design/cml-composite-statemachine-grammar-validation.md).
The accepted CSM-08 bootstrap authority is [CML Composite StateMachine Bootstrap
Registry](../design/cml-composite-statemachine-bootstrap.md).

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

- Current status: DONE
- Owner: Cozy Phase 47 static composite analysis
- Update rule: mark DONE only when the concrete checklist condition below is
  checked; the checked item is the closure basis and the [accepted CML
  Composite StateMachine Static Analysis](../design/cml-composite-statemachine-static-analysis.md)
  provides supporting evidence.

- [x] Define rule completeness/exclusivity, reachable configurations,
      impossible/dead states, redundant rules, and derived transition-graph
      analysis.

## CSM-04: Action algebra and composition model

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47 action algebra and composition model
- Update rule: mark DONE only when the concrete checklist condition below is
  checked; the checked item is the closure basis and the [accepted CML
  Composite StateMachine Action
  Algebra](../design/cml-composite-statemachine-action-algebra.md) provides
  supporting evidence.

- [x] Define one typed logical action model for constituent and composite
      actions, including a composable program representation for later CNCF
      interpretation.

## CSM-05: Workflow specialization analysis

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47 Workflow specialization analysis
- Update rule: mark DONE only when the concrete checklist condition below is
  checked; the checked item is the closure basis and the [accepted CML Workflow
  Specialization Classification](../design/cml-workflow-specialization.md)
  provides supporting evidence.

- [x] Test candidate Workflow-only requirements against the Composite
      StateMachine model and retain only mandatory residual semantics.

## CSM-06: Grammar and validation

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47 grammar and validation
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [x] Add or refine CML syntax and semantic validation for the accepted
      Composite StateMachine and any justified Workflow specialization.

## CSM-07: SimpleModeler generation

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47 SimpleModeler generation
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [x] Generate CSM-07's immutable typed definition/constituent/configuration/
      logical-action/action-occurrence IR and deterministic versioned Scala ABI
      source from both `modeler-scala` and `modeler-scala-value`, preserving
      source identity where the parser provides it. Evidence:
      `CompositeStateMachineGenerationSpec` covers typed normalization,
      non-deduplicated equal-action provenance, derived-action transition
      provenance, value-path generation, ABI source contents, value-mode
      `DomainComponent` absence, and repeated-generation determinism; the
      accepted [generated ABI authority](../design/cml-composite-statemachine-generation.md)
      records exact scope and deferrals.

## CSM-08: CNCF metadata/bootstrap contract

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47 CNCF metadata/bootstrap contract
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [x] Emit the fixed typed `CompositeStateMachineBootstrap` source entrypoint
      from both public Scala routes, with the exact bootstrap ABI version and
      direct declaration-ordered definition references (or a typed
      `Vector.empty` when no definitions normalize). Evidence:
      `CompositeStateMachineGenerationSpec` proves byte-identical route output,
      exact bootstrap source members/direct reference, and stable typed-empty
      bootstrap generation; the accepted [bootstrap authority](../design/cml-composite-statemachine-bootstrap.md)
      preserves CSM-07 definition ABI verification and defers CNCF admission
      and interpretation.

## CSM-09: Visualization/projection metadata

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47 visualization/projection metadata
- Update rule: mark complete only when the concrete checklist condition below
  is checked.

- [x] Preserve composite and constituent state, rule matches, derived
      transitions, action placement, and review metadata for the accepted model.
      The [CSM-09 projection authority](../design/cml-composite-statemachine-projection.md)
      fixes the deterministic Cozy document boundary. Evidence:
      `P47-CSM09-VAL-002` passed 2 suites / 6 tests with SBT and wrapper exit
      0 and lock released; the CSM-09 independent review sealed `PASS`.

## CSM-10: Cross-repository acceptance

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 47 cross-repository acceptance
- Update rule: mark complete only when the concrete checklist condition below
  is checked.
- Dependency record (2026-09-07): at
  `goldenport-cncf@696ae0664a51737525cb35422dbb84d01eb76400`, CNCF Phase 63
  and Phase 64 are both planned. Phase 64 depends on Phase 63 closure and its
  CML-first acceptance checklist is entirely unchecked. No CSM-10 runtime
  acceptance may be inferred from producer-side generation alone.

- [ ] Accept a real multi-constituent CML model through Cozy, generated
      metadata, and the explicitly admitted CNCF runtime boundary.
