# Generation Compatibility Specification

Given exact Maven coordinates, explicit source values, lifecycle, and typed
compatibility evidence, When Cozy resolves and admits generation, Then it
returns a structured `Supported`, `Unsupported`, or `Incompatible` result with
diagnostics and evidence owner/location.

The evidence shape is `cozy.generation-compatibility.v1` with exactly
`schema`, `evidenceOwner`, `pairs`, and `publishedDefault` fields. Each pair
contains structured exact immutable CNCF and Cozy coordinates and one of
`proven`, `unproven`, or `incompatible` statuses. Persistent evidence is
immutable-only: a mutable or SNAPSHOT entry is malformed. `publishedDefault`
is either `null` or one exact immutable pair coordinate already present in
`pairs` with `proven` status; it is used only when no project, owning-build, or
CLI source selects a pair. The packaged
0.5.1/0.3.0 pair is `unproven`. Runtime minimum/maximum/excluded/tested values
are separate CAR metadata and are not used to infer generation compatibility.

The production loader validates schema version, exact field sets, non-empty
owner/location and coordinates, duplicate/conflicting pair records, and status
semantics, including immutable-only persistent coordinates. It returns typed deterministic diagnostics with source/expected/
actual/coordinate context. The admission API distinguishes missing CNCF,
missing Cozy, both missing, unsupported coordinates, unsupported pairs, known
unproven pairs, and explicitly incompatible pairs.

For a CAR project, the unmerged `project.yaml` must supply exactly one CNCF
compile dependency and a runtime
compatibility declaration with `minimum` and a non-empty `tested` set. The
compile target must be within the optional maximum, absent from `excluded`, and
present in `tested`.

`build.cozyVersion` is an optional exact generator override. The owning SBT
build may also set an exact Cozy version; conflicting explicit sources fail.
An explicit project-local development checkout is an alternative override and
must identify its exact version before execution. When no override exists,
the launcher selects the highest version among active, proven Cozy runtimes
listed for the exact CNCF compile target in the runtime catalog. Compare
numeric version segments, not publication time or lexical strings. An absent
mapping fails closed; an unproven or merely published Cozy runtime is not a
candidate. Mutable/SNAPSHOT generation requires an explicit version or
checkout when no proven default exists. The resolved exact generator version
must be bound to generation and package evidence; a changed resolution cannot
reuse stale generated output.

Package admission must resolve exactly one CNCF descriptor from the actual
input JARs. Its runtime must be `cncf`; its module organization, artifact, and
version plus its root descriptor version must identify the exact compile
dependency. Operation defaults must not replace project-owned compatibility
values or resolved-artifact evidence. Every contradiction must fail with a
typed deterministic diagnostic before archive acceptance.

Review and publication must reuse the project-only portion of this decision.
Integrated CAR lint and the Review Provider must report an accepted contract
or the same typed project diagnostic as packaging. Publication must reject an
invalid declared CAR before repository output and must derive the accepted
catalog runtime range from that contract, not merged operation defaults.
Resolved-JAR identity remains a package-only requirement.

An actual CAR generation or publication command must derive lifecycle from the
project component version. `publish-car --version` must equal that exact
version. `car-sbt-project` must provide its output version explicitly, and
CNCF value generation must use its selected CNCF target as output version.
Mutable generation coordinates must not produce immutable output.

An explicit mutable SNAPSHOT pair is admitted for development without
pre-registration in persistent evidence; the exact executing Cozy version must
equal the selected generator coordinate. Its provenance together with compile
and test results supplies development evidence. Release admission remains
immutable and requires an exact evidence-proven pair; version similarity and
fallback inference never supply proof.

For development, `TEXTUS_SIMPLEMODELER_CNCF_SOURCE_ROOT` explicitly selects
the SimpleModeler source build used to prepare Cozy. It selects a build input,
not the CNCF generation target, executing Cozy coordinate, or compatibility
proof. Unset selection preserves the published backend and the existing
`simplemodeler.version` system property, then `SIMPLEMODELER_VERSION`, then
`1.1.26-SNAPSHOT` precedence. A selected checkout must have an absolute,
canonical directory, `build.sbt`, and
`src/main/scala/org/simplemodeling/SimpleModeler/SimpleModeler.scala` within
that directory. Blank, relative, uncanonicalizable, incomplete, or overlapping
selections fail before generation without artifact fallback. Source and Cozy
roots must be disjoint, except for a direct normalized absolute lexical and
canonical Cozy `.codex-workflow/checkouts/` child with an ASCII leaf matching
`[A-Za-z0-9_][A-Za-z0-9_.-]*`; a lexical contained path cannot escape through
symlinks.

