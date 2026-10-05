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

## Consumer responsibility

Consumers continue to use accepted qualified navigation/topology/traversal APIs.
The facade adds no generic question language, transitive query engine, external
resolver, CML parser, or semantic inference. Only Cause/Reaction assert causation;
affected subjects and aggregate membership are contextual. Names, Term candidate
relations, BoK kinds, arbitrary references, workflow lists, and expression text
provide no equivalence or execution result. External boundaries stop local
resolution even when the target is resident. Equal anonymous records remain
distinct occurrences without source/name/ordinal identity recovery.

S54.7.2 supplies authored top-down source-grounded question fixtures over the two actual
accepted Causal resources: Term/element, Use Case/Actor/Operation,
Operation/Entity-or-Aggregate/Event, Event/Rule-or-Reaction/Operation-or-Event,
Workflow/StateMachine/Entity, independent Classification dimensions, and explicit
External/provenance/unsupported gaps. Compatibility evidence and the reproducible
[producer handoff](cml-semantic-publication-handoff.md) retain this fixed supplier
boundary. Textus CBD Support owns its UI/runtime/integration acceptance; Cozy's
producer closure makes no external acceptance claim.

See [Phase 54.7](../phase/phase-54.7.md) and
[S54.7.1](../phase/s54.7.1.md).

## Test-owned finite consumer questions

S54.7.2's `CmlSemanticPublicationFixtureSpec` owns a fixed dispatch over the
seventeen declared/absence question occurrences. Its two small question resources
contain complete literal selected source facts, answer assertions and evidence,
and refer to the unchanged full Causal input publications. This ownership stops
at executable consumer evidence: it introduces no production question schema,
parser, request registry or general query engine. The stable
[question contract](../spec/cml-semantic-publication.md) defines the matrix;
the [Step checklist](../phase/s54.7.2-checklist.md) owns current acceptance status.

The facade owns final all-family admission and full retention. Terminology
Navigation owns element/Term/links/reverse-links and explicit Term resolution.
Use Case Navigation owns complete UseCase/Actor/core queries and ModelReference
resolution. Dynamic Topology owns Workflow, StateMachine, State, Transition and
node queries. Classification Topology owns independent dimensions and nodes.
Structure Graph owns full static projections. Causal Traversal owns finite
node/outgoing/causes/consequences/affecting/resolve requests and their direct or
contextual evidence strength. The consumer spec composes these accepted APIs;
it does not transfer semantic ownership to the facade or redesign suppliers.

Complete typed expectations are selected from independently admitted unchanged
supplier readers and raw predecessor facts, before facade or query actions.
Complete literal source facts also constrain observed encodings, so matching an
ID, source index, count, or roundtrip alone cannot conceal a changed projection,
reference, source or Presence value. Observation encoders use existing family
codecs or test-owned full-field encoding. They never generate expected output.
Full-record correspondence and ordered occurrence vectors preserve equal
anonymous owners and different carrier payloads without an identity fallback.

Traversal expectations preserve all nine link fields. Direct Cause/Reaction,
affected-subject context, RuleInput/RuleOutput and generic SemanticReference
context remain separate evidence. A full rule ElementRecord is the admitted
rule fact; there is no rule or operation projection and no policy execution.
Local static continuation is contextual, External stopping remains explicit
even for resident targets, and classification dimensions retain independent
declaration identities despite shared powertype targets. Available empty direct
answers do not erase full Dynamic capability or explicit field gaps.

Seed 42 property execution recursively reconstructs object keys independently
and separately permutes finite question execution. It keeps authored arrays,
nulls and scalar values intact, consumes both complete modes in every sample,
and reports actual Test.Result and coverage. The literal fixture order is a
contract independently of the varying execution order. Configured success
minima are not validation evidence.

All source coordinates are admitted snapshot provenance rather than claims
about current source files. The consumer boundary grants no name-based identity,
Term equivalence, policy result, transitive causation or read-model update. S3
retains compatibility and reproducible producer handoff ownership, while Textus
CBD Support retains UI/runtime/integration acceptance ownership.


## Stable producer and consumer reproduction responsibility

The [producer handoff](cml-semantic-publication-handoff.md) supplies the fixed
versions, six independent whole-root readers, build/read/handleLeft/render/
canonical-parse/read/query recipe, eight valid empty availability cases and exact
focused 22-suite reproduction command. External versioned JSON is distinguished
from Cozy's internal private[cozy] Scala APIs. Unknown opaque objects retain full
literal values and grant no capability/core override; known malformed siblings
fail the independent final gate, including peers outside Causal's narrower gate.
Base-first and fixed family-order failures remain safe and payload-independent.

CompatibilitySpec is a bottom-up component contract separate from S2's top-down
questions. It independently admits unchanged suppliers before facade actions,
uses literal empty/adversarial/qualified provenance expectations and a pure
recursive canonical serializer. Each active seed-42 sample consumes both complete
opaque-extended modes and all six invalid child versions, with actual result and
observed coverage. Full envelopes include their actual opaque peers; comparison
never mistakes the original peer-free Envelope for the extended one.

The producer preserves all complete records, field gaps, ordered arrays and
anonymous occurrences. Downstream consumers own external protocol admission,
view/navigation integration and acceptance; BoK meaning and CNCF runtime remain
with their owners. Version changes require the relevant core/child design and
explicit migration responsibility, not implicit registration or reconstruction.

## Current factual evidence

S1 is CLOSED at verified native `0b1db8996cc4979cbab029a801ae82f0611f7e43`
with 257 tests/20 suites and focused PASS. S2 is CLOSED at verified native
`2f7d1852b9d4b1e55e13d1200799673e00624606`, sole parent S1, with eighteen
checked entries, 268 tests/21 suites/all eleven SF examples, SF-11 100 successes/
zero discards/all 21 coverage counts100/31 execution orders and focused PASS.
The historical failures and bounded repair remain in the chronological journal.

S3 PC-01..06 and the producer handoff have accepted behavior and whole-file
conformance. P547-S5473-VAL-001 compiled successfully and passed all 274 tests
in 22 suites, including all six PC and all eleven SF examples. PC-06, SP-08 and
SF-11 each ran seed 42 with 100 successes, zero discards and passed=true; all
six, four and 21 respective coverage counts were 100, with 31 SF-11 question
execution orders. Fresh independent P547-S5473-REVIEW-001 returned PASS over all
eighteen Step paths. S3 remains IN_PROGRESS with its native Step acceptance commit
and verified receipt pending. All five original Phase obligations, sole full
Phase review, ordinary full validation, distinct release and native requirement
audit remain pending. Canonical current
state and closure basis remain [Phase 54.7](../phase/phase-54.7.md) and its
[checklist](../phase/phase-54.7-checklist.md); normative responsibility is separate
from these observed facts.
