# Workflow Semantic Outcome Source Lowering

Semantic role: Stable Semantics (frozen design).
Current status: Authored; pending parent validation, independent protected S69.1 review and manual Step commit. ABI, handoff and full Phase gates remain pending.
Owner: Cozy Workflow source lowering, Phase 69 parent.
Update rule: update together with the normative source contract and executable specification; record acceptance only from parent evidence.
Updated: 2026-10-03.

## Frozen responsibility boundary

The [source contract](../spec/workflow-semantic-outcome-source-contract.md)
defines one optional direct Workflow declaration and its closed three-value
semantic result vocabulary. The immutable types retain declared target,
canonical Action, actual ordinary StateMachine edge and source line separately.
The defaulted trailing WorkflowDefinition field preserves all old source
constructor labels/arities without promising binary/unapply compatibility.

WorkflowSemanticOutcomeCml owns closed scalar admission, outcome tokens,
Action/Participant resolution, normalized entry and canonical pair uniqueness,
and exactly-one actual graph edge resolution. It accepts the optional section,
workflow identity, normalized CSM, required capabilities, resolved constituent
evidence and duplicate-aware direct-field reader. It has no evaluator, model
division, catalog lookup or CNCF dependency.

CompositeStateMachineCml retains its existing source/model resolution and
parsing responsibilities. Private Constituent gains the already validated
trimmed STATE-MACHINE reference. Private NormalizedCompositeStateMachine carries
the unchanged public definition plus resolved constituents. Standalone CSM
extraction takes its definition; Workflow construction passes the typed
Vector[(CompositeStateMachineConstituent, StateMachineClass, String)] evidence
to outcome normalization. This is transient evidence, with no second model
lookup, repeated validation or public/serialized CSM change. Entity-qualified
reference spelling survives even when the resolved class has an unqualified name.

## Cohesive ownership extraction

WorkflowDeclarationOwnershipCml owns new structural position admission and
contextual generic outcome-field admission. Both public extraction routes call
it before normalization. Its traversal distinguishes structural containers
from established opaque identity positions and preserves unrelated generic
outcome meanings. Closed outcome container/entry shape rejects deeper content.

The complete prior invocation ownership group (field set, field rejection and
recursive misplaced-section rejection) moves from CompositeStateMachineCml to
this helper. All existing callers pass the same direct-field reader and retain
their old checks, messages, recursion and normalization. Only the direct owned
SEMANTIC-OUTCOME container gains the required traversal exemption for its
legitimate target fields. There are no duplicate forwarding wrappers.

WorkflowExecutionSafetyPolicyCml keeps its complete legacy safety/lifecycle
ownership and all normalizers. Its Workflow identity-container set additionally
recognizes SEMANTIC-OUTCOME so outcome entry names remain opaque without
weakening reserved-field or nested-structure checks. No other ownership rule
moves. The cohesive extraction bounds the final CSM parser at its original
1,037 lines without cosmetic compression. HYG-P67-S671-001 and
HYG-P67-S671-002 remain separately owned existing debt, not resolved here.

## Normalization and semantic independence

Structural cardinality and the CSM requirement precede Workflow construction.
Existing Action, required-operation and constituent admission supply canonical
evidence. Raw scalar duplicates are checked before maps; trimmed exact values
select the closed outcome alternatives. Role and states resolve against the
constituent evidence; transitions.call ++ transitions.global provide exactly
one NameTransitionTo/getEventName match. Actual names and source order are
immutable, deterministic and equality bearing; explicit self-loops remain valid.

No transition Action is necessary and no Action, guard or placement changes.
Unknown references, nonexistent/reversed/wrong-target edges and ambiguous
matches use RAISE.syntaxErrorFault. Events, composite derivations and attached
Actions cannot manufacture missing edges. Old CSM/API/SPI, CandidateAdmission,
invocation/lifecycle/waits/failure and execution safety normalization stays intact.

Cozy produces metadata; CNCF Phase 84 owns runtime result association, event
admission, current-state/guard/concurrency checks, history and enforcement.
NeedsInput, NeedsRevision and Rejected do not emit events, execute, suspend,
consume retries or alter ActionExecution or technical failure classification.
Rejected is terminal only through the authored graph. No provider-specific
AI/Human grammar or runtime engine belongs in this source lowering.

## Abstraction evaluation and acceptance

The real [review-loop fixture](../../src/test/resources/modeler/workflow-semantic-outcome.cml)
uses ordinary revision/input backedges and approval/rejection states. Ordinary
graphs also admit authored escalation states. Phase 69 and receiver Phase 84
evidence has no concrete runtime insufficiency requiring an Iteration type,
counter/budget, termination/escalation policy or scheduler. The actual
[receiver Phase 84 plan](../../../goldenport-cncf/docs/phase/phase-84.md)
restricts a dedicated abstraction to concrete evidence of ordinary graph
insufficiency. An unavailable
sm-workflow checkout is a verification limitation, never a stale-reference
finding. Future evidence has separate ownership; unknown extensions fail closed.

The [interface executable specification](../../src/test/scala/cozy/modeler/WorkflowSemanticOutcomeCmlSpec.scala)
admits real source including Entity-contained StateMachines, malformed/owned
positions and bounded generated aliases, with adjacent Given/When/Then clauses.
The parent owns the seven-suite focused accumulator, mechanical checks and
independent protected high source review. [Phase 69](../phase/phase-69.md) and
its [checklist](../phase/phase-69-checklist.md) retain unchecked validation,
reviews, manual commits, S69.2 ABI, S69.3 handoff and final gates. Source
authoring neither promises generated v5 output nor closes the producer Phase.
