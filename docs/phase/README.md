# docs/phase

Purpose: engineering work management.

Current phase state:

- Active phase: `phase-31.md`: Video Encoding Policy Profiles. VP31-01 is
  complete; VP31-02 renderer and metadata integration is next. Lightweight
  1280x720, 18 fps, CRF 32 is the encoding baseline.
- Planned split-required phase: `phase-30.md`: Unified Storyboard and
  Three-Gate Video Review Workflow. The intended workflow is
  `storyboard.md review -> optional image-backed visual-story review ->
  confirmation video review -> final video and rendered-video evidence review`.
  Content-review and video-review PPTX artifacts are optional inspection or
  distribution outputs, not workflow gates.
- Earlier planned phase: `phase-29.md`: Project-Owned Site BoK Metadata
  Finalization.
- Most recent closed phase: `phase-28.2.md`: SimpleModeling.org Part 5
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

Phase 30 is a parent planning boundary with `SPLIT_REQUIRED`. Its normative
Storyboard design/spec and executable child ledgers must be created in Phase
30.1 through Phase 30.3 before implementation begins.
