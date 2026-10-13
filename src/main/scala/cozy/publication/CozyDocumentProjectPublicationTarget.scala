package cozy.publication

import java.nio.file.{Files, LinkOption, Path}
import cozy.document.CozyDocumentProject
import cozy.document.CozyDocumentProjectExport
import cozy.media.CozyMedia

/*
 * @since   Oct. 1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectPublicationTarget {
  private val _article_review_path = "work-products/article-review-html/article-review.html"

  sealed trait Target {
    def slug: String
  }

  object Target {
    case object SimpleModelingOrg extends Target {
      val slug = "simplemodeling-org"
    }
  }

  final case class VerifiedExport(
    bundleRoot: Path,
    target: Target,
    evidence: CozyDocumentProjectExport.Bundle,
    articleReview: Path
  ) {
    def root: Path = bundleRoot
    def bundle: CozyDocumentProjectExport.Bundle = evidence
  }

  final case class RegistrationConfig(bundle: Path, media: CozyArticleMediaSiteBinding.Config)

  final case class RegistrationPlan(
    config: RegistrationConfig,
    export: VerifiedExport,
    siteBinding: CozyArticleMediaSiteBinding.Plan,
    siteContext: Option[CozyMedia.SiteContext]
  )

  final case class ProjectRegistrationConfig(project: Path, registration: RegistrationConfig)

  final case class ProjectRegistrationPlan(
    config: ProjectRegistrationConfig,
    registration: RegistrationPlan
  )

  def planProjectRegistration(config: ProjectRegistrationConfig): ProjectRegistrationPlan = {
    if (config == null || config.project == null || config.registration == null)
      _invalid("publication registration requires project and registration configuration")
    val project = _project_path(config.project)
    CozyDocumentProject._load_project(project)
    val registration = planRegistration(config.registration)
    ProjectRegistrationPlan(config.copy(project = project), registration)
  }

  def planRegistration(config: RegistrationConfig): RegistrationPlan = {
    if (config == null || config.media == null)
      _invalid("publication registration requires export and media configuration")
    val admitted = admit(config.bundle)
    val binding = CozyArticleMediaSiteBinding.plan(config.media)
    val mediaplan = CozyMedia.resolvePlan(
      CozyMedia.CommandConfig(
        binding.descriptorEvidence.path,
        target = config.media.target,
        profile = Some(binding.publicationProfile),
        siteRoot = config.media.siteRoot,
        siteConfig = config.media.siteConfig
      )
    )
    val sitecontext = CozyMedia.requireSiteContext(mediaplan)
    RegistrationPlan(config, admitted, binding, sitecontext)
  }

  def admit(bundle: Path): VerifiedExport = {
    if (bundle == null) _invalid("publication target requires an export bundle")
    val normalizedroot = try bundle.toAbsolutePath.normalize() catch {
      case _: RuntimeException => _invalid("publication target export bundle path is invalid")
    }
    val evidence = CozyDocumentProjectExport.verifyBundle(normalizedroot)
    val target = if (evidence.target == Target.SimpleModelingOrg.slug)
      Target.SimpleModelingOrg
    else
      _invalid("publication target export bundle target is not simplemodeling-org")
    VerifiedExport(
      normalizedroot,
      target,
      evidence,
      normalizedroot.resolve(_article_review_path).normalize()
    )
  }

  private def _project_path(project: Path): Path = {
    val normalized = try project.toAbsolutePath.normalize() catch {
      case _: RuntimeException => _invalid("publication project path is invalid")
    }
    if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized) ||
      !Option(normalized.getFileName).exists(_.toString.endsWith(".dox")))
      _invalid("publication project must be a direct canonical .dox directory")
    val canonical = try normalized.toRealPath() catch {
      case _: RuntimeException => _invalid("publication project path is invalid")
    }
    if (canonical != normalized)
      _invalid("publication project must be a direct canonical .dox directory")
    normalized
  }

  private def _invalid(message: String): Nothing = CozyDocumentProject._failure("DP-OP-001", message)
}
