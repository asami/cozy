# Component Usage Capability Specification

Status: current normative contract; executable authoring awaits parent validation and review
Adopted: 2026-10-15, corrected after the Application-owned package proposal
Owner: Cozy Phase 64 / CAP-64-PKG01; consumers CNCF Phase 78 and CBD Phase 11/11.1

## Meaning and separate model families

`ComponentUsageCapability` describes what a particular Component can be used for,
inductively organized from Use Cases, usage examples and other attributed evidence.
`ComponentCapability` describes the Component's provided/required architectural
contracts and explicit realizations, used deductively for matching and checking.
These are separate typed model families, not aliases, enum spellings of one
interchangeable record, or a single flattened capability list.

Application is reserved for the Application Model/Tier contrasted with Domain.
Neither this usage model nor the package containing it represents an Application
in that sense. The former `ApplicationCapability` model is reclassified here;
this is a semantic correction as well as a rename. The Component review metadata
set can include multiple model families; Usage Capability is only one of them.
Do not rename all model metadata to Usage Capability or create an Application
owner merely to distribute Component metadata.

A usage observation is not a provided contract, permission, current availability,
successful execution or proven test result. An authored/derived Usage capability
keeps its source and discovery evidence even when corresponding contract or
realization evidence is absent. Never upgrade it into a Component contract by
matching labels or filling missing links.

## Records, ownership and mapping

Retain the qualified model identity, independent contract version, name,
summary/description Presence, source, demandReferences, discoveryReferences,
componentMappings, dependencies, grounding and traceability of the old record
where their semantics conform to this definition. Add an explicit `component`
owner using the existing CNCF Component identity domain. That field names the
Component being described; it is not an Application or a model ID inferred from
an output path. Qualified model identity remains separate from Component identity.

Use `VersionedComponentUsageCapabilityReference`,
`ComponentUsageCapabilityMapping` and `ComponentUsageCapabilityDependency` as
separate typed references/edges. A mapping targets a versioned ComponentCapability
and keeps boundary, source and origin. Dependencies point to Usage Capability
references and express logical usage requirements, not execution order.
Use Case/usage-to-Usage links and Usage-to-ComponentCapability mappings retain
explicit many-to-many cardinality, direction and edge attribution. Discovery
is not forward demand: the unavailable upstream forward UseCase projection stays
unavailable until separately provided. No new inverse edge is inferred.

The source input explicitly assigns each Usage definition to its Component.
The generator publishes only records for the selected Component, preserving
references to other Components as external references. A shared source document
may produce multiple Component images, but the whole document's Usage records
must not be copied indiscriminately into each CAR. Existing source/origin and
Presence semantics remain; missing ownership is incomplete input, not permission
to infer an owner from the first declaration, mapping, name or filename.

## Source and wire contract

The normal CML declaration is `COMPONENT-USAGE-CAPABILITY`. The private Scala
record is `ComponentUsageCapability`; kind is `ComponentUsageKind`. Scope facts
use `COMPONENT_USAGE_CAPABILITY`, with `usageContracts` replacing the old
Application-contract collection. The validated model collection is
`componentUsageCapabilities`.

Cozy emits `cozy.component-usage-capability.v1` with integer `schemaVersion=1`.
The envelope carries `producer`, the selected `component` coordinate (including
release), `package` coordinate/version and ordered `documents`. Each document
retains its full source header and `componentUsageCapabilities`; each definition
has `kind: COMPONENT_USAGE`, its explicit Component identity owner and the
semantic fields above. Producer versions, source revisions, contract versions
and package releases remain independent. The envelope's owner must agree with
all included records. An acquired empty projection is distinct from no family.
The existing `cncf.component-capability.v1` ABI and its PROVIDED/REQUIRED meanings
remain separate and unchanged by this rename.

## Component package and normal generation

The package owner is the Component under review. CNCF's
`docs/spec/component-usage-capability-package.md` defines fixed paths:
`meta/component-usage-capability/index.json` and `usage.json` beside the existing
`meta/component-capability/` family. Both belong to the same Component's Directory
image or CAR; the Directory is directly consumable during development.

Replace `applicationOutput` and the withdrawn `applicationPackage` proposal with
the existing `packages` selection extended by explicit `usageCapabilities`
references for that Component. `resources` continues to select the existing
Component projection family; empty Component resources are allowed for a genuine
Component with only usage metadata. Do not create a synthetic Application package.
The normal producer derives metadata filenames from the package layout. No caller
selects a Usage filename or supplies a parallel loose-JSON input to CBD.
The private production request's new schema is revision 2, including `usageContracts`
in its scope. Coordinate/root ownership and actual source selection remain explicit.

