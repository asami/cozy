# SmartDox PDF Site-Link FQN Handoff

Date: 2026-09-07

Status: completed historical handoff; implementation and PDF acceptance closed; phase unassigned

## Authority

User direction during SimpleModeling.org Article 8 production.

## Completion record

This handoff's implementation and Article 8 PDF acceptance are complete. The
remaining sections preserve the original diagnostic and implementation contract
as historical evidence; they are not an open work order.

- SmartDox commit `84e9d99` accepts `--site-root` and `--site-config`, resolves
  site links on PDF routes, and emits LaTeX `\href` links with `hyperref`.
- Cozy commit `9b21e3b` requires and forwards the selected site root and site
  configuration to the SmartDox renderer, and records the site configuration
  and document-route inputs in the PDF receipt.
- The Article 8 PDF link and visual verification have been accepted by the
  user. No further Cozy or SmartDox change is requested by this handoff.

The implemented contract resolves `site:[literate-modeling.dox]` to the
localized title and the fully qualified public URL
`https://www.simplemodeling.org/ja/development-process/literate-modeling.html`.
Missing, invalid, escaping, ambiguous, or unpublished site targets remain
structured PDF-generation failures; they do not fall back to source-filename
text as a successful link.

## Historical diagnostic and implementation handoff (superseded)

The following records the pre-implementation diagnostic, the frozen
cross-repository contract, and the acceptance criteria that guided the now
completed work.

When a SmartDox site reference is emitted into an article PDF, the link target
must be a fully qualified public URL (FQN). Cozy must provide the publication
information needed for that resolution. A source filename must not be exposed
as the successful PDF fallback. If a valid public link cannot be produced, the
reference must not be presented as though it were a usable site link.

## Historical reproducer

The Article 8 source contains:

```dox
前回の記事: site:[literate-modeling.dox]
```

The DoxSite publication target is:

```text
https://www.simplemodeling.org/ja/development-process/literate-modeling.html
```

Before the implementation, the LaTeX article-PDF path instead rendered:

```text
前回の記事: literate-modeling.dox
```

This is not an Article 8 naming problem. It exposes a missing site-publication
context and PDF hyperlink projection contract.

## Historical boundary

SmartDox parses `site:[...]` as an internal hyperlink. The full DoxSite path
can resolve that hyperlink against the site tree, replace the authored target
with the target document title, and project an HTML-local path.

At diagnosis time, the standalone SmartDox PDF path did not run that
resolution:

- it parses only the selected input document;
- it supplies the input document's parent as a resource root for local assets;
- it does not supply the DoxSite source tree, current public page identity,
  locale-aware route mapping, or canonical site URL to the LaTeX converter;
- the then-current LaTeX converter traversed hyperlink label content but emitted no
  hyperlink target.

At diagnosis time, Cozy exposed the embedded SmartDox PDF operation, while Cozy Media's
article-PDF build invoked its configured SmartDox renderer with the source,
staged output, locale, and LaTeX format. It did not pass a site-publication
context. The semantic defect therefore originated in the SmartDox PDF
resolution/projection path, and the complete Article Media workflow required a
Cozy context-transfer extension.

## Implemented contract

The implementation uses one explicit, immutable **site publication context**
across the Cozy to SmartDox boundary. Cozy does not calculate or inject one
hard-coded FQN per authored link. SmartDox uses the supplied context and the
DoxSite document mapping to resolve every `site:[...]` reference.

The context must identify at least:

- the canonical DoxSite source root;
- the authoritative site configuration or equivalent canonical site base URL;
- the selected locale;
- the current source document identity;
- the current document's public path;
- the source-document to public-page mapping needed for Document Projects and
  ordinary site documents.

For SimpleModeling.org, resolving the example must produce the absolute URL:

```text
https://www.simplemodeling.org/ja/development-process/literate-modeling.html
```

The visible label should be the localized target article title. The PDF must
carry the absolute URL as a clickable hyperlink; displaying the URL itself is
not required when the title is present.

## Implemented responsibility split

### SmartDox

1. Define the typed site-publication context accepted by PDF generation.
2. Extract or reuse the DoxSite document-link resolver so HTML and PDF do not
   implement divergent source-to-public-path rules.
3. Resolve a relative `site:[...]` target to the localized target title and
   fully qualified public URL.
