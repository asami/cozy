package cozy.document

import java.nio.file.{Files, LinkOption, Path}
import java.util.UUID
import scala.util.control.NonFatal

/** Physical admission and allocation for an external Document Project run. */
private[cozy] object CozyDocumentProjectWorkRoot {
  final case class Run(root: Path, temporary: Path, cache: Path)

  def hostDefault(): Path = {
    val home = Option(System.getProperty("user.home")).filter(_.nonEmpty).map(value => java.nio.file.Paths.get(value))
      .getOrElse(_failure("user home is unavailable for the default work root"))
    hostDefault(home)
  }

  private[cozy] def hostDefault(home: Path): Path = {
    val directory = home.resolve(".cozy")
    val files = Vector("config.yaml", "config.yml", "config.json", "config.conf", "config.hocon", "config.xml")
      .map(name => directory.resolve(name)).filter(path => Files.exists(path, LinkOption.NOFOLLOW_LINKS))
    val values = files.flatMap { path =>
      if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        _failure(s"host configuration is not a direct regular file: $path")
      val config = try cozy.config.CozyProjectYamlConfig.load(path) catch {
        case NonFatal(error) => _failure(s"host configuration cannot be read: ${Option(error.getMessage).getOrElse(path.toString)}")
      }
      config.values.get("documentProject.workRoot").map(value => path -> value)
    }
    if (values.size > 1) _failure("documentProject.workRoot is duplicated in host configuration")
    values.headOption.map { case (_, value) =>
      if (value.isEmpty || value != value.trim) _failure("documentProject.workRoot must be a nonempty exact path")
      val path = try java.nio.file.Paths.get(value) catch { case NonFatal(_) => _failure("documentProject.workRoot is invalid") }
      if (!path.isAbsolute) _failure("documentProject.workRoot must be absolute")
      path
    }.getOrElse(home.resolve(".cozy/work"))
  }

  def allocate(workRoot: Path, project: Path, packageRoot: Option[Path], synchronizationRoots: Vector[Path]): Run = {
    val work = _physical(workRoot)
    val protectedroots = Vector(project) ++ packageRoot.toVector ++ synchronizationRoots
    protectedroots.foreach { value =>
      val protectedroot = _physical(value)
      if (_overlaps(work, protectedroot))
        _failure(s"work root overlaps protected root: $protectedroot")
    }
    val existing = _existing_ancestor(work)
    if (!Files.isDirectory(existing) || !Files.isWritable(existing))
      _failure(s"work root is not writable: $work")
    if (Files.exists(work, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(work))
      _failure(s"work root is not a directory: $work")
    val runs = work.resolve("target/document-project/runs")
    var current = work
    val segments = work.relativize(runs).iterator()
    while (segments.hasNext) {
      current = current.resolve(segments.next())
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
          (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        _failure(s"work subtree must contain only direct directories: $current")
    }
    try {
      Files.createDirectories(runs)
      val run = Files.createDirectory(runs.resolve(UUID.randomUUID().toString))
      val temporary = Files.createDirectory(run.resolve("tmp"))
      val cache = Files.createDirectory(run.resolve("cache"))
      Run(run, temporary, cache)
    } catch {
      case NonFatal(error) => _failure(s"work root cannot allocate a run: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }
  }

  private def _physical(path: Path): Path = {
    val absolute = path.toAbsolutePath.normalize()
    val ancestor = _existing_ancestor(absolute)
    try ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize()
    catch { case NonFatal(error) => _failure(s"cannot resolve path $path: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}") }
  }

  private def _existing_ancestor(path: Path): Path = {
    Iterator.iterate(Option(path))(_.flatMap(value => Option(value.getParent)))
      .takeWhile(_.nonEmpty).flatten
      .find(value => Files.exists(value, LinkOption.NOFOLLOW_LINKS))
      .getOrElse(_failure(s"path has no existing ancestor: $path"))
  }

  private def _overlaps(left: Path, right: Path): Boolean =
    left == right || left.startsWith(right) || right.startsWith(left)

  private def _failure(message: String): Nothing =
    CozyDocumentProject._failure("DP-PATH-001", message)
}
