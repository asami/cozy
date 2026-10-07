# Phase 75.1: Public Document Contract and Site Recovery

status=closed
split_full_test_policy=each-phase
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
depends_on=PHASE-75
primary_owner=Cozy
producer_dependency=SmartDox effective-content contract
predecessor=PHASE-75

## Current delivery priority — 2026-10-07

The user's current instruction supersedes the broader completion scope and
producer-first/Slice-first sequencing below: restore the **current
simplemodeling.org site through its normal build entry point**. Until that
works, the requested user-visible delivery is not achieved, regardless of
component test counts, accepted Slices or review counts. Outstanding unrelated
features may remain numerous; they do not block this delivery.

The minimum is **everything that previously worked through
`runweb-production.sh`**, not merely getting today's partially broken output
to exit successfully. Recover the previously working user-visible behavior
from existing site/history evidence and verify it with the current real inputs.
Do not redefine the baseline by deleting articles, media, navigation or checks.
The user describes the approximately two-week regression as "-100 points";
restoring the old route removes that regression but does not deliver the new
feature. The requested addition is correct management of **videos, article
PDFs and summary-slide PDFs**, with working links from article cards.
Both baseline recovery and this addition are required for completion.

Use the current site sources, configuration, templates, declared locales and
existing media references in `/Users/asami/src/dev2025/simplemodeling-org`.
The existing entry is `etc/runweb-production.sh`, producing `website.d`;
repair the normal entry where necessary rather than accepting a test-only
launcher, reduced article fixture or hand-built output as its substitute.
Current JA/EN content is part of this site; extra locale/layout combinations
are not. Preserve originals and the accepted Phase 75 artifacts while using
the supported local build/review route. No remote upload is needed for this
delivery, and a build must not trigger one.

### Current execution evidence — 2026-10-08

Connected A/B implementation is independently accepted and locally committed:
SmartDox `59d6d41`, Cozy `094370b` and SimpleModeling.org `73de27e`.
The protected connected Step review returned PASS; P751-02/03 are jointly
CLOSED at that boundary. SmartDox producer checks
passed 34/34 with `exportClasspath`, the unchanged `2.4.19-SNAPSHOT` local
refresh completed, and Cozy checks passed 145/145 with
`cozyExportRuntimeClasspath`. These are focused results, not full suites.

The first scoped native Application Modeling operation created six JA/EN
nonvideo placements and registered eight locale/role rows, preserving the
eleven existing entries and producing twelve registry identities. The native
registration merge was not hand-written. Separately, normal
`sh etc/runweb-production.sh` exited 0 with admission of twelve articles and
all eleven required SITE outputs. Placement and Web construction remain
separate; canonical payloads stay outside Git under the containing store's
`media/`, without rendering, upload or predecessor deployment replay.

Original A browser observations retain root/JA/EN homes, categories, article
navigation, bodies/includes, diagrams, styles and images. Fresh bounded preview
evidence with explicit `/media/` mapping records 56 native HTTP checks with
zero failures, exit 0, terminal `finished` and `cleanup=true`. Actual JA/EN
Application Modeling four-role cards, title navigation to localized styled
articles, both infographic displays and published YouTube destinations were
observed. HEAD coverage of 42 local nonvideo resources is not browser or full
visual verification of every artifact; no new remote availability, video
playback or popup-click success is claimed.

Eight delivered PDFs (Application Modeling and AI Development Harness, JA/EN
article and slides) returned 200 / `application/pdf`, parsed with `pdfinfo`
and rendered visible localized first pages through Poppler. This is not an
all-page layout review. IAB's embedded viewer remained blank/unverified;
product corruption was not diagnosed. The prior preview expired during the
native Preview UI timeout and cleaned up; its failed session remains retained.
The fresh preview finished without opening a native PDF app. The enclosing
runner PATH transcription omitted `/bin`; the selected child PATH was correct.
That deviation remains separate: exact-submission conformance and registered
candidate behavioral acceptance are not established, and no rerun is implied.

The sole epoch-1 comprehensive Phase review returned PASS with zero blockers;
ledger 16 retains fifteen reviews and exactly one full review. Final Cozy
validation passed 2,511/2,511 tests in 187 suites, with no failures/aborts;
twelve optional cancellations remain explicit, not passed coverage. All eleven
current declaration and seven fake-AWS upload scenarios ran. Final SmartDox
validation passed 461/461 tests in 42 suites, with no failures/aborts/cancellations
and four historical ignored cases. Both actual SBT/wrapper results are 0/0
with `lock=released`. Native A/B build/browser evidence above remains applicable;
no source change, upload, media placement replay or second full review follows.

