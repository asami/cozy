# Phase 13: BoK Tag Knowledge Navigation

Status: active

Start date: 2026-06-28

## Goal

Make tags a first-class BoK navigation and analysis surface.

Phase 13 treats tags as lightweight cross-cutting labels over BoK knowledge.
Tags should connect articles, terms, scenarios, projects, bibliography entries,
history, and RDF resources without replacing glossary terms or categories.

## Scope

In scope:

- tag metadata contract for BoK source and generated metadata
- hierarchical tag keys such as `technology.review` and `workflow.review`
- category-scoped short tag normalization to `category.tag`
- tag collection from SmartDox document metadata and Cozy-owned metadata
- `tags/index.html` dashboard
- hierarchical tag hub pages such as `tags/<namespace>/index.html` and
  `tags/<namespace>/<leaf>.html`
- Home and Category Dashboard tag summaries
- Term Hub, Scenario, Project, Bibliography, and Article tag marks / tag chips
- RDF Information View tag filtering and tag-related navigation
- executable specs for tag metadata consumption and generated pages

Out of scope:

- using tags as glossary term replacements
- using tags as category hierarchy
- automatic tag generation from body text
- tag governance workflow, approval, or moderation
- external tag recommendation services

## Phase Items

- [x] BK13-01: Phase 13 documentation opened
- [x] BK13-02: Tag model and responsibility boundary
- [x] BK13-03: SmartDox tag metadata handoff
- [ ] BK13-04: Cozy tag index reader
- [ ] BK13-05: Tag dashboard and tag hub rendering
- [ ] BK13-06: Dashboard and knowledge-page tag links
- [ ] BK13-07: RDF tag navigation
- [ ] BK13-08: KnowledgeHub operational verification
- [ ] BK13-09: Tests and executable specs
- [ ] BK13-10: Phase closure

## Acceptance Criteria

- BoK source can express tags without conflating them with glossary terms or
  categories.
- SmartDox-generated metadata exposes tags for source documents it owns.
- Cozy-generated metadata and publication metadata can participate in the same
  tag index.
- Tags can be hierarchical with `.` separated canonical keys.
- Category-scoped short tags are normalized to `category.tag`.
- `website.d/tags/index.html` lists tags with counts and linked knowledge items.
- `website.d/tags/<namespace>/index.html` and
  `website.d/tags/<namespace>/<leaf>.html` show related articles, terms,
  scenarios, projects, bibliography entries, and RDF links where metadata
  exists.
- Article, Scenario, Term Hub, Project, and Bibliography pages show tag marks
  or tag chips when tag metadata exists.
- Home and Category Dashboards expose tags as a compact navigation aid.
- Existing Term Hub, Scenario, Project, Bibliography, RDF, and Dashboard pages
  keep their current behavior when no tag metadata exists.
- Tag rendering is deterministic and covered by executable specs.

## Progress Notes

- 2026-06-28: Opened Phase 13 for BoK tag knowledge navigation after Phase 12
  closure. The initial direction is to keep tags lightweight and
  cross-cutting: categories organize source areas, glossary terms define
  domain vocabulary, and tags provide flexible navigation across BoK knowledge
  types.
- 2026-06-28: Added the first Cozy tag metadata consumer slice. Cozy now reads
  explicit `tags` fields from SmartDox document fragments, glossary term
  metadata, scenario metadata, and bibliography metadata when present. It
  renders `tags/index.html`, `tags/<tag>.html`, Home Dashboard tag KPI, and
  Category Dashboard tag KPI. The implementation intentionally does not derive
  tags from glossary terms or categories.
- 2026-06-28: Refined the Phase 13 output specification for hierarchical tags.
  Canonical tag keys use `.` separators, public tag pages use hierarchical
  URLs, category-scoped short tags are normalized to `category.tag`, and
  explicit dotted tags can use category or functional namespaces.
- 2026-06-28: Implemented the Cozy hierarchical tag consumer slice. Cozy now
  prefers SmartDox `metadata/tags/tags.json`, falls back to usage-derived tags
  when the handoff is absent, generates namespace and leaf tag pages, copies
  tag metadata to `website.d`, and renders tag chips on Cozy-owned Scenario,
  Term Hub, and Bibliography surfaces.
- 2026-06-28: Implemented the SmartDox tag metadata handoff slice in the
  sibling SmartDox worktree. SmartDox document fragments now preserve source
  tags and emit `metadata/tags/tags.json` with usage-derived tags, optional
  `tags/**` definitions, hierarchical keys, and resource references.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-13-checklist.md`
- `docs/phase/phase-12.md`
