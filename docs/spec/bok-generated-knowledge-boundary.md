# Generated BoK Knowledge Boundary Specification

Status: authoritative Phase 38 BOK38-01 specification; BOK38-05 extension admission complete

## Contract scope

This specification defines which project paths are source authority and which
resources are derived by Cozy and SmartDox. It is paired with
`docs/design/bok-generated-knowledge-boundary.md`. The contract is normative
for subsequent Phase 38 implementation; its implementation status is not
claimed here.

## Source paths and authority

The following source rules are mandatory:

| Path | Authority | Required boundary |
| --- | --- | --- |
| `src/main/doxsite` | Public human-readable SmartDox knowledge and adjacent public source metadata | May contain `site.conf`, category metadata, and ordinary public subject/type content; must not contain generated machine metadata or framework-owned surfaces |
| `src/main/media` | Durable article-media input | Is input only; is not generated website output |
| `src/main/publication` | Durable Cozy-managed publication-registry input | Is input only; is not generated website output |
| `src/main/extensions/rdf` | Optional explicit non-derived ontology, schema, or supplemental graph declarations | The `extensions` root is absent by default; only explicitly declared, admitted declarations are allowed |

`src/main/doxsite` is the authority for public human-readable knowledge and
its adjacent `site.conf` and category metadata. Public guidance, when needed,
uses the ordinary `guide` subject category. Repository operations, design,
specification, and journal documents remain outside `doxsite`.

The default source shape must not include `manual`, `history`, `rdf`, or
`metadata` directories below `src/main/doxsite`, and must not include a
copied standard UI bundle. A generated file with one of those names does not
become source authority merely because it is present in a generated output.

## Derived resources and output locations

Cozy and SmartDox SHALL derive the following from ordinary BoK inputs,
validated publication/component evidence, SIE handoffs, and valid explicit
extensions where permitted:

- effective RDF Turtle;
- effective RDF JSON-LD;
- the RDF graph summary and machine-readable metadata;
- the standard Manual;
- the History dashboard; and
- the standard site UI.

Generated working resources SHALL be under `doxsite.d`. Finalized public RDF
and machine-readable metadata SHALL be under `website.d/rdf` and
`website.d/metadata`. Generated resources SHALL NOT be treated as
hand-maintained source authority, and a generated graph summary SHALL NOT
become a competing source graph overlay.

`src/main/media` and `src/main/publication` remain durable inputs throughout
this process. They SHALL NOT be interpreted as website output roots.

## Extension admission

An implementation conforming to this specification SHALL admit an extension
only when:

- the project explicitly names it in the exact `bok.extensions.rdf` list;
  Cozy SHALL read only that list, and an empty list SHALL neither require nor
  inspect or scan `src/main/extensions/rdf`;
- it is a nonempty relative `.json` path below `src/main/extensions/rdf`; the
  root, parents, and file are existing non-symbolic-link paths with no absolute
  path, traversal, unsafe separator, nonregular file, symbolic link, or
  real-path escape;
- its JSON-object envelope has `schemaVersion` exactly
  `cozy.bok.rdf-extension.v1`, a nonempty `id`, `kind` exactly `ontology`,
  `schema`, or `supplemental-graph`, and `nodes` and `edges` arrays;
- every node has nonempty `id`, `label`, and `node_type` and no `componentRef`,
  while every edge has nonempty `source`, `predicate`, and `target`; and
- it supplements the generated graph after the SIE handoff merge and before
  graph-summary versioning, using declaration-id, node-id, and edge-identity
  ordering independent of configuration-list or filesystem order.

The implementation SHALL reject, rather than select or overwrite, duplicate
extension declaration ids, node ids, and edge `(source, predicate, target)`
identities with `bok.extension.identity.collision`. It SHALL reject an
extension when the generated graph is missing or an extension node or edge
collides with generated authority using `bok.extension.override.forbidden`.
Valid declarations SHALL not use the permissive SIE deep-merge collision
behavior. Extensions SHALL NOT replace an
ordinary source article, glossary, bibliography, scenario, project,
publication record, standard Manual, History dashboard, standard UI, RDF
output, or machine metadata resource. A valid extension supplements only
non-derived declarations.

The required diagnostic classes are deterministic and stable:

| Code | Condition |
| --- | --- |
| `bok.extension.path.invalid` | The declaration is outside the admitted `extensions/rdf` root or is not path-safe |
| `bok.extension.schema.invalid` | The declaration is malformed or fails its supported schema |
| `bok.extension.identity.collision` | The declaration collides with another extension or generated identity |
| `bok.extension.override.forbidden` | The declaration attempts to replace generated authority |

BOK38-05 implements these rules. Its focused
`CozyBokMetadataFinalizationSpec` validation and lightweight Step review are
accepted; this does not claim driver acceptance or Phase completion.

## Legacy source compatibility

The following legacy source shapes SHALL never be scaffolded:

- `src/main/doxsite/manual`;
- `src/main/doxsite/history`;
- `src/main/doxsite/rdf`;
- `src/main/doxsite/metadata`; and
- a project-local copied standard UI bundle below `doxsite`.

No legacy source reader SHALL be installed, used, or scanned by BOK38-05
before BOK38-06 driver acceptance. Legacy paths remain rejected by the final
source contract, are never scaffolded, and cannot provide a graph-overlay
fallback. Any legacy-reader implementation requires a separately approved
contract after that acceptance boundary.

## Responsibility and non-reparse invariants

Cozy owns source admission, build orchestration, metadata finalization, and
standard generated site surfaces. SmartDox owns parsing of the public
human-readable source and initial derived metadata/RDF production. SIE owns
its semantic integration runtime and consumes declared handoff resources.
These responsibilities SHALL remain separate.

Cozy and SIE SHALL NOT reparse RDF source files to reconstruct a missing
generated graph summary. Effective graph and metadata resources come from the
derived build outputs or valid explicit extension declarations under the
contract above. A missing or invalid generated resource remains a
deterministic failure; it is not repaired by reading a legacy source overlay.

## Phase boundary

This Slice specifies the final authority model only. BOK38-02 through
BOK38-06 remain responsible for scaffold/configuration, generation,
extension/migration, and driver implementation. No code behavior, validation,
review, publication, deployment, or Phase closure is claimed.
