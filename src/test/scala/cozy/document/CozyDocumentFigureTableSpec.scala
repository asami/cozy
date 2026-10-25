package cozy.document

import io.circe.Json
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.util.{Base64, UUID}
import javax.imageio.ImageIO
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.yaml.snakeyaml.Yaml
import scala.collection.JavaConverters._

/*
 * @since   Oct. 25, 2026
 * @version Oct. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyDocumentFigureTableSpec {
  final case class Fixture(root: Path) {
    def core: Path = root.resolve("content/core.yaml")
    def document(locale: String): Path = root.resolve(s"content/$locale/document.yaml")
  }
}

final class CozyDocumentFigureTableSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDocumentFigureTableSpec.Fixture
  import CozyDocumentDescriptionV2.{Figure, Table, TableColumn, TableRow}

  "Explanatory figure and table admission" should {
    "retain the paired Japanese and English representatives through both real loaders" in {
      _with_fixture { fixture =>
        Given("the actual paired source files with a comparison figure and a nested protection table")
        val locales = Vector("ja", "en")
        val expectedfigures = locales.map(locale => _figure(_source(fixture.document(locale))))
        val expectedheadings = Vector(Vector("保護対象", "仕組み", "適用場所"), Vector("Protection target", "Mechanism", "Application location"))
        val expectedcells = Vector(
          Vector(Vector("サービス境界", "アクセス制御", "サービス入口"), Vector("データ境界", "暗号化", "保存と転送")),
          Vector(Vector("Service boundary", "Access control", "Service entry"), Vector("Data boundary", "Encryption", "Storage and transfer"))
        )

        When("each locale is admitted as a runtime Document and as an authored source Document")
        val admitted = locales.map(locale => CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document(locale)))
        val sources = locales.map(locale => CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document(locale)))

        Then("nested order, localized content, exact claim membership and global block targets retain their authored meaning")
        admitted.zip(sources).zipWithIndex.foreach { case ((document, source), index) =>
          val section = document.description.document.sections.head
          section.blocks.map(_.id) shouldBe Vector("tier-protection-introduction", "tier-comparison")
          section.sections.map(_.id) shouldBe Vector("protection-explanation")
          val figure = section.blocks(1).asInstanceOf[Figure]
          val table = section.sections.head.blocks.head.asInstanceOf[Table]
          figure.asset shouldBe s"images/tier-balance-${locales(index)}.png"
          figure.caption shouldBe _field(expectedfigures(index), "caption").asString.get
          figure.alt shouldBe _field(expectedfigures(index), "alt").asString.get
          figure.coreRefs.claims shouldBe Vector("claim-tier-balance")
          table.id shouldBe "protection-coverage"
          table.columns.map(_.id) shouldBe Vector("target", "mechanism", "location")
          table.columns.map(_.heading) shouldBe expectedheadings(index)
          table.rows.map(_.id) shouldBe Vector("service-boundary", "data-boundary")
          table.rows.map(_.cells) shouldBe expectedcells(index)
          table.coreRefs.claims shouldBe Vector("claim-protection-coverage")
          document.core.claimsById.keySet shouldBe Set("claim-tier-balance", "claim-protection-coverage")
          source.document shouldBe document.description.document
          source.targets shouldBe document.documentTargets
          source.targets.blockIds shouldBe Set("tier-protection-introduction", "tier-comparison", "protection-coverage")
        }
        admitted.head.description.document should not be admitted(1).description.document
      }
    }

    "permit a shared claim and independent table-local identity namespaces" in {
      _with_fixture { fixture =>
        Given("two tables sharing a claim and row, column and global block identity spellings")
        val source = _source(fixture.document("en"))
        val table = _updated(_table(source), "columns", Json.arr(Json.obj("id" -> Json.fromString("protection-coverage"), "heading" -> Json.fromString("Axis"))))
        val shared = _updated(_updated(table, "rows", Json.arr(Json.obj("id" -> Json.fromString("protection-coverage"), "cells" -> Json.arr(Json.fromString(""))))),
          "coreRefs", _updated(_field(table, "coreRefs"), "claims", Json.arr(Json.fromString("claim-tier-balance"))))
        val second = _updated(shared, "id", Json.fromString("second-table"))
        val changed = _replace_table(source, shared, Vector(second))
        _write(fixture.document("en"), changed)

        When("both loader routes admit the explicit repeated claim and separate local namespaces")
        val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))
        val authored = CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en"))

        Then("both tables preserve the same row and column names and the deliberately empty cell")
        val tables = document.description.document.sections.head.sections.head.blocks.map(_.asInstanceOf[Table])
        tables.map(_.columns.map(_.id)) shouldBe Vector.fill(2)(Vector("protection-coverage"))
        tables.map(_.rows) shouldBe Vector.fill(2)(Vector(TableRow("protection-coverage", Vector(""))))
        tables.foreach(_.coreRefs.claims shouldBe Vector("claim-tier-balance"))
        authored.document shouldBe document.description.document
        document.documentTargets.blockIds should contain ("second-table")
      }
    }

    "count figure and table claims in complete Document coverage" in {
      _with_fixture { fixture =>
        Given("a Document whose two claims are covered only by its explicit figure and table")
        val source = _source(fixture.document("en"))
        val section = _section(source)
        val nested = _nested(source)
        val blocks = _field(section, "blocks").asArray.get
        val paragraph = _updated(blocks.head, "coreRefs", _updated(_field(blocks.head, "coreRefs"), "claims", Json.arr()))
        val changednested = _updated(nested, "coreRefs", _updated(_field(nested, "coreRefs"), "claims", Json.arr()))
        val changedsection = _updated(_updated(_updated(section, "coreRefs", _updated(_field(section, "coreRefs"), "claims", Json.arr())),
          "blocks", Json.fromValues(blocks.updated(0, paragraph))), "sections", Json.arr(changednested))
        val changed = _replace_section(source, changedsection)
        _write(fixture.document("en"), changed)

        When("the real loaders admit complete coverage and then a table that leaves its original claim uncovered")
        val runtime = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))
        val authored = CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en"))
        val table = _table(changed)
        _write(fixture.document("en"), _replace_table(changed, _updated(table, "coreRefs", _updated(_field(table, "coreRefs"), "claims", Json.arr(Json.fromString("claim-tier-balance"))))))
        val faults = Vector(_description_fault(CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))),
          _description_fault(CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en"))))

        Then("new blocks supply exact claim coverage and an uncovered claim fails through the established coverage gate")
        authored.document shouldBe runtime.description.document
        runtime.documentTargets.blockIds should contain allOf ("tier-comparison", "protection-coverage")
        faults.map(_.code) shouldBe Vector.fill(2)("DESCRIPTION_V2_COVERAGE")
        faults.map(_.path) shouldBe Vector.fill(2)("$.document")
      }
    }

    "reject missing unresolved mistyped and duplicated claim references on either new block" in {
      _with_fixture { fixture =>
        Given("each explanatory block with an independently invalid claim selection")
        val source = _source(fixture.document("en"))
        val variants = Vector(
          Json.arr() -> "DESCRIPTION_V2_REFERENCE",
          Json.arr(Json.fromString("missing-claim")) -> "DESCRIPTION_V2_REFERENCE",
          Json.arr(Json.fromString("tier-a")) -> "DESCRIPTION_V2_REFERENCE",
          Json.arr(Json.fromString("claim-tier-balance"), Json.fromString("claim-tier-balance")) -> "DESCRIPTION_V2_IDENTITY"
        )

        When("the real loaders interpret the exact typed reference arrays")
        val faults = Vector("figure", "table").flatMap { kind =>
          variants.map { case (claims, code) =>
            val block = if (kind == "figure") _figure(source) else _table(source)
            val changed = _updated(block, "coreRefs", _updated(_field(block, "coreRefs"), "claims", claims))
            _write(fixture.document("en"), _replace_block(source, kind, changed))
            (code, _description_fault(CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))),
              _description_fault(CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en"))))
          }
        }

        Then("both routes reject the precise claim field with the reference or duplicate-identity diagnostic")
        faults.foreach { case (code, runtime, authored) =>
          runtime.code shouldBe code
          runtime.path should endWith (".coreRefs.claims")
          authored shouldBe runtime
        }
      }
    }

    "reject global identity collisions unknown fields and kinds and invalid explanatory wording" in {
      _with_fixture { fixture =>
        Given("independent malformed figure and table objects in otherwise complete Documents")
        val source = _source(fixture.document("en"))
        val variants = Vector("figure", "table").flatMap { kind =>
          val block = if (kind == "figure") _figure(source) else _table(source)
          Vector(
            (kind, _updated(block, "id", Json.fromString("tier-protection-explanation")), "DESCRIPTION_V2_IDENTITY"),
            (kind, _updated(block, "invented", Json.fromBoolean(true)), "DESCRIPTION_V2_FIELDS"),
            (kind, _updated(block, "kind", Json.fromString("chart")), "DESCRIPTION_V2_BLOCK_KIND"),
            (kind, _updated(block, "caption", Json.fromString(" ")), "DESCRIPTION_V2_WORDING")
          )
        } ++ Vector(
          ("figure", _updated(_figure(source), "alt", Json.fromString("")), "DESCRIPTION_V2_WORDING"),
          ("figure", _updated(_figure(source), "asset", Json.fromString(" image.png")), "DESCRIPTION_V2_WORDING")
        )

        When("both real loaders encounter each malformed authored block")
        val faults = variants.map { case (kind, block, code) =>
          _write(fixture.document("en"), _replace_block(source, kind, block))
          (code, _description_fault(CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))),
            _description_fault(CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en"))))
        }

        Then("the established identity, fields, kind and wording categories remain exact")
        faults.foreach { case (code, runtime, authored) =>
          runtime.code shouldBe code
          authored shouldBe runtime
        }
      }
    }

    "reject empty duplicate malformed and nonrectangular table data" in {
      _with_fixture { fixture =>
        Given("a valid table and independent violations of its closed rectangular source shape")
        val source = _source(fixture.document("en"))
        val table = _table(source)
        val columns = _field(table, "columns").asArray.get
        val rows = _field(table, "rows").asArray.get
        val firstrow = rows.head
        val cells = _field(firstrow, "cells").asArray.get
        val variants = Vector(
          _updated(table, "columns", Json.arr()) -> "DESCRIPTION_V2_TABLE",
          _updated(table, "rows", Json.arr()) -> "DESCRIPTION_V2_TABLE",
          _updated(table, "columns", Json.fromValues(columns.updated(1, columns.head))) -> "DESCRIPTION_V2_IDENTITY",
          _updated(table, "rows", Json.fromValues(rows.updated(1, firstrow))) -> "DESCRIPTION_V2_IDENTITY",
          _updated(table, "columns", Json.arr(_updated(columns.head, "extra", Json.Null))) -> "DESCRIPTION_V2_FIELDS",
          _updated(table, "columns", Json.arr(_without(columns.head, "heading"))) -> "DESCRIPTION_V2_FIELDS",
          _updated(table, "rows", Json.arr(_without(firstrow, "cells"))) -> "DESCRIPTION_V2_FIELDS",
          _updated(table, "rows", Json.arr(_updated(firstrow, "extra", Json.Null))) -> "DESCRIPTION_V2_FIELDS",
          _updated(table, "rows", Json.arr(_updated(firstrow, "cells", Json.fromString("not an array")))) -> "DESCRIPTION_V2_STRUCTURE",
          _updated(table, "rows", Json.arr(_updated(firstrow, "cells", Json.fromValues(cells.dropRight(1))))) -> "DESCRIPTION_V2_TABLE",
          _updated(table, "rows", Json.arr(_updated(firstrow, "cells", Json.fromValues(cells :+ Json.fromString("extra"))))) -> "DESCRIPTION_V2_TABLE"
        ) ++ Vector(Json.fromInt(7), Json.fromBoolean(false), Json.Null, Json.arr()).map { invalid =>
          _updated(table, "rows", Json.arr(_updated(firstrow, "cells", Json.fromValues(cells.updated(1, invalid))))) -> "DESCRIPTION_V2_STRUCTURE"
        }

        When("both real loaders interpret the malformed table variants")
        val faults = variants.map { case (changed, code) =>
          _write(fixture.document("en"), _replace_table(source, changed))
          (code, _description_fault(CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))),
            _description_fault(CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en"))))
        }

        Then("table cardinality and width, duplicate local identities, object fields and string shape have distinct diagnostics")
        faults.foreach { case (code, runtime, authored) =>
          runtime.code shouldBe code
          authored shouldBe runtime
        }
      }
    }

    "preserve every ordered rectangular Unicode and markup cell while rejecting varying wrong widths" in {
      _with_fixture { fixture =>
        Given("bounded generated table dimensions, independent row and column order, and exact authored string cells")
        val source = _source(fixture.document("en"))
        val generator = for {
          width <- Gen.choose(1, 5)
          height <- Gen.choose(1, 5)
          columnorder <- Gen.pick(width, 0 until width)
          roworder <- Gen.pick(height, 0 until height)
          reversecolumns <- Gen.oneOf(true, false)
          reverserows <- Gen.oneOf(true, false)
          cells <- Gen.listOfN(width * height, Gen.oneOf("", "  ", " 日本語 ", "Δοκιμή", "<tag>&\"'", "line\nbreak", "é🙂"))
          extra <- Gen.oneOf(true, false)
        } yield (if (reversecolumns) columnorder.toVector.reverse else columnorder.toVector,
          if (reverserows) roworder.toVector.reverse else roworder.toVector, cells.toVector.grouped(width).toVector, extra)

        When("the real admission routes interpret each generated table and its independently wrong-width counterpart")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(35), Prop.forAll(generator) { sample =>
          val (columnorder, roworder, matrix, extra) = sample
          val columns = columnorder.map(index => Json.obj("id" -> Json.fromString(s"column-$index"), "heading" -> Json.fromString(s"""比較 <$index> & "axis""" + "\"")))
          val rows = roworder.map(index => Json.obj("id" -> Json.fromString(s"row-$index"), "cells" -> Json.fromValues(columnorder.map(column => Json.fromString(matrix(index)(column))))))
          val changed = _updated(_updated(_table(source), "columns", Json.fromValues(columns)), "rows", Json.fromValues(rows))
          _write(fixture.document("en"), _replace_table(source, changed))
          val admitted = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))
          val authored = CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en"))
          val actual = admitted.description.document.sections.head.sections.head.blocks.head.asInstanceOf[Table]
          val firstcells = _field(rows.head, "cells").asArray.get
          val wrongcells = if (extra) firstcells :+ Json.fromString("extra") else firstcells.dropRight(1)
          val wrong = _updated(changed, "rows", Json.fromValues(rows.updated(0, _updated(rows.head, "cells", Json.fromValues(wrongcells)))))
          _write(fixture.document("en"), _replace_table(source, wrong))
          val runtimefault = _description_fault(CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en")))
          val sourcefault = _description_fault(CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en")))
          actual.columns == columnorder.map(index => TableColumn(s"column-$index", s"""比較 <$index> & "axis""" + "\"")) &&
            actual.rows == roworder.map(index => TableRow(s"row-$index", columnorder.map(column => matrix(index)(column)))) &&
            authored.document == admitted.description.document && runtimefault.code == "DESCRIPTION_V2_TABLE" && sourcefault == runtimefault
        })

        Then("all generated ordered strings survive unchanged and every missing or extra cell fails table admission")
        result.passed shouldBe true
      }
    }
  }

  "Declared raster materialization" should {
    "embed only first-use distinct PNG and JPEG inputs from the actual project" in {
      _with_fixture { fixture =>
        Given("the paired raster fixtures, unselected SVG masters and an ImageIO-authored JPEG")
        val source = _source(fixture.document("en"))
        val figure = _figure(source)
        val jpeg = fixture.root.resolve("images/extra.JpEg")
        if (!ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg.toFile))
          throw new IllegalStateException("standard JPEG writer is required for this specification")
        val expectedpng = "data:image/png;base64," + Base64.getEncoder.encodeToString(Files.readAllBytes(fixture.root.resolve("images/tier-balance-en.png")))
        val expectedjpeg = "data:image/jpeg;base64," + Base64.getEncoder.encodeToString(Files.readAllBytes(jpeg))
        val duplicate = _updated(figure, "id", Json.fromString("same-image"))
        val second = _updated(_updated(figure, "id", Json.fromString("jpeg-image")), "asset", Json.fromString("images/extra.JpEg"))
        val section = _section(source)
        val nested = _nested(source)
        val changed = _updated(section, "sections", Json.arr(_updated(nested, "blocks", Json.fromValues(Vector(second, duplicate, _table(source))))))
        _write(fixture.document("en"), _replace_section(source, changed))
        val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))

        When("declared references are traversed depth first and materialized from the explicit canonical root")
        val references = CozyDocumentFigureAssets.references(document)
        val assets = CozyDocumentFigureAssets.materialize(fixture.root, document)
        val explicit = CozyDocumentFigureAssets.materialize(fixture.root, references ++ references)
        val pnguri = assets.dataUri(references.head)
        val jpeguri = assets.dataUri(references(1))

        Then("first-use source paths and data URIs contain the selected actual bytes without adding the SVG masters")
        references shouldBe Vector("images/tier-balance-en.png", "images/extra.JpEg")
        assets.sourcePaths shouldBe references.map(fixture.root.resolve(_))
        explicit.sourcePaths shouldBe assets.sourcePaths
        pnguri shouldBe expectedpng
        jpeguri shouldBe expectedjpeg
        explicit.dataUri(references.head) shouldBe pnguri
        assets.sourcePaths.exists(_.toString.endsWith(".svg")) shouldBe false
      }
    }

    "reject noncanonical roots unsafe references missing files and symlink routes with exact context" in {
      _with_fixture { fixture =>
        Given("a direct project and unsafe roots, path syntax, absent files and direct or ancestor symlinks")
        val png = fixture.root.resolve("images/tier-balance-en.png")
        val alias = Files.createSymbolicLink(fixture.root.resolve("project-alias"), fixture.root)
        Files.createSymbolicLink(fixture.root.resolve("images/linked.png"), png)
        Files.createSymbolicLink(fixture.root.resolve("linked-images"), fixture.root.resolve("images"))
        val roots = Vector(Paths.get("relative-project"), fixture.root.resolve("missing-root"), png, alias, fixture.root.resolve("content/.."))
        val references = Vector("", " image.png", "/absolute.png", "../outside.png", "images/../image.png", "images//image.png", "images/./image.png",
          "https://example.com/a.png", "file:image.png", "images\\image.png", "images/a.png?query", "images/a.png#fragment", "images/a\u0000.png", "images/a\n.png",
          "images/linked.png", "linked-images/tier-balance-en.png")

        When("the explicit materialization boundary admits each selected root and reference")
        val rootfaults = roots.map(root => _asset_fault(CozyDocumentFigureAssets.materialize(root, Vector.empty)))
        val pathfaults = references.map(reference => _asset_fault(CozyDocumentFigureAssets.materialize(fixture.root, Vector(reference))))
        val filefaults = Vector("images/missing.png", "images", "absent/image.png").map(reference =>
          _asset_fault(CozyDocumentFigureAssets.materialize(fixture.root, Vector(reference))))

        Then("root, unsafe-path and direct-file diagnoses remain distinct and identify the actual authored selection")
        rootfaults.map(_.code) shouldBe Vector.fill(roots.size)("FIGURE_ASSET_ROOT")
        rootfaults.map(_.path) shouldBe roots.map(_.toString)
        pathfaults.map(_.code) shouldBe Vector.fill(references.size)("FIGURE_ASSET_PATH")
        pathfaults.map(_.path) shouldBe references
        filefaults.map(_.code) shouldBe Vector.fill(3)("FIGURE_ASSET_FILE")
        filefaults.map(_.path) shouldBe Vector("images/missing.png", "images", "absent/image.png")
      }
    }

    "reject SVG disguised mismatched and truncated images instead of trusting a filename" in {
      _with_fixture { fixture =>
        Given("an SVG master, text named PNG, PNG bytes named JPEG and truncated PNG and JPEG inputs")
        val bytes = Files.readAllBytes(fixture.root.resolve("images/tier-balance-en.png"))
        Files.write(fixture.root.resolve("images/text.png"), "not an image".getBytes(StandardCharsets.UTF_8))
        Files.write(fixture.root.resolve("images/mismatch.jpg"), bytes)
        Files.write(fixture.root.resolve("images/truncated.png"), bytes.take(24))
        Files.write(fixture.root.resolve("images/truncated.jpeg"), Array[Byte](-1, -40, -1))
        val references = Vector("images/tier-balance-en.svg", "images/text.png", "images/mismatch.jpg", "images/truncated.png", "images/truncated.jpeg")

        When("real file bytes pass through extension, signature and standard image decoding admission")
        val faults = references.map(reference => _asset_fault(CozyDocumentFigureAssets.materialize(fixture.root, Vector(reference))))

        Then("every unsupported or malformed image has a format diagnostic carrying its authored reference")
        faults.map(_.code) shouldBe Vector.fill(references.size)("FIGURE_ASSET_FORMAT")
        faults.map(_.path) shouldBe references
      }
    }

    "report an ordinary unreadable selected image as an I/O fault" in {
      _with_fixture { fixture =>
        Given("a selected existing PNG whose read permission is denied")
        val reference = "images/tier-balance-en.png"
        val path = fixture.root.resolve(reference)
        val permissions = Files.getPosixFilePermissions(path)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("---------"))

        When("materialization attempts to read the admitted direct file")
        val fault = try _asset_fault(CozyDocumentFigureAssets.materialize(fixture.root, Vector(reference)))
          finally Files.setPosixFilePermissions(path, permissions)

        Then("the failure retains ordinary I/O classification and the exact declared reference")
        fault.code shouldBe "FIGURE_ASSET_IO"
        fault.path shouldBe reference
      }
    }
  }

  "Pure figure and table projections" should {
    "link claims-only figures and tables back from their exact owning Core Steps" in {
      _with_fixture { fixture =>
        Given("a figure and table with empty Step references and claims owned separately by a root and its direct child")
        val core = _source(fixture.core)
        val root = _field(core, "root")
        val child = Json.obj(
          "id" -> Json.fromString("protection-owner"), "semanticRole" -> Json.fromString("explanation"),
          "claims" -> Json.arr(Json.fromString("claim-protection-coverage")),
          "structure" -> Json.obj("pattern" -> Json.fromString("mapping"),
            "nodes" -> Json.arr(
              Json.obj("id" -> Json.fromString("child-source"), "role" -> Json.fromString("source")),
              Json.obj("id" -> Json.fromString("child-target"), "role" -> Json.fromString("target"))),
            "relations" -> Json.arr(Json.obj("id" -> Json.fromString("child-mapping"), "relationType" -> Json.fromString("maps-to"),
              "from" -> Json.fromString("child-source"), "to" -> Json.fromString("child-target")))),
          "flow" -> Json.obj("id" -> Json.fromString("protection-owner-flow"), "transitions" -> Json.arr()),
          "steps" -> Json.arr())
        _write(fixture.core, _updated(core, "root", _updated(_updated(root, "claims", Json.arr(Json.fromString("claim-tier-balance"))),
          "steps", Json.arr(child))))
        val source = _source(fixture.document("en"))
        val figure = _figure(source)
        val table = _table(source)
        val changedblocks = _replace_table(_replace_block(source, "figure",
          _updated(figure, "coreRefs", _updated(_field(figure, "coreRefs"), "steps", Json.arr()))),
          _updated(table, "coreRefs", _updated(_field(table, "coreRefs"), "steps", Json.arr())))
        val section = _section(changedblocks)
        val refs = _field(section, "coreRefs")
        val expandedrefs = _updated(_updated(_updated(_updated(refs,
          "steps", Json.fromValues(_field(refs, "steps").asArray.get :+ Json.fromString("protection-owner"))),
          "nodes", Json.fromValues(_field(refs, "nodes").asArray.get ++ Vector(Json.fromString("child-source"), Json.fromString("child-target")))),
          "relations", Json.fromValues(_field(refs, "relations").asArray.get :+ Json.fromString("child-mapping"))),
          "flows", Json.fromValues(_field(refs, "flows").asArray.get :+ Json.fromString("protection-owner-flow")))
        val labels = _field(source, "labels")
        val expandedlabels = _updated(_updated(labels,
          "steps", Json.fromValues(_field(labels, "steps").asArray.get :+ Json.obj(
            "stepRef" -> Json.fromString("protection-owner"), "text" -> Json.fromString("Protection owner")))),
          "nodes", Json.fromValues(_field(labels, "nodes").asArray.get ++ Vector(
            Json.obj("nodeRef" -> Json.fromString("child-source"), "text" -> Json.fromString("Source")),
            Json.obj("nodeRef" -> Json.fromString("child-target"), "text" -> Json.fromString("Target")))))
        _write(fixture.document("en"), _updated(_replace_section(changedblocks, _updated(section, "coreRefs", expandedrefs)), "labels", expandedlabels))

        When("the real Core and Document loaders admit the sources and the pure Reader renders their materialized PNG")
        val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))
        val authored = CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, fixture.document("en"))
        val assets = CozyDocumentFigureAssets.materialize(fixture.root, document)
        val reader = CozyDocumentReaderProjectionV2.render(document, _vocabulary(document.core), assets).html

        Then("each Core Step links only its own claim's explanatory block and preserves both authored empty Step arrays")
        val regions = Vector("tier-protection", "protection-owner").map { id =>
          val start = reader.indexOf(s"""<article id="core-step-$id""" + "\"")
          start should be >= 0
          reader.substring(start, reader.indexOf("</article>", start) + "</article>".length)
        }
        regions.head should include ("href=\"#document-block-tier-comparison\"")
        regions.head should not include "href=\"#document-block-protection-coverage\""
        regions(1) should include ("href=\"#document-block-protection-coverage\"")
        regions(1) should not include "href=\"#document-block-tier-comparison\""
        val figuretag = _tag(reader, "id=\"document-block-tier-comparison\"")
        val tabletag = _tag(reader, "id=\"document-block-protection-coverage\"")
        figuretag should include ("data-core-steps=\"\"")
        figuretag should include ("data-core-claims=\"claim-tier-balance\"")
        tabletag should include ("data-core-steps=\"\"")
        tabletag should include ("data-core-claims=\"claim-protection-coverage\"")
        reader should include ("href=\"#core-claim-claim-tier-balance\"")
        reader should include ("href=\"#core-claim-claim-protection-coverage\"")
        authored.document shouldBe document.description.document
      }
    }

    "retain exact correspondence semantic markup escaped wording and deterministic self-contained images in both locales" in {
      _with_fixture { fixture =>
        Given("both actual admitted localized Documents and immutable materialized raster values")
        val documents = Vector("ja", "en").map { locale =>
          val source = _source(fixture.document(locale))
          val allrefs = _field(_section(source), "coreRefs")
          val figure = _updated(_figure(source), "coreRefs", allrefs)
          _write(fixture.document(locale), _replace_block(source, "figure", figure))
          val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document(locale))
          (document, CozyDocumentFigureAssets.materialize(fixture.root, document))
        }

        When("both pure projections receive those values after the source image files are removed")
        documents.flatMap(_._2.sourcePaths).distinct.foreach(Files.delete(_))
        val pages = documents.map { case (document, assets) =>
          val vocabulary = _vocabulary(document.core)
          (document, assets, CozyDocumentConfirmationProjectionV2.render(document, vocabulary, assets).html,
            CozyDocumentReaderProjectionV2.render(document, vocabulary, assets).html,
            CozyDocumentConfirmationProjectionV2.render(document, vocabulary, assets).html,
            CozyDocumentReaderProjectionV2.render(document, vocabulary, assets).html)
        }

        Then("figures and tables remain ordered, portable and precisely linked to all five typed memberships without rendering I/O")
        pages.foreach { case (document, assets, confirmation, reader, repeatedconfirmation, repeatedreader) =>
          val figure = document.description.document.sections.head.blocks(1).asInstanceOf[Figure]
          val table = document.description.document.sections.head.sections.head.blocks.head.asInstanceOf[Table]
          Vector(confirmation, reader).foreach { html =>
            html should include (s"<figcaption>${figure.caption}</figcaption>")
            html should include (s"""alt="${figure.alt}""" + "\"")
            html should include (s"""src="${assets.dataUri(figure.asset)}""" + "\"")
            html should include (s"<caption>${table.caption}</caption>")
            html should include (s"""<th scope="col" data-table-column-id="target">${table.columns.head.heading}</th>""")
            table.rows.foreach(row => row.cells.foreach(cell => html should include (s"<td>$cell</td>")))
            html.indexOf("<figure ") should be < html.indexOf("<table>")
            val tag = _tag(html, if (html == confirmation) "data-block-id=\"tier-comparison\"" else "id=\"document-block-tier-comparison\"")
            tag should include ("data-reference-target=\"true\"")
            tag should include ("data-core-steps=\"tier-protection\"")
            tag should include ("data-core-claims=\"claim-tier-balance claim-protection-coverage\"")
            tag should include ("data-core-nodes=\"tier-a tier-b protection-target performance-security protection-mechanism application-location\"")
            tag should include ("data-core-relations=\"tier-a-balances tier-b-balances target-mechanism target-location\"")
            tag should include ("data-core-flows=\"tier-protection-flow\"")
            val tabletag = _tag(html, if (html == confirmation) "data-block-id=\"protection-coverage\"" else "id=\"document-block-protection-coverage\"")
            tabletag should include ("data-core-claims=\"claim-protection-coverage\"")
            Vector("steps", "claims", "nodes", "relations", "flows").foreach(kind => tabletag should include (s"""data-core-$kind=""" + "\""))
            tabletag should include ("data-core-flows=\"\"")
            if (html == reader) {
              tabletag should include ("data-document-kind=\"block\"")
              tabletag should include ("data-document-id=\"protection-coverage\"")
            }
            html should include ("max-width:100%")
            html should include ("overflow-wrap:anywhere")
          }
          val paragraphtag = _tag(reader, "id=\"document-block-tier-protection-introduction\"")
          paragraphtag should include ("data-core-flows=\"\"")
          paragraphtag should include ("data-document-kind=\"block\"")
          paragraphtag should include ("data-document-id=\"tier-protection-introduction\"")
          confirmation should include (".document-block.is-highlighted")
          confirmation should include ("scrollIntoView")
          reader should include ("href=\"#document-block-tier-comparison\"")
          reader should include ("href=\"#document-block-protection-coverage\"")
          reader should include ("href=\"#core-claim-claim-tier-balance\"")
          reader should include ("href=\"#core-claim-claim-protection-coverage\"")
          repeatedconfirmation shouldBe confirmation
          repeatedreader shouldBe reader
        }
      }
    }

    "escape authored caption alt headings and cells while preserving empty and whitespace cells" in {
      _with_fixture { fixture =>
        Given("real source strings containing Unicode, markup, quotes, an empty cell and significant whitespace")
        val source = _source(fixture.document("en"))
        val wording = "比較 <tag> & \"quoted\" 'single'"
        val figure = _updated(_updated(_figure(source), "caption", Json.fromString(wording)), "alt", Json.fromString(wording))
        val table = _table(source)
        val columns = _field(table, "columns").asArray.get
        val rows = _field(table, "rows").asArray.get
        val changedtable = _updated(_updated(_updated(table, "caption", Json.fromString(wording)), "columns", Json.fromValues(columns.updated(0, _updated(columns.head, "heading", Json.fromString(wording))))),
          "rows", Json.fromValues(rows.updated(0, _updated(rows.head, "cells", Json.arr(Json.fromString(wording), Json.fromString(""), Json.fromString("  日本語 \n "))))))
        _write(fixture.document("en"), _replace_table(_replace_block(source, "figure", figure), changedtable))
        val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))
        val assets = CozyDocumentFigureAssets.materialize(fixture.root, document)

        When("both projections render the admitted authored text with supplied assets")
        val pages = Vector(CozyDocumentConfirmationProjectionV2.render(document, _vocabulary(document.core), assets).html,
          CozyDocumentReaderProjectionV2.render(document, _vocabulary(document.core), assets).html)

        Then("semantic text is escaped completely and exact cell content and order survive")
        val escaped = "比較 &lt;tag&gt; &amp; &quot;quoted&quot; &#39;single&#39;"
        pages.foreach { html =>
          html should include (s"<figcaption>$escaped</figcaption>")
          html should include (s"""alt="$escaped""" + "\"")
          html should include (s"<caption>$escaped</caption>")
          html should include (s"""<th scope="col" data-table-column-id="target">$escaped</th>""")
          html should include (s"<td>$escaped</td><td></td><td>  日本語 \n </td>")
          html should not include "<tag>"
          html should not include " id=\"target\""
        }
      }
    }

    "fail explicitly when either legacy render call encounters an unmaterialized figure" in {
      _with_fixture { fixture =>
        Given("a real admitted Document with a declared figure and no materialized assets")
        val document = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document("en"))
        val vocabulary = _vocabulary(document.core)

        When("the source-compatible two-argument calls render that Document")
        val faults = Vector(
          _asset_fault(CozyDocumentConfirmationProjectionV2.render(document, vocabulary)),
          _asset_fault(CozyDocumentReaderProjectionV2.render(document, vocabulary)),
          _asset_fault(CozyDocumentFigureAssets.empty.dataUri("undeclared.png"))
        )

        Then("missing immutable image values have an explicit failure without placeholders")
        faults.map(_.code) shouldBe Vector.fill(3)("FIGURE_ASSET_MISSING")
        faults.map(_.path) shouldBe Vector("images/tier-balance-en.png", "images/tier-balance-en.png", "undeclared.png")
        CozyDocumentFigureAssets.empty.sourcePaths shouldBe Vector.empty
      }
    }

    "resolve explicit Summary block omissions and display labels without inferred image or table slides" in {
      _with_fixture { fixture =>
        Given("each actual localized Document and a same-locale Summary explicitly omitting its figure and table")
        val locales = Vector("ja", "en")
        locales.foreach { locale =>
          val source = _source(fixture.document(locale))
          _write(fixture.root.resolve(s"content/$locale/summary.yaml"), _summary(source, locale))
        }

        When("the real Summary loader resolves omissions and the Summary confirmation projection renders them")
        val admitted = locales.map(locale => CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document(locale), fixture.root.resolve(s"content/$locale/summary.yaml")))
        val rendered = admitted.map(value => CozySummaryConfirmationProjectionV2.render(value, _summary_vocabulary(value.document.core)).html)

        Then("both ordinary block targets expose caption, alternative and table content without materializing assets or inferring diagrams")
        admitted.zip(rendered).foreach { case (summary, html) =>
          val source = summary.document.description.document.sections.head
          val figure = source.blocks(1).asInstanceOf[Figure]
          val table = source.sections.head.blocks.head.asInstanceOf[Table]
          val unit = summary.description.summary.units.head
          unit.omissions.map(value => value.documentKind -> value.documentRef) shouldBe Vector("block" -> figure.id, "block" -> table.id)
          unit.diagram shouldBe None
          html should include (figure.caption)
          html should include (figure.alt)
          html should include (table.caption)
          table.columns.foreach(column => html should include (column.heading))
          table.rows.flatMap(_.cells).foreach(cell => html should include (cell))
          html should include ("data-document-ref=\"tier-comparison\"")
          html should include ("data-document-ref=\"protection-coverage\"")
          html should not include "<img"
          html should not include "<table"
          html should not include "data:image/"
          html should include ("data-empty-diagram=\"true\"")
        }
      }
    }
  }

  private def _field(value: Json, name: String): Json = value.hcursor.downField(name).focus.get
  private def _updated(value: Json, name: String, content: Json): Json = Json.fromJsonObject(value.asObject.get.add(name, content))
  private def _without(value: Json, name: String): Json = Json.fromJsonObject(value.asObject.get.remove(name))
  private def _section(source: Json): Json = _field(_field(source, "document"), "sections").asArray.get.head
  private def _nested(source: Json): Json = _field(_section(source), "sections").asArray.get.head
  private def _figure(source: Json): Json = _field(_section(source), "blocks").asArray.get(1)
  private def _table(source: Json): Json = _field(_nested(source), "blocks").asArray.get.head
  private def _replace_section(source: Json, section: Json): Json =
    _updated(source, "document", _updated(_field(source, "document"), "sections", Json.arr(section)))

  private def _replace_table(source: Json, table: Json, additional: Vector[Json] = Vector.empty): Json =
    _replace_section(source, _updated(_section(source), "sections", Json.arr(_updated(_nested(source), "blocks", Json.fromValues(table +: additional)))))

  private def _replace_block(source: Json, kind: String, block: Json): Json =
    if (kind == "table") _replace_table(source, block)
    else _replace_section(source, _updated(_section(source), "blocks", Json.fromValues(_field(_section(source), "blocks").asArray.get.updated(1, block))))

  private def _source(path: Path): Json = _json(new Yaml().load[Any](Files.readString(path, StandardCharsets.UTF_8)))
  private def _json(value: Any): Json = value match {
    case null => Json.Null
    case text: String => Json.fromString(text)
    case values: java.util.Map[_, _] => Json.obj(values.asScala.toVector.map { case (key, item) => key.toString -> _json(item) }: _*)
    case values: java.util.List[_] => Json.fromValues(values.asScala.map(_json))
    case value: java.lang.Boolean => Json.fromBoolean(value.booleanValue)
    case value: java.lang.Number => Json.fromBigDecimal(BigDecimal(value.toString))
    case other => throw new IllegalArgumentException(s"unsupported fixture value: ${other.getClass.getName}")
  }

  private def _write(path: Path, value: Json): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, value.noSpaces.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def _description_fault(body: => Any): CozyDocumentDescriptionV2.DescriptionV2Fault =
    intercept[CozyDocumentDescriptionV2.DescriptionV2Fault](body)
  private def _asset_fault(body: => Any): CozyDocumentFigureAssets.FigureAssetFault =
    intercept[CozyDocumentFigureAssets.FigureAssetFault](body)

  private def _vocabulary(core: CozyDocumentLogicTree.ValidatedCore): CozyDocumentConfirmationProjectionV2.Vocabulary = {
    val chrome = CozyDocumentConfirmationProjectionV2.Chrome("Document", "Status", "Coverage", "Current", "Admitted", "Resolved", "Containment", "Flow", "Structure", "Prose", "Pattern", "Nodes", "Role", "Relations", "No transitions", "Structure reference", "Identities")
    CozyDocumentConfirmationProjectionV2.Vocabulary(chrome,
      _wording(core.depthFirstSteps.map(_.structure.pattern).toSet), _wording(core.depthFirstSteps.flatMap(_.structure.nodes.map(_.role)).toSet),
      _wording(core.depthFirstSteps.flatMap(_.structure.relations.map(_.relationType)).toSet), _wording(core.depthFirstSteps.flatMap(_.flow.transitions.map(_.relationType)).toSet))
  }

  private def _summary_vocabulary(core: CozyDocumentLogicTree.ValidatedCore): CozySummaryConfirmationProjectionV2.Vocabulary = {
    val chrome = CozySummaryConfirmationProjectionV2.Chrome("Summary", "Status", "Current admitted sources", "Resolved", "Navigation", "Explanation", "Emphasis", "Points", "Diagram", "Items", "Edges", "No authored diagram", "Sources", "Omissions", "Rationale")
    val vocabulary = _vocabulary(core)
    CozySummaryConfirmationProjectionV2.Vocabulary(chrome, _wording(Set("steps", "claims", "nodes", "relations", "flows")),
      _wording(Set("step", "node")), vocabulary.relationTypes, vocabulary.flowTypes, _wording(Set("section", "block", "list-item")),
      _wording(Set("omitted", "condensed")), _wording(Set("forward", "inverse")), vocabulary.logicalPatterns, vocabulary.relationTypes, vocabulary.flowTypes)
  }

  private def _wording(values: Set[String]): Map[String, String] = values.toVector.map(value => value -> s"Meaning $value").toMap

  private def _summary(source: Json, locale: String): Json = {
    val refs = _field(_section(source), "coreRefs")
    val omissions = Vector("tier-comparison", "protection-coverage").map(id => Json.obj(
      "id" -> Json.fromString(s"omit-$id"), "documentKind" -> Json.fromString("block"), "documentRef" -> Json.fromString(id),
      "disposition" -> Json.fromString("omitted"), "rationale" -> Json.fromString("The body retains this complete explanation.")))
    val unit = Json.obj("id" -> Json.fromString("overview-message"), "heading" -> Json.fromString("Responsibility summary"),
      "message" -> Json.fromString("Both tier balance and protection responsibility are explained in the body."), "emphasis" -> Json.fromString("primary"),
      "coreRefs" -> refs, "navigationLabel" -> Json.fromString("Responsibility"),
      "retainedPoints" -> Json.arr(Json.obj("id" -> Json.fromString("point"), "text" -> Json.fromString("Read the complete comparison and matrix in the body."), "coreRefs" -> refs)),
      "omissions" -> Json.fromValues(omissions))
    Json.obj("schema" -> Json.fromString("cozy.summary-description.v2"), "id" -> Json.fromString(s"tier-summary-$locale"),
      "core" -> _field(source, "core"), "document" -> Json.obj("id" -> _field(source, "id")), "locale" -> Json.fromString(locale),
      "summary" -> Json.obj("title" -> Json.fromString("Summary"), "units" -> Json.arr(unit)))
  }

  private def _tag(html: String, marker: String): String = {
    val index = html.indexOf(marker)
    require(index >= 0, s"missing semantic target: $marker")
    html.substring(html.lastIndexOf('<', index), html.indexOf('>', index) + 1)
  }

  private def _with_fixture(body: Fixture => Unit): Unit = {
    val parent = Paths.get("target/phase87/spec").toAbsolutePath.normalize
    Files.createDirectories(parent)
    val root = Files.createDirectory(parent.resolve(UUID.randomUUID().toString)).toRealPath()
    val source = Paths.get(getClass.getResource("/cozy/document/phase-87/tier-protection/content/core.yaml").toURI).getParent.getParent
    val stream = Files.walk(source)
    try stream.iterator.asScala.foreach { path =>
      val destination = root.resolve(source.relativize(path))
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination)
      else Files.copy(path, destination)
    } finally stream.close()
    try body(Fixture(root))
    finally {
      val paths = Files.walk(root)
      try paths.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
      finally paths.close()
    }
  }
}
