# Phase 45: Document Project v2 Authoring Contract and Content Core Acceptance

Status: COMPLETE

Plan date: 2026-09-02

Split record date: 2026-09-02

Development items: DEV-013, DEV-016

Predecessor: Phase 44 closure. Semantic dependencies: accepted Phases 41, 42,
and 42.1.

## Goal

Freeze and implement the Document Project v2 authoring contract and the
explicit Content Core candidate-to-human-acceptance loop. This Phase replaces
the unoperated v1 authoring kernel with one closed v2 descriptor vocabulary
that later review, dashboard, localized-artifact, and Article 8 driver work
consumes. `cozy.document-project.v1` remains a distinct historical identity;
this Phase retains no v1 reader, migration path, or compatibility mode.

## P45-01: Versioned Authoring Contract

Stage Status:

- Current status: COMPLETE
- Owner: Document Project public v2 authoring contract
- Update rule: complete only when each admitted authored-state concept has a
  closed versioned descriptor representation and an Executable Specification
  target before implementation.

- Reconcile the promoted requirements against the accepted Phase 42/42.1
  kernel and retain their requirement traceability.
- Freeze the first-class Work Product for `article-review-html`, separate
  from its later article-review projection implementation in Phase 45.1. The
  pre-existing read-only dashboard may accurately show its selected
  contract-only status as `No action: contract-only in Phase 45`; it does not
  render review HTML, add a CLI/output, or add an action capability.
- Freeze the authored-state vocabulary for optional-deliverable
  activation/selection and secondary diagnostic projections. Phase 45.1 owns
  the expanded user dashboard/action surface; this Phase changes no dashboard
  behavior beyond the contract-only status above.
- Freeze the authored hooks needed by later Japanese/English parity
  and per-artifact alignment work without implementing that synchronization in
  this Phase.
- Replace the unoperated v1 descriptor with the explicit closed
  `cozy.document-project.v2` authored descriptor. It owns optional Work Product
  selection plus stable semantic/locale and per-artifact-alignment reference
  hooks, but it does not implement review UI or synchronization in this Phase.

Closure evidence: `P45-01A-VAL-008` passed 49
`CozyDocumentProjectSpec` tests with the SBT lock released. Focused closure
review 3 closed `CB-P45-RR-001` with no actionable findings. The local Step
acceptance commit binds this completed Stage; P45-02 then remained the sole
protected closure unit.

## P45-02: Content Core Candidate, Feedback, and Acceptance Loop

Stage Status:

- Current status: COMPLETE
- Owner: AI-assisted Content Core authoring and review workflow
- Update rule: complete only when a candidate can be produced, reviewed,
  revised, and explicitly accepted with complete provenance and no implicit
  acceptance.

- Implement the path from source/idea and AI dialogue to a visible Content
  Core candidate, reviewer feedback, revised candidate, and accepted Content
  Core.
- Keep candidate, rejected, superseded, and accepted revisions distinct in the
  authored/evidence model; later review projection is Phase 45.1 work.
- Record provider, model, request, response, source, reviewer decision, and
  resulting revision identities as evidence.
- Make `run` execute only an explicitly selected registered operation, or add
  an equally explicit execution command; recording an attempt alone is not an
  execution result.
- Preserve append-only failed, successful, rejected, accepted, and superseded
  attempt/review history, and require human acceptance for Content Core
  authority changes.

Closure evidence: exceptional Terra xhigh full recovery review
`P45-RECOVERY-FULL-REVIEW-001` identified and then bounded the public
writer-lock precedence defect as `CPB-P45-003`. The authorized source/spec
repair established external-input admission followed by writer-lock acquisition
before descriptor/Core observation; `P45-02D-VAL-010` passed 59 focused
Document Project specifications, and focused re-review sealed
`CPB-P45-001` through `CB-P45-003`. Final serialized Cozy validation
`P45-FINAL-VAL-011` / `67794-20260902T164202Z` passed 1,695 tests in 126
suites with 0 failures; SBT and wrapper exits were 0 and the lock was released.
This Stage does not perform an Article 8 driver acceptance, publication, or
external-consumer acceptance.

