# Phase 80 Checklist: Site Tag Navigation and Related Content Index

status=planned
phase=[Phase 80](phase-80.md)

## P80-01: Current behavior and canonical route

- [ ] Reproduce the user-visible flow from a tagged content page to its tag destination.
- [ ] Confirm all supported tag-chip producers resolve through the canonical Phase 13 tag key/path.
- [ ] Record representative undefined-definition and authored-definition fixtures.

## P80-02: Related-content primary projection

- [ ] Make TagEntry.refs related resources the primary tag-detail content.
- [ ] Preserve deterministic grouping/ordering by supported resource kind.
- [ ] Collapse duplicate resource references by canonical identity.
- [ ] Keep every result linked to its actual site resource.

## P80-03: Definition fallback correction

- [ ] Render explicit non-empty tag definition summary/body as supplemental context.
- [ ] Do not present generated generic description text as tag knowledge.
- [ ] Omit empty/generic Purpose presentation when no meaningful definition exists.
- [ ] Confirm a definition-free tag page remains fully useful through related content.

## P80-04: Hierarchy, locale, and RDF compatibility

- [ ] Preserve tag overview, namespace pages, canonical leaf paths, and breadcrumbs.
- [ ] Preserve category-scoped and explicit dotted tag normalization.
- [ ] Preserve locale isolation.
- [ ] Preserve RDF filtered-view navigation as an additional view.

## P80-05: Site-wide navigation verification

- [ ] Verify tag click -> canonical tag page from an article/document.
- [ ] Verify from Term Hub.
- [ ] Verify from Scenario.
- [ ] Verify from Project.
- [ ] Verify from Bibliography.
- [ ] Verify repository CAR and SIE/RDF routes when present in the representative fixture.
- [ ] Confirm the same canonical tag exposes the same effective related-resource set.

## P80-06: Executable specifications and closure

- [ ] Add/extend focused CozyBokTagSpec coverage for definition-free navigation.
- [ ] Cover authored-definition plus related-content behavior.
- [ ] Cover duplicate handoff/usage references.
- [ ] Cover multiple resource kinds.
- [ ] Cover hierarchy, locale, RDF link, and untagged regression behavior.
- [ ] Perform representative real-site/fixture click-through verification.
- [ ] Run focused tag specs.
- [ ] Run normal Cozy validation.
- [ ] Run git diff --check.
- [ ] Record independent Phase review and close only with zero blocking findings.
