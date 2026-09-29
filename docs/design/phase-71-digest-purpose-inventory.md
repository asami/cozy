# Phase 71 Digest-Purpose Inventory and Consumer Handoff

Status: frozen P710-01 inventory; removal and retained-integrity implementation pending

Date: 2026-09-29

This handoff records all 46 frozen purpose rows and all 156 covered paths from
the P710-01 global inventory at the accepted Cozy baseline. It is a purpose and
ownership boundary, not an implementation result. A digest remains only when a
named artifact, concrete operation, and integrity guarantee require it. A local
freshness, approval, semantic identity, cache, receipt, or provenance digest is
not retained merely because it is convenient or packaged.

The inventory separates 35 ordinary non-hash classifications from the
distributed CAR checksum path (src/main/catalog/car/cozy.yaml, A01). Mixed
files are split into their independent purposes. The table preserves the
frozen IDs, dispositions, owners, and non-hash preservation instructions;
successor owners remain responsible for implementation and proof.

## Frozen 46-purpose matrix

| ID | Purpose | Disposition | Exclusive owner(s) | Preservation / guarantee |
| --- | --- | --- | --- | --- |
| P71001-OBS-V01 | Human/source/evidence approval gate, not distribution integrity | remove | PHASE-71 P710-02 generation prerequisites and immediate callers; PHASE-71.2 remaining independent video review model/codec consumers; PHASE-71.1 media cross-review consumers | Source path, semantic scene/content validation, external visual inputs and diagnostics remain; no disabled approval switch or fake receipt |
| P71001-OBS-V02 | Local semantic/content identity | remove | PHASE-71 immediate build calls; PHASE-71.2 remaining API/parser/inspection/review callers | Typed normalization, canonical serialization and semantic values remain; equality does not require SHA |
| P71001-OBS-V03 | Local projected-source/handoff identity and cache matching | remove | PHASE-71 native bootstrap handoff/builder path; PHASE-71.2 remaining video handoff/review consumers; PHASE-71.1 media Explanation/cross-review codecs | partId, optional selected section, typed full Storyboard, paths and scoped dependency relation remain |
| P71001-OBS-V04 | Local file/directory/asset freshness; directory recursion hashes file bytes too | remove | PHASE-71 P710-02 selected Storyboard mode/currentness | Configuration/paths/renderer/narration/assets, required-input safety and validity remain explicit; use real dependencies/existence/mtime with conservative regeneration |
| P71001-OBS-V05 | Local generated-output cache/currentness and confirmation binding, not a publication operation | remove | PHASE-71 P710-02 native builder and immediate final-review validator | Valid direct files, prior-output preservation, mode, artifact paths, ffprobe/encoding/narration/execution details remain; final cannot depend on confirmation |
| P71001-OBS-V06 | Local technical/visual review input/evidence identity and currentness | remove | PHASE-71 immediate _validated_storyboard_final_manifest/_review_validated_storyboard_final_manifest caller reconciliation; PHASE-71.2 remaining video review evidence/hash model emission and comparison | Structural/audio/frame/timing/encoding evidence, actual source paths and real mismatch diagnostics remain, not hashes of review files |
| P71001-OBS-V07 | Hash of effective credit semantics and local generated credit metadata | remove | PHASE-71 selected build cache/manifest dependency; PHASE-71.2 remaining credit models/codecs/presentation/build consumers | Actual license/provenance/credit obligations, provider and voice/model identifier text remain |
| P71001-OBS-V08 | Verify exact named renderer resource bytes distributed in Cozy, not local freshness | retain-distribution-integrity-only | PHASE-71.2 must preserve during video removal; PHASE-71.3 retained-integrity acceptance; PHASE-71.4 global audit | Missing or mismatched bundled resource rejected; no local file freshness, source approval or cache decision authorized |
| P71001-OBS-V09 | Local props/part-manifest provenance; same hash source as retained resource verification but not itself verification at a distribution boundary | remove-emission; split from retained loader | PHASE-71.2 video render workspace/template models and props; PHASE-71.3 ensure retain-only-distribution boundary | Renderer/source resource names, template ID and visuals remain; retain loader's actual integrity comparison separately |
| P71001-OBS-V10 | Non-digest provider/model identifiers, ordinary stable IDs, pure identity function | outside artifact-hash removal; preserve non-hash data | PHASE-71.2 typed model edits must distinguish these; PHASE-71.4 lexical false-positive audit | Do not remove an identifier merely because its name contains identity |
| P71001-D01 | Local Core, feedback, proposal, acceptance and prior/resulting-source currentness | remove | PHASE-71.1 | Keep logical meaning, Core IDs, typed validation, safe atomic authoring/failure preservation; ignore obsolete hash extras through ordinary loading, not a compatibility hash codec. |
| P71001-D02 | Localized Document/Summary, Core bindings and local projection identities | remove | PHASE-71.1 | Preserve locale, CoreRef IDs, authored prose/concise meaning, mappings, non-hash metadata; no distinct compatibility hash reader. |
| P71001-D03 | Local alignment, attempt/production receipts, executability and provider source/output currentness | remove | PHASE-71.1 | Keep real successful operations, typed work-product roles, path/format validation, actual artifacts, resumable failure evidence. 'publicSource' is still local freshness. |
| P71001-D04 | Local presentation/source/assets/catalog/HTML/cross-media identities and review acceptance | remove | PHASE-71.1 | Preserve valid declared semantics/assets and optional presentation. Approval is not generation. |
| P71001-D05 | Project-authority currentness and selection/production bindings in export | remove | PHASE-71.1 | Split from D06. Do not retain local authority hashes merely because an export is produced. Preserve target/role/path and atomic bundle creation. |
| P71001-D06 | Distributed article-review export bundle integrity, independent of Document Project authority | retain-only-integrity | PHASE-71.1 split; PHASE-71.3 retained acceptance | verifyBundle detects byte changes in manifest.yaml and work-products/article-review-html/article-review.html declared by receipt.yaml without consulting the source project. |
| P71001-M01 | Local media resource/input-set/build/production receipts and approval snapshots | remove | PHASE-71.1; PHASE-71.3 site/PDF adapters | Maintain selected direct inputs, actual validity and prior-output safety; eliminate captured input hashes and local artifact QA identities. Distribution copy integrity is M06, not this receipt. |
| P71001-M02 | Explanation/VisualPage source, asset, catalog, projection, binding and preview content identities | remove | PHASE-71.1 | Preserve ordinary IDs, mappings, scene/page semantics, asset paths/validity; full codec/serialization deletion. M04 protects actual PNG codec CRC, not these identities. |
| P71001-M03 | Local legacy-migration digest gate | remove | PHASE-71.1 | Keep complete semantic migration map, typed validation, safe atomic output; remove digest-pinning of the local old source and emitted hash. |
| P71001-M04 | PNG on-wire required chunk CRC, not artifact-management identity/currentness | outside-artifact-management-preserve | PHASE-71.1 preserve; PHASE-71.4 audit | PNG IHDR/IDAT/IEND chunk format encodes CRC over chunk type+payload so the generated PNG is a valid interoperable artifact. |
| P71001-M05 | Local presentation/PDF/slide/montage/template/assets and cross-review QA identities | remove | PHASE-71.1; PHASE-71.3 complete PDF/site acceptance | Keep page count, role, safe paths, image/PDF/PPTX structure, actual declared media mapping and failure preservation. Hashing embedded PPTX assets for local QA is not distribution admission. |
| P71001-M06 | Named media publication source→temporary→destination byte integrity | retain-only-integrity | PHASE-71.3 | Atomic installation of the selected publishable media artifact checks copied temporary bytes match the source before public destination replacement. |
| P71001-A01 | CAR/SAR release archive, repository sidecar, catalog and index integrity | retain-only-integrity | PHASE-71.3 acceptance; PHASE-71.4 audit | Named canonical published .car/.sar bytes match sidecar, catalog coordinate integrityKey and index; repository admission rejects mismatches. |
| P71001-A02 | Subcomponent release/composition/archive/integrity/admission bytes | retain-only-integrity | PHASE-71.3 acceptance; PHASE-71.4 audit | Published parent/child CAR composition, release archive, integrity evidence and admission marker must reference the exact embedded/published payloads; validators reject changed bytes. |
| P71001-A03 | ZIP format CRC for stored entries | outside-artifact-management-preserve | PHASE-71.4 audit | Stored ZIP entry CRC/size is required by the ZIP format; no local build cache identity. |
| P71001-A04 | Packaged CAR runtime entry integrity | retain-only-integrity | PHASE-71.3 acceptance; PHASE-71.4 audit | car-runtime-manifest.json verifies every named regular embedded CAR entry against archive bytes and rejects tampering; packaged generation-provenance is opaque runtime bytes. |
| P71001-A05 | Mutable development-root evidence and classpath digest identities | remove | PHASE-71.1 adjacent consumer; PHASE-71.4 audit | Retain declared evidence paths, coordinates, classpath existence, file validity; no compiled/development snapshot hash. Reconcile Cozy-owned producer and explicitly hand off upstream consumer incompatibility, never edit CNCF here. |
| P71001-A06 | Standalone project source inventory and current-source/build comparisons | remove | PHASE-71.1 | Standalone source-archive is only a local inventory, not a packaged byte verifier. Preserve source selection/non-hash build semantics. Split payload manifest integrity in A07 from current-project freshness. |
| P71001-A07 | Admitted release-source/Scaladoc payload manifest byte integrity for CAR packaging | retain-only-integrity | PHASE-71.1 split; PHASE-71.3 acceptance | Named source/<payload>, source/release-source-manifest.json and packaged Scaladoc entries match the admitted payload inventory before insertion into the CAR; no claim about current authored source. |
| P71001-A08 | Component knowledge carrier transport integrity in a CAR | retain-only-integrity | PHASE-71.1 split; PHASE-71.3 acceptance | Archive logical entry component-knowledge.json (declared ARCHIVE_LOGICAL_PATH) must match componentKnowledge declaration before repository pages consume it. |
| P71001-A09 | API ABI content-hash filtering and required digest contract in local dependency selection | remove | PHASE-71.1 adjacent consumer; PHASE-71.4 audit | Keep exact coordinates, API class/version, artifactPath, required/optional providers and ambiguity diagnostics; this comparator does not verify API jar bytes. External CNCF descriptor authority is read-only and any required upstream migration is explicit handoff, not invented replacement ABI semantics. |
| P71001-G01 | CML source attribution and local generation provenance/revalidation digests | remove | PHASE-71.1 | Keep source paths/line/authority IDs, target/generator versions, actual output set, diagnostics and direct-input update safety; no source hash argument/strict old hash model/diagnostic-only calculation. Packaged CAR payloads are protected by A04, not source-freshness fields. |
| P71001-G02 | Redundant packaged provenance claim self-hashes, not verification of actual artifact bytes | remove | PHASE-71.1 removal; PHASE-71.3 actual CAR entry-integrity acceptance via A04 only | requireValidPackagedEvidence checks consistency of declared hash strings, not actual generated artifact bytes. Remove this self-hashing model/helper/codec as well; retain non-hash provenance. Actual generation-provenance.json CAR entry byte integrity remains A04. Never retain provenance hashing merely because it is packaged. |
| P71001-G03 | Selected distributed CNCF runtime descriptor and component-style resource integrity pin | retain-only-integrity | PHASE-71.1 split; PHASE-71.3 acceptance | Explicit selected CNCF descriptor pin is compared to selected distributed descriptor bytes; style catalog's declared resource SHA and byte count verify the named CNCF-distributed catalog before decoding. |
| P71001-U01 | Logical UI local candidate/semantic/projection/catalog/review identities | remove | PHASE-71.1 adjacent consumer | Preserve component coordinates, export/use-case IDs, realizations, structure, validity and source semantics. Ordinary historic loading does not validate/reemit old content hashes. |
| P71001-C01 | Project/site mutable configuration FileEvidence snapshot SHA | remove | PHASE-71.1 ProjectContext; PHASE-71.3 site binding consumers | Keep safe lexical/real paths, fileKey/size, direct-file checks, data and consistent reads; do not replace race safety with hash. Preserve independent input validity. |
| P71001-P01 | Local registry CAS snapshot, bundle identity and build-context cache key | remove | PHASE-71.3 | Publication placement is not an integrity purpose. Keep synchronization, ownership, atomic install/rollback, non-hash registry data and safe paths; retained actual media checks are P03. |
| P71001-P02 | Local WIP/source/render/descriptor/currentness evidence | remove | PHASE-71.3; PHASE-71.2 video source emission | Preserve exact source/role/location validity, real supplied artifact selection and prior-output safety. Copy/backup/restore byte integrity is P04; publication record artifact digest is P03. |
| P71001-P03 | Named article/media/video published artifact, registry and repository record integrity | retain-only-integrity | PHASE-71.3 | Selected public/repository article media artifact must match its declared role/path/registry checksum and staged/published bytes; mismatch rejects distribution admission. |
| P71001-P04 | Publication/WIP copy, backup and restore byte-integrity safety | retain-only-integrity | PHASE-71.3 | The admitted selected media installed in named publication/WIP destination and restored prior destination remain byte-exact through the atomic publication transaction. |
| P71001-P05 | Publication source-manifest inventory of local authored files | remove | PHASE-71.3 | No consumer verifies distribution bytes from these local-source digests. Keep paths/size/coordinate/publication metadata; actual release artifacts retain A01/P06. |
| P71001-P06 | Distributed warehouse/BoK artifact checksum metadata | retain-only-integrity | PHASE-71.3 | Named warehouse/library/CAR/SAR artifact bytes and published catalog metadata/sidecars carry consumer-verifiable checksums; BoK CAR archive is verified against canonical record. |
| P71001-P07 | Generated website local build→staging checksum-based rsync selection | remove | PHASE-71.3 | This local staging operation uses checksum for copy/reuse selection, not an independent integrity verifier; retain selected copy/deletion ownership and path protections, no mutation of actual SMorg scripts in PH71. |
| P71001-R01 | Local review provider request/target/evidence digest bindings | remove | PHASE-71.1 adjacent consumer | These are request/review identity binding, not CAR/archive bytes. Preserve review IDs/rules/provider/limits/targets/non-hash evidence. CBD-owned external schema migration must be explicit downstream handoff, never edited here. |
| P71001-V11 | Remaining local video script/audio/replay/transcription/credits/RDF identities and fields | remove | PHASE-71.2 | Preserve actual IDs/voice/model/provider identity, spoken/display text and encoding/timing evidence; remove unused imports and diagnostic-only fields too. |
| P71001-V12 | Approval/hash scaffolds and help/configuration descriptions | remove | PHASE-71 P710-02 required generation scaffold; PHASE-71.2 remaining video; PHASE-71.1 provenance/help; PHASE-71.3 site help | No new generated obsolete hashes or approval gates. Preserve ordinary project identity templates. |

