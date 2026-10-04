# CML Capability Model Specification

Status: normative semantic IR contract

## Authority and scope

This specification defines the conceptual Application Capability and Component
Capability IR, its qualified identities, declarations, relationships, versions,
provenance, grounding, and validation boundaries. R1 through R12 are normative.
The carrier tables describe required semantic data, not an implemented Scala
facade, codec, admitted Graph, CML syntax, or external wire shape. E1 through E12
are expected semantic examples, not executed tests or fixtures.

The [Capability Model design](../design/cml-capability-model.md) provides
structural context. The [CML Semantic Foundation](cml-semantic-foundation.md)
and [CML Terminology Metadata](cml-terminology-metadata.md) own the existing
qualified identity, Presence, reference-boundary, and vocabulary semantics.
CNCF's **Component Capability Specification R1-R10 / Phase 78** owns the
external architectural contract; its **Component Identity Specification /
Phase 56** remains the authority for runtime Component identity. This
specification does not redefine either CNCF authority.

[Phase 64](../phase/phase-64.md) and its
[checklist](../phase/phase-64-checklist.md) track work and acceptance separately.

## Rules

### R1 Capability kinds and non-instantiated meaning

Capability MUST be a non-instantiated declarative model. The IR MUST retain two
distinct record families, `ApplicationCapability` and `ComponentCapability`.
Application Capability supplies a stable application structure and management
axis and MAY exist independently of Use Cases. Component Capability expresses
the CNCF architectural contract independently of executable realization.

Both families MUST NOT hold execution, scheduling, retry, compensation,
authorization, Availability, Permission, Guard, or runtime state. This semantic
Step selects neither source constructs nor public Scala or wire APIs.

### R2 Exact qualified identity and contract version

Both record families MUST use the exact existing Foundation
`ModelElementId(modelId, elementId)`. The kind MUST be fixed for that qualified
identity within its declaring model snapshot. An identity MUST NOT occur twice
in that snapshot, including occurrences with different labels or versions, or
be declared as both Application Capability and Component Capability.

Every definition MUST have an explicitly supplied opaque `contractVersion`,
independent of source revision. A version change MUST NOT change stable
identity. Older versions acquired in separate snapshots remain an external
catalog concern.

Identity components MUST retain exact case, Unicode composition, and delimiters
under Foundation's nonempty, unpadded, ISO-control-free validity. They MUST NOT
be normalized, split by delimiters, issued through a parallel ID model or runtime
`EntityId`/`CanonicalId`, or derived from names, URIs, digests, or positions.

### R3 Complete conceptual carriers

The following tables are the required semantic shapes. Every listed field MUST
be explicitly supplied. Identity, kind, `contractVersion`, and definition source
are mandatory and MUST NOT have a default or absence. Kind is the fixed
Application or Component family tag; the tables do not choose a serialized kind
field. `name` is presentation text. Supplemental data MUST retain the existing
Presence meaning in R9. Definitions, declarations, and every edge MUST retain
their explicit owning source; the envelope MUST keep distinct declaration
vectors and supplied order.

`Vector[A]`, `Presence[A]`, `None`, and `Some` below express conceptual carriers
and existing Foundation meanings, not new public implementation signatures.

#### Definition carriers

| Carrier | Required fields | Binding |
| --- | --- | --- |
| `ApplicationCapability` | `identity: ModelElementId`<br>`contractVersion: opaque value`<br>`name: presentation text`<br>`summary: Presence[String]`<br>`description: Presence[String]`<br>`source: CapabilitySource`<br>`demandReferences: Presence[Vector[ModelLink]]`<br>`discoveryReferences: Presence[Vector[ModelLink]]`<br>`componentMappings: Presence[Vector[ApplicationComponentMapping]]`<br>`dependencies: Presence[Vector[ApplicationDependency]]`<br>`grounding: Presence[Vector[GroundingReference]]`<br>`traceability: Presence[Vector[TraceabilityReference]]` | Fixed Application kind; the exact enclosing identity and version own its mappings and dependencies. |
| `ComponentCapability` | `identity: ModelElementId`<br>`contractVersion: opaque value`<br>`name: presentation text`<br>`summary: Presence[String]`<br>`description: Presence[String]`<br>`source: CapabilitySource`<br>`discoveryReferences: Presence[Vector[ModelLink]]`<br>`grounding: Presence[Vector[GroundingReference]]`<br>`traceability: Presence[Vector[TraceabilityReference]]` | Fixed Component kind; its meaning is independent of provider declarations. |

