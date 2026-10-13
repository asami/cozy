package cozy.modeler

import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.collection.JavaConverters._
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.reporters.StoreReporter
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import CmlCapabilitySource._
import CmlCapabilityGenerationContext._
import CmlCapabilityGenerationSpec._
import CmlSemanticFoundation.Present

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityGenerationProvenanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Generated pure Scala Capability metadata" should {
    "carry the exact R11 JSON across the supported compiler boundary" which {
      "C64G21 compile actual generated files and return exact compact JSON through the pure accessor" in {
        _with_temp_dir { directory =>
          Given("the actual generated projections and explicitly selected Scala carrier paths and symbols")
          val generated = _generate(_inputs)
          val batch = _right(CmlCapabilityGenerationScala.render(generated, _selections(generated)))
          val output = directory.resolve("output")
          When("the files are written and the existing test compiler compiles and loads their pure accessors")
          val paths = _right(CmlCapabilityGenerationScala.write(output, batch))
          val values = _compile_accessors(paths, batch, directory.resolve("classes"))
          Then("every accessor returns its exact generated compact JSON")
          values shouldBe batch.artifacts.map(artifact => generated.projections.find(_.snapshotKey == artifact.selection.key).get.projectionJson)
          paths.map(output.relativize(_).toString.replace(File.separatorChar, '/')) shouldBe batch.artifacts.map(_.selection.path)
          values.map(Json.parse) shouldBe batch.artifacts.map(artifact => generated.projections.find(_.snapshotKey == artifact.selection.key).get.json)
        }
      }

      "C64G22 compile hostile and larger-than-JVM-constant literals without splitting a supplementary pair" in {
        _with_temp_dir { directory =>
          Given("a validated descriptive literal containing quotes, backslashes, a literal Unicode escape, Japanese, supplementary Unicode and more than 64k characters")
          val documents = _documents(_inputs)
          val definition = documents.head.model.componentCapabilities.head
          val prefix = "literal:\"\\u0041\\日本語|="
          val template = _store(_project(_replace_definition(documents, definition.copy(description = Present("boundary:"))))).projectionJson
          val before = template.indexOf("boundary:")
          val encodedprefixlength = Json.stringify(JsString(prefix)).length - 2
          val padding = "x" * ((1999 - (before + encodedprefixlength) % 2000 + 2000) % 2000)
          val literal = prefix + padding + "🙂" + ("日本語\\\"|=🙂" * 10000) + ":end"
          val generated = _project(_replace_definition(documents, definition.copy(description = Present(literal))))
          val batch = _right(CmlCapabilityGenerationScala.render(generated, _selections(generated)))
          val output = directory.resolve("output")
          When("the segmented real carriers are written, compiled and their pure accessors invoked")
          val paths = _right(CmlCapabilityGenerationScala.write(output, batch))
          val values = _compile_accessors(paths, batch, directory.resolve("classes"))
          Then("compiler diagnostics have not been swallowed and every JSON value returns the complete original literal")
          literal.length should be > 65535
          before should be >= 0
          val json = _store(generated).projectionJson
          val supplementary = json.indexOf("🙂")
          supplementary % 2000 shouldBe 1999
          values shouldBe batch.artifacts.map(artifact => generated.projections.find(_.snapshotKey == artifact.selection.key).get.projectionJson)
          values.zip(batch.artifacts).filter(_._2.selection.key.scope == _primary_scope).foreach { case (value, _) =>
            val definitionjson = (Json.parse(value) \ "capabilities").as[Vector[JsObject]].head
            (definitionjson \ "description" \ "value").as[String] shouldBe literal
          }
        }
      }
    }

    "require complete explicit output ownership" which {
      "C64G23 reject unknown, omitted, repeated and malformed output selections before publication" in {
        Given("real generated snapshots with omitted, unknown, repeated, noncanonical path and invalid package/object selections")
        val generated = _generate(_inputs)
        val selections = _selections(generated)
        val first = selections.head
        val variants = Vector(selections.tail,
          selections.updated(0, first.copy(key = first.key.copy(scope = SourceScope("unknown", "model/unknown.cml")))),
          selections :+ first,
          selections.updated(0, first.copy(path = "../Outside.scala")), selections.updated(0, first.copy(path = "source/Outside.scala")),
          selections.updated(0, first.copy(path = "target/../Outside.scala")), selections.updated(0, first.copy(path = "target\\Outside.scala")),
          selections.updated(0, first.copy(path = "target/output.json")), selections.updated(0, first.copy(packagename = Vector.empty)),
          selections.updated(0, first.copy(packagename = Vector("bad-name"))), selections.updated(0, first.copy(objectname = "3Bad"))) ++
          Vector("class", "forSome", "macro", "enum", "given", "then", "using", "end", "extension", "inline", "opaque", "open", "transparent", "derives", "export")
            .flatMap(keyword => Vector(selections.updated(0, first.copy(objectname = keyword)), selections.updated(0, first.copy(packagename = Vector(keyword)))))
        When("the renderer validates each complete batch")
        val results = variants.map(x => CmlCapabilityGenerationScala.render(generated, x))
        Then("every selection failure returns structured issues and no publishable RenderedBatch")
        results.foreach { result => result.isLeft shouldBe true; result.left.get.nonEmpty shouldBe true }
        results.head.left.get.head.kind shouldBe IssueKind.MissingCoverage
        results(1).left.get.head.kind shouldBe IssueKind.UnknownSnapshot
        results(2).left.get.exists(_.kind == IssueKind.DuplicateSnapshot) shouldBe true
      }

      "C64G24 reject conflicting paths and qualified symbols with both snapshot sources" in {
        Given("distinct real snapshots explicitly selecting the same output path or the same qualified object")
        val generated = _generate(_inputs)
        val selections = _selections(generated)
        val pathconflict = selections.updated(1, selections(1).copy(path = selections.head.path))
        val symbolconflict = selections.updated(1, selections(1).copy(packagename = selections.head.packagename, objectname = selections.head.objectname))
        When("the renderer checks those whole-batch claims")
        val results = Vector(pathconflict, symbolconflict).map(x => CmlCapabilityGenerationScala.render(generated, x))
        Then("each conflict preserves both explicit sources rather than choosing one artifact")
        results.foreach { result =>
          result.isLeft shouldBe true
          val issue = result.left.get.find(_.kind == IssueKind.OutputConflict).get
          issue.source shouldBe Some(generated.projections(1).context.source)
          issue.relatedsources shouldBe Vector(generated.projections.head.context.source)
        }
      }

      "C64G25 publish selected new files idempotently and reject unsafe or different existing paths before writes" in {
        _with_temp_dir { directory =>
          Given("a real rendered batch, an unselected sentinel and roots with different-content, nonregular or symlink paths")
          val generated = _generate(_inputs)
          val batch = _right(CmlCapabilityGenerationScala.render(generated, _selections(generated)))
          val clean = directory.resolve("clean")
          val sentinel = _write(clean.resolve("target/unselected.txt"), "Keep unselected bytes.")
          val outside = _write(directory.resolve("outside/sentinel.txt"), "Keep outside bytes.")
          val conflict = directory.resolve("conflict")
          val existing = _write(conflict.resolve(batch.artifacts.last.selection.path), "Existing different bytes.")
          val nonregular = directory.resolve("nonregular")
          Files.createDirectories(nonregular.resolve(batch.artifacts.last.selection.path))
          val linked = directory.resolve("linked")
          Files.createDirectories(linked.resolve("target"))
          Files.createSymbolicLink(linked.resolve("target/generated"), directory.resolve("outside"))
          val linkedroot = directory.resolve("linked-root")
          Files.createSymbolicLink(linkedroot, clean)
          val linkedtarget = directory.resolve("linked-target")
          Files.createDirectories(linkedtarget.resolve("target/generated"))
          Files.createSymbolicLink(linkedtarget.resolve(batch.artifacts.last.selection.path), outside)
          When("publication writes the clean batch twice and checks all blocked batches")
          val first = CmlCapabilityGenerationScala.write(clean, batch)
          val second = CmlCapabilityGenerationScala.write(clean, batch)
          val failures = Vector(conflict, nonregular, linked, linkedroot, linkedtarget).map(x => CmlCapabilityGenerationScala.write(x, batch))
          Then("new selected files are exact and idempotent while blocked batches preserve sentinels and publish nothing")
          first shouldBe second
          _right(first).zip(batch.artifacts).foreach { case (path, artifact) => Files.readString(path, StandardCharsets.UTF_8) shouldBe artifact.content }
          failures.foreach { failure => failure.isLeft shouldBe true; failure.left.get.writtenpaths shouldBe Vector.empty; failure.left.get.issues.nonEmpty shouldBe true }
          failures.head.left.get.issues.head.kind shouldBe IssueKind.OutputConflict
          Files.readString(existing, StandardCharsets.UTF_8) shouldBe "Existing different bytes."
          Files.readString(sentinel, StandardCharsets.UTF_8) shouldBe "Keep unselected bytes."
          Files.readString(outside, StandardCharsets.UTF_8) shouldBe "Keep outside bytes."
          Vector(conflict, nonregular, linkedtarget).foreach(x => Files.exists(x.resolve(batch.artifacts.head.selection.path)) shouldBe false)
          val stream = Files.walk(clean)
          try stream.iterator().asScala.exists(_.getFileName.toString.startsWith(".capability-")) shouldBe false
          finally stream.close()
        }
      }
    }

    "participate in the accepted Phase 63.1 product provenance contract" which {
      "C64G26 aggregate real two-CML generated carriers from V1 captures into a package-valid V2 union" in {
        _with_temp_dir { directory =>
          Given("two unchanged CML files copied to canonical project paths, actually parsed/generated and captured as delegated V1 outputs")
          val fixture = _fixture(directory)
          When("the existing aggregate API consumes the installed real generated outputs")
          val aggregate = GenerationProvenance.aggregateForPackaging(fixture.delegated.map(_input), fixture.project)
          val packaged = _package_valid(fixture.project)
          Then("canonical sources and the exact generated Scala artifact union are package-admissible")
          aggregate.sources.map(_.identity) shouldBe Vector(_secondary_scope.path, _primary_scope.path)
          aggregate.artifacts.map(_.path) shouldBe Vector("target/generated/Archive.scala", "target/generated/Remote.scala", "target/generated/Store.scala")
          aggregate.artifacts shouldBe fixture.delegated.flatMap(_.manifest.artifacts).sortBy(_.path)
          packaged shouldBe aggregate
          aggregate.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
          aggregate.sources.map(_.sha256) shouldBe fixture.delegated.sortBy(_.manifest.inputs.sourceIdentity).map(_.manifest.inputs.sourceSha256)
          fixture.delegated.foreach { delegated =>
            delegated.generated.projections.foreach(_.context.source.sourceRevision should startWith("header:"))
            delegated.manifest.artifacts.foreach(x => Files.isRegularFile(fixture.project.resolve(x.path)) shouldBe true)
          }
        }
      }

      "C64G27 preserve the actual aggregate contract in both delegated input orders" in {
        _with_temp_dir { directory =>
          Given("two real generated delegated outputs installed into their owning project")
          val fixture = _fixture(directory)
          val inputs = fixture.delegated.map(_input)
          When("the accepted aggregator consumes each explicit order")
          val normal = GenerationProvenance.aggregateForPackaging(inputs, fixture.project)
          val reversed = GenerationProvenance.aggregateForPackaging(inputs.reverse, fixture.project)
          Then("sources, artifacts, output digest and serialized V2 evidence are identical")
          reversed.sources shouldBe normal.sources
          reversed.artifacts shouldBe normal.artifacts
          reversed.generatedOutputDigest shouldBe normal.generatedOutputDigest
          reversed.toJsonString shouldBe normal.toJsonString
          _package_valid(fixture.project) shouldBe reversed
        }
      }

      "C64G28 retain direct V1 package admission and legacy one-source V2 rebinding" in {
        _with_temp_dir { directory =>
          Given("one real canonical CML source with actually generated delegated carriers and installed direct output")
          val project = directory.resolve("project")
          _copy_sources(project)
          val delegated = _delegate(directory.resolve("delegate"), project, _primary_scope)
          _install(project, delegated)
          val snapshot = GenerationProvenance.requireSourceSnapshot(project.resolve(_primary_scope.path), _primary_scope.path)
          val direct = GenerationProvenance.write(project, snapshot, _provenance_inputs(snapshot))
          When("the accepted packaging entry validates direct V1 and the legacy rebind entry consumes the delegated output")
          val directvalid = _package_valid(project)
          val rebound = GenerationProvenance.rebindForPackaging(delegated.provenance, delegated.root, project)
          val reboundvalid = _package_valid(project)
          Then("both accepted contracts retain the actual generated artifact set")
          directvalid shouldBe direct
          rebound.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
          rebound.sources.map(_.identity) shouldBe Vector(_primary_scope.path)
          rebound.artifacts shouldBe direct.artifacts
          reboundvalid shouldBe rebound
        }
      }

      "C64G29 reject duplicate canonical CML source inputs without a V2 winner" in {
        _with_temp_dir { directory =>
          Given("the same real generated source selected twice as independently explicit delegated inputs")
          val project = directory.resolve("project")
          _copy_sources(project)
          val first = _delegate(directory.resolve("delegate-one"), project, _primary_scope)
          val second = _delegate(directory.resolve("delegate-two"), project, _primary_scope)
          _install(project, first)
          When("the accepted aggregator consumes both duplicate canonical source claims")
          val error = intercept[Exception] { GenerationProvenance.aggregateForPackaging(Vector(_input(first), _input(second)), project) }
          Then("the actual SourceAmbiguous diagnostic retains the source identity and publishes no V2 manifest")
          error.getMessage should include("GENERATION_PROVENANCE_SOURCE_AMBIGUOUS")
          error.getMessage should include(_primary_scope.path)
          Files.exists(project.resolve(GenerationProvenance.METADATA_PATH)) shouldBe false
        }
      }

      "C64G30 reject actual generator, target and backend provenance input mismatches" in {
        _with_temp_dir { directory =>
          Given("actual V1 captures with one altered explicit generator, target or backend tuple field each")
          val project = directory.resolve("project")
          _copy_sources(project)
          val first = _delegate(directory.resolve("delegate-primary"), project, _primary_scope)
          val variants: Vector[GenerationProvenance.Inputs => GenerationProvenance.Inputs] = Vector(
            inputs => inputs.copy(cozyGeneratorVersion = "other-generator:r2"), inputs => inputs.copy(cncfTargetVersion = "other-target:r3"),
            inputs => inputs.copy(simpleModelerBackendVersion = "other-backend:r4"))
          val seconds = variants.zipWithIndex.map { case (alter, index) => _delegate(directory.resolve(s"delegate-secondary-$index"), project, _secondary_scope, alter = alter) }
          _install(project, first)
          _install(project, seconds.head)
          When("the accepted aggregator consumes each independently valid conflicting tuple")
          val errors = seconds.map(second => intercept[Exception] { GenerationProvenance.aggregateForPackaging(Vector(_input(first), _input(second)), project) })
          Then("every actual InputMismatch fails before merged evidence without version coercion")
          errors.foreach(_.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH"))
          Files.exists(project.resolve(GenerationProvenance.METADATA_PATH)) shouldBe false
        }
      }

      "C64G31 reject different real generated bytes at one installed output path" in {
        _with_temp_dir { directory =>
          Given("two independently generated and valid captured carriers explicitly claiming one path with different JSON")
          val fixture = _fixture(directory, collision = true)
          val collisionpath = "target/generated/Collision.scala"
          val claims = fixture.delegated.map(x => x.manifest.artifacts.find(_.path == collisionpath).get)
          When("the accepted aggregate API evaluates the actual generated artifact claims")
          val error = intercept[Exception] { GenerationProvenance.aggregateForPackaging(fixture.delegated.map(_input), fixture.project) }
          Then("the actual OutputConflict names the conflicting path and publishes no winner")
          claims.map(_.sha256).distinct.size shouldBe 2
          error.getMessage should include("GENERATION_PROVENANCE_OUTPUT_CONFLICT")
          error.getMessage should include(collisionpath)
          Files.exists(fixture.project.resolve(GenerationProvenance.METADATA_PATH)) shouldBe false
        }
      }

      "C64G32 reject missing real output or manifest and mismatched installed generated artifacts" in {
        _with_temp_dir { directory =>
          Given("real captures with a missing delegated manifest, missing generated artifact or different installed generated bytes")
          val missingmanifest = _fixture(directory.resolve("manifest"))
          Files.delete(missingmanifest.delegated.head.provenance)
          val missingoutput = _fixture(directory.resolve("output"))
          Files.delete(missingoutput.delegated.head.root.resolve(missingoutput.delegated.head.manifest.artifacts.head.path))
          val mismatched = _fixture(directory.resolve("installed"))
          val artifact = mismatched.delegated.head.manifest.artifacts.head.path
          _write(mismatched.project.resolve(artifact), "Different installed generated bytes.")
          When("the accepted aggregator consumes each explicit unavailable or mismatched input")
          val errors = Vector(missingmanifest, missingoutput, mismatched).map(fixture => intercept[Exception] {
            GenerationProvenance.aggregateForPackaging(fixture.delegated.map(_input), fixture.project)
          })
          Then("the actual missing/output diagnostics name their source or path and no reconstructed V2 succeeds")
          errors.head.getMessage should include("GENERATION_PROVENANCE_MISSING")
          errors.head.getMessage should include(missingmanifest.delegated.head.provenance.toString)
          errors(1).getMessage should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
          errors(2).getMessage should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
          errors(2).getMessage should include(artifact)
          Vector(missingmanifest, missingoutput, mismatched).foreach(fixture => Files.exists(fixture.project.resolve(GenerationProvenance.METADATA_PATH)) shouldBe false)
        }
      }
    }
  }

  private final case class Delegated(provenance: Path, root: Path, manifest: GenerationProvenance.Manifest,
    generated: CmlCapabilityGeneration.Generated)
  private final case class Fixture(project: Path, delegated: Vector[Delegated])
  private def _fixture(directory: Path, collision: Boolean = false): Fixture = {
    val project = directory.resolve("project")
    _copy_sources(project)
    val delegated = Vector(_delegate(directory.resolve("delegate-primary"), project, _primary_scope, collision),
      _delegate(directory.resolve("delegate-secondary"), project, _secondary_scope, collision))
    delegated.foreach(_install(project, _))
    Fixture(project, delegated)
  }
  private def _copy_sources(project: Path): Unit = Vector(_primary_scope, _secondary_scope).foreach { scope =>
    val target = project.resolve(scope.path)
    Files.createDirectories(target.getParent)
    Files.copy(Paths.get("src/test/resources/modeler").resolve(Paths.get(scope.path).getFileName), target)
  }
  private def _delegate(output: Path, project: Path, scope: SourceScope, collision: Boolean = false,
    alter: GenerationProvenance.Inputs => GenerationProvenance.Inputs = identity): Delegated = {
    val original = project.resolve(scope.path)
    val source = SourceInput(scope, KaleidoxModel.load(KaleidoxConfig.default, original.toFile))
    val contexts = _contexts.filter(_.source.path == scope.path)
    val generated = _right(CmlCapabilityGeneration.generate(Vector(source), CmlCapabilityValidationScope.ScopeInput.Ready(_scope), contexts))
    val selections = _selections(generated)
    val selected = if (collision) selections.updated(0, selections.head.copy(path = "target/generated/Collision.scala", objectname = "CollisionMetadata")) else selections
    val batch = _right(CmlCapabilityGenerationScala.render(generated, selected))
    _right(CmlCapabilityGenerationScala.write(output, batch))
    val snapshot = GenerationProvenance.requireSourceSnapshot(original, scope.path)
    val manifest = GenerationProvenance.write(output, snapshot, alter(_provenance_inputs(snapshot)))
    Delegated(output.resolve(GenerationProvenance.METADATA_PATH), output, manifest, generated)
  }
  private def _provenance_inputs(snapshot: GenerationProvenance.SourceSnapshot): GenerationProvenance.Inputs = GenerationProvenance.Inputs(
    cncfTargetVersion = "explicit-target:r11", runtimeDescriptorSha256 = "a" * 64,
    cozyGeneratorVersion = org.simplemodeling.cozy.BuildInfo.version, simpleModelerBackendVersion = "explicit-backend:r4",
    simpleModelingModelVersion = "explicit-model:r5", sourceIdentity = snapshot.identity, sourceSha256 = snapshot.sha256)
  private def _input(delegated: Delegated): GenerationProvenance.DelegatedInput = GenerationProvenance.DelegatedInput(delegated.provenance, delegated.root)
  private def _install(project: Path, delegated: Delegated): Unit = delegated.manifest.artifacts.foreach(artifact =>
    _write(project.resolve(artifact.path), Files.readString(delegated.root.resolve(artifact.path), StandardCharsets.UTF_8)))
  private def _package_valid(project: Path): GenerationProvenance.ValidatedManifest = GenerationProvenance.requireValidForPackaging(
    project.resolve(GenerationProvenance.METADATA_PATH), project, Some("explicit-target:r11"), Some(org.simplemodeling.cozy.BuildInfo.version))
  private def _compile_accessors(paths: Vector[Path], batch: CmlCapabilityGenerationScala.RenderedBatch, classes: Path): Vector[String] = {
    Files.createDirectories(classes)
    val settings = new Settings
    val library = Paths.get(classOf[scala.Option[_]].getProtectionDomain.getCodeSource.getLocation.toURI)
    settings.classpath.value = library.toString + File.pathSeparator + classes.toString
    settings.outputDirs.setSingleOutput(classes.toString)
    settings.encoding.value = "UTF-8"
    val reporter = new StoreReporter
    val compiler = new Global(settings, reporter)
    val run = new compiler.Run
    run.compile(paths.map(_.toString).toList)
    if (reporter.hasErrors) fail(reporter.infos.map(info => s"${info.pos}: ${info.msg}").mkString("Generated Scala compiler errors:\n", "\n", ""))
    val loader = new URLClassLoader(Array(classes.toUri.toURL), getClass.getClassLoader)
    try batch.artifacts.map { artifact =>
      val name = (artifact.selection.packagename :+ artifact.selection.objectname).mkString(".") + "$"
      val moduleclass = loader.loadClass(name)
      val module = moduleclass.getField("MODULE$").get(null)
      moduleclass.getMethod("projectionJson").invoke(module).asInstanceOf[String]
    } finally loader.close()
  }
  private def _write(path: Path, content: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }
  private def _with_temp_dir[A](body: Path => A): A = {
    val root = Paths.get("target/cozy-test/work/cml-capability-generation").toAbsolutePath.normalize()
    Files.createDirectories(root)
    val directory = Files.createTempDirectory(root, "generation-")
    try body(directory) finally _delete_tree(directory)
  }
  private def _delete_tree(path: Path): Unit = if (Files.exists(path)) {
    val stream = Files.walk(path)
    try stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(Files.deleteIfExists(_))
    finally stream.close()
  }
}
