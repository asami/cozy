package cozy.publication

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsObject, Json}

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
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaWipTransactionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaWipTransaction" should {
    "keep dry-run read-only" which {
      "return the exact frozen plan without locks, destinations, or registry bytes" in {
        Given("an external publication root, a project-local website root, and one valid JA video")
        _with_fixture("dry-run") { fixture =>
          val plan = _plan(fixture)
          val before = _tree(fixture.container)

          When("the WIP transaction runs in dry-run mode")
          val result = CozyArticleMediaWipTransaction.execute(plan, dryRun = true)

          Then("the deterministic plan is returned and no filesystem byte or lock changes")
          result.plan shouldBe plan
          result.installedPaths shouldBe empty
          result.merge shouldBe None
          _tree(fixture.container) shouldBe before
          Files.exists(fixture.publication.resolve(".cozy-article-media-wip.lock"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(fixture.website.resolve(".cozy-article-media-wip.lock"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "commit registry-last installation" which {
      "atomically install a fresh local MP4 and create only its canonical WIP registry pair" in {
        Given("one fresh JA WIP candidate and lock-order trace hooks")
        _with_fixture("fresh") { fixture =>
          val plan = _plan(fixture)
          val events = scala.collection.mutable.ArrayBuffer.empty[(String, Path)]

          When("the non-dry WIP transaction succeeds")
          val result = CozyArticleMediaWipTransaction.execute(plan, dryRun = false,
            CozyArticleMediaWipTransaction.Hooks(lockevent = (event, path) => events += event -> path))

          Then("the MP4 digest, strict record, integrity record, registry-last result, and reverse lock release are exact")
          val destination = fixture.website.resolve("ja/development-process/videos/example.mp4")
          result.installedPaths shouldBe Vector(destination)
          _sha256(destination) shouldBe _sha256(fixture.sourceja)
          result.merge.get.entryPaths should contain("metadata/article-media/development-process/example.json")
          result.merge.get.entryPaths should contain("metadata/article-media-integrity/development-process/example/ja/video.json")
          val snapshot = CozyArticleMediaRegistry.load(fixture.publication)
          snapshot.entries.map(_.path) should contain allOf (
            "metadata/article-media/development-process/example.json",
            "metadata/article-media-integrity/development-process/example/ja/video.json"
          )
          events.take(2).map(_._1) shouldBe Vector("acquire", "acquire")
          events.takeRight(2).map(_._1) shouldBe Vector("release", "release")
          events.take(2).map(_._2.toString) shouldBe events.take(2).map(_._2.toString).sorted
          events.takeRight(2).map(_._2) shouldBe events.take(2).map(_._2).reverse
          _temporary_names(fixture.website) shouldBe empty
        }
      }

      "admit the exact installed repeat without changing its public identity" in {
        Given("one completed WIP transaction and a newly planned exact repeat")
        _with_fixture("repeat") { fixture =>
          CozyArticleMediaWipTransaction.execute(_plan(fixture), dryRun = false)
          val repeat = _plan(fixture)
          val before = Files.readAllBytes(fixture.publication.resolve("article-media.json")).toVector

          When("the exact repeat is committed")
          val result = CozyArticleMediaWipTransaction.execute(repeat, dryRun = false)

          Then("the destination remains digest-identical and the registry is canonical")
          result.registry.videoStates(("development-process/example", "ja", "video")) shouldBe CozyArticleMediaRegistry.WipVideoState.ExactRepeat
          _sha256(fixture.website.resolve("ja/development-process/videos/example.mp4")) shouldBe _sha256(fixture.sourceja)
          Files.readAllBytes(fixture.publication.resolve("article-media.json")).toVector shouldBe before
          _temporary_names(fixture.website) shouldBe empty
        }
      }
    }

    "rollback every mutation" which {
      "remove a fresh destination when execution fails after install and before registry replacement" in {
        Given("a fresh plan and a hook that fails after the first atomic install")
        _with_fixture("install-failure") { fixture =>
          val plan = _plan(fixture)
          val before = _tree(fixture.publication)

          When("the post-install hook fails")
          val error = intercept[IllegalStateException](CozyArticleMediaWipTransaction.execute(plan, dryRun = false,
            CozyArticleMediaWipTransaction.Hooks(afterinstall = _ => throw new IllegalStateException("install failure"))))

          Then("the registry stays byte-exact and the fresh destination and all temporary evidence are removed")
          error.getMessage shouldBe "install failure"
          _tree(fixture.publication).filterNot { case (name, _) =>
            name.endsWith(".cozy-article-media-wip.lock") || name.endsWith(".cozy-publication-registry.lock")
          } shouldBe before
          Files.exists(fixture.website.resolve("ja/development-process/videos/example.mp4"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          _temporary_names(fixture.website) shouldBe empty
        }
      }

      "restore the registry before removing a fresh destination when validation fails after replacement" in {
        Given("a fresh plan and exact original empty registry bytes")
        _with_fixture("registry-failure") { fixture =>
          val plan = _plan(fixture)

          When("the post-registry hook fails")
          val error = intercept[IllegalStateException](CozyArticleMediaWipTransaction.execute(plan, dryRun = false,
            CozyArticleMediaWipTransaction.Hooks(afterregistryreplace = () => throw new IllegalStateException("registry failure"))))

          Then("the newly-created canonical bundle and installed destination are both absent")
          error.getMessage shouldBe "registry failure"
          Files.exists(fixture.publication.resolve("article-media.json"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(fixture.website.resolve("ja/development-process/videos/example.mp4"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          _temporary_names(fixture.website) shouldBe empty
        }
      }
    }

    "reject unsafe root and lock evidence" which {
      "reject equal roots and an invalid pre-existing WIP lock before destination or registry mutation" in {
        Given("separate fixtures for root overlap and an invalid lock artifact")
        _with_fixture("unsafe") { fixture =>
          val safeplan = _plan(fixture)
          val equal = safeplan.copy(publicationRoot = safeplan.websiteRoot)

          When("the root predicate and lock artifact are checked")
          val rooterror = intercept[IllegalArgumentException](CozyArticleMediaWipTransaction.execute(equal, dryRun = false))
          Files.createDirectory(fixture.website.resolve(".cozy-article-media-wip.lock"))
          val lockerror = intercept[IllegalArgumentException](CozyArticleMediaWipTransaction.execute(safeplan, dryRun = false))

          Then("both fail before a destination or registry bundle exists")
          rooterror.getMessage should include("roots must be independent")
          lockerror.getMessage should include("lock must be a direct regular file")
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
      _write(productionja, _production(_sha256(sourceja)))
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

  private def _production(sha256: String): String =
    s"""{
       |  "category": "development-process",
       |  "article": "example",
       |  "language": "ja",
       |  "render": {
       |    "status": "completed",
       |    "sha256": "$sha256",
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

  private def _temporary_names(root: Path): Vector[String] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.toVector.map(_.getFileName.toString).filter(name => name.contains(".cozy-wip.tmp") || name.contains(".cozy-wip.backup")).sorted
    finally stream.close()
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(Files.readAllBytes(path))
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(value => (-value.getNameCount, value.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
