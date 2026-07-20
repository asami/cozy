package cozy.media

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Jul. 19, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Media Package" should {
    "plan, build, verify, and publish a portable knowledge representation" in {
      _with_temp_dir("lifecycle") { dir =>
        Given("a knowledge unit, publication profile, and PNG representation")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write_png(dir.resolve("infographic/web-ja.png"), 1600, 900)
        val descriptor = dir.resolve("media.yaml")
        _write(descriptor, _media_yaml("copy"))
        val config = CozyMedia.CommandConfig(descriptor)

        When("Cozy plans the initial build")
        val initialplan = CozyMedia.plan(config)

        Then("the resource requires a deterministic build")
        initialplan should include_text("web-ja: build")

        When("Cozy builds and verifies the package")
        val build = CozyMedia.build(config)
        val verification = CozyMedia.verify(config)

        Then("the generated output and provenance manifest are available")
        build should include_text("web-ja: built")
        verification should include_text("status: valid")
        dir.resolve("target/cozy-media/web-ja.png") should be_regular_file
        dir.resolve("target/cozy-media/manifest.json") should be_regular_file

        When("Cozy publishes through the logical site profile")
        val publication = CozyMedia.publish(config.copy(profile = Some("site")))
        val publishedverification = CozyMedia.verify(config.copy(profile = Some("site")))

        Then("the publication is byte-identical and profile verification succeeds")
        publication should include_text("profile: site")
        publishedverification should include_text("status: valid profile=site")
        Files.readAllBytes(dir.resolve("publication/summary-ja.png")) shouldBe
          Files.readAllBytes(dir.resolve("target/cozy-media/web-ja.png"))

        And("a subsequent plan reports the resource as current")
        CozyMedia.plan(config) should include_text("web-ja: current")
      }
    }

    "invoke the SVG renderer through an explicit deterministic process boundary" in {
      _with_temp_dir("svg") { dir =>
        Given("an SVG representation and a recording renderer")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write(dir.resolve("infographic/web-ja.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1600\" height=\"900\"/>")
        val descriptor = dir.resolve("media.yaml")
        _write(descriptor, _media_yaml("svg-to-png", ".svg"))
        var command = Vector.empty[String]
        val runner = new CozyMedia.ProcessRunner {
          def run(pcommand: Vector[String], workingdirectory: Path): Int = {
            command = pcommand
            _write_png(Path.of(pcommand.last), 1600, 900)
            0
          }
        }

        When("Cozy builds the SVG representation")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)

        Then("the renderer receives explicit source and output paths")
        command.head shouldBe "rsvg-convert"
        command should contain("-o")
        command.last shouldBe dir.resolve("target/cozy-media/web-ja.png").toString
        CozyMedia.verify(CozyMedia.CommandConfig(descriptor)) should include_text("status: valid")
      }
    }

    "reject machine-specific absolute paths in a reusable package" in {
      _with_temp_dir("absolute") { dir =>
        Given("a descriptor with an absolute knowledge source")
        val descriptor = dir.resolve("media.yaml")
        _write(
          descriptor,
          _media_yaml("copy").replace("knowledge/article.dox", dir.resolve("knowledge/article.dox").toString)
        )

        When("Cozy inspects the package")
        val error = intercept[RuntimeException] {
          CozyMedia.inspect(CozyMedia.CommandConfig(descriptor))
        }

        Then("the portability boundary is reported")
        error.getMessage should include_text("knowledge.source must be relative")
      }
    }

    "keep dry-run builds free of generated state" in {
      _with_temp_dir("dry-run") { dir =>
        Given("a buildable package")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write_png(dir.resolve("infographic/web-ja.png"), 1600, 900)
        val descriptor = dir.resolve("media.yaml")
        _write(descriptor, _media_yaml("copy"))

        When("Cozy performs a dry-run")
        val result = CozyMedia.build(CozyMedia.CommandConfig(descriptor, dryRun = true))

        Then("only the plan is reported")
        result should include_text("dry-run")
        dir.resolve("target/cozy-media/web-ja.png") should not(be_regular_file)
        dir.resolve("target/cozy-media/manifest.json") should not(be_regular_file)
      }
    }

    "accept dry-run before the descriptor argument" in {
      _with_temp_dir("dry-run-cli") { dir =>
        Given("a buildable package and a leading dry-run option")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write_png(dir.resolve("infographic/web-ja.png"), 1600, 900)
        val descriptor = dir.resolve("media.yaml")
        _write(descriptor, _media_yaml("copy"))

        When("the command configuration is parsed")
        val config = CozyMedia.CommandConfig.create(List("--dry-run", descriptor.toString))

        Then("the descriptor and switch retain their independent meanings")
        config.descriptorFile shouldBe descriptor
        config.dryRun shouldBe true
      }
    }

    "publish a prebuilt video without copying it into the source package" in {
      _with_temp_dir("prebuilt") { dir =>
        Given("a final video generated in an ignored target directory")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write(dir.resolve("target/render/final.mp4"), "video")
        val descriptor = dir.resolve("media.yaml")
        _write(
          descriptor,
          """schema: cozy.media.v1
            |knowledge:
            |  id: development-process/example
            |  source: knowledge/article.dox
            |profiles:
            |  archive:
            |    root: publication
            |resources:
            |  - id: article-video-ja
            |    kind: video
            |    language: ja
            |    role: article-introduction
            |    source: target/render/final.mp4
            |    build: prebuilt
            |    publications:
            |      archive: example/ja/final.mp4
            |""".stripMargin
        )

        When("Cozy verifies and publishes the prebuilt representation")
        val verification = CozyMedia.verify(CozyMedia.CommandConfig(descriptor))
        val publication = CozyMedia.publish(CozyMedia.CommandConfig(descriptor, profile = Some("archive")))
        val publishedverification = CozyMedia.verify(CozyMedia.CommandConfig(descriptor, profile = Some("archive")))

        Then("the source package remains free of a second MP4 copy")
        verification should include_text("status: valid")
        publication should include_text("article-video-ja")
        publishedverification should include_text("status: valid profile=archive")
        dir.resolve("publication/example/ja/final.mp4") should be_regular_file
      }
    }

    "require a media output declaration for delegated video projects" in {
      _with_temp_dir("video-project-output") { dir =>
        Given("a delegated video project resource without a media package output")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write(
          dir.resolve("video_project.json"),
          """{
            |  "title": "Delegated Video",
            |  "output": "target/render/final.mp4",
            |  "parts": []
            |}
            |""".stripMargin
        )
        val descriptor = dir.resolve("media.yaml")
        _write(
          descriptor,
          """schema: cozy.media.v1
            |knowledge:
            |  id: development-process/example
            |  source: knowledge/article.dox
            |profiles:
            |  archive:
            |    root: publication
            |resources:
            |  - id: article-video-ja
            |    kind: video
            |    language: ja
            |    role: article-introduction
            |    build: video-project
            |    project: video_project.json
            |    publications:
            |      archive: example/ja/final.mp4
            |""".stripMargin
        )

        When("Cozy validates the media descriptor")
        val error = intercept[RuntimeException] {
          CozyMedia.verify(CozyMedia.CommandConfig(descriptor))
        }

        Then("the package reports that the delegated video needs a publishable output")
        error.getMessage should include_text("Media video-project resource requires output: article-video-ja")
      }
    }

    "verify a delegated video project through its declared media output" in {
      _with_temp_dir("video-project-verify") { dir =>
        Given("a delegated video project whose final output is declared in the media package")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write(
          dir.resolve("video_project.json"),
          """{
            |  "title": "Delegated Video",
            |  "output": "target/render/final.mp4",
            |  "parts": []
            |}
            |""".stripMargin
        )
        _write(dir.resolve("target/render/final.mp4"), "video")
        val descriptor = dir.resolve("media.yaml")
        _write(
          descriptor,
          """schema: cozy.media.v1
            |knowledge:
            |  id: development-process/example
            |  source: knowledge/article.dox
            |profiles:
            |  archive:
            |    root: publication
            |resources:
            |  - id: article-video-ja
            |    kind: video
            |    language: ja
            |    role: article-introduction
            |    build: video-project
            |    project: video_project.json
            |    output: target/render/final.mp4
            |    publications:
            |      archive: example/ja/final.mp4
            |""".stripMargin
        )

        When("Cozy verifies and publishes the delegated video output")
        val verification = CozyMedia.verify(CozyMedia.CommandConfig(descriptor))
        val publication = CozyMedia.publish(CozyMedia.CommandConfig(descriptor, profile = Some("archive")))

        Then("the declared output is the publishable media artifact")
        verification should include_text("status: valid")
        publication should include_text("article-video-ja")
        dir.resolve("publication/example/ja/final.mp4") should be_regular_file
      }
    }

    "reject a delegated video whose required credit cannot be resolved" in {
      _with_temp_dir("video-project-credit-verification") { dir =>
        Given("a rendered delegated video with unmatched required VOICEVOX provenance")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write(
          dir.resolve("video_project.yaml"),
          """title: Delegated Video
            |output: target/render/final.mp4
            |locale: ja
            |credits:
            |  profile: publication
            |parts:
            |  - id: scene
            |    type: dialogue
            |    script: script.yaml
            |""".stripMargin
        )
        _write(
          dir.resolve("script.yaml"),
          """title: Scene
            |scenes:
            |  - id: scene
            |    speaker: narrator
            |    narration: Test
            |    duration: 1.0
            |""".stripMargin
        )
        _write(
          dir.resolve("build/audio/scene/manifest.json"),
          """[{"sceneId":"scene","speaker":"narrator","file":"scene.wav","leadSilence":0.0,"audioDuration":1.0,"targetDuration":1.0,"tailSilence":0.0,"provider":"voicevox","voiceIdentity":"Unknown Voice"}]"""
        )
        _write(
          dir.resolve("conf/cozy/video/credit-profiles/publication.yaml"),
          """schema: cozy.video.credits.v1
            |profile: publication
            |required-audio-providers: [voicevox]
            |presentation:
            |  title: {ja: 使用素材・音声}
            |""".stripMargin
        )
        _write(dir.resolve("target/render/final.mp4"), "video")
        val descriptor = dir.resolve("media.yaml")
        _write(
          descriptor,
          """schema: cozy.media.v1
            |knowledge:
            |  id: development-process/example
            |  source: knowledge/article.dox
            |resources:
            |  - id: article-video-ja
            |    kind: video
            |    language: ja
            |    build: video-project
            |    project: video_project.yaml
            |    output: target/render/final.mp4
            |""".stripMargin
        )

        When("cozy media verify checks the delegated video contract")
        val error = intercept[RuntimeException] {
          CozyMedia.verify(CozyMedia.CommandConfig(descriptor))
        }

        Then("the unresolved speaker credit blocks media verification")
        error.getMessage should include_text("article-video-ja: credit.audio.unresolved")
        error.getMessage should include_text("Unknown Voice")
      }
    }

    "resolve environment-backed publication roots only when selected" in {
      _with_temp_dir("profile-env") { dir =>
        Given("a buildable package with an unavailable archive environment variable")
        _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
        _write_png(dir.resolve("infographic/web-ja.png"), 1600, 900)
        val descriptor = dir.resolve("media.yaml")
        _write(
          descriptor,
          _media_yaml("copy").replace(
            "profiles:\n  site:\n    root: publication",
            "profiles:\n  site:\n    root-env: COZY_MEDIA_SPEC_UNDEFINED_ROOT"
          )
        )

        When("Cozy builds without selecting that publication profile")
        val build = CozyMedia.build(CozyMedia.CommandConfig(descriptor))

        Then("the unrelated build does not require machine-specific publication state")
        build should include_text("web-ja: built")

        When("the unavailable profile is selected")
        val error = intercept[RuntimeException] {
          CozyMedia.publish(CozyMedia.CommandConfig(descriptor, profile = Some("site")))
        }

        Then("Cozy reports the missing environment contract")
        error.getMessage should include_text("COZY_MEDIA_SPEC_UNDEFINED_ROOT")
      }
    }
  }

  private def _media_yaml(buildkind: String, sourcesuffix: String = ".png"): String =
    s"""schema: cozy.media.v1
       |knowledge:
       |  id: development-process/example
       |  type: documentmodel:KnowledgeUnit
       |  source: knowledge/article.dox
       |  source-type: smartdox
       |languages:
       |  - ja
       |  - en
       |profiles:
       |  site:
       |    root: publication
       |resources:
       |  - id: web-ja
       |    kind: image
       |    language: ja
       |    role: web-summary
       |    source: infographic/web-ja$sourcesuffix
       |    output: target/cozy-media/web-ja.png
       |    build: $buildkind
       |    width: 1600
       |    height: 900
       |    publications:
       |      site: summary-ja.png
       |""".stripMargin

  private def _write(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _write_png(path: Path, width: Int, height: Int): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val bytes = new Array[Byte](24)
    Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).copyToArray(bytes)
    val buffer = ByteBuffer.wrap(bytes)
    buffer.position(16)
    buffer.putInt(width)
    buffer.putInt(height)
    Files.write(path, bytes)
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val dir = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/media-package").resolve(name)
    _delete(dir)
    Files.createDirectories(dir)
    try body(dir)
    finally _delete(dir)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.delete)
      finally paths.close()
    }
}
