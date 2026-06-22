# BoK Use Cases

status=active
published_at=2026-06-22

## Overview

This document defines the stable use cases for Cozy BoK operation.

A BoK is a SmartDox-based knowledge source project operated through Cozy. Cozy
is responsible for source scaffolding, validation, build orchestration,
publication registry updates, local preview, staging, upload workflow
delegation, dashboard rendering, and metadata-driven navigation. SmartDox
remains the source of truth for parsed documents, glossary metadata, RDF
metadata, history, and site-generation semantics.

The use cases below are not implementation tasks. They define what users and
operators need to accomplish so that future BoK features can be evaluated
against stable intent.

## Actors

The canonical audience actors are defined in
`docs/design/bok-dashboard-audience-actors.md`.

| Actor | Key | Primary intent |
|-------|-----|----------------|
| User | `reader` | Find and understand knowledge. |
| Knowledge Contributor | `contributor` | Add, improve, and connect knowledge. |
| BoK Manager | `project_manager` | Manage growth, coverage, coherence, and work status. |
| Site Administrator | `site_administrator` | Keep build, staging, upload, and publication operation healthy. |

The actor keys are presentation and operation terms, not authentication roles.
One person may act as multiple actors.

## System Boundaries

BoK operation uses four stable locations:

| Boundary | Typical path | Owner | Purpose |
|----------|--------------|-------|---------|
| Source | `src/main/doxsite` | BoK project | Human-maintained SmartDox source, categories, glossary, history, manual, RDF seeds, assets. |
| Publication registry | `src/main/publication` | BoK project / Cozy | BoK-facing machine-readable metadata for videos, artifacts, RDF, provenance, and publication entries. |
| Generated site | `website.d`, `doxsite.d`, `antora.d` | Cozy / SmartDox | Reproducible generated outputs, not canonical source. |
| Artifact warehouse | external repository path | Site/project operation | Distributed artifacts such as Maven, CAR/SAR, video, downloads, and sidecars. |

Rules:

- Generated outputs are reproducible and should not be treated as canonical
  source.
- Cozy must not require Docker, VOICEVOX, ffmpeg, Remotion, Playwright, or
  whisper.cpp for metadata-only BoK workflows.
- SmartDox should not scan arbitrary generated/work directories for BoK
  publication semantics.
- Sensitive local configuration belongs in `.cozy/*`; shared project
  configuration belongs in `conf/cozy/*`.
- Upload providers are project-owned external workflows. Cozy delegates to
  configured scripts and injects resolved environment variables; it does not
  embed hosting credentials or cloud-provider policy.

## Use Case Summary

| ID | Use case | Primary actor |
|----|----------|---------------|
| BOK-UC-01 | Create a new BoK source project | BoK Manager |
| BOK-UC-02 | Inspect and repair a BoK project root | Site Administrator |
| BOK-UC-03 | Add or update a category | Knowledge Contributor |
| BOK-UC-04 | Add or update knowledge content | Knowledge Contributor |
| BOK-UC-05 | Navigate knowledge from dashboard and terms | User |
| BOK-UC-06 | Inspect term relationships and RDF graph | User |
| BOK-UC-06A | Start learning from an unfamiliar BoK | User |
| BOK-UC-06B | Look up a known term | User |
| BOK-UC-06C | Follow related concepts from a term | User |
| BOK-UC-06D | Move between articles and term hubs | User |
| BOK-UC-06E | Check recent knowledge additions | User |
| BOK-UC-06F | Use RDF graph and triples when prose is not enough | User |
| BOK-UC-06G | Use video or publication artifacts as supporting knowledge | User |
| BOK-UC-07 | Build the local generated site | Site Administrator |
| BOK-UC-08 | Preview the generated site locally | User / Site Administrator |
| BOK-UC-09 | Check source quality and contribution gaps | Knowledge Contributor |
| BOK-UC-10 | Manage BoK growth and coverage | BoK Manager |
| BOK-UC-11 | Publish video packages into the BoK registry | Knowledge Contributor / Site Administrator |
| BOK-UC-12 | Run publish dry-run safely | Site Administrator |
| BOK-UC-13 | Stage generated site output | Site Administrator |
| BOK-UC-14 | Upload through project-owned workflow | Site Administrator |
| BOK-UC-15 | Recover from failed build or upload | Site Administrator |

## BOK-UC-01: Create A New BoK Source Project

Primary actor: BoK Manager

Goal:

- Create a source tree that can be operated by Cozy and rendered by SmartDox.

