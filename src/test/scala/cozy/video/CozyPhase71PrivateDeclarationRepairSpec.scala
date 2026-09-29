package cozy.video

import cozy.CozySpecVocabulary
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyPhase71PrivateDeclarationRepairSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "The PHASE-71 private declaration repair" should {
    "select the native nine-scene Storyboard while preserving the declared video contract" in {
      _with_fixture("native-plan") { fixture =>
        Given("a task-private old dialogue declaration, a nine-scene Storyboard, four supplied SVG assets, and a minimal credit profile")
        val oldvideo = _read(fixture.video)
        val oldstoryboard = _read(fixture.storyboard)

        When("the exact repair script and the native typed planning surface run")
        val repair = _run_script(fixture)
        val inspection = CozyVideo.inspect(
          CozyVideo.InspectConfig(fixture.project, checkTools = false),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
        val plan = CozyVideoImplementation._plan(fixture.project, None, None)

        Then("the private pair is repaired to one supported native Storyboard part with the frozen scene and asset selections")
        repair.exitcode shouldBe 0
        _read(fixture.video) should include("    type: storyboard")
        _read(fixture.video) should include("    storyboard: storyboard.md")
        _read(fixture.video) should include("    storyboardSection: explanation")
        _read(fixture.video) should not include "    script: script.json"
        _read(fixture.storyboard) shouldBe _native_storyboard_text(oldstoryboard)
        _read(fixture.video) shouldBe _native_video_text(oldvideo)
        _read(fixture.video) should include("  section-start:\n    path: assets/section-start.svg")
        inspection should include("part[1]: dialogue")
        inspection should include("type: storyboard")
        inspection should include("scenes: 9")
        plan.parts.size shouldBe 1
        plan.parts.head.id shouldBe "dialogue"
        plan.parts.head.partType shouldBe "storyboard"
        plan.parts.head.supported shouldBe true
        plan.parts.head.renderable shouldBe true
        plan.parts.head.scriptName shouldBe Some("storyboard.md")
        plan.parts.head.script.get.scenes.map(_.id.get) shouldBe _scene_ids
        plan.parts.head.script.get.scenes.map(_.section).distinct shouldBe Vector(Some("explanation"))
        plan.encoding.fps shouldBe 18
        plan.encoding.width shouldBe 1280
        plan.encoding.height shouldBe 720
        plan.parts.head.outputPath shouldBe fixture.destination.resolve("src/main/media/development-process/ai-development-harness/video/ja/build/parts/dialogue.mp4")
        plan.parts.head.audioDir shouldBe Some(fixture.destination.resolve("src/main/media/development-process/ai-development-harness/video/ja/build/audio/dialogue"))
        plan.assets.map(_.role.key) shouldBe Vector("opening", "section-start", "summary", "final-page")
        plan.assets.map(_.status) shouldBe Vector("configured", "configured", "configured", "configured")
        plan.project.renderer.map(_.engine) shouldBe Some(Some("remotion"))
        plan.project.tools.flatMap(_.voicevoxUrl) shouldBe Some("http://127.0.0.1:50021")
        plan.project.credits.map(_.profile) shouldBe Some(Some("fixture"))
        oldvideo should include("    audioDir: build/audio/dialogue")
        oldvideo should include("    output: build/parts/dialogue.mp4")
      }
    }

    "make a repeated repair a byte, mode, and nanosecond timestamp no-op" in {
      _with_fixture("idempotent") { fixture =>
        Given("a private old declaration and a prior MP4 sentinel outside the two admitted source files")
        val sentinel = fixture.video.getParent.resolve("build/ai-development-harness-ja.mp4")
        _write(sentinel, "prior-output-sentinel\n")

        When("the old pair is repaired once")
        val first = _run_script(fixture)

        Then("the first repair succeeds")
        first.exitcode shouldBe 0

        Given("the exact repaired bytes, POSIX modes, FileTimes, and prior output after the first repair")
        val videobytes = Files.readAllBytes(fixture.video)
        val storyboardbytes = Files.readAllBytes(fixture.storyboard)
        val videomode = Files.getPosixFilePermissions(fixture.video)
        val storyboardmode = Files.getPosixFilePermissions(fixture.storyboard)
        val videotime = Files.getLastModifiedTime(fixture.video, LinkOption.NOFOLLOW_LINKS)
        val storyboardtime = Files.getLastModifiedTime(fixture.storyboard, LinkOption.NOFOLLOW_LINKS)
        val sentinelbytes = Files.readAllBytes(sentinel)

        When("the already repaired pair is processed again")
        val second = _run_script(fixture)

        Then("the script reports reuse without writing and preserves every observed byte, mode, nanosecond FileTime, and prior output")
        second.exitcode shouldBe 0
        second.output should include("reused without write")
        Files.readAllBytes(fixture.video) shouldBe videobytes
        Files.readAllBytes(fixture.storyboard) shouldBe storyboardbytes
        Files.getPosixFilePermissions(fixture.video) shouldBe videomode
        Files.getPosixFilePermissions(fixture.storyboard) shouldBe storyboardmode
        Files.getLastModifiedTime(fixture.video, LinkOption.NOFOLLOW_LINKS) shouldBe videotime
        Files.getLastModifiedTime(fixture.storyboard, LinkOption.NOFOLLOW_LINKS) shouldBe storyboardtime
        Files.readAllBytes(sentinel) shouldBe sentinelbytes
      }
    }

    "refuse wrong destinations and every admitted symlink boundary before unrelated writes" in {
      val variants = Vector("wrong-destination", "parent-alias", "symlink-root", "symlink-source", "symlink-asset", "symlink-ancestor")
      variants.foreach { variant =>
        _with_fixture(variant) { fixture =>
          Given("a private old pair and one unsafe destination, root, source, asset, or ancestor boundary")
          val beforevideo = Files.readAllBytes(fixture.video)
          val beforestoryboard = Files.readAllBytes(fixture.storyboard)
          val wrongdestination = fixture.root.resolve("target/phase71-private-driver/wrong")
          Files.createDirectories(wrongdestination)

          When("the repair script receives the selected safety-boundary variant")
          val repair = variant match {
            case "wrong-destination" => _run_script(fixture.root, wrongdestination)
            case "parent-alias" => _run_script(fixture.root, fixture.destination.resolve("src/main/media/development-process/ai-development-harness/video/ja/../ja"))
            case "symlink-root" => _with_symlink(fixture.root)(() => _run_script(fixture.root, fixture.destination))
            case "symlink-source" => _with_symlink(fixture.video)(() => _run_script(fixture))
            case "symlink-asset" => _with_symlink(fixture.assetroot.resolve("opening.svg"))(() => _run_script(fixture))
            case "symlink-ancestor" => _with_symlink(fixture.assetroot)(() => _run_script(fixture))
          }

          Then("the boundary is rejected without changing either admitted source or creating a prior-output replacement")
          repair.exitcode should not be 0
          Files.readAllBytes(fixture.video) shouldBe beforevideo
          Files.readAllBytes(fixture.storyboard) shouldBe beforestoryboard
          Files.exists(fixture.video.getParent.resolve("build/ai-development-harness-ja.mp4"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "reject each missing supplied asset before either source installation" in {
      Vector("opening", "section-start", "summary", "final-page").foreach { asset =>
        _with_fixture("missing-" + asset) { fixture =>
          Given("an old coherent pair, a prior MP4 sentinel, and missing required " + asset + ".svg")
          val sentinel = fixture.video.getParent.resolve("build/ai-development-harness-ja.mp4")
          _write(sentinel, "prior-output-sentinel\n")
          val beforevideo = Files.readAllBytes(fixture.video)
          val beforestoryboard = Files.readAllBytes(fixture.storyboard)
          val beforevideomode = Files.getPosixFilePermissions(fixture.video)
          val beforestoryboardmode = Files.getPosixFilePermissions(fixture.storyboard)
          val beforevideotime = Files.getLastModifiedTime(fixture.video, LinkOption.NOFOLLOW_LINKS)
          val beforestoryboardtime = Files.getLastModifiedTime(fixture.storyboard, LinkOption.NOFOLLOW_LINKS)
          val beforesentinel = Files.readAllBytes(sentinel)
          Files.delete(fixture.assetroot.resolve(asset + ".svg"))

          When("the repair script validates its required " + asset + " asset")
          val repair = _run_script(fixture)

          Then("the missing " + asset + " asset fails before installation and preserves both sources and the prior output")
          repair.exitcode should not be 0
          Files.readAllBytes(fixture.video) shouldBe beforevideo
          Files.readAllBytes(fixture.storyboard) shouldBe beforestoryboard
          Files.getPosixFilePermissions(fixture.video) shouldBe beforevideomode
          Files.getPosixFilePermissions(fixture.storyboard) shouldBe beforestoryboardmode
          Files.getLastModifiedTime(fixture.video, LinkOption.NOFOLLOW_LINKS) shouldBe beforevideotime
          Files.getLastModifiedTime(fixture.storyboard, LinkOption.NOFOLLOW_LINKS) shouldBe beforestoryboardtime
          Files.readAllBytes(sentinel) shouldBe beforesentinel
        }
      }
    }

    "reject missing, conflicting, duplicate, and partially repaired declarations without half repair" in {
      val variants = Vector(
        "missing-selector" -> { fixture: Fixture => _write(fixture.video, _read(fixture.video).replace("    script: script.json\n", "")) },
        "conflicting-selectors" -> { fixture: Fixture => _write(fixture.video, _read(fixture.video).replace("    type: dialogue", "    type: storyboard")) },
        "duplicate-selector" -> { fixture: Fixture => _write(fixture.video, _read(fixture.video).replace("    script: script.json\n", "    script: script.json\n    script: script.json\n")) },
        "partial-storyboard" -> { fixture: Fixture => _write(fixture.storyboard, _read(fixture.storyboard).replace("asset-refs: [\"opening\"]", "asset-refs: [\"assets/opening.svg\"]")) }
      )
      variants.foreach { case (variant, mutate) =>
        _with_fixture(variant) { fixture =>
          Given("a private old pair with one incomplete or conflicting declaration mutation")
          mutate(fixture)
          val beforevideo = Files.readAllBytes(fixture.video)
          val beforestoryboard = Files.readAllBytes(fixture.storyboard)

          When("the repair script validates the declaration pair")
          val repair = _run_script(fixture)

          Then("the unsupported state fails before any half repair is installed")
          repair.exitcode should not be 0
          Files.readAllBytes(fixture.video) shouldBe beforevideo
          Files.readAllBytes(fixture.storyboard) shouldBe beforestoryboard
        }
      }
    }

    "reject an incorrect scene sequence or non-role asset reference" in {
      val variants = Vector(
        "wrong-scene" -> { fixture: Fixture => _write(fixture.storyboard, _read(fixture.storyboard).replace("id: \"speed\"", "id: \"speed-moved\"")) },
        "wrong-reference" -> { fixture: Fixture => _write(fixture.storyboard, _read(fixture.storyboard).replace("asset-refs: [\"opening\"]", "asset-refs: [\"unknown\"]")) },
        "duplicate-reference" -> { fixture: Fixture => _write(fixture.storyboard, _read(fixture.storyboard).replace("asset-refs: [\"opening\"]", "asset-refs: [\"opening\", \"summary\"]")) }
      )
      variants.foreach { case (variant, mutate) =>
        _with_fixture(variant) { fixture =>
          Given("a private old pair whose Storyboard scene or asset reference violates the frozen v1 contract")
          mutate(fixture)
          val beforevideo = Files.readAllBytes(fixture.video)
          val beforestoryboard = Files.readAllBytes(fixture.storyboard)

          When("the repair script validates the restricted Storyboard grammar")
          val repair = _run_script(fixture)

          Then("the incorrect source is refused without a descriptor-only or Storyboard-only installation")
          repair.exitcode should not be 0
          Files.readAllBytes(fixture.video) shouldBe beforevideo
          Files.readAllBytes(fixture.storyboard) shouldBe beforestoryboard
        }
      }
    }

    "preserve complete Storyboard source text for varied safe narrative metacharacters" in {
      Given("at least ten safe narrative values containing shell and awk metacharacters")
      val narratives = Gen.oneOf(
        "Narrative with $dollar, `backticks`, and $(not-a-command).",
        "Narrative with && pipes || semicolons; and [brackets].",
        "Narrative with \\\\slashes, 'quotes', and \"double quotes\".",
        "Narrative with question? stars* and braces { }.",
        "Narrative with colon: hash# and equals= signs."
      )
      val property = Prop.forAll(narratives) { narrative =>
        var passed = false
        _with_fixture("narrative-property", narrative) { fixture =>
          val original = _read(fixture.storyboard)
          When("the repair script transforms the selected fixture")
          val repair = _run_script(fixture)
          Then("only the four frozen role references differ and the native source remains complete")
          passed = repair.exitcode == 0 && _read(fixture.storyboard) == _native_storyboard_text(original)
        }
        passed
      }
      When("ScalaCheck executes the generated narrative preservation property")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(10), property)
      Then("all generated cases preserve the full source text and native role references")
      result.passed shouldBe true
    }
  }

  private val _scene_ids = Vector(
    "opening",
    "speed",
    "business-wall",
    "convergence",
    "harness-definition",
    "broad-concept",
    "responsibility-boundary",
    "technology-context",
    "series-roadmap"
  )

  private final case class Fixture(root: Path, destination: Path, project: Path, video: Path, storyboard: Path, assetroot: Path)
  private final case class ScriptResult(exitcode: Int, output: String)

  private val _script_path = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("scripts/test/repair-phase71-private-declarations.sh")

  private def _run_script(fixture: Fixture): ScriptResult =
    _run_script(fixture.root, fixture.destination)

  private def _run_script(root: Path, destination: Path): ScriptResult = {
    val process = new ProcessBuilder("sh", _script_path.toString, root.toString, destination.toString).
      redirectErrorStream(true).
      start()
    val output = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    ScriptResult(process.waitFor(), output)
  }

  private def _with_fixture[A](label: String, narrative: String = "The fixture narrative preserves approval semantics and all source meaning.")(body: Fixture => A): A = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/phase71-private-declarations").resolve(label)
    _delete(root)
    Files.createDirectories(root)
    val fixture = _write_fixture(root, narrative)
    try body(fixture)
    finally _delete(root)
  }

  private def _write_fixture(root: Path, narrative: String): Fixture = {
    val destination = root.resolve("target/phase71-private-driver/simplemodeling-org")
    val videoroot = destination.resolve("src/main/media/development-process/ai-development-harness/video/ja")
    val assetroot = videoroot.resolve("assets")
    val video = videoroot.resolve("video.yaml")
    val storyboard = videoroot.resolve("storyboard.md")
    Files.createDirectories(assetroot)
    _write(video, _old_video_text)
    _write(storyboard, _storyboard_text(narrative))
    _write(destination.resolve("conf/cozy/config.yaml"), _project_config_text)
    _write(destination.resolve("conf/cozy/video/credit-profiles/fixture.yaml"), _credit_profile_text)
    Vector("opening.svg", "section-start.svg", "summary.svg", "final-page.svg").foreach { name =>
      _write(assetroot.resolve(name), "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"></svg>\n")
    }
    Fixture(root, destination, video, video, storyboard, assetroot)
  }

  private val _old_video_text =
    """name: phase71-fixture
      |title: "Phase 71 fixture"
      |locale: ja
      |tools:
      |  dockerImage: ghcr.io/asami/textus-toolchain:0.2.1-SNAPSHOT
      |  voicevoxUrl: http://127.0.0.1:50021
      |output: build/ai-development-harness-ja.mp4
      |profile: explanation
      |credits:
      |  profile: fixture
      |visual-effects:
      |  opening: title-hold-subtle-motion
      |  section-start: none
      |  summary: overview-and-conclusion-hold
      |  final-page: end-card
      |assets:
      |  opening:
      |    path: assets/opening.svg
      |    kind: project-owned
      |    required: true
      |    license: LicenseRef-SimpleModeling-Org
      |    provenance: article-media:ai-development-harness
      |  summary:
      |    path: assets/summary.svg
      |    kind: project-owned
      |    required: true
      |    license: LicenseRef-SimpleModeling-Org
      |    provenance: article-media:ai-development-harness
      |  final-page:
      |    path: assets/final-page.svg
      |    kind: project-owned
      |    required: true
      |    license: LicenseRef-SimpleModeling-Org
      |    provenance: article-media:ai-development-harness
      |renderer:
      |  engine: remotion
      |  fps: 18
      |  width: 1280
      |  height: 720
      |parts:
      |  - id: dialogue
      |    type: dialogue
      |    script: script.json
      |    audioDir: build/audio/dialogue
      |    output: build/parts/dialogue.mp4
      |    renderer:
      |      engine: remotion
      |      strategy: slide
      |      effectProfile: simple
      |      width: 1280
      |      height: 720
      |""".stripMargin

  private val _credit_profile_text =
    """schema: cozy.video.credits.v1
      |profile: fixture
      |presentation:
      |  title:
      |    ja: Credits
      |""".stripMargin

  private val _project_config_text =
    """project:
      |  id: fixture
      |  kind: test
      |video:
      |  credits:
      |    default-profile: fixture
      |""".stripMargin

  private def _storyboard_text(narrative: String): String = {
    def _scene_(id: String, order: Int, heading: String, asset: String): String = {
      val assetrefs = if (asset.isEmpty) "[]" else s"""["$asset"]"""
      val text = _indent(if (id == "opening") narrative else s"Narration for $id.")
      s"""## scene
         |id: "$id"
         |order: $order
         |section: "explanation"
         |speaker: "narrator"
         |role: "narration"
         |narration: |
         |$text
         |screen:
         |  heading: "$heading"
         |  content: |
         |    Content for $id.
         |caption: "$heading caption"
         |duration: 10s
         |lead-silence: 0.2s
         |transition: "fade"
         |production-inserts: []
         |diagram-refs: []
         |asset-refs: $assetrefs
         |pronunciation-notes: []
         |direction: |
         |  Preserve the authored direction for $id.
         |""".stripMargin
    }
    val scenes = Vector(
      _scene_("opening", 1, "Opening", "opening"),
      _scene_("speed", 2, "Speed", ""),
      _scene_("business-wall", 3, "Business wall", ""),
      _scene_("convergence", 4, "Convergence", ""),
      _scene_("harness-definition", 5, "Harness", "section-start"),
      _scene_("broad-concept", 6, "Broad concept", "summary"),
      _scene_("responsibility-boundary", 7, "Boundary", ""),
      _scene_("technology-context", 8, "Technology", ""),
      _scene_("series-roadmap", 9, "Roadmap", "final-page")
    )
    """# Storyboard
       |schema: "cozy.video.storyboard.v1"
       |version: 1
       |
       |""".stripMargin + scenes.mkString("\n")
  }

  private def _indent(text: String): String =
    text.split("\\n", -1).map(line => "  " + line).mkString("\n")

  private def _native_storyboard_text(text: String): String =
    text.
      replace("asset-refs: [\"opening\"]", "asset-refs: [\"assets/opening.svg\"]").
      replace("asset-refs: [\"section-start\"]", "asset-refs: [\"assets/section-start.svg\"]").
      replace("asset-refs: [\"summary\"]", "asset-refs: [\"assets/summary.svg\"]").
      replace("asset-refs: [\"final-page\"]", "asset-refs: [\"assets/final-page.svg\"]")

  private def _native_video_text(text: String): String =
    text.
      replace("  summary:\n", "  section-start:\n    path: assets/section-start.svg\n    kind: project-owned\n    required: true\n    license: LicenseRef-SimpleModeling-Org\n    provenance: article-media:ai-development-harness\n  summary:\n").
      replace("    type: dialogue\n", "    type: storyboard\n").
      replace("    script: script.json\n", "    storyboard: storyboard.md\n    storyboardSection: explanation\n")

  private def _write(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
  }

  private def _read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _with_symlink[A](path: Path)(body: () => A): A = {
    val real = path.resolveSibling(path.getFileName.toString + ".real")
    Files.move(path, real)
    Files.createSymbolicLink(path, real.getFileName)
    try body()
    finally {
      Files.deleteIfExists(path)
      Files.move(real, path)
    }
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally paths.close()
    }
}
