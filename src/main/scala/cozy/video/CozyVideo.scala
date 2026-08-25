package cozy.video

import java.nio.file.Path

/*
 * @since   Jun. 18, 2026
 *  version Jun. 19, 2026
 *  version Jul. 20, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideo {
  type InspectConfig = CozyVideoImplementation.InspectConfig
  lazy val InspectConfig = CozyVideoImplementation.InspectConfig
  type BuildConfig = CozyVideoImplementation.BuildConfig
  lazy val BuildConfig = CozyVideoImplementation.BuildConfig
  type RenderConfig = CozyVideoImplementation.RenderConfig
  lazy val RenderConfig = CozyVideoImplementation.RenderConfig
  type SynthesizeConfig = CozyVideoImplementation.SynthesizeConfig
  lazy val SynthesizeConfig = CozyVideoImplementation.SynthesizeConfig
  type RdfConfig = CozyVideoImplementation.RdfConfig
  lazy val RdfConfig = CozyVideoImplementation.RdfConfig
  type ReviewEvidenceConfig = CozyVideoImplementation.ReviewEvidenceConfig
  lazy val ReviewEvidenceConfig = CozyVideoImplementation.ReviewEvidenceConfig
  type TranscribeConfig = CozyVideoImplementation.TranscribeConfig
  lazy val TranscribeConfig = CozyVideoImplementation.TranscribeConfig
  type DemoScriptConfig = CozyVideoImplementation.DemoScriptConfig
  lazy val DemoScriptConfig = CozyVideoImplementation.DemoScriptConfig
  type ReplayConfig = CozyVideoImplementation.ReplayConfig
  lazy val ReplayConfig = CozyVideoImplementation.ReplayConfig
  type StoryboardValidateConfig = CozyVideoImplementation.StoryboardValidateConfig
  lazy val StoryboardValidateConfig = CozyVideoImplementation.StoryboardValidateConfig
  type StoryboardInspectConfig = CozyVideoImplementation.StoryboardInspectConfig
  lazy val StoryboardInspectConfig = CozyVideoImplementation.StoryboardInspectConfig
  type StoryboardConvertConfig = CozyVideoImplementation.StoryboardConvertConfig
  lazy val StoryboardConvertConfig = CozyVideoImplementation.StoryboardConvertConfig

  type VideoProject = CozyVideoImplementation.VideoProject
  lazy val VideoProject = CozyVideoImplementation.VideoProject
  type VideoToolSettings = CozyVideoImplementation.VideoToolSettings
  lazy val VideoToolSettings = CozyVideoImplementation.VideoToolSettings
  type VideoPart = CozyVideoImplementation.VideoPart
  lazy val VideoPart = CozyVideoImplementation.VideoPart
  type VideoRenderer = CozyVideoImplementation.VideoRenderer
  lazy val VideoRenderer = CozyVideoImplementation.VideoRenderer
  type VideoScript = CozyVideoImplementation.VideoScript
  lazy val VideoScript = CozyVideoImplementation.VideoScript
  type VideoScene = CozyVideoImplementation.VideoScene
  lazy val VideoScene = CozyVideoImplementation.VideoScene
  type VideoReplayViewport = CozyVideoImplementation.VideoReplayViewport
  lazy val VideoReplayViewport = CozyVideoImplementation.VideoReplayViewport
  type VideoReplayStep = CozyVideoImplementation.VideoReplayStep
  lazy val VideoReplayStep = CozyVideoImplementation.VideoReplayStep
  type VideoReplayScript = CozyVideoImplementation.VideoReplayScript
  lazy val VideoReplayScript = CozyVideoImplementation.VideoReplayScript
  type Storyboard = CozyVideoImplementation.Storyboard
  lazy val Storyboard = CozyVideoImplementation.Storyboard
  type StoryboardScene = CozyVideoImplementation.StoryboardScene
  lazy val StoryboardScene = CozyVideoImplementation.StoryboardScene
  type StoryboardScreen = CozyVideoImplementation.StoryboardScreen
  lazy val StoryboardScreen = CozyVideoImplementation.StoryboardScreen
  type StoryboardProductionInsert = CozyVideoImplementation.StoryboardProductionInsert
  lazy val StoryboardProductionInsert = CozyVideoImplementation.StoryboardProductionInsert
  type StoryboardPronunciationNote = CozyVideoImplementation.StoryboardPronunciationNote
  lazy val StoryboardPronunciationNote = CozyVideoImplementation.StoryboardPronunciationNote
  type StoryboardDiagnostic = CozyVideoImplementation.StoryboardDiagnostic
  lazy val StoryboardDiagnostic = CozyVideoImplementation.StoryboardDiagnostic
  type StoryboardResult = CozyVideoImplementation.StoryboardResult
  lazy val StoryboardResult = CozyVideoImplementation.StoryboardResult

  type VideoToolMode = CozyVideoImplementation.VideoToolMode
  lazy val VideoToolMode = CozyVideoImplementation.VideoToolMode
  type VideoToolStatus = CozyVideoImplementation.VideoToolStatus
  lazy val VideoToolStatus = CozyVideoImplementation.VideoToolStatus
  type VideoToolCheck = CozyVideoImplementation.VideoToolCheck
  lazy val VideoToolCheck = CozyVideoImplementation.VideoToolCheck
  type VideoToolContext = CozyVideoImplementation.VideoToolContext
  lazy val VideoToolContext = CozyVideoImplementation.VideoToolContext
  type VideoExecutionConfig = CozyVideoImplementation.VideoExecutionConfig
  lazy val VideoExecutionConfig = CozyVideoImplementation.VideoExecutionConfig
  type VideoToolProvider = CozyVideoImplementation.VideoToolProvider
  type VideoToolRegistry = CozyVideoImplementation.VideoToolRegistry
  lazy val VideoToolRegistry = CozyVideoImplementation.VideoToolRegistry
  type UncheckedToolProvider = CozyVideoImplementation.UncheckedToolProvider
  lazy val UncheckedToolProvider = CozyVideoImplementation.UncheckedToolProvider
  type VideoCommandResult = CozyVideoImplementation.VideoCommandResult
  lazy val VideoCommandResult = CozyVideoImplementation.VideoCommandResult
  type VideoHttpResult = CozyVideoImplementation.VideoHttpResult
  lazy val VideoHttpResult = CozyVideoImplementation.VideoHttpResult
  type VideoToolProbe = CozyVideoImplementation.VideoToolProbe
  lazy val VideoToolProbe = CozyVideoImplementation.VideoToolProbe
  type VoicevoxClient = CozyVideoImplementation.VoicevoxClient
  lazy val VoicevoxClient = CozyVideoImplementation.VoicevoxClient
  type NarrationAudio = CozyVideoImplementation.NarrationAudio
  lazy val NarrationAudio = CozyVideoImplementation.NarrationAudio
  type NarrationProvider = CozyVideoImplementation.NarrationProvider
  type DockerToolchainProvider = CozyVideoImplementation.DockerToolchainProvider
  lazy val DockerToolchainProvider = CozyVideoImplementation.DockerToolchainProvider
  type DockerImageProvider = CozyVideoImplementation.DockerImageProvider
  lazy val DockerImageProvider = CozyVideoImplementation.DockerImageProvider
  type TextusToolchainImageProvider = CozyVideoImplementation.TextusToolchainImageProvider
  lazy val TextusToolchainImageProvider = CozyVideoImplementation.TextusToolchainImageProvider
  type VoicevoxProvider = CozyVideoImplementation.VoicevoxProvider
  lazy val VoicevoxProvider = CozyVideoImplementation.VoicevoxProvider
  type MacosSayProvider = CozyVideoImplementation.MacosSayProvider
  lazy val MacosSayProvider = CozyVideoImplementation.MacosSayProvider
  type PiperProvider = CozyVideoImplementation.PiperProvider
  lazy val PiperProvider = CozyVideoImplementation.PiperProvider
  type FfmpegProvider = CozyVideoImplementation.FfmpegProvider
  lazy val FfmpegProvider = CozyVideoImplementation.FfmpegProvider
  type RemotionNodeProvider = CozyVideoImplementation.RemotionNodeProvider
  lazy val RemotionNodeProvider = CozyVideoImplementation.RemotionNodeProvider
  type PlaywrightProvider = CozyVideoImplementation.PlaywrightProvider
  lazy val PlaywrightProvider = CozyVideoImplementation.PlaywrightProvider
  type WhisperCppProvider = CozyVideoImplementation.WhisperCppProvider
  lazy val WhisperCppProvider = CozyVideoImplementation.WhisperCppProvider
  type PythonPillowProvider = CozyVideoImplementation.PythonPillowProvider
  lazy val PythonPillowProvider = CozyVideoImplementation.PythonPillowProvider

  type VideoArtifactStatus = CozyVideoImplementation.VideoArtifactStatus
  lazy val VideoArtifactStatus = CozyVideoImplementation.VideoArtifactStatus
  type VideoArtifactPlan = CozyVideoImplementation.VideoArtifactPlan
  lazy val VideoArtifactPlan = CozyVideoImplementation.VideoArtifactPlan
  type VideoCommandPlan = CozyVideoImplementation.VideoCommandPlan
  lazy val VideoCommandPlan = CozyVideoImplementation.VideoCommandPlan
  type VideoTranscriptSegment = CozyVideoImplementation.VideoTranscriptSegment
  lazy val VideoTranscriptSegment = CozyVideoImplementation.VideoTranscriptSegment
  type VideoTranscriptionResult = CozyVideoImplementation.VideoTranscriptionResult
  lazy val VideoTranscriptionResult = CozyVideoImplementation.VideoTranscriptionResult
  type VideoReviewEvidenceResult = CozyVideoImplementation.VideoReviewEvidenceResult
  lazy val VideoReviewEvidenceResult = CozyVideoImplementation.VideoReviewEvidenceResult
  type VideoTranscribeExecution = CozyVideoImplementation.VideoTranscribeExecution
  lazy val VideoTranscribeExecution = CozyVideoImplementation.VideoTranscribeExecution
  type VideoPartPlan = CozyVideoImplementation.VideoPartPlan
  lazy val VideoPartPlan = CozyVideoImplementation.VideoPartPlan
  type VideoPlan = CozyVideoImplementation.VideoPlan
  lazy val VideoPlan = CozyVideoImplementation.VideoPlan
  type VideoAudioManifestEntry = CozyVideoImplementation.VideoAudioManifestEntry
  lazy val VideoAudioManifestEntry = CozyVideoImplementation.VideoAudioManifestEntry
  type VideoSynthesisResult = CozyVideoImplementation.VideoSynthesisResult
  lazy val VideoSynthesisResult = CozyVideoImplementation.VideoSynthesisResult
  type VideoRenderedPart = CozyVideoImplementation.VideoRenderedPart
  lazy val VideoRenderedPart = CozyVideoImplementation.VideoRenderedPart
  type VideoRenderResult = CozyVideoImplementation.VideoRenderResult
  lazy val VideoRenderResult = CozyVideoImplementation.VideoRenderResult
  type VideoBuildResult = CozyVideoImplementation.VideoBuildResult
  lazy val VideoBuildResult = CozyVideoImplementation.VideoBuildResult
  type VideoRdfResult = CozyVideoImplementation.VideoRdfResult
  lazy val VideoRdfResult = CozyVideoImplementation.VideoRdfResult
  type VideoDemoScriptResult = CozyVideoImplementation.VideoDemoScriptResult
  lazy val VideoDemoScriptResult = CozyVideoImplementation.VideoDemoScriptResult
  type VideoReplayResult = CozyVideoImplementation.VideoReplayResult
  lazy val VideoReplayResult = CozyVideoImplementation.VideoReplayResult
  type VideoAudioInput = CozyVideoImplementation.VideoAudioInput
  lazy val VideoAudioInput = CozyVideoImplementation.VideoAudioInput
  type VideoSimpleJava2dInput = CozyVideoImplementation.VideoSimpleJava2dInput
  lazy val VideoSimpleJava2dInput = CozyVideoImplementation.VideoSimpleJava2dInput
  type VideoProcessRunner = CozyVideoImplementation.VideoProcessRunner
  lazy val VideoProcessRunner = CozyVideoImplementation.VideoProcessRunner

  def execute(args: List[String]): Boolean =
    CozyVideoImplementation.execute(args)
  def execute(args: List[String], tools: VideoToolRegistry): Boolean =
    CozyVideoImplementation.execute(args, tools)
  def execute(args: List[String], tools: VideoToolRegistry, voicevox: VoicevoxClient): Boolean =
    CozyVideoImplementation.execute(args, tools, voicevox)
  def execute(args: List[String], tools: VideoToolRegistry, voicevox: VoicevoxClient, runner: VideoProcessRunner): Boolean =
    CozyVideoImplementation.execute(args, tools, voicevox, runner)

  def inspect(config: InspectConfig, tools: VideoToolRegistry): String =
    CozyVideoImplementation.inspect(config, tools)
  def build(config: BuildConfig, tools: VideoToolRegistry): String =
    CozyVideoImplementation.build(config, tools)
  def build(config: BuildConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String =
    CozyVideoImplementation.build(config, tools, runner)
  def synthesize(config: SynthesizeConfig, voicevox: VoicevoxClient): String =
    CozyVideoImplementation.synthesize(config, voicevox)
  def synthesize(config: SynthesizeConfig, tools: VideoToolRegistry, voicevox: VoicevoxClient): String =
    CozyVideoImplementation.synthesize(config, tools, voicevox)
  def synthesize(config: SynthesizeConfig, tools: VideoToolRegistry, voicevox: VoicevoxClient, runner: VideoProcessRunner): String =
    CozyVideoImplementation.synthesize(config, tools, voicevox, runner)
  def render(config: RenderConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String =
    CozyVideoImplementation.render(config, tools, runner)
  def transcribe(config: TranscribeConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String =
    CozyVideoImplementation.transcribe(config, tools, runner)
  def rdf(config: RdfConfig): String =
    CozyVideoImplementation.rdf(config)
  def reviewEvidence(config: ReviewEvidenceConfig, tools: VideoToolRegistry): String =
    CozyVideoImplementation.reviewEvidence(config, tools)
  def reviewEvidence(config: ReviewEvidenceConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String =
    CozyVideoImplementation.reviewEvidence(config, tools, runner)
  def verifyCredits(projectfile: Path): Vector[String] =
    CozyVideoImplementation.verifyCredits(projectfile)
  def demoScript(config: DemoScriptConfig): String =
    CozyVideoImplementation.demoScript(config)
  def replay(config: ReplayConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String =
    CozyVideoImplementation.replay(config, tools, runner)
  def loadStoryboard(source: Path): StoryboardResult =
    CozyVideoImplementation.loadStoryboard(source)
  def parseStoryboard(source: Path, text: String): StoryboardResult =
    CozyVideoImplementation.parseStoryboard(source, text)
  def validateStoryboard(storyboard: Storyboard): Vector[StoryboardDiagnostic] =
    CozyVideoImplementation.validateStoryboard(storyboard)
  def canonicalStoryboardJson(storyboard: Storyboard): String =
    CozyVideoImplementation.canonicalStoryboardJson(storyboard)
  def canonicalStoryboardMarkdown(storyboard: Storyboard): String =
    CozyVideoImplementation.canonicalStoryboardMarkdown(storyboard)
  def storyboardIdentity(storyboard: Storyboard): String =
    CozyVideoImplementation.storyboardIdentity(storyboard)
  def storyboardValidate(config: StoryboardValidateConfig): String =
    CozyVideoImplementation.storyboardValidate(config)
  def storyboardInspect(config: StoryboardInspectConfig): String =
    CozyVideoImplementation.storyboardInspect(config)
  def storyboardConvert(config: StoryboardConvertConfig): String =
    CozyVideoImplementation.storyboardConvert(config)
}

private[cozy] object CozyVideoImplementation
  extends CozyVideoTypes
  with CozyVideoStoryboard
  with CozyVideoRuntime
  with CozyVideoCommand
  with CozyVideoNarration
  with CozyVideoToolValidation
  with CozyVideoTranscription
  with CozyVideoReviewEvidence
  with CozyVideoBuildReplay
  with CozyVideoRdf
  with CozyVideoRenderWorkspace
  with CozyVideoRenderTemplates
  with CozyVideoPlanning
  with CozyVideoPresentation
