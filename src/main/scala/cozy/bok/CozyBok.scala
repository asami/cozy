package cozy.bok

import java.nio.file.Path
import cozy.publication.CozyArticleMediaInfographicEvidence
import cozy.video.{CozyVideo, CozyVideoPublisher}

/*
 * @since   Jun.  3, 2026
 *  version Jul. 23, 2026
 * @version Aug. 23, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyBok {
  type LocaleMode = CozyBokImplementation.LocaleMode
  lazy val LocaleMode = CozyBokImplementation.LocaleMode
  type CreateConfig = CozyBokImplementation.CreateConfig
  lazy val CreateConfig = CozyBokImplementation.CreateConfig
  type CategoryConfig = CozyBokImplementation.CategoryConfig
  lazy val CategoryConfig = CozyBokImplementation.CategoryConfig
  type BokGoal = CozyBokImplementation.BokGoal
  lazy val BokGoal = CozyBokImplementation.BokGoal
  type BokPurpose = CozyBokImplementation.BokPurpose
  lazy val BokPurpose = CozyBokImplementation.BokPurpose
  type CategoryArticle = CozyBokImplementation.CategoryArticle
  lazy val CategoryArticle = CozyBokImplementation.CategoryArticle
  type CategoryTerm = CozyBokImplementation.CategoryTerm
  lazy val CategoryTerm = CozyBokImplementation.CategoryTerm
  type DoctorConfig = CozyBokImplementation.DoctorConfig
  lazy val DoctorConfig = CozyBokImplementation.DoctorConfig
  type PreviewConfig = CozyBokImplementation.PreviewConfig
  lazy val PreviewConfig = CozyBokImplementation.PreviewConfig
  type BuildConfig = CozyBokImplementation.BuildConfig
  lazy val BuildConfig = CozyBokImplementation.BuildConfig
  type ArcadiaConfig = CozyBokImplementation.ArcadiaConfig
  lazy val ArcadiaConfig = CozyBokImplementation.ArcadiaConfig
  type DirectAssetsConfig = CozyBokImplementation.DirectAssetsConfig
  lazy val DirectAssetsConfig = CozyBokImplementation.DirectAssetsConfig
  type DirectAsset = CozyBokImplementation.DirectAsset
  lazy val DirectAsset = CozyBokImplementation.DirectAsset
  type PublicationSettings = CozyBokImplementation.PublicationSettings
  lazy val PublicationSettings = CozyBokImplementation.PublicationSettings
  type PublicationConfig = CozyBokImplementation.PublicationConfig
  lazy val PublicationConfig = CozyBokImplementation.PublicationConfig
  type WorkflowConfig = CozyBokImplementation.WorkflowConfig
  lazy val WorkflowConfig = CozyBokImplementation.WorkflowConfig
  type WebsiteBackupConfig = CozyBokImplementation.WebsiteBackupConfig
  lazy val WebsiteBackupConfig = CozyBokImplementation.WebsiteBackupConfig
  type SiteConfig = CozyBokImplementation.SiteConfig
  lazy val SiteConfig = CozyBokImplementation.SiteConfig
  type ProjectFilePolicy = CozyBokImplementation.ProjectFilePolicy
  lazy val ProjectFilePolicy = CozyBokImplementation.ProjectFilePolicy
  type Runner = CozyBokImplementation.Runner
  lazy val ProcessRunner = CozyBokImplementation.ProcessRunner

  def execute(args: List[String]): Boolean = CozyBokImplementation.execute(args)
  def create(config: CreateConfig): Unit = CozyBokImplementation.create(config)
  def doctor(config: DoctorConfig): Unit = CozyBokImplementation.doctor(config)
  def guide(args: List[String]): Unit = CozyBokImplementation.guide(args)
  def searchBibliography(config: BibliographySearchConfig, registry: BibliographySearchRegistry): String =
    CozyBokImplementation.searchBibliography(config, registry)
  def updateBibliography(config: BibliographyUpdateConfig, fetcher: BibliographyBibtexFetcher): String =
    CozyBokImplementation.updateBibliography(config, fetcher)
  def createCategory(config: CategoryConfig): Unit = CozyBokImplementation.createCategory(config)
  def build(config: BuildConfig, runner: Runner): Unit = CozyBokImplementation.build(config, runner)
  def build(config: BuildConfig, runner: Runner, bibliographyfetcher: BibliographyBibtexFetcher): Unit =
    CozyBokImplementation.build(config, runner, bibliographyfetcher)
  def finalizeMetadata(config: BuildConfig): Unit = CozyBokImplementation.finalizeMetadata(config)
  def preview(args: List[String], runner: Runner): Unit = CozyBokImplementation.preview(args, runner)
  def runWorkflow(config: WorkflowConfig, runner: Runner): Unit = CozyBokImplementation.runWorkflow(config, runner)
  def publishVideo(config: PublicationConfig, voicevox: CozyVideo.VoicevoxClient, videorunner: CozyVideo.VideoProcessRunner): Vector[CozyVideoPublisher.PublishVideoResult] =
    CozyBokImplementation.publishVideo(config, voicevox, videorunner)
  def publishMedia(config: PublicationConfig): Vector[CozyArticleMediaInfographicEvidence.Completion] =
    CozyBokImplementation.publishMedia(config)
  def publishProjects(config: PublicationConfig): Vector[CozyBokProjectPublisher.PublishProjectResult] =
    CozyBokImplementation.publishProjects(config)
  def updatePublication(config: PublicationConfig, voicevox: CozyVideo.VoicevoxClient, videorunner: CozyVideo.VideoProcessRunner): Vector[String] =
    CozyBokImplementation.updatePublication(config, voicevox, videorunner)
  def publish(config: PublicationConfig, runner: Runner, voicevox: CozyVideo.VoicevoxClient, videorunner: CozyVideo.VideoProcessRunner): Unit =
    CozyBokImplementation.publish(config, runner, voicevox, videorunner)
}

private[cozy] object CozyBokImplementation
  extends CozyBokConfig
  with CozyBokModel
  with CozyBokBuildConfig
  with CozyBokCommand
  with CozyBokBibliography
  with CozyBokBibliographyRdf
  with CozyBokBuild
  with CozyBokPublication
  with CozyBokSiteBuild
  with CozyBokSitePages
  with CozyBokProjectPages
  with CozyBokRepositoryMetadata
  with CozyBokRepositoryCatalog
  with CozyBokRepositoryPages
  with CozyBokSieMetadata
  with CozyBokGlossaryPages
  with CozyBokRdfPages
  with CozyBokRdfViewer
  with CozyBokGlossaryWorkflow
  with CozyBokGlossaryAnalysis
  with CozyBokTagPages
  with CozyBokBibliographyPages
  with CozyBokScenarioTermHub
  with CozyBokLocalizedGlossary
  with CozyBokHtmlPages
  with CozyBokSiteDocument
  with CozyBokDashboardCore
  with CozyBokDashboardAnalysis
  with CozyBokMetadata
  with CozyBokFileSupport
  with CozyBokUiAssets
  with CozyBokProjectResolution
  with CozyBokScaffold
