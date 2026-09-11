package cozy.document

import java.nio.file.Path

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectExport {
  private val _schema = "cozy.document-project.v2"
  private val _article_review_work_product = "article-review-html"
  private val _article_review_role = "article-review"
  private val _article_review_media_type = "text/html"
  private val _article_review_public_path = "work-products/article-review-html/article-review.html"

  def metadata(project: Path, descriptor: CozyDocumentProject.Descriptor, target: String): String = {
    val attempt = CozyDocumentProjectEvidence.currentAcceptedNativeAttempt(project, descriptor, _article_review_work_product)
    val output = attempt.outputs.headOption.getOrElse(
      CozyDocumentProject._failure("DP-OP-001", "export requires accepted article-review-html output")
    )
    if (output.identity != _article_review_work_product || output.mediaType != _article_review_media_type)
      CozyDocumentProject._failure("DP-OP-001", "export accepted output is outside the article review contract")
    Vector(
      "Cozy Document Project Export",
      s"schema: ${_schema}",
      s"project: ${descriptor.id}",
      s"target: $target",
      "work-products:",
      s"  - role: ${_article_review_role}",
      s"    mediaType: ${_article_review_media_type}",
      s"    path: ${_article_review_public_path}",
      s"    sha256: ${output.sha256}"
    ).mkString("\n")
  }
}
