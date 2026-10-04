# Phase 71.3: Local Site and PDF Currentness with Purpose-Bound Integrity

status=in-progress
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-71.4
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4"]
depends_on=phase-71.2.md
execution_priority=current_video_generation_blocker

Status: in progress; Step1 committed; whole Step2 independent review PASS; exact Step2 acceptance commit pending
Planned at: 2026-09-29
Development item: DEV-034
Primary owner: Cozy
Split from [Phase 71](phase-71.md).
Predecessor: [Phase 71.2](phase-71.2.md).
Successor: [Phase 71.4](phase-71.4.md).

Implementation progress: the local site/PDF/prebuilt adapters and selected publication-integrity purposes have focused implementation evidence recorded in the [implementation journal](../journal/2026/10/2026-10-04-phase-71.3-site-adapter-implementation.md). P710-03-SITE passed independent Step review `08a982c1-390b-47c5-84eb-2cf5bdc0f39b@1` and is bound by verified local acceptance commit `50f83ef4e87945684fd3672c3b621770896f60c1`. The [complete-site-driver journal](../journal/2026/10/2026-10-04-phase-71.3-complete-site-driver.md) retains prior baseline/failure history and records both S18-replan02 native cases passing. The [BoK target cleanup repair](../journal/2026/10/2026-10-04-phase-71.3-bok-target-cleanup-repair.md) records 4/4 representative passes, original 137/138 failure, test-only FIX01 then 138/138/export/lock release, exact supplied-input restoration and final-output/selected-input preservation admission. S19 actual missing-source/invalid-PDF/newer-actual-copy-source diagnostics and previous-output preservation, Step2 acceptance and Phase closure remain pending.

Current status projects parent `s19-terminal-projection-evidence.json` at
`2026-10-04T08:38:48.999389+00:00` and prior S18 terminal evidence. Earlier
dated failure/pending sections and the implementation-progress snapshot above
remain historical, superseded for the current frontier. No Step2 or Phase
closure follows from native-case or terminal-command evidence alone.

Current S19 record: all 18 CLI commands terminated, with 17 frozen expectations
met. Three baselines/three correct plans, two normal selected rebuilds,
invalid-output repaired verify/preflight and newer-source repaired preflight
exited zero. Six defect verify/preflight commands rejected with stage/copy/ID,
actual path/reason/next build. Newer-source repaired verify exited 1 solely on
published PDF mismatch after source/output/receipt/PDF-state became current.
**S19-REPAIRED-VERIFY-EXPECTATION-001** retains that failed oracle: frozen exit
zero contradicted unchanged publication equality while the prior destination
was deliberately preserved. Parent PLAN validation-selection reconciliation
`s19-validation-selection-reconciliation.json` at
`2026-10-04T08:55:39.561274+00:00` is complete: the separate reconciled
expectation is exit 1 with the exact diagnostic in the S19 journal. Original
plans, logs, failed exit-0 assertion and 17/18 frozen outcomes remain unchanged;
the other seventeen expectations and admitted outcomes remain retained.
no suppression, waiver, actual publication, product fix or blanket integrity
exemption is admitted.

