package cozy.media

import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.Locale
import scala.util.control.NonFatal
import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource

/*
 * @since   Oct.  6, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaWarehouse {
  sealed abstract class Status(val label: String)
  object Status {
    case object Planned extends Status("Planned")
    case object Unavailable extends Status("Unavailable")
    case object Created extends Status("Created")
  }

  final case class DeploymentItem(
    resourceId: String,
    articleIdentity: String,
    locale: String,
    role: String,
    source: Path,
    destination: Path,
    relativePath: String,
    publicPath: String,
    status: Status
  )
  final case class DeploymentPlan(resources: Vector[DeploymentItem])
  final case class DeploymentResult(resources: Vector[DeploymentItem], dryRun: Boolean)

  final case class DeployConfig(command: CozyMedia.CommandConfig, warehouseRoot: Option[Path] = None)
  object DeployConfig {
    def create(args: List[String]): DeployConfig = {
      if (args == null || args.exists(_ == null))
        _invalid("Media deploy arguments must be defined")
      val remaining = List.newBuilder[String]
      var root: Option[Path] = None
      var rest = args
      while (rest.nonEmpty) {
        rest match {
          case "--warehouse-root" :: tail =>
            if (root.nonEmpty) _invalid("Duplicate --warehouse-root")
            if (tail.isEmpty || tail.head.startsWith("--")) _invalid("Missing value for --warehouse-root")
            root = Some(requireWarehouseRoot(tail.head))
            rest = tail.tail
          case value :: tail if value.startsWith("--warehouse-root=") =>
            if (root.nonEmpty) _invalid("Duplicate --warehouse-root")
            root = Some(requireWarehouseRoot(value.drop("--warehouse-root=".length)))
            rest = tail
          case value :: tail =>
            remaining += value
            rest = tail
          case Nil => ()
        }
      }
      DeployConfig(CozyMedia.CommandConfig.create(remaining.result(), requireProfile = true), root)
    }
  }

  def requireWarehouseRoot(value: String): Path = {
    if (value == null || value.isEmpty || value != value.trim)
      _invalid("--warehouse-root must be a nonblank exact absolute path")
    val path = try Paths.get(value) catch {
      case NonFatal(_) => _invalid("--warehouse-root must be a valid absolute path")
    }
    _require_warehouse_root(path)
  }

  def canonicalPublicPath(articleIdentity: String, locale: String, role: String, extension: String): String = {
    if (articleIdentity == null) _invalid("Media articleIdentity must be defined")
    _require_identity(articleIdentity)
    if (!Set("ja", "en").contains(locale)) _invalid(s"Media locale must be ja or en: $locale")
    _public_path(articleIdentity, locale, _canonical_basename(role, extension))
  }

  def plan(config: CozyMedia.CommandConfig, environment: Map[String, String] = sys.env): DeploymentPlan =
    _plan(config, None, environment)

  def planAtRoot(config: CozyMedia.CommandConfig, warehouseRoot: Path, environment: Map[String, String] = sys.env): DeploymentPlan =
    _plan(config, Some(_require_warehouse_root(warehouseRoot)), environment)

  private def _plan(config: CozyMedia.CommandConfig, warehouseroot: Option[Path], environment: Map[String, String]): DeploymentPlan = {
    if (config.siteRoot.nonEmpty || config.siteConfig.nonEmpty) {
      _invalid("Media deploy does not support site context (--site-root / --site-config)")
    }
    val descriptorfile = config.descriptorFile.toAbsolutePath.normalize()
    _require_direct_path(descriptorfile)
    _require_file(descriptorfile, "Media descriptor")
    val descriptor = StructuredDocumentLoader.loadDocument[CozyMedia.Descriptor](InputSource(descriptorfile.toFile)).take
    if (descriptor.schema != "cozy.media.v1") {
      _invalid(s"Media deploy requires schema cozy.media.v1: ${descriptor.schema}")
    }
    if (descriptor.resources.map(_.id).distinct.size != descriptor.resources.size) {
      _invalid("Media deploy requires unique resource IDs")
    }
    val identity = descriptor.articleMedia.map(_.articleIdentity).getOrElse(
      _invalid("Media deploy requires explicit articleMedia.articleIdentity")
    )
    _require_identity(identity)
    val profileid = config.profile.getOrElse(_invalid("Media deploy requires an explicit profile"))
    val profile = descriptor.profiles.getOrElse(profileid, _invalid(s"Media deploy profile is not declared: $profileid"))
    val descriptorroot = descriptorfile.getParent
    val root = warehouseroot.getOrElse(_profile_root(profile, profileid, descriptorroot, environment))
    val selected = config.target match {
      case Some(id) =>
        val resource = descriptor.resources.find(_.id == id).getOrElse(_invalid(s"Unknown media deploy target: $id"))
        if (!resource.publications.contains(profileid)) {
          _invalid(s"Media deploy target has no publication for $profileid: $id")
        }
        Vector(resource)
      case None => descriptor.resources.filter(_.publications.contains(profileid))
    }
    if (selected.isEmpty) {
      _invalid(s"No media deploy resources mapped to profile: $profileid")
    }
    val resources = selected.map(resource => _resolve_item(resource, identity, profileid, descriptorroot, root))
    if (resources.map(_.destination).distinct.size != resources.size) {
      _invalid("Media deploy has duplicate selected destinations")
    }
    DeploymentPlan(resources.map(_preflight_item))
  }

  def deploy(config: CozyMedia.CommandConfig, environment: Map[String, String] = sys.env): DeploymentResult = {
    val prepared = plan(config, environment)
    _copy(config, prepared)
  }

  def deployAtRoot(config: CozyMedia.CommandConfig, warehouseRoot: Path, environment: Map[String, String] = sys.env): DeploymentResult =
    _copy(config, planAtRoot(config, warehouseRoot, environment))

  def deployCommand(config: DeployConfig): DeploymentResult = {
    if (config == null || config.command == null || config.warehouseRoot == null)
      _invalid("Media deploy configuration must be defined")
    config.warehouseRoot match {
      case Some(root) => deployAtRoot(config.command, root)
      case None => deploy(config.command)
    }
  }

  private def _copy(config: CozyMedia.CommandConfig, prepared: DeploymentPlan): DeploymentResult = {
    val resources = prepared.resources.map { item =>
      if (config.dryRun) {
        if (item.status == Status.Unavailable) item else item.copy(status = Status.Planned)
      } else {
        item.status match {
          case Status.Planned =>
            Files.createDirectories(item.destination.getParent)
            Files.copy(item.source, item.destination, StandardCopyOption.REPLACE_EXISTING)
            item.copy(status = Status.Created)
          case _ => item
        }
      }
    }
    DeploymentResult(resources, config.dryRun)
  }

  private def _require_warehouse_root(path: Path): Path = {
    if (path == null || !path.isAbsolute || path != path.normalize() || path.toString.isEmpty || path.toString != path.toString.trim)
      _invalid("--warehouse-root must be an absolute normalized exact path without dot or dotdot segments")
    _require_direct_path(path)
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"--warehouse-root must be an existing direct directory: $path")
    path
  }

  def render(result: DeploymentResult): String = {
    val outcome = if (result.resources.exists(_.status == Status.Unavailable)) "partial" else "complete"
    val operation = if (result.dryRun) "dry-run" else "local placement"
    val rows = result.resources.map { item =>
      s"${item.resourceId}: ${item.status.label} article=${item.articleIdentity} locale=${item.locale} role=${item.role} source=${item.source} destination=${item.destination} public=${item.publicPath}"
    }
    (Vector(s"Media deploy $operation: $outcome (remote publication not performed)") ++ rows).mkString("\n")
  }

  private def _profile_root(profile: CozyMedia.Profile, profileid: String, descriptorroot: Path, environment: Map[String, String]): Path = {
    val declared = profile.rootEnv match {
      case Some(name) =>
        _require_exact_value(name, s"Media profile $profileid rootEnv")
        val value = environment.get(name).getOrElse(_invalid(s"Media profile $profileid requires environment variable: $name"))
        _require_exact_value(value, s"Media profile $profileid environment variable $name")
        Paths.get(value).toAbsolutePath
      case None =>
        val value = profile.root.getOrElse(_invalid(s"Media profile $profileid requires root or rootEnv"))
        _require_exact_value(value, s"Media profile $profileid root")
        Paths.get(value)
    }
    val root = (if (declared.isAbsolute) declared else descriptorroot.resolve(declared)).toAbsolutePath.normalize()
    _require_direct_path(root)
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
      _invalid(s"Media profile $profileid root must be an existing direct directory: $root")
    }
    root
  }

  private def _resolve_item(resource: CozyMedia.Resource, identity: String, profileid: String, descriptorroot: Path, root: Path): DeploymentItem = {
    val locale = resource.language.filter(Set("ja", "en")).getOrElse(_invalid(s"Media deploy requires language ja or en: ${resource.id}"))
    val media = resource.articleMedia.getOrElse(_invalid(s"Media deploy requires articleMedia.role: ${resource.id}"))
    val declaration = resource.output.getOrElse {
      if (!Set("prebuilt", "copy").contains(resource.build)) {
        _invalid(s"Media deploy without output requires build prebuilt or copy: ${resource.id}")
      }
      resource.source.getOrElse(_invalid(s"Media deploy requires output or source: ${resource.id}"))
    }
    _require_exact_value(declaration, s"Media deploy source ${resource.id}")
    val declared = Paths.get(declaration)
    if (declared.isAbsolute) {
      _invalid(s"Media deploy source declaration must be relative: ${resource.id}")
    }
    val sourcepath = descriptorroot.resolve(declared)
    _require_direct_path(sourcepath)
    val source = sourcepath.toAbsolutePath.normalize()
    val basename = _basename(resource, media, source)
    val publicpath = _public_path(identity, locale, basename)
    val coordinate = publicpath.drop(1)
    if (resource.publications(profileid) != coordinate) {
      _invalid(s"Media deploy publication must equal $coordinate: ${resource.id}")
    }
    if (media.role != "video" && media.publicPath != Some(publicpath)) {
      _invalid(s"Media deploy articleMedia.publicPath must equal $publicpath: ${resource.id}")
    }
    val destination = root.resolve(coordinate).normalize()
    if (!destination.startsWith(root) || destination == root) {
      _invalid(s"Media deploy destination must be inside profile root: ${resource.id}")
    }
    _require_direct_path(destination)
    DeploymentItem(resource.id, identity, locale, media.role, source, destination, coordinate, publicpath, Status.Planned)
  }

  private def _basename(resource: CozyMedia.Resource, media: CozyMedia.ResourceArticleMedia, source: Path): String = {
    val filename = source.getFileName.toString
    val dot = filename.lastIndexOf('.')
    val extension = if (dot < 0) "" else filename.substring(dot + 1).toLowerCase(Locale.ROOT)
    media.role match {
      case "article_pdf" | "summary_slides_pdf" =>
        if (resource.kind != "document" || media.mediaType != Some("application/pdf") || extension != "pdf") {
          _invalid(s"Media deploy PDF role requires document, application/pdf and PDF source filename: ${resource.id}")
        }
      case "video" =>
        if (resource.kind != "video" || extension != "mp4") {
          _invalid(s"Media deploy video requires video kind and MP4 source filename: ${resource.id}")
        }
      case "infographic" =>
        val mimetypes = Map("svg" -> "image/svg+xml", "png" -> "image/png", "jpg" -> "image/jpeg", "jpeg" -> "image/jpeg", "webp" -> "image/webp")
        if (resource.kind != "infographic" || !mimetypes.get(extension).exists(value => media.mediaType.contains(value))) {
          _invalid(s"Media deploy infographic requires matching supported image format and MIME: ${resource.id}")
        }
      case _ => _invalid(s"Unsupported media deploy role: ${media.role}")
    }
    _canonical_basename(media.role, extension)
  }

  private def _canonical_basename(role: String, extension: String): String = (role, extension) match {
    case ("article_pdf", "pdf") => "article.pdf"
    case ("summary_slides_pdf", "pdf") => "slides.pdf"
    case ("video", "mp4") => "video.mp4"
    case ("infographic", value) if Set("svg", "png", "jpg", "jpeg", "webp").contains(value) => s"infographic.$value"
    case _ => _invalid(s"Unsupported canonical media role/extension: $role/$extension")
  }

  private def _public_path(identity: String, locale: String, basename: String): String =
    s"/media/$locale/$identity/$basename"

  private def _preflight_item(item: DeploymentItem): DeploymentItem = {
    val available = !Files.notExists(item.source, LinkOption.NOFOLLOW_LINKS)
    if (available) {
      _require_file(item.source, "Media deploy source")
    }
    val existing = !Files.notExists(item.destination, LinkOption.NOFOLLOW_LINKS)
    if (existing) {
      _require_file(item.destination, "Media deploy destination")
    }
    val status = if (!available) Status.Unavailable else Status.Planned
    item.copy(status = status)
  }

  private def _require_direct_path(path: Path): Unit = {
    val absolute = path.toAbsolutePath
    val segments = absolute.iterator()
    var ancestor = absolute.getRoot
    while (segments.hasNext) {
      ancestor = ancestor.resolve(segments.next())
      if (Files.isSymbolicLink(ancestor)) {
        _invalid(s"Media deploy path traverses a symlink: $ancestor")
      }
      if (segments.hasNext && Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(ancestor, LinkOption.NOFOLLOW_LINKS)) {
        _invalid(s"Media deploy path ancestor must be a direct directory: $ancestor")
      }
    }
  }

  private def _require_file(path: Path, label: String): Unit = {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
      _invalid(s"$label must be a direct regular readable file: $path")
    }
  }

  private def _require_identity(identity: String): Unit = {
    val segments = identity.split("/", -1)
    if (segments.length != 2 || segments.exists(value => value == "." || value == ".." || !value.matches("[A-Za-z0-9_-][A-Za-z0-9._-]*"))) {
      _invalid(s"Media deploy articleIdentity must be exactly category/article: $identity")
    }
  }

  private def _require_exact_value(value: String, label: String): Unit = {
    if (value.isEmpty || value != value.trim) {
      _invalid(s"$label must be a nonblank exact value")
    }
  }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
