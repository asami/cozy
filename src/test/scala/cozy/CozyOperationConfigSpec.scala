package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 30, 2026
 *  version Jul.  1, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyOperationConfigSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy operation config" should {
    "use pdf defaults from canonical hyphenated keys in conf/cozy config" in {
      _with_temp_dir("cozy-pdf-defaults") { dir =>
        Given("a project config with canonical PDF option names")
        _write(
          dir.resolve("conf/cozy/config.yaml"),
          """pdf:
            |  latex-format: business
            |  latex-author: 浅海
            |""".stripMargin
        )

        When("Cozy resolves operation arguments for SmartDox PDF")
        val result = CozyOperationConfig._with_pdf_defaults(List("report.dox"), dir)

        Then("the canonical config values are added as command-line defaults")
        result should contain("--latex-format")
        result should contain("business")
        result should contain("--latex-author")
        result should contain("浅海")
      }
    }

    "keep dotted PDF key compatibility" in {
      _with_temp_dir("cozy-pdf-defaults-dotted") { dir =>
        Given("a project config with legacy dotted PDF option names")
        _write(
          dir.resolve("conf/cozy/config.yaml"),
          """pdf:
            |  latex:
            |    format: business
            |""".stripMargin
        )

        When("Cozy resolves operation arguments for SmartDox PDF")
        val result = CozyOperationConfig._with_pdf_defaults(List("report.dox"), dir)

        Then("the dotted config value is still recognized")
        result should contain("--latex-format")
        result should contain("business")
      }
    }
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try {
      body(dir)
    } finally {
      _delete(dir)
    }
  }

  private def _write(path: Path, content: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
      } finally {
        stream.close()
      }
    }
}
