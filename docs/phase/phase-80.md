# Phase 80: Site Tag Navigation and Related Content Index

status=planned
planned_at=2026-10-05
predecessor=[Phase 13](phase-13.md)
scope=Cozy BoK/site tag navigation

## Problem

Phase 13 made tags first-class BoK metadata and generated tag pages, but the
current user-facing behavior does not make tags useful as navigation.

A page renders tag chips, but following a tag primarily presents the tag
definition/explanation surface. In normal operation many tags have no useful
curated definition, so the destination provides little value even though Cozy
already knows the resources carrying that tag.

This is a product/navigation defect rather than a request to generate richer
placeholder descriptions.

## Goal

Make a tag click answer the practical question:

> What content in this site is related to this tag?

Treat the tag destination primarily as a related-content index/navigation
surface. A curated tag definition, when one exists, is optional context for
that index and must not replace the related-content navigation.

## Design principles

- A tag is primarily a lightweight cross-cutting navigation key, consistent
  with Phase 13.
- The useful content of a tag page is the set of site resources associated
  with the canonical tag.
- Tag descriptions are optional authored metadata, not a prerequisite for a
  useful tag page.
- Cozy MUST NOT fabricate explanatory prose merely because a tag definition is
  absent.
- Existing canonical tag identity, hierarchical tag keys, tag URLs, metadata
  handoff, RDF linkage, and category-scoped normalization remain in force.
- Glossary terms and tags remain distinct. A tag page is not a glossary-entry
  page.
- Existing source tags remain authoritative; Phase 80 does not infer tags from
  body text.

## Required behavior

### P80-01: Tag click destination

Every rendered tag chip/link on a site content page MUST resolve to the
canonical tag navigation page for that tag.

The destination MUST prominently expose related content. The user must not
need a meaningful tag-definition document for the navigation to be useful.

### P80-02: Related-content index

For a canonical tag, aggregate all available site resources carrying that tag
through the existing Phase 13 metadata routes, including where present:

- articles/documents;
- terms;
- scenarios;
- projects;
- bibliography entries;
- repository CAR resources;
- SIE Information/RDF resources;
- other already-supported TagReference kinds.

Each related item MUST link to the actual resource page. Results MUST be
deterministic and duplicate resource references MUST be collapsed by canonical
resource identity.

The page MAY group results by resource kind and/or category, but the related
content itself is the primary surface.

### P80-03: Optional tag definition

If explicit tag definition metadata provides a non-empty label, summary, or
body, render it as supplemental context.

If no meaningful explicit definition exists:

- do not render generated explanatory filler as though it were tag knowledge;
- do not make an empty/generic Purpose section the main content;
- still render the related-content index normally.

A generic localized sentence derived only from the tag name is UI fallback,
not tag knowledge, and MUST NOT displace the related-content index.

### P80-04: Hierarchical navigation

Preserve Phase 13 hierarchical tag semantics.

- tags/index.html remains the tag overview.
- namespace pages remain useful entry points into their descendant tags.
- leaf/canonical tag pages expose related content.
- breadcrumbs and parent/namespace navigation remain available.
- existing canonical public paths remain stable unless a concrete compatibility
  defect requires a separately documented migration.

### P80-05: Site-wide consistency

Verify tag navigation from representative site surfaces, at minimum:

- article/document;
- Term Hub;
- Scenario;
- Project;
- Bibliography.

Where repository CAR or SIE/RDF tag links are present, verify those paths too.

A tag rendered on any supported surface must lead to the same canonical tag
identity and the same effective related-resource set for the selected locale.

## Implementation direction

Reuse the existing Phase 13 tag model, TagIndex, TagEntry.refs,
TagReference, canonical tag normalization, and hierarchical URL generation.

The current CozyBokTagPages detail projection already has both tag definition
data and related references. Refactor the presentation priority rather than
introducing a second tag subsystem.

In particular:

1. make related resources the principal body of a canonical tag detail page;
2. render authored definition content only when meaningful;
3. remove or demote generic Purpose/description fallback that currently makes
   an undefined tag look like an explanation page;
4. retain RDF navigation as an additional view, not as a replacement for the
   normal related-content list;
5. keep tag-chip targets canonical and locale-correct.

Do not introduce search indexing, AI-generated summaries, tag recommendation,
or a new database for this phase.

## Executable specifications

At minimum prove:

1. an article with tag technology.rdf renders a tag link to the canonical
   technology.rdf page;
2. the canonical page lists that article and every other fixture resource
   carrying the tag;
3. a tag with no explicit definition still produces a useful related-content
   page and does not present fabricated definition prose as knowledge;
4. a tag with an explicit authored summary/body preserves that content as
   supplemental context while related content remains visible;
5. duplicate references from handoff metadata and usage-derived metadata appear
   once;
6. multiple supported resource kinds are grouped/labeled and link to their
   actual pages;
7. hierarchical namespace and leaf navigation continues to work;
8. locale-specific tag metadata does not leak resources from another locale;
9. existing RDF tag navigation remains reachable;
10. untagged pages remain unchanged and no empty tag UI is introduced.

Include a representative real-site/fixture verification in addition to focused
unit/spec coverage so the original user-visible failure mode is exercised by
clicking a tag from content to its destination.

## Acceptance criteria

Phase 80 completes when:

1. clicking a tag from representative site content leads to a useful
   related-content navigation page;
2. tag usefulness does not depend on an authored tag explanation document;
3. the tag page exposes all supported related resource types available from
   existing metadata, with deterministic duplicate-free links;
4. meaningful authored tag descriptions remain available only as supplemental
   context;
5. generic/empty description fallback no longer dominates the tag destination;
6. Phase 13 canonical tag identity, hierarchy, metadata handoff, and RDF
   integration remain compatible;
7. focused executable specs, representative site navigation verification, and
   normal Cozy validation pass.

## Non-goals

- AI-generated tag explanations or summaries;
- automatic tagging from document body text;
- tag governance, approval, moderation, or recommendation;
- replacing glossary terms with tags;
- redesigning categories;
- introducing a search engine or external index;
- changing unrelated site navigation.
