# docs/phase

Purpose: engineering work management.

Current phase state:

- Historical closed phase: `phase-34.md`: BoK Metadata Input Admission
  Hardening. CFB3 fixture correction, focused review, and candidate final full
  Cozy test receipt `22693-20260824T094231Z` (`test`, 1,376 succeeded, 0
  failed, 8 canceled, 100 suites, 0 aborted, SBT/wrapper 0, lock released)
  close Phase 34. No SmartDox/Textus consumer execution or acceptance is
  claimed.
- No Phase 35 successor work is started. Its superseded planning records remain
  only as unexecuted history. SmartDox Phase 8 `LITERAL8-03` remains separate
  for generated-site and Textus BoK consumer acceptance.
- Most recent closed phase: `phase-33.md`: Declared Cozy Runtime Selection for
  CAR Publication. It established CAR-owned `project.yaml build.cozyVersion`
  as the publication runtime authority, without a temporary `.cozy` override.
- Historical closed phase: `phase-29.md`: Project-Owned Site BoK Metadata
  Finalization. It introduced `cozy bok finalize-metadata` without site build,
  SmartDox, Antora, Arcadia, media, publication, or deployment action; runtime
  generated-site acceptance remains separately owned by SmartDox Phase 8
  `LITERAL8-03`, while its transferred correction remains in-progress and
  in progress in Phase 34 awaiting final validation.
- Earlier closed phase: `phase-31.md`: Video Encoding Policy Profiles.
  VP31-01 through VP31-03 are complete. Lightweight 1280x720, 18 fps, CRF 32
  is the encoding baseline.
- Earlier closed phase: `phase-32.md`: CML Source-Result Canonical Naming.
  It owns only the canonical `Resolved.projectRelativePath` migration and its
  explicit retired-name boundary.
- Most recent closed phase: `phase-30.md`: Unified Storyboard and
  Three-Gate Video Review Workflow. Under the explicit 2026-08-26
  one-Phase authorization, its internal Steps are P30-00 through P30-03. The
  intended workflow is
  `storyboard.md review -> optional image-backed visual-story review ->
  confirmation video review -> final video and rendered-video evidence review`.
  Content-review and video-review PPTX artifacts are optional inspection or
  distribution outputs, not workflow gates. The closure validates Cozy-owned
  artifacts and does not claim SmartDox/Textus or other consumer acceptance.
  Phase 36 is complete under its accepted closure status; Phase 37 remains
  IN PROGRESS. Its P37-01 design/specification Step is review-clean and
  closed; implementation and executable specifications remain P37-02 onward.
- Most recent closed phase: `phase-36.md`: Common Visual Page and Cross-Media
  Presentation Contract. VIS36-01 through VIS36-06 are DONE. Its closed
  Cross-media Review route is accepted in `245ec94dccc8abcf81cb810cd0630d91ebb15e05`;
  its representative article-summary fixture, separate image inspection, full
  review, duplicate-JSON repair, and focused closure re-review are recorded in
  the Phase 36 checklist. No push, publish, or downstream-consumer acceptance
  is claimed; Phase 37 is IN PROGRESS under its accepted documentation
  foundation.
- Current phase: `phase-37.md`: Logical Explanation Composition and Media
  Projection. The P37-01/P37-01B design/specification contract is review-clean
  and closed as a Step; it separates Subject Pattern from Narrative /
  Argument-oriented Explanation Pattern, defines the typed Logical
  Pattern/Relation plan boundary, and fixes independent multi-slide and
  multi-scene projection through Phase 36 Visual Pages. Implementation and
  executable specifications remain P37-02 onward. No full Phase validation or
  Phase review, publication, push, or downstream consumer acceptance is claimed.
- Planned successor: `phase-38.md`: Generated BoK Knowledge Boundary. It will
  separate public SmartDox source from generated RDF/graph/Manual/History/UI,
  align scaffold and diagnostics, and accept the reorganized
  `bok-knowledgehub` driver. Phase 38 is NOT STARTED and does not alter the
  active Phase 37 boundary.
