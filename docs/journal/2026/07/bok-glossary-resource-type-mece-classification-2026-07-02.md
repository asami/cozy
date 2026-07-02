# BoK Glossary Resource Type MECE Classification

Date: 2026-07-02

This journal entry refines the previous operation-target classification. The
main correction is that `entity`, `resource`, `file`, `URL`, and `artifact`
should not be shown as peer `term_type` values.

The previous working model was useful for discovering the problem, but it still
mixed several axes. In particular, `entity` and `resource` were not MECE:

- `entity` described a persistent object.
- `resource` described something referenced or operated on.
- A persistent object can also be a referenced or operated-on resource.

Therefore `resource` should become the broader term type, and `entity` should
become a subtype of `resource`.

## Decision

Use `resource` as the BoK term type for things that become identifiable targets
in BoK, Project, RDF, or software operation.

Use `resource_type` to classify what kind of resource it is.

```yaml
term_type: resource
resource_type: entity | file | url | external_id | rdf_node | artifact | dataset | service_endpoint | ...
```

This makes the classification closer to MECE:

```text
term_type
  concept
  resource
  actor
  role
  event
  action
  process
  task
  rule
  state
  scenario
```

Then:

```text
resource_type
  entity
  file
  url
  external_id
  rdf_node
  artifact
  dataset
  service_endpoint
```

## Three-Space Model

The clearer framing is:

```text
Knowledge Space -> Term Space -> Project Space Mapping
```

The glossary should distinguish three spaces.

### Knowledge Space

This space answers:

```text
What knowledge narrative does the BoK describe?
How do articles and scenarios describe that narrative, and what machine-readable
knowledge representation is emitted from it?
```

There are two useful senses:

- Broad sense: the BoK narrative described by articles and scenarios.
- Output side: RDF graph, RDF nodes, triples, and RDF relationships generated
  from or linked to that narrative.

In the dashboard, "Knowledge Space" should show:

- narrative grounding: article and scenario links;
- RDF output: RDF graph/node/triple links;
- knowledge relationships: term-to-term and evidence links.

### Term Space

This space answers:

```text
How is this term classified and curated as a glossary term?
```

Primary classification:

- `term_type`
- `resource_type` when `term_type = resource`
- glossary definition, summary, aliases, and term relationships

This is where `actor`, `role`, `concept`, `event`, `rule`, and `resource` are
classified as glossary terms.

### Project Space

This space answers:

```text
How is this term represented, implemented, operated, or persisted by a project?
```

Mapping targets include:

- CML entity
- CML value / powertype
- CML event
- CML operation
- CML statemachine
- CML rule
- publication/artifact metadata
- RDF representation used by project workflows

This is where `entityKind`, persistence design, command/event handling, and
artifact publication belong.

## Mapping Rule

Do not treat Project Space mapping as a replacement for Knowledge Space or Term
Space classification.

The canonical shape is:

```text
Knowledge Space
  article/scenario/RDF/term relationships
  broad BoK narrative

Term Space classification
  term_type
  resource_type

Project Space mapping
  cml.kind
  cml.name
  cml.project
  entityKind or other CML subtype when applicable
  publication/artifact link when applicable
```

Examples:

```yaml
# Actor in knowledge space, entity in project space
term_type: actor
cml:
  - kind: entity
    name: User
```

```yaml
# Resource in knowledge space, external identifier before project persistence
term_type: resource
resource_type: external_id
```

```yaml
# Resource in knowledge space, entity in project space
term_type: resource
resource_type: entity
cml:
  - kind: entity
    name: Book
```

The first classification says how the term behaves in the knowledge space.
The second classification says how the glossary curates the term.
The third mapping says how the project implements or connects it.

## Meaning

`resource` means:

```text
An identifiable target that can be referenced, operated on, linked, or managed
by BoK, Project/CML, RDF, publication metadata, or software.
```

`entity` means:

