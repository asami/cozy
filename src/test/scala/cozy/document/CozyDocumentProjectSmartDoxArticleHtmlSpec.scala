package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.util.Locale
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectSmartDoxArticleHtmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project SmartDox article HTML" should {
    "render the admitted index.dox as a complete SmartDox article document" in {
      _with_temp_dir("cozy-document-project-smartdox-article-html") { root =>
        Given("one admitted .dox project with article constructs and unrelated confirmation inputs")
        val project = _project(root)
        val unrelated = _unrelated_inputs(project)

        When("the package-private article HTML adapter renders the project")
        val rendered = CozyDocumentProjectSmartDoxArticleHtml.render(project)
        val html = rendered.html.toLowerCase(Locale.ROOT)

        Then("the actual SmartDox result is a complete document with the authored article structures")
        html should include("<html")
        html should include("<head")
        html should include("<body")
        html should include("<article")
        html should include("<h1")
        html should include("<p")
        html should include("<ul")
        html should include("<li")
        html should include("<a href=\"https://example.com/article\"")
        html should include("<figure")
        html should include("<img")
        html should include("<figcaption")
        rendered.html should include("Article heading")
        rendered.html should include("The authored article paragraph remains intact.")
        rendered.html should include("First authored item")
        rendered.html should include("Read the source link")
        rendered.html should include("Figure caption")
        rendered.html should not include("review-table")
        rendered.html should not include("Cozy Document Confirmation")
        unrelated.foreach { case (path, value) =>
          Files.readString(path, StandardCharsets.UTF_8) shouldBe value
        }
        Vector("target", "receipt.yaml", "cache", "state.yaml").foreach { path =>
          Files.exists(project.resolve(path), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "disclose the direct renderer binding without installing outputs or assets" in {
      _with_temp_dir("cozy-document-project-smartdox-article-binding") { root =>
        Given("an admitted .dox project whose only rendering input is index.dox")
        val project = _project(root)

        When("the article adapter renders the direct source")
        val rendered = CozyDocumentProjectSmartDoxArticleHtml.render(project)

        Then("the typed result identifies SmartDox, the direct source, and no copied assets")
        rendered.rendererIdentity shouldBe "smartdox-2.4.19-SNAPSHOT"
        rendered.sourcePath shouldBe project.resolve("index.dox").toAbsolutePath.normalize()
        rendered.assets shouldBe Vector.empty
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("cache"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject missing, escaping, symbolic, and nonregular source paths before rendering" in {
      _with_temp_dir("cozy-document-project-smartdox-article-rejection") { root =>
        Given("direct project directories with missing, directory, symbolic, and escaping index.dox paths")
        val missingproject = _empty_project(root, "missing.dox")
        val directoryproject = _empty_project(root, "directory.dox")
        Files.createDirectories(directoryproject.resolve("index.dox"))
        val external = root.resolve("external-index.dox")
        Files.write(external, Array[Byte](0xc3.toByte, 0x28.toByte))
        val symbolicproject = _empty_project(root, "symbolic.dox")
        Files.createSymbolicLink(symbolicproject.resolve("index.dox"), external)
        val escapedroot = Files.createDirectories(root.resolve("external-project"))
        _write(escapedroot.resolve("index.dox"), _article_source)
        val escapedproject = root.resolve("escaped.dox")
        Files.createSymbolicLink(escapedproject, escapedroot)

        When("each unsafe project source is submitted to the adapter")
        val failures = Vector(
          _failure(CozyDocumentProjectSmartDoxArticleHtml.render(missingproject)),
          _failure(CozyDocumentProjectSmartDoxArticleHtml.render(directoryproject)),
          _failure(CozyDocumentProjectSmartDoxArticleHtml.render(symbolicproject)),
          _failure(CozyDocumentProjectSmartDoxArticleHtml.render(escapedproject))
        )

        Then("all path faults occur before SmartDox parsing and leave no target, receipt, cache, or state output")
        failures.foreach(_.getMessage should include("DP-PATH-001"))
        Vector(missingproject, directoryproject, symbolicproject, escapedproject).foreach { project =>
          Vector("target", "receipt.yaml", "cache", "state.yaml").foreach { path =>
            Files.exists(project.resolve(path), LinkOption.NOFOLLOW_LINKS) shouldBe false
          }
        }
      }
    }
  }

  private def _project(root: Path): Path = {
    val project = Files.createDirectories(root.resolve("article.dox"))
    _write(project.resolve("index.dox"), _article_source)
    project
  }

  private def _empty_project(root: Path, name: String): Path =
    Files.createDirectories(root.resolve(name))

  private def _unrelated_inputs(project: Path): Vector[(Path, String)] = {
    val values = Vector(
      "content/core.yaml" -> "Core input must not be consumed.",
      "content/en/document.yaml" -> "Document input must not be consumed.",
      "presentation/visual-pages.yaml" -> "Visual Page input must not be consumed.",
      "infographic/infographic.svg" -> "Infographic input must not be consumed.",
      "review/article.receipt.yaml" -> "Receipt input must not be consumed."
    )
    values.map { case (relative, value) =>
      _write(project.resolve(relative), value)
      project.resolve(relative) -> value
    }
  }

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _failure(body: => Any): RuntimeException =
    intercept[RuntimeException](body)

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name)
    try body(root)
    finally {
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }

  private val _article_source =
    """# Article heading
      |
      |The authored article paragraph remains intact.
      |
      |- First authored item
      |- Second authored item
      |
      |- [[https://example.com/article][Read the source link]]
      |
      |#+CAPTION: Figure caption
      |[[figure.png]]
      |""".stripMargin
}
