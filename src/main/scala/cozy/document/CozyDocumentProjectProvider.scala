package cozy.document

import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.util.control.NonFatal

/*
 * @since   Sep. 10, 2026
 * @version Sep. 10, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectProvider {
  private[cozy] val NATIVE_RECEIPT_IDENTITY = "cozy.document-project.native-receipt.v1"

  private[cozy] def nativeReceiptValue(operation: String, path: String, mediaType: String, sha256: String): String =
    s"operation=$operation;output=$path;mediaType=$mediaType;sha256=$sha256"

  sealed abstract class ProviderResult

  final case class ProviderOutput(identity: String, path: String, mediaType: String, sha256: String)
  final case class ProviderReceipt(identity: String, value: String)
  final case class Executed(
    outputs: Vector[ProviderOutput],
    diagnostics: Vector[String],
    receipt: ProviderReceipt
  ) extends ProviderResult
  final case class Blocked(
    operation: String,
    binding: String,
    provider: String,
    missingCapability: String,
    diagnostics: Vector[String]
  ) extends ProviderResult
  final case class Failed(
    operation: String,
    binding: String,
    provider: String,
    diagnostics: Vector[String]
  ) extends ProviderResult
  final case class Request(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation,
    declaration: CozyDocumentWorkflow.NativeProviderDeclaration,
    destinations: Vector[Path]
  )

  def unavailable(
    operation: CozyDocumentWorkflow.LogicalOperation,
    binding: CozyDocumentWorkflow.ProviderBinding
  ): Blocked =
    Blocked(
      operation.id,
      binding.id,
      binding.provider,
      "native typed provider execution is unavailable",
      Vector(s"native provider binding ${binding.id} has no Phase 56 implementation")
    )

  def isAvailable(
    operation: CozyDocumentWorkflow.LogicalOperation,
    declaration: CozyDocumentWorkflow.NativeProviderDeclaration
  ): Boolean =
    operation.id == "article.render-review" &&
      operation.providerBinding == "cozy-review-projection" &&
      declaration.operationId == operation.id &&
      declaration.providerBinding == operation.providerBinding &&
      declaration.outputs == Vector(CozyDocumentWorkflow.OutputDeclaration("article-review-html", "target/document-project/article-review.html", "text/html"))

  def execute(request: Request): ProviderResult = {
    val declaration = request.declaration
    if (request.operation.id != declaration.operationId || request.operation.providerBinding != declaration.providerBinding)
      Failed(request.operation.id, request.operation.providerBinding, "unresolved provider", Vector("native provider request does not match its workflow declaration"))
    else if (declaration.outputs.isEmpty || declaration.outputs.size != request.destinations.size)
      Failed(request.operation.id, declaration.providerBinding, "unresolved provider", Vector("native provider requires one admitted destination for every declared output"))
    else if (isAvailable(request.operation, declaration))
      _article_render_review(request)
    else
      Blocked(
        request.operation.id,
        declaration.providerBinding,
        "unresolved provider",
        "native typed provider execution is unavailable",
        Vector(s"native provider declaration is not implemented: ${request.operation.id}/${declaration.providerBinding}")
      )
  }

  private def _article_render_review(request: Request): ProviderResult = {
    val declaration = request.declaration
    val output = declaration.outputs.head
    val destination = request.destinations.head
    if (declaration.outputs.size != 1 || output.identity != "article-review-html" || output.path != "target/document-project/article-review.html" || output.mediaType != "text/html")
      Failed(request.operation.id, declaration.providerBinding, "Cozy review projection adapter", Vector("article review native provider requires its sole declared HTML output"))
    else {
      try {
        val html = CozyDocumentProjectProjection.articleReviewExecutionHtml(request.project, request.descriptor, output.path)
        CozyDocumentProjectProjection.publish(destination, html)
        if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.size(destination) == 0)
          Failed(request.operation.id, declaration.providerBinding, "Cozy review projection adapter", Vector("native provider published no usable output"))
        else {
          val sha256 = _sha256(destination)
          val receipt = ProviderReceipt(
            NATIVE_RECEIPT_IDENTITY,
            nativeReceiptValue(request.operation.id, output.path, output.mediaType, sha256)
          )
          Executed(
            Vector(ProviderOutput(output.identity, CozyDocumentProject._project_relative(request.project, destination), output.mediaType, sha256)),
            Vector("native review projection rendered for validated accepted-evidence closure"),
            receipt
          )
        }
      } catch {
        case NonFatal(error) =>
          Failed(
            request.operation.id,
            declaration.providerBinding,
            "Cozy review projection adapter",
            Vector(s"native provider execution failed: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
          )
      }
    }
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString
}