### Decision Resolution Record: P45-02-REVIEW-GATE-001

- Decision ID: `P45-02-REVIEW-GATE-001`
- Attributable answer: user instruction on 2026-09-02: `P45-02 を Phase 45 の
  唯一の protected closure unit とし、軽量 Step review を省略して Terra xhigh
  の Phase full review と local closure commit に進めることを許可する`.
- Selected option: treat P45-02 as the sole protected closure unit of Phase 45;
  omit the lightweight Step review and use one Terra xhigh Phase full review
  before the local closure commit.
- Affected identity: Phase 45 / P45-02; Phase base
  `a97492f8ad9a8e028cf6108745d2b666d0a66fb5`; P45-02 candidate, feedback,
  acceptance, project-local writer-lock, and standard-video generic-compose
  boundaries.
- Authorized next state: `REVIEW` (`phase-full`, Terra xhigh).
- Consumed: `true`.

## Moved Scope and Handoffs

Phase 45 retains the Phase identity because no Stage was complete at split
time. The following unfinished Stages have exactly one successor owner:

| Former Stage | Owner after split | Exact handoff |
| --- | --- | --- |
| P45-03, P45-04 | [Phase 45.1](phase-45.1.md) | Versioned Work Product roles, candidate/revision identity, acceptance state, and explicit optional-selection contract from P45-01/P45-02. |
| P45-05, P45-06 | [Phase 45.2](phase-45.2.md) | Phase 45 authoring/acceptance contract and Phase 45.1 review/dashboard behavior. |

The three bounded Phases estimate 6–8, 5–7, and 6–8 hours respectively. The
17–23 hour sequence includes an expected 2–3 hours of context restoration,
handoff validation, and one-order dependency overhead. Any adjacent merge
would exceed the preferred 4–8 hour Phase range; none is short enough to need
a short-Phase exception.

## Exclusions

- Implementing review HTML, a new dashboard, or optional-action UI; those
  belong to Phase 45.1. Phase 45 only permits the pre-existing read-only
  dashboard to show the selected `article-review-html` contract-only status;
  it adds neither an action nor a renderer/CLI/output.
- Localized artifact alignment, shared-media currentness, SimpleModeling.org
  mutation, and Article 8 acceptance; those belong to Phase 45.2.
- Retrospective Article 7-or-earlier migration, autonomous semantic acceptance,
  a general scheduler, daemon, arbitrary command descriptor, remote workflow
  service, or any v1 compatibility/migration behavior.
- Publishing, deploying, uploading, pushing, or external-service mutation.

## Completion Criteria

Phase 45 completes only when the closed v2 authoring contract replaces the
unoperated v1 kernel; Content Core candidate, feedback, revision, and explicit
human acceptance form a provenance-backed append-only loop; the selected
provider operation is explicit; required Executable Specifications pass; and
all open checklist items below are complete. Its completion makes no Article
8, public-output, or external-consumer acceptance claim.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: protected decision over a replacement public-contract and
  approval boundary
- parent profile: `gpt-5.6-terra` / `xhigh`
- estimate: 6–8 hours
- expensive reasoning kernel: replace the unoperated v1 kernel with one closed
  v2 authoring/approval contract that successor Phases can consume without
  reinterpretation
- predecessor: Phase 44 closure
- successor: Phase 45.1 only after this contract and Content Core acceptance
  loop close

## Pre-split Gate Evidence

The original Phase 45 plan estimated 14–20 hours across P45-01 through
P45-06. Its earlier `SPLIT_REQUIRED` result is retained as historical planning
evidence only. The user approved the resulting ordered split with
`$cncf-split-phase Phase 45` on 2026-09-02.

## References

- `docs/phase/phase-45-checklist.md`
- `docs/phase/phase-45.1.md`
- `docs/phase/phase-45.2.md`
- `docs/journal/2026/09/2026-09-02-phase-45-document-project-requirement-reconciliation.md`
- `docs/journal/2026/09/2026-09-02-phase-45-article-review-requirements.md`
- `docs/journal/2026/09/2026-09-02-phase-45-hygiene-follow-up.md`
- `docs/journal/2026/08/2026-08-30-document-project-content-core-direction.md`