Preconditions:

- A target project directory is chosen.
- The BoK name, site URL, and default language are known.

Main flow:

1. Create the BoK source scaffold.
2. Generate `src/main/doxsite`, `conf/cozy/config.yaml`, source guidance, and
   generated-directory ignores.
3. Seed site metadata, glossary, history, manual, RDF, and CSS assets.
4. Run `bok doctor` to confirm the source tree is recognizable.

Success:

- The project has a valid BoK root.
- Generated directories are ignored.
- Shared settings are in `conf/cozy/config.yaml`.
- Local sensitive overrides are not required for basic build.

Design implications:

- Scaffolding must not create generated site output.
- Scaffolding should create source narrative files, not generated dashboard
  fragments.

## BOK-UC-02: Inspect And Repair A BoK Project Root

Primary actor: Site Administrator

Goal:

- Determine whether a directory is a BoK root or can be safely repaired into
  one.

Preconditions:

- The user is in or near a BoK project tree.

Main flow:

1. Inspect current directory or supplied path.
2. Resolve nested paths back to the BoK root.
3. Report root markers and missing markers.
4. Present safe repairs without modifying source content unless explicitly
   requested.

Success:

- The user sees why the directory is recognized as a BoK root.
- Missing generated-directory ignores, stale Docker image defaults, and missing
  basic configuration are reported clearly.

Design implications:

- Diagnostics should say "root markers", not ambiguous terms such as "signals".
- Repair must be non-destructive and source-safe.

## BOK-UC-03: Add Or Update A Category

Primary actor: Knowledge Contributor

Goal:

- Add a knowledge category with source metadata, narrative, seed article, and
  seed glossary term.

Preconditions:

- A BoK source project exists.

Main flow:

1. Create category directory under `src/main/doxsite`.
2. Write `category.yaml` with title, description, and optional purpose metadata.
3. Write category `index.dox` as narrative source.
4. Optionally seed article and glossary term files.

Success:

- The category appears in dashboard navigation after build.
- Category dashboard can show category purpose, term map, article map, RDF
  entry, and quality indicators.

Design implications:

- Category `index.dox` is narrative source, not generated dashboard output.
- Purpose metadata belongs in `category.yaml`.

## BOK-UC-04: Add Or Update Knowledge Content

Primary actor: Knowledge Contributor

Goal:

- Add articles, glossary terms, RDF references, videos, or publication metadata
  that improves the BoK.

Preconditions:

- The relevant category exists.

Main flow:

1. Add or edit SmartDox source files.
2. Add or update glossary terms where the content introduces domain concepts.
3. Add RDF seed data or metadata when relationships should be machine-readable.
4. Build and inspect dashboard/term hub output.

Success:

- Content is visible from category, glossary, term hub, and RDF navigation where
  appropriate.
- Weak or missing relationships are surfaced as quality diagnostics.

Design implications:

- Term metadata and relationship metadata should be produced by SmartDox.
- Cozy should render metadata and diagnostics, not re-parse `.dox` to infer
  term relationships.

## BOK-UC-05: Navigate Knowledge From Dashboard And Terms

Primary actor: User

Goal:

- Understand what knowledge exists and where to start.

Preconditions:

- A generated site exists.

Main flow:

1. Open the Home Dashboard.
2. Review category cards, term counts, article counts, and RDF counts.
3. Move to a category dashboard or glossary dashboard.
4. Open a term hub or article.

Success:

- The user can reach key terms and articles without understanding source
  layout.
- The default dashboard actor view is User.

Design implications:

- User-oriented cards should be first-class dashboard content.
- Narrative `index.dox` content supports the dashboard but should not replace
  metadata-driven navigation.

## BOK-UC-06: Inspect Term Relationships And RDF Graph

Primary actor: User

Goal:

- Explore how terms, articles, RDF resources, and videos connect.

Preconditions:

- SmartDox generated glossary and RDF metadata exists, or fallback metadata is
  available.

Main flow:

1. Open the glossary dashboard.
2. Select a term hub.
3. Follow related articles, related terms, RDF resources, or video links.
4. Open `rdf/index.html` with a term or category filter when graph navigation
   is needed.

Success:

- Terms behave as knowledge hubs.
- RDF graph and triples view can be reached from dashboard, category, and term
  contexts.

Design implications:

- BoK is term-centered.
- RDF should be shown as knowledge navigation, not only as downloadable
  artifacts.

## User Use Case Detail

