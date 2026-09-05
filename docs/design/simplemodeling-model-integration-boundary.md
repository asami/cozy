# SimpleModeling Model Integration Boundary for Cozy

Status: design clarification
Date: 2026-09-05

## Purpose

This document clarifies Cozy's responsibility within the SimpleModeling model integration architecture defined upstream by SimpleModeling.org.

SimpleModeling.org owns the semantic integration specification across Object Model, Knowledge Model, and Literate Model. Cozy implements source/model metadata production and publication support; it does not become the semantic authority for BoK Terms or external RDF mappings.

## Current Cozy capabilities

Cozy already exposes the metadata hooks required for cross-model integration.

`cozy.cml.model-metadata.v1` publishes CML model elements and currently carries, among other fields:

- `kind`
- `name`
- `termId`
- `glossaryPath`
- descriptive/narrative metadata
- relationships/constraints/implementation metadata
- `rdfCandidates`

Component, service, and operation surfaces also carry `termId` and `glossaryPath` where available.

Phase 12 also established term-centered BoK metadata, CML linkage, project knowledge, RDF navigation, and model-element publication as BoK knowledge hooks.

These capabilities remain valid; this document narrows their semantic interpretation.

## Semantic authority

Cozy MUST treat `termId` as a reference to a governing BoK Term identity. Cozy does not define the canonical meaning of that Term.

For SimpleModeling.org content, the canonical Term meaning is owned by the SimpleModeling.org Glossary and consumed downstream by Textus BoK.

Conceptually:

```text
SimpleModeling.org Glossary
        |
        | canonical Term identity/definition
        v
CML model element --termId--> Term
```

The CML model element is not identical to the Term. It is a model realization or representation that can be interpreted through a typed binding by downstream consumers.

## `glossaryPath`

`glossaryPath` is a source/navigation hint tied to the current authored BoK structure. It MUST NOT replace the stable semantic identity carried by `termId`.

A file move or publication-layout change may alter a path without changing the underlying Term identity.

## `rdfCandidates`

`rdfCandidates` MUST be interpreted as candidate external semantic references only.

They are not, by themselves:

- canonical BoK concept identities;
- factual `sameAs` assertions;
- authoritative external mappings;
- replacements for a BoK-local semantic node.

A downstream BoK layer may validate, classify, or reject these candidates and turn accepted mappings into explicit, attributable knowledge.

The intended semantic path is:

```text
CML model element
      |
      | termId
      v
BoK Term
      |
      v
BoK-local semantic node
      |
      | explicit mapping
      v
External RDF node
```

Cozy should not skip the BoK Term/local-concept layer by asserting that a model element is directly equivalent to an external RDF node.

## Model-element identity

Cozy is responsible for publishing enough stable model metadata for downstream consumers to refer to CML model elements across:

- BoK visualization;
- CBD design visualization/review;
- semantic diff;
- cross-model traceability;
- Pull Request candidate review.

The current `kind` + model context + `name` metadata is useful, but the long-term stable ModelElement reference contract should follow the upstream SimpleModeling integration specification and the detailed CBD/Textus contracts.

If a stronger explicit model-element ID is introduced, it should be source-stable and should not depend on a rendered diagram, generated Scala symbol, or repository-local temporary path.

## Producer/consumer boundary

Cozy owns:

- parsing CML;
- generating `cozy.cml.model-metadata.v1`;
- publishing model-element metadata;
- retaining Term linkage declared by the source/model;
- producing BoK publication metadata and factual source topology.

Cozy does not own:

- canonical Term definitions;
- BoK-local RDF concept semantics;
- external ontology equivalence decisions;
- Term-to-model binding meaning beyond published metadata;
- BoK review findings;
- CBD design interpretation.

Those responsibilities belong to SimpleModeling.org, Textus BoK, and Textus CBD Support as appropriate.

## Relationship to Textus BoK

Textus BoK should consume Cozy-produced metadata and interpret it under the governing BoK semantics.

In particular, Textus BoK can use `termId` to connect:

```text
Term
  +-- Literate Model resources
  +-- CML/Object Model elements
  +-- BoK-local RDF relations
  +-- validated external mappings
```

Semantic candidates must not become factual Knowledge Map edges without explicit admitted evidence.

## Relationship to CNCF

CNCF may provide generic resource, reference, evidence, operation, authorization, and lifecycle mechanisms. Cozy may encode or publish metadata that uses such generic contracts.

CNCF does not define the meaning of `termId` or interpret SimpleModeling-specific cross-model semantics.

## Follow-up

Future Cozy work should:

1. preserve backward compatibility of `cozy.cml.model-metadata.v1` while clarifying `rdfCandidates` as candidate-only metadata;
2. align future ModelElement IDs/references with the SimpleModeling.org integration specification;
3. avoid introducing direct external-RDF equivalence semantics into the CML metadata producer;
4. expose enough typed relation metadata for Textus BoK and CBD Support to build their own semantic projections without re-parsing CML source;
5. treat any new cross-model relation vocabulary as an upstream SimpleModeling semantic decision, not a Cozy-local definition.
