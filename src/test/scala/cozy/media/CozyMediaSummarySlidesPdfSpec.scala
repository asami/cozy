package cozy.media

import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.FileTime
import io.circe.{Json, JsonObject}
import org.scalacheck.{Gen, Prop, Test}
import scala.collection.JavaConverters._

/*
 * @since   Aug. 29, 2026
 *  version Sep. 30, 2026
 * @version Oct. 18, 2026
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

        Then("both outputs are regular PDFs whose actual page counts and ordered nonhash evidence are accepted")
        _page_count(slideirpdf) shouldBe 1
        _page_count(visualpdf) shouldBe 2
        slideirman should include("cozy.summary-slides.render.v1")
        Vector(slideirman, visualman).foreach(_ should not include "sha256")
        slideirman should include("\"assets\":[{\"id\":\"infographic\"}]")
        visualman should include("cozy.summary-slides.render.v2")
        visualman.indexOf("\"overview\"") should be < visualman.indexOf("\"details\"")
        visualman should not include("visualPageSetSha256")
        io.circe.parser.parse(visualman).toOption.get.hcursor.downField("binding").focus shouldBe None
        visualman should include("\"assets\":[{\"id\":\"infographic\"}]")
      }
    }

    "generic generated metadata" which {
      "accept at least fifty reset trials through build and verify for both PDF contracts" taggedAs org.scalatest.tagobjects.Slow in {
        Given("generated ordinary and historical metadata names and arbitrary JSON kinds")
        val metadata = for {
          token <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
          number <- Gen.choose(-10000, 10000)
          extra <- Gen.oneOf(Json.Null, Json.True, Json.False, Json.fromInt(number), Json.fromString(token), Json.arr(Json.fromInt(number), Json.Null), Json.obj("note" -> Json.fromString(token)))
          includepptx <- Gen.oneOf(false, true)
        } yield ("annotation-" + token, extra, includepptx)
        val property = Prop.forAll(metadata) { case (name, extra, includepptx) =>
          Vector("slide-ir-v1", "visual-page-v1").foreach { contract =>
            _with_temp_dir("reset-generated-metadata") { root =>
              Given("independently reset current dependencies and real ordered two-page PDF and PNG outputs")
              val descriptor = _write_fixture(root, contract, includepptx, pages = 2)
              _accept_dependencies(descriptor)
              val initial = _renderer(root)
              var produced = ""
              val runner = new CozyMedia.ProcessRunner {
                def run(command: Vector[String], workingDirectory: Path): Int = {
                  val status = initial.run(command, workingDirectory)
                  val manifest = Path.of(command(command.indexOf("--manifest") + 1))
                  produced = Files.readString(manifest, StandardCharsets.UTF_8)
                  val evidence = io.circe.parser.parse(produced).fold(error => throw error, value => value)
                  _write(manifest, _annotate_metadata(evidence, name, extra).noSpaces)
                  status
                }
              }

              When("the public target build and verification consume arbitrary metadata at every renderer object")
              val built = CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), runner)
              val verified = CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary")))

              Then("the actual artifacts and ordered required evidence are accepted independently of extra metadata")
              built should include("summary: rendered")
              verified should include("status: valid")
              produced.toLowerCase(java.util.Locale.ROOT) should not include "sha256"
              _page_count(root.resolve("target/summary.pdf")) shouldBe 2
              CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))) should include("summary: current")
            }
          }
          true
        }

        When("ScalaCheck runs at least fifty independently reset successful trials of both contracts")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("all public build and verification behavior assertions succeed")
        withClue(s"ScalaCheck status=${result.status}, succeeded=${result.succeeded}") {
          result.passed shouldBe true
          result.succeeded should be >= 50
        }
      }
    }

    "render current PDFs without requiring historical Binding proofs" in {
      _with_temp_dir("complete-binding-proof") { root =>
        Given("a direct Visual Page PDF accepted from a complete Binding with its previous PDF and receipt")
        val descriptor = _write_fixture(root, "visual-page-v1", includepptx = false)
        _accept_dependencies(descriptor)
        CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(root))
        val output = root.resolve("target/summary.pdf")
        val variants = Vector("missing-binding", "binding-type", "binding-incomplete", "binding-slot") ++ Vector("\"obsolete\"", "{}", "[]", "true", "null", "1.5", "9223372036854775808").map("obsolete:" + _)

        When("a real rebuild receives missing ill-typed incomplete or changed obsolete Binding proof")
        val results = variants.map { variant =>
          _write(root.resolve("target/summary-renderer.json"), "{}")
          val action = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))).resources.find(_.resource.id == "summary").get.action
          val result = CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(root, variant = variant))
          (result, _page_count(output), action)
        }

        Then("each rebuild accepts the valid PDF and its required renderer evidence")
        results should have size variants.size
        results.foreach { case (result, pagecount, action) =>
          action shouldBe CozyMedia.Action.Build
          result should include("summary")
          pagecount shouldBe 1
        }
      }
    }

    "ignore missing and arbitrary historical renderer input hashes for both contracts" in {
      _with_temp_dir("historical-renderer-inputs") { root =>
        Given("current declared Slide-IR and Visual Page inputs with valid PDF artifacts")
        val cases = for {
          contract <- Vector("slide-ir-v1", "visual-page-v1")
          oldvalue <- Vector(None, Some("false"), Some("null"), Some("{}"), Some("\"obsolete\""))
        } yield (contract, oldvalue)

        When("renderer output omits or supplies arbitrary old input hash values")
        val results = cases.zipWithIndex.map { case ((contract, oldvalue), index) =>
          val directory = root.resolve(index.toString)
          val descriptor = _write_fixture(directory, contract, includepptx = false)
          _accept_dependencies(descriptor)
          val initial = _renderer(directory)
          val runner = new CozyMedia.ProcessRunner {
            def run(command: Vector[String], workingDirectory: Path): Int = {
              val status = initial.run(command, workingDirectory)
              val manifest = Path.of(command(command.indexOf("--manifest") + 1))
              oldvalue.foreach { raw =>
                val json = io.circe.parser.parse(Files.readString(manifest)).fold(error => throw error, identity)
                val obsolete = io.circe.parser.parse(raw).fold(error => throw error, identity)
                _write(manifest, json.mapObject(_.add("slideIrSha256", obsolete).add("templateSha256", obsolete)).noSpaces)
              }
              status
            }
          }
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), runner)
          CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary")))
        }

        Then("actual PDF validation succeeds independently of historical input hash fields")
        results should have size cases.size
        results.foreach(_ should include("status: valid"))
      }
    }

    "ignore ordinary renderer material metadata for both contracts while retaining required asset IDs" in {
      _with_temp_dir("historical-material-proof") { root =>
        Given("current direct Slide-IR and Visual Page dependencies with renderer material extras")
        val cases = Vector("slide-ir-v1", "visual-page-v1").map { contract =>
          val directory = root.resolve(contract)
          val descriptor = _write_fixture(directory, contract, includepptx = false)
          _accept_dependencies(descriptor)
          directory -> descriptor
        }

        When("both renderer contracts return required asset IDs with malformed obsolete and ordinary extra metadata")
        val verified = cases.map { case (directory, descriptor) =>
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(directory, variant = "historical-asset"))
          CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary")))
        }

        Then("actual PDF page counts and current structural evidence remain usable")
        cases.foreach { case (directory, _) =>
          _page_count(directory.resolve("target/summary.pdf")) shouldBe 1
        }
        verified.foreach(_ should include("status: valid"))
      }
    }

    "retain each authority's exact sequence when more than one actual asset is selected" in {
      Vector("slide-ir-v1", "visual-page-v1").foreach { contract =>
        _with_temp_dir("ordered-assets") { root =>
          Given("two actual declared PNG resources and a source authority selecting both")
          val descriptor = _write_fixture(root, contract, includepptx = false)
          _write_png(root.resolve("assets/detail.png"))
          val media = io.circe.parser.parse(Files.readString(descriptor)).fold(error => throw error, value => value)
          val detail = Json.obj("id" -> Json.fromString("detail"), "kind" -> Json.fromString("image"), "source" -> Json.fromString("assets/detail.png"), "build" -> Json.fromString("prebuilt"))
          _write(descriptor, media.mapObject(fields => fields.add("resources", Json.fromValues(fields("resources").get.asArray.get :+ detail))).noSpaces)
          if (contract == "slide-ir-v1") {
            val source = root.resolve("slides.json")
            _write(source, Files.readString(source).replace("\"elements\":[", "\"elements\":[{\"role\":\"figure\",\"asset\":\"detail\"},"))
          } else {
            val source = root.resolve("visual-pages.json")
            val document = CozyVisualPage.load(source, root.resolve("catalog.json")).document.asInstanceOf[CozyVisualPage.PageSet]
            _write(source, CozyVisualPage.canonicalJson(document.copy(pages = document.pages.map(page =>
              page.copy(assets = page.assets :+ CozyVisualPage.Asset("detail", "assets/detail.png", "image/png"))
            ))))
          }
          _accept_dependencies(descriptor)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("detail")))

          When("the renderer returns the exact source-ordered unique asset IDs")
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(root))
          val verified = CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary")))

          Then("both actual assets are accepted in the route's authority order")
          verified should include("status: valid")
          val receipt = root.resolve("target/cozy-media/manifest.json")
          val priorreceipt = Files.readAllBytes(receipt).toVector

          Given("that accepted receipt, with invalidated renderer evidence forcing a real rebuild")
          _write(root.resolve("target/summary-renderer.json"), "{}")
          When("the renderer reverses the required IDs for one page")
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(root, variant = "asset-order")))

          Then("asset order rejects without replacing the accepted receipt")
          failure.getMessage should include("assets differ")
          Files.readAllBytes(receipt).toVector shouldBe priorreceipt
        }
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

    "retain configured PPTX direct-file and path requirements without an OOXML promise" in {
      _with_temp_dir("pptx-rejections") { root =>
        Given("independent configured-PPTX targets with accepted dependency receipts")
        val variants = Vector("missing-pptx", "pptx-null", "pptx-path-type", "pptx-path", "pptx-unsafe", "missing-pptx-file")
        val cases = variants.map { variant =>
          val directory = root.resolve(variant)
          val descriptor = _write_fixture(directory, "visual-page-v1", includepptx = true)
          _accept_dependencies(descriptor)
          val receipt = directory.resolve("target/cozy-media/manifest.json")
          (directory, descriptor, receipt, Files.readAllBytes(receipt).toVector, variant)
        }

        When("a renderer omits or misdeclares the required configured sidecar evidence or actual file")
        val failures = cases.map { case (directory, descriptor, receipt, priorreceipt, variant) =>
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(directory, variant = variant)))
          (failure, Files.readAllBytes(receipt).toVector == priorreceipt)
        }

        Then("every configured-sidecar failure reports its error without replacing the accepted receipt")
        failures should have size variants.size
        failures.foreach { case (failure, receiptpreserved) =>
          failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include("summary-slides pdf")
          receiptpreserved shouldBe true
        }
      }
    }

    "reject missing and symlinked actual trusted inputs before invoking the renderer" in {
      _with_temp_dir("unsafe-inputs") { root =>
        Given("independent accepted dependency receipts, prior primary PDFs and missing or symlinked actual inputs")
        val variants = Vector("missing-source", "symlink-source", "symlink-template", "symlink-source-binding")
        val cases = variants.map { variant =>
          val directory = root.resolve(variant)
          val descriptor = _write_fixture(directory, "visual-page-v1", includepptx = false)
          _accept_dependencies(descriptor)
          val output = directory.resolve("target/summary.pdf")
          _write_pdf(output, 1, "prior")
          val receipt = directory.resolve("target/cozy-media/manifest.json")
          val path = directory.resolve(variant match {
            case "symlink-template" => "template.pptx"
            case "symlink-source-binding" => "sources/research.txt"
            case _ => "visual-pages.json"
          })
          val original = Files.readAllBytes(path)
          val time = Files.getLastModifiedTime(path)
          Files.delete(path)
          if (variant != "missing-source") {
            val alternate = directory.resolve("alternate-input")
            Files.write(alternate, original)
            Files.createSymbolicLink(path, alternate)
          }
          (directory, descriptor, output, receipt, path, original, time, Files.readAllBytes(output).toVector, Files.readAllBytes(receipt).toVector)
        }

        When("public build resolves each unsafe or missing actual input")
        val failures = cases.map { case (directory, descriptor, output, receipt, path, original, time, priorpdf, priorreceipt) =>
          var invoked = false
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(directory, _ => invoked = true)))
          Files.deleteIfExists(path)
          Files.write(path, original)
          Files.setLastModifiedTime(path, time)
          (failure, invoked, Files.readAllBytes(output).toVector == priorpdf, Files.readAllBytes(receipt).toVector == priorreceipt)
        }

        Then("each input fails before renderer invocation and preserves actual primary and receipt bytes")
        failures should have size variants.size
        failures.foreach { case (failure, invoked, pdfpreserved, receiptpreserved) =>
          failure.getMessage should not be empty
          invoked shouldBe false
          pdfpreserved shouldBe true
          receiptpreserved shouldBe true
        }
      }
    }

    "reject malformed, stale, and page-inconsistent direct renderer evidence before receipt acceptance" which {
      "reject invalid PDFs, required known fields, page count, order, paths and actual image evidence" in {
        _with_temp_dir("evidence-rejection") { root =>
          Given("direct Slide-IR and Visual Page targets with renderer evidence variants and accepted dependency receipts")
          val variants = Vector(
            "missing-pdf", "malformed", "non-pdf", "invalid-pdf", "real-count", "count", "count-type", "count-null", "count-missing", "count-zero", "count-fraction",
            "order", "duplicate-slide", "root-order", "schema", "target", "profile", "renderer-name", "renderer-version-type", "renderer-missing",
            "missing-slides", "slides-type", "missing-id", "id-null", "missing-path", "path-type", "unsafe-path", "wrong-path", "missing-image", "invalid-image",
            "missing-montage", "montage-type", "montage-path", "invalid-montage", "symlink-image", "unconfigured-pptx", "renderer-exit",
            "wrong-asset", "missing-asset", "duplicate-asset", "wrong-asset-type", "missing-asset-id", "assets-type"
          )

          val cases = Vector("slide-ir-v1", "visual-page-v1").flatMap { contract =>
            (variants ++ (if (contract == "slide-ir-v1") Vector("missing-title", "title-type", "title") else Vector.empty)).map { variant =>
              val directory = root.resolve(contract).resolve(variant)
              val descriptor = _write_fixture(directory, contract, includepptx = false, pages = 2)
              _accept_dependencies(descriptor)
              val manifest = directory.resolve("target/cozy-media/manifest.json")
              (directory, descriptor, manifest, Files.readString(manifest, StandardCharsets.UTF_8), variant)
            }
          }

          When("each variant renders directly to the declared primary PDF")
          val failures = cases.map { case (directory, descriptor, manifest, previous, variant) =>
            val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(directory, variant = variant)))
            (failure, previous, Files.readString(manifest, StandardCharsets.UTF_8))
          }

          Then("every invalid evidence shape reports its error without replacing the accepted receipt")
          failures should have size cases.size
          failures.foreach { case (failure, previous, actual) =>
            failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include("summary-slides pdf")
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

      "plan source and configuration changes from explicit modification times" in {
        _with_temp_dir("stale-planning") { root =>
          Given("an accepted direct summary PDF with explicitly older input times")
          val descriptor = _write_fixture(root, "slide-ir-v1", includepptx = false)
          val source = root.resolve("slides.json")
          val inputtime = FileTime.fromMillis(1000)
          val outputtime = FileTime.fromMillis(2000)
          _set_fixture_times(root, inputtime)
          val sourcebytes = Files.readAllBytes(source)
          val descriptorbytes = Files.readAllBytes(descriptor)
          _accept_dependencies(descriptor)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(root))
          Files.setLastModifiedTime(root.resolve("target/summary.pdf"), outputtime)

          When("the valid Slide-IR authority is changed at an explicitly newer time")
          _write(source, _slide_ir_json(1) + "\n")
          Files.setLastModifiedTime(source, FileTime.fromMillis(3000))
          val sourceplan = CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary")))
          Files.write(source, sourcebytes)
          Files.setLastModifiedTime(source, inputtime)

          Then("planning requires rebuilding for the newer input and reuses the restored authority")
          sourceplan should include("summary: build")
          CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))) should include("summary: current")

          Given("the original bytes and times are restored for an independent configuration case")
          Files.write(source, sourcebytes)
          Files.setLastModifiedTime(source, inputtime)
          Files.write(descriptor, descriptorbytes)
          Files.setLastModifiedTime(descriptor, inputtime)
          When("the closed renderer configuration changes at an explicitly newer time")
          _write(descriptor, new String(descriptorbytes, StandardCharsets.UTF_8).replace("\"version\": \"1\"", "\"version\": \"2\""))
          Files.setLastModifiedTime(descriptor, FileTime.fromMillis(3000))
          val configurationplan = CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary")))
          Files.write(descriptor, descriptorbytes)
          Files.setLastModifiedTime(descriptor, inputtime)

          Then("configuration identity and freshness require rebuilding while restoration returns the target to current")
          configurationplan should include("summary: build")
          CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))) should include("summary: current")
        }
      }

      "accept timestamp-preserved valid PDF edits and reject invalid real structure at that same time" in {
        Vector("slide-ir-v1", "visual-page-v1").foreach { contract =>
          _with_temp_dir("same-time-pdf") { root =>
            Given("an accepted direct PDF with an explicit output time newer than all declared inputs")
            val descriptor = _write_fixture(root, contract, includepptx = false)
            _set_fixture_times(root, FileTime.fromMillis(1000))
            _accept_dependencies(descriptor)
            CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer(root))
            val output = root.resolve("target/summary.pdf")
            val outputtime = FileTime.fromMillis(2000)
            Files.setLastModifiedTime(output, outputtime)
            val original = Files.readAllBytes(output)

            When("valid PDF material changes while its exact timestamp is preserved")
            _write_pdf(output, 1, "timestamp-preserved valid update")
            Files.setLastModifiedTime(output, outputtime)
            val validplan = CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary")))
            val validverify = CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary")))

            Then("actual valid current output is accepted without a historical content identity")
            Files.readAllBytes(output).toVector should not be original.toVector
            Files.getLastModifiedTime(output) shouldBe outputtime
            validplan should include("summary: current")
            validverify should include("status: valid")

            Given("the original primary bytes and time are restored separately for each actual-invalidity case")
            val failures = Vector("malformed", "wrong-page-count").map { variant =>
              Files.write(output, original)
              Files.setLastModifiedTime(output, outputtime)
              When("a malformed PDF or a valid PDF with the wrong real page count retains that timestamp")
              if (variant == "malformed") _write(output, "%PDF-invalid") else _write_pdf(output, 2, "wrong count")
              Files.setLastModifiedTime(output, outputtime)
              val planned = CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary")))
              val failure = intercept[RuntimeException](CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary"))))
              Files.write(output, original)
              Files.setLastModifiedTime(output, outputtime)
              planned -> failure
            }

            Then("actual invalidity rejects verification and requires rebuilding at the unchanged time")
            failures.foreach { case (planned, failure) =>
              planned should include("summary: build")
              failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include("summary-slides pdf")
            }
            CozyMedia.plan(CozyMedia.CommandConfig(descriptor, target = Some("summary"))) should include("summary: current")
          }
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
      def run(command: Vector[String], workingDirectory: Path): Int = {
        capture(command)
        _write_renderer_output(root, command, variant)
        if (variant == "renderer-exit") 7 else 0
      }
    }

  private def _write_renderer_output(root: Path, command: Vector[String], variant: String): Unit = {
    val pdf = Path.of(command(command.indexOf("--pdf") + 1))
    val images = Path.of(command(command.indexOf("--slide-images") + 1))
    val montage = Path.of(command(command.indexOf("--montage") + 1))
    val manifest = Path.of(command(command.indexOf("--manifest") + 1))
    val visual = command.contains("--visual-page-set")
    val ids = if (visual) CozyVisualPage.load(root.resolve("visual-pages.json"), root.resolve("catalog.json")).document.pages.map(_.id) else CozyMediaSlideIr.load(Path.of(command(command.indexOf("--slide-ir") + 1))).slides.map(_.id)
    val assetsbypage = if (visual) CozyVisualPage.load(root.resolve("visual-pages.json"), root.resolve("catalog.json")).document.pages.map(page => page.id -> page.assets.map(_.id)).toMap
      else CozyMediaSlideIr.load(Path.of(command(command.indexOf("--slide-ir") + 1))).slides.map(slide => slide.id -> slide.elements.flatMap(_.asset).distinct.sorted).toMap
    if (variant == "non-pdf") _write(pdf, "not-a-pdf")
    else if (variant == "invalid-pdf") _write(pdf, "%PDF-invalid")
    else if (variant != "missing-pdf") _write_pdf(pdf, if (variant == "real-count") ids.size + 1 else ids.size, "generated")
    ids.foreach(id => _write_png(images.resolve(id + ".png")))
    _write_png(montage)
    if (variant == "missing-pdf") return
    if (command.contains("--pptx")) _write(Path.of(command(command.indexOf("--pptx") + 1)), "pptx")
    if (variant == "malformed") {
      _write(manifest, "{}")
    } else {
      val renderedids = if (variant == "order") ids.reverse else ids
      val count = if (variant == "count") ids.size + 1 else ids.size
      val slides = renderedids.map { id =>
        val assetproof = variant match {
          case "wrong-asset" => """[{"id":"wrong"}]"""
          case "missing-asset" => "[]"
          case "duplicate-asset" => """[{"id":"infographic"},{"id":"infographic"}]"""
          case "wrong-asset-type" => """[{"id":true}]"""
          case "historical-asset" => """[{"id":"infographic","ordinaryMetadata":{"old":true},"sha256":"malformed"}]"""
          case _ => Json.fromValues(assetsbypage(id).map(asset => Json.obj("id" -> Json.fromString(asset)))).noSpaces
        }
        val title = if (!visual && id == "overview") ",\"title\":\"Overview\"" else if (!visual) ",\"title\":\"" + id.capitalize + "\"" else ""
        s"""{"id":"$id"$title,"path":"target/slides/$id.png","assets":$assetproof}"""
      }.mkString(",")
      val pptx = if (command.contains("--pptx")) ",\"pptx\":{\"path\":\"target/summary.pptx\"}" else ""
      val identity = if (visual) {
        val document = CozyVisualPage.load(root.resolve("visual-pages.json"), root.resolve("catalog.json"))
        val binding = CozyVisualPageBinding.load(root.resolve("binding.json"), document)
        val bindingfield = variant match {
          case "missing-binding" => ""
          case "binding-type" => "\"binding\":\"wrong\","
          case "binding-incomplete" => "\"binding\":{},"
          case "binding-slot" => "\"binding\":" + binding.canonicalJson.replace("nodes-slot", "revised-nodes-slot") + ","
          case value if value.startsWith("obsolete:") => Vector("visualPageSetSha256", "catalogSha256", "visualPageSet", "catalog", "binding").map(name => "\"" + name + "\":" + value.stripPrefix("obsolete:") + ",").mkString
          case _ => ""
        }
        s""""schema":"cozy.summary-slides.render.v2","target":"summary","profile":"business","renderer":{"name":"fake","version":"1"}${if (bindingfield.nonEmpty) "," + bindingfield.stripSuffix(",") else ""}"""
      } else {
        s""""schema":"cozy.summary-slides.render.v1","target":"summary","profile":"business","renderer":{"name":"fake","version":"1"}"""
      }
      val evidence = io.circe.parser.parse(s"""{$identity,"pdf":{"pageCount":$count},"slides":[$slides],"montage":{"path":"target/montage.png"}$pptx}""").fold(error => throw error, value => value)
      _write(manifest, _renderer_variant(evidence, variant).noSpaces)
      variant match {
        case "missing-image" => Files.delete(images.resolve(ids.head + ".png"))
        case "invalid-image" => _write(images.resolve(ids.head + ".png"), "not-png")
        case "invalid-montage" => _write(montage, "not-png")
        case "missing-pptx-file" => Files.delete(Path.of(command(command.indexOf("--pptx") + 1)))
        case "symlink-image" =>
          Files.delete(images.resolve(ids.head + ".png"))
          Files.createSymbolicLink(images.resolve(ids.head + ".png"), montage)
        case _ => ()
      }
    }
  }

  private def _renderer_variant(value: Json, variant: String): Json = {
    def _nested_(field: String)(change: JsonObject => JsonObject): Json =
      value.mapObject(fields => fields.add(field, fields(field).get.mapObject(change)))
    def _slide_(change: JsonObject => JsonObject): Json =
      value.mapObject(fields => fields.add("slides", Json.fromValues(fields("slides").get.asArray.get.zipWithIndex.map {
        case (slide, 0) => slide.mapObject(change)
        case (slide, _) => slide
      })))
    variant match {
      case "count-type" => _nested_("pdf")(_.add("pageCount", Json.fromString("2")))
      case "count-null" => _nested_("pdf")(_.add("pageCount", Json.Null))
      case "count-missing" => _nested_("pdf")(_.remove("pageCount"))
      case "count-zero" => _nested_("pdf")(_.add("pageCount", Json.fromInt(0)))
      case "count-fraction" => _nested_("pdf")(_.add("pageCount", Json.fromBigDecimal(BigDecimal("1.5"))))
      case "root-order" => Json.fromJsonObject(JsonObject.fromIterable(value.asObject.get.toVector.reverse))
      case "schema" => value.mapObject(_.add("schema", Json.fromString("wrong")))
      case "target" => value.mapObject(_.add("target", Json.fromString("wrong")))
      case "profile" => value.mapObject(_.add("profile", Json.fromString("wrong")))
      case "renderer-name" => _nested_("renderer")(_.add("name", Json.fromString("wrong")))
      case "renderer-version-type" => _nested_("renderer")(_.add("version", Json.fromInt(1)))
      case "renderer-missing" => value.mapObject(_.remove("renderer"))
      case "missing-slides" => value.mapObject(_.remove("slides"))
      case "slides-type" => value.mapObject(_.add("slides", Json.obj()))
      case "duplicate-slide" => value.mapObject(fields => fields.add("slides", Json.arr(fields("slides").get.asArray.get.head, fields("slides").get.asArray.get.head)))
      case "missing-id" => _slide_(_.remove("id"))
      case "id-null" => _slide_(_.add("id", Json.Null))
      case "missing-title" => _slide_(_.remove("title"))
      case "title-type" => _slide_(_.add("title", Json.True))
      case "title" => _slide_(_.add("title", Json.fromString("wrong")))
      case "missing-path" => _slide_(_.remove("path"))
      case "path-type" => _slide_(_.add("path", Json.True))
      case "unsafe-path" => _slide_(_.add("path", Json.fromString("../escape.png")))
      case "wrong-path" => _slide_(_.add("path", Json.fromString("assets/infographic.png")))
      case "missing-asset-id" => _slide_(_.add("assets", Json.arr(Json.obj())))
      case "assets-type" => _slide_(_.add("assets", Json.Null))
      case "asset-order" => _slide_(fields => fields.add("assets", Json.fromValues(fields("assets").get.asArray.get.reverse)))
      case "missing-montage" => value.mapObject(_.remove("montage"))
      case "montage-type" => value.mapObject(_.add("montage", Json.True))
      case "montage-path" => _nested_("montage")(_.add("path", Json.fromString("assets/infographic.png")))
      case "unconfigured-pptx" => value.mapObject(_.add("pptx", Json.obj("path" -> Json.fromString("template.pptx"))))
      case "missing-pptx" => value.mapObject(_.remove("pptx"))
      case "pptx-null" => value.mapObject(_.add("pptx", Json.Null))
      case "pptx-path-type" => _nested_("pptx")(_.add("path", Json.False))
      case "pptx-path" => _nested_("pptx")(_.add("path", Json.fromString("template.pptx")))
      case "pptx-unsafe" => _nested_("pptx")(_.add("path", Json.fromString("../escape.pptx")))
      case _ => value
    }
  }

  private def _annotate_metadata(value: Json, name: String, extra: Json): Json =
    value.arrayOrObject(value,
      values => Json.fromValues(values.map(item => _annotate_metadata(item, name, extra))),
      fields => {
        val annotated = JsonObject.fromIterable(fields.toVector.map { case (field, item) => field -> _annotate_metadata(item, name, extra) })
        val extras = Vector(name, "ordinaryMetadata", "sha256", "pdfSha256", "slideIrSha256", "templateSha256", "visualPageSetSha256", "catalogSha256", "visualPageSet", "catalog", "binding")
        val metadata = JsonObject.fromIterable(extras.map(field => field -> extra) ++ annotated.toVector)
        Json.fromJsonObject(if (fields.contains("pageCount")) metadata.add("path", extra) else metadata)
      })

  private def _set_fixture_times(root: Path, time: FileTime): Unit = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(path => Files.isRegularFile(path)).foreach(path => Files.setLastModifiedTime(path, time))
    finally stream.close()
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
      Vector(CozyVisualPage.Asset("infographic", "assets/infographic.png", "image/png")),
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
