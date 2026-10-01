# Document Project Publication Preparation Specification

## Status and authority

Phase 57.3 P573-01 specifies this native client boundary; P573-02 delivers the
implementation and P573-03 its failure proof. This specification does not claim
that the installed Cozy already exposes the preparation command. Until it does,
clients report missing preparation-client capability and stop without a legacy
adapter fallback.

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

The producer-native operation resolution and actual run result govern progress.
Missing providers/capabilities, blocked required Work Products, failed native
execution, or invalid evidence stop before export or preparation. Structural
verification does not create accepted production evidence; neither does a
`--dry-run`. The current public export admits only native `article-review-html`;
clients must not invent other required public Work Products or adopt retrospective
evidence. The export bundle remains the unchanged generic v1 bundle.

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

1. Call `planCurrentRegistration(config.registration)` before any destination
   write. Consume its fresh producer evidence; do not parse hashes, replace
   descriptors, synthesize receipts, or duplicate currentness authority.
2. Validate `taskRoot` as an existing direct canonical directory. Require an
   absent destination that is its direct child, disjoint from the project,
   source export bundle, media descriptor/context/profile, and site roots.
   Original paired site options remain paired and canonical; absent context
   remains absent without invented defaults.
3. Immediately before relying on source bytes, call
   `revalidateCurrentRegistration` and consume the returned fresh value.
4. Prepare under an owned temporary child of `taskRoot`. Copy exactly the
   admitted manifest, receipt, and article-review HTML into the layout below;
   do not copy an arbitrary tree or private source/evidence. Producer
   `CozyDocumentProjectExport.verifyBundle` and target `admit` must accept the
   copied bundle with evidence equal to the original verified bundle.
5. Call unchanged `CozyArticleMediaSiteCommand.execute` with its `Config`, the
   prepared root as `publicationRoot`, and original descriptor, media resource,
   and paired site inputs. Do not create compatibility descriptors or copy
   authority into the preparation.
6. Immediately before success installation, revalidate the original current
   registration again. Reject an existing destination and atomically rename
   the complete preparation into the absent destination. Return the actual
   prepared export and native registry paths.

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
later failure removes only the client's owned temporary preparation, leaves no
partial installed destination, and preserves source project, source export,
original media/site authority, and any existing destination bytes. Propagate
fresh producer diagnostics without synthetic attempts, receipts, currentness,
or success claims. The skill performs no direct evidence editing, adoption,
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
