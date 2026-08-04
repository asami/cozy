# Article Media Publication and BoK Integration Handoff

date=2026-08-03
phase=Phase 26
status=historical handoff
scope_decision=P26-DEC-001

## Context

The user admitted SmartDox as the Phase 26 dependency repository under
`P26-DEC-001`. The accepted contract identity is closed SmartDox Phase 1 commit
`fa21316973416c24bca7f8e366d65572c72720b7`, used in development through
`org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`. Public/non-SNAPSHOT publication
was explicitly not made a Phase 26 start gate; SmartDox Phase 1 was not
reopened.

SimpleModeling.org's direct YouTube/widget operation remains separate. Cozy
BoKs use the common SmartDox contract for internally hosted media while keeping
generated video, captions, transcripts, and comparable artifacts in a
Git-excluded configured artifact repository.

## Corrected Responsibility Record

The accepted handoff separates two record surfaces. SmartDox owns its strict
`article-media-publication` schema and article/Notice projection. Cozy owns
the separate `cozy.article-media-integrity.v1` association/integrity projection
for artifact identity/version, repository path, SHA-256, provenance, and
publication state. SmartDox does not receive those Cozy fields and does not
scan repository, work, target, or generated-site directories.

Video provenance reuses existing CozyVideoPublisher manifest and repository
evidence. Infographic evidence comes from explicit `cozy.media.v1`
descriptor/resource results. Ordinary `cozy bok build` reads registered
publication/repository context and does no rendering or transcoding.

Phase 9, Phase 10, and Phase 19 remain closed boundaries composed by Phase 26;
they are not reopened. Stage 26.1 implementation ownership is Cozy. SmartDox
is a pinned contract and validation dependency.

## Authority References

This journal is historical and non-normative. The promoted authority is:

- `docs/design/article-media-publication.md`
- `docs/spec/article-media-publication.md`

Related work ledger and note:

- `docs/phase/phase-26.md`
- `docs/phase/phase-26-checklist.md`
- `docs/notes/article-media-publication-bok-integration.md`
