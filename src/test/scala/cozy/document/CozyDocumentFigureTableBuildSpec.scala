package cozy.document

import cozy.Cozy
import io.circe.Json
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.FileTime
import java.util.{Base64, UUID}
import javax.imageio.ImageIO
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.yaml.snakeyaml.Yaml
import scala.collection.JavaConverters._

/*
 * @since   Oct. 25, 2026
 * @version Oct. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyDocumentFigureTableBuildSpec {
  final case class Fixture(root: Path, locale: String) {
    def core: Path = root.resolve("content/core.yaml")
    def document: Path = root.resolve(s"content/$locale/document.yaml")
    def vocabulary: Path = root.resolve(s"content/$locale/confirmation-vocabulary.yaml")
    def image: Path = root.resolve(s"images/tier-balance-$locale.png")
    def config: Path = root.resolve("build/document-project-targets.yaml")
    def index: Path = root.resolve("index.dox")
    def authoredInputs: Vector[String] = Vector("build/document-project-targets.yaml", "content/core.yaml", s"content/$locale/document.yaml", s"content/$locale/confirmation-vocabulary.yaml")
    def usedInputs: Vector[String] = authoredInputs :+ s"images/tier-balance-$locale.png"
    def output(targetId: String): Path = root.resolve(s"target/document-project/local-build/$targetId/index.html")
  }
}

final class CozyDocumentFigureTableBuildSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDocumentFigureTableBuildSpec.Fixture
  import CozyDocumentDescriptionV2.{Figure, Table}

  private val _document_targets = Vector("document-structure-html", "document-reader-html")

  "Native figure and table confirmation" should {
    "render the complete paired explanations through the normal CLI entry with an explicit root" in {
      Vector("ja", "en").foreach { locale =>
        _with_fixture(locale) { fixture =>
          Given("the actual localized comparison figure, protection table and an explicit direct project root")
          val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document)
          val uri = _image_uri(fixture.image)
          val output = fixture.root.resolve("confirmation.html")

          When("the normal Cozy command entry renders a Document with --project-root")
          val report = _execute(_confirmation(fixture, output) ++ List("--project-root", fixture.root.toString))
          val html = Files.readString(output, StandardCharsets.UTF_8)

          Then("the output contains the complete image, table, exact typed selection and native button activation")
          report should include("kind: document")
          _complete_html(html, document, uri, "document-structure-html")
        }
      }
    }

    "reject a missing figure root before creating an output" in {
      _with_fixture("en") { fixture =>
        Given("an admitted Document with a figure and no explicit materialization root")
        val output = fixture.root.resolve("missing-root.html")

        When("the normal Document render command omits --project-root")
        val fault = intercept[CozyDocumentDescriptionV2.DescriptionV2Fault](_execute(_confirmation(fixture, output)))
        val exists = Files.exists(output, LinkOption.NOFOLLOW_LINKS)

        Then("the actionable CLI diagnostic precedes output writing")
        fault.code shouldBe "CONFIRMATION_CLI"
        fault.path shouldBe "$.command"
        fault.reason should include("--project-root is required for figure assets")
        exists shouldBe false
      }
    }

    "retain root-free table-only and asset-free render behavior" in {
      Vector(true, false).foreach { tableonly =>
        _with_fixture("en") { fixture =>
          Given("an actual Document with its figure removed and optionally its table retained")
          _without_figure(fixture, tableonly)
          val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document)
          val vocabulary = CozyDocumentConfirmationVocabulary.loadDocument(fixture.vocabulary, "en")
          val expected = CozyDocumentConfirmationProjectionV2.render(document, vocabulary).html
          val output = fixture.root.resolve("root-free.html")

          When("the existing command form renders without --project-root")
          _execute(_confirmation(fixture, output))
          val html = Files.readString(output, StandardCharsets.UTF_8)

          Then("the old empty-asset projection remains identical and a retained table remains complete")
          html shouldBe expected
          html should not include "data:image/"
          if (tableonly) {
            html should include("<table>")
            html should include("<td>Storage and transfer</td>")
          } else html should not include "<table>"
        }
      }
    }

    "preserve root and asset diagnostics and reject a Summary root before output" in {
      _with_fixture("en") { fixture =>
        Given("an invalid direct root, a traversal reference, an SVG master reference and a Summary request with an unused root")
        val original = Files.readString(fixture.document, StandardCharsets.UTF_8)
        val outputs = Vector("root", "traversal", "svg", "summary").map(name => fixture.root.resolve(s"$name.html"))

        When("the normal entry admits each independently invalid command or asset")
        val rootfault = intercept[CozyDocumentFigureAssets.FigureAssetFault] {
          _execute(_confirmation(fixture, outputs(0)) ++ List("--project-root", fixture.core.toString))
        }
        Files.writeString(fixture.document, original.replace("images/tier-balance-en.png", "../outside.png"), StandardCharsets.UTF_8)
        val pathfault = intercept[CozyDocumentFigureAssets.FigureAssetFault] {
          _execute(_confirmation(fixture, outputs(1)) ++ List("--project-root", fixture.root.toString))
        }
        Files.writeString(fixture.document, original.replace("images/tier-balance-en.png", "images/tier-balance-en.svg"), StandardCharsets.UTF_8)
        val formatfault = intercept[CozyDocumentFigureAssets.FigureAssetFault] {
          _execute(_confirmation(fixture, outputs(2)) ++ List("--project-root", fixture.root.toString))
        }
        val summaryargs = _confirmation(fixture, outputs(3)).updated(10, "summary") ++ List("--project-root", fixture.root.toString)
        val summaryfault = intercept[CozyDocumentDescriptionV2.DescriptionV2Fault](_execute(summaryargs))
        val exists = outputs.map(Files.exists(_, LinkOption.NOFOLLOW_LINKS))

        Then("asset fault codes and authored paths survive while Summary rejects the unused option")
        rootfault.code shouldBe "FIGURE_ASSET_ROOT"
        rootfault.path shouldBe fixture.core.toString
        pathfault.code shouldBe "FIGURE_ASSET_PATH"
        pathfault.path shouldBe "../outside.png"
        formatfault.code shouldBe "FIGURE_ASSET_FORMAT"
        formatfault.path shouldBe "images/tier-balance-en.svg"
        summaryfault.code shouldBe "CONFIRMATION_CLI"
        summaryfault.reason should include("--kind summary does not accept --project-root")
        exists shouldBe Vector.fill(4)(false)
      }
    }
  }

  "Selected Document local-build asset closure" should {
    "build each native Document target in both locales with only its actual PNG dependency" in {
      Vector("ja", "en").foreach { locale =>
        _with_fixture(locale) { fixture =>
          Given("the actual paired fixture with the closed three-target configuration selecting one locale")
          val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document)
          val uri = _image_uri(fixture.image)

          When("both native Document targets are built through the normal command entry")
          val pages = _document_targets.map { target =>
            val report = _execute(_build(fixture, target))
            (target, report, Files.readString(fixture.output(target), StandardCharsets.UTF_8))
          }

          Then("reports retain exact authored input order and append only the used PNG while output navigation reaches the actual blocks")
          pages.foreach { case (target, report, html) =>
            _reported_inputs(report) shouldBe fixture.usedInputs
            report should include("outcome: built")
            report should include(s"output: target/document-project/local-build/$target/index.html")
            report should not include ".svg"
            _complete_html(html, document, uri, target)
          }
        }
      }
    }

    "reuse current output and rebuild after changed valid image bytes while ignoring unselected images and masters" in {
      Vector("ja", "en").foreach { locale =>
        _document_targets.foreach { target =>
          _with_fixture(locale) { fixture =>
            Given("a selected Document output with controlled current input and output timestamps")
            _execute(_build(fixture, target))
            _current_times(fixture, target)
            val prior = Files.readString(fixture.output(target), StandardCharsets.UTF_8)
            val prioruri = _image_uri(fixture.image)

            When("an unchanged build is followed by a valid pixel change newer than the output")
            val unchanged = _execute(_build(fixture, target))
            _replace_image(fixture.image)
            _set_time(fixture.image, 30000L)
            val changeduri = _image_uri(fixture.image)
            val rebuilt = _execute(_build(fixture, target))
            val changed = Files.readString(fixture.output(target), StandardCharsets.UTF_8)
            // Keep subsequent reuse deterministic after the deliberately newer input.
            _set_time(fixture.output(target), 40000L)
            val otherlocale = if (locale == "ja") "en" else "ja"
            val otherimage = fixture.root.resolve(s"images/tier-balance-$otherlocale.png")
            _replace_image(otherimage)
            _set_time(otherimage, 50000L)
            val master = fixture.root.resolve(s"images/tier-balance-$locale.svg")
            Files.writeString(master, Files.readString(master, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
            _set_time(master, 50000L)
            val unselected = _execute(_build(fixture, target))
            val retained = Files.readString(fixture.output(target), StandardCharsets.UTF_8)

            Then("only actual selected image changes rebuild and their new pixels reach the embedded output")
            unchanged should include("outcome: reused")
            rebuilt should include("outcome: built")
            changeduri should not be prioruri
            changed should not be prior
            changed should include(changeduri)
            changed should not include prioruri
            unselected should include("outcome: reused")
            retained shouldBe changed
            _reported_inputs(unselected) shouldBe fixture.usedInputs
          }
        }
      }
    }

    "reject a deleted used image before reusing a current output" in {
      _document_targets.foreach { target =>
        _with_fixture("en") { fixture =>
          Given("a current Document target output whose used image is then removed")
          _execute(_build(fixture, target))
          _current_times(fixture, target)
          val prior = Files.readAllBytes(fixture.output(target)).toVector
          Files.delete(fixture.image)

          When("the current target is requested without force")
          val fault = intercept[CozyDocumentFigureAssets.FigureAssetFault](_execute(_build(fixture, target)))
          val retained = Files.readAllBytes(fixture.output(target)).toVector

          Then("materialization fails on the exact missing asset before freshness can reuse the old HTML")
          fault.code shouldBe "FIGURE_ASSET_FILE"
          fault.path shouldBe "images/tier-balance-en.png"
          retained shouldBe prior
        }
      }
    }

    "retain the authored input set for a table-only Document target" in {
      _with_fixture("en") { fixture =>
        Given("the actual protection table retained without any figure references")
        _without_figure(fixture, true)

        When("both Document targets build the table-only Document")
        val results = _document_targets.map { target =>
          val report = _execute(_build(fixture, target))
          report -> Files.readString(fixture.output(target), StandardCharsets.UTF_8)
        }

        Then("no image dependency is added and all protection cells remain visible")
        results.foreach { case (report, html) =>
          _reported_inputs(report) shouldBe fixture.authoredInputs
          html should include("<td>Service boundary</td><td>Access control</td><td>Service entry</td>")
          html should include("<td>Data boundary</td><td>Encryption</td><td>Storage and transfer</td>")
          html should not include "data:image/"
        }
      }
    }

    "reuse an image-free article after a newer Document-only edit" in {
      _with_fixture("en") { fixture =>
        Given("a current image-free native article and a separate localized Document")
        _execute(_build(fixture, "smartdox-article-html"))
        Vector(fixture.config, fixture.index).foreach(_set_time(_, 10000L))
        _set_time(fixture.output("smartdox-article-html"), 20000L)
        val prior = Files.readAllBytes(fixture.output("smartdox-article-html")).toVector
        val source = Files.readString(fixture.document, StandardCharsets.UTF_8)
        Files.writeString(fixture.document, source.replace("Tier balance and protection responsibilities", "Updated tier balance and protection responsibilities"), StandardCharsets.UTF_8)
        _set_time(fixture.document, 30000L)

        When("only the article target is requested after the Document edit")
        val report = _execute(_build(fixture, "smartdox-article-html"))
        val retained = Files.readAllBytes(fixture.output("smartdox-article-html")).toVector

        Then("article freshness sees only its unchanged configuration and index.dox")
        report should include("outcome: reused")
        _reported_inputs(report) shouldBe Vector("build/document-project-targets.yaml", "index.dox")
        retained shouldBe prior
      }
    }
  }

  private def _complete_html(html: String, document: CozyDocumentDescriptionV2.ValidatedDocument, uri: String, target: String): Unit = {
    val figure = document.description.document.sections.head.blocks(1).asInstanceOf[Figure]
    val table = document.description.document.sections.head.sections.head.blocks.head.asInstanceOf[Table]
    html should include("<figure ")
    html should include(s"""src="$uri""" + "\"")
    html should include(s"""alt="${figure.alt}""" + "\"")
    html should include(s"<figcaption>${figure.caption}</figcaption>")
    html should include(s"<caption>${table.caption}</caption>")
    table.columns.foreach(column => html should include(s"""<th scope="col" data-table-column-id="${column.id}">${column.heading}</th>"""))
    table.rows.foreach(row => html should include(row.cells.map(cell => s"<td>$cell</td>").mkString))
    val markers = if (target == "document-structure-html") Vector("data-block-id=\"tier-comparison\"", "data-block-id=\"protection-coverage\"")
      else Vector("id=\"document-block-tier-comparison\"", "id=\"document-block-protection-coverage\"")
    Vector(figure.coreRefs, table.coreRefs).zip(markers).foreach { case (refs, marker) =>
      val index = html.indexOf(marker)
      index should be >= 0
      val tag = html.substring(html.lastIndexOf('<', index), html.indexOf('>', index) + 1)
      tag should include("data-reference-target=\"true\"")
      Vector("steps" -> refs.steps, "claims" -> refs.claims, "nodes" -> refs.nodes, "relations" -> refs.relations, "flows" -> refs.flows).foreach { case (kind, values) =>
        tag should include(s"""data-core-$kind="${values.mkString(" ")}""" + "\"")
      }
    }
    if (target == "document-structure-html") {
      html should include("<button type=\"button\" class=\"step-control\"")
      html should include("data-step-control=\"true\"")
      html should include("control.addEventListener('click'")
      html should include("scrollIntoView")
      html should include(".document-block.is-highlighted")
    } else {
      val start = html.indexOf("<article id=\"core-step-tier-protection\"")
      start should be >= 0
      val region = html.substring(start, html.indexOf("</article>", start))
      Vector("tier-comparison", "protection-coverage").foreach { id =>
        region should include(s"""data-prose-target-kind="block" data-prose-target-id="$id""" + "\"")
        region should include(s"""href="#document-block-$id""" + "\"")
      }
      html should include("href=\"#core-claim-claim-tier-balance\"")
      html should include("href=\"#core-claim-claim-protection-coverage\"")
    }
  }

  private def _confirmation(fixture: Fixture, output: Path): List[String] =
    List("document-project", "confirmation", "render", "--core", fixture.core.toString, "--document", fixture.document.toString,
      "--vocabulary", fixture.vocabulary.toString, "--kind", "document", "--save", output.toString)

  private def _build(fixture: Fixture, target: String): List[String] =
    List("document-project", "build", fixture.root.toString, "--target", target)

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    val stream = new PrintStream(bytes, true, "UTF-8")
    try Console.withOut(stream)(Cozy.build(args.toArray).executeDirect(args.toArray))
    finally stream.close()
    bytes.toString("UTF-8").trim
  }

  private def _reported_inputs(report: String): Vector[String] =
    report.split("\n").toVector.collect { case line if line.startsWith("  - ") => line.stripPrefix("  - ") }

  private def _image_uri(path: Path): String =
    "data:image/png;base64," + Base64.getEncoder.encodeToString(Files.readAllBytes(path))

  private def _replace_image(path: Path): Unit = {
    val image = ImageIO.read(path.toFile)
    image.setRGB(0, 0, image.getRGB(0, 0) ^ 0x00ffffff)
    ImageIO.write(image, "png", path.toFile) shouldBe true
  }

  private def _set_time(path: Path, millis: Long): Unit = {
    Files.setLastModifiedTime(path, FileTime.fromMillis(millis))
    ()
  }

  private def _current_times(fixture: Fixture, target: String): Unit = {
    fixture.usedInputs.foreach(relative => _set_time(fixture.root.resolve(relative), 10000L))
    _set_time(fixture.output(target), 20000L)
  }

  private def _without_figure(fixture: Fixture, keeptable: Boolean): Unit = {
    val source = _json(new Yaml().load[Any](Files.readString(fixture.document, StandardCharsets.UTF_8)))
    val document = source.hcursor.downField("document").focus.get
    val section = document.hcursor.downField("sections").focus.get.asArray.get.head
    val blocks = section.hcursor.downField("blocks").focus.get.asArray.get.filterNot(_.hcursor.get[String]("kind").toOption.contains("figure"))
    val changed = section.asObject.get.add("blocks", Json.fromValues(blocks))
    val selected = if (keeptable) changed else changed.add("sections", Json.arr())
    val result = source.asObject.get.add("document", Json.fromJsonObject(document.asObject.get.add("sections", Json.arr(Json.fromJsonObject(selected)))))
    Files.writeString(fixture.document, Json.fromJsonObject(result).noSpaces, StandardCharsets.UTF_8)
    ()
  }

  private def _json(value: Any): Json = value match {
    case null => Json.Null
    case text: String => Json.fromString(text)
    case values: java.util.Map[_, _] => Json.obj(values.asScala.toVector.map { case (key, item) => key.toString -> _json(item) }: _*)
    case values: java.util.List[_] => Json.fromValues(values.asScala.map(_json))
    case other => throw new IllegalArgumentException(s"unsupported fixture value: ${other.getClass.getName}")
  }

  private def _configuration(locale: String): String = {
    val inputs = Vector("content/core.yaml", s"content/$locale/document.yaml", s"content/$locale/confirmation-vocabulary.yaml")
    val definitions = (_document_targets.map(_ -> inputs) :+ ("smartdox-article-html" -> Vector("index.dox"))).map { case (id, sources) =>
      val lines = sources.map(value => s"      - $value").mkString("\n")
      s"  $id:\n    inputs:\n$lines\n    prerequisites: []"
    }.mkString("\n")
    s"schema: cozy.document-project.local-build-targets.v1\ntargets:\n$definitions\n"
  }

  private def _with_fixture(locale: String)(body: Fixture => Unit): Unit = {
    val parent = Paths.get("target/phase87/spec").toAbsolutePath.normalize
    Files.createDirectories(parent)
    val work = Files.createDirectory(parent.resolve(UUID.randomUUID().toString)).toRealPath()
    val root = Files.createDirectory(work.resolve("tier-protection.dox"))
    val source = Paths.get(getClass.getResource("/cozy/document/phase-87/tier-protection/content/core.yaml").toURI).getParent.getParent
    val stream = Files.walk(source)
    try stream.iterator.asScala.foreach { path =>
      val destination = root.resolve(source.relativize(path))
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination)
      else Files.copy(path, destination)
    } finally stream.close()
    val fixture = Fixture(root, locale)
    Files.createDirectories(fixture.config.getParent)
    Files.writeString(fixture.config, _configuration(locale), StandardCharsets.UTF_8)
    Files.writeString(fixture.index, "# Independent article\n\nThis image-free article remains authored in index.dox.\n", StandardCharsets.UTF_8)
    try body(fixture)
    finally {
      val paths = Files.walk(work)
      try paths.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
      finally paths.close()
    }
  }
}