The [S19 journal](../journal/2026/10/2026-10-04-phase-71.3-pdf-failure-preservation.md)
records initial 32-file/six-movie and expanded final 33–34 unchanged-file/nine
application/domain-movie comparisons, exact mutation/shared-source exclusions,
prior publication/site PDF bytes/mtime, other 13 receipt/other PDF-state entries
preserved, and independent four-page parsing of appended source and both repaired
PDFs. Stat is not universal byte proof; process-group completion remains unchecked.
Normal selected build and repaired publish `--dry-run` exited 0 without
destination writes; refreshed selected receipt/PDF-state and exact current
source/output bytes establish local currentness separately from distribution
equality. Existing inventory line 128 maps `CozyMedia.verify -> _verify_plan
publicationfindings` to M06 published destination integrity verification;
the [bounded supplement](../journal/2026/10/2026-10-03-phase-71.3-retained-integrity-audit.md#s19-m06-destination-verification-supplement)
and actual failure evidence require fresh whole Step2 independent inspection.
Parent PLAN supplies no independent acceptance or original-oracle success. All seven
P710-04/RELEASE boxes stay unchecked; Step1 CLOSED, original epoch/counters,
18 HYG/3 DEV and successor plans remain. Whole Step2 protected-focused review,
exact commit, original-epoch full Phase review and distinct release remain pending;
full SBT stays **deferred-not-run to PHASE-71.4**.

## 2026-10-04 S20 independent Step2 acceptance

Whole P710-04-SITE independent review `887eaa3f-d275-4cbe-b981-e904533de5d5@1`
returned PASS with no Current Boundary Blockers. Typed review ledger
`98d8bc5d-c359-4c47-a529-11e66d34c371@14` records and reads back the result.
The reviewer accepted the Media/Video producer correction, both unchanged
complete S18-replan02 native cases, selected application English-PDF evidence
and S19 diagnostics/preservation with the separately reconciled expectation.
The original 17/18 frozen outcomes and failed exit-0 oracle remain history;
no original failure is relabeled as success. Actual native process-group
completion remains unchecked. HYG-713-019 is nonblocking maintenance debt;
all 19 Hygiene and 3 Development Candidate records are materialized once in
their canonical journals. No accepted findings remain unpersisted.

Current frontier: exact local Step2 acceptance commit pending, followed by
one full Phase review under the original epoch and a distinct release commit.
Step2 behavior and executable-specification criteria are independently
accepted; its commit-bound acceptance checkbox and all release checkboxes
remain open until their actual operations succeed. Earlier dated failure and
pending sections retain their original evidence and attribution qualifications.
Repository-full SBT remains **deferred-not-run to PHASE-71.4**.

## Goal

Complete the local media/site-registration and PDF currentness correction with the accepted operation/schema/video contracts. Preserve only explicitly justified publication/distribution artifact-integrity digests; local generation, reuse and prebuilt adoption do not consume them as freshness or approval gates.

## Scope and owned Steps

- P710-03-SITE: reconcile site binding, registry, build context, PDF receipt/review-state and prebuilt registration adapters with declared producer/actual direct inputs, existence, modification times and explicit adoption. Retire the temporary site-video/PDF SHA exceptions.
- P710-04-SITE: use isolated copies of the current SimpleModeling.org package and its same complete local site-build entry. Replace only prebuilt video, then only article; both reach the final generated site without manual receipt edits/hash transfers or regenerating an unaffected video.
- Reproduce `application-modeling.dox/media-publication.json`: an `index.dox`-only update that leaves `article/article-en.pdf` and its actual producer inputs unchanged must not fail `publication-article-en` receipt or package-wide PDF review-state currentness.
- Missing/invalid PDFs and truly newer declared PDF inputs still select genuine rejection/regeneration/diagnostics. Surface stage, product, missing/invalid producer/input and next operation, not bare stale or schema-only errors.
- Retain only inventoried publication/distribution artifact-integrity digests with a named purpose and actual mismatch proof. Publication-related class placement alone is not a retention reason. Delete non-integrity calculations and supporting contracts; do not merely bypass checks.
- Correct additional same-operation blockers exposed by the real entry inside the admitted Cozy boundary; no replacement receipt/approval/hash exception.

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no directly comparable measured evidence; scope-based planning, low confidence; no model-speed multiplier
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none: accepted operation/schema/video contracts determine implementation
- frozen_profile_transition_handoff: accepted Phase 71.2 contract and handoff described below
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: expected 6h; range 5–8h; 0h above target, fits expected <=8h ceiling; upper range is risk evidence only
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none; no sub-four-hour expected member
- rebalance_attempts_for_every_sub_5h_child: none; no sub-five-hour expected member
- adjacent_merge_structural_rejection_evidence: no structural/authority rejection claimed; adjacent expected totals 14h/13h/12h/12h exceed ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: independent closure/handoff/review/release overhead included; final-only avoids repeated full suites, with no invented numerical or monetary saving
- agent_reasoning_mode_policy: default standard; pro only at an eligible supported agent launch with frozen quality-first justification
- runtime_suitability: re-evaluate in the Phase execution task; this structural gate does not attest a future runtime
- source: applied split from Phase 71 on 2026-09-29

## Frozen handoff and independent closure

Consume the immediate predecessor's accepted closure and frozen handoff; do
not repeat its semantic discovery or claim its Step implementation as new work.

Successful complete isolated site-entry evidence, selected-PDF-input currentness, named retained digest-purpose ledger with genuine integrity rejection, and actionable/atomic failure cases. Phase 71.4 consumes this accepted site contract.

No genuine incoming semantic authority/human/external-operation/release/migration
edge is asserted: `incoming_semantic_handoffs=[]`. These are serial delivery
and contract dependencies, not fabricated semantic gates. Close only this unit's owned checklist outcomes; integration rechecks do not reopen predecessors.
Routine execution included in a Sol/high container is limited to implementing
and proving that container's discovered contract or root-cause correction;
settled document/media and site/PDF work is assigned to Terra/high successors.

## Validation and closure boundary

S15-media-credit-producer is the admitted bounded normal Media-to-Video
producer/currentness correction for `S13-BASELINE-CREDITS-001`, recorded in the
[repair journal](../journal/2026/10/2026-10-04-media-video-credit-currentness-repair.md).
The frozen producer, companion-validity and in-process preservation contract
belongs to P710-04-SITE. Parent-admitted representative validation passed 81/81
without warnings. The original accumulator passed 264/265 with one obsolete
diagnostic expectation and no runtime-classpath export; after separately frozen
FIX01, the same nine-suite accumulator passed 265/265 and
`cozyExportRuntimeClasspath` succeeded without warnings. Both successful serial
invocations have SBT/wrapper exit zero, lock release and verified permission
consumption. Their admissions are recorded in the repair journal.

Fresh unchanged native entry request `b48bfac2-c063-4dc7-8e5b-7b3bc766ee05@1`
passed the S13 credit failure point and progressed through JA/EN media/video
production, then exited `1` at Japanese Antora Kroki readiness on `9609`
(`S15-BASELINE-KROKI-001`). Japanese MP4/companions were freshly generated;
English companions were emitted while its MP4 time remained. The marker and
both home pages are absent. Native session `5991` and chunks `7227ca`/`8498a2`
are actual-child reports, not directly inspected parent native calls; the
qualified record is `s15-baseline-runtime-failure.json` in the Phase workflow
directory. The read-only listener-configuration candidate is unproved; the
installed latest image/container Java log were not inspected and no fix is
claimed. Any possible Cozy Antora environment correction requires separate
parent capability/PLAN admission outside current program ownership.

Both complete video-only/article-only scenarios, real unchanged application
English-PDF and missing/invalid/newer declared-input diagnostics, independent
Step/Phase reviews and acceptance/release commits remain OPEN. No checklist
item, Phase/Step closure or exception is established by these focused and
partial runtime results.

[Phase 71.3 Checklist](phase-71.3-checklist.md) is the completion ledger.
Every owned Step/Slice needs executable focused validation and appropriate
compile/consumer coverage, independent acceptance review, exact Step commits,
closure journals/ledger, mandatory full Phase review and a distinct release
commit. The planning split itself did not implement or accept an item; the
focused implementation progress above does not establish Step or Phase acceptance.

Repository-full SBT validation alone is deferred to [Phase 71.4](phase-71.4.md)
under the reciprocal final-only chain. At release record focused assurance and
`repository_full_suite=deferred-not-run`; do not claim ordinary full-suite
assurance.

The repository already has `build.sbt`, so `split_validation_bootstrap=none`.
The first delivery's real media driver is product acceptance, not permission
to invent `bootstrap-structural` or waive that product proof. Entry seals the
live reciprocal planning chain; no prerequisite planning commit is required.

## Non-goals and ownership

Only Cozy is the mutation repository. SimpleModeling.org is a real acceptance
driver on a task-private copy, not permission to modify its production source,
media, scripts or history. External project declarations or authoring skills,
if actually required, need separate exact implementation authority; do not
silently replace the real route with a Cozy-only invented fixture. Skill
modification is not authorized by this split. No publish, upload, deployment,
remote write, human-approval workflow, speculative metadata/receipt framework,
CNCF runtime work or successor execution is included.

Use the [file-update/digest boundary](../spec/file-update-management.md).
Remove obsolete application hashes rather than keeping disabled or dedicated
compatibility code. Retained digests protect only named publication/distribution
artifact integrity, never local freshness. Preserve ordinary collection hashing,
non-hash data, safe paths, real artifact validity and previous outputs on failure.
Meaningful DSLs remain Codex-authored through declared producers; Cozy does not
invent semantics or `touch` sources to pretend they were regenerated.

Closed Phase 30/60/60.1 and Phase 61/61.1 remain baselines, not reopened work.
Later delivery scope belongs to its own Phase. Immediate caller reconciliation
necessary for a compile-valid change stays with that change, but does not claim
a successor's product outcome or move its canonical checklist obligation.

## S16 listener repair status

S16-antora-kroki-listener was separately admitted for
`S15-BASELINE-KROKI-001`. The [listener repair journal](../journal/2026/10/2026-10-04-phase-71.3-kroki-listener-repair.md)
records the authored `KROKI_LISTEN=127.0.0.1:9609` coordination with existing
readiness port `9609` and the enhanced existing build-to-Runner scenario.
Parent `s16-validation-admission.json` records 130/130 across five suites and
successful runtime-classpath export; SBT/wrapper exited zero, the lock was
released and consumed permission was verified. No `[warn]`/`[error]` lines
were recorded; known AkkaHttpServer package-only compiler information and six
not-a-git-repository disposable-fixture diagnostics remain retained. Source
origin was absent in child native context; the actual standing branch was
accepted and the parent independently admitted occurrence75.

Fresh unchanged native entry request `f665cd4d-df98-4813-a2e9-248dbfdda5d2@1`
through registered `/root/phase713_s16_baseline_runtime` (`gpt-6-luna` / `low`)
exited `1` at `Production ja home page does not match the Arcadia-authored
page`, new failure `S16-BASELINE-HOME-001`. Parent
`s16-baseline-runtime-failure.json` records direct inspection of actual command
item `exec-aa498373-7fff-42a3-a643-63a6ec3892f6` and full untruncated aggregate
output. Child-reported handle/chunks remain separately qualified in the repair
journal. No platform denial, restart, image or driver change occurred.

S13 credit and S15 Kroki failure points passed; both locale home outputs and
metadata exist, but the final marker is absent. The guarded entry-owned
`/private/tmp/simplemodeling-org-production-publication.4G4g4R` directory was
absent after exit. Parent HTML/source observations show Arcadia's MaterialKit
home replaced by BoK Dashboard: `CozyBokBuild.scala:119–123` copies Arcadia
then calls `_write_bok_pages`, and `CozyBokSitePages.scala:42–62`
unconditionally writes home. Further product correction needs parent
capability/PLAN outside S16 program ownership. Complete-site success is not
established. Both mutation scenarios, actual application English-PDF and
failure-preservation evidence, every Step2/release obligation remain OPEN.
Earlier S13/S15 history, 18 Hygiene and 3 Development Candidate records are
retained; repository-full SBT stays deferred-not-run to PHASE-71.4.

## S17 home ownership repair status

S17-arcadia-home-ownership is separately admitted for
`S16-BASELINE-HOME-001`. The [repair journal](../journal/2026/10/2026-10-04-phase-71.3-arcadia-home-repair.md)
records guards around only the two normal home
writes, using existing `bok.arcadia.enabled`. The new
[home ownership specification](../spec/bok-site-home-ownership.md) and
[design](../design/bok-site-home-ownership.md) preserve configured Arcadia
homes in both locale layouts while retaining disabled Dashboard behavior and
normal special/category/metadata generation. Both real build scenarios are
authored, including at least 50 required generated preservation trials;
worker execution is not claimed.

Parent-admitted focused validation passed 132/132 across five suites and
runtime export succeeded with SBT/wrapper exit zero and terminal lock release.
No `[warn]`/`[error]` lines were recorded; six fixture Git diagnostics were
retained. Initial `S17-SPEC-COMPILE-001` and its bounded sentinel-expression
repair remain recorded. Unchanged native request
`38eefe22-9d29-4be2-bda6-de76f7605fbd@1` finished exit zero, with both actual
Arcadia homes preserved, eight required outputs/fifteen direct assets valid,
a fresh exact completion marker and temporary registry `LgWrYw` absent after
exit. JA/EN video reuse was reported. The parent independently observed the
terminal command; child initial handle/chunk qualifications remain in the
repair journal. This proves the unchanged baseline only.

Prior S13/S15/S16/S17 failed attempts and attribution limits remain retained.
Both mutation cases, actual application English-PDF and failure-preservation
proof, independent Step2/Phase reviews and exact commits remain required. No
P710-04 or RELEASE item closes; repository-full SBT remains deferred-not-run
to PHASE-71.4.

## S18 setup failure and remaining acceptance

The [S18 factual journal](../journal/2026/10/2026-10-04-phase-71.3-prebuilt-article-native-acceptance.md)
records INITIAL English-associated movie/static-QA provisioning and two actual
setup failures: relative site-root/config arguments, then missing declared
Japanese `final.mp4` before selected English PDF production. The parent inspected
both native terminal outputs/exit 1; runner-reported granted escalation remains
separate and no permission denial occurred. Renderer-import and scratch
claim-construction failures/corrections retain their qualifications in that journal.

Parent inspection of all 21 declared operands establishes the remaining Japanese
fixture gap. Current package-wide required-source capture explains this failure;
no product bug or domain-check waiver is established. The English-associated
movie is not a legitimate Japanese substitute. Existence/size/mtime observations
across failed commands do not prove byte preservation. Originals/dependencies
remain read-only; the CLI session is idle with no pending request; native command items are terminal, with process-group completion unverified.

S18 is replan-required/proof-pending before any Japanese write or standalone
producer command. No successful setup gate, case mutation, new SBT or full native
entry occurred. Prior S15 265/265 and S17 132/132/export/unchanged-baseline evidence
retain their limited coverage. Four successful setup commands, both changed-input
cases/complete entries, actual English-PDF and S19 failure diagnostics, independent
Step2 acceptance/commit and full Phase review/release remain OPEN. All seven
Step2/release checklist items remain unchecked. Repository-full SBT remains
**deferred-not-run to PHASE-71.4**; no exception or closure is established.

## References

- [File update management](../spec/file-update-management.md)
- [Core-rooted dependency detail](../notes/core-rooted-make-level-media-regeneration.md)
- [2026-09-28 hash-removal decision](../journal/2026/09/2026-09-28-phase-71-hash-removal-decision.md)
- [Current Storyboard specification](../spec/video-storyboard.md)


## S18-replan01 terminal disposition: native input preservation failed

Parent `s18-replan01-terminal-projection-evidence.json`, dated
`2026-10-04T06:01:34.998857+00:00`, supplies the later actual outcomes.
Earlier INITIAL/proof-pending and setup-failure statements remain dated
historical snapshots, superseded for current disposition by this section;
all earlier failures and attribution qualifications remain retained.

The four actual Japanese inspect/synthesize/Remotion render/build commands
completed terminal exit zero. Real Japanese output was copied with attributes
to the article-only declared path after the first missing-parent-directory copy
attempt failed without mutation; the admitted mkdir/copy continuation succeeded.
All four ordinary setup commands exited zero: both actual English PDF copies
were current and both English prebuilt resources were adopted. The exact
37-byte video-only free-box mutation preserved nanosecond FileTime; the exact
article-only bilingual paragraph acquired actual newer FileTime. Parent directly
compared peer source/current transformed bytes.

All seven selected case CLI commands completed terminal zero: video-only
build/video verify/PDF verify; article-only PDF verify/PDF plan current/video
build/video verify. Actual English source PDF equalled copied PDF in both cases
and was valid/current. Selected inputs and movies retained observed size/mtime
before native execution. Two article-plan pre-start zsh unmatched-quote failures
remain retained; neither started the adapter/CLI. Original frozen renderer
strings passed zsh syntax-only checks. Truncated native display does not prove
an actual complete-shell-input mismatch. The same pending request succeeded
through structured complete JSON forwarding without a new request, cancellation
or skill change. Provider readiness was observed through registered escalated
CLI; earlier sandbox curl failure did not reproduce that route. Terminal CLI
completion differs from unchecked process-group completion; no live-process
absence, human listening/visual or publication approval is claimed.

Video-only unchanged native command
`exec-0f6940b8-4be9-47d8-880e-3f65814b1e47` exited zero (156947 ms), but is
**not acceptance**. Parent admission helper `s18-replan01-native-admit.py`
failed inventory key equality at line 65. Blocker
**S18-NATIVE-TARGET-CLEANUP-001** records removal of actual declared inputs:
`target/media/development-process/application-modeling/en/final.mp4`,
`target/media/development-process/application-modeling/ja/final.mp4`, and
`target/media/development-process/application-modeling/review/optimization-20260914/ja/part-remotion.mp4`.
Seven observed movies remained. Parent cause evidence identifies
`CozyBokBuild._build` calling `_clear_build_target`, with
`CozyBokFileSupport._cleanup_build_target_children` deleting immediate project
`target` children except the separately retained `cozy-bok/article-media` cache.
This removed `target/media` containing supplied movie inputs; those inputs are
not obsolete site projections.

Before the inventory assertion failed, parent helper checks covered eight
required nonempty outputs, fresh exact marker, fifteen final direct assets,
both Arcadia homes, both copied actual application PDFs and removal of the
invocation-owned temporary registry. These partial outcomes do not establish
native acceptance. Article-only native was intentionally **not started** to
avoid repeating known destructive cleanup before product repair. No passed
runtime admission, invariant relaxation, restoration or descriptor/driver
workaround occurred. Cleanup affected only the disposable video-only fixture;
original/dependency/unrelated work remains preserved.

Product input-preservation repair exceeds the fixture-only manifest. The parent
next returns through capability/PLAN for material replan under existing Phase
authority; no new approval or skill fix is required. Both complete native
acceptance gates, S19 real missing-source/invalid-PDF/newer-actual-copy-source
diagnostics/preflight and preservation, fresh whole Step2 protected focused
independent review/exact commit, single full Phase review/distinct release
remain open. All three P710-04 and four RELEASE items remain unchecked; no
waiver, exception or Step/Phase closure is claimed. Prior S15 265/265 and
S17 132/132/export/unchanged-native-baseline evidence retain their original
coverage. Repository-full SBT remains **deferred-not-run to PHASE-71.4**.
This pass projects supplied facts only; it runs no validation, runtime, review,
control update, delegation or commit.

## S18-replan02 passed native cases; S19 is the current frontier

Parent terminal projection `2026-10-04T07:16:00Z`, detailed in the
[cleanup journal](../journal/2026/10/2026-10-04-phase-71.3-bok-target-cleanup-repair.md),
admits both unchanged complete native entries and strict final output/input
checks. Selected application-modeling PDF/source equality, declared input/movie
retention and each final locale's visible expected paragraph passed. English
text uses the existing glossary anchor. Article-only also ran normal unrelated
AI-development-harness JA/EN production; the M1 helper-oracle correction admits
saved actual output under existing locale/glossary design, without native rerun
or product/fixture/request/script change. Original assertions and failures
remain retained. The original user-cited handoff was already implemented in S15.

Both `DISC-713-S18-SBT-HANDOFF-001` and
`DISC-713-S18-RUNTIME-HANDOFF-001` must be consumed by independent review:
renderer display preceded parsed-object/request retention, although matching
actual executions were independently verified. No retroactive procedural
compliance or waiver is claimed. Stat equality does not prove universal byte
preservation; native success proves no separate selected English-PDF copy
regeneration, upload, human approval or selected CLI process-group completion.

S19 actual missing-source/invalid-PDF/newer-actual-copy-source diagnostics and
previous-output preservation remain required. Fresh independent whole Step2
protected-focused review and exact commit, one full Phase review under the
original epoch and a distinct release commit remain pending. All three
P710-04 and four RELEASE items remain unchecked; Step1 is CLOSED. Existing
authority/bases/Goal/epoch/ledger13, 18 HYG/3 DEV and Phase 71.4/71.5 plans
remain retained. Full SBT stays **deferred-not-run to PHASE-71.4**.