The User actor is the default dashboard view. The User does not primarily come
to operate the BoK. The User comes to answer a knowledge question, learn a
concept, confirm meaning, or find a path through related concepts.

User-facing BoK design should therefore optimize for:

- entry points that explain where to start;
- term lookup;
- related concept navigation;
- movement between terms, articles, RDF, and videos;
- recent additions that are useful for learning;
- graph/triples views that clarify relationships when prose is insufficient.

The following use cases refine `BOK-UC-05` and `BOK-UC-06`.

## BOK-UC-06A: Start Learning From An Unfamiliar BoK

Primary actor: User

Goal:

- Understand the shape of the BoK and choose a first reading path.

Typical trigger:

- The user arrives from a search result, shared URL, project README, or
  external documentation link.

Main flow:

1. Open the Home Dashboard.
2. Read the BoK Vision when it exists.
3. Review category cards and major term counts.
4. Choose a category, glossary entry point, or recent addition.
5. Open a category dashboard or term hub.

Success:

- The user can answer "what is this BoK about?" and "where should I start?"
  within the first screen.

Required information:

- BoK title and short purpose.
- Category cards.
- Term and article counts.
- RDF graph entry when meaningful.
- Recent additions.

Design implications:

- The default actor view must be User.
- User cards must not be hidden behind operation-focused panels.
- If Vision/goals are missing, the dashboard should start from category and
  term navigation without leaving an empty purpose section.

## BOK-UC-06B: Look Up A Known Term

Primary actor: User

Goal:

- Find the meaning of a term the user already knows or has just encountered.

Typical trigger:

- The user sees a term in an article, conversation, video, or external system.

Main flow:

1. Open the Glossary dashboard or use a term link from an article.
2. Locate the term by title, reading, alias, or category.
3. Open the Term Hub.
4. Read definition, summary, aliases, and category context.

Success:

- The user understands the term enough to continue reading or make a decision.

Required information:

- Term title.
- Reading when available.
- Summary or definition.
- Category.
- Aliases.
- Source article or source path when useful.

Design implications:

- Glossary top should behave as a term dashboard, not only as a static index.
- Term Hub URLs should remain stable.
- Terms without rich metadata should still have a fallback page.

## BOK-UC-06C: Follow Related Concepts From A Term

Primary actor: User

Goal:

- Move from one term to adjacent concepts and understand the relationship.

Typical trigger:

- The current term definition mentions another concept or the user wants to
  understand context.

Main flow:

1. Open a Term Hub.
2. Inspect related terms and RDF resources.
3. Follow a related term link.
4. Optionally open RDF graph filtered by the current term.

Success:

- The user can see why terms are related and continue exploration without
  returning to the Home Dashboard.

Required information:

- Related terms.
- Relationship labels or predicates when available.
- Related articles.
- RDF graph link with `?term=<term-id>`.

Design implications:

- Related terms should be visible on the Term Hub.
- RDF predicates should be human-readable where possible.
- Term graph navigation should preserve term context.

## BOK-UC-06D: Move Between Articles And Term Hubs

Primary actor: User

Goal:

- Use articles for narrative explanation and terms for conceptual navigation.

Typical trigger:

- The user reads an article and needs to clarify a term, or starts at a term and
  needs a longer explanation.

Main flow:

1. Open an article or term hub.
2. Follow term links from article text to Term Hub.
3. Follow article references from Term Hub to explanatory articles.
4. Return to related terms or category dashboard as needed.

Success:

- The user can move between prose and structured knowledge without losing
  context.

Required information:

- Article-to-term links.
- Term-to-article references.
- Category breadcrumbs or equivalent context.

Design implications:

- Articles and Term Hubs should be mutually navigable.
- Term links should not be generated for pages that are intentionally outside
  the glossary link scope, such as Manual pages.

## BOK-UC-06E: Check Recent Changes

Primary actor: User

Goal:

- See what knowledge was recently added or changed.

Typical trigger:

- The user revisits the BoK after some time or wants to discover new material.

Main flow:

1. Open Home Dashboard.
2. Inspect recent activity or History.
3. Open recent articles, terms, or category updates.

Success:

- The user can find new useful knowledge without reading operational logs.

Required information:

- Recent additions by date.
- Links to articles, terms, or categories.
- Human-readable History page for broader context.

Design implications:

- Recent activity should focus on knowledge items, not build or upload events.
- Operational history belongs to Site Administrator views or History pages, not
  the primary User region.

