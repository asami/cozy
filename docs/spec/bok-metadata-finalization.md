# BoK Metadata Finalization Specification

Status: normative Phase 34 BOK34-02 specification; Phase 38 source-boundary alignment implemented

## Phase 38 source-boundary alignment

The finalization command consumes generated working resources and publishes
their validated effective form. The source authority is the public,
human-readable `src/main/doxsite` tree and its adjacent public source
metadata (`site.conf` and category metadata). Durable article-media input
belongs in `src/main/media`; durable Cozy-managed publication-registry input
belongs in `src/main/publication`. Neither root is generated website output.

The `src/main/extensions` root is absent by default. Only explicitly declared
and validated non-derived ontology, schema, or supplemental graph
declarations below `src/main/extensions/rdf` may contribute to finalization.
An extension must be path-safe and deterministic, and a collision with
generated or another extension identity must fail rather than override
generated authority. The default source must not contain `manual`, `history`,
`rdf`, or `metadata` directories, or a copied standard UI bundle.

Cozy and SmartDox derive effective RDF Turtle, JSON-LD, graph summary,
machine metadata, the standard Manual, the History dashboard, and the
standard UI. Generated working resources are under `doxsite.d`; finalized
public RDF and machine metadata are under `website.d/rdf` and
`website.d/metadata`. Generated resources are never hand-maintained source
authority. Public guidance uses the ordinary `guide` category, while
repository operations, design, specification, and journal documents remain
outside `doxsite`.

BOK38-02 through BOK38-06 implemented this source-boundary alignment.
This specification records the closed local scaffold, derivation, extension,
migration, driver, and validation boundary without claiming downstream
consumer acceptance.

## Command

Cozy provides the following public command:

```text
cozy bok finalize-metadata [<project-dir>] [--strategy wip|draft|preview|production]
```

The optional project argument and strategy resolve by the same project
configuration rules as `cozy bok build`. The command operates on an already
generated configured `doxsite.d`/`website.d` pair; it is not a site build.

## Inputs and outputs

The admitted ordinary SmartDox source and durable project inputs remain the
source authority. Cozy copies and validates only generated machine-readable
finalization resources, then writes the versioned graph summary and
`cncf.knowledge-source.v1` manifest. It must not reconstruct missing
SmartDox glossary metadata or treat a generated output as a new source
overlay.

The output mutation allowlist is limited to these paths below the configured
website root:

- `rdf/site.ttl` and `rdf/site.jsonld`;
- `metadata/rdf/graph.json`;
- `metadata/glossary/terms.json`, `metadata/bibliography/bibliography.json`,
  `metadata/scenarios/scenarios.json`, and `metadata/tags/tags.json` when
  present in the generated input;
- `metadata/repository/car/`, `metadata/cncf/component-references/`,
  `metadata/catalog/projects/`, `metadata/projects/`,
  `metadata/artifacts/repository/`, and `metadata/releases/`;
- `metadata/sie/integration.json` when an SIE handoff is configured; and
- `metadata/cncf/knowledge-source.json`.

No other website path may be created, deleted, or replaced. In particular,
rendered HTML, direct assets, SmartDox/Antora/Arcadia output, media, and
project-owned files remain unchanged.

## Configured source admission

Configuration-time admission runs while `BuildConfig.create` resolves the
project configuration. Cozy validates the selected project as an existing
non-symbolic-link directory and uses its absolute normalized path as the
lexical project root. It admits the configured source lexically below that
root, rejecting an outside-root path, an existing non-directory, the source
directory itself being a symbolic link, or a symbolic-link/non-directory path
segment on the way to the source. A safe in-project source leaf may be absent:
Cozy validates the existing parent segments and nearest existing ancestor for
canonical containment, then treats a missing `site.conf` as an empty site
configuration. The admitted source is stored as a normalized project-relative
`BuildConfig` value, and no source `site.conf` is read unless that file exists.

Execution-time admission remains strict. Before an ordinary `cozy bok build`
invokes its runner or mutates build output, and before `finalize-metadata`
reads admitted source declarations or generated RDF metadata, Cozy requires the configured source
to be an existing non-symbolic-link directory. It then compares the source's
canonical real path with the project's canonical real path to reject canonical
escapes. All source reads, build consumers, and output mutations use only this
strictly admitted execution-time configuration.

## Safety and failure behavior

Before any ordinary build runner or mutation, Cozy applies the configured
source admission above. Before any shared direct-copy build route copies
machine metadata, and before the staging finalizer creates a staging directory
or declares a `cncf.knowledge-source.v1` resource, Cozy validates every
generated `metadata/glossary/terms.json` with the canonical `TermIndex` decoder.
It also
validates every present generated
`metadata/cncf/component-references/car.json` and `sar.json` that could be
declared by the manifest with the established
`cncf.component-reference-index.v1` validator. This component-index validation
is unconditional on both routes: it runs even when the current RDF graph has
no `componentRef` node. A malformed JSON document or decoder/semantic failure
identifies its generated resource path in the diagnostic, before direct-copy
output or a KnowledgeSource manifest can be written.

Before mutation, Cozy validates that configured input and output roots are
directories under the resolved project, are not symbolic links, and do not
overlap unsafely. Every admitted output must remain below the canonical
website root.

Finalization is failure-atomic for the allowlisted output set: a validation or
write failure leaves the prior admitted output unchanged. Repeating the command
over unchanged inputs produces byte-identical admitted output.

If the source declares glossary terms but generated
`metadata/glossary/terms.json` is absent, finalization fails with the existing
SmartDox metadata diagnostic. Cozy does not infer or rebuild terms from source
documents. RDF graph and component-reference validation retain the existing
`cozy.rdf-graph-summary.v1`, `cncf.component-reference-index.v1`, and
`cncf.knowledge-source.v1` contracts. A non-derived graph declaration is
admissible only through the explicit Phase 38 extension boundary below
`src/main/extensions/rdf`; a legacy source overlay is not an alternate
authority.

## Prohibited behavior

`finalize-metadata` must not invoke SmartDox, Dox, Antora, Arcadia, Docker,
media generation, publication, upload, deployment, or project-owned workflow
commands. It must not read rendered HTML as a metadata source, scan arbitrary
artifact trees, or activate a Textus BoK handoff.


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

The affected contract and implementation references are [BoK finalization specification](bok-metadata-finalization.md), [BoK finalization design](../design/bok-metadata-finalization.md),
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
