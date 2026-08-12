package cozy.config

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import io.circe.{Json => CJson}
import org.goldenport.RAISE
import scala.util.control.NonFatal

/*
 * @since   Aug. 12, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyProjectContext {
  final case class DirectoryIdentity(path: Path, identity: Path, filekey: AnyRef) {
    def fileKey: AnyRef = filekey
  }

  final case class FileEvidence(
    path: Path,
    identity: Path,
    sha256: String,
    size: Long,
    filekey: AnyRef
  ) {
    def fileKey: AnyRef = filekey
  }

  final case class Layer(
    name: String,
    root: Option[DirectoryIdentity],
    baseroot: Option[DirectoryIdentity],
    files: Vector[FileEvidence],
    config: CozyProjectYamlConfig.Config
  ) {
    def baseRoot: Option[DirectoryIdentity] = baseroot
  }

  final case class Value(value: String, layer: String, sourcepath: Path) {
    def sourcePath: Path = sourcepath
  }

  final case class PublicationProfile(
    id: String,
    root: String,
    sitekind: String,
    layer: String,
    sourcepath: Path,
    baseroot: Path,
    resolvedroot: Path,
    resolvedidentity: Option[DirectoryIdentity]
  ) {
    def siteKind: String = sitekind
    def sourcePath: Path = sourcepath
    def baseRoot: Path = baseroot
    def resolvedRoot: Path = resolvedroot
  }

  final case class Project(
    root: DirectoryIdentity,
    marker: FileEvidence,
    id: Option[Value],
    kind: Option[Value]
  )

  final case class Context(
    packageroot: DirectoryIdentity,
    packagelexical: Path,
    userhome: Option[DirectoryIdentity],
    project: Option[Project],
    layers: Vector[Layer],
    config: CozyProjectYamlConfig.Config,
    values: Map[String, Value],
    lists: Map[String, Value],
    profiles: Map[String, PublicationProfile]
  ) {
    def packageRoot: DirectoryIdentity = packageroot
    def packageLexicalPath: Path = packagelexical
    def userHome: Option[DirectoryIdentity] = userhome
    def value(path: String): Option[String] = config.value(path)
    def list(path: String): Vector[String] = config.list(path)
    def provenance(path: String): Option[Value] = values.get(path).orElse(lists.get(path))
    def valueProvenance(path: String): Option[Value] = values.get(path)
    def listProvenance(path: String): Option[Value] = lists.get(path)
    def publicationProfile(id: String): Option[PublicationProfile] = profiles.get(id)
    def publicationProfiles: Vector[PublicationProfile] = profiles.toVector.sortBy(_._1).map(_._2)
  }

  private final case class LayerResult(
    layer: Layer,
    profiles: Map[String, ProfileDraft],
    valuesources: Map[String, FileEvidence],
    listsources: Map[String, FileEvidence]
  )
  private final case class ProfileDraft(
    id: String,
    root: String,
    sitekind: String,
    source: FileEvidence
  )
  private final case class Discovery(root: DirectoryIdentity, marker: FileSnapshot)
  private final case class FileSnapshot(evidence: FileEvidence, bytes: Array[Byte])

  private val _config_file_names = Vector(
    "config.yaml", "config.yml", "config.json", "config.conf", "config.hocon", "config.xml"
  )
  private val _profile_id = "[A-Za-z0-9][A-Za-z0-9_-]*".r
  private val _user_home_monitor = new AnyRef

  private[cozy] def withUserHomeLock[A](body: => A): A =
    _user_home_monitor.synchronized(body)

  def resolve(packageRoot: Path): Context = withUserHomeLock {
    val packagepath = _normalized_path(packageRoot, "package root")
    val packageidentity = _direct_directory(packagepath, "package root")
    val discovery = _discover_project(packageidentity.path)
    val userhome = _user_home()
    val roots = Vector.newBuilder[(String, Option[DirectoryIdentity], Option[DirectoryIdentity])]
    roots += (("built-in", None, None))
    roots += (("user", userhome.flatMap(x => _optional_layer_root(x.path.resolve(".cozy"), "user layer")), userhome))
    discovery.foreach { value =>
      roots += (("project-conf", _optional_layer_root(value.root.path.resolve("conf").resolve("cozy"), "project-conf layer"), Some(value.root)))
      roots += (("project-local", _optional_layer_root(value.root.path.resolve(".cozy"), "project-local layer"), Some(value.root)))
    }
    if (!discovery.exists(x => x.root.path == packageidentity.path && x.root.identity == packageidentity.identity)) {
      roots += (("package-conf", _optional_layer_root(packageidentity.path.resolve("conf").resolve("cozy"), "package-conf layer"), Some(packageidentity)))
      roots += (("package-local", _optional_layer_root(packageidentity.path.resolve(".cozy"), "package-local layer"), Some(packageidentity)))
    }
    val layerresults = roots.result().map { case (name, root, base) =>
      val captured = if (name == "project-conf")
        discovery.map(x => Map(x.marker.evidence.path -> x.marker)).getOrElse(Map.empty[Path, FileSnapshot])
      else
        Map.empty[Path, FileSnapshot]
      _load_layer(name, root, base, captured)
    }
    val layers = layerresults.map(_.layer)
    val config = layers.foldLeft(CozyProjectYamlConfig.Config.empty) { (z, x) => z.merge(x.config) }
    val values = _selected_scalar_values(layerresults)
    val lists = _selected_list_values(layerresults)
    val project = discovery.map { value =>
      Project(value.root, value.marker.evidence, _selected_exact(layerresults.filter(x => x.layer.name == "project-conf" || x.layer.name == "project-local"), "project.id"), _selected_exact(layerresults.filter(x => x.layer.name == "project-conf" || x.layer.name == "project-local"), "project.kind"))
    }
    val profiles = layerresults.foldLeft(Map.empty[String, PublicationProfile]) { (z, x) =>
      x.profiles.foldLeft(z) { case (zz, (id, draft)) =>
        zz.updated(id, _publication_profile(draft, x.layer))
      }
    }
    Context(packageidentity, packagepath, userhome, project, layers, config, values, lists, profiles)
  }

  def revalidate(context: Context): Context = withUserHomeLock {
    if (context == null)
      _invalid("Cozy project context must be defined")
    val recomputed = resolve(context.packagelexical)
    if (recomputed != context)
      _invalid("Cozy project context security/provenance snapshot has changed")
    recomputed
  }

  private def _discover_project(packagepath: Path): Option[Discovery] = {
    var current = packagepath
    while (current != null) {
      _direct_directory(current, s"project discovery ancestor: $current")
      _marker(current) match {
        case Some(marker) => return Some(Discovery(_direct_directory(current, "project root"), marker))
        case None => current = current.getParent
      }
    }
    None
  }

  private def _marker(root: Path): Option[FileSnapshot] = {
    val conf = root.resolve("conf")
    if (!_exists(conf))
      None
    else {
      _direct_directory(conf, s"project marker conf: $conf")
      val cozy = conf.resolve("cozy")
      if (!_exists(cozy))
        None
      else {
        _direct_directory(cozy, s"project marker cozy: $cozy")
        val marker = cozy.resolve("config.yaml")
        if (!_exists(marker))
          None
        else
          Some(_file_snapshot(marker, s"project marker: $marker"))
      }
    }
  }

  private def _user_home(): Option[DirectoryIdentity] =
    Option(System.getProperty("user.home")).map(_.trim).filter(_.nonEmpty).map { value =>
      _direct_directory(_normalized_path(_path(value, "user home"), "user home"), "user home")
    }

  private def _optional_layer_root(path: Path, label: String): Option[DirectoryIdentity] = {
    val lexical = _normalized_path(path, label)
    if (_exists(lexical)) Some(_direct_directory(lexical, label)) else None
  }

  private def _load_layer(
    name: String,
    root: Option[DirectoryIdentity],
    base: Option[DirectoryIdentity],
    captured: Map[Path, FileSnapshot]
  ): LayerResult = {
    val candidates = root.toVector.flatMap { value =>
      _config_file_names.flatMap { filename =>
        val candidate = value.path.resolve(filename)
        captured.get(candidate).orElse {
          if (_exists(candidate)) Some(_file_snapshot(candidate, s"$name configuration: $candidate")) else None
        }
      }
    }
    val files = candidates.map(_.evidence)
    val parsed = candidates.map(x => x -> CozyProjectYamlConfig.parsePublic(x.bytes, x.evidence.path.toUri))
    val config = parsed.foldLeft(CozyProjectYamlConfig.Config.empty) { case (z, (_, x)) =>
      z.merge(x)
    }
    val profiles = parsed.foldLeft(Map.empty[String, ProfileDraft]) { case (z, (snapshot, parsedconfig)) =>
      _profile_drafts(name, snapshot.evidence, parsedconfig).foldLeft(z) { case (zz, draft) =>
        if (zz.contains(draft.id))
          _invalid(s"Duplicate publication profile '${draft.id}' in layer $name: ${draft.source.path}")
        zz.updated(draft.id, draft)
      }
    }
    val valuesources = _source_map(parsed, _.values.keySet)
    val listsources = _source_map(parsed, _.lists.keySet)
    LayerResult(Layer(name, root, base, files, config), profiles, valuesources, listsources)
  }

  private def _profile_drafts(
    layer: String,
    source: FileEvidence,
    config: CozyProjectYamlConfig.Config
  ): Vector[ProfileDraft] = {
    _profile_objects(layer, source, config).sortBy(_._1).map { case (id, profile) =>
      if (_profile_id.findFirstIn(id).forall(_ != id))
        _invalid(s"Unsafe publication profile id '$id' in layer $layer: ${source.path}")
      val fields = profile.asObject.getOrElse(
        _invalid(s"Publication profile '$id' must be an object in layer $layer: ${source.path}")
      ).toMap
      fields.keys.filter(x => x != "root" && x != "site-kind").toVector.sorted.headOption.foreach { field =>
        _invalid(s"Unknown publication profile field '$field' for '$id' in layer $layer: ${source.path}")
      }
      val root = _exact_json_string(fields.get("root"), s"Publication profile '$id' root in layer $layer", source.path)
      val sitekind = _exact_json_string(fields.get("site-kind"), s"Publication profile '$id' site-kind in layer $layer", source.path)
      ProfileDraft(id, root, sitekind, source)
    }
  }

  private def _profile_objects(
    layer: String,
    source: FileEvidence,
    config: CozyProjectYamlConfig.Config
  ): Vector[(String, CJson)] =
    config.json.flatMap(_.hcursor.downField("media").focus).map { media =>
      val fields = media.asObject.getOrElse(
        _invalid(s"Publication media field must be an object in layer $layer: ${source.path}")
      )
      fields("publication-profiles").map { profiles =>
        profiles.asObject.map(_.toMap.toVector).getOrElse(
          _invalid(s"Publication media.publication-profiles field must be an object in layer $layer: ${source.path}")
        )
      }.getOrElse(Vector.empty)
    }.getOrElse(Vector.empty)

  private def _publication_profile(draft: ProfileDraft, layer: Layer): PublicationProfile = {
    val base = layer.baseroot.getOrElse(_invalid(s"Publication profile '${draft.id}' has no base root in layer ${layer.name}"))
    val relative = _relative_root(draft.root, draft.id, layer.name, draft.source.path)
    val resolved = base.path.resolve(relative).normalize()
    if (!resolved.startsWith(base.path))
      _invalid(s"Publication profile '${draft.id}' root escapes base root in layer ${layer.name}: ${draft.source.path}")
    val identity = if (_exists(resolved)) {
      val value = _direct_directory(resolved, s"Publication profile '${draft.id}' root in layer ${layer.name}")
      if (!value.identity.startsWith(base.identity))
        _invalid(s"Publication profile '${draft.id}' root real identity escapes base root in layer ${layer.name}: ${draft.source.path}")
      Some(value)
    } else None
    PublicationProfile(draft.id, draft.root, draft.sitekind, layer.name, draft.source.path, base.path, resolved, identity)
  }

  private def _selected_scalar_values(layerresults: Vector[LayerResult]): Map[String, Value] =
    layerresults.foldLeft(Map.empty[String, Value]) { (z, result) =>
      result.valuesources.foldLeft(z) { case (zz, (key, source)) =>
        zz.updated(key, Value(result.layer.config.values(key), result.layer.name, source.path))
      }
    }

  private def _selected_list_values(layerresults: Vector[LayerResult]): Map[String, Value] =
    layerresults.foldLeft(Map.empty[String, Value]) { (z, result) =>
      result.listsources.foldLeft(z) { case (zz, (key, source)) =>
        zz.updated(key, Value(result.layer.config.lists(key).mkString("\u0000"), result.layer.name, source.path))
      }
    }

  private def _selected_exact(layerresults: Vector[LayerResult], key: String): Option[Value] =
    _selected_scalar_values(layerresults).get(key).filter(x => x.value.nonEmpty && x.value == x.value.trim)

  private def _source_map(
    parsed: Vector[(FileSnapshot, CozyProjectYamlConfig.Config)],
    f: CozyProjectYamlConfig.Config => Set[String]
  ): Map[String, FileEvidence] =
    parsed.foldLeft(Map.empty[String, FileEvidence]) { case (z, (snapshot, config)) =>
      f(config).foldLeft(z) { (zz, key) =>
        zz.updated(key, snapshot.evidence)
      }
    }

  private def _exact_json_string(value: Option[CJson], label: String, path: Path): String =
    value.flatMap(_.asString).filter(x => x.nonEmpty && x == x.trim).getOrElse(
      _invalid(s"$label must be an exact non-empty string: $path")
    )

  private def _relative_root(value: String, id: String, layer: String, source: Path): Path = {
    val path = _path(value, s"Publication profile '$id' root in layer $layer")
    if (path.isAbsolute)
      _invalid(s"Publication profile '$id' root must be relative in layer $layer: $source")
    val normalized = path.normalize()
    if ((normalized.toString.isEmpty && value != ".") ||
      (normalized.getNameCount > 0 && normalized.getName(0).toString == ".."))
      _invalid(s"Publication profile '$id' root escapes its base in layer $layer: $source")
    normalized
  }

  private[config] var _file_snapshot_between_reads: () => Unit = () => ()

  private def _file_snapshot(path: Path, label: String): FileSnapshot = {
    val lexical = _normalized_path(path, label)
    val before = _regular_attributes(lexical, label)
    val identity = _real_path(lexical, label)
    if (identity != lexical)
      _invalid(s"Cozy project context $label must not use a lexical or symlink alias: $lexical")
    val firstbytes = _read_file_bytes(lexical, label)
    _file_snapshot_between_reads()
    val middle = _regular_attributes(lexical, label)
    val secondbytes = _read_file_bytes(lexical, label)
    val after = _regular_attributes(lexical, label)
    if (!_same_file_attributes(before, middle) || !_same_file_attributes(before, after) ||
      firstbytes.length.toLong != before.size() || secondbytes.length.toLong != before.size() ||
      !firstbytes.sameElements(secondbytes))
      _invalid(s"Cozy project context $label changed while being read: $lexical")
    FileSnapshot(FileEvidence(lexical, identity, _sha256(firstbytes), firstbytes.length.toLong, before.fileKey()), firstbytes)
  }

  private def _read_file_bytes(path: Path, label: String): Array[Byte] =
    try Files.readAllBytes(path) catch {
      case NonFatal(e) => _invalid(s"Cozy project context $label cannot be read: ${e.getMessage}")
    }

  private def _same_file_attributes(before: BasicFileAttributes, after: BasicFileAttributes): Boolean =
    before.fileKey() == after.fileKey() &&
      before.size() == after.size() &&
      before.lastModifiedTime() == after.lastModifiedTime()

  private def _direct_directory(path: Path, label: String): DirectoryIdentity = {
    val lexical = _normalized_path(path, label)
    if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Cozy project context $label must be a direct non-symlink directory: $lexical")
    val attributes = _directory_attributes(lexical, label)
    val identity = _real_path(lexical, label)
    if (identity != lexical)
      _invalid(s"Cozy project context $label must not use a lexical or symlink alias: $lexical")
    DirectoryIdentity(lexical, identity, attributes.fileKey())
  }

  private def _regular_attributes(path: Path, label: String): BasicFileAttributes = {
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Cozy project context $label must be a direct regular non-symlink file: $path")
    _attributes(path, label)
  }

  private def _directory_attributes(path: Path, label: String): BasicFileAttributes = {
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Cozy project context $label must be a direct non-symlink directory: $path")
    _attributes(path, label)
  }

  private def _attributes(path: Path, label: String): BasicFileAttributes = {
    val attributes = try Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS) catch {
      case NonFatal(e) => _invalid(s"Cozy project context $label attributes cannot be read: ${e.getMessage}")
    }
    if (attributes.fileKey() == null)
      _invalid(s"Cozy project context $label has no stable file identity: $path")
    attributes
  }

  private def _real_path(path: Path, label: String): Path =
    try path.toRealPath() catch {
      case NonFatal(e) => _invalid(s"Cozy project context $label cannot be resolved: ${e.getMessage}")
    }

  private def _normalized_path(path: Path, label: String): Path = {
    if (path == null)
      _invalid(s"Cozy project context $label path must be defined")
    path.toAbsolutePath.normalize()
  }

  private def _path(value: String, label: String): Path =
    try Path.of(value) catch {
      case NonFatal(_) => _invalid(s"Cozy project context $label is not a valid path")
    }

  private def _exists(path: Path): Boolean = Files.exists(path, LinkOption.NOFOLLOW_LINKS)

  private def _sha256(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(bytes)
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