## BOK-UC-06F: Use RDF Graph And Triples When Prose Is Not Enough

Primary actor: User

Goal:

- Inspect structured relationships when narrative explanation does not answer
  the question.

Typical trigger:

- The user needs exact relationships, neighboring resources, or machine-readable
  links behind a term or category.

Main flow:

1. Open RDF page from Home, Category, or Term Hub.
2. Use Graph view to inspect related nodes.
3. Use RDF triples view for precise statements.
4. Return to term or article context.

Success:

- The user can confirm structured relationships without downloading raw RDF
  files manually.

Required information:

- Graph view.
- Triples view.
- Category and term filters.
- Links back to terms and articles.

Design implications:

- RDF page should be a dashboard-style special page, not an Antora article page.
- RDF graph entry points should be obvious from Home, Category, and Term Hub.
- Triple view is a user-facing inspection tool, not only a developer artifact.

## BOK-UC-06G: Use Video Or Publication Artifacts As Supporting Knowledge

Primary actor: User

Goal:

- Use video, downloadable samples, or publication artifacts to understand or
  reproduce knowledge.

Typical trigger:

- The user needs a demonstration, tutorial, sample project, or published
  artifact related to an article or term.

Main flow:

1. Open article, category dashboard, or term hub.
2. Follow video or publication links when available.
3. Watch embedded video or download referenced artifacts.
4. Return to the related term/article context.

Success:

- The user can use supporting media/artifacts without needing to know warehouse
  layout or publication metadata internals.

Required information:

- Video player or artifact link.
- Caption/transcript links when available.
- Artifact title, version, and relationship to term/article/category.

Design implications:

- Publication metadata should be rendered as knowledge support.
- Warehouse paths should not be exposed as the primary navigation model.
- Missing publication metadata should not break the article; it should degrade
  with a diagnostic for operators.

## BOK-UC-07: Build The Local Generated Site

Primary actor: Site Administrator

Goal:

- Generate `website.d`, `doxsite.d`, and `antora.d` from BoK source.

Preconditions:

- BoK source tree is valid.
- Required SmartDox/Antora tooling is available through the configured route.

Main flow:

1. Run `bok build` with an explicit or default strategy.
2. Cozy invokes SmartDox and site generation.
3. Cozy post-processes dashboard, special pages, publication metadata, and
   navigation.

Success:

- Generated site output exists and is reproducible.
- Source files are not modified by build.
- Missing optional metadata produces diagnostics, not fabricated values.

Design implications:

- Build must not run heavy video generation automatically.
- Build should consume publication metadata when present.

## BOK-UC-08: Preview The Generated Site Locally

Primary actor: User / Site Administrator

Goal:

- View generated `website.d` through a local web server.

Preconditions:

- `website.d` exists.

Main flow:

1. Run `bok preview`.
2. Cozy serves `website.d` on the configured or requested port.
3. User opens the local HTTP URL.

Success:

- The generated site is viewed through HTTP rather than `file://`.

Design implications:

- Doctor output should explain preview through a local web server.
- Preview port can be configured in public project config when not sensitive.

## BOK-UC-09: Check Source Quality And Contribution Gaps

Primary actor: Knowledge Contributor

Goal:

- Identify missing, weak, isolated, stale, or poorly connected knowledge.

Preconditions:

- Dashboard metadata, glossary metadata, and RDF metadata are available when
  possible.

Main flow:

1. Open dashboard in Knowledge Contributor view or dim mode.
2. Inspect quality alerts, term quality, category coverage, and RDF links.
3. Follow diagnostics to source paths or term hubs.

Success:

- The contributor has actionable next edits.

Design implications:

- Diagnostics should point to category, term, article, or source path.
- Missing operational config should not be mixed with content quality problems.

## BOK-UC-10: Manage BoK Growth And Coverage

Primary actor: BoK Manager

Goal:

- Confirm that the BoK is growing coherently and in the intended direction.

Preconditions:

- Purpose metadata, dashboard counts, and history metadata exist when available.

Main flow:

1. Review BoK Vision, goals, and subgoals.
2. Review category balance, growth chart, RDF coverage, and quality summary.
3. Decide whether categories, terms, or publication entries need work.

Success:

- The BoK Manager can decide what to prioritize next.

Design implications:

- BoK Manager view should include all normal dashboard cards.
- Management information should be compact and portfolio-oriented.

## BOK-UC-11: Publish Video Packages Into The BoK Registry

Primary actor: Knowledge Contributor / Site Administrator

