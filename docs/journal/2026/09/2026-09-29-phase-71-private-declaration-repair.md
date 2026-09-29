# PHASE-71 P710-02A Private Declaration Repair

Stage Status:

- Current status: IMPLEMENTED; focused/native validation passed; independent Slice/Step review pending
- Owner: Cozy Phase71 P710-02
- Update rule: replace pending outcomes only from parent-verified evidence

## Scope and authority

This journal records the implementation boundary authorized by the attributable
approval `direct-user-message-a8045354aa3d6f559cf5458879244426`:

> 検証用コピーの宣言修復を承認する。Phase 71を再開して。

The repair is restricted to the task-private copy at
`target/phase71-private-driver/simplemodeling-org`. The original
`simplemodeling-org` repository, its production declarations, authoring
sources, scripts, and history remain read-only and unchanged. The utility itself
invokes no provider, renderer, Cozy/Dox command, or generated production output.
Parent-owned validation separately performed a read-only native Cozy inspection
against the task-private copy; no synthesis, render, build, media-provider
payload, or final video was generated.

## Implemented private repair contract

The new test utility
[`scripts/test/repair-phase71-private-declarations.sh`](../../../../scripts/test/repair-phase71-private-declarations.sh)
accepts exactly a Cozy root and the admitted private destination. It reads and
validates the old pair completely before staging either source, rejects
symlinked or non-regular roots, ancestors, sources, and supplied assets, and
recognizes only the coherent old or coherent native pair.

For the old pair it produces exactly two repaired files in the private copy:

1. `src/main/media/development-process/ai-development-harness/video/ja/video.yaml`
2. `src/main/media/development-process/ai-development-harness/video/ja/storyboard.md`

The descriptor changes only the dialogue part's source selectors to native
`storyboard`/`storyboard.md`/`explanation` values and inserts the supplied
`section-start.svg` asset declaration. It preserves the part ID, audio/output
paths, renderer, tools, credits, visual effects, and all unrelated declaration
text. The Storyboard changes only the four role-token asset references to the
four actual relative SVG paths. Its v1 schema, nine-scene order, narration,
timing, transitions, inserts, diagrams, pronunciation notes, direction, and
all other source bytes remain authored input.

The utility stages both files beneath a task-private temporary directory,
preserves source modes in staged files, verifies the new postconditions, checks
the observed source bytes again before each same-filesystem rename, and keeps
both backups when installation or rollback fails. A coherent native pair is a
true no-op: it reports reuse without writing, provider activation, or renderer
execution. Preflight failures occur before source installation and preserve
unrelated private files and prior outputs. After staging, each file is replaced
by a same-filesystem atomic rename; an install failure receives best-effort
paired rollback with retained backups. This is not a crash-proof two-file
transaction.

## Executable specification

[`src/test/scala/cozy/video/CozyPhase71PrivateDeclarationRepairSpec.scala`](../../../../src/test/scala/cozy/video/CozyPhase71PrivateDeclarationRepairSpec.scala)
is the executable specification. It uses AnyWordSpec, CozySpecVocabulary,
Given/When/Then boundaries, ProcessBuilder with the exact `sh` invocation, and
task-private fixture roots. Its scenarios cover:

- native nine-scene planning with the preserved part/output/audio/renderer/tools/credits contract;
- byte, mode, and nanosecond FileTime preservation on an idempotent repeat;
- wrong destination and symlink root/source/asset/ancestor refusal;
- missing required asset refusal before either source installation;
- missing, conflicting, duplicate, and partially repaired declaration refusal;
- incorrect scene and asset-reference refusal; and
- a ScalaCheck preservation property with at least ten successful cases and
  shell/awk metacharacters in authored narrative text.

The native assertions use `CozyVideo.inspect` with tool checks disabled and
package-private `CozyVideoImplementation._plan`. Native build, provider,
renderer, approval, and final-video outcomes are intentionally not asserted in
this Slice.

## Validation evidence

Parent-verified focused evidence:

- Representative `P710-02A-REP-003` completed 7/7 with SBT exit 0, wrapper exit
  0, and the shared lock released; receipt SHA256 is
  `1cbe31225fd792f61e027e083ba4fb1661114f4ecb4d937d00f85d5ecc8003ed`.
- Accumulator `P710-02A-ACC-001` completed 41/41 across the four suites plus
  `cozyExportRuntimeClasspath`, with SBT exit 0, wrapper exit 0, and the shared
  lock released; receipt SHA256 is
  `618e6a1805fa2ff5daf08e51343565e195c9474cc6cb4d3a31bf36197fd6afed`.
- The first representative attempt failed Scala 2 compilation at VAL001 (spec
  line 369) because escaped-quote interpolation `s"[\"$asset\"]"` was invalid;
  it was replaced by Scala 2-valid triple-quoted interpolation
  `s"""["$asset"]"""` in the exact same-file repair. The next attempt
  exposed VAL002: symlink-ancestor refusal failed because three nested guard
  returns were not propagated; the exact three `|| return 1` guard repairs
  were applied. Two Step repair units were consumed; the Phase full-review
  cycle remains 0.
- Registered runtime repair exited 0, changed only the exact two private
  declaration sources, preserved modes, and left all other 155 private inputs
  unchanged. All 157 corresponding originals and 129 original JA/EN products
  remained unchanged byte/mode/nanosecond mtime; no extra or lost private file.
- Exact repeat exited 0; all 157 private observations remained
  byte/mode/nanosecond-mtime identical, with no temporary or backup output.
- Registered native read-only inspect exited 0 and reported
  `dialogue`/`storyboard`, 9 scenes and 9 expanded scenes, four configured
  required existing SVGs, 18fps/1280x720, and preserved credits/provider/
  output/audio settings. Inspection source/private/original observations were
  unchanged. CLI result identity is `dd0956f7cb9a10c500b7b1e699e3f148`, the
  request SHA256 is
  `3f2956458f1103b1f98bf1af77104495dcc38aeef647907dd8662fd700c73a4b`, and
  the original session returned idle.

## Pending boundary

The implementation and focused/native validation are complete, but independent
Slice/Step review is pending. This Slice is not accepted or committed; P710-02
and Phase 71 remain open. Full repository validation is deliberately deferred-
not-run to PHASE-71.4 by the existing aggregate chain.

Native approval/hash removal, Core/Document/Summary authored-source dispatch,
and real nine-scene synthesis/render/final/update/reuse/failure evidence remain
open for later P710-02 slices. This record does not change accepted P710-01/F03
work or the canonical Phase/checklist/strategy.
