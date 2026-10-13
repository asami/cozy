# BoK site toolchain contract

Scope: internal site producer and asset handoff for [BOK-UC-07: Build the local generated
site](../design/bok-use-cases.md#bok-uc-07-build-the-local-generated-site).


## 2026-10-09 S22 BoK upload and UI ZIP direct output amendment

P715-02-S22-BOK-UPLOAD-BACKUP-UI-ZIP-DIRECT is
**implemented-focused-validated-unreviewed** under the [frozen S22 manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s22-implementation-manifest.json).
The initial editing handoff on 2026-10-09 was
**implemented-unvalidated-unreviewed**, with focused validation and independent
review pending. Focused validation is now recorded below; independent review and
acceptance remain OPEN. This amendment supersedes generated website backup configuration,
models, helpers, aliases, scaffold/help promises and protection-only scenarios,
and temporary final UI ZIP replacement promises. Original dated text and results
remain history.

The [workflow implementation](../../src/main/scala/cozy/bok/CozyBokBuild.scala) delegates configured stage/upload commands once
with their ordinary environment and project working directory; missing commands
and actual runner/I/O failures remain failures. The [workflow configuration](../../src/main/scala/cozy/bok/CozyBokBuildConfig.scala)
contains project, name, command and environment without a backup model or legacy
backup decoder. Generic YAML loading remains generic. The [UI ZIP writer](../../src/main/scala/cozy/bok/CozyBokSiteBuild.scala)
reads the configured/default source and writes the selected final ZIP directly,
closing input even if output opening fails and closing output before input.
Source entry order, directory skipping, the same twelve Cozy asset replacements,
supplemental header, standard ZIP format/CRC, toolchain and both locale layouts
retain their meanings. I/O/resource errors propagate and may leave a partial
final ZIP; recovery is regeneration.

Bibliography cache, target-contained SIE handoffs and default UI existence reuse
remain generation-input reuse. Authored-source backups and Site Administrator
monitoring/backups/incident response remain operator responsibilities.
The [existing CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala) retains ordinary workflow, UI content/default/missing
input, locale, path, format and failure contracts; four protection-only backup
cases were deleted, and existing scaffold/help expectations were narrowed.
See the [toolchain specification](bok-site-toolchain.md) and [toolchain design](../design/bok-site-toolchain.md) for retained site ownership.

Other finite removals and external E715-01..04, final current JA34 generation,
audible hearing/end-card/EOF, independent M2 cycles 6/7/8/10, separate Step and
full-Phase review/acceptance, exact commits and release remain OPEN.
Repository-full SBT and broader integration remain deferred-not-run to
PHASE-71.7. No Stage Status triple, closure checkbox, aggregate metadata or old
result is changed by this amendment.

### Actual S22 focused validation — 2026-10-09

The [actual S22 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s22-focused-validation-result.json), following the [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s22-parent-edit-admission.json), records
142 successes in 6 completed suites. Two sequential groups passed: 61 tests in
`cozy.bok.CozyBokSpec`, then 81 tests in the five existing Bibliography,
SiteRouting, BuildTarget, MetadataFinalization and InfographicCommand suites.
Failed, canceled, ignored, pending and aborted counts are zero. SBT and wrapper
exited 0/0, and the shared serial lock was released through the registered route.
Compiler warnings, errors, fixture diagnostics and runtime Logback warning lines
are zero for this invocation. Earlier `HYG-P715-S19-LOGBACK-001` remains pending
independent Step classification; no suppression or whole-Phase warning-free
claim is made. Prior S21 131-test/six-suite results and refusal/successor history
remain attributed to S21.

These existing fixture/consumer results cover affected site/upload/UI behavior.
They establish no live upload, publication/deployment or current native/audible
acceptance. Prior S14 JA34 evidence remains dated; final current generation,
assembly, hearing, end-card/EOF and whole-Phase main-route relevance remain OPEN,
as do the finite/external, M2 and separate Step/full-Phase assurance, commit and
release obligations above. Repository-full SBT and broader integration remain
deferred-not-run to PHASE-71.7.


Every Docker Antora command emitted by the normal `CozyBok.build` route supplies
both `-e SMARTDOX_KROKI_PORT=8000` and `-e KROKI_LISTEN=127.0.0.1:8000`.
The generated SmartDox Antora playbook sets
`asciidoc.attributes.kroki-server-url` to `http://127.0.0.1:8000` in both
single-locale root and JA/EN multiple-locale layouts. The existing toolchain
wrapper uses `SMARTDOX_KROKI_PORT` for Java launch and readiness, so the
generated endpoint, wrapper and Kroki listener select the same container-local
port, and the literal IPv4 client address matches the listener's address family.
`KROKI_LISTEN` uses the
[official Kroki listener configuration](https://docs.kroki.io/kroki/setup/configuration/).
The listener is internal to the container; this contract publishes no host port.

The configured Textus toolchain image, SmartDox host-side image environment,
producer and project roots, language selection, working directories and output
routes retain their existing contracts. This handoff adds no public CLI,
schema, configuration option or server lifecycle. Generated site resources
retain the [generated BoK knowledge boundary](../design/bok-generated-knowledge-boundary.md).

Executable specification: [CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala),
`pass the configured Textus toolchain image to SmartDox Kroki execution`, invokes
the real build orchestration and checks every emitted Docker Antora command.
The scenario covers both layouts, one or two Antora commands respectively,
and retains the host-side image contract. Recorded argv proves the handoff
contract. SmartDox's
[AntoraKrokiEndpointSpec](../../../smartdox/src/test/scala/org/smartdox/generators/AntoraKrokiEndpointSpec.scala)
parses actual generated root and locale playbooks to specify the
[producer endpoint contract](../../../smartdox/docs/spec/antora-kroki-endpoint.md).
Neither proof establishes actual listener startup or complete site generation;
those still require the unchanged native entry's successful execution.

## Arcadia inputs and output ownership

After SmartDox site generation and the existing metadata handoffs,
`CozyBok.build` invokes
`arcadia site <bok.arcadia.source> <bok.doxsite>` in the project directory.
Both positional directories are inputs: the authored Arcadia templates and
the generated Dox site, including `WEB-INF/data/<language>/notice01.yaml`.
The configured `bok.arcadia-site` destination SHALL NOT be passed as an input.

The current native producer writes `arcadiasite.d` under its working directory.
Cozy clears that producer-owned generated directory before invocation when the
configured destination differs, requires the successful producer directory,
and copies its contents to `bok.arcadia-site`, followed by the existing copy to
`bok.website`. The default destination reuses the produced directory directly.
No directory is copied onto itself in destination normalization. Template and
Dox input directories remain inputs.

A process reporting success without the declared producer directory SHALL fail
with an actionable `invalidArgumentFault` naming the output and inputs. The
[home ownership contract](bok-site-home-ownership.md) continues to select the
sole home writer; missing output SHALL NOT fabricate a Dashboard home.
Arcadia-disabled builds retain their established Dashboard route.

## Shared CSS routing

Shared CSS hrefs resolve from the actual page parent to these three files:
`_/css/bootstrap-grid.min.css`, `_/css/site.css` and
`_/css/cozy-bok-dashboard.css`.

- `SingleLocaleRoot` uses the configured website root for assets.
- `MultiLocaleSubdirs` uses the configured website root plus the first relative
  page component when that component is a configured language.
- Canonical root pages use root assets in either layout.

The layout selects the asset root deterministically, without inspecting asset
existence. Canonical machine metadata and global publication links retain their
website-root semantics. Single-locale auxiliary JA/EN glossary indexes retain
their existing `../../_/css/...` links to root assets. Multiple-locale builds
do not generate nested `ja/ja` or `en/en` auxiliary indexes.

[CozyBokSiteRoutingSpec](../../src/test/scala/cozy/bok/CozyBokSiteRoutingSpec.scala)
in package `cozy.bok` specifies real build orchestration with external process
output emulated: ordered input argv, notice titles and valid article URI,
default/custom output destinations, disabled and missing-output behavior,
build-generated category and auxiliary glossary CSS, and at least fifty
generated routing trials across layouts, languages, depths and website paths.
The amended `cozy.bok.CozyBokSpec` preserves its fifty-trial UTF-8 home contract.
These specifications establish process handoff and Cozy page behavior only.
Native Arcadia rendering and complete combined-tree/article-only/video-only site
acceptance require later parent-owned execution on task-owned copies.

## Selected native prerequisite

[CozyBokNativeSiteDriver](../../src/test/scala/cozy/bok/CozyBokNativeSiteDriver.scala)
is a standalone Test main, selected explicitly after the current normal
SmartDox `exportClasspath` and Cozy `cozyExportRuntimeClasspath` tasks:

```text
Test / runMain cozy.bok.CozyBokNativeSiteDriver /Users/asami/src/dev2025/cozy/target/phase751-native-site-repair12-20261007 /Users/asami/src/dev2025/smartdox/target/classpath.txt /Users/asami/src/dev2025/cozy/target/cozy.d/runtime-classpath.txt ghcr.io/asami/textus-toolchain:latest
```

Its four literal arguments are a nonexistent absolute workspace, the canonical
SmartDox exported classpath file, the current Cozy Runtime exported classpath
file and the unchanged installed image. It creates only two fresh proof projects
under that workspace. The constructor-parameterized private executable
specification runs P751-02F-NATIVE-01 (`SingleLocaleRoot`, JA) and
P751-02F-NATIVE-02 (`MultiLocaleSubdirs`, JA/EN) through actual
`BuildConfig.create` and `CozyBok.build`. Normal full-suite discovery adds no
Docker prerequisite.

Each case requires the parsed generated `http://127.0.0.1:8000` endpoint,
normal emitted Docker port/image/output arguments and successful real Antora
pages/assets. Canonical SmartDox diagram evidence is the complete inline SVG
on the actual published `development-process/ordinary.html` page. Each fragment
is parsed as XML with external entities, DOCTYPE, XInclude and entity expansion
disabled; its root must be `svg` in `http://www.w3.org/2000/svg`. Parsed diagram
text must contain the distinctive case label, Alice and Bob without error,
exception, failed-to-generate or fallback output. Source/prose labels, malformed
XML, an invalid namespace and cache or arbitrary asset files supply no proof.

Separately, before every Antora invocation the driver authors only a new raw
PlantUML endpoint page in that invocation's fresh generated
`antora/docs/development-process/modules/ROOT/pages/p751-kroki-endpoint.adoc`.
Its per-invocation label is the case label plus `_ANTORA_ENDPOINT_` and a UUID.
The `[plantuml,p751-endpoint,svg,opts=inline]` block selects inline output only;
the existing generated playbook still owns `http://127.0.0.1:8000`, SVG format
and diagram fetching through the unchanged image and paired port 8000.
After actual Antora success, the independently published
`development-process/p751-kroki-endpoint.html` must contain a securely parsed
inline SVG with that exact allocated label, Alice and Bob and the same failure
exclusions. Ordinary SmartDox rendering cannot satisfy this endpoint proof.

The unchanged `BuildConfig.create` selects HomeOnly. Each case SHALL require
`siteOutputScopePolicy` to be `home_only`, exactly one actual emitted `dox site`
command and its adjacent `-output.scope.policy home_only` argv pair. For every
locale, nested ordinary/package HTML SHALL be absent from intermediate
`doxsite.d`. Antora owns their final `website.d` pages: the exact ordinary page
must retain its ordinary body, and the exact package page must retain both its
public package body and relative include body. Actual exported global and
category Notice data under `doxsite.d/WEB-INF/data` SHALL contain both canonical
`development-process/ordinary.html` and `development-process/package.html`
URI keys for every locale. Private package Markdown must remain absent.
Actual exported Notice identities feed all three classpath
widgets in authored home/category inputs. The resulting native Arcadia pages
must equal the copied website pages byte-for-byte, contain the public links,
and coexist with locale-correct shared CSS targets on actual generated pages.

Every child uses argv arrays, preserved cwd/environment and a maximum
300-second lifetime; the entire run has an 1800-second bound including owned
cleanup. Only an allocated case-specific Docker container name is added to the
normal Antora argv. No host port, endpoint rewrite or global launcher/PATH
change is introduced. Products, stdout/stderr, emitted/effective argv,
classpath and installed-image provenance, separate canonical/endpoint diagram
products with owning page paths, endpoint source/label/playbook attribution,
generated-fixture Git preparation,
process status and cleanup outcomes remain in the fresh workspace. Failed,
aborted, canceled, ignored, pending or fewer than two successful scenarios,
nonzero native exits, missing/invalid products, timeout and uncertain cleanup
all fail the main; console output alone cannot establish success.

Generated docs and page parents must belong canonically to the new workspace,
and the endpoint source must be absent before creation. If SmartDox already
created a generated-docs repository, the driver registers and commits only the
new endpoint path with the explicit fixture author/committer. Otherwise it
retains fresh initialization and a fixture commit of all generated docs. The
actual repository path, registered endpoint path and fixture commit are
recorded. This isolated fixture provenance is neither a source-checkout commit
nor Step acceptance evidence.

This controlled prerequisite does not certify the normal SimpleModeling
`etc/runweb-production.sh` entry or live production template identities.
Prepared-media placement/Web-only script separation, repeated normal
`website.d` builds, explicit local `/media/` mapping, JA/EN browser evidence,
independent final full Cozy and changed SmartDox suites, Step acceptance and
the final Phase review/release remain separately required.

## Native repair evidence projection (2026-10-07)

- Owner: Phase 75.1 parent.
- Update rule: replace pending status only with actual fresh native validation
  and independent review; preserve earlier failed execution and products.
- Status: FIX8/9 compile repairs are complete. FIX10 compiled, and its actual
  SingleJA native SmartDox/Antora execution and secure canonical inline-SVG
  oracle passed, but the separate endpoint page contained `kroki-error`:
  Antora resolved `http://localhost:8000` to IPv6 `::1:8000` and received
  `ECONNREFUSED` while the listener selected IPv4 `127.0.0.1:8000`.
  Owned process/container cleanup completed; no native acceptance followed.
  FIX11's literal IPv4 repair passed the actual producer accumulator 21/21
  (three preexisting ignores recorded separately), typed same-coordinate
  `org.smartdox:smartdox_2.12:2.4.19-SNAPSHOT` local refresh and Cozy accumulator
  140/140. Both native cases failed at the incorrect intermediate HomeOnly
  article-body oracle despite fresh endpoint SVG in all three locale trees
  and complete owned cleanup. Those partial products accept neither case.
  Repair12 corrects only the private HomeOnly/final-website oracle and these
  four English projections. Fresh both-case execution and independent review
  remain pending; no incidental cache/hash behavior is changed.
- Evidence: `native-interpolation8-compile-failure-20261007.json` and
  `native-locale9-product-shape-failure-20261007.json` and
  `native-oracle10-endpoint-failure-20261007.json` and
  `native-address11-public-html-failure-20261007.json` under
  `.codex-workflow/phases/PHASE-75.1/` retain the compile/failure lineage.
  Old products at `target/phase751-native-site-20261007` and
  `target/phase751-native-site-repair10-20261007` and
  `target/phase751-native-site-repair11-20261007` remain retained;
  the selected repair12 workspace above must be absent and new. No native Slice, whole
  Step or Phase acceptance follows from this authorship.
