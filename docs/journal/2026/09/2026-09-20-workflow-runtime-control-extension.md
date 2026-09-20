# Workflow Runtime Control Extension

Date: 2026-09-20
Status: design direction

Workflow を実運用するための Retry、Timeout、Deadline、Timer / Wait、Cancellation、FailurePolicy、Idempotency、Iteration を Cozy / CNCF の拡張として整理した。

Cozy の責務は宣言意味論と generated ABI であり、runtime execution は CNCF が担当する。sm-workflow は consumer であり、必要な機能以外の runtime roadmap を意識しない。

最優先 consumer である sm-workflow の早期運用を妨げないため、最初の producer extension は Retry と Timeout に限定する。Retry は maximum attempts と fixed delay、Timeout は Action / Participant invocation の execution timeout を最小 contract とする。

後続は scheduling/lifecycle、failure/execution safety、Goal-oriented iteration に分割する。Retry は技術的再実行、Iteration は Goal 達成のための意味的反復として混同しない。

既存 Phase 62 は closed しているため改変せず、新規 Phase 群で ABI を additive に拡張する。
