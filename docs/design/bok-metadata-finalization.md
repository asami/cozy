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


## 2026-10-09 S21 Sample ZIP and BoK metadata direct output amendment

P715-02-S21-[CozySampleDistributor](../../src/main/scala/cozy/publication/CozySampleDistributor.scala)-ZIP-[CozyBokSieMetadata](../../src/main/scala/cozy/bok/CozyBokSieMetadata.scala)-FINALIZATION-DIRECT is
**implemented-focused-validated-unreviewed** under the [frozen S21 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-implementation-manifest.json).
This dated amendment supersedes the earlier staging, atomic installation,
failure-preservation and rollback guarantees for these selected outputs.
Earlier dated contracts, evidence, refusals, repairs and acceptance remain history.
At the initial editing handoff, no validation count, independent review or
completion was claimed.

Sample distribution freezes the ordered eligible source-file inventory before
opening each selected final archive. All planned collection and per-sample
archive paths are excluded by their lexical absolute-normalized paths, so
selected generated ZIP outputs do not become archive inputs. The final ZIP
is written directly with the standard ZipOutputStream/ZipEntry codec, including
required CRC behavior and zero entry timestamps. Source ordering, exclusions,
project/warehouse/name/version/publication-path defaults, exact collection and
sample coordinates and slug-collision prevalidation remain. Dry-run writes
nothing and no external runner is invoked. Each actual selected generation
writes current contents. An IOException propagates and may leave a partial
archive or earlier completed archives; the final output is not deleted or
restored on failure.

BoK finalization keeps initial project/configured-source/generated-root and
website-root admission, source/target non-overlap and known generated resource
validation. The canonical TermIndex decoder and CAR/SAR component-reference
index checks still run before copying or clearing selected website metadata.
The existing destination inventory admits ordinary selected file/directory
types and enumerates only the old allowlisted generated files. Those files
are deleted directly, then current generated metadata is copied directly to
the final website root. This is replacement of the generated allowlisted set:
missing current resources do not reuse stale website graphs, indexes, terms or
knowledge-source manifests. Directory roots, rendered HTML, assets and
unselected files remain outside that clearing. Finalization creates no stage,
backup, saved output copy or rollback transaction.

The nine file allowlist remains `rdf/site.ttl`, `rdf/site.jsonld`,
`metadata/rdf/graph.json`, `metadata/glossary/terms.json`,
`metadata/bibliography/bibliography.json`, `metadata/scenarios/scenarios.json`,
`metadata/tags/tags.json`, `metadata/sie/integration.json` and
`metadata/cncf/knowledge-source.json`. The six directory allowlist remains
`metadata/repository/car`, `metadata/cncf/component-references`,
`metadata/catalog/projects`, `metadata/projects`,
`metadata/artifacts/repository` and `metadata/releases`.
Both copy helpers admit the actual selected target and parent before creating
or copying it; source symlink/nonregular rejection and destination containment
remain. The shared ordinary/locale build-copy route and public signatures
remain. Authored source authority, generated resources, declared glossary
requirements and explicitly declared extension path/schema/collision/generated
authority rules retain their meanings. The existing pipeline still copies
resources, merges SIE, applies declared RDF extensions, versions and validates
the graph and its node/edge/component references, then writes the
`cncf.knowledge-source.v1` manifest with its existing resource/sourceRef semantics.
There is no schema or lifecycle replacement. Initial resource decoder failures
precede copying; later missing terms, extension, RDF or I/O failures propagate
and may leave already replaced allowed output or created directories.
Successful repeated generation remains deterministic.

Only the seven existing late-error cases lose protection-only manifest
sentinels and preservation assertions; their real domain diagnostics and
unrelated HTML assertions remain. The two existing malformed glossary/CAR
case titles say before copying. No case, suite or proof harness is added.
At the initial editing handoff, the parent-selected existing six-suite focused
validation and independent assurance were pending; the editor ran no validation
or self-review.

