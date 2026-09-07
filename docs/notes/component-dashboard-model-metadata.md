# Component Dashboard Model Metadata

Date: 2026-09-07

## Context

Textus CBD Support is evolving its Dashboard from a Review-oriented presentation into a general Component Dashboard. The Dashboard will provide a Content View with navigation to DomainModel View and Use Case View.

Cozy owns CML/model transformation and publication concerns relevant to this work. The Dashboard should not reconstruct model semantics heuristically from rendered documentation or source text. Cozy should preserve and publish machine-readable model metadata sufficient for faithful projections.

## Required Model Perspectives

DomainModel View is expected to contain:

- Static Model
  - Structure View
  - Classification View
- Dynamic Model
  - Workflow View
  - StateMachine Detail

Use Case View is a separate goal-oriented view linked to Workflow and Domain Model elements.

## Structure Metadata

Structure View requires strict distinction among composition, aggregation, and association.

Published model metadata should preserve, where declared by the model:

- relation kind;
- source and target model identities;
- whole/part or endpoint roles;
- cardinality;
- navigability;
- aggregate membership/boundary;
- ownership semantics;
- independent existence semantics;
- reassignment/reparenting policy;
- lifecycle propagation semantics.

Composition and aggregation must not collapse into a generic association in generated metadata. Their lifecycle distinction is intended to have executable consequences in CNCF and explanatory consequences in the CBD Dashboard.

## Classification Metadata

The Dashboard Classification View combines:

- generalization;
- trait;
- powertype.

Cozy should preserve these as distinct semantic constructs while providing enough cross-reference metadata to visualize them together. Multiple powertype classification dimensions for the same modeled concept must remain distinguishable.

## Dynamic Metadata

Workflow is the dynamic overview axis. Metadata should preserve Workflow identity, activities, control/flow relationships, participants, affected domain elements, and declared state effects where available.

StateMachine is a lifecycle deep dive. Metadata should preserve states, transitions, triggers, guards, actions, related domain element identity, and cross-references to Workflow activities, operations, and events where the source model declares them.

## Use Case Metadata

Use Case metadata should preserve actor, goal, preconditions, trigger, flows, postconditions, participating domain elements, operations/events, collaborating components/systems, and links to realizing Workflows where modeled.

## Cross-View Identity

Stable model-element identity is important. The Dashboard should be able to navigate without name-based guessing among:

    Use Case -> Workflow -> StateMachine -> Entity

and in the reverse direction.

The same principle applies to Structure and Classification projections. Cozy-generated metadata should therefore retain stable cross-references rather than flattening each diagram/view into an isolated document.

## Boundary

Cozy supplies faithful model metadata and publication artifacts. Textus CBD Support owns Dashboard projection and navigation. CNCF owns runtime realization/enforcement where model semantics such as lifecycle ownership have executable consequences.
