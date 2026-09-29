# PHASE-71 Real Private Video Driver

Date: 2026-09-29

Slice: P710-02G / P710-02H

Status: six actual private-video cases verified; P710-02 accepted in `ecb36aff9a00a946fdeb9d30f0e64145d8a792c5`

The frozen P710-02G implementation adds a reusable six-case observer for the
task-private `P71-REAL-GRAPH-001` Japanese video route. The observer records
confined before/after inventories for `baseline`, `core`, `intermediate`,
`storyboard`, `current`, and `missing`, then verifies the exact terminal Cozy
final-build result, validated final manifest, validated nine-scene dialogue
handoff, and the native manifest's finite positive `ffprobe` record.

The observer is evidence-only. Its SHA-256, byte-size, and modification-time
inventory never becomes source admission, currentness, a binding, a renderer,
or a receipt framework. It does not author semantic sources, alter the
original SimpleModeling.org project, or replace the registered native command
route.

The baseline driver route is frozen as:

```text
sh scripts/test/phase71-private-video-driver.sh snapshot <cozy-root> baseline before
sh scripts/test/phase71-private-video-driver.sh snapshot <cozy-root> baseline after
sh scripts/test/phase71-private-video-driver.sh verify <cozy-root> baseline <native-build-result-json>
```

The parent must supply the authentic `cozy.exact-cli-command-result.v4` native
result and independently retain the native receipts/logs. Synthetic fixtures
in `CozyPhase71PrivateVideoDriverSpec` exercise verifier behavior only; they do
not prove a generated video, provider readiness, or Step/Phase completion.

## Earlier P710-02G checkpoint (historical)

At this earlier checkpoint, the factual chronology was limited to the focused
checks and native prerequisite state: the actual focused records were 8/8 and
369/369, the source record was 9/8/8, and the tools check passed. The immutable
`baseline-before` snapshot remains the first baseline. The prior native
synthesis attempt actually failed because VOICEVOX required an explicit
speaker/style identity; it did not generate an accepted video. The private
declaration now supplies the existing `四国めたん`/`ノーマル` configuration:
fallback speaker ID 2, speed 0.98, pitch 0.03, and intonation 1.08. This is
configuration and failed-synthesis chronology only; no actual generated MP4 is
claimed.

No actual baseline product or native synthesis/final-build success was claimed
at this checkpoint. The five remaining actual cases, independent acceptance, and
the exact Step/Phase closure evidence were pending under P710-02. Focused
8/8 and 369/369 validation passed and is retained. Full repository SBT remains
deferred-not-run solely to PHASE-71.4.

## 2026-09-29 cache-isolation correction chronology

The status paragraphs above describe the earlier private-driver checkpoint,
not the current correction result. In the earlier closed execution, the real
synthesis, native Remotion render, final MP4 build, and observer verification
succeeded, but CPB-P71-G-001 prevented acceptance because the generated
Remotion child ran from the project root and changed the root Webpack cache.

The bounded correction changes only the generated child working directory to
the generated Remotion workspace and adds a closed "cache-isolation-003"
observer evidence path that preserves the historical baseline snapshots. This
entry records correction implementation only; new actual validation is
pending. All five remaining actual scenarios, independent review, and Step
closure remain pending.

## 2026-09-30 P710-02H real private-driver chronology

The P710-02H private five-case run completed on the task-private
`P71-REAL-GRAPH-001` copy. Full confined before/after inventories and native
result locators are in the [implementation progress
checkpoint](../../../../.codex-workflow/phases/PHASE-71/P710-02H-implementation-progress-001.json)
and the [sealed missing-failure checkpoint](../../../../.codex-workflow/phases/PHASE-71/P710-02H-missing-failure-sealed-001.json).
Those ignored `.codex-workflow` files are task-local evidence locators, not
portable files in a commit or clone. The essential observed results are
recorded below; the linked inventories are available only in this workspace.

