# RDF 1.5+hop Neighborhood

status=note
created_at=2026-06-22
scope=shared-concept
applies_to=Cozy BoK, CNCF, SIE/TKE

## Overview

`1.5+hop` is a shared RDF graph navigation rule for inspecting the meaning of a
focused RDF node. It is used by Cozy BoK as a viewer behavior, but the concept
is intended to be shared with CNCF knowledge projection and SIE/TKE semantic
integration.

It is not a mechanical graph distance such as "1-hop plus every adjacent
node's next hop". The rule is:

- show the focused RDF node;
- show its direct 1-hop RDF triples;
- additionally show the RDF triples required to explain the meaning of the
  focused node and its directly related nodes;
- decide those additional triples from node schema metadata when available;
- use a small default descriptive predicate set only as fallback.

The `+` means schema-driven expansion: the graph may include more than strict
1-hop only when those extra triples are necessary to describe the selected RDF
node.

## Shared Concept

`1.5+hop` should be treated as a common graph-explanation concept:

- Cozy BoK uses it for term-centric RDF navigation and dashboard graph viewing.
- CNCF uses it as a knowledge projection/explanation rule around
  `KnowledgeNode`, `KnowledgeRelationship`, `Evidence`, and provenance.
- SIE/TKE uses it for Information-centered semantic integration and RDF anchor
  review.

The common contract is not "show 2-hop". The common contract is "show direct
neighborhood plus schema-required meaning triples".

## Rationale

BoK RDF is used as knowledge navigation, not only as raw RDF artifact display.
When a user clicks an RDF node, the useful question is usually:

- What is this node?
- What does it mean in this BoK?
- Which terms, articles, resources, or schema fields define it?
- Which directly related resources matter for understanding it?

A pure 1-hop graph is often too small because a neighboring node may need its
label, type, definition, or source to be intelligible. A pure 2-hop graph is
often too large because it pulls unrelated graph structure into the view.

`1.5+hop` is the compromise: direct neighborhood first, then schema-required
meaning triples only.

## TKE Information Entity Model Basis

The Information framework used by `1.5+hop` is expected to start from the
Information entity model used by TKE. That model is not copied into Cozy or CNCF
as a concrete class dependency. Instead, its meaning is projected into concrete
Information schemas and RDF node schema metadata.

In practice, TKE Information fields such as RDF anchors, information links,
identifiers, classification, source evidence, review state, and RDF note define
which RDF predicates are required to explain a node. When those Information
schema fields are materialized into RDF, the materializer should also emit node
schema metadata such as:

- required predicates that identify the node;
- descriptive predicates that explain the node to a reader;
- outgoing required predicates that describe owned/linked information;
- incoming required predicates that explain why another node points here;
- source/evidence predicates needed to make the graph auditable.

Cozy BoK consumes this metadata from SmartDox/SIE-produced graph metadata. CNCF
should consume the same idea as a knowledge explanation profile without taking a
compile-time dependency on TKE.

## Viewer Interaction

The RDF graph page should behave as follows:

1. The default graph view shows the full filtered graph, or category/term
   filtered graph when URL query parameters are present.
2. Clicking an RDF node opens a floating detail panel for quick inspection.
3. The detail panel shows node facts such as ID, category, type, connections,
   Information View interpretation, and related edge examples. The
   interpretation should be displayed as nested `informationView`,
   `information`, `schema`, and `predicate` property groups rather than as one
   flat dotted-property list.
4. The detail panel provides explicit actions to open the 1.5+hop neighborhood
   graph and the full-page RDF node detail.
5. Activating the neighborhood action replaces the graph canvas with the
   Information View guided focused neighborhood graph.
6. The full-page detail lives at `rdf/node.html?id=<node-iri>` and uses the
   same `metadata/rdf/graph.json` source as the graph viewer. It is intended
   for long IRI values, larger Information View interpretation, and complete
   related-edge review.
7. Reset focus returns to the previous full filtered graph.

Clicking a node should not immediately switch the whole graph to the focused
view. The click is inspection; the focused neighborhood is an explicit action.

## Schema Metadata

Each RDF graph node may carry schema metadata that declares which predicates are
needed to describe that node. This metadata is the RDF-facing projection of one
concrete Information schema inside the 1.5+hop Information View framework, not
an RDF ontology replacement.

The canonical Cozy-side schema note is
`docs/design/bok-rdf-1-5-hop-schema.md`. In short, `informationView` is the RDF-node-as-Information framework,
`informationView.concept` names the `1.5+hop` expansion semantics,
`informationView.informationSchemas` contains the concrete Information schemas
selected per node, and `informationView.predicateProfile` is the supporting role
map used by those schemas.

Graph metadata may carry an explicit Information View. Cozy currently uses
`cncf-rdf-1.5-hop-information-view-v1` as the built-in Information View,
`1.5+hop` as its concept, and `cncf-rdf-1.5-hop-v1` as the built-in fallback
predicate profile. It prefers `graph.json` Information View metadata when
present.

