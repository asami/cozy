# BoK to SIE Integration Handoff

status=handoff
published_at=2026-06-28

## Purpose

This document hands off the SIE BoK KnowledgeSource integration requirements to
Cozy / SmartDox.

SIE now has a BoK KnowledgeSource reader route. Cozy should publish the metadata
contract that lets SIE ingest a generated BoK site without scraping HTML.

## Current SIE Baseline

SIE commit:

```text
64e035d Add BoK KnowledgeSource ingestion
```

Implemented SIE behavior:

- `BokKnowledgeSourceReader` accepts a BoK site base URI.
- The preferred first fetch is:

```text
/metadata/cncf/knowledge-source.json
```

- The manifest `resources` list points to metadata resources relative to the
  site base URI.
- `glossary-terms` is implemented as the first resource reader.
- Current Cozy layout fallback is supported when the manifest is missing:

```text
/metadata/glossary/terms.json
```

- `{ "terms": [] }` is a valid empty input and produces a successful frame with
  zero glossary-term nodes.
- SIE does not read rendered BoK HTML.

## Required Cozy Output

Cozy should publish this manifest into generated BoK sites:

```text
website.d/metadata/cncf/knowledge-source.json
```

Public URL:

```text
<site-base>/metadata/cncf/knowledge-source.json
```

Selected v1 schema:

```json
{
  "schemaVersion": "cncf.knowledge-source.v1",
  "kind": "bok-site",
  "id": "knowledgehub",
  "label": "KnowledgeHub",
  "sourceRef": {
    "kind": "bok-site",
    "value": "knowledgehub",
    "uri": "https://example.org/"
  },
  "resources": [
    {
      "kind": "glossary-terms",
      "href": "metadata/glossary/terms.json",
      "mediaType": "application/json"
    },
    {
      "kind": "rdf-jsonld",
      "href": "site.jsonld",
      "mediaType": "application/ld+json"
    },
    {
      "kind": "rdf-turtle",
      "href": "site.ttl",
      "mediaType": "text/turtle"
    },
    {
      "kind": "rdf-graph-summary",
      "href": "metadata/rdf/graph.json",
      "mediaType": "application/json"
    }
  ]
}
```

For v1, `glossary-terms` is required when the site has a glossary metadata
resource. RDF resources may be listed only when Cozy actually publishes those
files.

Do not publish `/.well-known/cncf-knowledge.json` for this integration. The
selected manifest path is `/metadata/cncf/knowledge-source.json`.

## Current `terms.json` Contract

SIE v1 consumes the current SmartDox / Cozy `metadata/glossary/terms.json`
shape. Cozy does not need to introduce a grouped v2 schema for this slice.

Accepted top-level shape:

```json
{
  "terms": []
}
```

Recognized term fields:

- `id`
- `slug`
- `title`
- `reading`
- `category`
- `source_path`
- `public_path`
- `definition_html`
- `summary`
- `aliases`
- `term_type`
- `event`
- `actor`
- `role`
- `article_refs`
- `term_refs`
- `rdf_refs`
- `video_refs`
- `quality`

SIE treats `rdf_refs` as RDF supplementary evidence / relationship candidates.
They are not confirmed RDF anchors by default.

## Cozy Implementation Targets

Likely Cozy implementation locations:

- `cozy.bok.CozyBok` for generated BoK site metadata output.
- Existing BoK tests around generated `metadata/glossary/terms.json`, including:
  - `CozyBokSpec`
  - `CozyBokMonoKotoSpec`
  - `CozyBokTermTypeSpec`
  - `CozyBokTagSpec`
  - `cozy.bok.CozyBokBibliographySpec`

The implementation should:

1. Keep the existing `metadata/glossary/terms.json` shape stable.
2. Add `metadata/cncf/knowledge-source.json`.
3. Resolve `sourceRef.uri` from the configured public site base URL when
   available.
4. Use a deterministic `sourceRef.value` / manifest `id`, preferably the BoK
   publication/site key.
5. Include `glossary-terms` resource when `terms.json` is emitted.
6. Include RDF resources only when the corresponding generated files are
   actually emitted.
7. Avoid copying raw rendered HTML into the manifest.

## Validation Plan

Cozy-side tests should assert:

- Generated site contains:

```text
website.d/metadata/cncf/knowledge-source.json
website.d/metadata/glossary/terms.json
```

- Manifest has:
  - `schemaVersion = cncf.knowledge-source.v1`
  - `kind = bok-site`
  - `sourceRef.kind = bok-site`
  - `resources[].kind = glossary-terms`
  - `resources[].href = metadata/glossary/terms.json`

- Manifest `resources.href` values are relative paths, not absolute URLs.
- No `/.well-known/cncf-knowledge.json` output is required.
- Existing `terms.json` tests remain unchanged.

SIE integration smoke check after Cozy output exists:

```console
sie.ingestBokKnowledgeSource baseUri=<generated-site-uri> registerKnowledgeSpace=false includeKnowledgeFrame=true
```

Expected result:

- manifest-backed path is used;
- `warningCount = 0`;
- `termCount` matches `terms.json`;
- `knowledgeSpaceState = frame_only` when `registerKnowledgeSpace=false`;
- `knowledgeFrame` is present when `includeKnowledgeFrame=true`.

## Non-goals for This Cozy Slice

- Do not redesign `terms.json`.
- Do not add grouped RDF term metadata such as `primaryRdfUri`, `sameAs`,
  `exactMatch`, `broader`, or `narrower`; that is a v2 candidate.
- Do not implement SIE search result mixing in Cozy.
- Do not require SIE to scrape HTML.
- Do not make RDF JSON-LD / Turtle parsing a Cozy responsibility.

## Open Follow-ups

- Decide the stable BoK site identifier used for manifest `id` and
  `sourceRef.value`.
- Confirm the public site base URI source used for `sourceRef.uri`.
- Confirm whether current BoK publishing always emits `site.jsonld`,
  `site.ttl`, and `metadata/rdf/graph.json`; list only existing resources.
- Add an end-to-end fixture that generates a BoK site with manifest and runs the
  SIE ingestion operation against the generated `file:` URI.
