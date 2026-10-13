# BoK Site Home Ownership Specification

Status: normative PHASE-71.3 / P710-04-SITE / S17 contract

For [BOK-UC-07](../design/bok-use-cases.md#bok-uc-07-build-the-local-generated-site),
the existing `bok.arcadia.enabled` setting selects the home producer:

| Locale mode | Home paths | Arcadia enabled | Arcadia disabled |
| --- | --- | --- | --- |
| `SingleLocaleRoot` | `website.d/index.html` | Preserve the copied Arcadia home byte-for-byte | Generate the established BoK Dashboard |
| `MultiLocaleSubdirs` | `website.d/<language>/index.html` | Preserve each copied Arcadia home byte-for-byte | Generate the established BoK Dashboard for each language |

Cozy page generation SHALL NOT replace Arcadia-authored homes when Arcadia is
enabled. The selection SHALL depend only on this configured producer, without
a file-existence fallback, alternate Dashboard route, new public switch or
new lifecycle. Missing producer output must not cause Cozy to fabricate a
Dashboard home.

Special and category pages remain mandatory in both modes. Canonical RDF,
graph metadata and KnowledgeSource output remain generated and finalized under
the [generated-knowledge boundary](bok-generated-knowledge-boundary.md) and
[metadata finalization contract](bok-metadata-finalization.md).

The [Dashboard audience design](../design/bok-dashboard-audience-actors.md)
continues to govern the normal Dashboard. The paired
[home ownership design](../design/bok-site-home-ownership.md) fixes producer
responsibility without redesigning that Dashboard.

[CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala), under `run optional
build extensions`, specifies `preserve Arcadia-authored homes in both locale
layouts` and `retain normal Dashboard homes when Arcadia is disabled` through
real configuration and build orchestration. External process output alone is
stubbed; these scenarios do not establish native complete-site acceptance.

## Phase 75.1 category extension (2026-10-07)

For COZY-75-07 / P751-02D, each category page SHALL retain the copied
Arcadia-authored UTF-8 content when Arcadia is enabled and the exact
website-relative counterpart is a regular file in the configured
`bok.arcadia-site` output. Ownership SHALL be independent for each
locale/category path, including custom website and producer destinations.
Neither a source template nor an existing final website page establishes
category ownership. When the counterpart is absent, or Arcadia is disabled,
the established BoK category writer SHALL generate the normal article links
and layout-relative CSS. Categories remain mandatory, provided by Arcadia or
BoK; special pages and canonical machine metadata remain generated normally.

The original Phase 71 home selection above remains configuration-only,
including the prohibition on fabricated homes for an enabled producer.
[CozyBokArcadiaCategoryOwnershipSpec](../../src/test/scala/cozy/bok/CozyBokArcadiaCategoryOwnershipSpec.scala)
specifies default/custom destinations, both layouts, missing and disabled
category producers, partial JA/EN ownership and Antora-only output through
real Cozy configuration and composition. Its external process seam does not
establish native complete-site or browser acceptance.
