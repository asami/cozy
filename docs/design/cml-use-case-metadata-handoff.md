# CML Use Case Metadata Supplier Handoff

Current status: PHASE-54.4 CLOSED; all three Steps CLOSED; final evidence below supersedes prior pending records.
Owner: Cozy metadata supplier; Phase parent for validation, independent review, acceptance and local commits.
Update rule: retain literal fixture facts and independent peer gates; project acceptance only from actual parent evidence.

## Supplier artifacts and authority

The [specification](../spec/cml-use-case-metadata.md) and
[design](cml-use-case-metadata.md) define the accepted private supplier and
qualified [navigation](../../src/main/scala/cozy/modeler/CmlUseCaseNavigation.scala).
The two fixed UTF-8 resources are
[declared](../../src/test/resources/cozy/modeler/use-case-metadata-v1-declared.json)
and [explicit absence](../../src/test/resources/cozy/modeler/use-case-metadata-v1-absence.json).
[CmlUseCaseMetadataFixtureSpec](../../src/test/scala/cozy/modeler/CmlUseCaseMetadataFixtureSpec.scala)
authors UF-01–09. These are synthetic caller declarations, with modeled source
attribution. The paths, digests and lines make no claim about real CML parsing,
filesystem provenance or currentness.

Both resources extend the complete accepted
[declared Dynamic fixture](../../src/test/resources/cozy/modeler/dynamic-metadata-v1-declared.json):
first 42 complete core records in original order, first three Terms, and all
six predecessor extension values. Both retain a declared addressable Dynamic
Workflow. The new absence mode applies only to Use Case/Actor declarations.
The outer schema remains `cozy.cml.semantic-metadata.v1`; the new closed child
is `cozy.cml.use-case` with exactly `schemaVersion: cozy.cml.use-case.v1`,
`actors` and `useCases`. Total counts in each file are 49 core records, four
Terms, two Actors and two Use Cases.

## Exact new core and declaration facts

All seven appended records have model/source authority `usecase.example`,
name `共有`, and source path `src/main/cml/use-case-handoff.cml`.
Their core references are `Absent(NotDeclared, "No semantic references are declared.")`.

| Line | Kind | Qualified element component |
| --- | --- | --- |
| 43 | actor | actor:requester |
| 44 | actor | actor:auditor |
| 45 | use-case | use-case:complete-order |
| 46 | use-case | use-case:observe-order |
| 47 | component | component:partner |
| 48 | service | service:fulfillment |
| 49 | external-system | external-system:payment |

Declared mode gives each record its exact Present ModelElementId. A separate
`TermId("usecase.example", "use-case:complete-order")` uses the full source at
line 45. Matching strings in Term and model domains never coerce either identity.
The fourth opaque peer `org.example.use-case-handoff` is exactly
`{"producer":"synthetic-caller","declaredFlowOrder":["main","alternate","exception"],"absence":null}`.
It cohabits with the complete Structure, Classification and Dynamic children
and the three original `org.example.*-handoff` peer objects. Array order and
null remain literal values; canonical rendering sorts object keys recursively.

Requester has actorKind `human`, summary `Request an order.`, description
`Submit the declared order use case.`, and declared Local links to complete-order,
Dynamic fulfillment Workflow, complete operation, completed event, and Structure
order Entity then order Aggregate. Auditor has actorKind `system`, summary
`Observe an order.`, description `Observe the declared order use case.`, a Local
observe-order link and Present-empty workflows, operations, events and domainElements.

Complete-order retains `UC-COMPLETE`, `Complete order.`,
`Complete the declared order use case.`, `Complete the declared order flow.`,
priority `high` and lifecycleStatus `approved`. Its trigger is `Order submitted.`
with Local Dynamic completed event; precondition `Order is pending.` has Local
pending state; postcondition `Order is complete.` has Local done state.
Participation order is Primary requester, Secondary auditor, Supporting External
`external.example/actor:payment`, Stakeholder requester, Actor auditor.
Repeated actors and role order carry no uniqueness or reverse-membership inference.
Domain elements are Local Structure order Entity then Aggregate; operation/event
links are Local Dynamic complete/completed. Collaborators are Local partner
component, fulfillment service, payment external-system, then External
`external.example/component:partner`. Realizing Workflows are Local
`dynamic.example/workflow:fulfillment`, then External
`external.example/workflow:fulfillment`.

