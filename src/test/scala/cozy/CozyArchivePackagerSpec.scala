package cozy

import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

import org.scalatest.funsuite.AnyFunSuite

class CozyArchivePackagerSpec extends AnyFunSuite {
  test("package-car writes descriptor-first CAR layout") {
    withTempDir("cozy-car") { dir =>
      val mainJar = write(dir.resolve("artifacts/main.jar"), "main")
      val libJar = write(dir.resolve("artifacts/dep.jar"), "dep")
      val spiJar = write(dir.resolve("artifacts/spi.jar"), "spi")
      val defaultConf = write(dir.resolve("conf/default.conf"), "service.timeout=10")
      val doc = write(dir.resolve("docs/guide/intro.md"), "# intro")
      val archive = dir.resolve("out/sample.car")

      CozyArchivePackager.buildCar(List(
        s"--save=$archive",
        s"--main-jar=$mainJar",
        s"--lib-jars=$libJar",
        s"--spi-jars=$spiJar",
        s"--default-conf=$defaultConf",
        s"--docs-dir=${doc.getParent.getParent}",
        "--name=sample-component",
        "--version=0.1.0",
        "--component=sample-component"
      ))

      val entries = zipEntries(archive)
      assert(entries.contains("component-descriptor.json"))
      assert(entries.contains("component/main.jar"))
      assert(entries.contains("lib/dep.jar"))
      assert(entries.contains("spi/spi.jar"))
      assert(entries.contains("config/default.conf"))
      assert(entries.contains("docs/guide/intro.md"))
      assert(!entries.contains("meta/manifest.json"))
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
