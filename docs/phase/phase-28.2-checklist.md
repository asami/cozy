# Phase 28.2 Checklist: Part 5 Integration and Regression

This checklist is the authoritative progress ledger for Phase 28.2. It is not
a normative contract.

## Split provenance and gate

The approved 2026-08-12 split moved the original `AM28-03` and `AM28-04` to
Phase 28.2 exactly once. Closed Phase 28.1 is the required predecessor.

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 3–5h
- recommended_minimum_effort: high
- runtime_suitability: re-evaluate when this Phase starts
- source: approved split from Phase 28

## AM28-03: SimpleModeling.org Integration and Part 5 Acceptance

Status: DONE

- [x] Add root `conf/cozy/config.yaml`, the shared credit/publication profiles,
      and explicit Part 5 top-level/resource-level `articleMedia` bindings
      using the Phase 28 contract.
- [x] Wire `etc/runweb-wip.sh` to supported Cozy/Dox product commands only;
      never edit a registry directly or depend on incidental article ranking.
- [x] Use Part 5 JA/EN final MP4s and summary infographics and deterministically
      render/target the Part 5 notice/card even when it is beyond five visible
      card slots.
- [x] Verify exact-locale EN and JA cards, both localized links, playable local
      paths beneath `website.d`, matching admitted-artifact hashes, no
      opposite-locale leakage, deterministic regeneration, and production
      YouTube behavior through separate accepted evidence.

### AM28-03 acceptance evidence

- SimpleModeling.org commit
  `fd460312d992b15086225341360e7586a9acbe35` owns the root configuration
  and shared credit profile, explicit Part 5 media binding, safe WIP launcher
  integration, complete JA/EN category-card visibility, and local
  `content_url` widgets.
- Two consecutive accepted final pre-review generations produced byte-identical
  URI-resolved JA/EN Object Modeling notices, Development Process category
  YAML/HTML, article HTML, localized PNGs, local MP4s, and article-media
  metadata. Identity was resolved by `notice.uri`, never by card rank.
- The JA MP4 SHA-256 is
  `d017693941554111689fe469a30051100ee974c64cb6cb8acdeba16fc0bd4962`;
  the EN MP4 SHA-256 is
  `6328784d34bd94e5d41d5432e33ebbb76537c1633d1ec1f6a3a1616eeadea600`.
  Both probe as H264/AAC at 1280x720.
- Exact-locale cards and metadata had no opposite-locale leakage. Stable media
  classes and external `watch_url` compatibility remained, while WIP used no
  network, upload, publish, or deploy and cleaned its external temporary roots.
- Independent review findings R10-001 through R10-006 were fixed.
  Focused re-review `P28.2-REREVIEW-20260812-R13` was CLEAN with all six
  findings RESOLVED; the final launcher and Bash syntax check exited 0.
- AM28-03 uses already accepted Phase 27/28.1 production and standard-BoK
  evidence only for its integration boundary. AM28-04 still owns explicit
  regression, full-suite validation, and Phase closure.

## AM28-04: Standard BoK Regression, Review, and Closure

Status: PLANNED

- [ ] Confirm standard BoK video remains on its repository/publication path and
      receives no `smartdox-site` WIP staging or registration behavior.
- [ ] Regress normal package, standalone, and Phase 27 production paths,
      including completed technical/visual QA and an accepted published
      YouTube URL; retain pending listening as non-gating evidence only.
- [ ] Run focused/full executable specifications and the Part 5
      `etc/runweb-wip.sh` end-to-end gate, recording exact results, paths, and
      source/staged/generated hashes.
- [ ] Obtain independent review and clean re-review, resolve actionable debt,
      and verify phase/checklist, design/spec, implementation, and evidence
      convergence before closure.

## Part 5 fixture and acceptance paths

- `/Users/asami/src/dev2025/simplemodeling-org/src/main/doxsite/development-process/object-modeling.dox`
- `/Users/asami/src/dev2025/simplemodeling-org/src/main/media/development-process/object-modeling/media.yaml`
- `/Users/asami/src/dev2025/simplemodeling-org/target/media/development-process/object-modeling/ja/final.mp4`
- `/Users/asami/src/dev2025/simplemodeling-org/target/media/development-process/object-modeling/en/final.mp4`
- `/Users/asami/src/dev2025/simplemodeling-org/etc/runweb-wip.sh`

Phase 28.2 closes only after the Part 5 WIP card/local-media acceptance,
production and standard-BoK regression, executable validation, independent
review, and documentation convergence are evidenced here.