The loaded source project's actual organization and module must be
`org.simplemodeling` and `simplemodeler`. Its actual version must equal the
owning Cozy backend version, and its actual Scala version must equal Cozy's
loaded Scala version (`1.1.26-SNAPSHOT` and `2.12.18` for the current default
development build). These are settings-initialization checks, not source-text
inference. Selected mode uses a source project dependency for compile/runtime
and excludes the published `simplemodeler_2.12` implementation, including
transitive copies. Unset mode adds no backend exclusion. Before invoking the
Cozy CLI development launcher, the producer must prepare
`cozyExportRuntimeClasspath`; the running generator must have exactly one
class resource owned by the selected source's `target` output. Provenance,
actual generated-source compilation, and specifications remain required to
establish development compatibility. The directory itself supplies no proof.
The executable selection and class-origin contract is
[`CozySimpleModelerDevelopmentBuildSpec`](../../src/test/scala/cozy/modeler/CozySimpleModelerDevelopmentBuildSpec.scala).

For CAR/SAR generation, sbt-cozy must reject a project missing its CNCF
compile dependency or declaring more
than one CNCF compile dependency declaration. Equivalent aliases or duplicate
declarations of the same version remain multiple inputs and must be rejected,
not collapsed. An explicit Cozy coordinate must be passed to the delegate and
incremental state. Without one, the exact CNCF coordinate must reach the
launcher for catalog selection and generated output must not be reused before
the resolved Cozy coordinate is known. An unavailable delegate must identify
the exact requested CNCF or Cozy coordinate and the recovery action. Resolving an exact CAR dependency archive
must not require loading, generating, or publishing that dependency's source
project. A flat SBT test runtime may extract the CAR's implementation JARs and
resolve only its `component-dependencies.yaml` `dependencies.local`
coordinates; `shared` and `provided` dependencies retain their runtime
ownership.

When `target/cozy/generation-provenance.json` exists, package admission validates
its known versions, canonical source identities, actual selected source
readability, and actual generated Scala path inventory against the accepted
project contract. Direct `cozy.generation-provenance.v1` and project aggregate
`cozy.generation-provenance.v2` remain supported at the existing path.
A successful package validates and packages the actual selected metadata path
directly as the CAR's top-level `generation-provenance.json`. Validation
or a project-contract mismatch fails before archive writing. A generated immutable
release requires provenance; absence remains permitted for nongenerated and legacy
CAR sources. The generic source
path `src/main/car/generation-provenance.json` is reserved and rejected.

Both schemas contain exactly the emitted known projection:

- `schemaVersion` identifies v1 or v2;
- `target` contains `cncfVersion`;
- `generator` contains `cozyVersion`, `simpleModelerBackendVersion`, and
  `simpleModelingModelVersion`;
- v1 `source` contains `identity`, while v2 `sources` is a sorted nonempty
  unique list of `{identity, output:{artifacts:[{path}]}}`;
- `output` contains a nonempty sorted unique `artifacts:[{path}]` inventory.

Ordinary object loading ignores arbitrary extra fields and values at every
object level, including historical hash properties, and never re-emits them.
Known required fields and object/array-entry types remain strict. Missing or
unsupported schema, malformed known fields, empty selected versions, unsafe or
noncanonical paths, duplicate claims, and an unequal v2 source/top-level output
union fail with typed diagnostics. No hash-specific migration codec is used.

`SourceInput(path, identity)` is a normalized selected readable regular source
reference. Source selection remains explicit. The source is opened and closed
to surface actual access failures without capturing bytes, a timestamp, or an
immutability certificate. Generation and CML model metadata read that actual
source directly. The provenance write independently requires source readability
and agreement with the selected canonical identity at its own stage. Readable
source or output contents may change without a historical-content rejection.

Direct generation enumerates regular Scala paths under `target`, opens and
closes each selected file, and records the sorted relative path inventory.
Validation requires the same actual inventory; missing, added, removed, or
unreadable required output fails. Source/output contents are not compared.
Versions remain exact normalized nonempty values, and logical source/artifact
paths must be canonical relative paths without absolute, Windows-drive,
parent-escaping, or noncanonical claims.

The aggregate operation alone writes v2. It receives a nonempty explicit list
of `(delegated manifest path, delegated output root)` pairs. Every selected v1
pair is validated against its actual source and delegated output, and all pairs
must share the exact target and generator versions. Canonical source identities
must be unique and are sorted. The final installed Scala inventory is validated
excluding the selected delegate work roots. Shared output paths contribute one
sorted path claim regardless of differing readable contents; no source wins by
order and no content or ownership certificate replaces the removed hashes.
Filesystem enumeration, input position, hashes, and timestamps do not select a
source. A single accepted delegated v1 pair may produce v2, while existing
one-source v1 package admission remains supported.

