# Phase 60.1: Goal-Driven DSL Dependency Resolution and Generation

split_full_test_policy=final-only
validation_ownership=aggregate-final-owner
aggregate_validation_owner=PHASE-60.1
aggregate_validation_sequence=["PHASE-60","PHASE-60.1"]
depends_on=phase-60.md
status=closed

Status: CLOSED
Plan recorded: 2026-09-14
Closed: 2026-09-15
Development item: DEV-028 (split child)
Split from Phase 60: 2026-09-14
Predecessor: Phase 60

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: Phase 59 documented 7–8 h and observed 318 min in the
  same repository and Document Project validation/review/release setting;
  Phase 60.1 adds a separate skill-led dependency workflow, so no
  shorter-duration multiplier is applied.
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: time-bound partition; no reasoning-cost isolation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: none
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6.5–7.5 h including focused validation,
  independent review, the aggregate repository-full SBT suite, release
  bookkeeping and commit; within the 8h ceiling.
- incoming_semantic_handoffs: [{ from_child: PHASE-60, kind: release, input:
  accepted P600-01 through P600-05 local target/CLI/validation contracts,
  action: close and commit Phase 60 under aggregate-deferred policy, output:
  committed local-build handoff, owner: PHASE-60, invalidation_reason: P600-07
  can invoke only admitted local operations and targets }]
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: merging with Phase 60 restores
  the 860-minute unsplit estimate and exceeds the 480-minute ceiling.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: one additional Phase review and release commit; this child
  owns the sequence's single repository-full SBT suite.
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch with frozen quality-first evidence.
- runtime_suitability: re-evaluate in the Phase execution task.
- source: applied split from Phase 60 on 2026-09-14.

## Purpose and boundary

Establish a goal-driven Codex capability set: start with a requested product,
recursively resolve its grounding DSL dependencies back to Core, create or
update the needed DSLs, and render the selected product in dependency order.
Document remains the complete prose authority within this graph; it is not
replaced by a product-specific independent draft. Authoring is a Codex action,
distinct from Cozy rendering. Valid existing inputs are reused.

An orchestration skill owns request and dependency planning, invokes Codex for
authored DSLs/documents at required intermediate nodes, and calls admitted Cozy
operations for validation, confirmation and rendering. Cozy owns the selected
operation and its declared local build dependencies, not the creative authoring
workflow. Supported operations are independently callable; unavailable
functions remain explicit capability gaps.

Phase 60.1 consumes the committed Phase 60 local-build handoff. It does not
reopen the Phase 60 three-view, SmartDox target, CLI, or timestamp contracts.
P600-07 consumes a separately committed single-page root Step in the external
skill-source root `/Users/asami/Dropbox/share/src/2026/codex-customizations`,
authorized explicitly by the user on 2026-09-15. That root Step is limited to
the maintained `cozy-document-project-generation` and
`cozy-document-project-article` skill sources and is recorded at
`4dfcd809365cb30a0ab9078d46604f567c565b49`. It does not enlarge the Phase
mutation repository set or its final release commit.

## Planning inputs and completion ledger

- [Phase 60 local-build handoff](phase-60.md)
- [Proposal](../notes/document-project-confirmation-views-and-make-dependencies-proposal.md)
- [Decision journal](../journal/2026/09/2026-09-14-confirmation-views-and-make-dependencies-decision.md)
- [Prose authority](../journal/2026/09/2026-09-14-document-description-prose-authority-decision.md)
- [Checklist](phase-60.1-checklist.md): sole completion ledger
- [Earlier Phase 60 decision](../journal/2026/09/2026-09-14-smartdox-article-and-core-confirmation-decision.md): planning history

The proposal and journals are non-normative planning inputs. P600-06 admits
the reverse-resolution and forward-generation contract before P600-07 changes
an orchestration or producer skill.

## Subphase 60B: Goal-driven DSL dependency resolution and generation

| Step | Observable outcome | Status | Closure basis |
| --- | --- | --- | --- |
| P600-06 | Frozen skill/Codex/Cozy responsibilities, goal-to-Core resolution and authored-node/tool invocation contracts | DONE | Checklist P600-06 |
| P600-07 | A maintained orchestration skill and producer skills implement shared graph planning and interleaved Codex authoring/Cozy operations | DONE | Checklist P600-07 |
| P600-08 | Isolated end-to-end skill execution with actual intermediate document authoring, reuse/update acceptance and independent Phase closure | DONE | Checklist P600-08 |

## Acceptance and exclusions

Article editing defaults to updating the manuscript and its authoritative DSL
sources, with necessary source validation only. It does not implicitly render
confirmation HTML, open a browser, regenerate media or notify a preview server.
The existing article skill adopts this source-only boundary; this Phase delivers
selected-product generation after P600-06 contract admission.

An explicit generation request identifies the product(s) currently being
worked on. The orchestration skill resolves selection back to grounding
DSLs/Core, then authors or reuses prerequisites and invokes implemented Cozy
operations in dependency order. It does not generate every project product
after a source edit. Generation and browser display are separate requested
actions; preview reload may follow successful selected generation only inside
an authorized preview session.

Acceptance proves product-request-to-Core reverse resolution, dependency-ordered
DSL authoring and selected-product rendering. Existing valid DSLs are reused;
shared dependencies are resolved and generated once. It includes actual Codex
creation/update of intermediate source documents and verified Cozy calls; a
plan, prepopulated fixtures or renderer-only tests cannot close this Phase.
Document remains prose authority, Summary owns concise selection, and Core owns
logical meaning. Generation does not approve prose.

Core annotations in `index.dox`, new hash/receipt management, new PDF/slides/
video renderers, whole-site/Antora builds, publication, upload, deployment and
external driver-project edits are excluded. Unadmitted skill roots are also
excluded until a later Phase entry records them explicitly. Phase 59/61 remain
independent capability dependencies where applicable.

## Aggregate final validation

Phase 60.1 is the `aggregate-final-owner` for the serial
`["PHASE-60","PHASE-60.1"]` sequence. Phase 60 closes with its focused
evidence, independent review and release commit while recording
`repository_full_suite=deferred-not-run`. This Phase verifies the committed
predecessor handoff, then runs the one repository-full SBT suite on its frozen
release tree in addition to its own focused validation and review.

## Closure and successor handoff

P600-06 through P600-08 are complete. Phase 60 is closed and its predecessor
aggregate-deferred handoff is verified. P600-07A delivered the resolver/producer
skill integration in the explicitly admitted skill-source root, with the two
review corrections closed by focused re-review. P600-08 exercised actual
isolated product requests, Codex authoring and admitted Cozy operations.

The independent Phase full review found two same-boundary final-gate repairs;
Phase Repair Cycle 1 corrected their portable temporary-directory identity and
stale prebuilt-output receipt behavior. Its focused re-review is clean. This
aggregate final-owner release binds the serial sequence's one repository-full
SBT suite and the distinct Phase release commit. It does not begin Phase 61,
publication, deployment or external-project work.
