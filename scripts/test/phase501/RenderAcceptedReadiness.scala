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
    val decision = TargetReadyAcceptanceDecision((document \ "decisionId").as[String],
      (document \ "reviewerId").as[String])
    val accepted = _right(CozyLogicalUiReadiness.accept(candidate, decision))
    val structural = _right(CozyLogicalUiReadinessReview.renderAccepted(accepted, scenario))
    val web = _right(CozyLogicalUiReadinessWeb.renderAccepted(accepted, scenario))
    val receipt = Json.parse(structural.receiptJson)
    val declarations = Json.obj("binding" -> (receipt \ "binding").get,
      "sourceInputs" -> (receipt \ "sourceInputs").get)
    val siteparent = Files.createDirectories(outputparent.resolve("site"))
    val evidenceparent = Files.createDirectories(outputparent.resolve("evidence"))
    _right(CozyLogicalUiReadinessWeb.write(web, siteparent, evidenceparent))
    Files.write(outputparent.resolve("accepted-logical-ui.json"),
      (Json.prettyPrint(declarations) + "\n").getBytes(StandardCharsets.UTF_8))
    println(Json.stringify(Json.obj("decisionId" -> accepted.decision.decisionId,
      "reviewerId" -> accepted.decision.reviewerId,
      "inputRoles" -> Vector("kernelContent", "projectionContent", "semanticContent", "readinessContent"))))
  }

  private def _right[A](result: Either[LogicalUiError, A]): A = result match {
    case Right(value) => value
    case Left(error) => throw new IllegalStateException(s"${error.code}: ${error.path}: ${error.reason}")
  }
}
