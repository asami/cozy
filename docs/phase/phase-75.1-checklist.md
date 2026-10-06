# Phase 75.1 Checklist: Public Document Contract and Site Recovery

Phase status: IN_PROGRESS / P751-01B independently accepted PASS (93/93,
review ledger revision 4); P751-01C1 independently accepted PASS after cycle 4
(current24 focused tests, sealed M2 review, ledger revision 6; earlier171
regression evidence retained, not rerun); P751-01C2 prior focused validation passed
(8/8 representative, 195/195 nine-suite accumulator, subject revision 2),
sealed complete-Step protected review FINDINGS (ledger revision 7), cycle-6
incoming/reciprocal repair focused validated (subject4, 205/205 in ten suites,
one representative-plus-accumulator command, not full repository validation);
cumulative Step6/Phase0/nonconverging0,
cycle6 consumed once by the parent from verified owned changes;
independent M2 re-review pending; ledger7 retains six reviews/full0;
Step and Phase acceptance OPEN, 2026-10-06
Ledger for: [Phase 75.1](phase-75.1.md)
Split from: [Phase 75](phase-75.md), 2026-10-06
Validation: each-phase / sbt-full-suite; no aggregate owner or deferral.
Planning update: COZY-75-09 adopts the
[independent placement/Web/upload policy](../journal/2026/10/2026-10-06-media-web-upload-operating-policy.md),
2026-10-06. All new execution items remain OPEN; Phase 75 stays closed.

Current acceptance update (2026-10-07): the independent cycle-6 M2 re-review
reports PASS and resolves `CPB-P751-01C2-MARKDOWN-INCOMING-001`. Review ledger
revision 8 contains seven reviews and zero full Phase reviews. P751-01C2 and
the complete producer Step are semantically accepted for the exact local Step
commit; that commit is still pending. Earlier pending-review statements are
retained pre-review history, superseded for current semantic acceptance only.
Step6/Phase0/nonconverging0 and all failures remain history. Phase acceptance
and outcome checklists stay OPEN pending the remaining integration, full
validation and Phase review/release. No deployment is claimed.

## Current Step and Slice

### P751-01: SmartDox producer contract

- Current status: IN_PROGRESS; acceptance remains OPEN.
- Owner: Cozy Phase 75.1 parent and SmartDox producer.
- Update rule: close only after parent-selected validation, independent review
  and exact Step acceptance; implementation does not check off outcomes.

### P751-01A: Physical include base compatibility

- Current status: independently accepted PASS, 2026-10-06; bounded encoded-URI
  repair and focused revalidation accepted. Step and Phase acceptance remain OPEN.
- Owner: bounded SmartDox repair worker; parent owns acceptance.
- Update rule: preserve accepted Slice evidence and repair history; all Step
  and Phase outcome checklists remain OPEN until their complete acceptance.
- [Producer spec](../../../smartdox/docs/spec/doxsite-physical-include-base.md)
  and [design](../../../smartdox/docs/design/doxsite-physical-include-base.md).
- Initial focused evidence: compilation passed; original
  [DoxSitePhysicalIncludeSpec](../../../smartdox/src/test/scala/org/smartdox/doxsite/DoxSitePhysicalIncludeSpec.scala)
  passed 3 of 4 scenarios, with encoded physical root failure at line 85
  (`P751-01A-ENCODED-PHYSICAL-INCLUDE-001`). The failed result is not acceptance.
- Bounded repair: native local `File.toURI` parent context in `Dox2Parser.Config`,
  retaining default resolver resources, parameters, text context and legacy
  relative/virtual/URL behavior. Original Builder and four DoxSite assertions
  remain; no outcome is checked off.
- Focused revalidation: original `DoxSitePhysicalIncludeSpec` plus
  [Dox2ParserPhysicalIncludeSpec](../../../smartdox/src/test/scala/org/smartdox/parser/Dox2ParserPhysicalIncludeSpec.scala)
  for native spaces/Japanese, literal `%25+hash#`, real relative inclusion and
  existing non-fetching HTTPS host-root candidate projection:
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
  typed independent review `phase75-1-p751-01a-step-review-20261006` revision 1
  reports PASS with zero Current Boundary Blockers. Parent accepted P751-01A;
  P751-01 and Phase acceptance remain OPEN.
