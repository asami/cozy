# Phase 75: Effective SmartDox Content Boundary and Kroki Site-Port Alignment

status=planned
execution_priority=production-site-build-correctness
entry_condition=explicit_owner_start
primary_owner=Cozy
producer_dependency=SmartDox effective-content contract

Planned at: 2026-09-28
Development item: not started

Renumbered from the local, uncommitted Phase 74 plan on 2026-09-30 to avoid
colliding with the independently closed Phase 74 Abstract UI Runtime Contract
on `origin/main`. Historical Phase 54.1 closure references to concurrent
"planned Phase 74" refer to this site-build plan, now Phase 75. The closed
Abstract UI Phase 74 remains authoritative and unchanged.

## Purpose

Make the production site pipeline distinguish a public SmartDox document from
other files that happen to live in a Document Project.  A `<slug>.dox/`
directory has a fixed, declared structure; public-site input must be selected
from that structure, not discovered by recursively scanning every file below
the directory.  Thus an arbitrary Markdown file, including a review note, is
not public SmartDox content, a LinkCollection source, or an Antora page input.

The same Phase incorporates the already isolated Cozy-side Kroki correction:
Docker Antora must start the SmartDox Kroki service on its configured internal
port (`8000`), rather than the obsolete `9609` value.  Both changes serve the
same production-site boundary, but neither is an authorization to publish a
site.

This Phase also tracks two production-site regressions reported on
2026-09-28: locale-local CSS assets are not correctly referenced, and existing
Arcadia pages are replaced by generated BoK Dashboards.  Restoring the
established site presentation is required; a successful build exit alone does
not establish compatibility.

## Problem statement

A Document Project may physically store its public article at:

```text
<directory>/<slug>.dox/index.dox
```

Its logical source identity is:

```text
<directory>/<slug>.dox
```

and its canonical public page remains:

```text
<directory>/<slug>.html
```

Consumers must not each reconstruct this mapping from a physical path.
SmartDox must expose one effective-content/effective-document access path;
LinkCollection, Antora projection, route metadata, and site-link resolution
must consume that access path.  For a Document Project package, the accessor
selects only declared structural inputs—its public article is `index.dox`—and
does not recursively discover arbitrary descendants.  Consequently, an
unselected Markdown file containing a bare `_source` key must neither be
parsed as SmartDox content nor require an authoring-format change merely for
site production to succeed.

The effective-content boundary must also preserve the established include
contract.  The existing include resolver receives a logical, site-relative
reference and a correctly resolved physical base file.  A new Document Project
route must select `index.dox` first and pass that base onward; it must not send
an absolute filesystem path through a site-relative URI normalizer.  This is a
bounded adapter correction, not a new global path-validation, hashing, or type
framework.  Existing ordinary-document include behavior is a compatibility
constraint, not an input to be reinterpreted.

When a participating public input has a `.md` suffix, it is Markdown input:
SmartDox must parse it with the Markdown parser first and transform the
resulting Markdown AST into the shared document representation.  It must not
run SmartDox/Dox-specific syntax directly over Markdown source.  Parser
selection occurs only after the fixed package structure or another explicit
site-source declaration has admitted the file; arbitrary Markdown descendants
are not parser candidates.

Separately, Cozy's `CozyBokSiteBuild` currently supplies
`SMARTDOX_KROKI_PORT=9609` to the Docker Antora container even though the
SmartDox Antora-side Kroki service uses port `8000`.  The accepted Cozy
adapter value is `8000`.

### Locale-local CSS asset references

The multi-locale Antora output stores its UI assets under each locale, for
example `website.d/ja/_/css/`.  Generated locale home and nested pages must
resolve their CSS links against that locale's asset root, not the canonical
website root.  The reported failure at `http://localhost:8085/ja/index.html`
included links to missing `/_/css/` assets.  Track this independently from
page-content selection: fixing CSS does not prove that the intended page is
being displayed.  Single-locale output must retain its existing asset paths.

### Existing Arcadia pages replaced by BoK Dashboards

