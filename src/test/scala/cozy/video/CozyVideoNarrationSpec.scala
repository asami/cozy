package cozy.video

import java.io.ByteArrayOutputStream
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
        val malformed = Vector(
          "a numeric value" -> "42",
          "a blank string" -> "\"  \"",
          "a null value" -> "null"
        )
        malformed.foreach { case (condition, invalid) =>
          Given(s"canonical provider authoring with $condition")
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

      "normalizes provider PCM WAV into the shared Cozy audio contract" in {
        Given("a VOICEVOX-compatible provider response using 22050 Hz stereo PCM")
        _with_script(
          """{
            |  "narration": {"provider": "voicevox"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          val voicevox = RecordingVoicevoxClient(audioBytes = _wav_bytes(0.2, 22050, 2, Vector(16384, 0)))

          When("the shared narration pipeline writes scene and combined audio")
          CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")),
            voicevox
          )
          val sceneformat = _wav_format(output.resolve("01-intro.wav"))
          val combinedformat = _wav_format(output.resolve("script.wav"))
          val scenesamples = _wav_pcm16_samples(output.resolve("01-intro.wav"))
          val entry = _manifest_entry(output)

          Then("the stereo samples are mixed and resampled to 24 kHz mono 16-bit PCM")
          sceneformat shouldBe (24000, 1, 16)
          combinedformat shouldBe (24000, 1, 16)
          scenesamples.size shouldBe 4800
          scenesamples.head shouldBe 8192
          scenesamples.last shouldBe 8192
          entry.hcursor.downField("sampleRate").as[Int].toOption shouldBe Some(24000)
          entry.hcursor.downField("channels").as[Int].toOption shouldBe Some(1)
          entry.hcursor.downField("bitsPerSample").as[Int].toOption shouldBe Some(16)
        }
      }

      "rejects malformed provider WAV boundaries" in {
        val valid = _wav_bytes(0.2, 22050, 2)
        val malformed = Vector(
          "a negative chunk size" -> _with_int_le(valid, 16, -1),
          "a short fmt chunk" -> _with_int_le(valid, 16, 8),
          "a negative sample rate" -> _with_int_le(valid, 24, -1)
        )

        malformed.foreach { case (condition, audio) =>
          Given(s"a provider response with $condition")
          _with_script(
            """{
              |  "narration": {"provider": "voicevox"},
              |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
              |}""".stripMargin
          ) { (script, output) =>
            When("the shared narration pipeline validates the provider response")
            val error = intercept[RuntimeException] {
              CozyVideo.synthesize(
                CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")),
                RecordingVoicevoxClient(audioBytes = audio)
              )
            }

            Then("the malformed WAV is rejected explicitly instead of entering conversion")
            error.getMessage should include_text("Invalid WAV file")
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

  private def _wav_format(path: Path): (Int, Int, Int) = {
    val bytes = Files.readAllBytes(path)
    (_read_int_le(bytes, 24), _read_short_le(bytes, 22), _read_short_le(bytes, 34))
  }

  private def _wav_bytes(
    duration: Double,
    samplerate: Int,
    channels: Int,
    channelsamples: Vector[Int] = Vector.empty
  ): Array[Byte] = {
    val frames = math.max(1, (duration * samplerate).toInt)
    val samples = if (channelsamples.isEmpty) Vector.fill(channels)(0) else channelsamples
    require(samples.size == channels)
    val data = new ByteArrayOutputStream()
    (0 until frames).foreach { _ =>
      samples.foreach(sample => _write_short_le(data, sample))
    }
    val out = new ByteArrayOutputStream()
    _write_ascii(out, "RIFF")
    _write_int_le(out, 36 + data.size())
    _write_ascii(out, "WAVE")
    _write_ascii(out, "fmt ")
    _write_int_le(out, 16)
    _write_short_le(out, 1)
    _write_short_le(out, channels)
    _write_int_le(out, samplerate)
    _write_int_le(out, samplerate * channels * 2)
    _write_short_le(out, channels * 2)
    _write_short_le(out, 16)
    _write_ascii(out, "data")
    _write_int_le(out, data.size())
    out.write(data.toByteArray)
    out.toByteArray
  }

  private def _wav_pcm16_samples(path: Path): Vector[Int] = {
    val bytes = Files.readAllBytes(path)
    val datasize = _read_int_le(bytes, 40)
    (44 until (44 + datasize) by 2).map(offset => _read_short_le(bytes, offset).toShort.toInt).toVector
  }

  private def _with_int_le(source: Array[Byte], offset: Int, value: Int): Array[Byte] = {
    val bytes = source.clone()
    bytes(offset) = (value & 0xff).toByte
    bytes(offset + 1) = ((value >>> 8) & 0xff).toByte
    bytes(offset + 2) = ((value >>> 16) & 0xff).toByte
    bytes(offset + 3) = ((value >>> 24) & 0xff).toByte
    bytes
  }

  private def _read_int_le(bytes: Array[Byte], offset: Int): Int =
    (bytes(offset) & 0xff) |
      ((bytes(offset + 1) & 0xff) << 8) |
      ((bytes(offset + 2) & 0xff) << 16) |
      ((bytes(offset + 3) & 0xff) << 24)

  private def _read_short_le(bytes: Array[Byte], offset: Int): Int =
    (bytes(offset) & 0xff) | ((bytes(offset + 1) & 0xff) << 8)

  private def _write_ascii(out: ByteArrayOutputStream, value: String): Unit =
    out.write(value.getBytes(StandardCharsets.US_ASCII))

  private def _write_int_le(out: ByteArrayOutputStream, value: Int): Unit = {
    out.write(value & 0xff)
    out.write((value >>> 8) & 0xff)
    out.write((value >>> 16) & 0xff)
    out.write((value >>> 24) & 0xff)
  }

  private def _write_short_le(out: ByteArrayOutputStream, value: Int): Unit = {
    out.write(value & 0xff)
    out.write((value >>> 8) & 0xff)
  }
}