## Retained integrity rows: artifact, operation, guarantee

The following retained rows are the explicit integrity exceptions. They are not
local currentness decisions, source approval, or generation selectors.

| ID | Named artifact | Concrete operation | Integrity guarantee | Owner |
| --- | --- | --- | --- | --- |
| P71001-OBS-V08 | /cozy/video/remotion/DialogueVideo.jsx; /cozy/video/remotion/DiagramLayout.js | Load each classpath resource with getResourceAsStream and compare bytes to the fixed distributed-template contract before renderer workspace use. | Missing or mismatched bundled resource is rejected; no local freshness, source approval, or cache decision is authorized. | PHASE-71.2 preserve; PHASE-71.3 acceptance; PHASE-71.4 audit |
| P71001-D06 | manifest.yaml and work-products/article-review-html/article-review.html named by receipt.yaml | verifyBundle compares the distributed bundle without consulting the source project. | Byte changes in the manifest or declared HTML are detected. | PHASE-71.1 split; PHASE-71.3 acceptance |
| P71001-M04 | Generated PNG IHDR/IDAT/IEND chunks | PNG codec writes and checks the required CRC over each chunk type and payload. | The PNG remains a valid interoperable on-wire artifact; this is not an artifact-management identity. | PHASE-71.1 preserve; PHASE-71.4 audit |
| P71001-M06 | Named media source, temporary installation, and destination | Atomic installation compares copied temporary bytes to the selected source before destination replacement. | The selected publishable media bytes are unchanged across the publication boundary. | PHASE-71.3 |
| P71001-A01 | Canonical published .car/.sar, sidecar, catalog coordinate, and repository index | Repository admission compares archive bytes with sidecar/checksum, integrityKey, and index metadata. | Published release bytes match their consumer-verifiable release records; mismatches are rejected. | PHASE-71.3 acceptance; PHASE-71.4 audit |
| P71001-A02 | Published parent/child CAR composition and release archives | Composition and admission validators compare embedded/published payloads and integrity evidence. | Parent/child roles and exact release payload bytes remain consistent; changed bytes are rejected. | PHASE-71.3 acceptance; PHASE-71.4 audit |
| P71001-A03 | Stored ZIP entries | ZIP codec verifies stored-entry CRC and size as required by the format. | Archive structure remains interoperable; no local build-cache identity is retained. | PHASE-71.4 audit |
| P71001-A04 | Named regular entries in a packaged CAR, including generation-provenance.json as opaque runtime bytes | car-runtime-manifest.json verifies each named embedded entry against archive bytes. | Packaged CAR entry tampering is rejected; provenance self-hashes are not retained. | PHASE-71.3 acceptance; PHASE-71.4 audit |
| P71001-A07 | Admitted source/<payload>, source/release-source-manifest.json, and packaged Scaladoc entries | Compare named payload bytes with the admitted payload inventory before CAR insertion. | The admitted release-source/Scaladoc payload is intact; this does not assert current authored source. | PHASE-71.1 split; PHASE-71.3 acceptance |
| P71001-A08 | CAR logical entry component-knowledge.json at ARCHIVE_LOGICAL_PATH | The component-knowledge carrier loader compares the archive entry with its declaration before repository pages consume it. | Component knowledge transport bytes match the declared carrier. | PHASE-71.1 split; PHASE-71.3 acceptance |
| P71001-G03 | Selected distributed CNCF runtime descriptor and named component-style catalog resource | Compare an explicitly selected descriptor pin and the catalog's declared resource SHA/byte count before decoding. | The selected distributed descriptor/catalog bytes are verified; no unsolicited local freshness digest is authorized. | PHASE-71.1 split; PHASE-71.3 acceptance |
| P71001-P03 | Named public/repository article, media, or video artifact and its registry record | Compare declared role/path checksum with staged and published bytes at distribution admission. | The selected distributed artifact matches its registry/repository record; mismatch rejects admission. | PHASE-71.3 |
| P71001-P04 | Selected media at publication/WIP destination and restored prior destination | Compare transient source, destination, backup, and restore bytes inside the atomic transaction. | The admitted media and restored prior destination remain byte-exact. | PHASE-71.3 |
| P71001-P06 | Named warehouse/library/CAR/SAR artifact and published catalog/sidecar metadata | Preserve consumer-verifiable artifact checksums and verify the canonical BoK CAR record. | Distributed artifact bytes match their published checksum metadata; legacy SHA1/MD5 remain transport data only. | PHASE-71.3 |

