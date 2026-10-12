# Artifact Protection Removal

Status: adopted future policy; implementation and acceptance belong to
[Phase 71.5](../phase/phase-71.5.md), after Phase 71.4 closes.
Decision: 2026-10-03.

## Execution and failure model

Cozy is a source-rooted workbench operated under the user's control. Source
coordination belongs to Git, worktrees and pull requests, not to a second
Cozy-managed exclusion or integrity protocol. Generated outputs can be
regenerated from authoritative sources. Cozy-owned detection, prevention and
recovery for artifact tampering, contamination, concurrent interference and
partial generated-output pollution are outside this policy's failure model.

Ordinary missing inputs, malformed data, unavailable producers, renderer
failures and filesystem I/O failures remain operation failures. Report them
without inventing a recovery protocol. An unsuccessful operation must not be
reported as successful. This policy does not guarantee preservation, atomic
replacement or rollback of generated outputs after an I/O failure; recovery is
regeneration. It does not authorize deleting or overwriting authored inputs,
unselected files or destinations outside the requested operation.

## Removal boundary

Remove Cozy-owned artifact-protection behavior and its protection-only models,
fields, codecs, diagnostics, configuration, scaffolds, help and tests. Local
generation and publication/distribution use the same rule. The earlier Phase
71 `retain-only-integrity` classification grants no Phase 71.5 exception.

Remove digest/checksum/signature comparison, byte-copy verification,
protection-oriented snapshots, drift/race checks, repeated reads, CAS, locks,
backups, rollback, fsync and atomic replacement when they implement the excluded
artifact-protection behavior. Do not reimplement that behavior with timestamps,
revisions, receipts, provenance, metadata, equality checks or another name.
No replacement safety protocol, legacy protection adapter or disabled branch
is authorized.

Classification is by the concrete purpose and caller, not by an identifier or
API name. This is an implementation work list, not a new integrity registry or
runtime retention taxonomy. A mixed-purpose construct must lose its protection
part without erasing independent product semantics.

## Independent product semantics

The following are not artifact-protection exceptions:

- Paths, ownership and the selected destination define where an operation may
  read/write. Preserve containment, ordinary path validation and the boundary
  between authored sources and generated output.
- Parsers, required schema fields unrelated to protection, CML semantics,
  target/runtime compatibility, roles/locales and actual renderer/media
  validity define whether an input is usable. Preserve these checks; do not
  turn removal into acceptance of malformed or wrongly selected input.
- Declared producer dependencies, existence and modification times select
  ordinary rebuild/reuse. They must not prove unchanged bytes, approval or
  race-free execution. Uncertain reuse selects regeneration.
- Actual source paths, semantic IDs, versions, attribution and production/review
  facts may remain where they have an independent meaning. They must not be
  substitute artifact-identity or immutability evidence.
- Ordinary collection hashing is not an artifact-management mechanism.
- PNG/ZIP format requirements implemented by standard codecs/libraries remain
  format behavior. Use a standard codec when removing a custom encoder would
  otherwise produce an invalid file; do not wrap it in additional Cozy
  integrity verification. Cozy checksum sidecars and integrity manifests do
  not become mandatory standards merely because the existing Cozy schema
  requires them.

Do not add or strengthen any of these behaviors as a substitute for protection.
The shared SBT execution lock, platform permission review and Git-native
coordination are outside Cozy product code and outside this removal scope.

## Consumer contracts and deletion order

Remove an implementation and reconcile its Cozy-owned callers, emitted schema,
decoder, scaffold and executable specifications in the same compile-valid
change. A parser accepting an old optional extra through ordinary loading is
not a dedicated compatibility promise; do not add legacy digest handling.

Inspect external readers before changing a shared producer contract. One known
edge is Cozy's component-knowledge carrier and CNCF's
`ComponentKnowledgeCarrierCodec`: the current CNCF codec requires exactly
`carrierSchema`, `consumerContractSchema`, `logicalPath` and `sha256`.
Removing that producer field cannot be called compatible with that reader.
sbt-cozy also currently supplies `runtime.cncf.descriptor.sha256` to Cozy;
its actual argument/consumer behavior must be checked before that boundary is
changed. Neither observation authorizes changing those repositories.

If a concrete reader prevents the selected removal, record its exact producer
and consumer paths, contract/field, expected versus proposed behavior and
smallest separately owned change. Stop that affected implementation boundary
before leaving an unsupported producer contract. Continue unrelated removals
only when they remain independently compile-valid. Do not invent an exception,
empty/fake digest, new schema/version, adapter or external acceptance claim.
The affected checklist stays open until the consumer contract is actually
reconciled under separate authority; full Phase closure cannot conceal it.

## Validation

Phase 71.5 completes the selected removal work and runs the affected existing
Executable Specifications for retained generation/reuse/adoption, packaging,
path/domain selection and ordinary I/O failure behavior. Keep these assertions;
fix retained-behavior failures rather than deleting them to obtain a pass.
Delete or amend only expectations for the protection intentionally removed.

Do not add tests proving hash absence, absence of tamper detection or unchanged
inputs. Do not build new test infrastructure or broader coverage gates for
removal. Ordinary diff review against a finite removal list, actual existing
specification results and normal independent review are sufficient. Real
integration and the aggregate full suite belong to Phase 71.7.

## Policy transition

The user's 2026-10-05 revision keeps completed removals in 71.4 and transfers
unfinished management-hash removal/inventory to 71.5. Removal is no longer a
71.4 completion condition; that Phase closes on video-ending implementation and
Executable Specifications, with normal review/commits. 71.5 applies this policy
without replacement protection and uses existing Specs for retained behavior.
71.6 owns execution/encoder implementation and corresponding Specs. 71.7 owns
real integration, benchmark and one aggregate full suite. Original acceptance
history, review/repair counts and committed deferred handoffs are preserved;
current declarations record the new full-suite owner without inventing results.
