# Cozy clean main/test compilation: warning inventory

Date: 2026-10-01
Disposition: compilation PASS; warning-free acceptance OPEN; tests NOT RUN
Phase 71.1: IN_PROGRESS; closure OPEN; no commit or push

## User execution evidence

The user ran `sbt --batch clean 'Test / compile'` in the Cozy checkout. SBT 1.9.7 / Eclipse Adoptium Java 21.0.2 completed `clean` at 19:42:41 JST, compiled 257 main and 175 test Scala sources, and reported final success at 19:44:11 JST after 90 seconds. Both main and test compilation completed; the shell prompt returned. No test execution or runtime classpath export is present in this transcript.

Source transcript: `/Users/asami/.codex/attachments/9ae6b630-6e31-489e-bad6-4eaea4e39ea4/貼り付けたテキスト.txt`.

| Area | Displayed outer-reference warnings | Other displayed warnings | Displayed total |
| --- | ---: | ---: | ---: |
| Main | 99 | 1 unreachable-code warning | 100 |
| Executable Specs | 22 | 1 hidden-import warning | 23 |
| Total | 121 | 2 | 123 |

These are displayed diagnostics, not a complete repository warning count. Scala 2.12.18 defaults `-Xmaxwarns` to 100; the main log reaches that cap and stops listing Video model diagnostics partway through the model file. Current build sources do not override that option. Therefore more main warnings may remain hidden. Reference: [Scala 2.12.18 compiler settings](https://raw.githubusercontent.com/scala/scala/v2.12.18/src/compiler/scala/tools/nsc/settings/ScalaSettings.scala). A temporary higher display limit can enumerate them without changing source or suppressing warnings.

## Confirmed warning locations

| Source | Count | Locations |
| --- | ---: | --- |
| [src/main/scala/cozy/bok/CozyBokBibliography.scala](../../../../src/main/scala/cozy/bok/CozyBokBibliography.scala) | 1 | 58 |
| [src/main/scala/cozy/bok/CozyBokBuildConfig.scala](../../../../src/main/scala/cozy/bok/CozyBokBuildConfig.scala) | 15 | 43, 75, 76, 77, 78, 91, 111, 118, 119, 120, 121, 122, 129, 141, 287 |
| [src/main/scala/cozy/bok/CozyBokConfig.scala](../../../../src/main/scala/cozy/bok/CozyBokConfig.scala) | 8 | 58, 65, 75, 78, 89, 93, 98, 99 |
| [src/main/scala/cozy/bok/CozyBokGlossaryWorkflow.scala](../../../../src/main/scala/cozy/bok/CozyBokGlossaryWorkflow.scala) | 3 | 87, 103, 105 |
| [src/main/scala/cozy/bok/CozyBokMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokMetadata.scala) | 1 | 41 |
| [src/main/scala/cozy/bok/CozyBokModel.scala](../../../../src/main/scala/cozy/bok/CozyBokModel.scala) | 32 | 41, 56, 57, 58, 59, 67, 74, 81, 89, 95, 104, 110, 114, 121, 128, 135, 136, 157, 158, 198, 199, 200, 201, 222, 223, 224, 225, 249, 253, 294, 304, 325 |
| [src/main/scala/cozy/bok/CozyBokProjectPages.scala](../../../../src/main/scala/cozy/bok/CozyBokProjectPages.scala) | 2 | 436, 709 |
| [src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala) | 15 | 41, 52, 66, 99, 116, 137, 144, 159, 164, 201, 215, 262, 274, 312, 319 |
| [src/main/scala/cozy/bok/CozyBokSieMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokSieMetadata.scala) | 4 | 41, 42, 724, 732 |
| [src/main/scala/cozy/modeler/StateMachineNormalizationProjector.scala](../../../../src/main/scala/cozy/modeler/StateMachineNormalizationProjector.scala) | 2 | 56, 76 |
| [src/main/scala/cozy/ui/CozyLogicalUiRuntimeValidation.scala](../../../../src/main/scala/cozy/ui/CozyLogicalUiRuntimeValidation.scala) | 1 | 249 |
| [src/main/scala/cozy/video/CozyVideoConfig.scala](../../../../src/main/scala/cozy/video/CozyVideoConfig.scala) | 9 | 36, 54, 74, 101, 139, 157, 188, 233, 258 |
| [src/main/scala/cozy/video/CozyVideoModel.scala](../../../../src/main/scala/cozy/video/CozyVideoModel.scala) | 7 | 66, 85, 104, 125, 140, 222, 263 |
| [src/test/scala/cozy/BridgeContractSpec.scala](../../../../src/test/scala/cozy/BridgeContractSpec.scala) | 1 | 5 |
| [src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala](../../../../src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala) | 1 | 307 |
| [src/test/scala/cozy/document/CozyDocumentPresentationSemanticsSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentPresentationSemanticsSpec.scala) | 1 | 507 |
| [src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala) | 1 | 705 |
| [src/test/scala/cozy/document/CozyDocumentProjectLocalBuildCommandSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentProjectLocalBuildCommandSpec.scala) | 1 | 296 |
| [src/test/scala/cozy/document/CozyDocumentSourceAdmissionSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentSourceAdmissionSpec.scala) | 1 | 339 |
| [src/test/scala/cozy/document/CozySummaryConfirmationProjectionV2Spec.scala](../../../../src/test/scala/cozy/document/CozySummaryConfirmationProjectionV2Spec.scala) | 1 | 571 |
| [src/test/scala/cozy/document/CozySummarySlidePdfAcceptanceSpec.scala](../../../../src/test/scala/cozy/document/CozySummarySlidePdfAcceptanceSpec.scala) | 1 | 221 |
| [src/test/scala/cozy/media/CozyExplanationPreviewSpec.scala](../../../../src/test/scala/cozy/media/CozyExplanationPreviewSpec.scala) | 1 | 507 |
| [src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala](../../../../src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala) | 1 | 307 |
| [src/test/scala/cozy/media/CozyMediaPresentationMigrationSpec.scala](../../../../src/test/scala/cozy/media/CozyMediaPresentationMigrationSpec.scala) | 1 | 192 |
| [src/test/scala/cozy/modeler/Phase63McmlGenerationProvenanceSpec.scala](../../../../src/test/scala/cozy/modeler/Phase63McmlGenerationProvenanceSpec.scala) | 1 | 430 |
| [src/test/scala/cozy/modeler/SkillDrivenWorkflowProducerFixtureSpec.scala](../../../../src/test/scala/cozy/modeler/SkillDrivenWorkflowProducerFixtureSpec.scala) | 1 | 197 |
| [src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala](../../../../src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala) | 1 | 177 |
| [src/test/scala/cozy/runtime/CozySbtBridgeMultiProvenanceSpec.scala](../../../../src/test/scala/cozy/runtime/CozySbtBridgeMultiProvenanceSpec.scala) | 1 | 130 |
| [src/test/scala/cozy/video/CozyPhase71PrivateDeclarationRepairSpec.scala](../../../../src/test/scala/cozy/video/CozyPhase71PrivateDeclarationRepairSpec.scala) | 2 | 255, 256 |
| [src/test/scala/cozy/video/CozyPhase71PrivateVideoDriverSpec.scala](../../../../src/test/scala/cozy/video/CozyPhase71PrivateVideoDriverSpec.scala) | 2 | 317, 329 |
| [src/test/scala/cozy/video/CozyVideoStoryboardArtifactsSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardArtifactsSpec.scala) | 1 | 288 |
| [src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala) | 2 | 1178, 1184 |
| [src/test/scala/cozy/video/CozyVideoStoryboardNarrationSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardNarrationSpec.scala) | 1 | 336 |

## Structural interpretation and audit correction

The displayed outer-reference warnings confirm a recurring declaration pattern in BoK, Video and Spec fixtures. In Modeler, the warning is reported at both `NormalizationFailure` declaration (line 56) and its explicit typed exception catch (line 76). This is stronger direct evidence of an unchecked type-test boundary than a declaration-only warning; an incorrect runtime catch across owner instances has not been demonstrated.

The earlier lexical/source audit incorrectly classified `CozySummaryConfirmationProjectionV2Spec.Fixture` and `CozyExplanationPreviewSpec.Fixture` as already moved because their source enclosure did not match the lexical scan. This clean transcript proves both remain warning-producing instance-nested fixtures. The prior count of 185 structural candidates missed these two: the corrected declaration inventory is 187 (83 Video, 81 BoK, 1 Modeler, 22 Specs). It is still an inventory count, not the emitted diagnostic total. The Modeler typed catch is an additional diagnostic location on the same type.

The focused review/command/build repairs compile as part of this full build. Main warning output is capped, so absence from a truncated warning list alone is not proof of all main warnings being resolved. Test output is below the cap and contains no `CozyVideoStoryboardReviewSpec.VisualPageMaterial` warning. No new Spec execution has been performed after the three additional command/build repairs.

## Other diagnostics

- `CozyLogicalUiRuntimeValidation.scala:249`: unreachable fallback after all cases of `DisplayValue` (and null/payload validation) are covered. This is a separate dead branch, not an outer-reference warning.
- `BridgeContractSpec.scala:5`: the imported archive `RepositoryArtifactCatalog` is hidden by the same-named package object declaration. The unqualified calls use the package facade; repair must explicitly choose the intended API rather than rename imports mechanically.
- `AkkaHttpServer.scala:1`: informational incremental-dependency warning about a package/import-only compilation unit containing no class/trait/object. The current file contains only the package and commented-out implementation. It is not counted among the 123 `[warn]` diagnostics.

## Repair direction and remaining acceptance

For plain data currently nested in implementation traits, move declarations into stable model/companion owners and preserve public facade aliases, decoding/encoding and direct consumers. Video and BoK currently compose each family through one implementation singleton; that limits cross-owner exposure but does not justify the unnecessary instance dependency. Some declarations reference other owner-dependent models, so coordinate model placement across those dependencies rather than introduce repeated generic wrappers only to remove diagnostics. Move plain Spec fixtures to companion objects. Independently review the Modeler exception owner and typed catch. Do not remove `final`, suppress warnings, or change diagnostic settings to conceal the structural problem.

The broader warning repair is not performed by this inventory. The current request to check the whole repository is fulfilled by the static audit plus this actual compile evidence. Phase remains open. No skills changes, SBT bypass, commit, push or successor start accompanied this investigation.