```text
A resource that is treated as a persistent object.
```

The persistence method of an `entity` is not decided by the glossary. It belongs
to CML/runtime design.

## Examples

```yaml
# Book managed as a project/domain object
term_type: resource
resource_type: entity
```

```yaml
# ISBN used as an external identifier
term_type: resource
resource_type: external_id
```

```yaml
# Report PDF
term_type: resource
resource_type: file
```

```yaml
# Wikidata resource
term_type: resource
resource_type: rdf_node
```

```yaml
# CAR or BoK publication package
term_type: resource
resource_type: artifact
```

## Difference From Previous Notes

Previous notes used a three-axis model:

```text
BoK semantic role -> software operation target -> CML/RDF representation
```

That remains useful conceptually, but the UI should not expose it as a flat set
of peer cards.

The refined classification is:

```text
BoK term_type -> resource_type -> CML/RDF/publication representation
```

Important changes:

- `entity` is no longer a peer of `resource` in BoK `term_type`.
- `file`, `url`, `external_id`, `rdf_node`, and `artifact` are not term types.
- `resource` is the term type for identifiable targets.
- `resource_type` handles the subtype that was previously mixed into operation
  target classification.
- CML `entity_type` or `entityKind` remains a later CML-side classification, not
  the BoK glossary's first classification.

## Actor / Role Linkage to Entity

`actor` and `role` remain BoK `term_type` values. They should not be moved under
`resource_type`.

However, they may link to CML entities when the project implements them as
persistent objects.

This is a linkage pattern, not a BoK term-type reclassification:

```text
BoK term_type: actor
  -> may link to CML entity when the actor is managed as a persistent object

BoK term_type: role
  -> may link to CML entity, value, powertype, authorization role, or policy
     model depending on implementation needs
```

Examples:

```yaml
# Actor kept as actor in the glossary, implemented as an entity in CML
term_type: actor
cml:
  - kind: entity
    name: User
```

```yaml
# Role kept as role in the glossary, implemented as a role master entity
term_type: role
cml:
  - kind: entity
    name: RoleDefinition
```

```yaml
# Role kept as role in the glossary, implemented as a powertype/value
term_type: role
cml:
  - kind: powertype
    name: ProjectRole
```

This means:

- BoK classification answers "what role does this term play in the knowledge
  space?"
- CML linkage answers "how is this term represented or implemented by the
  project?"
- A term can keep `term_type = actor` or `term_type = role` even when its CML
  representation is `entity`.

The dashboard should therefore show actor/role CML entity links as project
implementation links, not as evidence that actor/role are resource subtypes.

## UI Implication

The glossary dashboard should avoid a general "software operation target" card.
It should instead provide a Project Terms or Resource Terms card that answers:

```text
Which glossary resources are project-facing?
Which are resource_type = entity?
Which are non-entity resources such as file, URL, external id, RDF node, or artifact?
Which resources still lack resource_type?
```

Suggested card structure:

```text
Project Terms / Resource Terms

Resource terms
  total resource terms

Entities
  resource_type = entity
  CML entity linked
  CML entity pending

Non-entity resources
  file
  url
  external_id
  rdf_node
  artifact
  dataset
  service_endpoint

Resource type undecided
  term_type = resource but resource_type missing
```

The card should be operational:

- show counts;
- show a few example terms;
- show missing `resource_type` work;
- link to Project/CML, RDF, and term classification work surfaces.

It should not primarily explain the theory.

## Metadata Direction

Future glossary metadata should add `resource_type` for resource terms.

Possible shape:

```hocon
term_type: resource
resource_type: entity
```

or:

```hocon
term_type: resource
resource {
  type: entity
}
```

Open naming question:

- `resource_type` is simple and dashboard-friendly.
- `resource.type` is more structured and easier to extend with resource-specific
  metadata later.

The UI can start by reading both forms if needed, but the canonical name should
be chosen before source authoring guidance is updated.
