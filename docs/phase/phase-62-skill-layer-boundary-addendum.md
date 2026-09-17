# Phase 62 Addendum: Skill Layer Boundary

Status: planned / normative clarification to Phase 62

## Requirements

- Phase 62 generated Workflow ABI remains Skill-neutral.
- ABI provides generic Participant, StateMachine Required SPI, Provider binding, ActionExecution, Context, Continuation, Completion, and Evidence contracts sufficient for CNCF to build Skill Workflow projections.
- Do not generate SkillWorkOrder, ParentAI, WorkerAI, model name, reasoning level, git/sbt/build-specific concepts from generic CML Workflow declarations.
- Allow generic capability/constraint/risk metadata only where it is meaningful independent of Skill hosts.
- Demonstrate that the same generated StateMachine SPI and Provider-execution contract can be consumed by CNCF UI/Human, Skill/AI, direct, and deterministic-test providers without changing Workflow semantics.

Generic Skill Workflow Support belongs to CNCF Phase 77 or later runtime/application-support work, not to CML syntax/semantics unless a mechanism is proven client-neutral and promoted back into the generic contract.
