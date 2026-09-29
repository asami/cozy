# Phase 75 Checklist: Effective SmartDox Content Boundary and Kroki Site-Port Alignment

Phase status: planned / not started
Ledger for: [Phase 75](phase-75.md)

This is a planning ledger only.  No implementation, validation, review,
commit, dependency refresh, publication, or deployment is recorded here.

## Entry

- [ ] Freeze the exact SmartDox effective-document/content API and the
      participating source roots before changing either producer or consumer.
- [ ] Inventory the separate Cozy candidate diff and freeze the exact admitted
      Kroki-port, CSS-reference, and Arcadia page-preservation files; preserve
      unrelated changes in that source tree.
- [ ] Record the exact SmartDox development coordinate and the Cozy consumer
      baseline used for focused evidence.

## SDX-75-01: Effective document identity

- [ ] Specify and implement a single effective-content accessor for public
      Dox sources.
- [ ] Prove that `<slug>.dox/index.dox` resolves as logical `<slug>.dox` and
      public `<slug>.html`, while ordinary `<slug>.dox` behavior is unchanged.
- [ ] Keep the accessor as the sole owner of physical-package interpretation.

## SDX-75-02: Fixed public-content closure

- [ ] Route LinkCollection, Antora projection, route metadata, and site-link
      resolution through the effective-content accessor.
- [ ] Enumerate only the declared inputs of a `<slug>.dox/` package; do not
      recursively scan descendants for Markdown or Dox parser candidates.
- [ ] Add a regression fixture whose unselected package descendant contains a
      bare `_source` key and prove that public output and links remain correct
      without altering that file.  A `review/` note is one representative case,
      not a special exclusion rule.

## SDX-75-03: Markdown source parsing

- [ ] Select parsing only after an explicit package-structure or site-source
      admission, then use the admitted source suffix: `.md` must enter the
      Markdown parser, not the SmartDox/Dox parser.
- [ ] Transform the resulting Markdown AST into the shared document AST
      without reinterpreting SmartDox/Dox-specific syntax from the original
      Markdown bytes.
- [ ] Cover valid participating Markdown, Dox-only syntax embedded in Markdown,
      and the independent non-enumeration of arbitrary package descendants.

## SDX-75-04: Include-resolution compatibility

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

- [ ] Refresh only the admitted SmartDox development dependency after producer
      evidence is available.
- [ ] Add or update Cozy-focused integration evidence for logical package
      identity, canonical public path, and review-note exclusion.
- [ ] Do not add a Cozy route mapper, content scanner, or exception for review
      note syntax.

## COZY-75-05: Kroki adapter

- [ ] Change the Docker Antora launch environment in `CozyBokSiteBuild` from
      `SMARTDOX_KROKI_PORT=9609` to `SMARTDOX_KROKI_PORT=8000`.
- [ ] Update the focused `CozyBokSpec` launch-contract assertion and prove
      that the obsolete port is absent.
- [ ] Preserve the existing configured toolchain-image propagation and all
      unrelated media-site behavior.

## COZY-75-06: Locale-local CSS assets

- [ ] Prove that JA/EN home and nested pages resolve CSS links against their
      locale-local Antora asset root, not the canonical website root.
- [ ] Cover single-locale output as an unchanged compatibility control.
- [ ] Verify the actual generated stylesheet URLs return successfully over
      HTTP and the intended pages display correctly in a browser.

## COZY-75-07: Arcadia page preservation

- [ ] Preserve the established Arcadia root and JA/EN home pages instead of
      overwriting locale homes with generated BoK Dashboards.
- [ ] Preserve Arcadia-owned category indexes and verify their intended
      content and presentation.
- [ ] Keep existing generated BoK behavior for non-Arcadia sites and for
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

- [ ] Record focused SmartDox producer evidence for the effective-content and
      public-content closure and include-compatibility contract.
- [ ] Record focused Cozy consumer and Kroki-launch evidence against the exact
      accepted dependency coordinate.
- [ ] Record locale-local CSS and Arcadia page-preservation regression
      evidence, including the actual JA/EN home and category browser results.
- [ ] Record article-card PDF, summary-slide PDF, and video link evidence,
      including visible links and successful destination checks.
- [ ] Run any broader local production-site build only as downstream evidence;
      do not claim publication or deploy it.
- [ ] Complete the required review, validation, and release workflow only
      after an explicit Phase-start request.
