# Phase 67: Workflow Scheduling and Lifecycle ABI

Status: active

Current status: S67.1 source and S67.2 producer accepted; S67.3 independent review passed under P67-S673-REVIEW-001, and the local Step commit containing this record accepts its handoff. Phase 67 remains active pending comprehensive Phase review, final Cozy full validation and release commit.
Owner: Cozy; CNCF Phase 82 owns receiver integration and runtime behavior.
Update rule: mark each Step accepted only after focused validation, independent review and its local commit; mark released only after Phase review, final Cozy validation and release commit.

## Goal

Workflow の長時間運用に必要な Deadline、Timer / Wait、Cancellation と durable continuation に必要な宣言 metadata を追加する。

## Scope

- Deadline
- general Timer / Wait
- Cancellation semantics
- runtime suspension/resumption 用 metadata
- deterministic ABI generation

公開 suspend/resume operation を CML source contract として必須にはしない。

## Producer closure boundary

Cozy owns strict CML normalization, immutable declaration IR, and deterministic JSON / Scala / bootstrap generation. CNCF Phase 82 owns v3 admission and scheduling, signal delivery, cooperative cancellation, persistence and runtime suspension / resumption. Runtime implementation and CNCF full-suite completion are not Cozy acceptance gates.

## Frozen source contract

An optional singular direct Workflow-definition `LIFECYCLE` section contains `deadline-millis` (unsigned decimal 1..Long.MaxValue, relative to Workflow start) and/or `cancellation = COOPERATIVE`. Values other than the exact cancellation token are rejected. No nested children, empty section, duplicate aliases, unknown fields or implicit defaults are admitted.

An optional singular direct Workflow-definition `WAIT` section has no fields and contains one or more ordered named entries. Each entry has `state` and exactly one of `delay-millis` (unsigned decimal 0..Long.MaxValue, relative to each entry into that State) or `signal` (trimmed nonempty logical signal identity). State aliases resolve using the existing case/separator-insensitive key to exactly one CSM State. Both declared spelling and canonical State identity are retained. Entry identities and canonical target States must be unique under that key. Each entry and lifecycle section retains its source line. Entry fields are closed; nesting, ambiguous/missing targets and misplaced lifecycle sections/fields are rejected.

The normalized IR adds optional `lifecyclePolicy` and ordered `waits` with trailing constructor defaults. Timer and Signal are closed trigger variants. Signal names are not resolved against an invented external registry. Lifecycle section keys are recognized at structural positions; existing opaque Workflow, CSM, State, Action and other model identities named `Wait` or `Lifecycle` remain valid. Static declaration identities and State/source/version correlations supply the producer portion of durable wait metadata; run / continuation IDs, due timestamps, revisions and stored history remain runtime-owned.

## Frozen ABI contract

Any lifecycle policy or wait declaration selects collection-wide `cozy.cml.statemachine-workflow-abi.v3` and bootstrap v3. Every v3 member retains invocation policies and explicitly carries `lifecyclePolicy` (object or null) and `waits` (array). Lifecycle-free input preserves exact v1/v2 JSON and Scala products; v2 is selected only for existing invocation policies, and empty collections remain v1. Both public `modeler-scala` and `modeler-scala-value` routes must produce the same ABI. Generated Scala 3.3.8 must compile.

Lifecycle JSON: `{deadlineMillis: number|null, cancellation: "COOPERATIVE"|null, source: {line: number|null}}`. Wait JSON: `{identity, state, stateIdentity, trigger, source}` where trigger is `{kind:"TIMER",delayMillis:number}` or `{kind:"SIGNAL",identity:string}`. Integers are lossless. Existing execution/context/continuation contracts are unchanged.

## Delivery and acceptance

- S67.1: source IR, strict normalization, executable source specifications and source/lowering contracts.
- S67.2: deterministic v3 producer, compatibility specifications and real Scala 3 compilation.
- S67.3: actionable [CNCF Phase 82 handoff](phase-67-cncf-handoff.md) and producer documentation; independent medium review P67-S673-REVIEW-001 passed, with acceptance recorded by this local Step commit and the [checklist](phase-67-checklist.md).
- Each Step receives independent acceptance review and a parent manual local commit. Protected source/ABI reviews use GPT-6.1 Sol high; documentation review uses medium.
- Exactly one comprehensive GPT-6.1 Sol high Phase review in PLAN epoch P67-PLAN-E1; focused Step validation and final Cozy full suite use serialized registered SBT.
- Final closure synchronizes this document, checklist, index, strategy and accepted finding journals. No new branch or push; no successor Phase.

Original Cozy base: `53a5c90f31fe5c7a1fa11ab7e21496fdf0ac4d84`. Only Cozy is a mutation root.

## Accepted Step evidence

S67.1 SOURCE: `P67-S671-VAL-001` passed 291 tests in four suites; independent protected focused review `P67-S671-REVIEW-001` passed with zero current blockers. Commit `10548ef5ad332327bf4f3f2105a2a8ba1bcbc61d` accepts the source IR, normalization, fixture and paired source specifications. Nonblocking `HYG-P67-S671-001` and `HYG-P67-S671-002` are retained in the [Phase Hygiene journal](../journal/2026/10/2026-10-02-phase-67-hygiene-follow-up.md).

S67.2 PRODUCER: `P67-S672-VAL-007` passed 195 tests in five suites, including both public routes and exact v1/v2 product compatibility. `P67-S672-VAL-009` compiled ten actual generated / typed-consumer Scala sources with Scala 3.3.8 and its matching standard library into separate Cozy target outputs. Independent protected focused review `P67-S672-REVIEW-001` passed with zero current blockers and no new Hygiene or Development Candidate. Local Step commit `43af3f1cfe3fd37ae922cfe984b94c8fd6147452` accepted the deterministic lossless v3 producer, compatibility specifications and paired [ABI contract](../spec/workflow-scheduling-lifecycle-abi-contract.md) / [lowering design](../design/workflow-scheduling-lifecycle-abi-lowering.md). Recipient handoff acceptance is recorded below; full Phase acceptance remains pending.


S67.3 HANDOFF: independent medium review `P67-S673-REVIEW-001` passed with zero current blockers and no new Hygiene or Development Candidate. The local Step commit containing this record accepts the reproducible producer handoff and matching Phase/index/strategy projections. Static documentary checks passed; accepted source, generator, tests, fixtures and build inputs are unchanged. Comprehensive Phase review, final Cozy full suite and final local release remain pending.
