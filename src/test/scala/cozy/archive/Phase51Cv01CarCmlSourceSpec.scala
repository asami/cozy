package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class Phase51Cv01CarCmlSourceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Cozy CV-01 CAR source inventory" should {
    "register collection identity" in {
      Given("a CAR project with a real CML source and declared project identity")
      _with_temp_dir("cozy-phase51-cv01-collection") { projectdir =>
        _write_project(projectdir)
        val source = _write_cml(projectdir.resolve("src/main/cozy/sample.cml"))

        When("the production CAR CML resolver selects the source")
        val resolved = CarCmlSourceResolver.resolve(projectdir, "sample")

        Then("the later identity stage owns exact collection identity")
        resolved.map(_.source) shouldBe Right(source)
        cancel("CI-01 owns exact collection identity")
      }
    }
  }

  private def _write_project(projectdir: Path): Path =
    _write(projectdir.resolve("project.yaml"), "project:\n  name: sample\n")

  private def _write_cml(path: Path): Path =
    _write(path, "# COMPONENT\n\n## Sample\n")

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try body(dir)
    finally _delete(dir)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