A missing or unreadable delegated manifest/output root uses
`GENERATION_PROVENANCE_MISSING`; malformed known metadata uses
`GENERATION_PROVENANCE_MALFORMED`; required-source access and actual path
inventory failures retain `GENERATION_PROVENANCE_SOURCE_TAMPERED` and
`GENERATION_PROVENANCE_OUTPUT_TAMPERED` as stable diagnostic codes with text
that describes those access/path failures. Exact-input disagreement uses
`GENERATION_PROVENANCE_INPUT_MISMATCH`; duplicate or ambiguous source identity
uses `GENERATION_PROVENANCE_SOURCE_AMBIGUOUS`. Hash-only evidence-tamper and
output-content-conflict checks are removed.

Both producers complete their stage validation before creating or replacing
the final metadata document. They serialize temporary JSON, validate that
projection, atomically replace the final path, and clean temporary files on
failure. Previous successful metadata survives failed stage validation, and CLI
preflight does not eagerly delete it. This is metadata-output and preflight
failure preservation, with no new transactional backend source installer.
`requireValidPackagedMetadata` and aggregate `validatePackagedMetadata` validate
known schema, versions, canonical source/output declarations, and v2 union
without project source access or digest calculation.

Cozy owns this aggregate/rebind API, including a supported legacy single
rebind wrapper or one-source aggregate equivalent. Phase 63 does not change
CozySbtBridge request grammar, sbt-cozy manifest collection, generated-side
output installation, incremental behavior, plugin integration, or downstream
driver behavior. Those orchestration concerns remain exclusively Phase 63.1;
when it begins, sbt-cozy may call the settled Cozy API and remove delegate work
only after successful rebinding and installed-manifest verification.

Runtime activation must not import Cozy or evaluate generation provenance.
CNCF runtime range, canonical coordinate and ABI retain their independent
CV-06C2 meanings. The S43 primary producer/reader contract is
implemented-focused-validated-unreviewed after the actual Cozy and CNCF
own-root focused validations. Whole E715-03 remains OPEN for residual fixtures
and independent assurance.

For an accepted CAR project contract, Cozy creates top-level
`car-runtime-manifest.json` with unchanged schema `cncf.car-runtime-manifest.v1`,
CAR name/version/component and accepted CNCF minimum, optional maximum, excluded
and tested values. It emits no archive SHA inventory. Cozy rejects
`src/main/car/car-runtime-manifest.json` as a reserved source-managed path.

Cozy prebuilt-CAR admission and CNCF runtime admission preserve required
manifest, canonical descriptor/release coordinate, runtime metadata and ABI
format `cozy.car.abi-manifest.v2` with canonical component namespace/id/version,
export and ABI version `1`. Unsafe/duplicate ZIP paths, actual required main JAR
and ordinary required-file/read/format/semantic failures remain. Tested versions
remain nonempty evidence rather than a runtime allowlist. CNCF never interprets
provenance or loads Cozy. Archive inventory/file-set/digest admission is removed
from the primary producer/reader together.

Cozy publication admits and publishes the current selected `--car` path
directly. A generated immutable release requires packaged generation provenance
and validates the actual selected `target/cozy/generation-provenance.json`
against the owning project's readable selected source, actual generated Scala
path inventory and exact CNCF/Cozy pair. Ordinary missing, malformed and
contradictory inputs remain failures. Actual focused package/publication/
provenance and canonical packed/expanded runtime discovery evidence is recorded
below; residual fixtures and independent reviews remain OPEN.

CV-03 owns CNCF generation invocation wiring and rejects absent or contradictory
invocation sources before generation. CV-04 owns descriptor path, target, schema
and catalog validation. A generation invocation selects this contract only with
`--cncf-runtime-descriptor`; it then requires `--cncf-version` and the selected
descriptor path. Version-only commands remain outside the descriptor contract.
Before source emission, Cozy rejects missing, unreadable or malformed inputs,
requires root schema `1`, runtime `cncf`, an exact target version and predefined
Result schema `cncf.predefined-result.v1`.

`ValidatedDescriptor(path, targetVersion, config)` retains the normalized path,
selected target and parsed config. `requireValidDescriptor(path, targetVersion,
source)` and `validateDescriptor(path, targetVersion, source)` read bytes once
and parse that value. The predefined Result and ComponentStyle consumers use
that config; changing the file later does not change the current parsed value.
A new invocation reads its selected current input normally.

