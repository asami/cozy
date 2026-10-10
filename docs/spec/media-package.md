# Media Package Specification

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
implemented, with focused and enabled native validation pending; S03 is not
accepted.
The representative native generation/final-assembly/audible-playback result,
including correct end card and EOF, and Step/Phase acceptance remain pending.
Document/media evidence and concrete external readers remain explicit
obligations on Phase 71.5's finite list. This amendment closes no Step or Phase,
claims no accepted native route and grants no protection exception. Repository-full
validation remains deferred-not-run to Phase 71.7.

## Purpose

A Media Package binds reproducible media representations to one BoK knowledge unit without coupling the source package to a particular website, archive directory, or publication service.

## Descriptor

The descriptor file is named `media.yaml`, `media.yml`, `media.json`, `media.conf`, or `media.xml`. The initial schema identifier is `cozy.media.v1`.

```yaml
schema: cozy.media.v1
knowledge:
  id: development-process/example
  type: documentmodel:KnowledgeUnit
  source: ../../../doxsite/development-process/example.dox
languages: [ja, en]
profiles:
  simplemodeling-org:
    root: ../../../../..
resources:
  - id: web-ja
    kind: image
    language: ja
    role: web-summary
    source: infographic/web-ja.svg
    output: infographic/web-ja.png
    build: svg-to-png
    width: 1600
    height: 900
    publications:
      simplemodeling-org: src/main/doxsite/development-process/images/example/summary-ja.png
```

Paths are resolved relative to the descriptor. Absolute publication paths are rejected. Machine-specific archive roots belong in profile configuration and not in a Media Package.

### Article PDF resources

`articleMedia.role` has two additive SmartDox PDF discriminators: exactly
`article_pdf` and `summary_slides_pdf`. Each is a `kind: document` resource
with `language: ja` or `en`, a direct explicit site-visible `articleMedia.publicPath`,
and `articleMedia.mediaType: application/pdf`; `articleMedia.label` is optional
but, when present, is a nonblank exact value. Cozy never infers a PDF role,
public path, or locale from a filename or path.

Only `build: article-pdf` is implemented in this step. Its `source` is exactly
the descriptor `knowledge.source`, its `output` is a declared direct regular
PDF destination contained by the descriptor root after normalization with no
existing symlinked ancestor below that root, and its closed configuration has exactly `latexFormat`,
`infographic`, and `renderer`:

```yaml
articlePdf:
  latexFormat: business # standard | business
  infographic: infographic-ja
  renderer:
    name: smartdox-pdf
    version: 2.4.18-SNAPSHOT
    command: [smartdox]
```

`infographic` is a nonblank exact resource ID. It names a distinct declared
resource with `kind: infographic`, `articleMedia.role: infographic`, a source,
and the same required `ja` or `en` locale as the article-PDF resource. That
resolved source must be a direct regular non-symlink file. This is an explicit
article-PDF authority binding, not an additional renderer operand.

The renderer command is the configured exact argv tokens followed without a
shell by `<source> --output <declared-output> --locale <ja|en> --latex-format
<standard|business>`. Cozy never passes the infographic path to the renderer.
Cozy validates the current source, infographic authority, paired site context
and selected output before invoking the renderer, which writes directly to the
declared output. It accepts only a zero exit and an actual direct regular
`%PDF-` output. The existing receipt-v2 automatic source evidence includes the
selected infographic authority without a receipt schema change. Renderer,
format or I/O failure creates no fresh receipt or successful build report;
partial or changed generated output may remain, and recovery is regeneration. Its
resource entry records the accepted relative output path and receipt evidence. An absolute output
is rejected during descriptor path resolution; an output whose normalized
effective destination traverses or otherwise escapes the descriptor root is
rejected with the stable Article PDF output-escapes-descriptor-root diagnostic;
and an output with an existing symlinked ancestor below that root is rejected
by Article-PDF descriptor validation. Each rejection occurs before renderer
invocation or output writing.

`summary_slides_pdf` is a declared public-resource role only in this step.
Its Visual Page/slide-IR conversion, page verification, and any internal PPTX
handling are a later Phase 40 handoff; this route neither converts nor exposes
a public PPTX.

### Receipt Evidence

`cozy.media.v1` remains the descriptor schema. Additive presentation fields preserve every legacy descriptor. A descriptor may add one strict optional `receipt` block and a profile may add a strict optional `presentation` block:

```yaml
receipt:
  inputs:
    - id: slide-ir
      role: slide-ir
      path: slides/normalized.json
      normalization: structured-document
    - id: presentation-template
      role: template
      path: slides/template.pptx
      normalization: bytes
  producer:
    profile: article-media-v2
    renderer:
      name: approved-presentation-renderer
      version: 1.2.3
```

The only receipt keys are optional `inputs` and optional `producer`. Each input has exactly `id`, `role`, `path`, and `normalization`; IDs and roles are non-empty trimmed identities, paths are descriptor-relative direct regular non-symlink files when evidence is captured, and normalization is exactly `bytes` or `structured-document`. Input IDs are unique. `cozy:` is reserved for Cozy automatic evidence and an authored input must not use that prefix. A producer, when present, has exactly `profile` and `renderer`; the profile and the renderer `name` and `version` are required exact identities.

Cozy records non-hash input metadata for the descriptor, knowledge source, declared
resource source/project and effective profile. Authored receipt inputs join that
metadata. Paths remain descriptor-relative; input evidence has only `id`, `role`,
`path`, `normalization`. `structured-document` still validates supported syntax,
but neither normalization mode computes a content identity.

Generation reuse uses producer input files and their actual FileTime through
`CozyFileUpdatePolicy`. A `copy` consumes only its direct resolved source.
A `prebuilt` consumes that source and, for a site-video resource, its explicitly
named `articleMedia.production` file; their times and the output time must not
exceed `acceptedAt`. Knowledge, unrelated sources, receipt declarations,
profiles and site configuration remain package-wide informative evidence and
current-command validation inputs, rather than copy/prebuilt conversion inputs.
Other producers retain their existing input selection in this Slice.
Active builds capture and validate declared inputs and producer metadata once
before resource writes, then carry those input/producer facts into acceptance
after actual output verification. Dry runs do not capture inputs. Builds do not
compare inputs across rendering. Publication still observes paths and FileTime
in memory across preparation and installation; that publication protection
remains explicitly unfinished Phase 71.5 work, not an exception.

