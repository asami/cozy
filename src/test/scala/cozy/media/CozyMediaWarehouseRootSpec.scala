package cozy.media

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import cozy.publication.{CozyArticleMediaRegistry, CozyArticleMediaSiteCommand, CozyArticleMediaWarehouseSiteBinding}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

/*
 * @since   Oct.  6, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private object WarehouseRootFixture {
  final case class Data(root: Path, descriptor: Path, warehouse: Path, fallback: Path, registry: Path)
}

final class CozyMediaWarehouseRootSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _identity = "modeling/example"
  private val _roles = Vector("infographic", "article_pdf", "summary_slides_pdf", "video")
  private val _locales = Vector("ja", "en")
  private val _environment = "COZY_ROOT_SPEC_DELIBERATELY_ABSENT"
  private val _unused_runner = new CozyMedia.ProcessRunner {
    def run(command: Vector[String], workingdirectory: Path): Int =
      throw new IllegalStateException("Root selection must not invoke a producer or network process")
  }

  "Explicit canonical warehouse root selection" should {
    "place existing declared media" which {
      "place all four roles in both locales through native main with an explicit root" in {
        Given("eight existing declared outputs, an unavailable rootEnv and retained producer, production and registry data")
        _with_fixture { fixture =>
          val before = _retained(fixture)
          val args = _deploy_args(fixture)

          When("native main places the complete batch twice at the explicit alternate root")
          val created = _main(args)
          val repeated = _main(args)
          val observed = CozyMediaWarehouse.planAtRoot(_command(fixture), fixture.warehouse, Map.empty)

          Then("both placements report eight Created at exact JA/EN coordinates and all original business data remains")
          created.split("\n").count(_.contains(": Created ")) shouldBe 8
          repeated.split("\n").count(_.contains(": Created ")) shouldBe 8
          observed.resources.size shouldBe 8
          observed.resources.foreach { item =>
            item.destination shouldBe _destination(fixture, item.locale, item.role)
            item.publicPath shouldBe _public_path(item.locale, item.role)
            _read(item.destination) shouldBe _read(item.source)
          }
          _retained(fixture) shouldBe before
          Files.exists(fixture.fallback.resolve("media")) shouldBe false
          created should include("remote publication not performed")
          for (locale <- _locales)
            (_json(fixture.root.resolve(s"production/$locale.json")) \ "youtube" \ "videoUrl").as[String] shouldBe _watch_url(locale)
          Files.exists(fixture.root.resolve("target/cozy-media")) shouldBe false
        }
      }

      "accept both root option forms, explicit precedence and exact target/dry-run" in {
        Given("profile and environment fallback roots differ from a direct explicit root containing a space")
        _with_fixture { fixture =>
          val separated = _deploy_args(fixture).drop(2)
          val equals = List(fixture.descriptor.toString, "--profile=warehouse", s"--warehouse-root=${fixture.warehouse}")
          val environment = Map(_environment -> fixture.fallback.toString)

          When("both option forms and injected empty/conflicting environments plan the same root, and dispatcher dry-runs one target")
          val left = CozyMediaWarehouse.DeployConfig.create(separated)
          val right = CozyMediaWarehouse.DeployConfig.create(equals)
          val emptyplan = CozyMediaWarehouse.planAtRoot(left.command, left.warehouseRoot.get, Map.empty)
          val conflictingplan = CozyMediaWarehouse.planAtRoot(right.command, right.warehouseRoot.get, environment)
          val report = _dispatch(_deploy_args(fixture) ++ List("--target", "article_pdf-en", "--dry-run"))

          Then("the exact explicit root wins and only the selected target is Planned without writes")
          left.warehouseRoot shouldBe Some(fixture.warehouse)
          right.warehouseRoot shouldBe left.warehouseRoot
          emptyplan shouldBe conflictingplan
          report should include("article_pdf-en: Planned")
          report.split("\n").count(_.contains(": Planned ")) shouldBe 1
          Files.exists(fixture.warehouse.resolve("media")) shouldBe false
          Files.exists(fixture.fallback.resolve("media")) shouldBe false

          Given("generated exact target IDs from the eight declared resources")
          val property = Prop.forAll(Gen.oneOf(_locales), Gen.oneOf(_roles)) { (locale, role) =>
            When("the generated target is planned at the same explicit root")
            val plan = CozyMediaWarehouse.planAtRoot(_command(fixture).copy(target = Some(s"$role-$locale")), fixture.warehouse, Map.empty)
            Then("only that declared role and locale is selected")
            plan.resources.map(_.resourceId) shouldBe Vector(s"$role-$locale")
            plan.resources.head.destination shouldBe _destination(fixture, locale, role)
            true
          }
          When("the generated exact-selection specification is evaluated")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)
          Then("all generated selections retain their exact coordinate")
          result.passed shouldBe true
        }
      }

      "preserve ordinary profile root and rootEnv behavior when no override is supplied" in {
        Given("a legacy descriptor-relative root and a second profile with rootEnv precedence")
        _with_fixture { fixture =>
          _profile(fixture, Json.obj("root" -> "fallback"))
          val before = _read(fixture.descriptor)

          When("the retained APIs and no-root command config use the ordinary relative profile")
          val config = CozyMediaWarehouse.DeployConfig.create(List(fixture.descriptor.toString, "--profile", "warehouse", "--dry-run"))
          val plan = CozyMediaWarehouse.plan(config.command, Map.empty)
          val result = CozyMediaWarehouse.deployCommand(config)

          Then("the old relative-root destination and default absent override remain")
          config.warehouseRoot shouldBe None
          plan.resources.head.destination.startsWith(fixture.fallback) shouldBe true
          result.resources.map(_.status).distinct shouldBe Vector(CozyMediaWarehouse.Status.Planned)
          _read(fixture.descriptor) shouldBe before
          Files.exists(fixture.fallback.resolve("media")) shouldBe false

          Given("the same descriptor declares both root and rootEnv")
          _profile(fixture, Json.obj("root" -> "fallback", "rootEnv" -> _environment))
          val relativeenv = Paths.get("").toAbsolutePath.normalize().relativize(fixture.warehouse).toString
          When("retained plan/deploy APIs receive an environment root, including a working-directory-relative value")
          val envplan = CozyMediaWarehouse.plan(_command(fixture), Map(_environment -> relativeenv))
          val deployed = CozyMediaWarehouse.deploy(_command(fixture).copy(target = Some("article_pdf-ja")), Map(_environment -> fixture.warehouse.toString))
          Then("rootEnv retains its existing precedence and relative environment semantics")
          envplan.resources.head.destination.startsWith(fixture.warehouse) shouldBe true
          deployed.resources.head.status shouldBe CozyMediaWarehouse.Status.Created
          Files.exists(fixture.fallback.resolve("media")) shouldBe false
          Vector(Map.empty[String, String], Map(_environment -> ""), Map(_environment -> "  ")).foreach { environment =>
            When("legacy rootEnv is absent or blank")
            val error = intercept[Exception] { CozyMediaWarehouse.plan(_command(fixture), environment) }
            Then("the existing rootEnv fault is retained without a hidden fallback")
            error.getMessage should include(_environment)
          }
        }
      }

      "reject malformed root requests and unsupported command routes before writes" in {
        Given("a valid descriptor/root and malformed, repeated, null or unsupported input requests")
        _with_fixture { fixture =>
          val base = List(fixture.descriptor.toString, "--profile", "warehouse")
          val requests = Vector(
            null, base :+ null, base :+ "--warehouse-root", base ++ List("--warehouse-root", "--dry-run"),
            base ++ List("--warehouse-root", ""), base :+ "--warehouse-root= ", base :+ "--warehouse-root=relative",
            base :+ s"--warehouse-root= ${fixture.warehouse}", base :+ s"--warehouse-root=${fixture.warehouse}/.",
            base :+ s"--warehouse-root=${fixture.warehouse}/../explicit warehouse",
            base ++ List("--warehouse-root", fixture.warehouse.toString, s"--warehouse-root=${fixture.warehouse}"),
            base :+ "--unknown-option", List(fixture.descriptor.toString, "--warehouse-root", fixture.warehouse.toString))
          requests.foreach { args =>
            When("the deploy-specific parser interprets a malformed request")
            val error = intercept[Exception] { CozyMediaWarehouse.DeployConfig.create(args) }
            Then("an actionable argument fault occurs before any placement")
            error.getMessage should not be empty
          }
          Vector("inspect", "plan", "build", "verify", "publish", "register-site-wip").foreach { route =>
            When("an ordinary native media route receives the new root option")
            val error = intercept[Exception] {
              CozyMediaDispatcher.execute(List("media", route) ++ base ++ List("--warehouse-root", fixture.warehouse.toString), _unused_runner)
            }
            Then("that route rejects the option without calling a producer")
            error.getMessage should not be empty
          }
          When("the generic parser, slide route, unknown profile/target and null direct configurations are offered")
          val errors = Vector(
            intercept[Exception] { CozyMedia.CommandConfig.create(base ++ List("--warehouse-root", fixture.warehouse.toString)) },
            intercept[Exception] { _dispatch(List("media", "slide", "plan") ++ base ++ List("--warehouse-root", fixture.warehouse.toString)) },
            intercept[Exception] { CozyMediaWarehouse.deployAtRoot(_command(fixture).copy(profile = Some("unknown")), fixture.warehouse) },
            intercept[Exception] { CozyMediaWarehouse.deployAtRoot(_command(fixture).copy(target = Some("unknown")), fixture.warehouse) },
            intercept[Exception] { CozyMediaWarehouse.deployCommand(CozyMediaWarehouse.DeployConfig(_command(fixture), null)) },
            intercept[Exception] { CozyMediaWarehouse.deployCommand(CozyMediaWarehouse.DeployConfig(_command(fixture), Some(null))) },
            intercept[Exception] { CozyMediaWarehouse.deployCommand(null) })
          Then("all fail with readable faults and leave root and retained metadata untouched")
          errors.foreach(error => error.getMessage should not be empty)
          Files.exists(fixture.warehouse.resolve("media")) shouldBe false
          Files.exists(fixture.fallback.resolve("media")) shouldBe false

          Given("a declared resource whose requested profile has no mapping")
          val resources = (_json(fixture.descriptor) \ "resources").as[JsArray].value.map { value =>
            val resource = value.as[JsObject]
            if ((resource \ "id").as[String] == "article_pdf-ja") resource ++ Json.obj("publications" -> Json.obj()) else resource
          }
          _write_json(fixture.descriptor, _json(fixture.descriptor) ++ Json.obj("resources" -> JsArray(resources)))
          When("native dispatch selects that unmapped target at the explicit root")
          val unmapped = intercept[Exception] { _dispatch(_deploy_args(fixture) ++ List("--target", "article_pdf-ja")) }
          Then("profile mapping remains mandatory before placement")
          unmapped.getMessage should include("has no publication")
          Files.exists(fixture.warehouse.resolve("media")) shouldBe false
        }
      }

      "reject non-direct or unavailable explicit roots before placement" in {
        Given("missing and file roots, symlink leaves/ancestors and unnormalized alias paths")
        _with_fixture { fixture =>
          val file = _write(fixture.root.resolve("file-root"), "retained file")
          val leaf = Files.createSymbolicLink(fixture.root.resolve("root-alias"), fixture.warehouse)
          val ancestor = Files.createSymbolicLink(fixture.root.resolve("ancestor-alias"), fixture.root)
          val roots = Vector(fixture.root.resolve("missing"), file, leaf, ancestor.resolve("explicit warehouse"),
            fixture.warehouse.resolve("."), leaf.resolve("..").resolve("explicit warehouse"), null)
          roots.foreach { root =>
            When("native parsing and the direct Path API receive a non-direct explicit root")
            val parseerror = intercept[Exception] { CozyMediaWarehouse.requireWarehouseRoot(if (root == null) null else root.toString) }
            val apierror = intercept[Exception] { CozyMediaWarehouse.deployAtRoot(_command(fixture), root, Map(_environment -> fixture.fallback.toString)) }
            Then("both reject before any destination or fallback directory is created")
            parseerror.getMessage should not be empty
            apierror.getMessage should not be empty
            Files.exists(fixture.warehouse.resolve("media")) shouldBe false
            Files.exists(fixture.fallback.resolve("media")) shouldBe false
          }
          _read(file) shouldBe "retained file"
        }
      }

      "keep partial availability, direct placement and dry-run under the explicit root" in {
        Given("one missing source, an existing selected destination and other available absent destinations")
        _with_fixture { fixture =>
          Files.delete(_source(fixture, "en", "video"))
          val destination = _destination(fixture, "ja", "article_pdf")
          _write(destination, _read(_source(fixture, "ja", "article_pdf")))
          When("dry-run and real explicit-root deployment evaluate the partial batch")
          val dry = CozyMediaWarehouse.deployAtRoot(_command(fixture).copy(dryRun = true), fixture.warehouse, Map.empty)
          val dryabsent = Files.notExists(_destination(fixture, "ja", "infographic"))
          val real = CozyMediaWarehouse.deployAtRoot(_command(fixture), fixture.warehouse, Map.empty)
          Then("missing remains Unavailable, dry-run is Planned and real placement creates all seven available resources")
          dry.resources.count(_.status == CozyMediaWarehouse.Status.Unavailable) shouldBe 1
          dry.resources.count(_.status == CozyMediaWarehouse.Status.Planned) shouldBe 7
          dryabsent shouldBe true
          real.resources.count(_.status == CozyMediaWarehouse.Status.Created) shouldBe 7
          real.resources.count(_.status == CozyMediaWarehouse.Status.Unavailable) shouldBe 1
          _read(destination) shouldBe "article_pdf-ja original"
        }
        Given("a selected canonical destination contains an earlier edition and all sources are available")
        _with_fixture { fixture =>
          val destination = _destination(fixture, "en", "video")
          _write(destination, "earlier video edition")

          When("the complete batch is selected for dry-run placement")
          val dry = CozyMediaWarehouse.deployAtRoot(_command(fixture).copy(dryRun = true), fixture.warehouse)

          Then("all eight resources are Planned at their canonical paths without writes")
          dry.resources.map(_.status).distinct shouldBe Vector(CozyMediaWarehouse.Status.Planned)
          Files.exists(_destination(fixture, "ja", "infographic")) shouldBe false
          _read(destination) shouldBe "earlier video edition"
          dry.resources.foreach { item =>
            item.source shouldBe _source(fixture, item.locale, item.role)
            item.destination shouldBe _destination(fixture, item.locale, item.role)
            item.publicPath shouldBe _public_path(item.locale, item.role)
          }

          When("the complete selected batch is directly placed")
          val real = CozyMediaWarehouse.deployAtRoot(_command(fixture), fixture.warehouse)

          Then("all eight resources are Created and the current source replaces the selected earlier edition")
          real.resources.map(_.resourceId) shouldBe dry.resources.map(_.resourceId)
          real.resources.count(_.status == CozyMediaWarehouse.Status.Created) shouldBe 8
          _read(destination) shouldBe "video-en original"
          _read(_source(fixture, "en", "video")) shouldBe "video-en original"
          real.resources.foreach { item =>
            item.source shouldBe _source(fixture, item.locale, item.role)
            item.destination shouldBe _destination(fixture, item.locale, item.role)
            _read(item.destination) shouldBe _read(item.source)
          }
        }
      }
    }

    "register the same explicit canonical coordinates" which {
      "register canonical JA/EN roles through native main at the same explicit root" in {
        Given("canonical non-video files, unavailable rootEnv, exact QA-passed YouTube records and an existing article owner")
        _with_fixture { fixture =>
          _place_nonvideo(fixture)
          val production = _locales.map(locale => _read(fixture.root.resolve(s"production/$locale.json")))
          val owner = _json(fixture.registry.resolve("owner.json"))
          When("native main registers separated then equals root/profile forms")
          val report = _main(_register_args(fixture))
          val repeated = _main(List("media", "register-site", fixture.descriptor.toString,
            s"--publication=${fixture.registry}", "--warehouse-profile=warehouse", s"--warehouse-root=${fixture.warehouse}"))
          val state = CozyArticleMediaRegistry.readCurrentSite(fixture.registry)
          val strict = _strict(fixture)
          Then("both locales have exact strict non-video paths and YouTube watch URLs with the existing owner retained")
          report.split("\n").count(_.contains(": Registered ")) shouldBe 8
          repeated should include("outcome: complete")
          (strict \ "variants").as[JsObject].keys shouldBe Set("ja", "en", "fr")
          for (locale <- _locales) {
            for (role <- _roles.filterNot(_ == "video"))
              (strict \ "variants" \ locale \ role \ "public_path").as[String] shouldBe _public_path(locale, role)
            (strict \ "variants" \ locale \ "video" \ "watch_url").as[String] shouldBe _watch_url(locale)
            (strict \ "variants" \ locale \ "video" \ "provider").as[String] shouldBe "youtube"
            (strict \ "variants" \ locale \ "video" \ "content_url").toOption shouldBe empty
            Files.exists(_destination(fixture, locale, "video")) shouldBe false
          }
          state.bundleNames shouldBe Vector("owner")
          (_json(fixture.registry.resolve("owner.json")) \ "custom").toOption shouldBe (owner \ "custom").toOption
          _locales.map(locale => _read(fixture.root.resolve(s"production/$locale.json"))) shouldBe production
          Files.exists(fixture.root.resolve("target/cozy-media")) shouldBe false
          _files(fixture.registry) shouldBe Vector("owner.json")
        }
      }

      "reject root-only legacy registration and mixed site/warehouse contexts" in {
        Given("an existing registry and malformed root-only, mixed-context or null direct requests")
        _with_fixture { fixture =>
          val before = _read(fixture.registry.resolve("owner.json"))
          val legacy = List(fixture.descriptor.toString, "--publication", fixture.registry.toString)
          val warehouse = legacy ++ List("--warehouse-profile", "warehouse")
          val requests = Vector(legacy ++ List("--warehouse-root", fixture.warehouse.toString),
            warehouse :+ "--warehouse-root", warehouse :+ "--warehouse-root=relative", warehouse :+ "--warehouse-root=",
            warehouse ++ List("--warehouse-root", null), warehouse ++ List("--warehouse-root", fixture.warehouse.toString, s"--warehouse-root=${fixture.warehouse}"),
            warehouse ++ List("--warehouse-root", fixture.warehouse.toString, "--site-root", fixture.root.toString),
            warehouse ++ List("--site-root", fixture.root.toString, "--site-config", fixture.descriptor.toString))
          requests.foreach { args =>
            When("register-site parsing receives an invalid explicit-root request")
            val error = intercept[Exception] { CozyArticleMediaSiteCommand.Config.create(args) }
            Then("an argument fault prevents registry mutation")
            error.getMessage should not be empty
            _read(fixture.registry.resolve("owner.json")) shouldBe before
          }
          val base = CozyArticleMediaSiteCommand.Config(fixture.descriptor, fixture.registry)
          val configs = Vector(base.copy(warehouseRoot = Some(fixture.warehouse)), base.copy(warehouseRoot = null),
            base.copy(warehouseProfile = Some("warehouse"), warehouseRoot = Some(null)),
            base.copy(warehouseProfile = Some("warehouse"), warehouseRoot = Some(fixture.warehouse), siteRoot = Some(fixture.root)))
          configs.foreach { config =>
            When("direct configuration misuse reaches execute before legacy evidence capture")
            val error = intercept[Exception] { CozyArticleMediaSiteCommand.execute(config) }
            Then("it fails and preserves the complete registry")
            error.getMessage should not be empty
            _read(fixture.registry.resolve("owner.json")) shouldBe before
          }
          When("binding receives a null root option or null Path directly")
          val errors = Vector(
            intercept[Exception] { CozyArticleMediaWarehouseSiteBinding.plan(CozyArticleMediaWarehouseSiteBinding.Config(fixture.descriptor, "warehouse", warehouseRoot = null)) },
            intercept[Exception] { CozyArticleMediaWarehouseSiteBinding.plan(CozyArticleMediaWarehouseSiteBinding.Config(fixture.descriptor, "warehouse", warehouseRoot = Some(null))) })
          Then("direct binding also rejects invalid roots while legacy config defaults to absent")
          errors.foreach(error => error.getMessage should not be empty)
          base.warehouseRoot shouldBe None
          CozyArticleMediaSiteCommand.Config.create(legacy).warehouseRoot shouldBe None
          _read(fixture.registry.resolve("owner.json")) shouldBe before
        }
      }

      "preserve partial and dry-run canonical registration with explicit-root forwarding" in {
        Given("a missing canonical JA PDF with an old output, other placed roles and retained unrelated owner entries")
        _with_fixture { fixture =>
          _place_nonvideo(fixture, missing = Some("article_pdf-ja"))
          val before = _read(fixture.registry.resolve("owner.json"))
          When("exact missing target and the mixed selection are dry-run, then available roles are registered")
          val missing = _dispatch(_register_args(fixture) ++ List("--target", "article_pdf-ja"))
          val dry = _dispatch(_register_args(fixture) :+ "--dry-run")
          val afterdry = _read(fixture.registry.resolve("owner.json"))
          val plan = CozyArticleMediaWarehouseSiteBinding.plan(CozyArticleMediaWarehouseSiteBinding.Config(
            fixture.descriptor, "warehouse", warehouseRoot = Some(fixture.warehouse)), Map.empty)
          val report = _dispatch(_register_args(fixture))
          val strict = _strict(fixture)
          Then("missing has no old-output fallback, dry-run is read-only, and only available roles update the existing owner")
          missing should include("article_pdf-ja: Unavailable")
          dry should include("article_pdf-ja: Unavailable")
          dry should include("infographic-en: Planned")
          afterdry shouldBe before
          plan.rows.count(_.status == CozyArticleMediaWarehouseSiteBinding.Status.Unavailable) shouldBe 1
          report should include("outcome: partial")
          report.split("\n").count(_.contains(": Registered ")) shouldBe 7
          (strict \ "variants" \ "ja" \ "article_pdf" \ "public_path").as[String] shouldBe "/old/article.pdf"
          (strict \ "variants" \ "fr" \ "infographic" \ "public_path").as[String] shouldBe "/fr/kept.png"
          (strict \ "variants" \ "en" \ "article_pdf" \ "public_path").as[String] shouldBe _public_path("en", "article_pdf")
          CozyArticleMediaRegistry.readCurrentSite(fixture.registry).entries.find(_.path == "metadata/generic.json").get.metadata shouldBe Json.obj("retained" -> true)
          CozyArticleMediaRegistry.readCurrentSite(fixture.registry).entries.find(_.path == "metadata/article-media/modeling/other.json").get.metadata shouldBe
            Json.obj("type" -> "article-media-publication", "article" -> Json.obj("identity" -> "modeling/other"),
              "variants" -> Json.obj("en" -> Json.obj("infographic" -> Json.obj("public_path" -> "/en/other.png"))))
          _files(fixture.registry) shouldBe Vector("owner.json")
          _read(_source(fixture, "ja", "article_pdf")) shouldBe "article_pdf-ja original"
          Files.exists(_destination(fixture, "ja", "article_pdf")) shouldBe false
        }
      }
    }
  }

  private def _with_fixture(f: WarehouseRootFixture.Data => Unit): Unit = {
    val work = Paths.get("target/cozy-media-warehouse-root/work").toAbsolutePath.normalize()
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "root-")
    val fixture = WarehouseRootFixture.Data(root, root.resolve("media.json"), root.resolve("explicit warehouse"), root.resolve("fallback"), root.resolve("publication"))
    try {
      Vector(fixture.warehouse, fixture.fallback, fixture.registry).foreach(path => Files.createDirectory(path))
      _write_json(fixture.descriptor, Json.obj("schema" -> "cozy.media.v1",
        "knowledge" -> Json.obj("id" -> "example", "source" -> "missing-article.dox"), "languages" -> Json.arr("ja", "en"),
        "articleMedia" -> Json.obj("articleIdentity" -> _identity, "publicationProfile" -> "warehouse"),
        "profiles" -> Json.obj("warehouse" -> Json.obj("root" -> "fallback", "rootEnv" -> _environment)),
        "resources" -> JsArray(for (locale <- _locales; role <- _roles) yield _resource(locale, role))))
      for (locale <- _locales; role <- _roles) _write(_source(fixture, locale, role), s"$role-$locale original")
      for (locale <- _locales)
        _write_json(root.resolve(s"production/$locale.json"), Json.obj("category" -> "modeling", "article" -> "example", "language" -> locale,
          "render" -> Json.obj("status" -> "completed", "qa" -> Json.obj("status" -> "technical-and-visual-qa-passed")),
          "youtube" -> Json.obj("status" -> "published", "videoUrl" -> _watch_url(locale))))
      _write(root.resolve("producer/retained.json"), "unrelated producer data")
      val strict = Json.obj("type" -> "article-media-publication", "article" -> Json.obj("identity" -> _identity),
        "variants" -> Json.obj("ja" -> Json.obj("article_pdf" -> Json.obj("public_path" -> "/old/article.pdf", "media_type" -> "application/pdf")),
          "fr" -> Json.obj("infographic" -> Json.obj("public_path" -> "/fr/kept.png"))))
      _write_json(fixture.registry.resolve("owner.json"), Json.obj("schema" -> "cozy.publish-project.v1", "type" -> "publication-bundle",
        "publication" -> Json.obj("name" -> "owner"), "sourceRepository" -> "existing-project", "sourcePath" -> "authored",
        "sourceCommit" -> "historical", "custom" -> Json.obj("preserved" -> true), "entries" -> Json.arr(
          _entry(s"metadata/article-media/${_identity}.json", strict), _entry("metadata/generic.json", Json.obj("retained" -> true)),
          _entry("metadata/article-media/modeling/other.json", Json.obj("type" -> "article-media-publication",
            "article" -> Json.obj("identity" -> "modeling/other"), "variants" -> Json.obj("en" -> Json.obj(
              "infographic" -> Json.obj("public_path" -> "/en/other.png"))))))))
      f(fixture)
    } finally {
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }

  private def _resource(locale: String, role: String): JsObject = {
    val media = Json.obj("role" -> role) ++ (role match {
      case "video" => Json.obj("production" -> s"production/$locale.json")
      case "infographic" => Json.obj("publicPath" -> _public_path(locale, role), "mediaType" -> "image/png", "alt" -> s"Example $locale")
      case _ => Json.obj("publicPath" -> _public_path(locale, role), "mediaType" -> "application/pdf", "label" -> role)
    })
    Json.obj("id" -> s"$role-$locale", "kind" -> (if (role == "video") "video" else if (role == "infographic") "infographic" else "document"),
      "language" -> locale, "build" -> "prebuilt", "output" -> s"outputs/$role-$locale.${_extension(role)}",
      "publications" -> Json.obj("warehouse" -> _public_path(locale, role).drop(1)), "articleMedia" -> media)
  }

  private def _extension(role: String): String = if (role == "video") "mp4" else if (role == "infographic") "png" else "pdf"
  private def _basename(role: String): String = role match {
    case "article_pdf" => "article.pdf"
    case "summary_slides_pdf" => "slides.pdf"
    case "video" => "video.mp4"
    case _ => "infographic.png"
  }
  private def _public_path(locale: String, role: String): String = s"/media/$locale/${_identity}/${_basename(role)}"
  private def _source(fixture: WarehouseRootFixture.Data, locale: String, role: String): Path = fixture.root.resolve(s"outputs/$role-$locale.${_extension(role)}")
  private def _destination(fixture: WarehouseRootFixture.Data, locale: String, role: String): Path = fixture.warehouse.resolve(_public_path(locale, role).drop(1))
  private def _watch_url(locale: String): String = if (locale == "ja") "https://youtu.be/ja_Example-1" else "https://www.youtube.com/watch?v=en_Example-2"
  private def _command(fixture: WarehouseRootFixture.Data): CozyMedia.CommandConfig = CozyMedia.CommandConfig(fixture.descriptor, profile = Some("warehouse"))
  private def _deploy_args(fixture: WarehouseRootFixture.Data): List[String] = List("media", "deploy", fixture.descriptor.toString,
    "--profile", "warehouse", "--warehouse-root", fixture.warehouse.toString)
  private def _register_args(fixture: WarehouseRootFixture.Data): List[String] = List("media", "register-site", fixture.descriptor.toString,
    "--publication", fixture.registry.toString, "--warehouse-profile", "warehouse", "--warehouse-root", fixture.warehouse.toString)
  private def _main(args: List[String]): String = {
    val output = new ByteArrayOutputStream()
    Console.withOut(output) { cozy.Cozy.main(args.toArray) }
    output.toString("UTF-8")
  }
  private def _dispatch(args: List[String]): String = {
    val output = new ByteArrayOutputStream()
    Console.withOut(output) { CozyMediaDispatcher.execute(args, _unused_runner) }
    output.toString("UTF-8")
  }
  private def _profile(fixture: WarehouseRootFixture.Data, profile: JsObject): Unit =
    _write_json(fixture.descriptor, _json(fixture.descriptor) ++ Json.obj("profiles" -> Json.obj("warehouse" -> profile)))
  private def _place_nonvideo(fixture: WarehouseRootFixture.Data, missing: Option[String] = None): Unit =
    for (locale <- _locales; role <- _roles.filterNot(_ == "video") if !missing.contains(s"$role-$locale"))
      _write(_destination(fixture, locale, role), _read(_source(fixture, locale, role)))
  private def _retained(fixture: WarehouseRootFixture.Data): Vector[String] =
    Vector(_read(fixture.descriptor), _read(fixture.registry.resolve("owner.json")), _read(fixture.root.resolve("producer/retained.json"))) ++
      _locales.map(locale => _read(fixture.root.resolve(s"production/$locale.json"))) ++
      (for (locale <- _locales; role <- _roles) yield _read(_source(fixture, locale, role)))
  private def _strict(fixture: WarehouseRootFixture.Data): JsValue =
    CozyArticleMediaRegistry.readCurrentSite(fixture.registry).entries.find(_.path == s"metadata/article-media/${_identity}.json").get.metadata
  private def _entry(path: String, metadata: JsValue): JsObject =
    Json.obj("path" -> path, "key" -> path.stripPrefix("metadata/").stripSuffix(".json"), "metadata" -> metadata)
  private def _files(directory: Path): Vector[String] = {
    val stream = Files.list(directory)
    try stream.iterator().asScala.toVector.map(_.getFileName.toString).sorted finally stream.close()
  }
  private def _json(file: Path): JsObject = Json.parse(_read(file)).as[JsObject]
  private def _read(file: Path): String = Files.readString(file, StandardCharsets.UTF_8)
  private def _write_json(file: Path, value: JsValue): Unit = { _write(file, Json.prettyPrint(value)); () }
  private def _write(file: Path, value: String): Path = {
    Files.createDirectories(file.getParent)
    Files.writeString(file, value, StandardCharsets.UTF_8)
    file
  }
}
