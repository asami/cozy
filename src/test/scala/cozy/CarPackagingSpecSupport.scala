package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.jar.{JarEntry, JarFile, JarOutputStream}
import scala.collection.JavaConverters._
import scala.util.Try

import cozy.archive.CozyArchivePackager
import cozy.compatibility.{
  GenerationCompatibility,
  GenerationCompatibilityBoundary,
  GenerationCompatibilityEvidence,
  GenerationEvidenceOwner,
  GenerationPairEvidence,
  GenerationPairStatus
}
import cozy.config.CozyProjectYamlConfig
import io.circe.Json
import io.circe.parser.parse

/*
 * @since   Jul. 28, 2026
 * @version Aug. 20, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CarPackagingSpecSupport {
  private val _cncf_version = "0.5.17"
  private val _release_cozy_version = "0.3.2.1"

  def buildCarWithContract(args: List[String]): Unit = {
    val temporaryproject = !args.contains("--project-dir")
    val projectdir = _argument(args, "project-dir").
      map(Path.of(_)).
      getOrElse {
        val workroot = Path.of("target/cozy-test/work/car-packaging-spec-support").toAbsolutePath.normalize()
        Files.createDirectories(workroot)
        Files.createTempDirectory(workroot, "contract-")
      }
    val projectyaml = projectdir.resolve("project.yaml")
    val originalprojectyaml =
      Option(projectyaml).filter(Files.isRegularFile(_)).map(Files.readAllBytes(_))
    val originalconfig =
      originalprojectyaml.map(_load_project_config).getOrElse(
        CozyProjectYamlConfig.Config.empty
      )
    val requestversion = _argument(args, "version").getOrElse("")
    val defaultcozyversion =
      if (_is_snapshot_version(requestversion))
        org.simplemodeling.cozy.BuildInfo.version
      else
        _release_cozy_version
    val cozyversion =
      originalconfig.value("build.cozyVersion").getOrElse(defaultcozyversion)
    val runtimedescriptor = _runtime_descriptor(args)
    val cncfversion =
      runtimedescriptor.flatMap(_._2).
        orElse(originalconfig.list("packaging.car.runtime.cncf.tested").headOption).
        orElse(originalconfig.value("packaging.car.runtime.cncf.version")).
        orElse(originalconfig.value("packaging.car.runtime.cncf.minimum")).
        getOrElse(_cncf_version)
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
        cozyversion,
        requestversion,
        includedependencies = temporaryproject
      )
      val effectiveconfig = CozyProjectYamlConfig.load(projectyaml)
      val effectivecncfversion = _effective_cncf_version(effectiveconfig, cncfversion)
      val effectivecozyversion =
        effectiveconfig.value("build.cozyVersion").getOrElse(cozyversion)
      val evidence = _generation_evidence(effectivecncfversion, effectivecozyversion)
      CozyArchivePackager._build_car(effectiveargs, evidence, effectivecozyversion)
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
    cozyversion: String,
    requestversion: String,
    includedependencies: Boolean
  ): Unit = {
    val defaults = parse(
      s"""{
         |  "project": {
         |    "namespace": "org.sample",
         |    "id": "Component",
         |    "name": "packaging-spec",
         |    "kind": "car",
         |    "component": {
         |      "version": "${requestversion}"
         |    }
         |  },
         |  "build": {
         |    "cozyVersion": "${cozyversion}",
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
    val workroot = Path.of("target/cozy-test/work/car-packaging-spec-support").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val temporary = Files.createTempFile(workroot, "project-", ".yaml")
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

  private def _effective_cncf_version(
    config: CozyProjectYamlConfig.Config,
    fallback: String
  ): String =
    config.list("build.dependencies.compile").collectFirst {
      case value
          if value.startsWith("org.goldenport::goldenport-cncf:") ||
            value.startsWith("org.goldenport::goldenport-cncf_3:") ||
            value.startsWith("org.goldenport:goldenport-cncf_3:") =>
        value.split(":").last
    }.getOrElse(fallback)

  private def _generation_evidence(
    cncfversion: String,
    cozyversion: String
  ): GenerationCompatibilityEvidence = {
    val pair = GenerationCompatibilityBoundary.createPair(cncfversion, cozyversion)
    val evidencepair =
      if (GenerationCompatibility.isMutable(pair))
        GenerationCompatibilityBoundary.createPair("0.5.2", "0.3.1")
      else
        pair
    GenerationCompatibilityEvidence(
      GenerationCompatibility.evidenceSchema,
      GenerationEvidenceOwner(
        "CarPackagingSpecSupport controlled generation fixture",
        "CarPackagingSpecSupport"
      ),
      Vector(GenerationPairEvidence(evidencepair, GenerationPairStatus.Proven)),
      None
    )
  }

  private def _is_snapshot_version(version: String): Boolean =
    version.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")

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
