# Phase 62 Addendum: StateMachine API/SPI Foundation

Status: planned / normative refinement

## Goal

Phase 62 Workflow ABIのAPI/SPI/Continuation基盤をWorkflow専用機構として実装せず、StateMachine API/SPIの一般機構として定義・生成し、Workflowから再利用する。

## Requirements

- StateMachine generated ABI can declare/project Provided API and Required SPI operations.
- Required SPI operations preserve stable identity, typed input/result and generic Context/Completion/Evidence contracts.
- Action execution supports Completed/Suspended/Failed or equivalent typed outcomes.
- Suspended carries a durable Continuation for external SPI provider completion.
- Local/direct, external-continuation and test provider placement do not alter StateMachine transition semantics.
- Workflow generated ABI reuses/projects StateMachine API/SPI instead of defining a parallel interface model.
- Preserve enough identity/type metadata for future assemble binding and caller-side API projection.

## Immediate acceptance

The Skill-driven reference Workflow uses a StateMachine whose internal build/test actions complete locally and whose Review SPI suspends externally. A typed ReviewResult resumes the same StateMachine, which then executes closing actions and reaches terminal state.

## Non-goals

Phase 62 does not yet implement the full assemble syntax, Workflow-to-Workflow API proxy, REST connector, UI Workflow generation or Flutter generation. These remain follow-up work built on the StateMachine API/SPI ABI.
