package cozy.publication

import java.nio.file.{Files, LinkOption, Path}
import org.goldenport.RAISE
import scala.util.control.NonFatal

/*
 * @since   Aug. 12, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaWipCommand {
  final case class Config(
    descriptorFile: Path,
    publicationRoot: Path,
    websiteRoot: Path,
    target: Option[String] = None,
    dryRun: Boolean = false
  )

  object Config {
    def create(args: List[String]): Config = {
      if (args == null)
        _invalid("Article-media WIP command arguments must be defined")
      var descriptor: Option[String] = None
      var publication: Option[String] = None
      var website: Option[String] = None
      var target: Option[String] = None
      var dryrun = false
      var index = 0
      while (index < args.size) {
        val argument = args(index)
        if (argument == null)
          _invalid("Article-media WIP command argument must be defined")
        argument match {
          case "--publication" =>
            publication = _unique_option(publication, _option_value(args, index, "--publication"), "--publication")
            index += 2
          case value if value.startsWith("--publication=") =>
            publication = _unique_option(publication, _exact_value(value.drop("--publication=".length), "--publication"), "--publication")
            index += 1
          case "--website" =>
            website = _unique_option(website, _option_value(args, index, "--website"), "--website")
            index += 2
          case value if value.startsWith("--website=") =>
            website = _unique_option(website, _exact_value(value.drop("--website=".length), "--website"), "--website")
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
            _invalid("--profile is not supported by media register-site-wip")
          case value if value.startsWith("--profile=") =>
            _invalid("--profile is not supported by media register-site-wip")
          case value if value.startsWith("-") =>
            _invalid(s"Unknown media register-site-wip option: $value")
          case value =>
            if (descriptor.nonEmpty)
              _invalid("media register-site-wip requires exactly one media-file")
            descriptor = Some(_exact_value(value, "media-file"))
            index += 1
        }
      }
      val descriptorfile = descriptor.map(_host_path(_, "media-file")).getOrElse(
        _invalid("Missing media-file for media register-site-wip")
      )
      val publicationroot = publication.map(x => _direct_root(_host_path(x, "--publication"), "publication root")).getOrElse(
        _invalid("Missing --publication for media register-site-wip")
      )
      val websiteroot = website.map(x => _direct_root(_host_path(x, "--website"), "website root")).getOrElse(
        _invalid("Missing --website for media register-site-wip")
      )
      Config(descriptorfile, publicationroot, websiteroot, target, dryrun)
    }
  }

  def execute(args: List[String]): String =
    execute(Config.create(args))

  def execute(config: Config): String = {
    if (config == null || config.descriptorFile == null || config.publicationRoot == null ||
      config.websiteRoot == null || config.target == null)
      _invalid("Article-media WIP command configuration must be defined")
    val plan = CozyArticleMediaWipBinding.plan(CozyArticleMediaWipBinding.Config(
      config.descriptorFile,
      config.publicationRoot,
      config.websiteRoot,
      config.target
    ))
    val result = CozyArticleMediaWipTransaction.execute(plan, config.dryRun)
    _render(result.plan, config.dryRun)
  }

  private def _render(plan: CozyArticleMediaWipBinding.Plan, dryrun: Boolean): String =
    (Vector(
      "Cozy Media Register Site WIP",
      s"descriptor: ${plan.descriptorEvidence.path}",
      s"publication: ${plan.publicationRoot.identity}",
      s"website: ${plan.websiteRoot.identity}",
      s"articleIdentity: ${plan.articleIdentity}",
      s"mode: ${if (dryrun) "dry-run" else "registered"}"
    ) ++ plan.candidates.sortBy(_.resourceId).map { candidate =>
      val action = candidate.evidence match {
        case _: CozyArticleMediaWipBinding.InfographicEvidence => "reuse"
        case _: CozyArticleMediaWipBinding.PdfEvidence => "reuse"
        case video: CozyArticleMediaWipBinding.VideoEvidence => s"install=${video.destination}"
      }
      s"  - ${candidate.resourceId}: locale=${candidate.locale}, role=${candidate.role.name}, $action"
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

  private def _direct_root(path: Path, label: String): Path = {
    val lexical = Option(path).map(_.toAbsolutePath.normalize()).getOrElse(
      _invalid(s"Article-media WIP $label must be defined")
    )
    if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media WIP $label must be an existing direct non-symlink directory: $lexical")
    val identity = try lexical.toRealPath() catch {
      case NonFatal(e) => _invalid(s"Article-media WIP $label cannot be resolved: ${e.getMessage}")
    }
    if (identity != lexical)
      _invalid(s"Article-media WIP $label must not use a lexical or symlink alias: $lexical")
    identity
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
