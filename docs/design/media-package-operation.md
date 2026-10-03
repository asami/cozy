# Media Package Operation

## Boundary

Codex skills own semantic transformation: thesis extraction, bilingual writing, localization, dialogue design, semantic comparison, and visual review. Cozy owns deterministic production: path resolution, strict schema/asset validation, profile/template resolution, explicit-argv renderer launch, resource conversion, file-update planning, structural verification, publication projection, and path provenance.

`cozy media` is an orchestration layer above `cozy video`. It does not duplicate narration or rendering semantics.

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
