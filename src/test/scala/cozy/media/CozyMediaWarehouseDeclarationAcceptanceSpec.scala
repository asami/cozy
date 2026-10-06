package cozy.media

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaWarehouseDeclarationAcceptanceSpec
    extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyMediaWarehouseDeclarationAcceptanceSpec._

  "Native actual warehouse declaration interpretation" should {
    scenarios.foreach { scenario =>
      scenario.articleIdentity which {
        "interpret its actual mapped artifacts without placing them" in {
          Given("explicit integration roots, frozen semantic rows and actual selected source/destination availability")
          if (!sys.props.get("cozy.media.warehouse.integration").contains("true"))
            cancel("External declaration acceptance requires -Dcozy.media.warehouse.integration=true; cancellation is not acceptance.")
          val declarationroot = _required_root(
            sys.props.get("cozy.media.warehouse.declarationRoot"),
            "cozy.media.warehouse.declarationRoot"
          )
          val warehouseroot = _required_root(
            sys.env.get("SIMPLEMODELING_ARTIFACT_ROOT"),
            "SIMPLEMODELING_ARTIFACT_ROOT"
          )
          val descriptor = declarationroot.resolve(scenario.descriptor).normalize()
          if (!Files.isRegularFile(descriptor, LinkOption.NOFOLLOW_LINKS))
            fail(s"Enabled declaration fixture requires an existing descriptor: $descriptor")
          // The native typed descriptor decoder orders resources by ID.
          val expectedrows = scenario.resources.sortBy(_.resourceId).map { resource =>
            val source = descriptor.getParent.resolve(resource.sourceDeclaration).toAbsolutePath.normalize()
            val destination = warehouseroot.resolve(resource.coordinate).normalize()
            val status = if (Files.notExists(source, LinkOption.NOFOLLOW_LINKS)) "Unavailable" else "Planned"
            val row = s"${resource.resourceId}: $status article=${scenario.articleIdentity} locale=${resource.locale} role=${resource.role} source=$source destination=$destination public=${resource.publicPath}"
            (row, destination, status)
          }
          val absentdestinations = expectedrows.map(_._2).filter(path => Files.notExists(path, LinkOption.NOFOLLOW_LINKS))
          val outcome = if (expectedrows.exists(_._3 == "Unavailable")) "partial" else "complete"
          val expectedheading = s"Media deploy dry-run: $outcome (remote publication not performed)"
          val captured = new ByteArrayOutputStream()
          val output = new PrintStream(captured, true, StandardCharsets.UTF_8.name())

          When("native Cozy.main interprets the actual descriptor with literal --dry-run and selected destinations are observed")
          try {
            Console.withOut(output) {
              cozy.Cozy.main(Array("media", "deploy", descriptor.toString, "--profile", "artifact-archive", "--dry-run"))
            }
          } finally {
            output.close()
          }
          val createddestinations = absentdestinations.filter(path => !Files.notExists(path, LinkOption.NOFOLLOW_LINKS))
          val report = captured.toString(StandardCharsets.UTF_8.name())
          val lines = report.split("\\r?\\n").toVector
          val headings = lines.filter(_.startsWith("Media deploy "))
          val rows = lines.filter(_.matches("^\\S+: \\S+ article=.*"))

          Then("the complete ordered native interpretation reports every mapped field and creates no selected destination")
          headings shouldBe Vector(expectedheading)
          rows shouldBe expectedrows.map(_._1)
          rows should have size scenario.resources.size
          createddestinations shouldBe Vector.empty[Path]
          info(s"article=${scenario.articleIdentity} descriptor=$descriptor rows=${rows.size}")
          info(report)
        }
      }
    }
  }

  private def _required_root(value: Option[String], label: String): Path = {
    val declared = value.getOrElse(fail(s"Enabled declaration acceptance requires explicit $label"))
    if (declared.trim.isEmpty || declared != declared.trim)
      fail(s"Enabled declaration acceptance requires an exact nonblank $label")
    val path = Paths.get(declared)
    if (!path.isAbsolute || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      fail(s"Enabled declaration acceptance requires an existing absolute direct directory for $label: $path")
    path.normalize()
  }
}

