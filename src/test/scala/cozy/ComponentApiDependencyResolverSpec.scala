package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.archive.ComponentApiDependencyResolver
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 12, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentApiDependencyResolverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Component API dependency resolver" should {
    "extract the one declared CAR API that satisfies a required contract" in {
      Given("a consumer requirement, matching provider CAR, and assembly coordinate")
      _with_temp_dir("cozy-api-dependency") { dir =>
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val provider = _provider_car(dir.resolve("provider.car"), "provider", "0.1.0", "example.Api", "sha256:one")
        val assembly = _write(
          dir.resolve("assembly.yaml"),
          """subsystem: consumer
            |components:
            |  - name: provider
            |    version: 0.1.0
            |""".stripMargin
        )

        When("the dependency is resolved")
        val jars = ComponentApiDependencyResolver.resolve(
          consumer,
          Vector(ComponentApiDependencyResolver.Dependency("provider", "0.1.0", provider)),
          dir.resolve("resolved"),
          Some(assembly)
        )

        Then("only the contract API JAR is extracted")
        jars.map(_.getFileName.toString) shouldBe Vector("provider-api.jar")
        Files.readString(jars.head) shouldBe "api"
      }
    }

    "fail when no declared CAR provides a required API" in {
      Given("a required API and an unrelated provider CAR")
      _with_temp_dir("cozy-api-missing") { dir =>
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Required"))
        val provider = _provider_car(dir.resolve("provider.car"), "provider", "0.1.0", "example.Other", "sha256:other")

        When("dependency matching runs")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver.resolve(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("provider", "0.1.0", provider)),
            dir.resolve("resolved"),
            None
          )
        }

        Then("the missing required API is reported")
        error.getMessage should include("Required component API is not provided")
      }
    }

    "allow an unavailable optional API while resolving declared dependencies" in {
      Given("an optional API requirement with no matching provider")
      _with_temp_dir("cozy-api-optional") { dir =>
        val consumer = _write(
          dir.resolve("consumer.json"),
          """{"schemaVersion":"cncf.component-api.v1","component":{"name":"consumer","version":"0.1.0"},"provided":[],"required":[{"apiClass":"example.Optional","required":false}]}"""
        )

        When("dependency matching runs without providers")
        val jars = ComponentApiDependencyResolver.resolve(consumer, Vector.empty, dir.resolve("resolved"), None)

        Then("no API JAR is required")
        jars shouldBe Vector.empty
      }
    }

    "fail when more than one CAR provides the same required API" in {
      Given("two provider CARs for one required API")
      _with_temp_dir("cozy-api-ambiguous") { dir =>
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val first = _provider_car(dir.resolve("first.car"), "first", "0.1.0", "example.Api", "sha256:one")
        val second = _provider_car(dir.resolve("second.car"), "second", "0.2.0", "example.Api", "sha256:two")

        When("dependency matching runs")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver.resolve(
            consumer,
            Vector(
              ComponentApiDependencyResolver.Dependency("first", "0.1.0", first),
              ComponentApiDependencyResolver.Dependency("second", "0.2.0", second)
            ),
            dir.resolve("resolved"),
            None
          )
        }

        Then("the ambiguous contract is rejected")
        error.getMessage should include("provided ambiguously")
      }
    }

    "fail when a required ABI hash differs from the provider contract" in {
      Given("a consumer-pinned ABI hash and a different provider ABI")
      _with_temp_dir("cozy-api-abi") { dir =>
        val consumer = _write(
          dir.resolve("consumer.json"),
          """{"schemaVersion":"cncf.component-api.v1","component":{"name":"consumer","version":"0.1.0"},"provided":[],"required":[{"apiClass":"example.Api","required":true,"abiHash":"sha256:expected"}]}"""
        )
        val provider = _provider_car(dir.resolve("provider.car"), "provider", "0.1.0", "example.Api", "sha256:actual")

        When("dependency matching checks ABI compatibility")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver.resolve(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("provider", "0.1.0", provider)),
            dir.resolve("resolved"),
            None
          )
        }

        Then("the ABI mismatch is rejected")
        error.getMessage should include("ABI is incompatible")
      }
    }

    "fail when assembly metadata omits a declared CAR dependency" in {
      Given("a valid API provider omitted from the assembly descriptor")
      _with_temp_dir("cozy-api-assembly") { dir =>
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val provider = _provider_car(dir.resolve("provider.car"), "provider", "0.1.0", "example.Api", "sha256:one")
        val assembly = _write(dir.resolve("assembly.yaml"), "subsystem: consumer\ncomponents: []\n")

        When("dependency and assembly metadata are validated")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver.resolve(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("provider", "0.1.0", provider)),
            dir.resolve("resolved"),
            Some(assembly)
          )
        }

        Then("the coordinate mismatch is reported")
        error.getMessage should include("assembly-descriptor.yaml must contain declared CAR dependencies")
      }
    }
  }

  private def _consumer_descriptor(apiclass: String): String =
    s"""{"schemaVersion":"cncf.component-api.v1","component":{"name":"consumer","version":"0.1.0"},"provided":[],"required":[{"apiClass":"${apiclass}","required":true}]}"""

  private def _provider_car(path: Path, name: String, version: String, apiclass: String, abihash: String): Path = {
    val descriptor =
      s"""{"schemaVersion":"cncf.component-api.v1","component":{"name":"${name}","version":"${version}"},"provided":[{"apiClass":"${apiclass}","artifactPath":"spi/${name}-api.jar","abiHash":"${abihash}"}],"required":[]}"""
    _write_zip(path, Map("component-api-descriptor.json" -> descriptor, s"spi/${name}-api.jar" -> "api", "component/main.jar" -> "implementation"))
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try f(dir)
    finally {
      val stream = Files.walk(dir)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount)(Ordering[Int].reverse).foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path
  }

  private def _write_zip(path: Path, entries: Map[String, String]): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try entries.toVector.sortBy(_._1).foreach { case (name, content) =>
      output.putNextEntry(new ZipEntry(name))
      output.write(content.getBytes(StandardCharsets.UTF_8))
      output.closeEntry()
    } finally output.close()
    path
  }
}