- Initial admitted estimate: 480 minutes expected; range 420–720 minutes.
- Kroki target `8000` remains planned coordinated producer/consumer work;
  current launch/readiness/listener/Antora endpoint overrides use `9609`.
  A consumer-only change is insufficient, and this Slice makes no port edit.

### P751-01B: Effective public-content closure

- Current status: independently accepted PASS, 2026-10-06, with 93/93 focused
  tests. Step and Phase acceptance remain OPEN.
- Owner: bounded SmartDox implementation worker; parent owns validation/review.
- [Producer spec](../../../smartdox/docs/spec/doxsite-public-content.md)
  and [design](../../../smartdox/docs/design/doxsite-public-content.md).
- Seven
  [DoxSitePublicContentSpec](../../../smartdox/src/test/scala/org/smartdox/doxsite/DoxSitePublicContentSpec.scala)
  scenarios: P751-01B-01 ordinary/package
  identity and resources; -02 generic private closure/nested projects; -03 parsed
  bibliography refs; -04 reciprocal HTML links; -05 publication aliases;
  -06 structured failures; -07 ordinary `.dox` directory/Antora siblings.
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
  Step/Phase acceptance remains OPEN. Accepted Hygiene records are projected
  verbatim in the [follow-up journal](../journal/2026/10/2026-10-06-phase-75.1-hygiene-follow-up.md).
- Retain accepted P751-01A physical includes and P751-01C real Markdown parser/AST
  requirement. SDX-75-03, site/media/Kroki work and final full validation remain
  OPEN; this registration checks off no Step or Phase outcomes.

### P751-01C1: Real Markdown parser and shared AST component

- Current status: independently accepted PASS, 2026-10-06; cycle-4 current
  focused evidence is 24/24 and sealed M2 repair re-review accepted zero
  Current Boundary Blockers in review ledger revision 6.
  All Step and Phase outcome checklists remain OPEN.
- Owner: bounded SmartDox implementation worker; parent owns validation/review.
- Update rule: advance only from actual focused validation and independent
  review evidence; preserve accepted A/B and keep C2, Step and Phase outcomes
  OPEN until their complete acceptance.
- [Markdown producer spec](../../../smartdox/docs/spec/markdown-document-parsing.md)
  and [design](../../../smartdox/docs/design/markdown-document-parsing.md).
- Authored CommonMark/GFM parser, YAML metadata merge and direct shared-AST
  conversion of its rendered HTML fragment; shared legacy image admission uses
  existing physical/virtual origin and diagnostic semantics. Original Markdown
  body syntax is never reinterpreted as Dox. No site/PDF/global parser migration
  is included in C1.
- [MarkdownDocumentParserSpec](../../../smartdox/src/test/scala/org/smartdox/parser/MarkdownDocumentParserSpec.scala)
  authors 24 Given/When/Then scenarios for headings/title/metadata, Unicode and
  literal Dox syntax, markup/links, lists/quotes/breaks, code/tables, raw HTML
  image distinction and physical/virtual image failures, including seven
  repaired raw-HTML quote/rule/table/anchor/image-wrapper contracts. Initial representative
  `testOnly org.smartdox.parser.MarkdownDocumentParserSpec` passed all 17
  scenarios and supplies historical `Test / compile` evidence.
