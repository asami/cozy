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
    "documentPattern":{"pattern":"background-first","roles":[{"id":"dr","role":"lead","step":"child"},{"id":"di","role":"introduction","step":"child"},{"id":"dd","role":"definition","step":"child"},{"id":"dc","role":"composition","step":"child"},{"id":"du","role":"usage","step":"child"},{"id":"dz","role":"conclusion","step":"child"}]},
    "sections":[{"step":"root","pattern":"definition","question":"qr","answer":"root-claim","roles":[{"id":"ra","role":"definition","target":{"kind":"claim","id":"root-claim"}},{"id":"rr","role":"properties","target":{"kind":"child","id":"child","explainsClaim":"root-claim"}}],"inheritedContext":[]},
      {"step":"child","pattern":"mechanism","question":"qc","answer":"child-claim","roles":[{"id":"cr","role":"principle","target":{"kind":"claim","id":"child-claim"}},{"id":"co","role":"operation","target":{"kind":"claim","id":"child-claim"}},{"id":"ce","role":"result-condition","target":{"kind":"claim","id":"child-claim"}}],"inheritedContext":[]}] }"""
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
      result.sections.head.roles.last.target shouldBe CozyDocumentPatternCodec.ChildTarget("child", "root-claim")
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
    "render the typed hierarchy and references through the normal command in both locales" in {
      Given("a recursive v2 Core with different parent and child patterns")
      val directory = java.nio.file.Files.createTempDirectory("pattern-overview")
      val core = directory.resolve("core.yaml")
      val format = directory.resolve("format.yaml")
      val output = directory.resolve("overview.html")
      val source = _source.replace("\"step\":\"root\",\"pattern\":\"definition\"", "\"step\":\"root\",\"pattern\":\"decomposition\"")
        .replace("\"id\":\"ra\",\"role\":\"definition\"", "\"id\":\"rp\",\"role\":\"purpose\",\"target\":{\"kind\":\"claim\",\"id\":\"root-claim\"}},{\"id\":\"ra\",\"role\":\"overview\"")
        .replace("\"id\":\"rr\",\"role\":\"properties\"", "\"id\":\"rr\",\"role\":\"components\"")
        .replace("\"id\":\"ce\",\"role\":\"result-condition\",\"target\":{\"kind\":\"claim\",\"id\":\"child-claim\"}}],\"inheritedContext\":[]", "\"id\":\"ce\",\"role\":\"result-condition\",\"target\":{\"kind\":\"claim\",\"id\":\"child-claim\"}}],\"inheritedContext\":[{\"ancestor\":\"root\",\"role\":\"purpose\",\"target\":\"root-claim\"}]")
      java.nio.file.Files.writeString(core, source)
      try {
        for (locale <- Vector("ja", "en")) {
          val wording = if (locale == "ja") "親の問い？" else "Parent question?"
          val formatjson = s"""{
            "schema":"cozy.content-core.logic-tree-format.v2","coreId":"sample","locale":"$locale",
            "base":{"schema":"cozy.content-core.logic-tree-format.v1","id":"format","coreId":"sample","locale":"$locale",
              "stepBindings":[{"id":"root","title":"Root"},{"id":"child","title":"Child"}],
              "claimBindings":[{"id":"root-claim","text":"Root claim"},{"id":"child-claim","text":"Child claim"}],
              "nodeBindings":[{"id":"rs","label":"RS"},{"id":"rt","label":"RT"},{"id":"cs","label":"CS"},{"id":"ct","label":"CT"}],
              "chrome":{"overviewLabel":"Overview","overviewDocumentTitle":"Overview","slidesDocumentTitle":"Slides","pageCountLabel":"Pages",
                "claimsHeading":"Claims","localStructureHeading":"Structure","directChildrenHeading":"Children","directChildFlowHeading":"Flow",
                "noDirectChildren":"None","noDirectChildFlowTransitions":"None","previous":"Previous","next":"Next","deck":"Deck","navigationAriaLabel":"Navigation"}},
            "questionBindings":[{"id":"qr","text":"$wording"},{"id":"qc","text":"Child?"}] }"""
          java.nio.file.Files.writeString(format, formatjson)
          When("overview is rendered by the command")
          val rendered = CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "render", "--core", core.toString, "--format", format.toString, "--kind", "overview", "--save", output.toString))
          val html = java.nio.file.Files.readString(output)
          Then("localized choices, authored questions and explicit child-to-claim references remain navigable")
          rendered shouldBe true
          html should include (wording)
          html should include (if (locale == "ja") "構成分解型" else "Decomposition")
          html should include (if (locale == "ja") "仕組み型" else "Mechanism")
          html should include ("href=\"#claim-root-claim\"")
          html should include ("id=\"claim-root-claim\"")
          html should include ("id=\"step-child\"")
          html should include (":target{")
          html should include (":focus-visible{")
          html should include ("class=\"local-structure\"")
          html should include ("class=\"direct-child-flow\"")
          html should include ("class=\"containment\"")
          html should not include ("<script src=")
          html should not include ("<link rel=\"stylesheet\"")
          val ids = """ id="([^"]+)"""".r.findAllMatchIn(html).map(_.group(1)).toVector
          ids.distinct shouldBe ids
          html should include (s"""<html lang="$locale">""")
          html should include ("id=\"step-child\" tabindex=\"-1\"")
          html should include (if (locale == "ja") "継承元" else "Inherited from")
          val child = html.substring(html.indexOf("id=\"step-child\""))
          child should include ("href=\"#step-root\"")
          child should include ("href=\"#claim-root-claim\"")
          val targets = """ href="#([^"#]+)"""".r.findAllMatchIn(html).map(_.group(1)).toVector
          targets should not be empty
          targets.forall(ids.contains) shouldBe true
          val slides = intercept[CozyDocumentLogicTree.LogicTreeFault] {
            CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "render", "--core", core.toString, "--format", format.toString, "--kind", "slides", "--save", output.toString))
          }
          slides.code shouldBe "LOGIC_TREE_CLI"
          java.nio.file.Files.readString(output) shouldBe html
        }
      } finally {
        java.nio.file.Files.deleteIfExists(output)
        java.nio.file.Files.deleteIfExists(format)
        java.nio.file.Files.deleteIfExists(core)
        java.nio.file.Files.deleteIfExists(directory)
      }
    }
    "enforce role cardinality, order, answer references and explicit inherited context" in {
      Given("a completed recursive envelope")
      val missing = _source.replace("{\"id\":\"di\",\"role\":\"introduction\",\"step\":\"child\"},", "")
      val unordered = _source.replace("\"role\":\"introduction\"", "\"role\":\"usage\"")
      val answer = _source.replace("\"id\":\"ra\",\"role\":\"definition\",\"target\":{\"kind\":\"claim\",\"id\":\"root-claim\"}", "\"id\":\"ra\",\"role\":\"definition\",\"target\":{\"kind\":\"child\",\"id\":\"child\",\"explainsClaim\":\"root-claim\"}")
      val invalidinherit = _source.replace("\"step\":\"child\",\"pattern\":\"mechanism\"", "\"step\":\"child\",\"pattern\":\"mechanism\"").replace("\"id\":\"ce\",\"role\":\"result-condition\",\"target\":{\"kind\":\"claim\",\"id\":\"child-claim\"}}],\"inheritedContext\":[]", "\"id\":\"ce\",\"role\":\"result-condition\",\"target\":{\"kind\":\"claim\",\"id\":\"child-claim\"}}],\"inheritedContext\":[{\"ancestor\":\"root\",\"role\":\"purpose\",\"target\":\"root-claim\"}]")
      When("malformed variants are decoded")
      val faults = Vector(missing, unordered, answer, invalidinherit).map(value => intercept[CozyDocumentLogicTree.LogicTreeFault](CozyDocumentPatternCodec.decode(parse(value).toOption.get)))
      Then("each fails its appropriate structural rule")
      faults.map(_.code) shouldBe Vector("PATTERN_ROLE_CARDINALITY", "PATTERN_ROLE_ORDER", "PATTERN_REFERENCE", "PATTERN_REFERENCE")
    }
    "allow a direct optional context from a strict ancestor" in {
      Given("a root context assignment and a child choosing the same context role")
      val source = _source.replace("\"id\":\"ra\",\"role\":\"definition\"", "\"id\":\"rx\",\"role\":\"context\",\"target\":{\"kind\":\"claim\",\"id\":\"root-claim\"}},{\"id\":\"ra\",\"role\":\"definition\"")
        .replace("\"pattern\":\"mechanism\"", "\"pattern\":\"definition\"")
        .replace("\"role\":\"principle\"", "\"role\":\"definition\"")
        .replace("\"role\":\"operation\"", "\"role\":\"properties\"")
        .replace(",{\"id\":\"ce\",\"role\":\"result-condition\",\"target\":{\"kind\":\"claim\",\"id\":\"child-claim\"}}", "")
        .replace("\"id\":\"co\",\"role\":\"properties\",\"target\":{\"kind\":\"claim\",\"id\":\"child-claim\"}}],\"inheritedContext\":[]", "\"id\":\"co\",\"role\":\"properties\",\"target\":{\"kind\":\"claim\",\"id\":\"child-claim\"}}],\"inheritedContext\":[{\"ancestor\":\"root\",\"role\":\"context\",\"target\":\"root-claim\"}]")
      When("the envelope is decoded")
      val result = CozyDocumentPatternCodec.decode(parse(source).toOption.get)
      Then("the explicit inherited claim remains separate from local roles")
      result.sections.last.inheritedContext shouldBe Vector(CozyDocumentPatternCodec.InheritedContext("root", "context", "root-claim"))
    }
    "produce only unwritten draft slots for every catalog selection" in {
      Given("any catalog document and section selection")
      val prop = Prop.forAll(Gen.oneOf(CozyDocumentPatternCatalog.documents), Gen.oneOf(CozyDocumentPatternCatalog.sections)) { (document, section) =>
        val draft = CozyDocumentPatternDraft.create(CozyDocumentPatternCatalog.id, "1", document.id, section.id)
        CozyDocumentPatternDraft.validate(draft)
        val fault = intercept[CozyDocumentLogicTree.LogicTreeFault](CozyDocumentPatternDraft.rejectIncomplete(draft))
        fault.code == "draft-incomplete" && !draft.noSpaces.contains("claim-id")
      }
      When("a scaffold is created and read back")
      val result = Test.check(Test.Parameters.default, prop)
      Then("it remains explicitly incomplete")
      result.passed shouldBe true
    }
    "dispatch scaffold and reject its draft before reading Format" in {
      Given("direct output and nonexistent Format paths")
      val directory = java.nio.file.Files.createTempDirectory("pattern-draft")
      val core = directory.resolve("core.yaml")
      val format = directory.resolve("missing.yaml")
      try {
        When("the normal command writes and validates a scaffold")
        val created = CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "scaffold", "--catalog", CozyDocumentPatternCatalog.id, "--version", "1", "--document-pattern", "background-first", "--section-pattern", "definition", "--save", core.toString))
        val fault = intercept[CozyDocumentLogicTree.LogicTreeFault](CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "validate", "--core", core.toString, "--format", format.toString)))
        Then("the command reports its unwritten state before opening Format")
        created shouldBe true
        fault.code shouldBe "draft-incomplete"
      } finally {
        java.nio.file.Files.deleteIfExists(core)
        java.nio.file.Files.deleteIfExists(directory)
      }
    }
    "refuse to overwrite an authored Core when scaffolding" in {
      Given("an existing core.yaml containing authored content")
      val directory = java.nio.file.Files.createTempDirectory("pattern-authored")
      val core = directory.resolve("core.yaml")
      val authored = "schema: cozy.content-core.logic-tree.v1\nid: authored\nroot:\n  claims: [authored-claim]\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
      java.nio.file.Files.write(core, authored)
      try {
        When("the scaffold command targets that file")
        val fault = intercept[CozyDocumentLogicTree.LogicTreeFault] {
          CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "scaffold", "--catalog", CozyDocumentPatternCatalog.id, "--version", "1", "--document-pattern", "background-first", "--section-pattern", "definition", "--save", core.toString))
        }
        Then("it reports the existing destination and preserves the original bytes")
        fault.code shouldBe "LOGIC_TREE_PATH"
        fault.path shouldBe "$.save"
        fault.reason shouldBe "scaffold destination already exists"
        java.nio.file.Files.readAllBytes(core).toVector shouldBe authored.toVector
      } finally {
        java.nio.file.Files.deleteIfExists(core)
        java.nio.file.Files.deleteIfExists(directory)
      }
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
  "Explicit Document correspondence" should {
    "require complete direct typed references for both role kinds" in {
      Given("an admitted recursive envelope and Document targets with direct references")
      val envelope = CozyDocumentPatternCodec.decode(parse(_source).toOption.get)
      val empty = CozyDocumentDescriptionV2.References(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)
      val steprefs = empty.copy(steps = Vector("child"))
      val claimrefs = empty.copy(claims = Vector("root-claim", "child-claim"))
      val section = CozyDocumentDescriptionV2.Section("chapter", "Chapter", steprefs,
        Vector(CozyDocumentDescriptionV2.Paragraph("paragraph", "Text", claimrefs),
          CozyDocumentDescriptionV2.ListBlock("list", Vector(CozyDocumentDescriptionV2.ListItem("item", "Item", claimrefs)), empty),
          CozyDocumentDescriptionV2.Figure("figure", "asset", "Caption", "Alt", steprefs),
          CozyDocumentDescriptionV2.Table("table", "Caption", Vector.empty, Vector.empty, steprefs),
          CozyDocumentDescriptionV2.LogicalStructure("logical", "child")), Vector.empty)
      val description = CozyDocumentDescriptionV2.DocumentDescription("cozy.document-description.v2", "doc", CozyDocumentDescriptionV2.CoreBinding("sample"), "en", CozyDocumentDescriptionV2.Document("Title", Vector(section)), CozyDocumentDescriptionV2.Labels(Vector.empty, Vector.empty))
      val document = CozyDocumentDescriptionV2.ValidatedDocument(envelope.source, description, CozyDocumentDescriptionV2.DocumentTargets(Set("chapter"), Set("paragraph", "list", "figure", "table", "logical"), Set("item")))
      val chrome = CozyDocumentLogicTree.Chrome("Overview", "Overview", "Slides", "Pages", "Claims", "Structure", "Children", "Flow", "None", "None", "Previous", "Next", "Deck", "Navigation")
      val base = CozyDocumentLogicTree.Format("format", "sample", "en", Vector.empty, Vector.empty, Vector.empty, chrome)
      val format = CozyDocumentPatternCodec.LocalizedFormat("sample", "en", base, Vector.empty)
      val roleids = envelope.documentRoles.map(_.id) ++ envelope.sections.flatMap(_.roles.map(_.id))
      val stepids = envelope.documentRoles.map(_.id) :+ "rr"
      val assignments = roleids.map { id =>
        if (stepids.contains(id)) s"""{"roleAssignment":"$id","targetKind":"section","targetId":"chapter"}"""
        else s"""{"roleAssignment":"$id","targetKind":"block","targetId":"paragraph"}"""
      }
      def _mapping_(items: Vector[String]): io.circe.Json = parse(s"""{"schema":"cozy.document-pattern-correspondence.v1","coreId":"sample","documentId":"doc","locale":"en","assignments":[${items.mkString(",")}]}""").toOption.get
      When("complete mappings and altered mappings are checked")
      CozyDocumentPatternCorrespondence.validate(_mapping_(assignments), envelope, format, document)
      val duplicate = assignments :+ assignments.head
      val stale = assignments.updated(0, assignments.head.replace("dr", "stale"))
      val wrong = assignments.updated(0, assignments.head.replace("section", "block"))
      val indirect = assignments.updated(0, assignments.head.replace("chapter", "list").replace("section", "block"))
      val missing = assignments.tail
      val faults = Vector(duplicate, stale, wrong, indirect, missing).map(items => intercept[CozyDocumentLogicTree.LogicTreeFault] {
        CozyDocumentPatternCorrespondence.validate(_mapping_(items), envelope, format, document)
      })
      Then("duplicates, stale or wrong targets, indirect references and missing roles are rejected")
      faults.map(_.code) shouldBe Vector.fill(5)("PATTERN_CORRESPONDENCE")
      val alternatives = Vector("figure", "table", "logical")
      alternatives.foreach { target =>
        val changed = assignments.updated(0, assignments.head.replace("section", "block").replace("chapter", target))
        CozyDocumentPatternCorrespondence.validate(_mapping_(changed), envelope, format, document)
      }
      val listitem = assignments.updated(assignments.indexWhere(_.contains("\"ra\"")), assignments.find(_.contains("\"ra\"")).get.replace("block", "list-item").replace("paragraph", "item"))
      CozyDocumentPatternCorrespondence.validate(_mapping_(listitem), envelope, format, document)
      val changed = CozyDocumentPatternCodec.decode(parse(_source.replace("root-claim", "new-claim")).toOption.get)
      val changedsection = section.copy(coreRefs = steprefs.copy(claims = Vector("new-claim")), blocks = Vector(CozyDocumentDescriptionV2.Paragraph("paragraph", "Text", claimrefs.copy(claims = Vector("child-claim")))))
      val changeddescription = description.copy(document = description.document.copy(sections = Vector(changedsection)))
      val changeddocument = document.copy(core = changed.source, description = changeddescription)
      val changedfault = intercept[CozyDocumentLogicTree.LogicTreeFault] {
        CozyDocumentPatternCorrespondence.validate(_mapping_(assignments), changed, format, changeddocument)
      }
      changedfault.code shouldBe "PATTERN_CORRESPONDENCE"
      changedfault.reason should include ("new-claim")
    }
    "reject either half of the optional command pair before loading inputs" in {
      Given("a validate command with only one correspondence option")
      When("either half is supplied")
      val faults = Vector("document", "correspondence").map { option =>
        intercept[CozyDocumentLogicTree.LogicTreeFault] {
          CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "validate", "--core", "core.yaml", "--format", "format.yaml", s"--$option", "missing.yaml"))
        }
      }
      Then("both fail at the CLI boundary")
      faults.map(_.code) shouldBe Vector("LOGIC_TREE_CLI", "LOGIC_TREE_CLI")
    }
  }

  "The v2 authoring to reader handoff" should {
    "admit the same embedded Core in both Document paths and keep authoring labels out of SmartDox" in {
      Given("a complete v2 envelope, explicitly authored Document and separate SmartDox article")
      val root = java.nio.file.Files.createTempDirectory("pattern-document-handoff")
      val project = java.nio.file.Files.createDirectory(root.resolve("article.dox"))
      val content = java.nio.file.Files.createDirectory(project.resolve("content"))
      val locale = java.nio.file.Files.createDirectory(content.resolve("en"))
      val core = content.resolve("core.yaml")
      val document = locale.resolve("document.yaml")
      val article = project.resolve("index.dox")
      val prose = "Reader-authored explanatory prose."
      val documentjson = s"""{
        "schema":"cozy.document-description.v2","id":"doc","core":{"id":"sample"},"locale":"en",
        "document":{"title":"Reader title","sections":[{"id":"chapter","heading":"Reader heading",
          "coreRefs":{"steps":["root","child"],"claims":["root-claim","child-claim"],"nodes":["rs","rt","cs","ct"],"relations":["rm","cm"],"flows":["rf","cf"]},
          "blocks":[{"id":"paragraph","kind":"paragraph","text":"$prose","coreRefs":{"steps":["child"],"claims":["root-claim","child-claim"],"nodes":[],"relations":[],"flows":[]}}],"sections":[]}]},
        "labels":{"steps":[{"stepRef":"root","text":"Root"},{"stepRef":"child","text":"Child"}],
          "nodes":[{"nodeRef":"rs","text":"RS"},{"nodeRef":"rt","text":"RT"},{"nodeRef":"cs","text":"CS"},{"nodeRef":"ct","text":"CT"}]}}"""
      java.nio.file.Files.writeString(core, _source)
      java.nio.file.Files.writeString(document, documentjson)
      java.nio.file.Files.writeString(article, s"# Reader heading\n\n$prose\n")
      try {
        When("both normal loaders and the native article renderer consume their sources")
        val envelope = CozyDocumentPatternCodec.decode(parse(_source).toOption.get)
        val strict = CozyDocumentDescriptionV2.loadDocument(core, document)
        val source = CozyDocumentDescriptionV2.loadSourceDocument(core, document)
        val html = CozyDocumentProjectSmartDoxArticleHtml.render(project).html
        Then("both loaders use that validated embedded Core and retain only authored prose")
        strict.core shouldBe envelope.source
        source.core.core shouldBe envelope.core
        source.core.depthFirstSteps shouldBe envelope.source.depthFirstSteps
        strict.description.document.sections.head.blocks.head shouldBe CozyDocumentDescriptionV2.Paragraph("paragraph", prose, CozyDocumentDescriptionV2.References(Vector("child"), Vector("root-claim", "child-claim"), Vector.empty, Vector.empty, Vector.empty))
        html should include (prose)
        Vector("Background First", "Worked Example", "Roles and reading order", "root-claim", "background-first").foreach { label => html should not include (label) }
        java.nio.file.Files.readString(article) shouldBe s"# Reader heading\n\n$prose\n"
        java.nio.file.Files.readString(document) shouldBe documentjson
      } finally {
        java.nio.file.Files.deleteIfExists(article)
        java.nio.file.Files.deleteIfExists(document)
        java.nio.file.Files.deleteIfExists(core)
        java.nio.file.Files.deleteIfExists(locale)
        java.nio.file.Files.deleteIfExists(content)
        java.nio.file.Files.deleteIfExists(project)
        java.nio.file.Files.deleteIfExists(root)
      }
    }
    "reject stale role correspondence after a compatible authoring change" in {
      Given("an envelope whose valid role assignment was renamed without changing its meaning")
      val original = CozyDocumentPatternCodec.decode(parse(_source).toOption.get)
      val changed = CozyDocumentPatternCodec.decode(parse(_source.replace("\"id\":\"rr\"", "\"id\":\"rr-new\"")).toOption.get)
      val refs = CozyDocumentDescriptionV2.References(Vector("root", "child"), Vector("root-claim", "child-claim"), Vector.empty, Vector.empty, Vector.empty)
      val section = CozyDocumentDescriptionV2.Section("chapter", "Heading", refs, Vector.empty, Vector.empty)
      val description = CozyDocumentDescriptionV2.DocumentDescription("cozy.document-description.v2", "doc", CozyDocumentDescriptionV2.CoreBinding("sample"), "en", CozyDocumentDescriptionV2.Document("Title", Vector(section)), CozyDocumentDescriptionV2.Labels(Vector.empty, Vector.empty))
      val document = CozyDocumentDescriptionV2.ValidatedDocument(changed.source, description, CozyDocumentDescriptionV2.DocumentTargets(Set("chapter"), Set.empty, Set.empty))
      val chrome = CozyDocumentLogicTree.Chrome("Overview", "Overview", "Slides", "Pages", "Claims", "Structure", "Children", "Flow", "None", "None", "Previous", "Next", "Deck", "Navigation")
      val format = CozyDocumentPatternCodec.LocalizedFormat("sample", "en", CozyDocumentLogicTree.Format("format", "sample", "en", Vector.empty, Vector.empty, Vector.empty, chrome), Vector.empty)
      val roles = original.documentRoles.map(_.id) ++ original.sections.flatMap(_.roles.map(_.id))
      val entries = roles.map(id => s"""{"roleAssignment":"$id","targetKind":"section","targetId":"chapter"}""").mkString(",")
      val mapping = parse(s"""{"schema":"cozy.document-pattern-correspondence.v1","coreId":"sample","documentId":"doc","locale":"en","assignments":[$entries]}""").toOption.get
      When("the old mapping is checked against the changed envelope")
      val fault = intercept[CozyDocumentLogicTree.LogicTreeFault] { CozyDocumentPatternCorrespondence.validate(mapping, changed, format, document) }
      Then("the stale assignment is rejected without implicit conversion")
      fault.code shouldBe "PATTERN_CORRESPONDENCE"
      fault.reason should include ("rr")
    }
  }

}
