# Core-based Knowledge Compilation for Contextual Retrieval

Date: 2026-11-26

Status: exploratory design rationale; non-normative journal. No Phase is allocated.

## Motivation

Cozy's Core-to-natural-language article generation suggests a second use: generating retrieval-oriented knowledge representations from the same semantic source. Conventional RAG starts with prose and reconstructs context lost during chunking. A Core-based approach can preserve explicit semantic structure before rendering, treating retrieval preparation as knowledge compilation rather than only document preprocessing.

## Contextual Retrieval

Anthropic's [Contextual Retrieval](https://www.anthropic.com/research/contextual-retrieval) (2024-09-19) enriches chunks with document-level context before embedding and lexical indexing, optionally followed by reranking. Combine this approach with Core rather than replacing it: derive explicit context deterministically and use generative AI for missing context and natural-language expansion.

## Candidate compilation targets

- Human-readable articles from Core.
- Semantic chunks aligned with meaningful units rather than only token windows.
- Contextual retrieval text derived from hierarchy, definitions, explicit relationships and applicability conditions.
- Multiple retrieval views of the same knowledge: definition, usage, rationale, constraints and related concepts.
- Structured metadata and RDF projections where relationships are explicitly supported.

Use BoK/LexiDox definitions and explicit Core relationships where available. Distinguish source assertions, deterministic derivations and AI-generated hypotheses. Do not silently promote inferred relationships to authoritative knowledge.

## Provenance and lifecycle

Preserve stable source identifiers, Core version, source spans or semantic unit identifiers, original evidence links, generation method and model/prompt version where applicable. Link multiple views to the same underlying knowledge unit. Track dependencies so changed Core or definitions can invalidate and regenerate affected outputs and indexes. Preserve access restrictions across derived artifacts.

## Responsibility boundaries

- SmartDox/LexiDox: knowledge representation.
- Cozy: compilation and generation of retrieval-oriented representations, not the RAG query engine.
- Knowledge Workbench: editing, refinement and human admission.
- Knowledge Lake: original materials, knowledge, derived artifacts, lineage and regeneration lifecycle.
- SIE: embeddings, lexical/vector indexes, retrieval and reranking.
- KnowledgeHub Commons: knowledge-space context, terminology and pattern languages.

The precise integration contracts remain to be designed.

## Comparative PoC

Compare four approaches on the same corpus and query set:
A. Conventional prose-based RAG.
B. Anthropic-style Contextual Retrieval.
C. Core-based deterministic contextual retrieval.
D. Core-based retrieval with AI contextual enrichment.

Hold embedding models, retrieval budgets and reranking settings constant where practical; separately measure their effects. Evaluate recall@k, ranking quality, grounded answer accuracy, provenance traceability, index size, AI token/cost overhead and incremental regeneration cost. Include ambiguous terminology, cross-section questions and changed source knowledge. No performance or cost improvement is claimed before measurement.

## Executable Knowledge

Core may serve as a common knowledge source for human-readable prose, AI-retrievable representations, graph exploration and, where an explicit executable model exists, executable artifacts such as CML-based models. This is a possible bridge between SimpleModeling and KnowledgeHub. Not every Core statement is executable, and execution semantics must be separately defined and validated.

## Open questions

Inspect existing Cozy Core and generation implementation before specifying extensions. Determine chunk identity, context budgets, output schema, admission boundaries, RDF mapping and incremental compilation contracts with SIE and Knowledge Lake. Consider this a cross-project research candidate, not an accepted implementation plan or finalized specification.