- Actual invocation `p751-01c1-focused-20261006` passed 17/17 tests in one
  suite; `p751-01c1-accumulator-20261006` passed 171/171 regression tests in
  eight suites. Both completed with `sbt_exit=0`, `wrapper_exit=0`,
  `lock=released`. These initial 188 passing tests remain historical focused
  evidence. Independent review identified raw-HTML attribute/child loss and
  raw-anchor failures; the frozen repair retained the 17 original scenarios
  and added seven. Cycle-4 failed Test compilation and subsequent HorizontalRule
  equality abort remain retained separately from current successful invocation
  `p751-01c1-raw-html-cycle4-observable-test-20261006`: 24/24 tests, no failures
  or aborts, one suite, `sbt_exit=0`, `wrapper_exit=0`, `lock=released`, on typed
  subject `p751-01c1-validation-subject-20261006` revision 6. The earlier
  171/171 accumulator was not rerun in cycle 4. Sealed independent M2 repair
  re-review accepts C1 PASS with zero Current Boundary Blockers in
  `phase75-1-review-ledger-20261006` revision 6. Final full validation stays pending.
  Nine accepted nonblocking Hygiene records are materialized in the
  [follow-up journal](../journal/2026/10/2026-10-06-phase-75.1-hygiene-follow-up.md),
  including `HYG-P751-SDX-HORIZONTAL-RULE-EQUALITY-001`; scenario 19 retains
  raw/typed-rule expectations through complete ordered node projections without
  repairing equality.
  A58/B93 evidence is retained for its accepted predecessor subjects only.
- P751-01C2 remains required in P751-01 for site-source parser selection,
  `.md`/`.markdown` public HTML aliases/lookup and site behavioral coverage,
  including replacement of the old site HEAD-as-Dox expectation. C2 is separately
  frozen and authored as registered below. SDX-75-03, Step and Phase acceptance stay OPEN.
- Original Phase authority/start/binding/bases/PLAN epoch and accepted A/B
  history and C1's consumed Step repair cycle 4 remain. Global cumulative counts
  are now Step 6 / Phase 0 after the C2 test-only cycle-5 correction and
  parent-verified consumption of the incoming cycle-6 repair. This slice records
  no Step/Phase closure or commit.

### P751-01C2: Admitted Markdown site parsing and public aliases

- Current status: sealed complete-Step review FINDINGS / cycle-6 repair focused
  validated / independent M2 re-review pending / acceptance OPEN, 2026-10-06.
- Owner: bounded SmartDox implementation worker; parent owns validation/review
  and exact Step acceptance commit.
- Update rule: preserve accepted A/B/C1, original authority/binding/bases/epoch
  and C1 cycle4 history; global counts now Step6/Phase0, nonconverging0, no reset;
  cycle6 consumed once by the parent from verified owned changes.
  Advance only from actual C2 validation and
  complete-Step review, then exact Step commit; keep outcome checklists OPEN.
- Frozen manifest `phase75-1-implementation-p751-01c2-20261006` revision 1
  selects the accepted Markdown parser after admission and extends the sole
  effective-content owner for ordinary md/markdown public HTML and parsed aliases.
  Historical Dox/package candidate wins, then md, then markdown; no reparse,
  filesystem scan, fabricated Dox source or consumer route mapper is added.
- Frozen repair `fix-p751-01c2-incoming-cycle6-20261006` revision 1 addresses
  `CPB-P751-01C2-MARKDOWN-INCOMING-001` from sealed review ledger revision 7.
  The sole owner resolves decoded URI paths from effective logical bases,
  excludes query/fragment from document identity and uses actual parsed target
  logical paths. Configured scanner/builders and reciprocal filtering share
  that tree-aware calculation; public zero-argument construction and legacy
  string-only overloads retain behavior. Authored hrefs and established rendered
  fragment policy remain unchanged. No scan, reparse or URL policy is added.
- [DoxSiteMarkdownSpec](../../../smartdox/src/test/scala/org/smartdox/doxsite/DoxSiteMarkdownSpec.scala)
  authors P751-01C2-01–10 with adjacent Given/When/Then for grammar/YAML,
  Production status, nested routes/metadata/Antora, private package closure and
  ordinary siblings, physical/virtual images/traversal, real localized site/
  publication links, structured failures and parsed collision precedence.
  Cycle 6 strengthens C2-06 with actual incoming sources and target JA/EN
  Related Articles; C2-09 adds unique source/HTML alias and fragment cases,
  including package physical aliases; C2-10 excludes reciprocal source/HTML
  fragment aliases while retaining observers across md, markdown, Dox and
  package controls. Exact incoming identities/counts and localized Related
  Articles section href/title sets are authored and covered by the focused
  subject-revision-4 result.
  New fixtures own unique `target/doxsite-markdown` directories. The modified
  legacy HEAD scenario covers Full strategy literal status and parsed metadata.
