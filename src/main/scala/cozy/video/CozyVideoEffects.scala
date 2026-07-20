package cozy.video

import io.circe.{Decoder, HCursor}
import org.goldenport.RAISE

/*
 * @since   Jul. 18, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoEffects {
  val DEFAULT_OPENING_PROFILE = "title-hold-subtle-motion"
  val DEFAULT_SECTION_START_PROFILE = "line-sweep"
  val DEFAULT_SUMMARY_PROFILE = "overview-and-conclusion"
  val DEFAULT_FINAL_PAGE_PROFILE = "end-card"

  final case class Settings(
    opening: Option[String],
    sectionStart: Option[String],
    summary: Option[String],
    finalPage: Option[String]
  )
  object Settings {
    implicit val decoder: Decoder[Settings] = (c: HCursor) =>
      for {
        opening <- c.downField("opening").as[Option[String]]
        sectionstart <- _optional_string(c, "sectionStart", "section-start")
        summary <- c.downField("summary").as[Option[String]]
        finalpage <- _optional_string(c, "finalPage", "final-page")
      } yield Settings(opening, sectionstart, summary, finalpage)
  }

  sealed trait Role { def key: String }
  object Role {
    case object Opening extends Role { val key = "opening" }
    case object SectionStart extends Role { val key = "section-start" }
    case object Summary extends Role { val key = "summary" }
    case object FinalPage extends Role { val key = "final-page" }

    val ALL: Vector[Role] = Vector(Opening, SectionStart, Summary, FinalPage)
  }

  final case class Primitive(name: String, parameters: Vector[(String, String)] = Vector.empty) {
    def display: String =
      if (parameters.isEmpty) name
      else s"$name(${parameters.map { case (key, value) => s"$key=$value" }.mkString(",")})"
  }

  final case class Expansion(role: Role, profile: String, primitives: Vector[Primitive]) {
    def display: String =
      if (primitives.isEmpty) "none"
      else primitives.map(_.display).mkString(" -> ")
  }

  final case class Capability(renderer: String, unsupported: Vector[String]) {
    def isSupported: Boolean = unsupported.isEmpty
    def status: String = if (isSupported) "supported" else "unsupported"
  }

  private val _profiles: Map[(Role, String), Vector[Primitive]] = Map(
    (Role.Opening, "none") -> Vector.empty,
    (Role.Opening, "title-hold-subtle-motion") -> Vector(
      Primitive("title-card"),
      Primitive("subtle-motion", Vector("scale" -> "1.025")),
      Primitive("hold", Vector("seconds" -> "4.5"))
    ),
    (Role.SectionStart, "none") -> Vector.empty,
    (Role.SectionStart, "line-sweep") -> Vector(
      Primitive("flow-line", Vector("direction" -> "left-to-right")),
      Primitive("underline-sweep")
    ),
    (Role.Summary, "none") -> Vector.empty,
    (Role.Summary, "overview-and-conclusion") -> Vector(
      Primitive("summary-layout", Vector("mode" -> "single-page")),
      Primitive("fade-rise", Vector("target" -> "overview")),
      Primitive("spring-pop", Vector("target" -> "conclusion"))
    ),
    (Role.FinalPage, "none") -> Vector.empty,
    (Role.FinalPage, "end-card") -> Vector(
      Primitive("end-card"),
      Primitive("fade-rise", Vector("target" -> "end-card")),
      Primitive("hold", Vector("seconds" -> "2.0"))
    )
  )

  // Renderer adapters add primitives here only after they consume the expanded contract.
  private val _renderer_capabilities: Map[String, Set[String]] = Map(
    "remotion" -> Set(
      "title-card",
      "subtle-motion",
      "flow-line",
      "underline-sweep",
      "summary-layout",
      "fade-rise",
      "spring-pop",
      "end-card",
      "hold"
    ),
    "simple-java2d" -> Set.empty,
    "legacy" -> Set.empty
  )

  def expand(settings: Option[Settings]): Vector[Expansion] =
    settings.toVector.flatMap { value =>
      Vector(
        _expand(Role.Opening, value.opening.getOrElse("none")),
        _expand(Role.SectionStart, value.sectionStart.getOrElse("none")),
        _expand(Role.Summary, value.summary.getOrElse("none")),
        _expand(Role.FinalPage, value.finalPage.getOrElse("none"))
      )
    }

  def validateProfile(role: Role, profile: String): String = {
    _expand(role, profile)
    profile
  }

  def capability(renderer: String, expansions: Vector[Expansion]): Capability = {
    val supported = _renderer_capabilities.getOrElse(renderer, Set.empty)
    val unsupported = expansions.flatMap(_.primitives.map(_.name)).filterNot(supported).distinct
    Capability(renderer, unsupported)
  }

  def validate(renderer: String, expansions: Vector[Expansion]): Unit = {
    val result = capability(renderer, expansions)
    if (!result.isSupported)
      RAISE.invalidArgumentFault(
        s"Video renderer $renderer does not support visual-effect primitives: ${result.unsupported.mkString(", ")}. " +
          "Set the corresponding visual-effects profiles to none or use a renderer that declares these capabilities."
      )
  }

  private def _expand(role: Role, profile: String): Expansion =
    _profiles.get(role -> profile).
      map(Expansion(role, profile, _)).
      getOrElse {
        val available = _profiles.keys.collect { case (`role`, name) => name }.toVector.sorted
        RAISE.invalidArgumentFault(
          s"Unknown ${role.key} visual-effect profile: $profile. Available profiles: ${available.mkString(", ")}"
        )
      }

  private def _optional_string(c: HCursor, camelname: String, kebabname: String): Decoder.Result[Option[String]] =
    c.downField(camelname).as[Option[String]].flatMap {
      case value @ Some(_) => Right(value)
      case None => c.downField(kebabname).as[Option[String]]
    }
}
