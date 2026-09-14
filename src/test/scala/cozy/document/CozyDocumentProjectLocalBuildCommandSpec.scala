package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectLocalBuildCommandSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project local-build command" should {
    "select the default and render each frozen target without loading a descriptor" in {
      _with_temp_dir("cozy-document-project-local-build-routes") { root =>
        Given("a direct .dox package with the closed target definition and no descriptor")
        val fixture = _fixture(root)

        When("the default, reader, and SmartDox article targets are built")
        val defaultreport = _execute(List("document-project", "build", fixture.project.toString))
        val readerreport = _execute(List("document-project", "build", fixture.project.toString, "--target", "document-reader-html"))
        val articlereport = _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))

        Then("each route writes only its declared primary HTML and reports the selected target inputs and outcome")
        defaultreport should include("target: document-structure-html")
        defaultreport should include("  - build/document-project-targets.yaml")
        defaultreport should include("  - content/core.yaml")
        defaultreport should include("  - content/ja/document.yaml")
        defaultreport should include("  - content/ja/confirmation-vocabulary.yaml")
        defaultreport should include("output: target/document-project/local-build/document-structure-html/index.html")
        defaultreport should include("outcome: built")
        readerreport should include("target: document-reader-html")
        readerreport should include("outcome: built")
        articlereport should include("target: smartdox-article-html")
        articlereport should include("  - index.dox")
        articlereport should include("outcome: built")
        _html(fixture.project, "document-structure-html") should include("<h1>文書確認</h1>")
        _html(fixture.project, "document-reader-html") should include("id=\"document-reader\"")
        _html(fixture.project, "smartdox-article-html").toLowerCase should include("<article")
        Files.exists(fixture.project.resolve("document-project.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "preserve the closed source boundaries of an Article-9-shaped package" which {
      "render the initial Document views and direct Article 9 source" in {
        _with_temp_dir("cozy-document-project-local-build-article-nine-rendering") { root =>
          Given("a descriptor-free direct package with the Phase-58 Japanese Document inputs and an authored SmartDox Article 9 source")
          val fixture = _fixture(root)
          _write(fixture.index, _article_nine_source)

          When("each local confirmation target is initially built")
          val structurereport = _execute(List("document-project", "build", fixture.project.toString, "--target", "document-structure-html"))
          val readerreport = _execute(List("document-project", "build", fixture.project.toString, "--target", "document-reader-html"))
          val articlereport = _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))

          Then("the two Document views and genuine direct article are installed from their declared inputs")
          structurereport should include("outcome: built")
          readerreport should include("outcome: built")
          articlereport should include("outcome: built")
          val initialarticle = _html(fixture.project, "smartdox-article-html")
          initialarticle should include("Article 9 Application Modeling")
          initialarticle should include("This direct Article 9-shaped SmartDox article remains authored in index.dox.")
          initialarticle should include("The Article 9 Core remains the logical source.")
          initialarticle should include("<article")
          initialarticle should include("<h1")
          initialarticle should include("<p")
          initialarticle should include("<ul")
          initialarticle should include("<li")
          initialarticle should include("<a href=\"https://example.com/article-9\"")
          Files.exists(fixture.project.resolve("document-project.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }

      "rebuild only the Document targets after a Document-only prose edit" in {
        _with_temp_dir("cozy-document-project-local-build-article-nine-boundaries") { root =>
          Given("a descriptor-free direct package with current Document views and a current authored SmartDox Article 9 output")
          val fixture = _fixture(root)
          _write(fixture.index, _article_nine_source)
          _execute(List("document-project", "build", fixture.project.toString, "--target", "document-structure-html"))
          _execute(List("document-project", "build", fixture.project.toString, "--target", "document-reader-html"))
          _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))

          And("all selected inputs and installed outputs are given controlled current timestamps")
          val inputtime = FileTime.fromMillis(10000L)
          val outputtime = FileTime.fromMillis(20000L)
          val structureoutput = _output(fixture.project, "document-structure-html")
          val readeroutput = _output(fixture.project, "document-reader-html")
          val articleoutput = _output(fixture.project, "smartdox-article-html")
          Vector(fixture.config, fixture.core, fixture.document, fixture.vocabulary, fixture.index).foreach(_set_time(_, inputtime))
          Vector(structureoutput, readeroutput, articleoutput).foreach(_set_time(_, outputtime))
          val articlebytes = Files.readAllBytes(articleoutput).toVector

          And("one valid Document Description prose edit newer than the two Document outputs while index.dox remains current")
          val documentsource = Files.readString(fixture.document, StandardCharsets.UTF_8)
          val changedprose = "第9回では、更新後のDocument Descriptionの文章だけを確認します。"
          val changeddocument = documentsource.replace(
            "第9回では、第8回のドメインモデリングで整理した問題領域の意味と構造を受け、ユースケースをアプリケーションがどのように実現するかをアプリケーションモデリングとして扱います。ドメインモデルをアプリケーションモデルへ対応付け、同じSimpleModelingモデルを静的側面と動的側面という異なる関心から組織します。静的ビューを依存対象として、動的ビューはその意味に依存します。",
            changedprose
          )
          changeddocument should not be documentsource
          _write(fixture.document, changeddocument)
          _set_time(fixture.document, FileTime.fromMillis(30000L))

          When("the two Document targets and the unchanged direct article target are requested again")
          val changedstructurereport = _execute(List("document-project", "build", fixture.project.toString, "--target", "document-structure-html"))
          val changedreaderreport = _execute(List("document-project", "build", fixture.project.toString, "--target", "document-reader-html"))
          val currentarticlereport = _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))

          Then("only the Document views rebuild and render the changed prose while the current article output is reused unchanged")
          changedstructurereport should include("outcome: built")
          changedreaderreport should include("outcome: built")
          _html(fixture.project, "document-structure-html") should include(changedprose)
          _html(fixture.project, "document-reader-html") should include(changedprose)
          currentarticlereport should include("outcome: reused")
          Files.readAllBytes(articleoutput).toVector shouldBe articlebytes
          Files.getLastModifiedTime(articleoutput, LinkOption.NOFOLLOW_LINKS) shouldBe outputtime
        }
      }
    }

    "apply strict-newer, equal-time, configuration, and force freshness rules" which {
      "reuse a newer article output without mutation" in {
        _with_temp_dir("cozy-document-project-local-build-newer-output") { root =>
          Given("an article target with configuration and source inputs older than its installed output")
          val fixture = _fixture(root)
          val output = _output(fixture.project, "smartdox-article-html")
          _write(output, "<article>installed</article>\n")
          _set_time(fixture.config, FileTime.fromMillis(10000L))
          _set_time(fixture.index, FileTime.fromMillis(10000L))
          _set_time(output, FileTime.fromMillis(20000L))

          When("the current article target is built")
          val report = _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))

          Then("the output is reused and its newer timestamp remains unchanged")
          report should include("outcome: reused")
          Files.getLastModifiedTime(output, LinkOption.NOFOLLOW_LINKS) shouldBe FileTime.fromMillis(20000L)
        }
      }

      "reuse an equal-time article output without mutation" in {
        _with_temp_dir("cozy-document-project-local-build-equal-time") { root =>
          Given("an article target with configuration, source inputs, and installed output at the same timestamp")
          val fixture = _fixture(root)
          val output = _output(fixture.project, "smartdox-article-html")
          _write(output, "<article>installed</article>\n")
          val equal = FileTime.fromMillis(10000L)
          _set_time(fixture.config, equal)
          _set_time(fixture.index, equal)
          _set_time(output, equal)

          When("the equal-time article target is built")
          val report = _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))

          Then("the output is reused and its timestamp remains unchanged")
          report should include("outcome: reused")
          Files.getLastModifiedTime(output, LinkOption.NOFOLLOW_LINKS) shouldBe equal
        }
      }

      "rebuild an article output when its configuration is strictly newer" in {
        _with_temp_dir("cozy-document-project-local-build-newer-config") { root =>
          Given("an article target with configuration newer than its source input and installed output")
          val fixture = _fixture(root)
          val output = _output(fixture.project, "smartdox-article-html")
          _write(output, "<article>installed</article>\n")
          _set_time(fixture.config, FileTime.fromMillis(20000L))
          _set_time(fixture.index, FileTime.fromMillis(10000L))
          _set_time(output, FileTime.fromMillis(10000L))

          When("the article target is built")
          val report = _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))

          Then("the article output is rebuilt")
          report should include("outcome: built")
        }
      }

      "rebuild a current article output when force is selected" in {
        _with_temp_dir("cozy-document-project-local-build-force") { root =>
          Given("a current article target with equal-time configuration, source input, and installed output")
          val fixture = _fixture(root)
          val output = _output(fixture.project, "smartdox-article-html")
          _write(output, "<article>installed</article>\n")
          val equal = FileTime.fromMillis(10000L)
          _set_time(fixture.config, equal)
          _set_time(fixture.index, equal)
          _set_time(output, equal)

          When("the article target is built with force selected")
          val report = _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html", "--force"))

          Then("the article output is rebuilt")
          report should include("outcome: built")
        }
      }
    }

    "build generated prerequisites before their consumer and rebuild the consumer from a newer prerequisite output" in {
      _with_temp_dir("cozy-document-project-local-build-prerequisites") { root =>
        Given("a reader target that declares the structure target as its generated prerequisite")
        val fixture = _fixture(root, Map("document-reader-html" -> Vector("document-structure-html")))
        val initialreport = _execute(List("document-project", "build", fixture.project.toString, "--target", "document-reader-html"))
        val prerequisite = _output(fixture.project, "document-structure-html")
        val consumer = _output(fixture.project, "document-reader-html")
        _set_time(fixture.config, FileTime.fromMillis(10000L))
        _set_time(fixture.core, FileTime.fromMillis(10000L))
        _set_time(fixture.document, FileTime.fromMillis(10000L))
        _set_time(fixture.vocabulary, FileTime.fromMillis(10000L))
        _set_time(prerequisite, FileTime.fromMillis(30000L))
        _set_time(consumer, FileTime.fromMillis(10000L))

        When("the consumer is requested after its current prerequisite output becomes newer")
        val report = _execute(List("document-project", "build", fixture.project.toString, "--target", "document-reader-html"))

        Then("the prerequisite is available before the reader route and its newer primary output rebuilds the consumer only")
        initialreport should include("outcome: built")
        Files.exists(prerequisite, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.exists(consumer, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.getLastModifiedTime(prerequisite, LinkOption.NOFOLLOW_LINKS) shouldBe FileTime.fromMillis(30000L)
        report should include("target: document-reader-html")
        report should include("outcome: built")
      }
    }

    "reject invalid graphs unsafe sources and symbolic project or output ancestry before any renderer runs" in {
      _with_temp_dir("cozy-document-project-local-build-rejections") { root =>
        Given("packages with unknown and cyclic prerequisites, missing and symbolic sources, and symbolic project or output ancestry")
        val unknown = _fixture(root.resolve("unknown"), Map("smartdox-article-html" -> Vector("unknown-html")))
        val cyclic = _fixture(root.resolve("cyclic"), Map("document-structure-html" -> Vector("document-reader-html"), "document-reader-html" -> Vector("document-structure-html")))
        val missing = _fixture(root.resolve("missing"))
        Files.delete(missing.index)
        val symbolicinput = _fixture(root.resolve("symbolic-input"))
        val externalinput = _write(root.resolve("external-index.dox"), "# External\n")
        Files.delete(symbolicinput.index)
        Files.createSymbolicLink(symbolicinput.index, externalinput)
        val symbolicoutput = _fixture(root.resolve("symbolic-output"))
        val externaloutput = Files.createDirectories(root.resolve("external-output"))
        Files.createSymbolicLink(symbolicoutput.project.resolve("target"), externaloutput)
        val linkedparent = Files.createDirectories(root.resolve("linked-project-parent"))
        val directproject = _fixture(linkedparent)
        val projectparentlink = root.resolve("project-parent-link")
        Files.createSymbolicLink(projectparentlink, linkedparent)
        val linkedproject = projectparentlink.resolve(directproject.project.getFileName)

        When("each invalid build is requested")
        val linkedprojectfailure = _failure(List("document-project", "build", linkedproject.toString))
        val failures = Vector(
          _failure(List("document-project", "build", missing.project.toString, "--target", "nearby-html")),
          _failure(List("document-project", "build", unknown.project.toString, "--target", "smartdox-article-html")),
          _failure(List("document-project", "build", cyclic.project.toString)),
          _failure(List("document-project", "build", missing.project.toString, "--target", "smartdox-article-html")),
          _failure(List("document-project", "build", symbolicinput.project.toString, "--target", "smartdox-article-html")),
          _failure(List("document-project", "build", symbolicoutput.project.toString)),
          linkedprojectfailure
        )

        Then("every request fails closed before target HTML is rendered or installed")
        failures.foreach(_ should include("DP-"))
        linkedprojectfailure should include("DP-PATH-001")
        Vector(unknown, cyclic, missing, symbolicinput, directproject).foreach { fixture =>
          Files.exists(fixture.project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
        Files.exists(externaloutput.resolve("document-project/local-build/document-structure-html/index.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "preserve a successful article output when a later SmartDox renderer fault occurs" in {
      _with_temp_dir("cozy-document-project-local-build-atomic-failure") { root =>
        Given("a successfully installed article output and a later parser-faulting direct article source")
        val fixture = _fixture(root)
        _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))
        val output = _output(fixture.project, "smartdox-article-html")
        val bytes = Files.readAllBytes(output).toVector
        val time = Files.getLastModifiedTime(output, LinkOption.NOFOLLOW_LINKS)
        _write(fixture.index, "[[https://example.com/fault][Fault]]\n")
        _set_time(fixture.index, FileTime.fromMillis(time.toMillis + 10000L))

        When("the outdated target invokes the SmartDox renderer and it faults before installation")
        val failure = _failure(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html"))

        Then("the original output bytes and timestamp remain untouched")
        failure should not be empty
        Files.readAllBytes(output).toVector shouldBe bytes
        Files.getLastModifiedTime(output, LinkOption.NOFOLLOW_LINKS) shouldBe time
      }
    }
  }

  private final case class Fixture(
    project: Path,
    config: Path,
    core: Path,
    document: Path,
    vocabulary: Path,
    index: Path
  )

  private def _fixture(root: Path, prerequisites: Map[String, Vector[String]] = Map.empty): Fixture = {
    val project = Files.createDirectories(root.resolve("local-build.dox"))
    val config = _write(project.resolve("build/document-project-targets.yaml"), _configuration(prerequisites))
    val core = project.resolve("content/core.yaml")
    val document = project.resolve("content/ja/document.yaml")
    val vocabulary = project.resolve("content/ja/confirmation-vocabulary.yaml")
    Option(core.getParent).foreach(Files.createDirectories(_))
    Option(document.getParent).foreach(Files.createDirectories(_))
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content-v2/core.yaml"), core)
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content-v2/ja/document.yaml"), document)
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content-v2/ja/confirmation-vocabulary.yaml"), vocabulary)
    val index = _write(project.resolve("index.dox"), "# Local article\n\nThe direct article source.\n")
    Fixture(project, config, core, document, vocabulary, index)
  }

  private val _article_nine_source =
    """# Article 9 Application Modeling
      |
      |This direct Article 9-shaped SmartDox article remains authored in index.dox.
      |
      |- The Article 9 Core remains the logical source.
      |- The Japanese Document Description remains the local document source.
      |
      |- [[https://example.com/article-9][Read the Article 9 source]]
      |""".stripMargin

  private def _configuration(prerequisites: Map[String, Vector[String]]): String = {
    val targets = Vector(
      "document-structure-html" -> Vector("content/core.yaml", "content/ja/document.yaml", "content/ja/confirmation-vocabulary.yaml"),
      "document-reader-html" -> Vector("content/core.yaml", "content/ja/document.yaml", "content/ja/confirmation-vocabulary.yaml"),
      "smartdox-article-html" -> Vector("index.dox")
    )
    val definitions = targets.map { case (id, inputs) =>
      val inputlines = inputs.map(value => s"      - $value").mkString("\n")
      val prerequisitevalues = prerequisites.getOrElse(id, Vector.empty)
      val prerequisitelines = if (prerequisitevalues.isEmpty) "    prerequisites: []" else prerequisitevalues.map(value => s"      - $value").mkString("    prerequisites:\n", "\n", "")
      s"  $id:\n    inputs:\n$inputlines\n$prerequisitelines"
    }.mkString("\n")
    s"schema: cozy.document-project.local-build-targets.v1\ntargets:\n$definitions\n"
  }

  private def _output(project: Path, id: String): Path =
    project.resolve("target/document-project/local-build").resolve(id).resolve("index.html")

  private def _html(project: Path, id: String): String =
    Files.readString(_output(project, id), StandardCharsets.UTF_8)

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentProject.execute(args) shouldBe true
    }
    bytes.toString("UTF-8").trim
  }

  private def _failure(args: List[String]): String =
    intercept[RuntimeException] { CozyDocumentProject.execute(args) }.getMessage

  private def _set_time(path: Path, value: FileTime): Unit =
    Files.setLastModifiedTime(path, value)

  private def _resource(value: String): Path =
    Paths.get(getClass.getResource(value).toURI)

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name).toRealPath()
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item)
        catch { case NonFatal(_) => () }
      }
      finally stream.close()
    }
}
