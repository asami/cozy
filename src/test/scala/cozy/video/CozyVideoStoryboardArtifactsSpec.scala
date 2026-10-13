package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary
import scala.collection.JavaConverters._

/*
 * @since   Sep. 29, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardArtifactsSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video Storyboard artifacts" should {
    "preflight the selected producer destinations" which {
      "reject escaped direct input-alias hardlink and symlink destinations before production" in {
        _with_temp_dir("producer-preflight") { root =>
          Given("an input a hardlink to that input and a symbolic output ancestor")
          val input = _write(root.resolve("input.json"), "source")
          val hardlink = root.resolve("hardlink.json")
          Files.createLink(hardlink, input)
          val output = root.resolve("output")
          Files.createDirectories(output)
          val symlink = root.resolve("linked")
          Files.createSymbolicLink(symlink, output)

          When("producer preflight receives unsafe destination relations")
          val failures = Vector(
            root.getParent.resolve("escaped.mp4") -> "escapes its project root",
            input -> "aliases a declared input",
            hardlink -> "aliases a declared input",
            symlink.resolve("final.mp4") -> "unsafe ancestor"
          ).map { case (destination, diagnostic) =>
            val failure = intercept[RuntimeException] {
              CozyVideoStoryboardArtifacts.preflightProduction(root, Vector(destination), Set(input))
            }
            failure -> diagnostic
          }

          Then("each relation has its specific preflight diagnostic and the declared input remains intact")
          failures.foreach { case (failure, diagnostic) => failure.getMessage should include(diagnostic) }
          _read(input) shouldBe "source"
        }
      }

    }
  }

  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _with_temp_dir(label: String)(body: Path => Unit): Unit = {
    val parent = Path.of("target/cozy-video-storyboard-artifacts-spec/work").toAbsolutePath.normalize()
    Files.createDirectories(parent)
    val root = Files.createTempDirectory(parent, label + "-").toRealPath()
    try body(root)
    finally {
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
