# Workflow Execution Protocol を CML Workflow の基本構造にする

- Date: 2026-09-17
- Status: Design decision

## Decision

CML Workflow は StateMachine semantics を canonical control model とし、その上に Execution Protocol を第一級の実行境界として持つ。

基本 Protocol は次の二つとする。

- Orchestration Protocol: Workflow Runtime が Participant を直接 invoke する。
- Continuation Protocol: Workflow が semantic boundary で suspend し、Continuation を外部 Driver に返して Result で resume する。

両者は別 Workflow DSL ではない。同一 Workflow Definition、Operation、Guard、Result、Completion/Evidence semantics を共有する。

Continuation Protocol のために共通 Continuation / Context mechanism を定義する。Continuation は Resume Contract、Context Carrier、Completion Contract、Evidence Boundary から構成する。

Context は Workflow Context / Work Context / Execution Context に分離し、ContextBundle は summary/facts と ContextReference を中心に構成する。ContextSnapshot によって workflow/model/workspace/evidence revision を固定し、stale result を fail closed できるようにする。

この共通 Context/Invocation contract は Orchestration Protocol にも利用する。差は Invocation を Runtime が push/invoke するか、Continuation として yield/resume するかに限定する。

## Motivation

CNCF/Textus Runtime から AI/Service/Component を直接呼べる場合は Orchestration が自然である。一方 Codex/ChatGPT Skill のように外部側から Workflow を駆動する環境では Continuation が自然である。

Execution topology の違いを Workflow semantics の違いにするとモデルとExecutable Specificationが分裂する。Execution Protocol として分離すれば、同じ Workflow Model を両環境で利用できる。

`sm-workflow` は Continuation Protocol の先行 reference implementation とし、そこで得た durable continuation、revision、lease、idempotency、Context Budget の知見を CML/CNCF の共通 Workflow Execution Model へ戻す。

## Handoff

詳細設計は `docs/notes/workflow-execution-protocol.md` を正本候補とする。次期 CML Workflow phase では grammar/model/generated ABI に Execution Protocol、WorkflowInvocationContract、Continuation/Context contract を追加し、CNCF runtime handoff を作成する。
