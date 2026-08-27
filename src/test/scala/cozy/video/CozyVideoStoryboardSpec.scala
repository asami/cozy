package cozy.video

import cozy.CozySpecVocabulary
import cozy.media.CozyVisualPage
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Aug. 26, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video Storyboard v1" should {
    "preserve every required field empty value and nested sequence through canonical JSON and Markdown" in {
      Given("a typed v1 Storyboard with literal text empty values and ordered nested collections")
      val storyboard = _storyboard()
      val json = CozyVideo.canonicalStoryboardJson(storyboard)
      val markdown = CozyVideo.canonicalStoryboardMarkdown(storyboard)

      When("Cozy parses both canonical representations")
      val jsonresult = CozyVideo.parseStoryboard(Paths.get("storyboard.json"), json)
      val markdownresult = CozyVideo.parseStoryboard(Paths.get("storyboard.md"), markdown)

      Then("both representations preserve the same typed values canonical JSON and semantic identity")
      jsonresult.isValid shouldBe true
      markdownresult.isValid shouldBe true
      jsonresult.storyboard shouldBe Some(storyboard)
      markdownresult.storyboard shouldBe Some(storyboard)
      CozyVideo.canonicalStoryboardJson(markdownresult.storyboard.get) shouldBe json
      CozyVideo.storyboardIdentity(jsonresult.storyboard.get) shouldBe CozyVideo.storyboardIdentity(markdownresult.storyboard.get)
      markdown should include("caption: \"\"")
      markdown should include("production-inserts: [{\"id\":\"title-card\"")
      markdown should include("    Screen content")
    }

    "round trip every permitted empty scalar and array value without changing identity" in {
      Given("a valid typed v1 Storyboard whose permitted scalar and collection values are empty")
      val storyboard = _empty_storyboard()
      val json = CozyVideo.canonicalStoryboardJson(storyboard)
      val markdown = CozyVideo.canonicalStoryboardMarkdown(storyboard)

      When("Cozy parses the canonical JSON and restricted Markdown representations")
      val jsonresult = CozyVideo.parseStoryboard(Paths.get("empty-storyboard.json"), json)
      val markdownresult = CozyVideo.parseStoryboard(Paths.get("empty-storyboard.md"), markdown)

      Then("both representations preserve the typed value canonical JSON and semantic identity")
      jsonresult.isValid shouldBe true
      markdownresult.isValid shouldBe true
      jsonresult.storyboard shouldBe Some(storyboard)
      markdownresult.storyboard shouldBe Some(storyboard)
      CozyVideo.canonicalStoryboardJson(jsonresult.storyboard.get) shouldBe json
      CozyVideo.canonicalStoryboardJson(markdownresult.storyboard.get) shouldBe json
      CozyVideo.storyboardIdentity(jsonresult.storyboard.get) shouldBe CozyVideo.storyboardIdentity(markdownresult.storyboard.get)
      json should include("\"narration\":\"\"")
      json should include("\"heading\":\"\"")
      json should include("\"content\":\"\"")
      json should include("\"productionInserts\":[]")
      json should include("\"diagramRefs\":[]")
      json should include("\"assetRefs\":[]")
      json should include("\"pronunciationNotes\":[]")
      markdown should include("narration: |\n")
      markdown should include("direction: |\n")
    }

    "normalize CRLF Markdown and reject any source format other than exact .json or .md" in {
      Given("a canonical Markdown Storyboard rendered with CRLF line endings")
      val markdown = CozyVideo.canonicalStoryboardMarkdown(_storyboard()).replace("\n", "\r\n")

      When("the Markdown and an unsupported extension are parsed")
      val markdownresult = CozyVideo.parseStoryboard(Paths.get("storyboard.md"), markdown)
      val unsupported = CozyVideo.parseStoryboard(Paths.get("storyboard.yaml"), markdown)

      Then("line ending presentation is nonsemantic while input format selection is fail-closed")
      markdownresult.isValid shouldBe true
      unsupported.isValid shouldBe false
      unsupported.diagnostics.map(_.code) should contain("INPUT_FORMAT_UNSUPPORTED")
    }

    "reject duplicate unknown missing and wrongly typed JSON fields with field diagnostics" in {
      Given("one canonical Storyboard JSON document and malformed variations")
      val canonical = _canonical_json()
      val duplicate = canonical.replace("\"schema\":\"cozy.video.storyboard.v1\"", "\"schema\":\"cozy.video.storyboard.v1\",\"schema\":\"cozy.video.storyboard.v1\"")
      val unknown = canonical.replace("{\"schema\"", "{\"unknown\":true,\"schema\"")
      val missing = canonical.replace("\"schema\":\"cozy.video.storyboard.v1\",", "")
      val wrongtype = canonical.replace("\"version\":1", "\"version\":\"1\"")

      When("Cozy projects each JSON value into the typed v1 surface")
      val results = Vector(duplicate, unknown, missing, wrongtype).map(CozyVideo.parseStoryboard(Paths.get("storyboard.json"), _))

      Then("all invalid values fail closed with stable codes and precise paths")
      results.forall(_.isValid == false) shouldBe true
      results(0).diagnostics.map(_.code) should contain("JSON_DUPLICATE_FIELD")
      results(1).diagnostics.map(_.code) should contain("FIELD_UNKNOWN")
      results(2).diagnostics.map(_.path) should contain("schema")
      results(3).diagnostics.map(_.path) should contain("version")
    }

    "reject malformed or ordered incorrectly restricted Markdown without treating it as general Markdown" in {
      Given("a canonical restricted Markdown Storyboard with duplicate and reordered authoring lines")
      val canonical = CozyVideo.canonicalStoryboardMarkdown(_storyboard())
      val duplicate = canonical.replace("version: 1\n\n", "version: 1\nversion: 1\n\n")
      val reordered = canonical.replace("id: \"intro\"\norder: 1", "order: 1\nid: \"intro\"")
      val additional = canonical + "arbitrary Markdown content\n"

      When("the Markdown grammar is read")
      val results = Vector(duplicate, reordered, additional).map(CozyVideo.parseStoryboard(Paths.get("storyboard.md"), _))

      Then("duplicate order and additional constructs are rejected before a Storyboard exists")
      results.forall(_.isValid == false) shouldBe true
      results.foreach { result =>
        result.diagnostics.head.path should not be empty
      }
      results.map(_.diagnostics.head.code).forall(_.startsWith("MARKDOWN_")) shouldBe true
    }

    "enforce schema sequence timing role transition reference insert and pronunciation invariants" in {
      Given("canonical JSON variations at each v1 semantic boundary")
      val canonical = _canonical_json()
      val invalids = Vector(
        "schema" -> canonical.replace("cozy.video.storyboard.v1", "cozy.video.storyboard.v3"),
        "order" -> canonical.replace("\"order\":1", "\"order\":2"),
        "duration" -> canonical.replace("\"duration\":5", "\"duration\":0"),
        "precision" -> canonical.replace("\"leadSilence\":0.25", "\"leadSilence\":0.1234567"),
        "role" -> canonical.replace("\"role\":\"narration\"", "\"role\":\"unsupported\""),
        "transition" -> canonical.replace("\"transition\":\"cut\"", "\"transition\":\"slide\""),
        "reference" -> canonical.replace("assets/title.png", "../escape.png"),
        "insert" -> canonical.replace("\"kind\":\"overlay\"", "\"kind\":\"unknown\""),
        "pronunciation" -> canonical.replace("\"reading\":\"Cozy\"", "\"reading\":\"\"")
      )

      When("each variation is parsed and validated")
      val results = invalids.map { case (name, json) => name -> CozyVideo.parseStoryboard(Paths.get("storyboard.json"), json) }.toMap

      Then("the typed v1 validator reports a deterministic rejection at each changed field")
      val diagnosticmapping = results.toVector.sortBy(_._1).map { case (name, result) =>
        s"$name(valid=${result.isValid}, diagnostics=${_diagnostics(result)})"
      }.mkString("; ")
      withClue(s"v1 invariant diagnostics: $diagnosticmapping") {
        results.values.forall(_.isValid == false) shouldBe true
      }
      results("schema").diagnostics.map(_.code) should contain("SCHEMA_UNSUPPORTED")
      results("order").diagnostics.map(_.code) should contain("SCENE_ORDER_SEQUENCE")
      results("duration").diagnostics.map(_.code) should contain("DURATION_INVALID")
      results("precision").diagnostics.map(_.code) should contain("LEAD_SILENCE_INVALID")
      results("role").diagnostics.map(_.code) should contain("ROLE_UNSUPPORTED")
      results("transition").diagnostics.map(_.code) should contain("TRANSITION_UNSUPPORTED")
      results("reference").diagnostics.map(_.code) should contain("ASSET_REFERENCE_UNSAFE")
      results("insert").diagnostics.map(_.code) should contain("PRODUCTION_INSERT_KIND_UNSUPPORTED")
      results("pronunciation").diagnostics.map(_.code) should contain("PRONUNCIATION_READING_INVALID")
    }

    "reject duplicate scene and production insert identities through typed validation" in {
      Given("typed Storyboard variants with duplicate scene ids scene orders and production insert ids")
      val storyboard = _two_scene_storyboard()
      val firstscene = storyboard.scenes.head
      val secondscene = storyboard.scenes(1)
      val duplicateid = storyboard.copy(scenes = Vector(firstscene, secondscene.copy(id = firstscene.id)))
      val duplicateorder = storyboard.copy(scenes = Vector(firstscene, secondscene.copy(order = firstscene.order)))
      val duplicateinsert = storyboard.copy(
        scenes = Vector(firstscene.copy(productionInserts = firstscene.productionInserts :+ firstscene.productionInserts.head), secondscene)
      )

      When("the common semantic validator receives each typed variant")
      val results = Vector(
        CozyVideo.validateStoryboard(duplicateid),
        CozyVideo.validateStoryboard(duplicateorder),
        CozyVideo.validateStoryboard(duplicateinsert)
      )

      Then("each duplicate identity is rejected with its stable diagnostic code")
      results(0).map(_.code) should contain("SCENE_ID_DUPLICATE")
      results(1).map(_.code) should contain("SCENE_ORDER_DUPLICATE")
      results(2).map(_.code) should contain("PRODUCTION_INSERT_ID_DUPLICATE")
    }

    "reject absolute escaping URI-like backslash and control-character references at their fields" in {
      Given("typed Storyboard variants containing unsafe diagram and asset references")
      val scene = _storyboard().scenes.head
      val references = Vector(
        ("/assets/title.png", "ASSET_REFERENCE_UNSAFE", "assetRefs"),
        ("../escape.png", "DIAGRAM_REFERENCE_UNSAFE", "diagramRefs"),
        ("https://example.invalid/title.png", "ASSET_REFERENCE_UNSAFE", "assetRefs"),
        ("assets\\\\title.png", "DIAGRAM_REFERENCE_UNSAFE", "diagramRefs"),
        ("assets/" + 0.toChar + "title.png", "ASSET_REFERENCE_UNSAFE", "assetRefs")
      )

      When("the common semantic validator receives each unsafe typed reference")
      val results = references.map { case (reference, code, field) =>
        val invalidscene =
          if (field == "diagramRefs") scene.copy(diagramRefs = Vector(reference))
          else scene.copy(assetRefs = Vector(reference))
        val diagnostics = CozyVideo.validateStoryboard(_storyboard().copy(scenes = Vector(invalidscene)))
        (diagnostics, code, field)
      }

      Then("each rejection reports its stable code and field-specific path")
      results.foreach { case (diagnostics, code, field) =>
        val matching = diagnostics.filter(_.code == code)
        matching should not be empty
        matching.head.path shouldBe s"scenes[0].$field[0]"
      }
    }

    "reject missing invalid-UTF-8 and symlink direct-file inputs fail-closed" in {
      Given("a temporary directory containing a valid file and an invalid UTF-8 file")
      _with_temp_dir("direct-input-safety") { root =>
        val missing = root.resolve("missing.json")
        val valid = root.resolve("valid.json")
        val invalid = root.resolve("invalid.json")
        val symlink = root.resolve("linked.json")
        Files.writeString(valid, _canonical_json(), StandardCharsets.UTF_8)
        Files.write(invalid, Array[Byte](0xc3.toByte, 0x28.toByte))
        Files.createSymbolicLink(symlink, valid.getFileName)

        When("the direct Storyboard loader reads each filesystem boundary variant")
        val missingresult = CozyVideo.loadStoryboard(missing)
        val invalidresult = CozyVideo.loadStoryboard(invalid)
        val symlinkresult = CozyVideo.loadStoryboard(symlink)

        Then("missing encoding-invalid and non-regular inputs return stable fail-closed diagnostics")
        missingresult.isValid shouldBe false
        missingresult.diagnostics.map(_.code) should contain("INPUT_MISSING")
        invalidresult.isValid shouldBe false
        invalidresult.diagnostics.map(_.code) should contain("INPUT_ENCODING_INVALID")
        symlinkresult.isValid shouldBe false
        symlinkresult.diagnostics.map(_.code) should contain("INPUT_NOT_REGULAR")
      }
    }

    "make canonical conversion and semantic identity invariant across at least thirty key-order and representation variations" in {
      Given("generated permutations of the root JSON keys and equivalent JSON or CRLF Markdown inputs")
      val rootorders = Gen.oneOf(Vector(
        Vector("schema", "version", "scenes"),
        Vector("schema", "scenes", "version"),
        Vector("version", "schema", "scenes"),
        Vector("version", "scenes", "schema"),
        Vector("scenes", "schema", "version"),
        Vector("scenes", "version", "schema")
      ))
      val representations = Gen.oneOf("json", "markdown")
      val expected = _canonical_json()
      val expectedidentity = CozyVideo.storyboardIdentity(_storyboard())
      val property = Prop.forAll(rootorders, representations) { (order, representation) =>
        val input =
          if (representation == "json") _root_key_order(expected, order)
          else CozyVideo.canonicalStoryboardMarkdown(_storyboard()).replace("\n", "\r\n")
        val source = if (representation == "json") Paths.get("storyboard.json") else Paths.get("storyboard.md")
        val result = CozyVideo.parseStoryboard(source, input)
        result.isValid &&
          CozyVideo.canonicalStoryboardJson(result.storyboard.get) == expected &&
          CozyVideo.storyboardIdentity(result.storyboard.get) == expectedidentity
      }

      When("ScalaCheck evaluates thirty independently generated harmless variations")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

      Then("all presentations converge on byte-identical canonical JSON and one identity")
      result.passed shouldBe true
    }

    "dispatch direct validate inspect and convert commands without producing output after a failed conversion" in {
      Given("a direct JSON input a valid Markdown output and an invalid output destination")
      _with_temp_dir("direct-cli") { root =>
        val input = root.resolve("storyboard.json")
        val output = root.resolve("converted/storyboard.md")
        val invalid = root.resolve("failed/storyboard.yaml")
        val brokeninput = root.resolve("broken.json")
        val brokenoutput = root.resolve("failed/broken.md")
        Files.writeString(input, _canonical_json(), StandardCharsets.UTF_8)
        Files.writeString(brokeninput, "{\"schema\":\"cozy.video.storyboard.v1\"}", StandardCharsets.UTF_8)

        When("the direct v1 commands validate inspect and convert the input")
        val validate = CozyVideo.storyboardValidate(CozyVideo.StoryboardValidateConfig(input))
        val inspect = CozyVideo.storyboardInspect(CozyVideo.StoryboardInspectConfig(input))
        CozyVideo.execute(List("video", "storyboard", "validate", input.toString)) shouldBe true
        CozyVideo.execute(List("video", "storyboard", "inspect", input.toString)) shouldBe true
        CozyVideo.execute(List("video", "storyboard", "convert", input.toString, "--save", output.toString)) shouldBe true
        val failure = intercept[IllegalArgumentException] {
          CozyVideo.storyboardConvert(CozyVideo.StoryboardConvertConfig(input, invalid))
        }
        val validationfailure = intercept[IllegalArgumentException] {
          CozyVideo.storyboardConvert(CozyVideo.StoryboardConvertConfig(brokeninput, brokenoutput))
        }

        Then("validate stays summary-only inspect presents ordered scenes and conversion writes only a selected valid format")
        validate should include("schema: cozy.video.storyboard.v1")
        validate should include("identity: sha256:")
        validate should not include "scene 1:"
        inspect should include("scene 1: id=intro")
        inspect should include("assetRefs: assets/title.png")
        Files.readString(output, StandardCharsets.UTF_8) shouldBe CozyVideo.canonicalStoryboardMarkdown(_storyboard())
        failure.getMessage should include("INPUT_FORMAT_UNSUPPORTED")
        Files.exists(invalid) shouldBe false
        validationfailure.getMessage should include("FIELD_MISSING")
        Files.exists(brokenoutput) shouldBe false
      }
    }

    "retain the legacy script boundary without mutating or interpreting script.json as a v1 Storyboard" in {
      Given("a legacy dialogue-shaped script.json source")
      val legacy = "{\"title\":\"Legacy\",\"scenes\":[{\"line\":\"old dialogue\"}]}"

      When("the direct v1 Storyboard reader receives that source")
      val result = CozyVideo.parseStoryboard(Paths.get("script.json"), legacy)

      Then("it reports v1 diagnostics and creates no typed Storyboard or migration interpretation")
      result.isValid shouldBe false
      result.storyboard shouldBe empty
      result.diagnostics.map(_.code) should contain("FIELD_MISSING")
      result.diagnostics.map(_.code) should contain("FIELD_UNKNOWN")
    }
  }

  "Cozy Video Storyboard v2" should {
    "resolve direct VisualPageSet page IDs with dotted and digit-leading stable identifiers" in {
      Given("a direct VisualPageSet containing dotted and digit-leading page IDs with its catalog")
      _with_temp_dir("v2-visual-page-id") { root =>
        val visual = _write_visual_page_fixture(root)
        val pages = CozyVisualPage.PageSet("pages", Vector(visual._3.copy(id = "page.1"), visual._3.copy(id = "1.0")))
        Files.writeString(visual._1, CozyVisualPage.canonicalJson(pages), StandardCharsets.UTF_8)

        When("Cozy resolves each visual-page screen against the direct VisualPageSet")
        val results = Vector("page.1", "1.0").map { pageid =>
          pageid -> CozyVideo.parseStoryboard(
            root.resolve(s"$pageid.storyboard.json"),
            _v2_json(_visual_page_screen(visual._1, visual._2, pageid))
          )
        }.toMap
        val invalid = CozyVideo.parseStoryboard(
          root.resolve("invalid.storyboard.json"),
          _v2_json(_visual_page_screen(visual._1, visual._2, "page/1"))
        )

        Then("each declared VisualPage ID resolves exactly once and unsafe IDs retain the stable rejection")
        results.values.forall(_.isValid) shouldBe true
        results.foreach { case (pageid, result) =>
          withClue(s"$pageid visual-page diagnostics: ${_diagnostics(result)}") {
            result.storyboard.map(_.scenes.head.screen) shouldBe Some(
              CozyVideoImplementation.StoryboardVisualPageScreen(visual._1.getFileName.toString, visual._2.getFileName.toString, pageid)
            )
            result.diagnostics.map(_.code) should not contain "VISUAL_PAGE_SCREEN_PAGE_NOT_FOUND"
          }
        }
        invalid.isValid shouldBe false
        invalid.diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_PAGE_ID_INVALID")
      }
    }

    "preserve closed text and visual-page screens in canonical identity while planning only a screen reference" in {
      Given("a direct VisualPageSet and catalog beside a v2 Storyboard source")
      _with_temp_dir("v2-valid") { root =>
        val visual = _write_visual_page_fixture(root)
        val text = _v2_json("""{"kind":"text","heading":"Welcome","content":"Screen content\nwith an ordered literal block."}""")
        val visualjson = _v2_json(_visual_page_screen(visual._1, visual._2, "overview"))
        val v2markdown = CozyVideo.canonicalStoryboardMarkdown(_storyboard()).
          replace("cozy.video.storyboard.v1", "cozy.video.storyboard.v2").
          replace("version: 1", "version: 2")
        val visualsource = root.resolve("storyboard.json")
        val catalogcopy = root.resolve("catalog-copy.json")
        Files.copy(visual._2, catalogcopy)
        Files.writeString(visualsource, visualjson, StandardCharsets.UTF_8)

        When("Cozy parses the closed text and resolved visual-page JSON representations")
        val textresult = CozyVideo.parseStoryboard(root.resolve("text.json"), text)
        val visualresult = CozyVideo.loadStoryboard(visualsource)
        val markdownresult = CozyVideo.parseStoryboard(root.resolve("storyboard.md"), v2markdown)
        val copyresult = CozyVideo.parseStoryboard(
          visualsource,
          _v2_json(_visual_page_screen(visual._1, catalogcopy, "overview"))
        )
        val planned = visualresult.storyboard.map(_.scenes.head).map(CozyVideoImplementation._storyboard_video_scene)
        val v1screen = parser.parse(CozyVideo.canonicalStoryboardJson(_storyboard())).right.get.
          hcursor.downField("scenes").downArray.downField("screen").focus.get

        Then("v2 keeps its literal reference in canonical identity and preserves every existing video semantic field")
        withClue(s"v2 text diagnostics: ${_diagnostics(textresult)}") {
          textresult.isValid shouldBe true
        }
        withClue(s"v2 visual-page diagnostics: ${_diagnostics(visualresult)}") {
          visualresult.isValid shouldBe true
        }
        markdownresult.isValid shouldBe false
        copyresult.isValid shouldBe true
        planned should not be empty
        CozyVideo.canonicalStoryboardJson(textresult.storyboard.get) shouldBe text
        CozyVideo.canonicalStoryboardJson(visualresult.storyboard.get) shouldBe visualjson
        CozyVideo.storyboardIdentity(visualresult.storyboard.get) should not be CozyVideo.storyboardIdentity(copyresult.storyboard.get)
        CozyVideo.canonicalStoryboardJson(visualresult.storyboard.get) should include("\"catalog\":\"catalog.json\"")
        v1screen.noSpaces should not include "\"kind\""
        markdownresult.diagnostics.map(_.code) should contain("STORYBOARD_V2_JSON_ONLY")
        planned.get.narration shouldBe Some(_storyboard().scenes.head.narration)
        planned.get.duration shouldBe Some(_storyboard().scenes.head.duration.toDouble)
        planned.get.leadSilence shouldBe Some(_storyboard().scenes.head.leadSilence.toDouble)
        planned.get.effects.noSpaces shouldBe "{\"transition\":\"cut\"}"
        planned.get.visual.hcursor.downField("screen").focus.get.noSpaces shouldBe _visual_page_screen(visual._1, visual._2, "overview")
      }
    }

    "reject mixed unknown unsafe symlink nonregular and unresolved visual-page screens fail-closed" in {
      Given("one direct VisualPageSet/catalog fixture and adversarial v2 screen values")
      _with_temp_dir("v2-rejections") { root =>
        val visual = _write_visual_page_fixture(root)
        val link = root.resolve("pages-link.json")
        val cataloglink = root.resolve("catalog-link.json")
        Files.createSymbolicLink(link, visual._1.getFileName)
        Files.createSymbolicLink(cataloglink, visual._2.getFileName)
        val actual = root.resolve("actual")
        Files.createDirectories(actual)
        Files.copy(visual._1, actual.resolve("pages.json"))
        Files.createSymbolicLink(root.resolve("linked"), actual.getFileName)
        Files.createDirectories(root.resolve("pages-dir"))
        val duplicate = root.resolve("duplicate-pages.json")
        _write(duplicate, CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("duplicate-pages", Vector(visual._3, visual._3))))
        val variants = Vector(
          "mixed" -> _v2_json("""{"kind":"text","heading":"Welcome","content":"Text","source":"visual-pages.json"}"""),
          "unknown" -> _v2_json("""{"kind":"unknown","heading":"Welcome","content":"Text"}"""),
          "wrong-type" -> _v2_json("""{"kind":true,"heading":"Welcome","content":"Text"}"""),
          "missing" -> _v2_json("""{"kind":"visual-page","source":"visual-pages.json","pageId":"overview"}"""),
          "unsafe" -> _v2_json(_visual_page_screen("../visual-pages.json", visual._2.toString, "overview")),
          "final-symlink" -> _v2_json(_visual_page_screen(link, visual._2, "overview")),
          "ancestor-symlink" -> _v2_json(_visual_page_screen("linked/pages.json", visual._2.getFileName.toString, "overview")),
          "nonregular" -> _v2_json(_visual_page_screen("pages-dir", visual._2.getFileName.toString, "overview")),
          "input-not-found" -> _v2_json(_visual_page_screen("missing-pages.json", visual._2.getFileName.toString, "overview")),
          "catalog-symlink" -> _v2_json(_visual_page_screen(visual._1, cataloglink, "overview")),
          "not-found" -> _v2_json(_visual_page_screen(visual._1, visual._2, "missing")),
          "duplicate" -> _v2_json(_visual_page_screen(duplicate, visual._2, "overview"))
        )

        When("Cozy validates every discriminator and direct file resolution boundary")
        val results = variants.map { case (name, json) =>
          name -> CozyVideo.parseStoryboard(root.resolve(s"$name.json"), json)
        }.toMap

        Then("no mixed or unsafe v2 screen is interpreted, substituted, or rendered")
        results.values.forall(_.isValid == false) shouldBe true
        results("mixed").diagnostics.map(_.code) should contain("FIELD_UNKNOWN")
        results("unknown").diagnostics.map(_.code) should contain("SCREEN_KIND_UNSUPPORTED")
        results("wrong-type").diagnostics.map(_.code) should contain("TYPE_INVALID")
        results("missing").diagnostics.map(_.code) should contain("FIELD_MISSING")
        withClue(s"unsafe v2 diagnostics: ${_diagnostics(results("unsafe"))}") {
          results("unsafe").diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_SOURCE_UNSAFE")
        }
        results("final-symlink").diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_INPUT_SYMLINK")
        results("ancestor-symlink").diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_INPUT_SYMLINK")
        results("nonregular").diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_INPUT_NOT_REGULAR")
        results("input-not-found").diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_INPUT_NOT_REGULAR")
        results("catalog-symlink").diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_INPUT_SYMLINK")
        results("not-found").diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_PAGE_NOT_FOUND")
        results("duplicate").diagnostics.map(_.code) should contain("VISUAL_PAGE_SCREEN_RESOLUTION_INVALID")
      }
    }

    "migrate only v1 text screens to canonical v2 JSON and reject a lossy visual-page downgrade" in {
      Given("v1 JSON and Markdown sources plus a valid v2 visual-page source")
      _with_temp_dir("v2-migration") { root =>
        val inputjson = root.resolve("storyboard.json")
        val inputmarkdown = root.resolve("storyboard.md")
        val jsonoutput = root.resolve("migrated-json.json")
        val markdownoutput = root.resolve("migrated-markdown.json")
        Files.writeString(inputjson, _canonical_json(), StandardCharsets.UTF_8)
        Files.writeString(inputmarkdown, CozyVideo.canonicalStoryboardMarkdown(_storyboard()), StandardCharsets.UTF_8)
        val visual = _write_visual_page_fixture(root.resolve("visual"))
        val visualsource = root.resolve("visual/visual-v2.json")
        Files.writeString(visualsource, _v2_json(_visual_page_screen(visual._1, visual._2, "overview")), StandardCharsets.UTF_8)

        When("the explicit migration command receives each v1 representation and a prohibited v2 downgrade")
        CozyVideo.execute(List("video", "storyboard", "migrate", "--from", "v1", "--to", "v2", "--screen", "text", inputjson.toString, "--save", jsonoutput.toString)) shouldBe true
        CozyVideo.execute(List("video", "storyboard", "migrate", "--from", "v1", "--to", "v2", "--screen", "text", inputmarkdown.toString, "--save", markdownoutput.toString)) shouldBe true
        val lossy = intercept[IllegalArgumentException] {
          CozyVideo.execute(List("video", "storyboard", "migrate", "--from", "v2", "--to", "v1", "--screen", "text", visualsource.toString, "--save", root.resolve("downgraded.json").toString))
        }
        val migratedjson = CozyVideo.loadStoryboard(jsonoutput)
        val migratedmarkdown = CozyVideo.loadStoryboard(markdownoutput)

        Then("migration keeps all v1 scene semantics, emits only v2 text screens, and never writes a lossy downgrade")
        withClue(s"v1 JSON migration diagnostics: ${_diagnostics(migratedjson)}") {
          migratedjson.isValid shouldBe true
        }
        withClue(s"v1 Markdown migration diagnostics: ${_diagnostics(migratedmarkdown)}") {
          migratedmarkdown.isValid shouldBe true
        }
        migratedjson.storyboard.get.schema shouldBe "cozy.video.storyboard.v2"
        migratedjson.storyboard.get.version shouldBe 2
        migratedjson.storyboard.get.scenes.head.narration shouldBe _storyboard().scenes.head.narration
        migratedjson.storyboard.get.scenes.head.duration shouldBe _storyboard().scenes.head.duration
        CozyVideo.canonicalStoryboardJson(migratedjson.storyboard.get) should include("\"kind\":\"text\"")
        CozyVideo.canonicalStoryboardJson(migratedmarkdown.storyboard.get) shouldBe CozyVideo.canonicalStoryboardJson(migratedjson.storyboard.get)
        lossy.getMessage should include("VISUAL_PAGE_SCREEN_LOSSY")
        Files.exists(root.resolve("downgraded.json"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }
  }

  private def _storyboard(): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v1",
      1,
      Vector(
        CozyVideo.StoryboardScene(
          "intro",
          1,
          "opening",
          "narrator",
          "narration",
          "Welcome to Cozy.\nThis narration preserves its line break.",
          CozyVideo.StoryboardScreen("Welcome", "Screen content\nwith an ordered literal block."),
          "",
          BigDecimal("5.0"),
          BigDecimal("0.25"),
          "cut",
          Vector(
            CozyVideo.StoryboardProductionInsert("title-card", "overlay", "Show title"),
            CozyVideo.StoryboardProductionInsert("hold", "pause", "Hold composition")
          ),
          Vector("diagrams/overview.svg", "diagrams/flow.svg"),
          Vector("assets/title.png", "assets/logo.svg"),
          Vector(
            CozyVideo.StoryboardPronunciationNote("Cozy", "Cozy"),
            CozyVideo.StoryboardPronunciationNote("SHA-256", "sha two five six")
          ),
          "Keep the title readable."
        )
      )
    )

  private def _empty_storyboard(): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v1",
      1,
      Vector(
        CozyVideo.StoryboardScene(
          "silent",
          1,
          "opening",
          "narrator",
          "narration",
          "",
          CozyVideo.StoryboardScreen("", ""),
          "",
          BigDecimal("1"),
          BigDecimal("0"),
          "none",
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          ""
        )
      )
    )

  private def _two_scene_storyboard(): CozyVideo.Storyboard = {
    val storyboard = _storyboard()
    val firstscene = storyboard.scenes.head
    val secondscene = firstscene.copy(id = "second", order = 2, section = "middle")
    storyboard.copy(scenes = Vector(firstscene, secondscene))
  }

  private def _canonical_json(): String =
    CozyVideo.canonicalStoryboardJson(_storyboard())

  private def _root_key_order(json: String, order: Vector[String]): String = {
    val objectvalue = parser.parse(json).right.get.asObject.get
    val values = objectvalue.toMap
    Json.obj(order.map(name => name -> values(name)): _*).noSpaces
  }

  private def _diagnostics(result: CozyVideo.StoryboardResult): String =
    result.diagnostics.map(diagnostic => s"${diagnostic.code}@${diagnostic.path}:${diagnostic.reason}").mkString("[", ", ", "]")

  private def _v2_json(screen: String): String = {
    val screenjson = parser.parse(screen).right.get
    val root = parser.parse(_canonical_json()).right.get.asObject.get
    val scenes = root("scenes").flatMap(_.asArray).get
    val scene = Json.obj(scenes.head.asObject.get.toVector.map {
      case ("screen", _) => "screen" -> screenjson
      case field => field
    }: _*)
    Json.obj(
      "schema" -> Json.fromString("cozy.video.storyboard.v2"),
      "version" -> Json.fromInt(2),
      "scenes" -> Json.fromValues(Vector(scene))
    ).noSpaces
  }

  private def _visual_page_screen(source: Path, catalog: Path, pageid: String): String =
    _visual_page_screen(source.getFileName.toString, catalog.getFileName.toString, pageid)

  private def _visual_page_screen(source: String, catalog: String, pageid: String): String =
    s"""{"kind":"visual-page","source":"$source","catalog":"$catalog","pageId":"$pageid"}"""

  private def _write_visual_page_fixture(root: Path): (Path, Path, CozyVisualPage.Page) = {
    _write(root.resolve("sources/research.txt"), "research")
    val catalog = _visual_catalog()
    val page = CozyVisualPage.Page(
      "overview",
      "knowledge-1",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "sequence",
        Vector(
          CozyVisualPage.Node("discover", "step", "Discover", Vector("research")),
          CozyVisualPage.Node("apply", "step", "Apply", Vector("research"))
        ),
        Vector(CozyVisualPage.Relation("next", "next", "discover", "apply", Vector("research")))
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector.empty,
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )
    val catalogfile = _write(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(catalog))
    val pagesfile = _write(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("pages", Vector(page))))
    (pagesfile, catalogfile, page)
  }

  private def _visual_catalog(): CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "core",
    1,
    Vector(
      CozyVisualPage.RelationDefinition("next", "from-to"),
      CozyVisualPage.RelationDefinition("causes", "from-to"),
      CozyVisualPage.RelationDefinition("depends-on", "from-to"),
      CozyVisualPage.RelationDefinition("enables", "from-to"),
      CozyVisualPage.RelationDefinition("maps-to", "from-to")
    ),
    Vector(
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear"))),
      CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"), CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false), CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true), CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)))
    )
  )

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
    path
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory("cozy-video-storyboard-" + name + "-")
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.delete)
      finally stream.close()
    }
}
