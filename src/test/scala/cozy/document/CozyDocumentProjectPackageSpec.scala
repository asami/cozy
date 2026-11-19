package cozy.document

import java.nio.file.{Files, Path}
import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CozyDocumentProjectPackageSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Editable package placement" should {
    "copy the explicitly declared source, including authored input below target" in {
      Given("a project and an unrelated existing package file")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project"))
      val target = Files.createDirectory(project.resolve("target"))
      Files.writeString(target.resolve("authored.svg"), "<svg/>")
      Files.writeString(project.resolve("media.yaml"), """{"resources":[{"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"target/authored.svg","output":"infographic.png"}]}""")
      val destination = Files.createDirectory(root.resolve("package"))
      Files.writeString(destination.resolve("notes.txt"), "keep")
      val declaration = CozyDocumentProjectPackage.Placement(".", Vector("target/authored.svg", "media.yaml"), Vector(
        CozyDocumentProjectPackage.Product("infographic", "ja", "products/ja.png")
      ))

      When("the reviewed closure is placed")
      val manifest = CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration)

      Then("the input and declaration are portable and the unrelated file remains")
      Files.readString(destination.resolve("source/target/authored.svg")) shouldBe "<svg/>"
      Files.readString(manifest) should include("cozy.document-package.v1")
      Files.readString(destination.resolve("notes.txt")) shouldBe "keep"
      Files.exists(destination.resolve("products/ja.png")) shouldBe false
    }

    "resume an explicitly retained placement without overwriting edits" in {
      Given("a placement with staged bytes retained outside the package")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project"))
      Files.writeString(project.resolve("first.txt"), "first")
      Files.writeString(project.resolve("second.txt"), "second")
      Files.writeString(project.resolve("figure.svg"), "<svg/>")
      Files.writeString(project.resolve("media.yaml"), """{"resources":[{"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"figure.svg","output":"infographic.png"}]}""")
      val destination = root.resolve("package")
      val work = root.resolve("work")
      val declaration = CozyDocumentProjectPackage.Placement(".", Vector("first.txt", "second.txt", "figure.svg", "media.yaml"), Vector(
        CozyDocumentProjectPackage.Product("infographic", "ja", "report.png")
      ))
      CozyDocumentProjectPackage.place(project, destination, work, Vector.empty, declaration)
      val runs = Files.list(work.resolve("target/document-project/runs"))
      val run = try runs.findFirst().get() finally runs.close()
      Files.delete(destination.resolve("source/second.txt"))
      Files.writeString(destination.resolve("source/first.txt"), "edited")

      When("recovery encounters an edited file")
      intercept[Exception] { CozyDocumentProjectPackage.recover(destination, work, run) }
      Then("it preserves the edit and does not partially resume")
      Files.readString(destination.resolve("source/first.txt")) shouldBe "edited"
      Files.exists(destination.resolve("source/second.txt")) shouldBe false

      When("the conflicting bytes are reconciled and recovery is retried")
      Files.writeString(destination.resolve("source/first.txt"), "first")
      CozyDocumentProjectPackage.recover(destination, work, run)
      Then("the missing bytes are restored from the external stage")
      Files.readString(destination.resolve("source/second.txt")) shouldBe "second"
    }

    "refuse an existing unowned product without changing it or ignore rules" in {
      Given("a package with an unrelated product and existing Git ignore policy")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project"))
      Files.writeString(project.resolve("index.dox"), "source")
      val destination = Files.createDirectory(root.resolve("package"))
      Files.writeString(destination.resolve("report.pdf"), "existing")
      Files.writeString(destination.resolve(".gitignore"), "target/\n")
      val declaration = CozyDocumentProjectPackage.Placement(".", Vector("index.dox"), Vector(
        CozyDocumentProjectPackage.Product("article-pdf", "ja", "report.pdf")
      ))

      When("initial placement attempts to claim that product")
      intercept[Exception] {
        CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration)
      }

      Then("the existing files remain intact and no source is copied")
      Files.readString(destination.resolve("report.pdf")) shouldBe "existing"
      Files.readString(destination.resolve(".gitignore")) shouldBe "target/\n"
      Files.exists(destination.resolve("source")) shouldBe false
    }

    "bundle explicitly mapped renderer references for relocation" in {
      Given("a media descriptor referring to a renderer outside the project")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project.dox"))
      val tool = Files.writeString(root.resolve("renderer"), "renderer bytes")
      Files.writeString(project.resolve("figure.svg"), "<svg/>")
      Files.writeString(project.resolve("media.yaml"), """{"profiles":{"p":{"renderer":{"command":["../renderer","--asset=../renderer"]}}},"receipt":{"inputs":[{"path":"../renderer"}]},"resources":[{"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"figure.svg","output":"infographic.png"}]}""")
      val declaration = CozyDocumentProjectPackage.Placement("project.dox", Vector("media.yaml", "figure.svg"), Vector(
        CozyDocumentProjectPackage.Product("infographic", "ja", "report.png")
      ), Vector(CozyDocumentProjectPackage.Bundle("../renderer", "tools/renderer")),
        Vector(CozyDocumentProjectPackage.Reference("../renderer", "../tools/renderer")))

      When("the declared closure is placed at a new package")
      val destination = root.resolve("package")
      CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration)

      Then("the descriptor references the bundled tool without the original location")
      Files.readString(destination.resolve("source/project.dox/media.yaml")) should include("--asset=../tools/renderer")
      Files.readString(destination.resolve("source/tools/renderer")) shouldBe Files.readString(tool)
    }

    "reject an unresolved renderer before creating the package" in {
      Given("a renderer reference with no mapped source")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project.dox"))
      Files.writeString(project.resolve("media.yaml"), """{"renderer":{"command":["../../old/renderer"]}}""")
      val declaration = CozyDocumentProjectPackage.Placement("project.dox", Vector("media.yaml"), Vector(
        CozyDocumentProjectPackage.Product("article-pdf", "ja", "report.pdf")
      ))
      val destination = root.resolve("package")

      When("placement preflights the declared closure")
      intercept[Exception] { CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration) }

      Then("it writes neither the package nor a run")
      Files.exists(destination) shouldBe false
      Files.exists(root.resolve("work")) shouldBe false
    }

    "reject missing selected media inputs before copying the package" in {
      Given("a selected infographic whose authored SVG is absent from the explicit closure")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project"))
      Files.writeString(project.resolve("figure.svg"), "<svg/>")
      Files.writeString(project.resolve("media.yaml"), """{"resources":[{"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"figure.svg","output":"figure.png"}]}""")
      val destination = root.resolve("package")
      val declaration = CozyDocumentProjectPackage.Placement(".", Vector("media.yaml"), Vector(
        CozyDocumentProjectPackage.Product("infographic", "ja", "figure.png")
      ))
      When("the staged closure is admitted")
      intercept[Exception] { CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration) }
      Then("the package remains untouched")
      Files.exists(destination) shouldBe false
    }

    "reject external SVG CSS references before placing a package" in {
      Given("an infographic using an external CSS paint server")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project"))
      val figure = project.resolve("figure.svg")
      Files.writeString(figure, """<svg xmlns="http://www.w3.org/2000/svg"><rect style="fill:url(/old-host/pattern.svg#paint)"/></svg>""")
      Files.writeString(project.resolve("media.yaml"), """{"resources":[{"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"figure.svg","output":"figure.png"}]}""")
      val destination = root.resolve("package")
      val declaration = CozyDocumentProjectPackage.Placement(".", Vector("figure.svg", "media.yaml"), Vector(
        CozyDocumentProjectPackage.Product("infographic", "ja", "figure.png")
      ))

      When("placement preflights the selected SVG")
      intercept[Exception] { CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration) }
      Then("no package source or product is copied")
      Files.exists(destination.resolve("source")) shouldBe false
      Files.exists(destination.resolve("figure.png")) shouldBe false

      When("the SVG uses internal references and inline data")
      Files.writeString(figure, """<svg xmlns="http://www.w3.org/2000/svg"><style>.a { filter: url(#shadow) }</style><rect fill="url(#paint)" style="filter:url('#shadow')"/><image href="data:image/png;base64,YQ=="/></svg>""")
      CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration)
      Then("placement preserves the portable SVG")
      Files.readString(destination.resolve("source/figure.svg")) should include("url(#shadow)")
    }

    "admit slide review with its native inputs" in {
      Given("an infographic, article, slide IR and portable renderers")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project.dox"))
      Files.writeString(project.resolve("index.dox"), "Article")
      Files.writeString(project.resolve("figure.svg"), "<svg/>")
      Files.writeString(project.resolve("template.pptx"), "template")
      Files.writeString(project.resolve("slides.json"), """{"schema":"cozy.slide-ir.v1","knowledge":"article","language":"ja","slides":[{"id":"one","title":"One","layout":"figure","elements":[{"role":"title","text":"One"},{"role":"figure","asset":"infographic-ja"}]}]}""")
      val pdf = Files.writeString(project.resolve("render-pdf"), "#!/bin/sh\n")
      val summary = Files.writeString(project.resolve("render-summary"), "#!/bin/sh\n")
      pdf.toFile.setExecutable(true) shouldBe true
      summary.toFile.setExecutable(true) shouldBe true
      Files.writeString(project.resolve("media.yaml"), """{
        "profiles":{"business":{"presentation":{"template":"template.pptx","renderer":{"name":"simplemodeling-summary","version":"2","command":["render-summary"]}}}},
        "resources":[
          {"id":"infographic-ja","build":"svg-to-png","language":"ja","source":"figure.svg","output":"out/figure.png"},
          {"id":"article-pdf-ja","build":"article-pdf","language":"ja","source":"index.dox","output":"out/article.pdf","articlePdf":{"infographic":"infographic-ja","renderer":{"name":"simplemodeling-smartdox-pdf","version":"1","command":["render-pdf"]}}},
          {"id":"summary-slides-ja","build":"summary-slides-pdf","language":"ja","source":"slides.json","output":"out/slides.pdf","summarySlidesPdf":{"contract":"slide-ir-v1","profile":"business","articlePdf":"article-pdf-ja","infographic":"infographic-ja","slideImages":"out/images","montage":"out/montage.png","rendererManifest":"out/manifest.json"}}
        ]}""")
      val destination = root.resolve("package")
      val declaration = CozyDocumentProjectPackage.Placement("project.dox", Vector("index.dox", "figure.svg", "template.pptx", "slides.json", "render-pdf", "render-summary", "media.yaml"), Vector(
        CozyDocumentProjectPackage.Product("slide-review", "ja", "review.png")
      ))
      When("the selected closure is admitted")
      CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration)
      Then("ownership is recorded without generating the review")
      Files.readString(destination.resolve("source/cozy-package.yaml")) should include("slide-review")
      Files.exists(destination.resolve("review.png")) shouldBe false
    }

    "reject video review without its storyboard closure" in {
      Given("an incomplete video review source selection")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project.dox"))
      Files.writeString(project.resolve("index.dox"), "source")
      val destination = root.resolve("package")
      val declaration = CozyDocumentProjectPackage.Placement("project.dox", Vector("index.dox"), Vector(
        CozyDocumentProjectPackage.Product("video-review", "ja", "review.html")
      ))
      When("the staged sources are admitted")
      intercept[Exception] { CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration) }
      Then("the package remains untouched")
      Files.exists(destination) shouldBe false
    }

    "refresh only explicitly selected successful products" in {
      Given("a package with two owned products and one unrelated file")
      val root = _root()
      val destination = _refresh_package(root)
      Files.writeString(destination.resolve("ja.pdf"), "old-ja")
      Files.writeString(destination.resolve("en.pdf"), "old-en")
      Files.writeString(destination.resolve("notes.txt"), "keep")
      val producer = (_: Path, run: CozyDocumentProjectWorkRoot.Run, selection: CozyDocumentProjectPackage.Selection) => {
        val path = Files.writeString(run.root.resolve("candidate"), s"new-${selection.locale}")
        CozyDocumentProjectPackage.Candidate(path, "generated")
      }

      When("only the Japanese product is refreshed")
      val receipt = CozyDocumentProjectPackage.refresh(destination, root.resolve("work"), Vector.empty,
        Vector(CozyDocumentProjectPackage.Selection("article-pdf", "ja")), Some(producer))

      Then("the selected bytes change and the backup and completion marker remain external")
      Files.readString(destination.resolve("ja.pdf")) shouldBe "new-ja"
      Files.readString(destination.resolve("en.pdf")) shouldBe "old-en"
      Files.readString(destination.resolve("notes.txt")) shouldBe "keep"
      Files.readString(receipt.getParent.resolve("backup-0")) shouldBe "old-ja"
      Files.exists(receipt.getParent.resolve("completed-0")) shouldBe true
    }

    "retain all previous products when any selected generation fails" in {
      Given("two selected products whose second producer fails")
      val root = _root()
      val destination = _refresh_package(root)
      Files.writeString(destination.resolve("ja.pdf"), "old-ja")
      Files.writeString(destination.resolve("en.pdf"), "old-en")
      val producer = (_: Path, run: CozyDocumentProjectWorkRoot.Run, selection: CozyDocumentProjectPackage.Selection) => {
        if (selection.locale == "en") throw new IllegalStateException("controlled generation failure")
        CozyDocumentProjectPackage.Candidate(Files.writeString(run.root.resolve("candidate"), "new-ja"), "generated")
      }

      When("both products are selected")
      intercept[Exception] {
        CozyDocumentProjectPackage.refresh(destination, root.resolve("work"), Vector.empty,
          Vector(CozyDocumentProjectPackage.Selection("article-pdf", "ja"), CozyDocumentProjectPackage.Selection("article-pdf", "en")), Some(producer))
      }

      Then("neither existing product is changed")
      Files.readString(destination.resolve("ja.pdf")) shouldBe "old-ja"
      Files.readString(destination.resolve("en.pdf")) shouldBe "old-en"
    }

    "restore a touched output while preserving an untouched output" in {
      Given("a retained refresh receipt with one completed and one untouched item")
      val root = _root()
      val destination = Files.createDirectory(root.resolve("package"))
      val source = Files.createDirectory(destination.resolve("source"))
      val work = Files.createDirectory(root.resolve("work"))
      val run = Files.createDirectories(work.resolve("target/document-project/runs/first"))
      Files.writeString(destination.resolve("one.pdf"), "new")
      Files.writeString(destination.resolve("two.pdf"), "unrelated")
      Files.writeString(run.resolve("candidate"), "new")
      Files.writeString(run.resolve("backup-0"), "old")
      Files.writeString(run.resolve("uncertain-0"), "uncertain")
      Files.writeString(run.resolve("completed-0"), "completed")
      val first = _item("article-pdf", "ja", "one.pdf", "candidate", true)
      val second = _item("article-pdf", "en", "two.pdf", "not-created", true)
      _receipt(source, run, destination, Vector(first, second))

      When("the retained run is restored")
      CozyDocumentProjectPackage.recover(destination, work, run)

      Then("the backed-up product is restored and the untouched product is preserved")
      Files.readString(destination.resolve("one.pdf")) shouldBe "old"
      Files.readString(destination.resolve("two.pdf")) shouldBe "unrelated"
    }

    "refuse recovery when a touched destination has an external edit" in {
      Given("a receipt whose destination differs from both candidate and backup")
      val root = _root()
      val destination = Files.createDirectory(root.resolve("package"))
      val source = Files.createDirectory(destination.resolve("source"))
      val work = Files.createDirectory(root.resolve("work"))
      val run = Files.createDirectories(work.resolve("target/document-project/runs/first"))
      Files.writeString(destination.resolve("one.pdf"), "external edit")
      Files.writeString(run.resolve("candidate"), "new")
      Files.writeString(run.resolve("backup-0"), "old")
      Files.writeString(run.resolve("uncertain-0"), "uncertain")
      _receipt(source, run, destination, Vector(_item("article-pdf", "ja", "one.pdf", "candidate", true)))

      When("recovery is requested")
      intercept[Exception] { CozyDocumentProjectPackage.recover(destination, work, run) }

      Then("the externally edited bytes are retained")
      Files.readString(destination.resolve("one.pdf")) shouldBe "external edit"
    }

    "reject conflicting output paths before copying any source" in {
      Given("two selected outputs whose destinations overlap")
      val root = _root()
      val project = Files.createDirectory(root.resolve("project"))
      Files.writeString(project.resolve("index.dox"), "source")
      val destination = root.resolve("package")
      val declaration = CozyDocumentProjectPackage.Placement(".", Vector("index.dox"), Vector(
        CozyDocumentProjectPackage.Product("article-pdf", "ja", "report"),
        CozyDocumentProjectPackage.Product("article-pdf", "en", "report/page.pdf")
      ))

      When("placement is attempted")
      intercept[Exception] {
        CozyDocumentProjectPackage.place(project, destination, root.resolve("work"), Vector.empty, declaration)
      }

      Then("no package files or work run were created")
      Files.exists(destination) shouldBe false
      Files.exists(root.resolve("work")) shouldBe false
    }
  }

  private def _refresh_package(root: Path): Path = {
    val destination = Files.createDirectory(root.resolve("package"))
    val source = Files.createDirectory(destination.resolve("source"))
    Files.createDirectory(source.resolve("project.dox"))
    val products = Vector(_item("article-pdf", "ja", "ja.pdf", "", true), _item("article-pdf", "en", "en.pdf", "", true))
    val declaration = Json.obj("schema" -> Json.fromString("cozy.document-package.v1"), "project" -> Json.fromString("project.dox"), "products" -> Json.fromValues(products))
    Files.writeString(source.resolve("cozy-package.yaml"), declaration.noSpaces)
    destination
  }

  private def _item(product: String, locale: String, destination: String, candidate: String, existed: Boolean): Json =
    Json.obj("product" -> Json.fromString(product), "locale" -> Json.fromString(locale),
      "destination" -> Json.fromString(destination), "candidate" -> Json.fromString(candidate), "previouslyExisted" -> Json.fromBoolean(existed))

  private def _receipt(source: Path, run: Path, root: Path, items: Vector[Json]): Unit = {
    val declaration = Json.obj("schema" -> Json.fromString("cozy.document-package.v1"), "products" -> Json.fromValues(items))
    val receipt = Json.obj("schema" -> Json.fromString("cozy.package-refresh.v2"), "package" -> Json.fromString(root.toRealPath().toString), "items" -> Json.fromValues(items))
    Files.writeString(source.resolve("cozy-package.yaml"), declaration.noSpaces)
    Files.writeString(run.resolve("package-refresh.json"), receipt.noSpaces)
  }

  private def _root(): Path = {
    val base = java.nio.file.Paths.get("target", "cozy-package-spec")
    Files.createDirectories(base)
    Files.createTempDirectory(base, "case-").toAbsolutePath
  }
}
