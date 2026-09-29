package cozy.video

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.{FileTime, PosixFilePermission}
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary
import scala.collection.JavaConverters._

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardArtifactsSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video Storyboard artifacts" should {
    "atomically replace the exact prepared destination set" in {
      _with_temp_dir("success") { root =>
        Given("two nonempty staged records and two existing direct destinations")
        val staging = _staging(root)
        val first = _write(staging.resolve("first.json"), "new-first")
        val second = _write(staging.resolve("second.json"), "new-second")
        val firstdestination = _write(root.resolve("out/first.json"), "old-first")
        val seconddestination = _write(root.resolve("out/second.json"), "old-second")
        val unrelated = _write(root.resolve("out/unrelated.json"), "unrelated")
        val unrelatedsnapshot = _snapshot(unrelated)

        When("the exact staged artifact set is installed")
        CozyVideoStoryboardArtifacts.install(
          staging,
          Vector(CozyVideoStoryboardArtifacts.Artifact(first, firstdestination), CozyVideoStoryboardArtifacts.Artifact(second, seconddestination)),
          Set.empty
        )

        Then("only the declared direct destinations contain the prepared bytes and the unrelated set is untouched")
        _read(firstdestination) shouldBe "new-first"
        _read(seconddestination) shouldBe "new-second"
        _same_snapshot(unrelated, unrelatedsnapshot) shouldBe true
        Files.exists(staging.resolve("first.json")) shouldBe false
        Files.exists(staging.resolve("second.json")) shouldBe false
      }
    }

    "restore existing and absent destinations after an ordinary installation failure" in {
      _with_temp_dir("rollback") { root =>
        Given("one existing and one absent destination that are installed before a later staged move fails")
        val staging = _staging(root)
        val first = _write(staging.resolve("first.json"), "new-first")
        val second = _write(staging.resolve("second.json"), "new-second")
        val third = _write(staging.resolve("third.json"), "new-third")
        val firstdestination = _write(root.resolve("out/first.json"), "old-first")
        val seconddestination = root.resolve("out/second.json")
        val thirddestination = root.resolve("out/third.json")
        val original = _snapshot(firstdestination)
        var moves = 0

        When("the move seam fails after both an existing and absent-before destination have been replaced")
        val failure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.installWithMove(
            staging,
            Vector(
              CozyVideoStoryboardArtifacts.Artifact(first, firstdestination),
              CozyVideoStoryboardArtifacts.Artifact(second, seconddestination),
              CozyVideoStoryboardArtifacts.Artifact(third, thirddestination)
            ),
            Set.empty,
            (source, destination) => {
              moves += 1
              if (moves == 3) throw new IOException("third install failed")
              Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
              ()
            }
          )
        }

        Then("the original bytes, mode, and mtime and the absent-before state are restored without a partial generated result")
        failure.getMessage should include("rolled back")
        _same_snapshot(firstdestination, original) shouldBe true
        Files.exists(seconddestination) shouldBe false
        Files.exists(thirddestination) shouldBe false
      }
    }

    "reject duplicate, input-alias, and non-staged artifact destinations before mutation" in {
      _with_temp_dir("rejections") { root =>
        Given("a direct staging root and an untouched output sentinel")
        val staging = _staging(root)
        val prepared = _write(staging.resolve("prepared.json"), "prepared")
        val destination = _write(root.resolve("out/target.json"), "sentinel")

        When("duplicate destinations, input aliases, and external sources are offered")
        val duplicate = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(staging, Vector(
            CozyVideoStoryboardArtifacts.Artifact(prepared, destination),
            CozyVideoStoryboardArtifacts.Artifact(prepared, destination)
          ), Set.empty)
        }
        val alias = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(staging, Vector(CozyVideoStoryboardArtifacts.Artifact(prepared, destination)), Set(destination))
        }
        val external = _write(root.resolve("external.json"), "external")
        val outside = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(staging, Vector(CozyVideoStoryboardArtifacts.Artifact(external, destination)), Set.empty)
        }

        Then("each unsafe relation fails closed and leaves the destination unchanged")
        duplicate.getMessage should include("duplicate")
        alias.getMessage should include("aliases a declared input")
        outside.getMessage should include("escapes its staging root")
        _read(destination) shouldBe "sentinel"
      }
    }

    "reject staging-root symbolic ancestors and destinations inside protected declared directories before mutation" in {
      _with_temp_dir("staging-and-protected-ancestors") { root =>
        Given("sentinel destinations beneath direct output and protected declared-input directories")
        val stagingparent = root.resolve("staging-direct")
        val directstaging = stagingparent.resolve("attempt")
        Files.createDirectories(directstaging)
        val staginglink = root.resolve("staging-link")
        Files.createSymbolicLink(staginglink, stagingparent)
        val linkedprepared = _write(directstaging.resolve("linked-prepared.json"), "linked-prepared")
        val outputdestination = _write(root.resolve("out/target.json"), "output-sentinel")
        val outputsnapshot = _snapshot(outputdestination)
        val staging = _staging(root)
        val protectedprepared = _write(staging.resolve("protected-prepared.json"), "protected-prepared")
        val protectedroot = root.resolve("declared-input")
        val protecteddestination = _write(protectedroot.resolve("nested/target.json"), "protected-sentinel")
        val protectedsnapshot = _snapshot(protecteddestination)

        When("a staging root has a symbolic absolute ancestor or a generated destination is inside a protected declared directory")
        val stagingfailure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(
            staginglink.resolve("attempt"),
            Vector(CozyVideoStoryboardArtifacts.Artifact(linkedprepared, outputdestination)),
            Set.empty
          )
        }
        val protectedfailure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(
            staging,
            Vector(CozyVideoStoryboardArtifacts.Artifact(protectedprepared, protecteddestination)),
            Set(protectedroot)
          )
        }

        Then("both relations fail closed before a destination byte, POSIX mode, or FileTime can change")
        stagingfailure.getMessage should include("direct")
        protectedfailure.getMessage should include("aliases a declared input")
        _same_snapshot(outputdestination, outputsnapshot) shouldBe true
        _same_snapshot(protecteddestination, protectedsnapshot) shouldBe true
      }
    }

    "preserve an existing destination when atomic replacement is unavailable" in {
      _with_temp_dir("atomic") { root =>
        Given("an existing direct destination and a prepared replacement")
        val staging = _staging(root)
        val prepared = _write(staging.resolve("prepared.json"), "new")
        val destination = _write(root.resolve("out/target.json"), "old")

        When("the installation seam reports an unsupported atomic move")
        val failure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.installWithMove(
            staging,
            Vector(CozyVideoStoryboardArtifacts.Artifact(prepared, destination)),
            Set.empty,
            (_, _) => throw new AtomicMoveNotSupportedException("source", "destination", "unsupported")
          )
        }

        Then("there is no copy fallback and the previous destination remains intact")
        failure.getMessage should include("rolled back")
        _read(destination) shouldBe "old"
      }
    }

    "reject empty prepared sources and unsafe source or destination ancestors before any mutation" in {
      _with_temp_dir("safe-paths") { root =>
        Given("a direct staging root, an untouched destination, and empty or symlink-ancestor candidates")
        val staging = _staging(root)
        val destination = _write(root.resolve("out/target.json"), "sentinel")
        val original = _snapshot(destination)
        val empty = _write(staging.resolve("empty.json"), "")
        val direct = staging.resolve("direct")
        val prepared = _write(direct.resolve("prepared.json"), "prepared")
        val linked = staging.resolve("linked")
        Files.createSymbolicLink(linked, direct)
        val destinationparent = root.resolve("destination-direct")
        Files.createDirectories(destinationparent)
        val destinationlink = root.resolve("destination-link")
        Files.createSymbolicLink(destinationlink, destinationparent)

        When("the installer receives an empty source, a source through a symlink ancestor, or a symlinked destination parent")
        val emptyfailure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(staging, Vector(CozyVideoStoryboardArtifacts.Artifact(empty, destination)), Set.empty)
        }
        val sourcefailure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(staging, Vector(CozyVideoStoryboardArtifacts.Artifact(linked.resolve("prepared.json"), destination)), Set.empty)
        }
        val destinationfailure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(staging, Vector(CozyVideoStoryboardArtifacts.Artifact(prepared, destinationlink.resolve("target.json"))), Set.empty)
        }

        Then("each unsafe relation fails closed before replacing the existing destination")
        emptyfailure.getMessage should include("nonempty")
        sourcefailure.getMessage should include("direct")
        destinationfailure.getMessage should include("unsafe ancestor")
        _same_snapshot(destination, original) shouldBe true
      }
    }

    "fail backup preparation before any destination replacement" in {
      _with_temp_dir("backup-failure") { root =>
        Given("an existing destination and a staging backup path that is already a direct file")
        val staging = _staging(root)
        val prepared = _write(staging.resolve("prepared.json"), "new")
        _write(staging.resolve("backups"), "not-a-directory")
        val destination = _write(root.resolve("out/target.json"), "old")
        val original = _snapshot(destination)

        When("the installer cannot establish its private backup directory")
        val failure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.install(staging, Vector(CozyVideoStoryboardArtifacts.Artifact(prepared, destination)), Set.empty)
        }

        Then("backup failure leaves the original bytes, mode, and mtime untouched before its first move")
        failure.getMessage should include("Storyboard backup root")
        _same_snapshot(destination, original) shouldBe true
      }
    }

    "retain recovery evidence when rollback itself cannot complete" in {
      _with_temp_dir("rollback-recovery") { root =>
        Given("an existing destination and an installer seam that also rejects restoration")
        val staging = _staging(root)
        val prepared = _write(staging.resolve("prepared.json"), "new")
        val destination = _write(root.resolve("out/target.json"), "old")
        var moves = 0

        When("the seam fails after replacing the destination and then rejects rollback")
        val failure = intercept[RuntimeException] {
          CozyVideoStoryboardArtifacts.installWithMove(
            staging,
            Vector(CozyVideoStoryboardArtifacts.Artifact(prepared, destination), CozyVideoStoryboardArtifacts.Artifact(_write(staging.resolve("later.json"), "later"), root.resolve("out/later.json"))),
            Set.empty,
            (source, target) => {
              moves += 1
              if (moves == 1) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
              else throw new IOException("move unavailable")
              ()
            }
          )
        }

        Then("the failure reports the retained staging recovery location")
        failure.getMessage should include("requires recovery")
        failure.getMessage should include(staging.toString)
        failure.getMessage should include("original=")
        failure.getMessage should include("rollback=")
        Option(failure.getCause).map(_.getMessage) shouldBe Some("move unavailable")
        failure.getSuppressed.toVector.map(_.getMessage).exists(_.contains("cannot restore")) shouldBe true
        _read(staging.resolve("backups/0000.backup")) shouldBe "old"
      }
    }
  }

  private def _staging(root: Path): Path = {
    val path = root.resolve("target/cozy-video/staging/final/attempt")
    Files.createDirectories(path)
    path
  }

  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private final case class Snapshot(bytes: Vector[Byte], modified: FileTime, permissions: Set[PosixFilePermission])

  private def _snapshot(path: Path): Snapshot =
    Snapshot(Files.readAllBytes(path).toVector, Files.getLastModifiedTime(path), Files.getPosixFilePermissions(path).asScala.toSet)

  private def _same_snapshot(path: Path, expected: Snapshot): Boolean =
    Files.readAllBytes(path).toVector == expected.bytes &&
      Files.getLastModifiedTime(path) == expected.modified &&
      Files.getPosixFilePermissions(path).asScala.toSet == expected.permissions

  private def _with_temp_dir(label: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory("cozy-video-storyboard-artifacts-" + label).toRealPath()
    try body(root)
    finally {
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
