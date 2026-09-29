package cozy.ui

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyLogicalUiVocabulary {
  sealed trait Purpose {
    def id: String
  }

  case object BrowsePurpose extends Purpose {
    val id = "browse"
  }

  case object InspectPurpose extends Purpose {
    val id = "inspect"
  }

  case object EditPurpose extends Purpose {
    val id = "edit"
  }

  case object ConfirmPurpose extends Purpose {
    val id = "confirm"
  }

  sealed trait Display {
    def id: String
  }

  case object CollectionDisplay extends Display {
    val id = "collection"
  }

  case object DetailDisplay extends Display {
    val id = "detail"
  }

  case object FormDisplay extends Display {
    val id = "form"
  }

  case object StatusDisplay extends Display {
    val id = "status"
  }

  sealed trait InteractionPattern {
    def id: String
  }

  case object NavigatePattern extends InteractionPattern {
    val id = "navigate"
  }

  case object SelectPattern extends InteractionPattern {
    val id = "select"
  }

  case object InputPattern extends InteractionPattern {
    val id = "input"
  }

  case object CommandPattern extends InteractionPattern {
    val id = "command"
  }

  case object ObservePattern extends InteractionPattern {
    val id = "observe"
  }
}
