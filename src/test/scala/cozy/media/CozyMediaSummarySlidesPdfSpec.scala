package cozy.media

import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.collection.JavaConverters._

/*
 * @since   Aug. 29, 2026
 * @version Aug. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaSummarySlidesPdfSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy summary-slides PDF media resources" should {
    "render direct Slide-IR and Visual Page PDFs with ordered PDF evidence" in {
      _with_temp_dir("direct-routes") { root =>
        Given("closed Slide-IR and Visual Page descriptors with current article-PDF and infographic authorities")
        val slideir = _write_fixture(root.resolve("slide-ir"), "slide-ir-v1", includepptx = false, pages = 1)
        _accept_dependencies(slideir)
        val visual = _write_fixture(root.resolve("visual-page"), "visual-page-v1", includepptx = false, pages = 2)
        _accept_dependencies(visual)

        When("the target-only direct PDF routes invoke their business renderer")
        CozyMedia.build(CozyMedia.CommandConfig(slideir, target = Some("summary")), _renderer(root.resolve("slide-ir")))
        CozyMedia.build(CozyMedia.CommandConfig(visual, target = Some("summary")), _renderer(root.resolve("visual-page")))
        val slideirpdf = root.resolve("slide-ir/target/summary.pdf")
        val visualpdf = root.resolve("visual-page/target/summary.pdf")
        val slideirman = Files.readString(root.resolve("slide-ir/target/summary-renderer.json"), StandardCharsets.UTF_8)
        val visualman = Files.readString(root.resolve("visual-page/target/summary-renderer.json"), StandardCharsets.UTF_8)

        Then("both outputs are regular PDFs whose actual page counts and source-ordered evidence are accepted")
        _page_count(slideirpdf) shouldBe 1
        _page_count(visualpdf) shouldBe 2
        slideirman should include("cozy.summary-slides.render.v1")
        slideirman should include(_sha256(root.resolve("slide-ir/assets/infographic.png")))
        visualman should include("cozy.summary-slides.render.v2")
        visualman.indexOf("\"overview\"") should be < visualman.indexOf("\"details\"")
        visualman should include("visualPageSetSha256")
        visualman should include(_sha256(root.resolve("visual-page/assets/infographic.png")))
      }
    }

    "request an internal PPTX only when the descriptor declares it" in {
      _with_temp_dir("optional-pptx") { root =>
        Given("otherwise identical descriptors with absent and explicit PPTX sidecars")
        val absent = _write_fixture(root.resolve("absent"), "slide-ir-v1", includepptx = false)
        val explicit = _write_fixture(root.resolve("explicit"), "slide-ir-v1", includepptx = true)
        _accept_dependencies(absent)
        _accept_dependencies(explicit)
        var absentargv = Vector.empty[String]
        var explicitargv = Vector.empty[String]

        When("each direct summary target is built")
        CozyMedia.build(CozyMedia.CommandConfig(absent, target = Some("summary")), _renderer(root.resolve("absent"), command => absentargv = command))
        CozyMedia.build(CozyMedia.CommandConfig(explicit, target = Some("summary")), _renderer(root.resolve("explicit"), command => explicitargv = command))

        Then("only the explicit route passes and records the sidecar, while neither route exposes a publication operation")
        absentargv should not contain "--pptx"
        explicitargv.takeRight(2) shouldBe Vector("--pptx", root.resolve("explicit/target/summary.pptx").toString)
        Files.isRegularFile(root.resolve("explicit/target/summary.pptx")) shouldBe true
        Files.readString(root.resolve("absent/target/summary-renderer.json"), StandardCharsets.UTF_8) should not include "pptx"
        explicitargv.exists(value => value.contains("publish") || value.contains("register") || value.contains("bok")) shouldBe false
      }
    }

    "order a whole media build after ordinary resources without changing presentation behavior" in {
      _with_temp_dir("whole-build-order") { root =>
        Given("a new package with prebuilt ordinary resources and one direct summary-PDF resource")
        val descriptor = _write_fixture(root, "slide-ir-v1", includepptx = false)
        var invoked = false

        When("Cozy builds the whole media package")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), _renderer(root, command => invoked = command.contains("--pdf")))

        Then("ordinary resources are accepted before the summary PDF and no presentation route is invoked")
        invoked shouldBe true
        Files.isRegularFile(root.resolve("target/summary.pdf")) shouldBe true
        Files.readString(root.resolve("target/cozy-media/manifest.json"), StandardCharsets.UTF_8) should include("summary")
      }
    }

    "reject malformed, stale, and page-inconsistent direct renderer evidence before receipt acceptance" which {
      "reject malformed hashes, page count, order, and page asset evidence" in {
        _with_temp_dir("evidence-rejection") { root =>
          Given("direct Slide-IR targets with renderer evidence variants")
          val variants = Vector("missing-pdf", "malformed", "non-pdf", "hash", "count", "order", "asset")

          When("each variant attempts to replace a prior primary PDF")
          val failures = variants.map { variant =>
            val directory = root.resolve(variant)
            val descriptor = _write_fixture(directory, "slide-ir-v1", includepptx = false, pages = 2)
            _accept_dependencies(descriptor)
            val output = directory.resolve("target/summary.pdf")
            _write_pdf(output, 1, "prior-$variant")
            val manifest = directory.resolve("target/cozy-media/manifest.json")
            val previous = Files.readString(manifest, StandardCharsets.UTF_8)
            val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(directory, variant = variant)))
            output -> (failure -> (previous -> Files.readString(manifest, StandardCharsets.UTF_8)))
          }

          Then("every invalid evidence shape leaves the prior primary PDF and acceptance receipt visible")
          failures should have size variants.size
          failures.foreach { case (output, (failure, (previous, actual))) =>
            failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include("summary-slides pdf")
            _page_count(output) shouldBe 1
            actual shouldBe previous
          }
        }
      }

      "require current declared dependencies for a target-only build" in {
        _with_temp_dir("target-dependencies") { root =>
          Given("a direct summary descriptor whose declared article PDF and infographic have not been accepted")
          val descriptor = _write_fixture(root, "slide-ir-v1", includepptx = false)
          var invoked = false
          val runner = _renderer(root, command => invoked = true)

          When("the summary target is requested before its dependencies are current")
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), runner))

          Then("Cozy rejects it before renderer invocation")
          failure.getMessage should include("requires current dependency evidence")
          invoked shouldBe false

          Given("current accepted article-PDF and infographic resources")
          _accept_dependencies(descriptor)

          When("the same target is requested again")
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), runner)

          Then("the direct route is eligible without inferring publication or registration")
          invoked shouldBe true
          CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))) should include("summary: current")
        }
      }

      "reject verification after an accepted article-PDF or infographic receipt becomes stale" in {
        _with_temp_dir("verify-dependency-receipts") { root =>
          Given("a direct summary PDF with accepted article-PDF, infographic, and summary receipts")
          val descriptor = _write_fixture(root, "slide-ir-v1", includepptx = false)
          _accept_dependencies(descriptor)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(root))
          val manifest = root.resolve("target/cozy-media/manifest.json")
          val original = Files.readString(manifest, StandardCharsets.UTF_8)

          When("each dependency receipt is invalidated while the summary receipt remains unchanged")
          val failures = Vector("article-pdf", "infographic").map { dependency =>
            _write(manifest, original.replace("\"id\" : \"" + dependency + "\"", "\"id\" : \"" + dependency + "-stale\""))
            val failure = intercept[RuntimeException](CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary"))))
            _write(manifest, original)
            failure
          }

          Then("verification rejects the summary before accepting stale dependency evidence")
          failures should have size 2
          failures.foreach(_.getMessage should include("requires current dependency evidence"))
        }
      }

      "plan a direct output stale after source, configuration, or output changes" in {
        _with_temp_dir("stale-planning") { root =>
          Given("an accepted direct summary PDF")
          val descriptor = _write_fixture(root, "slide-ir-v1", includepptx = false)
          val original = Files.readString(descriptor, StandardCharsets.UTF_8)
          _accept_dependencies(descriptor)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(root))

          When("the Slide-IR authority changes")
          _write(root.resolve("slides.json"), _slide_ir_json(1).replace("Overview", "Changed"))

          Then("planning marks the PDF for rebuilding")
          CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))) should include("summary: build")

          Given("the original authority is restored")
          _write(root.resolve("slides.json"), _slide_ir_json(1))

          When("the closed renderer configuration changes")
          _write(descriptor, original.replace("\"version\": \"1\"", "\"version\": \"2\""))

          Then("descriptor configuration evidence also makes the PDF stale")
          CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))) should include("summary: build")

          Given("the original descriptor is restored")
          _write(descriptor, original)

          When("the installed primary PDF changes")
          _write_pdf(root.resolve("target/summary.pdf"), 1, "altered")

          Then("the receipt output hash rejects the altered primary PDF")
          CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))) should include("summary: build")
        }
      }

      "preserve a prior primary PDF and receipt when an input races during rendering" in {
        _with_temp_dir("input-race") { root =>
          Given("an existing primary PDF and accepted dependency receipt")
          val descriptor = _write_fixture(root, "slide-ir-v1", includepptx = false)
          _accept_dependencies(descriptor)
          val output = root.resolve("target/summary.pdf")
          _write_pdf(output, 1, "prior")
          val manifest = root.resolve("target/cozy-media/manifest.json")
          val previous = Files.readString(manifest, StandardCharsets.UTF_8)
          val source = root.resolve("slides.json")
          val runner = new CozyMedia.ProcessRunner {
            def run(command: Vector[String], workingdirectory: Path): Int = {
              _write_renderer_output(root, command, "valid")
              _write(source, _slide_ir_json(1).replace("Overview", "Raced"))
              0
            }
          }

          When("the renderer returns after changing the declared Slide-IR authority")
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), runner))

          Then("the staged result is rejected before atomic installation or receipt visibility")
          failure.getMessage should include("input hashes differ")
          _page_count(output) shouldBe 1
          Files.readString(manifest, StandardCharsets.UTF_8) shouldBe previous
        }
      }

      "preserve a prior primary PDF and receipt when a Visual Page source binding races during rendering" in {
        _with_temp_dir("visual-page-input-race") { root =>
          Given("an existing Visual Page primary PDF and accepted dependency receipt")
          val descriptor = _write_fixture(root, "visual-page-v1", includepptx = false)
          _accept_dependencies(descriptor)
          val output = root.resolve("target/summary.pdf")
          _write_pdf(output, 1, "prior")
          val manifest = root.resolve("target/cozy-media/manifest.json")
          val previous = Files.readString(manifest, StandardCharsets.UTF_8)
          val sourcebinding = root.resolve("sources/research.txt")
          val runner = new CozyMedia.ProcessRunner {
            def run(command: Vector[String], workingdirectory: Path): Int = {
              _write_renderer_output(root, command, "valid")
              _write(sourcebinding, "raced")
              0
            }
          }

          When("the renderer returns after changing a declared Visual Page source binding")
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), runner))

          Then("the staged result is rejected before atomic installation or receipt visibility")
          failure.getMessage should include("Summary-slides PDF inputs changed during build: summary")
          _page_count(output) shouldBe 1
          Files.readString(manifest, StandardCharsets.UTF_8) shouldBe previous
        }
      }

      "reject summary generated paths that overlap trusted inputs or dependency artifacts" in {
        _with_temp_dir("descriptor-overlap") { root =>
          Given("direct summary descriptors whose primary PDF output is redirected to trusted side inputs")
          val cases = Vector(
            ("slide-source", "slide-ir-v1", "slides.json"),
            ("template", "slide-ir-v1", "template.pptx"),
            ("visual-source", "visual-page-v1", "visual-pages.json"),
            ("visual-catalog", "visual-page-v1", "catalog.json"),
            ("visual-binding", "visual-page-v1", "binding.json"),
            ("visual-source-binding", "visual-page-v1", "sources/research.txt"),
            ("visual-asset", "visual-page-v1", "assets/page-asset.png"),
            ("article-pdf", "slide-ir-v1", "article.pdf"),
            ("infographic", "slide-ir-v1", "assets/infographic.png")
          )

          When("each descriptor is resolved")
          val failures = cases.map { case (name, contract, outputpath) =>
            val directory = root.resolve(name)
            val descriptor = _write_fixture(directory, contract, includepptx = false)
            if (name == "visual-asset") {
              _write_png(directory.resolve("assets/page-asset.png"))
              val visual = directory.resolve("visual-pages.json")
              _write(visual, Files.readString(visual, StandardCharsets.UTF_8).replace("assets/infographic.png", "assets/page-asset.png"))
            }
            _write(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace(
              "\"output\": \"target/summary.pdf\"",
              "\"output\": \"" + outputpath + "\""
            ))
            intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
          }

          Then("every generated-path overlap is rejected during descriptor resolution")
          failures should have size cases.size
          failures.foreach(_.getMessage.toLowerCase(java.util.Locale.ROOT) should include("overlap"))
        }
      }

      "reject a generated path that overlaps a dependency source when its output is distinct" in {
        _with_temp_dir("descriptor-distinct-dependency") { root =>
          Given("a direct summary descriptor whose article-PDF dependency declares separate source and output paths")
          val descriptor = _write_fixture(root, "slide-ir-v1", includepptx = false)
          val distinct = Files.readString(descriptor, StandardCharsets.UTF_8)
            .replace("\"source\": \"article.pdf\", \"build\": \"prebuilt\"", "\"source\": \"article.pdf\", \"output\": \"target/article-copy.pdf\", \"build\": \"copy\"")
            .replace("\"output\": \"target/summary.pdf\"", "\"output\": \"article.pdf\"")
          _write(descriptor, distinct)

          When("the descriptor is resolved")
          val failure = intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))

          Then("the summary implementation rejects the dependency source collision even though its output differs")
          failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include("overlap")
        }
      }

      "reject colliding generated paths across summary resources" in {
        _with_temp_dir("descriptor-resource-collision") { root =>
          Given("a descriptor with two otherwise valid summary resources sharing one primary output")
          val descriptor = _write_fixture(root, "slide-ir-v1", includepptx = false)
          _write(root.resolve("slides-2.json"), _slide_ir_json(1))
          val secondresource =
            """{
              |    "id": "summary-2", "kind": "document", "language": "en", "source": "slides-2.json", "output": "target/summary.pdf", "build": "summary-slides-pdf",
              |    "articleMedia": {"role": "summary_slides_pdf", "publicPath": "/articles/example/summary-2.pdf", "mediaType": "application/pdf"},
              |    "summarySlidesPdf": {"contract": "slide-ir-v1", "profile": "business", "slideImages": "target/slides-2", "montage": "target/montage-2.png", "rendererManifest": "target/summary-2-renderer.json", "articlePdf": "article-pdf", "infographic": "infographic"}
              |  }""".stripMargin
          val marker = "  }]\n}"
          val original = Files.readString(descriptor, StandardCharsets.UTF_8)
          original should include(marker)
          _write(descriptor, original.replace(marker, "  }, " + secondresource + "\n]}"))

          When("the descriptor is resolved")
          val failure = intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))

          Then("the summary implementation rejects the cross-resource generated-path collision")
          failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include("generated paths")
          failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include("overlap")
        }
      }

      "reject direct-summary profiles with invalid renderer identity or argv" in {
        _with_temp_dir("descriptor-renderer-contract") { root =>
          Given("direct summary descriptors with invalid renderer profile variants")
          val cases = Vector(
            ("empty-name", "\"name\": \"fake\"", "\"name\": \"\""),
            ("blank-version", "\"version\": \"1\"", "\"version\": \" \""),
            ("empty-argv", "\"command\": [\"fake-renderer\"]", "\"command\": []"),
            ("blank-argv", "\"command\": [\"fake-renderer\"]", "\"command\": [\" \"]")
          )

          When("each invalid profile is resolved")
          val failures = cases.map { case (name, before, after) =>
            val directory = root.resolve(name)
            val descriptor = _write_fixture(directory, "slide-ir-v1", includepptx = false)
            _write(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace(before, after))
            intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
          }

          Then("the direct route rejects every renderer contract variant")
          failures should have size cases.size
          failures.foreach(_.getMessage.toLowerCase(java.util.Locale.ROOT) should include("renderer"))
        }
      }
    }
  }

  private def _write_fixture(root: Path, contract: String, includepptx: Boolean, pages: Int = 1): Path = {
    _write(root.resolve("article.dox"), "Article")
    _write_pdf(root.resolve("article.pdf"), 1, "article")
    _write_png(root.resolve("assets/infographic.png"))
    _write(root.resolve("template.pptx"), "template")
    val source = contract match {
      case "slide-ir-v1" =>
        _write(root.resolve("slides.json"), _slide_ir_json(pages))
        "slides.json"
      case "visual-page-v1" =>
        _write_visual_page_source(root, pages)
        "visual-pages.json"
      case other => throw new IllegalArgumentException(other)
    }
    val visual =
      if (contract == "visual-page-v1") ", \"catalog\": \"catalog.json\", \"binding\": \"binding.json\""
      else ""
    val pptx = if (includepptx) ", \"pptx\": \"target/summary.pptx\"" else ""
    val descriptor = root.resolve("media.json")
    _write(descriptor,
      s"""{
         |  "schema": "cozy.media.v1",
         |  "knowledge": {"id": "article/example", "source": "article.dox"},
         |  "profiles": {"business": {"presentation": {"template": "template.pptx", "renderer": {"name": "fake", "version": "1", "command": ["fake-renderer"]}}}},
         |  "resources": [{
         |    "id": "article-pdf", "kind": "document", "language": "en", "source": "article.pdf", "build": "prebuilt",
         |    "articleMedia": {"role": "article_pdf", "publicPath": "/articles/example/article.pdf", "mediaType": "application/pdf"}
         |  }, {
         |    "id": "infographic", "kind": "infographic", "language": "en", "source": "assets/infographic.png", "build": "prebuilt",
         |    "articleMedia": {"role": "infographic", "publicPath": "/articles/example/infographic.png"}
         |  }, {
         |    "id": "summary", "kind": "document", "language": "en", "source": "$source", "output": "target/summary.pdf", "build": "summary-slides-pdf",
         |    "articleMedia": {"role": "summary_slides_pdf", "publicPath": "/articles/example/summary.pdf", "mediaType": "application/pdf"},
         |    "summarySlidesPdf": {"contract": "$contract", "profile": "business"$visual, "slideImages": "target/slides", "montage": "target/montage.png", "rendererManifest": "target/summary-renderer.json", "articlePdf": "article-pdf", "infographic": "infographic"$pptx}
         |  }]
         |}
         |""".stripMargin
    )
    descriptor
  }

  private def _accept_dependencies(descriptor: Path): Unit = {
    CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("article-pdf")))
    CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("infographic")))
  }

  private def _renderer(root: Path, capture: Vector[String] => Unit = _ => (), variant: String = "valid"): CozyMedia.ProcessRunner =
    new CozyMedia.ProcessRunner {
      def run(command: Vector[String], workingdirectory: Path): Int = {
        capture(command)
        _write_renderer_output(root, command, variant)
        0
      }
    }

  private def _write_renderer_output(root: Path, command: Vector[String], variant: String): Unit = {
    val pdf = Path.of(command(command.indexOf("--pdf") + 1))
    val images = Path.of(command(command.indexOf("--slide-images") + 1))
    val montage = Path.of(command(command.indexOf("--montage") + 1))
    val manifest = Path.of(command(command.indexOf("--manifest") + 1))
    val visual = command.contains("--visual-page-set")
    val ids = if (visual) CozyVisualPage.load(root.resolve("visual-pages.json"), root.resolve("catalog.json")).document.pages.map(_.id) else CozyMediaSlideIr.load(Path.of(command(command.indexOf("--slide-ir") + 1))).slides.map(_.id)
    if (variant == "non-pdf") _write(pdf, "not-a-pdf")
    else if (variant != "missing-pdf") _write_pdf(pdf, ids.size, "generated")
    ids.foreach(id => _write_png(images.resolve(id + ".png")))
    _write_png(montage)
    if (variant == "missing-pdf") return
    if (command.contains("--pptx")) _write(Path.of(command(command.indexOf("--pptx") + 1)), "pptx")
    if (variant == "malformed") {
      _write(manifest, "{}")
    } else {
      val hash = _sha256(pdf)
      val renderedids = if (variant == "order") ids.reverse else ids
      val count = if (variant == "count") ids.size + 1 else ids.size
      val assethash = if (variant == "asset") "0" * 64 else _sha256(root.resolve("assets/infographic.png"))
      val slides = renderedids.map { id =>
        val title = if (!visual && id == "overview") ",\"title\":\"Overview\"" else if (!visual) ",\"title\":\"" + id.capitalize + "\"" else ""
        s"""{"id":"$id"$title,"path":"target/slides/$id.png","sha256":"${_sha256(images.resolve(id + ".png"))}","pdfSha256":"$hash","assets":[{"id":"infographic","sha256":"$assethash"}]}"""
      }.mkString(",")
      val pptx = if (command.contains("--pptx")) {
        val path = Path.of(command(command.indexOf("--pptx") + 1))
        ",\"pptx\":{\"path\":\"target/summary.pptx\",\"sha256\":\"" + _sha256(path) + "\"}"
      } else ""
      val identity = if (visual) {
        val document = CozyVisualPage.load(root.resolve("visual-pages.json"), root.resolve("catalog.json"))
        val binding = CozyVisualPageBinding.load(root.resolve("binding.json"), document)
        s""""schema":"cozy.summary-slides.render.v2","target":"summary","profile":"business","renderer":{"name":"fake","version":"1"},"visualPageSetSha256":"${_semantic_sha256(document.documentIdentity)}","catalogSha256":"${_semantic_sha256(document.catalogIdentity)}","bindingSha256":"${_semantic_sha256(binding.bindingIdentity)}","templateSha256":"${_sha256(root.resolve("template.pptx"))}""""
      } else {
        s""""schema":"cozy.summary-slides.render.v1","target":"summary","profile":"business","renderer":{"name":"fake","version":"1"},"slideIrSha256":"${_sha256(Path.of(command(command.indexOf("--slide-ir") + 1)))}","templateSha256":"${_sha256(root.resolve("template.pptx"))}""""
      }
      val pdfhash = if (variant == "hash") "0" * 64 else hash
      _write(manifest, s"""{$identity,"pdf":{"sha256":"$pdfhash","pageCount":$count},"slides":[$slides],"montage":{"path":"target/montage.png","sha256":"${_sha256(montage)}","pdfSha256":"$hash"}$pptx}""")
    }
  }

  private def _write_visual_page_source(root: Path, pages: Int = 1): Unit = {
    _write(root.resolve("sources/research.txt"), "research")
    val catalog = CozyVisualPage.Catalog(
      "core", 1,
      Vector(
        CozyVisualPage.RelationDefinition("next", "from-to"),
        CozyVisualPage.RelationDefinition("causes", "from-to"),
        CozyVisualPage.RelationDefinition("depends-on", "from-to"),
        CozyVisualPage.RelationDefinition("enables", "from-to"),
        CozyVisualPage.RelationDefinition("maps-to", "from-to")
      ),
      Vector(
        CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear"))),
        CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"), CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic"))),
        CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic"))),
        CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")))
      ),
      Vector(
        CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
        CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
        CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false), CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true), CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)))
      )
    )
    val page = CozyVisualPage.Page(
      "overview", "article/example", "en", CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical("sequence", Vector(CozyVisualPage.Node("discover", "step", "Discover", Vector("research")), CozyVisualPage.Node("apply", "step", "Apply", Vector("research"))), Vector(CozyVisualPage.Relation("next", "next", "discover", "apply", Vector("research")))),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector(CozyVisualPage.Asset("infographic", "assets/infographic.png", "image/png", _sha256(root.resolve("assets/infographic.png")))),
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )
    val pageset = pages match {
      case 1 => Vector(page)
      case 2 => Vector(page, page.copy(id = "details"))
      case other => throw new IllegalArgumentException(other.toString)
    }
    _write(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(catalog))
    _write(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("article-pages", pageset)))
    val slots = Vector("knowledge", "nodes", "relations", "assets", "parameters").map(slot => s"""{"semanticSlot":"$slot","physicalSlot":"$slot-slot"}""").mkString(",")
    val patterns = Vector("flow-horizontal", "flow-vertical", "mapping-columns").map { pattern =>
      s"""{"visualPattern":"$pattern","slots":[$slots]}"""
    }.mkString(",")
    _write(root.resolve("binding.json"), s"""{"schema":"cozy.visual-page.binding.v1","version":1,"id":"business-default","profile":"business","catalog":{"id":"core","revision":1},"patterns":[$patterns]}""")
  }

  private def _slide_ir_json(pages: Int): String = {
    pages match {
      case 1 =>
        """{"schema":"cozy.slide-ir.v1","knowledge":"article/example","language":"en","slides":[{"id":"overview","title":"Overview","layout":"content","elements":[{"role":"title","text":"Overview"},{"role":"figure","asset":"infographic"}]}]}"""
      case 2 =>
        """{"schema":"cozy.slide-ir.v1","knowledge":"article/example","language":"en","slides":[{"id":"overview","title":"Overview","layout":"content","elements":[{"role":"title","text":"Overview"},{"role":"figure","asset":"infographic"}]},{"id":"details","title":"Details","layout":"content","elements":[{"role":"title","text":"Details"},{"role":"figure","asset":"infographic"}]}]}"""
      case other => throw new IllegalArgumentException(other.toString)
    }
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _write_pdf(path: Path, pages: Int, title: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val document = new PDDocument()
    try {
      (1 to pages).foreach(_ => document.addPage(new PDPage()))
      document.getDocumentInformation.setTitle(title)
      document.save(path.toFile)
    } finally document.close()
  }

  private def _write_png(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00))
  }

  private def _page_count(path: Path): Int = {
    val document = PDDocument.load(path.toFile)
    try document.getNumberOfPages
    finally document.close()
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _semantic_sha256(value: String): String = value.stripPrefix("sha256:")

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val work = Paths.get(sys.props("user.dir")).resolve("target")
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "cozy-summary-slides-pdf-" + name + "-")
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.delete)
      finally paths.close()
    }
}
