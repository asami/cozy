# CML Semantic Foundation Handoff

This document is the stable derivative handoff for the Phase 54 semantic
foundation. It records how the accepted Cozy foundation and the accepted
cozy.cml.semantic-metadata.v1 publication contract are consumed by the
planned child phases. It is a design handoff, not a work log, a new wire
specification, or a claim that any child projection has been implemented.

## Authority and boundary

The handoff consumes the accepted [CML Semantic Foundation
specification](../spec/cml-semantic-foundation.md) and [foundation
design](cml-semantic-foundation.md), together with the [CML Semantic Metadata
v1 specification](../spec/cml-semantic-metadata.md) and [metadata
design](cml-semantic-metadata.md). The [SimpleModeling model integration
boundary](simplemodeling-model-integration-boundary.md) remains the integration
boundary for model data. The [historical capability inventory](../notes/cml-semantic-capability-inventory.md)
is linked as a non-normative baseline only; it does not establish current
completion or add guarantees.

The accepted implementation boundaries are
[CmlSemanticFoundation.scala](../../src/main/scala/cozy/modeler/CmlSemanticFoundation.scala),
[CmlSemanticMetadata.scala](../../src/main/scala/cozy/modeler/CmlSemanticMetadata.scala),
and [CmlSemanticMetadataReader.scala](../../src/main/scala/cozy/modeler/CmlSemanticMetadataReader.scala).

Cozy owns the semantic IR and this publication boundary. BoK owns Term,
relation, and profile meanings. Textus CBD Support owns views and gap review,
and CNCF owns runtime behavior. No downstream acceptance or runtime work is
part of this handoff.

## Callable handoff path

The callable path is deliberately narrow and has the same shape at every
consumer boundary:

~~~
admitted element/Term records
  -> CmlSemanticFoundation.build(elements, terms)
  -> validated CmlSemanticFoundation.Catalog
  -> CmlSemanticMetadata.build(catalog, extensions)
  -> CmlSemanticMetadata.Envelope
  -> CmlSemanticMetadata.toJson(envelope) as JsObject
     or CmlSemanticMetadata.canonicalJson(envelope) as deterministic JSON text
  -> caller supplies a JsValue (parsing canonical text when that option is used)
  -> CmlSemanticMetadata.read(value)
  -> strict CmlSemanticMetadataReader traversal
  -> CmlSemanticFoundation.build(decoded elements, terms)
  -> validated consumer Catalog
~~~

CmlSemanticFoundation, CmlSemanticMetadata, and CmlSemanticMetadataReader are
private[cozy] implementation helpers. Their typed Scala API is an internal
boundary. The external handoff is the versioned JSON contract, whose reader
accepts a JsValue, not raw JSON text. Text parsing, if needed, belongs to the
caller that supplies that value.

The first build validates all admitted records, identities, source evidence,
presence values, and references before producing an immutable Catalog. The
Catalog retains authored record order while its element and Term indexes use
exact qualified identities. A second build after strict decoding repeats the
semantic validation, so malformed or contradictory evidence cannot bypass the
foundation through JSON. Shape failures, unsupported schema versions,
extension failures, and foundation diagnostics remain typed publication
diagnostics.

## Preserved semantics

Qualified identities are exact, case-sensitive two-component values. Model
element and Term spaces remain distinct even where components, names, or
labels coincide. A qualified ID is not provenance, a display name, a path, a
digest, a generated symbol, a navigation value, an RDF candidate, or a Term
slug. Lookup is by the qualified identity pair only; authored order and
presentation text cannot select a record.

SourceAttribution records admitted authority, canonical project-relative path,
digest, and optional positive line. It is evidence about an admitted source
snapshot, not source-currentness or identity verification. The foundation and
reader do not read files or calculate hashes.

Presence distinguishes Present(value) from Absent with NotDeclared,
NotRepresented, Unsupported, or NotApplicable. An explicit empty vector or
false remains present. A local reference must resolve against the corresponding
local Catalog space; an external reference retains its qualified target without
claiming local resolution. Relation, profile, context, preferred-label, and
declared/derived origin evidence remain upstream/admitted facts. This layer
does not infer relation meaning, equivalence, policy, causality, lifecycle,
grouping, or derivation.

The v1 JSON envelope fixes its core object shapes and tags, preserves authored
array order, and treats namespaced extension objects as opaque. Extension
content cannot add or override a core identity, reference, or absence fact.
Unknown schema versions fail closed; a core change requires a separately
designed schema and migration. Canonical rendering recursively sorts object
keys lexicographically, retains array order and exact strings, and uses normal
Play JSON number rendering. These are the deterministic bounds for a fixed
JSON value; this is not RFC 8785, a signature protocol, source-byte
preservation, or numeric-equivalence normalization.

## Compatibility isolation

cozy.cml.model-metadata.v1 is a separate legacy artifact. This handoff does
not read, upgrade, or redefine it. Existing StateMachine and Workflow artifacts
and their ABI contracts remain unchanged. metadataPath is reserved as the
target/cozy/cml-semantic-metadata.json artifact destination only: there is no
CML parser integration, filesystem writer, CLI flag, or automatically emitted
artifact in this boundary.

The historical proposed cozy.cml.model-metadata.v2 handoff wording is
normalized here and in incoming Phase references to the accepted additive
cozy.cml.semantic-metadata.v1 foundation. This document supplies no v2
runtime, schema, or producer claim.

