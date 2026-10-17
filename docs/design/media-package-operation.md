# Media Package Operation

## Boundary

Codex skills own semantic transformation: thesis extraction, bilingual writing, localization, dialogue design, semantic comparison, and visual review. Cozy owns deterministic production: path resolution, strict schema/asset validation, profile/template resolution, explicit-argv renderer launch, resource conversion, file-update planning, structural verification, publication projection, and path provenance.

`cozy media` is an orchestration layer above `cozy video`. It does not duplicate narration or rendering semantics.

### Video producer responsibility (PHASE-71.3 S15)

The internal `CozyVideoMediaBuild` route owns dependency discovery, part
rendering and final assembly for Media. Private Cozy bridges supply currentness,
producer inputs and normal production; public Video/Media models and commands
remain unchanged. Media checks its output declaration against Video's resolved
output and forwards only explicit receipt operands. Its article, descriptor,
site and publication observations remain package-owned validation/race inputs.

Video reuses the existing plan, Generation FileTime policy, admitted asset
resolver, Remotion/simple-java2d renderer and final assembler. Part decisions
consume real render inputs; assembly consumes core inputs and actual parts.
Selected credit input changes and invalid rendered-credit semantics rerender
before assembly. Valid reuse invokes the current credit emitter only, preserving
MP4 times. Positive field validation retains required attribution and display
semantics while permitting generic extras and relocated descriptive paths.

The existing generated-artifact utility also bounds in-process preservation
around this producer: direct destination preflight, output-only backups with
attributes, restore prior bytes/time/existence on failure, and retain original
plus rollback diagnostics with recovery files if restoration fails. No input
backups, locks or crash consistency are added. Managed renderer workspace files
are scratch. This resolves the admitted S13 credit-currentness incident without
RDF preparation and does not establish complete-site acceptance.

The preservation extent ends when Video production returns. Its input-time
race guarantee includes explicit receipt operands, while later package-wide
descriptor/knowledge/site race checks and receipt acceptance keep their existing
Media behavior without a generated-Video-output transaction across acceptance.

## BoK Model

A SmartDox article is one representation of a `documentmodel:KnowledgeUnit`. Images and videos are additional representations associated with the same unit. A publication profile projects those representations into SimpleModeling.org, YouTube metadata, an artifact archive, or another BoK distribution.

## Portability

Media descriptors contain relative package paths and logical profile names. User- or machine-specific roots are supplied by repository configuration or environment variables. This keeps the package reusable outside SimpleModeling.org.

Cozy receipt v2 is the ownership boundary for deterministic acceptance. Cozy captures declared and automatic input identities, producer identity, selected operation context, accepted relative output paths, and optional presentation subordinate artifacts after structural verification. It owns receipt serialization, target-entry merge, current-evidence admission, and pre-destination-write input revalidation. Acceptance prepares review-state and receipt documents first, installs state documents before receipt, and rolls back in-process installation failures; the receipt is the final visibility record.

The package-owned `target/cozy-media/pdf-review-state.json` is a separate,
closed `cozy.media.pdf-review-state.v1` projection of the candidate receipt
manifest, not another receipt or descriptor setting. During normal media-build
acceptance it is reconstructed from that exact merged candidate and current
plan, then installed before the receipt. It records only currently valid public
document PDFs with article-media roles `article_pdf` and `summary_slides_pdf`:
knowledge, resource/role/locale/public path/media type and accepted relative
output. The known typed fields are loaded directly, with arbitrary stored extras
ignored and no complete-JSON comparison. Only selected qualifying PDFs are
gated by ordinary receipt currentness and matching accepted state metadata;
unselected PDF drift cannot veto an unchanged selected PDF. The all-resource
Boolean uses this same selected-resource logic for all qualifying PDFs. A target
article refresh omits stale unselected summaries from the merged candidate state.
Direct summary PDFs retain their actual dependency and migrated nonhash structural
verifier under S07. Presentation subordinate
artifacts contain only `id,role,path`; no state or accepted primary-output digest
is required.
Presentation-only slide
operations, presentation/video review state, and cross-media review do not
depend on it or change shape. It performs no SmartDox registration, publish,
PPTX exposure, or semantic, visual, or audiovisual approval.

