# BoK / SIE Information Handoff

Status: normative Phase 14 schema

## Purpose

This document defines the SIE-owned JSON resources consumed by Cozy after SIE
has projected a BoK KnowledgeSource. It refines the SIE-to-Cozy side of
`bok-sie-integration-contract.md`.

Cozy reads only the local handoff root explicitly configured at:

```yaml
bok:
  projects:
    <project-ref>:
      sie:
        path: target/sie/projections/<projection>
```

The path is private operation configuration. It is not copied into publication
metadata. `cozy bok build` never fetches the public `handoff_base`. When the
configured handoff lives below `target/`, Cozy preserves that explicit
directory across its normal target cleanup so that a preceding SIE projection
remains available to the build.

## Manifest

The handoff root contains:

```text
metadata/cncf/knowledge-source.json
```

The envelope is `cncf.knowledge-source.v1` with `kind = sie-projection` and a
matching `sourceRef.kind/value`. Resource `href` values must be safe relative
filesystem paths below the handoff root. Absolute paths, URI schemes, query or
fragment suffixes, backslashes, and parent traversal are invalid. Each canonical
resource kind may be declared at most once; duplicate singleton declarations
make the handoff invalid rather than selecting one implicitly.

Every resource declaration requires non-empty `kind`, `href`, and `mediaType`
fields. A symbolic link is accepted only when its resolved target remains below
the configured handoff root. An unknown optional kind is reported as
`sie.handoff.resource.unsupported` and skipped without resolving or reading its
`href`.

The canonical Phase 14 resources are:

| Kind | Canonical path | Schema |
| --- | --- | --- |
| `sie-provenance` | `metadata/sie/provenance.json` | `sie.provenance.v1` |
| `information-schema` | `metadata/sie/information-schema.json` | `sie.information-schema.v1` |
| `information-instances` | `metadata/sie/information-instances.json` | `sie.information-instances.v1` |
| `rdf-graph-summary` | `metadata/rdf/graph.json` | `cozy.rdf-graph-summary.v1` |

`information-schema` and `information-instances` are required for a usable SIE
projection handoff. Provenance is optional, but its absence produces
`sie.handoff.freshness-unknown`. RDF JSON-LD and Turtle may also be declared as
evidence; Cozy does not reparse them to reconstruct graph metadata.

## RDF Graph Summary

When present, `metadata/rdf/graph.json` is a JSON object with
`schemaVersion: "cozy.rdf-graph-summary.v1"`, `kind: "rdf-graph-summary"`,
and a `sourceRef` copied from the BoK site identity. It always contains the
generated `nodes` and `edges` arrays and a boolean `truncated` flag. Node and
edge metadata stays source-attributable; Cozy does not use it to infer new
relations. Existing SmartDox graph fields remain compatible as additional
fields in this versioned producer contract.

The BoK producer may preserve a node-level `componentRef` object in this
schema only for nodes whose `node_type` is `component-reference`. The object
contains required `kind` and `name` fields and optional `organization` and
`version` fields. Cozy validates that object against the same generated
`component-reference-index` resources advertised by the BoK KnowledgeSource
manifest before publishing the effective graph summary. A missing,
ambiguous, mismatched, malformed, or wrong-node-type `componentRef` is an
invalid producer handoff. `componentRef` is existence-only metadata for a
consumer such as Textus BoK Knowledge Map; it does not authorize Cozy or SIE
to infer component identity from node labels, enrich component detail, query
CBD Support, or display CBD-owned capability, dependency, compatibility,
operation, manual, or usage data.

## Provenance

```json
{
  "schemaVersion": "sie.provenance.v1",
  "projection": "nict-knowledgehub",
  "projectRef": "nict-knowledgehub",
  "producer": "textus-semantic-integration-engine",
  "generatedAt": "2026-07-13T00:00:00Z"
}
```

`projection` and `projectRef` identify the expected Project input. A mismatch
is stale handoff evidence. Missing identity evidence makes freshness unknown.

## Information Schema

```json
{
  "schemaVersion": "sie.information-schema.v1",
  "projection": "nict-knowledgehub",
  "informationSchemas": [
    {
      "name": "knowledge-item-information-v1",
      "label": "Knowledge Item Information",
      "match": {"categories": ["technology"]},
      "requiredPredicates": ["rdf:type"],
      "descriptivePredicates": ["rdfs:label"]
    }
  ]
}
```

`informationSchemas` uses the existing BoK RDF Information View schema shape.
The resource must define at least one entry. `name` is required and is the merge
identity. Canonical fields are camelCase; snake-case variants remain
compatibility input where the existing RDF viewer already accepts them.

## Information Instances

```json
{
  "schemaVersion": "sie.information-instances.v1",
  "projection": "nict-knowledgehub",
  "instances": [
    {
      "id": "knowledge-item-001",
      "schema": "knowledge-item-information-v1",
      "label": "Knowledge Item Information",
      "summary": "SIE materialized knowledge item.",
      "rdfNode": "https://example.com/knowledge-item",
      "category": "technology",
      "termRefs": ["technology:knowledge-item"],
      "scenarioRefs": ["scenario:knowledge-review"],
      "projectRefs": ["nict-knowledgehub"],
      "tags": ["technology.sie"]
    }
  ]
}
```

`id`, `schema`, and `label` are required, and `id` is unique within the resource.
`schema` must name an entry declared by the same projection's
`information-schema` resource; an undefined schema is an invalid handoff
diagnostic and the instance is excluded from effective metadata and UI.
`rdfNode` is the confirmed RDF anchor chosen by SIE. When it is absent, Cozy
assigns a deterministic SIE Information URN for navigation; it does not promote
glossary `rdf_refs` to confirmed anchors. Term, scenario, Project, and tag
references are navigation metadata, not inferred RDF triples.

## Effective Metadata

Cozy publishes validated, path-safe metadata at:

```text
metadata/sie/integration.json
```

The schema is `cozy.bok.sie-integration.v1`. It contains public Project and
handoff identities, resources, Information schemas, Information instances, and
stable diagnostics. It never contains the configured local handoff path.

Cozy merges declared Information schemas, instances, and `rdf-graph-summary`
into the effective `metadata/rdf/graph.json` without parsing RDF source files:

- SmartDox graph fields remain authoritative when an SIE instance shares an
  RDF node identity;
- SIE linkage is added under the node's `sie` object;
- byte-equivalent schemas and edges are deduplicated deterministically;
- conflicting Information schemas with one name fail explicitly;
- edges with the same source, predicate, and target but different metadata fail
  explicitly instead of selecting one declaration;
- SIE graph nodes and edges are accepted only from the declared graph-summary
  JSON resource.

## UI Contract

- Project pages show validated SIE Information and diagnostics.
- Term pages show SIE Information whose `termRefs` identify the term.
- Scenario pages show SIE Information whose `scenarioRefs` identify the
  scenario.
- Tag pages list SIE Information through instance `tags`.
- RDF node details show the Information schema, SIE projection, instance id,
  and related term/scenario/Project/tag references.
- RDF graph and node detail retain the existing neighborhood action.
- No SIE metadata means no empty SIE UI container.
