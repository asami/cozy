# CML Capability Model Design

This document describes the stable structure and responsibility boundaries of
Capability Model IR, private CML normalization, finite semantic validation and
deterministic Component projection generation.
The [Capability Model specification](../spec/cml-capability-model.md)
owns normative semantics, carriers and authored grammar. [Phase 64](../phase/phase-64.md)
and its [checklist](../phase/phase-64-checklist.md) own progress and acceptance.

Current CAP-64-05 interoperability Step acceptance is recorded in
[Phase 64](../phase/phase-64.md#cap-64-05-acceptance--2026-10-15).
Separate Phase full closure and main integration remain owed.

## Application and Component kinds with common Foundation identities

Application Capability provides the application structure and management axis.
Component Capability describes architectural meaning available for Component
provided/required declarations. Separate record families keep these
responsibilities visible while sharing the existing qualified model-element
identity domain. Versioned references retain the family distinction.

The [Semantic Foundation](../spec/cml-semantic-foundation.md) supplies common
identities and reference semantics. The normative kind and identity boundaries
are [R1](../spec/cml-capability-model.md#r1-capability-kinds-and-non-instantiated-meaning)
and [R2](../spec/cml-capability-model.md#r2-exact-qualified-identity-and-contract-version).
Runtime Component identity remains owned by CNCF's Component Identity
Specification / Phase 56; the model's Component references and runtime
Component identity occupy separate domains.

## Definitions, declarations, and the relationship graph

The conceptual envelope has separate Application definitions, Component
definitions, Component declarations, and supplied compatibility data.
Definitions express meaning; declarations bind architectural meaning to an
exact Component and direction. Relationship edges retain their own attribution
instead of becoming flattened endpoint lists.

Application demand and discovery are separate many-to-many associations.
Application-to-Component mappings provide an architectural continuation without
choosing a provider. Realization edges continue from a particular Component
declaration to an executable target contract. Application dependency edges have
their own logical requirement role, independent of demand and grounding.

The complete structure is specified in
[R3](../spec/cml-capability-model.md#r3-complete-conceptual-carriers), with
demand/discovery in [R4](../spec/cml-capability-model.md#r4-demand-and-discovery-model-links),
declarations in [R5](../spec/cml-capability-model.md#r5-component-definitions-and-directional-declarations),
and mapping/realization in [R6](../spec/cml-capability-model.md#r6-application-mappings-and-realization-edges).
[R10](../spec/cml-capability-model.md#r10-validation-dependency-cycles-and-directed-compatibility)
defines the dependency and compatibility boundary.

## Independent revision-bearing sources and the Foundation boundary

Capability definitions and edges carry explicit source revisions through
`CapabilitySource`. Edge origins distinguish declared facts from supplied
derivation rules and ordered input sources. Independent attribution allows a
relationship revision to remain visible alongside the definition it connects.

Foundation's legacy source attribution remains in its existing domain.
Capability's revision-bearing source and origin carriers support this new
contract without rewriting Foundation or peer metadata APIs. Importing a
Foundation reference requires explicit source revision data alongside the
existing fact. The source and legacy boundary is specified in
[R7](../spec/cml-capability-model.md#r7-independent-revision-bearing-source-and-origin).

## Grounding, model, realization, and traceability domains

Grounding uses Foundation model or Term targets and the supplied vocabulary
relation/profile domains. The
[Terminology Metadata specification](../spec/cml-terminology-metadata.md)
provides the local context for exact vocabulary authority and literal reference
facts. Grounding retains context and labels as supplied descriptive information.

Model links describe demand or discovery; realization binds executable target
contracts; Specification and Evidence links describe independent traceability.
These distinct roles let consumers follow explicit relationships while retaining
Local/External boundaries and supplied gaps at each edge. Their normative
semantics are [R8](../spec/cml-capability-model.md#r8-grounding-and-vocabulary-authority)
and [R9](../spec/cml-capability-model.md#r9-presence-boundaries-and-independent-traceability).

## Cozy production and CNCF/CBD handoff

Cozy owns CML-facing parsing, normalization, validation, diagnostics, IR, and
deterministic multi-CML production. CNCF Phase 78 owns external Component
Capability semantics, concrete projection/codec/ABI admission, target binding,
and discovery. Textus CBD Support Phase 11 owns ingestion, catalog, and queries
over admitted upstream facts.

The generator separates upstream Application management data from the Component
projection. Its generation context carries independent producer, projection,
ABI, Component/package, and source-revision domains. CNCF's Component Capability
Specification R1-R10 is the external authority; CAP-78-02 supplies the concrete
external contract consumed by Cozy CAP-64-04. The handoff semantics are
[R11](../spec/cml-capability-model.md#r11-generation-domains-and-external-ownership).

The [R12 ownership table](../spec/cml-capability-model.md#r12-implementation-and-proof-boundary)
assigns syntax and actual IR to CAP-64-02, validation to CAP-64-03, generation to
CAP-64-04, and real producer/admission/consumer proof to CAP-64-05. It separates
this conceptual contract from the implementation and executable evidence needed
for the downstream ingestion boundary.

## Original CML documents and normalization responsibilities

The caller pairs each separately parsed Kaleidox document with explicit source
scope before any legacy aggregate merge. The normalization entry consumes
original logical sections, using containment filtering to keep promoted
divisions attached to their original parent. Its result is an ordered vector
of parsed document envelopes. Retaining the envelope makes source authority,
path and per-document compatibility available to the semantic validator without
flattening or choosing among records.

The [private input contract](../spec/cml-capability-model.md#private-scala-ir-and-per-document-input)
and [authored grammar](../spec/cml-capability-model.md#initial-authored-cml-grammar)
own the exact API, syntax and rejection behavior. Legacy CAPABILITY and metadata
remain separate programs. The private entry needs no existing Modeler,
Kaleidox, Foundation or deployment-style integration edit.

| Module | Structural responsibility |
| --- | --- |
| [CmlCapabilityModel](../../src/main/scala/cozy/modeler/CmlCapabilityModel.scala) | Immutable R3 records and distinct closed Capability, direction, role and realization-target families; actual Foundation and Java ComponentId types. |
| [CmlCapabilitySource](../../src/main/scala/cozy/modeler/CmlCapabilitySource.scala) | Revision-bearing source/origin, explicit source input/document envelopes, syntax issue carriers and lexical primitives. |
| [CmlCapabilityCml](../../src/main/scala/cozy/modeler/CmlCapabilityCml.scala) | Private callable SourceInput entry, parser failure/scope gate and all-or-nothing document results. |
| [CmlCapabilityCmlSectionsPart](../../src/main/scala/cozy/modeler/CmlCapabilityCmlSectionsPart.scala) | Original-section extraction, role-aware traversal, physical field-line grammar and actual section locations. |
| [CmlCapabilityJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityJsonPart.scala) | Duplicate-aware single JSON value boundary and shared closed fields, scalar, array and Presence decoding. |
| [CmlCapabilityDefinitionJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityDefinitionJsonPart.scala) | Application/Component definitions and distinct versioned mapping/dependency targets. |
| [CmlCapabilityReferenceJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityReferenceJsonPart.scala) | Foundation qualified identities/targets, model links, grounding, independent traceability and source/origin decoding. |
| [CmlCapabilityDeclarationJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityDeclarationJsonPart.scala) | Shared canonical Component references, complete declarations, repeated realization keys and compatibility. |

The three carrier/entry objects are private to Cozy; decoding and section
helpers are private to the modeler package. Closed field decoders stage typed
values only after successful decoding. Independent field and array outcomes
retain deterministic issue order; immutable records are assembled only when
their required structure succeeds. Jackson validates duplicate keys and the
single-value boundary before Play JSON can construct an object map.

Authored source line/revision and parser section location have distinct roles.
Direct sources bind to the document's explicit scope; derivation inputs can
retain foreign source scopes. The semantic validator receives unresolved
duplicates and graph/target facts through these same parsed envelopes, then
owns semantic admission and structured resolution. The generator consumes
validated facts under CAP-64-04 and the accepted CNCF external contract.

The [C64P executable case map](../spec/cml-capability-model.md#authored-executable-normalization-cases)
links two real separately parsed CML resources, complete typed expectations and
active property specifications. These inputs demonstrate authored syntax and
normalization with focused validation and independent CAP-64-02 acceptance
recorded in [Phase 64](../phase/phase-64.md#cap-64-02-acceptance); they supply no
generated producer/admission/CBD proof.

## Finite semantic validation responsibilities

The pipeline retains separately parsed source envelopes throughout:

```text
original Kaleidox models + explicit per-document SourceScope
  -> CmlCapabilityCml.normalize
  -> original ordered CapabilityDocuments + explicitly supplied finite ScopeInput
  -> CmlCapabilityValidation.validate
  -> private Validated(documents, scope, ordered target bindings)
  -> CmlCapabilityGeneration.project + explicit per-snapshot GenerationContexts
  -> canonical Component-only CNCF R11 JSON snapshots + original Validated retention
  -> CmlCapabilityGenerationScala.render + explicit ScalaOutputs
  -> pure segmented Scala carriers and bounded selected-path publication
```

Normalization and semantic validation remain separate callable entries. The
validator receives acquisition outcomes as supplied values rather than acquiring
Foundation, BoK or executable contracts itself. Scope facts use existing identity
domains with new explicit revision-bearing sources. Its result connects original
source records to exact target facts without making targets executable or selecting
providers. The specification owns the
[private API and finite resolution policy](../spec/cml-capability-model.md#private-finite-semantic-validation).

| Module | Structural responsibility |
| --- | --- |
| [CmlCapabilityValidationScope](../../src/main/scala/cozy/modeler/CmlCapabilityValidationScope.scala) | Immutable explicit acquisition outcome, finite model/vocabulary/contract/target fact carriers, closed model kinds and source-correlated diagnostics. |
| [CmlCapabilityValidation](../../src/main/scala/cozy/modeler/CmlCapabilityValidation.scala) | Orchestration of gates, sole successful construction of private Validated, original envelopes/scope and ordered bindings. |
| [CmlCapabilityValidationShapePart](../../src/main/scala/cozy/modeler/CmlCapabilityValidationShapePart.scala) | Typed R3 lexical, Presence, closed role/reference and Component carrier checks. |
| [CmlCapabilityValidationSourcePart](../../src/main/scala/cozy/modeler/CmlCapabilityValidationSourcePart.scala) | Source validity, original scope ownership, qualified authority and independent derivation evidence contexts. |
| [CmlCapabilityValidationScopePart](../../src/main/scala/cozy/modeler/CmlCapabilityValidationScopePart.scala) | Scope fact validity, exact candidate sets, current snapshot uniqueness and historical family/version distinctions. |
| [CmlCapabilityValidationReferencePart](../../src/main/scala/cozy/modeler/CmlCapabilityValidationReferencePart.scala) | Local role/grounding/vocabulary/traceability resolution and exact Application mapping/dependency references. |
| [CmlCapabilityValidationDeclarationPart](../../src/main/scala/cozy/modeler/CmlCapabilityValidationDeclarationPart.scala) | Complete declaration uniqueness, repeated-key agreement and exact executable target tuple binding. |
| [CmlCapabilityValidationGraphPart](../../src/main/scala/cozy/modeler/CmlCapabilityValidationGraphPart.scala) | Finite Local dependency reachability, cycle edge attribution, direct Component compatibility and result predicate. |

The two carrier/entry objects remain private to Cozy and the six responsibility
helpers private to modeler. Scalar/source checks keep malformed carriers out of
resolution. Valid duplicate candidates remain available to contextual diagnostics;
no intermediate successful bindings escape an eventual failure. Finite graph
reachability marks authored cycle edges in supplied order and excludes the
independent demand, grounding, traceability and realization relationship domains.

The [C64V case map](../spec/cml-capability-model.md#authored-executable-semantic-validation-cases)
connects real-parser scenarios, source-bearing fact and target mismatch tables and
two active properties to this boundary. Supplied synthetic facts exercise the
validator when executed; actual acquisition/extraction and producer/admission/
consumer evidence remain separate. CAP-64-03 validation and independent acceptance
are recorded in [Phase 64](../phase/phase-64.md#cap-64-03-acceptance).
CAP-64-04 generation acceptance is recorded in
[Phase 64](../phase/phase-64.md#cap-64-04-acceptance);
CAP-64-05 interoperability Step acceptance is recorded in
[Phase 64](../phase/phase-64.md#cap-64-05-acceptance--2026-10-15);
the separately tracked upstream parser candidate retains its existing separate
owner. Separate Phase full closure and main integration remain owed.

## Deterministic generation and existing provenance responsibilities

The callable producer core composes original normalization, explicit finite
validation and validated-only projection. It retains the entire original
Validated record alongside canonical per-document/Component snapshots. Explicit
context selection provides attribution and Component/package/version domains;
it does not acquire facts or infer definition ownership. R11 encoding uses the
accepted CNCF closed wire through Play JSON on Cozy's Scala 2.12 floor.

The specification owns the
[generation, selection and publication contract](../spec/cml-capability-model.md#private-deterministic-projection-generation).
The eight modules divide those responsibilities:

| Module | Structural responsibility |
| --- | --- |
| [CmlCapabilityGenerationContext](../../src/main/scala/cozy/modeler/CmlCapabilityGenerationContext.scala) | Explicit independent contexts, SnapshotKey, ScalaOutput and closed structured failure carriers. |
| [CmlCapabilityGeneration](../../src/main/scala/cozy/modeler/CmlCapabilityGeneration.scala) | Actual normalization/validation composition, validated-only projection and original successful retention. |
| [CmlCapabilityGenerationValidationPart](../../src/main/scala/cozy/modeler/CmlCapabilityGenerationValidationPart.scala) | Context domains, truthful producer, exact source/key ownership and complete document/declaration coverage. |
| [CmlCapabilityProjectionJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityProjectionJsonPart.scala) | Closed accepted R11 root and immutable Play JSON snapshot. |
| [CmlCapabilityProjectionReferenceJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityProjectionReferenceJsonPart.scala) | Source/origin, Presence, qualified identities, boundary, labels, discovery, grounding and trace encoders. |
| [CmlCapabilityProjectionDefinitionJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityProjectionDefinitionJsonPart.scala) | Component definition and original per-document compatibility encoding. |
| [CmlCapabilityProjectionDeclarationJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityProjectionDeclarationJsonPart.scala) | Directional Component declarations, repeated keys and all realization target families. |
| [CmlCapabilityGenerationScala](../../src/main/scala/cozy/modeler/CmlCapabilityGenerationScala.scala) | Complete explicit selection, segmented pure common-Scala carriers and bounded file publication. |

Context/generation/Scala entries are private to Cozy; JSON and validation helpers
are private to the modeler package. The successful constructors preserve the
validated/projection/rendered chain, so callers cannot turn raw IR into an
external certificate. Canonical outer order leaves original authored inner
vectors and compatibility states intact.

Real selected carriers remain ordinary generated `.scala` artifacts beneath
`target/`. Current GenerationProvenance records V1 known inputs and Scala paths,
then aggregates/rebinds V2 with sorted literal identities and the complete
`Artifact(path)` union. The package boundary validates schemas, exact versions,
identities and readable selected paths; readable content changes remain
admissible. Direct authoring path/symbol/content collisions still reject before
publication. The specifications use actual
original CML files, core generation, selected output publication and accepted
provenance APIs. The compiler and temporary child loader exist only in the
specification of the pure metadata accessor; production generation has no
ClassLoader or target-runtime responsibility.

The [C64G case map](../spec/cml-capability-model.md#authored-executable-generation-cases)
connects the core, literal/order properties, compiled carriers and existing
provenance outcomes. CAP-64-04 Step acceptance is complete and recorded in
[Phase 64](../phase/phase-64.md#cap-64-04-acceptance).
Normal CLI composition, actual target acquisition, Scala 3 CNCF
decoder/admission/binding/package/discovery and CBD consumption remain
CAP-64-05 and the CNCF/CBD owners. Resource placement and runtime activation are
not selected by this core. Separate Phase closure/local release and main
integration remain outstanding obligations.

## Normal source production and complete publication responsibilities

[R13](../spec/cml-capability-model.md#r13-normal-production-publication-and-application-handoff)
owns the opt-in caller, closed request, explicit finite facts, selected outputs
and canonical Component/Application product inputs. The normal Modeler hook
preflights the literal primary source path and identity, then prepares the full
Capability candidate inside the existing component-identity context before any
ordinary writes. It executes normalized ordinary source arguments, writes model
metadata from the original positional source and the component API descriptor,
publishes the accepted full batch, and writes current Scala path provenance. Typed multi-source acquisition
retains source order and makes no aggregate V1 provenance claim.

| Module | Responsibility |
| --- | --- |
| [Cozy](../../src/main/scala/cozy/Cozy.scala) | Existing normal CLI strict opt-in integration/selected source/output ordering and help only; retain original public constructor/other dispatch. |
| [CozyArchivePackager](../../src/main/scala/cozy/archive/CozyArchivePackager.scala) | Existing normal CAR strict three-flag selection/collision check and sole schema-3 serializer; a pure internal wrapper renders the selected Capability Directory release with empty entities/extensions/config. |
| [CozyCapabilityArchivePart](../../src/main/scala/cozy/archive/CozyCapabilityArchivePart.scala) | New bounded canonical index/resource archive-entry selection and envelope/actual-coordinate checks; no second semantic parser. |
| [CmlCapabilityProduction](../../src/main/scala/cozy/modeler/CmlCapabilityProduction.scala) | New private successful prepare/publish facade/original failure wrappers; selected input acquisition/core composition/complete batch. |
| [CmlCapabilityProductionRequest](../../src/main/scala/cozy/modeler/CmlCapabilityProductionRequest.scala) | New immutable ProductionRequest/PackageOutput/ResourceOutput and closed constants/types; no defaults or successful Prepared constructor. |
| [CmlCapabilityProductionRequestJsonPart](../../src/main/scala/cozy/modeler/CmlCapabilityProductionRequestJsonPart.scala) | New closed strict request+scope/context/selection JSON codec using unchanged original field/reference helpers. |
| [CmlCapabilityProductionSourcePart](../../src/main/scala/cozy/modeler/CmlCapabilityProductionSourcePart.scala) | New bounded source-root/selected original local Kaleidox parsing and honest typed failures. |
| [CmlCapabilityPackagePublication](../../src/main/scala/cozy/modeler/CmlCapabilityPackagePublication.scala) | Sole complete descriptor/index/resource/Scala/full Application batch; exact selected Component release admitted through the existing coordinate codec and serializer, with typed descriptor failure and separate 16MiB bound. |
| [CmlCapabilityPackagePublicationPart](../../src/main/scala/cozy/modeler/CmlCapabilityPackagePublicationPart.scala) | Bounded filesystem/selection/inventory/atomic publication; no-follow root/immediate-meta alternative descriptor preflight and unchanged selected-leaf/idempotency checks. |
| [CmlCapabilityApplicationProjection](../../src/main/scala/cozy/modeler/CmlCapabilityApplicationProjection.scala) | New lossless full original Application product serialization, source coverage and explicit no-inverse-edge contract. |

Successful Prepared and publication Batch values have sole private constructors.
The source helper consumes `GenerationProvenance.SourceInput(path,identity)` or
explicitly selected local files. A supplied SourceInput requires one exact
literal source header with the same normalized physical path and logical
identity; null or mismatched input fails without fallback. Both routes read
bounded actual file contents through the existing strict UTF8 reader. The
ordinary modeler and bounded Capability parser may each read that original
selected file; no capture, retained source bytes or protection copy is used.
Original normalization, finite validation, R11 projection
and Scala rendering own their existing diagnostics and coverage checks.
Application serialization consumes the same Generated.validated and ordered
source headers, preserving every original definition, source and Presence value.

Each selected Directory bundle includes one root `component-descriptor.json`
from the existing CAR serializer, using only its selected namespace/local ID and
Component release. Its empty entities/extensions/config introduce no native,
knowledge or style evidence; package/producer/source domains remain independent.
Publication preflights that descriptor in the complete batch and existing
metadata inventory before writing. Root descriptor alternatives in all six
canonical formats and immediate meta/component-descriptor alternatives, including
case aliases and equal contents, reject without following links or selecting a
winner. Unrelated root/meta assets remain preserved. Same-directory temporary files and atomic move provide bounded file
publication; failures report actual written paths without rollback claims. CAR
selection copies only the canonical index and listed siblings, checks actual
Component and independent package coordinates, and rejects collisions across
all composed archive entries before the destination ZIP is created. The existing
public CNCF Factory owns actual Directory/CAR admission, native target binding
and discovery; CBD owns subsequent consumption without CML reparsing.
