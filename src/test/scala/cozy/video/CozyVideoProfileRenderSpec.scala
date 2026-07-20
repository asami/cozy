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
 * @version Jul. 20, 2026
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
            val openingframes = if (index == 0) 135 else 0
            _int(props, "timing", "openingFrames") shouldBe openingframes
            _int(props, "timing", "sectionStartFrame") shouldBe openingframes
            _int(props, "timing", "sectionStartFrames") shouldBe 36
            _int(props, "timing", "summaryStartFrame") shouldBe openingframes + 168
            _int(props, "timing", "summaryFrames") shouldBe 72
            val finalframes = if (index == partids.size - 1) 60 else 0
            _int(props, "timing", "creditPageHoldFrames") shouldBe 0
            _int(props, "timing", "finalPageStartFrame") shouldBe openingframes + 240
            _int(props, "timing", "finalPageHoldFrames") shouldBe finalframes
            _int(props, "timing", "totalFrames") shouldBe openingframes + 240 + finalframes
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
        val runner = ProfileRenderRunner()
        CozyVideo.render(
          CozyVideo.RenderConfig(pkg.resolve("video.yaml"), "remotion", checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        When("Cozy builds the final MP4 through its managed assembly route")
        val result = CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        Then("the final artifact and verification manifest come from ffmpeg and ffprobe")
        result should include_text("Cozy Video Build")
        pkg.resolve("build/assembled.mp4") should be_regular_file
        pkg.resolve("build/manifest.json") should be_regular_file
        runner.commands.map(_.args).exists(_.contains("ffmpeg")) shouldBe true
        runner.commands.map(_.args).exists(_.contains("ffprobe")) shouldBe true
        val manifest = _read(pkg.resolve("build/manifest.json"))
        manifest should include_text("\"partOutputs\"")
        manifest should include_text("\"ffprobe\"")
      }
    }

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

    "insert resolved credits before the final page and preserve their provenance" in {
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
            replace("credits:\n  include: []", "credits:\n  profile: publication\n  include: []")
        )
        _write(
          pkg.resolve("script.yaml"),
          _read(pkg.resolve("script.yaml")).replace("    narration:", "    speaker: zundamon\n    narration:")
        )
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
        val build = CozyVideo.build(
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false),
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
        creditmarkdown should include_text("VOICEVOX:ずんだもん")
        creditprops should include_text("voice-zundamon")
        val digest = parser.parse(creditjson).toOption.get.hcursor.get[String]("digest").toOption.get
        _read(pkg.resolve("build/manifest.json")) should include_text(digest)
        _read(pkg.resolve("rdf/video.ttl")) should include_text(digest)
        _read(pkg.resolve("rdf/video.ttl")) should include_text("hasCredit")

        And("the non-empty static credit page precedes the existing final URL page")
        val workdir = pkg.resolve("target/cozy-video/remotion/explanation")
        val props = _json(workdir.resolve("props.json"))
        _int(props, "timing", "creditPageStartFrame") shouldBe 375
        _int(props, "timing", "creditPageHoldFrames") shouldBe 120
        _int(props, "timing", "finalPageStartFrame") shouldBe 495
        _int(props, "timing", "totalFrames") shouldBe 555
        val root = _read(workdir.resolve("src/Root.tsx"))
        root.indexOf("<CreditPage credits={credits}") should be < root.indexOf("<FinalPage effect={finalPage}")

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
          CozyVideo.BuildConfig(pkg.resolve("video.yaml"), dryRun = false, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        Then("both successful command results retain the recommended-attribution warning")
        rendered should include_text("creditWarning: credit.asset.unknown-item")
        built should include_text("creditWarning: credit.asset.unknown-item")
      }
    }

    "preserve profile contracts through inspect build RDF and publication" in {
      _with_temp_dir("lifecycle") { dir =>
        Given("a scaffolded explanation package entering the full publication lifecycle")
        val pkg = dir.resolve("src/main/doxsite/technology/tutorial.video")
        CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(List(
          "tutorial",
          s"--save=$pkg",
          "--profile=explanation"
        )))
        val project = pkg.resolve("video.yaml")
        _write_material_credit_profile(pkg)
        _write(pkg.resolve("assets/guide.svg"), "<svg/>")
        _write(
          project,
          _read(project).
            replace("credits:\n  include: []", "credits:\n  profile: material-publication\n  include: []").
            replace(
              "assets:\n",
              "assets:\n  guide:\n    path: assets/guide.svg\n    kind: character-material\n    required: true\n    tags: [character.guide]\n    credits: [guide-material]\n    credit-obligation: required\n"
            )
        )
        _write(
          pkg.resolve("script.yaml"),
          _read(pkg.resolve("script.yaml")).replace("    narration:", "    speaker: guide\n    narration:")
        )

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
        generated should include_text("\"locale\" : \"en\"")
        generated should include_text("\"profile\" : \"material-publication\"")
        generated should include_text("\"guide\"")
        generated should include_text("\"character.guide\"")
        result.workspaceRoot.resolve("conf/cozy/video/credit-profiles/material-publication.yaml") should be_regular_file
        result.workspaceRoot.resolve("build/credits/credits.json") should be_regular_file
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

  private def _write_credit_audio_manifest(pkg: Path): Unit = {
    val dir = pkg.resolve("build/audio/explanation")
    Files.createDirectories(dir)
    Files.write(dir.resolve("01-explanation.wav"), Array[Byte](0, 1, 2, 3))
    _write(
      dir.resolve("manifest.json"),
      """[{"sceneId":"explanation","speaker":"zundamon","file":"01-explanation.wav","leadSilence":0.0,"audioDuration":8.0,"targetDuration":8.0,"tailSilence":0.0,"provider":"voicevox","executionMode":"external-http","voiceIdentity":"ずんだもん","voiceId":"3"}]"""
    )
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
