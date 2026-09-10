package cozy.document

import java.nio.file.Path

/*
 * @since   Sep. 10, 2026
 * @version Sep. 10, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectVerification {
  private[cozy] def verify(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    snapshot: CozyDocumentProjectEvidence.Snapshot,
    verificationpolicy: CozyDocumentWorkflow.VerificationPolicy,
    workproduct: Option[String]
  ): String = verificationpolicy match {
    case CozyDocumentWorkflow.VerificationPolicy.Structural =>
      if (CozyDocumentWorkflow.isVideoProfile(descriptor.profile))
        CozyDocumentProject._direct_file(project, "video/storyboard.md", "initial authored source")
      val semantics = CozyDocumentProjectPresentationSemanticsState.requireCurrent(project, descriptor)
      (Vector(
        "Cozy Document Project Verify",
        s"project: ${descriptor.id}",
        s"package: $project",
        "schema: cozy.document-project.v2",
        "mode: structural",
        CozyDocumentProjectPresentationSemanticsState.summary(semantics)
      ) ++ CozyDocumentProjectExecutability.nativeOperationStateLines(snapshot)).mkString("\n")
    case CozyDocumentWorkflow.VerificationPolicy.Visual =>
      _visual_verify(project, descriptor, snapshot, workproduct.getOrElse(CozyDocumentProject._failure("DP-CLI-002", "visual verify requires --work-product <native-output-work-product>")))
  }

  private[cozy] def plan(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    val workflowplan = CozyDocumentWorkflow.plan(descriptor.profile, descriptor.activeOptionalWorkProducts) match {
      case Right(value) => value
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    val presentationstate = CozyDocumentProjectPresentationSemanticsState.derive(project, descriptor)
    val requiredlines = workflowplan.selectedWorkProducts.collect {
      case value if value.selection == CozyDocumentWorkflow.WorkProductSelection.Required => s"required: ${_plan_work_product_line(value, presentationstate)}"
    }
    val activeoptionallines = workflowplan.selectedWorkProducts.collect {
      case value if value.selection == CozyDocumentWorkflow.WorkProductSelection.ActiveOptional => s"active-optional: ${_plan_work_product_line(value, presentationstate)}"
    }
    val inactiveoptionallines = workflowplan.inactiveOptionalWorkProducts.map(value => s"inactive-optional: ${_plan_work_product_line(value, presentationstate)}")
    val profiledisabledlines = workflowplan.profileDisabledWorkProducts.map(value => s"profile-disabled: ${_plan_work_product_line(value, presentationstate)}")
    val blockedlines = workflowplan.blockedOperations.map(value => s"blocked: operation ${value.id} [${CozyDocumentWorkflow.executionReservedExplanation}]")
    val eligiblelines = workflowplan.eligibleOperations.map(value => s"eligible: operation ${value.id} [provider: ${value.providerBinding}]")
    val semanticaction = Vector(presentationstate).collect {
      case value if Set("blocked", "failed").contains(CozyDocumentProjectPresentationSemanticsState.evidenceReadiness(value)) =>
        s"next-action: presentation-semantics [${CozyDocumentProject.presentationSemanticsAuthoringInstruction(descriptor)}]"
    }
    (Vector(
      "Cozy Document Project Plan",
      s"project: ${descriptor.id}",
      s"package: $project",
      "schema: cozy.document-project.v2"
    ) ++ requiredlines ++ activeoptionallines ++ inactiveoptionallines ++ profiledisabledlines ++ semanticaction ++ blockedlines ++ eligiblelines ++ CozyDocumentProjectEvidence.planNativeOperationStateLines(project, descriptor)).mkString("\n")
  }

  private def _visual_verify(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    snapshot: CozyDocumentProjectEvidence.Snapshot,
    workproduct: String
  ): String = {
    val state = snapshot.nativeOperations.find(_.outputWorkProductId == workproduct).getOrElse(
      CozyDocumentProject._failure("DP-OP-001", s"visual verify Work Product is not a declared native output: $workproduct")
    )
    if (state.logicalSelection != CozyDocumentWorkflow.NativeLogicalSelection.Selected)
      CozyDocumentProject._failure("DP-OP-001", s"visual verify Work Product is not selected for profile ${descriptor.profile}: $workproduct")
    val nativeoutput = state.nativeOutput.getOrElse(CozyDocumentProject._failure("DP-OP-001", s"visual verify Work Product is not a declared native output: $workproduct"))
    if (state.acceptedOutputCurrentness != CozyDocumentWorkflow.NativeAcceptedOutputCurrentness.Current)
      CozyDocumentProject._failure("DP-OP-001", s"visual verify requires current accepted native output evidence: $workproduct")
    val output = CozyDocumentProject._direct_file(project, nativeoutput.path, "current accepted native output")
    val destination = CozyDocumentProjectProjection.admitVisualVerificationDestination(project, nativeoutput)
    CozyDocumentProjectProjection.publish(destination, CozyDocumentProjectProjection.visualVerificationHtml(project, descriptor, nativeoutput, output, destination))
    (Vector(
      "Cozy Document Project Verify",
      s"project: ${descriptor.id}",
      s"package: $project",
      "schema: cozy.document-project.v2",
      "mode: visual",
      s"work-product: $workproduct",
      s"temporary-output: ${CozyDocumentProject._project_relative(project, destination)}"
    ) ++ CozyDocumentProjectExecutability.nativeOperationStateLines(snapshot)).mkString("\n")
  }

  private def _plan_work_product_line(
    value: CozyDocumentWorkflow.ResolvedWorkProduct,
    presentationstate: CozyDocumentProjectPresentationSemanticsState.State
  ): String = {
    val product = value.workProduct
    val binding = value.binding
    val selection = value.selection match {
      case CozyDocumentWorkflow.WorkProductSelection.ActiveOptional => "; selected"
      case CozyDocumentWorkflow.WorkProductSelection.InactiveOptional => "; not-selected"
      case _ => ""
    }
    val reason = binding.reason.map(text => s": $text").getOrElse("")
    val state = if (product.id == "presentation-semantics")
      s"; state: ${presentationstate.semanticState}; coverage: ${CozyDocumentProjectPresentationSemanticsState.evidenceCoverage(presentationstate)}; currentness: ${CozyDocumentProjectPresentationSemanticsState.evidenceCurrentness(presentationstate)}; readiness: ${CozyDocumentProjectPresentationSemanticsState.evidenceReadiness(presentationstate)}; reason: ${presentationstate.reason}"
    else
      ""
    s"work-product ${product.id} [${product.role.value}, ${binding.disposition.value}$selection$reason]$state"
  }
}
