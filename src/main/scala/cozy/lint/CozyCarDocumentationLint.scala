package cozy.lint

import cozy.archive.CarCmlSourceResolver
import cozy.modeler.CmlModelMetadata
import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal

/*
 * @since   Jul. 14, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCarDocumentationLint {
  sealed trait Level {
    def name: String
  }
  object Level {
    case object Ok extends Level {
      val name = "OK"
    }
    case object Warn extends Level {
      val name = "WARN"
    }
  }

  final case class Finding(
    level: Level,
    code: String,
    message: String,
    path: Path,
    line: Int
  )

  private val _minimum_description_characters = 24
  private val _document_extensions = Vector("md", "markdown", "adoc", "asciidoc", "dox", "html")
  private val _canonical_reference_manual_paths = _document_paths(
    Vector(
      "src/main/car/manual/index",
      "src/main/car/manual/reference-manual",
      "src/main/car/manual/reference"
    )
  )
  private val _canonical_user_guide_paths = _document_paths(
    Vector("src/main/car/manual/user-guide")
  )
  private val _legacy_user_guide_paths = _document_paths(
    Vector(
      "src/main/web/docs/user-guide",
      "docs/user-guide",
      "docs/guide/index"
    )
  ) ++ Vector("docs/guide/README.md")

  def lint(projectroot: Path): Vector[Finding] = {
    val root = projectroot.toAbsolutePath.normalize()
    val referencefindings = _document_findings(
      root,
      _canonical_reference_manual_paths,
      Vector.empty,
      "car.documentation.reference-manual",
      "reference manual",
      root.resolve("src/main/car/manual/index.md")
    )
    val userguidefindings = _document_findings(
      root,
      _canonical_user_guide_paths,
      _legacy_user_guide_paths,
      "car.documentation.user-guide",
      "user guide",
      root.resolve("src/main/car/manual/user-guide.md")
    )
    val cmlfindings = _cml_findings(root)
    (referencefindings ++ userguidefindings ++ cmlfindings).
      sortBy(x => (x.path.toString, x.line, x.code, x.message))
  }

  private def _document_findings(
    root: Path,
    canonicalpaths: Vector[String],
    legacypaths: Vector[String],
    codeprefix: String,
    documentname: String,
    missingpath: Path
  ): Vector[Finding] = {
    val canonicalexisting = canonicalpaths.
      map(root.resolve).
      filter(_regular_file).
      sortBy(_.toString)
    val legacyexisting = legacypaths.
      map(root.resolve).
      filter(_regular_file).
      sortBy(_.toString)
    val existing =
      if (canonicalexisting.nonEmpty) canonicalexisting
      else legacyexisting
    if (existing.isEmpty)
      Vector(Finding(
        Level.Warn,
        s"${codeprefix}.missing",
        s"CAR ${documentname} is not defined. Add a canonical ${documentname} entry point.",
        missingpath,
        1
      ))
    else {
      val usable = existing.filter(_nonempty_document)
      if (usable.nonEmpty)
        Vector(Finding(
          Level.Ok,
          s"${codeprefix}.present",
          s"CAR ${documentname} is defined.",
          usable.head,
          1
        ))
      else
        Vector(Finding(
          Level.Warn,
          s"${codeprefix}.empty",
          s"CAR ${documentname} entry point is empty.",
          existing.head,
          1
        ))
    }
  }

  private def _cml_findings(root: Path): Vector[Finding] =
    CarCmlSourceResolver.resolve(root) match {
      case Left(_) =>
        Vector.empty
      case Right(source) =>
        try {
          val metadata = CmlModelMetadata.fromCml(source.source, source.projectRelativePath, "cml")
          metadata.surface.component match {
            case Some(component) =>
              _description_findings(
                "component",
                component.name,
                component.descriptive,
                component.narrative,
                source.source
              ) ++
                component.services.flatMap { service =>
                  _description_findings(
                    "service",
                    service.name,
                    service.descriptive,
                    service.narrative,
                    source.source
                  ) ++ service.operations.flatMap { operation =>
                    _description_findings(
                      "operation",
                      s"${service.name}.${operation.name}",
                      operation.descriptive,
                      operation.narrative,
                      source.source
                    )
                  }
                }
            case None =>
              Vector(Finding(
                Level.Warn,
                "car.documentation.component-help.missing",
                "The representative component help source is missing from CML.",
                source.source,
                1
              ))
          }
        } catch {
          case NonFatal(_) => Vector.empty
        }
    }

  private def _description_findings(
    kind: String,
    name: String,
    descriptive: CmlModelMetadata.Descriptive,
    narrative: Option[String],
    path: Path
  ): Vector[Finding] = {
    val text = _description_text(descriptive, narrative)
    val size = text.codePoints().toArray.count(x => !Character.isWhitespace(x))
    if (text.isEmpty)
      Vector(Finding(
        Level.Warn,
        s"car.documentation.${kind}.description.missing",
        s"${kind.capitalize} '${name}' has no summary, description, or narrative for generated help.",
        path,
        1
      ))
    else if (size < _minimum_description_characters)
      Vector(Finding(
        Level.Warn,
        s"car.documentation.${kind}.description.thin",
        s"${kind.capitalize} '${name}' help text is too short to explain its purpose (${size} non-whitespace characters; expected at least ${_minimum_description_characters}).",
        path,
        1
      ))
    else
      Vector(Finding(
        Level.Ok,
        s"car.documentation.${kind}.description.present",
        s"${kind.capitalize} '${name}' has descriptive help text.",
        path,
        1
      ))
  }

  private def _description_text(
    descriptive: CmlModelMetadata.Descriptive,
    narrative: Option[String]
  ): String =
    Vector(
      descriptive.brief,
      descriptive.summary,
      descriptive.description,
      narrative
    ).flatten.map(_.trim).filter(_.nonEmpty).distinct.mkString(" ")

  private def _document_paths(stems: Vector[String]): Vector[String] =
    stems.flatMap(stem => _document_extensions.map(extension => s"${stem}.${extension}"))

  private def _regular_file(path: Path): Boolean =
    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _nonempty_document(path: Path): Boolean =
    try Files.readString(path).trim.nonEmpty
    catch {
      case NonFatal(_) => false
    }
}
