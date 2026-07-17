package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import org.goldenport.RAISE
import org.goldenport.cli.spec
import cozy.runtime.CozyCliArgs

/*
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoScaffold {
  final case class Config(
    slug: String,
    save: Path,
    title: String,
    profile: CompositionProfile,
    visualeffects: VisualEffectProfiles
  )
  object Config {
    def create(args: List[String]): Config = {
      val normalizedargs = _normalize_property_args(args)
      val parsed = CozyCliArgs.parseStrict(
        spec.Parameter.argument("slug"),
        spec.Parameter.property("save"),
        spec.Parameter.property("title"),
        spec.Parameter.property("profile"),
        spec.Parameter.property("section-start-effect"),
        spec.Parameter.property("summary-effect"),
        spec.Parameter.property("final-page-effect")
      )(normalizedargs)
      val rawslug = parsed.argument("slug").getOrElse(
        RAISE.invalidArgumentFault("Missing slug for video scaffold")
      )
      val slug = _normalize_slug(rawslug)
      val save = parsed.pathProperty("save").getOrElse(
        Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve(s"$slug.video")
      )
      Config(
        slug,
        save,
        parsed.property("title").getOrElse(_title(slug)),
        CompositionProfile.parse(parsed.property("profile").getOrElse(CompositionProfile.DEFAULT.key)),
        VisualEffectProfiles(
          _parse_section_start_effect(parsed.property("section-start-effect").getOrElse(_default_section_start_effect)),
          _parse_summary_effect(parsed.property("summary-effect").getOrElse(_default_summary_effect)),
          _parse_final_page_effect(parsed.property("final-page-effect").getOrElse(_default_final_page_effect))
        )
      )
    }
  }

  sealed trait CompositionProfile {
    def key: String
    def parts: Vector[ScaffoldPart]
  }
  object CompositionProfile {
    case object Explanation extends CompositionProfile {
      val key = "explanation"
      val parts = Vector(ScaffoldPart("explanation", "dialogue", "script.yaml", None))
    }
    case object ExplanationDemoExplanation extends CompositionProfile {
      val key = "explanation-demo-explanation"
      val parts = Vector(
        ScaffoldPart("introduction", "dialogue", "script.yaml", None),
        ScaffoldPart("demonstration", "web-demo", "demo-script.yaml", Some("demo-steps.json")),
        ScaffoldPart("conclusion", "dialogue", "summary-script.yaml", None)
      )
    }

    val DEFAULT: CompositionProfile = ExplanationDemoExplanation
    val ALL: Vector[CompositionProfile] = Vector(Explanation, ExplanationDemoExplanation)

    def parse(p: String): CompositionProfile =
      ALL.find(_.key == p).getOrElse(
        RAISE.invalidArgumentFault(s"Unknown video composition profile: $p. Available profiles: ${ALL.map(_.key).mkString(", ")}")
      )
  }

  final case class ScaffoldPart(id: String, kind: String, script: String, steps: Option[String])
  final case class VisualEffectProfiles(sectionstart: String, summary: String, finalpage: String)

  private val _default_section_start_effect = "line-sweep"
  private val _default_summary_effect = "overview-and-conclusion"
  private val _default_final_page_effect = "end-card"
  private val _section_start_effects = Vector(_default_section_start_effect, "none")
  private val _summary_effects = Vector(_default_summary_effect, "none")
  private val _final_page_effects = Vector(_default_final_page_effect, "none")

  def scaffold(config: Config): String = {
    val save = config.save.toAbsolutePath.normalize()
    if (Files.exists(save))
      RAISE.invalidArgumentFault(s"Video scaffold destination already exists: $save")
    Files.createDirectories(save.resolve("assets"))
    val scriptfiles = config.profile.parts.map(part => part.script -> _script_yaml(config, part))
    val files = Vector(
      "index.dox" -> _index_dox(config),
      "video.yaml" -> _video_yaml(config),
      "assets/README.md" -> _assets_readme,
      "assets/section-start.svg" -> _placeholder_svg("SECTION START"),
      "assets/summary.svg" -> _placeholder_svg("SUMMARY"),
      "assets/final-page.svg" -> _placeholder_svg("END")
    ) ++ scriptfiles ++ (if (config.profile.parts.exists(_.steps.isDefined)) Vector("demo-steps.json" -> _demo_steps_json) else Vector.empty)
    files.foreach { case (name, contents) =>
      val file = save.resolve(name)
      Option(file.getParent).foreach(Files.createDirectories(_))
      Files.writeString(file, contents, StandardCharsets.UTF_8)
    }
    Vector(
      "Cozy Video Scaffold",
      s"package: $save",
      s"profile: ${config.profile.key}",
      "files:",
      files.map(_._1).sorted.map(x => s"  - $x").mkString("\n")
    ).mkString("\n")
  }

  private def _normalize_property_args(args: List[String]): List[String] = {
    val propertynames = Set(
      "save",
      "title",
      "profile",
      "section-start-effect",
      "summary-effect",
      "final-page-effect"
    )
    args.flatMap {
      case x if x.startsWith("--") && x.contains("=") =>
        val keyvalue = x.drop(2).split("=", 2)
        if (keyvalue.length == 2 && propertynames.contains(keyvalue(0)))
          List("--" + keyvalue(0), keyvalue(1))
        else
          List(x)
      case x => List(x)
    }
  }

  private def _normalize_slug(p: String): String = {
    val candidate = p.trim.stripSuffix(".video")
    if (!candidate.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
      RAISE.invalidArgumentFault(s"Invalid video slug: $p. Use lowercase kebab-case.")
    candidate
  }

  private def _title(p: String): String =
    p.split('-').map(_.capitalize).mkString(" ")

  private def _parse_effect(role: String, value: String, available: Vector[String]): String =
    if (available.contains(value)) value
    else RAISE.invalidArgumentFault(s"Unknown $role visual-effect profile: $value. Available profiles: ${available.mkString(", ")}")

  private def _parse_section_start_effect(p: String): String =
    _parse_effect("section-start", p, _section_start_effects)

  private def _parse_summary_effect(p: String): String =
    _parse_effect("summary", p, _summary_effects)

  private def _parse_final_page_effect(p: String): String =
    _parse_effect("final-page", p, _final_page_effects)

  private def _index_dox(config: Config): String =
    s"""${config.title}
       |${"=" * config.title.length}
       |
       |This video package uses the `${config.profile.key}` composition profile.
       |
       |Replace the generated narration and project-owned placeholder assets before publication.
       |""".stripMargin

  private def _video_yaml(config: Config): String = {
    val parts = config.profile.parts.map { part =>
      Vector(
        s"  - id: ${part.id}",
        s"    type: ${part.kind}",
        s"    script: ${part.script}"
      ) ++ part.steps.map(x => s"    steps: $x")
    }.flatten.mkString("\n")
    s"""name: ${config.slug}
       |title: ${_yaml_string(config.title)}
       |output: build/${config.slug}.mp4
       |profile: ${config.profile.key}
       |visual-effects:
       |  section-start: ${config.visualeffects.sectionstart}
       |  summary: ${config.visualeffects.summary}
       |  final-page: ${config.visualeffects.finalpage}
       |assets:
       |  section-start: assets/section-start.svg
       |  summary: assets/summary.svg
       |  final-page: assets/final-page.svg
       |renderer:
       |  engine: remotion
       |parts:
       |$parts
       |""".stripMargin
  }

  private def _script_yaml(config: Config, part: ScaffoldPart): String = {
    val scene = Vector(
      s"  - id: ${part.id}",
      s"    narration: ${_yaml_string(s"Replace this ${part.id} narration.")}",
      s"    caption: ${_yaml_string(part.id.capitalize)}",
      "    duration: 8.0"
    ).mkString("\n")
    s"""title: ${_yaml_string(config.title)}
       |scenes:
       |$scene
       |""".stripMargin
  }

  private def _yaml_string(p: String): String =
    "\"" + p.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private val _assets_readme =
    """# Video assets
      |
      |The SVG files in this directory are generated, license-safe placeholders.
      |Replace them with project-owned assets and record the asset license and provenance here.
      |The scaffold does not copy or reference media from `0714.techfirst.lt/assets`.
      |""".stripMargin

  private val _demo_steps_json =
    """{
      |  "schema": "cozy.video.replay-script.v1",
      |  "manualReview": true,
      |  "viewport": {"width": 1280, "height": 720},
      |  "steps": []
      |}
      |""".stripMargin

  private def _placeholder_svg(label: String): String =
    s"""<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 1280 720" role="img" aria-label="$label placeholder">
       |  <rect width="1280" height="720" fill="#edf4f1"/>
       |  <rect x="48" y="48" width="1184" height="624" rx="24" fill="none" stroke="#2f6f68" stroke-width="4" stroke-dasharray="16 12"/>
       |  <text x="640" y="350" text-anchor="middle" font-family="sans-serif" font-size="54" font-weight="700" fill="#173f3b">$label</text>
       |  <text x="640" y="410" text-anchor="middle" font-family="sans-serif" font-size="24" fill="#456b66">PROJECT-OWNED ASSET SLOT</text>
       |</svg>
       |""".stripMargin
}
