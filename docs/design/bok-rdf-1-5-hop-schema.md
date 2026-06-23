# BoK RDF 1.5+hop Information View

status=work-in-progress
published_at=2026-06-23

## Overview

`1.5+hop` is the concept that defines schema-guided RDF neighborhood expansion.
It is not a pure graph radius. The focused node and its direct 1-hop triples
are always shown, and Information-schema-required descriptive triples are added
only when they are needed to make visible RDF nodes understandable.

The model has three layers:

- `informationView` is the canonical metadata root for this interpretation.
- `informationView.concept` names the expansion concept, normally `1.5+hop`.
- `informationView.informationSchemas` defines concrete Information types.
  Different Information types can require different predicates and expansion
  rules.
- `informationView.predicateProfile` defines the predicate roles used by
  Information schemas.
- node-level `schema` metadata can override or refine the default expansion.
- Cozy consumes the Information View metadata and renders the graph;
  SmartDox/TKE/SIE should emit richer Information schema metadata over time.

## Concept Identity

Default Information View name:

```text
cncf-rdf-1.5-hop-information-view-v1
```

Default concept:

```text
1.5+hop
```

Default predicate profile:

```text
cncf-rdf-1.5-hop-v1
```

The Information View name identifies the RDF-node-as-Information view used by
the viewer. Its `concept` field identifies the graph expansion semantics. The
predicate profile is a supporting vocabulary profile, not the whole schema.
Concrete Information schemas are selected per RDF node.

## Information View

The Information View interprets each RDF node as Information. The rendered
interpretation is nested, not a flat list of dotted property names:

```text
informationView
  name
  concept
  label
  predicateProfile

information
  schema
  schemaLabel
  type
  category
  identity
  description
  links
  hierarchy
  provenance

schema
  required
  nodeDescription
  directional
  expansion
  fallback

predicate
  roles
```

The dotted names below are metadata attribute identifiers. UI should present
their hierarchy as grouped properties.

| Attribute | Meaning |
| --- | --- |
| `information.schema` | Concrete Information schema selected for the RDF node. |
| `information.type` | RDF node type from graph metadata, such as `uri`, `literal`, or a provider-specific type. |
| `information.category` | BoK category when the node can be assigned to one. |
| `information.identity` | Identity predicates such as `rdf:type`, `owl:sameAs`, `skos:exactMatch`, and primary RDF anchors. |
| `information.description` | Label, comment, definition, name, description, and summary predicates. |
| `information.links` | Cross-resource links such as `rdfs:seeAlso`, `schema:about`, and `schema:url`. |
| `information.hierarchy` | Broader/narrower or part/whole relationships. |
| `information.provenance` | Source, derivation, generation, and definition provenance. |
| `schema.required` | Node-specific predicates that must be included to explain this node. |
| `schema.directional` | Node-specific outgoing/incoming predicates required for explanation. |
| `schema.expansion` | Whether expansion used node schema metadata or fallback predicate profile. |

This Information view is what the Cozy RDF node detail panel should show.
Raw predicate lists remain available, but they should be grouped by these schema
attributes.

## Information Schemas

Information is a framework, not a single schema. A BoK can define multiple
Information schemas under the same 1.5+hop concept. Examples:

- glossary term Information;
- article Information;
- video artifact Information;
- RDF resource Information;
- TKE/SIE Information entity projections.

Each Information schema may define:

- a stable schema name;
- a human-readable label;
- matching rules such as category or node type;
- required predicates;
- descriptive predicates;
- outgoing and incoming predicates needed for explanation.

## Metadata Contract

Graph metadata may provide the Information View explicitly:

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
        "descriptivePredicates": ["rdfs:label", "skos:prefLabel", "schema:name"]
      },
      {
        "name": "term-information-v1",
        "label": "Glossary Term Information",
        "match": {
          "categories": ["glossary"]
        },
        "requiredPredicates": ["rdf:type", "skos:prefLabel"],
        "descriptivePredicates": ["skos:definition"]
      }
    ],
    "predicateProfile": {
      "name": "cncf-rdf-1.5-hop-v1",
      "roles": {
      "identity": ["rdf:type", "owl:sameAs", "skos:exactMatch"],
      "descriptive": ["rdfs:label", "skos:definition", "schema:description"],
      "link": ["rdfs:seeAlso", "schema:about"],
      "hierarchy": ["skos:broader", "skos:narrower"],
        "provenance": ["dcterms:source", "prov:wasDerivedFrom"]
      }
    }
  }
}
```

Node metadata may provide node-specific requirements:

```json
{
  "id": "https://example.com/resource",
  "label": "Resource",
  "node_type": "uri",
  "category": "concept",
  "informationSchema": "rdf-resource-information-v1",
  "schema": {
    "requiredPredicates": ["rdf:type", "rdfs:label"],
    "descriptivePredicates": ["skos:definition"],
    "outgoingRequiredPredicates": ["schema:about"],
    "incomingRequiredPredicates": ["schema:mentions"]
  }
}
```

Snake-case variants remain metadata compatibility inputs, but canonical schema
fields are camelCase.

## Expansion Rule

Given a focused RDF node `F`:

1. Include `F`.
2. Include every direct 1-hop edge where `F` is source or target.
3. Include the opposite endpoint nodes of those direct edges.
4. For each visible node, select its concrete Information schema.
5. Read node-level schema predicates and the selected Information schema
   predicates.
6. If both are absent, use the active predicate profile as fallback.
7. Include only edges whose predicates are required by the node schema,
   Information schema, or fallback profile.
8. Include newly reached nodes from those required edges.
9. Keep the expansion deterministic and bounded.

The result is `1.5+hop`: direct neighborhood plus schema-required explanatory
facts, not arbitrary 2-hop traversal.

## Responsibility Boundary

- SmartDox is the BoK metadata source of truth and should eventually emit graph
  schema metadata.
- CNCF owns the generic predicate profile and Information View naming
  convention for the 1.5+hop concept.
- TKE/SIE should project Information entity fields into concrete Information
  schemas and RDF node schema metadata.
- Cozy consumes the Information View metadata, renders graph/triples
  views, and should not rebuild RDF semantics by reparsing `.dox` sources.