M04 and A03 are required PNG/ZIP wire CRCs, not local artifact identities.
The retained renderer-template bundle hash is distinct from any emitted local
template hash. A04 is the actual CAR entry checksum; G02 packaged
generation-provenance self-hashes are removed, not retained.

## Consumer, field, model, codec, configuration, scaffold, and test ledger

This compact ledger carries the frozen field/model/codec/configuration/scaffold
and test-consumer references for every purpose row. Source paths and test
candidates are evidence, not a mutation authorization; each row's exclusive
owner above controls later work.

| ID | Fields/models/codecs/configuration named by the inventory | Test consumers/candidates | Production consumers |
| --- | --- | --- | --- |
| P71001-OBS-V01 | storyboardReview.approvedIdentity, storyboardReview.visualStory.approvedEvidenceIdentity, storyboardReview.visualPage.approvedEvidenceIdentity, confirmationReview.approvedIdentity |  | src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala:119-143,465-501, src/main/scala/cozy/video/CozyVideoStoryboardReview.scala:331-347 |
| P71001-OBS-V02 | StoryboardResult.semanticIdentity, storyboardIdentity API, normalized Storyboard SHA |  | src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala:139-146, src/main/scala/cozy/video/CozyVideoStoryboardReview.scala:341 |
| P71001-OBS-V03 | handoff.storyboardIdentity, handoff.projectedStoryboardIdentity, handoff.identity, modeManifest.storyboards[].storyboardIdentity, modeManifest.storyboards[].projectedStoryboardIdentity, modeManifest.handoffs[].identity |  | src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala:483,562-574, src/main/scala/cozy/media/CozyExplanationProjectionCodec.scala:125-132,232, src/main/scala/cozy/media/CozyMediaCrossReview.scala:128-129,166-197 |
| P71001-OBS-V04 | cacheInputIdentity, cacheInput.parts[].script.identity, cacheInput.parts[].steps.identity, cacheInput.parts[].recording.identity, cacheInput.parts[].output.identity, cacheInput.assets[].contentIdentity, cacheInput.storyboardAssets[].contentIdentity |  | src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala:51-70,562-604,689,705,728 |
| P71001-OBS-V05 | modeManifest.identity, modeManifest.output.sha256, modeManifest.partArtifacts[].output.sha256, modeManifest.partArtifacts[].manifest.identity, storyboardPartArtifact.identity, storyboardPartArtifact.output.sha256 |  | src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala:486-500,505-524,562-574 |
| P71001-OBS-V06 | finalhash argument in _validated_storyboard_final_manifest, reviewManifest.finalVideo.sha256, reviewManifest.videoManifest.sha256, projectFile.sha256, propsSha256, audioManifestSha256, review frames[].sha256 |  | src/main/scala/cozy/video/CozyVideoReviewEvidence.scala:477-506, src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala:505-524 |
| P71001-OBS-V07 | EffectiveSet.digest, OutputFiles.digest, credits.json.digest, creditDigest, cacheInput.creditsIdentity |  | src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala:245,589, src/main/scala/cozy/video/CozyVideoBuildReplay.scala:205, src/main/scala/cozy/video/CozyVideoPresentation.scala:136 |
| P71001-OBS-V08 | _character_dialogue_template_sha256, _character_dialogue_diagram_layout_sha256, _load_character_dialogue_template_resource expectedsha256/actualsha256 |  | src/main/scala/cozy/video/CozyVideoRenderWorkspace.scala:229-249 |
| P71001-OBS-V09 | rendererTemplateSha256, rendererTemplateResources[].sha256, CharacterDialogueTemplateResource.sha256 when solely re-emitted |  | src/test/scala/cozy/video/CozyVideoSpec.scala:2312,2420 |
| P71001-OBS-V10 | voiceIdentity, modelIdentity, scene.id, partId, identity function, groupBy(identity) |  | src/main/scala/cozy/video/CozyVideoCredits.scala:499-507 |
| P71001-D01 | coreIdentity, formatIdentity, sha256, acceptance and feedback identities | src/test/scala/cozy/document/CozyDocumentLogicTreeSpec.scala | Description V1/V2, LogicTreeProjection, ProjectProvider/Evidence |
| P71001-D02 | sourceIdentity, descriptionIdentity, summaryIdentity, coreIdentity, sha256, catalog/projection identities | src/test/scala/cozy/document/CozyDocumentConfirmationProjectionV2Spec.scala, src/test/scala/cozy/document/CozyDocumentDescriptionProjectionSpec.scala, src/test/scala/cozy/document/CozyDocumentDescriptionSpec.scala, src/test/scala/cozy/document/CozyDocumentDescriptionV2Spec.scala, src/test/scala/cozy/document/CozyDocumentReaderProjectionV2Spec.scala, src/test/scala/cozy/document/CozySummaryConfirmationProjectionV2Spec.scala, src/test/scala/cozy/document/CozySummarySlideProjectionSpec.scala | Project/local HTML/media projection, CrossMediaConfirmation |
| P71001-D03 | FileIdentity.sha256, source/output/receipt identities, native receipt value, publicSource.sha256 | src/test/scala/cozy/document/CozyDocumentProjectNativeEvidenceSpec.scala, src/test/scala/cozy/document/CozyDocumentProjectSpec.scala | Document command verify/build/export, site projection, project status |
| P71001-D04 | source.sha256, asset.sha256, logical/catalog/binding/projection/preview identities | src/test/scala/cozy/document/CozyDocumentPresentationSemanticsSpec.scala | VisualPage/Explanation, Document HTML and confirmation |
| P71001-D05 | sourceauthoritysha256, selectionsha256, retainedproductionreceiptsha256, export currentness source/selection/production hashes | src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala | export, currentness, strict receipt loading |
| P71001-D06 | manifestsha256, outputsha256, manifest.output.sha256 | src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala | verifyBundle, _bundle_components |
| P71001-M01 | inputSetSha256, reviewManifestSha256, artifactSetSha256, resource/artifact/sourceSha256, rendererManifestSha256 | src/test/scala/cozy/media/CozyMediaPdfReviewStateSpec.scala, src/test/scala/cozy/media/CozyMediaReceiptSpec.scala, src/test/scala/cozy/media/CozyMediaSpec.scala | build/verify/review, prepared publication, site/PDF receipt/review adapters |
| P71001-M02 | SourceDeclaration.sha256, AssetDeclaration.sha256, compositionIdentity, planIdentity, catalogIdentity, bindingIdentity, projection/receipt/storyboard identities, html/png/preview identity | src/test/scala/cozy/media/CozyExplanationPreviewSpec.scala, src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala, src/test/scala/cozy/media/CozyExplanationSpec.scala, src/test/scala/cozy/media/CozyVisualPageBindingSpec.scala, src/test/scala/cozy/media/CozyVisualPagePreviewSpec.scala, src/test/scala/cozy/media/CozyVisualPageSpec.scala | presentation/summary PDF, native Storyboard v2 projection, document cross-media |
| P71001-M03 | legacySlideIrSha256, migration report digest | src/test/scala/cozy/media/CozyMediaPresentationMigrationSpec.scala | migrate legacy Slide IR |
| P71001-M04 | CRC32 in _chunk | src/test/scala/cozy/media/CozyVisualPagePreviewSpec.scala | _png -> _chunk |
| P71001-M05 | slideIrSha256, visualPageSetSha256, catalogSha256, bindingSha256, templateSha256, pptxSha256, pdfSha256, slides/montage/assets.sha256 | src/test/scala/cozy/media/CozyMediaCrossReviewSpec.scala, src/test/scala/cozy/media/CozyMediaPresentationSpec.scala, src/test/scala/cozy/media/CozyMediaSummarySlidesPdfSpec.scala | renderer manifests, review snapshots, local PDF/media validation, Storyboard v2 cross-review |
| P71001-M06 | sourceSha256 used by _install_publication, temporary publication checksum | src/test/scala/cozy/media/CozyMediaSpec.scala | _install_publication, published destination integrity verification |
| P71001-A01 | checksum.sha256, .sha256 sidecar, integrityKey | src/test/scala/cozy/CozyCarPublisherSpec.scala, src/test/scala/cozy/CozySarPublisherSpec.scala, src/test/scala/cozy/RepositoryArtifactCatalogSpec.scala, src/test/scala/cozy/archive/ComponentRepositoryIndexSpec.scala | RepositoryArtifactPublisher, ComponentRepositoryIndex, CozyBokRepositoryPages, legacy archive migration |
| P71001-A02 | composition/member.sha256, releaseSha256, archiveSha256, integritySha256 | src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala | release packaging/admission validators |
| P71001-A03 | CRC32 when encoding ZIP entries | src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala | ZipOutputStream stored entry |
| P71001-A04 | integrity.entries[].sha256 | src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala | requireValid packaged CAR manifest, CozyArchivePackager, CozyCarPublisher |
| P71001-A05 | evidence[].sha256, logicalSha256, integrity.evidenceSha256 |  | CozySbtBridge write development manifest, CNCF development runtime consumer (read-only external contract) |
| P71001-A06 | standalone archiveDigest/Entry.sha256, release-source sourceDigest against currentall, buildEvidenceDigest/current build hash comparison, Scaladoc sourceDigest | src/test/scala/cozy/CozyArchivePackagerSpec.scala, src/test/scala/cozy/archive/ComponentReleaseSourceProjectionSpec.scala, src/test/scala/cozy/archive/ComponentSourceArchiveProjectionSpec.scala | CozySbtBridge write/stage/verify source actions, release source current-project admission, Scaladoc _verify |
| P71001-A07 | admitted payload entries[].sha256, contentDigest | src/test/scala/cozy/CozyArchivePackagerSpec.scala, src/test/scala/cozy/archive/ComponentReleaseSourceProjectionSpec.scala | _require_actual_entries, _validate_manifest, Scaladoc staged content verifier, Verified.archiveEntries -> CAR |
| P71001-A08 | componentKnowledge.sha256 |  | requireDeclaredArchiveCarrier, _repository componentKnowledge carrier verification |
| P71001-A09 | RequiredApi.abiHash, ProvidedApi.abiHash, hash filtering/dedup/nonempty gate | src/test/scala/cozy/ComponentApiDependencyResolverSpec.scala | _resolve_dependencies, _load_provider from component-api-descriptor.json |
| P71001-G01 | Source.sha256, SourceAttribution.sha256, sourceSha256, generatedOutputDigest, evidenceDigest, local artifacts[].sha256, runtimeDescriptorSha256 local generation identity | src/test/scala/cozy/modeler/CmlSemanticFoundationSpec.scala, src/test/scala/cozy/modeler/CmlSemanticMetadataSpec.scala | source snapshot/current validation, source/output packaging readiness, aggregate conflict/currentness, CLI provenance options, CozyCarPublisher/Packager |
| P71001-G02 | generatedOutputDigest, evidenceDigest, source/output/descriptor hashes in packaged provenance | src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala | requireValidPackagedEvidence, _require_valid_packaged_v1, Aggregate.validatePackagedEvidence |
| P71001-G03 | expected descriptor SHA, runtime descriptor sha256 argument/config, component-style catalog resource.sha256 | src/test/scala/cozy/modeler/ComponentStyleCatalogSpec.scala | validateDescriptor, ComponentStyleCatalog load resource |
| P71001-U01 | inputIdentity, candidate identity, semantic/projected identity, receipt identity, htmlSha256, use-case/catalog identities | src/test/scala/cozy/ui/CozyLogicalUiReviewSpec.scala, src/test/scala/cozy/ui/CozyLogicalUiSemanticsSpec.scala, src/test/scala/cozy/ui/CozyLogicalUiSpec.scala | decodeCandidate, candidate/evidence acceptance, HTML/UI review |
| P71001-C01 | FileEvidence.sha256 | src/test/scala/cozy/config/CozyProjectContextSpec.scala, src/test/scala/cozy/publication/CozyArticleMediaSiteBindingSpec.scala | context overlays/value sources, site binding file snapshots |
| P71001-P01 | rawdigest, Snapshot.bundleDigests, expectedBundleDigests, contextDigest, snapshot directory digest | src/test/scala/cozy/publication/CozyArticleMediaBuildContextSpec.scala, src/test/scala/cozy/publication/CozyArticleMediaPolicySpec.scala, src/test/scala/cozy/publication/CozyArticleMediaRegistrySpec.scala | registry transaction/read/rollback/CAS, configure/compile build-context, policy snapshot |
| P71001-P02 | WIP/source/config snapshot.sha256, render/build-manifest binding to local source, video descriptorSha256/scriptSha256, ProducerSource.sha256 | src/test/scala/cozy/publication/CozyArticleMediaInfographicEvidenceSpec.scala, src/test/scala/cozy/publication/CozyArticleMediaSiteBindingSpec.scala, src/test/scala/cozy/publication/CozyArticleMediaWipBindingSpec.scala, src/test/scala/cozy/publication/CozyArticleMediaWipTransactionSpec.scala | WIP repeat/prepared source checks, build-source proof, site/video/infographic binding |
| P71001-P03 | Record.sha256, manifest.artifact.sha256, repository registry selectedfile.sha256 | src/test/scala/cozy/publication/CozyArticleMediaAssociationSpec.scala, src/test/scala/cozy/publication/CozyArticleMediaInfographicEvidenceSpec.scala, src/test/scala/cozy/publication/CozyArticleMediaIntegritySpec.scala, src/test/scala/cozy/publication/CozyArticleMediaPolicySpec.scala, src/test/scala/cozy/publication/CozyArticleMediaRegistrySpec.scala, src/test/scala/cozy/publication/CozyArticleMediaVideoEvidenceSpec.scala | VideoEvidence actualsha check, InfographicEvidence destination byte verification, Policy artifact digest check, manifest/registry serializers |
| P71001-P04 | source checksum for copied bytes, temporary checksum, backup/restore byte checksum | src/test/scala/cozy/publication/CozyArticleMediaWipBindingSpec.scala, src/test/scala/cozy/publication/CozyArticleMediaWipTransactionSpec.scala | WIP staging/commit/rollback, VideoPublisher _install |
| P71001-P05 | SourceFile.sha256, project.descriptorSha256 |  | metadata/source-manifest generation, BoK project metadata |
| P71001-P06 | artifact sha256, sidecar sha1/md5, version checksumsha256 |  | warehouse index/artifact metadata, repository CAR pages/admission |
| P71001-P07 | rsync --checksum |  | generated build/staging script |
| P71001-R01 | requestDigest, bundleDigest, Target.digest | src/test/scala/cozy/review/CozyCarReviewProviderSpec.scala | provider-request parser, evidence bundle serializer, bundleDigest helper |
| P71001-V11 | sourceSha256, inputSha256, finalVideoSha256, propsSha256, audioManifestSha256, creditDigest, unused MessageDigest imports | src/test/scala/cozy/video/CozyVideoNarrationSpec.scala | native replay/audio/transcription/inspect/RDF/build, local rendering/narration |
| P71001-V12 | approvedIdentity, hash-oriented help, generation-source-sha256 option, migration digest help | src/test/scala/cozy/video/CozyVideoScaffoldSpec.scala | new project scaffold, CLI help |

