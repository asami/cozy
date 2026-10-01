# Document Project Publication Preparation Specification

## Status and authority

Phase 57.3 P573-01 specifies this native client boundary. P573-02 delivered the
API, media command and bounded executable proof described below and was accepted
locally with 41 passing tests, an independent PASS review, and Step commit
`8d06d7c`. P573-03 adds the seven native sequence/failure scenario families and
canonical skill guidance described below; those additions are authored pending
validation and review. Neither Step establishes Phase closure or proves that
the actually invoked installed Cozy exposes the preparation command. Clients
check that capability through native help or command resolution; if absent,
they report `BLOCKED: missing preparation-client capability` without a fallback.

The unchanged [Phase 57.2 target specification](document-project-publication-target.md)
owns portable admission, original media/site binding, live currentness, and
snapshot revalidation. [Document Project](document-project.md) owns native
run, structural verification, and public export. Preparation consumes these
authorities; it introduces no provider, evidence schema, target, registry
semantics, or production-site authority.

## Native inputs and command sequence

The API is exactly
`CozyDocumentProjectPublicationPreparation.Config(registration: CurrentRegistrationConfig,
taskRoot: Path, destination: Path)` with `prepare(Config): Prepared`.
`CurrentRegistrationConfig` retains the live project and `RegistrationConfig`
containing the verified public bundle and original
`CozyArticleMediaSiteBinding.Config`. The thin CLI parses into these unchanged
producer inputs:

```text
cozy document-project run <canonical.dox> --operation article.render-review
cozy document-project verify <canonical.dox> --mode structural
cozy document-project export <canonical.dox> --target simplemodeling-org --save <new-public-export>
cozy media prepare-publication <media-file> --project <canonical.dox> --bundle <verified-public-export> --task-root <existing canonical task-private root> --save <absent direct child> [--target <media-resource>] [--site-root <canonical paired root> --site-config <canonical paired config>]
```

`CozyDocumentProjectPublicationPreparationCommand.Config.create(List[String])`
parses exactly one media-file and the four required named options into the API
configuration. Options accept both `--name value` and `--name=value` in any
order. Missing, duplicate, empty, null and unknown inputs, extra positionals,
`--dry-run`, `--profile` and `--publication` are rejected. Host paths normalize
absolute; paired site paths must already be absolute. Canonical source/site
admission remains with the unchanged producers. `execute(args)` and
`execute(config)` call preparation and render only a completed installed result:

```text
Cozy Media Prepare Publication
status: PREPARED
root: <installed prepared-root>
export: <installed prepared-root>/export
registry:
  - <installed prepared-root>/<native registry bundle>.json
```

`Prepared(root: Path, exportRoot: Path, registryPaths: Vector[Path])` contains
actual installed paths. Registry filenames come from the existing registry
loader's bundle names; the client adds no parser or schema. Producer failures
escape unchanged before any successful command output; the invoking skill
reports `BLOCKED` for a failed native command.

The producer-native operation resolution and actual run result govern progress.
Required and selected Work Products apply to the producer-governed closure of
the requested operation and export. The current public export admits only
native `article-review-html`: an accepted/current Article review run followed by
structural verification and native export can proceed while unrelated standard
`article-pdf` remains Required, blocked, and bound to an unavailable provider.
Clients must not turn all standard profile products or PDF into additional
public-export prerequisites. Missing provider/capability, blocked participating
Work Product, failed native execution, or invalid evidence within the selected
closure stops progress with the exact producer reason and identity.

Actual native `accepted`, `resolved`, and `blocked` results remain distinct.
`article.render-pdf` reports its `smartdox-rendering` binding and missing native
typed provider capability without an accepted attempt. Unknown `render` and
profile-disabled `video.render-review` throw `DP-OP-001`; a missing `index.dox`
throws the original initial-source admission `DP-PATH-001` before execution.
Clients do not manufacture failed ProviderResults or receipts for these throws.
Structural verification legitimately writes derived
`target/document-project/state.yaml`, but creates no accepted production evidence.
Article review `--dry-run` reports resolved output identity, pending receipt,
and no evidence; it writes no accepted attempt, native Article output, or export.
Neither result authorizes preparation. The export bundle remains the unchanged
generic v1 bundle, without retrospective evidence adoption.

The caller supplies attributable authorization for the selected native
run/verify/export and exact task-private preparation effects. The canonical
skill source is
`/Users/asami/src/development-workstation/common/codex/skills/smorg-publication-prep`,
consumed by the installed `.agents` symlink. The skill preserves that invocation
and its exact paths through `cozy-command-execution` and `cozy_command_runner`.
Neither target evidence nor a caller-declared `taskRoot` proves platform access
or grants effects. The selected SimpleModeling.org checkout remains read-only;
production generation, commit, publication, deployment, upload, and push require
separate authorization and are outside this preparation boundary.

## Required preparation call order

1. After rejecting a null Config, call
   `planCurrentRegistration(config.registration)` before any task-root or
   destination mutation. Consume its fresh producer evidence; do not parse hashes, replace
   descriptors, synthesize receipts, or duplicate currentness authority.
