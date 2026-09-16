# Workflow SPI -> API Projection and Workflow Connection

Date: 2026-09-17
Status: Design refinement / future connection model

## Goal

Caller WorkflowからCallee Workflowを利用するとき、Callee側のWorkflow SPI/required interaction contractをCaller側では通常のtyped Workflow APIとして扱えるようにする。CallerのAction implementationは、相手が別Workflowであること、Continuationでsuspendすること、local/remote transportの違いを意識しない。

```text
Callee Workflow SPI specification
          |
          | projection / binding
          v
Caller-side Workflow API proxy
          |
          v
Caller Action
```

## Workflow API projection

Calleeが外部に要求/公開可能なtyped interaction contractから、Caller側で利用するAPI proxy contractを生成できるようにする。

概念例:

```text
ReviewWorkflow interface
  ReviewDocument : ReviewRequest -> ReviewResult
```

Caller Actionは生成APIだけを利用する。

```text
reviewWorkflow.reviewDocument(request)
```

Action implementationはWorkflowRun identity、Continuation、correlation、REST endpoint等を直接操作しない。

## Binding and transport separation

Workflow interface contractとtransport/deployment bindingを分離する。

```text
Generated Workflow API
  -> LocalWorkflowBinding  -> method/direct runtime call
  -> RestWorkflowBinding   -> REST remote call
  -> future bindings       -> other transports
```

CML semantic modelはlogical Workflow/API bindingを表現し、physical URL、service discovery、credential等はdeployment/runtime configurationに置く。

同一Workflow compositionを配置に応じてlocal method callまたはREST remote connectionへ変更しても、Caller Action codeとWorkflow semanticsは変更しない。

## Durable WorkflowCall

Workflow間呼び出しは長時間suspend、restart、correlation、timeout/cancelを伴い得るため、通常の同期method returnと同一視しない。generated API/runtimeでは概念的に `WorkflowCall[A]` のようなdurable call semanticsを表現できる余地を残す。

## Required / Provided interface connection

将来のWorkflow Connectionはtyped interface bindingとしてモデル化する。

```text
Caller requires/uses ReviewWorkflow API
          |
          | Workflow Connection
          v
Callee provides corresponding Workflow endpoint
```

Continuation-based SPI interactionを別Workflowが実装する場合も、Required/Provided input/result contractの型整合をproducerで検証する。

## Scope

Phase 62の初期ターゲットはSkill-driven Continuation基盤の確立であり、Workflow-to-Workflow API proxy生成やREST connector実装は後続Phaseとする。ただしWorkflow SPI ABIは将来のAPI projection/connectionを阻害しない構造にする。