Article PDF is a separate narrow adapter boundary. A resource explicitly marked
by SmartDox role `article_pdf` names a current article source, one `ja` or `en`
locale, a public PDF path, and an approved renderer argv. Its closed
`articlePdf` configuration names one distinct same-locale `kind: infographic`
resource with `articleMedia.role: infographic` and a direct regular source.
That source is an explicit authority input: Cozy validates it before rendering,
includes it through existing receipt-v2 automatic source evidence, and
revalidates it before acceptance. Cozy appends only the accepted
source/output/locale/LaTeX-format operands; it never passes the infographic
path to the renderer. Its declared PDF destination must resolve within the
descriptor root after normalization and cannot have an existing symlinked
ancestor below that root; an absolute destination is rejected during descriptor
path resolution, while traversal/root escape and a symlinked ancestor are
rejected by Article-PDF descriptor validation before renderer invocation,
staging, or output replacement. It stages beside the declared PDF destination,
validates a direct regular PDF and unchanged receipt inputs, then performs an atomic
replacement. Thus a renderer failure, invalid staging result, or
article/infographic source/configuration race cannot alter the previous output
or make a new receipt visible. `summary_slides_pdf` retains its prebuilt route
for a separately produced PDF and adds the closed `summary-slides-pdf` direct
route. That route consumes either a `slide-ir-v1` or `visual-page-v1` authority
through the business presentation renderer/template and stages a regular PDF
before verifying its header, PDFBox page count, source-ordered PNG evidence,
selected-asset raw-byte agreement, montage, and optional direct-file sidecar PPTX. PPTX is generated
only when its descriptor path is explicit; it has no resource, public, BoK, or
SmartDox-registration identity. Generation neither publishes to BoK nor
registers with SmartDox. The existing presentation grammar, `articlePdf`
dependency, receipt-v2 shape, and `cozy.media.cross-review.v1` boundary remain
unchanged.

Presentation is an adapter boundary: Cozy owns semantic-IR validation and invokes an approved external renderer by a configured argv vector, but never designs a layout or interpolates a shell command. The only presentation profile is `business`. The renderer manifest is untrusted evidence until Cozy checks collision-free descriptor-relative paths, PPTX output integrity, secure relationship-driven OOXML structure/text/per-slide media, and PNG evidence. Cozy reconstructs the required deterministic cross-artifact review semantics through recursive positive known-field selection before recording only the explicit `article` or `slide-ir` alignment decision. A build can refresh current evidence but cannot alter the last approved semantic alignment; a generation newer than the decision requires realignment. Valid timestamp-preserved edits carry no historical-content-detection promise. This keeps PPTX a distribution product rather than a semantic authority.

## Visual Page presentation coexistence

Generation currentness and distribution integrity are distinct operations.
The shared adapter selects local reuse from declared input existence and actual
FileTime, through the existing pure file-update policy. Receipt input hashes,
input-set hashes and PreparedPublication descriptor/input-set hashes are absent.
Current-command observations stay in memory; they do not duplicate the model or
replace removed hashes with a historical JSON-equality proof. Stored receipt
and PDF-state readers validate known fields and ignore arbitrary extras.
Copy freshness consumes only the resolved direct source; prebuilt consumes that
source and an explicitly named site-video production file, with source,
production and output no newer than acceptance. Other producer selection is
unchanged. Evidence admission checks knowledge/id/output and selected automatic
source/project mappings, plus required paired site context mappings. Receipt
and PDF diagnostics name the actual failing path, producer and next target
build. A valid timestamp-preserved rewrite has no digest-detection guarantee.
Real publication source/staged/destination byte checks, safe paths and atomic
installation remain required. Both Presentation routes omit local renderer,
template, PPTX, PNG, montage, article-PDF, infographic and review hashes.
Presentation artifacts contain only `id,role,path`. Current artifact evidence
compares actual structurally verified typed records with the saved ordered
records; no historical content proof replaces the removed fields. Presentation
review snapshots contain only generation/decision timestamps, not aggregate
review-manifest, artifact-set, or input-set identities. The review manifest is
written last in a verified generation, so its modification time is the current
generation boundary. Explicit alignment records the decision time. Unchanged
reuse preserves the decision; a newer generation requires explicit realignment,
including identical-byte regeneration. Snapshot readers select only the required
UTC ISO-8601 `acceptedAt` and ignore arbitrary extras without processing or
re-emission; the state root remains closed. Structural and actual output
integrity validation remain separate; timestamps are not content proofs.

