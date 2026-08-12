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

Status: PLANNED

- [ ] Add root `conf/cozy/config.yaml`, the shared credit/publication profiles,
      and explicit Part 5 top-level/resource-level `articleMedia` bindings
      using the Phase 28 contract.
- [ ] Wire `etc/runweb-wip.sh` to supported Cozy/Dox product commands only;
      never edit a registry directly or depend on incidental article ranking.
- [ ] Use Part 5 JA/EN final MP4s and summary infographics and deterministically
      render/target the Part 5 notice/card even when it is beyond five visible
      card slots.
- [ ] Verify exact-locale EN and JA cards, both localized links, playable local
      paths beneath `website.d`, matching admitted-artifact hashes, no
      opposite-locale leakage, deterministic regeneration, and production
      YouTube behavior through separate accepted evidence.

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