Rows that mix a retained loader with removed emission are split: OBS-V08
retains only the named renderer resource comparison while OBS-V09 removes
props/manifest emission; D05 removes local project-authority hashes while D06
retains distributed article-review bundle verification; A06 removes standalone
source currentness while A07 retains admitted release payload integrity; and
G01 removes source/provenance freshness while A04 protects actual CAR entry
bytes. No compatibility codec, disabled branch, diagnostic-only calculation,
or speculative receipt/metadata framework is a successor workaround.

## Cross-repository and historical boundaries

Three external edges remain read-only and require an explicit handoff before any
external mutation:

1. The CNCF development-runtime manifest reader consumes Cozy's producer; the
   Cozy side removes local hashes and keeps exact unchanged paths/non-hash
   metadata. Any actual upstream change must be reported before mutation.
2. CNCF component API descriptor/required-provider matching removes
   abiHash-dependent selection while preserving coordinates, API
   class/version, artifact path, and ambiguity behavior. External implementation
   is not authorized here.
3. The CBD review request/evidence JSON contract removes request/evidence hash
   binding while preserving non-hash review semantics. Any external migration
   need must be reported before external mutation.

Removed hash extras may be ignored or dropped by ordinary document loading. No
dedicated historical hash parser, validation, calculation, comparison,
re-emission, disabled flag, diagnostic-only hash, replacement receipt, or
speculative metadata framework is admitted. Local source/currentness parts of
release-source/Scaladoc differ from admitted payload integrity. Publication
placement or naming alone does not justify retention.

