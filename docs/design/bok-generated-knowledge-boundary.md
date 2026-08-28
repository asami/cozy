# Generated BoK Knowledge Boundary Design

Status: normative Phase 38 BOK38-01 design; implementation pending BOK38-02 through BOK38-06

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

1. The project explicitly declares the file below the admitted
   `src/main/extensions/rdf` root. Absence of the root is normal, and the
   implementation must not discover extension files by an arbitrary scan.
2. The normalized path is relative to that root, contains no absolute path,
   parent traversal, unsafe separator, or symlink escape, and resolves to the
   same project boundary.
3. The declaration has a supported schema and a stable semantic identity.
   It describes only project ontology, schema, or supplemental graph data
   that ordinary BoK inputs cannot derive.
4. The declaration and its contribution are validated before they are used
   to construct effective output. Ordering is deterministic and does not
   depend on filesystem enumeration order.
5. A declaration whose identity, path, graph node, edge, schema, or other
   authoritative identity collides with generated data or another extension
   fails deterministically. An extension cannot silently replace, shadow, or
   override generated authority.

The generated result remains authoritative for all derivable knowledge. An
extension can supplement a non-derived declaration only; it cannot provide a
second copy of an ordinary article, glossary, project, publication record,
standard UI, Manual, History dashboard, RDF output, or machine metadata
resource.

BOK38-05 will implement extension admission, schema validation, collision
diagnostics, and output integration against this contract. This design does
not claim that those mechanisms already exist.

## Legacy compatibility and removal

The legacy source directories `src/main/doxsite/manual`,
`src/main/doxsite/history`, `src/main/doxsite/rdf`, and
`src/main/doxsite/metadata`, together with a project-local copied standard UI
bundle, are never scaffolded. They are not final source authorities.

If a transitional reader is retained, it may recognize only the fixed legacy
shapes and must emit deterministic diagnostics for each recognized logical
path. The diagnostic must identify the logical legacy path, the replacement
authority (`src/main/doxsite`, `src/main/media`, `src/main/publication`, or
`src/main/extensions/rdf`), and the migration/removal condition without
leaking host-specific absolute paths into public output. It must not infer a
replacement graph overlay or silently prefer legacy data over generated
data. Unknown or unsafe legacy paths fail rather than being selected by
filesystem order. The stable diagnostic classes are
`bok.source.legacy.detected` (a deprecation warning for a recognized logical
path) and `bok.source.legacy.unsupported` (an error for an unknown, unsafe, or
unmigratable legacy shape).

The transitional reader is removable only after BOK38-06 accepts the
reorganized `bok-knowledgehub` driver with no legacy source path present or
consumed, and no admitted consumer still depends on a legacy path. At that
point BOK38-05 removes the reader and BOK38-07 records the closure evidence;
legacy paths remain rejected and are never re-scaffolded. BOK38-02 and
BOK38-05 are responsible for implementing and proving this behavior; this
Slice records the contract only.

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