object CozyMediaWarehouseDeclarationAcceptanceSpec {
  final case class ExpectedResource(
    resourceId: String,
    role: String,
    locale: String,
    sourceDeclaration: String,
    coordinate: String,
    publicPath: String
  )
  final case class DeclarationScenario(
    descriptor: String,
    articleIdentity: String,
    resources: Vector[ExpectedResource]
  )

  // Literal semantic expectations are authored from the frozen parent mapping.
  // The running specification never reads workflow records or copied descriptors.
  val scenarios: Vector[DeclarationScenario] = Vector(
    DeclarationScenario(
      "src/main/doxsite/development-process/domain-modeling.dox/media.yaml",
      "development-process/domain-modeling",
      Vector(
        ExpectedResource("web-ja", "infographic", "ja", "infographic/web-ja.png", "media/ja/development-process/domain-modeling/infographic.png", "/media/ja/development-process/domain-modeling/infographic.png"),
        ExpectedResource("summary-slides-ja", "summary_slides_pdf", "ja", "presentation/summary-ja.pdf", "media/ja/development-process/domain-modeling/slides.pdf", "/media/ja/development-process/domain-modeling/slides.pdf"),
        ExpectedResource("article-pdf-ja", "article_pdf", "ja", "article/article-ja.pdf", "media/ja/development-process/domain-modeling/article.pdf", "/media/ja/development-process/domain-modeling/article.pdf"),
        ExpectedResource("article-video-ja", "video", "ja", "../../../../../target/media/development-process/domain-modeling/ja/final.mp4", "media/ja/development-process/domain-modeling/video.mp4", "/media/ja/development-process/domain-modeling/video.mp4")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/ai-development-harness/media.yaml",
      "development-process/ai-development-harness",
      Vector(
        ExpectedResource("infographic-ja", "infographic", "ja", "infographic/infographic.png", "media/ja/development-process/ai-development-harness/infographic.png", "/media/ja/development-process/ai-development-harness/infographic.png"),
        ExpectedResource("infographic-en", "infographic", "en", "infographic/infographic-en.png", "media/en/development-process/ai-development-harness/infographic.png", "/media/en/development-process/ai-development-harness/infographic.png"),
        ExpectedResource("article-pdf-ja", "article_pdf", "ja", "article/article-ja.pdf", "media/ja/development-process/ai-development-harness/article.pdf", "/media/ja/development-process/ai-development-harness/article.pdf"),
        ExpectedResource("article-pdf-en", "article_pdf", "en", "article/article-en.pdf", "media/en/development-process/ai-development-harness/article.pdf", "/media/en/development-process/ai-development-harness/article.pdf"),
        ExpectedResource("summary-slides-ja", "summary_slides_pdf", "ja", "presentation/summary-ja.pdf", "media/ja/development-process/ai-development-harness/slides.pdf", "/media/ja/development-process/ai-development-harness/slides.pdf"),
        ExpectedResource("summary-slides-en", "summary_slides_pdf", "en", "presentation/summary-en.pdf", "media/en/development-process/ai-development-harness/slides.pdf", "/media/en/development-process/ai-development-harness/slides.pdf"),
        ExpectedResource("article-video-ja", "video", "ja", "video/ja/target/video-build/ai-development-harness-ja.mp4", "media/ja/development-process/ai-development-harness/video.mp4", "/media/ja/development-process/ai-development-harness/video.mp4"),
        ExpectedResource("article-video-en", "video", "en", "video/en/target/video-build/ai-development-harness-en.mp4", "media/en/development-process/ai-development-harness/video.mp4", "/media/en/development-process/ai-development-harness/video.mp4")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/knowledge-modeling/media.yaml",
      "development-process/knowledge-modeling-for-ai-collaboration",
      Vector(
        ExpectedResource("web-ja", "infographic", "ja", "infographic/web-ja.png", "media/ja/development-process/knowledge-modeling-for-ai-collaboration/infographic.png", "/media/ja/development-process/knowledge-modeling-for-ai-collaboration/infographic.png"),
        ExpectedResource("article-video-ja", "video", "ja", "../../../../../target/media/development-process/knowledge-modeling/ja/final.mp4", "media/ja/development-process/knowledge-modeling-for-ai-collaboration/video.mp4", "/media/ja/development-process/knowledge-modeling-for-ai-collaboration/video.mp4"),
        ExpectedResource("article-video-en", "video", "en", "../../../../../target/media/development-process/knowledge-modeling/en/final.mp4", "media/en/development-process/knowledge-modeling-for-ai-collaboration/video.mp4", "/media/en/development-process/knowledge-modeling-for-ai-collaboration/video.mp4")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/literate-modeling/media.yaml",
      "development-process/literate-modeling",
      Vector(
        ExpectedResource("web-ja", "infographic", "ja", "infographic/web-ja.png", "media/ja/development-process/literate-modeling/infographic.png", "/media/ja/development-process/literate-modeling/infographic.png"),
        ExpectedResource("summary-slides-ja", "summary_slides_pdf", "ja", "presentation/summary-ja.pdf", "media/ja/development-process/literate-modeling/slides.pdf", "/media/ja/development-process/literate-modeling/slides.pdf"),
        ExpectedResource("web-en", "infographic", "en", "infographic/web-en.png", "media/en/development-process/literate-modeling/infographic.png", "/media/en/development-process/literate-modeling/infographic.png"),
        ExpectedResource("summary-slides-en", "summary_slides_pdf", "en", "presentation/summary-en.pdf", "media/en/development-process/literate-modeling/slides.pdf", "/media/en/development-process/literate-modeling/slides.pdf"),
        ExpectedResource("article-pdf-ja", "article_pdf", "ja", "article/article-ja.pdf", "media/ja/development-process/literate-modeling/article.pdf", "/media/ja/development-process/literate-modeling/article.pdf"),
        ExpectedResource("article-pdf-en", "article_pdf", "en", "article/article-en.pdf", "media/en/development-process/literate-modeling/article.pdf", "/media/en/development-process/literate-modeling/article.pdf"),
        ExpectedResource("article-video-ja", "video", "ja", "../../../../../target/media/development-process/literate-modeling/ja/final.mp4", "media/ja/development-process/literate-modeling/video.mp4", "/media/ja/development-process/literate-modeling/video.mp4"),
        ExpectedResource("article-video-en", "video", "en", "../../../../../target/media/development-process/literate-modeling/en/final.mp4", "media/en/development-process/literate-modeling/video.mp4", "/media/en/development-process/literate-modeling/video.mp4")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/model-harness/media.yaml",
      "development-process/model-harness",
      Vector(
        ExpectedResource("article-pdf-ja", "article_pdf", "ja", "article/article-ja.pdf", "media/ja/development-process/model-harness/article.pdf", "/media/ja/development-process/model-harness/article.pdf"),
        ExpectedResource("article-pdf-en", "article_pdf", "en", "article/article-en.pdf", "media/en/development-process/model-harness/article.pdf", "/media/en/development-process/model-harness/article.pdf"),
        ExpectedResource("summary-slides-en", "summary_slides_pdf", "en", "presentation/summary-en.pdf", "media/en/development-process/model-harness/slides.pdf", "/media/en/development-process/model-harness/slides.pdf"),
        ExpectedResource("infographic-video-ja", "infographic", "ja", "video/ja/assets/infographic-ja.png", "media/ja/development-process/model-harness/infographic.png", "/media/ja/development-process/model-harness/infographic.png"),
        ExpectedResource("summary-slides-ja", "summary_slides_pdf", "ja", "presentation/summary-ja.pdf", "media/ja/development-process/model-harness/slides.pdf", "/media/ja/development-process/model-harness/slides.pdf"),
        ExpectedResource("infographic-video-ja-en", "infographic", "en", "video/en/assets/infographic-en.png", "media/en/development-process/model-harness/infographic.png", "/media/en/development-process/model-harness/infographic.png")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/modeling-technology-system/media.yaml",
      "development-process/modeling-technology-system",
      Vector(
        ExpectedResource("web-ja", "infographic", "ja", "infographic/web-ja.png", "media/ja/development-process/modeling-technology-system/infographic.png", "/media/ja/development-process/modeling-technology-system/infographic.png"),
        ExpectedResource("web-en", "infographic", "en", "infographic/web-en.png", "media/en/development-process/modeling-technology-system/infographic.png", "/media/en/development-process/modeling-technology-system/infographic.png"),
        ExpectedResource("article-video-ja", "video", "ja", "../../../../../target/media/development-process/modeling-technology-system/ja/final.mp4", "media/ja/development-process/modeling-technology-system/video.mp4", "/media/ja/development-process/modeling-technology-system/video.mp4"),
        ExpectedResource("article-video-en", "video", "en", "../../../../../target/media/development-process/modeling-technology-system/en/final.mp4", "media/en/development-process/modeling-technology-system/video.mp4", "/media/en/development-process/modeling-technology-system/video.mp4")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/object-modeling/media.yaml",
      "development-process/object-modeling-as-structural-foundation",
      Vector(
        ExpectedResource("web-ja", "infographic", "ja", "infographic/web-ja.png", "media/ja/development-process/object-modeling-as-structural-foundation/infographic.png", "/media/ja/development-process/object-modeling-as-structural-foundation/infographic.png"),
        ExpectedResource("web-en", "infographic", "en", "infographic/web-en.png", "media/en/development-process/object-modeling-as-structural-foundation/infographic.png", "/media/en/development-process/object-modeling-as-structural-foundation/infographic.png"),
        ExpectedResource("article-video-ja", "video", "ja", "../../../../../target/media/development-process/object-modeling/ja/final.mp4", "media/ja/development-process/object-modeling-as-structural-foundation/video.mp4", "/media/ja/development-process/object-modeling-as-structural-foundation/video.mp4"),
        ExpectedResource("article-video-en", "video", "en", "../../../../../target/media/development-process/object-modeling/en/final.mp4", "media/en/development-process/object-modeling-as-structural-foundation/video.mp4", "/media/en/development-process/object-modeling-as-structural-foundation/video.mp4")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/static-model-harness/media.yaml",
      "development-process/static-model-harness",
      Vector(
        ExpectedResource("infographic-ja", "infographic", "ja", "infographic/infographic-ja.png", "media/ja/development-process/static-model-harness/infographic.png", "/media/ja/development-process/static-model-harness/infographic.png"),
        ExpectedResource("article-pdf-ja", "article_pdf", "ja", "article/article-ja.pdf", "media/ja/development-process/static-model-harness/article.pdf", "/media/ja/development-process/static-model-harness/article.pdf"),
        ExpectedResource("infographic-en", "infographic", "en", "infographic/infographic-en.png", "media/en/development-process/static-model-harness/infographic.png", "/media/en/development-process/static-model-harness/infographic.png"),
        ExpectedResource("article-pdf-en", "article_pdf", "en", "article/article-en.pdf", "media/en/development-process/static-model-harness/article.pdf", "/media/en/development-process/static-model-harness/article.pdf"),
        ExpectedResource("summary-slides-ja", "summary_slides_pdf", "ja", "presentation/summary-ja.pdf", "media/ja/development-process/static-model-harness/slides.pdf", "/media/ja/development-process/static-model-harness/slides.pdf"),
        ExpectedResource("summary-slides-en", "summary_slides_pdf", "en", "presentation/summary-en.pdf", "media/en/development-process/static-model-harness/slides.pdf", "/media/en/development-process/static-model-harness/slides.pdf")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/view-based-modeling/media.yaml",
      "development-process/model-structure-and-views",
      Vector(
        ExpectedResource("web-ja", "infographic", "ja", "infographic/web-ja.png", "media/ja/development-process/model-structure-and-views/infographic.png", "/media/ja/development-process/model-structure-and-views/infographic.png"),
        ExpectedResource("web-en", "infographic", "en", "infographic/web-en.png", "media/en/development-process/model-structure-and-views/infographic.png", "/media/en/development-process/model-structure-and-views/infographic.png"),
        ExpectedResource("article-video-ja", "video", "ja", "../../../../../target/media/development-process/view-based-modeling/ja/final.mp4", "media/ja/development-process/model-structure-and-views/video.mp4", "/media/ja/development-process/model-structure-and-views/video.mp4"),
        ExpectedResource("article-video-en", "video", "en", "../../../../../target/media/development-process/view-based-modeling/en/final.mp4", "media/en/development-process/model-structure-and-views/video.mp4", "/media/en/development-process/model-structure-and-views/video.mp4")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/what-simplemodeling-has-pursued/media.yaml",
      "development-process/what-simplemodeling-has-pursued",
      Vector(
        ExpectedResource("web-ja", "infographic", "ja", "infographic/web-ja.png", "media/ja/development-process/what-simplemodeling-has-pursued/infographic.png", "/media/ja/development-process/what-simplemodeling-has-pursued/infographic.png"),
        ExpectedResource("web-en", "infographic", "en", "infographic/web-en.png", "media/en/development-process/what-simplemodeling-has-pursued/infographic.png", "/media/en/development-process/what-simplemodeling-has-pursued/infographic.png"),
        ExpectedResource("article-video-ja", "video", "ja", "../../../../../target/media/development-process/what-simplemodeling-has-pursued/ja/final.mp4", "media/ja/development-process/what-simplemodeling-has-pursued/video.mp4", "/media/ja/development-process/what-simplemodeling-has-pursued/video.mp4"),
        ExpectedResource("article-video-en", "video", "en", "../../../../../target/media/development-process/what-simplemodeling-has-pursued/en/final.mp4", "media/en/development-process/what-simplemodeling-has-pursued/video.mp4", "/media/en/development-process/what-simplemodeling-has-pursued/video.mp4")
      )
    ),
    DeclarationScenario(
      "src/main/media/development-process/why-reconstruct-software-development-methodology/media.yaml",
      "development-process/why-reconstruct-software-development-methodology",
      Vector(
        ExpectedResource("web-ja", "infographic", "ja", "infographic/web-ja.png", "media/ja/development-process/why-reconstruct-software-development-methodology/infographic.png", "/media/ja/development-process/why-reconstruct-software-development-methodology/infographic.png"),
        ExpectedResource("web-en", "infographic", "en", "infographic/web-en.png", "media/en/development-process/why-reconstruct-software-development-methodology/infographic.png", "/media/en/development-process/why-reconstruct-software-development-methodology/infographic.png"),
        ExpectedResource("article-video-ja", "video", "ja", "../../../../../target/media/development-process/why-reconstruct-software-development-methodology/ja/final.mp4", "media/ja/development-process/why-reconstruct-software-development-methodology/video.mp4", "/media/ja/development-process/why-reconstruct-software-development-methodology/video.mp4"),
        ExpectedResource("article-video-en", "video", "en", "../../../../../target/media/development-process/why-reconstruct-software-development-methodology/en/final.mp4", "media/en/development-process/why-reconstruct-software-development-methodology/video.mp4", "/media/en/development-process/why-reconstruct-software-development-methodology/video.mp4")
      )
    )
  )
}