Goal:

- Register `.video/` packages as publication metadata and warehouse artifacts
  without placing generated media under source packages.

Preconditions:

- `.video/` source package exists.
- Warehouse and publication paths are configured.

Main flow:

1. Discover `.video/` packages under BoK source.
2. Publish video artifact and sidecars to warehouse.
3. Write BoK-facing metadata to `src/main/publication`.
4. Build site so SmartDox can embed and link registered metadata.

Success:

- Video article renders with player and registered sidecar links where present.
- Generated MP4/RDF/caption/transcript artifacts remain outside `.video/`
  source packages.

Design implications:

- SmartDox consumes publication metadata; it does not execute video generation.
- RDF body merge and references must be driven from the registry boundary.

## BOK-UC-12: Run Publish Dry-Run Safely

Primary actor: Site Administrator

Goal:

- See the publication plan without changing source, publication registry,
  warehouse, generated site, or upload targets.

Preconditions:

- BoK source exists.

Main flow:

1. Run `bok publish --dry-run`.
2. Cozy performs preflight checks.
3. Cozy writes a dry-run operation manifest under `target/`.
4. Cozy prints planned update-publication, build, stage, and upload steps.

Success:

- The plan is visible and no side effects occur outside allowed ignored target
  output.

Design implications:

- Dry-run is an operation planning tool, not a partial publish.
- Missing upload workflow should be surfaced before production publish.

## BOK-UC-13: Stage Generated Site Output

Primary actor: Site Administrator

Goal:

- Copy generated `website.d` to a configured staging directory or run the
  configured stage workflow.

Preconditions:

- `website.d` exists.
- Stage workflow is configured when explicit staging is required.

Main flow:

1. Run `bok stage`.
2. Cozy delegates to project-owned stage command.
3. Cozy passes configured workflow environment values.

Success:

- Staged output is created outside the BoK source tree.

Design implications:

- Stage is workflow delegation, not built-in hosting policy.
- Stage should fail with an actionable message when configuration is missing.

## BOK-UC-14: Upload Through Project-Owned Workflow

Primary actor: Site Administrator

Goal:

- Upload staged or generated site output using a project-owned script.

Preconditions:

- Upload command is configured.
- Required public and sensitive environment settings are available from
  `conf/cozy/*` and `.cozy/*`.

Main flow:

1. Run `bok upload` or production `bok publish`.
2. Cozy resolves workflow environment values.
3. Cozy executes the configured upload script.
4. Script performs provider-specific upload and cache invalidation.

Success:

- Upload succeeds, or failure is reported as upload-step failure.

Design implications:

- Cozy should not silently skip unconfigured upload during production publish.
- Cozy should not implement provider credentials or provider-specific policy in
  core BoK operation.

## BOK-UC-15: Recover From Failed Build Or Upload

Primary actor: Site Administrator

Goal:

- Understand which step failed and safely retry.

Preconditions:

- A BoK operation failed during build, stage, upload, or publication.

Main flow:

1. Inspect command output and publish operation manifest.
2. Identify failed step and any completed prior steps.
3. Fix source, config, workflow script, or environment.
4. Rerun the same operation safely.

Success:

- Failure is attributed to the correct step.
- Upload is not attempted after build failure.
- Re-running with existing artifacts is safe.

Design implications:

- Publish manifests must record planned, skipped, succeeded, and failed steps.
- Failure diagnostics should distinguish build, stage, upload, and publication
  registry failures.

## Lifecycle View

Typical BoK operation follows this lifecycle:

```text
create source
  -> inspect / doctor
  -> add categories and knowledge
  -> build
  -> preview
  -> improve terms / RDF / publication metadata
  -> publish dry-run
  -> stage
  -> upload
  -> inspect operation manifest and iterate
```

Heavy media generation and external tool execution are explicit operations and
do not run as part of normal dashboard build or metadata-only checks.

## Open Design Pressure

The use cases intentionally leave the following as future design pressure:

- Whether `all` remains a visible dashboard actor control after User becomes
  the default view.
- How term quality diagnostics should be prioritized when a BoK has thousands
  of terms.
- How upload workflow templates should vary for non-AWS hosting.
- Whether BoK Manager should eventually receive a separate operation report in
  addition to the shared dashboard.

## References

- `docs/design/bok-dashboard-audience-actors.md`
- `docs/design/publish-d-metadata-spec.md`
- `docs/notes/bok-publish-operation.md`
- `docs/phase/phase-12.md`
