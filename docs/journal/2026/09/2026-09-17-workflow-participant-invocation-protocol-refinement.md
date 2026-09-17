# Workflow Participant Invocation Protocol refinement

Date: 2026-09-17
Status: Design decision / Phase 62 input

## Refinement

Orchestration Protocol と Continuation Protocol を Workflow 全体で排他的に選択する execution mode として扱わない。両者は Workflow Runtime と個々の Participant / Action / Operation の境界に適用する **Participant Invocation Protocol** として定義する。

Workflow semantics は StateMachine、Guard、Operation、Input/Result schema、Completion Criteria、Evidence Contract に置く。各 semantic Action がどのように Participant へ delivery されるかを invocation binding が決める。

```text
Workflow
  -> Action A : ORCHESTRATION
  -> Action B : ORCHESTRATION
  -> Human Approval : CONTINUATION -> UI
  -> Action C : ORCHESTRATION
  -> AI Review : CONTINUATION -> AI Driver
  -> Action D : ORCHESTRATION
```

## Orchestration binding

Runtime が Participant を直接 invoke し、Result を受け取って継続する。

## Continuation binding

Runtime は Invocation Contract を durable Continuation として yield し、一度 control を caller へ返す。UI、AI Skill、remote worker 等が Result を後から submit し、Workflow を resume する。

Continuation は UI 専用でも AI 専用でもない。Human Approval は代表的な Continuation use case である。

```text
Workflow Runtime
  -> yield ApprovalContinuation
  -> persist / return

UI
  -> render from contract
  -> Human decision
  -> submit ApprovalDecision

Workflow Runtime
  -> validate snapshot/result
  -> resume
```

Workflow Action が `openDialog()` のような UI command を直接所有しない。Workflow は `RequestApproval : ApprovalContext -> ApprovalDecision` の意味契約を所有し、UI は Continuation Contract の projection として表示を構築する。

## UI / AI symmetry

```text
Human Approval
  participant: HUMAN
  invocation: CONTINUATION
  channel/presentation: UI

AI Review
  participant: AI
  invocation: CONTINUATION
  channel/presentation: SKILL | MCP | other adapter
```

UI と AI は同じ Continuation Protocol の異なる Participant/Adapter として扱える。

## Phase 62 impact

CML/producer ABI は Workflow-level protocol flag よりも Action/Participant binding を第一級にする。少なくとも generated ABI が次を表現できるようにする。

- Participant identity/kind
- Invocation binding: ORCHESTRATION | CONTINUATION
- Operation identity and typed input/result
- Context/Completion/Evidence contracts
- Continuation presentation/channel hints は semantic contract と分離した optional metadata

同じ Workflow 内で両 binding を混在できることを基本構造とする。
