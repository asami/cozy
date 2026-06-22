# BoK Dashboard Audience Actors

status=active
published_at=2026-06-22

## Overview

This document defines the audience actors used by Cozy BoK dashboards and
analyzes dashboard content from each actor's point of view.

A BoK dashboard is not only an operational console. It is also the entry point
for people who read knowledge, contribute knowledge, manage the knowledge
project, and operate the published site. The dashboard is a single shared screen
for all actors, so it must compress actor-specific needs into one information
hierarchy instead of splitting them into separate pages or tabs.

SmartDox remains the source of truth for parsed BoK metadata, glossary metadata,
RDF metadata, history, and dashboard aggregates. Cozy consumes those metadata
records and renders actor-oriented views.

## Audience Actor Specification

The canonical actor set is:

| Actor | Canonical key | Japanese label | Primary intent |
|-------|---------------|----------------|----------------|
| User | `reader` | 利用者 | Find and understand knowledge. |
| Knowledge Contributor | `contributor` | 知識提供者 | Add, improve, and connect content. |
| BoK Manager | `project_manager` | BoK管理者 | Track BoK growth, coverage, and work status. |
| Site Administrator | `site_administrator` | サイト管理者 | Keep build, publication, and upload operation healthy. |

Rules:

- `actor` is an audience role, not an authentication role.
- One person may have multiple actors.
- The Home Dashboard and Category Dashboard are single dashboards shared by all
  actors.
- Dashboard information priority is:
  `User > Knowledge Contributor > BoK Manager > Site Administrator`.
- Higher-priority actor needs must be visible without scrolling when practical.
- Lower-priority actor needs should appear as compact readiness, diagnostic, or
  operation cards, not as dominant page structure.
- Dashboard content should be available from metadata without requiring Cozy to
  re-parse `.dox` source files.
- The dashboard can carry semantic actor metadata for future filtering, but v1
  does not render separate actor-specific dashboards.
- Actor-specific dashboard panels must degrade gracefully when metadata is
  missing.
- Actor labels are UI text and must be resource-managed when rendered by Cozy.

## Common Dashboard Information Model

Dashboard panels should draw from the following metadata groups:

| Metadata group | Source | Used for |
|----------------|--------|----------|
| Purpose | `site.conf`, `category.yaml` | Vision, goals, subgoals, expected direction. |
| Inventory | SmartDox dashboard metadata | Category, article, term, RDF counts. |
| Growth | SmartDox history/dashboard metadata | Day/week/month cumulative growth. |
| Glossary terms | `metadata/glossary/terms.json` | Term hub, isolated terms, related knowledge. |
| RDF graph | `metadata/rdf/graph.json`, `site.ttl` | Knowledge graph, term/category neighborhoods. |
| Publication registry | `src/main/publication` metadata | Video/publication artifacts and provenance. |
| Build diagnostics | Cozy/SmartDox operation results | Missing metadata, broken source layout, publish readiness. |
| Workflow config | `conf/cozy/config.*`, `.cozy/config.*` | Stage/upload readiness and operational commands. |

## Actor View Analysis

### User (`reader`)

Question the dashboard must answer:

- What knowledge exists here?
- Which terms are important?
- Where should I start?
- How are terms, articles, RDF, and videos connected?

Primary dashboard content:

- Search / navigation entry points.
- Category cards with article, term, and RDF counts.
- Term dashboard and recent terms.
- Term Hub links from category and glossary panels.
- RDF graph link focused on selected category or term.
- Recent additions that are useful for reading, not operation.
- Narrative overview from `index.dox` below the dashboard command surface.

Low priority for this actor:

- Build status internals.
- Upload workflow configuration.
- Detailed publication diagnostics.

Design implication:

- User-facing panels should be visually prominent and low-noise.
- Term cards and category cards should be clickable.
- RDF graph navigation should explain meaning, not just expose files.

### Knowledge Contributor (`contributor`)

Question the dashboard must answer:

- What should I write or improve next?
- Which terms are isolated or weakly connected?
- Which category lacks articles or terms?
- Which RDF/video/article links are missing?

Primary dashboard content:

