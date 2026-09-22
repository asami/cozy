# Phase 71: Core-Rooted Make-Level Media Regeneration Without Approval-Hash Gates

status=planned
execution_priority=current_video_generation_blocker

Status: planned; implementation not started
Planned at: 2026-09-21
Development item: DEV-034
Primary owner: Cozy

## Purpose

Repair Cozy's existing local media generation so that a request for a final
product performs the exact selected operation. Where that product has a
declared DSL chain back to Content Core, resolve only that chain and update
affected DSLs in forward order before generation. Introduce dependency
processing only where a real producer/input relationship needs it; do not
require an invented Core graph for an independently supplied `prebuilt` file.
Use ordinary make-level file modification times and declared direct/transitive
dependencies for automatic regeneration. A missing output, a newer dependency,
or an indeterminate reuse decision selects regeneration within the requested
product's dependency closure; it does not demand that a user or skill copy a
hash into configuration.

Human approval is not part of this generation workflow at this stage. In
particular, `storyboardReview.approvedIdentity`, visual-review approval
identities, and `confirmationReview.approvedIdentity` must not gate confirmation
or final video generation. Existing hash computation and stored identity
formats may remain for compatibility or diagnostics, but normal build,
regeneration, and reuse must not reject an input because an approval hash is
absent or differs. Do not introduce another hash-based artifact-management
framework.

For local media generation and `prebuilt` adoption, remove **all hash-based
pass/fail and freshness gates**, not only fields named `approvedIdentity`.
Neither a shared receipt `inputSetSha256`, a per-resource digest, an output
SHA-256, a renderer-manifest digest, nor a production-record digest may decide
whether a selected product can be built, reused, or re-adopted. Keeping a hash
field readable for historical compatibility or generating/storing it for a
possible future requirement does not make it an execution gate. Retain the
existing hash-generation and serialization code where practical, but do not
compare those values to accept, reject, rebuild, or reuse a local product.
Do not replace the current shared-hash gate with finer-grained hashes or a
special-case video/PDF hash exception. Introduce a hash check only through a
later, concrete requirement that names the protected boundary and proves why
ordinary source/producer/mtime checks are insufficient. Publication and
distribution are separate boundaries and are not changed by this local rule.

Generating a digest for a published artifact is a normal, purpose-bound
publication function and remains allowed. The problem addressed here is the
use of hashes as extra internal workflow state or defensive checks without a
clear requirement: those comparisons can contradict the selected operation
and reject routine edits or replacements. Express producer, input, and product
relationships with typed Scala models and explicit dependency rules; do not
add a parallel hash-based consistency protocol merely to recheck relationships
already represented by those types. Existing hash calculation may remain, but
its presence does not justify using it as a local media/adoption gate.

Removing those checks is the primary fix hypothesis, not a substitute for
observing the complete local site build. If another condition in Cozy's media
build, registration adapter, or the site's local invocation prevents an
otherwise valid video-only or article-only replacement, identify and remove or
correct that condition within this Phase. Do not add a new receipt, approval,
or hash exception to get past it. Preserve real missing-input, invalid-format,
unsafe-path, and published-artifact-integrity failures with actionable errors.
In particular, `register-site` must not reject an unchanged article PDF merely
because an unrelated package-level document changed and its shared receipt or
PDF review-state identity no longer matches. Check the PDF's actual producer
inputs and the published artifact itself; keep genuine missing, invalid, or
changed-required-input cases distinct from that global freshness gate.

## Selected operation contract

- **Generate/update a Cozy-managed product:** follow its declared producer
  inputs, invoke the required source authoring/renderer steps, and produce the
  selected output. If its real chain reaches Core, process that chain from Core
  forward. Do not traverse unrelated products or construct dependency edges
  merely to justify a receipt.
- **Automatic reuse:** compare existence and modification times of the
  selected output and its declared direct inputs. Reuse when they establish a
  current result; otherwise regenerate conservatively through the declared
  producer. Do not use any hash or historical receipt to make this choice.
