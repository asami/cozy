package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest

import org.goldenport.RAISE
import play.api.libs.json.{JsObject, JsValue, Json}

/*
 * Producer-side Component knowledge carrier handling. This boundary validates
 * declaration identity and raw-byte integrity without returning or projecting
 * consumer-contract content.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] final case class CozyComponentKnowledgeCarrier(
  source: Path,
  declaration: JsObject
) {
  def copyTo(output: Path): Unit = {
    Option(output.getParent).foreach(Files.createDirectories(_))
    Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING)
  }
}

private[cozy] object CozyComponentKnowledgeCarrier {
  val SOURCE_IDENTITY = "src/main/car/component-knowledge.json"
  val ARCHIVE_LOGICAL_PATH = "component-knowledge.json"
  val DEVELOPMENT_IDENTITY = "target/cncf.d/component-knowledge.json"
  val CARRIER_SCHEMA = "cncf.component-knowledge-carrier.v1"
  val CONSUMER_CONTRACT_SCHEMA = "cncf.component-knowledge-consumer.v1"
  private val _declaration_fields = Set("carrierSchema", "consumerContractSchema", "logicalPath", "sha256")

  def fromProject(
    projectRoot: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Option[CozyComponentKnowledgeCarrier] = {
    val root = projectRoot.toAbsolutePath.normalize()
    val source = root.resolve(SOURCE_IDENTITY)
    if (!Files.exists(source))
      None
    else if (!Files.isRegularFile(source))
      RAISE.invalidArgumentFault(s"Component knowledge carrier source must be a regular file: $source")
    else {
      val bytes = Files.readAllBytes(source)
      _require_consumer_contract(bytes, coordinate, SOURCE_IDENTITY)
      Some(
        CozyComponentKnowledgeCarrier(
          source,
          Json.obj(
            "carrierSchema" -> CARRIER_SCHEMA,
            "consumerContractSchema" -> CONSUMER_CONTRACT_SCHEMA,
            "logicalPath" -> ARCHIVE_LOGICAL_PATH,
            "sha256" -> _sha256(bytes)
          )
        )
      )
    }
  }

  def removeDevelopmentCopy(projectRoot: Path): Unit =
    Files.deleteIfExists(projectRoot.toAbsolutePath.normalize().resolve(DEVELOPMENT_IDENTITY))

  def injectGeneratedDeclaration(
    text: String,
    declaration: JsObject,
    label: String
  ): String = {
    val root = _object(_json(text, label), label)
    if (root.keys.contains("componentKnowledge"))
      RAISE.invalidArgumentFault(s"$label must not author componentKnowledge; Cozy generates the Component knowledge carrier declaration.")
    Json.prettyPrint(JsObject(root.value + ("componentKnowledge" -> declaration))) + "\n"
  }

  def requireNoAuthoredDescriptorDeclaration(text: String, label: String): Unit = {
    val root = _object(_json(text, label), label)
    if (root.keys.contains("componentKnowledge"))
      RAISE.invalidArgumentFault(s"$label must not author componentKnowledge; Cozy generates the Component knowledge carrier declaration.")
  }

  def requireDeclaredArchiveCarrier(
    descriptor: JsValue,
    archiveBytes: Option[Array[Byte]],
    label: String
  ): Unit = {
    val root = _object(descriptor, label)
    root.value.get("componentKnowledge") match {
      case None => ()
      case Some(value) =>
        val declaration = _object(value, s"$label.componentKnowledge")
        if (declaration.keys != _declaration_fields)
          RAISE.invalidArgumentFault(s"$label.componentKnowledge must contain exactly carrierSchema, consumerContractSchema, logicalPath, and sha256.")
        _require_string(declaration, "carrierSchema", CARRIER_SCHEMA, s"$label.componentKnowledge")
        _require_string(declaration, "consumerContractSchema", CONSUMER_CONTRACT_SCHEMA, s"$label.componentKnowledge")
        _require_string(declaration, "logicalPath", ARCHIVE_LOGICAL_PATH, s"$label.componentKnowledge")
        val digest = _string(declaration, "sha256", s"$label.componentKnowledge")
        if (!digest.matches("[0-9a-f]{64}"))
          RAISE.invalidArgumentFault(s"$label.componentKnowledge.sha256 must be lowercase 64-hex SHA-256.")
        val bytes = archiveBytes.getOrElse(
          RAISE.invalidArgumentFault(s"$label declares Component knowledge but archive entry $ARCHIVE_LOGICAL_PATH is missing.")
        )
        if (digest != _sha256(bytes))
          RAISE.invalidArgumentFault(s"$label.componentKnowledge.sha256 does not match archive entry $ARCHIVE_LOGICAL_PATH.")
    }
  }

  private def _require_consumer_contract(
    bytes: Array[Byte],
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    source: String
  ): Unit = {
    val root = _object(_json(new String(bytes, StandardCharsets.UTF_8), source), source)
    _require_string(root, "schema", CONSUMER_CONTRACT_SCHEMA, source)
    _require_string(root, "componentId", coordinate.qualifiedId, source)
    _require_string(root, "logicalRelease", coordinate.version, source)
  }

  private def _json(text: String, source: String): JsValue =
    try Json.parse(text)
    catch {
      case exception: Exception =>
        RAISE.invalidArgumentFault(s"$source must be valid Component knowledge consumer-contract JSON: ${Option(exception.getMessage).getOrElse(exception.getClass.getName)}")
    }

  private def _object(value: JsValue, source: String): JsObject =
    value.asOpt[JsObject].getOrElse(
      RAISE.invalidArgumentFault(s"$source must be a JSON object.")
    )

  private def _require_string(objectvalue: JsObject, field: String, expected: String, source: String): Unit = {
    val actual = _string(objectvalue, field, source)
    if (actual != expected)
      RAISE.invalidArgumentFault(s"$source.$field must be exactly '$expected', but was '$actual'.")
  }

  private def _string(objectvalue: JsObject, field: String, source: String): String =
    (objectvalue \ field).asOpt[String].getOrElse(
      RAISE.invalidArgumentFault(s"$source.$field must be a string.")
    )

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
}
