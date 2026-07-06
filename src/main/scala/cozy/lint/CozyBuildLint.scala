package cozy.lint

import org.goldenport.RAISE
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Jul.  6, 2026
 * @version Jul.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyBuildLint {
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
    code: String,
    message: String,
    path: Path,
    line: Int
  )

  private final case class PluginDeclaration(path: Path, line: Int, version: String)

  def execute(args: List[String]): Int = {
    val config = Config.create(args)
    val findings = lint(config.path)
    _render(config, findings)
  }

  private[cozy] def execute(args: List[String], latestVersion: Option[String]): Int = {
    val config = Config.create(args)
    val findings = lint(config.path, latestVersion)
    _render(config, findings)
  }

  private def _render(config: Config, findings: Vector[Finding]): Int = {
    config.format match {
      case "json" => println(toJson(findings))
      case "text" => println(toText(config.path, findings))
      case other => RAISE.invalidArgumentFault(s"Unsupported lint format: ${other}")
    }
    if (findings.exists(_.level == Level.Fail) || (config.strict && findings.exists(_.level == Level.Warn))) 1 else 0
  }

  private[cozy] def lint(path: Path): Vector[Finding] =
    lint(path, _latest_sbt_cozy_version())

  private[cozy] def lint(path: Path, latestVersion: Option[String]): Vector[Finding] = {
    val project = path.toAbsolutePath.normalize()
    val root =
      if (Files.isDirectory(project))
        project
      else
        project.getParent
    val candidates = Vector(
      root.resolve("project").resolve("plugins.sbt"),
      root.resolve("build.sbt")
    )
    val declarations = candidates.flatMap(_plugin_declarations)
    if (declarations.isEmpty)
      Vector(Finding(
        Level.Warn,
        "build.sbt-cozy-plugin",
        "Could not find addSbtPlugin for org.goldenport sbt-cozy in project/plugins.sbt or build.sbt.",
        root.resolve("project").resolve("plugins.sbt"),
        1
      ))
    else
      declarations.flatMap(_version_findings(_, latestVersion))
  }

  private[cozy] def toJson(findings: Seq[Finding]): String =
    s"""{"findings":[${findings.map(_finding_json).mkString(",")}]}"""

  private[cozy] def toText(path: Path, findings: Seq[Finding]): String =
    if (findings.isEmpty)
      s"OK build.no-findings [${path.toAbsolutePath.normalize()}]: No build lint findings."
    else
      findings.map { f =>
        s"${f.level.name} ${f.code} [${f.path}:${f.line}]: ${f.message}"
      }.mkString("\n")

  private def _version_findings(declaration: PluginDeclaration, latest: Option[String]): Vector[Finding] =
    if (declaration.version.endsWith("-SNAPSHOT"))
      Vector(Finding(
        Level.Warn,
        "build.sbt-cozy-latest",
        s"sbt-cozy uses SNAPSHOT version '${declaration.version}'; use the latest published sbt-cozy unless this is an explicit development test.",
        declaration.path,
        declaration.line
      ))
    else latest match {
      case None =>
        Vector(Finding(
          Level.Warn,
          "build.sbt-cozy-latest",
          s"sbt-cozy version '${declaration.version}' is declared, but latest published version could not be checked.",
          declaration.path,
          declaration.line
        ))
      case Some(x) if declaration.version == x =>
        Vector(Finding(
          Level.Ok,
          "build.sbt-cozy-latest",
          s"sbt-cozy uses latest published version ${declaration.version}.",
          declaration.path,
          declaration.line
        ))
      case Some(x) if _version_key(declaration.version) < _version_key(x) =>
        Vector(Finding(
          Level.Warn,
          "build.sbt-cozy-latest",
          s"sbt-cozy version '${declaration.version}' is older than latest published version '${x}'.",
          declaration.path,
          declaration.line
        ))
      case Some(x) =>
        Vector(Finding(
          Level.Warn,
          "build.sbt-cozy-latest",
          s"sbt-cozy version '${declaration.version}' is newer than published metadata latest '${x}'; confirm this is intentional.",
          declaration.path,
          declaration.line
        ))
    }

  private def _plugin_declarations(path: Path): Vector[PluginDeclaration] =
    if (!Files.isRegularFile(path))
      Vector.empty
    else {
      val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
      val variables = _string_variables(lines)
      lines.zipWithIndex.flatMap {
        case (line, i) =>
          _direct_plugin_version(line).
            orElse(_variable_plugin_version(line, variables)).
            map(PluginDeclaration(path, i + 1, _))
      }
    }

  private def _direct_plugin_version(line: String): Option[String] = {
    val patterns = Vector(
      """addSbtPlugin\(\s*"org\.goldenport"\s*%\s*"sbt-cozy"\s*%\s*"([^"]+)""".r,
      """addSbtPlugin\(\s*"org\.goldenport"\s*%\s*"sbt-cozy_[^"]+"\s*%\s*"([^"]+)""".r
    )
    patterns.view.flatMap(_.findFirstMatchIn(line).map(_.group(1))).headOption
  }

  private def _variable_plugin_version(line: String, variables: Map[String, String]): Option[String] = {
    val patterns = Vector(
      """addSbtPlugin\(\s*"org\.goldenport"\s*%\s*"sbt-cozy"\s*%\s*([A-Za-z_][A-Za-z0-9_]*)""".r,
      """addSbtPlugin\(\s*"org\.goldenport"\s*%\s*"sbt-cozy_[^"]+"\s*%\s*([A-Za-z_][A-Za-z0-9_]*)""".r
    )
    patterns.view.flatMap(_.findFirstMatchIn(line).flatMap(x => variables.get(x.group(1)))).headOption
  }

  private def _string_variables(lines: Vector[String]): Map[String, String] = {
    val literal = """\b(?:val|lazy\s+val)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"]+)""".r
    val sysfallback = """\b(?:val|lazy\s+val)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*sys\.props\.getOrElse\([^"]*"[^"]+"\s*,\s*sys\.env\.getOrElse\([^"]*"[^"]+"\s*,\s*"([^"]+)""".r
    lines.flatMap { line =>
      literal.findFirstMatchIn(line).
        orElse(sysfallback.findFirstMatchIn(line)).
        map(x => x.group(1) -> x.group(2))
    }.toMap
  }

  private def _latest_sbt_cozy_version(): Option[String] = {
    val versions = _metadata_versions() ++ _local_sbt_cozy_versions()
    val releases = versions.filterNot(_.endsWith("-SNAPSHOT"))
    if (releases.isEmpty)
      None
    else
      Some(releases.distinct.sortBy(_version_key).last)
  }

  private def _metadata_versions(): Vector[String] = {
    val urls = Vector(
      "https://www.simplemodeling.org/repository/maven/org/goldenport/sbt-cozy_2.12_1.0/maven-metadata.xml",
      "https://repo1.maven.org/maven2/org/goldenport/sbt-cozy_2.12_1.0/maven-metadata.xml"
    )
    urls.flatMap { url =>
      try {
        val connection = URI.create(url).toURL.openConnection()
        connection.setConnectTimeout(3000)
        connection.setReadTimeout(3000)
        val source = scala.io.Source.fromInputStream(connection.getInputStream, "UTF-8")
        try {
          _metadata_versions(source.mkString)
        } finally {
          source.close()
        }
      } catch {
        case NonFatal(_) => Vector.empty
      }
    }
  }

  private def _metadata_versions(xml: String): Vector[String] = {
    val release = """<release>\s*([^<]+)\s*</release>""".r.findAllMatchIn(xml).map(_.group(1).trim).toVector
    val latest = """<latest>\s*([^<]+)\s*</latest>""".r.findAllMatchIn(xml).map(_.group(1).trim).toVector
    val versions = """<version>\s*([^<]+)\s*</version>""".r.findAllMatchIn(xml).map(_.group(1).trim).toVector
    release ++ latest ++ versions
  }

  private def _local_sbt_cozy_versions(): Vector[String] = {
    val roots = Vector(
      Paths.get("/Users/asami/src/maven-repository/repository/maven/org/goldenport/sbt-cozy_2.12_1.0"),
      Paths.get("/Users/asami/src/maven-repository/maven/org/goldenport/sbt-cozy_2.12_1.0")
    )
    roots.flatMap { root =>
      if (Files.isDirectory(root)) {
        val stream = Files.list(root)
        try {
          stream.iterator.asScala.filter(Files.isDirectory(_)).map(_.getFileName.toString).toVector
        } finally {
          stream.close()
        }
      } else {
        Vector.empty
      }
    }
  }

  private def _version_key(version: String): String =
    version.split("[.-]").toVector.map { token =>
      if (token.forall(_.isDigit))
        "0" + f"${token.toInt}%09d"
      else
        "1" + token
    }.mkString(".")

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

  private final case class Config(path: Path, format: String, strict: Boolean)
  private object Config {
    def create(args: List[String]): Config = {
      var format = "text"
      var strict = false
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
        case "--strict" :: rest =>
          strict = true
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
        RAISE.invalidArgumentFault("Usage: cozy lint build <path> [--format text|json] [--strict]")
      if (format != "text" && format != "json")
        RAISE.invalidArgumentFault(s"Unsupported lint format: ${format}")
      Config(Paths.get(values.head), format, strict)
    }
  }
}
