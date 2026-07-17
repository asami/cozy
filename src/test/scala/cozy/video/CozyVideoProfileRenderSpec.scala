package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import io.circe.{Json, parser}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoProfileRenderSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy profile-driven video rendering" should {
    "render both composition profiles with placeholder assets and deterministic timing" in {
      _with_temp_dir("composition-profiles") { dir =>
        val profiles = Vector(
          "explanation" -> Vector("explanation"),
          "explanation-demo-explanation" -> Vector("introduction", "demonstration", "conclusion")
        )

        profiles.foreach { case (profile, partids) =>
          Given(s"a scaffold using the $profile profile and only generated placeholder assets")
          val pkg = dir.resolve(s"$profile.video")
          CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
            profile,
            s"--save=$pkg",
            s"--profile=$profile"
          )))
          _write_audio_manifests(pkg, partids)
          val runner = ProfileRenderRunner()

          When("the Remotion adapter renders every profile part")
          val result = CozyVideo.render(
            CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          Then("the adapter consumes every visual primitive and resolved placeholder")
          result should include_text(s"parts: ${partids.size}")
          runner.commands should have size partids.size
          partids.zipWithIndex.foreach { case (partid, index) =>
            val workdir = pkg.resolve(s"target/cozy-video/remotion/$partid")
            val props = _json(workdir.resolve("props.json"))
            _primitive_names(props) shouldBe Set(
              "flow-line",
              "underline-sweep",
              "summary-layout",
              "fade-rise",
              "spring-pop",
              "end-card",
              "hold"
            )
            _int(props, "timing", "sectionStartFrames") shouldBe 36
            _int(props, "timing", "summaryFrames") shouldBe 72
            val finalframes = if (index == partids.size - 1) 60 else 0
            _int(props, "timing", "finalPageHoldFrames") shouldBe finalframes
            _int(props, "timing", "totalFrames") shouldBe 240 + finalframes
            workdir.resolve("public/assets/section-start.svg") should be_regular_file
            workdir.resolve("public/assets/summary.svg") should be_regular_file
            workdir.resolve("public/assets/final-page.svg") should be_regular_file
            pkg.resolve(s"build/parts/$partid.manifest.json") should be_regular_file
            pkg.resolve(s"build/parts/$partid.mp4") should be_regular_file
          }

          And("the generated Remotion component has an implementation for each declared capability")
          val root = _read(pkg.resolve(s"target/cozy-video/remotion/${partids.head}/src/Root.tsx"))
          Vector("flow-line", "underline-sweep", "summary-layout", "fade-rise", "spring-pop", "end-card", "hold").
            foreach(x => root should include_text(x))
          val renderscript = _read(
            pkg.resolve(s"target/cozy-video/remotion/${partids.head}/src/render.mjs")
          )
          renderscript should include_text("--public-dir=${publicDir}")
          renderscript should include_text("--dns-result-order=ipv4first")
        }
      }
    }

    "render a configured project-owned asset without changing its bytes or provenance" in {
      _with_temp_dir("project-asset") { dir =>
        val pkg = dir.resolve("asset-demo.video")
        Given("a scaffold whose summary slot points to a project-owned SVG")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "asset-demo",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        val custom = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>PROJECT ART</text></svg>\n"
        _write(pkg.resolve("assets/custom-summary.svg"), custom)
        val descriptor = _read(pkg.resolve("video.yaml")).
          replace(
            "path: assets/summary.svg\n    kind: placeholder",
            "path: assets/custom-summary.svg\n    kind: illustration"
          ).
          replaceFirst(
            "(?s)(summary:\\s+path: assets/custom-summary.svg\\s+kind: illustration\\s+required: false\\s+license:) [^\\n]+",
            "$1 LicenseRef-Project-Owned"
          ).
          replaceFirst(
            "(?s)(summary:\\s+path: assets/custom-summary.svg.*?provenance:) [^\\n]+",
            "$1 project:design-system"
          )
        _write(pkg.resolve("video.yaml"), descriptor)
        _write_audio_manifests(pkg, Vector("explanation"))

        When("the Remotion adapter prepares the render workspace")
        CozyVideo.render(
          CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          ProfileRenderRunner()
        )

        Then("the copied asset and props preserve the configured contract")
        val workdir = pkg.resolve("target/cozy-video/remotion/explanation")
        _read(workdir.resolve("public/assets/summary.svg")) shouldBe custom
        val summary = _assets(_json(workdir.resolve("props.json"))).find(_._1 == "summary").get._2
        summary.hcursor.get[String]("kind").toOption.get shouldBe "illustration"
        summary.hcursor.get[String]("license").toOption.get shouldBe "LicenseRef-Project-Owned"
        summary.hcursor.get[String]("provenance").toOption.get shouldBe "project:design-system"
      }
    }

    "preserve profile contracts through inspect build RDF and publication" in {
      _with_temp_dir("lifecycle") { dir =>
        val pkg = dir.resolve("src/main/doxsite/technology/tutorial.video")
        Given("a scaffolded explanation package entering the full publication lifecycle")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "tutorial",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        val project = pkg.resolve("video.yaml")

        When("Cozy inspects plans and describes the package as RDF")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(project, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val build = CozyVideo.build(
          CozyVideo.BuildConfig(project, dryRun = true, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        CozyVideo.rdf(CozyVideo.RdfConfig(project, pkg.resolve("rdf")))

        Then("profile effects and assets remain observable without external asset access")
        inspection should include_text("profile: explanation")
        inspection should include_text("visualEffectCapability: supported")
        build should include_text("Cozy Video Build Dry-Run")
        val turtle = _read(pkg.resolve("rdf/video.ttl"))
        turtle should include_text("compositionProfile")
        turtle should include_text("visualEffectPrimitive")
        turtle should include_text("hasVisualAsset")
        turtle should include_text("provenance")

        When("the profile-driven source package is published")
        val result = CozyVideoPublisher.publish(
          CozyVideoPublisher.PublishVideoConfig(
            pkg,
            dir.resolve("src/main/publication"),
            dir.resolve("warehouse"),
            Some("0.1.0-SNAPSHOT"),
            force = false
          ),
          CozyVideoSpec.RecordingVoicevoxClient(),
          ProfileRenderRunner()
        )

        Then("the publication workspace renders the same profile and assets")
        result.warehouseArtifact should be_regular_file
        val generated = _read(result.projectFile)
        generated should include_text("\"profile\" : \"explanation\"")
        generated should include_text("\"visualEffects\"")
        generated should include_text("\"assets\"")
        result.workspaceRoot.resolve("target/cozy-video/remotion/explanation/public/assets/summary.svg") should be_regular_file
      }
    }
  }

  private def _write_audio_manifests(pkg: Path, partids: Vector[String]): Unit =
    partids.foreach { partid =>
      val dir = pkg.resolve(s"build/audio/$partid")
      Files.createDirectories(dir)
      Files.write(dir.resolve(s"01-$partid.wav"), Array[Byte](0, 1, 2, 3))
      _write(
        dir.resolve("manifest.json"),
        s"""[{"sceneId":"$partid","speaker":null,"file":"01-$partid.wav","leadSilence":0.0,"audioDuration":8.0,"targetDuration":8.0,"tailSilence":0.0}]"""
      )
    }

  private def _primitive_names(json: Json): Set[String] =
    json.hcursor.downField("visualEffects").as[Vector[Json]].toOption.get.
      flatMap(_.hcursor.downField("primitives").as[Vector[Json]].toOption.get).
      flatMap(_.hcursor.get[String]("name").toOption).
      toSet

  private def _assets(json: Json): Vector[(String, Json)] =
    json.hcursor.downField("assets").as[Vector[Json]].toOption.get.map { asset =>
      asset.hcursor.get[String]("role").toOption.get -> asset
    }

  private def _int(json: Json, parent: String, field: String): Int =
    json.hcursor.downField(parent).get[Int](field).toOption.get

  private def _json(path: Path): Json =
    parser.parse(_read(path)).toOption.get

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().
      resolve("target/test-generated/video-profile-render").resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _write(path: Path, contents: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, contents, StandardCharsets.UTF_8)
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).iterator().asScala.toVector.reverse.foreach(Files.delete)
}

