# Document Project Publication Target Specification

## Status and scope

This specification defines the Phase 57.2 P572-01 portable admission boundary
for one SimpleModeling.org publication target. It consumes the generic verified
Document Project export bundle from `CozyDocumentProjectExport` and provides a
read-only evidence snapshot for later orchestration.

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

## Boundary exclusions

This target does not parse YAML, recompute hashes, reinterpret bundle mappings,
replace descriptors or receipts, write a site, register an article, deploy,
publish, or execute Phase 57.3. Source-currentness and site-context integration
are defined by the remaining Phase 57.2 Steps.
