# BoK public document integration

Status: normative Phase 75.1 consumer contract
Date: 2026-10-07

## Producer consumption

Cozy consumes `org.smartdox:smartdox_2.12:2.4.19-SNAPSHOT` from the accepted
SmartDox producer Step P751-01, source commit `38719e1`. The existing Cozy
dependency declaration is unchanged. SmartDox owns source admission, grammar,
physical/logical/public identity, package closure and parsed aliases through
its sole effective-content owner. Cozy passes the DoxSite-configured Realm to
the public `DoxSiteGenerator` and `AntoraGenerator`; it must not implement a
second path mapper, parser, private-note filter or fabricated `.dox` source.

The producer contracts are [public content](../../../smartdox/docs/spec/doxsite-public-content.md),
[physical includes](../../../smartdox/docs/spec/doxsite-physical-include-base.md)
and [Markdown parsing](../../../smartdox/docs/spec/markdown-document-parsing.md).
The integration design is [BoK public document integration](../design/bok-public-document-integration.md).

## Physical source and public projection

A native physical Realm created with `DoxSite.realmConfig` retains its source
origin. A published package at `development-process/domain-modeling.dox/index.dox`
has logical identity `development-process/domain-modeling.dox` and canonical
public page `development-process/domain-modeling.html`. Relative includes in
the package index use its actual physical parent. Ordinary sibling `.dox`
sources retain their own physical relative include bases and same-stem routes.
An absolute fixture root containing spaces and Japanese characters must work
without changing the process working directory.

With multi-locale output, both JA and EN HTML pages contain the real included
sentinel content. Antora exposes the effective package and ordinary sibling
as pages under the category's `modules/ROOT/pages/`. Neither a public
`domain-modeling.dox/index.html` nor a package-index Antora page is an alternate
public article. Existing bilingual title and ordinary sibling assertions remain.

## Private package closure and card inputs

### Shared source catalogue and counts

`CozyBokMetadata._category_contents` selects the whole valid physical source root
once with `Realm.create(DoxSite.realmConfig, source.toFile)` and
`DoxSitePublicDocuments.sources`. Missing/empty source input remains empty.
Regular `category.yaml` summaries retain their existing ownership and order.
Each category groups producer descriptors by canonical public category prefix,
excluding its generic `index.html` and glossary presentation entries. Article
hrefs remove that prefix; title, brief and modification time come only from
the descriptor's actual physical source. The package direct index is one
flattened article, never omitted merely because its physical leaf is `index.dox`.

Article listings and `_source_article_count` share this selection. No article
filesystem walk, package parser, suffix mapper or fallback scan supplies another
candidate. The glossary-specific walker, metadata parser, locale presentation
and legacy no-status source eligibility retain their established behavior.
Invalid selected sources fail normally rather than being silently suppressed.

[CozyBokPublicArticleCatalogueSpec](../../src/test/scala/cozy/bok/CozyBokPublicArticleCatalogueSpec.scala)
authors P751-02F-CATALOGUE-COZY-01–05 using actual physical native selection:
exact ordinary/package hrefs, titles, briefs and count two; every private suffix,
nested package, bare `_source` and partial; ordinary suffixes/unrecognized
directories; generic/glossary and empty/missing controls; and normal
`CozyBok.build` article/category fallback in SingleLocaleRoot JA and
MultiLocaleSubdirs JA/EN. Only external products/assets are emulated. These
renderer/network/SBT-free scenarios are regression authoring, not real-site proof.

The package's direct index is its public document input. Direct and arbitrarily
nested `.md` and `.markdown` notes remain private, even with valid published
YAML and unique titles/content. A private note with a bare `_source` key must
not be parsed as public content or require an authoring workaround.

Generated HTML, Antora pages, public document/RDF/bibliography metadata and
localized notice YAML used as card inputs must expose the selected public
index and exclude the private notes' titles, content and source/public URLs.
The exclusion follows SmartDox admission rather than a name-specific exception
for a `review/` directory. Integration evidence observes public products;
saved-source byte comparisons, hashes and content-copy certificates are outside
this contract. Notice/card-input evidence does not establish the later Cozy
article-card media rendering outcome.

## Admitted Markdown grammar

Ordinary admitted `.md` and `.markdown` sources use actual Markdown grammar
after admission. Published status and bilingual title come from YAML front
matter. Markdown strong/emphasis constructs render with their proper HTML
semantics. Literal body `HEAD`, `status=draft`, `include::missing[]` and
`site:[missing.dox]` remain text: they neither change published admission nor
fetch the missing include. Production-strategy JA/EN HTML and actual Antora
pages retain those literals and the YAML-selected titles at same-stem routes.

## Localized aliases and real fragments

