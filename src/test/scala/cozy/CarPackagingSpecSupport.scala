package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.jar.{JarEntry, JarFile, JarOutputStream}
import scala.collection.JavaConverters._
import scala.util.Try

import cozy.archive.CozyArchivePackager
import cozy.config.CozyProjectYamlConfig
import io.circe.Json
import io.circe.parser.parse

/*
 * @since   Jul. 28, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CarPackagingSpecSupport {
  private val CNCF_VERSION = "0.5.17"

  def buildCarWithContract(args: List[String]): Unit = {
    val temporaryproject = !args.contains("--project-dir")
    val projectdir = _argument(args, "project-dir").
      map(Path.of(_)).
      getOrElse(Files.createTempDirectory("cozy-car-contract-"))
    val projectyaml = projectdir.resolve("project.yaml")
    val originalprojectyaml =
      Option(projectyaml).filter(Files.isRegularFile(_)).map(Files.readAllBytes(_))
    val originalconfig =
      originalprojectyaml.map(_load_project_config).getOrElse(
        CozyProjectYamlConfig.Config.empty
      )
    val runtimedescriptor = _runtime_descriptor(args)
    val cncfversion =
      runtimedescriptor.flatMap(_._2).
        orElse(originalconfig.list("packaging.car.runtime.cncf.tested").headOption).
        orElse(originalconfig.value("packaging.car.runtime.cncf.version")).
        orElse(originalconfig.value("packaging.car.runtime.cncf.minimum")).
        getOrElse(CNCF_VERSION)
    val runtimejar =
      if (runtimedescriptor.nonEmpty)
        None
      else
        Some(_write_cncf_runtime_jar(projectdir, cncfversion))
    val projectargs =
      args ++
        (if (temporaryproject)
           List("--project-dir", projectdir.toString)
         else
           Nil)
    val effectiveargs = runtimejar.fold(projectargs) { path =>
      _append_csv_argument(projectargs, "lib-jars", path.toString)
    }
    try {
      _write_project_contract(
        projectyaml,
        originalconfig.json.getOrElse(Json.obj()),
        cncfversion,
        includedependencies = temporaryproject
      )
      CozyArchivePackager.buildCar(effectiveargs)
    } finally {
      originalprojectyaml match {
        case Some(bytes) => Files.write(projectyaml, bytes)
        case None => Files.deleteIfExists(projectyaml)
      }
      runtimejar.foreach(Files.deleteIfExists(_))
      if (temporaryproject)
        _delete_tree(projectdir)
    }
  }

  private def _write_project_contract(
    projectyaml: Path,
    original: Json,
    cncfversion: String,
    includedependencies: Boolean
  ): Unit = {
    val defaults = parse(
      s"""{
         |  "project": {
         |    "name": "packaging-spec",
         |    "kind": "car"
         |  },
         |  "build": {
         |    "cozyVersion": "${org.simplemodeling.cozy.BuildInfo.version}",
         |    "dependencies": {
         |      "compile": [
         |        "org.goldenport::goldenport-cncf:${cncfversion}"
         |      ]
         |    }
         |  },
         |  "packaging": {
         |    "kind": "car",
         |    "car": {
         |      "include_dependencies": ${includedependencies},
         |      "runtime": {
         |        "cncf": {
         |          "minimum": "${cncfversion}",
         |          "tested": ["${cncfversion}"]
         |        }
         |      }
         |    }
         |  }
         |}""".stripMargin
    ).fold(error => throw error, identity)
    val content = defaults.deepMerge(original).spaces2
    Option(projectyaml.getParent).foreach(Files.createDirectories(_))
    Files.writeString(
      projectyaml,
      content,
      StandardCharsets.UTF_8
    )
  }

  private def _load_project_config(
    bytes: Array[Byte]
  ): CozyProjectYamlConfig.Config = {
    val temporary = Files.createTempFile("cozy-car-project-", ".yaml")
    try {
      Files.write(temporary, bytes)
      CozyProjectYamlConfig.load(temporary)
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private def _write_cncf_runtime_jar(
    projectdir: Path,
    cncfversion: String
  ): Path = {
    val path = projectdir.resolve(s"goldenport-cncf_3-${cncfversion}.jar")
    val output = new JarOutputStream(Files.newOutputStream(path))
    try {
      output.putNextEntry(new JarEntry("META-INF/cncf/runtime.yaml"))
      output.write(
        s"""runtime: cncf
           |module: org.goldenport:goldenport-cncf_3:${cncfversion}
           |version: ${cncfversion}
           |""".stripMargin.getBytes(StandardCharsets.UTF_8)
      )
      output.closeEntry()
    } finally {
      output.close()
    }
    path
  }

  private def _runtime_descriptor(
    args: List[String]
  ): Option[(Path, Option[String])] = {
    val paths = Vector("main-jar", "lib-jars").
      flatMap(key => _argument(args, key).toVector).
      flatMap(_.split(",").toVector).
      map(_.trim).
      filter(_.nonEmpty).
      map(Path.of(_))
    paths.flatMap(path => _runtime_descriptor(path).toVector).headOption
  }

  private def _runtime_descriptor(
    path: Path
  ): Option[(Path, Option[String])] =
    if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".jar"))
      Try {
        val jar = new JarFile(path.toFile)
        try {
          Option(jar.getJarEntry("META-INF/cncf/runtime.yaml")).map { entry =>
            val input = jar.getInputStream(entry)
            try {
              val config = CozyProjectYamlConfig.parsePublic(input.readAllBytes())
              path -> config.value("version")
            } finally {
              input.close()
            }
          }
        } finally {
          jar.close()
        }
      }.toOption.flatten
    else
      None

  private def _argument(args: List[String], key: String): Option[String] =
    args.sliding(2).collectFirst {
      case List(flag, value) if flag == s"--${key}" => value
    }

  private def _append_csv_argument(
    args: List[String],
    key: String,
    value: String
  ): List[String] = {
    val flag = s"--${key}"
    val index = args.indexOf(flag)
    if (index >= 0 && index + 1 < args.length)
      args.updated(index + 1, s"${args(index + 1)},${value}")
    else
      args ++ List(flag, value)
  }

  private def _delete_tree(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(
          Files.deleteIfExists(_)
        )
      } finally {
        stream.close()
      }
    }
  }
}
