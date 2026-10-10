package cozy.publication

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

private object CozyArticleMediaWipTransactionFixture {
  final case class Data(
    container: Path,
    project: Path,
    descriptor: Path,
    config: Path,
    publication: Path,
    website: Path,
    sourceja: Path,
    productionja: Path
  )
}

/*
 * @since   Aug. 12, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaWipTransactionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaWipTransaction" should {
    "keep dry-run read-only" which {
      "return the initial plan without installing destinations or writing the registry" in {
        Given("an external publication root, a project-local website root, and one valid JA video")
        _with_fixture("dry-run") { fixture =>
          val plan = _plan(fixture)
          val before = _tree(fixture.container)

          When("the WIP transaction runs in dry-run mode")
          val result = CozyArticleMediaWipTransaction.execute(plan, dryRun = true)

          Then("the initial plan is returned with no installed paths, merge, or filesystem changes")
          result.plan shouldBe plan
          result.installedPaths shouldBe empty
          result.merge shouldBe None
          _tree(fixture.container) shouldBe before
        }
      }
    }

    "commit registry-last installation" which {
      "copy a fresh local MP4 directly and create its canonical WIP registry pair last" in {
        Given("one initially admitted fresh JA WIP video candidate")
        _with_fixture("fresh") { fixture =>
          val plan = _plan(fixture)

          When("the non-dry WIP transaction succeeds")
          val result = CozyArticleMediaWipTransaction.execute(plan, dryRun = false)

          Then("the copied MP4, canonical strict and integrity records, and registry-last result are returned")
          val destination = fixture.website.resolve("ja/development-process/videos/example.mp4")
          result.installedPaths shouldBe Vector(destination)
          Files.readAllBytes(destination).toVector shouldBe Files.readAllBytes(fixture.sourceja).toVector
          result.merge.get.entryPaths should contain("metadata/article-media/development-process/example.json")
          result.merge.get.entryPaths should contain("metadata/article-media-integrity/development-process/example/ja/video.json")
          val snapshot = CozyArticleMediaRegistry.load(fixture.publication)
          snapshot.entries.map(_.path) should contain allOf (
            "metadata/article-media/development-process/example.json",
            "metadata/article-media-integrity/development-process/example/ja/video.json"
          )
          val integrity = snapshot.entries.find(_.path ==
            "metadata/article-media-integrity/development-process/example/ja/video.json").get.metadata
          (integrity \ "provenance" \ "kind").as[String] shouldBe "wip-site-video"
        }
      }

      "refresh an existing WIP video without changing its public identity" in {
        Given("one completed WIP transaction and a newly selected source for the same WIP destination")
        _with_fixture("repeat") { fixture =>
          CozyArticleMediaWipTransaction.execute(_plan(fixture), dryRun = false)
          val repeat = _plan(fixture)
          val before = Files.readAllBytes(fixture.publication.resolve("article-media.json")).toVector

          When("the selected WIP source is copied again")
          val result = CozyArticleMediaWipTransaction.execute(repeat, dryRun = false)

          Then("the destination contains the selected source bytes and the registry is canonical")
          result.installedPaths shouldBe Vector(fixture.website.resolve("ja/development-process/videos/example.mp4"))
          Files.readAllBytes(fixture.website.resolve("ja/development-process/videos/example.mp4")).toVector shouldBe Files.readAllBytes(fixture.sourceja).toVector
          Files.readAllBytes(fixture.publication.resolve("article-media.json")).toVector shouldBe before
        }
      }
    }

    "report ordinary execution failures" which {
      "report an afterinstall failure before registry replacement while leaving the copied destination" in {
        Given("a fresh plan and a hook that fails after the first direct copy")
        _with_fixture("install-failure") { fixture =>
          val plan = _plan(fixture)
          val application = new IllegalStateException("install failure")

          When("the afterinstall callback fails")
          val error = intercept[IllegalStateException](CozyArticleMediaWipTransaction.execute(plan, dryRun = false,
            CozyArticleMediaWipTransaction.Hooks(afterinstall = _ => throw application)))

          Then("the same application exception is reported, no registry bundle is written, and the copied file may remain")
          error shouldBe application
          error.getMessage shouldBe "install failure"
          Files.exists(fixture.publication.resolve("article-media.json"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.isRegularFile(fixture.website.resolve("ja/development-process/videos/example.mp4"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        }
      }
    }

    "reject unsafe initial roots" which {
      "reject equal roots before destination or registry mutation" in {
        Given("an initial plan whose publication and website roots overlap")
        _with_fixture("unsafe") { fixture =>
          val safeplan = _plan(fixture)
          val equal = safeplan.copy(publicationRoot = safeplan.websiteRoot)

          When("the initial root overlap predicate is checked")
          val rooterror = intercept[IllegalArgumentException](CozyArticleMediaWipTransaction.execute(equal, dryRun = false))

          Then("root overlap fails before a destination or registry bundle exists")
          rooterror.getMessage should include("roots must be independent")
          Files.exists(fixture.website.resolve("ja/development-process/videos/example.mp4"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(fixture.publication.resolve("article-media.json"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }
  }

  private def _plan(fixture: CozyArticleMediaWipTransactionFixture.Data): CozyArticleMediaWipBinding.Plan =
    CozyArticleMediaWipBinding.plan(CozyArticleMediaWipBinding.Config(
      fixture.descriptor,
      fixture.publication,
      fixture.website,
      Some("video-ja")
    ))

  private def _with_fixture[A](name: String)(f: CozyArticleMediaWipTransactionFixture.Data => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-wip-transaction")
    Files.createDirectories(workroot)
    val container = Files.createTempDirectory(workroot, name + "-")
    val project = Files.createDirectory(container.resolve("project"))
    val descriptor = project.resolve("media.yaml")
    val config = project.resolve("conf/cozy/config.yaml")
    val publication = Files.createDirectory(container.resolve("publication"))
    val website = project.resolve("website")
    val sourceja = project.resolve("output/video-ja.mp4")
    val productionja = project.resolve("video/ja/production.json")
    try {
      _write(config, _project_config)
      _write(descriptor, _media_yaml)
      _write(sourceja, "ja source")
      _write(productionja, _production)
      Files.createDirectories(project.resolve("profile"))
      Files.createDirectories(website.resolve("ja/development-process/videos"))
      f(CozyArticleMediaWipTransactionFixture.Data(container, project, descriptor, config, publication, website, sourceja, productionja))
    } finally _delete(container)
  }

  private def _project_config: String =
    """project:
      |  id: simplemodeling-org
      |  kind: smartdox-site
      |media:
      |  publication-profiles:
      |    site:
      |      root: profile
      |      site-kind: smartdox
      |""".stripMargin

  private def _media_yaml: String =
    """schema: cozy.media.v1
      |knowledge:
      |  id: media-package/example
      |  source: knowledge/example.dox
      |profiles:
      |  site:
      |    root: profile
      |articleMedia:
      |  articleIdentity: development-process/example
      |  publicationProfile: site
      |resources:
      |  - id: video-ja
      |    kind: video
      |    language: ja
      |    source: input/video-ja.mp4
      |    output: output/video-ja.mp4
      |    build: prebuilt
      |    articleMedia:
      |      role: video
      |      production: video/ja/production.json
      |""".stripMargin

  private def _production: String =
    """{
       |  "category": "development-process",
       |  "article": "example",
       |  "language": "ja",
       |  "render": {
       |    "status": "completed",
       |    "qa": {"status": "technical-and-visual-qa-passed"}
       |  }
       |}
       |""".stripMargin

  private def _tree(root: Path): Vector[(String, Vector[Byte])] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.toVector.filter(Files.isRegularFile(_, LinkOption.NOFOLLOW_LINKS)).map { path =>
      root.relativize(path).toString -> Files.readAllBytes(path).toVector
    }.sortBy(_._1)
    finally stream.close()
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(value => (-value.getNameCount, value.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
