package cozy.video

import java.io.ByteArrayOutputStream
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
final class CozyVideoScaffoldSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video Scaffold" should {
    "create a deterministic license-safe explanation package" in {
      _with_temp_dir("explanation") { dir =>
        val save = dir.resolve("domain-overview.video")
        Given("an explanation scaffold request without external media")
        val config = CozyVideoScaffold.Config.create(List(
          "domain-overview",
          "--save",
          save.toString,
          "--profile",
          "explanation"
        ))

        When("Cozy creates the video source package")
        val result = CozyVideoScaffold.scaffold(config)

        Then("the source package contains inspectable project files and generated placeholders")
        save.resolve("index.dox") should be_regular_file
        save.resolve("video.yaml") should be_regular_file
        save.resolve("script.yaml") should be_regular_file
        save.resolve("assets/section-start.svg") should be_regular_file
        save.resolve("assets/summary.svg") should be_regular_file
        save.resolve("assets/final-page.svg") should be_regular_file
        save.resolve("assets/README.md") should be_regular_file
        _read(save.resolve("video.yaml")) should include_text("profile: explanation")
        _read(save.resolve("video.yaml")) should include_text("section-start: line-sweep")
        _read(save.resolve("video.yaml")) should include_text("summary: overview-and-conclusion")
        _read(save.resolve("video.yaml")) should include_text("final-page: end-card")
        _read(save.resolve("video.yaml")) should include_text("id: explanation")
        _read(save.resolve("assets/README.md")) should include_text("does not copy or reference media")
        result should include_text("profile: explanation")

        And("the generated descriptor is accepted by the existing video inspector")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(save.resolve("video.yaml"), checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        inspection should include_text("parts: 1")
        inspection should include_text("part[1]: explanation")

        And("the same profile produces identical relative files and bytes")
        val secondsave = dir.resolve("domain-overview-second.video")
        CozyVideoScaffold.scaffold(config.copy(save = secondsave))
        _snapshot(secondsave) shouldBe _snapshot(save)
      }
    }

    "express explanation-demo-explanation as a distinct composition profile" in {
      _with_temp_dir("explanation-demo-explanation") { dir =>
        val save = dir.resolve("product-demo.video")
        Given("a scaffold request for an explanation, demo, and explanation sequence")
        val config = CozyVideoScaffold.Config.create(List(
          "product-demo.video",
          "--save=" + save,
          "--title=Product Demo"
        ))

        When("Cozy creates the default composition")
        CozyVideoScaffold.scaffold(config)

        Then("the project has introduction, web demo, and conclusion parts")
        val project = _read(save.resolve("video.yaml"))
        project should include_text("profile: explanation-demo-explanation")
        project should include_text("id: introduction")
        project should include_text("id: demonstration")
        project should include_text("type: web-demo")
        project should include_text("script: demo-script.yaml")
        project should include_text("steps: demo-steps.json")
        project should include_text("id: conclusion")
        project should include_text("script: summary-script.yaml")
        save.resolve("demo-steps.json") should be_regular_file
        save.resolve("demo-script.yaml") should be_regular_file
        save.resolve("summary-script.yaml") should be_regular_file

        And("the existing inspector observes all profile parts")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(save.resolve("video.yaml"), checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        inspection should include_text("parts: 3")
        inspection should include_text("part[2]: demonstration")
      }
    }

    "honor independent visual-effect profile settings through the CLI" in {
      _with_temp_dir("cli") { dir =>
        val save = dir.resolve("minimal.video")
        Given("a CLI scaffold request that disables each optional visual effect")

        When("the Cozy CLI creates the package")
        val result = _capture {
          cozy.Cozy.main(Array(
            "video",
            "scaffold",
            "minimal",
            "--save=" + save,
            "--profile=explanation",
            "--section-start-effect=none",
            "--summary-effect=none",
            "--final-page-effect=none"
          ))
        }

        Then("the selected composition and effect profiles are persisted")
        result should include_text("Cozy Video Scaffold")
        val project = _read(save.resolve("video.yaml"))
        project should include_text("profile: explanation")
        project should include_text("section-start: none")
        project should include_text("summary: none")
        project should include_text("final-page: none")

        And("the command is documented by CLI help")
        _capture(cozy.Cozy.main(Array("--help"))) should include_text("video scaffold <slug>")
      }
    }

    "reject unknown profiles and existing destinations explicitly" in {
      _with_temp_dir("diagnostics") { dir =>
        Given("an unknown composition profile")
        val profileerror = intercept[RuntimeException] {
          CozyVideoScaffold.Config.create(List(
            "diagnostic",
            "--save=" + dir.resolve("diagnostic.video"),
            "--profile=unknown"
          ))
        }
        Then("Cozy lists the supported composition profiles")
        profileerror.getMessage should include_text("Unknown video composition profile: unknown")
        profileerror.getMessage should include_text("explanation-demo-explanation")

        Given("an unknown section-start visual-effect profile")
        val effecterror = intercept[RuntimeException] {
          CozyVideoScaffold.Config.create(List(
            "diagnostic",
            "--save=" + dir.resolve("effect.video"),
            "--section-start-effect=unknown"
          ))
        }
        Then("Cozy names the invalid visual role and its available profiles")
        effecterror.getMessage should include_text("Unknown section-start visual-effect profile: unknown")
        effecterror.getMessage should include_text("line-sweep, none")

        Given("a slug that could escape the package naming contract")
        val slugerror = intercept[RuntimeException] {
          CozyVideoScaffold.Config.create(List("../diagnostic"))
        }
        Then("Cozy rejects the slug before creating files")
        slugerror.getMessage should include_text("Use lowercase kebab-case")

        Given("an existing package destination")
        val save = dir.resolve("existing.video")
        Files.createDirectories(save)
        val config = CozyVideoScaffold.Config.create(List("existing", "--save=" + save))
        When("the scaffold would overwrite that destination")
        val destinationerror = intercept[RuntimeException] {
          CozyVideoScaffold.scaffold(config)
        }
        Then("Cozy stops without changing the existing package")
        destinationerror.getMessage should include_text("Video scaffold destination already exists")
      }
    }
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().
      resolve("target/test-generated/video-scaffold").resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _snapshot(root: Path): Vector[(String, Vector[Byte])] =
    Files.walk(root).iterator().asScala.toVector.
      filter(Files.isRegularFile(_)).
      sortBy(_.toString).
      map(path => root.relativize(path).toString -> Files.readAllBytes(path).toVector)

  private def _capture(body: => Unit): String = {
    val out = new ByteArrayOutputStream()
    Console.withOut(out)(body)
    out.toString(StandardCharsets.UTF_8.name())
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).iterator().asScala.toVector.reverse.foreach(Files.delete)
}
