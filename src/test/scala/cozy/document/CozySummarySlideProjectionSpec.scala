package cozy.document

import cozy.media.{CozyMedia, CozyVisualPage, CozyVisualPageBinding}
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.security.MessageDigest
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 13, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozySummarySlideProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Summary Slide Projection" should {
    "conversion" which {
    "project the Article 9 application overview into one deterministic three-page Visual Page Set without creating a PageSet file" in {
      _with_work("cozy-summary-slide-projection-valid") { root =>
        Given("a closed local Core, v2 Document and Summary, fixed catalog and binding, media target, infographic, and projection profile")
        val fixture = _fixture(root)
        val pagesetoutput = root.resolve("visual-pages.json")
        Files.delete(pagesetoutput)

        When("the selected in-process profile is projected twice without invoking the explicit writer or media route")
        val first = CozySummarySlideProjection.project(root, root.relativize(fixture.profile))
        val second = CozySummarySlideProjection.project(root, root.relativize(fixture.profile))
        val pages = second.visualPageSet.document.asInstanceOf[CozyVisualPage.PageSet].pages
        val binding = CozyVisualPageBinding.load(fixture.binding, second.visualPageSet)

        Then("the deterministic model retains exact page order, directions, semantic roles, localized labels, sources, asset identity, and binding without a hidden file effect")
        Files.exists(pagesetoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
        first.visualPageSet.documentIdentity shouldBe second.visualPageSet.documentIdentity
        pages.map(_.id) shouldBe Vector("overview-domain-to-application", "overview-realization-to-foundation", "overview-conclusion-to-realization")
        pages.map(_.logical.pattern) shouldBe Vector("mapping", "dependency-map", "dependency-map")
        pages.map(_.visual.pattern) shouldBe Vector("mapping-columns", "flow-vertical", "flow-vertical")
        pages.map(_.logical.nodes.map(node => node.id -> node.role)) shouldBe Vector(
          Vector("overview-domain-model" -> "source", "overview-application-model" -> "target"),
          Vector("overview-realization" -> "dependent", "overview-foundation" -> "dependency"),
          Vector("overview-conclusion" -> "dependent", "overview-realization" -> "dependency")
        )
        pages.map(_.logical.relations.map(relation => (relation.id, relation.relationType, relation.from, relation.to))) shouldBe Vector(
          Vector(("overview-domain-to-application", "maps-to", "overview-domain-model", "overview-application-model")),
          Vector(("overview-realization-to-foundation", "depends-on", "overview-realization", "overview-foundation")),
          Vector(("overview-conclusion-to-realization", "depends-on", "overview-conclusion", "overview-realization"))
        )
        pages.flatMap(_.logical.nodes.map(_.label)).toSet should contain allOf ("Node root-domain-model", "Step use-case-realization", "Step application-conclusion")
        pages.foreach(_.sources.map(_.id) shouldBe Vector("core", "document", "summary", "profile", "media"))
        pages.foreach(_.assets shouldBe Vector(CozyVisualPage.Asset("infographic", "infographic.svg", "image/svg+xml", _sha256(fixture.infographic))))
        pages.foreach(_.logical.nodes.foreach(_.sourceRefs shouldBe Vector("core", "document", "summary")))
        pages.foreach(_.logical.relations.foreach(_.sourceRefs shouldBe Vector("core", "summary")))
        pages.flatMap(_.visual.parameters.map(_.name)) should not contain "emphasisNode"
        binding.id shouldBe "business-binding"
        binding.profile shouldBe "business"
        second.provenance.mediaTarget shouldBe "summary-slides-pdf"
        second.provenance.profile.identity shouldBe _identity(fixture.profile)
      }
    }

    "leave an existing PageSet and every admitted input byte-identical during pure projection" in {
      _with_work("cozy-summary-slide-projection-pure-existing-output") { root =>
        Given("a closed fixture with an existing derived PageSet and snapshots of the PageSet and its admitted inputs")
        val fixture = _fixture(root)
        val pagesetoutput = root.resolve("visual-pages.json")
        val prior = Files.readAllBytes(pagesetoutput).toVector
        val inputbytes = _input_bytes(fixture)

        When("the profile is projected without calling the explicit writer")
        val projected = CozySummarySlideProjection.project(root, root.relativize(fixture.profile))

        Then("the canonical model is available while the prior PageSet and all admitted inputs remain unchanged")
        projected.visualPageSet.documentIdentity.nonEmpty shouldBe true
        Files.readAllBytes(pagesetoutput).toVector shouldBe prior
        _input_bytes(fixture) shouldBe inputbytes
      }
    }
    }

    "output" which {
    "write canonical PageSet bytes at a caller-owned explicit alternative output without reading the media target source" in {
      _with_work("cozy-summary-slide-projection-explicit-write") { root =>
        Given("a closed fixture, a caller-owned absolute alternative PageSet output, and snapshots of every admitted input")
        val fixture = _fixture(root)
        val inputbytes = _input_bytes(fixture)
        val output = root.resolve("derived/alternative-pages.json").toAbsolutePath
        val projection = CozySummarySlideProjection.project(root, root.relativize(fixture.profile))

        When("the validated projection is written twice through the explicit writer")
        val first = CozySummarySlideProjection.write(projection, output)
        val firstbytes = Files.readAllBytes(first.outputPath).toVector
        val second = CozySummarySlideProjection.write(projection, output)

        Then("the writer atomically installs deterministic canonical bytes at that exact path and preserves every admitted input")
        first.outputPath shouldBe output
        firstbytes shouldBe (first.projection.visualPageSet.canonicalJson + "\n").getBytes(StandardCharsets.UTF_8).toVector
        Files.readAllBytes(second.outputPath).toVector shouldBe firstbytes
        first.projection.visualPageSet.documentIdentity shouldBe second.projection.visualPageSet.documentIdentity
        _input_bytes(fixture) shouldBe inputbytes
      }
    }
    }

    "conversion" which {
    "keep pure projection independent of a changed selected target source declaration" in {
      _with_work("cozy-summary-slide-projection-source-independent") { root =>
        Given("a closed fixture whose selected target source is rebound while all semantic target declarations remain valid")
        val fixture = _fixture(root)
        val baseline = CozySummarySlideProjection.project(root, root.relativize(fixture.profile))
        _set_media_source(fixture, "rebound-pages.json")

        When("A projects the rebound descriptor without using its selected target source")
        val rebound = CozySummarySlideProjection.project(root, root.relativize(fixture.profile))

        Then("the canonical Visual Page conversion remains unchanged while provenance admits the rebound descriptor")
        rebound.visualPageSet.canonicalJson shouldBe baseline.visualPageSet.canonicalJson
        rebound.mediaDescriptorPath shouldBe fixture.media
      }
    }
    }

    "output" which {
    "report ordinary explicit writer failures without a bespoke destination policy" in {
      _with_work("cozy-summary-slide-projection-ordinary-write-error") { root =>
        Given("a validated projection and a caller-selected directory destination")
        val fixture = _fixture(root)
        val projection = CozySummarySlideProjection.project(root, root.relativize(fixture.profile))
        val output = Files.createDirectory(root.resolve("writer-directory"))

        When("the writer performs its ordinary atomic replacement")
        val failure = intercept[CozySummarySlideProjection.ProjectionFault](CozySummarySlideProjection.write(projection, output))

        Then("the filesystem error is reported as an output failure")
        failure.code shouldBe "SUMMARY_SLIDE_PROJECTION_OUTPUT"
      }
    }
    }

    "connection" which {
    "connect a rebound root-level source to the same A output and B input path" in {
      _with_work("cozy-summary-slide-projection-connection") { root =>
        Given("a closed fixture whose selected summary target names a new root-level PageSet source")
        val fixture = _fixture(root)
        _set_media_source(fixture, "rebound-pages.json")

        When("X prepares and writes the single A-to-B connection")
        val prepared = CozySummarySlidePdf.prepare(root, root.relativize(fixture.profile))
        val written = CozySummarySlidePdf.write(root, root.relativize(fixture.profile))

        Then("the resolved connection path is the exact writer output and contains canonical PageSet bytes")
        prepared.pageSetPath shouldBe root.resolve("rebound-pages.json")
        written.outputPath shouldBe prepared.pageSetPath
        Files.readString(prepared.pageSetPath, StandardCharsets.UTF_8) shouldBe (prepared.projection.visualPageSet.canonicalJson + "\n")
      }
    }

    "reject malformed X connection source grammar before writing" in {
      _with_work("cozy-summary-slide-projection-connection-source") { root =>
        Given("fixtures whose selected summary target sources use nested or traversal grammar")
        val sources = Vector("nested/visual-pages.json", "..")

        When("X prepares each A-to-B connection")
        val failures = sources.map { source =>
          val fixture = _fixture(Files.createDirectory(root.resolve(source.replace('/', '_').replace('.', 'x'))))
          _set_media_source(fixture, source)
          val prior = Files.readAllBytes(fixture.root.resolve("visual-pages.json")).toVector
          val failure = intercept[CozySummarySlideProjection.ProjectionFault](CozySummarySlidePdf.write(fixture.root, fixture.root.relativize(fixture.profile)))
          failure -> (fixture.root.resolve("visual-pages.json") -> prior)
        }

        Then("every malformed connection is rejected before any PageSet write")
        failures.map(_._1.code).toSet shouldBe Set("SUMMARY_SLIDE_PDF_CONNECTION")
        failures.foreach { case (_, (output, prior)) => Files.readAllBytes(output).toVector shouldBe prior }
      }
    }

    "build the connected PageSet through the existing Phase 40 summary-PDF route" in {
      _with_work("cozy-summary-slide-projection-pdf-connection") { root =>
        Given("a projected PageSet, current prebuilt article and infographic receipts, and a Phase 40-compatible fake renderer")
        val fixture = _fixture(root)
        CozySummarySlidePdf.write(root, root.relativize(fixture.profile))
        CozyMedia.build(CozyMedia.CommandConfig(fixture.media, target = Some("article-pdf")))
        CozyMedia.build(CozyMedia.CommandConfig(fixture.media, target = Some("infographic")))
        var renderercommand = Vector.empty[String]

        When("X writes its connection and invokes the existing targeted media build")
        val result = CozySummarySlidePdf.build(root, root.relativize(fixture.profile), _projection_renderer(root, command => renderercommand = command))
        val connection = CozySummarySlidePdf.prepare(root, root.relativize(fixture.profile))

        Then("the renderer receives X's exact Visual Page path with ordered pages and existing receipt verification")
        result should include("summary-slides-pdf: rendered")
        renderercommand(renderercommand.indexOf("--visual-page-set") + 1) shouldBe connection.pageSetPath.toString
        _page_count(root.resolve("target/summary-slides.pdf")) shouldBe 3
        Files.readString(root.resolve("target/renderer.json"), StandardCharsets.UTF_8) should include("cozy.summary-slides.render.v2")
        Files.readString(root.resolve("target/cozy-media/manifest.json"), StandardCharsets.UTF_8) should include("summary-slides-pdf")
      }
    }
    }

    "semantic admission" which {
    "project the effective generated infographic output instead of its source" in {
      _with_work("cozy-summary-slide-projection-generated-infographic") { root =>
        Given("a copy-built infographic whose generated output differs from its declared source")
        val fixture = _fixture(root)
        val media = Files.readString(fixture.media, StandardCharsets.UTF_8).replace(
          "    source: infographic.svg\n    build: prebuilt",
          "    source: infographic.svg\n    output: target/infographic.svg\n    build: copy"
        )
        val output = _write(fixture.root.resolve("target/infographic.svg"), "generated infographic output\n")
        Files.writeString(fixture.media, media, StandardCharsets.UTF_8)
        _rewrite_profile(fixture)

        When("the summary slide profile is projected")
        val projected = CozySummarySlideProjection.project(root, root.relativize(fixture.profile))
        val pages = projected.visualPageSet.document.asInstanceOf[CozyVisualPage.PageSet].pages

        Then("every Visual Page asset names and hashes the effective generated output")
        val expected = CozyVisualPage.Asset("infographic", "target/infographic.svg", "image/svg+xml", _sha256(output))
        pages.foreach(_.assets shouldBe Vector(expected))
        _sha256(output) should not be _sha256(fixture.infographic)
      }
    }

    "reject stale unsafe malformed and semantically mismatched authorities while retaining prior derived bytes" in {
      Given("a previously projected closed fixture for each independent profile or authority mutation")

      val rejectioncases: Vector[(String, Fixture => Path)] = Vector(
        "cozy-summary-slide-projection-stale" -> ((fixture: Fixture) => {
          Files.writeString(fixture.core, Files.readString(fixture.core, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
          fixture.profile
        }),
        "cozy-summary-slide-projection-unsafe" -> ((fixture: Fixture) =>
          _write(fixture.root.resolve("unsafe.yaml"), Files.readString(fixture.profile, StandardCharsets.UTF_8).replace("path: content/core.yaml", "path: ../core.yaml"))
        ),
        "cozy-summary-slide-projection-reserved-profile" -> ((fixture: Fixture) =>
          _write(fixture.root.resolve("content/ja/summary.yaml"), Files.readString(fixture.profile, StandardCharsets.UTF_8))
        ),
        "cozy-summary-slide-projection-duplicate" -> ((fixture: Fixture) =>
          _write(fixture.root.resolve("duplicate.yaml"), Files.readString(fixture.profile, StandardCharsets.UTF_8).replace("schema: cozy.summary-slide-projection.v1\n", "schema: cozy.summary-slide-projection.v1\nschema: cozy.summary-slide-projection.v1\n"))
        ),
        "cozy-summary-slide-projection-unknown" -> ((fixture: Fixture) =>
          _write(fixture.root.resolve("unknown.yaml"), Files.readString(fixture.profile, StandardCharsets.UTF_8) + "unknown: prohibited\n")
        ),
        "cozy-summary-slide-projection-root-order" -> ((fixture: Fixture) => {
          val source = Files.readString(fixture.profile, StandardCharsets.UTF_8)
          val bindingstart = source.indexOf("binding:\n")
          val pagesstart = source.indexOf("pages:\n")
          _write(fixture.root.resolve("root-order.yaml"), source.substring(0, bindingstart) + source.substring(pagesstart) + source.substring(bindingstart, pagesstart))
        }),
        "cozy-summary-slide-projection-emphasis" -> ((fixture: Fixture) =>
          _write(fixture.root.resolve("emphasis.yaml"), Files.readString(fixture.profile, StandardCharsets.UTF_8).replace("visualParameters:\n      sourceColumnTitle", "visualParameters:\n      emphasisNode: overview-domain-model\n      sourceColumnTitle"))
        ),
        "cozy-summary-slide-projection-missing-edge" -> ((fixture: Fixture) =>
          _write(fixture.root.resolve("missing-edge.yaml"), Files.readString(fixture.profile, StandardCharsets.UTF_8).replace("    edges:\n      - diagramEdgeId: overview-conclusion-to-realization\n", "    edges: []\n"))
        ),
        "cozy-summary-slide-projection-misordered-edge" -> ((fixture: Fixture) =>
          _write(fixture.root.resolve("misordered-edge.yaml"), _profile_source(fixture).replace("overview-realization-to-foundation", "temporary-edge").replace("overview-conclusion-to-realization", "overview-realization-to-foundation").replace("temporary-edge", "overview-conclusion-to-realization"))
        ),
        "cozy-summary-slide-projection-missing-endpoint" -> ((fixture: Fixture) =>
          _write(fixture.root.resolve("missing-endpoint.yaml"), Files.readString(fixture.profile, StandardCharsets.UTF_8).replace("      - diagramItemId: overview-foundation\n        role: dependency\n", ""))
        ),
        "cozy-summary-slide-projection-unrelated-edge-free-item" -> ((fixture: Fixture) => {
          val source = Files.readString(fixture.core, StandardCharsets.UTF_8).replace(
            "      - id: root-application-model\n        role: target\n",
            "      - id: root-application-model\n        role: target\n      - id: root-orphan-model\n        role: source\n"
          )
          _write(fixture.core, source)
          val core = CozyDocumentLogicTree.loadCore(fixture.core)
          _write(fixture.document, _document_source(core, _identity(fixture.core)))
          _write(fixture.summary, _summary_source(core, _identity(fixture.core), _identity(fixture.document)))
          val profile = _rewrite_profile(fixture)
          _write(profile, Files.readString(profile, StandardCharsets.UTF_8).replace(
            "      - diagramItemId: overview-domain-model\n        role: source\n",
            "      - diagramItemId: overview-domain-model\n        role: source\n      - diagramItemId: overview-orphan-model\n        role: source\n"
          ))
        }),
        "cozy-summary-slide-projection-media" -> ((fixture: Fixture) => {
          Files.writeString(fixture.media, Files.readString(fixture.media, StandardCharsets.UTF_8).replace("catalog: presentation/catalog.json", "catalog: presentation/wrong.json"), StandardCharsets.UTF_8)
          _rewrite_profile(fixture)
        }),
        "cozy-summary-slide-projection-blank-target-public-path" -> ((fixture: Fixture) => {
          Files.writeString(fixture.media, Files.readString(fixture.media, StandardCharsets.UTF_8).replace("publicPath: /summary-slides.pdf", "publicPath: \"\""), StandardCharsets.UTF_8)
          _rewrite_profile(fixture)
        }),
        "cozy-summary-slide-projection-duplicate-article-pdf" -> ((fixture: Fixture) => {
          val source = Files.readString(fixture.media, StandardCharsets.UTF_8)
          val start = source.indexOf("  - id: article-pdf\n")
          val end = source.indexOf("  - id: infographic\n")
          val article = source.substring(start, end)
          Files.writeString(fixture.media, source.substring(0, end) + article + source.substring(end), StandardCharsets.UTF_8)
          _rewrite_profile(fixture)
        }),
        "cozy-summary-slide-projection-asset-kind" -> ((fixture: Fixture) => {
          Files.writeString(fixture.media, Files.readString(fixture.media, StandardCharsets.UTF_8).replace("  - id: infographic\n    kind: infographic", "  - id: infographic\n    kind: document"), StandardCharsets.UTF_8)
          _rewrite_profile(fixture)
        }),
        "cozy-summary-slide-projection-catalog" -> ((fixture: Fixture) => {
          Files.writeString(fixture.catalog, "{\"schema\":\"cozy.presentation-semantics.catalog.v1\"}", StandardCharsets.UTF_8)
          _rewrite_profile(fixture)
        }),
        "cozy-summary-slide-projection-binding" -> ((fixture: Fixture) => {
          Files.writeString(fixture.binding, Files.readString(fixture.binding, StandardCharsets.UTF_8).replace("\"business\"", "\"wrong\""), StandardCharsets.UTF_8)
          _rewrite_profile(fixture)
        })
      )

      When("each stale, path-unsafe, closed-grammar, coverage, media, catalog, or binding failure is projected")
      val failures = rejectioncases.map { case (name, mutate) => name -> _rejected_case(name)(mutate) }

      Then("every failure is fail-closed before the explicit writer can replace the established root-level output")
      val codes = failures.map(_._2.code).toSet
      failures.collectFirst { case (name, failure) if name == "cozy-summary-slide-projection-root-order" => failure.code } shouldBe Some("SUMMARY_SLIDE_PROJECTION_FIELDS")
      failures.collectFirst { case (name, failure) if name == "cozy-summary-slide-projection-reserved-profile" => failure.code } shouldBe Some("SUMMARY_SLIDE_PROJECTION_PROFILE_PATH")
      failures.collectFirst { case (name, failure) if name == "cozy-summary-slide-projection-blank-target-public-path" => failure.code } shouldBe Some("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET")
      failures.collectFirst { case (name, failure) if name == "cozy-summary-slide-projection-duplicate-article-pdf" => failure.code } shouldBe Some("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET")
      failures.collectFirst { case (name, failure) if name == "cozy-summary-slide-projection-asset-kind" => failure.code } shouldBe Some("SUMMARY_SLIDE_PROJECTION_MEDIA_ASSET")
      failures.collectFirst { case (name, failure) if name == "cozy-summary-slide-projection-unrelated-edge-free-item" => failure.code } shouldBe Some("SUMMARY_SLIDE_PROJECTION_MAPPING_ITEM")
      codes should contain allOf (
        "SUMMARY_SLIDE_PROJECTION_IDENTITY",
        "SUMMARY_SLIDE_PROJECTION_PATH",
        "SUMMARY_SLIDE_PROJECTION_EMPHASIS",
        "SUMMARY_SLIDE_PROJECTION_MAPPING_COVERAGE",
        "SUMMARY_SLIDE_PROJECTION_MAPPING_ENDPOINT",
        "SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET"
      )
      codes.exists(code => code == "SUMMARY_SLIDE_PROJECTION_PROFILE" || code == "SUMMARY_SLIDE_PROJECTION_FIELDS") shouldBe true
      codes.exists(code => code == "VISUAL_PAGE_CATALOG_CORE" || code == "VISUAL_PAGE_FIELDS") shouldBe true
      codes.exists(code => code == "VISUAL_PAGE_BINDING_PROFILE" || code == "VISUAL_PAGE_BINDING_CATALOG") shouldBe true
    }
    }
  }

  private final class Fixture(
    val root: Path,
    val core: Path,
    val document: Path,
    val summary: Path,
    val media: Path,
    val catalog: Path,
    val binding: Path,
    val infographic: Path,
    val profile: Path
  )

  private def _fixture(root: Path): Fixture = {
    val content = Files.createDirectories(root.resolve("content"))
    val locale = Files.createDirectories(content.resolve("ja"))
    val core = content.resolve("core.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/core.yaml"), core)
    val validated = CozyDocumentLogicTree.loadCore(core)
    val document = _write(locale.resolve("document.yaml"), _document_source(validated, _identity(core)))
    val summary = _write(locale.resolve("summary.yaml"), _summary_source(validated, _identity(core), _identity(document)))
    val presentation = Files.createDirectories(root.resolve("presentation"))
    val catalog = _write(presentation.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(CozyVisualPage.fixedCatalog))
    val binding = _write(presentation.resolve("binding.json"), _binding_source())
    _write_pdf(root.resolve("article.pdf"), 1)
    _write(root.resolve("template.pptx"), "presentation template")
    val infographic = _write(root.resolve("infographic.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\"/></svg>\n")
    val media = _write(root.resolve("media.yaml"), _media_source())
    val fixture = new Fixture(root, core, document, summary, media, catalog, binding, infographic, root.resolve("projection.yaml"))
    _write(fixture.profile, _profile_source(fixture))
    _write(root.resolve("visual-pages.json"), "prior derived bytes\n")
    fixture
  }

  private def _document_source(core: CozyDocumentLogicTree.ValidatedCore, coreidentity: String): String = {
    val refs = _references(core)
    val steplabels = core.depthFirstSteps.map(step => s"    - stepRef: ${step.id}\n      text: Step ${step.id}").mkString("\n")
    val nodelabels = core.nodesById.keys.toVector.sorted.map(node => s"    - nodeRef: $node\n      text: Node $node").mkString("\n")
    s"""|schema: cozy.document-description.v2
        |id: document-ja
        |core:
        |  id: ${core.core.id}
        |  identity: $coreidentity
        |locale: ja
        |document:
        |  title: Article 9 document
        |  sections:
        |    - id: document-root
        |      heading: Article 9
        |      coreRefs: $refs
        |      blocks:
        |        - id: document-paragraph
        |          kind: paragraph
        |          text: Complete Core coverage is retained here.
        |          coreRefs: $refs
        |      sections: []
        |labels:
        |  steps:
        |$steplabels
        |  nodes:
        |$nodelabels
        |""".stripMargin
  }

  private def _summary_source(core: CozyDocumentLogicTree.ValidatedCore, coreidentity: String, documentidentity: String): String = {
    val root = core.core.root
    val steps = root.id +: root.steps.map(_.id)
    val claims = root.claims.map(_.id)
    val nodes = root.structure.nodes.map(_.id)
    val relations = root.structure.relations.map(_.id)
    val flows = Vector(root.flow.id)
    val items = (root.structure.nodes.map(node => s"          - id: ${_overview_item_id(node.id)}\n            kind: node\n            ref: ${node.id}") ++ root.steps.map(step => s"          - id: ${_overview_item_id(step.id)}\n            kind: step\n            ref: ${step.id}")).mkString("\n")
    val edges = (root.structure.relations.map(relation => s"          - id: overview-domain-to-application\n            kind: relation\n            ref: ${relation.id}\n            direction: forward") ++ root.flow.transitions.map { transition =>
      val id = if (transition.id == "realization-depends-on-foundation") "overview-realization-to-foundation" else "overview-conclusion-to-realization"
      s"          - id: $id\n            kind: flow-transition\n            ref: ${transition.id}\n            direction: forward"
    }).mkString("\n")
    def _values_(values: Vector[String]): String = values.mkString("[", ", ", "]")
    val refs = s"{ steps: ${_values_(steps)}, claims: ${_values_(claims)}, nodes: ${_values_(nodes)}, relations: ${_values_(relations)}, flows: ${_values_(flows)} }"
    s"""|schema: cozy.summary-description.v2
        |id: summary-ja
        |core:
        |  id: ${core.core.id}
        |  identity: $coreidentity
        |document:
        |  id: document-ja
        |  identity: $documentidentity
        |locale: ja
        |summary:
        |  title: Article 9 overview
        |  units:
        |    - id: application-overview
        |      heading: Application overview
        |      message: The application model depends on the domain model.
        |      emphasis: primary
        |      coreRefs: $refs
        |      navigationLabel: Application overview
        |      retainedPoints:
        |        - id: overview-retained
        |          text: Root meaning remains visible.
        |          coreRefs: { steps: [${root.id}], claims: [], nodes: [], relations: [], flows: [] }
        |      diagram:
        |        items:
        |$items
        |        edges:
        |$edges
        |      overview:
        |        stepRef: ${root.id}
        |      omissions:
        |        - id: overview-omission
        |          documentKind: section
        |          documentRef: document-root
        |          disposition: condensed
        |          rationale: Details remain in the full document.
        |""".stripMargin
  }

  private def _media_source(): String =
    """|schema: cozy.media.v1
        |knowledge:
        |  id: application-modeling
        |  source: content/core.yaml
        |languages: [ja]
        |resources:
        |  - id: article-pdf
        |    kind: document
        |    language: ja
        |    source: article.pdf
        |    build: prebuilt
        |    articleMedia:
        |      role: article_pdf
        |      publicPath: /article.pdf
        |      mediaType: application/pdf
        |  - id: infographic
        |    kind: infographic
        |    language: ja
        |    source: infographic.svg
        |    build: prebuilt
        |    articleMedia:
        |      role: infographic
        |      publicPath: /infographic.svg
        |      mediaType: image/svg+xml
        |  - id: summary-slides-pdf
        |    kind: document
        |    language: ja
        |    source: visual-pages.json
        |    output: target/summary-slides.pdf
        |    build: summary-slides-pdf
        |    articleMedia:
        |      role: summary_slides_pdf
        |      publicPath: /summary-slides.pdf
        |      mediaType: application/pdf
        |    summarySlidesPdf:
        |      contract: visual-page-v1
        |      profile: business
        |      catalog: presentation/catalog.json
        |      binding: presentation/binding.json
        |      slideImages: target/slides
        |      montage: target/montage.png
        |      rendererManifest: target/renderer.json
        |      articlePdf: article-pdf
        |      infographic: infographic
        |profiles:
        |  business:
        |    presentation:
        |      template: template.pptx
        |      renderer:
        |        name: cozy-renderer
        |        version: "1"
        |        command: [cozy-renderer]
        |""".stripMargin

  private def _binding_source(): String = {
    val slots = Vector("knowledge", "nodes", "relations", "assets", "parameters")
    val patterns = CozyVisualPage.fixedCatalog.visualPatterns.map { pattern =>
      CozyVisualPageBinding.PatternBinding(pattern.id, slots.map(slot => CozyVisualPageBinding.SlotBinding(slot, "slot-" + slot)))
    }
    CozyVisualPageBinding.canonicalJson(CozyVisualPageBinding.Binding("business-binding", "business", CozyVisualPage.CatalogReference("presentation", 1), patterns))
  }

  private def _profile_source(fixture: Fixture): String =
    s"""|schema: cozy.summary-slide-projection.v1
        |version: 1
        |id: application-overview-pages
        |core:
        |  id: application-modeling
        |  identity: ${_identity(fixture.core)}
        |  path: content/core.yaml
        |document:
        |  id: document-ja
        |  identity: ${_identity(fixture.document)}
        |  path: content/ja/document.yaml
        |summary:
        |  id: summary-ja
        |  identity: ${_identity(fixture.summary)}
        |  path: content/ja/summary.yaml
        |locale: ja
        |media:
        |  id: application-modeling
        |  identity: ${_identity(fixture.media)}
        |  path: media.yaml
        |  summarySlidesPdf: summary-slides-pdf
        |catalog:
        |  id: presentation
        |  revision: 1
        |  identity: ${_identity(fixture.catalog)}
        |  path: presentation/catalog.json
        |binding:
        |  id: business-binding
        |  identity: ${_identity(fixture.binding)}
        |  profile: business
        |  path: presentation/binding.json
        |pages:
        |  - id: overview-domain-to-application
        |    summaryUnitId: application-overview
        |    logicalPattern: mapping
        |    visualPattern: mapping-columns
        |    items:
        |      - diagramItemId: overview-domain-model
        |        role: source
        |      - diagramItemId: overview-application-model
        |        role: target
        |    edges:
        |      - diagramEdgeId: overview-domain-to-application
        |    visualParameters:
        |      sourceColumnTitle: Domain
        |      targetColumnTitle: Application
        |  - id: overview-realization-to-foundation
        |    summaryUnitId: application-overview
        |    logicalPattern: dependency-map
        |    visualPattern: flow-vertical
        |    items:
        |      - diagramItemId: overview-realization
        |        role: dependent
        |      - diagramItemId: overview-foundation
        |        role: dependency
        |    edges:
        |      - diagramEdgeId: overview-realization-to-foundation
        |    visualParameters: {}
        |  - id: overview-conclusion-to-realization
        |    summaryUnitId: application-overview
        |    logicalPattern: dependency-map
        |    visualPattern: flow-vertical
        |    items:
        |      - diagramItemId: overview-conclusion
        |        role: dependent
        |      - diagramItemId: overview-realization
        |        role: dependency
        |    edges:
        |      - diagramEdgeId: overview-conclusion-to-realization
        |    visualParameters: {}
        |""".stripMargin

  private def _references(core: CozyDocumentLogicTree.ValidatedCore): String = {
    def _values_(values: Vector[String]): String = values.mkString("[", ", ", "]")
    s"{ steps: ${_values_(core.depthFirstSteps.map(_.id))}, claims: ${_values_(core.claimsById.keys.toVector.sorted)}, nodes: ${_values_(core.nodesById.keys.toVector.sorted)}, relations: ${_values_(core.relationsById.keys.toVector.sorted)}, flows: ${_values_(core.flowsById.keys.toVector.sorted)} }"
  }

  private def _overview_item_id(value: String): String = value match {
    case "root-domain-model" => "overview-domain-model"
    case "root-application-model" => "overview-application-model"
    case "root-orphan-model" => "overview-orphan-model"
    case "application-foundation" => "overview-foundation"
    case "use-case-realization" => "overview-realization"
    case "application-conclusion" => "overview-conclusion"
    case _ => throw new IllegalArgumentException(s"unexpected Article 9 overview item: $value")
  }

  private def _rewrite_profile(fixture: Fixture): Path = _write(fixture.profile, _profile_source(fixture))

  private def _set_media_source(fixture: Fixture, source: String): Path = {
    val media = Files.readString(fixture.media, StandardCharsets.UTF_8).replace("    source: visual-pages.json\n", s"    source: $source\n")
    _write(fixture.media, media)
    _rewrite_profile(fixture)
  }

  private def _rejected_case(name: String)(mutate: Fixture => Path): CozySummarySlideProjection.ProjectionFault = {
    var result = Option.empty[CozySummarySlideProjection.ProjectionFault]
    _with_work(name) { root =>
      Given("a closed fixture with a prior explicit PageSet and one mutated authority")
      val fixture = _fixture(root)
      val written = CozySummarySlideProjection.write(
        CozySummarySlideProjection.project(root, root.relativize(fixture.profile)),
        root.resolve("visual-pages.json")
      )
      val prior = Files.readAllBytes(written.outputPath).toVector
      val failedprofile = mutate(fixture)

      When("the mutated authority is projected")
      val failure = intercept[CozySummarySlideProjection.ProjectionFault](CozySummarySlideProjection.project(root, root.relativize(failedprofile)))

      Then("the failed authority cannot replace the established PageSet")
      Files.readAllBytes(written.outputPath).toVector shouldBe prior
      result = Some(failure)
    }
    result.get
  }

  private def _projection_renderer(root: Path, capture: Vector[String] => Unit): CozyMedia.ProcessRunner =
    new CozyMedia.ProcessRunner {
      def run(command: Vector[String], workingdirectory: Path): Int = {
        capture(command)
        val pageset = Path.of(command(command.indexOf("--visual-page-set") + 1))
        val catalog = Path.of(command(command.indexOf("--catalog") + 1))
        val binding = Path.of(command(command.indexOf("--binding") + 1))
        val template = Path.of(command(command.indexOf("--template") + 1))
        val pdf = Path.of(command(command.indexOf("--pdf") + 1))
        val images = Path.of(command(command.indexOf("--slide-images") + 1))
        val montage = Path.of(command(command.indexOf("--montage") + 1))
        val manifest = Path.of(command(command.indexOf("--manifest") + 1))
        val visual = CozyVisualPage.load(pageset, catalog)
        val bindingdocument = CozyVisualPageBinding.load(binding, visual)
        val pages = visual.document.pages
        _write_pdf(pdf, pages.size)
        pages.foreach(page => _write_png(images.resolve(page.id + ".png")))
        _write_png(montage)
        val pdfhash = _sha256(pdf)
        val slides = pages.map { page =>
          val assets = page.assets.map(asset => s"""{"id":"${asset.id}","sha256":"${_sha256(root.resolve(asset.path))}"}""").mkString(",")
          s"""{"id":"${page.id}","path":"target/slides/${page.id}.png","sha256":"${_sha256(images.resolve(page.id + ".png"))}","pdfSha256":"$pdfhash","assets":[$assets]}"""
        }.mkString(",")
        _write(
          manifest,
          s"""{"schema":"cozy.summary-slides.render.v2","target":"summary-slides-pdf","profile":"business","renderer":{"name":"cozy-renderer","version":"1"},"visualPageSetSha256":"${_semantic_sha256(visual.documentIdentity)}","catalogSha256":"${_semantic_sha256(visual.catalogIdentity)}","bindingSha256":"${_semantic_sha256(bindingdocument.bindingIdentity)}","templateSha256":"${_sha256(template)}","pdf":{"sha256":"$pdfhash","pageCount":${pages.size}},"slides":[$slides],"montage":{"path":"target/montage.png","sha256":"${_sha256(montage)}","pdfSha256":"$pdfhash"}}"""
        )
        0
      }
    }

  private def _write_pdf(path: Path, pages: Int): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val document = new PDDocument()
    try {
      (1 to pages).foreach(_ => document.addPage(new PDPage()))
      document.save(path.toFile)
    } finally document.close()
  }

  private def _write_png(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00))
  }

  private def _page_count(path: Path): Int = {
    val document = PDDocument.load(path.toFile)
    try document.getNumberOfPages
    finally document.close()
  }

  private def _semantic_sha256(value: String): String = value.stripPrefix("sha256:")

  private def _input_bytes(fixture: Fixture): Vector[(String, Vector[Byte])] =
    Vector(
      "profile" -> fixture.profile,
      "core" -> fixture.core,
      "document" -> fixture.document,
      "summary" -> fixture.summary,
      "media" -> fixture.media,
      "catalog" -> fixture.catalog,
      "binding" -> fixture.binding,
      "template" -> fixture.root.resolve("template.pptx"),
      "article" -> fixture.root.resolve("article.pdf"),
      "infographic" -> fixture.infographic
    ).map { case (name, path) => name -> Files.readAllBytes(path).toVector }

  private def _identity(path: Path): String = "sha256:" + _sha256(path)
  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString
  private def _resource(value: String): Path = Paths.get(getClass.getResource(value).toURI)
  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _with_work(name: String)(body: Path => Unit): Unit = {
    val target = Files.createDirectories(Paths.get("target").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(target, name)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
      }
      finally stream.close()
    }
}
