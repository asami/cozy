package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoAssetsSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video assets" should {
    "resolve portable local contracts" which {
      "preserve generated placeholder license and provenance metadata" in {
        _with_temp_dir("scaffold-placeholder") { dir =>
          val save = dir.resolve("placeholder.video")
          Given("a default video scaffold with generated placeholder files")
          CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
            "placeholder",
            "--save",
            save.toString
          )))

          When("Cozy inspects the scaffold asset contract")
          val descriptor = _read(save.resolve("video.yaml"))
          val inspection = _inspect(save.resolve("video.yaml"))

          Then("each slot carries machine-readable generated asset provenance")
          descriptor should include_text("license: LicenseRef-Cozy-Generated-Placeholder")
          descriptor should include_text("provenance: cozy:video-scaffold")
          inspection should include_text("section-start: configured path=assets/section-start.svg kind=placeholder required=false")
          inspection should include_text("license: LicenseRef-Cozy-Generated-Placeholder")
          inspection should include_text("provenance: cozy:video-scaffold")
        }
      }

      "resolve a configured project-owned asset with its attribution" in {
        _with_temp_dir("configured") { dir =>
          _write(dir.resolve("assets/custom-summary.svg"), "<svg/>")
          val project = _write_project(dir, """assets:
            |  summary:
            |    path: assets/custom-summary.svg
            |    kind: project-owned
            |    required: true
            |    license: CC-BY-4.0
            |    provenance: https://assets.example.test/summary
            |""".stripMargin)
          Given("a required project-owned summary asset with explicit attribution")

          When("Cozy resolves the local asset")
          val inspection = _inspect(project)

          Then("the configured file and attribution are visible without copying media")
          inspection should include_text("summary: configured path=assets/custom-summary.svg kind=project-owned required=true")
          inspection should include_text("license: CC-BY-4.0")
          inspection should include_text("provenance: https://assets.example.test/summary")
        }
      }

      "fall back to a generated placeholder for an unconfigured optional slot" in {
        _with_temp_dir("fallback") { dir =>
          _write(dir.resolve("assets/summary.svg"), "<svg/>")
          val project = _write_project(dir, """assets:
            |  summary:
            |    path: assets/missing-summary.png
            |    required: false
            |""".stripMargin)
          Given("an optional configured summary file that is absent and a generated placeholder")

          When("Cozy resolves the asset slot")
          val inspection = _inspect(project)

          Then("the placeholder is selected and the requested path remains diagnostic evidence")
          inspection should include_text("summary: fallback path=assets/summary.svg kind=placeholder required=false")
          inspection should include_text("requestedPath: assets/missing-summary.png")
          inspection should include_text("license: LicenseRef-Cozy-Generated-Placeholder")
        }
      }
    }

    "protect deterministic asset resolution" which {
      "reject a missing required configured asset" in {
        _with_temp_dir("missing-required") { dir =>
          val project = _write_project(dir, """assets:
            |  final-page:
            |    path: assets/required-end-card.png
            |    required: true
            |""".stripMargin)
          Given("a required final-page asset that does not exist")

          When("Cozy plans the video")
          val error = intercept[RuntimeException](_inspect(project))

          Then("planning identifies the required slot and local path")
          error.getMessage should include_text("Missing or unreadable required video asset final-page")
          error.getMessage should include_text("assets/required-end-card.png")
        }
      }

      "reject URL and project-root escape paths without network access" in {
        _with_temp_dir("non-local") { dir =>
          val urlproject = _write_project(dir, """assets:
            |  section-start: https://assets.example.test/opening.png
            |""".stripMargin)
          Given("an asset slot configured with an external URL")

          When("Cozy plans the URL-backed project")
          val urlerror = intercept[RuntimeException](_inspect(urlproject))

          Then("the local-only contract rejects the URL before any fetch")
          urlerror.getMessage should include_text("Video assets must be project-local files, not URLs")

          val escapeproject = _write_project(dir, """assets:
            |  section-start: ../outside.png
            |""".stripMargin)
          And("an asset slot configured outside the project root")

          When("Cozy plans the escaping path")
          val escapeerror = intercept[RuntimeException](_inspect(escapeproject))

          Then("the portable source package boundary rejects the escaping path")
          escapeerror.getMessage should include_text("Video asset escapes the project root")
        }
      }
    }
  }

  private def _inspect(project: Path): String =
    CozyVideo.inspect(
      CozyVideo.InspectConfig(project, checkTools = false),
      CozyVideo.VideoToolRegistry(Vector.empty)
    )

  private def _write_project(dir: Path, assets: String): Path = {
    val project = dir.resolve("video.yaml")
    _write(dir.resolve("script.yaml"), """title: Assets
      |scenes:
      |  - id: scene
      |    narration: Asset scene
      |    duration: 1.0
      |""".stripMargin)
    _write(
      project,
      s"""name: assets
         |title: Assets
         |${assets}parts:
         |  - id: scene
         |    type: dialogue
         |    script: script.yaml
         |""".stripMargin
    )
    project
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().
      resolve("target/test-generated/video-assets").resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _write(path: Path, contents: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, contents, StandardCharsets.UTF_8)
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).iterator().asScala.toVector.reverse.foreach(Files.delete)
}