4. Extend the LaTeX projection to emit a safe hyperlink representation, such
   as `\href{FQN}{localized title}`, with the required LaTeX package support.
5. Reject invalid, escaping, ambiguous, unpublished, or unresolved targets
   deterministically.
6. Never treat the raw `.dox` filename as a successful PDF link label fallback.

### Cozy

1. Extend the article-PDF configuration and build plan with an explicit
   site-publication-context selection suitable for SmartDox sites.
2. Resolve that selection through the existing Cozy project/publication
   configuration boundary rather than an implicit current-directory search.
3. Validate that the selected source root and configuration are direct,
   non-symlink entries and that the article source belongs to the selected site.
4. Pass the frozen context to the SmartDox PDF renderer through an exact CLI or
   typed embedded-operation contract.
5. Bind the authoritative site configuration and document-route mapping into
   the Article PDF input set and receipt so a route or base-URL change makes the
   previous PDF stale.
6. Keep non-site standalone PDF generation compatible when it contains no
   `site:[...]` references.

### SimpleModeling.org acceptance

1. Select the repository-owned `src/main/doxsite/site.conf` and DoxSite source
   root through project configuration.
2. Generate the Japanese Article 8 PDF through the normal Cozy Article Media
   path.
3. Verify the previous-article link against the actual published Japanese URL.
4. Repeat the acceptance for an English site link before claiming localized
   site-link support complete.

## Failure policy

A `site:[...]` reference declares that site-aware resolution is required.
Therefore the default PDF behavior must be fail-closed:

- valid publication context and published target: emit the localized title and
  clickable FQN;
- missing or invalid context, or an unresolved target: fail PDF generation with
  a structured diagnostic and produce no accepted replacement artifact.

Silently degrading to a filename, relative `.html` path, or non-clickable label
is forbidden. An explicit future authoring construct may suppress a reference
for a non-site output, but suppression must not be inferred from resolution
failure because that would hide broken publication metadata.

## Required executable evidence for the completed contract

The completed implementation required executable evidence for:

1. the exact Article 8 Japanese reproducer resolving to
   `https://www.simplemodeling.org/ja/development-process/literate-modeling.html`;
2. a localized target title in visible PDF text;
3. the FQN in the generated PDF hyperlink annotation;
4. absence of the raw `literate-modeling.dox` fallback from visible PDF text;
5. deterministic failure when the site context is absent or the target cannot
   be resolved;
6. rejection of source-root escape, symlink escape, and cross-site ambiguity;
7. stale-receipt detection after changing the site base URL or public route;
8. unchanged DoxSite HTML link behavior;
9. unchanged standalone PDF behavior for documents without site references;
10. Japanese and English route resolution from one shared implementation.

Visual PDF QA must additionally confirm that the localized title is readable,
the link annotation covers the intended text, and line wrapping does not clip
or overlap adjacent content.

## Completed implementation order

1. SmartDox freezes the site-publication-context and resolved-link contracts.
2. SmartDox implements shared resolution and LaTeX hyperlink projection.
3. Cozy adopts the frozen interface and adds context/receipt propagation.
4. SimpleModeling.org supplies the real Article 8 acceptance driver.

No Cozy implementation should invent a temporary second link resolver while
the SmartDox contract is unsettled.

## Historical diagnostic evidence

The source inspection that preceded implementation confirmed the handoff
boundary without relying on a rendered-PDF symptom alone.

- `org.smartdox.service.operations.PdfOperationClass` parses one input file
  with that file's parent directory as the resource root. Its `PdfCommand`
  accepts generic site parameters but has no typed site-publication-context
  input and the LaTeX route never constructs a `DoxSite` document tree.
- `org.smartdox.converters.Dox2LatexConverter` traverses a `Hyperlink`'s
  visible contents, but both `enter_Hyperlink` and `leave_Hyperlink` are
  currently empty. The generated TeX therefore has neither a link target nor
  the `hyperref` package needed to emit a PDF annotation.
- The existing DoxSite `LinkEnabler` already resolves a local/relative
  hyperlink through site metadata, replaces its label with the localized
  document title, and produces the HTML-local route. That behavior is not
  available to the standalone PDF route.
