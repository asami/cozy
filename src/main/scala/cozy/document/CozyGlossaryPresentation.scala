package cozy.document

import com.fasterxml.jackson.core.{JsonFactory, JsonParser}
import io.circe.{Json, JsonObject}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

/** Label selection after the producer has resolved and validated a glossary page. */
private[cozy] object CozyGlossaryPresentation {
  final case class Label(text: String, language: String)
  final case class Selector(collection: String, language: String, text: String, span: Option[Span] = None)
  final case class Preference(documentLanguage: String, label: Selector, span: Option[Span] = None)
  final case class Presentation(
    role: String,
    original: Selector,
    canonical: Vector[Selector],
    establishedJapanese: Option[Selector],
    preferences: Vector[Preference],
    span: Option[Span] = None
  )
  final case class Labels(titles: Vector[Label], aliases: Vector[Label])
  final case class Selection(label: String, language: Option[String], diagnostic: Option[Diagnostic])

  final case class Position(line: Int, column: Int)
  final case class Span(source: String, start: Position, end: Position)
  final case class WireLabel(text: String, language: Option[String], raw: Json)
  final case class PageIdentity(space: String, term: String, concept: String, page: String)
  final case class Diagnostic(code: String, message: String, span: Option[Span], raw: Option[Json] = None)
  final case class Page(identity: PageIdentity, titles: Vector[WireLabel], aliases: Vector[WireLabel],
    presentation: Option[Presentation], presentationIssue: Option[Diagnostic], raw: Json, link: String)
  final case class Reference(visibleText: Option[String], raw: Json, issue: Option[Diagnostic] = None)
  final case class WireDiagnostic(code: String, severity: String, message: String, span: Option[Span],
    value: Option[String], related: Vector[Span], candidates: Vector[PageIdentity], raw: Json)
  final case class Catalog(pages: Vector[Page], references: Vector[Reference], diagnostics: Vector[WireDiagnostic],
    presentationDiagnostics: Vector[WireDiagnostic], raw: Json) {
    private lazy val _by_identity = pages.groupBy(_.identity)
    private lazy val _by_page = pages.groupBy(_.identity.page)
    def resolve(binding: CozyDocumentPatternCodec.TermBinding): Either[Diagnostic, Page] = {
      val key = PageIdentity(binding.space, binding.term, binding.concept, binding.page)
      _by_identity.getOrElse(key, Vector.empty) match {
        case Vector(page) if _by_page.getOrElse(binding.page, Vector.empty).size == 1 => Right(page)
        case matches if matches.nonEmpty => Left(Diagnostic("LEX_PRESENTATION_AMBIGUOUS", "multiple pages match the supplied identity", None))
        case _ if _by_page.contains(binding.page) => Left(Diagnostic("LEX_PRESENTATION_IDENTITY_MISMATCH", "page identity does not match Space, Term and Concept", None))
        case _ => Left(Diagnostic("LEX_PRESENTATION_UNDEFINED", "page identity is absent in the admitted catalog", None))
      }
    }
  }
  final case class ResolvedSelection(identity: PageIdentity, link: String, selection: Option[Selection], page: Page)
  final case class WireFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  /** Reads the same knowledge envelope used by BoK projection, retaining raw producer fields. */
  def read(path: Path): Catalog = {
    val text = Files.readString(path, StandardCharsets.UTF_8)
    val parser = new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try { while (parser.nextToken() != null) () } catch {
      case NonFatal(error) => throw WireFault("LEX_PRESENTATION_METADATA", "$", error.getMessage)
    } finally parser.close()
    val json = io.circe.parser.parse(text).fold(error => throw WireFault("LEX_PRESENTATION_METADATA", "$", error.message), identity)
    _decode(json)
  }

  /** Decode presentation without inventing labels or changing baseline identity. */
  private def _decode(json: Json): Catalog = {
    val root = _obj(json, "$")
    if (_required_string(root, "schema", "$") != "lexidox-knowledge/1")
      throw WireFault("LEX_PRESENTATION_VERSION", "$.schema", "unsupported knowledge schema")
    val pages = _required_array(root, "pages", "$").zipWithIndex.map { case (value, index) => _page(value, s"$$.pages[$index]") }
    val references = _optional_array(root, "references", "$").zipWithIndex.map { case (value, index) =>
      val path = s"$$.references[$index]"
      val obj = _obj(value, path)
      val visible = _optional_string(obj, "visible_text", path)
      val resolution = value.hcursor.downField("resolution")
      val pageid = resolution.get[String]("page_iri").toOption
      val target = pages.filter(page => pageid.contains(page.identity.page))
      val extended = target.exists(page => page.presentation.nonEmpty || page.presentationIssue.nonEmpty)
      val complete = for {
        space <- resolution.get[String]("space").toOption
        term <- resolution.get[String]("term_iri").toOption
        concept <- resolution.get[String]("concept_iri").toOption
        page <- pageid
      } yield PageIdentity(space, term, concept, page)
      val issue = if (extended && (target.size != 1 || !complete.contains(target.head.identity) || visible.isEmpty))
        Some(Diagnostic("LEX_PRESENTATION_REFERENCE", "resolved presentation reference requires matching identities and visible_text", None, Some(value)))
      else None
      Reference(visible, value, issue)
    }
    val diagnostics = _optional_array(root, "diagnostics", "$").zipWithIndex.map { case (value, index) => _wire_diagnostic(value, s"$$.diagnostics[$index]") }
    val presentationdiagnostics = _optional_array(root, "presentation_diagnostics", "$").zipWithIndex.map { case (value, index) => _wire_diagnostic(value, s"$$.presentation_diagnostics[$index]") }
    Catalog(pages, references, diagnostics, presentationdiagnostics, json)
  }

  /** Select only after exact identity resolution, retaining the resolved link and page. */
  def resolve(term: CozyDocumentDescriptionV2.InlineTerm, catalog: Catalog, documentLanguage: String,
    profile: Option[String]): Either[Diagnostic, ResolvedSelection] =
    catalog.resolve(term.binding).flatMap { page =>
      page.presentationIssue match {
        case Some(problem) => Left(problem)
        case None if page.presentation.exists(_.role != term.binding.role) =>
          Left(Diagnostic("LEX_PRESENTATION_ROLE_CONFLICT", "Core role conflicts with producer role", None))
        case None =>
          val labels = Labels(page.titles.flatMap(x => x.language.map(Label(x.text, _))),
            page.aliases.flatMap(x => x.language.map(Label(x.text, _))))
          select(labels, page.presentation, documentLanguage, profile, term.visibleText) match {
            case Left(reason) => Left(Diagnostic("LEX_PRESENTATION_METADATA", reason, None))
            case Right(selection) => Right(ResolvedSelection(page.identity, page.link, selection, page))
          }
      }
    }

  private def _page(value: Json, path: String): Page = {
    val obj = _obj(value, path)
    val identity = PageIdentity(_required_string(obj, "space", path), _required_string(obj, "term_iri", path),
      _required_string(obj, "concept_iri", path), _required_string(obj, "page_iri", path))
    val titles = _required_array(obj, "titles", path).zipWithIndex.map { case (label, index) => _label(label, s"$path.titles[$index]") }
    val aliases = _optional_array(obj, "aliases", path).zipWithIndex.map { case (label, index) => _label(label, s"$path.aliases[$index]") }
    if (titles.isEmpty) throw WireFault("LEX_PRESENTATION_METADATA", s"$path.titles", "expected a title")
    val extension = obj("presentation").map { json =>
      try {
        val presentation = _presentation(json, s"$path.presentation")
        val labels = Labels(titles.flatMap(x => x.language.map(Label(x.text, _))), aliases.flatMap(x => x.language.map(Label(x.text, _))))
        select(labels, Some(presentation), "en", Some("original"), "").left.foreach(reason =>
          throw WireFault("LEX_PRESENTATION_METADATA", s"$path.presentation", reason))
        Right(presentation)
      } catch { case fault: WireFault => Left(Diagnostic(fault.code, fault.reason, None, Some(json))) }
    }
    Page(identity, titles, aliases, extension.flatMap(_.toOption), extension.flatMap(_.left.toOption), value, identity.page)
  }

  private def _wire_diagnostic(value: Json, path: String): WireDiagnostic = {
    val obj = _obj(value, path)
    val related = _optional_array(obj, "related", path).zipWithIndex.map { case (item, index) => _span(item, s"$path.related[$index]") }
    val candidates = _optional_array(obj, "candidates", path).zipWithIndex.map { case (item, index) =>
      val itempath = s"$path.candidates[$index]"
      val fields = _obj(item, itempath)
      PageIdentity(_required_string(fields, "space", itempath), _required_string(fields, "term_iri", itempath),
        _required_string(fields, "concept_iri", itempath), _required_string(fields, "page_iri", itempath))
    }
    WireDiagnostic(_required_string(obj, "code", path), _required_string(obj, "severity", path),
      _required_string(obj, "message", path), obj("span").map(item => _span(item, s"$path.span")),
      _optional_string(obj, "value", path), related, candidates, value)
  }
  private def _label(value: Json, path: String): WireLabel = {
    val obj = _obj(value, path)
    val text = _required_string(obj, "text", path)
    val language = _optional_string(obj, "language", path)
    if (text.isEmpty || language.exists(x => !_language(x))) throw WireFault("LEX_PRESENTATION_METADATA", path, "invalid label")
    WireLabel(text, language, value)
  }
  private def _presentation(value: Json, path: String): Presentation = {
    val obj = _obj(value, path)
    if (!obj("version").flatMap(_.asNumber).flatMap(_.toInt).contains(1))
      throw WireFault("LEX_PRESENTATION_VERSION", s"$path.version", "unsupported presentation version")
    val role = _required_string(obj, "role", path)
    val original = _selector(_required(obj, "original", path), s"$path.original")
    val canonical = _optional_array(obj, "canonical", path).zipWithIndex.map { case (x, i) => _selector(x, s"$path.canonical[$i]") }
    val established = obj("established_japanese").map(x => _selector(x, s"$path.established_japanese"))
    val preferences = _optional_array(obj, "preferences", path).zipWithIndex.map { case (x, i) =>
      val p = s"$path.preferences[$i]"
      val fields = _obj(x, p)
      Preference(_required_string(fields, "document_language", p), _selector(_required(fields, "label", p), s"$p.label"), Some(_span(_required(fields, "span", p), s"$p.span")))
    }
    Presentation(role, original, canonical, established, preferences, Some(_span(_required(obj, "span", path), s"$path.span")))
  }
  private def _selector(value: Json, path: String): Selector = {
    val obj = _obj(value, path)
    Selector(_required_string(obj, "collection", path), _required_string(obj, "language", path), _required_string(obj, "text", path),
      Some(_span(_required(obj, "span", path), s"$path.span")))
  }
  private def _span(value: Json, path: String): Span = {
    val obj = _obj(value, path)
    def _position_(name: String): Position = {
      val p = _obj(_required(obj, name, path), s"$path.$name")
      def _number_(key: String): Int = p(key).flatMap(_.asNumber).flatMap(_.toInt).filter(_ > 0).getOrElse(throw WireFault("LEX_PRESENTATION_METADATA", s"$path.$name.$key", "expected positive integer"))
      Position(_number_("line"), _number_("column"))
    }
    Span(_required_string(obj, "source", path), _position_("start"), _position_("end"))
  }
  private def _obj(value: Json, path: String): JsonObject = value.asObject.getOrElse(throw WireFault("LEX_PRESENTATION_METADATA", path, "expected object"))
  private def _required(obj: JsonObject, key: String, path: String): Json = obj(key).getOrElse(throw WireFault("LEX_PRESENTATION_METADATA", s"$path.$key", "missing field"))
  private def _required_string(obj: JsonObject, key: String, path: String): String = _required(obj, key, path).asString.getOrElse(throw WireFault("LEX_PRESENTATION_METADATA", s"$path.$key", "expected string"))
  private def _optional_string(obj: JsonObject, key: String, path: String): Option[String] = obj(key).map(_.asString.getOrElse(throw WireFault("LEX_PRESENTATION_METADATA", s"$path.$key", "expected string")))
  private def _required_array(obj: JsonObject, key: String, path: String): Vector[Json] = _required(obj, key, path).asArray.map(_.toVector).getOrElse(throw WireFault("LEX_PRESENTATION_METADATA", s"$path.$key", "expected array"))
  private def _optional_array(obj: JsonObject, key: String, path: String): Vector[Json] = obj(key).map(_.asArray.map(_.toVector).getOrElse(throw WireFault("LEX_PRESENTATION_METADATA", s"$path.$key", "expected array"))).getOrElse(Vector.empty)

  /** A None result retains legacy presentation when no effective profile applies. */
  def select(
    labels: Labels,
    presentation: Option[Presentation],
    documentLanguage: String,
    profile: Option[String],
    visibleText: String
  ): Either[String, Option[Selection]] = {
    if (profile.exists(x => !Set("original", "established-japanese", "japanese").contains(x)))
      Left("unknown presentation profile")
    else presentation match {
      case None => Right(None)
      case Some(value) if !Set("ordinary-term", "named-pattern").contains(value.role) => Left("invalid presentation role")
      case Some(value) =>
        val indexed = (labels.titles.map(x => (("titles", x.language.toLowerCase(java.util.Locale.ROOT), x.text), x)) ++
          labels.aliases.map(x => (("aliases", x.language.toLowerCase(java.util.Locale.ROOT), x.text), x))).groupBy(_._1).map {
            case (key, values) => key -> values.map(_._2)
          }
        def _matching_(selector: Selector): Vector[Label] =
          indexed.getOrElse((selector.collection, selector.language.toLowerCase(java.util.Locale.ROOT), selector.text), Vector.empty)
        val selectors = Vector(value.original) ++ value.canonical ++ value.establishedJapanese.toVector ++ value.preferences.map(_.label)
        val canonical = value.canonical.map(_.language.toLowerCase(java.util.Locale.ROOT))
        val preferences = value.preferences.map(_.documentLanguage.toLowerCase(java.util.Locale.ROOT))
        if (!_language(documentLanguage) || selectors.exists(x => x.text.isEmpty || !_language(x.language)) ||
            value.preferences.exists(x => !_language(x.documentLanguage)) ||
            canonical.distinct.size != canonical.size || preferences.distinct.size != preferences.size ||
            value.establishedJapanese.exists(x => !x.language.equalsIgnoreCase("ja") && !x.language.toLowerCase(java.util.Locale.ROOT).startsWith("ja-")) ||
            selectors.exists(x => _matching_(x).size != 1))
          Left("invalid presentation selectors")
        else {
          val selectedprofile = profile.orElse(if (value.role == "named-pattern") Some("original") else None)
          val preference = value.preferences.find(_.documentLanguage.equalsIgnoreCase(documentLanguage)).map(_.label)
          val documentcanonical = value.canonical.find(_.language.equalsIgnoreCase(documentLanguage))
          val japanese = value.canonical.find(_.language.equalsIgnoreCase("ja"))
          val candidates = selectedprofile.map {
            case "original" => Vector(Some(value.original), preference, documentcanonical)
            case "established-japanese" => Vector(value.establishedJapanese, Some(value.original), preference, documentcanonical)
            case "japanese" => Vector(japanese, preference, Some(value.original), documentcanonical)
          }
          Right(candidates.map { values =>
            values.flatten.headOption.flatMap(x => _matching_(x).headOption) match {
              case Some(label) => Selection(label.text, Some(label.language), None)
              case None => Selection(visibleText, None, Some(Diagnostic("LEX_PRESENTATION_LABEL_MISSING", "no usable label", None)))
            }
          })
        }
    }
  }

  private def _language(value: String): Boolean =
    if (value.isEmpty || value != value.trim) false
    else try {
      new java.util.Locale.Builder().setLanguageTag(value).build()
      true
    } catch {
      case _: java.util.IllformedLocaleException => false
    }

}
