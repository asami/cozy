# Failure Model source lowering

*Since / Version:* Oct. 3, 2026

The [source contract](../spec/failure-model-source-contract.md) fixes the design.
`FailureModel.scala` holds the immutable public shapes and ordered pure projector.
`FailureModelCml.scala` owns a cohesive source scanner and normalizer. Modeler adds
only a pre-construction admission call; its public constructor is unchanged.

Recover LogicalSection values from Kaleidox divisions using the established
Product technique. Remove only promoted root references already contained in
another original tree, then traverse the original positions and full paths. The
scanner admits execution definitions, component selectors and the three owner
delta positions, rejecting reserved vocabulary everywhere else. No source text
rewriting or upstream ownership API is introduced.

Parse direct leaf prologue lines as the closed `field = value` grammar, retaining
duplicates and empty values for diagnostics rather than collapsing into a map.
Collect typed issues locally; return Left before projecting any successful model.
Resolve authored owners against Kaleidox's explicit component and service models.
Use service operation identities from their actual owning service projection.
Capture original declaration locations, including owners without deltas.

Project each selected component from defaults and its own delta. Project each
resolved service from those inputs plus its own delta; project each operation
with that service's chain plus its own delta. Fold immutable vectors, preserving
default positions and appending origin chains. Validation belongs to normalization;
the projector may reject an invalid caller invariant by DbC.

Cozy's effective metadata remains development metadata. CNCF Phase 91 defines
and validates its canonical ResolvedFailureModel. Accepted S72.2 passes this
metadata into additive generation under the
[ABI contract](../spec/failure-model-abi-contract.md); SOURCE itself changed no
generator or runtime API. The authored
[recipient handoff](../phase/phase-72-cncf-handoff.md) records the fixture oracle,
accepted SOURCE/ABI commits and recipient prerequisites. HANDOFF acceptance and
canonical receiver/runtime integration remain pending.
