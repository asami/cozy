# Phase 75.1: Public Document Contract and Site Recovery

status=in-progress
split_full_test_policy=each-phase
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
depends_on=PHASE-75
primary_owner=Cozy
producer_dependency=SmartDox effective-content contract
predecessor=PHASE-75

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

Prioritize COZY-75-08 article links, then COZY-75-07 page ownership, using the
predecessor's accepted declaration/URL handoff. Take only concrete producer,
CSS or Kroki prerequisites ahead of that outcome order. The producer/consumer
closure remains required; do not regenerate the production tree until the
preservation, exact source boundary and page-ownership safeguards are established.
In the same bounded site integration, specify and separate COZY-75-09 before
the downstream production run. Use the accepted existing placement handoff;
do not repeat predecessor deployment just to exercise the new entry point.

1. Freeze the SmartDox effective-content API, fixed package-input inventory,
   and Markdown-source parsing behavior with focused producer specifications.
2. Update each SmartDox site consumer to use that API, with no physical-path
   conditionals outside the producer abstraction and no Dox parser applied to
   Markdown source.
3. Add the focused include-compatibility regression: an absolute physical base
   and relative include must resolve through the effective source without
   changing the established resolver contract.
4. Refresh the exact local SmartDox development dependency and run Cozy's
   focused consumer evidence.
5. Coordinate the Cozy Kroki-port adapter with producer launch, readiness,
   listener and Antora endpoint settings for target `8000`, then run focused
   producer and consumer contract specifications.
6. Verify locale-local CSS resolution and Arcadia-owned page preservation with
   focused regressions, including single-locale and non-Arcadia controls.
7. Promote the operating boundary into design/spec, separate the prepared-media
   placement script and Web-only production entry point, and add site-native
   checks for independence, missing inputs and absence of rendering/upload.
   Configure the local `/media/` mapping for browser review without copying
   warehouse payloads into `website.d`.
8. Run downstream local site-build and browser verification; record selected
   artifact deployment separately from local build success. Stop for operator
   inspection before any separately requested upload, then
   conduct the required review and release workflow in a separately authorized
   execution of Phase 75.1.

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

- Current status: IN_PROGRESS; all outcome acceptance criteria remain OPEN.
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

## References

- [Predecessor Phase 75 and retained history](phase-75.md)
- [Phase 75.1 checklist](phase-75.1-checklist.md)
- [Frozen split proposal](../notes/phase-75-split-proposal.md)
- [Independent media placement and Web-build operating policy](../journal/2026/10/2026-10-06-media-web-upload-operating-policy.md)
- [SmartDox PDF site-link FQN handoff](../journal/2026/09/2026-09-07-smartdox-pdf-site-link-fqn-handoff.md)
- [Document Project public URL normalization handoff](../journal/2026/09/2026-09-08-document-project-public-url-normalization-handoff.md)
