# CML Semantic Metadata v1 Design

## Boundary

This layer is a narrow publication boundary:

```text
validated CmlSemanticFoundation.Catalog
  -> CmlSemanticMetadata.Envelope
  -> canonical versioned JSON
  -> strict JsValue reader
  -> CmlSemanticFoundation.build
  -> validated consumer Catalog
```

The Catalog is admitted before publication and is admitted again after reading.
The envelope carries only the v1 foundation vocabulary: qualified identities,
source attribution, explicit absence, and semantic references. It deliberately
does not project Structure, Classification, Workflow, StateMachine, Use Case,
Terminology, Event Storming, Capability, or relation ontology detail.

## Roles and failure boundary

`CmlSemanticMetadata` owns the immutable envelope, direct builder, structured
publication diagnostics, writer, canonical renderer, and reader delegation.
Its constructor is not a public construction or copy bypass. It separately
rejects null descriptive kind/name values and malformed extension namespaces.

`CmlSemanticMetadataReader` owns strict typed-`JsValue` traversal. It accepts
no raw text, unsafe casts, coercions, defaults, ignored fields, or partial
success. Shape failures are one deterministic typed diagnostic; a completed
shape is passed to `CmlSemanticFoundation.build`, whose complete diagnostic
vector is wrapped without changing foundation kinds or logical coordinates.
The reader never reads sources, hashes files, derives IDs from labels/paths, or
claims a source snapshot is current.

## Compatibility isolation

The v1 core is deliberately closed. Opaque namespaced extension objects are
recursively rendered deterministically, but no extension content can create a
missing core fact or override a Catalog identity, reference, or absence. A
future core semantic change requires another schema and migration design;
unrecognized versions fail closed. Existing legacy model metadata and
StateMachine/Workflow ABI publications remain parallel and unchanged.

## Ownership and future integration

Cozy owns admitted semantic IR and this JSON contract. BoK owns Term, relation,
and profile meanings; Textus CBD Support owns rendering and gap review. The
checked-in fixture is a synthetic JSON-only consumer contract, not external
consumer acceptance.

`metadataPath` reserves a dedicated artifact name, but this design wires no
CML parser, filesystem output, Cozy CLI command, runtime session, or automatic
producer. Later projection phases must supply explicit upstream IDs and Terms;
they may use namespaced extensions for optional independent data without
guessing IDs from legacy metadata, names, paths, symbols, slugs, RDF candidates,
or delimiters.
