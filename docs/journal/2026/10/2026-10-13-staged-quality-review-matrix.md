# Model-derived scope for staged quality review

Date: 2026-10-13
Status: design direction
Related: CML, CAR generation, cbd-support Review, sm-workflow, CNCF Admission

## Decision

Cozy participates in staged quality review by supplying model-derived knowledge about what a generated/developed component means and what scope is relevant. It does not own development workflow progression or semantic quality judgment.

## Shared review matrix

| Concern | Step Close | Phase Review | Phase Final |
|---|---|---|---|
| CAR lint: CNCF/Textus rules | changed scope, primary | whole Phase, primary | unresolved/blocking confirmation |
| CAR lint: model/ABI consistency | changed scope, primary | integrated state, primary | freshness/blocker confirmation |
| CAR lint: prohibited/risky patterns | primary | primary | unresolved confirmation |
| Review: Step requirements | primary | supporting evidence | normally no new search |
| Review: Usage Contract | affected Slice | whole Phase, primary | blocking completion |
| Review: implementation quality | changed scope, primary | whole-system perspective | repair delta when needed |
| Review: excessive checking/assurance | changed scope, primary | cross-Step/system-wide, primary | repair delta when needed |
| Review: unnecessary complexity/abstraction | changed scope, primary | cross-Step/system-wide, primary | repair delta when needed |
| Review: duplicate implementation/state | relevant scope | primary | only unresolved/delta |
| Review: component integration | relevant connected scope | primary | blocking completion |
| Review: architecture fitness | relevant scope | primary | only affected delta |
| Review: cross-Step assumption conflicts | normally out of scope | primary | unresolved confirmation |
| Acceptance Boundary compliance | primary | primary | primary |
| Finding resolution | after repair | primary | primary |
| Evidence coverage/freshness | primary | primary | primary |
| New issue discovery | primary within Step | primary across Phase | normally not exploratory |
| Release blockers absent | not a Step concern | identify/classify | primary |

Interpretation:

- **Step Close** asks: is the delivered Step/Slice locally sound and admissible?
- **Phase Review** asks: is the integrated result sound as a whole? This is the broadest semantic quality review.
- **Phase Final** asks: is closure/release justified by fresh, sufficient evidence? It should not restart an unconstrained exploratory review.

CAR lint and cbd-support Review are therefore not three unrelated implementations and should not blindly rerun an identical full scan. The lifecycle context selects scope, checks, evidence reuse, and blocking policy. Phase Final preferentially reuses admitted evidence and examines repair deltas/unresolved blockers.

## Cozy responsibility

CML knows structural and usage concepts that source-only inspection may recover poorly or expensively. Cozy should make appropriate metadata/relationships available so review can identify affected and integrated scope, including where applicable:

- Component / Subcomponent;
- Service / Operation;
- Usage Contract-facing operations and externally observable behavior;
- Entity/DataStore and related logical model elements;
- Display Model/UI-facing model elements;
- Capability and annotations;
- generated-artifact provenance/relationships needed to connect model and implementation.

At Step Close this knowledge helps identify the changed Slice and connected model elements. At Phase Review it helps cbd-support reason across Operations/Services/components and against the adopted Usage Contract. At Phase Final it helps establish coverage/freshness and the affected scope of post-review repairs without demanding another unconstrained full semantic scan.

Cozy should not introduce a parallel review state machine. Nor should it hard-code sm-workflow's lifecycle into generic generated runtime APIs where normal metadata and requirement/profile selection suffice.

## Cross-project contract

- CNCF: generic Requirement/Evidence/Admission semantics.
- Cozy: model-derived review knowledge/scope.
- cbd-support: deterministic CAR lint and semantic quality evaluation.
- sm-workflow: Step/Phase/Final lifecycle orchestration and evidence consumption.

Future implementation should prefer extending existing CML/CAR metadata paths over a second exhaustive traceability database.

## Development-plan adoption — 2026-10-13

The owner adopted sm-workflow Phase 6 as the concrete consumer/driver. Required
producer/provider capabilities are implemented and accepted in their own projects:
CNCF Phase 106, Cozy Phase 81 and textus-cbd-support Phase 15. The connected
sequence is minimum shared contract -> real Step admission/commit -> integrated
Phase Review -> affected repair/re-review -> Final evidence reuse and closure.
Each project owns its implementation/checklist; do not wait for unrelated roadmap
items or import an entire provider backlog into Phase 6. Phase 9 extends this
working route with convergence/provider switching rather than delivering it first.

This project's [Phase 81 plan](../../../phase/phase-81.md)
records the bounded delivery. The shared driver/acceptance scenarios are in
[sm-workflow Phase 6](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-6.md).
Freshness means applicability to the actual current subject and requirements;
no TTL, hash/copy-based unchanged-content proof or automatic full review at every
boundary is introduced. Plans are PLANNED, not implementation or acceptance
claims. Existing active work and its Goal/base/epoch/history/counters are retained.
