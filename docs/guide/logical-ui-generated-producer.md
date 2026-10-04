# Logical UI generated producer developer guide

Document role: non-normative usage and evidence navigation for the accepted
Phase 76 producer. The [producer spec](../spec/logical-ui-generated-producer.md)
LUI76-S01..S07 and CNCF's
[generated ABI spec](../../../cloud-native-component-framework-phase96/docs/spec/logical-ui-generated-abi.md)
LUI96-S01..S08 govern behavior. This guide introduces no compatibility policy
or capability. P76-01..05 are accepted and locally committed; P76-06A-DOCS
has both documentary criteria MET after independent Step review
`phase96-cozy76-p76-06a-step-review-001` r1 PASS by
`/root/phase96_cozy76_p76_06a_step_review_001`, admitted in original CNCF
ledger `phase96-review-ledger-001` r23. Its own local Step commit is
PREPARED/PENDING, not executed. P76-06 and Phase 76 remain
IN_PROGRESS, 22 checked / 2 unchecked; final validation, full Phase review,
release and both Phase closures remain pending. The
[dated handoff](../journal/2026/10/2026-10-04-phase-76-compatibility-developer-handoff.md)
maps all ten scope items and 24 criteria, including pending final Phase gates.

## From ordinary CML to Display

```text
ESS2-01 ordinary CML COMPONENT Domain / package domain + LOGICAL-UI
  -> existing Modeler CNCF route and immutable admitted static projection
  -> public Scala 3 definitions object + metadata-provider mixin trait
  -> explicit application component/factory mixin and View/value/action bindings
  -> normal CNCF whole-vector static admission
  -> late atomic typed binding join after View registration
  -> contextual List/Detail Display projection
```

