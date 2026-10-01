# Workflow Retry and Timeout Source Lowering

Status: in-progress; source-contract acceptance pending
Updated: 2026-10-02

## Workflow wrapper ownership

The [source contract](../spec/workflow-retry-timeout-source-contract.md)
attaches invocation policies to `WorkflowDefinition`. An appended defaulted
`invocationPolicies` vector preserves old constructor calls and absent
metadata. `WorkflowInvocationPolicy` retains policy identity, declared
`WorkflowInvocationTarget.Action` or `.Participant`, canonical Action identity,
optional `WorkflowRetryPolicy`, optional per-attempt timeout and source identity.
These immutable public source types carry metadata only.

## Normalization sequence

`CompositeStateMachineCml` classifies a direct policy section as structural,
rejects duplicate sections, missing CSM and misplaced fields/sections, and
normalizes the existing CSM. It normalizes REQUIRED-OPERATION once and passes
that same mapping to the Workflow wrapper and `WorkflowInvocationPolicyCml`.

The package-private policy normalizer receives the existing direct-field
extraction function. It retains raw duplicate detection, applies the same
identifier normalization, resolves targets, rejects ineligible Actions,
checks unique policy and logical Action identities, and parses unsigned
integers with explicit bounds before conversion. It preserves declaration
order and entry locations without source rewriting or defaults. All malformed
declarations use the established `RAISE.syntaxErrorFault` boundary.

## CSM and execution separation

The existing CSM parser retains its normalization responsibility; the new
section's complete parsing lives in a separate file. Policy normalization
creates no Action kind, state, transition, timer, provider binding or control
execution. A Judgment Action may carry invocation metadata through its
existing operation seam. Its deterministic local Admission Action remains
outside that policy boundary.

The Workflow wrapper owns the policy because it describes logical invocation
metadata independently of CSM transition placement. Retry delay precedes
later attempts; timeout bounds each attempt. Neither implies Workflow deadline
or aggregate timing semantics.

## Subsequent producer and consumer boundaries

S66.1 is the normalized source boundary only. S66.2 introduces optional v2
generated Workflow ABI/bootstrap when policies exist, while metadata-free
inputs retain byte-identical v1 output. S66.3 admits that generated metadata
in CNCF and records a real consumer fixture without CML re-parsing. CNCF
Phase81 owns enforcement. Runtime jobs/datastores, provider binding and the
other excluded control features are outside Phase66's source lowering.
