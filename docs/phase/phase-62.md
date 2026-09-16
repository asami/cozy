# Phase 62: CML Workflow Execution Protocol and Producer ABI

Status: planned

## Goal

CML Workflow の基本構造として Orchestration Protocol と Continuation Protocol を定義し、同じ Workflow semantics を両 Protocol から利用できる generated ABI を確立する。

## Design baseline

- StateMachine / Composite StateMachine semantics を canonical control model とする。
- Workflow を Protocol ごとに別定義しない。
- `WorkflowInvocationContract` を両 Protocol の共通意味契約とする。
- `Continuation` を Resume Contract + Context Carrier + Completion Contract + Evidence Boundary とする。
- ContextBundle / ContextReference / ContextSnapshot を共通 model とする。
- stale continuation/result は fail closed できる contract を生成する。
- raw shell、runtime persistence、specific AI model/provider は CML contract に入れない。

## Scope

1. Workflow identity / semantic boundary の CML model を確定する。
2. Execution Protocol taxonomy (`ORCHESTRATION | CONTINUATION`) を定義する。
3. Workflow/profile 単位の protocol binding を定義する。
4. Operation/Participant 単位の binding を将来拡張可能な形で model 化する。
5. `WorkflowInvocationContract` を定義する。
6. `ContextBundle`, `ContextReference`, `ContextSnapshot` を定義する。
7. `CompletionContract`, `EvidenceContract` を定義する。
8. `Continuation`, `ContinuationResult`, resume contract を定義する。
9. generated Workflow ABI に上記 metadata/schema を出力する。
10. 同一 Workflow fixture が Orchestration / Continuation の双方へ bind でき、Operation semantics が同一であることを検証する。
11. CNCF consumer 向け versioned handoff fixture/document を作成する。

## Acceptance

- 同じ Workflow Definition を Protocol 別に複製しない。
- Protocol変更でState/Guard/Operation/Result semanticsが変化しない。
- Continuation が resume に必要な identity/revision/context/completion/evidence contract を持つ。
- Context payload に canonical Workflow state や全source/logを無制限コピーする設計になっていない。
- ContextSnapshot により stale result を検出可能である。
- Orchestration invocation と Continuation yield が同じ WorkflowInvocationContract を共有する。
- generated ABI が CNCF から CML 再解析なしで admission 可能である。
- AI/Human/Service/Job のいずれにも Continuation contract を適用可能である。

## Non-goals

- durable WorkflowRun datastore
- SQLite provider
- lease/idempotency implementation
- AI model selection/dispatch implementation
- Participant の具体的 remote transport
- CNCF runtime execution implementation

## Handoff

Phase closure では CNCF に以下を渡す。

- frozen Workflow ABI version
- real CML fixture
- protocol/binding metadata
- WorkflowInvocationContract schema
- Continuation/Context/Completion/Evidence schemas
- stale-result validation contract
- deterministic generation evidence

Planning references:

- `docs/notes/workflow-execution-protocol.md`
- `docs/journal/2026/09/2026-09-17-workflow-execution-protocol-baseline.md`
