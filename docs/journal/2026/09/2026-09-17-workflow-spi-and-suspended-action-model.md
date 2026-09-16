# Workflow SPI と Suspended Action Execution Model

Date: 2026-09-17
Status: Design refinement / supersedes protocol-mode interpretation

## Core refinement

WorkflowにOrchestration/Continuationという実行モードを持たせない。Workflow/StateMachineの基本意味論は常に次である。

```text
State -> select Action -> execute Action -> Result -> transition -> next State/Action
```

Action implementationの実行結果をgenericに扱う。

```text
ActionExecution
  Completed(Result)
  Suspended(Continuation)
  Failed(Error)
```

Runtime内で完結するActionはCompletedを返し、外部Participantの結果が必要なAction implementationはSuspended(Continuation)を返す。ContinuationはWorkflow modeではなく、Action executionが外部結果待ちになった時のdurable suspension valueである。

## Workflow SPI

外部Participantを必要とし、Continuationとして公開され得るtyped Action/Operation群は、Workflow/StateMachineが要求する **Workflow SPI (Required Interface)** として外部仕様化できる。

```text
Workflow Component
  Provided API
    start / advance / resume / status ...

  Required SPI
    RequestApproval : ApprovalContext -> ApprovalDecision
    ReviewChange    : ReviewContext -> ReviewResult
    CaptureMaterial : CaptureContext -> CaptureResult
```

Workflow SPIはOperation identity、typed input/result、Context、Completion Criteria、Evidence requirement、required capability等から生成できる。

Continuationは、Workflow SPI providerがRuntime内にdirect bindingされていない、またはdurable external interactionとして実装される場合のinvocation mechanismとなる。

```text
Required Operation
   -> direct SPI provider -> Completed(Result)
   -> external SPI provider -> Suspended(Continuation) -> resume(Result)
```

## Interface binding

将来のParticipant Workflow/UI Workflow接続はWorkflow同士のベタ結合ではなく、Required SPIとProvided Interfaceのtyped bindingとして扱える。

```text
Business Workflow
  requires RequestApproval
          |
          | typed binding
          v
UI Participant Workflow
  provides RequestApproval
```

input/result compatibilityをCML/producerで検証可能にする。

## Phase 62 implication

Phase 62ではSkillからWorkflowを確実に駆動する初期ターゲットを維持する。そのために必要な最小基盤として、typed Action/Operation、ActionExecution Completed/Suspended/Failed、Continuation resume contract、Workflow SPI projection可能なABIを優先する。

Workflow Connection/UI Workflow/Flutter generationは後続ロードマップのままとする。

以前の `InvocationBinding = ORCHESTRATION | CONTINUATION` はWorkflow semantic modeとして固定しない。必要ならruntime/provider binding metadataとして表現するが、core semanticsはActionExecutionの結果とWorkflow SPIに置く。
