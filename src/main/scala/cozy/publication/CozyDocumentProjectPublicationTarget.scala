package cozy.publication

import java.nio.file.Path
import cozy.document.CozyDocumentProject
import cozy.document.CozyDocumentProjectExport
import cozy.media.CozyMedia

/*
 * @since   Oct. 1, 2026
 * @version Oct. 1, 2026
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
      ),
      binding.descriptorBytes
    )
    if (mediaplan.descriptor != binding.descriptor || mediaplan.context != binding.context ||
      mediaplan.effectiveProfile != Some(binding.effectiveProfile))
      _invalid("publication registration media authority is inconsistent with the site binding")
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

  private def _invalid(message: String): Nothing = CozyDocumentProject._failure("DP-OP-001", message)
}
