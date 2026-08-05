package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, LinkOption, Path}
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaPublicationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "CozyMedia typed publication" should {
    "provide immutable preparation and force planning" which {
      "prepare exact immutable evidence without changing an absent destination" in {
        _with_work("prepare-no-op") { dir =>
          Given("a cozy.media.v1 descriptor with one explicit profile publication")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "prepared PNG bytes")
          val destination = dir.resolve("publication/images/summary-ja.png")
          Files.createDirectories(dir.resolve("publication"))

          When("the complete selected candidate set is prepared")
          val prepared = _prepare(descriptor)

          Then("the typed plan retains exact descriptor, resource, profile, path, hash, and create evidence")
          prepared.size shouldBe 1
          val publication = prepared.head
          publication.descriptorFile shouldBe descriptor.toAbsolutePath.normalize()
          publication.descriptorRoot shouldBe dir.toAbsolutePath.normalize()
          publication.descriptor.schema shouldBe "cozy.media.v1"
          publication.descriptorSha256 should have length 64
          publication.resource.id shouldBe "summary-ja"
          publication.resource.language shouldBe Some("ja")
          publication.resource.role shouldBe Some("detailed-infographic")
          publication.profile shouldBe "site"
          publication.profileRoot shouldBe dir.resolve("publication").toAbsolutePath.normalize()
          publication.profileRootIdentity shouldBe dir.resolve("publication").toAbsolutePath.normalize()
          publication.publishablePath shouldBe dir.resolve("outputs/summary-ja.png").toAbsolutePath.normalize()
          publication.destination shouldBe destination.toAbsolutePath.normalize()
          publication.destinationIdentity shouldBe destination.toAbsolutePath.normalize()
          publication.sourceSha256 should have length 64
          publication.destinationState shouldBe CozyMedia.DestinationState.Absent
          publication.disposition shouldBe CozyMedia.PublicationDisposition.Create

          And("preparation has created no destination, temporary file, or destination parent")
          Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(destination.getParent, LinkOption.NOFOLLOW_LINKS) shouldBe false
          _temporary_siblings(destination) shouldBe Vector.empty
        }
      }

      "reuse equal publication bytes without replacing their timestamp" in {
        _with_work("reuse") { dir =>
          Given("equal source and destination bytes with an explicit destination modification time")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "same bytes")
          val destination = dir.resolve("publication/images/summary-ja.png")
          _write(destination, "same bytes")
          Files.setLastModifiedTime(destination, FileTime.fromMillis(1000L))
          val before = Files.getLastModifiedTime(destination)

          When("the equal-SHA plan is committed")
          val prepared = _prepare(descriptor)
          val results = CozyMedia.commitPublication(prepared)

          Then("it reports reuse without replacing destination bytes or modification time")
          prepared.head.disposition shouldBe CozyMedia.PublicationDisposition.Reuse
          results.map(_.outcome) shouldBe Vector(CozyMedia.PublicationOutcome.Reused)
          Files.readAllBytes(destination) shouldBe "same bytes".getBytes(StandardCharsets.UTF_8)
          Files.getLastModifiedTime(destination) shouldBe before
        }
      }

      "reject differing destinations without force before changing any selected candidate" in {
        _with_work("differ-without-force") { dir =>
          Given("two selected destinations whose bytes differ from their outputs")
          val descriptor = _write_descriptor(dir, Vector(
            ("a", "inputs/a.svg", "outputs/a.png", "images/a.png"),
            ("b", "inputs/b.svg", "outputs/b.png", "images/b.png")
          ))
          _write(dir.resolve("inputs/a.svg"), "source a")
          _write(dir.resolve("inputs/b.svg"), "source b")
          _write(dir.resolve("outputs/a.png"), "new a")
          _write(dir.resolve("outputs/b.png"), "new b")
          val destinationa = dir.resolve("publication/images/a.png")
          val destinationb = dir.resolve("publication/images/b.png")
          _write(destinationa, "old a")
          _write(destinationb, "old b")

          When("preparation is requested without force")
          val error = _failure(_prepare(descriptor))

          Then("the force rule fails deterministically before mutation")
          error.getMessage should include_text("destination differs")
          Files.readString(destinationa, StandardCharsets.UTF_8) shouldBe "old a"
          Files.readString(destinationb, StandardCharsets.UTF_8) shouldBe "old b"
        }
      }

      "replace differing destinations through the typed atomic commit surface when force is explicit" in {
        _with_work("replace") { dir =>
          Given("a prepared output and a different direct regular destination")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "new bytes")
          val destination = dir.resolve("publication/images/summary-ja.png")
          _write(destination, "old bytes")

          When("force preparation and typed commit are performed")
          val prepared = _prepare(descriptor, force = true)
          val results = CozyMedia.commitPublication(prepared)

          Then("the source bytes are atomically installed and replacement is retained in the result")
          prepared.head.disposition shouldBe CozyMedia.PublicationDisposition.Replace
          results.map(_.outcome) shouldBe Vector(CozyMedia.PublicationOutcome.Replaced)
          Files.readAllBytes(destination) shouldBe "new bytes".getBytes(StandardCharsets.UTF_8)
          _temporary_siblings(destination) shouldBe Vector.empty
        }
      }

      "create an absent destination through the typed commit surface" in {
        _with_work("create") { dir =>
          Given("an absent explicit publication destination")
          val descriptor = _write_descriptor(dir, Vector(("summary-en", "inputs/summary-en.svg", "outputs/summary-en.png", "images/summary-en.png")))
          _write(dir.resolve("inputs/summary-en.svg"), "source")
          _write(dir.resolve("outputs/summary-en.png"), "created bytes")
          val destination = dir.resolve("publication/images/summary-en.png")
          Files.createDirectories(dir.resolve("publication"))

          When("the create plan is committed")
          val results = CozyMedia.commitPublication(_prepare(descriptor))

          Then("the destination is created and the result reports creation")
          results.map(_.outcome) shouldBe Vector(CozyMedia.PublicationOutcome.Created)
          Files.readAllBytes(destination) shouldBe "created bytes".getBytes(StandardCharsets.UTF_8)
        }
      }

      "reject duplicate normalized destinations during complete preparation" in {
        _with_work("duplicate-destination") { dir =>
          Given("two distinct resources whose normalized profile destinations are equal")
          val descriptor = _write_descriptor(dir, Vector(
            ("a", "inputs/a.svg", "outputs/a.png", "images/summary.png"),
            ("b", "inputs/b.svg", "outputs/b.png", "images/other/../summary.png")
          ))
          _write(dir.resolve("inputs/a.svg"), "source a")
          _write(dir.resolve("inputs/b.svg"), "source b")
          _write(dir.resolve("outputs/a.png"), "output a")
          _write(dir.resolve("outputs/b.png"), "output b")
          val destination = dir.resolve("publication/images/summary.png")
          Files.createDirectories(dir.resolve("publication"))

          When("the complete selected vector is prepared")
          val error = _failure(_prepare(descriptor, force = true))

          Then("the collision is rejected before either destination is created")
          error.getMessage should include_text("destinations must be unique")
          Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "enforce containment and reject malformed evidence" which {
      "reject an intermediate symbolic-link escape during complete preparation" in {
        _with_work("intermediate-symlink-escape") { dir =>
          Given("an existing strict profile root with a symbolic-link intermediate destination directory")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "output")
          Files.createDirectories(dir.resolve("publication"))
          val outside = dir.resolve("outside")
          Files.createDirectories(outside)
          Files.createSymbolicLink(dir.resolve("publication/images"), outside)

          When("the selected destination would traverse the symbolic link")
          val escapeerror = _failure(_prepare(descriptor))

          Then("preparation rejects the escape without mutating the outside directory")
          escapeerror.getMessage should include_text("destination parent")
          Files.exists(outside.resolve("summary-ja.png"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }

      "reject a profile root whose intermediate ancestor is a symbolic-link alias" in {
        _with_work("profile-root-intermediate-symlink") { dir =>
          Given("a descriptor-selected profile root with a direct final directory below a symbolic-link ancestor")
          val descriptor = _write_descriptor(
            dir,
            Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")),
            profileroot = "publication-alias/subdir"
          )
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "output")
          val directroot = dir.resolve("publication-target")
          Files.createDirectories(directroot.resolve("subdir"))
          Files.createSymbolicLink(dir.resolve("publication-alias"), directroot)
          val destination = directroot.resolve("subdir/images/summary-ja.png")

          When("typed preparation validates the selected profile root")
          val error = _failure(_prepare(descriptor))

          Then("canonical root identity rejects the alias before any destination mutation")
          error.getMessage should include_text("profile root")
          Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }

      "reject lexical destinations whose existing parents alias one real in-root directory" in {
        _with_work("real-parent-alias") { dir =>
          Given("two selected lexical destinations with direct and symbolic-link parent spellings")
          val descriptor = _write_descriptor(dir, Vector(
            ("a", "inputs/a.svg", "outputs/a.png", "images/summary.png"),
            ("b", "inputs/b.svg", "outputs/b.png", "alias/summary.png")
          ))
          _write(dir.resolve("inputs/a.svg"), "source a")
          _write(dir.resolve("inputs/b.svg"), "source b")
          _write(dir.resolve("outputs/a.png"), "output a")
          _write(dir.resolve("outputs/b.png"), "output b")
          val images = dir.resolve("publication/images")
          val alias = dir.resolve("publication/alias")
          Files.createDirectories(images)
          Files.createSymbolicLink(alias, images)

          When("their complete selected vector is prepared")
          val aliaserror = _failure(_prepare(descriptor, force = true))

          Then("the real-path alias is rejected before either lexical destination is mutated")
          Files.isSameFile(images, alias) shouldBe true
          aliaserror.getMessage should include_text("destination parent")
          Files.exists(images.resolve("summary.png"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }

      "reject a symbolic-link publishable source without mutation" in {
        _with_work("symlink-source") { dir =>
          Given("a descriptor whose selected publishable output is a symbolic link")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          val actualoutput = dir.resolve("outputs/actual.png")
          _write(actualoutput, "actual")
          Files.createSymbolicLink(dir.resolve("outputs/summary-ja.png"), actualoutput)
          val destination = dir.resolve("publication/images/summary-ja.png")
          Files.createDirectories(dir.resolve("publication"))

          When("the symbolic-link output is prepared")
          val sourceerror = _failure(_prepare(descriptor))

          Then("the source boundary rejects it without creating a destination")
          sourceerror.getMessage should include_text("direct regular non-symlink")
          Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }

      "reject a symbolic-link destination without overwriting its target" in {
        _with_work("symlink-destination") { dir =>
          Given("a regular output and a symbolic-link destination")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "regular")
          val destination = dir.resolve("publication/images/summary-ja.png")
          val actualdestination = dir.resolve("publication/images/actual.png")
          _write(actualdestination, "actual destination")
          Files.createSymbolicLink(destination, actualdestination)

          When("the symbolic-link destination is prepared")
          val destinationerror = _failure(_prepare(descriptor))

          Then("the destination boundary rejects it without overwriting the link target")
          destinationerror.getMessage should include_text("destination must be absent or a direct regular non-symlink")
          Files.readString(actualdestination, StandardCharsets.UTF_8) shouldBe "actual destination"
        }
      }

      "reject malformed prepared evidence before destination mutation" in {
        _with_work("malformed-prepared-evidence") { dir =>
          Given("a regular absent destination and otherwise valid prepared evidence")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "regular")
          Files.createDirectories(dir.resolve("publication"))
          val destination = dir.resolve("publication/images/summary-ja.png")
          val prepared = _prepare(descriptor)
          val malformed = prepared.head.copy(sourceSha256 = "not-a-sha256")

          When("a malformed plan is committed")
          val malformederror = _failure(CozyMedia.commitPublication(Vector(malformed)))

          Then("the malformed evidence fails before destination mutation")
          malformederror.getMessage should include_text("source has changed")
          Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "reject stale evidence and preserve commit outcomes across races" which {
      "reject a stale source vector before the first destination write" in {
        _with_work("stale-source-vector") { dir =>
          Given("a prepared two-resource create vector whose second source changes afterward")
          val descriptor = _write_descriptor(dir, Vector(
            ("a", "inputs/a.svg", "outputs/a.png", "images/a.png"),
            ("b", "inputs/b.svg", "outputs/b.png", "images/b.png")
          ))
          _write(dir.resolve("inputs/a.svg"), "source a")
          _write(dir.resolve("inputs/b.svg"), "source b")
          _write(dir.resolve("outputs/a.png"), "output a")
          _write(dir.resolve("outputs/b.png"), "output b")
          Files.createDirectories(dir.resolve("publication"))
          val sourceplans = _prepare(descriptor)
          val destinationa = dir.resolve("publication/images/a.png")
          val destinationb = dir.resolve("publication/images/b.png")
          _write(dir.resolve("outputs/b.png"), "changed output b")

          When("the stale source vector is committed")
          val sourceerror = _failure(CozyMedia.commitPublication(sourceplans))

          Then("complete revalidation fails before the first destination changes")
          sourceerror.getMessage should include_text("source has changed")
          Files.exists(destinationa, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(destinationb, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }

      "reject a stale destination vector before an earlier destination is written" in {
        _with_work("stale-destination-vector") { dir =>
          Given("a newly prepared two-resource vector with absent destinations")
          val descriptor = _write_descriptor(dir, Vector(
            ("a", "inputs/a.svg", "outputs/a.png", "images/a.png"),
            ("b", "inputs/b.svg", "outputs/b.png", "images/b.png")
          ))
          _write(dir.resolve("inputs/a.svg"), "source a")
          _write(dir.resolve("inputs/b.svg"), "source b")
          _write(dir.resolve("outputs/a.png"), "output a")
          _write(dir.resolve("outputs/b.png"), "output b")
          Files.createDirectories(dir.resolve("publication"))
          val destinationplans = _prepare(descriptor)
          val destinationa = dir.resolve("publication/images/a.png")
          val destinationb = dir.resolve("publication/images/b.png")
          _write(destinationb, "interloper")

          When("one destination changes after preparation")
          val destinationerror = _failure(CozyMedia.commitPublication(destinationplans))

          Then("no earlier vector destination is written")
          destinationerror.getMessage should include_text("destination has changed")
          Files.exists(destinationa, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.readString(destinationb, StandardCharsets.UTF_8) shouldBe "interloper"
        }
      }

      "reject a changed descriptor vector before the first selected destination write" in {
        _with_work("descriptor-staleness") { dir =>
          Given("a prepared two-candidate descriptor publication vector")
          val resources = Vector(
            ("a", "inputs/a.svg", "outputs/a.png", "images/a.png"),
            ("b", "inputs/b.svg", "outputs/b.png", "images/b.png")
          )
          val descriptor = _write_descriptor(dir, resources)
          resources.foreach { resource =>
            _write(dir.resolve(resource._2), s"source ${resource._1}")
            _write(dir.resolve(resource._3), s"output ${resource._1}")
          }
          Files.createDirectories(dir.resolve("publication"))
          val descriptorplans = _prepare(descriptor)
          val destinationa = dir.resolve("publication/images/a.png")
          val destinationb = dir.resolve("publication/images/b.png")
          _write_descriptor(dir, resources.reverse)

          When("the descriptor changes after preparation")
          val descriptorerror = _failure(CozyMedia.commitPublication(descriptorplans))

          Then("descriptor freshness rejects the complete vector before its first write")
          descriptorerror.getMessage should include_text("descriptor has changed")
          Files.exists(destinationa, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(destinationb, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }

      "preserve an intervening Create destination without writing another selected destination" in {
        _with_work("create-race") { dir =>
          Given("a newly prepared two-candidate create vector")
          val resources = Vector(
            ("a", "inputs/a.svg", "outputs/a.png", "images/a.png"),
            ("b", "inputs/b.svg", "outputs/b.png", "images/b.png")
          )
          val descriptor = _write_descriptor(dir, resources)
          resources.foreach { resource =>
            _write(dir.resolve(resource._2), s"source ${resource._1}")
            _write(dir.resolve(resource._3), s"output ${resource._1}")
          }
          Files.createDirectories(dir.resolve("publication"))
          val destinationa = dir.resolve("publication/images/a.png")
          val destinationb = dir.resolve("publication/images/b.png")
          val raceplans = _prepare(descriptor)

          When("an interloper is created after vector revalidation and before the first install")
          val raceerror = _failure(CozyMedia.commitPublication(raceplans, () => _write(destinationa, "interloper")))

          Then("atomic Create preserves the interloper, writes no later candidate, and cleans temporary siblings")
          raceerror.getMessage should include_text("Media publication failed")
          Files.readString(destinationa, StandardCharsets.UTF_8) shouldBe "interloper"
          Files.exists(destinationb, LinkOption.NOFOLLOW_LINKS) shouldBe false
          _temporary_siblings(destinationa) shouldBe Vector.empty
          _temporary_siblings(destinationb) shouldBe Vector.empty
        }
      }
    }

    "preserve legacy direct publish compatibility" which {
      "retain direct replacement publish behavior and identifying text" in {
        _with_work("legacy-replacement") { dir =>
          Given("a legacy media package with verified output and a differing destination")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "new bytes")
          val destination = dir.resolve("publication/images/summary-ja.png")
          _write(destination, "old bytes")
          val config = CozyMedia.CommandConfig(descriptor, profile = Some("site"))

          When("the direct legacy publish command is invoked")
          val publication = CozyMedia.publish(config)

          Then("its historical replacement behavior and identifying text remain available")
          Files.readString(destination, StandardCharsets.UTF_8) shouldBe "new bytes"
          publication should include_text("Cozy Media Publish")
          publication should include_text("profile: site")
          publication should include_text("summary-ja")
          publication should include_text(destination.toString)
        }
      }

      "retain direct dry-run text compatibility without mutation" in {
        _with_work("legacy-dry-run") { dir =>
          Given("a legacy media package with a differing destination")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "new bytes")
          val destination = dir.resolve("publication/images/summary-ja.png")
          _write(destination, "dry-run old bytes")
          val config = CozyMedia.CommandConfig(descriptor, profile = Some("site"))

          When("the direct legacy dry-run is invoked")
          val dryrun = CozyMedia.publish(config.copy(dryRun = true))

          Then("it remains non-mutating while reporting the legacy resource and profile")
          Files.readString(destination, StandardCharsets.UTF_8) shouldBe "dry-run old bytes"
          dryrun should include_text("profile: site")
          dryrun should include_text("summary-ja")
          dryrun should include_text("dry-run")
        }
      }

      "keep an initially absent legacy profile root unchanged during dry-run" in {
        _with_work("legacy-absent-profile-root-dry-run") { dir =>
          Given("a valid legacy package whose selected profile root is absent")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "output")
          val config = CozyMedia.CommandConfig(descriptor, profile = Some("site"))

          When("the legacy direct dry-run is invoked before the root exists")
          val dryrun = CozyMedia.publish(config.copy(dryRun = true))

          Then("it remains non-mutating while retaining the legacy publication report")
          Files.exists(dir.resolve("publication"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          dryrun should include_text("summary-ja")
          dryrun should include_text("dry-run")
        }
      }

      "create an initially absent legacy profile root during real publish" in {
        _with_work("legacy-absent-profile-root-publish") { dir =>
          Given("a valid legacy package whose selected profile root is absent")
          val descriptor = _write_descriptor(dir, Vector(("summary-ja", "inputs/summary-ja.svg", "outputs/summary-ja.png", "images/summary-ja.png")))
          _write(dir.resolve("knowledge/article.dox"), "Article\n=======\n")
          _write(dir.resolve("inputs/summary-ja.svg"), "source")
          _write(dir.resolve("outputs/summary-ja.png"), "output")
          val destination = dir.resolve("publication/images/summary-ja.png")
          val config = CozyMedia.CommandConfig(descriptor, profile = Some("site"))

          When("the legacy direct publish is invoked")
          CozyMedia.publish(config)

          Then("it safely creates the profile root and publishes the selected destination")
          Files.readString(destination, StandardCharsets.UTF_8) shouldBe "output"
        }
      }
    }

    "produce deterministic publication ordering" which {
      "produce field-identical deterministic plans for generated shuffled resource orderings" in {
        _with_work("deterministic-order") { dir =>
          Given("three distinct selected resources and thirty generated complete orderings")
          val resources = Vector(
            ("a", "inputs/a.svg", "outputs/a.png", "images/a.png"),
            ("b", "inputs/b.svg", "outputs/b.png", "images/b.png"),
            ("c", "inputs/c.svg", "outputs/c.png", "images/c.png")
          )
          resources.foreach { resource =>
            _write(dir.resolve(resource._2), s"source ${resource._1}")
            _write(dir.resolve(resource._3), s"output ${resource._1}")
          }
          Files.createDirectories(dir.resolve("publication"))
          val descriptor = dir.resolve("media.yaml")
          val orderings = Gen.oneOf(resources.permutations.toVector)

          When("ScalaCheck validates each generated descriptor ordering")
          val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(orderings) { shuffled =>
            _write_descriptor(dir, shuffled.toVector)
            val actual = _prepare(descriptor)
            _write_descriptor(dir, resources)
            val expected = _prepare(descriptor)
            val actualnormalized = actual.map(_.copy(descriptorSha256 = ""))
            val expectednormalized = expected.map(_.copy(descriptorSha256 = ""))

            shuffled.size == resources.size &&
              actualnormalized == expectednormalized &&
              actual.map(_.resource.id) == Vector("a", "b", "c") &&
              actual.map(_.sourceSha256) == expected.map(_.sourceSha256) &&
              actual.map(_.descriptorSha256).forall(_.matches("[0-9a-f]{64}")) &&
              expected.map(_.descriptorSha256).forall(_.matches("[0-9a-f]{64}"))
          })

          Then("the selected plan vector is field-identical apart from its order-sensitive raw descriptor hash")
          check.passed shouldBe true
          check.succeeded should be >= 30
        }
      }
    }
  }

  private def _prepare(descriptor: Path, force: Boolean = false): Vector[CozyMedia.PreparedPublication] =
    CozyMedia.preparePublication(CozyMedia.CommandConfig(descriptor, profile = Some("site")), force)

  private def _write_descriptor(
    dir: Path,
    resources: Vector[(String, String, String, String)],
    profileroot: String = "publication"
  ): Path = {
    val descriptor = dir.resolve("media.yaml")
    val entries = resources.map { resource =>
      s"""  - id: ${resource._1}
         |    kind: image
         |    language: ja
         |    role: detailed-infographic
         |    source: ${resource._2}
         |    output: ${resource._3}
         |    build: copy
         |    publications:
         |      site: ${resource._4}
         |""".stripMargin
    }.mkString
    _write(
      descriptor,
      s"""schema: cozy.media.v1
         |knowledge:
         |  id: development-process/example
         |  source: knowledge/article.dox
         |profiles:
         |  site:
         |    root: $profileroot
         |resources:
         |$entries""".stripMargin
    )
    descriptor
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _failure(body: => Any): RuntimeException =
    intercept[RuntimeException] {
      body
    }

  private def _temporary_siblings(destination: Path): Vector[Path] = {
    val parent = destination.getParent
    if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS))
      Vector.empty
    else {
      val stream = Files.list(parent)
      try stream.iterator.asScala.filter(_.getFileName.toString.endsWith(".cozy-media.tmp")).toVector
      finally stream.close()
    }
  }

  private def _with_work(name: String)(body: Path => Unit): Unit = {
    val root = Path.of("target/cozy-media-publication-spec").toAbsolutePath.normalize().resolve(name)
    _delete(root)
    Files.createDirectories(root)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete)
      finally stream.close()
    }
}
