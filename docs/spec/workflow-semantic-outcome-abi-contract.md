# Workflow Semantic Outcome Generated ABI Contract

Semantic role: Normative producer ABI contract.
Current status: Authored S69.2; parent behavior validation, actual generated Scala3 compilation, independent protected review and manual Step commit pending.
Owner: Cozy generated Workflow ABI, Phase69 parent.
Update rule: update together with the paired lowering design and executable specification; record acceptance only from parent evidence.
Updated: 2026-10-03.

## Source and collection selection

The accepted [source contract](workflow-semantic-outcome-source-contract.md)
supplies immutable `WorkflowDefinition.semanticOutcomes`. This contract
projects those declarations into deterministic Scala3 and canonical JSON
through both existing `modeler-scala` and `modeler-scala-value` public routes.
The [lowering design](../design/workflow-semantic-outcome-abi-lowering.md),
[Phase69](../phase/phase-69.md) and [checklist](../phase/phase-69-checklist.md)
record the producer boundary and pending ABI acceptance.

Selection applies to the entire declaration-ordered collection:

| Highest nonempty metadata tier | ABI version | Bootstrap version |
| --- | --- | --- |
| Any semantic outcome vector | `cozy.cml.statemachine-workflow-abi.v5` | `cozy.cml.statemachine-workflow-bootstrap.v5` |
| Failure policy or execution safety | `cozy.cml.statemachine-workflow-abi.v4` | `cozy.cml.statemachine-workflow-bootstrap.v4` |
| Lifecycle policy or wait | `cozy.cml.statemachine-workflow-abi.v3` | `cozy.cml.statemachine-workflow-bootstrap.v3` |
| Invocation policy | `cozy.cml.statemachine-workflow-abi.v2` | `cozy.cml.statemachine-workflow-bootstrap.v2` |
| None, including an empty collection | `cozy.cml.statemachine-workflow-abi.v1` | `cozy.cml.statemachine-workflow-bootstrap.v1` |

One outcome-bearing predicate selects v5 for the shared `VERSION`, every member
descriptor, canonical sidecar and bootstrap. V5 includes every inherited
invocation, lifecycle and safety type and field even when absent from individual
members. Outcome-only real CML consequently selects v5. An old member in a v5
collection keeps its complete data, with explicit empty policy/wait/outcome
vectors and nullable optional policies. Without outcomes, original v1-v4
formatting, type/field shapes, ordering, paths and bytes are preserved.

## Public Scala schema

The package remains `domain.statemachine.workflow`; the fixed outputs remain
`StateMachineWorkflowAbi.scala`, `StateMachineWorkflowComponentFactoryBootstrap.scala`
and one ordered `<Workflow>StateMachineWorkflow<N>.scala` member. Public producer
constants add `semanticOutcomeSchemaVersion` and
`semanticOutcomeBootstrapSchemaVersion`; every v1-v4 constant retains its value.
Only v5 appends the following types after existing safety types:

```scala
enum SemanticOutcome(val canonicalValue: String) {
  case NeedsInput extends SemanticOutcome("NEEDS_INPUT")
  case NeedsRevision extends SemanticOutcome("NEEDS_REVISION")
  case Rejected extends SemanticOutcome("REJECTED")
}
final case class SemanticOutcomeTransition(role: String, stateMachine: String, from: String, to: String, on: String)
final case class SemanticOutcomeDeclaration(identity: String, target: InvocationTarget, actionIdentity: String, outcome: SemanticOutcome, transition: SemanticOutcomeTransition, source: SourceIdentity)
```

V5 appends `semanticOutcomes: Vector[SemanticOutcomeDeclaration] = Vector.empty`
to `WorkflowDescriptor` after `executionSafetyPolicy`. All inherited labels,
types, order and defaults remain unchanged. Old positional arities 7 through
12 and named v4 construction compile against v5 with empty outcome defaults;
new positional arity 13 and named v5 construction can supply declarations.
There is no binary case-class/unapply compatibility promise or promise to
rewrite a caller-supplied `schemaVersion` value. New fields/types never appear
in generated v1-v4 sources.

Each v5 member appends a named `semanticOutcomes` vector in immutable source
order. Projection directly preserves declared Action/Participant target kind
and spelling, canonical Action identity, exact enum alternative, role, original
qualified StateMachine reference, already normalized from/to/event names and
nullable source line. No alias resolution, graph lookup or normalization occurs
again. Subsets, multiple eligible Actions sharing one outcome and explicitly
authored self-loops keep their exact declarations.

