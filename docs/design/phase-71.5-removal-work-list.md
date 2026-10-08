# Phase 71.5 Remaining Removal Work List

Status: selected implementation list; no product removal, validation or acceptance claimed.
Date: 2026-10-08.
Original accepted base: `fc640ec25e443d00c22b2c26b437a205d39910eb`.
Owner: Cozy only. External sources below are read-only observations.

This is P715-01's finite work list, under [Phase 71.5](../phase/phase-71.5.md)
and [artifact protection removal](artifact-protection-removal.md). It is not
a runtime registry, an integrity exception or a new closure gate. Preserve
accepted predecessor removals. Use this list in ordinary implementation and
review; do not repeat whole-codebase absence discovery after each batch.

## Implementation order and acceptance

1. Record this inventory and concrete external dependencies; accept and commit
   the documentation Step P715-01 independently. This does not close a product
   removal row or the Phase.
2. Remove connected document/media/video protection on the accepted 71.4 main
   route, including directly shared models, codecs, writes and callers in the
   same compile-valid batch. P715-02/04/05 track these changes together. Keep
   their existing required Step acceptance and commits; labels do not add a
   review per helper. No historical S08 manifest is dispatched unchanged.
3. Generate and assemble the same Japanese 34-scene Model Harness/VOICEVOX
   representative through normal Cozy commands into task-private outputs,
   then verify actual audible playback, end card and EOF. Existing 71.4 results
   identify the route and inputs; they do not prove the changed route works.
   Fix regressions here before expanding the remaining publication/archive
   scope. Later changes require another route check only when they affect it.
4. Finish the remaining finite publication/filesystem/contracts list. The
   affected external contracts remain open until independently reconciled
   under separate authority. Complete P715-06 handoff, normal Step acceptance,
   the sole Phase full review and distinct release only when actual closure
   is proven. Full SBT and broader integration remain deferred-not-run to 71.7.

Each later program manifest freezes its exact paths, concrete symbols, retained
behavior and affected existing suites before editing. This inventory is not
permission to change every matching file or execute all candidate suites.

## Finite removal groups

