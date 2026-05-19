package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RepositoryArtifactCatalog(
  schemaVersion: String,
  kind: String,
  artifactId: String,
  recommended: Option[String],
  latestStable: Option[String],
  latestSnapshot: Option[String],
  status: Option[String],
  aliases: Vector[String],
  versions: Vector[RepositoryArtifactCatalogVersion]
) {
  def validate: RepositoryArtifactCatalog = {
    RepositoryArtifactCatalog.validate(this, None)
    this
  }

  def validateSourcePath(path: Path): RepositoryArtifactCatalog = {
    RepositoryArtifactCatalog.validate(this, Some(path))
    this
  }

  def toYaml: String =
    RepositoryArtifactCatalog.toYaml(this)
}

final case class RepositoryArtifactCatalogVersion(
  version: String,
  channel: Option[String],
  status: Option[String],
  component: Option[String],
  publishedAt: Option[String],
  file: Option[String],
  runtime: Option[RepositoryArtifactRuntimeRequirement],
  checksumSha256: Option[String]
) {
  def effectiveStatus: String = status.getOrElse("active")
}

final case class RepositoryArtifactRuntimeRequirement(
  minimum: Option[String],
  maximum: Option[String],
  excluded: Vector[String],
  tested: Vector[String]
)

object RepositoryArtifactCatalog {
  private val _valid_kinds = Set("car", "sar")
  private val _valid_statuses = Set("active", "deprecated", "disabled")
  private val _valid_channels = Set("stable", "snapshot")

