# Phase 69: Goal-Oriented Workflow Iteration Semantics

Status: planned

## Goal

技術的 Retry と意味的 Iteration を分離し、AI / Human participant を含む Goal-oriented loop を必要最小限の宣言意味論として表現する。

## Scope

- NeedsInput / NeedsRevision / Rejected 等の semantic outcome
- iteration termination / escalation metadata の必要性評価
- StateMachine transition との整合
- generated ABI projection

専用 Iteration abstraction は実運用 evidence により必要性が確認された場合にのみ導入する。
