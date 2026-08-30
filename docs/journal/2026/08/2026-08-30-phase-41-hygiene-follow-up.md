# Phase 41 Hygiene Follow-up

## HYG-P41-001: Group Explanation Preview executable specifications by behavior area

- Status: OPEN
- Discovery: mandatory Phase 41 full review, 2026-08-30
- Repository: Cozy
- Location: `src/test/scala/cozy/media/CozyExplanationPreviewSpec.scala:24`
- Evidence: Five distinct Preview behavior scenarios are top-level examples without an enclosing behavior-area grouping.
- Category: executable-specification organization
- Risk/Priority: P3, nonblocking maintainability
- Phase boundary: This is not a Phase 41 behavior, currentness, output-safety, or validation-trust defect.
- Proposed follow-up: In a dedicated test-hygiene batch, group the existing scenarios by behavior area without removing coverage or weakening Given/When/Then structure.
- Resume condition: Start an explicitly scoped Cozy hygiene task.
- Prohibited local workaround: Do not alter Preview production behavior, receipt semantics, or scenario expectations merely to close this record.
- Source review: Phase 41 mandatory full review, `HYG-P41-001`.
