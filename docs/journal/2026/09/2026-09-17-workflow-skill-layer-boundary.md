# Workflow / Skill Layer Boundary

Date: 2026-09-17
Status: Design decision / Phase 62 clarification

## Decision

CML/CozyはSkill Workflow固有の実行支援を所有しない。CMLはSkill、UI、AI、Human、remote workerに共通するWorkflow semanticsとParticipant Invocation contractを定義する。

Generic Skill Workflow SupportはCNCF runtime/application-support layerに置く。

```text
CML / Cozy
  Workflow / StateMachine semantics
  WorkflowInvocationContract
  Participant + InvocationBinding
  Context / Continuation / Completion / Evidence contracts
        |
        v
CNCF
  runtime implementation
  Generic Skill Workflow Support
        |
        v
Domain specialization (e.g. sm-workflow)
```

## CML exclusion

CML generic ABIに次を入れない。

- SkillWorkOrderなどSkill専用projection名
- Parent/Worker AI topology
- concrete model/provider/reasoning level
- software developmentのPLAN/IMPLEMENT/REVIEW語彙
- git/sbt/build/test固有policy

ただし、Skill layerが必要とするmodel-independentな情報を表現できるgeneric contractはCMLに置く。例: Participant、required capability、risk/constraints、ContextBundle、Completion/Evidence contract。

## Promotion rule

CNCF Skill Workflow Supportで得た機構がSkill以外のUI/Human/Serviceにも一般化できると確認された場合、CML/CNCF generic Workflow contractへ昇格する。

CMLは実利用の便利機能を先取りしてSkill固有概念を増やすのではなく、一般化が確認された意味契約だけを取り込む。
