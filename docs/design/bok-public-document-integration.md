# BoK public document integration design

Status: normative Phase 75.1 consumer design
Date: 2026-10-07

## Decision and responsibility

Consume the accepted SmartDox producer through existing public generators and
the unchanged dependency coordinate. The
[integration specification](../spec/bok-public-document-integration.md) defines
the behavior; SmartDox's [public-content design](../../../smartdox/docs/design/doxsite-public-content.md),
[physical-include design](../../../smartdox/docs/design/doxsite-physical-include-base.md)
and [Markdown design](../../../smartdox/docs/design/markdown-document-parsing.md)
retain producer ownership.

```text
Native physical sources + site.conf
              |
      DoxSite-configured Realm and origin
              |
      Public HTML / Antora generators
              |
      SmartDox sole effective-content owner
      (admission, grammar, identities and aliases)
              |
      Localized HTML, public metadata/notice inputs
      and effective Antora pages
```

Cozy does not interpret package descendants or reconstruct physical/logical
mapping. SmartDox selects the physical package index for parsing and relative
includes, retains the logical `.dox` package identity, and flattens the public
page. The same Realm origin supports ordinary physical include compatibility.
Source-kind selection follows admission, so Markdown uses its accepted grammar
while private package notes never become parser candidates.

P751-02A changes only a Cozy executable integration specification and its
documentation/status projection. No production API, parser, route mapper,
filter or build dependency is introduced. Both generators receive the real
physical Realm, and assertions inspect generated products. The fixture helpers
only arrange sources, create contexts, read output and clean up the fixture.

## Observable integration boundary

The four scenarios keep the producer's accepted concerns together at the Cozy
consumer boundary: physical includes and canonical paths; private closure and
public card inputs; admitted Markdown grammar; localized aliases with a real
fragment target. Full strategy characterizes the package/ordinary and private
closure contracts. Production strategy proves that published Markdown YAML
survives contradictory literal body status text. JA/EN output and actual
Antora page content are observed without launching Antora or any service.

Localized notice YAML and generated public metadata provide the private-note
exclusion evidence for card inputs. They do not exercise the entire Cozy
metadata walker or prove the final rendered article-card media links. Ordinary
Markdown links retain the manifest's fragment-preserving acceptance assertions;
no Dox macro substitution or test-only SmartDox vocabulary masks that gate.

## Pending downstream consumers

### Connected catalogue repair

The raw article walker in Cozy metadata is replaced by one whole-source native
Realm selection through the stable SmartDox structural facade. Producer Rule
and effective-content mapping own admission and physical/public identity;
Cozy only groups canonical category prefixes and reads selected physical files
through its existing metadata presentation helpers. The glossary walker remains
separate. No cache or independent source-selection state is introduced, so
category/article projections, dashboard/category counts and source-count
consumers naturally receive the same result.

The catalogue specification keeps ordinary/package positive controls beside
private exclusions. Normal build scenarios emulate external process output
only; source selection, configuration, metadata writers, counts and fallback
composition are real. Connected actual-site entry/browser acceptance, combined
focused regressions, independent review and release validation remain owned by
the Phase parent. This repair does not reopen generic SingleJA/layout expansion
deferred under DP-751-01.

| Consumer | Remaining evidence |
| --- | --- |
| `CozyBokBuild` metadata walk | Real whole-Cozy admission/projection assessment, including the existing walker. |
| Article cards | Declared article PDF, summary-slide PDF, infographic and YouTube links for the intended JA/EN article; browser destinations. |
| Arcadia composition | Established home/category page ownership, identity and presentation. |
| Locale CSS | Actual locale-local stylesheet URLs, single-locale control and HTTP/browser retrieval. |
| Kroki/Antora toolchain | Coordinated launch, readiness, listener and endpoint settings for port `8000`. |
| SimpleModeling.org production entry points | Separate prepared-media placement, repeated Web-only build and normal `website.d`, with explicit local `/media/` mapping. |

Enabled Arcadia owns home-page production even when its expected output is
missing. Cozy does not substitute a BoK home for that missing result. Existing
per-category fallback and dedicated knowledge-page production remain separate.

These consumers remain in P751-02 and the remaining Phase work. Article-card
links retain priority, followed by Arcadia ownership/CSS after evidenced
prerequisites. Generator evidence cannot close the Step or Phase. Preserve
Phase 75's accepted preservation, deployment, editions and gaps, and all
concurrent Phase 71 work. Missing media rendering, remote upload and repeated
accepted deployment are outside this integration Slice.

## Native card consumer fixture

P751-02C keeps the production contracts unchanged and exposes the complete
availability-to-card edge through pinned SmartDox `2.4.19-SNAPSHOT` and Arcadia
`1.0.3` public APIs. Physical bilingual published Dox sources and a normal
publication bundle enter `CozyArticleMediaBuildContext.withContext`; the leased
effective publication enters `DoxSiteGenerator`. Exporting its actual Realm
preserves raw Notice YAML for Arcadia's native DataSet and widget processing.
Only Notice identifiers are inspected by their actual `notice.uri` to choose
widget data names. No flattened Notice map is authored or altered.

The authored site Realm contains JA/EN home and category pages and exactly
three classpath card templates copied from the current SimpleModeling widget
inputs. Fixture-owned section identifiers associate each widget with its own
observed anchors. Native `ArcadiaContext.create` uses an explicit configuration
whose `tmp.directory` is inside the fresh fixture tree; `ArcadiaSiteGenerator`
receives the authored Realm and reopened exported `doxsite.d`. The fixture does
not mutate the process directory or shared Arcadia work directories. Resource
streams and cleanup walks close within the fixture lifetime.

The six scenarios cover complete bilingual roles; each exact missing
nonvideo role/locale; external video statuses and media-free controls; a
JA-only declaration with bilingual public identity; explicit/default PDF
labels and resolved tags; and private package closure with positive public
controls. Representative SVG/PDF bytes only establish readable available
fixture files. Media URLs retain canonical warehouse coordinates and no MP4
is required for external video.

Generator acceptance is a focused integration boundary. Live template
correctness, Arcadia page ownership, locale CSS, normal site construction,
explicit local `/media/` mapping and browser destinations remain separate
required Phase gates. P751-02C authorship closes none of those outcomes.
