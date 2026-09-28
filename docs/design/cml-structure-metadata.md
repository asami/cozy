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
and compares embedded records with the validated core JSON. It removes only
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