Every newly accepted entry in `target/cozy-media/manifest.json` has `id`,
relative `path`, a `cozy.media.receipt.v2` receipt and optional presentation
`artifacts`. The manifest remains `cozy.media.v1`. The receipt has sorted input
metadata, producer metadata, the build operation with selected target/profile,
UTC ISO-8601 `acceptedAt`, and `{status:"valid",findings:[]}` verification.
Stored manifests, entries, receipts and input evidence load their known typed
fields directly, ignoring arbitrary extra stored properties without parsing or
re-emitting them. Descriptor configuration remains closed and strict. Known
schemas, types, IDs, ordering, verification, UTC timestamps and paths remain
validated. No accepted primary-output digest is emitted or compared.

`current` is a read-only producer-freshness Boolean. `evidenceCurrent` requires
matching knowledge/resource/output identity, a valid receipt, the selected
automatic source/project path mappings and the paired site context/path mappings
when required by the accepted receipt. It does not compare whole historical
input sets. `currentnessFailure` combines those gates and reports the concrete
reason, media receipt currentness stage, ID, actual failing input/output or
manifest path, producer kind, and `cozy media build <descriptor> --target <id>`.
Verification and receipt preflight use that diagnostic through `requireCurrent`.
PDF output headers are validated for generated PDFs and document resources with
`article_pdf` or `summary_slides_pdf` roles, including copy outputs.
A valid byte rewrite with deliberately preserved timestamps has no promise of
digest detection.

Acceptance prepares review states and receipts before writing and admits all
normalized selected targets, non-symlink parents/direct regular targets, and
distinct non-aliased target ownership before any write. It creates each required
parent and writes prepared review states directly in order, then the final
receipt. Input/format/preparation failures before writes leave acceptance records
untouched; failure to create the first review-state parent also precedes receipt
writing. An I/O failure during writes may leave earlier or partial documents,
including the receipt if its write starts, and reports no successful acceptance.
No rollback, multi-file atomicity or prior-record preservation is promised;
recovery is regeneration. Dry runs write no fresh receipt.
Target builds preserve unselected entries without reaccepting them. Explicit
prebuilt build adopts valid output even when the read-only plan reports current.
Real source-to-staged-to-destination publication byte integrity, safe paths and
atomic installation remain required. Presentation subordinate `Artifact`
records contain only `id`, `role`, and `path`. Current artifact evidence compares
these typed ordered records with the actual structurally verified generation;
it uses no persisted output or source content identity.

### PDF review currentness state

`target/cozy-media/pdf-review-state.json` is a package-owned, receipt-derived
currentness document with the exact closed schema
`cozy.media.pdf-review-state.v1`. It has no descriptor configuration and is
prepared from the same merged candidate `cozy.media.v1` manifest that a normal
`cozy media build` accepts. Acceptance writes it directly before the final
receipt through the ordered shared writer, with ordinary I/O failure and possible
earlier or partial documents. An absent state
target is created only when the candidate has at least one current qualifying
PDF resource; an existing target must be a direct regular non-symlink file.

The saved object has `schema`, `knowledge`, and `resources`, ordered by
`(role,id)`. Each resource saves only `id`, `role`, `language`, `publicPath`,
`mediaType` and relative `output`. Known typed fields load directly with valid
schema/knowledge, role and locale enums, `application/pdf`, safe paths and unique
IDs; arbitrary extra stored properties are ignored. No whole-JSON comparison or
state/output/renderer digest is used.

`requireCurrent(plan, resources)` checks only the selected qualifying PDFs.
It requires their ordinary receipt currentness, a valid direct state file and
matching knowledge, then matches each selected ID's known metadata to the
resolved resource and accepted entry. A stale unselected Japanese PDF cannot
veto unchanged selected English verification or publication preparation.
`current(plan)` checks all qualifying PDFs through the same logic and does not
gate a smaller selection. Missing or altered selected metadata reports PDF
review currentness, resource ID, actual state/output path, producer kind and the
next build operation.

Direct `summary-slides-pdf` resources require current dependency receipts and
the nonhash page count/order/image/renderer structural verifier specified below.
This direct renderer subsystem is migrated under S07.
Prebuilt summaries require their ordinary receipt and known state metadata;
they do not opt into direct structural verification. Candidate acceptance uses
the merged manifest without requiring prior on-disk receipts or freshness
against just-generated outputs. A stale unselected direct summary is omitted
from a target article refresh rather than accepted again.

Normal verify and publication preflight require this state only for selected
qualifying PDFs. Presentation-only slide build remains independent. The state
performs no registration, publication or semantic/visual/audiovisual approval;
existing presentation/video review and cross-review schemas are unchanged.

`prebuilt` uses explicit adoption of its current regular-file output, retaining
the consumer's existing safe in-root symlink support. A prior
receipt/input mismatch is not an adoption veto, even when output bytes and mtime
are unchanged. A normal explicit build records current input evidence for the
selected resource without regenerating that external output. Planning remains
read-only and distinguishes Current from AdoptPrebuilt; missing source remains
MissingSource. Target builds do not reaccept unselected resources.

Site-bound article media retains current file/descriptor validation independently
of historical receipt equality. Article and summary PDF outputs require a PDF
header. Infographic outputs declared as `image/svg+xml`, or with an `.svg`
public path and no media type, require well-formed XML with an SVG root;
other infographic outputs retain the PNG header check. SVG parsing does not
resolve external entities. This generic adoption does not change the separate
Phase 26 PNG-only publication contract. A video requires a direct
non-symlink MP4 path and direct `articleMedia.production` JSON with matching
article identity and language, completed render and passed technical-and-visual
QA. These known identity/status/QA fields are loaded directly; arbitrary extra
production properties do not veto adoption. Updated valid production metadata
is allowed. Real distribution byte integrity remains a separate consumer contract. No previous/current receipt equality, duplicated
production-metadata equality, or unchanged `final-summary-slides-pdf` proof is
required for adoption. Historical source-authority/video-manifest proof fields
are not adoption prerequisites. Missing or malformed required inputs, invalid paths or
formats, and invalid current video production fail without replacing prior
acceptance. Publication input-race and destination-integrity checks are unchanged.

