Literate Model Concept (SimpleModeling)
=======================================

status=draft
category=concept

See also (implementation-aligned latest snapshot):
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/literate-model-specification-latest-2026-03-24.md`

# Overview

A Literate Model is a modeling approach in which a specification document
serves simultaneously as:

- a human-readable description of a domain
- an AI-readable semantic representation
- a machine-executable DSL

In this approach, the specification is not separated into "document" and "code".
Instead, both are unified into a single artifact.

The model is expressed as a structured document that contains:

- executable structural definitions
- machine-consumable metadata
- narrative explanations

This unified representation eliminates the gap between specification and implementation.

---

# Core Idea

A Literate Model integrates three layers into one document:

1. Structural DSL Layer  
   Defines formal structures such as Value, Entity, Service, and Operation.

2. Metadata DSL Layer  
   Defines descriptive information used by systems such as CLI help,
   OpenAPI, UI generation, and AI agents.

3. Narrative Layer  
   Provides human-readable explanations, background, rationale,
   and contextual meaning.

These layers coexist and are interpreted differently depending on the consumer.

---

# Structural DSL Layer

The structural DSL layer is defined primarily in sections such as VALUE.

It provides:

- type definitions
- field structures
- constraints

This layer is:

- strictly structured
- machine-executable
- used for code generation

---

# Metadata DSL Layer

The metadata DSL layer includes textual elements that affect generated artifacts
and external contracts.

Examples:

- SUMMARY
- DESCRIPTION
- TITLE
- NAME
- ALIASES
- STATUS
- EXAMPLE

These elements are not merely documentation.
They are interpreted as part of the DSL because they influence:

- CLI help output
- OpenAPI specifications
- UI labels and descriptions
- AI capability descriptions

---

# Narrative Layer

The narrative layer provides explanatory context.

It includes:

- conceptual explanation
- interpretation rules
- design rationale
- background knowledge

This layer is:

- human-readable
- AI-interpretable
- not directly executable

---

# Design Principles

1. Single Source of Truth  
   The specification document itself is the authoritative model.

2. Co-location of Structure and Meaning  
   Structural definitions and their meanings are placed close together.

3. Separation of Concerns by Interpretation  
   The same document is interpreted differently by:
   - compilers (DSL)
   - generators (metadata)
   - humans and AI (narrative)

4. Executable Specification  
   The specification is not passive; it produces code, APIs, and behaviors.

---

# Implications

- No duplication between specification and implementation
- Direct generation of:
  - code
  - APIs
  - documentation
- Improved AI understanding due to co-located semantics
- Strong alignment with model-driven development

---

# Summary

A Literate Model is a unified specification format in which
structure, metadata, and narrative coexist and together form
an executable and meaningful domain model.

---

======================================================================
Section Classification Specification
======================================================================

status=draft
category=specification

# Overview

This section defines how section names in a Literate Model document
are classified and interpreted.

Each section is categorized into one of the following:

- Structural DSL sections
- Metadata DSL sections
- Narrative sections

---

# Structural DSL Sections

These sections define executable model structures.

They must be machine-readable and strictly structured.

## Standard Sections

- VALUE
- ENTITY
- SERVICE
- OPERATION
- PARAMETER
- EVENT
- RULE

## Characteristics

- formal syntax
- deterministic interpretation
- used for code generation

---

# Metadata DSL Sections

These sections define machine-consumable descriptive metadata.

They influence generated artifacts and external interfaces.

## Standard Sections

- TITLE
- NAME
- SUMMARY
- DESCRIPTION
- ALIASES
- STATUS
- VERSION
- SINCE
- DEPRECATED
- EXAMPLE
- DEFAULT
- REQUIRED

## Characteristics

- textual but semantically significant
- used by:
  - CLI help
  - OpenAPI
  - UI
  - AI agents

---

# Narrative Sections

These sections provide explanatory and contextual information.

They are not directly used for code generation.

## Recommended Sections

- Overview
- Background
- Semantics
- Usage
- Mapping
- Validation
- Formatting
- Design
- Notes
- Rationale
- NonGoals
- Example (narrative usage)

## Characteristics

- free-form text
- human-readable
- AI-interpretable
- no strict schema

---

# Section Usage Guidelines

1. Structural definitions must be placed in DSL sections (e.g., VALUE).

2. Metadata affecting generated outputs must be placed in metadata DSL sections.

3. Explanatory content should be placed in narrative sections.

4. Avoid duplication between sections.

5. Keep structure and its explanation close to each other when possible.

---

# Design Intent

The goal of this classification is to:

- enable a single document to serve multiple purposes
- allow precise machine interpretation
- maintain rich human-readable documentation
- support AI-assisted development

---

# Summary

Section names are not merely formatting elements.
They define how each part of the document is interpreted:

- DSL sections define structure
- metadata sections define machine-consumable description
- narrative sections define meaning and context

Together, they form a complete Literate Model.
