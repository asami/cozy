# Workflow / Skill Layer Boundary

CML Workflow is domain- and client-neutral.

CML owns:

- Workflow / StateMachine semantics
- typed Operation and Participant
- InvocationBinding (ORCHESTRATION / CONTINUATION)
- WorkflowInvocationContract
- ContextBundle / ContextSnapshot / ContextReference semantics
- CompletionContract / EvidenceContract
- generic capability/constraint metadata required to execute an invocation

CML does not own:

- Skill-specific WorkOrder projections
- parent/worker AI orchestration policy
- model/reasoning dispatch policy
- Skill context-budget heuristics
- domain-specific software development operations

Generic Skill Workflow Support is implemented by CNCF above these contracts. `sm-workflow` is a further Software Development specialization.

This boundary keeps generated Workflow ABI reusable by UI/Human, AI, remote services and deterministic runtime operations without importing Skill-specific vocabulary.
