package cozy.modeler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import scala.collection.JavaConverters._
import org.scalatest.matchers.should.Matchers

/*
 * @since   Jun. 23, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
trait ModelerSpecSupport extends Matchers {
  protected def run_modeler_scala(input: Path, out: Path): String = {
    Files.createDirectories(out.getParent)
    val projectroot = Path.of(sys.props("user.dir")).toAbsolutePath.normalize()
    val normalizedinput = input.toAbsolutePath.normalize()
    require(
      normalizedinput.startsWith(projectroot),
      s"Modeler spec input must be inside the project: $normalizedinput"
    )
    val sourceidentity =
      projectroot.relativize(normalizedinput).toString.replace(java.io.File.separatorChar, '/')
    val outbuffer = new ByteArrayOutputStream
    val errbuffer = new ByteArrayOutputStream
    val outps = new PrintStream(outbuffer, true, StandardCharsets.UTF_8.name())
    val errps = new PrintStream(errbuffer, true, StandardCharsets.UTF_8.name())
    try {
      Console.withOut(outps) {
        Console.withErr(errps) {
          cozy.Cozy.main(Array(
            "modeler-scala",
            input.toString,
            "--save",
            out.toString.toString,
            "--cncf-version",
            "0.5.2-SNAPSHOT",
            "--cozy-generator-version",
            org.simplemodeling.cozy.BuildInfo.version,
            "--component-version",
            "0.0.1-SNAPSHOT",
            "--cncf-runtime-descriptor",
            test_cncf_runtime_descriptor.toString,
            "--cncf-runtime-descriptor-sha256",
            _sha256(test_cncf_runtime_descriptor),
            "--generation-source-identity",
            sourceidentity
          ))
        }
      }
    } finally {
      outps.close()
      errps.close()
    }
    outbuffer.toString(StandardCharsets.UTF_8.name()) + "\n" + errbuffer.toString(StandardCharsets.UTF_8.name())
  }

  protected def test_cncf_runtime_descriptor: Path =
    Path.of(sys.props("user.dir")).toAbsolutePath.normalize().resolve("src/test/resources/cncf/runtime-with-predefined-results.yaml")

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").
      digest(Files.readAllBytes(path)).
      map(byte => f"${byte & 0xff}%02x").
      mkString

  protected def write_file(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, content)
  }

  protected def delete_recursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally
        stream.close()
    }
  }

  protected def count_token(content: String, token: String): Int = {
    @annotation.tailrec
    def _go_(index: Int, acc: Int): Int = {
      val i = content.indexOf(token, index)
      if (i < 0)
        acc
      else
        _go_(i + token.length, acc + 1)
    }
    _go_(0, 0)
  }

  protected def tree_snapshot(root: Path): Vector[(String, String)] = {
    if (!Files.exists(root))
      Vector.empty
    else {
      Files.walk(root).iterator().asScala
        .filter(path => Files.isRegularFile(path))
        .map { path =>
          val rel = root.relativize(path).toString
          val content = Files.readString(path)
          (rel, content)
        }
        .toVector
        .sortBy(_._1)
    }
  }
}