| ID | Remaining behavior and concrete owners/callers | Retained independent product meaning | Existing specification boundary |
| --- | --- | --- | --- |
| R715-01 | Document Project `Evidence.FileIdentity`, native inputs/outputs/attempts and provider receipts; `CozyDocumentProject`, `Evidence`, `NativeEvidence`, `Alignment`, `Executability`, `Projection`, `Provider` and `Export`. Remove local SHA fields, emission and comparisons, native receipt digest and byte-copy currentness. Reconcile scaffold, dashboard, public-source and export callers together. | Actual source/output paths, operation/provider/profile, roles/locales, Core/semantic mappings, successful/failed attempt facts and selected output validity. Declared dependency existence/mtime may select rebuild; no persisted unchanged-input certificate. | `CozyDocumentProjectSpec`, `CozyDocumentProjectNativeEvidenceSpec`, `CozyDocumentProjectExportSpec`, affected `CozyDocumentProjectSiteProjectionSpec` and local-build specs. |
| R715-02 | Unstarted 71.4 S08 plus remaining `CozyDocumentPresentationSemantics`, `CozyDocumentProjectPresentationSemanticsState`, CrossMediaProjection/Receipt/ConfirmationHtml. Remove semantic/source/acceptance hashes and support fields. S08's old D06 distribution exception and proposed new PathEvidence suite are superseded. | Core identity, article/summary mappings, human decision/rationale and actual semantic projection. Preserve source privacy and ordinary malformed data rejection. | Existing document presentation, confirmation/projection, native evidence and export specs; no new suite. |
| R715-03 | `CozyExplanation`, Json/Projection/Preview, `CozyVisualPage`/Parsing/Preview, `CozyMedia`, Presentation/Migration/CrossReview/Receipt. Remove remaining local content identities, unused digest routines/fields and asset-byte equality used as protection. Remove atomic-write/install helpers with all immediate callers. | Page/scene/asset IDs and paths, mappings, roles, actual image/PDF/presentation structure and semantic cross-review. Signature/header recognition is format behavior; collection hashing is not an artifact identity. | Existing Explanation, VisualPage, MediaPresentation, Migration, CrossReview, Receipt and media specs. |
| R715-04 | `CozyMediaPdf`, `CozyMediaSummarySlidesPdf`, `CozyMediaPublicationTransaction`, `CozyMediaArticleScaffold`. Remove pre/post-render snapshots, asset-copy comparisons, staging/atomic move, backups/restore, force/fsync and publication digest. | Selected input/destination, actual PDF parsing/page count/required images, ordinary renderer/I/O failure. No prior-generated-output preservation promise. | Existing PDF, summary slides, scaffold, receipt and media-publication specs. |
| R715-05 | `CozyVideoRenderWorkspace` and `CozyVideoRenderTemplates` bundle digest/pins; `CozyVideoStoryboardArtifacts`, Build and Publisher atomic-device/install/rollback/byte-verification machinery; `CozyVideoCredits` repeated profile reads/race checks. Remove protection-only fields/configuration/help with callers. | Renderer resource names, template layout, scene/frame/audio/end timing, voice/provider/credits and ordinary valid input/source paths. DiagramLayout `atomic` label-fit policy is typography, not filesystem protection. | Existing storyboard build/mode/artifacts/review/narration, render/credits/publisher-admission, media-ending/video-build specs and actual representative route. |
| R715-06 | `CozyLogicalUiSemantics`, Projection and Review semantic hashes and atomic-write helpers; `CozyCarReviewProvider` request/evidence digest bindings. | Public semantic types, components/use-case IDs, realized UI, review IDs/rules/provider/limits and substantive observations. | Existing LogicalUi/semantics/review/runtime and actual affected provider specs. |
| R715-07 | `CozyDocumentProjectExport` D06 bundle/output/manifest SHA and comparison; export staging/atomic install. Remove remaining receipt integrity views, not just local authority hashes. | Role/target/slug and relocated selected bundle's ordinary shape, missing members, source privacy and real output. No replacement distributed-integrity receipt. | Existing export and site-projection specs; intentionally retire protection-only tamper assertions. |
| R715-08 | `CncfRuntimeDescriptorContract`, `CozySbtBridge`, `ComponentStyleCatalog` selected descriptor/style digest pins and support fields/CLI settings. Exact external disposition E715-02 below. | Real descriptor location, parsed schema/runtime/version/result catalog and style semantics; ordinary source option agreement. | Existing descriptor, bridge, style-catalog and generation compatibility specs, affected actual sbt-cozy connection only after separate reconciliation. |
| R715-09 | `CozyArchivePackager`, `ComponentReleaseSourceProjection`, `CozyCarRuntimeManifest`, `CozyDevelopmentRuntimeManifest`, `ComponentApiDependencyResolver`, `ComponentApiJarPackager`. Remove payload inventory/content digests, integrity manifest emission/admission and protection-oriented extraction/install snapshots/backup/rollback. Carrier and runtime external boundaries E715-01/03 remain open. | Exact coordinates/API class/version/path, policy/license/source-disclosure, source/Scaladoc selection and ordinary ordered paths/missing/ambiguity/format errors. Standard ZIP stored-entry CRC/size remain. | Existing archive-packager, release/source projection, runtime-manifest and API resolver/packager specs. |
| R715-10 | `RepositoryArtifactPublisher`, both repository catalog facades, `ComponentRepositoryIndex`, `CozyCarPublisher`, `CozySarPublisher`, `LegacyComponentRepositoryMigration` checksum/catalog/integrityKey/sidecar checks and metadata; publication locks, CAS, snapshots and rollback. | Artifact coordinates, archive type/runtime compatibility, repository paths, selection and migration mapping. SNAPSHOT version matching is version semantics. | Existing repository catalog/index, publication/namespace/migration and BoK component-repository specs. |
| R715-11 | `CozyComponentKnowledgeCarrier` declaration digest and archive comparison, `SubcomponentReleasePackaging` archive/composition/release/signature/integrity/admission hashes and post-byte verification/locks/restore. Exact external boundaries E715-01/03/04 below. | Carrier schema/logical path and genuine knowledge content, parent/child coordinates/classification/membership/roles, runtime/technology and selected payload paths. | Existing knowledge/archive and subcomponent packaging specs plus separately authorized real CNCF readers before changing emitted required fields. |
| R715-12 | `CozyArticleMediaIntegrity` record/digest codec and Association/Policy/Registry/VideoEvidence/VideoRegistration/InfographicEvidence callers; WipBinding/Transaction, SiteBinding, BuildContext/WarehouseBuildContext, PublicationCompiler. Remove distributed artifact identities, registry CAS/locks, repeated read/race observations, protected snapshots, transaction backups/restore/fsync/atomic installs. | Selected article/media owner, role/path/URL, publication intent, declared dependencies, real video/image/PDF/manifest facts and ordinary failure. Do not use dates/size/receipt IDs as substitute immutability certificates. | Existing article-media registry/policy/association/site/WIP/build/evidence/registration/publication specs selected by actual changed edge. |
| R715-13 | `CozyWarehouseIndexer`, `CozySampleDistributor`, BoK ProjectPublisher/RepositoryMetadata/RepositoryPages/Publications/Build/SieMetadata; former artifact checksum and byte-admission metadata and finalization snapshots/backups/atomic operations. Website backup configuration/helpers used for generated-output protection are included. | Bibliography/catalog/project/publication coordinates, titles/terms/site routes, selected copy destinations and ordinary process/I/O result. No production site/warehouse or remote deployment is executed. | Existing warehouse, BoK/publication/site/metadata-finalization/build specs; task-private actual connection where affected. |
| R715-14 | Non-hash protection in `CozyProjectContext`, `CozyArticleMediaSiteBinding`, `CozyArticleMediaWipBinding`, `CozyMediaWarehouse`, `CozyMediaPresentation`, `CozyVideoCredits`; document ContentCore/Description/Confirmation/LogicTree/SummarySlide and modeler GenerationProvenance/Aggregate write paths. | Direct-file/containment/authored-vs-generated ownership, normal parsing and actual selected mappings. Ordinary state snapshots describing human/semantic facts may remain; repeated bytes/fileKey/size observations cannot establish interference-free artifacts. | Existing affected context/path, warehouse/presentation/credits, ContentCore/document and provenance specs. |
| R715-15 | Associated protection-only documentation/help/configuration/resources/scaffolds/catalog metadata and executable expectations in the indexed boundary. | Amend only removed guarantees. Existing semantic/domain/path/failure assertions stay. PNG/ZIP wire behavior stays; no new removal-proof test, generic harness or final absence-audit cycle. | Existing candidate suite index below is selection input, not a full-test gate. |