Metadata-only Capability generation with no actual Scala artifacts emits no V1
Scala-generation provenance. Normal invocations with actual Scala artifacts retain
the existing provenance, and Capability metadata retains its original source fields.

## Exact production and projection records

The production request root has exactly `schemaVersion`, `producer`, `sources`,
`scope`, `contexts`, `packages`, `scalaOutputs`, with integer revision 2. Scope
replaces the old collection with `usageContracts`; its closed model kind is
`COMPONENT_USAGE_CAPABILITY`. Context/projection schema versions remain 1.
Each package has exactly `component`, `package`, `path`, `resources`,
`usageCapabilities`. Each Usage selection reference has exactly
`kind: COMPONENT_USAGE`, `identity: {modelId,elementId}`, `contractVersion`.
Resolve those references in validated original documents and require exact
canonical Component ownership. Unknown/ambiguous, duplicate or wrong-owner
selection is a typed failure before ordinary generation writes. Unselected
records need not be published; selection is never inferred from names or paths.

The Usage envelope has exactly `schemaVersion`, `generatedAbiVersion`, `producer`,
`component`, `package`, `documents`. Component coordinate is `{namespace,id,version}`;
package is `{coordinate,version}`. Each contributing document has exactly
`source`, `componentUsageCapabilities`. Preserve original request source order,
complete literal headers and each selected definition's original record/edge order.
The explicit owner is `{namespace,id}` on each `COMPONENT_USAGE` record.
Mapping targets use `kind: COMPONENT`; dependency targets use
`kind: COMPONENT_USAGE`, with identity and independent contractVersion. Both
edges retain their boundary, source and origin without an inferred inverse edge.

The Usage index is exactly `{schemaVersion:1,projections:["usage.json"]}` at
`meta/component-usage-capability/index.json`; its sole resource is the fixed
sibling `usage.json`. Empty Usage selection emits no family. The empty Component
index remains valid for a Usage-only Component. The complete descriptor,
Component metadata, optional Usage metadata and original Component Scala artifacts
join one immutable existing publication batch with the original preflight and
idempotence behavior. The three existing normal reservations apply to package and
Scala paths. Normal package-car carries the same optional bytes beside ordinary
config, reusing the existing limits/strict UTF8/index/context/coordinate and actual
entry-collision code without reparsing Usage record semantics.

## Coordinated breaking change, no compatibility layer

This is pre-release work. Remove old types, constructors, extractors, methods,
fields, grammar declarations, tags and codecs from the current implementation.
Update producer, native bridge, CBD ingestion/catalog/query, generated APIs,
fixtures and examples together. Do not add type aliases, deprecated names,
forwarding methods, dual readers, automatic conversion or fallback paths.
A stale typed call site MUST fail compilation. Old CML/JSON is rejected by ordinary
syntax/schema validation; do not introduce an old-format detection/migration
subsystem or a compatibility test matrix. Regenerate disposable generated data.

The old `cozy.application-capability.v1` is not a second supported ABI. Preserve
historical acceptance and journals as history, not executable compatibility.
Any retained durable historical data is not silently reinterpreted as a Usage
record; it is outside the normal new input route. Existing Component Capability
compatibility edges concern provided/required contracts and are not a legacy
Application compatibility mechanism; their semantics remain unchanged.

## Focused acceptance for the new boundary

- Compile the producer and affected consumer/generated APIs after deleting old
  symbols; no alias hides an unconverted call site. Update the existing relevant
  executable specifications, rather than keeping parallel old/new suites.
- Use a real multi-Component source to prove explicit owner partitioning and
  external references. Do not duplicate the complete Usage envelope across CARs.
- Preserve inductive evidence and many-to-many mappings without promoting usage
  evidence into PROVIDED/REQUIRED declarations, native realization or execution.
- Prove the normal generated Directory -> CNCF -> CBD route, then equivalent CAR
  semantic/query results from that image. Do not require CAR creation for routine
  development intake. Reuse existing lower-layer resource/codec verification.

The preceding Application-based executable acceptance remains historical. New
model, source, ownership and wire behavior requires new focused acceptance.

CAP-64-PKG01 updates the existing parser, semantic, projection, normal CLI,
Directory and archive executable specifications. The real source assigns Store
and Archive explicitly in one document and retains External references to
Remote. C64P26/C64I17–19/C64B13–15 cover current owner/selection diagnostics,
Usage-only products, optional absence and normal CAR context/bytes. Parent-owned
validation, independent review and the CNCF/CBD U11 integration remain pending;
no tests were executed in this authoring pass.
