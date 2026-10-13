# Cozy enclosing-instance type structure audit

Date: 2026-10-01
Disposition: historical static audit; full compilation subsequently PASS with warnings; broad repair OPEN
Phase 71.1: IN_PROGRESS; no closure, commit or push

## Subsequent clean compilation and correction

The user subsequently supplied a full `clean` / `Test / compile` transcript.
Both compilations passed at 19:44:11 JST; 123 diagnostics were displayed,
including 121 outer-reference warnings. Main diagnostics reached the compiler
limit of 100, so this is not the complete total. The
[clean-compile inventory](2026-10-01-clean-compile-warning-inventory.md) now
governs observed compiler evidence.

That run also disproves the earlier explanation that the two unmatched Spec
fixtures had already moved: `CozySummaryConfirmationProjectionV2Spec.Fixture`
and `CozyExplanationPreviewSpec.Fixture` remain instance-nested and emit
warnings. Their source enclosure was missed by the lexical scan. The static
declaration inventory is corrected from 185 to 187 candidates, with 22 Spec
fixtures. Original counts below describe the initial scan and are retained
historically. Broad structural repair and warning-free acceptance remain OPEN.

## Actual focused execution evidence

The user manually ran `sbt --batch 'testOnly cozy.video.CozyVideoStoryboardReviewSpec'`. At 19:33:10 JST, SBT completed successfully: 16 tests succeeded, 0 failures, 1 suite completed, 0 aborted. Incremental main compilation processed 1 source followed by 16 dependent sources; test compilation processed 3 sources. The previous 9 Storyboard review model warnings and 1 Spec fixture warning disappeared. Three other outer-reference warnings appeared: `SynthesisInput` in `CozyVideoCommand`, and `StoryboardBuildPart` / `StoryboardBuildMode` in `CozyVideoStoryboardBuild`.

Source evidence: `/Users/asami/.codex/attachments/93a1f732-5850-4e57-8de0-ac0e6ad44b7a/貼り付けたテキスト.txt`.

Those three types have subsequently been moved into companion objects by the dedicated Storyboard review chat. That additional repair has not yet been compiled. No warning annotations or compiler-warning configuration changes were introduced.

## Repository-wide inspection method and limits

Read-only inspection covered all 431 main/test Scala files, additional project/sample source declarations, and existing main/test compiled output. A lexical declaration/enclosure inventory was cross-checked against classfile methods. 1,188 compiled case classes were inspected. 190 existing classfiles have the same unchecked outer-reference equality-test bytecode shape as the warning-producing types. Three refer to the just-moved types, and two did not match the lexical source enclosure. The latter two were initially misclassified as moved fixtures; clean compilation later disproved that classification, as corrected above. Only the three just-moved types are excluded from current findings.

The remaining 185 classfiles have matching current-source case-class declarations enclosed in a class or trait: 83 Video, 81 BoK, 1 Modeler, 20 executable Spec fixtures. These are structural candidates, NOT 185 freshly emitted compiler warnings or proven runtime defects. Existing incremental classfiles can be stale. A lexical scan is not a Scala compiler or exhaustive semantic analysis; unmatched source candidates are listed below for follow-up.

No `@nowarn`, `@unchecked`, or `-Wconf` suppression was found in inspected Scala/build sources. `-unchecked` in `build.sbt` enables warning diagnostics; it is not suppression.

## Semantics and priority

Video and BoK model declarations are nested in reusable traits although current production composition uses `CozyVideoImplementation` and `CozyBokImplementation` singleton objects. This means model identity is tied to a trait owner while generated equality does not check that owner. The current singleton composition limits cross-owner exposure; this inspection does not establish an actual cross-owner runtime failure. The structural pattern is nevertheless wider than the ten initial warnings and should not be dismissed based on a passing incremental focused suite.

`StateMachineNormalizationProjector.NormalizationFailure` is enclosed in an ordinary class, extends `RuntimeException`, and is caught with `case failure: NormalizationFailure`. This is an explicit instance-dependent type-test boundary in addition to generated equality and warrants focused examination. No incorrect exception catch across projector instances was demonstrated.

Spec fixtures have separate suite owners, making companion-object placement suitable when they are plain data. Do not fix these findings by suppressing diagnostics or blindly erase deliberately instance-dependent types. Broad model migration must preserve the existing types, constructors, codecs, member behavior and direct consumers.

