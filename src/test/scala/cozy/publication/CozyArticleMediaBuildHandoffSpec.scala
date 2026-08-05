package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, Json}
import cozy.bok.CozyBok

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaBuildHandoffSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy BoK article-media build handoff" should {
    "canonicalize strategy names before project construction" which {
      "retain aliases in the effective BuildConfig and reject unsupported names before filesystem work" in {
        Given("a configured project and a nonexistent unsupported-project path")
        _with_fixture { fixture =>
          val unsupported = fixture.root.resolve("unsupported-project")

          When("BuildConfig receives the wip alias and an unsupported strategy")
          val alias = CozyBok.BuildConfig.create(List(fixture.project.toString, "--strategy", "wip"))
          val error = intercept[IllegalArgumentException](
            CozyBok.BuildConfig.create(List(unsupported.toString, "--strategy", "unsupported"))
          )

          Then("the effective strategy is canonical and unsupported input has no project effect")
          alias.strategy shouldBe "work-in-progress"
          error.getMessage should include("Unsupported article-media build strategy")
          Files.exists(unsupported, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "lease one immutable effective publication to every ordinary-build consumer" which {
      "pass the same digest-addressed publication and configured repository to Dox consumers" in {
        Given("a media-free configured publication, an unrelated invalid media descriptor, and stale target content")
        _with_fixture { fixture =>
          _write_media_free_bundle(fixture.publication, revision = 1)
          val descriptor = fixture.project.resolve("src/main/media/unrelated/media.yaml")
          val descriptorbytes = "not: a cozy media descriptor\n".getBytes(StandardCharsets.UTF_8)
          Files.createDirectories(descriptor.getParent)
          Files.write(descriptor, descriptorbytes)
          Files.createDirectories(fixture.project.resolve("target/unrelated"))
          Files.writeString(fixture.project.resolve("target/unrelated/stale.txt"), "stale", StandardCharsets.UTF_8)
          val config = CozyBok.BuildConfig.create(List(fixture.project.toString, "--strategy", "preview", "--no-bib-service"))
          val runner = new HandoffRunner

          When("the actual ordinary CozyBok build invokes its recording consumers")
          CozyBok.build(config, runner)

          Then("all Dox commands observe one active immutable snapshot and the configured repository")
          val publications = runner.doxCommands.map(_option(_, "-publication"))
          publications.distinct.size shouldBe 1
          publications.head should include("target/cozy-bok/article-media/production-preview/snapshots/")
          runner.doxCommands.foreach { command =>
            _option(command, "-publication.repository") shouldBe fixture.repository.toString
          }
          runner.leaseObservations should contain only (true)
          val commandtext = runner.commands.map(_.mkString(" ").toLowerCase(Locale.ROOT)).mkString("\n")
          Vector("renderer", "transcoder", "ffmpeg", "remotion", "publish-media").foreach { tool =>
            commandtext should not include tool
          }
          Files.readAllBytes(descriptor).toVector shouldBe descriptorbytes.toVector
          Files.exists(fixture.project.resolve("target/unrelated/stale.txt"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          CozyArticleMediaBuildContext.activeLeaseCount(Paths.get(publications.head)) shouldBe 0
        }
      }

      "reuse unchanged snapshots across target cleanup and retain prior digests after a registry change" in {
        Given("a deterministic media-free publication and a recording ordinary-build runner")
        _with_fixture { fixture =>
          _write_media_free_bundle(fixture.publication, revision = 1)
          val config = CozyBok.BuildConfig.create(List(fixture.project.toString, "--strategy", "draft", "--no-bib-service"))
          val first = new HandoffRunner
          CozyBok.build(config, first)
          val firstpath = Paths.get(_option(first.doxCommands.head, "-publication"))
          val firstbytes = Files.readAllBytes(firstpath.resolve("publication.json"))
          val firstdirectoryattributes = Files.readAttributes(firstpath, classOf[BasicFileAttributes])
          val firstfileattributes = Files.readAttributes(firstpath.resolve("publication.json"), classOf[BasicFileAttributes])

          When("the same registry is built again after stale ordinary target content is present")
          Files.writeString(fixture.project.resolve("target/stale.txt"), "stale", StandardCharsets.UTF_8)
          val second = new HandoffRunner
          CozyBok.build(config, second)
          val secondpath = Paths.get(_option(second.doxCommands.head, "-publication"))

          Then("the exact snapshot identity and bytes survive the cleanup")
          secondpath shouldBe firstpath
          Files.readAllBytes(secondpath.resolve("publication.json")).toVector shouldBe firstbytes.toVector
          val seconddirectoryattributes = Files.readAttributes(secondpath, classOf[BasicFileAttributes])
          val secondfileattributes = Files.readAttributes(secondpath.resolve("publication.json"), classOf[BasicFileAttributes])
          seconddirectoryattributes.fileKey shouldBe firstdirectoryattributes.fileKey
          seconddirectoryattributes.lastModifiedTime shouldBe firstdirectoryattributes.lastModifiedTime
          secondfileattributes.fileKey shouldBe firstfileattributes.fileKey
          secondfileattributes.lastModifiedTime shouldBe firstfileattributes.lastModifiedTime
          Files.exists(fixture.project.resolve("target/stale.txt"), LinkOption.NOFOLLOW_LINKS) shouldBe false

          When("the configured registry content changes")
          _write_media_free_bundle(fixture.publication, revision = 2)
          val changed = new HandoffRunner
          CozyBok.build(config, changed)
          val changedpath = Paths.get(_option(changed.doxCommands.head, "-publication"))

          Then("a different digest is installed while the former immutable snapshot remains byte-identical")
          changedpath should not be firstpath
          Files.exists(firstpath, LinkOption.NOFOLLOW_LINKS) shouldBe true
          Files.readAllBytes(firstpath.resolve("publication.json")).toVector shouldBe firstbytes.toVector
        }
      }

      "release the handoff lease after a consumer failure" in {
        Given("a media-free configured publication and a runner that fails at its first Dox consumer")
        _with_fixture { fixture =>
          _write_media_free_bundle(fixture.publication, revision = 1)
          val config = CozyBok.BuildConfig.create(List(fixture.project.toString, "--strategy", "draft", "--no-bib-service"))
          val runner = new HandoffRunner(failfirstdox = true)

          When("the ordinary build propagates that consumer failure")
          val error = intercept[IllegalStateException](CozyBok.build(config, runner))

          Then("the digest-addressed snapshot remains installed but its lease is released")
          error.getMessage shouldBe "handoff consumer failure"
          runner.doxCommands.size shouldBe 1
          CozyArticleMediaBuildContext.activeLeaseCount(Paths.get(_option(runner.doxCommands.head, "-publication"))) shouldBe 0
        }
      }

      "include the same handoff options in production site marking" in {
        Given("a media-free production publication")
        _with_fixture { fixture =>
          _write_media_free_bundle(fixture.publication, revision = 1)
          val config = CozyBok.BuildConfig.create(List(fixture.project.toString, "--strategy", "production", "--no-bib-service"))
          val runner = new HandoffRunner

          When("the actual production build reaches site marking")
          CozyBok.build(config, runner)

          Then("every Dox consumer, including site-mark, receives identical context paths")
          val publications = runner.doxCommands.map(_option(_, "-publication"))
          publications.distinct.size shouldBe 1
          runner.doxCommands.map(_option(_, "-publication.repository")).distinct shouldBe Vector(fixture.repository.toString)
          runner.doxCommands.exists(_.take(2) == Vector("dox", "site-mark")) shouldBe true
          runner.leaseObservations should contain only (true)
        }
      }
    }

    "fail production policy before an external build consumer" which {
      "leave the runner untouched for unavailable registered site-hosted media" in {
        Given("a production registry with registered rather than published path-bearing video evidence")
        _with_fixture { fixture =>
          _write_registered_video_bundle(fixture.publication)
          Files.createDirectories(fixture.repository)
          val config = CozyBok.BuildConfig.create(List(fixture.project.toString, "--strategy", "production", "--no-bib-service"))
          val runner = new HandoffRunner

          When("the actual ordinary production build starts")
          val error = intercept[IllegalArgumentException](CozyBok.build(config, runner))

          Then("policy rejects the effective context before any runner command")
          error.getMessage should include("requires published Cozy integrity")
          runner.commands shouldBe empty
        }
      }
    }
  }

  private final case class Fixture(root: Path, project: Path, publication: Path, repository: Path)

  private final class HandoffRunner(failfirstdox: Boolean = false) extends CozyBok.Runner {
    var commands = Vector.empty[Vector[String]]
    var leaseObservations = Vector.empty[Boolean]
    private var _publication: Option[Path] = None

    def doxCommands: Vector[Vector[String]] = commands.filter(_.take(1) == Vector("dox"))

    def run(command: Vector[String], cwd: Path): Unit = {
      commands :+= command
      if (command.take(1) == Vector("dox")) {
        val publication = Paths.get(_option(command, "-publication"))
        _publication = Some(publication)
        if (failfirstdox && doxCommands.size == 1)
          throw new IllegalStateException("handoff consumer failure")
      }
      leaseObservations :+= _publication.exists { publication =>
        Files.isDirectory(publication, LinkOption.NOFOLLOW_LINKS) &&
          CozyArticleMediaBuildContext.activeLeaseCount(publication) > 0
      }
    }
  }

  private def _with_fixture[A](f: Fixture => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize.resolve("target/cozy-article-media-build-handoff-spec")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "fixture-")
    val project = Files.createDirectory(root.resolve("project"))
    val fixture = Fixture(root, project, project.resolve("src/main/publication"), project.resolve("repository"))
    _write_project_config(fixture)
    try f(fixture)
    finally _delete(root)
  }

  private def _write_project_config(fixture: Fixture): Unit = {
    Files.createDirectories(fixture.project.resolve("conf/cozy"))
    Files.createDirectories(fixture.project.resolve("src/main/doxsite"))
    Files.createDirectories(fixture.repository)
    Files.writeString(
      fixture.project.resolve("conf/cozy/config.yaml"),
      """bok:
        |  publication: src/main/publication
        |  repository: repository
        |  rdf:
        |    merge-publication-artifacts: true
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
  }

  private def _write_media_free_bundle(root: Path, revision: Int): Unit =
    _write_bundle(root, Vector(_entry("metadata/catalog/ordinary.json", Json.obj("revision" -> revision))))

  private def _write_registered_video_bundle(root: Path): Unit = {
    val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
      CozyArticleMediaPublication.Variant("ja", video = Some(
        VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI("/repository/video/example.mp4")))
      ))
    ))
    val integrity = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = "development-process/example",
      locale = "ja",
      role = CozyArticleMediaIntegrity.Role.Video,
      artifact = CozyArticleMediaIntegrity.Artifact("example", "1.0.0"),
      publicPath = new URI("/repository/video/example.mp4"),
      repositoryPath = "video/example.mp4",
      mediaType = "video/mp4",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example/1.0.0/manifest.json", "metadata/artifacts/repository/example.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Registered
    ))
    _write_bundle(root, Vector(_entry(strict.entryPath, strict.metadata), _entry(integrity.entryPath, integrity.metadata)))
  }

  private def _write_bundle(root: Path, entries: Vector[JsObject]): Unit = {
    Files.createDirectories(root)
    Files.writeString(
      root.resolve("publication.json"),
      Json.stringify(Json.obj(
        "type" -> "publication-bundle",
        "publication" -> Json.obj("name" -> "publication"),
        "entries" -> entries
      )),
      StandardCharsets.UTF_8
    )
  }

  private def _entry(path: String, metadata: JsObject): JsObject =
    Json.obj("path" -> path, "key" -> path.stripPrefix("metadata/").stripSuffix(".json"), "metadata" -> metadata)

  private def _option(command: Vector[String], name: String): String = {
    val index = command.indexOf(name)
    if (index < 0 || index + 1 >= command.size)
      throw new IllegalArgumentException(s"Missing option: $name")
    command(index + 1)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
