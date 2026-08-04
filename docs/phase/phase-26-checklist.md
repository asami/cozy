# Phase 26 Checklist

This checklist is the authoritative progress ledger for Phase 26: Article
Media Publication and BoK Integration. Contract authority is
`docs/design/article-media-publication.md` and
`docs/spec/article-media-publication.md`.

## AM26-00: Contract Promotion and Pinning

Status: DONE

- [x] Promote accepted exploratory material into Cozy design and authoritative
      specification documents.
- [x] Pin accepted closed SmartDox Phase 1 commit
      `fa21316973416c24bca7f8e366d65572c72720b7` and development coordinate
      `org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`.
- [x] Record that public/non-SNAPSHOT SmartDox publication is not a Phase 26
      start gate and that SmartDox Phase 1 remains closed.
- [x] Obtain independent re-review evidence for this contract repair.

## AM26-01: SmartDox Contract Consumption

Status: DONE

- [x] Emit only accepted SmartDox `article-media-publication` fields: `type`,
      `article.identity`, exact canonical locale variants, infographic
      `public_path`/optional `media_type`/optional `alt`, and video
      `presentation`/`status`/optional `provider`/optional `watch_url`/
      optional `content_url`.
- [x] Create the separate `cozy.article-media-integrity.v1` record keyed by
      normalized `(articleIdentity, locale, role)`.
- [x] Correlate each SmartDox field to exactly one integrity record and require
      equal site-visible public paths only for Cozy-produced infographics and
      site-hosted videos carrying `content_url`; preserve external-link and
      URL-less draft/withdrawn forms without artifact correlation.
- [x] Preserve existing `VideoPublication` and `.video` behavior: a complete,
      exact-locale native record wins without merge or fallback.
- [x] Reject invalid/ambiguous identities and duplicate normalized keys.

Step acceptance evidence recorded on 2026-08-04:

- AM26-01A, AM26-01B, and AM26-01C each completed independent review,
  conditional finding repair, and clean focused re-review.
- The behavior-focused Step gate exercised strict SmartDox production and
  parsing, Cozy integrity serialization, exact-key association, and legacy
  delegation: serialized invocation `81064-20260804T080628Z`, 3 suites and
  74/74 tests passed with `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.
- Repository-wide full validation remains reserved for the Phase 26 release
  gate.

## AM26-02: Artifact Repository and Publication Registry

Status: IN PROGRESS

- [ ] Carry artifact identity/version, public/repository paths, media type,
      SHA-256, provenance/registry references, and publication state only in
      Cozy integrity records.
- [ ] Upsert deterministic strict article entries at
      `metadata/article-media/<articleIdentity>.json` and integrity entries at
      `metadata/article-media-integrity/<articleIdentity>/<locale>/<role>.json`
      in configured publication bundles only.
- [ ] Reuse video evidence at `metadata/video/<name>/<version>/manifest.json`
      and `metadata/artifacts/repository/<name>.json`; derive infographic
      evidence from explicit `cozy.media.v1` descriptor/resource results.
- [ ] Derive the Cozy-only `registered`/`published`/`withdrawn` artifact state
      independently from SmartDox `draft`/`published`/`withdrawn` status.
- [ ] Forbid discovery scans of `target`, work directories, generated site
      trees, arbitrary repositories, and SmartDox discovery output.
- [ ] Validate containment, existence, hash, version, and production policy.
- [ ] Apply no-fallback locale behavior and the specified missing/stale media
      policy.

## AM26-03: BoK Build and Deployment Projection

Status: PLANNED

- [ ] Have `cozy bok publish-video`/media publication create or update records.
- [ ] Have ordinary `cozy bok build` only read registry/repository context and
      perform no rendering or transcoding.
- [ ] Verify exact-locale article and global/category Notice projection for a
      bilingual pilot.
- [ ] Stage site output and repository artifacts under one BoK public URL
      space.
- [ ] Preserve ordinary articles and BoKs with no media record.

## AM26-04: Specifications and Closure

Status: PLANNED

- [ ] Add executable specifications proving Cozy producer/validator strict
      field emission without integrity-field serialization, conditional
      integrity correlation, boundary validation, and every preview/production
      policy.
- [ ] Add executable specifications for build handoff/no-heavy-work and
      `VideoPublication`/`.video` compatibility.
- [ ] Add a bilingual BoK pilot fixture.
- [ ] Run focused and full Cozy and affected SmartDox tests.
- [ ] Run `git diff --check` in every modified repository.
- [ ] Complete read-only post-implementation review, repair actionable
      findings, and complete clean re-review.
- [ ] Record BoK staging and artifact-publication evidence.
- [ ] Commit validated changes with required version updates and close Phase 26
      from this checklist.