CLI and sbt-bridge entry points share deterministic diagnostics with code,
source, expected, actual, message and correctiveAction. Repeated or cross-source
target/path values must agree exactly; contradictions fail before delegated
generation. Global `~/.cozy` operation defaults are excluded from generation
resolution. The bridge forwards only `runtime.cncf.descriptor` alongside the
selected exact versions. No descriptor SHA parameter, setting, CLI option,
syntax/mismatch diagnostic or legacy adapter remains in this contract.

sbt-cozy selects the fixed `META-INF/cncf/runtime.yaml` resource from the actual
`Compile / dependencyClasspath` entries, accepting a JAR or a dependency class
directory. Its `extract(classpath, target)` and `extractRequired(classpath,
target)` APIs return `Option[File]` and `File`; the selected output contains the
exact resource bytes. Distinct dependency owners are ambiguous even when their
bytes coincide. Missing required resources and ordinary read/write failures
remain failures. There is no ambient filesystem search.

The optional ComponentStyle carrier retains schema
`cncf.component-style-catalog-carrier.v1` and exactly `schemaVersion`, `resource`,
`encoding`, and `bytes`. Resource identity is
`META-INF/cncf/component-style-catalog.json`, encoding is `base64`, and the
payload is the actual catalog JSON. Unknown fields, missing/invalid payload,
unsupported schema/resource/encoding and malformed catalog remain invalid.
Catalog API/kind/provider, canonical ASCII identities and signed-Int majors,
closed empty parameters, bundle closure/cycles/conflicts and qualified style
selection keep their existing semantics. No descriptor or payload digest is
required, emitted, or compared, and the schema is unchanged.

For selected CNCF-aware generation, successful Scala emission writes the v1
nonhash projection above after model metadata production. Provenance retains
its independent exact versions, source identity, actual required paths and
ordinary access diagnostics; it records no descriptor digest.

Cold and repeated generation with the same selected versions, canonical source
identity, and generated path inventory emits identical nonhash metadata across
output roots. Absolute paths, timestamps, traversal order, source/output bytes,
and local hash fields do not enter that metadata. Genuine required-source and
output access failures remain typed diagnostics.

CV-05A owns the Cozy producer and validator. The direct CLI is:

```text
generation-provenance-validate <model-file> --save <generation-output-root> --cncf-version <version> --cozy-generator-version <version> --generation-source-identity <project-relative-path> [--simplemodeling-model-version <version>]
```

The command compares the selected CNCF and Cozy versions, compiled backend
version, selected model version (defaulting to the current compiled model
version), and source identity, then validates actual source access and output
paths. It requires no source or descriptor hash argument and prints only the
successful metadata path. It does not replace descriptor preflight validation.

CV-05B owns the CNCF build consumer. Its selected input value contains only
cozyGeneratorVersion, cncfTargetVersion, runtimeDescriptor, source and
sourceIdentity. Exact normalized versions, required descriptor/project/source,
source containment and current source/output availability remain required.
Generation uses the selected descriptor path and target, produces the ordinary
Information model and requires nonempty sorted Scala output. It then invokes
the actual Cozy metadata validator with current source/output paths, exact
versions and canonical project-relative source identity. Source-SHA storage,
pre-launch source drift comparison and the protection-only generated/provenance
SHA snapshot model/task/test hook are removed together. Pure invocation/value
determinism and nonhash metadata reproducibility retain their meanings.
S41 descriptor/style validation retains its dated attribution. S42 actual
production Information generation/current metadata validation passed with
two suites/eight tests and 34 generated Scala paths. S43 primary runtime
producer/reader is implemented-focused-validated-unreviewed after actual focused
Cozy/CNCF validation. E715-03 residual fixtures,
composition E715-04, provider E715-05 and warning dispositions remain OPEN.

## 2026-10-09 S20 Media, warehouse and provenance direct output amendment

Initial implementation handoff (2026-10-09):
P715-02-S20-MEDIA-WAREHOUSE-PROVENANCE-DIRECT was
**implemented-unvalidated-unreviewed** under the
[frozen S20 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s20-implementation-manifest.json).
At that initial handoff, the parent-selected ten existing suites, independent
review and acceptance were pending. This amendment supersedes earlier publication/output content-equality,
warehouse no-overwrite/Reused and provenance temporary/readback/atomic/cleanup
and I/O-failure-preservation guarantees in these selected contracts. Existing
dated decisions, validation, refusals, repairs and acceptance records remain history.

Media verification checks publication existence without comparing its contents
to the selected output. Ordinary source/output availability, PNG signature and
dimensions, presentation/summary/PDF structure, required receipts, explicit
alignment and credits retain their existing semantics.

