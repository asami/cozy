package cozy.publication

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.collection.JavaConverters._
import cozy.media.{CozyMedia, CozyMediaDispatcher}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.{ImageReference, PdfDocumentReference}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

/*
 * @since   Oct.  6, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private object WarehouseSiteFixture {
  final case class Data(root: Path, descriptor: Path, warehouse: Path, registry: Path)
}

final class CozyArticleMediaWarehouseSiteCommandSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _identity = "modeling/example"
  private val _roles = Vector("infographic", "article_pdf", "summary_slides_pdf", "video")

  "Canonical warehouse site registration" should {
    "use placed artifacts and declared publication evidence" which {
      "register all four roles in both locales from canonical destinations and published YouTube evidence" in {
        Given("eight explicit JA/EN resources, six canonical non-video files and two QA-passed published production records")
        _with_fixture { fixture =>
          var calls = Vector.empty[Vector[String]]
          val runner = new CozyMedia.ProcessRunner {
            def run(command: Vector[String], workingdirectory: Path): Int = {
              calls = calls :+ command
              0
            }
          }
          val captured = new ByteArrayOutputStream()

          When("the existing media dispatcher registers the explicitly selected warehouse profile")
          val handled = Console.withOut(captured) {
            CozyMediaDispatcher.execute(_args(fixture), runner)
          }
          val strict = _strict(fixture)

          Then("both locales contain the four distinct strict roles and exact canonical non-video paths")
          handled shouldBe true
          (strict \ "variants").as[JsObject].keys shouldBe Set("ja", "en")
          for (locale <- Vector("ja", "en")) {
            val variant = (strict \ "variants" \ locale).as[JsObject]
            variant.keys shouldBe _roles.toSet
            (variant \ "infographic" \ "public_path").as[String] shouldBe _public_path(locale, "infographic")
            (variant \ "infographic" \ "media_type").as[String] shouldBe "image/png"
            (variant \ "infographic" \ "alt").as[String] shouldBe s"Example $locale infographic"
            (variant \ "article_pdf" \ "public_path").as[String] shouldBe _public_path(locale, "article_pdf")
            (variant \ "article_pdf" \ "label").as[String] shouldBe "Article PDF"
            (variant \ "summary_slides_pdf" \ "public_path").as[String] shouldBe _public_path(locale, "summary_slides_pdf")
            (variant \ "summary_slides_pdf" \ "label").as[String] shouldBe "Summary slides PDF"
            (variant \ "video" \ "presentation").as[String] shouldBe "external-link"
            (variant \ "video" \ "status").as[String] shouldBe "published"
            (variant \ "video" \ "provider").as[String] shouldBe "youtube"
            (variant \ "video" \ "watch_url").as[String] shouldBe _watch_url(locale)
            (variant \ "video" \ "content_url").toOption shouldBe empty
            Files.exists(fixture.warehouse.resolve(s"media/$locale/${_identity}/video.mp4")) shouldBe false
          }
          captured.toString("UTF-8") should include("outcome: complete")
          captured.toString("UTF-8").split("\n").count(_.contains(": Registered ")) shouldBe 8
          calls shouldBe empty
        }
      }

      "register a placed PDF without producer receipts or a remaining old output" in {
        Given("a declared article PDF is already canonical, while its old output and all producer receipts are absent")
        _with_fixture { fixture =>
          Files.exists(fixture.root.resolve("old/article_pdf-ja.pdf")) shouldBe false
          Files.exists(fixture.root.resolve("target/cozy-media")) shouldBe false

          When("canonical registration selects just that PDF")
          val report = CozyArticleMediaSiteCommand.execute(_config(fixture, Some("article_pdf-ja")))

          Then("the placed PDF is registered with its declared label and no producer outputs appear")
          (_strict(fixture) \ "variants" \ "ja" \ "article_pdf" \ "public_path").as[String] shouldBe _public_path("ja", "article_pdf")
          report should include("article_pdf-ja: Registered")
          Files.exists(fixture.root.resolve("old/article_pdf-ja.pdf")) shouldBe false
          Files.exists(fixture.root.resolve("target/cozy-media")) shouldBe false
        }
      }

      "report unplaced roles without adopting an old generated output" in {
        Vector(false, true).foreach { dryrun =>
          Given("an old generated PDF exists, its canonical destination is missing and an earlier strict role is registered")
          _with_fixture { fixture =>
            Files.delete(_destination(fixture, "ja", "article_pdf"))
            _write(fixture.root.resolve("old/article_pdf-ja.pdf"), "old PDF")
            val previous = _pdf_publication("/old/article.pdf")
            _write_bundle(fixture, "owner", Vector(_entry(previous.entryPath, previous.metadata)))

            When("real or dry-run canonical registration selects the missing placement")
            val report = CozyArticleMediaSiteCommand.execute(_config(fixture, Some("article_pdf-ja"), dryrun))

            Then("the row is unavailable, the old strict role stays intact and no local output is adopted")
            report should include("article_pdf-ja: Unavailable")
            report should include("outcome: partial")
            _strict(fixture) shouldBe previous.metadata
            _registry_files(fixture) shouldBe Vector("owner.json")
            Files.exists(_destination(fixture, "ja", "article_pdf")) shouldBe false
          }
        }
      }

      "apply available roles while preserving unavailable and unrelated state" in {
        Given("available roles are mixed with an absent canonical PDF, and one owner contains other roles/locales/articles and historical integrity")
        _with_fixture { fixture =>
          Files.delete(_destination(fixture, "ja", "article_pdf"))
          val previous = CozyArticleMediaPublication.produce(_identity, Vector(
            CozyArticleMediaPublication.Variant("ja", articlePdf = Some(PdfDocumentReference(new URI("/old/article.pdf"), "application/pdf", None))),
            CozyArticleMediaPublication.Variant("fr", infographic = Some(ImageReference(new URI("/fr/kept.png"), Some("image/png"), None)))
          ))
          val unrelated = CozyArticleMediaPublication.produce("modeling/other", Vector(
            CozyArticleMediaPublication.Variant("en", infographic = Some(ImageReference(new URI("/en/other.png"), None, None)))
          ))
          val integrity = _integrity(CozyArticleMediaIntegrity.Role.Infographic)
          val generic = _entry("metadata/generic.json", Json.obj("kept" -> true)) ++ Json.obj("annotation" -> "retained")
          _write_bundle(fixture, "owner", Vector(_entry(previous.entryPath, previous.metadata),
            _entry(unrelated.entryPath, unrelated.metadata), _entry(integrity.entryPath, integrity.metadata), generic))
          val before = _read_json(fixture.registry.resolve("owner.json"))

          When("canonical registration applies the available exact roles")
          val report = CozyArticleMediaSiteCommand.execute(_config(fixture))
          val state = CozyArticleMediaRegistry.readCurrentSite(fixture.registry)
          val after = _read_json(fixture.registry.resolve("owner.json"))
          val strict = _strict(fixture)

          Then("the result is partial, the unavailable JA PDF and the French locale are preserved")
          report should include("outcome: partial")
          report should include("article_pdf-ja: Unavailable")
          report should include("video-ja: Registered")
          (strict \ "variants" \ "ja" \ "article_pdf").toOption shouldBe (previous.metadata \ "variants" \ "ja" \ "article_pdf").toOption
          (strict \ "variants" \ "fr").toOption shouldBe (previous.metadata \ "variants" \ "fr").toOption
          state.entries.find(_.path == integrity.entryPath).map(_.metadata) shouldBe Some(integrity.metadata)
          state.entries.find(_.path == unrelated.entryPath).map(_.metadata) shouldBe Some(unrelated.metadata)
          (after \ "entries").as[JsArray].value.find(x => (x \ "path").as[String] == "metadata/generic.json") shouldBe Some(generic)
          Vector("publication", "sourceRepository", "sourcePath", "sourceCommit", "custom").foreach { field =>
            (after \ field).toOption shouldBe (before \ field).toOption
          }
          _registry_files(fixture) shouldBe Vector("owner.json")
        }
      }

      "leave an all-unavailable selection without creating an owner bundle" in {
        Given("the descriptor declares all roles but no canonical non-video file or production record is present")
        _with_fixture(placed = false, production = false) { fixture =>
          When("canonical real registration evaluates the complete selection")
          val report = CozyArticleMediaSiteCommand.execute(_config(fixture))
          val merged = CozyArticleMediaRegistry.mergeCurrentSite(fixture.registry, Vector.empty, dryRun = false)

          Then("every row is unavailable, the no-op is truthful and no owner bundle is created")
          report should include("outcome: partial")
          report.split("\n").count(_.contains(": Unavailable ")) shouldBe 8
          _registry_files(fixture) shouldBe empty
          merged.bundlePaths shouldBe empty
          merged.entryPaths shouldBe empty
          merged.state shouldBe CozyArticleMediaRegistry.CurrentSiteState(Vector.empty, Vector.empty)
        }
      }
    }

    "reuse the existing registry namespace" which {
      "reuse the existing article owner and create only the canonical default for a new identity" in {
        Vector(false, true).foreach { existing =>
          Given("the article has either one configured integrity-only owner or no owner in the current registry")
          _with_fixture { fixture =>
            if (existing) {
              val integrity = _integrity(CozyArticleMediaIntegrity.Role.Video)
              _write_bundle(fixture, "owner", Vector(_entry(integrity.entryPath, integrity.metadata)))
            }

            When("a canonical available PDF is registered")
            CozyArticleMediaSiteCommand.execute(_config(fixture, Some("article_pdf-ja")))
            val state = CozyArticleMediaRegistry.readCurrentSite(fixture.registry)

            Then("exactly the existing owner or the canonical article-media default owns the strict record")
            val owner = if (existing) "owner" else "article-media"
            _registry_files(fixture) shouldBe Vector(s"$owner.json")
            state.bundleNames shouldBe Vector(owner)
            state.entries.find(_.path == s"metadata/article-media/${_identity}.json").map(_.bundleName) shouldBe Some(owner)
          }
        }
      }

      "reject split owners and malformed configured bundles before any replacement" in {
        Vector("split", "malformed", "duplicate-key", "unsafe-path").foreach { variant =>
          Given(s"an existing valid article owner and configured $variant input")
          _with_fixture { fixture =>
            val previous = _pdf_publication("/old/article.pdf")
            _write_bundle(fixture, "owner", Vector(_entry(previous.entryPath, previous.metadata)))
            variant match {
              case "split" =>
                val integrity = _integrity(CozyArticleMediaIntegrity.Role.Video)
                _write_bundle(fixture, "another", Vector(_entry(integrity.entryPath, integrity.metadata)))
              case "malformed" => _write(fixture.registry.resolve("broken.json"), "{invalid")
              case "duplicate-key" => _write_bundle(fixture, "another", Vector(_entry(previous.entryPath, previous.metadata)))
              case "unsafe-path" => _write_bundle(fixture, "another", Vector(_entry("metadata/../escape.json", Json.obj())))
            }
            val expectedfiles = _registry_files(fixture)

            When("canonical registration preflights the complete configured input")
            val error = intercept[RuntimeException](CozyArticleMediaSiteCommand.execute(_config(fixture)))

            Then("an actionable semantic failure occurs before changing the valid owner or creating output")
            error.getMessage.toLowerCase should include(variant match {
              case "split" => "multiple bundle owners"
              case "malformed" => "malformed json"
              case "duplicate-key" => "duplicate"
              case _ => "path"
            })
            (_read_json(fixture.registry.resolve("owner.json")) \ "entries").as[JsArray] shouldBe JsArray(Vector(_entry(previous.entryPath, previous.metadata)))
            _registry_files(fixture) shouldBe expectedfiles
          }
        }
      }

      "reject duplicate selected locale-role tuples before registry writes" in {
        Given("distinct PNG and SVG resource IDs both select the same JA infographic role")
        _with_fixture { fixture =>
          val additional = _resource("ja", "infographic") ++ Json.obj("id" -> "infographic-ja-svg", "output" -> "old/second.svg",
            "publications" -> Json.obj("warehouse" -> s"media/ja/${_identity}/infographic.svg"),
            "articleMedia" -> Json.obj("role" -> "infographic", "mediaType" -> "image/svg+xml", "publicPath" -> s"/media/ja/${_identity}/infographic.svg"))
          _descriptor_resources(fixture, _resources :+ additional)

          When("the complete warehouse selection is registered")
          val error = intercept[RuntimeException](CozyArticleMediaSiteCommand.execute(_config(fixture)))

          Then("the duplicate semantic key rejects the selection before any registry bundle is written")
          error.getMessage should include("duplicate selected article/locale/role")
          _registry_files(fixture) shouldBe empty
        }
      }

      "read actual-shaped mixed publication containers without enrolling the framework contract" in {
        Given("six existing-style publication bundles share the configured directory with the explicitly typed framework contract")
        _with_fixture { fixture =>
          val names = Vector("textus-semantic-integration-engine", "textus-user-notification", "textus-blog", "textus-user-account", "maven-repository", "textus-tutorial")
          names.foreach(name => _write_bundle(fixture, name, Vector(_entry(s"metadata/projects/$name/metadata.json", Json.obj("name" -> name)))))
          val contract = Json.obj("schemaVersion" -> "cncf.framework-publication.v1", "kind" -> "framework-publication-contract",
            "id" -> "DOC03-PUBLIC-FRAMEWORK-PROJECTION", "framework" -> Json.obj("category" -> "component-based-development"))
          val contractfile = fixture.registry.resolve("cncf-framework-publication.json")
          _write_json(contractfile, contract)

          When("the current-site parser reads the mixed container and canonical registration adds an article owner")
          val initial = CozyArticleMediaRegistry.readCurrentSite(fixture.registry)
          CozyArticleMediaSiteCommand.execute(_config(fixture, Some("article_pdf-ja")))
          val state = CozyArticleMediaRegistry.readCurrentSite(fixture.registry)

          Then("the six bundles are recognized and the framework contract remains untouched and never owns media")
          initial.bundleNames shouldBe names.sorted
          state.bundleNames shouldBe (names :+ "article-media").sorted
          state.entries.exists(_.bundleName == "cncf-framework-publication") shouldBe false
          _read_json(contractfile) shouldBe contract

          Vector(contract ++ Json.obj("entries" -> Json.arr()), contract ++ Json.obj("type" -> "unknown"),
            contract - "kind", Json.obj("kind" -> "other")).foreach { invalid =>
            Given("the shared contract filename instead contains a bundle-shaped, unknown or incomplete impostor")
            _write_json(contractfile, invalid)
            val before = _read_json(fixture.registry.resolve("article-media.json"))

            When("canonical registration encounters that present invalid container input")
            val error = intercept[RuntimeException](CozyArticleMediaSiteCommand.execute(_config(fixture)))

            Then("the impostor fails strict admission before any media owner replacement")
            error.getMessage should include("invalid type")
            _read_json(fixture.registry.resolve("article-media.json")) shouldBe before
          }
        }
      }
    }

    "reject contradictory present inputs and retain declared incomplete outcomes" which {
      "reject unsafe canonical destinations and production paths" in {
        Vector("destination-leaf", "destination-ancestor", "production-leaf", "production-ancestor", "production-traversal", "production-absolute", "production-nonobject", "production-malformed").foreach { variant =>
          Given(s"a canonical declaration with $variant input")
          _with_fixture { fixture =>
            val target = if (variant.startsWith("destination")) "article_pdf-ja" else "video-ja"
            variant match {
              case "destination-leaf" =>
                Files.delete(_destination(fixture, "ja", "article_pdf"))
                Files.createSymbolicLink(_destination(fixture, "ja", "article_pdf"), _destination(fixture, "ja", "summary_slides_pdf"))
              case "destination-ancestor" =>
                val article = fixture.warehouse.resolve(s"media/ja/${_identity}")
                val moved = fixture.root.resolve("moved-canonical")
                Files.move(article, moved)
                Files.createSymbolicLink(article, moved)
              case "production-leaf" =>
                Files.delete(_production_path(fixture, "ja"))
                Files.createSymbolicLink(_production_path(fixture, "ja"), _production_path(fixture, "en"))
              case "production-ancestor" =>
                val moved = fixture.root.resolve("moved-production")
                Files.move(fixture.root.resolve("production"), moved)
                Files.createSymbolicLink(fixture.root.resolve("production"), moved)
              case "production-traversal" => _set_production_declaration(fixture, "../outside.json")
              case "production-absolute" => _set_production_declaration(fixture, _production_path(fixture, "ja").toString)
              case "production-nonobject" => _write(_production_path(fixture, "ja"), "[]")
              case "production-malformed" => _write(_production_path(fixture, "ja"), "{invalid")
            }

            When("canonical registration preflights the exact target")
            val error = intercept[RuntimeException](CozyArticleMediaSiteCommand.execute(_config(fixture, Some(target))))

            Then("unsafe or malformed present inputs fail before registry mutation")
            error.getMessage.toLowerCase should include(variant match {
              case value if value.endsWith("leaf") || value.endsWith("ancestor") => "symlink"
              case "production-nonobject" => "json object"
              case "production-malformed" => "json"
              case _ => "relative path"
            })
            _registry_files(fixture) shouldBe empty
          }
        }
      }

      "reject published video evidence that contradicts article or locale or accepted YouTube URL" in {
        val invalidrecords = Vector(
          _production("ja") ++ Json.obj("article" -> "different"),
          _production("ja") ++ Json.obj("language" -> "en"),
          _production("ja") ++ Json.obj("youtube" -> Json.obj("status" -> "published", "videoUrl" -> "https://example.org/watch")),
          _production("ja") ++ Json.obj("youtube" -> Json.obj("status" -> "published", "videoUrl" -> "https://youtu.be/ja_Example-1?tracking=yes")),
          _production("ja") ++ Json.obj("youtube" -> Json.obj("status" -> "published", "videoUrl" -> _watch_url("ja"), "videoId" -> "contradiction"))
        )
        invalidrecords.foreach { record =>
          Given("a present published production record contradicts its identity, exact locale, accepted URL or optional videoId")
          _with_fixture { fixture =>
            _write_json(_production_path(fixture, "ja"), record)

            When("canonical registration includes the contradictory video and available non-video roles")
            val error = intercept[RuntimeException](CozyArticleMediaSiteCommand.execute(_config(fixture)))

            Then("the complete preflight fails before replacing any registry metadata")
            error.getMessage should include("Warehouse site video")
            _registry_files(fixture) shouldBe empty
          }
        }
      }

      "report declared unfinished video publication as unavailable" in {
        val unfinished = Vector(
          _production("ja") ++ Json.obj("render" -> Json.obj("status" -> "pending", "qa" -> Json.obj("status" -> "technical-and-visual-qa-passed"))),
          _production("ja") ++ Json.obj("render" -> Json.obj("status" -> "completed", "qa" -> Json.obj("status" -> "pending"))),
          _production("ja") ++ Json.obj("youtube" -> Json.obj("status" -> "not-published"))
        )
        unfinished.foreach { record =>
          Given("the production identity and locale match but render, QA or YouTube publication is unfinished")
          _with_fixture { fixture =>
            _write_json(_production_path(fixture, "ja"), record)

            When("the declared video alone is considered for canonical registration")
            val report = CozyArticleMediaSiteCommand.execute(_config(fixture, Some("video-ja")))

            Then("the row is unavailable with its reason and no published tuple or master URL is invented")
            report should include("video-ja: Unavailable")
            report should include("Video publication is unfinished")
            report should include("outcome: partial")
            report should not include("video.mp4")
            _registry_files(fixture) shouldBe empty
          }
        }
      }
    }

    "select the additive CLI boundary" which {
      "support exact targets and both warehouse-profile option forms without site-context mixing" in {
        Given("an explicit warehouse descriptor and an existing configured publication directory")
        _with_fixture { fixture =>
          val base = List(fixture.descriptor.toString, "--publication", fixture.registry.toString)

          When("the separated and equals options select one exact mapped PDF")
          val separated = CozyArticleMediaSiteCommand.Config.create(base ++ List("--warehouse-profile", "warehouse", "--target", "article_pdf-ja"))
          val equals = CozyArticleMediaSiteCommand.Config.create(base ++ List("--warehouse-profile=warehouse", "--target=article_pdf-ja"))
          val report = CozyArticleMediaSiteCommand.execute(equals)

          Then("both configurations agree and only the exact selected locale/role is registered")
          separated shouldBe equals
          report should include("article_pdf-ja: Registered")
          report should not include("video-ja:")
          (_strict(fixture) \ "variants").as[JsObject].keys shouldBe Set("ja")
          (_strict(fixture) \ "variants" \ "ja").as[JsObject].keys shouldBe Set("article_pdf")

          Vector(List("--warehouse-profile"), List("--warehouse-profile="), List("--warehouse-profile", "warehouse", "--warehouse-profile=warehouse"),
            List("--warehouse-profile=warehouse", "--site-root", fixture.root.toString),
            List("--warehouse-profile=warehouse", "--site-config", fixture.descriptor.toString),
            List("--profile", "warehouse"), List("--profile=warehouse")).foreach { options =>
            When("missing, duplicate, mixed-site or unsupported legacy-profile options are supplied")
            val error = intercept[RuntimeException](CozyArticleMediaSiteCommand.Config.create(base ++ options))
            Then("each ambiguous or unsupported request fails option admission")
            error.getMessage should not be empty
          }

          When("the historical no-option configuration is parsed")
          val legacy = CozyArticleMediaSiteCommand.Config.create(base)
          Then("it retains no warehouse opt-in")
          legacy.warehouseProfile shouldBe None

          Vector(Some("not-a-target"), Some("video"), Some("article_pdf-ja-suffix")).foreach { target =>
            When("a non-exact target is requested")
            val error = intercept[RuntimeException](CozyArticleMediaSiteCommand.execute(_config(fixture, target)))
            Then("only an exact mapped resource ID is eligible")
            error.getMessage should include("Unknown media deploy target")
          }

          When("arbitrary nonempty alphanumeric suffixes are appended to a valid target ID")
          val exacttargets = Test.check(Test.Parameters.default, Prop.forAll(Gen.nonEmptyListOf(Gen.alphaNumChar)) { suffix =>
            val target = Some("article_pdf-ja-" + suffix.mkString)
            val error = intercept[RuntimeException](CozyArticleMediaSiteCommand.execute(_config(fixture, target)))
            error.getMessage.contains("Unknown media deploy target")
          })
          Then("the exact-target boundary rejects every generated suffix")
          exacttargets.passed shouldBe true

          Given("the requested declared profile differs from the top-level article association")
          _write_json(fixture.descriptor, _read_json(fixture.descriptor) ++
            Json.obj("articleMedia" -> Json.obj("articleIdentity" -> _identity, "publicationProfile" -> "different")))
          When("canonical registration is requested with that mismatched association")
          val mismatch = intercept[RuntimeException](CozyArticleMediaSiteCommand.execute(equals))
          Then("profile equality is required and the prior exact registration remains")
          mismatch.getMessage should include("publicationProfile must equal")
          (_strict(fixture) \ "variants" \ "ja").as[JsObject].keys shouldBe Set("article_pdf")
        }
      }

      "keep canonical dry-run free of registry writes and self-managed locks" in {
        Given("available canonical resources and one configured generic publication owner")
        _with_fixture { fixture =>
          _write_bundle(fixture, "owner", Vector(_entry("metadata/generic.json", Json.obj("kept" -> true))))
          val before = _read_json(fixture.registry.resolve("owner.json"))

          When("canonical dry-run performs semantic selection and registry ownership preflight")
          val report = CozyArticleMediaSiteCommand.execute(_config(fixture, dryrun = true))
          val plan = CozyArticleMediaRegistry.mergeCurrentSite(fixture.registry, Vector(
            CozyArticleMediaRegistry.SiteRoleUpdate(_identity,
              CozyArticleMediaPublication.Variant("ja", articlePdf = Some(PdfDocumentReference(new URI(_public_path("ja", "article_pdf")), "application/pdf", None))))
          ), dryRun = true)

          Then("the report and projected paths are planned while only the original semantic input remains")
          report should include("mode: dry-run")
          report should include("outcome: complete")
          report.split("\n").count(_.contains(": Planned ")) shouldBe 8
          plan.bundlePaths shouldBe Vector(fixture.registry.resolve("article-media.json"))
          plan.entryPaths shouldBe Vector(s"metadata/article-media/${_identity}.json")
          plan.state.bundleNames shouldBe Vector("article-media", "owner")
          _registry_files(fixture) shouldBe Vector("owner.json")
          _read_json(fixture.registry.resolve("owner.json")) shouldBe before
          Files.exists(fixture.root.resolve("target")) shouldBe false
        }
      }

      "round-trip canonical strict metadata through the existing article-media parser and native media dispatch" in {
        Given("all four canonical roles and a task-local native command fixture")
        _with_fixture { fixture =>
          val captured = new ByteArrayOutputStream()

          When("native Cozy.main registers the explicit warehouse profile and SmartDox loads the resulting bundle")
          Console.withOut(captured) { cozy.Cozy.main(_args(fixture).toArray) }
          val state = CozyArticleMediaRegistry.readCurrentSite(fixture.registry)
          val projection = CozyArticleMediaRegistry.canonicalProjection(CozyArticleMediaRegistry.Snapshot(state.entries))
          val parsed = PublishMetadata.load(fixture.registry.toFile).get
          val expected = projection.strictPublications.head.publication

          Then("the existing strict decoder and pinned SmartDox parser agree on the complete publication and both locale variants")
          parsed.articleMedia.publications.find(_.articleIdentity == _identity) shouldBe Some(expected)
          expected.variants.foreach { variant => parsed.resolveArticleMedia(_identity, variant.locale) shouldBe Some(variant) }
          projection.integrityResults shouldBe empty
          captured.toString("UTF-8") should include("warehouse-profile: warehouse")
          captured.toString("UTF-8") should include("outcome: complete")
          captured.toString("UTF-8") should include("video-ja: Registered")
          Files.exists(fixture.root.resolve("old")) shouldBe false
          Files.exists(fixture.root.resolve("target")) shouldBe false
        }
      }
    }
  }

  private def _with_fixture(f: WarehouseSiteFixture.Data => Unit): Unit = _with_fixture(true, true)(f)

  private def _with_fixture(placed: Boolean, production: Boolean)(f: WarehouseSiteFixture.Data => Unit): Unit = {
    val work = Paths.get("target/cozy-article-media-warehouse-site/work").toAbsolutePath.normalize()
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "registration-")
    val fixture = WarehouseSiteFixture.Data(root, root.resolve("media.json"), root.resolve("warehouse"), root.resolve("publication"))
    try {
      Files.createDirectories(fixture.warehouse)
      Files.createDirectories(fixture.registry)
      _write_json(fixture.descriptor, Json.obj("schema" -> "cozy.media.v1",
        "knowledge" -> Json.obj("id" -> "example", "source" -> "article.dox"), "languages" -> Json.arr("ja", "en"),
        "articleMedia" -> Json.obj("articleIdentity" -> _identity, "publicationProfile" -> "warehouse"),
        "profiles" -> Json.obj("warehouse" -> Json.obj("root" -> "warehouse")), "resources" -> JsArray(_resources)))
      if (placed)
        for (locale <- Vector("ja", "en"); role <- _roles.filterNot(_ == "video"))
          _write(_destination(fixture, locale, role), s"$locale $role canonical bytes")
      if (production)
        Vector("ja", "en").foreach(locale => _write_json(_production_path(fixture, locale), _production(locale)))
      f(fixture)
    } finally {
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(file => Files.deleteIfExists(file))
      finally stream.close()
    }
  }

  private def _resources: Vector[JsObject] =
    for (locale <- Vector("ja", "en"); role <- _roles) yield _resource(locale, role)

  private def _resource(locale: String, role: String): JsObject = {
    val kind = if (role == "video") "video" else if (role == "infographic") "infographic" else "document"
    val extension = if (role == "video") "mp4" else if (role == "infographic") "png" else "pdf"
    val media = Json.obj("role" -> role) ++ (role match {
      case "video" => Json.obj("production" -> s"production/$locale.json")
      case "infographic" => Json.obj("publicPath" -> _public_path(locale, role), "mediaType" -> "image/png", "alt" -> s"Example $locale infographic")
      case _ => Json.obj("publicPath" -> _public_path(locale, role), "mediaType" -> "application/pdf",
        "label" -> (if (role == "article_pdf") "Article PDF" else "Summary slides PDF"))
    })
    Json.obj("id" -> s"$role-$locale", "kind" -> kind, "language" -> locale, "build" -> "copy",
      "output" -> s"old/$role-$locale.$extension", "publications" -> Json.obj("warehouse" -> _public_path(locale, role).drop(1)), "articleMedia" -> media)
  }

  private def _basename(role: String): String = role match {
    case "infographic" => "infographic.png"
    case "article_pdf" => "article.pdf"
    case "summary_slides_pdf" => "slides.pdf"
    case "video" => "video.mp4"
  }

  private def _public_path(locale: String, role: String): String = s"/media/$locale/${_identity}/${_basename(role)}"

  private def _destination(fixture: WarehouseSiteFixture.Data, locale: String, role: String): Path =
    fixture.warehouse.resolve(_public_path(locale, role).drop(1))

  private def _production_path(fixture: WarehouseSiteFixture.Data, locale: String): Path = fixture.root.resolve(s"production/$locale.json")

  private def _watch_url(locale: String): String =
    if (locale == "ja") "https://youtu.be/ja_Example-1" else "https://www.youtube.com/watch?v=en_Example-2"

  private def _production(locale: String): JsObject =
    Json.obj("category" -> "modeling", "article" -> "example", "language" -> locale,
      "render" -> Json.obj("status" -> "completed", "qa" -> Json.obj("status" -> "technical-and-visual-qa-passed")),
      "youtube" -> Json.obj("status" -> "published", "videoUrl" -> _watch_url(locale)))

  private def _config(fixture: WarehouseSiteFixture.Data, target: Option[String] = None, dryrun: Boolean = false): CozyArticleMediaSiteCommand.Config =
    CozyArticleMediaSiteCommand.Config(fixture.descriptor, fixture.registry, target, dryrun, warehouseProfile = Some("warehouse"))

  private def _args(fixture: WarehouseSiteFixture.Data): List[String] =
    List("media", "register-site", fixture.descriptor.toString, "--publication", fixture.registry.toString, "--warehouse-profile", "warehouse")

  private def _strict(fixture: WarehouseSiteFixture.Data): JsValue =
    CozyArticleMediaRegistry.readCurrentSite(fixture.registry).entries.find(_.path == s"metadata/article-media/${_identity}.json").get.metadata

  private def _pdf_publication(publicpath: String): CozyArticleMediaPublication.Result =
    CozyArticleMediaPublication.produce(_identity, Vector(CozyArticleMediaPublication.Variant("ja",
      articlePdf = Some(PdfDocumentReference(new URI(publicpath), "application/pdf", None)))))

  private def _integrity(role: CozyArticleMediaIntegrity.Role): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = _identity, locale = "ja", role = role,
      artifact = Some(CozyArticleMediaIntegrity.Artifact("example", "1.0.0")),
      publicPath = new URI(if (role == CozyArticleMediaIntegrity.Role.Video) "/repository/old.mp4" else "/repository/old.png"),
      repositoryPath = if (role == CozyArticleMediaIntegrity.Role.Video) "old.mp4" else "old.png",
      mediaType = if (role == CozyArticleMediaIntegrity.Role.Video) "video/mp4" else "image/png",
      provenance = if (role == CozyArticleMediaIntegrity.Role.Video)
        CozyArticleMediaIntegrity.VideoPublication("metadata/video/example/manifest.json", "metadata/artifacts/repository/example.json")
        else CozyArticleMediaIntegrity.MediaPackage("media.yaml", "example", "target/cozy-media/manifest.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))

  private def _entry(path: String, metadata: JsValue): JsObject =
    Json.obj("path" -> path, "key" -> path.stripPrefix("metadata/").stripSuffix(".json"), "metadata" -> metadata)

  private def _write_bundle(fixture: WarehouseSiteFixture.Data, name: String, entries: Vector[JsObject]): Unit =
    _write_json(fixture.registry.resolve(s"$name.json"), Json.obj("schema" -> "cozy.publish-project.v1", "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name, "customTitle" -> "Preserved owner title"), "sourceRepository" -> "existing-project",
      "sourcePath" -> "authored", "sourceCommit" -> "historical-commit", "custom" -> Json.obj("retained" -> true), "entries" -> JsArray(entries)))

  private def _descriptor_resources(fixture: WarehouseSiteFixture.Data, resources: Vector[JsObject]): Unit =
    _write_json(fixture.descriptor, _read_json(fixture.descriptor) ++ Json.obj("resources" -> JsArray(resources)))

  private def _set_production_declaration(fixture: WarehouseSiteFixture.Data, declaration: String): Unit =
    _descriptor_resources(fixture, _resources.map { resource =>
      if ((resource \ "id").as[String] == "video-ja")
        resource ++ Json.obj("articleMedia" -> ((resource \ "articleMedia").as[JsObject] ++ Json.obj("production" -> declaration)))
      else resource
    })

  private def _registry_files(fixture: WarehouseSiteFixture.Data): Vector[String] = {
    val stream = Files.list(fixture.registry)
    try stream.iterator().asScala.toVector.map(_.getFileName.toString).sorted finally stream.close()
  }

  private def _read_json(file: Path): JsObject = Json.parse(Files.readString(file, StandardCharsets.UTF_8)).as[JsObject]

  private def _write_json(file: Path, value: JsValue): Unit = _write(file, Json.prettyPrint(value))

  private def _write(file: Path, value: String): Unit = {
    Files.createDirectories(file.getParent)
    Files.writeString(file, value, StandardCharsets.UTF_8)
    ()
  }
}
