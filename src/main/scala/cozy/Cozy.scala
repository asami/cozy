package cozy

import org.goldenport.RAISE
import org.goldenport.i18n.I18NString
import org.goldenport.cli.{Config => CliConfig, _}
import org.goldenport.value._
import org.goldenport.parser.CommandParser
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.kaleidox.http.HttpHandle
import org.smartdox.service.operations.{
  HtmlOperationClass,
  PdfOperationClass,
  SiteOperationClass
}
import play.api.libs.json._
import cozy.web.jetty.JettyServer
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Try
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._

/*
 * @since   Dec.  4, 2021
 *  version Dec. 19, 2021
 *  version Jan.  1, 2022
 *  version Feb. 28, 2022
 *  version Aug. 20, 2025
 *  version Mar. 17, 2026
 *  version Apr.  8, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
class Cozy(
  val config: Config,
  val environment: Environment,
  services: Services,
  operations: Operations
) {
  private val _engine = Engine.standard(services, operations)

  def execute(args: Array[String]) = _engine.apply(environment, args)

  def run(args: Array[String]) {
    val call = _operation_call(args)
    if (call.request.arguments.isEmpty || call.request.isInteractive)
      repl(call)
    else if (_is_cli_command(call))
      execute(args)
    else
      executeDirect(args)
  }

  def repl(call: OperationCall) {
    val kal = interpreter
    kal.repl(call)
  }

  def interpreter = environment.appEnvironment match {
    case m: Context => m.kaleidox
    case _ => createInterpreter()
  }

  def createInterpreter(): Kaleidox = {
    val kconfig = org.goldenport.kaleidox.Config.create(environment).
      setModeler(new modeler.Modeler()).
      setPrompt("cozy> ")
    new Kaleidox(kconfig, environment)
  }

  def createHttpHandle(): HttpHandle = {
    val args = Array[String]()
    val kal = interpreter
    val call = _operation_call(args)
    kal.http(call)
  }

  def executeDirect(args: Array[String]): Unit = {
    if (!_execute_car_sbt_project(args) && !_execute_sbt_bridge(args) && !_execute_package_archive(args))
      _to_repl_commandline(args) match {
        case Some(s) =>
          val c = _operation_call(Array(s))
          interpreter.execute(c)
        case None =>
          execute(args)
      }
  }

  private def _is_cli_command(call: OperationCall): Boolean =
    call.argumentsAsString.headOption.fold(false)(_is_cli_command)

  private def _is_cli_command(name: String): Boolean =
    _engine.commandParser(name) match {
      case _: CommandParser.Found[_] => true
      case _: CommandParser.Candidates[_] => true
      case _: CommandParser.NotFound[_] => false
    }

  private def _operation_call(args: Array[String]): OperationCall = {
    val req = spec.Request.empty
    val res = spec.Response()
    val op = spec.Operation("cozy", req, res)
    val request = Request.create(op, args)
    OperationCall(environment, op, request, Response())
  }

  private def _to_repl_commandline(args: Array[String]): Option[String] =
    _leading_command(args).map { case (command, rest) =>
      val converted = _convert_args(rest)
      (Vector(command) ++ converted).mkString(" ")
    }

  private def _execute_car_sbt_project(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("car-sbt-project", rest)) =>
        val save = _save_path(rest).getOrElse {
          RAISE.invalidArgumentFault("Missing --save for car-sbt-project")
        }
        val repl = (Vector("modeler-scala") ++ _convert_args(rest)).mkString(" ")
        val c = _operation_call(Array(repl))
        interpreter.execute(c)
        _materialize_car_sbt_project(save)
        true
      case _ =>
        false
    }

  private def _execute_package_archive(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("package-car", rest)) =>
        CozyArchivePackager.buildCar(rest)
        true
      case Some(("package-sar", rest)) =>
        CozyArchivePackager.buildSar(rest)
        true
      case _ =>
        false
    }

  private def _execute_sbt_bridge(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("sbt-bridge", rest)) =>
        CozySbtBridge.execute(rest)
        true
      case _ =>
        false
    }

  private def _leading_command(args: Array[String]): Option[(String, List[String])] = {
    val i = args.indexWhere(x => !x.startsWith("-"))
    if (i >= 0)
      Some((args(i), args.drop(i + 1).toList))
    else
      None
  }

  private def _convert_args(args: List[String]): Vector[String] = {
    @annotation.tailrec
    def go(xs: List[String], z: Vector[String]): Vector[String] = xs match {
      case Nil => z
      case x :: xx if x.startsWith(":") =>
        go(xx, z :+ x)
      case x :: xx if x.startsWith("--") =>
        _split_option(x.drop(2)) match {
          case Some((name, value)) =>
            go(xx, z :+ s":$name" :+ _quote(value))
          case None =>
            xx match {
              case y :: yy if !y.startsWith("-") =>
                go(yy, z :+ s":${x.drop(2)}" :+ _quote(y))
              case _ =>
                go(xx, z :+ s":${x.drop(2)}")
            }
        }
      case x :: xx =>
        go(xx, z :+ _quote(x))
    }
    go(args, Vector.empty)
  }

  private def _split_option(p: String): Option[(String, String)] = {
    val i = p.indexOf('=')
    if (i >= 0)
      Some((p.substring(0, i), p.substring(i + 1)))
    else
      None
  }

  private def _quote(p: String): String = {
    val escaped = p.
      replace("\\", "\\\\").
      replace("\"", "\\\"")
    s""""$escaped""""
  }

  private def _save_path(args: List[String]): Option[Path] =
    Cozy._save_path(args)

  private def _materialize_car_sbt_project(dir: Path): Unit = {
    Files.createDirectories(dir)
    Files.writeString(
      dir.resolve("build.sbt"),
      Cozy.carBuildSbt(),
      StandardCharsets.UTF_8
    )
    val projectdir = dir.resolve("project")
    Files.createDirectories(projectdir)
    Files.writeString(
      projectdir.resolve("build.properties"),
      s"sbt.version=${Cozy.detectSbtVersion()}",
      StandardCharsets.UTF_8
    )
    Files.writeString(
      projectdir.resolve("plugins.sbt"),
      Cozy.carPluginsSbt(),
      StandardCharsets.UTF_8
    )
  }
}

object Cozy {
  private val DefaultSbtVersion = "1.9.7"
  private val DefaultSbtCozyVersion = "0.1.2"

  private[cozy] def detectSbtVersion(): String = {
    val path = Paths.get("project/build.properties")
    if (Files.exists(path))
      Try(Files.readAllLines(path, StandardCharsets.UTF_8)).
        toOption.
        flatMap(_.toArray.collectFirst {
          case s: String if s.startsWith("sbt.version=") => s.substring("sbt.version=".length).trim
        }).
        filter(_.nonEmpty).
        getOrElse(DefaultSbtVersion)
    else
      DefaultSbtVersion
  }

  private[cozy] def carBuildSbt(): String =
    """import org.goldenport.cozy.CozyPlugin.autoImport._
      |import sbt.Keys.*
      |
      |val scala3Version = "3.3.7"
      |
      |lazy val packageCar = taskKey[File]("Create versioned CAR archive.")
      |
      |lazy val root = project
      |  .in(file("."))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(
      |    organization := "com.example",
      |    name := "sample",
      |    version := "0.0.1-SNAPSHOT",
      |
      |    scalaVersion := scala3Version,
      |
      |    resolvers += Resolver.defaultLocal,
      |    resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
      |    resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
      |    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/maven",
      |
      |    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
      |    libraryDependencies += "org.typelevel" %% "cats-core" % "2.7.0",
      |    libraryDependencies += "org.typelevel" %% "cats-kernel-laws" % "2.7.0",
      |    libraryDependencies += "org.typelevel" %% "cats-free" % "2.7.0",
      |    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.3.0",
      |    libraryDependencies += "org.typelevel" %% "kittens" % "3.5.0",
      |    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % "test",
      |    libraryDependencies += "org.typelevel" %% "cats-testkit" % "2.7.0" % "test",
      |    libraryDependencies += "org.typelevel" %% "discipline-core" % "1.3.0" % "test",
      |    libraryDependencies += "org.typelevel" %% "discipline-scalatest" % "2.1.5" % "test",
      |    libraryDependencies += "org.typelevel" %% "spire" % "0.18.0",
      |    libraryDependencies += "io.circe" %% "circe-core" % "0.14.3",
      |    libraryDependencies += "io.circe" %% "circe-generic" % "0.14.3",
      |    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.3",
      |    libraryDependencies += "org.goldenport" %% "goldenport-cncf" % "0.4.2-SNAPSHOT",
      |    libraryDependencies += "org.simplemodeling" %% "simplemodeling-model" % "0.1.2-SNAPSHOT",
      |    libraryDependencies += "org.goldenport" % "cncf-collaborator-api" % "0.1.0-SNAPSHOT",
      |
      |    dependencyOverrides ++= Seq(
      |      "org.goldenport" % "cncf-collaborator-api" % "0.1.0-SNAPSHOT",
      |      "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
      |      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"
      |    ),
      |
      |    cozyGeneratorBackend := "cozy",
      |    cozyDelegateProjectDir := None,
      |    cozyDelegateCommand := Seq("cozy"),
      |    cozyManifestMetadata ++= Map(
      |      "component" -> "sample-component",
      |      "boundedContext" -> "default",
      |      "domain" -> "default"
      |    ),
      |
      |    packageCar := {
      |      val out = target.value / "car" / s"${name.value}-${version.value}.car"
      |      val sourcedir = baseDirectory.value / "car.d"
      |      val pairs =
      |        if (sourcedir.exists())
      |          sbt.Path.allSubpaths(sourcedir).toSeq
      |        else
      |          Seq.empty
      |      IO.createDirectory(out.getParentFile)
      |      IO.zip(pairs, out)
      |      streams.value.log.info(s"CAR archive: ${out.getAbsolutePath}")
      |      out
      |    },
      |
      |    Compile / sourceGenerators += Def.task {
      |      val out = (Compile / sourceManaged).value / "domain" / "meta" / "BuildVersion.scala"
      |      val content =
      |        s"package domain.meta\\n\\nobject BuildVersion {\\n  val name: String = \\"${name.value}\\"\\n  val version: String = \\"${version.value}\\"\\n  val scalaVersion: String = \\"${scalaVersion.value}\\"\\n}\\n"
      |      IO.write(out, content)
      |      Seq(out)
      |    }.taskValue,
      |
      |    Compile / unmanagedSourceDirectories += (Compile / sourceManaged).value
      |  )
      |""".stripMargin

  private[cozy] def carPluginsSbt(): String =
    s"""resolvers += Resolver.defaultLocal
       |addSbtPlugin("org.goldenport" % "sbt-cozy" % "${DefaultSbtCozyVersion}")
       |""".stripMargin

  case object CozyServiceClass extends ServiceClass {
    val name = "cozy"
    val defaultOperation = Some(CozyOperationClass)
    val operations = Operations(
      CozyOperationClass,
      WebOperationClass,
      HtmlOperationClass,
      PdfOperationClass,
      SiteOperationClass
    )
  }

  case object CozyOperationClass extends OperationClassWithOperation {
    val request = spec.Request.empty
    val response = spec.Response.empty
    val specification = spec.Operation("cozy", request, response)
    
    def apply(env: Environment, req: Request): Response = {
      val ctx = env.toAppEnvironment[Context]
      val kaleidox = ctx.kaleidox
      val args = Array[String]() // TODO
      val req = spec.Request.empty
      val res = spec.Response()
      val op = spec.Operation("cozy", req, res)
      val call = OperationCall.create(op, args)
      kaleidox.repl(call)
      VoidResponse
    }
  }

  case object WebOperationClass extends OperationClassWithOperation {
    val request = spec.Request.empty
    val response = spec.Response.empty
    val specification = spec.Operation("web", request, response)
    
    def apply(env: Environment, req: Request): Response = {
      val ctx = env.toAppEnvironment[Context]
      JettyServer.run(ctx)
      VoidResponse
    }
  }

  def build(args: Array[String]): Cozy = {
    val env0 = Environment.create("cozy", args)
    val env1 = _apply_save_output_directory(env0, args)
    val config = Config.create(env1)
    val kaleidox = _create(env1)
    val context = new Context(env1, config, kaleidox)
    val env = env1.withAppEnvironment(context)
    val services = Services(
      CozyServiceClass,
      modeler.ModelerServiceClass
    )
    new Cozy(config, env, services, CozyServiceClass.operations)
  }

  private def _create(environment: Environment) = {
    val kconfig = org.goldenport.kaleidox.Config.create(environment).
      setModeler(new modeler.Modeler()).
      setPrompt("cozy> ")
    new Kaleidox(kconfig, environment)
  }

  def main(args: Array[String]) {
    val cozy = build(args)
    cozy.run(args)
  }

  private def _apply_save_output_directory(env: Environment, args: Array[String]): Environment =
    _save_path(args.toList).
      map(path => env.copy(config = env.config.copy(projectDirectory = Some(path.toFile)))).
      getOrElse(env)

  private def _save_path(args: List[String]): Option[Path] = {
    @annotation.tailrec
    def go(xs: List[String]): Option[Path] = xs match {
      case Nil => None
      case x :: xx if x.startsWith("--save=") =>
        Some(Paths.get(x.drop("--save=".length)).toAbsolutePath.normalize())
      case "--save" :: value :: _ =>
        Some(Paths.get(value).toAbsolutePath.normalize())
      case _ :: xx =>
        go(xx)
    }
    go(args)
  }
}

private object CozyArchivePackager {
  def buildCar(args: List[String]): Unit = {
    val save = _required_path(args, "save")
    val mainJar = _required_path(args, "main-jar")
    val libJars = _paths(args, "lib-jars")
    val spiJars = _paths(args, "spi-jars")
    val defaultConf = _path(args, "default-conf")
    val docsDir = _path(args, "docs-dir")
    val name = _required_value(args, "name")
    val version = _required_value(args, "version")
    val component = _required_value(args, "component")
    val extensionMap = _string_map(args, "extensions")
    val configMap = _string_map(args, "config")
    _write_archive(
      save,
      Vector(
        mainJar -> "component/main.jar"
      ) ++
        libJars.map(p => p -> s"lib/${p.getFileName}") ++
        spiJars.map(p => p -> s"spi/${p.getFileName}") ++
        defaultConf.toVector.map(_ -> "config/default.conf") ++
        _docs_entries(docsDir) ++
        Vector(_write_temp("component-descriptor", _component_descriptor_json(name, version, component, extensionMap, configMap)) -> "component-descriptor.json"),
      Vector("component", "lib", "spi", "config", "docs")
    )
  }

  def buildSar(args: List[String]): Unit = {
    val save = _required_path(args, "save")
    val sourceDir = _required_path(args, "source-dir")
    val sourceFiles = _values(args, "source-files")
    val extensionJars = _paths(args, "extension-jars")
    val applicationConf = _path(args, "application-conf")
    val subsystemSources = _archive_sources(sourceDir, sourceFiles)
    _write_archive(
      save,
      subsystemSources ++
        extensionJars.map(p => p -> s"extension/${p.getFileName}") ++
        applicationConf.toVector.map(_ -> "config/application.conf"),
      Vector("extension", "config")
    )
  }

  private def _archive_sources(sourceDir: Path, includes: Vector[String] = Vector.empty): Vector[(Path, String)] = {
    if (!Files.exists(sourceDir))
      Vector.empty
    else {
      val includeSet = includes.map(_.replace('\\', '/')).toSet
      val stream = Files.walk(sourceDir)
      try {
        stream.iterator().asScala.toVector.collect {
          case p if Files.isRegularFile(p) =>
            p -> sourceDir.relativize(p).toString.replace('\\', '/')
        }.filter { case (_, rel) =>
          includeSet.isEmpty || includeSet.contains(rel)
        }.sortBy(_._2)
      } finally {
        stream.close()
      }
    }
  }

  private def _docs_entries(docsDir: Option[Path]): Vector[(Path, String)] =
    docsDir.toVector.flatMap { dir =>
      _archive_sources(dir).map { case (p, rel) => p -> s"docs/${rel}" }
    }

  private def _write_archive(
    archive: Path,
    entries: Vector[(Path, String)],
    placeholderDirs: Vector[String]
  ): Unit = {
    Files.createDirectories(archive.getParent)
    Files.deleteIfExists(archive)
    val tempDir = Files.createTempDirectory("cozy-package-")
    try {
      entries.foreach { case (source, relative) =>
        val dest = tempDir.resolve(relative)
        Option(dest.getParent).foreach(Files.createDirectories(_))
        Files.copy(source, dest)
      }
      placeholderDirs.foreach { dir =>
        val target = tempDir.resolve(dir)
        if (!Files.exists(target) || _is_empty_dir(target))
          _write_text(target.resolve(".keep"), "")
      }
      _zip_dir(tempDir, archive)
    } finally {
      _delete_tree(tempDir)
    }
  }

  private def _zip_dir(sourceDir: Path, archive: Path): Unit = {
    val stream = Files.walk(sourceDir)
    try {
      val files = stream.iterator().asScala.toVector.collect {
        case p if Files.isRegularFile(p) =>
          p -> sourceDir.relativize(p).toString.replace('\\', '/')
      }.sortBy(_._2)
      val out = new ZipOutputStream(Files.newOutputStream(archive))
      try {
        files.foreach { case (file, relative) =>
          out.putNextEntry(new ZipEntry(relative))
          Files.copy(file, out)
          out.closeEntry()
        }
      } finally {
        out.close()
      }
    } finally {
      stream.close()
    }
  }

  private def _component_descriptor_json(
    name: String,
    version: String,
    component: String,
    extensions: Map[String, String],
    config: Map[String, String]
  ): String =
    s"""{
       |  "name": ${_json_string(name)},
       |  "version": ${_json_string(version)},
       |  "component": ${_json_string(component)},
       |  "extensions": ${_json_map(extensions)},
       |  "config": ${_json_map(config)}
       |}
       |""".stripMargin

  private def _json_map(xs: Map[String, String]): String =
    xs.toVector.sortBy(_._1).map { case (k, v) => s"${_json_string(k)}: ${_json_string(v)}" }.mkString("{", ", ", "}")

  private def _json_string(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _required_path(args: List[String], key: String): Path =
    _path(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _required_value(args: List[String], key: String): String =
    _value(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _path(args: List[String], key: String): Option[Path] =
    _value(args, key).map(p => Paths.get(p).toAbsolutePath.normalize())

  private def _paths(args: List[String], key: String): Vector[Path] =
    _value(args, key).toVector.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).map(p => Paths.get(p).toAbsolutePath.normalize())

  private def _string_map(args: List[String], key: String): Map[String, String] =
    _value(args, key).toVector.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).flatMap { kv =>
      kv.split("=", 2).toList match {
        case k :: v :: Nil if k.nonEmpty => Some(k -> v)
        case _ => None
      }
    }.toMap

  private def _value(args: List[String], key: String): Option[String] = {
    val prefix = s"--${key}="
    args.collectFirst {
      case s if s.startsWith(prefix) => s.substring(prefix.length)
    }.orElse {
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => value
      }
    }
  }

  private def _values(args: List[String], key: String): Vector[String] =
    _value(args, key).toVector.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)

  private def _write_text(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _write_temp(prefix: String, text: String): Path = {
    val path = Files.createTempFile(prefix, ".json")
    Files.writeString(path, text, StandardCharsets.UTF_8)
    path.toAbsolutePath.normalize()
  }

  private def _is_empty_dir(path: Path): Boolean =
    !Files.exists(path) || {
      val stream = Files.walk(path)
      try !stream.iterator().hasNext
      finally stream.close()
    }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(p => Files.deleteIfExists(p))
      } finally {
        stream.close()
      }
    }
}

private object CozySbtBridge {
  def execute(args: List[String]): Unit =
    args match {
      case "v1" :: rest =>
        executeV1(rest)
      case _ =>
        RAISE.invalidArgumentFault("Missing sbt-bridge version. Expected: sbt-bridge v1 --request=<file>")
    }

  private def executeV1(args: List[String]): Unit = {
    val requestPath = _required_path(args, "request")
    val request = _load_request(requestPath)
    request.action match {
      case "generate" =>
        _run_generation(request.arguments)
      case "package-car" =>
        CozyArchivePackager.buildCar(request.arguments.toList)
      case "package-sar" =>
        CozyArchivePackager.buildSar(request.arguments.toList)
      case other =>
        RAISE.invalidArgumentFault(s"Unsupported sbt-bridge v1 action: $other")
    }
  }

  private def _run_generation(args: Vector[String]): Unit =
    args.toList match {
      case command :: rest =>
        command match {
          case "modeler-scala" =>
            val cozy = Cozy.build(Array.empty)
            cozy.executeDirect((command :: rest).toArray)
          case other =>
            RAISE.invalidArgumentFault(s"Unsupported sbt-bridge generation command: $other")
        }
      case Nil =>
        RAISE.invalidArgumentFault("Missing sbt-bridge generation arguments")
    }

  private def _load_request(path: Path): BridgeRequest = {
    val text = Files.readString(path, StandardCharsets.UTF_8)
    Json.parse(text).validate[BridgeRequest] match {
      case JsSuccess(request, _) =>
        if (request.version != "v1")
          RAISE.invalidArgumentFault(s"Unsupported sbt-bridge request version: ${request.version}")
        request
      case JsError(errors) =>
        val detail = errors.map { case (p, xs) => s"${p.toJsonString}: ${xs.map(_.message).mkString(", ")}" }.mkString("; ")
        RAISE.invalidArgumentFault(s"Invalid sbt-bridge request file: ${path.toAbsolutePath.normalize()} (${detail})")
    }
  }

  private def _required_path(args: List[String], key: String): Path = {
    val prefix = s"--${key}="
    args.collectFirst {
      case s if s.startsWith(prefix) => Paths.get(s.substring(prefix.length)).toAbsolutePath.normalize()
    }.orElse {
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => Paths.get(value).toAbsolutePath.normalize()
      }
    }.getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))
  }

  private case class BridgeRequest(version: String, action: String, arguments: Vector[String])
  private implicit val _bridge_request_format: Format[BridgeRequest] = Json.format[BridgeRequest]
}
