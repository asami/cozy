# Phase 62: First-Class CML WORKFLOW on StateMachine API/SPI

Status: planned

## Goal

`WORKFLOW` を first-class CML 宣言として定義し、既存の StateMachine / Composite StateMachine semantics に正規化する。同時に StateMachine 一般の API/SPI と Action execution contract を generated ABI として確立し、Workflow はその基盤を再利用する。

初期ターゲットは Skill から Workflow を確実に駆動できる producer ABI までとする。Workflow-to-Workflow proxy、REST connector、UI Workflow、Flutter generation は後続段階へ分離する。

## Canonical model

```text
StateMachine
  Provided API
  Required SPI
  State / Action / Transition
  ActionExecution
    Completed(Result)
    Suspended(Continuation)
    Failed(Error)
        ^
        |
Workflow
  first-class declaration
  purpose / actor / use-case / process metadata
  normalized to StateMachine / Composite StateMachine semantics
```

Continuation は Workflow mode や protocol mode ではない。Action provider が外部 Result を必要とするときに `ActionExecution.Suspended` が返す durable suspension value である。

## Design baseline

- StateMachine / Composite StateMachine semantics を canonical control model とする。
- `WORKFLOW` は first-class CML declaration とし、既存 StateMachine model へ正規化する。
- Workflow 専用の API/SPI、Action algebra、Continuation engine を重複定義しない。
- StateMachine の Provided API / Required SPI を generated ABI の基本 interface model とする。
- StateMachine の基本進行は `State -> Action -> ActionExecution -> Result -> Transition` とする。
- `ActionExecution = Completed | Suspended | Failed` を typed closed contract とする。
- `Suspended` は typed durable `Continuation` を持つ。
- Required SPI provider は local/direct、external continuation、deterministic test provider 等へ binding 可能であり、provider placement は StateMachine semantics を変更しない。
- automatic transition と semantic boundary を明示的に区別し、曖昧な自動進行を許可しない。
- `ContextBundle` / `ContextReference` / `ContextSnapshot`、Completion / Evidence contract は external SPI completion に再利用できる共通 contract とする。
- stale continuation/result は fail closed とする。
- UI presentation、transport、specific AI model/provider、raw shell、runtime persistence は CML semantics に入れない。
- 将来の assemble による `StateMachine SPI -> Provided API` binding と caller-side API projection に必要な stable identity/type metadata は保持する。

## Scope

1. `WORKFLOW` grammar、identity、version、constituent/reference boundary を定義し、StateMachine / Composite StateMachine に正規化する。
2. declared Workflow、entity-local StateMachine、runtime WorkflowInstance の identity を分離する。
3. StateMachine Provided API / Required SPI の typed declaration / generated representation を定義する。
4. `ActionExecution = Completed | Suspended | Failed` と typed Result/Error/Continuation contract を定義する。
5. Required SPI operation metadataとして stable identity、typed input/result、generic Context、Completion、Evidence、capability/constraint を定義する。
6. `Continuation`、`ContinuationResult`、`ContextBundle`、`ContextReference`、`ContextSnapshot`、resume contract を定義する。
7. automatic transition と semantic external SPI boundary の guard/effect、拒否診断、source correlation を定義する。
8. generated StateMachine/Workflow ABI に API/SPI schema、ActionExecution、Continuation/Context/Completion/Evidence schema、stable identity を出力する。
9. direct ComponentFactory bootstrap metadata を runtime policy や inferred name matching なしで出力する。
10. real CML fixture に internal Build/Test、external Review SPI、internal Commit を定義し、ReviewだけがSuspendedとなる vertical slice を検証する。
11. deterministic generated evidence、ABI version、CNCF consumer handoff fixture/document を固定する。

## Acceptance

- Workflow は StateMachine / Composite StateMachine semantics に正規化され、別の Workflow control language を生成しない。
- internal Action は `Completed(Result)` で進行できる。
- external Review SPI は `Suspended(Continuation)` を生成し、typed ReviewResult で同じ Action を resume できる。
- resume 後は StateMachine が Result を評価して transition し、internal closing Action へ進める。
- provider placement を local/test/external で変更しても State / Guard / Operation / Result semantics を複製しない。
- Continuation が run/instance identity、revision、最小 Context、Completion/Evidence contract、typed result contract を持つ。
- `ContextSnapshot` により stale result を fail closed にする。
- Context payload に canonical Workflow state、全 source、全 log を無制限にコピーしない。
- generated ABI が CML 再解析なしで CNCF に admission 可能である。
- Skill host、UI、REST、specific model/provider の概念を generic CML ABI に固定しない。

## Initial reference scenario

```text
BuildProject  -> Completed
RunTests      -> Completed
ReviewChange  -> Suspended(Continuation)
ReviewResult  -> resume -> transition
CommitChanges -> Completed
Terminal
```

この vertical slice を `sm-workflow` が最初の Skill-driven consumer として利用できることを Phase 62 handoff の中心にする。

## Non-goals

- durable WorkflowRun datastore、SQLite provider、lease/idempotency implementation
- AI model selection/dispatch implementation
- Generic Skill Workflow runtime/support implementation
- Workflow-to-Workflow connection syntax、generated caller-side proxy、REST connector
- full assemble API/SPI binding implementation
- UI-WORKFLOW grammar、screen/form generation、Flutter/Dart generation、offline sync
- CNCF runtime execution implementation

## Handoff

Phase closure では CNCF に以下を渡す。

- frozen generated StateMachine/Workflow ABI version
- real first-class CML `WORKFLOW` fixture と deterministic generated evidence
- StateMachine Provided API / Required SPI schema
- `ActionExecution = Completed | Suspended | Failed`
- Continuation / Context / Completion / Evidence schemas と stale-result validation contract
- future assemble/API projection に必要な stable identity/type metadata

## Planning references

Current design:

- `docs/notes/statemachine-api-spi.md`
- `docs/notes/workflow-spi.md`
- `docs/phase/phase-62-statemachine-api-spi-addendum.md`
- `docs/phase/phase-62-workflow-spi-addendum.md`
- `docs/phase/phase-62-initial-scope-and-ui-workflow-roadmap-addendum.md` (UI roadmap and deferrals only; its earlier execution-binding clauses are historical)

Historical refinement journals and earlier protocol/binding addenda remain as design history. Where they conflict with this consolidated Phase 62, this document and the StateMachine API/SPI foundation are normative.