#### Reference, declaration, and edge carriers

| Carrier | Required fields | Binding |
| --- | --- | --- |
| `VersionedApplicationCapabilityReference` / `VersionedComponentCapabilityReference` | `identity: ModelElementId`<br>`contractVersion: opaque value` | Distinct kind-specific domains; no cross-kind substitution. |
| CNCF Component reference | `namespace`<br>`localId` | Existing Phase 56 runtime Component identity; release version is separate. These semantic fields introduce no new public Scala type. |
| `ComponentDeclaration` | `component: exact CNCF Component reference`<br>`direction: PROVIDED \| REQUIRED`<br>`capability: VersionedComponentCapabilityReference`<br>`source: CapabilitySource`<br>`realizations: Presence[Vector[RealizationEdge]]`<br>`traceability: Presence[Vector[TraceabilityReference]]` | Complete key is Component identity, direction, qualified Capability identity, and contract version. |
| `ApplicationComponentMapping` | `target: VersionedComponentCapabilityReference`<br>`boundary: ReferenceBoundary`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | The logical source endpoint is the exact enclosing versioned Application Capability; `source` is edge provenance. |
| `ApplicationDependency` | `target: VersionedApplicationCapabilityReference`<br>`boundary: ReferenceBoundary`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | The logical source endpoint is the enclosing Application Capability. This is an explicit logical requirement, not execution order or containment. |
| `ModelLink` | `role: GOAL \| USE_CASE \| SCENARIO \| USE_CASE_SLICE \| COMPONENT \| SPECIFICATION \| EVIDENCE`<br>`target: existing qualified ModelReference(identity, boundary)`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | Demand permits only the first four roles; discovery retains all supplied allowed roles. The full role binds the admitted target kind. |
| `TraceabilityReference` | `role: SPECIFICATION \| EVIDENCE`<br>`target: existing qualified ModelReference(identity, boundary)`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | Independent qualified Specification or Evidence edge. |
| `GroundingReference` | `target: existing SemanticTarget`<br>`boundary: existing ReferenceBoundary`<br>`relation: Presence[RelationId]`<br>`profile: Presence[ProfileId]`<br>`context: Presence[String]`<br>`preferredLabel: Presence[LocalizedLabel]`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | Exact Foundation target and vocabulary domains; edge-specific revision-bearing source and origin. |
| `RealizationEdge` | `declarationKey: complete Component/direction/versioned Capability tuple`<br>`targetKind: OPERATION \| WORKFLOW \| STATE_MACHINE`<br>`target: existing qualified ModelReference(identity, boundary)`<br>`targetComponent: exact CNCF Component reference`<br>`targetContractVersion: opaque value`<br>`generatedAbiVersion: independently supplied value`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | Repeated declaration key agrees with its enclosing declaration; one exact target per edge. |
| `CompatibilityEdge` | `identity: exact Component Capability ModelElementId`<br>`requiredVersion: opaque value`<br>`providedVersion: opaque value`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | Directed required-to-provided compatibility for the same qualified Component Capability identity. |

#### Provenance, envelope, and generation carriers

