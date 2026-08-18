package cozy.publication

import java.nio.charset.StandardCharsets
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cozy.bok.CozyBok
import cozy.video.{CozyVideoPublisher, CozyVideoSpec}
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsArray, JsObject, Json}

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaVideoCommandSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaVideoCommand" should {
    "leave an empty prepared command as an exact no-op" in {
      Given("an existing publication and repository root with no prepared video result")
      val work = Paths.get("target/cozy-article-media-video-command/work").toAbsolutePath.normalize()
      _delete_tree(work)
      Files.createDirectories(work)
      val publication = Files.createDirectories(work.resolve("publication"))
      val repository = Files.createDirectories(work.resolve("repository"))

      try {
        When("the command commits its empty result vector")
        val results = CozyArticleMediaVideoCommand.publish(publication, repository, Vector.empty)

        Then("it returns the same ordered empty result and creates no registry bundle")
        results shouldBe Vector.empty
        _direct_children(publication) shouldBe Vector.empty
      } finally {
        _delete_tree(work)
      }
    }

    "produce deterministic bound video candidates before rendering" in {
      _with_root { root =>
        Given("a resolved bound video package and empty configured publication and repository roots")
        val packagedir = _video_package(root, "planned", Some("development-process/planned"))
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        val repository = Files.createDirectories(root.resolve("repository"))
        val plan = CozyVideoPublisher.planPublication(CozyVideoPublisher.PublishVideoConfig(
          packagedir, publication, root.resolve("warehouse"), Some("1.0.0"), false, Some(repository)
        ))

        When("the command constructs its render-independent preflight")
        val preflight = CozyArticleMediaVideoCommand.preflight(publication, repository, Vector(plan))

        Then("the exact article role, target, version, current state, and canonical owner are ordered deterministically")
        preflight.candidates.map(x => (x.articleIdentity, x.locale, x.role, x.video, x.version, x.destination, x.destinationState, x.forceRequested, x.owner)) shouldBe Vector(
          ("development-process/planned", "ja", "video", "planned", "1.0.0", repository.resolve("video/planned/1.0.0/planned-1.0.0.mp4"), "missing", false, "article-media")
        )
        _direct_children(publication) shouldBe Vector.empty
        _direct_children(repository) shouldBe Vector.empty
      }
    }

    "reject a current bound article ownership conflict during video preflight" in {
      _with_root { root =>
        Given("a bound video package and strict and integrity records split across configured bundles")
        val packagedir = _video_package(root, "ownership-conflict", Some("development-process/example"))
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        val repository = Files.createDirectories(root.resolve("repository"))
        val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
          CozyArticleMediaPublication.Variant("ja", video = Some(VideoReference(
            VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI("/repository/video/example.mp4"))
          )))
        ))
        val integrity = _video_integrity("ja", "/repository/video/example.mp4")
        _write_existing_bundle(publication, "strict-owner", Vector(_bundle_entry(strict.entryPath, strict.metadata)))
        _write_existing_bundle(publication, "integrity-owner", Vector(_bundle_entry(integrity.entryPath, integrity.metadata)))
        val plan = CozyVideoPublisher.planPublication(CozyVideoPublisher.PublishVideoConfig(
          packagedir, publication, root.resolve("warehouse"), Some("1.0.0"), false, Some(repository)
        ))
        val beforepublication = _tree(publication)
        val beforerepository = _tree(repository)

        When("the command performs its complete read-only video ownership preflight")
        val error = intercept[IllegalArgumentException](CozyArticleMediaVideoCommand.preflight(publication, repository, Vector(plan)))

        Then("the ambiguity is rejected before rendering or any repository and registry mutation")
        error.getMessage should include("multiple bundle owners")
        _tree(publication) shouldBe beforepublication
        _tree(repository) shouldBe beforerepository
      }
    }

    "commit an unbound BoK video as its legacy bundle and repository artifact only" in {
      _with_root { root =>
        Given("an unbound .video package")
        _video_package(root, "unbound", None)

        When("the BoK command publishes all discovered video packages")
        val results = _publish_bok(root)

        Then("the legacy registry shape and artifact exist without article-media records")
        results.map(_.video.name) shouldBe Vector("unbound")
        Files.isRegularFile(root.resolve("src/main/publication/unbound.json")) shouldBe true
        Files.isRegularFile(root.resolve("repository/video/unbound/1.0.0/unbound-1.0.0.mp4")) shouldBe true
        _entry_paths(root.resolve("src/main/publication/unbound.json")) shouldBe Vector(
          "metadata/artifacts/repository/unbound.json", "metadata/catalog/videos/unbound.json",
          "metadata/video/unbound/1.0.0/manifest.json", "metadata/video/unbound/1.0.0/rdf.json",
          "metadata/video/unbound/latest.json", "metadata/videos/unbound/metadata.json"
        )
        _publication_entries(root).filter(_.startsWith("metadata/article-media")) shouldBe Vector.empty
      }
    }

    "project opted-in video evidence into strict and integrity records without descriptor publicPath substitution" in {
      _with_root { root =>
        Given("an opted-in YAML video descriptor with a legacy publicPath decoy")
        _video_package(root, "bound", Some("development-process/example"))

        When("the BoK command prepares and commits the package")
        val results = _publish_bok(root)
        val entries = _publication_entries(root)

        Then("legacy metadata and canonical evidence-derived article-media records coexist")
        results.map(_.video.name) shouldBe Vector("bound")
        entries should contain("metadata/videos/bound/metadata.json")
        entries should contain("metadata/article-media/development-process/example.json")
        entries should contain("metadata/article-media-integrity/development-process/example/ja/video.json")
        val strict = _entry(root, "metadata/article-media/development-process/example.json")
        (strict \ "variants" \ "ja" \ "video" \ "content_url").as[String] shouldBe "/repository/video/bound/1.0.0/bound-1.0.0.mp4"
        (strict \ "variants" \ "ja" \ "video" \ "status").as[String] shouldBe "published"
        (strict \ "variants" \ "ja" \ "video" \ "content_url").as[String] should not be "/videos/legacy-decoy.mp4"
        (_entry(root, "metadata/article-media-integrity/development-process/example/ja/video.json") \ "publicationState").as[String] shouldBe "published"
      }
    }

    "commit mixed bound and unbound packages in one command transaction" in {
      _with_root { root =>
        Given("one bound and one unbound package")
        _video_package(root, "bound", Some("development-process/example"))
        _video_package(root, "unbound", None)

        When("the command commits its prepared package vector")
        val results = _publish_bok(root)

        Then("both legacy bundles exist while only the bound package contributes the role")
        results.map(_.video.name) shouldBe Vector("bound", "unbound")
        Files.isRegularFile(root.resolve("src/main/publication/bound.json")) shouldBe true
        Files.isRegularFile(root.resolve("src/main/publication/unbound.json")) shouldBe true
        _publication_entries(root).filter(_.startsWith("metadata/article-media")) shouldBe Vector(
          "metadata/article-media-integrity/development-process/example/ja/video.json",
          "metadata/article-media/development-process/example.json"
        )
      }
    }

    "reject duplicate bound roles before replacing any publication bundle" in {
      _with_root { root =>
        Given("two prepared packages targeting the same normalized article locale and video role")
        _video_package(root, "first", Some("development-process/example"))
        _video_package(root, "second", Some(" development-process/example "))
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        val repository = Files.createDirectories(root.resolve("repository"))
        Files.createFile(publication.resolve(".cozy-publication-registry.lock"))
        val before = _tree(publication)
        val beforerepository = _tree(repository)

        When("the command attempts its one transaction")
        val error = intercept[IllegalArgumentException](_publish_bok(root))

        Then("the duplicate is rejected before video artifact admission and both managed trees remain unchanged")
        error.getMessage should include("Duplicate article-media role intent")
        _tree(publication) shouldBe before
        _tree(repository) shouldBe beforerepository
      }
    }

    "leave registry and repository bytes unchanged when separate artifact admission fails" in {
      _with_root { root =>
        Given("an existing registry bundle, repository tree, and a planned video metadata replacement")
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        val repository = Files.createDirectories(root.resolve("repository"))
        _write_unbound_existing_bundle(publication, "existing")
        val metadata = CozyPublicationCompiler.metadataPublication(
          "candidate",
          None,
          root,
          Vector("metadata/videos/candidate/metadata.json" -> Json.obj("name" -> "candidate"))
        )
        val beforepublication = _tree(publication)
        val beforerepository = _tree(repository)

        When("the command transaction rejects its distinct artifact-admission callback")
        val error = intercept[IllegalArgumentException](
          CozyArticleMediaRegistry.transaction(publication) { transaction =>
            transaction.publish(Vector(metadata))(_ => Vector.empty)(
              () => throw new IllegalArgumentException("simulated artifact admission failure")
            )
          }
        )

        Then("no registry replacement or repository mutation is committed after planning")
        error.getMessage should include("simulated artifact admission failure")
        _tree(publication) shouldBe beforepublication
        _tree(repository) shouldBe beforerepository
      }
    }

    "retain direct CozyVideoPublisher.publish legacy behavior without the command adapter" in {
      _with_root { root =>
        Given("one unbound package and the standard lightweight video fixtures")
        val packagedir = _video_package(root, "direct", None)
        val publication = Files.createDirectories(root.resolve("publication"))
        val repository = Files.createDirectories(root.resolve("repository"))

        When("the direct publisher publishes it")
        val result = CozyVideoPublisher.publish(
          CozyVideoPublisher.PublishVideoConfig(packagedir, publication, root.resolve("warehouse"), None, false, Some(repository)),
          CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner()
        )

        Then("the legacy bundle is committed without article-media registry records")
        Files.isRegularFile(result.publicationBundle) shouldBe true
        _entry_paths(result.publicationBundle) should contain("metadata/videos/direct/metadata.json")
      }
    }

    "serialize concurrent force-enabled publishes before either renderer enters the same plan workspace" in {
      _with_root { root =>
        Given("one force-enabled bound plan, two command callers, and a lightweight first renderer held by bounded latches")
        val packagedir = _video_package(root, "concurrent", Some("development-process/example"))
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        val repository = Files.createDirectories(root.resolve("repository"))
        val plan = CozyVideoPublisher.planPublication(CozyVideoPublisher.PublishVideoConfig(
          packagedir, publication, root.resolve("warehouse"), Some("1.0.0"), true, Some(repository)
        ))
        val firstentered = new CountDownLatch(1)
        val releasefirst = new CountDownLatch(1)
        val secondstarted = new CountDownLatch(1)
        val secondentered = new CountDownLatch(1)
        val runner = new BlockingPublishingRunner(firstentered, releasefirst, secondentered)
        implicit val executioncontext: ExecutionContext = ExecutionContext.global

        When("the first publish is held in rendering and the second publish begins against the same frozen plan")
        val first = Future(CozyArticleMediaVideoCommand.publish(
          publication, repository, Vector(plan), CozyVideoSpec.RecordingVoicevoxClient(), runner
        ))
        firstentered.await(5, TimeUnit.SECONDS) shouldBe true
        val second = Future {
          secondstarted.countDown()
          CozyArticleMediaVideoCommand.publish(
            publication, repository, Vector(plan), CozyVideoSpec.RecordingVoicevoxClient(), runner
          )
        }
        secondstarted.await(5, TimeUnit.SECONDS) shouldBe true
        secondentered.await(250, TimeUnit.MILLISECONDS) shouldBe false
        releasefirst.countDown()
        val firstresult = Await.result(first, 10.seconds)
        val secondresult = Await.result(second, 10.seconds)

        Then("the second renderer entered only after the shared transaction released and both callers observe one consistent artifact and metadata result")
        firstresult.map(_.video.name) shouldBe Vector("concurrent")
        secondresult.map(_.video.name) shouldBe Vector("concurrent")
        secondentered.await(5, TimeUnit.SECONDS) shouldBe true
        Files.isRegularFile(repository.resolve("video/concurrent/1.0.0/concurrent-1.0.0.mp4")) shouldBe true
        _entry_paths(publication.resolve("concurrent.json")) should contain("metadata/videos/concurrent/metadata.json")
      }
    }

    "reject producer input changed after preflight before renderer entry or artifact admission" in {
      _with_root { root =>
        Given("a bound plan whose command-wide preflight has captured its package source evidence")
        val packagedir = _video_package(root, "before-render-drift", Some("development-process/example"))
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        val repository = Files.createDirectories(root.resolve("repository"))
        val plan = CozyVideoPublisher.planPublication(CozyVideoPublisher.PublishVideoConfig(
          packagedir, publication, root.resolve("warehouse"), Some("1.0.0"), false, Some(repository)
        ))
        val preflight = CozyArticleMediaVideoCommand.preflight(publication, repository, Vector(plan))
        val beforepublication = _tree(publication)
        val beforerepository = _tree(repository)
        Files.write(packagedir.resolve("script.json"), "{\"changed\":true}".getBytes(StandardCharsets.UTF_8))
        val runner = CozyVideoSpec.PublishingRunner()

        When("the command reruns the frozen preflight after the producer script has changed")
        val error = intercept[IllegalArgumentException](CozyArticleMediaVideoCommand.publish(
          preflight, CozyVideoSpec.RecordingVoicevoxClient(), runner
        ))

        Then("source drift is rejected before the renderer, repository admission, or publication replacement")
        error.getMessage should include("producer sources changed after planning")
        runner.commands shouldBe empty
        _tree(publication) shouldBe beforepublication
        _tree(repository) shouldBe beforerepository
      }
    }

    "reject producer input changed during rendering before repository admission or publication replacement" in {
      _with_root { root =>
        Given("a bound plan and a lightweight renderer that changes one producer script after workspace preparation")
        val packagedir = _video_package(root, "during-render-drift", Some("development-process/example"))
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        val repository = Files.createDirectories(root.resolve("repository"))
        val plan = CozyVideoPublisher.planPublication(CozyVideoPublisher.PublishVideoConfig(
          packagedir, publication, root.resolve("warehouse"), Some("1.0.0"), false, Some(repository)
        ))
        val beforepublication = _tree(publication)
        val beforerepository = _tree(repository)
        val runner = new SourceMutatingPublishingRunner(packagedir.resolve("script.json"))

        When("the renderer completes its workspace work after the original producer input changes")
        val error = intercept[IllegalArgumentException](CozyArticleMediaVideoCommand.publish(
          publication, repository, Vector(plan), CozyVideoSpec.RecordingVoicevoxClient(), runner
        ))

        Then("post-render validation rejects the drift before either managed tree is admitted or replaced")
        error.getMessage should include("producer sources changed after planning")
        runner.commands should not be empty
        _tree(publication) shouldBe beforepublication
        _tree(repository) shouldBe beforerepository
      }
    }

    "reuse a bound legacy bundle while preserving its other article-media roles, generic entries, and declaration extensions" in {
      _with_root { root =>
        Given("a bound video package whose sole existing article-media owner is its legacy bundle")
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        _write_bound_existing_bundle(publication, "legacy")
        val generic = _entry(root, "metadata/generic/legacy.json")
        val jaintegrity = _entry(root, "metadata/article-media-integrity/development-process/example/ja/infographic.json")
        val enintegrity = _entry(root, "metadata/article-media-integrity/development-process/example/en/infographic.json")
        _video_package(root, "legacy", Some("development-process/example"))

        When("the BoK command republishes the generated legacy video paths")
        val results = _publish_bok(root)
        val bundle = Json.parse(Files.readString(publication.resolve("legacy.json"), StandardCharsets.UTF_8)).as[JsObject]
        val strict = _entry(root, "metadata/article-media/development-process/example.json")

        Then("the same owner receives the video role without losing retained bundle state")
        results.map(_.video.name) shouldBe Vector("legacy")
        Files.exists(publication.resolve("article-media.json")) shouldBe false
        _entry_paths(publication.resolve("legacy.json")) should contain("metadata/article-media/development-process/example.json")
        (bundle \ "publication" \ "title").as[String] shouldBe "Legacy video bundle"
        (bundle \ "publication" \ "declarationExtension" \ "retained").as[String] shouldBe "yes"
        (bundle \ "extension" \ "owner").as[String] shouldBe "legacy"
        (bundle \ "provenance" \ "origin").as[String] shouldBe "fixture"
        _entry(root, "metadata/generic/legacy.json") shouldBe generic
        (strict \ "variants" \ "ja" \ "infographic" \ "public_path").as[String] shouldBe "/images/ja/example.png"
        (strict \ "variants" \ "en" \ "infographic" \ "public_path").as[String] shouldBe "/images/en/example.png"
        (strict \ "variants" \ "ja" \ "video" \ "content_url").as[String] shouldBe "/repository/video/legacy/1.0.0/legacy-1.0.0.mp4"
        _entry(root, "metadata/article-media-integrity/development-process/example/ja/infographic.json") shouldBe jaintegrity
        _entry(root, "metadata/article-media-integrity/development-process/example/en/infographic.json") shouldBe enintegrity
        (_entry(root, "metadata/article-media-integrity/development-process/example/ja/video.json") \ "publicationState").as[String] shouldBe "published"
        _entry(root, "metadata/videos/legacy/metadata.json") should not be Json.obj("state" -> "stale")
      }
    }

    "republish an unbound legacy bundle without creating article-media records or replacing unrelated state" in {
      _with_root { root =>
        Given("an unbound video package and an existing legacy bundle with generic declaration state")
        val publication = Files.createDirectories(root.resolve("src/main/publication"))
        _write_unbound_existing_bundle(publication, "legacy")
        val generic = _entry(root, "metadata/generic/legacy.json")
        _video_package(root, "legacy", None)

        When("the BoK command republishes its generated legacy metadata paths")
        val results = _publish_bok(root)
        val bundle = Json.parse(Files.readString(publication.resolve("legacy.json"), StandardCharsets.UTF_8)).as[JsObject]

        Then("the legacy update preserves unrelated bundle bytes and remains article-media-free")
        results.map(_.video.name) shouldBe Vector("legacy")
        (bundle \ "publication" \ "title").as[String] shouldBe "Legacy video bundle"
        (bundle \ "publication" \ "declarationExtension" \ "retained").as[String] shouldBe "yes"
        (bundle \ "extension" \ "owner").as[String] shouldBe "legacy"
        (bundle \ "provenance" \ "origin").as[String] shouldBe "fixture"
        _entry(root, "metadata/generic/legacy.json") shouldBe generic
        _publication_entries(root).filter(_.startsWith("metadata/article-media")) shouldBe Vector.empty
        _entry(root, "metadata/videos/legacy/metadata.json") should not be Json.obj("state" -> "stale")
      }
    }

    "keep pure prepared-plan transaction output deterministic across shuffled orders" in {
      Given("three distinct lightweight unbound prepared plans")
      val names = Vector("alpha", "beta", "gamma")

      When("at least thirty shuffled command orders are committed")
      val orders = Gen.oneOf(names.permutations.toVector)
      val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(orders) { shuffled =>
        _with_root { root =>
          val publication = Files.createDirectories(root.resolve("publication"))
          val repository = Files.createDirectories(root.resolve("repository"))
          CozyArticleMediaVideoCommand.publish(publication, repository, shuffled.map(_prepared(root, _)).toVector)
          _publication_entries_at(publication) == names.map(x => s"metadata/videos/$x/metadata.json")
        }
      })

      Then("every transaction produces the same sorted legacy registry surface without nested locking")
      check.passed shouldBe true
      check.succeeded should be >= 30
    }
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }

  private final class BlockingPublishingRunner(
    firstentered: CountDownLatch,
    releasefirst: CountDownLatch,
    secondentered: CountDownLatch
  ) extends cozy.video.CozyVideo.VideoProcessRunner {
    private val _delegate = CozyVideoSpec.PublishingRunner()
    private val _first_render = new AtomicBoolean(true)

    def run(args: Vector[String], cwd: Path): cozy.video.CozyVideo.VideoCommandResult = {
      if (args.contains("python3")) {
        if (_first_render.compareAndSet(true, false)) {
          firstentered.countDown()
          if (!releasefirst.await(5, TimeUnit.SECONDS))
            throw new IllegalStateException("Timed out waiting to release the first publisher renderer")
        } else
          secondentered.countDown()
      }
      _delegate.run(args, cwd)
    }
  }

  private final class SourceMutatingPublishingRunner(source: Path) extends cozy.video.CozyVideo.VideoProcessRunner {
    private val _delegate = CozyVideoSpec.PublishingRunner()
    private val _changed = new AtomicBoolean(false)

    def commands = _delegate.commands

    def run(args: Vector[String], cwd: Path): cozy.video.CozyVideo.VideoCommandResult = {
      val result = _delegate.run(args, cwd)
      if (args.contains("python3") && _changed.compareAndSet(false, true))
        Files.write(source, "{\"changedDuringRender\":true}".getBytes(StandardCharsets.UTF_8))
      result
    }
  }

  private def _direct_children(path: Path): Vector[Path] = {
    val stream = Files.list(path)
    try stream.iterator().asScala.toVector
    finally stream.close()
  }

  private def _with_root[A](f: Path => A): A = {
    val work = Paths.get("target/cozy-article-media-video-command/work").toAbsolutePath.normalize()
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "command-")
    try f(root)
    finally _delete_tree(root)
  }

  private def _video_package(root: Path, name: String, articleidentity: Option[String]): Path = {
    val packagedir = root.resolve(s"src/main/doxsite/concepts/${name}.video")
    Files.createDirectories(packagedir)
    Files.write(packagedir.resolve("index.dox"), "# Example\n".getBytes(StandardCharsets.UTF_8))
    Files.write(packagedir.resolve("script.json"), "{}".getBytes(StandardCharsets.UTF_8))
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
    Files.write(packagedir.resolve("video.yaml"), descriptor.getBytes(StandardCharsets.UTF_8))
    packagedir
  }

  private def _publish_bok(root: Path): Vector[CozyVideoPublisher.PublishVideoResult] =
    CozyBok.publishVideo(
      CozyBok.PublicationConfig.create("publish-video", List(root.toString)),
      CozyVideoSpec.RecordingVoicevoxClient(),
      CozyVideoSpec.PublishingRunner()
    )

  private def _publication_entries(root: Path): Vector[String] = {
    val publication = root.resolve("src/main/publication")
    if (!Files.isDirectory(publication)) Vector.empty
    else _direct_children(publication).filter(_.getFileName.toString.endsWith(".json")).flatMap(_entry_paths).distinct.sorted
  }

  private def _publication_entries_at(root: Path): Vector[String] =
    _direct_children(root).filter(_.getFileName.toString.endsWith(".json")).flatMap(_entry_paths).distinct.sorted

  private def _entry_paths(bundle: Path): Vector[String] =
    (Json.parse(Files.readString(bundle, StandardCharsets.UTF_8)) \ "entries").as[JsArray].value.toVector.map(x => (x \ "path").as[String]).sorted

  private def _entry(root: Path, path: String): JsObject = {
    val bundle = _direct_children(root.resolve("src/main/publication")).find { value =>
      value.getFileName.toString.endsWith(".json") && _entry_paths(value).contains(path)
    }.get
    (Json.parse(Files.readString(bundle, StandardCharsets.UTF_8)) \ "entries").as[JsArray].value.collectFirst {
      case value: JsObject if (value \ "path").as[String] == path => (value \ "metadata").as[JsObject]
    }.get
  }

  private def _tree(root: Path): Vector[(String, String)] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.toVector.filter { path =>
      Files.isRegularFile(path) && !(path.getParent == root && path.getFileName.toString == ".cozy-publication-registry.lock")
    }.sortBy(_.toString).map { path =>
      root.relativize(path).toString -> _sha256(path)
    }
    finally stream.close()
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(x => f"${x & 0xff}%02x").mkString

  private def _prepared(root: Path, name: String): CozyVideoPublisher.PublishVideoResult = {
    val packagedir = Files.createDirectories(root.resolve(s"prepared/$name.video"))
    val video = CozyVideoPublisher.ResolvedVideoPackage(
      packagedir, name, packagedir.resolve("video.yaml"), null, name, name, "1.0.0",
      packagedir.resolve("index.dox"), "index.dox", packagedir.resolve("script.json"), "script.json",
      "simple-java2d", "docker", name, "videos/legacy-decoy.mp4", Vector.empty, None
    )
    val plan = CozyPublicationCompiler.metadataPublication(
      name, None, packagedir, Vector(s"metadata/videos/$name/metadata.json" -> Json.obj("name" -> name))
    )
    CozyVideoPublisher.PublishVideoResult(video, packagedir, packagedir.resolve("project.json"), packagedir.resolve("artifact.mp4"),
      root.resolve(s"publication/$name.json"), Some(plan))
  }

  private def _write_bound_existing_bundle(root: Path, name: String): Unit = {
    val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
      CozyArticleMediaPublication.Variant(
        "ja",
        infographic = Some(ImageReference(new URI("/images/ja/example.png"), Some("image/png"), None)),
        video = Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI("/repository/video/legacy/0.9.0/legacy-0.9.0.mp4"))))
      ),
      CozyArticleMediaPublication.Variant("en", infographic = Some(ImageReference(new URI("/images/en/example.png"), Some("image/png"), None)))
    ))
    _write_existing_bundle(root, name, Vector(
      _bundle_entry(strict.entryPath, strict.metadata),
      _bundle_entry(_infographic_integrity("ja", "/images/ja/example.png").entryPath, _infographic_integrity("ja", "/images/ja/example.png").metadata),
      _bundle_entry(_video_integrity("ja", "/repository/video/legacy/0.9.0/legacy-0.9.0.mp4").entryPath, _video_integrity("ja", "/repository/video/legacy/0.9.0/legacy-0.9.0.mp4").metadata),
      _bundle_entry(_infographic_integrity("en", "/images/en/example.png").entryPath, _infographic_integrity("en", "/images/en/example.png").metadata),
      _bundle_entry("metadata/generic/legacy.json", Json.obj("retained" -> true, "bytes" -> "exact")),
      _bundle_entry(s"metadata/videos/$name/metadata.json", Json.obj("state" -> "stale"))
    ))
  }

  private def _write_unbound_existing_bundle(root: Path, name: String): Unit =
    _write_existing_bundle(root, name, Vector(
      _bundle_entry("metadata/generic/legacy.json", Json.obj("retained" -> true, "bytes" -> "exact")),
      _bundle_entry(s"metadata/videos/$name/metadata.json", Json.obj("state" -> "stale"))
    ))

  private def _write_existing_bundle(root: Path, name: String, entries: Vector[JsObject]): Unit = {
    val bundle = Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name, "title" -> "Legacy video bundle", "declarationExtension" -> Json.obj("retained" -> "yes")),
      "sourceRepository" -> "fixture",
      "sourcePath" -> ".",
      "sourceCommit" -> None,
      "extension" -> Json.obj("owner" -> name),
      "provenance" -> Json.obj("origin" -> "fixture"),
      "entries" -> JsArray(entries)
    )
    Files.write(root.resolve(s"$name.json"), (Json.prettyPrint(bundle) + "\n").getBytes(StandardCharsets.UTF_8))
  }

  private def _bundle_entry(path: String, metadata: JsObject): JsObject =
    Json.obj("path" -> path, "key" -> path.stripPrefix("metadata/").stripSuffix(".json"), "metadata" -> metadata)

  private def _video_integrity(locale: String, publicpath: String): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = "development-process/example",
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Video,
      artifact = CozyArticleMediaIntegrity.Artifact("legacy-video", "0.9.0"),
      publicPath = new URI(publicpath),
      repositoryPath = "video/legacy/0.9.0/legacy-0.9.0.mp4",
      mediaType = "video/mp4",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/legacy/0.9.0/manifest.json", "metadata/artifacts/repository/legacy.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))

  private def _infographic_integrity(locale: String, publicpath: String): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = "development-process/example",
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Infographic,
      artifact = CozyArticleMediaIntegrity.Artifact("example-infographic", "1.0.0"),
      publicPath = new URI(publicpath),
      repositoryPath = s"images/$locale/example.png",
      mediaType = "image/png",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.MediaPackage(s"src/main/media/$locale-example.yaml", s"example-infographic-$locale", "target/cozy-media/manifest.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Registered
    ))
}
