package cozy.modeler

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import CmlCapabilitySource._
import CmlCapabilityGenerationContext._
import CmlCapabilityProductionRequest._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityProductionSourcePart {
  def _request(path: Path): Either[Vector[Issue], ProductionRequest] =
    _bytes(path, REQUEST_LIMIT, None).flatMap(_utf8(_, path.toString, None))
      .flatMap(CmlCapabilityProductionRequestJsonPart.decode(_, path))

  def _preflight_source(sources: Vector[CapabilitySource], root: Path,
    path: Path, identity: String): Either[Vector[Issue], Unit] = {
    def _issue_(detail: String): Issue =
      Issue(IssueKind.Malformed, "sources", Option(sources).flatMap(_.headOption).flatMap(Option(_)), Vector.empty, detail)
    if (root == null || path == null || sources == null || sources.size != 1)
      return Left(Vector(_issue_("Selected primary source requires exactly one selected source and explicit root and positional path.")))
    val sourceissues = _source_issues(sources)
    if (sourceissues.nonEmpty) return Left(sourceissues)
    val source = sources.head
    try {
      val sourceroot = root.toAbsolutePath.normalize()
      val selected = sourceroot.resolve(source.path).normalize()
      if (identity != source.path || !selected.startsWith(sourceroot) || path.toAbsolutePath.normalize() != selected)
        Left(Vector(_issue_("Selected primary source requires one exact selected identity and physical path before acquisition.")))
      else {
        val readableissues = Iterator.iterate(selected.getParent)(_.getParent).takeWhile(_ != null).toVector.reverse.flatMap { parent =>
          if (Files.isReadable(parent)) Vector.empty
          else Vector(Issue(IssueKind.Malformed, parent.toString, Some(source), Vector.empty,
            "Source root and ancestors must be readable regular directories without symlinks."))
        }
        val issues = _directory_issues(sourceroot) ++ _file_issues(selected, Some(source)) ++ readableissues
        if (issues.nonEmpty) Left(issues)
        else if (Files.size(selected) > SOURCE_LIMIT)
          Left(Vector(_issue_("Selected source exceeds 16MiB before acquisition.")))
        else Right(())
      }
    } catch { case NonFatal(error) => Left(Vector(Issue(IssueKind.IoFailure, path.toString, Some(source), Vector.empty,
      error.getClass.getName + ": " + Option(error.getMessage).getOrElse("input IO failure")))) }
  }

  def _acquire(sources: Vector[CapabilitySource], root: Path,
    selected: Option[GenerationProvenance.SourceInput]): Either[Vector[Issue], Vector[SourceInput]] = {
    def _issue_(detail: String, source: Option[CapabilitySource] = None): Issue =
      Issue(IssueKind.Malformed, "sources", source, Vector.empty, detail)
    if (root == null || sources == null || selected == null || sources.isEmpty || sources.size > SOURCE_COUNT)
      return Left(Vector(_issue_("An explicit regular source root and 1..256 selected source headers are required.")))
    val sourceissues = _source_issues(sources)
    if (sourceissues.nonEmpty) return Left(sourceissues)
    if (sources.map(x => SourceScope(x.modelAuthority, x.path)).distinct.size != sources.size)
      return Left(Vector(_issue_("Selected SourceScopes must be unique.")))
    val sourceroot = root.toAbsolutePath.normalize()
    val rootissues = _directory_issues(sourceroot)
    if (rootissues.nonEmpty) return Left(rootissues)
    selected.foreach { input =>
      if (input == null || sources.size != 1 || input.path == null ||
        input.identity != sources.head.path || input.path.toAbsolutePath.normalize() != sourceroot.resolve(sources.head.path).normalize())
        return Left(Vector(_issue_("Selected primary source requires one exact selected identity and physical path.", sources.headOption)))
    }
    var total = 0L
    val acquired = sources.map { source =>
      val path = sourceroot.resolve(source.path)
      val bytes = if (total > TOTAL_LIMIT) Left(Vector(_issue_("Selected sources exceed 64MiB in total.", Some(source))))
      else _bytes(path, SOURCE_LIMIT, Some(source))
      bytes.flatMap { content =>
        total += content.length
        if (total > TOTAL_LIMIT) Left(Vector(_issue_("Selected sources exceed 64MiB in total.", Some(source))))
        else Right(content)
      }
    }
    val acquisitionissues = acquired.collect { case Left(items) => items }.flatten
    if (acquisitionissues.nonEmpty) return Left(acquisitionissues)
    val results = sources.zip(acquired.collect { case Right(bytes) => bytes }).map { case (source, content) =>
      val path = sourceroot.resolve(source.path)
      _utf8(content, path.toString, Some(source)).flatMap { text =>
        try Right(SourceInput(SourceScope(source.modelAuthority, source.path), KaleidoxModel.parse(KaleidoxConfig.default, text)))
        catch { case NonFatal(error) => Left(Vector(Issue(IssueKind.Malformed, path.toString, Some(source), Vector.empty,
          "Source parsing failed: " + error.getClass.getName + ": " + Option(error.getMessage).getOrElse("parse failure")))) }
      }
    }
    val issues = results.collect { case Left(items) => items }.flatten
    if (issues.nonEmpty) Left(issues) else Right(results.collect { case Right(input) => input })
  }

  private def _source_issues(sources: Vector[CapabilitySource]): Vector[Issue] =
    sources.flatMap { source =>
      if (source == null || !_valid_text(source.modelAuthority) || !_valid_path(source.path) ||
        !_valid_text(source.sourceRevision) || source.line == null || source.line.exists(_ <= 0))
        Vector(Issue(IssueKind.Malformed, "sources", Option(source), Vector.empty, "Every selected source needs its complete valid literal header."))
      else if (!source.path.endsWith(".cml") && !source.path.endsWith(".dox"))
        Vector(Issue(IssueKind.Malformed, "sources", Some(source), Vector.empty, "Selected sources must have .cml or .dox extensions."))
      else Vector.empty
    }
  private def _directory_issues(path: Path): Vector[Issue] =
    Iterator.iterate(path)(_.getParent).takeWhile(_ != null).toVector.reverse.flatMap { item =>
      if (Files.isSymbolicLink(item) || !Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS))
        Vector(Issue(IssueKind.Malformed, item.toString, None, Vector.empty, "Source root and ancestors must be existing regular directories without symlinks."))
      else Vector.empty
    }
  private def _file_issues(path: Path, source: Option[CapabilitySource]): Vector[Issue] = {
    val parents = _directory_issues(path.toAbsolutePath.normalize().getParent)
    if (parents.nonEmpty) parents
    else if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path))
      Vector(Issue(IssueKind.Malformed, path.toString, source, Vector.empty, "Selected input must be a readable regular file without symlinks."))
    else Vector.empty
  }
  private def _bytes(path: Path, limit: Int, source: Option[CapabilitySource]): Either[Vector[Issue], Array[Byte]] = {
    if (path == null) return Left(Vector(Issue(IssueKind.Malformed, "path", source, Vector.empty, "Explicit input path is required.")))
    try {
      val issues = _file_issues(path, source)
      if (issues.nonEmpty) Left(issues)
      else if (Files.size(path) > limit) Left(Vector(Issue(IssueKind.Malformed, path.toString, source, Vector.empty, "Input exceeds its selected byte bound.")))
      else {
        val stream = Files.newInputStream(path)
        try {
          val content = new java.io.ByteArrayOutputStream()
          val buffer = new Array[Byte](8192)
          var count = stream.read(buffer)
          while (count >= 0) {
            if (content.size().toLong + count > limit)
              return Left(Vector(Issue(IssueKind.Malformed, path.toString, source, Vector.empty, "Input exceeds its selected byte bound.")))
            content.write(buffer, 0, count)
            count = stream.read(buffer)
          }
          Right(content.toByteArray)
        } finally stream.close()
      }
    } catch { case NonFatal(error) => Left(Vector(Issue(IssueKind.IoFailure, path.toString, source, Vector.empty,
      error.getClass.getName + ": " + Option(error.getMessage).getOrElse("input IO failure")))) }
  }
  private def _utf8(bytes: Array[Byte], path: String, source: Option[CapabilitySource]): Either[Vector[Issue], String] =
    try Right(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString)
    catch { case NonFatal(_) => Left(Vector(Issue(IssueKind.Malformed, path, source, Vector.empty, "Malformed UTF8 input."))) }
}
