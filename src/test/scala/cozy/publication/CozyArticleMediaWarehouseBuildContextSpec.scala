package cozy.publication

import java.io.{ByteArrayOutputStream, IOException}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.nio.file.attribute.PosixFilePermissions
import scala.collection.JavaConverters._
import cozy.media.{CozyMedia, CozyMediaWarehouse}
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.realm.Realm
import org.goldenport.realm.Realm.StringData
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.doxsite.DoxSite
import org.smartdox.generator.{Config => SmartDoxConfig, Context => SmartDoxContext}
import org.smartdox.generators.DoxSiteGenerator
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.{ImageReference, PdfDocumentReference, VideoPresentation, VideoReference, VideoStatus}
import org.yaml.snakeyaml.Yaml
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

/*
 * @since   Oct.  7, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaWarehouseBuildContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyArticleMediaWarehouseBuildContextSpec.Fixture
  private val _identity = "development-process/example"
  private val _locales = Vector("ja", "en")
  private val _roles = Vector("infographic", "article_pdf", "summary_slides_pdf")

  "Canonical warehouse article-media build context" should {
    "P751-02B-01 project available bilingual package media and ordinary pages through the actual generator" in {
      Given("a real JA/EN Document Project, ordinary control, placed images/PDFs and declared published YouTube URLs without MP4s")
      _with_fixture { fixture =>
        val original = _read(fixture.publication.resolve("owner.json"))
        When("production consumes the fresh effective publication with the normal DoxSiteGenerator")
        val context = _context(fixture, "production")
        val site = _generate(fixture, context)
        Then("article HTML and global/category Notice YAML retain exact localized media and labels")
        _locales.foreach { locale =>
          val html = _string(site, s"doxsite.d/$locale/${_identity}.html")
          _roles.foreach(role => html should include(_path(locale, role)))
          html should include(_watch(locale))
          _header_actions(html) shouldBe Vector(
            _watch(locale) -> (if (locale == "ja") "動画を見る" else "Watch video"),
            _path(locale, "summary_slides_pdf") -> (if (locale == "ja") "要約スライド PDF" else "Summary slides PDF"),
            _path(locale, "article_pdf") -> (if (locale == "ja") "記事 PDF" else "Article PDF"),
            "#smartdox-article-infographic" -> (if (locale == "ja") "インフォグラフィックを見る" else "View infographic"))
          val global = _notice(site, s"doxsite.d/WEB-INF/data/$locale", s"${_identity}.html")
          val category = _notice(site, s"doxsite.d/WEB-INF/data/$locale/development-process", s"${_identity}.html")
          global("notice.media") shouldBe category("notice.media")
          val media = global("notice.media").asInstanceOf[Map[String, Any]]
          _roles.foreach { role =>
            media(role).asInstanceOf[Map[String, Any]]("public_path") shouldBe _path(locale, role)
          }
          media("article_pdf").asInstanceOf[Map[String, Any]]("label") shouldBe s"Article $locale"
          media("summary_slides_pdf").asInstanceOf[Map[String, Any]]("label") shouldBe s"Slides $locale"
          media("video").asInstanceOf[Map[String, Any]]("watch_url") shouldBe _watch(locale)
          _string(site, s"doxsite.d/$locale/development-process/plain.html") should include("Ordinary control content")
          _notice(site, s"doxsite.d/WEB-INF/data/$locale", "development-process/plain.html").get("notice.media") shouldBe None
        }
        _read(fixture.publication.resolve("owner.json")) shouldBe original
        Files.exists(fixture.warehouse.resolve("media/ja/" + _identity + "/video.mp4")) shouldBe false
        context.omittedKeys shouldBe empty
      }
    }

    "P751-02B-02 omit exactly one missing nonvideo role in either locale for preview and production" in {
      for (strategy <- Vector("preview", "production"); locale <- _locales; role <- _roles) {
        Given(s"a $strategy registry whose $locale $role canonical file is absent")
        _with_fixture { fixture =>
          Files.delete(fixture.warehouse.resolve(_path(locale, role).drop(1)))
          val original = _read(fixture.publication.resolve("owner.json"))
          val output = new ByteArrayOutputStream()
          When("the normal consumer leases the available effective publication")
          val context = Console.withErr(output) { _context(fixture, strategy) }
          val effective = _strict(context.publicationPath)
          val result = _materialize(fixture, strategy)
          Then("only the exact role disappears, the typed diagnostic is exact, and source and other media survive")
          (effective \ "variants" \ locale).as[JsObject].keys should not contain role
          _roles.filterNot(_ == role).foreach { retained =>
            (effective \ "variants" \ locale \ retained \ "public_path").as[String] shouldBe _path(locale, retained)
          }
          val other = _locales.find(_ != locale).get
          _roles.foreach(retained => (effective \ "variants" \ other \ retained \ "public_path").as[String] shouldBe _path(other, retained))
          (effective \ "variants" \ locale \ "video" \ "watch_url").as[String] shouldBe _watch(locale)
          result.diagnostics shouldBe Vector(CozyArticleMediaWarehouseBuildContext.Diagnostic(_identity, locale, role, _path(locale, role), "Unavailable"))
          output.toString("UTF-8") should include(s"${_identity} [$locale, $role] ${_path(locale, role)}")
          _read(fixture.publication.resolve("owner.json")) shouldBe original
        }
      }
    }

    "P751-02B-03 retain only published external video projection when all canonical artifacts are absent" in {
      for (status <- Vector(VideoStatus.Published, VideoStatus.Draft, VideoStatus.Withdrawn)) {
        Given(s"all six nonvideo files absent and declared external videos with status ${status.name}")
        _with_fixture { fixture =>
          for (locale <- _locales; role <- _roles) Files.delete(fixture.warehouse.resolve(_path(locale, role).drop(1)))
          _write_bundle(fixture, Vector(_entry(_publication(status = status))))
          When("effective publication and real article/Notice generation run")
          val context = _context(fixture, "production")
          val site = _generate(fixture, context)
          val result = _materialize(fixture, "production")
          Then("no image or PDF is fabricated, ordinary pages render, and native status controls video visibility")
          _locales.foreach { locale =>
            val html = _string(site, s"doxsite.d/$locale/${_identity}.html")
            _roles.foreach(role => html should not include _path(locale, role))
            if (status == VideoStatus.Published) html should include(_watch(locale))
            else html should not include _watch(locale)
            _string(site, s"doxsite.d/$locale/development-process/plain.html") should include("Ordinary control content")
            val notice = _notice(site, s"doxsite.d/WEB-INF/data/$locale", s"${_identity}.html")
            if (status == VideoStatus.Published) notice("notice.media").asInstanceOf[Map[String, Any]].keySet shouldBe Set("video")
            else notice.get("notice.media") shouldBe None
          }
          (_strict(context.publicationPath) \ "variants" \ "ja").as[JsObject].keys shouldBe Set("video")
          result.diagnostics.map(x => (x.articleIdentity, x.locale, x.role, x.publicPath)) shouldBe
            (for (locale <- _locales.sorted; role <- _roles.sorted) yield (_identity, locale, role, _path(locale, role)))
        }
      }
      Given("an image-only article whose two canonical locale artifacts are absent")
      _with_fixture { fixture =>
        val publication = CozyArticleMediaPublication.produce(_identity, _locales.map(locale =>
          CozyArticleMediaPublication.Variant(locale, infographic = Some(ImageReference(new URI(_path(locale, "infographic")), None, None)))))
        _write_bundle(fixture, Vector(_entry(publication), _generic_entry))
        _locales.foreach(locale => Files.delete(fixture.warehouse.resolve(_path(locale, "infographic").drop(1))))
        When("canonical availability removes the last recognized role of each variant")
        val context = _context(fixture, "production")
        val site = _generate(fixture, context)
        Then("the empty native article entry is removed while generic metadata and ordinary generated pages survive")
        CozyArticleMediaRegistry.readCurrentSite(context.publicationPath).entries.map(_.path) shouldBe Vector("metadata/generic.json")
        _locales.foreach(locale => _string(site, s"doxsite.d/$locale/development-process/plain.html") should include("Ordinary control content"))
      }
    }

    "P751-02B-04 reject literal malformed canonical coordinates and MIME before consumer or output" in {
      val paths = Vector(
        "/media/ja/development-process/wrong/infographic.svg", "/media/en/development-process/example/infographic.svg",
        "/media/ja/development-process/example/wrong.svg", "/media/ja/development-process/example/infographic.SVG",
        "/media/ja/development-process/./example/infographic.svg", "/media/ja/development-process/other/../example/infographic.svg",
        "/media/ja/development-process/%65xample/infographic.svg", "/media/ja/development-process/example/infographic.svg?x=1",
        "/media/ja/development-process/example/infographic.svg#image", "/media/ja/development-process/example/infographic.gif")
      for ((path, mimetype) <- paths.map(_ -> "image/svg+xml") :+ (_path("ja", "infographic") -> "image/png")) {
        Given(s"a raw invalid native image registry declaring literal $path with $mimetype")
        _with_fixture { fixture =>
          val valid = _publication()
          val metadata = valid.metadata.as[JsObject]
          val variants = (metadata \ "variants").as[JsObject]
          val ja = (variants \ "ja").as[JsObject]
          val infographic = (ja \ "infographic").as[JsObject]
          val malformed = metadata ++ Json.obj("variants" -> (variants ++ Json.obj("ja" -> (ja ++ Json.obj(
            "infographic" -> (infographic ++ Json.obj("public_path" -> path, "media_type" -> mimetype)))))))
          _write_bundle(fixture, Vector(_entry(valid.entryPath, malformed)))
          When("canonical dispatch attempts to validate it")
          val outcome = _failure(fixture)
          Then("the coordinate is never normalized or silently routed to legacy policy")
          outcome._1.getMessage should not be empty
          outcome._2 shouldBe false
          Files.exists(_contexts(fixture)) shouldBe false
        }
      }
    }

    "P751-02B-05 distinguish absent files from unsafe warehouse roots ancestors and leaves" in {
      for (kind <- Vector("missing-root", "symlink-root", "symlink-ancestor", "symlink-file", "directory-file", "file-ancestor", "unreadable")) {
        Given(s"a canonical input with $kind warehouse state")
        _with_fixture { fixture =>
          val media = fixture.warehouse.resolve(_path("ja", "infographic").drop(1))
          val permissions = Files.getPosixFilePermissions(media)
          val repository = kind match {
            case "missing-root" => fixture.root.resolve("absent")
            case "symlink-root" => Files.createSymbolicLink(fixture.root.resolve("alias"), fixture.warehouse)
            case "symlink-ancestor" =>
              val moved = Files.move(media.getParent, fixture.root.resolve("moved-article"))
              Files.createSymbolicLink(media.getParent, moved)
              fixture.warehouse
            case "symlink-file" =>
              Files.delete(media)
              Files.createSymbolicLink(media, fixture.warehouse.resolve(_path("en", "infographic").drop(1)))
              fixture.warehouse
            case "directory-file" => Files.delete(media); Files.createDirectory(media); fixture.warehouse
            case "file-ancestor" =>
              _delete(media.getParent)
              _write(media.getParent, "not a directory")
              fixture.warehouse
            case "unreadable" => Files.setPosixFilePermissions(media, PosixFilePermissions.fromString("---------")); fixture.warehouse
          }
          try {
            When("normal context dispatch validates the configured warehouse")
            val outcome = _failure(fixture, repository)
            Then("unsafe existing state fails before consumption and effective allocation")
            outcome._1.getMessage should not be empty
            outcome._2 shouldBe false
            Files.exists(_contexts(fixture)) shouldBe false
          } finally {
            if (kind == "unreadable") Files.setPosixFilePermissions(media, permissions)
          }
        }
      }
      Given("a configured root argument with a dot segment presented directly to the shared owner")
      _with_fixture { fixture =>
        When("the warehouse root contract admits exact lexical paths")
        val error = intercept[Exception] { CozyMediaWarehouse.requireWarehouseRoot(fixture.warehouse.toString + "/.") }
        Then("a noncanonical root is rejected")
        error.getMessage should include("normalized exact")
      }
    }

    "P751-02B-06 preserve admitted containers wrappers and generic data and reject every native unknown field" in {
      Given("a mixed directory with admitted owner extras, generic data, framework contract and optional native fields")
      _with_fixture { fixture =>
        val generic = _generic_entry
        _write_bundle(fixture, Vector(_entry(_publication()) ++ Json.obj("wrapperExtra" -> "keep"), generic))
        val framework = Json.obj("schemaVersion" -> "cncf.framework-publication.v1", "kind" -> "framework-publication-contract")
        _write_json(fixture.publication.resolve("cncf-framework-publication.json"), framework)
        Files.delete(fixture.warehouse.resolve(_path("ja", "summary_slides_pdf").drop(1)))
        val original = _read(fixture.publication.resolve("owner.json"))
        When("the effective semantic publication is materialized")
        val context = _context(fixture, "production")
        val bundle = _json(context.publicationPath.resolve("owner.json"))
        Then("only the unavailable role changes while admitted extras and optional fields survive and the exact framework is excluded")
        (bundle \ "custom").as[String] shouldBe "container"
        (bundle \ "sourceCommit").as[String] shouldBe "historical"
        (bundle \ "publication" \ "headerExtra").as[String] shouldBe "header"
        val entries = (bundle \ "entries").as[Vector[JsObject]]
        entries should contain(generic)
        (entries.head \ "wrapperExtra").as[String] shouldBe "keep"
        val strict = _strict(context.publicationPath)
        (strict \ "variants" \ "ja" \ "infographic" \ "alt").as[String] shouldBe "Image ja"
        (strict \ "variants" \ "ja" \ "article_pdf" \ "label").as[String] shouldBe "Article ja"
        (strict \ "variants" \ "ja" \ "video").as[JsObject] shouldBe (_publication().metadata \ "variants" \ "ja" \ "video").as[JsObject]
        Files.exists(context.publicationPath.resolve("cncf-framework-publication.json")) shouldBe false
        _json(fixture.publication.resolve("cncf-framework-publication.json")) shouldBe framework
        _read(fixture.publication.resolve("owner.json")) shouldBe original
      }
      for (location <- Vector("metadata", "article", "variant", "role", "duplicate-owner", "duplicate-native")) {
        Given(s"a canonical registry containing invalid $location input")
        _with_fixture { fixture =>
          val original = _publication().metadata
          val ja = (original \ "variants" \ "ja").as[JsObject]
          val changed = location match {
            case "metadata" => original ++ Json.obj("unknown" -> true)
            case "article" => original ++ Json.obj("article" -> ((original \ "article").as[JsObject] ++ Json.obj("unknown" -> true)))
            case "variant" => original ++ Json.obj("variants" -> ((original \ "variants").as[JsObject] ++ Json.obj("ja" -> (ja ++ Json.obj("unknown" -> true)))))
            case "role" => original ++ Json.obj("variants" -> ((original \ "variants").as[JsObject] ++ Json.obj("ja" -> (ja ++ Json.obj("infographic" -> ((ja \ "infographic").as[JsObject] ++ Json.obj("unknown" -> true)))))))
            case _ => original
          }
          val entry = _entry(_publication()) ++ Json.obj("metadata" -> changed)
          _write_bundle(fixture, if (location == "duplicate-native") Vector(entry, entry) else Vector(entry))
          if (location == "duplicate-owner") {
            val other = _json(fixture.publication.resolve("owner.json")) ++ Json.obj("publication" -> Json.obj("name" -> "other"))
            _write_json(fixture.publication.resolve("other.json"), other)
          }
          When("readCurrentSite acts as the mandatory admission gate")
          val outcome = _failure(fixture)
          Then("invalid native schema or ownership never reaches consumer or effective directory creation")
          outcome._1.getMessage should not be empty
          outcome._2 shouldBe false
          Files.exists(_contexts(fixture)) shouldBe false
        }
      }
    }

    "P751-02B-07 retain historical same-key integrity without making it a canonical dependency" in {
      Given("available canonical images and historical same-key infographic integrity with old path and absent repository bytes")
      _with_fixture { fixture =>
        val historical = _integrity("ja")
        val retained = _entry(historical.entryPath, historical.metadata)
        _write_bundle(fixture, Vector(_entry(_publication()), retained))
        val original = _read(fixture.publication.resolve("owner.json"))
        When("production evaluates only actual remaining legacy keys")
        val context = _context(fixture, "production")
        Then("canonical images survive and old integrity remains unchanged in effective and source metadata")
        (_strict(context.publicationPath) \ "variants" \ "ja" \ "infographic" \ "public_path").as[String] shouldBe _path("ja", "infographic")
        (_json(context.publicationPath.resolve("owner.json")) \ "entries").as[Vector[JsObject]] should contain(retained)
        _read(fixture.publication.resolve("owner.json")) shouldBe original
        context.omittedKeys shouldBe empty
      }
    }

    "P751-02B-08 preserve legacy image omissions video failures and excluded WIP integrity in mixed input" in {
      Given("a canonical JA image with a legacy EN image that has no integrity")
      _with_fixture { fixture =>
        _write_bundle(fixture, Vector(_entry(_publication(englishimage = "/en/legacy.svg"))))
        When("the unchanged preview policy evaluates the remaining legacy image")
        val context = _context(fixture, "preview")
        Then("only the legacy EN image is omitted and its legacy diagnostic remains typed")
        (_strict(context.publicationPath) \ "variants" \ "en").as[JsObject].keys should not contain "infographic"
        (_strict(context.publicationPath) \ "variants" \ "ja" \ "infographic" \ "public_path").as[String] shouldBe _path("ja", "infographic")
        context.omittedKeys shouldBe Vector(CozyArticleMediaAssociation.Key(_identity, "en", CozyArticleMediaIntegrity.Role.Infographic))
        context.diagnostics.map(_.kind) shouldBe Vector(CozyArticleMediaPolicy.DiagnosticKind.MissingMedia)
      }
      Given("a canonical image plus a required legacy site-hosted video without integrity")
      _with_fixture { fixture =>
        _write_bundle(fixture, Vector(_entry(_publication(sitevideo = true))))
        When("the remaining legacy video reaches policy association")
        val outcome = _failure(fixture)
        Then("the original required integrity failure occurs before consumer and output")
        outcome._1.getMessage should include("Missing article-media integrity")
        outcome._2 shouldBe false
        Files.exists(_contexts(fixture)) shouldBe false
      }
      Given("a mixed canonical image and site-hosted WIP video with its admitted integrity")
      _with_fixture { fixture =>
        val integrity = _integrity("ja", wip = true)
        _write_bundle(fixture, Vector(_entry(_publication(sitevideo = true)), _entry(integrity.entryPath, integrity.metadata)))
        val original = _read(fixture.publication.resolve("owner.json"))
        When("unchanged policy excludes WIP video integrity from the effective output")
        val context = _context(fixture, "production")
        Then("only the effective video/integrity disappear and the original registry is preserved")
        (_strict(context.publicationPath) \ "variants" \ "ja").as[JsObject].keys should not contain "video"
        (_json(context.publicationPath.resolve("owner.json")) \ "entries").as[Vector[JsObject]].map(x => (x \ "path").as[String]) should not contain integrity.entryPath
        _read(fixture.publication.resolve("owner.json")) shouldBe original
      }
    }

    "P751-02B-09 materialize ordinary contexts for pure legacy and media-free inputs" in {
      for (mediafree <- Vector(false, true)) {
        Given(s"a legacy-only registry with mediafree=$mediafree and an absent unused repository")
        _with_fixture { fixture =>
          val entries = if (mediafree) Vector(_generic_entry) else Vector(_entry(_publication(imagepath = "/ja/legacy.svg", englishimage = "/en/legacy.svg", canonicalpdfs = false)))
          _write_bundle(fixture, entries)
          When("a normal consumer receives the legacy context")
          val context = CozyArticleMediaBuildContext.withContext(fixture.root, fixture.publication, fixture.root.resolve("absent-repository"), "preview")(value => value)
          Then("the original snapshots layout is selected and unavailable legacy images retain policy behavior")
          context.publicationPath.getParent.getParent.getFileName.toString shouldBe "snapshots"
          Files.exists(_contexts(fixture)) shouldBe false
          if (mediafree) context.omittedKeys shouldBe empty else context.omittedKeys should have size 2
        }
      }
    }

    "P751-02B-10 allocate independent target contexts and retain source warehouse and prior contexts after failure" in {
      Given("a canonical registry and warehouse with a completed prior context")
      _with_fixture { fixture =>
        val original = _read(fixture.publication.resolve("owner.json"))
        val image = _read(fixture.warehouse.resolve(_path("ja", "infographic").drop(1)))
        val first = _context(fixture, "production")
        val prior = _read(first.publicationPath.resolve("owner.json"))
        var leased = 0
        When("another normal consumer observes its lease and a later consumer fails")
        val second = CozyArticleMediaBuildContext.withContext(fixture.root, fixture.publication, fixture.warehouse, "production") { context =>
          leased = CozyArticleMediaBuildContext.activeLeaseCount(context.publicationPath)
          Then("the leased context exposes the declared Japanese article media")
          PublishMetadata.load(context.publicationPath.toFile).get.resolveArticleMedia(_identity, "ja").isDefined shouldBe true
          context
        }
        When("a later consumer fails")
        val error = intercept[IllegalStateException] {
          CozyArticleMediaBuildContext.withContext(fixture.root, fixture.publication, fixture.warehouse, "production")(_ => throw new IllegalStateException("consumer failed"))
        }
        Then("each completed context is distinct and usable, lease releases, and all original and prior content survives")
        first.publicationPath should not be second.publicationPath
        second.publicationPath.getParent.getParent shouldBe _contexts(fixture)
        leased shouldBe 1
        CozyArticleMediaBuildContext.activeLeaseCount(second.publicationPath) shouldBe 0
        error.getMessage shouldBe "consumer failed"
        _read(first.publicationPath.resolve("owner.json")) shouldBe prior
        _read(fixture.publication.resolve("owner.json")) shouldBe original
        _read(fixture.warehouse.resolve(_path("ja", "infographic").drop(1))) shouldBe image
        Given("a target parent that cannot create a fresh materialization while prior contexts are outside that failed strategy")
        val blocked = fixture.root.resolve("target/cozy-bok/article-media/production-preview/warehouse-contexts")
        _write(blocked, "blocked parent")
        When("materialization fails at the target directory boundary")
        val failure = intercept[Exception] { _context(fixture, "preview") }
        Then("failure preserves originals and prior context without deleting unrelated target data")
        failure.getMessage should not be empty
        _read(blocked) shouldBe "blocked parent"
        _read(first.publicationPath.resolve("owner.json")) shouldBe prior
        _read(fixture.publication.resolve("owner.json")) shouldBe original
      }
    }

    "direct materialization failure" which {
      "P751-02B-10 propagate the actual second-write failure with partial semantic output" in {
        Given("a valid canonical owner and separate generic bundle, a readable warehouse, a completed production context and unrelated target data")
        _with_fixture { fixture =>
          val extra = _json(fixture.publication.resolve("owner.json")) ++ Json.obj(
            "publication" -> Json.obj("name" -> "extra"), "entries" -> Json.arr(_generic_entry))
          _write_json(fixture.publication.resolve("extra.json"), extra)
          val originals = Vector("owner.json", "extra.json").map(name => name -> _read(fixture.publication.resolve(name))).toMap
          val warehouse = (for (locale <- _locales; role <- _roles) yield {
            val path = fixture.warehouse.resolve(_path(locale, role).drop(1))
            path -> _read(path)
          }).toMap
          val prior = _context(fixture, "production")
          val priorbundles = originals.keys.map(name => name -> _read(prior.publicationPath.resolve(name))).toMap
          val sentinel = fixture.root.resolve("target/unrelated/sentinel.txt")
          _write(sentinel, "unrelated target data")
          val current = CozyArticleMediaRegistry.readCurrentSite(fixture.publication)
          val projection = CozyArticleMediaRegistry.canonicalProjection(CozyArticleMediaRegistry.Snapshot(current.entries))
          val failure = new IOException("deterministic second bundle write failure")
          var attempted = Vector.empty[Path]
          var work = Option.empty[Path]
          val expectedbundles = priorbundles.map { case (name, contents) => name -> Json.parse(contents).as[JsObject] }
          When("the same materializer physically writes its first bundle and the per-call writer throws on its second bundle")
          val error = intercept[IOException] {
            CozyArticleMediaWarehouseBuildContext.materializeWithWriter(fixture.root, fixture.publication, fixture.warehouse,
              CozyArticleMediaBuildContext.normalizeStrategy("production"), current, projection) { (path, bundle) =>
              attempted = attempted :+ path
              if (attempted.size == 1) {
                work = Some(path.getParent.getParent)
                Files.write(path, Json.stringify(bundle).getBytes(StandardCharsets.UTF_8))
              } else throw failure
            }
          }
          Then("the exact failure reaches the caller with the first semantic bundle still present, while prior, source, warehouse and unrelated contents survive")
          attempted.size shouldBe 2
          attempted.map(_.getFileName.toString).toSet shouldBe originals.keySet
          error should be theSameInstanceAs failure
          work.isDefined shouldBe true
          val failedwork = work.get
          failedwork.getParent shouldBe _contexts(fixture)
          failedwork should not be prior.publicationPath.getParent
          attempted.map(_.getParent.getParent).toSet shouldBe Set(failedwork)
          Files.isDirectory(failedwork, LinkOption.NOFOLLOW_LINKS) shouldBe true
          val firstwritten = attempted.head
          Files.isRegularFile(firstwritten, LinkOption.NOFOLLOW_LINKS) shouldBe true
          _json(firstwritten) shouldBe expectedbundles(firstwritten.getFileName.toString)
          priorbundles.foreach { case (name, contents) => _read(prior.publicationPath.resolve(name)) shouldBe contents }
          originals.foreach { case (name, contents) => _read(fixture.publication.resolve(name)) shouldBe contents }
          warehouse.foreach { case (path, contents) => _read(path) shouldBe contents }
          _read(sentinel) shouldBe "unrelated target data"
        }
      }
    }

    "P751-02B-11 share exact JA/EN coordinate ownership with actual warehouse planning" in {
      for (locale <- _locales; (role, extension, mimetype) <- Vector(
        ("article_pdf", "pdf", "application/pdf"), ("summary_slides_pdf", "pdf", "application/pdf"),
        ("infographic", "svg", "image/svg+xml"), ("infographic", "png", "image/png"),
        ("infographic", "jpg", "image/jpeg"), ("infographic", "jpeg", "image/jpeg"), ("infographic", "webp", "image/webp"))) {
        Given(s"a declared $locale $role resource with supported $extension source and matching MIME")
        _with_fixture { fixture =>
          val publicpath = CozyMediaWarehouse.canonicalPublicPath(_identity, locale, role, extension)
          val descriptor = fixture.root.resolve("media.json")
          if (extension == "svg" || extension == "pdf")
            _write(fixture.root.resolve(s"source.$extension"), if (extension == "svg") _svg else _pdf)
          _write_json(descriptor, Json.obj("schema" -> "cozy.media.v1", "knowledge" -> Json.obj("id" -> "example", "source" -> "missing.dox"),
            "articleMedia" -> Json.obj("articleIdentity" -> _identity, "publicationProfile" -> "warehouse"), "profiles" -> Json.obj("warehouse" -> Json.obj("root" -> fixture.warehouse.toString)),
            "resources" -> Json.arr(Json.obj("id" -> "selected", "kind" -> (if (role == "infographic") "infographic" else "document"),
              "language" -> locale, "build" -> "prebuilt", "output" -> s"source.$extension", "publications" -> Json.obj("warehouse" -> publicpath.drop(1)),
              "articleMedia" -> Json.obj("role" -> role, "publicPath" -> publicpath, "mediaType" -> mimetype)))))
          When("the unchanged deployment planner resolves the selected coordinate")
          val plan = CozyMediaWarehouse.planAtRoot(CozyMedia.CommandConfig(descriptor, profile = Some("warehouse")), fixture.warehouse, Map.empty)
          Then("shared public path and actual destination are exact and planning performs no placement")
          plan.resources.head.publicPath shouldBe publicpath
          plan.resources.head.relativePath shouldBe publicpath.drop(1)
          plan.resources.head.destination shouldBe fixture.warehouse.resolve(publicpath.drop(1))
        }
      }
    }
  }

  private def _with_fixture(consumer: Fixture => Unit): Unit = {
    val parent = Files.createDirectories(Path.of("target/cozy-article-media-warehouse-context-spec").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "fixture-")
    val fixture = Fixture(root, Files.createDirectory(root.resolve("publication")), Files.createDirectory(root.resolve("warehouse")), Files.createDirectory(root.resolve("source")))
    try {
      for (locale <- _locales; role <- _roles) _write(fixture.warehouse.resolve(_path(locale, role).drop(1)), if (role == "infographic") _svg else _pdf)
      _write_bundle(fixture, Vector(_entry(_publication())))
      _write(fixture.source.resolve("site.conf"), "site.output.locale_mode = \"multi_locale_subdirs\"\nsite.output.default_locale = \"ja\"\n")
      _write(fixture.source.resolve("development-process/category.yaml"), "name: development-process\ntitle: Development Process\ndescription:\n  en: Development process articles.\n  ja: 開発プロセスの記事です。\n")
      _write(fixture.source.resolve(s"${_identity}.dox/index.dox"), _dox("Example｜サンプル", "Document Project content"))
      _write(fixture.source.resolve("development-process/plain.dox"), _dox("Plain｜普通", "Ordinary control content"))
      consumer(fixture)
    } finally _delete(root)
  }

  private def _publication(
    imagepath: String = "/media/ja/development-process/example/infographic.svg",
    imagemime: String = "image/svg+xml",
    englishimage: String = "/media/en/development-process/example/infographic.svg",
    status: VideoStatus = VideoStatus.Published,
    sitevideo: Boolean = false,
    canonicalpdfs: Boolean = true
  ): CozyArticleMediaPublication.Result = CozyArticleMediaPublication.produce(_identity, _locales.map { locale =>
    val video = if (sitevideo && locale == "ja") VideoReference(VideoPresentation.SiteHosted, status, Some("cozy"), None, Some(new URI("/repository/video/old.mp4")))
      else VideoReference(VideoPresentation.ExternalLink, status, Some("youtube"), Some(new URI(_watch(locale))), None)
    CozyArticleMediaPublication.Variant(locale,
      Some(ImageReference(new URI(if (locale == "ja") imagepath else englishimage), Some(if (locale == "ja") imagemime else "image/svg+xml"), Some(s"Image $locale"))),
      Some(video),
      if (canonicalpdfs) Some(PdfDocumentReference(new URI(_path(locale, "article_pdf")), "application/pdf", Some(s"Article $locale"))) else None,
      if (canonicalpdfs) Some(PdfDocumentReference(new URI(_path(locale, "summary_slides_pdf")), "application/pdf", Some(s"Slides $locale"))) else None)
  })

  private def _integrity(locale: String, wip: Boolean = false): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(_identity, locale,
      if (wip) CozyArticleMediaIntegrity.Role.Video else CozyArticleMediaIntegrity.Role.Infographic,
      if (wip) None else Some(CozyArticleMediaIntegrity.Artifact("historical", "1.0.0")), new URI(if (wip) "/repository/video/old.mp4" else "/old/image.png"),
      if (wip) "video/old.mp4" else "old/image.png", if (wip) "video/mp4" else "image/png",
      if (wip) CozyArticleMediaIntegrity.WipSiteVideo("media.json", "video", "production.json") else CozyArticleMediaIntegrity.MediaPackage("media.json", "image", "target/manifest.json"),
      CozyArticleMediaIntegrity.PublicationState.Published))

  private def _entry(result: CozyArticleMediaPublication.Result): JsObject = _entry(result.entryPath, result.metadata)
  private def _entry(path: String, metadata: JsValue): JsObject =
    Json.obj("path" -> path, "key" -> path.stripPrefix("metadata/").stripSuffix(".json"), "metadata" -> metadata)
  private def _generic_entry: JsObject = _entry("metadata/generic.json", Json.obj("retained" -> Json.obj("unknown" -> true)))
  private def _write_bundle(fixture: Fixture, entries: Vector[JsObject]): Unit =
    _write_json(fixture.publication.resolve("owner.json"), Json.obj("schema" -> "cozy.publish-project.v1", "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> "owner", "headerExtra" -> "header"), "sourceCommit" -> "historical", "custom" -> "container", "entries" -> JsArray(entries)))
  private def _context(fixture: Fixture, strategy: String): CozyArticleMediaBuildContext.Context =
    CozyArticleMediaBuildContext.withContext(fixture.root, fixture.publication, fixture.warehouse, strategy)(value => value)
  private def _materialize(fixture: Fixture, strategy: String): CozyArticleMediaWarehouseBuildContext.Result = {
    val current = CozyArticleMediaRegistry.readCurrentSite(fixture.publication)
    val projection = CozyArticleMediaRegistry.canonicalProjection(CozyArticleMediaRegistry.Snapshot(current.entries))
    CozyArticleMediaWarehouseBuildContext.materialize(fixture.root, fixture.publication, fixture.warehouse, CozyArticleMediaBuildContext.normalizeStrategy(strategy), current, projection)
  }
  private def _failure(fixture: Fixture): (Exception, Boolean) = _failure(fixture, fixture.warehouse)
  private def _failure(fixture: Fixture, repository: Path): (Exception, Boolean) = {
    var consumed = false
    val error = intercept[Exception] { CozyArticleMediaBuildContext.withContext(fixture.root, fixture.publication, repository, "production")(_ => consumed = true) }
    error -> consumed
  }
  private def _strict(publication: Path): JsObject =
    CozyArticleMediaRegistry.readCurrentSite(publication).entries.find(_.path == s"metadata/article-media/${_identity}.json").get.metadata.as[JsObject]
  private def _contexts(fixture: Fixture): Path = fixture.root.resolve("target/cozy-bok/article-media/production/warehouse-contexts")
  private def _path(locale: String, role: String): String = CozyMediaWarehouse.canonicalPublicPath(_identity, locale, role, if (role == "infographic") "svg" else "pdf")
  private def _watch(locale: String): String = s"https://www.youtube.com/watch?v=${locale}_Example-1"
  private def _dox(title: String, content: String): String = s"$title\n========================\n\n# HEAD\n\nstatus=published\npublished_at=2026-10-07\n\n# Body\n\n$content\n"
  private def _svg: String = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"><rect width=\"1\" height=\"1\"/></svg>"
  private def _pdf: String = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"
  private def _generate(fixture: Fixture, context: CozyArticleMediaBuildContext.Context): Realm = {
    val environment = Environment.createJaJp()
    val smartdox = new SmartDoxContext(environment, SmartDoxConfig(CliConfig.buildJaJp()), environment.contextFoundation)
    new DoxSiteGenerator(smartdox, DoxSite.Config.default, Some(context.publicationPath.toFile), Some(fixture.warehouse.toFile), "fail")
      .generate(Realm.create(DoxSite.realmConfig, fixture.source.toFile))
  }
  private def _string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: StringData => value.string }.getOrElse(fail(s"Missing generated content: $path"))
  private def _header_actions(html: String): Vector[(String, String)] =
    """(?s)<a\b(?=[^>]*\bclass="smartdox-article-header-action")(?=[^>]*\bhref="([^"]*)")[^>]*>([^<]*)</a>""".r.
      findAllMatchIn(html).
      map(m => m.group(1) -> m.group(2)).
      toVector
  private def _notice(realm: Realm, directory: String, uri: String): Map[String, Any] =
    (1 to 99).flatMap { index =>
      realm.get(f"$directory/notice$index%02d.yaml").collect { case value: StringData => _yaml(new Yaml().load[Any](value.string)).asInstanceOf[Map[String, Any]] }
    }.find(_.get("notice.uri").contains(uri)).getOrElse(fail(s"Missing Notice: $directory $uri"))
  private def _yaml(value: Any): Any = value match {
    case map: java.util.Map[_, _] => map.asScala.map { case (key, item) => key.toString -> _yaml(item) }.toMap
    case list: java.util.List[_] => list.asScala.map(_yaml).toVector
    case other => other
  }
  private def _json(path: Path): JsObject = Json.parse(_read(path)).as[JsObject]
  private def _read(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)
  private def _write_json(path: Path, value: JsValue): Unit = _write(path, Json.stringify(value))
  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    ()
  }
  private def _delete(root: Path): Unit = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
    finally stream.close()
  }
}

private object CozyArticleMediaWarehouseBuildContextSpec {
  final case class Fixture(root: Path, publication: Path, warehouse: Path, source: Path)
}