The reported `/index.html` remains the expected Arcadia page, while locale
home pages display a different BoK Dashboard.  The current Cozy build copies
Arcadia output into the website and then calls `_write_bok_pages`, which writes
locale home and category indexes over the existing pages.  Phase 75 must
restore the established Arcadia page ownership at those public URLs while
retaining BoK-generated pages where no Arcadia page owns the destination.
Verification must compare the intended JA/EN home and category pages, not
merely confirm that styled HTML or a build-completion marker exists.

### Article-card media links

On 2026-09-28, the user reported that article cards do not correctly display
links to the article PDF, summary-slide PDF, and video.  Track this as an open
Phase 75 issue; the cause is not yet established.  Verify the declared media
information reaches the card and that the rendered links lead to the intended
article's available media, including the appropriate locale.

## Ownership and dependency boundary

### SmartDox producer contract

SmartDox owns the effective-document abstraction, the declared Document
Project package inventory, and the rule that maps a package's `index.dox` to
its logical `.dox` identity.  It also owns suffix-directed source parsing. The
implementation must centralize this behind an effective-content accessor (for
example, `effectiveContent` or `getDox`); it must not duplicate package-path
special cases in LinkCollection or Antora.  That accessor must preserve source
kind: an explicitly admitted `.md` uses Markdown parsing followed by AST
transformation, while `.dox` uses the SmartDox/Dox parser.  It must not turn a
recursive package-directory scan into an implicit source declaration.

### Cozy consumer/adapter contract

Cozy consumes the resulting SmartDox site behavior and owns its Docker Antora
launch adapter.  The candidate change already present only in the separate
Cozy source tree is limited to:

- `src/main/scala/cozy/bok/CozyBokSiteBuild.scala` — pass
  `SMARTDOX_KROKI_PORT=8000` to Docker Antora; and
- `src/test/scala/cozy/CozyBokSpec.scala` — assert the same launch contract.

The CSS-reference repair belongs to Cozy's site UI asset-link generation
(`CozyBokDashboardCore`); the Arcadia overwrite repair belongs to Cozy's site
composition/page-generation boundary (`CozyBokBuild` and `CozyBokSitePages`).
Freeze the exact admitted files and focused specifications at Phase entry;
existing isolated candidates are not acceptance evidence by themselves.

No candidate is accepted, merged, committed, or validated by this planning
record.  Implementation work begins only when this Phase is explicitly
started.

## Work outline

| ID | Outcome | Status |
| --- | --- | --- |
| SDX-75-01 | Freeze and implement one SmartDox effective-document/content contract for ordinary Dox files and `<slug>.dox/index.dox` packages. | planned |
| SDX-75-02 | Route LinkCollection, Antora input/projection, public route metadata, and site-link resolution through that contract; enumerate only declared `<slug>.dox/` package inputs rather than recursively discovering files. | planned |
| SDX-75-03 | Parse participating `.md` sources as Markdown and transform their AST; never apply SmartDox/Dox-specific syntax directly to Markdown bytes. | planned |
| SDX-75-04 | Preserve the legacy include resolver contract by adapting a Document Project to its effective physical base before link/include resolution; add a focused absolute-base/relative-include regression. | planned |
| COZY-75-04 | Adopt the exact SmartDox contract in Cozy integration evidence without adding a duplicate path mapper or a review-note parser. | planned |
| COZY-75-05 | Apply and validate the isolated Docker Antora Kroki-port adapter correction (`8000`). | planned |
| COZY-75-06 | Restore CSS asset references for locale home and nested pages against each locale's Antora asset root; preserve single-locale behavior. | planned |
| COZY-75-07 | Preserve existing Arcadia home and category pages instead of replacing them with BoK Dashboards; retain default BoK behavior for sites without an Arcadia-owned page. | planned |
| COZY-75-08 | Restore correct article-card links to the article PDF, summary-slide PDF, and video. | planned |

## Acceptance

- An ordinary `foo.dox` and a Document Project
  `foo.dox/index.dox` both resolve through one effective-document access path;
  the latter has logical identity `foo.dox` and public identity `foo.html`.
- LinkCollection, Antora generation, public-route metadata, and site-link
  resolution receive the effective document rather than inspecting package
  layout themselves.
