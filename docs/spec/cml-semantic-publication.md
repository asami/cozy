# CML semantic publication contract

The normative executable supplier contract is
[CmlSemanticPublicationSpec](../../src/test/scala/cozy/modeler/CmlSemanticPublicationSpec.scala),
SP-01 through SP-08. `CmlSemanticPublication` admits the complete consumer-neutral
publication before a consumer relies on an individual family. Its contract version
is `cozy.cml.semantic-strength.v1`. The wire remains the unchanged
`cozy.cml.semantic-metadata.v1` object with `schemaVersion`, `elements`, `terms`,
and `extensions`; the contract version adds no wire field or namespace.

## Registered suppliers

Every present known child is independently read on the same complete root in this
fixed order. The first failed supplied family determines the failure.

| Order | Namespace | Child version | Reader | Diagnostic label |
| --- | --- | --- | --- | --- |
| 1 | `cozy.cml.structure` | `cozy.cml.structure.v1` | `CmlStructureMetadata.read` | Structure |
| 2 | `cozy.cml.classification` | `cozy.cml.classification.v1` | `CmlClassificationMetadata.read` | Classification |
| 3 | `cozy.cml.dynamic` | `cozy.cml.dynamic.v1` | `CmlDynamicMetadata.read` | Dynamic |
| 4 | `cozy.cml.use-case` | `cozy.cml.use-case.v1` | `CmlUseCaseMetadata.read` | Use Case |
| 5 | `cozy.cml.terminology` | `cozy.cml.terminology.v1` | `CmlTerminologyMetadata.read` | Terminology |
| 6 | `cozy.cml.causal` | `cozy.cml.causal.v1` | `CmlCausalMetadata.read` | Causal |

Workflow and StateMachine are Dynamic carriers. Registration is exactly this list;
an unknown namespaced object, including an unknown `cozy.*` object, stays opaque
and proves no capability. A malformed known family fails admission and cannot
be treated as missing or opaque. Each accepted child specification continues to
own its unchanged version and meaning.

## Typed API and admission

The package-private `CmlSemanticPublication` exposes:

```scala
val schemaVersion: String
val contractVersion: String
val registeredNamespaces: Vector[String]
def build(envelope: CmlSemanticMetadata.Envelope):
  Either[Vector[PublicationDiagnostic], Publication]
def read(value: JsValue): Either[Vector[PublicationDiagnostic], Publication]
def toJson(publication: Publication): JsObject
def canonicalJson(publication: Publication): String
```

`Publication` is an immutable, constructor-restricted class with `envelope`,
`catalog = envelope.catalog`, and `structure`, `classification`, `dynamic`,
`useCase`, `terminology`, and `causal`. Each family field is
`Presence[its supplier's full Graph]`. There is no public constructor or `copy`
that bypasses admission.

`build` re-admits the supplied catalog and extensions through the accepted base
factory, rendering, and reader. On success it retains the caller's Envelope and
Catalog. `read` first admits the untrusted root through the accepted base reader,
then admits all present registered peers. Neither API reads source files, infers
identities, nor performs migration or external resolution.

For a missing registered namespace, its field is exactly
`Absent(NotDeclared, "No admitted <label> metadata is supplied.")`, using the
label above. A valid supplied namespace is `Present(full admitted Graph)`;
raw carriers, provenance, core occurrences, Terms, and field-level Presence
remain intact. Optional absence is valid only when the base and remaining
supplied children independently admit it. Removing Dynamic from populated
supplied Causal does not authorize rewriting its summary.

Available empty vectors, missing families, explicit field gaps, and unknown
queries remain distinct. A present admitted Graph promises admission; a grounded
query supplies the evidence for a particular capability. No publication-level
gap summary replaces child field gaps.

## Fixed diagnostics and null safety

`PublicationDiagnostic(kind, path, detail)` uses a sealed
`PublicationDiagnosticKind`: `InvalidShape`, `PublicationFailure`, or
`InvalidSupplier`.

| Condition | Kind | Path | Exact detail |
| --- | --- | --- | --- |
| `build(null)` | InvalidShape | `envelope` | An admitted semantic metadata envelope is required. |
| Invalid base, unsafe JSON/publication, or caught boundary `NonFatal` | PublicationFailure | `publication` | The semantic metadata publication is invalid. |
| Present known family rejected by its reader or boundary `NonFatal` | InvalidSupplier | `extensions.` followed by its registered namespace | The supplied &lt;label&gt; metadata is not admitted. |