| Carrier | Required fields | Binding |
| --- | --- | --- |
| `CapabilitySource` | `modelAuthority: supplied exact authority`<br>`path: canonical project-relative POSIX path`<br>`sourceRevision: upstream-authored explicit value`<br>`line: explicit None \| Some(positive line)` | Location and revision remain independent of identity and contract version. |
| `CapabilityOrigin` | `Declared \| Derived(ruleId, nonempty ordered Vector[CapabilitySource])` | Declared and derived facts remain distinct; derived inputs preserve supplied evidence and order. |
| `CapabilityModel` | `applicationCapabilities: Vector[ApplicationCapability]`<br>`componentCapabilities: Vector[ComponentCapability]`<br>`componentDeclarations: Vector[ComponentDeclaration]`<br>`compatibility: Presence[Vector[CompatibilityEdge]]` | Retain supplied order and source ownership. Zero definitions or declarations differ from source acquisition failure. |
| `GenerationContext` | `projection semantic generation`<br>`producer identity/version`<br>`generated CNCF ABI version`<br>`Component identity`<br>`package coordinate/release`<br>`explicit declaration/edge source revisions` | Separate supplied generation inputs; no concrete wire keys or invented ABI value. |

### R4 Demand and discovery model links

Application demand and discovery MUST be separate edge sets to qualified Goal,
UseCase, Scenario, or UseCaseSlice records. Discovery MAY additionally reference
Component, Specification, or Evidence records. Component Capability discovery
uses the supplied permitted discovery roles. Each edge MUST retain its full
role, explicit boundary, owning source, and declared or derived origin.

Several Use Cases MAY require one Application Capability, and one Use Case MAY
require several Application Capabilities. These associations MUST NOT infer
inverse edges, containment, Use Case Group parentage, or one-to-one induction.

The narrative `UseCaseProjection.goal` string, local `declaredId`, display name,
or other text MUST NOT become a qualified Goal or Capability ID. When no
admitted qualified target exists, the model MUST retain an explicit
`NotRepresented` or `Unsupported` gap or reject an explicitly malformed
reference. It MUST NOT invent a target.

### R5 Component definitions and directional declarations

`ComponentCapability` MUST define a versioned architectural meaning
independently of providers. A `ComponentDeclaration` MUST bind the existing
CNCF namespace/local-ID Component identity, a `PROVIDED` or `REQUIRED` direction,
and a `VersionedComponentCapabilityReference`.

The uniqueness key MUST be the complete tuple
`(Component identity, direction, qualified Capability identity, contractVersion)`.
Several Components MAY provide one reference, and a Component MAY both provide
and require it. Duplicate or conflicting complete declarations MUST fail;
first-wins or last-wins behavior MUST NOT resolve them.

Component model-element references and runtime Component identity MUST remain
distinct domains. A model label MUST NOT select a runtime Component.

### R6 Application mappings and realization edges

An `ApplicationComponentMapping` MUST link one exact enclosing versioned
Application Capability to one versioned Component Capability per edge and retain
boundary, edge source, and origin. It MUST NOT select providers or establish
execution.

A `RealizationEdge` MUST separately link one exact Component declaration key to
exactly one `OPERATION`, `WORKFLOW`, or `STATE_MACHINE` target. It MUST retain
qualified target model scope, target-owning CNCF Component identity, opaque
target contract version, exact generated ABI version, edge source, and origin.
The enclosing declaration and any repeated `declarationKey` MUST agree.

Explicitly empty relationships are valid model facts. Dangling, ambiguous,
wrong-kind, or incompatible declared realization targets MUST fail admission.
An External boundary does not waive exact target contract and ABI evidence.
Mappings and realization edges MUST NOT embed ordering or control flow.

### R7 Independent revision-bearing source and origin

`CapabilitySource(modelAuthority, path, sourceRevision, line)` MUST retain a
supplied upstream-authored revision and canonical project-relative POSIX
location. Paths MUST reject absolute, drive, URI, backslash, empty, dot,
dot-dot, and ISO-control-character forms. A supplied line MUST be positive;
explicit `None` remains unknown.

A definition's source authority MUST equal its identity's `modelId`. An edge
or declaration source MUST match its declaring model authority, rather than an
external target's authority. Definition and edge revisions MUST be retained
independently. `Derived(ruleId, inputs)` MUST retain a valid supplied rule and a
nonempty ordered vector of valid `CapabilitySource` inputs; `Declared` remains
a distinct origin. Derivation evidence MUST NOT be inferred.

