package cozy.media

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import io.circe.Json
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.JavaConverters._

/*
 * @since   Oct.  6, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaWarehouseSpec extends AnyWordSpec with GivenWhenThen with Matchers {
  "Canonical existing-artifact deployment" should {
    "copy four distinct roles in both locales through the actual dispatcher without invoking a producer" in {
      _with_work { dir =>
        Given("eight declared outputs, a production YouTube record and existing receipt/site metadata")
        val resources = _four_resources(dir, Vector("ja", "en"))
        val descriptor = _descriptor(dir, resources)
        val production = dir.resolve("production/video.json")
        val receipt = dir.resolve("target/cozy-media/manifest.json")
        val registry = dir.resolve("publication/article-media.json")
        _write(production, "{\"watch_url\":\"https://www.youtube.com/watch?v=retained\"}")
        _write(receipt, "existing receipt")
        _write(registry, "existing site registration")
        var calls = Vector.empty[Vector[String]]
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            calls = calls :+ command
            0
          }
        }
        val captured = new ByteArrayOutputStream()

        When("media deploy is dispatched with the explicit warehouse profile")
        val handled = Console.withOut(captured) {
          CozyMediaDispatcher.execute(List("media", "deploy", descriptor.toString, "--profile", "warehouse"), runner)
        }

        Then("all declared bytes land at their role/locale coordinates and originals remain retrievable")
        handled shouldBe true
        for (locale <- Vector("ja", "en"); role <- Vector("article_pdf", "summary_slides_pdf", "video", "infographic")) {
          val basename = _basename(role)
          val destination = dir.resolve(s"warehouse/media/$locale/modeling/example/$basename")
          _read(destination) shouldBe s"$role-$locale bytes"
          _read(dir.resolve(s"outputs/$role-$locale.${_extension(role)}")) shouldBe s"$role-$locale bytes"
        }
        captured.toString("UTF-8") should include ("local placement: complete")
        captured.toString("UTF-8") should include ("Created")
        calls shouldBe Vector.empty
        _read(production) shouldBe "{\"watch_url\":\"https://www.youtube.com/watch?v=retained\"}"
        _read(receipt) shouldBe "existing receipt"
        _read(registry) shouldBe "existing site registration"

        When("an existing non-media route is offered to the same dispatcher")
        val unrelated = CozyMediaDispatcher.execute(List("unrelated", "command"), runner)

        Then("the media dispatcher leaves that route to its owner")
        unrelated shouldBe false
        calls shouldBe Vector.empty
      }
    }

    "deploy only a selected mapped target and ignore unmapped resources" in {
      _with_work { dir =>
        Given("both locales are mapped and an additional unmapped resource has unusable deployment metadata")
        val resources = _four_resources(dir, Vector("ja", "en")) :+
          _set(_set(_resource("unmapped", "article_pdf"), "publications", Json.obj()), "language", Json.fromString("unsupported"))
        val descriptor = _descriptor(dir, resources)

        When("only the Japanese article PDF is selected")
        val result = CozyMediaWarehouse.deploy(CozyMedia.CommandConfig(descriptor, target = Some("article_pdf-ja"), profile = Some("warehouse")))

        Then("only that mapped resource is created")
        result.resources.map(_.resourceId) shouldBe Vector("article_pdf-ja")
        result.resources.map(_.status) shouldBe Vector(CozyMediaWarehouse.Status.Created)
        _read(dir.resolve("warehouse/media/ja/modeling/example/article.pdf")) shouldBe "article_pdf-ja bytes"
        Files.exists(dir.resolve("warehouse/media/en")) shouldBe false
        Files.exists(dir.resolve("warehouse/media/ja/modeling/example/slides.pdf")) shouldBe false

        When("all resources explicitly mapped to the profile are subsequently deployed")
        val all = CozyMediaWarehouse.deploy(_config(descriptor))

        Then("the malformed unmapped resource is ignored while all eight mapped resources are directly placed")
        all.resources.size shouldBe 8
        all.resources.map(_.resourceId) should not contain ("unmapped")
        all.resources.find(_.resourceId == "article_pdf-ja").map(_.status) shouldBe Some(CozyMediaWarehouse.Status.Created)
        all.resources.filterNot(_.resourceId == "article_pdf-ja").map(_.status).distinct shouldBe Vector(CozyMediaWarehouse.Status.Created)
      }
    }

    "reject unknown targets, unmapped targets, unknown profiles, empty selections and site context before placement" in {
      _with_work { dir =>
        Given("an explicit descriptor with one mapped and one unmapped resource")
        _write(dir.resolve("outputs/article.pdf"), "article")
        val mapped = _resource("article", "article_pdf")
        val unmapped = _set(_resource("unmapped", "article_pdf"), "publications", Json.obj())
        val descriptor = _descriptor(dir, Vector(mapped, unmapped))
        val base = CozyMedia.CommandConfig(descriptor, profile = Some("warehouse"))
        val configurations = Vector(
          base.copy(target = Some("unknown")),
          base.copy(target = Some("unmapped")),
          base.copy(profile = Some("unknown")),
          base.copy(siteRoot = Some(dir), siteConfig = Some(dir.resolve("site.conf"))),
          base.copy(siteRoot = Some(dir)),
          base.copy(siteConfig = Some(dir.resolve("site.conf")))
        )

        configurations.foreach { config =>
          When("an unsupported target/profile/site request is deployed")
          val error = intercept[Exception] { CozyMediaWarehouse.deploy(config) }

          Then("an argument fault prevents all warehouse writes")
          error.getMessage should not be empty
          Files.exists(dir.resolve("warehouse/media")) shouldBe false
        }

        Given("the selected profile maps no resource")
        val emptydescriptor = _descriptor(dir, Vector(unmapped))

        When("deployment selects that empty profile")
        val error = intercept[Exception] { CozyMediaWarehouse.deploy(base.copy(descriptorFile = emptydescriptor)) }

        Then("empty selection is a fault rather than an all-success result")
        error.getMessage should include ("No media deploy resources mapped")
        Files.exists(dir.resolve("warehouse/media")) shouldBe false

        When("the actual deploy CLI omits its required profile")
        val missingprofile = intercept[Exception] {
          CozyMediaDispatcher.execute(List("media", "deploy", descriptor.toString), _unused_runner)
        }

        Then("the existing parser's missing-profile wording is preserved")
        missingprofile.getMessage should include ("Missing --profile for media publish")
      }
    }

    "resolve explicit relative roots and injected rootEnv with environment precedence" in {
      _with_work { dir =>
        Given("a relative root and an alternate existing warehouse selected by rootEnv")
        _write(dir.resolve("outputs/article.pdf"), "article")
        val descriptor = _descriptor(dir, Vector(_resource("article", "article_pdf")))
        val alternate = Files.createDirectory(dir.resolve("alternate"))

        When("the explicit relative-root profile is planned")
        val relative = CozyMediaWarehouse.plan(_config(descriptor), Map.empty)

        Then("the destination is resolved against the descriptor parent")
        relative.resources.head.destination shouldBe dir.resolve("warehouse/media/ja/modeling/example/article.pdf")

        Given("the same profile declares both root and rootEnv")
        val envdescriptor = _descriptor(dir, Vector(_resource("article", "article_pdf")), profile = Json.obj(
          "root" -> Json.fromString("warehouse"), "rootEnv" -> Json.fromString("TEST_MEDIA_ROOT")
        ))

        When("the injected environment supplies the selected warehouse")
        val result = CozyMediaWarehouse.deploy(_config(envdescriptor), Map("TEST_MEDIA_ROOT" -> alternate.toString))

        Then("the environment root wins over the descriptor root")
        result.resources.head.destination shouldBe alternate.resolve("media/ja/modeling/example/article.pdf")
        result.resources.head.status shouldBe CozyMediaWarehouse.Status.Created
        Files.exists(dir.resolve("warehouse/media")) shouldBe false

        Given("a rootEnv value relative to the current working directory, following existing profile semantics")
        val relativeenv = Paths.get("").toAbsolutePath.normalize().relativize(alternate).toString

        When("that relative environment value is injected into planning")
        val envplan = CozyMediaWarehouse.plan(_config(envdescriptor), Map("TEST_MEDIA_ROOT" -> relativeenv))

        Then("rootEnv is made absolute using the working directory rather than the descriptor parent")
        envplan.resources.head.destination shouldBe alternate.resolve("media/ja/modeling/example/article.pdf")

        Vector(Map.empty[String, String], Map("TEST_MEDIA_ROOT" -> ""), Map("TEST_MEDIA_ROOT" -> "  ")).foreach { environment =>
          When("rootEnv is absent or blank")
          val error = intercept[Exception] { CozyMediaWarehouse.deploy(_config(envdescriptor), environment) }

          Then("deployment faults without falling back to root")
          error.getMessage should include ("TEST_MEDIA_ROOT")
          Files.exists(dir.resolve("warehouse/media")) shouldBe false
        }
      }
    }

    "require explicit schema, unique IDs, article identity and an existing descriptor-owned root" in {
      _with_work { dir =>
        Given("invalid descriptor contracts and a readable existing output")
        _write(dir.resolve("outputs/article.pdf"), "article")
        val resource = _resource("article", "article_pdf")
        val variants = Vector(
          _document(Vector(resource), schema = "cozy.media.v2"),
          _document(Vector(resource, resource)),
          _document(Vector(resource), identity = None),
          _document(Vector(resource), profile = Json.obj()),
          _document(Vector(resource), profile = Json.obj("root" -> Json.fromString("absent-root"))),
          _document(Vector(resource), profile = Json.obj("root" -> Json.fromString("outputs/article.pdf"))),
          _set(_document(Vector(resource)), "profiles", Json.obj())
        )
        _write(dir.resolve("conf/cozy/config.yaml"), "publication:\n  profiles:\n    warehouse:\n      root: warehouse\n")

        variants.foreach { document =>
          val descriptor = _write(dir.resolve("media.json"), document.spaces2)
          When("the invalid explicit descriptor is deployed")
          val error = intercept[Exception] { CozyMediaWarehouse.deploy(_config(descriptor)) }

          Then("descriptor admission fails without using a project profile or creating directories")
          error.getMessage should include ("Media")
          Files.exists(dir.resolve("warehouse/media")) shouldBe false
          Files.exists(dir.resolve("absent-root")) shouldBe false
        }
      }
    }

    "keep missing outputs unavailable without falling back and place independent prebuilt resources" in {
      _with_work { dir =>
        Given("a missing output with an existing manuscript and a separate existing prebuilt slide PDF")
        val missing = _set(_resource("article", "article_pdf"), "source", Json.fromString("inputs/manuscript.pdf"))
        _write(dir.resolve("inputs/manuscript.pdf"), "must not substitute")
        val present = _without(_resource("slides", "summary_slides_pdf"), "output")
        _write(dir.resolve("inputs/slides.pdf"), "prebuilt slides")
        val absent = _without(_resource("video", "video", "en"), "output")
        val descriptor = _descriptor(dir, Vector(missing, present, absent))

        When("the mixed availability set is deployed")
        val result = CozyMediaWarehouse.deploy(_config(descriptor))

        Then("only the independent slides are created and the result explicitly reports partial placement")
        result.resources.map(item => item.resourceId -> item.status).toMap shouldBe Map(
          "article" -> CozyMediaWarehouse.Status.Unavailable,
          "slides" -> CozyMediaWarehouse.Status.Created,
          "video" -> CozyMediaWarehouse.Status.Unavailable
        )
        _read(dir.resolve("warehouse/media/ja/modeling/example/slides.pdf")) shouldBe "prebuilt slides"
        Files.exists(dir.resolve("warehouse/media/ja/modeling/example/article.pdf")) shouldBe false
        Files.exists(dir.resolve("warehouse/media/en")) shouldBe false
        _read(dir.resolve("inputs/manuscript.pdf")) shouldBe "must not substitute"
        CozyMediaWarehouse.render(result) should include ("local placement: partial")
        CozyMediaWarehouse.render(result) should include ("Unavailable")
        CozyMediaWarehouse.render(result) should include ("remote publication not performed")
      }
    }

    "resolve dry-run paths and availability without creating a destination parent" in {
      _with_work { dir =>
        Given("one available declared output and one absent output")
        _write(dir.resolve("outputs/article.pdf"), "article")
        val descriptor = _descriptor(dir, Vector(_resource("article", "article_pdf"), _resource("slides", "summary_slides_pdf")))

        When("deployment runs with dry-run")
        val result = CozyMediaWarehouse.deploy(_config(descriptor).copy(dryRun = true))

        Then("available and absent sources have actual paths with Planned and Unavailable statuses and no writes")
        result.resources.map(_.status) shouldBe Vector(CozyMediaWarehouse.Status.Planned, CozyMediaWarehouse.Status.Unavailable)
        result.resources.head.source shouldBe dir.resolve("outputs/article.pdf")
        result.resources.head.destination shouldBe dir.resolve("warehouse/media/ja/modeling/example/article.pdf")
        result.resources.head.publicPath shouldBe "/media/ja/modeling/example/article.pdf"
        CozyMediaWarehouse.render(result) should include ("dry-run: partial")
        Files.exists(dir.resolve("warehouse/media")) shouldBe false
      }
    }

    "preserve each supported infographic format rather than substituting the illustrative SVG" in {
      _with_work { dir =>
        Vector("svg" -> "image/svg+xml", "png" -> "image/png", "jpg" -> "image/jpeg", "jpeg" -> "image/jpeg", "webp" -> "image/webp").foreach { case (extension, mime) =>
          Given(s"an explicitly declared $extension infographic with matching MIME")
          val resource = _resource("image", "infographic", extension = extension, mimetype = Some(mime))
          _write(dir.resolve(s"outputs/image.$extension"), s"$extension bytes")
          val descriptor = _descriptor(dir, Vector(resource))

          When("the existing image is deployed")
          val result = CozyMediaWarehouse.deploy(_config(descriptor))

          Then("its bytes and actual extension are preserved at the declared public path")
          result.resources.head.publicPath shouldBe s"/media/ja/modeling/example/infographic.$extension"
          _read(result.resources.head.destination) shouldBe s"$extension bytes"
          _read(result.resources.head.source) shouldBe s"$extension bytes"
        }
      }
    }

    "reject malformed identity, locale, kind, format, coordinate and PDF-role conflation before writes" in {
      _with_work { dir =>
        Given("a valid article resource followed by a malformed resource or identity")
        _write(dir.resolve("outputs/article.pdf"), "article")
        _write(dir.resolve("outputs/slides.pdf"), "slides")
        val article = _resource("article", "article_pdf")
        val slides = _resource("slides", "summary_slides_pdf")
        val wrongmedia = _set(slides.hcursor.downField("articleMedia").focus.get, "mediaType", Json.fromString("image/png"))
        val wrongpublic = _set(slides.hcursor.downField("articleMedia").focus.get, "publicPath", Json.fromString("/media/ja/modeling/example/article.pdf"))
        val malformed = Vector(
          _set(slides, "language", Json.fromString("fr")),
          _without(slides, "language"),
          _set(slides, "kind", Json.fromString("video")),
          _set(slides, "output", Json.fromString("outputs/slides.png")),
          _set(slides, "articleMedia", wrongmedia),
          _set(slides, "articleMedia", wrongpublic),
          _set(slides, "output", Json.fromString(dir.resolve("outputs/slides.pdf").toString)),
          _set(_without(slides, "output"), "build", Json.fromString("article-pdf")),
          _without(_without(slides, "output"), "source"),
          _resource("badimage", "infographic", extension = "gif", mimetype = Some("image/gif")),
          _resource("badmime", "infographic", extension = "png", mimetype = Some("image/jpeg")),
          _set(_resource("badvideo", "video"), "output", Json.fromString("outputs/movie.webm"))
        ) ++ Vector(
          "/media/ja/modeling/example/slides.pdf",
          "media/ja/modeling/../example/slides.pdf",
          "media\\ja\\modeling\\example\\slides.pdf",
          "media/ja/modeling/example/slides.pdf?download=1",
          "media/ja/modeling/example/slides.pdf#page=1",
          "media/en/modeling/example/slides.pdf",
          "media/ja/modeling/example/article.pdf"
        ).map(value => _set(slides, "publications", Json.obj("warehouse" -> Json.fromString(value))))

        malformed.foreach { resource =>
          val descriptor = _descriptor(dir, Vector(article, resource))
          When("the malformed selected resource is deployed with a valid independent resource")
          val error = intercept[Exception] { CozyMediaWarehouse.deploy(_config(descriptor)) }

          Then("the whole preflight faults before copying the valid article")
          error.getMessage should include ("Media")
          Files.exists(dir.resolve("warehouse/media")) shouldBe false
          _read(dir.resolve("outputs/article.pdf")) shouldBe "article"
        }

        Vector("modeling", "/modeling/example", "modeling/../example", "modeling/..", "./example", "modeling/example/", "modeling/a?b", "modeling/a%20b").foreach { identity =>
          val descriptor = _descriptor(dir, Vector(article), identity = Some(identity))
          When("a malformed article identity is planned")
          val error = intercept[Exception] { CozyMediaWarehouse.deploy(_config(descriptor)) }

          Then("identity rejection creates no media directory")
          error.getMessage should include ("articleIdentity")
          Files.exists(dir.resolve("warehouse/media")) shouldBe false
        }
      }
    }

    "reject duplicate destinations before any copy" in {
      _with_work { dir =>
        Given("two distinct resource IDs mapped to the same article PDF coordinate")
        _write(dir.resolve("outputs/first.pdf"), "first")
        _write(dir.resolve("outputs/second.pdf"), "second")
        val descriptor = _descriptor(dir, Vector(_resource("first", "article_pdf"), _resource("second", "article_pdf")))

        When("both resources are deployed")
        val error = intercept[Exception] { CozyMediaWarehouse.deploy(_config(descriptor)) }

        Then("duplicate destination admission fails without creating the parent")
        error.getMessage should include ("duplicate selected destinations")
        Files.exists(dir.resolve("warehouse/media")) shouldBe false
      }
    }

    "admit relative prebuilt/copy sources outside the descriptor parent without admitting symlinks" in {
      _with_work { dir =>
        Given("an existing sibling artifact explicitly selected from a nested descriptor")
        _write(dir.resolve("existing/article.pdf"), "retained sibling")
        val nested = Files.createDirectory(dir.resolve("descriptor"))
        val resource = _set(_without(_resource("article", "article_pdf"), "output"), "source", Json.fromString("../existing/article.pdf"))
        val descriptor = _descriptor(nested, Vector(resource))

        When("the explicitly declared relative prebuilt source is deployed")
        val result = CozyMediaWarehouse.deploy(_config(descriptor))

        Then("the outside-parent original is retained and copied to the configured warehouse")
        result.resources.head.source shouldBe dir.resolve("existing/article.pdf")
        _read(result.resources.head.destination) shouldBe "retained sibling"
        _read(dir.resolve("existing/article.pdf")) shouldBe "retained sibling"

        Given("an independent copy source with no output")
        val copy = _set(resource, "build", Json.fromString("copy"))
        val copydescriptor = _descriptor(nested, Vector(copy))

        When("that copy resource places its selected source at the existing destination")
        val copied = CozyMediaWarehouse.deploy(_config(copydescriptor))

        Then("the copy build form is admitted and directly places the source")
        copied.resources.head.status shouldBe CozyMediaWarehouse.Status.Created
        copied.resources.head.source shouldBe dir.resolve("existing/article.pdf")
        _read(copied.resources.head.destination) shouldBe "retained sibling"
      }
    }

    "reject source, root and destination symlinks and nonregular paths before writes" in {
      _with_work { dir =>
        Given("direct existing artifacts and symlink aliases within this isolated test root")
        _write(dir.resolve("outputs/article.pdf"), "article")
        val warehouse = Files.createDirectory(dir.resolve("warehouse"))
        Files.createSymbolicLink(dir.resolve("root-alias"), warehouse)
        Files.createSymbolicLink(dir.resolve("output-alias"), dir.resolve("outputs"))
        Files.createSymbolicLink(dir.resolve("leaf.pdf"), dir.resolve("outputs/article.pdf"))
        Files.createDirectory(dir.resolve("directory.pdf"))
        _write(dir.resolve("parent-file"), "not a directory")
        val article = _resource("article", "article_pdf")
        val variants = Vector(
          _document(Vector(article), profile = Json.obj("root" -> Json.fromString("root-alias"))),
          _document(Vector(_set(article, "output", Json.fromString("output-alias/article.pdf")))),
          _document(Vector(_set(article, "output", Json.fromString("leaf.pdf")))),
          _document(Vector(_set(article, "output", Json.fromString("directory.pdf")))),
          _document(Vector(_set(article, "output", Json.fromString("parent-file/article.pdf")))),
          _document(Vector(_set(article, "output", Json.fromString("output-alias/../outputs/article.pdf"))))
        )

        variants.foreach { document =>
          val descriptor = _write(dir.resolve("media.json"), document.spaces2)
          When("a source/root alias or nonregular path participates in deployment")
          val error = intercept[Exception] { CozyMediaWarehouse.deploy(_config(descriptor)) }

          Then("direct-path admission rejects it before warehouse writes")
          error.getMessage should include ("Media")
          Files.exists(warehouse.resolve("media")) shouldBe false
        }

        Given("the canonical media ancestor is a symlink to a retained directory")
        val retained = Files.createDirectory(dir.resolve("retained"))
        Files.createSymbolicLink(warehouse.resolve("media"), retained)
        val descriptor = _write(dir.resolve("media.json"), _document(Vector(article)).spaces2)

        When("the declared destination traverses that symlink")
        val ancestorerror = intercept[Exception] { CozyMediaWarehouse.deploy(_config(descriptor)) }

        Then("the outside destination remains absent")
        ancestorerror.getMessage should include ("symlink")
        Files.exists(retained.resolve("ja")) shouldBe false

        Given("a direct media parent with a symlink at the final destination")
        Files.delete(warehouse.resolve("media"))
        val destination = warehouse.resolve("media/ja/modeling/example/article.pdf")
        Files.createDirectories(destination.getParent)
        Files.createSymbolicLink(destination, dir.resolve("outputs/article.pdf"))

        When("the destination leaf is preflighted")
        val leaferror = intercept[Exception] { CozyMediaWarehouse.deploy(_config(descriptor)) }

        Then("the source and symlink are retained without replacement")
        leaferror.getMessage should include ("symlink")
        Files.isSymbolicLink(destination) shouldBe true
        _read(dir.resolve("outputs/article.pdf")) shouldBe "article"

        Given("the final destination is a directory rather than an artifact")
        Files.delete(destination)
        Files.createDirectory(destination)

        When("the directory destination is preflighted")
        val directoryerror = intercept[Exception] { CozyMediaWarehouse.deploy(_config(descriptor)) }

        Then("the directory is retained and reported as a fault rather than a missing artifact")
        directoryerror.getMessage should include ("direct regular readable file")
        Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS) shouldBe true
      }
    }

    "carry generated unreserved category/article segments in the full path without a reverse lookup" in {
      _with_work { dir =>
        Given("a generated pair of valid URI-unreserved category/article segments and one existing PDF")
        _write(dir.resolve("outputs/article.pdf"), "article")
        val segment = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
        val property = Prop.forAll(segment, segment) { (category, article) =>
          Given("a generated explicit identity and matching declared PDF coordinate")
          val identity = s"$category/$article"
          val coordinate = s"media/ja/$identity/article.pdf"
          val resource = _set(_set(_resource("article", "article_pdf"), "publications", Json.obj("warehouse" -> Json.fromString(coordinate))),
            "articleMedia", Json.obj("role" -> Json.fromString("article_pdf"), "mediaType" -> Json.fromString("application/pdf"), "publicPath" -> Json.fromString("/" + coordinate)))
          val descriptor = _descriptor(dir, Vector(resource), identity = Some(identity))

          When("the generated explicit coordinates are planned without mutation")
          val plan = CozyMediaWarehouse.plan(_config(descriptor))

          Then("the full public/local paths retain the exact identity and locale with a simple basename")
          plan.resources.head.articleIdentity shouldBe identity
          plan.resources.head.publicPath shouldBe "/" + coordinate
          plan.resources.head.destination shouldBe dir.resolve("warehouse").resolve(coordinate)
          Files.exists(dir.resolve("warehouse/media")) shouldBe false
          true
        }

        When("the generated coordinate specification is evaluated")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

        Then("all generated valid identities satisfy the path contract")
        result.passed shouldBe true
      }
    }
  }

  private def _config(descriptor: Path): CozyMedia.CommandConfig = CozyMedia.CommandConfig(descriptor, profile = Some("warehouse"))

  private def _four_resources(dir: Path, locales: Vector[String]): Vector[Json] = {
    locales.flatMap { locale =>
      Vector("article_pdf", "summary_slides_pdf", "video", "infographic").map { role =>
        val id = s"$role-$locale"
        _write(dir.resolve(s"outputs/$id.${_extension(role)}"), s"$role-$locale bytes")
        _resource(id, role, locale)
      }
    }
  }

  private def _resource(id: String, role: String, locale: String = "ja", extension: String = "", mimetype: Option[String] = None): Json = {
    val ext = if (extension.isEmpty) _extension(role) else extension
    val basename = if (role == "infographic") s"infographic.${ext.toLowerCase(java.util.Locale.ROOT)}" else _basename(role)
    val coordinate = s"media/$locale/modeling/example/$basename"
    val media = if (role == "video") {
      Json.obj("role" -> Json.fromString(role), "production" -> Json.fromString("production/video.json"))
    } else {
      Json.obj(
        "role" -> Json.fromString(role),
        "publicPath" -> Json.fromString("/" + coordinate),
        "mediaType" -> Json.fromString(mimetype.getOrElse(if (role == "infographic") "image/svg+xml" else "application/pdf"))
      )
    }
    Json.obj(
      "id" -> Json.fromString(id), "kind" -> Json.fromString(if (role == "video") "video" else if (role == "infographic") "infographic" else "document"),
      "language" -> Json.fromString(locale), "build" -> Json.fromString("prebuilt"),
      "source" -> Json.fromString(s"inputs/$id.$ext"), "output" -> Json.fromString(s"outputs/$id.$ext"),
      "publications" -> Json.obj("warehouse" -> Json.fromString(coordinate)), "articleMedia" -> media
    )
  }

  private def _extension(role: String): String = role match {
    case "video" => "mp4"
    case "infographic" => "svg"
    case _ => "pdf"
  }

  private def _basename(role: String): String = role match {
    case "article_pdf" => "article.pdf"
    case "summary_slides_pdf" => "slides.pdf"
    case "video" => "video.mp4"
    case _ => "infographic.svg"
  }

  private def _document(resources: Vector[Json], identity: Option[String] = Some("modeling/example"), schema: String = "cozy.media.v1", profile: Json = Json.obj("root" -> Json.fromString("warehouse"))): Json = {
    val document = Json.obj(
      "schema" -> Json.fromString(schema),
      "knowledge" -> Json.obj("id" -> Json.fromString("unrelated-knowledge-id"), "source" -> Json.fromString("missing-manuscript.dox")),
      "resources" -> Json.fromValues(resources), "profiles" -> Json.obj("warehouse" -> profile)
    )
    identity.map { value =>
      _set(document, "articleMedia", Json.obj("articleIdentity" -> Json.fromString(value), "publicationProfile" -> Json.fromString("warehouse")))
    }.getOrElse(document)
  }

  private def _descriptor(dir: Path, resources: Vector[Json], identity: Option[String] = Some("modeling/example"), profile: Json = Json.obj("root" -> Json.fromString("warehouse"))): Path = {
    Files.createDirectories(dir.resolve("warehouse"))
    _write(dir.resolve("media.json"), _document(resources, identity = identity, profile = profile).spaces2)
  }

  private def _set(document: Json, name: String, value: Json): Json = document.mapObject(_.add(name, value))
  private def _without(document: Json, name: String): Json = document.mapObject(_.remove(name))

  private def _write(path: Path, content: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
  }

  private def _read(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private val _unused_runner: CozyMedia.ProcessRunner = new CozyMedia.ProcessRunner {
    def run(command: Vector[String], workingdirectory: Path): Int = fail("Deployment must not invoke a process runner")
  }

  private def _with_work(body: Path => Unit): Unit = {
    val parent = Paths.get("target/cozy-media-warehouse-spec").toAbsolutePath.normalize()
    Files.createDirectories(parent)
    val dir = Files.createTempDirectory(parent, "deployment-")
    try {
      body(dir)
    } finally {
      val paths = Files.walk(dir)
      try {
        paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path))
      } finally {
        paths.close()
      }
    }
  }
}
