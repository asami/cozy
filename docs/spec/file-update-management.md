# File Update Management and Digest Boundary

## 2026-10-09 S19 Video publication direct output amendment

P715-02-S19-VIDEO-PUBLICATION-DIRECT is **implemented-focused-validated-unreviewed**
under the [frozen S19 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-implementation-manifest.json).
Independent review and acceptance remain OPEN.
This current amendment supersedes the earlier Video producer observation,
mtime/seal and repeated source-admission promises, destination force/hash reuse,
staged/fsync/atomic/hard-link installation and Video SHA metadata promises below.
The dated decisions and results remain history. Shared publication configuration
and the otherwise unused Project publication config argument no longer carry
`force`; direct `publish-video` and BoK `publish`, `publish-video`,
`publish-projects` and `update-publication` CLI/help omit `--force`.
Publication requests use the existing strict parser; undeclared options fail.
Unrelated bibliography and `force-project-files` behavior remains in its own scope.

Initial package, descriptor, article, scripts/steps, required assets, path
containment and direct non-symlink source admission remain normal input checks.
Optional source absence remains supported. One resolved plan selects the workspace
and final repository path. The actual synthesize/render/build/RDF pipeline,
encoding, narration, providers, voice identity and credits retain their meanings.
Publication metadata keeps actual source/output paths, kinds, versions, catalog,
RDF, captions, transcript and factual size; none proves unchanged source or output.
The main rendered artifact and present optional sidecars are copied in their
existing deterministic order directly to selected destinations with overwrite.
The callback runs once before the first copy. Ordinary direct source, shared
repository parent, unique destination and direct destination checks remain.
Article-media publication consumes its selected plans inside the ordinary registry
transaction, preparing registration after rendering and copying artifacts before
the registry write. Ordinary I/O failures report failure and may leave already
copied outputs; prior-output preservation and rollback are not promised.

The [S19 combined actual focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-focused-validation-result.json) records
102 passing tests in 8 completed suites: six boundary suites contributed 79,
the CozyVideoSpec publication group 5, and the actual
`cozy.bok.CozyBokSpec` publication filter 18. Failed, aborted, canceled, ignored
and pending counts are all zero. Both invocations ended with SBT and wrapper
exit 0 and serial lock release.
The [first coverage-gap record](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-focused-validation-failure01.json) retains 84 passing tests
in 7 suites: the incorrect `cozy.CozyBokSpec` selector omitted the planned BoK
consumer. Its failed coverage admission is not a failed test. The
[selector correction basis](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-supplemental-validation-selection-basis.json) identifies the actual package;
the [supplemental result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-supplemental-focused-validation-result.json) covers only the missing 18-test
publication filter. The original passing groups were not repeated.

Compiler warnings, errors and fixture diagnostics are zero. Three runtime Logback
duplicate `logback.xml` warning lines remain in unchanged kaleidox/SmartDox
dependencies and were also observed in the actual dated S18 log. They are retained
as `HYG-P715-S19-LOGBACK-001`, pending independent Step classification; no
diagnostic suppression or all-warnings-zero claim is made.
The [M0 placement admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s19-doc-placement-fix-admission.json) records restoration of normal
typed aggregate verification: the Phase plan's top-level status, dependency and
aggregate metadata precede its first H2; its S19 section remains after that
metadata and before Goal.

The [S18 actual focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s18-repair03-focused-validation-result.json) remains dated evidence: 155 successes
in 10 completed suites, zero failures. It is not S19 validation or acceptance.
Remaining ProjectPublisher SHA and original R715-06/08/09/10/11/13/14/15 duties,
MediaReview, MediaWarehouse/modeler and E715-01..04 external consumers remain OPEN.
The [dated S14 native result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s14-factual-projection.json) does not establish current hearing
or final native relevance. Parent source assessment records changes to publication
entry, copy, metadata and shared force grammar; existing source resolution,
workspace copy and synthesize/render/build/RDF implementations preserve generation
behavior. Existing ProfileRender, Video/publication and supplemental BoK suites
exercise the changed entry and output consumers. No new S19 audible playback or
final whole-Phase native acceptance is recorded. Current human hearing and final
JA34 generation/assembly/end-card/EOF obligations remain OPEN.
Mandatory independent M2 cycles 6/7/8/10, whole-Step and full-Phase review,
acceptance, exact local commits and the separate release remain OPEN.
Repository-full validation and broader integration remain deferred-not-run to
PHASE-71.7. This amendment changes no Stage Status triple or closure checkbox.


Status: approved direction; implementation and executable acceptance pending in
[Phase 71](../phase/phase-71.md) through [Phase 71.4](../phase/phase-71.4.md).
Decision: 2026-09-28.


## 2026-10-09 S22 BoK upload and UI ZIP direct output amendment

P715-02-S22-BOK-UPLOAD-BACKUP-UI-ZIP-DIRECT is
**implemented-focused-validated-unreviewed** under the [frozen S22 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s22-implementation-manifest.json).
The initial editing handoff on 2026-10-09 was
**implemented-unvalidated-unreviewed**, with focused validation and independent
review pending. Focused validation is now recorded below; independent review and
acceptance remain OPEN. This amendment supersedes generated website backup configuration,
models, helpers, aliases, scaffold/help promises and protection-only scenarios,
and temporary final UI ZIP replacement promises. Original dated text and results
remain history.

The [workflow implementation](../../src/main/scala/cozy/bok/CozyBokBuild.scala) delegates configured stage/upload commands once
with their ordinary environment and project working directory; missing commands
and actual runner/I/O failures remain failures. The [workflow configuration](../../src/main/scala/cozy/bok/CozyBokBuildConfig.scala)
contains project, name, command and environment without a backup model or legacy
backup decoder. Generic YAML loading remains generic. The [UI ZIP writer](../../src/main/scala/cozy/bok/CozyBokSiteBuild.scala)
reads the configured/default source and writes the selected final ZIP directly,
closing input even if output opening fails and closing output before input.
Source entry order, directory skipping, the same twelve Cozy asset replacements,
supplemental header, standard ZIP format/CRC, toolchain and both locale layouts
retain their meanings. I/O/resource errors propagate and may leave a partial
final ZIP; recovery is regeneration.

Bibliography cache, target-contained SIE handoffs and default UI existence reuse
remain generation-input reuse. Authored-source backups and Site Administrator
monitoring/backups/incident response remain operator responsibilities.
The [existing CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala) retains ordinary workflow, UI content/default/missing
input, locale, path, format and failure contracts; four protection-only backup
cases were deleted, and existing scaffold/help expectations were narrowed.
See the [toolchain specification](bok-site-toolchain.md) and [toolchain design](../design/bok-site-toolchain.md) for retained site ownership.

Other finite removals and external E715-01..04, final current JA34 generation,
audible hearing/end-card/EOF, independent M2 cycles 6/7/8/10, separate Step and
full-Phase review/acceptance, exact commits and release remain OPEN.
Repository-full SBT and broader integration remain deferred-not-run to
PHASE-71.7. No Stage Status triple, closure checkbox, aggregate metadata or old
result is changed by this amendment.

### Actual S22 focused validation — 2026-10-09

The [actual S22 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s22-focused-validation-result.json), following the [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s22-parent-edit-admission.json), records
142 successes in 6 completed suites. Two sequential groups passed: 61 tests in
`cozy.bok.CozyBokSpec`, then 81 tests in the five existing Bibliography,
SiteRouting, BuildTarget, MetadataFinalization and InfographicCommand suites.
Failed, canceled, ignored, pending and aborted counts are zero. SBT and wrapper
exited 0/0, and the shared serial lock was released through the registered route.
Compiler warnings, errors, fixture diagnostics and runtime Logback warning lines
are zero for this invocation. Earlier `HYG-P715-S19-LOGBACK-001` remains pending
independent Step classification; no suppression or whole-Phase warning-free
claim is made. Prior S21 131-test/six-suite results and refusal/successor history
remain attributed to S21.

These existing fixture/consumer results cover affected site/upload/UI behavior.
They establish no live upload, publication/deployment or current native/audible
acceptance. Prior S14 JA34 evidence remains dated; final current generation,
assembly, hearing, end-card/EOF and whole-Phase main-route relevance remain OPEN,
as do the finite/external, M2 and separate Step/full-Phase assurance, commit and
release obligations above. Repository-full SBT and broader integration remain
deferred-not-run to PHASE-71.7.


## 2026-10-08 Phase 71.5 production/assembly amendment

Authority: [Phase 71.5](../phase/phase-71.5.md), P715-02-S01-VIDEO-PRODUCTION,
P715-02-S02-TEMPLATE-CREDITS, P715-02-S03-RENDER-ASSEMBLY-OUTPUT, and the
[artifact-protection removal](../design/artifact-protection-removal.md) policy. This current amendment
supersedes the earlier Video-generated output backup, restoration, rollback,
atomic-install and producer input-interference promises below. Those prior
decisions and accepted results remain dated history; they do not define the
current selected Video production/assembly failure contract.

Selected Remotion and simple-java2d renderers write their declared part outputs
directly. Ordinary final assembly writes and probes the configured final output
directly, then writes its manifest. Native
confirmation/final assembly muxes and probes the selected mode output directly,
then writes its handoffs, part records, applicable credit companions and mode
manifest to their declared destinations. Normal Media-to-Video production uses
one selected input graph and direct render/build/credit writes, without
before/after input comparison or output preservation. A unique assembly work
directory holds concat lists, extracted compressed video, per-part PCM WAVs and
combined WAV processing; it does not hold complete products for later installation.
Selected original part paths and their order are used directly. Docker maps
project-contained paths through `/workspace`, mounts each selected external input
file at its actual absolute path read-only, and mounts the selected external output
parent at its actual absolute path read-write. Exact mounts are deduplicated; an
input already covered by the selected writable output parent needs no extra mount.
The selected output parent is created normally before the command.

Remotion uses the official bundle, composition-selection and media-render APIs
with H264 and PCM16 processing, then writes the returned encoded buffer directly
to the props-selected part output. Its FFmpeg override retains parallel video
encoding and the existing video timebase, CRF and optional x264 preset, and performs
one AAC encode with the existing 320k/18000Hz/stereo-48k parameters. The finite
audio envelope remains `floor(totalFrames * 48000 / fps)` with a 48000 movie
timescale and actual MP4 output. Multi-part assembly retains exact cumulative
48k allocations, lossless WAV concatenation and one final AAC encode.

Malformed or missing required inputs, selected safe paths, explicit mode/output
locations, generation/reuse FileTime decisions, typed Storyboard/handoff schemas,
positive media validity, narration and common final-card/end-of-media semantics,
and required credits remain in force. Authored sources, confirmation products
when final is selected, and unselected outputs remain outside the selected write
scope. Ordinary renderer, mux, probe and I/O failures report the actual failed
operation and selected path. Failed production promises neither success nor
preservation of prior generated outputs; recovery is regeneration.

Bundled named character-dialogue renderer resources (`DialogueVideo.jsx` and
`DiagramLayout.js`) are read once as actual workspace payload, without fixed
SHA pins. Resource names, layout and the actionable missing-resource failure
remain. Selected direct regular non-symlink credit files are read once and
parsed normally, without a file-key prerequisite or repeated-read interference
proof. Direct directory and lexical/real-path admission, context precedence,
schema, vocabulary, duplicate IDs, credit obligations and projections remain;
ordinary read and parse failures identify the selected input. These changes
preserve renderer, narration, domain and timing semantics. The affected existing
Executable Specifications are
[CozyVideoSpec](../../src/test/scala/cozy/video/CozyVideoSpec.scala),
[CozyVideoCreditsSpec](../../src/test/scala/cozy/video/CozyVideoCreditsSpec.scala),
[CozyVideoAssemblyAudioSpec](../../src/test/scala/cozy/video/CozyVideoAssemblyAudioSpec.scala),
[CozyVideoProfileRenderSpec](../../src/test/scala/cozy/video/CozyVideoProfileRenderSpec.scala),
[CozyVideoMediaEndingSpec](../../src/test/scala/cozy/video/CozyVideoMediaEndingSpec.scala), and
[CozyVideoStoryboardBuildModeSpec](../../src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala).

S01 is implemented and focused-validated: four existing suites reported 72
successes, zero failures and one optional native case canceled because its
native property is disabled by default; the serial lock was released. That
canceled case is not native acceptance. S02 template/credit focused validation
reported 154 successes, zero failures and zero canceled cases; the serial lock
was released. S03 direct render/assembly output and selected Docker mounts are
implemented and focused-validated: 180 successes, zero failures and zero
canceled cases; enabled short native generation/assembly also passed and the
serial lock was released. S03 remains unaccepted at the Step/Phase boundary.
The representative native generation/final-assembly/audible-playback result,
including correct end card and EOF, and Step/Phase acceptance remain pending.
Document/media evidence and concrete external readers remain explicit
obligations on Phase 71.5's finite list. This amendment closes no Step or Phase,
claims no accepted native route and grants no protection exception. Repository-full
validation remains deferred-not-run to Phase 71.7.

## 2026-10-08 Phase 71.5 document evidence/export amendment

Authority: [Phase 71.5](../phase/phase-71.5.md),
`P715-02-S04-DOCUMENT-EVIDENCE-EXPORT`, and the
[artifact-protection removal](../design/artifact-protection-removal.md) policy.
This current amendment supersedes earlier digest-currentness, generated-byte
certification and atomic-install/preservation clauses on the selected Document
Project evidence, native provider, state, projection and portable-export paths.
Earlier Phase 42.1, 45, 56/56.1/56.2 and 57.1 decisions remain dated history;
their removed protection promises are not the current production contract.

A file reference records only its project-relative `path`, an ordinary location.
Sidecar `publicSource` retains `kind`, semantic `identity`, `path` and
`mediaDescriptor`; source/artifact alternatives retain `kind` and `path`.
State sources, sidecar and attempt references, historical v1 inputs, native v2
inputs, and generated-review input/output references are path-only. Native
outputs retain `identity`, `path`, and `mediaType`. The canonical in-memory
native receipt value is
`operation=<operation>;output=<path>;mediaType=<mediaType>` under the existing
receipt identity. Schemas and their closed field/order, selected Work Product,
profile, provider, semantic role and media associations remain required.

Native accepted-output state means a valid accepted operation with usable direct
required inputs and a nonempty direct regular selected output. Generated-review
receipts retain kind, project, profile, fixed output and ordered input paths;
their state checks availability. Logical charts require their direct default
output and required inputs. Presentation confirmation still requires valid typed
semantics and complete coverage, plus the direct default HTML.
These paths perform no digest recapture, unchanged-input comparison or rendered
expected-byte comparison. Independent semantic dependency failures remain visible.

