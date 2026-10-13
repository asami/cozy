package cozy.media

import CozyExplanationProjection._

/*
 * @since   Aug. 28, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[media] object CozyExplanationProjectionCodec {
  private[media] def _parse_projection_map(value: CozyExplanation.JsonValue, path: String): ProjectionMap = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "explanationCatalog", "presentationCatalog", "presentation", "video"), path)
    _schema_version(fields, _projection_map_schema, path)
    ProjectionMap(
      _parse_catalog_selector(_field(fields, "explanationCatalog", path), s"$path.explanationCatalog"),
      _parse_catalog_selector(_field(fields, "presentationCatalog", path), s"$path.presentationCatalog"),
      _parse_presentation_mapping(_field(fields, "presentation", path), s"$path.presentation"),
      _parse_video_mapping(_field(fields, "video", path), s"$path.video")
    )
  }

  private[media] def _parse_projection(value: CozyExplanation.JsonValue, path: String): Projection = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "explanationCatalog", "presentationCatalog", "presentation", "video"), path)
    _schema_version(fields, _projection_schema, path)
    Projection(
      _parse_catalog_selector(_field(fields, "explanationCatalog", path), s"$path.explanationCatalog"),
      _parse_catalog_selector(_field(fields, "presentationCatalog", path), s"$path.presentationCatalog"),
      _parse_projection_presentation(_field(fields, "presentation", path), s"$path.presentation"),
      _parse_projection_video(_field(fields, "video", path), s"$path.video")
    )
  }

  private[media] def _parse_catalog_selector(value: CozyExplanation.JsonValue, path: String): CozyExplanation.CatalogSelector = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "revision"), path)
    CozyExplanation.CatalogSelector(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "revision", path), s"$path.revision")
    )
  }

  private[media] def _parse_presentation_mapping(value: CozyExplanation.JsonValue, path: String): PresentationMapping = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("stepMappings"), path)
    val mappings = _array(_field(fields, "stepMappings", path), s"$path.stepMappings").zipWithIndex.map {
      case (item, index) => _parse_presentation_step_mapping(item, s"$path.stepMappings[$index]")
    }
    PresentationMapping(mappings)
  }

  private[media] def _parse_video_mapping(value: CozyExplanation.JsonValue, path: String): VideoMapping = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("stepMappings"), path)
    val mappings = _array(_field(fields, "stepMappings", path), s"$path.stepMappings").zipWithIndex.map {
      case (item, index) => _parse_video_step_mapping(item, s"$path.stepMappings[$index]")
    }
    VideoMapping(mappings)
  }

  private[media] def _parse_projection_presentation(value: CozyExplanation.JsonValue, path: String): ProjectionPresentation = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("visualPageSet", "stepMappings"), path)
    val selectorfields = _object(_field(fields, "visualPageSet", path), s"$path.visualPageSet")
    _exact_fields(selectorfields, Vector("id"), s"$path.visualPageSet")
    ProjectionPresentation(
      VisualPageSetSelector(
        _token(_string(_field(selectorfields, "id", s"$path.visualPageSet"), s"$path.visualPageSet.id"), s"$path.visualPageSet.id")
      ),
      _array(_field(fields, "stepMappings", path), s"$path.stepMappings").zipWithIndex.map {
        case (item, index) => _parse_presentation_step_mapping(item, s"$path.stepMappings[$index]")
      }
    )
  }

  private[media] def _parse_projection_video(value: CozyExplanation.JsonValue, path: String): ProjectionVideo = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("stepMappings"), path)
    ProjectionVideo(
      _array(_field(fields, "stepMappings", path), s"$path.stepMappings").zipWithIndex.map {
        case (item, index) => _parse_video_step_mapping(item, s"$path.stepMappings[$index]")
      }
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

  private[media] def _projection_map_value(value: ProjectionMap): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "schema" -> CozyExplanation.JsonString(_projection_map_schema),
      "version" -> CozyExplanation.JsonNumber(1),
      "explanationCatalog" -> _catalog_selector_value(value.explanationCatalog),
      "presentationCatalog" -> _catalog_selector_value(value.presentationCatalog),
      "presentation" -> _presentation_mapping_value(value.presentation.stepMappings),
      "video" -> _video_mapping_value(value.video.stepMappings)
    )
    CozyExplanation.JsonObject(fields)
  }

  private[media] def _projection_value(value: Projection): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "schema" -> CozyExplanation.JsonString(_projection_schema),
      "version" -> CozyExplanation.JsonNumber(1),
      "explanationCatalog" -> _catalog_selector_value(value.explanationCatalog),
      "presentationCatalog" -> _catalog_selector_value(value.presentationCatalog),
      "presentation" -> _projection_presentation_value(value.presentation),
      "video" -> _projection_video_value(value.video)
    )
    CozyExplanation.JsonObject(fields)
  }

  private[media] def _catalog_selector_value(value: CozyExplanation.CatalogSelector): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "id" -> CozyExplanation.JsonString(value.id),
    "revision" -> CozyExplanation.JsonNumber(value.revision)
  ))

  private[media] def _presentation_mapping_value(
    mappings: Vector[PresentationStepMapping]
  ): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "stepMappings" -> CozyExplanation.JsonArray(mappings.map(_presentation_step_mapping_value))
    )
    CozyExplanation.JsonObject(fields)
  }

  private[media] def _video_mapping_value(
    mappings: Vector[VideoStepMapping]
  ): CozyExplanation.JsonValue = {
    val fields = Vector[(String, CozyExplanation.JsonValue)](
      "stepMappings" -> CozyExplanation.JsonArray(mappings.map(_video_step_mapping_value))
    )
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
      "id" -> CozyExplanation.JsonString(value.visualPageSet.id)
    )),
    "stepMappings" -> CozyExplanation.JsonArray(value.stepMappings.map(_presentation_step_mapping_value))
  ))

  private[media] def _projection_video_value(value: ProjectionVideo): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "stepMappings" -> CozyExplanation.JsonArray(value.stepMappings.map(_video_step_mapping_value))
  ))

}
