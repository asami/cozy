package cozy.document

import io.circe.parser.parse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CozyDocumentPatternCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _source = """{
    "schema":"cozy.content-core.logic-tree.v2",
    "core":{"schema":"cozy.content-core.logic-tree.v1","id":"sample","root":{
      "id":"root","semanticRole":"explanation","claims":["root-claim"],
      "structure":{"pattern":"mapping","nodes":[{"id":"rs","role":"source"},{"id":"rt","role":"target"}],"relations":[{"id":"rm","relationType":"maps-to","from":"rs","to":"rt"}]},
      "flow":{"id":"rf","transitions":[]},"steps":[{
        "id":"child","semanticRole":"explanation","claims":["child-claim"],
        "structure":{"pattern":"mapping","nodes":[{"id":"cs","role":"source"},{"id":"ct","role":"target"}],"relations":[{"id":"cm","relationType":"maps-to","from":"cs","to":"ct"}]},
        "flow":{"id":"cf","transitions":[]},"steps":[]}] }},
    "catalog":{"id":"cozy.document-section-patterns","version":1},
    "documentPattern":{"pattern":"background-first","roles":[{"id":"dr","role":"lead","step":"child"}]},
    "sections":[{"step":"root","pattern":"definition","question":"qr","answer":"root-claim","roles":[{"id":"rr","role":"properties","target":{"kind":"child","id":"child","explainsClaim":"root-claim"}}],"inheritedContext":[]},
      {"step":"child","pattern":"mechanism","question":"qc","answer":"child-claim","roles":[{"id":"cr","role":"principle","target":{"kind":"claim","id":"child-claim"}}],"inheritedContext":[]}] }"""
  "The document pattern catalog" should {
    "retain all expanded identities, ordered bounds, and bilingual wording" in {
      Given("the bundled authoring catalog")
      val documents = CozyDocumentPatternCatalog.documents
      val sections = CozyDocumentPatternCatalog.sections
      When("each document and section pattern is inspected")
      val patterns = documents ++ sections
      Then("each stable identity has localized names and each section has localized questions")
      documents.map(_.id) shouldBe Vector("background-first", "definition-first", "concept-to-realization", "comparison-and-selection", "technology-survey-and-catalog")
      sections.map(_.id) shouldBe Vector("definition", "rationale", "problem-solution", "decomposition", "mechanism", "comparison", "procedure", "worked-example", "verification-and-evaluation")
      val prop = Prop.forAll(Gen.oneOf(patterns)) { pattern =>
        pattern.label.ja.nonEmpty && pattern.label.en.nonEmpty && pattern.roles.forall(role => role.label.ja.nonEmpty && role.label.en.nonEmpty && role.min >= 0 && role.max.forall(_ >= role.min))
      }
      Test.check(Test.Parameters.default, prop).passed shouldBe true
      sections.forall(_.question.exists(q => q.ja.nonEmpty && q.en.nonEmpty)) shouldBe true
      documents.last.roles.last.min shouldBe 0
    }
  }
  "The v2 pattern codec" should {
    "preserve independent recursive selections and discriminated references" in {
      Given("a v1 recursive core embedded in a v2 envelope")
      val json = parse(_source).toOption.get
      When("the envelope is decoded")
      val result = CozyDocumentPatternCodec.decode(json)
      Then("its selections and existing child and claim identities remain distinct")
      result.sections.map(_.pattern) shouldBe Vector("definition", "mechanism")
      result.sections.head.roles.head.target shouldBe CozyDocumentPatternCodec.ChildTarget("child", "root-claim")
      result.sections.last.roles.head.target shouldBe CozyDocumentPatternCodec.ClaimTarget("child-claim")
      result.source.depthFirstSteps.map(_.id) shouldBe Vector("root", "child")
    }
    "validate format wording against the envelope Core even when another Core has the same id" in {
      Given("two recursive Cores with the same id but different Step, claim, and node identities")
      val first = CozyDocumentPatternCodec.decode(parse(_source).toOption.get)
      val secondsource = _source.replace("child-claim", "other-claim").replace("child", "other").replace(""""kind":"other"""", """"kind":"child"""").replace(""""cs"""", """"os"""").replace(""""ct"""", """"ot"""")
      val second = CozyDocumentPatternCodec.decode(parse(secondsource).toOption.get)
      val format = parse("""{
        "schema":"cozy.content-core.logic-tree-format.v2","coreId":"sample","locale":"en",
        "base":{"schema":"cozy.content-core.logic-tree-format.v1","id":"format","coreId":"sample","locale":"en",
          "stepBindings":[{"id":"root","title":"Root"},{"id":"other","title":"Other"}],
          "claimBindings":[{"id":"root-claim","text":"Root"},{"id":"other-claim","text":"Other"}],
          "nodeBindings":[{"id":"rs","label":"RS"},{"id":"rt","label":"RT"},{"id":"os","label":"OS"},{"id":"ot","label":"OT"}],
          "chrome":{"overviewLabel":"Overview","overviewDocumentTitle":"Overview","slidesDocumentTitle":"Slides","pageCountLabel":"Pages",
            "claimsHeading":"Claims","localStructureHeading":"Structure","directChildrenHeading":"Children","directChildFlowHeading":"Flow",
            "noDirectChildren":"None","noDirectChildFlowTransitions":"None","previous":"Previous","next":"Next","deck":"Deck","navigationAriaLabel":"Navigation"}},
        "questionBindings":[{"id":"qr","text":"Root?"},{"id":"qc","text":"Other?"}] }""").toOption.get
      When("the same Format is decoded with each envelope")
      val accepted = CozyDocumentPatternCodec.decodeFormat(format, second)
      val fault = intercept[CozyDocumentLogicTree.LogicTreeFault](CozyDocumentPatternCodec.decodeFormat(format, first))
      Then("only the envelope whose Core supplies the wording identities accepts it")
      accepted.coreId shouldBe "sample"
      fault.code shouldBe "LOGIC_TREE_FORMAT_COVERAGE"
      fault.path shouldBe "$.stepBindings"
    }
    "reject wrong reference kinds, targets, and unknown fields" in {
      Given("v2 envelopes with malformed target and root shapes")
      val kind = parse(_source.replace("\"kind\":\"child\"", "\"kind\":\"node\"")).toOption.get
      val target = parse(_source.replace("\"explainsClaim\":\"root-claim\"", "\"explainsClaim\":\"child-claim\"")).toOption.get
      val field = parse(_source.replace("\"catalog\":{", "\"extra\":true,\"catalog\":{")).toOption.get
      When("the codec admits each input")
      val faults = Vector(kind, target, field).map(value => intercept[CozyDocumentLogicTree.LogicTreeFault](CozyDocumentPatternCodec.decode(value)))
      Then("each failure carries its structural or typed-reference category")
      faults.map(_.code) shouldBe Vector("PATTERN_REFERENCE", "PATTERN_REFERENCE", "PATTERN_FIELDS")
    }
    "reject unsupported versions and duplicate assignment identities" in {
      Given("a valid envelope and malformed variants")
      val version = parse(_source.replace("\"version\":1", "\"version\":2")).toOption.get
      val duplicate = parse(_source.replace("\"id\":\"cr\"", "\"id\":\"rr\"")).toOption.get
      When("each malformed variant is decoded")
      val first = intercept[CozyDocumentLogicTree.LogicTreeFault](CozyDocumentPatternCodec.decode(version))
      val second = intercept[CozyDocumentLogicTree.LogicTreeFault](CozyDocumentPatternCodec.decode(duplicate))
      Then("the diagnostics identify the catalog and duplicate identity failures")
      first.code shouldBe "PATTERN_CATALOG"
      second.code shouldBe "PATTERN_IDENTITY"
    }
  }
}