State, projection, native attempt and portable-export writes use ordinary direct
operations after their established destination/path admission. A native attempt
uses a fresh UUID for operation history. Portable export writes only
`manifest.yaml` and `work-products/article-review-html/article-review.html` to a
new admitted directory; it reads the actual selected accepted HTML once. The
manifest retains the opaque target and one mapping with exactly `identity`,
`role`, `mediaType`, and `path`. Consumer verification checks the closed grammar,
exact file/directory shape, direct files, duplicate keys and missing, extra,
nonregular or symbolic entries. It requires no project authority or export receipt.
Ordinary read/write failures report the selected operation/path; recovery is
regeneration, with no atomic install, rollback or prior-output/partial-bundle
preservation promise.

Independent Content Alignment, Content Core acceptance,
Description/Confirmation writers and delegated MediaReceipt mechanisms remain
concrete pending finite removals. Their current requirements are not exceptions
to Phase 71.5 policy. Concrete external reader obligations E715-01 through
E715-04 remain open under separate authority. S04 is focused-validated;
S05 validation remains pending. This amendment records no Step or Phase acceptance.
The same full 34-scene native generation/final assembly/audible playback through
correct end card and player EOF remains mandatory and uncompleted. Repository-full
validation remains deferred-not-run to Phase 71.7.


## 2026-10-08 Phase 71.5 presentation semantics and direct scaffold amendment

`P715-02-S05-PRESENTATION-SEMANTIC-COVERAGE` implements the authored v2 Core
association as exactly `{id, language}`, matching the actual admitted closed v1
Core. Strict UTF-8, root/key/schema grammar, catalogs, Composition/Plan,
StoryFlow/Structure, Logical/Visual selection, policy id/revision/bindings,
resource declarations, and semantic diagnostics remain required.

The typed aggregate carries the actual normalized values. Projection carries
Content Core id, declarations, StoryFlow, pages/scenes and mappings. Rendered
carries ordinary deterministic HTML and the fixed semantic renderer/profile.
In-operation coverage checks every Step and Structure, exact mappings, HTML
presence, and actual associations. The existing Cross-Media receipt object and
document path now own only this typed coverage result. Aggregate/output digest
binding and protection-only receipt capture/currentness/persistence are removed.
Historical receipt files are left in place; no replacement is emitted.

Presentation review admits actual typed semantics and complete coverage, then
writes the default or explicitly selected HTML. Confirmation availability checks
that admitted coverage and the actual direct default HTML. Invalid id/language
association or incomplete projection blocks confirmation; no new generic
failure propagation to independent Article, Visual, or chart evidence is added.

Authoring-incomplete is the strict closed scaffold shape with expected ids and
language, empty composition/transitions/structures/bindings and supported policy
revision 1. It is not full-text equality. Scaffold admits the existing direct
parent and absent contained destination, creates that destination, and writes
the declared authored sources directly. Ordinary write failure is reported as
`DP-SCAFFOLD-001`; partial generated scaffold content may remain and recovery is
regeneration. Atomic install, staging, rollback, and previous-output preservation
are not promised. Independent Core/Description/Confirmation writers, Alignment,
upstream Explanation hashes, and delegated MediaReceipt removal remain finite
pending work without an exception.

S04 focused validation completed its three existing suites with 120 successes,
zero failures, zero canceled cases, no warnings, exit 0, and terminal shared-lock
release (`p715-02-s04-focused-validation-result.json`). The first 119-success/
one-failure run and bounded existing MediaReceipt fixture correction remain
retained history. S05 focused validation is pending; Step and Phase acceptance
remain pending. External E715-01 through E715-04 reconciliation and the same full
34-scene JA ModelHarness VOICEVOX generation/final assembly/audible correct end
card/player EOF remain open. Repository-full validation is deferred-not-run to
Phase 71.7.


## Hash removal

Remove application-level hash logic except digest generation and verification
with an explicit purpose of checking published/distributed artifact integrity.
Inventory each retained digest by artifact, operation, and integrity guarantee.
Publication-related placement or naming alone does not justify retention.

Removal includes internal artifact/approval identities, freshness hashes,
computation, comparisons, hash-specific models and codecs, emitted fields,
configuration, scaffolds, and tests requiring those hashes. Do not retain
disabled code, diagnostic-only calculation, compatibility hash processing, or
fallback switches for potential future use. Ordinary collection hashing is
outside this artifact-management contract.

Old document hash properties may be ignored by ordinary document loading or
removed by migration while preserving required non-hash data. They must not
require hash-specific parsing, calculation, comparison, or re-emission.

## File updates

For now, use file existence, declared producer/input dependencies, and file
modification times. Missing outputs or newer declared inputs select the real
producer. Uncertain reuse selects conservative regeneration; a missing producer
or required input is reported explicitly. Do not infer undeclared dependencies.
An explicit prebuilt adoption accepts a valid supplied file even when a copy
preserved its modification time. Validate paths, formats, and required inputs
independently of freshness.

If stricter file update management is required, introduce accurate, explicit
metadata management. Specify the tracked files, dependencies, production
operations, successful-generation state, and metadata update semantics before
implementing it. Content hashes must not substitute for those semantics.
The current Phase does not require a speculative metadata framework.

## Verification obligations

The Phase 71 sequence must update affected subsystem specifications and executable
specifications together with implementation. Verify timestamp-driven rebuild
and reuse, conservative regeneration, explicit prebuilt replacement, historical
document handling without hash processing, and preservation of prior successful
outputs on failure. Audit removal of non-integrity hash code and emitted fields.
Retained publication/distribution integrity checks must still detect actual
artifact mismatches at that boundary and must not determine local freshness.

## Retained-purpose acceptance

The twelve retained purposes in the
[frozen digest inventory](../design/phase-71-digest-purpose-inventory.md)
require evidence for each named artifact, its actual distribution operation,
and the specified mismatch rejection. Checksum metadata emission and parser
syntax alone do not establish byte-verifier coverage; a CAR mismatch scenario
does not establish SAR rejection. The
[Phase 71.3 retained-integrity audit](../journal/2026/10/2026-10-03-phase-71.3-retained-integrity-audit.md)
records the selected existing executable scenarios, result locators, and exact
coverage limitations. All twelve purposes remain PENDING actual selected
validation and independent acceptance, including unresolved OBS-S10-A06 and
OBS-S10-SAR. This ledger changes no purpose definition or integrity guarantee
and grants no local freshness or approval hash exception. Unchanged English-PDF
admission, actual declared-input diagnostics, complete isolated-site proof,
Step/Phase closure, and the Phase 71.4 global removal audit remain pending.

## CML model source metadata

`CmlModelMetadata` describes its source with exactly `path`, `compiler`, and
`cozyVersion`, alongside the unchanged semantic surface, ordered model elements,
and optional component-style snapshot. JSON and YAML keep the
`cozy.cml.model-metadata.v1` schema. They emit no local CML source digest or
replacement identity, and this producer adds no historical-document decoder.

Both `fromCml` and `fromKaleidox` require the actual normalized source to be a
readable regular file, independently of metadata provenance. A missing,
nonregular, or unreadable source raises an invalid-argument fault with
`CML_MODEL_SOURCE_UNREADABLE`, the concrete path, and the required corrective
action. Opening and closing the source exposes actual access failures without
copying its content or recording a file-time receipt. Existing source-path
selection, symlink handling, parsed-model semantics, and parser/component-style
failures remain. Required-input failure occurs before metadata writing, so
existing JSON/YAML destinations remain unchanged. No source immutability or
historical-currentness guarantee is added.

The executable contract is
[CmlModelMetadataSpec](../../src/test/scala/cozy/modeler/CmlModelMetadataSpec.scala):
actual JSON/YAML provenance, both source routes, twenty generated relative-path
and Entity cases, and missing-source failure with prior-output preservation.
This is one bounded producer migration. The separate generation-provenance
producer, aggregate, and CLI migration is implemented under the contract below,
with 136/136 focused scenarios passed and its bounded independent review PASS.
The global audit
and Step/Phase closure remain unfinished. Named CAR distribution-byte
integrity retains its independent purpose and owner.

## Generation provenance metadata

Direct `cozy.generation-provenance.v1` and aggregate
`cozy.generation-provenance.v2` keep
`target/cozy/generation-provenance.json`. Both emit only `schemaVersion`,
`target{cncfVersion}`, `generator{cozyVersion,simpleModelerBackendVersion,
simpleModelingModelVersion}`, and `output{artifacts:[{path}]}`. V1 also emits
`source{identity}`; v2 emits sorted nonempty unique
`sources:[{identity,output:{artifacts:[{path}]}}]`. Every artifact list is a
nonempty sorted unique inventory of canonical safe relative paths. Required
known fields and object/array-entry types remain strict. Ordinary loading
ignores arbitrary historical extras of any type at every object level and
re-emits only the known nonhash projection.

`SourceInput(path, identity)` is the explicit normalized selected readable
regular-file reference. Source identity/version inputs are normalized and
nonempty, while persisted source/artifact claims must already be canonical.
Absolute paths, Windows-drive paths, parent escape and noncanonical claims
fail. Source accessibility is checked by opening/closing its stream; no full
source read/copy/hash, timestamp capture, or historical-content certificate is
added. Actual generation and CML metadata read that selected path directly.
The metadata writer independently checks source readability and identity
agreement at its stage.

Direct and aggregate validation enumerate actual regular Scala paths under
the existing target/excluded-delegate rules and open/close each selected file.
Declared and actual path inventories must agree. Changed readable source or
output contents alone are valid. Missing/added/removed/unreadable required
output, unavailable source, malformed known fields, unsafe paths and exact
selected-version contradictions remain actionable typed stage failures.
Aggregate input is the same explicit nonempty manifest/output-root pair list;
every v1 pair is checked, canonical source identities are unique/sorted, and
all target/generator versions agree. Installed output excludes selected delegate
work, and the sorted source/path union is checked. Shared paths deduplicate
without comparing content or creating a replacement ownership certificate.

Both metadata producers validate before creating/replacing final metadata,
validate temporary JSON, atomically replace the final path, and clean temporary
files on failure. Previous successful metadata and preflight outputs survive
stage/preflight failure; eager CLI metadata deletion is removed. This does not
promise transactional backend source installation. Stable SourceTampered and
OutputTampered codes describe actual source-access/path-inventory failures.
Packaged-only `requireValidPackagedMetadata`/`validatePackagedMetadata` validate
known schemas, exact target/generator, canonical declarations and v2 union
without local source or digest access. The metadata validator CLI needs the
model path, `--save`, `--cncf-version`, `--cozy-generator-version`, explicit
`--generation-source-identity`, and optional current model version; it prints
the successful path without an evidence token.

G03 remains the actual distributed descriptor pin and component-style resource
byte integrity. Descriptor arguments still apply to generation/bridge preflight.
A04 remains actual CAR entry SHA verification, exact file set, coordinates,
ranges and ABI, plus the named source-to-package provenance byte transfer.
Those verifiers retain their existing staging/materialization and do not infer
local source currentness. Changed readable local source/generated contents
alone do not invalidate an intact prebuilt CAR; genuine packaged corruption
continues to fail independently.

The executable contracts are
[Phase51Cv05GenerationProvenanceSpec](../../src/test/scala/cozy/modeler/Phase51Cv05GenerationProvenanceSpec.scala),
[Phase63McmlGenerationProvenanceSpec](../../src/test/scala/cozy/modeler/Phase63McmlGenerationProvenanceSpec.scala),
[CozySbtBridgeMultiProvenanceSpec](../../src/test/scala/cozy/runtime/CozySbtBridgeMultiProvenanceSpec.scala),
[CozyArchivePackagerCv06Spec](../../src/test/scala/cozy/archive/CozyArchivePackagerCv06Spec.scala), and
[CozyCarPublisherSpec](../../src/test/scala/cozy/CozyCarPublisherSpec.scala).
Implementation, selected focused validation (136/136), and bounded independent
review are accepted; global audit and whole Step/Phase acceptance remain open.
The actual evidence is recorded in the
[provenance journal](../journal/2026/10/2026-10-05-phase-71.4-generation-provenance-without-hashes.md).
External build/plugin owners
must adopt current nonhash metadata/validator inputs through their own scope.

## CAR development runtime evidence

The development writer retains `write(projectRoot, runtimeClasspathFile, output):
Unit`, `car-runtime-manifest.json`, `cncf.car-development-runtime-manifest.v2`,
`sourceKind=development-directory`, exact CAR name/version/component and the
declared CNCF minimum/maximum/excluded/tested range. Evidence is an ordered
path-only array: `target/cncf.d/runtime-classpath.txt`,
`target/cncf.d/component-descriptor.json`, optional
`target/cncf.d/component-knowledge.json`, and `src/main/car/abi-manifest.json`.
It emits no `sha256`, `logicalSha256`, or root `integrity` block and computes no
local evidence or normalized classpath digest or replacement certificate.

Required evidence stays contained within the project root and must be regular
and nonempty. Classpath UTF-8 lines and platform path-separator operands are
trimmed and filtered; invalid paths and missing actual entries fail with the
declared path and actionable diagnostic. Descriptor schema 3, exact project and
descriptor coordinates, and the ABI format/coordinate contract remain required.
Valid classpath whitespace/order/duplicates, ABI JSON formatting and compiled
byte changes alone do not change the known development projection. This equality
does not certify historical file contents or complete runtime admission.

Generated descriptor and optional raw carrier preparation retain their existing
contract validation and byte transfer. A04 verifies named packaged CAR entry
bytes; A08 verifies named carrier transport bytes against its descriptor
declaration. Their independent mismatch rejection remains required.

The writer performs its own-stage input, schema, coordinate, ABI and classpath
checks before normalizing the selected final output, creating its parent and
writing the UTF-8 manifest directly. Actual filesystem I/O failures propagate;
partial output may remain and recovery is regeneration. There is no atomic
replacement, temporary-output cleanup or prior-final preservation promise.
Generated descriptor/carrier preparation retains its independent contract.
The executable contract is
[CozyCarRuntimeManifestSpec](../../src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala).
The bounded producer edit is implemented; selected validation passed 104/104 and
independent review remains PENDING in the [A05 journal](../journal/2026/10/2026-10-05-phase-71.4-development-runtime-without-hashes.md).
CNCF reader adoption is an explicit separate handoff; external compatibility is
unproven. Global audit, complete combined-tree/site acceptance and Step/Phase
closure remain open.

## Character-dialogue template resources

The character-dialogue renderer verifies the named distributed resources
`DialogueVideo.jsx` and `DiagramLayout.js` against their fixed integrity
contracts when loading them. Local workspace props and part-manifest metadata
record the selected template ID and those ordered path-only resource entries,
without local template digests. The selection/staging, declarative flow/axis,
and missing/mismatched resource behaviors in
[CozyVideoSpec](../../src/test/scala/cozy/video/CozyVideoSpec.scala) provide
executable coverage for this boundary.

