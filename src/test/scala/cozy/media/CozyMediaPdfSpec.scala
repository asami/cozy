package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import io.circe.parser.parse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 29, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaPdfSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy article-PDF media resources" should {
    "accept only the explicit article-PDF grammar" in {
      _with_temp_dir("grammar") { root =>
        Given("a direct SmartDox article source and a closed article PDF descriptor")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor())

        When("Cozy resolves the descriptor")
        val resource = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)).resources.head.resource

        Then("the article_pdf role and its exact renderer configuration are retained")
        resource.articleMedia.map(_.role) shouldBe Some("article_pdf")
        resource.articlePdf.map(_.latexFormat) shouldBe Some("business")
        resource.articlePdf.map(_.infographic) shouldBe Some("infographic-ja")
      }
    }

    "reject malformed article-PDF role, locale, and renderer descriptors" in {
      _with_temp_dir("malformed-grammar") { root =>
        Given("a direct SmartDox article source and three malformed article PDF descriptors")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        val descriptor = root.resolve("media.json")
        val invalids = Vector(
          _descriptor().replace("article_pdf", "article-pdf"),
          _descriptor().replace("\"language\": \"ja\"", "\"language\": \"fr\""),
          _descriptor().replace("\"version\": \"2.4.18-SNAPSHOT\"", "\"version\": \"2.4.18-SNAPSHOT\", \"extra\": true")
        )

        When("Cozy resolves each malformed descriptor")
        val errors = invalids.map { value =>
          _write(descriptor, value)
          intercept[Exception](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        }

        Then("each malformed descriptor is rejected with a diagnostic")
        errors should have size invalids.size
        errors.map(_.getMessage).foreach(_ should not be empty)
      }
    }

    "reject an article-PDF output that escapes the descriptor root before renderer invocation" in {
      _with_temp_dir("output-escape") { root =>
        Given("an article-PDF descriptor whose declared output traverses outside its descriptor root")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        val escaped = root.getParent.resolve(root.getFileName.toString + "-escaped.pdf")
        _write_pdf(escaped, "previous")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor(articleoutput = "../" + escaped.getFileName.toString))
        var rendererinvoked = false
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            rendererinvoked = true
            _write_pdf(Path.of(command(command.indexOf("--output") + 1)), "unexpected")
            0
          }
        }

        When("Cozy resolves the build plan before rendering")
        val failure = intercept[Exception](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

        Then("descriptor validation rejects the escape without invoking the renderer or mutating the outside destination")
        failure.getMessage should include("Article PDF output escapes descriptor root")
        rendererinvoked shouldBe false
        Files.readString(escaped, StandardCharsets.US_ASCII) shouldBe "%PDF-1.7\nprevious"
      }
    }

    "reject an article-PDF output with a symlinked in-root parent before renderer invocation" in {
      _with_temp_dir("output-symlink-ancestor") { root =>
        Given("an article-PDF descriptor whose lexically in-root output parent is a symlink to an external sibling directory")
        val external = root.resolveSibling(root.getFileName.toString + "-external")
        try {
          _write(root.resolve("knowledge/article.dox"), "article")
          _write_infographic(root)
          _write_pdf(external.resolve("article-ja.pdf"), "external-previous")
          Files.createDirectories(root.resolve("target"))
          Files.createSymbolicLink(root.resolve("target/cozy-media"), external)
          val descriptor = root.resolve("media.json")
          _write(descriptor, _descriptor())
          var rendererinvoked = false
          val runner = new CozyMedia.ProcessRunner {
            def run(command: Vector[String], workingdirectory: Path): Int = {
              rendererinvoked = true
              _write_pdf(Path.of(command(command.indexOf("--output") + 1)), "unexpected")
              0
            }
          }

          When("Cozy resolves the build plan before rendering")
          val failure = intercept[Exception](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

          Then("descriptor validation rejects the symlinked ancestor without invoking the renderer or replacing the external previous PDF")
          failure.getMessage should include("Article PDF output has a symlinked ancestor below descriptor root")
          rendererinvoked shouldBe false
          Files.readString(external.resolve("article-ja.pdf"), StandardCharsets.US_ASCII) shouldBe "%PDF-1.7\nexternal-previous"
        } finally _delete(external)
      }
    }

    "reject missing, unknown, wrong-kind, wrong-role, and cross-locale infographic bindings" in {
      _with_temp_dir("infographic-binding") { root =>
        Given("a direct Japanese article source and a declared Japanese infographic authority")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        Files.createDirectories(root.resolve("infographic/nonregular.svg"))
        _write(root.resolve("infographic/symlink-target.svg"), "symlink-target")
        Files.createSymbolicLink(root.resolve("infographic/symlink.svg"), Paths.get("symlink-target.svg"))
        val descriptor = root.resolve("media.json")
        val invalids = Vector(
          (_descriptor(articleinfographic = "missing-infographic"), "Article PDF infographic authority is not declared"),
          (_descriptor(articleinfographic = "article-pdf-ja"), "Article PDF infographic authority must be distinct"),
          (_descriptor(infographickind = "image"), "Article PDF infographic authority must have kind infographic"),
          (_descriptor(infographiclanguage = "en"), "Article PDF infographic authority language must match"),
          (_descriptor(infographicsource = None), "Article PDF infographic authority requires a source"),
          (_descriptor(infographicsource = Some("infographic/nonregular.svg")), "Article PDF infographic authority source must be a direct regular non-symlink file"),
          (_descriptor(infographicsource = Some("infographic/symlink.svg")), "Article PDF infographic authority source must be a direct regular non-symlink file")
        )

        When("Cozy resolves each invalid article-PDF authority binding")
        val errors = invalids.map { case (value, _) =>
          _write(descriptor, value)
          intercept[Exception](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        }

        Then("each absent or incompatible infographic authority is rejected with its contract diagnostic before rendering")
        errors should have size invalids.size
        errors.zip(invalids).foreach { case (error, (_, diagnostic)) =>
          error.getMessage should include(diagnostic)
        }
      }
    }

    "reject an authority resource whose decoded articleMedia role is not infographic" in {
      _with_temp_dir("infographic-wrong-role") { root =>
        Given("a direct article source and a syntactically valid authority resource with an article_pdf role")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        val decoded = _decode_descriptor(_descriptor(infographicrole = "article_pdf"))
        val articlepdf = decoded.resources.find(_.id == "article-pdf-ja").get

        When("Cozy validates the decoded article-PDF authority binding")
        val failure = intercept[Exception](CozyMediaPdf.validateDescriptor(decoded, articlepdf, root))

        Then("the decoded non-infographic role is rejected with the role-specific diagnostic")
        decoded.resources.find(_.id == "infographic-ja").flatMap(_.articleMedia).map(_.role) shouldBe Some("article_pdf")
        failure.getMessage should include("Article PDF infographic authority requires articleMedia.role infographic")
      }
    }

    "keep already-prebuilt summary-slides PDF declarations on the compatible prebuilt route" in {
      _with_temp_dir("summary-slides-declaration") { root =>
        Given("a direct article source and an explicitly declared summary-slides PDF resource")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write(root.resolve("summary-slides.pdf"), "%PDF-1.7\ndeclared")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _summary_slides_pdf_descriptor())

        When("Cozy plans the summary-slides PDF resource")
        val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor))
        val resource = plan.resources.head

        Then("the exact role is decoded without article-PDF conversion configuration")
        resource.resource.articleMedia.map(_.role) shouldBe Some("summary_slides_pdf")
        resource.resource.articlePdf shouldBe None

        And("planning uses generic prebuilt adoption without invoking a converter")
        resource.action shouldBe CozyMedia.Action.AdoptPrebuilt
      }
    }

    "reject summary-slides PDF declarations on unsupported generic conversion routes" in {
      _with_temp_dir("summary-slides-generic-routes") { root =>
        Given("a direct article source and syntactically valid summary-slides PDF descriptors for copy, SVG conversion, and video delegation")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_pdf(root.resolve("summary-slides.pdf"), "declared")
        _write(root.resolve("video/project.json"), "video")
        val descriptor = root.resolve("media.json")

        When("Cozy resolves each non-prebuilt summary-slides PDF route")
        val errors = Vector("copy", "svg-to-png", "video-project").map { build =>
          _write(descriptor, _summary_slides_pdf_descriptor(build))
          intercept[Exception](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        }

        Then("every generic route is rejected before any converter or delegated build is invoked")
        errors should have size 3
        errors.map(_.getMessage).foreach(_ should include("summary_slides_pdf requires build: prebuilt or summary-slides-pdf"))
      }
    }

    "append the accepted PDF operands to the configured exact argv" in {
      _with_temp_dir("argv") { root =>
        Given("a Japanese article PDF target and a recording renderer")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor())
        var recordedcommand = Vector.empty[String]
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            recordedcommand = command
            _write_pdf(Path.of(command(command.indexOf("--output") + 1)), "new")
            0
          }
        }

        When("Cozy builds the resource")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)

        Then("the renderer receives only its configured argv followed by source, staged output, locale, and canonical format")
        val staged = recordedcommand(4)
        recordedcommand shouldBe Vector("smartdox", "pdf", root.resolve("knowledge/article.dox").toString, "--output", staged, "--locale", "ja", "--latex-format", "business")
        Path.of(staged).getParent shouldBe root.resolve("target/cozy-media")
        Path.of(staged).getFileName.toString should startWith(".cozy-media-pdf-")
        recordedcommand should not contain root.resolve("infographic/article-ja.svg").toString
        Files.readString(root.resolve("target/cozy-media/article-ja.pdf"), StandardCharsets.US_ASCII) should include("%PDF-")
      }
    }

    "preserve prior output and receipt visibility when rendering fails" in {
      _with_temp_dir("failure") { root =>
        Given("an existing PDF and manifest before a failing render")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        _write_pdf(root.resolve("target/cozy-media/article-ja.pdf"), "previous")
        val manifest = root.resolve("target/cozy-media/manifest.json")
        _write(manifest, "previous-receipt")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor())
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = 9
        }

        When("the configured renderer exits unsuccessfully")
        val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

        Then("the former output and receipt bytes remain visible")
        failure.getMessage should include("exit=9")
        Files.readString(root.resolve("target/cozy-media/article-ja.pdf"), StandardCharsets.US_ASCII) should include("previous")
        Files.readString(manifest, StandardCharsets.UTF_8) shouldBe "previous-receipt"
      }
    }

    "preserve prior output and receipt bytes when staged output is not a PDF" in {
      _with_temp_dir("invalid-staged-output") { root =>
        Given("an existing PDF and manifest before a renderer writes a regular non-PDF staged file")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        val output = root.resolve("target/cozy-media/article-ja.pdf")
        _write_pdf(output, "previous-invalid-output")
        val manifest = root.resolve("target/cozy-media/manifest.json")
        _write(manifest, "previous-invalid-receipt")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor())
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            _write(Path.of(command(command.indexOf("--output") + 1)), "not-a-pdf")
            0
          }
        }

        When("the renderer exits successfully with a non-PDF staged file")
        val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

        Then("the renderer output is rejected and prior output and receipt bytes remain unchanged")
        failure.getMessage should include("did not create a direct regular PDF output")
        Files.readString(output, StandardCharsets.US_ASCII) shouldBe "%PDF-1.7\nprevious-invalid-output"
        Files.readString(manifest, StandardCharsets.UTF_8) shouldBe "previous-invalid-receipt"
      }
    }

    "preserve prior output and receipt bytes when renderer input changes before return" in {
      _with_temp_dir("input-race") { root =>
        Given("an existing PDF and manifest before a renderer changes the article source")
        val source = root.resolve("knowledge/article.dox")
        _write(source, "article")
        _write_infographic(root)
        val output = root.resolve("target/cozy-media/article-ja.pdf")
        _write_pdf(output, "previous-race-output")
        val manifest = root.resolve("target/cozy-media/manifest.json")
        _write(manifest, "previous-race-receipt")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor())
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            _write_pdf(Path.of(command(command.indexOf("--output") + 1)), "raced")
            _write(source, "article-changed-during-render")
            0
          }
        }

        When("the renderer returns after changing a declared input")
        val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

        Then("the input race is rejected and prior output and receipt bytes remain unchanged")
        failure.getMessage should include("inputs changed during build")
        Files.readString(output, StandardCharsets.US_ASCII) shouldBe "%PDF-1.7\nprevious-race-output"
        Files.readString(manifest, StandardCharsets.UTF_8) shouldBe "previous-race-receipt"
      }
    }

    "preserve prior output and receipt bytes when the infographic authority changes during rendering" in {
      _with_temp_dir("infographic-input-race") { root =>
        Given("an existing PDF and manifest before a renderer changes the selected infographic source")
        _write(root.resolve("knowledge/article.dox"), "article")
        val infographic = root.resolve("infographic/article-ja.svg")
        _write_infographic(root)
        val output = root.resolve("target/cozy-media/article-ja.pdf")
        _write_pdf(output, "previous-infographic-race-output")
        val manifest = root.resolve("target/cozy-media/manifest.json")
        _write(manifest, "previous-infographic-race-receipt")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor())
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            _write_pdf(Path.of(command(command.indexOf("--output") + 1)), "raced")
            _write(infographic, "infographic-changed-during-render")
            0
          }
        }

        When("the renderer returns after changing the explicit infographic authority")
        val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

        Then("the authority race is rejected and prior output and receipt bytes remain unchanged")
        failure.getMessage should include("inputs changed during build")
        Files.readString(output, StandardCharsets.US_ASCII) shouldBe "%PDF-1.7\nprevious-infographic-race-output"
        Files.readString(manifest, StandardCharsets.UTF_8) shouldBe "previous-infographic-race-receipt"
      }
    }

    "become stale after an accepted infographic authority source changes" in {
      _with_temp_dir("infographic-currentness") { root =>
        Given("an accepted article PDF resource with an explicit infographic authority")
        _write(root.resolve("knowledge/article.dox"), "article")
        val infographic = root.resolve("infographic/article-ja.svg")
        _write_infographic(root)
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor())
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            _write_pdf(Path.of(command(command.indexOf("--output") + 1)), "accepted")
            0
          }
        }
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)

        When("the accepted infographic source bytes change")
        _write(infographic, "infographic-changed")

        Then("planning reports the article PDF stale through existing receipt-v2 evidence")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-pdf-ja: build")
      }
    }

    "become stale after bound source, configuration, or output changes" in {
      _with_temp_dir("currentness") { root =>
        Given("an accepted article PDF resource")
        _write(root.resolve("knowledge/article.dox"), "article")
        _write_infographic(root)
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor())
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            _write_pdf(Path.of(command(command.indexOf("--output") + 1)), "accepted")
            0
          }
        }
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)

        When("the bound article source changes")
        _write(root.resolve("knowledge/article.dox"), "changed")

        Then("planning reports the PDF stale")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-pdf-ja: build")

        When("the closed renderer configuration changes")
        _write(descriptor, _descriptor(version = "2.4.19-SNAPSHOT"))

        Then("configuration evidence also makes the output stale")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-pdf-ja: build")

        When("the declared PDF bytes change")
        _write_pdf(root.resolve("target/cozy-media/article-ja.pdf"), "altered")

        Then("the manifest output hash no longer accepts it")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-pdf-ja: build")
      }
    }
  }

  private def _descriptor(
    version: String = "2.4.18-SNAPSHOT",
    articleoutput: String = "target/cozy-media/article-ja.pdf",
    articleinfographic: String = "infographic-ja",
    infographickind: String = "infographic",
    infographicrole: String = "infographic",
    infographiclanguage: String = "ja",
    infographicsource: Option[String] = Some("infographic/article-ja.svg")
  ): String = {
    val source = infographicsource.map(value => "\"source\": \"" + value + "\", ").getOrElse("")
    val mediatype = if (infographicrole == "article_pdf") ", \"mediaType\": \"application/pdf\"" else ""
    s"""{
       |  "schema": "cozy.media.v1",
       |  "knowledge": {"id": "development-process/example", "source": "knowledge/article.dox"},
       |  "resources": [{
       |    "id": "article-pdf-ja", "kind": "document", "language": "ja",
       |    "source": "knowledge/article.dox", "output": "$articleoutput", "build": "article-pdf",
       |    "articleMedia": {"role": "article_pdf", "publicPath": "/articles/example/article-ja.pdf", "mediaType": "application/pdf", "label": "Article PDF"},
       |    "articlePdf": {"latexFormat": "business", "infographic": "$articleinfographic", "renderer": {"name": "smartdox-pdf", "version": "$version", "command": ["smartdox", "pdf"]}}
       |  }, {
       |    "id": "infographic-ja", "kind": "$infographickind", "language": "$infographiclanguage",
       |    $source"build": "prebuilt",
       |    "articleMedia": {"role": "$infographicrole", "publicPath": "/articles/example/infographic-ja.svg"$mediatype}
       |  }]
       |}
       |""".stripMargin
  }

  private def _decode_descriptor(value: String): CozyMedia.Descriptor =
    parse(value).flatMap(_.as[CozyMedia.Descriptor]).fold(
      error => throw new RuntimeException(error.toString),
      identity
    )

  private def _summary_slides_pdf_descriptor(build: String = "prebuilt"): String = {
    val route = build match {
      case "prebuilt" => ""
      case "copy" | "svg-to-png" => ",\n    \"output\": \"target/cozy-media/summary-slides.pdf\""
      case "video-project" => ",\n    \"project\": \"video/project.json\",\n    \"output\": \"target/cozy-media/summary-slides.pdf\""
    }
    s"""{
      |  "schema": "cozy.media.v1",
      |  "knowledge": {"id": "development-process/example", "source": "knowledge/article.dox"},
      |  "resources": [{
      |    "id": "summary-slides-pdf", "kind": "document", "language": "ja",
      |    "source": "summary-slides.pdf", "build": "$build"$route,
      |    "articleMedia": {"role": "summary_slides_pdf", "publicPath": "/articles/example/summary-slides.pdf", "mediaType": "application/pdf"}
      |  }]
      |}
      |""".stripMargin
  }

  private def _write(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _write_pdf(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, ("%PDF-1.7\n" + value).getBytes(StandardCharsets.US_ASCII))
  }

  private def _write_infographic(root: Path): Unit =
    _write(root.resolve("infographic/article-ja.svg"), "<svg>infographic</svg>")

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val work = Paths.get(sys.props("user.dir")).resolve("target")
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "cozy-media-pdf-" + name + "-")
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
