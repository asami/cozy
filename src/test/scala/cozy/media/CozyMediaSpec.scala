package cozy.media

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource

/*
 * @since   Jul. 19, 2026
 *  version Jul. 20, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Media Package" should {
    "manage portable media package lifecycles" which {
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
    }

    "handle prebuilt and delegated video resources" which {
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
          CozyMedia.build(CozyMedia.CommandConfig(descriptor))

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

      "reject a delegated video project without current receipt evidence" in {
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

          When("Cozy plans the delegated video and rejects receiptless verification and publication")
          val plan = CozyMedia.plan(CozyMedia.CommandConfig(descriptor))
          val verificationerror = intercept[RuntimeException] {
            CozyMedia.verify(CozyMedia.CommandConfig(descriptor))
          }
          val publicationerror = intercept[RuntimeException] {
            CozyMedia.publish(CozyMedia.CommandConfig(descriptor, profile = Some("archive")))
          }

          Then("planning reports delegation and receiptless verification and publication are rejected")
          plan should include_text("article-video-ja: delegate-video")
          verificationerror.getMessage should include_text("missing or stale cozy.media.receipt.v2 evidence")
          publicationerror.getMessage should include_text("missing or stale cozy.media.receipt.v2 evidence")
          dir.resolve("publication/example/ja/final.mp4") should not(be_regular_file)
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
    }

    "resolve selected publication profiles" which {
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

      "reuse a discovered project publication profile for nested ordinary plans" in {
        _with_temp_dir("project-profile") { root =>
          Given("a nested media descriptor beneath a SmartDox project authority")
          val descriptor = root.resolve("packages/part-5/media.yaml")
          _write(root.resolve("conf/cozy/config.yaml"), _project_config_yaml("site", "publication", "smartdox"))
          _write(descriptor, _media_yaml("copy").replace("profiles:\n  site:\n    root: publication\n", ""))

          When("Cozy resolves the explicit ordinary publication profile")
          val mediaplan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor, profile = Some("site")))
          val inspection = CozyMedia.inspect(CozyMedia.CommandConfig(descriptor, profile = Some("site")))

          Then("the configured root and immutable project provenance are retained")
          mediaplan.context.project.map(_.root.path) shouldBe Some(root.toAbsolutePath.normalize())
          mediaplan.effectiveProfile.map(_.resolvedRoot) shouldBe Some(root.resolve("publication").toAbsolutePath.normalize())
          mediaplan.effectiveProfile.flatMap(_.siteKind) shouldBe Some("smartdox")
          mediaplan.effectiveProfile.flatMap(_.layer) shouldBe Some("project-conf")
          inspection should include_text(s"projectRoot: ${root.toAbsolutePath.normalize()}")
          inspection should include_text(s"projectMarker: ${root.resolve("conf/cozy/config.yaml").toAbsolutePath.normalize()}")
          inspection should include_text("profileLayer: project-conf")
          inspection should include_text("profileSiteKind: smartdox")
        }
      }

      "select the top-level articleMedia profile when no CLI profile is supplied" in {
        _with_temp_dir("associated-project-profile") { root =>
          Given("a nested descriptor with an explicit articleMedia association and two configured project profiles")
          val descriptor = root.resolve("packages/part-5/media.yaml")
          _write(
            root.resolve("conf/cozy/config.yaml"),
            """project:
              |  id: simplemodeling-org
              |  kind: smartdox-site
              |media:
              |  publication-profiles:
              |    site:
              |      root: publication
              |      site-kind: smartdox
              |    archive:
              |      root: archive
              |      site-kind: archive
              |""".stripMargin
          )
          _write(
            descriptor,
            _media_yaml("copy").
              replace("profiles:\n  site:\n    root: publication\n", "articleMedia:\n  articleIdentity: development-process/part-5\n  publicationProfile: site\n").
              replace("      site: summary-ja.png", "      site: summary-ja.png\n      archive: archive-ja.png")
          )

          When("Cozy resolves and renders ordinary plans without an explicit CLI profile")
          val selected = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor))
          val inspection = CozyMedia.inspect(CozyMedia.CommandConfig(descriptor))
          val plan = CozyMedia.plan(CozyMedia.CommandConfig(descriptor))

          Then("the association selects the project profile and its publication mapping")
          selected.effectiveProfile.map(_.id) shouldBe Some("site")
          selected.effectiveProfile.map(_.resolvedRoot) shouldBe Some(root.resolve("publication").toAbsolutePath.normalize())
          selected.effectiveProfile.flatMap(_.layer) shouldBe Some("project-conf")
          selected.effectiveProfile.flatMap(_.sourcePath) shouldBe Some(root.resolve("conf/cozy/config.yaml").toAbsolutePath.normalize())
          selected.effectiveProfile.flatMap(_.siteKind) shouldBe Some("smartdox")
          inspection should include_text("profile: site")
          inspection should include_text(s"profileRoot: ${root.resolve("publication").toAbsolutePath.normalize()}")
          inspection should include_text("profileLayer: project-conf")
          inspection should include_text(s"profileSource: ${root.resolve("conf/cozy/config.yaml").toAbsolutePath.normalize()}")
          inspection should include_text("profileSiteKind: smartdox")
          inspection should include_text(s"publications=site=${root.resolve("publication/summary-ja.png").toAbsolutePath.normalize()}")
          plan should include_text("profile: site")
          plan should include_text(s"publish=${root.resolve("publication/summary-ja.png").toAbsolutePath.normalize()}")

          And("an explicit CLI profile overrides the association and selects its own mapping")
          val explicit = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor, profile = Some("archive")))
          explicit.effectiveProfile.map(_.id) shouldBe Some("archive")
          explicit.resources.flatMap(_.publications.values).toVector should contain only
            (root.resolve("archive/archive-ja.png").toAbsolutePath.normalize())
        }
      }

      "overlay only a configured profile location while retaining configured provenance" in {
        _with_temp_dir("profile-location-overlay") { root =>
          Given("one configured project profile and one same-ID descriptor root overlay")
          val descriptor = root.resolve("packages/part-5/media.yaml")
          _write(root.resolve("conf/cozy/config.yaml"), _project_config_yaml("site", "configured-publication", "smartdox"))
          _write(descriptor, _media_yaml("copy"))

          When("the explicit profile is planned")
          val mediaplan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor, profile = Some("site")))

          Then("the descriptor base selects its location without changing site authorization provenance")
          mediaplan.effectiveProfile.map(_.resolvedRoot) shouldBe Some(descriptor.getParent.resolve("publication").toAbsolutePath.normalize())
          mediaplan.effectiveProfile.flatMap(_.siteKind) shouldBe Some("smartdox")
          mediaplan.effectiveProfile.flatMap(_.sourcePath) shouldBe Some(root.resolve("conf/cozy/config.yaml").toAbsolutePath.normalize())
        }
      }

      "preserve descriptor-only profiles and report standalone context without a marker" in {
        _with_temp_dir("descriptor-only-profile") { root =>
          Given("a standalone descriptor with its legacy profile definition")
          val descriptor = root.resolve("media.yaml")
          _write(descriptor, _media_yaml("copy"))

          When("Cozy resolves and inspects its explicit profile")
          val mediaplan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor, profile = Some("site")))
          val inspection = CozyMedia.inspect(CozyMedia.CommandConfig(descriptor, profile = Some("site")))

          Then("the descriptor-relative root remains valid and no project authority is inferred")
          mediaplan.context.project shouldBe empty
          mediaplan.effectiveProfile.flatMap(_.configuration) shouldBe empty
          mediaplan.effectiveProfile.map(_.resolvedRoot) shouldBe Some(root.resolve("publication").toAbsolutePath.normalize())
          inspection should include_text("project: legacy/standalone")
        }
      }

      "enumerate every publication-profile source when an ordinary selection is unknown" in {
        _with_temp_dir("unknown-publication-profile") { root =>
          Given("a nested descriptor, a known project configuration profile, and a descriptor-only profile")
          val descriptor = root.resolve("packages/part-5/media.yaml")
          val configpath = root.resolve("conf/cozy/config.yaml")
          _write(
            configpath,
            _project_config_yaml("known", "publication", "smartdox")
          )
          _write(
            descriptor,
            _media_yaml("copy").
              replace("profiles:\n  site:\n    root: publication\n", "profiles:\n  descriptor-only:\n    root: descriptor-publication\n").
              replace("      site: summary-ja.png", "      descriptor-only: summary-ja.png")
          )

          When("Cozy resolves the missing ordinary publication profile")
          val error = intercept[RuntimeException] {
            CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor, profile = Some("missing")))
          }

          Then("the diagnostic names the request and every searched context layer")
          error.getMessage should include("requested publication profile: missing")
          Vector("built-in", "user", "project-conf", "project-local", "package-conf", "package-local").foreach { layer =>
            error.getMessage should include(s"$layer:")
          }
          error.getMessage should include("root=<absent>")
          error.getMessage should include("known")
          error.getMessage should include(configpath.toAbsolutePath.normalize().toString)
          error.getMessage should include("descriptor-only")
          error.getMessage should include(descriptor.toAbsolutePath.normalize().toString)
        }
      }
    }

    "decode optional site-registration bindings" which {
      "preserve the normal inspect path when site registration binding is absent" in {
        _with_temp_dir("legacy-inspect") { dir =>
          Given("a normal media descriptor without articleMedia")
          val descriptor = dir.resolve("media.yaml")
          _write(descriptor, _media_yaml("copy"))

          When("Cozy inspects the descriptor")
          val inspection = CozyMedia.inspect(CozyMedia.CommandConfig(descriptor))

          Then("the established inspect representation remains available")
          inspection should include_text("Cozy Media Inspect")
          inspection should include_text("knowledge: development-process/example")
          inspection should include_text("resources: 1")
        }
      }

      "decode a strict optional site registration binding without coupling existing roles" in {
        _with_temp_dir("article-media-binding") { dir =>
          Given("a Part5-style descriptor with independent legacy and registration roles")
          val descriptor = dir.resolve("media.yaml")
          _write(dir.resolve("conf/cozy/config.yaml"), _project_config_yaml("public", "publication", "smartdox"))
          _write(descriptor, _part5_media_yaml())

          When("Cozy inspects and decodes the descriptor")
          val inspection = CozyMedia.inspect(CozyMedia.CommandConfig(descriptor))
          val decoded = StructuredDocumentLoader.loadDocument[CozyMedia.Descriptor](InputSource(descriptor.toFile)).take

          Then("the strict descriptor binding retains its declared registration identity")
          inspection should include_text("resources: 2")
          decoded.articleMedia shouldBe Some(CozyMedia.DescriptorArticleMedia("development-process/part-5", "public"))

          And("each resource retains its independent top-level production role")
          decoded.resources.find(_.id == "part-5-infographic").flatMap(_.role) shouldBe Some("article-summary")
          decoded.resources.find(_.id == "part-5-video").flatMap(_.role) shouldBe Some("article-introduction")

          And("the nested registration roles retain their canonical payloads")
          decoded.resources.find(_.id == "part-5-infographic").flatMap(_.articleMedia) shouldBe
            Some(CozyMedia.ResourceArticleMedia("infographic", Some("/articles/development-process/part-5/summary.png"), Some("image/png"), Some("Part 5 summary"), None))
          decoded.resources.find(_.id == "part-5-video").flatMap(_.articleMedia) shouldBe
            Some(CozyMedia.ResourceArticleMedia("video", None, None, None, Some("part-5-video-ja")))
        }
      }

      "accept a resource registration block before a site association is planned" in {
        _with_temp_dir("unassociated-resource-binding") { dir =>
          Given("a descriptor with canonical resource bindings and no top-level articleMedia")
          val descriptor = dir.resolve("media.yaml")
          _write(descriptor, _part5_media_yaml(toparticlemedia = ""))

          When("Cozy inspects the descriptor")
          val inspection = CozyMedia.inspect(CozyMedia.CommandConfig(descriptor))

          Then("the resource bindings remain valid without an association decision")
          inspection should include_text("resources: 2")
        }
      }

      "reject non-canonical site registration bindings at the articleMedia boundary" in {
        _with_temp_dir("article-media-rejections") { dir =>
          Given("a deterministic matrix of malformed descriptor and resource binding blocks")
          val topcases = Vector(
            "null top-level block" -> "articleMedia: null",
            "scalar top-level block" -> "articleMedia: invalid",
            "partial top-level block" -> "articleMedia:\n  articleIdentity: development-process/part-5",
            "alias top-level block" -> "articleMedia:\n  articleIdentity: development-process/part-5\n  publication-profile: public",
            "unknown top-level block key" -> "articleMedia:\n  articleIdentity: development-process/part-5\n  publicationProfile: public\n  extra: value",
            "empty article identity" -> "articleMedia:\n  articleIdentity: ' '\n  publicationProfile: public",
            "empty publication profile" -> "articleMedia:\n  articleIdentity: development-process/part-5\n  publicationProfile: ' '",
            "untrimmed article identity" -> "articleMedia:\n  articleIdentity: ' development-process/part-5'\n  publicationProfile: public"
          )
          val resourcecases = Vector(
            "null resource block" -> _part5_media_yaml(infographicarticlemedia = "articleMedia: null"),
            "scalar resource block" -> _part5_media_yaml(infographicarticlemedia = "articleMedia: invalid"),
            "missing infographic public path" -> _part5_media_yaml(infographicarticlemedia = "articleMedia:\n      role: infographic"),
            "unknown infographic key" -> _part5_media_yaml(infographicarticlemedia = "articleMedia:\n      role: infographic\n      publicPath: /articles/part-5/summary.png\n      extra: value"),
            "alias infographic key" -> _part5_media_yaml(infographicarticlemedia = "articleMedia:\n      role: infographic\n      public-path: /articles/part-5/summary.png"),
            "unsupported nested role" -> _part5_media_yaml(infographicarticlemedia = "articleMedia:\n      role: image\n      publicPath: /articles/part-5/summary.png"),
            "forbidden infographic production" -> _part5_media_yaml(infographicarticlemedia = "articleMedia:\n      role: infographic\n      publicPath: /articles/part-5/summary.png\n      production: forbidden"),
            "forbidden video public path" -> _part5_media_yaml(videoarticlemedia = "articleMedia:\n      role: video\n      production: part-5-video-ja\n      publicPath: /articles/part-5/video.mp4"),
            "empty infographic public path" -> _part5_media_yaml(infographicarticlemedia = "articleMedia:\n      role: infographic\n      publicPath: ' '"),
            "empty video production" -> _part5_media_yaml(videoarticlemedia = "articleMedia:\n      role: video\n      production: ' '"),
            "untrimmed video production" -> _part5_media_yaml(videoarticlemedia = "articleMedia:\n      role: video\n      production: ' part-5-video-ja'"),
            "infographic kind mismatch" -> _part5_media_yaml().replace("kind: infographic", "kind: image"),
            "video kind mismatch" -> _part5_media_yaml().replace("kind: video", "kind: image")
          )

          When("Cozy inspects every present malformed binding block")
          val topfailures = topcases.map { case (name, articlemedia) =>
            val descriptor = dir.resolve(s"top-${name.replace(' ', '-')}.yaml")
            _write(descriptor, _part5_media_yaml(toparticlemedia = articlemedia))
            name -> intercept[Exception] {
              CozyMedia.inspect(CozyMedia.CommandConfig(descriptor))
            }
          }
          val resourcefailures = resourcecases.map { case (name, yaml) =>
            val descriptor = dir.resolve(s"resource-${name.replace(' ', '-')}.yaml")
            _write(descriptor, yaml)
            name -> intercept[Exception] {
              CozyMedia.inspect(CozyMedia.CommandConfig(descriptor))
            }
          }

          Then("each failure identifies the articleMedia contract boundary")
          topfailures.foreach { case (_, error) =>
            error.getMessage should include_text("articleMedia")
          }
          resourcefailures.foreach { case (_, error) =>
            error.getMessage should include_text("articleMedia")
          }
        }
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

  private def _part5_media_yaml(
    toparticlemedia: String = "articleMedia:\n  articleIdentity: development-process/part-5\n  publicationProfile: public",
    infographicarticlemedia: String = "articleMedia:\n      role: infographic\n      publicPath: /articles/development-process/part-5/summary.png\n      mediaType: image/png\n      alt: Part 5 summary",
    videoarticlemedia: String = "articleMedia:\n      role: video\n      production: part-5-video-ja"
  ): String =
    s"""schema: cozy.media.v1
       |knowledge:
       |  id: development-process/part-5-media
       |  source: knowledge/part-5.dox
       |$toparticlemedia
       |resources:
       |  - id: part-5-infographic
       |    kind: infographic
       |    role: article-summary
       |    source: infographic/part-5.png
       |    build: prebuilt
       |    $infographicarticlemedia
       |  - id: part-5-video
       |    kind: video
       |    role: article-introduction
       |    source: target/render/part-5.mp4
       |    build: prebuilt
       |    $videoarticlemedia
       |""".stripMargin

  private def _project_config_yaml(id: String, root: String, sitekind: String): String =
    s"""project:
       |  id: simplemodeling-org
       |  kind: smartdox-site
       |media:
       |  publication-profiles:
       |    $id:
       |      root: $root
       |      site-kind: $sitekind
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
