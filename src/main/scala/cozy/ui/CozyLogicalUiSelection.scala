package cozy.ui

import cozy.ui.CozyLogicalUiRuntime._
import cozy.ui.CozyLogicalUiVocabulary._

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyLogicalUiSelection {
  sealed abstract class AdaptiveIntent
  case object CompactIntent extends AdaptiveIntent
  case object ExpandedIntent extends AdaptiveIntent

  sealed abstract class DetailPlacement
  case object NavigateToDetail extends DetailPlacement
  case object UpdateDetailRegion extends DetailPlacement

  final case class Selection(listId: String, itemId: String)

  final case class DetailRequest(screenId: String, resourceId: String)

  final case class Transition(
    selection: Selection,
    detailRequest: DetailRequest,
    placement: DetailPlacement
  ) {
    def pattern: InteractionPattern = placement match {
      case NavigateToDetail => NavigatePattern
      case UpdateDetailRegion => SelectPattern
    }
  }

  def select(model: Model, itemId: String, intent: AdaptiveIntent): Either[RuntimeError, Transition] =
    CozyLogicalUiRuntimeValidation.validate(model).flatMap { validmodel =>
      validmodel.screen match {
        case list: ResourceList =>
          if (!_is_valid_id(itemId))
            Left(_error("LUI74_SELECTION_INVALID", "itemId", "invalid or unknown item ID"))
          else if (!list.items.exists(_.id == itemId))
            Left(_error("LUI74_SELECTION_INVALID", "itemId", "invalid or unknown item ID"))
          else
            intent match {
              case CompactIntent => Right(_transition(list, itemId, NavigateToDetail))
              case ExpandedIntent => Right(_transition(list, itemId, UpdateDetailRegion))
              case _ => Left(_error("LUI74_ADAPTIVE_INTENT_INVALID", "intent", "null adaptive intent"))
            }
        case _: ResourceDetail =>
          Left(_error("LUI74_SELECTION_SCREEN_INVALID", "model.screen", "selection requested on a Detail"))
      }
    }

  private def _transition(list: ResourceList, itemid: String, placement: DetailPlacement): Transition =
    Transition(
      selection = Selection(list.id, itemid),
      detailRequest = DetailRequest(list.detailTarget.screenId, itemid),
      placement = placement
    )

  private def _is_valid_id(value: String): Boolean =
    value != null && value.nonEmpty && value == value.trim

  private def _error(code: String, path: String, reason: String): RuntimeError =
    RuntimeError(code, path, reason)
}
