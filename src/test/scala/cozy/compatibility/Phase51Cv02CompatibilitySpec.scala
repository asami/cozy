package cozy.compatibility

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class Phase51Cv02CompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _cncf_target    = MavenCoordinate("org.goldenport", "goldenport-cncf_3", "0.5.1")
  private val _cozy_generator = MavenCoordinate("org.simplemodeling", "cozy_2.12", "0.3.0")
  private val _pair           = GenerationPair(_cncf_target, _cozy_generator)
  private val _owner =
    GenerationEvidenceOwner("test evidence", "src/test/resources/generation-compatibility.v1.json")
  private val _proven = GenerationCompatibilityEvidence(
    GenerationCompatibility.evidenceSchema,
    _owner,
    Vector(GenerationPairEvidence(_pair, GenerationPairStatus.Proven)),
    None
  )
  private val _unproven =
    _proven.copy(entries = Vector(GenerationPairEvidence(_pair, GenerationPairStatus.Unproven)))

  "Cozy CV-02 generation compatibility" should {
    "load and validate compatibility evidence" which {
      "parse and validate the packaged evidence resource" in {
        Given("the packaged versioned compatibility resource")
        When("the production resource loader parses it")
        val loaded = GenerationCompatibilityEvidenceLoader.load()
        Then("the exact resource is valid and the observed pair remains unproven")
        loaded.isRight shouldBe true
        loaded.toOption.get.schema shouldBe GenerationCompatibility.evidenceSchema
        loaded.toOption.get.entries should contain(
          GenerationPairEvidence(_pair, GenerationPairStatus.Unproven)
        )
        loaded.toOption.get.publishedDefault shouldBe None
      }
      "reject malformed evidence before admission" in {
        Given("evidence with an unknown field, empty owner, and a malformed coordinate")
        val malformed =
          """{"schema":"cozy.generation-compatibility.v1","evidenceOwner":{"owner":"","location":"x"},"pairs":[{"cncfTarget":{"organization":"","artifact":"a","version":"1"},"cozyGenerator":{"organization":"o","artifact":"a","version":"1"},"status":"proven"}],"extra":true}"""
        val malformedevidence = _proven.copy(schema = "cozy.generation-compatibility.v0")
        When("the production evidence parser validates it")
        val result = GenerationCompatibilityEvidenceLoader.parse(malformed, "fixture.json")
        val decision = GenerationCompatibility.admit(
          GenerationInputs(Some(_cncf_target), Some(_cozy_generator)),
          GenerationLifecycle.Development,
          malformedevidence
        )
        Then("typed deterministic diagnostics are returned")
        result.isLeft shouldBe true
        result.left.toOption.get.map(_.code).distinct should contain(
          GenerationDiagnosticCode.MalformedEvidence
        )
        result.left.toOption.get.forall(_.coordinate.nonEmpty) shouldBe true
        decision.result shouldBe GenerationAdmission.Unsupported
      }
      "reject non-string evidence fields without throwing or silently dropping malformed pairs" in {
        Given(
          "an owner with a non-string field and a mixed pair list containing non-string coordinate and status fields"
        )
        val wrongowner =
          """{"schema":"cozy.generation-compatibility.v1","evidenceOwner":{"owner":51,"location":"fixture.json"},"pairs":[{"cncfTarget":{"organization":"org.goldenport","artifact":"goldenport-cncf_3","version":"0.5.1"},"cozyGenerator":{"organization":"org.simplemodeling","artifact":"cozy_2.12","version":"0.3.0"},"status":"proven"}]}"""
        val mixedpairs =
          """{"schema":"cozy.generation-compatibility.v1","evidenceOwner":{"owner":"fixture","location":"fixture.json"},"pairs":[{"cncfTarget":{"organization":"org.goldenport","artifact":"goldenport-cncf_3","version":"0.5.1"},"cozyGenerator":{"organization":"org.simplemodeling","artifact":"cozy_2.12","version":"0.3.0"},"status":"proven"},{"cncfTarget":{"organization":"org.goldenport","artifact":"goldenport-cncf_3","version":51},"cozyGenerator":{"organization":"org.simplemodeling","artifact":"cozy_2.12","version":"0.3.0"},"status":51}]}"""
        When("the production evidence parser validates both documents")
        val ownerresult =
          GenerationCompatibilityEvidenceLoader.parse(wrongowner, "wrong-owner.json")
        val pairresult = GenerationCompatibilityEvidenceLoader.parse(mixedpairs, "mixed-pairs.json")
        Then("both documents return typed malformed-evidence diagnostics")
        ownerresult.left.toOption.get.map(_.code).distinct shouldBe Vector(
          GenerationDiagnosticCode.MalformedEvidence
        )
        pairresult.left.toOption.get.map(_.code).distinct shouldBe Vector(
          GenerationDiagnosticCode.MalformedEvidence
        )
      }
    }

    "admit exact pairs by evidence and lifecycle" which {
      "admit only a proven exact pair" in {
        Given("a proven exact CNCF target and Cozy generator pair")
        When("the production admission API evaluates the pair")
        val decision = GenerationCompatibility.admit(
          GenerationInputs(Some(_cncf_target), Some(_cozy_generator)),
          GenerationLifecycle.Development,
          _proven
        )
        Then("the pair is supported")
        decision.result shouldBe GenerationAdmission.Supported
      }
      "reject a SNAPSHOT CNCF target for release admission" in {
        Given("a proven pair whose CNCF target is a SNAPSHOT coordinate")
        val pair = GenerationPair(_cncf_target.copy(version = "0.5.2-SNAPSHOT"), _cozy_generator)
        When("the production admission API evaluates release lifecycle")
        val decision = _admit(pair, GenerationLifecycle.Release)
        Then("CV-02 rejects the mutable CNCF target with a typed lifecycle diagnostic")
        decision.result shouldBe GenerationAdmission.Unsupported
        decision.diagnostics.map(_.code) should contain(
          GenerationDiagnosticCode.SnapshotNotAllowedForRelease
        )
      }
      "reject a SNAPSHOT Cozy generator for release admission" in {
        Given("a proven pair whose Cozy generator is a SNAPSHOT coordinate")
        val pair = GenerationPair(_cncf_target, _cozy_generator.copy(version = "0.3.1-SNAPSHOT"))
        When("the production admission API evaluates release lifecycle")
        val decision = _admit(pair, GenerationLifecycle.Release)
        Then("CV-02 rejects the mutable Cozy generator with a typed lifecycle diagnostic")
        decision.result shouldBe GenerationAdmission.Unsupported
        decision.diagnostics.map(_.code) should contain(
          GenerationDiagnosticCode.SnapshotNotAllowedForRelease
        )
      }
      "reject both SNAPSHOT coordinates for release admission" in {
        Given("a proven pair whose CNCF and Cozy coordinates are both SNAPSHOTs")
        val pair = GenerationPair(
          _cncf_target.copy(version = "0.5.2-SNAPSHOT"),
          _cozy_generator.copy(version = "0.3.1-SNAPSHOT")
        )
        When("the production admission API evaluates release lifecycle")
        val decision = _admit(pair, GenerationLifecycle.Release)
        Then("CV-02 rejects the mutable pair with a typed lifecycle diagnostic")
        decision.result shouldBe GenerationAdmission.Unsupported
        decision.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.SnapshotNotAllowedForRelease
        )
      }
      "admit an immutable released exact pair with explicit compatible evidence" in {
        Given("an immutable released exact pair marked proven by explicit evidence")
        When("the production admission API evaluates release lifecycle")
        val decision = _admit(_pair, GenerationLifecycle.Release)
        Then("CV-02 admits the exact released pair")
        decision.result shouldBe GenerationAdmission.Supported
        decision.diagnostics shouldBe empty
      }
      "admit an explicitly evidenced SNAPSHOT pair for development" in {
        Given("an explicitly evidenced SNAPSHOT pair and development lifecycle")
        val pair = GenerationPair(
          _cncf_target.copy(version = "0.5.2-SNAPSHOT"),
          _cozy_generator.copy(version = "0.3.1-SNAPSHOT")
        )
        When("the production admission API evaluates development lifecycle")
        val decision = _admit(pair, GenerationLifecycle.Development)
        Then("CV-02 admits the explicitly evidenced development pair")
        decision.result shouldBe GenerationAdmission.Supported
        decision.diagnostics shouldBe empty
      }
      "use evidence rather than numeric version similarity" in {
        Given("a proven pair whose CNCF and Cozy version numbers are unrelated")
        val pair = GenerationPair(
          _cncf_target.copy(version = "7.4.1"),
          _cozy_generator.copy(version = "0.2.9")
        )
        When("the production admission API evaluates the exact evidenced pair")
        val decision = _admit(pair, GenerationLifecycle.Release)
        Then("CV-02 admits based on exact evidence without numeric similarity inference")
        decision.result shouldBe GenerationAdmission.Supported
        decision.diagnostics shouldBe empty
      }
      "distinguish known but unproven and explicitly incompatible pairs" in {
        Given("two evidence records with different non-proven semantics")
        val incompatible = _proven.copy(entries =
          Vector(GenerationPairEvidence(_pair, GenerationPairStatus.Incompatible))
        )
        When("both pairs are admitted")
        val unproven = GenerationCompatibility.admit(
          GenerationInputs(Some(_cncf_target), Some(_cozy_generator)),
          GenerationLifecycle.Development,
          _unproven
        )
        val rejected = GenerationCompatibility.admit(
          GenerationInputs(Some(_cncf_target), Some(_cozy_generator)),
          GenerationLifecycle.Development,
          incompatible
        )
        Then("the diagnostics retain the evidence distinction")
        unproven.diagnostics.map(_.code) should contain(
          GenerationDiagnosticCode.UnprovenGenerationPair
        )
        rejected.diagnostics.map(_.code) should contain(
          GenerationDiagnosticCode.IncompatibleGenerationPair
        )
      }
      "distinguish unsupported CNCF, unsupported Cozy, and both unsupported" in {
        Given("inputs that omit evidence for one or both coordinates")
        When("the production admission API evaluates each unsupported shape")
        val cncfonly = GenerationCompatibility.admit(
          GenerationInputs(Some(_cncf_target.copy(version = "9.9.9")), Some(_cozy_generator)),
          GenerationLifecycle.Development,
          _proven
        )
        val cozyonly = GenerationCompatibility.admit(
          GenerationInputs(Some(_cncf_target), Some(_cozy_generator.copy(version = "9.9.9"))),
          GenerationLifecycle.Development,
          _proven
        )
        val both = GenerationCompatibility.admit(
          GenerationInputs(
            Some(_cncf_target.copy(version = "9.9.9")),
            Some(_cozy_generator.copy(version = "9.9.9"))
          ),
          GenerationLifecycle.Development,
          _proven
        )
        Then("only the unsupported dimensions are diagnosed")
        cncfonly.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.UnsupportedCncfTarget
        )
        cozyonly.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.UnsupportedCozyGenerator
        )
        both.diagnostics.map(_.code) should contain theSameElementsInOrderAs Vector(
          GenerationDiagnosticCode.UnsupportedCncfTarget,
          GenerationDiagnosticCode.UnsupportedCozyGenerator
        )
      }
      "reject an unevidenced pair whose CNCF and Cozy coordinates are individually known" in {
        Given("evidence for two distinct pairs and a requested cross-pair combination")
        val alternate = GenerationPair(
          _cncf_target.copy(version = "0.5.2"),
          _cozy_generator.copy(version = "0.3.1")
        )
        val evidence = _proven.copy(entries =
          Vector(
            GenerationPairEvidence(_pair, GenerationPairStatus.Proven),
            GenerationPairEvidence(alternate, GenerationPairStatus.Proven)
          )
        )
        val requested = GenerationPair(_cncf_target, alternate.cozyGenerator)
        When("the production admission API evaluates the unevidenced combination")
        val decision =
          GenerationCompatibility.admit(requested, GenerationLifecycle.Release, evidence)
        Then("the pair is incompatible with an unsupported-pair diagnostic")
        decision.result shouldBe GenerationAdmission.Incompatible
        decision.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.UnsupportedGenerationPair
        )
      }
      "keep missing CNCF, missing Cozy, and both missing distinguishable" in {
        Given("primary inputs with each missing-coordinate shape")
        When("the production admission API evaluates each missing-coordinate shape")
        val missingcncf = GenerationCompatibility.admit(
          GenerationInputs(None, Some(_cozy_generator)),
          GenerationLifecycle.Development,
          _proven
        )
        val missingcozy = GenerationCompatibility.admit(
          GenerationInputs(Some(_cncf_target), None),
          GenerationLifecycle.Development,
          _proven
        )
        val missingboth = GenerationCompatibility.admit(
          GenerationInputs(None, None),
          GenerationLifecycle.Development,
          _proven
        )
        Then("each shape has only its own typed missing diagnostics")
        missingcncf.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.MissingCncfTarget
        )
        missingcozy.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.MissingCozyGenerator
        )
        missingboth.diagnostics.map(_.code) should contain theSameElementsInOrderAs Vector(
          GenerationDiagnosticCode.MissingCncfTarget,
          GenerationDiagnosticCode.MissingCozyGenerator
        )
      }
    }

    "resolve generation source authority" which {
      "apply project, bridge, CLI, and published-default precedence" in {
        Given("distinct valid exact pairs assigned to combined source-resolution scenarios")
        val projectpair = _pair("51.1.0")
        val bridgepair  = _pair("51.2.0")
        val clipair     = _pair("51.3.0")
        val defaultpair = _pair("51.4.0")
        val projectwins = GenerationSourceValues(
          Some(projectpair),
          Some(projectpair),
          Some(projectpair)
        )
        val bridgewins =
          GenerationSourceValues(None, Some(bridgepair), Some(bridgepair))
        val cliwins     = GenerationSourceValues(None, None, Some(clipair))
        val defaultwins = GenerationSourceValues(None, None, None)
        val contradiction = GenerationSourceValues(
          Some(projectpair),
          Some(bridgepair),
          Some(projectpair)
        )
        val evidence = _proven.copy(
          entries = Vector(projectpair, bridgepair, clipair, defaultpair).
            map(GenerationPairEvidence(_, GenerationPairStatus.Proven)),
          publishedDefault = Some(defaultpair)
        )
        When("source resolution is evaluated")
        val projectresolution = GenerationCompatibility.resolveSources(projectwins, evidence)
        val bridgeresolution  = GenerationCompatibility.resolveSources(bridgewins, evidence)
        val cliresolution     = GenerationCompatibility.resolveSources(cliwins, evidence)
        val defaultresolution = GenerationCompatibility.resolveSources(defaultwins, evidence)
        val rejected          = GenerationCompatibility.resolveSources(contradiction, evidence)
        Then(
          "the production resolver preserves the strongest agreed source and rejects contradiction"
        )
        projectresolution.pair shouldBe Some(projectpair)
        projectresolution.source shouldBe Some(GenerationSource.ProjectContract)
        bridgeresolution.pair shouldBe Some(bridgepair)
        bridgeresolution.source shouldBe Some(GenerationSource.OwningBuildBridge)
        cliresolution.pair shouldBe Some(clipair)
        cliresolution.source shouldBe Some(GenerationSource.Cli)
        defaultresolution.pair shouldBe Some(defaultpair)
        defaultresolution.source shouldBe Some(GenerationSource.PublishedDefault)
        rejected.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.ContradictorySource
        )
      }
    }

    "preserve deterministic diagnostics" which {
      "order parser and validator diagnostics identically for generated shuffled duplicate and conflict inputs" in {
        Given(
          "generated evidence records containing several duplicate and conflicting pair statuses"
        )
        val records = Vector(
          GenerationPairEvidence(_pair("52.1.0"), GenerationPairStatus.Proven),
          GenerationPairEvidence(_pair("52.1.0"), GenerationPairStatus.Unproven),
          GenerationPairEvidence(_pair("52.2.0"), GenerationPairStatus.Incompatible),
          GenerationPairEvidence(_pair("52.2.0"), GenerationPairStatus.Proven),
          GenerationPairEvidence(_pair("52.3.0"), GenerationPairStatus.Unproven),
          GenerationPairEvidence(_pair("52.3.0"), GenerationPairStatus.Incompatible)
        )
        val property = Prop.forAll(_shuffled(records)) { shuffledrecords =>
          val shuffledjson = _render_evidence(shuffledrecords)
          val canonicaljson = _render_evidence(records.sortBy(record =>
            (GenerationCompatibility.pairName(record.pair), record.status.toString)
          ))
          val parsedshuffled =
            GenerationCompatibilityEvidenceLoader.parse(shuffledjson, "generated-shuffled.json")
          val parsedcanonical =
            GenerationCompatibilityEvidenceLoader.parse(canonicaljson, "generated-canonical.json")
          val parserstable = parsedshuffled.left.toOption == parsedcanonical.left.toOption
          val shuffledvalidator =
            GenerationCompatibility.validate(_proven.copy(entries = shuffledrecords))
          val canonicalvalidator = GenerationCompatibility.validate(_proven.copy(entries =
            records.sortBy(record =>
              (GenerationCompatibility.pairName(record.pair), record.status.toString)
            )
          ))
          parserstable && shuffledvalidator == canonicalvalidator
        }
        When("each generated permutation is serialized through the production JSON parser")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
        Then("parser and validator return the same ordered typed diagnostics for every permutation")
        checked.passed shouldBe true
      }
    }

    "preserve later-slice ownership" which {
      "keep generation-boundary wiring and descriptor checks deferred to their owning slices" in {
        Given("the CV-02 production admission API")
        When("the packaged evidence resource identity is inspected")
        val resourcename = GenerationCompatibilityEvidenceLoader.resourceName
        Then("CV-03 owns invocation wiring and CV-04 owns runtime descriptor validation")
        resourcename shouldBe "META-INF/cozy/generation-compatibility.v1.json"
      }
    }
  }

  private def _pair(version: String): GenerationPair =
    GenerationPair(
      MavenCoordinate("org.goldenport", "goldenport-cncf_3", version),
      MavenCoordinate("org.simplemodeling", "cozy_2.12", version)
    )

  private def _admit(
      pair: GenerationPair,
      lifecycle: GenerationLifecycle
  ): GenerationAdmissionDecision =
    GenerationCompatibility.admit(
      GenerationInputs(Some(pair.cncfTarget), Some(pair.cozyGenerator)),
      lifecycle,
      _proven.copy(entries = Vector(GenerationPairEvidence(pair, GenerationPairStatus.Proven)))
    )

  private def _shuffled[A](values: Vector[A]): Gen[Vector[A]] =
    Gen.listOfN(values.size, Gen.chooseNum(Int.MinValue, Int.MaxValue)).map { keys =>
      values.zip(keys).sortBy(_._2).map(_._1)
    }

  private def _render_evidence(entries: Vector[GenerationPairEvidence]): String = {
    def _coordinate_(value: MavenCoordinate): String =
      s"""{"organization":"${value.organization}","artifact":"${value.artifact}","version":"${value
          .version}"}"""
    def _entry_(value: GenerationPairEvidence): String =
      s"""{"cncfTarget":${_coordinate_(value.pair.cncfTarget)},"cozyGenerator":${_coordinate_(
          value.pair.cozyGenerator
        )},"status":"${value.status.toString.toLowerCase}"}"""
    s"""{"schema":"${GenerationCompatibility.evidenceSchema}","evidenceOwner":{"owner":"property","location":"generated.json"},"publishedDefault":null,"pairs":[${entries
        .map(_entry_).mkString(",")}]}"""
  }
}