Incoming ordinary Dox links and admitted Markdown hyperlinks may address a
package's physical direct source or its physical HTML alias. The public
generators must resolve both to the flattened canonical package page, project
the target's exact JA/EN title, and preserve the authored fragment. The target
HTML must contain the actual fragment identifier; a canonical-looking href
alone is insufficient evidence. The package's link to its ordinary sibling
retains the established sibling route and bilingual caption.

Markdown hyperlink syntax remains Markdown. Literal Dox `site:[...]` body text
is not an alias-link workaround. Fragment-preserving ordinary Markdown
assertions are part of the consumer integration gate and require actual
execution against the accepted dependency before a positive result is claimed.

## Executable specification and evidence boundary

[CozyDocumentProjectSiteProjectionSpec](../../src/test/scala/cozy/publication/CozyDocumentProjectSiteProjectionSpec.scala)
authors exactly four observable scenarios:

| Scenario | Observable contract |
| --- | --- |
| P751-02A-SPEC-01 | Native physical package/ordinary includes, JA/EN titles, canonical HTML and Antora paths, no alternate index page. |
| P751-02A-SPEC-02 | Direct/deep private Markdown and bare `_source` exclusion from HTML, Antora and public notice/card metadata, with positive public-index controls. |
| P751-02A-SPEC-03 | Published bilingual YAML, genuine Markdown emphasis, literal Dox body directives, Production HTML and Antora routes for both suffixes. |
| P751-02A-SPEC-04 | Incoming ordinary/Markdown direct-source and HTML aliases, localized canonical hrefs, real preserved target fragment and sibling compatibility. |

Each scenario has adjacent Given/When/Then clauses and `should` matchers.
Fresh physical fixtures and their cleanup are bounded to the specification's
unique directories under `target/cozy-document-project-site-projection`.

The Phase parent owns the local accepted dependency refresh, focused execution,
independent review and acceptance. Authorship is not validation. Generator
integration does not prove the complete `CozyBokBuild` metadata walk, article
cards, Arcadia page ownership, locale CSS, coordinated Kroki readiness or the
normal production `website.d`. Those consumers require their own actual gates,
including native build/browser evidence and the explicit local `/media/`
warehouse mapping in [Phase 75.1](../phase/phase-75.1.md).

## Native article-card rendering

Normal `CozyArticleMediaBuildContext.withContext` selects the available effective
publication before SmartDox generation. `DoxSiteGenerator` receives that actual
`publicationPath` and the configured warehouse. Its output is exported as a
physical `doxsite.d`, reopened with `Realm.create`, and consumed unchanged by
the pinned Arcadia `ArcadiaSiteGenerator` together with the authored site Realm.
Global home and category cards select actual generated Notice names by
`notice.uri`, never by an assumed ordering or a reconstructed binding map.

For both exact JA and EN variants, `headline`, `headline-wide` and
`headline-wide-reverse` must render the canonical localized article href/title
and the declared infographic, article PDF, summary-slide PDF and published
external YouTube hrefs. Canonical nonvideo URLs are
`/media/<locale>/<article-identity>/<role-file>`. Removing one physical role
before context construction omits exactly that locale/role; other roles and
the opposite locale survive. Legacy `_images` and another locale cannot supply
a missing role. An undeclared EN media variant leaves the EN article title and
route intact without JA fallback.

Published external YouTube links remain available when every nonvideo artifact
is absent and no MP4 exists. Draft and withdrawn external videos do not become
card links. An ordinary media-free article retains its public identity and has
no media wrapper. Declared PDF labels survive; absent labels use native card
defaults: JA `記事 PDF` / `まとめスライド PDF`, EN `Article PDF` /
`Summary slides PDF`. Infographic/video captions are JA
`インフォグラフィックを見る` / `動画を見る` and EN `View infographic` /
`Watch video`. Rendered cards contain no `N/A`, literal `${...}`, or unresolved
`c:widget` / `c:optional` tags.

The Document Project's selected public index and a second ordinary public
article are positive rendering controls. Direct and deeply nested private
Markdown titles, content and source/public URIs must not become cards or
public article output through either generator.

[CozyArticleCardRenderingSpec](../../src/test/scala/cozy/publication/CozyArticleCardRenderingSpec.scala)
authors six scenarios P751-02C-01–06 for these contracts. The three classpath
widget fixtures originate from the current SimpleModeling site widgets and
are normal behavioral inputs. Each native rendered widget has a fixture-owned
section so expectations observe its actual article/media anchors, classes and
captions independently. Fixtures, exported products and Arcadia `tmp.directory`
remain in one fresh `target/cozy-article-card-rendering-spec/fixture-*` tree.
These generator scenarios require actual parent execution and independent
review; they do not certify the live SimpleModeling templates or replace the
normal `website.d` native build and JA/EN browser acceptance.
