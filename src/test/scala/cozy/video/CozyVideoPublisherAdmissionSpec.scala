package cozy.video

import cozy.CozySpecVocabulary
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

final class CozyVideoPublisherAdmissionSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy video artifact admission" should {
    "reuse equal main and sidecar bytes without replacing either destination" in {
      _with_temp_dir("equal-artifacts") { dir =>
        Given("equal direct main and sidecar source and destination files")
        val source = dir.resolve("source/final.mp4")
        val sidecarsource = dir.resolve("source/final.manifest.json")
        val target = dir.resolve("repository/final.mp4")
        val sidecartarget = dir.resolve("repository/final.manifest.json")
        _write(source, "main-equal")
        _write(sidecarsource, "sidecar-equal")
        _write(target, "main-equal")
        _write(sidecartarget, "sidecar-equal")
        val mainidentity = _file_identity(target)
        val sidecaridentity = _file_identity(sidecartarget)

        When("artifact admission runs without force")
        CozyVideoPublisher.admitPublication(_prepared(source, target, Vector(sidecarsource -> sidecartarget), force = false))

        Then("the equal destinations retain their bytes and filesystem identities")
        _read(target) shouldBe "main-equal"
        _read(sidecartarget) shouldBe "sidecar-equal"
        _file_identity(target) shouldBe mainidentity
        _file_identity(sidecartarget) shouldBe sidecaridentity
      }
    }

    "reject a differing main before changing any existing artifact without force" in {
      _with_temp_dir("different-main") { dir =>
        Given("differing main and sidecar destinations")
        val source = dir.resolve("source/final.mp4")
        val sidecarsource = dir.resolve("source/final.manifest.json")
        val target = dir.resolve("repository/final.mp4")
        val sidecartarget = dir.resolve("repository/final.manifest.json")
        _write(source, "new-main")
        _write(sidecarsource, "new-sidecar")
        _write(target, "old-main")
        _write(sidecartarget, "old-sidecar")

        When("artifact admission is attempted without force")
        val error = intercept[RuntimeException] {
          CozyVideoPublisher.admitPublication(_prepared(source, target, Vector(sidecarsource -> sidecartarget), force = false))
        }

        Then("the admission fails before either destination changes")
        error.getMessage should include("differs")
        _read(target) shouldBe "old-main"
        _read(sidecartarget) shouldBe "old-sidecar"
      }
    }

    "reject a differing sidecar before rewriting an equal main without force" in {
      _with_temp_dir("different-sidecar") { dir =>
        Given("an equal main and a differing existing sidecar")
        val source = dir.resolve("source/final.mp4")
        val sidecarsource = dir.resolve("source/final.manifest.json")
        val target = dir.resolve("repository/final.mp4")
        val sidecartarget = dir.resolve("repository/final.manifest.json")
        _write(source, "equal-main")
        _write(sidecarsource, "new-sidecar")
        _write(target, "equal-main")
        _write(sidecartarget, "old-sidecar")
        val mainidentity = _file_identity(target)

        When("artifact admission is attempted without force")
        val error = intercept[RuntimeException] {
          CozyVideoPublisher.admitPublication(_prepared(source, target, Vector(sidecarsource -> sidecartarget), force = false))
        }

        Then("the equal main is untouched when the sidecar prevents admission")
        error.getMessage should include("differs")
        _read(target) shouldBe "equal-main"
        _file_identity(target) shouldBe mainidentity
        _read(sidecartarget) shouldBe "old-sidecar"
      }
    }

    "stage every forced replacement before the first final installation" in {
      _with_temp_dir("forced-replacement") { dir =>
        Given("differing main and sidecar source and destination files")
        val source = dir.resolve("source/final.mp4")
        val sidecarsource = dir.resolve("source/final.manifest.json")
        val target = dir.resolve("repository/final.mp4")
        val sidecartarget = dir.resolve("repository/final.manifest.json")
        _write(source, "new-main")
        _write(sidecarsource, "new-sidecar")
        _write(target, "old-main")
        _write(sidecartarget, "old-sidecar")

        When("forced admission reaches the after-staging callback")
        CozyVideoPublisher.admitPublication(
          _prepared(source, target, Vector(sidecarsource -> sidecartarget), force = true),
          () => {
            _read(target) shouldBe "old-main"
            _read(sidecartarget) shouldBe "old-sidecar"
            _publisher_temporaries(target.getParent).size shouldBe 2
          }
        )

        Then("both destinations are atomically replaced and no temporary remains")
        _read(target) shouldBe "new-main"
        _read(sidecartarget) shouldBe "new-sidecar"
        _publisher_temporaries(target.getParent) shouldBe empty
      }
    }

    "clean staged artifacts when the after-staging callback fails" in {
      _with_temp_dir("callback-failure") { dir =>
        Given("differing existing artifacts awaiting forced replacement")
        val source = dir.resolve("source/final.mp4")
        val sidecarsource = dir.resolve("source/final.manifest.json")
        val target = dir.resolve("repository/final.mp4")
        val sidecartarget = dir.resolve("repository/final.manifest.json")
        _write(source, "new-main")
        _write(sidecarsource, "new-sidecar")
        _write(target, "old-main")
        _write(sidecartarget, "old-sidecar")

        When("the after-staging callback fails")
        val error = intercept[RuntimeException] {
          CozyVideoPublisher.admitPublication(
            _prepared(source, target, Vector(sidecarsource -> sidecartarget), force = true),
            () => throw new RuntimeException("stop before installation")
          )
        }

        Then("all destinations remain unchanged and temporary files are cleaned")
        error.getMessage shouldBe "stop before installation"
        _read(target) shouldBe "old-main"
        _read(sidecartarget) shouldBe "old-sidecar"
        _publisher_temporaries(target.getParent) shouldBe empty
      }
    }
  }

  private def _prepared(
    source: Path,
    target: Path,
    sidecars: Vector[(Path, Path)],
    force: Boolean
  ): CozyVideoPublisher.PreparedPublication =
    CozyVideoPublisher.PreparedPublication(
      CozyVideoPublisher.PublishVideoResult(null, null, null, target, null),
      source,
      sidecars,
      force
    )

  private def _file_identity(path: Path): (AnyRef, java.nio.file.attribute.FileTime) = {
    val attributes = Files.readAttributes(path, classOf[BasicFileAttributes])
    attributes.fileKey() -> attributes.lastModifiedTime()
  }

  private def _publisher_temporaries(parent: Path): Vector[Path] = {
    val stream = Files.list(parent)
    try {
      stream.iterator().asScala.toVector.filter(_.getFileName.toString.contains(".cozy-video."))
    } finally {
      stream.close()
    }
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/video-admission").resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
