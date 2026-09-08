package cozy.publication

import java.nio.file.{Files, LinkOption, Path}
import org.goldenport.RAISE
import scala.util.control.NonFatal

/*
 * @since   Aug. 11, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaSiteCommand {
  final case class Config(
    descriptorFile: Path,
    publicationRoot: Path,
    target: Option[String] = None,
    dryRun: Boolean = false,
    siteRoot: Option[Path] = None,
    siteConfig: Option[Path] = None
  )

  object Config {
    def create(args: List[String]): Config = {
      if (args == null)
        _invalid("Article-media site command arguments must be defined")
      var descriptor: Option[String] = None
      var publication: Option[String] = None
      var target: Option[String] = None
      var siteroot: Option[String] = None
      var siteconfig: Option[String] = None
      var dryrun = false
      var index = 0
      while (index < args.size) {
        val argument = args(index)
        if (argument == null)
          _invalid("Article-media site command argument must be defined")
        argument match {
          case "--publication" =>
            publication = _unique_option(publication, _option_value(args, index, "--publication"), "--publication")
            index += 2
          case value if value.startsWith("--publication=") =>
            publication = _unique_option(publication, _exact_value(value.drop("--publication=".length), "--publication"), "--publication")
            index += 1
          case "--target" =>
            target = _unique_option(target, _option_value(args, index, "--target"), "--target")
            index += 2
          case value if value.startsWith("--target=") =>
            target = _unique_option(target, _exact_value(value.drop("--target=".length), "--target"), "--target")
            index += 1
          case "--site-root" =>
            siteroot = _unique_option(siteroot, _option_value(args, index, "--site-root"), "--site-root")
            index += 2
          case value if value.startsWith("--site-root=") =>
            siteroot = _unique_option(siteroot, _exact_value(value.drop("--site-root=".length), "--site-root"), "--site-root")
            index += 1
          case "--site-config" =>
            siteconfig = _unique_option(siteconfig, _option_value(args, index, "--site-config"), "--site-config")
            index += 2
          case value if value.startsWith("--site-config=") =>
            siteconfig = _unique_option(siteconfig, _exact_value(value.drop("--site-config=".length), "--site-config"), "--site-config")
            index += 1
          case "--dry-run" =>
            if (dryrun)
              _invalid("Duplicate --dry-run")
            dryrun = true
            index += 1
          case "--profile" =>
            _invalid("--profile is not supported by media register-site")
          case value if value.startsWith("--profile=") =>
            _invalid("--profile is not supported by media register-site")
          case value if value.startsWith("--") =>
            _invalid(s"Unknown media register-site option: $value")
          case value if value.startsWith("-") =>
            _invalid(s"Unknown media register-site option: $value")
          case value =>
            if (descriptor.nonEmpty)
              _invalid("media register-site requires exactly one media-file")
            descriptor = Some(_exact_value(value, "media-file"))
            index += 1
        }
      }
      val descriptorfile = descriptor.map(_host_path(_, "media-file")).getOrElse(
        _invalid("Missing media-file for media register-site")
      )
      val publicationroot = publication.map(_host_path(_, "--publication")).getOrElse(
        _invalid("Missing --publication for media register-site")
      )
      val sitecontext = _site_context(siteroot, siteconfig)
      Config(descriptorfile, _direct_publication_root(publicationroot), target, dryrun, sitecontext._1, sitecontext._2)
    }
  }

  def execute(args: List[String]): String =
    execute(Config.create(args))

  def execute(config: Config): String =
    execute(config, () => ())

  /* The seam lets the executable specification deterministically change
   * admitted evidence in the narrow interval before final revalidation. */
  private[publication] def execute(
    config: Config,
    beforeEvidenceRevalidation: () => Unit
  ): String = {
    if (config == null || config.descriptorFile == null || config.publicationRoot == null || config.target == null ||
      config.siteRoot == null || config.siteConfig == null)
      _invalid("Article-media site command configuration must be defined")
    if (beforeEvidenceRevalidation == null)
      _invalid("Article-media site command before-evidence-revalidation callback must be defined")
    val rootevidence = CozyPublicationCompiler.captureSiteRootEvidence(config.publicationRoot)
    val publicationroot = rootevidence.real
    val plan = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
      descriptorFile = config.descriptorFile,
      target = config.target,
      siteRoot = config.siteRoot,
      siteConfig = config.siteConfig
    ))
    val updates = plan.candidates.map(candidate =>
      CozyArticleMediaRegistry.SiteRoleUpdate(plan.articleIdentity, candidate.variant)
    )
    val revalidate = () => {
      beforeEvidenceRevalidation()
      CozyArticleMediaSiteBinding.revalidate(plan)
      ()
    }
    if (config.dryRun)
      CozyArticleMediaRegistry.validateSiteReadOnly(rootevidence, updates, revalidate)
    else
      CozyArticleMediaRegistry.transactionExisting(rootevidence)(_.mergeSite(updates, revalidate))
    _render(plan, publicationroot, config.dryRun)
  }

  private def _render(
    plan: CozyArticleMediaSiteBinding.Plan,
    publicationroot: Path,
    dryrun: Boolean
  ): String =
    (Vector(
      "Cozy Media Register Site",
      s"descriptor: ${plan.descriptorEvidence.path}",
      s"publication: $publicationroot",
      s"articleIdentity: ${plan.articleIdentity}",
      s"mode: ${if (dryrun) "dry-run" else "registered"}"
    ) ++ plan.candidates.sortBy(_.resourceId).map { candidate =>
      s"  - ${candidate.resourceId}: locale=${candidate.locale}, role=${candidate.role.serializedName}"
    }).mkString("\n")

  private def _option_value(args: List[String], index: Int, option: String): String =
    if (index + 1 >= args.size || args(index + 1) == null || args(index + 1).startsWith("-"))
      _invalid(s"Missing value for $option")
    else
      _exact_value(args(index + 1), option)

  private def _unique_option(current: Option[String], value: String, option: String): Option[String] = {
    if (current.nonEmpty)
      _invalid(s"Duplicate $option")
    Some(value)
  }

  private def _exact_value(value: String, label: String): String = {
    if (value == null || value.isEmpty || value != value.trim)
      _invalid(s"$label must be a non-empty exact value")
    value
  }

  private def _host_path(value: String, label: String): Path =
    try Path.of(value).toAbsolutePath.normalize() catch {
      case NonFatal(_) => _invalid(s"$label path is invalid")
    }

  private def _site_path(value: String, label: String): Path = {
    val path = try Path.of(value) catch {
      case NonFatal(_) => _invalid(s"$label path is invalid")
    }
    if (!path.isAbsolute)
      _invalid(s"$label path must be absolute")
    path.normalize()
  }

  private def _site_context(siteroot: Option[String], siteconfig: Option[String]): (Option[Path], Option[Path]) =
    (siteroot, siteconfig) match {
      case (None, None) => None -> None
      case (Some(root), Some(config)) =>
        Some(_site_path(root, "--site-root")) -> Some(_site_path(config, "--site-config"))
      case _ => _invalid("Media --site-root and --site-config must be supplied together")
    }

  private def _direct_publication_root(path: Path): Path = {
    val lexical = Option(path).map(_.toAbsolutePath.normalize()).getOrElse(
      _invalid("Configured publication root must be defined")
    )
    if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Configured publication root must be an existing direct non-symlink directory: $lexical")
    val identity = try lexical.toRealPath() catch {
      case NonFatal(e) => _invalid(s"Configured publication root cannot be resolved: ${e.getMessage}")
    }
    if (identity != lexical)
      _invalid(s"Configured publication root must not use a lexical or symlink alias: $lexical")
    identity
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
