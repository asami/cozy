package cozy.document

import java.net.URI
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import org.smartdox.{Body, Caption, Code, Document, Dox, Figure, Head, Li, Paragraph, ReferenceImg, Section, Table, TBody, TD, THead, TR, Text, Ul}
import org.smartdox.semanticweb.TermPresentation
import org.smartdox.semanticweb.TermPresentation.{Emphasis, Label, Occurrence, Options, Profile, Role, Style}

/** Converts already-resolved Document occurrences to the SmartDox renderer contract. */
private[cozy] object CozyDocumentTermPresentation {
  final case class RoleStyle(symbol: Option[String], style: Style)
  final case class PdfSettings(latexEngine: String, dependencyMode: String, docker: Option[String], dockerImage: Option[String])
  final case class Configuration(profile: Profile, editorEnabled: Boolean, ordinary: RoleStyle, named: RoleStyle, base: Option[URI], pdf: PdfSettings)
  final case class RenderInput(document: Document, options: Options)

  def load(project: java.nio.file.Path, relative: String): Configuration = {
    val requested = java.nio.file.Paths.get(relative)
    if (relative.isEmpty || relative != relative.trim || requested.isAbsolute ||
        requested.iterator().asScala.exists(part => Set(".", "..").contains(part.toString)))
      throw new IllegalArgumentException("term presentation configuration must be a direct project-relative path")
    val path = CozyDocumentProject._direct_file(project, relative, "term presentation configuration")
    val root = CozyDocumentProject._load_json(path, "term presentation configuration").asObject.getOrElse(
      throw new IllegalArgumentException("term presentation configuration must be an object"))
    if (!root.keys.toSet.subsetOf(Set("profile", "htmlSwitch", "glossaryBase", "ordinary", "named", "pdf")))
      throw new IllegalArgumentException("unknown term presentation configuration field")
    val profile = root("profile").flatMap(_.asString).getOrElse(throw new IllegalArgumentException("profile is required")) match {
      case "original" => Profile.Original
      case "established-japanese" => Profile.EstablishedJapanese
      case "japanese" => Profile.Japanese
      case value => throw new IllegalArgumentException(s"unknown presentation profile: $value")
    }
    val enabled = root("htmlSwitch").map(_.asBoolean.getOrElse(throw new IllegalArgumentException("htmlSwitch must be boolean"))).getOrElse(false)
    val base = root("glossaryBase").map(value => new URI(value.asString.getOrElse(throw new IllegalArgumentException("glossaryBase must be a URI"))))
    base.foreach { value =>
      if (!value.isAbsolute || !Set("http", "https").contains(Option(value.getScheme).getOrElse("").toLowerCase(java.util.Locale.ROOT)) ||
          Option(value.getHost).forall(_.isEmpty) || value.getUserInfo != null || value.getRawQuery != null || value.getRawFragment != null)
        throw new IllegalArgumentException("glossaryBase must be an HTTP(S) base URI without user information, query or fragment")
    }
    def _style_(name: String): RoleStyle = root(name) match {
      case None => RoleStyle(None, Style())
      case Some(value) =>
        val fields = value.asObject.getOrElse(throw new IllegalArgumentException(s"$name must be an object"))
        if (!fields.keys.toSet.subsetOf(Set("symbol", "font", "color"))) throw new IllegalArgumentException(s"unknown $name style field")
        val symbol = fields("symbol").map(_.asString.filter(_.matches("[A-Za-z0-9][A-Za-z0-9._-]*")).getOrElse(throw new IllegalArgumentException(s"invalid $name symbol")))
        val font = fields("font").map(_.asString.getOrElse(throw new IllegalArgumentException(s"invalid $name font"))).getOrElse("inherit") match {
          case "inherit" => TermPresentation.Font.Inherit
          case "serif" => TermPresentation.Font.Serif
          case "sans-serif" => TermPresentation.Font.SansSerif
          case "monospace" => TermPresentation.Font.Monospace
          case value => throw new IllegalArgumentException(s"invalid font: $value")
        }
        val color = fields("color").map(_.asString.filter(_.matches("#[0-9a-fA-F]{6}")).map { value =>
          TermPresentation.Color(Integer.parseInt(value.substring(1, 3), 16), Integer.parseInt(value.substring(3, 5), 16), Integer.parseInt(value.substring(5, 7), 16))
        }.getOrElse(throw new IllegalArgumentException(s"invalid $name color")))
        RoleStyle(symbol, Style(font, color))
    }
    val ordinary = _style_("ordinary")
    val named = _style_("named")
    if (ordinary.symbol.nonEmpty && ordinary.symbol == named.symbol && ordinary.style != named.style)
      throw new IllegalArgumentException("a style symbol cannot designate conflicting semantic role styles")
    val pdf = root("pdf").map(_.asObject.getOrElse(throw new IllegalArgumentException("pdf must be an object"))).getOrElse(io.circe.JsonObject.empty)
    if (!pdf.keys.toSet.subsetOf(Set("latexEngine", "dependencyMode", "docker", "dockerImage")))
      throw new IllegalArgumentException("unknown PDF setting")
    def _pdf_string_(name: String): Option[String] = pdf(name).map(_.asString.filter(value => value.nonEmpty && value == value.trim)
      .getOrElse(throw new IllegalArgumentException(s"invalid PDF $name")))
    val engine = _pdf_string_("latexEngine").getOrElse("lualatex")
    val mode = _pdf_string_("dependencyMode").getOrElse("auto")
    if (!Set("lualatex", "uplatex").contains(engine) || !Set("auto", "local", "docker").contains(mode))
      throw new IllegalArgumentException("unsupported PDF engine or dependency mode")
    Configuration(profile, enabled, ordinary, named, base, PdfSettings(engine, mode, _pdf_string_("docker"), _pdf_string_("dockerImage")))
  }

  def assemble(validated: CozyDocumentDescriptionV2.ValidatedDocument, configuration: Configuration, vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary): RenderInput = {
    import CozyDocumentDescriptionV2.{InlineCode, InlineTerm, InlineText, ListBlock, LogicalStructure, Note, Example}
    val description = validated.description
    val terms = validated.resolvedTerms.map { term =>
      val expected = if (term.binding.role == "named-pattern") configuration.named.symbol else configuration.ordinary.symbol
      if (term.binding.styleRole.exists(value => !expected.contains(value)))
        throw new IllegalArgumentException(s"unsupported or conflicting styleRole: ${term.binding.styleRole.get}")
      (term.blockId, term.itemId, term.inlineIndex) -> occurrence(term, description.locale, configuration.base)
    }
    if (terms.map(_._1).distinct.size != terms.size)
      throw new IllegalArgumentException("duplicate resolved term occurrence")
    val indexed = terms.toMap
    def _inline_(block: String, item: Option[String], text: String, values: Vector[CozyDocumentDescriptionV2.Inline]): List[org.smartdox.Inline] =
      if (values.isEmpty) List(Text(text)) else values.zipWithIndex.map {
        case (InlineText(value), _) => Text(value)
        case (InlineCode(value), _) => Code(Text(value))
        case (_: InlineTerm, index) => TermPresentation.marker(indexed.getOrElse((block, item, index),
          throw new IllegalArgumentException(s"unresolved term occurrence: $block/$index")))
      }.toList
    def _section_(section: CozyDocumentDescriptionV2.Section): Section = {
      val blocks: Vector[Dox] = section.blocks.map {
        case paragraph: CozyDocumentDescriptionV2.Paragraph =>
          Paragraph(_inline_(paragraph.id, None, paragraph.text, paragraph.inline))
        case list: ListBlock =>
          Ul(list.items.map(item => Li(_inline_(list.id, Some(item.id), item.text, item.inline))).toList)
        case example: Example => Section(example.title, Vector(Paragraph.text(example.text)))
        case note: Note => Section(note.title, Vector(Paragraph.text(note.text)))
        case figure: CozyDocumentDescriptionV2.Figure =>
          Figure(ReferenceImg(new URI(figure.asset), Some(figure.alt)), org.smartdox.Figcaption(figure.caption), Some(figure.id))
        case table: CozyDocumentDescriptionV2.Table =>
          val head = THead.create(table.columns.map(_.heading))
          val body = TBody(table.rows.map(row => TR(row.cells.map(TD(_)).toList)).toList)
          Table(head, body).copy(caption = Some(Caption(table.caption)), label = Some(table.id))
        case logical: LogicalStructure =>
          val step = validated.core.stepsById(logical.stepRef)
          val labels = description.labels.nodes.map(value => value.nodeRef -> value.text).toMap
          def _label_(id: String): String = labels.getOrElse(id, throw new IllegalArgumentException(s"missing node label: $id"))
          val nodes = step.structure.nodes.map(node => TR(List(TD(_label_(node.id)), TD(vocabulary.nodeRoles(node.role)), TD(""))))
          val relations = step.structure.relations.map(relation => TR(List(TD(_label_(relation.from)), TD(vocabulary.relationTypes(relation.relationType)), TD(_label_(relation.to)))))
          val head = THead.create(Vector(vocabulary.chrome.nodesHeading, vocabulary.chrome.nodeRoleHeading, vocabulary.chrome.relationsHeading))
          Table(head, TBody((nodes ++ relations).toList)).copy(label = Some(logical.id))
      }
      Section(section.heading, blocks ++ section.sections.map(_section_))
    }
    val document = Document(Head.title(Text(description.document.title)), Body(description.document.sections.map(_section_).toList))
    val options = Options(configuration.profile, configuration.editorEnabled, terms.map(_._2), configuration.ordinary.style, configuration.named.style)
    TermPresentation.validate(document, options)
    RenderInput(document, options)
  }

  def occurrence(term: CozyDocumentDescriptionV2.ResolvedTerm, locale: String, base: Option[URI]): Occurrence = {
    val authored = Label(term.visibleText, locale)
    def _label_(profile: String): Option[Label] =
      term.profiles.find(_._1 == profile).flatMap(_._2.selection).map { value =>
        Label(value.label, value.language.getOrElse(locale))
      }
    val location = new URI(term.defaultSelection.link)
    val target = if (location.isAbsolute) location else base.getOrElse(
      throw new IllegalArgumentException("relative glossary link requires an HTTP(S) base URI")).resolve(location)
    if (!target.isAbsolute || !Set("http", "https").contains(Option(target.getScheme).getOrElse("").toLowerCase(java.util.Locale.ROOT)) ||
        Option(target.getHost).forall(_.isEmpty) || target.getUserInfo != null)
      throw new IllegalArgumentException("glossary link must resolve to an HTTP(S) URI with a host and no user information")
    val id = "term-" + _encoded(term.blockId) + "-" + term.itemId.map(_encoded).getOrElse("none") + "-" + term.inlineIndex
    val role = term.binding.role match {
      case "ordinary-term" => Role.Ordinary
      case "named-pattern" => Role.Named
      case value => throw new IllegalArgumentException(s"unsupported term role: $value")
    }
    val emphasis = term.binding.emphasis match {
      case None => Emphasis.Normal
      case Some("bold") => Emphasis.Bold
      case Some(value) => throw new IllegalArgumentException(s"unsupported term emphasis: $value")
    }
    Occurrence(id, term.binding.term, term.binding.concept, Some(target), authored,
      _label_("original"), _label_("established-japanese"), _label_("japanese"), role, emphasis)
  }

  private def _encoded(value: String): String =
    value.getBytes(StandardCharsets.UTF_8).map(byte => f"${byte & 0xff}%02x").mkString
}