## Concrete external obligations — open

| ID | Actual producer and reader | Observed contract and smallest separately owned reconciliation | Disposition |
| --- | --- | --- | --- |
| E715-01 | Cozy `archive/CozyComponentKnowledgeCarrier.scala` declaration; CNCF `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeCarrierCodec.scala:19,36,40` and `ComponentKnowledgeCarrier.scala` | Reader requires exactly four fields including a valid `sha256`; dropping the producer field is rejected. Reconcile CNCF carrier model/codec and its actual consuming repository/knowledge paths to schema/logical-path/content semantics without SHA. No empty digest/new version/adapter. | Stop this emitted-contract deletion before incompatibility; P715-03 remains open. External mutation not authorized. |
| E715-02 | sbt-cozy `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala:918`, `CozyCncfRuntimeDescriptor`; Cozy `runtime/CozySbtBridge.scala:321,345,421,447` and `compatibility/CncfRuntimeDescriptorContract.scala` | Plugin currently extracts/calculates SHA and supplies `runtime.cncf.descriptor.sha256`; Cozy requires the complete target/path/SHA option trio and compares bytes. Reconcile owning plugin extraction/settings/argument construction and Cozy loader together, retaining descriptor path/version/schema parsing. Do not silently ignore a supported SHA option or create legacy protection compatibility. | This connection needs separately owned plugin reconciliation before deleting its required option/field; other generator removal proceeds independently. |
| E715-03 | Cozy `archive/CozyCarRuntimeManifest.scala` integrity entries; CNCF `component/CarRuntimeAdmission.scala:235,274` | Reader requires entry SHA, listed regular-file set and actual byte equality before activation. Reconcile CNCF admission/manifest parsing to retained coordinate/ABI/runtime/path semantics without integrity records/checks. | Actual external acceptance is required before producer integrity shape deletion; do not call current reader compatible. |
| E715-04 | Cozy `archive/SubcomponentReleasePackaging.scala` composition artifact; CNCF `component/ComponentSubcomponentComposition.scala:282` | External artifact parser requires valid SHA and signature in composition members. Reconcile external artifact model/codec and consumers to real coordinates/membership/runtime/paths. | Keep affected emitted boundary explicitly unfinished until separately reconciled. No signature replacement. |

