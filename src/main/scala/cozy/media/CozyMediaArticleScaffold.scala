package cozy.media

import org.goldenport.RAISE
import org.goldenport.cli.spec
import cozy.runtime.CozyCliArgs
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 25, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaArticleScaffold {
  private val _p_slug = spec.Parameter.argument("slug")
  private val _p_profile = spec.Parameter.property("profile")
  private val _p_language = spec.Parameter.property("language")
  private val _p_save = spec.Parameter.propertyFileOption("save")

  def execute(args: List[String]): String = {
    val parsed = CozyCliArgs.parseStrict(_p_slug, _p_profile, _p_language, _p_save)(args)
    val slug = parsed.argument("slug").getOrElse(_invalid("Missing article slug"))
    val profile = parsed.requiredProperty("profile")
    val language = parsed.requiredProperty("language")
    val save = parsed.requiredPathProperty("save")
    scaffold(slug, profile, language, save)
    s"Cozy Media Article Scaffold\nstatus: created\npackage: $save\nnext: install the approved presentation template and run the configured renderer"
  }

  def scaffold(slug: String, profile: String, language: String, save: Path): Unit = {
    if (slug == null || !slug.matches("[a-z0-9][a-z0-9._-]*")) _invalid("Article scaffold slug must match [a-z0-9][a-z0-9._-]*")
    if (profile != "business") _invalid("Article scaffold profile must be exactly business")
    if (language == null || !language.matches("[a-z]{2,8}(?:-[a-z0-9]{1,8})*")) _invalid("Article scaffold language must match [a-z]{2,8}(?:-[a-z0-9]{1,8})*")
    val destination = Option(save).map(_.toAbsolutePath.normalize()).getOrElse(_invalid("Article scaffold destination must be defined"))
    if (Files.exists(destination)) _invalid(s"Article scaffold destination already exists: $destination")
    val parent = Option(destination.getParent).getOrElse(_invalid("Article scaffold destination requires a parent directory"))
    if (!Files.isDirectory(parent)) _invalid(s"Article scaffold destination parent must exist: $parent")
    val temporary = Files.createTempDirectory(parent, ".cozy-media-scaffold-")
    try {
      _write(temporary.resolve("article.dox"), s"$slug\n${"=" * slug.length}\n\nA new article media package.\n")
      _write(temporary.resolve("brief.json"), Json.obj("knowledge" -> Json.fromString(slug), "language" -> Json.fromString(language)).spaces2 + "\n")
      _write(temporary.resolve("media.yaml"), _media_yaml(slug, profile, language))
      _write(temporary.resolve("infographic/infographic.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1600\" height=\"900\"><title>Article infographic</title></svg>\n")
      _write(temporary.resolve("presentation/slide-ir.yaml"), _slide_ir(slug, language))
      _write(temporary.resolve("review/manifest.json"), _scaffold_review(slug, language))
      CozyMediaReviewState.scaffold(temporary.resolve("review/state.yaml"))
      Files.createDirectories(temporary.resolve("presentation/template"))
      Files.createDirectories(temporary.resolve("target/cozy-media/slides"))
      Files.createDirectories(temporary.resolve("target/cozy-media/rendered"))
      try Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
      catch {
        case _: AtomicMoveNotSupportedException => _invalid(s"Article scaffold atomic installation is not supported: $destination")
      }
    } catch {
      case e: Throwable =>
        _delete_temporary(temporary)
        throw e
    }
  }

  private def _media_yaml(slug: String, profile: String, language: String): String =
    s"""schema: cozy.media.v1
       |knowledge:
       |  id: $slug
       |  type: documentmodel:KnowledgeUnit
       |  source: article.dox
       |languages: [$language]
       |profiles:
       |  $profile:
       |    root: publication
       |    presentation:
       |      template: presentation/template/approved-template.pptx
       |      renderer:
       |        name: approved-presentation-renderer
       |        version: "1"
       |        command: [approved-presentation-renderer]
       |receipt:
       |  inputs:
       |    - id: slide-ir
       |      role: slide-ir
       |      path: presentation/slide-ir.yaml
       |      normalization: structured-document
       |    - id: presentation-template
       |      role: template
       |      path: presentation/template/approved-template.pptx
       |      normalization: bytes
       |  producer:
       |    profile: $profile
       |    renderer:
       |      name: approved-presentation-renderer
       |      version: "1"
       |resources:
       |  - id: infographic-$language
       |    kind: infographic
       |    language: $language
       |    source: infographic/infographic.svg
       |    output: target/cozy-media/infographic.png
       |    build: svg-to-png
       |    publications:
       |      $profile: $slug/infographic.png
       |  - id: article-pdf-$language
       |    kind: document
       |    language: $language
       |    source: target/rendered/article.pdf
       |    output: target/rendered/article.pdf
       |    build: prebuilt
       |    publications:
       |      $profile: $slug/article.pdf
       |  - id: presentation-$language
       |    kind: presentation
       |    language: $language
       |    source: presentation/slide-ir.yaml
       |    output: target/cozy-media/rendered/article.pptx
       |    build: presentation
       |    publications:
       |      $profile: $slug/article.pptx
       |    presentation:
       |      profile: $profile
       |      slideImages: target/cozy-media/slides
       |      montage: target/cozy-media/rendered/montage.png
       |      rendererManifest: target/cozy-media/rendered/renderer-manifest.json
       |      reviewManifest: review/manifest.json
       |      reviewState: review/state.yaml
       |      articlePdf: article-pdf-$language
       |      infographic: infographic-$language
       |""".stripMargin

  private def _slide_ir(slug: String, language: String): String =
    s"""schema: cozy.slide-ir.v1
       |knowledge: $slug
       |language: $language
       |slides:
       |  - id: overview
       |    title: Article overview
       |    layout: title
       |    elements:
       |      - role: title
       |        text: Article overview
       |      - role: figure
       |        asset: infographic-$language
       |""".stripMargin

  private def _scaffold_review(slug: String, language: String): String =
    Json.obj(
      "schema" -> Json.fromString("cozy.media.presentation-review.scaffold.v1"),
      "target" -> Json.fromString(s"presentation-$language"),
      "knowledge" -> Json.fromString(slug),
      "language" -> Json.fromString(language),
      "status" -> Json.fromString("not-rendered")
    ).spaces2 + "\n"

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _delete_temporary(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).iterator.asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
      }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
