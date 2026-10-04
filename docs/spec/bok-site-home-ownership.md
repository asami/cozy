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