  def load(path: Path): RepositoryArtifactCatalog =
    parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).validateSourcePath(path)

  def parse(text: String): RepositoryArtifactCatalog = {
    val parsed = YamlParser.parse(text)
    val root = parsed.values
    val versions = parsed.versions.map(_version)
    RepositoryArtifactCatalog(
      schemaVersion = root.getOrElse("schemaVersion", "1"),
      kind = root.getOrElse("kind", ""),
      artifactId = root.getOrElse("artifactId", ""),
      recommended = _non_empty(root.get("recommended")),
      latestStable = _non_empty(root.get("latestStable")),
      latestSnapshot = _non_empty(root.get("latestSnapshot")),
      status = _non_empty(root.get("status")),
      aliases = parsed.lists.get("aliases").getOrElse(_csv(root.get("aliases"))),
      versions = versions
    ).validate
  }

  def validate(catalog: RepositoryArtifactCatalog, sourcepath: Option[Path]): Unit = {
    _require(catalog.schemaVersion == "1", s"Unsupported repository artifact catalog schemaVersion: ${catalog.schemaVersion}")
    _require(_valid_kinds.contains(catalog.kind), s"Invalid repository artifact catalog kind: ${catalog.kind}")
    _require(catalog.artifactId.nonEmpty, "Repository artifact catalog requires artifactId")
    catalog.status.foreach(status => _require(_valid_statuses.contains(status), s"Invalid repository artifact catalog status: $status"))
    sourcepath.foreach(path => _validate_source_path(catalog, path))
    _validate_versions(catalog)
    _validate_selector(catalog, "recommended", catalog.recommended, None)
    _validate_selector(catalog, "latestStable", catalog.latestStable, Some("stable"))
    _validate_selector(catalog, "latestSnapshot", catalog.latestSnapshot, Some("snapshot"))
  }

  def toYaml(catalog: RepositoryArtifactCatalog): String = {
    catalog.validate
    val lines =
      Vector(
        s"schemaVersion: ${catalog.schemaVersion}",
        s"kind: ${catalog.kind}",
        s"artifactId: ${catalog.artifactId}"
      ) ++
        _optional_line("recommended", catalog.recommended) ++
        _optional_line("latestStable", catalog.latestStable) ++
        _optional_line("latestSnapshot", catalog.latestSnapshot) ++
        _optional_line("status", catalog.status) ++
        _list_lines("aliases", catalog.aliases, 0) ++
        Vector("versions:") ++
        catalog.versions.flatMap(_version_lines)
    lines.mkString("\n") + "\n"
  }

  private def _version(values: Map[String, String]): RepositoryArtifactCatalogVersion =
    RepositoryArtifactCatalogVersion(
      version = values.getOrElse("version", ""),
      channel = _non_empty(values.get("channel")),
      status = _non_empty(values.get("status")),
      component = _non_empty(values.get("component")),
      publishedAt = _non_empty(values.get("publishedAt")),
      file = _non_empty(values.get("file")),
      runtime = _runtime(values),
      checksumSha256 = _non_empty(values.get("checksum.sha256"))
    )

  private def _runtime(values: Map[String, String]): Option[RepositoryArtifactRuntimeRequirement] = {
    val minimum = _non_empty(values.get("runtime.cncf.minimum"))
    val maximum = _non_empty(values.get("runtime.cncf.maximum"))
    val excluded = _csv(values.get("runtime.cncf.excluded"))
    val tested = _csv(values.get("runtime.cncf.tested"))
    if (minimum.isEmpty && maximum.isEmpty && excluded.isEmpty && tested.isEmpty)
      None
    else
      Some(RepositoryArtifactRuntimeRequirement(minimum, maximum, excluded, tested))
  }

  private def _validate_source_path(catalog: RepositoryArtifactCatalog, path: Path): Unit = {
    val filename = path.getFileName.toString
    val stem = filename.stripSuffix(".yaml").stripSuffix(".yml")
    _require(stem == catalog.artifactId, s"Catalog filename does not match artifactId: $filename != ${catalog.artifactId}")
    Option(path.getParent).flatMap(parent => Option(parent.getFileName)).foreach { kind =>
      _require(kind.toString == catalog.kind, s"Catalog path kind does not match catalog kind: $kind != ${catalog.kind}")
    }
  }

  private def _validate_versions(catalog: RepositoryArtifactCatalog): Unit = {
    val duplicates = catalog.versions.groupBy(_.version).collect { case (version, xs) if xs.size > 1 => version }.toVector.sorted
    _require(duplicates.isEmpty, s"Duplicate repository artifact catalog versions: ${duplicates.mkString(", ")}")
    val suffix = "." + catalog.kind
    catalog.versions.foreach { version =>
      _require(version.version.nonEmpty, "Repository artifact catalog version requires version")
      version.channel.foreach(channel => _require(_valid_channels.contains(channel), s"Invalid repository artifact catalog channel: $channel"))
      version.status.foreach(status => _require(_valid_statuses.contains(status), s"Invalid repository artifact catalog version status: $status"))
      _require(version.file.nonEmpty, s"Repository artifact catalog version requires file: ${version.version}")
      version.file.foreach(file => _require(file.endsWith(suffix), s"Repository artifact file must end with $suffix: $file"))
    }
  }

  private def _validate_selector(
    catalog: RepositoryArtifactCatalog,
    name: String,
    selector: Option[String],
    expectedchannel: Option[String]
  ): Unit =
    selector.foreach { version =>
      val target = catalog.versions.find(_.version == version)
      _require(target.nonEmpty, s"Catalog $name points to missing version: $version")
      _require(!target.exists(_.effectiveStatus == "disabled"), s"Catalog $name points to disabled version: $version")
      expectedchannel.foreach { channel =>
        _require(target.flatMap(_.channel).contains(channel), s"Catalog $name must point to $channel version: $version")
      }
    }

  private def _version_lines(version: RepositoryArtifactCatalogVersion): Vector[String] =
    Vector(s"  - version: ${version.version}") ++
      _optional_line("channel", version.channel, 4) ++
      _optional_line("status", version.status, 4) ++
      _optional_line("component", version.component, 4) ++
      _optional_line("publishedAt", version.publishedAt, 4) ++
      _optional_line("file", version.file, 4) ++
      version.runtime.toVector.flatMap(_runtime_lines) ++
      version.checksumSha256.toVector.flatMap(value => Vector("    checksum:", s"      sha256: $value"))

  private def _runtime_lines(runtime: RepositoryArtifactRuntimeRequirement): Vector[String] =
    Vector("    runtime:", "      cncf:") ++
      _optional_line("minimum", runtime.minimum, 8) ++
      _optional_line("maximum", runtime.maximum, 8) ++
      _list_lines("excluded", runtime.excluded, 8) ++
      _list_lines("tested", runtime.tested, 8)

  private def _optional_line(name: String, value: Option[String], indent: Int = 0): Vector[String] =
    value.map(v => " " * indent + s"$name: $v").toVector

  private def _list_lines(name: String, values: Vector[String], indent: Int): Vector[String] = {
    val prefix = " " * indent
    if (values.isEmpty)
      Vector(s"$prefix$name: []")
    else
      s"$prefix$name:" +: values.map(value => s"$prefix  - $value")
  }

  private def _non_empty(value: Option[String]): Option[String] =
    value.map(_.trim).filter(_.nonEmpty)

  private def _csv(value: Option[String]): Vector[String] =
    _non_empty(value).map { v =>
      if (v == "[]")
        Vector.empty[String]
      else
        v.stripPrefix("[").stripSuffix("]").split(",").toVector.map(_unquote).map(_.trim).filter(_.nonEmpty)
    }.getOrElse(Vector.empty)

  private def _unquote(value: String): String = {
    val t = value.trim
    if (t.length >= 2 && ((t.head == '"' && t.last == '"') || (t.head == '\'' && t.last == '\'')))
      t.substring(1, t.length - 1)
    else
      t
  }

  private def _require(condition: Boolean, message: => String): Unit =
    if (!condition)
      throw new IllegalArgumentException(message)

  private object YamlParser {
    final case class Parsed(
      values: Map[String, String],
      lists: Map[String, Vector[String]],
      versions: Vector[Map[String, String]]
    )

    def parse(text: String): Parsed = {
      var values = Map.empty[String, String]
      var lists = Map.empty[String, Vector[String]]
      var versions = Vector.empty[Map[String, String]]
      var versionvalues = Map.empty[String, String]
      var rootstack = Vector.empty[(Int, String)]
      var versionstack = Vector.empty[(Int, String)]
      var inversions = false

      def _finish_version_(): Unit =
        if (versionvalues.nonEmpty) {
          versions :+= versionvalues
          versionvalues = Map.empty
        }

      def _path_(stack: Vector[(Int, String)], key: String): String =
        (stack.map(_._2) :+ key).mkString(".")

      def _drop_stack_(stack: Vector[(Int, String)], indent: Int): Vector[(Int, String)] =
        stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)

      def _append_list_(path: String, value: String): Unit =
        if (path.nonEmpty)
          lists = lists.updated(path, lists.getOrElse(path, Vector.empty) :+ _unquote(value))

      def _append_version_list_(path: String, value: String): Unit =
        if (path.nonEmpty) {
          val current = versionvalues.get(path).map(v => if (v == "[]") "" else v + ",").getOrElse("")
          versionvalues = versionvalues.updated(path, current + _unquote(value))
        }

      text.split("\\r?\\n").toVector.foreach { raw =>
        val line = _strip_comment(raw)
        if (line.trim.nonEmpty) {
          val indent = line.takeWhile(_ == ' ').length
          val trimmed = line.trim
          if (indent == 0)
            inversions = trimmed == "versions:"
          if (inversions && indent == 2 && trimmed.startsWith("- ")) {
            _finish_version_()
            versionstack = Vector.empty
            val rest = trimmed.drop(2).trim
            if (rest.contains(":")) {
              val (key, value) = _split_key_value(rest)
              versionvalues = versionvalues.updated(key, _unquote(value))
            }
          } else if (inversions && indent > 2) {
            if (trimmed.startsWith("- ")) {
              versionstack = _drop_stack_(versionstack, indent)
              _append_version_list_(versionstack.map(_._2).mkString("."), trimmed.drop(2).trim)
            } else if (trimmed.contains(":")) {
              val (key, value) = _split_key_value(trimmed)
              versionstack = _drop_stack_(versionstack, indent)
              if (value.isEmpty)
                versionstack :+= indent -> key
              else
                versionvalues = versionvalues.updated(_path_(versionstack, key), _unquote(value))
            }
          } else if (trimmed.startsWith("- ")) {
            rootstack = _drop_stack_(rootstack, indent)
            _append_list_(rootstack.map(_._2).mkString("."), trimmed.drop(2).trim)
          } else if (trimmed.contains(":")) {
            val (key, value) = _split_key_value(trimmed)
            rootstack = _drop_stack_(rootstack, indent)
            if (value.isEmpty)
              rootstack :+= indent -> key
            else
              values = values.updated(_path_(rootstack, key), _unquote(value))
          }
        }
      }
      _finish_version_()
      Parsed(values, lists, versions)
    }

    private def _strip_comment(value: String): String = {
      val trimmed = value.trim
      if (trimmed.startsWith("#"))
        ""
      else
        value
    }

    private def _split_key_value(value: String): (String, String) = {
      val idx = value.indexOf(':')
      value.substring(0, idx).trim -> value.substring(idx + 1).trim
    }
  }
}
