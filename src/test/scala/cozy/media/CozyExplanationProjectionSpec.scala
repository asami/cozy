package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}

import scala.collection.JavaConverters._

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

import cozy.CozySpecVocabulary
import cozy.video.CozyVideoImplementation
import io.circe.Json
import io.circe.parser
import io.circe.parser.parse

/*
 * @since   Aug. 28, 2026
 *  version Sep. 30, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyExplanationProjectionSpec {
  private final case class Fixture(
    catalogfile: Path,
    presentationcatalogfile: Path,
    compositionfile: Path,
    planfile: Path,
    mapfile: Path,
    visualpagesetfile: Path,
    storyboardfile: Path,
    bindings: CozyExplanation.ResourceBindings,
    presentationcatalog: CozyVisualPage.Catalog,
    pages: CozyVisualPage.PageSet,
    storyboard: CozyVideoImplementation.Storyboard,
    projectionmap: CozyExplanationProjection.ProjectionMap
  )
}

final class CozyExplanationProjectionSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  import CozyExplanationProjectionSpec.Fixture

  "Cozy Explanation ProjectionMap" should {
    "canonicalize generated root key orders and validate inspect and convert one explicit map" in {
      _with_work("map-canonical") { root =>
        Given("a direct-file software-product product-overview closure with independent authored page and scene mappings")
        val fixture = _fixture(root)
        val canonical = Files.readString(fixture.mapfile, StandardCharsets.UTF_8)
        val keys = Vector("schema", "version", "explanationCatalog", "presentationCatalog", "presentation", "video")
        val orders = Gen.chooseNum(0, 35).map(index => _key_order(keys, index))

        When("ScalaCheck parses reordered map root objects and the explanation command validates inspects and converts the named map")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(orders) { order =>
          val parsed = CozyExplanationProjection.parseProjectionMapJson(_root_key_order(canonical, order))
          CozyExplanationProjection.canonicalProjectionMapJson(parsed) == canonical.trim
        })
        val validate = CozyExplanation.execute(_map_command("validate", fixture))
        val inspect = CozyExplanation.execute(_map_command("inspect", fixture))
        val converted = root.resolve("converted-map.json")
        val convert = CozyExplanation.execute(_map_command("convert", fixture, Some(converted)))

        Then("every equivalent JSON root order converges on one canonical mapping value and each command uses only named direct files")
        check.passed shouldBe true
        check.succeeded should be >= 30
        validate should include_text("cozy.explanation-projection-map.v1")
        inspect should include_text("presentation: vision-step")
        convert should include_text("presentationMappings: 5")
        Files.readString(converted, StandardCharsets.UTF_8).trim shouldBe canonical.trim
      }
    }
  }

  "Cozy Explanation Projection" should {
    "reject malformed catalog selectors and ProjectionMap syntax" in {
      _with_work("map-input-shape") { root =>
        Given("a valid explicit ProjectionMap and malformed required selector and syntax variants")
        val fixture = _fixture(root)
        val originalmap = Files.readString(fixture.mapfile, StandardCharsets.UTF_8)
        val invalidmaps = Vector("explanationCatalog", "presentationCatalog").flatMap { name =>
          Vector(Json.obj("id" -> Json.Null, "revision" -> Json.fromInt(1)),
            Json.obj("id" -> Json.fromString("required"), "revision" -> Json.fromBoolean(true)),
            Json.obj("id" -> Json.fromString("required"), "revision" -> Json.fromInt(0)),
            Json.obj("id" -> Json.fromString("required"), "revision" -> Json.fromInt(1), "unknown" -> Json.obj()),
            Json.obj("revision" -> Json.fromInt(1))).map { selector =>
            parse(originalmap).toOption.get.mapObject(_.add(name, selector)).noSpaces
          }
        } ++ Vector(originalmap.replace("\"explanationCatalog\":{", "\"explanationCatalog\":{\"revision\":1,"), originalmap + "{}", originalmap.trim.dropRight(1))

        When("the strict ProjectionMap parser reads each malformed selector or JSON document")
        val failures = invalidmaps.map(text => _failure(CozyExplanationProjection.parseProjectionMapJson(text)))

        Then("required IDs and revisions, closed fields, duplicate keys, and syntax constraints reject every variant")
        failures should have size invalidmaps.size
        failures.foreach(_.getMessage should include_text("EXPLANATION_"))
      }
    }

    "project only the selected Projection with copied mappings and verify the same semantic closure" in {
      _with_work("project") { root =>
        Given("multiple P36 pages and multiple P30 v2 visual-page scenes whose counts deliberately differ")
        val fixture = _fixture(root)
        val output = root.resolve("projection.json")

        When("project receives every map Composition Plan catalog PageSet Storyboard and resource binding as explicit direct files")
        val projected = CozyExplanation.execute(_project_command(fixture, output))
        val validated = CozyExplanationProjection.loadProjection(
          output,
          fixture.compositionfile,
          fixture.planfile,
          fixture.mapfile,
          fixture.catalogfile,
          fixture.presentationcatalogfile,
          fixture.visualpagesetfile,
          fixture.storyboardfile,
          fixture.bindings
        )
        val verified = CozyExplanation.execute(_verify_command(fixture, output))

        Then("the final Projection copies both independent mappings exactly and validates actual named media")
        projected should include_text("cozy.explanation-projection.v1")
        verified should include_text("schema: cozy.explanation-projection.v1")
        validated.projection.presentation.stepMappings shouldBe fixture.projectionmap.presentation.stepMappings
        validated.projection.video.stepMappings shouldBe fixture.projectionmap.video.stepMappings
        validated.projection.presentation.visualPageSet shouldBe CozyExplanationProjection.VisualPageSetSelector(fixture.pages.id)
        fixture.pages.pages.size should be > fixture.storyboard.scenes.size
        fixture.pages.pages.filter(_.logical == fixture.pages.pages.head.logical).map(_.visual.pattern) should contain allOf ("flow-horizontal", "flow-vertical")
        root.toFile.list().toVector.sorted should contain only (
          "assets", "composition.json", "explanation-catalog.json", "map.json", "plan.json",
          "presentation-catalog.json", "projection.json", "sources", "storyboard.json", "visual-pages.json"
        )
      }
    }

    "project and verify an explicitly authored product-mechanism page and visual-page scene" in {
      _with_work("product-mechanism") { root =>
        Given("a direct-file software-product product-mechanism Composition with resolved mechanism links, mapping logical data, a mapping-columns page, and a Storyboard-v2 visual-page scene")
        val fixture = _mechanism_fixture(root)
        val validatedcomposition = CozyExplanation.loadComposition(
          fixture.compositionfile, fixture.catalogfile, fixture.presentationcatalogfile, fixture.bindings
        )
        val plan = CozyExplanation.expand(validatedcomposition)
        val output = root.resolve("projection.json")

        When("project and verify receive the explicitly authored ProjectionMap, Composition, Plan, catalogs, VisualPageSet, Storyboard, and resource bindings")
        val projected = CozyExplanation.execute(_project_command(fixture, output))
        val validated = CozyExplanationProjection.loadProjection(
          output,
          fixture.compositionfile,
          fixture.planfile,
          fixture.mapfile,
          fixture.catalogfile,
          fixture.presentationcatalogfile,
          fixture.visualpagesetfile,
          fixture.storyboardfile,
          fixture.bindings
        )
        val verified = CozyExplanation.execute(_verify_command(fixture, output))

        Then("the Projection preserves the authored mechanism mapping and typed values while validating and copying only explicit targets, without inference, rendering, or semantic, visual, audiovisual, or approval output")
        validatedcomposition.composition.explanation.pattern shouldBe CozyExplanation.PatternReference("product-mechanism", 1)
        validatedcomposition.composition.explanation.parameters.map(_.name) shouldBe Vector("mechanismLinks")
        plan.steps should have size 1
        plan.steps.head.semanticRole shouldBe "mechanism"
        plan.steps.head.parameterProvenance.values.map(_.name) shouldBe Vector("mechanismLinks")
        plan.steps.head.logical.pattern shouldBe "mapping"
        plan.steps.head.logical.relations.map(_.relationType) shouldBe Vector("maps-to")
        fixture.pages.pages.map(_.visual.pattern) shouldBe Vector("mapping-columns")
        fixture.pages.pages.head.logical shouldBe plan.steps.head.logical
        fixture.pages.pages.head.visual.parameters.map(_.name) should contain allOf ("sourceColumnTitle", "targetColumnTitle")
        fixture.storyboard.scenes.map(_.screen) shouldBe Vector(
          CozyVideoImplementation.StoryboardVisualPageScreen("visual-pages.json", "presentation-catalog.json", "page-mechanism")
        )
        fixture.projectionmap.presentation.stepMappings shouldBe Vector(
          CozyExplanationProjection.PresentationStepMapping("mechanism-step", Vector("page-mechanism"))
        )
        fixture.projectionmap.video.stepMappings shouldBe Vector(
          CozyExplanationProjection.VideoStepMapping("mechanism-step", Vector("scene-mechanism"))
        )
        projected should include_text("cozy.explanation-projection.v1")
        verified should include_text("schema: cozy.explanation-projection.v1")
        validated.projection.presentation.stepMappings shouldBe fixture.projectionmap.presentation.stepMappings
        validated.projection.video.stepMappings shouldBe fixture.projectionmap.video.stepMappings
        validated.projection.presentation.visualPageSet.id shouldBe "product-mechanism-pages"
        validated.projection.presentation.visualPageSet shouldBe CozyExplanationProjection.VisualPageSetSelector(fixture.pages.id)
        validated.canonicalJson should not include ("inferred")
        validated.canonicalJson should not include ("approval")
        validated.canonicalJson should not include ("render")
      }
    }

    "verify changed narration caption and direction while rejecting an unresolved mapped screen without replacing files" in {
      _with_work("storyboard-meaning") { root =>
        Given("valid explicitly named pages and Storyboard scenes with a selected Projection output")
        val fixture = _fixture(root)
        val output = root.resolve("projection.json")
        When("the actual media closure is projected and load-validated")
        val projected = CozyExplanation.execute(_project_command(fixture, output))
        val original = CozyExplanationProjection.loadProjection(
          output, fixture.compositionfile, fixture.planfile, fixture.mapfile, fixture.catalogfile,
          fixture.presentationcatalogfile, fixture.visualpagesetfile, fixture.storyboardfile, fixture.bindings
        )
        Then("the saved Projection preserves the authored mappings")
        projected should include_text("cozy.explanation-projection.v1")
        original.projection.video.stepMappings shouldBe fixture.projectionmap.video.stepMappings
        val outputbytes = Files.readAllBytes(output).toVector

        Given("real narration caption and direction changes with the same scene IDs screens and timing")
        val changed = fixture.storyboard.copy(scenes = fixture.storyboard.scenes.map(scene =>
          scene.copy(narration = "Revised narration: " + scene.narration, caption = "Revised caption", direction = "Hold this visual")
        ))
        _write(fixture.storyboardfile, CozyVideoImplementation.canonicalStoryboardJson(changed))
        val changedbytes = Files.readAllBytes(fixture.storyboardfile).toVector
        When("verification reads the changed named Storyboard and existing Projection")
        val verified = CozyExplanation.execute(_verify_command(fixture, output))
        Then("valid actual media mappings still verify and the saved output and source bytes remain untouched")
        verified should include_text("schema: cozy.explanation-projection.v1")
        Files.readAllBytes(output).toVector shouldBe outputbytes
        Files.readAllBytes(fixture.storyboardfile).toVector shouldBe changedbytes

        Given("the mapped first scene now references an unresolved pageId")
        val invalid = changed.copy(scenes = changed.scenes.updated(0, changed.scenes.head.copy(
          screen = CozyVideoImplementation.StoryboardVisualPageScreen("visual-pages.json", "presentation-catalog.json", "missing-page")
        )))
        _write(fixture.storyboardfile, CozyVideoImplementation.canonicalStoryboardJson(invalid))
        val invalidbytes = Files.readAllBytes(fixture.storyboardfile).toVector
        When("verification resolves the invalid screen against the actual named pages")
        val failure = _failure(CozyExplanation.execute(_verify_command(fixture, output)))
        Then("the unresolved screen fails closed before any saved output or source replacement")
        failure.getMessage should include_text("EXPLANATION_PROJECTION_STALE")
        failure.getMessage should include_text("VISUAL_PAGE_SCREEN_PAGE_NOT_FOUND")
        Files.readAllBytes(output).toVector shouldBe outputbytes
        Files.readAllBytes(fixture.storyboardfile).toVector shouldBe invalidbytes
      }
    }

    "verify at least fifty actual Storyboard formatting and root-key variations without rewriting the saved Projection" in {
      _with_work("storyboard-format") { root =>
        Given("one actual named-media closure and a selected saved Projection")
        val fixture = _fixture(root)
        val output = root.resolve("projection.json")
        When("the real Projection command writes the selected output")
        val projected = CozyExplanation.execute(_project_command(fixture, output))
        Then("the output is a valid Projection with the authored mappings")
        projected should include_text("cozy.explanation-projection.v1")
        val saved = CozyExplanationProjection.loadProjection(
          output, fixture.compositionfile, fixture.planfile, fixture.mapfile, fixture.catalogfile,
          fixture.presentationcatalogfile, fixture.visualpagesetfile, fixture.storyboardfile, fixture.bindings
        )
        saved.projection.video.stepMappings shouldBe fixture.projectionmap.video.stepMappings
        val outputbytes = Files.readAllBytes(output).toVector
        val canonical = parse(CozyVideoImplementation.canonicalStoryboardJson(fixture.storyboard)).toOption.get
        val property = Prop.forAll(Gen.choose(0, 3), Gen.oneOf(false, true)) { (format, reverse) =>
          Given("the same actual typed Storyboard with generated whitespace and optional reversed root keys")
          val reordered = if (reverse) canonical.mapObject(fields => io.circe.JsonObject.fromIterable(fields.toVector.reverse)) else canonical
          val source = format match {
            case 0 => reordered.noSpaces
            case 1 => reordered.spaces2
            case 2 => reordered.noSpaces + "\n"
            case 3 => reordered.spaces2 + "\n"
          }
          _write(fixture.storyboardfile, source)
          When("real verification reads that serialized named Storyboard")
          val verified = CozyExplanation.execute(_verify_command(fixture, output))
          val actual = CozyVideoImplementation.loadStoryboard(fixture.storyboardfile)
          Then("the valid v2 fields and current mappings verify while exact saved bytes are preserved")
          actual.isValid shouldBe true
          actual.storyboard shouldBe Some(fixture.storyboard)
          actual.storyboard.get.schema shouldBe "cozy.video.storyboard.v2"
          verified should include_text("schema: cozy.explanation-projection.v1")
          Files.readAllBytes(output).toVector shouldBe outputbytes
          Files.readString(fixture.storyboardfile, StandardCharsets.UTF_8) shouldBe source
          true
        }
        When("ScalaCheck exercises at least fifty actual named-source variations")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
        Then("every formatting variation verifies through the public operation")
        checked.passed shouldBe true
        checked.succeeded should be >= 50
      }
    }

    "fail closed for catalog selectors mappings PageSet linkage and Storyboard screens" in {
      _with_work("projection-stale") { root =>
        Given("one valid Projection closure plus selector mapping page and actual Storyboard input variants")
        val fixture = _fixture(root)
        val selectorvariant = fixture.projectionmap.copy(explanationCatalog = fixture.projectionmap.explanationCatalog.copy(id = "other-catalog"))
        val selectorfile = _write(root.resolve("selector-map.json"), CozyExplanationProjection.canonicalProjectionMapJson(selectorvariant))
        val revisionvariant = fixture.projectionmap.copy(presentationCatalog = fixture.projectionmap.presentationCatalog.copy(revision = 2))
        val revisionfile = _write(root.resolve("revision-map.json"), CozyExplanationProjection.canonicalProjectionMapJson(revisionvariant))
        val missingvariant = fixture.projectionmap.copy(
          presentation = fixture.projectionmap.presentation.copy(stepMappings = fixture.projectionmap.presentation.stepMappings.dropRight(1))
        )
        val missingfile = _write(root.resolve("missing-map.json"), CozyExplanationProjection.canonicalProjectionMapJson(missingvariant))
        val reorderedvariant = fixture.projectionmap.copy(
          video = fixture.projectionmap.video.copy(stepMappings = fixture.projectionmap.video.stepMappings.reverse)
        )
        val reorderedfile = _write(root.resolve("reordered-map.json"), CozyExplanationProjection.canonicalProjectionMapJson(reorderedvariant))
        val duplicatevariant = fixture.projectionmap.copy(
          presentation = fixture.projectionmap.presentation.copy(stepMappings = fixture.projectionmap.presentation.stepMappings.updated(0,
            fixture.projectionmap.presentation.stepMappings.head.copy(pageIds = Vector("page-vision-a", "page-vision-a"))
          ))
        )
        val duplicatefile = _write(root.resolve("duplicate-map.json"), CozyExplanationProjection.canonicalProjectionMapJson(duplicatevariant))
        val unsafe = Files.readString(fixture.mapfile, StandardCharsets.UTF_8).
          replace("\"pageIds\":[\"page-vision-a\",\"page-vision-b\"]", "\"pageIds\":[\"../unsafe\"]")
        val unsafefile = _write(root.resolve("unsafe-map.json"), unsafe)
        val changedpage = fixture.pages.pages.head.copy(logical = fixture.pages.pages.head.logical.copy(
          nodes = fixture.pages.pages.head.logical.nodes.updated(0, fixture.pages.pages.head.logical.nodes.head.copy(label = "Changed logical claim"))
        ))
        val changedpages = fixture.pages.copy(pages = fixture.pages.pages.updated(0, changedpage))
        val nonvisual = fixture.storyboard.copy(scenes = fixture.storyboard.scenes.updated(0, fixture.storyboard.scenes.head.copy(
          screen = CozyVideoImplementation.StoryboardTextScreen("Text only", "Not a visual page")
        )))
        val nonv2 = fixture.storyboard.copy(schema = "cozy.video.storyboard.v1", version = 1)
        _write(root.resolve("other-pages.json"), CozyVisualPage.canonicalJson(fixture.pages))
        val mismatchedliteral = fixture.storyboard.copy(scenes = fixture.storyboard.scenes.updated(0, fixture.storyboard.scenes.head.copy(
          screen = CozyVideoImplementation.StoryboardVisualPageScreen("other-pages.json", "presentation-catalog.json", "page-vision-a")
        )))

        When("the Projection boundary loads incompatible catalog selectors and incomplete, reordered, duplicate, or unsafe mappings")
        val selectorfailure = _failure(CozyExplanationProjection.loadProjectionMap(
          selectorfile, fixture.compositionfile, fixture.planfile, fixture.catalogfile, fixture.presentationcatalogfile, fixture.bindings
        ))
        val revisionfailure = _failure(CozyExplanationProjection.loadProjectionMap(
          revisionfile, fixture.compositionfile, fixture.planfile, fixture.catalogfile, fixture.presentationcatalogfile, fixture.bindings
        ))
        val missingfailure = _failure(CozyExplanationProjection.loadProjectionMap(
          missingfile, fixture.compositionfile, fixture.planfile, fixture.catalogfile, fixture.presentationcatalogfile, fixture.bindings
        ))
        val reorderedfailure = _failure(CozyExplanationProjection.loadProjectionMap(
          reorderedfile, fixture.compositionfile, fixture.planfile, fixture.catalogfile, fixture.presentationcatalogfile, fixture.bindings
        ))
        val duplicatefailure = _failure(CozyExplanationProjection.loadProjectionMap(
          duplicatefile, fixture.compositionfile, fixture.planfile, fixture.catalogfile, fixture.presentationcatalogfile, fixture.bindings
        ))
        val unsafefailure = _failure(CozyExplanationProjection.loadProjectionMap(
          unsafefile, fixture.compositionfile, fixture.planfile, fixture.catalogfile, fixture.presentationcatalogfile, fixture.bindings
        ))
        Then("catalog and mapping failures retain structured semantic and format diagnostics")
        Vector(selectorfailure, revisionfailure, missingfailure, reorderedfailure).foreach(_.getMessage should include_text("EXPLANATION_PROJECTION_STALE"))
        duplicatefailure.getMessage should include_text("EXPLANATION_SCHEMA_INVALID")
        unsafefailure.getMessage should include_text("EXPLANATION_SCHEMA_INVALID")

        Given("a changed mapped page logical claim")
        _write(fixture.visualpagesetfile, CozyVisualPage.canonicalJson(changedpages))
        When("projection compares that page with the authored Plan")
        val pagefailure = _failure(CozyExplanation.execute(_project_command(fixture, root.resolve("page-failure.json"))))
        Then("the invalid actual media fails closed before output replacement")
        pagefailure.getMessage should include_text("EXPLANATION_PROJECTION_STALE")
        Vector(
          "missing-source" -> fixture.pages.copy(pages = fixture.pages.pages.map(_.copy(sources = Vector.empty))),
          "asset-media-type" -> fixture.pages.copy(pages = fixture.pages.pages.map(page =>
            page.copy(assets = page.assets.map(_.copy(mediaType = "application/octet-stream")))
          )),
          "missing-source-file" -> fixture.pages.copy(pages = fixture.pages.pages.map(page =>
            page.copy(sources = page.sources.map(_.copy(path = "sources/missing.txt")))
          ))
        ).foreach { case (name, pages) =>
          Given(s"mapped Pages with an invalid $name declaration or resource")
          _write(fixture.visualpagesetfile, CozyVisualPage.canonicalJson(pages))
          When("projection admits required declarations, media types, and direct descriptor paths")
          val failure = _failure(CozyExplanation.execute(_project_command(fixture, root.resolve(s"$name-failure.json"))))
          Then("the invalid semantic resource boundary fails before writing output")
          failure.getMessage should include_text("EXPLANATION_PROJECTION_STALE")
          Files.exists(root.resolve(s"$name-failure.json")) shouldBe false
        }

        Given("the valid named pages restored for the Storyboard screen variants")
        _write(fixture.visualpagesetfile, CozyVisualPage.canonicalJson(fixture.pages))
        Given("a mapped scene using a text screen")
        _write(fixture.storyboardfile, CozyVideoImplementation.canonicalStoryboardJson(nonvisual))
        When("projection resolves the mapped scene screen")
        val screenfailure = _failure(CozyExplanation.execute(_project_command(fixture, root.resolve("screen-failure.json"))))
        Then("the invalid actual media fails closed before output replacement")
        screenfailure.getMessage should include_text("EXPLANATION_PROJECTION_STALE")
        Given("a named Storyboard using the wrong schema version")
        _write(fixture.storyboardfile, CozyVideoImplementation.canonicalStoryboardJson(nonv2))
        When("projection loads the named source through the strict reader")
        val versionfailure = _failure(CozyExplanation.execute(_project_command(fixture, root.resolve("version-failure.json"))))
        Then("the invalid actual media fails closed before output replacement")
        versionfailure.getMessage should include_text("EXPLANATION_PROJECTION_STALE")
        Given("a mapped screen naming a different actual PageSet path")
        _write(fixture.storyboardfile, CozyVideoImplementation.canonicalStoryboardJson(mismatchedliteral))
        When("projection checks the literal screen source against the named media")
        val literalfailure = _failure(CozyExplanation.execute(_project_command(fixture, root.resolve("literal-failure.json"))))
        Then("the invalid actual media fails closed before output replacement")
        literalfailure.getMessage should include_text("EXPLANATION_PROJECTION_STALE")
        Given("the original valid named media restored after the rejected variants")
        _write(fixture.storyboardfile, CozyVideoImplementation.canonicalStoryboardJson(fixture.storyboard))
        val output = root.resolve("projection.json")
        When("the valid closure is projected to the selected output and loaded")
        val projected = CozyExplanation.execute(_project_command(fixture, output))
        val original = CozyExplanationProjection.loadProjection(
          output, fixture.compositionfile, fixture.planfile, fixture.mapfile, fixture.catalogfile,
          fixture.presentationcatalogfile, fixture.visualpagesetfile, fixture.storyboardfile, fixture.bindings
        )
        Then("the saved Projection preserves the valid video mapping")
        projected should include_text("cozy.explanation-projection.v1")
        original.projection.video.stepMappings shouldBe fixture.projectionmap.video.stepMappings

        Given("a Projection with another PageSet ID and another ordered page mapping")
        val differentset = original.projection.copy(presentation = original.projection.presentation.copy(
          visualPageSet = CozyExplanationProjection.VisualPageSetSelector("other-pages")
        ))
        val differentmapping = original.projection.copy(presentation = original.projection.presentation.copy(
          stepMappings = original.projection.presentation.stepMappings.reverse
        ))
        val setfile = _write(root.resolve("different-set.json"), CozyExplanationProjection.canonicalProjectionJson(differentset))
        val mappingfile = _write(root.resolve("different-mapping.json"), CozyExplanationProjection.canonicalProjectionJson(differentmapping))
        When("verification resolves those selected PageSet and mapping values against the named inputs")
        val setfailure = _failure(CozyExplanation.execute(_verify_command(fixture, setfile)))
        val mappingfailure = _failure(CozyExplanation.execute(_verify_command(fixture, mappingfile)))
        Then("the meaningful selector and mapping conflicts fail with their field paths")
        setfailure.getMessage should include_text("$.presentation.visualPageSet")
        mappingfailure.getMessage should include_text("$.presentation.stepMappings")

        And("all semantic closure conflicts report a structured path and reason")
        Vector(selectorfailure, revisionfailure, missingfailure, reorderedfailure, pagefailure, screenfailure, versionfailure, literalfailure, setfailure, mappingfailure).foreach { failure =>
          failure.getMessage should include_text("EXPLANATION_PROJECTION_STALE")
          failure.getMessage should include_text("path=")
          failure.getMessage should include_text("reason=")
        }
        selectorfailure.getMessage should include_text("explanationCatalog")
        revisionfailure.getMessage should include_text("presentationCatalog")
        pagefailure.getMessage should include_text("pageIds")
        screenfailure.getMessage should include_text("sceneIds")
      }
    }
  }

  private def _fixture(root: Path): Fixture = {
    val catalog = _catalog()
    val presentationcatalog = _presentation_catalog()
    val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(catalog))
    val presentationcatalogfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(presentationcatalog))
    val sourcefile = _write(root.resolve("sources/source.txt"), "source bytes")
    val assetfile = _write(root.resolve("assets/asset.txt"), "asset bytes")
    val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))
    val composition = _composition(catalog)
    val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
    val validatedcomposition = CozyExplanation.loadComposition(compositionfile, catalogfile, presentationcatalogfile, bindings)
    val plan = CozyExplanation.expand(validatedcomposition)
    val planfile = _write(root.resolve("plan.json"), CozyExplanation.canonicalPlanJson(plan))
    val pages = _pages(plan, presentationcatalog)
    val visualpagesetfile = _write(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(pages))
    val storyboard = _storyboard(pages)
    val storyboardfile = _write(root.resolve("storyboard.json"), CozyVideoImplementation.canonicalStoryboardJson(storyboard))
    val projectionmap = _projection_map(plan)
    val mapfile = _write(root.resolve("map.json"), CozyExplanationProjection.canonicalProjectionMapJson(projectionmap))
    Fixture(catalogfile, presentationcatalogfile, compositionfile, planfile, mapfile, visualpagesetfile, storyboardfile, bindings, presentationcatalog, pages, storyboard, projectionmap)
  }

  private def _mechanism_fixture(root: Path): Fixture = {
    val catalog = _catalog()
    val presentationcatalog = _presentation_catalog()
    val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(catalog))
    val presentationcatalogfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(presentationcatalog))
    val sourcefile = _write(root.resolve("sources/source.txt"), "mechanism source bytes")
    val assetfile = _write(root.resolve("assets/asset.txt"), "mechanism asset bytes")
    val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))
    val composition = _mechanism_composition(catalog)
    val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
    val validatedcomposition = CozyExplanation.loadComposition(compositionfile, catalogfile, presentationcatalogfile, bindings)
    val plan = CozyExplanation.expand(validatedcomposition)
    val planfile = _write(root.resolve("plan.json"), CozyExplanation.canonicalPlanJson(plan))
    val page = _mechanism_page(plan.steps.head.logical, presentationcatalog)
    val pages = CozyVisualPage.PageSet("product-mechanism-pages", Vector(page))
    val visualpagesetfile = _write(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(pages))
    val storyboard = _mechanism_storyboard()
    val storyboardfile = _write(root.resolve("storyboard.json"), CozyVideoImplementation.canonicalStoryboardJson(storyboard))
    val projectionmap = _mechanism_projection_map(plan)
    val mapfile = _write(root.resolve("map.json"), CozyExplanationProjection.canonicalProjectionMapJson(projectionmap))
    Fixture(catalogfile, presentationcatalogfile, compositionfile, planfile, mapfile, visualpagesetfile, storyboardfile, bindings, presentationcatalog, pages, storyboard, projectionmap)
  }

  private def _mechanism_composition(catalog: CozyExplanation.Catalog): CozyExplanation.Composition = {
    val sources = Vector("source")
    val assets = Vector("asset")
    val facts = Vector(
      CozyExplanation.Fact("name", CozyExplanation.JsonString("Cozy"), sources, assets),
      CozyExplanation.Fact("vision", CozyExplanation.JsonString("Make mechanism linkage explicit"), sources, assets),
      CozyExplanation.Fact("goals", CozyExplanation.JsonArray(Vector(_labeled("goal-1", "Make plans trustworthy", sources, assets))), sources, assets),
      CozyExplanation.Fact("context", CozyExplanation.JsonString("A direct-file explanation workflow"), sources, assets),
      CozyExplanation.Fact("useCases", CozyExplanation.JsonArray(Vector(_labeled("use-case-1", "Explain a product", sources, assets))), sources, assets),
      CozyExplanation.Fact("mainScenario", CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString("scenario-1"),
        "label" -> CozyExplanation.JsonString("Project explicit mechanism steps"),
        "steps" -> CozyExplanation.JsonArray(Vector(_labeled("scenario-step-1", "Validate mechanism links", sources, assets)))
      )), sources, assets),
      CozyExplanation.Fact("mechanisms", CozyExplanation.JsonArray(Vector(_labeled("mechanism-1", "Strict mechanism validation", sources, assets))), sources, assets)
    )
    val mechanismlinks = CozyExplanation.JsonArray(Vector(CozyExplanation.JsonObject(Vector(
      "id" -> CozyExplanation.JsonString("mechanism-link-1"),
      "goalId" -> CozyExplanation.JsonString("goal-1"),
      "useCaseId" -> CozyExplanation.JsonString("use-case-1"),
      "mechanismId" -> CozyExplanation.JsonString("mechanism-1"),
      "sourceRefs" -> CozyExplanation.JsonArray(sources.map(CozyExplanation.JsonString)),
      "assetRefs" -> CozyExplanation.JsonArray(assets.map(CozyExplanation.JsonString))
    ))))
    val logical = _mechanism_logical(sources)
    val step = CozyExplanation.CompositionStep(
      "mechanism-step", 1, "mechanism",
      Vector(CozyExplanation.Claim("mechanism-claim", "The mechanism link is explicitly authored.", "primary", sources, assets)),
      logical, sources, assets, Vector(CozyExplanation.ParameterSelection("mechanismLinks"))
    )
    CozyExplanation.Composition(
      "product-mechanism-composition",
      CozyExplanation.CatalogSelector(catalog.id, catalog.revision),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(
        CozyExplanation.PatternReference("product-mechanism", 1),
        Vector(CozyExplanation.Parameter("mechanismLinks", mechanismlinks)),
        Vector(step)
      ),
      Vector(CozyExplanation.SourceDeclaration("source")),
      Vector(CozyExplanation.AssetDeclaration("asset", "text/plain"))
    )
  }

  private def _mechanism_logical(sources: Vector[String]): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "mapping",
    Vector(
      CozyVisualPage.Node("mechanism-source", "source", "Goal and use case", sources),
      CozyVisualPage.Node("mechanism-target", "target", "Mechanism", sources)
    ),
    Vector(CozyVisualPage.Relation("mechanism-maps-to", "maps-to", "mechanism-source", "mechanism-target", sources))
  )

  private def _mechanism_page(logical: CozyVisualPage.Logical, catalog: CozyVisualPage.Catalog): CozyVisualPage.Page = CozyVisualPage.Page(
    "page-mechanism",
    "product-mechanism",
    "en",
    CozyVisualPage.CatalogReference(catalog.id, catalog.revision),
    logical,
    CozyVisualPage.Visual("mapping-columns", Vector(
      CozyVisualPage.VisualParameter("sourceColumnTitle", CozyVisualPage.StringParameter("Goal and use case")),
      CozyVisualPage.VisualParameter("targetColumnTitle", CozyVisualPage.StringParameter("Mechanism"))
    )),
    Vector(CozyVisualPage.Asset("asset", "assets/asset.txt", "text/plain")),
    Vector(CozyVisualPage.SourceBinding("source", "sources/source.txt"))
  )

  private def _mechanism_storyboard(): CozyVideoImplementation.Storyboard = CozyVideoImplementation.Storyboard(
    "cozy.video.storyboard.v2",
    2,
    Vector(CozyVideoImplementation.StoryboardScene(
      "scene-mechanism", 1, "section-mechanism", "narrator", "narration", "Narrate page-mechanism",
      CozyVideoImplementation.StoryboardVisualPageScreen("visual-pages.json", "presentation-catalog.json", "page-mechanism"),
      "Caption page-mechanism", BigDecimal(1), BigDecimal(0), "cut", Vector.empty, Vector.empty, Vector.empty, Vector.empty, ""
    ))
  )

  private def _mechanism_projection_map(plan: CozyExplanation.Plan): CozyExplanationProjection.ProjectionMap = {
    val presentationsteps = Vector(CozyExplanationProjection.PresentationStepMapping("mechanism-step", Vector("page-mechanism")))
    val videosteps = Vector(CozyExplanationProjection.VideoStepMapping("mechanism-step", Vector("scene-mechanism")))
    val presentation = CozyExplanationProjection.PresentationMapping(presentationsteps)
    val video = CozyExplanationProjection.VideoMapping(videosteps)
    CozyExplanationProjection.ProjectionMap(
      plan.explanationCatalog, plan.presentationCatalog, presentation, video
    )
  }

  private def _catalog(): CozyExplanation.Catalog =
    CozyExplanation.parseCatalogJson(_catalog_json())

  private def _presentation_catalog(): CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "presentation", 1,
    Vector(
      CozyVisualPage.RelationDefinition("next", "from-to"),
      CozyVisualPage.RelationDefinition("causes", "from-to"),
      CozyVisualPage.RelationDefinition("depends-on", "from-to"),
      CozyVisualPage.RelationDefinition("enables", "from-to"),
      CozyVisualPage.RelationDefinition("maps-to", "from-to")
    ),
    Vector(
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(
        CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear")
      )),
      CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(
        CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"),
        CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic")
      )),
      CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(
        CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic")
      )),
      CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(
        CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")
      ))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(
        CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false),
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false)
      )),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(
        CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false),
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false)
      )),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false),
        CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true),
        CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)
      ))
    )
  )

  private def _composition(catalog: CozyExplanation.Catalog): CozyExplanation.Composition = {
    val sources = Vector("source")
    val assets = Vector("asset")
    val facts = Vector(
      CozyExplanation.Fact("name", CozyExplanation.JsonString("Cozy"), sources, assets),
      CozyExplanation.Fact("vision", CozyExplanation.JsonString("Make explanation linkage explicit"), sources, assets),
      CozyExplanation.Fact("goals", CozyExplanation.JsonArray(Vector(_labeled("goal-1", "Make plans trustworthy", sources, assets))), sources, assets),
      CozyExplanation.Fact("context", CozyExplanation.JsonString("A direct-file explanation workflow"), sources, assets),
      CozyExplanation.Fact("useCases", CozyExplanation.JsonArray(Vector(_labeled("use-case-1", "Explain a product", sources, assets))), sources, assets),
      CozyExplanation.Fact("mainScenario", CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString("scenario-1"),
        "label" -> CozyExplanation.JsonString("Project explicit steps"),
        "steps" -> CozyExplanation.JsonArray(Vector(_labeled("scenario-step-1", "Validate links", sources, assets)))
      )), sources, assets),
      CozyExplanation.Fact("mechanisms", CozyExplanation.JsonArray(Vector(_labeled("mechanism-1", "Strict validation", sources, assets))), sources, assets)
    )
    val roles = Vector("vision", "goal", "context", "use-case", "main-scenario")
    val steps = roles.zipWithIndex.map { case (role, index) =>
      CozyExplanation.CompositionStep(
        s"$role-step", index + 1, role,
        Vector(CozyExplanation.Claim(s"$role-claim", s"$role is an authored product overview claim.", if (index == 0) "primary" else "supporting", sources, assets)),
        _logical(role, sources), sources, assets, Vector.empty
      )
    }
    CozyExplanation.Composition(
      "product-overview-composition",
      CozyExplanation.CatalogSelector(catalog.id, catalog.revision),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(CozyExplanation.PatternReference("product-overview", 1), Vector.empty, steps),
      Vector(CozyExplanation.SourceDeclaration("source")),
      Vector(CozyExplanation.AssetDeclaration("asset", "text/plain"))
    )
  }

  private def _logical(role: String, sources: Vector[String]): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "sequence",
    Vector(
      CozyVisualPage.Node(s"$role-start", "step", s"$role start", sources),
      CozyVisualPage.Node(s"$role-end", "step", s"$role end", sources)
    ),
    Vector(CozyVisualPage.Relation(s"$role-next", "next", s"$role-start", s"$role-end", sources))
  )

  private def _pages(plan: CozyExplanation.Plan, catalog: CozyVisualPage.Catalog): CozyVisualPage.PageSet = {
    val primary = plan.steps.map { step => _page(s"page-${step.semanticRole}-a", step.logical, catalog) }
    val secondary = _page("page-vision-b", plan.steps.head.logical, catalog, "flow-vertical")
    CozyVisualPage.PageSet("product-overview-pages", primary.updated(0, primary.head) :+ secondary)
  }

  private def _page(
    id: String,
    logical: CozyVisualPage.Logical,
    catalog: CozyVisualPage.Catalog,
    visualpattern: String = "flow-horizontal"
  ): CozyVisualPage.Page = CozyVisualPage.Page(
    id,
    "product-overview",
    "en",
    CozyVisualPage.CatalogReference(catalog.id, catalog.revision),
    logical,
    CozyVisualPage.Visual(visualpattern, Vector.empty),
    Vector(CozyVisualPage.Asset("asset", "assets/asset.txt", "text/plain")),
    Vector(CozyVisualPage.SourceBinding("source", "sources/source.txt"))
  )

  private def _storyboard(pages: CozyVisualPage.PageSet): CozyVideoImplementation.Storyboard = {
    val pageids = Vector("page-vision-a", "page-goal-a", "page-context-a", "page-use-case-a", "page-main-scenario-a")
    CozyVideoImplementation.Storyboard(
      "cozy.video.storyboard.v2",
      2,
      pageids.zipWithIndex.map { case (pageid, index) =>
        CozyVideoImplementation.StoryboardScene(
          s"scene-${index + 1}", index + 1, s"section-${index + 1}", "narrator", "narration", s"Narrate $pageid",
          CozyVideoImplementation.StoryboardVisualPageScreen("visual-pages.json", "presentation-catalog.json", pageid),
          s"Caption $pageid", BigDecimal(1), BigDecimal(0), "cut", Vector.empty, Vector.empty, Vector.empty, Vector.empty, ""
        )
      }
    )
  }

  private def _projection_map(plan: CozyExplanation.Plan): CozyExplanationProjection.ProjectionMap = {
    val pages = plan.steps.map { step =>
      val targets = if (step.id == "vision-step") Vector("page-vision-a", "page-vision-b") else Vector(s"page-${step.semanticRole}-a")
      CozyExplanationProjection.PresentationStepMapping(step.id, targets)
    }
    val scenes = plan.steps.zipWithIndex.map { case (step, index) =>
      CozyExplanationProjection.VideoStepMapping(step.id, Vector(s"scene-${index + 1}"))
    }
    val presentation = CozyExplanationProjection.PresentationMapping(pages)
    val video = CozyExplanationProjection.VideoMapping(scenes)
    CozyExplanationProjection.ProjectionMap(
      plan.explanationCatalog, plan.presentationCatalog, presentation, video
    )
  }

  private def _map_command(action: String, fixture: Fixture, save: Option[Path] = None): List[String] =
    List(action, fixture.mapfile.toString, "--composition", fixture.compositionfile.toString, "--plan", fixture.planfile.toString,
      "--explanation-catalog", fixture.catalogfile.toString, "--presentation-catalog", fixture.presentationcatalogfile.toString,
      "--source", s"source=${fixture.bindings.sources("source")}", "--asset", s"asset=${fixture.bindings.assets("asset")}") ++
      save.toList.flatMap(path => List("--save", path.toString))

  private def _project_command(fixture: Fixture, output: Path): List[String] =
    List("project", "--projection-map", fixture.mapfile.toString, "--composition", fixture.compositionfile.toString,
      "--plan", fixture.planfile.toString, "--explanation-catalog", fixture.catalogfile.toString,
      "--presentation-catalog", fixture.presentationcatalogfile.toString, "--visual-page-set", fixture.visualpagesetfile.toString,
      "--storyboard", fixture.storyboardfile.toString, "--source", s"source=${fixture.bindings.sources("source")}",
      "--asset", s"asset=${fixture.bindings.assets("asset")}", "--save", output.toString)

  private def _verify_command(fixture: Fixture, output: Path): List[String] =
    List("verify-projection", output.toString, "--composition", fixture.compositionfile.toString, "--plan", fixture.planfile.toString,
      "--projection-map", fixture.mapfile.toString, "--explanation-catalog", fixture.catalogfile.toString,
      "--presentation-catalog", fixture.presentationcatalogfile.toString, "--visual-page-set", fixture.visualpagesetfile.toString,
      "--storyboard", fixture.storyboardfile.toString, "--source", s"source=${fixture.bindings.sources("source")}",
      "--asset", s"asset=${fixture.bindings.assets("asset")}")

  private def _catalog_json(): String =
    """{"schema":"cozy.explanation.catalog.v1","version":1,"id":"software-explanation","revision":1,"subjectPatterns":[{"id":"software-product","version":1,"factDefinitions":[{"name":"context","type":"text","required":true},{"name":"goals","type":"goal-list","required":true},{"name":"mainScenario","type":"scenario","required":true},{"name":"mechanisms","type":"mechanism-list","required":true},{"name":"name","type":"text","required":true},{"name":"useCases","type":"use-case-list","required":true},{"name":"vision","type":"text","required":true}]}],"explanationPatterns":[{"id":"problem-solution","version":1,"compatibleSubjects":[{"id":"software-product","version":1}],"parameterDefinitions":[{"name":"problem","type":"text","required":true},{"name":"solution","type":"text","required":true}],"roles":[{"id":"problem","order":1,"required":true},{"id":"solution","order":2,"required":true}]},{"id":"product-mechanism","version":1,"compatibleSubjects":[{"id":"software-product","version":1}],"parameterDefinitions":[{"name":"mechanismLinks","type":"mechanism-link-list","required":true}],"roles":[{"id":"mechanism","order":1,"required":true}]},{"id":"product-overview","version":1,"compatibleSubjects":[{"id":"software-product","version":1}],"parameterDefinitions":[],"roles":[{"id":"vision","order":1,"required":true},{"id":"goal","order":2,"required":true},{"id":"context","order":3,"required":true},{"id":"use-case","order":4,"required":true},{"id":"main-scenario","order":5,"required":true}]}],"reservedNarrativeArgumentIds":["problem-solution","problem-cause-solution","current-target","before-after","challenge-approach-result","observation-insight-implication","fact-interpretation-action","why-what-how","input-process-output","concept-example","claim-evidence","claim-reasons","question-answer","principle-mechanism-effect","strategy-execution-outcome","past-present-future"]}"""

  private def _labeled(id: String, label: String, sources: Vector[String], assets: Vector[String]): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "id" -> CozyExplanation.JsonString(id), "label" -> CozyExplanation.JsonString(label),
    "sourceRefs" -> CozyExplanation.JsonArray(sources.map(CozyExplanation.JsonString)),
    "assetRefs" -> CozyExplanation.JsonArray(assets.map(CozyExplanation.JsonString))
  ))

  private def _root_key_order(json: String, order: Vector[String]): String = {
    val root = parser.parse(json).right.get.asObject.get
    val values = root.toMap
    Json.obj(order.map(name => name -> values(name)): _*).noSpaces
  }

  private def _key_order(keys: Vector[String], index: Int): Vector[String] = {
    val base = if ((index / keys.size) % 2 == 0) keys else keys.reverse
    val offset = index % keys.size
    base.drop(offset) ++ base.take(offset)
  }

  private def _failure[A](body: => A): RuntimeException = intercept[RuntimeException](body)

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    path
  }


  private def _with_work[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-explanation-projection-" + name + "-")
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) Files.list(path).iterator().asScala.foreach(_delete_tree)
      Files.deleteIfExists(path)
    }
}