## Complete 156-path coverage

Every path below is present in the frozen inventory. Purpose IDs identify the
applicable row; dash entries are the 35 ordinary non-hash classifications plus
the separately identified A01 CAR checksum path.

| Covered path | Purpose ID(s) | Non-hash classification or purpose-row reference |
| --- | --- | --- |
| `src/main/catalog/car/cozy.yaml` | — | existing distributed CAR checksum, A01 |
| `src/main/resources/cozy/antora-ui/js/site.js` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/Cozy.scala` | P71001-G01 | See purpose row(s) |
| `src/main/scala/cozy/RepositoryArtifactCatalog.scala` | P71001-A01 | See purpose row(s) |
| `src/main/scala/cozy/archive/ComponentApiDependencyResolver.scala` | P71001-A09 | See purpose row(s) |
| `src/main/scala/cozy/archive/ComponentReleaseSourceProjection.scala` | P71001-A06, P71001-A07 | See purpose row(s) |
| `src/main/scala/cozy/archive/ComponentRepositoryIndex.scala` | P71001-A01 | See purpose row(s) |
| `src/main/scala/cozy/archive/ComponentSourceArchiveProjection.scala` | P71001-A06 | See purpose row(s) |
| `src/main/scala/cozy/archive/CozyArchivePackager.scala` | P71001-A06, P71001-A07 | See purpose row(s) |
| `src/main/scala/cozy/archive/CozyCarAbiManifest.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/archive/CozyCarPublisher.scala` | P71001-A01 | See purpose row(s) |
| `src/main/scala/cozy/archive/CozyCarRuntimeManifest.scala` | P71001-A04, P71001-G02 | See purpose row(s) |
| `src/main/scala/cozy/archive/CozyComponentKnowledgeCarrier.scala` | P71001-A08 | See purpose row(s) |
| `src/main/scala/cozy/archive/CozyComponentReleaseCoordinateCodec.scala` | P71001-A01 | See purpose row(s) |
| `src/main/scala/cozy/archive/CozyDevelopmentRuntimeManifest.scala` | P71001-A05 | See purpose row(s) |
| `src/main/scala/cozy/archive/CozySarPublisher.scala` | P71001-A01 | See purpose row(s) |
| `src/main/scala/cozy/archive/LegacyComponentRepositoryMigration.scala` | P71001-A01 | See purpose row(s) |
| `src/main/scala/cozy/archive/RepositoryArtifactCatalog.scala` | P71001-A01 | See purpose row(s) |
| `src/main/scala/cozy/archive/RepositoryArtifactPublisher.scala` | P71001-A01 | See purpose row(s) |
| `src/main/scala/cozy/archive/SubcomponentReleasePackaging.scala` | P71001-A02, P71001-A03 | See purpose row(s) |
| `src/main/scala/cozy/bok/CozyBokProjectPublisher.scala` | P71001-P05, P71001-P06 | See purpose row(s) |
| `src/main/scala/cozy/bok/CozyBokPublication.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/bok/CozyBokRdfViewer.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala` | P71001-P06 | See purpose row(s) |
| `src/main/scala/cozy/bok/CozyBokRepositoryPages.scala` | P71001-A08, P71001-P06 | See purpose row(s) |
| `src/main/scala/cozy/bok/CozyBokSiteDocument.scala` | P71001-P07 | See purpose row(s) |
| `src/main/scala/cozy/bok/CozyBokUiAssets.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/compatibility/CarMetadataCompatibility.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/compatibility/CncfRuntimeDescriptorContract.scala` | P71001-G03 | See purpose row(s) |
| `src/main/scala/cozy/config/CozyProjectContext.scala` | P71001-C01 | See purpose row(s) |
| `src/main/scala/cozy/config/CozyProjectYamlConfig.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/document/CozyDocumentConfirmationCommand.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentConfirmationProjectionV2.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentConfirmationVocabulary.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentContentCore.scala` | P71001-D01 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentCrossMediaConfirmationHtml.scala` | P71001-D04 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentCrossMediaProjection.scala` | P71001-D04 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentCrossMediaReceipt.scala` | P71001-D04 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentDescription.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentDescriptionCommand.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentDescriptionProjection.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentDescriptionV2.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentLogicTree.scala` | P71001-D01 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentLogicTreeCommand.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentLogicTreeProjection.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentPresentationSemantics.scala` | P71001-D04 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProject.scala` | P71001-D03 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectAlignment.scala` | P71001-D03 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectEvidence.scala` | P71001-D03 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectExecutability.scala` | P71001-D03 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectExport.scala` | P71001-D05, P71001-D06 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectNativeEvidence.scala` | P71001-D03 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectPresentationSemanticsState.scala` | P71001-D04 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectProjection.scala` | P71001-D03 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectProvider.scala` | P71001-D03 | See purpose row(s) |
| `src/main/scala/cozy/document/CozyDocumentProjectSmartDoxArticleHtml.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/document/CozyDocumentReaderProjectionV2.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozySummaryConfirmationProjectionV2.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/document/CozySummarySlideProjection.scala` | P71001-D02 | See purpose row(s) |
| `src/main/scala/cozy/lint/CozyCarIdentityLint.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/lint/CozyCarLint.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/media/CozyExplanation.scala` | P71001-M02 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyExplanationCodec.scala` | P71001-M02 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyExplanationJson.scala` | P71001-M02 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyExplanationPreview.scala` | P71001-M02 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyExplanationProjection.scala` | P71001-M02 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyExplanationProjectionCodec.scala` | P71001-M02, P71001-OBS-V03 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMedia.scala` | P71001-M01, P71001-M06 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaCrossReview.scala` | P71001-M05, P71001-OBS-V03 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaPdf.scala` | P71001-M01 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaPdfReviewState.scala` | P71001-M01 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaPresentation.scala` | P71001-M05 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaPresentationMigration.scala` | P71001-M03 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaPublicationTransaction.scala` | P71001-M06 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaReceipt.scala` | P71001-M01 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaReviewState.scala` | P71001-M01 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyMediaSummarySlidesPdf.scala` | P71001-M05 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyVisualPage.scala` | P71001-M02 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyVisualPageBinding.scala` | P71001-M02 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyVisualPageParsing.scala` | P71001-M02 | See purpose row(s) |
| `src/main/scala/cozy/media/CozyVisualPagePreview.scala` | P71001-M02, P71001-M04 | See purpose row(s) |
| `src/main/scala/cozy/modeler/CandidateAdmissionProducerAbiGenerator.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/CmlModelMetadata.scala` | P71001-G01 | See purpose row(s) |
| `src/main/scala/cozy/modeler/CmlSemanticFoundation.scala` | P71001-G01 | See purpose row(s) |
| `src/main/scala/cozy/modeler/CmlSemanticMetadata.scala` | P71001-G01 | See purpose row(s) |
| `src/main/scala/cozy/modeler/CmlSemanticMetadataReader.scala` | P71001-G01 | See purpose row(s) |
| `src/main/scala/cozy/modeler/CmlStructureMetadataCodec.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/ComponentStyleCatalog.scala` | P71001-G03 | See purpose row(s) |
| `src/main/scala/cozy/modeler/CompositeStateMachineActionProducerMetadata.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/CompositeStateMachineActionProgram.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/CompositeStateMachineCml.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/CompositeStateMachineDefinition.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/CompositeStateMachineProjectionMetadata.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/CompositeStateMachineScalaGenerator.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/GenerationProvenance.scala` | P71001-G01, P71001-G02 | See purpose row(s) |
| `src/main/scala/cozy/modeler/GenerationProvenanceAggregate.scala` | P71001-G01, P71001-G02 | See purpose row(s) |
| `src/main/scala/cozy/modeler/ModelTypeProjector.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/PredefinedScalarCatalog.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/ProjectIdentityAdapter.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/ProjectIdentityContractScenarioSpi.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/StateMachineApiSpi.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/StateMachineNormalizationProjector.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/StateMachineProvidedApiAbiGenerator.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/publication/CozyArticleMediaAssociation.scala` | P71001-P03 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaBuildContext.scala` | P71001-P01 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaInfographicCommand.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/publication/CozyArticleMediaInfographicEvidence.scala` | P71001-P02, P71001-P03 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaIntegrity.scala` | P71001-P03 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaNormalization.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/publication/CozyArticleMediaPolicy.scala` | P71001-P01, P71001-P03 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaPublication.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/publication/CozyArticleMediaRegistry.scala` | P71001-P01, P71001-P03 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaSiteBinding.scala` | P71001-C01, P71001-P02 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaSiteCommand.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/publication/CozyArticleMediaVideoCommand.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/publication/CozyArticleMediaVideoEvidence.scala` | P71001-P03 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaVideoRegistration.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/publication/CozyArticleMediaWipBinding.scala` | P71001-P02, P71001-P04 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyArticleMediaWipCommand.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/publication/CozyArticleMediaWipTransaction.scala` | P71001-P02, P71001-P04 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyPublicationCompiler.scala` | P71001-P01, P71001-P05 | See purpose row(s) |
| `src/main/scala/cozy/publication/CozyWarehouseIndexer.scala` | P71001-P06 | See purpose row(s) |
| `src/main/scala/cozy/review/CozyCarReviewProvider.scala` | P71001-R01 | See purpose row(s) |
| `src/main/scala/cozy/runtime/CozySbtBridge.scala` | P71001-G01, P71001-G03 | See purpose row(s) |
| `src/main/scala/cozy/scaffold/CozyHelpText.scala` | P71001-V12 | See purpose row(s) |
| `src/main/scala/cozy/scaffold/CozyScaffold.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/scaffold/CozyScaffoldComponentTemplates.scala` | — | ordinary semantic IDs/coordinates/provider/renderer IDs, Scala collections/identity, URL fragments, vocabulary or non-hash callers; preserve; reconcile any compiler edge to removed contracts |
| `src/main/scala/cozy/ui/CozyLogicalUi.scala` | P71001-U01 | See purpose row(s) |
| `src/main/scala/cozy/ui/CozyLogicalUiCodec.scala` | P71001-U01 | See purpose row(s) |
| `src/main/scala/cozy/ui/CozyLogicalUiProjection.scala` | P71001-U01 | See purpose row(s) |
| `src/main/scala/cozy/ui/CozyLogicalUiReview.scala` | P71001-U01 | See purpose row(s) |
| `src/main/scala/cozy/ui/CozyLogicalUiSemantics.scala` | P71001-U01 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideo.scala` | P71001-OBS-V02 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoBuildReplay.scala` | P71001-V11, P71001-OBS-V07 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoCommand.scala` | P71001-V11 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoConfig.scala` | P71001-V11 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoCredits.scala` | P71001-OBS-V07, P71001-OBS-V10 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoModel.scala` | P71001-V11, P71001-OBS-V01, P71001-OBS-V10 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoNarration.scala` | P71001-V11 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoPlanning.scala` | P71001-V11 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoPresentation.scala` | P71001-V11, P71001-OBS-V07 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoPublisher.scala` | P71001-P02, P71001-P03, P71001-P04 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoRdf.scala` | P71001-V11 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoRenderTemplates.scala` | P71001-OBS-V08, P71001-OBS-V09 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoRenderWorkspace.scala` | P71001-OBS-V08, P71001-OBS-V09 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoReviewEvidence.scala` | P71001-OBS-V06 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoRuntime.scala` | P71001-V11 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoScaffold.scala` | P71001-V12, P71001-OBS-V01 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoStoryboard.scala` | P71001-OBS-V02 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala` | P71001-OBS-V01, P71001-OBS-V02, P71001-OBS-V03, P71001-OBS-V04, P71001-OBS-V05, P71001-OBS-V06, P71001-OBS-V07 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoStoryboardParsing.scala` | P71001-OBS-V02 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoStoryboardReview.scala` | P71001-OBS-V01, P71001-OBS-V02 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoToolValidation.scala` | P71001-V11 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoTools.scala` | P71001-V11 | See purpose row(s) |
| `src/main/scala/cozy/video/CozyVideoTranscription.scala` | P71001-V11 | See purpose row(s) |

## Ownership and remaining proof

P710-01 freezes these classifications and consumer handoffs. P710-02 owns the
actual private Core -> authored DSL -> nine-scene Storyboard -> generated JA
video driver, including update/reuse/failure scenarios. PHASE-71.1 owns
document/media and adjacent application removal; PHASE-71.2 owns remaining
video removal and continuity; PHASE-71.3 owns site/PDF/prebuilt adapters and
retained-integrity acceptance; PHASE-71.4 owns the global removal audit and
combined-tree acceptance.

No purpose row is accepted merely by this document. Removals, mismatch proofs,
focused validation, independent review, commits, and actual video evidence
remain pending with their named successors.
