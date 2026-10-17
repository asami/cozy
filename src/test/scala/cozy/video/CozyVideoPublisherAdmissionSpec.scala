package cozy.video

import cozy.CozySpecVocabulary
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 5, 2026
 *  version Aug. 14, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */

final class CozyVideoPublisherAdmissionSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy video artifact admission" should {
    "replace selected main and sidecars after the before-install callback" in {
      _with_temp_dir("direct-replacement") { dir =>
        Given("differing main and sidecar source and destination files")
        val source = dir.resolve("source/final.mp4")
        val sidecarsource = dir.resolve("source/final.manifest.json")
        val target = dir.resolve("repository/final.mp4")
        val sidecartarget = dir.resolve("repository/final.manifest.json")
        _write(source, "new-main")
        _write(sidecarsource, "new-sidecar")
        _write(target, "old-main")
        _write(sidecartarget, "old-sidecar")

        When("selected artifact admission reaches its before-install callback")
        CozyVideoPublisher.admitPublication(
          _prepared(source, target, Vector(sidecarsource -> sidecartarget)),
          () => {
            _read(target) shouldBe "old-main"
            _read(sidecartarget) shouldBe "old-sidecar"
          }
        )

        Then("both selected destinations receive their respective source bytes")
        _read(target) shouldBe "new-main"
        _read(sidecartarget) shouldBe "new-sidecar"
      }
    }

    "report callback failure before any selected artifact copy" in {
      _with_temp_dir("callback-failure") { dir =>
        Given("differing existing artifacts selected for direct copying")
        val source = dir.resolve("source/final.mp4")
        val sidecarsource = dir.resolve("source/final.manifest.json")
        val target = dir.resolve("repository/final.mp4")
        val sidecartarget = dir.resolve("repository/final.manifest.json")
        _write(source, "new-main")
        _write(sidecarsource, "new-sidecar")
        _write(target, "old-main")
        _write(sidecartarget, "old-sidecar")

        When("the before-install callback fails")
        val error = intercept[RuntimeException] {
          CozyVideoPublisher.admitPublication(
            _prepared(source, target, Vector(sidecarsource -> sidecartarget)),
            () => throw new RuntimeException("stop before installation")
          )
        }

        Then("the callback error is reported before either destination is written")
        error.getMessage shouldBe "stop before installation"
        _read(target) shouldBe "old-main"
        _read(sidecartarget) shouldBe "old-sidecar"
      }
    }
  }

  private def _prepared(
    source: Path,
    target: Path,
    sidecars: Vector[(Path, Path)]
  ): CozyVideoPublisher.PreparedPublication =
    CozyVideoPublisher.PreparedPublication(
      CozyVideoPublisher.PublishVideoResult(null, null, null, target, null),
      source,
      sidecars
    )

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
