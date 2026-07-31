package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.util.Base64
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cozy.compatibility.CncfRuntimeDescriptorContract
import play.api.libs.json.{JsObject, Json}

/*
 * @since   Jul. 31, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentStyleCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  private def _metadata(example: String, rules: String, slice: String = "CS-02D") =
    afterWord(s"in spec:phase-53-component-style-catalog, example:$example, rules:$rules, phase:53, slice:$slice")

  "The selected CNCF ComponentStyle catalog" should {
    "resolve catalog selections" which {
    "E1 resolve an explicit known selection from the validated runtime descriptor" must _metadata("E1", "CS02A-R1") {
      "when Cozy reads the authoritative catalog carrier" in {
      Given("Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1; Example: E1; the selected descriptor carrying the authoritative framework catalog")
      val descriptor = test_cncf_runtime_descriptor

      When("Cozy validates the descriptor and reads its catalog carrier")
      val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(descriptor, "0.5.2-SNAPSHOT", Some(CncfRuntimeDescriptorContract.sha256(descriptor)), "component-style-spec")
      val catalog = ComponentStyleCatalog.fromValidatedDescriptor(validated)
      val style = catalog.requireSelection("full-fledged-with-standalone")

      Then("the unversioned CML selection resolves to the canonical framework identity")
      style.canonicalid shouldBe "full-fledged-with-standalone@1"
    }
    }

    "E2 reject an explicit style that is unavailable from the selected catalog" must _metadata("E2", "CS02A-R1") {
      "when Cozy resolves an unavailable CML selection" in {
      Given("Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1; Example: E2; the selected framework catalog and an authored domain-only selection")
      val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(test_cncf_runtime_descriptor, "0.5.2-SNAPSHOT", Some(CncfRuntimeDescriptorContract.sha256(test_cncf_runtime_descriptor)), "component-style-spec")
      val catalog = ComponentStyleCatalog.fromValidatedDescriptor(validated)

      When("Cozy resolves the explicit unavailable selection")
      val error = intercept[RuntimeException] {
        catalog.requireSelection("domain-only")
      }

      Then("generation receives a stable unknown-or-unavailable diagnostic")
      error.getMessage should include("CNCF component style is unknown or unavailable: domain-only")
    }
    }

    "E5 reject an explicit style with an older descriptor lacking a catalog" must _metadata("E5", "CS02A-R1,CS02A-R3") {
      "when explicit CML uses a digest-pinned descriptor without a catalog carrier" in {
      Given("Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1, CS02A-R3; Example: E5; explicit-component CML and a valid descriptor without a catalog carrier")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("target/test-generated/component-style-catalog-legacy-explicit.dox")
      val output = base.resolve("target/test-generated/component-style-catalog-legacy-explicit-out")
      val descriptor = base.resolve("target/test-generated/component-style-catalog-legacy-explicit-runtime.yaml")
      delete_recursively(output)
      write_file(input, _component("full-fledged-with-standalone"))
      Files.writeString(
        descriptor,
        Files.readString(test_cncf_runtime_descriptor).linesIterator.
          takeWhile(!_.startsWith("componentStyleCatalog:")).mkString("\n") + "\n"
      )

      When("Cozy reaches the model generation boundary")
      val log = run_modeler_scala(input, output, descriptor)

      Then("explicit style admission fails before output")
      log should include("CNCF component style is unknown or unavailable: full-fledged-with-standalone")
      Files.exists(output) shouldBe false
      }
    }
    }

    "admit catalog selections at the modeler generation boundary" which {
    "E3 admit explicit-component CML with a known style before generation" must _metadata("E3", "CS02A-R1") {
      "when model generation receives a selected known style" in {
      Given("Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1; Example: E3; explicit-component CML with an Entity and one catalog style")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val validinput = base.resolve("target/test-generated/component-style-catalog-valid.dox")
      val validoutput = base.resolve("target/test-generated/component-style-catalog-valid-out")
      delete_recursively(validoutput)
      write_file(validinput, _component("full-fledged-with-standalone"))

      When("Cozy reaches the model generation boundary with the selected runtime descriptor")
      val validlog = run_modeler_scala(validinput, validoutput)

      Then("the known style is admitted")
      validlog should not include "CNCF component style is unknown or unavailable"
      Files.exists(_generated_entity(validoutput)) shouldBe true
    }
    }

    "E4 keep style-less generation compatible with an older descriptor" must _metadata("E4", "CS02A-R3") {
      "when style-less CML uses a digest-pinned descriptor without a catalog carrier" in {
      Given("Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R3; Example: E4; style-less explicit-component CML and a valid descriptor without a catalog carrier")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("target/test-generated/component-style-catalog-legacy.dox")
      val output = base.resolve("target/test-generated/component-style-catalog-legacy-out")
      val descriptor = base.resolve("target/test-generated/component-style-catalog-legacy-runtime.yaml")
      delete_recursively(output)
      write_file(input, _legacy_component)
      Files.writeString(
        descriptor,
        Files.readString(test_cncf_runtime_descriptor).linesIterator.
          takeWhile(!_.startsWith("componentStyleCatalog:")).mkString("\n") + "\n"
      )

      When("Cozy runs style-less and explicit models with the older selected descriptor")
      val log = run_modeler_scala(input, output, descriptor)

      Then("style-less CML generates")
      log should not include "CNCF component style catalog"
      log should not include "CNCF component style is unknown or unavailable"
      Files.exists(_generated_entity(output)) shouldBe true
    }
    }
    }

    "E27 generate an explicit component without entities or services and retain its catalog snapshot" must _metadata("E27", "CS02E-R1,CS02E-R2", "CS-02E") {
      "when component-only CML selects an admitted style" in {
        Given("the component-only artscene CML fixture and the selected runtime catalog")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/component-style-selection.dox")
        val output = base.resolve("target/test-generated/component-style-catalog-component-only-out")
        delete_recursively(output)
        val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(test_cncf_runtime_descriptor, "0.5.2-SNAPSHOT", Some(CncfRuntimeDescriptorContract.sha256(test_cncf_runtime_descriptor)), "component-style-component-only")
        val catalog = ComponentStyleCatalog.fromValidatedDescriptor(validated)

        When("Cozy generates the explicit component and emits model metadata")
        val log = run_modeler_scala(input, output)
        val emittedjson = Json.parse(Files.readString(output.resolve("target/cozy/model-metadata.json")))
        val emittedyaml = Files.readString(output.resolve("target/cozy/model-metadata.yaml"))
        val expected = catalog.requireSelection("full-fledged-with-standalone").snapshot.toJson

        Then("the component exists without unrelated model declarations and both emitted metadata forms carry the catalog snapshot")
        log should not include "CNCF component style is unknown or unavailable"
        tree_snapshot(output).exists { case (_, content) => content.contains("class ComponentFactory") } shouldBe true
        tree_snapshot(output).exists { case (path, _) => path.contains("/entity/") } shouldBe false
        (emittedjson \ "componentStyle").as[JsObject] shouldBe expected
        emittedyaml should include ("componentStyle:")
        emittedyaml should include ("apiVersion: \"cncf.textus/v1\"")
        emittedyaml should include ("parameterSchema:")
        emittedyaml should include ("properties: {}")
        emittedyaml should include ("required: []")
        emittedyaml should include ("parameters: {}")
        emittedyaml should include ("bundles: [\"domain.full@1\"]")
        emittedyaml should include ("capabilities: [\"user.fixed-context-compatible@1\", \"user.multi-user@1\"]")
        emittedyaml should include ("effective: [\"domain.aggregate@1\"")
        emittedyaml should include ("subsystemCapabilities: [\"datastore.optimistic-concurrency@1\"")
      }
    }

    "enforce carrier integrity" which {
      _carrier_variants(Files.readString(test_cncf_runtime_descriptor)).zipWithIndex.foreach { case ((label, content, expected), index) =>
        s"E${index + 6} reject a $label carrier violation" must _metadata(s"E${index + 6}", "CS02A-R1,CS02D-R2") {
          "when Cozy reads the digest-pinned altered carrier" in {
          Given(s"Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1, CS02D-R2; Example: E${index + 6}; a $label catalog carrier")
          val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
          val descriptor = base.resolve(s"target/test-generated/component-style-catalog-carrier-$index.yaml")
          Files.createDirectories(descriptor.getParent)
          Files.writeString(descriptor, content)

          When("the selected descriptor is validated and its catalog is admitted")
          val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(descriptor, "0.5.2-SNAPSHOT", Some(CncfRuntimeDescriptorContract.sha256(descriptor)), label)
          val error = intercept[RuntimeException] { ComponentStyleCatalog.fromValidatedDescriptor(validated) }

          Then("the carrier violation has its stable diagnostic")
          error.getMessage should include(expected)
          }
        }
      }

      _graph_variants(_catalog_text(Files.readString(test_cncf_runtime_descriptor))).zipWithIndex.foreach { case ((label, catalog, expected), index) =>
        s"E${index + 14} reject a $label capability graph" must _metadata(s"E${index + 14}", "CS02D-R1,CS02D-R2") {
          "when Cozy admits the altered graph through the digest-pinned carrier" in {
          Given(s"Spec: ../cloud-native-component-framework/docs/journal/2026/07/2026-07-31-phase-53-cs02d-capability-graph-validation.md; Rules: CS02D-R1, CS02D-R2; Example: E${index + 14}; a catalog with a $label violation")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val runtime = Files.readString(test_cncf_runtime_descriptor)
          val descriptor = base.resolve(s"target/test-generated/component-style-catalog-graph-$index.yaml")
          Files.createDirectories(descriptor.getParent)
          Files.writeString(descriptor, _runtime_with_catalog(runtime, catalog))

          When("the selected descriptor is validated and its catalog is admitted")
          val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(descriptor, "0.5.2-SNAPSHOT", Some(CncfRuntimeDescriptorContract.sha256(descriptor)), label)
          val error = intercept[RuntimeException] { ComponentStyleCatalog.fromValidatedDescriptor(validated) }

          Then("the graph violation has its stable diagnostic")
          error.getMessage should include(expected)
          }
        }
      }

    "E20 admit a family-qualified ASCII catalog style at Int.MaxValue" must _metadata("E20", "CS02A-R2") {
      "when Cozy admits a qualified style through the digest-pinned carrier" in {
      Given("Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E20; a family-qualified ASCII style at Int.MaxValue")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val runtime = Files.readString(test_cncf_runtime_descriptor)
      val catalogtext = _catalog_text(runtime)
      val qualifiedcatalog = catalogtext.replace(
        "\"id\" : \"full-fledged-with-standalone@1\"",
        "\"id\" : \"vendor.full-fledged-with-standalone@2147483647\""
      )
      When("the selected descriptor is validated and its catalog is admitted")
      val qualifieddescriptor = base.resolve("target/test-generated/component-style-catalog-qualified.yaml")
      Files.createDirectories(qualifieddescriptor.getParent)
      Files.writeString(qualifieddescriptor, _runtime_with_catalog(runtime, qualifiedcatalog))
      val qualifiedvalidated = CncfRuntimeDescriptorContract.requireValidDescriptor(qualifieddescriptor, "0.5.2-SNAPSHOT", Some(CncfRuntimeDescriptorContract.sha256(qualifieddescriptor)), "qualified-style")
      val qualified = ComponentStyleCatalog.fromValidatedDescriptor(qualifiedvalidated)

      Then("the consumer exposes the qualified unversioned CML selection")
      qualified.requireSelection("vendor.full-fledged-with-standalone").canonicalid shouldBe "vendor.full-fledged-with-standalone@2147483647"
    }
    }

    _identity_variants(_catalog_text(Files.readString(test_cncf_runtime_descriptor))).zipWithIndex.foreach { case ((label, catalog), index) =>
      s"E${index + 21} reject a $label catalog identity" must _metadata(s"E${index + 21}", "CS02A-R2") {
        "when Cozy admits the noncanonical identity through the digest-pinned carrier" in {
        Given(s"Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E${index + 21}; a catalog with a $label identity")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val runtime = Files.readString(test_cncf_runtime_descriptor)
        val descriptor = base.resolve(s"target/test-generated/component-style-catalog-identity-$index.yaml")
        Files.createDirectories(descriptor.getParent)
        Files.writeString(descriptor, _runtime_with_catalog(runtime, catalog))

        When("the selected descriptor is validated and its catalog is admitted")
        val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(descriptor, "0.5.2-SNAPSHOT", Some(CncfRuntimeDescriptorContract.sha256(descriptor)), label)
        val error = intercept[RuntimeException] { ComponentStyleCatalog.fromValidatedDescriptor(validated) }

        Then("the noncanonical identity is rejected")
        error.getMessage should include("Invalid")
        }
      }
    }

    "E25 reject generated catalog identity major overflows" must _metadata("E25", "CS02A-R2") {
      "when Cozy validates generated overflowing catalog carriers" in {
      Given("Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E25; digest-pinned catalogs whose style major exceeds Int.MaxValue")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val runtime = Files.readString(test_cncf_runtime_descriptor)
      val catalogtext = _catalog_text(runtime)
      val property = Prop.forAll(Gen.chooseNum(0, 96)) { suffix =>
        val catalog = catalogtext.replace(
          "\"id\" : \"full-fledged-with-standalone@1\"",
          "\"id\" : \"vendor.full-fledged-with-standalone@2147483648" + suffix + "\""
        )
        val descriptor = base.resolve(s"target/test-generated/component-style-catalog-overflow-$suffix.yaml")
        Files.createDirectories(descriptor.getParent)
        Files.writeString(descriptor, _runtime_with_catalog(runtime, catalog))
        val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(descriptor, "0.5.2-SNAPSHOT", Some(CncfRuntimeDescriptorContract.sha256(descriptor)), "overflow-major")
        try {
          ComponentStyleCatalog.fromValidatedDescriptor(validated)
          false
        } catch {
          case _: RuntimeException => true
        }
      }

      When("Cozy validates each generated overflowing catalog carrier")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)

      Then("no major outside the shared signed-Int range is admitted")
      checked.passed shouldBe true
    }
    }

    "E26 reject an unpinned carrier descriptor even when its bytes are internally valid" must _metadata("E26", "CS02A-R1") {
      "when Cozy receives a valid catalog carrier without the selected descriptor digest" in {
      Given("Spec: ../cloud-native-component-framework/docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1; Example: E26; a valid catalog carrier validated without an external descriptor digest")
      val descriptor = test_cncf_runtime_descriptor
      val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(descriptor, "0.5.2-SNAPSHOT", None, "component-style-unpinned")

      When("the catalog consumer admits the validated descriptor")
      val error = intercept[RuntimeException] {
        ComponentStyleCatalog.fromValidatedDescriptor(validated)
      }

      Then("the consumer requires digest-pinned validation structurally")
      error.getMessage should include("CNCF component style catalog requires a digest-pinned runtime descriptor")
    }
    }
    }
  }

  private val _entity_component_prefix =
    """# Entity
      |
      |## ComponentStyleCatalogEntity
      |
      |### Attribute
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || id   | entityid | 1 |
      |
      |# COMPONENT
       |
       |## Domain
       |
       |### PACKAGE
       |
       |domain
      |""".stripMargin

  private def _component(style: String): String =
    _entity_component_prefix +
    s"""
       |
       |### STYLE
       |
       |$style
       |""".stripMargin

  private val _legacy_component =
    _entity_component_prefix

  private def _generated_entity(output: java.nio.file.Path): java.nio.file.Path =
    output.resolve("target/scala-3.3.8/src_managed/main/scala/domain/entity/ComponentStyleCatalogEntity.scala")

  private def _catalog_text(runtime: String): String = {
    val payload = runtime.linesIterator.collectFirst {
      case line if line.startsWith("  bytes: ") => line.stripPrefix("  bytes: ").stripPrefix("\"").stripSuffix("\"")
    }.getOrElse(fail("runtime descriptor has no component style catalog bytes"))
    new String(Base64.getDecoder.decode(payload), StandardCharsets.UTF_8)
  }

  private def _runtime_with_catalog(runtime: String, catalog: String): String = {
    val bytes = catalog.getBytes(StandardCharsets.UTF_8)
    runtime
      .replaceFirst("(?m)^  sha256:.*$", s"  sha256: ${_sha256(bytes)}")
      .replaceFirst("(?m)^  bytes:.*$", "  bytes: \"" + Base64.getEncoder.encodeToString(bytes) + "\"")
  }

  private def _carrier_variants(runtime: String): Vector[(String, String, String)] =
    Vector(
      "unknown carrier field" -> runtime.replaceFirst("(?m)^(  bytes:.*)$", "$1\n  unexpected: true") -> "Unknown CNCF component style catalog carrier fields",
      "unknown empty carrier field" -> runtime.replaceFirst("(?m)^(  bytes:.*)$", "$1\n  unexpected: {}") -> "Unknown CNCF component style catalog carrier fields",
      "missing carrier bytes" -> runtime.replaceAll("(?m)^  bytes:.*\\n", "") -> "requires componentStyleCatalog.bytes",
      "unsupported carrier schema" -> runtime.replace("cncf.component-style-catalog-carrier.v1", "cncf.component-style-catalog-carrier.v2") -> "Unsupported CNCF component style catalog carrier schema",
      "unsupported encoding" -> runtime.replace("encoding: base64", "encoding: hex") -> "Unsupported CNCF component style catalog encoding",
      "unsupported resource" -> runtime.replace("META-INF/cncf/component-style-catalog.json", "META-INF/other.json") -> "Unsupported CNCF component style catalog resource",
      "invalid base64 payload" -> runtime.replaceFirst("(?m)^  bytes:.*$", "  bytes: \"%%%\"") -> "Invalid CNCF component style catalog base64 payload",
      "catalog digest mismatch" -> runtime.replace("sha256: 58ae649f9331af5ec789ac7b6fc60a857223598a1a1d46ca481af2d2e9cf5339", "sha256: \"" + ("0" * 64) + "\"") -> "CNCF component style catalog SHA-256 mismatch"
    ).map { case ((label, content), expected) => (label, content, expected) }

  private def _graph_variants(catalogtext: String): Vector[(String, String, String)] =
    Vector(
      "duplicate bundle" -> catalogtext.replace("\"capabilityBundles\" : [", "\"capabilityBundles\" : [\n    { \"id\" : \"domain.full@1\", \"bundles\" : [], \"capabilities\" : [] },") -> "Duplicate component capability bundle identities",
      "unknown bundle reference" -> catalogtext.replace("\"bundles\" : []", "\"bundles\" : [\"domain.missing@1\"]") -> "Unknown component capability bundle: domain.missing@1",
      "cyclic bundle reference" -> catalogtext.replace("\"bundles\" : []", "\"bundles\" : [\"domain.loop@1\"]").replace("\n    }\n  ],\n  \"componentStyles\"", "\n    },\n    {\n      \"id\" : \"domain.loop@1\",\n      \"bundles\" : [\"domain.full@1\"],\n      \"capabilities\" : []\n    }\n  ],\n  \"componentStyles\"") -> "Cyclic component capability bundle reference",
      "capability major conflict" -> catalogtext.replace("\"domain.entity@1\",", "\"domain.entity@1\", \"domain.entity@2\",") -> "version-incompatible identities: domain.entity",
      "nested bundle major conflict" -> _catalog_with_nested_bundle_major_conflict(catalogtext) -> "version-incompatible identities: domain.nested-major-root",
      "sibling bundle major conflict" -> _catalog_with_sibling_bundle_major_conflict(catalogtext) -> "version-incompatible identities: domain.shared",
      "unreferenced diamond" -> _catalog_with_unreferenced_diamond(catalogtext, "static") -> "Duplicate component capability bundle domain.unusedstatic@1 closure identities",
      "required subsystem major conflict" -> catalogtext.replace("\"user-context.current@1\"", "\"user-context.current@1\", \"user-context.current@2\"") -> "subsystem capabilities contains version-incompatible identities: user-context.current"
    ).map { case ((label, catalog), expected) => (label, catalog, expected) }

  private def _identity_variants(catalogtext: String): Vector[(String, String)] =
    Vector(
      "Unicode style" -> catalogtext.replace("\"id\" : \"full-fledged-with-standalone@1\"", "\"id\" : \"vendor.é@1\""),
      "leading-zero style major" -> catalogtext.replace("\"id\" : \"full-fledged-with-standalone@1\"", "\"id\" : \"vendor.full-fledged-with-standalone@01\""),
      "overflow style major" -> catalogtext.replace("\"id\" : \"full-fledged-with-standalone@1\"", "\"id\" : \"vendor.full-fledged-with-standalone@2147483648\""),
      "Unicode capability" -> catalogtext.replace("\"domain.entity@1\"", "\"domain.é@1\"")
    )

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _catalog_with_unreferenced_diamond(catalogtext: String, suffix: String): String =
    catalogtext.replace(
      "\n    }\n  ],\n  \"componentStyles\"",
      s"""
         |    },
         |    {
         |      "id" : "domain.unused$suffix@1",
         |      "bundles" : ["domain.left$suffix@1", "domain.right$suffix@1"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.left$suffix@1",
         |      "bundles" : [],
         |      "capabilities" : ["domain.entity@1"]
         |    },
         |    {
         |      "id" : "domain.right$suffix@1",
         |      "bundles" : [],
         |      "capabilities" : ["domain.entity@1"]
         |    }
         |  ],
         |  "componentStyles"""".stripMargin
    )

  private def _catalog_with_nested_bundle_major_conflict(catalogtext: String): String =
    catalogtext.replace(
      "\n    }\n  ],\n  \"componentStyles\"",
      """
         |    },
         |    {
         |      "id" : "domain.nested-major-root@1",
         |      "bundles" : ["domain.nested-major-middle@1"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.nested-major-middle@1",
         |      "bundles" : ["domain.nested-major-root@2"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.nested-major-root@2",
         |      "bundles" : [],
         |      "capabilities" : []
         |    }
         |  ],
         |  "componentStyles"""".stripMargin
    )

  private def _catalog_with_sibling_bundle_major_conflict(catalogtext: String): String =
    catalogtext.replace(
      "\n    }\n  ],\n  \"componentStyles\"",
      """
         |    },
         |    {
         |      "id" : "domain.branch-root@1",
         |      "bundles" : ["domain.branch-left@1", "domain.branch-right@1"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.branch-left@1",
         |      "bundles" : ["domain.shared@1"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.branch-right@1",
         |      "bundles" : ["domain.shared@2"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.shared@1",
         |      "bundles" : [],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.shared@2",
         |      "bundles" : [],
         |      "capabilities" : []
         |    }
         |  ],
         |  "componentStyles"""".stripMargin
    )
}
