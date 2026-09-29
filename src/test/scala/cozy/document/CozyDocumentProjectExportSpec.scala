package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.{StandardProtocolFamily, UnixDomainSocketAddress}
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 11, 2026
 * @version Sep. 29, 2026
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

    "report all five currentness facets for a fresh accepted export" in {
      _with_temp_dir("cozy-document-project-export-current") { root =>
        Given("a genuine accepted Article review project and its freshly exported bundle")
        val project = _accepted_project(root, "export-currentness")
        val descriptor = CozyDocumentProject._load_project(project)
        val bundle = _export(project, root.resolve("currentness-bundle"))

        When("project-aware currentness evaluates the fresh bundle")
        val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

        Then("source, selection, retained production, manifest, and exported bytes are current")
        currentness shouldBe CozyDocumentProjectExport.Currentness("current", "current", "current", "current", "current")
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

    "reject a symbolic ancestor of an export destination before staging output" in {
      _with_temp_dir("cozy-document-project-export-destination-ancestor") { root =>
        Given("an accepted project and a real nested parent reached through a symbolic ancestor")
        val project = _accepted_project(root, "export-destination-ancestor")
        val actual = Files.createDirectory(root.resolve("actual"))
        val nested = Files.createDirectory(actual.resolve("nested"))
        val alias = root.resolve("alias")
        Files.createSymbolicLink(alias, actual)
        val destination = alias.resolve("nested/new-bundle")
        val realdestination = nested.resolve("new-bundle")

        When("export saves beneath the symbolic ancestor")
        val failure = _failure(List("document-project", "export", project.toString, "--target", "preview", "--save", destination.toString))

        Then("destination admission fails before creating a real, aliased, or staging bundle")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.exists(realdestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        _relative_files(nested) shouldBe Vector.empty
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

    "invalidate source and retained production currentness when each declared source changes" in {
      Vector("content/core-en.yaml", "index.dox", "presentation/visual-pages.yaml", "infographic/infographic.svg").foreach { sourcepath =>
        _with_temp_dir(s"cozy-document-project-export-source-change-${sourcepath.replace('/', '-')}") { root =>
          Given(s"a fresh accepted export whose declared source is $sourcepath")
          val project = _accepted_project(root, "export-source-change")
          val descriptor = CozyDocumentProject._load_project(project)
          val bundle = _export(project, root.resolve("source-change-bundle"))

          When("the declared source bytes receive an appended newline")
          val path = project.resolve(sourcepath)
          Files.writeString(path, Files.readString(path, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
          val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

          Then("source and retained production are stale while selection and bundle bytes remain current")
          currentness shouldBe CozyDocumentProjectExport.Currentness("stale", "current", "stale", "current", "current")
        }
      }
    }

    "invalidate source and retained production currentness when each declared source is removed" in {
      Vector("content/core-en.yaml", "index.dox", "presentation/visual-pages.yaml", "infographic/infographic.svg").foreach { sourcepath =>
        _with_temp_dir(s"cozy-document-project-export-source-removal-${sourcepath.replace('/', '-')}") { root =>
          Given(s"a fresh accepted export whose declared source is $sourcepath")
          val project = _accepted_project(root, "export-source-removal")
          val descriptor = CozyDocumentProject._load_project(project)
          val bundle = _export(project, root.resolve("source-removal-bundle"))

          When("the declared source file is removed after descriptor admission")
          Files.delete(project.resolve(sourcepath))
          val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

          Then("source and retained production are stale without losing the other valid facets")
          currentness shouldBe CozyDocumentProjectExport.Currentness("stale", "current", "stale", "current", "current")
        }
      }
    }

    "preserve all five currentness facets when selected identities are reordered" in {
      _with_temp_dir("cozy-document-project-export-selection-order") { root =>
        Given("an accepted project authored with article review and infographic PNG selected")
        val project = _accepted_project_with_selection(
          root,
          "export-selection-order",
          "activeOptionalWorkProducts:\n  - article-review-html\n  - infographic-png"
        )
        val bundle = _export(project, root.resolve("selection-order-bundle"))
        val descriptorfile = project.resolve("document-project.yaml")
        val before = Files.readString(descriptorfile, StandardCharsets.UTF_8)
        val after = before.replace(
          "activeOptionalWorkProducts:\n  - article-review-html\n  - infographic-png",
          "activeOptionalWorkProducts:\n  - infographic-png\n  - article-review-html"
        )

        When("the same two authored optional identities are reversed and reloaded")
        Files.writeString(descriptorfile, after, StandardCharsets.UTF_8)
        val descriptor = CozyDocumentProject._load_project(project)
        val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

        Then("reordering leaves every currentness facet current without rendering infographic PNG")
        after should not be before
        currentness shouldBe CozyDocumentProjectExport.Currentness("current", "current", "current", "current", "current")
      }
    }

    "invalidate selection alone when infographic PNG is added after Article export" in {
      _with_temp_dir("cozy-document-project-export-selection-membership") { root =>
        Given("an Article-only accepted export")
        val project = _accepted_project(root, "export-selection-membership")
        val bundle = _export(project, root.resolve("selection-membership-bundle"))
        val descriptorfile = project.resolve("document-project.yaml")
        val before = Files.readString(descriptorfile, StandardCharsets.UTF_8)
        val after = before.replace(
          "activeOptionalWorkProducts:\n  - article-review-html",
          "activeOptionalWorkProducts:\n  - article-review-html\n  - infographic-png"
        )

        When("the existing optional infographic PNG identity is added to the descriptor")
        Files.writeString(descriptorfile, after, StandardCharsets.UTF_8)
        val descriptor = CozyDocumentProject._load_project(project)
        val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

        Then("only selection is stale while source, production, manifest, and output remain current")
        after should not be before
        currentness shouldBe CozyDocumentProjectExport.Currentness("current", "stale", "current", "current", "current")
      }
    }

    "invalidate selection and retained production when the registered profile changes" in {
      _with_temp_dir("cozy-document-project-export-selection-profile") { root =>
        Given("a standard-profile accepted Article export")
        val project = _accepted_project(root, "export-selection-profile")
        val bundle = _export(project, root.resolve("selection-profile-bundle"))
        val descriptorfile = project.resolve("document-project.yaml")
        val before = Files.readString(descriptorfile, StandardCharsets.UTF_8)
        val after = before.replace("profile: standard", "profile: bok")

        When("the descriptor is changed to the registered bok profile and reloaded")
        Files.writeString(descriptorfile, after, StandardCharsets.UTF_8)
        val descriptor = CozyDocumentProject._load_project(project)
        val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

        Then("selection and retained production are stale while source and bundle bytes remain current")
        after should not be before
        currentness shouldBe CozyDocumentProjectExport.Currentness("current", "stale", "stale", "current", "current")
      }
    }

    "invalidate retained production alone when only the embedded receipt value changes" in {
      _with_temp_dir("cozy-document-project-export-receipt-value") { root =>
        Given("a genuine accepted Article export with its retained native attempt")
        val project = _accepted_project(root, "export-receipt-value")
        val bundle = _export(project, root.resolve("receipt-value-bundle"))
        val attemptname = _relative_files(project.resolve("evidence/attempts")).head
        val attemptpath = project.resolve("evidence/attempts").resolve(attemptname)
        val before = Files.readString(attemptpath, StandardCharsets.UTF_8)
        val after = before.replace("operation=article.render-review;", "operation=forged;")
        val descriptor = CozyDocumentProject._load_project(project)

        When("only the canonical embedded native receipt operation value is changed")
        Files.writeString(attemptpath, after, StandardCharsets.UTF_8)
        val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

        Then("retained production is stale while source, selection, manifest, and output remain current")
        after should not be before
        after.replace("operation=forged;", "operation=article.render-review;") shouldBe before
        after should include("value: \"operation=forged;")
        after should include("operation: article.render-review")
        after should include("identity: cozy.document-project.native-receipt.v1")
        currentness shouldBe CozyDocumentProjectExport.Currentness("current", "current", "stale", "current", "current")
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
        val receiptstate = CozyDocumentProjectExport.currentness(receiptproject, CozyDocumentProject._load_project(receiptproject), receiptbundle)
        val missingstate = CozyDocumentProjectExport.currentness(missingproject, CozyDocumentProject._load_project(missingproject), missingbundle)

        Then("the verifier rejects malformed, missing, symbolic, and tampered content while currentness marks changed identities stale")
        _diagnostic_tokens(manifestfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(outputfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(receiptfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(missingfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(symbolicfailure) shouldBe Vector("DP-OP-001")
        manifeststate shouldBe CozyDocumentProjectExport.Currentness("current", "current", "current", "stale", "current")
        outputstate shouldBe CozyDocumentProjectExport.Currentness("current", "current", "current", "current", "stale")
        receiptstate shouldBe CozyDocumentProjectExport.Currentness("invalid", "invalid", "invalid", "invalid", "invalid")
        missingstate shouldBe CozyDocumentProjectExport.Currentness("invalid", "invalid", "invalid", "invalid", "invalid")
      }
    }

    "reject a symbolic ancestor of a consumer bundle while accepting its direct path" in {
      _with_temp_dir("cozy-document-project-export-consumer-ancestor") { root =>
        Given("an otherwise valid bundle under a real nested parent with a symbolic ancestor alias")
        val project = _accepted_project(root, "export-consumer-ancestor")
        val actual = Files.createDirectory(root.resolve("actual"))
        val nested = Files.createDirectory(actual.resolve("nested"))
        val bundle = _export(project, nested.resolve("bundle"))
        val alias = root.resolve("alias")
        Files.createSymbolicLink(alias, actual)
        val aliasedbundle = alias.resolve("nested/bundle")
        val sourcebytes = Files.readAllBytes(project.resolve("index.dox"))
        val manifestbytes = Files.readAllBytes(bundle.resolve("manifest.yaml"))
        val receiptbytes = Files.readAllBytes(bundle.resolve("receipt.yaml"))
        val outputbytes = Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html"))

        When("a consumer verifies the direct path and then its symbolic-ancestor alias")
        val verified = CozyDocumentProjectExport.verifyBundle(bundle)
        val failure = _bundle_failure(aliasedbundle)

        Then("the direct bundle succeeds, the alias fails once, and source and bundle bytes are unchanged")
        verified.target shouldBe "preview"
        _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
        Files.readAllBytes(project.resolve("index.dox")) shouldBe sourcebytes
        Files.readAllBytes(bundle.resolve("manifest.yaml")) shouldBe manifestbytes
        Files.readAllBytes(bundle.resolve("receipt.yaml")) shouldBe receiptbytes
        Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")) shouldBe outputbytes
      }
    }

    "verify a relocated genuine bundle after its private project is removed" in {
      _with_temp_dir("cozy-document-project-export-relocation") { root =>
        Given("a genuine accepted export and a second direct consumer directory")
        val project = _accepted_project(root, "export-relocation")
        val bundle = _export(project, root.resolve("original-bundle"))
        val baseline = CozyDocumentProjectExport.verifyBundle(bundle)
        val bytes = _bundle_bytes(bundle)
        val relocatedparent = Files.createDirectory(root.resolve("relocated"))
        val relocated = relocatedparent.resolve("bundle")

        When("the consumer relocates the bundle and deletes only its fixture project parent")
        Files.move(bundle, relocated)
        _delete(project.getParent)
        val verified = CozyDocumentProjectExport.verifyBundle(relocated)

        Then("bundle-only verification retains its identity and bytes without project authority")
        Files.exists(bundle, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.getParent, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("document-project.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        verified shouldBe baseline
        _bundle_bytes(relocated) shouldBe bytes
      }
    }

    "reject duplicate nested manifest mapping keys" in {
      val cases = Vector[(String, String, String => String)](
        ("identity", "repeated identical value", _.replace("  - identity: article-review-html", "  - identity: article-review-html\n    identity: article-review-html")),
        ("identity", "conflicting earlier value with original valid last value", _.replace("  - identity: article-review-html", "  - identity: another-product\n    identity: article-review-html")),
        ("role", "repeated identical value", _.replace("    role: article-review", "    role: article-review\n    role: article-review")),
        ("role", "conflicting earlier value with original valid last value", _.replace("    role: article-review", "    role: another-role\n    role: article-review")),
        ("mediaType", "repeated identical value", _.replace("    mediaType: text/html", "    mediaType: text/html\n    mediaType: text/html")),
        ("mediaType", "conflicting earlier value with original valid last value", _.replace("    mediaType: text/html", "    mediaType: text/plain\n    mediaType: text/html")),
        ("path", "repeated identical value", _.replace("    path: work-products/article-review-html/article-review.html", "    path: work-products/article-review-html/article-review.html\n    path: work-products/article-review-html/article-review.html")),
        ("path", "conflicting earlier value with original valid last value", _.replace("    path: work-products/article-review-html/article-review.html", "    path: work-products/article-review-html/other.html\n    path: work-products/article-review-html/article-review.html")),
        ("sha256", "repeated identical value", _.replaceFirst("(    sha256: )([0-9a-f]{64})", "$1$2\n$1$2")),
        ("sha256", "conflicting earlier value with original valid last value", _.replaceFirst("(    sha256: )([0-9a-f]{64})", "$1" + ("f" * 64) + "\n$1$2"))
      )
      cases.zipWithIndex.foreach { case ((key, form, mutate), index) =>
        _with_temp_dir(s"cozy-document-project-export-duplicate-manifest-$index") { root =>
          Given(s"a genuine accepted export with a $form duplicate manifest $key mapping key")
          val project = _accepted_project(root, s"export-duplicate-manifest-$index")
          val descriptor = CozyDocumentProject._load_project(project)
          val bundle = _export(project, root.resolve("bundle"))
          val verified = CozyDocumentProjectExport.verifyBundle(bundle)
          val baseline = _bundle_bytes(bundle)
          val manifestpath = bundle.resolve("manifest.yaml")
          val receiptpath = bundle.resolve("receipt.yaml")
          val manifestbefore = Files.readString(manifestpath, StandardCharsets.UTF_8)
          val receiptbefore = Files.readString(receiptpath, StandardCharsets.UTF_8)

          When("the public manifest gains the duplicate while its receipt binds the mutated manifest bytes")
          val manifestafter = mutate(manifestbefore)
          manifestafter should not be manifestbefore
          Files.writeString(manifestpath, manifestafter, StandardCharsets.UTF_8)
          val receiptafter = receiptbefore.replaceFirst("(  sha256: )[0-9a-f]{64}", "$1" + _sha256(manifestpath))
          receiptafter should not be receiptbefore
          Files.writeString(receiptpath, receiptafter, StandardCharsets.UTF_8)
          val mutated = _bundle_bytes(bundle)
          val failure = _bundle_failure(bundle)
          val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

          Then("the consumer rejects the duplicate before hash comparison, preserves bytes, invalidates currentness, and restores the genuine bundle")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          failure should include("export manifest contains duplicate mapping keys")
          mutated.manifest should not be baseline.manifest
          mutated.receipt should not be baseline.receipt
          mutated.output shouldBe baseline.output
          _bundle_bytes(bundle) shouldBe mutated
          currentness shouldBe CozyDocumentProjectExport.Currentness("invalid", "invalid", "invalid", "invalid", "invalid")
          Files.write(manifestpath, baseline.manifest.toArray)
          Files.write(receiptpath, baseline.receipt.toArray)
          CozyDocumentProjectExport.verifyBundle(bundle) shouldBe verified
          _bundle_bytes(bundle) shouldBe baseline
        }
      }
    }

    "reject duplicate nested receipt mapping keys" in {
      val cases = Vector[(String, String, String => String)](
        ("manifest.identity", "repeated identical value", _.replace("  identity: cozy.document-project-export-manifest.v1", "  identity: cozy.document-project-export-manifest.v1\n  identity: cozy.document-project-export-manifest.v1")),
        ("manifest.identity", "conflicting earlier value with original valid last value", _.replace("  identity: cozy.document-project-export-manifest.v1", "  identity: forged-manifest\n  identity: cozy.document-project-export-manifest.v1")),
        ("manifest.sha256", "repeated identical value", _.replaceFirst("(  sha256: )([0-9a-f]{64})", "$1$2\n$1$2")),
        ("manifest.sha256", "conflicting earlier value with original valid last value", _.replaceFirst("(  sha256: )([0-9a-f]{64})", "$1" + ("f" * 64) + "\n$1$2")),
        ("exportedBytes.path", "repeated identical value", _.replace("  - path: work-products/article-review-html/article-review.html", "  - path: work-products/article-review-html/article-review.html\n    path: work-products/article-review-html/article-review.html")),
        ("exportedBytes.path", "conflicting earlier value with original valid last value", _.replace("  - path: work-products/article-review-html/article-review.html", "  - path: wrong.html\n    path: work-products/article-review-html/article-review.html")),
        ("exportedBytes.sha256", "repeated identical value", _.replaceFirst("(exportedBytes:\\n  - path: .*\\n    sha256: )([0-9a-f]{64})", "$1$2\n    sha256: $2")),
        ("exportedBytes.sha256", "conflicting earlier value with original valid last value", _.replaceFirst("(exportedBytes:\\n  - path: .*\\n    sha256: )([0-9a-f]{64})", "$1" + ("f" * 64) + "\n    sha256: $2")),
        ("authority.sourceAuthoritySha256", "repeated identical value", _.replaceFirst("(sourceAuthoritySha256: )([0-9a-f]{64})", "$1$2\n  $1$2")),
        ("authority.sourceAuthoritySha256", "conflicting earlier value with original valid last value", _.replaceFirst("(sourceAuthoritySha256: )([0-9a-f]{64})", "$1" + ("f" * 64) + "\n  $1$2")),
        ("authority.selectionSha256", "repeated identical value", _.replaceFirst("(selectionSha256: )([0-9a-f]{64})", "$1$2\n  $1$2")),
        ("authority.selectionSha256", "conflicting earlier value with original valid last value", _.replaceFirst("(selectionSha256: )([0-9a-f]{64})", "$1" + ("f" * 64) + "\n  $1$2")),
        ("authority.retainedProductionReceiptSha256", "repeated identical value", _.replaceFirst("(retainedProductionReceiptSha256: )([0-9a-f]{64})", "$1$2\n  $1$2")),
        ("authority.retainedProductionReceiptSha256", "conflicting earlier value with original valid last value", _.replaceFirst("(retainedProductionReceiptSha256: )([0-9a-f]{64})", "$1" + ("f" * 64) + "\n  $1$2"))
      )
      cases.zipWithIndex.foreach { case ((key, form, mutate), index) =>
        _with_temp_dir(s"cozy-document-project-export-duplicate-receipt-$index") { root =>
          Given(s"a genuine accepted export with a $form duplicate receipt $key mapping key")
          val project = _accepted_project(root, s"export-duplicate-receipt-$index")
          val descriptor = CozyDocumentProject._load_project(project)
          val bundle = _export(project, root.resolve("bundle"))
          val verified = CozyDocumentProjectExport.verifyBundle(bundle)
          val baseline = _bundle_bytes(bundle)
          val receiptpath = bundle.resolve("receipt.yaml")
          val receiptbefore = Files.readString(receiptpath, StandardCharsets.UTF_8)

          When("the public receipt gains the duplicate while manifest and output bytes stay genuine")
          val receiptafter = mutate(receiptbefore)
          receiptafter should not be receiptbefore
          Files.writeString(receiptpath, receiptafter, StandardCharsets.UTF_8)
          val mutated = _bundle_bytes(bundle)
          val failure = _bundle_failure(bundle)
          val currentness = CozyDocumentProjectExport.currentness(project, descriptor, bundle)

          Then("the consumer rejects the duplicate before receipt validation, preserves bytes, invalidates currentness, and restores the genuine bundle")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          failure should include("export receipt contains duplicate mapping keys")
          mutated.manifest shouldBe baseline.manifest
          mutated.receipt should not be baseline.receipt
          mutated.output shouldBe baseline.output
          _bundle_bytes(bundle) shouldBe mutated
          currentness shouldBe CozyDocumentProjectExport.Currentness("invalid", "invalid", "invalid", "invalid", "invalid")
          Files.write(receiptpath, baseline.receipt.toArray)
          CozyDocumentProjectExport.verifyBundle(bundle) shouldBe verified
          _bundle_bytes(bundle) shouldBe baseline
        }
      }
    }

    "reject every malformed manifest mapping before receipt-hash comparison" in {
      val cases = Vector[(String, String, String => String)](
        ("unsupported Work Product identity", "export manifest Work Product mapping is invalid", _.replace("identity: article-review-html", "identity: another-product")),
        ("unsupported Work Product role", "export manifest Work Product mapping is invalid", _.replace("role: article-review", "role: another-role")),
        ("unsupported Work Product media type", "export manifest Work Product mapping is invalid", _.replace("mediaType: text/html", "mediaType: text/plain")),
        ("traversing Work Product path", "export manifest Work Product mapping is invalid", _.replace("path: work-products/article-review-html/article-review.html", "path: ../article-review.html")),
        ("absolute Work Product path", "export manifest Work Product mapping is invalid", _.replace("path: work-products/article-review-html/article-review.html", "path: /article-review.html")),
        ("noncanonical Work Product path", "export manifest Work Product mapping is invalid", _.replace("path: work-products/article-review-html/article-review.html", "path: ./work-products/article-review-html/article-review.html")),
        ("short Work Product hash", "export manifest Work Product SHA-256 is invalid", _.replaceFirst("sha256: [0-9a-f]{64}", "sha256: short")),
        ("uppercase Work Product hash", "export manifest Work Product SHA-256 is invalid", _.replaceFirst("sha256: [0-9a-f]{64}", "sha256: " + ("A" * 64))),
        ("nonhex Work Product hash", "export manifest Work Product SHA-256 is invalid", _.replaceFirst("sha256: [0-9a-f]{64}", "sha256: " + ("g" * 64))),
        ("unsupported manifest identity", "export manifest identity or fields are invalid", _.replace("identity: cozy.document-project-export-manifest.v1", "identity: forged-manifest")),
        ("non-slug target", "export manifest target is invalid", _.replace("target: preview", "target: invalid target")),
        ("unknown Work Product field", "export manifest Work Product mapping is invalid", _.replace("    sha256:", "    unsupported: value\n    sha256:")),
        ("duplicate Work Product", "export manifest must contain exactly one Work Product", value => value + value.substring(value.indexOf("  - identity:"))),
        ("unknown top-level field", "export manifest top-level keys are invalid", _.replace("target: preview\n", "target: preview\nunsupported: value\n")),
        ("reordered top-level fields", "export manifest top-level keys are invalid", _.replace("identity: cozy.document-project-export-manifest.v1\ntarget: preview", "target: preview\nidentity: cozy.document-project-export-manifest.v1"))
      )
      cases.zipWithIndex.foreach { case ((name, cause, mutate), index) =>
        _with_temp_dir(s"cozy-document-project-export-manifest-$index") { root =>
          Given(s"a genuine accepted export for the $name manifest mutation")
          val project = _accepted_project(root, s"export-manifest-$index")
          val bundle = _export(project, root.resolve("bundle"))
          CozyDocumentProjectExport.verifyBundle(bundle).target shouldBe "preview"
          val baseline = _bundle_bytes(bundle)
          val manifestpath = bundle.resolve("manifest.yaml")
          val before = Files.readString(manifestpath, StandardCharsets.UTF_8)

          When("one manifest field is changed without rebinding the receipt")
          val after = mutate(before)
          after should not be before
          Files.writeString(manifestpath, after, StandardCharsets.UTF_8)
          val failure = _bundle_failure(bundle)

          Then("the consumer reports the frozen manifest diagnostic and leaves its fixture bytes intact")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          failure should include(cause)
          Files.readString(manifestpath, StandardCharsets.UTF_8) shouldBe after
          Files.readAllBytes(bundle.resolve("receipt.yaml")).toVector shouldBe baseline.receipt
          Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")).toVector shouldBe baseline.output
        }
      }
    }

    "reject every malformed receipt mapping without private project access" in {
      val cases = Vector[(String, String, String => String)](
        ("unsupported receipt identity", "export receipt identity or fields are invalid", _.replace("identity: cozy.document-project-export-receipt.v1", "identity: forged-receipt")),
        ("unsupported bound manifest identity", "export receipt manifest identity is invalid", _.replace("  identity: cozy.document-project-export-manifest.v1", "  identity: forged-manifest")),
        ("wrong exported byte path", "export receipt exported byte path is invalid", _.replace("  - path: work-products/article-review-html/article-review.html", "  - path: wrong.html")),
        ("duplicate exported byte identity", "export receipt must contain exactly one exported byte identity", value => value.replace("authority:\n", value.substring(value.indexOf("  - path:"), value.indexOf("authority:")) + "authority:\n")),
        ("unknown manifest binding field", "export receipt manifest identity is invalid", _.replace("exportedBytes:", "  unsupported: value\nexportedBytes:")),
        ("unknown exported byte field", "export receipt exported byte path is invalid", _.replace("    sha256:", "    unsupported: value\n    sha256:")),
        ("unknown authority field", "export receipt authority is invalid", _.replace("authority:\n", "authority:\n  unsupported: value\n")),
        ("unknown top-level field", "export receipt top-level keys are invalid", _.replace("identity: cozy.document-project-export-receipt.v1\n", "identity: cozy.document-project-export-receipt.v1\nunsupported: value\n")),
        ("short manifest hash", "export receipt manifest SHA-256 is invalid", _.replaceFirst("  sha256: [0-9a-f]{64}", "  sha256: short")),
        ("nonhex exported byte hash", "export receipt exported byte SHA-256 is invalid", _.replaceFirst("(exportedBytes:\\n  - path: .*\\n    sha256: )[0-9a-f]{64}", "$1" + ("g" * 64))),
        ("uppercase source authority hash", "export receipt source authority SHA-256 is invalid", _.replaceFirst("(sourceAuthoritySha256: )[0-9a-f]{64}", "$1" + ("A" * 64))),
        ("short selection hash", "export receipt selection SHA-256 is invalid", _.replaceFirst("(selectionSha256: )[0-9a-f]{64}", "$1short")),
        ("nonhex retained production receipt hash", "export receipt retained production receipt SHA-256 is invalid", _.replaceFirst("(retainedProductionReceiptSha256: )[0-9a-f]{64}", "$1" + ("g" * 64))),
        ("disagreeing valid exported byte hash", "export manifest and receipt output identities disagree", _.replaceFirst("(exportedBytes:\\n  - path: .*\\n    sha256: )[0-9a-f]{64}", "$1" + ("f" * 64))),
        ("tampered valid manifest authority hash", "export receipt manifest authority is stale or tampered", _.replaceFirst("  sha256: [0-9a-f]{64}", "  sha256: " + ("f" * 64))),
        ("reordered top-level fields", "export receipt top-level keys are invalid", _.replace("identity: cozy.document-project-export-receipt.v1\nmanifest:", "manifest:\nidentity: cozy.document-project-export-receipt.v1"))
      )
      cases.zipWithIndex.foreach { case ((name, cause, mutate), index) =>
        _with_temp_dir(s"cozy-document-project-export-receipt-$index") { root =>
          Given(s"a genuine accepted export for the $name receipt mutation")
          val project = _accepted_project(root, s"export-receipt-$index")
          val bundle = _export(project, root.resolve("bundle"))
          CozyDocumentProjectExport.verifyBundle(bundle).target shouldBe "preview"
          val baseline = _bundle_bytes(bundle)
          val receiptpath = bundle.resolve("receipt.yaml")
          val before = Files.readString(receiptpath, StandardCharsets.UTF_8)

          When("one receipt field is changed while the manifest and exported bytes stay genuine")
          val after = mutate(before)
          after should not be before
          Files.writeString(receiptpath, after, StandardCharsets.UTF_8)
          val failure = _bundle_failure(bundle)

          Then("the bundle-only verifier reports the frozen receipt diagnostic without changing bytes")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          failure should include(cause)
          Files.readString(receiptpath, StandardCharsets.UTF_8) shouldBe after
          Files.readAllBytes(bundle.resolve("manifest.yaml")).toVector shouldBe baseline.manifest
          Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")).toVector shouldBe baseline.output
        }
      }
    }

    "reject each extra bundle entry while preserving the genuine components" in {
      val cases = Vector[(String, Path => Unit)](
        ("extra root file", bundle => Files.writeString(bundle.resolve("extra.yaml"), "extra\n", StandardCharsets.UTF_8)),
        ("extra work-products file", bundle => Files.writeString(bundle.resolve("work-products/private.yaml"), "extra\n", StandardCharsets.UTF_8)),
        ("extra article-review-html file", bundle => Files.writeString(bundle.resolve("work-products/article-review-html/unlisted.html"), "extra\n", StandardCharsets.UTF_8)),
        ("extra empty directory", bundle => Files.createDirectory(bundle.resolve("unlisted-directory")))
      )
      cases.zipWithIndex.foreach { case ((name, mutate), index) =>
        _with_temp_dir(s"cozy-document-project-export-extra-$index") { root =>
          Given(s"a genuine accepted export for the $name fixture")
          val project = _accepted_project(root, s"export-extra-$index")
          val bundle = _export(project, root.resolve("bundle"))
          CozyDocumentProjectExport.verifyBundle(bundle).target shouldBe "preview"
          val baseline = _bundle_bytes(bundle)

          When("one unlisted node is added to the closed bundle tree")
          mutate(bundle)
          val failure = _bundle_failure(bundle)

          Then("the consumer rejects the file shape and does not change the original components")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          failure should include("export bundle has an invalid file shape")
          _bundle_bytes(bundle) shouldBe baseline
        }
      }
    }

    "reject UNIX socket nodes at every bundle tree level" in {
      val cases = Vector[(String, Path => Path)](
        ("bundle root", bundle => bundle.resolve("socket")),
        ("work-products directory", bundle => bundle.resolve("work-products/socket")),
        ("article-review-html directory", bundle => bundle.resolve("work-products/article-review-html/socket"))
      )
      cases.zipWithIndex.foreach { case ((name, destination), index) =>
        _with_temp_dir("socket") { root =>
          Given(s"a genuine accepted export and a UNIX socket for the $name")
          val project = _accepted_project(root, s"export-socket-$index")
          val bundle = _export(project, root.resolve("bundle"))
          val baseline = CozyDocumentProjectExport.verifyBundle(bundle)
          val bytes = _bundle_bytes(bundle)
          val socket = root.resolve("socket")
          val address = Paths.get("").toAbsolutePath.normalize().relativize(socket)
          val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)

          When("the direct UNIX socket is moved into the bundle tree")
          try {
            channel.bind(UnixDomainSocketAddress.of(address))
            val socketpath = destination(bundle)
            Files.move(socket, socketpath)
            val attributes = Files.readAttributes(socketpath, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
            val failure = _bundle_failure(bundle)

            Then("the unsupported node is rejected and removal restores the unchanged valid bundle")
            attributes.isOther shouldBe true
            Files.isRegularFile(socketpath, LinkOption.NOFOLLOW_LINKS) shouldBe false
            Files.isDirectory(socketpath, LinkOption.NOFOLLOW_LINKS) shouldBe false
            Files.isSymbolicLink(socketpath) shouldBe false
            _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
            failure should include("export bundle must contain only direct regular files and directories")
            Files.delete(socketpath)
            CozyDocumentProjectExport.verifyBundle(bundle) shouldBe baseline
            _bundle_bytes(bundle) shouldBe bytes
          } finally channel.close()
        }
      }
    }
  }

  private final case class BundleBytes(manifest: Vector[Byte], receipt: Vector[Byte], output: Vector[Byte])

  private def _accepted_project(root: Path, slug: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    val project = parent.resolve(s"$slug.dox")
    val descriptor = project.resolve("document-project.yaml")
    Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - article-review-html"), StandardCharsets.UTF_8)
    _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
    project
  }

  private def _accepted_project_with_selection(root: Path, slug: String, selection: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    val project = parent.resolve(s"$slug.dox")
    val descriptor = project.resolve("document-project.yaml")
    Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", selection), StandardCharsets.UTF_8)
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
  private def _bundle_bytes(bundle: Path): BundleBytes = BundleBytes(Files.readAllBytes(bundle.resolve("manifest.yaml")).toVector, Files.readAllBytes(bundle.resolve("receipt.yaml")).toVector, Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")).toVector)
  private def _relative_files(root: Path): Vector[String] = { val stream = Files.list(root); try stream.iterator().asScala.map(_.getFileName.toString).toVector.sorted finally stream.close() }
  private def _bundle_files(root: Path): Set[String] = { if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Set.empty else { val stream = Files.walk(root); try stream.iterator().asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(root.relativize(_).toString.replace('\\', '/')).toSet finally stream.close() } }
  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = { val work = Files.createDirectories(Paths.get("target/document-project-export-spec/work").toAbsolutePath.normalize()); val root = Files.createTempDirectory(work, s"$name-").toRealPath(); try body(root) finally _delete(root) }
  private def _delete(path: Path): Unit = if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { val stream = Files.walk(path); try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item => try Files.deleteIfExists(item) catch { case NonFatal(_) => () } } finally stream.close() }
}
