package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoEffectsSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video visual effects" should {
    "expand renderer-neutral profiles" which {
      "preserve role and primitive order through video inspect" in {
        _with_temp_dir("inspect") { dir =>
          val project = _write_project(dir, """visual-effects:
            |  section-start: line-sweep
            |  summary: overview-and-conclusion
            |  final-page: end-card
            |""".stripMargin)
          Given("a video project with all three initial visual-effect profiles")

          When("Cozy inspects the renderer-neutral profile expansion")
          val result = CozyVideo.inspect(
            CozyVideo.InspectConfig(project, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )

          Then("each role expands to a deterministic primitive sequence")
          result should include_text("section-start: line-sweep => flow-line(direction=left-to-right) -> underline-sweep")
          result should include_text("summary: overview-and-conclusion => summary-layout(mode=single-page) -> fade-rise(target=overview) -> spring-pop(target=conclusion)")
          result should include_text("final-page: end-card => end-card -> fade-rise(target=end-card) -> hold(seconds=2.0)")
          result should include_text_in_order("section-start:", "summary:", "final-page:")

          And("the Remotion adapter declares the primitives it consumes")
          result should include_text("visualEffectRenderer: remotion")
          result should include_text("visualEffectCapability: supported")
          result should not(include_text("unsupportedVisualEffectPrimitives:"))
        }
      }

      "treat an explicit none profile as an empty supported expansion" in {
        Given("all visual-effect roles explicitly disabled")
        val expansions = CozyVideoEffects.expand(Some(CozyVideoEffects.Settings(
          Some("none"),
          Some("none"),
          Some("none")
        )))

        When("Cozy checks renderer capability")
        val capability = CozyVideoEffects.capability("simple-java2d", expansions)

        Then("the renderer has no unsupported primitive work")
        expansions should have_effect_displays("none", "none", "none")
        capability should support_all_effects
      }
    }

    "enforce profile and capability diagnostics" which {
      "reject an unknown authored profile while loading the project" in {
        _with_temp_dir("unknown-profile") { dir =>
          val project = _write_project(dir, """visual-effects:
            |  summary: spinning-summary
            |""".stripMargin)
          Given("a video descriptor with an unknown summary profile")

          When("Cozy loads the descriptor for inspection")
          val error = intercept[RuntimeException] {
            CozyVideo.inspect(
              CozyVideo.InspectConfig(project, checkTools = false),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }

          Then("the diagnostic lists the role and available profiles")
          error.getMessage should include_text("Unknown summary visual-effect profile: spinning-summary")
          error.getMessage should include_text("Available profiles: none, overview-and-conclusion")
        }
      }

      "stop rendering before invoking a renderer that lacks primitive capabilities" in {
        _with_temp_dir("renderer-capability") { dir =>
          val project = _write_project(dir, """visual-effects:
            |  section-start: line-sweep
            |""".stripMargin)
          val runner = CozyVideoSpec.RecordingRunner()
          Given("a simple-java2d project whose requested primitives are not implemented by the adapter")

          When("Cozy attempts to render the project")
          val error = intercept[RuntimeException] {
            CozyVideo.render(
              CozyVideo.RenderConfig(project, "simple-java2d"),
              CozyVideo.VideoToolRegistry(Vector.empty),
              runner
            )
          }

          Then("rendering fails before tools run and names every unsupported primitive")
          error.getMessage should include_text("Video renderer simple-java2d does not support visual-effect primitives: flow-line, underline-sweep")
          runner should have_no_recorded_commands
        }
      }
    }
  }

  private def _write_project(dir: Path, effects: String): Path = {
    val project = dir.resolve("video.yaml")
    _write(dir.resolve("script.yaml"), """title: Effects
      |scenes:
      |  - id: scene
      |    narration: Effect scene
      |    duration: 1.0
      |""".stripMargin)
    _write(
      project,
      s"""name: effects
         |title: Effects
         |renderer:
         |  engine: remotion
         |${effects}parts:
         |  - id: scene
         |    type: dialogue
         |    script: script.yaml
         |""".stripMargin
    )
    project
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().
      resolve("target/test-generated/video-effects").resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _write(path: Path, contents: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, contents, StandardCharsets.UTF_8)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).iterator().asScala.toVector.reverse.foreach(Files.delete)

  protected final def include_text_in_order(expected: String*): Matcher[String] = Matcher { actual =>
    val positions = expected.map(actual.indexOf)
    val matches = positions.forall(_ >= 0) && positions.sliding(2).forall {
      case Seq(left, right) => left < right
      case _ => true
    }
    MatchResult(
      matches,
      s"text did not include ${expected.mkString(", ")} in order",
      s"text included ${expected.mkString(", ")} in order"
    )
  }

  protected final def have_effect_displays(expected: String*): Matcher[Vector[CozyVideoEffects.Expansion]] = Matcher { actual =>
    val displays = actual.map(_.display)
    MatchResult(
      displays == expected,
      s"effect displays $displays did not equal $expected",
      s"effect displays $displays equaled $expected"
    )
  }

  protected final def support_all_effects: Matcher[CozyVideoEffects.Capability] = Matcher { actual =>
    MatchResult(
      actual.isSupported,
      s"renderer ${actual.renderer} did not support: ${actual.unsupported.mkString(", ")}",
      s"renderer ${actual.renderer} supported all effects"
    )
  }

  protected final def have_no_recorded_commands: Matcher[CozyVideoSpec.RecordingRunner] = Matcher { actual =>
    MatchResult(
      actual.commands.isEmpty,
      s"runner recorded commands: ${actual.commands.mkString(", ")}",
      "runner recorded no commands"
    )
  }
}
