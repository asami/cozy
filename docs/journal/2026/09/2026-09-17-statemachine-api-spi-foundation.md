# StateMachine API/SPI Foundation

Date: 2026-09-17
Status: Design refinement / latest foundation

## Decision

Workflow固有のAPI/SPI機構を新設するのではなく、StateMachineをAPI/SPI境界を持つ基本実行モデルへ一般化し、WorkflowはそのStateMachine API/SPI機構を利用する。

```text
StateMachine
  API / Provided Interface
  SPI / Required Interface
  State
  Action
  Transition
  ActionExecution
       ^
       |
Workflow
  purpose / actors / use cases / process semantics
  uses StateMachine API/SPI
```

WorkflowはStateMachineの意味論を再利用するため、Workflow SPIとして議論してきたrequired operationsは実現上 `StateMachine SPI` のprojection/specializationとなる。

## StateMachine API

StateMachineを外部から起動・操作するtyped operationをProvided Interfaceとして定義できる。

例:

```text
API
  StartOrder
  CancelOrder
  GetStatus
```

Workflowは必要に応じてStateMachine APIをWorkflow/Component APIへprojection/exposeする。

## StateMachine SPI

StateMachineが遷移を完了するために外部Capabilityを必要とするtyped Action/OperationをRequired Interfaceとして定義する。

例:

```text
SPI
  RequestPayment  : PaymentRequest -> PaymentResult
  RequestApproval : ApprovalContext -> ApprovalDecision
```

StateMachine SPI providerはlocal/direct、external continuation、test/mock等で実装可能であり、provider placementによってStateMachine semanticsを変更しない。

## Execution

```text
State -> Action -> ActionExecution
                   Completed(Result)
                   Suspended(Continuation)
                   Failed(Error)
      -> Result -> Transition
```

SPI providerがruntime内で完了すればCompleted、外部Resultが必要ならSuspendedとなる。ContinuationはStateMachine/Workflow modeではない。

## Assemble

Component-owned Workflow/StateMachineの接続はassembleでStateMachine API/SPI bindingとして記述する方向とする。

```text
assemble Component
  bind StateMachine.spi.X -> Component/Workflow.api.Y
  expose StateMachine.api.Z -> Component.api.W
```

Workflowを独立Componentとして運用する場合も、Component構成要素として運用する場合も同じStateMachine API/SPI基盤を利用する。

## Scope

Phase 62の初期Skill-driven Workflow targetは維持するが、generated ABIの基礎概念はWorkflow専用SPIではなくStateMachine API/SPIとして定義する。UI StateMachine、Job、Entity lifecycle等への将来再利用を可能にする。
