# Phase 62 Addendum: Workflow API Projection Roadmap

Status: planned / forward-compatibility clarification

## Phase 62 sequence requirement

Phase 62.1 fixes the SPI metadata and Phase 62.2 generates it. The frozen ABI
must retain sufficient typed metadata for future projection into caller-side
Workflow APIs without changing core Workflow/Operation semantics.

Retain at least:

- stable operation identity
- typed input/result
- completion contract
- externally relevant context/evidence contract
- capability/constraint metadata where generic
- durable invocation/correlation identity requirements

## Non-goals

The Phase 62 sequence does not implement:

- generated caller-side Workflow proxy APIs
- Workflow-to-Workflow connection syntax
- local/direct Workflow connector
- REST Workflow connector
- service discovery/deployment binding
- durable `WorkflowCall` programming API

These are follow-up work after Skill-driven Continuation execution is closed.

## Compatibility acceptance

A later phase must generate local/direct and REST-backed proxies from the frozen
Phase 62.1/62.2 SPI contract without requiring caller Actions to depend on
transport or Continuation internals.
