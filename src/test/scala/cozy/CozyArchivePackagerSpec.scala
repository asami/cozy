package cozy

import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

import cozy.archive.CozyArchivePackager
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.Json

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyArchivePackagerSpec extends AnyFunSuite {
  test("package-car writes descriptor-first CAR layout") {
    _with_temp_dir("cozy-car") { dir =>
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val libjar = _write(dir.resolve("artifacts/dep.jar"), "dep")
      val spijar = _write(dir.resolve("artifacts/spi.jar"), "spi")
      val defaultconf = _write(dir.resolve("conf/default.conf"), "service.timeout=10")
      val dependencymanifest = _write(dir.resolve("conf/component-dependencies.yaml"), "dependencies:\n  shared:\n    - org.postgresql:postgresql:42.7.3\n")
      _write(dir.resolve("docs/guide/intro.md"), "# intro")
      val carnote = _write(dir.resolve("src/main/car/manual/component.md"), "# component")
      val carwebdescriptor = _write(dir.resolve("src/main/car/web/web.yaml"), "apps:\n  - name: from-car\n")
      _write(dir.resolve("src/main/web/web.yaml"), "apps:\n  - name: from-web-app\n")
      val webapp = _write(dir.resolve("src/main/web/cwitter/index.html"), "<!doctype html><title>Cwitter</title>")
      val assembly = _write(dir.resolve("assembly-descriptor.yaml"), "subsystem: sample-component\ncomponents:\n  - name: sample-component\n")
      val archive = dir.resolve("out/sample.car")

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--main-jar=$mainjar",
        s"--lib-jars=$libjar",
        s"--spi-jars=$spijar",
        s"--car-dir=${carnote.getParent.getParent}",
        s"--default-conf=$defaultconf",
        s"--dependency-manifest=$dependencymanifest",
        s"--web-dir=${webapp.getParent.getParent}",
        s"--assembly-descriptor=$assembly",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component",
        "--entities=Notice:usageKind=public-content,operationKind=resource,applicationDomain=cms;SalesOrder:usage_kind=business-object,operation_kind=resource,application_domain=business"
      ))

      val entries = _zip_entries(archive)
      val descriptor = _zip_text(archive, "component-descriptor.json")
      val webdescriptor = _zip_text(archive, "web/web.yaml")
      assert(entries.contains("component-descriptor.json"))
      assert(entries.contains("component/main.jar"))
      assert(entries.contains("lib/dep.jar"))
      assert(entries.contains("spi/spi.jar"))
      assert(entries.contains("config/default.conf"))
      assert(entries.contains("component-dependencies.yaml"))
      assert(!entries.contains("cozy/component-dependencies.yaml"))
      assert(entries.contains("assembly-descriptor.yaml"))
      assert(entries.contains("web/web.yaml"))
      assert(entries.contains("web/cwitter/index.html"))
      assert(webdescriptor == Files.readString(carwebdescriptor))
      assert(!webdescriptor.contains("from-web-app"))
      assert(!entries.contains("component.d/provider.car"))
      assert(entries.contains("manual/component.md"))
      assert(!entries.contains("docs/guide/intro.md"))
      assert(!entries.contains("meta/manifest.json"))
      assert(descriptor.contains(""""entities": ["""))
      assert(descriptor.contains(""""entity": "Notice""""))
      assert(descriptor.contains(""""usageKind": "public-content""""))
      assert(descriptor.contains(""""operationKind": "resource""""))
      assert(descriptor.contains(""""applicationDomain": "cms""""))
      assert(descriptor.contains(""""entity": "SalesOrder""""))
      assert(descriptor.contains(""""usageKind": "business-object""""))
      assert(descriptor.contains(""""applicationDomain": "business""""))
    }
  }

  test("package-car prefers structured component descriptor override") {
    _with_temp_dir("cozy-car-componentlet") { dir =>
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      val descriptorjson =
        """{"component":{"name":"sample-component","kind":"component","isPrimary":"true"},"componentlets":[{"name":"notice-admin","kind":"componentlet"},{"name":"public-notice","kind":"componentlet"}]}"""

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--main-jar=$mainjar",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component",
        s"""--extensions={"componentDescriptorJson":${Json.stringify(Json.toJson(descriptorjson))}}"""
      ))

      val descriptor = _zip_text(archive, "component-descriptor.json")
      assert(descriptor == descriptorjson)
      assert(descriptor.contains("\"componentlets\""))
      assert(descriptor.contains("\"name\":\"notice-admin\""))
      assert(descriptor.contains("\"name\":\"public-notice\""))
    }
  }

  test("package-car reads project packaging policy and writes dependency manifest") {
    _with_temp_dir("cozy-car-project-policy") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val libjar = _write(dir.resolve("artifacts/dep.jar"), "dep")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    include_dependencies: false
          |    manifest_metadata:
          |      component: policy-component
          |    dependencies:
          |      provided:
          |        - org.goldenport:goldenport-cncf_3:0.4.8-SNAPSHOT
          |      shared:
          |        - org.postgresql:postgresql:42.7.3
          |      repositories:
          |        - maven-central
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--project-dir=$projectdir",
        s"--main-jar=$mainjar",
        s"--lib-jars=$libjar",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component"
      ))

      val entries = _zip_entries(archive)
      val manifest = _zip_text(archive, "component-dependencies.yaml")
      val descriptor = _zip_text(archive, "component-descriptor.json")
      assert(entries.contains("component-dependencies.yaml"))
      assert(!entries.contains("cozy/component-dependencies.yaml"))
      assert(!entries.contains("lib/dep.jar"))
      assert(descriptor.contains(""""component": "policy-component""""))
      assert(manifest.contains("provided:"))
      assert(manifest.contains("shared:"))
      assert(manifest.contains("\"org.postgresql:postgresql:42.7.3\""))
    }
  }

  test("package-car accepts CNCF runtime requirement while defaulting project CAR policy") {
    _with_temp_dir("cozy-car-runtime-requirement") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val libjar = _write(dir.resolve("artifacts/dep.jar"), "dep")
      val archive = dir.resolve("out/sample.car")
      _write(projectdir.resolve("src/main/car/web/web.yaml"), "apps:\n  - name: default-car-dir\n")
      _write(
        projectdir.resolve("repository/textus/runtime-catalog.yaml"),
        """schemaVersion: 1
          |baseProvided:
          |  - org.goldenport:goldenport-cncf_3
          |  - org.simplemodeling:simplemodeling-model_3
          |""".stripMargin
      )
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.8
          |        excluded: []
          |        tested:
          |          - 0.4.8
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--project-dir=$projectdir",
        s"--main-jar=$mainjar",
        s"--lib-jars=$libjar",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component"
      ))

      val entries = _zip_entries(archive)
      assert(entries.contains("web/web.yaml"))
      assert(!entries.contains("component-dependencies.yaml"))
      assert(!entries.contains("lib/dep.jar"))
    }
  }

  test("package-car embeds dependency jars only when project policy enables them") {
    _with_temp_dir("cozy-car-include-dependencies") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val libjar = _write(dir.resolve("artifacts/dep.jar"), "dep")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    include_dependencies: true
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--project-dir=$projectdir",
        s"--main-jar=$mainjar",
        s"--lib-jars=$libjar",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component"
      ))

      val entries = _zip_entries(archive)
      assert(entries.contains("lib/dep.jar"))
    }
  }

  test("package-car writes only component-owned dependencies with CNCF runtime requirement") {
    _with_temp_dir("cozy-car-component-owned-deps") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("repository/textus/runtime-catalog.yaml"),
        """schemaVersion: 1
          |baseProvided:
          |  - org.goldenport:goldenport-cncf_3
          |  - org.typelevel:cats-core_3
          |""".stripMargin
      )
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.8
          |    dependencies:
          |      shared:
          |        - org.postgresql:postgresql:42.7.3
          |      local:
          |        - com.example:legacy-driver:1.2.0
          |      repositories:
          |        - maven-central
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--project-dir=$projectdir",
        s"--main-jar=$mainjar",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component"
      ))

      val manifest = _zip_text(archive, "component-dependencies.yaml")
      assert(!manifest.contains("goldenport-cncf"))
      assert(!manifest.contains("cats-core"))
      assert(manifest.contains("\"org.postgresql:postgresql:42.7.3\""))
      assert(manifest.contains("\"com.example:legacy-driver:1.2.0\""))
      assert(manifest.contains("repositories:"))
    }
  }

  test("package-car rejects dependencies already provided by CNCF runtime catalog") {
    _with_temp_dir("cozy-car-base-provided-overlap") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("repository/textus/runtime-catalog.yaml"),
        """schemaVersion: 1
          |baseProvided:
          |  - org.typelevel:cats-core_3
          |""".stripMargin
      )
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.8
          |    dependencies:
          |      shared:
          |        - org.typelevel:cats-core_3:2.10.0
          |""".stripMargin
      )

      val ex = intercept[Throwable] {
        CozyArchivePackager.buildCar(List(
          s"--save=$archive",
          s"--project-dir=$projectdir",
          s"--main-jar=$mainjar",
          "--name=sample-component",
          "--version=0.1.0",
          "--component=sample-component"
        ))
      }
      assert(ex.getMessage.contains("base-provided"))
      assert(ex.getMessage.contains("org.typelevel:cats-core_3:2.10.0"))
    }
  }

  test("package-sar writes descriptor at SAR top level") {
    _with_temp_dir("cozy-sar") { dir =>
      val sourcedir = dir.resolve("src")
      _write(sourcedir.resolve("subsystem-descriptor.yaml"), "subsystem: textus-identity\n")
      val extension = _write(dir.resolve("ext/grpc.jar"), "grpc")
      val appconf = _write(dir.resolve("conf/application.conf"), "env=dev")
      val archive = dir.resolve("out/sample.sar")

      CozyArchivePackager.buildSar(List(
        s"--save=$archive",
        s"--source-dir=$sourcedir",
        "--source-files=subsystem-descriptor.yaml",
        s"--extension-jars=$extension",
        s"--application-conf=$appconf"
      ))

      val entries = _zip_entries(archive)
      assert(entries.contains("subsystem-descriptor.yaml"))
      assert(entries.contains("extension/grpc.jar"))
      assert(entries.contains("config/application.conf"))
      assert(!entries.contains("subsystem/subsystem-descriptor.yaml"))
      assert(!entries.contains("meta/manifest.json"))
    }
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try f(dir)
    finally _delete_tree(dir)
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes("UTF-8"))
    path
  }

  private def _zip_entries(path: Path): Set[String] = {
    val zip = new ZipFile(path.toFile)
    try zip.entries().asScala.map(_.getName).toSet
    finally zip.close()
  }

  private def _zip_text(path: Path, entryname: String): String = {
    val zip = new ZipFile(path.toFile)
    try {
      val entry = zip.getEntry(entryname)
      val in = zip.getInputStream(entry)
      try scala.io.Source.fromInputStream(in, "UTF-8").mkString
      finally in.close()
    } finally {
      zip.close()
    }
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.sortBy(_.getNameCount)(Ordering[Int].reverse).foreach(Files.deleteIfExists(_))
      } finally {
        stream.close()
      }
    }
}