- Earlier closed phase: `phase-28.2.md`: SimpleModeling.org Part 5
  Integration and Regression; `AM28-03` and `AM28-04` are complete.
- Most recent split predecessor: `phase-28.1.md`: WIP Local Article Media
  Registration; `AM28-02` is complete.
- Earlier closed phase: `phase-28.md`: Project Configuration and Profile
  Resolution; `AM28-00` and `AM28-01` are complete.
- Prior closed phase: `phase-27.md`: SmartDox Site Media Registration;
  `AM27-04`: Review, Validation, and Closure is complete.
- Prior closed phase: `phase-26.md`: Article Media Publication and BoK
  Integration. Its SmartDox dependency was satisfied by accepted closed Phase 1
  commit `fa21316973416c24bca7f8e366d65572c72720b7` and development coordinate
  `org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`; public/non-SNAPSHOT publication
  was not a start gate.
- Separately planned/blocked phase: `phase-24.md`: Component Skill Distribution
  (awaiting its own CNCF Skill Bundle contract).
- Earlier closed phase: `phase-25.md`: Entity Revision Generator Alignment
  (CBD Support runtime-selection acceptance is separately tracked as P8-61).

Belongs:

- current phase or stage status
- completion criteria
- checklist ledger
- remaining work
- handoff position

Not allowed:

- exploratory design notes
- speculative requirements
- historical diary-style records
- normative design or specification text

This directory is the work ledger layer. See
`ai/directive/core/document-lifecycle.md`. Phase 26 contract authority is
`docs/design/article-media-publication.md` and
`docs/spec/article-media-publication.md`. Phase 27 contract authority is
`docs/design/smartdox-site-media-registration.md` and
`docs/spec/smartdox-site-media-registration.md`.
Phase 28 normative design/spec authority is
`docs/design/simplemodeling-org-wip-article-media.md` and
`docs/spec/simplemodeling-org-wip-article-media.md`; this authority is frozen,
and AM28-00/AM28-01 implementation, review, and completion evidence are
complete. The approved split is recorded in `phase-28.md`, `phase-28.1.md`, and
`phase-28.2.md`; numbering preserves the original Phase identity and does not
consume later integer phases.

Phase 30 is one explicitly authorized Phase with internal Step/Slice ledger
`P30-00` through `P30-03`. Its normative Storyboard design/spec are recorded
in `docs/design/video-storyboard.md` and `docs/spec/video-storyboard.md`.
No child Phase 30.1, 30.2, or 30.3 is created or required. Its closure records
the completed Cozy-owned workflow only; it neither starts a successor Phase
nor claims external consumer acceptance.

Phase 36 is the closed common Visual Page successor to the representation and
identity principles established by Phase 30. Its canonical status authority is
`phase-36.md` plus `phase-36-checklist.md`; accepted normative
design/specification is recorded in `docs/design/visual-page.md` plus
`docs/spec/visual-page.md`. VIS36-01 through VIS36-06 are DONE. P36-06's
accepted structural route and representative article-summary evidence have
passed the independent Phase review, bounded duplicate-JSON repair, and
focused closure re-review. Phase 37 remains IN PROGRESS; its P37-01/P37-01B
design/specification Step is review-clean and closed. Implementation and
executable specifications remain P37-02 onward, and no external consumer
acceptance is claimed.

Phase 37 is the logical explanation-composition successor to Phase 36 and
remains IN PROGRESS. Its P37-01/P37-01B design/specification contract is
review-clean and closed as a Step; its canonical status authority is
`phase-37.md` plus `phase-37-checklist.md`, and normative design/specification
is recorded in `docs/design/explanation-composition.md` and
`docs/spec/explanation-composition.md`. Implementation and executable
specifications remain P37-02 onward. No full Phase validation or Phase review,
publication, push, or downstream consumer acceptance is claimed.

Phase 38 is a planned, not-started successor for the generated BoK knowledge
boundary. Its canonical planning authority is `phase-38.md` plus
`phase-38-checklist.md`. It must not be reported as active or implemented while
Phase 37 remains active unless the user explicitly changes the Phase order.
