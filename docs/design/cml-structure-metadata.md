# CML Structure Metadata v1 Design

## Boundary

Structure is an independent, versioned extension over the frozen semantic
metadata envelope:

```text
caller-admitted Catalog + typed Structure projections
  -> CmlStructureMetadata.build
  -> validated foundation Envelope with cozy.cml.structure
  -> canonical JSON / actual JsValue reader
  -> the same factory validation
```

The `Graph` constructor is private to its facade. It retains immutable Catalog,
validated Envelope, and authored typed projection order. There is no public
unchecked factory or copy route. The base foundation, metadata writer, and
reader remain unchanged; Structure uses them rather than duplicating their
identity, provenance, Presence, or canonical rendering rules.

## Admission and identity

The catalog is caller-admitted truth. The factory validates that every
supported base record has exactly one Structure projection, and that a present
model identity is the exact Catalog identity, not a display name, source path,
array position, delimiter-derived substitute, or BoK relation identity.
Anonymous records deliberately remain unindexed. Their full record equality is
only a binding witness, not an invented identifier.

The extension carries six closed static tags so Entity, Value, Aggregate,
Composition, Aggregation, and Association cannot flatten into a generic
relation. Relation policies are literal declared carriers. No relation kind
adds lifecycle, ownership, endpoint, or aggregate defaults.

## JSON boundary and failure model

The codec strictly owns `cozy.cml.structure.v1`. It validates the child version
before its exact object shapes, delegates core parsing to the existing reader,
and compares full typed embedded records with admitted Catalog records. It
builds one empty-extension semantic envelope from the Catalog, replaces only
one candidate element at each authored index with the embedded JSON, and reads
the complete envelope through CmlSemanticMetadata.read. A successful read must
produce the original full typed record at that index; the codec returns the
first matching original record. No match yields InvalidCoreBinding. Catalog
validation preserves local references and duplicate/authority checks; Structure
validation preserves categories, coverage, and anonymous witness multiplicity. It removes only
its own namespace before reusing the factory. Unknown Structure keys, malformed
Presence/Option/null forms, and impossible local references return typed
diagnostics with fixed safe coordinates; no partial graph is exposed.

Canonical object ordering remains the existing foundation writer’s behavior;
authored array ordering remains meaningful. This is not an RFC 8785 claim,
signature protocol, parser freshness proof, or filesystem snapshot.

## Ownership

Cozy supplies neutral, typed metadata for CBD Support. CNCF owns runtime
meaning and enforcement; Textus BoK owns terminology and traversal. This slice
does not connect parsers, legacy projectors, runtime, CLI, source exporters,
site/UI, publication, deployment, or later Phase 54.2–54.4 consumers. Those
consumers receive a supplier handoff only after their separately authorized
work.

## Supplier fixture handoff

The concrete supplier package is recorded in the [Structure metadata supplier
handoff](cml-structure-metadata-handoff.md). Its declared and explicit-absence
JSON resources and STR-25..29 executable scenarios freeze the Structure
identity, declared-policy, and Presence matrix for later children. This records
fixture and vocabulary supply only; later consumers, runtime behavior, CLI,
source integration, publication, and external acceptance remain separately
authorized. No Structure design or v1 contract is changed by the handoff.

## Phase 71 attribution retirement

Under [file update management](../spec/file-update-management.md), Phase 71
retires the non-integrity source digest while retaining
`cozy.cml.semantic-metadata.v1` and `cozy.cml.structure.v1`. This is an explicit
exception to historical all-core-changes-new-version wording: ordinary
SourceAttribution loading projects required `authorityId`, `path`, and `line`,
ignoring unknown source extras of any JSON type. Missing known fields remain
invalid; explicit `line: null` means no line, and supplied lines must be positive.
There is no legacy decoder, upgrade, alternate version, or fake value. All other
core shapes stay closed; identity, Presence, references, opaque extensions, and
genuine diagnostics retain their contracts. Attribution supplies authority,
canonical project-relative POSIX path, and optional line without hash, snapshot,
or currentness proof. Future material core semantics require independently
designed versioning. Canonical fixtures publish the retired field set; their
STR evidence remains synthetic supplier evidence, without external runtime or
CML integration acceptance.
