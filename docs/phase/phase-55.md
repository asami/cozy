# Phase 55: Site-Context-Preserving Media Registration

Status: COMPLETE

Plan date: 2026-09-07
Implementation started: 2026-09-08
Closure prepared: 2026-09-08

Development item: DEV-021

## Goal

Make `cozy media register-site` and `register-site-wip` validate media
currentness with the same effective site context used by build and receipt
creation. A site-aware artifact that is current must register directly from its
authoring descriptor without a copied compatibility descriptor or replacement
receipt.

## P55-01: Canonical Site Context Selection

Stage Status: COMPLETE

- Freeze one canonical way to recover the effective paired `site-root` and
  `site-config` authority during registration.
- Prefer deterministic descriptor/project-owned selection when a publication
  profile already identifies one unambiguous site context.
- If an explicit command surface is required, keep `--site-root` and
  `--site-config` inseparable and apply the same resolution rules as build.
- Use one resolved `CozyMediaSiteContext` across build, verify, publish,
  `register-site`, and `register-site-wip`; do not reconstruct context-free
  plans at downstream currentness boundaries.

## P55-02: Registration Currentness Integration

Stage Status: COMPLETE

- Propagate the selected site context through
  `CozyArticleMediaSiteCommand.Config`, `CozyArticleMediaSiteBinding.plan`, and
  `CozyMedia.CommandConfig` without weakening `cozy.media.receipt.v2`.
- Preserve site configuration, canonical base URL, localized route, source,
  descriptor, and artifact identities as receipt/currentness inputs.
- Preserve normalized direct non-symlink path authority, site membership, and
  article-source containment checks.
- Keep packages without site-aware resources behaviorally unchanged.

## P55-03: Ordinary and Document Project Acceptance

Stage Status: COMPLETE

The command, binding, prepared-publication, and receipt-currentness path carry
the explicit paired context. Ordinary and Document Project media acceptance,
the unsafe-path rejection matrix, and context-free compatibility are covered
by the accepted focused specifications. The release commit is bound only after
the required post-edit full Cozy validation succeeds.

- Build and directly register Japanese and English article PDFs containing
  `site:[...]` links from their original authoring descriptors.
- Cover both an ordinary DoxSite article and a Document Project-backed article.
- Prove that changes to `site.conf`, base URL, or localized routes make prior
  receipts stale and that regeneration restores registrability.
- Prove deterministic rejection of a missing context half, symlinked context,
  out-of-site paths, and an article outside the selected site.
- Cover both normal and WIP registration when they share the receipt contract.
- Use `COZY-GAP-REGISTER-SITE-CONTEXT-001` only as downstream acceptance and
  removal evidence; do not adopt its adapter layout as the Cozy contract.

## Dependencies

- completed SmartDox site-link/FQN PDF behavior and its site-aware receipt;
- the existing `cozy.media.receipt.v2` currentness contract; and
- the SimpleModeling.org Part 7/8 temporary adapter as a downstream driver.

## Exclusions

- Weakening fail-closed receipt currentness.
- Copying accepted artifacts into a compatibility descriptor as normal Cozy
  behavior.
- Changing PDF rendering or SmartDox link semantics.
- Document Project provider execution or Publication Export.
- Publication, deployment, upload, push, or deletion of downstream adapters.

## Completion Criteria

Phase 55 completes when site-aware ordinary and Document Project media packages
register directly from their original descriptors with exact accepted artifact
and receipt bytes; stale and unsafe context changes remain rejected; packages
without site context remain compatible; focused specifications and full Cozy
validation pass; and independent review finds no Current Phase Blocker.

## Closure Evidence

- Step implementation is accepted in Cozy commit
  `faf00577f8bc0149ab5d0fe99abb9aec5f01243e`
  (`feat(media): preserve site context during registration`).
- The Phase full review found the relative paired-path admission and one
  executable-specification boundary defect; the bounded repair is accepted by
  focused re-review with no Current Phase Blocker, Hygiene, or Development
  Candidate.
- Focused repair validation `P55-PHASE-REPAIR-VAL-001` passed 24 tests across
  the normal and WIP registration command specifications. The final full Cozy
  validation is the post-edit release gate; this Phase makes no publication,
  deployment, upload, push, adapter deletion, or external-consumer claim.

## References

- `docs/phase/phase-55-checklist.md`
- `docs/journal/2026/09/2026-09-07-register-site-context-currentness-gap.md`
- `docs/design/smartdox-site-media-registration.md`
- `docs/spec/smartdox-site-media-registration.md`
