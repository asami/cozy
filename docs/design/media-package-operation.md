# Media Package Operation

## Boundary

Codex skills own semantic transformation: thesis extraction, bilingual writing, localization, dialogue design, semantic comparison, and visual review. Cozy owns deterministic production: path resolution, strict schema/asset validation, profile/template resolution, explicit-argv renderer launch, resource conversion, content-identity planning, structural verification, publication projection, and provenance hashes.

`cozy media` is an orchestration layer above `cozy video`. It does not duplicate narration or rendering semantics.

## BoK Model

A SmartDox article is one representation of a `documentmodel:KnowledgeUnit`. Images and videos are additional representations associated with the same unit. A publication profile projects those representations into SimpleModeling.org, YouTube metadata, an artifact archive, or another BoK distribution.

## Portability

Media descriptors contain relative package paths and logical profile names. User- or machine-specific roots are supplied by repository configuration or environment variables. This keeps the package reusable outside SimpleModeling.org.

Cozy receipt v2 is the ownership boundary for deterministic acceptance. Cozy captures declared and automatic input identities, producer identity, selected operation context, validated output hashes, and optional presentation subordinate artifacts after structural verification. It owns receipt serialization, target-entry merge, current-evidence admission, and pre-destination-write input revalidation. Acceptance prepares review-state and receipt documents first, installs state documents before receipt, and rolls back in-process installation failures; the receipt is the final visibility record.

The package-owned `target/cozy-media/pdf-review-state.json` is a separate,
closed `cozy.media.pdf-review-state.v1` projection of the candidate receipt
manifest, not another receipt or descriptor setting. During normal media-build
acceptance it is reconstructed from that exact merged candidate and current
plan, then installed before the receipt. It records only currently valid public
document PDFs with article-media roles `article_pdf` and `summary_slides_pdf`:
knowledge, resource/role/locale/public path/media type, accepted relative
output/hash, without a receipt input-set identity. Direct `summary-slides-pdf` summary
entries additionally bind the already verified renderer-manifest hash. Only that
direct route requires the summary PDF structural verifier for page count/order/
assets and current dependent receipts; a prebuilt `summary_slides_pdf` retains
its ordinary receipt-derived role/path/hash currentness without renderer
evidence or direct structural/dependency verification. This state neither
duplicates nor weakens either contract. Exact reconstruction makes it a deterministic PDF-currentness guard
for selected PDF verification and publication preflight, while declared-file
timestamps determine generation reuse and receipt v2 protects output integrity.
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
shared-infographic hash, montage, and optional sidecar PPTX. PPTX is generated
only when its descriptor path is explicit; it has no resource, public, BoK, or
SmartDox-registration identity. Generation neither publishes to BoK nor
registers with SmartDox. The existing presentation grammar, `articlePdf`
dependency, receipt-v2 shape, and `cozy.media.cross-review.v1` boundary remain
unchanged.

Presentation is an adapter boundary: Cozy owns semantic-IR validation and invokes an approved external renderer by a configured argv vector, but never designs a layout or interpolates a shell command. The only presentation profile is `business`. The renderer manifest is untrusted evidence until Cozy checks collision-free descriptor-relative paths, PPTX output integrity, secure relationship-driven OOXML structure/text/per-slide media, and PNG evidence. Cozy reconstructs the deterministic cross-artifact review manifest exactly before recording only the explicit `article` or `slide-ir` alignment decision. A build can refresh current evidence but cannot alter the last approved semantic alignment; changed inputs or artifacts make the state stale. This keeps PPTX a distribution product rather than a semantic authority.

## Visual Page presentation coexistence

Generation currentness and distribution integrity are distinct operations.
The shared adapter selects local reuse from declared input existence and actual
FileTime, through the existing pure file-update policy. Receipt input hashes,
input-set hashes and PreparedPublication descriptor/input-set hashes are absent.
Current-command observations stay in memory; they do not duplicate the model or
replace removed hashes with a historical JSON-equality proof. Verify/publication
retain actual artifact-byte checks, safe path binding and atomic installation.
Historical input-hash properties are ignored by direct consumers. Presentation
review snapshots contain only generation/decision timestamps, not aggregate
review-manifest, artifact-set, or input-set identities. The review manifest is
written last in a verified generation, so its modification time is the current
generation boundary. Explicit alignment records the decision time. Unchanged
reuse preserves the decision; a newer generation requires explicit realignment,
including identical-byte regeneration. Historical snapshot hash properties are
ignored without processing or re-emission. Structural and actual output
integrity validation remain separate; timestamps are not content proofs.

Explicit prebuilt acceptance is an operation on current inputs and current
external output, not a comparison against an earlier receipt. Changed article,
policy, producer, or production metadata does not alone veto adoption of
unchanged media. The shared receipt adapter validates current regular files,
retaining consumer-owned safe in-root symlink support, and
site-bound PDF/PNG headers or declared SVG XML/root validity, and current video production identity/status/QA/output
association before recording selected acceptance. It does not require unchanged
historical evidence or duplicate metadata proofs. Current video output-to-QA
association and publication transaction/input-race integrity remain separate
from the removed old-receipt gate. Validation failure retains prior acceptance.
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
Summary-PDF v2 uses `pdf` and optional sidecar `pptx` instead of the primary
PPTX. Both filter obsolete `visualPageSetSha256`, `catalogSha256`,
`visualPageSet`, `catalog`, and `binding` before ordered validation,
without interpreting any JSON value or requiring their presence.

Current PageSet/catalog/Binding loading is generation input validation, not a
historical proof comparison. Page order, asset IDs, safe actual paths/bytes,
PPTX relationships/media, PDF page count, PNG format, and atomic prior-output
preservation remain. Review's three VisualPage provenance wrappers contain only
`{path}`; private evidence carries only pages/asset declarations and a review
path. No whole semantic copy, digest, or alternate identity is introduced.
Renderer argv and external ownership are unchanged.

The subsequent receipt migration removes the common input-set and per-input
hash fields, including their direct review-state consumers. Remaining
template/output/artifact hashes, deterministic review reconstruction,
and review-state currentness remain unfinished later same-Phase work rather
than integrity exemptions. Other Storyboard/video hashes and approvals likewise
remain separately unfinished. This slice claims no Step/Phase closure or
external renderer/consumer acceptance.

## Cross-media review boundary

CrossReview reads existing current presentation and validated Storyboard review
inputs. Actual named PageSet/catalog/page/Binding and safe files are validated
separately; current per-side ordered asset IDs/media types and bytes are compared
by the existing operation. It does not compare historical catalog/page/Binding
snapshots with either current inputs or the other medium.

The aggregate omits root PageSet/catalog/Binding proofs, per-page logical/
visual proofs, presentation/Storyboard/evidence/handoff digest identities, and
per-slide digests. Slides retain their declared IDs only. Legacy removed
properties are ignored for any JSON value. CrossReview no longer computes or
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