## Evidence interfaces

The [synthetic semantic-metadata v1 JSON fixture](../../src/test/resources/cozy/modeler/semantic-metadata-v1.json)
and its [foundation executable specification](../../src/test/scala/cozy/modeler/CmlSemanticFoundationSpec.scala)
and [metadata executable specification](../../src/test/scala/cozy/modeler/CmlSemanticMetadataSpec.scala)
are evidence interfaces for consumers. Together they show:

- two same-named elements resolving only through distinct qualified IDs;
- Term grounding with source, context, preferred-label, and declared/derived
  evidence;
- explicit absence, local target validation, and an external target without a
  local-resolution claim; and
- preservation of an opaque namespaced extension without turning it into a
  core semantic claim.

The fixture is synthetic and JSON-only. It is not real CML generation, a
downstream acceptance fixture, or proof of source currentness.

## Parent-frozen downstream handoff matrix

Each child consumes only the foundation facts admitted at its boundary. The
dependency order is preserved; detailed projection vocabulary and registered
extension names or meanings are intentionally not designed in this Step.

| Phase | Scope | Input | Remaining producer obligation | Later result | Owner | Dependency |
| --- | --- | --- | --- | --- | --- | --- |
| [Phase 54.1](../phase/phase-54.1.md) | Structure | qualified model IDs, provenance, Presence, references and versioned envelope | Distinct Entity/Value/Aggregate/association/aggregation/composition and admitted endpoint/ownership/lifecycle policy; no implied defaults | Structure fixtures/vocabulary for54.2–54.4 | Cozy Structure producer | 54 |
| [Phase 54.2](../phase/phase-54.2.md) | Classification | foundation plus future54.1 Structure vocabulary | Generalization, traits and independent powertype dimensions with source-grounded qualified links | Classification fixtures for54.3–54.4 | Cozy Classification producer | 54.1 |
| [Phase 54.3](../phase/phase-54.3.md) | Workflow and StateMachine | foundation plus future54.1/54.2 vocabulary; existing dynamic IR is only a partial detail source | Activities/control-flow/participants, states/transitions, affected elements and admitted operation/event/effect/cause/reaction links; no runtime semantics | Dynamic references for54.4 and54.6 | Cozy dynamic producer | 54.2 |
| [Phase 54.4](../phase/phase-54.4.md) | Use Case and Actor | foundation plus future dynamic/static links | Modeled Actors/goals/triggers/conditions/flows/collaborators and realizing Workflow; no actor inference from runtime or names | Use Case/Actor links for54.5–54.7 and possible Capability grounding | Cozy Use Case producer | 54.3 |
| [Phase 54.5](../phase/phase-54.5.md) | Terminology / BoK | qualified Term/Relation/Profile IDs and SemanticReference evidence; future54.4 links | Source-grounded semantic element–Term binding and admitted labels/context; Mono != Entity, Koto != Event; no synonym/sameAs/group inference | Terminology fixtures for54.6/54.7 and Phase64 | Cozy reference producer; BoK retains meaning authority | 54.4 |
| [Phase 54.6](../phase/phase-54.6.md) | Event Storming | foundation plus prior admitted structure/dynamic/actor/term edges | Actor→Command/Operation→Aggregate/Entity→Event→Reaction→subsequent action traversal only where modeled or attributable derivation is admitted | Source-grounded causal fixture set for54.7 | Cozy causal metadata producer; CBD Support renders | 54.5 |
| [Phase 54.7](../phase/phase-54.7.md) | Final semantic-strength contract and consumer fixtures | v1 core/extension policy plus completed54.1–54.6 child handoffs | Freeze each optional projection extension meaning and representative JSON-only cross-view fixtures; current opaque extension is not proof of any view/capability | Cozy producer closure for Textus CBD Support; no external UI acceptance | Cozy producer | 54.6 |
| [Phase 64](../phase/phase-64.md) (not a serial54.x successor) | Capability grounding | foundation qualified model/Term reference vocabulary and future54.5 admitted grounding; other Phase64 dependencies remain | First-class Capability IR/CML syntax/validation/CNCF projection and actual cross-repository acceptance under its own authority | Application/Component Capability references reuse foundation; no parallel Term IDs | Cozy Phase64 with separately required CNCF/CBD contracts | Existing64 predecessor/upstream dependencies unchanged |

## Future producer/consumer recipe

Future work may use this handoff with the following bounded recipe:

1. Admit stable model IDs, Term facts, and source evidence into the Catalog;
   preserve explicit presence and gaps.
2. Build and validate the Catalog, publish only semantics already admitted by
   the relevant authority, and use a separately specified child extension for
   optional projection data.
3. Read the versioned JSON through the strict JsValue reader and resolve by
   qualified IDs; preserve external targets and explicit gaps as represented.
4. Prove each child projection through real fixtures before treating it as a
   consumer handoff. Do not implement a producer or consumer, rename settled
   constants, or infer missing semantics in this foundation handoff.

## Non-claims

This handoff does not implement a producer, consumer, CML parser, generated
artifact, filesystem output, CLI behavior, runtime session, CBD Support view,
external acceptance, or Phase 64 capability projection. All child Phases
54.1–54.7 and Phase 64 remain planned/unstarted under their own authority.