Other CNCF knowledge-manifest hashes exist, but are not automatically a Cozy
producer dependency. If an indexed caller actually emits one of those shared
shapes, record that concrete subordinate edge under the same finite group
before editing; do not broaden into unrelated CNCF cleanup or another global
inventory. External obligations are current removal requirements, not
nonblocking Development Candidates. They prevent full all-removal closure.

## Accepted work reused and exclusions

The 71.4 release journal records accepted A06 source selection, A09 local API
selection, G01/G02 provenance and A05 development-runtime work, S07 site/PDF
inputs and actual normal JA generation/final assembly/playback. Keep their
removals and unaffected results. Their remaining atomic or non-hash protection
is now selected above; accepted old protection assertions do not override 71.5.

Keep ordinary Java/Scala collection hashing, pure semantic equality,
PNG/ZIP format markers and required CRC, typography's `atomic` label policy,
SNAPSHOT version semantics, provider/model IDs and actual review facts.
Narration and replay have no remaining SHA operation in the current scan;
their independently useful speech/execution facts are not deletion targets.
Do not alter Git or the shared execution lock. Preserve all 16 preexisting
uncommitted documents; this inventory does not stage/adopt them incidentally.

## Indexed current boundary

The appendices enumerate parent-observed current candidate paths and existing
specification paths. They include mixed-purpose files; the group dispositions
above determine the delta. Later manifests freeze only the concrete next
compile-valid unit and its actual callers. Historical inventory rows supply
traceability, not unchanged-content or current removal proof.

### Current production/resource candidates

