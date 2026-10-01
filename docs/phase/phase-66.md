# Phase 66: Workflow Retry and Timeout ABI

Status: in-progress; unaccepted

Canonical progress record: [Phase66 checklist](phase-66-checklist.md).

## Goal

CML Workflow に sm-workflow の初期実運用で必要な Retry / Timeout の最小宣言意味論を追加し、CNCF が実行可能な generated ABI として出力する。

## Scope

- Action / Participant invocation の maximum attempts。
- fixed retry delay。
- execution timeout。
- validation と deterministic lowering。
- generated Workflow ABI への lossless projection。
- CNCF consumer fixture / handoff。

## Source contract

S66.1 defines the Workflow-owned optional `INVOCATION-POLICY` source section
and immutable normalized policy types. See the canonical
[source contract](../spec/workflow-retry-timeout-source-contract.md) and
[lowering design](../design/workflow-retry-timeout-source-lowering.md).
Participant means an existing REQUIRED-OPERATION capability, never a provider;
ADMISSION remains deterministic local semantics.

## Internal Steps and responsibility

- S66.1: Cozy source grammar, normalization and executable specifications.
- S66.2: Cozy optional v2 generated Workflow ABI/bootstrap for policy-bearing
  source; metadata-free inputs retain byte-identical v1 output.
- S66.3: CNCF metadata admission and a real CML-agnostic consumer fixture/handoff.
  CNCF Phase81 owns runtime enforcement.

All Step acceptance remains pending. Final full validation, independent Phase
review and release commit are pending; this in-progress status records no
test, review, commit or Phase acceptance.

## Non-goals

Deadline、general Timer / Wait、Cancellation、FailurePolicy、general idempotency contract、backoff/jitter、dedicated Iteration semantics。

## Acceptance

real CML fixture から Retry / Timeout metadata が deterministic に生成され、CNCF が CML 再解析なしで admission できる。既存 Workflow は metadata 未指定時に従来 semantics を維持する。