## Video credits

### Normal Media-to-Video production

`media build` delegates video-project currentness and production to Video.
Video observes the selected project, effective context configuration, selected
credit profile/selection, part scripts or Storyboards/sections, steps and
recordings, resolved configured and selected character/visual assets, parsed
audio manifests and consumed WAVs, and native referenced Visual Page, catalog
and asset inputs. Explicit receipt inputs join this producer closure; package
knowledge, descriptor, site and publication inputs remain package validation
and race inputs without automatically becoming video render operands.

Each part uses Generation FileTime policy against its actual render inputs.
Final assembly uses core inputs and actual part MP4s. Missing or invalid credit
companions or unestablished rendered-credit semantics select rendering;
missing final output alone may assemble reusable parts. Newer selected credit
inputs select rendering before assembly, including title, locale, hold, enabled
state and displayed items. Ordinary success always emits the current three
credit companions. Reuse leaves part/final MP4 modification times unchanged.
Required inputs fail specifically; no audio synthesis is implied.

Credit validity positively checks required schemas, types, selected profile,
attribution obligations and presentation fields. Unknown JSON extras, harmless
Markdown whitespace and relocated descriptive provenance paths alone do not
invalidate reuse. These checks validate current obligations, never historical
MP4 content. Valid edits preserving mtime have no additional detection promise.
No equality ledger or replacement identity is introduced.

Before production, direct generated destinations are checked for root escape,
unsafe paths and actual input aliases. Bounded output-only COPY_ATTRIBUTES
backups restore selected primary/companion bytes, mtimes and existence on
in-process render/probe/assembly/install/input-time failure. Failed restoration
retains recovery files and both diagnostics. Renderer scratch is scratch;
confirmation and unselected resources remain separate. There is no lock,
concurrency or crash-safe transaction claim. Integration is specified by
[CozyMediaVideoBuildSpec](../../src/test/scala/cozy/media/CozyMediaVideoBuildSpec.scala).

Preservation ends at the Video producer return and covers its actual inputs
including explicit receipt operands. Later Media package-wide descriptor,
knowledge or site race rejection retains the existing receipt behavior and
does not promise rollback of generated Video outputs across receipt acceptance.

Video credits have no local credit digest computation, model, or field in
credit projections, build/part/RDF records, or CLI output. Actual attribution,
selected profile, provenance, obligations, required-artifact checks, and native
validated-final-record semantics remain. The actual output and failure
behaviors are specified by
[CozyVideoCreditsSpec](../../src/test/scala/cozy/video/CozyVideoCreditsSpec.scala)
and [CozyVideoProfileRenderSpec](../../src/test/scala/cozy/video/CozyVideoProfileRenderSpec.scala).

## Video production and review metadata

Local final-video, audio, input, replay and review digest computations, models,
fields and comparisons are removed. Production and review records retain real
paths, source/step/provider IDs, attribution, actual transcript, scene, timing,
encoding and extracted-frame evidence. Direct-path and artifact checks and
existing failure diagnostics remain; no replacement freshness or identity token
is introduced. Actual production/review outputs, real timing and record failures,
and harmless JSON formatting/key-order behavior are specified by
[CozyVideoSpec](../../src/test/scala/cozy/video/CozyVideoSpec.scala).
This bounded metadata contract does not claim complete P710-03-V, Phase closure
or actual-driver assurance.

## Video publication source metadata

Video publication metadata omits authored `descriptorSha256` and
`scriptSha256`; descriptor/script paths, source package, article path, roles
and publication coordinates remain. Main MP4 and named sidecar publication
checksums, and transient staged/installed byte checks, protect only the
distributed artifacts selected by the publication operation.

P71001-P02 producer planning uses ordered unique relative path/state/`FileTime`
observations over the existing resolved descriptor, article, script/part-step,
tree files, resolved assets and semantic assets. Direct regular files record
their actual modification time after lexical/real containment and non-symlink
file/parent checks. Absent, not-directory, directory and optional-missing states
carry no time; directory modification times are not compared. These typed
observations exist only within the operation and are never serialized or used
as approval evidence. No local producer source digest or replacement content
identity is calculated.

The existing publication plan compares these ordered observations before
rendering and again after rendering, before repository admission and registry
replacement. Missing/unsafe inputs, path/state/set changes and observed file-time
changes reject; optional semantic assets becoming present also invalidate the
plan. Parsers, renderer/format checks and path safety remain independently
authoritative. Valid authored byte edits with preserved modification times are
admissible: this contract does not promise historical detection of such edits
or a stronger race, atomicity or rollback guarantee. Actual preflight, command
rendering, workspace copies and publication behavior are specified by
[CozyArticleMediaVideoCommandSpec](../../src/test/scala/cozy/publication/CozyArticleMediaVideoCommandSpec.scala).

P71001-P03/P04 separately retain actual named distributed MP4/sidecar metadata
digests, byte-equal artifact reuse and force replacement, and source-to-temporary
installation integrity verification. Those checks protect the selected
distribution bytes and do not determine local source currentness. Local
registry/WIP observations follow the bounded correction below.
The written bundle and actual
copied bytes are specified by
[CozyVideoProfileRenderSpec](../../src/test/scala/cozy/video/CozyVideoProfileRenderSpec.scala);
artifact reuse, difference, force and failure preservation by
[CozyVideoPublisherAdmissionSpec](../../src/test/scala/cozy/video/CozyVideoPublisherAdmissionSpec.scala);
and selected published-byte mismatch rejection by
[CozyArticleMediaVideoEvidenceSpec](../../src/test/scala/cozy/publication/CozyArticleMediaVideoEvidenceSpec.scala).
See the [frozen digest inventory](../design/phase-71-digest-purpose-inventory.md)
and [video purpose audit](../journal/2026/10/2026-10-02-phase-71.2-video-digest-boundary-audit.md).
This source-metadata change does not establish actual-driver, Step or Phase
closure.

## Local WIP observations and MP4 transfer integrity

The Phase 71.3 WIP binding uses ordinary local file observations: normalized
lexical/real paths, size, actual `FileTime` modification time, and stable
fileKey. Direct regular non-symlink and root containment checks, coherent
two-read bytes/attributes, exact transient descriptor bytes, and actual selected
production JSON bytes preserve same-operation validation. Equivalent production
JSON formatting with unchanged size, modification time, and file identity
invalidates that operation's captured bytes. These observations are not
persisted approval/currentness identities, and generic snapshots calculate no
hash. WIP explicitly adopts the valid supplied MP4 and requires its actual
production identity/locale, completed render, and technical-and-visual QA;
it has no `render.sha256` admission gate or dedicated legacy hash processing.
Ordinary PDF receipts and selected review-state currentness remain unchanged.

Only the actual selected MP4 supplies the explicit source artifact digest and
optional admitted installed artifact digest. They protect the canonical
correlated publication record and named copy, backup, installed destination,
and restoration bytes under P71001-P03/P04. No local configuration, production
JSON, infographic reuse, or PDF digest is retained for this purpose, and no
command-wide MP4 byte snapshot is added. Rollback rechecks the complete original
registry including bundle bytes, unchanged roots/source/production/context and
candidate evidence, and original/restored optional artifact digest equality.
Atomic backup restoration uses the actual restored direct destination inode
and modification time; this restoration-only check preserves strict ordinary
plan revalidation and installed-byte checks for independent operations.

Executable coverage belongs to the WIP
[binding](../../src/test/scala/cozy/publication/CozyArticleMediaWipBindingSpec.scala),
[transaction](../../src/test/scala/cozy/publication/CozyArticleMediaWipTransactionSpec.scala),
and [public command](../../src/test/scala/cozy/publication/CozyArticleMediaWipCommandSpec.scala)
specifications. Publisher source-manifest removal is migrated under S08;
BoK project-source metadata and generated staging selection are migrated under
S09 as specified below. The final named-integrity mismatch audit, unchanged
English-PDF admission and declared-input diagnostics, and complete isolated
site-entry acceptance remain unfinished. This correction claims no Step or
Phase closure.

### Presentation and its direct generated-evidence consumers

Both `slide-ir-v1` and `visual-page-v1` Presentation routes use nonhash renderer
and review evidence. Receipt artifacts are exactly `id,role,path` and current
artifact evidence compares those typed ordered records with the structurally
verified current outputs. Renderer records retain schema/target/profile,
renderer name/version, PPTX and montage paths, ordered slide/page IDs and paths,
v1 titles and ordered asset IDs. Review records retain the corresponding
required semantic fields and path wrappers. Recursive positive known-field
selection ignores arbitrary extras at renderer/review/artifact objects,
Storyboard root/scene/screen/visual-input/page/asset/renderer/configuration objects,
and CrossReview root/slide/page/asset/verification objects. It has no special
legacy-field mask or replacement whole-content identity. Authored configuration
and Storyboard contracts remain unchanged.

ReviewState's root schema/current/last-aligned/authority/sync grammar stays
closed; each snapshot requires only a UTC ISO-8601 `acceptedAt`, with generic
extras ignored. Current is the actual review-file generation time and explicit
alignment records decision time. Reuse preserves alignment; a newer generation
requires realignment even when semantic bytes are identical. Declared-input and
generation-companion timestamps remain admission boundaries. Valid edits that
preserve timestamps are not promised historical-content detection. Invalid
required semantics, actual PNG/PPTX/OOXML structure, unsafe/missing direct files,
and one-for-one raw asset-byte multiset disagreement still reject at the same
timestamp. Cross-root current material byte agreement is retained as structural
QA; source/staged/destination publication integrity remains separately owned.

Receipt/review-state installation and aggregate CrossReview keep their existing
failure-preservation guarantees. Primary Presentation renderer files remain
direct outputs; no new atomic renderer transaction is claimed. The SummarySlidesPdf
renderer subsystem is migrated under S07; Publisher source-manifest removal
under S08 and BoK/staging removal under S09 are bounded migrations. The final
named-integrity mismatch audit, unchanged English-PDF admission and declared-input
diagnostics, and complete isolated-site drivers remain pending. This amendment
claims no Step/Phase closure or integrity exemption.

### Direct SummarySlidesPdf

Both direct PDF renderer schemas now select positive required fields generically,
with arbitrary metadata ignored at root, renderer, PDF, slide, asset, montage
and configured PPTX objects. Required relative root order is
`schema,target,profile,renderer,pdf,slides,montage[,pptx]`. Exact renderer
`name,version`, positive `pdf.pageCount`, unique authority-ordered page and
asset IDs, both slide paths, v1 title and montage path remain required.
Configured PPTX requires its path; unconfigured `pptx` rejects. The authored
configuration remains closed. No named legacy mask, local digest calculation
or replacement content identity is used.

Current dependency receipts and actual safe direct inputs remain mandatory.
PDF header/PDFBox parse and actual page count, PNG signature, descriptor-relative
path/collision/symlink rules, and Visual Page selected-asset raw-byte agreement
remain current structural checks. Optional PPTX has only its existing internal
direct-file floor; no OOXML or render-equivalence promise is added. Sorted
operation-local Path/FileTime observations over the exact renderer input set
are compared before/after rendering, together with existing receipt-input
FileTime observations. Neither observation is serialized. Changed times and
missing/unsafe inputs reject before installation; same-time valid byte changes
have no historical detection guarantee. Same-time malformed PDFs, wrong actual
page counts and invalid required structure continue to reject.

Same-parent staged ATOMIC_MOVE installs the primary only after checks pass;
modeled pre-install failure preserves actual prior primary PDF bytes and receipt
and cleans staging. Renderer sidecars are direct outputs with no rollback or
atomicity guarantee after an invoked renderer fails. This subsystem is migrated
under S07 without claiming Phase inventory, Step/Phase closure, EnglishPDF or
isolated-site proof. BoK project-source metadata and rsync selection are migrated
under S09 below; the final named-integrity mismatch audit and complete site
proofs remain separately unfinished.

## BoK project-source metadata and generated staging

Public CAR project registration and subsequent authored-title updates retain
the actual source package, descriptor path, article and publication paths,
project name, mode and reference, CAR module, version and version source,
summary, terms, tags, and catalog/CML/SIE metadata. Project metadata emits no
`descriptorSha256`, computes no authored descriptor digest, and introduces no
replacement source identity. Actual selected CAR bytes still supply published
artifact SHA-256 and size; project metadata, the version manifest and artifact
metadata agree on the selected artifact coordinates and integrity data.
Catalog-declared checksums remain distribution metadata. This emission contract
does not itself establish canonical CAR mismatch rejection; the named-integrity
acceptance audit remains separate. At least fifty isolated public registration
and update trials are specified by
[CozyBokProjectSpec](../../src/test/scala/cozy/CozyBokProjectSpec.scala).

The staging prototype produced by public BoK scaffold creation uses ordinary
`rsync -av` size/mtime reuse, with no local `--checksum` selection. It makes no
promise to detect historical byte changes that preserve both size and time.
The main site copy keeps `--delete`, quoted source/destination roots, and the
`.git` and `repository` exclusions. Built and separately configured repository
sources overlay the staging repository without deletion. Environment/default
paths, `set -eu`, directory guards, missing-build diagnostic with exit 2 and
the `cozy bok build` hint, optional repository diagnostic, and final Git
directory-or-file status guard remain. The upload prototype retains its
existing contract. The actual generated products are specified by
[CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala). This bounded S09
migration does not claim complete-site proof or Step/Phase closure.

## Source inventory and admitted CAR distributions

Standalone component source inventory is an ordered relative path inventory;
it reads no selected content for identity and emits no local source, entry or
archive hash. Fresh release-source production validates the project root, exact
`target/cozy/release-source` destination, authored path selection, explicit
managed-source/root operands, typed policy/license and safe build evidence
before mutating generated output. Selected source paths must not overlap that
output root. An existing output must be a non-symlink directory. The producer
clears only this selected generated root without following links, creates it,
copies ordered public sources directly with overwrite and writes its path-only
manifest directly. Restricted generation emits the manifest only. Ordinary
I/O failures propagate and may leave partial output; recovery is regeneration.
Authored inputs and unselected roots retain their ownership.

Explicit release-source and Scaladoc admission uses the supplied distribution
independently of current authored/managed source bytes or build hashes.
Release-source admission retains exact safe output location and current declared
project policy agreement. Both loaders positively require their unchanged v1
schema and sorted unique safe relative entry paths excluding their own manifest.
Release-source also requires SourceCode identity, typed license/policy and safe
string-array build evidence. Unknown JSON properties at every object are ignored
generically. Each declared path's segments must be non-symlinks, and its selected
final file must be regular. Only declared entries are packaged; unlisted stage
files are neither selected nor rejected through a whole-tree inventory.
Entry SHA, content digests, exact whole-stage sets and repeated-byte identity
checks are removed. Restricted manifests must declare no source entries.
Scaladoc keeps index.html, generated search/symbol evidence and source-page/
source-link rejection on selected text inputs.

