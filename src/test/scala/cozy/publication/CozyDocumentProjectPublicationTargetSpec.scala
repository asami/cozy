package cozy.publication

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import cozy.document.{CozyDocumentProject, CozyDocumentProjectExport}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 1, 2026
 * @version Oct. 1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectPublicationTargetSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project publication target admission" should {
    "admit a genuine SimpleModeling.org export with typed evidence and exact HTML" in {
      _with_temp_dir("cozy-document-project-publication-target-genuine") { root =>
        Given("a standard English project with Article review selected, rendered, and exported for SimpleModeling.org")
        val project = _accepted_project(root, "publication-genuine")
        val bundle = _export(project, root.resolve("portable-bundle"))
        val before = _bundle_bytes(bundle)
        val sourcebefore = _tree_bytes(project)

        When("the portable bundle is admitted through the target boundary")
        val admitted = CozyDocumentProjectPublicationTarget.admit(bundle.resolve(".").resolve("..").resolve(bundle.getFileName))

        Then("the target, normalized root, generic evidence, and Article review bytes are typed and exact")
        admitted.bundleRoot shouldBe bundle.toAbsolutePath.normalize()
        admitted.root shouldBe admitted.bundleRoot
        admitted.target shouldBe CozyDocumentProjectPublicationTarget.Target.SimpleModelingOrg
        admitted.target.slug shouldBe "simplemodeling-org"
        admitted.evidence shouldBe CozyDocumentProjectExport.verifyBundle(bundle)
        admitted.articleReview shouldBe bundle.toAbsolutePath.normalize().resolve("work-products/article-review-html/article-review.html")
        Files.readAllBytes(admitted.articleReview) shouldBe Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html"))
        _bundle_bytes(bundle) shouldBe before
        _tree_bytes(project) shouldBe sourcebefore
      }
    }

    "reject a preview-target export and preserve its portable evidence" in {
      _with_temp_dir("cozy-document-project-publication-target-wrong-target") { root =>
        Given("a genuine export whose generic target is preview")
        val project = _accepted_project(root, "publication-preview")
        val bundle = _export(project, root.resolve("preview-bundle"), "preview")
        val before = _bundle_bytes(bundle)
        val sourcebefore = _tree_bytes(project)

        When("the preview-target bundle is offered to the SimpleModeling.org boundary")
        val failure = intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.admit(bundle)
        }

        Then("the wrong target is rejected after generic verification without mutation")
        failure.getMessage should include("DP-OP-001")
        _bundle_bytes(bundle) shouldBe before
        _tree_bytes(project) shouldBe sourcebefore
      }
    }

    "reject a null bundle path with the deterministic operation diagnostic" in {
      Given("an absent export bundle path")

      When("the absent path is offered to the target boundary")
      val nullfailure = intercept[RuntimeException] {
        CozyDocumentProjectPublicationTarget.admit(null)
      }

      Then("null admission fails closed with DP-OP-001")
      nullfailure.getMessage should include("DP-OP-001")
    }

    "reject a partial bundle without changing its remaining bytes" in {
      _with_temp_dir("cozy-document-project-publication-target-damaged") { root =>
        Given("a genuine export whose receipt has been removed")
        val project = _accepted_project(root, "publication-partial")
        val partial = _export(project, root.resolve("partial-bundle"))
        val partialmanifest = Files.readAllBytes(partial.resolve("manifest.yaml")).toVector
        val partialoutput = Files.readAllBytes(partial.resolve("work-products/article-review-html/article-review.html")).toVector
        Files.delete(partial.resolve("receipt.yaml"))

        When("the partial bundle is admitted")
        val partialfailure = intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.admit(partial)
        }

        Then("the missing receipt is rejected while remaining bytes stay unchanged")
        partialfailure.getMessage should include("DP-OP-001")
        Files.readAllBytes(partial.resolve("manifest.yaml")).toVector shouldBe partialmanifest
        Files.readAllBytes(partial.resolve("work-products/article-review-html/article-review.html")).toVector shouldBe partialoutput
      }
    }

    "reject tampered output without changing the other bundle bytes" in {
      _with_temp_dir("cozy-document-project-publication-target-tampered") { root =>
        Given("a genuine export whose Article review bytes have been changed")
        val project = _accepted_project(root, "publication-tampered")
        val tampered = _export(project, root.resolve("tampered-bundle"))
        val manifestbefore = Files.readAllBytes(tampered.resolve("manifest.yaml")).toVector
        val receiptbefore = Files.readAllBytes(tampered.resolve("receipt.yaml")).toVector
        val output = tampered.resolve("work-products/article-review-html/article-review.html")
        Files.writeString(output, "tampered output\n", StandardCharsets.UTF_8)

        When("the tampered bundle is admitted")
        val tamperedfailure = intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.admit(tampered)
        }

        Then("the generic verifier rejects the damaged output without changing other bundle bytes")
        tamperedfailure.getMessage should include("DP-OP-001")
        Files.readAllBytes(tampered.resolve("manifest.yaml")).toVector shouldBe manifestbefore
        Files.readAllBytes(tampered.resolve("receipt.yaml")).toVector shouldBe receiptbefore
      }
    }

    "admit a relocated bundle without its source project" in {
      _with_temp_dir("cozy-document-project-publication-target-relocation") { root =>
        Given("a genuine portable export and a destination outside its source project")
        val project = _accepted_project(root, "publication-relocation")
        val bundle = _export(project, root.resolve("original-bundle"))
        val relocated = root.resolve("relocated-bundle")
        Files.move(bundle, relocated)
        val before = _bundle_bytes(relocated)
        _delete(project)

        When("the relocated bundle is admitted after the source project is gone")
        val admitted = CozyDocumentProjectPublicationTarget.admit(relocated)

        Then("portable admission succeeds and preserves the relocated bundle")
        admitted.bundleRoot shouldBe relocated.toAbsolutePath.normalize()
        admitted.target shouldBe CozyDocumentProjectPublicationTarget.Target.SimpleModelingOrg
        admitted.evidence.target shouldBe "simplemodeling-org"
        Files.exists(admitted.articleReview, LinkOption.NOFOLLOW_LINKS) shouldBe true
        _bundle_bytes(relocated) shouldBe before
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

  private def _export(project: Path, bundle: Path, target: String = "simplemodeling-org"): Path = {
    _execute(List("document-project", "export", project.toString, "--target", target, "--save", bundle.toString))
    bundle
  }

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentProject.execute(args) shouldBe true
    }
    bytes.toString("UTF-8").trim
  }

  private def _bundle_bytes(bundle: Path): BundleBytes = BundleBytes(
    Files.readAllBytes(bundle.resolve("manifest.yaml")).toVector,
    Files.readAllBytes(bundle.resolve("receipt.yaml")).toVector,
    Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")).toVector
  )

  private def _tree_bytes(root: Path): Map[String, Vector[Byte]] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(path => root.relativize(path).toString.replace('\\', '/') -> Files.readAllBytes(path).toVector).toMap
    finally stream.close()
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val work = Files.createDirectories(Paths.get("target/document-project-publication-target-spec/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(work, s"$name-").toRealPath()
    try body(root) finally _delete(root)
  }

  private def _delete(path: Path): Unit = if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
    val stream = Files.walk(path)
    try stream.iterator.asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
      try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
    }
    finally stream.close()
  }
}