The new source contract MUST NOT introduce hashing, computed byte revisions,
source-currentness proof, or content identity. Legacy Foundation
`SourceAttribution.sha256` remains in its existing upstream domain. It MUST NOT
be copied into the new Capability source or projection, or become identity,
revision, permission, or admission authority. Importing Foundation reference
facts MUST require explicitly supplied source revisions for every source,
including derived inputs. The legacy digest MUST NOT fill a missing revision.

### R8 Grounding and vocabulary authority

`GroundingReference` MUST reuse exact Foundation `SemanticTarget` values
`ModelTarget(ModelElementId)` and `TermTarget(TermId)`, `RelationId`,
`ProfileId`, `ReferenceBoundary`, `LocalizedLabel`, and Presence meaning.
`TermId(vocabularyId, termId)`, `RelationId(vocabularyId, relationId)`, and
`ProfileId(vocabularyId, profileId)` retain their own literal qualified domains
and Foundation validity; no domain substitutes for another.

Grounding MUST retain supplied relation, profile, context, preferred label,
boundary, declared or derived origin, rule/input evidence, and its edge-specific
`CapabilitySource`. `LocalizedLabel(text, language)` retains literal text and
explicit `None` or `Some(language)` without normalization. The new
revision-bearing source and origin carriers replace no Foundation identity or
peer API.

Supplied Domain Concept, Glossary, and BoK facts MUST remain distinct, preserving
opaque vocabulary authority. The IR MUST NOT infer name/IRI equivalence, merge
synonyms, or require ontology completeness. A declared Local target MUST resolve
uniquely in its admitted scope. Unknown External grounding MUST remain explicitly
external. An unsupported model family MUST NOT become a synthetic Term.

### R9 Presence, boundaries, and independent traceability

Presence MUST be exactly `Present(value)` or `Absent(reason, detail)`, with the
closed reasons `NotDeclared`, `NotRepresented`, `Unsupported`, and
`NotApplicable`. Every supplied reason and detail MUST survive unchanged;
details retain Foundation's nonempty, unpadded, ISO-control-free validity. A
present empty vector MUST NOT become absence or failed acquisition.

`ReferenceBoundary` MUST remain explicit `Local` or `External`, including when
the same target pair is present locally. Admitting External grounding or
traceability MUST NOT certify target availability. Declared realization
admission still requires exact target contract and ABI evidence.

Specification and Evidence MUST remain independent qualified edges, each with
its own source and origin. Their presence MUST NOT prove successful execution,
coverage, authorization, or runtime availability. Independent reference lists
MUST NOT generate cross-products.

### R10 Validation, dependency cycles, and directed compatibility

Capability validation MUST reject malformed or absent mandatory identities,
versions, and sources; duplicate definitions or complete declarations; missing
or ambiguous Local targets; wrong-kind realization or mapping targets; and
incompatible versioned references.

`ApplicationDependency` MUST represent an explicitly supplied non-executable
requirement from its enclosing Application Capability to an exact versioned
Application Capability. Local dependency self-loops and directed cycles MUST
fail. External dependencies MUST retain an explicit unresolved boundary and
MUST NOT certify global acyclicity. Many-to-many demand associations, general
traceability, and grounding are not dependency edges and MUST NOT be rejected
merely for cycles.

Component compatibility MUST use exact contract-version equality or a supplied
directed same-identity `requiredVersion`-to-`providedVersion` edge. It MUST NOT
infer SemVer, transitivity, provider order preference, cross-identity equivalence,
or name matching. Compatibility does not waive realization target/ABI checks.

### R11 Generation domains and external ownership

External generation MUST retain only Component definitions/declarations,
grounding, realization, and traceability required by CNCF Component Capability
R1-R10. Application definitions, demand, discovery, dependency links, and
management information MUST remain Cozy-owned upstream data. A generated
external projection MUST NOT include Application Capability lifecycle state.

