# Phase 28.1 Checklist: WIP Local Article Media Registration

This checklist is the authoritative progress ledger for Phase 28.1. It is not
a normative contract.

## Split provenance and gate

The approved 2026-08-12 split moved the original `AM28-02` to Phase 28.1
exactly once. Phase 28 is the required predecessor; Phase 28.2 is the ordered
successor.

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate when this Phase starts
- source: approved split from Phase 28

## AM28-02: WIP Local Video Staging and Provider-Neutral Registration

Status: PLANNED

- [ ] Stage hash-verified JA/EN direct regular MP4 inputs beneath disposable
      `website.d` at the frozen deterministic locale path and emit exact-locale
      provider-neutral WIP/local video and infographic records.
- [ ] Prove exact article/locale/role selection, playable local URLs, matching
      source and staged SHA-256 values, and no fallback or opposite-locale
      leakage.
- [ ] Implement transactional preflight, staging, registry merge,
      rollback/cleanup, and atomic website/registry replacement; preserve
      unrelated records and leave no partial mutation on failure or drift.
- [ ] Prove WIP performs no external network, upload, publish, or deploy and
      that production stays external-YouTube/evidence based while standard BoK
      stays repository/publication based.

## Security and regression acceptance

- [ ] Reject symlink inputs, symlink destinations, broad roots, root escape,
      unsafe identifiers, non-regular files, stale hashes, and changed
      evidence before replacement.
- [ ] Preserve existing provider-neutral and unrelated locale/article records,
      registry integrity, and strict role ownership.
- [ ] Cover dry-run/non-dry plan parity, zero/invalid candidates, exact target
      semantics, rollback, and repeatability.
- [ ] Regress Phase 27 production registration, package-local/standalone media,
      and standard-BoK behavior without invoking SimpleModeling.org scripts.

Phase 28.1 closes only after the WIP capability and its safety/regression
evidence converge. `etc/runweb-wip.sh` integration and Part 5 rendered-card
acceptance remain Phase 28.2 work.