Warehouse placement retains its initial whole-batch descriptor/schema,
identity/role/locale/MIME/extension, canonical source/public coordinate,
unique-destination and direct-path/symlink/nonregular admission. Explicit root,
profile and rootEnv precedence remain. Absent sources report Unavailable.
Available resources are Planned during dry-run, which writes nothing. Real
placement copies each available selected source in the existing order directly
with overwrite and reports Created after successful copying, including existing
destinations. No content-dependent reuse or differing-edition rejection remains.
An ordinary copy IOException reports no success and may leave earlier or partial
output; placement provides no rollback or prior-output preservation guarantee.

The v1/v2 provenance producers complete initial own-stage validation before
writing final metadata. SourceInput remains the selected readable source/path
identity API; exact normalized versions, canonical identities, actual readable
sorted Scala inventory, nonempty output, delegate exclusions and v2 source/path
union retain their meanings. Each selected delegated v1 manifest is parsed once
and passed directly to the existing v1 validator; actual source/output access and
metadata validation remain. The aggregate retains its one initial installed-output
validation and writes the normalized selected manifest. Both producers create
the final parent and serialize/write directly to the final metadata path,
without temporary metadata, serialized readback, atomic installation or cleanup.
Invalid pre-write inputs fail before the final write, leaving previous metadata
untouched. Once I/O starts, failures propagate and may leave partial metadata;
previous final output is not protected. The existing v1/v2 schemas and packaged
metadata validators remain. SourceTampered/OutputTampered retain the stable
`GENERATION_PROVENANCE_SOURCE_TAMPERED` and
`GENERATION_PROVENANCE_OUTPUT_TAMPERED` codes for genuine access/path/inventory
failures, without a content guard.

The SourceInput API, descriptor-SHA option/contract and CAR packaging byte-transfer
and protection contracts are outside S20. Original distribution/runtime removal
and concrete E715-01..04 external-reader reconciliation remain OPEN under their
separate authority; S20 grants no compatibility exception or closure.
The [S19 actual focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-focused-validation-result.json)
records dated evidence of 102 passing tests in 8 completed suites. Its three
existing runtime Logback duplicate-resource warnings remain
`HYG-P715-S19-LOGBACK-001`, pending independent Step classification. This is no
S20 validation result. S19 and other native evidence remain dated; current final
JA34 generation/assembly, audible hearing, end-card and EOF obligations remain
OPEN. Parent assessment of the actual S20 changes and existing real generation,
Video and media consumers determines any affected native rerun.
Remaining finite work, mandatory independent M2 cycles 6/7/8/10, whole-Step and
full-Phase reviews, acceptance, exact local commits and distinct release remain
OPEN. Repository-full SBT and broader integration remain deferred-not-run to
PHASE-71.7. Stage Status triples and checklist checkboxes remain unchanged;
no Step, Phase or per-Slice acceptance, review or commit is claimed.

### Actual S20 focused validation

Current factual state on 2026-10-09:
**implemented-focused-evidence-retained-validation-selection-unresolved-unreviewed**.
This actual result supersedes the initial implemented-unvalidated handoff status
above while preserving that dated handoff and its implementation/GWT record.
The [actual serialized S20 result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s20-focused-validation-failure01.json)
and [parent factual diagnosis](../../.codex-workflow/phases/PHASE-71.5/p715-02-s20-validation-diagnosis01.json)
retain the execution and unresolved selection facts.

SBT and wrapper exited **0/0**, and the shared serial lock was **released**.
The invocation reported **10 completed suites, 0 aborted**, with **100 passing
tests, 0 failed, 18 canceled, 0 ignored and 0 pending**. The first task group
reported **48 passing / 18 canceled across 6 completed suites**; the second
reported **52 passing / 0 canceled across 4 completed suites**. Eight ordinary
focused suites supplied all **100 successful tests** with no canceled tests.
The remaining **11 CozyMediaWarehouseDeclarationAcceptanceSpec** and
**7 CozyMediaWarehouseUploadSpec** tests were canceled solely because the
integration flag was disabled; their required external roots were also not
selected. Completed-suite counters do not establish acceptance of canceled
tests or complete ten-suite acceptance.

Compiler warnings, runtime warnings, errors and fixture diagnostics are all
**zero for this invocation**. S19's three dated Logback warning lines remain
unchanged under `HYG-P715-S19-LOGBACK-001`, pending independent Step
classification; they are not current S20 warnings.