- Selected representative: `testOnly org.smartdox.doxsite.DoxSiteMarkdownSpec`;
  selected accumulator: MarkdownDocumentParserSpec, DoxInlineParserSpec,
  Dox2ParserSpec, DoxMarkdownParserSpec, DoxSitePublicContentSpec, DoxSiteSpec,
  DoxSitePhysicalIncludeSpec, Dox2ParserPhysicalIncludeSpec, PdfOperationClassSpec.
  Parent-verified `p751-01c2-heading-repair-focused-20261006` passed 8/8 tests
  in one suite; `p751-01c2-accumulator-20261006` passed 195/195 in nine suites.
  Both use `p751-01c2-validation-subject-20261006` revision 2, have zero
  failures/aborts, `sbt_exit=0`, `wrapper_exit=0`, `lock=released`, and accepted
  typed parent verification. Current195 includes repaired C1 24 and remains
  distinct from historical171 and A58/B93/currentC1 24 predecessor evidence.
- Initial `p751-01c2-focused-20261006` compiled successfully but passed 7/8;
  `TEST-P751-01C2-GENERATED-HEADING-001` failed the exact Section-title oracle
  because the unchanged site appends an I18NFragment Bibliography section.
  SBT/wrapper exited 1 and the lock was released. The parent-admitted M1
  correction changes only the oracle to exact ordered localized-title mapping,
  without product repair, filtering or expectation weakening. The M1 waiver
  retains focused8/8 evidence and does not waive independent complete-Step
  protected conformance review. This consumes cumulative Step cycle 5 / Phase 0.
- The subsequent sealed complete-Step protected review reports FINDINGS in
  `phase75-1-review-ledger-20261006` revision 7. The admitted incoming blocker
  identifies legacy string-only keys for HTML aliases/fragments and reciprocal
  exclusion. Cycle-6 edits in exactly nine paths are focused validated on
  subject revision 4; prior subject-revision-2 evidence remains history. The
  parent consumed cycle 6 once from verified owned changes. Ledger revision 7
  retains six reviews and zero full reviews; independent M2 re-review remains
  required before acceptance. All nine historical Hygiene records remain unchanged.
- Initial cycle-6 representative `p751-01c2-incoming-cycle6-focused-20261006`
  compiled on subject revision 3 and passed 9/10, with the shared lock released.
  `TEST-P751-01C2-CANONICAL-INCOMING-COUNT-001` failed only C2-06's expected-2
  versus actual-1 incoming count. Parent-confirmed unchanged canonical href
  grouping and final recollection combine source/HTML aliases. One exact count
  correction to 1 retained the exact source identity and Related Articles
  size-2/href/title assertions; all C2-09/10 counts remained.
- Parent-accepted typed closure `cycle6-focused-closure-p751-01c2-20261006`
  revision 1 records `p751-01c2-incoming-cycle6-combined-20261006` on
  `p751-01c2-validation-subject-20261006` revision 4: 205/205 tests in ten suites,
  zero failures/aborts, `sbt_exit=0`, `wrapper_exit=0`, `lock=released`. One actual
  command covered the ten C2 representatives and unchanged nine-suite 195-test
  accumulator. This is focused validation, not full repository validation or
  independent blocker closure. Step6/Phase0/nonconverging0 and consumed-once
  cycle6 remain; independent M2 re-review and acceptance are pending.
- Full Phase review, independent complete SmartDox/Cozy suites and site-native/
  browser closure evidence remain required. No C2/Step/Phase outcome is checked
  off by implementation, and no acceptance commit is claimed.

## Entry

- [ ] Verify predecessor Phase 75 closure and consume its accepted canonical
      declarations, public/YouTube URLs, archive retrieval and gap dispositions.
