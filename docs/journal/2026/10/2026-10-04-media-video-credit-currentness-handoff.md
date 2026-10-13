# Media/video credit currentness repair handoff

Date: 2026-10-04
Owner: Cozy
Status: investigation and proposed implementation handoff; not implemented or validated
Incident: `S13-BASELINE-CREDITS-001`

## Request and intended outcome

The user requested a product-level solution to the contradiction between
`media build` reporting a video current and subsequent credit verification
rejecting its generated companions. The user rejected running RDF generation
to rewrite those files as a workaround, and requested this handoff for Cozy.

Normal `media build` should select the required video/companion production and
then validate the resulting domain artifacts. No RDF preflight, manual sidecar
rewrite, forced timestamp adjustment, or agent recovery should be a prerequisite
for fixing this product behavior.

This journal records the proposed direction. Promote the selected behavior into
the relevant design/spec and executable specifications during implementation;
this entry does not independently change normative contracts or Phase status.

## Evidence and limits

- [Complete-site driver record](2026-10-04-phase-71.3-complete-site-driver.md)
  records the selected `ai-development-harness/article-video-ja` failure:
  both credit JSON and renderer props failed effective credit-set equality.
  The baseline stopped before English video and BoK/site generation. This was
  a reported runtime exit 1 after execution admission, not a platform denial.
- That record reports legacy digest fields and original-root provenance paths
  in copied companions, while current emitters omit those digest fields and
  resolve paths under the copied root. These are observed mismatch candidates,
  not a complete explanation of every failing projection.
- In `CozyMedia._build_resource`, `Action.Current` returns without production.
  `_verify_plan` subsequently calls `CozyVideo.verifyCredits` for video projects.
- `CozyMediaReceipt._freshness_failure` evaluates output validity and declared
  input/output modification times. `_dependency_paths` does not automatically
  enumerate the video's internal credit-profile/asset/audio dependencies.
  Explicit descriptor receipt inputs may cover some dependencies; inspect the
  actual selected project rather than assuming all are absent.
- `CozyVideoCommand._credit_json_projection_finding` compares parsed JSON with
  the full expected projection. Credit JSON includes profile source/selection
  paths and evidence manifest paths.
- `CozyVideoCredits.toRendererProps` contains presentation values and attribution
  items, without those absolute provenance paths. Therefore relocation alone
  does not explain the reported renderer-props failure. Inspect the actual
  field differences before choosing a sidecar-only repair.

No new reproduction, source edit, SBT run, or runtime acceptance was performed
while preparing this handoff. Preserve the incident record's qualifications
about incomplete native execution evidence and pending complete-site acceptance.

## Proposed responsibility and behavior

Video should own the dependency and production decision for a video and its
companions. Media should consume that decision and delegate the required work,
rather than maintain a separate incomplete approximation of Video's inputs.
Inspect and reuse the existing Video build/reuse path before adding an API.

| Condition | Required direction |
| --- | --- |
| Video-affecting input or displayed credit changes | Run normal video production and emit its companions. |
| MP4 reusable; only non-rendered companion data needs updating | Retain MP4 and emit the necessary companions through Video-owned production. |
| Video and companions current | Reuse without rendering. |
| Reuse cannot be established using the declared update model | Regenerate conservatively through the normal producer. |
| Required input missing or credit configuration invalid | Return a specific domain failure; do not manufacture successful credit data. |

Use the actual selected project, effective profile/configuration, assets, audio,
and other real producer dependencies with the existing existence/mtime update
policy. Do not invent content hashes, persistent comparison ledgers, or new
artifact-protection metadata. A profile file may affect both video display and
companion data; if the current model cannot distinguish the impact, regenerate.
Do not claim content-change detection beyond the documented mtime model.

Extract/share the existing credit emitter where needed so normal Video/Media
production can write `credits.json`, `credits.md`, and `renderer-props.json`
without invoking RDF export. Emit the current model directly; avoid a dedicated
legacy-digest stripping adapter or editing old JSON until equality happens to
pass. Legacy companions alone must not be treated as proof that an MP4 is current.

