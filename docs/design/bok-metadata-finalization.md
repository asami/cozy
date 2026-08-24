# BoK Metadata Finalization Design

Status: Phase 34 BOK34-01 working design

## Responsibility

`cozy bok build` currently performs generation and then copies generated
machine metadata into the public website before versioning the RDF graph and
writing the KnowledgeSource manifest. Phase 29 extracts that latter operation
as a reusable finalization service. The build path and the public command must
call the same service so the metadata contracts cannot diverge.

## Transaction boundary

The service reads the configured generated metadata root and stages every
allowlisted website mutation in a sibling temporary tree. It performs glossary,
RDF, component-reference, SIE, and KnowledgeSource validation against the
staged tree. Only after all validation succeeds does it replace the admitted
output paths at the website root. It never replaces the website root itself.

The implementation must reject symlinked roots and any configuration in which
the source, target, staging, or admitted path escapes its project root. For
configuration-path admission, `finalizeMetadata` first uses the validated
absolute normalized project path as the lexical root, rejecting lexical
escapes, non-directories, source symlinks, and symlink-traversing path segments
before staging. It then compares the admitted source's canonical real path
with the project's canonical real path to reject canonical escapes. The
resulting canonical source path is passed to source glossary declaration and
source RDF graph merge; those consumers do not resolve the unadmitted
configuration independently. This keeps an existing project-owned site
orchestration intact while allowing Cozy to update its machine-readable
handoff.

## Command integration

The BoK command dispatcher parses `finalize-metadata` with the same optional
project and strategy arguments as `build`, but its execution route accepts no
runner and has no external-command capability. CLI help describes the
generated inputs, the allowlisted outputs, and the distinction from `bok
build`.

## Validation approach

Executable specifications use a prepared generated-output fixture and compare
inventories and hashes before and after finalization. They cover successful
glossary-only and glossary-plus-RDF paths, missing declared glossary metadata,
unsafe roots, component-reference validation, rollback on failure, deterministic
repeat execution, and proof that no external runner is invoked. Existing
`CozyBokKnowledgeSourceSpec` remains the regression authority for the shared
manifest and graph contracts.