- Quality alerts: isolated terms, unreferenced terms, weakly connected terms.
- Category coverage matrix: articles, terms, RDF triples, freshness.
- Recent changes by category.
- Term Hub quality panel.
- Missing summary/description metadata indicators.
- Suggested next actions: add article, add term, connect RDF, add video metadata.
- Links to source paths when available.

Low priority for this actor:

- Upload commands.
- Site deployment state unless it blocks review.

Design implication:

- Knowledge Contributor panels should be actionable.
- Each diagnostic should point to the relevant category, term, article, or source
  path.
- The dashboard should distinguish missing content from missing operational
  configuration.

### BoK Manager (`project_manager`)

Question the dashboard must answer:

- Is the BoK growing in the intended direction?
- Are categories balanced?
- Which goals/subgoals are under-covered?
- What is ready for publication or review?

Primary dashboard content:

- Vision / goals / subgoals with coverage indicators.
- Growth chart: cumulative article, term, RDF increments.
- Category portfolio / category matrix.
- Readiness card: strategy, last build, publication readiness, issue count.
- Trend and freshness indicators by category.
- Publication/video registry summary.
- Work queue summary from diagnostics.

Low priority for this actor:

- Low-level Antora/Docker command details.
- Raw RDF triples unless linked through a term/category decision.

Design implication:

- BoK Manager view should compress state into balanced portfolio cards.
- BoK Manager view should include all normal dashboard cards because BoK
  management spans reader navigation, contribution quality, portfolio balance,
  and operation readiness.
- The dashboard should show whether the BoK is coherent, not only whether files
  exist.
- Goal/subgoal coverage should be represented as a management signal when
  metadata supports it.

### Site Administrator (`site_administrator`)

Question the dashboard must answer:

- Can I build, preview, stage, publish, and upload safely?
- What generated outputs exist?
- What configuration or credential boundary is missing?
- Are repository/publication/RDF artifacts consistent?

Primary dashboard content:

- Build status and last command result.
- Preview URL and configured preview port.
- Stage/upload workflow readiness.
- Publication registry path and warehouse/repository path.
- Missing RDF artifact policy by strategy.
- Generated output health: `website.d`, `doxsite.d`, `antora.d`.
- Upload script/config diagnostics.
- Operation manifest links.

Low priority for this actor:

- User-oriented narrative overview.
- Detailed term definitions, except as validation of generated pages.

Design implication:

- Site Administrator panels should be grouped as an operation console.
- Missing upload config must not look like content quality failure.
- Sensitive `.cozy/*` values should never be displayed directly.

## Dashboard Content Priority Matrix

| Dashboard element | User | Knowledge Contributor | BoK Manager | Site Administrator |
|-------------------|--------|-------------|-----------------|--------------------|
| Vision / goals / subgoals | Medium | Medium | High | Low |
| Category cards | High | High | High | Low |
| Article count | Medium | High | High | Low |
| Term count | High | High | High | Low |
| RDF count / graph link | Medium | High | Medium | Medium |
| Term Hub | High | High | Medium | Low |
| Quality alerts | Low | High | High | Medium |
| Growth chart | Low | Medium | High | Low |
| Recent activity | High | Medium | Medium | Low |
| Publication/video artifacts | Medium | Medium | Medium | High |
| Build/preview/stage/upload | Low | Low | Medium | High |
| Operation manifest | Low | Low | Medium | High |

## Single Dashboard Layout Principles

The dashboard must serve all actors in one screen. The layout therefore uses
progressive disclosure:

1. BoK/category Vision is placed first when it is defined, because it frames
   the whole knowledge space for every actor.
2. User entry points are placed immediately after Vision and made prominent.
3. Knowledge Contributor improvement signals are placed next to the User entry
   points so content problems are visible while browsing.
4. BoK Manager portfolio and trend signals summarize direction and balance.
5. Site Administrator operation signals are compact and placed after knowledge
   and quality information.

This avoids turning the public dashboard into an operation console while still
making operational readiness discoverable.

### Home Dashboard Screen Order

The Home Dashboard should use this vertical and visual priority:

