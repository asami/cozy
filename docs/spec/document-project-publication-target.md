# Document Project Publication Target Specification

## Status and scope

This specification defines the Phase 57.2 P572-01 portable admission boundary,
P572-02 registration snapshot, and P572-03 live-current registration gate for
one SimpleModeling.org publication target.
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

## Live-current registration gate

`CurrentRegistrationConfig(project: Path, registration: RegistrationConfig)`
supplies a live Document Project root and the original registration inputs.
`planCurrentRegistration` rejects null configuration, project, or registration
with `DP-OP-001`. The project becomes an absolute lexical normalized path and
must be an existing direct `.dox` directory whose canonical path equals that
lexical path. Project aliases, including symbolic ancestors, are rejected.
Path resolution runtime failures retain `DP-OP-001`; descriptor loading
failures retain the descriptor producer's diagnostics.

The gate loads a fresh descriptor through `CozyDocumentProject._load_project`.
No caller-supplied descriptor is accepted. It then calls `planRegistration`
to verify the complete portable bundle and capture the original media/site
authorities, followed by `CozyDocumentProjectExport.currentness` with that
normalized project, freshly loaded descriptor, and verified export bundle root.
The following five opaque producer-owned facets must each be exactly `current`:

| Facet | Producer authority |
| --- | --- |
| `sourceauthority` | Authored source authority bound by the export |
| `selection` | Current profile and active optional Work Product selection |
| `retainedproductionevidence` | Current accepted native production evidence |
| `manifestauthority` | Export manifest authority |
| `exportedbytes` | Exported Work Product bytes |

Any other facet state fails with `DP-OP-001`, naming every offending facet and
its unchanged producer state. The target computes no fingerprints and does
not reinterpret currentness. Portable admission and registration snapshots can
still succeed when live source, selection, or retained evidence is stale;
they are not substitutes for this gate. Damaged or incomplete bundles fail
portable verification before live acceptance.

`CurrentRegistrationPlan(config: CurrentRegistrationConfig,
registration: RegistrationPlan, currentness: CozyDocumentProjectExport.Currentness)`
returns the configuration with its normalized project, the fresh registration,
and unchanged producer currentness. The effective optional `siteContext` remains
the canonical Phase 55 context when paired options were supplied and `None`
when absent. The gate invents no site defaults and performs no writes.

## Snapshot revalidation

`revalidateRegistration(value: RegistrationPlan): RegistrationPlan` recomputes
`planRegistration(value.config)` and requires whole-value equality before
returning the recomputed value. `revalidateCurrentRegistration(value:
CurrentRegistrationPlan): CurrentRegistrationPlan` similarly recomputes
`planCurrentRegistration(value.config)` and requires whole-value equality.
Null or unequal snapshots fail with `DP-OP-001`; fresh producer diagnostics
remain authoritative when recomputation fails. Caller-created or copied
snapshots are not proofs: they must equal the newly resolved authorities.
Equality covers producer binding evidence and canonical optional context
without target-owned hashes or duplicate producer planning/revalidation calls.

Executable coverage is in `CozyDocumentProjectPublicationCurrentnessSpec`:
genuine scaffold/run/export fixtures, all five current facets, paired and absent
context, immutable successful and failed evaluation, independently stale source,
selection and native evidence, incomplete/damaged bundles, unchanged and forged
snapshots, original media/profile/image/context mutations, invalid roots, and
preserved fresh descriptor diagnostics.

## Frozen Phase 57.3 consumer handoff

The internal typed inputs are exactly `RegistrationConfig` and
`CurrentRegistrationConfig` above; the outputs are `VerifiedExport`,
`RegistrationPlan`, and `CurrentRegistrationPlan` through their respective
admission/planning operations. Exactly one target remains `SimpleModelingOrg`
with slug `simplemodeling-org`; the generic export v1 schema is unchanged.
The separately authorized Phase 57.3 orchestrator must use this call order:

1. Supply the live project root, portable bundle path, and original Phase 55
   media configuration in a `CurrentRegistrationConfig`.
2. Call `planCurrentRegistration`, which loads the descriptor, calls
   `planRegistration` (portable admission, original binding, effective context),
   and evaluates the five producer currentness facets in that order.
3. Immediately before downstream reliance, call `revalidateCurrentRegistration`
   and consume its returned fresh value. A consumer relying only on the
   portable registration snapshot must call `revalidateRegistration` immediately
   before use and cannot infer live-current authority from that operation.

Changed authored sources, profile/active optional selection, retained native
receipt or missing accepted attempt invalidate live authority. Changed export
manifest, receipt, or output, original media descriptor, configured publication
profile/root, selected candidate image bytes, or effective paired site context
invalidate the relevant snapshot. Deleted/empty site configuration and invalid
or aliased knowledge sources are resolved through the existing Phase 55
diagnostics. Changed target binding or site-aware registration contract requires
a fresh handoff. None of these triggers can be bypassed by retaining an earlier
snapshot, inventing a receipt, or adopting manual evidence.

Execution assumes a single operator. Revalidation is a read-only observation
immediately before reliance; it provides no locking, retries, or protection
against external concurrent mutation. A capability-limited client may transport
the typed inputs and inspect returned evidence, but cannot widen producer
authority, infer filesystem access authorization from a target/context, or
replace the gate with client assertions. Phase 57.3 needs separate authorization
for its own orchestration and filesystem capabilities. This handoff delivers
neither prepared-site output nor access authorization.

## Boundary exclusions

This target does not parse YAML, recompute hashes, reinterpret bundle mappings,
replace descriptors or receipts, write a site, register an article, deploy,
publish, or execute Phase 57.3. The read-only live gate and revalidation authorize
no preparation, copying, production-site mutation, deployment, upload, or push.
