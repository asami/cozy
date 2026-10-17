# Document Project Publication Target Specification

## Status and scope

This specification defines the Phase 57.2 P572-01 portable admission boundary,
P572-02 registration planning, and project input resolution for
one SimpleModeling.org publication target.
It consumes the generic verified Document Project export bundle from
`CozyDocumentProjectExport` and the original Phase 55 media/site authorities,
providing read-only selected inputs for later orchestration.

Revision: 2026-10-13 repository integration. The historical Phase 57.2
SHA/currentness and snapshot-revalidation gates are superseded by the accepted
Phase 71.5 removal policy and this explicitly approved integration. Historical
Phase acceptance and original Git records remain intact.

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
partial, malformed, or differently targeted bundles fail with `DP-OP-001`.
Admission performs no filesystem mutation and does not read a source project.

Admission establishes required export roles, safe paths, manifest format and the
selected target. It does not establish unchanged bytes or bind an export to a
past source snapshot. It grants no deployment or preparation authorization.

## Registration planning

`RegistrationConfig(bundle, media)` accepts a bundle path and the original
`CozyArticleMediaSiteBinding.Config`. `planRegistration` re-admits the bundle
through `admit`; caller-created `VerifiedExport` values cannot replace this
verification. Null registration or media configuration fails with `DP-OP-001`.
Media descriptor paths, resource selection, and paired site options retain
Phase 55's validation and diagnostics.

The planner calls `CozyArticleMediaSiteBinding.plan` with the original media
configuration. It resolves the selected descriptor through the ordinary
`CozyMedia` API, forwards the original target and paired site options, and
selects the binding's publication profile explicitly. No captured descriptor
bytes or before/after snapshot comparison are required.

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
selection, invalid context, required export files, null inputs, and read-only planning.

## Project registration inputs

`ProjectRegistrationConfig(project: Path, registration: RegistrationConfig)`
supplies the selected Document Project root and original media configuration.
`planProjectRegistration` rejects missing configuration, project or registration
with `DP-OP-001`. The project must be an existing direct canonical `.dox`
directory. Aliased roots and symbolic ancestors remain invalid.

The planner loads the current project descriptor through
`CozyDocumentProject._load_project`, preserving its domain diagnostics, then
calls `planRegistration`. `ProjectRegistrationPlan(config, registration)`
contains the normalized configuration and resolved registration. It performs
no write and declares no SHA/currentness facets or source/export equality.
Callers resolve their selected inputs through these ordinary operations; an
earlier plan is not an unchanged-content proof. There is no snapshot
revalidation API or protection compatibility adapter.

`CozyDocumentProjectPublicationCurrentnessSpec` retains its historical suite
identity while covering ordinary project input resolution, required/malformed
bundle files, unusable profile/site inputs, invalid project roots and original
descriptor diagnostics. Obsolete unchanged-byte and stale-facet expectations
are retired; unrelated product behavior remains specified.

## Preparation consumer inputs

The consumer supplies `ProjectRegistrationConfig`, including the selected
project, export bundle and original media configuration, and consumes
`ProjectRegistrationPlan`. Target identity, configured profile, resource and
locale/role selections, paired canonical site context and ordinary I/O/domain
validation remain owned by their existing producers. No receipt synthesis,
manual evidence adoption or replacement descriptor is introduced.

The registration planner does not generate or deploy a production site and
grants no remote-publication, upload, Git or filesystem authorization. The
separate preparation operation owns its selected generated output boundary.