CAR packaging projects normalized manifest text from the known required fields
and leaves the supplied manifest untouched. Manifest path provenance remains
available separately from payload-only archive entries. Source/Scaladoc manifest
namespace collisions with CAR inputs still reject. Ordinary `verify-existing`
selects reuse of a declared prebuilt release-source distribution with these
required metadata and selected-path checks. The external sbt-cozy producer stays
read-only and unmodified: Cozy normalizes the packaged manifest without rewriting
that producer's input stage. This grants no external protection-removal or
compatibility acceptance. Existing-output refusal, CREATE_NEW, producer readback
verification and catch/delete rollback are retired.
Executable contracts remain
[ComponentSourceArchiveProjectionSpec](../../src/test/scala/cozy/archive/ComponentSourceArchiveProjectionSpec.scala),
[ComponentReleaseSourceProjectionSpec](../../src/test/scala/cozy/archive/ComponentReleaseSourceProjectionSpec.scala)
and [CozyArchivePackagerSpec](../../src/test/scala/cozy/CozyArchivePackagerSpec.scala).
This bounded distribution contract does not establish Step or Phase completion.

## Local component API dependency selection

Cozy selects a required `apiClass` among the exact declared CAR dependency
coordinates, retaining actual `provided.version` and `artifactPath` projections,
assembly membership and missing/optional/ambiguous-provider checks. Ordinary
descriptor loading ignores unknown historical hash extras of any JSON type;
they supply no ABI compatibility guarantee. Repeated selected artifacts are
consolidated by exact dependency coordinate, normalized archive path and artifact
path in first selected order, after ambiguity checks. Ordinary selection completes
before admitting the caller-selected generated extraction root as a non-symlink
directory. A regular-file or symbolic-link root is rejected before deletion.
The operation clears the entire obsolete generated output set under that root
without following symlinks, creates the directory and copies selected API JARs
directly in first selected order. Successful empty optional selection creates an
empty output directory. Actual deletion or copy failures propagate and may leave
partial generated output; recovery is regeneration. No stage, backup, restoration,
suppressed recovery diagnostic or backup-cleanup warning is promised. The executable contract is
[ComponentApiDependencyResolverSpec](../../src/test/scala/cozy/ComponentApiDependencyResolverSpec.scala).
This is the Cozy local selection boundary: the external descriptor producer and
runtime reader remain unchanged, and their migration and the global removal
audit remain open. This amendment grants no Step or Phase closure.

## Implementation ownership after the 2026-09-29 split

The behavior above is unchanged by the planning split. Phase 71 owns the real
producer/graph contract, digest-purpose inventory and executed Core-rooted video
bootstrap; Phase 71.1 owns document/media and inventoried adjacent application
removal/currentness; Phase 71.2 owns remaining video removal, continuity and
pronunciation; Phase 71.3 owns local site/PDF/prebuilt adapters and retained
publication-integrity acceptance; Phase 71.4 owns media-end repair, the global
removal audit and complete combined-tree acceptance. Each canonical member
checklist is the work-state authority; this specification grants no additional
external repository/skill mutation or successor execution authority.

## Phase 71 single-operation policy boundary

The executable contract for one admitted file operation is
[CozyFileUpdatePolicySpec](../../src/test/scala/cozy/generation/CozyFileUpdatePolicySpec.scala),
backed by the package-private `CozyFileUpdatePolicy` classifier. It is a pure,
deterministic selector over already checked file observations: generation uses
declared input order, existence, independently established validity, and
`FileTime` modification timestamps; explicit prebuilt adoption is a separate
operation that examines only the supplied output. The policy has no hash,
receipt, persisted schema, filesystem I/O, CLI, graph resolver, dispatcher, or
producer invocation. A missing producer or required input is a reported
unavailable result, not an inferred dependency or an invented fallback.

This policy does not become an overall native AI dependency planner. Codex and
the declared source-authoring graph continue to own semantic dependency
planning and source updates. Passing these file-policy specifications alone is
not product acceptance: the selected private Core-rooted video driver, focused
validation, independent review, and later Phase-owned acceptance remain
separate obligations. No successor implementation is performed or implied by
this contract.

## S18 Media direct-publication amendment — 2026-10-09

Status: **implemented-focused-validated-unreviewed** under `P715-02-S18-MEDIA-PUBLICATION-DIRECT`.
The original implementation handoff on 2026-10-09 recorded
implemented-unvalidated-unreviewed with ten-suite validation pending; that former
state is retained as history. The actual later result is recorded below.
This dated amendment supersedes earlier Media publication source/destination
hashes, prepared-input/currentness seals, force/reuse dispositions, temporary
files, fsync, atomic installation and generated-output protection promises,
including the earlier `publish-media --force` grammar. Earlier observations and
acceptance records remain historical evidence.

Initial selected descriptor/resource/profile and normalized direct-path
admission remains. Public PDF preparation still requires its selected ordinary
receipt and PDF review state; infographic preparation still admits the actual
PNG, declared build manifest, receipt/dependencies, locale, version and provenance.
These are initial format and semantic admissions. Commit uses the already
selected typed vector and current in-memory effective profile, without rereading
the descriptor or reconstructing a saved publication plan. It validates unique
normalized destinations and direct roots/parents, invokes the before-first-install
callback once, then copies each selected direct regular source in deterministic
order with `Files.copy(..., REPLACE_EXISTING)`. Actual destination existence
reports factual `Created` or `Replaced`; existing destinations receive the selected
output through ordinary replacement.

Direct Media dry-run retains identifying text and changes no destination or
absent local profile root. Real legacy publication may create its selected local
root; externally selected roots must already exist. Explicit BoK `publish-media`
still rejects dry-run and rejects `--force` through the existing strict
declared-request builder, reporting `Too many arguments: --force`. `PublicationConfig` and the BoK manifest no longer carry
`mediaForce`. The remaining `force` setting belongs to actual Video behavior.
Infographic commands keep initial complete candidate/registry admission,
semantic invocation matching, both existing callbacks, actual artifact results
and the registry-last merge. Ordinary directory/symlink errors retain path
diagnostics; an actual copy IOException reports Media publication failure.
A failed direct copy may leave earlier or partial output, with no success,
rollback or prior-output guarantee. Recovery is regeneration.

The parent-selected ten existing suites completed with **155 successes** and
**zero failures, aborted suites, canceled, ignored or pending tests**. Warnings,
errors and fixture diagnostics are zero. SBT and wrapper exits are 0 and the
shared serial lock was released. Independent review and acceptance remain pending. VideoPublisher
force/hash/staging/fsync/atomic behavior and producer metadata remain original
unfinished next-unit work. Remaining finite removals, `E715-01..04` external
readers, current human hearing, independent M2 assurance for cycles 6/7/8/10,
Step/Phase reviews, acceptance, local commits and release remain **OPEN**.
Repository-full SBT and broader integration remain **deferred-not-run to Phase
71.7**. This amendment uses the original work list and introduces no new schema,
version, compatibility adapter, removal-proof test or absence audit.

## S18 actual-result continuation — 2026-10-09

S18 is **implemented-focused-validated-unreviewed**. The parent-admitted
[final focused-validation result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s18-repair03-focused-validation-result.json) records **155 successful tests
across ten completed suites**: the first seven suites passed 131 tests and the
second three passed 24. There are **zero failures, aborted suites, canceled,
ignored or pending tests**, and **zero warnings, errors or fixture diagnostics**.
SBT and wrapper exits are both **0**, and the shared serial lock was **released**.
The [raw terminal log](/private/tmp/skill.cncf.d/a-6db5d345-ad86-4db6-93b2-33ec9f816dfc/88215-20261008T222603Z.log) and
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s18-repair03-parent-edit-admission.json) retain the supporting execution and
admission facts. This document-only continuation performs no source, Executable
Specification or control edit and runs no validation, review or commit.

The [first S18 failed result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s18-focused-validation-failure01.json) remains historical: its first
seven-suite run reported **130 successes and one failure**, with zero warnings.
The original failure and all repair/refusal histories remain preserved. The
already completed strict-parser repair uses the existing `buildStrict` API for
publish-media and the same existing force-rejection scenario observes
`org.goldenport.context.FaultException` with `Too many arguments: --force`.
The journal's corrected strict-parser requirement row, actual force-rejection
clauses and source/GWT reference 111 remain unchanged by this projection.

Cycle **9** restored the original English executable-specification grouping;
parent exact inspection plus the final ten-suite validation admit that **M1**
repair. Cycle **10**'s strict-parser source and existing FaultException assertion
are focused-validated, while **independent M2 assurance remains OPEN** together
with cycles **6/7/8**. Focused test success does not replace independent M2
assurance, fabricate a P715-02 Step-review predecessor or close the Step/Phase.
No per-Slice review or commit is claimed and no checklist box is closed.

Original VideoPublisher producer/destination force/hash/staging/fsync/atomic
work, remaining Media review byte checks, finite MediaWarehouse/modeler and other
removals, and separately owned **E715-01..04** external-reader duties remain
**OPEN**. Current human hearing and final whole-Phase native relevance remain
**OPEN**. S18 selects no speech/storyboard/renderer/assembly/end-card change;
dated S14 JA34 evidence supplies no new native execution or hearing result.
Independent Step/Phase reviews, acceptance, local commits and distinct release
remain **OPEN** under the unchanged original full Goal, base, authority and plan
epoch. Repository-full SBT and broader integration remain **deferred-not-run to
Phase 71.7**. Earlier S17 and S18 handoffs remain dated history; this actual
focused result grants no product, Step, Phase, all-removal or hearing acceptance.

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


## 2026-10-09 S21 Sample ZIP and BoK metadata direct output amendment

P715-02-S21-[CozySampleDistributor](../../src/main/scala/cozy/publication/CozySampleDistributor.scala)-ZIP-[CozyBokSieMetadata](../../src/main/scala/cozy/bok/CozyBokSieMetadata.scala)-FINALIZATION-DIRECT is
**implemented-focused-validated-unreviewed** under the [frozen S21 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-implementation-manifest.json).
This dated amendment supersedes the earlier staging, atomic installation,
failure-preservation and rollback guarantees for these selected outputs.
Earlier dated contracts, evidence, refusals, repairs and acceptance remain history.
At the initial editing handoff, no validation count, independent review or
completion was claimed.

Sample distribution freezes the ordered eligible source-file inventory before
opening each selected final archive. All planned collection and per-sample
archive paths are excluded by their lexical absolute-normalized paths, so
selected generated ZIP outputs do not become archive inputs. The final ZIP
is written directly with the standard ZipOutputStream/ZipEntry codec, including
required CRC behavior and zero entry timestamps. Source ordering, exclusions,
project/warehouse/name/version/publication-path defaults, exact collection and
sample coordinates and slug-collision prevalidation remain. Dry-run writes
nothing and no external runner is invoked. Each actual selected generation
writes current contents. An IOException propagates and may leave a partial
archive or earlier completed archives; the final output is not deleted or
restored on failure.

BoK finalization keeps initial project/configured-source/generated-root and
website-root admission, source/target non-overlap and known generated resource
validation. The canonical TermIndex decoder and CAR/SAR component-reference
index checks still run before copying or clearing selected website metadata.
The existing destination inventory admits ordinary selected file/directory
types and enumerates only the old allowlisted generated files. Those files
are deleted directly, then current generated metadata is copied directly to
the final website root. This is replacement of the generated allowlisted set:
missing current resources do not reuse stale website graphs, indexes, terms or
knowledge-source manifests. Directory roots, rendered HTML, assets and
unselected files remain outside that clearing. Finalization creates no stage,
backup, saved output copy or rollback transaction.

The nine file allowlist remains `rdf/site.ttl`, `rdf/site.jsonld`,
`metadata/rdf/graph.json`, `metadata/glossary/terms.json`,
`metadata/bibliography/bibliography.json`, `metadata/scenarios/scenarios.json`,
`metadata/tags/tags.json`, `metadata/sie/integration.json` and
`metadata/cncf/knowledge-source.json`. The six directory allowlist remains
`metadata/repository/car`, `metadata/cncf/component-references`,
`metadata/catalog/projects`, `metadata/projects`,
`metadata/artifacts/repository` and `metadata/releases`.
Both copy helpers admit the actual selected target and parent before creating
or copying it; source symlink/nonregular rejection and destination containment
remain. The shared ordinary/locale build-copy route and public signatures
remain. Authored source authority, generated resources, declared glossary
requirements and explicitly declared extension path/schema/collision/generated
authority rules retain their meanings. The existing pipeline still copies
resources, merges SIE, applies declared RDF extensions, versions and validates
the graph and its node/edge/component references, then writes the
`cncf.knowledge-source.v1` manifest with its existing resource/sourceRef semantics.
There is no schema or lifecycle replacement. Initial resource decoder failures
precede copying; later missing terms, extension, RDF or I/O failures propagate
and may leave already replaced allowed output or created directories.
Successful repeated generation remains deterministic.

Only the seven existing late-error cases lose protection-only manifest
sentinels and preservation assertions; their real domain diagnostics and
unrelated HTML assertions remain. The two existing malformed glossary/CAR
case titles say before copying. No case, suite or proof harness is added.
At the initial editing handoff, the parent-selected existing six-suite focused
validation and independent assurance were pending; the editor ran no validation
or self-review.

Original finite R715-06/08/09/10/11/13/14/15 and other unfinished removal duties,
including BoK ProjectPublisher/RepositoryMetadata/RepositoryPages,
WarehouseIndexer and remaining Build/website/config helpers, remain **OPEN**.
Concrete external E715-01..04 reader reconciliation, final current JA34 native
generation/assembly, actual audible hearing, end card and EOF, independent M2
cycles 6/7/8/10, whole-Step/full-Phase review, acceptance, exact local commits
and the distinct release remain **OPEN**. Prior S14 JA34 evidence remains dated;
the parent must assess actual S21 main-route relevance. Repository-full SBT
and broader integration remain **deferred-not-run to PHASE-71.7**. Stage Status
triples, checklist checkboxes and top-level aggregate metadata are unchanged.
No per-Slice acceptance, external compatibility, publication, upload,
deployment, successor execution or Phase closure is claimed.

The affected contract and implementation references are [BoK finalization specification](bok-metadata-finalization.md), [BoK finalization design](../design/bok-metadata-finalization.md),
[existing CozyBokMetadataFinalizationSpec](../../src/test/scala/cozy/CozyBokMetadataFinalizationSpec.scala), [CozySampleDistributor](../../src/main/scala/cozy/publication/CozySampleDistributor.scala) and [CozyBokSieMetadata](../../src/main/scala/cozy/bok/CozyBokSieMetadata.scala).


