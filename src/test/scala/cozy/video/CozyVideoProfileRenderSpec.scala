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
 *  version Jul. 20, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoProfileRenderSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy profile-driven video rendering" should {
    "render composition profiles and timing" which {
    "carry one effective encoding plan through Remotion artifacts" in {
      _with_temp_dir("effective-encoding") { dir =>
        Given("a multi-part Remotion profile with a standard policy and explicit encoding overrides")
        val pkg = dir.resolve("effective-encoding.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "effective-encoding",
          s"--save=$pkg",
          "--profile=explanation-demo-explanation"
        )))
        _write(
          pkg.resolve("video.yaml"),
          _read(pkg.resolve("video.yaml")).replace(
            "renderer:\n  engine: remotion\n  policy: lightweight",
            "renderer:\n  engine: remotion\n  policy: standard\n  width: 1440\n  crf: 21\n  x264Preset: slow"
          )
        )
        val partids = Vector("introduction", "demonstration", "conclusion")
        _write_audio_manifests(pkg, partids)
        val recording = pkg.resolve("build/record/demonstration/reviewed.webm")
        Files.createDirectories(recording.getParent)
        Files.write(recording, Array[Byte](1, 2, 3))

        When("inspect and build dry-run plan the effective encoding")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(pkg.resolve("video.yaml"), checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val dryrun = CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = true, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )

        Then("both planning presentations expose the same effective policy and values")
        Vector(inspection, dryrun).foreach { output =>
          output should include_text("encodingPolicy: standard")
          output should include_text("encodingFps: 30")
          output should include_text("encodingDimensions: 1440x720")
          output should include_text("encodingCrf: 21")
          output should include_text("encodingX264Preset: slow")
        }

        When("the Remotion adapter renders and builds every planned part")
        val runner = ProfileRenderRunner()
        CozyVideo.render(
          CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        _approve_confirmation_review(pkg.resolve("video.yaml"))
        CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("final")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        Then("every part props, generated render arguments, and the build manifest agree with the effective encoding")
        partids.foreach { partid =>
          val workdir = pkg.resolve(s"target/cozy-video/remotion/$partid")
          val props = _json(workdir.resolve("props.json"))
          props.hcursor.get[String]("encodingPolicy").toOption shouldBe Some("standard")
          props.hcursor.get[Int]("fps").toOption shouldBe Some(30)
          props.hcursor.get[Int]("width").toOption shouldBe Some(1440)
          props.hcursor.get[Int]("height").toOption shouldBe Some(720)
          props.hcursor.get[Int]("crf").toOption shouldBe Some(21)
          props.hcursor.get[String]("x264Preset").toOption shouldBe Some("slow")
          val render = _read(workdir.resolve("src/render.mjs"))
          render should include_text("`--crf=${props.crf}`")
          render should include_text("`--x264-preset=${props.x264Preset}`")
        }
        val encoding = _json(pkg.resolve("target/cozy-video/final/manifest.json")).hcursor.
          downField("cacheInput").downField("encoding")
        encoding.get[String]("policy").toOption shouldBe Some("standard")
        encoding.get[Int]("fps").toOption shouldBe Some(30)
        encoding.get[Int]("width").toOption shouldBe Some(1440)
        encoding.get[Int]("height").toOption shouldBe Some(720)
        encoding.get[Int]("crf").toOption shouldBe Some(21)
        encoding.get[String]("x264Preset").toOption shouldBe Some("slow")
      }
    }

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
          if (profile == "explanation-demo-explanation") {
            val recording = pkg.resolve("build/record/demonstration/reviewed.webm")
            Files.createDirectories(recording.getParent)
            Files.write(recording, Array[Byte](1, 2, 3))
          }
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
              "title-card",
              "subtle-motion",
              "flow-line",
              "underline-sweep",
              "summary-layout",
              "fade-rise",
              "spring-pop",
              "end-card",
              "hold"
            )
            val openingframes = if (index == 0) 81 else 0
            _int(props, "timing", "openingFrames") shouldBe openingframes
            _int(props, "timing", "sectionStartFrame") shouldBe openingframes
            _int(props, "timing", "sectionStartFrames") shouldBe 22
            val summaryframes = if (index == partids.size - 1) 43 else 0
            _int(props, "timing", "summaryStartFrame") shouldBe openingframes + 144 - summaryframes
            _int(props, "timing", "summaryFrames") shouldBe summaryframes
            val finalframes = if (index == partids.size - 1) 36 else 0
            _int(props, "timing", "creditPageHoldFrames") shouldBe 0
            _int(props, "timing", "finalPageStartFrame") shouldBe openingframes + 144
            _int(props, "timing", "finalPageHoldFrames") shouldBe finalframes
            _int(props, "timing", "totalFrames") shouldBe openingframes + 144 + finalframes
            workdir.resolve("public/assets/opening.svg") should be_regular_file
            workdir.resolve("public/assets/section-start.svg") should be_regular_file
            workdir.resolve("public/assets/summary.svg") should be_regular_file
            workdir.resolve("public/assets/final-page.svg") should be_regular_file
            pkg.resolve(s"build/parts/$partid.manifest.json") should be_regular_file
            pkg.resolve(s"build/parts/$partid.mp4") should be_regular_file
          }

          And("the generated Remotion component has an implementation for each declared capability")
          val root = _read(pkg.resolve(s"target/cozy-video/remotion/${partids.head}/src/Root.tsx"))
          Vector("title-card", "subtle-motion", "flow-line", "underline-sweep", "summary-layout", "fade-rise", "spring-pop", "end-card", "hold").
            foreach(x => root should include_text(x))
          val renderscript = _read(
            pkg.resolve(s"target/cozy-video/remotion/${partids.head}/src/render.mjs")
          )
          renderscript should include_text("--public-dir=${publicDir}")
          renderscript should include_text("--dns-result-order=ipv4first")
          Files.isDirectory(pkg.resolve("build/credits")) shouldBe false
        }
      }
    }

    "hold a summary infographic after final narration when its profile requests a hold" in {
      _with_temp_dir("summary-post-narration-hold") { dir =>
        Given("an explanation profile whose summary must remain visible after narration")
        val pkg = dir.resolve("summary-post-narration-hold.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "summary-post-narration-hold",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        _write(
          pkg.resolve("video.yaml"),
          _read(pkg.resolve("video.yaml")).replace(
            "summary: overview-and-conclusion",
            "summary: overview-and-conclusion-hold"
          )
        )
        _write_audio_manifests(pkg, Vector("explanation"))

        When("the Remotion adapter renders the held-summary profile")
        CozyVideo.render(
          CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          ProfileRenderRunner()
        )

        Then("the summary starts after the content, holds for five seconds, and precedes the final page")
        val props = _json(pkg.resolve("target/cozy-video/remotion/explanation/props.json"))
        _int(props, "timing", "openingFrames") shouldBe 81
        _int(props, "timing", "contentFrames") shouldBe 144
        _int(props, "timing", "summaryStartFrame") shouldBe 225
        _int(props, "timing", "summaryFrames") shouldBe 90
        _int(props, "timing", "finalPageStartFrame") shouldBe 315
        _int(props, "timing", "finalPageHoldFrames") shouldBe 36
        _int(props, "timing", "totalFrames") shouldBe 351
      }
    }

    "extend scene rendering when synthesized narration exceeds its authored target duration" in {
      _with_temp_dir("narration-overrun") { dir =>
        Given("an authored eight-second scene whose lead and synthesized narration require eleven seconds")
        val pkg = dir.resolve("narration-overrun.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "narration-overrun",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        _write(
          pkg.resolve("video.yaml"),
          _read(pkg.resolve("video.yaml")).replace(
            "    storyboardSection: explanation",
            "    storyboardSection: explanation\n    output: ../render-output/narration-overrun.mp4"
          )
        )
        val audiodir = pkg.resolve("build/audio/explanation")
        Files.createDirectories(audiodir)
        Files.write(audiodir.resolve("01-explanation.wav"), Array[Byte](0, 1, 2, 3))
        _write(
          audiodir.resolve("manifest.json"),
          """[{"sceneId":"explanation","speaker":null,"file":"01-explanation.wav",
            |"leadSilence":0.5,"audioDuration":10.5,"targetDuration":8.0,"tailSilence":0.0}]""".stripMargin
        )

        When("the Remotion adapter derives its scene and composition timing")
        CozyVideo.render(
          CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          ProfileRenderRunner()
        )

        Then("the render uses the complete narration duration without changing the authored target")
        val props = _json(pkg.resolve("target/cozy-video/remotion/explanation/props.json"))
        _int(props, "timing", "contentFrames") shouldBe 198
        _scene_duration(props, 0) shouldBe 11.0
        _json(audiodir.resolve("manifest.json")).asArray.get.head.hcursor.
          get[Double]("targetDuration").toOption.get shouldBe 8.0
        pkg.resolve("../render-output/narration-overrun.mp4").normalize() should be_regular_file
      }
    }

      "add compact section-transition silence without changing authored audio timing" in {
      _with_temp_dir("compact-character-dialogue-section-transition") { dir =>
        Given("a compact character-dialogue part that exercises section precedence and fallbacks")
        _write(
          dir.resolve("dialogue/script.json"),
          """{
            |  "characters": {"guide": {"side": "left"}},
            |  "scenes": [
            |    {"id": "first-section", "speaker": "guide", "line": "First section.", "section": "foundation", "effects": {"section": "views"}, "visual": {"heading": "First", "section": "quality"}},
            |    {"id": "changed-section", "speaker": "guide", "line": "Changed section.", "section": "views", "visual": {"heading": "Changed"}},
            |    {"id": "same-section", "speaker": "guide", "line": "Same section.", "section": "views", "effects": {"section": "quality"}, "visual": {"heading": "Same", "section": "foundation"}},
            |    {"id": "effects-fallback", "speaker": "guide", "line": "Effects fallback.", "effects": {"section": "quality"}, "visual": {"heading": "Effects", "section": "foundation"}},
            |    {"id": "visual-fallback", "speaker": "guide", "line": "Visual fallback.", "visual": {"heading": "Visual", "section": "quality"}},
            |    {"id": "default-fallback", "speaker": "guide", "line": "Default fallback.", "visual": {"heading": "Default"}}
            |  ]
            |}""".stripMargin
        )
        val audiodir = dir.resolve("build/audio/lecture")
        Files.createDirectories(audiodir)
        Vector("first-section", "changed-section", "same-section", "effects-fallback", "visual-fallback", "default-fallback").zipWithIndex.foreach { case (sceneid, index) =>
          Files.write(audiodir.resolve(f"${index + 1}%02d-$sceneid.wav"), Array[Byte](0, 1, 2, 3))
        }
        _write(
          audiodir.resolve("manifest.json"),
          """[
            |  {"sceneId":"first-section","speaker":"guide","file":"01-first-section.wav","leadSilence":0.5,"audioDuration":2.0,"targetDuration":4.0,"tailSilence":0.0},
            |  {"sceneId":"changed-section","speaker":"guide","file":"02-changed-section.wav","leadSilence":0.5,"audioDuration":2.0,"targetDuration":4.0,"tailSilence":0.0},
            |  {"sceneId":"same-section","speaker":"guide","file":"03-same-section.wav","leadSilence":0.5,"audioDuration":2.0,"targetDuration":4.0,"tailSilence":0.0},
            |  {"sceneId":"effects-fallback","speaker":"guide","file":"04-effects-fallback.wav","leadSilence":0.5,"audioDuration":2.0,"targetDuration":4.0,"tailSilence":0.0},
            |  {"sceneId":"visual-fallback","speaker":"guide","file":"05-visual-fallback.wav","leadSilence":0.5,"audioDuration":2.0,"targetDuration":4.0,"tailSilence":0.0},
            |  {"sceneId":"default-fallback","speaker":"guide","file":"06-default-fallback.wav","leadSilence":0.5,"audioDuration":2.0,"targetDuration":4.0,"tailSilence":0.0}
            |]""".stripMargin
        )
        _write(
          dir.resolve("video_project.json"),
          """{
            |  "renderer": {"engine": "remotion", "effectProfile": "compact"},
            |  "visualEffects": {"sectionStart": "line-sweep"},
            |  "parts": [{"id": "lecture", "type": "dialogue", "script": "dialogue/script.json"}]
            |}""".stripMargin
        )

        When("Cozy stages the compact character-dialogue renderer")
        CozyVideo.render(
          CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          ProfileRenderRunner()
        )

        Then("top-level sections take precedence before effects and visual fallbacks")
        val workdir = dir.resolve("target/cozy-video/remotion/lecture")
        val props = _json(workdir.resolve("props.json"))
        val scenes = props.hcursor.get[Vector[Json]]("scenes").toOption.get
        scenes.map(_.hcursor.get[Int]("sectionTransitionFrames").toOption.get) shouldBe Vector(0, 22, 0, 22, 0, 22)
        scenes.map(_.hcursor.get[Int]("leadInFrames").toOption.get) shouldBe Vector(9, 31, 9, 31, 9, 31)
        scenes.map(_.hcursor.get[Int]("durationFrames").toOption.get) shouldBe Vector(72, 94, 72, 94, 72, 94)
        scenes.map(_.hcursor.get[Int]("startFrame").toOption.get) shouldBe Vector(0, 72, 166, 238, 332, 404)
        scenes(1).hcursor.get[Double]("duration").toOption.get shouldBe 5.222222222222222
        _int(props, "timing", "contentFrames") shouldBe 498

        And("the authored audio manifest remains unchanged")
        val manifest = _json(audiodir.resolve("manifest.json")).asArray.get
        manifest.foreach { entry =>
          entry.hcursor.get[Double]("leadSilence").toOption.get shouldBe 0.5
          entry.hcursor.get[Double]("audioDuration").toOption.get shouldBe 2.0
          entry.hcursor.get[Double]("targetDuration").toOption.get shouldBe 4.0
        }
      }
    }

      "gate compact section transitions by strategy profile and section-start effect" in {
        _with_temp_dir("compact-section-transition-gating") { dir =>
          Vector(
            "generic-strategy" -> """{"renderer":{"engine":"remotion","strategy":"generic","effectProfile":"compact"},"visualEffects":{"sectionStart":"line-sweep"}}""",
            "non-compact-profile" -> """{"renderer":{"engine":"remotion","effectProfile":"default"},"visualEffects":{"sectionStart":"line-sweep"}}""",
            "no-section-start-effect" -> """{"renderer":{"engine":"remotion","effectProfile":"compact"},"visualEffects":{}}"""
          ).foreach { case (variant, renderer) =>
            Given(s"a $variant dialogue fixture with two changed sections")
            val fixture = dir.resolve(variant)
            _write(
              fixture.resolve("dialogue/script.json"),
              """{"characters":{"guide":{"side":"left"}},"scenes":[
                |{"id":"first-section","speaker":"guide","line":"First section.","section":"foundation","visual":{"heading":"First"}},
                |{"id":"changed-section","speaker":"guide","line":"Changed section.","section":"views","visual":{"heading":"Changed"}}
                |]}""".stripMargin
            )
            val audiodir = fixture.resolve("build/audio/lecture")
            Files.createDirectories(audiodir)
            Vector("first-section", "changed-section").zipWithIndex.foreach { case (sceneid, index) =>
              Files.write(audiodir.resolve(f"${index + 1}%02d-$sceneid.wav"), Array[Byte](0, 1, 2, 3))
            }
            _write(
              audiodir.resolve("manifest.json"),
              """[
                |{"sceneId":"first-section","speaker":"guide","file":"01-first-section.wav","leadSilence":0.5,"audioDuration":2.0,"targetDuration":4.0,"tailSilence":0.0},
                |{"sceneId":"changed-section","speaker":"guide","file":"02-changed-section.wav","leadSilence":0.5,"audioDuration":2.0,"targetDuration":4.0,"tailSilence":0.0}
                |]""".stripMargin
            )
            _write(
              fixture.resolve("video_project.json"),
              parser.parse(renderer).toOption.get.deepMerge(
                parser.parse(
                  """{"parts":[{"id":"lecture","type":"dialogue","script":"dialogue/script.json"}]}"""
                ).toOption.get
              ).spaces2
            )

            When("Cozy stages the profile-driven dialogue renderer")
            CozyVideo.render(
              CozyVideo.RenderConfig(fixture.resolve("video_project.json"), "remotion", toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty),
              ProfileRenderRunner()
            )

            Then("neither scene receives a compact section transition")
            val props = _json(fixture.resolve("target/cozy-video/remotion/lecture/props.json"))
            val scenes = props.hcursor.get[Vector[Json]]("scenes").toOption.get
            scenes.map(_.hcursor.get[Int]("sectionTransitionFrames").toOption.get) shouldBe Vector(0, 0)
            scenes.map(_.hcursor.get[Int]("leadInFrames").toOption.get) shouldBe Vector(9, 9)
            scenes.map(_.hcursor.get[Int]("durationFrames").toOption.get) shouldBe Vector(72, 72)
            _int(props, "timing", "contentFrames") shouldBe 144
          }
        }
      }

    }

    "assemble rendered outputs" which {
    "assemble rendered profile parts through Cozy-managed ffmpeg and ffprobe" in {
      _with_temp_dir("assembly") { dir =>
        Given("a rendered explanation-demo-explanation package with every part output")
        val pkg = dir.resolve("assembled.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "assembled",
          s"--save=$pkg",
          "--profile=explanation-demo-explanation"
        )))
        val partids = Vector("introduction", "demonstration", "conclusion")
        _write_audio_manifests(pkg, partids)
        val recording = pkg.resolve("build/record/demonstration/reviewed.webm")
        Files.createDirectories(recording.getParent)
        Files.write(recording, Array[Byte](1, 2, 3))
        val runner = ProfileRenderRunner()
        CozyVideo.render(
          CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        When("Cozy builds the final MP4 through its managed assembly route")
        CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        _approve_confirmation_review(pkg.resolve("video.yaml"))
        val result = CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("final")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        Then("the final artifact and verification manifest come from ffmpeg and ffprobe")
        result should include_text("Cozy Video Build")
        pkg.resolve("build/assembled.mp4") should be_regular_file
        pkg.resolve("target/cozy-video/final/manifest.json") should be_regular_file
        runner.commands.map(_.args).exists(_.contains("ffmpeg")) shouldBe true
        runner.commands.map(_.args).exists(_.contains("ffprobe")) shouldBe true
        val manifest = _json(pkg.resolve("target/cozy-video/final/manifest.json"))
        manifest.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.final.v1")
        manifest.hcursor.get[String]("status").toOption shouldBe Some("validated")
      }
    }

    "assemble Docker parts and final output outside the video project root" in {
      _with_temp_dir("external-assembly-output") { dir =>
        Given("a rendered part and final output configured outside the video project root")
        val pkg = dir.resolve("external-assembly.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "external-assembly",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        _write(
          pkg.resolve("video.yaml"),
          _read(pkg.resolve("video.yaml")).
            replace("output: build/external-assembly.mp4", "output: ../render-output/final.mp4").
            replace(
              "    storyboardSection: explanation",
              "    storyboardSection: explanation\n    output: ../render-output/part.mp4"
            )
        )
        val partoutput = pkg.resolve("../render-output/part.mp4").normalize()
        Option(partoutput.getParent).foreach(Files.createDirectories(_))
        Files.write(partoutput, Array[Byte](0, 1, 2, 3))
        val runner = ProfileRenderRunner()

        When("Cozy assembles the project through Docker-local staging")
        CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        _approve_confirmation_review(pkg.resolve("video.yaml"))
        CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("final")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        Then("the staged inputs remain container-visible and the verified result reaches its configured path")
        val concat = _read(pkg.resolve("target/cozy-video/ffmpeg/concat.txt"))
        concat should include_text("file '/workspace/target/cozy-video/ffmpeg/parts/part-01.mp4'")
        runner.commands.find(_.args.contains("ffmpeg")).get.args should
          contain("/workspace/target/cozy-video/ffmpeg/rendered.mp4")
        pkg.resolve("../render-output/final.mp4").normalize() should be_regular_file
      }
    }

    }

    "preserve visual assets and credits" which {
    "render a configured project-owned asset without changing its bytes or provenance" in {
      _with_temp_dir("project-asset") { dir =>
        Given("a scaffold whose summary slot points to a project-owned SVG")
        val pkg = dir.resolve("asset-demo.video")
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

    "retain selected credits while disabling their standalone presentation page" in {
      _with_temp_dir("credits") { dir =>
        Given("a scaffold with a selected credit profile and authoritative VOICEVOX audio evidence")
        val pkg = dir.resolve("credited.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "credited",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        _write_credit_profile(pkg)
        _write(
          pkg.resolve("video.yaml"),
          _read(pkg.resolve("video.yaml")).
            replace("locale: en", "locale: ja").
            replace("credits:\n  include: []", "credits:\n  profile: publication\n  presentation:\n    enabled: false\n  include: []")
        )
        _update_storyboard(pkg) { storyboard =>
          storyboard.copy(
            scenes = storyboard.scenes.map { scene =>
              if (scene.id == "explanation") scene.copy(speaker = "zundamon") else scene
            }
          )
        }
        _write_credit_audio_manifest(pkg)
        val runner = ProfileRenderRunner()

        When("Cozy renders the final part and then assembles and describes the video")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(pkg.resolve("video.yaml"), checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        CozyVideo.render(
          CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        _approve_confirmation_review(pkg.resolve("video.yaml"))
        val build = CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("final")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        CozyVideo.rdf(CozyVideo.RdfConfig(pkg.resolve("video.yaml"), pkg.resolve("rdf")))

        Then("one effective credit set drives inspection publication files renderer timing and RDF")
        inspection should include_text("creditProfile: publication")
        inspection should include_text("creditAudio: provider=voicevox voice=ずんだもん")
        inspection should include_text("voice-zundamon: required")
        build should include_text("creditProfile: publication")
        val creditjson = _read(pkg.resolve("build/credits/credits.json"))
        val creditmarkdown = _read(pkg.resolve("build/credits/credits.md"))
        val creditprops = _read(pkg.resolve("build/credits/renderer-props.json"))
        creditjson should include_text("voice-zundamon")
        creditjson should include_text("\"enabled\" : false")
        creditmarkdown should include_text("VOICEVOX:ずんだもん")
        creditprops should include_text("voice-zundamon")
        creditprops should include_text("\"enabled\" : false")
        val digest = parser.parse(creditjson).toOption.get.hcursor.get[String]("digest").toOption.get
        _read(pkg.resolve("target/cozy-video/final/manifest.json")) should include_text(digest)
        _read(pkg.resolve("rdf/video.ttl")) should include_text(digest)
        _read(pkg.resolve("rdf/video.ttl")) should include_text("hasCredit")

        And("the disabled credit presentation contributes no hold before the existing final URL page")
        val workdir = pkg.resolve("target/cozy-video/remotion/explanation")
        val props = _json(workdir.resolve("props.json"))
        _int(props, "timing", "creditPageStartFrame") shouldBe 225
        _int(props, "timing", "creditPageHoldFrames") shouldBe 0
        _int(props, "timing", "finalPageStartFrame") shouldBe
          _int(props, "timing", "openingFrames") + _int(props, "timing", "contentFrames")
        _int(props, "timing", "finalPageHoldFrames") shouldBe 36
        _int(props, "timing", "totalFrames") shouldBe 261

        And("verification rejects a projection changed after the effective set was built")
        CozyVideo.verifyCredits(pkg.resolve("video.yaml")) shouldBe Vector.empty
        _write(pkg.resolve("build/credits/renderer-props.json"), "{}")
        CozyVideo.verifyCredits(pkg.resolve("video.yaml")).mkString("\n") should
          include_text("credit renderer props does not match the effective credit set")
      }
    }

    "report recommended unresolved credits without blocking render or build" in {
      _with_temp_dir("recommended-credit-warning") { dir =>
        Given("a scaffold with a recommended semantic credit absent from its selected profile")
        val pkg = dir.resolve("recommended.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "recommended",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        _write_material_credit_profile(pkg)
        _write(pkg.resolve("assets/advisory.svg"), "<svg/>")
        _write(
          pkg.resolve("video.yaml"),
          _read(pkg.resolve("video.yaml")).
            replace("credits:\n  include: []", "credits:\n  profile: material-publication\n  include: []").
            replace(
              "assets:\n",
              "assets:\n  advisory:\n    path: assets/advisory.svg\n    credits: [unlisted-advisory]\n    credit-obligation: recommended\n"
            )
        )
        _write_credit_audio_manifest(pkg)
        val runner = ProfileRenderRunner()

        When("Cozy renders and assembles the project")
        val rendered = CozyVideo.render(
          CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        val built = CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        Then("both successful command results retain the recommended-attribution warning")
        rendered should include_text("creditWarning: credit.asset.unknown-item")
        built should include_text("creditWarning: credit.asset.unknown-item")
      }
    }

    }

    "preserve lifecycle contracts" which {
    "resolve a deep Part 5 descriptor from its discovered project credit profile without publication mutation" in {
      _with_temp_dir("part5-project-context") { dir =>
        Given("a marked project root and a deep video-ja descriptor without package credit configuration")
        val descriptor = dir.resolve("src/main/media/development-process/object-modeling/video-ja.yaml")
        val marker = dir.resolve("conf/cozy/config.yaml")
        val profile = dir.resolve("conf/cozy/video/credit-profiles/simplemodeling-org.yaml")
        _write(marker, "video:\n  credits:\n    default-profile: simplemodeling-org\n")
        _write(
          profile,
          """schema: cozy.video.credits.v1
            |profile: simplemodeling-org
            |presentation:
            |  title: {default: SimpleModeling credits}
            |""".stripMargin
        )
        _write(
          descriptor,
          """name: Part 5
            |title: Project context
            |renderer: {engine: remotion}
            |parts: []
            |""".stripMargin
        )

        When("inspect and build dry-run plan the descriptor without invoking external tools")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(descriptor, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val dryrun = CozyVideo.build(
          CozyVideo.BuildConfig(descriptor, dryRun = true, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )

        Then("both views expose the same project-conf profile source and discovered marker")
        Vector(inspection, dryrun).foreach { output =>
          output should include_text("creditProfile: simplemodeling-org")
          output should include_text("creditProfileSelectionLayer: project-conf")
          output should include_text("creditProfileSourceLayer: project-conf")
          output should include_text(s"creditProfileSelectionPath: $marker")
          output should include_text(s"creditProfileSourcePath: $profile")
          output should include_text(s"discoveredProjectRoot: $dir")
          output should include_text(s"discoveredProjectMarker: $marker")
        }

        And("planning does not register publish or create project output state")
        Files.exists(dir.resolve("warehouse")) shouldBe false
        Files.exists(descriptor.getParent.resolve("build")) shouldBe false
      }
    }

    "reject symbolic project descriptor aliases before context or profile resolution" in {
      _with_temp_dir("symbolic-project-descriptor") { dir =>
        Given("a direct video descriptor and a symbolic alias to the same descriptor")
        val descriptor = dir.resolve("video.yaml")
        val alias = dir.resolve("video-alias.yaml")
        _write(
          descriptor,
          """title: Direct descriptor
            |renderer: {engine: remotion}
            |parts: []
            |""".stripMargin
        )
        Files.createSymbolicLink(alias, descriptor)

        When("Cozy inspects or dry-runs the symbolic descriptor alias")
        val inspecterror = intercept[RuntimeException] {
          CozyVideo.inspect(
            CozyVideo.InspectConfig(alias, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }
        val builderror = intercept[RuntimeException] {
          CozyVideo.build(
            CozyVideo.BuildConfig(alias, dryRun = true, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }

        Then("both commands fail at the direct descriptor boundary")
        Vector(inspecterror, builderror).foreach { error =>
          error.getMessage should include("direct regular non-symlink")
          error.getMessage should include(alias.toAbsolutePath.normalize().toString)
        }

        And("the direct descriptor remains valid for inspect and build dry-run")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(descriptor, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val dryrun = CozyVideo.build(
          CozyVideo.BuildConfig(descriptor, dryRun = true, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        inspection should include_text("projectRoot:")
        dryrun should include_text("Cozy Video Build")
      }
    }

    "preserve profile contracts through inspect build RDF and publication" in {
      _with_temp_dir("lifecycle") { dir =>
        Given("an explicit legacy explanation descriptor entering the publisher's legacy build lifecycle")
        val pkg = dir.resolve("src/main/doxsite/technology/tutorial.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "tutorial",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        val project = pkg.resolve("video.yaml")
        _write(
          pkg.resolve("script.yaml"),
          """title: Tutorial
            |voice:
            |  fallbackSpeakerId: 42
            |narration:
            |  provider: voicevox
            |scenes:
            |  - id: explanation
            |    speaker: guide
            |    narration: Replace this explanation narration.
            |    caption: Explanation
            |    duration: 8.0
            |""".stripMargin
        )
        _write_material_credit_profile(pkg)
        _write(pkg.resolve("assets/guide.svg"), "<svg/>")
        _write(
          project,
          _read(project).
            replaceFirst(
              "(?s)storyboardReview:\\n  source: storyboard\\.md\\n  approvedIdentity: [^\\n]+\\nparts:\\n  - id: explanation\\n    type: dialogue\\n    storyboard: storyboard\\.md\\n    storyboardSection: explanation",
              "parts:\n  - id: explanation\n    type: dialogue\n    script: script.yaml"
            ).
            replace("credits:\n  include: []", "credits:\n  profile: material-publication\n  presentation:\n    enabled: false\n  include: []").
            replace(
              "assets:\n",
              "assets:\n  guide:\n    path: assets/guide.svg\n    kind: character-material\n    required: true\n    tags: [character.guide]\n    credits: [guide-material]\n    credit-obligation: required\n"
            )
        )
        When("Cozy inspects plans and describes the package as RDF")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(project, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val build = CozyVideo.build(
          CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")),
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
        generated should include_text("\"locale\" : \"en\"")
        generated should include_text("\"profile\" : \"material-publication\"")
        generated should include_text("\"presentation\" : {")
        generated should include_text("\"enabled\" : false")
        generated should include_text("\"guide\"")
        generated should include_text("\"character.guide\"")
        result.workspaceRoot.resolve("conf/cozy/video/credit-profiles/material-publication.yaml") should be_regular_file
        result.workspaceRoot.resolve("build/credits/credits.json") should be_regular_file
        result.workspaceRoot.resolve("build/credits/credits.md") should be_regular_file
        result.workspaceRoot.resolve("build/credits/renderer-props.json") should be_regular_file
        val workspaceprops = _json(result.workspaceRoot.resolve("target/cozy-video/remotion/explanation/props.json"))
        _int(workspaceprops, "timing", "creditPageHoldFrames") shouldBe 0
        _int(workspaceprops, "timing", "finalPageStartFrame") shouldBe
          _int(workspaceprops, "timing", "openingFrames") + _int(workspaceprops, "timing", "contentFrames")
        result.workspaceRoot.resolve("target/cozy-video/remotion/explanation/public/assets/summary.svg") should be_regular_file
      }
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

  private def _write_credit_audio_manifest(pkg: Path): Unit = {
    val dir = pkg.resolve("build/audio/explanation")
    Files.createDirectories(dir)
    Files.write(dir.resolve("01-explanation.wav"), Array[Byte](0, 1, 2, 3))
    _write(
      dir.resolve("manifest.json"),
      """[{"sceneId":"explanation","speaker":"zundamon","file":"01-explanation.wav","leadSilence":0.0,"audioDuration":8.0,"targetDuration":8.0,"tailSilence":0.0,"provider":"voicevox","executionMode":"external-http","voiceIdentity":"ずんだもん","voiceId":"3"}]"""
    )
  }

  private def _approve_confirmation_review(project: Path): Unit = {
    val manifest = _json(project.getParent.resolve("target/cozy-video/confirmation/manifest.json"))
    val identity = manifest.hcursor.get[String]("identity").toOption.get
    _write(project, _read(project) + s"confirmationReview:\n  approvedIdentity: $identity\n")
  }

  private def _update_storyboard(pkg: Path)(transform: CozyVideo.Storyboard => CozyVideo.Storyboard): Unit = {
    val source = pkg.resolve("storyboard.md")
    val result = CozyVideo.loadStoryboard(source)
    if (!result.isValid)
      throw new IllegalArgumentException(result.diagnostics.map(_.render).mkString("\n"))
    val storyboard = transform(result.storyboard.get)
    _write(source, CozyVideo.canonicalStoryboardMarkdown(storyboard))
    val identity = CozyVideo.storyboardIdentity(storyboard)
    val project = pkg.resolve("video.yaml")
    _write(project, _read(project).replaceFirst("(?m)(  approvedIdentity: )[^\\n]+", "$1" + identity))
  }

  private def _write_credit_profile(pkg: Path): Unit =
    _write(
      pkg.resolve("conf/cozy/video/credit-profiles/publication.yaml"),
      """schema: cozy.video.credits.v1
        |profile: publication
        |required-audio-providers: [voicevox]
        |presentation:
        |  title: {ja: 使用素材・音声, en: Credits}
        |  hold-seconds: 4.0
        |selectors:
        |  - when:
        |      audio-provider: voicevox
        |      voice-identity: ずんだもん
        |    include: [voice-zundamon]
        |credits:
        |  - id: voice-zundamon
        |    category: voice
        |    label: {ja: "VOICEVOX:ずんだもん", en: "VOICEVOX:Zundamon"}
        |    publication-text: {ja: "VOICEVOX:ずんだもん", en: "VOICEVOX:Zundamon"}
        |    creator: VOICEVOX
        |    surfaces: [video, publication, rdf]
        |""".stripMargin
    )

  private def _write_material_credit_profile(pkg: Path): Unit =
    _write(
      pkg.resolve("conf/cozy/video/credit-profiles/material-publication.yaml"),
      """schema: cozy.video.credits.v1
        |profile: material-publication
        |presentation:
        |  title: {default: Credits}
        |  hold-seconds: 3.0
        |selectors:
        |  - when:
        |      any-character-id: [guide]
        |    include: [guide-material]
        |credits:
        |  - id: guide-material
        |    category: character-material
        |    label: {default: Guide material}
        |    publication-text: {default: Guide material}
        |    creator: Example Studio
        |    surfaces: [video, publication, rdf]
        |""".stripMargin
    )

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

  private def _scene_duration(json: Json, index: Int): Double =
    json.hcursor.get[Vector[Json]]("scenes").toOption.get(index).hcursor.
      get[Double]("duration").toOption.get

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
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.delete)
      finally paths.close()
    }
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
