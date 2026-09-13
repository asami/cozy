# Phase 59 module connection and ordinary output policy

Date: 2026-09-14
Authority: direct user handoff, “Phase 59のモジュール接続と出力先チェックの見直し”.
Scope: Cozy, P590-02A and the P590-03A in-process connection.

## Adopted direction

Separating input/output arguments alone is insufficient. Coordinator X owns
reading the selected media resource's `source` and passes one connection path
to generator A as output and PDF consumer B as input. A does not independently
check agreement with B's input configuration.

The exhaustive input/output collision, ancestor overlap, filesystem identity,
hard-link, case-alias, and destination-symlink protection added for A is
withdrawn, not relocated to X. A retains ordinary output-argument and write-error
handling. A materially incorrect media configuration has no guarantee against
overwriting another file. Strict input admission and DSL semantic validation
remain; the pre-existing Phase 40 renderer/verification/receipt/currentness
contracts are unchanged.

The implementation boundary is `CozySummarySlideProjection` (A), the new
`CozySummarySlidePdf` coordinator (X), and the existing `CozyMedia` /
`CozyMediaSummarySlidesPdf` flow (B). No new renderer or receipt scheme is
authorized. Connection source grammar retains the root-level reference base
required by this projection profile; it is not an output-protection policy.

## Historical evidence

This direct instruction supersedes the preceding
[output ownership decision](2026-09-14-phase-59-projection-output-ownership-decision.md)
and its corresponding acceptance requirements. Its validation attempts and
[Step review](2026-09-14-phase-59-projection-output-ownership-review.md)
remain historical evidence for that earlier candidate, not validation of the
new direction. They are not deleted or reinterpreted.

Implementation and validation results are recorded separately. This decision
does not claim a Step commit, final full-Phase review, full test, release, or
Phase closure.
