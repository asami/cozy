# Phase 66: Workflow Retry and Timeout ABI

Status: COMPLETE; Cozy CML/ABI producer delivery closed 2026-10-02

Canonical progress record: [Phase66 checklist](phase-66-checklist.md).

## Goal

CML Workflow に sm-workflow の初期実運用で必要な Retry / Timeout の最小宣言意味論を追加し、CNCF が受け取れる generated ABI として提供する。CNCF の実行機構への繋ぎ込みは CNCF 側が引き取る。

## Scope

- Action / Participant invocation の maximum attempts。
- fixed retry delay。
- execution timeout。
- validation と deterministic lowering。
- generated Workflow ABI への lossless projection。
- 生成物と既存 CNCF consumer fixture / receiver の証拠を含む handoff。

2026-10-02 のユーザー指示により、Cozy の完了範囲は CML 機能と生成 ABI
の提供までとする。CNCF の繋ぎ込み・実行処理・残る CNCF 検証は CNCF 側が所有する。

## Source contract

S66.1 defines the Workflow-owned optional `INVOCATION-POLICY` source section
and immutable normalized policy types. See the canonical
[source contract](../spec/workflow-retry-timeout-source-contract.md) and
[lowering design](../design/workflow-retry-timeout-source-lowering.md).
Participant means an existing REQUIRED-OPERATION capability, never a provider;
ADMISSION remains deterministic local semantics.

S66.2's separately frozen [generated ABI contract](../spec/workflow-retry-timeout-abi-contract.md)
defines collection-wide schema selection and lossless Scala/JSON/bootstrap
projection. S66.2 is COMMITTED and accepted; its evidence is recorded below.

## Internal Steps and responsibility

- S66.1: Cozy source grammar, normalization and executable specifications.
- S66.2: Cozy optional v2 generated Workflow ABI/bootstrap for policy-bearing
  source; metadata-free inputs retain byte-identical v1 output.
- S66.3: CNCF metadata admission and a real CML-agnostic consumer fixture/handoff.
  CNCF Phase81 owns runtime enforcement.

## S66.1

- Current status: COMMITTED; accepted.
- Owner: Cozy source/model producer; parent owns acceptance.
- Update rule: preserve accepted source evidence; change status only through parent acceptance.

Cozy source grammar and typed normalization were accepted in commit
`358ae6d1a233557b3e45087558ecff89e420f4af` (parent
`97bf6c1e641bb245cc425341523f2c31cbcda636`). Independent review
`P66-S661-REVIEW-001` PASS and validation `P66-S661A-VAL-002`
(166 passed / 0 failed) cover this predecessor source boundary.

## S66.2

- Current status: COMMITTED; accepted.
- Owner: Cozy generated Workflow ABI/bootstrap producer.
- Update rule: preserve accepted producer receipts; parent alone records successor acceptance.

S66.2 acceptance: commit `e3262754023e0afc42bb5fcd3aeecf1cca0ab78e`, parent
`358ae6d1a233557b3e45087558ecff89e420f4af`; review `P66-S662-REVIEW-001`
PASS; `P66-S662A-VAL-001` (152 passed / 0 failed); `P66-S662A-VAL-002`
(three actual generated Scala 3.3.8 files compiled into 93 classes,
SBT exit 0, lock released). These receipts cover the producer Step only;
Historical S66.3 fixture/receiver Step acceptance, Cozy producer closure and
CNCF-owned integration follow-up are recorded in the handoff and Phase checklist.

## S66.3

- Current status: COMMITTED; accepted.
- Owner: CNCF metadata admission and generated consumer fixtures; Cozy owns producer handoff.
- Update rule: parent records evidence after consumer validation and independent Step review.

CNCF owns metadata admission and the actual generated consumer fixture;
Phase81 owns execution enforcement. The [consumer handoff](phase-66-cncf-handoff.md)
records the pure receiver, actual fixtures and accepted Step evidence.

## Phase closure

Phase66 is COMPLETE at the Cozy CML and generated ABI producer-delivery
boundary, following the user's 2026-10-02 instruction. Cozy supplies the
Retry / Timeout declarations, strict source validation, deterministic lossless
v2 JSON/Scala/bootstrap metadata, metadata-free v1 compatibility and the
documented consumer handoff. CNCF owns connecting these products to its
ComponentFactory/runtime, execution enforcement and remaining CNCF validation.

All prior Step commits are preserved: Cozy S66.1
`358ae6d1a233557b3e45087558ecff89e420f4af`, S66.2
`e3262754023e0afc42bb5fcd3aeecf1cca0ab78e`, and the historical S66.3
fixture/receiver evidence in Cozy `a8a78a23492a7aed40a6cb11239fcdaa23afa240`
and CNCF `f7cf5bc04c11b9f74e09d61b8199452c5edb3275`. S66.3 proves the
pure metadata receiver and generated fixtures; production runtime integration
remains CNCF-owned work.

Exactly one independent full review, `P66-FULL-REVIEW-E1-001`, covered the
25-file accumulator. Its producer-specification authoring blocker was closed
by `P66-PHASE-E1-C1-M0-WAIVER-001`: six action/IO relocations in four existing
scenarios, preserving requests, expectations and fixtures. Normal final Cozy
`sbt --batch test`, `P66-FINAL-COZY-VAL-001`, passed after this repair:
2,367 succeeded / 0 failed / 9 canceled; 178 suites completed, none aborted;
SBT and wrapper exit 0, lock released. Subsequent closure edits affect
documentation only and preserve that tested program and specification.

The actual CNCF full run, `P66-FINAL-CNCF-VAL-001`, had 4,240 succeeded /
1 failed. The audit-viewer Job-list failure is recorded in the
[CNCF handoff](phase-66-cncf-handoff.md#cncf-owned-connection-and-validation-work)
for CNCF follow-up. CNCF full-suite success and runtime connection are outside
the user-selected Cozy closure boundary.

The [Hygiene journal](../journal/2026/10/2026-10-02-phase-66-hygiene-follow-up.md)
preserves two accepted nonblocking records exactly. There are no accepted
Development Candidates or unpersisted review records. This producer closure
is finalized by the distinct local Cozy Phase release commit.

## Non-goals

Deadline、general Timer / Wait、Cancellation、FailurePolicy、general idempotency contract、backoff/jitter、dedicated Iteration semantics。

## Acceptance

real CML fixture から Retry / Timeout metadata が deterministic に生成され、CNCF が CML 再解析なしで受け取れる JSON / Scala / bootstrap と契約・再生成手順を提供する。既存 Workflow は metadata 未指定時に従来 semantics を維持する。Cozy の検証と生成物 handoff で完了とし、CNCF の繋ぎ込み・実行処理・CNCF 側の残る検証は CNCF 側へ引き継ぐ。
