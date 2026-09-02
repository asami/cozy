package cozy.document

/*
 * @since   Aug. 31, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentWorkflow {
  sealed abstract class WorkProductRole(val value: String)

  object WorkProductRole {
    case object Authority extends WorkProductRole("authority")
    case object Plan extends WorkProductRole("plan")
    case object Candidate extends WorkProductRole("candidate")
    case object ReviewProjection extends WorkProductRole("review-projection")
    case object SiteDeliverable extends WorkProductRole("site-deliverable")
    case object Deliverable extends WorkProductRole("deliverable")
    case object Receipt extends WorkProductRole("receipt")

    val all: Vector[WorkProductRole] = Vector(Authority, Plan, Candidate, ReviewProjection, SiteDeliverable, Deliverable, Receipt)
  }

  sealed abstract class WorkProductDisposition(val value: String)

  object WorkProductDisposition {
    case object Required extends WorkProductDisposition("required")
    case object Optional extends WorkProductDisposition("optional")
    case object Disabled extends WorkProductDisposition("disabled")

    val all: Vector[WorkProductDisposition] = Vector(Required, Optional, Disabled)
  }

  sealed abstract class WorkProductSelection(val value: String, val isParticipating: Boolean)

  object WorkProductSelection {
    case object Required extends WorkProductSelection("required", true)
    case object ActiveOptional extends WorkProductSelection("active-optional", true)
    case object InactiveOptional extends WorkProductSelection("inactive-optional", false)
    case object ProfileDisabled extends WorkProductSelection("profile-disabled", false)
  }

  final case class Criterion(id: String, description: String)
  final case class Gate(id: String, description: String, criteria: Vector[String])
  final case class EvidenceReference(id: String, description: String)
  final case class ProviderBinding(id: String, provider: String)
  final case class LogicalOperation(
    id: String,
    providerBinding: String,
    consumes: Vector[String],
    produces: Vector[String]
  )
  final case class WorkProduct(
    id: String,
    label: String,
    role: WorkProductRole,
    producer: String,
    consumers: Vector[String],
    criteria: Vector[String],
    dependencies: Vector[String],
    gates: Vector[String],
    evidenceReferences: Vector[String]
  )
  final case class WorkProductBinding(
    workProductId: String,
    disposition: WorkProductDisposition,
    reason: Option[String]
  )
  final case class WorkflowProfile(id: String, bindings: Vector[WorkProductBinding])
  final case class WorkflowDefinition(
    id: String,
    workProducts: Vector[WorkProduct],
    operations: Vector[LogicalOperation],
    criteria: Vector[Criterion],
    gates: Vector[Gate],
    evidenceReferences: Vector[EvidenceReference],
    providerBindings: Vector[ProviderBinding],
    profiles: Vector[WorkflowProfile]
  )
  final case class ResolvedWorkProduct(workProduct: WorkProduct, binding: WorkProductBinding, selection: WorkProductSelection) {
    def isParticipating: Boolean = selection.isParticipating
  }
  final case class ResolvedWorkflow(
    definition: WorkflowDefinition,
    profile: WorkflowProfile,
    workProducts: Vector[ResolvedWorkProduct]
  )
  final case class WorkflowPlan(
    selectedWorkProducts: Vector[ResolvedWorkProduct],
    inactiveOptionalWorkProducts: Vector[ResolvedWorkProduct],
    profileDisabledWorkProducts: Vector[ResolvedWorkProduct],
    blockedOperations: Vector[LogicalOperation],
    eligibleOperations: Vector[LogicalOperation]
  )

  val executionReservedExplanation: String = "execution and Operation Attempts are reserved for Phase 42.1"

  def documentProduction: WorkflowDefinition = _document_production

  def isRegisteredProfile(profileId: String): Boolean =
    _validated_document_production().profiles.exists(_.id == profileId)

  def isVideoProfile(profileId: String): Boolean =
    Set("standard-video", "bok-video", "simplemodeling-org-video").contains(profileId)

  def isHiddenProfile(profileId: String): Boolean =
    Set("simplemodeling-org", "simplemodeling-org-video").contains(profileId)

  def isScaffoldProfile(profileId: String): Boolean =
    Set("standard", "standard-video", "bok", "bok-video").contains(profileId)

  def declaredOperation(operationId: String): Either[String, Option[LogicalOperation]] =
    Right(_validated_document_production().operations.find(_.id == operationId))

  def resolve(profileId: String, activeOptionalWorkProductIds: Vector[String] = Vector.empty): Either[String, ResolvedWorkflow] = {
    val definition = _validated_document_production()
    definition.profiles.find(_.id == profileId) match {
      case Some(profile) =>
        val bindings = profile.bindings.map(binding => binding.workProductId -> binding).toMap
        val selectedids = activeOptionalWorkProductIds.toSet
        if (selectedids.size != activeOptionalWorkProductIds.size) {
          Left("active optional Work Product ids must be duplicate-free")
        } else {
          activeOptionalWorkProductIds.find { id =>
            bindings.get(id).forall(_.disposition != WorkProductDisposition.Optional)
          } match {
            case Some(id) => Left(s"active optional Work Product id is not optional for profile $profileId: $id")
            case None =>
              val products = definition.workProducts.map { product =>
                val binding = bindings(product.id)
                val selection = binding.disposition match {
                  case WorkProductDisposition.Required => WorkProductSelection.Required
                  case WorkProductDisposition.Optional if selectedids.contains(product.id) => WorkProductSelection.ActiveOptional
                  case WorkProductDisposition.Optional => WorkProductSelection.InactiveOptional
                  case WorkProductDisposition.Disabled => WorkProductSelection.ProfileDisabled
                }
                ResolvedWorkProduct(product, binding, selection)
              }
              Right(ResolvedWorkflow(definition, profile, products))
          }
        }
      case None => Left(s"unknown document-production profile: $profileId")
    }
  }

  def plan(profileId: String, activeOptionalWorkProductIds: Vector[String] = Vector.empty): Either[String, WorkflowPlan] =
    resolve(profileId, activeOptionalWorkProductIds) match {
      case Right(resolved) =>
        val selectedproducts = resolved.workProducts.filter(_.isParticipating)
        val inactiveoptionalproducts = resolved.workProducts.filter(_.selection == WorkProductSelection.InactiveOptional)
        val profiledisabledproducts = resolved.workProducts.filter(_.selection == WorkProductSelection.ProfileDisabled)
        val activeids = selectedproducts.map(_.workProduct.id).toSet
        val activeoperations = resolved.definition.operations.filter(_.produces.exists(activeids.contains))
        Right(WorkflowPlan(selectedproducts, inactiveoptionalproducts, profiledisabledproducts, activeoperations, activeoperations))
      case Left(cause) => Left(cause)
    }

  def validate(definition: WorkflowDefinition): Vector[String] = {
    val errors = Vector.newBuilder[String]
    val workproductids = definition.workProducts.map(_.id)
    val operationids = definition.operations.map(_.id)
    val criterionids = definition.criteria.map(_.id)
    val gateids = definition.gates.map(_.id)
    val evidencereferenceids = definition.evidenceReferences.map(_.id)
    val providerbindingids = definition.providerBindings.map(_.id)
    val profileids = definition.profiles.map(_.id)
    val workproductidset = workproductids.toSet
    val operationidset = operationids.toSet
    val criterionidset = criterionids.toSet
    val gateidset = gateids.toSet
    val evidencereferenceidset = evidencereferenceids.toSet
    val providerbindingidset = providerbindingids.toSet

    def _empty_(value: String): Boolean = value.trim.isEmpty

    def _duplicate_ids_(label: String, ids: Vector[String]): Unit =
      ids.groupBy(identity).collect { case (id, values) if values.size > 1 => id }.toVector.sorted.foreach { id =>
        errors += s"duplicate $label id: $id"
      }

    def _unknown_references_(owner: String, label: String, references: Vector[String], known: Set[String]): Unit =
      references.filterNot(known.contains).foreach(reference => errors += s"$owner references unknown $label: $reference")

    if (_empty_(definition.id))
      errors += "workflow id is empty"
    _duplicate_ids_("Work Product", workproductids)
    _duplicate_ids_("logical operation", operationids)
    _duplicate_ids_("criterion", criterionids)
    _duplicate_ids_("gate", gateids)
    _duplicate_ids_("evidence reference", evidencereferenceids)
    _duplicate_ids_("provider binding", providerbindingids)
    _duplicate_ids_("profile", profileids)
    if (profileids.toSet != Set("standard", "standard-video", "bok", "bok-video", "simplemodeling-org", "simplemodeling-org-video"))
      errors += "document-production must resolve the closed standard, bok, and simplemodeling-org profile set"

    definition.criteria.foreach { criterion =>
      if (_empty_(criterion.id) || _empty_(criterion.description))
        errors += s"criterion has empty required metadata: ${criterion.id}"
    }
    definition.gates.foreach { gate =>
      if (_empty_(gate.id) || _empty_(gate.description) || gate.criteria.isEmpty || gate.criteria.exists(_empty_))
        errors += s"gate has empty required metadata: ${gate.id}"
      _unknown_references_(s"gate ${gate.id}", "criterion", gate.criteria, criterionidset)
    }
    definition.evidenceReferences.foreach { reference =>
      if (_empty_(reference.id) || _empty_(reference.description))
        errors += s"evidence reference has empty required metadata: ${reference.id}"
    }
    definition.providerBindings.foreach { binding =>
      if (_empty_(binding.id) || _empty_(binding.provider))
        errors += s"provider binding has empty required metadata: ${binding.id}"
    }
    definition.operations.foreach { operation =>
      if (_empty_(operation.id) || _empty_(operation.providerBinding) || operation.produces.isEmpty || operation.produces.exists(_empty_) || operation.consumes.exists(_empty_))
        errors += s"logical operation has empty required metadata: ${operation.id}"
      _unknown_references_(s"logical operation ${operation.id}", "provider binding", Vector(operation.providerBinding), providerbindingidset)
      _unknown_references_(s"logical operation ${operation.id}", "consumed Work Product", operation.consumes, workproductidset)
      _unknown_references_(s"logical operation ${operation.id}", "produced Work Product", operation.produces, workproductidset)
    }
    definition.workProducts.foreach { product =>
      if (_empty_(product.id) || _empty_(product.label) || _empty_(product.producer) || product.criteria.isEmpty || product.gates.isEmpty || product.evidenceReferences.isEmpty || product.consumers.exists(_empty_) || product.criteria.exists(_empty_) || product.dependencies.exists(_empty_) || product.gates.exists(_empty_) || product.evidenceReferences.exists(_empty_))
        errors += s"Work Product has empty required metadata: ${product.id}"
      if (!WorkProductRole.all.contains(product.role))
        errors += s"Work Product ${product.id} has an unknown role"
      _unknown_references_(s"Work Product ${product.id}", "producer logical operation", Vector(product.producer), operationidset)
      _unknown_references_(s"Work Product ${product.id}", "consumer logical operation", product.consumers, operationidset)
      _unknown_references_(s"Work Product ${product.id}", "criterion", product.criteria, criterionidset)
      _unknown_references_(s"Work Product ${product.id}", "dependency Work Product", product.dependencies, workproductidset)
      _unknown_references_(s"Work Product ${product.id}", "gate", product.gates, gateidset)
      _unknown_references_(s"Work Product ${product.id}", "evidence reference", product.evidenceReferences, evidencereferenceidset)
      definition.operations.find(_.id == product.producer).foreach { operation =>
        if (!operation.produces.contains(product.id))
          errors += s"Work Product ${product.id} producer does not produce the Work Product"
      }
      product.consumers.foreach { consumer =>
        definition.operations.find(_.id == consumer).foreach { operation =>
          if (!operation.consumes.contains(product.id))
            errors += s"Work Product ${product.id} consumer does not consume the Work Product: $consumer"
        }
      }
    }
    definition.profiles.foreach { profile =>
      if (_empty_(profile.id) || profile.bindings.isEmpty)
        errors += s"profile has empty required metadata: ${profile.id}"
      val bindingids = profile.bindings.map(_.workProductId)
      _duplicate_ids_(s"profile ${profile.id} Work Product binding", bindingids)
      _unknown_references_(s"profile ${profile.id}", "Work Product binding", bindingids, workproductidset)
      if (bindingids.toSet != workproductidset)
        errors += s"profile ${profile.id} must bind exactly the closed Work Product definition"
      profile.bindings.foreach { binding =>
        if (_empty_(binding.workProductId) || !WorkProductDisposition.all.contains(binding.disposition) || binding.disposition == WorkProductDisposition.Disabled && binding.reason.forall(_empty_) || binding.disposition != WorkProductDisposition.Disabled && binding.reason.nonEmpty)
          errors += s"profile ${profile.id} binding has invalid disposition metadata: ${binding.workProductId}"
      }
    }
    _dependency_cycle_work_product_ids(definition.workProducts).foreach { id =>
      errors += s"dependency cycle includes Work Product: $id"
    }
    _canonical_profile_binding_errors(definition).foreach(errors += _)
    errors.result().distinct
  }

  private val _criteria = Vector(
    Criterion("content-core-candidate-composed", "Content Core candidate is composed"),
    Criterion("content-core-accepted", "Content Core is explicitly accepted"),
    Criterion("core-review-rendered", "Content Core review HTML is rendered"),
    Criterion("article-source-authored", "SmartDox article source is authored"),
    Criterion("article-site-rendered", "Article site HTML is generated"),
    Criterion("article-review-rendered", "Article review HTML is rendered"),
    Criterion("article-pdf-rendered", "Article PDF is rendered"),
    Criterion("visual-pages-authored", "Visual Page IR is authored"),
    Criterion("slide-review-rendered", "Slide review HTML is rendered"),
    Criterion("summary-slides-rendered", "Summary slides PDF is rendered"),
    Criterion("infographic-svg-authored", "Editable infographic SVG is authored"),
    Criterion("infographic-png-rendered", "Infographic PNG is rendered"),
    Criterion("video-storyboard-authored", "Video storyboard is authored"),
    Criterion("video-review-rendered", "Video review projection is rendered"),
    Criterion("video-deliverable-rendered", "Video deliverable is rendered"),
    Criterion("slide-logical-chart-rendered", "Slide Logical Chart HTML is rendered"),
    Criterion("video-logical-chart-rendered", "Video Logical Chart HTML is rendered"),
    Criterion("operation-receipt-recorded", "Operation receipt evidence is recorded")
  )

  private val _gates = Vector(
    Gate("content-core-acceptance", "Content Core acceptance gate", Vector("content-core-candidate-composed")),
    Gate("core-review", "Content Core review gate", Vector("content-core-accepted")),
    Gate("article-composition", "Article composition gate", Vector("content-core-accepted", "infographic-svg-authored")),
    Gate("article-site-publication", "Article site publication gate", Vector("article-source-authored")),
    Gate("article-review", "Article review gate", Vector("content-core-accepted", "article-source-authored", "visual-pages-authored")),
    Gate("article-delivery", "Article PDF delivery gate", Vector("article-source-authored")),
    Gate("visual-pages-authoring", "Visual Page authoring gate", Vector("content-core-accepted", "infographic-svg-authored")),
    Gate("slide-review", "Slide review gate", Vector("visual-pages-authored")),
    Gate("slides-delivery", "Summary slides delivery gate", Vector("content-core-accepted", "infographic-svg-authored")),
    Gate("infographic-delivery", "Infographic delivery gate", Vector("infographic-svg-authored")),
    Gate("video-delivery", "Video delivery gate", Vector("video-storyboard-authored", "video-review-rendered")),
    Gate("slide-logical-chart", "Slide Logical Chart gate", Vector("content-core-accepted", "visual-pages-authored")),
    Gate("video-logical-chart", "Video Logical Chart gate", Vector("content-core-accepted", "visual-pages-authored", "video-storyboard-authored")),
    Gate("receipt-evidence", "Operation receipt evidence gate", Vector("operation-receipt-recorded"))
  )

  private val _evidence_references = Vector(
    EvidenceReference("content-core-reference", "Content Core authority reference"),
    EvidenceReference("core-review-reference", "Content Core review reference"),
    EvidenceReference("article-source-reference", "SmartDox article source reference"),
    EvidenceReference("article-site-reference", "Article site output reference"),
    EvidenceReference("article-review-reference", "Article review reference"),
    EvidenceReference("article-output-reference", "Article PDF output reference"),
    EvidenceReference("visual-pages-reference", "Visual Page IR reference"),
    EvidenceReference("slide-review-reference", "Slide review reference"),
    EvidenceReference("slides-output-reference", "Summary slides output reference"),
    EvidenceReference("infographic-source-reference", "Editable infographic SVG reference"),
    EvidenceReference("infographic-output-reference", "Infographic PNG reference"),
    EvidenceReference("video-storyboard-reference", "Video storyboard reference"),
    EvidenceReference("video-review-reference", "Video review projection reference"),
    EvidenceReference("video-output-reference", "Video deliverable reference"),
    EvidenceReference("slide-logical-chart-reference", "Phase-41 Slide Logical Chart reference"),
    EvidenceReference("video-logical-chart-reference", "Video Logical Chart reference"),
    EvidenceReference("operation-receipt-reference", "Future operation receipt evidence reference")
  )

  private val _provider_bindings = Vector(
    ProviderBinding("cozy-content-core", "Cozy Content Core adapter"),
    ProviderBinding("cozy-review-projection", "Cozy review projection adapter"),
    ProviderBinding("smartdox-authoring", "SmartDox article authoring adapter"),
    ProviderBinding("smartdox-rendering", "SmartDox rendering adapter"),
    ProviderBinding("cozy-site", "Cozy Site generation adapter"),
    ProviderBinding("cozy-visual-page", "Cozy Visual Page adapter"),
    ProviderBinding("cozy-infographic", "Cozy infographic adapter"),
    ProviderBinding("cozy-video", "Cozy video adapter"),
    ProviderBinding("phase-41-explanation-structure", "Phase-41 Explanation Structure Review adapter"),
    ProviderBinding("cozy-operation-receipt", "Cozy operation receipt adapter")
  )

  private val _operations = Vector(
    LogicalOperation("content-core.compose", "cozy-content-core", Vector.empty, Vector("content-core-candidate")),
    LogicalOperation("content-core.review", "cozy-review-projection", Vector("content-core-candidate"), Vector("content-core")),
    LogicalOperation("content-core.render-review", "cozy-review-projection", Vector("content-core"), Vector("core-review-html")),
    LogicalOperation("article.compose", "smartdox-authoring", Vector("content-core", "infographic-svg"), Vector("article-source")),
    LogicalOperation("article.publish-site", "cozy-site", Vector("article-source"), Vector("article-html")),
    LogicalOperation("article.render-review", "cozy-review-projection", Vector("content-core", "article-source", "visual-pages"), Vector("article-review-html")),
    LogicalOperation("article.render-pdf", "smartdox-rendering", Vector("article-source"), Vector("article-pdf")),
    LogicalOperation("visual-pages.author", "cozy-visual-page", Vector("content-core", "infographic-svg"), Vector("visual-pages")),
    LogicalOperation("visual-pages.render-review", "cozy-visual-page", Vector("visual-pages"), Vector("slide-review-html")),
    LogicalOperation("summary-slides.render-pdf", "cozy-visual-page", Vector("visual-pages"), Vector("summary-slides-pdf")),
    LogicalOperation("infographic.compose", "cozy-infographic", Vector("content-core"), Vector("infographic-svg")),
    LogicalOperation("infographic.render-png", "cozy-infographic", Vector("infographic-svg"), Vector("infographic-png")),
    LogicalOperation("video.compose-storyboard", "cozy-video", Vector("content-core", "infographic-svg", "visual-pages"), Vector("video-storyboard")),
    LogicalOperation("video.render-review", "cozy-video", Vector("video-storyboard", "visual-pages"), Vector("video-review")),
    LogicalOperation("video.render-deliverable", "cozy-video", Vector("video-review"), Vector("video-deliverable")),
    LogicalOperation("slide-logical-chart.render-review", "phase-41-explanation-structure", Vector("content-core", "visual-pages"), Vector("explanation-structure-review-html")),
    LogicalOperation("video-logical-chart.render-review", "phase-41-explanation-structure", Vector("content-core", "visual-pages", "video-storyboard"), Vector("video-logical-chart-html")),
    LogicalOperation("operation-receipt.record", "cozy-operation-receipt", Vector("content-core-candidate", "content-core", "core-review-html", "article-source", "article-html", "article-review-html", "article-pdf", "visual-pages", "slide-review-html", "summary-slides-pdf", "infographic-svg", "infographic-png", "video-storyboard", "video-review", "video-deliverable", "explanation-structure-review-html", "video-logical-chart-html"), Vector("operation-receipt-evidence"))
  )

  private val _work_products = Vector(
    WorkProduct("content-core-candidate", "Content Core candidate", WorkProductRole.Candidate, "content-core.compose", Vector("content-core.review", "operation-receipt.record"), Vector("content-core-candidate-composed"), Vector.empty, Vector("content-core-acceptance"), Vector("content-core-reference")),
    WorkProduct("content-core", "Content Core", WorkProductRole.Authority, "content-core.review", Vector("content-core.render-review", "article.compose", "article.render-review", "visual-pages.author", "infographic.compose", "video.compose-storyboard", "slide-logical-chart.render-review", "video-logical-chart.render-review", "operation-receipt.record"), Vector("content-core-accepted"), Vector("content-core-candidate"), Vector("content-core-acceptance"), Vector("content-core-reference")),
    WorkProduct("core-review-html", "Core review HTML", WorkProductRole.ReviewProjection, "content-core.render-review", Vector("operation-receipt.record"), Vector("core-review-rendered"), Vector("content-core"), Vector("core-review"), Vector("core-review-reference")),
    WorkProduct("article-source", "SmartDox article source", WorkProductRole.Authority, "article.compose", Vector("article.publish-site", "article.render-review", "article.render-pdf", "operation-receipt.record"), Vector("article-source-authored"), Vector("content-core", "infographic-svg"), Vector("article-composition"), Vector("article-source-reference")),
    WorkProduct("article-html", "Article site HTML", WorkProductRole.SiteDeliverable, "article.publish-site", Vector("operation-receipt.record"), Vector("article-site-rendered"), Vector("article-source"), Vector("article-site-publication"), Vector("article-site-reference")),
    WorkProduct("article-review-html", "Article review HTML", WorkProductRole.ReviewProjection, "article.render-review", Vector("operation-receipt.record"), Vector("article-review-rendered"), Vector("content-core", "article-source", "visual-pages"), Vector("article-review"), Vector("article-review-reference")),
    WorkProduct("article-pdf", "Article PDF", WorkProductRole.Deliverable, "article.render-pdf", Vector("operation-receipt.record"), Vector("article-pdf-rendered"), Vector("article-source"), Vector("article-delivery"), Vector("article-output-reference")),
    WorkProduct("visual-pages", "Visual Page IR", WorkProductRole.Authority, "visual-pages.author", Vector("article.render-review", "visual-pages.render-review", "summary-slides.render-pdf", "video.compose-storyboard", "video.render-review", "slide-logical-chart.render-review", "video-logical-chart.render-review", "operation-receipt.record"), Vector("visual-pages-authored"), Vector("content-core", "infographic-svg"), Vector("visual-pages-authoring"), Vector("visual-pages-reference")),
    WorkProduct("slide-review-html", "Slide review HTML", WorkProductRole.ReviewProjection, "visual-pages.render-review", Vector("operation-receipt.record"), Vector("slide-review-rendered"), Vector("visual-pages"), Vector("slide-review"), Vector("slide-review-reference")),
    WorkProduct("summary-slides-pdf", "Summary slides PDF", WorkProductRole.Deliverable, "summary-slides.render-pdf", Vector("operation-receipt.record"), Vector("summary-slides-rendered"), Vector("visual-pages"), Vector("slides-delivery"), Vector("slides-output-reference")),
    WorkProduct("infographic-svg", "Editable infographic SVG", WorkProductRole.Authority, "infographic.compose", Vector("article.compose", "infographic.render-png", "video.compose-storyboard", "operation-receipt.record"), Vector("infographic-svg-authored"), Vector("content-core"), Vector("infographic-delivery"), Vector("infographic-source-reference")),
    WorkProduct("infographic-png", "Infographic PNG", WorkProductRole.Deliverable, "infographic.render-png", Vector("operation-receipt.record"), Vector("infographic-png-rendered"), Vector("infographic-svg"), Vector("infographic-delivery"), Vector("infographic-output-reference")),
    WorkProduct("video-storyboard", "Video storyboard", WorkProductRole.Plan, "video.compose-storyboard", Vector("video.render-review", "video-logical-chart.render-review", "operation-receipt.record"), Vector("video-storyboard-authored"), Vector("content-core", "infographic-svg", "visual-pages"), Vector("video-delivery"), Vector("video-storyboard-reference")),
    WorkProduct("video-review", "Video review HTML", WorkProductRole.ReviewProjection, "video.render-review", Vector("video.render-deliverable", "operation-receipt.record"), Vector("video-review-rendered"), Vector("video-storyboard", "visual-pages"), Vector("video-delivery"), Vector("video-review-reference")),
    WorkProduct("video-deliverable", "Video deliverable", WorkProductRole.Deliverable, "video.render-deliverable", Vector("operation-receipt.record"), Vector("video-deliverable-rendered"), Vector("video-review"), Vector("video-delivery"), Vector("video-output-reference")),
    WorkProduct("explanation-structure-review-html", "Slide Logical Chart HTML", WorkProductRole.ReviewProjection, "slide-logical-chart.render-review", Vector("operation-receipt.record"), Vector("slide-logical-chart-rendered"), Vector("content-core", "visual-pages"), Vector("slide-logical-chart"), Vector("slide-logical-chart-reference")),
    WorkProduct("video-logical-chart-html", "Video Logical Chart HTML", WorkProductRole.ReviewProjection, "video-logical-chart.render-review", Vector("operation-receipt.record"), Vector("video-logical-chart-rendered"), Vector("content-core", "visual-pages", "video-storyboard"), Vector("video-logical-chart"), Vector("video-logical-chart-reference")),
    WorkProduct("operation-receipt-evidence", "Future operation receipt evidence", WorkProductRole.Receipt, "operation-receipt.record", Vector.empty, Vector("operation-receipt-recorded"), Vector("content-core-candidate", "content-core", "core-review-html", "article-source", "article-html", "article-review-html", "article-pdf", "visual-pages", "slide-review-html", "summary-slides-pdf", "infographic-svg", "infographic-png", "video-storyboard", "video-review", "video-deliverable", "explanation-structure-review-html", "video-logical-chart-html"), Vector("receipt-evidence"), Vector("operation-receipt-reference"))
  )

  private def _no_video_bindings(profileid: String): Vector[WorkProductBinding] = Vector(
    WorkProductBinding("content-core-candidate", WorkProductDisposition.Optional, None),
    WorkProductBinding("content-core", WorkProductDisposition.Required, None),
    WorkProductBinding("core-review-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("article-source", WorkProductDisposition.Required, None),
    WorkProductBinding("article-html", WorkProductDisposition.Required, None),
    WorkProductBinding("article-review-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("article-pdf", WorkProductDisposition.Required, None),
    WorkProductBinding("visual-pages", WorkProductDisposition.Required, None),
    WorkProductBinding("slide-review-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("summary-slides-pdf", WorkProductDisposition.Optional, None),
    WorkProductBinding("infographic-svg", WorkProductDisposition.Required, None),
    WorkProductBinding("infographic-png", WorkProductDisposition.Optional, None),
    WorkProductBinding("video-storyboard", WorkProductDisposition.Disabled, Some(s"profile $profileid disables video branch")),
    WorkProductBinding("video-review", WorkProductDisposition.Disabled, Some(s"profile $profileid disables video branch")),
    WorkProductBinding("video-deliverable", WorkProductDisposition.Disabled, Some(s"profile $profileid disables video branch")),
    WorkProductBinding("explanation-structure-review-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("video-logical-chart-html", WorkProductDisposition.Disabled, Some(s"profile $profileid disables video branch")),
    WorkProductBinding("operation-receipt-evidence", WorkProductDisposition.Optional, None)
  )

  private val _video_bindings = Vector(
    WorkProductBinding("content-core-candidate", WorkProductDisposition.Optional, None),
    WorkProductBinding("content-core", WorkProductDisposition.Required, None),
    WorkProductBinding("core-review-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("article-source", WorkProductDisposition.Required, None),
    WorkProductBinding("article-html", WorkProductDisposition.Required, None),
    WorkProductBinding("article-review-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("article-pdf", WorkProductDisposition.Required, None),
    WorkProductBinding("visual-pages", WorkProductDisposition.Required, None),
    WorkProductBinding("slide-review-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("summary-slides-pdf", WorkProductDisposition.Optional, None),
    WorkProductBinding("infographic-svg", WorkProductDisposition.Required, None),
    WorkProductBinding("infographic-png", WorkProductDisposition.Optional, None),
    WorkProductBinding("video-storyboard", WorkProductDisposition.Required, None),
    WorkProductBinding("video-review", WorkProductDisposition.Required, None),
    WorkProductBinding("video-deliverable", WorkProductDisposition.Required, None),
    WorkProductBinding("explanation-structure-review-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("video-logical-chart-html", WorkProductDisposition.Optional, None),
    WorkProductBinding("operation-receipt-evidence", WorkProductDisposition.Optional, None)
  )

  private val _standard_bindings = _no_video_bindings("standard")
  private val _standard_video_bindings = _video_bindings
  private val _bok_bindings = _no_video_bindings("bok")
  private val _bok_video_bindings = _video_bindings
  private val _simplemodeling_org_bindings = _no_video_bindings("simplemodeling-org")
  private val _simplemodeling_org_video_bindings = _video_bindings

  private val _document_production = WorkflowDefinition(
    "document-production",
    _work_products,
    _operations,
    _criteria,
    _gates,
    _evidence_references,
    _provider_bindings,
    Vector(
      WorkflowProfile("standard", _standard_bindings),
      WorkflowProfile("standard-video", _standard_video_bindings),
      WorkflowProfile("bok", _bok_bindings),
      WorkflowProfile("bok-video", _bok_video_bindings),
      WorkflowProfile("simplemodeling-org", _simplemodeling_org_bindings),
      WorkflowProfile("simplemodeling-org-video", _simplemodeling_org_video_bindings)
    )
  )

  private def _validated_document_production(): WorkflowDefinition = {
    val errors = validate(_document_production)
    if (errors.nonEmpty)
      throw new IllegalStateException(s"invalid document-production workflow: ${errors.mkString("; ")}")
    _document_production
  }

  private def _dependency_cycle_work_product_ids(workproducts: Vector[WorkProduct]): Vector[String] = {
    val dependencies = workproducts.map(product => product.id -> product.dependencies).toMap

    def _visit_(id: String, visiting: Set[String]): Vector[String] =
      if (visiting.contains(id)) Vector(id)
      else dependencies.getOrElse(id, Vector.empty).flatMap(dependency => _visit_(dependency, visiting + id))

    workproducts.flatMap(product => _visit_(product.id, Set.empty)).distinct
  }

  private def _canonical_profile_binding_errors(definition: WorkflowDefinition): Vector[String] = {
    Vector(
      "standard" -> _standard_bindings,
      "standard-video" -> _standard_video_bindings,
      "bok" -> _bok_bindings,
      "bok-video" -> _bok_video_bindings,
      "simplemodeling-org" -> _simplemodeling_org_bindings,
      "simplemodeling-org-video" -> _simplemodeling_org_video_bindings
    ).flatMap { case (profileid, expectedbindings) =>
      definition.profiles.find(_.id == profileid).toVector.flatMap { profile =>
        expectedbindings.flatMap { expectedbinding =>
          profile.bindings.find(_.workProductId == expectedbinding.workProductId).toVector.flatMap { binding =>
            if (binding.disposition == expectedbinding.disposition && binding.reason == expectedbinding.reason)
              Vector.empty
            else
              Vector(
                s"profile $profileid Work Product binding ${expectedbinding.workProductId} differs from canonical matrix: expected disposition ${expectedbinding.disposition.value} with reason ${expectedbinding.reason.getOrElse("none")}, found disposition ${binding.disposition.value} with reason ${binding.reason.getOrElse("none")}"
              )
          }
        }
      }
    }
  }
}
