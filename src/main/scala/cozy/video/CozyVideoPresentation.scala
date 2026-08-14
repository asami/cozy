package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.context.{FaultException, NetworkIoFault, SubsystemIoFault}
import org.goldenport.io.InputSource
import cozy.config.{CozyProjectContext, CozyProjectYamlConfig}
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import org.smartdox.semanticweb.{Rdf, RdfRenderer, Vocabulary}
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import java.io.ByteArrayOutputStream
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoPresentation {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning =>
  private[video] def _render_inspect(
    config: InspectConfig,
    plan: VideoPlan,
    checks: Vector[VideoToolCheck]
  ): String = {
    val project = plan.project
    val b = Vector.newBuilder[String]
    b += "Cozy Video Inspect"
    b += s"projectFile: ${plan.projectFile}"
    b += s"projectRoot: ${plan.projectRoot}"
    b ++= _render_project_context(plan.projectContext)
    project.name.foreach(x => b += s"name: $x")
    project.title.foreach(x => b += s"title: $x")
    b += s"toolMode: ${plan.execution.toolMode.label}"
    b += s"dockerImage: ${plan.execution.dockerImage}"
    b += s"output: ${plan.outputPath}"
    b += s"renderer: ${project.renderer.map(_.summary).getOrElse("engine=legacy")}"
    val narrationproviders = _plan_narration_providers(plan).toVector.sorted
    if (narrationproviders.nonEmpty)
      b += s"narrationProviders: ${narrationproviders.mkString(", ")}"
    project.profile.foreach(x => b += s"profile: $x")
    val effects = CozyVideoEffects.expand(project.visualEffects)
    if (effects.nonEmpty) {
      b += "visualEffects:"
      effects.foreach(effect => b += s"  - ${effect.role.key}: ${effect.profile} => ${effect.display}")
      val renderer = project.renderer.map(_.engineOrDefault).getOrElse("legacy")
      val capability = CozyVideoEffects.capability(renderer, effects)
      b += s"visualEffectRenderer: $renderer"
      b += s"visualEffectCapability: ${capability.status}"
      if (capability.unsupported.nonEmpty)
        b += s"unsupportedVisualEffectPrimitives: ${capability.unsupported.mkString(", ")}"
    }
    if (plan.assets.nonEmpty) {
      b += "assets:"
      plan.assets.foreach { asset =>
        b += s"  - ${asset.role.key}: ${asset.status} path=${asset.displayPath(plan.projectRoot)} kind=${asset.kind} required=${asset.required}"
        b += s"    license: ${asset.license}"
        b += s"    provenance: ${asset.provenance}"
        asset.requestedDisplayPath(plan.projectRoot).foreach(x => b += s"    requestedPath: $x")
      }
    }
    b ++= _render_credit_inspection(plan.credits)
    b += s"parts: ${project.parts.size}"
    plan.parts.foreach { part =>
      b ++= _render_part(part)
    }
    b ++= _render_artifacts(plan.artifacts)
    if (config.checkTools) {
      b ++= _render_tool_checks(checks)
    }
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_build_dry_run(
    config: BuildConfig,
    plan: VideoPlan,
    checks: Vector[VideoToolCheck]
  ): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Build Dry-Run"
    b += s"projectFile: ${plan.projectFile}"
    b += s"projectRoot: ${plan.projectRoot}"
    b ++= _render_project_context(plan.projectContext)
    b += s"toolMode: ${plan.execution.toolMode.label}"
    b += s"dockerImage: ${plan.execution.dockerImage}"
    b += s"output: ${plan.outputPath}"
    b += s"parts: ${plan.parts.size}"
    b ++= _render_credit_inspection(plan.credits)
    b ++= _render_artifacts(plan.artifacts)
    b += "commands:"
    plan.commands.foreach { command =>
      b += s"  - ${command.stepName}: ${command.toolName} (${command.mode.label}) - ${command.preview}"
      if (command.inputs.nonEmpty)
        b += s"    inputs: ${command.inputs.mkString(", ")}"
      if (command.outputs.nonEmpty)
        b += s"    outputs: ${command.outputs.mkString(", ")}"
    }
    if (config.checkTools) {
      b ++= _render_tool_checks(checks)
    }
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_build_result(result: VideoBuildResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Build"
    b += s"projectFile: ${result.projectFile}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    b += s"output: ${result.outputPath}"
    b += s"manifest: ${result.manifestPath}"
    b += s"concatList: ${result.concatListPath}"
    b += s"parts: ${result.partOutputs.size}"
    result.partOutputs.foreach { path =>
      b += s"  - partOutput: $path"
    }
    b += s"ffprobe: ${result.ffprobeSummary.noSpaces}"
    result.creditFiles.foreach { files =>
      result.creditProfile.foreach(x => b += s"creditProfile: $x")
      b += s"creditDigest: ${files.digest}"
      b += s"credits: ${files.jsonFile}"
      b += s"creditMarkdown: ${files.markdownFile}"
      b += s"creditRendererProps: ${files.rendererPropsFile}"
    }
    result.creditWarnings.foreach { warning =>
      b += s"creditWarning: ${warning.code}: ${warning.message}"
    }
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_project_context(context: CozyProjectContext.Context): Vector[String] = {
    val b = Vector.newBuilder[String]
    b += s"discoveredProjectPackageRoot: ${context.packageRoot.path}"
    context.project match {
      case Some(project) =>
        b += s"discoveredProjectRoot: ${project.root.path}"
        b += s"discoveredProjectMarker: ${project.marker.path}"
      case None =>
        b += "discoveredProjectRoot: legacy/standalone absence"
    }
    b.result()
  }

  private[video] def _render_credit_inspection(credits: CozyVideoCredits.EffectiveSet): Vector[String] = {
    val b = Vector.newBuilder[String]
    credits.selection.foreach { selection =>
      b += s"creditProfile: ${selection.id}"
      b += s"creditProfileSelectionLayer: ${selection.layer}"
      selection.configPath.foreach(x => b += s"creditProfileSelectionPath: $x")
    }
    credits.profile.foreach { source =>
      b += s"creditProfileSourceLayer: ${source.layer}"
      b += s"creditProfileSourcePath: ${source.path}"
    }
    b += s"creditLocale: ${credits.locale}"
    if (credits.evidence.characterIds.nonEmpty)
      b += s"creditCharacters: ${credits.evidence.characterIds.mkString(", ")}"
    if (credits.evidence.assetTags.nonEmpty)
      b += s"creditAssetTags: ${credits.evidence.assetTags.mkString(", ")}"
    credits.evidence.audio.foreach { audio =>
      b += s"creditAudio: provider=${audio.provider} voice=${audio.voiceIdentity.orElse(audio.voiceId).orElse(audio.modelIdentity).getOrElse("unknown")} manifest=${audio.manifestPath}"
    }
    if (credits.items.nonEmpty) {
      b += "credits:"
      credits.items.foreach { resolved =>
        b += s"  - ${resolved.item.id}: ${resolved.item.obligation} evidence=${resolved.evidence.mkString(",")}"
        resolved.item.sourceUrl.foreach(x => b += s"    source: $x")
        resolved.item.termsUrl.foreach(x => b += s"    terms: $x")
      }
    }
    credits.diagnostics.foreach { diagnostic =>
      b += s"credit${diagnostic.severity.capitalize}: ${diagnostic.code}: ${diagnostic.message}"
    }
    b.result()
  }

  private[video] def _render_synthesis_result(result: VideoSynthesisResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Synthesize"
    b += s"scriptFile: ${result.scriptFile}"
    b += s"outputDir: ${result.outputDir}"
    b += s"provider: ${result.provider}"
    b += s"executionMode: ${result.executionMode}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    result.diagnostics.foreach(x => b += s"warning: $x")
    if (result.toolChecks.nonEmpty)
      b ++= _render_tool_checks(result.toolChecks)
    b += s"scenes: ${result.entries.size}"
    b += s"combined: ${result.combinedFile}"
    b += s"manifest: ${result.manifestFile}"
    result.entries.foreach { entry =>
      b += f"  - ${entry.sceneId}: ${entry.file} audio=${entry.audioDuration}%.3f target=${entry.targetDuration}%.3f lead=${entry.leadSilence}%.3f tail=${entry.tailSilence}%.3f"
    }
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_render_result(result: VideoRenderResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Render"
    b += s"projectFile: ${result.projectFile}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    b += s"parts: ${result.parts.size}"
    result.parts.foreach { part =>
      b += s"  - part.${part.id}: ${part.outputPath}"
      b += s"    manifest: ${part.manifestPath}"
      b += s"    ${part.workDirLabel}: ${part.workDir}"
    }
    result.creditWarnings.foreach { warning =>
      b += s"creditWarning: ${warning.code}: ${warning.message}"
    }
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_transcription_result(result: VideoTranscriptionResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Transcribe"
    b += s"inputVideo: ${result.inputVideo}"
    b += s"outputDir: ${result.saveDir}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    b += s"modelPath: ${result.modelPath}"
    b += s"audio: ${result.audioPath}"
    b += s"transcript: ${result.transcriptPath}"
    b += s"captions: ${result.captionsPath}"
    b += s"narration: ${result.narrationPath}"
    b += s"manifest: ${result.manifestPath}"
    b += s"segments: ${result.segmentCount}"
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_demo_script_result(result: VideoDemoScriptResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Demo Script"
    b += s"inputVideo: ${result.inputVideo}"
    b += s"scriptFile: ${result.scriptFile}"
    b += s"manualReview: ${result.manualReview}"
    b += s"steps: ${result.steps.size}"
    result.steps.foreach { step =>
      b += s"  - ${step.kind}${step.selector.map(x => s" selector=$x").getOrElse("")}${step.url.map(x => s" url=$x").getOrElse("")}"
    }
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_replay_result(result: VideoReplayResult): String = {
    val b = Vector.newBuilder[String]
    b += (if (result.dryRun) "Cozy Video Replay Dry-Run" else "Cozy Video Replay")
    b += s"scriptFile: ${result.scriptFile}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    result.outputVideo.foreach(x => b += s"outputVideo: $x")
    b += s"manifest: ${result.manifestPath}"
    b += "commands:"
    result.commands.foreach { command =>
      b += s"  - ${command.stepName}: ${command.toolName} (${command.mode.label}) - ${command.preview}"
      if (command.inputs.nonEmpty)
        b += s"    inputs: ${command.inputs.mkString(", ")}"
      if (command.outputs.nonEmpty)
        b += s"    outputs: ${command.outputs.mkString(", ")}"
    }
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_rdf_result(result: VideoRdfResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video RDF"
    b += s"projectFile: ${result.projectFile}"
    b += s"outputDir: ${result.outputDir}"
    b += s"turtle: ${result.turtleFile}"
    b += s"jsonld: ${result.jsonLdFile}"
    b += s"manifest: ${result.manifestFile}"
    b += s"triples: ${result.tripleCount}"
    b += s"resources: ${result.resourceCount}"
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_review_evidence_result(result: VideoReviewEvidenceResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Review Evidence"
    b += s"projectFile: ${result.projectFile}"
    b += s"finalVideo: ${result.finalVideo}"
    b += s"outputDir: ${result.saveDir}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    b += s"manifest: ${result.manifestPath}"
    b += s"parts: ${result.partCount}"
    b += s"scenes: ${result.sceneCount}"
    b += s"frames: ${result.frameCount}"
    b.result().mkString("\n") + "\n"
  }

  private[video] def _render_part(part: VideoPartPlan): Vector[String] = {
    val z = Vector.newBuilder[String]
    z += s"part[${part.index}]: ${part.id}"
    z += s"  type: ${part.partType}${if (part.supported) "" else " (unsupported)"}"
    z += s"  renderer: ${part.renderer}"
    part.scriptPath match {
      case Some(path) =>
        z += s"  script: ${part.scriptName.getOrElse(path.getFileName.toString)}"
        z += s"  scriptPath: $path"
        z += s"  scriptStatus: ${part.scriptStatus}"
        part.script.foreach { videoscript =>
          videoscript.title.foreach(x => z += s"  scriptTitle: $x")
          z += s"  narrationProvider: ${_resolve_narration_selection(videoscript).provider}"
          z += s"  scenes: ${videoscript.scenes.size}"
          z += s"  expandedScenes: ${videoscript.expandedScenes.size}"
          z += f"  estimatedDuration: ${videoscript.estimatedDuration}%.2f"
        }
      case None =>
        z += s"  scriptStatus: none"
    }
    part.stepsPath.foreach { path =>
      z += s"  steps: ${part.stepsName.getOrElse(path.getFileName.toString)}"
      z += s"  stepsPath: $path"
      part.stepsStatus.foreach(x => z += s"  stepsStatus: $x")
    }
    z += s"  output: ${part.outputPath}"
    part.audioDir.foreach(x => z += s"  audioDir: $x")
    part.recordDir.foreach(x => z += s"  recordDir: $x")
    z.result()
  }

  private[video] def _render_artifacts(artifacts: Vector[VideoArtifactPlan]): Vector[String] = {
    val b = Vector.newBuilder[String]
    b += "artifacts:"
    artifacts.foreach { artifact =>
      b += s"  - ${artifact.kind}: ${artifact.status.label} ${artifact.path}"
      b += s"    producer: ${artifact.producerStep}"
    }
    b.result()
  }

  private[video] def _render_tool_checks(checks: Vector[VideoToolCheck]): Vector[String] = {
    val b = Vector.newBuilder[String]
    b += "tool checks:"
    checks.foreach { check =>
      b += s"  - ${check.name}: ${check.status.label} (${check.mode.label}) - ${check.message}"
      check.setupHint.foreach(x => b += s"    setup: $x")
    }
    b.result()
  }
}
