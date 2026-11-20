package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, LinkOption, Path, Paths, StandardWatchEventKinds}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 14, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyDocumentProjectLocalBuildCommandSpec {
  private final case class Fixture(
    project: Path,
    config: Path,
    core: Path,
    document: Path,
    vocabulary: Path,
    index: Path
  )
}

final class CozyDocumentProjectLocalBuildCommandSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDocumentProjectLocalBuildCommandSpec.Fixture

  "Cozy Document Project local-build command" should {
    "scope child temporary and cache settings to the external run" in {
      _with_temp_dir("cozy-document-project-child-environment") { root =>
        Given("an admitted external run and its scoped media runner")
        val fixture = _fixture(root)
        val workspace = CozyDocumentProjectWorkRoot.allocate(root.resolve("work"), fixture.project, None, Vector.empty)
        val runner = cozy.media.CozyMedia.ProcessRunner.external(workspace, fixture.project)
        val script = "case \"$JAVA_TOOL_OPTIONS\" in *-Djava.io.tmpdir=*) ;; *) exit 4;; esac; printf '%s\\n' \"$TMPDIR\" \"$XDG_CACHE_HOME\" > child-paths.txt"

        When("a child process observes its environment and writes a file")
        val exit = runner.run(Vector("/bin/sh", "-c", script), workspace.root)

        Then("the child sees run-local paths and its output and log remain in that run")
        exit shouldBe 0
        val paths = Files.readAllLines(workspace.root.resolve("child-paths.txt"), StandardCharsets.UTF_8).asScala.toVector
        paths shouldBe Vector(workspace.temporary.toString, workspace.cache.toString)
        Files.isDirectory(workspace.root.resolve("logs")) shouldBe true
        Files.exists(fixture.project.resolve("child-paths.txt")) shouldBe false
      }
    }

    "keep native infographic writes in the external run while the producer executes" in {
      _with_temp_dir("cozy-document-project-media-boundary") { root =>
        Given("a project with a declared SVG and an unrelated private file")
        val fixture = _fixture(root)
        val images = Files.createDirectory(fixture.project.resolve("images"))
        _write(images.resolve("figure.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
        _write(fixture.project.resolve(".cozy/private.txt"), "private")
        _write(fixture.project.resolve("media.yaml"), """{
          "schema":"cozy.media.v1", "knowledge":{"id":"sample","type":"documentmodel:KnowledgeUnit","source":"index.dox"},
          "languages":["ja"], "resources":[{"id":"infographic-ja","kind":"infographic","language":"ja",
          "source":"images/figure.svg","output":"images/figure.png","build":"svg-to-png"}]
        }""")
        val workspace = CozyDocumentProjectWorkRoot.allocate(root.resolve("work"), fixture.project, None, Vector.empty)
        val watcher = java.nio.file.FileSystems.getDefault.newWatchService()
        fixture.project.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE)
        images.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE)
        val runner = new cozy.media.CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingDirectory: Path): Int = {
            workingDirectory.startsWith(workspace.root) shouldBe true
            val transient = workingDirectory.resolve("temporary-child-file")
            Files.writeString(transient, "intermediate", StandardCharsets.UTF_8)
            Files.delete(transient)
            Files.write(Paths.get(command.last), Array[Byte](1, 2, 3))
            0
          }
        }

        When("the native media build runs with a wrapped child producer")
        val result = try {
          val report = CozyDocumentProjectMediaWorkspace.build(fixture.project, workspace, "infographic", "ja", Some(runner))
          val event = watcher.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS)
          (report, Option(event).toVector.flatMap(_.pollEvents().asScala.toVector))
        } finally watcher.close()

        Then("the original project has no observed writes and only declared inputs are materialized")
        result._1 should include("infographic-ja: built")
        result._2 shouldBe empty
        Files.exists(fixture.project.resolve("images/figure.png")) shouldBe false
        Files.isRegularFile(workspace.root.resolve("project.dox/images/figure.png")) shouldBe true
        Files.exists(workspace.root.resolve("project.dox/.cozy/private.txt")) shouldBe false
      }
    }

    "reject an escaping media output before invoking a producer" in {
      _with_temp_dir("cozy-document-project-media-output-escape") { root =>
        Given("an external media selection with an escaping output")
        val fixture = _fixture(root)
        _write(fixture.project.resolve("figure.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
        _write(fixture.project.resolve("media.yaml"), """{"resources":[{"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"figure.svg","output":"../../escape.png"}]}""")
        val workspace = CozyDocumentProjectWorkRoot.allocate(root.resolve("work"), fixture.project, None, Vector.empty)
        var invoked = false
        val runner = new cozy.media.CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingDirectory: Path): Int = { invoked = true; 0 }
        }

        When("the selected product is built")
        val error = intercept[Exception] {
          CozyDocumentProjectMediaWorkspace.build(fixture.project, workspace, "infographic", "ja", Some(runner))
        }

        Then("the path is rejected before materialization or producer execution")
        error.getMessage should include("media output escapes")
        invoked shouldBe false
        Files.exists(workspace.root.resolve("project.dox")) shouldBe false
      }
    }

    "resolve the external default from host configuration" in {
      _with_temp_dir("cozy-document-project-host-work") { root =>
        Given("a host home with one explicit WorkRoot")
        val configured = root.resolve("external")
        _write(root.resolve(".cozy/config.json"), s"""{"documentProject":{"workRoot":"$configured"}}""")

        When("the host default is resolved")
        val result = CozyDocumentProjectWorkRoot.hostDefault(root)

        Then("the host setting is used")
        result shouldBe configured
      }
    }

    "reject duplicate host WorkRoot declarations" in {
      _with_temp_dir("cozy-document-project-host-duplicate") { root =>
        Given("two host configuration files declaring the same setting")
        val value = root.resolve("external")
        _write(root.resolve(".cozy/config.json"), s"""{"documentProject":{"workRoot":"$value"}}""")
        _write(root.resolve(".cozy/config.yaml"), s"documentProject:\n  workRoot: $value\n")

        When("the host default is resolved")
        val error = intercept[Exception] { CozyDocumentProjectWorkRoot.hostDefault(root) }

        Then("the conflicting layer is rejected")
        error.getMessage should include("duplicated")
      }
    }

    "diagnose a missing dialogue script before starting audio synthesis" in {
      _with_temp_dir("cozy-document-project-audio-input") { root =>
        Given("an audio selection whose declared dialogue script is absent")
        val fixture = _fixture(root)
        _write(fixture.project.resolve("media.yaml"), """{"resources":[{"id":"video-ja","project":"video-ja.yaml"}]}""")
        _write(fixture.project.resolve("video-ja.yaml"), """{"parts":[{"id":"dialogue","type":"dialogue","script":"missing.json","audioDir":"target/audio","output":"target/part.mp4"}],"output":"target/final.mp4"}""")
        val work = root.resolve("work")

        When("the Japanese audio product is requested")
        val error = intercept[Exception] {
          _execute(List("document-project", "build", fixture.project.toString, "--target", "audio", "--locale", "ja", "--work-root", work.toString))
        }

        Then("the source diagnostic is returned without project-local output")
        error.getMessage should include("video input is unavailable")
        Files.exists(fixture.project.resolve("target")) shouldBe false
      }
    }

    "reject an unsupported summary renderer before starting any producer" in {
      _with_temp_dir("cozy-document-project-summary-renderer") { root =>
        Given("a locale-selected summary with an unknown shared renderer")
        val fixture = _fixture(root)
        val renderer = fixture.project.resolve("render-pdf")
        _write(renderer, "#!/bin/sh\nexit 0\n")
        renderer.toFile.setExecutable(true) shouldBe true
        _write(fixture.index, "An article without figures.")
        _write(fixture.project.resolve("media.yaml"), """{
          "profiles":{"business":{"presentation":{"renderer":{"name":"unknown","version":"2","command":["./render-summary"]}}}},
          "resources":[
            {"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"figure.svg","output":"figure.png"},
            {"id":"article-pdf-ja","build":"article-pdf","language":"ja","source":"index.dox","output":"article.pdf",
             "articlePdf":{"infographic":"infographic-ja","renderer":{"name":"simplemodeling-smartdox-pdf","version":"1","command":["./render-pdf"]}}},
            {"id":"summary-slides-ja","build":"summary-slides-pdf","language":"ja","source":"slide.json",
             "summarySlidesPdf":{"contract":"slide-ir-v1","profile":"business","articlePdf":"article-pdf-ja","infographic":"infographic-ja"}}
          ]
        }""")
        val work = root.resolve("work")

        When("the external summary target is requested")
        val error = intercept[Exception] {
          _execute(List("document-project", "build", fixture.project.toString, "--target", "summary-slides", "--locale", "ja", "--work-root", work.toString))
        }

        Then("the unsupported renderer is diagnosed before the infographic or PDF can run")
        error.getMessage should include("renderer has no admitted external-workspace capability")
        Files.exists(fixture.project.resolve("figure.png")) shouldBe false
        Files.exists(fixture.project.resolve("article.pdf")) shouldBe false
      }
    }

    "diagnose a missing article image before starting the PDF renderer" in {
      _with_temp_dir("cozy-document-project-media-input") { root =>
        Given("a PDF target with an unavailable project-relative image")
        val fixture = _fixture(root)
        val renderer = fixture.project.resolve("render-pdf")
        _write(renderer, "#!/bin/sh\nexit 0\n")
        renderer.toFile.setExecutable(true) shouldBe true
        _write(fixture.index, "[[images/missing.png]]")
        _write(fixture.project.resolve("figure.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
        _write(fixture.project.resolve("media.yaml"), """{
          "resources": [{"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"figure.svg","output":"figure.png"},
            {"id":"article-pdf-ja","build":"article-pdf","language":"ja","source":"index.dox",
            "articlePdf":{"infographic":"infographic-ja","renderer":{"name":"simplemodeling-smartdox-pdf","version":"1","command":["./render-pdf"]}}}]
        }""")
        val work = root.resolve("work")

        When("the external PDF target is requested")
        val error = intercept[Exception] {
          _execute(List("document-project", "build", fixture.project.toString, "--target", "article-pdf", "--locale", "ja", "--work-root", work.toString))
        }

        Then("the missing input is reported and no project-local build output appears")
        error.getMessage should include("article image requires a resolvable project-relative input")
        Files.exists(fixture.project.resolve("target")) shouldBe false
      }
    }

    "render confirmation HTML in an explicitly selected external run" in {
      _with_temp_dir("cozy-document-project-external-html") { root =>
        Given("a project and a separate local work root")
        val fixture = _fixture(root)
        val work = root.resolve("external-work")

        When("the article HTML target is built with that work root")
        val report = _execute(List("document-project", "build", fixture.project.toString, "--target", "smartdox-article-html", "--work-root", work.toString))

        Then("the report points into the owned run and the project receives no local output")
        report should include("outcome: built")
        report should include(work.resolve("target/document-project/runs").toString)
        val output = report.split("\n").iterator.find(_.startsWith("output: ")).get.stripPrefix("output: ")
        Files.readString(Paths.get(output), StandardCharsets.UTF_8).toLowerCase should include("<article")
        Files.exists(fixture.project.resolve("target/document-project/local-build")) shouldBe false
      }
    }

    "reject a synchronization root inside the work root" in {
      _with_temp_dir("cozy-document-project-sync-overlap") { root =>
        Given("a separate work root containing an identified synchronization root")
        val fixture = _fixture(root)
        val work = root.resolve("external-work")
        val sync = work.resolve("target/document-project")

        When("the build identifies that synchronization root")
        val error = intercept[Exception] {
          _execute(List("document-project", "build", fixture.project.toString, "--work-root", work.toString, "--sync-root", sync.toString))
        }

        Then("admission fails before allocating any work")
        error.getMessage should include("overlaps protected root")
        Files.exists(work) shouldBe false
      }
    }

    "reject a work subtree redirected into the project" in {
      _with_temp_dir("cozy-document-project-work-symlink") { root =>
        Given("an external work root whose target points into the project")
        val fixture = _fixture(root)
        val work = Files.createDirectory(root.resolve("external-work"))
        Files.createSymbolicLink(work.resolve("target"), fixture.project)

        When("a build requests that work root")
        val error = intercept[Exception] {
          _execute(List("document-project", "build", fixture.project.toString, "--work-root", work.toString))
        }

        Then("admission rejects the redirect before creating a run")
        error.getMessage should include("work subtree must contain only direct directories")
        Files.exists(fixture.project.resolve("document-project")) shouldBe false
      }
    }

    "reject an external work root that overlaps the project" in {
      _with_temp_dir("cozy-document-project-overlap") { root =>
        Given("a project and a work root inside it")
        val fixture = _fixture(root)
        val work = fixture.project.resolve("work")

        When("a build requests the overlapping work root")
        val error = intercept[Exception] {
          _execute(List("document-project", "build", fixture.project.toString, "--work-root", work.toString))
        }

        Then("admission fails before creating the work directory")
        error.getMessage should include("overlaps protected root")
        Files.exists(work) shouldBe false
      }
    }

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
      _with_temp_dir("cozy-document-project-local-build-render-failure") { root =>
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
