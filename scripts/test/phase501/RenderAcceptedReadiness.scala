package cozy.ui

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import cozy.ui.CozyLogicalUi.LogicalUiError
import cozy.ui.CozyLogicalUiReadiness.TargetReadyAcceptanceDecision
import play.api.libs.json._

/*
 * @since   Oct. 13, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object RenderAcceptedReadiness {
  def main(args: Array[String]): Unit = {
    require(args.length == 2, "Exactly acceptance.json and output parent required")
    val acceptancepath = Paths.get(args(0))
    val outputparent = Paths.get(args(1))
    require(acceptancepath.isAbsolute && outputparent.isAbsolute, "Absolute arguments required")
    val document = Json.parse(new String(Files.readAllBytes(acceptancepath), StandardCharsets.UTF_8))
    val candidate = _right(CozyLogicalUiSalesOrderFixtures.readyCandidate())
    val scenario = CozyLogicalUiSalesOrderFixtures.readinessWebScenario()
    require((document \ "candidateIdentity").as[String] == candidate.identity,
      "Human decision must name the exact complete candidate")
    require((document \ "consumedInputIdentity").as[String] == candidate.inputIdentity,
      "Human decision must name the exact complete consumed input")
    _right(CozyLogicalUiReadiness.verifyCurrent(candidate, candidate.kernelCandidate,
      candidate.projection, candidate.semantics, candidate.input))
    val decision = TargetReadyAcceptanceDecision((document \ "decisionId").as[String],
      (document \ "candidateIdentity").as[String], (document \ "reviewerId").as[String])
    val accepted = _right(CozyLogicalUiReadiness.accept(candidate, decision))
    val structural = _right(CozyLogicalUiReadinessReview.renderAccepted(accepted, scenario))
    val web = _right(CozyLogicalUiReadinessWeb.renderAccepted(accepted, scenario))
    _right(CozyLogicalUiReadinessReview.verifyAcceptedCurrent(accepted, scenario, structural))
    _right(CozyLogicalUiReadinessWeb.verifyAcceptedCurrent(accepted, scenario, web))
    require(structural.receipt.binding == web.receipt.reviewReceipt.binding &&
      structural.receipt.sourceInputs == web.receipt.reviewReceipt.sourceInputs &&
      structural.receiptJson == web.reviewReceiptJson, "Both complete projections must agree")
    val rerender = _right(CozyLogicalUiReadinessWeb.renderAccepted(accepted, scenario))
    require(web.html == rerender.html && web.reviewHtml == rerender.reviewHtml &&
      web.compareHtml == rerender.compareHtml && web.receipt == rerender.receipt &&
      web.receiptJson == rerender.receiptJson && web.reviewReceiptJson == rerender.reviewReceiptJson &&
      web.diagnostics == rerender.diagnostics, "Accepted Web observables must be deterministic")
    val receipt = Json.parse(structural.receiptJson)
    val declarations = Json.obj("binding" -> (receipt \ "binding").get,
      "sourceInputs" -> (receipt \ "sourceInputs").get)
    val siteparent = Files.createDirectories(outputparent.resolve("site"))
    val evidenceparent = Files.createDirectories(outputparent.resolve("evidence"))
    _right(CozyLogicalUiReadinessWeb.write(web, siteparent, evidenceparent))
    Files.write(outputparent.resolve("accepted-logical-ui.json"),
      (Json.prettyPrint(declarations) + "\n").getBytes(StandardCharsets.UTF_8))
    println(Json.stringify(Json.obj("candidateIdentity" -> candidate.identity,
      "consumedInputIdentity" -> candidate.inputIdentity, "acceptedIdentity" -> accepted.identity,
      "decisionIdentity" -> accepted.decision.decisionIdentity, "decisionId" -> accepted.decision.decisionId,
      "reviewerId" -> accepted.decision.reviewerId,
      "inputRoles" -> Vector("kernelContent", "projectionContent", "semanticContent", "readinessContent"),
      "candidateCurrent" -> true, "structuralCurrent" -> true, "webCurrent" -> true,
      "deterministic" -> true)))
  }

  private def _right[A](result: Either[LogicalUiError, A]): A = result match {
    case Right(value) => value
    case Left(error) => throw new IllegalStateException(s"${error.code}: ${error.path}: ${error.reason}")
  }
}
