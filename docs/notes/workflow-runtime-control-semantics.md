# Workflow Runtime Control Semantics

## Purpose

CML / Cozy 側で Workflow の実用制御を宣言・ABI 化するための拡張方針を整理する。runtime execution 自体は CNCF の責務であり、Cozy は StateMachine / Workflow source semantics と generated ABI の authority を持つ。

## Core principle

Retry 回数や timeout を Retry1 / Retry2 のような State として展開しない。State / Transition / Event / Guard / Action / Context という一般的 StateMachine semantics を維持し、Workflow declaration から runtime policy metadata を生成する。

Retry と Iteration は別概念とする。

- Retry: 同一 logical action の技術的再実行。
- Iteration: Goal 達成のために結果を評価して意味的作業を反復する。

## Extension set

### Operational minimum

- Retry declaration: maximum attempts, fixed delay。
- Timeout declaration: Action / Participant invocation execution timeout。
- attempt / timeout semantics を generated Workflow ABI へ lossless に投影する。

### Scheduling and lifecycle

- Deadline。
- Timer / Wait。
- Cancellation semantics。
- runtime suspension/resumption に必要な declarative metadata。公開 suspend/resume command を source language に要求しない。

### Failure and execution safety

- retryable / permanent を中心とした failure classification。
- FailurePolicy。
- idempotency / duplicate-protection contract に必要な logical execution identity metadata。
- backoff / jitter は必要性が確認された時点で追加可能とする。

### Goal-oriented iteration

- NeedsInput / NeedsRevision / Rejected 等の semantic outcome。
- review -> revision -> review 等の iteration。
- 専用 Iteration abstraction は通常の StateMachine transition では重複が大きいことが実運用で確認された場合に限定して導入する。

## Boundary with CNCF

Cozy は syntax / model / validation / lowering / generated ABI を所有する。timer scheduling、attempt execution、timeout enforcement、cancellation、deduplication 等の runtime behavior は CNCF が所有する。

## Consumer boundary

sm-workflow はこれらの semantics の consumer であり、Cozy の将来拡張ロードマップを所有しない。当面必要な Retry / Timeout のみを利用する。
