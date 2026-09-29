package cozy.video

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import org.goldenport.RAISE
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[video] object CozyVideoStoryboardArtifacts {
  final case class Artifact(source: Path, destination: Path)

  private final case class Backup(destination: Path, backup: Option[Path], existed: Boolean)

  def install(stagingRoot: Path, artifacts: Vector[Artifact], protectedPaths: Set[Path]): Unit =
    installWithMove(stagingRoot, artifacts, protectedPaths, _atomic_move)

  private[video] def installWithMove(
    stagingRoot: Path,
    artifacts: Vector[Artifact],
    protectedPaths: Set[Path],
    move: (Path, Path) => Unit
  ): Unit = {
    val stagingroot = _direct_directory(stagingRoot, "Storyboard staging root")
    val normalized = artifacts.map { artifact =>
      Artifact(artifact.source.toAbsolutePath.normalize(), artifact.destination.toAbsolutePath.normalize())
    }
    if (normalized.isEmpty)
      RAISE.invalidArgumentFault("Storyboard artifact installation requires at least one prepared artifact.")
    if (normalized.map(_.destination).distinct.size != normalized.size)
      RAISE.invalidArgumentFault("Storyboard artifact installation has duplicate generated destinations.")
    val protectedpaths = protectedPaths.map(_.toAbsolutePath.normalize())
    normalized.foreach { artifact =>
      _validate_source(stagingroot, artifact.source)
      _validate_destination(artifact.destination)
      if (_protected_destination(artifact.destination, protectedpaths))
        RAISE.invalidArgumentFault(s"Storyboard artifact destination aliases a declared input: ${artifact.destination}")
    }
    val backuproot = stagingroot.resolve("backups").normalize()
    _create_direct_directory(backuproot, stagingroot, "Storyboard backup root")
    val backups = normalized.zipWithIndex.map { case (artifact, index) =>
      if (Files.exists(artifact.destination, LinkOption.NOFOLLOW_LINKS)) {
        _require_direct_regular_file(artifact.destination, s"Storyboard existing destination ${artifact.destination}")
        val backup = backuproot.resolve(f"$index%04d.backup").normalize()
        try Files.copy(artifact.destination, backup, StandardCopyOption.COPY_ATTRIBUTES)
        catch {
          case NonFatal(error) =>
            RAISE.invalidArgumentFault(s"Cannot snapshot Storyboard destination ${artifact.destination}: ${error.getMessage}")
        }
        Backup(artifact.destination, Some(backup), existed = true)
      } else {
        Backup(artifact.destination, None, existed = false)
      }
    }
    val completed = Vector.newBuilder[Backup]
    try {
      normalized.zip(backups).foreach { case (artifact, backup) =>
        move(artifact.source, artifact.destination)
        completed += backup
      }
    } catch {
      case NonFatal(error) =>
        val rollbackfailures = completed.result().reverse.flatMap(_rollback(_, move))
        if (rollbackfailures.nonEmpty)
          _raise_install_failure(
            s"Storyboard artifact installation failed and rollback requires recovery at $stagingroot",
            error,
            rollbackfailures
          )
        _raise_install_failure(s"Storyboard artifact installation failed and was rolled back", error, Vector.empty)
    }
  }

  private def _atomic_move(source: Path, destination: Path): Unit =
    try Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    catch {
      case error: AtomicMoveNotSupportedException =>
        RAISE.invalidArgumentFault(s"Storyboard artifact installation requires atomic replacement: ${error.getMessage}")
      case NonFatal(error) => throw error
    }

  private def _rollback(backup: Backup, move: (Path, Path) => Unit): Option[Throwable] =
    try {
      backup.backup match {
        case Some(path) => move(path, backup.destination)
        case None => Files.deleteIfExists(backup.destination)
      }
      None
    } catch {
      case NonFatal(error) => Some(new IOException(s"cannot restore ${backup.destination}: ${error.getMessage}", error))
    }

  private def _raise_install_failure(label: String, original: Throwable, rollbackfailures: Vector[Throwable]): Nothing = {
    val rollback = rollbackfailures.map(error => s"${error.getClass.getName}: ${error.getMessage}").mkString("; ")
    val message = s"$label: original=${original.getClass.getName}: ${original.getMessage}" +
      (if (rollbackfailures.nonEmpty) s"; rollback=$rollback" else "")
    val failure = new IllegalArgumentException(message, original)
    rollbackfailures.foreach(failure.addSuppressed)
    throw failure
  }

  private def _validate_source(stagingroot: Path, source: Path): Unit = {
    if (!source.startsWith(stagingroot))
      RAISE.invalidArgumentFault(s"Storyboard prepared artifact escapes its staging root: $source")
    _require_direct_descendant(stagingroot, source.getParent, s"Storyboard prepared artifact ancestor for $source")
    _require_direct_regular_file(source, s"Storyboard prepared artifact $source")
    if (Files.size(source) <= 0)
      RAISE.invalidArgumentFault(s"Storyboard prepared artifact must be nonempty: $source")
  }

  private def _validate_destination(destination: Path): Unit = {
    val parent = Option(destination.getParent).getOrElse(
      RAISE.invalidArgumentFault(s"Storyboard artifact destination has no parent: $destination")
    )
    var current = parent.getRoot
    if (current == null)
      RAISE.invalidArgumentFault(s"Storyboard artifact destination is not absolute: $destination")
    parent.iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        RAISE.invalidArgumentFault(s"Storyboard artifact destination has an unsafe ancestor: $current")
    }
    _require_direct_directory(parent, s"Storyboard artifact destination parent $parent")
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
      _require_direct_regular_file(destination, s"Storyboard artifact destination $destination")
  }

  private def _direct_directory(path: Path, label: String): Path = {
    val value = path.toAbsolutePath.normalize()
    _require_direct_absolute_ancestors(value, label)
    value
  }

  private def _protected_destination(destination: Path, protectedpaths: Set[Path]): Boolean =
    protectedpaths.exists { protectedpath =>
      destination == protectedpath ||
        (Files.isDirectory(protectedpath, LinkOption.NOFOLLOW_LINKS) && destination.startsWith(protectedpath))
    }

  private def _require_direct_absolute_ancestors(path: Path, label: String): Unit = {
    val value = path.toAbsolutePath.normalize()
    var current = value.getRoot
    if (current == null)
      RAISE.invalidArgumentFault(s"$label must be an absolute directory: $value")
    _require_direct_directory(current, label)
    value.iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      _require_direct_directory(current, label)
    }
  }

  private def _create_direct_directory(path: Path, boundary: Path, label: String): Unit = {
    val value = path.toAbsolutePath.normalize()
    if (!value.startsWith(boundary))
      RAISE.invalidArgumentFault(s"$label escapes its staging root: $value")
    var current = boundary
    boundary.relativize(value).iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS))
        _direct_directory(current, label)
      else
        try Files.createDirectory(current)
        catch {
          case NonFatal(error) => RAISE.invalidArgumentFault(s"Cannot create $label: ${error.getMessage}")
        }
    }
  }

  private def _require_direct_descendant(boundary: Path, directory: Path, label: String): Unit = {
    val value = Option(directory).map(_.toAbsolutePath.normalize()).getOrElse(
      RAISE.invalidArgumentFault(s"$label has no parent directory")
    )
    if (!value.startsWith(boundary))
      RAISE.invalidArgumentFault(s"$label escapes its required boundary: $value")
    var current = boundary
    boundary.relativize(value).iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      _require_direct_directory(current, label)
    }
  }

  private def _require_direct_directory(path: Path, label: String): Unit =
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"$label must be an existing direct non-symlink directory: $path")

  private def _require_direct_regular_file(path: Path, label: String): Unit =
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"$label must be a direct regular non-symlink file: $path")
}