```json
{
  "informationView": {
    "name": "cncf-rdf-1.5-hop-information-view-v1",
    "label": "CNCF RDF 1.5+hop Information View",
    "concept": "1.5+hop",
    "attributes": [
        "information.schema",
        "information.type",
        "information.category",
        "information.identity",
        "information.description",
        "information.links",
        "information.hierarchy",
        "information.provenance",
        "schema.required",
        "schema.directional",
        "schema.expansion"
    ],
    "informationSchemas": [
      {
        "name": "rdf-resource-information-v1",
        "label": "RDF Resource Information",
        "match": {
          "nodeTypes": ["uri", "literal"]
        },
        "requiredPredicates": ["rdf:type"],
        "descriptivePredicates": ["rdfs:label", "schema:name"]
      }
    ],
    "predicateProfile": {
      "name": "cncf-rdf-1.5-hop-v1",
      "roles": {
      "identity": ["rdf:type", "owl:sameAs", "schema:sameAs"],
      "descriptive": ["rdfs:label", "rdfs:comment", "schema:name"],
      "link": ["rdfs:seeAlso", "schema:about"],
      "hierarchy": ["skos:broader", "skos:narrower"],
        "provenance": ["dcterms:source", "prov:wasDerivedFrom"]
      }
    }
  }
}
```

Supported metadata field names in the current Cozy viewer are:

```json
{
  "id": "https://example.com/resource",
  "label": "Resource",
  "node_type": "uri",
  "category": "concept",
  "informationSchema": "rdf-resource-information-v1",
  "requiredPredicates": ["rdf:type", "rdfs:label", "skos:definition"],
  "descriptivePredicates": ["schema:name", "schema:description"],
  "schema": {
    "requiredPredicates": ["rdf:type", "rdfs:label"],
    "descriptivePredicates": ["skos:definition"],
    "outgoingRequiredPredicates": ["schema:name"],
    "incomingRequiredPredicates": ["schema:about"]
  }
}
```

Snake-case variants are also accepted for generated metadata compatibility:

- `required_predicates`
- `descriptive_predicates`
- `schema.required_predicates`
- `schema.descriptive_predicates`
- `schema.outgoing_required_predicates`
- `schema.incoming_required_predicates`

## Expansion Rule

Given a focused RDF node `F`:

1. Include `F`.
2. Include all 1-hop edges where `F` is source or target.
3. Include the opposite endpoint nodes of those 1-hop edges.
4. For every visible node, select its concrete Information schema.
5. Inspect node-level predicates and selected Information-schema-required
   predicates.
6. Include only edges whose predicate matches the required or descriptive
   predicate set.
7. Include newly reached nodes from those required edges.
8. Repeat required expansion with a small hard limit to avoid infinite expansion.
9. Mark nodes by role:
   - `focus`: the selected RDF node;
   - `near`: the focused node and direct 1-hop nodes;
   - `schema`: nodes pulled in only because schema-required triples need them.

The expansion should remain deterministic and bounded.

## Default Descriptive Predicates

When node schema metadata is absent, the viewer uses a conservative predicate
profile fallback. The current built-in profile is `cncf-rdf-1.5-hop-v1` plus
TKE/SIE anchor predicates.

The CNCF generic profile covers:

- `rdf:type`
- `rdfs:label`
- `rdfs:comment`
- `skos:prefLabel`
- `skos:altLabel`
- `skos:broader`
- `skos:narrower`
- `skos:exactMatch`
- `owl:sameAs`
- `rdfs:seeAlso`
- `rdfs:isDefinedBy`
- `dcterms:source`
- `dcterms:isPartOf`
- `dcterms:hasPart`
- `prov:wasDerivedFrom`
- `prov:generatedAtTime`
- `schema:name`
- `schema:title`
- `schema:description`
- `schema:summary`
- `schema:url`
- `schema:about`
- `schema:sameAs`
- `schema:isPartOf`
- `schema:hasPart`
- `schema:memberOf`

The TKE/SIE Information anchor extension adds:

- `textus:primaryRdfAnchor`
- `skos:closeMatch`

This fallback is intentionally small. Rich BoK RDF navigation should eventually
come from SmartDox/TKE/SIE-generated predicate profile and node schema metadata,
not from hard-coded Cozy heuristics.

## System Boundaries

TKE/SIE should own the Information-centered schema and materialization rules
that decide which facts explain an Information node.

SmartDox should be the BoK publication source of truth for RDF graph metadata:

- `doxsite.d/metadata/rdf/graph.json`
- future node schema metadata used for RDF graph navigation
- term-to-RDF and category-to-RDF relationships

Cozy should remain the BoK consumer and renderer:

- copy RDF metadata to `website.d/metadata/rdf/graph.json`;
- render the RDF graph page;
- implement bounded client-side filtering and focused graph display;
- avoid reparsing `.dox` files or rebuilding RDF semantics itself.

CNCF should own generic knowledge projection boundaries:

- do not import TKE-specific Information classes into CNCF core;
- expose generic node/relationship/evidence/provenance explanation metadata;
- allow SIE/TKE providers to project Information-schema-derived RDF metadata
  into CNCF-visible knowledge structures.

## Current Cozy Implementation

The current Cozy RDF page implements:

- graph/triples special page layout;
- category and term filters;
- SVG graph rendering from `metadata/rdf/graph.json`;
- click-to-open floating RDF node detail panel;
- explicit `1.5+hop` focused neighborhood action;
- Information View guided expansion using available node schema metadata;
- fallback descriptive predicates when schema metadata is absent;
- role classes for focused, near, and schema-expanded nodes.

This implementation is a viewer-level bridge until SmartDox emits richer RDF
node schema metadata.

## Open Questions

- What should the canonical SmartDox node schema metadata format be?
- Should schema-required predicates be defined globally per RDF type, per node,
  or both?
- Should `1.5+hop` include inverse descriptive predicates by default, or only
  when schema declares `incomingRequiredPredicates`?
- How should very large RDF graphs cap node and edge counts while preserving
  useful semantic context?
- Should the RDF triples view follow the same focused node and schema expansion
  selection as the graph view?
