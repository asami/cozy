# Phase66 Hygiene Follow-up

Recorded: 2026-10-02
Owner: Cozy
Source: verified PHASE-66 review disposition ledger
Update rule: retain exact accepted record bytes; follow-up work requires its own bounded task.

HYG-P66-FULL-E1-001: OPEN; discovered by P66-FULL-REVIEW-E1-001 on 2026-10-02. Cozy docs/design/workflow-retry-timeout-source-lowering.md line 3 still states source-contract acceptance pending, although S66.1 is COMMITTED and its canonical source contract records accepted source semantics. This is low-risk nonbehavioral status presentation debt; the design body agrees with the accepted source/producer/consumer responsibilities and does not weaken the contract or invalidate validation. In a separate documentation-maintenance task, update only this source-acceptance status projection to the accepted S66.1 evidence while retaining full Phase/release pending status until actual closure. No implementation, specification meaning, prior acceptance or broad design rewrite is authorized.

HYG-P66-S661-REVIEW-001: WorkflowRuntimeControlCmlSpec.scala contains 101 expanded behavior examples in 510 lines under one should block without which grouping. All scenarios retain meaningful adjacent Given/When/Then and observable should expectations; the frozen source contract is covered and reviewable, so this is nonblocking specification organization/size debt. Retain the cohesive policy-contract spec for S66.1; a separate Cozy specification-maintenance task should record a retention/split disposition and introduce observable target-resolution, numeric-value, placement, and eligibility groups or a behavior-preserving split with a shared fixture plan. Broad fixture/spec restructuring is outside the current Step and is not authorized by this review.
