# CAR Documentation Lint

## Purpose

`cozy lint car` treats documentation as part of the CAR public contract. The
deterministic Cozy check verifies that documentation sources and generated-help
inputs exist. `cncf-car-lint` adds an AI review of whether the prose is useful
and accurate.

## Admitted CAR Manual Source

The manual source is resolved from `packaging.car.source_dir` when that value is
configured in the project configuration. Relative values are resolved from the
project root and normalized; absolute values are normalized as-is. When the
setting is omitted, Cozy uses `src/main/car`. A configured but missing source is
not silently replaced by the default. Entry-point and recursive manual checks
inspect only the admitted source's `manual/` subtree, matching the CAR packager's
source selection.

## Cozy Documentation Category

Cozy reports these checks under the `documentation` category. Missing or thin
documentation is a warning in normal lint and fails `--strict` release
readiness.

### Reference Manual

The reference manual must have a CAR-package entry point under the admitted
source's `manual/` directory. Recognized entry-point stems are:

- `index`
- `reference-manual`
- `reference`

Recognized source suffixes are `.md`, `.markdown`, `.adoc`, `.asciidoc`, `.dox`,
and `.html`.

When more than one canonical file matches an entry-point class, Cozy retains one
deterministic present finding and emits
`car.documentation.manual.entry-point.duplicate` as a warning for the duplicate.
Cozy packages this source subtree as `manual/` in the CAR. CNCF runtime exposes
the same relative paths below `/man/<component>/`.

### User Guide

The user guide may be provided through one of these authoring surfaces:

- `<admitted-car-source>/manual/user-guide.*`
- `src/main/web/docs/user-guide.*`
- `docs/user-guide.*`
- `docs/guide/index.*`
- `docs/guide/README.md`

The canonical admitted-source entry point takes precedence over legacy locations
for compatibility.

### Manual References and Assets

Supported manual documents (`.md`, `.markdown`, `.adoc`, `.asciidoc`, `.dox`,
and `.html`) are inspected recursively below the admitted `manual/` directory.
Markdown-style local references must resolve inside that directory. Anchors,
empty references, and URL-scheme references are ignored. Absolute or escaping
paths produce `car.documentation.manual.link.unsafe`; missing image references
produce `car.documentation.manual.asset.missing`; missing non-image references
produce `car.documentation.manual.link.stale`. Manual files are decoded as
strict UTF-8, and unreadable or malformed input produces
`car.documentation.manual.input.unparseable`. Findings point to the source
manual document, never to a referenced target, and are deterministic with
duplicate findings suppressed. Symlinks are not followed.

These findings are warnings in normal lint. Existing strict mode treats
documentation warnings as release-blocking and exits nonzero; the CLI contract
is unchanged.

### Generated Help Descriptions

The CML component, every service, and every operation should provide a summary,
description, or narrative. Cozy warns when the effective text is absent or has
fewer than 24 non-whitespace characters. This threshold only detects obviously
thin help; it does not claim that the prose is semantically sufficient.

### Generated Component Help

CNCF runtime owns the representative component Help layout. It generates the
CLI help command and navigation to `/help/<component>`, `/man/<component>`, the
canonical OpenAPI route `/openapi.json`, and `/mcp`. CAR authors do not repeat
these framework-owned routes in CML prose. Cozy validates the author-owned CML
descriptions; CNCF executable specifications validate the generated routes.

`/help/system/openapi.json` and
`/web/system/document/specification/openapi.json` remain runtime compatibility
routes. New generated Help uses `/openapi.json`.

Service and operation discovery remains generated from CML, while their own
descriptions must pass the description checks above.

## AI Review Boundary

The `cncf-car-lint` skill reads the actual manual, guide, CML descriptions, CLI
examples, and Web route declarations after Cozy lint. It checks semantic
quality, task coverage, consistency, and whether links lead users to real
information. AI findings must be reported separately from deterministic Cozy
findings.

Cozy does not call an AI provider, start the component, or fetch external
documentation during lint. Runtime CLI/Web execution remains an explicit
release-readiness verification when the project can be started locally.
