package cozy.document

import java.nio.file.Path

/*
 * @since   Sep. 10, 2026
 * @version Sep. 10, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectExecutability {
  private val _attempt_v2_schema = "cozy.document-operation-attempt.v2"

  private[cozy] def nativeOperationStateLine(state: CozyDocumentWorkflow.NativeOperationState): String =
    s"native-operation ${state.operation.id} [logical-selection: ${state.logicalSelection.value}; prerequisite-readiness: ${state.prerequisiteReadiness.value}; provider-availability: ${state.providerAvailability.value}; accepted-output-currentness: ${state.acceptedOutputCurrentness.value}; immediate-executability: ${state.immediateExecutability.value}; presentation-semantics: ${state.presentationSemanticsState}; reason: ${state.reason}]"

  private[cozy] def nativeOperationStateLines(value: CozyDocumentProjectEvidence.Snapshot): Vector[String] =
    value.nativeOperations.map(nativeOperationStateLine)

  private[cozy] def nativeOperationStates(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    resolved: CozyDocumentWorkflow.ResolvedWorkflow,
    sources: Vector[CozyDocumentProjectEvidence.FileIdentity],
    products: Vector[CozyDocumentProjectEvidence.WorkProductState],
    attempts: Vector[CozyDocumentProjectEvidence.Attempt],
    presentationstate: CozyDocumentProjectPresentationSemanticsState.State
  ): Vector[CozyDocumentWorkflow.NativeOperationState] = {
    val sourcepaths = sources.map(_.path).toSet
    val productsbyid = products.map(value => value.value.workProduct.id -> value).toMap
    resolved.definition.operations.map { operation =>
      val outputworkproductid = operation.produces.headOption.getOrElse(
        CozyDocumentProject._descriptor_failure(s"logical operation has no declared output Work Product: ${operation.id}")
      )
      val outputworkproduct = resolved.workProducts.find(_.workProduct.id == outputworkproductid).getOrElse(
        CozyDocumentProject._descriptor_failure(s"logical operation output Work Product is missing: ${operation.id}/$outputworkproductid")
      )
      val nativeproviderdeclaration = CozyDocumentWorkflow.nativeProviderDeclaration(operation.id) match {
        case Right(value) => value
        case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
      }
      val nativeoutput = nativeproviderdeclaration.flatMap(_.outputs.headOption)
      val logicalselection =
        if (outputworkproduct.isParticipating) CozyDocumentWorkflow.NativeLogicalSelection.Selected
        else if (outputworkproduct.selection == CozyDocumentWorkflow.WorkProductSelection.InactiveOptional) CozyDocumentWorkflow.NativeLogicalSelection.NotSelected
        else CozyDocumentWorkflow.NativeLogicalSelection.ProfileDisabled
      val prerequisitereadiness = nativeoutput match {
        case Some(_) if CozyDocumentProjectNativeEvidence.nativeInputPaths(descriptor, operation).forall(sourcepaths.contains) => CozyDocumentWorkflow.NativePrerequisiteReadiness.Ready
        case Some(_) => CozyDocumentWorkflow.NativePrerequisiteReadiness.Missing
        case None if operation.consumes.forall(id => productsbyid.get(id).exists(_.readiness == "ready")) => CozyDocumentWorkflow.NativePrerequisiteReadiness.Ready
        case None => CozyDocumentWorkflow.NativePrerequisiteReadiness.Missing
      }
      val provideravailability = nativeproviderdeclaration match {
        case Some(declaration) if CozyDocumentProjectProvider.isAvailable(operation, declaration) => CozyDocumentWorkflow.NativeProviderAvailability.Available
        case _ => CozyDocumentWorkflow.NativeProviderAvailability.Unavailable
      }
      val acceptedoutputcurrentness = nativeoutput match {
        case Some(_) =>
          val acceptedattempts = attempts.filter(attempt => attempt.schema == _attempt_v2_schema && attempt.operation == operation.id && attempt.outcome == "accepted")
          if (acceptedattempts.exists(CozyDocumentProjectNativeEvidence.nativeAttemptCurrent(project, _))) CozyDocumentWorkflow.NativeAcceptedOutputCurrentness.Current
          else if (acceptedattempts.nonEmpty) CozyDocumentWorkflow.NativeAcceptedOutputCurrentness.Stale
          else CozyDocumentWorkflow.NativeAcceptedOutputCurrentness.Missing
        case None => productsbyid.get(outputworkproductid).map(_.currentness) match {
          case Some("current") => CozyDocumentWorkflow.NativeAcceptedOutputCurrentness.Current
          case Some("stale") => CozyDocumentWorkflow.NativeAcceptedOutputCurrentness.Stale
          case _ => CozyDocumentWorkflow.NativeAcceptedOutputCurrentness.Missing
        }
      }
      val immediateexecutability =
        if (logicalselection == CozyDocumentWorkflow.NativeLogicalSelection.Selected &&
            prerequisitereadiness == CozyDocumentWorkflow.NativePrerequisiteReadiness.Ready &&
            provideravailability == CozyDocumentWorkflow.NativeProviderAvailability.Available)
          CozyDocumentWorkflow.NativeImmediateExecutability.Executable
        else
          CozyDocumentWorkflow.NativeImmediateExecutability.Blocked
      val reason =
        if (logicalselection != CozyDocumentWorkflow.NativeLogicalSelection.Selected)
          "native output Work Product is not selected"
        else if (prerequisitereadiness != CozyDocumentWorkflow.NativePrerequisiteReadiness.Ready)
          "one or more direct native prerequisites are missing or unsafe"
        else if (provideravailability != CozyDocumentWorkflow.NativeProviderAvailability.Available)
          "native provider capability is unavailable"
        else
          "native operation is immediately executable"
      CozyDocumentWorkflow.NativeOperationState(
        operation,
        outputworkproductid,
        nativeoutput,
        logicalselection,
        prerequisitereadiness,
        provideravailability,
        acceptedoutputcurrentness,
        immediateexecutability,
        presentationstate.semanticState,
        reason
      )
    }
  }

  private[cozy] def stateYaml(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    value: CozyDocumentProjectEvidence.Snapshot
  ): String = {
    val sourceyaml = value.sources.map(identity => s"  - path: ${identity.path}\n    sha256: ${identity.sha256}")
    val evidenceyaml = value.sidecar match {
      case Some(sidecar) =>
        Vector(
          "  sidecar:",
          s"    path: ${sidecar.path.path}",
          s"    sha256: ${sidecar.path.sha256}",
          "  attempts:"
        ) ++ _attempt_yaml(value.attempts)
      case None => Vector("  sidecar: none", "  attempts:") ++ _attempt_yaml(value.attempts)
    }
    val productyaml = value.products.map { product =>
      val workproduct = product.value.workProduct
      val binding = product.value.binding
      (Vector(
        s"  - id: ${workproduct.id}",
        s"    role: ${workproduct.role.value}",
        s"    disposition: ${binding.disposition.value}",
        s"    selection: ${product.value.selection.value}",
        s"    criterion: ${workproduct.criteria.head}",
        s"    coverage: ${product.coverage}",
        s"    currentness: ${product.currentness}",
        s"    review: ${product.review}",
        s"    readiness: ${product.readiness}"
      ) ++ product.reason.map(reason => s"    reason: ${_yaml_double_quoted(reason)}").toVector).mkString("\n")
    }
    val criteriayaml = Vector(
      s"  satisfied: ${value.criteria.count(_.coverage == "satisfied")}",
      s"  total: ${value.criteria.count(_.coverage != "not-applicable")}",
      "  missing:"
    ) ++ _criterion_yaml(value.criteria.filter(_.coverage == "missing")) ++ Vector(
      "  notApplicable:"
    ) ++ _criterion_yaml(value.criteria.filter(_.coverage == "not-applicable"))
    val nativeoperationyaml = value.nativeOperations.map { state =>
      Vector(
        s"  - operation: ${state.operation.id}",
        s"    output: ${state.outputWorkProductId}",
        s"    nativeOutput: ${state.nativeOutput.map(_.identity).getOrElse("none")}",
        s"    logicalSelection: ${state.logicalSelection.value}",
        s"    prerequisiteReadiness: ${state.prerequisiteReadiness.value}",
        s"    providerAvailability: ${state.providerAvailability.value}",
        s"    acceptedOutputCurrentness: ${state.acceptedOutputCurrentness.value}",
        s"    immediateExecutability: ${state.immediateExecutability.value}",
        s"    presentationSemantics: ${state.presentationSemanticsState}",
        s"    reason: ${_yaml_double_quoted(state.reason)}"
      ).mkString("\n")
    }
    (Vector(
      "schema: cozy.document-project-state.v2",
      s"project: ${descriptor.id}",
      s"profile: ${descriptor.profile}",
      s"workspace: ${descriptor.workspace}",
      "sources:"
    ) ++ sourceyaml ++ Vector("evidence:") ++ evidenceyaml ++ Vector("criteria:") ++ criteriayaml ++ Vector("workProducts:") ++ productyaml ++ Vector("nativeOperations:") ++ nativeoperationyaml).mkString("\n") + "\n"
  }

  private def _criterion_yaml(criteria: Vector[CozyDocumentProjectEvidence.CriterionState]): Vector[String] =
    if (criteria.isEmpty) Vector("    []")
    else criteria.flatMap { criterion =>
      Vector(
        s"    - id: ${criterion.id}",
        s"      reason: ${_yaml_double_quoted(criterion.reason)}"
      )
    }

  private def _attempt_yaml(attempts: Vector[CozyDocumentProjectEvidence.Attempt]): Vector[String] =
    if (attempts.isEmpty) {
      Vector("    []")
    } else {
      attempts.flatMap { attempt =>
        Vector(
          s"    - path: ${attempt.path.path}",
          s"      sha256: ${attempt.path.sha256}",
          s"      outcome: ${attempt.outcome}"
        )
      }
    }

  private def _yaml_double_quoted(value: String): String = {
    val builder = new StringBuilder("\"")
    value.foreach {
      case '\\' => builder.append("\\\\")
      case '"' => builder.append("\\\"")
      case '\r' => builder.append("\\r")
      case '\n' => builder.append("\\n")
      case '\t' => builder.append("\\t")
      case character if Character.isISOControl(character) => builder.append(f"\\u${character.toInt}%04x")
      case character => builder.append(character)
    }
    builder.append('"').result()
  }
}
