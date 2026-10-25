package cozy.document

import cozy.media.{CozyMedia, CozyMediaReceipt}
import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.FileTime
import java.util.UUID
import org.apache.pdfbox.pdmodel.{PDDocument, PDResources}
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.text.PDFTextStripper
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Slow
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Oct. 25, 2026
 * @version Oct. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyDocumentFigureTablePdfSpec {
  final case class PdfObservation(pageCount: Int, imageCount: Int, text: String)
}

final class CozyDocumentFigureTablePdfSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDocumentFigureTableArticleSpec.{Fixture, assemble, replaceImage, texts}
  import CozyDocumentFigureTablePdfSpec.PdfObservation

  "Native paired SmartDox article PDF acceptance" should {
    "build real JA and EN figure-table PDFs, reuse current inputs and rebuild the changed EN raster through CozyMedia" taggedAs Slow in {
      Given("the actual exported development runtime and paired authored articles with explicit same-locale figure receipt inputs")
      val export = Paths.get("target/cozy.d/runtime-classpath.txt").toAbsolutePath.normalize()
      withClue("Parent must run cozyExportRuntimeClasspath before this explicit HEAVY acceptance: ") {
        Files.isRegularFile(export, LinkOption.NOFOLLOW_LINKS) shouldBe true
      }
      val classpath = Files.readString(export, StandardCharsets.UTF_8).trim
      classpath should not be empty
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      Files.isExecutable(java) shouldBe true
      val command = Vector(java.toString, "-cp", classpath, "org.smartdox.service.SmartDoxService", "pdf")
      val parent = Files.createDirectories(Paths.get("target/phase87/pdf-acceptance").toAbsolutePath.normalize())
      val work = Files.createDirectory(parent.resolve(UUID.randomUUID.toString)).toRealPath()
      val fixtures = Vector("ja", "en").map(locale => assemble(work, locale))
      fixtures.foreach(fixture => _configure_renderer(fixture, command))
      var observations = fixtures.map(fixture => _entry(fixture, "prepared", Vector.empty, None))
      var reports = Map.empty[String, Vector[String]]
      _write_index(work, observations)

      try {
        When("normal CozyMedia.build invokes the actual native SmartDox service for both selected article-pdf resources")
        fixtures.zipWithIndex.foreach { case (fixture, index) =>
          val config = CozyMedia.CommandConfig(fixture.media, target = Some(s"article-pdf-${fixture.locale}"))
          val plan = CozyMedia.resolvePlan(config)
          val observed = CozyMediaReceipt.observeInputs(plan).map(_._1)
          val report = CozyMedia.build(config)
          reports += fixture.locale -> Vector(report)
          observations = observations.updated(index, _entry(fixture, "built", Vector(report), None))
          _write_index(work, observations)
          val pdf = _inspect_pdf(fixture.pdf)

          Then("each actual PDF contains readable complete localized explanations and native image resources with only the used dependencies")
          report should include(s"article-pdf-${fixture.locale}: built")
          observed.toSet shouldBe Set(fixture.media, fixture.index, fixture.image)
          plan.descriptor.receipt.toVector.flatMap(_.inputs).map(value => (value.id, value.role, value.path, value.normalization)) shouldBe
            Vector(("figure-tier-comparison", "figure", s"images/tier-balance-${fixture.locale}.png", "bytes"))
          observed should not contain fixture.document
          observed.exists(_.toString.endsWith(".svg")) shouldBe false
          _complete_pdf(fixture, pdf)
          observations = observations.updated(index, _entry(fixture, "verified", Vector(report), Some(pdf)))
          _write_index(work, observations)
        }

        Given("both real PDFs are current with ordinary controlled timestamps and retained renderer reports")
        fixtures.foreach { fixture =>
          val config = CozyMedia.CommandConfig(fixture.media, target = Some(s"article-pdf-${fixture.locale}"))
          CozyMediaReceipt.observeInputs(CozyMedia.resolvePlan(config)).foreach { case (path, _) => _time(path, 10000L) }
          _time(fixture.pdf, 20000L)
        }

        When("unchanged native plans and then newer Document, other-locale raster and SVG-master inputs are observed")
        val reuse = fixtures.map { fixture =>
          val config = CozyMedia.CommandConfig(fixture.media, target = Some(s"article-pdf-${fixture.locale}"))
          val unchanged = CozyMedia.plan(config)
          val currentreport = CozyMedia.build(config)
          Files.writeString(fixture.document, Files.readString(fixture.document, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
          _time(fixture.document, 30000L)
          val otherlocale = if (fixture.locale == "ja") "en" else "ja"
          val otherimage = fixture.root.resolve(s"images/tier-balance-$otherlocale.png")
          replaceImage(otherimage)
          _time(otherimage, 30000L)
          _time(fixture.root.resolve(s"images/tier-balance-${fixture.locale}.svg"), 30000L)
          val unrelated = CozyMedia.plan(config)
          (fixture, unchanged, currentreport, unrelated)
        }

        Then("the native media route reuses both outputs and excludes every unrelated authority from observed input paths")
        reuse.zipWithIndex.foreach { case ((fixture, unchanged, report, unrelated), index) =>
          unchanged should include(s"article-pdf-${fixture.locale}: current")
          report should include(s"article-pdf-${fixture.locale}: current")
          unrelated should include(s"article-pdf-${fixture.locale}: current")
          val config = CozyMedia.CommandConfig(fixture.media, target = Some(s"article-pdf-${fixture.locale}"))
          CozyMediaReceipt.observeInputs(CozyMedia.resolvePlan(config)).map(_._1).toSet shouldBe Set(fixture.media, fixture.index, fixture.image)
          reports += fixture.locale -> (reports(fixture.locale) ++ Vector(unchanged, report, unrelated))
          observations = observations.updated(index, _entry(fixture, "current-after-unrelated-edits",
            reports(fixture.locale), Some(_inspect_pdf(fixture.pdf))))
        }
        _write_index(work, observations)

        Given("the current English PDF and its selected complete PNG raster")
        val english = fixtures.find(_.locale == "en").get
        val englishconfig = CozyMedia.CommandConfig(english.media, target = Some("article-pdf-en"))

        When("one valid English raster pixel changes with a newer timestamp and the normal native producer actually rebuilds once")
        replaceImage(english.image)
        _time(english.image, 30000L)
        val stale = CozyMedia.plan(englishconfig)
        val rebuilt = CozyMedia.build(englishconfig)
        reports += english.locale -> (reports(english.locale) ++ Vector(stale, rebuilt))
        observations = observations.updated(1, _entry(english, "rebuilt", reports(english.locale), None))
        _write_index(work, observations)
        val finalpdfs = fixtures.map(fixture => fixture -> _inspect_pdf(fixture.pdf))

        Then("the used raster makes the English PDF stale, actual production succeeds and final JA and EN PDFs remain complete for visual inspection")
        stale should include("article-pdf-en: build")
        rebuilt should include("article-pdf-en: built")
        finalpdfs.zipWithIndex.foreach { case ((fixture, pdf), index) =>
          _complete_pdf(fixture, pdf)
          observations = observations.updated(index, _entry(fixture, "final-verified-awaiting-parent-visual-inspection", reports(fixture.locale), Some(pdf)))
        }
        _write_index(work, observations)
      } catch {
        case NonFatal(error) =>
          _write_index(work, observations, Some(error.toString))
          throw error
      }
      // Retain both projects, real renderer outputs and diagnostics for parent visual inspection.
    }
  }

  private def _configure_renderer(fixture: Fixture, command: Vector[String]): Unit = {
    val descriptor = parse(Files.readString(fixture.media, StandardCharsets.UTF_8)).fold(error => throw error, identity)
    val resources = descriptor.hcursor.downField("resources").as[Vector[Json]].fold(error => throw error, identity)
    val configured = resources.map { resource =>
      if (resource.hcursor.downField("build").as[String].toOption.contains("article-pdf"))
        resource.hcursor.downField("articlePdf").downField("renderer").withFocus(_.mapObject(_.add("command", Json.fromValues(command.map(Json.fromString)))))
          .top.get
      else resource
    }
    val value = descriptor.mapObject(_.add("resources", Json.fromValues(configured)))
    Files.writeString(fixture.media, value.spaces2 + "\n", StandardCharsets.UTF_8)
    ()
  }

  private def _inspect_pdf(path: Path): PdfObservation = {
    val document = PDDocument.load(path.toFile)
    try PdfObservation(document.getNumberOfPages,
      document.getPages.iterator.asScala.map(page => _image_count(page.getResources)).sum,
      new PDFTextStripper().getText(document))
    finally document.close()
  }

  private def _image_count(resources: PDResources): Int =
    if (resources == null) 0
    else resources.getXObjectNames.asScala.toVector.map { name =>
      resources.getXObject(name) match {
        case _: PDImageXObject => 1
        case form: PDFormXObject => _image_count(form.getResources)
        case _ => 0
      }
    }.sum

  private def _complete_pdf(fixture: Fixture, pdf: PdfObservation): Unit = {
    withClue(s"Retained actual native PDF: ${fixture.pdf}: ") {
      Files.isRegularFile(fixture.pdf, LinkOption.NOFOLLOW_LINKS) shouldBe true
      pdf.pageCount should be > 0
      pdf.imageCount should be > 0
      val normalized = _layout_text(pdf.text)
      texts(fixture.admitted).foreach(value => normalized should include(_layout_text(value)))
    }
  }

  private def _layout_text(value: String): String =
    // Remove only word hyphenation across physical line breaks, preserving same-line hyphens.
    value.replaceAll("(?<=\\p{L})-\\h*\\R\\h*(?=\\p{L})", "").replaceAll("\\s+", "")

  private def _entry(fixture: Fixture, status: String, reports: Vector[String], pdf: Option[PdfObservation]): Json =
    Json.obj(
      "locale" -> Json.fromString(fixture.locale),
      "project" -> Json.fromString(fixture.root.toString),
      "article" -> Json.fromString(fixture.index.toString),
      "pdf" -> Json.fromString(fixture.pdf.toString),
      "status" -> Json.fromString(status),
      "pageCount" -> pdf.map(value => Json.fromInt(value.pageCount)).getOrElse(Json.Null),
      "imageCount" -> pdf.map(value => Json.fromInt(value.imageCount)).getOrElse(Json.Null),
      "reports" -> Json.fromValues(reports.map(Json.fromString))
    )

  private def _write_index(work: Path, observations: Vector[Json], failure: Option[String] = None): Unit = {
    val path = Paths.get("target/phase87/pdf-acceptance-latest.json").toAbsolutePath.normalize()
    val value = Json.obj(
      "workspace" -> Json.fromString(work.toString),
      "articles" -> Json.fromValues(observations),
      "failure" -> failure.map(Json.fromString).getOrElse(Json.Null)
    )
    Files.writeString(path, value.spaces2 + "\n", StandardCharsets.UTF_8)
    ()
  }

  private def _time(path: Path, millis: Long): Unit = { Files.setLastModifiedTime(path, FileTime.fromMillis(millis)); () }
}