- `cozy.media.CozyMediaPdf` then appended only the selected source,
  staged output, locale, and LaTeX format to the configured renderer argv.
  It neither accepts nor transports publication context.

This was a confirmed missing cross-repository capability, not a malformed
Article 8 source or a Cozy renderer-token ordering defect.

## Historical frozen implementation handoff

### Admitted update roots

1. SmartDox production and executable-spec sources implementing the PDF
   operation, DoxSite-aware link resolution, and LaTeX projection.
2. Cozy article-PDF configuration, plan/receipt input capture, renderer argv
   construction, and their executable specifications, but only after the
   SmartDox contract is available as a development dependency.

SimpleModeling.org is an acceptance driver only. Its Article 8 source and
published site must not be edited, deployed, uploaded, or published by this
work.

### SmartDox deliverable

Freeze a typed, immutable site-publication context and an exact PDF-operation
input surface before implementation. The context must be sufficient to load
the selected DoxSite source root and authoritative configuration, identify the
current source document and public route, select the locale, and resolve a
site target through the same document mapping used by DoxSite HTML output.

For a valid `site:[literate-modeling.dox]` reference in the Japanese Article 8
context, SmartDox must project the localized target title as a safe LaTeX
hyperlink whose target is exactly:

```text
https://www.simplemodeling.org/ja/development-process/literate-modeling.html
```

The shared resolver, not Cozy, owns source-root containment, symlink checks,
route selection, ambiguity detection, localized title lookup, and public-URL
construction. The LaTeX projection must add its required package support and
escape both URL and label safely. A missing, invalid, escaping, ambiguous, or
unpublished target is a structured PDF-generation failure; it must not fall
back to a source filename, a relative HTML path, or non-clickable text.

### Cozy follow-on deliverable

Once SmartDox exposes the frozen input contract, add one explicit
`sitePublicationContext` selection to the Article PDF descriptor. Resolve it
only through the selected project/publication configuration. The selection
must name direct, non-symlink source/configuration entries, prove that the
article source belongs to that site, and become part of the captured article
PDF input set and receipt.

Pass the frozen context to SmartDox through its exact CLI or typed embedded
operation. Do not infer a site root from the current directory or pass one
untyped bundle of ad-hoc flags. A changed site base URL, source-to-public route
mapping, or selected configuration must make prior Article PDF acceptance
evidence stale.

### Required regression specifications

SmartDox must add executable specifications for:

1. Japanese and English relative `site:[...]` resolution through one shared
   mapping implementation.
2. The localized visible title and absolute FQN in generated LaTeX/PDF
   hyperlink evidence.
3. Rejection of absent context, unresolved target, source-root escape,
   symlink escape, and cross-site ambiguity.
4. Preservation of existing DoxSite HTML link output and standalone PDF
   behavior for documents that do not use `site:[...]`.

Cozy must add executable specifications for:

1. exact propagation of the selected context to the frozen SmartDox input
   surface;
2. rejection before renderer invocation for an invalid context selection or a
   source outside the chosen site; and
3. receipt staleness after changes to the selected site configuration or
   route-mapping input.

The SimpleModeling.org acceptance driver then verifies Article 8 PDF text,
annotation target, and visual layout for Japanese and English. It is not a
substitute for the focused SmartDox and Cozy specifications.

### Integration and stop conditions

Do not begin Cozy production changes until the SmartDox interface is frozen
and consumable from a distinct development (non-release) coordinate. Do not
replace the missing upstream behavior with filename rewriting, a hard-coded
SimpleModeling.org URL, independent Cozy route parsing, or a permissive
fallback. If the proposed SmartDox interface cannot carry all context fields
above, stop and revise that interface before any Cozy integration.

## Non-solutions and forbidden scope

- Do not hard-code `https://www.simplemodeling.org/` in Cozy or SmartDox.
- Do not rewrite every `site:[...]` source occurrence to an Article 8-specific
  absolute Markdown link as the architectural fix.
- Do not infer a site root by unconstrained ancestor scanning at render time.
- Do not duplicate DoxSite route and Document Project mapping logic in Cozy.
- Do not accept a PDF merely because it contains readable filename text.
- Do not deploy, upload, publish, or alter the live SimpleModeling.org site as
  part of this handoff.
- Do not assign this work to an existing closed phase without a separate phase
  planning decision.
