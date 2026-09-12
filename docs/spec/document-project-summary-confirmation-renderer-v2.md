# Document Project Summary Confirmation Renderer v2 Specification

Status: normative for Phase 58.2, Step P582-04, Slice P582-04A

This specification defines the implementation-facing contract for the
package-private v2 Summary confirmation projection.

## Input and vocabulary

`CozySummaryConfirmationProjectionV2.render` MUST accept exactly an already
admitted `CozyDocumentDescriptionV2.ValidatedSummary` and caller-supplied
typed `Vocabulary`, returning `Rendered(html, identity)`. It MUST NOT load,
mutate, repair, or convert Summary, Document, or Core data, and MUST NOT call
or adapt a v1 renderer.

An admitted Summary with an empty `summary.units` vector MUST be rejected
before rendering with deterministic `ProjectionFault` code
`SUMMARY_CONFIRMATION_V2_UNITS`; it MUST NOT leak a collection-selection
exception.

The Summary is the sole authority for its project-specific title, heading,
message, navigation label, retained wording, ordering, emphasis, diagram
selection, omissions, and omission rationale. Visible Step and node labels
MUST come only from the nested admitted v2 Document. Generic chrome, source
categories, diagram item categories, Relation types, Flow types, Document
target kinds, omission dispositions, and displayed directions MUST come from
the caller vocabulary.

Every actually rendered generic term and every chrome field MUST have one
nonblank trimmed caller-supplied wording. Missing or unusable wording MUST
raise deterministic `ProjectionFault`. The renderer MUST NOT use English
fallbacks, IDs as generic wording, locale resources, Article 9 mappings, or
semantic inference.

## Required HTML behavior

The output MUST be one safely escaped deterministic self-contained HTML
document with inline CSS and script. It MUST render a named ordered navigation
region of native unit buttons in exact `summary.units` order. The first unit
MUST be initially selected. One `aria-pressed` model MUST associate that
selection with one visible semantic panel and one visible evidence panel;
native keyboard activation and visible focus are mandatory.

The semantic panel MUST be renderer-owned and responsive with
`aspect-ratio:16 / 9`. It MUST render only the selected admitted unit heading,
message, and emphasis. It MUST NOT introduce a second selected slide,
auto-summary, automatic condensation, inferred unit, coordinate, or author
layout field.

The evidence panel MUST show the selected unit's exact Core references by
category; each retained point and its own exact references; the unit's
authored diagram records; and each omission's exact Document kind, reference,
disposition, and authored rationale. An absent diagram MUST render the
vocabulary-owned empty-diagram message.

Diagram item output MUST carry admitted identity, kind, and Core ref. Diagram
edge output MUST carry admitted identity, kind, ref, and declared direction.
A Relation edge MUST resolve only through its exact typed Core Relation and a
Flow-transition edge only through its exact owning Core Flow transition.
Containment, graph traversal, proximity, and item order MUST NOT create
diagram edges or items.

The primary status MUST say only that explicit selected Summary sources are
current/admitted and have no unresolved selected references. It MUST NOT say
complete coverage or imply all-Core coverage. Core, Document, Summary, and
output identities MUST be collapsed secondary disclosure.

The output identity MUST be SHA-256 of deterministic HTML with only the
self-disclosing output-identity value normalized to empty. Returned and
disclosed identities MUST match. The artifact MUST have no external resource,
network action, canvas, fixed authored coordinate, or pointer-only dependency.

## Executable specification coverage

`CozySummaryConfirmationProjectionV2Spec` MUST use `AnyWordSpec`, adjacent
Given/When/Then clauses, and `should` matchers. It MUST exercise deterministic
self-disclosing identity and escaping; ordered native selection and associated
panels; the one selected 16:9 semantic explanation; exact source, retained,
and omission evidence; authored-only typed Relation and Flow edges including
inverse direction; selective admitted primary status with secondary identities;
responsive self-contained accessibility markup; and rejection of missing
generic wording. Its fixtures use only strict v2 model data and test-local
vocabulary, never a production locale resource or Article 9 driver.
