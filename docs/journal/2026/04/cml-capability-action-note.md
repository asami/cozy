いい選択です。
Capability × Action にすると、CMLがそのまま CNCF実行モデルに落ちる構造になります。

以下に、**CMLにActionを組み込んだDSL設計（仕様レベル）**をまとめます。
（そのままCozyコンパイラ仕様に使える粒度で書いています）

⸻


CML Action DSL Specification
============================

# HEAD

status=draft
published_at=2026-04-06

# OVERVIEW

This specification defines the integration of Action and Capability into Cozy Modeling Language (CML).

The goal is to:

- Make scenario steps executable
- Enable reuse via Capability
- Align CML with execution platforms (e.g., CNCF)

---

# CORE CONCEPTS

## Capability

A Capability represents a reusable functional unit of the system.

Characteristics:

- Independent of specific use cases
- Composable
- Provides one or more Actions

Examples:

- Authentication
- Payment
- Inventory
- Notification

---

## Action

An Action is the smallest executable unit in CML.

Characteristics:

- Atomic or near-atomic execution
- Referenced from scenario steps
- Defined within a Capability or locally

---

# MODEL STRUCTURE

## Top-Level Elements

```cml
# CAPABILITY
# ACTION
# USECASE


⸻

CAPABILITY DEFINITION

Syntax

# CAPABILITY Payment

## ACTION authorizePayment
input:
  userId: UserId
  amount: Money
output:
  authorizationId: String

## ACTION capturePayment
input:
  authorizationId: String
output:
  receiptId: String


⸻

Semantics
	•	Capability groups related Actions
	•	Actions are namespaced by Capability
	•	Capabilities can be mapped to Components

⸻

ACTION DEFINITION

Local Action (within UseCase)

# USECASE PlaceOrder

## ACTION calculateTotal
input:
  items: List[Item]
output:
  total: Money


⸻

Global Action (top-level)

# ACTION validateEmail

input:
  email: String
output:
  isValid: Boolean


⸻

Action Properties
	•	Deterministic or side-effecting
	•	Can be mapped to:
	•	Function
	•	Procedure
	•	Remote call

⸻

USE CASE INTEGRATION

Scenario Definition

# USECASE PlaceOrder

## SCENARIO main

1. calculateTotal(items)
2. Payment.authorizePayment(userId, total)
3. Payment.capturePayment(authorizationId)
4. Notification.sendEmail(userId)


⸻

Step Semantics

Each step:
	•	Is an Action invocation
	•	May produce outputs
	•	May pass outputs to subsequent steps

⸻

Data Flow

1. total = calculateTotal(items)
2. authId = Payment.authorizePayment(userId, total)
3. receipt = Payment.capturePayment(authId)


⸻

ACTION RESOLUTION

Resolution Order
	1.	Local Action (within UseCase)
	2.	Imported Capability Action
	3.	Global Action

⸻

Namespacing

CapabilityName.ActionName

Example:

Payment.authorizePayment


⸻

CONTROL FLOW EXTENSIONS

Conditional Execution

if (stockAvailable)
  Inventory.reserve(item)


⸻

Parallel Execution (optional)

parallel:
  - Notification.sendEmail(user)
  - Analytics.recordEvent(order)


⸻

RELATIONSHIP WITH USE CASE RELATIONSHIPS

include
	•	Includes scenario fragments (sequence of Actions)

extend
	•	Inserts Actions conditionally

generalize
	•	Refines Action sequences via type hierarchy

⸻

EXECUTION MAPPING

Mapping to Runtime

CML Concept	Runtime Mapping
Capability	Component / Service
Action	Operation / ActionCall
Scenario	Workflow / Procedure
Step	Invocation


⸻

Example Mapping

Payment.authorizePayment

↓

ActionCall("Payment.authorizePayment", input)


⸻

DESIGN PRINCIPLES

1. Action-Centric Execution

All executable behavior must be expressed as Actions.

⸻

2. Capability-Based Reuse

Reuse is achieved via Capability, not via use case duplication.

⸻

3. Explicit Data Flow

Inputs and outputs must be explicit.

⸻

4. Deterministic Composition

Scenario execution must be predictable and analyzable.

⸻

EXTENSION POINTS

Action Types
	•	PureAction (no side effects)
	•	EffectfulAction
	•	ExternalAction (API call)

⸻

Binding

Action can be bound to:
	•	Local function
	•	Service endpoint
	•	Remote component

⸻

SUMMARY

By introducing Action and Capability:
	•	Scenarios become executable
	•	Reuse becomes explicit
	•	CML aligns with component-based execution platforms

This enables:
	•	Direct transformation from model to runtime
	•	Strong compatibility with AI-assisted generation

---

# 重要ポイント（設計的にかなり効いてる）

この設計で👇

### ① Step = Action に統一
→ DSLが一気にシンプルになる

### ② Capability = Component に対応
→ CNCFに直結

### ③ ActionCallにそのまま落ちる
→ 実行基盤とのズレが消える

---

# 次にやるべきこと（ほぼ確定）

ここまで来たら👇

## ① Action型の分類を詰める
- Query / Command
- Sync / Async

## ② 型システム
- Input/OutputをValueと統合

## ③ コンパイラ仕様
- CML → Scala → CNCF

---

必要なら次は：

👉 「ActionをCommand/Queryに分けた完全仕様」  
👉 「CML → CNCF ActionCall 変換仕様」  
👉 「Cozyコンパイラ設計」

まで一気に行けます。
