# BoK site toolchain coordination

Scope: the existing internal site producer and asset handoff supporting
[BOK-UC-07](bok-use-cases.md#bok-uc-07-build-the-local-generated-site).
The [toolchain specification](../spec/bok-site-toolchain.md) fixes its contract.

`CozyBokSiteBuild._docker_antora` selects
`SMARTDOX_KROKI_PORT=8000` for the existing wrapper's Java launch and readiness.
The documented `KROKI_LISTEN=127.0.0.1:8000` beside that setting selects the
actual Kroki listener. SmartDox's stable
`Antora.Playbook.Asciidoc.defaultAttributes` owner supplies
`http://127.0.0.1:8000` to generated root and locale playbooks. The literal IPv4
client address matches the listener's address family, and all three settings
coordinate port 8000 on the same internal endpoint. This uses the
[official Kroki configuration](https://docs.kroki.io/kroki/setup/configuration/)
without changing the selected toolchain image or introducing a host listener.

Both single-locale and multiple-locale orchestration continue using this same
command producer. Producer/project roots, language routing, working directories,
output destinations and SmartDox host-side image settings stay under their
existing owners. The [generated-source boundary](bok-generated-knowledge-boundary.md)
continues to distinguish durable inputs from generated Antora/site resources.
No new helper, public API, schema, configuration option or lifecycle is needed.

The existing [CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala) configured
toolchain scenario observes the real `CozyBok.build` to Runner boundary and
requires coordinated settings on every emitted Antora command for both layouts.
SmartDox's [generated-product specification](../../../smartdox/docs/spec/antora-kroki-endpoint.md)
and [endpoint suite](../../../smartdox/src/test/scala/org/smartdox/generators/AntoraKrokiEndpointSpec.scala)
observe the serialized attributes through the actual generator. Historically,
the paired `9609` handoff was a plausible explanation of
`S15-BASELINE-KROKI-001`; neither that attribution nor the fixed `8000` handoff
establishes the sole native cause or successful server startup. Parent-owned
focused validation and the unchanged complete native site entry must supply
their separate evidence. Cache paths, public APIs and output layout remain
under their existing owners.

## Source and output responsibilities

Arcadia's existing `SiteCommand.params.in` accepts multiple input directories.
Its generator merges the resulting realms and exports the site under the fixed
`arcadiasite.d` directory in the project working directory. Although the command
parses an output option, the current producer does not use it for this export.
Cozy therefore supplies authored templates and the actual generated Dox site as
the two inputs, after the existing metadata preparation. Notice data stays under
the Dox producer's `WEB-INF/data/<language>` ownership.

`bok.arcadia-site` remains Cozy's destination setting. Cozy normalizes the fixed
producer output into that destination when the paths differ, then retains the
existing website copy. Clearing the fixed generated directory before the call
prevents stale output from satisfying the missing-output requirement in a
custom-destination build. Default output needs no intermediate copy. A missing
declared output is a producer contract violation reported through the existing
`invalidArgumentFault`; the configured home owner remains authoritative.
This introduces no public configuration or external producer change.

## Locale asset responsibility

Antora places multiple-locale CSS under `bok.website/<language>/_/css`.
Cozy's shared CSS helper uses the original configuration and actual page path,
selects the asset root from the declared layout and first relative language
component, and delegates relative path calculation to `_relative_href`.
Canonical pages keep website-root assets.

The locale asset root is independent of `_site_root_prefix`, which continues to
govern canonical machine metadata and global publication links. Changing the
global website configuration for a locale would move those other ownership
boundaries; CSS routing instead chooses only its destination path.

The auxiliary localized glossary writer runs only for `SingleLocaleRoot` at
`<website>/ja|en/glossary/index.html`. Its fixed `../../` links already reach
the root assets and need no change. `MultiLocaleSubdirs` disables that auxiliary
writer and produces each language's normal glossary under its locale root.

The [routing executable specification](../../src/test/scala/cozy/bok/CozyBokSiteRoutingSpec.scala)
emulates only external process outputs while exercising actual Cozy build and
page generation. Its randomized shared-CSS observations resolve emitted hrefs
against actual page parents and distinct existing asset roots. This evidence
remains distinct from native Arcadia rendering and complete-site acceptance.

## Explicit native source activation

The test-only [native driver](../../src/test/scala/cozy/bok/CozyBokNativeSiteDriver.scala)
selects the current normal SmartDox `target/classpath.txt` export and Cozy
`target/cozy.d/runtime-classpath.txt` Runtime export. The latter retains the
declared Arcadia 1.0.3 dependency. Both exports must exist before invocation;
the driver records their actual files and classpath entries. It introduces no
dependency or production launcher option.

Its private Runner translates only `dox` argv0 into
`java -cp <SmartDox export> org.smartdox.service.SmartDoxService` and `arcadia`
argv0 into `java -cp <Cozy Runtime export> arcadia.standalone.Standalone`.
The original operation, options, cwd and environment remain intact and both
emitted and effective argv are recorded. Docker executes the unchanged normal
Antora command with the selected installed
`ghcr.io/asami/textus-toolchain:latest` image, paired internal port 8000 and
normal output routes. Image inspection records the actual installed image;
this authorship claims no successful native listener observation. The driver
does not build or pull an image, rewrite an endpoint, expose a host port or
replace PATH/global wrappers.

The selected nonexistent workspace owns two fresh source projects and their
products. Public Dox, package-index/relative-include and private-Markdown
controls reuse existing source conventions. A distinctive fenced PlantUML
diagram yields canonical SmartDox inline SVG on the published ordinary page.
The driver extracts complete SVG elements from that known page and parses
their namespaced SVG roots and text using the existing secure XML restrictions.
It requires the case label and Alice/Bob, and rejects error/exception/
failed-to-generate/fallback text. A label in prose, source or cache is not a
rendered diagram product.

Canonical rendering is separate from endpoint fetch evidence. Immediately
before each real Antora invocation, the driver writes a new test-only
`development-process/modules/ROOT/pages/p751-kroki-endpoint.adoc` under that
invocation's generated Antora `docs` repository. This is raw PlantUML with an
allocated case-label-plus-`_ANTORA_ENDPOINT_`-plus-UUID label. The block's
`opts=inline` selects output placement; the unchanged generated playbook still
selects SVG, fetching and `http://127.0.0.1:8000` in the same image. The
separately published `development-process/p751-kroki-endpoint.html` must supply
its own securely parsed SVG bearing the exact allocated label and Alice/Bob
with every failure exclusion. Canonical SmartDox output cannot establish that
this Antora invocation contacted its configured endpoint.

The unchanged normal BuildConfig HomeOnly selection excludes nested document
leaves from intermediate `doxsite.d`, while Antora owns the final ordinary and
package `website.d` article pages. The private oracle requires `home_only` in
the configuration and exactly one actual emitted `dox site` command with the
adjacent `-output.scope.policy home_only` pair. For each locale it checks both
intermediate article paths are absent and reads the three existing body
expectations from their exact final ordinary/package pages. It separately
reads actual exported global and category Notice maps and requires canonical
`development-process/ordinary.html` and `development-process/package.html`
URI keys. This checks metadata ownership without fabricating Notice data or
changing output scope.

After actual SmartDox export,
the Runner prepares authored home/category templates from actual global and
category Notice names using the three existing classpath widget resources.
No Notice or resulting HTML is fabricated. Canonical workspace/docs/page-parent
guards and absent-source admission restrict endpoint authorship to fresh
generated Antora `docs`. JGit opens a producer-created repository and registers
and commits only that endpoint path with the existing explicit fixture
author/committer, or retains fresh initialization/add-all/fixture commit when
the repository is absent. Git location is confined to the canonical generated
repository. The actual fixture commit, registered source path, allocated label
and generated playbook attribution are recorded alongside separate canonical
and endpoint diagram products with their published owning pages. This is
fixture provenance, not an acceptance commit or unchanged-content certificate;
no source checkout is staged or committed.

The driver owns bounded Java Process/descendant handles and uniquely allocated
container names. Logs are file-backed, each child is bounded to 300 seconds,
and the whole run reserves cleanup time within 1800 seconds. Inspection,
removal and confirmation address only the allocated exact container; uncertain
native status or cleanup is terminal failure with retained evidence. The
explicitly invoked private constructor-parameterized AnyWordSpec reports both
behavior outcomes and makes incomplete/failed execution fail the main without
adding a native suite to ordinary full-test discovery.

These fixtures establish only the selected producer/native composition seam
after parent execution and review. Normal SimpleModeling source/template
identities, Web-only script/media separation, repeated normal site generation,
local `/media/`, browser checks, full-suite validation and Step/Phase release
remain their existing owners' required evidence.

## Native repair evidence projection (2026-10-07)

- Owner: Phase 75.1 parent.
- Update rule: qualify native success only from the selected fresh execution
  and independent review; retain failed old products and original obligations.
- Status: FIX8/9 removed the Scala compile failures. FIX10 compiled and its
  actual SingleJA run passed the secure canonical inline-SVG oracle after
  successful native SmartDox/Antora execution. The separate endpoint page
  contained `kroki-error`: Antora's `http://localhost:8000` request resolved to
  IPv6 `::1:8000` and failed with `ECONNREFUSED`, while the listener selected
  IPv4 `127.0.0.1:8000`. Owned process/container cleanup completed.
  FIX11's literal IPv4 repair passed actual producer 21/21 (three preexisting
  ignores separate), the typed same-coordinate
  `org.smartdox:smartdox_2.12:2.4.19-SNAPSHOT` local refresh and Cozy 140/140.
  Both real native cases failed at the incorrect intermediate HomeOnly article
  oracle after fresh endpoint SVG succeeded in all three locale trees and
  owned cleanup completed. These are partial results, not native acceptance.
  Repair12 changes only the private final-website/HomeOnly/Notice oracle and
  four English projections; fresh both-case execution and independent review
  remain pending. Incidental cache/hash behavior is unchanged.
- Evidence: `.codex-workflow/phases/PHASE-75.1/native-locale9-product-shape-failure-20261007.json`
  and `native-oracle10-endpoint-failure-20261007.json` and
  `native-address11-public-html-failure-20261007.json` retain the observed
  failures. The specification selects the nonexistent
  `target/phase751-native-site-repair12-20261007` workspace and literal
  `Test / runMain` command in the specification; prior
  `target/phase751-native-site-20261007` and
  `target/phase751-native-site-repair10-20261007` and
  `target/phase751-native-site-repair11-20261007` products remain history. Full suites,
  whole-Step/Phase review and exact acceptance/release commits remain required.