### Actual S21 focused validation — 2026-10-09

Current S21 state is **implemented-focused-validated-unreviewed**.
The [actual S21 focused-validation result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-focused-validation-result.json) records **131 successful tests across 6 completed suites**.
The first sequential task group passed **45 tests in 2 suites**; the second
passed **86 tests in 4 suites**. Both groups have **0 failed, canceled, ignored
or pending tests and 0 aborted suites**. SBT and wrapper exits were **0/0**,
and the shared serial lock was **released**. Compiler warnings, errors,
fixture diagnostics and runtime known Logback warning lines were all **zero
for this invocation**. The dated S19 Logback lines remain
`HYG-P715-S19-LOGBACK-001`, pending independent Step classification; no broader
warning-free claim or warning suppression is made.

The [original pre-render origin diagnosis](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-pre-render-origin-diagnosis01.json) retains the original current S21 history-preflight refusal:
the old runner omitted exactly one trailing LF from the native source, and the
unchanged helper rejected it before renderer, submission or SBT execution.
The parent admitted exactly one fresh successor under the already deployed
revision 17, as recorded by the [one-successor relation](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-origin-successor-relation.json). It used the **same original
unused attempt, permission, authority, subject, argv and effects**; permission
was not renewed. The successor supplied the exact native source and obtained
`origin_present=true` in the [actual successor native-source acceptance](../../.codex-workflow/phases/PHASE-71.5/p715-02-s21-origin-successor-native-acceptance.json). It then followed the normal
first scoped platform review and shared serialized SBT route. No shared skill
was edited. These origin facts describe the command's preflight and execution;
they do not establish audiovisual playback or hearing.

The parent assessment is limited to the actual selected Sample ZIP writes and
BoK website metadata finalization. Existing publication, metadata finalization,
knowledge-source, site-routing, build-target and BoK suites cover the ordinary
selected paths and callers. No narration, frame, render, audio, assembly or
end-card implementation changed, and no current native, audible or whole-Phase
acceptance is inferred. Prior S14 JA34 remains dated evidence only. Final
current representative JA34 generation/assembly, actual audible hearing,
end-card/EOF and whole-Phase main-route relevance remain **OPEN**.

Original finite removals, remaining R715-13 WarehouseIndexer,
ProjectPublisher/RepositoryMetadata/RepositoryPages/build/helper work and other
unfinished finite duties, E715-01..04, independent M2 cycles 6/7/8/10,
whole-Step/full-Phase review, acceptance, exact local Step commits and the
distinct release remain **OPEN**. Repository-full SBT and broader integration
remain **deferred-not-run to PHASE-71.7**. The initial implementation handoff,
S21 behavior and GWT clauses, all prior history and the admitted S20 selection
correction remain recorded. All seven Stage Status triples, checklist
checkboxes and top-level aggregate metadata retain their existing state.
This factual projection performs no new validation, independent review,
workflow transition, per-Slice acceptance/commit, Step/Phase closure or release.

## 2026-10-09 S23 LogicalUI direct review implementation

P715-02-S23-LOGICAL-UI-DIRECT-REVIEW is
**implemented-focused-validated-unreviewed** under the
[frozen S23 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s23-implementation-manifest.json).
The initial editing handoff on 2026-10-09 was
**implemented-unvalidated-unreviewed**. Focused validation is now recorded
below; independent review and acceptance remain OPEN.
It implements the selected LogicalUI portion of original finite
[R715-06](../design/phase-71.5-removal-work-list.md), retaining the Phase 71.4
original base `fc640ec25e443d00c22b2c26b437a205d39910eb`.

Candidate, strict JSON codec, screen projection, and semantic projection now
consume normalized typed values directly. Explicit acceptance retains its
candidate and nonempty trimmed decision ID. Management digests and their
bindings are removed. Review retains substantive escaped, deterministic HTML
and informational diagnostics; its overview shows the explicit decision ID.
Review receipts, currentness recomputation, and temporary/atomic HTML
replacement are removed. The selected existing non-symlink parent/direct-child
regular-or-absent `.html` admission runs once, followed by direct UTF-8
create/write/truncate. Ordinary write failures remain errors and can leave
partial generated output; regeneration is recovery. Component coordinates,
UseCase and constraint IDs, domain validation, navigation, feedback, and typed
state facts retain their meanings.

Exactly four existing LogicalUI suites are updated; two protection-only
receipt/currentness scenarios are deleted. Existing semantic and HTML ordering
properties remain. The [LogicalUI specification](../spec/logical-ui-model.md),
[design](../design/logical-ui-model.md), and
[S23 editing journal](../journal/2026/10/2026-10-09-phase-71.5-logical-ui-direct-review.md)
record this boundary. Parent-selected focused validation covers the existing
Phase43 acceptance suite and the five candidate/semantics/review/runtime suites.
The actual result is recorded below; independent acceptance remains OPEN.

The other original R715-06 CAR review provider request/evidence digests and all
remaining original finite duties, concrete E715-01..04 external readers, final
current JA34 generation/assembly/audible hearing/end-card/EOF, independent M2
cycles 6/7/8/10, whole-Step/full-Phase review and acceptance, exact local commits,
and distinct release remain OPEN. Prior S14 native and S22 142/6 evidence remain
dated and attributed. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. Aggregate metadata, Stage Status triples,
checkboxes, and earlier dated evidence retain their recorded status.

### Actual S23 focused validation — 2026-10-09

The [actual S23 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s23-focused-validation-result.json), following the
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s23-parent-edit-admission.json), records **50 successes in 6 completed
suites**. Two sequential groups passed: 3 tests in the Phase43 SalesOrder
acceptance suite, then 47 tests in the existing LogicalUi, Semantics, Review,
Runtime, and `cozyruntime` List/Detail acceptance suites. Failed, canceled,
ignored, pending, and aborted counts are zero. The initial registered runner
route completed with SBT/wrapper exits 0/0 and shared serial lock release.
Compiler warnings, errors, fixture diagnostics, and runtime Logback warning
lines are zero for this invocation. Existing `HYG-P715-S19-LOGBACK-001` remains
pending its original Step classification; no suppression or whole-Phase
warning-free claim is made.

Parent assessment confines the changed source to LogicalUI typed values,
review, and direct HTML output, with existing runtime List/Detail consumers
covered. It identifies no JA34 narration/synthesis/frame/render/assembly/audio/
credit/end-card producer edge. No new native generation or hearing is claimed;
S14 native and S22 142/6 remain dated evidence only. CAR-provider request/evidence
digests in original R715-06, all remaining finite duties and E715-01..04,
final current JA34 generation/assembly/hearing/end-card/EOF, independent M2
cycles 6/7/8/10, whole-Step/full-Phase assurance and acceptance, exact local
commits, and distinct release remain OPEN. Repository-full SBT and broader
integration remain **deferred-not-run to PHASE-71.7**. Stage Status triples,
checkboxes, aggregate metadata, and prior dated history retain their status.

## 2026-10-09 S24 warehouse and project artifact metadata emission

P715-02-S24-WAREHOUSE-PROJECT-ARTIFACT-METADATA is
**implemented-focused-validated-unreviewed** under the
[frozen S24 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s24-implementation-manifest.json).
The initial editing handoff on 2026-10-09 was
**implemented-unvalidated-unreviewed**. Focused validation is now recorded
below; independent review and acceptance remain OPEN.
This amendment supersedes checksum emission promises for the selected warehouse
and BoK project artifact metadata. Earlier dated results remain history.

[CozyWarehouseIndexer](../../src/main/scala/cozy/publication/CozyWarehouseIndexer.scala)
no longer calculates SHA-256, reads SHA1/MD5 sidecars, or carries/emits those
fields in indexed files and JSON/YAML. The existing jar/pom/car/sar/zip allowlist
selects artifact files; on-disk sidecars and authored inputs are untouched.
Coordinates, release/SNAPSHOT versions, classifiers, names, selected paths,
canonical/legacy download selection, first-result deduplication, registry
consistency, actual size, stream closing and ordinary missing/path/I/O behavior
retain their meanings.
[CozyBokProjectPublisher](../../src/main/scala/cozy/bok/CozyBokProjectPublisher.scala)
emits base metadata plus actual size for present artifacts and base metadata for
absent artifacts. It omits calculated SHA-256 and the catalog checksum projection;
catalog input models/loading/admission retain their existing contracts. Status,
expected/sourceOnly, version/module/path, catalog channel/status/publishedAt and
source/runtime/CML/SIE facts remain ordinary metadata.

Exactly three existing [PublicationCompilerSpec](../../src/test/scala/cozy/PublicationCompilerSpec.scala)
scenarios change: Maven hash fixtures/expectations and irrelevant index sidecar
fixtures are removed; repeated sample distribution asserts selected sample and
collection archives are regular files. Existing ZIP members/exclusions and all
ordinary coordinates, paths, registry, status and version assertions remain.
No scenario, suite or removal-proof harness is added.

The [S24 journal](../journal/2026/10/2026-10-09-phase-71.5-warehouse-project-artifact-metadata.md)
records the editing handoff. At that initial handoff, parent-selected focused
validation and independent review were pending. Other original catalog/archive/descriptor/runtime/filesystem
duties, external E715-01..05, final current JA34 generation/assembly/audible
hearing/end-card/EOF, independent M2 cycles 6/7/8/10, whole-Step/full-Phase review,
acceptance, exact local commits and the distinct release remain OPEN.
Repository-full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. Stage Status triples, checkboxes and aggregate metadata retain
their existing state; no acceptance or closure is claimed.

### Actual S24 focused validation — 2026-10-09

The [actual S24 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s24-focused-validation-result.json), following the
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s24-parent-edit-admission.json), records **106 successes in 3 completed suites**.
Two sequential groups passed: **20 tests in 1 suite**, PublicationCompilerSpec,
then **86 tests in 2 suites**, `cozy.bok.CozyBokSpec` and
CozyBokMetadataFinalizationSpec. Failed, canceled, ignored, pending and aborted
counts are zero. Terminal SBT/wrapper exits are **0/0** and the shared serial
lock was **released**. Compiler warning lines, errors, fatal fixture diagnostics
and known runtime Logback warning lines are zero for this invocation. Existing
`HYG-P715-S19-LOGBACK-001` remains pending original independent Step
classification; no suppression or whole-Phase warning-free claim is made.

Parent assessment limits the two-source change to artifact metadata checksum
calculation/read/projection. The repeated sample-distribution fixture narrows hash
proof while retaining ordinary output and earlier ZIP content. No narration,
source, frame, synthesis, render, assembly, audio, credits or end-card producer
changed. Prior S14 native and S23 actual 50/6 remain dated, attributed evidence;
no new native generation or hearing is claimed. Other original catalog/archive/
descriptor/runtime/filesystem duties, E715-01..05 including the incompatible CAR
reader, final current JA34 generation/assembly/hearing/end-card/EOF, independent
M2 cycles 6/7/8/10, whole-Step/full-Phase assurance and acceptance, exact local
commits and distinct release remain OPEN. Repository-full SBT and broader
integration remain **deferred-not-run to PHASE-71.7**. Stage Status triples,
checkboxes, aggregate metadata and initial handoff history retain their state.

## 2026-10-09 S25 BoK repository metadata and local admission amendment

P715-02-S25-BOK-REPOSITORY-METADATA-ADMISSION is
**implemented-focused-validated-unreviewed** under the
[frozen S25 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s25-implementation-manifest.json).
The initial editing handoff on 2026-10-09 was
**implemented-unvalidated-unreviewed**. Focused validation is now recorded
below; independent review and acceptance remain OPEN.
This amendment supersedes checksum JSON/HTML projection and independent local
archive/sidecar byte-admission promises in this selected BoK boundary. The
[metadata model](../../src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala)
omits the version checksum field and JSON entry. The
[repository pages](../../src/main/scala/cozy/bok/CozyBokRepositoryPages.scala)
omit the checksum row/helper, archive SHA/catalog-checksum and integrityKey
comparisons, and sidecar SHA calculation/64hex/equality admission.

Archive selection still uses the declared path and a regular file, with explicit
release-coordinate admission before opening the ZIP. ZIP/JSON parsing, standard
format/CRC, descriptor/ABI diagnostics, availability and resource closing remain
ordinary behavior. A carrier selects its sidecar by coordinates; missing regular
sidecars fail. Declaration validation keeps the exact current four string fields,
schemas and logical path, including string type validation for `sha256`, followed
by the existing direct copy to the public consumer-contract path. No byte-binding
or unchanged-byte guarantee is made. The shared carrier shape is unfinished under
**E715-01 OPEN**; source catalog/publisher checksum/integrityKey admission remains
**original R715-10 OPEN**. Neither is an exception or completed removal.

The [existing repository specification](../../src/test/scala/cozy/CozyBokComponentRepositorySpec.scala)
narrows its carrier scenario to canonical metadata, the public contract path and
regular-file availability, retaining the real producer/catalog digest fixtures
until their original dependencies are reconciled. Its substituted-archive
protection scenario is deleted. No scenario, suite or removal-proof harness is
added. The [S25 journal](../journal/2026/10/2026-10-09-phase-71.5-bok-repository-metadata-admission.md)
records exact clauses and header maintenance.

At the initial handoff, parent-selected CozyBokComponentRepositorySpec,
CozyBokSpec and CozyBokMetadataFinalizationSpec focused validation and independent
review were pending. Focused validation is recorded below; independent review
remains OPEN. Other original finite duties, E715-01..05, current JA34 native generation/
assembly/audible hearing/end-card/EOF, independent M2 cycles 6/7/8/10, separate
whole-Step/full-Phase review and acceptance, exact local Step commits and distinct
release remain OPEN. Prior results and Hygiene remain dated and attributed.
Repository-full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. Aggregate metadata, Stage Status triples and closure checkboxes
retain their state; no Slice, Step or Phase acceptance is claimed.

### Actual S25 focused validation — 2026-10-09

The [actual S25 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s25-focused-validation-result.json), following the
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s25-parent-edit-admission.json), records **93 successes in 3 completed suites**.
Two sequential groups passed: **7 tests in 1 suite**,
`cozy.bok.CozyBokComponentRepositorySpec`, then **86 tests in 2 suites**,
`cozy.bok.CozyBokSpec` and `cozy.CozyBokMetadataFinalizationSpec`. Failed,
canceled, ignored, pending and aborted counts are zero. Terminal SBT/wrapper
exits are **0/0**, and the shared serial lock was **released**. Compiler warning
lines, errors, fatal fixture diagnostics and known runtime Logback warning lines
are zero for this invocation. Existing `HYG-P715-S19-LOGBACK-001` remains pending
its original independent Step classification; no suppression or whole-Phase
warning-free claim is made.