Explicit prebuilt acceptance is an operation on current inputs and current
external output, not a comparison against an earlier receipt. Changed article,
policy, producer, or production metadata does not alone veto adoption of
unchanged media. The shared receipt adapter validates current regular files,
retaining consumer-owned safe in-root symlink support, and
PDF/PNG headers or declared SVG XML/root validity, and current video production identity/status/QA before recording selected acceptance. It does not require unchanged
historical evidence or duplicate metadata proofs. Extra production properties are ignored generically. Publication distribution
byte integrity and operation input-race checks remain required. Validation failure retains prior acceptance.
SVG adoption uses its existing media type/public path declaration, with external
entity resolution disabled; Phase 26 PNG-only publication is unchanged.

The accepted presentation adapter remains the legacy
`cozy.slide-ir.v1` / `--slide-ir` / `cozy.presentation.render.v1` path. Its
existing untagged closed `presentation` object remains exactly the accepted
`profile`, `slideImages`, `montage`, `rendererManifest`, `reviewManifest`,
`reviewState`, `articlePdf`, and `infographic` shape. It cannot contain
`contract`, `catalog`, or `binding`.

The implemented Visual Page route described in
[`docs/spec/visual-page.md`](../spec/visual-page.md) is instead an explicit
parallel discriminated `presentation` object with exact keys `contract`,
`profile`, `catalog`, `binding`, `slideImages`, `montage`, `rendererManifest`,
`reviewManifest`, `reviewState`, `articlePdf`, and `infographic`. Its contract
is exactly `visual-page-v1`; the resource's existing required `source` is the
VisualPageSet input; and `source`, `catalog`, and `binding` are safe descriptor-
relative direct regular non-symlink files under the descriptor root. They
reject empty, absolute, traversal, URI-like, control-character, and backslash
paths; normalization changes; symlink escapes; and missing or nonregular
resolved files. `source` must parse as exactly `cozy.visual-page-set.v1` with
integer version `1`. Every set page declares one shared catalog `{id,
revision}` pair; resolution accepts exactly one supplied resolved catalog
matching that pair, and a mixed pair, unresolved catalog,
or mismatch fails closed. The one media-route catalog/binding pair therefore
binds every page deterministically. All legacy artifact/dependency fields
retain their existing meanings, and profile retains its exact template/renderer
shape. This variant cannot take an untagged legacy shape or infer `--slide-ir`.

Its v2 renderer argv replaces `--slide-ir <source>` with
`--visual-page-set <source> --catalog <catalog> --binding <binding>`.
The v2 renderer's operational fields are
`schema,target,profile,renderer,pptx,slides,montage`.
Presentation readers positively select known fields and ignore arbitrary extra
metadata at roots, renderer name/version, PPTX/montage paths, slides and assets.
V1 slides require `id,title,path,assets`; v2 slides require `id,path,assets` in
their existing relative key order; assets require ordered unique `id` values.
There is no named historical-field exclusion list. Summary-PDF v1/v2 now use
positive known-field readers too, with required relative root order
`schema,target,profile,renderer,pdf,slides,montage[,pptx]`. Renderer `name,version`
must match the profile; `pdf` requires positive `pageCount` only, with no
required path. V1 slides require `id,title,path,assets`, v2 `id,path,assets`,
with unique authority-ordered page and asset IDs. Montage requires `path`;
configured PPTX requires `path`, while unconfigured `pptx` rejects. Arbitrary
metadata at each object is ignored, and missing/null/ill-typed known fields
reject. Authored configuration and public calls remain unchanged.

