# Failure Model ABI lowering

*Since:* Oct. 3, 2026. *Version:* Oct. 13, 2026.

The [ABI contract](../spec/failure-model-abi-contract.md) fixes product paths,
public shapes and behavior. `FailureModelAbiGenerator.scala` is the cohesive,
private modeler emitter. `Modeler.scala` remains an adapter from the actual
Kaleidox `SModel.model` to admitted definitions using the existing match/DbC
boundary. SOURCE grammar, ownership, immutable IR and ordered effective
projection remain the accepted S72.1 implementation.

## One ordered projection, two encodings

Build an internal immutable tree from the accepted component vector. Fixed
record names identify generated constructors; ordered named fields follow the
accepted IR constructor order. Text, integers, scope values, optional values
and vectors retain their distinctions. JSON and Scala writers consume that
same tree, preventing a separate merge or resolution algorithm in either writer.

JSON records retain camelCase field labels and encode option absence as null.
Scala records call the fixed public case classes and encode options as None or
Some. Scope values are matched explicitly to the two sealed alternatives. Both
writers use safe quoted text; authored identities are never constructor names.
Static declarations mirror accepted IR shapes, excluding admission issues and
runtime imports. Empty definitions produce an empty Realm.

The generator adds no timestamp, absolute source file identity, descriptor flag,
publication hook, inferred failure or robustness mechanism. It preserves the
already admitted source locations and all ordered authored/default/effective data.
Cozy's effective levels are development comparison evidence. CNCF alone owns
the canonical recipient resolver and runtime validation.

## Additive integration and compatibility

The trailing defaulted `ScalaGenerator.failureModels` argument follows
`generationTarget`. Both Modeler generation routes obtain it before generation
from the actual source model. The CNCF target conditionally adds the sidecar
and metadata Realm only for a nonempty vector. The empty branch leaves the old
realm composition and inventory intact.

The library target forwards the vector to generator-input policy. Value Modeler
forwards it to model admission; `requireLibrarySource` derives it from the actual
source. Policy appends the `failure-model` category only for nonempty input,
preserving existing diagnostic category order and public argument labels.

Workflow definitions/generators, component API contract metadata and model
metadata implementations are unchanged. Failure Model output coexists with
the current selected Workflow schema without evolving that schema.

## Evidence boundaries

The behavior spec invokes real public CLI entrypoints with original fixtures,
checks complete newly generated products and decodes the full Failure Model
back into independent typed shapes. Its JSON oracle fixes constructor field
order recursively. Pure typed samples include absent/present locations and
escaped text. The seeded 100-sample property checks full roundtrip, order,
winner/origin chains and sibling inheritance against semantic counterexamples.

The parent admitted legacy generation on native SOURCE commit
`6c15b65cd83181fdf61aa8674c9a847f79751805` before ABI editing, with terminal success
and released serial lock. The resulting parent-owned fixture retains all four
fixture/route inventories and 80 complete files. The current spec reads it unchanged and compares every generated file and byte,
with the explicit adaptations in the ABI contract. Expected model metadata
removes the historical `source.sha256` line and rebinds only `source.path` to
the selected fixture, without replacing unrelated strings or changing the
producer. The normal Workflow component expectation adds only the four named
operation content builders introduced independently by SimpleModeler `549a38c`.
SimpleModeler `52aea03` additionally replaces private conversion implementations
with canonical ModelValueEncoder and ValueReader delegates. A fixed expected-source
adapter applies only the named helpers in CountryCode, ReviewContext,
ComponentFactory and DomainComponent, removing the unused application-record
vector helper in these four products. Expected generic vectors retain
scalar compatibility and contextual readers forward the identical context.
Every line outside these declared adaptations, every inventory and every other
expected byte remains exact; actual generated products are compared unchanged.
The baseline producer identity and fixture remain unchanged; no expectation is
derived from newly generated output and no broad comparison exclusion is added.

Typed Scala consumers are written beside actual normal/value/mixed generated
metadata. Mixed output retains the original single Domain component and old
Workflow v5 products. The source-derived declarations are written only under
`target/test-generated`; no new durable source fixture is introduced.

The parent accepted 105/105 behavior tests across six suites, froze actual 2/2/5
source inventories and completed sequential Scala 3.3.8 compilation with distinct
class and incremental analysis directories, successful SBT/wrapper exits and
released serial locks. Compilation acceptance rests on those actual runs.
Independent protected ABI review passed with zero current blockers, followed by
parent manual local ABI commit `1d9c80775e74f7d0ba0dd60f1cf4730ef84c3843`.
The authored [recipient handoff](../phase/phase-72-cncf-handoff.md) records
reproduction, comparison evidence and CNCF91/sm-workflow prerequisites. HANDOFF
is accepted at commit `783a48552de5fa6a4b34a8dfe9c1184f005de62f`; the sole
Phase-wide review passed. Final full validation passed; the containing distinct parent manual local release commit closes Cozy Phase 72. Recipient integration remains recipient-owned.
