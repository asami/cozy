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

S66.2's separately frozen [generated ABI contract](../spec/workflow-retry-timeout-abi-contract.md)
defines collection-wide schema selection and lossless Scala/JSON/bootstrap
projection. Its implementation is in-progress and acceptance remains pending.

## Internal Steps and responsibility

- S66.1: Cozy source grammar, normalization and executable specifications.
- S66.2: Cozy optional v2 generated Workflow ABI/bootstrap for policy-bearing
  source; metadata-free inputs retain byte-identical v1 output.
- S66.3: CNCF metadata admission and a real CML-agnostic consumer fixture/handoff.
  CNCF Phase81 owns runtime enforcement.

## S66.1

- Current status: COMMITTED; accepted.

Cozy source grammar and typed normalization were accepted in commit
`358ae6d1a233557b3e45087558ecff89e420f4af` (parent
`97bf6c1e641bb245cc425341523f2c31cbcda636`). Independent review
`P66-S661-REVIEW-001` PASS and validation `P66-S661A-VAL-002`
(166 passed / 0 failed) cover this predecessor source boundary.

## S66.2

- Current status: implementation in-progress; acceptance pending.

Cozy owns conditional v2 producer projection and v1 byte compatibility.
Tests, independent review, commit and acceptance for this Step remain pending.

## S66.3

- Current status: pending; unaccepted.

CNCF owns metadata admission and the actual generated consumer fixture;
Phase81 owns execution enforcement.

Final full validation, mandatory independent full Phase review and release
commit remain pending. S66.1 acceptance does not imply S66.2, S66.3 or Phase
acceptance; the Phase remains in-progress.

## Non-goals

Deadline、general Timer / Wait、Cancellation、FailurePolicy、general idempotency contract、backoff/jitter、dedicated Iteration semantics。

## Acceptance

real CML fixture から Retry / Timeout metadata が deterministic に生成され、CNCF が CML 再解析なしで admission できる。既存 Workflow は metadata 未指定時に従来 semantics を維持する。
