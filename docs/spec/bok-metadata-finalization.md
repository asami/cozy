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