The base fails before any family. Diagnostics contain no hostile input, unknown
key, exception text, or supplied identity. Actual Java-null values and object keys
are detected recursively at JSON object/array boundaries before untrusted
publication rendering or reading. `JsNull` is valid JSON and retained. Domain
failures are typed `Left` results; boundary guards catch `NonFatal`, never Errors.
Rendering a null internal Publication violates the fixed DbC precondition:
`An admitted semantic publication is required.`

## Rendering and compatibility

Rendering delegates to the original Envelope's base methods. Canonical rendering
recursively sorts object keys while retaining authored array order, duplicate
occurrences, all core/Term fields, opaque objects, JSON nulls, and exact strings.
It does not claim RFC 8785, signatures, or source-file currentness. Synthetic
fixture sources are admitted provenance data. ModelElementId and TermId remain
separate qualified identity pairs. An outer core change requires a new schema and
a separately designed migration; a registered child change requires its own
version/design. Scala 2.12.18, 0.3.3-SNAPSHOT, and the legacy ABI are unchanged.

## Executable evidence

| Scenario | Supplier contract |
| --- | --- |
| SP-01-declared / SP-01-absence | Both accepted full Causal resources retain all six independent full Graphs, 75 core occurrences, seven Terms, literal sources and carrier counts, and explicit gaps. |
| SP-02 | Empty core has six exact missing-family absences; opaque unknown `cozy.*` JSON null survives. |
| SP-03 | Every known unknown child version fails with its fixed diagnostic; multiple failures obey registered order. |
| SP-04-A / SP-04-B | Independently built empty families and their complete combination remain available; missing Causal is valid on full input; inconsistent missing Dynamic fails. |
| SP-05 | Invalid base/version/missing root field takes precedence; embedded full-record and owned Causal-summary mismatches fail. |
| SP-06 | Java null input/envelope/nested value/key fails safely; JSON null stays valid; rendering enforces DbC. |
| SP-07 | Entire input and independent typed facts survive canonical parsing and re-admission. |
| SP-08 | Active seed 42 ScalaCheck asserts actual passed result, at least 100 successes, zero discards, and observed coverage for recursive object reconstruction in both complete modes. |

These are bottom-up supplier specifications. The authored top-down question
fixtures in S54.7.2 have accepted behavior and conformance: current compilation
succeeded, all 268 tests in 21 suites and all eleven SF examples passed, SF-11
seed 42 passed with 100 successes and zero discards, all 21 observed coverage
counts at 100 and 31 distinct execution orders, and the fresh independent focused
Step review returned PASS. Native S2 Step acceptance commit and verified receipt
remain pending; S54.7.3 final compatibility/adoption evidence and final Phase
gates remain separate and pending.

Consumers use the accepted Terminology and Use Case navigation, Classification
and Dynamic topology, and Causal traversal APIs. Only Cause/Reaction assert
causation. Membership, affected subjects, names, arbitrary references, workflow
lists, guards, expressions, classification, and BoK candidates imply no execution,
equivalence, or inferred causation. External remains external even when resident;
anonymous equal records retain occurrence multiplicity without fallback identity.
There is no generic query engine or external consumer acceptance in this contract.

See [design](../design/cml-semantic-publication.md) and
[Phase 54.7](../phase/phase-54.7.md).

## Source-grounded consumer question fixtures

`CmlSemanticPublicationFixtureSpec` is the top-down executable consumer contract
for S54.7.2. It consumes the unchanged `causal-metadata-v1-declared.json` and
`causal-metadata-v1-absence.json` full publications through `Publication.read`
and the existing navigation/topology/traversal APIs. The paired question resources
are `semantic-publication-v1-declared-questions.json` and
`semantic-publication-v1-absence-questions.json` under `src/test/resources/cozy/modeler`.
They are test-owned fixtures, without a production question wire API or parser.

Each resource has exactly `schemaVersion`, `publicationResource`, `mode` and
`questions`. Its schema is `cozy.cml.semantic-questions.v1`, its publication
filename is the corresponding unchanged Causal resource, and its mode is
`declared` or `absence`. Every ordered question has exactly `id`, `question`,
`inputs`, `expected` and `evidence`. `expected` has exactly `sourceFacts` and
`answerAssertions`: complete selected raw source objects and independent answer
assertions. Evidence is an ordered array of `{namespace, field, owner}` objects;
owner is a full qualified pair, or null for the global unsupported gap.

