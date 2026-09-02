# BoK Metadata Finalization Design

Status: normative Phase 34 BOK34-02 design; Phase 38 source-boundary alignment implemented

## Phase 38 source-boundary alignment

The finalization service operates on generated working resources, not on a
second source tree. `src/main/doxsite` is the public, human-readable
SmartDox knowledge source plus adjacent public source metadata (`site.conf`
and category metadata). `src/main/media` and `src/main/publication` are
durable inputs, not generated website output. `src/main/extensions` is absent
by default; only explicitly declared, path-safe, schema-validated
non-derived declarations below `src/main/extensions/rdf` may supplement the
derived result.

Cozy and SmartDox derive effective RDF Turtle, JSON-LD, graph summary,
machine metadata, the standard Manual, the History dashboard, and the
standard UI during the build. Generated working resources are under
`doxsite.d`; finalized public RDF and machine metadata are under
`website.d/rdf` and `website.d/metadata`. None of these generated resources
is hand-maintained source authority. The standard source therefore contains
no `manual`, `history`, `rdf`, or `metadata` source directory and no copied
standard UI bundle. Public guidance belongs in the ordinary `guide` category;
repository operations, design, specification, and journal documents remain
outside `doxsite`.

This alignment does not change the finalizer's allowlist or SIE ownership
boundary. BOK38-02 through BOK38-06 implemented scaffold, derivation,
extension, migration, and driver behavior against the paired Phase 38
contract; this design records that closed local contract without claiming
downstream consumer acceptance.

## Responsibility

`cozy bok build` currently performs generation and then copies generated
machine metadata into the public website before versioning the RDF graph and
writing the KnowledgeSource manifest. Phase 29 extracts that latter operation
as a reusable finalization service. The build path and the public command must
call the same service so the metadata contracts cannot diverge. The public
build entry resolves a configuration-time safe source path before reading the
configured source `site.conf`; a missing in-project source is valid at this
stage because configuration resolution can use `SiteConfig.empty`. Before
execution, the build entry applies the finalizer's strict project-root/source
admission before invoking a runner or mutating build output; it passes the
strictly admitted source through a project-relative `BuildConfig` to every
build consumer.

## Transaction boundary

The service follows this pre-stage validation order, and the shared direct-copy
build route applies the same resource validation before copying into a website
locale target:

```text
ordinary build entry
  -> admit project root and configured source lexically (missing leaf allowed)
  -> load site.conf when present and derive project-relative BuildConfig
  -> strictly re-admit an existing source before execution
  -> source reads / runner calls / build mutations
  -> validate generated metadata
  -> direct-copy or staged finalization

staged finalization
  -> strictly admit an existing configured source
  -> validate glossary/component resources
  -> create stage
  -> copy/version/manifest
  -> atomic commit
```

Both routes validate the generated glossary with the canonical `TermIndex` decoder and
validate every present known manifest-publishable
`metadata/cncf/component-references/car.json` and `sar.json` with the existing
`cncf.component-reference-index.v1` validator before creating the stage or
copying any machine metadata. The component-index checks do not depend on RDF
graph reachability. Only those known `car.json` / `sar.json` indexes are
prevalidated; neither route scans arbitrary artifact trees or introduces a
resource schema. Remaining RDF, SIE, and KnowledgeSource checks run while the
allowlisted website mutation is staged. Only after all validation succeeds does
the finalizer replace the admitted output paths at the website root. It never
replaces the website root itself.

The implementation must reject symlinked roots and any configuration in which
the source, target, staging, or admitted path escapes its project root. At
configuration time, it uses the validated absolute normalized project path as
the lexical root, rejecting lexical escapes, existing non-directories, source
symlinks, and symlink-traversing path segments; an absent source leaf is
allowed only when its existing ancestor remains canonically below the project
root. At execution time, `build` and `finalizeMetadata` use the strict
existing-source admission, then compare the admitted source's canonical real
path with the project's canonical real path to reject canonical escapes. The
resulting strictly admitted source path is passed to source glossary
declaration and generated graph assembly; those consumers do not resolve the
unadmitted configuration independently. This keeps an existing project-owned
site orchestration intact while allowing Cozy to update its machine-readable
handoff. Any non-derived graph supplement is accepted only from an
explicitly declared, validated extension below
`src/main/extensions/rdf`; it cannot override or replace generated graph
authority.

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
