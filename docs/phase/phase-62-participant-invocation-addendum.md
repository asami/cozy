# Phase 62 Addendum: Participant Invocation Protocol

Status: planned / normative addendum to Phase 62

## Goal

CML Workflow の基本構造として、Orchestration / Continuation を Workflow-level の排他的 mode ではなく semantic Action / Participant 単位の Invocation Binding として生成ABIへ表現する。

## Requirements

- `WorkflowInvocationContract` を generated ABI の共通 invocation boundary とする。
- `InvocationBinding = ORCHESTRATION | CONTINUATION` を closed contract として定義する。
- 同一 Workflow 内で両 binding を混在可能にする。
- CONTINUATION は ContextBundle / ContextSnapshot / CompletionContract / EvidenceContract を持つ durable resume boundary を生成できるようにする。
- HUMAN participant の Approval/Decision を Continuation として表現できるようにする。
- AI participant も同じ Continuation contract を利用できるようにする。
- UI表示は `presentationHints` 等の optional projection metadata とし、dialog/open-screen commandをWorkflow semanticsへ埋め込まない。
- 同一 Operation の binding を環境に応じて ORCHESTRATION / CONTINUATION へ変更しても operation/result semantics が不変であることを検証する。

## Acceptance examples

```text
Action BuildProject       -> ORCHESTRATION
Action RequestApproval    -> CONTINUATION / HUMAN / UI
Action ReviewChange       -> CONTINUATION / AI
Action CommitChanges      -> ORCHESTRATION
```

この混在Workflowが一つのStateMachine/Workflow definitionから生成され、protocol差によってsemantic transitionが分岐しないことをacceptanceとする。
