package cozy.lint

import cozy.archive.CarCmlSourceResolver
import cozy.config.CozyProjectYamlConfig
import cozy.modeler.CmlModelMetadata
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
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
    Vector("index", "reference-manual", "reference")
  )
  private val _canonical_user_guide_paths = _document_paths(Vector("user-guide"))
  private val _legacy_user_guide_paths = _document_paths(
    Vector(
      "src/main/web/docs/user-guide",
      "docs/user-guide",
      "docs/guide/index"
    )
  ) ++ Vector("docs/guide/README.md")
  private val _manual_image_reference_pattern: Regex = """!\[[^\]]*\]\(([^)]*)\)""".r
  private val _manual_link_reference_pattern: Regex = """(?<!!)\[[^\]]*\]\(([^)]*)\)""".r
  private val _url_scheme_pattern: Regex = "^[A-Za-z][A-Za-z0-9+.-]*:".r

  def lint(projectroot: Path): Vector[Finding] = {
    val root = projectroot.toAbsolutePath.normalize()
    val carsource = _car_source(root)
    val manualroot = carsource.resolve("manual").toAbsolutePath.normalize()
    val referencefindings = _document_findings(
      root,
      manualroot,
      _canonical_reference_manual_paths.map(manualroot.resolve),
      Vector.empty,
      "car.documentation.reference-manual",
      "reference manual",
      manualroot.resolve("index.md")
    )
    val userguidefindings = _document_findings(
      root,
      manualroot,
      _canonical_user_guide_paths.map(manualroot.resolve),
      _legacy_user_guide_paths,
      "car.documentation.user-guide",
      "user guide",
      manualroot.resolve("user-guide.md")
    )
    val manualfindings = _manual_findings(manualroot)
    val cmlfindings = _cml_findings(root)
    (referencefindings ++ userguidefindings ++ manualfindings ++ cmlfindings).
      sortBy(x => (x.path.toString, x.line, x.code, x.message))
  }

  private def _car_source(root: Path): Path = {
    val config = CozyProjectYamlConfig.loadProjectConfig(root)
    config.value("packaging.car.source_dir").
      map(path => _config_path(root, path)).
      getOrElse(root.resolve("src/main/car").toAbsolutePath.normalize())
  }

  private def _config_path(root: Path, value: String): Path = {
    val path = Paths.get(value)
    if (path.isAbsolute)
      path.normalize()
    else
      root.resolve(path).toAbsolutePath.normalize()
  }

  private def _document_findings(
    root: Path,
    manualroot: Path,
    canonicalpaths: Vector[Path],
    legacypaths: Vector[String],
    codeprefix: String,
    documentname: String,
    missingpath: Path
  ): Vector[Finding] = {
    val canonicalexisting = canonicalpaths.
      filter(path => !_contains_symlink(manualroot, path) && _regular_file(path)).
      sortBy(_.toString)
    val legacyexisting = legacypaths.
      map(root.resolve).
      filter(_regular_file).
      sortBy(_.toString)
    val existing =
      if (canonicalexisting.nonEmpty) canonicalexisting
      else legacyexisting
    val duplicatefindings =
      if (canonicalexisting.size > 1)
        Vector(Finding(
          Level.Warn,
          "car.documentation.manual.entry-point.duplicate",
          s"CAR ${documentname} has multiple canonical manual entry points.",
          canonicalexisting(1),
          1
        ))
      else
        Vector.empty
    if (existing.isEmpty)
      duplicatefindings ++ Vector(Finding(
        Level.Warn,
        s"${codeprefix}.missing",
        s"CAR ${documentname} is not defined. Add a canonical ${documentname} entry point.",
        missingpath,
        1
      ))
    else {
      val usable = existing.filter(_nonempty_document)
      if (usable.nonEmpty)
        duplicatefindings ++ Vector(Finding(
          Level.Ok,
          s"${codeprefix}.present",
          s"CAR ${documentname} is defined.",
          usable.head,
          1
        ))
      else
        duplicatefindings ++ Vector(Finding(
          Level.Warn,
          s"${codeprefix}.empty",
          s"CAR ${documentname} entry point is empty.",
          existing.head,
          1
        ))
    }
  }

  private def _manual_findings(manualroot: Path): Vector[Finding] =
    _manual_files(manualroot).flatMap { path =>
      _read_utf8(path) match {
        case Left(_) =>
          Vector(Finding(
            Level.Warn,
            "car.documentation.manual.input.unparseable",
            "CAR manual input is not readable as UTF-8.",
            path,
            1
          ))
        case Right(text) =>
          _manual_reference_findings(path, text, manualroot)
      }
    }.foldLeft(Vector.empty[Finding]) { (findings, finding) =>
      if (findings.exists(existing =>
        existing.code == finding.code &&
          existing.path == finding.path &&
          existing.message == finding.message
      )) findings else findings :+ finding
    }

  private def _manual_files(manualroot: Path): Vector[Path] = {
    if (_has_symlink_component(manualroot) || !Files.isDirectory(manualroot, LinkOption.NOFOLLOW_LINKS))
      Vector.empty
    else {
      try {
        val stream = Files.walk(manualroot)
        try {
          stream.iterator().asScala.toVector.
            filter(path =>
              _within(path, manualroot) &&
                !_contains_symlink(manualroot, path) &&
                _regular_file(path) &&
                _supported_document(path)
            ).
            sortBy(_.toString)
        } finally {
          stream.close()
        }
      } catch {
        case NonFatal(_) => Vector.empty
      }
    }
  }

  private def _manual_reference_findings(
    path: Path,
    text: String,
    manualroot: Path
  ): Vector[Finding] =
    _manual_image_reference_pattern.findAllMatchIn(text).flatMap { reference =>
      _manual_reference_finding(
        path,
        manualroot,
        _manual_reference_target(reference.group(1)),
        true,
        text.substring(0, reference.start).count(_ == '\n') + 1
      )
    }.toVector ++ _manual_link_reference_pattern.findAllMatchIn(text).flatMap { reference =>
      _manual_reference_finding(
        path,
        manualroot,
        _manual_reference_target(reference.group(1)),
        false,
        text.substring(0, reference.start).count(_ == '\n') + 1
      )
    }.toVector

  private def _manual_reference_finding(
    path: Path,
    manualroot: Path,
    target: String,
    image: Boolean,
    line: Int
  ): Option[Finding] = {
    val localtarget = target.takeWhile(_ != '#')
    if (localtarget.isEmpty || _url_scheme_pattern.findPrefixOf(localtarget).nonEmpty)
      None
    else {
      val targetpath = try {
        Some(Paths.get(localtarget))
      } catch {
        case NonFatal(_) => None
      }
      targetpath match {
        case None => Some(Finding(
          Level.Warn,
          "car.documentation.manual.link.unsafe",
          s"CAR manual reference '$target' is not a safe local path.",
          path,
          line
        ))
        case Some(parsedpath) =>
          if (parsedpath.isAbsolute)
            Some(Finding(
              Level.Warn,
              "car.documentation.manual.link.unsafe",
              s"CAR manual reference '$target' is an absolute path outside the local manual contract.",
              path,
              line
            ))
          else {
            val resolved = path.getParent.resolve(parsedpath).toAbsolutePath.normalize()
            if (!_within(resolved, manualroot))
              Some(Finding(
                Level.Warn,
                "car.documentation.manual.link.unsafe",
                s"CAR manual reference '$target' escapes the admitted manual subtree.",
                path,
                line
              ))
            else if (!_regular_manual_file(resolved, manualroot))
              Some(Finding(
                Level.Warn,
                if (image)
                  "car.documentation.manual.asset.missing"
                else
                  "car.documentation.manual.link.stale",
                if (image)
                  s"CAR manual asset '$target' is missing."
                else
                  s"CAR manual reference '$target' is missing.",
                path,
                line
              ))
            else
              None
          }
      }
    }
  }

  private def _manual_reference_target(value: String): String = {
    val trimmed = value.trim
    if (trimmed.startsWith("<")) {
      val end = trimmed.indexOf('>')
      if (end >= 0) trimmed.substring(1, end).trim else trimmed.substring(1).trim
    } else {
      trimmed.takeWhile(character => !character.isWhitespace)
    }
  }

  private def _within(path: Path, root: Path): Boolean = {
    val normalizedpath = path.toAbsolutePath.normalize()
    val normalizedroot = root.toAbsolutePath.normalize()
    normalizedpath == normalizedroot || normalizedpath.startsWith(normalizedroot)
  }

  private def _regular_manual_file(path: Path, manualroot: Path): Boolean =
    !_contains_symlink(manualroot, path) && _regular_file(path)

  private def _contains_symlink(root: Path, path: Path): Boolean = {
    if (!_within(path, root))
      false
    else if (_has_symlink_component(root))
      true
    else {
      var current = root
      if (Files.isSymbolicLink(current))
        true
      else
        root.relativize(path).iterator().asScala.exists { segment =>
          current = current.resolve(segment.toString)
          Files.isSymbolicLink(current)
        }
    }
  }

  private def _has_symlink_component(path: Path): Boolean = {
    val normalized = path.toAbsolutePath.normalize()
    val filesystemroot = normalized.getRoot
    if (filesystemroot == null)
      false
    else {
      var current = filesystemroot
      normalized.iterator().asScala.exists { segment =>
        current = current.resolve(segment.toString)
        Files.isSymbolicLink(current)
      }
    }
  }

  private def _supported_document(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase(java.util.Locale.ROOT)
    _document_extensions.exists(extension => name.endsWith(s".$extension"))
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

  private def _read_utf8(path: Path): Either[Unit, String] =
    try {
      val decoder = StandardCharsets.UTF_8.newDecoder().
        onMalformedInput(CodingErrorAction.REPORT).
        onUnmappableCharacter(CodingErrorAction.REPORT)
      Right(decoder.decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString)
    } catch {
      case NonFatal(_) => Left(())
    }

  private def _nonempty_document(path: Path): Boolean =
    _read_utf8(path) match {
      case Right(text) => text.trim.nonEmpty
      case Left(_) => false
    }
}
