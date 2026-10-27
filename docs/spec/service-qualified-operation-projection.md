# Service-qualified operation projection

Status: normative projection contract

Executable specification:
[ServiceQualifiedOperationProjectionSpec](../../src/test/scala/cozy/modeler/ServiceQualifiedOperationProjectionSpec.scala).

## Owned normalized definitions

`ModelServiceOperationProjector.serviceOperationDefinitions` returns a
`Vector[(String, OperationModel.NormalizedOperationDefinition)]`. Each tuple
retains the actual parsed service name beside its normalized operation. The
existing service iteration, normalization and metadata merge are preserved.
The existing `normalizedServiceOperationDefinitions` projection returns the
tuple values and preserves its previous semantics for existing callers.

The service owner is modeled metadata, independent of a canonical bare
operation name. Two distinct services may declare `readOriginal` without
renaming either operation or changing their DTO identities.

The native `ModelBuilder` carries each parsed service's operation leaf contracts
in `serviceOperationLeafContractsByService`, using the existing owner-specific
CML extraction and its duplicate selected-operation validation. Both service
method construction and normalized definition projection select a leaf by the
actual service name and then operation name before merging kind, input and
output. If owned leaf metadata is present, a missing service or operation leaf
does not fall back to another owner's same-name contract.

The existing flat `serviceOperationLeafContracts` map and all previous builder
parameters and defaults remain available. The new owner map is the final,
defaulted builder parameter. Only contexts whose owner map is empty use the
previous flat-map lookup, preserving manually built legacy contexts and
owner-narrowed compatibility views. Native builder copies retain their owned
metadata, including when Dart selects a service-specific view.

`ModelRuntimeDefinitionProjector` consumes the owned sequence, retains operation
name sorting, and supplies `Some(owner)` in `MComponent.OperationDefinition`.
Summary, input/output identities and descriptions, ordered parameter and result
schemas, access, authorization, evaluation and relationship metadata retain
their existing projections. Separately generated entity-operation definitions
retain their existing ownership representation; this projection does not infer
an owner for them.

## Producer and consumer responsibilities

Kaleidox owns CML parsing and its existing normalized model. Cozy extracts the
actual service owner from `ServiceClass` beside that normalized model and
supplies it to SimpleModeler. No Kaleidox source or dependency contract changes
are required by this boundary.

SimpleModeler owns carrying the optional service owner into its Scala model,
stamping actual service methods, and selecting owner-qualified metadata for
request/action/factory/response and API generation. Exact owner selection takes
precedence over a unique ownerless legacy definition. Another owner's same-name
definition is never selected as a fallback; selected ambiguity is a model
syntax error.

## Behavioral specification

The executable specification parses real CML and projects two services sharing
`readOriginal`. It observes actual `Some(owner)` values, input DTO names, ordered
String field schemas and result identities. BookMaterial retains
`BookMaterialReadOriginalQuery(resourceId, originalId)` and KnowledgeMaterial
retains `OriginalSelectionQuery(materialRef, resourceId)`.

The existing `run_modeler_scala` path generates actual Scala artifacts only under
owned `target/` work directories. The specification checks service-local request
requiredness, action and factory types, response identities and absence of the
other owner's fields. Reversed same-name command/query declarations retain
their own kind, access policy and implementation. A deterministic property uses
independently generated safe owner names and real parsing/projection in both
declaration orders, requiring twenty successes with zero discards.

## Staged runtime boundary

This contribution does not emit a service owner argument into CNCF
`CmlOperationDefinition`. Its current runtime carrier has no owner field.
Assessment of existing bare-name runtime metadata consumers, refreshed producer
dependencies, parent-run focused tests, native Studio generation/compilation,
independent review and whole Phase acceptance remain separate work. A generated
source artifact is not evidence of runtime metadata isolation or first-original
completion.
