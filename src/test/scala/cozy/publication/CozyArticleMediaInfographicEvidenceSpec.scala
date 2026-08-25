package cozy.publication

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import javax.imageio.ImageIO
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cozy.media.CozyMedia

/*
 * @since   Aug.  5, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaInfographicEvidenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaInfographicEvidence" should {
    "project one exact explicit infographic evidence record" which {
      "preserve canonical integrity fields and project-relative provenance" in {
        Given("an explicit descriptor, its exact build manifest, and a published PNG under the configured root")
        _with_fixture { fixture =>
          When("the explicit infographic evidence is projected")
          val result = CozyArticleMediaInfographicEvidence.project(_input(fixture))
          val record = result.integrity.record

          Then("the integrity record is the selected image/png artifact and contains no machine-absolute provenance")
          val decoded = ImageIO.read(fixture.destination.toFile)
          decoded should not be null
          decoded.getWidth shouldBe 1
          decoded.getHeight shouldBe 1
          record.articleIdentity shouldBe "development-process/example"
          record.locale shouldBe "ja"
          record.role shouldBe CozyArticleMediaIntegrity.Role.Infographic
          record.artifact shouldBe CozyArticleMediaIntegrity.Artifact("example-infographic-ja", "1.0.0")
          record.publicPath.toString shouldBe "/images/development-process/example-ja.png"
          record.repositoryPath shouldBe "images/development-process/example-ja.png"
          record.mediaType shouldBe "image/png"
          record.sha256 shouldBe fixture.sha256
          record.provenance shouldBe CozyArticleMediaIntegrity.MediaPackage(
            "project/media.yaml",
            "example-infographic-ja",
            "project/target/cozy-media/manifest.json"
          )
          record.publicationState shouldBe CozyArticleMediaIntegrity.PublicationState.Published
          result.integrity.entryPath shouldBe "metadata/article-media-integrity/development-process/example/ja/infographic.json"
          result.artifactPath shouldBe fixture.destination.toRealPath()
          record.provenance.asInstanceOf[CozyArticleMediaIntegrity.MediaPackage].descriptor should not include (fixture.root.toString)
          record.provenance.asInstanceOf[CozyArticleMediaIntegrity.MediaPackage].buildManifest should not include (fixture.root.toString)
        }
      }

      "accept the descriptor root when the selected profile omits root and rootEnv" in {
        Given("a descriptor whose selected profile defaults to its descriptor root")
        _with_fixture { fixture =>
          _write(fixture.descriptor, _descriptor(profiledefinition = "{}"))
          val destination = fixture.project.resolve("images/development-process/example-ja.png")
          _write_bytes(destination, _png_bytes)
          CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))

          When("the configured publication and repository roots equal the descriptor root")
          val result = CozyArticleMediaInfographicEvidence.project(_input(
            fixture,
            publicationroot = fixture.project,
            destination = destination,
            repositoryroot = fixture.project
          ))

          Then("the default profile root provides the declared publication destination")
          result.artifactPath shouldBe destination.toRealPath()
        }
      }

      "accept the selected prebuilt image source as the exact manifest path" in {
        Given("a prebuilt resource whose source is the selected manifest path")
        _with_fixture { fixture =>
          _write(fixture.descriptor, _descriptor(source = "target/cozy-media/example-ja.png", build = "prebuilt"))
          Files.delete(fixture.manifest)
          CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))

          When("the exact prebuilt image evidence is projected")
          val result = CozyArticleMediaInfographicEvidence.project(_input(fixture))

          Then("the manifest attribution uses resource.source without an output path")
          result.integrity.record.sha256 shouldBe fixture.sha256
        }
      }

      "reject a v2 receipt after its accepted input changes" in {
        Given("a valid v2 build manifest whose knowledge source later changes")
        _with_fixture { fixture =>
          _write(fixture.project.resolve("knowledge/article.dox"), "changed knowledge")

          When("article-media projection reads the stale manifest")
          val stale = intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))

          Then("downstream evidence admission rejects the stale receipt before projection")
          stale.getMessage should include("receipt is not current")
        }
      }

      "accept a valid rootEnv whose declared destination remains in the fixture target" in {
        Given("PWD equals the normalized current user.dir and contains the created fixture artifact")
        _with_fixture { fixture =>
          val userdir = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
          val pwd = sys.env.get("PWD").map(Paths.get(_).toAbsolutePath.normalize()).getOrElse(
            cancel("PWD is unavailable for this rootEnv specification")
          )
          if (pwd != userdir)
            cancel("PWD does not equal normalized user.dir for this rootEnv specification")
          val destination = userdir.relativize(fixture.destination.toAbsolutePath.normalize()).toString.replace('\\', '/')
          _write(fixture.descriptor, _descriptor(destination = destination, profiledefinition = "{\"rootEnv\": \"PWD\"}"))
          CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))

          When("the PWD rootEnv and explicit configured roots are projected")
          val result = CozyArticleMediaInfographicEvidence.project(_input(
            fixture,
            publicationroot = pwd,
            repositoryroot = pwd
          ))

          Then("the rootEnv profile accepts only the explicit fixture artifact beneath that root")
          result.artifactPath shouldBe fixture.destination.toRealPath()
        }
      }

      "reject a lexical profile-root alias even when its canonical real directory matches the repository" in {
        Given("a descriptor and typed publication destination declared through a repository symlink alias")
        _with_fixture { fixture =>
          val alias = fixture.root.resolve("repository-alias")
          try Files.createSymbolicLink(alias, fixture.repository)
          catch {
            case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
          }
          _write(fixture.descriptor, _descriptor(profiledefinition = "{\"root\": \"../repository-alias\"}"))
          val aliasdestination = alias.resolve("images/development-process/example-ja.png")

          When("the profile and publication roots use the alias while the configured repository uses its real path")
          val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(
            fixture,
            publicationroot = alias,
            destination = aliasdestination,
            repositoryroot = fixture.repository
          )))

          Then("both lexical and canonical real root identities are required to match")
          error.getMessage should include("identical")
        }
      }

      "map every typed publication outcome without SmartDox input" in {
        Given("the three admitted publication outcomes with otherwise identical explicit evidence")
        val outcomes = Vector(
          CozyArticleMediaInfographicEvidence.PublicationOutcome.Registered -> CozyArticleMediaIntegrity.PublicationState.Registered,
          CozyArticleMediaInfographicEvidence.PublicationOutcome.Published -> CozyArticleMediaIntegrity.PublicationState.Published,
          CozyArticleMediaInfographicEvidence.PublicationOutcome.Withdrawn -> CozyArticleMediaIntegrity.PublicationState.Withdrawn
        )

        When("each typed outcome is projected")
        val states = outcomes.map { case (outcome, expected) =>
          _with_fixture { fixture =>
            CozyArticleMediaInfographicEvidence.project(_input(fixture, outcome = outcome)).integrity.record.publicationState -> expected
          }
        }

        Then("Cozy admission state maps one-to-one independently of SmartDox status")
        states.foreach { case (actual, expected) => actual shouldBe expected }
      }

      "retain outcome mapping under generated input orderings" in {
        Given("generated permutations of the three typed outcomes and reset fixtures")
        val outcomes = Vector(
          CozyArticleMediaInfographicEvidence.PublicationOutcome.Registered,
          CozyArticleMediaInfographicEvidence.PublicationOutcome.Published,
          CozyArticleMediaInfographicEvidence.PublicationOutcome.Withdrawn
        )
        val property = Prop.forAll(Gen.someOf(outcomes).map(_.toVector)) { selected =>
          selected.forall { outcome =>
            _with_fixture { fixture =>
              CozyArticleMediaInfographicEvidence.project(_input(fixture, outcome = outcome)).integrity.record.publicationState.name == _state_name(outcome)
            }
          }
        }

        When("ScalaCheck evaluates independent explicit evidence fixtures")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

        Then("each selected typed outcome remains deterministic")
        result.passed shouldBe true
      }
    }

    "prepare and complete publication evidence without exposing a role before the artifact result" in {
      Given("a typed CozyMedia prepared publication and its exact build manifest")
      _with_fixture { fixture =>
        val publication = CozyMedia.preparePublication(CozyMedia.CommandConfig(
          fixture.descriptor,
          target = Some("example-infographic-ja"),
          profile = Some("site")
        )).head

        When("the command prepares then completes the matching typed media result")
        val prepared = CozyArticleMediaInfographicEvidence.prepare(CozyArticleMediaInfographicEvidence.PreparedInput(
          fixture.root, fixture.manifest, "1.0.0", fixture.repository, publication
        ))
        val completion = CozyArticleMediaInfographicEvidence.complete(
          prepared,
          CozyMedia.PublicationResult(publication, CozyMedia.PublicationOutcome.Reused)
        )

        Then("pre-publication evidence is completed as one strict infographic role with matching hash")
        prepared.integrity.record.sha256 shouldBe fixture.sha256
        completion.roleUpdate.variant.infographic.map(_.publicPath.toString) shouldBe Some("/images/development-process/example-ja.png")
        completion.result.integrity.record.publicationState shouldBe CozyArticleMediaIntegrity.PublicationState.Published
      }
    }

    "select only the exact descriptor resource and explicit paths" which {
      "ignore unrelated resource and filesystem decoys without locale fallback or discovery" in {
        Given("a descriptor with unrelated kind, role, language, and nested file decoys")
        _with_fixture { fixture =>
          _write(fixture.descriptor, _descriptor(extraresources = true))
          _write(fixture.project.resolve("x"), "unrelated source")
          Files.createDirectories(fixture.project.resolve("nested/decoys"))
          _write(fixture.project.resolve("nested/decoys/manifest.json"), "not an explicit manifest")
          _write(fixture.repository.resolve("nested/decoys/other.png"), "not selected")
          CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))

          When("the exact ja detailed-infographic image is projected")
          val result = CozyArticleMediaInfographicEvidence.project(_input(fixture))

          Then("only the literal image/detailed-infographic/ja resource and supplied paths are used")
          result.integrity.record.artifact.identity shouldBe "example-infographic-ja"
          result.integrity.record.sha256 shouldBe fixture.sha256
          result.artifactPath shouldBe fixture.destination.toRealPath()
        }
      }

      "reject absent, ambiguous, fallback, and invalid candidate locales" in {
        Given("descriptor variants that cannot choose exactly one canonical candidate")
        _with_fixture { fixture =>
          val variants = Vector(
            _descriptor(language = "en"),
            _descriptor(extraresources = true, duplicatecandidate = true),
            _descriptor(language = "ja-jp"),
            _descriptor(language = "")
          )

          When("the candidate selection is evaluated")
          val errors = variants.map { value =>
            _write(fixture.descriptor, value)
            intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))
          }

          Then("every missing, ambiguous, or noncanonical candidate fails deterministically")
          errors should have size variants.size
          errors.map(_.getMessage).foreach(_ should not be empty)
        }
      }

      "reject a duplicate descriptor id even when its duplicate is not a candidate" in {
        Given("a selected resource and a noncandidate resource that share one exact id")
        _with_fixture { fixture =>
          _write(fixture.descriptor, _descriptor(duplicatenoncandidate = true))

          When("descriptor resource invariants are checked before candidate selection and manifest attribution")
          val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))

          Then("the duplicate descriptor id is rejected deterministically")
          error.getMessage should include("ids must be unique")
        }
      }
    }

    "reject malformed descriptors, explicit paths, profiles, and manifests" which {
      "reject descriptor schema, identity, resource, version, publication, and selected build-path defects" in {
        Given("a table of selected descriptor and input contract defects")
        _with_fixture { fixture =>
          val invalids = Vector[() => Unit](
            () => _write(fixture.descriptor, _descriptor(schema = "wrong")),
            () => _write(fixture.descriptor, _descriptor(knowledge = "other/article")),
            () => _write(fixture.descriptor, _descriptor(knowledge = "/unsafe")),
            () => _write(fixture.descriptor, _descriptor(profile = "missing")),
            () => _write(fixture.descriptor, _descriptor().replace("\"publications\": {\"site\": \"images/development-process/example-ja.png\"}", "\"publications\": {}")),
            () => _write(fixture.descriptor, _descriptor(destination = "images/example.jpg")),
            () => _write(fixture.descriptor, _descriptor(destination = "images/../escape.png")),
            () => _write(fixture.descriptor, _descriptor(destination = "images/example.png?query")),
            () => _write(fixture.descriptor, _descriptor(resourceid = " bad")),
            () => _write(fixture.descriptor, _descriptor(output = "../escape.png")),
            () => _write(fixture.descriptor, _descriptor().replace("\"source\": \"infographic/example.svg\", \"output\": \"target/cozy-media/example-ja.png\", \"build\": \"copy\"", "\"source\": \"../escape.png\", \"build\": \"prebuilt\""))
          )

          When("each explicit descriptor contract is projected")
          val errors = invalids.map { mutate =>
            _write(fixture.descriptor, _descriptor())
            mutate()
            intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))
          }
          val versionerror = intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture, version = " ")))

          Then("all descriptor and version defects fail before integrity production")
          errors should have size invalids.size
          versionerror.getMessage should include("version")
        }
      }

      "reject missing, wrong, outside, and symlink-escaping descriptor or manifest inputs" in {
        Given("the exact explicit project paths and a second external file")
        _with_fixture { fixture =>
          val external = fixture.root.resolveSibling(s"${fixture.root.getFileName}-external.json")
          try {
            _write(external, _manifest(fixture.sha256))
            val missingdescriptor = _input(fixture, descriptor = fixture.project.resolve("missing.yaml"))
            val outsidedescriptor = _input(fixture, descriptor = external)
            val wrongmanifest = _input(fixture, manifest = fixture.project.resolve("manifest.json"))
            val outsidemanifest = _input(fixture, manifest = external)

            Given("descriptor and manifest symlinks that point to the external target")
            val symlinkinputs = _prepare_symlink_inputs(fixture, external)

            When("the missing, outside, and wrong explicit paths are projected")
            val errors = Vector(
              () => CozyArticleMediaInfographicEvidence.project(missingdescriptor),
              () => CozyArticleMediaInfographicEvidence.project(outsidedescriptor),
              () => CozyArticleMediaInfographicEvidence.project(wrongmanifest),
              () => CozyArticleMediaInfographicEvidence.project(outsidemanifest)
            ).map(x => intercept[IllegalArgumentException](x()))

            When("the symlinked descriptor or manifest are projected")
            val linkerrors = Vector(
              () => CozyArticleMediaInfographicEvidence.project(symlinkinputs.descriptorinput),
              () => CozyArticleMediaInfographicEvidence.project(symlinkinputs.manifestinput)
            ).map(x => intercept[IllegalArgumentException](x()))

            Then("lexical and real path containment failures are rejected without serialized absolute provenance")
            errors should have size 4
            linkerrors should have size 2
            linkerrors.foreach(_.getMessage should not be empty)
          } finally {
            Files.deleteIfExists(external)
          }
        }
      }

      "reject contradictory roots, destination mismatches, and missing files" in {
        Given("a static profile root and independently forged explicit publication results")
        _with_fixture { fixture =>
          val wrongroot = fixture.project
          val wrongdestination = fixture.repository.resolve("images/other.png")

          When("contradictory roots, destinations, rootEnv, and missing artifact paths are projected")
          val errors = Vector(
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture, publicationroot = wrongroot)),
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture, repositoryroot = wrongroot)),
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture, destination = wrongdestination)),
            () => {
              _write(fixture.descriptor, _descriptor().replace("{\"root\": \"../repository\"}", "{\"rootEnv\": \"COZY_ARTICLE_MEDIA_INFOGRAPHIC_EVIDENCE_UNDEFINED_ROOT\"}"))
              CozyArticleMediaInfographicEvidence.project(_input(fixture))
            },
            () => {
              _write(fixture.descriptor, _descriptor())
              Files.delete(fixture.destination)
              CozyArticleMediaInfographicEvidence.project(_input(fixture))
            }
          ).map(x => intercept[IllegalArgumentException](x()))

          Then("the explicit configured root and destination identity are required")
          errors should have size 5
          errors.map(_.getMessage).foreach(_ should not be empty)
        }

      }

      "reject a selected destination symlink that escapes the configured real root" in {
        Given("an external target substituted through the selected destination symlink")
        _with_fixture { fixture =>
          val external = fixture.root.resolve("external.png")
          _write(external, "external bytes")
          val before = Files.readAllBytes(external).toVector
          Files.delete(fixture.destination)
          try Files.createSymbolicLink(fixture.destination, external)
          catch {
            case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
          }

          When("the explicit destination real path is resolved")
          val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))

          Then("the symlink escape fails and leaves external bytes unchanged")
          error.getMessage should include("real path escapes")
          Files.readAllBytes(external).toVector shouldBe before
        }
      }

      "accept a safe in-root generated build-output symlink" in {
        Given("a descriptor-selected generated PNG link that resolves inside the descriptor root")
        _with_fixture { fixture =>
          val target = fixture.project.resolve("target/cozy-media/generated-source.png")
          val link = fixture.project.resolve("target/cozy-media/generated-link.png")
          _write(fixture.descriptor, _descriptor(output = "target/cozy-media/generated-link.png"))
          _write_bytes(target, _png_bytes)
          CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))
          Files.delete(link)
          try Files.createSymbolicLink(link, target)
          catch {
            case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
          }
          And("the accepted replacement is visibly a symbolic link before projection")
          Files.isSymbolicLink(link) shouldBe true

          When("the accepted generated selected output is projected through the safe in-root link")
          val result = CozyArticleMediaInfographicEvidence.project(_input(fixture))

          Then("the generated resource retains its canonical integrity")
          result.integrity.record.sha256 shouldBe fixture.sha256
        }
      }

      "accept a safe in-root prebuilt build-output symlink" in {
        Given("a descriptor-selected prebuilt PNG link that resolves inside the descriptor root")
        _with_fixture { fixture =>
          val prebuilttarget = fixture.project.resolve("assets/prebuilt-source.png")
          val prebuiltlink = fixture.project.resolve("assets/prebuilt-link.png")
          _write_bytes(prebuilttarget, _png_bytes)
          try Files.createSymbolicLink(prebuiltlink, prebuilttarget)
          catch {
            case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
          }
          _write(fixture.descriptor, _descriptor(source = "assets/prebuilt-link.png", build = "prebuilt"))
          Files.delete(fixture.manifest)
          CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))

          When("the prebuilt selected output follows the safe in-root link")
          val prebuilt = CozyArticleMediaInfographicEvidence.project(_input(fixture))

          Then("the prebuilt resource retains its canonical integrity")
          prebuilt.integrity.record.sha256 shouldBe fixture.sha256
        }
      }

      "reject a missing selected build output" in {
        Given("a descriptor-selected build PNG that is absent")
        _with_fixture { fixture =>
          Files.delete(fixture.buildoutput)

          When("the explicit selected build output is absent")
          val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))

          Then("absence is rejected before destination evidence can be serialized")
          error.getMessage should include("selected build output")
        }
      }

      "reject a selected build output that resolves outside its descriptor root" in {
        Given("a descriptor-selected build PNG linked outside its descriptor root")
        _with_fixture { fixture =>
          val external = fixture.root.resolve("external-build.png")
          _write_bytes(external, _png_bytes)
          Files.delete(fixture.buildoutput)
          try Files.createSymbolicLink(fixture.buildoutput, external)
          catch {
            case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
          }

          When("the explicit selected build output resolves outside its descriptor root")
          val escaped = intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))

          Then("real-path containment rejects the escaping build output after following the link")
          escaped.getMessage should include("selected build output")
          escaped.getMessage should include("real path escapes")
        }
      }

      "reject malformed exact manifest shapes and selected entries" in {
        Given("manifest shape and selected-entry failures")
        _with_fixture { fixture =>
          val variants = Vector(
            "[]",
            "{\"schema\":\"wrong\",\"knowledge\":\"development-process/example\",\"resources\":[]}",
            "{\"schema\":\"cozy.media.v1\",\"knowledge\":\"other/article\",\"resources\":[]}",
            "{\"schema\":\"cozy.media.v1\",\"knowledge\":\"development-process/example\",\"resources\":{}}",
            "{\"schema\":\"cozy.media.v1\",\"knowledge\":\"development-process/example\",\"resources\":[null]}",
            _manifest(fixture.sha256, id = "other"),
            _manifest(fixture.sha256, duplicate = true),
            _manifest(fixture.sha256, path = "target/cozy-media/other.png"),
            _manifest("A" * 64),
            _manifest("")
          )

          When("each supplied exact manifest is parsed without discovery")
          val errors = variants.map { value =>
            _write(fixture.manifest, value)
            intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))
          }

          Then("every malformed selected manifest fails")
          errors should have size variants.size
        }
      }

      "reject stale output disagreement even when the manifest matches the selected build output" in {
        Given("a current manifest and build output that differ from the published PNG")
        _with_fixture { fixture =>
          _write_bytes(fixture.project.resolve("infographic/example.svg"), _different_png_bytes)
          CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))

          When("the exact selected build output and publication destination are both verified")
          val stale = intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))

          Then("the build output cannot disagree with the manifest or explicit destination bytes")
          stale.getMessage should include("manifest, build output, and published destination")
        }
      }

      "reject malformed parser input, invalid profile root syntax, and non-PNG destination bytes" in {
        Given("descriptor parser, profile-root, and published-byte variants with otherwise matching explicit input")
        _with_fixture { fixture =>
          val variants = Vector[() => Unit](
            () => _write(fixture.descriptor, "{"),
            () => _write(fixture.descriptor, _descriptor(profiledefinition = "{\"root\": \"bad\\u0000root\"}")),
            () => {
              _write_bytes(fixture.destination, "not a PNG but manifest-matching bytes".getBytes(StandardCharsets.UTF_8))
              _write(fixture.manifest, _manifest(_sha256(fixture.destination)))
            }
          )

          When("each malformed descriptor or structurally invalid published destination is projected")
          val errors = variants.map { mutate =>
            _write(fixture.descriptor, _descriptor())
            _write_bytes(fixture.destination, _png_bytes)
            _write(fixture.manifest, _manifest(_sha256(fixture.destination)))
            mutate()
            intercept[IllegalArgumentException](CozyArticleMediaInfographicEvidence.project(_input(fixture)))
          }

          Then("all parser, path-syntax, and PNG-integrity defects are deterministic failures before production")
          errors should have size variants.size
          errors.map(_.getMessage).foreach(_ should not be empty)
          errors.last.getMessage should include("structurally valid PNG")
        }
      }

      "reject null and unsafe public input fields before explicit parsing" in {
        Given("null nested values and unsafe identity, locale, profile, and outcome inputs")
        _with_fixture { fixture =>
          val invalids = Vector[() => CozyArticleMediaInfographicEvidence.Result](
            () => CozyArticleMediaInfographicEvidence.project(null),
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture).copy(projectRoot = null)),
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture).copy(publication = null)),
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture).copy(articleIdentity = "/unsafe")),
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture).copy(locale = "ja-jp")),
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture, profile = " ")),
            () => CozyArticleMediaInfographicEvidence.project(_input(fixture, outcome = null))
          )

          When("input normalization is applied")
          val errors = invalids.map(x => intercept[IllegalArgumentException](x()))

          Then("every public boundary defect is a deterministic failure")
          errors should have size invalids.size
          errors.map(_.getMessage).foreach(_ should not be empty)
        }
      }
    }
  }

  private final class Fixture(
    val root: Path,
    val project: Path,
    val repository: Path,
    val descriptor: Path,
    val manifest: Path,
    val buildoutput: Path,
    val destination: Path,
    val sha256: String
  )

  private def _with_fixture[A](f: Fixture => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-infographic-evidence/work")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "evidence-")
    try f(_fixture(root))
    finally _delete(root)
  }

  private def _fixture(root: Path): Fixture = {
    val project = Files.createDirectories(root.resolve("project"))
    val repository = Files.createDirectories(root.resolve("repository"))
    val descriptor = project.resolve("media.yaml")
    val manifest = project.resolve("target/cozy-media/manifest.json")
    val buildoutput = project.resolve("target/cozy-media/example-ja.png")
    val destination = repository.resolve("images/development-process/example-ja.png")
    _write_bytes(destination, _png_bytes)
    _write(project.resolve("knowledge/article.dox"), "knowledge")
    _write_bytes(project.resolve("infographic/example.svg"), _png_bytes)
    val digest = _sha256(destination)
    _write(descriptor, _descriptor())
    CozyMedia.build(CozyMedia.CommandConfig(descriptor))
    new Fixture(root, project, repository, descriptor, manifest, buildoutput, destination, digest)
  }

  private def _input(
    fixture: Fixture,
    descriptor: Path = null,
    manifest: Path = null,
    version: String = "1.0.0",
    profile: String = "site",
    publicationroot: Path = null,
    destination: Path = null,
    outcome: CozyArticleMediaInfographicEvidence.PublicationOutcome = CozyArticleMediaInfographicEvidence.PublicationOutcome.Published,
    repositoryroot: Path = null
  ): CozyArticleMediaInfographicEvidence.Input =
    CozyArticleMediaInfographicEvidence.Input(
      projectRoot = fixture.root,
      descriptorFile = Option(descriptor).getOrElse(fixture.descriptor),
      buildManifest = Option(manifest).getOrElse(fixture.manifest),
      articleIdentity = "development-process/example",
      locale = "ja",
      version = version,
      publication = CozyArticleMediaInfographicEvidence.PublicationResult(
        profile,
        Option(publicationroot).getOrElse(fixture.repository),
        Option(destination).getOrElse(fixture.destination),
        outcome
      ),
      repositoryRoot = Option(repositoryroot).getOrElse(fixture.repository)
    )

  private def _descriptor(
    schema: String = "cozy.media.v1",
    knowledge: String = "development-process/example",
    profile: String = "site",
    destination: String = "images/development-process/example-ja.png",
    resourceid: String = "example-infographic-ja",
    language: String = "ja",
    source: String = "infographic/example.svg",
    output: String = "target/cozy-media/example-ja.png",
    build: String = "copy",
    profiledefinition: String = "{\"root\": \"../repository\"}",
    extraresources: Boolean = false,
    duplicatecandidate: Boolean = false,
    duplicatenoncandidate: Boolean = false
  ): String = {
    val extras =
      if (!extraresources) ""
      else
        "," +
          "{\"id\":\"other-language\",\"kind\":\"image\",\"language\":\"en\",\"role\":\"detailed-infographic\",\"source\":\"x\",\"output\":\"target/cozy-media/x.png\",\"build\":\"copy\",\"publications\":{\"site\":\"images/x.png\"}}," +
          "{\"id\":\"other-role\",\"kind\":\"image\",\"language\":\"ja\",\"role\":\"thumbnail\",\"source\":\"x\",\"output\":\"target/cozy-media/x.png\",\"build\":\"copy\",\"publications\":{\"site\":\"images/x.png\"}}," +
          "{\"id\":\"other-kind\",\"kind\":\"video\",\"language\":\"ja\",\"role\":\"detailed-infographic\",\"source\":\"x\",\"output\":\"target/cozy-media/x.mp4\",\"build\":\"copy\",\"publications\":{\"site\":\"images/x.mp4\"}}"
    val duplicate =
      if (!duplicatecandidate) ""
      else ",{\"id\":\"duplicate\",\"kind\":\"image\",\"language\":\"ja\",\"role\":\"detailed-infographic\",\"source\":\"x\",\"output\":\"target/cozy-media/x.png\",\"build\":\"copy\",\"publications\":{\"site\":\"images/x.png\"}}"
    val noncandidate =
      if (!duplicatenoncandidate) ""
      else s""",{"id":"$resourceid","kind":"video","language":"ja","role":"detailed-infographic","source":"x","output":"target/cozy-media/x.mp4","build":"copy","publications":{"site":"images/x.mp4"}}"""
    val resource =
      if (build == "prebuilt")
        s"""{"id": "$resourceid", "kind": "image", "language": "$language", "role": "detailed-infographic", "source": "$source", "build": "$build", "publications": {"site": "$destination"}}"""
      else
        s"""{"id": "$resourceid", "kind": "image", "language": "$language", "role": "detailed-infographic", "source": "$source", "output": "$output", "build": "$build", "publications": {"site": "$destination"}}"""
    s"""{
       |  "schema": "$schema",
       |  "knowledge": {"id": "$knowledge", "source": "knowledge/article.dox"},
       |  "profiles": {"$profile": $profiledefinition},
       |  "resources": [
       |    $resource$extras$duplicate$noncandidate
       |  ]
       |}
       |""".stripMargin
  }

  private def _manifest(sha256: String, id: String = "example-infographic-ja", path: String = "target/cozy-media/example-ja.png", duplicate: Boolean = false): String = {
    val other = if (duplicate) s""",{"id":"$id","path":"$path","sha256":"$sha256"}""" else ""
    s"""{"schema":"cozy.media.v1","knowledge":"development-process/example","resources":[{"id":"$id","path":"$path","sha256":"$sha256"}$other]}"""
  }

  private final class SymlinkInputs(
    val descriptorinput: CozyArticleMediaInfographicEvidence.Input,
    val manifestinput: CozyArticleMediaInfographicEvidence.Input
  )

  private def _prepare_symlink_inputs(fixture: Fixture, external: Path): SymlinkInputs = {
    val descriptorlink = fixture.project.resolve("linked.yaml")
    try Files.createSymbolicLink(descriptorlink, external)
    catch {
      case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
    }
    Files.delete(fixture.manifest)
    try Files.createSymbolicLink(fixture.manifest, external)
    catch {
      case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
    }
    new SymlinkInputs(
      _input(fixture, descriptor = descriptorlink),
      _input(fixture, manifest = fixture.manifest)
    )
  }

  private def _state_name(value: CozyArticleMediaInfographicEvidence.PublicationOutcome): String =
    value match {
      case CozyArticleMediaInfographicEvidence.PublicationOutcome.Registered => "registered"
      case CozyArticleMediaInfographicEvidence.PublicationOutcome.Published => "published"
      case CozyArticleMediaInfographicEvidence.PublicationOutcome.Withdrawn => "withdrawn"
      case _ => ""
    }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
  }

  private def _write_bytes(path: Path, value: Array[Byte]): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value)
  }

  private lazy val _png_bytes: Array[Byte] = {
    val image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, 0xff3366cc)
    val output = new ByteArrayOutputStream()
    if (!ImageIO.write(image, "png", output))
      throw new IllegalStateException("PNG writer unavailable")
    output.toByteArray
  }

  private lazy val _different_png_bytes: Array[Byte] = {
    val image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, 0xffcc6633)
    val output = new ByteArrayOutputStream()
    if (!ImageIO.write(image, "png", output))
      throw new IllegalStateException("PNG writer unavailable")
    output.toByteArray
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(Files.readAllBytes(path)).map(x => f"${x & 0xff}%02x").mkString
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path))
      Files.walk(path).iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
  }
}
