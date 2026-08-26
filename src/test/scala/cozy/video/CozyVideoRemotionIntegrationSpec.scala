package cozy.video

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary
import io.circe.Json
import io.circe.parser

/*
 * @since   Jul. 18, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoRemotionIntegrationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Remotion integration" should {
    "render every part of both scaffold composition profiles with the real toolchain" in {
      val image = _integration_image()
      _with_temp_dir("profiles") { dir =>
        val profiles = Vector(
          "explanation" -> Vector("explanation"),
          "explanation-demo-explanation" -> Vector(
            "introduction",
            "demonstration",
            "conclusion"
          )
        )

        profiles.foreach { case (profile, partids) =>
          Given(s"the $profile scaffold with valid local silence audio and placeholder assets")
          val pkg = dir.resolve(s"$profile.video")
          CozyVideoScaffold.scaffold(
            CozyVideoScaffold.Config.create(
              List(profile, s"--save=$pkg", s"--profile=$profile")
            )
          )
          _write_integration_credit_profile(pkg)
          _write(
            pkg.resolve("video.yaml"),
            _read(pkg.resolve("video.yaml")).replace(
              "credits:\n  include: []",
              "credits:\n  profile: integration\n  include: [integration-credit]"
            )
          )
          partids.foreach(partid => _write_audio_manifest(pkg, partid))
          if (profile == "explanation-demo-explanation")
            _write_integration_web_demo_recording(pkg)

          When(s"the real Remotion CLI in $image renders every part")
          val result = CozyVideo.render(
            CozyVideo.RenderConfig(
              pkg.resolve("video.yaml"),
              "remotion",
              checkTools = true,
              toolMode = Some("docker"),
              dockerImage = Some(image)
            ),
            CozyVideo.VideoToolRegistry.default,
            CozyVideo.VideoProcessRunner.default
          )

          And("the real managed ffmpeg and ffprobe route assembles the rendered parts")
          CozyVideo.build(
            CozyVideo.BuildConfig(
              pkg.resolve("video.yaml"),
              dryRun = false,
              checkTools = true,
              toolMode = Some("docker"),
              dockerImage = Some(image),
              mode = Some("confirmation")
            ),
            CozyVideo.VideoToolRegistry.default,
            CozyVideo.VideoProcessRunner.default
          )
          _approve_confirmation_review(pkg)
          val build = CozyVideo.build(
            CozyVideo.BuildConfig(
              pkg.resolve("video.yaml"),
              dryRun = false,
              checkTools = true,
              toolMode = Some("docker"),
              dockerImage = Some(image),
              mode = Some("final")
            ),
            CozyVideo.VideoToolRegistry.default,
            CozyVideo.VideoProcessRunner.default
          )

          Then("Cozy reports every part and verifies the final MP4")
          result should include_text(s"parts: ${partids.size}")
          build should include_text("Cozy Video Build")
          partids.foreach { partid =>
            val output = pkg.resolve(s"build/parts/$partid.mp4")
            output should be_regular_file
            Files.size(output) should be > 0L
          }
          val finaloutput = pkg.resolve(s"build/$profile.mp4")
          finaloutput should be_regular_file
          Files.size(finaloutput) should be > 0L
          _json(pkg.resolve("target/cozy-video/final/manifest.json")).hcursor.
            get[String]("status").toOption shouldBe Some("validated")
          _read(pkg.resolve("build/credits/credits.json")) should include_text("integration-credit")
          val finalprops = io.circe.parser.parse(_read(
            pkg.resolve(s"target/cozy-video/remotion/${partids.last}/props.json")
          )).toOption.get
          val effectivefps = finalprops.hcursor.get[Int]("fps").toOption.get
          val creditholdseconds = finalprops.hcursor.downField("credits").
            get[Double]("holdSeconds").toOption.get
          creditholdseconds shouldBe 1.0
          finalprops.hcursor.downField("timing").get[Int]("creditPageHoldFrames").toOption.get shouldBe
            math.round(creditholdseconds * effectivefps).toInt
          finalprops.hcursor.downField("credits").downField("items").as[Vector[io.circe.Json]].toOption.get should have size 1
        }
      }
    }

    "render a configured project-owned visual asset with the real toolchain" in {
      val image = _integration_image()
      _with_temp_dir("project-asset") { dir =>
        Given("an explanation scaffold whose summary slot is a required project-owned SVG")
        val pkg = dir.resolve("project-asset.video")
        CozyVideoScaffold.scaffold(
          CozyVideoScaffold.Config.create(
            List("project-asset", s"--save=$pkg", "--profile=explanation")
          )
        )
        val customsvg =
          """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 720"><rect width="1280" height="720" fill="#dbe9e5"/><text x="120" y="360" font-size="72">PROJECT ASSET</text></svg>
            |""".stripMargin
        _write(pkg.resolve("assets/project-summary.svg"), customsvg)
        val descriptor = _read(pkg.resolve("video.yaml"))
          .replace(
            "path: assets/summary.svg\n    kind: placeholder\n    required: false",
            "path: assets/project-summary.svg\n    kind: illustration\n    required: true"
          )
          .replaceFirst(
            "(?s)(summary:\\s+path: assets/project-summary.svg.*?license:) [^\\n]+",
            "$1 LicenseRef-Project-Owned"
          )
          .replaceFirst(
            "(?s)(summary:\\s+path: assets/project-summary.svg.*?provenance:) [^\\n]+",
            "$1 project:phase-17-integration"
          )
        _write(pkg.resolve("video.yaml"), descriptor)
        _write_audio_manifest(pkg, "explanation")

        When(s"the real Remotion CLI in $image renders the configured asset")
        CozyVideo.render(
          CozyVideo.RenderConfig(
            pkg.resolve("video.yaml"),
            "remotion",
            checkTools = true,
            toolMode = Some("docker"),
            dockerImage = Some(image)
          ),
          CozyVideo.VideoToolRegistry.default,
          CozyVideo.VideoProcessRunner.default
        )

        Then("the non-empty MP4 and copied renderer asset preserve the project contract")
        val output = pkg.resolve("build/parts/explanation.mp4")
        output should be_regular_file
        Files.size(output) should be > 0L
        _read(
          pkg.resolve("target/cozy-video/remotion/explanation/public/assets/summary.svg")
        ) shouldBe customsvg
      }
    }

    "encoding policy runtime acceptance with the real toolchain" in {
      val image = _integration_image()
      _with_temp_dir("encoding-policy-runtime-acceptance") { dir =>
        Given("one representative character-dialogue fixture with a caption and declarative diagram")
        val packages = Vector(
          "lightweight" -> None,
          "standard" -> Some("standard")
        ).map { case (name, policy) =>
          val pkg = dir.resolve(s"$name.video")
          _write_encoding_policy_fixture(pkg, policy)
          name -> pkg
        }

        When(s"the real Docker Remotion renderer in $image renders lightweight and standard")
        packages.foreach { case (_, pkg) =>
          CozyVideo.render(
            CozyVideo.RenderConfig(
              pkg.resolve("video_project.json"),
              "remotion",
              checkTools = true,
              toolMode = Some("docker"),
              dockerImage = Some(image)
            ),
            CozyVideo.VideoToolRegistry.default,
            CozyVideo.VideoProcessRunner.default
          )
        }

        And("the real Docker ffmpeg and ffprobe build route assembles both final MP4s")
        packages.foreach { case (_, pkg) =>
          CozyVideo.build(
            CozyVideo.BuildConfig(
              pkg.resolve("video_project.json"),
              dryRun = false,
              checkTools = true,
              toolMode = Some("docker"),
              dockerImage = Some(image)
            ),
            CozyVideo.VideoToolRegistry.default,
            CozyVideo.VideoProcessRunner.default
          )
        }

        Then("both build manifests prove non-empty H.264 MP4 output at the policy frame rates")
        val packagesbyname = packages.toMap
        val lightweight = packagesbyname("lightweight")
        val standard = packagesbyname("standard")
        val lightweightoutput = lightweight.resolve("build/lightweight.mp4")
        val standardoutput = standard.resolve("build/standard.mp4")
        lightweightoutput should be_regular_file
        standardoutput should be_regular_file
        val lightweightsize = Files.size(lightweightoutput)
        val standardsize = Files.size(standardoutput)
        lightweightsize should be > 0L
        standardsize should be > 0L
        val lightweightmanifest = _json(lightweight.resolve("build/manifest.json"))
        val standardmanifest = _json(standard.resolve("build/manifest.json"))
        val lightweightprobe = lightweightmanifest.hcursor.downField("ffprobe").focus.get
        val standardprobe = standardmanifest.hcursor.downField("ffprobe").focus.get
        val lightweightstream = _ffprobe_video_stream(lightweightprobe)
        val standardstream = _ffprobe_video_stream(standardprobe)
        lightweightprobe.hcursor.downField("format").get[String]("format_name").toOption.get should include_text("mp4")
        standardprobe.hcursor.downField("format").get[String]("format_name").toOption.get should include_text("mp4")
        lightweightstream.hcursor.get[String]("codec_name").toOption shouldBe Some("h264")
        standardstream.hcursor.get[String]("codec_name").toOption shouldBe Some("h264")
        lightweightstream.hcursor.get[Int]("width").toOption shouldBe Some(1280)
        lightweightstream.hcursor.get[Int]("height").toOption shouldBe Some(720)
        standardstream.hcursor.get[Int]("width").toOption shouldBe Some(1280)
        standardstream.hcursor.get[Int]("height").toOption shouldBe Some(720)
        _ffprobe_fps(lightweightstream) shouldBe 18.0
        _ffprobe_fps(standardstream) shouldBe 30.0

        And("the same fixture has equivalent runtime duration and a materially smaller lightweight encoding")
        val lightweightduration = _ffprobe_duration(lightweightprobe)
        val standardduration = _ffprobe_duration(standardprobe)
        math.abs(lightweightduration - standardduration) should be <= 0.25
        lightweightsize.toDouble should be <= standardsize.toDouble * 0.91

        And("each retained Remotion props artifact preserves the caption and declarative diagram input")
        packages.foreach { case (_, pkg) =>
          val props = _json(pkg.resolve("target/cozy-video/remotion/dialogue/props.json"))
          props.hcursor.downField("scenes").downArray.get[String]("caption").toOption shouldBe
            Some("Lightweight keeps captions and diagrams readable.")
          props.hcursor.downField("scenes").downArray.downField("visual").get[String]("kind").toOption shouldBe Some("diagram")
          props.hcursor.downField("scenes").downArray.downField("visual").downField("diagram").get[String]("layout").toOption shouldBe Some("flow")
          props.hcursor.downField("scenes").downArray.downField("visual").downField("diagram").downField("nodes").downArray.get[String]("label").toOption shouldBe Some("Source")
        }
      }
    }
  }

  private def _integration_image(): String = {
    if (!sys.props.get("cozy.video.remotion.integration").contains("true"))
      cancel(
        "Set -Dcozy.video.remotion.integration=true to run the Docker Remotion integration."
      )
    sys.props.getOrElse(
      "cozy.video.remotion.image",
      CozyVideo.VideoToolSettings.DEFAULT_DOCKER_IMAGE
    )
  }

  private def _write_audio_manifest(pkg: Path, partid: String, targetduration: Double = 1.0, speaker: Option[String] = None): Unit = {
    val dir = pkg.resolve(s"build/audio/$partid")
    Files.createDirectories(dir)
    val filename = s"01-$partid.wav"
    val speakervalue = speaker.map(value => "\"" + value + "\"").getOrElse("null")
    Files.write(dir.resolve(filename), _wav_bytes(0.2))
    Files.writeString(
      dir.resolve("manifest.json"),
      s"""[{"sceneId":"$partid","speaker":$speakervalue,"file":"$filename","leadSilence":0.0,"audioDuration":0.2,"targetDuration":$targetduration,"tailSilence":0.0}]""",
      StandardCharsets.UTF_8
    )
  }

  private def _write_integration_web_demo_recording(pkg: Path): Unit = {
    val source = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve(
      "src/test/resources/cozy/publication/article-media-bilingual-bok/repository/video/article-media-bilingual-example-en-video/1.0.0/article-media-bilingual-example-en-video-1.0.0.mp4"
    )
    val recording = pkg.resolve("build/record/demonstration/reviewed.mp4")
    Files.createDirectories(recording.getParent)
    Files.copy(source, recording)
  }

  private def _write_integration_credit_profile(pkg: Path): Unit =
    _write(
      pkg.resolve("conf/cozy/video/credit-profiles/integration.yaml"),
      """schema: cozy.video.credits.v1
        |profile: integration
        |presentation:
        |  title: {default: Credits}
        |  hold-seconds: 1.0
        |credits:
        |  - id: integration-credit
        |    label: {default: Integration material}
        |    publication-text: {default: Integration material}
        |    surfaces: [video, publication, rdf]
        |""".stripMargin
    )

  private def _approve_confirmation_review(pkg: Path): Unit = {
    val manifest = parser.parse(
      _read(pkg.resolve("target/cozy-video/confirmation/manifest.json"))
    ).toOption.get
    val identity = manifest.hcursor.get[String]("identity").toOption.get
    _write(
      pkg.resolve("video.yaml"),
      _read(pkg.resolve("video.yaml")) + s"confirmationReview:\n  approvedIdentity: $identity\n"
    )
  }

  private def _write(path: Path, contents: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, contents, StandardCharsets.UTF_8)
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _write_encoding_policy_fixture(pkg: Path, policy: Option[String]): Unit = {
    val renderer = policy.map(x => ",\"policy\":\"" + x + "\"").getOrElse("")
    _write(
      pkg.resolve("video_project.json"),
      s"""{"renderer":{"engine":"remotion"$renderer},"parts":[{"id":"dialogue","type":"dialogue","script":"dialogue/script.json"}],"output":"build/${pkg.getFileName.toString.stripSuffix(".video")}.mp4"}"""
    )
    _write(
      pkg.resolve("dialogue/script.json"),
      """{
        |  "characters": {
        |    "guide": {"asset":"assets/guide.svg","mouthClosedAsset":"assets/guide.svg","mouthOpenAsset":"assets/guide.svg","side":"left"},
        |    "reviewer": {"asset":"assets/reviewer.svg","side":"right"}
        |  },
        |  "sections": [{"id":"encoding","title":"Encoding policy"}],
        |  "scenes": [{
        |    "id":"policy-scene",
        |    "speaker":"guide",
        |    "line":"Encoding policy keeps captions and diagrams readable.",
        |    "caption":"Lightweight keeps captions and diagrams readable.",
        |    "section":"encoding",
        |    "duration":4.0,
        |    "visual":{"kind":"diagram","heading":"Encoding flow","diagram":{"layout":"flow","nodes":[{"id":"source","label":"Source","role":"lead","labelPolicy":"atomic"},{"id":"render","label":"Render","role":"lead","labelPolicy":"atomic"},{"id":"output","label":"Output","role":"lead","labelPolicy":"atomic"}],"edges":[{"from":"source","to":"render"},{"from":"render","to":"output"}]}}
        |  }]
        |}""".stripMargin
    )
    _write(
      pkg.resolve("dialogue/assets/guide.svg"),
      """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 480"><rect width="320" height="480" fill="#284b63"/><circle cx="160" cy="150" r="90" fill="#f3c6a8"/><rect x="70" y="260" width="180" height="180" rx="24" fill="#f4d35e"/></svg>"""
    )
    _write(
      pkg.resolve("dialogue/assets/reviewer.svg"),
      """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 480"><rect width="320" height="480" fill="#5b2c6f"/><circle cx="160" cy="150" r="90" fill="#e8b894"/><rect x="70" y="260" width="180" height="180" rx="24" fill="#82c0cc"/></svg>"""
    )
    _write_audio_manifest(pkg, "dialogue", 4.0, Some("guide"))
  }

  private def _json(path: Path): Json =
    parser.parse(_read(path)).fold(
      error => throw new IllegalArgumentException(s"Invalid JSON at $path: ${error.getMessage}"),
      identity
    )

  private def _ffprobe_video_stream(probe: Json): Json =
    probe.hcursor.downField("streams").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      .find(_.hcursor.get[String]("codec_type").toOption.contains("video")).get

  private def _ffprobe_fps(stream: Json): Double = {
    val value = stream.hcursor.get[String]("r_frame_rate").toOption.get
    val parts = value.split("/", 2)
    parts(0).toDouble / parts.lift(1).map(_.toDouble).getOrElse(1.0)
  }

  private def _ffprobe_duration(probe: Json): Double =
    probe.hcursor.downField("format").get[String]("duration").toOption.get.toDouble

  private def _wav_bytes(duration: Double): Array[Byte] = {
    val samplerate = 24000
    val frames = math.max(1, (duration * samplerate).toInt)
    val data = Array.fill(frames * 2)(0.toByte)
    val out = new ByteArrayOutputStream()
    _write_ascii(out, "RIFF")
    _write_int_le(out, 36 + data.length)
    _write_ascii(out, "WAVE")
    _write_ascii(out, "fmt ")
    _write_int_le(out, 16)
    _write_short_le(out, 1)
    _write_short_le(out, 1)
    _write_int_le(out, samplerate)
    _write_int_le(out, samplerate * 2)
    _write_short_le(out, 2)
    _write_short_le(out, 16)
    _write_ascii(out, "data")
    _write_int_le(out, data.length)
    out.write(data)
    out.toByteArray
  }

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

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths
      .get(sys.props("user.dir"))
      .toAbsolutePath
      .normalize()
      .resolve("target/test-generated/video-remotion-integration")
      .resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.reverse.foreach(Files.delete)
      } finally {
        stream.close()
      }
    }
}