- **Explicit `prebuilt` adoption/replacement:** accept the named existing
  source after path, file-type, readability, and required format checks. An
  explicit replacement is not rejected because its modification time was
  preserved, a global receipt changed, or an old/new SHA value is equal or
  different. Record the adoption without claiming that an undeclared producer
  or semantic review was verified. A publication claim, if requested later,
  remains a distinct operation.
- **Unavailable producer/input:** if automatic regeneration is selected but a
  required producer or declared input is missing, report the exact product,
  missing dependency, and next operation. Do not turn uncertainty into a
  hash-mismatch error or require a fabricated provenance graph.

This is a correction of the closed Phase 30 video contract where an edited
`storyboard.md` both invalidates dependent audio/render artifacts and blocks
their regeneration as "unapproved". It applies the make-level dependency
direction already recorded for Document Project Phase 60 and the request-to-Core
source-authoring order recorded in Phase 60.1 to the current Cozy video path.
The Phase does not reopen historical Phase 30, Phase 60, or Phase 60.1 closure.

## Requested-product dependency algorithm

For an explicit request such as "generate the video", start at the selected
product and follow each declared direct source dependency through its parent
DSLs to Core. Do not infer an edge from a filename, proximity, or similar prose.
Then process the selected graph from Core toward the product:

1. Reuse a valid DSL when it exists and none of its declared parents is newer.
   If it is absent, a parent is newer, or safe reuse cannot be decided, invoke
   that DSL's declared producer and validate its result before proceeding.
   Content-bearing DSLs such as `storyboard.md` require Codex authoring through
   the maintained source-production workflow; Cozy does not invent their
   semantics or treat a timestamp touch as source regeneration.
2. For each derived product, build it when absent or when any declared direct
   DSL, other file input, or required intermediate output is newer. Otherwise
   reuse it. If simple dependency rules cannot prove reuse, rebuild the
   selected product rather than rejecting it for a hash mismatch.
3. Execute only the requested product's dependency closure. An intermediate
   DSL change updates its affected descendants, while unrelated DSLs and
   products remain untouched. A missing producer, invalid source, or cycle is
   reported at that node; prior successful outputs remain intact.

Use file modification times for this make-level comparison. A missing file,
an input newer than its output, or ambiguous timestamp ordering selects a
rebuild when a declared producer exists. An unchanged local `prebuilt` file
whose actual declared dependencies have not changed is reusable even if an
unrelated package-level input or historical receipt has changed. An externally
refreshed video, PDF, or other local `prebuilt` output is adopted through the
same declared producer/input route without an approval-hash transfer. An
explicit `build --target` / adoption request selects the named existing
`prebuilt` file even when a copy operation preserved its old modification
time; the ordinary timestamp rule still governs automatic rebuild/reuse.
If automatic regeneration requires an unknown producer or direct input,
report that missing contract explicitly; do not disguise it as `stale` or
invent a hash-based substitute.

For video, the chain is `Core -> intermediate DSLs -> storyboard.md -> video`.
A changed Core first updates each affected downstream DSL, including the
selected Storyboard, and then updates the video. A Storyboard-only change
updates the video without rewriting Core or unaffected DSLs.

## Scope and behavior

1. Inventory every hash/identity comparison that can reject or select ordinary
   local media build, reuse, `prebuilt` adoption, or site-registration
   preparation, including shared media receipts, PDF review-state freshness,
   renderer/production manifests, PDF/slides/presentation adapters, and video
   approval fields. Remove the unrelated/global freshness comparisons from
   those decisions without disabling checks of the actual artifact being
   registered. Identify each target's actual DSL chain back to Core, direct
   file inputs, source producer, and product producer; distinguish make-level
   freshness from source validity and external publication.
2. For Storyboard video, declare dependencies from Core through any selected
   intermediate DSLs to one authoritative `storyboard.md` path. From there,
   `video.yaml`, the selected Storyboard, referenced local assets, and
   effective renderer/narration inputs feed generated audio, render parts, and
   the selected video target.
   A parent DSL change first updates its affected authored descendants; a
   changed Storyboard then rebuilds the video. If safe partial reuse cannot be
   established with simple dependency rules, rebuild the selected video.
