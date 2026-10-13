package cozy.video

import io.circe.{Json, parser}
import java.nio.file.{Files, Path, Paths}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.mutable.ArrayBuffer
import scala.sys.process.{Process, ProcessLogger}

/*
 * @since   Oct. 12, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyVideoQualityRouteSpec {
  private final class RouteRunner(
    counts: Vector[Int] = Vector(31, 32),
    fps: Int = 29,
    hasaudio: Boolean = true,
    propschange: Json => Json = identity
  ) extends CozyVideo.VideoProcessRunner {
    val commands: ArrayBuffer[Vector[String]] = ArrayBuffer.empty
    val compositions: ArrayBuffer[Json] = ArrayBuffer.empty

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += args
      args.find(_.endsWith("render.mjs")) match {
        case Some(script) =>
          val workspace = _path(cwd, script).getParent.getParent
          val props = propschange(_json(workspace.resolve("props.json")))
          _write(workspace.resolve("props.json"), props)
          compositions += props
          val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
          if (args.contains("--frame-audio")) {
            val names = Vector.tabulate(count)(index => s"frames/frame-$index.png")
            names.foreach(name => _touch(workspace.resolve(name)))
            _pcm(workspace.resolve("composition.wav"), count * 48000 / props.hcursor.get[Int]("fps").toOption.get)
            _write(workspace.resolve("frame-audio.json"), Json.obj(
              "schema" -> Json.fromString("cozy.video.frame-audio.v1"),
              "width" -> props.hcursor.downField("width").focus.get,
              "height" -> props.hcursor.downField("height").focus.get,
              "fps" -> props.hcursor.downField("fps").focus.get,
              "frameCount" -> Json.fromInt(count),
              "frames" -> Json.fromValues(names.map(Json.fromString)), "audioPath" -> Json.fromString("composition.wav")))
          } else _touch(cwd.resolve(props.hcursor.get[String]("outputPath").toOption.get))
          CozyVideo.VideoCommandResult(0, "injected composition boundary", "")
        case None if args.contains("ffprobe") =>
          val input = _path(cwd, args.last)
          val count = if (input.getFileName.toString == "first.mp4") counts.head
            else if (input.getFileName.toString == "second.mp4") counts.last else counts.sum
          CozyVideo.VideoCommandResult(0, _summary(count, fps, hasaudio).noSpaces, "")
        case None =>
          _touch(_path(cwd, args.last))
          CozyVideo.VideoCommandResult(0, "injected encoder/assembly boundary", "")
      }
    }
  }

  private def _summary(count: Int, fps: Int, hasaudio: Boolean): Json = {
    val video = Json.obj("codec_type" -> Json.fromString("video"), "start_time" -> Json.fromString("0"),
      "duration_ts" -> Json.fromLong(count * 1000L), "time_base" -> Json.fromString(s"1/${fps * 1000}"),
      "duration" -> Json.fromString((count.toDouble / fps).toString), "nb_frames" -> Json.fromString(count.toString))
    val samples = count * 48000L / fps
    val audio = Json.obj("codec_type" -> Json.fromString("audio"), "start_time" -> Json.fromString("0"),
      "duration_ts" -> Json.fromLong(samples), "time_base" -> Json.fromString("1/48000"),
      "duration" -> Json.fromString((samples / 48000.0).toString))
    Json.obj("format" -> Json.obj("duration" -> Json.fromString((count.toDouble / fps).toString)),
      "streams" -> Json.fromValues(Vector(video) ++ (if (hasaudio) Vector(audio) else Vector.empty)))
  }

  private def _value(args: Vector[String], name: String): String = args(args.indexOf(name) + 1)
  private def _json(path: Path): Json = parser.parse(Files.readString(path)).toOption.get
  private def _write(path: Path, json: Json): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, json.spaces2)
  }
  private def _touch(path: Path): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, Array[Byte](1, 2, 3, 4))
  }
  private def _pcm(path: Path, samples: Int): Unit = {
    Files.createDirectories(path.getParent)
    CozyVideoImplementation._write_wav(path, CozyVideoImplementation.WaveData(1, 48000, 2, 16, Array.fill[Byte](samples * 4)(0)))
  }
  private def _path(root: Path, text: String): Path =
    if (text.startsWith("/workspace/")) root.resolve(text.stripPrefix("/workspace/"))
    else { val path = Paths.get(text); if (path.isAbsolute) path else root.resolve(path) }

  private def _renderer(profile: CozyVideoQuality.Profile, audio: Option[Json]): Json =
    Json.obj("engine" -> Json.fromString("remotion"), "qualityProfile" -> Json.fromString(profile.name),
      "width" -> Json.fromInt(4), "height" -> Json.fromInt(4), "fps" -> Json.fromInt(29))
      .deepMerge(Json.fromFields(audio.toVector.map("audioBitrate" -> _)))

  private def _fixture(root: Path, renderer: Json, ids: Vector[String] = Vector("first"), storyboard: Boolean = false): Path = {
    val project = root.resolve("video.json")
    if (storyboard) Files.writeString(root.resolve("storyboard.json"), CozyVideo.canonicalStoryboardJson(_storyboard()))
    else _write(root.resolve("script.json"), Json.obj("scenes" -> Json.arr(Json.obj(
      "id" -> Json.fromString("scene"), "narration" -> Json.fromString("Practice narration"), "duration" -> Json.fromInt(1)))))
    _write(project, Json.obj("output" -> Json.fromString("build/final.mp4"), "renderer" -> renderer,
      "visualEffects" -> Json.obj("opening" -> Json.fromString("none"), "sectionStart" -> Json.fromString("none"),
        "summary" -> Json.fromString("none"), "finalPage" -> Json.fromString("end-card")),
      "parts" -> Json.fromValues(ids.map { id =>
        Json.obj("id" -> Json.fromString(id), "type" -> Json.fromString(if (storyboard) "storyboard" else "dialogue"),
          (if (storyboard) "storyboard" else "script") -> Json.fromString(if (storyboard) "storyboard.json" else "script.json"),
          "audioDir" -> Json.fromString(s"build/audio/$id"), "output" -> Json.fromString(s"build/parts/$id.mp4"))
      })))
    ids.foreach { id =>
      val audio = root.resolve(s"build/audio/$id")
      _pcm(audio.resolve("scene.wav"), 48000)
      _pcm(audio.resolve("script.wav"), 48000)
      _write(audio.resolve("manifest.json"), Json.arr(Json.obj("sceneId" -> Json.fromString("scene"),
        "file" -> Json.fromString("scene.wav"), "leadSilence" -> Json.fromInt(0), "audioDuration" -> Json.fromInt(1),
        "targetDuration" -> Json.fromInt(1), "tailSilence" -> Json.fromInt(0))))
      _touch(root.resolve(s"build/parts/$id.mp4"))
    }
    project
  }

  private def _storyboard(): CozyVideo.Storyboard = CozyVideo.Storyboard("cozy.video.storyboard.v1", 1,
    Vector(CozyVideo.StoryboardScene("scene", 1, "opening", "narrator", "narration", "Practice narration",
      CozyVideo.StoryboardScreen("Practice", "Layout"), "Caption", BigDecimal(1), BigDecimal(0), "cut",
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, "Show the layout")))

  private def _operating(root: Path, mixed: Boolean, hardware: Boolean = false): Unit =
    _write(root.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("tools" -> Json.obj(
      "remotion" -> Json.obj("mode" -> Json.fromString(if (mixed) "docker" else "host")),
      "ffmpeg" -> Json.obj("mode" -> Json.fromString("host")),
      "ffprobe" -> Json.obj("mode" -> Json.fromString("host")),
      "python-pillow" -> Json.obj("mode" -> Json.fromString("host"))),
      "encoder" -> Json.obj("name" -> Json.fromString(if (hardware) "h264_videotoolbox" else "libx264")))))

  private def _with_project(body: Path => Unit): Unit = CozyVideoExecutionSpec.synchronized {
    val directory = Files.createTempDirectory("cozy-video-quality-routes-").toRealPath()
    val root = Files.createDirectories(directory.resolve("project"))
    val home = Files.createDirectories(directory.resolve("home"))
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.toString)
    try body(root)
    finally {
      if (previous == null) System.clearProperty("user.home") else System.setProperty("user.home", previous)
      CozyVideoImplementation._delete_tree(directory)
    }
  }

  private def _node(workspace: Path, props: Json): (Int, String, String) = {
    _write(workspace.resolve("props.json"), props)
    Files.createDirectories(workspace.resolve("src"))
    Files.writeString(workspace.resolve("src/render.mjs"), CozyVideoImplementation._remotion_render_mjs)
    val modules = workspace.resolve("node_modules/@remotion")
    _write(modules.resolve("cli/package.json"), Json.obj("name" -> Json.fromString("@remotion/cli")))
    Files.createDirectories(modules.resolve("bundler"))
    Files.writeString(modules.resolve("bundler/index.js"), "exports.bundle = async () => 'local-fixture';\n")
    Files.createDirectories(modules.resolve("renderer"))
    Files.writeString(modules.resolve("renderer/index.js"), """exports.selectComposition = async ({inputProps}) => ({
      |  durationInFrames: inputProps.timing.totalFrames, fps: inputProps.fps, width: inputProps.width, height: inputProps.height
      |});
      |exports.renderMedia = async (options) => {
      |  const pre = options.ffmpegOverride({type: 'pre-stitcher', args: ['-c:a', 'pcm_s16le', 'pre.mp4']});
      |  const stitch = options.ffmpegOverride({type: 'stitcher', args: ['-c:v', 'copy', '-c:a', 'copy', 'final.mp4']});
      |  const other = options.ffmpegOverride({type: 'other', args: ['unchanged']});
      |  console.log(JSON.stringify({audioCodec: options.audioCodec, pre, stitch, other,
      |    frameCount: options.composition.durationInFrames, fps: options.composition.fps}));
      |  return {buffer: Buffer.from('local fixture; no rendered media')};
      |};
      |""".stripMargin)
    val stdout = ArrayBuffer.empty[String]
    val stderr = ArrayBuffer.empty[String]
    val executable = System.getProperty("cozy.video.quality.node", "node")
    val exit = try Process(Vector(executable, workspace.resolve("src/render.mjs").toString), workspace.toFile)
      .!(ProcessLogger(line => { stdout += line; () }, line => { stderr += line; () }))
    catch { case error: java.io.IOException => throw new IllegalStateException(s"Mandatory quality route specification requires Node: $executable", error) }
    (exit, stdout.mkString("\n"), stderr.mkString("\n"))
  }

  private def _node_props(audio: Option[Json], profile: Option[Json]): Json =
    Json.obj("outputPath" -> Json.fromString("output.mp4"), "width" -> Json.fromInt(4), "height" -> Json.fromInt(4),
      "fps" -> Json.fromInt(29), "crf" -> Json.fromInt(32), "timing" -> Json.obj("totalFrames" -> Json.fromInt(63)))
      .deepMerge(Json.fromFields(audio.toVector.map("audioBitrate" -> _) ++ profile.toVector.map("qualityProfile" -> _)))

  private def _review_fixture(root: Path, renderer: Json): Path = {
    val project = _fixture(root, renderer)
    val plan = CozyVideoImplementation._plan(project, None, Some("host"))
    _touch(plan.outputPath)
    _write(plan.manifestPath, Json.obj("status" -> Json.fromString("validated"), "outputPath" -> Json.fromString(plan.outputPath.toString),
      "encoding" -> CozyVideoQuality.encodingJson(plan.encoding)))
    _write(root.resolve("target/cozy-video/remotion/first/props.json"), CozyVideoQuality.encodingJson(plan.encoding, "encodingPolicy", "encodingOrigins")
      .deepMerge(Json.obj("partId" -> Json.fromString("first"), "timing" -> Json.obj(
        "openingFrames" -> Json.fromInt(0), "contentFrames" -> Json.fromInt(29), "summaryStartFrame" -> Json.fromInt(29),
        "summaryFrames" -> Json.fromInt(0), "finalPageStartFrame" -> Json.fromInt(29), "finalPageHoldFrames" -> Json.fromInt(0),
        "totalFrames" -> Json.fromInt(29)), "scenes" -> Json.arr(Json.obj("id" -> Json.fromString("scene"),
        "startFrame" -> Json.fromInt(0), "durationFrames" -> Json.fromInt(29), "leadInFrames" -> Json.fromInt(0),
        "sectionTransitionFrames" -> Json.fromInt(0))))))
    project
  }

  private def _without_joint(json: Json, originsfield: String): Json =
    json.mapObject(_.remove("qualityProfile").remove("audioBitrate").remove(originsfield))

  private def _storyboard_review_props(project: Path): Unit = {
    val plan = CozyVideoImplementation._plan(project, Some("host"), None)
    val frames = plan.encoding.fps
    _write(plan.projectRoot.resolve("target/cozy-video/remotion/first/props.json"),
      CozyVideoQuality.encodingJson(plan.encoding, "encodingPolicy", "encodingOrigins").deepMerge(Json.obj(
        "partId" -> Json.fromString("first"),
        "timing" -> Json.obj("openingFrames" -> Json.fromInt(0), "contentFrames" -> Json.fromInt(frames),
          "summaryStartFrame" -> Json.fromInt(frames), "summaryFrames" -> Json.fromInt(0),
          "finalPageStartFrame" -> Json.fromInt(frames), "finalPageHoldFrames" -> Json.fromInt(0),
          "totalFrames" -> Json.fromInt(frames)),
        "scenes" -> Json.arr(Json.obj("id" -> Json.fromString("scene"), "startFrame" -> Json.fromInt(0),
          "durationFrames" -> Json.fromInt(frames), "leadInFrames" -> Json.fromInt(0),
          "sectionTransitionFrames" -> Json.fromInt(0))))))
  }

  private def _publication(root: Path): (CozyVideoPublisher.PublicationPlan, Path, Path) = {
    val pkg = root.resolve("tutorial.video")
    Files.createDirectories(pkg)
    Files.writeString(pkg.resolve("index.dox"), "# Tutorial\n")
    _write(pkg.resolve("script.json"), Json.obj("scenes" -> Json.arr(Json.obj("id" -> Json.fromString("scene"),
      "narration" -> Json.fromString("Practice narration"), "duration" -> Json.fromInt(1))),
      "voice" -> Json.obj("fallbackSpeakerId" -> Json.fromInt(99))))
    _write(pkg.resolve("video.json"), Json.obj("video" -> Json.obj("name" -> Json.fromString("tutorial")),
      "title" -> Json.fromString("Tutorial"), "version" -> Json.fromString("0.1.0"),
      "renderer" -> Json.obj("engine" -> Json.fromString("remotion"), "qualityProfile" -> Json.fromString("lightweight"),
        "audioBitrate" -> Json.fromString(" 160k ")), "toolMode" -> Json.fromString("host"),
      "publish" -> Json.obj("module" -> Json.fromString("textus"), "publicPath" -> Json.fromString("videos/tutorial.mp4"))))
    val plan = CozyVideoPublisher.planPublication(CozyVideoPublisher.PublishVideoConfig(pkg, root.resolve("publication"), root.resolve("warehouse"), None))
    CozyVideoPublisher.renderPublication(plan, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
  }

  private def _metadata_encoding(prepared: CozyVideoPublisher.PreparedPublication): Json = {
    val metadata = prepared.result.metadataPublication.get.entries.find(_._1 == "metadata/videos/tutorial/metadata.json").get._2
    parser.parse(metadata.toString).toOption.get.hcursor.downField("video").downField("encoding").focus.get
  }
}

final class CozyVideoQualityRouteSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyVideoQualityRouteSpec._

  "Joint encoding routes" should {
    "transport all canonical tiers and independent overrides" which {
      "preserve profile video controls and full sample endings across native and mixed software/hardware commands" in {
        Given("all four tiers and generated independent audio overrides, renderer modes and encoder selections")
        val cases = for {
          audio <- Gen.oneOf(Vector(None, Some(Json.fromString(" 160k ")), Some(Json.fromInt(180000)), Some(Json.fromString("2M"))))
          mixed <- Gen.oneOf(true, false)
          hardware <- Gen.oneOf(true, false)
        } yield (audio, mixed, hardware)
        CozyVideoQuality.values.foreach { profile =>
          val property = Prop.forAll(cases) { case (audio, mixed, hardware) =>
            var valid = false
            _with_project { root =>
              Given(s"a ${profile.name} descriptor, independent audio override and selected renderer/encoder modes")
              val project = _fixture(root, _renderer(profile, audio))
              _operating(root, mixed, hardware)
              val runner = new RouteRunner()
              When("public render consumes the complete composition through the selected native or mixed route")
              CozyVideo.render(CozyVideo.RenderConfig(project, "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), runner)
              val props = runner.compositions.head
              val args = runner.commands.last
              val bitrate = audio.map(value => CozyVideoQuality.normalizeAudioBitrate(value).toOption.get).getOrElse(profile.audioBitrate)
              val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
              val evidence = _json(root.resolve("target/cozy-video/remotion/first/encoding-result.json"))
              val manifest = _json(root.resolve("build/parts/first.manifest.json"))
              Then("the returned props, actual selected argv and intent records retain independent audio and full ending controls")
              valid = props.hcursor.get[String]("qualityProfile").toOption.contains(profile.name) &&
                props.hcursor.get[String]("audioBitrate").toOption.contains(bitrate) && _value(args, "-b:a") == bitrate &&
                args.count(_ == "-b:a") == 1 && _value(args, "-ar") == "48000" && _value(args, "-ac") == "2" &&
                _value(args, "-cutoff") == "18000" && _value(args, "-frames:v") == count.toString &&
                _value(args, "-af").contains(s"atrim=end_sample=${BigInt(count) * 48000 / 29},") && !args.contains("-shortest") &&
                props.hcursor.downField("timing").get[Int]("finalPageHoldFrames").toOption.exists(_ > 0) &&
                evidence.hcursor.get[String]("audioBitrate").toOption.contains(bitrate) &&
                evidence.hcursor.downField("encodingOrigins").focus == props.hcursor.downField("encodingOrigins").focus &&
                manifest.hcursor.downField("encoding").get[String]("audioBitrate").toOption.contains(bitrate) &&
                (if (hardware) _value(args, "-b:v") == profile.hardwareBitrate && !args.contains("-crf") && !args.contains("-preset")
                 else _value(args, "-crf") == profile.crf.toString && profile.x264Preset.forall(_value(args, "-preset") == _))
            }
            valid
          }
          When(s"seeded ScalaCheck interprets ${profile.name} through the public render surface")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(12).withInitialSeed(71802L), property)
          Then("the selected audio has one argv value and remains independent of picture/encoder controls")
          withClue(result.toString) { result.passed shouldBe true }
        }
      }

      "carry selected audio and origins into simple-java2d without changing still-image timing ownership" in {
        Given("a root joint profile and an independent explicit audio override")
        _with_project { root =>
          val project = _fixture(root, _renderer(CozyVideoQuality.Lightweight, Some(Json.fromString(" 160k "))))
          _write(project, _json(project).deepMerge(Json.obj("visualEffects" -> Json.obj("finalPage" -> Json.fromString("none")))))
          _operating(root, mixed = false)
          val commands = ArrayBuffer.empty[Vector[String]]
          val runner = new CozyVideo.VideoProcessRunner {
            def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
              commands += args
              if (args.head == "python3") _touch(Paths.get(args.last).getParent.resolve("frame.png")) else _touch(Paths.get(args.last))
              CozyVideo.VideoCommandResult(0, "injected still-image output", "")
            }
          }
          When("the public simple-java2d renderer interprets its existing still-image pipeline")
          CozyVideo.render(CozyVideo.RenderConfig(project, "simple-java2d"), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          Then("the AAC command and intent records contain selected audio, stereo48k, cutoff and existing shortest/tune")
          val args = commands.last
          _value(args, "-b:a") shouldBe "160k"
          _value(args, "-ar") shouldBe "48000"
          _value(args, "-ac") shouldBe "2"
          _value(args, "-cutoff") shouldBe "18000"
          _value(args, "-tune") shouldBe "stillimage"
          args should contain("-shortest")
          val props = _json(root.resolve("target/cozy-video/simple-java2d/first/props.json"))
          props.hcursor.get[String]("audioBitrate").toOption shouldBe Some("160k")
          props.hcursor.get[Int]("width").toOption shouldBe Some(1280)
          _json(root.resolve("build/parts/first.manifest.json")).hcursor.downField("encoding").get[String]("qualityProfile").toOption shouldBe Some("lightweight")
        }
      }

      "stop native encoding when a producer returns incomplete or invalid joint props" in {
        Given("complete public render inputs whose producer supplies malformed selected audio or canonical fields")
        val changes: Vector[Json => Json] = Vector(
          props => props.mapObject(_.remove("audioBitrate")), props => props.mapObject(_.add("audioBitrate", Json.Null)),
          props => props.mapObject(_.add("qualityProfile", Json.fromString("unknown"))))
        changes.foreach { change =>
          _with_project { root =>
            val project = _fixture(root, _renderer(CozyVideoQuality.Standard, None))
            _operating(root, mixed = true)
            val runner = new RouteRunner(propschange = change)
            When("the public render consumes the produced handoff and joint props")
            val error = intercept[Throwable](CozyVideo.render(CozyVideo.RenderConfig(project, "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), runner))
            Then("structured input admission fails before any native encoder invocation")
            error.getMessage should not be empty
            runner.commands should have size 1
          }
        }
      }
    }

    "execute the generated Docker mux callback in local Node fixtures" which {
      "use the selected profile or normalized override in exactly one stitcher AAC encode" in {
        Given("the actual generated render.mjs and local Remotion API fixtures with complete 63-frame timing")
        _with_project { root =>
          val cases = CozyVideoQuality.values.map(profile => _node_props(Some(Json.fromString(profile.audioBitrate)), Some(Json.fromString(profile.name))) -> profile.audioBitrate) ++
            Vector(_node_props(None, None) -> "320k", _node_props(Some(Json.fromString(" 160k ")), None) -> "160k",
              _node_props(Some(Json.fromInt(180000)), Some(Json.fromString("standard"))) -> "180000")
          cases.zipWithIndex.foreach { case ((props, expected), index) =>
            When("Node executes generated code and the fixture calls its actual ffmpegOverride")
            val (exit, stdout, stderr) = _node(root.resolve(s"node-$index"), props)
            Then("recorded callback output retains PCM before one selected stitcher AAC and the full sample trim")
            withClue(stderr) { exit shouldBe 0 }
            val record = parser.parse(stdout).toOption.get
            record.hcursor.get[Int]("frameCount").toOption shouldBe Some(63)
            record.hcursor.get[Int]("fps").toOption shouldBe Some(29)
            record.hcursor.get[String]("audioCodec").toOption shouldBe Some("pcm-16")
            val pre = record.hcursor.get[Vector[String]]("pre").toOption.get
            val stitch = record.hcursor.get[Vector[String]]("stitch").toOption.get
            _value(pre, "-c:a") shouldBe "pcm_s16le"
            pre should not contain "-b:a"
            _value(stitch, "-c:a") shouldBe "libfdk_aac"
            stitch.count(_ == "-c:a") shouldBe 1
            _value(stitch, "-b:a") shouldBe expected
            _value(stitch, "-ar") shouldBe "48000"
            _value(stitch, "-ac") shouldBe "2"
            _value(stitch, "-cutoff") shouldBe "18000"
            _value(stitch, "-af") shouldBe s"apad,atrim=end_sample=${63 * 48000L / 29},asetpts=PTS-STARTPTS"
            _value(stitch, "-movie_timescale") shouldBe "48000"
            record.hcursor.get[Vector[String]]("other").toOption shouldBe Some(Vector("unchanged"))
          }
        }
      }

      "reject invalid, partial, null and overflowing joint settings before the callback runs" in {
        Given("malformed profiles and audio values offered to both native admission and generated code")
        val audio = Vector(Json.Null, Json.True, Json.arr(), Json.fromInt(0), Json.fromInt(-1), Json.fromDoubleOrNull(1.5),
          Json.fromString(""), Json.fromString("0128k"), Json.fromString("128K"), Json.fromString("9223372036854775808"),
          Json.fromString("9223372036854776k"), Json.fromString("1k extra"))
        val invalid = audio.map(value => _node_props(Some(value), Some(Json.fromString("standard")))) ++
          Vector(_node_props(None, Some(Json.fromString("standard"))), _node_props(Some(Json.fromString("128k")), Some(Json.Null)),
            _node_props(Some(Json.fromString("128k")), Some(Json.fromString(" standard "))))
        _with_project { root =>
          invalid.zipWithIndex.foreach { case (props, index) =>
            When("native admission and Node interpret the same invalid props")
            val admitted = CozyVideoQuality.audioBitrateFromProps(props)
            val (exit, stdout, stderr) = _node(root.resolve(s"invalid-$index"), props)
            Then("the error is explicit and no renderMedia callback output is recorded")
            admitted.isLeft shouldBe true
            exit should not be 0
            stdout shouldBe empty
            stderr should include("Error")
          }
          Given("an unsafe JSON integer that cannot be represented exactly in JavaScript")
          val unsafe = _node_props(Some(Json.fromLong(9007199254740992L)), None)
          When("Node interprets the unsafe numeric value")
          val (exit, stdout, stderr) = _node(root.resolve("unsafe-number"), unsafe)
          Then("the generated adapter refuses a guessed numeric bitrate")
          exit should not be 0
          stdout shouldBe empty
          stderr should include("safe integer")
        }
      }
    }

    "assemble selected audio without changing presentation allocation" which {
      "preserve selected canonical and explicit audio in normal and Storyboard confirmation/final builds" in {
        Given("two independently probed parts with rational durations and canonical or explicit audio")
        for {
          mode <- Vector(None, Some("confirmation"), Some("final"))
          overrideaudio <- Vector(None, Some(Json.fromString(" 160k ")))
        } _with_project { root =>
          val project = _fixture(root, _renderer(CozyVideoQuality.Lightweight, overrideaudio), Vector("first", "second"), mode.isDefined)
          _operating(root, mixed = false)
          val runner = new RouteRunner()
          When("the public build interprets its selected existing assembly route")
          val built = CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false, mode = mode), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          Then("the only AAC mux consumes concatenated PCM at the selected rate and independently allocated cumulative floors")
          built should include("Cozy Video")
          val muxes = runner.commands.filter(_.contains("aac"))
          muxes should have size 1
          val expected = if (overrideaudio.isDefined) "160k" else "96k"
          _value(muxes.head, "-b:a") shouldBe expected
          _value(muxes.head, "-c:v") shouldBe "copy"
          _value(muxes.head, "-ar") shouldBe "48000"
          _value(muxes.head, "-ac") shouldBe "2"
          val decodes = runner.commands.filter(_.contains("pcm_s16le"))
          decodes should have size 2
          _value(decodes.head, "-af") should include(s"atrim=end_sample=${31 * 48000L / 29},")
          _value(decodes.last, "-af") should include(s"atrim=end_sample=${63 * 48000L / 29 - 31 * 48000L / 29},")
          runner.commands.count(args => args.last.endsWith("combined.wav") && _value(args, "-c:a") == "copy") shouldBe 1
          val manifest = mode.map(value => root.resolve(s"target/cozy-video/$value/manifest.json")).getOrElse(root.resolve("build/manifest.json"))
          val record = _json(manifest)
          record.hcursor.downField("encoding").get[String]("audioBitrate").toOption shouldBe Some(expected)
          record.hcursor.downField("encoding").get[String]("qualityProfile").toOption shouldBe Some("lightweight")
          mode.foreach { _ =>
            record.hcursor.downField("renderers").downField("project").get[String]("qualityProfile").toOption shouldBe Some("lightweight")
            record.hcursor.downField("renderers").downField("project").get[String]("audioBitrate").toOption shouldBe overrideaudio.map(_ => "160k")
          }
        }
      }

      "retain copy-only single-part and all-video-only paths plus old package-helper AAC320 compatibility" in {
        Given("the same joint selection with single-part or all-silent inputs")
        Vector((Vector("first"), true), (Vector("first", "second"), false)).foreach { case (ids, hasaudio) =>
          _with_project { root =>
            val project = _fixture(root, _renderer(CozyVideoQuality.Lightweight, None), ids)
            _operating(root, mixed = false)
            val runner = new RouteRunner(hasaudio = hasaudio)
            When("normal public build interprets the copy-only assembly case")
            CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty), runner)
            Then("there is no new AAC/PCM encoding stage")
            runner.commands.exists(_.contains("aac")) shouldBe false
            runner.commands.exists(_.contains("pcm_s16le")) shouldBe false
            runner.commands.find(_.contains("-c")).get should contain("copy")
          }
        }
        _with_project { root =>
          val project = _fixture(root, _renderer(CozyVideoQuality.Lightweight, None), Vector("first", "second"))
          _operating(root, mixed = false)
          val plan = CozyVideoImplementation._plan(project, None, None)
          val list = root.resolve("assembly/concat.txt")
          val runner = new RouteRunner()
          CozyVideoImplementation._write_ffmpeg_concat_list(root, plan.execution, list, plan.parts.map(_.outputPath))
          When("an old package caller omits the appended encoding argument")
          CozyVideoImplementation._run_build_ffmpeg(root, plan.execution, list, plan.parts.map(_.outputPath), root.resolve("build/old.mp4"), runner)
          Then("source-compatible internal construction keeps historical320k")
          _value(runner.commands.find(_.contains("aac")).get, "-b:a") shouldBe "320k"
        }
      }
    }
  }

  "Joint encoding observations" should {
    "show exact field origins in public inspection, dry-run and RDF while retaining legacy policy semantics" in {
      Given("each canonical tier with a field-specific override and each original legacy policy")
      val selections = CozyVideoQuality.values.map(profile => Json.obj("qualityProfile" -> Json.fromString(profile.name))) ++
        Vector("lightweight", "standard", "quality").map(policy => Json.obj("policy" -> Json.fromString(policy)))
      selections.foreach { selection =>
        _with_project { root =>
          val renderer = selection.deepMerge(Json.obj("engine" -> Json.fromString("remotion"), "crf" -> Json.fromInt(27), "audioBitrate" -> Json.fromString("160k")))
          val project = _fixture(root, renderer)
          val plan = CozyVideoImplementation._plan(project, None, None)
          When("public inspect, build dry-run and RDF project the selected root encoding")
          val inspect = CozyVideo.inspect(CozyVideo.InspectConfig(project, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
          val dryrun = CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
          CozyVideo.rdf(CozyVideo.RdfConfig(project, root.resolve("rdf")))
          Then("picture/audio settings and all seven origins agree with the shared intent record")
          Vector(inspect, dryrun).foreach { text =>
            text should include(s"qualityProfile: ${plan.encoding.effectiveQualityProfile.name}")
            text should include("encodingAudioBitrate: 160k")
            text should include(s"encodingPolicy: ${plan.encoding.policy.name}")
            plan.encoding.origins.foreach { case (field, source) => text should include(s"encodingOrigin.$field: $source") }
            text should include("encodingOrigin.crf: explicit")
            text should include("encodingOrigin.audioBitrate: explicit")
          }
          _json(root.resolve("rdf/manifest.json")).hcursor.downField("encoding").focus shouldBe Some(CozyVideoQuality.encodingJson(plan.encoding))
          val triples = Files.readString(root.resolve("rdf/video.ttl"))
          triples should include(s"""cozy-video:qualityProfile "${plan.encoding.effectiveQualityProfile.name}"""")
          triples should include("cozy-video:audioBitrate \"160k\"")
          plan.encoding.origins.foreach { case (field, source) =>
            triples should include(s"""cozy-video:encodingOrigin${field.head.toUpper}${field.tail} "$source"""")
          }
        }
      }
    }

    "admit current review evidence and controlled legacy320 records before executing any runner" which {
      "accept complete matching joint observations and missing-all original policy observations" in {
        Given("current joint descriptors and an original legacy-only policy descriptor")
        Vector(_renderer(CozyVideoQuality.Lightweight, None),
          Json.obj("engine" -> Json.fromString("remotion"), "policy" -> Json.fromString("lightweight"),
            "fps" -> Json.fromInt(29), "width" -> Json.fromInt(4), "height" -> Json.fromInt(4))).foreach { renderer =>
          _with_project { root =>
            val project = _review_fixture(root, renderer)
            val plan = CozyVideoImplementation._plan(project, None, Some("host"))
            val propspath = root.resolve("target/cozy-video/remotion/first/props.json")
            if (renderer.hcursor.downField("policy").focus.isDefined) {
              _write(propspath, _without_joint(_json(propspath), "encodingOrigins"))
              _write(plan.manifestPath, _json(plan.manifestPath).mapObject(fields => fields.add("encoding", _without_joint(fields("encoding").get, "origins"))))
            }
            val runner = CozyVideoSpec.ReviewEvidenceRunner()
            When("public review-evidence validates the current record")
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
            Then("admitted extraction runs and the emitted review renderer contains joint intent")
            runner.commands should not be empty
            _json(root.resolve("review/review-manifest.json")).hcursor.downField("renderer").focus shouldBe
              Some(CozyVideoQuality.encodingJson(plan.encoding, "encodingPolicy", "encodingOrigins"))
          }
        }
        Given("old internal resolved construction with empty origins and its historical320k default")
        val internal = CozyVideoModel.ResolvedEncodingSettings.lightweight
        val oldrecord = _without_joint(CozyVideoQuality.encodingJson(internal), "origins")
        When("the package review adapter admits the source-compatible internal record")
        val admitted = scala.util.Try(CozyVideoImplementation._review_require_effective_encoding(oldrecord, "policy", internal, "old internal record"))
        Then("the legacy internal compatibility path remains admissible")
        admitted.isSuccess shouldBe true
      }

      "reject missing, partial, changed and malformed joint observations with old six-field error ordering" in {
        Given("canonical/default/explicit-audio plans whose current manifest or props disagree")
        val renderers = Vector(_renderer(CozyVideoQuality.Lightweight, None),
          Json.obj("engine" -> Json.fromString("remotion"), "fps" -> Json.fromInt(29), "width" -> Json.fromInt(4), "height" -> Json.fromInt(4)),
          _renderer(CozyVideoQuality.Standard, Some(Json.fromString("320k"))),
          Json.obj("engine" -> Json.fromString("remotion"), "policy" -> Json.fromString("lightweight"),
            "fps" -> Json.fromInt(29), "width" -> Json.fromInt(4), "height" -> Json.fromInt(4), "audioBitrate" -> Json.fromString("320k")))
        for { renderer <- renderers; props <- Vector(false, true); variant <- 0 until 7 } _with_project { root =>
          val project = _review_fixture(root, renderer)
          val target = if (props) root.resolve("target/cozy-video/remotion/first/props.json") else root.resolve("build/manifest.json")
          val original = _json(target)
          val encoding = if (props) original else original.hcursor.downField("encoding").focus.get
          val originsfield = if (props) "encodingOrigins" else "origins"
          val changed = variant match {
            case 0 => _without_joint(encoding, originsfield)
            case 1 => encoding.mapObject(_.remove("audioBitrate"))
            case 2 => encoding.mapObject(_.add("audioBitrate", Json.fromString("192k")))
            case 3 => encoding.mapObject(_.add("qualityProfile", Json.fromString("high-quality")))
            case 4 => encoding.mapObject(_.add(originsfield, Json.obj()))
            case 5 => encoding.mapObject(_.add("audioBitrate", Json.Null))
            case _ => _without_joint(encoding, originsfield).mapObject(_.add("crf", Json.fromInt(999)))
          }
          _write(target, if (props) changed else original.mapObject(_.add("encoding", changed)))
          val runner = CozyVideoSpec.ReviewEvidenceRunner()
          When("public review-evidence interprets the disagreeing record")
          val error = intercept[Throwable] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }
          Then("settings fail before any extraction and old field errors retain precedence")
          error.getMessage should include(if (variant == 6) "crf does not match" else "does not match planned effective encoding")
          runner.commands shouldBe empty
        }
      }
    }

    "admit Storyboard final build records through public review evidence" which {
      "accept missing-all joint observations for each original legacy policy at historical320k" in {
        Vector("lightweight", "standard", "quality").foreach { policy =>
          _with_project { root =>
            Given(s"a $policy legacy Storyboard descriptor, narration and part output created before final generation")
            val renderer = Json.obj("engine" -> Json.fromString("remotion"), "policy" -> Json.fromString(policy),
              "fps" -> Json.fromInt(29), "width" -> Json.fromInt(4), "height" -> Json.fromInt(4))
            val project = _fixture(root, renderer, storyboard = true)
            _operating(root, mixed = false)
            When("public final build generates the current mode manifest, handoff and part-artifact records")
            CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false, mode = Some("final"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), new RouteRunner())
            And("matching props and the generated final encoding retain only their historical six fields")
            _storyboard_review_props(project)
            val manifest = root.resolve("target/cozy-video/final/manifest.json")
            val original = _json(manifest)
            _write(manifest, original.mapObject(fields => fields.add("encoding", _without_joint(fields("encoding").get, "origins"))))
            val props = root.resolve("target/cozy-video/remotion/first/props.json")
            _write(props, _without_joint(_json(props), "encodingOrigins"))
            val runner = CozyVideoSpec.ReviewEvidenceRunner()
            When("public review evidence admits the existing Storyboard final manifest")
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), runner)
            Then("extraction succeeds and review records the planned canonical tier and historical audio")
            runner.commands should not be empty
            val plan = CozyVideoImplementation._plan(project, Some("host"), None)
            plan.encoding.audioBitrate shouldBe "320k"
            _json(root.resolve("review/review-manifest.json")).hcursor.downField("renderer").focus shouldBe
              Some(CozyVideoQuality.encodingJson(plan.encoding, "encodingPolicy", "encodingOrigins"))
          }
        }
      }

      "accept complete current canonical, default and explicit-audio final records" in {
        val renderers = CozyVideoQuality.values.map(profile => _renderer(profile, None)) ++ Vector(
          _renderer(CozyVideoQuality.Standard, None).mapObject(_.remove("qualityProfile")),
          _renderer(CozyVideoQuality.Standard, Some(Json.fromString(" 160k "))),
          _renderer(CozyVideoQuality.HighQuality, Some(Json.fromString("320k"))))
        renderers.foreach { renderer =>
          _with_project { root =>
            Given("a current Storyboard selection with narration and part output created before final generation")
            val project = _fixture(root, renderer, storyboard = true)
            _operating(root, mixed = false)
            When("public final build emits complete joint observations and all required companion records")
            CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false, mode = Some("final"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), new RouteRunner())
            And("matching review props are prepared from the same selected plan and one-second narration")
            _storyboard_review_props(project)
            val runner = CozyVideoSpec.ReviewEvidenceRunner()
            When("public review evidence interprets the generated Storyboard final manifest")
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), runner)
            Then("extraction succeeds and both final and review encoding observations match resolved intent")
            runner.commands should not be empty
            val plan = CozyVideoImplementation._plan(project, Some("host"), None)
            _json(root.resolve("target/cozy-video/final/manifest.json")).hcursor.downField("encoding").focus shouldBe
              Some(CozyVideoQuality.encodingJson(plan.encoding))
            _json(root.resolve("review/review-manifest.json")).hcursor.downField("renderer").focus shouldBe
              Some(CozyVideoQuality.encodingJson(plan.encoding, "encodingPolicy", "encodingOrigins"))
          }
        }
      }

      "reject missing-all final observations for default, canonical and explicit320 plans before extraction" in {
        val renderers = CozyVideoQuality.values.map(profile => _renderer(profile, None)) ++ Vector(
          _renderer(CozyVideoQuality.Standard, None).mapObject(_.remove("qualityProfile")),
          _renderer(CozyVideoQuality.Standard, Some(Json.fromString("320k"))),
          Json.obj("engine" -> Json.fromString("remotion"), "policy" -> Json.fromString("lightweight"),
            "fps" -> Json.fromInt(29), "width" -> Json.fromInt(4), "height" -> Json.fromInt(4),
            "audioBitrate" -> Json.fromString("320k")))
        renderers.foreach { renderer =>
          _with_project { root =>
            Given("a nonlegacy audio selection and all Storyboard inputs created before final generation")
            val project = _fixture(root, renderer, storyboard = true)
            _operating(root, mixed = false)
            When("public final build generates a complete current final record")
            CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false, mode = Some("final"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), new RouteRunner())
            Given("matching review props and the complete generated final record")
            _storyboard_review_props(project)
            val baselinerunner = CozyVideoSpec.ReviewEvidenceRunner()
            When("public review evidence admits the unmodified Storyboard final record")
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("baseline-review"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), baselinerunner)
            Then("the complete baseline permits actual extraction")
            baselinerunner.commands should not be empty
            Given("only the final manifest loses all three joint fields while matching props remain complete")
            val manifest = root.resolve("target/cozy-video/final/manifest.json")
            _write(manifest, _json(manifest).mapObject(fields => fields.add("encoding", _without_joint(fields("encoding").get, "origins"))))
            val runner = CozyVideoSpec.ReviewEvidenceRunner()
            When("public review evidence attempts to admit the incomplete Storyboard final record")
            val error = intercept[Throwable] {
              CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")),
                CozyVideo.VideoToolRegistry(Vector.empty), runner)
            }
            Then("final-manifest admission rejects missing current evidence before any extraction")
            error.getMessage should include("Storyboard final manifest is incomplete, stale, or noncanonical")
            runner.commands shouldBe empty
          }
        }
      }

      "reject partial, null and inconsistent supplied joint fields even for legacy320 final records" in {
        val renderers = Vector(_renderer(CozyVideoQuality.Standard, None),
          Json.obj("engine" -> Json.fromString("remotion"), "policy" -> Json.fromString("lightweight"),
            "fps" -> Json.fromInt(29), "width" -> Json.fromInt(4), "height" -> Json.fromInt(4)))
        val jointfields = Vector("qualityProfile", "audioBitrate", "origins")
        val changes: Vector[(String, Json => Json)] = jointfields.flatMap { field => Vector(
          s"missing $field" -> ((encoding: Json) => encoding.mapObject(_.remove(field))),
          s"null $field" -> ((encoding: Json) => encoding.mapObject(_.add(field, Json.Null))),
          s"only $field supplied" -> ((encoding: Json) => _without_joint(encoding, "origins")
            .mapObject(_.add(field, encoding.hcursor.downField(field).focus.get))))
        } ++ Vector(
          "wrong canonical tier" -> ((encoding: Json) => encoding.mapObject(_.add("qualityProfile", Json.fromString("lightweight")))),
          "unknown canonical tier" -> ((encoding: Json) => encoding.mapObject(_.add("qualityProfile", Json.fromString("unknown")))),
          "wrong audio" -> ((encoding: Json) => encoding.mapObject(_.add("audioBitrate", Json.fromString("192k")))),
          "incomplete origins" -> ((encoding: Json) => encoding.mapObject(_.add("origins", Json.obj()))),
          "wrong origin" -> ((encoding: Json) => encoding.mapObject(fields => fields.add("origins",
            fields("origins").get.mapObject(_.add("audioBitrate", Json.fromString("explicit")))))))
        for { renderer <- renderers; (label, change) <- changes } _with_project { root =>
          Given(s"a current or legacy320 Storyboard selection whose supplied final observation will have $label")
          val project = _fixture(root, renderer, storyboard = true)
          _operating(root, mixed = false)
          When("public final build creates the actual current manifest and companion records")
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false, mode = Some("final"), toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty), new RouteRunner())
          Given("matching review props and the complete generated final record")
          _storyboard_review_props(project)
          val baselinerunner = CozyVideoSpec.ReviewEvidenceRunner()
          When("public review evidence admits the unmodified Storyboard final record")
          CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("baseline-review"), toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty), baselinerunner)
          Then("the complete baseline permits actual extraction")
          baselinerunner.commands should not be empty
          Given(s"matching props remain complete while only the final encoding has $label")
          val manifest = root.resolve("target/cozy-video/final/manifest.json")
          _write(manifest, _json(manifest).mapObject(fields => fields.add("encoding", change(fields("encoding").get))))
          val runner = CozyVideoSpec.ReviewEvidenceRunner()
          When("public review evidence interprets the supplied inconsistent final observations")
          val error = intercept[Throwable] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }
          Then("the Storyboard final record is rejected before any extraction")
          withClue(label) { error.getMessage should include("Storyboard final manifest is incomplete, stale, or noncanonical") }
          runner.commands shouldBe empty
        }
      }

      "retain every original six-field check for missing-all legacy final records" in {
        val changes = Vector("policy" -> Json.fromString("unknown"), "fps" -> Json.fromInt(0),
          "width" -> Json.fromInt(0), "height" -> Json.fromInt(0), "crf" -> Json.fromInt(999),
          "x264Preset" -> Json.fromString("slow"))
        for { policy <- Vector("lightweight", "standard", "quality"); (field, value) <- changes } _with_project { root =>
          Given(s"a $policy legacy Storyboard selection with all inputs prepared before final generation")
          val renderer = Json.obj("engine" -> Json.fromString("remotion"), "policy" -> Json.fromString(policy),
            "fps" -> Json.fromInt(29), "width" -> Json.fromInt(4), "height" -> Json.fromInt(4))
          val project = _fixture(root, renderer, storyboard = true)
          _operating(root, mixed = false)
          When("public final build generates the legacy320 final manifest and companion records")
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false, mode = Some("final"), toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty), new RouteRunner())
          Given("matching review props and the complete generated final record")
          _storyboard_review_props(project)
          val baselinerunner = CozyVideoSpec.ReviewEvidenceRunner()
          When("public review evidence admits the unmodified Storyboard final record")
          CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("baseline-review"), toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty), baselinerunner)
          Then("the complete baseline permits actual extraction")
          baselinerunner.commands should not be empty
          Given(s"only $field differs in its six-field final encoding while review props remain complete")
          val manifest = root.resolve("target/cozy-video/final/manifest.json")
          _write(manifest, _json(manifest).mapObject(fields => fields.add("encoding",
            _without_joint(fields("encoding").get, "origins").mapObject(_.add(field, value)))))
          val runner = CozyVideoSpec.ReviewEvidenceRunner()
          When("public review evidence attempts to admit the altered legacy final record")
          val error = intercept[Throwable] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }
          Then("legacy joint-field omission never bypasses the original encoding checks")
          withClue(field) { error.getMessage should include("Storyboard final manifest is incomplete, stale, or noncanonical") }
          runner.commands shouldBe empty
        }
      }

      "retain required non-encoding mode fields and require an actual encoding object" in {
        val changes: Vector[(String, Json => Json)] = Vector(
          "missing buildSettings" -> ((record: Json) => record.mapObject(_.remove("buildSettings"))),
          "missing encoding" -> ((record: Json) => record.mapObject(_.remove("encoding"))),
          "null encoding" -> ((record: Json) => record.mapObject(_.add("encoding", Json.Null))),
          "nonobject encoding" -> ((record: Json) => record.mapObject(_.add("encoding", Json.arr()))))
        changes.foreach { case (label, change) =>
          _with_project { root =>
            Given(s"a current Storyboard selection whose final mode record will have $label")
            val project = _fixture(root, _renderer(CozyVideoQuality.Standard, None), storyboard = true)
            _operating(root, mixed = false)
            When("public final build produces all current mode and companion records")
            CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false, mode = Some("final"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), new RouteRunner())
            Given("matching review props and the complete generated final record")
            _storyboard_review_props(project)
            val baselinerunner = CozyVideoSpec.ReviewEvidenceRunner()
            When("public review evidence admits the unmodified Storyboard final record")
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("baseline-review"), toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), baselinerunner)
            Then("the complete baseline permits actual extraction")
            baselinerunner.commands should not be empty
            Given(s"matching props are prepared while the final mode record has $label")
            val manifest = root.resolve("target/cozy-video/final/manifest.json")
            _write(manifest, change(_json(manifest)))
            val runner = CozyVideoSpec.ReviewEvidenceRunner()
            When("public review evidence attempts to admit the structurally incomplete final mode record")
            val error = intercept[Throwable] {
              CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")),
                CozyVideo.VideoToolRegistry(Vector.empty), runner)
            }
            Then("the unchanged mode-field contract rejects it before extraction")
            withClue(label) { error.getMessage should include("Storyboard final manifest is incomplete, stale, or noncanonical") }
            runner.commands shouldBe empty
          }
        }
      }
    }

    "roundtrip Document Project renderer and validate publication intent without inventing legacy provenance" which {
      "preserve authored joint selection through the normal publication workspace and metadata preparation" in {
        Given("a Document Project .video descriptor with canonical practice selection and a normalized independent override")
        _with_project { root =>
          When("the normal publication pipeline renders its workspace with injected producers and prepares metadata")
          val rendered = _publication(root)
          val prepared = CozyVideoPublisher.preparePublication(rendered)
          Then("the authored renderer decodes identically and complete joint metadata is retained")
          val workspace = _json(rendered._3)
          val renderer = workspace.hcursor.downField("renderer").focus.get.as[CozyVideo.VideoRenderer].toOption.get
          renderer.qualityProfile shouldBe Some(CozyVideoQuality.Lightweight)
          renderer.audioBitrate shouldBe Some("160k")
          prepared.result.metadataPublication.isDefined shouldBe true
          val encoding = _json(rendered._2.resolve("build/manifest.json")).hcursor.downField("encoding").focus.get
          encoding.hcursor.get[String]("audioBitrate").toOption shouldBe Some("160k")
          _metadata_encoding(prepared) shouldBe encoding
        }
      }

      "pass through six-field legacy metadata and reject partial or malformed joint metadata" in {
        Given("one rendered publication workspace whose input manifest can carry old or current intent")
        _with_project { root =>
          val rendered = _publication(root)
          val manifest = rendered._2.resolve("build/manifest.json")
          val original = _json(manifest)
          val encoding = original.hcursor.downField("encoding").focus.get
          val legacy = _without_joint(encoding, "origins")
          _write(manifest, original.mapObject(_.add("encoding", legacy)))
          When("metadata preparation consumes the unchanged six-field legacy schema")
          val prepared = CozyVideoPublisher.preparePublication(rendered)
          Then("legacy data remains admissible without fabricated new fields")
          prepared.result.metadataPublication.isDefined shouldBe true
          _metadata_encoding(prepared) shouldBe legacy
          _json(manifest).hcursor.downField("encoding").focus shouldBe Some(legacy)
          val invalid = Vector(
            encoding.mapObject(_.remove("qualityProfile")), encoding.mapObject(_.remove("audioBitrate")), encoding.mapObject(_.remove("origins")),
            encoding.mapObject(_.add("qualityProfile", Json.Null)), encoding.mapObject(_.add("qualityProfile", Json.fromString("Standard"))),
            encoding.mapObject(_.add("audioBitrate", Json.fromString(" 160k "))), encoding.mapObject(_.add("audioBitrate", Json.fromInt(160000))),
            encoding.mapObject(_.add("audioBitrate", Json.fromString("9223372036854775808"))),
            encoding.mapObject(_.add("origins", Json.obj())), encoding.mapObject(fields => fields.add("origins", fields("origins").get.mapObject(_.add("fps", Json.fromString("observed"))))))
          invalid.foreach { value =>
            _write(manifest, original.mapObject(_.add("encoding", value)))
            When("metadata preparation consumes partial or malformed supplied joint fields")
            val error = intercept[Throwable](CozyVideoPublisher.preparePublication(rendered))
            Then("ordinary input-schema admission rejects the record")
            error.getMessage should include("Video build manifest encoding.")
          }
        }
      }
    }
  }
}
