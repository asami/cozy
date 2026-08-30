package cozy.publication

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import cozy.media.CozyMedia
import cozy.scaffold.CozyScaffold
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

private object CozyArticleMediaWipCommandFixture {
  final case class Data(
    container: Path,
    project: Path,
    descriptor: Path,
    publication: Path,
    website: Path,
    sourceja: Path
  )
}

/*
 * @since   Aug. 12, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaWipCommandSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaWipCommand" should {
    "dispatch the additive WIP registration command" which {
      "keep dry-run read-only and install the same local video in non-dry mode" in {
        Given("one authorized smartdox-site media descriptor and two independent existing roots")
        _with_fixture("dispatch") { fixture =>
          val args = List(
            "media",
            "register-site-wip",
            fixture.descriptor.toString,
            "--publication",
            fixture.publication.toString,
            "--website",
            fixture.website.toString,
            "--target=video-ja"
          )
          val before = _tree(fixture.container)

          When("the public media dispatcher runs dry-run and then the identical registration")
          val dryrun = _capture(CozyMedia.execute(args :+ "--dry-run"))
          val afterdryrun = _tree(fixture.container)
          val registered = _capture(CozyMedia.execute(args))

          Then("the deterministic plan is identical while only non-dry installs and registers")
          dryrun._1 shouldBe true
          registered._1 shouldBe true
          dryrun._2.replace("mode: dry-run", "mode: registered") shouldBe registered._2
          afterdryrun shouldBe before
          registered._2 should include("Cozy Media Register Site WIP")
          registered._2 should include("articleIdentity: development-process/example")
          registered._2 should include("video-ja: locale=ja, role=video")
          _sha256(fixture.website.resolve("ja/development-process/videos/example.mp4")) shouldBe _sha256(fixture.sourceja)
          CozyArticleMediaRegistry.load(fixture.publication).entries.map(_.path) should contain allOf (
            "metadata/article-media/development-process/example.json",
            "metadata/article-media-integrity/development-process/example/ja/video.json"
          )
        }
      }

      "register PDF roles as strict reuse without changing the website tree or creating PDF integrity" in {
        Given("a PDF-only authorized descriptor with current receipt and review-state evidence")
        _with_pdf_fixture("pdf-reuse") { fixture =>
          val args = List(
            "media",
            "register-site-wip",
            fixture.descriptor.toString,
            "--publication",
            fixture.publication.toString,
            "--website",
            fixture.website.toString
          )
          val beforewebsite = _tree(fixture.website).filterNot(_._1 == ".cozy-article-media-wip.lock")

          When("the WIP command registers every selected PDF")
          val dryrun = _capture(CozyMedia.execute(args :+ "--dry-run"))
          val registered = _capture(CozyMedia.execute(args))

          Then("dry-run and registration report PDF reuse with no website output mutation")
          dryrun._1 shouldBe true
          registered._1 shouldBe true
          dryrun._2.replace("mode: dry-run", "mode: registered") shouldBe registered._2
          registered._2 should include("article-pdf-ja: locale=ja, role=article_pdf, reuse")
          registered._2 should include("summary-pdf-en: locale=en, role=summary_slides_pdf, reuse")
          _tree(fixture.website).filterNot(_._1 == ".cozy-article-media-wip.lock") shouldBe beforewebsite
          CozyArticleMediaRegistry.load(fixture.publication).entries.map(_.path) should contain (
            "metadata/article-media/development-process/example.json"
          )
          val snapshot = CozyArticleMediaRegistry.load(fixture.publication)
          snapshot.entries.map(_.path).exists(_.contains("article-media-integrity")) shouldBe false
          val strict = snapshot.entries.find(_.path == "metadata/article-media/development-process/example.json").get.metadata
          val variants = (strict \ "variants").as[JsObject]
          ((variants \ "ja" \ "article_pdf").as[JsObject]).fields.map(_._1) shouldBe Vector("public_path", "media_type", "label")
          ((variants \ "en" \ "article_pdf").as[JsObject]).fields.map(_._1) shouldBe Vector("public_path", "media_type")
          ((variants \ "ja" \ "summary_slides_pdf").as[JsObject]).fields.map(_._1) shouldBe Vector("public_path", "media_type", "label")
          ((variants \ "en" \ "summary_slides_pdf").as[JsObject]).fields.map(_._1) shouldBe Vector("public_path", "media_type")
          Json.stringify(strict) should not include "sha256"
          Json.stringify(strict) should not include "receipt"
          Json.stringify(strict) should not include "renderer"
        }
      }
    }

    "parse the frozen strict grammar" which {
      "accept separated and equals forms and reject every ambiguous option edge" in {
        Given("one descriptor token and two direct roots")
        _with_fixture("grammar") { fixture =>
          val descriptor = fixture.descriptor.toString
          val publication = fixture.publication.toString
          val website = fixture.website.toString

          When("the parser receives accepted and rejected forms")
          val separated = CozyArticleMediaWipCommand.Config.create(List(
            descriptor, "--publication", publication, "--website", website, "--target", "video-ja", "--dry-run"
          ))
          val equals = CozyArticleMediaWipCommand.Config.create(List(
            descriptor, "--publication=" + publication, "--website=" + website, "--target=-video-ja"
          ))
          val missingpublication = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--website", website)))
          val missingwebsite = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--publication", publication)))
          val extra = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "extra", "--publication", publication, "--website", website)))
          val unknown = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--publication", publication, "--website", website, "--unknown")))
          val duplicatepublication = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--publication", publication, "--publication=" + publication, "--website", website)))
          val duplicatewebsite = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--publication", publication, "--website", website, "--website=" + website)))
          val duplicatetarget = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--publication", publication, "--website", website, "--target=x", "--target=y")))
          val duplicatedryrun = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--publication", publication, "--website", website, "--dry-run", "--dry-run")))
          val profile = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--publication", publication, "--website", website, "--profile=x")))
          val separatedleadingdash = _failure(CozyArticleMediaWipCommand.Config.create(List(descriptor, "--publication", publication, "--website", website, "--target", "-video-ja")))

          Then("only exact unambiguous syntax is admitted")
          separated shouldBe CozyArticleMediaWipCommand.Config(fixture.descriptor, fixture.publication, fixture.website, Some("video-ja"), dryRun = true)
          equals.target shouldBe Some("-video-ja")
          missingpublication.getMessage should include("Missing --publication")
          missingwebsite.getMessage should include("Missing --website")
          extra.getMessage should include("exactly one media-file")
          unknown.getMessage should include("Unknown media register-site-wip option")
          duplicatepublication.getMessage should include("Duplicate --publication")
          duplicatewebsite.getMessage should include("Duplicate --website")
          duplicatetarget.getMessage should include("Duplicate --target")
          duplicatedryrun.getMessage should include("Duplicate --dry-run")
          profile.getMessage should include("--profile is not supported")
          separatedleadingdash.getMessage should include("Missing value for --target")
        }
      }
    }

    "advertise only the public WIP grammar" which {
      "show both independent roots without internal evidence names" in {
        Given("the public Cozy help text")

        When("the WIP registration command is selected")
        val help = CozyScaffold.helpText.split("\\n").dropWhile(_ !=
          "  media register-site-wip <media-file> --publication <publication-root> --website <website-root> [--target <resource-id>] [--dry-run]"
        ).take(2).mkString("\n")

        Then("the exact syntax and concise purpose are visible")
        help should include("media register-site-wip <media-file> --publication <publication-root> --website <website-root>")
        help should include("Install validated local WIP video")
        help should not include "descriptorEvidence"
        help should not include "sha256"
      }
    }
  }

  private def _capture(value: => Boolean): (Boolean, String) = {
    val bytes = new ByteArrayOutputStream()
    val stream = new PrintStream(bytes, true, StandardCharsets.UTF_8.name())
    try {
      val result = Console.withOut(stream)(value)
      result -> bytes.toString(StandardCharsets.UTF_8.name()).trim
    } finally stream.close()
  }

  private def _failure(value: => Any): RuntimeException =
    intercept[RuntimeException](value)

  private def _with_fixture[A](name: String)(f: CozyArticleMediaWipCommandFixture.Data => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-wip-command")
    Files.createDirectories(workroot)
    val container = Files.createTempDirectory(workroot, name + "-")
    val project = Files.createDirectory(container.resolve("project"))
    val descriptor = project.resolve("media.yaml")
    val publication = Files.createDirectory(container.resolve("publication"))
    val website = project.resolve("website")
    val sourceja = project.resolve("output/video-ja.mp4")
    try {
      _write(project.resolve("conf/cozy/config.yaml"), _project_config)
      _write(descriptor, _media_yaml)
      _write(sourceja, "ja source")
      _write(project.resolve("video/ja/production.json"), _production(_sha256(sourceja)))
      Files.createDirectories(project.resolve("profile"))
      Files.createDirectories(website.resolve("ja/development-process/videos"))
      f(CozyArticleMediaWipCommandFixture.Data(container, project, descriptor, publication, website, sourceja))
    } finally _delete(container)
  }

  private def _with_pdf_fixture[A](name: String)(f: CozyArticleMediaWipCommandFixture.Data => A): A =
    _with_fixture(name) { fixture =>
      _write(fixture.project.resolve("knowledge/example.dox"), "Example article authority")
      _write(fixture.descriptor, _pdf_media_yaml)
      _write(fixture.project.resolve("output/article-ja.pdf"), "%PDF-1.7\nArticle PDF ja")
      _write(fixture.project.resolve("output/article-en.pdf"), "%PDF-1.7\nArticle PDF en")
      _write(fixture.project.resolve("output/summary-ja.pdf"), "%PDF-1.7\nSummary PDF ja")
      _write(fixture.project.resolve("output/summary-en.pdf"), "%PDF-1.7\nSummary PDF en")
      CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))
      f(fixture)
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

  private def _pdf_media_yaml: String =
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
      |  - id: article-pdf-ja
      |    kind: document
      |    language: ja
      |    source: output/article-ja.pdf
      |    build: prebuilt
      |    articleMedia:
      |      role: article_pdf
      |      publicPath: /ja/development-process/pdf/example/article.pdf
      |      mediaType: application/pdf
      |      label: Article PDF ja
      |  - id: article-pdf-en
      |    kind: document
      |    language: en
      |    source: output/article-en.pdf
      |    build: prebuilt
      |    articleMedia:
      |      role: article_pdf
      |      publicPath: /en/development-process/pdf/example/article.pdf
      |      mediaType: application/pdf
      |  - id: summary-pdf-ja
      |    kind: document
      |    language: ja
      |    source: output/summary-ja.pdf
      |    build: prebuilt
      |    articleMedia:
      |      role: summary_slides_pdf
      |      publicPath: /ja/development-process/pdf/example/summary.pdf
      |      mediaType: application/pdf
      |      label: Summary PDF ja
      |  - id: summary-pdf-en
      |    kind: document
      |    language: en
      |    source: output/summary-en.pdf
      |    build: prebuilt
      |    articleMedia:
      |      role: summary_slides_pdf
      |      publicPath: /en/development-process/pdf/example/summary.pdf
      |      mediaType: application/pdf
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

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(Files.readAllBytes(path))
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