### Presentation

Presentation resources use `kind: presentation` and `build: presentation`. They require a language, versioned slide IR source, PPTX output, a publication entry for the selected presentation profile, and this exact nested resource object:

```yaml
presentation:
  profile: business
  slideImages: target/cozy-media/slides
  montage: target/cozy-media/montage.png
  rendererManifest: target/cozy-media/renderer-manifest.json
  reviewManifest: target/cozy-media/review-manifest.json
  reviewState: review/state.yaml
  articlePdf: article-pdf-ja
  infographic: infographic-ja
```

The referenced article PDF is a declared non-presentation document resource with a publication for `business`; the infographic is a declared non-presentation image resource and, when it declares a language, uses the presentation language. Neither dependency may be the presentation itself. The profile object is exactly:

```yaml
presentation:
  template: presentation/template/business.pptx
  renderer:
    name: approved-presentation-renderer
    version: 1.2.3
    command: [approved-presentation-renderer]
```

`template` resolves descriptor-relative and must be a direct regular non-symlink file. `command` is a nonempty vector of nonempty exact argv tokens. Cozy invokes it without a shell, in the descriptor root, with the configured tokens followed by `render --media <absolute descriptor> --target <id> --profile <profile> --slide-ir <absolute source> --template <absolute template> --pptx <absolute output> --slide-images <absolute directory> --montage <absolute path> --manifest <absolute renderer-manifest>`.

The slide semantic source is YAML or JSON with schema `cozy.slide-ir.v1`. Its exact top-level keys are `schema`, `knowledge`, `language`, and `slides`; slide keys are `id`, `title`, `layout`, and `elements`; element keys are `role` plus exactly one of `text` or `asset`. Layouts are `title`, `section`, `content`, `comparison`, `figure`, and `summary`. Roles are `title`, `subtitle`, `body`, `bullet`, `figure`, `caption`, `source`, `footer`, and `logo`. `figure`/`logo` use only assets; all other roles use only text. Slide IDs match `[a-z0-9][a-z0-9._-]*`, are unique, and each slide has exactly one title element equal to its title. Inputs reject empty or untrimmed identities, coordinates, unknown fields, missing assets, and `TODO`, `TBD`, `PLACEHOLDER`, `FIXME`, or `{{...}}` text. The IR knowledge/language match the descriptor knowledge/presentation language and it must reference the configured infographic.

The renderer writes `cozy.presentation.render.v1` with required top-level
`schema,target,profile,renderer,pptx,slides,montage`. The profile is exactly
`business`; renderer requires `name,version`; PPTX and montage require `path`;
each slide requires `id,title,path,assets`, with ordered unique `{id}` assets.
Readers select the known fields positively at every level and ignore arbitrary
unspecified metadata, without a named historical-field mask. Missing, ill-typed,
wrong, duplicate, or reordered authority IDs still reject. All serialized paths
are descriptor-relative forward-slash paths. No Presentation input, template,
output, slide, montage, article-PDF, infographic, renderer or review digest is
calculated or required. Cozy checks actual output structure; securely parses
`[Content_Types].xml`, `ppt/presentation.xml`, presentation and per-slide
relationships; derives relationship-ordered contiguous slide parts; and binds
each slide's image relationships to existing linked ZIP entries. Selected asset
paths still resolve to safe direct files. It does not read selected asset or
embedded image bytes for copy agreement, image cardinality or multiset equality.
These transient structural checks preserve every existing relationship,
slide-order, XML, linkage and packaging gate. Hostile XML, renderer failure,
incomplete output, malformed required evidence, unsafe paths and
overlap still reject. Authored IR and descriptor configuration stay closed.

### Summary-slides PDF

A `summary_slides_pdf` document resource may remain `build: prebuilt` for an
already-produced direct PDF. Its only new generation route is
`build: summary-slides-pdf`; it requires a `summarySlidesPdf` object with the
closed common keys `contract`, `profile`, `slideImages`, `montage`,
`rendererManifest`, `articlePdf`, and `infographic`, plus optional `pptx`.
`slide-ir-v1` uses the common keys; `visual-page-v1` additionally requires
`catalog` and `binding`. The profile is exactly `business`, `articlePdf` and
`infographic` are current declared resources, and the source is respectively a
current Slide-IR or Visual Page authority. The direct route invokes the existing
presentation renderer/template with the declared output in `--pdf` and writes
that primary directly. One pre-render admission requires the selected actual
inputs and dependency files to be direct regular files. Cozy validates the
actual PDF header, PDFBox page count and required renderer evidence.

Both renderer schemas require the relative root field order
`schema,target,profile,renderer,pdf,slides,montage[,pptx]`:
`cozy.summary-slides.render.v1` for Slide-IR and
`cozy.summary-slides.render.v2` for Visual Page. Readers positively select
required known fields and ignore arbitrary extra object metadata at every
level, without a named legacy mask or content identity. Missing, null or
ill-typed required fields reject. Renderer `name,version` must exactly match
the current profile. `pdf` requires only a positive integer `pageCount`, with
no required path, and must agree with both the actual PDFBox page count and
the current authority's page count. Slide-IR slides require
`id,title,path,assets`; Visual Page slides require `id,path,assets`. Slide/page
IDs must follow the unique authority order; v1 titles must match. Each asset
requires a typed exact `id` in unique authority order. The montage requires
`path`. A configured PPTX requires `path`; an unconfigured `pptx` field is
forbidden even though arbitrary other metadata is ignored. Authored
`summarySlidesPdf` configuration retains its closed grammar.

