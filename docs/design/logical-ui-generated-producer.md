# Logical UI Generated Producer Design

Document role: stable Phase 76 design transcribed from parent-decided contract
`phase76-cycle-plan-001` revision 1. Status: documentary design accepted after
retained independent Step review PASS, committed locally as
`0224d56cd5a1a94ab110b7e776e6922264eb7950`. P76-02 pure-producer executable
verification and fresh independent Step review are accepted; the
[pure producer](../../src/test/scala/cozy/modeler/LogicalUiProducerSpec.scala) and
[ordinary CML](../../src/test/scala/cozy/modeler/LogicalUiCmlSpec.scala) specifications
passed parent focused validation. Actual Scala 3 generation/wiring, generated
consumer acceptance and whole-Phase verification remain pending. The local
P76-02 Step acceptance commit is PREPARED/PENDING, not executed.

The [producer specification](../spec/logical-ui-generated-producer.md) fixes
behavior. [Phase 74 spec](../spec/logical-ui-runtime-contract.md),
[Phase 74 design](logical-ui-runtime-contract.md),
[CozyLogicalUiRuntime](../../src/main/scala/cozy/ui/CozyLogicalUiRuntime.scala)
and [CozyLogicalUiVocabulary](../../src/main/scala/cozy/ui/CozyLogicalUiVocabulary.scala)
retain semantic authority. CNCF owns the
[fixed generated ABI](../../../cloud-native-component-framework-phase96/docs/spec/logical-ui-generated-abi.md),
[public ABI source](../../../cloud-native-component-framework-phase96/src/main/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbi.scala)
and [provider interface](../../../cloud-native-component-framework-phase96/src/main/scala/org/goldenport/cncf/display/GeneratedLogicalUiMetadataProvider.scala).

## Source and pure model boundaries

```text
Ordinary Kaleidox LogicalSection AST / source COMPONENT declarations
  -> immutable Scala 2.12 static model using shared Phase 74 semantics
  -> pure validation and admitted consumer-shape projection
  -> deterministic Realm Scala 3 definitions/provider source
  -> explicit application component/factory mixin and application bindings
  -> CNCF static admission -> post-View typed join -> List/Detail projection
```

Modeler retains the LOGICAL-UI DocumentDivision and extracts it in the ordinary
CML path. Root component names an exact existing declaration; its package and
stem come from the source component model. No implicit ownership is inferred
from ordering, screen identities or binding keys. Each component owns one root
and one selected vector. CML prologue syntax, property types and ownership are
admitted before pure semantic validation; decoded strings are preserved.

The static model uses Phase 74 PresentationRole values, an explicit closed
counterpart to its five DisplayValue kinds and the complete minimumContract
descriptor. It models List item templates and Detail sections, explicit View/
value/action binding IDs and source provenance. It does not use sample runtime
instances to infer bindings, resource identity, values or kind support.

Validation and consumer-shape projection are pure and separate from rendering.
They preserve declaration order and scoped reuse, reject malformed structures
and duplicates with the closed typed producer diagnostics, then resolve all
List targets against the complete same-component vector. Failure returns no
admitted prefix and no generated STree. CLI adaptation preserves code/path/
reason in structured Consequence/Conclusion. No string-only failure or new
failXxx utility supplies this boundary.

## Generation integration

Modeler.generateScala and Modeler.generateScalaValue both extract and validate
before building output for the CNCF target. Parsing, validation, projection and
rendering live in separate responsibility files; Modeler adds only bounded
plumbing. ScalaGenerator accepts an optional trailing producer argument with
empty default, retaining existing callers and absent-input output. Merge
Logical UI Realm artifacts only for CNCF. ModelGenerationTarget's library
boundary rejects present Logical UI definitions before output; absence retains
ordinary library behavior.

The renderer emits the component's package plus `.logicalui`, a public
`<ComponentStem>LogicalUiDefinitions` object with immutable definitions vector,
and a public `<ComponentStem>LogicalUiMetadataProvider` auxiliary mixin trait.
The trait extends CNCF's exact GeneratedLogicalUiMetadataProvider and overrides
`generatedLogicalUiDefinitions` to return the generated vector. Public CNCF
constructors and the explicit descriptor are emitted directly; no copied ABI
implementation, runtime binary bridge or JSON sidecar supplies the contract.

Safe Scala literal escaping and validated namespace/stem keep strings separate
from identifiers. UTF-8 LF bytes, final newline and declaration order are
stable. No clock, generated-time header or machine path affects output.

## Application and consumer responsibility

The application explicitly mixes the generated provider into its component or
actual factory. Generation must not rewrite user factory files. Application
registration supplies View keys and typed value/action bindings; these keys
are not property expressions, guessed inverse writes or runtime authority.
CNCF's [ComponentFactoryDisplayPart](../../../cloud-native-component-framework-phase96/src/main/scala/org/goldenport/cncf/component/ComponentFactoryDisplayPart.scala)
selects the component provider first, otherwise its factory, and admits the
complete vector atomically. Invalid selected providers have no fallback.

Static admission and runtime binding readiness are distinct gates. The typed
join waits for View/application registration and completes before usable
Display exposure. CNCF owns current resource data/revisions, authorization,
capability closure and projection. Generic Operation invocation stays in its
ordinary application/runtime boundary. Generated Action metadata is inert.
No reflective classpath discovery, independent generator, runtime consumer CML
parser, executable callback, Flutter widget or target navigation object is added.

## Versioning and proof

ABI identity/version, semantic driver descriptor, definitionRevision, declared
source provenance revision, receipt Git revisions and runtime resource/wire
revisions have separate owners and meanings. V1 accepts only the fixed
consumer vocabulary; extensions require explicit consumer admission.

The Domain/domain reference maps the consumer's handwritten ESS2-01 shape,
using declared producer provenance from the spec. Golden sources remain
expectations. LUI-GEN-01 records actual twice-generated ordinary CLI output,
input/command/revisions/deltas and deterministic evidence. CNCF LUI-ACCEPT-01
must consume those same files through Scala 3 compilation, normal discovery,
static admission and typed projection. No handwritten sample substitutes for
that receipt; no whole-Phase mutual closure or intermediate public release is
required.

Phase 51 remains PLANNED with no Flutter source/suites in this checkout.
Preserve its plan and existing compile-time target-neutral semantics; use
existing Logical UI/runtime regression proof without claiming Flutter execution.
Implementation, consumer acceptance and release work remain owned by the later
[Phase 76 stages](../phase/phase-76-checklist.md).
