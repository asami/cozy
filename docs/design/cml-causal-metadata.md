# CML Causal Metadata responsibility

The [Causal v1 specification](../spec/cml-causal-metadata.md) fixes an additive
consumer view over the accepted Foundation envelope. Cozy supplies source-grounded
facts for Textus CBD Support without adding Event Storming as a CML source model.
The existing [Phase 54.6](../phase/phase-54.6.md) contains supplier, traversal
and fixture Steps. S54.6.1 implements the supplier; S54.6.2 traversal and S54.6.3
fixed resources/final handoff are pending.

## Source-grounded boundary

Accepted Dynamic CausalLinkProjection already represents Cause and Reaction
with complete owner, source, target, rule and affectedElements Presence fields.
Its Local source/target kinds cover operations, commands, events, rules, actions,
Workflow/Activity and StateMachine lifecycle elements. Entity/Aggregate are
affected subjects rather than Local causal endpoints. The view therefore keeps
an operation causing an event affecting an Aggregate as that exact causal fact;
it cannot promote the affected Aggregate into the cause of the event.

Accepted Use Case Actor.operations, UseCase.collaborators, Dynamic
Workflow/Activity.rules and Structure.aggregateBoundary are independent declared
contexts. Foundation ModelTarget references retain relation/profile tokens,
context, boundary and provenance without deriving causal meaning from the tokens.
Policy/rule context is not policy execution. Query/View context is not event-driven
read-model update; a dependency is not executable interaction. Missing strength
remains literal Presence plus a capability gap.

## Source responsibilities

- `CmlCausalMetadata` owns exact public carriers, closed diagnostics, immutable
  Graph and the admitted factory/rendering API. It retains the supplied Catalog
  and maps publication failures to safe fixed diagnostics.
- `CmlCausalMetadataProjection` admits the three consumed peers through their
  unchanged strict readers, derives typed facts in full core-occurrence order,
  filters complete model references and enumerates deterministic gaps.
- `CmlCausalMetadataCodec` pairs full admitted typed/JSON occurrences, publishes
  the exact owned child, and validates complete deterministic correspondence on
  read. Existing base-private decoders stay private.

These responsibilities share one frozen additive publication boundary. The
accepted Foundation, Dynamic, Use Case and Structure APIs and schemas do not
change. Classification, Terminology and other object peers remain opaque and
complete. Canonical rendering delegates to Foundation recursive sorting.

## Identity, occurrence and failure

Identity is the exact ModelElementId pair, with no case or Unicode normalization,
string concatenation, source hash, name or ordinal identity substitute. Full
ElementRecord equality determines correspondence. Each equal anonymous peer
occurrence is consumed once in its authored order against core occurrence order;
different payloads, sources and references cannot collapse into an ID dictionary.
All core records and Terms survive even when not used in a causal declaration.

Known-peer absence differs from present-empty and malformed supplied evidence.
Missing peers have fixed NotDeclared gaps; malformed supplied peers fail admission.
All four accepted absence reasons and exact details survive. The final Unsupported
causalInference gap makes the unsupported inference boundary explicit. Null or
malformed untrusted publication produces structured safe diagnostics; null
internal Graph rendering is DbC. No user/peer payload is attached to failures.

The strict receiver reconstructs the owned child from re-admitted peers and
requires complete structural equality. Arrays and duplicate counts are literal;
recursive object-key order is immaterial. This validates publication
correspondence without a workflow certificate, source-currentness assertion,
hash or immutable transport requirement.

## Pending consumers and exclusions

S54.6.2 will consume only admitted Graph to provide qualified direct traversal
and separately typed context references. It will not infer transitive causes,
reciprocal relations, Workflow co-membership or automatic External resolution.
S54.6.3 will exercise actual JSON resources, accepted peer compatibility and
grounded declared/absence questions, then provide the final supplier handoff.
Phase 54.7 owns the final all-family Cozy supplier contract and fixtures.
External Textus CBD Support UI/runtime implementation and acceptance remain
outside the Cozy producer closure and with the downstream consumer owner.

This supplier adds no parser/DSL, caller-authored causal declarations, generator,
runtime, CLI, UI/site, external resolution, upstream schema/ABI change, dependency
coordinate change, publication or deployment. Scala 2.12.18 and
Cozy 0.3.3-SNAPSHOT remain the accepted build boundary.
