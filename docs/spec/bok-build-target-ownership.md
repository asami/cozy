# BoK Build Target Ownership Specification

Status: normative PHASE-71.3 / P710-04-SITE / S18-replan02 contract

BoK target cleanup SHALL delete obsolete generated children only within the
project's direct `target/cozy-bok` directory. Its existing direct
`article-media` cache SHALL retain snapshot bytes and FileTimes. Other immediate
`target` children belong to their producers and SHALL remain unchanged, including
files, directories and symlinks. This includes supplied Media/Video movies and
audio under `target/media`; it is an ownership rule, not a media whitelist.

The `target`, `cozy-bok` and retained `article-media` roots SHALL be direct
directories. A symlink at any of these roots SHALL be rejected without following
or unlinking it. Unrelated target content and external symlink destinations
SHALL remain unchanged on rejection.

The ordinary build retains its separately configured doxsite, Antora, website
and enabled Arcadia output cleanup, bibliography stash/restore and configured
SIE handoff behavior. Publication snapshot identity, leases and named
publication-integrity digests retain their existing semantics.

[CozyBokBuildTargetSpec](../../src/test/scala/cozy/bok/CozyBokBuildTargetSpec.scala)
specifies generated unrelated input preservation and the three symlink root
rejections using actual `BuildConfig` and target cleanup. The ordinary and
repeat-build scenarios in
[CozyArticleMediaBuildHandoffSpec](../../src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala)
specify the same boundary through normal `CozyBok.build`. Native complete-site
acceptance is a separate obligation.

## 2026-10-09 S32 build-input direct lifecycle amendment

Authority: [Phase 71.5](../phase/phase-71.5.md),
`P715-02-S32-BUILD-INPUT-DIRECT-LIFECYCLE`, under
[artifact protection removal](../design/artifact-protection-removal.md).
This amendment supersedes the earlier bibliography and SIE stash/restore
promises. Their actual selected inputs remain in place during ordinary
build-target cleanup; the copied handoff model and backup transport are removed.
Earlier normative text and accepted history retain their dated attribution.

At cleanup entry, the selected input roots are
`target/cozy-bok/bibliography/cache` and configured `bok.projects.*.sie.path`
values from the existing operation defaults. Relative values resolve against
the normalized project root; only target-contained paths participate in this
cleanup. Existing paths and missing-input ancestors have the same selected
ownership. Bibliography contents, lookup, modification-time reuse, metadata and
fetching retain their existing meanings. SIE's ordinary consuming parser still
owns input validity and missing/read failures.

Cleanup dispatches only the direct `target/cozy-bok` child. Other immediate
`target` children retain producer ownership and are not traversed or deleted.
The existing direct-directory and symlink-root checks for `target`, `cozy-bok`
and `article-media` remain. Article-media remains an effective-publication
input in its existing location. A child equal to or contained within a selected
input is left in place. For an ancestor of a selected input, cleanup recurses
only through direct real directories and removes unselected stale siblings.
A selected input link or an ancestor link is neither followed, unlinked nor
recreated. Unselected owned stale links retain ordinary deletion. Authored
sources remain outside this cleanup; the ordinary doxsite, Antora, website and
enabled Arcadia output cleanup retains its existing scope.

This is input/output ownership and ordinary cache reuse, with no input copying,
retention registry, snapshot, seal, lock or restoration protocol. Generated
outputs have no failure-preservation guarantee; ordinary read/write failures
remain failures and recovery is regeneration.

The existing [CozyBokBuildTargetSpec](../../src/test/scala/cozy/bok/CozyBokBuildTargetSpec.scala)
retains four cases and at least fifty generated ownership trials. Its first
property includes actual bibliography cache content, configured SIE input
beneath `cozy-bok`, another ordinary target producer input, nested handoff
content and stale siblings. The three root rejection cases remain. Normal
build orchestration and actual bibliography reuse remain covered by the
existing [CozyArticleMediaBuildHandoffSpec](../../src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala)
and [CozyBokBibliographySpec](../../src/test/scala/cozy/bok/CozyBokBibliographySpec.scala).

