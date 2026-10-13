# CAR Development Runtime Evidence

Decision: 2026-10-05, Phase 71.4 A05.
Status: settled implementation boundary; selected validation passed 104/104, independent review pending.

## Mutable development inputs

A development directory supplies current required files and an explicit runtime
classpath. Its evidence document declares ordered paths and exact CAR coordinates
and CNCF runtime range. These declarations carry no content certificate: valid
classpath whitespace, ordering, duplicates, ABI JSON formatting and recompilation
can change while the observable development metadata remains the same.

The writer retains `write(projectRoot, runtimeClasspathFile, output): Unit` and
`cncf.car-development-runtime-manifest.v2`. Evidence order is runtime classpath,
generated descriptor, optional component knowledge, then ABI. Local evidence
SHA-256, classpath logical SHA-256 and the aggregate integrity block are retired
without a replacement token, schema alias, timestamp receipt or adapter.

Current path containment, regular nonempty evidence files, UTF-8 classpath parsing,
valid path operands and existing entries remain required. Canonical descriptor
schema, project/descriptor coordinates and ABI contract checks remain independent
of the evidence projection. Descriptor and carrier preparation retain their
existing actual contract reads and named carrier byte verification.

## Integrity and failure boundaries

P71001-A04 protects actual named packaged CAR entries. P71001-A08 protects the
named `component-knowledge.json` transport bytes and its descriptor declaration.
Neither purpose authorizes hashes over mutable development evidence. Their
existing packaged mismatch behavior is retained.

Preflight failure preserves the previous final development manifest. The final
writer retains atomic replacement with its existing move fallback and temporary
cleanup. Descriptor/carrier preparation may have occurred before a later failure;
this is no transaction or rollback guarantee across all prepared artifacts.

## External adoption

CNCF owns the development-runtime reader. Its adoption must account for absent
`evidence[].sha256`, `evidence[].logicalSha256` and root `integrity` fields while
retaining required nonhash contracts. This is a separate read-only handoff from
the Cozy producer change. External compatibility and complete runtime admission
are unproven; no external mutation or fabricated digest is part of A05.

The paired contract is [file-update-management.md](../spec/file-update-management.md)
and [CozyCarRuntimeManifestSpec](../../src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala).
Global removal audit, complete site/integration acceptance and Step/Phase closure
remain open.
