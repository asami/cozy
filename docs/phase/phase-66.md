# Phase 66: Workflow Retry and Timeout ABI

Status: planned

## Goal

CML Workflow に sm-workflow の初期実運用で必要な Retry / Timeout の最小宣言意味論を追加し、CNCF が実行可能な generated ABI として出力する。

## Scope

- Action / Participant invocation の maximum attempts。
- fixed retry delay。
- execution timeout。
- validation と deterministic lowering。
- generated Workflow ABI への lossless projection。
- CNCF consumer fixture / handoff。

## Non-goals

Deadline、general Timer / Wait、Cancellation、FailurePolicy、general idempotency contract、backoff/jitter、dedicated Iteration semantics。

## Acceptance

real CML fixture から Retry / Timeout metadata が deterministic に生成され、CNCF が CML 再解析なしで admission できる。既存 Workflow は metadata 未指定時に従来 semantics を維持する。
