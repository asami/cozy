package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.zip.ZipFile
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

import cozy.compatibility.CarMetadataCompatibility
import cozy.modeler.GenerationProvenance
import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

/*
 * CNCF-owned runtime admission evidence packaged by Cozy.
 *
 * Generation compatibility and provenance are intentionally absent from this
 * runtime document. Runtime activation consumes only the CNCF range, ABI
 * sidecar, and archive digests. Cozy publication separately verifies packaged
 * release provenance before accepting a prebuilt CAR.
 *
 * @since   Jul. 28, 2026
 *  version Jul. 28, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCarRuntimeManifest {
  val FILE_NAME = "car-runtime-manifest.json"
  val SCHEMA_VERSION = "cncf.car-runtime-manifest.v1"

  def write(
    root: Path,
    contract: CarMetadataCompatibility.Contract,
    name: String,
    version: String,
    component: String
  ): Unit = {
    val runtime = contract.runtimeCompatibility
    val entries = _files(root).filterNot(_._2 == FILE_NAME).map { case (path, relative) =>
      Json.obj(
        "path" -> relative,
        "sha256" -> _sha256(path)
      )
    }
    val cncf = Json.obj(
      "minimum" -> runtime.minimum,
      "maximum" -> runtime.maximum,
      "excluded" -> runtime.excluded,
      "tested" -> runtime.tested
    )
    val manifest = Json.obj(
      "schemaVersion" -> SCHEMA_VERSION,
      "car" -> Json.obj(
        "name" -> name,
        "version" -> version,
        "component" -> component
      ),
      "runtime" -> Json.obj("cncf" -> cncf),
      "integrity" -> Json.obj(
        "algorithm" -> "SHA-256",
        "entries" -> JsArray(entries)
      )
    )
    Files.writeString(
      root.resolve(FILE_NAME),
      Json.prettyPrint(manifest) + "\n",
      StandardCharsets.UTF_8
    )
  }

  def requireValidArchive(
    archive: Path,
    contract: CarMetadataCompatibility.Contract,
    name: String,
    version: String,
    component: String,
    expectedGenerationProvenance: Option[Path]
  ): Unit = {
    val path = archive.toAbsolutePath.normalize()
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"CAR archive does not exist: $path")
    val zip =
      try
        new ZipFile(path.toFile)
      catch {
        case NonFatal(exception) =>
          RAISE.invalidArgumentFault(
            s"Prebuilt CAR is not a readable archive: ${Option(exception.getMessage).getOrElse(exception.getClass.getName)}"
          )
      }
    try {
      val entries = zip.entries().asScala.toVector.filterNot(_.isDirectory)
      val unsafepaths = entries.map(_.getName).filter(_unsafe_path).sorted
      if (unsafepaths.nonEmpty)
        RAISE.invalidArgumentFault(
          s"CAR archive contains unsafe paths: ${unsafepaths.mkString(", ")}"
        )
      val duplicates = entries.groupBy(_.getName).collect {
        case (entryname, xs) if xs.size > 1 => entryname
      }.toVector.sorted
      if (duplicates.nonEmpty)
        RAISE.invalidArgumentFault(
          s"CAR archive contains duplicate paths: ${duplicates.mkString(", ")}"
        )
      val byname = entries.map(entry => entry.getName -> entry).toMap
      val manifestentry = byname.getOrElse(
        FILE_NAME,
        RAISE.invalidArgumentFault(
          s"Prebuilt CAR requires package-generated $FILE_NAME."
        )
      )
      val manifest = _json(_bytes(zip, manifestentry), FILE_NAME)
      _require_string(manifest, "schemaVersion", SCHEMA_VERSION, FILE_NAME)
      val car = (manifest \ "car").asOpt[JsObject].getOrElse(
        RAISE.invalidArgumentFault(s"$FILE_NAME must contain car metadata.")
      )
      _require_string(car, "name", name, s"$FILE_NAME car")
      _require_string(car, "version", version, s"$FILE_NAME car")
      _require_string(car, "component", component, s"$FILE_NAME car")
      _require_runtime(manifest, contract)
      _require_integrity(zip, byname, manifest)
      val descriptorentry = byname.getOrElse(
        "component-descriptor.json",
        RAISE.invalidArgumentFault(
          "Prebuilt CAR requires component-descriptor.json."
        )
      )
      val descriptor = _json(
        _bytes(zip, descriptorentry),
        "component-descriptor.json"
      )
      _require_string(descriptor, "name", name, "component-descriptor.json")
      _require_string(
        descriptor,
        "version",
        version,
        "component-descriptor.json"
      )
      _require_component(descriptor, component, "component-descriptor.json")
      expectedGenerationProvenance.foreach(
        _require_generation_provenance(zip, byname, contract, _)
      )
    } finally {
      zip.close()
    }
  }

  private def _require_component(value: JsValue, expected: String, label: String): Unit = {
    val actual = (value \ "component").toOption.flatMap {
      case text: play.api.libs.json.JsString => Some(text.value)
      case objectvalue: JsObject => (objectvalue \ "name").asOpt[String]
      case _ => None
    }
    if (!actual.contains(expected))
      RAISE.invalidArgumentFault(s"$label component mismatch: expected=$expected actual=${actual.getOrElse("missing")}")
  }

  private def _require_runtime(
    manifest: JsValue,
    contract: CarMetadataCompatibility.Contract
  ): Unit = {
    val runtime = (manifest \ "runtime" \ "cncf").asOpt[JsObject].getOrElse(
      RAISE.invalidArgumentFault(s"$FILE_NAME must contain runtime.cncf.")
    )
    val expected = contract.runtimeCompatibility
    _require_optional_string(
      runtime,
      "minimum",
      expected.minimum,
      s"$FILE_NAME runtime.cncf"
    )
    _require_optional_string(
      runtime,
      "maximum",
      expected.maximum,
      s"$FILE_NAME runtime.cncf"
    )
    _require_strings(
      runtime,
      "excluded",
      expected.excluded,
      s"$FILE_NAME runtime.cncf"
    )
    _require_strings(
      runtime,
      "tested",
      expected.tested,
      s"$FILE_NAME runtime.cncf"
    )
  }

  private def _require_integrity(
    zip: ZipFile,
    byname: Map[String, java.util.zip.ZipEntry],
    manifest: JsValue
  ): Unit = {
    val integrity = (manifest \ "integrity").asOpt[JsObject].getOrElse(
      RAISE.invalidArgumentFault(s"$FILE_NAME must contain integrity metadata.")
    )
    _require_string(integrity, "algorithm", "SHA-256", s"$FILE_NAME integrity")
    val recorded =
      (integrity \ "entries").asOpt[Vector[JsObject]].getOrElse(
        RAISE.invalidArgumentFault(
          s"$FILE_NAME integrity.entries must be an array."
        )
      ).map { entry =>
        val path = (entry \ "path").asOpt[String].getOrElse(
          RAISE.invalidArgumentFault(
            s"$FILE_NAME integrity entry requires path."
          )
        )
        val digest = (entry \ "sha256").asOpt[String].getOrElse(
          RAISE.invalidArgumentFault(
            s"$FILE_NAME integrity entry requires sha256."
          )
        )
        if (!digest.matches("[0-9a-f]{64}"))
          RAISE.invalidArgumentFault(
            s"$FILE_NAME integrity digest is malformed for $path."
          )
        path -> digest
      }
    val recordedduplicates = recorded.groupBy(_._1).collect {
      case (path, xs) if xs.size > 1 => path
    }.toVector.sorted
    if (recordedduplicates.nonEmpty)
      RAISE.invalidArgumentFault(
        s"$FILE_NAME repeats integrity paths: ${recordedduplicates.mkString(", ")}"
      )
    val actualentries = byname - FILE_NAME
    val recordedmap = recorded.toMap
    if (recordedmap.keySet != actualentries.keySet)
      RAISE.invalidArgumentFault(
        s"$FILE_NAME archive path set mismatch: expected=${recordedmap.keySet.toVector.sorted.mkString(",")} actual=${actualentries.keySet.toVector.sorted.mkString(",")}"
      )
    actualentries.foreach { case (path, entry) =>
      val actual = _sha256(_bytes(zip, entry))
      if (recordedmap(path) != actual)
        RAISE.invalidArgumentFault(
          s"$FILE_NAME digest mismatch for $path."
        )
    }
  }

  private def _require_generation_provenance(
    zip: ZipFile,
    byname: Map[String, java.util.zip.ZipEntry],
    contract: CarMetadataCompatibility.Contract,
    expectedprovenance: Path
  ): Unit = {
    val entry = byname.getOrElse(
      "generation-provenance.json",
      RAISE.invalidArgumentFault(
        "Generated release CAR requires generation-provenance.json."
      )
    )
    val packagedbytes = _bytes(zip, entry)
    val expectedbytes = Files.readAllBytes(expectedprovenance)
    if (!MessageDigest.isEqual(packagedbytes, expectedbytes))
      RAISE.invalidArgumentFault(
        "Packaged generation-provenance.json differs from the owning project's validated generation provenance."
      )
    val temporary = Files.createTempFile("cozy-car-provenance-", ".json")
    try {
      Files.write(temporary, packagedbytes)
      GenerationProvenance.requireValidPackagedEvidence(
        temporary,
        contract.cncfCompileTarget.version,
        contract.cozyVersion
      )
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private def _files(root: Path): Vector[(Path, String)] = {
    val stream = Files.walk(root)
    try {
      val files = stream.iterator().asScala.toVector.collect {
        case path if Files.isRegularFile(path) =>
          val relative = root.relativize(path).toString.replace('\\', '/')
          path -> relative
      }.sortBy(_._2)
      val duplicates = files.groupBy(_._2).collect {
        case (relative, xs) if xs.size > 1 => relative
      }.toVector.sorted
      if (duplicates.nonEmpty)
        RAISE.invalidArgumentFault(
          s"CAR runtime manifest cannot represent duplicate archive paths: ${duplicates.mkString(", ")}"
        )
      files
    } finally {
      stream.close()
    }
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var count = input.read(buffer)
      while (count >= 0) {
        if (count > 0)
          digest.update(buffer, 0, count)
        count = input.read(buffer)
      }
    } finally {
      input.close()
    }
    digest.digest().map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").
      digest(bytes).
      map(byte => f"${byte & 0xff}%02x").
      mkString

  private def _bytes(
    zip: ZipFile,
    entry: java.util.zip.ZipEntry
  ): Array[Byte] = {
    val input = zip.getInputStream(entry)
    try input.readAllBytes()
    finally input.close()
  }

  private def _json(bytes: Array[Byte], source: String): JsValue =
    try
      Json.parse(bytes)
    catch {
      case NonFatal(exception) =>
        RAISE.invalidArgumentFault(
          s"$source is not valid JSON: ${Option(exception.getMessage).getOrElse(exception.getClass.getName)}"
        )
    }

  private def _unsafe_path(path: String): Boolean = {
    val normalized = Option(path).map(_.replace('\\', '/')).getOrElse("")
    normalized.isEmpty ||
      normalized.startsWith("/") ||
      normalized.split('/').contains("..")
  }

  private def _require_string(
    json: JsValue,
    key: String,
    expected: String,
    source: String
  ): Unit = {
    val actual = (json \ key).asOpt[String]
    if (actual != Some(expected))
      RAISE.invalidArgumentFault(
        s"$source $key mismatch: expected=$expected actual=${actual.getOrElse("missing")}"
      )
  }

  private def _require_optional_string(
    json: JsValue,
    key: String,
    expected: Option[String],
    source: String
  ): Unit = {
    val actual = (json \ key).asOpt[String]
    if (actual != expected)
      RAISE.invalidArgumentFault(
        s"$source $key mismatch: expected=${expected.getOrElse("missing")} actual=${actual.getOrElse("missing")}"
      )
  }

  private def _require_strings(
    json: JsValue,
    key: String,
    expected: Vector[String],
    source: String
  ): Unit = {
    val actual = (json \ key).asOpt[Vector[String]]
    if (actual != Some(expected))
      RAISE.invalidArgumentFault(
        s"$source $key mismatch: expected=${expected.mkString(",")} actual=${actual.getOrElse(Vector.empty).mkString(",")}"
      )
  }
}