- `src/main/catalog/car/cozy.yaml`
- `src/main/resources/cozy/video/remotion/DiagramLayout.js`
- `src/main/scala/cozy/RepositoryArtifactCatalog.scala`
- `src/main/scala/cozy/archive/ComponentApiDependencyResolver.scala`
- `src/main/scala/cozy/archive/ComponentApiJarPackager.scala`
- `src/main/scala/cozy/archive/ComponentReleaseSourceProjection.scala`
- `src/main/scala/cozy/archive/ComponentRepositoryIndex.scala`
- `src/main/scala/cozy/archive/CozyArchivePackager.scala`
- `src/main/scala/cozy/archive/CozyCarPublisher.scala`
- `src/main/scala/cozy/archive/CozyCarRuntimeManifest.scala`
- `src/main/scala/cozy/archive/CozyComponentKnowledgeCarrier.scala`
- `src/main/scala/cozy/archive/CozyComponentReleaseCoordinateCodec.scala`
- `src/main/scala/cozy/archive/CozyDevelopmentRuntimeManifest.scala`
- `src/main/scala/cozy/archive/CozySarPublisher.scala`
- `src/main/scala/cozy/archive/LegacyComponentRepositoryMigration.scala`
- `src/main/scala/cozy/archive/RepositoryArtifactCatalog.scala`
- `src/main/scala/cozy/archive/RepositoryArtifactPublisher.scala`
- `src/main/scala/cozy/archive/SubcomponentReleasePackaging.scala`
- `src/main/scala/cozy/bok/CozyBokBibliography.scala`
- `src/main/scala/cozy/bok/CozyBokBuild.scala`
- `src/main/scala/cozy/bok/CozyBokBuildConfig.scala`
- `src/main/scala/cozy/bok/CozyBokCommand.scala`
- `src/main/scala/cozy/bok/CozyBokProjectPublisher.scala`
- `src/main/scala/cozy/bok/CozyBokPublication.scala`
- `src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala`
- `src/main/scala/cozy/bok/CozyBokRepositoryPages.scala`
- `src/main/scala/cozy/bok/CozyBokSieMetadata.scala`
- `src/main/scala/cozy/bok/CozyBokSiteBuild.scala`
- `src/main/scala/cozy/bok/CozyBokSiteDocument.scala`
- `src/main/scala/cozy/compatibility/CarMetadataCompatibility.scala`
- `src/main/scala/cozy/compatibility/CncfRuntimeDescriptorContract.scala`
- `src/main/scala/cozy/compatibility/GenerationCompatibility.scala`
- `src/main/scala/cozy/config/CozyProjectContext.scala`
- `src/main/scala/cozy/document/CozyDocumentConfirmationCommand.scala`
- `src/main/scala/cozy/document/CozyDocumentContentCore.scala`
- `src/main/scala/cozy/document/CozyDocumentCrossMediaConfirmationHtml.scala`
- `src/main/scala/cozy/document/CozyDocumentCrossMediaProjection.scala`
- `src/main/scala/cozy/document/CozyDocumentCrossMediaReceipt.scala`
- `src/main/scala/cozy/document/CozyDocumentDescriptionCommand.scala`
- `src/main/scala/cozy/document/CozyDocumentLogicTreeCommand.scala`
- `src/main/scala/cozy/document/CozyDocumentPresentationSemantics.scala`
- `src/main/scala/cozy/document/CozyDocumentProject.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectAlignment.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectEvidence.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectExecutability.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectExport.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectLocalBuildCommand.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectNativeEvidence.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectPresentationSemanticsState.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectProjection.scala`
- `src/main/scala/cozy/document/CozyDocumentProjectProvider.scala`
- `src/main/scala/cozy/document/CozySummarySlideProjection.scala`
- `src/main/scala/cozy/lint/CozyBuildLint.scala`
- `src/main/scala/cozy/media/CozyExplanation.scala`
- `src/main/scala/cozy/media/CozyExplanationJson.scala`
- `src/main/scala/cozy/media/CozyExplanationPreview.scala`
- `src/main/scala/cozy/media/CozyExplanationProjection.scala`
- `src/main/scala/cozy/media/CozyMedia.scala`
- `src/main/scala/cozy/media/CozyMediaArticleScaffold.scala`
- `src/main/scala/cozy/media/CozyMediaCrossReview.scala`
- `src/main/scala/cozy/media/CozyMediaPdf.scala`
- `src/main/scala/cozy/media/CozyMediaPresentation.scala`
- `src/main/scala/cozy/media/CozyMediaPresentationMigration.scala`
- `src/main/scala/cozy/media/CozyMediaPublicationTransaction.scala`
- `src/main/scala/cozy/media/CozyMediaReceipt.scala`
- `src/main/scala/cozy/media/CozyMediaReviewState.scala`
- `src/main/scala/cozy/media/CozyMediaSummarySlidesPdf.scala`
- `src/main/scala/cozy/media/CozyMediaWarehouse.scala`
- `src/main/scala/cozy/media/CozyVisualPage.scala`
- `src/main/scala/cozy/media/CozyVisualPageParsing.scala`
- `src/main/scala/cozy/media/CozyVisualPagePreview.scala`
- `src/main/scala/cozy/modeler/ComponentStyleCatalog.scala`
- `src/main/scala/cozy/modeler/GenerationProvenance.scala`
- `src/main/scala/cozy/modeler/GenerationProvenanceAggregate.scala`
- `src/main/scala/cozy/modeler/ModelGenerationTarget.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaAssociation.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaBuildContext.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaInfographicCommand.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaInfographicEvidence.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaIntegrity.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaPolicy.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaRegistry.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaSiteBinding.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaVideoEvidence.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaVideoRegistration.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaWarehouseBuildContext.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaWipBinding.scala`
- `src/main/scala/cozy/publication/CozyArticleMediaWipTransaction.scala`
- `src/main/scala/cozy/publication/CozyPublicationCompiler.scala`
- `src/main/scala/cozy/publication/CozySampleDistributor.scala`
- `src/main/scala/cozy/publication/CozyWarehouseIndexer.scala`
- `src/main/scala/cozy/review/CozyCarReviewProvider.scala`
- `src/main/scala/cozy/runtime/CozySbtBridge.scala`
- `src/main/scala/cozy/scaffold/CozyHelpText.scala`
- `src/main/scala/cozy/ui/CozyLogicalUiProjection.scala`
- `src/main/scala/cozy/ui/CozyLogicalUiReview.scala`
- `src/main/scala/cozy/ui/CozyLogicalUiSemantics.scala`
- `src/main/scala/cozy/video/CozyVideoBuildReplay.scala`
- `src/main/scala/cozy/video/CozyVideoCredits.scala`
- `src/main/scala/cozy/video/CozyVideoNarration.scala`
- `src/main/scala/cozy/video/CozyVideoPublisher.scala`
- `src/main/scala/cozy/video/CozyVideoRenderTemplates.scala`
- `src/main/scala/cozy/video/CozyVideoRenderWorkspace.scala`
- `src/main/scala/cozy/video/CozyVideoStoryboardArtifacts.scala`
- `src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala`
- `src/main/scala/cozy/web/arcadia/CozyPlatformContext.scala`

