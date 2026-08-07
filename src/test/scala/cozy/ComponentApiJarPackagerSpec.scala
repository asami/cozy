package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.archive.{ComponentApiJarPackager, CozyArchivePackager}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 12, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentApiJarPackagerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Component API JAR packager" should {
    "derive contract-only component API artifacts" should {
    "package only descriptor-selected public artifacts" in {
      Given("a component JAR and descriptor with public class, companion, nested, and TASTy patterns")
      _with_temp_dir("cozy-component-api-jar") { dir =>
        val mainjar = _write_zip(
          dir.resolve("component.jar"),
          Map(
            "example/api/ExampleApi.class" -> "api",
            "example/api/ExampleApi$.class" -> "companion",
            "example/api/ExampleApi$Socket.class" -> "socket",
            "example/api/ExampleApi.tasty" -> "tasty",
            "example/value/Request.class" -> "request",
            "example/value/Request$.class" -> "request-companion",
            "example/value/Request.tasty" -> "request-tasty",
            "example/impl/ExampleLogic.class" -> "implementation",
            "example/ComponentFactory.class" -> "factory"
          )
        )
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          _descriptor(
            provided =
              """[{"version":"0.1.0","artifactPath":"spi/example-api-api.jar","publicTypes":[
                |{"className":"example.api.ExampleApi","artifactPatterns":["example/api/ExampleApi.class","example/api/ExampleApi$*.class","example/api/ExampleApi.tasty"]},
                |{"className":"example.value.Request","artifactPatterns":["example/value/Request.class","example/value/Request$*.class","example/value/Request.tasty"]}
                |]}]""".stripMargin
          )
        )
        val output = dir.resolve("example-api-api.jar")

        When("the descriptor is used to build the contract-only JAR")
        ComponentApiJarPackager._build_api_jar(mainjar, descriptor, output)

        Then("all public runtime and Scala metadata artifacts are present")
        val entries = _zip_entries(output)
        entries should contain allOf (
          "example/api/ExampleApi.class",
          "example/api/ExampleApi$.class",
          "example/api/ExampleApi$Socket.class",
          "example/api/ExampleApi.tasty",
          "example/value/Request.class",
          "example/value/Request$.class",
          "example/value/Request.tasty"
        )

        And("component implementation and factory classes are absent")
        entries should not contain "example/impl/ExampleLogic.class"
        entries should not contain "example/ComponentFactory.class"
      }
    }

    "partition public API artifacts out of the packaged implementation JAR" in {
      Given("a component JAR whose public API is also published as an SPI artifact")
      _with_temp_dir("cozy-component-api-partition") { dir =>
        val mainjar = _write_zip(
          dir.resolve("component.jar"),
          Map(
            "example/api/ExampleApi.class" -> "api",
            "example/api/ExampleApi$.class" -> "companion",
            "example/api/ExampleApi.tasty" -> "tasty",
            "example/impl/ExampleLogic.class" -> "implementation"
          )
        )
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          _descriptor(
            provided =
              """[{"version":"0.1.0","artifactPath":"spi/example-api-api.jar","publicTypes":[
                |{"className":"example.api.ExampleApi","artifactPatterns":["example/api/ExampleApi.class","example/api/ExampleApi$*.class","example/api/ExampleApi.tasty"]}
                |]}]""".stripMargin
          )
        )

        When("the CAR implementation JAR is derived from the API descriptor")
        ComponentApiJarPackager._with_implementation_jar(mainjar, descriptor) { implementationjar =>
          val entries = _zip_entries(implementationjar)

          Then("implementation classes remain available")
          entries should contain("example/impl/ExampleLogic.class")

          And("all descriptor-owned public API artifacts are absent")
          entries should not contain "example/api/ExampleApi.class"
          entries should not contain "example/api/ExampleApi$.class"
          entries should not contain "example/api/ExampleApi.tasty"
        }
      }
    }

    "reject a descriptor that selects an implementation artifact" in {
      Given("a malformed public contract descriptor that includes an implementation class")
      _with_temp_dir("cozy-component-api-forbidden") { dir =>
        val mainjar = _write_zip(dir.resolve("component.jar"), Map("example/impl/Leaked.class" -> "leaked"))
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          _descriptor(
            provided =
              """[{"version":"0.1.0","artifactPath":"spi/example-api-api.jar","publicTypes":[
                |{"className":"example.impl.Leaked","artifactPatterns":["example/impl/Leaked.class"]}
                |]}]""".stripMargin
          )
        )

        When("the API JAR is built")
        val error = intercept[Throwable] {
          ComponentApiJarPackager._build_api_jar(mainjar, descriptor, dir.resolve("example-api-api.jar"))
        }

        Then("packaging fails at the public artifact boundary")
        error.getMessage should include("forbidden implementation class")
      }
    }

    "remove stale output when the component provides no API" in {
      Given("a consumer-only component descriptor and stale API JAR")
      _with_temp_dir("cozy-component-api-consumer") { dir =>
        val mainjar = _write_zip(dir.resolve("component.jar"), Map("example/Consumer.class" -> "consumer"))
        val descriptor = _write(dir.resolve("component-api-descriptor.json"), _descriptor("[]"))
        val output = _write(dir.resolve("example-api-api.jar"), "stale")

        When("component API packaging runs")
        ComponentApiJarPackager._build_api_jar(mainjar, descriptor, output)

        Then("no provider API artifact remains")
        Files.exists(output) shouldBe false
      }
    }

    "reject a provided API release that differs from the component release" in {
      Given("a direct API descriptor with a stale provided.version")
      _with_temp_dir("cozy-component-api-release") { dir =>
        val mainjar = _write_zip(dir.resolve("component.jar"), Map("example/Api.class" -> "api"))
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          _descriptor("""[{"version":"0.0.9","artifactPath":"spi/example-api-api.jar","publicTypes":[]}]""")
        )

        When("the direct API JAR boundary admits the descriptor")
        val error = intercept[Throwable] {
          ComponentApiJarPackager._build_api_jar(mainjar, descriptor, dir.resolve("example-api-api.jar"))
        }

        Then("the shared provided.version projection diagnostic is retained")
        error.getMessage should include("component.release-coordinate.projection-mismatch")
        error.getMessage should include("field=provided.version")
      }
    }
    }

    "enforce CAR API and SPI packaging contracts" should {
    "embed a coordinate-matched descriptor and its declared API JAR in a CAR" in {
      Given("a component descriptor and matching contract-only API JAR")
      _with_temp_dir("cozy-component-api-car") { dir =>
        val mainjar = _write(dir.resolve("component.jar"), "component")
        _write(dir.resolve("project.yaml"), _project_yaml)
        val apijar = _write(dir.resolve("example-api-api.jar"), "api")
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          _car_descriptor("""[{"version":"0.1.0-SNAPSHOT","artifactPath":"spi/example-api-api.jar","publicTypes":[]}]""")
        )
        val car = dir.resolve("example.car")

        When("the CAR is packaged")
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", car.toString,
          "--project-dir", dir.toString,
          "--main-jar", mainjar.toString,
          "--spi-jars", apijar.toString,
          "--component-api-descriptor", descriptor.toString,
          "--name", "example-api",
          "--version", "0.1.0-SNAPSHOT",
          "--component", "Api"
        ))

        Then("the descriptor and its API JAR are both present")
        val entries = _zip_entries(car)
        entries should contain allOf ("component-api-descriptor.json", "spi/example-api-api.jar")
        _zip_text(car, "component-api-descriptor.json") shouldBe Files.readString(descriptor)
      }
    }

    "reject a CAR whose descriptor API artifact is missing" in {
      Given("a provider descriptor without its declared API JAR")
      _with_temp_dir("cozy-component-api-missing") { dir =>
        val mainjar = _write(dir.resolve("component.jar"), "component")
        _write(dir.resolve("project.yaml"), _project_yaml)
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          _car_descriptor("""[{"version":"0.1.0-SNAPSHOT","artifactPath":"spi/example-api-api.jar","publicTypes":[]}]""")
        )

        When("the invalid CAR is packaged")
        val error = intercept[Throwable] {
          CarPackagingSpecSupport.buildCarWithContract(List(
            "--save", dir.resolve("example.car").toString,
            "--project-dir", dir.toString,
            "--main-jar", mainjar.toString,
            "--component-api-descriptor", descriptor.toString,
            "--name", "example-api",
            "--version", "0.1.0-SNAPSHOT",
            "--component", "Api"
          ))
        }

        Then("packaging reports the missing descriptor artifact")
        error.getMessage should include("artifacts are missing from CAR SPI JARs")
      }
    }

    "reject a CAR descriptor whose API artifact path disagrees with its coordinate" in {
      Given("a canonical project and a descriptor with a forged SPI projection")
      _with_temp_dir("cozy-component-api-projection") { dir =>
        val mainjar = _write(dir.resolve("component.jar"), "component")
        _write(dir.resolve("project.yaml"), _project_yaml)
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          _car_descriptor("""[{"version":"0.1.0-SNAPSHOT","artifactPath":"spi/forged-api.jar","publicTypes":[]}]""")
        )

        When("the CAR boundary reads the descriptor")
        val error = intercept[Throwable] {
          CarPackagingSpecSupport.buildCarWithContract(List(
            "--save", dir.resolve("example.car").toString,
            "--project-dir", dir.toString,
            "--main-jar", mainjar.toString,
            "--component-api-descriptor", descriptor.toString,
            "--name", "example-api",
            "--version", "0.1.0-SNAPSHOT",
            "--component", "Api"
          ))
        }

        Then("the projection mismatch is rejected before archive output")
        error.getMessage should include("component.release-coordinate.projection-mismatch")
      }
    }

    "reject a CAR descriptor whose provided API release disagrees with its coordinate" in {
      Given("a canonical CAR project and a stale provided.version")
      _with_temp_dir("cozy-component-api-car-release") { dir =>
        val mainjar = _write(dir.resolve("component.jar"), "component")
        _write(dir.resolve("project.yaml"), _project_yaml)
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          _car_descriptor("""[{"version":"0.0.9","artifactPath":"spi/example-api-api.jar","publicTypes":[]}]""")
        )

        When("the CAR boundary admits the descriptor")
        val error = intercept[Throwable] {
          CarPackagingSpecSupport.buildCarWithContract(List(
            "--save", dir.resolve("example.car").toString,
            "--project-dir", dir.toString,
            "--main-jar", mainjar.toString,
            "--component-api-descriptor", descriptor.toString,
            "--name", "example-api",
            "--version", "0.1.0-SNAPSHOT",
            "--component", "Api"
          ))
        }

        Then("the shared provided.version projection diagnostic is retained")
        error.getMessage should include("component.release-coordinate.projection-mismatch")
        error.getMessage should include("field=provided.version")
      }
    }

    "reject duplicate SPI JAR archive names" in {
      Given("two distinct SPI JAR files with the same archive name")
      _with_temp_dir("cozy-component-api-duplicate") { dir =>
        val mainjar = _write(dir.resolve("component.jar"), "component")
        _write(dir.resolve("project.yaml"), _project_yaml)
        val first = _write(dir.resolve("first/example-api-api.jar"), "first")
        val second = _write(dir.resolve("second/example-api-api.jar"), "second")

        When("the CAR is packaged")
        val error = intercept[Throwable] {
          CarPackagingSpecSupport.buildCarWithContract(List(
            "--save", dir.resolve("example.car").toString,
            "--project-dir", dir.toString,
            "--main-jar", mainjar.toString,
            "--spi-jars", s"${first},${second}",
            "--name", "example-api",
            "--version", "0.1.0-SNAPSHOT",
            "--component", "Api"
          ))
        }

        Then("packaging rejects the ambiguous archive target")
        error.getMessage should include("SPI JAR names must be unique")
      }
    }

    "reject a component API descriptor for a different CAR coordinate" in {
      Given("a component API descriptor with a stale version")
      _with_temp_dir("cozy-component-api-coordinate") { dir =>
        val mainjar = _write(dir.resolve("component.jar"), "component")
        _write(dir.resolve("project.yaml"), _project_yaml)
        val descriptor = _write(
          dir.resolve("component-api-descriptor.json"),
          """{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Api","version":"0.0.9"},"provided":[],"required":[]}"""
        )

        When("a newer CAR is packaged")
        val error = intercept[Throwable] {
          CarPackagingSpecSupport.buildCarWithContract(List(
            "--save", dir.resolve("example.car").toString,
            "--project-dir", dir.toString,
            "--main-jar", mainjar.toString,
            "--component-api-descriptor", descriptor.toString,
            "--name", "example-api",
            "--version", "0.1.0-SNAPSHOT",
            "--component", "Api"
          ))
        }

        Then("the mismatched descriptor is rejected")
        error.getMessage should include("component.release-coordinate.mismatch")
        error.getMessage should include("expected=org.example.Api:0.1.0-SNAPSHOT")
        error.getMessage should include("actual=org.example.Api:0.0.9")
      }
    }
    }
  }

  private def _descriptor(provided: String): String =
    s"""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Api","version":"0.1.0"},"provided":${provided},"required":[]}"""

  private def _car_descriptor(provided: String): String =
    s"""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Api","version":"0.1.0-SNAPSHOT"},"provided":${provided},"required":[]}"""

  private def _project_yaml: String =
    s"""project:
      |  namespace: org.example
      |  id: Api
      |  component:
      |    version: 0.1.0-SNAPSHOT
      |build:
      |  cozyVersion: ${org.simplemodeling.cozy.BuildInfo.version}
      |  dependencies:
      |    compile:
      |      - org.goldenport::goldenport-cncf:0.5.2-SNAPSHOT
      |packaging:
      |  car:
      |    runtime:
      |      cncf:
      |        minimum: 0.5.2-SNAPSHOT
      |        tested: [0.5.2-SNAPSHOT]
      |""".stripMargin

  "Component API JAR packager identity reader" should {
    "reject legacy or unknown component identity fields before macro decoding" in {
      _with_temp_dir("cozy-api-exact-component") { dir =>
        val mainjar = _write_zip(dir.resolve("component.jar"), Map("example/Api.class" -> "api"))
        Vector(
          "{\"name\":\"Api\",\"version\":\"0.1.0\"}",
          "{\"namespace\":\"org.example\",\"id\":\"Api\",\"version\":\"0.1.0\",\"unknown\":\"x\"}"
        ).zipWithIndex.foreach { case (component, index) =>
          val descriptor = _write(dir.resolve(s"descriptor-$index.json"), s"""{"schemaVersion":"cncf.component-api.v2","component":$component,"provided":[],"required":[]}""")
        intercept[Throwable] { ComponentApiJarPackager._build_api_jar(mainjar, descriptor, dir.resolve(s"api-$index.jar")) }.getMessage should include("component.release-coordinate.mismatch")
        }
      }
    }
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/component-api-jar-packager-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val dir = Files.createTempDirectory(workroot, s"$prefix-")
    try f(dir)
    finally _delete_tree(dir)
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path
  }

  private def _write_zip(path: Path, entries: Map[String, String]): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val zip = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.toVector.sortBy(_._1).foreach { case (name, content) =>
        zip.putNextEntry(new ZipEntry(name))
        zip.write(content.getBytes(StandardCharsets.UTF_8))
        zip.closeEntry()
      }
    } finally {
      zip.close()
    }
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
      val input = zip.getInputStream(zip.getEntry(entryname))
      try scala.io.Source.fromInputStream(input, "UTF-8").mkString
      finally input.close()
    } finally {
      zip.close()
    }
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount)(Ordering[Int].reverse).foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
}