## Candidate inventory

| Source | Types | Names |
| --- | ---: | --- |
| [src/main/scala/cozy/bok/CozyBokBibliography.scala](../../../../src/main/scala/cozy/bok/CozyBokBibliography.scala) | 1 | `PreservedSieHandoff` (58) |
| [src/main/scala/cozy/bok/CozyBokBuildConfig.scala](../../../../src/main/scala/cozy/bok/CozyBokBuildConfig.scala) | 15 | `BuildConfig` (43), `ArcadiaConfig` (75), `DirectAssetsConfig` (76), `DirectAsset` (77), `PublicationSettings` (78), `PublicationConfig` (91), `WorkflowConfig` (111), `WebsiteBackupConfig` (118), `PublishStep` (119), `WebsiteBackupSnapshot` (120), `BibliographyRdfAlias` (121), `KnowledgeSourceResource` (122), `PublishPreflight` (129), `ParsedArgs` (141), `SiteConfig` (287) |
| [src/main/scala/cozy/bok/CozyBokConfig.scala](../../../../src/main/scala/cozy/bok/CozyBokConfig.scala) | 8 | `CreateConfig` (58), `CategoryConfig` (65), `BokGoal` (75), `BokPurpose` (78), `CategoryArticle` (89), `CategoryTerm` (93), `DoctorConfig` (98), `PreviewConfig` (99) |
| [src/main/scala/cozy/bok/CozyBokGlossaryWorkflow.scala](../../../../src/main/scala/cozy/bok/CozyBokGlossaryWorkflow.scala) | 3 | `WorkflowStage` (87), `WorkflowSourceStatus` (103), `WorkflowExtractionProgress` (105) |
| [src/main/scala/cozy/bok/CozyBokMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokMetadata.scala) | 1 | `CategorySummary` (41) |
| [src/main/scala/cozy/bok/CozyBokModel.scala](../../../../src/main/scala/cozy/bok/CozyBokModel.scala) | 32 | `BokInspection` (41), `BokFix` (56), `DoxMetadataSectionIssue` (57), `MarkdownMetadataIssue` (58), `CategoryContent` (59), `CategoryPageItem` (67), `LocalizedGlossaryItem` (74), `ArticleIndexItem` (81), `DashboardCounts` (89), `DashboardBucket` (95), `DashboardRdfSummary` (104), `DashboardIncrements` (110), `DashboardRecentItem` (114), `DashboardCategory` (121), `BokDashboard` (128), `ScenarioIndex` (135), `ScenarioEntry` (136), `TermIndex` (157), `TermEntry` (158), `TermReference` (198), `TermRdfReference` (199), `TermCmlLink` (200), `TermEvent` (201), `TermActor` (222), `TermRole` (223), `TermQuality` (224), `TermExtraction` (225), `DocumentFragmentIndex` (249), `DocumentFragment` (253), `TagIndex` (294), `TagEntry` (304), `TagReference` (325) |
| [src/main/scala/cozy/bok/CozyBokProjectPages.scala](../../../../src/main/scala/cozy/bok/CozyBokProjectPages.scala) | 2 | `ProjectArtifactRecord` (436), `ProjectCmlTermRow` (709) |
| [src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala) | 15 | `RepositoryCarIndex` (41), `ComponentReferenceIndex` (52), `ComponentReferenceEntry` (66), `ComponentReferenceVersion` (99), `RepositoryCarDiagnostic` (116), `RepositoryCatalogSource` (137), `RepositoryCatalogDiagnostic` (144), `RepositoryCatalogDiscovery` (159), `RepositoryCarEntry` (164), `RepositoryCarSidecars` (201), `RepositoryCarVersion` (215), `RepositoryCarComponentKnowledge` (262), `RepositoryCarLinks` (274), `RepositoryCarArchiveMetadata` (312), `RepositoryCarMetadataCoordinate` (319) |
| [src/main/scala/cozy/bok/CozyBokSieMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokSieMetadata.scala) | 4 | `FinalizationBackup` (41), `RdfExtensionDeclaration` (42), `GraphComponentRef` (724), `GraphComponentReferenceEntry` (732) |
| [src/main/scala/cozy/modeler/StateMachineNormalizationProjector.scala](../../../../src/main/scala/cozy/modeler/StateMachineNormalizationProjector.scala) | 1 | `NormalizationFailure` (56) |
| [src/main/scala/cozy/video/CozyVideoConfig.scala](../../../../src/main/scala/cozy/video/CozyVideoConfig.scala) | 9 | `InspectConfig` (36), `BuildConfig` (54), `RenderConfig` (74), `SynthesizeConfig` (101), `RdfConfig` (139), `ReviewEvidenceConfig` (157), `TranscribeConfig` (188), `DemoScriptConfig` (233), `ReplayConfig` (258) |
| [src/main/scala/cozy/video/CozyVideoModel.scala](../../../../src/main/scala/cozy/video/CozyVideoModel.scala) | 34 | `StoryboardReviewVisualStory` (66), `StoryboardReviewVisualPage` (85), `StoryboardReview` (104), `ConfirmationReview` (125), `VideoProject` (140), `VideoToolSettings` (222), `VideoPart` (263), `VideoRenderer` (321), `ResolvedEncodingSettings` (454), `VideoScript` (473), `VideoScene` (516), `VideoReplayViewport` (593), `VideoReplayStep` (604), `VideoReplayScript` (651), `VideoArtifactPlan` (680), `VideoCommandPlan` (687), `VideoTranscriptSegment` (696), `VideoTranscriptionResult` (703), `VideoReviewEvidenceResult` (717), `ReviewEvidencePart` (729), `ReviewEvidenceFrame` (742), `VideoTranscribeExecution` (751), `VideoPartPlan` (761), `VideoPlan` (786), `VideoAudioManifestEntry` (802), `VideoSynthesisResult` (856), `VideoRenderedPart` (870), `VideoRenderResult` (878), `VideoBuildResult` (886), `VideoRdfResult` (900), `VideoDemoScriptResult` (913), `VideoReplayResult` (920), `VideoAudioInput` (930), `VideoSimpleJava2dInput` (936) |
| [src/main/scala/cozy/video/CozyVideoRenderWorkspace.scala](../../../../src/main/scala/cozy/video/CozyVideoRenderWorkspace.scala) | 3 | `CharacterDialogueAssets` (206), `CharacterDialogueTemplateResource` (225), `CharacterDialogueTemplate` (226) |
| [src/main/scala/cozy/video/CozyVideoRuntime.scala](../../../../src/main/scala/cozy/video/CozyVideoRuntime.scala) | 2 | `WaveData` (60), `NarrationSelection` (74) |
| [src/main/scala/cozy/video/CozyVideoStoryboard.scala](../../../../src/main/scala/cozy/video/CozyVideoStoryboard.scala) | 14 | `Storyboard` (21), `StoryboardScene` (27), `StoryboardScreen` (51), `StoryboardTextScreen` (56), `StoryboardVisualPageScreen` (61), `StoryboardProductionInsert` (70), `StoryboardPronunciationNote` (76), `StoryboardDiagnostic` (81), `StoryboardResult` (91), `StoryboardValidateConfig` (99), `StoryboardInspectConfig` (105), `StoryboardConvertConfig` (111), `StoryboardMigrateConfig` (125), `MarkdownFailure` (955) |
| [src/main/scala/cozy/video/CozyVideoTools.scala](../../../../src/main/scala/cozy/video/CozyVideoTools.scala) | 21 | `VideoToolCheck` (57), `VideoToolContext` (65), `VideoExecutionConfig` (75), `DockerMount` (81), `ReviewEvidenceDockerMounts` (87), `VideoToolRegistry` (131), `UncheckedToolProvider` (152), `VideoCommandResult` (162), `VideoHttpResult` (171), `NarrationAudio` (237), `DockerToolchainProvider` (303), `DockerImageProvider` (326), `TextusToolchainImageProvider` (361), `VoicevoxProvider` (408), `MacosSayProvider` (444), `PiperProvider` (495), `FfmpegProvider` (558), `RemotionNodeProvider` (577), `PlaywrightProvider` (597), `WhisperCppProvider` (618), `PythonPillowProvider` (655) |
| [src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala](../../../../src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala) | 1 | `Fixture` (307) |
| [src/test/scala/cozy/document/CozyDocumentPresentationSemanticsSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentPresentationSemanticsSpec.scala) | 1 | `Fixture` (507) |
| [src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala) | 1 | `BundleBytes` (705) |
| [src/test/scala/cozy/document/CozyDocumentProjectLocalBuildCommandSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentProjectLocalBuildCommandSpec.scala) | 1 | `Fixture` (296) |
| [src/test/scala/cozy/document/CozyDocumentSourceAdmissionSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentSourceAdmissionSpec.scala) | 1 | `Fixture` (339) |
| [src/test/scala/cozy/document/CozySummarySlidePdfAcceptanceSpec.scala](../../../../src/test/scala/cozy/document/CozySummarySlidePdfAcceptanceSpec.scala) | 1 | `FailureEvidence` (221) |
| [src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala](../../../../src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala) | 1 | `Fixture` (307) |
| [src/test/scala/cozy/media/CozyMediaPresentationMigrationSpec.scala](../../../../src/test/scala/cozy/media/CozyMediaPresentationMigrationSpec.scala) | 1 | `Fixture` (192) |
| [src/test/scala/cozy/modeler/Phase63McmlGenerationProvenanceSpec.scala](../../../../src/test/scala/cozy/modeler/Phase63McmlGenerationProvenanceSpec.scala) | 1 | `Delegated` (430) |
| [src/test/scala/cozy/modeler/SkillDrivenWorkflowProducerFixtureSpec.scala](../../../../src/test/scala/cozy/modeler/SkillDrivenWorkflowProducerFixtureSpec.scala) | 1 | `FixtureRunState` (197) |
| [src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala](../../../../src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala) | 1 | `Fixture` (177) |
| [src/test/scala/cozy/runtime/CozySbtBridgeMultiProvenanceSpec.scala](../../../../src/test/scala/cozy/runtime/CozySbtBridgeMultiProvenanceSpec.scala) | 1 | `Delegated` (130) |
| [src/test/scala/cozy/video/CozyPhase71PrivateDeclarationRepairSpec.scala](../../../../src/test/scala/cozy/video/CozyPhase71PrivateDeclarationRepairSpec.scala) | 2 | `Fixture` (255), `ScriptResult` (256) |
| [src/test/scala/cozy/video/CozyPhase71PrivateVideoDriverSpec.scala](../../../../src/test/scala/cozy/video/CozyPhase71PrivateVideoDriverSpec.scala) | 2 | `Fixture` (317), `ScriptResult` (329) |
| [src/test/scala/cozy/video/CozyVideoStoryboardArtifactsSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardArtifactsSpec.scala) | 1 | `Snapshot` (288) |
| [src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala) | 2 | `ArtifactSnapshot` (1178), `StaticTool` (1184) |
| [src/test/scala/cozy/video/CozyVideoStoryboardNarrationSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardNarrationSpec.scala) | 1 | `NarrationFixture` (336) |

