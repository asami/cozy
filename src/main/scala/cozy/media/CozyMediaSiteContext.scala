package cozy.media

import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal
import org.goldenport.RAISE
import org.goldenport.cli.spec
import cozy.runtime.CozyCliArgs

/*
 * @since   Sep.  7, 2026
 * @version Sep.  7, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaSiteContext {
  final case class SiteContext(
    root: Path,
    config: Path,
    source: Path,
    configRoute: String,
    documentRoute: String
  )

  final case class CommandConfig(
    descriptorFile: Path,
    target: Option[String] = None,
    profile: Option[String] = None,
    dryRun: Boolean = false,
    siteRoot: Option[Path] = None,
    siteConfig: Option[Path] = None
  ) {
    def siteContextRequest: Option[(Path, Path)] =
      request(siteRoot, siteConfig)
  }
  object CommandConfig {
    def create(args: List[String], requireProfile: Boolean = false): CommandConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_media_file, _p_target, _p_profile, _p_site_root, _p_site_config, _p_dry_run)(_normalize_property_args(args))
      val descriptorfile = parsed.argument("media-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing media descriptor")
      )
      val profile = parsed.property("profile")
      if (requireProfile && profile.isEmpty)
        RAISE.invalidArgumentFault("Missing --profile for media publish")
      val siteroot = parsed.property("site-root")
      val siteconfig = parsed.property("site-config")
      (siteroot, siteconfig) match {
        case (None, None) => ()
        case (Some(_), Some(_)) => ()
        case _ => RAISE.invalidArgumentFault("Media --site-root and --site-config must be supplied together")
      }
      CommandConfig(
        descriptorfile,
        parsed.property("target"),
        profile,
        parsed.flag("dry-run"),
        siteroot.map(value => _site_path(value, "site-root")),
        siteconfig.map(value => _site_path(value, "site-config"))
      )
    }

    private def _site_path(value: String, name: String): Path = {
      val path = try Path.of(value) catch {
        case NonFatal(_) => RAISE.invalidArgumentFault(s"Media --$name must be a valid absolute path")
      }
      if (!path.isAbsolute)
        RAISE.invalidArgumentFault(s"Media --$name must be an absolute path")
      CozyCliArgs.toPath(path)
    }
  }

  private val _p_media_file = spec.Parameter.argumentFile("media-file")
  private val _p_target = spec.Parameter.property("target")
  private val _p_profile = spec.Parameter.property("profile")
  private val _p_site_root = spec.Parameter.property("site-root")
  private val _p_site_config = spec.Parameter.property("site-config")
  private val _p_dry_run = spec.Parameter("dry-run", spec.Parameter.SwitchKind)
  private val _property_options = Set("target", "profile", "site-root", "site-config")

  def request(siteRoot: Option[Path], siteConfig: Option[Path]): Option[(Path, Path)] =
    (siteRoot, siteConfig) match {
      case (None, None) => None
      case (Some(root), Some(config)) if root != null && config != null => Some(root -> config)
      case _ => _invalid("Media --site-root and --site-config must be supplied together")
    }

  def resolve(
    request: Option[(Path, Path)],
    profile: Option[CozyMedia.EffectiveProfile],
    source: Path
  ): Option[SiteContext] =
    request.map { case (suppliedroot, suppliedconfig) =>
      val effectiveprofile = profile.getOrElse(
        _invalid("Media site context requires a selected configured SmartDox publication profile")
      )
      val configuredprofile = effectiveprofile.configuration.getOrElse(
        _invalid(s"Media site context requires a configured SmartDox publication profile: ${effectiveprofile.id}")
      )
      if (configuredprofile.siteKind != "smartdox")
        _invalid(s"Media site context requires configured SmartDox site-kind for profile: ${effectiveprofile.id}")
      val root = _canonical_direct_directory(suppliedroot, "Media site root")
      val profileroot = _canonical_direct_directory(effectiveprofile.resolvedRoot, s"Media selected SmartDox profile root: ${effectiveprofile.id}")
      if (!root.startsWith(profileroot))
        _invalid(s"Media site root must be contained by the selected SmartDox profile root: ${effectiveprofile.id}")
      val config = _canonical_direct_file(suppliedconfig, "Media site config")
      if (!config.getFileName.toString.endsWith(".conf"))
        _invalid(s"Media site config must be a nonempty .conf file: $config")
      val configsize = try Files.size(config) catch {
        case NonFatal(_) => _invalid(s"Media site config must be a current nonempty .conf file: $config")
      }
      if (configsize == 0)
        _invalid(s"Media site config must be a nonempty .conf file: $config")
      val canonicalsource = _canonical_direct_file(source, "Media site document source")
      val configroute = _site_relative_route(root, config, "Media site config")
      val documentroute = _site_relative_route(root, canonicalsource, "Media site document source")
      SiteContext(root, config, canonicalsource, configroute, documentroute)
    }

  private def _canonical_direct_directory(path: Path, label: String): Path = {
    val absolute = _absolute_site_path(path, label)
    if (Files.isSymbolicLink(absolute) || !Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"$label must be a direct non-symlink directory: $absolute")
    try absolute.toRealPath() catch {
      case NonFatal(_) => _invalid(s"$label must be a current direct non-symlink directory: $absolute")
    }
  }

  private def _canonical_direct_file(path: Path, label: String): Path = {
    val absolute = _absolute_site_path(path, label)
    if (Files.isSymbolicLink(absolute) || !Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"$label must be a direct regular non-symlink file: $absolute")
    try absolute.toRealPath() catch {
      case NonFatal(_) => _invalid(s"$label must be a current direct regular non-symlink file: $absolute")
    }
  }

  private def _absolute_site_path(path: Path, label: String): Path = {
    val value = Option(path).getOrElse(_invalid(s"$label must be defined"))
    if (!value.isAbsolute)
      _invalid(s"$label must be an absolute path")
    value.normalize()
  }

  private def _site_relative_route(root: Path, path: Path, label: String): String = {
    if (!path.startsWith(root) || path == root)
      _invalid(s"$label must be contained by the canonical site root")
    root.relativize(path).toString.replace('\\', '/')
  }

  private def _normalize_property_args(args: List[String]): List[String] =
    args.flatMap {
      case x if x.startsWith("--") && x.contains("=") =>
        val keyvalue = x.drop(2).split("=", 2)
        if (keyvalue.length == 2 && _property_options.contains(keyvalue(0)))
          List("--" + keyvalue(0), keyvalue(1))
        else
          List(x)
      case x =>
        List(x)
    }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
