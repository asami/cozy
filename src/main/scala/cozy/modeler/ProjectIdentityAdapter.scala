package cozy.modeler

import org.goldenport.cncf.component.identity.{
  ComponentId,
  ComponentIdentityProjection,
  ComponentIdentityResult,
  ComponentLocalId,
  ComponentNamespace
}

import scala.collection.JavaConverters._

private[cozy] object ProjectIdentityAdapter {
  def projection(
    input: ProjectIdentityInput
  ): Either[ComponentIdentityResult.Error, ComponentIdentityProjection] =
    _component_id(input).map(ComponentIdentityProjection.of)

  def validateNoScopedCollisions(
    inputs: Vector[ProjectIdentityInput]
  ): Either[ComponentIdentityResult.Error, Unit] =
    _component_ids(inputs).flatMap { componentids =>
      _either(ComponentIdentityProjection.validateNoScopedCollisions(componentids.asJava)).map(_ => ())
    }

  private def _component_id(
    input: ProjectIdentityInput
  ): Either[ComponentIdentityResult.Error, ComponentId] =
    _either(ComponentNamespace.parse(input.namespace)).flatMap { namespace =>
      _either(ComponentLocalId.parse(input.localid)).map { localid =>
        ComponentId.of(namespace, localid)
      }
    }

  private def _component_ids(
    inputs: Vector[ProjectIdentityInput]
  ): Either[ComponentIdentityResult.Error, Vector[ComponentId]] =
    inputs.foldLeft[Either[ComponentIdentityResult.Error, Vector[ComponentId]]](Right(Vector.empty)) {
      case (Right(componentids), input) => _component_id(input).map(componentids :+ _)
      case (failure @ Left(_), _) => failure
    }

  private def _either[A](
    result: ComponentIdentityResult[A]
  ): Either[ComponentIdentityResult.Error, A] =
    if (result.isSuccess())
      Right(result.value().get())
    else
      Left(result.error().get())
}
