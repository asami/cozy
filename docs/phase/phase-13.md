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
- Component Repository CAR knowledge pages that connect Project knowledge to
  published CAR catalog entries, versions, descriptors, ABI manifests, CML
  sidecars, and model metadata
- executable specs for tag metadata consumption and generated pages

Out of scope:

- using tags as glossary term replacements
- using tags as category hierarchy
- automatic tag generation from body text
- tag governance workflow, approval, or moderation
- external tag recommendation services
- building or publishing CAR artifacts as part of BoK build
- scanning repository artifact directories as source instead of using repository
  catalog metadata

## Phase Items

- [x] BK13-01: Phase 13 documentation opened
- [x] BK13-02: Tag model and responsibility boundary
- [x] BK13-03: SmartDox tag metadata handoff
- [x] BK13-04: Cozy tag index reader
- [x] BK13-05: Tag dashboard and tag hub rendering
- [x] BK13-06: Dashboard and knowledge-page tag links
- [ ] BK13-07: RDF tag navigation
- [ ] BK13-08: KnowledgeHub operational verification
- [x] BK13-09: Tests and executable specs
- [ ] BK13-11: Component Repository CAR knowledge
- [ ] BK13-12: Phase closure

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
- Project pages expose related published CAR artifacts when repository catalog
  metadata exists.
- Repository CAR pages expose CAR versions, catalog paths, descriptors, ABI
  manifests, CML sidecars, model metadata, related project links, terms, tags,
  and diagnostics without building or publishing CAR artifacts.

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
- 2026-07-13: Added BK13-11 to cover Component Repository CAR knowledge before
  the SIE-specific Phase 14 catalog integration. The generic contract is that
  Project knowledge represents the program-development / CAR-provider unit,
  while repository CAR knowledge represents published CAR artifact versions
  from `repository/catalog/car`. Cozy should connect the two through metadata
  and pages without building, publishing, or ad hoc scanning CAR artifacts.
- 2026-07-13: Implemented the first BK13-11 repository CAR knowledge slices.
  Cozy now materializes repository CAR index/module metadata, renders CAR
  index/module/version pages, links Project and published CAR knowledge in both
  directions, and includes Project-derived tag navigation for related CARs.
  Warehouse catalog discovery, sidecar detail, Term/RDF hooks, and KnowledgeHub
  operational verification remain open.
- 2026-07-13: Added deterministic Project/CAR connection diagnostics to the
  repository CAR metadata and maintainer dashboard. Catalog entries without a
  Project and Project definitions without a published catalog are reported
  separately; connected Project/CAR pairs remain outside the diagnostic set.
- 2026-07-13: Fixed the warehouse CAR catalog route with a dedicated executable
  specification. `--warehouse <dir>` resolves the catalog source to
  `<warehouse>/repository/catalog/car`, and the generated metadata and CAR
  knowledge pages preserve the warehouse-relative catalog provenance.
- 2026-07-13: Added public CML and model metadata sidecar handoff for repository
  CAR knowledge. Cozy resolves only the fixed files produced by `publish-car`,
  copies them under `website.d/repository/catalog/car`, and links them from CAR
  module/version pages. Model metadata YAML is excluded from catalog parsing.
- 2026-07-13: Added CAR archive metadata handoff for repository CAR knowledge.
  Cozy reads top-level `component-descriptor.json` and `abi-manifest.json` from
  catalog-selected CAR artifacts, preserves their JSON in module metadata, and
  shows concise descriptor and ABI summaries on CAR module/version pages.
- 2026-07-13: Added maintainer diagnostics for incomplete or inconsistent CAR
  archives. Existing CAR files report missing descriptor/ABI entries and
  catalog-coordinate mismatches, while catalog versions whose artifact is not
  locally available remain outside archive-internal diagnostics.
- 2026-07-13: Added repository CAR tags and terms. Catalog-level metadata is
  authoritative and remains first, related Project metadata supplements it
  with duplicate removal, and effective CAR tags link bidirectionally with
  hierarchical tag resource pages.
- 2026-07-13: Connected repository CAR terms to SmartDox glossary metadata.
  CAR module and version pages link resolved terms to their Term Hub and RDF
  filtered view, while each Term Hub lists related repository CAR modules.
  Unresolved CAR terms remain readable plain text rather than creating derived
  glossary entries in Cozy.
- 2026-07-13: Added dedicated hierarchical tag executable coverage for
  repository CAR resources. SmartDox tag definitions and Cozy-owned CAR tags
  merge on the canonical tag page, which groups the CAR resource and links to
  its repository module page.
- 2026-07-13: Added RDF tag navigation handoff. SmartDox graph metadata now
  carries canonical tags on source-backed RDF nodes and propagates them to
  edges; Cozy tag pages link to `rdf/index.html?tag=<canonical-key>` and the RDF
  Information View combines tag filtering with its existing category and term
  filters. Modeling tags as RDF resources or predicates remains open.
- 2026-07-13: Reconciled the Project tag checklist with the implemented
  metadata consumer and executable specification. Project metadata contributes
  canonical tag references, Project pages render tag links, and tag resource
  pages group and link related Projects.
- 2026-07-13: Reconciled the tag reader, tag page, and executable-specification
  items with the completed implementation. Article pages render tag chips, tag
  pages link to RDF filtered views, and BK13-04, BK13-05, and BK13-09 now match
  their completed checklists. Empty-tag presentation and tag RDF resource
  modeling remain open in BK13-06 and BK13-07.
- 2026-07-13: Completed BK13-06 by fixing the empty-tag presentation contract
  in `CozyBokTagSpec`. Untagged articles remain unchanged, no empty tag chip
  container or leaf page is generated, and the tag dashboard uses its explicit
  empty state.
- 2026-07-13: Verified the current development runtime against KnowledgeHub in
  preview and offline-bibliography mode. Category-scoped `embedding` and `rdf`
  become `technology.embedding` and `technology.rdf`, explicit
  `knowledge.search` retains its namespace, Home and Category dashboards link
  to the tag tree, and article and SmartDox-owned term pages link to existing
  hierarchical tag pages. The operational check exposed and fixed the missing
  tag-chip post-processing for SmartDox-owned term pages.
- 2026-07-13: KnowledgeHub Project/CML knowledge renders from the external
  `nict-knowledgehub` source, but repository CAR metadata reports
  `project-without-catalog`. BK13-11 operational closure therefore still needs
  one published repository CAR catalog entry; Cozy does not synthesize one
  during `bok build`.
- 2026-07-13: Added representative Project, Scenario, and curated Bibliography
  tags to KnowledgeHub. SmartDox now preserves curated bibliography tags in
  `bibliography.json` and identifies their tag references as Bibliography
  resources at the canonical detail-page path. Cozy merges producer and
  effective-metadata references by resource identity, so the tag page lists
  Design Patterns once. Project, Scenario, and Bibliography detail-page links
  passed the operational check. The published Scenario detail page carries its
  `workflow.review` tag chip, and SmartDox selects the first component that
  actually has a root index as the Antora site start page instead of selecting
  an index-less Bibliography component.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-13-checklist.md`
- `docs/phase/phase-12.md`
- `docs/journal/2026/07/bok-component-repository-car-knowledge-plan-2026-07-13.md`
