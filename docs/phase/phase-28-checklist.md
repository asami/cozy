# Phase 28 Checklist: Project Configuration and Profile Resolution

This checklist is the authoritative progress ledger for Phase 28. It is not a
normative contract. AM28-00 uses the existing frozen design/spec authority:

- `docs/design/simplemodeling-org-wip-article-media.md`; and
- `docs/spec/simplemodeling-org-wip-article-media.md`.

Their existence records the contract freeze, not Stage completion. AM28-00
remains `PLANNED` until implementation review and completion evidence are
recorded here.

## Split provenance and gate

The approved 2026-08-12 split retains `AM28-00` and `AM28-01` in Phase 28,
moves `AM28-02` once to Phase 28.1, and moves `AM28-03` and `AM28-04` once to
Phase 28.2. No Stage, Slice, validation, or commit completed before the split.

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 28

All retained stages begin `PLANNED`; checkboxes remain unchecked until the
corresponding work and evidence are complete.

## AM28-00: Contract Promotion and WIP/Production Boundary

Status: DONE

- [x] Complete implementation review and completion evidence for the frozen
      normative design/spec documents, including explicit `smartdox-site`
      versus standard-BoK responsibility and the preserved Phase 27 production
      boundary.
- [x] Freeze exact project configuration, CLI/mode ownership, downstream WIP
      registry and deterministic `website.d` video contracts before product
      implementation.
- [x] Freeze explicit top-level `articleMedia.publicationProfile`, discovered
      `project.kind: smartdox-site`, and resource-level `articleMedia` opt-in;
      reject source-type, descriptor-profile, path, repository-name,
      `credits.profile`, scan, or inference as workflow selectors.
- [x] Freeze regular-file, normalized-path, hash, symlink, root, atomicity,
      no-network, and no-upload invariants. WIP state must not spoof or promote
      production technical/visual QA or published-YouTube evidence; listening
      may remain pending as non-gating evidence only.

## AM28-01: Project Configuration Discovery and Profile Layering

Status: DONE

- [x] Specify and validate nearest direct non-symlink
      `conf/cozy/config.yaml` project-root discovery with provenance
      diagnostics and no Git dependency.
- [x] Implement deterministic built-in < user < project `conf` < project
      `.cozy` < package `conf` < package `.cozy` < explicit profile layering,
      including shared credit and publication profiles.
- [x] Require explicit selectors and resource-level opt-in while preserving
      legacy package-local, standalone, and standard-BoK compatibility.
- [x] Add executable specifications for safe ancestor resolution, layer
      precedence/provenance, symlink rejection, selector separation, and the
      absence of implicit article-media registration.

## Baseline evidence retained from the source Phase (2026-08-12)

- `simplemodeling-org/etc/runweb-wip.sh` exited 0 and rebuilt `doxsite.d`,
  `arcadiasite.d`, `antora.d`, and `website.d`.
- Generated Part 5 EN/JA notices contained no `notice.media`.
- `website.d` contained no MP4 and generated HTML contained no article-media
  buttons, although widget templates already had optional media controls.
- The script invoked Dox, Antora, Arcadia, and copy only; it did not invoke
  Cozy registration or stage video.
- SimpleModeling.org had no root `conf/cozy/config.yaml`; the Part 5
  `media.yaml` had no top-level or resource-level `articleMedia` declarations.
- Part 5 production descriptors had completed technical/visual QA,
  `listeningReview` pending, and YouTube not published. Listening is
  evidence-only and non-gating; accepted published YouTube remains the
  production gate.

## Part 5 discovery fixture

- `/Users/asami/src/dev2025/simplemodeling-org/src/main/doxsite/development-process/object-modeling.dox`
- `/Users/asami/src/dev2025/simplemodeling-org/src/main/media/development-process/object-modeling/media.yaml`
- `/Users/asami/src/dev2025/simplemodeling-org/src/main/media/development-process/object-modeling/video-ja.yaml`
- `/Users/asami/src/dev2025/simplemodeling-org/src/main/media/development-process/object-modeling/video-en.yaml`

Phase 28 closes only after the normative contract, configuration/profile
implementation, executable validation, independent review, and documentation
convergence are evidenced here. Phase 28.1 starts only after that closure.
