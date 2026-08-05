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

Status: DONE

- [x] Carry artifact identity/version, public/repository paths, media type,
      SHA-256, provenance/registry references, and publication state only in
      Cozy integrity records.
- [x] Upsert deterministic strict article entries at
      `metadata/article-media/<articleIdentity>.json` and integrity entries at
      `metadata/article-media-integrity/<articleIdentity>/<locale>/<role>.json`
      in configured publication bundles only.
- [x] Reuse video evidence at `metadata/video/<name>/<version>/manifest.json`
      and `metadata/artifacts/repository/<name>.json`; derive infographic
      evidence from explicit `cozy.media.v1` descriptor/resource results.
- [x] Derive the Cozy-only `registered`/`published`/`withdrawn` artifact state
      independently from SmartDox `draft`/`published`/`withdrawn` status.
- [x] Forbid discovery scans of `target`, work directories, generated site
      trees, arbitrary repositories, and SmartDox discovery output.
- [x] Validate containment, existence, hash, version, and production policy.
- [x] Apply no-fallback locale behavior and the specified missing/stale media
      policy.

Step acceptance evidence recorded on 2026-08-04:

- AM26-02A registry projection, AM26-02B video evidence, AM26-02C infographic
  evidence, and AM26-02D policy integration each completed focused planning,
  implementation, review, conditional finding repair, and clean focused
  re-review.
- Configured publication-bundle mutation is serialized by one real-root lock,
  revalidates the complete bundle-digest snapshot, binds recognized
  article-media metadata to canonical semantic keys, and rejects malformed
  bundles before atomic replacement.
- The behavior-focused Step gate exercised strict/integrity association,
  deterministic registry upsert, exact registered evidence, containment,
  preview/production policy, concurrency, and generic registry compatibility:
  serialized invocation `45371-20260804T130503Z`, 9 suites and 195/195 tests
  passed with `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.
- Repository-wide full validation remains reserved for the Phase 26 release
  gate.

## AM26-03: BoK Build and Deployment Projection

Status: DONE

- [x] Have `cozy bok publish-video`/media publication create or update records.
- [x] Have ordinary `cozy bok build` only read registry/repository context and
      perform no rendering or transcoding.
- [x] Verify exact-locale article and global/category Notice projection for a
      bilingual pilot.
- [x] Stage site output and repository artifacts under one BoK public URL
      space.
- [x] Preserve ordinary articles and BoKs with no media record.

Planned slices:

- `AM26-03A` — promote the operation and build-handoff contract before code:
  fix the explicit article identity/locale inputs for video and infographic
  publication, role-local registry merge semantics, preview/production build
  context, and compatibility behavior in the authoritative design/spec.
- `AM26-03B` — adapt explicit publication operations: expose typed media
  publication results, bind opted-in video/media publication to article-media
  evidence, and atomically preserve unaffected roles and locales. Existing
  unbound `.video` packages and ordinary media publication remain legacy-only.
  Execute this as three independently reviewed internal Slices: `AM26-03B1`
  adds the shared locked registry transaction and role-local merge foundation;
  `AM26-03B2` adds explicit `.video` article-media opt-in and registration;
  `AM26-03B3` adds `bok publish-media`, typed media publication results,
  command-wide preflight, and `update-publication`/one-stop orchestration.
  `AM26-03B1` is limited to `CozyPublicationCompiler`,
  `CozyArticleMediaRegistry`, and its executable specification: expose one
  synchronous same-real-root transaction seam, merge exactly one-role variant
  updates, create/reuse the deterministic article owner, preserve all
  unaffected entries, reject duplicate/conflicting ownership, and prove
  concurrent role updates cannot be lost. It does not change CLI, video/media
  descriptors, artifact publication, build handoff, or staging.
  Execute `AM26-03B2` in two independently reviewed parts: `AM26-03B2a`
  extends `.video` decoding/resolution with the optional all-or-nothing
  `publish.articleMedia` binding and adds a typed adapter from validated video
  publication evidence to one registry `RoleUpdate`; `AM26-03B2b` then makes
  `bok publish-video` commit its legacy video bundles and opted-in role updates
  under the shared transaction. `B2a` does not change command behavior, while
  `B2b` preserves unbound legacy packages and prevents nested lock domains.
  `AM26-03B2a` is limited to `CozyVideoPublisher`, a new Cozy publication-side
  video-registration adapter, and its focused executable specification. The
  resolved binding contains normalized `articleIdentity`, exact canonical
  `locale`, and SmartDox `status`; the adapter derives site-hosted
  `content_url` only from validated video evidence and returns no update when
  the block is absent. It does not write a bundle or alter `CozyBok` execution.
- `AM26-03C` — integrate the ordinary BoK build boundary: validate only the
  configured registry/repository context, materialize the strategy-effective
  publication view, pass it to SmartDox, and prove that build invokes no media
  renderer or transcoder.
- `AM26-03D` — add the bilingual BoK acceptance fixture and staging evidence:
  verify exact-locale article plus global/category Notice projection through
  pinned SmartDox behavior, retain media-free outputs, and prove site and
  repository artifacts share the staged public URL root. The acceptance uses
  the actual pinned SmartDox `DoxSiteGenerator` with the production-effective
  Cozy snapshot; it does not reproduce article or Notice projection in Cozy
  test code. Compare the complete media-free generated realm with the
  no-publication baseline: require the same relative path set, exact bytes for
  non-YAML outputs, and complete parsed-value equality for YAML outputs where
  mapping key order is non-semantic. Execute the scaffold-generated stage
  prototype against a persistent Git staging working tree, preserve either a
  repository `.git` directory or worktree `.git` file, and verify every staged
  artifact path and SHA-256 below `<staging-root>/repository` against its Cozy
  integrity record.

Each Slice receives its own implementation review and clean re-review. The
Step gate remains focused; repository-wide full validation is reserved for
`AM26-04` and the Phase 26 release gate.

Step acceptance evidence recorded on 2026-08-05:

- AM26-03A contract promotion, AM26-03B publication operations, AM26-03C
  build handoff, and AM26-03D bilingual staging acceptance completed their
  planned Slice reviews, conditional finding repairs, and clean focused
  re-reviews.
- The pinned SmartDox acceptance exercised exact-locale article and
  global/category Notice projection, media-free full-Realm compatibility,
  canonical producer manifests and registries, staged artifact path/SHA-256
  agreement, repository union semantics, and preservation of both Git
  directories and worktree files.
- The behavior-focused Step gate exercised registry transactions, video and
  infographic publication commands, orchestration, preview/production build
  context, no-heavy-work handoff, policy/evidence validation, bilingual BoK
  staging, legacy BoK behavior, and bibliography compatibility: serialized
  invocation `84710-20260805T021810Z`, 16 suites and 332/332 tests passed with
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`, and no SBT warnings.
- Repository-wide full validation remains reserved for `AM26-04` and the
  Phase 26 release gate.