- [ ] Freeze the exact SmartDox effective-document/content API and the
      participating source roots before changing either producer or consumer;
      this is not a prerequisite for independent existing-artifact preservation.
- [ ] Inventory the separate Cozy candidate diff and freeze the exact admitted
      Kroki-port, CSS-reference, and Arcadia page-preservation files; preserve
      unrelated changes in that source tree.
- [ ] Record the exact SmartDox development coordinate and the Cozy consumer
      baseline used for focused evidence.

- [ ] Freeze participating roots and each native validation boundary; preserve
      unrelated work and all predecessor completed evidence.
- [ ] Re-evaluate the combined Phase duration and 8h ceiling with COZY-75-09;
      the original split estimate predates this added script boundary.
- [ ] Freeze the site-owned placement/Web entry points and affected design/spec
      scope, including the configured warehouse root and local `/media/` mapping.

## SDX-75-01: Effective document identity

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Specify and implement a single effective-content accessor for public
      Dox sources.
- [ ] Prove that `<slug>.dox/index.dox` resolves as logical `<slug>.dox` and
      public `<slug>.html`, while ordinary `<slug>.dox` behavior is unchanged.
- [ ] Keep the accessor as the sole owner of physical-package interpretation.

## SDX-75-02: Fixed public-content closure

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Route LinkCollection, Antora projection, route metadata, and site-link
      resolution through the effective-content accessor.
- [ ] Enumerate only the declared inputs of a `<slug>.dox/` package; do not
      recursively scan descendants for Markdown or Dox parser candidates.
- [ ] Add a regression fixture whose unselected package descendant contains a
      bare `_source` key and prove that public output and links remain correct
      without altering that file.  A `review/` note is one representative case,
      not a special exclusion rule.

## SDX-75-03: Markdown source parsing

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Select parsing only after an explicit package-structure or site-source
      admission, then use the admitted source suffix: `.md` must enter the
      Markdown parser, not the SmartDox/Dox parser.
- [ ] Transform the resulting Markdown AST into the shared document AST
      without reinterpreting SmartDox/Dox-specific syntax from the original
      Markdown bytes.
- [ ] Cover valid participating Markdown, Dox-only syntax embedded in Markdown,
      and the independent non-enumeration of arbitrary package descendants.

## SDX-75-04: Include-resolution compatibility

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Keep the existing include resolver's logical site-relative input
      contract unchanged; resolve a Document Project's effective `index.dox`
      as the physical base before calling it.
- [ ] Add one focused regression fixture with an absolute physical base and a
      relative include.  Prove resolution retains the absolute-base meaning and
      never loses a leading `/` by sending the base through a relative-URI
      normalizer.
- [ ] Characterize an ordinary document's existing include result alongside
      the Document Project fixture.  Do not introduce a global path type
      system, all-input scan, hash/manifest gate, or broad resolver rewrite.

## COZY-75-04: Consumer integration

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Refresh only the admitted SmartDox development dependency after producer
      evidence is available.
- [ ] Add or update Cozy-focused integration evidence for logical package
      identity, canonical public path, and review-note exclusion.
- [ ] Do not add a Cozy route mapper, content scanner, or exception for review
      note syntax.

## COZY-75-05: Kroki adapter

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Coordinate producer launch/readiness/listener/Antora endpoint settings
      and the Docker Antora launch environment in `CozyBokSiteBuild` from
      `SMARTDOX_KROKI_PORT=9609` to target `SMARTDOX_KROKI_PORT=8000`;
      a consumer-only value change is insufficient.
- [ ] Update the focused `CozyBokSpec` launch-contract assertion and prove
      that the obsolete port is absent.
- [ ] Preserve the existing configured toolchain-image propagation and all
      unrelated media-site behavior.

## COZY-75-06: Locale-local CSS assets

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Prove that JA/EN home and nested pages resolve CSS links against their
      locale-local Antora asset root, not the canonical website root.
- [ ] Cover single-locale output as an unchanged compatibility control.
- [ ] Verify the actual generated stylesheet URLs return successfully over
      HTTP and the intended pages display correctly in a browser.

