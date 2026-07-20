package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import io.circe.parser
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoNarrationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  import CozyVideoSpec.RecordingVoicevoxClient

  "Cozy Video narration" should {
    "select a provider before synthesis" which {
      "uses canonical narration.provider and records provider provenance" in {
        Given("a script that selects the canonical VOICEVOX narration provider")
        _with_script(
          """{
            |  "narration": {"provider": "voicevox"},
            |  "voice": {"speakerName": "ずんだもん", "styleName": "ノーマル"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "値とBoK"}]
            |}""".stripMargin
        ) { (script, output) =>
          When("Cozy synthesizes the shared scene pipeline through the selected provider")
          val result = CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")),
            RecordingVoicevoxClient()
          )
          val entry = _manifest_entry(output)

          Then("the command and manifest identify the provider execution and voice")
          result should include_text("provider: voicevox")
          result should include_text("executionMode: external-http")
          entry.hcursor.downField("provider").as[String].toOption shouldBe Some("voicevox")
          entry.hcursor.downField("executionMode").as[String].toOption shouldBe Some("external-http")
          entry.hcursor.downField("voiceIdentity").as[String].toOption shouldBe Some("ずんだもん/ノーマル")
          entry.hcursor.downField("voiceId").as[String].toOption shouldBe Some("3")
          entry.hcursor.downField("modelIdentity").as[String].toOption shouldBe None
        }
      }

      "keeps VOICEVOX as the default for an existing script" in {
        Given("an existing script without provider authoring")
        _with_script(
          """{"scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]}"""
        ) { (script, output) =>
          When("Cozy synthesizes it without migration metadata")
          val result = CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")),
            RecordingVoicevoxClient()
          )

          Then("VOICEVOX remains the provider without a deprecation diagnostic")
          result should include_text("provider: voicevox")
          result should not(include_text("warning:"))
        }
      }

      "accepts legacy voice.engine with a deprecation diagnostic" in {
        Given("a script that still declares the old voice.engine setting")
        _with_script(
          """{
            |  "voice": {"engine": "voicevox"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          When("Cozy reads the compatibility input")
          val result = CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")),
            RecordingVoicevoxClient()
          )

          Then("the script works and reports the canonical replacement")
          result should include_text("provider: voicevox")
          result should include_text("Deprecated voice.engine authoring detected; use narration.provider instead.")
        }
      }

      "rejects conflicting canonical and legacy providers before output" in {
        Given("canonical and compatibility settings that select different providers")
        _with_script(
          """{
            |  "narration": {"provider": "piper"},
            |  "voice": {"engine": "voicevox"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          When("Cozy validates provider selection")
          val error = intercept[RuntimeException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(script, output),
              RecordingVoicevoxClient()
            )
          }

          Then("the conflict is explicit and no output directory is created")
          error.getMessage should include_text("Conflicting narration provider settings")
          Files.exists(output) shouldBe false
        }
      }

      "rejects an unavailable provider before output" in {
        Given("a canonical provider that is not implemented in this runtime")
        _with_script(
          """{
            |  "narration": {"provider": "piper"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          When("Cozy resolves the provider implementation")
          val error = intercept[RuntimeException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(script, output),
              RecordingVoicevoxClient()
            )
          }

          Then("the unsupported provider is reported before files are written")
          error.getMessage should include_text("Unsupported narration provider: piper")
          Files.exists(output) shouldBe false
        }
      }

      "rejects malformed canonical provider authoring before output" in {
        Given("explicit provider settings that are not non-empty strings")
        Vector("42", "\"  \"", "null").foreach { invalid =>
          _with_script(
            s"""{
               |  "narration": {"provider": $invalid},
               |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
               |}""".stripMargin
          ) { (script, output) =>
            When("Cozy validates canonical provider authoring")
            val error = intercept[RuntimeException] {
              CozyVideo.synthesize(
                CozyVideo.SynthesizeConfig(script, output),
                RecordingVoicevoxClient()
              )
            }

            Then("the malformed value is reported without falling back to VOICEVOX")
            error.getMessage should include_text("narration.provider must be a non-empty string")
            Files.exists(output) shouldBe false
          }
        }
      }
    }
  }

  private def _with_script(text: String)(body: (Path, Path) => Unit): Unit = {
    val directory = Files.createTempDirectory("cozy-video-narration-spec")
    try {
      val script = directory.resolve("script.json")
      Files.writeString(script, text, StandardCharsets.UTF_8)
      body(script, directory.resolve("audio"))
    } finally {
      Files.walk(directory).iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
    }
  }

  private def _manifest_entry(output: Path) =
    parser.parse(Files.readString(output.resolve("manifest.json"), StandardCharsets.UTF_8)).toOption.flatMap(_.asArray).get.head
}
