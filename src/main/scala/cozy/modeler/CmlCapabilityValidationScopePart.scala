package cozy.modeler

import CmlSemanticFoundation.ModelElementId
import CmlCapabilitySource._
import CmlCapabilityModel._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationScope.DiagnosticKind._
import CmlCapabilityValidationSourcePart._
import CmlCapabilityValidationShapePart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityValidationScopePart {
  final case class Contract(identity: ModelElementId, kind: CapabilityKind, version: String,
    source: CapabilitySource, context: Context, path: String)

  private def _fact(identity: ModelElementId, source: CapabilitySource, path: String): Vector[Diagnostic] = {
    val context = Context(None, Option(source))
    _model_id(identity, s"$path.identity", context) ++ _source(source, s"$path.source", context) ++
      (if (identity == null) Vector.empty else _authority(source, identity.modelId, s"$path.source.modelAuthority", context))
  }

  private def _duplicates[A, K](values: Vector[A], path: String)(key: A => K,
    source: A => CapabilitySource): Vector[Diagnostic] = values.zipWithIndex.flatMap { case (value, index) =>
    values.take(index).find(previous => key(previous) == key(value)).toVector.map { previous =>
      Context(None, Option(source(value)))._issue(Ambiguous, s"$path[$index]",
        "Multiple exact scope candidates, including equal facts, are ambiguous.", Option(source(previous)).toVector)
    }
  }

  private[modeler] def _scope(scope: Scope): Vector[Diagnostic] = {
    val context = Context(None, Option(scope.source))
    val models = scope.modelfacts.zipWithIndex.flatMap { case (fact, index) =>
      val path = s"scope.modelfacts[$index]"
      _fact(fact.identity, fact.source, path) ++ _required(fact.kind, s"$path.kind", Context(None, Option(fact.source)))
    } ++ _duplicates(scope.modelfacts, "scope.modelfacts")(_.identity, _.source)
    val terms = scope.termfacts.zipWithIndex.flatMap { case (fact, index) =>
      val path = s"scope.termfacts[$index]"
      val ctx = Context(None, Option(fact.source))
      _term_id(fact.identity, s"$path.identity", ctx) ++ _source(fact.source, s"$path.source", ctx) ++
        (if (fact.identity == null) Vector.empty else _authority(fact.source, fact.identity.vocabularyId, s"$path.source.modelAuthority", ctx))
    } ++ _duplicates(scope.termfacts, "scope.termfacts")(_.identity, _.source)
    val relations = scope.relationfacts.zipWithIndex.flatMap { case (fact, index) =>
      val path = s"scope.relationfacts[$index]"
      val ctx = Context(None, Option(fact.source))
      _relation_id(fact.identity, s"$path.identity", ctx) ++ _source(fact.source, s"$path.source", ctx) ++
        (if (fact.identity == null) Vector.empty else _authority(fact.source, fact.identity.vocabularyId, s"$path.source.modelAuthority", ctx))
    } ++ _duplicates(scope.relationfacts, "scope.relationfacts")(_.identity, _.source)
    val profiles = scope.profilefacts.zipWithIndex.flatMap { case (fact, index) =>
      val path = s"scope.profilefacts[$index]"
      val ctx = Context(None, Option(fact.source))
      _profile_id(fact.identity, s"$path.identity", ctx) ++ _source(fact.source, s"$path.source", ctx) ++
        (if (fact.identity == null) Vector.empty else _authority(fact.source, fact.identity.vocabularyId, s"$path.source.modelAuthority", ctx))
    } ++ _duplicates(scope.profilefacts, "scope.profilefacts")(_.identity, _.source)
    val usages = scope.usagecontracts.zipWithIndex.flatMap { case (fact, index) =>
      val path = s"scope.usagecontracts[$index]"
      _fact(fact.identity, fact.source, path) ++ _text(fact.contractversion, s"$path.contractversion", Context(None, Option(fact.source)))
    } ++ _duplicates(scope.usagecontracts, "scope.usagecontracts")(x => (x.identity, x.contractversion), _.source)
    val components = scope.componentcontracts.zipWithIndex.flatMap { case (fact, index) =>
      val path = s"scope.componentcontracts[$index]"
      _fact(fact.identity, fact.source, path) ++ _text(fact.contractversion, s"$path.contractversion", Context(None, Option(fact.source))) ++
        scope.usagecontracts.find(_.identity == fact.identity).toVector.map { previous =>
          Context(None, Option(fact.source))._issue(WrongKind, s"$path.identity", "A catalog Capability identity has one fixed family.", Option(previous.source).toVector)
        }
    } ++ _duplicates(scope.componentcontracts, "scope.componentcontracts")(x => (x.identity, x.contractversion), _.source)
    val targets = scope.targetcontracts.zipWithIndex.flatMap { case (fact, index) =>
      val path = s"scope.targetcontracts[$index]"
      val ctx = Context(None, Option(fact.source))
      _required(fact.kind, s"$path.kind", ctx) ++ _fact(fact.identity, fact.source, path) ++
        _component(fact.component, s"$path.component", ctx) ++
        _text(fact.contractversion, s"$path.contractversion", ctx) ++ _text(fact.generatedabiversion, s"$path.generatedabiversion", ctx)
    } ++ _duplicates(scope.targetcontracts, "scope.targetcontracts")(x =>
      (x.identity, x.component, x.kind, x.contractversion, x.generatedabiversion), _.source)
    _text(scope.scopeid, "scope.scopeid", context) ++ _source(scope.source, "scope.source", context) ++
      models ++ terms ++ relations ++ profiles ++ usages ++ components ++ targets
  }

  private[modeler] def _current(documents: Vector[CapabilityDocument]): Vector[Contract] =
    documents.zipWithIndex.flatMap { case (document, index) =>
      val path = s"documents[$index].model"
      document.model.componentUsageCapabilities.zipWithIndex.map { case (value, offset) =>
        Contract(value.identity, ComponentUsageKind, value.contractVersion, value.source,
          _document(document.scope, value.source), s"$path.componentUsageCapabilities[$offset]")
      } ++ document.model.componentCapabilities.zipWithIndex.map { case (value, offset) =>
        Contract(value.identity, ComponentKind, value.contractVersion, value.source,
          _document(document.scope, value.source), s"$path.componentCapabilities[$offset]")
      }
    }

  private[modeler] def _catalog(scope: Scope): Vector[Contract] =
    scope.usagecontracts.zipWithIndex.map { case (value, index) =>
      Contract(value.identity, ComponentUsageKind, value.contractversion, value.source,
        Context(None, Option(value.source)), s"scope.usagecontracts[$index]")
    } ++ scope.componentcontracts.zipWithIndex.map { case (value, index) =>
      Contract(value.identity, ComponentKind, value.contractversion, value.source,
        Context(None, Option(value.source)), s"scope.componentcontracts[$index]")
    }

  private[modeler] def _snapshot(current: Vector[Contract], catalog: Vector[Contract]): Vector[Diagnostic] = {
    val duplicates = current.zipWithIndex.flatMap { case (value, index) =>
      current.take(index).find(_.identity == value.identity).toVector.map { previous =>
        value.context._issue(DuplicateDefinition, s"${value.path}.identity",
          "Repeated current Capability identity across the declaring snapshot.", Vector(previous.source))
      }
    }
    val conflicts = catalog.flatMap { value =>
      current.filter(_.identity == value.identity).flatMap { previous =>
        if (previous.kind != value.kind) Vector(value.context._issue(WrongKind, s"${value.path}.identity",
          "Catalog and current Capability families differ.", Vector(previous.source)))
        else if (previous.version == value.version) Vector(value.context._issue(Ambiguous, s"${value.path}.contractversion",
          "Current and catalog supply the same exact identity/version candidate.", Vector(previous.source)))
        else Vector.empty
      }
    }
    // Catalog/input conflicts precede current document duplicate diagnostics.
    conflicts ++ duplicates
  }

  private[modeler] def _model_candidates(identity: ModelElementId, scope: Scope,
    contracts: Vector[Contract]): Vector[(ModelKind, Vector[CapabilitySource])] = {
    val explicit = scope.modelfacts.filter(_.identity == identity).map(x => x.kind -> Vector(x.source))
    val families = Vector(ComponentUsageKind, ComponentKind).flatMap { family =>
      val matches = contracts.filter(x => x.identity == identity && x.kind == family)
      if (matches.isEmpty) Vector.empty
      else Vector((if (family == ComponentUsageKind) ModelKind.ComponentUsageCapability else ModelKind.ComponentCapability) -> matches.map(_.source))
    }
    explicit ++ families
  }

  private[modeler] def _resolve(identity: ModelElementId, version: String, kind: CapabilityKind,
    contracts: Vector[Contract], path: String, context: Context): Vector[Diagnostic] = {
    val identities = contracts.filter(_.identity == identity)
    val family = identities.filter(_.kind == kind)
    val exact = family.filter(_.version == version)
    if (identities.isEmpty) Vector(context._issue(DanglingReference, s"$path.identity", "No exact qualified Capability identity."))
    else if (family.isEmpty || identities.exists(_.kind != kind))
      Vector(context._issue(WrongKind, s"$path.identity", "Capability family differs from the typed reference.", identities.map(_.source)))
    else if (exact.isEmpty) Vector(context._issue(Incompatible, s"$path.contractVersion", "No exact requested contract version.", family.map(_.source)))
    else if (exact.size != 1) Vector(context._issue(Ambiguous, path, "Multiple exact Capability candidates.", exact.map(_.source)))
    else Vector.empty
  }
}
