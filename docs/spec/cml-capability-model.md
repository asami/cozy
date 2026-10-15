# CML Capability Model Specification

Status: normative semantic IR, private normalization, finite validation, generation and normal production contract

## Current terminology and implementation boundary — 2026-10-15

The [Component Usage Capability contract](component-usage-capability.md) defines
`ComponentUsageCapability` as an inductive Component-owned usage model, separate
from the deductive `ComponentCapability` contract. It owns the current naming,
source/wire changes, Component ownership and no-compatibility migration.
The Scala, CML, request revision 2 and per-Component publication contracts below
use the current Usage terminology. Clearly dated prior acceptance and the
revision-1 request example remain historical. CAP-64-PKG01 authoring does not
claim new executed validation or acceptance.

## Authority and scope

This specification defines the conceptual Component Usage Capability and Component
Capability IR, its qualified identities, declarations, relationships, versions,
provenance, grounding, and validation boundaries. The current Usage contract
governs the current source/IR and R13 package route. Unchanged semantic and
Component projection guarantees remain normative; prior acceptance stays historical.
The R3 tables describe semantic data. The private Scala IR and initial authored
CML syntax are specified below for CAP-64-02. Parsed documents are not admitted
Graphs or CNCF projections. E1 through E12 remain expected semantic examples;
the C64P executable cases below passed focused validation and independent
CAP-64-02 acceptance on 2026-10-05. C64V01–32 are authored semantic validation
specifications; their focused validation and independent CAP-64-03 acceptance are
recorded in [Phase 64](../phase/phase-64.md#cap-64-03-acceptance). C64G01–33
are generation specifications with accepted focused validation, independent
protected Step review and independent focused closure recorded in
[Phase 64](../phase/phase-64.md#cap-64-04-acceptance).

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

All four original CAP-64-05 criteria are accepted as recorded in
[Phase 64](../phase/phase-64.md#cap-64-05-acceptance--2026-10-15). Separate full
Phase closure remains outstanding. The unsupported ForwardUseCase positive
production path remains not yet admitted and unexercised; Usage links
do not establish inverse demand.

## Rules

### R1 Capability kinds and non-instantiated meaning

Capability MUST be a non-instantiated declarative model. The IR MUST retain two
distinct record families, `ComponentUsageCapability` and `ComponentCapability`.
Component Usage Capability inductively describes what its explicitly owned
Component can be used for, retaining usage/discovery evidence; it MAY exist
without a direct Use Case link. Application/Domain tier terminology is unrelated
to the family name. Component Capability expresses
the CNCF architectural contract independently of executable realization.

Both families MUST NOT hold execution, scheduling, retry, compensation,
authorization, Availability, Permission, Guard, or runtime state. The original
semantic Step selects no public Scala or wire API. CAP-64-02's private source
constructs are specified below.

### R2 Exact qualified identity and contract version

Both record families MUST use the exact existing Foundation
`ModelElementId(modelId, elementId)`. The kind MUST be fixed for that qualified
identity within its declaring model snapshot. An identity MUST NOT occur twice
in that snapshot, including occurrences with different labels or versions, or
be declared as both Component Usage Capability and Component Capability.

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
Component Usage or Component family tag; the tables do not choose a serialized kind
field. `name` is presentation text. Supplemental data MUST retain the existing
Presence meaning in R9. Definitions, declarations, and every edge MUST retain
their explicit owning source; the envelope MUST keep distinct declaration
vectors and supplied order.

`Vector[A]`, `Presence[A]`, `None`, and `Some` below express conceptual carriers
and existing Foundation meanings, not new public implementation signatures.

#### Definition carriers

| Carrier | Required fields | Binding |
| --- | --- | --- |
| `ComponentUsageCapability` | `component: existing CNCF Component identity`<br>`identity: ModelElementId`<br>`contractVersion: opaque value`<br>`name: presentation text`<br>`summary: Presence[String]`<br>`description: Presence[String]`<br>`source: CapabilitySource`<br>`demandReferences: Presence[Vector[ModelLink]]`<br>`discoveryReferences: Presence[Vector[ModelLink]]`<br>`componentMappings: Presence[Vector[ComponentUsageCapabilityMapping]]`<br>`dependencies: Presence[Vector[ComponentUsageCapabilityDependency]]`<br>`grounding: Presence[Vector[GroundingReference]]`<br>`traceability: Presence[Vector[TraceabilityReference]]` | Fixed Component Usage kind; the exact enclosing identity and version own its mappings and dependencies. |
| `ComponentCapability` | `identity: ModelElementId`<br>`contractVersion: opaque value`<br>`name: presentation text`<br>`summary: Presence[String]`<br>`description: Presence[String]`<br>`source: CapabilitySource`<br>`discoveryReferences: Presence[Vector[ModelLink]]`<br>`grounding: Presence[Vector[GroundingReference]]`<br>`traceability: Presence[Vector[TraceabilityReference]]` | Fixed Component kind; its meaning is independent of provider declarations. |

#### Reference, declaration, and edge carriers

| Carrier | Required fields | Binding |
| --- | --- | --- |
| `VersionedComponentUsageCapabilityReference` / `VersionedComponentCapabilityReference` | `identity: ModelElementId`<br>`contractVersion: opaque value` | Distinct kind-specific domains; no cross-kind substitution. |
| CNCF Component reference | `namespace`<br>`localId` | Existing Phase 56 runtime Component identity; release version is separate. These semantic fields introduce no new public Scala type. |
| `ComponentDeclaration` | `component: exact CNCF Component reference`<br>`direction: PROVIDED \| REQUIRED`<br>`capability: VersionedComponentCapabilityReference`<br>`source: CapabilitySource`<br>`realizations: Presence[Vector[RealizationEdge]]`<br>`traceability: Presence[Vector[TraceabilityReference]]` | Complete key is Component identity, direction, qualified Capability identity, and contract version. |
| `ComponentUsageCapabilityMapping` | `target: VersionedComponentCapabilityReference`<br>`boundary: ReferenceBoundary`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | The logical source endpoint is the exact enclosing versioned Component Usage Capability; `source` is edge provenance. |
| `ComponentUsageCapabilityDependency` | `target: VersionedComponentUsageCapabilityReference`<br>`boundary: ReferenceBoundary`<br>`source: CapabilitySource`<br>`origin: CapabilityOrigin` | The logical source endpoint is the enclosing Component Usage Capability. This is an explicit logical requirement, not execution order or containment. |
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
| `CapabilityModel` | `componentUsageCapabilities: Vector[ComponentUsageCapability]`<br>`componentCapabilities: Vector[ComponentCapability]`<br>`componentDeclarations: Vector[ComponentDeclaration]`<br>`compatibility: Presence[Vector[CompatibilityEdge]]` | Retain supplied order and source ownership. Zero definitions or declarations differ from source acquisition failure. |
| `GenerationContext` | `projectiongeneration: Int`<br>`schemaversion: Int`<br>`generatedabiversion: String`<br>`producer: Producer`<br>`component: ComponentCoordinate`<br>`packagecoordinate: PackageCoordinate`<br>`source: CapabilitySource` | Separate supplied generation inputs; the private generation contract below consumes accepted CNCF R11 without inventing a wire or resource name. |

### R4 Demand and discovery model links

Component Usage demand and discovery MUST be separate edge sets to qualified Goal,
UseCase, Scenario, or UseCaseSlice records. Discovery MAY additionally reference
Component, Specification, or Evidence records. Component Capability discovery
uses the supplied permitted discovery roles. Each edge MUST retain its full
role, explicit boundary, owning source, and declared or derived origin.

Several Use Cases MAY require one Component Usage Capability, and one Use Case MAY
require several Component Usage Capabilities. These associations MUST NOT infer
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

### R6 Component Usage mappings and realization edges

An `ComponentUsageCapabilityMapping` MUST link one exact enclosing versioned
Component Usage Capability to one versioned Component Capability per edge and retain
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

`ComponentUsageCapabilityDependency` MUST represent an explicitly supplied non-executable
requirement from its enclosing Component Usage Capability to an exact versioned
Component Usage Capability. Local dependency self-loops and directed cycles MUST
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
R1-R10. Component Usage definitions, demand, discovery, dependency links, and
management information MUST remain Cozy-owned upstream data. A generated
external projection MUST NOT include Component Usage Capability lifecycle state.

`GenerationContext` MUST distinguish projection semantic generation 1, producer
identity/version, generated CNCF ABI version, Component identity, package
coordinate/release, and explicit declaration/edge source revisions. None of
these domains substitutes for stable Capability identity or its contract version.

CNCF CAP-78-02 owns concrete external wire keys, codec, and the exact supported
ABI set. Cozy CAP-64-04 consumes that accepted R11 contract through the private
generation entry specified below. No extension namespace or canonical CAR
resource filename is selected. The private CML field grammar below remains
separate from the generated CNCF projection.

### R12 Implementation and proof boundary

CAP-64-01 freezes semantic IR and conceptual carriers. CAP-64-02 adds the private
typed IR and source normalization below. The implementation and proof owners
remain separate:

| Step | Implementation and executable proof responsibility |
| --- | --- |
| CAP-64-02 | Syntax, normalization, parser/metamodel, and actual Scala IR. |
| CAP-64-03 | Structured validation and source-correlated diagnostics. |
| CAP-64-04 | Deterministic multi-CML generation against the accepted CNCF external contract. |
| CAP-64-05 | Real producer, admission, and consumer proof. |

Successful CAP-64-02 normalization proves syntax, lexical validity and direct
source ownership only. It MUST preserve structurally valid duplicate identities
and declaration keys, missing or ambiguous Local targets, reference versions,
dependency cycles, and disagreements between repeated realization keys and
their enclosing declarations. CAP-64-03 MUST diagnose these semantic
contradictions before admission; it also owns role/target-kind resolution,
direct compatibility policy and actual executable target ABI validation.
Normalization MUST NOT select winners, claim target availability or certify
parsed records as admitted or usable. Closed syntax rejection of embedded
execution/control/authorization fields does not close CAP-64-03.

The examples below MUST NOT be treated as executed tests or fixtures. Failure
outcomes MUST remain typed and source-correlated: invalid, unsupported,
unavailable, unauthorized, stale, ambiguous, or incompatible input MUST NOT
become an admitted empty relation or `NoProvider`. `NoProvider` requires an
explicitly selected, successfully acquired, admitted catalog scope under CNCF
R10; failed source acquisition cannot establish nonexistence.

Textus CBD Support CAP-11-01 remains incomplete until actual Cozy-generated
input and CNCF executable admission exist. A semantic documentation contract
does not supply producer, admission, or consumer acceptance evidence.

### Package publication amendment — 2026-10-15

Status: current normative contract; CAP-64-PKG01 executable authoring awaits parent validation and review.
The [Component Usage Capability specification](component-usage-capability.md)
owns the inductive model and coordinated breaking change. CNCF
`docs/spec/component-usage-capability-package.md` owns common Directory/CAR layout.
Generate `meta/component-usage-capability/index.json` and `usage.json` for the
reviewed Component alongside its existing `meta/component-capability/` family.
The payload ABI is `cozy.component-usage-capability.v1`; Component ABI remains
`cncf.component-capability.v1`. Do not treat usage evidence as provided contracts.

The private request revision 2 extends each existing `packages` entry with
explicit `usageCapabilities` references. Remove `applicationOutput`; withdraw
`applicationPackage` entirely. The selected Component/root and record ownership
must agree. Normal generation partitions records by their declared Component;
only external references cross package boundaries. No Application-only owner or
whole-application metadata package is introduced. Fixed filenames belong to the
producer; no nine-file output blacklist is needed.

Directory is consumed directly during development. Normal CAR packaging carries
the same canonical paths and records. Reuse existing resource safeguards and
actual archive entry collision handling. The revised source/IR, request decoder,
CLI, producer/native bridge, CBD consumer and fixtures switch together, without
old aliases, readers or conversion. Old typed call sites should fail compilation;
old serialized inputs fail ordinary grammar/schema validation.

R13 and the current executable case tables describe the revision-2 route.
Recorded prior Step acceptance remains evidence of its original inputs only;
no old result accepts the changed owner, request or package inputs.

### R13 Normal production, publication and Component Usage handoff

This rule governs the opt-in normal Cozy producer and package-car input. It adds
no CML grammar, semantic acquisition, native admission or provenance policy.
All selections are explicit immutable values; R1–R12 remain unchanged.

#### Normal production opt-in and legacy compatibility

Hook only existing Cozy._execute_modeler_scala for modeler-scala and modeler-scala-value. Parse and remove exactly --capability-request and --capability-source-root before existing first-positional normalization/interpreter arguments. Each accepts one --name=value or --name value spelling; reject duplicates, missing value, edge-padded/control text or only one flag. Both absent take original path unchanged. Both present require CNCF generation, existing explicit --save, actual validated CNCF runtime descriptor/SourceInput and positional CML source; Library opt-in fails before output. Retain existing component identity/catalog/style/provenance behavior and constructor signatures. Document/help these flags within owned Cozy.scala; no global mutable request/scope and no new command, default or parser grammar.

#### Closed production request and separate fact authority

`ProductionRequest` is private[cozy] and immutable: producer, ordered
`sources: Vector[CapabilitySource]`, `ScopeInput`, contexts, packages and
scalaoutputs. The JSON root has exactly `schemaVersion`, `producer`, `sources`,
`scope`, `contexts`, `packages`, `scalaOutputs`; `schemaVersion` is exactly integer
2. No loose product output or caller-selected Usage filename exists. Producer is
exactly `{id,version}` and equals `cozy`/actual `BuildInfo.version`, including
Usage-only input. Scope is the explicit Ready record; typed Rejected diagnostics
remain unchanged. Context retains exactly `projectionGeneration`, `schemaVersion`,
`generatedAbiVersion`, `producer`, `component`, `package`, `source`, with semantic
1/wire 1/ABI `cncf.component-capability.v1`. Source headers retain the complete
original record. The existing closed duplicate/trailing-token grammar rejects
null, missing, duplicate, unknown keys, wrong types or tags and noninteger schema
versions. Diagnostic request-file location never becomes product identity.

#### Exact Scope serialization

scope has exactly scopeId,source,modelFacts,termFacts,relationFacts,profileFacts,usageContracts,componentContracts,targetContracts. modelFacts items exactly identity:{modelId,elementId},kind:<the thirteen original ModelKind tags>,source. term/relation/profile facts exactly identity:{vocabularyId,termId|relationId|profileId},source. usageContracts/componentContracts exactly identity:{modelId,elementId},contractVersion,source. targetContracts exactly kind:<OPERATION|WORKFLOW|STATE_MACHINE>,identity:{modelId,elementId},component:{namespace,id},contractVersion,generatedAbiVersion,source. Keep all ordered supplied vectors and original per-field/source domains; use original foreign-source decoder for independent facts and canonical Java/Cozy Component identity validation. No new semantic/target acquisition policy, inference from parsed CML, current artifact/filename/version or empty missing facts. Original finite validator owns duplicates, kind/Local/External/version/ABI/reference/cycle decisions; its original diagnostic vector is preserved.

#### Actual source acquisition and one source per normal invocation

Normal CLI request.sources must contain exactly one explicitly selected source whose path equals the existing generation-source-identity and whose sourceRoot.resolve(path) is the actual normalized positional source. Before ordinary writes, preflightSource validates the exact selected primary path, logical identity and literal source header. GenerationProvenance.SourceInput(path,identity) identifies that selected original file; bounded Capability acquisition reads it through the existing reader and parses it with actual unchanged KaleidoxModel.parse(KaleidoxConfig.default, text). Ordinary Modeler generation may read the same original source separately; no captured bytes or replacement source copy is retained. SourceRoot and selected parent/leaf must have regular directory/file kinds without symlinks; reject traversal/noncanonical project-relative POSIX paths, missing/unreadable input, malformed UTF8 and unsupported extension other than .cml/.dox. Request acquisition and each source bounded16MiB, at most256 selected sources and total64MiB in typed API; request JSON bounded1MiB. The typed prepare API may take explicitly selected multiple source files under one sourceRoot and returns original order, but it makes no multi-source V1 provenance claim. Normal multi-CML production uses separate normal invocations/output roots and the current accepted V1/V2 aggregate/rebind bridge. Source headers/revisions are supplied semantic facts, never physical digest aliases. Parsing errors become typed acquisition issues; actual normalizer SyntaxFailure and semantic ScopeInput.Rejected vectors pass unchanged.

#### Successful private production facade

CmlCapabilityProduction.prepare(request:ProductionRequest, sourceRoot:Path, outputRoot:Path, selectedSource:Option[GenerationProvenance.SourceInput]) acquires actual sources and calls unchanged CmlCapabilityGeneration.generate. For selectedSource Some require exactly one literal selected source header and exact normalized physical path/logical identity correspondence. Null or mismatched selections fail without fallback. Some and None both acquire bounded actual selected file contents through the existing reader; None permits explicit typed multi-source local acquisition. Production Failure is closed Core(original CmlCapabilityGenerationContext.Failure), RequestOrAcquisition(Vector[Issue]), Publication(original PublicationFailure). Prepared sole private successful constructor retains exact Request, Generated/Validated, source selection, rendered Scala batch and publication batch. No raw CapabilityModel or caller-constructible successful output bypass. prepare validates all request/generation/output selections and current filesystem preflight without writing anything. publish(outputRoot,prepared) repeats ordinary filesystem contract checks and returns actual written/selected paths or truthful typed partial failure. CLI raises its existing structured invalid-argument fault with finite diagnostic kind/location/detail; never silently produces empty success.

#### Complete independent publication selection

`PackageOutput` contains `component: ComponentCoordinate`,
`packagecoordinate: PackageCoordinate`, `path: String`, `resources: Vector[ResourceOutput]`
and `usagecapabilities: Vector[VersionedComponentUsageCapabilityReference]`.
Its exact JSON keys are `component`, `package`, `path`, `resources`,
`usageCapabilities`. Each Usage reference is the closed
`{kind:"COMPONENT_USAGE",identity:{modelId,elementId},contractVersion}` record.
Resource is exactly `{snapshot,filename}`; Snapshot is exactly
`{source:{modelAuthority,path},component:{namespace,id}}`. Component coordinate is
`{namespace,id,version}`; independent package is `{coordinate,version}`.

Every generated Component SnapshotKey occurs exactly once among resources;
its complete Component release and package domains match the selected bundle.
Zero resources are allowed and create an empty Component index with no synthetic
projection, context, declaration or native target. Usage selection resolves exact
identity/contract-version pairs in validated original documents and requires the
explicit definition owner to equal the selected Component. Unknown, ambiguous,
repeated or mismatched-owner selections retain typed selection diagnostics.
Unselected records need not be published. Empty Usage selection omits that optional
family. Bundle directories and Scala files retain existing canonical target-path,
three reserved normal destinations, collision and complete Scala coverage guards.
There is no expanded normal-output reservation list.

For prepared normal opt-in, inspect the actual generated in-memory Realm before
the interpreter saves it. Recursively collect exportable leaf file paths, excluding
EmptyData leaves and traversal-only directories. Compare each actual normal path
with every selected package directory and Scala file using the existing
case-insensitive equal/ancestor/descendant overlap rule, including destinations
that do not yet exist. Report OutputConflict at the exact packages[i].path or
scalaOutputs[i].path and name the actual conflicting normal path. Valid siblings
under target/cozy remain allowed; generated filenames are not a second reservation
catalog. Include target/cozy/component-api-descriptor.json only when the actual
tree exports target/cozy/component-api-model.json or that model is already a
regular file under the selected output root, matching the existing post-writer.
This check reads only the tree and filesystem kinds and does not mutate or export
the tree.

#### Canonical accepted Component package image

Each explicitly selected Directory bundle MUST contain exactly one root
`component-descriptor.json`, `meta/component-capability/index.json` and exactly
the selected flat sibling projection JSON resources. The descriptor MUST reuse
the existing CAR schema-3 serializer through its pure internal Capability
wrapper and existing release-coordinate codec. Its `component` is exactly the
selected Component namespace, local ID and release version; package coordinate,
opaque package version, producer version, source path and labels MUST NOT
supply that identity. The minimal descriptor has `schemaVersion:3`,
`component:{namespace,id,version}`, `entities:[]`, `extensions:{}` and `config:{}`.
It MUST NOT infer native entities, knowledge, style or target evidence. Coordinate
or rendering exceptions MUST become typed Malformed issues at
`packages[i].component/descriptor`; no thrown exception or empty-success fallback.
Descriptor UTF8 is bounded separately to 16MiB, outside the Capability metadata
counts and total. Each Usage-only selected Component also has its real descriptor;
zero-bundle products emit no descriptor.

Index exactly {schemaVersion:1,projections:[filenames in explicit resource order]}; each name matches [A-Za-z0-9][A-Za-z0-9._-]*\.json, excludes index.json, count<=256 and unique. Projection content is unchanged Generated.projectionJson plus terminal newline, never reconstructing wire/records. Index<=1MiB, individual resource<=16MiB and bundle total selected metadata<=64MiB, matching actual CNCF R14 limits. Preserve authored vector order and independent source/contract/ABI/domain values; sort output artifact paths only to give deterministic publication traversal. Cozy owns this generated canonical Directory image and the normal CAR index/resource selection. The existing CAR builder retains its descriptor semantics. Actual Directory/CAR admission, native target binding and discovery remain the existing public CNCF Factory's responsibility; generated descriptors alone supply no admission-success claim.

#### Component-owned Usage projection without inverse edges

`CmlCapabilityComponentUsageProjection.encode(generated, sources, producer, output)`
consumes the original Generated/Validated, ordered selected source headers, actual
producer and explicit PackageOutput. It emits the exact closed envelope:

```text
{schemaVersion:1, generatedAbiVersion:"cozy.component-usage-capability.v1",
 producer:{id,version}, component:{namespace,id,version},
 package:{coordinate,version},
 documents:[{source:<full original header>, componentUsageCapabilities:[<selected records>]}]}
```

Only contributing documents appear, in original request source order. Each record
is explicitly selected by its exact versioned reference and owned by that package
Component; another owner's records are not copied. All source headers and full
selected definition/source/origin/Presence/edge fields remain exact and ordered.
Each definition has `kind:"COMPONENT_USAGE"`, canonical `component:{namespace,id}`,
identity, contractVersion, name, summary, description, source, demandReferences,
discoveryReferences, componentMappings, dependencies, grounding and traceability.
Mapping target is `{kind:"COMPONENT",identity,contractVersion}`; dependency target
is `{kind:"COMPONENT_USAGE",identity,contractVersion}`; both retain boundary, source
and origin. External references remain external across owners. No inverse UseCase
demand projection is inferred. Empty acquired envelopes differ from optional
family absence, although production omits the family for an empty selection.

The fixed Usage index is exactly `{schemaVersion:1,projections:["usage.json"]}`
at `meta/component-usage-capability/index.json`; the resource is the sibling
`usage.json`. It is bounded by the existing UTF8/index/resource limits and joins
the existing Component metadata family in the same selected Directory.

#### One bounded full publication batch

CmlCapabilityPackagePublication.prepare combines each selected canonical Component descriptor/index/resources, optional fixed Usage index/resource and original CmlCapabilityGenerationScala.render artifacts in one immutable private accepted batch. Preflight every path and full existing metadata inventory before any write: regular root/ancestors/leaf, no symlink, no output escaping target/, no traversal/case alias selection, no nonregular target, no unlisted/nested metadata JSON. Retain complete selection, path and ownership checks. After publication preflight, write every selected generated artifact directly with the current rendered bytes, including existing regular files on repeat generation. Preserve authored inputs, unselected files, unrelated nonJSON assets and destinations outside the selected operation. Report structured IO failure and the exact completed prefix of written paths; an unsuccessful operation is not success. There is no byte-equality precondition, temporary-file or atomic-replacement protocol, race protection, rollback or multiwriter guarantee. Recovery from ordinary IO failure is regeneration.

Root `component-descriptor.json` MUST be the sole canonical descriptor slot.
Reject all root `descriptor.{json,yaml,yml,conf,hocon,xml}`, root
`component-descriptor.{yaml,yml,conf,hocon,xml}` and
`meta/component-descriptor.{json,yaml,yml,conf,hocon,xml}` alternatives, including
case aliases and equal-content alternatives. Inspect only bundle root and
immediate meta entries with no-follow kind checks. Preserve alternatives and
unrelated root/meta assets; never choose a first descriptor, follow a symlink,
overwrite alternatives or delete them. Selected descriptor symlink/nonregular/case-alias
leaves remain UnsafeOutput. Different bytes in the selected regular root descriptor
are admissible and publication writes its current canonical contents. Regular
alternative slots remain OutputConflict with actual locations, even for equal
contents. Descriptor artifacts participate in the same sorted selection/path
collision checks and publication preflight as every sibling.

#### Normal generator ordering and existing provenance

Within the existing component-identity context, prepare the complete Capability candidate from the selected SourceInput before the interpreter or ordinary Modeler generation can mutate outputs. With valid prepared opt-in, execute the ordinary normalized source arguments and check the actual returned STree before uri_save, then write ordinary model metadata from the original positional source, write the component API descriptor, publish prepared Capability files and selected Scala carriers, then write current generation provenance so those real Scala paths participate. Request, semantic, selection, reserved normal provenance/Modeler metadata and actual normal-tree collision failures precede ordinary writes. The invocation-local check raises the existing typed diagnostic before returning a conflicting tree and retains its original issues; if the interpreter converts that error, raise the same diagnostic immediately after execute and before later model metadata, publication or provenance writers. Successful generation returns the same tree without a second generation or output copy. Actual later ordinary generation/IO failures preserve honest existing failure semantics without rollback promises. Current V1/V2 metadata validates exact known inputs, literal source identities and selected Scala path inventory; readable content changes remain admissible. No digest options, retained source bytes, output preparation protection, copied provenance or source-replacement facade is used. Metadata JSON remains outside the Scala-artifact vocabulary. A metadata-only Capability selection does not imply that the ordinary transformer emits no Scala: actual normal Scala artifacts require standard provenance describing their complete observed inventory, while actual no-Scala output emits no Scala-generation provenance. The selected primary source owns normal V1 metadata; separate typed multi-source publication invents no aggregate authority. If the original positional Capability-only CML produces no ordinary entities, it still uses the ordinary transformer and actual Capability output without a fake DomainComponent fixture.

#### Explicit normal CAR hook and strict selected archive inventory

Extend normal package-car with all-or-none --capability-dir, --capability-package-coordinate and --capability-package-version, each one literal value with duplicate/missing-value rejection. No flags keep old behavior. CozyCapabilityArchivePart selects the existing Component family and, when present, the fixed Usage index/usage.json from that same selected Directory, using strict duplicate-key index/inventory/UTF8/kind/symlink/limits and the same 256/1MiB/16MiB/64MiB contracts. Reject unexpected/nested/unlisted metadata JSON, unsafe filenames and duplicate archive names. Compare each projection envelope exact R11 root keys/schema1/ABI cncf.component-capability.v1 and canonical Component release to actual existing CozyComponentReleaseCoordinateCodec coordinate; package coordinate/version exactly the independent explicit new args. Producer is cozy with valid literal version, not inferred as executing builder version; body bytes are copied unchanged and actual full R11/R12 native semantic admission remains CNCF. The Usage envelope has exactly schemaVersion/generatedAbiVersion/producer/component/package/documents, schema 1 and ABI cozy.component-usage-capability.v1; its selected Component release and independent package agree through the same coordinate codec. Do not parse Usage record semantics in the packager. Before _write_archive creates destination ZIP, detect any canonical-prefix JSON/name collision with existing _car_entries and all other composed entries, even identical content; fail rather than discard/userasset winner. Include selected original files under their canonical archive paths alongside unchanged descriptor/ABI/provenance/runtime resources, without broad directory copy or scanning other packages.

#### Executable behavior and genuine local product inputs

Author C64I01-19/C64B01-15/C64A01-08 in four owned semantic AnyWordSpec suites with actual adjacent Given/When/Then and should matchers, all spec:R13 normative bindings. New normative R13 is Cozy normal production/publication/Component Usage handoff only; preserve all original R1-R12 and existing accepted grammar/core/provenance/wire rules. New cases actually invoke prepare/publish, actual Cozy.main normal Modeler/package-car and read real output/CAR entries; no source-shape/doc/status/header-only tests. Reuse two explicitly owned real CML fixture files by copying intentional fixture content into test-private canonical model/... source paths and use original explicit finite facts/contexts independently supplied. Do not import private helpers from unowned test companions or extend unowned tests. Test requests use actual BuildInfo.version dynamically, not hard-coded tracked producer version. Tests clean only their own temporary roots; never write canonical repositories/fixtures, invoke remote/warehouse publication, reflectively execute targets or start SBT from a test. Three active ScalaCheck properties min100 successful and zero discarded with checked passed/succeeded/discarded results.

#### CAP-64-PKG01 additional authored behaviors

The existing suites now author C64P26 (mandatory canonical Usage owner),
C64I17 (both normal commands with Usage-only Component packages), C64I18 (actual
unknown/repeated/wrong-owner selection diagnostics before normal writes), C64I19
(optional Usage absence), C64B13 (normal CAR optional absence), C64B14 (normal
Usage-only CAR), and C64B15 (selected Usage package disagreement). C64A01 now
partitions a real multi-owner source; C64A02 retains ordered contributing headers
across two explicitly authored sources for one owner. Existing active properties
retain their success and zero-discard requirements. This editing pass claims no
executed validation, independent review or cross-repository U11 proof.

#### Historical revision-1 request record (superseded for new production)

The JSON root is exactly the following record. The type names denote the full
closed records specified above and the existing R3 reference domains.

```text
{
  "schemaVersion": "integer1",
  "producer": "{id,version}",
  "sources": "ordered full CapabilitySource[]",
  "scope": "exact explicit Ready Scope (D03)",
  "contexts": "D02 closed GenerationContext[]",
  "packages": "D06 closed PackageOutput[]",
  "scalaOutputs": "[{snapshot:{source:{modelAuthority,path},component:{namespace,id}},path,packageName:[ASCII segments],objectName}]",
  "applicationOutput": "explicit canonical target/*.json"
}
```

#### Authored normal production cases

Historical authoring note as of 2026-10-14: the following pending-validation
statement is superseded only for current CAP-64-05 Step acceptance recorded in
[Phase 64](../phase/phase-64.md#cap-64-05-acceptance--2026-10-15).

Each case binds to `spec:R13` and has adjacent Given/When/Then clauses in its
owned semantic suite. Authored cases are pending parent validation and review.

| Case | Observable contract | Executable specification |
| --- | --- | --- |
| C64I01 | Actual normal modeler-scala reads primary original CML and produces canonical Component bundles, fixed per-package Usage JSON and real selected Scala artifacts before original provenance writes. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I02 | Actual normal modeler-scala-value uses the same opt-in route with exact independent header/definition/edge revisions retained. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I03 | No flags preserves ordinary generation; partial/duplicate/new malformed option rejects before output; Library rejects opt-in without mutation. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I04 | Strict request rejects duplicate/unknown/missing JSON fields, trailing tokens, Boolean/noninteger/unsupported versions and unknown tags without output. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I05 | Producer id/version disagreement rejects including empty/app-only generation; actual dynamic BuildInfo.version is retained. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I06 | All explicit finite fact/contract domains decode as supplied, including independent vocabulary/source/version/target ABI; no implicit alias or missing facts. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I07 | Actual selected primary SourceInput identity/path mismatch, repeated/multiple CLI selected source or root mismatch rejects before ordinary generation. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I08 | Missing/nonregular/unreadable selected source or symlink root/ancestor/leaf rejects; no outside source acquisition. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I09 | Malformed UTF8, traversal/noncanonical path, unsupported extension and source/request size/count bounds reject honestly. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I10 | Actual normalizer syntax diagnostics and original semantic dangling/cyclic/version/ABI diagnostics are retained with no published partial success. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I11 | Typed explicitly Rejected Scope preserves original ordered source-correlated diagnostics exactly; no empty successful result. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I12 | Complete snapshot/package/Scala selection validates unknown/duplicate/missing coverage; every context independent domains match supplied package. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I13 | Preparation touches no output; request/selection failure preserves ordinary existing sentinel outputs before output preparation. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I20 | The same ordinary-Entity-plus-Capability selected input requires an error-free parse and actual ordinary model build with rich Capability JSON opaque to unrelated Workflow/CSM metadata parsing, then actual normal metadata baselines from both real commands before four package-equality/Scala-descent collisions against initially absent composite metadata, retaining exact field/path diagnostics and only the unchanged original Scala sentinel. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I14 | Typed multi-source actual parser acquisition preserves original document order and source selection without inventing multi-source V1 provenance. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I15 | Repeated independent normal invocations do not leak scope/request through global Modeler state; actual original provenance contains the produced Scala artifacts. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I16 | Active property varies literal independent valid source/identity/revision/producer-context fields and proves request decoding preserves their values without aliases. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64I17 | Both real normal commands retain genuine Usage-only Component products with empty Capability Scala/context selections; parsed standard provenance exactly matches the independently observed sorted ordinary Scala inventory and source identity when nonempty, and is absent when that inventory is empty. | [CozyCapabilityProductionSpec](../../src/test/scala/cozy/CozyCapabilityProductionSpec.scala) |
| C64B01 | Canonical selected package index order/resource contents exactly match Generated projections and original inner order. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B02 | Separate Component releases and independent packages remain separate selected bundles; no filename-derived grouping. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B03 | Unknown/repeated/missing snapshot/resource keys, duplicate/reserved/unsafe filenames, count overflow and overlapping output paths reject whole batch. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B04 | Unlisted/nested JSON and symlink/nonregular existing metadata reject; unrelated nonJSON remains untouched. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B05 | Root/ancestor traversal or symlink and cross-bundle/Scala path conflicts fail full preflight before any write. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B06 | Repeat publication returns the same selected paths; a changed valid Scala object selection regenerates every selected artifact with exact current batch contents. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B07 | A real ordinary first write followed by a controlled second-write IOException returns typed IoFailure, the exact completed path prefix and exact first bytes, with the second target absent. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B08 | Active property permutes package traversal selections while preserving each explicit index order and proves identical semantic selected image mapping. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B09 (publication) | Actual arbitrary normal-tree leaf paths reject package equality and case-aliased Scala descent with exact field/path diagnostics and no output writes. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B11 (publication) | Non-overlapping target/cozy siblings and EmptyData leaves remain allowed; conditional descriptor overlaps from generated or already regular model files reject without changing existing bytes or inventory. | [CmlCapabilityPackagePublicationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityPackagePublicationSpec.scala) |
| C64B09 | Actual normal package-car includes exactly produced canonical index/resources with unchanged bytes and original canonical descriptor/ABI/resources. | [CozyCapabilityArchiveSpec](../../src/test/scala/cozy/archive/CozyCapabilityArchiveSpec.scala) |
| C64B10 | Actual package-car existing source-CAR canonical-prefix/index/resource collision rejects including equal content before destination ZIP creation. | [CozyCapabilityArchiveSpec](../../src/test/scala/cozy/archive/CozyCapabilityArchiveSpec.scala) |
| C64B11 | Actual package-car Component release/independent package version or coordinate/schema/ABI mismatches and malformed selected inventory fail. | [CozyCapabilityArchiveSpec](../../src/test/scala/cozy/archive/CozyCapabilityArchiveSpec.scala) |
| C64B12 | All three CAR flags required together with duplicate rejection; absent flags preserve existing normal package behavior and omit added resources. | [CozyCapabilityArchiveSpec](../../src/test/scala/cozy/archive/CozyCapabilityArchiveSpec.scala) |
| C64A01 | Per-package selected owner records retain original contributing document order and exact source headers beside independent Component metadata. | [CmlCapabilityComponentUsageProjectionSpec](../../src/test/scala/cozy/modeler/CmlCapabilityComponentUsageProjectionSpec.scala) |
| C64A02 | Literal model/vocabulary/source identities, revisions, optional positive/null line and actual producer are preserved separately. | [CmlCapabilityComponentUsageProjectionSpec](../../src/test/scala/cozy/modeler/CmlCapabilityComponentUsageProjectionSpec.scala) |
| C64A03 | Authored Usage demand/discovery edges and ordering survive; no reverse UseCase-requiredUsage edge is synthesized. | [CmlCapabilityComponentUsageProjectionSpec](../../src/test/scala/cozy/modeler/CmlCapabilityComponentUsageProjectionSpec.scala) |
| C64A04 | Every mapping and dependency has the exact versioned COMPONENT/COMPONENT_USAGE target, boundary, source and origin. | [CmlCapabilityComponentUsageProjectionSpec](../../src/test/scala/cozy/modeler/CmlCapabilityComponentUsageProjectionSpec.scala) |
| C64A05 | Present(empty), all four Absent reasons/details and explicit nulls remain distinguishable, without omission/inference. | [CmlCapabilityComponentUsageProjectionSpec](../../src/test/scala/cozy/modeler/CmlCapabilityComponentUsageProjectionSpec.scala) |
| C64A06 | MODEL/TERM grounding, independent relation/profile/preferred label and ordered derived/trace evidence sources survive. | [CmlCapabilityComponentUsageProjectionSpec](../../src/test/scala/cozy/modeler/CmlCapabilityComponentUsageProjectionSpec.scala) |
| C64A07 | Usage-only Component packages need no Component context; only contributing documents export and missing/repeated/reordered headers fail. | [CmlCapabilityComponentUsageProjectionSpec](../../src/test/scala/cozy/modeler/CmlCapabilityComponentUsageProjectionSpec.scala) |
| C64A08 | Active property varies independent Unicode literal identities/revisions/Presence and proves semantic exported JSON values equal original IR without inferred aliases. | [CmlCapabilityComponentUsageProjectionSpec](../../src/test/scala/cozy/modeler/CmlCapabilityComponentUsageProjectionSpec.scala) |

Historical S03 authoring note as of 2026-10-14: the following parent-acceptance
statement is superseded only for current CAP-64-05 Step acceptance recorded in
[Phase 64](../phase/phase-64.md#cap-64-05-acceptance--2026-10-15).

[CmlCapabilityDirectoryDescriptorSpec](../../src/test/scala/cozy/modeler/CmlCapabilityDirectoryDescriptorSpec.scala)
authors the S03 Directory cases with complete real CML, actual BuildInfo producer,
adjacent Given/When/Then and should expectations. These are authoring records;
parent validation and cross-repository acceptance remain separate.

| Case | Observable authored Directory behavior |
| --- | --- |
| C64D01 | Real normal Cozy.main produces Store/Archive schema-3 literal releases and original Component index/projection/Scala and per-owner Usage products. |
| C64D02 | Typed real two-source publication preserves Store/Archive/Remote independent Component releases and opaque package domains. |
| C64D03 | Two original sources sharing one release/package publish one descriptor and complete resources in explicit second.json, first.json index order. |
| C64D04 | A selected regular descriptor with different contents regenerates to the exact canonical descriptor shape alongside every exact selected batch artifact and path. |
| C64D05 | Selected symlink, directory and case-aliased leaves yield UnsafeOutput while target, kinds and unrelated assets remain unchanged. |
| C64D06 | Complete repeat publication restores a missing generated descriptor, returns all selected paths and writes exact current batch images. |
| C64D07 | Real Generated/rendered input with null ComponentId or invalid release yields typed component/descriptor Malformed without exception/default/partial output. |
| C64D08 | All seventeen alternative six-format root/meta slots and case aliases reject before writes, even equal generated content; unrelated assets survive clean publication. |

Historical S01/S03 status as of 2026-10-14: the following pending Step/native-proof
statements are superseded only for current CAP-64-05 Step acceptance recorded in
[Phase 64](../phase/phase-64.md#cap-64-05-acceptance--2026-10-15). Separate Phase
closure and unsupported ForwardUseCase positive production remain outstanding.

S01 and S03 are producer/publication prerequisites. CAP-64-05 remains IN_PROGRESS with
all four criteria unchecked. Actual Scala 3 CNCF decode, native target binding,
package/Factory discovery and CBD consumption without CML reparsing remain owed.
Upstream UseCase inverse-demand projection is unavailable under R4; consumers
must retain UnavailableUpstreamProjection. Separate participant Phase closure,
full validation, independent review and local release precede Cozy main integration.

## Private Scala IR and per-document input

All carriers are immutable Scala 2.12 implementation types under
`private[cozy]` objects in `cozy.modeler`. They add no public facade or dependency.
`CmlCapabilityModel` supplies the exact R3 definition, reference, declaration,
mapping, dependency, grounding, realization, compatibility and traceability
records. `ComponentUsageCapability.kind` is `ComponentUsageKind` and
`ComponentCapability.kind` is `ComponentKind`; distinct versioned reference
classes retain the corresponding fixed family. These tags are assigned by the
definition container, never by a missing-field default.

Foundation supplies its actual `ModelElementId`, `TermId`, `RelationId`,
`ProfileId`, `Presence`, `Present`, `Absent`, four `AbsenceReason` values,
`ReferenceBoundary`, `SemanticTarget`, `ModelTarget`, `TermTarget` and
`LocalizedLabel`. Capability neither redeclares those domains nor modifies
Foundation's legacy source/origin carriers. Runtime Component values are the
existing Java `org.goldenport.cncf.component.identity.ComponentId`.

| Private closed family | Scala alternatives / exact authored tags |
| --- | --- |
| CapabilityKind | `ComponentUsageKind` / COMPONENT_USAGE; `ComponentKind` / COMPONENT |
| Direction | `Provided` / PROVIDED; `Required` / REQUIRED |
| TargetKind | `Operation` / OPERATION; `Workflow` / WORKFLOW; `StateMachine` / STATE_MACHINE |
| ModelRole | `Goal`, `UseCase`, `Scenario`, `UseCaseSlice`, `Component`, `Specification`, `Evidence` / GOAL, USE_CASE, SCENARIO, USE_CASE_SLICE, COMPONENT, SPECIFICATION, EVIDENCE |
| TraceabilityRole | `SpecificationTrace` / SPECIFICATION; `EvidenceTrace` / EVIDENCE |

`CmlCapabilitySource` supplies these input, source and diagnostic carriers:

| Carrier | Complete fields / alternatives |
| --- | --- |
| CapabilitySource | `modelAuthority: String`, `path: String`, `sourceRevision: String`, `line: Option[Int]` |
| CapabilityOrigin | `Declared` or `Derived(ruleId: String, inputs: Vector[CapabilitySource])` |
| SourceScope | `modelAuthority: String`, `path: String` |
| SourceInput | `scope: SourceScope`, `model: org.goldenport.kaleidox.Model` |
| CapabilityDocument | `scope: SourceScope`, `model: CapabilityModel` |
| SyntaxIssue | `kind: SyntaxIssueKind`, `fieldPath: String`, `scope: SourceScope`, `sectionPath: Vector[String]`, `line: Option[Int]`, `message: String` |
| SyntaxIssueKind | `MalformedInput`, `InvalidStructure`, `InvalidField`, `InvalidSource`, `UnsupportedSyntax` |

The private callable entry is:

```scala
CmlCapabilityCml.normalize(inputs: Vector[SourceInput]):
  Either[Vector[SyntaxIssue], Vector[CapabilityDocument]]
```

Callers MUST supply individually parsed original documents before a legacy
`Model.+` aggregate can lose per-file identity. The entry MUST inspect each
model's parser errors before interpreting absence. Error-bearing parser/load
results yield `MalformedInput` with supplied input source context. Invalid or
missing scope and duplicate input paths yield `InvalidSource`; duplicate paths
MUST NOT select a first or last document. Acquisition, IO, CLI dispatch, imports,
revision lookup and generated-metadata reparsing are outside this entry.

Successful output retains input order, per-document ordered definitions,
declarations, relationship vectors and compatibility Presence. It MUST NOT
flatten envelopes, sort, deduplicate or issue name indexes. A successfully
parsed legacy source without a reserved new construct yields no document.
An explicitly authored empty new root yields one document with empty vectors
and its authored compatibility Presence. Every error yields Left with no
partial successful output or sibling salvage.

Issues retain input, original section and field coordinates in deterministic
supplied document/section/field order. Missing-field issues follow the required
field order. `sectionPath` contains original logical section labels and `line`
is the actual optional section-header line. Diagnostic locations MUST NOT
overwrite authored `CapabilitySource.line` or claim source byte currentness.

## Initial authored CML grammar

`CAPABILITY-MODEL` is exactly one top-level generic logical section in an
authored document. It is separate from Kaleidox's legacy `CAPABILITY` division
and deployment style bundles. There are no placement or case aliases. Original
sections are extracted from parsed divisions and contained promoted sections
are visited only through their original parent.

```text
# CAPABILITY-MODEL
compatibility = JSON-Presence-of-Compatibility-array

## COMPONENT-USAGE-CAPABILITY
### arbitrary entry label
mandatory field = one JSON value

## COMPONENT-CAPABILITY
### arbitrary entry label
mandatory field = one JSON value

## COMPONENT-CAPABILITY-DECLARATION
### arbitrary entry label
mandatory field = one JSON value
```

Each of the three containers is optional, occurs at most once, is a direct
child of the root, and has an empty prologue. An omitted container supplies an
explicitly empty model vector. Entry headings are arbitrary presentation and
diagnostic labels, including labels identical to reserved tokens. They MUST
NOT supply identity, kind, name, contract version or source. Entries are leaves.
Reserved container tokens elsewhere, or with wrong capitalization, fail.
Duplicate roots/containers, unexpected root fields, container prologue and
child sections under records fail with contextual issues.

| Section role | Exact mandatory body keys |
| --- | --- |
| Root | `compatibility` |
| Component Usage entry | `component`, `identity`, `contractVersion`, `name`, `summary`, `description`, `source`, `demandReferences`, `discoveryReferences`, `componentMappings`, `dependencies`, `grounding`, `traceability` |
| Component entry | `identity`, `contractVersion`, `name`, `summary`, `description`, `source`, `discoveryReferences`, `grounding`, `traceability` |
| Declaration entry | `component`, `direction`, `capability`, `source`, `realizations`, `traceability` |

Each field MUST be one physical nonblank `key = JSON-value` line. Split at the
first equals sign and trim only grammar around the delimiter. Decoded JSON
strings retain literal contents, including interior equals signs. Keys have
exact case; each mandatory field appears once, in any authored order. Bare
strings, multiline values, comments, code fences, aliases and convenience
defaults are unsupported. Before Play JSON decoding, Jackson core
`STRICT_DUPLICATE_DETECTION` and a one-value/trailing-token check reject
duplicate JSON keys at every depth and malformed syntax. Unknown fields are
rejected recursively. This is a private authoring representation, not a CNCF
projection envelope or producer output codec.

### Exact closed JSON carriers

Every object has exactly the keys below. Every listed key is mandatory.
`text` means valid literal text; `array[A]` retains authored order and repeats.
The R3 tables above define which Presence payload each enclosing field uses.
Only Source.line and LocalizedLabel.language allow explicit null.

| Carrier / branch | Exact keys and values |
| --- | --- |
| ComponentRef | `namespace: canonical namespace`, `id: canonical local ID` |
| ModelElementId | `modelId: text`, `elementId: text` |
| TermId | `vocabularyId: text`, `termId: text` |
| RelationId | `vocabularyId: text`, `relationId: text` |
| ProfileId | `vocabularyId: text`, `profileId: text` |
| Source | `modelAuthority: text`, `path: canonical project-relative POSIX path`, `sourceRevision: opaque text`, `line: null or positive whole Int-valued JSON number` |
| Presence / Present | `state: PRESENT`, `value: A` |
| Presence / Absent | `state: ABSENT`, `reason: NotDeclared \| NotRepresented \| Unsupported \| NotApplicable`, `detail: text` |
| Origin / Declared | `kind: DECLARED` |
| Origin / Derived | `kind: DERIVED`, `ruleId: text`, `inputs: nonempty array[Source]` |
| ModelReference | `identity: ModelElementId`, `boundary: LOCAL \| EXTERNAL` |
| SemanticTarget / Model | `kind: MODEL`, `identity: ModelElementId` |
| SemanticTarget / Term | `kind: TERM`, `identity: TermId` |
| LocalizedLabel | `text: text`, `language: null or text` |
| VersionedComponentUsageCapabilityReference | `kind: COMPONENT_USAGE`, `identity: ModelElementId`, `contractVersion: opaque text` |
| VersionedComponentCapabilityReference / CapabilityReference | `kind: COMPONENT`, `identity: ModelElementId`, `contractVersion: opaque text` |
| DeclarationKey | `component: ComponentRef`, `direction: PROVIDED \| REQUIRED`, `capability: VersionedComponentCapabilityReference` |
| Declaration | `component: ComponentRef`, `direction: PROVIDED \| REQUIRED`, `capability: VersionedComponentCapabilityReference`, `source: Source`, `realizations: Presence[array[Realization]]`, `traceability: Presence[array[Traceability]]` |
| Realization | `declarationKey: DeclarationKey`, `targetKind: OPERATION \| WORKFLOW \| STATE_MACHINE`, `target: ModelReference`, `targetComponent: ComponentRef`, `targetContractVersion: opaque text`, `generatedAbiVersion: independently supplied opaque target ABI text`, `source: Source`, `origin: Origin` |
| Compatibility | `identity: ModelElementId`, `requiredVersion: opaque text`, `providedVersion: opaque text`, `source: Source`, `origin: Origin` |
| ModelLink | `role: GOAL \| USE_CASE \| SCENARIO \| USE_CASE_SLICE \| COMPONENT \| SPECIFICATION \| EVIDENCE`, `target: ModelReference`, `source: Source`, `origin: Origin` |
| Traceability | `role: SPECIFICATION \| EVIDENCE`, `target: ModelReference`, `source: Source`, `origin: Origin` |
| Grounding | `target: SemanticTarget`, `boundary: LOCAL \| EXTERNAL`, `relation: Presence[RelationId]`, `profile: Presence[ProfileId]`, `context: Presence[text]`, `preferredLabel: Presence[LocalizedLabel]`, `source: Source`, `origin: Origin` |
| ComponentUsageCapabilityMapping | `target: VersionedComponentCapabilityReference`, `boundary: LOCAL \| EXTERNAL`, `source: Source`, `origin: Origin` |
| ComponentUsageCapabilityDependency | `target: VersionedComponentUsageCapabilityReference`, `boundary: LOCAL \| EXTERNAL`, `source: Source`, `origin: Origin` |

Demand ModelLinks permit only GOAL, USE_CASE, SCENARIO and USE_CASE_SLICE.
Discovery permits all seven ModelLink roles. Traceability has only its two
distinct roles. Type and role syntax checks MUST NOT certify target-kind
availability, infer inverse edges or combine unrelated lists.

Decode ComponentRef through the existing `ComponentNamespace.parse`,
`ComponentLocalId.parse` and `ComponentId.of`. Never issue another Component
identity domain. The Component reference is independent of the Capability's
qualified model identity. Reference kind, direction, target kind, contract and
target ABI values are all explicit. Repeated realization keys are retained
for the later semantic validator; this layer neither invokes nor adapts targets.

### Lexical and direct source ownership

Every qualified identity component and opaque contract/revision/ABI/rule/name/
description/detail/context/label/language value MUST be nonnull, nonempty,
free of ISO controls, and have no leading or trailing Unicode whitespace.
Whitespace checks use `Character.isWhitespace || Character.isSpaceChar` on
code points. Preserve case, Unicode composition and delimiters; never trim or
repair decoded text. Paths reject absolute, drive/URI-prefix, backslash,
control, empty-segment, dot-segment and dot-dot-segment forms without
normalization. Source.line MUST be explicitly null or a positive whole
Int-valued number; fraction, zero, negative, overflow, boolean and string fail.

Every definition, declaration and relationship/compatibility edge MUST carry
its own explicit Source. Direct source authority and path MUST equal its
SourceInput scope. A definition's identity.modelId MUST equal that authority.
Definition and edge revisions can differ independently in one document.
Derived inputs MUST be nonempty, ordered and independently valid; foreign
authority/path/revision evidence is allowed and retained. No primary source
replacement or revision equality requirement applies to Derived inputs.
Headings, model names, URI, source bytes, legacy sha256, packages and CLI defaults
MUST NOT supply missing identity/version/revision/ABI/source data. External
boundaries remain External even if another input contains the target.

### Authored executable normalization cases

The two real CML parser inputs are
[primary](../../src/test/resources/modeler/capability-model.cml) and
[secondary](../../src/test/resources/modeler/capability-model-secondary.cml).
They are authored inputs, not generated projections or producer/consumer proof.
[CmlCapabilityCmlSpec](../../src/test/scala/cozy/modeler/CmlCapabilityCmlSpec.scala)
authors C64P01–23 and explicit-owner C64P26;
[CmlCapabilityModelSpec](../../src/test/scala/cozy/modeler/CmlCapabilityModelSpec.scala)
authors C64P24–25 and immutable-carrier behavior. All cases use semantic
should/which/in grouping and actual Given/When/Then boundaries. Active
ScalaCheck requires at least 100 successful samples with zero discards.
CAP-64-02 focused validation and independent acceptance are recorded in
[Phase 64](../phase/phase-64.md#cap-64-02-acceptance). This syntax acceptance
does not certify semantic validation, generation or producer/consumer behavior.

| Cases | Observable normalization obligation |
| --- | --- |
| C64P01 | Actual load and complete independent typed R3 equality. |
| C64P02–03 | Independent Usage emptiness and four explicit many-to-many demand edges. |
| C64P04–05 | Canonical providers/directions and three complete realization kinds. |
| C64P06–08 | Explicit boundaries, all Presence branches and ordered independent Derived evidence. |
| C64P09–12 | Two real sources, deterministic order, identity independence, literal Foundation vocabulary/labels and optional actual diagnostic lines. |
| C64P13 | Successful legacy absence versus explicit empty new root. |
| C64P14–15 | Role-aware containers, arbitrary entry labels and strict physical-field/JSON failures. |
| C64P16–18 | Invalid lexical/source/Component domains and wrong closed reference/role/Presence/Origin branches. |
| C64P19–20 | Direct source ownership, foreign derivation evidence, parser failures, malformed scopes and duplicate paths without partial salvage. |
| C64P21–23 | Parsed unresolved graph facts, independent Usage edges and per-document directed compatibility. |
| C64P24–25 | Active literal-ID/revision/order determinism and invalid source/identity/path with explicit Presence/Origin variation properties. |

## Private finite semantic validation

The separate `private[cozy]` entry is:

```scala
CmlCapabilityValidation.validate(
  documents: Vector[CmlCapabilitySource.CapabilityDocument],
  input: CmlCapabilityValidationScope.ScopeInput
): Either[Vector[CmlCapabilityValidationScope.Diagnostic],
          CmlCapabilityValidation.Validated]
```

Documents MUST be the original ordered envelopes from successful normalization.
Callers MUST NOT feed a failed normalization into validation, clear parser errors,
merge away source scopes or reinterpret SyntaxIssue as a semantic diagnostic.
The entry has no parser, loader, acquisition default, runtime activation or IO.
It validates supplied Cozy semantic facts; it is not CNCF admission, generated
output, target execution or runtime usability evidence.

`Validated` has a private constructor and no `apply` or `copy` bypass. Only the
successful entry constructs it. It retains exact `documents`, exact `scope` and
ordered `bindings: Vector[(RealizationEdge, TargetContract)]`. Every failure
returns Left with no partial Validated or successful-sibling salvage.

### Explicit scope and admitted fact carriers

`CmlCapabilityValidationScope` is private to Cozy. Its new internal fields are
flatcase; the accepted R3 authored carriers retain their existing field names.

| Carrier | Complete fields or alternatives |
| --- | --- |
| ScopeInput | `Ready(scope: Scope)` or `Rejected(diagnostics: nonempty Vector[Diagnostic])` |
| Scope | `scopeid: String`, `source: CapabilitySource`, `modelfacts: Vector[ModelFact]`, `termfacts: Vector[TermFact]`, `relationfacts: Vector[RelationFact]`, `profilefacts: Vector[ProfileFact]`, `usagecontracts: Vector[ComponentUsageContract]`, `componentcontracts: Vector[ComponentContract]`, `targetcontracts: Vector[TargetContract]` |
| ModelFact | `identity: Foundation.ModelElementId`, `kind: ModelKind`, `source: CapabilitySource` |
| TermFact | `identity: Foundation.TermId`, `source: CapabilitySource` |
| RelationFact | `identity: Foundation.RelationId`, `source: CapabilitySource` |
| ProfileFact | `identity: Foundation.ProfileId`, `source: CapabilitySource` |
| ComponentUsageContract | `identity: Foundation.ModelElementId`, `contractversion: String`, `source: CapabilitySource` |
| ComponentContract | `identity: Foundation.ModelElementId`, `contractversion: String`, `source: CapabilitySource` |
| TargetContract | `kind: CmlCapabilityModel.TargetKind`, `identity: Foundation.ModelElementId`, `component: existing Java ComponentId`, `contractversion: String`, `generatedabiversion: String`, `source: CapabilitySource` |

ModelKind is closed, with these exact tags and Scala alternatives:

| Tag | ModelKind alternative |
| --- | --- |
| DOMAIN_CONCEPT | DomainConcept |
| GOAL | Goal |
| USE_CASE | UseCase |
| SCENARIO | Scenario |
| USE_CASE_SLICE | UseCaseSlice |
| COMPONENT | Component |
| COMPONENT_CAPABILITY | ComponentCapability |
| COMPONENT_USAGE_CAPABILITY | ComponentUsageCapability |
| SPECIFICATION | Specification |
| EVIDENCE | Evidence |
| OPERATION | Operation |
| WORKFLOW | Workflow |
| STATE_MACHINE | StateMachine |

Scope acquisition and actual Foundation/BoK/catalog adapters, target-contract
extraction, reflection and source-currentness proof belong to later owners.
Callers MUST supply actual Foundation IDs and separately explicit revision-bearing
sources; legacy sha256 MUST NOT supply any revision. An explicit Ready empty scope
means successful finite acquisition selected by the caller. Rejected input MUST
return its original nonempty diagnostic vector unchanged, including its original
kind, detail and source. It MUST NOT be replaced with Ready emptiness or NoProvider.

### Source and candidate rejection contract

Scope facts and typed R3 values MUST satisfy the accepted literal validity and
canonical path rules. Mandatory identity components, opaque versions, revisions,
ABI, names, Presence details, labels/languages and derivation rules MUST reject
null, empty, padded or ISO-control-bearing values. Missing source carriers,
invalid lines, empty Derived inputs and missing closed typed values fail.
Container objects, vectors and their members MUST be nonnull; null container
graphs are outside the entry contract. No fallback, repair, hashing or preprocessing
is permitted. Present-empty remains present, and each Absent reason/detail remains
unchanged. Direct record/edge authority and path MUST equal the original document
scope; definition/fact source authority MUST equal its qualified identity authority.
Derived input sources remain independently valid, nonempty, ordered and may be
foreign. Revisions and optional source lines remain independent of each other.
Repeated original document paths MUST fail.

The combined documents form one current declaring snapshot per model authority.
Every repeated qualified Capability identity MUST fail as DuplicateDefinition,
including equal records, differing versions/labels, cross-file repeats and reuse
across Usage/Component families. The identity has one fixed family.
Historical catalog contracts MAY retain distinct versions of one fixed family.
Exact repeated catalog identity/version candidates, or a current definition plus
the same exact catalog identity/version, are Ambiguous. Opposite catalog/current
families are WrongKind. Equal Model/Term/Relation/Profile facts and full target
tuples are ambiguous scope candidates, not deduplication opportunities.

Model candidate sets contain explicit ModelFacts plus one family candidate per
distinct Capability identity from definitions/catalogs. Historical versions alone
MUST NOT multiply that model identity. A separately supplied ModelFact at the same
Capability identity is an additional candidate and makes Local resolution ambiguous.
No first/last, provider, label, URI, synonym or descriptive-kind winner is selected.

| Authored boundary | Required finite resolution / failure |
| --- | --- |
| Local demand/discovery | One exact qualified model candidate with the full role kind; no candidate is DanglingReference, multiple candidates Ambiguous, another kind WrongKind. Demand remains restricted to its four roles. |
| Local Specification/Evidence | Resolve each independent list to its exact role; no executed-proof inference. |
| Local Model/Term grounding | Resolve exactly one candidate in its own domain. Model grounding retains the explicitly admitted model kind without ontology inference. |
| Local present Relation/Profile | Resolve uniquely in the corresponding exact vocabulary domain. |
| External model/grounding/traceability | Preserve External without Local resolution or target-availability certification, even with matching or conflicting local facts. |
| Local Component mapping / Usage dependency | Resolve one exact family/identity/version candidate. Missing identity is DanglingReference, opposite family WrongKind, absent exact version Incompatible and multiple exact candidates Ambiguous. |
| External mapping/dependency | Retain explicit unresolved boundary; no catalog or global cycle certificate. |
| Component declaration | Require one exact Component Capability identity/version in current definitions or the supplied historical catalog. Compatibility cannot replace that candidate. |
| Declaration uniqueness | Reject repeated full `(ComponentId, direction, qualified Capability identity, contractVersion)` as DuplicateDeclaration; retain distinct providers, directions and empty realization lists. |
| Repeated realization key | Every Component, direction, qualified identity component and version MUST equal the enclosing complete declaration key; mismatch is Malformed at the realization key. |
| Realization target | Require exact target-owning ComponentId plus qualified model identity, then kind, target contract version and generated ABI. Missing identity/Component is DanglingReference; kind mismatch WrongKind; version/ABI mismatch Incompatible at their distinct fields; multiple exact contracts Ambiguous. External also requires this evidence. |

Target ABI MUST match the supplied executable target contract. It is not a guessed
global constant or CNCF projection ABI input. Procedure, order, retry, compensation,
authorization and runtime state are rejected by the existing closed CML syntax and
cannot inhabit the closed IR. No target invocation, adapter, classloading or runtime
policy is part of validation.

### Dependency graph and directed compatibility

Only supplied Local ComponentUsageCapabilityDependency edges enter cycle checks. A self-loop
or directed cycle across original files MUST issue CyclicDependency for every
participating authored edge, preserving edge order and source. External edges
remain excluded even when their target is current. Missing or version-invalid
Local dependencies retain their independent resolution errors; failed resolution
does not certify an empty graph. Demand/discovery, grounding, traceability,
Component mappings and realizations are not dependency edges. Success certifies
only this explicitly selected finite Local graph.

Every supplied CompatibilityEdge identity and its required/provided versions MUST
resolve exact Component contract candidates. Direct edges retain independent
source, origin and order. The result exposes:

```scala
validated.isCompatible(
  required: VersionedComponentCapabilityReference,
  provided: VersionedComponentCapabilityReference
): Boolean
```

Both references MUST have unique admitted exact candidates and the same identity.
The predicate is true only for equal versions or a supplied direct same-identity
required-to-provided edge. It MUST NOT infer reverse, transitive, SemVer, same-name,
cross-identity or provider-order compatibility. It selects no provider and waives
no realization contract/ABI checks.

### Structured source-correlated diagnostics

Diagnostic has complete fields `kind`, `fieldpath`, `scope: Option[SourceScope]`,
`source: Option[CapabilitySource]`, `relatedsources: Vector[CapabilitySource]`,
and `detail`. DiagnosticKind is closed: Malformed, Unsupported, Unavailable,
Unauthorized, Stale, DuplicateDefinition, DuplicateDeclaration, DanglingReference,
Ambiguous, WrongKind, Incompatible and CyclicDependency.

Document diagnostics MUST carry the exact original scope and offending authored
record/edge source, including its own revision and optional line. Scope issues
carry their fact source and no document scope; absent source remains None.
Duplicate issues retain the previous source as related evidence. Acquisition
diagnostics are preserved exactly. Deterministic scope checks precede document
checks; snapshot/record/reference checks precede graph checks. A malformed carrier
gate stops before those carriers can be consumed. Structurally valid duplicate
candidates remain available for contextual ambiguity diagnostics, with no winner
selection. Issue generation follows supplied document, record, field and edge order
within each gate. Every nonempty issue vector is an all-or-nothing rejection.

### Authored executable semantic validation cases

[CmlCapabilityValidationCmlSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationCmlSpec.scala)
authors C64V01–05 through real Kaleidox parsing and successful normalization.
[CmlCapabilityValidationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationSpec.scala)
authors C64V06–24 over explicit source-bearing semantic facts.
[CmlCapabilityValidationGraphSpec](../../src/test/scala/cozy/modeler/CmlCapabilityValidationGraphSpec.scala)
authors C64V25–32 and active ScalaCheck properties requiring at least 100 successes
and zero discards. Every behavior has actual Given/When/Then boundaries.
These supplied facts prove the validator contract when executed; they do not prove
actual target extraction or producer/admission/CBD interoperability. Validation
and independent protected CAP-64-03 Step acceptance are recorded in
[Phase 64](../phase/phase-64.md#cap-64-03-acceptance).

| Case | Observable authored behavior |
| --- | --- |
| C64V01 | Both unchanged real resources normalize and validate; original documents/scope/order, revisions and three realization pairs survive. |
| C64V02 | Four Local Use Case demand links, Scenario discovery, standalone Usage, equal labels and three provider/direction keys stay independent. |
| C64V03 | Real CML gaps, Local/External Domain and Term grounding, Relation/Profile/labels and ordered foreign Derived evidence remain exact. |
| C64V04 | Each forbidden procedure/order/retry/compensation/authorization/runtimeState field yields contextual SyntaxIssue before semantic validation. |
| C64V05 | Original parser errors and unavailable/unauthorized/stale/unsupported Rejected scope cannot become successful empty input. |
| C64V06 | Equal, version-changing, cross-file and cross-family current IDs are duplicate definitions. |
| C64V07 | Three distinct provider/direction keys succeed; a complete key repeat fails with previous source evidence. |
| C64V08 | All four demand and seven discovery roles require exact Local kind, with missing/wrong-kind/ambiguous edge diagnostics. |
| C64V09 | Definition/declaration Specification and Evidence resolve independently; missing/wrong/duplicate facts fail. |
| C64V10 | Local Model and BoK Term grounding use distinct domains, rejecting missing and duplicate candidates. |
| C64V11 | Local present Relation/Profile use their own domains; unknown External grounding remains External. |
| C64V12 | Local mapping/dependency require exact family and version; External needs no invented catalog availability. |
| C64V13 | Exact historical Component declaration candidate resolves; compatibility cannot repair missing family/version/identity. |
| C64V14 | Each repeated realization key field must agree with its enclosing complete tuple. |
| C64V15 | Three target kinds bind exactly under either boundary; missing identity or owning Component fails. |
| C64V16 | Target kind, contract, ABI and exact-candidate multiplicity have distinct typed/field-specific rejection. |
| C64V17 | Every absence reason, present-empty relationship, optional language and ordered foreign evidence survives equality. |
| C64V18 | Invalid mandatory literals, source paths/lines and empty derivation evidence fail without defaults. |
| C64V19 | Direct sources match scope/identity authority, while foreign derivation and independent revisions are retained. |
| C64V20 | Malformed facts/contracts and equal duplicates in every scope domain fail before successful use. |
| C64V21 | Historical distinct versions succeed; exact catalog/current collisions and family reuse fail. |
| C64V22 | Historical versions give one family model candidate; independent same-ID ModelFact makes Local grounding ambiguous. |
| C64V23 | Duplicate and edge sources/lines/revisions, previous evidence and repeated diagnostic order remain exact without salvage. |
| C64V24 | Parsed explicit empty root plus explicit Ready empty scope succeeds; invalid/rejected/repeated paths fail. |
| C64V25 | Self/two-node/long cross-file cycles diagnose every edge; a finite acyclic chain succeeds. |
| C64V26 | External dependency and cyclic ordinary grounding/independent model links do not form Local dependency cycles. |
| C64V27 | Missing/family/version/ambiguity dependency failure stays typed, never an admitted empty graph. |
| C64V28 | Equality/direct edges require exact admitted references and cannot waive realization ABI failure. |
| C64V29 | No inverse/transitive/SemVer/name/cross-identity/provider-order compatibility is inferred. |
| C64V30 | Failed compatibility identity/family/required/provided version/candidate evidence retains its own source. |
| C64V31 | Active real-parser literal-ID/property samples retain case, Unicode, delimiters, quote/backslash, independent revisions/versions, Presence/origin/order and deterministic failures. |
| C64V32 | Active graph/version properties preserve Local cycle policy and exact directed nontransitive compatibility without discarded samples. |

Equivalent valid JSON Unicode escapes retain decoded quote/backslash literals
through the actual parser. This does not promise all escaped-quote spellings or
repair upstream LogicalLines. That separate parser work remains tracked by
the coordinating Phase 11 as DEV-P11-COZY64-CAP02-001.

## Private deterministic projection generation

The `private[cozy]` production entry MUST compose the unchanged normalization
and finite validation entries:

```scala
CmlCapabilityGeneration.generate(inputs: Vector[SourceInput],
  scopeInput: ScopeInput, contexts: Vector[GenerationContext]):
  Either[Failure, Generated]
CmlCapabilityGeneration.project(validated: CmlCapabilityValidation.Validated,
  contexts: Vector[GenerationContext]): Either[Vector[Issue], Generated]
```

`project` MUST accept only the original successful `Validated`; there is no raw
IR overload or new validation certificate. The sole successful `Generated`
constructor retains that original result, its Usage records, scope and
ordered bindings, plus immutable Component projections. `SyntaxFailure`,
`SemanticFailure` and `GenerationFailure` MUST preserve their original ordered
syntax, semantic/acquisition and generation vectors. Failure supplies no partial
Generated, admitted-empty relation or NoProvider evidence. No target executes.

### Explicit independent contexts and snapshot coverage

`Producer(id, version)`, `ComponentCoordinate(component: Java ComponentId,
version)` and `PackageCoordinate(coordinate, version)` MUST be explicit.
Semantic generation MUST be 1, schema version MUST be 1 and projection ABI MUST
be exactly `cncf.component-capability.v1`, independently of target ABIs.
Producer identity MUST be `cozy` and its supplied version MUST equal actual
`org.simplemodeling.cozy.BuildInfo.version`; disagreement MUST fail rather than
be rewritten. Component release admission MUST use the existing canonical Java
identity and Cozy release-coordinate codec. Package coordinate/version and source
revision retain valid nonempty exact literal text and MUST NOT alias those domains.

`SnapshotKey(SourceScope(modelAuthority, path), ComponentId)` MUST select exactly
one original document. Header source MUST have that exact scope, an explicit valid
revision, and None or a positive line. Duplicate keys MUST fail even with different
releases/revisions, retaining related sources. Every authored Component declaration
requires its exact key; every document with Component definitions or declarations
requires at least one context. Missing, unknown and app-only selections MUST fail.
Definition-only documents require an explicit selected Component. App-only or
empty documents require no external context and remain in the original Validated;
an empty external projection vector is a successful private retention result.

Contexts MUST be checked completely before projection, with issues in supplied
context/field order followed by document coverage issues, then compatibility issues
in authored document and edge order. Each selected Component document MUST reject
every second or later occurrence of the exact compatibility tuple `(identity,
requiredVersion, providedVersion)` before any Generated is returned. Each rejection
MUST retain the repeated edge's source and the first edge's related source, even
when source or origin differs. A document selected by several contexts MUST be
checked only once; absent compatibility supplies no edges. The original Validated
MUST remain unchanged, with no deduplication or winner. Each successful snapshot
contains its selected document's Component definitions, declarations for its exact
Component and compatibility Presence. Several Components selected for a document
each receive its definitions without asserting that they provide all of them.
Catalog definitions, synthetic edges and Usage data MUST NOT enter the wire.
Snapshots MUST sort by exact path, authority, Component namespace and local ID.
Authored definitions, declarations, edges, derivation inputs and compatibility
vectors MUST retain their original order, sources and Presence; document states
MUST NOT be merged across paths or resolved by labels.

### Exact accepted CNCF R11 serialization

The accepted CNCF `ComponentCapabilityProjectionJsonEncodingPart` owns the closed
record keys and order. The Play JSON encoder MUST emit root keys in this order:
`schemaVersion`, `generatedAbiVersion`, `producer`, `component`, `package`,
`source`, `capabilities`, `declarations`, `compatibility`. Producer has `id,version`;
Component coordinate has `namespace,id,version`; package has `coordinate,version`.
Component definitions have `kind,identity,contractVersion,name,summary,description,
source,discoveryReferences,grounding,traceability`, with fixed kind COMPONENT.
The previously specified Source, Presence, Origin, model/vocabulary identity,
reference, label, discovery, grounding, trace, declaration, repeated realization
key, realization and compatibility records MUST use the same complete accepted
R11 keys and order. Null line/language, all absence reasons/detail, declared and
ordered derived inputs, MODEL/TERM targets and separate vocabulary domains MUST
remain exact. No alias, inferred relationship, omitted key or Usage branch
is allowed. `Projection` exposes its typed context, original selected records,
`JsObject` and compact `Json.stringify` representation without reparsing CML.

### Explicit pure Scala carriers and bounded publication

`ScalaOutput(key, path, packagename: Vector[String], objectname)` MUST select each
generated snapshot exactly once. Missing, unknown or repeated keys and duplicate
paths or qualified symbols MUST fail before a publishable batch exists. Paths MUST
be canonical project-relative POSIX paths beneath `target/` ending `.scala`.
Package segments and object names MUST match `[A-Za-z][A-Za-z0-9_]*` and exclude
Scala 2/3 keywords: abstract, case, catch, class, def, do, else, extends, false,
final, finally, for, forSome, if, implicit, import, lazy, macro, match, new, null, object,
override, package, private, protected, return, sealed, super, this, throw, trait,
true, try, type, val, var, while, with, yield, enum, export, given, then, using,
end, extension, inline, opaque, open, transparent and derives. No name/path is
inferred from labels or chosen as a canonical CAR resource name.

`CmlCapabilityGenerationScala.render` MUST produce a Scala 2.12/Scala 3 common
object with pure `def projectionJson: String`, immutable private `_segments:
Vector[String]` and `_segments.mkString`. Ordinary escaped string literals MUST
preserve JSON exactly, without interpolation or triple-quote exposure. Segments
MUST contain at most 2000 UTF-16 units and MUST NOT split a supplementary pair;
the whole value MUST NOT become one oversized JVM string constant. Production
code imports no compiler/reflection or executable model/runtime dependency.
The sole successful `RenderedBatch` constructor retains Generated, explicit
selections and artifacts sorted by path.

`write(outputRoot: Path, batch: RenderedBatch)` MUST preflight the root, actual
ancestors and all selected artifacts before writing. Symlinks, nonregular existing
outputs and traversal MUST fail. After complete path and ownership admission,
write every selected generated artifact directly with the current rendered bytes,
including existing regular artifacts on repeat generation. Preserve authored
inputs, unselected files and destinations outside the selected operation. IO
failure MUST report source-associated structured issues and the exact completed
prefix of written paths. No byte-equality precondition, per-artifact repeated
preflight, race protection, temporary-file or atomic-replacement protocol,
rollback or multiwriter guarantee is supplied; recovery is regeneration.

### Existing provenance and outstanding consumer proof

Actual generated regular `.scala` files participate in the current canonical
V1/V2 metadata, deterministic path union, legacy rebind and package validation.
`SourceInput(path,identity)` selects the original source. V1 retains the exact
CNCF target, Cozy generator, SimpleModeler backend, SimpleModeling model and
source identity inputs; V2 retains their exact shared version tuple, sorted
literal source identities and the complete `Artifact(path)` union. Readers
validate schemas, known inputs and readable selected paths. Different readable
contents at a shared path remain admissible; missing selected source, metadata
or output paths fail. Header/source revisions remain independent supplied
semantic facts. Complete authoring selection, path and qualified-symbol collision
checks remain required. Repeat generation writes current rendered contents to
selected regular outputs; existing byte equality is not an admission condition.
No transit checksum, byte protection or alternate source copy is introduced.
Ordinary CLI/bridge descriptor forwarding carries the real
path, target and source identity without digest flags. Directory/CAR metadata
and required generated-release provenance use these same current contracts.

CAP-64-05 retains normal producer composition, actual source/target acquisition,
Scala 3 CNCF decoder/admission/binding/package/discovery and CBD no-reparse proof,
with actual source/Cozy/ABI/CNCF/CBD revisions. This callable core and its test-only
pure carrier loader establish no current CLI hookup, runtime target activation,
canonical resource placement, CNCF/CBD acceptance or Phase closure. Separate
Cozy Step and Phase validation/review/local release precede main integration.

### Authored executable generation cases

[CmlCapabilityGenerationSpec](../../src/test/scala/cozy/modeler/CmlCapabilityGenerationSpec.scala)
authors C64G01–20 and the C64G33 compatibility-tuple regression, including actual
validation/project JSON round trips and two ScalaCheck properties requiring at
least 100 successes and zero discards.
[CmlCapabilityGenerationProvenanceSpec](../../src/test/scala/cozy/modeler/CmlCapabilityGenerationProvenanceSpec.scala)
authors C64G21–32, using the existing test compiler with an explicit Scala-library
classpath, closed test-only child classloader and actual generated outputs in
the accepted provenance APIs. Every behavior has adjacent Given/When/Then clauses.
The C64G01–33 Step obligations have accepted focused validation, independent
protected Step review and independent focused closure, recorded in
[Phase 64](../phase/phase-64.md#cap-64-04-acceptance).

| Case | Observable authored behavior |
| --- | --- |
| C64G01 | Real two-CML composition, all explicit snapshots and original Validated retention. |
| C64G02 | Provided/required declarations, exact Component/versioned reference and repeated realization key. |
| C64G03 | Three realization target families, boundaries, independent contracts/ABIs and edge sources/origins. |
| C64G04 | Complete closed definition/discovery/grounding/trace records and authored values. |
| C64G05 | Independent Specification/Evidence roles, revisions and explicit Local/External traces. |
| C64G06 | Literal qualified MODEL/TERM, Relation and Profile domains. |
| C64G07 | Present-empty, four absent reasons/detail and null or supplied line/language. |
| C64G08 | Independent header/definition/edge revisions and ordered foreign Derived inputs. |
| C64G09 | Actual Cozy version and independent Component/package/contract/ABI domains. |
| C64G10 | Unsupported semantic generation/schema/projection ABI without conversion. |
| C64G11 | Producer identity/version disagreement without rewriting. |
| C64G12 | Malformed context text/source/line/canonical Component/release and package fields. |
| C64G13 | Unknown scope/path and duplicate snapshot keys with changed revisions/releases. |
| C64G14 | Complete declaration/document coverage, explicit definitions-only selection and app-only rejection. |
| C64G15 | Original Usage records retained upstream; valid zero external projections. |
| C64G16 | Original parser, target validation and acquisition vectors retained by failure wrappers. |
| C64G17 | Authored inner definition/trace/Derived/compatibility order under canonical outer order. |
| C64G18 | Same authority with separate paths and separate compatibility states. |
| C64G19 | At least 100 active literal validation/project/JSON round trips, zero discards. |
| C64G20 | At least 100 independent source/context permutations with exact JSON/rendered artifacts, zero discards. |
| C64G21 | Real files compiled and pure accessor returns exact generated JSON. |
| C64G22 | Compiled hostile and larger-than-64k literal with a supplementary pair at the segment boundary. |
| C64G23 | Complete explicit output selection and lexical/path/keyword rejection. |
| C64G24 | Path and symbol conflicts with both explicit snapshot sources. |
| C64G25 | Clean repeat publication and selected regular-output regeneration retain exact contents and selected paths; nonregular/symlink paths fail before sibling writes while unselected/outside sentinels remain unchanged. |
| C64G26 | Actual two-source V1 metadata to package-valid V2 sorted literal identities and complete Artifact(path) union. |
| C64G27 | Complete Manifest and serialized V2 JSON are identical in both delegated orders. |
| C64G28 | Direct V1 admission and accepted legacy one-source V2 rebind. |
| C64G29 | Actual duplicate source produces SourceAmbiguous without a winner. |
| C64G30 | Actual generator/target/backend input disagreement produces InputMismatch. |
| C64G31 | Shared path claims with different readable contents produce one deterministic path union in both orders. |
| C64G32 | Missing delegated manifest, delegated output or installed selected artifact fails; changed readable installed contents at the same path remain package-admissible. |
| C64G33 | Repeated compatibility tuple rejection before any snapshot, ordered repeated/first source attribution for exact and distinct-origin repetitions, and original Validated retention. |

## Semantic acceptance matrix

These examples specify expected outcomes only. The executable case maps above
and separate acceptance records establish behavior under the R12 owners.

| ID | Case | Expected semantic outcome | Rules |
| --- | --- | --- | --- |
| E1 | Standalone Component Usage Capability has no Use Case links. | Retain the stable Usage record and exact present-empty or explicit absence. | R1, R2, R4, R9 |
| E2 | Two Use Cases require two Capabilities with shared endpoints. | Preserve all four explicit many-to-many edges and their sources; infer no grouping or inverse edges. | R4, R7 |
| E3 | Component Capability is discovered from a Scenario, and a Component Usage Capability maps to it. | Keep discovery, Usage mapping, and Component provided/required declaration separate. | R4, R5, R6 |
| E4 | Two Components provide one reference; one also requires it. | Retain three distinct full declaration keys; reject a repeated complete key. | R2, R5, R10 |
| E5 | Equal labels occur on exact IDs, or contract versions/source revisions change. | Do not merge or issue IDs; preserve distinct contract-version, source-revision, ABI, and package domains. | R2, R7, R11 |
| E6 | Goal narrative or local declaredId has no qualified model target. | Retain an explicit gap or reject a malformed declared reference; derive no identity from text. | R4, R9, R10 |
| E7 | Local/External Term and Domain grounding include declared/derived edges. | Preserve exact target, relation, profile, source, and evidence; infer no ontology or synonyms. | R7, R8, R9 |
| E8 | Realization is explicitly empty, or a declared target is unknown, wrong-kind, or incompatible. | Retain the empty architectural fact; reject failed declared binding with source-correlated diagnostics. | R6, R9, R10 |
| E9 | Capability source revision is missing while a legacy Foundation sha256 exists. | Do not use the digest as revision or admission authority; retain an explicit missing-source diagnostic. | R7, R12 |
| E10 | Usage dependencies form a cycle, while grounding or ordinary many-to-many links also cycle. | Reject the Local Capability dependency cycle only; preserve unrelated supplied semantic relations. | R4, R8, R10 |
| E11 | Component versions differ and a directed compatibility edge is supplied. | Match only the same qualified identity in the supplied required-to-provided direction; infer no transitivity. | R5, R10 |
| E12 | Usage demand and Specification/Evidence references exist, but producer input is unavailable. | Keep upstream management and independent traceability facts; claim neither executed proof nor admitted-empty/NoProvider. | R9, R11, R12 |
