# BoK Bibliography / Reference Knowledge

## Purpose

`bibliography/` is BoK reference-source knowledge. It is not limited to
academic literature. Entries may represent books, papers, standards, web pages,
repositories, datasets, videos, specifications, or other external information
sources used by a BoK.

## Source Layout

Canonical source files live under:

```text
src/main/doxsite/bibliography/<category>/<slug>.dox
src/main/doxsite/bibliography/<category>/<slug>.md
src/main/doxsite/bibliography/<category>/<slug>.markdown
```

SmartDox `.dox` uses `HEAD` / properties. Markdown uses YAML front matter. Both
are parsed by SmartDox and normalized into Dox IR plus `DocumentMetaData`.

## Metadata

Common metadata is stored under `bibliography.*`:

- `bibliography.id`
- `bibliography.type`: `book`, `article`, `paper`, `standard`, `web-page`,
  `repository`, `dataset`, `video`, `specification`, or `other`
- `bibliography.title`
- `bibliography.summary`
- `bibliography.authors`
- `bibliography.published_at`
- `bibliography.publisher`
- `bibliography.source_url`
- `bibliography.accessed_at`
- `bibliography.terms`
- `bibliography.citation`
- `bibliography.identifiers.doi`, `isbn`, `issn`, `url`, `urn`, `arxiv`,
  `github`, `wikidata`
- `bibliography.bibtex.key`, `entry_type`, `source_url`, `raw`

BibTeX is supplemental import/cache data. It is not the BoK source of truth.
BoK source metadata and narrative take precedence over BibTeX-derived values.

## Handoff Contract

SmartDox generates:

```text
doxsite.d/metadata/bibliography/bibliography.json
```

Cozy consumes that metadata for:

- Bibliography Dashboard: `website.d/bibliography/index.html`
- Home Dashboard reference KPI
- Category related-knowledge links
- Term Hub related references
- generated metadata copy under `website.d/metadata/bibliography/`

Cozy does not re-parse `.dox` or Markdown bibliography source during `bok build`.

## External Search And Cache

External search is explicit:

```sh
cozy bok search-bibliography "Design Patterns" --provider all --limit 10
```

Cache update is explicit:

```sh
cozy bok update-bibliography <project-dir> [--force]
```

`bok build` never performs external HTTP. Cache output lives under:

```text
target/cozy-bok/bibliography/cache/
```

The cache is generated state and is not Git-managed.

## Responsibility Boundary

- SmartDox: parse `.dox` / Markdown, normalize metadata, collect bibliography
  entries, and emit site RDF/resource metadata.
- Cozy: render bibliography UI, connect entries to Dashboard/Term Hub, search
  external providers on explicit command, and update local cache on explicit
  command.
