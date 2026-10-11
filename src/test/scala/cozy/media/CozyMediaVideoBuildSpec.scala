package cozy.media

import cozy.CozySpecVocabulary
import cozy.video.CozyVideo
import io.circe.{Json, parser}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/*
 * @since   Oct.  4, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyMediaVideoBuildSpec {
  private final case class FileSnapshot(bytes: Vector[Byte], modified: FileTime)
}

final class CozyMediaVideoBuildSpec extends AnyWordSpec with GivenWhenThen with CozySpecVocabulary {
  import CozyMediaVideoBuildSpec.FileSnapshot
  "Normal Media-to-Video production" should {
    "reuse equivalent rendering inputs" which {
      "emit canonical companions after relocation without rewriting either MP4 or the original tree" in {
        _with_root("relocation") { root =>
          Given("a normally produced original package copied with file attributes to a different root")
          val original = root.resolve("original")
          _fixture(original)
          _build(original, new RecordingRunner)
          val originalstate = _tree_snapshot(original)
          val copied = root.resolve("copied")
          _copy_tree(original, copied)
          val jsonfile = copied.resolve("video/en/build/credits/credits.json")
          val propsfile = copied.resolve("video/en/build/credits/renderer-props.json")
          _write(jsonfile, _json(jsonfile).mapObject(_.add("obsolete", Json.obj("arbitrary" -> Json.arr(Json.Null, Json.fromInt(3))))).spaces2)
          _write(propsfile, _json(propsfile).mapObject(_.add("obsolete", Json.obj("anything" -> Json.fromBoolean(true)))).spaces2)
          val before = _video_times(copied)
          val runner = new RecordingRunner

          When("the copied selected video enters the real normal Media-to-Video producer")
          _build(copied, runner)

          Then("canonical emission removes extras and relocates descriptive paths with zero render or concat calls")
          runner.renderCalls shouldBe 0
          runner.concatCalls shouldBe 0
          _video_times(copied) shouldBe before
          _json(jsonfile).hcursor.downField("obsolete").focus shouldBe None
          _json(propsfile).hcursor.downField("obsolete").focus shouldBe None
          _json(jsonfile).hcursor.downField("profile").get[String]("sourcePath").toOption shouldBe
            Some(copied.resolve("conf/cozy/video/credit-profiles/selected.yaml").toString)
          _tree_snapshot(original) shouldBe originalstate
        }
      }

      "refresh companions on repeated unchanged builds with zero renderer or assembly calls" in {
        _with_root("repeat") { root =>
          Given("a normal successful Media build and unchanged declared Video inputs")
          _fixture(root)
          _build(root, new RecordingRunner)
          val before = _video_times(root)
          val runner = new RecordingRunner

          When("Media builds the same selected target twice")
          _build(root, runner)
          _build(root, runner)

          Then("both current actions emit valid companions and preserve the part and final MP4 times")
          runner.renderCalls shouldBe 0
          runner.concatCalls shouldBe 0
          _video_times(root) shouldBe before
          CozyVideo.verifyCredits(root.resolve("video/en/video.yaml")) shouldBe empty
        }
      }

      "reuse across article-only and descriptor-only package updates" in {
        _with_root("article-only") { root =>
          Given("a built video whose article and package descriptor are separate validation inputs")
          _fixture(root)
          _build(root, new RecordingRunner)
          val before = _video_times(root)
          _write(root.resolve("index.dox"), "= Changed article\n")
          _write(root.resolve("media.yaml"), _read(root.resolve("media.yaml")) + "\n")
          val runner = new RecordingRunner

          When("Media builds the selected target with those updated package inputs")
          _build(root, runner)

          Then("package validation succeeds and unchanged actual Video inputs require no MP4 work")
          runner.renderCalls shouldBe 0
          runner.concatCalls shouldBe 0
          _video_times(root) shouldBe before
        }
      }
    }

    "render current embedded-credit semantics before assembly" which {
      Vector("label", "creator", "title", "hold", "enabled", "locale", "item").foreach { change =>
        s"render when the selected $change changes with a newer dependency time" in {
          _with_root("credit-" + change) { root =>
            Given("a normally built Remotion video followed by a newer selected attribution or presentation input")
            _fixture(root)
            _build(root, new RecordingRunner)
            val profile = root.resolve("conf/cozy/video/credit-profiles/selected.yaml")
            val project = root.resolve("video/en/video.yaml")
            val changed = change match {
              case "label" => _write(profile, _read(profile).replace("Original label", "Changed label")); profile
              case "creator" => _write(profile, _read(profile).replace("Original creator", "Changed creator")); profile
              case "title" => _write(profile, _read(profile).replace("Original credits", "Changed credits")); profile
              case "hold" => _write(profile, _read(profile).replace("hold-seconds: 4.0", "hold-seconds: 6.0")); profile
              case "enabled" => _write(project, _read(project).replace("presentation: {enabled: true}", "presentation: {enabled: false}")); project
              case "locale" => _write(project, _read(project).replace("locale: en", "locale: ja")); project
              case "item" => _write(project, _read(project).replace("include: [material]", "include: [material, extra]")); project
            }
            Files.setLastModifiedTime(changed, FileTime.fromMillis(_video_times(root).values.map(_.toMillis).max + 1))
            val runner = new RecordingRunner

            When("Media consumes the changed declared Video dependency")
            _build(root, runner)

            Then("the renderer receives current display values before final assembly uses the rendered part")
            runner.renderCalls shouldBe 1
            runner.concatCalls shouldBe 1
            runner.commands.indexWhere(_.contains("node")) should be < runner.commands.indexWhere(_.contains("ffmpeg"))
            val props = runner.renderedProps.head.hcursor.downField("credits")
            change match {
              case "label" => props.get[Vector[Json]]("items").toOption.get.head.hcursor.get[String]("label").toOption shouldBe Some("Changed label")
              case "creator" => props.get[Vector[Json]]("items").toOption.get.head.hcursor.get[String]("creator").toOption shouldBe Some("Changed creator")
              case "title" => props.get[String]("title").toOption shouldBe Some("Changed credits")
              case "hold" => props.get[Double]("holdSeconds").toOption shouldBe Some(6.0)
              case "enabled" => props.get[Boolean]("enabled").toOption shouldBe Some(false)
              case "locale" => props.get[String]("locale").toOption shouldBe Some("ja")
              case "item" => props.get[Vector[Json]]("items").toOption.get.size shouldBe 2
            }
          }
        }
      }

      "observe older equal and newer profile times through real builds" in {
        val property = Prop.forAll(Gen.oneOf(-1L, 0L, 1L)) { offset =>
          _with_root("profile-time") { root =>
            Given("a normally produced video and its selected unchanged profile")
            _fixture(root)
            _build(root, new RecordingRunner)
            val before = _video_times(root)
            Files.setLastModifiedTime(root.resolve("conf/cozy/video/credit-profiles/selected.yaml"), FileTime.fromMillis(before("part").toMillis + offset))
            val runner = new RecordingRunner

            When("normal Media production compares the actual selected profile modification time")
            _build(root, runner)

            Then("only strictly newer inputs select rendering and assembly")
            runner.renderCalls shouldBe (if (offset > 0) 1 else 0)
            runner.concatCalls shouldBe (if (offset > 0) 1 else 0)
            if (offset <= 0) _video_times(root) shouldBe before
          }
          true
        }
        Test.check(Test.Parameters.default.withMinSuccessfulTests(18), property).passed shouldBe true
      }

      "carry generated valid presentation values into actual renderer props" in {
        val property = Prop.forAll(Gen.choose(0, 12), Gen.oneOf(true, false), Gen.alphaNumStr.suchThat(_.nonEmpty)) { (hold, enabled, suffix) =>
          _with_root("presentation") { root =>
            Given("a normally built video with a newer selected title hold and enabled state")
            _fixture(root)
            _build(root, new RecordingRunner)
            val profile = root.resolve("conf/cozy/video/credit-profiles/selected.yaml")
            _write(profile, _read(profile).replace("hold-seconds: 4.0", s"hold-seconds: $hold.0").replace("Original credits", s"Credits $suffix"))
            val project = root.resolve("video/en/video.yaml")
            _write(project, _read(project).replace("presentation: {enabled: true}", s"presentation: {enabled: $enabled}"))
            Files.setLastModifiedTime(profile, FileTime.fromMillis(_video_times(root).values.map(_.toMillis).max + 1))
            val runner = new RecordingRunner

            When("Media invokes the selected renderer followed by final assembly")
            _build(root, runner)

            Then("observed rendered title hold and enabled values are the selected domain values")
            runner.renderCalls shouldBe 1
            runner.concatCalls shouldBe 1
            val props = runner.renderedProps.head.hcursor.downField("credits")
            props.get[String]("title").toOption shouldBe Some(s"Credits $suffix")
            props.get[Double]("holdSeconds").toOption shouldBe Some(hold.toDouble)
            props.get[Boolean]("enabled").toOption shouldBe Some(enabled)
          }
          true
        }
        Test.check(Test.Parameters.default.withMinSuccessfulTests(12), property).passed shouldBe true
      }
    }

    "resolve conservative production and declared operands" which {
      "reject package output disagreement and an unavailable selected renderer before mutation" in {
        _with_root("producer-admission") { root =>
          Given("a successful Video output and companions with a package output that now disagrees")
          _fixture(root)
          _build(root, new RecordingRunner)
          val before = _generated_snapshot(root)
          val media = root.resolve("media.yaml")
          val original = _read(media)
          _write(media, original.replace("output: video/en/build/final.mp4", "output: video/en/build/other.mp4"))
          val runner = new RecordingRunner

          When("Media attempts normal Video production with that mismatched package output")
          val mismatch = intercept[Exception] { _build(root, runner) }

          Then("the resolved-output diagnostic precedes process work and preserves prior generated artifacts")
          mismatch.getMessage should include("must match resolved Video output")
          runner.commands shouldBe empty
          _generated_snapshot(root) shouldBe before

          Given("the output declaration is restored but the selected renderer has no real producer")
          _write(media, original)
          val project = root.resolve("video/en/video.yaml")
          _write(project, _read(project).replace("engine: remotion", "engine: unavailable"))

          When("Media resolves the unsupported producer")
          val unavailable = intercept[Exception] { _build(root, runner) }

          Then("the concrete renderer diagnostic preserves primary and companions")
          unavailable.getMessage should include("renderer=unavailable")
          runner.commands shouldBe empty
          _generated_snapshot(root) shouldBe before
        }
      }

      "use the selected simple-java2d producer with its actual combined audio" in {
        _with_root("simple-java2d") { root =>
          Given("a declared simple-java2d part with scene audio and the combined WAV consumed by its renderer")
          _fixture(root)
          val project = root.resolve("video/en/video.yaml")
          _write(project, _read(project).replace("engine: remotion", "engine: simple-java2d"))
          _write_wav(root.resolve("video/en/build/audio/dialogue/script.wav"))
          val runner = new RecordingRunner

          When("Media produces the selected part and final output through its existing renderer")
          _build(root, runner)
          val before = _video_times(root)
          val reused = new RecordingRunner
          _build(root, reused)

          Then("the frame/audio render and final concat occur once and unchanged production reuses both MP4s")
          runner.commands.count(_.contains("python3")) shouldBe 1
          runner.concatCalls shouldBe 1
          reused.commands.count(_.contains("python3")) shouldBe 0
          reused.concatCalls shouldBe 0
          _video_times(root) shouldBe before
        }
      }

      Vector("missing", "invalid", "stale-display", "missing-manifest").foreach { state =>
        s"render when credit companions are $state" in {
          _with_root("companion-" + state) { root =>
            Given("a normal video whose selected rendered-credit semantics can no longer be established")
            _fixture(root)
            _build(root, new RecordingRunner)
            val path = root.resolve("video/en/build/credits/renderer-props.json")
            state match {
              case "missing" => Files.delete(path)
              case "invalid" => _write(path, "{}")
              case "stale-display" => _write(path, _json(path).mapObject(_.add("title", Json.fromString("Old presentation"))).noSpaces)
              case "missing-manifest" => Files.delete(root.resolve("video/en/build/manifest.json"))
            }
            val runner = new RecordingRunner

            When("Media produces the selected target")
            _build(root, runner)

            Then("normal rendering and assembly regenerate usable companions")
            runner.renderCalls shouldBe 1
            runner.concatCalls shouldBe 1
            CozyVideo.verifyCredits(root.resolve("video/en/video.yaml")) shouldBe empty
          }
        }
      }

      "assemble reusable parts when only the final MP4 is missing" in {
        _with_root("missing-final") { root =>
          Given("valid rendered parts and companions with only a missing final output")
          _fixture(root)
          _build(root, new RecordingRunner)
          val parttime = _video_times(root)("part")
          Files.delete(root.resolve("video/en/build/final.mp4"))
          val runner = new RecordingRunner

          When("Media produces the missing final")
          _build(root, runner)

          Then("assembly reuses the valid rendered part without changing its modification time")
          runner.renderCalls shouldBe 0
          runner.concatCalls shouldBe 1
          _video_times(root)("part") shouldBe parttime
        }
      }

      "rerender for a newer explicitly authored receipt input" in {
        _with_root("explicit-input") { root =>
          Given("a built package with an explicitly authored additional Video producer operand")
          _fixture(root)
          _write(root.resolve("policy.txt"), "declared input")
          _write(root.resolve("media.yaml"), _read(root.resolve("media.yaml")) + "receipt:\n  inputs:\n    - {id: policy, role: render-policy, path: policy.txt, normalization: bytes}\n")
          _build(root, new RecordingRunner)
          Files.setLastModifiedTime(root.resolve("policy.txt"), FileTime.fromMillis(_video_times(root).values.map(_.toMillis).max + 1))
          val runner = new RecordingRunner

          When("the actual explicit receipt input is newer")
          _build(root, runner)

          Then("rendering and assembly consume the declared extra operand")
          runner.renderCalls shouldBe 1
          runner.concatCalls shouldBe 1
        }
      }
    }

    "report modeled producer failures within the selected scope" which {
      Vector("profile-missing", "profile-invalid", "audio-missing", "audio-invalid", "audio-malformed-wav", "asset-missing").foreach { problem =>
        s"report the required $problem input before mutating prior outputs" in {
          _with_root(problem) { root =>
            Given("successful primary and companions followed by a newly unusable required input")
            _fixture(root)
            _build(root, new RecordingRunner)
            val before = _generated_snapshot(root)
            problem match {
              case "profile-missing" => Files.delete(root.resolve("conf/cozy/video/credit-profiles/selected.yaml"))
              case "profile-invalid" => _write(root.resolve("conf/cozy/video/credit-profiles/selected.yaml"), "invalid: [")
              case "audio-missing" => Files.delete(root.resolve("video/en/build/audio/dialogue/scene.wav"))
              case "audio-invalid" => _write(root.resolve("video/en/build/audio/dialogue/manifest.json"), "[]")
              case "audio-malformed-wav" => _write(root.resolve("video/en/build/audio/dialogue/scene.wav"), "invalid WAV")
              case "asset-missing" => Files.delete(root.resolve("video/en/character.svg"))
            }
            val runner = new RecordingRunner

            When("normal Media production resolves the actual required source closure")
            val error = intercept[Exception] { _build(root, runner) }

            Then("the specific domain failure precedes process work and preserves primary and companion bytes and times")
            error.getMessage.toLowerCase should include(problem.takeWhile(_ != '-'))
            runner.renderCalls shouldBe 0
            runner.concatCalls shouldBe 0
            _generated_snapshot(root) shouldBe before
          }
        }
      }

      Vector("render", "probe", "assembly").foreach { stage =>
        s"report the actual $stage failure after producer work starts" in {
          _with_root("failure-" + stage) { root =>
            Given("successful prior outputs and a newer render input")
            _fixture(root)
            _build(root, new RecordingRunner)
            val script = root.resolve("video/en/script.json")
            Files.setLastModifiedTime(script, FileTime.fromMillis(_video_times(root).values.map(_.toMillis).max + 1))
            val runner = new RecordingRunner
            runner.failure = Some(stage)

            When("the real producer encounters the selected modeled failure")
            val error = intercept[Exception] { _build(root, runner) }

            Then("the failure identifies the renderer probe or assembly stage that could not complete")
            val diagnostic = stage match {
              case "render" => "selected render failure"
              case "probe" => "finite positive duration and video stream"
              case "assembly" => "selected assembly failure"
            }
            error.getMessage should include(diagnostic)
          }
        }
      }

      "leave an unselected language and original inputs untouched" in {
        _with_root("selected-language") { root =>
          Given("two normally built language resources and a newer selected English script")
          _fixture(root, Vector("en", "ja"))
          _build(root, new RecordingRunner, "video-en")
          _build(root, new RecordingRunner, "video-ja")
          val japanese = _tree_snapshot(root.resolve("video/ja"))
          val inputs = Vector(root.resolve("index.dox"), root.resolve("media.yaml"), root.resolve("video/en/script.json"))
          Files.setLastModifiedTime(inputs.last, FileTime.fromMillis(_video_times(root).values.map(_.toMillis).max + 1))
          val inputstate = inputs.map(path => path -> _snapshot(path)).toMap
          val runner = new RecordingRunner

          When("Media builds only the English resource")
          _build(root, runner)

          Then("only English renders while the other language and original input files preserve bytes and times")
          runner.renderCalls shouldBe 1
          _tree_snapshot(root.resolve("video/ja")) shouldBe japanese
          inputs.map(path => path -> _snapshot(path)).toMap shouldBe inputstate
        }
      }
    }
  }


  private final class RecordingRunner extends CozyVideo.VideoProcessRunner {
    val commands = ArrayBuffer.empty[Vector[String]]
    val renderedProps = ArrayBuffer.empty[Json]
    var failure: Option[String] = None
    def renderCalls: Int = commands.count(_.contains("node"))
    def concatCalls: Int = commands.count(args => args.contains("ffmpeg") && args.contains("concat"))
    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += args
      if (args.contains("node")) {
        val script = _command_path(cwd, args.find(_.endsWith("render.mjs")).get)
        val props = _json(script.getParent.getParent.resolve("props.json"))
        renderedProps += props
        _write(_command_path(cwd, props.hcursor.get[String]("outputPath").toOption.get), "rendered video")
        CozyVideo.VideoCommandResult(if (failure.contains("render")) 1 else 0, "rendered", "selected render failure")
      } else if (args.contains("ffmpeg")) {
        _write(_command_path(cwd, args.last), "assembled video")
        CozyVideo.VideoCommandResult(if (failure.contains("assembly")) 1 else 0, "assembled", "selected assembly failure")
      } else if (args.contains("python3")) {
        val script = _command_path(cwd, args.find(_.endsWith("render_frame.py")).get)
        _write(script.getParent.resolve("frame.png"), "rendered-frame")
        CozyVideo.VideoCommandResult(0, "frame", "")
      } else if (args.contains("ffprobe")) {
        CozyVideo.VideoCommandResult(0, if (failure.contains("probe")) "{}" else
          """{"format":{"duration":"1.0"},"streams":[{"codec_type":"video"}]}""", "")
      } else CozyVideo.VideoCommandResult(0, "ok", "")
    }
  }

  private def _command_path(cwd: Path, value: String): Path =
    if (value.startsWith("/workspace/")) cwd.resolve(value.stripPrefix("/workspace/")).normalize()
    else { val path = Paths.get(value); if (path.isAbsolute) path else cwd.resolve(path).normalize() }

  private def _build(root: Path, runner: RecordingRunner, target: String = "video-en"): String =
    CozyMedia.buildWithVideoRunner(CozyMedia.CommandConfig.create(List(root.resolve("media.yaml").toString, "--target", target)), runner,
      CozyVideo.VideoToolRegistry(Vector.empty))

  private def _fixture(root: Path, languages: Vector[String] = Vector("en")): Unit = {
    _write(root.resolve("conf/cozy/config.yaml"), "project:\n  id: media-video-spec\nvideo:\n  tool-mode: host\n")
    _write(root.resolve("index.dox"), "= Article\n")
    _write(root.resolve("conf/cozy/video/credit-profiles/selected.yaml"),
      """schema: cozy.video.credits.v1
        |profile: selected
        |presentation:
        |  title: {en: Original credits, ja: Japanese credits}
        |  hold-seconds: 4.0
        |credits:
        |  - id: material
        |    category: material
        |    label: {en: Original label, ja: Japanese label}
        |    publication-text: {en: Original attribution, ja: Japanese attribution}
        |    creator: Original creator
        |    source-url: https://example.org/source
        |    terms-url: https://example.org/terms
        |    obligation: required
        |    surfaces: [publication, video]
        |  - id: extra
        |    category: material
        |    label: {en: Extra label, ja: Extra Japanese label}
        |    publication-text: {en: Extra attribution, ja: Extra Japanese attribution}
        |    obligation: required
        |    surfaces: [publication, video]
        |""".stripMargin)
    languages.foreach { locale =>
      val video = root.resolve(s"video/$locale")
      _write(video.resolve("video.yaml"), s"""title: Video
        |locale: $locale
        |output: build/final.mp4
        |renderer: {engine: remotion}
        |credits:
        |  profile: selected
        |  include: [material]
        |  presentation: {enabled: true}
        |parts:
        |  - id: dialogue
        |    type: dialogue
        |    script: script.json
        |    output: build/parts/dialogue.mp4
        |    audioDir: build/audio/dialogue
        |""".stripMargin)
      _write(video.resolve("character.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
      _write(video.resolve("script.json"), """{"title":"Dialogue","characters":{"speaker":{"asset":"character.svg"}},"scenes":[{"id":"scene","speaker":"speaker","line":"Hello","duration":1.0}]}""")
      _write(video.resolve("build/audio/dialogue/manifest.json"), """[{"sceneId":"scene","speaker":"speaker","file":"scene.wav","leadSilence":0.0,"audioDuration":1.0,"targetDuration":1.0,"tailSilence":0.0,"provider":"macos-say","voiceIdentity":"Alex"}]""")
      _write_wav(video.resolve("build/audio/dialogue/scene.wav"))
    }
    val resources = languages.map(locale => s"""  - id: video-$locale
      |    kind: video
      |    language: $locale
      |    build: video-project
      |    project: video/$locale/video.yaml
      |    output: video/$locale/build/final.mp4
      |""".stripMargin).mkString
    _write(root.resolve("media.yaml"), "schema: cozy.media.v1\nknowledge:\n  id: article\n  source: index.dox\nresources:\n" + resources)
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(Files.isRegularFile(_)).foreach(Files.setLastModifiedTime(_, FileTime.fromMillis(1000000L)))
    finally stream.close()
  }

  private def _write_wav(path: Path): Unit = {
    val data = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
    data.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(38).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
    data.putInt(16).putShort(1.toShort).putShort(1.toShort).putInt(24000).putInt(48000).putShort(2.toShort).putShort(16.toShort)
    data.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(2).putShort(0.toShort)
    Files.write(path, data.array())
  }
  private def _write(path: Path, text: String): Unit = { Files.createDirectories(path.getParent); Files.writeString(path, text, StandardCharsets.UTF_8); () }
  private def _read(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)
  private def _json(path: Path): Json = parser.parse(_read(path)).toOption.get
  private def _snapshot(path: Path): FileSnapshot = FileSnapshot(Files.readAllBytes(path).toVector, Files.getLastModifiedTime(path))
  private def _video_times(root: Path): Map[String, FileTime] =
    Map("part" -> Files.getLastModifiedTime(root.resolve("video/en/build/parts/dialogue.mp4")), "final" -> Files.getLastModifiedTime(root.resolve("video/en/build/final.mp4")))
  private def _generated_snapshot(root: Path): Map[String, FileSnapshot] =
    _tree_snapshot(root.resolve("video/en/build")).filterNot { case (path, _) => path.startsWith("audio/") }
  private def _tree_snapshot(root: Path): Map[String, FileSnapshot] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(Files.isRegularFile(_)).map(path => root.relativize(path).toString -> _snapshot(path)).toMap
    finally stream.close()
  }
  private def _copy_tree(source: Path, destination: Path): Unit = {
    val stream = Files.walk(source)
    try stream.iterator().asScala.toVector.sortBy(_.getNameCount).foreach { path =>
      val target = destination.resolve(source.relativize(path))
      if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES)
    } finally stream.close()
  }
  private def _with_root(label: String)(body: Path => Unit): Unit = {
    val parent = Paths.get("target/cozy-media-video-spec/work").toAbsolutePath.normalize()
    Files.createDirectories(parent)
    val root = Files.createTempDirectory(parent, label + "-").toRealPath()
    try body(root) finally {
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
