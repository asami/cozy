# Workflow / Skill Layer Boundary

CML Workflow is domain- and client-neutral.

CML owns:

- Workflow / StateMachine semantics
- typed Operation and Participant
- StateMachine Provided API / Required SPI declarations
- assemble-time `StateMachine SPI -> Provider` binding
- `ActionExecution = Completed | Suspended(Continuation) | Failed`
- ContextBundle / ContextSnapshot / ContextReference semantics
- CompletionContract / EvidenceContract
- generic capability/constraint metadata required to execute a Provider

CML does not own:

- Skill-specific WorkOrder projections
- parent/worker AI orchestration policy
- model/reasoning dispatch policy
- Skill context-budget heuristics
- domain-specific software development operations

Generic Skill Workflow Support is implemented by CNCF above these contracts. `sm-workflow` is a further Software Development specialization.

This boundary keeps generated Workflow ABI reusable by UI/Human, AI, remote services and deterministic runtime operations without importing Skill-specific vocabulary. Participant/capability metadata describes who can provide a capability; it does not declare whether execution completes directly or suspends. That outcome is returned by the selected Provider as `ActionExecution`.