**P715-02-S32-BUILD-INPUT-DIRECT-LIFECYCLE** is currently
**implemented-focused-validated-unreviewed**. The initial 2026-10-09 editing
handoff recorded **implemented-unvalidated-unreviewed**. All four parent-selected
existing suites, including
[CozyArticleMediaWarehouseBuildContextSpec](../../src/test/scala/cozy/publication/CozyArticleMediaWarehouseBuildContextSpec.scala),
were pending at that initial handoff, together with parent admission and
independent review. Actual focused validation and parent admission are recorded
below; independent review remains OPEN. S31's
actual 27 successes in three completed suites remain separate dated evidence.
All original finite remainders and **E715-01..05**, final current **JA34
generation/assembly/audible hearing/end-card/EOF**, independent **M2 cycles
6/7/8/10**, whole-Step/full-Phase review and acceptance, exact local Step
commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**.

### Actual S32 focused validation — 2026-10-09

**P715-02-S32-BUILD-INPUT-DIRECT-LIFECYCLE** is currently
**implemented-focused-validated-unreviewed**. The [actual S32 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s32-focused-validation-result.json)
and [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s32-parent-edit-admission.json) record **48 successes across four completed suites**.
The first sequential `testOnly` group completed **16 successes in two suites**:
`cozy.bok.CozyBokBuildTargetSpec` and
`cozy.publication.CozyArticleMediaWarehouseBuildContextSpec`.
The second completed **32 successes in two suites**:
`cozy.publication.CozyArticleMediaBuildHandoffSpec` and
`cozy.bok.CozyBokBibliographySpec`. Failed, canceled, ignored and pending tests
and aborted suites were all **zero**. Normal implicit Main compilation reported
completed batches of **five and thirty-one Scala sources**; Test compilation
completed **two Scala sources**.

Compiler warnings and errors, unclassified runtime warnings, errors and fixture
diagnostics were **zero for this invocation**. Three actual runtime Logback
warning lines reported duplicate `logback.xml` resources from
`org.goldenport/kaleidox_2.12/0.6.20-SNAPSHOT` and
`org.smartdox/smartdox_2.12/2.4.19-SNAPSHOT`. These are retained under the existing
**HYG-P715-S19-LOGBACK-001**, pending independent Step classification; no warning
is suppressed. This selected result is not a whole-Phase warning-free claim.
Existing diagnostic and warning dispositions retain their original scope.

Serial invocation `4088733d-6159-438c-a708-e9085036de58` completed with SBT and
wrapper exit codes **0/0**, and the shared serial lock was **released**.
The connected BoK build-input and generated warehouse-work failure lifecycle
is focused-validated only. The initial **implemented-unvalidated-unreviewed**
editing handoff and pending-suite record remain dated 2026-10-09 history.
Parent edit admission records source inspection; it is not independent review
or Step/Phase acceptance. S31's actual **27 successes in three completed suites**
retain their separate dated attribution.

Other original **R715-12/13/14/15** remainders, all **E715-01..05** and other
original finite/runtime/carrier/descriptor/review obligations remain **OPEN**.
The supplied native assessment concerns local BoK build inputs and effective
publication materialization, with no JA34 narration, synthesis, render,
assembly or credit producer change. Final current **JA34 generation, assembly,
audible hearing, end-card and EOF** remain **OPEN**. Independent **M2 cycles
6/7/8/10**, whole-Step/full-Phase review and acceptance, exact local Step
commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**.

Original base/authority/epoch, repair counters, finite rows, aggregate metadata,
Stage Status triples, closure checkboxes and all prior refusal/review/repair/
native history retain their state. This factual projection runs no new
validation and performs no self-review, staging, commit or workflow transition.
No Slice, Step, Phase, native or external-consumer acceptance is claimed.