3. Make confirmation video an optional review output, not a prerequisite for
   final generation. Both modes consume current valid inputs without a human
   approval record. Optional visual review artifacts must likewise not gate
   normal video generation.
4. Keep existing approval and identity fields readable for compatibility, but
   treat them as non-authoritative for local build, reuse, and adoption. Retire
   any temporary site-video/PDF SHA exception as a permanent solution; the
   general make-level rule must cover those routes. New scaffolds and
   companion skill handoffs must not require or populate approval hashes as a
   way to make generation succeed. Reconcile the Phase 60.1 source-authoring
   dispatcher with the declared video dependency chain; a media route naming
   only `storyboard.md` must not be misrepresented as a Core-to-Storyboard
   dependency. Removing legacy serialized fields and hash computation is not
   required in this Phase, but no remaining hash comparison may gate the
   selected local-generation or prebuilt-adoption operation.
5. Preserve independent errors for invalid Storyboard syntax, unsafe paths,
   missing required inputs, incompatible configuration, and renderer failure.
   A failed rebuild must leave the prior output intact and must not claim that
   the old output is current.

An explicit external `prebuilt` adoption may register the supplied file as a
local artifact without asserting producer freshness or semantic approval.
Automatic regeneration still requires a declared producer and inputs; it does
not infer them from the existence of a prebuilt file. Publication admission
remains a separate decision. No route silently promotes an explicitly
adopted file into a generated, reviewed, or published result.

## Work outline

| ID | Observable outcome | Status |
| --- | --- | --- |
| P710-01 | Classify selected operations as generation/update, automatic reuse, or explicit prebuilt adoption; declare only real producer/input edges, including the video-to-Core chain where applicable, and inventory every local generation/adoption hash gate separately from publication/distribution checks. | planned |
| P710-02 | Update affected DSLs from Core toward Storyboard, then replace the Storyboard, visual-review, and confirmation approval gates with make-level video rebuild/reuse; permit direct final generation. | planned |
| P710-03 | Reconcile the Phase 60.1 dispatcher, all affected local media/prebuilt and site-registration adapters (including PDF receipt/review-state preflight), and scaffold/skill contracts under one make-level dependency decision rule; retire narrow SHA-based exceptions instead of generalizing them. | planned |
| P710-04 | Prove Core-to-DSL-to-video, intermediate-DSL-to-video, and Storyboard-to-video regeneration; independent video/article replacement through the complete local site-build entry point; resolve any additional same-operation blockers found there; conservative rebuild; actionable failures; legacy compatibility; and failure preservation with executable tests. | planned |

## Acceptance

- Change Core and request the video: the declared affected intermediate DSLs
  are updated in dependency order, including `storyboard.md`, before the video
  is regenerated. A changed intermediate DSL updates only its selected
  downstream chain. An undeclared Core-to-Storyboard edge cannot pass this
  test by merely building the existing Storyboard.
- Edit `storyboard.md`, request the corresponding video, and obtain an updated
  output without editing any `approvedIdentity` or first approving a
  confirmation artifact.
- Repeating the request with unchanged inputs reuses the current output;
  changing a declared local asset or production input updates its dependent
  output. An uncertain reuse decision rebuilds rather than failing on a hash.
- Updating an article without changing a video's declared inputs does not
  invalidate the video. Replacing a locally produced video or PDF is a normal
  adoption/generation operation. Neither operation requires deleting a receipt,
  changing a hash by hand, or satisfying a new resource-specific SHA exception.
- On an isolated copy of the current SimpleModeling.org article package, test
  the two ordinary operations separately: replace only the prebuilt video,
  then run the local site build through its final generated site output;
  replace only the article, then run that same entry point without
  regenerating an unchanged video. Both must complete without manual receipt
  edits or hash transfers. If removing the hash gates reveals another
  same-operation blocker, fix it and repeat these two cases; a narrow `cozy
  media build` unit test alone is not sufficient evidence.
