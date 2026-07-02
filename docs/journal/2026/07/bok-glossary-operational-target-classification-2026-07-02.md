# BoK Glossary Operational Target Classification

Date: 2026-07-02

This journal entry refines the glossary/CML alignment notes with a third axis:
whether a glossary term becomes a software operation target.

Earlier notes correctly separated BoK term type from CML representation. The
missing distinction is that CML `entity` is not the only way a term can become
operable in software. A term may become an entity, meaning a persistent object,
but it may also become a file, URL, external identifier, artifact, RDF node, or
other addressable target.

## Delta From Existing Journal

This entry changes the working interpretation from the earlier July 2 notes in
the following way.

What remains unchanged:

- BoK term type and CML representation are separate axes.
- CML `entity` means a persistent, identifiable domain object.
- `entityKind` is the preferred CML/CNCF classification axis for entities.
- Legacy `operationKind = "resource"` and `operationKind = "task"` remain only a
  compatibility bridge.
- The glossary dashboard should not become a full `EntityKind` reference table.

What is refined:

- Earlier notes treated the key distinction as:

  ```text
  BoK term type -> CML representation -> optional entityKind
  ```

- This entry inserts an explicit software-operation axis:

  ```text
  BoK term type -> software operation target -> CML/RDF representation
  ```

- Earlier notes could be read as if terms that become software-managed targets
  mostly become CML entities. This entry narrows that claim: CML entity is the
  persistent-object case, not the general term for every operable target.
- `resource` and `artifact` are no longer explained mainly through CML entity
  mapping. They may be files, URLs, external ids, RDF nodes, repository
  artifacts, or entities depending on how software operates on them.
- `actor` is not a child type of `entity` in BoK classification. It becomes a
  CML entity with `entityKind = actor` only when the actor itself is managed as a
  persistent system object.
- `role` remains a semantic/authorization/modeling role, not an entity by
  default.

What should change in UI guidance:

- The type analysis dashboard should stop implying an entity nesting hierarchy.
- The dashboard should show CML entity mapping as one representation path, not
  as the general rule for all operable terms.
- A future operation-target view should explicitly distinguish entity /
  persistent object, file, URL, external id, RDF node, artifact, event/message,
  and job/task targets.

## Refined Decision

Glossary analysis needs three separate axes:

```text
BoK semantic role -> software operation target -> CML/RDF representation
```

These axes must not be collapsed into a single enum.

## Axis 1: BoK Semantic Role

The BoK term type describes the term's role in the knowledge space.

Examples:

- `concept`
- `entity`
- `actor`
- `role`
- `resource`
- `artifact`
- `event`
- `action`
- `process`
- `task`
- `rule`
- `state`
- `scenario`

This axis is for authors and readers. It answers:

```text
What kind of knowledge is this term in the BoK?
```

It does not by itself decide how software stores or operates on the term.

## Axis 2: Software Operation Target

Some terms become direct software operation targets. Others remain explanatory
or analytical terms.

This axis answers:

```text
Can software operate on this term as a concrete target?
If yes, what kind of target is it?
```

Representative target classes:

| Target class | Meaning | Examples |
| --- | --- | --- |
| `none` | Not directly operated on by software | abstract concept, explanatory principle |
| `entity` | Persistent, identifiable object | customer, knowledge item, workflow instance |
| `file` | File-like target managed by path, object storage key, or file id | PDF, source file, exported report |
| `url` | Web-addressable target | article URL, external reference page, API endpoint |
| `external-id` | Identifier owned by another system | ISBN, Wikidata QID, DOI, ORCID |
| `rdf-node` | RDF-addressable knowledge node | URI resource, blank node, named graph node |
| `artifact` | Versioned build or publication output | CAR, SAR, video package, BoK publication asset |
| `message-event` | Event or message target | domain event, webhook payload, notification |
| `job-task` | Executable or schedulable work target | import job, indexing task, batch command |

`entity` is one operation target class. It is important, but it should not
absorb file, URL, external-id, RDF, artifact, event, or task targets. At this
axis, `entity` only means that the target is treated as a persistent object.
The persistence method is decided by the CML/runtime representation, not by the
operation-target classification.

