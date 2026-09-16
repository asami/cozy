# Phase 62 Addendum: Workflow API Projection Roadmap

Status: planned / forward-compatibility clarification

## Phase 62 requirement

The Workflow SPI/generated ABI must retain sufficient typed metadata to support future projection into caller-side Workflow APIs without changing core Workflow/Operation semantics.

Retain at least:

- stable operation identity
- typed input/result
- completion contract
- externally relevant context/evidence contract
- capability/constraint metadata where generic
- durable invocation/correlation identity requirements

## Non-goals

Phase 62 does not implement:

- generated caller-side Workflow proxy APIs
- Workflow-to-Workflow connection syntax
- local/direct Workflow connector
- REST Workflow connector
- service discovery/deployment binding
- durable `WorkflowCall` programming API

These are follow-up work after Skill-driven Continuation execution is closed.

## Compatibility acceptance

A later phase must be able to generate both local/direct and REST-backed proxies from the Phase 62 Workflow SPI contract without requiring caller Action implementations to depend on transport or Continuation internals.
