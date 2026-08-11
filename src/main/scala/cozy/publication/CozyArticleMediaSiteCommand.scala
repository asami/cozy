package cozy.publication

import java.nio.file.{Files, LinkOption, Path}
import org.goldenport.RAISE
import scala.util.control.NonFatal

/*
 * @since   Aug. 11, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaSiteCommand {
  final case class Config(
    descriptorFile: Path,
    publicationRoot: Path,
    target: Option[String] = None,
    dryRun: Boolean = false
  )

  object Config {
    def create(args: List[String]): Config = {
      if (args == null)
        _invalid("Article-media site command arguments must be defined")
      var descriptor: Option[String] = None
      var publication: Option[String] = None
      var target: Option[String] = None
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
      Config(descriptorfile, _direct_publication_root(publicationroot), target, dryrun)
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
    if (config == null || config.descriptorFile == null || config.publicationRoot == null || config.target == null)
      _invalid("Article-media site command configuration must be defined")
    if (beforeEvidenceRevalidation == null)
      _invalid("Article-media site command before-evidence-revalidation callback must be defined")
    val rootevidence = CozyPublicationCompiler.captureSiteRootEvidence(config.publicationRoot)
    val publicationroot = rootevidence.real
    val plan = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
      descriptorFile = config.descriptorFile,
      target = config.target
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
