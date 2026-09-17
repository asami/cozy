# Phase 62 Checklist: First-Class CML WORKFLOW on StateMachine API/SPI

Status: planned
Phase: [Phase 62](phase-62.md)

This checklist follows the consolidated Phase 62. Historical protocol/binding addenda remain design history; this checklist does not require a Workflow-wide protocol mode or `InvocationBinding` semantic switch.

## WFL-62-01: Workflow Source Contract and Lowering

Stage Status:
- Current status: OPEN
- Owner: Cozy CML / SimpleModeler owner
- Update rule: Mark DONE only after real `WORKFLOW` source fixes identity, versioning, StateMachine/Composite StateMachine normalization, and explicit progression semantics.

- [ ] Define `WORKFLOW` as a first-class CML declaration that normalizes to the existing StateMachine / Composite StateMachine semantic model.
- [ ] Define Workflow identity, version, constituent/reference boundaries, and declared-definition versus runtime-instance separation.
- [ ] Preserve existing StateMachine and Composite StateMachine source compatibility.
- [ ] Prohibit CML from treating entity persistence as the WorkflowInstance store.
- [ ] Define automatic-transition guards/effects and external semantic SPI boundaries without Action-name or consumer-default inference.
- [ ] Reject ambiguous progression, undeclared required operations, raw execution surfaces, and unsupported Workflow-only control semantics.

## WFL-62-02: StateMachine API/SPI Contract

Stage Status:
- Current status: OPEN
- Owner: Cozy StateMachine / generated ABI owner
- Update rule: Mark DONE only after StateMachine has one reusable Provided API / Required SPI contract consumed by Workflow without a parallel Workflow-specific interface model.

- [ ] Define/project StateMachine Provided API operations with stable identity and typed input/result.
- [ ] Define/project StateMachine Required SPI operations with stable identity and typed input/result.
- [ ] Preserve generic Context, Completion, Evidence, capability/constraint metadata required by an SPI operation.
- [ ] Permit local/direct, deterministic test/mock, and external provider placement without duplicating State/Guard/Operation/Result semantics.
- [ ] Ensure Workflow API/SPI is a projection/reuse of StateMachine API/SPI rather than an independent model.
- [ ] Preserve enough identity/type metadata for future assemble `SPI -> API` binding and caller-side API projection.

## WFL-62-03: ActionExecution and Durable Continuation ABI

Stage Status:
- Current status: OPEN
- Owner: Cozy StateMachine / generator owner
- Update rule: Mark DONE only after generated ABI represents internal completion, external suspension, failure, and typed resume without protocol-mode semantics.

- [ ] Define `ActionExecution = Completed | Suspended | Failed`, or an equivalent closed typed contract, as StateMachine-general ABI.
- [ ] Define `Completed(Result)` and `Failed(Error)` typed payload semantics.
- [ ] Define `Suspended(Continuation)` as the durable result of an Action/provider that requires an external result.
- [ ] Define `Continuation`, `ContinuationResult`, `ContextBundle`, `ContextReference`, `ContextSnapshot`, `CompletionContract`, and `EvidenceContract`.
- [ ] Require Continuation instance/run identity, expected revision, minimum context/reference set, completion/evidence contracts, and typed resume result.
- [ ] Generate fail-closed stale-result semantics using `ContextSnapshot`.
- [ ] Exclude Workflow-wide Orchestration/Continuation mode and semantic `InvocationBinding` switch from the canonical ABI.

## WFL-62-04: Generated ABI and Bootstrap

Stage Status:
- Current status: OPEN
- Owner: Cozy generator / generated-contract owner
- Update rule: Mark DONE only after deterministic generated artifacts can be admitted by CNCF without CML reparsing.

- [ ] Generate StateMachine/Workflow identity, API/SPI, ActionExecution, Continuation/Context/Completion/Evidence schemas.
- [ ] Preserve source correlation/provenance for State, Action, Operation and required SPI identity.
- [ ] Emit direct ComponentFactory bootstrap metadata without runtime policy or inferred name matching.
- [ ] Keep generation deterministic and ABI-versioned.
- [ ] Exclude dialog/open-screen commands, concrete transport, REST URL, specific AI model/provider, raw shell and runtime persistence details.
- [ ] Retain stable typed metadata sufficient for future local/REST API proxy projection without implementing those projections.

## WFL-62-05: Skill-Driven Producer Fixture

Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 62 coordinating with CNCF Phase 77 / `sm-workflow`
- Update rule: Mark DONE only after the real producer fixture demonstrates internal completion plus one external SPI suspension/resume boundary.

- [ ] Add a real CML Workflow fixture equivalent to `BuildProject -> RunTests -> ReviewChange -> CommitChanges`.
- [ ] Model Build/Test/Commit as internal Actions/providers that can produce `Completed`.
- [ ] Model ReviewChange as a StateMachine Required SPI operation capable of producing `Suspended(Continuation)`.
- [ ] Verify a typed ReviewResult can resume the suspended Action and permit subsequent transition/closing.
- [ ] Verify deterministic test provider binding can exercise the same StateMachine semantics without an AI/Skill provider.
- [ ] Verify stale ReviewResult is rejected by generated snapshot/revision contract.

## WFL-62-06: CNCF Handoff and Closure

Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 62 coordinating with CNCF Phase 77
- Update rule: Mark DONE only after exact producer evidence and consumer handoff are frozen; CNCF runtime acceptance remains external.

- [ ] Record exact CML source fixture and generated artifact digests/revision.
- [ ] Freeze the StateMachine/Workflow ABI version and compatibility requirements.
- [ ] Document Provided API / Required SPI schemas and ActionExecution/Continuation contracts for CNCF admission.
- [ ] Record future-compatibility metadata for assemble/API projection without claiming implementation.
- [ ] Complete Cozy-focused generation tests, executable specifications, regression validation, independent review, clean re-review where required, and final release closure.
- [ ] Do not claim CNCF runtime, Generic Skill Workflow Support, `sm-workflow` SQLite/CLI, Workflow-to-Workflow REST, UI Workflow or Flutter completion.
