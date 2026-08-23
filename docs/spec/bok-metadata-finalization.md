# BoK Metadata Finalization Specification

Status: Phase 29 working specification

## Command

Cozy provides the following public command:

```text
cozy bok finalize-metadata [<project-dir>] [--strategy wip|draft|preview|production]
```

The optional project argument and strategy resolve by the same project
configuration rules as `cozy bok build`. The command operates on an already
generated configured `doxsite.d`/`website.d` pair; it is not a site build.

## Inputs and outputs

The generated SmartDox metadata and RDF input remains the source of truth.
Cozy copies and validates only machine-readable finalization resources and
then writes the versioned graph summary and `cncf.knowledge-source.v1`
manifest. It must not reconstruct missing SmartDox glossary metadata.

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

## Safety and failure behavior

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
`cncf.knowledge-source.v1` contracts.

## Prohibited behavior

`finalize-metadata` must not invoke SmartDox, Dox, Antora, Arcadia, Docker,
media generation, publication, upload, deployment, or project-owned workflow
commands. It must not read rendered HTML as a metadata source, scan arbitrary
artifact trees, or activate a Textus BoK handoff.
