package cozy.lint

import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal
import cozy.archive.ComponentRepositoryIndex
import org.goldenport.RAISE

/*
 * Local, network-free validation for a published Component Repository index.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyRepositoryLint {
  sealed trait Level {
    def name: String
  }
  object Level {
    case object Fail extends Level {
      val name = "FAIL"
    }
  }

  final case class Finding(level: Level, code: String, message: String, path: Path, line: Int)

  def execute(args: List[String]): Int = {
    val config = Config.create(args)
    val findings = lint(config.path)
    config.format match {
      case "json" => println(toJson(findings))
      case "text" => println(toText(config.path, findings))
      case other => RAISE.invalidArgumentFault(s"Unsupported lint format: $other")
    }
    if (findings.nonEmpty) 1 else 0
  }

  private[cozy] def lint(path: Path): Vector[Finding] = {
    val indexpath = indexPath(path)
    if (!Files.isRegularFile(indexpath))
      Vector(Finding(Level.Fail, "repository.index.missing", "Component Repository index is missing.", indexpath, 1))
    else
      try {
        ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath)
        Vector.empty
      } catch {
        case NonFatal(error) =>
          Vector(Finding(
            Level.Fail,
            "repository.index.invalid",
            Option(error.getMessage).getOrElse(error.getClass.getSimpleName),
            indexpath,
            1
          ))
      }
  }

  private[cozy] def indexPath(path: Path): Path = {
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.getFileName.toString == "index.json")
      normalized
    else if (normalized.getFileName.toString == "repository")
      normalized.resolve("catalog/index.json")
    else
      normalized.resolve("repository/catalog/index.json")
  }

  private[cozy] def toText(path: Path, findings: Seq[Finding]): String =
    if (findings.isEmpty)
      s"OK repository.no-findings [${indexPath(path)}]: No Component Repository index findings."
    else
      findings.map(f => s"${f.level.name} repository ${f.code} [${f.path}:${f.line}]: ${f.message}").mkString("\n")

  private[cozy] def toJson(findings: Seq[Finding]): String =
    s"""{"findings":[${findings.map(_finding_json).mkString(",") }]}"""

  private def _finding_json(finding: Finding): String =
    s"""{"level":"${finding.level.name}","category":"repository","code":"${_json(finding.code)}","message":"${_json(finding.message)}","path":"${_json(finding.path.toString)}","line":${finding.line}}"""

  private def _json(value: String): String =
    value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' => "\\u%04x".format(c.toInt)
      case c => c.toString
    }

  private final case class Config(path: Path, format: String)
  private object Config {
    def create(args: List[String]): Config = {
      var format = "text"
      val paths = Vector.newBuilder[String]
      @annotation.tailrec
      def go(xs: List[String]): Unit = xs match {
        case Nil => ()
        case "--format" :: value :: rest =>
          format = value
          go(rest)
        case value :: rest if value.startsWith("--format=") =>
          format = value.substring("--format=".length)
          go(rest)
        case value :: _ if value.startsWith("-") =>
          RAISE.invalidArgumentFault(s"Unsupported lint option: $value")
        case value :: rest =>
          paths += value
          go(rest)
      }
      go(args)
      val values = paths.result()
      if (values.size != 1)
        RAISE.invalidArgumentFault("Usage: cozy lint repository <repository-root> [--format text|json]")
      if (format != "text" && format != "json")
        RAISE.invalidArgumentFault(s"Unsupported lint format: $format")
      Config(Paths.get(values.head), format)
    }
  }
}
