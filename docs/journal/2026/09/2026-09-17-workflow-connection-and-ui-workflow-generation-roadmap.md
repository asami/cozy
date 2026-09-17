# Workflow Connection と UI Workflow Generation Roadmap

Date: 2026-09-17
Status: Long-term design direction / initial scope constrained

## 方針

CMLでは将来的にWorkflow単体だけでなく、Business Workflowのsemantic Actionと外部Participant Workflowの接続をモデル化可能にする。

代表例として、server-side Business WorkflowとFlutter側UI WorkflowをContinuation境界で接続する。

```text
Business Workflow (CNCF/server)
  -> RequestApproval
       |
       | Continuation / Workflow Binding
       v
UI Workflow (Flutter/client)
  -> ShowSummary
  -> ShowDetails
  -> Edit/Confirm
  -> ApprovalDecision
       |
       v
Business Workflow resume
```

Business WorkflowとUI Workflowを一つの巨大Workflowにはしない。Business Workflowは業務上の状態・意味を所有し、UI WorkflowはParticipant側のinteraction processを所有する。両者はtyped Invocation/Continuation Contractで接続する。

## 将来のCML概念候補

- Workflow Connection / Workflow Binding
- Participant Workflow
- UI Workflow
- typed input/result compatibility
- Continuation binding
- participant/runtime placement metadata

概念的には次を表現できるようにする。

```text
Business Action
  operation: RequestApproval
  input: ApprovalContext
  result: ApprovalDecision
  participant: HUMAN
  invocation: CONTINUATION
  targetWorkflow: ApprovalUiWorkflow

UI Workflow ApprovalUiWorkflow
  input: ApprovalContext
  result: ApprovalDecision
```

producerはBusiness Action inputとParticipant Workflow input、Participant Workflow resultとBusiness Action resultの型整合を検証する。

## 将来の生成

Cozyの長期的な生成パイプライン候補:

```text
CML
  -> Workflow Connection Model
       +-> Server Business Workflow ABI -> CNCF
       +-> UI Workflow IR -> Flutter generator -> Dart/Flutter
       `-> Continuation client/server binding artifacts
```

UI Workflow IRは画面だけでなくstates/transitions/actions/screens/forms/local operations/continuation input/resultを保持し、PSWA等で確認可能にした上で将来Flutterへ生成する方向を検討する。

Flutter側では `textus-flutter-core` の将来RuntimeがContinuation Client、Action Registry、UI Workflow Runtime、Context Resolver、Result/Resume Client等を提供する想定である。

## Initial target / 非目標

ただし最初のWorkflow実装段階では、Workflow Connection、UI Workflow、Flutter generatorまでは狙わない。

初期ターゲットは明確に次へ限定する。

> SkillからCML/CNCF WorkflowをContinuation経由で確実に駆動し、semantic workのResultを安全にresumeできること。

初期段階で優先するもの:

- first-class Workflow / StateMachine semantics
- Participant Invocation Binding
- Continuation identity / Context / Completion / Evidence
- durable yield/resume
- Skill-friendly CNCF support
- stale/idempotency/restart safety
- deterministic operationとsemantic continuationの混在

UI Workflow Connection / Flutter generationは、この基盤が実用上安定した後の後続段階とする。

## 設計上の留保

初期ABI/APIは将来のParticipant Workflow bindingを阻害しない形にするが、将来機能を先取りしてPhase 62の実装量を増やさない。Workflow Connection syntax、UI Workflow grammar、Flutter-specific metadataは後続Phaseで具体化する。
