# Generation Compatibility Contract

The Cozy generation boundary owns admission of the exact pair used to generate
Scala source. The pair is independent of CAR runtime activation:

- the CNCF coordinate is the exact target used to compile generated Scala;
- the Cozy coordinate is the exact generator executing generation; and
- `packaging.car.runtime.cncf` is an independent runtime compatibility range.

Admission is typed and evidence-based for immutable coordinates. The
machine-readable `cozy.generation-compatibility.v1` evidence is persistent
immutable-only: every evidence pair must use immutable coordinates, and a
mutable or SNAPSHOT entry is malformed. An immutable pair is `Supported` only
when evidence identifies the exact coordinates and marks the pair `proven`. A
known pair marked `unproven` is `Incompatible`, as is a pair marked
`incompatible`; an absent coordinate is `Unsupported`, while individually known
coordinates without a pair record are an incompatible pair. CNCF-only,
Cozy-only, and both-unsupported diagnostics remain distinct.
Version-number equality or similarity never supplies proof. The current
checked-in `0.5.1`/`0.3.0` observation is explicitly unproven.

Project contract, owning-build bridge, and CLI are explicit sources. Every
present explicit source must agree; disagreement returns a typed contradiction
diagnostic. When they agree, provenance is selected by project contract,
owning-build bridge, then CLI. The published default is non-explicit fallback,
used only when no explicit source exists and ignored whenever one does, even
when its value differs. Environment variables and stale fallbacks are not
release-generation sources. An explicit SNAPSHOT pair is admitted for
development without persistent-pair registration, while release generation
requires an immutable evidence-proven pair. The executing Cozy version must
equal the selected exact generator coordinate; provenance plus compile and test
results supplies development evidence. The resource loader validates the exact
schema, owner/location, coordinate fields, immutable-only evidence pairs, pair
uniqueness, and status semantics before admission; malformed evidence cannot
produce `Supported`.

For sbt-cozy runtime selection, the CNCF compile version is authoritative when
no Cozy override is declared. The launcher runtime catalog projects only
proven compatible pairs from Cozy generation evidence and selects the highest
numeric Cozy version for that CNCF version. This selector is distinct from the
global latest Cozy runtime and from the single published default pair. Explicit
project or build Cozy versions and an explicit development checkout override
that default; contradictory explicit versions fail. The exact selected version
is still required at the generation and package admission boundaries.

Evidence owner and location are returned with every admission decision. Cozy is
the generation owner, and Cozy is not a CAR runtime dependency. CV-02 owns
pure admission of missing or unsupported coordinates, unproven or incompatible
pairs, source contradiction, and development/release lifecycle. CV-03 owns
resolving and wiring explicit inputs at the actual generation invocation,
including rejecting absent or contradictory invocation sources. CV-04 owns
runtime descriptor path/target/schema and catalog validation only.

CV-06A makes the unmerged CAR `project.yaml` the package-gate authority for
an optional `build.cozyVersion`, the unique CNCF compile coordinate, and the independent
runtime range. `CarMetadataCompatibility` returns typed diagnostics for missing
or contradictory project values, while `CozyArchivePackager` requires exactly
one resolved CNCF JAR descriptor whose runtime/module/root-version identity
matches the compile dependency. The accepted exact version drives runtime
range admission without consulting merged operation defaults a second time.

CV-06B splits that evaluator into a project-only decision and the package-only
resolved-artifact extension. Integrated CAR lint and the Review Provider expose
the project-only decision with the same diagnostic codes. Publication runs the
same decision before repository writes and projects catalog runtime metadata
from its accepted contract rather than merged operation defaults. Resolved-JAR
identity remains owned by package admission.

CV-07 makes lifecycle a property of the actual generation or publication
boundary. A CAR command derives its output lifecycle from the project component
version, and `publish-car --version` must equal that version before repository
writes. `car-sbt-project` requires its explicit output version, while CNCF
value generation uses the selected CNCF target as its output version. Mutable
generation pairs are admitted only for mutable output; immutable generated
output requires a proven immutable pair and valid provenance.

For CAR/SAR sbt builds, unmerged `project.yaml` must own exactly one CNCF
compile dependency. An optional exact Cozy override may be in the project or
owning build; otherwise the launcher resolves the newest proven generator for
that CNCF target. sbt-cozy does not substitute its own plugin version or an
ambient generator. When the selected generator cannot be launched, the failure
names its CNCF or Cozy coordinate and gives the publish/select recovery action. Exact CAR dependency
archives are resolved independently of source-project builds.

CV-06C1 carries generation metadata into the CAR as top-level
`generation-provenance.json`. Package admission validates known versions and
canonical declarations, actual selected source readability and generated Scala
path inventory, and the accepted CNCF/Cozy pair. It validates the actual selected
`target/cozy/generation-provenance.json` path and packages that path directly
as the CAR's top-level metadata. Invalid metadata or contradictory inputs fail before
archive writing. Nongenerated and legacy provenance absence remains allowed,
and `src/main/car/generation-provenance.json` remains reserved.

Cozy owns both existing provenance schemas and their validation. Direct isolated
generation writes v1 with `schemaVersion`, `target{cncfVersion}`,
`generator{cozyVersion,simpleModelerBackendVersion,simpleModelingModelVersion}`,
`source{identity}`, and `output{artifacts:[{path}]}`. The aggregate writes v2 with
`sources:[{identity,output:{artifacts:[{path}]}}]` in place of `source`.
Both emit only these known fields. Ordinary JSON loading ignores unknown extras
of any type at every object level; it retains strict known fields and types,
nonempty exact versions, canonical safe relative source/output paths, unique
ordered claims, and v2 source/output union. Historical extras supply no content
claim, special decoder, or compatibility hash adapter.

