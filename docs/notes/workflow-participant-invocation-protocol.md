# Workflow Participant Invocation Protocol

> **Status: historical design record.** This note records the earlier proposal
> to make `InvocationBinding = ORCHESTRATION | CONTINUATION` an Action /
> Participant attribute. It is superseded for Phase 62. The canonical model
> preserves semantic Participant/capability metadata and
> `StateMachine SPI -> Provider` binding; the bound provider returns
> `ActionExecution = Completed | Suspended(Continuation) | Failed`. The
> historical content below must not be used as the current ABI contract.

## Core model

Workflow の StateMachine semantics と Participant の呼び出し方式を分離する。

各 semantic Action / Operation は typed `WorkflowInvocationContract` を持ち、その `InvocationBinding` が Participant の実行方式を指定する。

```text
InvocationBinding
  ORCHESTRATION
  CONTINUATION
```

- ORCHESTRATION: Runtime が Participant を direct invoke し Result を受け取る。
- CONTINUATION: Runtime が durable Continuation を yield し、external driver が Result を後から submit して resume する。

Workflow 全体の mode ではなく Action/Participant 単位の binding とし、同一 Workflow 内で混在を許す。

## Shared invocation contract

```text
WorkflowInvocationContract
  operation
  participant
  inputSchema
  resultSchema
  ContextBundle
  ContextSnapshot
  CompletionContract
  EvidenceContract
  InvocationBinding
  presentationHints?
```

`presentationHints` は semantic meaning ではない。UI、Skill、MCP等の adapter が利用する optional projection metadata とする。

## Human UI

Human Approval を Workflow 内の dialog command としてモデル化しない。

```text
RequestApproval
  input: ApprovalContext
  result: ApprovalDecision
  participant: HUMAN
  binding: CONTINUATION
```

Runtime は Continuation を永続化して return できる。UI は Continuation Contract を受け取り表示し、後から Decision を submit する。Workflow process/thread を UI 待ちで保持する必要はない。

## AI

AI Review 等も同じ構造を利用する。

```text
ReviewChange
  participant: AI
  binding: CONTINUATION
```

AIをRuntimeから直接呼べる環境では同じOperationをORCHESTRATION bindingへ変更できる。Workflow semantics、Completion Criteria、Evidence Contractは変更しない。

## Invariants

1. Protocol/binding は Workflow semantics を変更しない。
2. Continuation driver は Workflow control semantics owner ではない。
3. Continuation は durable Resume Contract + Context Carrier + Completion Contract + Evidence Boundary である。
4. stale ContextSnapshot の Result は fail closed する。
5. UI command、model name、host-specific path を semantic Workflow Action に埋め込まない。
6. Orchestration と Continuation の混在を標準ケースとして扱う。
