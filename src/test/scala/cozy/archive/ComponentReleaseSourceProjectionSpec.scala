package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.collection.JavaConverters._

final class ComponentReleaseSourceProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _public_policy = ComponentReleaseSourceProjection.Policy("public", "Apache-2.0")
  private val _restricted_policy = ComponentReleaseSourceProjection.Policy("restricted", "Apache-2.0")
  private val _evidence = ComponentReleaseSourceProjection.BuildEvidence(
    Vector("-deprecation"),
    Vector("-source:future"),
    Vector("org.example:generator:1.0.0"),
    Vector("org.simplemodeling:cozy:0.3.3-SNAPSHOT", "backend:cozy")
  )

  "ComponentReleaseSourceProjection" should {
    "DOC03-SOURCE-RELEASE-AC-01 emit canonical public entries and normalized managed sources" in {
      _with_temp_dir("cozy-release-source-public") { root =>
        Given("a project with authored files and current main/test managed Scala sources")
        _write(root.resolve("src/main/scala/PublicApi.scala"), "final class PublicApi\n")
        _write(root.resolve("src/main/scala/Generated.class"), "class bytes\n")
        _write(root.resolve("src/main/.env.local"), "secret\n")
        _write(root.resolve("README.md"), "# Public\n")
        val mainroot = root.resolve("target/scala-3/src_managed/main")
        val testroot = root.resolve("target/scala-3/src_managed/test")
        val main = _write(mainroot.resolve("fixture/Generated.scala"), "final class Generated\n")
        val test = _write(testroot.resolve("fixture/GeneratedSpec.scala"), "final class GeneratedSpec\n")
        When("the public release-source stage is created")
        val stage = ComponentReleaseSourceProjection.stage(
          root,
          root.resolve("target/cozy/release-source"),
          _public_policy,
          Vector(main), Vector(mainroot), Vector(test), Vector(testroot), _evidence
        )
        Then("the stage contains the canonical exact public projection and verifies against the same inputs")
        val manifest = Json.parse(Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME), StandardCharsets.UTF_8))
        val paths = (manifest \ "entries").as[Vector[play.api.libs.json.JsObject]].map(value => (value \ "path").as[String])
        paths shouldBe paths.sorted
        paths should contain allOf (
          "README.md",
          "src/main/scala/PublicApi.scala",
          "generated-source/main/fixture/Generated.scala",
          "generated-source/test/fixture/GeneratedSpec.scala"
        )
        paths should not contain "src/main/scala/Generated.class"
        paths should not contain "src/main/.env.local"
        Files.readString(stage.resolve("generated-source/main/fixture/Generated.scala"), StandardCharsets.UTF_8) shouldBe
          "final class Generated\n"
        val generatedentry = (manifest \ "entries").as[Vector[play.api.libs.json.JsObject]].find(value => (value \ "path").as[String] == "generated-source/main/fixture/Generated.scala").get
        (generatedentry \ "sha256").as[String] shouldBe _sha256("final class Generated\n")
        Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME), StandardCharsets.UTF_8) should not include root.toAbsolutePath.normalize().toString
        ComponentReleaseSourceProjection.verify(
          stage, root, _public_policy,
          Vector(main), Vector(mainroot), Vector(test), Vector(testroot), _evidence
        ).entries.map(_.path) shouldBe paths
      }
    }

    "DOC03-SOURCE-RELEASE-AC-02 reject hostile, escaping, duplicate, and transient managed inputs" in {
      _with_temp_dir("cozy-release-source-hostile") { root =>
        Given("a project and managed-source inputs that include unsafe or escaping paths")
        _write(root.resolve("src/main/scala/PublicApi.scala"), "final class PublicApi\n")
        val managedroot = root.resolve("managed")
        Files.createDirectories(managedroot)
        val outside = _write(root.getParent.resolve("Outside.scala"), "outside\n")
        When("an escaping managed source is staged")
        val escaping = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.stage(
            root, root.resolve("target/cozy/release-source"), _public_policy,
            Vector(outside), Vector(managedroot), Vector.empty, Vector.empty, _evidence
          )
        }
        Then("an escaping managed source is rejected")
        escaping.getMessage should include("escapes")
        val left = _write(root.resolve("left/Same.scala"), "left\n")
        val right = _write(root.resolve("right/Same.scala"), "right\n")
        When("duplicate managed source paths are staged")
        val duplicate = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.stage(
            root, root.resolve("target/cozy/release-source"), _public_policy,
            Vector(left, right), Vector(root.resolve("left"), root.resolve("right")),
            Vector.empty, Vector.empty, _evidence
          )
        }
        Then("duplicate managed source paths are rejected")
        duplicate.getMessage should include("duplicated")
        val link = root.resolve("managed/Link.scala")
        Files.createDirectories(link.getParent)
        Files.createSymbolicLink(link, root.resolve("src/main/scala/PublicApi.scala"))
        When("a symbolic-link managed source is staged")
        val symlink = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.stage(
            root, root.resolve("target/cozy/release-source"), _public_policy,
            Vector(link), Vector(managedroot), Vector.empty, Vector.empty, _evidence
          )
        }
        Then("the projection rejects every unsafe managed-source form")
        symlink.getMessage should include("symbolic link")
      }
    }

    "DOC03-SOURCE-RELEASE-AC-03 represent restricted source as manifest-only" in {
      _with_temp_dir("cozy-release-source-restricted") { root =>
        Given("a project with private authored source and a restricted policy")
        _write(root.resolve("src/main/scala/PrivateApi.scala"), "final class PrivateApi\n")
        When("the restricted release-source stage is created and verified")
        val stage = ComponentReleaseSourceProjection.stage(
          root, root.resolve("target/cozy/release-source"), _restricted_policy,
          Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
        )
        Then("restricted staging contains only its canonical manifest")
        val files = Files.walk(stage)
        val actual = try files.iterator().asScala.filter(Files.isRegularFile(_)).toVector
        finally files.close()
        actual.map(_.getFileName.toString) shouldBe Vector(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME)
        val manifest = Json.parse(Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME), StandardCharsets.UTF_8))
        (manifest \ "policy" \ "mode").as[String] shouldBe "restricted"
        (manifest \ "entries").as[Vector[play.api.libs.json.JsObject]] shouldBe Vector.empty
        ComponentReleaseSourceProjection.verify(
          stage, root, _restricted_policy,
          Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
        ).entries shouldBe Vector.empty
      }
    }

    "DOC03-SOURCE-RELEASE-AC-04 reject repeated stale and tampered stages" in {
      _with_temp_dir("cozy-release-source-strict") { root =>
        Given("a public stage built from the current authored source")
        val source = _write(root.resolve("src/main/scala/PublicApi.scala"), "final class PublicApi\n")
        When("the stage is verified repeatedly, then the authored source changes, then staged bytes are tampered")
        val stage = ComponentReleaseSourceProjection.stage(
          root, root.resolve("target/cozy/release-source"), _public_policy,
          Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
        )
        ComponentReleaseSourceProjection.verify(
          stage, root, _public_policy, Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
        )
        val repeated = ComponentReleaseSourceProjection.verify(
          stage, root, _public_policy, Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
        )
        _write(source, "final class Changed\n")
        val stale = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.verify(
            stage, root, _public_policy, Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
          )
        }
        _write(source, "final class PublicApi\n")
        _write(stage.resolve("src/main/scala/PublicApi.scala"), "tampered\n")
        val tampered = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.verify(
            stage, root, _public_policy, Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
          )
        }
        Then("stale current inputs and tampered staged bytes are rejected")
        stale.getMessage should include("digest")
        tampered.getMessage should include("digest")
        repeated.entries should not be empty
      }
    }

    "DOC03-SOURCE-RELEASE-AC-05 reject stale managed inputs and extra staged entries at package time" in {
      _with_temp_dir("cozy-release-source-managed-freshness") { root =>
        Given("a public stage containing current authored and managed Scala source inputs")
        _write(root.resolve("src/main/scala/PublicApi.scala"), "final class PublicApi\n")
        val managedroot = root.resolve("target/scala-3/src_managed/main")
        val managed = _write(managedroot.resolve("fixture/Generated.scala"), "final class Generated\n")
        val stage = ComponentReleaseSourceProjection.stage(
          root, root.resolve("target/cozy/release-source"), _public_policy,
          Vector(managed), Vector(managedroot), Vector.empty, Vector.empty, _evidence
        )
        When("Cozy verifies the same snapshot, then a stale source, then an extra staged entry")
        val samesnapshotentries = ComponentReleaseSourceProjection.verifyForPackaging(
          stage, root, Vector(managed), Vector(managedroot), Vector.empty, Vector.empty, _evidence
        ).entries.map(_.path)
        _write(managed, "final class ChangedGenerated\n")
        val stale = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.verifyForPackaging(
            stage, root, Vector(managed), Vector(managedroot), Vector.empty, Vector.empty, _evidence
          )
        }
        _write(managed, "final class Generated\n")
        _write(stage.resolve("generated-source/main/fixture/Fake.scala"), "fake\n")
        val extra = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.verifyForPackaging(
            stage, root, Vector(managed), Vector(managedroot), Vector.empty, Vector.empty, _evidence
          )
        }
        Then("package-time verification preserves the same snapshot and rejects stale or extra inputs")
        samesnapshotentries should contain("generated-source/main/fixture/Generated.scala")
        stale.getMessage should include("digest")
        extra.getMessage should include("entries differ")
      }
    }

    "DOC03-SOURCE-RELEASE-AC-06 reject unsafe policy and build evidence while admitting deterministic identities" in {
      _with_temp_dir("cozy-release-source-evidence-admission") { root =>
        Given("representative path, state, host, timestamp, secret, and raw-byte metadata values")
        val hostile = Vector(
          "/tmp/project",
          "src/main/scala/Foo.scala",
          "target/classes",
          ".cozy/config.yaml",
          "https://example.com",
          "localhost:8080",
          "192.168.1.1",
          "192.168.1.1:8080",
          "2001:db8::1",
          "[2001:db8::1]:8080",
          "2026-08-25T12:34:56Z",
          "password=secret",
          "AAECAwQFBgcICQoLDA0ODw=="
        )
        When("each hostile value is supplied as a policy license or build-evidence value")
        val policyerrors = hostile.map { value =>
          the[Throwable] thrownBy ComponentReleaseSourceProjection.policy("public", value)
        }
        val evidenceerrors = hostile.map { value =>
          the[Throwable] thrownBy ComponentReleaseSourceProjection.stage(
            root,
            root.resolve("target/cozy/release-source"),
            _public_policy,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            ComponentReleaseSourceProjection.BuildEvidence(Vector(value), Vector.empty, Vector.empty, Vector.empty)
          )
        }
        val admitted = ComponentReleaseSourceProjection.stage(
          root,
          root.resolve("target/cozy/release-source"),
          _public_policy,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          _evidence
        )
        val admittedverification = ComponentReleaseSourceProjection.verify(
          admitted, root, _public_policy,
          Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
        )
        Then("all hostile values are rejected")
        policyerrors.foreach(_.getMessage should include("safe license"))
        evidenceerrors.foreach(_.getMessage should include("local path"))
        ComponentReleaseSourceProjection.policy("public", "Apache-2.0") shouldBe _public_policy
        admittedverification.entries shouldBe Vector.empty
      }
    }
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val root = Files.createTempDirectory(prefix)
    try f(root)
    finally _delete_tree(root)
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _delete_tree(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }

  private def _sha256(content: String): String =
    MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
}
