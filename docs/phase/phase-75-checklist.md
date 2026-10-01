# Phase 75 Checklist: Effective SmartDox Content Boundary and Kroki Site-Port Alignment

Phase status: in progress / Step 75.1 independently accepted
Ledger for: [Phase 75](phase-75.md)

Current status: Step 75.1 implementation, focused producer/consumer validation,
and local SmartDox dependency refresh are recorded in workflow evidence.
Independent Step 75.1 review passed. Step 75.2 focused validation passed all
71 tests for Kroki, CSS paths, and Arcadia composition. Its independent review
and local commit are recorded in the Step 75.2 workflow result. Step 75.3
actual site/browser and article-card media verification remain pending.
Owner: Cozy, with SmartDox owning the effective-content and parser contract.
Update rule: check an item only when its required acceptance evidence is
verified; record the accepted Step/release commit as its closure basis.

Closure basis: Step 75.1's local acceptance commit records the checked items,
149 passing parser tests, 44 passing producer integration tests, 9 passing
Cozy consumer tests, and independent protected review with no blockers.
Step 75.2 adds 71 passing focused tests, including exact-byte Arcadia
preservation, partial-output fallback, and disabled-Arcadia controls. The
actual HTTP/browser portions of COZY-75-06/07 remain unchecked for Step 75.3.
Unchecked items and Phase release remain pending. No publication or deployment
has occurred.

## Entry

- [x] Freeze the exact SmartDox effective-document/content API and the
      participating source roots before changing either producer or consumer.
- [x] Inventory the separate Cozy candidate diff and freeze the exact admitted
      Kroki-port, CSS-reference, and Arcadia page-preservation files; preserve
      unrelated changes in that source tree.
- [x] Record the exact SmartDox development coordinate and the Cozy consumer
      baseline used for focused evidence.

## SDX-75-01: Effective document identity

- [x] Specify and implement a single effective-content accessor for public
      Dox sources.
- [x] Prove that `<slug>.dox/index.dox` resolves as logical `<slug>.dox` and
      public `<slug>.html`, while ordinary `<slug>.dox` behavior is unchanged.
- [x] Keep the accessor as the sole owner of physical-package interpretation.

## SDX-75-02: Fixed public-content closure

- [x] Route LinkCollection, Antora projection, route metadata, and site-link
      resolution through the effective-content accessor.
- [x] Enumerate only the declared inputs of a `<slug>.dox/` package; do not
      recursively scan descendants for Markdown or Dox parser candidates.
- [x] Add a regression fixture whose unselected package descendant contains a
      bare `_source` key and prove that public output and links remain correct
      without altering that file.  A `review/` note is one representative case,
      not a special exclusion rule.

## SDX-75-03: Markdown source parsing

- [x] Select parsing only after an explicit package-structure or site-source
      admission, then use the admitted source suffix: `.md`/`.markdown` defaults to
      Pure CommonMark plus GFM tables; explicit Config Enhanced retains the
      existing Dox parser. Content never selects mode.
- [x] For Pure, transform the resulting Markdown AST into the shared document AST
      without reinterpreting SmartDox/Dox-specific syntax from the original
      Markdown bytes.
- [x] Cover valid participating Pure Markdown, literal Dox-only syntax in Pure,
      explicit Enhanced legacy semantics,
      and the independent non-enumeration of arbitrary package descendants.

## SDX-75-04: Include-resolution compatibility

- [x] Keep the existing include resolver's logical site-relative input
      contract unchanged; resolve a Document Project's effective `index.dox`
      as the physical base before calling it.
- [x] Add one focused regression fixture with an absolute physical base and a
      relative include.  Prove resolution retains the absolute-base meaning and
      never loses a leading `/` by sending the base through a relative-URI
      normalizer.
- [x] Characterize an ordinary document's existing include result alongside
      the Document Project fixture.  Do not introduce a global path type
      system, all-input scan, hash/manifest gate, or broad resolver rewrite.

## COZY-75-04: Consumer integration

- [x] Refresh only the admitted SmartDox development dependency after producer
      evidence is available.
- [x] Add or update Cozy-focused integration evidence for logical package
      identity, canonical public path, and review-note exclusion.
- [x] Do not add a Cozy route mapper, content scanner, or exception for review
      note syntax.

## COZY-75-05: Kroki adapter

- [x] Change the Docker Antora launch environment in `CozyBokSiteBuild` from
      `SMARTDOX_KROKI_PORT=9609` to `SMARTDOX_KROKI_PORT=8000`.
- [x] Update the focused `CozyBokSpec` launch-contract assertion and prove
      that the obsolete port is absent.
- [x] Preserve the existing configured toolchain-image propagation and all
      unrelated media-site behavior.

## COZY-75-06: Locale-local CSS assets

- [x] Prove that JA/EN home and nested pages resolve CSS links against their
      locale-local Antora asset root, not the canonical website root.
- [x] Cover single-locale output as an unchanged compatibility control.
- [ ] Verify the actual generated stylesheet URLs return successfully over
      HTTP and the intended pages display correctly in a browser.

## COZY-75-07: Arcadia page preservation

- [x] Preserve the established Arcadia root and JA/EN home pages instead of
      overwriting locale homes with generated BoK Dashboards.
- [x] Preserve Arcadia-owned category indexes with exact-byte regression
      evidence. Actual content/presentation browser verification remains
      pending in Step 75.3.
- [x] Keep existing generated BoK behavior for non-Arcadia sites and for
      destinations not owned by an Arcadia page.
- [ ] Add focused page-composition regression evidence; browser checks must
      distinguish the intended Arcadia page from a merely styled Dashboard.

## COZY-75-08: Article-card media links

- [ ] Reproduce the reported missing or incorrect article-card links for the
      article PDF, summary-slide PDF, and video; identify the cause across
      declared media information, card generation, and public URL resolution.
- [ ] Correct the links for available media without creating broken links
      for media that is not available.
- [ ] Verify actual JA/EN article cards in a browser: all applicable media
      links are visible, use the intended article and locale, and open the
      correct PDF or video resource.

## Closure evidence

- [x] Record focused SmartDox producer evidence for the effective-content and
      public-content closure and include-compatibility contract.
- [x] Record focused Cozy consumer and Kroki-launch evidence against the exact
      accepted dependency coordinate.
- [ ] Record locale-local CSS and Arcadia page-preservation regression
      evidence, including the actual JA/EN home and category browser results.
- [ ] Record article-card PDF, summary-slide PDF, and video link evidence,
      including visible links and successful destination checks.
- [ ] Run any broader local production-site build only as downstream evidence;
      do not claim publication or deploy it.
- [ ] Complete the required review, validation, and release workflow only
      after an explicit Phase-start request.