Actual current Slide-IR/PageSet/catalog/Binding loading remains required.
The primary output must be a direct regular PDF with a PDF header and a
successful PDFBox parse. Slide images must be safe direct regular files below
the configured image directory with the existing PNG signature, and the
montage must use its configured safe direct PNG path. The optional PPTX is an
internal direct-file sidecar at the configured path; this contract adds no
OOXML or render-equivalence promise and no public resource or registration
identity. Visual Page assets resolve safely from the actual PageSet parent;
the page asset paths and selected declared resource paths are admitted as safe
direct files without comparing copied bytes.
Input/output collisions, unsafe descriptor-relative spellings, missing direct
inputs and symlink paths continue to reject.

Rendering records no operation-local input observations and makes no
before/after input comparison. Ordinary dependency/modification-time reuse
remains. Malformed PDFs, wrong actual page counts and invalid required current
structure reject independently of historical content identity.

The primary PDF, renderer manifest, PNGs, montage and optional PPTX are direct
outputs. Renderer, structure or I/O failure creates no fresh acceptance or
successful build report and may leave partial or changed generated outputs;
no atomic install, cleanup, prior-primary preservation or rollback is promised.
Recovery is regeneration. Building either the prebuilt
or direct route does not imply BoK publication or SmartDox registration.

The SummarySlidesPdf renderer subsystem is migrated under S07. ProducerSource
and BoK metadata, generated rsync, final named-integrity audit, EnglishPDF and
complete isolated-site proofs remain separately unfinished. This migration
claims no Phase inventory, Step or Phase closure, or integrity exemption.
S09 removes direct Article/Summary PDF output protection and immediate active-build
input observations. On 2026-10-08 its six existing focused suites completed with
114 successes, zero failed/canceled/ignored/pending cases, zero aborted suites,
zero warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S10 removes Presentation copied-image byte/multiset checks and replaces shared
acceptance staging/backup/rollback with ordered direct review-state then receipt
writes after initial target admission. On 2026-10-08 its six existing focused
suites completed with 113 successes, zero failed/canceled/ignored/pending cases,
zero aborted suites, zero SBT warnings/errors, SBT/wrapper exit 0, and terminal
shared serial-lock release. Five hostile-XML/DOCTYPE diagnostics are intentional
existing negative-fixture output, not SBT compilation or test failures.
S11 implements direct PresentationMigration and CrossReview output writes and
direct absent-destination article scaffold creation; its five-suite focused
validation completed on 2026-10-08 with 58 successes, zero
failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S11 emitted five intentional hostile-XML/DOCTYPE negative-fixture diagnostics,
separate from SBT compilation or test failures. S12's current JA native route is
planned/not-run; generation, assembly, native inspection, browser EOF and current
listening remain OPEN ([journal](../journal/2026/10/2026-10-08-phase-71.5-ja-main-route.md)).
Receipt publication/prepared-input observations
and remaining finite publication/runtime/CAR groups remain unfinished, as do
independent Step/Phase acceptance, the same full 34-scene native route and external
E715-01..04 reconciliation. No whole-Phase closure is claimed.

`target/cozy-media/manifest.json` retains its `cozy.media.v1` top-level shape.
A resource may add `artifacts`, whose emitted records contain exactly
`id,role,path`. Ordinary readers require these typed fields and ignore arbitrary
extras. Presentation evidence includes slide PNGs, montage, renderer manifest
and the Cozy review manifest. Non-Presentation resources omit `artifacts` rather
than writing an empty field. Safe paths, unique identities and roles, ordered
artifact correspondence, producer/dependency admission and actual modification
time/generation-companion currentness remain required.

After renderer verification Cozy deterministically writes
`cozy.media.presentation-review.v1` before receipt acceptance. Its required
fields are `schema,target,knowledge,language,renderer,slideIr,template,pptx,
slides,assets,montage,articlePdf,infographic,verification`. `slideIr`, `template`
and `pptx` contain `{path}`; slides contain `{id,title,path}`; material assets
are ID-sorted `{id}`; montage contains `{id,role,path}`; article PDF and
infographic contain `{id}`. Renderer retains `name,version` and verification
retains `status:valid,findings:[]`. Verification reconstructs those required
semantics recursively, selecting only known fields from stored objects and
preserving array order. Wrong titles, paths, assets, renderer or status reject
even at an unchanged modification time. Arbitrary extras are ignored at every
object without parsing or re-emitting them. No historical byte identity replaces
removed hashes. AI/Codex remains responsible for semantic comparison; Cozy never
self-approves alignment. The scaffold-only
`cozy.media.presentation-review.scaffold.v1` marker retains its closed
`schema,target,knowledge,language,status:not-rendered` grammar and is replaced
by verified evidence before receipt acceptance.

`review/state.yaml` uses `cozy.media.review-state.v1` and exact keys `schema`, `current`, `last_aligned`, `selected_authority`, and `sync`. Snapshots require only UTC ISO-8601 `acceptedAt`; arbitrary snapshot extras are ignored and never re-emitted. The top-level state grammar stays closed. A successful presentation build refreshes `current` with the verified review manifest's modification time (the last-written generation boundary); it preserves `last_aligned` and `selected_authority`. Alignment remains valid only when the generation is no newer than the explicit alignment time. Unchanged reuse preserves alignment; regeneration after alignment becomes stale even when bytes are identical. `cozy media review align` records the current human/AI decision time and selected authority. Ordinary media verification and slide verification require an aligned state and a review manifest no newer than `current`. Actual output integrity and structural verification remain separate; no aggregate content identity is calculated for alignment.

### Visual Page coexistence boundary

This accepted presentation contract remains the `cozy.slide-ir.v1` source,
`--slide-ir` renderer argument, and `cozy.presentation.render.v1` receipt
route. It is unchanged by the implemented discriminated contract in
[`visual-page.md`](visual-page.md); no existing `cozy.media.v1` presentation
resource, receipt, or review state silently opts into that route.

The preceding legacy `presentation` object is an untagged closed object with
exactly `profile`, `slideImages`, `montage`, `rendererManifest`,
`reviewManifest`, `reviewState`, `articlePdf`, and `infographic`. It never
permits `contract`, `catalog`, or `binding` and never infers a new route from
its source or profile.

The following separately closed discriminated Visual Page presentation object
is implemented. Its resource `source` remains required and is
the VisualPageSet input; all legacy artifact and dependency fields retain their
existing meanings.

