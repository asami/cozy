# Phase 46.1: Cross-Media Logical Projection and Cozy-Local Closure

Status: IN_PROGRESS

Plan date: 2026-09-04

Development item: DEV-017

Predecessor: Phase 46 closure

## Goal

Consume the frozen Phase 46 typed Story Flow and Explanation Structure model
to generate deterministic slide and video projections plus one integrated
confirmation HTML, and accept their semantic completeness and traceability in
Cozy-local artifacts. Under `P461-DEC-ROOT-001`, Article 8 driver promotion,
acceptance, and confirmation are separately created work and excluded from
this Phase.

## PROJECT46-01: Slide and video projections

Stage Status:

- Current status: COMPLETE
- Owner: typed cross-media projection
- Update rule: complete only when slide and video mappings preserve the same
  accepted meaning and their medium-specific additions remain separate.

- Map Story Steps and Explanation Structures to one or more Visual Pages.
- Map the same identities independently to one or more Storyboard scenes.
- Preserve Logical Pattern, nodes, Relations, claims, references, policy, and
  traceability in both projections.
- Keep pagination separate from video narration, timing, transition, caption,
  and production semantics.

## PROJECT46-02: Integrated confirmation HTML

Stage Status:

- Current status: COMPLETE
- Owner: deterministic cross-media review projection
- Update rule: complete only when Story Flow, local logical structure, visual
  selection, cross-media mapping, diagnostics, and currentness are visible.

- Show the complete Story Flow before page and scene details.
- Show each Explanation Structure's Logical Pattern, nodes, typed Relations,
  selected Visual Pattern, and typed parameters.
- Show article-section, slide-page, and video-scene mapping from stable core
  identities.
- Separate reader-facing material, reviewer diagnostics, and production-only
  instructions.
- Generate deterministic, self-contained, accessible HTML without external
  CDN, font, script, or service dependencies.

Closure basis: `PROJECT46-02A-confirmation-html` passed independent Step review
and focused `CozyDocumentPresentationSemanticsSpec` validation (10 succeeded,
0 failed). This acceptance commit binds the reviewed confirmation HTML,
projection identity, executable specification, and supporting specification and
design to the exact Step path set.

## PROJECT46-03: Receipts and semantic coverage

Stage Status:

- Current status: OPEN
- Owner: projection identity and executable completeness evidence
- Update rule: complete only when receipts and semantic-coverage specs pass on
  exact inputs and outputs.

- Bind accepted Content Core, typed plan, projection mappings, catalogs,
  policy, renderer/profile, sources, assets, and output bytes.
- Prove each declared Story Step and Explanation Structure appears in every
  selected projection.
- Diagnose missing, incompatible, stale, ambiguous, or unprojected content.
- Prove currentness receipts cannot substitute for semantic-completeness tests.

## PROJECT46-04: Cozy-local review, validation, and release closure

Stage Status:

- Current status: OPEN
- Owner: Cozy-local Phase closure
- Update rule: complete only when Cozy-local confirmation, focused/full
  validation, independent review, and local closure pass.

- Retain the accepted Story Flow and Explanation Structure in the integrated
  confirmation HTML without placeholder loss.
- Verify slide and video projections share semantic identities while retaining
  medium-local structure in Cozy-local outputs.
- Exclude project-private state from any Cozy-local projection.
- Run focused and final Cozy validation.
- Complete independent Phase review and bounded focused repair/re-review when
  applicable.
- Create the local Phase release commit without publication, registration,
  deployment, upload, or push.

## Exclusions

- Redefining the accepted Phase 46 public contract.
- Automatic semantic authoring or human-review bypass.
- Article 8 driver promotion, acceptance, or confirmation; that work is
  separately created under `P461-DEC-ROOT-001` and excluded from this Phase.
- Final PowerPoint/PDF/video rendering unless strictly required by an already
  accepted focused projection specification; renderer delivery remains a
  separate operation.
- Retrofitting Article 7 or earlier articles.
- Publication, registration, deployment, upload, push, or external-service
  mutation.

## Completion Criteria

Phase 46.1 completes only when slides, video, and integrated confirmation HTML
are deterministic projections of the same accepted Story Flow and Explanation
Structures; their typed mappings, receipts, semantic coverage, and stale
behavior pass; Cozy-local confirmation HTML shows the intended content without
placeholder loss; and independent review plus final Cozy validation accept the
exact local tree. Article 8 driver promotion, acceptance, and confirmation are
separate work and are neither required nor claimed here.

## Execution Profile

- planned duration: approximately 6 hours
- recommended parent profile: `gpt-5.6-luna / xhigh`
- cost role: lower-cost execution against the frozen Phase 46 contract
- pinpoint escalation: `gpt-5.6-terra / high` only if implementation exposes a
  genuine public-contract ambiguity that Phase 46 did not settle
- validation and review guarantees remain unchanged by the lower-cost profile.

## Primary references

- `docs/phase/phase-46.md`
- `docs/phase/phase-46.1-checklist.md`
- `docs/notes/document-project-logical-presentation-projection-specification-proposal.md`
- `docs/journal/2026/09/2026-09-04-document-project-logical-presentation-projection-decision.md`
