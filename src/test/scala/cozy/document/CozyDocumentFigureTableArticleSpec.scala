package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.nio.file.attribute.FileTime
import java.util.{Base64, UUID}
import javax.imageio.ImageIO
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.{Dox, Figure => NativeFigure, Section => NativeSection, Table => NativeTable}
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.transformers.Dox2HtmlTransformer
import scala.collection.JavaConverters._

/*
 * @since   Oct. 25, 2026
 * @version Oct. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyDocumentFigureTableArticleSpec {
  final case class Fixture(root: Path, locale: String) {
    def index: Path = root.resolve("index.dox")
    def media: Path = root.resolve("media.yaml")
    def image: Path = root.resolve(s"images/tier-balance-$locale.png")
    def document: Path = root.resolve(s"content/$locale/document.yaml")
    def pdf: Path = root.resolve(s"target/cozy-media/article-$locale.pdf")
    def output: Path = root.resolve("target/document-project/local-build/smartdox-article-html/index.html")
    def inputs: Vector[String] = Vector("build/document-project-targets.yaml", "index.dox", s"images/tier-balance-$locale.png")
    def admitted: CozyDocumentDescriptionV2.ValidatedDocument =
      CozyDocumentDescriptionV2.loadDocument(root.resolve("content/core.yaml"), document)
  }

  def assemble(work: Path, locale: String): Fixture = {
    val root = Files.createDirectory(work.resolve(s"$locale.dox"))
    val source = Paths.get(getClass.getResource("/cozy/document/phase-87/tier-protection/content/core.yaml").toURI).getParent.getParent
    Vector("content", "images").foreach { category =>
      val directory = source.resolve(category)
      val stream = Files.walk(directory)
      try stream.iterator.asScala.foreach { path =>
        val destination = root.resolve(source.relativize(path))
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination)
        else Files.copy(path, destination)
      } finally stream.close()
    }
    Vector("index.dox", "media.yaml").foreach { name =>
      Files.copy(source.resolve(s"articles/$locale/$name"), root.resolve(name), StandardCopyOption.REPLACE_EXISTING)
    }
    val inputs = Vector("content/core.yaml", s"content/$locale/document.yaml", s"content/$locale/confirmation-vocabulary.yaml")
    val definitions = Vector("document-structure-html" -> inputs, "document-reader-html" -> inputs,
      "smartdox-article-html" -> Vector("index.dox")).map { case (id, sources) =>
      s"  $id:\n    inputs:\n" + sources.map(value => s"      - $value\n").mkString + "    prerequisites: []\n"
    }.mkString
    val configuration = root.resolve("build/document-project-targets.yaml")
    Files.createDirectories(configuration.getParent)
    Files.writeString(configuration, "schema: cozy.document-project.local-build-targets.v1\ntargets:\n" + definitions, StandardCharsets.UTF_8)
    Fixture(root, locale)
  }

  def replaceImage(path: Path): Unit = {
    val image = ImageIO.read(path.toFile)
    image.setRGB(0, 0, image.getRGB(0, 0) ^ 0x00ffffff)
    ImageIO.write(image, "png", path.toFile)
    ()
  }

  def sections(document: CozyDocumentDescriptionV2.ValidatedDocument): Vector[CozyDocumentDescriptionV2.Section] = {
    def _walk_(values: Vector[CozyDocumentDescriptionV2.Section]): Vector[CozyDocumentDescriptionV2.Section] =
      values.flatMap(value => Vector(value) ++ _walk_(value.sections))
    _walk_(document.description.document.sections)
  }

  def blocks(document: CozyDocumentDescriptionV2.ValidatedDocument): Vector[CozyDocumentDescriptionV2.Block] =
    sections(document).flatMap(_.blocks)

  def texts(document: CozyDocumentDescriptionV2.ValidatedDocument): Vector[String] =
    Vector(document.description.document.title) ++ sections(document).flatMap { section =>
      Vector(section.heading) ++ section.blocks.flatMap {
        case paragraph: CozyDocumentDescriptionV2.Paragraph => Vector(paragraph.text)
        case figure: CozyDocumentDescriptionV2.Figure => Vector(figure.caption)
        case table: CozyDocumentDescriptionV2.Table => Vector(table.caption) ++ table.columns.map(_.heading) ++ table.rows.flatMap(_.cells)
        case _ => Vector.empty
      }
    }
}

final class CozyDocumentFigureTableArticleSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDocumentFigureTableArticleSpec._
  import CozyDocumentDescriptionV2.{Figure, Paragraph, Table}

  "Paired native SmartDox figure and table articles" should {
    "parse and embed the complete localized Document explanations through the native article route" in {
      Vector("ja", "en").foreach { locale =>
        _with_fixture(locale) { fixture =>
          Given("the actual paired Core, admitted localized Document and independently authored matching index.dox")
          val document = fixture.admitted
          val blocks = CozyDocumentFigureTableArticleSpec.blocks(document)
          val figure = blocks.collectFirst { case value: Figure => value }.get
          val table = blocks.collectFirst { case value: Table => value }.get
          val paragraph = blocks.collectFirst { case value: Paragraph => value }.get

          When("the production parser prepares the article and the native DOM transformer attaches its used image")
          val prepared = CozyDocumentProjectSmartDoxArticleHtml.prepare(fixture.root)
          val nodes = _nodes(prepared.document)
          val nativefigure = nodes.collect { case value: NativeFigure => value }
          val nativetable = nodes.collect { case value: NativeTable => value }
          val rendered = CozyDocumentProjectSmartDoxArticleHtml.render(prepared)
          val html = scala.xml.XML.loadString(rendered.html)
          val original = scala.xml.XML.loadString(Dox2HtmlTransformer(SmartDoxContext.create(), Dox2HtmlTransformer.Rule.default)
            .transform(prepared.document).take)

          Then("native model content and emitted figure, table and metadata preserve the admitted meaning and ordering")
          nodes.flatMap(_.getLanguage) should contain(java.util.Locale.forLanguageTag(locale))
          figure.coreRefs.claims shouldBe Vector("claim-tier-balance")
          table.coreRefs.claims shouldBe Vector("claim-protection-coverage")
          figure.coreRefs.steps shouldBe table.coreRefs.steps
          nativefigure.size shouldBe 1
          nativefigure.head.img.src.getPath shouldBe figure.asset
          nativefigure.head.caption.toText shouldBe figure.caption
          nativetable.size shouldBe 1
          nativetable.head.caption.map(_.toText) shouldBe Some(table.caption)
          nativetable.head.label shouldBe Some(table.id)
          nativetable.head.head.toVector.flatMap(_.records).map(_.fields.map(_.toText).toVector) shouldBe Vector(table.columns.map(_.heading))
          nativetable.head.body.records.map(_.fields.map(_.toText).toVector).toVector shouldBe table.rows.map(_.cells)
          nodes.collect { case value: NativeSection => Dox.getText(value.title).getOrElse("") } should contain allElementsOf sections(document).map(_.heading)
          nodes.map(_.toText) should contain(paragraph.text)
          rendered.assets shouldBe Vector(fixture.image)
          _elements(html, "img").map(node => (node \ "@src").text) shouldBe Vector(_image_uri(fixture.image))
          _elements(html, "figcaption").map(_.text.trim) shouldBe Vector(figure.caption)
          _elements(html, "caption").map(_.text.trim) shouldBe Vector(table.caption)
          _elements(html, "th").map(_.text.trim) shouldBe table.columns.map(_.heading)
          _elements(html, "td").map(_.text.trim) shouldBe table.rows.flatMap(_.cells)
          _elements(html, "head") shouldBe _elements(original, "head")
          _elements(html, "a").map(node => (node \ "@href").text) should contain("https://example.com/tier-protection")
          val order = Vector(paragraph.text, figure.caption, sections(document).last.heading, table.caption) ++ table.rows.flatMap(_.cells)
          val positions = order.map(rendered.html.indexOf)
          positions.foreach(_ should be >= 0)
          positions shouldBe positions.sorted
          texts(document).foreach(value => rendered.html should include(value))
        }
      }
    }

    "rebuild only for a used article image and embed its changed valid pixels" in {
      Vector("ja", "en").foreach { locale =>
        _with_fixture(locale) { fixture =>
          Given("a current article output whose exact inputs are configuration, index.dox and one localized PNG")
          _build(fixture)
          _current_times(fixture)
          val prioruri = _image_uri(fixture.image)

          When("an unchanged request precedes a newer valid pixel change and then unrelated source changes")
          val unchanged = _build(fixture)
          replaceImage(fixture.image)
          _time(fixture.image, 30000L)
          val rebuilt = _build(fixture)
          val changeduri = _image_uri(fixture.image)
          val changedhtml = Files.readString(fixture.output, StandardCharsets.UTF_8)
          _time(fixture.output, 40000L)
          val otherlocale = if (locale == "ja") "en" else "ja"
          val otherimage = fixture.root.resolve(s"images/tier-balance-$otherlocale.png")
          replaceImage(otherimage)
          _time(otherimage, 50000L)
          _time(fixture.root.resolve(s"images/tier-balance-$locale.svg"), 50000L)
          Files.writeString(fixture.document, Files.readString(fixture.document, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
          _time(fixture.document, 50000L)
          val unrelated = _build(fixture)

          Then("only the selected PNG invalidates the article and the complete new image reaches its output")
          unchanged.outcome shouldBe "reused"
          rebuilt.outcome shouldBe "built"
          unrelated.outcome shouldBe "reused"
          Vector(unchanged, rebuilt, unrelated).foreach(_.inputs shouldBe fixture.inputs)
          changeduri should not be prioruri
          changedhtml should include(changeduri)
          changedhtml should not include prioruri
          Files.readString(fixture.output, StandardCharsets.UTF_8) should include(changeduri)
        }
      }
    }

    "reject a missing used image before reusing current article output" in {
      _with_fixture("en") { fixture =>
        Given("a current native article whose selected image has been removed")
        _build(fixture)
        _current_times(fixture)
        Files.delete(fixture.image)

        When("the same local article target is requested without force")
        val fault = intercept[CozyDocumentFigureAssets.FigureAssetFault](_build(fixture))

        Then("materialization reports the actual used image before freshness can reuse the output")
        fault.code shouldBe "FIGURE_ASSET_FILE"
        fault.path shouldBe "images/tier-balance-en.png"
      }
    }

    "reject unsafe actual article image URIs before current output reuse" in {
      Vector("../outside.png", "%2e%2e/outside.png", "/outside.png", "https://example.com/outside.png", "//example.com/outside.png").foreach { reference =>
        _with_fixture("en") { fixture =>
          Given("a current native article whose authored used image URI is independently changed to " + reference)
          _build(fixture)
          _current_times(fixture)
          val source = Files.readString(fixture.index, StandardCharsets.UTF_8)
          Files.writeString(fixture.index, source.replace("images/tier-balance-en.png", reference), StandardCharsets.UTF_8)
          _time(fixture.index, 10000L)

          When("the local article target prepares the actual changed native image node")
          val fault = intercept[CozyDocumentFigureAssets.FigureAssetFault](_build(fixture))

          Then("the shared asset path fault rejects the unsafe selection before reuse")
          fault.code shouldBe "FIGURE_ASSET_PATH"
          fault.path should not be empty
        }
      }
    }

    "decode local image URI paths exactly once while retaining first-use dependency order" in {
      _with_fixture("en") { fixture =>
        Given("an authored article with repeated selected images and a literal percent-encoded filename")
        val second = fixture.root.resolve("images/tier%20comparison.png")
        Files.copy(fixture.image, second)
        val source = Files.readString(fixture.index, StandardCharsets.UTF_8) +
          "\n[[images/tier%2520comparison.png]]\n\n[[images/tier-balance-en.png]]\n"
        Files.writeString(fixture.index, source, StandardCharsets.UTF_8)

        When("the article prepares its native image references and embeds each DOM image")
        val prepared = CozyDocumentProjectSmartDoxArticleHtml.prepare(fixture.root)
        val rendered = CozyDocumentProjectSmartDoxArticleHtml.render(prepared)
        val images = _elements(scala.xml.XML.loadString(rendered.html), "img")

        Then("only actual paths appear once in first-use order and every produced image has admitted bytes")
        rendered.assets shouldBe Vector(fixture.image, second)
        images.size shouldBe 3
        images.map(node => (node \ "@src").text) shouldBe Vector(_image_uri(fixture.image), _image_uri(second), _image_uri(fixture.image))
      }
    }
  }

  private def _nodes(value: Dox): Vector[Dox] = Vector(value) ++ value.elements.toVector.flatMap(_nodes)

  private def _elements(value: scala.xml.Node, label: String): Vector[scala.xml.Node] =
    value.descendant_or_self.filter(_.label.equalsIgnoreCase(label)).toVector

  private def _image_uri(path: Path): String = "data:image/png;base64," + Base64.getEncoder.encodeToString(Files.readAllBytes(path))

  private def _build(fixture: Fixture): CozyDocumentProjectLocalBuildCommand.Result =
    CozyDocumentProjectLocalBuildCommand.execute(fixture.root, Some("smartdox-article-html"), force = false)

  private def _time(path: Path, millis: Long): Unit = { Files.setLastModifiedTime(path, FileTime.fromMillis(millis)); () }

  private def _current_times(fixture: Fixture): Unit = {
    fixture.inputs.foreach(relative => _time(fixture.root.resolve(relative), 10000L))
    _time(fixture.output, 20000L)
  }

  private def _with_fixture(locale: String)(body: Fixture => Unit): Unit = {
    val parent = Files.createDirectories(Paths.get("target/phase87/spec").toAbsolutePath.normalize())
    val work = Files.createDirectory(parent.resolve(UUID.randomUUID.toString)).toRealPath()
    val fixture = assemble(work, locale)
    try body(fixture)
    finally {
      val stream = Files.walk(work)
      try stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
      finally stream.close()
    }
  }
}
