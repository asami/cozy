package cozy.video

import cozy.CozySpecVocabulary
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardNarrationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  import CozyVideoSpec.{RecordingVoicevoxClient, StubProvider}

  "Cozy Video native Storyboard narration" should {
    "selection and projection" which {
      "D-NARR-001 parse an explicit part in both spellings while retaining the six-field no-part construction" in {
      Given("a project path, declared audio directory, and legacy six-field synthesis construction")
        _with_target("parse") { root =>
        val project = root.resolve("video.json")
        val audio = root.resolve("build/audio/story")
        val nopart = CozyVideo.SynthesizeConfig(project, audio, None, false, None, None)

        When("Cozy parses the two explicit --part spellings")
        val spaced = CozyVideo.SynthesizeConfig.create(List(project.toString, "--save", audio.toString, "--part", "story"))
        val equals = CozyVideo.SynthesizeConfig.create(List(project.toString, "--save=" + audio, "--part=story"))

        Then("both spellings select the declared part and legacy construction remains no-part")
        spaced.part shouldBe Some("story")
        equals.part shouldBe Some("story")
        nopart.part shouldBe None
      }
      }

      "D-NARR-002 synthesize only the selected native Storyboard section with source provenance" in {
      Given("a typed Storyboard project with a declared selected audio directory")
      _with_fixture("selected") { fixture =>
        val provider = RecordingVoicevoxClient()

        When("Cozy synthesizes the explicitly selected native part")
        CozyVideo.synthesize(_config(fixture), provider)
        val entries = _manifest_entries(fixture.audio)

        Then("the ordered scene ids narration WAVs manifest and combined source basename follow the Storyboard")
        entries.map(_.hcursor.get[String]("sceneId").toOption) shouldBe Vector(Some("opening"), Some("detail"))
        provider.calls.collect { case call if call.kind == "audio_query" => call.text } shouldBe Vector(Some("OpeningCozy"), Some("DetailCozy"))
        Files.isRegularFile(fixture.audio.resolve("01-opening.wav")) shouldBe true
        Files.isRegularFile(fixture.audio.resolve("02-detail.wav")) shouldBe true
        Files.isRegularFile(fixture.audio.resolve("storyboard.wav")) shouldBe true
        Files.isRegularFile(fixture.audio.resolve("manifest.json")) shouldBe true
      }
      }

      "D-NARR-003 excludes unselected sections and retains projected voice pronunciation and normalization" in {
      Given("a Storyboard whose selected section has a character voice and pronunciation note")
      _with_fixture("projection", section = Some("selected"), includelegacy = true) { fixture =>
        val provider = RecordingVoicevoxClient()

        When("Cozy projects and synthesizes only the selected section without a legacy script source")
        CozyVideo.synthesize(_config(fixture), provider)

        Then("only selected projected narration reaches the provider with its character voice and normalization")
        provider.calls.collect { case call if call.kind == "audio_query" => call.text } shouldBe Vector(Some("Selectedコージー"))
        provider.calls.collect { case call if call.kind == "audio_query" => call.speakerId } shouldBe Vector(Some(8))
        _manifest_entries(fixture.audio).map(_.hcursor.get[String]("sceneId").toOption) shouldBe Vector(Some("selected"))
      }
      }

      "D-NARR-004 uses descriptor provider configuration while CLI overrides it and tool checks see the real project" in {
      Given("a native Storyboard descriptor with a provider URL and a recording tool checker")
      _with_fixture("provider-config") { fixture =>
        val recorder = new RecordingToolProvider
        val descriptorprovider = RecordingVoicevoxClient()

        When("Cozy runs the part route with descriptor settings and then an explicit CLI URL")
        CozyVideo.synthesize(_config(fixture, checktools = true), CozyVideo.VideoToolRegistry(Vector(recorder)), descriptorprovider)
        val overrideprovider = RecordingVoicevoxClient()
        CozyVideo.synthesize(_config(fixture, voicevoxurl = Some("http://cli.example")), overrideprovider)

        Then("CLI wins over project configuration and the checker receives the real project boundary")
        descriptorprovider.calls.head.baseUrl shouldBe "http://descriptor.example"
        overrideprovider.calls.head.baseUrl shouldBe "http://cli.example"
        recorder.contexts.map(_.projectFile) shouldBe Vector(fixture.project)
        recorder.contexts.map(_.projectRoot) shouldBe Vector(fixture.root)
        recorder.contexts.flatMap(_.narrationProviders) shouldBe Vector("voicevox")
      }
      }
    }

    "pre-start failures" which {
    "D-NARR-005 rejects blank unknown duplicate non-native and invalid native selection before provider or output mutation" in {
      Given("project fixtures with invalid explicit part selection or source admission")
      _with_fixture("selection-failures") { fixture =>
        _write(fixture.audio.resolve("prior.wav"), "prior")
        val before = Files.readAllBytes(fixture.audio.resolve("prior.wav"))
        val modified = Files.getLastModifiedTime(fixture.audio.resolve("prior.wav"))
        val provider = RecordingVoicevoxClient()

        When("Cozy receives blank and unknown part identifiers")
        val blank = intercept[RuntimeException] { CozyVideo.synthesize(_config(fixture, part = " "), provider) }
        val unknown = intercept[RuntimeException] { CozyVideo.synthesize(_config(fixture, part = "unknown"), provider) }

        Then("selection fails before a provider call or previous audio mutation")
        blank.getMessage should include_text("nonblank exact declared")
        unknown.getMessage should include_text("Unknown declared video part id")
        provider.calls shouldBe empty
        Files.readAllBytes(fixture.audio.resolve("prior.wav")).toVector shouldBe before.toVector
        Files.getLastModifiedTime(fixture.audio.resolve("prior.wav")) shouldBe modified
      }

      Given("descriptors with non-native script-only missing invalid symbolic-link or escaping native Storyboard sources")
      _with_target("source-failures") { root =>
        val provider = RecordingVoicevoxClient()
        val nonnative = _write_project(root.resolve("non-native"), "storyboard.md", "story", "dialogue", Some("legacy.json"))
        _write(nonnative.getParent.resolve("legacy.json"), "{\"voice\":{\"fallbackSpeakerId\":7},\"scenes\":[]}")
        val scriptonly = _write_project(root.resolve("script-only"), "storyboard.md", "story", "storyboard", Some("legacy.json"))
        _write(scriptonly.getParent.resolve("legacy.json"), "{\"voice\":{\"fallbackSpeakerId\":7},\"scenes\":[]}")
        val missing = _write_project(root.resolve("missing"), "missing.md", "story")
        val invalid = _write_project(root.resolve("invalid"), "invalid.yaml", "story")
        _write(invalid.getParent.resolve("invalid.yaml"), "not a supported Storyboard representation")
        val linkedroot = root.resolve("linked")
        val linked = _write_project(linkedroot, "linked.json", "story")
        _write(linkedroot.resolve("outside.json"), "{}")
        Files.createSymbolicLink(linkedroot.resolve("linked.json"), linkedroot.resolve("outside.json"))
        val escaping = _write_project(root.resolve("escaping"), "../outside.json", "story")
        val invalidsectionroot = root.resolve("invalid-section")
        val invalidsection = _write_project(invalidsectionroot, "storyboard.json", "story", section = Some("missing"))
        _write(invalidsectionroot.resolve("storyboard.json"), CozyVideo.canonicalStoryboardJson(CozyVideo.Storyboard(
          "cozy.video.storyboard.v1",
          1,
          Vector(_scene("opening", 1, "available", "narrator", "Opening Cozy"))
        )))
        val duplicate = root.resolve("duplicate/video.json")
        _write(duplicate, "{\"parts\":[{\"id\":\"story\",\"type\":\"storyboard\",\"storyboard\":\"missing.md\"},{\"id\":\"story\",\"type\":\"storyboard\",\"storyboard\":\"missing.md\"}]}")

        val webdemoroot = root.resolve("web-demo")
        val webdemo = _write_project(webdemoroot, "storyboard.json", "story", "web-demo")
        _write(webdemoroot.resolve("storyboard.json"), CozyVideo.canonicalStoryboardJson(CozyVideo.Storyboard(
          "cozy.video.storyboard.v1",
          1,
          Vector(_scene("opening", 1, "available", "narrator", "Opening Cozy"))
        )))

        When("Cozy selects each invalid declared route")
        val nonnativefailure = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(nonnative, nonnative.getParent.resolve("build/audio/story"), part = Some("story")), provider) }
        val scriptonlyfailure = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(scriptonly, scriptonly.getParent.resolve("build/audio/story"), part = Some("story")), provider) }
        val missingfailure = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(missing, missing.getParent.resolve("build/audio/story"), part = Some("story")), provider) }
        val invalidfailure = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(invalid, invalid.getParent.resolve("build/audio/story"), part = Some("story")), provider) }
        val linkedfailure = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(linked, linked.getParent.resolve("build/audio/story"), part = Some("story")), provider) }
        val escapingfailure = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(escaping, escaping.getParent.resolve("build/audio/story"), part = Some("story")), provider) }
        val invalidsectionfailure = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(invalidsection, invalidsection.getParent.resolve("build/audio/story"), part = Some("story")), provider) }
        val duplicatefailure = intercept[io.circe.DecodingFailure] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(duplicate, duplicate.getParent.resolve("build/audio/story"), part = Some("story")), provider) }
        val webdemofailure = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(webdemo, webdemo.getParent.resolve("build/audio/story"), part = Some("story")), provider) }

        Then("non-native script-only missing invalid linked escaping and duplicate declarations stop before output")
        nonnativefailure.getMessage should include_text("requires a native storyboard")
        scriptonlyfailure.getMessage should include_text("requires a native storyboard")
        missingfailure.getMessage should include_text("source is missing or invalid")
        invalidfailure.getMessage should not be empty
        linkedfailure.getMessage should include_text("symbolic link")
        escapingfailure.getMessage should include_text("Traversal outside project root")
        invalidsectionfailure.getMessage should include_text("selects no scenes")
        duplicatefailure.getMessage should include_text("Duplicate effective video part id")
        webdemofailure.getMessage should include_text("requires a native storyboard")
        provider.calls shouldBe empty
        Files.exists(nonnative.getParent.resolve("build/audio/story")) shouldBe false
        Files.exists(scriptonly.getParent.resolve("build/audio/story")) shouldBe false
        Files.exists(missing.getParent.resolve("build/audio/story")) shouldBe false
        Files.exists(invalid.getParent.resolve("build/audio/story")) shouldBe false
        Files.exists(linked.getParent.resolve("build/audio/story")) shouldBe false
        Files.exists(escaping.getParent.resolve("build/audio/story")) shouldBe false
        Files.exists(invalidsection.getParent.resolve("build/audio/story")) shouldBe false
        Files.exists(duplicate.getParent.resolve("build/audio/story")) shouldBe false
        Files.exists(webdemo.getParent.resolve("build/audio/story")) shouldBe false
      }
    }

    "D-NARR-006 rejects mismatched root escaping and symbolic or nonregular output targets before provider work" in {
      Given("a declared native audio directory")
      _with_fixture("output-failures") { fixture =>
        val provider = RecordingVoicevoxClient()

        When("Cozy receives a mismatched root or escaping --save path")
        val mismatch = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(fixture.project, fixture.root.resolve("other"), part = Some("story")), provider) }
        val root = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(fixture.project, fixture.root, part = Some("story")), provider) }
        val escape = intercept[RuntimeException] { CozyVideo.synthesize(CozyVideo.SynthesizeConfig(fixture.project, fixture.root.resolve("../escape").normalize(), part = Some("story")), provider) }

        Then("all directory substitutions fail before provider activity")
        mismatch.getMessage should include_text("exactly equal")
        root.getMessage should include_text("exactly equal")
        escape.getMessage should include_text("exactly equal")
        provider.calls shouldBe empty
        Files.exists(fixture.root.resolve("other/manifest.json")) shouldBe false
        Files.exists(fixture.root.resolve("manifest.json")) shouldBe false
        Files.exists(fixture.root.resolve("../escape/manifest.json").normalize()) shouldBe false
      }

      Given("a fixture whose audio directory is an existing symbolic link")
      _with_fixture("audio-link") { fixture =>
        val outside = fixture.root.resolve("outside")
        Files.createDirectories(outside)
        val prior = outside.resolve("prior.wav")
        _write(prior, "prior")
        val before = Files.readAllBytes(prior)
        val modified = Files.getLastModifiedTime(prior)
        Files.createDirectories(fixture.audio.getParent)
        Files.createSymbolicLink(fixture.audio, outside)
        val provider = RecordingVoicevoxClient()
        When("Cozy preflights the symbolic audio directory")
        val failure = intercept[RuntimeException] { CozyVideo.synthesize(_config(fixture), provider) }
        Then("an existing audio-directory symbolic link is rejected before output")
        failure.getMessage should include_text("symbolic-link path")
        provider.calls shouldBe empty
        Files.exists(outside.resolve("manifest.json")) shouldBe false
        Files.readAllBytes(prior).toVector shouldBe before.toVector
        Files.getLastModifiedTime(prior) shouldBe modified
      }
      Given("a fixture whose generated WAV is an existing symbolic link")
      _with_fixture("output-link") { fixture =>
        Files.createDirectories(fixture.audio)
        val outside = fixture.root.resolve("outside.wav")
        _write(outside, "outside")
        val before = Files.readAllBytes(outside)
        val modified = Files.getLastModifiedTime(outside)
        Files.createSymbolicLink(fixture.audio.resolve("01-opening.wav"), outside)
        val provider = RecordingVoicevoxClient()
        When("Cozy preflights the symbolic generated WAV")
        val failure = intercept[RuntimeException] { CozyVideo.synthesize(_config(fixture), provider) }
        Then("an existing generated WAV symbolic link is rejected before output")
        failure.getMessage should include_text("symbolic-link or nonregular target")
        provider.calls shouldBe empty
        Files.isSymbolicLink(fixture.audio.resolve("01-opening.wav")) shouldBe true
        Files.readAllBytes(outside).toVector shouldBe before.toVector
        Files.getLastModifiedTime(outside) shouldBe modified
      }
      Given("a fixture whose manifest target is an existing directory")
      _with_fixture("output-directory") { fixture =>
        Files.createDirectories(fixture.audio.resolve("manifest.json"))
        val prior = fixture.audio.resolve("manifest.json/prior.wav")
        _write(prior, "prior")
        val before = Files.readAllBytes(prior)
        val modified = Files.getLastModifiedTime(prior)
        val provider = RecordingVoicevoxClient()
        When("Cozy preflights the nonregular manifest target")
        val failure = intercept[RuntimeException] { CozyVideo.synthesize(_config(fixture), provider) }
        Then("an existing generated nonregular target is rejected before output")
        failure.getMessage should include_text("symbolic-link or nonregular target")
        provider.calls shouldBe empty
        Files.isDirectory(fixture.audio.resolve("manifest.json")) shouldBe true
        Files.readAllBytes(prior).toVector shouldBe before.toVector
        Files.getLastModifiedTime(prior) shouldBe modified
      }
    }

    "D-NARR-007 preserves prior audio when required provider checks fail while checkTools false remains ordinary" in {
      Given("a selected native Storyboard part, previous audio, and an unavailable provider check")
      _with_fixture("tool-check") { fixture =>
        _write(fixture.audio.resolve("prior.wav"), "prior")
        val before = Files.readAllBytes(fixture.audio.resolve("prior.wav"))
        val modified = Files.getLastModifiedTime(fixture.audio.resolve("prior.wav"))
        val unavailable = CozyVideo.VideoToolRegistry(Vector(StubProvider(
          CozyVideo.VideoToolCheck("voicevox", CozyVideo.VideoToolMode.ExternalService, CozyVideo.VideoToolStatus.Missing, "unavailable")
        )))
        val blockedprovider = RecordingVoicevoxClient()

        When("checkTools preflights the unavailable selected provider")
        val failure = intercept[RuntimeException] { CozyVideo.synthesize(_config(fixture, checktools = true), unavailable, blockedprovider) }

        Then("the failure preserves prior audio and makes no provider call")
        failure.getMessage should include_text("External service connection unavailable")
        blockedprovider.calls shouldBe empty
        Files.readAllBytes(fixture.audio.resolve("prior.wav")).toVector shouldBe before.toVector
        Files.getLastModifiedTime(fixture.audio.resolve("prior.wav")) shouldBe modified

        Given("the selected provider with no matching tool check")
        val missing = CozyVideo.VideoToolRegistry(Vector(StubProvider(
          CozyVideo.VideoToolCheck("unrelated", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Available, "available")
        )))
        val missingprovider = RecordingVoicevoxClient()

        When("checkTools preflights without a selected provider check")
        val missingfailure = intercept[RuntimeException] { CozyVideo.synthesize(_config(fixture, checktools = true), missing, missingprovider) }

        Then("the missing provider check preserves prior audio and makes no provider call")
        missingfailure.getMessage should include_text("External service connection unavailable")
        missingfailure.getMessage should include_text("No tool check is registered for provider voicevox")
        missingprovider.calls shouldBe empty
        Files.readAllBytes(fixture.audio.resolve("prior.wav")).toVector shouldBe before.toVector
        Files.getLastModifiedTime(fixture.audio.resolve("prior.wav")) shouldBe modified

        When("checktools is false for the same ordinary provider route")
        val ordinaryprovider = RecordingVoicevoxClient()
        CozyVideo.synthesize(_config(fixture), unavailable, ordinaryprovider)

        Then("normal provider synthesis remains available without a requested tool preflight")
        ordinaryprovider.calls should not be empty
        Files.isRegularFile(fixture.audio.resolve("manifest.json")) shouldBe true
      }
    }
    }

    "generated correspondence" which {
    "D-NARR-008 preserves generated valid selected-section order through provider and manifest correspondence" in {
      Given("ScalaCheck-generated valid scene identifiers and narration")
      val generated = Gen.listOfN(3, Gen.choose(1, 16).flatMap(length => Gen.listOfN(length, Gen.alphaChar).map(_.mkString))).map(_.toVector.zipWithIndex.map {
        case (text, index) => (s"scene-${index + 1}", text)
      })

      When("ScalaCheck synthesizes selected native Storyboard sections")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(12), Prop.forAll(generated) { scenes =>
        _with_fixture("property-" + scenes.map(_._1).mkString("-"), selectedscenes = scenes) { fixture =>
          val provider = RecordingVoicevoxClient()
          CozyVideo.synthesize(_config(fixture), provider)
          val manifestids = _manifest_entries(fixture.audio).flatMap(_.hcursor.get[String]("sceneId").toOption)
          val providertexts = provider.calls.collect { case call if call.kind == "audio_query" => call.text.getOrElse("") }
          manifestids == scenes.map(_._1).toVector && providertexts == scenes.map(_._2).toVector
        }
      })

      Then("each generated projection retains its selected order in provider calls and manifest evidence")
      result.passed shouldBe true
    }
    }
  }

  private final case class NarrationFixture(root: Path, project: Path, audio: Path)

  private final class RecordingToolProvider extends CozyVideo.VideoToolProvider {
    val contexts = ArrayBuffer.empty[CozyVideo.VideoToolContext]

    def check(context: CozyVideo.VideoToolContext): CozyVideo.VideoToolCheck = {
      contexts += context
      CozyVideo.VideoToolCheck("voicevox", CozyVideo.VideoToolMode.ExternalService, CozyVideo.VideoToolStatus.Available, "available")
    }
  }

  private def _config(
    fixture: NarrationFixture,
    part: String = "story",
    checktools: Boolean = false,
    voicevoxurl: Option[String] = None
  ): CozyVideo.SynthesizeConfig =
    CozyVideo.SynthesizeConfig(fixture.project, fixture.audio, voicevoxurl, checktools, None, None, Some(part))

  private def _with_fixture[A](
    name: String,
    section: Option[String] = None,
    includelegacy: Boolean = false,
    selectedscenes: Vector[(String, String)] = Vector("opening" -> "Opening Cozy", "detail" -> "Detail Cozy")
  )(body: NarrationFixture => A): A =
    _with_target(name) { root =>
      val source = root.resolve("storyboard.json")
      val scenes =
        if (section.isDefined)
          Vector(_scene("selected", 1, section.get, "guide", "Selected Cozy"), _scene("unselected", 2, "other", "narrator", "Unselected"))
        else
          selectedscenes.zipWithIndex.map { case ((id, text), index) => _scene(id, index + 1, "all", "narrator", text) }
      _write(source, CozyVideo.canonicalStoryboardJson(CozyVideo.Storyboard("cozy.video.storyboard.v1", 1, scenes)))
      val project = _write_project(root, "storyboard.json", "story", "storyboard", None, section, includelegacy)
      body(NarrationFixture(root, project, root.resolve("build/audio/story")))
    }

  private def _scene(id: String, order: Int, section: String, speaker: String, narration: String): CozyVideo.StoryboardScene =
    CozyVideo.StoryboardScene(
      id,
      order,
      section,
      speaker,
      "narration",
      narration,
      CozyVideo.StoryboardScreen(id, narration),
      narration,
      BigDecimal("1"),
      BigDecimal("0"),
      "cut",
      Vector.empty,
      Vector.empty,
      Vector.empty,
      if (speaker == "guide") Vector(CozyVideo.StoryboardPronunciationNote("Cozy", "コージー")) else Vector.empty,
      narration
    )

  private def _write_project(
    root: Path,
    source: String,
    id: String,
    parttype: String = "storyboard",
    script: Option[String] = None,
    section: Option[String] = None,
    includelegacy: Boolean = false
  ): Path = {
    val project = root.resolve("video.json")
    val sourcefield = script.map(value => "script" -> Json.fromString(value)).getOrElse("storyboard" -> Json.fromString(source))
    val partfields = Vector(
      "id" -> Json.fromString(id),
      "type" -> Json.fromString(parttype),
      sourcefield,
      "audioDir" -> Json.fromString("build/audio/story")
    ) ++ section.map(value => Vector("storyboardSection" -> Json.fromString(value))).getOrElse(Vector.empty)
    val parts = Vector(Json.obj(partfields: _*)) ++
      (if (includelegacy)
         Vector(Json.obj(
           "id" -> Json.fromString("legacy"),
           "type" -> Json.fromString("dialogue"),
           "script" -> Json.fromString("legacy.json")
         ))
       else Vector.empty)
    _write(project, Json.obj(
      "title" -> Json.fromString("Storyboard narration fixture"),
      "tools" -> Json.obj(
        "toolMode" -> Json.fromString("host"),
        "voicevoxUrl" -> Json.fromString("http://descriptor.example")
      ),
      "narration" -> Json.obj("provider" -> Json.fromString("voicevox")),
      "voice" -> Json.obj("fallbackSpeakerId" -> Json.fromInt(7)),
      "voiceTextNormalization" -> Json.obj("removeSpaces" -> Json.fromBoolean(true)),
      "characters" -> Json.obj("guide" -> Json.obj("voice" -> Json.obj("fallbackSpeakerId" -> Json.fromInt(8)))),
      "parts" -> Json.fromValues(parts)
    ).noSpaces)
    project
  }

  private def _manifest_entries(audio: Path) =
    parser.parse(Files.readString(audio.resolve("manifest.json"), StandardCharsets.UTF_8)).toOption.flatMap(_.asArray).getOrElse(Vector.empty)

  private def _with_target[A](name: String)(body: Path => A): A = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/storyboard-narration").resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _write(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      finally paths.close()
    }
}
