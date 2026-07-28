package cozy.modeler

import java.nio.file.Path
import org.goldenport.RAISE
import cozy.compatibility.CncfRuntimeDescriptorContract
import cozy.config.CozyProjectYamlConfig

/*
 * @since   Jul. 15, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] final case class PredefinedResultField(
  name: String,
  datatype: String,
  multiplicity: String
)

private[cozy] final case class PredefinedResultDefinition(
  name: String,
  runtimeclassname: String,
  resultfields: Vector[PredefinedResultField]
)

private[cozy] final case class PredefinedResultCatalog(
  schemaversion: String,
  cncfversion: String,
  results: Vector[PredefinedResultDefinition]
) {
  private lazy val _result_map = results.map(x => x.name -> x).toMap

  def names: Set[String] = _result_map.keySet

  def get(name: String): Option[PredefinedResultDefinition] =
    _result_map.get(name)
}

private[cozy] object PredefinedResultCatalog {
  val empty: PredefinedResultCatalog = PredefinedResultCatalog("", "", Vector.empty)

  def loadRuntimeDescriptor(path: Path, expectedCncfVersion: String): PredefinedResultCatalog =
    fromValidatedDescriptor(
      CncfRuntimeDescriptorContract.requireValidDescriptor(
        path,
        expectedCncfVersion,
        None,
        "predefined-result-catalog"
      )
    )

  def fromValidatedDescriptor(
    validatedDescriptor: CncfRuntimeDescriptorContract.ValidatedDescriptor
  ): PredefinedResultCatalog = {
    val validated = validatedDescriptor
    val path = validated.path
    val config = validated.config
    val actualversion = validated.targetVersion
    val prefix = "predefinedResults"
    val schemaversion = CncfRuntimeDescriptorContract.SUPPORTED_PREDEFINED_RESULT_SCHEMA
    val names = config.list(s"$prefix.resultNames")
    if (names.distinct.size != names.size)
      RAISE.invalidArgumentFault(s"CNCF runtime descriptor has duplicate predefined Result names: $path")
    val results = names.map { name =>
      val resultprefix = s"$prefix.results.$name"
      val runtimeclassname = config.value(s"$resultprefix.runtimeClassName").getOrElse {
        RAISE.invalidArgumentFault(s"CNCF predefined Result $name requires runtimeClassName: $path")
      }
      val fieldnames = config.list(s"$resultprefix.fields")
      val fields = fieldnames.map { fieldname =>
        val fieldprefix = s"$resultprefix.fieldDefinitions.$fieldname"
        PredefinedResultField(
          name = fieldname,
          datatype = config.value(s"$fieldprefix.datatype").getOrElse {
            RAISE.invalidArgumentFault(s"CNCF predefined Result $name field $fieldname requires datatype: $path")
          },
          multiplicity = config.value(s"$fieldprefix.multiplicity").getOrElse("1")
        )
      }
      PredefinedResultDefinition(name, runtimeclassname, fields)
    }
    PredefinedResultCatalog(schemaversion, actualversion, results)
  }
}