Parent assessment limits the selected change to the BoK model/page and local
archive/sidecar boundary. No narration, frame, synthesis, render, assembly, audio,
credits or end-card producer changed. Prior S14 native and S24 actual 106/3 focused
results remain dated and attributed; no new native generation or hearing is
claimed. Original R715-10 source catalog/publisher admission, E715-01 shared
carrier shape and all other original catalog/archive/descriptor/runtime/filesystem
duties remain OPEN. E715-01..05, including the actual incompatible CAR reader,
final current JA34 generation/assembly/audible hearing/end-card/EOF, independent
M2 cycles 6/7/8/10, whole-Step/full-Phase assurance and acceptance, exact local
Step commits and distinct release remain OPEN. Repository-full SBT and broader
integration remain **deferred-not-run to PHASE-71.7**. Aggregate metadata,
Stage Status triples, closure checkboxes and initial handoff history retain their
state. This factual projection performs no new validation, self-review, workflow
transition, staging or commit, and claims no Slice, Step or Phase acceptance.


## 2026-10-09 S26 repository publication direct output amendment

P715-02-S26-REPOSITORY-PUBLICATION-DIRECT is
**implemented-focused-validated-unreviewed** under the
[frozen S26 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s26-implementation-manifest.json).
The initial editing handoff on 2026-10-09 was
**implemented-unvalidated-unreviewed**. Actual focused validation is recorded
below; independent review and acceptance remain OPEN.
The selected repository publisher writes its archive, current SHA sidecar,
catalogs, Maven metadata and CAR sidecars directly to final paths. Actual detailed
catalog/archive validation precedes the direct index write last. Index validation
retains coordinate, canonical path, regular archive, identity, lifecycle and
selector checks, without archive/sidecar byte admission. CAR prebuilt/provenance
admission uses real selected inputs; generated temporary CAR cleanup remains
ordinary resource ownership. Explicit complete legacy mapping converts canonical
archive/catalog/sidecar output directly and preserves legacy inputs and unrelated
SAR entries. Partial I/O output is possible; there is no transaction, lock,
candidate overlay, snapshot, backup, rollback, atomic visibility or lost-update
guarantee. See the [publication design](../design/component-repository-publication.md)
and [migration specification](../spec/component-repository-migration.md).

Current catalog model/facade/parser checksum and integrityKey fields, actual
hash emission and SHA sidecar production remain **unfinished original R715-10**
work. Shared carrier/runtime shapes and concrete **E715-01..05** reconciliation
remain OPEN; no fake value, schema change or compatibility claim is introduced.
The existing specifications retain ordinary behavior and narrow only the selected
protection contracts; no new scenario, suite or proof harness is added. Exact
scenario/Given/When/Then changes are recorded in the
[S26 journal](../journal/2026/10/2026-10-09-phase-71.5-repository-publication-direct-output.md).

At the initial handoff, parent-selected existing six-suite focused validation
and independent review were pending. Actual focused validation is recorded
below; independent review remains OPEN. Remaining finite duties, current JA34 native generation/assembly/
audible hearing/end-card/EOF, independent M2 cycles 6/7/8/10, whole-Step and
full-Phase review/acceptance, exact local Step commits and distinct release remain
OPEN. Prior results and dated evidence retain their attribution. Repository-full
SBT and broader integration remain **deferred-not-run to PHASE-71.7**. Aggregate
metadata before the first H2, Stage Status triples, closure checkboxes and original
row numbering retain their state. This implementation amendment claims no Slice,
Step or Phase acceptance.

### Actual S26 focused validation — 2026-10-09

The [actual S26 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s26-focused-validation-result.json), following the
[parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s26-parent-edit-admission.json), records **74 successes in 6 completed suites** for
**P715-02-S26-REPOSITORY-PUBLICATION-DIRECT**. The first sequential group passed
**35 tests in 3 suites** (`cozy.CozyCarPublisherSpec`, `cozy.CozySarPublisherSpec`
and `cozy.archive.Phase56NamespaceQualifiedCarPublicationSpec`); the second passed
**39 tests in 3 suites** (`cozy.archive.ComponentRepositoryIndexSpec`,
`cozy.RepositoryArtifactCatalogSpec` and `cozy.bok.CozyBokComponentRepositorySpec`).
Failed, canceled, ignored, pending and aborted counts are **zero**. The two
`testOnly` tasks included their normal implicit Main/Test compilation. Compiler
warning/error lines, runtime warning/error lines and fatal fixture diagnostics
are **zero for this invocation**. Existing Hygiene and prior results retain their
attribution; no suppression or whole-Phase warning-free claim is made.

Registered serial invocation `4d8e25f1-085d-44a1-b33b-5a2c1ca7a4d3` completed
with terminal **SBT/wrapper exits 0/0** and the **shared serial lock released**.
These accepted focused results establish the selected existing specification
behavior. Independent review and final acceptance remain OPEN.

The parent assessment limits S26 to direct repository publication/index/migration
and the CAR compatibility caller. No narration, frame, synthesis, render,
assembly, audio, credit or end-card producer changed. Prior S14 native results
and **S25's 93 successes in 3 suites** remain dated and attributed; they do not
establish acceptance of the changed pipeline or current audible hearing. Final
current **JA34 native generation/assembly/audible hearing/end-card/EOF and
whole-Phase route acceptance remain OPEN**.

Original **R715-10 catalog model/hash/integrityKey/checksum/SHA sidecar emission
remains unfinished**. Shared carrier/runtime shapes and **E715-01..05 remain
OPEN**, without removal closure or compatibility claims. Other finite duties,
independent **M2 cycles 6/7/8/10**, whole-Step/full-Phase review and acceptance,
exact local Step commits and the distinct release remain **OPEN**.
Repository-full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. Aggregate metadata, Stage Status triples, closure checkboxes,
original row numbering and prior refusal/review/native evidence retain their
state. This factual projection runs no new validation and performs no review,
workflow transition, staging or commit; no Slice, Step or Phase acceptance is
claimed.


## S27 repository catalog without digests — 2026-10-09

P715-02-S27-REPOSITORY-CATALOG-WITHOUT-DIGESTS is currently
**implemented-focused-validated-unreviewed** under the
[S27 frozen manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s27-implementation-manifest.json).
The initial 2026-10-09 implementation handoff was
**implemented-unvalidated-unreviewed**; that dated status remains history.
The catalog model, public facade, YAML/JSON projections, renderer and CAR
admission now use exactly `version`, `channel`, `status`, `component`,
`publishedAt`, `file` and `runtime`. Canonical CAR allowed fields omit the retired
checksum/integrity fields; generic SAR/legacy parsing remains generic. CAR/SAR
publication and mapped migration copy archives directly and emit no repository
SHA sidecars. Ordinary coordinates, schema/runtime, selectors, status, selected
paths, source/sidecar preflight, metadata/index-last sequencing, generated-build
cleanup and I/O failure behavior retain their meanings. Existing unselected
sidecar files are not removed.

The six existing Catalog, CAR/SAR publisher, ComponentRepositoryIndex, Phase56
namespace publication and BoK component-repository specifications retain their
ordinary behavior clauses. Only retired digest/sidecar clauses and fixtures are
removed; no whole case or suite is added or deleted. The existing SAR varied
payload property retains at least fifty publication trials. The initial
2026-10-09 handoff left parent-selected six-suite focused validation, diff
admission and independent review pending. Actual S27 focused validation and
parent edit admission are recorded below; independent review remains OPEN.
S26's actual 74 successes in 6 suites remain dated S26 evidence.

`RepositoryArtifactPublisher.sha256` remains because the four actual
`SubcomponentReleasePackaging` call sites and required Component knowledge
carrier declaration fixtures still use real SHA values. This is unfinished
original **R715-09/11 and E715-01/03/04** work, not an accepted protection
exception or compatibility adapter. Required carrier/runtime/descriptor/
subcomponent/review contracts and all **E715-01..05 remain OPEN**; no external
repository or unsupported producer-only contract is changed.

This catalog distribution change edits no narration, frame, synthesis, render,
assembly, audio, credit or end-card producer. Final current **JA34 generation,
assembly, audible hearing, end-card and EOF** obligations remain OPEN, as do
other original finite work, independent **M2 cycles 6/7/8/10**, whole-Step and
full-Phase review/acceptance, exact local Step commits and the distinct release.
Repository-full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. Original authority/base/epoch, repair counts, aggregate metadata,
Stage Status triples, closure checkboxes, row numbering and prior refusal,
review and native evidence retain their state. No Slice, Step or Phase acceptance
is claimed.

### Actual S27 focused validation — 2026-10-09

P715-02-S27-REPOSITORY-CATALOG-WITHOUT-DIGESTS is currently
**implemented-focused-validated-unreviewed**. The
[actual S27 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s27-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s27-parent-edit-admission.json)
record **74 successes across six completed suites**: the first sequential group
had 35 successes in three suites and the second had 39 in three suites. Failed,
canceled, ignored and pending tests and aborted suites were all zero. Main and
Test compilation was implicit in this invocation; compiler warnings and errors,
fatal fixture diagnostics and runtime warning lines were zero for this invocation.
This records the selected invocation only; whole-Phase warning disposition remains
subject to independent assurance.

Serial invocation `e0fe6161-f0bf-48b1-996a-d592d8a75388` completed with SBT and
wrapper exit codes **0/0**, and the shared serial lock was released. The selected
**R715-10 catalog model/hash/integrityKey/checksum and repository SHA sidecar
emission** removal is focused-validated-unreviewed. Whole R715-10 and Phase closure
remain OPEN. The required real `RepositoryArtifactPublisher.sha256` helper,
carrier/runtime/descriptor/subcomponent/review contracts, remaining original
finite work and **E715-01..05** remain OPEN.

Parent edit admission is source inspection, not independent review or acceptance.
Independent **M2 cycles 6/7/8/10**, whole-Step and full-Phase review/acceptance,
exact local Step commits and the distinct release remain OPEN. Seven catalog and
distribution owners changed; no narration, frame, synthesis, render, assembly,
audio, credit or end-card producer changed. Prior native evidence remains dated;
final current **JA34 native generation, assembly, audible hearing, end-card and
EOF** remain OPEN. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. Prior S26's actual 74 successes in six suites,
initial S27 handoff, refusal/review/native history and original stage and aggregate
metadata retain their dated attribution and state. No Slice, Step or Phase
acceptance is claimed.

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

## 2026-10-09 S29 source and Scaladoc direct distribution amendment

**P715-02-S29-SOURCE-SCALADOC-DIRECT-DISTRIBUTION** is
**implemented-focused-validated-unreviewed** under the
[frozen S29 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s29-implementation-manifest.json).
The initial 2026-10-09 editing handoff was
**implemented-unvalidated-unreviewed**; that dated status remains history.
This dated amendment supersedes the earlier source/Scaladoc entry SHA,
content-digest, whole-stage inventory and release-source protected-install
promises. Original observations and validation/refusal history remain dated
history. The [release-source projection](../../src/main/scala/cozy/archive/ComponentReleaseSourceProjection.scala)
and [Scaladoc admission](../../src/main/scala/cozy/archive/CozyArchivePackager.scala)
use path-only manifests with unchanged schemas and generic unknown-property
loading. Required metadata, policy/license, selected regular paths and
non-symlink path segments, sorted unique order, restricted empty declarations,
Scaladoc index/search and selected source-disclosure semantics remain.
Only declared entries are packaged, with read-only known-field manifest
normalization and manifest provenance.

Release-source generation preflights inputs, the exact selected generated root,
ordinary existing-directory shape and source/output separation before clearing
obsolete output. It directly copies ordered selected public sources and writes
the manifest. Existing-output refusal, CREATE_NEW, readback verification and
catch/delete rollback are removed. I/O failure may leave partial output;
recovery is regeneration. Authored/unselected paths retain their ownership.
Meaningful `verify-existing` reuse remains. The external sbt-cozy producer is
read-only and unmodified; Cozy does not rewrite its stage or claim removal or
compatibility acceptance for that producer.

The existing [release-source Executable Specification](../../src/test/scala/cozy/archive/ComponentReleaseSourceProjectionSpec.scala)
and [packager Executable Specification](../../src/test/scala/cozy/CozyArchivePackagerSpec.scala)
retain their whole cases, adjacent Given/When/Then, actual copied/packaged
payload assertions, valid-input read-only normalization and the fifty-trial
source metadata property. Only frozen protection clauses/variant arms are
retired; AC02 gains the ordinary selected source/output overlap branch.
All four Scala files have the Oct. 9, 2026 header with original since,
earlier-month history and author attribution preserved or restored from the
parent-observed Git evidence. Dead digest helpers/imports and the local `go`
naming debt are removed in this same pass.

At the initial 2026-10-09 handoff, the parent-selected two representative suites
were **pending**:
`cozy.archive.ComponentReleaseSourceProjectionSpec` and
`cozy.CozyArchivePackagerSpec`. The three accumulator suites were also
**pending**: `cozy.archive.ComponentSourceArchiveProjectionSpec`,
`cozy.archive.Phase56CanonicalCarDescriptorCodecSpec` and
`cozy.archive.CozyArchivePackagerCv06Spec`. No validation was run by the editor.
The [S29 journal](../journal/2026/10/2026-10-09-phase-71.5-source-scaladoc-direct-distribution.md)
records the initial implementation handoff. Focused validation and parent
admission are now recorded below; independent review and acceptance remain OPEN.

Actual runtime/carrier/subcomponent/descriptor/review duties and original
**E715-01..05**, other original finite rows, final current **JA34 generation,
assembly, audible hearing, end-card and EOF**, independent **M2 cycles 6/7/8/10**,
whole-Step/full-Phase review and acceptance, exact local Step commits and the
distinct release remain **OPEN**. Repository-full SBT and broader integration
remain **deferred-not-run to PHASE-71.7**. Original base/authority/epoch, counters,
finite list, aggregate metadata, Stage Status triples, closure checkboxes and
all existing dirty text retain their state. No Slice, Step, Phase or native
acceptance is claimed.

### Actual S29 focused validation — 2026-10-09

**P715-02-S29-SOURCE-SCALADOC-DIRECT-DISTRIBUTION** is currently
**implemented-focused-validated-unreviewed**. The
[actual S29 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s29-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s29-parent-edit-admission.json)
record **93 successes across five completed suites**. The two sequential groups
completed with **59 successes in two suites**, then **34 successes in three
suites**. Failed, canceled, ignored and pending tests and aborted suites were
all **zero**. Main and Test compilation was implicit in this invocation,
compiling two Scala sources in each scope. Compiler warnings, runtime Logback
warnings, unclassified runtime warnings, errors and fixture diagnostics were
all **zero for this invocation**. Whole-Phase warning disposition remains
subject to independent assurance.