2. Validate normalized absolute `taskRoot` as an existing direct non-symlink
   directory whose real path equals its normalized path. Require a destination
   absent under `NOFOLLOW_LINKS` that is its direct child. Reject containment in
   either direction between either output boundary and producer-returned project,
   source export bundle, media descriptor roots/identities, effective profile
   roots/identities, configured project/profile inputs, and optional site
   root/config/source.
   Original paired site options remain paired and canonical; absent context
   remains absent without invented defaults.
3. Immediately before relying on source bytes, call
   `revalidateCurrentRegistration` and consume the returned fresh value.
4. Allocate one owned `Files.createTempDirectory` child of `taskRoot`. Copy exactly the
   admitted manifest, receipt, and article-review HTML into the layout below;
   do not copy an arbitrary tree or private source/evidence. Producer
   `CozyDocumentProjectExport.verifyBundle` and target `admit` must accept the
   copied bundle with evidence equal to the fresh original verified bundle.
   Use `Files.copy` for only these three files; copy no media or site authority.
5. Call unchanged `CozyArticleMediaSiteCommand.execute` with its `Config`, the
   prepared root as `publicationRoot`, and original descriptor, media resource,
   and paired site inputs. Do not create compatibility descriptors or copy
   authority into the preparation.
6. Immediately before success installation, revalidate the original current
   registration again. The sole package-private specification callback runs
   after site registration immediately before this check; production delegates
   to the same overload with a no-op. Reject an existing destination under
   `NOFOLLOW_LINKS` and use `Files.move` with `ATOMIC_MOVE`, without replacement
   or fallback. Return only the complete installed export and registry paths.

## Prepared layout and proven scope

```text
<prepared-root>/
  export/
    manifest.yaml
    receipt.yaml
    work-products/
      article-review-html/
        article-review.html
```

The prepared root additionally contains the existing article-media registry
metadata produced by the unchanged native site command. No new registry schema
or complete production-site layout is introduced. `PREPARED` means only this
verified public export and native registry scope. It is never a whole-site
`READY` guarantee, site-pipeline readiness claim, or instruction to run a
production build.

## Failure, no-write policy, and execution assumption

Producer/capability/currentness failures report `BLOCKED` with their exact
diagnostics and offending facets; unsupported operations and dry-runs never
become success. Planning and input rejection perform no destination write. A
later failure removes only the client's owned temporary preparation without
following symlinks, leaves no
partial installed destination, and preserves source project, source export,
original media/site authority, and any existing destination bytes. Propagate
fresh producer diagnostics without synthetic attempts, receipts, currentness,
or success claims. If cleanup fails, attach that error as suppressed on the
original failure. Never remove a preexisting destination or original source.
The skill performs no direct evidence editing, adoption,
hash parsing, receipt synthesis, or copy workaround.

Execution assumes a single developer does not concurrently mutate source
authority, preparation destination, or its parent directories. Use standard
Java atomic move; add no JNA, concurrency locks, retries, or stronger concurrent
mutation guarantee.

The client uses `lightweight-no-raster-review`: declared infographic deliverables
are allowed, but PDF/slide/Web/video inspection-only rasterization, video-frame
extraction, and montage QA are excluded. Structural skill acceptance validates
frontmatter and agent-interface YAML only; genuine native behavior proof belongs
to P573-02/03.

## Executable preparation proof

[CozyDocumentProjectPublicationPreparationSpec](../../src/test/scala/cozy/publication/CozyDocumentProjectPublicationPreparationSpec.scala)
authors five bounded native scenarios: paired/absent original context with
all/exact English selection and exact portable export/strict registry metadata;
six ScalaCheck dispatcher permutations with split/equal-value options; existing
destination preservation; missing/file/aliased/non-direct/source-overlapping
output rejection; and a controlled authored source change before final
revalidation that propagates the fresh producer diagnostic and cleans only
owned temporary output. Fixtures scaffold a real Document Project, select
Article review, execute its native run, and export through the unchanged
producer. Prebuilt PNG bytes are admitted media evidence, never native receipt
substitutes. These five scenarios and six CLI property cases retain the accepted
P573-02 local proof recorded above.

P573-03 authors seven additional bounded families, pending parent validation
and independent review:

- A real run/structural verify/export/dispatcher preparation sequence, consuming
  typed current Article review evidence while unrelated required PDF is blocked.
- Native unavailable PDF provider output and typed Required/blocked Work Product
  state, followed by no-write export and preparation rejection.
- Structural verification with legitimate derived state, then resolved dry-run
  and rejected export/preparation preserving the complete post-verification tree.
- Unknown and profile-disabled operation throws, plus missing authored Article
  `DP-PATH-001` admission, with no synthesized failed result or evidence.
- Independently stale authored source, selection, genuine receipt identity,
  and deleted genuine accepted attempt; portable admission still succeeds while
  API and real CLI reproduce the fresh producer failure byte-for-byte.
- Malformed manifest, changed HTML, missing manifest/receipt/HTML, and private
  entry rejection through the fresh producer, API, and real CLI.
- Actual dispatcher rejection of missing task root, duplicate save, unknown
  profile, unsupported dry-run, and unpaired site options, matching Config.create.

Negative cases compare the complete intentionally changed baseline, absent
requested destination, absence of owned preparation temporary children, and
absence of PREPARED output; stale/bundle and CLI matrices preserve existing
sibling destination sentinels. This authored proof does not claim installed
binary capability, structural skill acceptance, or Phase closure.
