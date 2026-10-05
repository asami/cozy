# CML Causal Metadata supplier handoff

Supplier: Cozy / [Phase 54.6](../phase/phase-54.6.md)
Consumer closure: Cozy [Phase 54.7](../phase/phase-54.7.md), CLOSED in final release content;
all three native Steps CLOSED, sole full Phase review PASS, actual ordinary full
validation 2895 tests/204 suites with nine existing optional/deferred cancellations.
External consumer: CNCF/Textus CBD Support runtime/UI integration and acceptance
Behavior authority: [Causal v1 spec](../spec/cml-causal-metadata.md)
Responsibility: [Causal design](cml-causal-metadata.md)

## Publication and fixed inputs

The unchanged envelope schema is `cozy.cml.semantic-metadata.v1`. The owned
namespace `cozy.cml.causal` has schema `cozy.cml.causal.v1`, exactly eight child
fields and strict complete correspondence to admitted Foundation, Dynamic,
Use Case and Structure facts. Classification, Terminology and opaque peers
remain complete. Qualified identities are exact pairs, without normalization
or label/source/ordinal recovery. Arrays and duplicate occurrences are semantic;
recursive object-key order is immaterial.

Reusable actual UTF-8 resources:

- [Declared](../../src/test/resources/cozy/modeler/causal-metadata-v1-declared.json)
- [Absence](../../src/test/resources/cozy/modeler/causal-metadata-v1-absence.json)
- [Top-down CF-01..08 working specification](../../src/test/scala/cozy/modeler/CmlCausalMetadataFixtureSpec.scala)

Both retain the complete accepted fifty-five-core/seven-Term Terminology
declared predecessor, append twenty literal `causal.example` core occurrences,
and contain seventy-five core occurrences, seven Terms and eight causal carriers.
Structure has seven elements/three relations; Dynamic has two Workflows/eight
causal links with other prefix arrays intact; Use Case has three Actors/three
Use Cases; Terminology has seventy-five element projections/seven Term
projections. Classification remains unchanged. Five opaque namespaces survive,
including `org.example.causal-handoff` and literal null values.

All new names are `共有`. Supplied source authority/path are `causal.example`
and `fixtures/causal-model.cml`, sha256 is sixty-four `a` characters, and line
is the authored occurrence plus one, with both anonymous occurrences at line 19.
These are synthetic caller facts. No corresponding source file, CML parse,
computed digest or actual source-currentness certification is supplied.
Anonymous full records are exactly equal, have no stable identity and retain
present-empty core references; Cause/Reaction payloads remain distinct.

## Consumer query recipes

`CmlCausalMetadata.read(parsedJson)` admits a fixed publication;
`CmlCausalTraversal.build(graph)` supplies complete nodes and nine-field links.
Build accepts only an already admitted semantic Envelope. Reader admission
rejects malformed supplied peers and forged owned correspondence with safe
fixed kind/path/detail diagnostics. Rendering and canonical reconsumption retain
the complete envelope. Consumers use the existing package-private Cozy APIs;
this handoff introduces no new public ABI or production migration.

Each unqualified name below denotes `ModelElementId("causal.example", name)`.

| Question | Exact declared result and strength |
| --- | --- |
| `outgoing(actor:buyer)` | ActorOperation command/confirm/command in that order, including duplicate command. |
| `consequences(command:place-order)` / `consequences(operation:confirm-order)` | Placement / confirmation Cause to event:order-placed. |
| `causes(event:order-placed)` | Placement then confirmation full Cause links. |
| `consequences(event:order-placed)` | Reservation Reaction to operation:reserve-stock. |
| RuleInput / RuleOutput context | Placed to rule:reserve-stock, then that rule to operation:reserve-stock; declared context without rule execution. |
| `consequences(operation:reserve-stock)` / `causes(event:stock-reserved)` | Stock-event Cause. |
| `affecting(aggregate:order)` | Placement, confirmation and reservation complete causal carriers. |
| `affecting(entity:order-item)` | Placement and stock-event complete causal carriers. |
| ModelReferenceLink from component:inventory | Full External causal.external inventory dependency declaration. |
| ModelReferenceLink from event:order-placed | Local view, Local query, resident External inventory, duplicate Local view; complete read-model provenance. |
| Use Case Collaborator | Local inventory/query/view and nonresident External causal.external inventory in authored order. |

Only Cause and Reaction assert direct causation. Actor operations, affected
subjects, AggregateMember, WorkflowRule, collaborators and generic ModelTarget
references retain their separate declared meanings. Affected Aggregate/Entity
does not become the cause of its Event. Workflow operations/events/participants,
shared names, expression text, membership and relation/profile tokens produce no
causal cross-product. Query/View references do not claim read-model updates;
dependency does not claim execution. Direct cycles are followed through bounded
separate requests, without transitive closure.

Resolve admits complete value membership, including equivalent immutable
copies. Local returns the exact full record; External returns ExternalTarget
even when its pair is resident; Absent returns its unchanged reason/detail.
External links never enter Local adjacency or gain an inferred external type.
Evidence field/index and supplied line are occurrence coordinates only.

## Absence and oracle boundaries

The absence input preserves the declared prefix and all new source/kind/name
occurrences. Buyer identity is NotRepresented. Nonanonymous core references
cycle by new-core ordinal modulo four. Supplemental fields cycle by projection
plus field ordinal: Actor 0, Use Case 1, Workflow 2, causal carriers 3..8 and
Structure Aggregate/Entity 9..10. The four literal reasons/details are
NotDeclared/"No causal fact is declared.", NotRepresented/"The causal fact is
not represented.", Unsupported/"The causal fact is unsupported." and
NotApplicable/"The causal fact is not applicable.". Declared anonymous causal
fields use causal-family ordinals 4/5 plus field ordinal. No missing Actor is
referenced by a remaining Present new reference.