## Axis 3: CML/RDF Representation

CML and RDF describe how a term is represented for modeling, runtime, or graph
knowledge.

Common mappings:

| Operation target | Typical representation |
| --- | --- |
| `entity` | CML `entity`, with `entityKind` when runtime purpose matters |
| `file` | CML entity with `entityKind = asset` or file metadata; RDF resource link |
| `url` | RDF resource, `schema:url`, `rdfs:seeAlso`, or external link metadata |
| `external-id` | RDF identity/alignment link; identifier metadata |
| `rdf-node` | RDF URI/blank node/named graph node |
| `artifact` | publication metadata, repository artifact metadata, or CML entity when managed |
| `message-event` | CML `event` or event metadata |
| `job-task` | CML entity with `entityKind = task`, operation, or job metadata |

CML `entity` means a persistent, identifiable domain object. Its runtime purpose
is classified by `entityKind`. This remains true, but `entity` is not the
general name for every software operation target.

The persistence method of a persistent object belongs to CML/runtime design.
The glossary operation-target axis should not decide whether persistence is
implemented by a database table, document store, object storage, event store,
file-backed repository, or another mechanism.

## Consequence for Existing Term Types

`actor`, `resource`, and `artifact` should not be displayed as nested subclasses
of `entity` in the glossary dashboard.

Their relationship to `entity` is conditional:

- An `actor` can be represented as a CML entity with `entityKind = actor` when
  the actor itself is managed by the system.
- A `resource` can be represented as a CML entity when it is managed as master,
  document, or asset data, but it may also be only a URL, file, external id, or
  RDF node.
- An `artifact` can be represented as a managed publication/build artifact. It
  may have repository metadata or file/blob metadata. It becomes a CML entity
  only when the system manages artifact records as domain objects.
- A `role` is usually not an entity. It may be represented as a value,
  powertype, entity attribute, authorization role, or policy concept.

For koto terms:

- An `event` may be CML `event`, a message target, or a persisted event record.
- A `process` may be a statemachine, workflow entity, or documentation-only
  flow.
- A `task` may be a job/task target or CML entity with `entityKind = task`.
- A `rule` is a constraint, guard, validation, policy, or CML `rule`, not an
  entity by default.

## Dashboard Implication

The glossary dashboard should avoid presenting `entity` as the parent of
`actor`, `resource`, and `artifact`.

The better structure is:

```text
1. BoK term type
   - concept / entity / actor / role / resource / artifact / ...

2. Operation target
   - not operated directly
   - entity / persistent object
   - file
   - URL
   - external id
   - RDF node
   - repository artifact
   - event/message
   - job/task

3. Representation link
   - CML entity/value/event/operation/statemachine/rule
   - RDF URI/triple/node
   - publication/artifact metadata
```

The type analysis card should focus on authoring routes and term meaning.
The project/CML and RDF cards should show operation-target and representation
details.

## Metadata Direction

Future metadata should represent operation target explicitly instead of inferring
it from `term_type`.

Possible shape:

```hocon
operation_target {
  kind: entity | file | url | external-id | rdf-node | artifact | message-event | job-task | none
  managed: true | false
  identifier: ...
  system: ...
}
```

This is a planning note, not an immediate schema change. The current
implementation can continue to render existing `term_type`, `rdf_refs`, and
`cml` metadata. The important rule is that UI text and dashboard grouping should
not imply:

```text
software operation target == CML entity
```

The correct relationship is:

```text
CML entity is the persistent-object kind of software operation target.
```

## Next Implementation Guidance

- Keep `entity` route wording tied to CML's persistent identifiable object
  meaning.
- Remove or soften any UI that makes `actor`, `resource`, or `artifact` look
  like child types of `entity`.
- Add an operation-target view later if the BoK needs to show which terms become
  DB records, files, URLs, RDF nodes, artifacts, events, or jobs.
- Keep detailed `entityKind` summaries in Project/CML connection views or Term
  Hub details, not as the primary glossary type dashboard.
