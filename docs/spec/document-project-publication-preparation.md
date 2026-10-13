# Document Project Publication Preparation Specification

## Status and authority

Phase 57.3 P573-01 specifies this native client boundary. P573-02 delivered the
API, media command and bounded executable proof described below and was accepted
locally with 41 passing tests, an independent PASS review, and Step commit
`8d06d7c`. P573-03 adds the seven native sequence/failure scenario families and
canonical skill guidance described below; they were accepted locally with
50 passing focused tests across four suites, independent PASS review and Step
commits Cozy `68af4c0` and development-workstation `049683f`. The single
Phase full review passed; final closure is recorded by the
[Phase checklist](../phase/phase-57.3-checklist.md) and distinct release commit.
Source acceptance does not prove that the actually invoked written Cozy
exposes the preparation command. Clients
check that capability through native help or command resolution; if absent,
they report `BLOCKED: missing preparation-client capability` without a fallback.

The [target specification](document-project-publication-target.md) owns
portable role/format admission and ordinary project/media/site input resolution.
On 2026-10-13, the user explicitly approved integration with the Phase 71.5
removal policy: no SHA/currentness or snapshot-revalidation gate, atomic
installation, rollback or existing-generated-output refusal. The acceptance
counts above remain historical results for their original revisions. [Document Project](document-project.md) owns native
run, structural verification, and public export. Preparation consumes these
authorities; it introduces no provider, evidence schema, target, registry
semantics, or production-site authority.

## Native inputs and command sequence

The API is exactly
`CozyDocumentProjectPublicationPreparation.Config(registration: ProjectRegistrationConfig,
taskRoot: Path, destination: Path)` with `prepare(Config): Prepared`.
`ProjectRegistrationConfig` retains the live project and `RegistrationConfig`
containing the admitted public bundle and original
`CozyArticleMediaSiteBinding.Config`. The thin CLI parses into these unchanged
producer inputs:

```text
cozy document-project run <canonical.dox> --operation article.render-review
cozy document-project verify <canonical.dox> --mode structural
cozy document-project export <canonical.dox> --target simplemodeling-org --save <new-public-export>
cozy media prepare-publication <media-file> --project <canonical.dox> --bundle <verified-public-export> --task-root <existing canonical task-private root> --save <direct child> [--target <media-resource>] [--site-root <canonical paired root> --site-config <canonical paired config>]
```

`CozyDocumentProjectPublicationPreparationCommand.Config.create(List[String])`
parses exactly one media-file and the four required named options into the API
configuration. Options accept both `--name value` and `--name=value` in any
order. Missing, duplicate, empty, null and unknown inputs, extra positionals,
`--dry-run`, `--profile` and `--publication` are rejected. Host paths normalize
absolute; paired site paths must already be absolute. Canonical source/site
admission remains with the unchanged producers. `execute(args)` and
`execute(config)` call preparation and render only a completed written result:

```text
Cozy Media Prepare Publication
status: PREPARED
root: <written prepared-root>
export: <written prepared-root>/export
registry:
  - <written prepared-root>/<native registry bundle>.json
```

`Prepared(root: Path, exportRoot: Path, registryPaths: Vector[Path])` contains
actual written paths. Registry filenames come from the existing registry
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
consumed by the written `.agents` symlink. The skill preserves that invocation
and its exact paths through `cozy-command-execution` and `cozy_command_runner`.
Neither target evidence nor a caller-declared `taskRoot` proves platform access
or grants effects. The selected SimpleModeling.org checkout remains read-only;
production generation, commit, publication, deployment, upload, and push require
separate authorization and are outside this preparation boundary.

## Required preparation call order

1. Resolve `planProjectRegistration(config.registration)` before output writes.
   Retain ordinary project descriptor, export target/format/required-file,
   media profile/resource/locale/role and paired site-context diagnostics.
2. Validate `taskRoot` as an existing canonical direct non-symlink directory.
   The destination is its direct child, either absent or an existing direct
   directory. Keep output paths disjoint from authored project, source export,
   media and site inputs. Existing generated output may be overwritten.
3. Create the selected generated directories directly. Reject symbolic output
   directories and file aliases. Copy only `manifest.yaml` and the declared
   article-review HTML from the admitted export, replacing selected generated
   files. Do not copy an arbitrary source/evidence tree or require a
   protection-only export receipt.
4. Execute the existing native article-media site command using the original
   media/site configuration and the destination as its publication root.
   Resolve the produced registry paths through the native registry bundle names.
5. Return `Prepared` and emit `PREPARED` only after these operations finish.
   There is no before/after source revalidation, temporary installation tree,
   atomic move, rollback or substitute unchanged-content proof.

## Prepared layout and proven scope

```text
<prepared-root>/
  export/
    manifest.yaml
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

## Failure and execution semantics

Invalid selected input fails with the ordinary producer diagnostic before
preparation writes. Native blocked operations, unavailable providers and
unexecuted dry-runs remain unsuccessful and cannot fabricate production
evidence. A later I/O failure propagates and may leave earlier or partial
generated output at the requested destination; no success is returned.
Existing generated files may be overwritten. Authored inputs, unselected
destination files and paths outside the selected operation remain untouched.

Execution assumes the user's Git-managed single-developer workbench. No
concurrency/tamper assurance, atomic installation, retry or rollback protocol
is provided. Platform permission and build serialization remain separate.

The client uses `lightweight-no-raster-review`: declared infographic deliverables
are allowed, but PDF/slide/Web/video inspection-only rasterization, video-frame
extraction, and montage QA are excluded. Structural skill acceptance validates
frontmatter and agent-interface YAML only; genuine native behavior proof belongs
to P573-02/03.

## Executable preparation specification

[CozyDocumentProjectPublicationPreparationSpec](../../src/test/scala/cozy/publication/CozyDocumentProjectPublicationPreparationSpec.scala)
retains genuine scaffold/run/structural-verify/export and actual dispatcher
scenarios, selected Article review despite unrelated blocked PDF, native
unavailable/unknown/disabled/missing-source diagnostics, ordinary malformed or
incomplete/private bundle rejection, CLI option rejection, paired/absent context
and exact resource selection. Six ScalaCheck cases exercise option orders and
split/equal-value spelling.

Output scenarios cover overwriting selected generated files while preserving
unselected files, safe output-root and symlink rejection, and an ordinary I/O
failure that propagates with partial output and intact authored inputs. Retired
stale-facet, protected-receipt and final snapshot-revalidation scenarios do not
define the current contract. Fixtures use the actual producers and prebuilt
media rather than manufactured native receipt or provider evidence.

These specifications do not claim complete production-site readiness,
installed-binary capability, deployment or a new Phase closure.