```yaml
presentation:
  contract: visual-page-v1
  profile: business
  catalog: presentation/catalog.json
  binding: presentation/binding.json
  slideImages: target/cozy-media/slides
  montage: target/cozy-media/montage.png
  rendererManifest: target/cozy-media/renderer-manifest.json
  reviewManifest: target/cozy-media/review-manifest.json
  reviewState: review/state.yaml
  articlePdf: article-pdf-ja
  infographic: infographic-ja
```

This object is closed with exactly the shown keys; `contract` is exactly
`visual-page-v1`, and `source`, `catalog`, and `binding` are safe descriptor-
relative direct regular non-symlink files under the descriptor root. Each
rejects an empty, absolute, traversal, URI-like, control-character, or
backslash path; normalization changes; symlink escapes; and a missing or
nonregular resolved file. `source` must parse as exactly
`cozy.visual-page-set.v1` with integer version `1`. Its VisualPageSet pages
must declare one shared catalog `{id, revision}` pair, and resolution accepts
exactly one supplied resolved catalog matching that pair;
a mixed pair, unresolved catalog, or mismatch fails closed. It never permits
the untagged legacy shape or `--slide-ir` inference. Its `profile` remains the
exact existing template/renderer object. Its fixed renderer argv is the legacy
fixed argv with `--slide-ir <source>` replaced by `--visual-page-set <source>
--catalog <catalog> --binding <binding>`.

The renderer manifest is `cozy.presentation.render.v2` with required relative
field order `schema,target,profile,renderer,pptx,slides,montage`. Renderer is
`{name,version}`, PPTX and montage are `{path}`, slides require ordered
`id,path,assets`, and assets require `{id}`. Positive selection of known fields
ignores arbitrary extras at all those levels. Actual named
PageSet/catalog/Binding validity, required page IDs/order/assets, output paths,
PPTX relationship/media structure, linked ZIP image existence, safe direct selected
asset paths and PNG validity remain, without copied-image byte or cardinality
agreement. Renderer files are direct outputs. Invalid required renderer evidence
rejects before acceptance writes, leaving accepted review-state/receipt records
untouched. Shared acceptance writes prepared review states directly in order,
then the receipt, after initial target/alias admission; I/O failure may leave
earlier or partial documents and reports no success. There is no atomic
installation, rollback or prior-record preservation guarantee.

The deterministic `cozy.media.presentation-review.v2` wrappers for
`visualPageSet`, `catalog`, `binding`, `template`, and `pptx` contain `{path}`.
Its required root fields are `schema,target,knowledge,language,renderer,
visualPageSet,catalog,binding,template,pptx,slides,assets,montage,articlePdf,
infographic,verification` in their existing relative order. Slides are
`{id,path}`; assets are `{id,path,mediaType}`; the other records use the same
nonhash grammar as v1. Recursive positive known-field projection verifies
actual required values and array order, including nested paths and media types.
Private evidence contains only `(id,assets)` pages
and `(reviewPath,pages)`. No complete semantic JSON replacement gate exists.
Renderer argv is unchanged and external renderer implementation/acceptance is
not claimed. Artifact/review readers emit no hashes or complete semantic copies.
Timestamp-preserved valid edits are admissible and are not promised historical
content detection. Actual descriptor/source/catalog/Binding files remain
required. The SummarySlidesPdf renderer subsystem is migrated under S07.
Publisher/rsync, the final named-integrity audit,
English-PDF and complete isolated-site drivers remain pending; this contract
claims no Step or Phase closure.

### Cross-media review

The closed Cross-media Review route proves that one current normalized Visual
Page is the common semantic input of an already verified Visual Page
presentation and an already verified Storyboard v2 visual-page review. It is
not a renderer, a video build, a publication route, or an approval operation.
Its exact commands are:

```text
cozy media cross-review build <media-file> --target <presentation-id> --video-project <project-file> --save <output.json>
cozy media cross-review verify <media-file> --target <presentation-id> --video-project <project-file> --cross-review <input.json>
```

`target` selects exactly one current `presentation` resource whose closed
`presentation` object has `contract: visual-page-v1`. The route requires that
resource's current receipt and deterministic
`cozy.media.presentation-review.v2` manifest, but it does not require or
create a semantic-alignment approval. `video-project` must have a current
Storyboard v2 review-evidence/handoff package; Cozy revalidates the project's
declared current Storyboard, literal screen references, VisualPageSet/catalog/binding,
selected assets, and effective renderers before accepting it as an input.
Neither a stale presentation review nor a malformed, missing-input, or
wrong-schema Storyboard package is a Cross-media input.

The build output is the canonical `cozy.media.cross-review.v1` object:

```text
schema,target,slides,visualPages,verification
```

Slides contain `{id}`; visual pages contain
`{sceneId,pageId,assets}` with `{id,mediaType}` assets. Ordinary readers select
known fields at Presentation wrappers/slides, Storyboard evidence/handoff and
visual-page entries, and aggregate root/slide/page/asset/verification objects.
Unspecified properties of any JSON type are ignored generically, preserving
existing relative known-key order. Generated output is deterministic and omits
hashes and copied PageSet/catalog/Binding proofs. CrossReview computes no digest and performs no duplicate
Storyboard identity check; the video-owned review-input API validates actual inputs
and record structure without digest-based approval or historical whole-payload
gates. Current per-side page references must resolve, and declared ordered unique
asset IDs/media types must agree; each side resolves its safe actual selected files without cross-root
asset-byte equality. No complete
catalog/page/Binding correspondence gate replaces the deletion.

Verification checks actual saved schema, required fields, IDs, uniqueness, and
references rather than whole-record equality with reconstructed current JSON.
Build constructs once, validates inputs before writing, and directly writes the
canonical aggregate JSON plus newline to an admitted direct regular target.
Paths remain contained,
normalized, and non-symlinked; actual inputs and participating review evidence
must never be overwritten. Invalid inputs and ownership/path failures before
writing preserve prior aggregate bytes. Ordinary write failures report the
Cross-media Review diagnostic without success and may leave partial output;
there is no atomic installation, cleanup or prior-output preservation promise.
Verification remains read-only and checks actual format and references.