## COZY-75-07: Arcadia page preservation

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Preserve the established Arcadia root and JA/EN home pages instead of
      overwriting locale homes with generated BoK Dashboards.
- [ ] Preserve Arcadia-owned category indexes and verify their intended
      content and presentation.
- [ ] Keep existing generated BoK behavior for non-Arcadia sites and for
      destinations not owned by an Arcadia page.
- [ ] Add focused page-composition regression evidence; browser checks must
      distinguish the intended Arcadia page from a merely styled Dashboard.

## COZY-75-08: Article-card media links

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1
- Update rule: close only on this section's completed checklist evidence.

- [ ] Reproduce the reported missing or incorrect article-card links for the
      article PDF, summary-slide PDF, infographic and YouTube video; identify the cause across
      declared media information, card generation, and public URL resolution.
- [ ] Correct the links for available media without creating broken links
      for media that is not available.
- [ ] Verify actual JA/EN article cards in a browser: all applicable media
      links are visible, use the intended article and locale, and open the
      correct PDF, infographic or YouTube video resource.

## COZY-75-09: Independent local operations and explicit upload

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 75.1 / SimpleModeling.org site integration
- Update rule: close only on this section's completed checklist evidence.

- [ ] Promote the selected operating boundaries into the affected design/spec
      before implementation; retain the original journal as historical context.
- [ ] Provide a separate placement entry point for explicitly selected prepared
      artifacts using existing Phase 75 facilities and configured warehouse
      `media/<ja|en>/<category>/<article>/<file>` paths. Preserve roles, locales,
      editions and gaps; report missing artifacts without rendering/substitution.
- [ ] Prove placement performs no Web build, new media render or remote upload;
      retain video-master backup and declared YouTube-link policy.
- [ ] Make `runweb-production.sh` Web-build-only while retaining normal
      Cozy + Arcadia + Antora composition, existing Arcadia page ownership,
      locale routing, navigation and ordinary CSS/JS/site assets in `website.d`.
- [ ] Prove Web construction only reads/projects existing media declarations
      and references: no call to placement, rendering/deployment, warehouse
      payload copying into `website.d`, or remote upload.
- [ ] Prove repeated Web construction runs independently of placement and leaves
      warehouse media unchanged; preserve predecessor accepted deployment.
- [ ] Configure and verify a separate localhost `/media/` mapping to the warehouse
      for selected JA/EN resource checks; do not claim local success as remote
      availability or introduce a second payload store in `website.d`.
- [ ] Document operator inspection points after placement and after Web build,
      and upload as a separately authorized explicit operation. Record distinct
      placement, reference-registration, Web-build and retrieval evidence;
      no upload is required merely to close this local separation item.
- [ ] Keep deployed payloads outside GitHub; preserve source/master ownership
      and use the configured warehouse root, not the CAR `repository/` subtree.

## Closure evidence

- [ ] Record focused SmartDox producer evidence for the effective-content and
      public-content closure and include-compatibility contract.
- [ ] Record focused Cozy consumer and Kroki-launch evidence against the exact
      accepted dependency coordinate.
- [ ] Record locale-local CSS and Arcadia page-preservation regression
      evidence, including the actual JA/EN home and category browser results.
- [ ] Record article-card PDF, summary-slide PDF, infographic and YouTube link evidence,
      including visible links and successful destination checks.
- [ ] Keep a broader local site-build result distinct from the selected
      artifact publication results; do not infer deployment from build success.
- [ ] Record site-native script-separation evidence, independent repeated Web
      build and local `/media/` browser retrieval; prove absence of implicit
      rendering, placement and upload without performing a remote upload.
- [ ] Complete the required review, validation, and release workflow only
      after an explicit Phase-start request.
- [ ] Pass complete Cozy SBT validation and the admitted changed SmartDox
      producer's complete suite; run site-native structural/build/browser checks.
      Record distinct receipts and coverage, with no aggregate deferral.
- [ ] Close this child independently without rewriting predecessor validation,
      resuming 71.4, starting a later numeric phase or republishing accepted media.
