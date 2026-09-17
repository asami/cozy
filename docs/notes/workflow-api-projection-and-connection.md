# Workflow API Projection and Connection

## Principle

Workflow-to-Workflow composition should be written against a typed generated Workflow API, not against Continuation mechanics or transport APIs.

```text
Caller Action
  -> Generated Workflow API
  -> Workflow Binding
       -> local/direct
       -> REST remote
  -> Callee Workflow
```

The Action implementation must not know whether the callee is local, remote, currently suspended, or backed by another Participant Workflow.

## Generated API contract

A Workflow interface projection preserves:

- operation identity
- typed input/result
- completion semantics
- context/evidence requirements where externally relevant
- durable call identity/correlation semantics

Transport details are excluded.

## Connection model

CML may later declare a logical connection from a caller-visible Workflow API to a callee Workflow interface. Cozy can then generate type-safe API/proxy artifacts and compatibility checks.

Runtime/deployment configuration selects the provider binding:

```text
WorkflowBinding
  Local
  Rest
  future transport
```

REST URL, credentials, discovery and deployment topology are runtime configuration rather than Workflow semantics.

## Durable calls

A Workflow API may represent a durable call rather than a stack-bound method call. The generated programming API should therefore preserve suspend/resume/correlation behavior even when local syntax looks method-like.

## Current scope

Phase 62 only needs to generate/retain enough Workflow SPI metadata for future API projection. Actual workflow-to-workflow proxy generation and REST transport are roadmap work after the Skill-driven Workflow foundation is stable.