The selected source is `SourceInput(path, identity)`, a normalized actual readable
regular-file reference. The producer checks access by opening and closing the
stream without capturing content or time. The modeler and CML metadata read the
actual selected path directly. The provenance stage checks its source identity
and readability independently. Generated Scala enumeration follows the existing
`target`/delegate-exclusion rules and opens/closes each selected regular file.
Validation compares only declared and actual path inventories. Readable source
and generated content changes alone are valid; missing, added, removed, or
unreadable required paths remain genuine failures.

The aggregate accepts a nonempty explicit list of delegated manifest/output-root
pairs, validates every v1 pair, rejects duplicate canonical source identities
and target/generator disagreement, and verifies the final installed path set
excluding delegated work. It sorts sources by identity and records the sorted
path union. Shared output paths deduplicate independently of different readable
contents. No order-based source winner or replacement ownership certificate is
introduced. Empty or malformed inputs, unsafe paths, genuine access failures,
missing evidence, and contradictory source/top-level unions remain typed
failures. Stable source/output-tampered codes describe actual access and path
inventory failures; hash-only evidence-tamper and output-content-conflict checks
are retired.

Both producers validate their stage before creating/replacing final metadata,
then validate temporary JSON and publish by atomic replacement with temporary
cleanup. Failure preserves previous successful metadata. CLI preflight also
preserves that metadata by removing eager deletion. The backend's source output
installation is not redesigned into a new transaction. Packaged-only validation
uses `requireValidPackagedMetadata` and aggregate `validatePackagedMetadata`
without local source access or digest calculations.

Historically, Phase 63 froze and implemented the Cozy aggregate/rebind API, preserving the
legacy single-rebind API as a wrapper or one-source aggregate equivalent. It
does not change CozySbtBridge request grammar, sbt-cozy collection,
generated-side installation, incremental state, plugin behavior, or a
downstream driver. Those transport/orchestration responsibilities were assigned
to Phase 63.1, whose owning build may invoke the settled Cozy API, delete
delegate work after successful rebind, and require an installed final manifest
before incremental reuse.

The packaged provenance remains metadata. CV-06C2 separately owns CNCF runtime
range, canonical coordinate and ABI admission without loading Cozy or using
generator compatibility/provenance to decide activation.

The edited Cozy producer writes top-level `car-runtime-manifest.json` with
unchanged schema `cncf.car-runtime-manifest.v1`, CAR name/version/component and
accepted minimum, optional maximum, excluded and tested metadata after staging
the selected package. It no longer builds an archive SHA inventory. The
reserved runtime document cannot originate from `src/main/car`. Cozy's selected
prebuilt admission retains canonical descriptor/ABI format v2 and component
namespace/id/version/export/ABI version1, runtime metadata, required packaged
provenance and actual ZIP safe-path/duplicate/read semantics.

S43 primary producer/reader is implemented-focused-validated-unreviewed after
actual Cozy and CNCF own-root focused validation, including native canonical
packed/expanded repository discovery/factory loading/materialization. The primary
reader no longer requires archive SHA inventory/file-set/digest admission.
Canonical descriptor/release coordinate, runtime range and ABI format v2/export/
ABI version1 remain. Whole E715-03 remains OPEN for residual fixtures and
independent assurance. CNCF treats provenance as opaque metadata and never links
Cozy classes. Existing extraction owns required main JAR, safe/duplicate ZIP
paths and I/O semantics. Cozy required provenance and project inputs remain.

The descriptor contract is selected only by `--cncf-runtime-descriptor`, with
an exact `--cncf-version` and selected path. CLI preflight and the sbt bridge
share the validator before source generation. One read supplies parsed config
for root schema `1`, runtime `cncf`, exact target and predefined Result schema
`cncf.predefined-result.v1`, and for the semantic catalog consumers.
`ValidatedDescriptor` contains only path, targetVersion and config. Both public
validation entry points take path, targetVersion and source.

Diagnostics retain typed code, source, expected, actual, message and corrective
action. Missing, unreadable and malformed inputs remain distinct. The bridge
preserves `runtime.cncf.descriptor`; explicit project, build and request values
must agree for the target and selected path. Ambient global defaults do not
participate. Descriptor SHA API fields/options/settings and pin checks are
removed together with their strict caller handoff, without an adapter.
Version-only commands remain outside this descriptor contract.

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

The parsed config belongs to the current invocation; it is not a certificate of
unchanged file bytes. Existing predefined Result and ComponentStyle semantics
consume it directly. CNCF-aware generation still requires canonical
`--generation-source-identity`; the bridge derives it only relative to explicit
`sbt.project_dir` or receives `generation.source.identity` from the owning build.

CV-05A owns the nonhash producer and validator. The metadata CLI consumes the
actual model/output paths, exact target and generator, source identity and
optional model version, with compiled backend/default model versions. It checks
known inputs and actual source/output access, then prints the successful path.
Descriptor/source digest arguments are not metadata-validator inputs.
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
Current source/product edits are handed to the parent for focused validation
and independent review; dated S40 failure remains history with zero Textus
suites. E715-01 validation/review, E715-03..05, Step/Phase acceptance, exact
commits and distinct release remain OPEN.

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
