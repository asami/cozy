package cozy.video

import cozy.CozySpecVocabulary
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardBuildSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video Storyboard planning" should {
    "preserve full-Storyboard planning when a Storyboard part has no section selector" in {
      _with_temp_dir("markdown") { root =>
        Given("an approved Storyboard Markdown project with no legacy script source or section selector")
        val storyboard = _storyboard()
        val source = root.resolve("storyboard.md")
        Files.writeString(source, CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
        val project = _write_project(root, "storyboard.md", CozyVideo.storyboardIdentity(storyboard))

        When("public inspection and dry-run build plan the selected Storyboard")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(project, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val dryrun = CozyVideo.build(
          CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )

        Then("one supported renderable part reports its projected scene summary and render route")
        Files.exists(root.resolve("script.json")) shouldBe false
        inspection should include("part[1]: storyboard")
        inspection should include("type: storyboard")
        inspection should include("scriptStatus: found")
        inspection should include("scenes: 2")
        inspection should include("expandedScenes: 2")
        inspection should include("estimatedDuration: 7.75")
        dryrun should include("part.storyboard.synthesize")
        dryrun should include("part.storyboard.render")
      }
    }

    "plan exactly the scenes selected by a Storyboard section selector" in {
      _with_temp_dir("selected-section") { root =>
        Given("an approved Storyboard part selecting its detail section")
        val base = _storyboard()
        val storyboard = base.copy(
          scenes = base.scenes :+ base.scenes(1).copy(
            id = "detail-follow-up",
            order = 3,
            narration = "The detail concludes.",
            caption = "Detail conclusion",
            duration = BigDecimal("2.5"),
            direction = "Conclude the details"
          )
        )
        val source = root.resolve("storyboard.md")
        Files.writeString(source, CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
        val project = _write_project(root, "storyboard.md", CozyVideo.storyboardIdentity(storyboard), Some("detail"))

        When("public planning projects the selected section")
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(project, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val plan = CozyVideoImplementation._plan(project, None, None)

        Then("the original order, selected pronunciation, and planning metadata belong only to detail")
        inspection should include("scenes: 2")
        inspection should include("expandedScenes: 2")
        inspection should include("estimatedDuration: 6.75")
        plan.parts.head.script.get.scenes.map(_.id) shouldBe
          Vector(Some("detail"), Some("detail-follow-up"))
        plan.parts.head.script.get.pronunciations shouldBe Map("Reimu" -> "れいむ")
        plan.parts.head.script.get.scenes.head.section shouldBe Some("detail")
      }
    }

    "project equivalent JSON and Markdown Storyboards into the same observable planning semantics" in {
      _with_temp_dir("equivalent") { root =>
        Given("equivalent approved Markdown and JSON Storyboard projects")
        val storyboard = _storyboard()
        val markdownroot = root.resolve("markdown")
        val jsonroot = root.resolve("json")
        Files.createDirectories(markdownroot)
        Files.createDirectories(jsonroot)
        val markdown = markdownroot.resolve("storyboard.md")
        val json = jsonroot.resolve("storyboard.json")
        Files.writeString(markdown, CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
        Files.writeString(json, CozyVideo.canonicalStoryboardJson(storyboard), StandardCharsets.UTF_8)
        val markdownproject = _write_project(markdownroot, "storyboard.md", CozyVideo.storyboardIdentity(storyboard))
        val jsonproject = _write_project(jsonroot, "storyboard.json", CozyVideo.storyboardIdentity(storyboard))

        When("both sources are planned through the public video surface")
        val markdowninspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(markdownproject, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val jsoninspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(jsonproject, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val markdowndryrun = CozyVideo.build(
          CozyVideo.BuildConfig(markdownproject, dryRun = true, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val jsondryrun = CozyVideo.build(
          CozyVideo.BuildConfig(jsonproject, dryRun = true, checkTools = false, mode = Some("confirmation")),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )

        Then("both sources expose the same renderable planning semantics")
        val expectedinspection = Vector(
          "part[1]: storyboard",
          "type: storyboard",
          "scriptStatus: found",
          "scenes: 2",
          "expandedScenes: 2",
          "estimatedDuration: 7.75"
        )
        expectedinspection.foreach { line =>
          markdowninspection should include(line)
          jsoninspection should include(line)
        }
        _command_steps(markdowndryrun) shouldBe Vector(
          "part.storyboard.parse-script",
          "part.storyboard.prepare-visuals",
          "part.storyboard.synthesize",
          "part.storyboard.render",
          "part.storyboard.manifest"
        )
        _command_steps(markdowndryrun) shouldBe _command_steps(jsondryrun)
      }
    }

    "reject a part that declares both legacy script and Storyboard sources deterministically" in {
      _with_temp_dir("disjoint") { root =>
        Given("a project part declaring both mutually exclusive source routes")
        val project = root.resolve("video.json")
        Files.writeString(
          project,
          """{
            |  "parts": [
            |    {"id": "conflict", "type": "dialogue", "script": "legacy.json", "storyboard": "storyboard.md"}
            |  ]
            |}
            |""".stripMargin,
          StandardCharsets.UTF_8
        )

        When("the public inspector decodes the project")
        val failure = intercept[Exception] {
          CozyVideo.inspect(
            CozyVideo.InspectConfig(project, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }

        Then("the source-selection conflict is rejected without choosing either route")
        failure.getMessage should include("Video part must not declare both script and storyboard")
      }
    }

    "reject invalid and unmatched Storyboard section selectors deterministically" in {
      _with_temp_dir("invalid-section") { root =>
        Given("an approved Storyboard source")
        val storyboard = _storyboard()
        val source = root.resolve("storyboard.md")
        Files.writeString(source, CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)

        When("a selector violates the stable section token grammar")
        val invalid = _write_project(root, "storyboard.md", CozyVideo.storyboardIdentity(storyboard), Some("not a section"))
        val invalidfailure = intercept[Exception] {
          CozyVideo.inspect(
            CozyVideo.InspectConfig(invalid, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }

        And("a valid selector does not match any Storyboard scene")
        val unmatched = _write_project(root, "storyboard.md", CozyVideo.storyboardIdentity(storyboard), Some("missing"))
        val unmatchedfailure = intercept[Exception] {
          CozyVideo.inspect(
            CozyVideo.InspectConfig(unmatched, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }

        Then("decoding and planning reject their respective selector failures without fallback")
        invalidfailure.getMessage should include("storyboardSection must be a non-empty stable token")
        unmatchedfailure.getMessage should include("Storyboard section missing selects no scenes")
      }
    }

    "reject a Storyboard section selector outside a Storyboard part" in {
      _with_temp_dir("legacy-section-selector") { root =>
        Given("a legacy script part declaring a Storyboard-only selector")
        val project = root.resolve("video.json")
        Files.writeString(
          project,
          """{
            |  "parts": [
            |    {"id": "legacy", "type": "dialogue", "script": "legacy.json", "storyboardSection": "opening"}
            |  ]
            |}
            |""".stripMargin,
          StandardCharsets.UTF_8
        )

        When("the public inspector decodes the legacy part")
        val failure = intercept[Exception] {
          CozyVideo.inspect(
            CozyVideo.InspectConfig(project, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }

        Then("the selector is rejected rather than being ignored by legacy planning")
        failure.getMessage should include("storyboardSection requires storyboard")
      }
    }

    "reject a symbolic-link Storyboard source without adopting its target" in {
      _with_temp_dir("symbolic-link") { root =>
        Given("a project-local Storyboard source represented by a symbolic link")
        val storyboard = _storyboard()
        val source = root.resolve("source.md")
        val link = root.resolve("storyboard.md")
        Files.writeString(source, CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
        Files.createSymbolicLink(link, source.getFileName)
        val project = _write_project(root, "storyboard.md", CozyVideo.storyboardIdentity(storyboard))

        When("the public inspector plans the Storyboard part")
        val failure = intercept[Exception] {
          CozyVideo.inspect(
            CozyVideo.InspectConfig(project, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }

        Then("the symbolic link is rejected rather than loading its target")
        failure.getMessage should include("Storyboard source must not be a symbolic link")
      }
    }

    "reject a Storyboard source under a symbolic-link parent before loading its target" in {
      _with_temp_dir("symbolic-link-parent") { root =>
        Given("a Storyboard path whose parent directory links outside the project")
        val storyboard = _storyboard()
        val outside = root.resolveSibling(root.getFileName.toString + "-outside")
        Files.createDirectories(outside)
        Files.writeString(outside.resolve("storyboard.md"), CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
        Files.createSymbolicLink(root.resolve("sources"), outside)
        val project = _write_project(root, "sources/storyboard.md", CozyVideo.storyboardIdentity(storyboard))

        When("the public inspector plans the Storyboard part")
        val failure = intercept[Exception] {
          CozyVideo.inspect(
            CozyVideo.InspectConfig(project, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }

        Then("planning fails closed at the symbolic parent before loading the outside source")
        failure.getMessage should include("Storyboard source must not use a symbolic-link path")
        Files.deleteIfExists(outside.resolve("storyboard.md"))
        Files.deleteIfExists(outside)
      }
    }

    "retain project narration configuration in the Storyboard projection" in {
      _with_temp_dir("project-narration") { root =>
        Given("an approved Storyboard project with explicit project-level narration and character configuration")
        val storyboard = _storyboard()
        val source = root.resolve("storyboard.md")
        Files.writeString(source, CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
        val project = _write_project(
          root,
          "storyboard.md",
          CozyVideo.storyboardIdentity(storyboard),
          configuration = """  "narration": {"provider": "voicevox"},
                          |  "voice": {"fallbackSpeakerId": 7},
                          |  "voiceTextNormalization": {"dictionary": {"Cozy": "コージー"}},
                          |  "characters": {"narrator": {"voice": {"fallbackSpeakerId": 9}}},
                          |""".stripMargin
        )

        When("public planning projects the selected Storyboard into its execution script")
        val script = CozyVideoImplementation._plan(project, None, None).parts.head.script.get

        Then("the exact project configuration supplies narration selection, cache input, normalization, and characters")
        script.narration.hcursor.get[String]("provider").toOption shouldBe Some("voicevox")
        script.voice.hcursor.get[Int]("fallbackSpeakerId").toOption shouldBe Some(7)
        script.voiceTextNormalization.hcursor.downField("dictionary").get[String]("Cozy").toOption shouldBe Some("コージー")
        script.characters("narrator").hcursor.downField("voice").get[Int]("fallbackSpeakerId").toOption shouldBe Some(9)
        CozyVideoImplementation._resolve_narration_selection(script).provider shouldBe "voicevox"
        CozyVideoImplementation._narration_voice_identity("voicevox", script.voice) shouldBe "speaker-id:7"
      }
    }

    "retain ordered Storyboard pronunciation notes and reject conflicting duplicate readings" in {
      _with_temp_dir("ordered-pronunciations") { root =>
        Given("an approved Storyboard with ordered duplicate-compatible notes and a conflicting variant")
        val storyboard = _storyboard().copy(scenes = _storyboard().scenes.map { scene =>
          scene.copy(pronunciationNotes = Vector(
            CozyVideo.StoryboardPronunciationNote("Cozy", "コージー"),
            CozyVideo.StoryboardPronunciationNote("Reimu", "れいむ"),
            CozyVideo.StoryboardPronunciationNote("Cozy", "コージー")
          ))
        })
        val source = root.resolve("storyboard.md")
        Files.writeString(source, CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
        val project = _write_project(root, "storyboard.md", CozyVideo.storyboardIdentity(storyboard))
        val conflicting = storyboard.copy(scenes = storyboard.scenes.updated(1, storyboard.scenes(1).copy(
          pronunciationNotes = Vector(CozyVideo.StoryboardPronunciationNote("Cozy", "こーずぃー"))
        )))

        When("planning first projects the ordered notes and then loads the conflicting Storyboard")
        val script = CozyVideoImplementation._plan(project, None, None).parts.head.script.get
        Files.writeString(source, CozyVideo.canonicalStoryboardMarkdown(conflicting), StandardCharsets.UTF_8)
        val failure = intercept[Exception] {
          CozyVideoImplementation._plan(project, None, None)
        }

        Then("the projection preserves source order while conflicting readings fail before rendering")
        script.storyboardPronunciationNotes.map(note => note.surface -> note.reading) shouldBe Vector(
          "Cozy" -> "コージー", "Reimu" -> "れいむ", "Cozy" -> "コージー",
          "Cozy" -> "コージー", "Reimu" -> "れいむ", "Cozy" -> "コージー"
        )
        script.pronunciations shouldBe Map("Cozy" -> "コージー", "Reimu" -> "れいむ")
        failure.getMessage should include("Storyboard pronunciation surface has conflicting readings: Cozy")
      }
    }

    "reject duplicate effective part IDs before planning generated paths" in {
      _with_temp_dir("duplicate-part-id") { root =>
        Given("a project whose explicit first ID collides with the second generated default ID")
        val project = root.resolve("video.json")
        Files.writeString(
          project,
          """{
            |  "parts": [
            |    {"id": "part-02", "type": "storyboard", "storyboard": "storyboard.md"},
            |    {"type": "storyboard", "storyboard": "storyboard.md"}
            |  ]
            |}
            |""".stripMargin,
          StandardCharsets.UTF_8
        )

        When("the public inspector decodes the project")
        val failure = intercept[Exception] {
          CozyVideo.inspect(
            CozyVideo.InspectConfig(project, checkTools = false),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )
        }

        Then("the collision is rejected before any generated Storyboard handoff path exists")
        failure.getMessage should include("Duplicate effective video part id: part-02")
        Files.exists(root.resolve("target/cozy-video")) shouldBe false
      }
    }
  }

  private def _storyboard(): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v1",
      1,
      Vector(
        CozyVideo.StoryboardScene(
          "opening",
          1,
          "introduction",
          "narrator",
          "narration",
          "Welcome to Cozy.",
          CozyVideo.StoryboardScreen("Welcome", "Cozy overview"),
          "Welcome",
          BigDecimal("3.5"),
          BigDecimal("0.25"),
          "fade",
          Vector(CozyVideo.StoryboardProductionInsert("title-card", "overlay", "Welcome")),
          Vector("assets/overview.svg"),
          Vector("assets/opening.png"),
          Vector(CozyVideo.StoryboardPronunciationNote("Cozy", "コージー")),
          "Fade in the title"
        ),
        CozyVideo.StoryboardScene(
          "detail",
          2,
          "detail",
          "reimu",
          "dialogue",
          "The detail follows.",
          CozyVideo.StoryboardScreen("Detail", "Details"),
          "Detail",
          BigDecimal("4.25"),
          BigDecimal("0.5"),
          "cut",
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector(CozyVideo.StoryboardPronunciationNote("Reimu", "れいむ")),
          "Show the details"
        )
      )
    )

  private def _write_project(root: Path, source: String, identity: String, storyboardsection: Option[String] = None, configuration: String = ""): Path = {
    Files.createDirectories(root)
    val project = root.resolve("video.json")
    val selector = storyboardsection.map(value => s""", "storyboardSection": "$value"""").getOrElse("")
    Files.writeString(
      project,
      s"""{
         |$configuration  "storyboardReview": {"source": "$source", "approvedIdentity": "$identity"},
         |  "parts": [
         |    {"id": "storyboard", "type": "storyboard", "storyboard": "$source"$selector}
         |  ]
         |}
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    project
  }

  private def _command_steps(output: String): Vector[String] =
    output.linesIterator.collect {
      case line if line.startsWith("  - part.storyboard.") =>
        line.drop("  - ".length).takeWhile(_ != ':')
    }.toVector

  private def _with_temp_dir[A](label: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-video-storyboard-build-" + label)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
}
