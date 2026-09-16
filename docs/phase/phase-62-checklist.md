# Phase 62 Checklist: First-Class CML WORKFLOW Language and Producer ABI

Status: planned
Phase: [Phase 62](phase-62.md)

## WFL-62-01: Workflow Contract and Lowering

Stage Status:

- Current status: OPEN
- Owner: Cozy CML / SimpleModeler owner
- Update rule: Mark DONE only after the accepted contract fixes the source
  `WORKFLOW` declaration, its Composite StateMachine relationship, source
  identity/versioning, and the closed progression-boundary vocabulary.

- [ ] Define `WORKFLOW` as a first-class CML declaration that normalizes to
      the existing StateMachine / Composite StateMachine semantic model.
- [ ] Define Workflow identity, version, constituent/reference boundaries, and
      the required reuse of existing state, transition, guard, and Action
      semantics.
- [ ] Separate declared Workflow identity from entity-local StateMachine and
      runtime WorkflowInstance identity; prohibit CML from treating entity
      persistence as the WorkflowInstance store.
- [ ] Freeze the explicit automatic versus semantic-boundary contract and its
      typed Work Order, Decision, and Wait representations.
- [ ] Define valid automatic-transition guards/effects and rejection semantics
      without relying on Action names or consumer defaults.

## WFL-62-02: Parsing, Normalization, and Validation

Stage Status:

- Current status: OPEN
- Owner: Cozy CML parser/modeler owner
- Update rule: Mark DONE only after real `WORKFLOW` source parses into the
  accepted normalized model and every required invalid form has diagnostics.

- [ ] Add the `WORKFLOW` grammar and normalized source-model entry point.
- [ ] Validate stable references, typed Operations, state/transition bindings,
      declared boundary forms, and source locations.
- [ ] Reject ambiguous automatic progression, undeclared semantic boundaries,
      raw execution surfaces, and unsupported Workflow-only syntax.
- [ ] Preserve existing StateMachine and Composite StateMachine source
      compatibility.

## WFL-62-03: Generated ABI and Bootstrap

Stage Status:

- Current status: OPEN
- Owner: Cozy generator / generated-contract owner
- Update rule: Mark DONE only after generated Scala/metadata exposes the
  accepted versioned WorkflowDefinition contract and bootstrap surface.

- [ ] Generate a versioned typed WorkflowDefinition ABI with Workflow,
      Composite StateMachine, transition/action provenance, source, typed
      Operation, and progression-boundary metadata.
- [ ] Preserve the declared-definition versus runtime-instance boundary without
      generating entity-owned WorkflowInstance persistence fields.
- [ ] Emit direct ComponentFactory bootstrap metadata without runtime policy or
      inferred name matching.
- [ ] Reuse `cozy.cml.logical-action-program.v1`; do not add a Workflow Action
      algebra.
- [ ] Make generation deterministic and preserve declaration order where it is
      semantically observable.

## WFL-62-04: Producer Fixture and CNCF Handoff

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 62 coordinating with CNCF Phase 77
- Update rule: Mark DONE only after a real source fixture, deterministic
  generated evidence, ABI version, and exact handoff are frozen. CNCF runtime
  acceptance remains external to this Phase.

- [ ] Add a real CML Workflow fixture containing automatic progression and at
      least one typed semantic boundary.
- [ ] Verify deterministic generated Scala/metadata and static invalid-case
      diagnostics.
- [ ] Record the exact generated ABI/version, source fixture, and consumer
      binding for CNCF Phase 77.
- [ ] Complete Cozy-focused validation, review, and release closure without
      claiming CNCF or Textus runtime completion.
