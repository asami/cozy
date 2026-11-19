package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.net.URI
import org.smartdox.{Dox, Img, I18NFragment}
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.{Dox2DomHtmlTransformer, Dox2HtmlTransformer, HtmlTransformerBase, LanguageFilterTransformer}
import org.goldenport.tree.{TreeNode, TreeTransformer}
import org.w3c.dom.{Element, Node}

/*
 * @since   Sep. 14, 2026
 * @version Oct. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectSmartDoxArticleHtml extends HtmlTransformerBase {
  final case class Prepared(
    sourcePath: Path,
    document: Dox,
    assets: CozyDocumentFigureAssets.Materialized
  )

  private[cozy] final case class Result(
    rendererIdentity: String,
    sourcePath: Path,
    html: String,
    assets: Vector[Path]
  )

  private val _renderer_identity = "smartdox-2.4.19-SNAPSHOT"
  private val _rule = Dox2HtmlTransformer.Rule.default

  def isPretty: Boolean = _rule.isPretty
  def isDocument: Boolean = _rule.isDocument

  def prepare(project: Path): Prepared = {
    val admittedproject = _admit_project(project)
    val sourcepath = CozyDocumentProject._direct_file(admittedproject, "index.dox", "SmartDox article source")
    val content = Files.readString(sourcepath, StandardCharsets.UTF_8)
    val document = Dox2Parser.parseWithFilename(Dox2Parser.Config.default, "index.dox", content)
    val references = _image_references(document).map(_relative_reference).distinct
    val assets = CozyDocumentFigureAssets.materialize(admittedproject.toRealPath(), references)
    Prepared(sourcepath, document, assets)
  }

  def articleImageReferences(project: Path): Vector[String] = {
    val admitted = _admit_project(project)
    val source = CozyDocumentProject._direct_file(admitted, "index.dox", "SmartDox article source")
    val document = Dox2Parser.parseWithFilename(Dox2Parser.Config.default, "index.dox", Files.readString(source, StandardCharsets.UTF_8))
    _image_references(document).map(_relative_reference).distinct
  }

  def render(project: Path): Result = render(prepare(project))

  def render(prepared: Prepared, locale: Option[String] = None): Result = {
    locale.foreach { selected =>
      if (!Set("ja", "en").contains(selected)) CozyDocumentProject._failure("DP-OP-001", s"unsupported article locale: $selected")
      val languages = _languages(prepared.document).distinct
      val supported = if (languages.nonEmpty) languages.contains(selected) else {
        val project = prepared.sourcePath.getParent
        CozyDocumentProject._load_project(project).language == selected
      }
      if (!supported) CozyDocumentProject._failure("DP-OP-001", s"article does not declare selected locale: $selected")
    }
    val references = _image_references(prepared.document).flatMap { uri =>
      val relative = _relative_reference(uri)
      Vector(uri.toString -> relative, uri.toASCIIString -> relative)
    }.toMap
    val context = locale.map(value => SmartDoxContext.create().withTargetI18NContext(java.util.Locale.forLanguageTag(value))).getOrElse(SmartDoxContext.create())
    val document = locale.map { selected =>
      val treecontext = context.doxContext.withI18NContext(context.targetI18NContext)
      val filter = new LanguageFilterTransformer(treecontext) {
        override protected def make_Node(node: TreeNode[Dox], content: Dox): TreeTransformer.Directive[Dox] =
          if (content.attributes.get("lang").exists(value => java.util.Locale.forLanguageTag(value).getLanguage != selected))
            directive_empty
          else
            super.make_Node(node, content)
      }
      Dox.transform(prepared.document, filter)
    }.getOrElse(prepared.document)
    val dom = new Dox2DomHtmlTransformer(context, _rule.toDox2DomHtmlTransformerRule)
      .transformG(document).take

    def _attach_(node: Node): Unit = {
      node match {
        case image: Element if image.getTagName.equalsIgnoreCase("img") =>
          val source = image.getAttribute("src")
          val relative = references.getOrElse(source, throw CozyDocumentFigureAssets.FigureAssetFault(
            "FIGURE_ASSET_PATH", source, "native image has no admitted article reference"))
          image.setAttribute("src", prepared.assets.dataUri(relative))
        case _ => ()
      }
      val children = node.getChildNodes
      (0 until children.getLength).foreach(index => _attach_(children.item(index)))
    }

    _attach_(dom)
    Result(_renderer_identity, prepared.sourcePath, to_html(dom), prepared.assets.sourcePaths)
  }

  private def _languages(value: Dox): Vector[String] = {
    val attribute = value.attributes.get("lang").toVector.map(value => java.util.Locale.forLanguageTag(value).getLanguage)
    val fragment = value match {
      case item: I18NFragment => Vector("ja", "en").filter(value => item.hasExactSource(java.util.Locale.forLanguageTag(value)))
      case _ => Vector.empty
    }
    attribute ++ fragment ++ value.elements.toVector.flatMap(_languages)
  }

  private def _image_references(value: Dox): Vector[URI] = {
    val selected = value match {
      case image: Img => Vector(image.src)
      case _ => Vector.empty
    }
    selected ++ value.elements.toVector.flatMap(_image_references)
  }

  private def _relative_reference(uri: URI): String = {
    val path = Option(uri.getPath).getOrElse("")
    if (uri.isAbsolute || uri.getRawAuthority != null || uri.getRawQuery != null || uri.getRawFragment != null ||
      path.isEmpty || path.startsWith("/"))
      throw CozyDocumentFigureAssets.FigureAssetFault(
        "FIGURE_ASSET_PATH", uri.toString, "article images must select a nonempty relative local URI without authority, query or fragment")
    path
  }

  private def _admit_project(project: Path): Path = {
    val admittedproject = project.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(admittedproject) || !Files.isDirectory(admittedproject, LinkOption.NOFOLLOW_LINKS))
      CozyDocumentProject._failure("DP-PATH-001", "project must be an existing direct non-symlink directory")
    admittedproject
  }
}