The parent records **P715-S20-EXTERNAL-INTEGRATION-SELECTION-001** as a validation
selection error: the original full Goal and aggregate authority defer broader
external integration to **PHASE-71.7**, while the S20 selection included these
two gated suites without enabled integration inputs. This is not a product
failure or consumed product repair. The next parent action remains **PLAN
correction pending**: correct selection under that existing authority, retain
the **100 passing / 8 ordinary-suite evidence** and raw **18 canceled / 2-suite
gap**, without repeating passing tests, repairing product code, changing the
repair counter or enabling/selecting external roots. This factual documentation
projection performs none of those actions.

The stated direct Media verification, warehouse placement and provenance source
behavior remains the implemented S20 contract. Finite removal and E715-01..04
external obligations, current final JA34 generation/assembly/hearing/end-card/EOF,
independent M2 cycles **6/7/8/10**, whole-Step/full-Phase review, acceptance,
exact local commits and distinct release remain **OPEN**. Repository-full SBT
and broader integration remain **deferred-not-run to PHASE-71.7**. Phase metadata,
all seven Stage Status triples and checklist checkboxes remain unchanged. No
canceled-test, Step, Phase or per-Slice acceptance is claimed. The editor ran no
validation, self-review, workflow transition or commit for this projection.

## 2026-10-09 S28 API and archive direct output amendment

**P715-02-S28-API-ARCHIVE-DIRECT-OUTPUT** is
currently **implemented-focused-validated-unreviewed** under the
[frozen S28 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s28-implementation-manifest.json).
The initial 2026-10-09 implementation handoff was
**implemented-unvalidated-unreviewed**; that dated status remains history.
API JAR generation keeps descriptor/class/pattern preflight, sorted entries,
manifest, standard ZIP format and resource closure, then writes the selected
final path directly. API dependency selection keeps descriptor, coordinate,
version/path, assembly, required/optional/ambiguity and first-order deduplication
semantics. After selection, the caller-selected generated extraction root passes
one existing non-symlink-directory admission, loses its obsolete generated set,
is created and receives selected JARs directly in order. Empty success leaves an
empty directory. Deletion/copy/write failures propagate and may leave partial
outputs; recovery is regeneration.

CAR packaging validates the actual selected target provenance path and passes
that path into archive writing. Both known schemas, canonical source/generated
path inventory, exact versions/pair, required missing-provenance rejection and
reserved source location remain. Transformed implementation-JAR inputs,
generated descriptor inputs and component-style semantic snapshots retain their
independent meanings. Prior final-output staging/backups/restoration and copied
provenance acceptance snapshots are retired.

At the initial 2026-10-09 handoff, the parent-selected six existing suites were pending:
`cozy.ComponentApiJarPackagerSpec`,
`cozy.ComponentApiDependencyResolverSpec`,
`cozy.archive.CozyArchivePackagerCv06Spec`,
`cozy.archive.Phase56CanonicalCarDescriptorCodecSpec`,
`cozy.CozyArchivePackagerSpec` and `cozy.CozyCarPublisherSpec`.
Existing real failure, output-set, path-admission and package-content clauses
remain; only the protection-only backup-warning scenario is deleted.

Remaining original **R715-09 source/Scaladoc/runtime/carrier/subcomponent** duties
and **E715-01..05 remain OPEN**, including their real required integrity fields;
these are unfinished work, not exceptions. Final current **JA34 generation,
assembly, audible hearing, end-card and EOF**, independent **M2 cycles 6/7/8/10**,
whole-Step/full-Phase review and acceptance, exact local Step commits and the
distinct release remain **OPEN**. Repository-full SBT and broader integration
remain **deferred-not-run to PHASE-71.7**. Original authority/base/epoch, counters,
finite list, aggregate metadata, Stage Status triples, closure checkboxes and
dated validation/review/refusal/native facts retain their state. No Slice, Step
or Phase acceptance is claimed.

### Actual S28 focused validation — 2026-10-09

**P715-02-S28-API-ARCHIVE-DIRECT-OUTPUT** is currently
**implemented-focused-validated-unreviewed**. The
[actual S28 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s28-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s28-parent-edit-admission.json)
record **118 successes across six completed suites**: the first sequential
group had **38 successes in three suites**, followed by **80 successes in
three suites**. Failed, canceled, ignored and pending tests and aborted suites
were all **zero**. Main and Test compilation was implicit in this invocation.
Compiler warnings, runtime Logback warnings, unclassified runtime warnings,
errors and fixture diagnostics were all **zero for this invocation**.
Whole-Phase warning disposition remains subject to independent assurance.

Serial invocation `2aa82b22-408b-4e9e-b4ec-dd230583ca10` completed with
SBT and wrapper exit codes **0/0**, and the shared serial lock was **released**.
The selected API final-write/extraction and copied-provenance-snapshot removal
is focused-validated-unreviewed. The initial 2026-10-09
**implemented-unvalidated-unreviewed** handoff and its scenario/header record
remain dated history. Earlier **S27's 74 successes in six suites** retain their
S27 attribution. Parent edit admission records source inspection; independent
review and acceptance remain OPEN.

