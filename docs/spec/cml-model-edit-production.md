# Source-owned model editing production

Status: implemented and independently accepted, 2026-10-27.
Consumer usage: CBD Support `cbd-phase12-model-edit-source-production`, revision 1.

This is an additive source-owner contract. Existing semantic-metadata,
semantic-strength, Use Case and Causal v1 schemas and their accepted meaning
remain unchanged. The new source declaration is not an opaque extension to those
schemas and does not confer any existing v1 capability.

## Source grammar and ownership

One exact top-level `# MODEL-EDIT` leaf in actual CML contains five single-line
`key = JSON-value` declarations: `schemaVersion`, `source`, `coverage`, `elements`,
and `relationships`. The schema is `cozy.cml.model-edit.v1`. This follows the
existing Capability source acquisition and record syntax. Kaleidox parses the
actual entire CML; the producer admits the reserved section through its own
closed typed reader. Unknown fields, enum values, nested sections, duplicate
keys/roots and unsupported versions are explicit diagnostics. A different
namespace or a label spelling does not establish support.

`source` has explicit `owner`, `identity`, `revision`; the header must match the
selected producer input exactly. Every element and relationship has its own
`ref` (owner, stable identity, positive logical revision) and source attribution.
There is no identity allocation, name matching, order-derived ID, hash, CAS,
replay, Job, persistence or external operation in this contract.

Elements distinguish Actor, independent Goal, UseCase, Slice, Command, Operation,
Aggregate, Entity, DomainEvent, Policy, Component, Query and View. Goal and Slice
retain explicit intent. Slice retains title, fields (name/datatype/multiplicity),
scope/acceptance references and lifecycle: Proposed, InDevelopment, Implemented,
Abandoned. Lifecycle is a source declaration, not proof of delivery or ticket
completion. A shared Actor is one explicit stable reference, not copied per View.

Relationships distinguish actor-goal, goal-use-case, use-case-slice,
slice-specification, slice-ticket, cause, reaction, dependency and context.
Each owns its independent reference, revision, endpoints and attribution. Only
explicit Cause/Reaction assert causal meaning; contextual references do not.
Local endpoints must resolve to their exact declared element revision. External
model endpoints remain external even if a matching local element is resident.
Specification endpoints retain owner, identity and sourceRevision. Ticket
endpoints retain provider, owner and immutable externalIdentity; an optional
state/revision observation is separate from that identity. Linking does no I/O.

Coverage is independently declared per element/relation family. `present` with
zero records is present-empty. Omitted coverage is `missing`. `unavailable`,
`unsupported` and `external` are distinct explicit states with details. Facts
cannot be supplied under missing/unsupported/unavailable coverage. Incomplete
coverage cannot prove an absent relationship. Legacy CML without MODEL-EDIT is
reported as unsupported source semantics where declarations are present; no
identities or causal meaning are synthesized from ordinary names or references.

## Supported API and wire boundary

Public types are in `cozy.modeler.CmlModelEditModel`; closed JSON codecs are in
`CmlModelEditJson`. `CmlModelEditProduction` supplies:

```
produce(path: Path, source: Source): Either[Vector[Diagnostic], Publication]
read(value: JsValue): Either[Vector[Diagnostic], Publication]
readCandidate(value: JsValue): Either[Vector[Diagnostic], Candidate]
renderCandidate(path: Path, source: Source, candidate: Candidate):
  Either[Vector[Diagnostic], Rendering]
```

Publication schema is `cozy.cml.model-edit-production.v1`; candidate schema is
`cozy.cml.model-edit-candidate.v1`. The entire selected source's typed model edit,
trace and causal records are produced together, with actual input Source and
executing BuildInfo producer version/Git revision/modified flag. Reading a wire
admits shape and graph consistency; it does not authenticate supplied provenance
or prove that a source file exists. Unsupported data never becomes an empty graph.

Candidate operations replace explicit existing owned elements/relationships or
remove exact existing owned relationships. Replacements preserve owner/identity
and require a caller-supplied higher logical revision. The successor Source is
explicit and must retain owner/identity with a different revision. New element
creation and arbitrary legacy syntax edits are outside this initial rendering
surface. Local endpoint revision changes require explicit corresponding edge
edits; the renderer does not infer relationships or silently repair references.

Rendering validates the resulting graph, replaces only the declared MODEL-EDIT
block and returns text. Other source bytes and historical attributions remain.
It never writes canonical source, approves a candidate, resolves tickets or
promotes through CBD's canonical gate. The proposed text is then read as actual
CML with the explicit successor Source through the same `produce` operation.

Public JVM CLI main `cozy.modeler.CmlModelEditCli` exposes the same operations:

```
produce <model.cml> <owner> <sourceIdentity> <sourceRevision>
render-candidate <model.cml> <owner> <sourceIdentity> <sourceRevision> <candidate.json>
read <publication.json>
```

It returns JSON on stdout; callers own candidate output destinations. Runtime
classpath export supplies this main from the normal development runtime. No
global installation or publication is required to use the exported classpath.

## Executable acceptance

The executable specification must use an actual representative CML file with
shared Actor, Goal/intent, UseCase, implementation Slice/fields/lifecycle, all
five independent trace relationships, Specification and observed Ticket, and
declared Command/Operation, Aggregate/Entity, Event, Policy/Reaction, external
Component/dependency and Query/View. It must prove parse/produce/read and actual
edit/render/reparse, stable identities and attribution, untouched canonical
source, explicit revision/endpoint handling, coverage distinctions, missing and
unsupported syntax, external stopping and no inferred cause. Property-based
checks vary independent labels without changing identities or relationships.
Real CLI execution and normal compilation/focused regression, independent
integrated review and local commit are required before producer delivery.

CBD owns mapping to its Views, Actor Goal List, Event Storming and development
trace, integration validation and Phase acceptance. This contract does not close
CBD Phase 12 or its FU-01/FU-03/FU-07 follow-up obligations.
