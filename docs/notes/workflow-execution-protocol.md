# CML Workflow Execution Protocol

- Status: Historical design baseline; superseded for Phase 62 by the StateMachine API/SPI model
- Date: 2026-09-17

## Supersession

This note preserves the earlier protocol-oriented exploration. It is not the
normative Phase 62 execution ABI. The current model keeps
`StateMachine SPI -> Provider` binding, and the selected provider determines
the outcome through `ActionExecution = Completed | Suspended(Continuation) |
Failed`. A Participant or capability may describe who can provide work, but it
does not declare an orchestration/continuation execution mode. The historical
protocol and binding material below must not be used to reintroduce
`InvocationBinding = ORCHESTRATION | CONTINUATION`.

## Purpose

CML Workflow の基本構造として、Workflow semantics と execution driving を分離する。同じ Workflow / StateMachine / Operation 定義に対して、実行環境に応じて二つの Execution Protocol を利用できるようにする。

```text
Workflow Definition
  StateMachine / Guard / Operation / Result Contract
                    |
           Workflow Invocation Contract
                    |
          +---------+---------+
          |                   |
          v                   v
 Orchestration Protocol   Continuation Protocol
```

Workflow を二種類に分けない。Protocol が変えるのは Participant への delivery / execution driving であり、Workflow semantics は共通とする。

原則:

> Who drives execution changes; workflow semantics do not.

## Orchestration Protocol

Workflow Runtime が Orchestrator として Participant を直接 invoke する。

```text
Workflow Runtime
  -> Invocation(Operation, Context, Contract)
  -> Participant
  -> Result / Evidence
  -> Workflow continues
```

Component Operation、AI Runtime、Service、Human Task adapter 等を Runtime から呼べる環境で標準的に利用する。

## Continuation Protocol

Workflow Runtime が semantic boundary で suspend し、外部 Driver に Continuation を返す。Driver が Participant を実行し、Result を返して Workflow を resume する。

```text
External Driver
  -> advance
  -> Workflow Runtime
  -> Continuation
  -> Participant
  -> ContinuationResult
  -> resume
  -> Workflow Runtime
```

External Driver は execution を駆動するが Workflow semantics の owner ではない。次に許可される作業は WorkflowRun / StateMachine / Continuation が決定する。

## Common Workflow Invocation Contract

両 Protocol は次の意味情報を共有する。

```text
WorkflowInvocationContract
  operation
  ContextBundle
  CompletionContract
  EvidenceContract
  ExecutionRequirements
```

### ContextBundle

大量の Context を value として複製せず、必要最小限の facts/summary と versioned reference を持つ。

```text
ContextBundle
  summary
  requiredFacts
  references: ContextReference*
  snapshot: ContextSnapshot
```

Context は三層に分ける。

1. Workflow Context: WorkflowRun、Plan、State、Decision、Evidence 等の durable canonical state。
2. Work Context: 対象、input、constraint、completion criteria、required evidence 等、その作業に必要な情報。
3. Execution Context: workspace、tool capability、host policy 等、Executor 側の一時情報。

Workflow Context 全体や conversation/source/log 全体を Continuation payload にコピーしない。

### ContextSnapshot

Continuation / Invocation 発行時の前提 revision を保持し、Result 受理時に staleness を検証できるようにする。

```text
ContextSnapshot
  workflowRevision
  modelRevision?
  workspaceRevision?
  evidenceRevision?
```

重要な前提が変わっていれば fail closed とし、stale result をそのまま受理しない。

## Continuation Contract

Continuation は次の組み合わせとして定義する。

```text
Continuation
  = Resume Contract
  + Context Carrier
  + Completion Contract
  + Evidence Boundary
```

概念フィールド:

```text
Continuation
  runId
  continuationId
  revision
  boundaryKind
  operation
  requiredCapabilities
  context: ContextBundle
  completionContract
  evidenceContract
  executionPolicy
```

AI専用にはしない。Human Task、external service、async job、remote executor も同じ機構を利用できる。

## Protocol Binding

初期設計では Workflow/Runtime profile 単位の Protocol 選択を可能にし、将来的には Operation / Participant binding ごとに Orchestration と Continuation を混在できるようにする。

```text
Workflow
  -> Component Operation : ORCHESTRATION
  -> AI Review           : CONTINUATION
  -> Build Operation     : ORCHESTRATION
  -> Human Approval      : CONTINUATION
```

Protocol binding は Operation semantics を変更しない。

## CML responsibility

CML/Cozy は次を所有する。

- Workflow identity と StateMachine semantics
- semantic boundary
- typed Operation / Result contract
- WorkflowInvocationContract の生成可能な model
- Context/Completion/Evidence contract の構造
- Execution Protocol / binding metadata
- generated Workflow ABI

CML/Cozy は durable execution、SQLite、lease、external AI dispatch、specific runtime provider を所有しない。それらは CNCF/Textus/sm-workflow 等の Runtime responsibility とする。
