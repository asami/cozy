# App6.2 structured COMMAND input participation

Date: 2026-10-19

Failure: VAL-ESA62-OPERATION-JSON-GENERATION-001

Boundary: PHASE-6.2 / ESA62-02 / ESA62-02C producer Cozy, frozen Fix Manifest
esa62-cozy-structured-input-fix-20261019 revision 3.

The normal service COMMAND projector previously retained scalar OperationField
metadata while constructing a Record-only MOperation. That discarded the actual
selected input Value's structured semantic attributes. SimpleModeler's schema
generation consequently had no semantic Json input and rejected the operation
with `Operation structured datatype requires semantic input: json`.

Cozy now carries the selected inline or canonically resolved input Value through
ModelTypeProjector and one public MParameter when that Value contains builtin Json
or SemiStructuredData. The operation's body, result, access and metadata remain
under their existing construction. The shared event body reads name and title
from the request record so its fallback behavior also applies to typed actions.

The required SimpleModeler 1.1.26-SNAPSHOT producer capability owns normal Value
schema propagation and matching nonempty named COMMAND_VALUE action fields.
This Cozy change consumes that dependency through its existing declaration;
generated-source patches and generator workarounds are outside this boundary.
CNCF and server changes remain with their respective repository owners.

The paired [normative specification](../../../spec/structured-service-input-projection.md)
and [executable specification](../../../../src/test/scala/cozy/modeler/StructuredServiceInputProjectionSpec.scala)
cover inline and top-level inputs, ordered Json and ordinary fields, multiplicity,
SemiStructuredData, event effects, legacy HealthQuery and custom datatype controls.
The property varies independently shrinking field names and multiplicities with
a required 100 successes and zero discards.

Focused producer validation, local artifact refresh, independent review and the
server's real generation/Scala 3 compile oracle belong to the parent workflow.
This record documents the bounded ownership and cause; it grants no whole Cozy
Phase acceptance.

## DC-ESA62-CML-INLINE-ABI-METADATA-001

Development Candidate, nonblocking for the explicit project source ABI boundary.

The normal server attempt 034 to export the ABI automatically failed.
`CmlModelMetadata.fromCml` currently reads operation input/output names through
the type property or `INPUT`/`TYPE` paths; it does not read the nested `VALUE`
names used by the server's inline `HealthQuery`, `HealthResult`,
`BookMaterialSubmitCommand` and `BookMaterialReceipt`. Its `_sections` traversal
collects only top-level model elements. The actual server metadata consequently
has empty input/output types for health and submit and an empty `modelElements`
array, although normal typed Scala generation retains the named values and
their fields.

Future semantic metadata reconciliation should reuse the existing CML AST's
inline value types and attributes, preserve the canonical public ABI schema,
and cover full ABI export with executable specifications. This candidate is
recorded for separate work; no metadata or exporter fix is implemented here.

The existing [CAR ABI design](../../../design/car-abi-compatibility.md) supports
an explicitly project-owned current `src/main/car/abi-manifest.json`. Editing
Studio now authors that complete public contract, including both services,
both operations, all four value types and fields, and its KnowledgeHub ABI
dependency. Standard CAR packaging embeds the source declaration and normal
development-runtime preparation validates its contract and coordinates. The
project must maintain it with its CML and actual typed definition changes.
This is authored project authority, not an automatic export success or proof of
implementation conformance. Normal packaging/preparation and live connection
verification remain with the parent workflow; this entry grants no whole Cozy
Phase closure.

## Connected Step named leaf admission contribution — 2026-10-19

The successor `esa62-structured-operation-admission-fix-20261019@1` admits
`CPB-ESA62-COMMAND-JSON-CARDINALITY-001` and
`CPB-ESA62-COMMAND-RECORD-LEAF-001` in the original connected Step. Modeler owns
the internal selected-command context and keyed structured reader repair; Core
owns the prerequisite canonical structured Vector validation. Cozy production
projection needs no additional change for this repair.

StructuredServiceInputProjectionSpec now authors a realistic mixed CML command
with required, optional, repeated and nonempty Json and SemiStructuredData
attributes plus a String sibling. Normal generation must retain semantic type,
field order, schema and cardinality, and emit the selected keyed whole-container
readers. Existing event body, pure String, custom datatype and HealthQuery
controls remain part of the specification.

The server owns a test-only CML fixture generated with the normal pinned Cozy
artifact, the actual normal CNCF runtime descriptor and isolated Test outputs.
Its compiled consumer calls the actual generated public constructor, specifying
absence/null, document shape, strict collection order and nested Record field
isolation. This contribution is authored with validation pending. The parent
owns execution, artifact visibility, focused closure review and all transitions;
no test success or whole producer Phase acceptance follows from this entry.

## Contribution acceptance — 2026-10-19

The preceding authored/pending statements are historical. Final Cozy validation
050 passed 38 tests/2 suites. The actual downstream normal generated constructor
and receiver validation 052 compiled and passed 76 tests/4 suites, following
retained failures 047 (generated compilation) and 051 (fixture initialization)
and their bounded corrections. No Cozy production change followed the 046
snapshot; overlapping earlier runs are not additive.

Independent focused result `esa62-structured-operation-focused-result-20261019@1`
is PASS and accepts the limited structured input/generation contribution with
both structured-operation blockers resolved. Whole Cozy Phase acceptance and
the inline ABI metadata Development Candidate remain separate. App Phase 6.2
remains OPEN/`in_progress`; Step2/3 local commits, sole comprehensive Phase
review, owning final full validation and distinct release remain due. Strict
CAR lint exit-1/publication and domain debt remain nonblocking follow-ups.
Native 001 uninstall/data loss and original native 006/host 001 cleanup false
remain; only 52 October 9 capture files have proven capture recovery, with later
audio/configuration/credentials and full product-data recovery unproven.
