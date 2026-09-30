# Document Project Publication Target Specification

## Status and scope

This specification defines the Phase 57.2 P572-01 portable admission boundary
and P572-02 registration snapshot for one SimpleModeling.org publication target.
It consumes the generic verified Document Project export bundle from
`CozyDocumentProjectExport` and the original Phase 55 media/site authorities,
providing read-only evidence for later orchestration.

## Target admission

`CozyDocumentProjectPublicationTarget` has one sealed target identity:
`SimpleModelingOrg`, whose opaque serialized slug is `simplemodeling-org`.
The target is independent of the Document Project authoring profile. Admission
accepts a bundle path, normalizes its absolute lexical root, calls the generic
`CozyDocumentProjectExport.verifyBundle` verifier, and then requires the exact
`simplemodeling-org` target slug.

Successful admission returns a `VerifiedExport` containing the normalized
bundle root, the typed `SimpleModelingOrg` target, the unchanged generic
`CozyDocumentProjectExport.Bundle` evidence, and the direct
`work-products/article-review-html/article-review.html` path. Null, malformed,
partial, damaged, or differently targeted bundles fail with `DP-OP-001`.
Admission performs no filesystem mutation and does not read a source project.

The snapshot proves portable bundle integrity only. It is not source
currentness evidence, site context, registration authority, deployment
authorization, or a preparation plan. Later orchestration must re-admit and
revalidate before relying on it.

## Registration snapshot

`RegistrationConfig(bundle, media)` accepts a bundle path and the original
`CozyArticleMediaSiteBinding.Config`. `planRegistration` re-admits the bundle
through `admit`; caller-created `VerifiedExport` values cannot replace this
verification. Null registration or media configuration fails with `DP-OP-001`.
Media descriptor paths, resource selection, and paired site options retain
Phase 55's validation and diagnostics.

The planner calls `CozyArticleMediaSiteBinding.plan` with the original media
configuration. It resolves a `CozyMedia` plan from the binding's exact descriptor
path and captured descriptor bytes, forwards the original target, site root,
and site config, and selects the binding's publication profile explicitly.
Descriptor, project context, and effective profile must match the producer's
binding; inconsistent authorities fail with `DP-OP-001`.

`RegistrationPlan(config, export, siteBinding, siteContext)` retains the input
configuration, newly admitted export, and unchanged Phase 55 binding, including
its candidates and evidence. `siteContext` is obtained through
`CozyMedia.requireSiteContext`. An explicit paired context preserves the
producer's canonical root, config, knowledge source, and relative routes.
Absent explicit context remains `None`; no root/config defaults are invented.
The configured SmartDox profile and descriptor overlay remain producer-owned
authorities. Registration creates no compatibility descriptor, copies no
sources, fabricates no receipts, and writes no files.

Executable coverage is in
`CozyDocumentProjectPublicationRegistrationSpec`: genuine scaffold/run/export
admission, paired and absent site context, configured profile overlay, resource
selection, invalid context, portable-export integrity, null inputs, and fixture
immutability.

## Boundary exclusions and pending work

This target does not parse YAML, recompute hashes, reinterpret bundle mappings,
replace descriptors or receipts, write a site, register an article, deploy,
publish, or execute Phase 57.3. P572-03 live source currentness, registration
snapshot revalidation, and the final handoff to separately authorized Phase
57.3 remain pending. Portable integrity and this read-only registration snapshot
do not supply those later guarantees or authorize preparation or mutation.
