package cozy.ui

import cozy.ui.CozyLogicalUiRuntime._

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyLogicalUiRuntimeValidation {
  def validate(model: Model): Either[RuntimeError, Model] =
    if (model == null)
      Left(_error("LUI74_MODEL_INVALID", "model", "null root model"))
    else if (model.metadata == null)
      Left(_error("LUI74_MODEL_INVALID", "model.metadata", "null metadata"))
    else if (model.metadata.contract != minimumContract)
      Left(_error("LUI74_CONTRACT_INCOMPATIBLE", "model.metadata.contract", "null or nonidentical ContractDescriptor"))
    else if (model.metadata.provenance == null)
      Left(_error("LUI74_PROVENANCE_INVALID", "model.metadata.provenance", "missing SourceProvenance"))
    else
      _validate_provenance(model.metadata.provenance, "model.metadata.provenance").flatMap { _ =>
        if (model.screen == null)
          Left(_error("LUI74_MODEL_INVALID", "model.screen", "null screen"))
        else
          _validate_screen(model.screen, "model.screen").map(_ => model)
      }

  private def _validate_provenance(provenance: SourceProvenance, path: String): Either[RuntimeError, Unit] =
    _validate_provenance_text(provenance.producerId, path + ".producerId").flatMap { _ =>
      _validate_provenance_text(provenance.sourceId, path + ".sourceId").flatMap { _ =>
        _validate_provenance_text(provenance.revision, path + ".revision")
      }
    }

  private def _validate_provenance_text(value: String, path: String): Either[RuntimeError, Unit] =
    if (_is_valid_id(value)) Right(())
    else Left(_error("LUI74_PROVENANCE_INVALID", path, "invalid SourceProvenance field"))

  private def _validate_screen(screen: Screen, path: String): Either[RuntimeError, Unit] =
    screen match {
      case list: ResourceList => _validate_list(list, path)
      case detail: ResourceDetail => _validate_detail(detail, path)
    }

  private def _validate_list(list: ResourceList, path: String): Either[RuntimeError, Unit] =
    _validate_id(list.id, path + ".id").flatMap { _ =>
      _validate_text(list.title, path + ".title").flatMap { _ =>
        _validate_list_items(list.items, path + ".items").flatMap { _ =>
          _validate_actions(list.actions, path + ".actions").flatMap { _ =>
            _validate_detail_target(list.detailTarget, list.id, path + ".detailTarget")
          }
        }
      }
    }

  private def _validate_detail(detail: ResourceDetail, path: String): Either[RuntimeError, Unit] =
    _validate_id(detail.id, path + ".id").flatMap { _ =>
      _validate_id(detail.resourceId, path + ".resourceId").flatMap { _ =>
        _validate_text(detail.title, path + ".title").flatMap { _ =>
          _validate_sections(detail.sections, path + ".sections").flatMap { _ =>
            _validate_actions(detail.actions, path + ".actions")
          }
        }
      }
    }

  private def _validate_list_items(items: Vector[ListItem], path: String): Either[RuntimeError, Unit] =
    if (items == null)
      Left(_error("LUI74_ELEMENT_INVALID", path, "null nested vector"))
    else
      _validate_item_ids(items, path, Set.empty).flatMap { _ =>
        _validate_items(items, path, 0)
      }

  private def _validate_item_ids(items: Vector[ListItem], path: String, ids: Set[String]): Either[RuntimeError, Unit] =
    items.zipWithIndex.foldLeft[Either[RuntimeError, Set[String]]](Right(ids)) {
      case (result, (item, index)) =>
        result.flatMap { knownids =>
          val itempath = path + "[" + index + "]"
          if (item == null)
            Left(_error("LUI74_ELEMENT_INVALID", itempath, "null nested element"))
          else
            _validate_id(item.id, itempath + ".id").flatMap { _ =>
              if (knownids.contains(item.id))
                Left(_error("LUI74_DUPLICATE_ID", itempath + ".id", "duplicate within its declared ID scope"))
              else Right(knownids + item.id)
            }
        }
    }.map(_ => ())

  private def _validate_items(items: Vector[ListItem], path: String, offset: Int): Either[RuntimeError, Unit] =
    if (offset >= items.size) Right(())
    else {
      val item = items(offset)
      _validate_fields(item.fields, path + "[" + offset + "].fields").flatMap { _ =>
        _validate_actions(item.actions, path + "[" + offset + "].actions").flatMap { _ =>
          _validate_items(items, path, offset + 1)
        }
      }
    }

  private def _validate_sections(sections: Vector[Section], path: String): Either[RuntimeError, Unit] =
    if (sections == null)
      Left(_error("LUI74_ELEMENT_INVALID", path, "null nested vector"))
    else
      _validate_section_ids(sections, path, Set.empty).flatMap { _ =>
        _validate_section_content(sections, path, 0)
      }

  private def _validate_section_ids(sections: Vector[Section], path: String, ids: Set[String]): Either[RuntimeError, Unit] =
    sections.zipWithIndex.foldLeft[Either[RuntimeError, Set[String]]](Right(ids)) {
      case (result, (section, index)) =>
        result.flatMap { knownids =>
          val sectionpath = path + "[" + index + "]"
          if (section == null)
            Left(_error("LUI74_ELEMENT_INVALID", sectionpath, "null nested element"))
          else
            _validate_id(section.id, sectionpath + ".id").flatMap { _ =>
              if (knownids.contains(section.id))
                Left(_error("LUI74_DUPLICATE_ID", sectionpath + ".id", "duplicate within its declared ID scope"))
              else Right(knownids + section.id)
            }
        }
    }.map(_ => ())

  private def _validate_section_content(sections: Vector[Section], path: String, offset: Int): Either[RuntimeError, Unit] =
    if (offset >= sections.size) Right(())
    else {
      val section = sections(offset)
      _validate_optional_text(section.title, path + "[" + offset + "].title").flatMap { _ =>
        _validate_fields(section.fields, path + "[" + offset + "].fields").flatMap { _ =>
          _validate_section_content(sections, path, offset + 1)
        }
      }
    }

  private def _validate_fields(fields: Vector[Field], path: String): Either[RuntimeError, Unit] =
    if (fields == null)
      Left(_error("LUI74_ELEMENT_INVALID", path, "null nested vector"))
    else
      _validate_field_ids_and_roles(fields, path, Set.empty, Set.empty).flatMap { _ =>
        _validate_field_content(fields, path, 0)
      }

  private def _validate_field_ids_and_roles(
    fields: Vector[Field],
    path: String,
    ids: Set[String],
    roles: Set[String]
  ): Either[RuntimeError, Unit] =
    fields.zipWithIndex.foldLeft[Either[RuntimeError, (Set[String], Set[String])] ](Right((ids, roles))) {
      case (result, (field, index)) =>
        result.flatMap { case (knownids, knownroles) =>
          val fieldpath = path + "[" + index + "]"
          if (field == null)
            Left(_error("LUI74_ELEMENT_INVALID", fieldpath, "null nested element"))
          else
            _validate_id(field.id, fieldpath + ".id").flatMap { _ =>
              if (knownids.contains(field.id))
                Left(_error("LUI74_DUPLICATE_ID", fieldpath + ".id", "duplicate within its declared ID scope"))
              else if (field.role == null)
                Left(_error("LUI74_ROLE_INVALID", fieldpath + ".role", "null presentation role"))
              else if (field.role != ContentRole && knownroles.contains(field.role.id))
                Left(_error("LUI74_DUPLICATE_ROLE", fieldpath + ".role", "repeated non-Content role"))
              else {
                val nextroles = if (field.role == ContentRole) knownroles else knownroles + field.role.id
                Right((knownids + field.id, nextroles))
              }
            }
        }
    }.map(_ => ())

  private def _validate_field_content(fields: Vector[Field], path: String, offset: Int): Either[RuntimeError, Unit] =
    if (offset >= fields.size) Right(())
    else {
      val field = fields(offset)
      val fieldpath = path + "[" + offset + "]"
      _validate_text(field.label, fieldpath + ".label").flatMap { _ =>
        _validate_value(field.value, fieldpath + ".value").flatMap { _ =>
          _validate_role_value(field.role, field.value, fieldpath).flatMap { _ =>
            _validate_field_content(fields, path, offset + 1)
          }
        }
      }
    }

  private def _validate_actions(actions: Vector[Action], path: String): Either[RuntimeError, Unit] =
    if (actions == null)
      Left(_error("LUI74_ELEMENT_INVALID", path, "null nested vector"))
    else
      _validate_action_ids(actions, path, Set.empty).flatMap { _ =>
        _validate_action_content(actions, path, 0)
      }

  private def _validate_action_ids(actions: Vector[Action], path: String, ids: Set[String]): Either[RuntimeError, Unit] =
    actions.zipWithIndex.foldLeft[Either[RuntimeError, Set[String]]](Right(ids)) {
      case (result, (action, index)) =>
        result.flatMap { knownids =>
          val actionpath = path + "[" + index + "]"
          if (action == null)
            Left(_error("LUI74_ELEMENT_INVALID", actionpath, "null nested element"))
          else
            _validate_id(action.id, actionpath + ".id").flatMap { _ =>
              if (knownids.contains(action.id))
                Left(_error("LUI74_DUPLICATE_ID", actionpath + ".id", "duplicate within its declared ID scope"))
              else Right(knownids + action.id)
            }
        }
    }.map(_ => ())

  private def _validate_action_content(actions: Vector[Action], path: String, offset: Int): Either[RuntimeError, Unit] =
    if (offset >= actions.size) Right(())
    else {
      val action = actions(offset)
      _validate_text(action.label, path + "[" + offset + "].label").flatMap { _ =>
        _validate_action_content(actions, path, offset + 1)
      }
    }

  private def _validate_detail_target(target: DetailTarget, listid: String, path: String): Either[RuntimeError, Unit] =
    if (target == null)
      Left(_error("LUI74_DETAIL_TARGET_INVALID", path, "missing DetailTarget"))
    else if (!_is_valid_id(target.screenId) || target.screenId == listid)
      Left(_error("LUI74_DETAIL_TARGET_INVALID", path + ".screenId", "missing, invalid, or self DetailTarget"))
    else Right(())

  private def _validate_role_value(role: PresentationRole, value: DisplayValue, path: String): Either[RuntimeError, Unit] =
    role match {
      case ContentRole => Right(())
      case TitleRole => value match {
        case TextValue(text) if _is_valid_text(text) => Right(())
        case TextValue(_) => Left(_error("LUI74_TEXT_INVALID", path + ".value.text", "TitleRole text must be nonblank"))
        case _ => Left(_error("LUI74_ROLE_INVALID", path + ".role", "TitleRole requires TextValue"))
      }
      case SubtitleRole | DescriptionRole | StatusRole => value match {
        case TextValue(_) => Right(())
        case _ => Left(_error("LUI74_ROLE_INVALID", path + ".role", "non-Content role requires TextValue"))
      }
      case _ => Left(_error("LUI74_ROLE_INVALID", path + ".role", "unknown presentation role"))
    }

  private def _validate_value(value: DisplayValue, path: String): Either[RuntimeError, Unit] =
    value match {
      case null => Left(_error("LUI74_VALUE_INVALID", path, "null display value"))
      case TextValue(text) if text == null => Left(_error("LUI74_VALUE_INVALID", path + ".text", "null text payload"))
      case NumberValue(number) if number == null => Left(_error("LUI74_VALUE_INVALID", path + ".number", "null numeric payload"))
      case TimestampValue(instant) if instant == null => Left(_error("LUI74_VALUE_INVALID", path + ".instant", "null timestamp payload"))
      case _: TextValue | _: NumberValue | _: BooleanValue | MissingValue | _: TimestampValue => Right(())
      case _ => Left(_error("LUI74_VALUE_INVALID", path, "unknown display value"))
    }

  private def _validate_optional_text(value: Option[String], path: String): Either[RuntimeError, Unit] =
    value match {
      case null => Left(_error("LUI74_ELEMENT_INVALID", path, "null Option container"))
      case None => Right(())
      case Some(null) => Left(_error("LUI74_ELEMENT_INVALID", path, "Some(null)"))
      case Some(text) => _validate_text(text, path)
    }

  private def _validate_id(value: String, path: String): Either[RuntimeError, Unit] =
    if (_is_valid_id(value)) Right(())
    else Left(_error("LUI74_ID_INVALID", path, "invalid ordinary ID"))

  private def _validate_text(value: String, path: String): Either[RuntimeError, Unit] =
    if (_is_valid_text(value)) Right(())
    else Left(_error("LUI74_TEXT_INVALID", path, "invalid label or title"))

  private def _is_valid_id(value: String): Boolean =
    value != null && value.nonEmpty && value == value.trim

  private def _is_valid_text(value: String): Boolean =
    value != null && value.trim.nonEmpty

  private def _error(code: String, path: String, reason: String): RuntimeError =
    RuntimeError(code, path, reason)
}
