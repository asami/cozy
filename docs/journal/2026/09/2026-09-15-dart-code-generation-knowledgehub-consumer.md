# Dart Code Generation with KnowledgeHub as Initial Consumer

Date: 2026-09-15

Status: Historical planning input; active Phase/consumer assignment superseded

## 2026-09-17 reconciliation

This record preserves the GitHub-side KnowledgeHub discussion as planning
history. Its active Phase 55 assignment and initial-consumer decision are
superseded by [Phase 65](../../phase/phase-65.md): the current planned target
is Textus CBD Support. Completed local Phase 55 remains the separate
site-context-preserving media-registration closure.

Related:
- `docs/notes/dart-model-api-code-generation-provisional-specification.md`
- `docs/phase/phase-50.md`
- `docs/phase/phase-51.md`
- `docs/phase/phase-52.md`
- `docs/phase/phase-53.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`

## Context

KnowledgeHub Phase 1では、server sideをScala 3 / CNCF / KnowledgeHubで実装し、Flutter client sideをDart / Flutterで実装する。

Flutter側は、

- `asami/textus-flutter-core`: reusable Flutter client foundation / capture runtime
- `KnowledgeHubProject/nict-editing-studio-app`: Flutter Editing Studio application

の2-project構成とした。

`textus-flutter-core` は「Textusのモバイルアプリ」ではなく、iPhone / Androidを最初のconsumerとしつつ、将来のFlutter Web / Desktopも含めて再利用できるFlutter共通基盤として位置づける。

この構成ではserver/client間で同じmodel semanticsを手作業で二重定義するとdriftが起こりやすい。

## Decision

CozyにDart targetを追加し、まずFlutter UI全体ではなく、**model / serialization / API contract code generationを先行する**。

```text
CML / Application Model
        |
        v
      Cozy IR
      /     \
     v       v
Scala/CNCF   Dart/Flutter
     \       /
      contract continuity
```

## Why model/API first

Flutter widget generationはLogical UI IR、responsive projection、platform behavior等の検討を伴う。

一方、Dart model / DTO / codec / typed API contractはKnowledgeHub Phase 1ですぐに必要であり、生成効果も明確である。

したがって、

> **model continuity first, UI generation second**

を採用する。

## Verification Harness perspective

Dartは静的型、null safety、sealed class、pattern matching等を持つが、Scala 3ほど強いmodeling constraintを提供するわけではない。

Cozyから定型モデルを生成することで、AIが自由にDart domain modelを作る範囲を減らし、

```text
CML / IR
 -> generated Dart
 -> Dart analyzer / compiler
 -> hand-written application logic
```

というclient-side Verification Harnessを形成する。

## Relationship with existing Flutter roadmap

既存Phase 50-53のFlutter UI roadmapは維持する。

新しいDart generation Phaseはそれを置き換えず、UI projectionの前段・横断基盤として追加する。

Phase 50-53がLogical UIからFlutter presentationへ向かうのに対し、新Phaseはcanonical model/operation IRからDart contractへ向かう。

## Initial consumer

KnowledgeHub Phase 1を最初の実利用consumerとする。

候補:

- Capture-related shared contracts where modeled in Cozy
- Information / Knowledge API projection
- Semantic Grounding request/response DTO
- Knowledge Formation result / trace DTO
- Editing Studio server API contracts

ただしKnowledgeHub固有型をCozy generatorへhard-codeしない。

## Generated versus hand-written

Cozy-generated Dartは「boring code」を担当する。

Generated:
- model
- enum / sealed variants
- JSON codecs
- DTO
- typed operation contract

Hand-written:
- Editing Studio UX
- Textus Flutter Core runtime behavior
- camera/device integration
- offline/sync strategy
- complex Flutter presentation

## Original future direction

Phase 55でmodel/API targetを成立させた後、Phase 50-53の成果と統合し、

```text
Cozy IR
  +-- Dart Model/API
  +-- Flutter UI projection
```

を同一model continuityの中で扱う。

将来的にはiPhone / Android / Web / DesktopのFlutter application generationへ拡張できる。

## Original 2026-09-15 decision

1. Cozyに汎用Dart code generation targetを追加する。
2. 最初はModel / JSON / DTO / API contractに限定する。
3. KnowledgeHub Phase 1をinitial consumerとする。
4. `asami/textus-flutter-core`をreusable Flutter client foundationとして扱う。
5. KnowledgeHub固有generatorにはしない。
6. Phase 50-53 Flutter UI roadmapは維持し、新Phaseは補完関係とする。
7. 同じcanonical IRからScala / Dartを投影するmodel continuityを重視する。
