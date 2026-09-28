# CML Semantic Foundation Design

The semantic foundation is a Cozy-only, additive boundary between admitted CML
model data and later consumer-neutral projections. It centers a validated,
immutable `Catalog`, so consumers can retain exact qualified identities and
source snapshots without reparsing CML or rebuilding identity from display
text.

## Boundary shape

The design separates four concerns:

- qualified model, Term, relation, and profile identities;
- source attribution, which records authority and an admitted project-relative
  snapshot without becoming identity;
- explicit `Presence`, which makes semantic gaps observable without inventing
  defaults or policy; and
- common `SemanticReference` vocabulary for future CBD Support and Capability
  handoffs.

Element and Term namespaces stay distinct even when their strings, local IDs,
or labels coincide. The catalog retains authored record order for inspection,
but indexes exact qualified identities only. Thus ordering, display names, and
delimiter-joined strings cannot select a record. An element without an identity
remains observable outside the identity index.

## Validation and failure boundary

`build` is the only nonempty Catalog construction path. It validates identity
components, canonical source attribution, absence detail, supplied relation /
profile / context / label evidence, and declared or derived reference origin.
It also rejects duplicate admitted identities and local targets that do not
resolve exactly once in their corresponding index. Typed diagnostics preserve
the logical field coordinate and distinguish invalid identity, provenance,
reference, duplication, and dangling-reference cases. A lookup for an unknown
identity returns the same structured dangling-reference form rather than
throwing or falling back to a name.

External references deliberately carry their qualified target without a local
resolution claim. Derived input sources can have distinct authorities; only the
reference's primary source is required to match the owning element authority.
No relation, derivation, same-as relation, alias, lifecycle, causal connection,
or policy is inferred by this layer.

## Ownership and future work

Cozy owns this semantic IR boundary. BoK owns Term and relation meaning, while
Textus CBD Support owns views and gap review. This foundation does not establish
IRI rules or upstream relation semantics. It also is not an external-consumer
acceptance, runtime, CML parser, serializer, or generated-metadata change.

Detailed projections are deferred to Phases 54.1–54.7. MMD-54-03 separately
owns the versioned publication envelope and compatibility policy. Phase 64
later consumes the reference vocabulary for Capability IR without redefining
this foundation.