| Case | Before inventory SHA-256 | After inventory SHA-256 | Native/observer outcome |
| --- | --- | --- | --- |
| Core | `9b0f4b8caef39dcca852c97c42f5f73e1eca353ec0463640e4755d631f1ca17c` | `eeee56d960ac164ca912a5f16d766e23dd1a111b1822d6cf000a9499ef7dd6be` | Build succeeded; `core: verified` |
| Intermediate | `eeee56d960ac164ca912a5f16d766e23dd1a111b1822d6cf000a9499ef7dd6be` | `02416bb6c811a530640d879f0e99000f9059cefd131589df86c704c9e73d8944` | Build succeeded; `intermediate: verified` |
| Storyboard | `02416bb6c811a530640d879f0e99000f9059cefd131589df86c704c9e73d8944` | `6c346e9edba4d79346225960c468a00e4d825e0b7f67e874399ffacbcea09ee1` | Build succeeded; `storyboard: verified` |
| Current | `6c346e9edba4d79346225960c468a00e4d825e0b7f67e874399ffacbcea09ee1` | `6c346e9edba4d79346225960c468a00e4d825e0b7f67e874399ffacbcea09ee1` | Cache hit; `current: verified` |
| Missing | `6c346e9edba4d79346225960c468a00e4d825e0b7f67e874399ffacbcea09ee1` | `90f659bfd719cfb5dff359f93e87c30904f40e494ffd4128047db73922c9c6b2` | Expected exit 1, prior MP4 preserved; `missing: verified` |

The Core case authored the Core, Japanese Document, Japanese Summary, and
Storyboard forward chain. Native source admission reported 9/8/8, followed by
inspection, VOICEVOX synthesis, Remotion rendering, and final build. The final
MP4 was H264 1280x720 at 18fps with stereo AAC, approximately 267.56 seconds
and 12,451,078 bytes. The independent observer reported `core: verified`.

The intermediate case preserved Core bytes and FileTimes and changed only the
selected Document, Summary, and Storyboard descendants. Its native inspection,
synthesis, Remotion render, and final build produced a 12,486,578-byte MP4;
the observer reported `intermediate: verified`.

The Storyboard case changed only the `responsibility-boundary` narration and
caption. Core, Document, and Summary bytes and FileTimes remained unchanged.
Native inspection, synthesis, Remotion render, and final build produced a
12,488,221-byte MP4; the observer reported `storyboard: verified`.

The all-current case made no source edit. Native final selected the cache-hit
path, and the complete private inventory bytes and FileTimes were identical
before and after; the observer reported `current: verified`.

The missing case temporarily declared the absent
`phase71-missing-storyboard.md`. Native final stopped with exit 1 and the
direct-regular-file diagnostic, while the observer reported `missing:
verified` and confirmed that the prior MP4 was unchanged. The private video
configuration was then restored byte-exactly to SHA-256
`cada91e07e89b07d54be3e2d90d4f75a320e4a39c47b436709f8968e84b935d6`; its
restoration FileTime is recorded as `1790701805.526580244`. No native video
command ran after restoration.

The ordinary two-file symlink repair also passed focused validation at 18/18.
The fresh H focused accumulator passed 23 suites and 372/372 tests with zero
failures, `sbt_exit=0`, `wrapper_exit=0`, and the shared lock released. Its
verified receipt SHA-256 is
`20aae34faa5992160815a8f44914584a05cb1df1273447e19849ec0fe117e3ea`,
bound to candidate tree SHA-256
`7b8e9a16359606c56ff8a6e77cbda5b56e58f99dfd06ad5a57ad3144b3df9828`.
The independent protected-focused Step review found one documentation
portability blocker in this journal (`CPB-P710-02H-001`). The focused
independent closure review accepted the correction recorded above. The exact
37-path P710-02 Step commit is `ecb36aff9a00a946fdeb9d30f0e64145d8a792c5`.
Mandatory Phase full review and the distinct Phase release commit remain
pending. Repository-full validation is deferred to PHASE-71.4.