| Order | Region | Primary actors | Visual weight | Purpose |
|-------|--------|----------------|---------------|---------|
| 1 | Recent Changes | User, Knowledge Contributor, BoK Manager | Notification | Surface recent additions and updates before the stable dashboard context. |
| 2 | BoK Vision | User, Knowledge Contributor, BoK Manager | Largest | Show the BoK vision, goals, and subgoals when defined. |
| 3 | Category / Term / RDF Navigation | User, Knowledge Contributor | Large | Move to categories, term hubs, and RDF graph. |
| 4 | Knowledge Map Cards | User, Knowledge Contributor | Large | Show categories with article, term, and RDF counts. |
| 5 | Quality And Connectivity | Knowledge Contributor, BoK Manager | Medium | Expose isolated terms, weak links, missing metadata, and stale areas. |
| 6 | Portfolio / Growth | BoK Manager, Knowledge Contributor | Medium | Show balance and growth without replacing User navigation. |
| 7 | Operation Readiness | Site Administrator, BoK Manager | Compact | Show build, preview, publication, and upload readiness. |
| 8 | Narrative Overview | User | Supporting | Render `index.dox` narrative below dashboard cards. |

Home dashboard implications:

- Recent Changes is notification-like. It appears above Vision so
  returning Users can check what changed before reading the stable BoK frame.
- The first stable context should show the BoK Vision when it exists; if it is
  not defined, the card is omitted rather than replaced by a placeholder.
- User navigation should immediately follow the notification/Vision area and answer User
  questions before operational questions.
- Category cards should be direct links to category dashboards.
- Term and RDF graph navigation should be visible near the top because the BoK
  is term-centered.
- Quality alerts should be close enough to User navigation to show knowledge
  health, but they should not dominate the page.
- Operation readiness should use compact cards and status badges.
- Narrative content is useful, but it should support the dashboard rather than
  replace metadata-driven navigation.

### Category Dashboard Screen Order

Category Dashboard should keep the same actor priority but scope all information
to one category:

| Order | Region | Primary actors | Visual weight | Purpose |
|-------|--------|----------------|---------------|---------|
| 1 | Category Vision | User, Knowledge Contributor, BoK Manager | Largest | Show category vision, goals, and subgoals when defined. |
| 2 | Term Hub / Article Map | User, Knowledge Contributor | Large | Navigate terms, articles, and related knowledge. |
| 3 | Category RDF Entry | User, Knowledge Contributor | Large | Link to `rdf/index.html?category=<slug>` and category graph/triples. |
| 4 | Local Quality Alerts | Knowledge Contributor | Medium | Show isolated terms, weak links, missing metadata, and freshness. |
| 5 | Category Growth | BoK Manager | Medium | Show category balance and trend signals. |
| 6 | Category Operation Signals | Site Administrator | Compact | Show publication/video/RDF readiness scoped to the category. |
| 7 | Category Narrative | User | Supporting | Render category `index.dox` narrative below the dashboard. |

Category dashboard implications:

- Category term navigation is more important than raw article lists.
- RDF links should be term/category oriented, not file oriented.
- Category vision/goals/subgoals are shown first only when defined; otherwise
  term and article navigation become the first cards.
- Operation information should stay compact unless the category has blocking
  diagnostics.

### Card Sizing Guidance

Use Bootstrap grid sizing to encode priority:

| Priority | Card size | Typical cards |
|----------|-----------|---------------|
| User primary | `col-12 col-xl-8` or `col-12 col-xl-7` | Hero, category cards, term map, RDF entry. |
| Knowledge Contributor primary | `col-12 col-xl-5` or `col-12 col-xl-4` | Quality alerts, weak connectivity, next improvements. |
| BoK Manager | `col-12 col-md-6 col-xl-4` | Purpose, growth, portfolio, readiness summary. |
| Site Administrator | `col-12 col-md-6 col-xl-3` | Build, preview, stage/upload, manifest. |
| KPI | `col-6 col-md-3` | Articles, terms, RDF, categories/issues. |

Cards should expose the actor priority through content order and size, not
through separate actor tabs.

### Implemented Card Mapping

The current Cozy renderer uses this Home card order:

