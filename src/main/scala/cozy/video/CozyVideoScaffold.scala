package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import org.goldenport.RAISE
import org.goldenport.cli.spec
import cozy.runtime.CozyCliArgs

/*
 * @since   Jul. 18, 2026
 *  version Jul. 20, 2026
 * @version Aug. 26, 2026
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
        spec.Parameter.property("opening-effect"),
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
          CozyVideoEffects.validateProfile(
            CozyVideoEffects.Role.Opening,
            parsed.property("opening-effect").getOrElse(CozyVideoEffects.DEFAULT_OPENING_PROFILE)
          ),
          CozyVideoEffects.validateProfile(
            CozyVideoEffects.Role.SectionStart,
            parsed.property("section-start-effect").getOrElse(CozyVideoEffects.DEFAULT_SECTION_START_PROFILE)
          ),
          CozyVideoEffects.validateProfile(
            CozyVideoEffects.Role.Summary,
            parsed.property("summary-effect").getOrElse(CozyVideoEffects.DEFAULT_SUMMARY_PROFILE)
          ),
          CozyVideoEffects.validateProfile(
            CozyVideoEffects.Role.FinalPage,
            parsed.property("final-page-effect").getOrElse(CozyVideoEffects.DEFAULT_FINAL_PAGE_PROFILE)
          )
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
      val parts = Vector(ScaffoldPart("explanation", "dialogue", "explanation", None, None))
    }
    case object ExplanationDemoExplanation extends CompositionProfile {
      val key = "explanation-demo-explanation"
      val parts = Vector(
        ScaffoldPart("introduction", "dialogue", "introduction", None, None),
        ScaffoldPart("demonstration", "web-demo", "demonstration", Some("demo-steps.json"), Some("build/record/demonstration")),
        ScaffoldPart("conclusion", "dialogue", "conclusion", None, None)
      )
    }

    val DEFAULT: CompositionProfile = ExplanationDemoExplanation
    val ALL: Vector[CompositionProfile] = Vector(Explanation, ExplanationDemoExplanation)

    def parse(p: String): CompositionProfile =
      ALL.find(_.key == p).getOrElse(
        RAISE.invalidArgumentFault(s"Unknown video composition profile: $p. Available profiles: ${ALL.map(_.key).mkString(", ")}")
      )
  }

  final case class ScaffoldPart(id: String, kind: String, storyboardSection: String, steps: Option[String], recordDir: Option[String])
  final case class VisualEffectProfiles(opening: String, sectionstart: String, summary: String, finalpage: String)

  def scaffold(config: Config): String = {
    val save = config.save.toAbsolutePath.normalize()
    if (Files.exists(save))
      RAISE.invalidArgumentFault(s"Video scaffold destination already exists: $save")
    Files.createDirectories(save.resolve("assets"))
    val storyboard = _storyboard(config)
    val files = Vector(
      ".gitignore" -> _gitignore,
      "index.dox" -> _index_dox(config),
      "video.yaml" -> _video_yaml(config, storyboard),
      "storyboard.md" -> CozyVideo.canonicalStoryboardMarkdown(storyboard),
      "assets/README.md" -> _assets_readme,
      "assets/opening.svg" -> _placeholder_svg("OPENING"),
      "assets/section-start.svg" -> _placeholder_svg("SECTION START"),
      "assets/summary.svg" -> _placeholder_svg("SUMMARY"),
      "assets/final-page.svg" -> _placeholder_svg("END")
    ) ++ (if (config.profile.parts.exists(_.steps.isDefined)) Vector("demo-steps.json" -> _demo_steps_json) else Vector.empty)
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
      "opening-effect",
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

  private def _index_dox(config: Config): String =
    s"""${config.title}
       |${"=" * config.title.length}
       |
       |This video package uses the `${config.profile.key}` composition profile.
       |
       |Replace the generated narration and project-owned placeholder assets before publication.
       |""".stripMargin

  private def _video_yaml(config: Config, storyboard: CozyVideo.Storyboard): String = {
    val parts = config.profile.parts.map { part =>
      Vector(
        s"  - id: ${part.id}",
        s"    type: ${part.kind}",
        "    storyboard: storyboard.md",
        s"    storyboardSection: ${part.storyboardSection}"
      ) ++ part.steps.map(x => s"    steps: $x") ++ part.recordDir.map(x => s"    recordDir: $x")
    }.flatten.mkString("\n")
    s"""name: ${config.slug}
       |title: ${_yaml_string(config.title)}
       |locale: en
       |output: build/${config.slug}.mp4
       |profile: ${config.profile.key}
       |credits:
       |  include: []
       |  exclude: []
       |visual-effects:
       |  opening: ${config.visualeffects.opening}
       |  section-start: ${config.visualeffects.sectionstart}
       |  summary: ${config.visualeffects.summary}
       |  final-page: ${config.visualeffects.finalpage}
       |assets:
       |${_asset_yaml("opening", "assets/opening.svg")}
       |${_asset_yaml("section-start", "assets/section-start.svg")}
       |${_asset_yaml("summary", "assets/summary.svg")}
       |${_asset_yaml("final-page", "assets/final-page.svg")}
       |renderer:
       |  engine: remotion
       |  policy: lightweight
       |narration:
       |  provider: voicevox
       |voice:
       |  fallbackSpeakerId: 0
       |storyboardReview:
       |  source: storyboard.md
       |  approvedIdentity: ${CozyVideo.storyboardIdentity(storyboard)}
       |parts:
       |$parts
       |""".stripMargin
  }

  private def _storyboard(config: Config): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v1",
      1,
      config.profile.parts.zipWithIndex.map { case (part, index) =>
        CozyVideo.StoryboardScene(
          part.id,
          index + 1,
          part.storyboardSection,
          "narrator",
          "narration",
          s"Replace this ${part.id} narration.",
          CozyVideo.StoryboardScreen(part.id.capitalize, s"${part.id.capitalize} scene."),
          part.id.capitalize,
          BigDecimal("8.0"),
          BigDecimal("0.0"),
          "none",
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          s"Replace this ${part.id} scene."
        )
      }
    )

  private def _yaml_string(p: String): String =
    "\"" + p.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _asset_yaml(slot: String, path: String): String =
    s"""  $slot:
       |    path: $path
       |    kind: placeholder
       |    required: false
       |    license: ${CozyVideoAssets.GENERATED_PLACEHOLDER_LICENSE}
       |    provenance: ${CozyVideoAssets.GENERATED_PLACEHOLDER_PROVENANCE}""".stripMargin

  private val _assets_readme =
    """# Video assets
      |
      |The SVG files in this directory are generated, license-safe placeholders.
      |Replace a slot's `path` with a project-owned file and record its `kind`, `license`, and `provenance` in `video.yaml`.
      |Set `required: true` when rendering must stop rather than use the generated placeholder if that configured file is absent.
      |Additional asset IDs may declare `tags`, `credits`, and `credit-obligation` as semantic credit evidence without becoming renderer slots.
      |Asset paths are project-relative local files. Cozy does not fetch asset URLs during inspect, build, or render.
      |The scaffold does not copy or reference media from `0714.techfirst.lt/assets`.
      |""".stripMargin

  private val _gitignore =
    """build/
      |target/
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
