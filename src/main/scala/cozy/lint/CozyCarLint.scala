package cozy.lint

import cozy.archive.CarCmlSourceResolver
import cozy.compatibility.CarMetadataCompatibility
import cozy.config.CozyProjectYamlConfig
import cozy.modeler.CmlModelMetadata
import org.goldenport.RAISE
import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal

/*
 * @since   Jul.  7, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCarLint {
  sealed trait Level {
    def name: String
  }
  object Level {
    case object Ok extends Level {
      val name = "OK"
    }
    case object Fail extends Level {
      val name = "FAIL"
    }
    case object Warn extends Level {
      val name = "WARN"
    }
  }

  final case class Finding(
    level: Level,
    category: String,
    code: String,
    message: String,
    path: Path,
    line: Int
  )

  def execute(args: List[String]): Int = {
    val config = Config.create(args)
    val findings = lint(config.path, config.baseline, config.noabi)
    _render(config, findings)
  }

  private[cozy] def execute(args: List[String], latestversion: Option[String]): Int = {
    val config = Config.create(args)
    val findings = lint(config.path, config.baseline, config.noabi, latestversion)
    _render(config, findings)
  }

  private[cozy] def lint(projectroot: Path, baseline: Option[Path], noabi: Boolean): Vector[Finding] = {
    val root = _project_root(projectroot)
    val buildfindings = CozyBuildLint.lintBuildOnly(root).map(_build_finding)
    _lint(root, baseline, noabi, buildfindings)
  }

  private[cozy] def lint(
    projectroot: Path,
    baseline: Option[Path],
    noabi: Boolean,
    latestversion: Option[String]
  ): Vector[Finding] = {
    val root = _project_root(projectroot)
    val buildfindings = CozyBuildLint.lintBuildOnly(root, latestversion).map(_build_finding)
    _lint(root, baseline, noabi, buildfindings)
  }

  private def _lint(
    root: Path,
    baseline: Option[Path],
    noabi: Boolean,
    buildfindings: Vector[Finding]
  ): Vector[Finding] = {
    val cmlsourcefindings = _car_cml_source_findings(root)
    val cmlfindings = _cml_path(root).toVector.flatMap(path => CozyCmlLint.lint(path).map(_cml_finding))
    val compatibilityfindings = _compatibility_findings(root)
    val documentationfindings = CozyCarDocumentationLint.lint(root).map(_documentation_finding)
    val repositoryfindings = _repository_findings(root)
    val abifindings =
      if (noabi)
        Vector.empty
      else
        CozyCarAbiLint.lint(root, baseline).map(_abi_finding)
    (buildfindings ++ cmlsourcefindings ++ cmlfindings ++ compatibilityfindings ++ documentationfindings ++ repositoryfindings ++ abifindings).sortBy(x => (x.category, x.path.toString, x.line, x.code, x.message))
  }

  private def _compatibility_findings(root: Path): Vector[Finding] = {
    val path = root.resolve("project.yaml")
    val decision =
      CarMetadataCompatibility.evaluateProject(
        CozyProjectYamlConfig.loadProjectMetadata(root)
      )
    if (decision.diagnostics.nonEmpty)
      decision.diagnostics.map { diagnostic =>
        Finding(
          Level.Fail,
          "compatibility",
          diagnostic.code.name,
          diagnostic.render,
          path,
          1
        )
      }
    else
      decision.contract.toVector.map { contract =>
        Finding(
          Level.Ok,
          "compatibility",
          "car.metadata.compatibility.accepted",
          contract.render,
          path,
          1
        )
      }
  }

  private def _repository_findings(root: Path): Vector[Finding] = {
    val index = root.resolve("repository/catalog/index.json")
    if (Files.isRegularFile(index))
      CozyRepositoryLint.lint(root).map { finding =>
        Finding(Level.Fail, "repository", finding.code, finding.message, finding.path, finding.line)
      }
    else
      Vector.empty
  }

  private def _car_cml_source_findings(root: Path): Vector[Finding] = {
    val generatedmetadata = root.resolve("target/cozy/model-metadata.json")
    if (Files.isRegularFile(generatedmetadata))
      return Vector.empty
    CarCmlSourceResolver.resolve(root) match {
      case Left(issue) =>
        Vector(Finding(Level.Fail, "cml", issue.code, issue.message, issue.path, 1))
      case Right(source) =>
        try {
          CmlModelMetadata.fromCml(source.source, source.projectrelativepath, "cml")
          Vector.empty
        } catch {
          case NonFatal(e) =>
            Vector(Finding(
              Level.Fail,
              "cml",
              "car.cml.metadata.generation_failed",
              s"Could not generate CML model metadata from ${source.projectrelativepath}: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}",
              source.source,
              1
            ))
        }
    }
  }

  private def _project_root(path: Path): Path = {
    val normalized = path.toAbsolutePath.normalize()
    if (Files.isDirectory(normalized))
      normalized
    else
      Option(normalized.getParent).getOrElse(normalized)
  }

  private def _cml_path(root: Path): Option[Path] = {
    val path = root.resolve("src/main/cozy")
    if (Files.isDirectory(path))
      Some(path)
    else
      None
  }

  private def _build_finding(finding: CozyBuildLint.Finding): Finding =
    Finding(_level(finding.level), "build", finding.code, finding.message, finding.path, finding.line)

  private def _cml_finding(finding: CozyCmlLint.Finding): Finding =
    Finding(_level(finding.level), "cml", finding.code, finding.message, finding.path, finding.line)

  private def _abi_finding(finding: CozyCarAbiLint.Finding): Finding =
    Finding(_level(finding.level), "abi", finding.code, finding.message, finding.path, finding.line)

  private def _documentation_finding(finding: CozyCarDocumentationLint.Finding): Finding =
    Finding(_level(finding.level), "documentation", finding.code, finding.message, finding.path, finding.line)

  private def _level(level: CozyBuildLint.Level): Level =
    level match {
      case CozyBuildLint.Level.Ok => Level.Ok
      case CozyBuildLint.Level.Fail => Level.Fail
      case CozyBuildLint.Level.Warn => Level.Warn
    }

  private def _level(level: CozyCmlLint.Level): Level =
    level match {
      case CozyCmlLint.Level.Fail => Level.Fail
      case CozyCmlLint.Level.Warn => Level.Warn
    }

  private def _level(level: CozyCarAbiLint.Level): Level =
    level match {
      case CozyCarAbiLint.Level.Ok => Level.Ok
      case CozyCarAbiLint.Level.Fail => Level.Fail
      case CozyCarAbiLint.Level.Warn => Level.Warn
    }

  private def _level(level: CozyCarDocumentationLint.Level): Level =
    level match {
      case CozyCarDocumentationLint.Level.Ok => Level.Ok
      case CozyCarDocumentationLint.Level.Warn => Level.Warn
    }

  private def _render(config: Config, findings: Vector[Finding]): Int = {
    config.format match {
      case "json" => println(toJson(findings))
      case "text" => println(toText(config.path, findings))
      case other => RAISE.invalidArgumentFault(s"Unsupported lint format: ${other}")
    }
    if (findings.exists(_.level == Level.Fail) || (config.strict && findings.exists(_strict_warning))) 1 else 0
  }

  private def _strict_warning(finding: Finding): Boolean =
    finding.level == Level.Warn && finding.code != "abi.baseline.missing"

  private[cozy] def toJson(findings: Seq[Finding]): String =
    s"""{"findings":[${findings.map(_finding_json).mkString(",")}]}"""

  private[cozy] def toText(path: Path, findings: Seq[Finding]): String =
    if (findings.isEmpty)
      s"OK car.no-findings [${path.toAbsolutePath.normalize()}]: No CAR lint findings."
    else
      findings.map { f =>
        s"${f.level.name} ${f.category} ${f.code} [${f.path}:${f.line}]: ${f.message}"
      }.mkString("\n")

  private def _finding_json(f: Finding): String =
    s"""{"level":"${f.level.name}","category":"${_json(f.category)}","code":"${_json(f.code)}","message":"${_json(f.message)}","path":"${_json(f.path.toString)}","line":${f.line}}"""

  private def _json(p: String): String =
    p.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' => "\\u%04x".format(c.toInt)
      case c => c.toString
    }

  private final case class Config(path: Path, baseline: Option[Path], format: String, strict: Boolean, noabi: Boolean)
  private object Config {
    def create(args: List[String]): Config = {
      var format = "text"
      var strict = false
      var noabi = false
      var baseline: Option[Path] = None
      val paths = Vector.newBuilder[String]
      @annotation.tailrec
      def _go_(xs: List[String]): Unit = xs match {
        case Nil =>
          ()
        case "--baseline" :: value :: rest =>
          baseline = Some(Paths.get(value).toAbsolutePath.normalize())
          _go_(rest)
        case x :: rest if x.startsWith("--baseline=") =>
          baseline = Some(Paths.get(x.substring("--baseline=".length)).toAbsolutePath.normalize())
          _go_(rest)
        case "--format" :: value :: rest =>
          format = value
          _go_(rest)
        case x :: rest if x.startsWith("--format=") =>
          format = x.substring("--format=".length)
          _go_(rest)
        case "--strict" :: rest =>
          strict = true
          _go_(rest)
        case "--no-abi" :: rest =>
          noabi = true
          _go_(rest)
        case x :: _ if x.startsWith("-") =>
          RAISE.invalidArgumentFault(s"Unsupported lint option: ${x}")
        case x :: rest =>
          paths += x
          _go_(rest)
      }
      _go_(args)
      val values = paths.result()
      if (values.size != 1)
        RAISE.invalidArgumentFault("Usage: cozy lint car <project-root> [--baseline <car|manifest>] [--format text|json] [--strict] [--no-abi]")
      if (format != "text" && format != "json")
        RAISE.invalidArgumentFault(s"Unsupported lint format: ${format}")
      Config(Paths.get(values.head), baseline, format, strict, noabi)
    }
  }
}
