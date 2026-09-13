package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectExportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project export" should {
    "atomically publish the portable Article review bundle" in {
      _with_temp_dir("cozy-document-project-export-bundle") { root =>
        Given("a selected Article review project with strict current accepted native evidence")
        val project = _accepted_project(root, "export-current")
        val output = project.resolve("target/document-project/article-review.html")
        val bundle = root.resolve("portable-bundle")

        When("export writes a new opaque-target bundle")
        val response = _execute(List("document-project", "export", project.toString, "--target", "preview", "--save", bundle.toString))
        val verified = CozyDocumentProjectExport.verifyBundle(bundle)

        Then("exactly the manifest, receipt, and preserved normalized HTML are installed")
        response should include("target: preview")
        _bundle_files(bundle) shouldBe Set("manifest.yaml", "receipt.yaml", "work-products/article-review-html/article-review.html")
        Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")) shouldBe Files.readAllBytes(output)
        Files.readString(bundle.resolve("manifest.yaml"), StandardCharsets.UTF_8) should include("identity: cozy.document-project-export-manifest.v1")
        Files.readString(bundle.resolve("receipt.yaml"), StandardCharsets.UTF_8) should include("identity: cozy.document-project-export-receipt.v1")
        verified.target shouldBe "preview"
        verified.outputsha256 shouldBe _sha256(output)
      }
    }

    "require --save and a new direct destination without compatibility output" in {
      _with_temp_dir("cozy-document-project-export-destination") { root =>
        Given("an accepted project, an existing directory, and a symbolic-link destination")
        val project = _accepted_project(root, "export-destination")
        val existing = Files.createDirectory(root.resolve("existing-bundle"))
        val external = Files.createDirectory(root.resolve("external-bundle"))
        val symbolic = root.resolve("symbolic-bundle")
        Files.createSymbolicLink(symbolic, external)

        When("export omits --save or selects an existing or symbolic destination")
        val missing = _failure(List("document-project", "export", project.toString, "--target", "preview"))
        val existingfailure = _failure(List("document-project", "export", project.toString, "--target", "preview", "--save", existing.toString))
        val symbolicfailure = _failure(List("document-project", "export", project.toString, "--target", "preview", "--save", symbolic.toString))

        Then("each request fails closed and leaves no partial bundle")
        _diagnostic_tokens(missing) shouldBe Vector("DP-CLI-002")
        _diagnostic_tokens(existingfailure) shouldBe Vector("DP-PATH-001")
        _diagnostic_tokens(symbolicfailure) shouldBe Vector("DP-PATH-001")
        _bundle_files(existing) shouldBe Set.empty
        _bundle_files(external) shouldBe Set.empty
      }
    }

    "reject stale, missing, and unsafe admitted inputs without a partial bundle" in {
      _with_temp_dir("cozy-document-project-export-input-admission") { root =>
        Given("three accepted projects with stale, missing, and symbolic Article authority")
        val stale = _accepted_project(root, "export-stale")
        val missing = _accepted_project(root, "export-missing")
        val unsafe = _accepted_project(root, "export-unsafe")
        Files.writeString(stale.resolve("index.dox"), "changed after acceptance\n", StandardCharsets.UTF_8)
        Files.delete(missing.resolve("index.dox"))
        val external = root.resolve("external-index.dox")
        Files.writeString(external, "external\n", StandardCharsets.UTF_8)
        Files.delete(unsafe.resolve("index.dox"))
        Files.createSymbolicLink(unsafe.resolve("index.dox"), external)
        val staledestination = root.resolve("stale-bundle")
        val missingdestination = root.resolve("missing-bundle")
        val unsafedestination = root.resolve("unsafe-bundle")

        When("export evaluates each invalid retained admission")
        val stalefailure = _failure(List("document-project", "export", stale.toString, "--target", "preview", "--save", staledestination.toString))
        val missingfailure = _failure(List("document-project", "export", missing.toString, "--target", "preview", "--save", missingdestination.toString))
        val unsafefailure = _failure(List("document-project", "export", unsafe.toString, "--target", "preview", "--save", unsafedestination.toString))

        Then("all invalid source states are rejected before any destination exists")
        _diagnostic_tokens(stalefailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(missingfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(unsafefailure) shouldBe Vector("DP-OP-001")
        Files.exists(staledestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(missingdestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(unsafedestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "derive opaque project-aware invalidation for source selection and retained production evidence" in {
      _with_temp_dir("cozy-document-project-export-currentness") { root =>
        Given("three independently exported accepted Article review bundles")
        val sourceproject = _accepted_project(root, "export-source")
        val selectionproject = _accepted_project(root, "export-selection")
        val productionproject = _accepted_project(root, "export-production")
        val sourcebundle = _export(sourceproject, root.resolve("source-bundle"))
        val selectionbundle = _export(selectionproject, root.resolve("selection-bundle"))
        val productionbundle = _export(productionproject, root.resolve("production-bundle"))
        Files.writeString(sourceproject.resolve("index.dox"), "source changed\n", StandardCharsets.UTF_8)
        _deactivate_article_review(selectionproject)
        val attempt = _relative_files(productionproject.resolve("evidence/attempts")).head
        val attemptpath = productionproject.resolve("evidence/attempts").resolve(attempt)
        Files.writeString(attemptpath, Files.readString(attemptpath, StandardCharsets.UTF_8).replace("cozy.document-project.native-receipt.v1", "forged-receipt"), StandardCharsets.UTF_8)

        When("currentness compares opaque authority fingerprints against the projects")
        val source = CozyDocumentProjectExport.currentness(sourceproject, CozyDocumentProject._load_project(sourceproject), sourcebundle)
        val selection = CozyDocumentProjectExport.currentness(selectionproject, CozyDocumentProject._load_project(selectionproject), selectionbundle)
        val production = CozyDocumentProjectExport.currentness(productionproject, CozyDocumentProject._load_project(productionproject), productionbundle)

        Then("source, selection, and retained production evidence invalidate without private consumer data")
        source.sourceauthority shouldBe "stale"
        selection.selection shouldBe "stale"
        production.retainedproductionevidence shouldBe "stale"
        Files.readString(sourcebundle.resolve("receipt.yaml"), StandardCharsets.UTF_8) should not include "index.dox"
        Files.readString(productionbundle.resolve("receipt.yaml"), StandardCharsets.UTF_8) should not include "evidence/attempts"
      }
    }

    "retain retained production currentness when accepted-attempt diagnostics change" in {
      _with_temp_dir("cozy-document-project-export-diagnostics") { root =>
        Given("an accepted Article review project and its exported bundle")
        val project = _accepted_project(root, "export-diagnostics")
        val bundle = _export(project, root.resolve("diagnostics-bundle"))
        val attemptname = _relative_files(project.resolve("evidence/attempts")).head
        val attemptpath = project.resolve("evidence/attempts").resolve(attemptname)
        val before = Files.readString(attemptpath, StandardCharsets.UTF_8)
        val after = before.replace(
          "native review projection rendered for validated accepted-evidence closure",
          "retained diagnostic changed after export"
        )

        When("only the retained accepted-attempt diagnostic changes")
        Files.writeString(attemptpath, after, StandardCharsets.UTF_8)
        val descriptor = CozyDocumentProject._load_project(project)
        val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

        Then("retained production evidence remains current while receipt and native identities remain unchanged")
        after should not be before
        after.substring(after.indexOf("receipt:")) shouldBe before.substring(before.indexOf("receipt:"))
        after.split("\\n").toVector.filter(value => value.trim.startsWith("path:") || value.trim.startsWith("sha256:")) shouldBe
          before.split("\\n").toVector.filter(value => value.trim.startsWith("path:") || value.trim.startsWith("sha256:"))
        currentness.retainedproductionevidence shouldBe "current"
      }
    }

    "invalidate source currentness when contentCore moves to a byte-identical local path" in {
      _with_temp_dir("cozy-document-project-export-source-path") { root =>
        Given("an exported project and a byte-identical local replacement for descriptor contentCore")
        val sourceproject = _accepted_project(root, "export-source-path")
        val sourcebundle = _export(sourceproject, root.resolve("source-path-bundle"))
        val descriptor = CozyDocumentProject._load_project(sourceproject)
        val replacementpath = sourceproject.resolve("content/core-en-copy.yaml")
        Files.write(replacementpath, Files.readAllBytes(sourceproject.resolve(descriptor.contentCore)))
        val replaceddescriptor = descriptor.copy(contentCore = "content/core-en-copy.yaml")

        When("currentness evaluates the descriptor with the replacement contentCore path")
        val source = CozyDocumentProjectExport.currentness(sourceproject, replaceddescriptor, sourcebundle)

        Then("the source authority is stale even though the replacement bytes are identical")
        source.sourceauthority shouldBe "stale"
      }
    }

    "reject tampered manifest and exported bytes through the consumer-only verifier" in {
      _with_temp_dir("cozy-document-project-export-consumer") { root =>
        Given("portable bundles from accepted projects with isolated tamper fixtures")
        val manifestproject = _accepted_project(root, "export-manifest")
        val outputproject = _accepted_project(root, "export-output")
        val receiptproject = _accepted_project(root, "export-receipt")
        val missingproject = _accepted_project(root, "export-missing-receipt")
        val symbolicproject = _accepted_project(root, "export-symbolic-output")
        val manifestbundle = _export(manifestproject, root.resolve("manifest-bundle"))
        val outputbundle = _export(outputproject, root.resolve("output-bundle"))
        val receiptbundle = _export(receiptproject, root.resolve("receipt-bundle"))
        val missingbundle = _export(missingproject, root.resolve("missing-bundle"))
        val symbolicbundle = _export(symbolicproject, root.resolve("symbolic-bundle"))
        Files.writeString(manifestbundle.resolve("manifest.yaml"), Files.readString(manifestbundle.resolve("manifest.yaml"), StandardCharsets.UTF_8).replace("target: preview", "target: release"), StandardCharsets.UTF_8)
        Files.writeString(outputbundle.resolve("work-products/article-review-html/article-review.html"), "tampered\n", StandardCharsets.UTF_8)
        Files.writeString(receiptbundle.resolve("receipt.yaml"), "identity: forged\n", StandardCharsets.UTF_8)
        Files.delete(missingbundle.resolve("receipt.yaml"))
        val external = root.resolve("external-exported-output.html")
        Files.writeString(external, "external\n", StandardCharsets.UTF_8)
        val symbolicoutput = symbolicbundle.resolve("work-products/article-review-html/article-review.html")
        Files.delete(symbolicoutput)
        Files.createSymbolicLink(symbolicoutput, external)

        When("a consumer without any Document Project verifies each bundle")
        val manifestfailure = _bundle_failure(manifestbundle)
        val outputfailure = _bundle_failure(outputbundle)
        val receiptfailure = _bundle_failure(receiptbundle)
        val missingfailure = _bundle_failure(missingbundle)
        val symbolicfailure = _bundle_failure(symbolicbundle)
        val manifeststate = CozyDocumentProjectExport.currentness(manifestproject, CozyDocumentProject._load_project(manifestproject), manifestbundle)
        val outputstate = CozyDocumentProjectExport.currentness(outputproject, CozyDocumentProject._load_project(outputproject), outputbundle)

        Then("the verifier rejects malformed, missing, symbolic, and tampered content while currentness marks changed identities stale")
        _diagnostic_tokens(manifestfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(outputfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(receiptfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(missingfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(symbolicfailure) shouldBe Vector("DP-OP-001")
        manifeststate.manifestauthority shouldBe "stale"
        outputstate.exportedbytes shouldBe "stale"
      }
    }
  }

  private def _accepted_project(root: Path, slug: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    val project = parent.resolve(s"$slug.dox")
    val descriptor = project.resolve("document-project.yaml")
    Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - article-review-html"), StandardCharsets.UTF_8)
    _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
    project
  }

  private def _export(project: Path, bundle: Path): Path = { _execute(List("document-project", "export", project.toString, "--target", "preview", "--save", bundle.toString)); bundle }
  private def _deactivate_article_review(project: Path): Unit = { val descriptor = project.resolve("document-project.yaml"); Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts:\n  - article-review-html", "activeOptionalWorkProducts: []"), StandardCharsets.UTF_8) }
  private def _execute(args: List[String]): String = { val bytes = new ByteArrayOutputStream(); Console.withOut(new PrintStream(bytes, true, "UTF-8")) { CozyDocumentProject.execute(args) shouldBe true }; bytes.toString("UTF-8").trim }
  private def _failure(args: List[String]): String = intercept[RuntimeException] { CozyDocumentProject.execute(args) }.getMessage
  private def _bundle_failure(bundle: Path): String = intercept[RuntimeException] { CozyDocumentProjectExport.verifyBundle(bundle) }.getMessage
  private def _diagnostic_tokens(value: String): Vector[String] = """DP-[A-Z]+-\d{3}""".r.findAllIn(value).toVector
  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString
  private def _relative_files(root: Path): Vector[String] = { val stream = Files.list(root); try stream.iterator().asScala.map(_.getFileName.toString).toVector.sorted finally stream.close() }
  private def _bundle_files(root: Path): Set[String] = { if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Set.empty else { val stream = Files.walk(root); try stream.iterator().asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(root.relativize(_).toString.replace('\\', '/')).toSet finally stream.close() } }
  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = { val root = Files.createTempDirectory(name); try body(root) finally _delete(root) }
  private def _delete(path: Path): Unit = if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { val stream = Files.walk(path); try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item => try Files.deleteIfExists(item) catch { case NonFatal(_) => () } } finally stream.close() }
}
