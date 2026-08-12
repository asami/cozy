package cozy.publication

import java.net.URI
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.JsObject

/*
 * @since   Aug.  4, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaIntegritySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaIntegrity" should {
    "serialize video-publication provenance" which {
      "emit the exact deterministic video integrity contract and entry path" in {
        Given("a canonical article video integrity input")
        When("the integrity record is produced")
        val result = CozyArticleMediaIntegrity.produce(_video_input())
        val metadata = result.metadata
        val provenance = (metadata \ "provenance").as[JsObject]

        Then("the fields and values use the fixed video contract order")
        metadata.fields.map(_._1) shouldBe Vector("schema", "articleIdentity", "locale", "role", "artifact", "publicPath", "repositoryPath", "mediaType", "sha256", "provenance", "publicationState")
        metadata.value("schema").as[String] shouldBe "cozy.article-media-integrity.v1"
        metadata.value("articleIdentity").as[String] shouldBe "development-process/example"
        metadata.value("locale").as[String] shouldBe "ja"
        metadata.value("role").as[String] shouldBe "video"
        val artifact = (metadata \ "artifact").as[JsObject]
        artifact.fields.map(_._1) shouldBe Vector("identity", "version")
        artifact.value("identity").as[String] shouldBe "example-video"
        artifact.value("version").as[String] shouldBe "1.0.0"
        metadata.value("publicPath").as[String] shouldBe "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"
        metadata.value("repositoryPath").as[String] shouldBe "video/example-video/1.0.0/example-video-1.0.0.mp4"
        metadata.value("mediaType").as[String] shouldBe "video/mp4"
        metadata.value("sha256").as[String] shouldBe "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        provenance.fields.map(_._1) shouldBe Vector("kind", "videoManifest", "repositoryRegistry")
        provenance.value("kind").as[String] shouldBe "video-publication"
        provenance.value("videoManifest").as[String] shouldBe "metadata/video/example-video/1.0.0/manifest.json"
        provenance.value("repositoryRegistry").as[String] shouldBe "metadata/artifacts/repository/example-video.json"
        metadata.value("publicationState").as[String] shouldBe "published"
        result.entryPath shouldBe "metadata/article-media-integrity/development-process/example/ja/video.json"
      }
    }

    "serialize media-package provenance" which {
      "emit the exact deterministic infographic integrity contract and entry path" in {
        Given("a canonical article infographic integrity input")
        When("the integrity record is produced")
        val result = CozyArticleMediaIntegrity.produce(_infographic_input())
        val metadata = result.metadata
        val provenance = (metadata \ "provenance").as[JsObject]

        Then("the fields and values use the fixed media package contract order")
        metadata.fields.map(_._1) shouldBe Vector("schema", "articleIdentity", "locale", "role", "artifact", "publicPath", "repositoryPath", "mediaType", "sha256", "provenance", "publicationState")
        metadata.value("schema").as[String] shouldBe "cozy.article-media-integrity.v1"
        metadata.value("articleIdentity").as[String] shouldBe "development-process/example"
        metadata.value("locale").as[String] shouldBe "ja"
        metadata.value("role").as[String] shouldBe "infographic"
        val artifact = (metadata \ "artifact").as[JsObject]
        artifact.fields.map(_._1) shouldBe Vector("identity", "version")
        artifact.value("identity").as[String] shouldBe "example-infographic"
        artifact.value("version").as[String] shouldBe "1.0.0"
        metadata.value("publicPath").as[String] shouldBe "/ja/development-process/images/example.png"
        metadata.value("repositoryPath").as[String] shouldBe "images/development-process/example.png"
        metadata.value("mediaType").as[String] shouldBe "image/png"
        metadata.value("sha256").as[String] shouldBe "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        provenance.fields.map(_._1) shouldBe Vector("kind", "descriptor", "resourceId", "buildManifest")
        provenance.value("kind").as[String] shouldBe "media-package"
        provenance.value("descriptor").as[String] shouldBe "src/main/media/example.yaml"
        provenance.value("resourceId").as[String] shouldBe "example-infographic"
        provenance.value("buildManifest").as[String] shouldBe "target/cozy-media/manifest.json"
        metadata.value("publicationState").as[String] shouldBe "registered"
        result.entryPath shouldBe "metadata/article-media-integrity/development-process/example/ja/infographic.json"
      }
    }

    "serialize Cozy publication states" which {
      "retain every fixed artifact admission state without SmartDox status semantics" in {
        Given("otherwise identical integrity inputs with each Cozy publication state")
        val states = Vector(
          CozyArticleMediaIntegrity.PublicationState.Registered,
          CozyArticleMediaIntegrity.PublicationState.Published,
          CozyArticleMediaIntegrity.PublicationState.Withdrawn
        )

        When("the integrity records are produced")
        val serialized = states.map { state =>
          CozyArticleMediaIntegrity.produce(_video_input(publicationstate = state)).metadata.value("publicationState").as[String]
        }

        Then("the serialized states remain Cozy-owned fixed values")
        serialized shouldBe Vector("registered", "published", "withdrawn")
      }
    }

    "share article normalization with strict publication" which {
      "normalize the same source-relative identity and canonical locale" in {
        Given("a backslash-delimited article identity accepted by the strict producer")
        val strict = CozyArticleMediaPublication.produce(
          "development-process\\example",
          Vector(CozyArticleMediaPublication.Variant(
            "en",
            video = Some(VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("https://example.com/watch")), None))
          ))
        )

        When("the integrity producer receives the same identity and locale")
        val integrity = CozyArticleMediaIntegrity.produce(_video_input(articleidentity = "development-process\\example", locale = "en"))

        Then("both record surfaces use the same normalized registry key values")
        strict.publication.articleIdentity shouldBe integrity.record.articleIdentity
        strict.publication.variants.map(_.locale) shouldBe Vector(integrity.record.locale)
      }
    }

    "reject invalid integrity inputs" which {
      "reject a null integrity input" in {
        Given("a null integrity input")
        val invalid = () => CozyArticleMediaIntegrity.produce(null)

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the input failure is deterministic")
        error.getMessage should include("input")
      }

      "reject a null role" in {
        Given("an integrity input without a role")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(role = null))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the role failure is deterministic")
        error.getMessage should include("role")
      }

      "reject a null publication state" in {
        Given("an integrity input without a publication state")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(publicationstate = null))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the publication state failure is deterministic")
        error.getMessage should include("publicationState")
      }

      "reject a null artifact" in {
        Given("an integrity input without an artifact")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(artifact = null))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the artifact failure is deterministic")
        error.getMessage should include("artifact")
      }

      "reject a null public path" in {
        Given("an integrity input without a public path")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(publicpath = null))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the public path failure is deterministic")
        error.getMessage should include("publicPath")
      }

      "reject a null provenance" in {
        Given("an integrity input without provenance")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(provenance = null))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the provenance failure is deterministic")
        error.getMessage should include("provenance")
      }

      "reject a blank artifact identity" in {
        Given("an artifact with a blank identity")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(artifact = CozyArticleMediaIntegrity.Artifact("  ", "1.0.0")))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the artifact identity failure is deterministic")
        error.getMessage should include("artifact identity")
      }

      "reject a non-lowercase or non-SHA-256 digest" in {
        Given("a malformed integrity digest")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(sha256 = "A" * 64))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the digest failure is deterministic")
        error.getMessage should include("sha256")
      }

      "reject a digest with the wrong length" in {
        Given("a digest that is not 64 hexadecimal characters")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(sha256 = "0" * 63))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the digest length failure is deterministic")
        error.getMessage should include("sha256")
      }

      "reject an empty artifact version" in {
        Given("an artifact with no version")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(artifact = CozyArticleMediaIntegrity.Artifact("example-video", "")))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the artifact version failure is deterministic")
        error.getMessage should include("artifact version")
      }

      "reject a blank artifact version" in {
        Given("an artifact with a blank version")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(artifact = CozyArticleMediaIntegrity.Artifact("example-video", "  ")))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the artifact version failure is deterministic")
        error.getMessage should include("artifact version")
      }

      "reject a video media type intended for an infographic" in {
        Given("an infographic input carrying the video media type")
        val invalid = () => CozyArticleMediaIntegrity.produce(_infographic_input(mediatype = "video/mp4"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the role-specific media type failure is deterministic")
        error.getMessage should include("mediaType")
      }

      "reject an unsupported media type" in {
        Given("a video input carrying an unsupported media type")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(mediatype = "video/webm"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the unsupported media type failure is deterministic")
        error.getMessage should include("mediaType")
      }

      "reject a whitespace-padded media type" in {
        Given("a video input with surrounding media type whitespace")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(mediatype = " video/mp4 "))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the media type failure is deterministic")
        error.getMessage should include("mediaType")
      }

      "reject an escaping repository path" in {
        Given("a repository path with a parent segment")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(repositorypath = "video/../example.mp4"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the path failure is deterministic")
        error.getMessage should include("repositoryPath")
      }

      "reject an absolute repository path" in {
        Given("a repository path beginning at the filesystem root")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(repositorypath = "/video/example.mp4"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the repository path failure is deterministic")
        error.getMessage should include("repositoryPath")
      }

      "reject a non-site-visible public path" in {
        Given("a public path with a lexical parent segment")
        val invalid = () => CozyArticleMediaIntegrity.produce(
          CozyArticleMediaIntegrity.Input(
            articleIdentity = "development-process/example",
            locale = "ja",
            role = CozyArticleMediaIntegrity.Role.Video,
            artifact = CozyArticleMediaIntegrity.Artifact("example-video", "1.0.0"),
            publicPath = new URI("/repository/video/../example.mp4"),
            repositoryPath = "video/example.mp4",
            mediaType = "video/mp4",
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example-video/1.0.0/manifest.json", "metadata/artifacts/repository/example-video.json"),
            publicationState = CozyArticleMediaIntegrity.PublicationState.Published
          )
        )

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the public path failure is deterministic")
        error.getMessage should include("publicPath")
      }

      "reject an absolute public URI" in {
        Given("a public path with an absolute URI")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(publicpath = new URI("https://example.com/video.mp4")))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the public path failure is deterministic")
        error.getMessage should include("publicPath")
      }

      "reject an invalid source-relative article identity" in {
        Given("an article identity with a leading slash")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(articleidentity = "/development-process/example"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the identity failure is deterministic")
        error.getMessage should include("article identity")
      }

      "reject a generated article identity suffix" in {
        Given("an article identity that includes a generated document suffix")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(articleidentity = "development-process/example.dox"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the identity failure is deterministic")
        error.getMessage should include("article identity")
      }

      "reject a noncanonical locale" in {
        Given("a lower-case regional locale")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(locale = "ja-jp"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the locale failure is deterministic")
        error.getMessage should include("locale")
      }

      "reject a blank locale" in {
        Given("an integrity input without a locale")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(locale = " "))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the locale failure is deterministic")
        error.getMessage should include("locale")
      }

      "reject an empty artifact identity" in {
        Given("an artifact with no identity")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(artifact = CozyArticleMediaIntegrity.Artifact("", "1.0.0")))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the artifact failure is deterministic")
        error.getMessage should include("artifact identity")
      }

      "reject an invalid video manifest relative path" in {
        Given("video provenance with an absolute manifest path")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(provenance = CozyArticleMediaIntegrity.VideoPublication("/metadata/video/manifest.json", "metadata/artifacts/repository/example-video.json")))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the video manifest failure is deterministic")
        error.getMessage should include("videoManifest")
      }

      "reject an invalid repository registry relative path" in {
        Given("video provenance with a parent registry path")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example-video/1.0.0/manifest.json", "metadata/../registry.json")))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the repository registry failure is deterministic")
        error.getMessage should include("repositoryRegistry")
      }

      "reject an invalid descriptor relative path" in {
        Given("infographic provenance with an absolute descriptor path")
        val invalid = () => CozyArticleMediaIntegrity.produce(_infographic_input(descriptor = "/src/main/media/example.yaml"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the descriptor failure is deterministic")
        error.getMessage should include("descriptor")
      }

      "reject an invalid build manifest relative path" in {
        Given("infographic provenance with a parent build manifest path")
        val invalid = () => CozyArticleMediaIntegrity.produce(_infographic_input(buildmanifest = "target/../manifest.json"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the build manifest failure is deterministic")
        error.getMessage should include("buildManifest")
      }

      "reject an empty resource id" in {
        Given("infographic provenance without a resource id")
        val invalid = () => CozyArticleMediaIntegrity.produce(_infographic_input(resourceid = ""))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the resource id failure is deterministic")
        error.getMessage should include("resourceId")
      }

      "reject a non-trimmed resource id" in {
        Given("infographic provenance with a padded resource id")
        val invalid = () => CozyArticleMediaIntegrity.produce(_infographic_input(resourceid = " example-infographic"))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the resource id failure is deterministic")
        error.getMessage should include("resourceId")
      }

      "reject provenance whose fixed role differs from the record role" in {
        Given("a video record paired with infographic provenance")
        val invalid = () => CozyArticleMediaIntegrity.produce(_video_input(provenance = CozyArticleMediaIntegrity.MediaPackage("src/main/media/example.yaml", "example-infographic", "target/cozy-media/manifest.json")))

        When("the record is normalized")
        val error = intercept[IllegalArgumentException](invalid())

        Then("the role failure is deterministic")
        error.getMessage should include("provenance role")
      }
    }

    "preserve generated normalized registry keys" which {
      "produce deterministic paths for generated valid source-relative identities" in {
        Given("generated valid identity segments")
        val segmentgenerator = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
        val property = Prop.forAll(segmentgenerator) { segment =>
          Given("one generated identity segment")
          val identity = s"development-process/$segment"

          When("the integrity producer constructs the entry")
          val result = CozyArticleMediaIntegrity.produce(_video_input(articleidentity = identity))

          Then("the entry key remains canonical and deterministic")
          result.record.articleIdentity shouldBe identity
          result.entryPath shouldBe s"metadata/article-media-integrity/$identity/ja/video.json"
          true
        }

        When("the producer checks each generated identity")
        val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every generated identity has a deterministic integrity key")
        propertyresult.passed shouldBe true
      }
    }

    "serialize WIP site-video provenance" which {
      "emit the additive canonical provenance fields in their fixed order" in {
        Given("a canonical WIP video integrity input")
        When("the record is produced")
        val result = CozyArticleMediaIntegrity.produce(_wip_video_input())
        val provenance = (result.metadata \ "provenance").as[JsObject]

        Then("only the WIP provenance contract is added")
        provenance.fields.map(_._1) shouldBe Vector("kind", "descriptor", "resourceId", "production")
        provenance.value("kind").as[String] shouldBe "wip-site-video"
        provenance.value("descriptor").as[String] shouldBe "media/article.yaml"
        provenance.value("resourceId").as[String] shouldBe "part-5-video-ja"
        provenance.value("production").as[String] shouldBe "video/ja/production.json"
        result.record.provenance shouldBe CozyArticleMediaIntegrity.WipSiteVideo("media/article.yaml", "part-5-video-ja", "video/ja/production.json")
      }

      "reject an infographic role or unsafe WIP provenance path" in {
        Given("a WIP provenance used for an infographic and one with a parent path")

        When("both records are normalized")
        val roleerror = intercept[IllegalArgumentException](CozyArticleMediaIntegrity.produce(
          _infographic_input().copy(provenance = CozyArticleMediaIntegrity.WipSiteVideo("media/article.yaml", "part-5-video-ja", "video/ja/production.json"))
        ))
        val patherror = intercept[IllegalArgumentException](CozyArticleMediaIntegrity.produce(
          _wip_video_input(provenance = CozyArticleMediaIntegrity.WipSiteVideo("media/article.yaml", "part-5-video-ja", "video/../production.json"))
        ))

        Then("the role and safe-relative path contracts fail closed")
        roleerror.getMessage should include("provenance role")
        patherror.getMessage should include("production")
      }
    }
  }

  private def _video_input(
    articleidentity: String = "development-process/example",
    locale: String = "ja",
    role: CozyArticleMediaIntegrity.Role = CozyArticleMediaIntegrity.Role.Video,
    artifact: CozyArticleMediaIntegrity.Artifact = CozyArticleMediaIntegrity.Artifact("example-video", "1.0.0"),
    publicpath: URI = new URI("/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"),
    repositorypath: String = "video/example-video/1.0.0/example-video-1.0.0.mp4",
    mediatype: String = "video/mp4",
    sha256: String = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    provenance: CozyArticleMediaIntegrity.Provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example-video/1.0.0/manifest.json", "metadata/artifacts/repository/example-video.json"),
    publicationstate: CozyArticleMediaIntegrity.PublicationState = CozyArticleMediaIntegrity.PublicationState.Published
  ): CozyArticleMediaIntegrity.Input =
    CozyArticleMediaIntegrity.Input(
      articleIdentity = articleidentity,
      locale = locale,
      role = role,
      artifact = artifact,
      publicPath = publicpath,
      repositoryPath = repositorypath,
      mediaType = mediatype,
      sha256 = sha256,
      provenance = provenance,
      publicationState = publicationstate
    )

  private def _infographic_input(
    descriptor: String = "src/main/media/example.yaml",
    resourceid: String = "example-infographic",
    buildmanifest: String = "target/cozy-media/manifest.json",
    mediatype: String = "image/png"
  ): CozyArticleMediaIntegrity.Input =
    CozyArticleMediaIntegrity.Input(
      articleIdentity = "development-process/example",
      locale = "ja",
      role = CozyArticleMediaIntegrity.Role.Infographic,
      artifact = CozyArticleMediaIntegrity.Artifact("example-infographic", "1.0.0"),
      publicPath = new URI("/ja/development-process/images/example.png"),
      repositoryPath = "images/development-process/example.png",
      mediaType = mediatype,
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.MediaPackage(descriptor, resourceid, buildmanifest),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Registered
    )

  private def _wip_video_input(
    provenance: CozyArticleMediaIntegrity.Provenance = CozyArticleMediaIntegrity.WipSiteVideo("media/article.yaml", "part-5-video-ja", "video/ja/production.json")
  ): CozyArticleMediaIntegrity.Input =
    _video_input(
      artifact = CozyArticleMediaIntegrity.Artifact("part-5-video-ja", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
      publicpath = new URI("/ja/development-process/videos/example.mp4"),
      repositorypath = "ja/development-process/videos/example.mp4",
      provenance = provenance
    )
}
