# CML Dynamic Metadata Supplier Handoff

Current status: S54.3.3 ACCEPTED after focused validation and independent Step
review; manual Step commit audit pending. S54.3.1 and S54.3.2 are CLOSED.
Owner: Cozy Dynamic metadata supplier; Phase 54.4 owns later Use Case/Actor adoption.
Update rule: preserve the accepted foundation, Structure, Classification and
Dynamic v1 contracts; consume explicit declared facts and gaps by qualified identity.
Closure basis: all 141 tests passed in nine selected focused/predecessor suites;
P543-S5433-REVIEW-001 revision 1 independently accepted DF-01–09 with zero blockers.
The parent records the manual local Step commit in its native audit.
S1 is CLOSED at `feb4ba862635a56fa0519ae75cef0bdd910739b2`;
S2 is CLOSED at `4a57f37244be8f0bb5238bb92bb951ca615c8930`, with 132 successful
tests in eight suites and independent P543-S5432-REVIEW-001 revision 1 PASS.
The sole full Phase review, ordinary full test, release and final closure remain
pending. Phase 54.4 is unstarted.

## Exact internal supplier and reader APIs

The [specification](../spec/cml-dynamic-metadata.md) fixes the closed wire and
[design](cml-dynamic-metadata.md) fixes responsibility. These are `private[cozy]`
internal APIs used by Cozy code in `cozy.modeler`; this is no public CLI or
external protocol delivery.

- [`CmlSemanticFoundation`](../../src/main/scala/cozy/modeler/CmlSemanticFoundation.scala):
  `build(elements: Vector[ElementRecord], terms: Vector[TermRecord])` returns
  `Either[Vector[Diagnostic], Catalog]`. `Catalog.element(ModelElementId)` and
  `Catalog.term(TermId)` retain separate qualified identity domains.
- [`CmlDynamicMetadata`](../../src/main/scala/cozy/modeler/CmlDynamicMetadata.scala):
  `build(catalog, workflows, activities, flows, stateMachines, states, transitions,
  causalLinks, extensions)` returns `Either[Vector[DynamicDiagnostic], Graph]`.
  Only the validated factory creates Graph. `read(value: JsValue)` returns the
  same typed result; `toJson(graph): JsObject` and `canonicalJson(graph): String`
  render an admitted Graph.
- [`CmlDynamicTopology`](../../src/main/scala/cozy/modeler/CmlDynamicTopology.scala):
  `build(graph)` returns `Either[Vector[DynamicDiagnostic], Topology]`.
  `node(id: ModelElementId)` returns `Either[DynamicDiagnostic, ElementRecord]`;
  `incoming(id)` and `outgoing(id)` return
  `Either[DynamicDiagnostic, Vector[TopologyLink]]`. `workflow`, `activity`,
  `flow`, `stateMachine`, `state`, `transition` and `causalLink` each take
  ModelElementId and return `Either[DynamicDiagnostic, <family>Projection]`.
  The Graph and complete nodes/links remain available on Topology.

Read a resource as UTF-8 and parse it with Play JSON `Json.parse`. Pass the
JsValue to Dynamic.read and handle Left diagnostics or the admitted Right Graph.
Build Topology from that Graph; query by exact qualified pairs. Follow direct
context references through the seven family queries or Catalog.element. A typed
producer first admits its Catalog, then passes all seven complete carrier
vectors and predecessor/opaque extensions to Dynamic.build. Supplying Dynamic's
own namespace to build is an ExtensionConflict; the reader removes that child
before invoking the same validated factory.

