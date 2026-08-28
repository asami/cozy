package cozy.media

import CozyExplanationProjection._

/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[media] object CozyExplanationProjectionCodec {
  private[media] def _parse_projection_map(value: CozyExplanation.JsonValue, path: String): ProjectionMap = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "compositionIdentity", "planIdentity", "explanationCatalog", "presentationCatalog", "presentation", "video", "identity"), path)
    _schema_version(fields, _projection_map_schema, path)
    ProjectionMap(
      _identity_text(_string(_field(fields, "compositionIdentity", path), s"$path.compositionIdentity"), s"$path.compositionIdentity"),
      _identity_text(_string(_field(fields, "planIdentity", path), s"$path.planIdentity"), s"$path.planIdentity"),
      _parse_catalog_selector(_field(fields, "explanationCatalog", path), s"$path.explanationCatalog"),
      _parse_catalog_selector(_field(fields, "presentationCatalog", path), s"$path.presentationCatalog"),
      _parse_presentation_mapping(_field(fields, "presentation", path), s"$path.presentation"),
      _parse_video_mapping(_field(fields, "video", path), s"$path.video"),
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
  }

  private[media] def _parse_projection(value: CozyExplanation.JsonValue, path: String): Projection = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "compositionIdentity", "planIdentity", "projectionMapIdentity", "explanationCatalog", "presentationCatalog", "presentation", "video", "receipt", "identity"), path)
    _schema_version(fields, _projection_schema, path)
    Projection(
      _identity_text(_string(_field(fields, "compositionIdentity", path), s"$path.compositionIdentity"), s"$path.compositionIdentity"),
      _identity_text(_string(_field(fields, "planIdentity", path), s"$path.planIdentity"), s"$path.planIdentity"),
      _identity_text(_string(_field(fields, "projectionMapIdentity", path), s"$path.projectionMapIdentity"), s"$path.projectionMapIdentity"),
      _parse_catalog_selector(_field(fields, "explanationCatalog", path), s"$path.explanationCatalog"),
      _parse_catalog_selector(_field(fields, "presentationCatalog", path), s"$path.presentationCatalog"),
      _parse_projection_presentation(_field(fields, "presentation", path), s"$path.presentation"),
      _parse_projection_video(_field(fields, "video", path), s"$path.video"),
      _parse_receipt(_field(fields, "receipt", path), s"$path.receipt"),
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
  }

  private[media] def _parse_catalog_selector(value: CozyExplanation.JsonValue, path: String): CozyExplanation.CatalogSelector = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "revision", "identity"), path)
    CozyExplanation.CatalogSelector(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "revision", path), s"$path.revision"),
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
  }

  private[media] def _parse_presentation_mapping(value: CozyExplanation.JsonValue, path: String): PresentationMapping = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("stepMappings", "identity"), path)
    val mappings = _array(_field(fields, "stepMappings", path), s"$path.stepMappings").zipWithIndex.map {
      case (item, index) => _parse_presentation_step_mapping(item, s"$path.stepMappings[$index]")
    }
    PresentationMapping(mappings, _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity"))
  }

  private[media] def _parse_video_mapping(value: CozyExplanation.JsonValue, path: String): VideoMapping = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("stepMappings", "identity"), path)
    val mappings = _array(_field(fields, "stepMappings", path), s"$path.stepMappings").zipWithIndex.map {
      case (item, index) => _parse_video_step_mapping(item, s"$path.stepMappings[$index]")
    }
    VideoMapping(mappings, _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity"))
  }

  private[media] def _parse_projection_presentation(value: CozyExplanation.JsonValue, path: String): ProjectionPresentation = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("visualPageSet", "stepMappings", "identity"), path)
    val selectorfields = _object(_field(fields, "visualPageSet", path), s"$path.visualPageSet")
    _exact_fields(selectorfields, Vector("id", "identity"), s"$path.visualPageSet")
    ProjectionPresentation(
      VisualPageSetSelector(
        _token(_string(_field(selectorfields, "id", s"$path.visualPageSet"), s"$path.visualPageSet.id"), s"$path.visualPageSet.id"),
        _identity_text(_string(_field(selectorfields, "identity", s"$path.visualPageSet"), s"$path.visualPageSet.identity"), s"$path.visualPageSet.identity")
      ),
      _array(_field(fields, "stepMappings", path), s"$path.stepMappings").zipWithIndex.map {
        case (item, index) => _parse_presentation_step_mapping(item, s"$path.stepMappings[$index]")
      },
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
  }

  private[media] def _parse_projection_video(value: CozyExplanation.JsonValue, path: String): ProjectionVideo = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("storyboard", "stepMappings", "identity"), path)
    val selectorfields = _object(_field(fields, "storyboard", path), s"$path.storyboard")
    _exact_fields(selectorfields, Vector("identity"), s"$path.storyboard")
    ProjectionVideo(
      StoryboardSelector(_identity_text(_string(_field(selectorfields, "identity", s"$path.storyboard"), s"$path.storyboard.identity"), s"$path.storyboard.identity")),
      _array(_field(fields, "stepMappings", path), s"$path.stepMappings").zipWithIndex.map {
        case (item, index) => _parse_video_step_mapping(item, s"$path.stepMappings[$index]")
      },
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
  }

  private[media] def _parse_presentation_step_mapping(value: CozyExplanation.JsonValue, path: String): PresentationStepMapping = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("stepId", "pageIds"), path)
    PresentationStepMapping(
      _token(_string(_field(fields, "stepId", path), s"$path.stepId"), s"$path.stepId"),
      _target_ids(_field(fields, "pageIds", path), s"$path.pageIds")
    )
  }

  private[media] def _parse_video_step_mapping(value: CozyExplanation.JsonValue, path: String): VideoStepMapping = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("stepId", "sceneIds"), path)
    VideoStepMapping(
      _token(_string(_field(fields, "stepId", path), s"$path.stepId"), s"$path.stepId"),
      _target_ids(_field(fields, "sceneIds", path), s"$path.sceneIds")
    )
  }

  private[media] def _parse_receipt(value: CozyExplanation.JsonValue, path: String): ProjectionReceipt = {
    val fields = _object(value, path)
    val names = Vector(
      "compositionIdentity", "planIdentity", "projectionMapIdentity", "explanationCatalogIdentity",
      "presentationLogicalCatalogIdentity", "presentationCatalogIdentity", "visualPageSetIdentity",
      "storyboardIdentity", "presentationMappingIdentity", "videoMappingIdentity", "identity"
    )
    _exact_fields(fields, names, path)
    ProjectionReceipt(
      _identity_field(fields, "compositionIdentity", path), _identity_field(fields, "planIdentity", path),
      _identity_field(fields, "projectionMapIdentity", path), _identity_field(fields, "explanationCatalogIdentity", path),
      _identity_field(fields, "presentationLogicalCatalogIdentity", path), _identity_field(fields, "presentationCatalogIdentity", path),
      _identity_field(fields, "visualPageSetIdentity", path), _identity_field(fields, "storyboardIdentity", path),
      _identity_field(fields, "presentationMappingIdentity", path), _identity_field(fields, "videoMappingIdentity", path),
      _identity_field(fields, "identity", path)
    )
  }

  private[media] def _projection_map_value(value: ProjectionMap, includeidentity: Boolean): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "schema" -> CozyExplanation.JsonString(_projection_map_schema),
      "version" -> CozyExplanation.JsonNumber(1),
      "compositionIdentity" -> CozyExplanation.JsonString(value.compositionIdentity),
      "planIdentity" -> CozyExplanation.JsonString(value.planIdentity),
      "explanationCatalog" -> _catalog_selector_value(value.explanationCatalog),
      "presentationCatalog" -> _catalog_selector_value(value.presentationCatalog),
      "presentation" -> _presentation_mapping_value(value.presentation.stepMappings, includeidentity = true, value.presentation.identity),
      "video" -> _video_mapping_value(value.video.stepMappings, includeidentity = true, value.video.identity)
    ) ++ (if (includeidentity) Vector("identity" -> CozyExplanation.JsonString(value.identity)) else Vector.empty)
    CozyExplanation.JsonObject(fields)
  }

  private[media] def _projection_value(value: Projection, includeidentity: Boolean): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "schema" -> CozyExplanation.JsonString(_projection_schema),
      "version" -> CozyExplanation.JsonNumber(1),
      "compositionIdentity" -> CozyExplanation.JsonString(value.compositionIdentity),
      "planIdentity" -> CozyExplanation.JsonString(value.planIdentity),
      "projectionMapIdentity" -> CozyExplanation.JsonString(value.projectionMapIdentity),
      "explanationCatalog" -> _catalog_selector_value(value.explanationCatalog),
      "presentationCatalog" -> _catalog_selector_value(value.presentationCatalog),
      "presentation" -> _projection_presentation_value(value.presentation),
      "video" -> _projection_video_value(value.video),
      "receipt" -> _receipt_value(value.receipt, includeidentity = true)
    ) ++ (if (includeidentity) Vector("identity" -> CozyExplanation.JsonString(value.identity)) else Vector.empty)
    CozyExplanation.JsonObject(fields)
  }

  private[media] def _catalog_selector_value(value: CozyExplanation.CatalogSelector): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "id" -> CozyExplanation.JsonString(value.id),
    "revision" -> CozyExplanation.JsonNumber(value.revision),
    "identity" -> CozyExplanation.JsonString(value.identity)
  ))

  private[media] def _presentation_mapping_value(
    mappings: Vector[PresentationStepMapping],
    includeidentity: Boolean,
    identity: String
  ): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "stepMappings" -> CozyExplanation.JsonArray(mappings.map(_presentation_step_mapping_value))
    ) ++ (if (includeidentity) Vector("identity" -> CozyExplanation.JsonString(identity)) else Vector.empty)
    CozyExplanation.JsonObject(fields)
  }

  private[media] def _video_mapping_value(
    mappings: Vector[VideoStepMapping],
    includeidentity: Boolean,
    identity: String
  ): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "stepMappings" -> CozyExplanation.JsonArray(mappings.map(_video_step_mapping_value))
    ) ++ (if (includeidentity) Vector("identity" -> CozyExplanation.JsonString(identity)) else Vector.empty)
    CozyExplanation.JsonObject(fields)
  }

  private[media] def _presentation_step_mapping_value(value: PresentationStepMapping): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "stepId" -> CozyExplanation.JsonString(value.stepId),
    "pageIds" -> CozyExplanation.JsonArray(value.pageIds.map(CozyExplanation.JsonString))
  ))

  private[media] def _video_step_mapping_value(value: VideoStepMapping): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "stepId" -> CozyExplanation.JsonString(value.stepId),
    "sceneIds" -> CozyExplanation.JsonArray(value.sceneIds.map(CozyExplanation.JsonString))
  ))

  private[media] def _projection_presentation_value(value: ProjectionPresentation): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "visualPageSet" -> CozyExplanation.JsonObject(Vector(
      "id" -> CozyExplanation.JsonString(value.visualPageSet.id),
      "identity" -> CozyExplanation.JsonString(value.visualPageSet.identity)
    )),
    "stepMappings" -> CozyExplanation.JsonArray(value.stepMappings.map(_presentation_step_mapping_value)),
    "identity" -> CozyExplanation.JsonString(value.identity)
  ))

  private[media] def _projection_video_value(value: ProjectionVideo): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "storyboard" -> CozyExplanation.JsonObject(Vector(
      "identity" -> CozyExplanation.JsonString(value.storyboard.identity)
    )),
    "stepMappings" -> CozyExplanation.JsonArray(value.stepMappings.map(_video_step_mapping_value)),
    "identity" -> CozyExplanation.JsonString(value.identity)
  ))

  private[media] def _receipt_value(value: ProjectionReceipt, includeidentity: Boolean): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "compositionIdentity" -> CozyExplanation.JsonString(value.compositionIdentity),
      "planIdentity" -> CozyExplanation.JsonString(value.planIdentity),
      "projectionMapIdentity" -> CozyExplanation.JsonString(value.projectionMapIdentity),
      "explanationCatalogIdentity" -> CozyExplanation.JsonString(value.explanationCatalogIdentity),
      "presentationLogicalCatalogIdentity" -> CozyExplanation.JsonString(value.presentationLogicalCatalogIdentity),
      "presentationCatalogIdentity" -> CozyExplanation.JsonString(value.presentationCatalogIdentity),
      "visualPageSetIdentity" -> CozyExplanation.JsonString(value.visualPageSetIdentity),
      "storyboardIdentity" -> CozyExplanation.JsonString(value.storyboardIdentity),
      "presentationMappingIdentity" -> CozyExplanation.JsonString(value.presentationMappingIdentity),
      "videoMappingIdentity" -> CozyExplanation.JsonString(value.videoMappingIdentity)
    ) ++ (if (includeidentity) Vector("identity" -> CozyExplanation.JsonString(value.identity)) else Vector.empty)
    CozyExplanation.JsonObject(fields)
  }

  private[media] def _receipt_identity(value: ProjectionReceipt): String =
    _identity(_receipt_value(value, includeidentity = false))
}
