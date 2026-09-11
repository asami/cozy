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
    "selection-only public metadata" which {
      "report only the current selected Article review mapping without writing project state" in {
        _with_temp_dir("cozy-document-project-export") { root =>
          Given("a selected Article review project with strict current accepted native evidence")
          val project = _scaffolded_project(root, "export-current")
          _activate_optional_work_products(project, Vector("article-review-html"))
          val accepted = _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
          val before = _tree_identities(project)
          val nativehash = _sha256(project.resolve("target/document-project/article-review.html"))

          When("two opaque targets request the public selection metadata")
          val first = _execute(List("document-project", "export", project.toString, "--target", "preview"))
          val second = _execute(List("document-project", "export", project.toString, "--target", "release-2026"))

          Then("each response retains the sole normalized Article review mapping and no private evidence")
          accepted should include("outcome: accepted")
          first should include("schema: cozy.document-project.v2")
          first should include("project: export-current")
          first should include("target: preview")
          first should include("role: article-review")
          first should include("mediaType: text/html")
          first should include("path: work-products/article-review-html/article-review.html")
          first should include(s"sha256: $nativehash")
          first should not include "attempt:"
          first should not include "receipt:"
          first should not include "evidence:"
          first should not include "state:"
          first should not include "cache:"
          first should not include "source:"
          first should not include "input:"
          first should not include "provider:"
          first should not include "sidecar:"
          first should not include "workflow:"
          first should not include "filesystem:"
          first should not include "target/document-project"
          first should not include project.toString
          second should include("target: release-2026")
          second should include("path: work-products/article-review-html/article-review.html")
          _tree_identities(project) shouldBe before
        }
      }

      "reject malformed target grammar, unselected output, generated review material, and stale accepted evidence" in {
        _with_temp_dir("cozy-document-project-export-rejection") { root =>
          Given("an unselected project with no native accepted attempt")
          val unselected = _scaffolded_project(root, "export-unselected")
          val unselectedbefore = _tree_identities(unselected)

          When("export is requested without selection or with a path-like target")
          val selectionfailure = _failure(List("document-project", "export", unselected.toString, "--target", "preview"))
          val targetfailure = _failure(List("document-project", "export", unselected.toString, "--target", "site/preview"))
          val savefailure = _failure(List("document-project", "export", unselected.toString, "--target", "preview", "--save", "result.yaml"))

          Then("the command fails closed with one CLI or operation diagnostic and creates nothing")
          _diagnostic_tokens(selectionfailure) shouldBe Vector("DP-OP-001")
          _diagnostic_tokens(targetfailure) shouldBe Vector("DP-CLI-001")
          _diagnostic_tokens(savefailure) shouldBe Vector("DP-CLI-001")
          _tree_identities(unselected) shouldBe unselectedbefore

          Given("a selected project with only a generated Article review receipt")
          val generated = _scaffolded_project(root, "export-generated")
          _activate_optional_work_products(generated, Vector("article-review-html"))
          _execute(List("document-project", "review", generated.toString, "--kind", "article"))
          val generatedbefore = _tree_identities(generated)

          When("export attempts to use generated review output as production proof")
          val generatedfailure = _failure(List("document-project", "export", generated.toString, "--target", "preview"))

          Then("generated review material is not admitted as accepted native evidence")
          _diagnostic_tokens(generatedfailure) shouldBe Vector("DP-OP-001")
          _tree_identities(generated) shouldBe generatedbefore

          Given("a selected project whose strict accepted Article review evidence is initially current")
          val stale = _scaffolded_project(root, "export-stale")
          _activate_optional_work_products(stale, Vector("article-review-html"))
          _execute(List("document-project", "run", stale.toString, "--operation", "article.render-review"))
          Files.writeString(stale.resolve("index.dox"), "changed after acceptance\n", StandardCharsets.UTF_8)
          val stalebefore = _tree_identities(stale)

          When("export observes that a declared direct input identity is stale")
          val stalefailure = _failure(List("document-project", "export", stale.toString, "--target", "preview"))

          Then("the retained attempt does not qualify and remains untouched")
          _diagnostic_tokens(stalefailure) shouldBe Vector("DP-OP-001")
          _tree_identities(stale) shouldBe stalebefore
        }
      }

      "reject a standalone native receipt without a retained accepted v2 attempt" in {
        _with_temp_dir("cozy-document-project-export-standalone-receipt") { root =>
          Given("a selected project with plausible native output and a standalone native receipt")
          val project = _scaffolded_project(root, "export-standalone-receipt")
          _activate_optional_work_products(project, Vector("article-review-html"))
          val target = project.resolve("target/document-project")
          Files.createDirectories(target)
          val output = target.resolve("article-review.html")
          Files.writeString(output, "<html><body>standalone native output</body></html>\n", StandardCharsets.UTF_8)
          val outputhash = _sha256(output)
          Files.writeString(
            target.resolve("native-receipt.yaml"),
            s"identity: cozy.document-project.native-receipt.v1\nvalue: operation=article.render-review;output=target/document-project/article-review.html;mediaType=text/html;sha256=$outputhash\n",
            StandardCharsets.UTF_8
          )
          val before = _tree_identities(project)

          When("export is requested without a retained accepted v2 attempt")
          val failure = _failure(List("document-project", "export", project.toString, "--target", "preview"))

          Then("the standalone receipt is not accepted and the project remains unchanged")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          _tree_identities(project) shouldBe before
        }
      }

      "reject a changed accepted output without export mutation" in {
        _with_temp_dir("cozy-document-project-export-stale-output") { root =>
          Given("a selected project with an accepted native attempt and current output")
          val project = _scaffolded_project(root, "export-stale-output")
          _activate_optional_work_products(project, Vector("article-review-html"))
          _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
          val output = project.resolve("target/document-project/article-review.html")
          Files.writeString(output, "<html><body>changed after acceptance</body></html>\n", StandardCharsets.UTF_8)
          val before = _tree_identities(project)

          When("export observes that the declared output identity is stale")
          val failure = _failure(List("document-project", "export", project.toString, "--target", "preview"))

          Then("the changed output is rejected with DP-OP-001 and remains untouched")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          _tree_identities(project) shouldBe before
        }
      }

      "reject an unsafe manually replaced output without touching external content" in {
        _with_temp_dir("cozy-document-project-export-unsafe-output") { root =>
          Given("a selected project with accepted native evidence and an external replacement fixture")
          val project = _scaffolded_project(root, "export-unsafe-output")
          _activate_optional_work_products(project, Vector("article-review-html"))
          _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
          val external = root.resolve("external-article-review.html")
          Files.writeString(external, "external fixture content\n", StandardCharsets.UTF_8)
          val output = project.resolve("target/document-project/article-review.html")
          Files.delete(output)
          Files.createSymbolicLink(output, external)
          val before = _tree_identities(project)
          val externalbefore = Files.readAllBytes(external)

          When("export observes a manually replaced symbolic-link output")
          val failure = _failure(List("document-project", "export", project.toString, "--target", "preview"))

          Then("unsafe output is rejected with DP-OP-001 without changing project or external fixture content")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          _tree_identities(project) shouldBe before
          Files.isSymbolicLink(output) shouldBe true
          Files.readAllBytes(external) shouldBe externalbefore
        }
      }

      "fail closed when retained v2 evidence does not retain the exact native receipt" in {
        _with_temp_dir("cozy-document-project-export-receipt") { root =>
          Given("a selected Article review project with one accepted native attempt")
          val project = _scaffolded_project(root, "export-receipt")
          _activate_optional_work_products(project, Vector("article-review-html"))
          _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
          val attempts = project.resolve("evidence/attempts")
          val attempt = _relative_files(attempts).head
          val attemptpath = attempts.resolve(attempt)
          Files.writeString(
            attemptpath,
            Files.readString(attemptpath, StandardCharsets.UTF_8).replace("cozy.document-project.native-receipt.v1", "forged-receipt"),
            StandardCharsets.UTF_8
          )
          val before = _tree_identities(project)

          When("export reads the retained accepted attempt")
          val failure = _failure(List("document-project", "export", project.toString, "--target", "preview"))

          Then("the retained parser rejects the forged receipt without creating export state")
          _diagnostic_tokens(failure).size shouldBe 1
          failure should include("retained accepted v2 attempt receipt")
          _tree_identities(project) shouldBe before
        }
      }
    }
  }

  private def _scaffolded_project(root: Path, slug: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    parent.resolve(s"$slug.dox")
  }

  private def _activate_optional_work_products(project: Path, selected: Vector[String]): Unit = {
    val descriptor = project.resolve("document-project.yaml")
    val selection = "activeOptionalWorkProducts:\n" + selected.map(id => s"  - $id").mkString("\n")
    Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", selection), StandardCharsets.UTF_8)
  }

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentProject.execute(args) shouldBe true
    }
    bytes.toString("UTF-8").trim
  }

  private def _failure(args: List[String]): String =
    intercept[RuntimeException] {
      CozyDocumentProject.execute(args)
    }.getMessage

  private def _diagnostic_tokens(value: String): Vector[String] =
    """DP-[A-Z]+-\d{3}""".r.findAllIn(value).toVector

  private def _tree_identities(root: Path): Map[String, String] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map { path =>
      root.relativize(path).toString.replace('\\', '/') -> _sha256(path)
    }.toMap
    finally stream.close()
  }

  private def _relative_files(root: Path): Vector[String] = {
    val stream = Files.list(root)
    try stream.iterator().asScala.map(_.getFileName.toString).toVector.sorted
    finally stream.close()
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
      } finally stream.close()
    }
}
