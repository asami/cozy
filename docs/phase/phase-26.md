# Phase 26: Article Media Publication and BoK Integration

Status: closed

Start date: 2026-08-03
Close date: 2026-08-05

Dependency: accepted closed SmartDox Phase 1 commit
`fa21316973416c24bca7f8e366d65572c72720b7`, development integration coordinate
`org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`, and existing Cozy Phase 9, Phase
10, and Phase 19 publication boundaries. Public/non-SNAPSHOT SmartDox
publication is not a Phase 26 start gate.

## Goal

Let a Cozy-created BoK register optional exact-locale detailed infographics and
internally hosted video for an article, then provide the accepted SmartDox
article-media input together with Cozy-owned integrity evidence. The published
BoK stages site pages and Git-external artifact-repository media in one public
URL space.

The authoritative design and specification are:

- `docs/design/article-media-publication.md`
- `docs/spec/article-media-publication.md`

## Scope

In scope:

- contract promotion and pinning before implementation;
- Cozy association/integrity record production and validation;
- exact article identity/locale media correlation;
- registered artifact-repository publication and BoK build context; and
- staged BoK acceptance that preserves ordinary articles and existing video
  publication behavior.

Out of scope:

- changing SmartDox's closed Phase 1 schema or reopening that phase;
- public release, publication, or deployment;
- media rendering/transcoding during ordinary `cozy bok build`; and
- Phase 24/CBD Support work or unrelated documentation debt.

## Stage 26.0: Contract Promotion and Pinning

Stage Status:

- Current status: DONE
- Owner: Cozy
- SmartDox role: pinned contract and validation dependency, not implementation
  owner.
- Update rule: mark work complete only from the Phase 26 checklist.
- Checklist basis: `AM26-00`

Focus:

- promote accepted notes into the stable Cozy design and specification;
- pin the closed SmartDox Phase 1 contract and development coordinate; and
- obtain independent re-review evidence before marking this stage complete.

## Stage 26.1: Registry Contract Consumption

Stage Status:

- Current status: DONE
- Owner: Cozy
- SmartDox role: pinned contract and validation dependency.
- Update rule: mark work complete only from the Phase 26 checklist.
- Checklist basis: `AM26-01`

Focus:

- emit only the accepted SmartDox record fields;
- create the separately-owned Cozy integrity association and deterministic
  correlation; and
- preserve `VideoPublication` and `.video` compatibility.

## Stage 26.2: Repository Publication Boundary

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: mark work complete only from the Phase 26 checklist.
- Checklist basis: `AM26-02`

Focus:

- validate explicit descriptors, registered metadata, containment, and
  integrity without directory discovery; and
- retain final media and sidecars outside Git under the configured repository.

## Stage 26.3: BoK Site and Deployment Acceptance

Stage Status:

- Current status: DONE
- Owner: Cozy / BoK operator
- Update rule: mark work complete only from the Phase 26 checklist.
- Checklist basis: `AM26-03`

Focus:

- pass existing registry/repository context to SmartDox without heavy work; and
- demonstrate exact-locale projection and a common staged public URL space.

## Stage 26.4: Phase Closure

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: mark work complete only from the Phase 26 checklist.
- Checklist basis: `AM26-04`

Focus:

- complete required executable evidence, focused/full validation, review,
  re-review, staging evidence, and closure from the checklist ledger.

## Completion Criteria

Phase 26 closes when a BoK can publish a strict SmartDox article-media variant
and its separate Cozy integrity record from registered inputs, retain
Git-external artifacts in a configured repository, and stage matching site and
artifact URLs without changing ordinary `bok build` into media generation.

## Completion Evidence

- Step implementation commit:
  `7fd12152577e252a36db596d74bd0fdffd58ab75`.
- The final behavior-focused AM26-03 gate passed 16 suites and 332/332 tests in
  serialized invocation `84710-20260805T021810Z`.
- The Phase 26 repository-wide release gate passed 85 suites and 1088/1088
  tests, with 8 canceled and no failures, in serialized invocation
  `94900-20260805T023400Z`; SBT and wrapper exited zero and released the shared
  lock without warnings.
- Independent full review, conditional review-fix, test-fix, and focused
  re-review converged with no unresolved actionable findings.
- SmartDox remained an unchanged pinned dependency; Cozy's bilingual
  acceptance exercised its actual `DoxSiteGenerator`, so no SmartDox
  repository commit or separate full suite was required.
- Existing directive-hygiene follow-up `P26-HYG-001` remains non-blocking and
  persisted in
  `docs/journal/2026/08/2026-08-04-phase-26-hygiene-follow-up.md`.

## References

- `docs/phase/phase-26-checklist.md`
- `docs/design/article-media-publication.md`
- `docs/spec/article-media-publication.md`
- `docs/notes/article-media-publication-bok-integration.md`
- `docs/journal/2026/08/article-media-publication-bok-integration-handoff-2026-08-03.md`
- `docs/phase/phase-9.md`
- `docs/phase/phase-10.md`
- `docs/phase/phase-19.md`
