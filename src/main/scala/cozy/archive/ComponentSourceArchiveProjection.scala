package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardOpenOption}
import java.security.MessageDigest

import scala.collection.JavaConverters._

/*
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object ComponentSourceArchiveProjection {
  val Schema = "cozy.component-source-archive.v1"

  private val _excluded_names = Set(
    ".env",
    ".git",
    "cache",
    "classes",
    "download",
    "host-local",
    "jars",
    "local.conf",
    "log",
    "secrets",
    "target",
    "temp",
    "tmp"
  )

  final case class Archive(entries: Vector[Entry], archiveDigest: String)
  final case class Entry(path: String, sha256: String)

  def project(projectRoot: Path): Archive = {
    val root = _normalized_root(projectRoot)
    val rootreal = root.toRealPath()
    val entries = _candidate_paths(root)
      .flatMap(path => _entry(root, rootreal, path))
      .sortBy(_.path)
      .toVector
    Archive(entries, _archive_digest(entries))
  }

  def write(projectRoot: Path, output: Path): Path = {
    val root = _normalized_root(projectRoot)
    val destination = _safe_output(root, output)
    val parent = Option(destination.getParent).getOrElse(
      throw new IllegalArgumentException(s"Component source archive output has no parent: $destination")
    )
    Files.createDirectories(parent)
    _require_within_project(root, parent)
    if (Files.isSymbolicLink(destination))
      throw new IllegalArgumentException(s"Component source archive output must not be a symbolic link: $destination")
    Files.write(
      destination,
      render(project(root)).getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )
    destination
  }

  def render(archive: Archive): String = {
    val entries = archive.entries.map { entry =>
      s"""{"path":${_json(entry.path)},"sha256":${_json(entry.sha256)}}"""
    }.mkString("[", ",", "]")
    s"""{"schema":${_json(Schema)},"archiveDigest":${_json(archive.archiveDigest)},"entries":$entries}"""
  }

  private def _normalized_root(projectroot: Path): Path = {
    if (projectroot == null)
      throw new IllegalArgumentException("Component source archive project root is required")
    val root = projectroot.toAbsolutePath.normalize()
    if (!Files.isDirectory(root))
      throw new IllegalArgumentException(s"Component source archive project root is not a directory: $root")
    root
  }

  private def _candidate_paths(root: Path): Vector[Path] = {
    val selectedroots = Vector("src")
      .map(root.resolve)
      .filter(path => !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
    val namedfiles = Vector("project.yaml", "build.sbt", "README.md")
      .map(root.resolve)
      .filter(path => !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
    (selectedroots ++ namedfiles).flatMap { path =>
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        val stream = Files.walk(path)
        try stream.iterator().asScala.toVector
        finally stream.close()
      } else Vector(path)
    }
  }

  private def _entry(root: Path, rootreal: Path, path: Path): Option[Entry] = {
    val normalized = path.toAbsolutePath.normalize()
    val relative = _relative_path(root, normalized)
    if (
      Files.isSymbolicLink(normalized) ||
        relative.isEmpty ||
        !_safe_path(relative) ||
        _excluded(relative) ||
        !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
    )
      None
    else {
      val real = try normalized.toRealPath()
      catch { case _: java.io.IOException => return None }
      if (!real.startsWith(rootreal)) None
      else Some(Entry(relative, _sha256(Files.readAllBytes(real))))
    }
  }

  private def _safe_output(root: Path, output: Path): Path = {
    if (output == null)
      throw new IllegalArgumentException("Component source archive output is required")
    val destination = output.toAbsolutePath.normalize()
    val targetroot = root.resolve("target").toAbsolutePath.normalize()
    if (destination == targetroot || !destination.startsWith(targetroot))
      throw new IllegalArgumentException(
        s"Component source archive output must be inside the project target directory: $destination"
      )
    _existing_ancestor(destination).foreach(_require_within_project(root, _))
    destination
  }

  private def _existing_ancestor(path: Path): Option[Path] = {
    @annotation.tailrec
    def go(candidate: Path): Option[Path] =
      if (candidate == null) None
      else if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) Some(candidate)
      else go(candidate.getParent)
    go(path)
  }

  private def _require_within_project(root: Path, path: Path): Unit = {
    val rootreal = root.toRealPath()
    val real = path.toRealPath()
    if (!real.startsWith(rootreal))
      throw new IllegalArgumentException(
        s"Component source archive output resolves outside the project root: $path"
      )
  }

  private def _relative_path(root: Path, path: Path): String =
    if (path.startsWith(root)) root.relativize(path).toString.replace('\\', '/') else ""

  private def _safe_path(path: String): Boolean = {
    val segments = path.split("/", -1).toVector
    path.nonEmpty &&
      !path.startsWith("/") &&
      !segments.contains("") &&
      !segments.contains(".") &&
      !segments.contains("..")
  }

  private def _excluded(path: String): Boolean = {
    val segments = path.split("/", -1).toVector
    segments.exists(_excluded_names.contains) ||
      segments.exists { segment =>
        val name = segment.toLowerCase(java.util.Locale.ROOT)
        name == ".env" ||
          name.startsWith(".env.") ||
          name.endsWith(".class") ||
          name.endsWith(".jar") ||
          name.endsWith(".log") ||
          name.endsWith(".temp") ||
          name.endsWith(".tmp")
      }
  }

  private def _archive_digest(entries: Vector[Entry]): String =
    _sha256(entries.map(entry => s"${entry.path}\t${entry.sha256}\n").mkString.getBytes(StandardCharsets.UTF_8))

  private def _json(s: String): String = {
    val builder = new StringBuilder("\"")
    s.foreach {
      case '\\' => builder.append("\\\\")
      case '"' => builder.append("\\\"")
      case '\b' => builder.append("\\b")
      case '\f' => builder.append("\\f")
      case '\n' => builder.append("\\n")
      case '\r' => builder.append("\\r")
      case '\t' => builder.append("\\t")
      case c if c < ' ' => builder.append(f"\\u${c.toInt}%04x")
      case c => builder.append(c)
    }
    builder.append('"').result()
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(bytes)
      .map(byte => f"${byte & 0xff}%02x")
      .mkString
}
