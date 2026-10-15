package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal
import CmlCapabilityGeneration.Generated
import CmlCapabilityGenerationContext._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityGenerationScala {
  final case class RenderedArtifact(selection: ScalaOutput, content: String)
  final class RenderedBatch private[CmlCapabilityGenerationScala] (
    val generated: Generated,
    val selections: Vector[ScalaOutput],
    val artifacts: Vector[RenderedArtifact]
  )
  private val _keywords = Set("abstract", "case", "catch", "class", "def", "do", "else", "extends", "false",
    "final", "finally", "for", "forSome", "if", "implicit", "import", "lazy", "macro", "match", "new", "null", "object",
    "override", "package", "private", "protected", "return", "sealed", "super", "this", "throw", "trait", "true",
    "try", "type", "val", "var", "while", "with", "yield", "enum", "export", "given", "then", "using", "end",
    "extension", "inline", "opaque", "open", "transparent", "derives")

  def render(generated: Generated, selections: Vector[ScalaOutput]): Either[Vector[Issue], RenderedBatch] = {
    if (generated == null) return Left(Vector(Issue(IssueKind.Malformed, "generated", None, Vector.empty, "A successful Generated is required.")))
    if (selections == null) return Left(Vector(Issue(IssueKind.Malformed, "selections", None, Vector.empty, "Explicit output selections are required.")))
    val projections = generated.projections.map(x => x.snapshotKey -> x).toMap
    val keys = scala.collection.mutable.Map.empty[SnapshotKey, ScalaOutput]
    val paths = scala.collection.mutable.Map.empty[String, ScalaOutput]
    val symbols = scala.collection.mutable.Map.empty[String, ScalaOutput]
    def _source_(selection: ScalaOutput): Option[CmlCapabilitySource.CapabilitySource] =
      Option(selection).flatMap(x => projections.get(x.key)).map(_.context.source)
    val selectionissues = selections.zipWithIndex.flatMap { case (selection, index) =>
      val field = s"selections[$index]"
      def _issue_(kind: IssueKind, suffix: String, detail: String, previous: Option[ScalaOutput] = None): Issue =
        Issue(kind, s"$field.$suffix", _source_(selection), previous.flatMap(_source_).toVector, detail)
      if (selection == null) Vector(_issue_(IssueKind.Malformed, "selection", "An explicit selection is required."))
      else {
        val unknown = if (projections.contains(selection.key)) Vector.empty else
          Vector(_issue_(IssueKind.UnknownSnapshot, "key", "No generated projection has this exact SnapshotKey."))
        val duplicate = keys.get(selection.key).toVector.map(previous =>
          _issue_(IssueKind.DuplicateSnapshot, "key", "A snapshot requires exactly one output selection.", Some(previous)))
        keys.getOrElseUpdate(selection.key, selection)
        val pathissues = if (_valid_output_path(selection.path)) Vector.empty else
          Vector(_issue_(IssueKind.Malformed, "path", "Select a canonical project-relative POSIX target/*.scala path."))
        val packageissues = if (selection.packagename != null && selection.packagename.nonEmpty && selection.packagename.forall(_valid_identifier)) Vector.empty else
          Vector(_issue_(IssueKind.Malformed, "packagename", "Nonempty ASCII package segments excluding Scala 2/3 keywords are required."))
        val objectissues = if (_valid_identifier(selection.objectname)) Vector.empty else
          Vector(_issue_(IssueKind.Malformed, "objectname", "An ASCII object name excluding Scala 2/3 keywords is required."))
        val pathconflict = if (pathissues.nonEmpty) Vector.empty else {
          val previous = paths.get(selection.path)
          paths.getOrElseUpdate(selection.path, selection)
          previous.toVector.map(x => _issue_(IssueKind.OutputConflict, "path", "Two snapshots claim one output path.", Some(x)))
        }
        val symbolconflict = if (packageissues.nonEmpty || objectissues.nonEmpty) Vector.empty else {
          val symbol = (selection.packagename :+ selection.objectname).mkString(".")
          val previous = symbols.get(symbol)
          symbols.getOrElseUpdate(symbol, selection)
          previous.toVector.map(x => _issue_(IssueKind.OutputConflict, "objectname", "Two snapshots claim one qualified object name.", Some(x)))
        }
        unknown ++ duplicate ++ pathissues ++ packageissues ++ objectissues ++ pathconflict ++ symbolconflict
      }
    }
    val missing = generated.projections.filterNot(x => keys.contains(x.snapshotKey)).map(x =>
      Issue(IssueKind.MissingCoverage, "selections", Some(x.context.source), Vector.empty, "Every generated projection requires one explicit output selection."))
    val issues = selectionissues ++ missing
    if (issues.nonEmpty) Left(issues)
    else {
      val ordered = selections.sortBy(_.path)
      val artifacts = ordered.map(selection => RenderedArtifact(selection,
        _render(selection, projections(selection.key).projectionJson)))
      Right(new RenderedBatch(generated, ordered, artifacts))
    }
  }

  def write(outputRoot: Path, batch: RenderedBatch): Either[PublicationFailure, Vector[Path]] = {
    val written = scala.collection.mutable.ArrayBuffer.empty[Path]
    if (outputRoot == null || batch == null)
      return Left(PublicationFailure(Vector(Issue(IssueKind.Malformed, "outputRoot/batch", None, Vector.empty, "An explicit root and rendered batch are required.")), Vector.empty))
    val root = outputRoot.toAbsolutePath.normalize()
    var active = Option.empty[RenderedArtifact]
    try {
      val issues = _root_issues(root) ++ batch.artifacts.flatMap(artifact => _filesystem_issues(root, batch, artifact))
      if (issues.nonEmpty) Left(PublicationFailure(issues, Vector.empty))
      else {
        batch.artifacts.foreach { artifact =>
          active = Some(artifact)
          val target = root.resolve(artifact.selection.path)
          Files.createDirectories(target.getParent)
          Files.write(target, artifact.content.getBytes(StandardCharsets.UTF_8))
          written += target
        }
        Right(batch.artifacts.map(x => root.resolve(x.selection.path)))
      }
    } catch {
      case NonFatal(error) =>
        val source = active.flatMap(x => batch.generated.projections.find(_.snapshotKey == x.selection.key)).map(_.context.source)
        Left(PublicationFailure(Vector(Issue(IssueKind.IoFailure, active.map(_.selection.path).getOrElse("outputRoot"),
          source, Vector.empty, error.getClass.getName + ": " + Option(error.getMessage).getOrElse("IO failure"))), written.toVector))
    }
  }

  private def _root_issues(root: Path): Vector[Issue] =
    Iterator.iterate(root)(_.getParent).takeWhile(_ != null).toVector.reverse.flatMap { path =>
      if (Files.isSymbolicLink(path)) Vector(Issue(IssueKind.UnsafeOutput, path.toString, None, Vector.empty, "Symlink output root or ancestor is forbidden."))
      else if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        Vector(Issue(IssueKind.UnsafeOutput, path.toString, None, Vector.empty, "Every existing output-root ancestor must be a regular directory."))
      else Vector.empty
    }

  private def _filesystem_issues(root: Path, batch: RenderedBatch, artifact: RenderedArtifact): Vector[Issue] = {
    val target = root.resolve(artifact.selection.path)
    val source = batch.generated.projections.find(_.snapshotKey == artifact.selection.key).map(_.context.source)
    def _issue_(kind: IssueKind, path: Path, detail: String): Issue =
      Issue(kind, path.toString, source, Vector.empty, detail)
    val ancestors = Iterator.iterate(target.getParent)(_.getParent).takeWhile(_ != null).toVector.reverse
    val parentissues = ancestors.flatMap { path =>
      if (Files.isSymbolicLink(path)) Vector(_issue_(IssueKind.UnsafeOutput, path, "Symlink output root or ancestor is forbidden."))
      else if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        Vector(_issue_(IssueKind.UnsafeOutput, path, "Every existing output ancestor must be a regular directory."))
      else Vector.empty
    }
    if (parentissues.nonEmpty) parentissues
    else if (Files.isSymbolicLink(target) || (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)))
      Vector(_issue_(IssueKind.UnsafeOutput, target, "Existing output must be a regular file, never a symlink."))
    else Vector.empty
  }

  private def _valid_output_path(value: String): Boolean = CmlCapabilitySource._valid_path(value) &&
    value.startsWith("target/") && value.endsWith(".scala")
  private def _valid_identifier(value: String): Boolean = value != null && value.matches("[A-Za-z][A-Za-z0-9_]*") && !_keywords(value)
  private def _render(selection: ScalaOutput, json: String): String = {
    val segments = _segments(json).map(_literal).mkString(",\n    ")
    "package " + selection.packagename.mkString(".") + "\n\nobject " + selection.objectname + " {\n" +
      "  private val _segments: Vector[String] = Vector(\n    " + segments + "\n  )\n" +
      "  def projectionJson: String = _segments.mkString\n}\n"
  }
  private def _segments(value: String): Vector[String] = {
    val result = Vector.newBuilder[String]
    var offset = 0
    while (offset < value.length) {
      var end = math.min(offset + 2000, value.length)
      if (end < value.length && Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) end -= 1
      result += value.substring(offset, end)
      offset = end
    }
    result.result()
  }
  private def _literal(value: String): String = {
    val result = new StringBuilder("\"")
    value.foreach {
      case '"' => result.append("\\\"")
      case '\\' => result.append("\\\\")
      case '\b' => result.append("\\b")
      case '\t' => result.append("\\t")
      case '\n' => result.append("\\n")
      case '\f' => result.append("\\f")
      case '\r' => result.append("\\r")
      case character if character < ' ' => result.append(f"\\u${character.toInt}%04x")
      case character => result.append(character)
    }
    result.append('"').toString
  }
}