Declared order is Q01..Q08. Absence order is
Q01, Q02, Q05, Q06, Q08, A01, A02, A03, A04. All seventeen occurrences execute
complete finite semantic requests; merely parsing their shape proves no answer.

| Questions / requirements | Observable source-grounded answer |
| --- | --- |
| Q01 / SF-02 | Separate `ModelElementId(terminology.a, shared)` and `TermId(terminology.a, shared)` domains; full element/Term projections, three forward references, six Local reverse occurrences and resident External stopping. |
| Q02 / SF-03 | Complete UseCase and Actor projections, five ordered roles, External payment stop and full `dynamic.example/operation:complete` core with line 38 provenance. |
| Q03 / SF-04 | Direct `causal.example/cause:placement` Cause, separately derived affected-subject context, full affected Structure and member boundary; aggregate consequences are available empty. |
| Q04 / SF-05 | Both incoming placement/confirmation Causes, direct reservation Reaction, separate RuleInput/RuleOutput evidence and subsequent stock-event Cause; complete owner/source/target/provenance/order retained. |
| Q05 / SF-06 | Full Workflow/StateMachine/states/transition, Local `structure.example/aggregate:order` owner and ordered full static affected Structure; Dynamic and Causal identities stay distinct. |
| Q06 / SF-07 | Ordered status, category and status-secondary Classification declarations with status/category/status targets; two status-like dimensions remain independent full declarations. |
| Q07 / SF-08 | Complete component dependency and ordered event generic references; External remains External even when resident; Query/View causes remain available empty. |
| Q08, A01..04 / SF-09 | Full global unsupported gap, exact unknown-node diagnostic, four complete causal field absences, retained earlier answers and two equal anonymous core occurrences with distinct ordered carrier payloads. |

SF-01 executes the exact matrix independently of production output. SF-10
retains both entire Envelopes, 75 core occurrences, seven Terms, all six complete
typed graphs and five opaque peers. Counts support full object comparisons and
do not substitute for them. SF-11 runs actual seed 42 ScalaCheck with at least
100 successes, zero discards, a passed result and published observed coverage
for both modes and all seventeen occurrences. Each sample recursively rebuilds
object keys while retaining arrays, nulls, scalars and duplicate occurrences,
and separately varies question execution order while retaining literal fixture
order. It executes every mandatory complete answer in both modes.

Literal expectations come from complete predecessor raw source facts. The spec
independently admits unchanged suppliers before facade/query actions and compares
complete typed projections/core records/Terms/links with selected or reconstructed
independent objects. Raw observations also match entire literal fields, including
Presence, sources, references and nested projections. Full records govern record
correspondence; anonymous payloads retain ordered occurrence vectors. Existing
family codecs encode observations only. No expected answer is generated from a
current facade, navigation, topology or traversal output.

Every traversal-link comparison covers `owner`, `kind`, `source`, `target`,
`sourceAttribution`, `origin`, `evidenceField`, `evidenceIndex` and
`semanticReference`. Direct causal evidence retains Declared origin, field
`causalLinks` and index 0. Affected/rule context retains its exact Derived rule,
owner source vector, field and index; RuleInput uses 0 and RuleOutput uses 1.
Generic-reference context retains the entire authored SemanticReference and its
own source/origin/index. Rule cores remain full ElementRecords: there is no
invented RuleProjection or operation projection.

Only direct Cause/Reaction answers assert causation. These finite continuations
do not execute policy or operations, infer transitive causes, update read models,
merge classification dimensions, alias Terms with model elements, or recover
anonymous identity from names/source/ordinal. Unsupported causal inference is
the full owner-null `causalInference` gap with Unsupported reason and detail
`No causation is inferred from names, membership, generic relations or expression text.`
Missing family, explicit field absence, available empty consequences and an
unknown query stay distinct. Provenance records are admitted snapshots; they
do not assert that source files exist or are current.

The [S54.7.2 checklist](../phase/s54.7.2-checklist.md) records current acceptance
status. Authored executable behavior and conformance are accepted from current
VAL003 compilation, all 268 tests in 21 suites and all eleven SF examples passing,
SF-11 seed 42 with 100 successes, zero discards, a passed result, all 21 observed
coverage counts at 100 and 31 distinct question execution orders, and the fresh
independent focused Step review PASS. Native S2 Step acceptance commit and verified
receipt remain pending. S3 compatibility/handoff and final Phase gates remain
separate, and Textus CBD Support external acceptance is outside this contract.
