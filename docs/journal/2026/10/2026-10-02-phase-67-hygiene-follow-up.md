# Phase 67: Retained Hygiene Follow-ups

Current status: nonblocking accepted follow-ups retained for separately scoped maintenance after Phase 67; no maintenance implementation in S67.3.
Owner: Cozy modeler and executable-specification maintenance owners.
Update rule: preserve the accepted records below; add resolution evidence only through separately scoped maintenance and its acceptance.
Updated: 2026-10-02
Phase: [67](../../../phase/phase-67.md)

<!-- cncf-phase-finding:HYG-P67-S671-001:begin -->
HYG-P67-S671-001 — CompositeStateMachineCml parser size. src/main/scala/cozy/modeler/CompositeStateMachineCml.scala is 1,097 lines, compared with 1,010 at the original Phase67 base, exceeding RULE.md's 1,000-line ordinary-source debt threshold. It combines Workflow ownership/API/SPI normalization, CSM constituent/derivation/action validation, and shared field/reference extraction. The S67.1 additions remain cohesive source-admission guards, and independent review found no resulting correctness or reviewability failure. Disposition: nonblocking Hygiene; evaluate a responsibility split in a separately frozen Cozy modeler-maintenance task after Phase67. Coordinate shared parser/helper boundaries and executable coverage before extraction; do not introduce an arbitrary partial extraction or expand S67.1 acceptance scope. Risk: increasing navigation and future review cost.
<!-- cncf-phase-finding:HYG-P67-S671-001:end -->

<!-- cncf-phase-finding:HYG-P67-S671-002:begin -->
HYG-P67-S671-002 — Lifecycle executable-specification organization. src/test/scala/cozy/modeler/WorkflowLifecycleCmlSpec.scala is 390 lines but expands its tables into 156 executed behavior cases, exceeding RULE.md's approximately 100-example specification-size guidance. Its immutable-declaration/compatibility, closed-grammar, and ownership groups remain readable; every authored scenario has adjacent semantic Given/When/Then and meaningful parsing/lowering assertions. Disposition: nonblocking Hygiene; consider organizing these behavior surfaces into separately named specifications in a dedicated Cozy specification-maintenance task after Phase67, with coordinated fixture/helper organization and preservation of all invalid-input and compatibility coverage. Do not reduce coverage or cosmetically hide example counts. Risk: increasingly lengthy specification discovery and execution output.
<!-- cncf-phase-finding:HYG-P67-S671-002:end -->