| Order | Card | Actor metadata |
|-------|------|----------------|
| 1 | Recent Changes notification with History link | `reader contributor project_manager` |
| 2 | BoK Vision, when defined | `reader contributor project_manager` |
| 3 | Category Matrix | `reader contributor project_manager` |
| 4 | KPI cards; Terms KPI links to Glossary / Term Hub entry | `reader contributor project_manager` |
| 5 | RDF KPI link to Graph / Triples view | `reader contributor project_manager` |
| 6 | Quality Alerts | `contributor project_manager` |
| 7 | Growth | `project_manager contributor` |
| 8 | Readiness | `site_administrator project_manager` |
| 9 | Next Actions | `site_administrator project_manager` |

The current Cozy renderer uses this Category card order:

| Order | Card | Actor metadata |
|-------|------|----------------|
| 1 | Category Vision, when defined | `reader contributor project_manager` |
| 2 | Term Map | `reader contributor project_manager` |
| 3 | Article Map | `reader contributor project_manager` |
| 4 | RDF KPI link to category-filtered Graph / Triples view | `reader contributor project_manager` |
| 5 | KPI cards | `reader contributor project_manager` |
| 6 | Local Quality Alerts | `contributor project_manager` |
| 7 | Category Growth | `project_manager contributor` |
| 8 | Category Readiness | `site_administrator project_manager` |
| 9 | Recent Changes | `reader contributor project_manager` |
| 10 | Related Knowledge | `reader contributor project_manager` |

The `data-bok-actors` attributes are a rendering contract for display
filtering and future personalization. They do not imply authentication or
per-actor pages in v1.

The rendered dashboard provides a display-only actor filter over the same card
set:

- `all`
- `reader`
- `contributor`
- `project_manager`
- `site_administrator`

`reader` (User) is the default view. The filter may also be initialized by
`?actor=<key>`, including `all`, but this is only a local presentation state.
It must not be used for authorization, publication policy, or content ownership
decisions.

## Screen Design Rationale By Actor

The dashboard layout is derived from actor priority, not from implementation
convenience. Each visible region must justify its position by the highest
priority actor it serves.

### Home Dashboard Rationale

| Screen region | Actor mapping | Why it appears there |
|---------------|---------------|----------------------|
| BoK Vision | User, Knowledge Contributor, BoK Manager | Vision/goals/subgoals define why the BoK exists and what knowledge it is meant to grow. They are first when defined, and omitted when absent. |
| Category and term entry | User first, Knowledge Contributor second | The BoK is term-centered, so category cards, term entry, and RDF entry are the primary navigation surface. Knowledge Contributors also use the same cards to see where content is thin. |
| Category knowledge map | User, Knowledge Contributor, BoK Manager | User uses it to choose a topic. Knowledge Contributor uses article/term/RDF counts to find gaps. BoK Manager uses category balance as a portfolio signal. |
| RDF graph entry | User, Knowledge Contributor | RDF should be reachable as knowledge navigation, especially from terms and categories. It is not primarily an administrator artifact link. |
| Quality and connectivity | Knowledge Contributor first, BoK Manager second | Content health belongs near navigation because it explains whether knowledge is connected. It is below User navigation because it is an improvement signal, not the primary reading path. |
| Portfolio / growth | BoK Manager first, Knowledge Contributor second | Growth and category balance indicate direction and coverage. They follow User navigation because they are management signals. |
| Recent activity | User, Knowledge Contributor | Users want recent useful additions. Knowledge Contributors want recent change context. The section should avoid operational noise. |
| Operation readiness | Site Administrator first, BoK Manager second | Build, preview, stage, upload, and manifest state matter, but they are lower priority on a shared public dashboard. They should be compact and status-oriented. |
| Narrative overview | User | `index.dox` provides human narrative. It belongs after metadata-driven entry points because the dashboard should first expose current BoK structure and state. |

User use-case mapping:

- Start learning from an unfamiliar BoK: use Vision when defined, then the
  category matrix.
- Look up a known term: use the Terms KPI link or Glossary dashboard, then open
  the Term Hub.
- Follow related concepts from a term: use Term Hub links and
  `rdf/index.html?term=<term-id>`.