Summary-PDF retains actual PDF header/PDFBox parse/page-count and PNG-signature
checks, safe descriptor-relative direct files, output/input collision and
symlink rejection, current dependency receipts, and Visual Page current asset
media-type/raw-byte agreement. Optional PPTX is only an internal direct-file
sidecar, with no new OOXML or render-equivalence contract. Sorted operation-local
Path/FileTime observations over the same exact renderer input set are compared
before/after rendering, alongside existing receipt-input FileTime observations.
No observation is serialized or replaced with a content identity. Changed
observed times or missing/unsafe inputs reject before installation. Valid
same-time byte changes are admissible without historical detection, while
invalid current PDF/page-count/required structure still rejects.

Same-parent staged ATOMIC_MOVE installs the primary only after verification and
observations agree. Modeled pre-install failures preserve actual prior primary
PDF bytes and receipt and clean staging. Renderer sidecars remain direct outputs;
no sidecar rollback or atomicity is promised after an invoked renderer fails.
This SummarySlidesPdf subsystem is migrated under S07 without claiming Phase
inventory, Step/Phase closure, EnglishPDF or complete isolated-site proof.

Current PageSet/catalog/Binding loading is generation input validation, not a
historical proof comparison. Page order, asset IDs, safe actual paths/bytes,
PPTX relationships/media and PNG format remain. `_verify_media` compares actual
declared assets and embedded media as a one-for-one raw-byte multiset, including
duplicate-content multiplicity, without persisting a freshness proof. Review's
VisualPage/template/PPTX provenance wrappers contain only `{path}`;
v1 slides are `{id,title,path}`, v2 slides `{id,path}`; material assets are v1
`{id}` or v2 `{id,path,mediaType}`; montage is `{id,role,path}`; article PDF and
infographic are `{id}`. Schema/target/knowledge/language, renderer name/version,
verification status/findings and array order are reconstructed recursively.
Private evidence carries only pages/asset declarations and a review
path. No whole semantic copy, digest, or alternate identity is introduced.
Renderer argv and external ownership are unchanged.

Receipt input and artifact hashes and direct review-state hash consumers are
removed. Renderer files are direct outputs; no primary Presentation transaction
is added. Malformed renderer evidence preserves accepted review/receipt records,
receipt-last installation preserves receipt/review-state on modeled failures,
and aggregate CrossReview remains atomic with prior-output preservation.
ProducerSource/BoK metadata, Publisher/rsync, final named-integrity audit,
English-PDF and complete isolated-site drivers remain pending without an
integrity exemption.
This contract claims no Step/Phase closure or external renderer/consumer acceptance.

## Cross-media review boundary

CrossReview reads existing current presentation and validated Storyboard review
inputs. Actual named PageSet/catalog/page/Binding and safe files are validated
separately; current per-side ordered asset IDs/media types and bytes are compared
by the existing operation. It does not compare historical catalog/page/Binding
snapshots with either current inputs or the other medium.

The aggregate omits root PageSet/catalog/Binding proofs, per-page logical/
visual proofs, presentation/Storyboard/evidence/handoff digest identities, and
per-slide digests. Slides retain their declared IDs only. Positive known-field
reading ignores arbitrary extras at Presentation wrappers/slides, Storyboard
root/rows/configurations/pages/assets and CrossReview root/slides/pages/assets/
verification, preserving required relative key order where already enforced.
CrossReview no longer computes or
parses digests or duplicates video-owned identity checks. It still consumes the
Storyboard review-input API, which now validates actual inputs and non-hash
record structure without historical approval/currentness proofs. Actual presentation output integrity and cross-medium material
byte agreement are independent of aggregate proof identities and remain intact.
Verification enforces saved operational structure, uniqueness, and reference
resolution instead of whole-record currentness equality. Build reconstructs once
and atomically replaces an existing safe direct regular aggregate after input
validation, never a participating input or review evidence; failure preserves
prior bytes.

The explicit approval values remain `not-recorded`. No renderer, publication,
or semantic acceptance is invoked. Explicit media prebuilt adoption no longer
rejects solely on old receipt/input mismatch. Template/output/artifact integrity
remains separate from the removed Storyboard review identities; it is not a
replacement proof gate.

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