The envelope is `cozy.cml.semantic-metadata.v1`, with exactly `schemaVersion`,
`elements`, `terms`, `extensions`. Dynamic lives at `extensions.cozy.cml.dynamic`,
version `cozy.cml.dynamic.v1`. Its exact fields are `schemaVersion`, `workflows`,
`activities`, `flows`, `stateMachines`, `states`, `transitions`, `causalLinks`.
Each projection embeds the entire canonical admitted `element` JSON and the
[exact carrier fields](../spec/cml-dynamic-metadata.md#complete-carriers).
All listed payload fields use Presence except direct causal `kind`.

Presence is `{status:present,value:typedPayload}` or
`{status:absent,reason:not-declared|not-represented|unsupported|not-applicable,detail:String}`.
ModelReference is `{identity:{modelId:String,elementId:String},boundary:local|external}`;
DeclaredExpression is `{text:String,references:Presence[Vector[ModelReference]]}`.
Recursive typed containers are closed. Version is checked before child shape;
missing, extra, null, wrong-type and unknown values fail with fixed safe typed
diagnostics. Only Local references resolve admitted kinds. External values are
literal opaque pairs even when equal to a local identity. Arrays preserve order
and multiplicity; canonical rendering recursively sorts object keys. This is
not source-currentness or signature evidence.

## Two concrete fixed UTF-8 resources

[`dynamic-metadata-v1-declared.json`](../../src/test/resources/cozy/modeler/dynamic-metadata-v1-declared.json)
and [`dynamic-metadata-v1-absence.json`](../../src/test/resources/cozy/modeler/dynamic-metadata-v1-absence.json)
preserve the accepted
[`classification-metadata-v1-declared.json`](../../src/test/resources/cozy/modeler/classification-metadata-v1-declared.json)
first 22 ElementRecords, first two Terms, entire Structure and Classification
children and both opaque peers unchanged. Each adds twenty records, one Term and
seventeen carriers: 42 records / three Terms, with arrays 1 Workflow / 5 Activities /
5 Flows / 1 StateMachine / 2 States / 1 Transition / 2 CausalLinks.
Operation, event and rule are supporting core records rather than new carrier families.

All new names are `共有`. Qualified model authority is `dynamic.example`;
source path is `src/main/cml/dynamic-handoff.cml`, digest is 64 lowercase `b`
characters, and source lines 23–42 follow the exact twenty-record Catalog order.
The added Term is `TermId(dynamic.example, workflow:fulfillment)` at line 23.
Core references are NotDeclared with `No semantic references are declared.`.
These are synthetic caller-admitted source facts: they assert neither a real
CML file, parser provenance nor filesystem currentness.

Declared Workflow `workflow:fulfillment` has purpose
`Complete the declared order flow.`. Its activities are review, branch, accept,
reject, merge, with roles Action, Branch, Action, Action, Merge. Its five ordered
flows are review-branch, branch-accept, branch-reject, accept-merge, reject-merge.
Every activity points to the Workflow. All share External
`external.example/actor:reviewer` and affected Local
`structure.example/entity:order`, then `structure.example/aggregate:order`.
The Workflow declares machine:order, operation:complete, event:completed,
rule:complete, state:done and transition:complete. Accept declares complete
operation, completed event and done state effect; branch declares rule:complete.
Other activity operation/event/rule/state-effect vectors are Present(empty).
Branch flow guards are literal `approved` and `!approved`, each referencing
rule:complete. Other guards are NotApplicable with
`The flow has no declared guard.`.

Machine `machine:order` is owned by the Local order aggregate and retains the
same affected pair. Its ordered states are pending, done; its transition is
complete and its Workflow is fulfillment. Both states point to machine:order,
with Present(empty) entry and exit actions. Transition complete points from
pending to done; trigger text `completed` references event:completed, guard
`approved` references rule:complete and action `complete()` references
operation:complete. It independently declares activity:accept,
workflow:fulfillment, the affected pair and state:done.

Cause `cause:completed` points operation:complete to event:completed; its rule
is NotApplicable with `The cause has no declared rule.`. Reaction
`reaction:completed` points event:completed to activity:accept with rule:complete.
Both retain the affected pair. Activity operation context and the two literal
causal declarations may describe a cycle; no runtime policy or additional edge
is inferred. All unqualified new references above are Local in dynamic.example.

Declared topology has exactly 23 complete nodes (11 accepted prefix nodes plus
12 new nodes) and eight links in Catalog order: five Flows, one Transition,
Cause, Reaction. Only these explicit endpoint fields produce edges. Membership,
context, expressions and state effects add no edges; no reciprocal constraint
is inferred. Incoming/outgoing indexes use each present Local endpoint only.

Absence mode changes only the new Workflow's core identity to
NotRepresented with `No stable Workflow identity is represented.`. Each carrier
Presence field is Absent with reason index `(projectionOrdinal + fieldOrdinal) % 4`:
projection ordinal 0–16 follows the seven arrays above; field ordinal excludes
`element` and direct causal `kind`. Exact pairs are NotDeclared / `No Dynamic
fact is declared.`, NotRepresented / `The Dynamic fact is not represented.`,
Unsupported / `The Dynamic fact is unsupported.`, NotApplicable / `The Dynamic
fact is not applicable.`. Sources, other identities, separate Term, prefix and
Cause/Reaction kinds remain unchanged. No present link names the anonymous Workflow.

Absence topology retains 23 nodes including anonymous Workflow and eight links
with fully absent endpoints. Every addressable node has Right(empty)
incoming/outgoing. Six other family queries return their complete absent
projections. Workflow lookup at dynamic.example/workflow:fulfillment returns
UnknownProjection at `workflow.identity`; node lookup returns UnknownNode at
`node.identity`, both with their fixed safe details and publication None. The
matching separate Term still resolves. Names, Terms, source and ordinals supply
no fallback identity.

The third opaque peer is `org.example.dynamic-handoff` with exactly
`{producer:synthetic-caller,declaredFlowOrder:[review-branch,branch-accept,branch-reject,accept-merge,reject-merge],absence:null}`.
Both original peers and this peer survive strict read/rebuild/render intact.
Their values are retained without assigning opaque semantics.

## Runnable focused evidence recipe

From the Cozy repository root, the parent-selected logical recipe is:

```sh
sbt --batch "testOnly cozy.modeler.CmlDynamicMetadataFixtureSpec cozy.modeler.CmlDynamicTopologySpec cozy.modeler.CmlDynamicMetadataSpec cozy.modeler.CmlSemanticFoundationSpec cozy.modeler.CmlSemanticMetadataSpec cozy.modeler.CmlStructureMetadataSpec cozy.modeler.CmlClassificationMetadataSpec cozy.modeler.CmlClassificationTopologySpec cozy.modeler.CmlClassificationMetadataFixtureSpec"
```

On the managed workstation route this exact logical request through registered
`cncf_command_runner` and
`/Users/asami/.agents/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`,
with the parent-admitted execution authority and scoped first-attempt escalation.
Wait for terminal `lock=released` before another top-level SBT invocation.
This exact recipe passed all 141 tests in nine suites, with SBT and wrapper exit
codes 0 and the shared lock released; P543-S5433-VALIDATION-ADMISSION-001 revision 1
retains the actual command result, log and summary.

[`CmlDynamicMetadataFixtureSpec`](../../src/test/scala/cozy/modeler/CmlDynamicMetadataFixtureSpec.scala)
authors DF-01 full core/carrier/source/canonical wire equality, DF-02 literal
23-node/eight-link and all-seven queries, DF-03 complete cyclic absences,
DF-04 anonymous identity and separate Term navigation, DF-05 full prefix and
three-peer retention, DF-06 recursive shape/version diagnostics, DF-07 full
embedded/factory core binding, DF-08 Local semantic rejection and External
literal isolation, DF-09 independent Catalog/carrier permutations and recursive
key reconstruction. Every example uses adjacent Given/When/Then. Actual file
parsing and prerequisite production calls occur beneath When. New expected
facts are independent literal typed data; only the unchanged accepted
Classification prefix is read as a prefix oracle. DF-09 actively requests at
least 100 successes and zero discards and exercises both modes per generated
plan. The actual validation passed these terminal property assertions;
P543-S5433-REVIEW-001 revision 1 independently accepted the complete Step.

## Successor adoption and ownership

Phase 54.4 consumes declared purpose, participants, operations/events/rules,
state effects and direct context references with explicit gaps. It remains
unstarted; this document provides no successor acceptance or implementation.
Phase 54.6 causal traversal must follow only admitted Cause/Reaction and other
explicit edges and preserve External/absent endpoints. Cozy owns metadata;
CNCF owns runtime realization and Textus CBD Support owns view/navigation and
external acceptance. This authoring delivers no parser, projector, CLI, runtime,
external dashboard/site acceptance or real-source/currentness claim.

The [Phase](../phase/phase-54.3.md) and
[checklist](../phase/phase-54.3-checklist.md) remain IN_PROGRESS. S1 and S2
accepted history is retained; S3 is ACCEPTED with its manual commit audit and
all final Phase gates pending. Original HYG-P543-S5431-001 remains the separately attributed legacy
StateMachine projector follow-up, with canonical Hygiene projection required
by the parent before final release bookkeeping; no projector edit occurs here.
