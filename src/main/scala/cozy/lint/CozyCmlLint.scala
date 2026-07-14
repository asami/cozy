package cozy.lint

import org.goldenport.RAISE
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import cozy.modeler.CmlModelInspection

/*
 * @since   Jul.  6, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCmlLint {
  sealed trait Level {
    def name: String
  }
  object Level {
    case object Fail extends Level {
      val name = "FAIL"
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

  def execute(args: List[String]): Int = {
    val config = Config.create(args)
    val findings = lint(config.path)
    config.format match {
      case "json" => println(toJson(findings))
      case "text" => println(toText(config.path, findings))
      case other => RAISE.invalidArgumentFault(s"Unsupported lint format: ${other}")
    }
    if (findings.exists(_.level == Level.Fail)) 1 else 0
  }

  private[cozy] def lint(path: Path): Vector[Finding] =
    lintFiles(_cml_files(path.toAbsolutePath.normalize()))

  private[cozy] def lintFiles(paths: Seq[Path]): Vector[Finding] = {
    val attrs = paths.toVector.flatMap { path =>
      CmlModelInspection.load(path).attributes.map(path -> _)
    }
    val entityfindings = attrs.collect {
      case (path, a) if a.kind == CmlModelInspection.DeclarationKind.Entity && a.isRawString =>
        Finding(
          Level.Fail,
          "cml.domain.string-attribute",
          s"${a.owner}.${a.name} uses raw string; define a dedicated value type or an existing semantic datatype.",
          path,
          a.line
        )
    }
    val valuefindings = attrs.
      filter(_._2.kind == CmlModelInspection.DeclarationKind.Value).
      groupBy { case (path, attribute) => (path, attribute.owner) }.
      toVector.
      sortBy { case ((path, owner), _) => (path.toString, owner) }.
      flatMap {
        case (_, xs) if _is_single_string_value(xs.map(_._2)) => Vector.empty
        case (_, xs) =>
          xs.filter(_._2.isRawString).map { case (path, a) =>
            Finding(
              Level.Warn,
              "cml.value.string-attribute",
              s"${a.owner}.${a.name} uses raw string; prefer an explicit value type unless this is the single internal value representation.",
              path,
              a.line
            )
          }
      }
    (entityfindings ++ valuefindings).sortBy(x => (x.path.toString, x.line, x.code))
  }

  private[cozy] def toJson(findings: Seq[Finding]): String =
    s"""{"findings":[${findings.map(_finding_json).mkString(",")}]}"""

  private[cozy] def toText(path: Path, findings: Seq[Finding]): String =
    if (findings.isEmpty)
      s"OK cml.no-findings [${path.toAbsolutePath.normalize()}]: No CML lint findings."
    else
      findings.map { f =>
        s"${f.level.name} ${f.code} [${f.path}:${f.line}]: ${f.message}"
      }.mkString("\n")

  private def _finding_json(f: Finding): String =
    s"""{"level":"${f.level.name}","code":"${_json(f.code)}","message":"${_json(f.message)}","path":"${_json(f.path.toString)}","line":${f.line}}"""

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

  private def _cml_files(path: Path): Vector[Path] =
    if (Files.isRegularFile(path))
      Vector(path)
    else if (Files.isDirectory(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.
          filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".cml")).
          toVector.
          sortBy(_.toString)
      } finally {
        stream.close()
      }
    } else {
      RAISE.invalidArgumentFault(s"CML path not found: ${path}")
    }

  private def _is_single_string_value(xs: Seq[CmlModelInspection.Attribute]): Boolean =
    xs.length == 1 && xs.head.name == "value" && xs.head.isRawString

  private final case class Config(path: Path, format: String)
  private object Config {
    def create(args: List[String]): Config = {
      var format = "text"
      val paths = Vector.newBuilder[String]
      @annotation.tailrec
      def go(xs: List[String]): Unit = xs match {
        case Nil =>
          ()
        case "--format" :: value :: rest =>
          format = value
          go(rest)
        case x :: rest if x.startsWith("--format=") =>
          format = x.substring("--format=".length)
          go(rest)
        case x :: _ if x.startsWith("-") =>
          RAISE.invalidArgumentFault(s"Unsupported lint option: ${x}")
        case x :: rest =>
          paths += x
          go(rest)
      }
      go(args)
      val values = paths.result()
      if (values.size != 1)
        RAISE.invalidArgumentFault("Usage: cozy lint cml <path> [--format text|json]")
      if (format != "text" && format != "json")
        RAISE.invalidArgumentFault(s"Unsupported lint format: ${format}")
      Config(Paths.get(values.head), format)
    }
  }
}
