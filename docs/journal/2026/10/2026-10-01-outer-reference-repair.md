# Cozy known compiler-warning repair

Date: 2026-10-01
Authority: user requested known repairs and will run compile manually
Disposition: source repair, warning-free clean main/test compile and focused Storyboard review Spec PASS; user-selected limited acceptance complete
Phase 71.1: CLOSED under revised user-defined criteria; exception closure withdrawn; no commit or push

## Frozen repair boundary

- OUTER-001: the 187 case-class declarations in the corrected audit (83 Video, 81 BoK, 1 Modeler, 22 Spec fixtures), plus the typed catch of the same Modeler exception.
- UNREACHABLE-001: unreachable `DisplayValue` fallback in `CozyLogicalUiRuntimeValidation._validate_value`.
- IMPORT-001: hidden archive `RepositoryArtifactCatalog` import in `BridgeContractSpec`; preserve actual calls through the package facade.

The relevant incoming dirty bytes were captured before editing under `target/outer-reference-repair/before/`. These snapshots, not Git HEAD, define the repair baseline. Existing document/media hash-removal and Storyboard review edits remain preserved.

## Applied structure

Each target case class and its same-name explicit companion was moved unchanged into the companion object of its former enclosing trait/class. Main classes retain the original trait-facing type and constructor paths through alias bindings; private classes are imported from the companion. Required same-owner private helper functions were moved together and imported back into the trait. The remaining functions and constants stay in their existing implementation scope. Main model references to existing singleton-owned types/helpers remain bound to the current `CozyVideoImplementation` / `CozyBokImplementation` family. Spec fixtures are plain static data in their suite companion.

The first independent review found two path-dependent type mismatches between moved models and remaining trait-owned interfaces. The correction moves the related Video tool mode/status/provider/probe/client types and default objects, Video encoding policy/artifact status enums, and Storyboard screen interface into those same static companions. Existing interfaces, enum cases and method bodies remain unchanged; traits without term companions retain type-only aliases, and object-only defaults retain term-only aliases. These corrections remain within the original three owned source files. The same registered reviewer is checking correction revision 2 against the same incoming dirty baseline.

Field names, order, defaults, custom overloads, model methods, codecs, error branches and Spec assertions were preserved. `final` remains unchanged. No `@nowarn`, `@unchecked`, `-Wconf`, warning limit or other build setting was introduced. The two additional warning fixes remove only the unreachable branch and the already-hidden import.

The nested JVM class owners change. This repair does not claim binary ABI compatibility with a previously published library; the affected model surface is repository-internal and existing source-facing facade paths are retained. No release, publication or baseline commit is created.

## Static evidence

- Original nonempty lines are preserved across each of the 35 model/fixture moves; the extra text consists of companion wrappers, imports and source-compatible aliases.
- Each of the 187 original target case-class declarations remains present exactly once.
- Lexical delimiter deltas match the incoming source (not a substitute for compilation).
- `git diff --check` succeeds.
- Registered read-only `cncf_rereview_worker_luna_xhigh` (gpt-6.1-sol / high) reviewed the exact 37-file delta and resumed for correction revision 2. Focused source review PASS: CB-OUTER-TYPE-001 and CB-OUTER-TYPE-002 resolved, with no new actionable blocker; compiler and warning clearance were unverified at source review, and are subsequently confirmed by the manual clean compilation below.
- Before/after snapshots, manifest, exact repair diff, static checks and review disposition also survive the user's next `clean` at `/private/tmp/cozy-outer-reference-repair-oixmpm6e`. The manifest retains its original target paths as historical review identity; archived snapshots are under this backup directory.

## Changed model / fixture files

