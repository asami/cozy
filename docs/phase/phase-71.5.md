# Phase 71.5: Tamper and Contamination Protection Removal

status=planned
depends_on=phase-71.4.md
execution_priority=post_phase_71_cleanup

Status: planned; implementation not started
Planned at: 2026-10-03
Primary owner: Cozy
Predecessor: [Phase 71.4](phase-71.4.md).
Phase 71 through 71.4 remain unchanged and complete/close under their existing contracts before this Phase starts.

## Goal

Remove all Cozy-owned tamper-prevention, contamination-prevention, artifact-integrity and generated-artifact protection logic.

This is a removal Phase. It does not replace removed protection with another
mechanism.

## Governing decision

Cozy does not protect generated artifacts against tampering or contamination.
Generated artifacts are recreated from their authoritative sources when needed.

The following rules are absolute for this Phase:

- remove Cozy-owned tamper and contamination protection;
- no publication/distribution exception;
- no retained-integrity exception from the Phase 71 inventory;
- do not replace a removed mechanism with hash, digest, checksum, fingerprint,
  signature, copy comparison, snapshot, timestamp, revision, receipt,
  provenance, metadata, version, CAS, lock, atomicity, backup, rollback,
  fsync, re-read, equality check, or another protection mechanism;
- do not invent a new mechanism that preserves the same protection semantics
  under a different name or representation.

Format-mandated behavior implemented by standard codecs/libraries is not a
Cozy-owned protection mechanism. Do not add Cozy verification around it.

## Scope and owned Steps

### P715-01: Complete protection-mechanism inventory

Search the complete Cozy codebase, specifications, designs, tests, scaffolds,
configuration and help for behavior whose purpose is to detect, prevent,
survive, repair, prove or reject tampering/contamination of generated,
intermediate, copied, staged, published or distributed artifacts.

The inventory is purpose-based, not keyword- or hash-based. Include at least:

- tamper/integrity verification and mismatch rejection;
- hash/digest/checksum/fingerprint/signature artifact comparison;
- byte-for-byte copy/install/restore verification;
- snapshot or duplicate-state comparison used as protection;
- receipt/provenance/manifest evidence used to prove artifact immutability or
  unchanged bytes;
- drift/race/change-during-operation detection whose purpose is artifact
  protection;
- backup/restore and rollback whose purpose is preserving generated artifacts;
- atomic move/replacement, fsync, staging, lock or CAS behavior whose purpose is
  tamper/contamination/partial-artifact protection;
- post-write, post-copy, post-install or post-restore re-read/comparison;
- publication/distribution integrity mechanisms previously classified
  `retain-only-integrity` by Phase 71.

Inventory classification must identify the concrete behavior and its callers.
It must not create a new retention taxonomy.

### P715-02: Remove generated/intermediate protection

Delete the inventoried protection behavior from local generation, document,
media, video, UI, modeler, currentness, review, receipt and provenance paths.

Remove supporting models, fields, codecs, diagnostics, configuration,
scaffolds, tests and documentation when their only remaining purpose is the
removed protection.

Generation failure remains an ordinary operation failure. Recovery is
regeneration from authoritative source, not protection or restoration of a
generated artifact.

### P715-03: Remove publication/distribution protection

Delete Cozy-owned artifact-integrity/tamper/contamination protection from
publication, WIP, repository, CAR/SAR, subcomponent release, export and
distribution paths as well.

This explicitly supersedes Phase 71's decision to retain selected
publication/distribution integrity mechanisms. Remove those mechanisms rather
than preserving them as exceptions.

Do not replace them with signatures, alternate checksums, snapshots, byte
copies, metadata identities, stronger transactional machinery, or another
integrity scheme.

### P715-04: Remove protection-oriented filesystem machinery

For filesystem staging, copy and output code, remove backup, restore, rollback,
atomic replacement, fsync, locking, CAS, post-operation verification and
related machinery when it exists to prevent or recover from artifact
tampering/contamination or partial/generated-artifact pollution.

Do not introduce a replacement safety protocol. Use ordinary filesystem
operations and ordinary I/O failure reporting.

If a construct has an independent product semantic unrelated to artifact
protection, that independent behavior may remain, but this Phase must not add
or strengthen it. The burden of the Phase is deletion, not redesign.

### P715-05: Contract and test simplification

Update design/specification/help so they no longer require or promise removed
tamper/contamination protection.

Delete tests whose asserted product behavior is the removed protection.
Retain or rewrite tests only for remaining product semantics; do not translate
an integrity/tamper test into an equivalent non-hash protection test.

Add focused negative structural checks proving that removed protection
mechanisms and their contracts are absent and that no replacement protection
mechanism was introduced.

### P715-06: Full-codebase removal audit

Perform a purpose-based audit across all Cozy subsystems after implementation.

Acceptance requires:

- no Cozy-owned tamper-prevention logic;
- no Cozy-owned contamination-prevention logic;
- no Cozy-owned generated-artifact integrity protection;
- no publication/distribution integrity exception;
- no alternate mechanism implementing the removed semantics;
- no stale model, codec, field, diagnostic, scaffold, configuration, help,
  design/spec promise or test that requires those semantics.

The audit must inspect implementation behavior, not merely search for words
such as `hash`, `tamper`, or `integrity`.

## Explicit non-goals

- Do not redesign Cozy storage or publication.
- Do not introduce a new artifact lifecycle.
- Do not introduce security/signing/supply-chain infrastructure.
- Do not create a replacement receipt/provenance/integrity framework.
- Do not improve protection before deleting it.
- Do not preserve compatibility for removed protection metadata or behavior.
- Do not modify external repositories as part of this Phase.

## Relationship to Phase 71-71.4

Phase 71-71.4 must close according to their current accepted/planned contracts.
Phase 71.5 is a subsequent policy change and cleanup. It does not rewrite their
historical acceptance evidence.

Where Phase 71 documents say that a publication/distribution integrity
mechanism is retained, Phase 71.5 supersedes that product policy after Phase
71.4 closure and removes the retained mechanism.

## Validation and closure boundary

[Phase 71.5 Checklist](phase-71.5-checklist.md) is the completion ledger.

The Phase closes only after focused validation for remaining product behavior,
a complete purpose-based protection-removal audit, repository-full validation,
independent review, and a distinct release commit.

A passing build alone is insufficient. Review must explicitly check for
semantic substitutions: a deleted protection mechanism reimplemented using a
different representation is a Phase failure.