- Move between articles and terms: use Category Dashboard Term Map and Article
  Map.
- Check recent changes: use Recent Changes and the
  History link.
- Use RDF when prose is not enough: use the RDF KPI or category RDF link to open
  Graph / Triples view.
- Use video/publication artifacts: use publication/video links exposed from Term
  Hub or related knowledge panels when metadata exists.

### Category Dashboard Rationale

| Screen region | Actor mapping | Why it appears there |
|---------------|---------------|----------------------|
| Category Vision | User, Knowledge Contributor, BoK Manager | Category vision/goals/subgoals define scope when available. If absent, the dashboard starts from term/article navigation. |
| Term map | User first, Knowledge Contributor second | Terms are the category's primary knowledge units. Knowledge Contributor uses the same term map to identify missing definitions or weak relationships. |
| Article map | User first, Knowledge Contributor second | Articles are supporting explanations around terms. They should be visible but not dominate term navigation. |
| Category RDF entry | User, Knowledge Contributor | RDF navigation should start from the category context and allow moving to term-focused graph/triple views. |
| Local quality alerts | Knowledge Contributor first | Category-specific problems should be actionable: isolated terms, missing metadata, stale content, weak RDF links. |
| Category growth | BoK Manager first | Category trend and balance explain coverage after the navigation-oriented cards. |
| Category operation signals | Site Administrator | Publication/video/RDF readiness for the category should be visible only as compact diagnostics. |
| Category narrative | User | Category `index.dox` remains useful prose, but it should support metadata navigation rather than replace it. |

### Conflict Resolution Rules

When two actors need the same screen area, use these rules:

- User navigation wins over management reporting.
- Knowledge Contributor diagnostics may sit next to User navigation only when they help
  explain knowledge quality.
- BoK Manager metrics should summarize, not expand into long reports.
- Site Administrator details should be status badges, links, or compact cards
  unless there is a blocking failure.
- Raw file paths and internal command details should not appear in high-priority
  User regions.
- RDF links should be described as knowledge graph navigation before artifact
  download links.

### Minimum Useful Dashboard

If metadata is incomplete, the dashboard should preserve this minimum order:

1. BoK/category title and purpose summary.
2. Category or term navigation, if purpose metadata is absent.
3. Available article/term/RDF counts.
4. RDF graph or RDF artifact link when available.
5. Diagnostics explaining what metadata is missing.
6. Operation readiness only if operation metadata exists.

This keeps the page useful for Users even when Knowledge Contributor, BoK Manager,
or Site Administrator metadata is still incomplete.

## Recommended Dashboard Sections

The default Home Dashboard should include these sections in the single shared
Dashboard:

1. Vision

- Primary actors: User, Knowledge Contributor, BoK Manager.
- Vision, goals, and subgoals when metadata defines them.
- Omit the card entirely when purpose metadata is absent.

2. Knowledge Navigation

- Primary actors: User, Knowledge Contributor.
- Category cards, term dashboard links, RDF graph links, recent additions.

3. Quality And Connectivity

- Primary actors: Knowledge Contributor, BoK Manager.
- Isolated terms, weak RDF connectivity, missing metadata, stale categories.

4. Operation Readiness

- Primary actors: Site Administrator, BoK Manager.
- Build/preview/stage/upload readiness, publication registry, warehouse paths,
  missing artifact policy, latest operation manifest.

Category Dashboard should follow the same grouping but scoped to one category.
Term Hub should prioritize User and Knowledge Contributor views, with operation data kept
secondary.

## Rendering Contract

- Cozy may render a default mixed dashboard, but panels should carry semantic
  classes or metadata that identify their primary actor group.
- Future UI may expose actor filters or tabs without changing the underlying
  metadata contract.
- `reader` is the default public audience for a published site.
- `site_administrator` content may be omitted or reduced on public-facing sites
  if it exposes operational details.
- Actor-specific content must not require authentication assumptions in v1.

## Deferred Items

- Actor-aware UI filter controls.
- Goal/subgoal coverage scoring.
- Per-actor dashboard personalization.
- Security model mapping between authenticated users and audience actors.
- Publication policy for hiding operational diagnostics from public sites.
