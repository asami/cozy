# Phase 40.1: Article and Summary Slide PDF Registration and Driver Acceptance

Status: COMPLETE

Plan date: 2026-08-29

## Split From Phase 40

Phase 40.1 was created on 2026-08-29 by the user-approved
`$cncf-split-phase Phase 40`. It owns the original Phase 40 registration scope
and `PDF40-05` exactly once. Phase 40 retains PDF role binding, deterministic
PDF generation, and receipt/review currentness. Phase 41 starts only after
this Phase closes, unless the active order is changed by explicit
authorization.

Phase 40.1 consumes the frozen Phase 40 handoff: accepted `article_pdf` and
`summary_slides_pdf` resource roles, exact locale/public-path identities,
current PDF media receipts, and stale-input rejection semantics. It must not
redefine the PDF renderer, resource contract, or receipt meaning.

## Goal

Register the accepted localized article and summary-slide PDFs through the
normal and WIP SmartDox site routes atomically, record the selected
SimpleModeling.org driver, and hand off its operational acceptance to the
post-Phase-42 Article 8 authoring loop without deployment, upload, publication,
or series backfill.

## Ownership and Execution Boundary

- Cozy owns the normal `cozy media register-site` and WIP
  `cozy media register-site-wip` registration behavior, registry atomicity,
  exact-locale selection, and preservation of existing infographic/video
  records.
- The selected clean static driver is
  `/Users/asami/src/dev2025/simplemodeling-org/src/main/media/development-process/knowledge-modeling/media.yaml`.
  Its existing article and two infographic sources were inspected; it has no
  article/summary PDF resources or summary-slide authority. It remains
  unmodified in this Phase. Existing untracked `literate-modeling` work is not
  used.
- The user has authorized that root for a later, minimal driver-acceptance
  update, but the requested Article 8 operating loop starts only after Phase
  42. No external source/configuration, metadata/link, registry, generated
  output, or website file is changed here.
- SmartDox Phase 9 remains the accepted PDF role contract. This Phase adds no
  SmartDox schema/projection work and no standalone Cozy PDF receipt contract.

## Stages

### PDF40.1-01: Normal and WIP PDF Site Registration

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 40.1
- Update rule: Update when the PDF40.1-01 checklist state changes.

- Extend the normal `cozy media register-site` and distinct WIP
  `cozy media register-site-wip` routes to emit only the accepted SmartDox
  Phase 9 document-reference fields from current Phase 40 receipts.
- Preserve existing infographic/video registration, exact-locale behavior,
  atomicity, and unrelated registry entries.
- Reject absent, stale, cross-locale, or role-incompatible PDF inputs before
  mutation and leave neither route partially registered on failure.
- Evidence: local Step acceptance commit
  `c41e5c7942ad4f8d12bbe8b6114cc5f7d86aefb3` implements the normal and WIP
  PDF role registration. Serialized focused validation receipt
  `cozy-p401-sbt-011` / invocation `99893-20260830T021021Z` ran the eight
  P401 suites: 174 tests passed, 0 failures.

### PDF40.1-02: SimpleModeling.org Driver Acceptance and Closure

Stage Status:

- Current status: CLOSED
- Owner: Cozy / SimpleModeling.org acceptance boundary
- Update rule: Update when the PDF40.1-02 checklist state changes.

- The selected clean static driver is recorded for a future handoff, but its
  missing PDF and summary-slide authorities mean that WIP/production
  article/Notice metadata and localized PDF links are not fabricated or
  accepted in this Phase.
- No SimpleModeling.org source/configuration, article/Notice metadata/link,
  registry record, generated output, or website file was changed in this
  Phase.
- **(Future Development Candidate) `P401-DC-001`** defers the external driver
  acceptance to the post-Phase-42 Article 8 authoring loop; see the dedicated
  handoff journal. This closes the Phase's external-driver stage without
  claiming operational acceptance.
- The preflight Dox runtime observation was read-only and non-diagnostic:
  `/Users/asami/Dropbox/share/bin.air-sonoma/dox --help` stopped at
  `NoClassDefFoundError: org/goldenport/cli/Response`; the user-dirty
  SmartDox `build.sbt` and pre-existing `target/classpath.txt` were not
  changed or interpreted as a Cozy defect.

## Exclusions

- PDF resource-role design, article/summary-slide PDF generation, renderer
  selection, and receipt/currentness semantics; Phase 40 owns and freezes
  those first.
- SmartDox schema or projection implementation, Markdown-image admission, and
  any standalone Cozy PDF-receipt contract (`DEV-012`).
- Article prose changes or summary-slide editorial authoring outside the
  selected driver package.
- Public PPTX registration or distribution; video regeneration; YouTube
  changes; site deployment; upload; push; and series backfill.

## Completion Criteria

Phase 40.1 is COMPLETE: the normal/WIP registration contract, explicit
`P401-DC-001` handoff, current full Cozy validation, independent Phase review,
and this local Phase release commit form its closure. The selected external
driver has not been operationally accepted, and no external metadata/link,
registry, generated output, or website change is claimed. External Article 8
acceptance is deferred until after Phase 42. No deployment, upload, push,
publication, or series backfill is claimed.

## Structural Phase Plan Gate

Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4–8h band
- planning_demand: bounded-settled
- recommended_parent_profile: `gpt-5.6-terra / high`
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none; Phase 40 freezes the PDF role,
  currentness, and renderer/receipt boundary.
- frozen_profile_transition_handoff: accepted Phase 40 PDF roles, locale and
  public-path identities, current media receipts, and stale-input rejection
  semantics.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5.0–6.5 hours; within the preferred 4–8h
  band.
- merge_attempts_for_every_sub_4h_child: none; this child is not sub-four-hour.
- adjacent_merge_structural_rejection_evidence: none; this child is not
  sub-four-hour.
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: the added handoff, validation, review, and commit are
  offset by isolating mutable registry/atomicity and external-driver acceptance
  from Phase 40's protected PDF contract and rendering closure.
- agent_reasoning_mode_policy: standard
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 40

## Dependencies and Successor

- Predecessor: Phase 40 must close with its frozen PDF resource and receipt
  handoff before this Phase starts.
- Successor: Phase 41 starts only after Phase 40.1 closes, unless explicitly
  authorized otherwise.

## References

- `docs/phase/phase-40.md`
- `docs/phase/phase-40-checklist.md`
- `docs/phase/phase-40.1-checklist.md`
- `docs/phase/phase-41.md`
- `docs/spec/media-package.md`
- `docs/design/article-media-publication.md`
- `docs/spec/article-media-publication.md`
- `docs/design/smartdox-site-media-registration.md`
- `docs/spec/smartdox-site-media-registration.md`
