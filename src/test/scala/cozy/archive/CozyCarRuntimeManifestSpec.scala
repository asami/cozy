package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._

import cozy.compatibility.{
  CarMetadataCompatibility,
  CncfRuntimeCompatibility,
  MavenCoordinate
}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

final class CozyCarRuntimeManifestSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "CNCF CAR runtime manifest packaging" should {
    "preserve runtime range and exact archive bytes across generated versions" in {
      Given("generated CAR coordinates and arbitrary packaged component bytes")
      val versions = for {
        patch <- Gen.choose(0, 999)
        suffix <- Gen.oneOf("", "-SNAPSHOT")
        content <- Gen.nonEmptyListOf(Gen.alphaNumChar)
      } yield (s"0.5.${patch}${suffix}", content.mkString)

      When("Cozy writes CNCF-owned runtime admission evidence")
      val property = Prop.forAll(versions) { case (cncfversion, content) =>
        _with_temp_dir { root =>
          val component = root.resolve("component/main.jar")
          Files.createDirectories(component.getParent)
          Files.writeString(component, content, StandardCharsets.UTF_8)
          val abi = root.resolve("abi-manifest.json")
          Files.writeString(abi, """{"format":"cozy.car.abi-manifest.v1"}""")
          CozyCarRuntimeManifest.write(
            root,
            _contract(cncfversion),
            "sample",
            "0.0.1-SNAPSHOT",
            "sample"
          )
          val manifest = Json.parse(
            Files.readString(
              root.resolve(CozyCarRuntimeManifest.FILE_NAME),
              StandardCharsets.UTF_8
            )
          )
          val entries = (manifest \ "integrity" \ "entries")
            .as[Vector[play.api.libs.json.JsObject]]
            .map { entry =>
              (entry \ "path").as[String] -> (entry \ "sha256").as[String]
            }
            .toMap
          (manifest \ "runtime" \ "cncf" \ "minimum").as[String] == cncfversion &&
          (manifest \ "runtime" \ "cncf" \ "tested").as[Vector[String]] == Vector(cncfversion) &&
          entries.keySet == Set("abi-manifest.json", "component/main.jar") &&
          entries("component/main.jar") == _sha256(content.getBytes(StandardCharsets.UTF_8))
        }
      }

      Then("at least fifty generated contracts retain their range and digest evidence")
      val result = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(50),
        property
      )
      result.passed shouldBe true
    }

    "admit package bytes and reject any post-package modification" in {
      _with_temp_dir { root =>
        Given("one package-generated CAR runtime manifest and descriptor")
        val staged = root.resolve("staged")
        _write(staged.resolve("component/main.jar"), "main")
        _write(
          staged.resolve("component-descriptor.json"),
          """{
            |  "name": "sample",
            |  "version": "0.0.1-SNAPSHOT",
            |  "component": "sample"
            |}
            |""".stripMargin
        )
        val contract = _contract("0.5.17")
        CozyCarRuntimeManifest.write(
          staged,
          contract,
          "sample",
          "0.0.1-SNAPSHOT",
          "sample"
        )
        val validarchive = _zip(staged, root.resolve("valid.car"))

        When("publication admission evaluates the unchanged archive")
        noException should be thrownBy CozyCarRuntimeManifest.requireValidArchive(
          validarchive,
          contract,
          "sample",
          "0.0.1-SNAPSHOT",
          "sample",
          expectedGenerationProvenance = None
        )

        And("component bytes are changed without regenerating the manifest")
        _write(staged.resolve("component/main.jar"), "tampered")
        val tamperedarchive = _zip(staged, root.resolve("tampered.car"))
        val error = intercept[Throwable] {
          CozyCarRuntimeManifest.requireValidArchive(
            tamperedarchive,
            contract,
            "sample",
            "0.0.1-SNAPSHOT",
            "sample",
            expectedGenerationProvenance = None
          )
        }

        Then("publication rejects the digest contradiction")
        error.getMessage should include(
          "car-runtime-manifest.json digest mismatch for component/main.jar"
        )
      }
    }
  }

  private def _contract(cncfversion: String): CarMetadataCompatibility.Contract =
    CarMetadataCompatibility.Contract(
      cozyVersion = org.simplemodeling.cozy.BuildInfo.version,
      cncfCompileCoordinate = s"org.goldenport::goldenport-cncf:${cncfversion}",
      cncfCompileTarget = MavenCoordinate(
        "org.goldenport",
        "goldenport-cncf",
        cncfversion
      ),
      runtimeCompatibility = CncfRuntimeCompatibility(
        Some(cncfversion),
        None,
        Vector.empty,
        Vector(cncfversion)
      )
    )

  private def _with_temp_dir[A](body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-car-runtime-manifest")
    try body(root)
    finally _delete_tree(root)
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).
      map(byte => f"${byte & 0xff}%02x").mkString

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path
  }

  private def _zip(root: Path, archive: Path): Path = {
    val output = new ZipOutputStream(Files.newOutputStream(archive))
    try {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala.
          filter(Files.isRegularFile(_)).
          toVector.
          sortBy(_.toString).
          foreach { path =>
            val relative = root.relativize(path).toString.replace('\\', '/')
            output.putNextEntry(new ZipEntry(relative))
            output.write(Files.readAllBytes(path))
            output.closeEntry()
          }
      } finally {
        stream.close()
      }
    } finally {
      output.close()
    }
    archive
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
