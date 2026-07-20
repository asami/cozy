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
 * @version Jul. 20, 2026
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
          Given(s"a $profile scaffold with valid local silence audio and placeholder assets")
          val pkg = dir.resolve(s"$profile.video")
          CozyVideoScaffold.scaffold(
            CozyVideoScaffold.Config.create(
              List(profile, s"--save=$pkg", s"--profile=$profile")
            )
          )
          partids.foreach(partid => _write_audio_manifest(pkg, partid))

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
          val build = CozyVideo.build(
            CozyVideo.BuildConfig(
              pkg.resolve("video.yaml"),
              dryRun = false,
              checkTools = true,
              toolMode = Some("docker"),
              dockerImage = Some(image)
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
          _read(pkg.resolve("build/manifest.json")) should include_text("\"ffprobe\"")
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

  private def _write_audio_manifest(pkg: Path, partid: String): Unit = {
    val dir = pkg.resolve(s"build/audio/$partid")
    Files.createDirectories(dir)
    val filename = s"01-$partid.wav"
    Files.write(dir.resolve(filename), _wav_bytes(0.2))
    Files.writeString(
      dir.resolve("manifest.json"),
      s"""[{"sceneId":"$partid","speaker":null,"file":"$filename","leadSilence":0.0,"audioDuration":0.2,"targetDuration":1.0,"tailSilence":0.0}]""",
      StandardCharsets.UTF_8
    )
  }

  private def _write(path: Path, contents: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, contents, StandardCharsets.UTF_8)
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

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
