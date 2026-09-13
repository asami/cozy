package cozy.document

import cozy.media.{CozyMedia, CozyVisualPage, CozyVisualPageBinding}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozySummarySlidePdfAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Article 9 Summary Slide PDF" should {
    "render the static Japanese projection with ordered provenance and distinct flow semantics" in {
      _with_work("cozy-summary-slide-pdf-article-9") { root =>
        Given("the checked-in Article 9 v2 authorities, projection profile, catalog, binding, and local media dependencies")
        val fixture = _copy_fixture(root)
        _accept_prebuilt_dependencies(fixture)
        val runner = _renderer(root)

        When("the existing Summary Slide PDF coordinator builds the selected target")
        CozySummarySlidePdf.build(root, root.relativize(fixture.profile), runner)
        val connection = CozySummarySlidePdf.prepare(root, root.relativize(fixture.profile))
        val visual = CozyVisualPage.load(fixture.pageset, fixture.catalog)
        val binding = CozyVisualPageBinding.load(fixture.binding, visual)

        Then("the emitted PDF has the complete ordered page set and exact source and Summary provenance")
        _page_count(fixture.pdf) shouldBe 3
        visual.document.pages.map(_.id) shouldBe Vector(
          "overview-domain-to-application",
          "overview-realization-to-foundation",
          "overview-conclusion-to-realization"
        )
        visual.document.pages.map(_.logical.pattern) shouldBe Vector("mapping", "dependency-map", "dependency-map")
        visual.document.pages.map(_.visual.pattern) shouldBe Vector("mapping-columns", "flow-vertical", "flow-vertical")
        visual.document.pages.map(_.logical.relations.map(relation => (relation.id, relation.relationType, relation.from, relation.to))) shouldBe Vector(
          Vector(("overview-domain-to-application", "maps-to", "overview-domain-model", "overview-application-model")),
          Vector(("overview-realization-to-foundation", "depends-on", "overview-realization", "overview-foundation")),
          Vector(("overview-conclusion-to-realization", "depends-on", "overview-conclusion", "overview-realization"))
        )
        visual.document.pages.foreach { page =>
          page.sources.map(_.id) shouldBe Vector("core", "document", "summary", "profile", "media")
          page.sources.map(_.path) shouldBe Vector("content-v2/core.yaml", "content-v2/ja/document.yaml", "content-v2/ja/summary.yaml", "projection.yaml", "media.yaml")
          page.sources.find(_.id == "summary").map(_.path) shouldBe Some("content-v2/ja/summary.yaml")
          page.assets.map(_.id) shouldBe Vector("infographic")
        }
        visual.document.pages.head.logical.nodes.map(_.id) shouldBe Vector("overview-domain-model", "overview-application-model")
        visual.document.pages.tail.flatMap(_.logical.nodes.map(_.id)).toSet should contain allOf ("overview-realization", "overview-foundation", "overview-conclusion")
        connection.projection.provenance.summary.id shouldBe "application-modeling-summary-ja-v2"
        connection.projection.provenance.summary.path shouldBe "content-v2/ja/summary.yaml"
        connection.projection.provenance.summary.identity shouldBe "sha256:d66661e755bc446c3b90ce6fb45fc15ef0d19a5500af4605dfd935e0564ee744"
        connection.projection.provenance.core.identity shouldBe "sha256:6afe2e0a8bc534be97e12fb34dffd0175ee82baccb22b98923f92801d3ee0998"
        connection.projection.provenance.document.identity shouldBe "sha256:5e91fb42d9e18ab3a8209de937b691c7ae4075b4ce2e227b6e74fcc6d28703ac"
        connection.projection.provenance.media.identity shouldBe "sha256:a1e376f0701c5694a8bc129117090cdf026d56d9b1ce16ca7810b5918a5db8a1"
        connection.projection.provenance.catalog.identity shouldBe "sha256:4a292696d0f8c85833345cae59382c9c2ddb74c9d9488142d0a74706fe102b59"
        connection.projection.provenance.binding.identity shouldBe "sha256:203279df1af258e201c27d86574d07a89e1289a2cc7b5b6706348d67c92a1dd3"
        connection.projection.provenance.mediaTarget shouldBe "summary-slides-pdf"
        binding.id shouldBe "business-binding"
        binding.profile shouldBe "business"
        Files.readString(fixture.renderermanifest, StandardCharsets.UTF_8) should include("\"pageCount\":3")
        Files.readString(fixture.renderermanifest, StandardCharsets.UTF_8) should include("\"overview-realization-to-foundation\"")
        Files.readString(fixture.renderermanifest, StandardCharsets.UTF_8) should include("\"overview-conclusion-to-realization\"")
      }
    }

    "produce deterministic PageSet, PDF, and renderer evidence for unchanged authorities" in {
      _with_work("cozy-summary-slide-pdf-deterministic") { root =>
        Given("one accepted static Article 9 projection and its Phase 40-compatible fake renderer")
        val fixture = _copy_fixture(root)
        _accept_prebuilt_dependencies(fixture)
        val runner = _renderer(root)

        When("the unchanged projection is requested again after its first accepted build")
        CozySummarySlidePdf.build(root, root.relativize(fixture.profile), runner)
        val priorpageset = Files.readAllBytes(fixture.pageset).toVector
        val priorpdf = Files.readAllBytes(fixture.pdf).toVector
        val priormanifest = Files.readAllBytes(fixture.renderermanifest).toVector
        CozySummarySlidePdf.build(root, root.relativize(fixture.profile), runner)

        Then("the accepted PageSet, PDF, and renderer manifest remain byte-identical")
        Files.readAllBytes(fixture.pageset).toVector shouldBe priorpageset
        Files.readAllBytes(fixture.pdf).toVector shouldBe priorpdf
        Files.readAllBytes(fixture.renderermanifest).toVector shouldBe priormanifest
      }
    }

    "reject every independently changed authority before replacing accepted PDF evidence" in {
      Given("isolated accepted copies and one independent Core, Document, Summary, Profile, catalog, or binding mutation per copy")
      val mutations: Vector[(String, Fixture => Unit)] = Vector(
        "core" -> ((fixture: Fixture) => _append(fixture.core, "\n")),
        "document" -> ((fixture: Fixture) => _append(fixture.document, "\n")),
        "summary" -> ((fixture: Fixture) => _append(fixture.summary, "\n")),
        "profile" -> ((fixture: Fixture) => _append(fixture.profile, "\nunknown: rejected\n")),
        "catalog" -> ((fixture: Fixture) => _append(fixture.catalog, "\n")),
        "binding" -> ((fixture: Fixture) => _append(fixture.binding, "\n"))
      )

      When("each changed authority is passed through the existing coordinator")
      val failures = mutations.map { case (name, mutate) => _stale_case("cozy-summary-slide-pdf-stale-" + name)(mutate) }

      Then("each stale or malformed authority fails closed and leaves the accepted PDF and receipt un-replaced")
      failures.foreach { evidence =>
        evidence.failure.getMessage should not be empty
        evidence.pdfbytes shouldBe evidence.priorpdf
        evidence.manifestbytes shouldBe evidence.priormanifest
        evidence.receiptbytes shouldBe evidence.priorreceipt
      }
    }

    "reject every independently missing authority before replacing accepted PDF evidence" in {
      Given("isolated accepted copies with one independently removed Core, Document, Summary, Profile, catalog, or binding per copy")
      val removals: Vector[(String, Fixture => Unit)] = Vector(
        "core" -> ((fixture: Fixture) => _remove(fixture.core)),
        "document" -> ((fixture: Fixture) => _remove(fixture.document)),
        "summary" -> ((fixture: Fixture) => _remove(fixture.summary)),
        "profile" -> ((fixture: Fixture) => _remove(fixture.profile)),
        "catalog" -> ((fixture: Fixture) => _remove(fixture.catalog)),
        "binding" -> ((fixture: Fixture) => _remove(fixture.binding))
      )

      When("each missing authority is passed through the existing coordinator")
      val failures = removals.map { case (name, remove) => _missing_case("cozy-summary-slide-pdf-missing-" + name)(remove) }

      Then("each missing authority fails closed and leaves the accepted PDF and receipt un-replaced")
      failures.foreach { evidence =>
        evidence.failure.getMessage should not be empty
        evidence.pdfbytes shouldBe evidence.priorpdf
        evidence.manifestbytes shouldBe evidence.priormanifest
        evidence.receiptbytes shouldBe evidence.priorreceipt
      }
    }
  }

  private final class Fixture(
    val root: Path,
    val core: Path,
    val document: Path,
    val summary: Path,
    val profile: Path,
    val media: Path,
    val catalog: Path,
    val binding: Path,
    val pageset: Path,
    val pdf: Path,
    val renderermanifest: Path,
    val receipt: Path
  )

  private final case class FailureEvidence(
    failure: RuntimeException,
    pdfbytes: Vector[Byte],
    manifestbytes: Vector[Byte],
    receiptbytes: Vector[Byte],
    priorpdf: Vector[Byte],
    priormanifest: Vector[Byte],
    priorreceipt: Vector[Byte]
  )

  private def _copy_fixture(root: Path): Fixture = {
    val source = _resource("/cozy/document/phase-59/application-modeling/projection.yaml").getParent
    val stream = Files.walk(source)
    try {
      stream.iterator.asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).foreach { path =>
        val destination = root.resolve(source.relativize(path).toString)
        Files.createDirectories(destination.getParent)
        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
      }
    } finally stream.close()
    _write(root.resolve("visual-pages.json"), "prior derived bytes\n")
    new Fixture(
      root,
      root.resolve("content-v2/core.yaml"),
      root.resolve("content-v2/ja/document.yaml"),
      root.resolve("content-v2/ja/summary.yaml"),
      root.resolve("projection.yaml"),
      root.resolve("media.yaml"),
      root.resolve("presentation/catalog.json"),
      root.resolve("presentation/binding.json"),
      root.resolve("visual-pages.json"),
      root.resolve("target/summary-slides.pdf"),
      root.resolve("target/renderer.json"),
      root.resolve("target/cozy-media/manifest.json")
    )
  }

  private def _accept_prebuilt_dependencies(fixture: Fixture): Unit = {
    CozySummarySlidePdf.write(fixture.root, fixture.root.relativize(fixture.profile))
    CozyMedia.build(CozyMedia.CommandConfig(fixture.media, target = Some("article-pdf")))
    CozyMedia.build(CozyMedia.CommandConfig(fixture.media, target = Some("infographic")))
  }

  private def _stale_case(name: String)(mutate: Fixture => Unit): FailureEvidence = {
    var result = Option.empty[FailureEvidence]
    _with_work(name) { root =>
      val fixture = _copy_fixture(root)
      _accept_prebuilt_dependencies(fixture)
      val runner = _renderer(root)
      CozySummarySlidePdf.build(root, root.relativize(fixture.profile), runner)
      val priorpdf = Files.readAllBytes(fixture.pdf).toVector
      val priormanifest = Files.readAllBytes(fixture.renderermanifest).toVector
      val priorreceipt = Files.readAllBytes(fixture.receipt).toVector
      mutate(fixture)
      val failure = intercept[RuntimeException](CozySummarySlidePdf.build(root, root.relativize(fixture.profile), runner))
      result = Some(FailureEvidence(
        failure,
        Files.readAllBytes(fixture.pdf).toVector,
        Files.readAllBytes(fixture.renderermanifest).toVector,
        Files.readAllBytes(fixture.receipt).toVector,
        priorpdf,
        priormanifest,
        priorreceipt
      ))
    }
    result.get
  }

  private def _missing_case(name: String)(remove: Fixture => Unit): FailureEvidence = {
    var result = Option.empty[FailureEvidence]
    _with_work(name) { root =>
      val fixture = _copy_fixture(root)
      _accept_prebuilt_dependencies(fixture)
      val runner = _renderer(root)
      CozySummarySlidePdf.build(root, root.relativize(fixture.profile), runner)
      val priorpdf = Files.readAllBytes(fixture.pdf).toVector
      val priormanifest = Files.readAllBytes(fixture.renderermanifest).toVector
      val priorreceipt = Files.readAllBytes(fixture.receipt).toVector
      remove(fixture)
      val failure = intercept[RuntimeException](CozySummarySlidePdf.build(root, root.relativize(fixture.profile), runner))
      result = Some(FailureEvidence(
        failure,
        Files.readAllBytes(fixture.pdf).toVector,
        Files.readAllBytes(fixture.renderermanifest).toVector,
        Files.readAllBytes(fixture.receipt).toVector,
        priorpdf,
        priormanifest,
        priorreceipt
      ))
    }
    result.get
  }

  private def _renderer(root: Path): CozyMedia.ProcessRunner = {
    var pdfbytes = Option.empty[Array[Byte]]
    new CozyMedia.ProcessRunner {
      def run(command: Vector[String], workingdirectory: Path): Int = {
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
        val currentpdf = pdfbytes.getOrElse {
          val value = _write_pdf(pdf, pages.size)
          pdfbytes = Some(value)
          value
        }
        Files.createDirectories(pdf.getParent)
        Files.write(pdf, currentpdf)
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
  }

  private def _write_pdf(path: Path, pages: Int): Array[Byte] = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val document = new PDDocument()
    try {
      (1 to pages).foreach(_ => document.addPage(new PDPage()))
      document.save(path.toFile)
      Files.readAllBytes(path)
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

  private def _append(path: Path, value: String): Unit =
    Files.write(path, Files.readAllBytes(path) ++ value.getBytes(StandardCharsets.UTF_8))

  private def _remove(path: Path): Unit = {
    Files.delete(path)
    ()
  }

  private def _semantic_sha256(value: String): String = value.stripPrefix("sha256:")
  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString
  private def _resource(value: String): Path = Paths.get(getClass.getResource(value).toURI)
  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _with_work(name: String)(body: Path => Unit): Unit = {
    val target = Files.createDirectories(Paths.get("target").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(target, name + "-")
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
      } finally stream.close()
    }
}
