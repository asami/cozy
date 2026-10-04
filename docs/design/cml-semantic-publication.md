# CML semantic publication design

`CmlSemanticPublication` has one responsibility: admit every supplied registered
semantic family on one complete accepted publication and expose the full typed
Graphs. It sits above the existing Foundation catalog, base Envelope, and six
supplier readers. Foundation and each child retain ownership of qualified
identities, provenance, occurrence correspondence, field Presence, and semantics.
The [supplier contract](../spec/cml-semantic-publication.md) and its bottom-up
executable specification define delivered S54.7.1 behavior.

## Responsibility and admission order

The facade first admits the base. It then reads Structure, Classification,
Dynamic, Use Case, Terminology, and Causal independently in that fixed order,
short-circuiting the first rejected present family. Classification and Terminology
receive the same strict final admission as the families understood by the narrower
Causal peer gate. No existing reader is refactored or made responsible for a new
family.

The facade owns a modest recursive Java-null predicate at the untrusted JSON
boundary, safe fixed publication/family diagnostics, immutable constructor-gated
Publication, and typed missing-family values. It delegates decoding to the base
and peers. `NonFatal` guards are confined to publication and supplier admission;
normal typed failures remain the domain outcome. Rendering a null admitted object
is a DbC violation.

`build` validates the complete supplied publication while retaining its caller's
Envelope and Catalog. `read` retains the independently admitted base Envelope.
Both expose complete family Graphs with original carriers, field gaps, provenance,
core records, Terms, ordering, and occurrence multiplicity. A missing family is
explicit `NotDeclared`; a present admitted family supplies a Graph rather than a
Boolean capability. Source-grounded observable queries establish each requested
view strength. An inconsistent remaining Causal summary fails after peer removal
and is never repaired during final admission.

## Version and compatibility policy

The facade contract is `cozy.cml.semantic-strength.v1`. The external wire remains
`cozy.cml.semantic-metadata.v1`, with the same four root fields and six unchanged
child namespaces/versions. Workflow and StateMachine belong to Dynamic. No extra
carrier family or outer version field is introduced.

Unknown namespaced objects are literal opaque peers, including unknown `cozy.*`
namespaces. Their shape or apparent version grants no capability. Known malformed
children cannot escape into opacity. Full canonical output uses the accepted base
renderer: recursively sorted object keys, authored arrays and duplicates, exact
strings, opaque peers and JSON null. It is neither a signature scheme nor an
assertion of filesystem currentness. Changes to the outer core need a new schema
and separately designed migration; child changes need their own version/design.
Legacy CmlModelMetadata, Scala 2.12.18, dependency coordinates and 0.3.3-SNAPSHOT
remain unchanged, and no runtime/CLI/CML call site migrates.

## Consumer responsibility and pending adoption evidence

Consumers continue to use accepted qualified navigation/topology/traversal APIs.
The facade adds no generic question language, transitive query engine, external
resolver, CML parser, or semantic inference. Only Cause/Reaction assert causation;
affected subjects and aggregate membership are contextual. Names, Term candidate
relations, BoK kinds, arbitrary references, workflow lists, and expression text
provide no equivalence or execution result. External boundaries stop local
resolution even when the target is resident. Equal anonymous records remain
distinct occurrences without source/name/ordinal identity recovery.

S54.7.2 will supply top-down source-grounded question fixtures over the two actual
accepted Causal resources: Term/element, Use Case/Actor/Operation,
Operation/Entity-or-Aggregate/Event, Event/Rule-or-Reaction/Operation-or-Event,
Workflow/StateMachine/Entity, independent Classification dimensions, and explicit
External/provenance/unsupported gaps. S54.7.3 will supply final compatibility and
reproducible producer handoff evidence. These are pending consumers of this fixed
supplier boundary. Textus CBD Support owns its UI/runtime/integration acceptance;
Cozy's producer closure makes no external acceptance claim.

See [Phase 54.7](../phase/phase-54.7.md) and
[S54.7.1](../phase/s54.7.1.md).
