# CML Operation Input / Output Discussion Result

- date: 2026-04-01
- status: draft

## Purpose

This note records the current discussion result for `OPERATION` input/output
syntax in CML.

It is not yet the frozen grammar contract.

## Problem Statement

The old style:

```text
##### IN

Item creation payload.

##### OUT

Command job result.
```

is too weak.

`IN` / `OUT` in that form only behave like explanatory text.

An `OPERATION` needs at least:

- parameter contract
- return-value contract

At the same time, explanatory text is still needed for:

- help
- OpenAPI
- scaladoc

## Agreed Direction

### 1. OPERATION is Type-Based

`OPERATION` is a typed interaction boundary.

- input side = action type
- output side = result type

Structure must be represented through `VALUE`.

### 2. INPUT / OUTPUT are Structural

`INPUT` and `OUTPUT` must represent structural contract.

They are not only descriptive text blocks.

### 3. VALUE Remains the Only Structural Unit

No new inline structural syntax is introduced outside `VALUE`.

This means:

- top-level `VALUE` reference is allowed
- operation-local nested `VALUE` is allowed

### 4. TYPE Section is Introduced

Inside `INPUT` / `OUTPUT`, `TYPE` is used as the counterpart of `VALUE`.

This allows:

- reference existing type with `TYPE`
- define local type with `VALUE`

Example:

```text
### INPUT

#### TYPE

CreateItemCommand
```

or:

```text
### INPUT

#### VALUE

##### CreateItemCommand
```

### 5. SUMMARY / DESCRIPTION are Required

`SUMMARY` and `DESCRIPTION` are needed as explicit metadata.

Their purpose is to provide source material for:

- help
- OpenAPI
- scaladoc

So the design must not rely on free text alone.

### 6. Narrative is Also Allowed

CML should still allow narrative text before and after formal subsections,
as in other parts of the grammar.

So `INPUT` / `OUTPUT` may contain:

- narrative
- `SUMMARY`
- `DESCRIPTION`
- `TYPE` or `VALUE`

## Working Shape

The current preferred shape is:

```text
## createItem

Creates a new item through the command side.

### SUMMARY

Create a new item.

### DESCRIPTION

Creates a new item through the command side and returns its identifier.

### INPUT

Item creation payload.

#### SUMMARY

Create item command payload.

#### DESCRIPTION

Payload used by the command side to create an item.

#### TYPE

CreateItemCommand

### OUTPUT

Command result.

#### SUMMARY

Create item result.

#### DESCRIPTION

Result returned after successful item creation.

#### TYPE

CreateItemResult
```

## Why TYPE Is Preferred

`TYPE` solves a grammar ambiguity.

Without it, this shape is awkward:

```text
#### SUMMARY

Item creation payload.

CreateItemCommand
```

because the trailing type name is hard to distinguish from summary text.

Using `TYPE` separates:

- explanatory text
- type reference

more cleanly.

## Current Status

This is the current discussion result.

It should be used as the next implementation direction for:

- parser
- modeler
- generator

but it is not yet part of the frozen grammar contract.
