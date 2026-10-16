# Phase 83: Safe-by-Construction Web/Application Generation

Status: PLANNED
Plan date: 2026-10-14
Depends on: CNCF Phase 107
Related: Textus Security Architecture, Cozy security responsibility
Consumer: textus-cbd-support Phase 16 / sm-workflow Phase 6

## Goal

Make the normal Cozy-generated application/Web path secure by construction
against SQL injection, XSS, CSRF, mass assignment and authorization bypass by
consuming CNCF Phase 107 contracts.

## Work

1. Generate typed Entity/Repository/DataStore use; never generate application
   value concatenation into SQL. Raw/native SQL remains an explicit escape.
2. Make Display Model rendering context-aware and escape ordinary text by
   default. Generate trusted markup only from declared trusted/sanitized
   carriers.
3. Generate typed URL/redirect targets and reject unsafe implicit String
   projection where the CNCF contract requires a logical type.
4. Derive HTTP method/exposure from Operation semantics: Query/read routes may
   use safe retrieval; mutation Commands are not generated as GET.
5. Wire the standard browser mutation adapter to CNCF's CSRF policy without
   application handlers implementing token checks.
6. Bind request input to declared Operation Input models, not persistent Entity
   classes, so undeclared fields cannot become mass assignments.
7. Preserve CNCF authorization-before-handler semantics; generated adapters
   cannot bypass or replace it.
8. Emit CAR metadata/lint evidence for explicit Security Escape Hatches.

## Acceptance

Executable examples cover malicious SQL-shaped values, script/markup text,
unsafe URL schemes, forged browser mutation requests, undeclared JSON fields and
unauthorized operations. The safe cases must work through ordinary generated
code without application-specific security boilerplate.

The Phase must not restore Phase 71.5 management hashes, artifact protection,
whole-tree copies or speculative integrity checks.

## CNCF authorization route

Cozy does not generate a parallel authorization framework or scatter ad-hoc
authorization helper calls through application code.

Generated public/application execution must preserve CNCF's existing two
authorization chokepoints: Operation authorization at execution entry and
Entity/object authorization at the internal DSL -> UnitOfWork boundary.
Generated Action code uses the canonical CNCF internal DSL for ordinary
Entity read/update/search/list/mutation so UnitOfWorkInterpreter/
OperationAccessPolicy applies authorization automatically.

Generation must not introduce direct EntityStore/DataStore access that bypasses
that UoW route. ServiceInternal/System access mode is never selected from
untrusted request data. If CML later declares proxy/delegated operations, Cozy
compiles them to CNCF's bounded authority model rather than inventing a second
one.
