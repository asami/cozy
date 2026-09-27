# CML Candidate-Admission Producer ABI

Status: derived Cozy producer-boundary design
Scope: Cozy CML StateMachine / Workflow lowering for CNCF Phase 77 and `sm-workflow`

## Authority

This design consumes the user-selected CNCF Phase 77 StateMachine API/SPI
runtime design at commit
[`766df74a3bcd6e8286c5ef88fa2bf1bf9fbae036`](https://github.com/asami/goldenport-cncf/blob/766df74a3bcd6e8286c5ef88fa2bf1bf9fbae036/docs/phase/phase-77.md).
That design defines the consumer seam which Cozy Phase 73 must produce. It is
not a request to recreate the same contract in a separate Phase 73 intake.

That commit is the inherited semantic source, not evidence of current external
acceptance. CNCF Phase 77 is now closed for the released Cozy 62.1–62.3
surface. The CAM producer can therefore be delivered only to a canonical
successor admission receiver: CWF-77-04C in CNCF Phase 77.1, accepted in
commit `3006494cdd279c44c37831387184554f7a202f23`. That receipt establishes
the bounded producer-to-receiver handoff only; this design deliberately makes
no CNCF runtime or `sm-workflow` completion claim.

Phase 73 makes one explicit additive compatibility choice. The closed Phase
62.3 producer fixture and all of its generated Workflow outputs remain
unchanged: `cozy.cml.statemachine-workflow-abi.v1`,
`cozy.cml.statemachine-workflow-bootstrap.v1`, and
`target/cozy/statemachine-workflow-abi.json`. Candidate-admission lowering
does not reinterpret that generic ABI.

The new independent product has schema identity
`cozy.cml.candidate-admission-producer-abi.v1`, bootstrap identity
`cozy.cml.candidate-admission-producer-bootstrap.v1`, and canonical sidecar
`target/cozy/candidate-admission-producer-abi.json`. It is additive and does
not alter Phase 62.3 ComponentFactory types. Its files are emitted only when
the normalized source declares at least one Candidate-Admission Action; a
Phase 62.3-only source therefore retains its exact generated output tree.

The representative canonical sidecar is additionally retained as the
source-tracked handoff fixture
`src/test/resources/modeler/candidate-admission-producer-abi.json`. A public
lowering route must reproduce those bytes before that fixture can be offered to
the external admission receiver. This fixture preserves producer facts only;
it is neither a local replacement for the accepted CWF-77-04C receiver runtime nor a direct
`sm-workflow` input.

## Semantic boundary

A semantic Action constructs typed candidate information. The StateMachine
consumer alone admits that candidate and selects any resulting progression.

```text
semantic JudgmentAction
  -> Candidate / JudgmentResult / Evidence
  -> deterministic admission boundary
  -> consumer StateMachine guard and transition
```

`JudgmentAction` is the reference Action classification. Its typed request
preserves goal, context, alternatives, criteria, and expected result.
The Candidate-Admission descriptor records no selected alternative, transition,
or next-state directive.

Candidate identity, alternatives, evidence scope, freshness, provenance, and
the declared admission/progression relation must be represented in the Cozy
source model and generated producer ABI. Domain payloads remain
application-owned.

## Provider and continuation separation

Action semantics are independent of execution placement:

```text
Action
  -> Required SPI
  -> Provider binding
  -> ActionExecution
       Completed(Result)
       Suspended(Continuation)
       Failed(Error)
```

`Suspended(Continuation)` is a Provider execution outcome, not a declarative
Action, Participant, or Workflow mode. The producer must not add
`InvocationBinding = ORCHESTRATION | CONTINUATION`, provider identity, or
AI/Codex/human/transport semantics to the candidate-admission model.

Continuation identity, expected revision, context snapshot, completion, and
evidence remain the established generic StateMachine ABI boundary. Phase 73
does not implement continuation persistence, provider dispatch, execution, or
resume validation.

## Producer responsibility

Phase 73 introduces only the provider-neutral CML/IR classification and
generated information required for CNCF to admit the candidate contract
without CML reparsing, display-name inference, or a handwritten Cozy
replacement. `CandidateAdmissionProducerAbiGenerator` accepts normalized
`CompositeStateMachineDefinition` and `WorkflowDefinition` values. It emits a
descriptor only for a definition with CAM Actions.

Each descriptor carries fixed schema and generator provenance; the Composite
StateMachine identity, name, and source; optional Workflow identity, version,
root source, definition source, and Required SPI correlation; typed Judgment
and Admission descriptors; and an ordered `JudgmentAdmission` relation. A
Judgment preserves its resolved operation/input, goal, context, candidate,
alternatives, criteria, expected result, typed rationale, evidence, evidence scope, evidence
freshness, and evidence provenance. An Admission preserves its candidate
Judgment, resolved operation/input, and `LOCAL` / `REQUIRED` boundary.

All values retain normalized-IR source objects: definition and Action source,
and sources for GOAL, CONTEXT, CANDIDATE, ALTERNATIVE, CRITERIA,
EXPECTED-RESULT, RATIONALE, EVIDENCE, EVIDENCE-SCOPE, EVIDENCE-FRESHNESS,
EVIDENCE-PROVENANCE, and CANDIDATE-ACTION. The canonical JSON sidecar mirrors
the complete typed data in deterministic input order. Generated Scala adds
only separate ComponentFactory discovery metadata and bootstrap.

An impossible malformed normalized graph fails closed with a `CAM-73-02`
diagnostic before output: missing or ambiguous Judgment/Admission pairing,
blank or null required Judgment references, null or empty collections, CML
normalized-duplicate alternatives or criteria, missing Admission `LOCAL` /
`REQUIRED` metadata, or absent source provenance. Parser validation remains
intact, and lowering repeats these semantic checks so a corrupted normalized IR
cannot publish an invalid ABI.

The exact CML spelling, Scala hierarchy, and new schema version are producer
decisions made in Phase 73 after inventorying the released Phase 62.3 source,
generated ABI, and fixture. They must be recorded as an explicit versioned
compatibility decision; source compatibility is never inferred from a similar
Action name.

## Ownership

Cozy owns declarative semantics, lowering, generated ABI, diagnostics,
deterministic sidecar, fixture, and producer evidence. CNCF owns Required SPI
binding, outcome handling, durable Continuation/resume, and StateMachine
progression. `sm-workflow` owns application payload and domain policy. Neither
consumer may replace the Cozy producer contract by reparsing CML or by
maintaining a handwritten shadow model.