Never refresh renderer props and declare success while retaining a video whose
embedded credits should change. Cover creator/item text, enabled state, title,
locale, hold duration, and any other actual rendering input. Where trustworthy
reuse information is unavailable, use normal regeneration rather than adding a
new proof mechanism. Missing companions do not automatically justify MP4 reuse.

Reconcile verification with this production contract: validate required format,
attribution obligations, selected profile and independently required video/domain
semantics. Whole-document equality, including provenance paths, must not serve
as proof of video freshness. Removing equality alone is insufficient if normal
production still omits needed companion updates or embedded credit changes.

## Implementation entry points

- [CozyMedia.scala](../../../../src/main/scala/cozy/media/CozyMedia.scala):
  `_build`, action selection/consumption, `_build_resource`, `_verify_plan`.
- [CozyMediaReceipt.scala](../../../../src/main/scala/cozy/media/CozyMediaReceipt.scala):
  `action`, `current`, `_producer_paths`, `_freshness_failure`, `_declared_paths`,
  `_dependency_paths`.
- [CozyVideoCommand.scala](../../../../src/main/scala/cozy/video/CozyVideoCommand.scala):
  `verifyCredits` and projection comparison.
- [CozyVideoCredits.scala](../../../../src/main/scala/cozy/video/CozyVideoCredits.scala):
  effective credit resolution, `write`, JSON/Markdown/renderer projections.
- [CozyVideoBuildReplay.scala](../../../../src/main/scala/cozy/video/CozyVideoBuildReplay.scala)
  and [CozyVideoStoryboardBuild.scala](../../../../src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala):
  existing production and companion emission; inspect their normal reuse routes.
- [File update management](../../../spec/file-update-management.md):
  dependency/mtime behavior and current contract.

Before editing, inspect the copied failing project and all reported projection
differences. Resolve actual input/output paths and confirm the reproduction
cannot write to the original project through retained absolute configuration.
Use a task-owned fixture under `target/` for runtime work. Do not rewrite the
original user's artifacts as part of diagnosis.

## Phase boundary

[Phase 71.5](../../../phase/phase-71.5.md) is planned after Phase 71.4 closure.
Its [artifact-protection removal policy](../../../design/artifact-protection-removal.md)
removes protection behavior, including prior distribution-integrity exceptions,
without replacing it with timestamps, receipts, provenance, or equality checks.
Normal declared-dependency mtime rebuilding and independent domain validity remain.

Keep this repair compatible with that direction, but do not silently start the
repository-wide Phase 71.5 removal or waive current Phase 71.3/71.4 acceptance
obligations. Register the bounded repair in the owning current work boundary.
If the proposed verifier change conflicts with an active contract, resolve that
specific design/spec boundary explicitly before implementation; do not use this
journal as authority to delete unrelated validation.

## Acceptance scenarios

1. A relocated project with equivalent rendering inputs is handled by ordinary
   `media build`; path-only companion differences require no RDF command.
2. Legacy companions containing obsolete digest fields are emitted in the
   current format through normal production, without a hash migration mechanism.
3. A selected credit-profile/dependency update is visible to the build decision,
   even when the media descriptor itself is unchanged.
4. Changes to embedded credits cause video production; sidecar refresh alone
   cannot satisfy the scenario.
5. Missing/outdated companions are produced. Retain MP4 only where the normal
   declared update model establishes reuse; otherwise regenerate.
6. A repeated unchanged build does not invoke the renderer or rewrite MP4.
7. Missing required inputs and unresolved attribution obligations still fail
   with actionable domain diagnostics.
8. Unselected resources and original-project outputs remain untouched.

Add focused Given/When/Then executable specifications in the existing Media
and Video spec suites. Observe renderer invocation and MP4 modification behavior
directly; do not add production hashes for test convenience. Exercise the real
Media-to-Video integration boundary as well as the credit projection behavior.
Then rerun the admitted complete-site baseline and required reuse scenarios;
focused specs alone do not close the complete-site acceptance record.

Route any SBT validation through the registered `cncf_command_runner` and shared
serialization wrapper, with first-attempt scoped escalation and terminal
`lock=released` evidence. Follow the active Phase validation schedule; distinguish
checks actually passed from deferred/unexecuted checks. Preserve unrelated dirty
work. This handoff itself includes no implementation, test, commit, or push result.
