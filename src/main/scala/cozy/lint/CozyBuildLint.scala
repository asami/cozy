package cozy.lint

import org.goldenport.RAISE
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Jul.  6, 2026
 * @version Jul. 10, 2026
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
  private[cozy] final case class DependencyDeclaration(
    path: Path,
    line: Int,
    group: String,
    artifact: String,
    version: String
  )
  private[cozy] trait PublicArtifactAvailability {
    def exists(dependency: DependencyDeclaration): Option[Boolean]
  }

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
    lint(path, _latest_sbt_cozy_version(), SimpleModelingPublicArtifactAvailability)

  private[cozy] def lint(path: Path, latestversion: Option[String]): Vector[Finding] = {
    lint(path, latestversion, SimpleModelingPublicArtifactAvailability)
  }

  private[cozy] def lint(
    path: Path,
    latestversion: Option[String],
    publicartifacts: PublicArtifactAvailability
  ): Vector[Finding] = {
    _lint(path, latestversion, publicartifacts, includeabi = true)
  }

  private[cozy] def lintBuildOnly(
    path: Path,
    latestversion: Option[String],
    publicartifacts: PublicArtifactAvailability
  ): Vector[Finding] =
    _lint(path, latestversion, publicartifacts, includeabi = false)

  private[cozy] def lintBuildOnly(path: Path): Vector[Finding] =
    lintBuildOnly(path, _latest_sbt_cozy_version(), SimpleModelingPublicArtifactAvailability)

  private def _lint(
    path: Path,
    latestversion: Option[String],
    publicartifacts: PublicArtifactAvailability,
    includeabi: Boolean
  ): Vector[Finding] = {
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
    val pluginfindings = if (declarations.isEmpty)
      Vector(Finding(
        Level.Warn,
        "build.sbt-cozy-plugin",
        "Could not find addSbtPlugin for org.goldenport sbt-cozy in project/plugins.sbt or build.sbt.",
        root.resolve("project").resolve("plugins.sbt"),
        1
      ))
    else
      declarations.flatMap(_version_findings(_, latestversion))
    val basefindings = pluginfindings ++ _public_dependency_findings(root, publicartifacts)
    if (includeabi)
      basefindings ++ _abi_findings(root)
    else
      basefindings
  }

  private def _abi_findings(root: Path): Vector[Finding] =
    CozyCarAbiLint.lintBuildProject(root).map { f =>
      Finding(_level(f.level), f.code, f.message, f.path, f.line)
    }

  private def _level(level: CozyCarAbiLint.Level): Level =
    level match {
      case CozyCarAbiLint.Level.Ok => Level.Ok
      case CozyCarAbiLint.Level.Fail => Level.Fail
      case CozyCarAbiLint.Level.Warn => Level.Warn
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
    }.toMap ++ _multiline_string_variables(lines)
  }

  private def _multiline_string_variables(lines: Vector[String]): Map[String, String] = {
    val sysfallback = """\b(?:val|lazy\s+val)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*sys\.props\.getOrElse\([^"]*"[^"]+"\s*,\s*sys\.env\.getOrElse\([^"]*"[^"]+"\s*,\s*"([^"]+)""".r
    _logical_lines(lines).flatMap { line =>
      sysfallback.findFirstMatchIn(line).map(x => x.group(1) -> x.group(2))
    }.toMap
  }

  private def _public_dependency_findings(root: Path, publicartifacts: PublicArtifactAvailability): Vector[Finding] = {
    val build = root.resolve("build.sbt")
    _dependency_declarations(build).flatMap { dependency =>
      publicartifacts.exists(dependency) match {
        case Some(true) =>
          Vector(Finding(
            Level.Ok,
            "build.public-dependency",
            s"${dependency.group}:${dependency.artifact}:${dependency.version} is available in the public SimpleModeling Maven repository.",
            dependency.path,
            dependency.line
          ))
        case Some(false) =>
          Vector(Finding(
            Level.Warn,
            "build.public-dependency",
            s"${dependency.group}:${dependency.artifact}:${dependency.version} is not available in the public SimpleModeling Maven repository; publish it before publishing this project.",
            dependency.path,
            dependency.line
          ))
        case None =>
          Vector(Finding(
            Level.Warn,
            "build.public-dependency",
            s"Could not verify ${dependency.group}:${dependency.artifact}:${dependency.version} in the public SimpleModeling Maven repository.",
            dependency.path,
            dependency.line
          ))
      }
    }
  }

  private def _dependency_declarations(path: Path): Vector[DependencyDeclaration] =
    if (!Files.isRegularFile(path))
      Vector.empty
    else {
      val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
      val variables = _string_variables(lines)
      val scalabinaryversion = _scala_binary_version(lines).getOrElse("2.12")
      lines.zipWithIndex.flatMap {
        case (line, i) =>
          val n = i + 1
          _dependency_declaration(path, n, line, variables, scalabinaryversion)
      }.filter(x => _is_public_simplemodeling_repository_group(x.group)).
        filterNot(_is_snapshot_dependency).
        groupBy(x => (x.group, x.artifact, x.version)).
        values.
        map(_.maxBy(_.line)).
        toVector.
        sortBy(x => (x.path.toString, x.line, x.group, x.artifact, x.version))
    }

  private def _is_snapshot_dependency(dependency: DependencyDeclaration): Boolean =
    dependency.version.endsWith("-SNAPSHOT")

  private def _dependency_declaration(
    path: Path,
    line: Int,
    text: String,
    variables: Map[String, String],
    scalabinaryversion: String
  ): Option[DependencyDeclaration] = {
    val active = _strip_line_comment(text)
    if (active.trim.isEmpty) {
      None
    } else {
      val cross = """"([^"]+)"\s*%%\s*"([^"]+)"\s*%\s*("[^"]+"|[A-Za-z_][A-Za-z0-9_]*)""".r
      val direct = """"([^"]+)"\s*%\s*"([^"]+)"\s*%\s*("[^"]+"|[A-Za-z_][A-Za-z0-9_]*)""".r
      cross.findFirstMatchIn(active).flatMap { m =>
        _dependency_version(m.group(3), variables).map { version =>
          DependencyDeclaration(path, line, m.group(1), s"${m.group(2)}_${scalabinaryversion}", version)
        }
      }.orElse {
        direct.findFirstMatchIn(active).flatMap { m =>
          _dependency_version(m.group(3), variables).map { version =>
            DependencyDeclaration(path, line, m.group(1), m.group(2), version)
          }
        }
      }
    }
  }

  private def _strip_line_comment(text: String): String = {
    val builder = new StringBuilder
    var instring = false
    var escaped = false
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (!instring && c == '/' && i + 1 < text.length && text.charAt(i + 1) == '/')
        return builder.toString
      builder.append(c)
      if (escaped)
        escaped = false
      else if (c == '\\')
        escaped = true
      else if (c == '"')
        instring = !instring
      i += 1
    }
    builder.toString
  }

  private def _is_public_simplemodeling_repository_group(group: String): Boolean =
    group == "org.simplemodeling" ||
    group == "org.goldenport" ||
    group == "org.smartdox"

  private def _dependency_version(token: String, variables: Map[String, String]): Option[String] =
    if (token.startsWith("\"") && token.endsWith("\""))
      Some(token.substring(1, token.length - 1))
    else
      variables.get(token)

  private def _scala_binary_version(lines: Vector[String]): Option[String] = {
    val pattern = """scalaVersion\s*:=\s*"([0-9]+)\.([0-9]+)\.[^"]+"""".r
    _logical_lines(lines).flatMap { line =>
      pattern.findFirstMatchIn(line).map(x => s"${x.group(1)}.${x.group(2)}")
    }.headOption
  }

  private def _logical_lines(lines: Vector[String]): Vector[String] =
    _logical_lines_with_line_numbers(lines).map(_._1)

  private def _logical_lines_with_line_numbers(lines: Vector[String]): Vector[(String, Int)] =
    lines.indices.toVector.flatMap { i =>
      val one = lines(i)
      val two = if (i + 1 < lines.length) s"${lines(i)} ${lines(i + 1)}" else one
      val three = if (i + 2 < lines.length) s"${lines(i)} ${lines(i + 1)} ${lines(i + 2)}" else two
      Vector(one, two, three).distinct.map(_ -> (i + 1))
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

  private object SimpleModelingPublicArtifactAvailability extends PublicArtifactAvailability {
    def exists(dependency: DependencyDeclaration): Option[Boolean] = {
      val group = dependency.group.replace('.', '/')
      val base = s"https://www.simplemodeling.org/repository/maven/${group}/${dependency.artifact}/${dependency.version}"
      val pom = s"${base}/${dependency.artifact}-${dependency.version}.pom"
      val jar = s"${base}/${dependency.artifact}-${dependency.version}.jar"
      for {
        pomexists <- _http_exists(pom)
        jarexists <- _http_exists(jar)
      } yield pomexists && jarexists
    }

    private def _http_exists(url: String): Option[Boolean] =
      try {
        val connection = URI.create(url).toURL.openConnection().asInstanceOf[HttpURLConnection]
        connection.setRequestMethod("HEAD")
        connection.setConnectTimeout(3000)
        connection.setReadTimeout(3000)
        val code = connection.getResponseCode
        Some(code >= 200 && code < 300)
      } catch {
        case NonFatal(_) => None
      }
  }
}
