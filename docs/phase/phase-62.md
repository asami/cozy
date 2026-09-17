# Phase 62: First-Class CML WORKFLOW, Continuation, and Producer ABI

Status: planned

## Goal

`WORKFLOW` を first-class CML 宣言として定義し、既存の StateMachine /
Composite StateMachine semantics に正規化する。その一つの意味論から、同じ
State / Action / Operation / Result 契約を実行する Orchestration と
Continuation を、Action / Participant ごとに選べる generated ABI として確立する。

## Design baseline

- StateMachine / Composite StateMachine semantics を canonical control model とする。
- `WORKFLOW` は first-class CML declaration とし、既存の
  StateMachine / Composite StateMachine model へ正規化する。
- automatic transition と semantic boundary を明示的に区別し、曖昧な自動進行を
  許可しない。
- Workflow を Protocol ごとに別定義せず、Protocol の変更で State / Guard /
  Operation / Result semantics を変更しない。
- `InvocationBinding = ORCHESTRATION | CONTINUATION` は Workflow/profile 単位の
  選択ではなく、semantic Action / Participant ごとの閉じた binding とする。
- `ActionExecution = Completed | Suspended | Failed` を Workflow 専用の二重
  model ではなく、StateMachine 一般の generated API/SPI として定義する。
- `Suspended` は typed durable `Continuation` を持つ。Continuation は `runId`、
  revision、最小限の context、completion/evidence contract、および typed resume
  result を持つ。
- `ContextBundle` / `ContextReference` / `ContextSnapshot` は共通 model とし、
  stale continuation/result を fail closed にする契約を生成する。
- UI は optional presentation metadata とし、画面遷移・dialog・特定 transport を
  Workflow semantics に埋め込まない。将来の direct/REST proxy に必要な型情報だけを
  ABI に残す。
- raw shell、runtime persistence、specific AI model/provider は CML contract に
  入れない。

## Scope

1. `WORKFLOW` grammar、identity、version、constituent/reference boundary を定義し、
   StateMachine / Composite StateMachine に正規化する。
2. declared Workflow、entity-local StateMachine、runtime WorkflowInstance の identity
   を分離し、CML が WorkflowInstance persistence を所有しないことを固定する。
3. automatic transition と typed semantic boundary（Work Order / Decision / Wait を
   含む）の vocabulary、guard/effect、拒否診断を定義する。
4. StateMachine 一般の `ActionExecution = Completed | Suspended | Failed` と、
   direct / test / external provider placement を定義する。
5. Action / Participant ごとの `InvocationBinding = ORCHESTRATION | CONTINUATION`
   を定義し、同一 Workflow 内での混在を許可する。
6. `WorkflowInvocationContract`、typed input/result、および required SPI operation
   metadata を定義する。
7. `Continuation`、`ContinuationResult`、`ContextBundle`、`ContextReference`、
   `ContextSnapshot`、`CompletionContract`、`EvidenceContract`、resume contract を
   定義する。
8. generated Workflow/StateMachine ABI に、上記 schema、stable identity、
   typed API/SPI、optional presentation metadata、将来の direct/REST projection に
   必要な型情報を出力する。
9. direct ComponentFactory bootstrap metadata を、runtime policy や inferred name
   matching なしで出力する。
10. real CML fixture に Build → AI Review → Approval → Commit の mixed binding を
    定義し、semantic transition と operation/result semantics が不変であることを
    検証する。
11. deterministic generated evidence、ABI version、および CNCF `sm-workflow` consumer
    handoff fixture/document を固定する。

## Acceptance

- 同じ Workflow Definition を Protocol 別に複製しない。
- direct internal Action は `Completed` で進み、external Review/Approval のみが
  typed `Suspended(Continuation)` を返せる。
- 同一 Workflow 内で Build/Commit を ORCHESTRATION、AI Review/Approval を
  CONTINUATION に bind しても、State / Guard / Operation / Result semantics が
  不変である。
- Continuation が `runId`、revision、最小 context、completion/evidence contract、
  typed result を持ち、`ContextSnapshot` により stale result を fail closed にする。
- Context payload に canonical Workflow state、全 source、または全 log を無制限に
  コピーしない。
- real `WORKFLOW` source が既存 StateMachine / Composite StateMachine semantics と
  source compatibility を保って正規化され、曖昧な automatic progression を拒否する。
- generated ABI が CML 再解析なしで CNCF に admission 可能であり、将来の direct/REST
  proxy が core semantics の再定義を必要としない。
- UI Workflow、Flutter、Workflow Connection を実装せず、optional presentation
  metadata の範囲を越えない。

## Non-goals

- durable WorkflowRun datastore、SQLite provider、lease/idempotency implementation
- AI model selection/dispatch implementation
- Participant の具体的 remote transport
- Workflow-to-Workflow connection syntax、generated caller-side proxy、REST connector
- UI-WORKFLOW grammar、screen/form generation、Flutter/Dart generation、offline sync
- CNCF runtime execution implementation

## Handoff

Phase closure では CNCF に以下を渡す。

- frozen generated StateMachine/Workflow ABI version
- real first-class CML `WORKFLOW` fixture と deterministic generated evidence
- Action / Participant invocation-binding metadata
- `ActionExecution`、`WorkflowInvocationContract`、API/SPI schema
- Continuation / Context / Completion / Evidence schemas と stale-result validation contract
- optional presentation metadata と future proxy projection に残す typed metadata

Planning references:

- `docs/notes/workflow-execution-protocol.md`
- `docs/notes/statemachine-api-spi.md`
- `docs/phase/phase-62-participant-invocation-addendum.md`
- `docs/phase/phase-62-statemachine-api-spi-addendum.md`