`GenerationContext` MUST distinguish projection semantic generation 1, producer
identity/version, generated CNCF ABI version, Component identity, package
coordinate/release, and explicit declaration/edge source revisions. None of
these domains substitutes for stable Capability identity or its contract version.

CNCF CAP-78-02 owns concrete external wire keys, codec, and the exact supported
ABI set. Cozy CAP-64-04 MUST consume that accepted contract before generating
output. This specification freezes no guessed ABI, extension namespace,
resource filename, JSON shape, or CML keyword.

### R12 Implementation and proof boundary

This Step freezes semantic IR and conceptual carriers only. The remaining work
is separately owned:

| Step | Implementation and executable proof responsibility |
| --- | --- |
| CAP-64-02 | Syntax, normalization, parser/metamodel, and actual Scala IR. |
| CAP-64-03 | Structured validation and source-correlated diagnostics. |
| CAP-64-04 | Deterministic multi-CML generation against the accepted CNCF external contract. |
| CAP-64-05 | Real producer, admission, and consumer proof. |

The examples below MUST NOT be treated as executed tests or fixtures. Failure
outcomes MUST remain typed and source-correlated: invalid, unsupported,
unavailable, unauthorized, stale, ambiguous, or incompatible input MUST NOT
become an admitted empty relation or `NoProvider`. `NoProvider` requires an
explicitly selected, successfully acquired, admitted catalog scope under CNCF
R10; failed source acquisition cannot establish nonexistence.

Textus CBD Support CAP-11-01 remains incomplete until actual Cozy-generated
input and CNCF executable admission exist. A semantic documentation contract
does not supply producer, admission, or consumer acceptance evidence.

## Semantic acceptance matrix

These examples specify expected outcomes only. The future implementation and
executable specifications must establish their behavior under the R12 owners.

| ID | Case | Expected semantic outcome | Rules |
| --- | --- | --- | --- |
| E1 | Standalone Application Capability has no Use Case links. | Retain the stable application record and exact present-empty or explicit absence. | R1, R2, R4, R9 |
| E2 | Two Use Cases require two Capabilities with shared endpoints. | Preserve all four explicit many-to-many edges and their sources; infer no grouping or inverse edges. | R4, R7 |
| E3 | Component Capability is discovered from a Scenario, and an Application Capability maps to it. | Keep discovery, Application mapping, and Component provided/required declaration separate. | R4, R5, R6 |
| E4 | Two Components provide one reference; one also requires it. | Retain three distinct full declaration keys; reject a repeated complete key. | R2, R5, R10 |
| E5 | Equal labels occur on exact IDs, or contract versions/source revisions change. | Do not merge or issue IDs; preserve distinct contract-version, source-revision, ABI, and package domains. | R2, R7, R11 |
| E6 | Goal narrative or local declaredId has no qualified model target. | Retain an explicit gap or reject a malformed declared reference; derive no identity from text. | R4, R9, R10 |
| E7 | Local/External Term and Domain grounding include declared/derived edges. | Preserve exact target, relation, profile, source, and evidence; infer no ontology or synonyms. | R7, R8, R9 |
| E8 | Realization is explicitly empty, or a declared target is unknown, wrong-kind, or incompatible. | Retain the empty architectural fact; reject failed declared binding with source-correlated diagnostics. | R6, R9, R10 |
| E9 | Capability source revision is missing while a legacy Foundation sha256 exists. | Do not use the digest as revision or admission authority; retain an explicit missing-source diagnostic. | R7, R12 |
| E10 | Application dependencies form a cycle, while grounding or ordinary many-to-many links also cycle. | Reject the Local Capability dependency cycle only; preserve unrelated supplied semantic relations. | R4, R8, R10 |
| E11 | Component versions differ and a directed compatibility edge is supplied. | Match only the same qualified identity in the supplied required-to-provided direction; infer no transitivity. | R5, R10 |
| E12 | Application demand and Specification/Evidence references exist, but producer input is unavailable. | Keep upstream management and independent traceability facts; claim neither executed proof nor admitted-empty/NoProvider. | R9, R11, R12 |