private[video] final case class ProfileRenderRunner()
    extends CozyVideo.VideoProcessRunner {
  val commands = ArrayBuffer.empty[CozyVideoSpec.RecordingCommand]

  def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
    commands += CozyVideoSpec.RecordingCommand(args, cwd)
    if (args.contains("node")) {
      val script = args.find(_.endsWith("render.mjs")).map(_command_path(cwd, _)).get
      val props = parser.parse(Files.readString(script.getParent.getParent.resolve("props.json"), StandardCharsets.UTF_8)).toOption.get
      val output = props.hcursor.get[String]("outputPath").toOption.get
      _touch(_command_path(cwd, output))
      CozyVideo.VideoCommandResult(0, "remotion ok", "")
    } else if (args.contains("ffmpeg")) {
      _touch(_command_path(cwd, args.last))
      CozyVideo.VideoCommandResult(0, "ffmpeg ok", "")
    } else if (args.contains("ffprobe")) {
      CozyVideo.VideoCommandResult(0, """{"format":{"duration":"1.000"},"streams":[]}""", "")
    } else {
      CozyVideo.VideoCommandResult(0, "ok", "")
    }
  }

  private def _touch(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, Array[Byte](0, 1, 2, 3))
  }

  private def _command_path(cwd: Path, value: String): Path =
    if (value.startsWith("/workspace/"))
      cwd.resolve(value.stripPrefix("/workspace/")).normalize()
    else {
      val path = Paths.get(value).normalize()
      if (path.isAbsolute) path else cwd.resolve(path).normalize()
    }
}