Start with the ordinary [ESS2-01 input](../../src/test/resources/logicalui/ess2-01.cml).
Its `# COMPONENT` declaration names `Domain` with package `domain`.
`# LOGICAL-UI` explicitly selects that declared component and supplies
`producerId`, `sourceId`, `sourceRevision`. Ordered `## LIST` and `## DETAIL`
prologues supply `screenId`, `definitionRevision`, `viewBindingId`, `title`;
List also names its same-component `detailTarget`. Field/action/section
containers and exact case-sensitive properties follow
[LUI76-S03](../spec/logical-ui-generated-producer.md#lui76-s03-ordinary-cml-grammar-and-component-ownership).
Use double-quoted JSON-compatible strings and ordered `allowedKinds` arrays.
Unknown/repeated keys, invalid hierarchy, multiline literals, substitutions
and runtime-only metadata are rejected. Decoded bytes are preserved rather
than trimmed or inferred from sample values. Containers may be absent/empty;
a present root requires definitions.

Both existing `modeler-scala` and `modeler-scala-value` CNCF routes preflight
the ordinary AST/component model before generated STree. Both are exercised
in-process by [LogicalUiAbiGenerationSpec](../../src/test/scala/cozy/modeler/LogicalUiAbiGenerationSpec.scala).
The actual twice-executed CLI proof uses **modeler-scala** only. No actual
`modeler-scala-value` CLI run is established by this guide. The library target
rejects present Logical UI; absence retains existing route behavior.

## Reproduction recipe and recorded evidence

These are repository-relative recipes adapted from the actual commands in the
[generation receipt](../journal/2026/10/2026-10-04-phase-76-generated-handoff.md)
and [consumer receipt](../journal/2026/10/2026-10-04-phase-76-consumer-acceptance.md).
They have **not been executed in P76-06A**. The actual historical commands
used the receipts' absolute paths. Run from the owning repository, through
the registered `cncf_command_runner` and shared serial SBT wrapper with
appropriate scoped permissions, never concurrent independent top-level SBT.
The blocks below are logical SBT argv after `--batch`, not direct shell commands.
Use the admitted source/dependency environment and keep the save roots under
`target/`; adapting a recipe does not supply a new execution receipt.

From Cozy:

```text
runMain cozy.Cozy modeler-scala src/test/resources/logicalui/ess2-01.cml --save target/phase76/p76-04/first
runMain cozy.Cozy modeler-scala src/test/resources/logicalui/ess2-01.cml --save target/phase76/p76-04/second
```

Each save root contains the nested paths below; `--save` does not flatten the
managed-source tree:

```text
target/scala-3.3.8/src_managed/main/scala/domain/logicalui/DomainLogicalUiDefinitions.scala
target/scala-3.3.8/src_managed/main/scala/domain/logicalui/DomainLogicalUiMetadataProvider.scala
```

For example, the first definitions file is under
`target/phase76/p76-04/first/target/scala-3.3.8/src_managed/main/scala/domain/logicalui/DomainLogicalUiDefinitions.scala`.
Both runs' two files were inspected and byte-compared with each other and the
existing goldens: four comparisons exited 0; definitions were 2771 bytes and
provider 329 bytes, UTF-8 LF with final newline. The receipt delivers the exact
unmodified first-run text in these resources and ordinary CNCF Test sources:

| Delivered Cozy resource (`.scala.txt`) | CNCF consumer source (`.scala`) |
| --- | --- |
| [DomainLogicalUiDefinitions.scala.txt](../../src/test/resources/logicalui/generated/ess2-01/DomainLogicalUiDefinitions.scala.txt) | [DomainLogicalUiDefinitions.scala](../../../cloud-native-component-framework-phase96/src/test/scala/domain/logicalui/DomainLogicalUiDefinitions.scala) |
| [DomainLogicalUiMetadataProvider.scala.txt](../../src/test/resources/logicalui/generated/ess2-01/DomainLogicalUiMetadataProvider.scala.txt) | [DomainLogicalUiMetadataProvider.scala](../../../cloud-native-component-framework-phase96/src/test/scala/domain/logicalui/DomainLogicalUiMetadataProvider.scala) |

The resource suffix stores delivery text; the consumer suffix makes it ordinary
Scala 3 compilation input. Do not add headers, copy ABI implementation or replace
delivery with the [definitions golden](../../src/test/resources/logicalui/golden/DomainLogicalUiDefinitions.scala.txt)
or [provider golden](../../src/test/resources/logicalui/golden/DomainLogicalUiMetadataProvider.scala.txt).
Goldens and handwritten expected fixtures are expectations, not actual generation.

The historical focused producer accumulator was:

```text
testOnly cozy.modeler.LogicalUiProducerSpec cozy.modeler.LogicalUiCmlSpec cozy.modeler.LogicalUiAbiGenerationSpec cozy.ui.CozyLogicalUiRuntimeSpec cozy.ui.CozyLogicalUiSemanticsSpec cozyruntime.CozyLogicalUiRuntimeAcceptanceSpec
```

It passed 254/254 tests across six completed suites. Both generation and this
accumulator have admitted SBT/wrapper exit 0 and shared lock release. The second
CLI invocation reported a Logback JoranException involving a removed background
job jar; that observation remains unresolved, without a new cause or repair claim.

Ordinary quoted CML depends on the accepted escaped-quote prerequisite in
`goldenport-scala-library` Scala 2.12 checkout, commit
`e2119e3b6e69d4564ee8a3c5a280be4753654fa0`, and its admitted local dependency
refresh. The [producer contract history](../journal/2026/10/2026-10-04-phase-76-producer-contract.md)
and generation receipt retain context. This guide does not run dependency
refresh/publication or substitute a parser. Coordinates alone do not establish
that the requisite local dependency state has been reproduced.

From CNCF, the historical representative logical argv was:

```text
show Test / fullClasspath
testOnly org.goldenport.cncf.component.ComponentFactoryGeneratedLogicalUiAcceptanceSpec
```

Compile/Test and GC1..GC6/GCP1 passed 7/7 tests in one suite. Full Test classpath
inspection found no `cozy_2.12` runtime jar. The exact separate fifteen-suite
`testOnly` accumulator is retained in the
[consumer command record](../journal/2026/10/2026-10-04-phase-76-consumer-acceptance.md#actual-focused-consumer-commands-and-terminal-results):
133/133 across 15 suites, total 140/140 across 16, with both shared locks
released and SBT/wrapper exit 0. These are focused intermediate results,
not full repository tests, app E2E, public release or either Phase closure.
CNCF's ordinary build also ran its existing Information CML hook; it did not
rerun the ESS2-01 producer. P76-06A executes none of these recipes.

## Application registration and normal CNCF consumption

Generation emits public `domain.logicalui.DomainLogicalUiDefinitions` and
`domain.logicalui.DomainLogicalUiMetadataProvider`. The application explicitly
mixes the latter trait into its component or actual factory. Generation does
not rewrite factory files. See the development-only
[GeneratedDisplayConsumerFixture](../../../cloud-native-component-framework-phase96/src/test/scala/logicalui/GeneratedDisplayConsumerFixture.scala):
`ActualComponent` mixes the generated provider; its application-owned base
implements `DisplayBindingProvider` and installs typed bindings. The factory
fixture demonstrates a separate factory-provider path; it is not an automatic
factory rewrite or generated application implementation.

The ESS example explicitly registers View key `ess.reference`, value keys
`ess.title`, `ess.stage`, `ess.description` and action key `ess.request-review`.
Keys name registrations, not field expressions, guessed setters or routes.
The application supplies canonical typed Browser handles plus typed value/action
converters. Generated actions remain inert metadata; capability, authorization,
current resource values/revisions and Operation execution belong to CNCF/application.

Normal ComponentFactory selects the component provider first and factory only
if absent; an invalid selected provider has no fallback. It admits the entire
static vector, waits for View/application registration, and atomically joins
typed bindings before exposing usable Display. The
[bootstrap acceptance spec](../../../cloud-native-component-framework-phase96/src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala)
proves normal discovery, equivalent List/Detail models, context/order/Unicode,
component isolation, invalid actual-derived vectors and failed last-binding
publication without a Display prefix. GC2 compares semantic models and asserts
provenance separately; it does not equate entire actual/handwritten Definitions.

## Compatibility and diagnosis

Follow the [existing-v1 compatibility index](../spec/logical-ui-generated-producer.md#compatibility-and-evolution-navigation--existing-v1)
and [owner/proof design](../design/logical-ui-generated-producer.md#versioning-and-proof).
ABI `cncf.logical-ui-generated.v1` / integer `1` in `org.goldenport.cncf.display`
has no SemVer/range/negotiation. The whole exact Phase 74 descriptor is separate.
Static List/Detail admits five roles and five kinds only, with explicit keys,
ordered declarations and scoped uniqueness. Future additive vocabulary needs
explicit consumer ABI/spec/admission and executable proof before emission;
neither a new opaque definition/provenance revision nor this guide admits it.
No v2, automatic backward compatibility, tolerance or fallback is selected.

Use structured code/path/reason to locate a failure, retaining the first-error
order in the specs. The following navigation uses existing codes only:

| Boundary | Existing code and next contract location |
| --- | --- |
| CML hierarchy/property/type or unsupported input | `LUI76_CML_INVALID`, LUI76-S03/S04 |
| Missing/ambiguous component or unsafe Scala namespace/stem | `LUI76_COMPONENT_INVALID`, LUI76-S03/S05 |
| Empty present definitions or invalid element shape | `LUI76_DEFINITIONS_MISSING`, `LUI76_ELEMENT_INVALID`, LUI76-S04 |
| IDs/text/provenance | `LUI76_ID_INVALID`, `LUI76_TEXT_INVALID`, `LUI76_PROVENANCE_INVALID`, LUI76-S04 |
| Kind/role, duplicate scope or List target | `LUI76_VALUE_KIND_INVALID`, `LUI76_ROLE_INVALID`, `LUI76_DUPLICATE_ID`, `LUI76_DUPLICATE_ROLE`, `LUI76_DETAIL_TARGET_INVALID`, LUI76-S02/S04 |
| Consumer ABI/full descriptor mismatch | `LUI96_ABI_INCOMPATIBLE`, `LUI96_CONTRACT_INCOMPATIBLE`, LUI96-S01/S05 |
| Consumer empty/element/ID/text/provenance rejection | `LUI96_DEFINITIONS_MISSING`, `LUI96_ELEMENT_INVALID`, `LUI96_ID_INVALID`, `LUI96_TEXT_INVALID`, `LUI96_PROVENANCE_INVALID`, LUI96-S05 |
| Consumer kind/role, duplicate scope or target | `LUI96_VALUE_KIND_INVALID`, `LUI96_ROLE_INVALID`, `LUI96_DUPLICATE_ID`, `LUI96_DUPLICATE_ROLE`, `LUI96_DETAIL_TARGET_INVALID`, LUI96-S03..S05 |

A static ABI rejection differs from a later missing/incompatible application
binding. Inspect application registration and the exact diagnostic from the
normal bootstrap spec rather than adding inferred bindings or tolerant fallback.
Neither failure boundary permits partially emitted source or Display publication.

Phase 74 owns semantics, Cozy owns deterministic compiler/source, CNCF owns
ABI/admission/runtime and the application owns component/factory and bindings.
Declared provenance != definition revision != receipt Git commit != runtime
Entity/source revision != Display wire schema/version. Generated v1 contains
no editable/constraint/Entity/mutation metadata, runtime CML parsing, shared
Cozy binary, generated runtime Display, endpoint/widget/Flutter dependency or
automatic inverse write.

## Three handoff receipts

| Handoff | Durable navigation and what it establishes |
| --- | --- |
| LUI-ABI-01 | [Producer contract journal](../journal/2026/10/2026-10-04-phase-76-producer-contract.md) and [CNCF ABI receipt](../../../cloud-native-component-framework-phase96/docs/journal/2026/10/2026-10-04-phase-96-logical-ui-abi-handoff.md): fixed source/descriptor/provider/diagnostic boundary and handwritten expected-fixture proof |
| LUI-GEN-01 | [Generated handoff](../journal/2026/10/2026-10-04-phase-76-generated-handoff.md): actual two ordinary CLI runs, exact delivery and focused 254-test producer evidence |
| LUI-ACCEPT-01 | [Reciprocal consumer receipt](../journal/2026/10/2026-10-04-phase-76-consumer-acceptance.md) and [CNCF actual consumer journal](../../../cloud-native-component-framework-phase96/docs/journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md): same files compile/admit/project, 140 focused tests, exact commits and distinct provenance |

Accepted producer delivery is `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`,
consumer acceptance `bef59e729834addd3ac00c1930034bb23a8b428f`, reciprocal
P76-05 document commit `3546755fc3f52606cfb1f209434a2a776ef9358b`.
Source coordinates are `org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT` /
Scala 2.12.18 and `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT` /
Scala 3.3.8. Actual provenance is `cozy.logical-ui-generated.v1` / `ess2-01` /
`ess2-01-cml-1`, definition revision `ess-definition-1`, View `ess.reference`.
These are development evidence and source coordinates, not publication claims.
