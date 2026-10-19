package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.record.v2.XString
import org.simplemodeling.model._
import scala.util.Try

/*
 * @since   Oct. 19, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object PowertypePersistenceCodes {
  def requireValid(model: SimpleModel): Unit = {
    val declarations = _declarations(model.elements)
    declarations.collect { case p: MPowertype => p }.foreach(_require_explicit_codes)
    declarations.collect { case p: MEntity => p }.foreach { entity =>
      _require_persistent_codes(entity, Vector.empty, Set.empty, declarations)
    }
  }

  private def _declarations(elements: Vector[MElement]): Vector[MObject] =
    elements.flatMap {
      case p: MPackage => _declarations(p.elements)
      case p: MObject => Vector(p)
      case _ => Vector.empty
    }

  private def _require_explicit_codes(powertype: MPowertype): Unit = {
    val codes = powertype.kinds.flatMap { kind =>
      kind.value.map { raw =>
        val code = Option(raw).filter(_.matches("[+-]?[0-9]+")).flatMap(value => Try(value.toInt).toOption)
        code.getOrElse(RAISE.syntaxErrorFault(
          s"POWERTYPE ${powertype.qualifiedName} kind ${kind.name}: invalid explicit signed base-10 Int persistence code '$raw'."
        )) -> kind.name
      }
    }
    codes.groupBy(_._1).toVector.sortBy(_._1).foreach { case (code, kinds) =>
      if (kinds.size > 1)
        RAISE.syntaxErrorFault(
          s"POWERTYPE ${powertype.qualifiedName} kinds ${kinds.map(_._2).mkString(", ")}: duplicate persistence code $code."
        )
    }
  }

  private def _require_persistent_codes(
    declaration: MObject,
    path: Vector[String],
    visited: Set[String],
    declarations: Vector[MObject]
  ): Unit = {
    val identity = declaration.qualifiedName
    if (!visited.contains(identity)) {
      val nextvisited = visited + identity
      declaration match {
        case p: MPowertype =>
          p.kinds.foreach { kind =>
            kind.value match {
              case None =>
                RAISE.syntaxErrorFault(
                  s"POWERTYPE ${p.qualifiedName} kind ${kind.name}: missing explicit persistence code for persistent use ${path.mkString(" -> ")}."
                )
              case Some(raw) =>
                val code = raw.toInt
                if (code < Short.MinValue.toInt || code > Short.MaxValue.toInt)
                  RAISE.syntaxErrorFault(
                    s"POWERTYPE ${p.qualifiedName} kind ${kind.name}: persistence code $code is outside signed16 XShort -32768..32767 for persistent use ${path.mkString(" -> ")}."
                  )
            }
          }
        case _ =>
          declaration.attributes.foreach { attribute =>
            val nextpath = path :+ s"$identity.${attribute.name}"
            val target = attribute.attributeType match {
              case p: MObjectAttributeType => _resolve(p.ref, declaration, declarations)
              case p: MDataType if p.resolveDeclaredType && p.datatype == XString && p.name.nonEmpty && !p.name.equalsIgnoreCase("string") =>
                val ref = if (p.name.contains(".")) MObjectRef.create(p.name) else MObjectRef(p.affiliation, p.name)
                _resolve(ref, declaration, declarations)
              case _ => None
            }
            target.foreach(_require_persistent_codes(_, nextpath, nextvisited, declarations))
          }
          val inherited = declaration.base.toList ++ declaration.traits.map { ref =>
            MObjectRef(ref.packageRef, ref.traitName)
          }
          inherited.flatMap(_resolve(_, declaration, declarations)).foreach { parent =>
            _require_persistent_codes(parent, path, nextvisited, declarations)
          }
      }
    }
  }

  private def _resolve(
    ref: MObjectRef,
    owner: MObject,
    declarations: Vector[MObject]
  ): Option[MObject] = {
    val exact = declarations.find(p => p.packageName == ref.packageName && p.name == ref.objectName)
    if (ref.packageName.nonEmpty)
      exact
    else
      exact.orElse(declarations.find(p => p.packageName == owner.packageName && p.name == ref.objectName)).orElse {
        declarations.filter(_.name == ref.objectName) match {
          case Vector(single) => Some(single)
          case _ => None
        }
      }
  }
}
