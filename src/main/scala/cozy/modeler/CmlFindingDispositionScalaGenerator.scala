package cozy.modeler

import org.goldenport.context.Conclusion
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.realm.Realm
import play.api.libs.json.{JsString, Json}
import CmlFindingDisposition._

/** Projects CML properties; the generated consumer invokes the CNCF V1 decoder.
 * @since   Nov.  5, 2026
 * @version Nov.  9, 2026
 */
private[modeler] object CmlFindingDispositionScalaGenerator {
  def fromModel(model: KaleidoxModel): Either[Vector[Diagnostic], Option[Realm]] = {
    val inspection = CmlFindingDisposition.inspect(model)
    if (!inspection.isValid) Left(inspection.findings)
    else if (inspection.declarations.isEmpty) Right(None)
    else {
      val builder = Realm.Builder()
      inspection.declarations.groupBy(_.owner).toVector.sortBy { case (owner, _) => owner.scope }.foreach {
        case (owner, declarations) =>
          val namespace = owner.packageName + ".finding"
          val classname = owner.name + "FindingDispositions"
          val properties = declarations.map(_.properties).sortBy(p => (p.scope, p.target, p.rule, p.finding))
          val values = properties.map { p =>
            "    FindingDispositionRepresentation.Properties(" + Vector(p.abiVersion.toString,
              _quote(p.rule), _quote(p.finding), _quote(p.scope), _quote(p.target), _quote(p.kind),
              _quote(p.reason), _quote(p.provenance)).mkString(", ") + ")"
          }.mkString(",\n")
          val source = s"""package ${namespace.split("\\.").map(part => "`" + part + "`").mkString(".")}
            |
            |import org.goldenport.Consequence
            |import org.goldenport.cncf.finding.{FindingDispositionRepresentation, FindingDispositionV1}
            |
            |// Projection of CML properties. Regeneration follows CML; this is not an approval record.
            |object $classname {
            |  val properties: Vector[FindingDispositionRepresentation.Properties] = Vector(
            |$values
            |  )
            |  def declarationsC: Consequence[Vector[FindingDispositionV1.Declaration]] =
            |    properties.foldLeft(Consequence.success(Vector.empty[FindingDispositionV1.Declaration])) { (prior, value) =>
            |      prior.flatMap(values => FindingDispositionRepresentation.decodeC(value).map(values :+ _))
            |    }
            |}
            |""".stripMargin
          builder.set("target/scala-3.3.8/src_managed/main/scala/" + namespace.replace('.', '/') + "/" + classname + ".scala", source)
      }
      Right(Some(builder.build()))
    }
  }

  def conclusion(findings: Vector[Diagnostic]): Conclusion =
    Conclusion.invalidArgumentFault(findings.map(f => s"${f.code}: declaration=${f.declaration}; property=${f.property.getOrElse("")}; ${f.message}")
      .mkString("; ")).withData(findings)
  def libraryConclusion: Conclusion = Conclusion.invalidArgumentFault(
    "LIBRARY_GENERATION_UNSUPPORTED_RUNTIME: category=finding-disposition requires --generation-target cncf")
  private def _quote(value: String): String =
    Json.stringify(JsString(value)).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}