## Additional source-only enclosure candidates

The initial lexical scan also matched strings in `ModelerValueGenerationSpec` and misattributed the now-static `VisualPageMaterial` companion; manual source inspection excluded those false positives. The five declarations below are actual current declarations that did not match the unchecked equality shape in existing classfiles. Some are non-final types whose generated equality can check the outer instance; others require checking whether their nesting is intentional or whether a compiled counterpart exists. They are not asserted to emit the warning.

- `src/main/scala/cozy/modeler/ModelStateMachineProjector.scala:178` — `Z`
- `src/main/scala/cozy/modeler/ModelStateMachineProjector.scala:219` — `TransitionDefinition`
- `src/main/scala/cozy/modeler/ModelTypeProjector.scala:602` — `LocalValueDefinition`
- `src/main/scala/cozy/modeler/Modeler.scala:249` — `Z`
- `src/main/scala/cozy/modeler/StateMachineNormalizationProjector.scala:48` — `NormalizationTransitionDefinition`

## Remaining validation and execution disposition

A full main/test recompilation is needed to enumerate actual current compiler diagnostics. The successful incremental 16-test run does not provide that evidence. Shared-skill repair and denied helper rewiring remain discontinued; no agent SBT invocation or bypass was attempted. User manual execution remains the admitted practical validation route. Broad structural repair is not included in this read-only audit. Phase/checklist remain open.

Machine-readable diagnostic artifacts are retained under `target/outer-reference-audit/`; this journal preserves the evidence and disposition if later `clean` removes those generated artifacts.