`verification` is exactly
`{status:"valid",semanticApproval:"not-recorded",visualApproval:"not-recorded",audiovisualApproval:"not-recorded"}`.
This records no semantic, visual, or audiovisual approval and invokes no renderer.
Media receipt admission and non-review artifact integrity remain separate
responsibilities. Removed historical snapshots cannot
serve as substitute local currentness proof.

## Commands

### Video-project production

For a `video-project`, package `output` must equal the Video-resolved final
output. Both current actions and delegated generation enter Video's normal
Media producer, so reusable MP4s still receive canonical current credit
companions. Video owns the actual dependency closure and per-part rendering
decision under [file update management](file-update-management.md). Package
knowledge/descriptor/site/publication inputs are independently validated once
before resource writes without implicitly rerendering unchanged Video inputs.
Only explicitly authored receipt inputs are extra Video producer operands.

Newer render inputs or missing/invalid credit semantics run the selected
Remotion/simple-java2d renderer before final assembly. Valid existing parts may
be assembled when only the final is missing; unchanged builds refresh the
three credit companions without rewriting MP4s. Required profile/audio/asset
failures remain actionable failures. Credit verification checks selected
domain fields, obligations, presentation and required Markdown attribution,
allowing harmless extras and descriptive path relocation. Native Media build
uses final mode; standalone Video assembly and explicit confirmation/review
remain separately owned. Selected generated-output backups preserve prior
bytes/mtimes/existence on modeled in-process producer failure.

That preservation extent is Video production and its declared producer inputs,
including explicit receipt operands. Media carries its initial input/producer
capture through actual output verification into receipt acceptance, with no
package-wide race comparison. Shared acceptance uses ordered direct review-state
then receipt writes after initial target admission; a write failure may leave
earlier or partial documents and reports no success.

- `cozy media inspect <media-file>` describes identity, resources, and resolved paths.
- `cozy media plan <media-file> [--target <id>] [--profile <name>]` reports build and publication actions without changing files.
- `cozy media build <media-file> [--target <id>] [--dry-run]` performs deterministic resource builds, including configured business presentation and receipt acceptance.
- `cozy media verify <media-file> [--target <id>] [--profile <name>]` checks sources, outputs, dimensions, publication equality, and current v2 receipt evidence.
- `cozy media publish <media-file> --profile <name> [--target <id>] [--dry-run]` rejects stale or v1-only evidence before copying verified outputs to profile destinations.
- `cozy media slide validate|plan|verify <media-file> [--target <id>] [--profile business]` selects business presentation resources only.
- `cozy media slide build <media-file> [--target <id>] [--profile business] [--dry-run]` renders business presentation resources only.
- `cozy media cross-review build|verify <media-file> --target <presentation-id> --video-project <project-file> --save <output.json>|--cross-review <input.json>` creates or exactly verifies structural Cross-media Review evidence without performing a render, publication, or acceptance decision.
- `cozy media review align <media-file> --target <presentation-id> --authority <article|slide-ir>` records an explicit semantic-alignment decision after deterministic verification.
- `cozy media scaffold article <slug> --profile business --language <tag> --save <dir>` directly creates an absent source package destination and never merges or overwrites an existing destination. Slug, business-profile and language grammar and the existing-parent requirement are checked first. The same authored payloads, review state and subdirectories are written in their established order. An ordinary creation/write failure reports no success and may leave a partial newly created package; there is no staged installation or failure cleanup promise.

`cozy media presentation migrate` validates its strict source, semantic map,
catalog, complete embedded PageSet and exact bijective bindings before directly
writing canonical PageSet JSON plus newline to the admitted `.json` output.
Invalid pre-write inputs preserve existing output bytes and emit no success
report. Ordinary writing failures retain `MIGRATION_SAVE` diagnostics, may leave
partial output and emit no successful migration report. Recovery is regeneration;
there is no atomic replacement or cleanup guarantee.

Initial build kinds are `svg-to-png`, `copy`, `prebuilt`, `video-project`, `presentation`, `article-pdf`, and `summary-slides-pdf`. A `prebuilt` resource is generated by a separate deterministic producer and is verified and published without being rebuilt. Video resources use `prebuilt` after separately owned video production, or `video-project` for normal Video-owned rendering/reuse and final assembly. A `video-project` resource must declare the same package-level `output` path as Video resolves so `verify` and `publish` can check the generated media artifact. Cozy is an asset/schema/verifier/receipt orchestrator, not a PowerPoint layout engine; article `.dox` and its PDF, editable infographic SVG, and semantic slide IR remain their respective sources of truth while a PPTX is only an explicitly requested internal presentation sidecar.

## Source Control Boundary

Authored descriptors, briefs, scripts, SVG masters, and publication PNG inputs may be versioned. Audio, renderer caches, intermediate videos, and final large video artifacts must be generated outside the source package or below ignored target directories.

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

## 2026-10-09 S33 presentation production and explicit review facts

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is
**implemented-unvalidated-unreviewed** at this initial 2026-10-09 editing
handoff, within original **R715-03/04/14/15**. The four parent-selected existing
suites `cozy.media.CozyMediaPresentationSpec`,
`cozy.media.CozyMediaPdfReviewStateSpec`, `cozy.media.CozyMediaCrossReviewSpec`,
and `cozy.media.CozyMediaSpec` are **pending**. Validation, parent admission and
independent review are not claimed.

The [actual S32 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s32-focused-validation-result.json)
records **48 successes across four completed suites**, separately dated
**2026-10-09** and attributed to S32. It does not validate or accept S33.
Other original finite obligations, including R715-03/04/14/15 remainders and
concrete external **E715-01..05**, remain **OPEN**. Final current **JA34 native
generation, assembly, audible hearing, end-card and EOF**, independent
**M2 cycles 6/7/8/10**, whole-Step/full-Phase reviews and acceptance, exact local
Step commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**. Existing base,
authority, epoch, repair counters, stage/aggregate metadata and prior history
retain their state; this authoring handoff makes no workflow transition.

