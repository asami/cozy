# CML Capability Model Specification

Status: normative semantic IR, private CML normalization and finite semantic validation contract

## Authority and scope

This specification defines the conceptual Application Capability and Component
Capability IR, its qualified identities, declarations, relationships, versions,
provenance, grounding, and validation boundaries. R1 through R12 are normative.
The R3 tables describe semantic data. The private Scala IR and initial authored
CML syntax are specified below for CAP-64-02. Parsed documents are not admitted
Graphs or CNCF projections. E1 through E12 remain expected semantic examples;
the C64P executable cases below passed focused validation and independent
CAP-64-02 acceptance on 2026-10-05. C64V01–32 are authored semantic validation
specifications; their focused validation and independent CAP-64-03 acceptance are
recorded in [Phase 64](../phase/phase-64.md#cap-64-03-acceptance).

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
authorization, Availability, Permission, Guard, or runtime state. The original
semantic Step selects no public Scala or wire API. CAP-64-02's private source
constructs are specified below.

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
resource filename or external JSON shape. The private CML field grammar below
is separate from the generated CNCF projection.

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

## Private Scala IR and per-document input

All carriers are immutable Scala 2.12 implementation types under
`private[cozy]` objects in `cozy.modeler`. They add no public facade or dependency.
`CmlCapabilityModel` supplies the exact R3 definition, reference, declaration,
mapping, dependency, grounding, realization, compatibility and traceability
records. `ApplicationCapability.kind` is `ApplicationKind` and
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
| CapabilityKind | `ApplicationKind` / APPLICATION; `ComponentKind` / COMPONENT |
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

## APPLICATION-CAPABILITY
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
| Application entry | `identity`, `contractVersion`, `name`, `summary`, `description`, `source`, `demandReferences`, `discoveryReferences`, `componentMappings`, `dependencies`, `grounding`, `traceability` |
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
| VersionedApplicationCapabilityReference | `kind: APPLICATION`, `identity: ModelElementId`, `contractVersion: opaque text` |
| VersionedComponentCapabilityReference / CapabilityReference | `kind: COMPONENT`, `identity: ModelElementId`, `contractVersion: opaque text` |
| DeclarationKey | `component: ComponentRef`, `direction: PROVIDED \| REQUIRED`, `capability: VersionedComponentCapabilityReference` |
| Declaration | `component: ComponentRef`, `direction: PROVIDED \| REQUIRED`, `capability: VersionedComponentCapabilityReference`, `source: Source`, `realizations: Presence[array[Realization]]`, `traceability: Presence[array[Traceability]]` |
| Realization | `declarationKey: DeclarationKey`, `targetKind: OPERATION \| WORKFLOW \| STATE_MACHINE`, `target: ModelReference`, `targetComponent: ComponentRef`, `targetContractVersion: opaque text`, `generatedAbiVersion: independently supplied opaque target ABI text`, `source: Source`, `origin: Origin` |
| Compatibility | `identity: ModelElementId`, `requiredVersion: opaque text`, `providedVersion: opaque text`, `source: Source`, `origin: Origin` |
| ModelLink | `role: GOAL \| USE_CASE \| SCENARIO \| USE_CASE_SLICE \| COMPONENT \| SPECIFICATION \| EVIDENCE`, `target: ModelReference`, `source: Source`, `origin: Origin` |
| Traceability | `role: SPECIFICATION \| EVIDENCE`, `target: ModelReference`, `source: Source`, `origin: Origin` |
| Grounding | `target: SemanticTarget`, `boundary: LOCAL \| EXTERNAL`, `relation: Presence[RelationId]`, `profile: Presence[ProfileId]`, `context: Presence[text]`, `preferredLabel: Presence[LocalizedLabel]`, `source: Source`, `origin: Origin` |
| ApplicationComponentMapping | `target: VersionedComponentCapabilityReference`, `boundary: LOCAL \| EXTERNAL`, `source: Source`, `origin: Origin` |
| ApplicationDependency | `target: VersionedApplicationCapabilityReference`, `boundary: LOCAL \| EXTERNAL`, `source: Source`, `origin: Origin` |

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
authors C64P01–23;
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
| C64P02–03 | Independent Application emptiness and four explicit many-to-many demand edges. |
| C64P04–05 | Canonical providers/directions and three complete realization kinds. |
| C64P06–08 | Explicit boundaries, all Presence branches and ordered independent Derived evidence. |
| C64P09–12 | Two real sources, deterministic order, identity independence, literal Foundation vocabulary/labels and optional actual diagnostic lines. |
| C64P13 | Successful legacy absence versus explicit empty new root. |
| C64P14–15 | Role-aware containers, arbitrary entry labels and strict physical-field/JSON failures. |
| C64P16–18 | Invalid lexical/source/Component domains and wrong closed reference/role/Presence/Origin branches. |
| C64P19–20 | Direct source ownership, foreign derivation evidence, parser failures, malformed scopes and duplicate paths without partial salvage. |
| C64P21–23 | Parsed unresolved graph facts, independent Application edges and per-document directed compatibility. |
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
| Scope | `scopeid: String`, `source: CapabilitySource`, `modelfacts: Vector[ModelFact]`, `termfacts: Vector[TermFact]`, `relationfacts: Vector[RelationFact]`, `profilefacts: Vector[ProfileFact]`, `applicationcontracts: Vector[ApplicationContract]`, `componentcontracts: Vector[ComponentContract]`, `targetcontracts: Vector[TargetContract]` |
| ModelFact | `identity: Foundation.ModelElementId`, `kind: ModelKind`, `source: CapabilitySource` |
| TermFact | `identity: Foundation.TermId`, `source: CapabilitySource` |
| RelationFact | `identity: Foundation.RelationId`, `source: CapabilitySource` |
| ProfileFact | `identity: Foundation.ProfileId`, `source: CapabilitySource` |
| ApplicationContract | `identity: Foundation.ModelElementId`, `contractversion: String`, `source: CapabilitySource` |
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
| APPLICATION_CAPABILITY | ApplicationCapability |
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
across Application/Component families. The identity has one fixed family.
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
| Local Component mapping / Application dependency | Resolve one exact family/identity/version candidate. Missing identity is DanglingReference, opposite family WrongKind, absent exact version Incompatible and multiple exact candidates Ambiguous. |
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

Only supplied Local ApplicationDependency edges enter cycle checks. A self-loop
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
| C64V02 | Four Local Use Case demand links, Scenario discovery, standalone Application, equal labels and three provider/direction keys stay independent. |
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