## AM26-04: Specifications and Closure

Status: DONE

- [x] Add executable specifications proving Cozy producer/validator strict
      field emission without integrity-field serialization, conditional
      integrity correlation, boundary validation, and every preview/production
      policy.
- [x] Add executable specifications for build handoff/no-heavy-work and
      `VideoPublication`/`.video` compatibility.
- [x] Add a bilingual BoK pilot fixture.
- [x] Run focused and full Cozy and affected SmartDox tests.
- [x] Run `git diff --check` in every modified repository.
- [x] Complete read-only post-implementation review, repair actionable
      findings, and complete clean re-review.
- [x] Record BoK staging and artifact-publication evidence.
- [x] Commit validated changes with required version updates and close Phase 26
      from this checklist.

Phase closure evidence recorded on 2026-08-05:

- The AM26-03 implementation and executable evidence were committed as
  `7fd12152577e252a36db596d74bd0fdffd58ab75`; every included Scala header was
  rechecked against the version-update instruction and records its Aug. 5
  source-update date.
- The final focused gate passed 16 suites and 332/332 tests in invocation
  `84710-20260805T021810Z`. The final repository-wide release gate passed 85
  suites and 1088/1088 tests, with 8 canceled and no failures, in invocation
  `94900-20260805T023400Z`; both SBT and wrapper exited zero, released the
  shared lock, and reported no warnings.
- SmartDox was unchanged and required no repository commit or separate full
  suite. The pinned actual generator remained covered by the bilingual Cozy
  acceptance gate.
- Full review and all conditional fix/test-fix loops converged through clean
  focused re-review with zero unresolved actionable findings. `git diff
  --check` passed in the sole modified repository.
- Staging evidence covers exact-locale article and Notice projection,
  media-free output equality, valid producer PNG/MP4 artifacts, canonical
  manifests/registries, artifact SHA-256, common public URL roots, repository
  union semantics, and Git directory/worktree-file preservation.
- Existing `P26-HYG-001` remains a separate, non-blocking directive-hygiene
  follow-up in
  `docs/journal/2026/08/2026-08-04-phase-26-hygiene-follow-up.md`.

Phase 26 is closed from this completed ledger.
