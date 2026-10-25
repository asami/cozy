package cozy.document

import cozy.Cozy
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.{Base64, UUID}
import org.jsoup.Jsoup
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Oct. 25, 2026
 * @version Oct. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentFigureTableAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDocumentFigureTableArticleSpec.{Fixture, assemble, blocks, texts}
  import CozyDocumentDescriptionV2.{Figure, Table}

  "Localized explanatory figures and tables across the normal Document Project route" should {
    Vector("ja", "en").foreach { locale =>
      s"retain the complete $locale explanation and Core correspondence in both Document views and the native article" in {
        Given(s"the actual shared Core, admitted $locale Document and paired authored SmartDox article")
        _with_fixture(locale) { fixture =>
          val document = fixture.admitted
          val figure = blocks(document).collectFirst { case value: Figure => value }.get
          val table = blocks(document).collectFirst { case value: Table => value }.get
          val article = Files.readString(fixture.index, StandardCharsets.UTF_8)
          val imageuri = "data:image/png;base64," + Base64.getEncoder.encodeToString(Files.readAllBytes(fixture.image))
          val targets = Vector("document-structure-html", "document-reader-html", "smartdox-article-html")

          When("the normal Cozy command entry builds the two Document views and the native SmartDox article from that project")
          val results = targets.map { target =>
            val args = List("document-project", "build", fixture.root.toString, "--target", target)
            val report = _execute(args)
            val output = s"target/document-project/local-build/$target/index.html"
            val html = Files.readString(fixture.root.resolve(output), StandardCharsets.UTF_8)
            (target, report, output, Jsoup.parse(html))
          }

          Then("all three actual HTML outputs retain the selected raster, localized captions, ordered table and complete explanation")
          figure.id shouldBe "tier-comparison"
          table.id shouldBe "protection-coverage"
          figure.coreRefs.claims shouldBe Vector("claim-tier-balance")
          table.coreRefs.claims shouldBe Vector("claim-protection-coverage")
          figure.coreRefs.steps shouldBe Vector("tier-protection")
          table.coreRefs.steps shouldBe figure.coreRefs.steps
          document.core.claimsById.keySet shouldBe Set("claim-tier-balance", "claim-protection-coverage")
          figure.asset shouldBe s"images/tier-balance-$locale.png"
          table.rows.map(_.id) shouldBe Vector("service-boundary", "data-boundary")
          texts(document).foreach(value => article should include(value))
          results.foreach { case (target, report, output, html) =>
            withClue(s"$locale / $target: ") {
              html.select("img").asScala.map(_.attr("src")).toVector shouldBe Vector(imageuri)
              html.select("figcaption").asScala.map(_.text()).toVector shouldBe Vector(figure.caption)
              val tables = html.select("table").asScala.toVector
              tables.size shouldBe 1
              val renderedtable = tables.head
              renderedtable.select("caption").text() shouldBe table.caption
              renderedtable.select("thead th").asScala.map(_.text()).toVector shouldBe table.columns.map(_.heading)
              renderedtable.select("tbody tr").asScala.map { row =>
                row.select("td").asScala.map(_.text()).toVector
              }.toVector shouldBe table.rows.map(_.cells)
              texts(document).foreach(value => html.text() should include(value))

              And("each target reports its actual ordered selected sources, output path and built outcome")
              val expectedinputs = if (target == "smartdox-article-html") fixture.inputs else Vector(
                "build/document-project-targets.yaml", "content/core.yaml", s"content/$locale/document.yaml",
                s"content/$locale/confirmation-vocabulary.yaml", figure.asset)
              report.split("\n").toVector.collect {
                case line if line.startsWith("  - ") => line.stripPrefix("  - ")
              } shouldBe expectedinputs
              report should include(s"target: $target\n")
              report should include(s"output: $output\n")
              report should include("outcome: built")

              if (target != "smartdox-article-html") {
                And("the actual Document reference targets expose precisely their admitted Core claims")
                val figureselector = if (target == "document-structure-html") "[data-block-id='tier-comparison']" else "#document-block-tier-comparison"
                val tableselector = if (target == "document-structure-html") "[data-block-id='protection-coverage']" else "#document-block-protection-coverage"
                html.select(figureselector).size() shouldBe 1
                html.select(tableselector).size() shouldBe 1
                html.select(figureselector).attr("data-core-claims") shouldBe figure.coreRefs.claims.mkString(" ")
                html.select(tableselector).attr("data-core-claims") shouldBe table.coreRefs.claims.mkString(" ")
                html.select(s"$figureselector img").attr("alt") shouldBe figure.alt
              }
              if (target == "document-reader-html") {
                And("the reader Core Step links back to both complete explanatory blocks")
                val backlinks = html.select("#core-step-tier-protection a").asScala.map(_.attr("href")).toVector
                backlinks should contain allElementsOf Vector("#document-block-tier-comparison", "#document-block-protection-coverage")
              }
              if (target == "document-structure-html") {
                html.select("button[data-step-control][data-step-id='tier-protection']").size() shouldBe 1
              }
              if (target == "smartdox-article-html") {
                html.select("a").asScala.map(_.attr("href")).toVector should contain("https://example.com/tier-protection")
              }
            }
          }
        }
      }
    }
  }

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    val stream = new PrintStream(bytes, true, "UTF-8")
    try Console.withOut(stream)(Cozy.build(args.toArray).executeDirect(args.toArray))
    finally stream.close()
    bytes.toString("UTF-8").trim
  }

  private def _with_fixture(locale: String)(body: Fixture => Unit): Unit = {
    val parent = Files.createDirectories(Paths.get("target/phase87/spec").toAbsolutePath.normalize())
    val work = Files.createDirectory(parent.resolve(UUID.randomUUID().toString)).toRealPath()
    try body(assemble(work, locale))
    finally {
      val stream = Files.walk(work)
      try stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
      finally stream.close()
    }
  }
}
