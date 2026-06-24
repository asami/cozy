# BoK Document Fragment Handoff

## Summary
SmartDox is the source of truth for BoK source document processing. Cozy does
not re-parse Home, Category, or article body narrative for Dashboard rendering.
SmartDox parses SmartDox `.dox` and Markdown `.md` / `.markdown` sources into
Dox IR, applies locale filtering, glossary auto-linking, and `site:[...]`
self-site link resolution, then emits localized document fragments as metadata.

## Handoff File
SmartDox emits:

```text
doxsite.d/metadata/documents/fragments.json
```

Each entry contains:

- `sourcePath`: source document path under `src/main/doxsite`
- `publicPath`: projected public HTML path
- `locale`: localized fragment locale
- `kind`: document kind when available
- `category`: source category when available
- `title`: document title
- `headline`: effective headline
- `brief`: effective brief
- `bodyHtml`: localized HTML body fragment

## Processing Boundary
SmartDox owns:

- SmartDox and Markdown parsing
- YAML front matter and SmartDox `HEAD` metadata normalization
- Dox IR generation
- locale filtering
- glossary extraction and glossary auto-linking
- `site:[...]` self-site link resolution
- manual and special-page auto-link exclusion rules
- `metadata/glossary/terms.json`
- `metadata/documents/fragments.json`

Cozy owns:

- Dashboard, Category, Term Hub, Scenario, Video, and CAR UI rendering
- scenario semantics in the Kaleidox-oriented model layer
- publication registry and artifact integration
- copying SmartDox machine-readable metadata into generated websites

Cozy must not reconstruct document narrative or glossary terms from source
files when SmartDox metadata is available. If `terms.json` is missing, Term Hub
and Glossary Dashboard render empty metadata-driven surfaces rather than
rebuilding terms from category source directories.

## Scenario Exception
Scenario semantics are not SmartDox document-processing semantics. SmartDox
only provides Dox IR and `DocumentMetaData`; Cozy/Kaleidox interprets scenario
metadata, flows, directives, and persona journey structure.
