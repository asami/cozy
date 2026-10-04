# Logical UI Generated Producer Design

Document role: stable Phase 76 design transcribed from parent-decided contract
`phase76-cycle-plan-001` revision 1. Status: documentary design accepted after
retained independent Step review PASS, committed locally as
`0224d56cd5a1a94ab110b7e776e6922264eb7950`. P76-02 pure-producer executable
verification and fresh independent Step review are accepted; the
[pure producer](../../src/test/scala/cozy/modeler/LogicalUiProducerSpec.scala) and
[ordinary CML](../../src/test/scala/cozy/modeler/LogicalUiCmlSpec.scala) specifications
passed parent focused validation. The local
P76-02 Step acceptance commit is
`d9e106ca7d154402487274e189063f4276d0d94a`. P76-03A source generation and
ordinary in-process wiring are accepted after 322/322 focused tests across
nine suites, including three ScalaCheck properties with at least 100 successful
cases, and independent Step review `phase96-cozy76-p76-03a-step-review-001`
r1 PASS by `/root/phase96_cozy76_p76_03a_step_review_001`, attributed in
original CNCF primary ledger `phase96-review-ledger-001` r14. The local
P76-03 Step acceptance commit is
`8a4c1794b7ad4557af5b4260d1233acd07520c6e`. P76-04A's
[generated handoff journal](../journal/2026/10/2026-10-04-phase-76-generated-handoff.md)
records actual twice-generated CLI LUI-GEN-01 delivery and 254/254 focused
producer/runtime tests across six suites, with SBT/wrapper exit 0 and shared
lock released. P76-04 producer generation handoff is accepted under
`phase96-cozy76-p76-04-acceptance-handoff-001` r2. Independent Step review
`phase96-cozy76-p76-04a-step-review-001` r1 by
`/root/phase96_cozy76_p76_04a_step_review_001` retains original FINDINGS;
its sole `CPB-COZY76-P7604-PRODUCER-COORDINATE-001` is resolved by accepted
parent M0 amendment `phase96-cozy76-p76-04a-coordinate-m0-amendment-001` r1,
recorded in original CNCF ledger `phase96-review-ledger-001` r17. The local
P76-04 Step commit is `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`.
CNCF P96-03 accepted same-generated Scala 3 compilation, normal
ComponentFactory admission/bootstrap and equivalent List/Detail models at
local Step commit `bef59e729834addd3ac00c1930034bb23a8b428f`, after 140/140
focused tests across 16 suites with both serial locks released. The
[LUI-ACCEPT-01 reciprocal receipt](../journal/2026/10/2026-10-04-phase-76-consumer-acceptance.md)
records that evidence. Own P76-05 independent Step review
`phase96-cozy76-p76-05a-step-review-001` r1 by
`/root/phase96_cozy76_p76_05a_step_review_001` is PASS with all four criteria
MET and zero new CPB/HYG/DEV, recorded in original CNCF ledger
`phase96-review-ledger-001` r21. P76-05 is CLOSED on its unchanged checklist
criteria; its local Step commit is `3546755fc3f52606cfb1f209434a2a776ef9358b`.
P76-06 is IN_PROGRESS at P76-06A documentary acceptance: independent Step
review `phase96-cozy76-p76-06a-step-review-001` r1 PASS by
`/root/phase96_cozy76_p76_06a_step_review_001`, admitted in original CNCF
ledger `phase96-review-ledger-001` r23, finds both documentary criteria MET.
The checklist has 22 checked / 2 unchecked; the own local documentary Step
commit is PREPARED/PENDING, not executed. Phase 76 remains IN_PROGRESS;
whole-Phase validation/review, release and both Phase closures remain pending.

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
plumbing. ScalaGenerator appends `logicalUi: Option[Realm] = None` after
`generationTarget`, retaining existing callers and absent-input output. Merge
Logical UI Realm artifacts only for CNCF. ModelGenerationTarget's library
boundary rejects present Logical UI definitions before output; absence retains
ordinary library behavior.

The separate [LogicalUiScalaGenerator](../../src/main/scala/cozy/modeler/LogicalUiScalaGenerator.scala)
consumes the complete admitted projection before creating a Realm. Modeler
preflights both routes before ModelBuilder and retains only an optional Realm;
invalid source becomes an `SError` with typed Diagnostic data in an
invalid-argument Conclusion. The source-preflight exception interface uses
the same Conclusion through `RAISEC`. Null Option/Realm shape is structured;
library rejects any present Realm, including empty, before transformation.
The CNCF Realm fold follows all existing generator folds so their artifacts
remain in the complete output. No new parser or alternate validation boundary
is introduced. Keeping the renderer separate and two-route plumbing bounded
preserves the existing Modeler responsibilities without a mechanical split.

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
Accepted implementation and CNCF consumer proof are recorded by the
[Phase 76 stages](../phase/phase-76-checklist.md); the reciprocal P76-05 Step
has its own independent PASS and four accepted unchanged criteria. Its local
Step commit is `3546755fc3f52606cfb1f209434a2a776ef9358b`;
P76-06 is IN_PROGRESS at P76-06A accepted documentary criteria, own local Step
commit PREPARED/PENDING and not executed; full Phase
validation/review/release and both Phase closures remain pending.

The [existing-v1 compatibility index](../spec/logical-ui-generated-producer.md#compatibility-and-evolution-navigation--existing-v1)
cross-references LUI76-S01..07 and LUI96-S01..08 without changing this design.
The fixed ABI `cncf.logical-ui-generated.v1` / integer `1` in
`org.goldenport.cncf.display` selects no SemVer/range/negotiation. The entire
Phase 74 descriptor is exact and independently checked. Static List/Detail,
the five roles and five value kinds, explicit registration keys, preserved
order and scoped uniqueness remain the supported subset; editable/constraint,
Entity and mutation data stay outside generated definitions.

Future additive vocabulary requires explicit consumer ABI/spec/admission and
executable proof before producer emission. An opaque validated definition or
provenance revision is not vocabulary/schema authorization. There is no selected
v2, automatic backward compatibility, tolerant decoder or invalid-provider
fallback. Producer LUI76 diagnostics stop invalid syntax/owner/subset before
STree; consumer `LUI96_ABI_INCOMPATIBLE` and `LUI96_CONTRACT_INCOMPATIBLE`
retain exact descriptor rejection alongside the existing scalar/scope/target
diagnostics. Component-first selection, whole-vector static admission and late
atomic typed joining remain the gates before Display exposure.

Phase 74 semantic authority, CNCF ABI/runtime, Cozy compiler/source and explicit
application bindings keep separate ownership. Declared provenance != definition
revision != receipt Git commit != runtime Entity/source revision != Display wire
schema/version. The [developer guide](../guide/logical-ui-generated-producer.md)
navigates the actual LUI-ABI-01 -> LUI-GEN-01 -> LUI-ACCEPT-01 sources and
three durable receipts; the [dated handoff](../journal/2026/10/2026-10-04-phase-76-compatibility-developer-handoff.md)
maps all Phase scope/criteria. Goldens and the handwritten expected fixture
remain expectations, distinct from generated delivery and consumer compilation.
No shared Scala 2.12 runtime binary, runtime CML parser or Flutter dependency
enters Scala 3 consumption through this documentary navigation.
