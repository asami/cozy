# BoK / SIE Integration Contract

Status: normative Phase 14 contract

## Purpose

SIE means `textus-semantic-integration-engine`. This contract defines the
machine-readable boundary between Cozy BoK publication and SIE semantic
integration without assigning SIE runtime semantics to Cozy.

The SIE baseline for BoK KnowledgeSource ingestion is:

```text
64e035d Add BoK KnowledgeSource ingestion
```

## Responsibility Boundary

Cozy owns:

- BoK build and publication integration;
- publication, project, and repository metadata validation;
- explicit integration diagnostics;
- BoK navigation for Project, Term, Tag, and RDF resources;
- publication of the BoK KnowledgeSource manifest.

SIE owns:

- semantic integration runtime behavior;
- Information-schema interpretation and materialization;
- reading resources declared by a BoK KnowledgeSource manifest;
- publication of SIE-derived Information and RDF handoff resources.

SmartDox remains the source parser and initial metadata/RDF producer. Cozy must
not silently reconstruct missing SmartDox output or regenerate missing SIE
semantic output. SIE must not scrape rendered BoK HTML.

## Cozy To SIE Handoff

A generated BoK site publishes:

```text
metadata/cncf/knowledge-source.json
```

The manifest uses `schemaVersion = cncf.knowledge-source.v1`, `kind = bok-site`,
and `sourceRef.kind = bok-site`. Resource `href` values are relative to the
KnowledgeSource base URI. The manifest declares only files that were actually
published.

`site.metadata.id` is the canonical manifest `id` and `sourceRef.value`.
`site.metadata.key` is accepted as a compatibility alias. If neither is set,
Cozy derives a deterministic identifier from `site.metadata.name`.
`site.metadata.url` supplies `sourceRef.uri` and is normalized as a site base
URI with a trailing slash.

The first required resource kind is `glossary-terms` when
`metadata/glossary/terms.json` exists. RDF resources use the existing
`rdf-jsonld`, `rdf-turtle`, and `rdf-graph-summary` kinds. Rendered HTML and a
`/.well-known/cncf-knowledge.json` compatibility document are not part of this
contract.

## SIE To Cozy Handoff

An SIE-linked Project or publication points to an explicit SIE handoff base.
Cozy reads `metadata/cncf/knowledge-source.json` below that base. Cozy does not
scan an SIE source repository or infer outputs from directory names.

The SIE handoff reuses the `cncf.knowledge-source.v1` envelope with:

- `kind = sie-projection`;
- `sourceRef.kind = sie-projection`;
- a stable projection identifier in `sourceRef.value`;
- the handoff base URI in `sourceRef.uri` when available;
- relative resource `href` values.

The manifest may declare these resource kinds:

- `sie-provenance`: producer, input, version, and generation evidence used for
  freshness diagnostics;
- `information-schema`: SIE-owned Information schema metadata;
- `information-instances`: SIE-owned materialized Information metadata;
- `rdf-jsonld`, `rdf-turtle`, and `rdf-graph-summary`: SIE-derived RDF output.

BK14-07 defines the resource file paths and JSON schemas. Until a resource kind
is defined there, Cozy may report it but must not interpret its semantic
content. Cozy consumes only manifest-declared resources and must not regenerate
an absent resource.

## Compatibility

Phase 14 accepts `cncf.knowledge-source.v1`. A future incompatible major schema
requires an explicit reader. Unknown optional resource kinds are skipped with a
warning; they do not authorize filesystem discovery.

The current `metadata/glossary/terms.json` top-level shape remains:

```json
{
  "terms": []
}
```

SIE treats term `rdf_refs` as supplementary evidence or relationship
candidates, not as confirmed RDF anchors.

## Diagnostics

No SIE configuration means no SIE diagnostic and preserves existing BoK build
behavior. Once a Project or publication declares an SIE handoff, Cozy reports
stable diagnostic categories:

| Code | Default severity | Condition |
| --- | --- | --- |
| `sie.handoff.missing` | error | The configured manifest is absent. |
| `sie.handoff.invalid` | error | The manifest cannot be decoded or violates the envelope contract. |
| `sie.handoff.schema.unsupported` | error | The manifest uses an unsupported schema version. |
| `sie.handoff.resource.missing` | error | A declared required resource is absent. |
| `sie.handoff.resource.unsupported` | warning | An optional resource kind is unknown. |
| `sie.handoff.stale` | warning | Declared provenance does not match the expected Project/publication input. |
| `sie.handoff.freshness-unknown` | warning | Provenance is insufficient to determine freshness. |

Strict validation may promote stale or unknown freshness warnings to errors.
Diagnostics identify the configured handoff, manifest, resource kind, and
expected Project/publication reference without exposing local source paths in
public pages.

## Implementation Order

1. BK14-03 emits the BoK KnowledgeSource manifest.
2. BK14-04 and BK14-05 register SIE Project/publication and CAR/SAR references.
3. BK14-07 defines and consumes the SIE projection resource schemas.
4. BK14-08 renders navigation from validated metadata only.
