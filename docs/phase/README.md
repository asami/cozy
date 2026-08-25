# docs/phase

Purpose: engineering work management.

Current phase state:

- Most recent closed phase: `phase-34.md`: BoK Metadata Input Admission
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
- In-progress single-Phase execution: `phase-30.md`: Unified Storyboard and
  Three-Gate Video Review Workflow. Under the explicit 2026-08-26
  one-Phase authorization, its internal Steps are P30-00 through P30-03. The
  intended workflow is
  `storyboard.md review -> optional image-backed visual-story review ->
  confirmation video review -> final video and rendered-video evidence review`.
  Content-review and video-review PPTX artifacts are optional inspection or
  distribution outputs, not workflow gates. Phase 30 remains incomplete; no
  implementation, validation, review, commit, or successor Phase is claimed.
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
No child Phase 30.1, 30.2, or 30.3 is created or required, and the current
record does not claim implementation or closure.