This normative amendment supersedes the earlier presentation review-state
promises that use the review manifest's modification time, generation/decision
date ordering, or a review-manifest date no newer than `current` to establish
approval or currentness. It applies to both the legacy slide-IR and
`visual-page-v1` presentation routes.

An ordinary media build that selects the existing `Action.Current` presentation
reuses its generated output and retains the recorded semantic review state
when `current` exists, including the existing explicit decision. An actual
successful render, including the explicit slide-build route, records the new
acceptance observation time as UTC ISO-8601 `acceptedAt`, preserves
`last_aligned` and `selected_authority`, and sets `sync` to `stale`. A first
acceptance without a current record also requires explicit alignment. Failed
or rejected rendering never records acceptance. The execution branch supplies
this production fact; no file equality or timestamp comparison establishes it.

Explicit alignment first requires the ordinary current presentation validity
checks and an existing `current` record. It records the actual decision time,
selects `article` or `slide-ir`, and sets `sync` to `aligned`. An aligned state
requires `current`, `last_aligned`, and `selected_authority` plus `sync: aligned`.
Date fields record acceptance and decision facts; they do not prove current
approval, unchanged content, or race-free execution. No temporal ordering is
required to load those facts. The exact root grammar and required UTC string
type/syntax remain, with arbitrary snapshot extras ignored generically.

The ordinary presentation verifier still checks actual renderer/review
schemas, semantic IDs and mappings, selected paths, PPTX/OOXML structure,
relationships, legacy titles, and PNG format. Ordinary dependency existence
and modification times continue to select rebuild/reuse; they are not approval
or race evidence. Direct writes, acceptance order and ordinary input, output,
reuse and I/O failures retain their existing boundaries. The obsolete
review-state refresh seam and timestamp approval comparisons are removed.

The existing presentation scenarios specify actual reuse, explicit rendering,
and explicit re-alignment on both routes, plus retained XML/relationship/title/
PNG/page-mapping failures and arbitrary annotations. The existing PDF review
fixture emits the canonical summary-renderer schema, target/profile/renderer,
PDF page count, slide IDs/titles/paths and asset IDs, and montage path; retired
digest transport and protection-field absence expectations are removed.

See [the frozen S33 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-implementation-manifest.json) and [the dated implementation handoff](../journal/2026/10/2026-10-09-phase-71.5-presentation-explicit-review-facts.md). At the initial handoff, selected validation and independent review remained pending.

### Actual S33 focused validation failure — 2026-10-09

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is currently
**implemented-focused-validation-failed-unreviewed**. The initial
**implemented-unvalidated-unreviewed** handoff and its pending-suite record
remain dated history. The [actual failure result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-focused-validation-failure01.json)
and [raw execution log](/private/tmp/skill.cncf.d/a-ede4e1d9-7928-4d8e-9170-635b2dbaa193/73204-20261009T043126Z.log)
record the first group, `cozy.media.CozyMediaPresentationSpec` plus
`cozy.media.CozyMediaPdfReviewStateSpec`: **31 tests, 30 succeeded, 1 failed,
2 suites completed**, with **0 aborted, canceled, ignored or pending**.
The second group, `cozy.media.CozyMediaCrossReviewSpec` plus
`cozy.media.CozyMediaSpec`, was **NOT RUN** after the first group's failure.
There is no four-suite success or acceptance result.

Implicit compilation completed for **3 Main and 2 Test Scala sources**, with
**zero compiler warnings**. The **three existing duplicate `logback.xml`
runtime WARN lines** remain under **HYG-P715-S19-LOGBACK-001**, unsuppressed and
pending independent classification. Separately, the hostile-XML negative
fixture emitted **DOCTYPE disallowed Fatal Error on stderr**; that diagnostic
is retained unsuppressed, with independent classification still open. Zero
compiler warnings does not establish clean runtime diagnostics.

The failed existing scenario is **preserve explicit alignment on ordinary
reuse and require realignment after explicit rendering**. Reuse and the stale
review-state facts were reached successfully. After `_slide_build`, ordinary
media verification reports missing accepted receipt evidence for
`article-pdf` and `infographic`; the expected `stale` substring assertion fails
at **CozyMediaPresentationSpec.scala:576**. Repair design and validation remain
pending. This factual record changes no product behavior or executable
expectation. The [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-parent-edit-admission.json)
is parent inspection, not independent review or acceptance.

The [quote-stop correction completion](../../target/phase715-s33-quote-skill-repair-completion.json)
records the minimal shared correction to **two canonical instruction files**
and **one deployed TOML**, **55 focused tests passed** and **independent PASS**,
with deployed contract **revision 20**. The existing stopped child retained
loaded **revision 19** and used **one same-child, one-use correction** for the
first original submission; no new request, attempt or permission and no retry
were created. Actual **SBT exit 1, wrapper exit 1 and serial lock released**
establish a terminal validation failure. Fresh registered revision-20
operational acceptance remains pending; shared correction evidence does not
accept the Cozy product.

Prior **S32 48 successes / 4 completed suites** remain separately attributed.
Original **R715-03/04/14/15** finite remainders, concrete external
**E715-01..05**, final current **JA34 native generation, assembly, audible
hearing, end-card and EOF**, independent **M2 cycles 6/7/8/10**, whole-Step and
full-Phase review, exact local Step commits and the distinct release remain
**OPEN**. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. Original authority/base/goal binding/epoch,
repair history, stage and aggregate metadata and closure checkboxes retain
their state. This addition records facts without a workflow transition.

### S33 partial-receipt repair handoff — 2026-10-09

The [frozen Fix Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-fix-manifest01-partial-receipt.json)
addresses **P715-S33-PRESENTATION-PARTIAL-RECEIPT-001** in
`CozyMediaReceipt.prepareAcceptance`. Actual selected resource IDs are compared
with the declared IDs in `plan.resources`. A target build or an actual partial
selection retains unselected accepted entries without constructing fresh
receipts for them. An untargeted full selection continues to replace the
manifest and drop obsolete entries. The untargeted slide build therefore
preserves dependent article-PDF and infographic acceptance while normal
verification retains the real stale-decision and explicit-realignment contract.
Receipt construction, selected replacements, merge ordering, validation and
direct write failures retain their existing behavior.

