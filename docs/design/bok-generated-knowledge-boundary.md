# Generated BoK Knowledge Boundary Design

Status: normative Phase 38 BOK38-01 design; BOK38-05 extension admission complete

## Purpose

This design fixes the authority boundary for a Cozy BoK project. It separates
the human-readable SmartDox source and durable project inputs from the RDF,
machine metadata, generated site surfaces, and framework-owned resources that
Cozy and SmartDox derive during a build. The boundary is a design authority;
the implementation stages named below have not been completed by this
document.

## Source authorities

The project `src/main` boundary is:

```text
src/main/
├── doxsite/       public human-readable SmartDox source and adjacent metadata
├── media/         durable article-media input
├── publication/   durable Cozy-managed publication-registry input
└── extensions/    optional explicit non-derived declarations
```

`src/main/doxsite` is the public, human-readable SmartDox knowledge source.
Its adjacent public source metadata consists of `site.conf` and category
metadata such as `category.yaml`. Ordinary articles, glossary, bibliography,
scenario, project, and subject-category content are authoritative inputs.
The directory is not a root for generated machine metadata or
framework-owned surfaces.

`src/main/media` is durable article-media input. `src/main/publication` is
the durable Cozy-managed publication registry input. Neither directory is
generated website output, and neither is a replacement source for the other.

`src/main/extensions` is absent by default. The only admitted extension
boundary is an explicitly declared `src/main/extensions/rdf` tree, and only
for project ontology, schema, or supplemental graph declarations that cannot
be derived from ordinary BoK inputs. The extension boundary does not turn
generated output into source authority.

Public project guidance belongs in the ordinary `guide` subject category.
Repository operations, design, specification, and journal documents remain
outside `src/main/doxsite`; document lifecycle categories are not a public
subject-category axis.

## Generated authorities

Cozy and SmartDox derive effective resources from the admitted source
authorities and their validated handoffs. The derived set includes:

- effective RDF Turtle and JSON-LD;
- the RDF graph summary and other machine-readable metadata;
- the standard Manual;
- the History dashboard; and
- the standard site UI.

Generated working resources are placed under `doxsite.d`. Finalized public
RDF and machine metadata are placed under `website.d/rdf` and
`website.d/metadata`. Other generated site surfaces remain generated
resources under the website output boundary. No generated resource is a
hand-maintained source of truth, and no generated resource creates a second
authority competing with `src/main/doxsite`, `src/main/media`,
`src/main/publication`, or an admitted extension declaration.

The standard source must not contain `manual`, `history`, `rdf`, or
`metadata` source directories, or a copied standard UI bundle. In particular,
`metadata/rdf/graph.json` in a generated working or public output directory
is not a source graph overlay under `src/main/doxsite`.

## Explicit extension contract

An extension declaration is valid only when all of the following hold:

1. The project explicitly opts in through the exact configuration list
   `bok.extensions.rdf`. Cozy reads only that list; when it is empty, the
   absent `src/main/extensions/rdf` root is not required or inspected, and no
   extension directory scan is permitted.
2. Every listed declaration is a nonempty relative `.json` path below
   `src/main/extensions/rdf`. The root, every parent, and the file must be
   existing non-symbolic-link paths; absolute paths, traversal, unsafe
   separators, nonregular files, symbolic links, and real-path escapes fail
   with `bok.extension.path.invalid`. Public diagnostics use logical paths.
3. A declaration is one JSON object with
   `schemaVersion: "cozy.bok.rdf-extension.v1"`, a nonempty `id`, `kind` equal
   to `ontology`, `schema`, or `supplemental-graph`, and `nodes` and `edges`
   arrays. Each node has nonempty `id`, `label`, and `node_type` and must not
   declare `componentRef`; each edge has nonempty `source`, `predicate`, and
   `target`. Malformed envelopes or fields fail with
   `bok.extension.schema.invalid`.
4. Cozy applies validated declarations after the SIE handoff merge and before
   final graph-summary versioning. A generated graph remains required and
   authoritative; extensions cannot create a missing graph or collide with a
   generated node or edge identity, and those attempts fail with
   `bok.extension.override.forbidden`.
5. Duplicate declaration ids, node ids, and `(source, predicate, target)` edge
   identities across declarations fail with
   `bok.extension.identity.collision`. Valid declarations are appended by
   declaration id, nodes by id, and edges by identity. This merge does not use
   the permissive SIE deep-merge collision behavior.

The generated result remains authoritative for all derivable knowledge. An
extension can supplement a non-derived declaration only; it cannot provide a
second copy of an ordinary article, glossary, project, publication record,
standard UI, Manual, History dashboard, RDF output, or machine metadata
resource.

BOK38-05 implements only this extension admission, schema validation,
collision diagnostics, and output integration. Its focused
`CozyBokMetadataFinalizationSpec` validation and lightweight Step review are
accepted; this does not claim driver acceptance or Phase completion.

## Legacy compatibility and removal

The legacy source directories `src/main/doxsite/manual`,
`src/main/doxsite/history`, `src/main/doxsite/rdf`, and
`src/main/doxsite/metadata`, together with a project-local copied standard UI
bundle, are never scaffolded. They are not final source authorities.

No legacy source reader is installed in BOK38-05 before BOK38-06 driver
acceptance. Legacy paths remain neither scaffolded nor source authority, and
Cozy does not read, scan, or infer a legacy graph overlay during this Slice.
Any later legacy-reader decision requires a separate approved contract.

## Responsibility boundary

Cozy owns source admission, deterministic generation orchestration, metadata
finalization, and generated site surfaces. SmartDox parses the public
SmartDox source and produces its derived resources. SIE consumes declared
KnowledgeSource and handoff resources and retains its own semantic
responsibility. No part of this design assigns SIE runtime behavior to Cozy,
and no part authorizes re-parsing RDF as a fallback for a missing generated
graph summary.

## Non-goals

This design does not change SmartDox syntax, SIE runtime semantics, media
composition, publication hosting, UI product design, or repository operation
rules. It does not claim scaffold, doctor, migration, generator, validation,
driver acceptance, publication, or deployment implementation.
