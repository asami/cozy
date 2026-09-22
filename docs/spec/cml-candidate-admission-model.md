# CML candidate-admission model

Status: CAM-73 executable-specification companion

## Scope

This specification defines the provider-neutral Candidate-Admission Model (CAM)
surface admitted by Cozy CML for a Composite StateMachine Action. It records
source normalization and validation only. Provider selection, Provider binding,
`ActionExecution`, and runtime commitment remain outside this model.

The existing generic seam remains:

```text
Action -> Required SPI -> Provider binding -> ActionExecution
```

`Suspended(Continuation)` is a Provider outcome and is not CML CAM syntax.

## Literate CML grammar

An `ACTION` declaration remains a named literate heading. Its properties are
direct fields; CAM fields are not headings or nested structures.

```text
### ACTION

#### <judgment-action>

KIND = JUDGMENT
OPERATION = <resolved-operation>
INPUT = <role>.subject
GOAL = <goal-reference>
CONTEXT = <context-reference>
CANDIDATE = <candidate-identity>
ALTERNATIVE = <alternative-reference>
ALTERNATIVE = <alternative-reference>
CRITERIA = <criterion-reference>
EXPECTED-RESULT = <expected-result-reference>
EVIDENCE = <evidence-reference>
EVIDENCE-SCOPE = <evidence-scope-reference>
EVIDENCE-FRESHNESS = <evidence-freshness-reference>
EVIDENCE-PROVENANCE = <evidence-provenance-reference>

#### <admission-action>

KIND = ADMISSION
OPERATION = <resolved-operation>
INPUT = <role>.subject
CANDIDATE-ACTION = <judgment-action>
EFFECT = LOCAL
TRANSACTION = REQUIRED
IDEMPOTENCY = NOT_REQUIRED | REQUIRED
IDEMPOTENCY-KEY = <key-reference>  # required only when IDEMPOTENCY = REQUIRED
```

`KIND = OPERATION` remains the legacy Action form. All three kinds retain the
existing resolved `OPERATION` and typed `INPUT` contract: input is required
when the resolved Operation declares an input, and is prohibited otherwise.
The operation identifies the generic SPI seam; it does not bind a Provider.

## Normalized source IR

`CompositeStateMachineLogicalAction` retains its existing identity, kind,
operation, input binding, source, and metadata fields. Its trailing defaulted
`candidateAdmission` field is empty for normal legacy Actions.

For `JUDGMENT`, it contains a closed `CompositeStateMachineJudgmentAction`
with typed source-attributed reference wrappers for goal, context, candidate
identity, alternatives, criteria, expected result, evidence, evidence scope,
evidence freshness, and evidence provenance. A Judgment semantic result has
no next-state or transition field.

For `ADMISSION`, it contains a closed
`CompositeStateMachineAdmissionAction` with one source-attributed typed
reference to its candidate-producing Judgment Action. Field/action source
locations remain in the normalized source IR.

## Validation

- `KIND` is exactly one of `OPERATION`, `JUDGMENT`, or `ADMISSION`.
- Every required Judgment singular field has exactly one nonempty direct value.
- `ALTERNATIVE` and `CRITERIA` each have at least one nonempty direct value and
  are unique under CML normalized-key comparison.
- CAM Actions reject nested structural content and direct fields other than
  their declared fields and existing Action metadata.
- CAM Actions reject `PROVIDER`, `PARTICIPANT`, `AI`, `CODEX`, `HUMAN`,
  `INVOCATION-BINDING`, `ORCHESTRATION`, `CONTINUATION`, `TRANSPORT`,
  `RUNTIME`, `SCRIPT`, `RETRY`, `EXECUTION`, `IMPLEMENTATION`, and `BINDING`.
- An Admission Action has exactly one direct `CANDIDATE-ACTION`, which resolves
  to a Judgment Action. Its existing Action metadata must declare
  `EFFECT = LOCAL` and `TRANSACTION = REQUIRED`.
- Each Judgment Action has exactly one Admission Action. This is the explicit
  deterministic relationship:

```text
JUDGMENT -> ADMISSION
```

- StateMachine guards and transitions, not either CAM declaration, remain the
  authority for progression.
- A WORKFLOW `REQUIRED-OPERATION` may target an `OPERATION` or `JUDGMENT`
  Action. It rejects `ADMISSION`, which is local deterministic StateMachine
  semantics rather than a required SPI capability.

## Generation boundary

Public Scala model generation lowers normalized CAM Actions to the additive
`cozy.cml.candidate-admission-producer-abi.v1` generated Scala ABI and the
canonical `target/cozy/candidate-admission-producer-abi.json` sidecar. The
lowering uses the normalized source IR directly; it does not reparse CML or
create a handwritten shadow model.

The output preserves model identity (Composite StateMachine plus optional
Workflow identity/version), fixed schema and generator provenance, all Action
and CAM reference source locations, Judgment/Admission descriptors, their
deterministic relationship, and Workflow Required SPI correlation. The
Admission descriptor records its `LOCAL` / `REQUIRED` boundary. It does not
contain a transition selection or next-state command.

Existing Phase 62.3 generated ABI/schema, JSON path, and ComponentFactory
types and generated output tree remain byte-for-byte compatible when the input
contains no CAM Actions. Both public Scala routes emit identical new artifacts
when CAM is present. A malformed normalized CAM graph fails closed with a
`CAM-73-02` diagnostic before generation can return partial output.

## Exclusions

This CML surface does not introduce `InvocationBinding`, orchestration or
continuation modes, Provider/participant/AI/Codex/human/transport attributes,
runtime admission or commitment, provider registry semantics, continuation
persistence or resume, `sm-workflow` behavior, REST/UI/Flutter behavior, or
application payload policy.
