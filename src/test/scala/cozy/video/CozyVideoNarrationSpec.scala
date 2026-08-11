package cozy.video

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import io.circe.{Json, parser}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Jul. 20, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoNarrationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  import CozyVideoSpec.{RecordingProbe, RecordingVoicevoxClient, StubProvider}

  "Cozy Video narration" should {
    "select a provider before synthesis" which {
      "uses canonical narration.provider and records provider provenance" in {
        Given("a script that selects the canonical VOICEVOX narration provider")
        _with_script(
          """{
            |  "narration": {"provider": "voicevox"},
            |  "voice": {"speakerName": "presenter", "styleName": "neutral"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "値とBoK"}]
            |}""".stripMargin
        ) { (script, output) =>
          When("Cozy synthesizes the shared scene pipeline through the selected provider")
          val result = CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")),
            RecordingVoicevoxClient(speakersJson = Json.arr(Json.obj(
              "name" -> Json.fromString("presenter"),
              "styles" -> Json.arr(Json.obj("name" -> Json.fromString("neutral"), "id" -> Json.fromInt(42)))
            )))
          )
          val entry = _manifest_entry(output)

          Then("the command and manifest identify the provider execution and voice")
          result should include_text("provider: voicevox")
          result should include_text("executionMode: external-http")
          entry.hcursor.downField("provider").as[String].toOption shouldBe Some("voicevox")
          entry.hcursor.downField("executionMode").as[String].toOption shouldBe Some("external-http")
          entry.hcursor.downField("voiceIdentity").as[String].toOption shouldBe Some("presenter/neutral")
          entry.hcursor.downField("voiceId").as[String].toOption shouldBe Some("42")
          entry.hcursor.downField("modelIdentity").as[String].toOption shouldBe None
        }
      }

      "does not attribute generated silence to the selected narration provider" in {
        Given("a silent title scene in a script that selects VOICEVOX for narrated scenes")
        _with_script(
          """{
            |  "narration": {"provider": "voicevox"},
            |  "voice": {"fallbackSpeakerId": 42},
            |  "scenes": [{"id": "title", "duration": 0.2, "silent": true}]
            |}""".stripMargin
        ) { (script, output) =>
          val client = RecordingVoicevoxClient()

          When("Cozy generates the scene silence without invoking the provider")
          CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")),
            client
          )
          val entry = _manifest_entry(output)

          Then("the manifest contains no false provider or voice provenance")
          client.calls shouldBe empty
          entry.hcursor.downField("provider").as[String].toOption shouldBe None
          entry.hcursor.downField("executionMode").as[String].toOption shouldBe None
          entry.hcursor.downField("voiceIdentity").as[String].toOption shouldBe None
          entry.hcursor.downField("voiceId").as[String].toOption shouldBe None
          entry.hcursor.downField("modelIdentity").as[String].toOption shouldBe None
        }
      }

      "requires explicit VOICEVOX identity for a script without provider authoring" in {
        Given("an existing script without any VOICEVOX identity")
        _with_script(
          """{"scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]}"""
        ) { (script, output) =>
          When("Cozy synthesizes it without an implicit speaker")
          val error = intercept[RuntimeException] {
            CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")), RecordingVoicevoxClient())
          }

          Then("the required caller-owned identity is explicit")
          error.getMessage should include_text("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
        }
      }

      "accepts legacy voice.engine with a deprecation diagnostic" in {
        Given("a script that still declares the old voice.engine setting")
        _with_script(
          """{
            |  "voice": {"engine": "voicevox", "fallbackSpeakerId": 42},
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

      "uses an explicit VOICEVOX fallback speaker id without directory lookup" in {
        Given("a VOICEVOX script that names only an explicit numeric speaker id")
        _with_script(
          """{"narration":{"provider":"voicevox"},"voice":{"fallbackSpeakerId":42},"scenes":[{"id":"intro","duration":0.2,"line":"Hello"}]}"""
        ) { (script, output) =>
          val client = RecordingVoicevoxClient()

          When("Cozy synthesizes the scene")
          CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")), client)
          val entry = _manifest_entry(output)

          Then("the generic id provenance is retained without speaker lookup")
          client.calls.map(_.kind) should not contain "speakers"
          entry.hcursor.downField("voiceIdentity").as[String].toOption shouldBe Some("speaker-id:42")
          entry.hcursor.downField("voiceId").as[String].toOption shouldBe Some("42")
        }
      }

      "rejects partial or absent VOICEVOX identity before provider selection" in {
        Vector(
          "speaker name only" -> "{\"speakerName\":\"presenter\"}",
          "style name only" -> "{\"styleName\":\"neutral\"}",
          "no identity" -> "{}"
        ).foreach { case (condition, voice) =>
          Given(s"a VOICEVOX script with $condition")
          _with_script(s"""{"narration":{"provider":"voicevox"},"voice":$voice,"scenes":[{"id":"intro","duration":0.2,"line":"Hello"}]}""") { (script, output) =>
            When("Cozy resolves the required caller-owned voice identity")
            val error = intercept[RuntimeException] {
              CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")), RecordingVoicevoxClient())
            }

            Then("the missing complete selection is explicit")
            error.getMessage should include_text("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
          }
        }
      }

      "fails unmatched explicit VOICEVOX names unless a caller supplies fallbackSpeakerId" in {
        Given("a configured name pair absent from the provider directory")
        _with_script("""{"narration":{"provider":"voicevox"},"voice":{"speakerName":"presenter","styleName":"neutral"},"scenes":[{"id":"intro","duration":0.2,"line":"Hello"}]}""") { (script, output) =>
          When("Cozy cannot resolve the configured pair")
          val error = intercept[RuntimeException] {
            CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")), RecordingVoicevoxClient())
          }
          Then("it fails instead of selecting an arbitrary provider style")
          error.getMessage should include_text("VOICEVOX speaker style not found: presenter/neutral")
        }

        Given("the same unmatched pair with a caller-provided fallback id")
        _with_script("""{"narration":{"provider":"voicevox"},"voice":{"speakerName":"presenter","styleName":"neutral","fallbackSpeakerId":42},"scenes":[{"id":"intro","duration":0.2,"line":"Hello"}]}""") { (script, output) =>
          When("Cozy synthesizes through the explicit fallback")
          CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")), RecordingVoicevoxClient())
          Then("configured pair provenance remains caller-owned")
          _manifest_entry(output).hcursor.downField("voiceIdentity").as[String].toOption shouldBe Some("presenter/neutral")
          _manifest_entry(output).hcursor.downField("voiceId").as[String].toOption shouldBe Some("42")
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
            |  "narration": {"provider": "espeak"},
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
          error.getMessage should include_text("Unsupported narration provider: espeak")
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
            |  "voice": {"fallbackSpeakerId": 42},
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
              |  "voice": {"fallbackSpeakerId": 42},
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

      "uses shared CLI execution settings ahead of script settings" in {
        Given("a narration script with host-mode tool defaults")
        _with_script(
          """{
            |  "tools": {"toolMode": "host", "dockerImage": "script/image:1", "voicevoxUrl": "http://script.example"},
            |  "narration": {"provider": "voicevox"},
            |  "voice": {"fallbackSpeakerId": 42},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          val voicevox = RecordingVoicevoxClient()

          When("CLI execution settings override the script defaults")
          val result = CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(
              script,
              output,
              Some("http://cli.example"),
              toolMode = Some("docker"),
              dockerImage = Some("cli/image:2")
            ),
            voicevox
          )

          Then("the resolved shared execution settings and endpoint are used")
          result should include_text("toolMode: docker")
          result should include_text("dockerImage: cli/image:2")
          voicevox.calls.map(_.baseUrl).distinct shouldBe Vector("http://cli.example")
        }
      }

      "checks the selected provider before writing output" in {
        Given("a selected VOICEVOX provider whose tool check reports it missing")
        _with_script(
          """{
            |  "narration": {"provider": "voicevox"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          val missing = CozyVideo.VideoToolCheck(
            "voicevox",
            CozyVideo.VideoToolMode.ExternalService,
            CozyVideo.VideoToolStatus.Missing,
            "VOICEVOX is unavailable.",
            Some("Start VOICEVOX.")
          )

          When("synthesis runs with tool checking enabled")
          val error = intercept[RuntimeException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(script, output, checkTools = true),
              CozyVideo.VideoToolRegistry(Vector(StubProvider(missing))),
              RecordingVoicevoxClient()
            )
          }

          Then("the selected provider failure stops synthesis before output")
          error.getMessage should include_text("External service connection unavailable for synthesize narration")
          error.getMessage should include_text("dependency=voicevox is missing;")
          error.getMessage should include_text("Start VOICEVOX")
          Files.exists(output) shouldBe false
        }
      }

      "does not probe VOICEVOX when another narration provider is selected" in {
        Given("a tool context that selects only Piper narration")
        val directory = Files.createTempDirectory("cozy-video-provider-check-spec")
        try {
          val probe = RecordingProbe()
          val project = CozyVideo.VideoProject(None, None, None, None, None, Vector.empty)
          val execution = CozyVideo.VideoExecutionConfig(
            CozyVideo.VideoToolMode.Docker,
            "toolchain/image:1",
            "http://voicevox.example"
          )
          val context = CozyVideo.VideoToolContext(directory.resolve("video.json"), directory, project, execution, Set("piper"))

          When("the VOICEVOX tool provider evaluates that plan")
          val check = CozyVideo.VoicevoxProvider(probe).check(context)

          Then("the provider is marked unselected without an HTTP probe")
          check.status shouldBe CozyVideo.VideoToolStatus.Unchecked
          check.message should include_text("not selected")
          probe.httpGets shouldBe empty
        } finally {
          _delete_tree(directory)
        }
      }

      "checks only the selected portable Piper runtime without network access" in {
        Given("a Docker tool context selecting Piper and a validated toolchain image")
        val directory = Files.createTempDirectory("cozy-video-piper-check-spec")
        try {
          val image = "textus-toolchain:0.2.1-SNAPSHOT"
          val dockercheck = Vector("docker", "version", "--format", "{{.Server.Version}}")
          val imagecheck = Vector("docker", "image", "inspect", image)
          val ttscheck = Vector("docker", "run", "--rm", "--network=none", image, "textus-toolchain", "check", "tts")
          val success = CozyVideo.VideoCommandResult(0, "ok", "")
          val probe = RecordingProbe(commandResults = Map(
            dockercheck -> success,
            imagecheck -> success,
            ttscheck -> success
          ))
          val project = CozyVideo.VideoProject(None, None, None, None, None, Vector.empty)
          val execution = CozyVideo.VideoExecutionConfig(
            CozyVideo.VideoToolMode.Docker,
            image,
            "http://voicevox.example"
          )
          val context = CozyVideo.VideoToolContext(directory.resolve("video.json"), directory, project, execution, Set("piper"))

          When("the Piper tool provider validates the selected image")
          val check = CozyVideo.PiperProvider(probe).check(context)

          Then("the offline TTS contract is available and all checks are argument vectors")
          check.status shouldBe CozyVideo.VideoToolStatus.Available
          probe.commands shouldBe Vector(dockercheck, imagecheck, ttscheck)
          ttscheck should contain("--network=none")
        } finally {
          _delete_tree(directory)
        }
      }

      "synthesizes two-character portable narration with bundled Piper models" in {
        Given("a Docker-mode Piper script with two explicitly selected model identities")
        _with_script(
          """{
            |  "narration": {"provider": "piper"},
            |  "characters": {
            |    "guide": {"voice": {"model": "en_US-ljspeech-medium"}},
            |    "reviewer": {"voice": {"model": "en_US-joe-medium"}}
            |  },
            |  "scenes": [
            |    {"id": "intro", "speaker": "guide", "duration": 0.2, "line": "Welcome"},
            |    {"id": "review", "speaker": "reviewer", "duration": 0.2, "line": "Continue"}
            |  ]
            |}""".stripMargin
        ) { (script, output) =>
          val available = CozyVideo.VideoToolCheck(
            "piper",
            CozyVideo.VideoToolMode.Docker,
            CozyVideo.VideoToolStatus.Available,
            "available"
          )
          val runner = new PiperRunner

          When("Cozy invokes each bundled model through the offline Docker command")
          val result = CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(
              script,
              output,
              checkTools = true,
              toolMode = Some("docker"),
              dockerImage = Some("textus-toolchain:0.2.1-SNAPSHOT")
            ),
            CozyVideo.VideoToolRegistry(Vector(StubProvider(available))),
            RecordingVoicevoxClient(),
            runner
          )
          val entries = parser.parse(Files.readString(output.resolve("manifest.json"))).toOption.flatMap(_.asArray).get

          Then("both model identities and the portable execution boundary are retained")
          result should include_text("provider: piper")
          result should include_text("executionMode: docker")
          runner._commands.size shouldBe 2
          runner._commands.foreach { command =>
            command.take(4) shouldBe Vector("docker", "run", "--rm", "--network=none")
            command should contain allOf ("textus-toolchain", "piper-synthesize", "--input", "--output")
            command.exists(_.startsWith("/workspace/target/cozy-video/piper/")) shouldBe true
          }
          runner._commands.head should contain("en_US-ljspeech-medium")
          runner._commands(1) should contain("en_US-joe-medium")
          entries.flatMap(_.hcursor.downField("voiceIdentity").as[String].toOption) shouldBe
            Vector("en_US-ljspeech-medium", "en_US-joe-medium")
          entries.flatMap(_.hcursor.downField("modelIdentity").as[String].toOption) shouldBe
            Vector("en_US-ljspeech-medium", "en_US-joe-medium")
          val workroot = script.getParent.resolve("target/cozy-video/piper")
          val entriesstream = Files.list(workroot)
          try entriesstream.iterator().asScala.toVector shouldBe empty
          finally entriesstream.close()
        }
      }

      "rejects portable Piper narration in host mode before output" in {
        Given("a Piper script with host tool mode")
        _with_script(
          """{
            |  "narration": {"provider": "piper"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          When("Cozy validates provider and execution mode compatibility")
          val error = intercept[RuntimeException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(script, output, toolMode = Some("host")),
              RecordingVoicevoxClient()
            )
          }

          Then("the Docker-only provider fails before creating audio output")
          error.getMessage should include_text("piper requires Docker tool mode")
          Files.exists(output) shouldBe false
        }
      }

      "synthesizes two-character macOS narration through argument-vector tools" in {
        Given("a host-mode macos-say script with Samantha and Karen characters")
        _with_script(
          """{
            |  "narration": {"provider": "macos-say"},
            |  "characters": {
            |    "guide": {"voice": {"voiceName": "Samantha", "rate": 185}},
            |    "reviewer": {"voice": {"voiceName": "Karen", "rate": 175}}
            |  },
            |  "scenes": [
            |    {"id": "intro", "speaker": "guide", "duration": 0.2, "line": "Welcome"},
            |    {"id": "review", "speaker": "reviewer", "duration": 0.2, "line": "Continue"}
            |  ]
            |}""".stripMargin
        ) { (script, output) =>
          val available = CozyVideo.VideoToolCheck(
            "macos-say",
            CozyVideo.VideoToolMode.Host,
            CozyVideo.VideoToolStatus.Available,
            "available"
          )
          val runner = new MacosSayRunner

          When("Cozy synthesizes with host mode and validates the selected provider")
          val result = CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(script, output, checkTools = true, toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector(StubProvider(available))),
            RecordingVoicevoxClient(),
            runner
          )
          val entries = parser.parse(Files.readString(output.resolve("manifest.json"))).toOption.flatMap(_.asArray).get

          Then("say and ffmpeg run as argument vectors and voice provenance is retained")
          result should include_text("provider: macos-say")
          result should include_text("executionMode: host")
          runner._commands.map(_.head) shouldBe Vector("say", "ffmpeg", "say", "ffmpeg")
          runner._commands(0) should contain allOf ("Samantha", "185", "Welcome")
          runner._commands(2) should contain allOf ("Karen", "175", "Continue")
          entries.flatMap(_.hcursor.downField("voiceIdentity").as[String].toOption) shouldBe Vector("Samantha", "Karen")
          entries.flatMap(_.hcursor.downField("modelIdentity").as[String].toOption).distinct shouldBe Vector("macos-say")
        }
      }

      "rejects macOS narration in Docker mode before output" in {
        Given("a macos-say script with Docker tool mode")
        _with_script(
          """{
            |  "narration": {"provider": "macos-say"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          When("Cozy validates provider and execution mode compatibility")
          val error = intercept[RuntimeException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(script, output, toolMode = Some("docker")),
              RecordingVoicevoxClient()
            )
          }

          Then("the incompatible mode is explicit and no output is written")
          error.getMessage should include_text("macos-say requires host tool mode")
          Files.exists(output) shouldBe false
        }
      }

      "rejects an unknown synthesis tool mode before output" in {
        Given("a VOICEVOX script with an unknown execution mode")
        _with_script(
          """{
            |  "narration": {"provider": "voicevox"},
            |  "scenes": [{"id": "intro", "duration": 0.2, "line": "Hello"}]
            |}""".stripMargin
        ) { (script, output) =>
          When("Cozy resolves the shared synthesis execution settings")
          val error = intercept[RuntimeException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(script, output, toolMode = Some("sidecar")),
              RecordingVoicevoxClient()
            )
          }

          Then("the unknown mode is rejected before audio output exists")
          error.getMessage should include_text("Invalid video tool mode: sidecar")
          Files.exists(output) shouldBe false
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
      _delete_tree(directory)
    }
  }

  private def _delete_tree(path: Path): Unit = {
    val paths = Files.walk(path)
    try paths.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
    finally paths.close()
  }

  private def _manifest_entry(output: Path) =
    parser.parse(Files.readString(output.resolve("manifest.json"), StandardCharsets.UTF_8)).toOption.flatMap(_.asArray).get.head

  private final class MacosSayRunner extends CozyVideo.VideoProcessRunner {
    val _commands = ArrayBuffer.empty[Vector[String]]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      _commands += args
      args.headOption match {
        case Some("say") =>
          val output = Path.of(args(args.indexOf("-o") + 1))
          Files.write(output, "AIFF".getBytes(StandardCharsets.US_ASCII))
        case Some("ffmpeg") =>
          Files.write(Path.of(args.last), _wav_bytes(0.2, 24000, 1, Vector(8192)))
        case _ =>
      }
      CozyVideo.VideoCommandResult(0, "ok", "")
    }
  }

  private final class PiperRunner extends CozyVideo.VideoProcessRunner {
    val _commands = ArrayBuffer.empty[Vector[String]]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      _commands += args
      val outputarg = args(args.indexOf("--output") + 1)
      val output =
        if (outputarg.startsWith("/workspace/"))
          cwd.resolve(outputarg.stripPrefix("/workspace/"))
        else
          Path.of(outputarg)
      Files.write(output, _wav_bytes(0.2, 24000, 1, Vector(8192)))
      CozyVideo.VideoCommandResult(0, "ok", "")
    }
  }

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