Serial invocation `bc518203-bf52-4097-a5a4-17f0911b97ec` completed with
SBT and wrapper exit codes **0/0**, and the shared serial lock was **released**.
The selected source/Scaladoc path-only distribution admission and direct
release-source generated output are focused-validated-unreviewed. The initial
2026-10-09 **implemented-unvalidated-unreviewed** handoff and its pending-suite
record remain dated history. Prior **S28's 118 successes in six suites** retain
their S28 attribution. Parent edit admission records source inspection;
independent review and acceptance remain OPEN.

The parent assessment identifies only source/Scaladoc archive distribution,
with no narration, frame, synthesis, render, assembly, audio, credit or end-card
producer change. Prior native evidence remains dated; final current **JA34
generation, assembly, audible hearing, end-card and EOF** remain OPEN. Other
original **R715-09 runtime/carrier/subcomponent** duties, required descriptor/
review contracts, other finite rows and **E715-01..05** remain OPEN. Independent
**M2 cycles 6/7/8/10**, whole-Step/full-Phase review and acceptance, exact local
Step commits and the distinct release remain OPEN. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**.

Original base/authority/epoch, counters, finite list, aggregate metadata,
Stage Status triples, closure checkboxes and all refusal/review/repair/native
history retain their state. This factual projection performs no new validation,
self-review, staging, commit or workflow transition. No Slice, Step, Phase or
native acceptance is claimed.

## 2026-10-09 S30 CAR runtime direct output amendment

**P715-02-S30-CAR-RUNTIME-DIRECT-OUTPUT** is
**implemented-focused-validated-unreviewed** under the
[frozen S30 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s30-implementation-manifest.json).
The initial 2026-10-09 **implemented-unvalidated-unreviewed** editing handoff
remains dated history; actual focused validation is recorded below.
Independent review and acceptance remain OPEN.
This dated amendment supersedes development runtime atomic replacement,
temporary-output cleanup and prior-final preservation promises, and the
prebuilt release provenance source/package raw-byte equality requirement.
Original dated evidence and the separate **S29 implemented-focused-validated-unreviewed
93-success/five-suite result** retain their attribution.

The [development runtime writer](../../src/main/scala/cozy/archive/CozyDevelopmentRuntimeManifest.scala)
performs its existing own-stage input/schema/coordinate/ABI/classpath checks,
normalizes the selected final output, creates its parent and directly writes
UTF-8 JSON with a final LF. The ordered path-only evidence, runtime range,
generated descriptor and optional carrier preparation retain their meanings.
Actual I/O failure propagates, may leave partial output, and requires
regeneration; no temporary output, protected installation or recovery protocol
is introduced.

The [packaged runtime admission](../../src/main/scala/cozy/archive/CozyCarRuntimeManifest.scala)
uses the canonical public Boolean `requireGenerationProvenance`, with no old
parameter label or Option-path alias. Required admission reads the actual named
`generation-provenance.json` entry and invokes the existing packaged parser for
known schema, exact CNCF target and Cozy generator versions, and safe source/
artifact logical paths. Its ordinary temporary input materializes packaged
bytes for the existing Path parser and is deleted in `finally`; it is neither
output protection nor a source/package equality certificate.
The [CAR publisher](../../src/main/scala/cozy/archive/CozyCarPublisher.scala)
still validates generated-release provenance at the owning selected project's
`target/cozy/generation-provenance.json` through `requireValidForPackaging`
before archive admission. The helper returns explicit Unit and the same
generated-release decision supplies the requiredness Boolean. Generation/reuse,
coordinates, runtime, version and warehouse publication semantics remain.

**E715-01 and E715-03 remain unfinished.** The actual external CNCF
`ComponentKnowledgeCarrierCodec` requires exactly `carrierSchema`,
`consumerContractSchema`, `logicalPath` and `sha256`; `CarRuntimeAdmission`
requires integrity-entry SHA, the whole listed regular-file set and actual byte
hash equality. Their Cozy producer fields, helpers and admission checks remain
unchanged. This is an unresolved external responsibility edge, with no
integrity exception, compatibility claim, fake digest or schema/adapter change.
All original **E715-01..05** obligations remain OPEN.

The existing [runtime Executable Specification](../../src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala)
retains every whole case, both fifty-trial properties, actual carrier/ABI/raw
payload assertions and all real error variants. Four development error cases
retire only previous-output snapshots/preservation clauses and retain an
initial successful schema expectation before the later invalid Given. The
existing mixed package case keeps optional admission and runtime integrity
contradiction, and adds required packaged provenance valid, missing and
wrong-CNCF-target branches with regenerated actual runtime evidence and ZIPs.
Modified behavior uses adjacent Given/When/Then and `should` expectations.
All four Scala headers are Oct. 9, 2026 with original since, earlier-month
history and ASAMI attribution. Dead atomic-write imports/helper and the old
Option-path provenance helper are removed in this pass; required runtime
digest helpers remain used by the unfinished external contract.

At the initial 2026-10-09 editing handoff, representative suites
`cozy.archive.CozyCarRuntimeManifestSpec` and `cozy.CozyCarPublisherSpec`, and
accumulator suites `cozy.archive.CozyArchivePackagerCv06Spec` and
`cozy.runtime.CozySbtBridgeMultiProvenanceSpec` were **pending**, as were parent
edit admission, independent review and acceptance. No validation was run by the
editor. The [S30 journal](../journal/2026/10/2026-10-09-phase-71.5-car-runtime-direct-output.md)
retains that dated handoff. Actual focused validation and parent edit admission
are now recorded below; independent review and acceptance remain OPEN.

Other original finite/runtime/carrier/subcomponent/descriptor/review obligations,
final current **JA34 generation, assembly, audible hearing, end-card and EOF**,
independent **M2 cycles 6/7/8/10**, whole-Step/full-Phase review and acceptance,
exact local Step commits and the distinct release remain **OPEN**.
Repository-full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. Original base/authority/epoch, counters, finite list, aggregate
metadata, Stage Status triples, closure checkboxes and all existing dirty text
and refusal/review/repair/native history retain their state. No Slice, Step,
Phase or native acceptance is claimed.

### Actual S30 focused validation — 2026-10-09

**P715-02-S30-CAR-RUNTIME-DIRECT-OUTPUT** is currently
**implemented-focused-validated-unreviewed**. The
[actual S30 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s30-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s30-parent-edit-admission.json)
record **49 successes across four completed suites**. The two sequential groups
completed with **36 successes in two suites**, then **13 successes in two
suites**. Failed, canceled, ignored and pending tests and aborted suites were
all **zero**. Main and Test compilation was implicit in the invocation,
compiling **three main Scala sources and one Test Scala source**. Compiler
warnings, runtime Logback warnings, unclassified runtime warnings, errors and
fixture diagnostics were all **zero for this invocation**. This makes no
whole-Phase warning-free claim; independent assurance and existing warning
dispositions retain their scope.

Serial invocation `de341157-1133-4b7a-9d72-560869d033a2` completed with
SBT and wrapper exit codes **0/0**, and the shared serial lock was **released**.
S30 CAR runtime direct output and required packaged provenance semantics are
focused-validated only. The initial implemented-unvalidated-unreviewed handoff
and pending-suite record remain dated history. Parent edit admission records
source inspection; it is not independent review. Separate **S29's 93 successes
in five completed suites** retain their S29 attribution.

The supplied native assessment covers CAR runtime manifest writing and
provenance admission only, with no JA34 narration, synthesis, render, assembly
or credit producer changes. Final current **JA34 generation, assembly, audible
hearing, end-card and EOF** remain OPEN. Other original **R715-09
runtime/carrier/subcomponent** duties, required descriptor/review contracts,
other finite rows and **E715-01..05** remain OPEN. Independent **M2 cycles
6/7/8/10**, whole-Step/full-Phase review and acceptance, exact local Step commits
and the distinct release remain OPEN. Repository-full SBT and broader
integration remain **deferred-not-run to PHASE-71.7**.

Original base/authority/epoch, counters, finite list, aggregate metadata,
Stage Status triples, closure checkboxes and all refusal/review/repair/native
history retain their state. This factual projection performs no new validation,
self-review, staging, commit or workflow transition. No Slice, Step, Phase or
native acceptance is claimed.

## S31 subcomponent release direct output amendment — 2026-10-09

**P715-02-S31-SUBCOMPONENT-RELEASE-DIRECT** is currently
**implemented-focused-validated-unreviewed**. Its initial 2026-10-09 editing
handoff was **implemented-unvalidated-unreviewed**; actual focused validation
and parent edit admission are recorded below. Independent review and acceptance
remain OPEN.

Under [P715-02-S31-SUBCOMPONENT-RELEASE-DIRECT](../../.codex-workflow/phases/PHASE-71.5/p715-02-s31-implementation-manifest.json),
Cozy's own `cozy.subcomponent-release.v1` manifest has exactly
`schemaVersion`, `publishedAt`, `parent`, `children` and `composition`.
Parent and child references have exactly `archivePath`, `canonicalId` and
`coordinate`; coordinates retain `namespace`, `id` and `version`. The
composition reference has exactly `archivePath`, `schema` and `membershipKind`.
These references describe selected semantic payloads and paths. Own release,
payload and composition references carry no local SHA/signature proof, and
there is no release-integrity document or admission marker protocol.

`package-subcomponent-release` and `publish-subcomponent-release` no longer
accept `--integrity`; no ignored flag, compatibility alias or schema-version
replacement is introduced. Packaging admits selected CAR/ABI inputs, project
coordinates, required/optional membership and canonical composition, then
writes the semantic manifest, unchanged composition bytes and actual CAR
payloads directly to the selected final archive. Sorted stored ZIP entries
retain standard CRC, size and time fields. Publication reads the selected
release once, parses its manifest, composition and payloads, checks project
coordinates and canonical `publishedAt`, and writes its bytes directly with
overwrite to:

```text
repository/subcomponent-release/<groupPath>/<mavenArtifactId>/<version>/<mavenArtifactId>-<version>.release.car
```

Source/output nonoverlap, safe archive paths, exact path sets, duplicate JSON
field rejection, coordinate/ABI/schema parsing, composition classification,
roles, logical resources, runtime/technology and provenance meanings remain.
No ordinary CAR/SAR catalog, discovery index or runtime activation changes.
There is no local hash calculation/comparison, post-output reread, lock, CAS,
protected install, temporary staging, snapshot, backup, rollback or prior-output
preservation guarantee. Actual I/O failures propagate as operation failures;
partial final output requires regeneration. Existing unselected legacy files
and authored inputs are not swept or deleted.

The copied RSC02 `cncf.component-subcomponent-composition.v1` contract remains
unchanged, including required supplied artifact `sha256` and `signature`
fields and their format parsing. Those fields are not compared with local CAR
payload bytes. Actual external CNCF
`ComponentSubcomponentComposition.scala` still requires `coordinate`,
`sha256`, `signature`, `repository` and `physicalPath`. This is unfinished
**E715-04**, requiring separately authorized consumer reconciliation, not a
compatibility exception or removal closure. All **E715-01..05 remain OPEN**.

The [existing release Executable Specification](../../src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala)
retains seven semantic scenarios and real invalid membership/composition/
release-manifest variants. Only the two own integrity/admission protocol cases
are retired. The deterministic case retains repeated actual package equality,
independent semantic and nested payload expectations, and adds fifty generated
child-input permutation trials through the actual command, checking canonical
ZIP order and payload content. At the initial 2026-10-09
**implemented-unvalidated-unreviewed** handoff, selected three-suite validation,
parent edit admission and independent review were pending. Actual validation
and parent edit admission are now recorded below; independent review remains
OPEN. S30's actual **49 successes in
four suites** remain S30 evidence. No Slice, Step or Phase acceptance is claimed.

### Actual S31 focused validation — 2026-10-09

**P715-02-S31-SUBCOMPONENT-RELEASE-DIRECT** is currently
**implemented-focused-validated-unreviewed**. The
[actual S31 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s31-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s31-parent-edit-admission.json)
record **27 successes across three completed suites**. The first sequential
`testOnly` group completed **7 successes in one suite**,
`cozy.archive.SubcomponentReleasePackagingSpec`; the second completed
**20 successes in two suites**, `cozy.archive.CozyCarRuntimeManifestSpec` and
`cozy.runtime.CozySbtBridgeMultiProvenanceSpec`. Failed, canceled, ignored and
pending tests and aborted suites were all **zero**. Normal implicit Main/Test
compilation compiled **two main Scala sources and one Test Scala source**.
Compiler warnings and errors, runtime Logback warnings, unclassified runtime
warnings, errors and fatal fixture diagnostics were all **zero for this
invocation**. This is a result for the selected invocation, not a whole-Phase
warning-free claim; existing warning dispositions retain their scope.

Serial invocation `7eb79b82-c90d-464a-bc5f-a009e6a74341` completed with SBT and
wrapper exit codes **0/0**, and the shared serial lock was **released**.
S31's local direct package/warehouse output and semantic release contract are
focused-validated only. The initial **implemented-unvalidated-unreviewed**
editing handoff and its pending-suite record remain dated 2026-10-09 history.
Parent edit admission records source inspection; it is not independent review
or Step/Phase acceptance. S30's actual **49 successes in four completed suites**
retain their separate S30 attribution.

Original **R715-11** remains unfinished at the required copied external RSC02
composition contract: actual required supplied SHA/signature fields and format
parsing remain unchanged, with **E715-04** and all **E715-01..05 OPEN**.
Other original finite/runtime/carrier/descriptor/review and external-consumer
obligations remain OPEN. There is no external reconciliation, compatibility
exception, schema bump, fake digest or producer-only removal closure.

The supplied native assessment covers only local subcomponent package and
warehouse output, with no JA34 narration, synthesis, render, assembly or credit
producer change. Final current **JA34 generation, assembly, audible hearing,
end-card and EOF** remain **OPEN**. Independent **M2 cycles 6/7/8/10**,
whole-Step/full-Phase reviews and acceptance, exact local Step commits and the
distinct release remain **OPEN**. Repository-full SBT and broader integration
remain **deferred-not-run to PHASE-71.7**.

Original base/authority/epoch, repair counters, finite rows, aggregate metadata,
Stage Status triples, closure checkboxes and all prior refusal/review/repair/
native history retain their state. This factual projection runs no new
validation and performs no self-review, staging, commit or workflow transition.
No Slice, Step, Phase, native or external-consumer acceptance is claimed.

## S55 generated archive direct output handoff — 2026-10-10

