package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{FileAlreadyExistsException, Files, Path}
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipOutputStream}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 25, 2026
 *  version Sep. 30, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaPresentationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy media presentation" should {
    "interpret a strict semantic slide IR" in {
      _with_temp_dir("slide-ir") { root =>
        Given("a slide IR with one semantic title and a declared infographic asset")
        val source = root.resolve("slide-ir.yaml")
        _write(source,
          """schema: cozy.slide-ir.v1
            |knowledge: article/example
            |language: en
            |slides:
            |  - id: overview
            |    title: Overview
            |    layout: title
            |    elements:
            |      - role: title
            |        text: Overview
            |      - role: figure
            |        asset: infographic-en
            |""".stripMargin
        )

        When("Cozy decodes the semantic source")
        val document = CozyMediaSlideIr.load(source)

        Then("the stable semantic identities and asset references are retained")
        document.knowledge shouldBe "article/example"
        document.language shouldBe "en"
        document.slides.map(_.id) shouldBe Vector("overview")
        document.assetIds shouldBe Vector("infographic-en")
      }
    }

    "renderer acceptance" which {
      "reject a missing or unknown IR asset before invoking the renderer" in {
        _with_temp_dir("renderer-missing-asset") { root =>
          Given("a presentation whose semantic IR names an undeclared asset")
          val descriptor = _write_fixture(root)
          _write(root.resolve("slide-ir.yaml"), Files.readString(root.resolve("slide-ir.yaml")).replace("infographic", "unknown-asset"))
          var invoked = false
          val runner = new CozyMedia.ProcessRunner { def run(command: Vector[String], workingdirectory: Path): Int = { invoked = true; 0 } }

          When("Cozy starts the presentation build")
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

          Then("semantic asset validation rejects it before renderer execution or receipt acceptance")
          failure.getMessage should include("asset")
          invoked shouldBe false
          Files.exists(root.resolve("target/cozy-media/manifest.json")) shouldBe false
        }
      }

      "reject a nonzero renderer exit without receipt acceptance" in {
        _with_temp_dir("renderer-exit") { root =>
          Given("a complete presentation fixture and a renderer that exits unsuccessfully")
          val descriptor = _write_fixture(root)
          val runner = new CozyMedia.ProcessRunner { def run(command: Vector[String], workingdirectory: Path): Int = 17 }

          When("Cozy renders the presentation")
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

          Then("the renderer failure leaves no fresh receipt")
          failure.getMessage should include("renderer failed")
          Files.exists(root.resolve("target/cozy-media/manifest.json")) shouldBe false
        }
      }

      "reject successful renderer exits with incomplete generated evidence" in {
        _with_temp_dir("renderer-incomplete") { root =>
          Given("complete inputs and renderer variants omitting required output evidence")
          val descriptor = _write_fixture(root)
          val variants = Vector("pptx", "manifest", "png", "montage")

          When("each renderer reports success without one required artifact")
          val failures = variants.map { variant =>
            Vector("target/rendered/article.pptx", "target/slides/overview.png", "target/rendered/montage.png", "target/rendered/renderer-manifest.json").foreach(path => Files.deleteIfExists(root.resolve(path)))
            val runner = new CozyMedia.ProcessRunner {
              def run(command: Vector[String], workingdirectory: Path): Int = {
                val pptx = Path.of(command(command.indexOf("--pptx") + 1)); val images = Path.of(command(command.indexOf("--slide-images") + 1)); val montage = Path.of(command(command.indexOf("--montage") + 1)); val manifest = Path.of(command(command.indexOf("--manifest") + 1))
                if (variant != "pptx") _write_pptx(pptx, "Overview", root.resolve("infographic.png"))
                if (variant != "png") _write_png(images.resolve("overview.png"))
                if (variant != "montage") _write_png(montage)
                if (variant != "manifest") _write(manifest, "{}")
                0
              }
            }
            intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))
          }

          Then("all incomplete successful runs are rejected without receipt evidence")
          failures should have size variants.size
          Files.exists(root.resolve("target/cozy-media/manifest.json")) shouldBe false
        }
      }

      "reject a receipt-input race after rendering" in {
        _with_temp_dir("renderer-input-race") { root =>
          Given("a renderer that mutates the declared semantic IR while producing valid outputs")
          val descriptor = _write_fixture(root)
          val runner = _successful_runner(root)
          val racing = new CozyMedia.ProcessRunner {
            def run(command: Vector[String], workingdirectory: Path): Int = {
              val status = runner.run(command, workingdirectory)
              val source = root.resolve("slide-ir.yaml")
              val previous = Files.getLastModifiedTime(source)
              _write(source, Files.readString(source) + "\n")
              Files.setLastModifiedTime(source, FileTime.fromMillis(previous.toMillis + 1000))
              status
            }
          }

          When("Cozy re-captures acceptance inputs")
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), racing))

          Then("the changed input rejects acceptance before a receipt is written")
          failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include("inputs changed")
          Files.exists(root.resolve("target/cozy-media/manifest.json")) shouldBe false
        }
      }

      "accept missing and arbitrary historical renderer input hashes for both contracts" in {
        _with_temp_dir("historical-renderer-inputs") { root =>
          Given("valid current renderer outputs with missing or arbitrary old input hash properties")
          val cases = for {
            visual <- Vector(false, true)
            oldvalue <- Vector(None, Some("false"), Some("null"), Some("{}"), Some("\"obsolete\""))
          } yield (visual, oldvalue)

          When("each renderer output is accepted using current declared inputs")
          val results = cases.zipWithIndex.map { case ((visual, oldvalue), index) =>
            val directory = root.resolve(index.toString)
            val descriptor = if (visual) _write_visual_page_fixture(directory) else _write_fixture(directory)
            val initial = if (visual) _visual_page_runner(directory) else _successful_runner(directory)
            val runner = new CozyMedia.ProcessRunner {
              def run(command: Vector[String], workingdirectory: Path): Int = {
                val status = initial.run(command, workingdirectory)
                val manifest = Path.of(command(command.indexOf("--manifest") + 1))
                oldvalue.foreach { raw =>
                  val json = io.circe.parser.parse(Files.readString(manifest)).fold(error => throw error, identity)
                  val obsolete = io.circe.parser.parse(raw).fold(error => throw error, identity)
                  _write(manifest, json.mapObject(_.add("slideIrSha256", obsolete).add("templateSha256", obsolete)).noSpaces)
                }
                status
              }
            }
            CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)
            val reviewpath = directory.resolve("review/manifest.json")
            val written = io.circe.parser.parse(Files.readString(reviewpath)).fold(error => throw error, identity)
            oldvalue.foreach { raw =>
              val obsolete = io.circe.parser.parse(raw).fold(error => throw error, identity)
              val historical = written.mapObject { fields =>
                Vector("slideIr", "template").foldLeft(fields) { (current, name) =>
                  current(name).map(value => current.add(name, value.mapObject(_.add("sha256", obsolete)))).getOrElse(current)
                }
              }
              _write(reviewpath, historical.noSpaces)
            }
            val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor))
            CozyMediaPresentation.verifyStructural(plan, plan.resources.find(_.resource.id == "slides").get)
            written
          }

          Then("historical input properties are not parsed as hashes or emitted into Cozy review inputs")
          results should have size cases.size
          results.foreach { review =>
            review.hcursor.downField("template").downField("sha256").focus shouldBe None
            review.hcursor.downField("slideIr").downField("sha256").focus shouldBe None
          }
        }
      }

      "reject malformed and duplicate renderer slide evidence" in {
        _with_temp_dir("renderer-evidence") { root =>
          Given("renderer manifests with malformed or duplicated slide records")
          val descriptor = _write_fixture(root)
          val variants = Vector("malformed", "duplicate")

          When("each manifest is returned after otherwise valid output files")
          val failures = variants.map { variant =>
            val runner = new CozyMedia.ProcessRunner {
              def run(command: Vector[String], workingdirectory: Path): Int = {
                val pptx = Path.of(command(command.indexOf("--pptx") + 1))
                val images = Path.of(command(command.indexOf("--slide-images") + 1))
                val montage = Path.of(command(command.indexOf("--montage") + 1))
                val manifest = Path.of(command(command.indexOf("--manifest") + 1))
                _write_pptx(pptx, "Overview", root.resolve("infographic.png"))
                _write_png(images.resolve("overview.png"))
                _write_png(montage)
                if (variant == "malformed") {
                  _write(manifest, "{}")
                } else {
                  val pptxhash = _sha256(pptx)
                  val imagehash = _sha256(images.resolve("overview.png"))
                  val montagehash = _sha256(montage)
                  val slide = s"""{"id":"overview","title":"Overview","path":"target/slides/overview.png","sha256":"$imagehash","pptxSha256":"$pptxhash","assets":[{"id":"infographic"}]}"""
                  _write(manifest,
                    s"""{"schema":"cozy.presentation.render.v1","target":"slides","profile":"business","renderer":{"name":"fake","version":"1"},"pptx":{"path":"target/rendered/article.pptx","sha256":"$pptxhash"},"slides":[$slide,$slide],"montage":{"path":"target/rendered/montage.png","sha256":"$montagehash","pptxSha256":"$pptxhash"}}"""
                  )
                }
                0
              }
            }
            intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))
          }

          Then("strict renderer evidence parsing rejects both forms")
          failures should have size 2
          Files.exists(root.resolve("target/cozy-media/manifest.json")) shouldBe false
        }
      }
    }

    "required material proof IDs in both renderer versions" which {
      "accept required IDs with historical extras and reject wrong missing duplicate or ill-typed IDs before replacing review evidence" in {
        _with_temp_dir("renderer-material-ids") { root =>
          Given("legacy and Visual Page descriptor fixtures with selected material IDs and historical renderer metadata")
          val cases = Vector(false, true).map { visual =>
            val directory = root.resolve(if (visual) "visual" else "legacy")
            val descriptor = if (visual) _write_visual_page_fixture(directory) else _write_fixture(directory)
            (directory, descriptor, visual)
          }
          val historical = """[{"id":"infographic","sha256":"malformed","ordinaryMetadata":{"old":true}}]"""
          val invalid = Vector("""[{"id":"wrong"}]""", "[]", """[{"id":"infographic"},{"id":"infographic"}]""", """[{"id":true}]""", """[{"ordinaryMetadata":true}]""")

          When("both renderer versions return ordinary historical metadata followed by each invalid required-ID proof")
          val results = cases.map { case (directory, descriptor, visual) =>
            val accepted = if (visual) _visual_page_runner(directory, assetproof = historical) else _successful_runner(directory, assetproof = historical)
            CozyMedia.build(CozyMedia.CommandConfig(descriptor), accepted)
            val review = Files.readAllBytes(directory.resolve("review/manifest.json")).toVector
            val receipt = Files.readAllBytes(directory.resolve("target/cozy-media/manifest.json")).toVector
            val failures = invalid.map { proof =>
              val runner = if (visual) _visual_page_runner(directory, assetproof = proof) else _successful_runner(directory, assetproof = proof)
              intercept[RuntimeException](CozyMedia._slide_build(CozyMedia.CommandConfig(descriptor), runner))
            }
            (directory, failures, review, receipt)
          }

          Then("historical metadata is ignored and every invalid ID proof rejects with the prior deterministic review and receipt preserved")
          results.flatMap(_._2) should have size 10
          results.flatMap(_._2).foreach(_.getMessage should include("Presentation"))
          results.foreach { case (directory, _, review, receipt) =>
            Files.readAllBytes(directory.resolve("review/manifest.json")).toVector shouldBe review
            Files.readAllBytes(directory.resolve("target/cozy-media/manifest.json")).toVector shouldBe receipt
          }
        }
      }

      "preserve duplicate-content multiplicity while allowing arbitrary OOXML image order for both authorities" in {
        _with_temp_dir("renderer-byte-multiset") { root =>
          Given("three selected direct asset IDs whose content multiset is A A B in each renderer version")
          val cases = Vector(false, true).map { visual =>
            val directory = root.resolve(if (visual) "visual" else "legacy")
            val descriptor = if (visual) _write_visual_page_fixture(directory) else _write_fixture(directory)
            val prefix = if (visual) "assets/" else ""
            val primary = directory.resolve(prefix + "infographic.png")
            val duplicate = directory.resolve(prefix + "duplicate.png")
            val other = directory.resolve(prefix + "other.png")
            Files.write(duplicate, Files.readAllBytes(primary))
            Files.write(other, Files.readAllBytes(primary) ++ Array[Byte](2))
            val resources = Vector("duplicate", "other").map { id =>
              s"  - id: $id\n    kind: infographic\n    language: en\n    source: $prefix$id.png\n    output: $prefix$id.png\n    build: prebuilt\n"
            }.mkString
            _write(descriptor, Files.readString(descriptor).replace("  - id: slides\n", resources + "  - id: slides\n"))
            if (visual) {
              val source = directory.resolve("visual-pages.json")
              val document = CozyVisualPage.load(source, directory.resolve("catalog.json")).document.asInstanceOf[CozyVisualPage.PageSet]
              _write(source, CozyVisualPage.canonicalJson(document.copy(pages = document.pages.map(page =>
                page.copy(assets = page.assets ++ Vector(CozyVisualPage.Asset("duplicate", prefix + "duplicate.png", "image/png"), CozyVisualPage.Asset("other", prefix + "other.png", "image/png")))
              ))))
            } else {
              val source = directory.resolve("slide-ir.yaml")
              _write(source, Files.readString(source).replace("        asset: infographic\n", "        asset: infographic\n      - role: figure\n        asset: duplicate\n      - role: figure\n        asset: other\n"))
            }
            (directory, descriptor, visual, primary, duplicate, other)
          }

          When("the renderer embeds B A A in a different order, then attempts A B B and a two-image package")
          val results = cases.map { case (directory, descriptor, visual, primary, duplicate, other) =>
            val proof = if (visual) """[{"id":"infographic"},{"id":"duplicate"},{"id":"other"}]""" else """[{"id":"duplicate"},{"id":"infographic"},{"id":"other"}]"""
            def _runner_(media: Vector[Path]): CozyMedia.ProcessRunner = {
              val writer: (Path, String, Path) => Unit = (path, title, asset) => _write_pptx(path, title, asset, media = media)
              if (visual) _visual_page_runner(directory, assetproof = proof, pptxwriter = writer) else _successful_runner(directory, assetproof = proof, pptxwriter = writer)
            }
            CozyMedia.build(CozyMedia.CommandConfig(descriptor), _runner_(Vector(other, primary, duplicate)))
            val review = Files.readAllBytes(directory.resolve("review/manifest.json")).toVector
            val receipt = Files.readAllBytes(directory.resolve("target/cozy-media/manifest.json")).toVector
            val multiplicity = intercept[RuntimeException](CozyMedia._slide_build(CozyMedia.CommandConfig(descriptor), _runner_(Vector(primary, other, other))))
            val count = intercept[RuntimeException](CozyMedia._slide_build(CozyMedia.CommandConfig(descriptor), _runner_(Vector(primary, other))))
            (multiplicity, count, review, receipt, directory)
          }

          Then("the real ZIP byte multiset admits reordered equal content and rejects wrong multiplicity or cardinality without replacing accepted review evidence")
          results.foreach { case (multiplicity, count, review, receipt, directory) =>
            multiplicity.getMessage should include("slide media differs")
            count.getMessage should include("slide media differs")
            Files.readAllBytes(directory.resolve("review/manifest.json")).toVector shouldBe review
            Files.readAllBytes(directory.resolve("target/cozy-media/manifest.json")).toVector shouldBe receipt
          }
        }
      }
    }

    "OOXML, artifact identity, and review lifecycle" which {
      "accept a semantic slide named montage using a namespaced receipt artifact identity" in {
        _with_temp_dir("renderer-montage-id") { root =>
          Given("a valid semantic slide whose raw ID is montage")
          val descriptor = _write_fixture(root, "montage")

          When("Cozy renders and accepts the presentation")
          CozyMedia.build(CozyMedia.CommandConfig(descriptor), _successful_runner(root, "montage"))

          Then("the slide and fixed montage artifacts remain collision-free")
          val receipt = Files.readString(root.resolve("target/cozy-media/manifest.json"))
          receipt should include("slide:montage")
          receipt should include("\"id\" : \"montage\"")
        }
      }

      "reject overlapping configured generated paths before invoking the renderer" in {
        _with_temp_dir("renderer-overlap") { root =>
          Given("a descriptor whose montage path aliases the slide image directory")
          val descriptor = _write_fixture(root)
          _write(descriptor, Files.readString(descriptor).replace("montage: target/rendered/montage.png", "montage: target/slides"))
          var invoked = false
          val runner = new CozyMedia.ProcessRunner { def run(command: Vector[String], workingdirectory: Path): Int = { invoked = true; 0 } }

          When("Cozy resolves the descriptor")
          val failure = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

          Then("the overlap is rejected before external execution")
          failure.getMessage should include("overlap")
          invoked shouldBe false
        }
      }

      "reject unsafe or relationship-inconsistent OOXML packages" in {
        _with_temp_dir("renderer-ooxml") { root =>
          Given("standard renderer evidence with relationship target, orphan-part/media, extra relationship, escaping structural relationship, hostile XML, and per-slide media variants")
          val descriptor = _write_fixture(root)
          val other = root.resolve("other.png")
          _write(other, "not-the-infographic")
          val writers = Vector[(Path, String, Path) => Unit](
            (path, title, asset) => _write_pptx(path, title, asset, slidetarget = "slides/slide2.xml", extraslide = true),
            (path, title, asset) => _write_pptx(path, title, asset, extraslide = true),
            (path, title, asset) => _write_pptx(path, title, asset, orphanmedia = true),
            (path, title, asset) => _write_pptx(path, title, asset, extrapresentationrelation = true),
            (path, title, asset) => _write_pptx(path, title, asset, extrasliderelation = true),
            (path, title, asset) => _write_pptx(path, title, asset, structuralescape = true),
            (path, title, asset) => _write_pptx(path, title, asset, hostiledtd = true),
            (path, title, asset) => _write_pptx(path, title, other)
          )

          When("Cozy validates each package through presentation and per-slide relationships")
          val failures = writers.map(writer => intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), _successful_runner(root, pptxwriter = writer))))

          Then("all relationship/order/media, unreferenced/escaping structural relationship, and hostile XML variants are rejected")
          failures should have size writers.size
          Files.exists(root.resolve("target/cozy-media/manifest.json")) shouldBe false
        }
      }

      "reject tampered artifacts and false review-manifest claims through current reconstruction" in {
        _with_temp_dir("renderer-tamper") { root =>
          Given("one accepted presentation generation")
          val descriptor = _write_fixture(root)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor), _successful_runner(root))
          val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor))
          val resource = plan.resources.find(_.resource.id == "slides").get
          val targets = Vector("target/rendered/article.pptx", "target/slides/overview.png", "target/rendered/montage.png", "article.pdf", "infographic.png")

          When("each accepted output or dependency is changed after receipt acceptance")
          val failures = targets.map { target =>
            val path = root.resolve(target)
            val original = Files.readAllBytes(path)
            Files.write(path, original ++ Array[Byte](1))
            val failure = intercept[RuntimeException](CozyMedia.verify(CozyMedia.CommandConfig(descriptor)))
            Files.write(path, original)
            failure
          }
          val review = root.resolve("review/manifest.json")
          val originalreview = Files.readString(review)
          _write(review, originalreview.replace("\"name\" : \"fake\"", "\"name\" : \"false-claim\""))
          val reviewfailure = intercept[RuntimeException](CozyMediaPresentation.verifyStructural(plan, resource))

          Then("receipt currentness rejects changed files and deterministic reconstruction rejects a nested false claim")
          failures should have size targets.size
          reviewfailure.getMessage should include("reconstruction")
          _write(review, originalreview)
        }
      }

      "preserve prior receipt bytes when review-state acceptance cannot be prepared or installed" in {
        _with_temp_dir("renderer-state-failure") { root =>
          Given("an accepted presentation and then malformed state plus an uncreatable state parent")
          val descriptor = _write_fixture(root)
          val runner = _successful_runner(root)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)
          val receipt = root.resolve("target/cozy-media/manifest.json")
          val originalreceipt = Files.readAllBytes(receipt)
          _write(root.resolve("review/state.yaml"), "{}")

          When("the next acceptance reads malformed review state")
          val malformed = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

          Then("no new receipt replaces the accepted receipt")
          malformed.getMessage should include("review state")
          Files.readAllBytes(receipt) shouldBe originalreceipt

          Given("an aliased review-state path")
          Files.delete(root.resolve("review/state.yaml"))
          Files.createSymbolicLink(root.resolve("review/state.yaml"), root.resolve("article.pdf"))

          When("the next acceptance reads the symbolic-link state")
          val aliased = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))

          Then("the alias is rejected without replacing receipt evidence")
          aliased.getMessage should include("non-symlink")
          Files.readAllBytes(receipt) shouldBe originalreceipt
          Files.delete(root.resolve("review/state.yaml"))

          Given("a prepared review state whose parent is an existing regular file")
          _write(root.resolve("blocker"), "file")
          val preparedstate = CozyMediaReceipt.PreparedDocument(root.resolve("blocker/state.yaml"), "{}".getBytes(StandardCharsets.UTF_8))
          val preparedreceipt = CozyMediaReceipt.PreparedDocument(receipt, originalreceipt)

          When("the receipt-last acceptance transaction stages its state and receipt")
          val installfailure = intercept[FileAlreadyExistsException](CozyMediaReceipt.commit(Vector(preparedstate), preparedreceipt))

          Then("the failed install still preserves the previously accepted receipt bytes")
          installfailure.getFile should include("blocker")
          Files.readAllBytes(receipt) shouldBe originalreceipt
        }
      }

      "preserve explicit alignment on unchanged reuse and invalidate it on a newer generation time" in {
        _with_temp_dir("review-generation-time") { root =>
          Given("an explicitly aligned presentation generation")
          val descriptor = _write_fixture(root)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor), _successful_runner(root))
          val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor))
          val resource = plan.resources.find(_.resource.id == "slides").get
          CozyMediaReviewState.align(plan, resource, "article")
          val statepath = root.resolve("review/state.yaml")
          val aligned = CozyMediaReviewState.load(statepath)

          When("the same generated files are reused")
          CozyMediaReviewState.refresh(plan, resource)

          Then("the existing explicit authority and alignment are preserved")
          CozyMediaReviewState.load(statepath) shouldBe aligned
          CozyMediaReviewState.requireAligned(plan, resource)
          Files.readString(statepath) should not include "Sha256"

          When("identical review bytes have a generation time newer than the decision")
          val review = root.resolve(resource.resource.presentation.get.reviewManifest)
          Files.setLastModifiedTime(review, FileTime.from(java.time.Instant.parse(aligned.lastAligned.get.acceptedAt).plusSeconds(1)))
          CozyMediaReviewState.refresh(plan, resource)

          Then("a timestamp refresh preserves the decision record but requires new alignment")
          val stale = CozyMediaReviewState.load(statepath)
          stale.sync shouldBe "stale"
          stale.lastAligned shouldBe aligned.lastAligned
          stale.selectedAuthority shouldBe aligned.selectedAuthority
          intercept[RuntimeException](CozyMediaReviewState.requireAligned(plan, resource)).getMessage should include("stale")
        }
      }

      "ignore historical aggregate hash values without parsing or re-emitting them" in {
        _with_temp_dir("review-historical-hashes") { root =>
          Given("historical aligned snapshots containing arbitrary old hash properties")
          val path = root.resolve("state.json")
          val values = Vector(io.circe.Json.Null, io.circe.Json.True, io.circe.Json.obj("legacy" -> io.circe.Json.fromInt(1)), io.circe.Json.fromString("not-a-digest"))
          values.foreach { value =>
            val snapshot = io.circe.Json.obj(
              "acceptedAt" -> io.circe.Json.fromString("2026-10-01T00:00:00Z"),
              "inputSetSha256" -> value, "reviewManifestSha256" -> value, "artifactSetSha256" -> value
            )
            _write(path, io.circe.Json.obj(
              "schema" -> io.circe.Json.fromString("cozy.media.review-state.v1"),
              "current" -> snapshot, "last_aligned" -> snapshot,
              "selected_authority" -> io.circe.Json.fromString("article"), "sync" -> io.circe.Json.fromString("aligned")
            ).spaces2)

            When("the historical state is loaded and written through the current codec")
            val state = CozyMediaReviewState.load(path)
            CozyMediaReviewState.write(path, state)

            Then("non-hash data is preserved and only the timestamp remains in each snapshot")
            state.current shouldBe Some(CozyMediaReviewState.Snapshot("2026-10-01T00:00:00Z"))
            state.sync shouldBe "aligned"
            Files.readString(path) should not include "Sha256"
          }
        }
      }

      "refresh current evidence but require re-alignment after changed rendered artifacts" in {
        _with_temp_dir("renderer-realignment") { root =>
          Given("an explicitly aligned presentation generation")
          val descriptor = _write_fixture(root)
          val initial = _successful_runner(root)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor), initial)
          val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor))
          val resource = plan.resources.find(_.resource.id == "slides").get
          CozyMediaReviewState.align(plan, resource, "slide-ir")
          val aligned = CozyMediaReviewState.load(root.resolve("review/state.yaml"))
          Files.write(root.resolve("target/slides/overview.png"), Files.readAllBytes(root.resolve("target/slides/overview.png")) ++ Array[Byte](2))
          val changed = new CozyMedia.ProcessRunner {
            def run(command: Vector[String], workingdirectory: Path): Int = {
              initial.run(command, workingdirectory)
              val image = Path.of(command(command.indexOf("--slide-images") + 1)).resolve("overview.png")
              val manifest = Path.of(command(command.indexOf("--manifest") + 1))
              val oldhash = _sha256(image)
              Files.write(image, Files.readAllBytes(image) ++ Array[Byte](3))
              _write(manifest, Files.readString(manifest).replace(oldhash, _sha256(image)))
              0
            }
          }

          When("changed rendered output is rebuilt with unchanged receipt inputs")
          CozyMedia.build(CozyMedia.CommandConfig(descriptor), changed)
          val refreshed = CozyMediaReviewState.load(root.resolve("review/state.yaml"))
          val stale = intercept[RuntimeException](CozyMedia.verify(CozyMedia.CommandConfig(descriptor)))

          Then("current refreshes while alignment authority is preserved and becomes stale")
          refreshed.current should not be (aligned.current)
          refreshed.lastAligned shouldBe aligned.lastAligned
          refreshed.selectedAuthority shouldBe Some("slide-ir")
          refreshed.sync shouldBe "stale"
          stale.getMessage should include("presentation verification failed")

          When("an explicit re-alignment is made for the refreshed generation")
          CozyMediaReviewState.align(CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)), CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)).resources.find(_.resource.id == "slides").get, "slide-ir")

          Then("ordinary verification again accepts the aligned current generation")
          CozyMedia.verify(CozyMedia.CommandConfig(descriptor)) should include("status: valid")
        }
      }
    }

    "reject coordinates, duplicate slide identities, placeholders, and malformed semantic elements" in {
      _with_temp_dir("slide-ir-invalid") { root =>
        Given("otherwise complete IR variants outside the exact presentation schema")
        val invalid = Vector(
          """{"schema":"cozy.slide-ir.v1","knowledge":"article/example","language":"en","slides":[{"id":"overview","title":"TODO","layout":"title","elements":[{"role":"title","text":"TODO"}]}]}""",
          """{"schema":"cozy.slide-ir.v1","knowledge":"article/example","language":"en","slides":[{"id":"overview","title":"Overview","layout":"title","elements":[{"role":"title","text":"Overview","x":1}]}]}""",
          """{"schema":"cozy.slide-ir.v1","knowledge":"article/example","language":"en","slides":[{"id":"overview","title":"Overview","layout":"title","elements":[{"role":"title","text":"Overview"}]},{"id":"overview","title":"Again","layout":"title","elements":[{"role":"title","text":"Again"}]}]}"""
        )

        When("Cozy parses each variant")
        val failures = invalid.zipWithIndex.map { case (value, index) =>
          val source = root.resolve(s"invalid-$index.json")
          _write(source, value)
          intercept[RuntimeException](CozyMediaSlideIr.load(source))
        }

        Then("each unsupported semantic form is deterministically rejected")
        failures should have size 3
        failures.map(_.getMessage).foreach(_ should not be empty)
      }
    }

    "collect a structurally verified renderer result and require explicit review alignment" in {
      _with_temp_dir("renderer") { root =>
        Given("article, infographic, template, semantic IR, and a deterministic fake renderer")
        _write(root.resolve("article.dox"), "Article")
        _write(root.resolve("article.pdf"), "PDF")
        _write_png(root.resolve("infographic.png"))
        _write(root.resolve("template.pptx"), "template")
        _write(root.resolve("slide-ir.yaml"),
          """schema: cozy.slide-ir.v1
            |knowledge: article/example
            |language: en
            |slides:
            |  - id: overview
            |    title: Overview
            |    layout: content
            |    elements:
            |      - role: title
            |        text: Overview
            |      - role: figure
            |        asset: infographic
            |""".stripMargin
        )
        val descriptor = root.resolve("media.yaml")
        _write(descriptor, _presentation_descriptor)
        var argv = Vector.empty[String]
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            argv = command
            val pptx = Path.of(command(command.indexOf("--pptx") + 1))
            val images = Path.of(command(command.indexOf("--slide-images") + 1))
            val montage = Path.of(command(command.indexOf("--montage") + 1))
            val manifest = Path.of(command(command.indexOf("--manifest") + 1))
            _write_pptx(pptx, "Overview", root.resolve("infographic.png"))
            _write_png(images.resolve("overview.png"))
            _write_png(montage)
            val pptxhash = _sha256(pptx)
            _write(manifest,
              s"""{"schema":"cozy.presentation.render.v1","target":"slides","profile":"business","renderer":{"name":"fake","version":"1"},"pptx":{"path":"target/rendered/article.pptx","sha256":"$pptxhash"},"slides":[{"id":"overview","title":"Overview","path":"target/slides/overview.png","sha256":"${_sha256(images.resolve("overview.png"))}","pptxSha256":"$pptxhash","assets":[{"id":"infographic"}]}],"montage":{"path":"target/rendered/montage.png","sha256":"${_sha256(montage)}","pptxSha256":"$pptxhash"}}"""
            )
            0
          }
        }

        When("Cozy builds the presentation through explicit renderer argv")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)

        Then("the configured argv, subordinate artifacts, and receipt are deterministic")
        argv.take(2) shouldBe Vector("fake-renderer", "render")
        argv should contain("--slide-ir")
        argv should contain("--template")
        Files.isRegularFile(root.resolve("target/rendered/article.pptx")) shouldBe true
        Files.readString(root.resolve("target/cozy-media/manifest.json")) should include("review-manifest")

        When("ordinary verification is requested before a semantic authority is recorded")
        val stale = intercept[RuntimeException](CozyMedia.verify(CozyMedia.CommandConfig(descriptor)))

        Then("review lifecycle rejects automatic semantic acceptance")
        stale.getMessage should include("presentation verification failed")

        When("the explicit slide-IR alignment decision is recorded")
        CozyMediaReviewState.align(
          CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)),
          CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)).resources.find(_.resource.id == "slides").get,
          "slide-ir"
        )

        Then("the same current artifact generation verifies")
        CozyMedia.verify(CozyMedia.CommandConfig(descriptor)) should include("status: valid")
      }
    }

    "render a closed visual-page-v1 presentation route with canonical semantic evidence" in {
      _with_temp_dir("visual-page-renderer") { root =>
        Given("a VisualPageSet, resolved catalog, complete business binding, explicit receipt inputs, and a v2 renderer")
        val descriptor = _write_visual_page_fixture(root)
        var argv = Vector.empty[String]
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            argv = command
            val pptx = Path.of(command(command.indexOf("--pptx") + 1))
            val images = Path.of(command(command.indexOf("--slide-images") + 1))
            val montage = Path.of(command(command.indexOf("--montage") + 1))
            val manifest = Path.of(command(command.indexOf("--manifest") + 1))
            val validated = CozyVisualPage.load(root.resolve("visual-pages.json"), root.resolve("catalog.json"))
            CozyVisualPageBinding.load(root.resolve("binding.json"), validated)
            _write_pptx(pptx, "Overview", root.resolve("assets/infographic.png"))
            _write_png(images.resolve("overview.png"))
            _write_png(montage)
            val pptxhash = _sha256(pptx)
            _write(manifest,
              s"""{"schema":"cozy.presentation.render.v2","target":"slides","profile":"business","renderer":{"name":"fake","version":"1"},"pptx":{"path":"target/rendered/article.pptx","sha256":"$pptxhash"},"slides":[{"id":"overview","path":"target/slides/overview.png","sha256":"${_sha256(images.resolve("overview.png"))}","pptxSha256":"$pptxhash","assets":[{"id":"infographic"}]}],"montage":{"path":"target/rendered/montage.png","sha256":"${_sha256(montage)}","pptxSha256":"$pptxhash"}}"""
            )
            0
          }
        }

        When("Cozy invokes the versioned renderer and records deterministic review evidence")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)
        val review = Files.readString(root.resolve("review/manifest.json"))

        Then("the visual-page argv is accepted and review provenance contains only the current paths")
        argv should contain("--visual-page-set")
        argv should contain("--catalog")
        argv should contain("--binding")
        argv.contains("--slide-ir") shouldBe false
        review should include("cozy.media.presentation-review.v2")
        review should include("visualPageSet")
        io.circe.parser.parse(review).toOption.get.hcursor.downField("binding").focus shouldBe Some(io.circe.Json.obj("path" -> io.circe.Json.fromString("binding.json")))
        Files.readString(root.resolve("target/cozy-media/manifest.json")) should include("renderer-manifest")

        Given("the accepted v2 renderer or review evidence is changed without changing inputs")
        val manifest = root.resolve("target/rendered/renderer-manifest.json")
        val originalmanifest = Files.readString(manifest)
        _write(manifest, originalmanifest.replace("\"assets\":[{\"id\":\"infographic\"", "\"assets\":[{\"id\":\"wrong\""))

        When("structural verification reconstructs the page order and asset linkage")
        val rendererfailure = intercept[RuntimeException](CozyMedia.verify(CozyMedia.CommandConfig(descriptor)))
        _write(manifest, originalmanifest)
        val originalreview = Files.readString(root.resolve("review/manifest.json"))
        _write(root.resolve("review/manifest.json"), originalreview.replace("cozy.media.presentation-review.v2", "cozy.media.presentation-review.v9"))
        val reviewfailure = intercept[RuntimeException](CozyMediaPresentation.verifyStructural(CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)), CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)).resources.find(_.resource.id == "slides").get))

        Then("stale renderer and review claims fail closed before they can prove acceptance")
        rendererfailure.getMessage should include("presentation verification failed")
        reviewfailure.getMessage should include("review manifest")
      }
    }

    "process current rendering when obsolete Binding proof is missing malformed or changed" in {
      _with_temp_dir("visual-binding-proof") { root =>
        Given("a presentation accepted from its complete normalized Binding and its prior receipt")
        val descriptor = _write_visual_page_fixture(root)
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), _visual_page_runner(root))
        val receipt = root.resolve("target/cozy-media/manifest.json")
        val proofs = Vector[String => Option[String]](
          _ => None,
          _ => Some("\"wrong\""),
          _ => Some("{}"),
          value => Some(value.replace("nodes-slot", "revised-nodes-slot"))
        ) ++ Vector("\"obsolete\"", "{}", "[]", "true", "null", "1.5", "9223372036854775808").map(raw => (_: String) => Some(raw + Vector("visualPageSetSha256", "catalogSha256", "visualPageSet", "catalog").map(name => ",\"" + name + "\":" + raw).mkString))

        When("a real rebuild receives each obsolete proof shape")
        val results = proofs.map { proof =>
          _write(root.resolve("target/rendered/renderer-manifest.json"), "{}")
          val action = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)).resources.find(_.resource.id == "slides").get.action
          val result = CozyMedia.build(CozyMedia.CommandConfig(descriptor), _visual_page_runner(root, bindingproof = proof))
          (result, action)
        }

        Then("all rebuilds accept actual renderer outputs without interpreting historical proof values")
        results should have size proofs.size
        results.foreach { case (result, action) =>
          action shouldBe CozyMedia.Action.Build
          result should include("slides")
        }
        Files.isRegularFile(receipt) shouldBe true
      }
    }

    "reject mixed, malformed, unsafe, symlink, nonregular, and mismatched visual-page presentation inputs" in {
      _with_temp_dir("visual-page-rejections") { root =>
        Given("closed v2 descriptor variants and direct input files that violate their declared boundary")
        val descriptor = _write_visual_page_fixture(root)
        val original = Files.readString(descriptor)
        val variants = Vector(
          original.replace("contract: visual-page-v1", "contract: visual-page-v9"),
          original.replace("contract: visual-page-v1", "contract: visual-page-v1\n      unknown: value"),
          original.replace("source: visual-pages.json", "source: ../visual-pages.json"),
          original.replace("catalog: catalog.json", "catalog: missing.json"),
          original.replace("binding: binding.json", "binding: catalog.json")
        )

        When("Cozy resolves every closed grammar, path, and catalog/binding mismatch variant")
        val failures = variants.zipWithIndex.map { case (value, index) =>
          _write(descriptor, value)
          intercept[Exception](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        }
        _write(descriptor, original)
        val linktarget = root.resolve("visual-target.json")
        Files.move(root.resolve("visual-pages.json"), linktarget)
        Files.createSymbolicLink(root.resolve("visual-pages.json"), linktarget)
        val linkfailure = intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        Files.delete(root.resolve("visual-pages.json"))
        Files.move(linktarget, root.resolve("visual-pages.json"))
        Files.move(root.resolve("visual-pages.json"), linktarget)
        Files.createDirectory(root.resolve("visual-pages.json"))
        val sourcedirectoryfailure = intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        Files.delete(root.resolve("visual-pages.json"))
        Files.move(linktarget, root.resolve("visual-pages.json"))
        Files.delete(root.resolve("catalog.json"))
        Files.createDirectory(root.resolve("catalog.json"))
        val directoryfailure = intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        Files.delete(root.resolve("catalog.json"))
        _write(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(_visual_catalog))
        val catalogtarget = root.resolve("catalog-target.json")
        Files.move(root.resolve("catalog.json"), catalogtarget)
        Files.createSymbolicLink(root.resolve("catalog.json"), catalogtarget)
        val cataloglinkfailure = intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        Files.delete(root.resolve("catalog.json"))
        Files.move(catalogtarget, root.resolve("catalog.json"))
        val bindingtarget = root.resolve("binding-target.json")
        Files.move(root.resolve("binding.json"), bindingtarget)
        Files.createSymbolicLink(root.resolve("binding.json"), bindingtarget)
        val bindinglinkfailure = intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        Files.delete(root.resolve("binding.json"))
        Files.createDirectory(root.resolve("binding.json"))
        val bindingdirectoryfailure = intercept[RuntimeException](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        Files.delete(root.resolve("binding.json"))
        Files.move(bindingtarget, root.resolve("binding.json"))
        val directinputs = root.resolve("direct-inputs")
        Files.createDirectory(directinputs)
        Vector("visual-pages.json", "catalog.json", "binding.json").foreach(name => Files.copy(root.resolve(name), directinputs.resolve(name)))
        Vector("source-parent", "catalog-parent", "binding-parent").foreach(name => Files.createSymbolicLink(root.resolve(name), directinputs))
        var rendered = false
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            rendered = true
            0
          }
        }
        val parentlinkfailures = Vector(
          "source" -> original.replace("source: visual-pages.json", "source: source-parent/visual-pages.json"),
          "catalog" -> original.replace("catalog: catalog.json", "catalog: catalog-parent/catalog.json"),
          "binding" -> original.replace("binding: binding.json", "binding: binding-parent/binding.json")
        ).map { case (field, value) =>
          _write(descriptor, value)
          field -> intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner))
        }

        Then("all variants reject before renderer invocation or receipt acceptance")
        (failures ++ Vector(linkfailure, sourcedirectoryfailure, directoryfailure, cataloglinkfailure, bindinglinkfailure, bindingdirectoryfailure)).map(_.getMessage).forall(_.nonEmpty) shouldBe true
        parentlinkfailures.map(_._1).toSet shouldBe Set("source", "catalog", "binding")
        parentlinkfailures.map(_._2.getMessage).forall(_.nonEmpty) shouldBe true
        rendered shouldBe false
        Files.exists(root.resolve("target/cozy-media/manifest.json")) shouldBe false
      }
    }
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name).toRealPath()
    try body(root)
    finally Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _write_png(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00))
  }

  private def _write_slide_png(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01))
  }

  private def _successful_runner(root: Path, slideid: String = "overview", title: String = "Overview", pptxwriter: (Path, String, Path) => Unit = (path, value, asset) => _write_pptx(path, value, asset), assetproof: String = """[{"id":"infographic"}]"""): CozyMedia.ProcessRunner =
    new CozyMedia.ProcessRunner {
      def run(command: Vector[String], workingdirectory: Path): Int = {
        val pptx = Path.of(command(command.indexOf("--pptx") + 1))
        val images = Path.of(command(command.indexOf("--slide-images") + 1))
        val montage = Path.of(command(command.indexOf("--montage") + 1))
        val manifest = Path.of(command(command.indexOf("--manifest") + 1))
        pptxwriter(pptx, title, root.resolve("infographic.png"))
        _write_slide_png(images.resolve(s"$slideid.png"))
        _write_png(montage)
        val pptxhash = _sha256(pptx)
        _write(manifest,
          s"""{"schema":"cozy.presentation.render.v1","target":"slides","profile":"business","renderer":{"name":"fake","version":"1"},"pptx":{"path":"target/rendered/article.pptx","sha256":"$pptxhash"},"slides":[{"id":"$slideid","title":"$title","path":"target/slides/$slideid.png","sha256":"${_sha256(images.resolve(s"$slideid.png"))}","pptxSha256":"$pptxhash","assets":$assetproof}],"montage":{"path":"target/rendered/montage.png","sha256":"${_sha256(montage)}","pptxSha256":"$pptxhash"}}"""
        )
        0
      }
    }

  private def _visual_page_runner(root: Path, assetproof: String = """[{"id":"infographic"}]""", pptxwriter: (Path, String, Path) => Unit = (path, title, asset) => _write_pptx(path, title, asset), bindingproof: String => Option[String] = _ => None): CozyMedia.ProcessRunner =
    new CozyMedia.ProcessRunner {
      def run(command: Vector[String], workingdirectory: Path): Int = {
        val pptx = Path.of(command(command.indexOf("--pptx") + 1))
        val images = Path.of(command(command.indexOf("--slide-images") + 1))
        val montage = Path.of(command(command.indexOf("--montage") + 1))
        val manifest = Path.of(command(command.indexOf("--manifest") + 1))
        val validated = CozyVisualPage.load(root.resolve("visual-pages.json"), root.resolve("catalog.json"))
        val binding = CozyVisualPageBinding.load(root.resolve("binding.json"), validated)
        pptxwriter(pptx, "Overview", root.resolve("assets/infographic.png"))
        _write_png(images.resolve("overview.png"))
        _write_png(montage)
        val pptxhash = _sha256(pptx)
        val bindingfield = bindingproof(binding.canonicalJson).map(value => "\"binding\":" + value + ",").getOrElse("")
        _write(manifest,
          s"""{"schema":"cozy.presentation.render.v2","target":"slides","profile":"business","renderer":{"name":"fake","version":"1"},${bindingfield}"pptx":{"path":"target/rendered/article.pptx","sha256":"$pptxhash"},"slides":[{"id":"overview","path":"target/slides/overview.png","sha256":"${_sha256(images.resolve("overview.png"))}","pptxSha256":"$pptxhash","assets":$assetproof}],"montage":{"path":"target/rendered/montage.png","sha256":"${_sha256(montage)}","pptxSha256":"$pptxhash"}}"""
        )
        0
      }
    }

  private def _write_fixture(root: Path, slideid: String = "overview"): Path = {
    _write(root.resolve("article.dox"), "Article")
    _write(root.resolve("article.pdf"), "PDF")
    _write_png(root.resolve("infographic.png"))
    _write(root.resolve("template.pptx"), "template")
    _write(root.resolve("slide-ir.yaml"),
      s"""schema: cozy.slide-ir.v1
         |knowledge: article/example
         |language: en
         |slides:
         |  - id: $slideid
         |    title: Overview
         |    layout: content
         |    elements:
         |      - role: title
         |        text: Overview
         |      - role: figure
         |        asset: infographic
         |""".stripMargin
    )
    val descriptor = root.resolve("media.yaml")
    _write(descriptor, _presentation_descriptor)
    descriptor
  }

  private def _write_visual_page_fixture(root: Path): Path = {
    _write(root.resolve("article.dox"), "Article")
    _write(root.resolve("article.pdf"), "PDF")
    _write_png(root.resolve("assets/infographic.png"))
    _write(root.resolve("sources/research.txt"), "research")
    _write(root.resolve("template.pptx"), "template")
    val catalog = _visual_catalog
    val page = CozyVisualPage.Page(
      "overview",
      "article/example",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "sequence",
        Vector(
          CozyVisualPage.Node("discover", "step", "Discover", Vector("research")),
          CozyVisualPage.Node("apply", "step", "Apply", Vector("research"))
        ),
        Vector(CozyVisualPage.Relation("next", "next", "discover", "apply", Vector("research")))
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector(CozyVisualPage.Asset("infographic", "assets/infographic.png", "image/png")),
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )
    _write(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(catalog))
    _write(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("article-pages", Vector(page))))
    _write(root.resolve("binding.json"), _visual_binding_json)
    val descriptor = root.resolve("media.yaml")
    _write(descriptor,
      """schema: cozy.media.v1
        |knowledge:
        |  id: article/example
        |  source: article.dox
        |profiles:
        |  business:
        |    root: publication
        |    presentation:
        |      template: template.pptx
        |      renderer:
        |        name: fake
        |        version: "1"
        |        command: [fake-renderer]
        |receipt:
        |  inputs:
        |    - id: visual-pages
        |      role: visual-page-set
        |      path: visual-pages.json
        |      normalization: structured-document
        |    - id: catalog
        |      role: catalog
        |      path: catalog.json
        |      normalization: structured-document
        |    - id: binding
        |      role: binding
        |      path: binding.json
        |      normalization: bytes
        |    - id: template
        |      role: template
        |      path: template.pptx
        |      normalization: bytes
        |  producer:
        |    profile: business
        |    renderer:
        |      name: fake
        |      version: "1"
        |resources:
        |  - id: article-pdf
        |    kind: document
        |    language: en
        |    source: article.pdf
        |    output: article.pdf
        |    build: prebuilt
        |    publications:
        |      business: article.pdf
        |  - id: infographic
        |    kind: infographic
        |    language: en
        |    source: assets/infographic.png
        |    output: assets/infographic.png
        |    build: prebuilt
        |  - id: slides
        |    kind: presentation
        |    language: en
        |    source: visual-pages.json
        |    output: target/rendered/article.pptx
        |    build: presentation
        |    publications:
        |      business: article.pptx
        |    presentation:
        |      contract: visual-page-v1
        |      profile: business
        |      catalog: catalog.json
        |      binding: binding.json
        |      slideImages: target/slides
        |      montage: target/rendered/montage.png
        |      rendererManifest: target/rendered/renderer-manifest.json
        |      reviewManifest: review/manifest.json
        |      reviewState: review/state.yaml
        |      articlePdf: article-pdf
        |      infographic: infographic
        |""".stripMargin
    )
    descriptor
  }

  private val _visual_page_slots = Vector("knowledge", "nodes", "relations", "assets", "parameters")

  private def _visual_binding_json: String = {
    val patterns = Vector("flow-horizontal", "flow-vertical", "mapping-columns").map { pattern =>
      val slots = _visual_page_slots.map(slot => s"""{"semanticSlot":"$slot","physicalSlot":"$slot-slot"}""").mkString("[", ",", "]")
      s"""{"visualPattern":"$pattern","slots":$slots}"""
    }.mkString("[", ",", "]")
    s"""{"schema":"cozy.visual-page.binding.v1","version":1,"id":"business-default","profile":"business","catalog":{"id":"core","revision":1},"patterns":$patterns}"""
  }

  private def _visual_catalog: CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "core",
    1,
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


  private def _write_pptx(path: Path, title: String, asset: Path, slidetarget: String = "slides/slide1.xml", extraslide: Boolean = false, hostiledtd: Boolean = false, extrapresentationrelation: Boolean = false, extrasliderelation: Boolean = false, orphanmedia: Boolean = false, structuralescape: Boolean = false, media: Vector[Path] = Vector.empty): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val output = new ZipOutputStream(Files.newOutputStream(path))
    val selected = if (media.nonEmpty) media else Vector(asset)
    try {
      _zip(output, "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>")
      _zip(output, "ppt/presentation.xml", "<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><p:sldIdLst><p:sldId id=\"256\" r:id=\"rId1\"/></p:sldIdLst></p:presentation>")
      val presentationextra = if (extrapresentationrelation) "<Relationship Id=\"rIdExtra\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide1.xml\"/>" else if (structuralescape) "<Relationship Id=\"rIdStructural\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../outside.xml\"/>" else ""
      val presentationrelations = s"""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="$slidetarget"/>$presentationextra</Relationships>"""
      _zip(output, "ppt/_rels/presentation.xml.rels", presentationrelations)
      val xml = if (hostiledtd) s"""<!DOCTYPE p:sld [<!ENTITY injected SYSTEM "file:///never-read">]><p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:cSld><p:spTree><a:p><a:r><a:t>&injected;</a:t></a:r></a:p><a:blip r:embed="rId1"/></p:spTree></p:cSld></p:sld>""" else s"""<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:cSld><p:spTree><a:p><a:r><a:t>$title</a:t></a:r></a:p><a:blip r:embed="rId1"/></p:spTree></p:cSld></p:sld>"""
      val blips = selected.indices.map(index => s"""<a:blip r:embed="rId${index + 1}"/>""").mkString
      _zip(output, "ppt/slides/slide1.xml", xml.replace("""<a:blip r:embed="rId1"/>""", blips))
      val sliderelations = s"""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>${if (extrasliderelation) "<Relationship Id=\"rIdExtra\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image1.png\"/>" else ""}</Relationships>"""
      val images = selected.indices.map(index => s"""<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image${index + 1}.png"/>""").mkString
      _zip(output, "ppt/slides/_rels/slide1.xml.rels", sliderelations.replace("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>""", images))
      if (extraslide) _zip(output, "ppt/slides/slide2.xml", xml)
      selected.zipWithIndex.foreach { case (path, index) =>
        output.putNextEntry(new ZipEntry(s"ppt/media/image${index + 1}.png"))
        output.write(Files.readAllBytes(path))
        output.closeEntry()
      }
      if (orphanmedia) {
        output.putNextEntry(new ZipEntry("ppt/media/orphan.png"))
        output.write(Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x02))
        output.closeEntry()
      }
    } finally output.close()
  }

  private def _zip(output: ZipOutputStream, name: String, value: String): Unit = {
    output.putNextEntry(new ZipEntry(name))
    output.write(value.getBytes(StandardCharsets.UTF_8))
    output.closeEntry()
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private val _presentation_descriptor =
    """schema: cozy.media.v1
      |knowledge:
      |  id: article/example
      |  source: article.dox
      |profiles:
      |  business:
      |    root: publication
      |    presentation:
      |      template: template.pptx
      |      renderer:
      |        name: fake
      |        version: "1"
      |        command: [fake-renderer]
      |receipt:
      |  inputs:
      |    - id: slide-ir
      |      role: slide-ir
      |      path: slide-ir.yaml
      |      normalization: structured-document
      |    - id: template
      |      role: template
      |      path: template.pptx
      |      normalization: bytes
      |  producer:
      |    profile: business
      |    renderer:
      |      name: fake
      |      version: "1"
      |resources:
      |  - id: article-pdf
      |    kind: document
      |    language: en
      |    source: article.pdf
      |    output: article.pdf
      |    build: prebuilt
      |    publications:
      |      business: article.pdf
      |  - id: infographic
      |    kind: infographic
      |    language: en
      |    source: infographic.png
      |    output: infographic.png
      |    build: prebuilt
      |  - id: slides
      |    kind: presentation
      |    language: en
      |    source: slide-ir.yaml
      |    output: target/rendered/article.pptx
      |    build: presentation
      |    publications:
      |      business: article.pptx
      |    presentation:
      |      profile: business
      |      slideImages: target/slides
      |      montage: target/rendered/montage.png
      |      rendererManifest: target/rendered/renderer-manifest.json
      |      reviewManifest: review/manifest.json
      |      reviewState: review/state.yaml
      |      articlePdf: article-pdf
      |      infographic: infographic
      |""".stripMargin
}