Full capability gaps retain owners and deterministic ordering, ending with the
Unsupported causalInference gap. Present-empty remains distinct from absent.
Unknown/unaddressable qualified queries fail safely; names, source and positions
never recover anonymous addressability. The fixed specification independently
authors literal carriers, complete child wire, typed families/gaps and all nine
traversal fields. Its accepted predecessor reader is not a Causal-output oracle.
CF-08 exercises both actual modes per sample, independently permuting core,
Terms, peer/reference arrays and recursive keys, with actual result/category
receipts required before acceptance.

## Observed evidence and final closure

S1 is CLOSED at parent-verified native manual commit
`1d928b39c8b83c533c55dce389cb6c6589640249`, parent original Phase base
`c9defb8ac25945f5b048c9fe6900d7437c068041`. Its selected validation passed
227 tests in 17 suites, with CM-10 100 successes/zero discards/pass and fresh
independent protected-focused S1 review PASS.

S2 is CLOSED at parent-verified native manual commit
`661e11b848102faf143bf5f4dc173e5ab7bcafdd`, parent S1. Its selected validation
passed 239 tests in 18 suites; CT-12, CM-10, TM-09, TN-08 and TF-09 each recorded
100 successes/zero discards/pass with positive baseline coverage. Fresh
independent protected-focused S2 review is PASS and its actual native receipt
is verified. No duplicate/amended S2 acceptance is required.

[S3](../phase/s54.6.3.md) fixed declared/absence resources, CF-01..08 and this
handoff are accepted after parent mechanical preflight and selected validation:
all 247 tests in 19 suites passed. CF-08, CT-12, CM-10, TM-09, TN-08 and TF-09
each recorded 100 successes, zero discards and passed status; baseline category
counts were positive. CF-08 exercised both actual modes per case: declared and
absence counts were each 100, with all seven observed categories positive. SBT
and wrapper exited 0 and the serialization lock was released. Fresh independent
lightweight S3 review is PASS with no CPB, HYG or DEV items. The parent verified
manual native local S3 Step commit `2360d78ac1ee24e4b9e51465d41f669a861283f6`
and its actual receipt, whose sole parent is S2
`661e11b848102faf143bf5f4dc173e5ab7bcafdd`; S3 is CLOSED.
[Phase 54.6](../phase/phase-54.6.md) is CLOSED with all five original obligations,
all three native Steps and all four MMD outcomes accepted. The sole full Phase
review covered all 21 accepted paths and all seven whole programs against the
original Phase base and five original obligations.
The sole independent full Phase review passed with no Current Phase Blockers,
Hygiene or Development Candidates. Ordinary `sbt --batch test` passed 2868
tests in 201 suites, zero failures/aborted suites, 9 pre-existing optional/deferred
canceled cases. SBT and wrapper exited zero; the serial lock was released.
The distinct parent manual local release binds `P546-CLOSURE-001@1`; its native
commit and closure receipt are verified after committing these final records.
The nine existing cancellations comprise four optional Docker Remotion opt-in
cases and five CV-04/CV-05/CV-06/SP-01 ownership cases; all 33 current Causal
behaviors succeeded. Scala 2.12.18 and Cozy 0.3.3-SNAPSHOT remain unchanged.

Phase 54.7 owns final all-family Cozy supplier contract and fixture closure.
External CNCF/Textus CBD runtime/UI work and consumer acceptance remain with
downstream owners. This handoff supplies no parser/second source model, external
resolution, dependency execution, runtime/CLI/UI, publication or deployment.


## Current Phase 54.7 consumer closure projection

The [final publication handoff](cml-semantic-publication-handoff.md) supersedes
this document's planned successor projection for current Phase 54.7 status.
Phase 54.7 is CLOSED in final release content. Final all-family admission S1 and
source-grounded questions S2 are CLOSED at verified native commits
`0b1db8996cc4979cbab029a801ae82f0611f7e43` and
`2f7d1852b9d4b1e55e13d1200799673e00624606`; S3 compatibility and reproducible
producer handoff are CLOSED at `abe935c6e2144b04308e16f10e4f82fed92c471b`, sole
parent S2, with exact eighteen-path verified native receipt and thirteen accepted
checklist entries. Focused S3 evidence remains 274 tests/22 suites, fresh
independent review PASS and repair 0; PC-06/SF-11/SP-08 each observed seed 42/100
successes/zero discards/pass and all six/21/four coverage counts100, with 31 SF-11
execution orders. S1/S2 focused evidence remains scoped to their own Steps.

The sole full Phase review `P547-FULL-REVIEW-001 r1` is PASS over the original
base through accepted S3, all 24 paths/four whole programs/four outcomes/five
obligations, with no CPB, HYG or DEV. Actual ordinary `P547-FINAL-VAL-001`, logical
`sbt --batch test`, passed 2895 tests in 204 suites, zero failures/aborted suites
and nine existing optional/deferred cancellations. All 27 new publication behaviors
passed, none canceled. The distinct final release contains canonical closure and
binds `P547-CLOSURE-001@1`; its native release OID, committed-tree receipt and final
requirement audit are verified after committing and retained in parent workflow
evidence. All Phase 54.6 closure facts and accepted contracts above remain historical
accepted evidence. Cozy produces the completed versioned consumer-neutral JSON
contract, source-grounded fixtures and reproducible handoff. BoK canonical meaning/
synonyms, CNCF runtime and Textus CBD Support integration/views/navigation/acceptance
retain their owners; no external acceptance is claimed. Scala 2.12.18 and
0.3.3-SNAPSHOT remain unchanged.
