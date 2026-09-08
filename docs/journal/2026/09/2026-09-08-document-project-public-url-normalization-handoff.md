# Document Project Public URL Normalization Handoff

Date: 2026-09-08

Status: SmartDox correction committed; Cozy consumer validation and local development-SNAPSHOT refresh pending; no validation or publication performed

## Authority

User-reported SimpleModeling.org publication result for the Document Project
form of the Domain Modeling article.

## Historical observed behavior

The Document Project source package is represented as:

```text
development-process/domain-modeling.dox/index.dox
```

The originally observed published Japanese article resolved at:

```text
https://www.simplemodeling.org/ja/development-process/domain-modeling.dox/index.html
```

That historical path exposed the internal Document Project package layout.  The
canonical public article URL is instead:

```text
https://www.simplemodeling.org/ja/development-process/domain-modeling.html
```

The same normalization is required for every locale and for every admitted
`<slug>.dox/index.dox` Document Project package.  It is not an Article 8-only
redirect or content correction.  The originally observed bad route is
historical; this handoff does not claim a current publication result.

## Confirmed cause and ownership

The historical host DoxSite output and metadata routes derived public paths by
changing only the final source suffix from `dox` to `html`.  Consequently,
`development-process/domain-modeling.dox/index.dox` became
`development-process/domain-modeling.dox/index.html`.

The affected implementation was SmartDox's DoxSite path projection, including
the generated realm path, internal links, document fragments, tag metadata,
RDF, and Atom entries.  The shared SmartDox correction is already committed at
`73ce206dbddc3634aa6d29f9e704d3decfa49c99` (`Normalize Document Project public
URLs`).  That producer change implements the canonical mapping and updates its
`DoxSiteSpec` coverage for realm paths, links, fragments, tags, RDF/JSON-LD,
Atom, and PDF-publication context.  Cozy 2.4.19-SNAPSHOT depends on that
SmartDox projection; Cozy consumer validation and the local development
dependency-artifact refresh remain pending.

Cozy's Document Project contract intentionally passes the package unchanged to
the host DoxSite build and does not independently discover, register, or
publish site documents.  Therefore Cozy must not add a second source-to-public
path mapper, a post-generation URL rewrite, or an Article 8-specific adapter.
Those would create divergent route authority.

## SmartDox contract (committed producer correction)

The committed SmartDox correction defines one canonical source-to-public-route
mapping shared by all DoxSite projections.  For a Document Project package it
maps:

```text
<directory>/<slug>.dox/index.dox -> <directory>/<slug>.html
```

The mapping preserves existing ordinary-file behavior, including:

```text
<directory>/<slug>.dox -> <directory>/<slug>.html
```

It is used consistently for generated HTML output, internal hyperlinks,
document-fragment `publicPath`, tag metadata, RDF/JSON-LD, Atom, navigation,
and any site-publication context consumed by PDF generation.  No generated
surface may retain `.dox/index.html` as the public identity of a Document
Project article.  The producer focused proof for this committed correction is
still pending in the execution order below.

The SmartDox regression specification covers both the exact Document Project
mapping and unchanged ordinary Dox source mapping, including Japanese and
English output paths for the Domain Modeling-shaped fixture.  This handoff
does not report that proof as executed.

## Cozy follow-on

After the committed SmartDox correction is refreshed as the local development
SNAPSHOT dependency, Cozy must run its existing integration acceptance evidence
against the actual generated DoxSite realm.  Cozy's evidence must assert the
canonical locale paths:

```text
doxsite.d/ja/development-process/domain-modeling.html
doxsite.d/en/development-process/domain-modeling.html
```

The follow-on must preserve Cozy's Document Project ownership boundary.  It
must not change the Document Project descriptor, infer a site root, publish or
deploy SimpleModeling.org, modify generated website files, or retain a
compatibility path for `.dox/index.html`.

## Acceptance boundary

1. SmartDox generates only the canonical public Document Project article paths.
2. Generated links and all generated metadata reference those same canonical
   paths.
3. Cozy resolves the updated SmartDox SNAPSHOT and its focused integration
   evidence observes the canonical paths.
4. SimpleModeling.org is used only as a downstream acceptance driver after the
   two producer repositories are validated.  This handoff authorizes no
   website mutation, publication, deployment, upload, or push.

## Suggested execution order (remaining work)

1. Run the focused SmartDox proof for the already committed route
   normalization.
2. Refresh the local development dependency artifact through the approved
   serialized `publishLocal` dependency-refresh path.
3. Run Cozy's focused DoxSite integration specification against that exact
   development SNAPSHOT.
4. Later, rebuild the SimpleModeling.org site in its separate workflow and
   verify the canonical Japanese and English URLs before any publication
   decision.

None of these remaining steps is reported as completed by this handoff.

## Non-goals

- no Article 8 source rename or manual source-tree reshaping;
- no Cozy-side output rewrite or duplicate route resolver;
- no redirect as a substitute for correcting generated canonical links;
- no editing of the SimpleModeling.org generated website in this work; and
- no publication, deployment, upload, push, or commit implied by this handoff.
