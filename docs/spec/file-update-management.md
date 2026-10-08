# File Update Management and Digest Boundary

Status: approved direction; implementation and executable acceptance pending in
[Phase 71](../phase/phase-71.md) through [Phase 71.4](../phase/phase-71.4.md).
Decision: 2026-09-28.

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

Preflight errors preserve the prior final manifest. Final writing retains atomic
replacement and temporary cleanup; descriptor/carrier preparation before a later
error has no all-artifact rollback guarantee. The executable contract is
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
archive hash. Fresh release-source production validates authored path selection,
explicit managed-source/root operands, typed policy/license and safe build
evidence. Public staging copies selected bytes before calculating its named
payload entry SHA-256 and sorted content inventory digest; restricted staging
contains only its required manifest and performs no source-content hashing.

Explicit release-source and Scaladoc admission validates the supplied stage
independently of current authored/managed source bytes or build hashes.
Release-source admission retains exact safe output location and current declared
project policy agreement. Both loaders positively require their v1 schema and
safe ordered unique entry paths/checksums; release-source additionally requires
SourceCode identity, typed license/policy and safe string-array build evidence.
Unknown JSON properties at every object are ignored generically. CAR packaging
projects normalized manifest text from required semantic fields, preserves all
required nonhash metadata and leaves the supplied manifest untouched. Manifest
path provenance remains available separately from payload-only archive entries.

Named distributed entry and content inventory mismatch checks remain mandatory,
as do missing/extra/unsafe/symlink stage rejection, restricted no-payload policy,
and Scaladoc index/search evidence and source-disclosure guards. Staging failures
remove only the failed new stage; an existing stage is untouched. Actual admission
failures and ambiguous source/Scaladoc manifest replacement by CAR input payloads
reject before archive writing deletes a prior successful CAR. No additional
crash, concurrency or broad archive transaction guarantee is introduced.
Executable contracts are
[ComponentSourceArchiveProjectionSpec](../../src/test/scala/cozy/archive/ComponentSourceArchiveProjectionSpec.scala),
[ComponentReleaseSourceProjectionSpec](../../src/test/scala/cozy/archive/ComponentReleaseSourceProjectionSpec.scala)
and [CozyArchivePackagerSpec](../../src/test/scala/cozy/CozyArchivePackagerSpec.scala).
This bounded A06/A07 contract does not establish global audit, site, Step or
Phase completion.

## Local component API dependency selection

Cozy selects a required `apiClass` among the exact declared CAR dependency
coordinates, retaining actual `provided.version` and `artifactPath` projections,
assembly membership and missing/optional/ambiguous-provider checks. Ordinary
descriptor loading ignores unknown historical hash extras of any JSON type;
they supply no ABI compatibility guarantee. Repeated selected artifacts are
consolidated by exact dependency coordinate, normalized archive path and artifact
path in first selected order, after ambiguity checks. All selected API JARs are
extracted into a sibling stage before output replacement, preserving the prior
successful output on actual resolution or extraction failure. Installation
failure attempts restoration and preserves the original install error, with
restoration or empty-backup cleanup failures as suppressed diagnostics. A
recovery backup is reported only when restoration itself fails. After a complete
installation, failure to delete the prior backup emits an actionable warning
with its path and original cause, retains the remaining backup for cleanup, and
returns the successfully installed final paths. Successful empty optional
selection installs an empty directory. The executable contract is
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