| Source | Moved case classes |
| --- | ---: |
| [src/main/scala/cozy/bok/CozyBokBibliography.scala](../../../../src/main/scala/cozy/bok/CozyBokBibliography.scala) | 1 |
| [src/main/scala/cozy/bok/CozyBokBuildConfig.scala](../../../../src/main/scala/cozy/bok/CozyBokBuildConfig.scala) | 15 |
| [src/main/scala/cozy/bok/CozyBokConfig.scala](../../../../src/main/scala/cozy/bok/CozyBokConfig.scala) | 8 |
| [src/main/scala/cozy/bok/CozyBokGlossaryWorkflow.scala](../../../../src/main/scala/cozy/bok/CozyBokGlossaryWorkflow.scala) | 3 |
| [src/main/scala/cozy/bok/CozyBokMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokMetadata.scala) | 1 |
| [src/main/scala/cozy/bok/CozyBokModel.scala](../../../../src/main/scala/cozy/bok/CozyBokModel.scala) | 32 |
| [src/main/scala/cozy/bok/CozyBokProjectPages.scala](../../../../src/main/scala/cozy/bok/CozyBokProjectPages.scala) | 2 |
| [src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokRepositoryMetadata.scala) | 15 |
| [src/main/scala/cozy/bok/CozyBokSieMetadata.scala](../../../../src/main/scala/cozy/bok/CozyBokSieMetadata.scala) | 4 |
| [src/main/scala/cozy/modeler/StateMachineNormalizationProjector.scala](../../../../src/main/scala/cozy/modeler/StateMachineNormalizationProjector.scala) | 1 |
| [src/main/scala/cozy/video/CozyVideoConfig.scala](../../../../src/main/scala/cozy/video/CozyVideoConfig.scala) | 9 |
| [src/main/scala/cozy/video/CozyVideoModel.scala](../../../../src/main/scala/cozy/video/CozyVideoModel.scala) | 34 |
| [src/main/scala/cozy/video/CozyVideoRenderWorkspace.scala](../../../../src/main/scala/cozy/video/CozyVideoRenderWorkspace.scala) | 3 |
| [src/main/scala/cozy/video/CozyVideoRuntime.scala](../../../../src/main/scala/cozy/video/CozyVideoRuntime.scala) | 2 |
| [src/main/scala/cozy/video/CozyVideoStoryboard.scala](../../../../src/main/scala/cozy/video/CozyVideoStoryboard.scala) | 14 |
| [src/main/scala/cozy/video/CozyVideoTools.scala](../../../../src/main/scala/cozy/video/CozyVideoTools.scala) | 21 |
| [src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala](../../../../src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala) | 1 |
| [src/test/scala/cozy/document/CozyDocumentPresentationSemanticsSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentPresentationSemanticsSpec.scala) | 1 |
| [src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentProjectExportSpec.scala) | 1 |
| [src/test/scala/cozy/document/CozyDocumentProjectLocalBuildCommandSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentProjectLocalBuildCommandSpec.scala) | 1 |
| [src/test/scala/cozy/document/CozyDocumentSourceAdmissionSpec.scala](../../../../src/test/scala/cozy/document/CozyDocumentSourceAdmissionSpec.scala) | 1 |
| [src/test/scala/cozy/document/CozySummarySlidePdfAcceptanceSpec.scala](../../../../src/test/scala/cozy/document/CozySummarySlidePdfAcceptanceSpec.scala) | 1 |
| [src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala](../../../../src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala) | 1 |
| [src/test/scala/cozy/media/CozyMediaPresentationMigrationSpec.scala](../../../../src/test/scala/cozy/media/CozyMediaPresentationMigrationSpec.scala) | 1 |
| [src/test/scala/cozy/modeler/Phase63McmlGenerationProvenanceSpec.scala](../../../../src/test/scala/cozy/modeler/Phase63McmlGenerationProvenanceSpec.scala) | 1 |
| [src/test/scala/cozy/modeler/SkillDrivenWorkflowProducerFixtureSpec.scala](../../../../src/test/scala/cozy/modeler/SkillDrivenWorkflowProducerFixtureSpec.scala) | 1 |
| [src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala](../../../../src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala) | 1 |
| [src/test/scala/cozy/runtime/CozySbtBridgeMultiProvenanceSpec.scala](../../../../src/test/scala/cozy/runtime/CozySbtBridgeMultiProvenanceSpec.scala) | 1 |
| [src/test/scala/cozy/video/CozyPhase71PrivateDeclarationRepairSpec.scala](../../../../src/test/scala/cozy/video/CozyPhase71PrivateDeclarationRepairSpec.scala) | 2 |
| [src/test/scala/cozy/video/CozyPhase71PrivateVideoDriverSpec.scala](../../../../src/test/scala/cozy/video/CozyPhase71PrivateVideoDriverSpec.scala) | 2 |
| [src/test/scala/cozy/video/CozyVideoStoryboardArtifactsSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardArtifactsSpec.scala) | 1 |
| [src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala) | 2 |
| [src/test/scala/cozy/video/CozyVideoStoryboardNarrationSpec.scala](../../../../src/test/scala/cozy/video/CozyVideoStoryboardNarrationSpec.scala) | 1 |
| [src/test/scala/cozy/document/CozySummaryConfirmationProjectionV2Spec.scala](../../../../src/test/scala/cozy/document/CozySummaryConfirmationProjectionV2Spec.scala) | 1 |
| [src/test/scala/cozy/media/CozyExplanationPreviewSpec.scala](../../../../src/test/scala/cozy/media/CozyExplanationPreviewSpec.scala) | 1 |

The two additional files are `src/main/scala/cozy/ui/CozyLogicalUiRuntimeValidation.scala` and `src/test/scala/cozy/BridgeContractSpec.scala`.

## Validation handoff

The user ran `sbt --batch clean 'Test / compile'`: clean succeeded at 20:26:56 JST and main/test compilation succeeded at 20:28:10 JST (74 seconds). All 257 main and 175 test Scala sources compiled, with zero `[warn]` or `[error]` diagnostics. This validates the latest owner-relocation repair tree. The previous successful compile with 123 displayed warnings describes the preceding source tree only. The earlier 16-test Storyboard review results also do not validate this new repair tree. Warning-free clean compilation is confirmed. `CozyVideoStoryboardReviewSpec` subsequently passed all 16 tests at 20:30:25 JST in 5 seconds, with zero warning/error diagnostics. The transcript is `/Users/asami/.codex/attachments/f0eea69b-df11-433f-9276-2f4e750a411d/貼り付けたテキスト.txt`. Together these satisfy the user-selected acceptance gate; the later [exception closure proposal](2026-10-01-phase-71.1-warning-free-storyboard-closure.md) was rejected by the user and withdrawn. The user subsequently selected normal closure with revised completion criteria; see the [normal closure record](2026-10-01-phase-71.1-normal-closure.md). Passing validation is retained, and unperformed broader work is not claimed as successful. The remaining `[info]` diagnostic names `AkkaHttpServer.scala`, which contains only a package declaration and commented-out scaffold, with no actual class, trait or object. This is a separate incremental-dependency informational diagnostic, not a remaining outer-reference, unreachable-code or hidden-import warning. No source edit was made after this compile result.

Shared execution-skill repair and prior denied helper rewiring stay discontinued. No agent SBT invocation, bypass, warning suppression, commit/push or successor work occurred.