- Reproduce the observed next failure at SimpleModeling.org
  `application-modeling.dox/media-publication.json`: after a document-only
  update to `index.dox` that leaves `article/article-en.pdf` and its actual
  producer inputs unchanged, `register-site` must not report
  `publication-article-en` as lacking current `cozy.media.receipt.v2` or fail
  subsequently on a package-wide PDF review-state identity. The same
  registration still rejects a missing or invalid PDF, and a genuinely newer
  declared PDF input selects its normal regeneration/diagnostic path.
- No hash comparison in the selected local generation or `prebuilt` adoption
  path returns a blocking result. If execution must stop, diagnostics identify
  the site-build step, exact product, missing/invalid source or producer, and
  next required operation; a bare `stale` message or unexplained nonzero exit
  is insufficient. The site script must surface the failing Cozy diagnostic
  rather than hiding it.
- Existing hash fields continue to be generated and parsed for compatibility,
  but their values do not decide local generation, reuse, or explicit adoption;
  missing historical approval hashes do not block those operations. No new
  hash comparison is introduced without a separately justified future
  requirement.
- Confirmation and final video are separately selectable outputs. Final video
  generation has no mandatory human-review step or confirmation-manifest hash.
- Invalid or missing required inputs still fail clearly. Renderer failure
  preserves the previous product without presenting it as current.
- Existing `parts[].script` projects continue to work, and projects carrying
  historical approval fields remain readable without those fields controlling
  ordinary generation.

## Completion gate for the requested video workflow

Phase 71 cannot close on a Storyboard-to-video renderer test or an invented
isolated graph alone. Its end-to-end acceptance driver is the
SimpleModeling.org `ai-development-harness` Document Project and its selected
Japanese video product, exercised on a task-private copy so the source project
and its existing outputs are not modified by the test.

P710-01 must identify one authoritative Core path, each intermediate DSL and
its producer, one authoritative video Storyboard path, and the video target in
that project's declared dependency graph. The current Document Project and
media descriptors must agree on those paths; an undeclared Core-to-Storyboard
edge or a second placeholder Storyboard cannot be treated as a valid binding.
If project/skill integration needs changes outside Cozy, obtain their separate
implementation authority rather than closing this Phase with a Cozy-only
fixture.

The same user-facing "generate the video" entry point must pass all of these
tests before Phase closure:

1. A Core change updates each affected downstream DSL in dependency order,
   including the selected `storyboard.md`, and then regenerates the video.
2. A changed intermediate DSL updates its selected descendants; a
   Storyboard-only change regenerates the video without rewriting Core.
3. An all-current repeat reuses the DSLs and video. None of these calls needs
   a human approval action or an `approvedIdentity` edit.

Passing only a direct `cozy video build` call does not prove the Core-rooted
workflow. Conversely, the authoring dispatcher must invoke the real declared
video route and produce the updated video; a plan or revised Storyboard alone
does not prove the final product.

## Boundaries and references

No human-approval workflow, publication, upload, deployment, source-content
approval, new receipt framework, or repository-wide removal of hash algorithms
is authorized by this Phase. This exclusion does not preserve hash-based
blocking checks in local media generation or `prebuilt` adoption. Approval may
be designed later as a separate
explicit workflow if a concrete need is established; it must not be inferred
from a build input's identity.

The site-build acceptance above covers local media build and registration
preparation, not upload or deployment. Purpose-bound published-artifact digest
generation and verification remain allowed; they must not be repurposed as a
hidden local-generation freshness gate.

- [Phase 30 Storyboard workflow](phase-30.md)
- [Phase 60 make-level dependency contract](phase-60.md)
- [Phase 60.1 goal-driven DSL authoring](phase-60.1.md)
- [Make-level dependency decision](../journal/2026/09/2026-09-14-confirmation-views-and-make-dependencies-decision.md)
- [Core-rooted regeneration detail](../notes/core-rooted-make-level-media-regeneration.md)
- [Phase 71 decision record](../journal/2026/09/2026-09-21-phase-71-core-rooted-media-regeneration-decision.md)
- [Current Storyboard specification](../spec/video-storyboard.md)
