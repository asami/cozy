# BoK Bibliography / Reference Knowledge

## Purpose

`bibliography/` is BoK reference-source knowledge. It is not limited to
academic literature. Entries may represent books, papers, standards, web pages,
repositories, datasets, videos, specifications, or other external information
sources used by a BoK.

## Source Layout

Canonical source files live under:

```text
src/main/doxsite/bibliography/<slug>.dox
src/main/doxsite/bibliography/<slug>.md
src/main/doxsite/bibliography/<slug>.markdown
src/main/doxsite/bibliography/<slug>.bib.dox
src/main/doxsite/bibliography/<slug>.bib
src/main/doxsite/bibliography/<category>/<slug>.dox
src/main/doxsite/bibliography/<category>/<slug>.md
src/main/doxsite/bibliography/<category>/<slug>.markdown
src/main/doxsite/bibliography/<category>/<slug>.bib.dox
src/main/doxsite/bibliography/<category>/<slug>.bib
```

SmartDox `.dox` uses `HEAD` / properties. Markdown uses YAML front matter. Both
are parsed by SmartDox and normalized into Dox IR plus `DocumentMetaData`.
`*.bib.dox` is a BoK bibliography source entry whose suffix signals that the
entry is paired with or backed by BibTeX. Plain `*.bib` files are accepted as
BibTeX-only entries, but they are marked as `source_kind=bibtex-only` and
`needs_curation=true` because they lack BoK narrative and term-positioning text.

Files directly under `src/main/doxsite/bibliography/` define BoK-wide reference
knowledge. Files under `src/main/doxsite/bibliography/<category>/` define
category reference knowledge. In category context, category files are considered
before BoK-wide files.

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
`bibliography.refs`, `bibliography/*.bib`, `bibliography/<category>/*.bib`,
or bibliography source documents and
`doxsite.d/metadata/bibliography/bibliography.json` is empty after `dox site`,
Cozy fails explicitly. Cozy must not call the SmartDox library directly to
regenerate `doxsite.d`, because that would split the `dox antora` and `dox site`
runtime paths and hide an outdated PATH `dox` launcher.

## External Search And Cache

External search is explicit:

```sh
cozy bok search-bibliography "Design Patterns" --provider all --limit 10
```

Build-time resolution is the normal operation:

```sh
cozy bok build <project-dir>
```

After SmartDox emits `bibliography.json`, Cozy resolves `needs_resolution=true`
entries through the local cache first, then local BibTeX resolver sources, and
then the configured external bibliography provider. Resolved BibTeX is stored under
`target/cozy-bok/bibliography/cache/`, and the effective bibliography metadata
is written back for Dashboard, Term Hub, and entry pages.

Local BibTeX resolver sources are supported for existing BibTeX collections:

- `src/main/doxsite/bibliography/*.bib`
- `src/main/doxsite/bibliography/<category>/*.bib`
- `<repository-root>/bibliography/*.bib`
- `<repository-root>/bibliography/<category>/*.bib`
- `<repository-root>/catalog/bibliography/*.bib`
- `<repository-root>/catalog/bibliography/<category>/*.bib`

The resolver does not scan `<repository-root>/**/*.bib` broadly and does not
scan project-root `bibliography/**/*.bib`. Repository-side `*.bib.dox` files are
not supported; repository BibTeX sources are plain `.bib` resolver inputs.

This is not the recommended primary authoring style. BoK bibliography source
documents remain the preferred source of truth because they can add BoK-specific
summary, term links, RDF links, and narrative. Local `.bib` files are resolver
inputs alongside external services.

When the same `bibid` appears in `*.bib.dox` and `.bib`, the `*.bib.dox`
metadata and body are authoritative. BibTeX fields supplement only missing
metadata such as authors, publication year, DOI, ISBN, URL, and citation. If a
`*.bib.dox` entry has `bibliography.bibtex.raw`, that embedded BibTeX is used
first. If it has `bibliography.bibtex.source_url`, that source is resolved.
Otherwise a matching local `.bib` entry may supplement it.

Accepted local `bibliography.bibtex.source_url` prefixes are:

- `bibliography/...` for `src/main/doxsite/bibliography/...`
- `repository/bibliography/...` for `<repository-root>/bibliography/...`
- `repository/catalog/bibliography/...` for
  `<repository-root>/catalog/bibliography/...`

Other project-relative `.bib` paths are not resolver sources. URI-based URLs
are passed to the external provider/fetcher.

Offline/cache-only build is explicit:

```sh
cozy bok build <project-dir> --no-bib-service
```

In this mode, Cozy does not call external HTTP providers. Missing cache entries
are reported as warnings and the build continues.

Manual cache update is also available:

```sh
cozy bok update-bibliography <project-dir> [--force] [--report-only|--no-fetch]
```

`bok build --no-bib-service` never performs external HTTP. Cache output lives under:

```text
target/cozy-bok/bibliography/cache/
```

The cache is generated state and is not Git-managed.

`cozy bok update-bibliography` reads unresolved `bibid` entries and explicit
`bibliography.bibtex.source_url` values from `bibliography.json`, fetches the
corresponding BibTeX through local resolver sources or the configured
provider/fetcher, and writes cache files only. It does not rewrite BoK source
documents. `--report-only` and `--no-fetch` report missing cache entries without
external fetches. Cached external references are reported as
`source_kind=external-cache`.

## Responsibility Boundary

- SmartDox: parse `.dox` / Markdown, normalize metadata, collect bibliography
  entries, collect `bibid` references, accept BibTeX-only entries, and emit site
  RDF/resource metadata.
- Cozy: render bibliography UI, connect entries to Dashboard/Term Hub, search
  external providers on explicit command, resolve unresolved `bibid` entries
  during normal build, resolve local repository / bibliography `.bib` files as
  resolver sources, provide `--no-bib-service` offline/cache-only builds, and
  update local cache on explicit command without rewriting source.