The [original failed validation](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-focused-validation-failure01.json)
remains history: **31 tests, 30 succeeded, 1 failed, 2 completed suites**.
The later `CozyMediaCrossReviewSpec` and `CozyMediaSpec` group remains
**NOT RUN**. Repair candidate **11** was reserved for this bounded edit and is
**not accepted**; editing does not establish convergence. The parent-selected
current rerun of **CozyMediaPresentationSpec, CozyMediaPdfReviewStateSpec,
CozyMediaReceiptSpec, CozyMediaCrossReviewSpec and CozyMediaSpec**, implicit
compilation, diff check, actual terminal exits and serial-lock release are
pending, together with required independent assurance for cycle 11.
The existing lifecycle scenario, stale assertion, target-build stale-unselected
retention scenario and full-build obsolete-entry scenario are unchanged.
No new executable scenario, fixture or acceptance claim is introduced.
Zero compiler warnings in the failed run, the three known Logback warnings
under **HYG-P715-S19-LOGBACK-001**, and the hostile-XML DOCTYPE stderr remain
separately attributed and unsuppressed; independent diagnostic classification
remains pending.

The original Goal, base `fc640ec25e443d00c22b2c26b437a205d39910eb`,
authority `aa60c9e4-4b23-4e74-8efb-c936f19a1d2d` revision 1, Goal binding
`26b00c86-6ac3-4cae-9692-b19447ecd801` revision 1, and PLAN epoch
`1a94b256-a803-4ac9-a868-bbb9e59fbc41` revision 1 remain unchanged.
The six prior repair-history entries, ten previously consumed cycles and
pending independent **M2 cycles 6/7/8/10** are preserved. Concrete external
**E715-01..05**, other original finite work, final current **JA34 native
generation, assembly, audible hearing, end-card and EOF**, whole-Step and
full-Phase reviews, exact local Step commits and the distinct release remain
**OPEN**. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. This dated handoff preserves prior content,
closure checkboxes, Stage Status triples and aggregate metadata; it performs
no workflow transition.

### Actual S33 repair11 focused validation result — 2026-10-09

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is currently
**implemented-focused-validated-unreviewed**. The [current typed repair11 result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-repair11-focused-validation-result.json)
records the fresh repaired-subject run at **2026-10-09T04:51:35.719977+00:00**.
The initial unvalidated handoff, [original failed result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-focused-validation-failure01.json)
(**31 tests, 30 succeeded, 1 failed, 2 completed suites**) and earlier second
suite group **NOT RUN** remain dated history, rather than current failures.
The prior partial-receipt handoff's pending-rerun statement is also historical.

The first group, `cozy.media.CozyMediaPresentationSpec` and
`cozy.media.CozyMediaPdfReviewStateSpec`, reports **31 successes / 2 completed
suites**. The second group, `cozy.media.CozyMediaReceiptSpec`,
`cozy.media.CozyMediaCrossReviewSpec` and `cozy.media.CozyMediaSpec`, reports
**52 successes / 3 completed suites**. The total is **83 successes / 5 completed
suites**, with **0 failed, aborted, canceled, ignored or pending**. All five
selected suites and the unchanged explicit alignment/reuse/render lifecycle,
target-build stale-unselected retention and full-build obsolete-entry
replacement scenarios passed. No test, fixture or expectation was edited for
repair11. Implicit compilation completed for **1 Main Scala source**, with
**zero compiler warnings**.

The **three known duplicate `logback.xml` runtime warnings** remain retained
unsuppressed under **HYG-P715-S19-LOGBACK-001**. One exact hostile-XML rejection
was also retained on stderr:
`[Fatal Error] :1:10: DOCTYPE is disallowed when the feature "http://apache.org/xml/features/disallow-doctype-decl" set to true.`
The parent classified it from the actual source and log as deliberate hostile
XML fixture rejection: the existing unsafe-OOXML and legacy/VisualPage cases
use `hostiledtd`, and the real parser's `disallow-doctype-decl` maps rejection
to failure. The typed result records the source attribution at
`CozyMediaPresentationSpec.scala:426,638,1192` and
`CozyMediaPresentation.scala:769`. Independent Step classification of these
diagnostics remains **OPEN**. Zero compiler warnings is not a claim of zero
runtime diagnostics or independent acceptance.

The [repair11 parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-repair11-parent-edit-admission.json)
records the actual partial-selection receipt repair. Partial slide builds
retain unselected accepted receipts without reaccepting dependencies; target
merge and untargeted full replacement retain their meanings. Repair cycle
**11** was consumed on the actual source edit, and the current focused
validation converges: **cumulative 11 / nonconverging streak 0**. The six prior
repair-history entries and independent **M2 cycles 6/7/8/10** remain preserved;
independent **M2 cycle 11** also remains **OPEN**. Editing and passing tests do
not complete independent review.

Actual **SBT exit 0 / wrapper exit 0 / serial lock released** establish terminal
execution. Fresh registered **revision-20** runner operation is now observed
as `/root/phase715_s33_repair11_sbt_ba34cd9e`; the earlier quote-repair completion
note about pending fresh operation remains historical. This new repaired-subject
attempt needed no recovery, correction or repeated approval. The actual log is
`/private/tmp/skill.cncf.d/a-396741e5-182f-4ae5-a34a-7bf8efb9d6dd/82193-20261009T044958Z.log`.

The original Goal, base `fc640ec25e443d00c22b2c26b437a205d39910eb`, authority,
Goal binding, PLAN epoch, finite work list, aggregate metadata, Stage Status
triples and closure checkboxes retain their state, together with prior refusals
and reviews. Prior **S32 48 successes / 4 completed suites** and earlier native
evidence remain separately dated. Concrete external **E715-01..05**, other
original finite work, final current **JA34 native generation, assembly, audible
hearing, end-card and EOF**, whole-Step and full-Phase reviews, exact local Step
commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**. This factual
addition performs no workflow transition and claims no Step acceptance, Phase
closure or commit.
