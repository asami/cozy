# CML Semantic Foundation Specification

The CML semantic foundation is an immutable, validated in-memory boundary for
admitted model elements, BoK Terms, provenance, explicit absence, and supplied
semantic references. Its executable behavior is specified by
[CmlSemanticFoundationSpec](../../src/test/scala/cozy/modeler/CmlSemanticFoundationSpec.scala).

## Identity and provenance

`ModelElementId(modelId, elementId)`, `TermId(vocabularyId, termId)`,
`RelationId(vocabularyId, relationId)`, and `ProfileId(vocabularyId, profileId)`
are distinct qualified identities. Their components are exact, case-sensitive,
admitted values. They are neither names nor generated IRIs, paths, digests,
ordering keys, concatenated strings, or normalized symbols. Components must be
nonempty, unpadded, and free of ISO control characters; valid Unicode and
delimiters are retained unchanged.

`SourceAttribution(authorityId, path, sha256, line)` describes an admitted
source snapshot independently of stable identity. The path is a canonical
project-relative POSIX path. It rejects absolute, drive, URI, backslash, empty,
dot, dot-dot, and control-character forms. A digest is exactly 64 lowercase
hexadecimal characters, and a supplied line is positive. A definition source
authority equals the model ID or vocabulary ID that defines it. The foundation
does not read the source, calculate a digest, or make provenance identity.

## Absence and records

`Presence[A]` is either `Present(value)` or
`Absent(reason, detail)`, where the closed absence reasons are `NotDeclared`,
`NotRepresented`, `Unsupported`, and `NotApplicable`. Details are nonempty,
unpadded, and control-free. An explicit false value or an explicit
empty vector remains present; it is never rewritten as absence. No default
policy can supply an identity or replace an admitted gap.

`ElementRecord` retains an optional qualified model identity, descriptive kind
and name, source attribution, and explicitly present or absent references.
An absent element identity keeps the record but excludes it from identity
indexes. `TermRecord` registers only its explicitly admitted `TermId` and
source. Names and labels remain presentation data, never lookup keys.

## References and catalog

`SemanticReference` targets either a qualified model element or a qualified
Term. Its boundary is explicitly `Local` or `External`; relation and profile
identifiers are opaque upstream vocabulary references. A reference can retain
context, a localized presentation label, source attribution, and either a
declared origin or a derived origin with a valid nonempty rule and source set.
The reference's primary source authority equals its owning element source
authority. The foundation does not infer relations, derivations, policy, or
term/model equivalence.

`CmlSemanticFoundation.build(elements, terms)` validates every admitted record
and reference before returning a `Catalog`. It returns all deterministic typed
diagnostics or an immutable catalog. Duplicated identities, invalid identity or
provenance data, malformed reference evidence, and unresolved local targets
are rejected. External targets remain explicitly external and are not treated
as locally resolved. The catalog retains authored record order but indexes only
qualified identities; lookup is by exact identity only and returns a typed
`DanglingReference` diagnostic for an unknown value. It has one canonical
empty zero-argument construction and no public constructor/copy bypass.

## Scope

This specification does not freeze Term-to-model relation meanings, relation
equivalence, or IRI rules; those remain upstream BoK authority. It does not
define a JSON/YAML envelope, parser, writer, detailed Structure/Classification/
dynamic/Use Case/Terminology/Event Storming projections, or runtime behavior.
Those projections belong to Phases 54.1–54.7, versioned publication belongs to
MMD-54-03, and Capability IR belongs to Phase 64.