| Kind / name | Exact summary and description | Condition | Ordered steps / alternates / exceptions |
| --- | --- | --- | --- |
| MainFlow / main | Complete the order. / Submit and complete the declared order. | Absent(NotApplicable, "The main flow has no declared condition.") | Requester submits the order. → Local requester; Complete the order. → Local Dynamic complete operation / Present-empty / Present-empty |
| AlternateFlow / alternate | Reject the order. / Reject and review the declared order. | rejected → Local Dynamic complete rule | Reject the order. → Local Dynamic reject activity / Review the order again. → Local Dynamic review activity / Present-empty |
| ExceptionFlow / exception | Handle payment failure. / Notify failure and keep the declared order pending. | payment unavailable → External external.example/event:unavailable | Notify payment failure. → Local Dynamic completed event / Present-empty / Keep the order pending. → Local Dynamic pending state |

Each expression has explicit Present reference vectors. Flow name, summary and
description are Present; steps, alternates and exceptions are Present vectors.
Flows stay in main/alternate/exception order. This publishes literal declarations;
it does not interpret directives or execute include/extend or flow composition.
Observe-order has all 16 fields Present: six empty scalar strings, an empty-text
trigger with Present-empty references, and empty vectors in every collection.
Present-empty content never becomes absence.

## Explicit absence and stopping behavior

The absence resource changes only requester and complete-order core identity,
including their complete embedded records, to
`Absent(NotRepresented, "No stable Use Case or Actor identity is represented.")`.
Their records remain in the Catalog and carrier arrays. All other identities,
names, kinds, sources, Terms and predecessor facts remain literal.

All eight Actor fields and all 16 Use Case fields become Absent. The cyclic
reason/detail pairs are:

| Index | Reason | Exact detail |
| --- | --- | --- |
| 0 | NotDeclared | No Use Case fact is declared. |
| 1 | NotRepresented | The Use Case fact is not represented. |
| 2 | Unsupported | The Use Case fact is unsupported. |
| 3 | NotApplicable | The Use Case fact is not applicable. |

Index is `(projectionordinal + fieldordinal) % 4`, with requester/auditor/
complete-order/observe-order ordinals 0/1/2/3. Actor field order is actorKind,
summary, description, useCases, workflows, operations, events, domainElements.
Use Case field order is declaredId, summary, description, goal, trigger,
preconditions, postconditions, priority, lifecycleStatus, actorReferences, flows,
domainElements, operations, events, collaborators, realizingWorkflows.
There is no Present outbound reference to either anonymous identity. Auditor
and observe-order remain queryable with complete absent fields; Dynamic
fulfillment remains addressable. The matching Term remains independently queryable.
Names, declaredId, source coordinates, Terms or ordinal guesses do not rescue
unknown model or projection queries.

## Reproducible independent consumer recipe

1. Parse one fixed resource as UTF-8. Independently call `CmlUseCaseMetadata.read`,
   `CmlDynamicMetadata.read`, `CmlStructureMetadata.read` and
   `CmlClassificationMetadata.read` on that same whole envelope. Each reader
   owns its admission gate; success in one proves nothing about another.
2. Build `CmlUseCaseNavigation` from the admitted Use Case Graph and
   `CmlDynamicTopology` from the separately admitted Dynamic Graph.
3. Query `ModelElementId("usecase.example", "actor:requester")`. Follow its
   declared Local complete-order pair with `resolve` and `useCase`, retaining
   the full target record and full UseCaseProjection.
4. For each Local realizingWorkflows value resolve its complete Workflow core,
   then pass the unchanged qualified pair to `CmlDynamicTopology.workflow`.
   Follow declared activities, flows, stateMachines, operations, events, rules
   and stateEffects through the corresponding type lookup or Catalog core.
5. Query complete accept Action, branch Branch, order state machine, pending/done
   states, complete transition and all five Dynamic flows. Retain ordered
   endpoints/guards and transition trigger/guard/action context. Topology remains
   23 complete nodes and eight explicit links; the new records add no Dynamic edges.
6. Resolve Use Case and Workflow order Entity/Aggregate references to complete
   Catalog records and the independently admitted Structure projections. Retain
   complete Classification carrier vectors from its own admission.
7. Resolve declared role, collaborator and flow-expression references in their
   literal order. External workflow/payment references return exact opaque
   ExternalTarget values, including when a pair is equal to a locally admitted
   pair. Stop there. Anonymous identities and absent outbound facts also stop
   Local continuation; there is no network fallback, peer auto-admission,
   inferred reciprocal membership or synthetic link.

## Executable evidence and parent validation recipe