The parent assessment identifies only three archive/API owners, with no
narration, frame, synthesis, render, assembly, audio, credit or end-card producer
change. Prior native evidence remains dated; final current **JA34 generation,
assembly, audible hearing, end-card and EOF** remain OPEN. Other original
**R715-09 source/Scaladoc/runtime/carrier/subcomponent** duties and
**E715-01..05 remain OPEN**, including actual required integrity fields/checks
pending separately authorized external-reader reconciliation. Independent
**M2 cycles 6/7/8/10**, whole-Step/full-Phase review and acceptance, exact local
Step commits and the distinct release remain OPEN. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**.

Original authority/base/epoch, repair counters, finite list, aggregate metadata,
Stage Status triples, closure checkboxes and refusal/review/repair/native history
retain their state. This factual projection performs no new validation,
self-review, staging, commit or workflow transition. No Slice, Step, Phase or
native acceptance is claimed.

## 2026-10-09 S41 initial descriptor handoff (historical)

**P715-02-S41-DESCRIPTOR-HANDOFF** is implemented-unvalidated-unreviewed under
the [frozen S41 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s41-implementation-manifest.json).
This current descriptor/carrier amendment supersedes the earlier distributed-pin
contract without altering dated implementation, validation or refusal history.
The parent owns actual focused validation, independent review and acceptance.
E715-01 connected validation/review and E715-03..05 remain OPEN.

## 2026-10-09 S42 initial information generation handoff (historical)

**P715-02-S42-INFORMATION-GENERATION-DIRECT / R715-09 / E715-02 generation**
is **implemented-unvalidated-unreviewed** under the
[frozen S42 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s42-implementation-manifest.json).
CNCF now uses the five selected build inputs and actual generation/metadata
validation without source SHA/drift or the dedicated output/provenance snapshot
model/task/test hook. Existing CV03 retains five cases and normalization PBT;
CV05 retains three cases, including current invocation and outside-project
rejection. Parent-selected production generation and both suites have not run
for S42. Independent review and original Step/Phase acceptance remain OPEN.
All dated S41 and earlier refusal/validation records remain history; runtime
E715-03, composition E715-04 and provider E715-05 remain OPEN.

## S43 packaged runtime partial implementation and scoped denial — 2026-10-09

**P715-02-S43-CAR-RUNTIME-DIRECT / R715-09 / E715-03 primary runtime**
is **partially-implemented-permission-blocked-unvalidated-unreviewed** under the
[frozen S43 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s43-implementation-manifest.json).
Only the Cozy producer and its two existing suites have been edited.
CozyCarRuntimeManifest now writes unchanged v1 schema/car/runtime range
metadata and admits the current selected CAR without archive SHA inventory
comparison. Canonical descriptor/ABI, component knowledge, required packaged
provenance, runtime metadata, unsafe/duplicate ZIP paths and ordinary I/O
semantics remain. Existing runtime and package suites retain their 17 and 11
cases; only their protection expectations and hash helpers were removed.
The generated-version range PBT and actual buildCar/scaffold/provenance
scenarios remain. No new suite, case or replacement protection was added.

The exact five CNCF paths below were submitted in one honest scoped filesystem
escalation under original Phase protection-removal authority and the actual
「外部ライブラリも修正して」 direction. Automatic approval review rejected the
request before execution: it classified removing external CAR SHA/integrity
admission as weakening a security boundary and found the broad external-library
direction insufficiently specific for that removal. No CNCF edit or execution
occurred, and no alternate write or unchanged retry was attempted. The later
Textus-specific informed approval is retained as Textus-specific history and
was not presented as specific CNCF approval.

Denied paths under `/Users/asami/src/dev2025/cloud-native-component-framework`:

- `src/main/scala/org/goldenport/cncf/component/CarRuntimeAdmission.scala`
- `src/test/scala/org/goldenport/cncf/component/CarRuntimeAdmissionSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/testutil/CarArchiveFixture.scala`
- `docs/spec/generation-compatibility-contract.md`
- `docs/design/generation-compatibility-contract.md`

**Whole E715-03 remains OPEN pending specific CNCF approval.** The current
CNCF reader still requires integrity entries/file-set/digests, so this partial
Cozy producer change has no connected compatibility, validation or acceptance
claim. Its focused validation and independent review have not run. The parent
owns admission, any focused validation and the approval/continuation decision.
The complete S43 manifest is not satisfied.

