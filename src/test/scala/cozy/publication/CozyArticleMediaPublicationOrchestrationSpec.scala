package cozy.publication

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import javax.imageio.ImageIO
import io.circe.Json
import io.circe.parser.parse
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cozy.bok.CozyBok
import cozy.video.{CozyVideo, CozyVideoSpec}

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaPublicationOrchestrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "article-media publication orchestration" should {
    "publish infographic evidence through update-publication" which {
      "return the infographic artifact identity and install strict/integrity records" in {
        _with_root { root =>
          Given("a valid detailed-infographic descriptor, build manifest, and PNG output")
          _write_config(root)
          val fixture = _media(root, "update", "development-process/update", "ja", "images/update.png", _png(0xff3366cc))
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = false)

          When("update-publication executes its producer sequence")
          val result = CozyBok.updatePublication(config, VoicevoxStub, VideoRunnerStub)

          Then("the infographic is published and returned by artifact identity")
          result shouldBe Vector("update")
          Files.isRegularFile(fixture.destination) shouldBe true
          _entry_paths(root.resolve("src/main/publication/article-media.json")) shouldBe Vector(
            "metadata/article-media-integrity/development-process/update/ja/infographic.json",
            "metadata/article-media/development-process/update.json"
          )
        }
      }

      "return video, infographic, and project identities in deterministic producer order" in {
        _with_root { root =>
          Given("lightweight video, infographic, and project knowledge packages")
          _write_config(root, project = true)
          _video_package(root, "video", Some("development-process/video"))
          _media(root, "media", "development-process/media", "ja", "images/media.png", _png(0xff3366cc))
          _project_package(root)
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = true)

          When("update-publication publishes all three producer surfaces")
          val result = CozyBok.updatePublication(
            config,
            CozyVideoSpec.RecordingVoicevoxClient(),
            CozyVideoSpec.PublishingRunner()
          )

          Then("video identity precedes infographic artifact identity and project identity")
          result shouldBe Vector("video", "media", "project")
          Files.isRegularFile(root.resolve("repository/video/video/1.0.0/video-1.0.0.mp4")) shouldBe true
          Files.isRegularFile(root.resolve("repository/images/media.png")) shouldBe true
          Files.isRegularFile(root.resolve("src/main/publication/project.json")) shouldBe true
        }
      }
    }

    "run media before the first one-stop build" which {
      "make the recording runner observe the installed artifact and registry" in {
        _with_root { root =>
          Given("a valid infographic and a local no-op upload workflow")
          _write_config(root)
          _media(root, "publish", "development-process/publish", "ja", "images/publish.png", _png(0xff3366cc))
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = false)
          val runner = new RecordingRunner(root)

          When("one-stop publish runs with a recording build runner")
          CozyBok.publish(config, runner, VoicevoxStub, VideoRunnerStub)

          Then("the first build invocation sees media output already installed")
          runner.commands.nonEmpty shouldBe true
          runner.mediaObserved shouldBe true
        }
      }
    }

    "complete one-stop producer planning before video rendering" which {
      "reject a later malformed infographic before any opted-in video collaborator or managed tree is touched" in {
        _with_root { root =>
          Given("a valid opted-in video followed by an infographic whose required build manifest is missing")
          _write_config(root)
          _video_package(root, "planned-video", Some("development-process/planned-video"))
          _media(root, "malformed-media", "development-process/malformed-media", "ja", "images/malformed.png", _png(0xff3366cc))
          Files.delete(root.resolve("src/main/doxsite/malformed-media/target/cozy-media/manifest.json"))
          val publication = Files.createDirectories(root.resolve("src/main/publication"))
          val repository = root.resolve("repository")
          val beforepublication = _tree(publication)
          val beforerepository = _tree(repository)
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = true)
          val refusingvoicevox = new CozyVideo.VoicevoxClient {
            def speakers(baseurl: String): io.circe.Json = fail("planning must not request video speakers")
            def audioQuery(baseurl: String, text: String, speakerid: Int): io.circe.Json = fail("planning must not create video audio queries")
            def synthesis(baseurl: String, speakerid: Int, audioquery: io.circe.Json): Array[Byte] = fail("planning must not synthesize video audio")
          }
          val refusingrunner = new CozyVideo.VideoProcessRunner {
            def run(command: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = fail("planning must not render video")
          }

          When("update-publication plans every producer before invoking the video renderer")
          val error = intercept[IllegalArgumentException](
            CozyBok.updatePublication(config, refusingvoicevox, refusingrunner)
          )

          Then("the malformed infographic fails before rendering, synthesis, repository admission, or registry replacement")
          error.getMessage should include("build manifest")
          _tree(publication) shouldBe beforepublication
          _tree(repository) shouldBe beforerepository
          Files.exists(root.resolve("repository/video/planned-video/1.0.0/planned-video-1.0.0.mp4")) shouldBe false
        }
      }
    }

    "revalidate committed infographic evidence before registry merge" which {
      "preserve every registry bundle when the build manifest disappears after artifact installation" in {
        _with_root { root =>
          Given("a valid infographic, its build manifest, and an unrelated configured registry bundle")
          _write_config(root)
          val fixture = _media(root, "post-install", "development-process/post-install", "ja", "images/post-install.png", _png(0xff3366cc))
          val registry = root.resolve("src/main/publication/existing.json")
          _write_bundle(registry, "existing")
          val before = _registry_bundles(root.resolve("src/main/publication"))
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = false)
          val manifest = root.resolve("src/main/doxsite/post-install/target/cozy-media/manifest.json")

          When("the manifest is removed after the artifact commit and before the registry merge")
          val error = intercept[IllegalArgumentException](
            CozyArticleMediaInfographicCommand.publish(config, () => (), () => Files.delete(manifest))
          )

          Then("the artifact may be installed but every registry bundle remains byte-identical")
          error.getMessage should include("build manifest")
          Files.isRegularFile(fixture.destination) shouldBe true
          _registry_bundles(root.resolve("src/main/publication")) shouldBe before
          Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
        }
      }
    }

    "perform complete non-mutating one-stop dry-run preflight" which {
      "report descriptor/resource/article/locale/destination/version without build or upload" in {
        _with_root { root =>
          Given("a valid infographic and existing empty publication/repository roots")
          _write_config(root)
          _media(root, "dry", "development-process/dry", "ja", "images/dry.png", _png(0xff3366cc))
          Files.createDirectories(root.resolve("src/main/publication"))
          val beforepublication = _tree(root.resolve("src/main/publication"))
          val beforerepository = _tree(root.resolve("repository"))
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = false, dryrun = true)
          val output = new ByteArrayOutputStream()
          val runner = new RecordingRunner(root)

          When("one-stop publish is invoked in dry-run mode")
          Console.withOut(new PrintStream(output))(CozyBok.publish(config, runner, VoicevoxStub, VideoRunnerStub))

          Then("the candidate is reported and only the operation manifest is written")
          val text = output.toString("UTF-8")
          text should include("infographic: development-process/dry [ja, dry, 1.0.0]")
          text should include("images/dry.png")
          Files.isRegularFile(config.manifestPath) shouldBe true
          val manifest = Files.readString(config.manifestPath, StandardCharsets.UTF_8)
          val json = parse(manifest).fold(error => fail(error.message), identity)
          json.hcursor.downField("infographicPackages").focus shouldBe Some(Json.arr(Json.obj(
            "descriptor" -> Json.fromString(root.resolve("src/main/doxsite/dry/media.json").toString),
            "resource" -> Json.fromString("dry"),
            "articleIdentity" -> Json.fromString("development-process/dry"),
            "locale" -> Json.fromString("ja"),
            "profile" -> Json.fromString("site"),
            "destination" -> Json.fromString(root.resolve("repository/images/dry.png").toString),
            "artifact" -> Json.fromString("dry"),
            "version" -> Json.fromString("1.0.0")
          )))
          json.hcursor.downField("publicationArtifacts").focus shouldBe Some(Json.arr())
          runner.commands shouldBe Vector.empty
          _tree(root.resolve("src/main/publication")) shouldBe beforepublication
          _tree(root.resolve("repository")) shouldBe beforerepository
          Files.exists(root.resolve("website.d")) shouldBe false
          Files.exists(root.resolve("doxsite.d")) shouldBe false
        }
      }

      "use the same video-enabled plan without rendering or admitting an artifact" in {
        _with_root { root =>
          Given("a valid video package and empty publication and repository roots")
          _write_config(root)
          _video_package(root, "dry-video", Some("development-process/dry-video"))
          Files.createDirectories(root.resolve("src/main/publication"))
          val beforepublication = _tree(root.resolve("src/main/publication"))
          val beforerepository = _tree(root.resolve("repository"))
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = true, dryrun = true)
          val refusingvoicevox = new CozyVideo.VoicevoxClient {
            def speakers(baseurl: String): io.circe.Json = fail("dry-run must not synthesize video audio")
            def audioQuery(baseurl: String, text: String, speakerid: Int): io.circe.Json = fail("dry-run must not create video audio queries")
            def synthesis(baseurl: String, speakerid: Int, audioquery: io.circe.Json): Array[Byte] = fail("dry-run must not synthesize video audio")
          }
          val refusingrunner = new CozyVideo.VideoProcessRunner {
            def run(command: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = fail("dry-run must not render video")
          }

          When("one-stop publish is invoked with video enabled in dry-run mode")
          CozyBok.publish(config, new RecordingRunner(root), refusingvoicevox, refusingrunner)

          Then("the precomputed video package is reported without renderer, repository, or registry mutation")
          val manifest = parse(Files.readString(config.manifestPath, StandardCharsets.UTF_8)).fold(error => fail(error.message), identity)
          manifest.hcursor.downField("videoPackages").focus shouldBe Some(Json.arr(Json.fromString(
            root.resolve("src/main/doxsite/concepts/dry-video.video").toString
          )))
          manifest.hcursor.downField("videoCandidates").focus shouldBe Some(Json.arr(Json.obj(
            "articleIdentity" -> Json.fromString("development-process/dry-video"),
            "locale" -> Json.fromString("ja"),
            "role" -> Json.fromString("video"),
            "video" -> Json.fromString("dry-video"),
            "version" -> Json.fromString("1.0.0"),
            "destination" -> Json.fromString(root.resolve("repository/video/dry-video/1.0.0/dry-video-1.0.0.mp4").toString),
            "destinationState" -> Json.fromString("missing"),
            "forceRequested" -> Json.fromBoolean(false),
            "owner" -> Json.fromString("article-media")
          )))
          _tree(root.resolve("src/main/publication")) shouldBe beforepublication
          _tree(root.resolve("repository")) shouldBe beforerepository
          Files.exists(root.resolve("repository/video/dry-video/1.0.0/dry-video-1.0.0.mp4")) shouldBe false
        }
      }

      "reject an existing video destination without force before invoking render collaborators" in {
        _with_root { root =>
          Given("a bound video package whose final repository artifact already exists")
          _write_config(root)
          _video_package(root, "video-conflict", Some("development-process/video-conflict"))
          Files.createDirectories(root.resolve("src/main/publication"))
          val destination = root.resolve("repository/video/video-conflict/1.0.0/video-conflict-1.0.0.mp4")
          _write_bytes(destination, Array[Byte](1, 2, 3))
          val beforepublication = _tree(root.resolve("src/main/publication"))
          val beforerepository = _tree(root.resolve("repository"))
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = true, dryrun = true)
          val refusingvoicevox = new CozyVideo.VoicevoxClient {
            def speakers(baseurl: String): io.circe.Json = fail("dry-run must not inspect video audio")
            def audioQuery(baseurl: String, text: String, speakerid: Int): io.circe.Json = fail("dry-run must not create video audio")
            def synthesis(baseurl: String, speakerid: Int, audioquery: io.circe.Json): Array[Byte] = fail("dry-run must not synthesize video audio")
          }
          val refusingrunner = new CozyVideo.VideoProcessRunner {
            def run(command: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = fail("dry-run must not render video")
          }

          When("one-stop dry-run preflight examines the existing final target")
          val error = intercept[IllegalArgumentException](CozyBok.publish(config, new RecordingRunner(root), refusingvoicevox, refusingrunner))

          Then("force-required replacement is rejected without rendering, registry mutation, manifest output, or byte replacement")
          error.getMessage should include("Use --force")
          _tree(root.resolve("src/main/publication")) shouldBe beforepublication
          _tree(root.resolve("repository")) shouldBe beforerepository
          Files.exists(config.manifestPath) shouldBe false
        }
      }

      "report an explicitly forced existing video destination without replacing its bytes" in {
        _with_root { root =>
          Given("a bound video package and an existing final repository artifact")
          _write_config(root)
          _video_package(root, "video-forced", Some("development-process/video-forced"))
          Files.createDirectories(root.resolve("src/main/publication"))
          val destination = root.resolve("repository/video/video-forced/1.0.0/video-forced-1.0.0.mp4")
          _write_bytes(destination, Array[Byte](4, 5, 6))
          val before = Files.readAllBytes(destination).toVector
          val config = _config(root, Some("1.0.0"), force = true, videoenabled = true, dryrun = true)

          When("one-stop dry-run plans an explicitly forced video replacement")
          CozyBok.publish(config, new RecordingRunner(root), VoicevoxStub, VideoRunnerStub)

          Then("the planned candidate records current destination state and force intent without replacement")
          val manifest = parse(Files.readString(config.manifestPath, StandardCharsets.UTF_8)).fold(error => fail(error.message), identity)
          manifest.hcursor.downField("videoCandidates").downArray.downField("destinationState").as[String] shouldBe Right("existing-regular-file")
          manifest.hcursor.downField("videoCandidates").downArray.downField("forceRequested").as[Boolean] shouldBe Right(true)
          Files.readAllBytes(destination).toVector shouldBe before
          Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
        }
      }

      "reject an external registry snapshot change without mutating registry bytes" in {
        _with_root { root =>
          Given("a valid dry-run candidate and a configured registry bundle")
          _write_config(root)
          _media(root, "snapshot", "development-process/snapshot", "ja", "images/snapshot.png", _png(0xff3366cc))
          val bundle = root.resolve("src/main/publication/existing.json")
          _write_bundle(bundle, "existing")
          val changed = _bundle_json("existing", pretty = true).getBytes(StandardCharsets.UTF_8)
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = false, dryrun = true)

          When("read-only dry-run planning observes a changed registry bundle before snapshot revalidation")
          val error = intercept[IllegalArgumentException](
            CozyArticleMediaInfographicCommand.plan(config, () => _write_bytes(bundle, changed))
          )

          Then("planning fails and leaves the controlled external registry bytes in place")
          error.getMessage should include("stale read-only snapshot")
          Files.readAllBytes(bundle).toVector shouldBe changed.toVector
          Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
          Files.exists(root.resolve("repository/images/snapshot.png")) shouldBe false
        }
      }
    }

    "preserve publication-root alias parity" which {
      "plan and publish through an existing symlink alias into its canonical target" in {
        _with_root { root =>
          Given("an existing publication target directory addressed through a configured symlink alias")
          _write_config(root)
          _media(root, "alias", "development-process/alias", "ja", "images/alias.png", _png(0xff3366cc))
          val target = Files.createDirectories(root.resolve("publication-target"))
          val alias = root.resolve("publication-alias")
          Files.createSymbolicLink(alias, target)
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = false, publication = "publication-alias")

          When("read-only planning and actual publication use the configured alias")
          CozyArticleMediaInfographicCommand.plan(config)
          CozyArticleMediaInfographicCommand.publish(config)

          Then("one canonical target receives the registry without an independent alias tree")
          val canonical = target.resolve("article-media.json")
          Files.isRegularFile(canonical) shouldBe true
          alias.resolve("article-media.json").toRealPath() shouldBe canonical.toRealPath()
          _registry_bundle_names(target) shouldBe Vector("article-media.json")
        }
      }
    }

    "describe all update-publication producers" which {
      "print detailed-infographic media help without command side effects" in {
        _with_root { root =>
          Given("an otherwise empty project directory")
          val before = _tree(root)
          val output = new ByteArrayOutputStream()

          When("update-publication help is requested")
          val result = Console.withOut(new PrintStream(output))(CozyBok.execute(List("bok", "update-publication", "--help")))

          Then("the help names video, detailed-infographic media, and project knowledge packages only")
          result shouldBe true
          output.toString("UTF-8") should include(".video, detailed-infographic media, and project knowledge packages")
          _tree(root) shouldBe before
        }
      }
    }

    "reject dry-run media preflight before mutation" which {
      "fail when version is missing" in {
        _with_root { root =>
          Given("a selected infographic without an explicit version")
          _write_config(root)
          _media(root, "missing-version", "development-process/version", "ja", "images/version.png", _png(0xff3366cc))
          val config = _config(root, None, force = false, videoenabled = false, dryrun = true)

          When("one-stop dry-run performs media preflight")
          val error = intercept[IllegalArgumentException](CozyBok.publish(config, new RecordingRunner(root), VoicevoxStub, VideoRunnerStub))

          Then("version failure precedes manifest, artifact, and registry mutation")
          error.getMessage should include("--version")
          Files.exists(config.manifestPath) shouldBe false
          Files.exists(root.resolve("repository/images/version.png")) shouldBe false
          Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
        }
      }

      "fail a differing destination without force and preserve bytes" in {
        _with_root { root =>
          Given("a differing existing infographic destination")
          _write_config(root)
          _media(root, "conflict", "development-process/conflict", "ja", "images/conflict.png", _png(0xff3366cc))
          val destination = root.resolve("repository/images/conflict.png")
          _write_bytes(destination, _png(0xffcc6633))
          val before = Files.readAllBytes(destination).toVector
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = false, dryrun = true)

          When("one-stop dry-run validates the current destination")
          val error = intercept[IllegalArgumentException](CozyBok.publish(config, new RecordingRunner(root), VoicevoxStub, VideoRunnerStub))

          Then("the conflict fails without manifest, registry, or byte replacement")
          error.getMessage should include("use force")
          Files.readAllBytes(destination).toVector shouldBe before
          Files.exists(config.manifestPath) shouldBe false
          Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
        }
      }

      "accept a differing destination with explicit force without replacing it" in {
        _with_root { root =>
          Given("a differing existing infographic destination and explicit force")
          _write_config(root)
          _media(root, "forced", "development-process/forced", "ja", "images/forced.png", _png(0xff3366cc))
          val destination = root.resolve("repository/images/forced.png")
          _write_bytes(destination, _png(0xffcc6633))
          val before = Files.readAllBytes(destination).toVector
          val config = _config(root, Some("1.0.0"), force = true, videoenabled = false, dryrun = true)

          When("one-stop dry-run plans the forced replacement")
          CozyBok.publish(config, new RecordingRunner(root), VoicevoxStub, VideoRunnerStub)

          Then("the plan succeeds but does not replace the destination")
          Files.readAllBytes(destination).toVector shouldBe before
          Files.isRegularFile(config.manifestPath) shouldBe true
          Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
        }
      }
    }

    "isolate legacy video force from infographic force" which {
      "retain video force while leaving mediaForce disabled" in {
        _with_root { root =>
          Given("a project config with bok.video.force enabled and no CLI force")
          _write_config(root, videoforce = true)

          When("publication arguments are normalized")
          val config = CozyBok.PublicationConfig.create("publish", List(root.toString, "--dry-run", "--version", "1.0.0"))
          Then("legacy video force remains enabled but infographic force remains isolated")
          config.force shouldBe true
          config.mediaForce shouldBe false
        }
      }
    }

    "preserve media-free compatibility" which {
      "keep planning non-mutating and retain explicit dry-run rejection" in {
        _with_root { root =>
          Given("a source with no media descriptors and an absent publication root")
          Files.createDirectories(root.resolve("src/main/doxsite"))
          Files.createDirectories(root.resolve("repository"))
          val config = _config(root, Some("1.0.0"), force = false, videoenabled = false)

          When("the infographic plan is evaluated")
          val plan = CozyArticleMediaInfographicCommand.plan(config)

          Then("it remains empty and non-mutating")
          plan.candidates shouldBe Vector.empty
          Files.exists(root.resolve("src/main/publication")) shouldBe false
          _direct_children(root.resolve("repository")) shouldBe Vector.empty

          And("an explicit publish-media dry-run remains invalid")
          val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(config.copy(dryRun = true)))
          error.getMessage should include("only supported by bok publish")
        }
      }
    }
  }

  private final class MediaFixture(val destination: Path)

  private final class RecordingRunner(root: Path) extends CozyBok.Runner {
    var commands = Vector.empty[Vector[String]]
    var mediaObserved = false
    def run(command: Vector[String], cwd: Path): Unit = {
      if (commands.isEmpty)
        mediaObserved = Files.isRegularFile(root.resolve("repository/images/publish.png")) &&
          Files.isRegularFile(root.resolve("src/main/publication/article-media.json"))
      commands :+= command
    }
  }

  private object VoicevoxStub extends CozyVideo.VoicevoxClient {
    def speakers(baseurl: String): io.circe.Json = io.circe.Json.arr()
    def audioQuery(baseurl: String, text: String, speakerid: Int): io.circe.Json = io.circe.Json.obj()
    def synthesis(baseurl: String, speakerid: Int, audioquery: io.circe.Json): Array[Byte] = Array.emptyByteArray
  }

  private object VideoRunnerStub extends CozyVideo.VideoProcessRunner {
    def run(command: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = CozyVideo.VideoCommandResult(0, "", "")
  }

  private def _config(
    root: Path,
    version: Option[String],
    force: Boolean,
    videoenabled: Boolean,
    dryrun: Boolean = false,
    publication: String = "src/main/publication"
  ): CozyBok.PublicationConfig =
    CozyBok.PublicationConfig(root, "src/main/doxsite", publication, None, "repository", version, force, videoenabled, dryrun, "draft", force)

  private def _bundle_json(name: String, pretty: Boolean = false): String =
    if (pretty)
      s"""{
         |  "type": "publication-bundle",
         |  "publication": { "name": "$name" },
         |  "entries": []
         |}""".stripMargin
    else
      s"""{"type":"publication-bundle","publication":{"name":"$name"},"entries":[]}"""

  private def _write_bundle(path: Path, name: String): Unit =
    _write_bytes(path, _bundle_json(name).getBytes(StandardCharsets.UTF_8))

  private def _write_config(root: Path, videoforce: Boolean = false, project: Boolean = false): Unit = {
    val projectconfig = if (project)
      s"""  projects:
         |    project:
         |      repository: path
         |      path: ${root.resolve("external/project")}
         |""".stripMargin
    else ""
    val command =
      s"""bok:
         |  video:
         |    force: ${videoforce}
         |${projectconfig}
         |  workflow:
         |    upload:
         |      command: \"true\"
         |""".stripMargin
    _write_bytes(root.resolve("conf/cozy/config.yaml"), command.getBytes(StandardCharsets.UTF_8))
    Files.createDirectories(root.resolve("src/main/doxsite"))
    Files.createDirectories(root.resolve("repository"))
  }

  private def _video_package(root: Path, name: String, articleidentity: Option[String]): Path = {
    val packagedir = root.resolve(s"src/main/doxsite/concepts/${name}.video")
    Files.createDirectories(packagedir)
    _write_bytes(packagedir.resolve("index.dox"), "# Example\n".getBytes(StandardCharsets.UTF_8))
    _write_bytes(packagedir.resolve("script.json"), "{}".getBytes(StandardCharsets.UTF_8))
    val binding = articleidentity.map { identity =>
      s"""
         |  articleMedia:
         |    articleIdentity: "$identity"
         |    locale: ja
         |    status: published""".stripMargin
    }.getOrElse("")
    val descriptor =
      s"""video:
         |  name: $name
         |title: $name
         |version: 1.0.0
         |article: index.dox
         |script: script.json
         |publish:
         |  module: $name
         |  publicPath: videos/legacy-decoy.mp4$binding
         |""".stripMargin
    _write_bytes(packagedir.resolve("video.yaml"), descriptor.getBytes(StandardCharsets.UTF_8))
    packagedir
  }

  private def _project_package(root: Path): Unit = {
    _write_bytes(root.resolve("external/project/build.sbt"), "ThisBuild / version := \"0.1.0\"\n".getBytes(StandardCharsets.UTF_8))
    val packagedir = root.resolve("src/main/doxsite/projects/concept/project")
    _write_bytes(packagedir.resolve("index.dox"), "Project\n=======\n".getBytes(StandardCharsets.UTF_8))
    _write_bytes(packagedir.resolve("project.yaml"),
      """project:
        |  type: car
        |  name: project
        |  mode: external
        |  ref: project
        |car:
        |  module: project
        |title: Project
        |version: 0.1.0
        |publication:
        |  path: projects/concept/project
        |""".stripMargin.getBytes(StandardCharsets.UTF_8))
  }

  private def _media(root: Path, id: String, article: String, locale: String, destination: String, png: Array[Byte]): MediaFixture = {
    val directory = Files.createDirectories(root.resolve(s"src/main/doxsite/$id"))
    val output = directory.resolve(s"target/cozy-media/$id.png")
    val manifest = directory.resolve("target/cozy-media/manifest.json")
    _write_bytes(output, png)
    val digest = _sha256(output)
    val descriptor =
      s"""{"schema":"cozy.media.v1","knowledge":{"id":"$article","source":"article.dox"},"profiles":{"site":{"root":"../../../../repository"}},"resources":[{"id":"$id","kind":"image","language":"$locale","role":"detailed-infographic","source":"input.svg","output":"target/cozy-media/$id.png","build":"copy","publications":{"site":"$destination"}}]}"""
    val buildmanifest = s"""{"schema":"cozy.media.v1","knowledge":"$article","resources":[{"id":"$id","path":"target/cozy-media/$id.png","sha256":"$digest"}]}"""
    _write_bytes(directory.resolve("media.json"), descriptor.getBytes(StandardCharsets.UTF_8))
    _write_bytes(manifest, buildmanifest.getBytes(StandardCharsets.UTF_8))
    new MediaFixture(root.resolve(s"repository/$destination"))
  }

  private def _entry_paths(path: Path): Vector[String] = {
    val json = play.api.libs.json.Json.parse(Files.readString(path, StandardCharsets.UTF_8))
    (json \ "entries").as[play.api.libs.json.JsArray].value.toVector.map(x => (x \ "path").as[String]).sorted
  }

  private def _tree(root: Path): Vector[(String, String)] =
    if (!Files.exists(root)) Vector.empty
    else {
      val stream = Files.walk(root)
      try stream.iterator().asScala.filter(Files.isRegularFile(_)).toVector.sortBy(_.toString).map(path => root.relativize(path).toString -> _sha256(path))
      finally stream.close()
    }

  private def _direct_children(root: Path): Vector[Path] = {
    val stream = Files.list(root)
    try stream.iterator().asScala.toVector.sortBy(_.toString)
    finally stream.close()
  }

  private def _registry_bundles(root: Path): Vector[(String, String)] =
    _direct_children(root).
      filter(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".json")).
      map(path => path.getFileName.toString -> _sha256(path))

  private def _registry_bundle_names(root: Path): Vector[String] =
    _registry_bundles(root).map(_._1)

  private def _png(color: Int): Array[Byte] = {
    val image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, color)
    val output = new ByteArrayOutputStream()
    ImageIO.write(image, "png", output)
    output.toByteArray
  }

  private def _write_bytes(path: Path, bytes: Array[Byte]): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, bytes)
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(x => f"${x & 0xff}%02x").mkString

  private def _with_root(f: Path => Unit): Unit = {
    val work = Paths.get("target/cozy-article-media-publication-orchestration/work").toAbsolutePath.normalize()
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "orchestration-")
    try f(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
}
