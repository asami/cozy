# Dart Model / API Code Generation — Provisional Specification

status=provisional
recorded_at=2026-09-15
consumer=Textus CBD Support client-contract boundary
historical_planning_input=KnowledgeHub Phase 1 / Textus Flutter Core / NICT Editing Studio App
phase_assignment=Phase 65; Phase 55 remains the completed site-context media-registration closure
related_ui_phases=Phase 50-53 Flutter UI projection series

## Purpose

CozyのCML / model / IRから、Flutter applicationで利用するDartの型・serialization・API boundary codeを生成する。

本機能はFlutter UI生成そのものより先に、**server / client間のmodel continuityを確保するためのboring but critical code generation**を対象とする。

```text
CML / Cozy Model
      |
      v
Canonical / Shared IR
      |
      +--> Scala / CNCF side
      |
      +--> Dart / Flutter side
              |
              +-- model types
              +-- sealed hierarchies / enums
              +-- JSON codecs
              +-- API DTOs
              +-- validation metadata
              +-- client operation contracts
```

## Design goals

1. Scala server modelとDart client modelのdriftを減らす。
2. AI / humanが手書きする定型Dart codeを減らす。
3. Dart compiler / analyzerをVerification Harnessの一部として利用する。
4. Phase 50以降のLogical UI / Flutter UI generationへ自然に接続する。
5. CBD Support固有generatorにせず、Cozyの汎用Dart targetとして設計する。

## Initial generation scope

### Model types

- scalar/value fields
- required / optional fields
- list / collection fields
- entity/value identifiers where represented in IR
- enum
- closed sum type / sealed hierarchy where IR provides exhaustive variants
- immutable-oriented generated classes

### Serialization

- JSON encode / decode
- stable field naming
- nullability preservation
- enum / variant discriminator contract
- version / compatibility hook where needed

### API boundary

- request DTO
- response DTO
- error/result envelope mapping where canonical IR exists
- operation signature
- generated client interface / contract

Concrete HTTP client implementation may remain hand-written or separately generated; Phase 65 first fixes the typed boundary.

## Dart mapping principles

Examples of intended mappings:

```text
IR required String      -> String
IR optional String      -> String?
IR enum                 -> enum
IR closed variant       -> sealed class + final variants
IR immutable value      -> final class / final fields
IR collection           -> List<T> or appropriate declared collection projection
```

Generated code should prefer explicit types over `dynamic` and avoid weakly typed Map-based domain representation.

## Null safety

IR optionality must map deterministically to Dart null safety.

No generator path may silently convert an optional field into required or vice versa.

## Shared IR principle

Dart generation should consume the same canonical model / contract IR used by other target projections where possible.

Do not create a Flutter-only shadow model of server contracts.

```text
                  Cozy IR
                /         \
          Scala target    Dart target
                \         /
                 semantic continuity
```

## Relationship with Phase 50-53

Phase 50-53 focus on Logical UI / responsive preview / Flutter UI projection.

Dart Model/API generation is complementary and can precede full Flutter widget generation.

Later pipeline:

```text
Application Model
      |
      v
Cozy IR
   |       |
   |       +--> Dart Model/API
   |
   +--> Logical UI IR
              |
              v
          Flutter UI
```

## CBD Support consumer and historical planning input

The 2026-09-15 KnowledgeHub / Flutter consumer names are retained as historical
planning input only. The active plan is Phase 65 for Textus CBD Support; it
does not reopen or replace completed local Phase 55.

Initial consumer candidates include contracts required by:

- Textus CBD Support client-contract boundary
- CNCF Information / Knowledge projections where explicitly exported to clients

The consumer validates the generator but must not cause consumer-specific
semantics to enter Cozy core.

## Generated / hand-written boundary

Generated:
- data/value types
- enums / sealed variants
- codecs
- DTOs
- validation metadata where model-derived
- typed API contracts

Hand-written initially:
- application navigation
- sophisticated Flutter UX
- camera/device integration
- local database strategy
- synchronization orchestration
- Knowledge Canvas
- application-specific visual effects

## Acceptance

- one representative shared model generates compilable Dart.
- required/optional fields preserve nullability.
- enum / sealed variant exhaustiveness is represented where possible.
- JSON round-trip tests pass.
- generated API DTOs match declared model/operation contracts.
- Textus CBD Support can consume generated code without `dynamic` domain maps.
- generator remains independent of consumer-specific semantics.

## Non-goals

- full Flutter widget generation.
- state management framework selection.
- routing/navigation generation.
- device plugin generation.
- local persistence implementation.
- complete OpenAPI generator replacement.
- arbitrary hand-written Dart preservation/merging in Phase 65.
