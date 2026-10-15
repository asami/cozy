package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.util.Locale
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import CmlCapabilityGenerationContext._
import CmlCapabilityPackagePublication.{Artifact, Batch}
import CmlCapabilityProductionRequest.{METADATA_PREFIX, USAGE_METADATA_PREFIX}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityPackagePublicationPart {
  def _preflight(root: Path, batch: Batch): Either[Vector[Issue], Unit] = {
    if (root == null || batch == null)
      return Left(Vector(_issue("outputRoot/batch", "Explicit output root and accepted batch are required.")))
    try {
      val outputroot = root.toAbsolutePath.normalize()
      val issues = _ancestors(outputroot) ++ batch.artifacts.flatMap(_file_issues(outputroot, _)) ++
        batch.packages.flatMap { bundle =>
          val metadata = outputroot.resolve(bundle.path + "/" + METADATA_PREFIX)
          _inventory(metadata, (bundle.resources.map(_.filename) :+ "index.json").toSet) ++
            _inventory(outputroot.resolve(bundle.path + "/" + USAGE_METADATA_PREFIX),
              if (bundle.usagecapabilities.isEmpty) Set.empty[String] else Set("index.json", "usage.json")) ++
            _descriptor_inventory(outputroot.resolve(bundle.path))
        }
      if (issues.nonEmpty) Left(issues) else Right(())
    } catch { case NonFatal(error) => Left(Vector(_io(root.toString, error))) }
  }
  def _publish(root: Path, batch: Batch, write: (Path, String) => Unit): Either[PublicationFailure, Vector[Path]] = {
    val written = scala.collection.mutable.ArrayBuffer.empty[Path]
    _preflight(root, batch) match {
      case Left(issues) => Left(PublicationFailure(issues, Vector.empty))
      case Right(_) =>
        val outputroot = root.toAbsolutePath.normalize()
        var active = "outputRoot"
        try {
          batch.artifacts.foreach { artifact =>
            active = artifact.path
            val path = outputroot.resolve(artifact.path)
            write(path, artifact.content)
            written += path
          }
          Right(batch.artifacts.map(x => outputroot.resolve(x.path)))
        } catch { case NonFatal(error) => Left(PublicationFailure(Vector(_io(active, error)), written.toVector)) }
    }
  }
  def _write_file(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }
  private def _ancestors(path: Path): Vector[Issue] =
    Iterator.iterate(path)(_.getParent).takeWhile(_ != null).toVector.reverse.flatMap { item =>
      val kindissues = if (Files.isSymbolicLink(item) || (Files.exists(item, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)))
        Vector(_issue(item.toString, "Existing root and ancestors must be regular directories without symlinks."))
      else Vector.empty
      kindissues ++ _case_alias(item)
    }
  private def _case_alias(path: Path): Vector[Issue] = {
    val parent = path.getParent
    if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent)) Vector.empty
    else {
      val stream = Files.list(parent)
      try stream.iterator().asScala.toVector.collect {
        case item if item.getFileName.toString != path.getFileName.toString &&
          item.getFileName.toString.toLowerCase(Locale.ROOT) == path.getFileName.toString.toLowerCase(Locale.ROOT) =>
          _issue(path.toString, "Case-aliased existing output path is forbidden.")
      } finally stream.close()
    }
  }
  private def _file_issues(root: Path, artifact: Artifact): Vector[Issue] = {
    val path = root.resolve(artifact.path)
    val parents = _ancestors(path.getParent)
    if (parents.nonEmpty) parents
    else if (Files.isSymbolicLink(path) || (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
      Vector(_issue(path.toString, "Existing output leaf must be a regular file without symlinks."))
    else _case_alias(path)
  }
  private def _descriptor_inventory(root: Path): Vector[Issue] = {
    val parents = _ancestors(root)
    if (parents.nonEmpty || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) parents
    else {
      val formats = Vector("json", "yaml", "yml", "conf", "hocon", "xml")
      val rootnames = (formats.map("descriptor." + _) ++
        formats.filterNot(_ == "json").map("component-descriptor." + _)).toSet
      val metanames = formats.map("component-descriptor." + _).toSet
      def _alternatives_(directory: Path, names: Set[String]): Vector[Issue] = {
        val stream = Files.list(directory)
        try stream.iterator().asScala.toVector.sortBy(_.toString).flatMap { path =>
          if (!names(path.getFileName.toString.toLowerCase(Locale.ROOT))) Vector.empty
          else if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            Vector(_issue(path.toString, "Alternative descriptor slot must not be a symlink or nonregular entry; canonical root component-descriptor.json is the sole slot."))
          else Vector(Issue(IssueKind.OutputConflict, path.toString, None, Vector.empty,
            "Alternative canonical descriptor is preserved; root component-descriptor.json is the sole allowed slot, even for equal content."))
        } finally stream.close()
      }
      val metadata = root.resolve("meta")
      val metaissues = _ancestors(metadata)
      _alternatives_(root, rootnames) ++ metaissues ++
        (if (metaissues.nonEmpty || !Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) Vector.empty
          else _alternatives_(metadata, metanames))
    }
  }
  private def _inventory(metadata: Path, names: Set[String]): Vector[Issue] = {
    val parents = _ancestors(metadata)
    if (parents.nonEmpty || !Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) parents
    else {
      val stream = Files.walk(metadata)
      try stream.iterator().asScala.toVector.filterNot(_ == metadata).flatMap { path =>
        if (Files.isSymbolicLink(path) || (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
          Vector(_issue(path.toString, "Metadata inventory may contain only regular files and directories without symlinks."))
        else if (path.getFileName.toString.toLowerCase(Locale.ROOT).endsWith(".json") &&
          (path.getParent != metadata || !names(path.getFileName.toString) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
          Vector(_issue(path.toString, "Unlisted, nested or case-aliased metadata JSON is forbidden."))
        else Vector.empty
      } finally stream.close()
    }
  }
  private def _issue(path: String, detail: String): Issue = Issue(IssueKind.UnsafeOutput, path, None, Vector.empty, detail)
  private def _io(path: String, error: Throwable): Issue = Issue(IssueKind.IoFailure, path, None, Vector.empty,
    error.getClass.getName + ": " + Option(error.getMessage).getOrElse("publication IO failure"))
}
