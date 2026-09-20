# Phase 67: Workflow Scheduling and Lifecycle ABI

Status: planned

## Goal

Workflow の長時間運用に必要な Deadline、Timer / Wait、Cancellation と durable continuation に必要な宣言 metadata を追加する。

## Scope

- Deadline
- general Timer / Wait
- Cancellation semantics
- runtime suspension/resumption 用 metadata
- deterministic ABI generation

公開 suspend/resume operation を CML source contract として必須にはしない。
