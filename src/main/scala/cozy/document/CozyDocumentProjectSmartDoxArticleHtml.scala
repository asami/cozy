package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectSmartDoxArticleHtml {
  private[cozy] final case class Result(
    rendererIdentity: String,
    sourcePath: Path,
    html: String,
    assets: Vector[Path]
  )

  private val _renderer_identity = "smartdox-2.4.19-SNAPSHOT"

  def render(project: Path): Result = {
    val admittedproject = _admit_project(project)
    val sourcepath = CozyDocumentProject._direct_file(admittedproject, "index.dox", "SmartDox article source")
    val content = Files.readString(sourcepath, StandardCharsets.UTF_8)
    val document = Dox2Parser.parseWithFilename(Dox2Parser.Config.default, "index.dox", content)
    val html = Dox2HtmlTransformer(SmartDoxContext.create(), Dox2HtmlTransformer.Rule.default).transform(document).take
    Result(_renderer_identity, sourcepath, html, Vector.empty)
  }

  private def _admit_project(project: Path): Path = {
    val admittedproject = project.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(admittedproject) || !Files.isDirectory(admittedproject, LinkOption.NOFOLLOW_LINKS))
      CozyDocumentProject._failure("DP-PATH-001", "project must be an existing direct non-symlink directory")
    admittedproject
  }
}
