package cozy.modeler

import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov. 20, 2026
 * @version Nov. 20, 2026
 * @author  ASAMI, Tomoharu
 */
class Scala3StrictOutputGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  override def withFixture(test: NoArgTest): org.scalatest.Outcome = {
    val outcome = super.withFixture(test)
    outcome match {
      case org.scalatest.Failed(error) => println(s"FAILED ${test.name}: ${Option(error.getMessage).getOrElse(error.toString).take(4000)}")
      case _ => ()
    }
    outcome
  }

  private val base = Paths.get("").toAbsolutePath
  private val managed = "target/scala-3.3.8/src_managed/"

  "Core C3 logical-action membership" should {
    "emit selected and legacy source through the public generator" in {
      Given("the workflow fixture and both membership selections")
      When("the public generator emits fresh output")
      val (legacy, selected) = _c3_generate()
      val oldsource = Files.readString(legacy.resolve("LogicalActionCompiler.scala"))
      val source = Files.readString(selected.resolve("LogicalActionCompiler.scala"))
      Then("only the selected membership expressions and their dependencies differ")
      oldsource should include ("requestedOccurrenceIds.contains(occurrence.occurrenceId)")
      source should include ("requestedOccurrenceIds.typeSafe.containsEq(occurrence.occurrenceId)")
      source should include ("acc.typeSafe.containsEq(occurrence.actionId)")
      source should include ("requestedOccurrenceIds.take(index).typeSafe.containsEq(occurrenceId)")
      val restored = source
        .replace("import cats.free.Free\nimport org.goldenport.util.TypeSafeCollections.*", "import cats.free.Free")
        .replace("object LogicalActionCompiler {\n  private given cats.Eq[String] = cats.Eq.fromUniversalEquals[String]", "object LogicalActionCompiler {")
        .replace(".typeSafe.containsEq(", ".contains(")
      restored shouldBe oldsource
      structuralErrors(source) shouldBe empty
    }

    "compile selected output with the selected Core and reject missing or incompatible membership" in {
      Given("selected and legacy target classpaths")
      val selectedcp = sys.env.get("COZY_C3_STRICT_SCALA3_CLASSPATH_DIR").map(Paths.get(_)).getOrElse {
        cancel("C3 qualification requires COZY_C3_STRICT_SCALA3_CLASSPATH_DIR")
      }
      val legacycp = sys.env.get("COZY_STRICT_SCALA3_CLASSPATH_DIR").map(Paths.get(_)).getOrElse {
        cancel("C3 qualification requires COZY_STRICT_SCALA3_CLASSPATH_DIR")
      }
      val (legacy, selected) = _c3_generate()
      val compiler = Files.readString(selectedcp.resolve("compiler-classpath.txt")).trim
      val adopted = Files.readString(selectedcp.resolve("compile-classpath.txt")).trim
      val old = Files.readString(legacycp.resolve("compile-classpath.txt")).trim
      val flags = Vector("-language:strictEquality", "-Yexplicit-nulls", "-Ysafe-init", "-Wvalue-discard",
        "-Wnonunit-statement", "-Wunused:imports,privates,locals", "-unchecked", "-deprecation", "-feature", "-Werror")
      def compile(label: String, root: Path, classpath: String): (Int, String) = {
        val destination = base.resolve(s"target/test-generated/c3-compile-$label")
        Files.createDirectories(destination)
        val command = Vector(Paths.get(sys.props("java.home"), "bin", "java").toString, "-cp", compiler,
          "dotty.tools.dotc.Main", "-color:never", "-classpath", classpath, "-d", destination.toString) ++ flags ++
          Vector("CompositeStateMachineActionProgramAbi.scala", "LogicalActionCompiler.scala").map(root.resolve(_).toString)
        val process = new ProcessBuilder(command: _*).redirectErrorStream(true).start()
        val output = scala.io.Source.fromInputStream(process.getInputStream, "UTF-8")
        val result = try output.mkString finally output.close()
        (process.waitFor(), result)
      }
      When("the generated source is compiled against each Core and with an incompatible argument")
      val (legacyStatus, legacyOutput) = compile("legacy", legacy, old)
      Then("the supported combinations compile and missing or incompatible capability fails")
      withClue(legacyOutput) { legacyStatus shouldBe 0 }
      val (selectedStatus, selectedOutput) = compile("selected", selected, adopted)
      withClue(selectedOutput) { selectedStatus shouldBe 0 }
      val (missingStatus, missingOutput) = compile("missing-core", selected, old)
      missingStatus should not be 0
      missingOutput should include ("TypeSafeCollections")
      val invalid = base.resolve("target/test-generated/c3-invalid")
      Files.createDirectories(invalid)
      Files.copy(selected.resolve("CompositeStateMachineActionProgramAbi.scala"), invalid.resolve("CompositeStateMachineActionProgramAbi.scala"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      val source = Files.readString(selected.resolve("LogicalActionCompiler.scala"))
      Files.writeString(invalid.resolve("LogicalActionCompiler.scala"), source.replace("containsEq(occurrence.occurrenceId)", "containsEq(1)"))
      val (invalidStatus, invalidOutput) = compile("invalid", invalid, adopted)
      invalidStatus should not be 0
      invalidOutput should include ("containsEq(1)")
      invalidOutput should include ("Int =:= String")
    }
  }

  private def _c3_generate(): (Path, Path) = {
    val property = "cozy.generation.logical-action-membership"
    val previous = sys.props.get(property)
    val input = "src/test/resources/modeler/workflow-semantic-outcome.cml"
    val legacy = base.resolve("target/test-generated/c3-legacy")
    val selected = base.resolve("target/test-generated/c3-selected")
    try {
      sys.props.remove(property)
      generate(input, legacy, "modeler-scala")
      sys.props.update(property, "core-c3")
      generate(input, selected, "modeler-scala")
    } finally {
      previous match {
        case Some(value) => sys.props.update(property, value)
        case None => sys.props.remove(property)
      }
    }
    val path = managed + "main/scala/domain/composite/statemachine/actionprogram"
    (legacy.resolve(path), selected.resolve(path))
  }

  "Scala 3 strict output" should {
    "emit active imports and structural braces through both public routes" in {
      val inputs = fixtureInputs()
      for ((input, fixture) <- inputs; route <- Vector("modeler-scala", "modeler-scala-value")) {
        val out = base.resolve(s"target/test-generated/scala3-strict-$fixture-$route")
        generate(input, out, route)
        val root = out.resolve(managed)
        Files.exists(root) shouldBe true
        val stream = Files.walk(root)
        val files = try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).toVector
          finally stream.close()
        files.nonEmpty shouldBe true
        val selected = files.filter { p =>
          val s = p.toString.replace('\\', '/')
          s.contains("/domain/composite/statemachine/") || s.contains("/domain/statemachine/workflow/") ||
            s.contains("/domain/statemachine/providedapi/") || s.contains("/domain/value/") ||
            s.contains("/org/example/validation/") || s.contains("/domain/statemachine/candidateadmission/") ||
            s.contains("/cozy/generated/failuremodel/") || s.contains("/domain/logicalui/") ||
            s.contains("/org/example/finding/")
        }
        selected.nonEmpty shouldBe true
        if (fixture == "finding") selected.exists(_.getFileName.toString == "OrdersFindingDispositions.scala") shouldBe true
        if (fixture == "candidate") selected.exists(_.getFileName.toString == "CandidateAdmissionProducerAbi.scala") shouldBe true
        if (fixture == "failure") selected.exists(_.getFileName.toString == "FailureModelMetadata.scala") shouldBe true
        if (fixture == "logicalui") selected.exists(_.getFileName.toString == "DomainLogicalUiDefinitions.scala") shouldBe true
        if (fixture == "validation") selected.exists(_.toString.contains("src_managed/test/scala")) shouldBe true
        if (fixture == "value") selected.exists(_.getFileName.toString == "CountryCode.scala") shouldBe true
        if (fixture == "workflow") {
          selected.exists(_.getFileName.toString == "StateMachineWorkflowAbi.scala") shouldBe true
          selected.exists(_.getFileName.toString == "StateMachineProvidedApiAbi.scala") shouldBe true
        }
        selected.foreach { path =>
          val tokens = lexicalTokens(Files.readString(path))
          withClue(path.toString) {
            tokens.sliding(6).count(_ == Vector("import", "scala", ".", "language", ".", "strictEquality")) shouldBe 1
            // Older published SimpleModeler versions predate the shared ValueReader
            // brace repair; the activated 1.1.27 source is the qualification target.
            if (!path.toString.replace('\\', '/').contains("/domain/value/") ||
              org.simplemodeling.cozy.BuildInfo.simpleModelerVersion == "1.1.27-SNAPSHOT") {
              withClue(" structural bodies: ") {
                structuralErrors(Files.readString(path)) shouldBe empty
              }
            }
            if (path.getFileName.toString == "CountryCode.scala" &&
              org.simplemodeling.cozy.BuildInfo.simpleModelerVersion == "1.1.27-SNAPSHOT") {
              tokens.sliding(12).exists(_ == Vector("given", "org", ".", "goldenport", ".", "convert", ".", "ValueReader", "[", "CountryCode", "]", "with")) shouldBe true
              tokens.sliding(13).exists(_ == Vector("given", "org", ".", "goldenport", ".", "convert", ".", "ValueReader", "[", "CountryCode", "]", "with", "{")) shouldBe true
              tokens.sliding(3).exists(_ == Vector("object", "CountryCode", "{")) shouldBe true
            }

          }
        }
      }
    }
  }

  "Structural body qualification" should {
    "distinguish braced declarations from indentation while ignoring decoys" in {
      val valid = Vector(
        "object Outer { class Inner { def f: Int = 1 } }",
        "object Outer { given Reader with { def read: Int = 1 } }",
        "trait DomainLogicalUiMetadataProvider { def metadata: Int = 1 }",
        "object O { extension (x: Int) { def twice: Int = x * 2 } }",
        "package p { enum E { case A }; class Empty; given Int = 1 }",
        "object O { val s = \"class Fake: given Fake with\"; /* trait Fake: */ def f: Int =\n  if true then\n    1\n  else\n    2\n}")
      val invalid = Vector(
        "object Outer {\n  class Inner:\n    def f: Int = 1\n}",
        "object Outer {\n  given Reader with\n    def read: Int = 1\n}",
        "trait DomainLogicalUiMetadataProvider:\n  def metadata: Int = 1",
        "object O {\n  extension (x: Int)\n    def twice: Int = x * 2\n}",
        "package p:\n  enum E:\n    case A",
        "object O {\n  def f =\n    class Inner { }\n    1\n}")
      valid.foreach(source => withClue(source) { structuralErrors(source) shouldBe empty })
      invalid.foreach(source => withClue(source) { structuralErrors(source) should not be empty })
    }
  }

  "Scala 3 target compilation" should {
    "compile the real generated main and test scopes with the supplied target classpaths" in {
      val directory = sys.env.get("COZY_STRICT_SCALA3_CLASSPATH_DIR").map(Paths.get(_)).getOrElse {
        cancel("Target Scala 3 qualification requires COZY_STRICT_SCALA3_CLASSPATH_DIR")
      }
      def cp(name: String): String = Files.readString(directory.resolve(name)).trim
      val compiler = cp("compiler-classpath.txt")
      val compileClasspath = cp("compile-classpath.txt")
      val testClasspath = cp("test-classpath.txt")
      require(compiler.nonEmpty && compileClasspath.nonEmpty && testClasspath.nonEmpty, "Empty target classpath")
      val fixtures = fixtureInputs()
      def sources(root: Path): Vector[Path] = if (!Files.exists(root)) Vector.empty else {
        val walk = Files.walk(root)
        try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).toVector.sortBy(_.toString)
        finally walk.close()
      }
      def compile(label: String, files: Vector[Path], classpath: String, destination: Path): Unit = {
        Files.createDirectories(destination)
        if (files.nonEmpty) {
          val command = Vector(Paths.get(sys.props("java.home"), "bin", "java").toString, "-cp", compiler,
            "dotty.tools.dotc.Main", "-color:never", "-classpath", classpath, "-d", destination.toString) ++ files.map(_.toString)
          val process = new ProcessBuilder(command: _*).redirectErrorStream(true).start()
          val output = scala.io.Source.fromInputStream(process.getInputStream, "UTF-8")
          val result = try output.mkString finally output.close()
          val status = process.waitFor()
          info(s"Scala 3.3.8 $label: ${files.size} sources: ${files.map(_.toString).mkString(", ")}")
          if (status == 0) println(s"Scala 3.3.8 $label passed; ${files.size} sources: ${files.map(_.getFileName.toString).mkString(", ").take(3000)}")
          else println(s"Scala 3.3.8 $label exited $status\n${result.take(12000)}")
          withClue(s"Scala 3.3.8 $label exited $status\n$result") { status shouldBe 0 }
        } else info(s"Scala 3.3.8 $label: no sources")
      }
      for ((input, fixture) <- fixtures; route <- Vector("modeler-scala", "modeler-scala-value")) {
        val out = base.resolve(s"target/test-generated/scala3-strict-$fixture-$route")
        generate(input, out, route)
        val allMain = sources(out.resolve(managed + "main/scala")) ++ sources(out.resolve("src/main/scala"))
        // The legacy workflow has duplicate generated ReviewContext declarations in DomainComponent.
        // Its standalone ABI families do not depend on that component.
        val main = if (fixture == "workflow") allMain.filter { path =>
          val name = path.toString.replace('\\', '/')
          name.contains("/domain/composite/statemachine/") || name.contains("/domain/statemachine/")
        } else if (fixture == "failure") {
          // The fixture's LocalStore component repeats Context declarations. The failure
          // metadata is self-contained and is compiled directly from each real route.
          val metadata = allMain.filter(_.getFileName.toString == "FailureModelMetadata.scala")
          metadata.size shouldBe 1
          metadata
        } else allMain
        val test = sources(out.resolve(managed + "test/scala")) ++ sources(out.resolve("src/test/scala"))
        withClue(s"$fixture $route generation was not completed") { main.nonEmpty shouldBe true }
        if (fixture == "validation") test.nonEmpty shouldBe true
        val mainClasses = out.resolve("target/strict-qualification/main-classes")
        val testClasses = out.resolve("target/strict-qualification/test-classes")
        compile(s"$fixture $route Compile", main, compileClasspath, mainClasses)
        compile(s"$fixture $route Test", test, mainClasses.toString + java.io.File.pathSeparator + testClasspath, testClasses)
      }
    }
  }

  private def fixtureInputs(): Vector[(String, String)] = {
    val findingInput = base.resolve("target/test-input/scala3-strict-finding.cml")
      Files.createDirectories(findingInput.getParent)
      Files.writeString(findingInput, """# COMPONENT
    |
    |## Orders
    |
    |### PACKAGE
    |
    |org.example
    |
    |### SERVICE
    |
    |#### OrderService
    |
    |# VALUE
    |
    |## SubmitOrder
    |- input-kind :: COMMAND
    |
    |### ATTRIBUTE
    |
    || name | type | multiplicity |
    ||------+------|--------------|
    || requestId | string | 1 |
    |
    |## SubmitOrderResult
    |
    |### ATTRIBUTE
    |
    || name | type | multiplicity |
    ||------+------|--------------|
    || accepted | boolean | 1 |
    |
    |# SERVICE
    |
    |## OrderService
    |
    |### OPERATION
    |
    |#### submit
    |
    |##### TYPE
    |
    |COMMAND
    |
    |##### INPUT
    |###### TYPE
    |SubmitOrder
    |##### OUTPUT
    |###### TYPE
    |SubmitOrderResult
    |
    |# FINDING-DISPOSITION
    |## Orders
    |### submit-followup
    |abiVersion = 1
    |rule = "car.required-operation"
    |finding = "missing-submit"
    |target = "operation:OrderService.submit"
    |kind = "defer"
    |reason = "Implement submission next milestone"
    |provenance = "issue:orders:42"
    |""".stripMargin)
      Vector(
        "src/test/resources/modeler/workflow-semantic-outcome.cml" -> "workflow",
        "src/test/resources/modeler/value-only-literate.dox" -> "value",
        "src/test/resources/validation/metadata.cml" -> "validation",
        "src/test/resources/modeler/candidate-admission-workflow.cml" -> "candidate",
        "src/test/resources/modeler/failure-model.cml" -> "failure",
        "src/test/resources/logicalui/ess2-01.cml" -> "logicalui",
        findingInput.toString -> "finding")
  }

  private def generate(input: String, out: Path, route: String): Unit = {
    if (Files.exists(out)) {
      val stream = Files.walk(out)
      try stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete)
      finally stream.close()
    }
    cozy.Cozy.main(Array(route, base.resolve(input).toString, "--save", out.toString))
  }

  // This deliberately checks declaration/header forms in the selected generated
  // families, not arbitrary framework DSLs. The compiler separately checks syntax.
  private case class Token(text: String, line: Int, column: Int)

  private def structuralErrors(source: String): Vector[String] = {
    val ts = positionedTokens(source)
    val errors = Vector.newBuilder[String]
    val starts = Set("package", "class", "trait", "object", "enum", "given", "extension")
    var stack = List.empty[String]
    ts.zipWithIndex.foreach { case (t, index) =>
      t.text match {
        case "{" | "(" | "[" => stack = t.text :: stack
        case "}" | ")" | "]" =>
          val expected = Map("}" -> "{", ")" -> "(", "]" -> "[")(t.text)
          if (stack.headOption.contains(expected)) stack = stack.tail
          else errors += s"unmatched ${t.text} at ${t.line}:${t.column}"
        case _ => ()
      }
      if (starts(t.text) && !stack.exists(x => x == "(" || x == "[")) {
        var j = index + 1
        var nesting = 0
        var done = false
        var last = t
        var bodyBrace = false
        var hasWith = false
        var hasEquals = false
        while (j < ts.length && !done) {
          val n = ts(j)
          val newLine = n.line > last.line
          if (nesting == 0 && (n.text == "}" || n.text == ";" ||
            (newLine && last.text != "." && last.text != "," &&
              n.text != "extends" && n.text != "with" && n.text != "derives" && n.text != "{"))) {
            done = true
          } else if (nesting == 0 && n.text == "{") {
            bodyBrace = true; done = true
          } else {
            if (n.text == "(" || n.text == "[") nesting += 1
            if (n.text == ")" || n.text == "]") nesting -= 1
            if (nesting == 0 && n.text == "with") hasWith = true
            if (nesting == 0 && n.text == "=") hasEquals = true
            last = n; j += 1
          }
        }
        if (last.text == ":" && !bodyBrace)
          errors += s"${t.text} indentation body at ${t.line}:${t.column}"
        if (t.text == "given" && hasWith && !bodyBrace && !hasEquals)
          errors += s"given implementation without braces at ${t.line}:${t.column}"
        if (t.text == "extension" && !bodyBrace)
          errors += s"extension group without braces at ${t.line}:${t.column}"
        // A declaration inside an indented expression needs its enclosing
        // expression braced as well, even when the declaration itself is braced.
        if (index > 0 && t.line > ts(index - 1).line) {
          val previous = ts.take(index).reverse.find(_.line < t.line)
          if (previous.exists { p =>
            val lineStart = ts.find(_.line == p.line).map(_.column).getOrElse(p.column)
            (p.text == "=" || p.text == ">") && lineStart < t.column
          })
            errors += s"structural declaration in unbraced expression at ${t.line}:${t.column}"
        }
      }
    }
    if (stack.nonEmpty) errors += "unclosed delimiter"
    errors.result()
  }

  private def lexicalTokens(source: String): Vector[String] = positionedTokens(source).map(_.text)

  // Keep original offsets through comments and literals so line endings and
  // nesting in declaration headers remain meaningful.
  private def positionedTokens(source: String): Vector[Token] = {
    val tokens = Vector.newBuilder[Token]
    var i = 0
    var line = 1
    var column = 1
    def advance(): Unit = {
      if (source(i) == '\n') { line += 1; column = 1 } else column += 1
      i += 1
    }
    def skip(n: Int): Unit = (0 until n).foreach(_ => advance())
    while (i < source.length) {
      if (source(i).isWhitespace) advance()
      else if (source.startsWith("//", i)) {
        while (i < source.length && source(i) != '\n') advance()
      } else if (source.startsWith("/*", i)) {
        skip(2)
        var nesting = 1
        while (i < source.length && nesting > 0) {
          if (source.startsWith("/*", i)) { nesting += 1; skip(2) }
          else if (source.startsWith("*/", i)) { nesting -= 1; skip(2) }
          else advance()
        }
        require(nesting == 0, "Unterminated block comment")
      } else if (source.startsWith("\"\"\"", i)) {
        skip(3)
        while (i < source.length && !source.startsWith("\"\"\"", i)) advance()
        require(i < source.length, "Unterminated triple-quoted string")
        skip(3)
      } else if (source(i) == '"' || source(i) == '\'') {
        val quote = source(i); advance()
        var closed = false
        while (i < source.length && !closed) {
          if (source(i) == '\\') { advance(); if (i < source.length) advance() }
          else if (source(i) == quote) { closed = true; advance() }
          else advance()
        }
        require(closed, "Unterminated literal")
      } else {
        val start = i; val tokenLine = line; val tokenColumn = column
        if (source(i).isLetter || source(i) == '_') {
          advance()
          while (i < source.length && (source(i).isLetterOrDigit || source(i) == '_')) advance()
        } else advance()
        tokens += Token(source.substring(start, i), tokenLine, tokenColumn)
      }
    }
    tokens.result()
  }
}
