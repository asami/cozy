package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaAssociationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaAssociation" should {
    "correlate strict native media" which {
      "correlate exact infographic and site-hosted video keys in deterministic order" in {
        Given("one native exact-locale infographic and site-hosted video with matching Cozy records")
        val publication = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrities = Vector(_video_integrity("development-process/example", "ja", "/repository/video/example.mp4"), _infographic_integrity("development-process/example", "ja", "/ja/development-process/images/example.png"))

        When("the native metadata is associated with integrity results")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.associate(metadata, integrities))

        Then("the exact typed keys and their deterministic role order are retained")
        result.correlations.map(_.key) shouldBe Vector(
          CozyArticleMediaAssociation.Key("development-process/example", "ja", CozyArticleMediaIntegrity.Role.Infographic),
          CozyArticleMediaAssociation.Key("development-process/example", "ja", CozyArticleMediaIntegrity.Role.Video)
        )
        result.correlations.map(_.publicPath.toString) shouldBe Vector("/ja/development-process/images/example.png", "/repository/video/example.mp4")
      }

      "reject a native infographic without exact-key integrity" in {
        Given("a native infographic without an integrity result")
        val publication = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)

        When("association requires its exact integrity key")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector.empty)))

        Then("the missing integrity is rejected deterministically")
        error.getMessage should include("Missing article-media integrity")
      }

      "reject duplicate normalized integrity keys" in {
        Given("two supplied integrity results with the same normalized video key")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4")

        When("association normalizes integrity keys")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(integrity, integrity))))

        Then("the duplicate key is rejected before correlation")
        error.getMessage should include("Duplicate article-media integrity key")
      }

      "reject duplicate native article identities even with disjoint locales" in {
        Given("two native entries for one identity with different locale variants")
        val english = _publication("development-process/example", "en", Some(_image("/en/development-process/images/example.png")), None)
        val japanese = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)

        When("association inspects the native SmartDox publications")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(english, japanese))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector.empty)))

        Then("the one-entry-per-normalized-identity invariant is enforced")
        error.getMessage should include("Duplicate native article-media publication identity")
      }

      "reject an infographic path that differs from its integrity public path" in {
        Given("a native infographic and exact-key integrity with different public paths")
        val publication = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)
        val integrity = _infographic_integrity("development-process/example", "ja", "/ja/development-process/images/other.png")

        When("association compares the two public paths")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(integrity))))

        Then("the byte-identical path requirement is enforced")
        error.getMessage should include("public path does not match integrity")
      }

      "reject a site-hosted video path that differs from its integrity public path" in {
        Given("a native site-hosted video and exact-key integrity with different public paths")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/other.mp4")

        When("association compares the two public paths")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(integrity))))

        Then("the byte-identical path requirement is enforced")
        error.getMessage should include("public path does not match integrity")
      }
    }

    "enforce published site-hosted admission" which {
      "reject a published site-hosted video backed by registered integrity" in {
        Given("a published native site-hosted video with registered Cozy integrity")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4", CozyArticleMediaIntegrity.PublicationState.Registered)

        When("association checks published projection admission")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(integrity))))

        Then("registered integrity cannot support the published video")
        error.getMessage should include("requires published Cozy integrity")
      }

      "reject a published site-hosted video backed by withdrawn integrity" in {
        Given("a published native site-hosted video with withdrawn Cozy integrity")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4", CozyArticleMediaIntegrity.PublicationState.Withdrawn)

        When("association checks published projection admission")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(integrity))))

        Then("withdrawn integrity cannot support the published video")
        error.getMessage should include("requires published Cozy integrity")
      }

      "correlate a draft site-hosted video that still carries content URL" in {
        Given("a draft native site-hosted video with a path-bearing content URL")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Draft, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4", CozyArticleMediaIntegrity.PublicationState.Registered)

        When("association validates the path-bearing draft video")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(integrity)))

        Then("the exact video correlation remains required without a published-state gate")
        result.correlations.map(_.key.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Video)
      }

      "correlate a withdrawn site-hosted video that still carries content URL" in {
        Given("a withdrawn native site-hosted video with a path-bearing content URL")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Withdrawn, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4", CozyArticleMediaIntegrity.PublicationState.Withdrawn)

        When("association validates the path-bearing withdrawn video")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(integrity)))

        Then("the exact video correlation remains required without a published-state gate")
        result.correlations.map(_.key.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Video)
      }
    }

    "leave non-Cozy video forms uncorrelated" which {
      "accept a watch-only external-link video without integrity" in {
        Given("a published external-link video with only a watch URL")
        val publication = _publication("development-process/example", "ja", None, Some(VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("https://example.com/watch")), None)))

        When("association inspects native media")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector.empty))

        Then("no watch URL correlation is invented")
        result.correlations shouldBe empty
      }

      "accept a URL-less draft site-hosted video without integrity" in {
        Given("a draft site-hosted video with no content URL")
        val publication = _publication("development-process/example", "ja", None, Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Draft)))

        When("association inspects native media")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector.empty))

        Then("no correlation is required")
        result.correlations shouldBe empty
      }

      "accept a URL-less withdrawn site-hosted video without integrity" in {
        Given("a withdrawn site-hosted video with no content URL")
        val publication = _publication("development-process/example", "ja", None, Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Withdrawn)))

        When("association inspects native media")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector.empty))

        Then("no correlation is required")
        result.correlations shouldBe empty
      }
    }

    "delegate compatibility resolution" which {
      "let an exact native locale win without merging a legacy video" in {
        Given("an exact native infographic and a legacy video for the same identity")
        val publication = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)
        val legacy = _legacy_video("development-process/example", "/repository/video/legacy.mp4")

        When("the association resolves the exact locale through supplied metadata")
        val result = _with_metadata(Vector(publication), Vector(legacy))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(_infographic_integrity("development-process/example", "ja", "/ja/development-process/images/example.png"))))

        Then("the complete native variant wins and is not merged with legacy video")
        result.resolve("development-process/example", "ja").flatMap(_.infographic).map(_.publicPath.toString) shouldBe Some("/ja/development-process/images/example.png")
        result.resolve("development-process/example", "ja").flatMap(_.video) shouldBe empty
      }

      "not fall back from a missing native locale to another native locale" in {
        Given("a native Japanese-only article-media variant")
        val publication = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)

        When("the association resolves an absent English locale")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(_infographic_integrity("development-process/example", "ja", "/ja/development-process/images/example.png"))))

        Then("the supplied SmartDox resolver does not use a native cross-locale fallback")
        result.resolve("development-process/example", "en") shouldBe empty
      }

      "resolve a legacy-only VideoPublication without Cozy integrity" in {
        Given("only a legacy SmartDox VideoPublication compatibility entry")
        val legacy = _legacy_video("development-process/legacy", "/repository/video/legacy.mp4")

        When("the association delegates resolution without native correlation")
        val result = _with_metadata(Vector.empty, Vector(legacy))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector.empty))

        Then("the legacy compatibility video remains available without a Cozy record")
        result.resolve("development-process/legacy", "en").flatMap(_.video).flatMap(_.contentUrl).map(_.toString) shouldBe Some("/repository/video/legacy.mp4")
      }
    }

    "reject invalid association inputs" which {
      "reject null supplied metadata" in {
        Given("a null PublishMetadata value")
        val invalid = () => CozyArticleMediaAssociation.validate(null, Vector.empty)

        When("association starts validation")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the metadata boundary fails deterministically")
        error.getMessage should include("metadata")
      }

      "reject a null integrity result collection" in {
        Given("a valid native publication and a null integrity collection")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))

        When("association normalizes integrity results")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, null)))

        Then("the collection boundary fails deterministically")
        error.getMessage should include("integrity results")
      }

      "reject a null integrity result element" in {
        Given("otherwise valid native metadata and a null integrity result")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))

        When("association normalizes integrity results")
        val error = intercept[IllegalArgumentException](_with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.validate(metadata, Vector(null))))

        Then("the element boundary fails deterministically")
        error.getMessage should include("integrity result")
      }
    }

    "preserve deterministic correlations" which {
      "keep correlation ordering stable for shuffled integrity inputs" in {
        Given("three exact-key integrity results in every generated input order")
        val publications = Vector(
          _publication("development-process/alpha", "en", Some(_image("/en/development-process/images/alpha.png")), None),
          _publication("development-process/beta", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/beta.mp4"))),
          _publication("development-process/gamma", "en", Some(_image("/en/development-process/images/gamma.png")), None)
        )
        val integrities = Vector(
          _infographic_integrity("development-process/alpha", "en", "/en/development-process/images/alpha.png"),
          _video_integrity("development-process/beta", "ja", "/repository/video/beta.mp4"),
          _infographic_integrity("development-process/gamma", "en", "/en/development-process/images/gamma.png")
        )
        val ordergenerator = Gen.oneOf(integrities.permutations.toVector)
        val property = Prop.forAll(ordergenerator) { shuffled =>
          _with_metadata(publications) { metadata =>
            CozyArticleMediaAssociation.validate(metadata, shuffled).correlations.map(_.key) == Vector(
              CozyArticleMediaAssociation.Key("development-process/alpha", "en", CozyArticleMediaIntegrity.Role.Infographic),
              CozyArticleMediaAssociation.Key("development-process/beta", "ja", CozyArticleMediaIntegrity.Role.Video),
              CozyArticleMediaAssociation.Key("development-process/gamma", "en", CozyArticleMediaIntegrity.Role.Infographic)
            )
          }
        }

        When("ScalaCheck validates shuffled integrity inputs")
        val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("all generated orderings produce the same correlation view")
        propertyresult.passed shouldBe true
      }

      "retain orphan integrity results without correlating them" in {
        Given("one native infographic with its required integrity and one orphan video integrity")
        val publication = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)
        val required = _infographic_integrity("development-process/example", "ja", "/ja/development-process/images/example.png")
        val orphan = _video_integrity("development-process/orphan", "en", "/repository/video/orphan.mp4")

        When("association retains supplied integrity results alongside native correlation")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.associate(metadata, Vector(orphan, required)))

        Then("only native path-bearing media is correlated and the orphan integrity is retained")
        result.correlations.map(_.key) shouldBe Vector(
          CozyArticleMediaAssociation.Key("development-process/example", "ja", CozyArticleMediaIntegrity.Role.Infographic)
        )
        result.integrityResults.map(x => (x.record.articleIdentity, x.record.locale, x.record.role.name)) shouldBe Vector(
          ("development-process/example", "ja", "infographic"),
          ("development-process/orphan", "en", "video")
        )
      }

      "order returned integrity results by normalized key" in {
        Given("multiple integrity results supplied in noncanonical order, including both roles for one key")
        val publication = _publication(
          "development-process/alpha",
          "en",
          Some(_image("/en/development-process/images/alpha.png")),
          Some(_site_video(VideoStatus.Published, "/repository/video/alpha.mp4"))
        )
        val alphavideo = _video_integrity("development-process/alpha", "en", "/repository/video/alpha.mp4")
        val alphainfographic = _infographic_integrity("development-process/alpha", "en", "/en/development-process/images/alpha.png")
        val zetainfographic = _infographic_integrity("development-process/zeta", "en", "/en/development-process/images/zeta.png")

        When("association returns normalized integrity results")
        val result = _with_metadata(Vector(publication))(metadata => CozyArticleMediaAssociation.associate(metadata, Vector(zetainfographic, alphavideo, alphainfographic)))

        Then("integrity results are sorted by article identity, locale, and role name")
        result.integrityResults.map(x => (x.record.articleIdentity, x.record.locale, x.record.role.name)) shouldBe Vector(
          ("development-process/alpha", "en", "infographic"),
          ("development-process/alpha", "en", "video"),
          ("development-process/zeta", "en", "infographic")
        )
      }

      "keep correlation keys stable across native publication entry permutations" in {
        Given("three distinct native publications with matching exact-key integrities")
        val publications = Vector(
          _publication("development-process/alpha", "en", Some(_image("/en/development-process/images/alpha.png")), None),
          _publication("development-process/beta", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/beta.mp4"))),
          _publication("development-process/gamma", "en", Some(_image("/en/development-process/images/gamma.png")), None)
        )
        val integrities = Vector(
          _infographic_integrity("development-process/alpha", "en", "/en/development-process/images/alpha.png"),
          _video_integrity("development-process/beta", "ja", "/repository/video/beta.mp4"),
          _infographic_integrity("development-process/gamma", "en", "/en/development-process/images/gamma.png")
        )
        val expected = Vector(
          CozyArticleMediaAssociation.Key("development-process/alpha", "en", CozyArticleMediaIntegrity.Role.Infographic),
          CozyArticleMediaAssociation.Key("development-process/beta", "ja", CozyArticleMediaIntegrity.Role.Video),
          CozyArticleMediaAssociation.Key("development-process/gamma", "en", CozyArticleMediaIntegrity.Role.Infographic)
        )

        When("association processes every native publication entry ordering")
        val observed = publications.permutations.map { permutation =>
          _with_metadata(permutation) { metadata =>
            CozyArticleMediaAssociation.associate(metadata, integrities).correlations.map(_.key)
          }
        }.toVector

        Then("every native publication ordering produces the same sorted correlation keys")
        observed.distinct shouldBe Vector(expected)
      }
    }
  }

  private def _publication(
    articleidentity: String,
    locale: String,
    infographic: Option[ImageReference],
    video: Option[VideoReference]
  ): CozyArticleMediaPublication.Result =
    CozyArticleMediaPublication.produce(articleidentity, Vector(CozyArticleMediaPublication.Variant(locale, infographic, video)))

  private def _image(publicpath: String): ImageReference =
    ImageReference(new URI(publicpath), Some("image/png"), Some("Example infographic"))

  private def _site_video(status: VideoStatus, publicpath: String): VideoReference =
    VideoReference(VideoPresentation.SiteHosted, status, None, None, Some(new URI(publicpath)))

  private def _video_integrity(
    articleidentity: String,
    locale: String,
    publicpath: String,
    publicationstate: CozyArticleMediaIntegrity.PublicationState = CozyArticleMediaIntegrity.PublicationState.Published
  ): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = articleidentity,
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Video,
      artifact = CozyArticleMediaIntegrity.Artifact("example-video", "1.0.0"),
      publicPath = new URI(publicpath),
      repositoryPath = "video/example.mp4",
      mediaType = "video/mp4",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example/1.0.0/manifest.json", "metadata/artifacts/repository/example.json"),
      publicationState = publicationstate
    ))

  private def _infographic_integrity(
    articleidentity: String,
    locale: String,
    publicpath: String
  ): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = articleidentity,
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Infographic,
      artifact = CozyArticleMediaIntegrity.Artifact("example-infographic", "1.0.0"),
      publicPath = new URI(publicpath),
      repositoryPath = "images/example.png",
      mediaType = "image/png",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.MediaPackage("src/main/media/example.yaml", "example-infographic", "target/cozy-media/manifest.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))

  private def _legacy_video(articleidentity: String, publicpath: String): (String, JsObject) =
    s"metadata/video/${articleidentity.replace('/', '-')}.json" -> Json.obj(
      "type" -> "video-publication",
      "video" -> Json.obj(
        "name" -> "legacy-video",
        "version" -> "1.0.0",
        "articlePath" -> s"$articleidentity.dox",
        "artifact" -> Json.obj("publicPath" -> publicpath)
      )
    )

  private def _with_metadata[A](
    publications: Vector[CozyArticleMediaPublication.Result],
    extraentries: Vector[(String, JsObject)] = Vector.empty
  )(f: PublishMetadata => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target").resolve("cozy-article-media-association").resolve("work")
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, "bundle-")
    try {
      val entries = publications.map(x => Json.obj("path" -> x.entryPath, "metadata" -> x.metadata)) ++ extraentries.map {
        case (path, metadata) => Json.obj("path" -> path, "metadata" -> metadata)
      }
      val bundle = Json.obj("type" -> "publication-bundle", "entries" -> entries)
      Files.write(directory.resolve("publication.json"), Json.stringify(bundle).getBytes(StandardCharsets.UTF_8))
      f(PublishMetadata.load(directory.toFile).get)
    } finally {
      _delete(directory)
    }
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