- An arbitrary descendant of `<slug>.dox/`, including Markdown under
  `review/`, is not implicitly public content.  A bare `_source` key in that
  unselected file does not affect SmartDox parsing, link collection, Antora
  generation, or public-site output.
- A participating `.md` source is explicitly admitted by the fixed package
  structure or a site-source declaration, then parsed as Markdown before any
  SmartDox document transformation.  SmartDox/Dox-specific syntax is never
  interpreted directly from Markdown bytes.
- An ordinary source file and a Document Project source with an absolute
  physical base both resolve their relative includes as before.  The Document
  Project adapter chooses its effective `index.dox` base without passing that
  absolute path to a site-relative URI normalizer; no leading path separator is
  removed during resolution.
- Regression evidence covers an ordinary document, a packaged Document
  Project, a relative include from an absolute physical base, and a project
  containing a review note that is intentionally not public content.  The
  evidence verifies links, include resolution, and canonical package output
  paths as well as successful parsing.
- The Docker Antora invocation constructed by Cozy passes
  `SMARTDOX_KROKI_PORT=8000` and never emits the obsolete `9609` value.
- JA/EN home and nested pages resolve their local stylesheets to real assets
  under the corresponding locale root and load them successfully over HTTP.
  Focused evidence also preserves single-locale asset resolution.
- With Arcadia enabled, the established root, JA/EN home, and Arcadia-owned
  category pages retain their intended content and presentation.  They are not
  overwritten by BoK Dashboards.  Sites without an Arcadia-owned destination
  retain the existing generated BoK behavior.
- Browser verification checks the intended page identity and visible layout
  against the established Arcadia output.  Neither CSS loading alone nor a
  successful production command is sufficient evidence.
- Article cards display the declared, available article PDF, summary-slide
  PDF, and video links correctly.  JA/EN browser evidence verifies the labels
  and destinations refer to the intended article and locale and that the
  linked resources can be opened; unavailable media must not produce broken
  links.
- Focused producer and Cozy consumer evidence uses the same effective-content
  contract.  A production-site build may be used as a downstream acceptance
  driver only after those checks; it is not publication authority.

## Non-goals

- Editing an unselected package descendant, including a review note, or
  changing its Markdown/Dox syntax to work around a public-content enumeration
  defect.
- A Cozy-side duplicate implementation of SmartDox route, package, link, or
  effective-content resolution.
- A global path type system, all-input validation pass, hash/manifest gate, or
  broad resolver rewrite introduced only to prevent this bounded adapter
  misuse.  The targeted regression fixture is the required protection.
- Manual rewriting of generated HTML paths, redirects, or public links.
- Media prebuilt-currentness, receipt-hash, or retained-output policy changes;
  those are separate production-workflow concerns.
- Publishing, deploying, uploading, or modifying generated
  SimpleModeling.org website content.

## Implementation sequence

1. Freeze the SmartDox effective-content API, fixed package-input inventory,
   and Markdown-source parsing behavior with focused producer specifications.
2. Update each SmartDox site consumer to use that API, with no physical-path
   conditionals outside the producer abstraction and no Dox parser applied to
   Markdown source.
3. Add the focused include-compatibility regression: an absolute physical base
   and relative include must resolve through the effective source without
   changing the established resolver contract.
4. Refresh the exact local SmartDox development dependency and run Cozy's
   focused consumer evidence.
5. Apply the bounded Cozy Kroki-port candidate from the separate source tree
   and run its focused launch-contract specification.
6. Verify locale-local CSS resolution and Arcadia-owned page preservation with
   focused regressions, including single-locale and non-Arcadia controls.
7. Run downstream local site-build and browser verification without publishing, then
   conduct the required review and release workflow in a separately authorized
   execution of this Phase.

## References

- [Phase 75 checklist](phase-75-checklist.md)
- [SmartDox PDF site-link FQN handoff](../journal/2026/09/2026-09-07-smartdox-pdf-site-link-fqn-handoff.md)
- [Document Project public URL normalization handoff](../journal/2026/09/2026-09-08-document-project-public-url-normalization-handoff.md)
- [Phase 71](phase-71.md) — adjacent media work; its currentness policy is
  explicitly outside this Phase.
