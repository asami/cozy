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

These are bottom-up supplier specifications. The top-down question fixtures in
S54.7.2 and final compatibility/adoption evidence in S54.7.3 remain pending.

Consumers use the accepted Terminology and Use Case navigation, Classification
and Dynamic topology, and Causal traversal APIs. Only Cause/Reaction assert
causation. Membership, affected subjects, names, arbitrary references, workflow
lists, guards, expressions, classification, and BoK candidates imply no execution,
equivalence, or inferred causation. External remains external even when resident;
anonymous equal records retain occurrence multiplicity without fallback identity.
There is no generic query engine or external consumer acceptance in this contract.

See [design](../design/cml-semantic-publication.md) and
[Phase 54.7](../phase/phase-54.7.md).