Final C disposition: the integrated validation, sole comprehensive review,
normal site rebuild and applicable browser evidence above meet the adopted
current-site boundary. P751-04 gathers this evidence; it is not a new
implementation Step requiring another source change or review. This final
closure record is effective only with the distinct local release commit bound
to `phase75-1-final-closure-20261008@1` and its verified closure receipt.
Before that receipt exists, the execution state remains PHASE_RELEASE_COMMIT;
completed Steps and tests alone are not a release.
Original Goal/authority/start/binding/bases/PLAN epoch 1, Step repair
17 / Phase repair 0 / nonconverging 0 and 66 consumed SBT identities remain.
The SmartDox pre-render stop and reviewed common-runner recovery are retained
in the [final-validation journal](../journal/2026/10/2026-10-08-phase-75.1-final-validation-runner-recovery.md).
No helper-only acceptance, new Phase/epoch or renewed repair allowance follows.
The shared Phase index and strategy retain concurrent Phase 71 planning;
their projection synchronization is deferred, not adopted into this release.
P751-01 retains its producer acceptance commits, P751-02/03 retain their joint
A/B acceptance commits, and P751-04/C is closed by this final evidence/release
record. Earlier dated open/interim statuses below are retained history.
See the [current checklist](phase-75.1-checklist.md#current-delivery-checklist--2026-10-08)
and [operating note](../../../simplemodeling-org/docs/notes/web-only-production-build.md).

### Historical execution baseline — 2026-10-07

Observed on 2026-10-07; these are restart facts, not additional acceptance gates:

| Boundary | Current evidence | Treatment on resume |
| --- | --- | --- |
| Phase 75 artifact preservation/deployment | Closed checklist and accepted warehouse/site/Cozy commits | Consume existing inventory, declarations, placements and explicit gaps; do not repeat recovery or deployment |
| P751-01 producer work | Accepted and committed; later relevant SmartDox 21 / Cozy 140 focused checks retained | Reuse relevant evidence and implemented facilities; refresh changed dependencies only when needed |
| P751-02F synthetic native site | Bodies/includes/diagrams generated; private listings and standalone single-JA links failed | Supporting diagnosis only; current real-site success is still unproven |
| Public-catalogue repair13 | Ten-path plan frozen, editing not started | Use the diagnosed SmartDox public-selection / Cozy listing connection inside batch A; do not finish another synthetic-only cycle first |
| Execution capacity | Development chat reports Goal blocked after agent start limit; control51 still IMPLEMENT; 12 repairs consumed, 13 reserved | Capacity recovery and actual Goal resume are prerequisites for execution, not missing feature approvals. Preserve both states and all counters |

At that historical restart, the next substantive execution was batch A below.
The instruction then was: do not blindly dispatch the
old repair13 manifest: reconcile its necessary product changes with this
real-site batch in the development task's existing work record. This planning
edit does not change control records, reactivate a Goal, reset repair limits or
claim capacity recovery. Do not repeatedly poll/retry an unchanged capacity stop.
These capacity and repair13 instructions are historical, superseded for current
execution by the evidence above; they are not a current capacity blocker.

### A. Restore the previous normal build on current real inputs

Historical Connected A authoring update (2026-10-07; superseded for current
execution by the evidence above): frozen repair13 connects SmartDox's
existing structural public admission to Cozy's shared catalogue/counts and
the ordinary full-input `etc/runweb-production.sh`. The entry now consumes
persistent `src/main/publication` with the configured containing store root
`../../maven-repository`, and selects normal canonical SmartDox/Cozy development
exports through project-owned Dox/Arcadia adapters. Media rendering, temporary
registration and prepared/warehouse payload copying are removed from this Web
entry. Existing Application Modeling site-source aliases remain configured
direct assets. Its real descriptor and six assets exist, but its identity is
absent from the accepted eleven-article registry: available-but-unregistered/
unplaced is a remaining B gap, not absent artifacts or feature acceptance.

Producer and consumer GWT regression scenarios are authored. Actual normal
build, usable `website.d`, explicit `/media/` preview/browser evidence and all
A/B/C acceptance remain pending. Historical capacity stops, repair outcomes,
original authority/binding/base/epoch and counters are retained; this authoring
record creates no execution, review, commit or Phase closure result.

One connected batch spans the site entry, current producer/consumer connection
and affected live templates. It is not a new Phase or a series of helper gates.

- Use existing script history, site declarations and accepted artifact records
  to identify the previously working pages/assets/links. Record only concrete
  expected user-visible results needed for this comparison, not a new inventory
  framework or an exhaustive historical reconstruction.
- Fix the known public-list selection through SmartDox's existing selection
  rules and Cozy's actual list/count consumers. Keep its focused regression
  alongside the implementation, without a separate facade-only release cycle.
- The former mixed `simplemodeling-org/etc/runweb-production.sh` invoked
  `media build` for videos/PDFs and copied prepared payloads. The current
  Web-only entry consumes accepted existing artifacts/declarations, with
  placement/registration separate, without regenerating media or overwriting
  preserved editions.
  This minimum P751-03 connection comes forward into A; do not wait for all
  P751-02 variants to finish. Retain previous visible functionality, not obsolete
  internal copying arrangements. Use the configured canonical store.
- Execute the normal full-input site build through its supported runner. Read
  all observable failures from that run together, repair the connected causes,
  and rebuild. Keep ordinary article/card/navigation checks active. Never
  remove real articles or substitute fixture output to make the command pass.
- Inspect the generated root/JA/EN home, categories, ordinary/package articles,
  includes/diagrams, CSS/images and existing links in the local browser.
  Exclude private package content and retain Arcadia page ownership.

A's exit is restored previous site behavior with a real build result and
usable local site. This is baseline recovery, not completion of the media
feature. Report the exact failing stage if A is not met; no new optional work
is admitted merely because a new corner case was found.

### B. Complete artifact management through actual article cards

Start with one real article/locale having available video, article PDF and
summary-slide PDF. Connect its existing canonical storage/registration through
the live card template to openable destinations in one batch. If no single
article has all three, use the smallest real set covering them and state that
fact; do not fabricate missing media or stop to render a demonstration set.

Then apply the same connection to all applicable current site declarations,
including JA/EN. Reuse Phase 75's selected article/locale/role inventory and
known unavailable/uncertain dispositions. An available artifact omitted from
a card is a defect; a genuinely absent artifact is not replaced or silently
declared available. Keep infographic behavior working as part of the baseline.

Videos retain declared YouTube URLs and archive/backup copies. Article PDFs
and summary-slide PDFs retain canonical durable storage and correct article,
locale and role mapping. Reuse accepted storage/deployment evidence; this
batch implements missing management and consumption, not another recovery or
upload campaign. Build caches cannot be the only artifact store. Configure
the supported local `/media/` mapping where required to open real links.

B's exit is the rebuilt normal site with correct management and usable
video/article-PDF/summary-slide-PDF card links across applicable current
content. A single representative article proves the connection early; it does
not replace checking the remaining actual declarations. P751-02 and required
P751-03 changes are implemented together where they meet at this boundary.

### C. Validate the integrated delivery and close

Reuse A/B's actual build/browser results for the same applicable inputs;
repeat the normal build as needed to demonstrate that routine rebuilding can
reuse managed artifacts. Do not build a new generic acceptance harness.
Run the retained each-phase full Cozy and changed SmartDox validation on the
integrated candidate, plus affected site-native checks. Complete required
independent acceptance/review and normal Step/Phase closing. Combine review
of related connected changes; do not add a review for each helper or temporary
milestone. A/B implementation can span the original Steps before acceptance.

After fixes, rerun affected behavior and required regressions; unchanged
checks are not repeated solely because another helper or checkpoint exists.
Full-suite or review failures remain failures requiring repair or explicit
disposition; no requirement is silently bypassed. No remote upload follows
automatically from local success.

These three batches replace the old finish-02-before-starting-03-before-04
ordering. Preserve original IDs for history and final disposition: P751-01 is
reused, P751-02/03 deliver A/B together, and P751-04 gathers C's actual evidence.
The earlier eight-hour estimate is exceeded and is not a remaining-time
forecast. Estimate remaining work only from real A/B blockers, not test counts.

The current private-document listing defect is on this route. Determine
whether the standalone single-JA link defect affects the current site before
scheduling it ahead of this delivery. Share a necessary fix when appropriate,
but do not make an unused single-locale configuration a release prerequisite.
Use small specs to diagnose and prevent concrete regressions, not as separate
deliveries that postpone integration.

### Retained work and deferred scope

- SDX-75-01..04 / COZY-75-04..08: retain accepted implementation/evidence and
  only the remaining work necessary for this actual site. Additional generic
  parser, non-Arcadia, layout and locale coverage moves to
  [DP-751-01](phase-75.1-deferred.md#dp-751-01-general-site-and-producer-coverage).
- COZY-75-09: only additional reusable tooling and broader operational
  combinations beyond this site's required artifact management move to
  [DP-751-02](phase-75.1-deferred.md#dp-751-02-independent-media-and-web-operations).
  Keep storage, placement/registration, existing artifact reuse and card-link
  integration necessary for videos/article PDFs/summary-slide PDFs in this
  delivery. Separate operations as needed to make that route correct; a generic
  operations framework is not a prerequisite. Do not use this deferral to
  postpone the user's artifact-management requirement.
- Additional cleanup, tuning and optional features move to
  [DP-751-03](phase-75.1-deferred.md#dp-751-03-optional-improvements).

The [current delivery checklist](phase-75.1-checklist.md#current-delivery-checklist--2026-10-08)
is the closure checklist for this reduced scope. Later broad requirements and
dated Slice projections are retained as history/source IDs; they do not
silently restore deferred work to this delivery. This is a work-scope decision,
not a rewrite of product semantics or a claim that unfinished features pass.
Preserve the existing Goal, original bases, PLAN epoch, findings, repair counts,
failed runs and accepted work. Adopt this priority at the next safe execution
boundary; no fresh Goal or general approval is needed to repeat the decision.
Report baseline recovery and artifact-management/card-link acceptance separately,
with the concrete remaining blockers and next actual-site action. Neither
component progress nor baseline recovery alone is completion of the request.

Planned at: 2026-10-06. Split from Phase 75.
Implementation started at: 2026-10-06, P751-01A; P751-01B independently accepted
PASS with 93/93 focused tests and review ledger revision 4. P751-01C1 is
independently accepted PASS after cycle 4: current focused evidence is 24/24,
with sealed M2 re-review and review ledger revision 6. The earlier 17/17 and
171/171 runs remain historical evidence; no cycle-4 regression rerun is claimed.
P751-01C2 site integration previously passed focused validation: 8/8 representative
and 195/195 nine-suite accumulator tests passed on subject revision 2.
The sealed complete-Step protected conformance review reports FINDINGS in
ledger revision 7 for `CPB-P751-01C2-MARKDOWN-INCOMING-001`. The frozen cycle-6
incoming/reciprocal repair is focused validated: parent-accepted subject revision
4 passed 205/205 in ten suites in one representative-plus-accumulator command.
Independent M2 re-review remains pending; this was not full repository validation.
Cumulative Step repair cycle 6 / Phase repair cycle 0; the parent consumed
cycle 6 once from verified owned changes; nonconverging 0. Review ledger revision
7 retains six reviews and zero full reviews. C1 cycle 4 remains history.
Step and Phase acceptance remain OPEN.

Current acceptance update (2026-10-07): the independent cycle-6 M2 re-review
reports PASS and resolves `CPB-P751-01C2-MARKDOWN-INCOMING-001`. Review ledger
revision 8 contains seven reviews and zero full Phase reviews. P751-01C2 and
the complete producer Step are semantically accepted for the exact local Step
commit; that commit is still pending. The earlier pending-review statements
describe the pre-review history and are superseded by this update only for
current semantic acceptance. Step6/Phase0/nonconverging0 and all failures remain
history. Phase acceptance remains OPEN; full suites, site/browser validation
and the final Phase review/release remain required. No deployment is claimed.

Current committed acceptance update (2026-10-07): P751-01 is CLOSED for the
accepted producer subject. Actual local acceptance commits are SmartDox
`38719e1` (producer contract) and Cozy `e0f7db2` (producer Step acceptance
evidence). The pending-commit and earlier pending-review statements above and
in the dated Slice history are superseded by these actual commits and the
ledger-revision-8 PASS; all earlier failures and Step6/Phase0/nonconverging0
history remain. This closes the producer Step only. Cozy consumer outcomes,
P751-02 and Phase acceptance remain OPEN; full validation, native site/browser
evidence and final Phase review/release are still required. No deployment is
claimed by this update.

## Entry, provenance and handoff

Start only after [Phase 75](phase-75.md) has closed its independently validated
preservation/canonical-deployment delivery. Consume its accepted article/locale
declarations, four-role bindings, actual public/YouTube URLs, preserved editions
and explicit gap dispositions. Do not repeat accepted preservation or require
75 to complete this child's source/site changes before it can close.

This child solely owns the unfinished SDX-75-01–04 and COZY-75-04–08 from
the pre-split plan. IDs and requirements remain; completed history stays in
75 and its canonical journals. COZY-75-09 adds the owner-selected separation of
prepared-media placement, Web construction and explicit upload, adopted into
this plan on 2026-10-06 from the
[operating-policy journal](../journal/2026/10/2026-10-06-media-web-upload-operating-policy.md).
The planning adoption did not start execution or reopen Phase 75. Phase 75
remains closed during this child's implementation.
See the [split proposal](../notes/phase-75-split-proposal.md).
The sequence is 75 → 75.1 followed by unchanged later numeric phases; no
Phase 76 is invented. Phase 71 resumption follows completion of both deliveries
under the selected priority, without changing that chain's validation policy.

There is no parent-profile transition or genuine incoming semantic split edge
(`[]`). The predecessor declaration/URL handoff is an ordinary dependency.
Within this child, freeze the producer API and exact admitted source-coordinate
handoff before bounded downstream implementation; do not create micro-phases.

## Structural Phase Plan Gate

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable estimated/actual evidence; bottom-up estimate,
  low confidence, no invented ratio or multiplier
- planning_demand: open-ended-discovery
- recommended_parent_profile: gpt-6.1-sol / high
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: effective-content API/Markdown/include boundary and actual card/page-composition diagnosis
- frozen_profile_transition_handoff: Phase 75 accepted declarations, public URLs and gap dispositions; same recommended parent profile
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 480 minutes expected; 420–720 minutes
  uncertainty; expected <=480 minutes, upper bound is risk only
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: no selected short child; a 3.5h producer
  candidate must merge with the coherent 4h site/card candidate (7.5h <=8h)
- rebalance_attempts_for_every_sub_5h_child: no selected short child; moving card
  rendering into the first delivery would couple its closure to unresolved site
  generation prerequisites; declaration migration remains in 75
- adjacent_merge_structural_rejection_evidence: the complete adjacent scope was
  estimated at 12h before splitting, above the 8h ceiling regardless of profile cost
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: 120 minutes total added planning/handoff, full-validation,
  review and release overhead; 840 minutes total expected; no monetary saving claimed
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible
  launch with supported interface and frozen quality-first evidence
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 75 on 2026-10-06
- scope_update: admitted execution entry on 2026-10-06 includes COZY-75-09;
  initial estimate is 480 minutes expected, range 420–720 minutes. The earlier
  450-minute split assumption is superseded; the upper range remains risk.

## Purpose

Make the production site pipeline distinguish a public SmartDox document from
other files that happen to live in a Document Project.  A `<slug>.dox/`
directory has a fixed, declared structure; public-site input must be selected
from that structure, not discovered by recursively scanning every file below
the directory.  Thus an arbitrary Markdown file, including a review note, is
not public SmartDox content, a LinkCollection source, or an Antora page input.

This child incorporates the planned coordinated Kroki correction:
Docker Antora must start the SmartDox Kroki service on the target internal
port (`8000`). Current launch, readiness, listener and Antora endpoint settings
use `9609` overrides, so a consumer-only value change is insufficient. Both changes serve the
same production-site boundary. Those adapter changes alone do not authorize
unrelated site publication; the selected artifact deployment belongs to the predecessor Phase 75.

This Phase also tracks two production-site regressions reported on
2026-09-28: locale-local CSS assets are not correctly referenced, and existing
Arcadia pages are replaced by generated BoK Dashboards.  Restoring the
established site presentation is required; a successful build exit alone does
not establish compatibility.

## Problem statement

A Document Project may physically store its public article at:

```text
<directory>/<slug>.dox/index.dox
```

Its logical source identity is:

```text
<directory>/<slug>.dox
```

and its canonical public page remains:

```text
<directory>/<slug>.html
```

Consumers must not each reconstruct this mapping from a physical path.
SmartDox must expose one effective-content/effective-document access path;
LinkCollection, Antora projection, route metadata, and site-link resolution
must consume that access path.  For a Document Project package, the accessor
selects only declared structural inputs—its public article is `index.dox`—and
does not recursively discover arbitrary descendants.  Consequently, an
unselected Markdown file containing a bare `_source` key must neither be
parsed as SmartDox content nor require an authoring-format change merely for
site production to succeed.

The effective-content boundary must also preserve the established include
contract.  The existing include resolver receives a logical, site-relative
reference and a correctly resolved physical base file.  A new Document Project
route must select `index.dox` first and pass that base onward; it must not send
an absolute filesystem path through a site-relative URI normalizer.  This is a
bounded adapter correction, not a new global path-validation, hashing, or type
framework.  Existing ordinary-document include behavior is a compatibility
constraint, not an input to be reinterpreted.

When a participating public input has a `.md` suffix, it is Markdown input:
SmartDox must parse it with the Markdown parser first and transform the
resulting Markdown AST into the shared document representation.  It must not
run SmartDox/Dox-specific syntax directly over Markdown source.  Parser
selection occurs only after the fixed package structure or another explicit
site-source declaration has admitted the file; arbitrary Markdown descendants
are not parser candidates.

Separately, Cozy's `CozyBokSiteBuild` currently supplies
`SMARTDOX_KROKI_PORT=9609` to the Docker Antora container. Current producer
launch/readiness/listener and Antora endpoint settings also use `9609`
overrides. The target remains `8000` and requires coordinated producer and
consumer work; a consumer-only value change cannot establish compatibility.
No actual Kroki setting is changed in P751-01A.

### Locale-local CSS asset references

The multi-locale Antora output stores its UI assets under each locale, for
example `website.d/ja/_/css/`.  Generated locale home and nested pages must
resolve their CSS links against that locale's asset root, not the canonical
website root.  The reported failure at `http://localhost:8085/ja/index.html`
included links to missing `/_/css/` assets.  Track this independently from
page-content selection: fixing CSS does not prove that the intended page is
being displayed.  Single-locale output must retain its existing asset paths.

### Existing Arcadia pages replaced by BoK Dashboards

The reported `/index.html` remains the expected Arcadia page, while locale
home pages display a different BoK Dashboard.  The current Cozy build copies
Arcadia output into the website and then calls `_write_bok_pages`, which writes
locale home and category indexes over the existing pages.  Phase 75.1 must
restore the established Arcadia page ownership at those public URLs while
retaining BoK-generated pages where no Arcadia page owns the destination.
Verification must compare the intended JA/EN home and category pages, not
merely confirm that styled HTML or a build-completion marker exists.

### Article-card media links

On 2026-09-28, the user reported that article cards do not correctly display
links to the article PDF, summary-slide PDF, and video.  Track this as an open
Phase 75.1 issue; the cause is not yet established.  Verify the declared media
information reaches the card and that the rendered links lead to the intended
article's available media, including the appropriate locale.

### Independent placement, Web construction and upload

The production entry point currently mixes media generation and copying with
Web construction. COZY-75-09 separates those operations without replacing the
established Cozy + Arcadia + Antora composition. The planned operator sequence
is prepared-media placement and inspection, independent `runweb-production.sh`
construction of `website.d`, then separately authorized explicit upload.
Repeated Web builds must not require repeating media placement.

Placement consumes selected existing artifacts and the predecessor's canonical
mapping: configured warehouse `media/<ja|en>/<category>/<article>/<file>` to
public `/media/<ja|en>/<category>/<article>/<file>`. It must neither render
missing artifacts nor silently substitute editions. Infographics, article PDFs
and summary-slide PDFs retain declared roles/locales; video masters retain the
local-backup policy and site video links retain declared YouTube URLs. The
workstation warehouse is `/Users/asami/src/maven-repository`, not its CAR
`repository/` subtree; reusable Cozy behavior must keep the store root configured.

Web construction may read or project existing declarations and canonical
references. It must not invoke media placement, article-media rendering or
deployment, copy warehouse media payloads into `website.d`, or upload. Pages,
navigation, CSS, JavaScript and ordinary site assets remain Web-build outputs.
Local `/media/` review needs a separately configured warehouse mapping; serving
`website.d` alone or recording a public URL is not remote-retrieval evidence.

## Ownership and dependency boundary

### SmartDox producer contract

SmartDox owns the effective-document abstraction, the declared Document
Project package inventory, and the rule that maps a package's `index.dox` to
its logical `.dox` identity.  It also owns suffix-directed source parsing. The
implementation must centralize this behind an effective-content accessor (for
example, `effectiveContent` or `getDox`); it must not duplicate package-path
special cases in LinkCollection or Antora.  That accessor must preserve source
kind: an explicitly admitted `.md` uses Markdown parsing followed by AST
transformation, while `.dox` uses the SmartDox/Dox parser.  It must not turn a
recursive package-directory scan into an implicit source declaration.

### Cozy consumer/adapter contract

Cozy consumes the resulting SmartDox site behavior and owns its Docker Antora
launch adapter. Planned consumer files for coordinated Kroki work are:

- `src/main/scala/cozy/bok/CozyBokSiteBuild.scala` — pass
  `SMARTDOX_KROKI_PORT=8000` to Docker Antora; and
- `src/test/scala/cozy/CozyBokSpec.scala` — assert the same launch contract.

These consumer changes must be coordinated with SmartDox producer launch,
readiness, listener and Antora endpoint overrides currently using `9609`.
The target `8000` is retained; P751-01A changes none of these settings.

The CSS-reference repair belongs to Cozy's site UI asset-link generation
(`CozyBokDashboardCore`); the Arcadia overwrite repair belongs to Cozy's site
composition/page-generation boundary (`CozyBokBuild` and `CozyBokSitePages`).
Freeze the exact admitted files and focused specifications at Phase entry;
existing isolated candidates are not acceptance evidence by themselves.

No source candidate is accepted, merged, committed or validated by this split.
Freeze the exact admitted source roots, candidates and dependency coordinate at
execution entry; preserve unrelated work and the retained Phase 75 history.

### SimpleModeling.org operational scripts

Cozy owns reusable media/site behavior; SimpleModeling.org owns its production
Web entry point and separate prepared-media placement script. Freeze the exact
site-script roots and candidates at entry. Promote the adopted operating
boundary into the affected design/spec before script changes; this plan and its
source journal do not themselves replace the normative contracts. Use existing
Phase 75 placement/registration facilities rather than reimplementing the
warehouse. Upload scripts remain explicit operator entry points, not dependencies
of either local operation and not a Phase closure prerequisite.

## Work outline

| ID | Outcome | Status |
| --- | --- | --- |
| SDX-75-01 | Freeze and implement one SmartDox effective-document/content contract for ordinary Dox files and `<slug>.dox/index.dox` packages. | OPEN |
| SDX-75-02 | Route LinkCollection, Antora input/projection, public route metadata, and site-link resolution through that contract; enumerate only declared `<slug>.dox/` package inputs rather than recursively discovering files. | OPEN |
| SDX-75-03 | Parse participating `.md` sources as Markdown and transform their AST; never apply SmartDox/Dox-specific syntax directly to Markdown bytes. | OPEN |
| SDX-75-04 | Preserve the legacy include resolver contract by adapting a Document Project to its effective physical base before link/include resolution; add a focused absolute-base/relative-include regression. | OPEN |
| COZY-75-04 | Adopt the exact SmartDox contract in Cozy integration evidence without adding a duplicate path mapper or a review-note parser. | OPEN |
| COZY-75-05 | Coordinate and validate producer/consumer Kroki-port settings for target `8000`. | OPEN |
| COZY-75-06 | Restore CSS asset references for locale home and nested pages against each locale's Antora asset root; preserve single-locale behavior. | OPEN |
| COZY-75-07 | Preserve existing Arcadia home and category pages instead of replacing them with BoK Dashboards; retain default BoK behavior for sites without an Arcadia-owned page. | OPEN |
| COZY-75-08 | Restore article-card links from deployed artifact declarations: article PDF, summary-slide PDF, infographic and YouTube video, as applicable. | OPEN |
| COZY-75-09 | Separate prepared-media placement from Web-only `runweb-production.sh`; support independent local review and retain separately authorized explicit upload. | OPEN |

## Acceptance

Original broader acceptance is retained below for traceability. Apply the
current delivery checklist and explicit deferrals first; these historical
items do not reinstate optional breadth or the former execution order.

- An ordinary `foo.dox` and a Document Project
  `foo.dox/index.dox` both resolve through one effective-document access path;
  the latter has logical identity `foo.dox` and public identity `foo.html`.
- LinkCollection, Antora generation, public-route metadata, and site-link
  resolution receive the effective document rather than inspecting package
  layout themselves.
- An arbitrary descendant of `<slug>.dox/`, including Markdown under
  `review/`, is not implicitly public content.  A bare `_source` key in that
  unselected file does not affect SmartDox parsing, link collection, Antora
  generation, or public-site output.
- A participating `.md` source is explicitly admitted by the fixed package
  structure or a site-source declaration, then parsed as Markdown before any
  SmartDox document transformation.  SmartDox/Dox-specific syntax is never
  interpreted directly from Markdown bytes.
- An ordinary source file and a Document Project source with an absolute
  physical base both resolve their relative includes as before.  The Document
  Project adapter chooses its effective `index.dox` base without passing that
  absolute path to a site-relative URI normalizer; no leading path separator is
  removed during resolution.
- Regression evidence covers an ordinary document, a packaged Document
  Project, a relative include from an absolute physical base, and a project
  containing a review note that is intentionally not public content.  The
  evidence verifies links, include resolution, and canonical package output
  paths as well as successful parsing.
- The Docker Antora invocation constructed by Cozy passes
  `SMARTDOX_KROKI_PORT=8000` and never emits the obsolete `9609` value.
- JA/EN home and nested pages resolve their local stylesheets to real assets
  under the corresponding locale root and load them successfully over HTTP.
  Focused evidence also preserves single-locale asset resolution.
- With Arcadia enabled, the established root, JA/EN home, and Arcadia-owned
  category pages retain their intended content and presentation.  They are not
  overwritten by BoK Dashboards.  Sites without an Arcadia-owned destination
  retain the existing generated BoK behavior.
- Browser verification checks the intended page identity and visible layout
  against the established Arcadia output.  Neither CSS loading alone nor a
  successful production command is sufficient evidence.
- Article cards display the declared, available article PDF, summary-slide
  PDF, infographic and YouTube video links correctly. JA/EN browser evidence verifies the labels
  and destinations refer to the intended article and locale and that the
  linked resources can be opened; unavailable media must not produce broken
  links.
- Focused producer and Cozy consumer evidence uses the same effective-content
  contract.  A production-site build may be used as a downstream acceptance
  driver only after those checks; it is not publication authority.
- A separate placement entry point uses selected prepared media and configured
  canonical warehouse paths without rendering, site construction or upload;
  missing artifacts and retained editions/gaps remain explicit.
- `runweb-production.sh` produces the normal Cozy/Arcadia/Antora `website.d`
  tree from existing declarations without media placement, rendering,
  warehouse-payload copying or upload. A repeated build leaves warehouse media
  untouched and does not invoke the placement entry point.
- Local browser checks use an explicit `/media/` warehouse mapping and distinguish
  local retrieval from previously verified public availability. Operation checks
  record placement, reference registration and Web construction separately;
  neither operation implicitly invokes or authorizes remote upload.

## Non-goals

- Editing an unselected package descendant, including a review note, or
  changing its Markdown/Dox syntax to work around a public-content enumeration
  defect.
- A Cozy-side duplicate implementation of SmartDox route, package, link, or
  effective-content resolution.
- A global path type system, all-input validation pass, hash/manifest gate, or
  broad resolver rewrite introduced only to prevent this bounded adapter
  misuse.  The targeted regression fixture is the required protection.
- Manual rewriting of generated HTML paths, redirects, or public links.
- Broad media-currentness or artifact-protection redesign. Canonical placement
  and master backup belong to Phase 75; hash/receipt-proof frameworks are excluded.
- Publication outside the selected articles, artifacts and declared destinations;
  wholesale site replacement or manual modification of generated HTML.
- GitHub staging, commit or push of deployed warehouse payloads, or broad
  untracking/history rewrites of previously tracked source/master files.

- Repeating predecessor preservation/warehouse implementation or redeploying
  accepted media solely to obtain site evidence. Missing publication inputs need
  explicit resolution, not fabricated links or historical-original substitutes.
- New media authoring/rendering, remote upload or republication merely to prove
  the separated local script boundary. An upload requires a separate explicit
  request and its own execution/retrieval evidence.

## Implementation sequence

The current A/B/C sequence at the top of this document replaces the former
producer -> consumer variants -> independent operations -> native-site order.
Implement necessary P751-02 and P751-03 connections together, drive the normal
real-site entry in A, complete real artifact/card consumption in B, then gather
P751-04 integrated acceptance in C. Accepted producer work is reused.

The former broad acceptance list above remains a source of original behavior
and IDs. Its non-current-site portions are explicitly relocated to
[DP-751-01..03](phase-75.1-deferred.md); it does not add prerequisites to A.
Current closure is defined by the current delivery checklist. Historical
per-Slice plans/estimates below do not reimpose the old ordering.

## Validation and release ownership

The owner explicitly selected `each-phase` on 2026-10-06. Each child owns a
successful complete Cozy SBT suite (`sbt --batch test` logically, through the
registered serial SBT runner), focused validation, Step acceptance, its own
independent full Phase review, closure ledger and release commit. No suite is
deferred to the successor; no aggregate-final owner exists for this sequence.
At execution entry, freeze every participating source root and its native
validation boundary. Changed SmartDox producer code requires its own focused
and complete validation before consumer acceptance; site declarations and
warehouse upload wiring require their project-native structural/driver checks.
Do not treat a Cozy test receipt as producer, site, upload or browser evidence.
Site-native validation must also cover the separated local entry points,
repeat Web construction and the configured local warehouse mapping; check the
absence of implicit upload without contacting remote upload services. Preserve
the complete-suite policy unchanged; this adoption runs no tests or scripts.

The original split validated planning documents only. Execution was admitted
on 2026-10-06; implementation itself is not validation or acceptance evidence.
The split needs no prerequisite planning commit.

## Registered implementation Step and Slice

### P751-01: SmartDox producer contract

- Current status: CLOSED, 2026-10-07; accepted local commits `38719e1` and
  `e0f7db2`, with ledger revision 8 PASS. Phase acceptance remains OPEN.
- Historical registration: IN_PROGRESS before the actual Step acceptance
  commits; dated Slice statements below retain that earlier state and evidence.
- Owner: Cozy Phase 75.1 parent, SmartDox producer implementation.
- Update rule: record attributable Slice implementation, then close only after
  parent-selected validation, independent review and Step acceptance.

### P751-01A: Physical include base compatibility (SDX-75-04)

- Current status: independently accepted PASS, 2026-10-06; bounded encoded-URI
  repair and focused revalidation accepted. Step and Phase acceptance remain OPEN.
- Owner: bounded SmartDox repair worker; parent owns acceptance.
- Update rule: preserve this Slice's accepted evidence and all repair history;
  this Slice alone closes neither P751-01 nor the Phase.
- Contract: [producer spec](../../../smartdox/docs/spec/doxsite-physical-include-base.md)
  and [producer design](../../../smartdox/docs/design/doxsite-physical-include-base.md).
- Initial focused evidence: compilation passed; original
  [DoxSitePhysicalIncludeSpec](../../../smartdox/src/test/scala/org/smartdox/doxsite/DoxSitePhysicalIncludeSpec.scala)
  passed 3 of 4 scenarios. The encoded physical root failed at line 85 as
  `P751-01A-ENCODED-PHYSICAL-INCLUDE-001`; that result is not acceptance.
- Bounded repair: `Dox2Parser.Config` constructs the native local `File.toURI`
  parent directly and retains legacy relative/virtual/URL behavior and default
  resolver resources, parameters and text context. Original Builder and four
  DoxSite scenario assertions are retained.
- Focused revalidation: original `DoxSitePhysicalIncludeSpec` plus
  [Dox2ParserPhysicalIncludeSpec](../../../smartdox/src/test/scala/org/smartdox/parser/Dox2ParserPhysicalIncludeSpec.scala),
  covering native spaces/Japanese, literal `%25+hash#`, real relative includes
  and existing non-fetching HTTPS host-root candidate projection:
  `https://example.invalid/docs/source.dox` plus `_fragment.dox` projects
  `https://example.invalid/_fragment.dox` without a fetch.
- Revalidation history: after native file-URI repair, the next run passed 7 of
  8 scenarios; only the added HTTPS expected URL conflicted with unchanged
  legacy host-root projection. Correcting that expectation yielded 8 of 8
  scenarios in 2 suites (`p751-01a-corrected-focused-20261006`), with
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
- Parent-selected accumulator: `DoxSiteSpec` and `Dox2ParserSpec` passed 50 of
  50 scenarios in 2 suites (`p751-01a-accumulator-20261006`), with
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
- Slice acceptance: 58 focused tests passed (8 representative + 50 accumulator);
  independent typed review `phase75-1-p751-01a-step-review-20261006` revision 1
  reports PASS with zero Current Boundary Blockers. Parent accepted P751-01A;
  Step and Phase outcomes remain OPEN.
- Initial admitted estimate: 480 minutes expected; range 420–720 minutes.
- Kroki `8000` remains planned coordinated work, with no port edits in this Slice.

### P751-01B: Effective public-content closure (SDX-75-01/02)

- Current status: independently accepted PASS, 2026-10-06, with 93/93 focused
  tests. Step and Phase acceptance remain OPEN.
- Owner: bounded SmartDox implementation worker; parent owns validation/review.
- Contract: [producer spec](../../../smartdox/docs/spec/doxsite-public-content.md)
  and [producer design](../../../smartdox/docs/design/doxsite-public-content.md).
- Executable evidence: P751-01B-01 ordinary/package identity and resources;
  -02 generic private descendants and nested projects; -03 selected parsed
  bibliography refs; -04 real reciprocal site links; -05 selected publication
  aliases; -06 structured failures; -07 ordinary `.dox` directories and Antora
  siblings, in
  [DoxSitePublicContentSpec](../../../smartdox/src/test/scala/org/smartdox/doxsite/DoxSitePublicContentSpec.scala).
- Initial focused run: 7 tests, 1 passed and 6 failed with an empty-base NPE.
  One owner repair using `./` for the empty base yielded 7 of 7 passing tests
  with the same seven scenarios. This failure and repair history is retained
  separately from acceptance.
- Following four-suite accumulator: 86 tests, 85 passed and 1 failed in
  [DoxSiteSpec](../../../smartdox/src/test/scala/org/smartdox/doxsite/DoxSiteSpec.scala)
  because its legacy positive expectation used an unresolved fragment token.
  Existing fragments render after site-link processing. One test-only repair
  checks four decoded JA/EN canonical link bodies and retains the other
  assertions; this repair changes no production behavior or schema.
- Actual combined focused invocation
  `p751-01b-fragment-repair-validation-20261006`: 93 tests, 93 passed and 0 failed;
  5 suites completed and 0 aborted. The logical `testOnly` invocation covered
  `DoxSitePublicContentSpec`, `DoxSiteSpec`, `DoxSitePhysicalIncludeSpec`,
  `Dox2ParserPhysicalIncludeSpec` and `PdfOperationClassSpec`, with
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`. This is one actual combined
  result covering 7 representative and 86 affected-consumer regression tests.
- Typed validation was accepted for the current 17-path subject
  `p751-01b-validation-subject-20261006` revision 3. Independent typed review
  `phase75-1-p751-01b-step-review-20261006` revision 1 reports PASS with zero
  Current Boundary Blockers in review ledger
  `phase75-1-review-ledger-20261006` revision 4. Parent accepted this Slice;
  Step/Phase acceptance remains OPEN. The accepted Hygiene findings are
  projected verbatim in the [follow-up journal](../journal/2026/10/2026-10-06-phase-75.1-hygiene-follow-up.md).
- P751-01A's physical includes remain accepted; P751-01C retains real Markdown
  parser/AST work and SDX-75-03 remains OPEN. Site/media/Kroki and final full
  validation remain required; no Step or Phase closure is recorded here.

### P751-01C1: Real Markdown parser and shared AST component (SDX-75-03)

- Current status: independently accepted PASS, 2026-10-06; cycle-4 current
  focused evidence is 24/24 and sealed M2 repair re-review accepted zero
  Current Boundary Blockers in review ledger revision 6.
  Step and Phase acceptance remain OPEN.
- Owner: bounded SmartDox implementation worker; parent owns validation/review.
- Update rule: advance only from actual focused validation and independent
  review evidence; preserve accepted A/B and keep C2, Step and Phase outcomes
  OPEN until their complete acceptance.
- Contract: [Markdown producer spec](../../../smartdox/docs/spec/markdown-document-parsing.md)
  and [design](../../../smartdox/docs/design/markdown-document-parsing.md).
- Authored component: package-internal CommonMark/GFM document parser, existing
  YAML split/merge, direct rendered-fragment conversion to shared Dox AST and
  mechanically shared legacy image admission. The body retains literal Dox
  syntax, code and raw HTML; Markdown image association uses transient rendered
  HTML source positions without injected markers or Markdown location claims.
- Authored executable specification:
  [MarkdownDocumentParserSpec](../../../smartdox/src/test/scala/org/smartdox/parser/MarkdownDocumentParserSpec.scala),
  24 Given/When/Then scenarios covering heading hierarchy/title metadata,
  literal inlines/links, lists/quotes/breaks/code/tables, raw HTML and images,
  physical/virtual origins and existing failure categories, including seven
  raw-HTML repair scenarios for quotes, rules, complete/implied tables, literal
  and flow-content anchors and Markdown images inside raw wrappers.
- Parent-selected representative: `testOnly org.smartdox.parser.MarkdownDocumentParserSpec`;
  parent-selected accumulator covers legacy inline/document/Markdown parsers,
  public-content/site/physical-include behavior and PDF operation compatibility.
  Actual invocation `p751-01c1-focused-20261006` passed 17/17 tests in one
  suite; `p751-01c1-accumulator-20261006` passed 171/171 tests in eight suites.
  Both completed with `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
  Those initial 188 passing tests remain historical focused evidence only.
  Independent review identified raw-HTML attribute/child loss and raw-anchor
  failures. The frozen repair retained all original scenarios and added seven.
  Cycle 4 retains the failed Test compilation and subsequent HorizontalRule
  equality abort separately from successful invocation
  `p751-01c1-raw-html-cycle4-observable-test-20261006`: 24/24 tests, no failures
  or aborts, one suite, `sbt_exit=0`, `wrapper_exit=0`, `lock=released`, on typed
  subject `p751-01c1-validation-subject-20261006` revision 6. The earlier
  171/171 accumulator was not rerun for cycle 4. Sealed independent M2 repair
  re-review accepted C1 PASS with zero Current Boundary Blockers in
  `phase75-1-review-ledger-20261006` revision 6. Final full suites remain pending.
  Nine accepted nonblocking Hygiene records are materialized in the
  [follow-up journal](../journal/2026/10/2026-10-06-phase-75.1-hygiene-follow-up.md),
  including `HYG-P751-SDX-HORIZONTAL-RULE-EQUALITY-001`. Scenario 19 compares
  complete ordered node class/attributes/location/rendering projections while
  retaining direct raw/typed-rule assertions; no equality implementation repair
  is admitted.
  Accepted A58/B93 remains predecessor evidence only; it does not validate the
  new parser/dependencies/helper change.
- P751-01C2 remains mandatory in this Step for admitted site-source parser
  selection, `.md`/`.markdown` public HTML aliases/lookup and site scenarios,
  including replacement of the old site HEAD-as-Dox expectation. Site builder,
  legacy executable specs and PDF/publication entry points are untouched in C1.
- Preserve original Phase authority/start/binding/bases and PLAN epoch, accepted
  A/B review history and C1's consumed Step repair cycle 4. Global cumulative
  counts are now Step 6 / Phase 0 after the bounded C2 test-only cycle-5
  correction and parent-verified consumption of the incoming cycle-6 repair.
  C1 records no Step/Phase closure or commit.

### P751-01C2: Admitted Markdown site parsing and public aliases

- Current status: sealed complete-Step review FINDINGS / cycle-6 repair focused
  validated / independent M2 re-review pending / acceptance OPEN, 2026-10-06.
- Owner: bounded SmartDox implementation worker; parent owns validation,
  independent review and exact Step commit.
- Update rule: advance only on actual C2 focused validation and complete-Step
  conformance review, then exact Step acceptance commit. Keep Step/Phase and
  all outcome checklists OPEN until their complete evidence exists.
- Frozen manifest: `phase75-1-implementation-p751-01c2-20261006`, revision 1.
  This is planned implementation under the original authority/binding/bases
  and PLAN epoch. C1's four consumed Step repair cycles remain; the bounded
  C2 test-only correction consumes cycle 5. Phase cycle 0 / nonconverging 0;
  no budget reset is claimed. Frozen repair
  `fix-p751-01c2-incoming-cycle6-20261006` revision 1 addresses
  `CPB-P751-01C2-MARKDOWN-INCOMING-001`; the parent consumed cycle 6 once
  from verified owned changes. The repair is focused validated on subject
  revision 4, with independent M2 re-review pending.
- Only admitted ordinary md/markdown sources use the accepted C1 parser and
  existing Builder context. The shared effective-content owner maps their
  public suffix and resolves HTML aliases in legacy Dox/package, md, markdown
  order through actual parsed pages. Package/private closure, source identity,
  includes/images and legacy parser APIs remain. The bounded repair configures
  LinkCollector/LinkCollection with the parsed tree and resolves incoming and
  reciprocal keys through the sole effective-content owner. Decoded URI paths
  determine document identity without query/fragment, physical package bases
  normalize to logical identity, and authored hrefs stay unchanged. Existing
  public zero-argument constructors and string-only overloads retain behavior.
- [DoxSiteMarkdownSpec](../../../smartdox/src/test/scala/org/smartdox/doxsite/DoxSiteMarkdownSpec.scala)
  authors ten Given/When/Then scenarios: literal Markdown/YAML; Production
  status; nested identities/routes/metadata/Antora; package-private exclusion
  and ordinary siblings; physical/virtual images and traversal; real localized
  Dox-to-Markdown/publication links; structured failures; collision precedence;
  incoming alias identity; reciprocal exclusion with retained observers.
  C2-06 now observes actual incoming sources and target JA/EN Related Articles.
  C2-09 uses uniquely named sources for source/HTML aliases with and without
  fragments, including package physical aliases. C2-10 covers reciprocal source
  and HTML fragment aliases for md, markdown, ordinary Dox and package targets.
  Exact incoming identity/count and localized Related Articles section href/title
  expectations are authored and covered by the focused subject-revision-4 result.
  The old HEAD-as-Dox expectation becomes a Full strategy body-literal scenario.
- Parent-selected representative: `testOnly org.smartdox.doxsite.DoxSiteMarkdownSpec`.
  The nine-suite accumulator covers MarkdownDocumentParserSpec, DoxInlineParserSpec,
  Dox2ParserSpec, DoxMarkdownParserSpec, DoxSitePublicContentSpec, DoxSiteSpec,
  DoxSitePhysicalIncludeSpec, Dox2ParserPhysicalIncludeSpec and PdfOperationClassSpec.
  Actual parent-verified representative
  `p751-01c2-heading-repair-focused-20261006` passed 8/8 in one suite;
  `p751-01c2-accumulator-20261006` passed 195/195 in nine suites. Both use
  `p751-01c2-validation-subject-20261006` revision 2, have zero failures/aborts,
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`, and accepted typed parent
  verification. Current195 includes repaired C1 24 and is distinct from
  historical171. Accepted A58/B93/C1 current24 and historical171 remain
  subject-specific predecessor evidence.
- Initial `p751-01c2-focused-20261006` compiled successfully but passed 7/8;
  failure `TEST-P751-01C2-GENERATED-HEADING-001` is the exact Section-title
  oracle omitting the unchanged site's appended I18NFragment Bibliography
  section, not a product grammar failure. SBT/wrapper exited 1 and the shared
  lock was released. Parent-admitted M1 correction changes only the test oracle
  to exact ordered localized-title mapping, without product repair, filtering
  or expectation weakening. The M1 waiver retains focused8/8 evidence and does
  not waive the mandatory independent complete-Step protected conformance review.
- The subsequent sealed complete-Step protected review reports FINDINGS in
  `phase75-1-review-ledger-20261006` revision 7. Its admitted incoming blocker
  shows that legacy string resolution fabricated Dox keys for Markdown HTML
  aliases and included fragments in document identity; reciprocal exclusion
  used the same calculation. Cycle 6 authors the frozen correction in exactly
  nine paths. Prior subject-revision-2 results remain history and do not validate
  this repair. The parent consumed cycle 6 once from verified owned changes.
  Review ledger revision 7 retains six reviews and zero full reviews; independent
  M2 re-review is required before acceptance. Existing nine Hygiene records remain
  unchanged.
- Initial cycle-6 representative `p751-01c2-incoming-cycle6-focused-20261006`
  compiled on subject revision 3 and passed 9/10, with the shared lock released.
  `TEST-P751-01C2-CANONICAL-INCOMING-COUNT-001` failed only C2-06's incoming
  count, expected 2 versus actual 1. Parent-confirmed unchanged href grouping
  and final recollection of enabled canonical links combine source/HTML aliases.
  One exact count correction to 1 retained the exact source identity and Related
  Articles size-2/href/title assertions; all C2-09/10 counts remained.
- Parent-accepted typed closure `cycle6-focused-closure-p751-01c2-20261006`
  revision 1 records `p751-01c2-incoming-cycle6-combined-20261006` on
  `p751-01c2-validation-subject-20261006` revision 4: 205/205 tests in ten suites,
  zero failures/aborts, `sbt_exit=0`, `wrapper_exit=0`, `lock=released`. One actual
  command covered ten C2 representatives plus the unchanged nine-suite 195-test
  accumulator. This is focused validation, not full repository validation or
  independent blocker closure. Step6/Phase0/nonconverging0 and consumed-once
  cycle6 remain; independent M2 re-review and acceptance are pending.
- Later independent full Phase review, complete SmartDox/Cozy suites and
  site-native/browser validation remain mandatory. This record claims no C2
  acceptance, Step commit, Phase closure, publication or deployment.

### P751-02: Cozy consumer and site recovery (COZY-75-04–08)

- Current status: CLOSED on 2026-10-08
  through the joint P751-02/03 protected connected acceptance and actual local
  commits SmartDox `59d6d41`, Cozy `094370b`, site `73de27e`. Phase release remains OPEN.
- Historical status: IN_PROGRESS, 2026-10-07; all Cozy consumer outcomes and Step
  acceptance remain OPEN.
- Owner: Cozy Phase 75.1 parent; exact Slice ownership is frozen before edits.
- Update rule: close only after all admitted Slices, actual focused consumer/
  site evidence, independent whole-Step review and exact local acceptance.
- Scope retains article-card links first, then Arcadia ownership/CSS after
  evidenced prerequisites, and coordinated Kroki `8000` work. The integration
  prerequisite below does not establish those outcomes or close this Step.

### P751-02A: Accepted public-document generator integration

- Current status: focused validation complete / independent Slice review
  pending / acceptance OPEN, 2026-10-07. Whole P751-02 and Phase remain OPEN.
- Owner: bounded Cozy implementation worker; parent owns dependency refresh,
  source inspection, validation, independent review and transitions.
- Update rule: Slice acceptance requires completed focused evidence and
  independent Slice review. Whole-Step acceptance requires the remaining
  Slices, independent whole-Step review and the exact Step acceptance commit;
  Phase acceptance remains OPEN until its complete evidence exists.
- Contract: [consumer spec](../spec/bok-public-document-integration.md) and
  [consumer design](../design/bok-public-document-integration.md).
- [CozyDocumentProjectSiteProjectionSpec](../../src/test/scala/cozy/publication/CozyDocumentProjectSiteProjectionSpec.scala)
  authors P751-02A-SPEC-01–04 for native physical includes and canonical JA/EN
  HTML/Antora routes; private direct/deep Markdown and bare `_source` exclusion
  from public pages and notice/card metadata; published bilingual YAML and
  literal Dox body grammar in both Markdown suffixes; incoming direct-source/
  HTML aliases with canonical localized hrefs and a real package fragment.
  The original package/sibling title and route assertions remain.
- Historical authored dependency plan (superseded by current evidence below):
  unchanged `org.smartdox:smartdox_2.12:2.4.19-SNAPSHOT`, accepted producer
  commit `38719e1`. Parent-selected local refresh is `publishLocal`;
  representative is `testOnly cozy.publication.CozyDocumentProjectSiteProjectionSpec`;
  accumulator is `CozyArticleMediaBokAcceptanceSpec`, `CozyBokSiteRoutingSpec`
  and `CozyBokBuildTargetSpec`. None is claimed executed by this authorship.
- Historical unresolved integration observation (superseded by current
  evidence below): accepted producer ordinary-hyperlink
  rendering reconstructs a relative URI from the public path, while its site
  hyperlink branch uses the resolved URI. The ordinary Markdown fragment
  assertions remain as frozen. This source observation is not a runtime
  failure; actual parent execution against the pinned dependency must determine
  the result before any separate bounded repair is admitted.
- Current evidence, 2026-10-07: the bounded SmartDox ordinary-hyperlink repair
  preserves authored raw fragments, including existing percent-escapes.
  Focused producer validation passed 206/206 tests in ten suites. Local
  `publishLocal` refreshed the unchanged `2.4.19-SNAPSHOT` coordinate; Cozy
  dependency `update` and the consumer representative passed, with 4/4 tests.
  The separate accumulator passed 11/11 tests in three suites. Each SBT
  invocation completed with `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
  Original accepted producer commit `38719e1` remains the baseline; the
  uncommitted fragment repair is not part of that commit. These completed
  results supersede the historical authored plan and unresolved observation;
  independent Slice review remains pending, and whole P751-02 and Phase
  acceptance remain OPEN.
- No Cozy production source is changed and no assertion is weakened. Real
  whole-Cozy metadata walking,
  article cards, Arcadia/CSS, coordinated Kroki and native `website.d`/browser
  proof remain pending; generator evidence alone cannot close them. Phase 71
  and predecessor preservation/deployment remain outside this Slice.

### Current P751-02A accepted Slice projection (2026-10-07)

- Current status: independently accepted PASS, review ledger revision 9
  (eight reviews, zero full Phase reviews). This supersedes earlier pending
  P2A review prose while retaining its historical receipts and repair cycle 1.
- Accepted evidence: four Cozy consumer scenarios and eleven accumulator
  regression tests, plus 206 SmartDox producer tests and the unchanged
  `2.4.19-SNAPSHOT` local refresh. P2A is an accepted Slice only;
  P751-02 and Phase acceptance remain OPEN. P751-01 remains CLOSED.
- Owner: Phase 75.1 parent; no native/card/browser/full outcome is inferred.
- Update rule: preserve exact accepted Slice subject and history; Step closure
  requires the remaining Slices, complete-Step review and actual local commit.

### P751-02B: Canonical warehouse build consumption

- Current status: IN_PROGRESS, 2026-10-07. Implementation and executable
  scenarios are authored; focused validation and independent review are pending.
- Owner: bounded Cozy implementation worker; parent owns diff verification,
  validation, independent review and every acceptance transition.
- Update rule: close only on parent-selected focused evidence and independent
  review. This registration checks off no article-card, media/CSS, native,
  browser, full-suite, Step, release or Phase outcome.
- Contract: [article-media spec](../spec/article-media-publication.md) and
  [design](../design/article-media-publication.md); executable boundary:
  [CozyArticleMediaWarehouseBuildContextSpec](../../src/test/scala/cozy/publication/CozyArticleMediaWarehouseBuildContextSpec.scala).
- Scope: strict current-site admission, shared canonical coordinates, direct
  warehouse availability, exact role omission, preserved external YouTube,
  admitted wrapper/generic preservation and unchanged legacy-policy dispatch.
- Parent-selected validation remains planned: new warehouse context Spec as
  representative; existing BuildContext/Policy/Association/Warehouse/Root/
  Registry/BokAcceptance/DocumentProjectSiteProjection Specs as accumulator.
  P751-02/03/04 and Phase remain OPEN; site-native/browser evidence remains pending.

### Current P751-02B accepted Slice projection (2026-10-07)

- Current status: independently accepted PASS as a Slice only. Actual focused
  evidence is 12/12 representative tests and 165/165 accumulator tests.
  Independent source review and the parent's M0 disposition are accepted in
  review ledger revision 11. This supersedes the earlier authored/pending
  P2B status while preserving its dated history.
- Owner: Phase 75.1 parent; validation, review and transitions remain with the
  parent. Original authority, bases, binding and PLAN epoch remain unchanged;
  cumulative Step repair 6 / Phase repair 0 / nonconverging 0 are retained.
- Update rule: P751-02B acceptance does not close P751-02 or Phase 75.1 and
  supplies no rendered-card, normal native site, browser or full-suite claim.

### P751-02C: Native article-card rendering

- Current status: AUTHORED / IN_PROGRESS, 2026-10-07; six native generator
  scenarios and three ordinary classpath card-widget fixtures are authored.
  Focused execution and independent review remain pending.
- Owner: bounded Cozy implementation worker; parent owns diff verification,
  focused validation, independent review and all acceptance transitions.
- [Consumer spec](../spec/bok-public-document-integration.md),
  [design](../design/bok-public-document-integration.md) and
  [CozyArticleCardRenderingSpec](../../src/test/scala/cozy/publication/CozyArticleCardRenderingSpec.scala)
  define normal availability context to actual SmartDox export/raw Notice to
  pinned native Arcadia, all three JA/EN home/category widgets, exact roles,
  gaps, external statuses, optional labels and private-package closure.
- Parent-selected validation is planned: the six-scenario representative
  and nine-suite regression accumulator. No worker validation is claimed.
  The fixture templates do not certify live SimpleModeling templates.
- Update rule: authorship closes no COZY-75-08, Step or Phase outcome.
  P751-02/03/04, normal `website.d`, explicit local `/media/` mapping,
  native/browser/full evidence and final Phase review/release remain OPEN.

### Current P751-02C focused evidence projection (2026-10-07)

- Current status: focused validation complete / independent complete-19-path
  interim Step conformance review pending / Slice acceptance OPEN.
  The authored/pending P2C registration above remains historical dated state
  and is superseded here for current focused execution status only.
- Actual parent evidence for `p751-02c-native-card-subject-20261007` revision 1:
  `p751-02c-card-rendering-focused-20261007` passed 6/6 tests in one suite;
  `p751-02c-card-regression-accumulator-20261007` passed 177/177 tests in nine
  suites. Both completed with `sbt_exit=0`, `wrapper_exit=0`, `lock=released`,
  zero failures/aborts/canceled/ignored/pending tests, and accepted public
  current-request readers. Representative proof is recorded in
  `.codex-workflow/phases/PHASE-75.1/card-rendering-focused-validation-20261007.json`;
  accumulator proof is the actual invocation log and its serial summary.
- This projection changes documentation only; actual runtime subject-1
  evidence remains applicable to its unchanged runtime inputs. The parent
  owns the documentation subject revision and subsequent review disposition.
- No Slice, Step or Phase closure or commit is claimed. Live SimpleModeling
  templates, normal native `website.d`, local `/media/` mapping, browser/full
  evidence and final Phase review/release remain OPEN.

### Current P751-02C accepted interim Slice projection (2026-10-07)

- Current status: independent interim PASS, recorded in review ledger revision
  12 by `p751-02c-independent-slice-review-20261007` revision 1.
  This supersedes the earlier pending review projection for current P2C
  acceptance while preserving the dated history and actual 6/6 native
  representative and 177/177 nine-suite accumulator results.
- Owner: Phase 75.1 parent. Acceptance is interim Slice evidence only;
  P751-02 and Phase remain OPEN. Live SimpleModeling templates, normal native
  site/browser acceptance, full validation and final release are not inferred.

### P751-02D: Configured Arcadia category ownership (2026-10-07)

- Current status: AUTHORED / IN_PROGRESS. The bounded six-path implementation
  and [category executable specification](../../src/test/scala/cozy/bok/CozyBokArcadiaCategoryOwnershipSpec.scala)
  implement the dated [spec](../spec/bok-site-home-ownership.md) and
  [design](../design/bok-site-home-ownership.md) extension.
- Owner: bounded implementation worker; parent owns diff verification,
  validation, independent review and acceptance transitions.
- Scenario mapping: P751-02D-01 default destinations/both layouts;
  P751-02D-02 custom producer and website/both layouts; P751-02D-03 enabled
  homes-only category fallback; P751-02D-04 disabled/stale producer output;
  P751-02D-05 exact JA/category-A and EN/category-B ownership;
  P751-02D-06 Antora-only final category fallback. Actual Cozy builds use
  emulated external products; this is composition, not native site proof.
- Parent-selected representative remains planned:
  `testOnly cozy.bok.CozyBokArcadiaCategoryOwnershipSpec`.
  The affected eight-suite accumulator is SiteRoutingSpec, CozyBokSpec,
  CozyBokDashboardSpec, CozyBokMetadataFinalizationSpec, CozyBokBuildTargetSpec,
  CozyDocumentProjectSiteProjectionSpec, CozyArticleMediaSiteBindingSpec and
  CozyArticleCardRenderingSpec, retaining the existing fifty-trial CSS property.
- Update rule: authorship checks off no COZY-75-07, Step or Phase outcome.
  P2D runtime and independent review remain OPEN. Original authority/binding/
  bases, PLAN epoch 1, ledger 12 (eleven reviews, zero full), Step repair 6 /
  Phase repair 0 / nonconverging 0 and 35 consumed SBT identities are retained.
  Coordinated Kroki 8000, Web-only production script/prepared-media separation,
  normal `website.d` with explicit local `/media/` mapping, independent final
  full Cozy/changed SmartDox suites, native/browser validation, one epoch-1
  full Phase review and exact local Step/release commits remain mandatory.

### Current P751-02D accepted interim Slice projection (2026-10-07)

- Current status: independent interim PASS, review
  `p751-02d-independent-slice-review-20261007` revision 1, recorded in review
  ledger revision 13 (twelve reviews, zero full Phase reviews). Earlier P2D
  authored/pending statements remain dated history and are superseded only
  for current interim Slice acceptance.
- Actual accepted subject revision 2 passed the same six category scenarios,
  6/6 representative tests, and 134/134 tests in the eight-suite accumulator.
  Both completed with `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
  The initial 2/6 failure and the frozen headline-fixture FIX7 remain history;
  the repair preserved product behavior and assertions.
- Owner: Phase 75.1 parent. Original authority/binding/bases and PLAN epoch 1
  remain; cumulative Step repair 7 / Phase repair 0 / nonconverging 0 and all
  38 consumed SBT identities are retained. P751-01 remains CLOSED.
- Update rule: interim Slice acceptance closes neither whole P751-02 nor
  Phase 75.1. Emulated external products do not establish actual native
  Arcadia/site/browser evidence, full validation or acceptance/release commits.

### P751-02E: Coordinated Kroki endpoint (2026-10-07)

- Current status: AUTHORED / IN_PROGRESS under frozen implementation manifest
  `p751-02e-kroki-implementation-manifest-20261007` revision 2. Parent-owned
  focused execution and independent complete-31-path interim Step review
  remain pending; authorship is not acceptance.
- Owner: bounded implementation worker; parent owns diff verification,
  validation, artifact refresh, independent review and every transition.
- [Cozy specification](../spec/bok-site-toolchain.md) and
  [design](../design/bok-site-toolchain.md) pair the existing Docker Antora
  settings `SMARTDOX_KROKI_PORT=8000` and `KROKI_LISTEN=127.0.0.1:8000`.
  SmartDox's [producer specification](../../../smartdox/docs/spec/antora-kroki-endpoint.md)
  and [design](../../../smartdox/docs/design/antora-kroki-endpoint.md) set the
  generated `asciidoc.attributes.kroki-server-url` default to
  `http://localhost:8000`. The existing wrapper uses the selected port for Java
  launch/readiness; actual installed-image listener startup remains unproven.
- [AntoraKrokiEndpointSpec](../../../smartdox/src/test/scala/org/smartdox/generators/AntoraKrokiEndpointSpec.scala)
  authors two active generated-product behaviors: the single JA root playbook
  and both JA/EN locale playbooks. Actual generator Realm products are parsed
  as YAML for endpoint, SVG/fetch and wrong-layout absence. The existing
  [CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala) configured-toolchain
  scenario authors actual build orchestration in both layouts, exact one/two
  recorded Antora commands, paired port settings, absence of obsolete `9609`
  argv and all three host image settings.
- Parent-selected sequence, still planned: producer endpoint representative;
  producer accumulator with `AntoraKrokiEndpointSpec`, `DoxSiteGeneratorSpec`
  and `KrokiGeneratorSpec`; exact local `publishLocal` refresh of unchanged
  `org.smartdox:smartdox_2.12:2.4.19-SNAPSHOT`; Cozy dependency `update` and the
  configured-toolchain representative; then the nine-suite Cozy accumulator:
  `CozyBokSpec`, `CozyBokSiteRoutingSpec`, `CozyBokArcadiaCategoryOwnershipSpec`,
  `CozyBokDashboardSpec`, `CozyBokMetadataFinalizationSpec`,
  `CozyBokBuildTargetSpec`, `CozyDocumentProjectSiteProjectionSpec`,
  `CozyArticleMediaSiteBindingSpec` and `CozyArticleCardRenderingSpec`.
  The three existing ignored producer cases (`mini`, `plain`, `dfn`) remain
  unchanged and cannot establish endpoint acceptance. No worker execution is
  claimed; Cozy stays at `0.3.3-SNAPSHOT`.
- Update rule: recorded argv and generated playbooks supply separate focused
  seams only. COZY-75-05 actual listener/native evidence, whole Step and Phase
  stay OPEN. Prepared-media/Web-only script separation, normal `website.d`,
  explicit local `/media/` mapping, JA/EN browser resource/link/layout checks,
  independent final full Cozy and changed SmartDox suites, one epoch-1 full
  Phase review and exact Step/release commits remain required. Preserve Phase
  75 deployment/editions/gaps and concurrent Phase 71 Arcadia/CSS work.

### Current P751-02E accepted interim Slice projection (2026-10-07)

- Current status: independent interim PASS in
  `p751-02e-independent-slice-review-20261007` revision 1, review ledger
  revision 14: thirteen reviews and zero full Phase reviews. Earlier P2E
  authored/pending snapshots remain history. Current retained Hygiene is 13
  accepted nonblocking items; Development Candidates remain zero.
- Actual retained evidence: SmartDox endpoint representative 2/2 and producer
  accumulator 21/21; exact unchanged SNAPSHOT local refresh; Cozy configured
  toolchain representative 1/1 and nine-suite accumulator 140/140. Accepted
  executions returned `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
  These results establish the generated endpoint/command seams; native
  listener/site execution remains separate.
- Owner: Phase 75.1 parent. Original active Goal, authority/binding revision 1,
  original bases and PLAN epoch 1 remain, with Step repair 7 / Phase repair 0 /
  nonconverging 0. P751-01 remains CLOSED; P751-02/03/04 and Phase remain OPEN.

### P751-02F: Standalone real-native site prerequisite (2026-10-07)

- Current status: AUTHORED / IN_PROGRESS; compilation completed in FIX11,
  whose native cases failed at the intermediate HomeOnly oracle. Fresh repair12
  native execution and independent complete-32-path/15-target interim Step
  review are pending.
  The bounded worker owns only the driver and these four English projections;
  the parent owns verification, execution, review and transitions.
- [Specification](../spec/bok-site-toolchain.md),
  [design](../design/bok-site-toolchain.md) and
  [driver](../../src/test/scala/cozy/bok/CozyBokNativeSiteDriver.scala) register
  P751-02F-NATIVE-01 SingleLocaleRoot JA and P751-02F-NATIVE-02
  MultiLocaleSubdirs JA/EN. Both require actual normal Cozy composition,
  selected current SmartDox/Cozy Runtime exports, pinned Arcadia 1.0.3,
  installed Antora/Kroki image, canonical ordinary inline SVG and separately
  fetched endpoint inline SVG, public/include/private
  closure, native home/category ownership, locale CSS and owned cleanup.
- Parent-selected sequence: canonical SmartDox `exportClasspath`, Cozy
  `cozyExportRuntimeClasspath`, then explicit `Test / runMain` with the four
  literal arguments documented in the specification. The accepted current
  P2E accumulator is retained only under parent relevance admission; no
  driver execution or validation success is claimed by authorship.
- This prerequisite closes no requirement, whole Step or Phase. Preserve
  concurrent Phase 71 and accepted predecessor deployment/editions/gaps.
  Production template/normal SimpleModeling entry proof, prepared-media/
  Web-only separation, repeated normal `website.d`, explicit local `/media/`,
  JA/EN browser evidence, independent final full Cozy/changed SmartDox suites,
  one epoch-1 comprehensive Phase review and exact local Step/release commits
  remain mandatory.

### Current P751-02F repair12 projection (2026-10-07)

- Owner: Phase 75.1 parent; the bounded repair worker owns only five frozen
  Cozy native-oracle/specification/design/Phase projection paths. The
  complete Step remains 32 paths and 15 target programs. Parent owns
  validation, independent review and transitions.
- Update rule: supersede stale current pending projections only for recorded
  partial results; retain all failed runs/products and close no acceptance item
  until actual fresh native evidence and independent review are admitted.
- Status: FIX8/9 resolved the Scala compile failures. FIX10 compiled, and
  its actual SingleJA native SmartDox/Antora execution passed the secure
  canonical inline-SVG oracle. The separately published endpoint page
  contained `kroki-error`: Antora resolved `http://localhost:8000` to IPv6
  `::1:8000` and failed with `ECONNREFUSED`, while the existing listener
  selected IPv4 `127.0.0.1:8000`. SBT/wrapper returned 1 with `lock=released`;
  owned processes were reaped and the allocated container was confirmed absent.
  FIX11's generated IPv4 default and matching expectations passed the actual
  producer accumulator 21/21 (three preexisting ignores separate), typed
  same-coordinate `org.smartdox:smartdox_2.12:2.4.19-SNAPSHOT` local refresh
  and nine-suite Cozy accumulator 140/140. Both original native cases failed
  reading nested article HTML from intermediate HomeOnly `doxsite.d`, despite
  fresh endpoint SVG in all three locale trees and complete cleanup: zero
  owned processes and all three allocated containers absent. Neither case,
  P751-02F, whole Step nor Phase is accepted by these partial results.
  Repair12 requires unchanged `home_only` configuration, exactly one actual
  `dox site` command and adjacent `-output.scope.policy home_only` argv pair,
  intermediate ordinary/package HTML absence, actual global/category canonical
  Notice keys for every locale and the three exact article-body expectations
  on Antora-owned final `website.d` ordinary/package pages. All secure SVG,
  native Arcadia/widget/CSS/private/cleanup predicates remain strict; incidental
  cache/hash behavior is unchanged. Fresh both-case execution and independent
  complete-32-path/15-target review remain pending. Dated accepted P2E
  localhost seam results remain historical evidence.
- Evidence: `.codex-workflow/phases/PHASE-75.1/native-interpolation8-compile-failure-20261007.json`
  and `native-locale9-product-shape-failure-20261007.json`, followed by
  `native-oracle10-endpoint-failure-20261007.json` and
  `native-address11-public-html-failure-20261007.json`, retain the lineage.
  Old `target/phase751-native-site-20261007` and
  `target/phase751-native-site-repair10-20261007` and
  `target/phase751-native-site-repair11-20261007` products remain retained. The
  selected fresh invocation uses the absent
  `target/phase751-native-site-repair12-20261007` workspace and literal
  `Test / runMain` command documented in the paired specification.
  Generated-docs fixture Git
  registration records only the new endpoint path in an existing producer
  repository, or the fresh generated docs initialization; it is isolated
  fixture provenance, not source Git or acceptance evidence.
- P751-02 and Phase remain OPEN. Original normal SimpleModeling/Web-only/media
  separation, repeated `website.d`, explicit `/media/`, browser/full suites,
  sole epoch-1 full review and exact Step/release/closure obligations remain.
  Preserve Phase 75 deployment/editions/gaps and concurrent Phase 71 work.

## References

- [Predecessor Phase 75 and retained history](phase-75.md)
- [Phase 75.1 checklist](phase-75.1-checklist.md)
- [Frozen split proposal](../notes/phase-75-split-proposal.md)
- [Independent media placement and Web-build operating policy](../journal/2026/10/2026-10-06-media-web-upload-operating-policy.md)
- [SmartDox PDF site-link FQN handoff](../journal/2026/09/2026-09-07-smartdox-pdf-site-link-fqn-handoff.md)
- [Document Project public URL normalization handoff](../journal/2026/09/2026-09-08-document-project-public-url-normalization-handoff.md)
