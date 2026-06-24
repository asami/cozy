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
src/main/doxsite/bibliography/<category>/<slug>.bib
```

SmartDox `.dox` uses `HEAD` / properties. Markdown uses YAML front matter. Both
are parsed by SmartDox and normalized into Dox IR plus `DocumentMetaData`.
BibTeX-only files are accepted as publishable bibliography entries, but they are
marked as `source_kind=bibtex-only` and `needs_curation=true` because they lack
BoK narrative and term-positioning text.

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

Documents that cite bibliography knowledge may use either of these metadata
keys:

- `bibliography.refs`
- `references.bibliography`

Each reference is a provider-qualified `bibid`, for example `bib:local-id`,
`doi:10.1145/...`, `isbn:978...`, `openlibrary:works/...`, or `dblp:...`.

BibTeX is supplemental import/cache data. It is not the BoK source of truth.
BoK source metadata and narrative take precedence over BibTeX-derived values.

If a `bibid` reference has no internal or BibTeX-only definition, SmartDox still
materializes it in `bibliography.json` as `source_kind=external-ref` with
`needs_resolution=true`. This makes unresolved references visible in published
preview output without performing network access during build.

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
It only validates the SmartDox handoff boundary: if BoK source declares
`bibliography.refs` or `bibliography/**/*.bib` and
`doxsite.d/metadata/bibliography/bibliography.json` is empty after `dox site`,
Cozy fails explicitly. Cozy must not call the SmartDox library directly to
regenerate `doxsite.d`, because that would split the `dox antora` and `dox site`
runtime paths and hide an outdated PATH `dox` launcher.

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

`cozy bok update-bibliography` reads unresolved `bibid` entries and explicit
`bibliography.bibtex.source_url` values from `bibliography.json`, fetches the
corresponding BibTeX through the configured provider/fetcher, and writes cache
files only. It does not rewrite BoK source documents. A later `bok build` uses
that cache to materialize effective title, authors, year, identifiers, citation,
and BibTeX key fields. Cached external references are reported as
`source_kind=external-cache`.

## Responsibility Boundary

- SmartDox: parse `.dox` / Markdown, normalize metadata, collect bibliography
  entries, collect `bibid` references, accept BibTeX-only entries, and emit site
  RDF/resource metadata.
- Cozy: render bibliography UI, connect entries to Dashboard/Term Hub, search
  external providers on explicit command, update local cache on explicit
  command, and apply cached BibTeX metadata during build without network calls.