Each member's `componentFactoryMetadata` wraps that member's `workflow` value.
The shared bootstrap keeps ordered direct member metadata references. Every
metadata workflow is therefore the actual generated descriptor, with no
inferred factory lookup or provider instantiation.

## Canonical JSON and lossless values

The sidecar remains `target/cozy/statemachine-workflow-abi.json` and has exactly
`schemaVersion`, then `workflows`. Every v5 Workflow record retains inherited
field order and appends `semanticOutcomes` after `executionSafetyPolicy`.
Outcome objects have exactly this ordered shape:

```text
identity: String
target: { kind: "ACTION" | "PARTICIPANT", identity: String }
actionIdentity: String
outcome: "NEEDS_INPUT" | "NEEDS_REVISION" | "REJECTED"
transition: { role: String, stateMachine: String, from: String, to: String, on: String }
source: { line: Int | null }
```

Workflow and declaration array order follows source/IR order. Missing individual
outcomes serialize `[]`; inherited policy/wait vectors and optional policy
nulls retain established representations. Existing literal encoding preserves
quotes, backslashes, newline, carriage return, tab, U+0001, U+0085, Unicode and
opaque identity spelling in every new string field. Int/Long values, including
Long.MaxValue and zero retry delay, remain exact without floating-point rounding.

Semantic outcomes are independent of retry, failure classification, lifecycle,
waits, API/SPI, ActionExecution, Continuation, CandidateAdmission,
Action idempotency and UnitOfWork. Rejected has no implicit failure classification
or inferred terminality. The authored ordinary graph determines terminal states.
No provider/result/event execution or synthetic runtime behavior is generated.

## Independent compatibility and executable consumers

The [original v4 provenance](../../src/test/resources/modeler/workflow-semantic-outcome-v4-baseline/provenance.md)
records the pre-Phase69 original producer commit and actual full-safety route
products. Four immutable resources supply byte expectations, independently of
the edited generator. Existing v1 pins and v2/v3 baseline specifications remain
independent and retain their original expectations.

The [interface executable specification](../../src/test/scala/cozy/modeler/WorkflowSemanticOutcomeAbiGenerationSpec.scala)
authors twelve behavior families: repeated public routes, original v4 bytes,
all schema tiers, seven nonempty subsets, aliases, shared/Judgment targets,
qualified/self-loop edges, complete ordered JSON/literals, escaped/null/numeric
IR, at least forty meaningful opaque samples, the exact mixed collection, and
typed source consumers. All materialization is authored before expectations.

Actual compiler inputs are produced under
`target/test-generated/workflow-semantic-outcome-{normal,value}/` and
`target/test-generated/workflow-semantic-outcome-compile/`, each with the standard
`target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/` suffix.
Normal and value groups each have the actual ABI, bootstrap,
`ReviewWorkflowStateMachineWorkflow1.scala` and
`WorkflowSemanticOutcomeAbiConsumerProbe.scala`. Mixed has the same ABI,
bootstrap and probe plus exactly these ordered members:

1. `LegacyWorkflowStateMachineWorkflow1`
2. `PolicyWorkflowStateMachineWorkflow2`
3. `LifecycleWorkflowStateMachineWorkflow3`
4. `SafetyWorkflowStateMachineWorkflow4`
5. `ReviewWorkflowStateMachineWorkflow5`
6. `OutcomeOnlyWorkflowStateMachineWorkflow6`
7. `OutcomeEdgeWorkflowStateMachineWorkflow7`

The first four use actual old CML, Review uses accepted source, OutcomeOnly uses
real admitted CML and OutcomeEdge uses a bounded immutable copy of accepted IR.
Each group saves its canonical JSON at the standard `target/cozy` location.
Typed probes use actual descriptors/bootstrap, old and new constructors,
all three enum alternatives, both target alternatives and every transition/source
field. The parent owns the selected eight-suite behavior accumulator and one
Scala3.3.8 compilation invocation across the three groups sequentially, with
separate `target/phase69-generated-compile/{normal,value,mixed}/` classes and
analysis files, normal shared caches and the registered serialized SBT runner.
Authored probes and expected output are not compiler or acceptance evidence.

## Receiver responsibilities and non-goals

[CNCF Phase84](../../../goldenport-cncf/docs/phase/phase-84.md) owns decoder and
bootstrap integration, result/event dispatch, guards/current-state/concurrency,
execution history, diagnostics and enforcement. Cozy producer acceptance does
not require receiver runtime completion. No Iteration abstraction, counter,
budget, termination/escalation metadata or scheduler is introduced; ordinary
graphs suffice for supplied evidence. Source/parser/fixture semantics and
adjacent producer contracts remain unchanged by this ABI work.
