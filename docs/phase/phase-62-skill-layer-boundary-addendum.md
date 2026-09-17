# Phase 62 Addendum: Skill Layer Boundary

Status: planned / normative clarification to Phase 62

## Requirements

- Phase 62 generated Workflow ABI remains Skill-neutral.
- ABI provides generic Participant/Invocation/Context/Continuation/Completion/Evidence contracts sufficient for CNCF to build Skill Workflow projections.
- Do not generate SkillWorkOrder, ParentAI, WorkerAI, model name, reasoning level, git/sbt/build-specific concepts from generic CML Workflow declarations.
- Allow generic capability/constraint/risk metadata only where it is meaningful independent of Skill hosts.
- Demonstrate the same generated invocation contract can be consumed by CNCF UI/Human continuation and Skill/AI continuation adapters.

Generic Skill Workflow Support belongs to CNCF Phase 77 or later runtime/application-support work, not to CML syntax/semantics unless a mechanism is proven client-neutral and promoted back into the generic contract.
