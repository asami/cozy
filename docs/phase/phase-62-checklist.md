# Phase 62 Checklist: First-Class CML WORKFLOW, Continuation, and Producer ABI

Status: planned
Phase: [Phase 62](phase-62.md)

## WFL-62-01: Workflow Source Contract and Lowering

Stage Status:

- Current status: OPEN
- Owner: Cozy CML / SimpleModeler owner
- Update rule: Mark DONE only after real `WORKFLOW` source fixes identity,
  versioning, Composite StateMachine normalization, and the closed automatic /
  semantic boundary vocabulary.

- [ ] Define `WORKFLOW` as a first-class CML declaration that normalizes to the
      existing StateMachine / Composite StateMachine semantic model.
- [ ] Define Workflow identity, version, constituent/reference boundaries, and
      declared-definition versus runtime-instance separation.
- [ ] Preserve existing StateMachine and Composite StateMachine source
      compatibility; prohibit CML from treating entity persistence as the
      WorkflowInstance store.
- [ ] Define automatic-transition guards/effects and typed semantic boundaries
      without Action-name or consumer-default inference.
- [ ] Reject ambiguous automatic progression, undeclared semantic boundaries,
      raw execution surfaces, and unsupported Workflow-only source forms.

## WFL-62-02: StateMachine Execution and Invocation Binding

Stage Status:

- Current status: OPEN
- Owner: Cozy StateMachine / generated ABI owner
- Update rule: Mark DONE only after the generated StateMachine API/SPI has one
  typed execution outcome contract and Action / Participant binding leaves
  State / Guard / Operation / Result semantics unchanged.

- [ ] Define `ActionExecution = Completed | Suspended | Failed`, or an
      equivalent closed typed contract, as a StateMachine-general API/SPI.
- [ ] Define `InvocationBinding = ORCHESTRATION | CONTINUATION` per semantic
      Action / Participant; do not make it a Workflow/profile-wide mode.
- [ ] Permit direct, test/mock, and external provider placement for one
      required typed operation without duplicating StateMachine semantics.
- [ ] Project required external typed Actions as SPI operations with stable
      identity and typed input/result contracts.
- [ ] Prove one Workflow mixes internal Orchestration and external
      Continuation bindings without semantic-transition divergence.

## WFL-62-03: Durable Continuation and Generated ABI

Stage Status:

- Current status: OPEN
- Owner: Cozy generator / generated-contract owner
- Update rule: Mark DONE only after a versioned generated ABI carries the
  exact resume and stale-result boundary without a parallel Workflow-only
  model.

- [ ] Define `WorkflowInvocationContract`, `Continuation`, `ContinuationResult`,
      `ContextBundle`, `ContextReference`, `ContextSnapshot`,
      `CompletionContract`, and `EvidenceContract`.
- [ ] Require Continuation `runId`, revision, minimum context, completion and
      evidence contracts, and a typed resume result.
- [ ] Generate a fail-closed stale-result contract using `ContextSnapshot`.
- [ ] Generate typed API/SPI, binding metadata, and optional presentation
      metadata while excluding dialog/open-screen commands and concrete
      transport semantics.
- [ ] Retain stable typed metadata sufficient for future direct/REST proxy
      projection without implementing either projection.
- [ ] Emit direct ComponentFactory bootstrap metadata without runtime policy or
      inferred name matching; keep generation deterministic.

## WFL-62-04: Producer Fixture and CNCF Handoff

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 62 coordinating with CNCF `sm-workflow`
- Update rule: Mark DONE only after a real source fixture, deterministic
  generated evidence, ABI version, and exact consumer handoff are frozen.
  CNCF runtime acceptance remains external to this Phase.

- [ ] Add a real CML Workflow fixture with `Build -> AI Review -> Approval ->
      Commit`, binding Build/Commit to ORCHESTRATION and Review/Approval to
      CONTINUATION.
- [ ] Verify `Completed`, `Suspended(Continuation)`, typed resume, stale-result
      rejection, and deterministic generated Scala/metadata.
- [ ] Record the ABI version, source fixture, schemas, and consumer binding for
      CNCF `sm-workflow`.
- [ ] Complete Cozy-focused validation, review, and release closure without
      claiming CNCF or Textus runtime completion.
