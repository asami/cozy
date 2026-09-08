# Phase 57 Checklist: Document Project Publication Export

Phase Status: PLANNED

Development item: DEV-020

## P57-01: Export Contract

- [ ] Freeze the public Document Project export command and target contract.
- [ ] Select only admitted current public Work Products with valid production
      receipts.
- [ ] Exclude private authorities, dialogue, history, attempts, review evidence,
      raw media, and state caches.
- [ ] Reject stale, partial, private, unsafe-path, or unreceipted input.

## P57-02: Manifest, Receipt, and Currentness

- [ ] Emit a versioned target/Work Product/hash/role/media/path manifest.
- [ ] Bind exact manifest and output bytes in an export receipt.
- [ ] Derive export stale/current state from all authoritative inputs.
- [ ] Let consumers validate exports without Document Project internals.

## P57-03: SimpleModeling.org Target Binding

- [ ] Add one typed SimpleModeling.org target binding.
- [ ] Consume Phase 55 site-aware registration without compatibility
      descriptors.
- [ ] Exercise task-private site preparation from an exact export bundle.
- [ ] Reject stale and partial exports at the consumer boundary.
- [ ] Keep deployment/public upload/production mutation outside Phase closure.

## P57-04: Native Skill Boundary and Closure

- [ ] Specify the publication-preparation skill as a client of native run,
      verify, export, and target contracts only.
- [ ] Remove transitional `cozy-article-media` adapter assumptions from the
      proposed skill contract.
- [ ] Report exact missing capability instead of adopting evidence manually.
- [ ] Run focused export/target/currentness specifications.
- [ ] Run full Cozy validation.
- [ ] Complete independent focused review with no Current Phase Blocker.