| Scenario | Authored behavior evidence |
| --- | --- |
| UF-01 | Full declared core/source/Term/carrier facts and independent wire/build/read/canonical reconsumption |
| UF-02 | Four independent admissions and full Actor → UseCase → Workflow → Dynamic/static queries |
| UF-03 | Complete cyclic absence facts, anonymous embedded equality and declared prefix |
| UF-04 | Separate Term/model domains, named survivors and anonymous/alias stopping diagnostics |
| UF-05 | Complete predecessor core/Terms/carriers, publication admission and four opaque peers |
| UF-06 | Single-mutation strict recursive shape/version failures and original publication attribution |
| UF-07 | Complete embedded forgeries and read/factory occurrence multiset failures in both valid modes |
| UF-08 | Wrong-kind/missing Local failures and exact opaque External alternatives at every declared context |
| UF-09 | Active 100-success/zero-discard property, six independent observable permutations in both modes |

The literal logical thirteen-suite recipe, from the Cozy repository root, is:

```text
sbt --batch "testOnly cozy.modeler.CmlUseCaseMetadataFixtureSpec cozy.modeler.CmlUseCaseNavigationSpec cozy.modeler.CmlUseCaseMetadataSpec cozy.modeler.CmlSemanticFoundationSpec cozy.modeler.CmlSemanticMetadataSpec cozy.modeler.CmlStructureMetadataSpec cozy.modeler.CmlClassificationMetadataSpec cozy.modeler.CmlClassificationTopologySpec cozy.modeler.CmlClassificationMetadataFixtureSpec cozy.modeler.CmlDynamicMetadataSpec cozy.modeler.CmlDynamicTopologySpec cozy.modeler.CmlDynamicMetadataFixtureSpec cozy.modeler.CmlModelMetadataSpec"
```

The parent executes this through registered `cncf_command_runner` and the
`cncf-sbt-serial-execution` wrapper with first-attempt scoped escalation. Never
run independent top-level SBT processes concurrently; wait for the terminal
`lock=released` marker. `testOnly` supplies Test/compile.

S54.4.3 is CLOSED on 2026-10-04. The recipe passed 181 tests in thirteen
suites. Fixture, navigation and Use Case metadata properties each passed 100
generated cases with zero discards; both resource modes run in every fixture
sample. SBT and wrapper exited zero and the serial lock was released.
Independent complete Step review passed with no blockers, Hygiene or
Development Candidates. The parent manual local acceptance commit binds ten
paths; native Git history records its revision.
Legacy compatibility stays in unchanged CmlModelMetadataSpec; this new envelope
is not input to the legacy format parser. Final full Phase review, full repository validation and distinct local release
are recorded below.

Cozy publishes metadata. CNCF owns runtime realization; Textus CBD Support owns
external views, navigation and acceptance. Terminology/BoK, Event Storming and
final external fixture acceptance remain with Phases 54.5–54.7. This handoff
claims no external operation or product acceptance. Durable scope and status
are recorded in the [2026-10-04 journal](../journal/2026/10/2026-10-04-phase-54.4-use-case-metadata.md)
and [Phase 54.4](../phase/phase-54.4.md).

## Final Phase acceptance and release — 2026-10-04

S54.4.1, S54.4.2 and S54.4.3 are CLOSED at parent manual local commits
`fcfa28582e66b948a9fe5be9f20558766e98efe4`,
`2ea61373c5e3754124e3d17a6d03b94b01a0c135` and
`8d204fdda0d3469a57a0ccf30c5c90e6475d99fb`.
The sole independent full Phase review P544-FULL-REVIEW-001 revision 1 is PASS
with zero current blockers, Hygiene or Development Candidates. Original review
history remains in P544-REVIEW-LEDGER-001 revision 6; Phase repair cycles: zero.
Ordinary final `sbt --batch test` passed 2802 tests across 195 suites,
with zero failures or aborted suites. The 9 pre-existing canceled cases
are four optional Remotion integration cases and five Phase 51 deferred cases;
their acceptance is not claimed. All Use Case scenarios passed. SBT/wrapper
exit 0 and `lock=released` are verified in
P544-FINAL-FULL-VALIDATION-ADMISSION-001 revision 1.
The distinct parent manual local release binds P544-CLOSURE-001 revision 1.
Its actual Git revision and committed-tree/clean-tree audit are recorded after
successful commit in the private closure receipt. This closure projection is
authoritative only on that verified release. No empty follow-up journal is
created. Phases 54.5–54.7 remain PLANNED and unstarted; CNCF runtime realization
and Textus CBD Support external views/navigation/acceptance retain their owners.
