package cozy

import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.Json

class CozyArchivePackagerSpec extends AnyFunSuite {
  test("package-car writes descriptor-first CAR layout") {
    withTempDir("cozy-car") { dir =>
      val mainJar = write(dir.resolve("artifacts/main.jar"), "main")
      val libJar = write(dir.resolve("artifacts/dep.jar"), "dep")
      val spiJar = write(dir.resolve("artifacts/spi.jar"), "spi")
      val defaultConf = write(dir.resolve("conf/default.conf"), "service.timeout=10")
      write(dir.resolve("docs/guide/intro.md"), "# intro")
      val carNote = write(dir.resolve("src/main/car/manual/component.md"), "# component")
      val carWebDescriptor = write(dir.resolve("src/main/car/web/web.yaml"), "apps:\n  - name: from-car\n")
      write(dir.resolve("src/main/web/web.yaml"), "apps:\n  - name: from-web-app\n")
      val webApp = write(dir.resolve("src/main/web/cwitter/index.html"), "<!doctype html><title>Cwitter</title>")
      val assembly = write(dir.resolve("assembly-descriptor.yaml"), "subsystem: sample-component\ncomponents:\n  - name: sample-component\n")
      val archive = dir.resolve("out/sample.car")

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--main-jar=$mainJar",
        s"--lib-jars=$libJar",
        s"--spi-jars=$spiJar",
        s"--car-dir=${carNote.getParent.getParent}",
        s"--default-conf=$defaultConf",
        s"--web-dir=${webApp.getParent.getParent}",
        s"--assembly-descriptor=$assembly",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component",
        "--entities=Notice:usageKind=public-content,operationKind=resource,applicationDomain=cms;SalesOrder:usage_kind=business-object,operation_kind=resource,application_domain=business"
      ))

      val entries = zipEntries(archive)
      val descriptor = zipText(archive, "component-descriptor.json")
      val webDescriptor = zipText(archive, "web/web.yaml")
      assert(entries.contains("component-descriptor.json"))
      assert(entries.contains("component/main.jar"))
      assert(entries.contains("lib/dep.jar"))
      assert(entries.contains("spi/spi.jar"))
      assert(entries.contains("config/default.conf"))
      assert(entries.contains("assembly-descriptor.yaml"))
      assert(entries.contains("web/web.yaml"))
      assert(entries.contains("web/cwitter/index.html"))
      assert(webDescriptor == Files.readString(carWebDescriptor))
      assert(!webDescriptor.contains("from-web-app"))
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
    withTempDir("cozy-car-componentlet") { dir =>
      val mainJar = write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      val descriptorJson =
        """{"component":{"name":"sample-component","kind":"component","isPrimary":"true"},"componentlets":[{"name":"notice-admin","kind":"componentlet"},{"name":"public-notice","kind":"componentlet"}]}"""

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--main-jar=$mainJar",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component",
        s"""--extensions={"componentDescriptorJson":${Json.stringify(Json.toJson(descriptorJson))}}"""
      ))

      val descriptor = zipText(archive, "component-descriptor.json")
      assert(descriptor == descriptorJson)
      assert(descriptor.contains("\"componentlets\""))
      assert(descriptor.contains("\"name\":\"notice-admin\""))
      assert(descriptor.contains("\"name\":\"public-notice\""))
    }
  }

  test("package-sar writes descriptor at SAR top level") {
    withTempDir("cozy-sar") { dir =>
      val sourceDir = dir.resolve("src")
      write(sourceDir.resolve("subsystem-descriptor.yaml"), "subsystem: textus-identity\n")
      val extension = write(dir.resolve("ext/grpc.jar"), "grpc")
      val appConf = write(dir.resolve("conf/application.conf"), "env=dev")
      val archive = dir.resolve("out/sample.sar")

      CozyArchivePackager.buildSar(List(
        s"--save=$archive",
        s"--source-dir=$sourceDir",
        "--source-files=subsystem-descriptor.yaml",
        s"--extension-jars=$extension",
        s"--application-conf=$appConf"
      ))

      val entries = zipEntries(archive)
      assert(entries.contains("subsystem-descriptor.yaml"))
      assert(entries.contains("extension/grpc.jar"))
      assert(entries.contains("config/application.conf"))
      assert(!entries.contains("subsystem/subsystem-descriptor.yaml"))
      assert(!entries.contains("meta/manifest.json"))
    }
  }

  private def withTempDir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try f(dir)
    finally deleteTree(dir)
  }

  private def write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes("UTF-8"))
    path
  }

  private def zipEntries(path: Path): Set[String] = {
    val zip = new ZipFile(path.toFile)
    try zip.entries().asScala.map(_.getName).toSet
    finally zip.close()
  }

  private def zipText(path: Path, entryName: String): String = {
    val zip = new ZipFile(path.toFile)
    try {
      val entry = zip.getEntry(entryName)
      val in = zip.getInputStream(entry)
      try scala.io.Source.fromInputStream(in, "UTF-8").mkString
      finally in.close()
    } finally {
      zip.close()
    }
  }

  private def deleteTree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.sortBy(_.getNameCount)(Ordering[Int].reverse).foreach(Files.deleteIfExists(_))
      } finally {
        stream.close()
      }
    }
}
