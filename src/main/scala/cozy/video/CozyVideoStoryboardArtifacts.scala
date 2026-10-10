package cozy.video

import java.nio.file.{Files, LinkOption, Path}
import org.goldenport.RAISE
import scala.collection.JavaConverters._

/*
 * @since   Sep. 29, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[video] object CozyVideoStoryboardArtifacts {
  private[video] def preflightProduction(projectRoot: Path, destinations: Vector[Path], protectedPaths: Set[Path]): Unit = {
    val root = _direct_directory(projectRoot, "Video production root")
    val paths = destinations.map(_.toAbsolutePath.normalize())
    if (paths.distinct.size != paths.size)
      RAISE.invalidArgumentFault("Video production has duplicate generated destinations")
    val protectedpaths = protectedPaths.map(_.toAbsolutePath.normalize())
    paths.foreach { destination =>
      if (!destination.startsWith(root) || destination == root)
        RAISE.invalidArgumentFault(s"Video generated destination escapes its project root: $destination")
      _preflight_direct_destination(destination)
      if (_protected_destination(destination, protectedpaths) || protectedpaths.exists { input =>
        Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && Files.exists(input, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(destination, input)
      })
        RAISE.invalidArgumentFault(s"Video generated destination aliases a declared input: $destination")
    }
  }

  private def _preflight_direct_destination(destination: Path): Unit = {
    val parent = Option(destination.getParent).getOrElse(RAISE.invalidArgumentFault(s"Video generated destination has no parent: $destination"))
    var current = parent.getRoot
    parent.iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        RAISE.invalidArgumentFault(s"Video generated destination has an unsafe ancestor: $current")
    }
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
      _require_direct_regular_file(destination, s"Video generated destination $destination")
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

  private def _require_direct_directory(path: Path, label: String): Unit =
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"$label must be an existing direct non-symlink directory: $path")

  private def _require_direct_regular_file(path: Path, label: String): Unit =
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"$label must be a direct regular non-symlink file: $path")
}