Original finite R715-06/08/09/10/11/13/14/15 and other unfinished removal duties,
including BoK ProjectPublisher/RepositoryMetadata/RepositoryPages,
WarehouseIndexer and remaining Build/website/config helpers, remain **OPEN**.
Concrete external E715-01..04 reader reconciliation, final current JA34 native
generation/assembly, actual audible hearing, end card and EOF, independent M2
cycles 6/7/8/10, whole-Step/full-Phase review, acceptance, exact local commits
and the distinct release remain **OPEN**. Prior S14 JA34 evidence remains dated;
the parent must assess actual S21 main-route relevance. Repository-full SBT
and broader integration remain **deferred-not-run to PHASE-71.7**. Stage Status
triples, checklist checkboxes and top-level aggregate metadata are unchanged.
No per-Slice acceptance, external compatibility, publication, upload,
deployment, successor execution or Phase closure is claimed.

The affected contract and implementation references are [BoK finalization specification](../spec/bok-metadata-finalization.md), [BoK finalization design](bok-metadata-finalization.md),
[existing CozyBokMetadataFinalizationSpec](../../src/test/scala/cozy/CozyBokMetadataFinalizationSpec.scala), [CozySampleDistributor](../../src/main/scala/cozy/publication/CozySampleDistributor.scala) and [CozyBokSieMetadata](../../src/main/scala/cozy/bok/CozyBokSieMetadata.scala).


### Actual S21 focused validation — 2026-10-09

Current S21 state is **implemented-focused-validated-unreviewed**.
The [actual S21 focused-validation result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-focused-validation-result.json) records **131 successful tests across 6 completed suites**.
The first sequential task group passed **45 tests in 2 suites**; the second
passed **86 tests in 4 suites**. Both groups have **0 failed, canceled, ignored
or pending tests and 0 aborted suites**. SBT and wrapper exits were **0/0**,
and the shared serial lock was **released**. Compiler warnings, errors,
fixture diagnostics and runtime known Logback warning lines were all **zero
for this invocation**. The dated S19 Logback lines remain
`HYG-P715-S19-LOGBACK-001`, pending independent Step classification; no broader
warning-free claim or warning suppression is made.

The [original pre-render origin diagnosis](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-pre-render-origin-diagnosis01.json) retains the original current S21 history-preflight refusal:
the old runner omitted exactly one trailing LF from the native source, and the
unchanged helper rejected it before renderer, submission or SBT execution.
The parent admitted exactly one fresh successor under the already deployed
revision 17, as recorded by the [one-successor relation](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-origin-successor-relation.json). It used the **same original
unused attempt, permission, authority, subject, argv and effects**; permission
was not renewed. The successor supplied the exact native source and obtained
`origin_present=true` in the [actual successor native-source acceptance](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-origin-successor-native-acceptance.json). It then followed the normal
first scoped platform review and shared serialized SBT route. No shared skill
was edited. These origin facts describe the command's preflight and execution;
they do not establish audiovisual playback or hearing.

The parent assessment is limited to the actual selected Sample ZIP writes and
BoK website metadata finalization. Existing publication, metadata finalization,
knowledge-source, site-routing, build-target and BoK suites cover the ordinary
selected paths and callers. No narration, frame, render, audio, assembly or
end-card implementation changed, and no current native, audible or whole-Phase
acceptance is inferred. Prior S14 JA34 remains dated evidence only. Final
current representative JA34 generation/assembly, actual audible hearing,
end-card/EOF and whole-Phase main-route relevance remain **OPEN**.

Original finite removals, remaining R715-13 WarehouseIndexer,
ProjectPublisher/RepositoryMetadata/RepositoryPages/build/helper work and other
unfinished finite duties, E715-01..04, independent M2 cycles 6/7/8/10,
whole-Step/full-Phase review, acceptance, exact local Step commits and the
distinct release remain **OPEN**. Repository-full SBT and broader integration
remain **deferred-not-run to PHASE-71.7**. The initial implementation handoff,
S21 behavior and GWT clauses, all prior history and the admitted S20 selection
correction remain recorded. All seven Stage Status triples, checklist
checkboxes and top-level aggregate metadata retain their existing state.
This factual projection performs no new validation, independent review,
workflow transition, per-Slice acceptance/commit, Step/Phase closure or release.