### Existing specification candidates

- `src/test/scala/cozy/media/CozyMediaSummarySlidesPdfSpec.scala`
- `src/test/scala/cozy/media/CozyExplanationPreviewSpec.scala`
- `src/test/scala/cozy/media/CozyMediaWarehouseDeclarationAcceptanceSpec.scala`
- `src/test/scala/cozy/media/CozyMediaWarehouseSpec.scala`
- `src/test/scala/cozy/media/CozyMediaPdfReviewStateSpec.scala`
- `src/test/scala/cozy/media/CozyVisualPageSpec.scala`
- `src/test/scala/cozy/media/CozyVisualPageBindingSpec.scala`
- `src/test/scala/cozy/media/CozyVisualPagePreviewSpec.scala`
- `src/test/scala/cozy/media/CozyMediaVideoBuildSpec.scala`
- `src/test/scala/cozy/media/CozyMediaPublicationSpec.scala`
- `src/test/scala/cozy/media/CozyExplanationSpec.scala`
- `src/test/scala/cozy/media/CozyMediaCrossReviewSpec.scala`
- `src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala`
- `src/test/scala/cozy/media/CozyMediaSpec.scala`
- `src/test/scala/cozy/media/CozyMediaArticleScaffoldSpec.scala`
- `src/test/scala/cozy/media/CozyMediaPresentationSpec.scala`
- `src/test/scala/cozy/media/CozyMediaReceiptSpec.scala`
- `src/test/scala/cozy/media/CozyMediaPresentationMigrationSpec.scala`
- `src/test/scala/cozy/media/CozyMediaWarehouseRootSpec.scala`
- `src/test/scala/cozy/media/CozyMediaWarehouseUploadSpec.scala`
- `src/test/scala/cozy/media/CozyMediaPdfSpec.scala`
- `src/test/scala/cozy/RepositoryArtifactCatalogSpec.scala`
- `src/test/scala/cozy/CozyBokDashboardSpec.scala`
- `src/test/scala/cozy/CozyBokMetadataFinalizationSpec.scala`
- `src/test/scala/cozy/CozyBokKnowledgeSourceSpec.scala`
- `src/test/scala/cozy/PublicationCompilerSpec.scala`
- `src/test/scala/cozy/CozyBokScenarioSpec.scala`
- `src/test/scala/cozy/CozyBokSpec.scala`
- `src/test/scala/cozy/CozyBokMonoKotoSpec.scala`
- `src/test/scala/cozy/CozyBokTermHubSpec.scala`
- `src/test/scala/cozy/CozyBokProjectSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentDescriptionV2Spec.scala`
- `src/test/scala/cozy/document/CozyDocumentDescriptionSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentConfirmationDriverSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentProjectSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentProjectNativeEvidenceSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentDescriptionProjectionSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentProjectSmartDoxArticleHtmlSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentProjectLocalBuildSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentConfirmationVocabularySpec.scala`
- `src/test/scala/cozy/document/CozyDocumentPresentationSemanticsSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentContentCoreSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentProjectLocalBuildCommandSpec.scala`
- `src/test/scala/cozy/document/CozyDocumentConfirmationProjectionV2Spec.scala`
- `src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala`
- `src/test/scala/cozy/CozyBokTermTypeSpec.scala`
- `src/test/scala/cozy/lint/CozyRepositoryLintSpec.scala`
- `src/test/scala/cozy/video/CozyVideoStoryboardBuildSpec.scala`
- `src/test/scala/cozy/video/CozyVideoStoryboardSpec.scala`
- `src/test/scala/cozy/video/CozyVideoCreditsSpec.scala`
- `src/test/scala/cozy/video/CozyVideoMediaEndingSpec.scala`
- `src/test/scala/cozy/video/CozyVideoPublisherAdmissionSpec.scala`
- `src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala`
- `src/test/scala/cozy/video/CozyVideoStoryboardReviewSpec.scala`
- `src/test/scala/cozy/video/CozyVideoStoryboardNarrationSpec.scala`
- `src/test/scala/cozy/video/CozyVideoStoryboardArtifactsSpec.scala`
- `src/test/scala/cozy/CozyBokComponentRepositorySpec.scala`
- `src/test/scala/cozy/ui/CozyLogicalUiPhase43AcceptanceSpec.scala`
- `src/test/scala/cozy/ui/CozyLogicalUiReviewSpec.scala`
- `src/test/scala/cozy/ui/CozyLogicalUiSpec.scala`
- `src/test/scala/cozy/ui/CozyLogicalUiRuntimeSpec.scala`
- `src/test/scala/cozy/ui/CozyLogicalUiSemanticsSpec.scala`
- `src/test/scala/cozy/CozyArchivePackagerSpec.scala`
- `src/test/scala/cozy/CozyBokTagSpec.scala`
- `src/test/scala/cozy/modeler/Phase51Cv05GenerationProvenanceSpec.scala`
- `src/test/scala/cozy/modeler/Phase63McmlGenerationProvenanceSpec.scala`
- `src/test/scala/cozy/modeler/ComponentStyleCatalogSpec.scala`
- `src/test/scala/cozyruntime/CozyLogicalUiRuntimeAcceptanceSpec.scala`
- `src/test/scala/cozyruntime/CozyLogicalUiRuntimeFixtures.scala`
- `src/test/scala/cozy/archive/ComponentRepositoryIndexSpec.scala`
- `src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala`
- `src/test/scala/cozy/archive/ComponentSourceArchiveProjectionSpec.scala`
- `src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala`
- `src/test/scala/cozy/archive/Phase56NamespaceQualifiedCarPublicationSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaAssociationSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaInfographicCommandSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaWarehouseBuildContextSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaIntegritySpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaWipTransactionSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaWipBindingSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaVideoCommandSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaSiteBindingSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaInfographicEvidenceSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaVideoRegistrationSpec.scala`
- `src/test/scala/cozy/publication/CozyDocumentProjectSiteProjectionSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaBuildContextSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaPublicationSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaBokAcceptanceSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaSiteCommandSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaWipCommandSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaSiteRegistrySpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaPublicationOrchestrationSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaRegistrySpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaWarehouseSiteCommandSpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaPolicySpec.scala`
- `src/test/scala/cozy/publication/CozyArticleMediaVideoEvidenceSpec.scala`
- `src/test/scala/cozy/archive/CozyArchivePackagerCv06Spec.scala`
- `src/test/scala/cozy/bok/CozyBokSiteRoutingSpec.scala`
- `src/test/scala/cozy/bok/CozyBokBuildTargetSpec.scala`
- `src/test/scala/cozy/bok/CozyBokNativeSiteDriver.scala`
- `src/test/scala/cozy/bok/CozyBokBibliographySpec.scala`
- `src/test/scala/cozy/bok/scenario/CozyBokScenarioModelSpec.scala`
- `src/test/scala/cozy/bok/CozyBokPublicArticleCatalogueSpec.scala`
- `src/test/scala/cozy/bok/CozyBokArcadiaCategoryOwnershipSpec.scala`
