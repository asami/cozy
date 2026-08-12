# Phase 28.1: WIP Local Article Media Registration

Status: active

Start date: 2026-08-12

## Split provenance

Phase 28.1 was split from the original Phase 28 scope on 2026-08-12 after the
source Phase received `Phase Plan Gate: SPLIT_REQUIRED`. It owns the former
`AM28-02` exactly once. Phase 28 retains project configuration and profile
resolution; Phase 28.2 owns SimpleModeling.org script integration and Part 5
rendered-card acceptance.

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate when this Phase starts
- source: approved split from Phase 28

## Goal

Add a provider-neutral WIP registration path that stages admitted infographic
and video artifacts beneath a disposable site root. The command must place
hash-verified JA/EN MP4s directly under `website.d`, emit exact-locale local
article-media records, and preserve unrelated registry data atomically.

This Phase implements the reusable Cozy capability. It does not wire
`simplemodeling-org/etc/runweb-wip.sh` or own Part 5 rendered-card acceptance;
those belong to Phase 28.2.

## Boundary and invariants

- Phase 28 project discovery, profile layering, explicit
  `articleMedia.publicationProfile`, `project.kind: smartdox-site`, and
  resource opt-in are prerequisites.
- Inputs are normalized direct regular files with frozen identities and
  SHA-256 values. Symlinks, broad roots, root escape, ambiguous destinations,
  and raw identifiers in destination construction are rejected.
- The publication root is existing, normalized, and direct. Video is staged
  at the contract-defined exact-locale path beneath disposable `website.d`;
  no locale fallback or opposite-locale substitution is allowed.
- Infographic records remain site-public paths. WIP video records are
  provider-neutral local records and never masquerade as published YouTube
  evidence.
- Planning, staging, registry merge, and replacement are deterministic.
  Preflight completes before mutation; failure leaves no partial registry,
  staged file, or replacement tree.
- WIP registration performs no upload, publication, deploy, or network
  operation. WIP state is not copied into production state.
- Phase 27 production behavior is unchanged: accepted technical/visual QA and
  a published YouTube URL remain required; listening may remain pending as
  evidence only.
- Standard BoK behavior remains repository/publication based and does not use
  the `smartdox-site` WIP path.

## Stage

### AM28-02: WIP Local Video Staging and Provider-Neutral Registration

Stage Status:

- Current status: PLANNED
- Owner: Cozy
- Update rule: mark work complete only from the Phase 28.1 checklist.
- Checklist basis: `AM28-02`

Implement deterministic WIP planning, direct local artifact staging,
provider-neutral registry updates, and transaction/rollback behavior. Prove
exact-locale selection, artifact identity, no partial mutation, no external
side effects, and compatibility with the production and standard-BoK paths.

## Completion criteria

- The WIP command exposes a strict supported CLI and deterministic plan.
- Admitted infographic and local-video records retain their exact article,
  locale, role, path, and SHA-256 identities.
- Staged MP4s are playable at the declared site-local URLs and no other locale
  or provider state leaks into their records.
- Atomicity, symlink/root hardening, drift detection, rollback, no-network,
  production preservation, and standard-BoK exclusion have executable
  coverage.
- Focused validation, independent review, and documentation convergence pass.
- Phase 28.1 closes before Phase 28.2 starts.

## Dependencies and successor

- Predecessor: Phase 28, which must be closed.
- Successor: Phase 28.2, which starts only after Phase 28.1 closes.

## References

- `docs/phase/phase-28.1-checklist.md`
- `docs/phase/phase-28.md`
- `docs/phase/phase-28.2.md`
- `docs/design/simplemodeling-org-wip-article-media.md`
- `docs/spec/simplemodeling-org-wip-article-media.md`
- `docs/design/smartdox-site-media-registration.md`
- `docs/spec/smartdox-site-media-registration.md`
