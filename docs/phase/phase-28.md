# Phase 28: Project Configuration and Profile Resolution

Status: closed

Start date: 2026-08-12
Close date: 2026-08-12

Phase 27 remains closed. Phase 24 remains separately planned and blocked on
its own CNCF Skill Bundle contract.

## Split provenance

On 2026-08-12 the original `SimpleModeling.org WIP Article Media Integration`
scope received `Phase Plan Gate: SPLIT_REQUIRED`: its conservative estimate
was 12–18 hours because project configuration, safe profile discovery, WIP
staging and registry mutation, and the Part 5 cross-repository acceptance path
formed three independently closable boundaries. The user approved this exact
ordered split:

1. Phase 28: Project Configuration and Profile Resolution;
2. Phase 28.1: WIP Local Article Media Registration; and
3. Phase 28.2: SimpleModeling.org Part 5 Integration and Regression.

This file retains the source Phase identity, observed baseline, corrections,
and first delivery unit. No completed Stage, accepted Slice, validation, or
commit preceded the split. `AM28-02` moved once to Phase 28.1; `AM28-03` and
`AM28-04` moved once to Phase 28.2.

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 28

## Goal

Freeze the special `simplemodeling.org` `smartdox-site` boundary and give Cozy
one safe project-configuration and profile-resolution contract. Deeply nested
media and video packages must discover shared project policy without Git,
without confusing credit selection with site registration, and without
changing standalone or standard-BoK behavior.

Part 5, `development-process/object-modeling`, is the required executable
fixture for project discovery and profile provenance. Local WIP staging belongs
to Phase 28.1; `runweb-wip` integration and rendered-card acceptance belong to
Phase 28.2.

The phase ledger is not the normative contract. The existing frozen `AM28-00`
authority is:

- `docs/design/simplemodeling-org-wip-article-media.md`; and
- `docs/spec/simplemodeling-org-wip-article-media.md`.

Their existence records the contract freeze, not Stage completion. `AM28-00`
remains `PLANNED` until implementation review and completion evidence are
recorded in the checklist.

## Boundary and invariants

- `simplemodeling.org` is a special `smartdox-site`: SmartDox builds its site
  directly, while Cozy supplies and registers article media.
- Standard BoK behavior is unchanged. A standard BoK keeps video in its BoK
  repository/publication path and is never routed through `smartdox-site`
  handling.
- Workflow selection requires explicit top-level
  `articleMedia.publicationProfile`, discovered `project.kind:
  smartdox-site`, and resource-level `articleMedia` opt-in. It is never
  selected by source type, descriptor profile presence, paths, repository
  name, or `credits.profile`.
- Project discovery uses the nearest direct non-symlink
  `conf/cozy/config.yaml`, stops at the filesystem root, uses no Git state, and
  rejects ambiguous or unsafe roots.
- Configuration precedence is deterministic: built-in < user < project
  `conf` < project `.cozy` < package `conf` < package `.cozy` < explicit.
  Diagnostics expose the selected project root, layer, publication profile,
  credit profile, and source without serializing internal paths into public
  SmartDox records.
- Project publication-profile roots resolve from the discovered project root;
  descriptor-local roots remain descriptor-relative. Package-local and
  standalone profiles remain compatible.
- Production eligibility remains the Phase 27 contract: completed technical
  and visual QA plus an accepted published YouTube URL. Human listening review
  may remain pending and is evidence only.

## Stages

### AM28-00: Contract Promotion and WIP/Production Boundary

Stage Status:

- Current status: DONE
- Owner: Cozy / SmartDox boundary
- Update rule: mark work complete only from the Phase 28 checklist.
- Checklist basis: `AM28-00`

The handoff and observed baseline are frozen in the normative design/spec
authority. Complete implementation review and completion evidence for that
contract, including project configuration, selector separation, CLI/mode
ownership, production versus WIP evidence, standard-BoK exclusion, and the
downstream Phase 28.1 staging/registry contract before implementation.

### AM28-01: Project Configuration Discovery and Profile Layering

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: mark work complete only from the Phase 28 checklist.
- Checklist basis: `AM28-01`

Implement the shared safe-ancestor resolver and configuration provenance
chain, project configuration discovery, shared credit profiles, and reusable
media publication profiles. Require explicit selectors and resource opt-in
while preserving legacy package-local, standalone, and standard-BoK behavior.

## Observed baseline (2026-08-12)

`simplemodeling-org/etc/runweb-wip.sh` exits 0 and rebuilds `doxsite.d`,
`arcadiasite.d`, `antora.d`, and `website.d`, but the generated Part 5 notices
contain no `notice.media`; `website.d` contains no MP4; and the generated HTML
contains no article-media buttons. Widget templates already contain optional
infographic/video controls. The script invokes Dox, Antora, Arcadia, and copy
only. The repository lacks root `conf/cozy/config.yaml`; Part 5 `media.yaml`
lacks top-level and resource-level `articleMedia`; and its production records
have technical/visual QA complete, listening pending, and YouTube not
published.

These observations remain source-Phase history. Phase 28 fixes project policy
discovery; Phase 28.1 owns WIP staging and local registration; Phase 28.2 owns
the SimpleModeling integration and rendered-card acceptance.

## Completion criteria

- Normative design/spec remain frozen as the complete three-Phase
  responsibility and evidence boundary without changing Phase 27 production
  eligibility; implementation review and completion evidence are recorded.
- Deep Part 5 descriptors resolve the project root, project config, shared
  credit profile, and selected publication profile with deterministic
  provenance and no Git dependency.
- Explicit profile-ID selection wins; package-local configuration override and
  standalone compatibility remain supported, while descriptor-local location
  overlays cannot supply site kind; implicit registration remains impossible.
- Standard BoK behavior remains repository-based and unchanged.
- Focused executable specifications, independent review, and Step validation
  pass; Phase 28 closes before Phase 28.1 starts.

## Dependencies and successors

- Predecessor: closed Phase 27.
- Successor: Phase 28.1, which starts only after Phase 28 closes.
- Phase 28.2 starts only after Phase 28.1 closes.

## References

- `docs/phase/phase-28-checklist.md`
- `docs/phase/phase-28.1.md`
- `docs/phase/phase-28.2.md`
- `docs/phase/phase-27.md`
- `docs/design/simplemodeling-org-wip-article-media.md`
- `docs/spec/simplemodeling-org-wip-article-media.md`
- `docs/design/smartdox-site-media-registration.md`
- `docs/spec/smartdox-site-media-registration.md`
- `docs/design/article-media-publication.md`
- `docs/spec/article-media-publication.md`
