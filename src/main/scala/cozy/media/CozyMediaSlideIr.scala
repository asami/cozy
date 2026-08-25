package cozy.media

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import io.circe.{Json, JsonObject}
import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal

/*
 * @since   Aug. 25, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaSlideIr {
  final case class Element(role: String, text: Option[String], asset: Option[String])
  final case class Slide(id: String, title: String, layout: String, elements: Vector[Element])
  final case class Document(knowledge: String, language: String, slides: Vector[Slide]) {
    def assetIds: Vector[String] = slides.flatMap(_.elements.flatMap(_.asset)).distinct
  }

  private val _schema = "cozy.slide-ir.v1"
  private val _layouts = Set("title", "section", "content", "comparison", "figure", "summary")
  private val _roles = Set("title", "subtitle", "body", "bullet", "figure", "caption", "source", "footer", "logo")
  private val _asset_roles = Set("figure", "logo")
  private val _placeholder = "(?is).*\\b(TODO|TBD|PLACEHOLDER|FIXME)\\b.*|.*\\{\\{.*\\}\\}.*".r

  def load(path: Path): Document = {
    if (!_direct_regular_file(path))
      _invalid(s"Media slide IR must be a direct regular non-symlink file: $path")
    val json = try StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take catch {
      case NonFatal(_) => _invalid(s"Media slide IR must be a supported YAML or JSON document: $path")
    }
    _document(json)
  }

  def validate(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, requireAssets: Boolean): Document = {
    val resource = Option(resolved).map(_.resource).getOrElse(_invalid("Presentation resource must be defined"))
    val configuration = resource.presentation.getOrElse(_invalid(s"Presentation resource requires presentation configuration: ${resource.id}"))
    val source = resolved.source.getOrElse(_invalid(s"Presentation resource requires slide IR source: ${resource.id}"))
    val document = load(source)
    if (document.knowledge != plan.descriptor.knowledge.id)
      _invalid(s"Media slide IR knowledge does not match descriptor: ${resource.id}")
    if (document.language != resource.language.getOrElse(""))
      _invalid(s"Media slide IR language does not match presentation resource: ${resource.id}")
    if (!document.assetIds.contains(configuration.infographic))
      _invalid(s"Media slide IR must reference the configured infographic asset: ${resource.id}")
    document.assetIds.foreach { id =>
      val dependency = plan.resources.find(_.resource.id == id).getOrElse(
        _invalid(s"Media slide IR asset is not a declared resource: $id")
      )
      if (dependency.resource.id == resource.id || dependency.resource.build == "presentation")
        _invalid(s"Media slide IR asset must be a non-presentation dependency: $id")
      if (requireAssets) {
        val path = dependency.output.orElse(if (dependency.resource.build == "prebuilt") dependency.source else None).getOrElse(
          _invalid(s"Media slide IR asset has no current output: $id")
        )
        if (!_direct_regular_file(path))
          _invalid(s"Media slide IR asset has no current output: $id")
      }
    }
    document
  }

  private def _document(value: Json): Document = {
    val objectvalue = _object(value, "Media slide IR")
    _exact_keys(objectvalue, Set("schema", "knowledge", "language", "slides"), "Media slide IR")
    if (_string(objectvalue, "schema", "Media slide IR") != _schema)
      _invalid(s"Media slide IR schema must be exactly ${_schema}")
    val knowledge = _identity(_string(objectvalue, "knowledge", "Media slide IR"), "Media slide IR knowledge")
    val language = _identity(_string(objectvalue, "language", "Media slide IR"), "Media slide IR language")
    val slides = objectvalue("slides").flatMap(_.asArray).getOrElse(
      _invalid("Media slide IR slides must be an array")
    ).toVector.map(_slide)
    if (slides.isEmpty)
      _invalid("Media slide IR requires at least one slide")
    val ids = slides.map(_.id)
    if (ids.distinct.size != ids.size)
      _invalid("Media slide IR slide ids must be unique")
    Document(knowledge, language, slides)
  }

  private def _slide(value: Json): Slide = {
    val objectvalue = _object(value, "Media slide IR slide")
    _exact_keys(objectvalue, Set("id", "title", "layout", "elements"), "Media slide IR slide")
    val id = _identity(_string(objectvalue, "id", "Media slide IR slide"), "Media slide IR slide id")
    if (!id.matches("[a-z0-9][a-z0-9._-]*"))
      _invalid(s"Media slide IR slide id is invalid: $id")
    val title = _text(_string(objectvalue, "title", "Media slide IR slide"), "Media slide IR slide title")
    val layout = _identity(_string(objectvalue, "layout", "Media slide IR slide"), "Media slide IR slide layout")
    if (!_layouts.contains(layout))
      _invalid(s"Media slide IR layout is invalid: $layout")
    val elements = objectvalue("elements").flatMap(_.asArray).getOrElse(
      _invalid(s"Media slide IR slide elements must be an array: $id")
    ).toVector.map(_element)
    if (elements.isEmpty)
      _invalid(s"Media slide IR slide requires at least one element: $id")
    val titles = elements.filter(_.role == "title")
    if (titles.size != 1 || titles.head.text != Some(title))
      _invalid(s"Media slide IR slide requires exactly one matching title element: $id")
    Slide(id, title, layout, elements)
  }

  private def _element(value: Json): Element = {
    val objectvalue = _object(value, "Media slide IR element")
    val keys = objectvalue.keys.toSet
    if (!keys.subsetOf(Set("role", "text", "asset")) || !keys.contains("role") || keys.contains("text") == keys.contains("asset"))
      _invalid("Media slide IR element requires role and exactly one of text or asset")
    val role = _identity(_string(objectvalue, "role", "Media slide IR element"), "Media slide IR element role")
    if (!_roles.contains(role))
      _invalid(s"Media slide IR element role is invalid: $role")
    val text = objectvalue("text").map(_.asString.getOrElse(_invalid("Media slide IR element text must be a string"))).map(_text(_, "Media slide IR element text"))
    val asset = objectvalue("asset").map(_.asString.getOrElse(_invalid("Media slide IR element asset must be a string"))).map(_identity(_, "Media slide IR element asset"))
    if (_asset_roles.contains(role) != asset.isDefined)
      _invalid(s"Media slide IR element role requires ${if (_asset_roles.contains(role)) "asset" else "text"}: $role")
    Element(role, text, asset)
  }

  private def _text(value: String, label: String): String = {
    val text = _identity(value, label)
    if (_placeholder.pattern.matcher(text).matches())
      _invalid(s"$label contains a forbidden placeholder")
    text
  }

  private def _object(value: Json, label: String): JsonObject =
    value.asObject.getOrElse(_invalid(s"$label must be an object"))

  private def _exact_keys(value: JsonObject, expected: Set[String], label: String): Unit =
    if (value.keys.toSet != expected)
      _invalid(s"$label requires exactly: ${expected.toVector.sorted.mkString(", ")}")

  private def _string(value: JsonObject, field: String, label: String): String =
    value(field).flatMap(_.asString).getOrElse(_invalid(s"$label.$field must be a string"))

  private def _identity(value: String, label: String): String =
    if (value == null || value.isEmpty || value != value.trim)
      _invalid(s"$label must be a non-empty exact trimmed string")
    else value

  private def _direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