Original residual fixture work remains OPEN: CNCF ComponentRepositoryCarSpec
`_write_packaged_runtime_evidence` / `_write_runtime_classpath`,
ComponentInitializationBootstrapSpec's development writer, and
CozyCarRuntimeManifestSpec `_consumer_contract` carrier resource SHA /
matchingDigest / integrity metadata (original E715-03/E715-01). Composition
E715-04, provider E715-05 and unsuppressed plugin/task/cycle/logging/deprecation
warning classification/repair remain OPEN. Original independent whole-Step and
full-Phase reviews, exact local commits and distinct release remain OPEN.

S40 refusal/source-bound failure history remains actual history. S41's actual
21 suites/181 distinct successful tests and S42's actual two suites/eight tests,
production Information generation/current metadata validation and 34 generated
Scala paths retain attribution. S36 unchanged native/movie/smooth hearing/
end-card/EOF evidence is reused. Original Goal/authority/binding/bases/epoch,
review ledger revision 5, repair 14/streak 0, pending M2 cycles 6/7/8/10/11/13,
seven Stage Status/Owner/Update triples and acceptance checkboxes retain their
state. Full/broader validation remains deferred-not-run to PHASE-71.7.
The worker ran no tests, self-review, Git mutation or workflow transition.

## S43 approved primary runtime actual focused validation — 2026-10-09

The primary producer/reader portion of **P715-02-S43-CAR-RUNTIME-DIRECT /
R715-09 / E715-03** is **implemented-focused-validated-unreviewed**. This
parent-admitted actual result supplements the preserved dated denied, partial
and unvalidated handoffs. The exact informed CNCF approval enabled the single
renewed scoped five-file implementation; the earlier raw request/refusal remains
history and Textus-specific approval was not substituted for CNCF approval.

The [accepted Cozy result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s43-validation-1-result.json) records invocation
`a7703154-ac40-456c-bf5f-ef7d473fa578`, executor session **78070**,
`CozyCarRuntimeManifestSpec` **17 tests** and `CozyArchivePackagerCv06Spec`
**11 tests**: **2 suites / 28 passed tests**. The [accepted CNCF result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s43-validation-2-result.json)
records invocation `dec6e72e-acc1-4cee-b0b6-d0fa0ab41db4`, executor session
**51720** with terminal tool exit **0**, `CarRuntimeAdmissionSpec` **10 tests**
and the actual existing `ComponentRepositoryCarSpec` canonical packed/expanded
repository case **1 test**: **2 suites / 11 passed tests**. That selected native
case actually ran through discovery, factory loading and materialization for
packed and expanded CARs. Both invocations ended with **SBT 0 / wrapper 0 /
serial lock released**. Combined evidence is **4 completed suites / 39 distinct
passed tests**, with failed, aborted, canceled, ignored and pending counts all
**zero**, and no warnings or errors in either invocation.

These are two own-root focused validations, including existing native Cozy
package/publication/provenance behavior and the actual CNCF repository case.
They do not establish broad end-to-end or full integration validation. The
primary contract retains unchanged v1 schema/car/runtime range, canonical ABI
format v2/export/ABI version1, actual required files/main JAR, ZIP safe paths/
duplicates and JSON/I/O failures. Cozy required packaged provenance and selected
project inputs retain their meanings; CNCF never parses provenance or loads Cozy.
No new case, suite, inventory certificate or replacement protection was added.

Whole **E715-03 remains OPEN** for original repository/bootstrap fixture
emissions: `ComponentRepositoryCarSpec` `_write_packaged_runtime_evidence` /
`_write_runtime_classpath` and `ComponentInitializationBootstrapSpec`'s
development writer. Original E715-01 carrier resource metadata in
`CozyCarRuntimeManifestSpec` `_consumer_contract`, E715-04 composition, E715-05
provider and prior unsuppressed plugin/task/cycle/logging/deprecation warning
classification/repair remain **OPEN**. Original independent whole-Step/full-Phase
reviews, acceptance, exact local commits and distinct release remain **OPEN**.

S40 denials/source-bound failure, S41 actual 21 suites/181 tests and S42 actual
two suites/eight tests with production generation/current metadata for 34 Scala
paths retain their attribution. Original Goal/authority/binding/bases/epoch,
review ledger **5**, repair **14 / streak 0**, pending M2 cycles
**6/7/8/10/11/13**, all seven Stage Status/Owner/Update triples and closure
checkboxes retain their state. S36 unchanged native/movie/smooth hearing/end-card/
EOF evidence is reused. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. This documentation editor ran no tests,
self-review, source edit, Git mutation or workflow transition. No Step, Phase
or all-removal acceptance is claimed.
