package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.archive.{ComponentApiDependencyResolver, CozyComponentReleaseCoordinateCodec}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 12, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentApiDependencyResolverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Component API dependency resolver" should {
    "extract the one declared CAR API that satisfies a required contract" in {
      Given("a consumer requirement, matching provider CAR, and assembly coordinate")
      _with_temp_dir("cozy-api-dependency") { dir =>
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Api", "sha256:one")
        val assembly = _write(
          dir.resolve("assembly.yaml"),
          """subsystem: consumer
            |components:
            |  - namespace: org.example
            |    id: Provider
            |    version: 0.1.0
            |""".stripMargin
        )

        When("the dependency is resolved")
        val jars = ComponentApiDependencyResolver._resolve_dependencies(
          consumer,
          Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
          dir.resolve("resolved"),
          Some(assembly)
        )

        Then("only the contract API JAR is extracted")
        jars.map(_.getFileName.toString) shouldBe Vector("example-provider-api.jar")
        Files.readString(jars.head) shouldBe "api"
      }
    }

    "fail when no declared CAR provides a required API" in {
      Given("a required API and an unrelated provider CAR")
      _with_temp_dir("cozy-api-missing") { dir =>
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Required"))
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Other", "sha256:other")

        When("dependency matching runs")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
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
          """{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Consumer","version":"0.1.0"},"provided":[],"required":[{"apiClass":"example.Optional","required":false}]}"""
        )

        When("dependency matching runs without providers")
        val jars = ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, dir.resolve("resolved"), None)

        Then("no API JAR is required")
        jars shouldBe Vector.empty
      }
    }

    "fail when more than one CAR provides the same required API" in {
      Given("two provider CARs for one required API")
      _with_temp_dir("cozy-api-ambiguous") { dir =>
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val first = _provider_car(dir.resolve("first.car"), "org.example", "First", "0.1.0", "example.Api", "sha256:one")
        val second = _provider_car(dir.resolve("second.car"), "org.example", "Second", "0.2.0", "example.Api", "sha256:two")

        When("dependency matching runs")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(
              ComponentApiDependencyResolver.Dependency("org.example", "First", "0.1.0", first),
              ComponentApiDependencyResolver.Dependency("org.example", "Second", "0.2.0", second)
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
          """{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Consumer","version":"0.1.0"},"provided":[],"required":[{"apiClass":"example.Api","required":true,"abiHash":"sha256:expected"}]}"""
        )
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Api", "sha256:actual")

        When("dependency matching checks ABI compatibility")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
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
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Api", "sha256:one")
        val assembly = _write(dir.resolve("assembly.yaml"), "subsystem: consumer\ncomponents: []\n")

        When("dependency and assembly metadata are validated")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
            dir.resolve("resolved"),
            Some(assembly)
          )
        }

        Then("the coordinate mismatch is reported")
        error.getMessage should include("assembly-descriptor.yaml must contain declared CAR dependencies")
      }
    }

    "reject a provider CAR whose provided API release differs from its component release" in {
      Given("a provider CAR with a stale provided.version")
      _with_temp_dir("cozy-api-provider-release") { dir =>
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Api", "sha256:one", "0.0.9")

        When("the provider descriptor is admitted before API extraction")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
            dir.resolve("resolved"),
            None
          )
        }

        Then("the shared provided.version projection diagnostic is retained")
        error.getMessage should include("component.release-coordinate.projection-mismatch")
        error.getMessage should include("field=provided.version")
      }
    }

    "admit consumer identity before resolving an empty provider set" in {
      Given("malformed consumer namespace, ID, and release values with no requirements")
      val cases = Vector(
        ("""{"schemaVersion":"cncf.component-api.v2","component":{"id":"Consumer","version":"0.1.0"},"provided":[],"required":[]}""", "component.release-coordinate.mismatch"),
        ("""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"consumer","version":"0.1.0"},"provided":[],"required":[]}""", "component.identity.local-id.format"),
        ("""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Consumer"},"provided":[],"required":[]}""", "component.release-coordinate.mismatch")
      )
      _with_temp_dir("cozy-api-consumer-identity") { dir =>
        cases.zipWithIndex.foreach { case ((descriptor, code), index) =>
          val consumer = _write(dir.resolve(s"consumer-${index}.json"), descriptor)

          When("the consumer descriptor is loaded before provider selection")
          val error = intercept[Throwable] {
            ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, dir.resolve(s"resolved-${index}"), None)
          }

          Then("the shared consumer identity diagnostic is retained")
          error.getMessage should include(code)
          error.getMessage should include("consumer-descriptor:")
        }
      }
    }
  }

  "Component API dependency reader" should {
    "reject legacy or unknown consumer component identity fields before selection" in {
      _with_temp_dir("cozy-api-reader-exact-component") { dir =>
        Vector(
          "{\"name\":\"Consumer\",\"version\":\"0.1.0\"}",
          "{\"namespace\":\"org.example\",\"id\":\"Consumer\",\"version\":\"0.1.0\",\"unknown\":\"x\"}"
        ).zipWithIndex.foreach { case (component, index) =>
          val consumer = _write(dir.resolve(s"consumer-$index.json"), s"""{"schemaVersion":"cncf.component-api.v2","component":$component,"provided":[],"required":[]}""")
          intercept[Throwable] { ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, dir.resolve(s"resolved-$index"), None) }.getMessage should include("component.release-coordinate.mismatch")
        }
      }
    }
  }

  private def _consumer_descriptor(apiclass: String): String =
    s"""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Consumer","version":"0.1.0"},"provided":[],"required":[{"apiClass":"${apiclass}","required":true}]}"""

  private def _provider_car(path: Path, namespace: String, id: String, version: String, apiclass: String, abihash: String): Path =
    _provider_car(path, namespace, id, version, apiclass, abihash, version)

  private def _provider_car(path: Path, namespace: String, id: String, version: String, apiclass: String, abihash: String, providedversion: String): Path = {
    val coordinate = CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "test-provider")
    val descriptor =
      s"""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"${namespace}","id":"${id}","version":"${version}"},"provided":[{"apiClass":"${apiclass}","version":"${providedversion}","artifactPath":"${coordinate.apiArtifactPath}","abiHash":"${abihash}"}],"required":[]}"""
    _write_zip(path, Map("component-api-descriptor.json" -> descriptor, coordinate.apiArtifactPath -> "api", "component/main.jar" -> "implementation"))
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/component-api-dependency-resolver-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val dir = Files.createTempDirectory(workroot, s"$prefix-")
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