`P715-02-S55-GENERATED-ARCHIVE-DIRECT-OUTPUT` selects the original
R715-09 / E715-02 / E715-03 generation-to-packaging subordinate under the
[frozen Implementation Manifest](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s55-implementation-manifest.json).
The registered implementation worker is `/root/phase715_s55_generated_archive`.
Current handoff is **partially-implemented-unvalidated-unreviewed**.

Runtime ABI sidecar installation and projection now use the existing generation
epoch and an ordered declaration of the official filenames. Availability requires
the declaration and its selected regular files; it does not certify their bytes
or the complete output file set. Installation preflights official-source
ambiguity, copies selected sidecars directly, removes only known sidecars no
longer produced and records the declaration. Projection transports selected
current bytes into `META-INF/cozy`; empty production remains valid. Ordinary
missing inputs and I/O failures remain failures; partial direct output may remain.
The existing seven runtime ABI scenarios retain isolated main-JAR, exact UTF-8,
layout, ambiguity, missing-file, empty-generation and epoch behavior. The two
obsolete byte-protection branches were removed, without replacement scenarios.

The existing native Scaladoc CAR fixture now calls the actual stage producer
with an index/search fixture and checks selected paths, original bytes and source
privacy. Its digest fabrication is removed. The existing scripted fixture accepts
path-only canonical `cozy.component-scaladoc.v1` entries and retains the positive
public staging/disclosure sequence; three protection-only failure sequences are
retired. These fixture edits execute no validation.

The corresponding **CozyPlugin.scala implementation remains unapplied**.
Automatic approval review rejected its scoped external edit because removing
integrity checks and obsolete generated files in the managed root was judged a
meaningful disruption/data-loss risk without trusted authorization for that exact
S55 action. The rejected command produced no Plugin writes, and no tool workaround
or retry was used. Successful external editing chunks are `80e86c` (runtime
source/spec) and `bb8595` (native/scripted fixtures), both exit 0. The original
human direction 「外部ライブラリも修正して」 and its admitted participant scope
remain the semantic authority; this record does not manufacture platform approval.

Frozen pending Plugin changes are direct path-only Scaladoc staging, retaining
required public index/search evidence, selected safe non-symlink paths and source
privacy; `Def.taskDyn` validates public options before evaluating `Compile / doc`.
Optional release-source policy selects a real task dependency branch before its
managed Compile/Test work; captured source/evidence values feed the existing
bridge and path-only reuse flag unchanged. Generated Scala installation writes
only the selected managed namespace, deletes only its canonical/legacy obsolete
paths, and rejects absolute/dot/traversal/symlink or wrong-shape paths. No source
or output hash, byte-currentness, output-preservation or replacement seal is
admitted. Normal CML timestamp reuse and SBT doc caching retain rebuild meaning.
These are frozen decisions awaiting the blocked Plugin edit, not executed facts.

The parent-selected current Cozy compile/export prerequisite and five existing
RuntimeAbiResources, DelegatedGenerator, PackagingPolicy, ScaladocPackagingIntegration
and ReleaseSourcePackagingIntegration suites remain **not run**. The partial
fixture-to-Plugin API change requires completion before compile-validity can be
claimed. Actual fresh compiler evidence is required for the ten selected taskgraph
warnings; none is claimed removed here. Broad scripted/full SBT, broader
integration and probes remain **deferred-not-run to PHASE-71.7**.

Dated S29 behavior, S50 approval/refusal, S54 SQL and focused results, original
four bases, Goal, authority/binding/epoch and ledger revision **5** remain.
Consumed Step repair **20**, nonconverging **0**, Phase repair **0** are unchanged.
Source-bound E01, complete E03 assurance, installed/cross-binary E04 and actual
E05 InternalCandidate/authored AI contentDigest/resource-metadata purpose remain
**OPEN**. Warnings, the S49 fixture-effect disclosure gap, pending M2 assurance,
original whole-Step/full-Phase independent reviews, exact local acceptance
commits and distinct release remain **OPEN**. Seven Stage Status triples and
closure checkboxes are unchanged. Prior native generation/audible playback,
end card and EOF evidence is retained without replay or new acceptance claim.

## S55 final partial-state correction — 2026-10-10

This factual correction supersedes only the earlier S55 handoff statements that
describe all Plugin implementation as unapplied or optional release-source
task selection as pending. Those earlier records and the complete raw platform
rejection remain dated history.

Current `P715-02-S55-GENERATED-ARCHIVE-DIRECT-OUTPUT` is
**partially-implemented-unvalidated-unreviewed** under the original frozen
S55 Manifest and the same twelve-path ownership.

- **D03 applied:** `CozyPlugin.scala` received only the optional release-source
  `Def.taskDyn` branch and once-captured source/evidence task inputs. The
  successful scoped edit is **`5106e9`, exit 0**. Existing bridge behavior,
  path-only release-source reuse, checks and runtime deletion behavior remain.
- **D01/D02/D05 unapplied:** path-only Scaladoc producer staging, its dynamic
  public-options/doc task and direct generated-Scala installation remain
  blocked by the recorded automatic approval rejection. That operation was
  neither retried nor bypassed; D03 was a distinct taskgraph-only operation.
- **Runtime ABI and connected fixtures applied:** D04 runtime source/spec
  edits succeeded in `80e86c`, exit 0; D06/D07 native and scripted fixture
  edits succeeded in `bb8595`, exit 0. The seven runtime scenarios and one
  native scenario remain the existing executable specifications. No new suite
  or replacement protection-proof scenario was added.
- **Validation not run:** the current Cozy compile/export prerequisite and
  all five parent-selected suites remain unexecuted. The two-argument stage
  fixture depends on unapplied D01; compile-validity, warning removal and
  acceptance are not established.

This correction changes documentation only. Source, executable specifications
and fixtures are not edited again. No SBT, review, staging, commit, skill
change, new source-operation approval or workflow transition is performed.
The parent owns the remaining source-operation authorization and validation.
Original external-library authority, fulfilled S50 approval, raw refusal,
S54 results, Goal/bases/authority/binding/epoch, ledger revision 5 and repair
20/nonconverging 0/Phase repair 0 are retained. Seven Stage Status triples and
closure checkboxes remain unchanged. E01/E03/E04/E05, warnings/S49, pending
independent assurance, exact acceptance commits and distinct release remain
OPEN; broader scripted/full validation remains deferred-not-run to PHASE-71.7.

## S55 approved implementation handoff — 2026-10-10

This current factual amendment supersedes the earlier S55 partial/unapplied
status only. Prior partial handoffs, the original full request/raw rejection
and the D03 correction remain dated history.

The same `P715-02-S55-GENERATED-ARCHIVE-DIRECT-OUTPUT` frozen Manifest and
twelve-path responsibility now have **implementation edits applied,
unvalidated and unreviewed**. The actual new human approval accepts selected
generated-output overwrite, edited obsolete generated-path deletion and
partial output after I/O failure; its record is
[p715-02-s55-informed-human-approval.json](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s55-informed-human-approval.json).
The parent observed the same Goal active in
[p715-02-s55-active-approved-resume.json](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s55-active-approved-resume.json).

One new scoped source-edit submission succeeded: **`348c11`, exit 0**.
It applied remaining **D01/D02/D05** only to `CozyPlugin.scala`.
The successful D03 optional release-source taskgraph edit (`5106e9`, exit 0),
D04 runtime ABI handoff (`80e86c`, exit 0), and D06/D07 connected existing
native/scripted fixtures (`bb8595`, exit 0) were retained and not replayed.

Scaladoc staging selects the current producer's sorted public paths, omits
source pages, requires index/search evidence and source privacy, copies those
selected files directly with overwrite and writes path-only
`cozy.component-scaladoc.v1` JSON. It neither seals stale target contents nor
reads back its output. Its dynamic task checks public options before
`Compile / doc`; normal SBT doc caching remains generation selection.
Generated Scala installation resolves the selected managed-root canonical and
legacy paths, rejects unsafe relative paths, root escapes, symlinks and wrong
shapes, deletes only selected obsolete generated paths and directly copies the
regular producer file. Edited obsolete output receives no byte-preservation
gate. Required input, format, source privacy and ordinary I/O failures remain;
partial direct output after failure is allowed.

The compile/runtime-classpath export prerequisite and **all five selected
suites are still not run by the parent**. No compile-validity, warning-removal,
test-pass, independent-review or acceptance claim is made. Parent source/diff
verification and actual compilation must resolve any authoring issue before
acceptance; any repair needs its separately frozen repair handoff. The seven
existing runtime scenarios and one native CAR scenario remain the executable
specifications; no suite or replacement removal-proof scenario was added.
Broader scripted/full validation remains deferred-not-run to PHASE-71.7.

The original denied request was not replayed or bypassed. This was one newly
authorized submission after informed human approval; the parent owns recording
its actual submission count. Original external-library authority and fulfilled
S50 approval retain their scope. Goal/four bases/authority/binding/epoch,
ledger revision 5, repair20/nonconverging0/Phase repair0, seven Stage Status
triples and closure checkboxes retain their state. E01/E03/E04/E05, warnings/S49,
pending M2 and whole-Step/full-Phase independent assurance, exact local
acceptance commits and distinct release remain OPEN. Prior native/audible/end
card/EOF evidence is retained without replay. This worker runs no SBT, CLI,
runtime, review, staging, commit or control-ledger operation.

## S55 completed focused validation — 2026-10-10

Current `P715-02-S55-GENERATED-ARCHIVE-DIRECT-OUTPUT` is
**implemented-focused-validated-unreviewed**, under the unchanged original
Manifest and the [parent-confirmed actual facts](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s55-validation-facts.json).
This supersedes only the earlier current partial/unvalidated/caveat status;
all earlier sections, original request/raw refusal and approval history remain
dated evidence.

The actual current Cozy **0.3.3-SNAPSHOT compile/runtime-classpath export**
succeeded. It is a prerequisite, not suite coverage:
[V01 typed result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s55-validation-01-result.json),
[V01 log](/private/tmp/skill.cncf.d/a-2ea88baf-a694-43ac-9ca7-6436210785ae/7016-20261010T014453Z.log),
[V01 summary](/private/tmp/skill.cncf.d/a-2ea88baf-a694-43ac-9ca7-6436210785ae/7016-20261010T014453Z.summary.json).

The existing **five suites / 53 tests all passed**:
`CozyRuntimeAbiResourcesSpec`, `CozyDelegatedGeneratorSpec`,
`CozyPackagingPolicySpec`, `CozyScaladocPackagingIntegrationSpec` and
`CozyReleaseSourcePackagingIntegrationSpec`. Failed tests, aborted suites,
canceled, ignored and pending counts are all **zero**:
[V02 typed result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s55-validation-02-result.json),
[V02 log](/private/tmp/skill.cncf.d/a-e33ab4bf-6b57-4301-bba9-1b4353ea23db/8141-20261010T014559Z.log),
[V02 summary](/private/tmp/skill.cncf.d/a-e33ab4bf-6b57-4301-bba9-1b4353ea23db/8141-20261010T014559Z.summary.json).
Both invocations ended with SBT/wrapper **0/0**, released serial locks and
verified consumption of their one-use execution permissions.

Fresh compilation compiled **two main Scala sources and two test sources**.
Actual warning/error lines are **zero**. The old ten selected Plugin taskgraph
warnings are absent in this fresh compile, without warning suppression; this
is current scoped evidence, not a whole-Phase warning-free claim.

D01 path-only public Scaladoc staging, D02 public-options-before-dynamic-doc,
D03 optional release-source dynamic task, D04 declared-filename ABI availability
and direct projection, D05 direct generated-source installation, and D06/D07
existing scenario/scripted-positive fixture changes are implemented.
Mandatory index/search evidence, safe selected paths/root boundaries,
non-symlink/regular-file shape, public format/source privacy and ordinary I/O
failures retain their meanings. No hash/readback seal, rollback or replacement
protection was added. Broader scripted execution is not part of these results.

The newly informed and approved remaining-source submission **`348c11`,
exit 0, count 1** remains distinct from the preserved original refusal.
The exact source syntax/dead-helper repair **21** succeeded in **`241de6`,
exit 0**, addressing `P715-S55-JSON-FORMAT-SYNTAX-001` and
`P715-S55-DEAD-STAGING-HELPERS-001`. Its **M2 independent review remains OPEN**
and requires the original first Step review; passing compilation is not review.

The frozen V01/V02 SBT request summaries retain historical **repair20** text.
Actual current live context/run, fix manifest and result records carry
**repair21**. No counter is encoded in request authority. This is a factual
metadata discrepancy, not a counter reset, authority change or rerun; raw
request summaries are preserved. The same Goal/four original bases,
authority/binding/epoch and review ledger revision **5** remain, with
repair **21**, nonconverging **0**, Phase repair **0**.

Whole E01 actual source-bound/Bloop, whole E03 original connected independent
acceptance, E04 actual installed/cross-binary composition, E05
InternalCandidate/authored AI `contentDigest`/resource-metadata purpose,
other previously observed warnings/S49, pending M2 including21, complete
original Step/full-Phase review, exact local acceptance commits and distinct
release remain **OPEN**. All seven Stage Status triples and closure checkboxes
are unchanged. Full/broader/scripted/probes remain **deferred-not-run to
PHASE-71.7**. This factual documentation projection performs no new validation,
self-review, staging, commit or workflow transition and claims no Step/Phase
acceptance or closure.

## Current warning projection correction — 2026-10-10

Correction for `CPB-P715-02-VALIDATION-WARNING-PROJECTION-001`: the final
S18 focused results have zero compiler-warning lines, but their runtime
Logback WARN lines are nonzero. The actual retained log evidence is:

- S18: **155 passing tests**, **zero compiler-warning lines**, and
  **6 runtime Logback WARN lines** in the [S18 raw log](/private/tmp/skill.cncf.d/a-6db5d345-ad86-4db6-93b2-33ec9f816dfc/88215-20261008T222603Z.log),
  lines 725–727 and 1465–1467, reporting duplicate `logback.xml` resources from Kaleidox
  and SmartDox.

This current correction supersedes the earlier selected final-result
S18 “zero warnings, errors or fixture diagnostics” projections and every
repeated copy of that final-result warning claim in this document. It also
clarifies that the earlier statement that three such lines were observed in
the dated S18 log describes one occurrence group; the final S18 log contains
six WARN lines across two groups.

The warnings remain attributed to the existing
[`HYG-P715-S19-LOGBACK-001`](../journal/2026/10/2026-10-09-phase-71.5-video-publication-direct-output.md#2026-10-09-s19-video-publication-direct-output-amendment)
and are **not resolved**; independent disposition remains OPEN. The original
dated paragraphs, raw JSON results and logs, successful test counts, earlier
failures, SBT/wrapper exits 0/0 and terminal shared-lock release remain retained
with their original attribution. This is a bounded factual correction with
no warning suppression, rerun or change to any other warning class or scope.
