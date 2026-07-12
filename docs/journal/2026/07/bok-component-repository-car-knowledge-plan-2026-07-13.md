# BoK Component Repository CAR Knowledge Plan

Date: 2026-07-13

## Summary

Cozy BoK should distinguish two related knowledge surfaces:

- Project knowledge: the program-development and CAR-provider unit.
- Repository CAR knowledge: the published CAR artifact and version knowledge
  from the component repository catalog.

Phase 12 introduced the Project knowledge package and the basic CAR project
publication metadata boundary. The remaining gap is to make repository CAR
catalog entries visible as BoK knowledge, so users can move between a Project
page and the published CAR versions, descriptors, ABI manifests, CML sidecars,
and model metadata that represent its distributed artifacts.

This work is generic Component Repository knowledge. Phase 14 SIE catalog
integration should build on it for SIE-specific CAR/SAR entries rather than
owning the generic repository CAR knowledge layer.

## Scope

In scope:

- Read CAR catalog entries from repository catalog metadata.
- Generate CAR knowledge metadata under `metadata/repository/car/`.
- Generate CAR index and detail pages under `repository/car/`.
- Link Project pages to related published CAR versions.
- Link CAR pages back to related Project pages when metadata can resolve the
  relationship.
- Show descriptor, ABI manifest, CML sidecar, and model metadata links when
  present.
- Surface related tags, terms, RDF hooks, and diagnostics.
- Verify with KnowledgeHub using at least one repository CAR catalog entry.

Out of scope:

- Building CAR artifacts as part of `cozy bok build`.
- Publishing CAR artifacts as part of `cozy bok build`.
- Treating repository artifact directories as source by ad hoc scanning.
- Reimplementing CAR runtime behavior or SIE semantics inside Cozy.

## Source Of Truth

Priority order:

1. Project knowledge package:
   `src/main/doxsite/projects/<category>/<slug>/project.yaml|yml|json`.
2. Repository catalog metadata:
   `repository/catalog/car/<module>.yaml|json` in BoK repository-root mode.
3. Warehouse repository catalog metadata:
   `<warehouse>/repository/catalog/car/<module>.yaml|json` in warehouse mode.
4. Sidecar metadata referenced from catalog/project metadata:
   CML, model metadata, component descriptor, and ABI manifest.

CAR archive files may be linked as artifacts, but they should not become the
primary source of BoK knowledge during `bok build`.

## Metadata Contract

Add a repository CAR index:

```text
metadata/repository/car/index.json
metadata/repository/car/<module>.json
```

Expected conceptual shape:

```json
{
  "cars": [
    {
      "id": "car:textus-semantic-integration-engine",
      "module": "textus-semantic-integration-engine",
      "project_ref": "project:technology/textus-semantic-integration-engine",
      "versions": [
        {
          "version": "0.1.0",
          "artifact": "repository/car/textus-semantic-integration-engine/0.1.0/textus-semantic-integration-engine-0.1.0.car",
          "catalog": "repository/catalog/car/textus-semantic-integration-engine.yaml",
          "recommended": true
        }
      ],
      "tags": ["technology.sie"],
      "terms": []
    }
  ]
}
```

The exact JSON schema should be fixed by executable specs before implementation
is considered complete.

## Page Output

Generate:

```text
repository/car/index.html
repository/car/<module>/index.html
repository/car/<module>/<version>.html
```

Project pages should gain a `Published CARs` section when related CAR metadata
exists.

CAR pages should show:

- CAR summary
- distribution and version metadata
- catalog source
- artifact path
- component descriptor metadata
- ABI manifest metadata
- CML sidecar link
- model metadata sidecar link
- related Project link
- related tags and terms
- RDF entry points
- diagnostics

## Diagnostics

Report explicit diagnostics for:

- catalog entries without Project links: unlinked published CARs;
- Project CAR references without catalog entries: unpublished or unresolved CAR
  artifacts;
- missing descriptor, ABI manifest, CML sidecar, or model metadata when the
  catalog claims they should exist;
- ambiguous Project-to-CAR matching.

Diagnostics should be visible to maintainers without polluting normal user
navigation when no issue exists.

## Tag, Term, And RDF Integration

CAR entries may inherit or merge tags and terms from related Project metadata.
Explicit CAR metadata should win over inherited Project metadata where both are
present.

Tag resource pages should include CAR resources. Term Hub and RDF Information
View should gain CAR-related hooks where metadata exists.

Full RDF triples can be implemented after the metadata/page layer is stable, but
page links and metadata fields should be shaped so RDF emission can be added
without changing source contracts.

## Tests

Add or extend:

- `CozyBokRepositoryCarSpec`
- `CozyBokProjectSpec`
- `CozyBokTagSpec`
- RDF/tag navigation specs when RDF hooks are implemented

Minimum executable coverage:

- repository catalog metadata is read from repository-root and warehouse modes;
- CAR metadata JSON is generated deterministically;
- CAR index/detail pages are generated;
- Project pages link to published CAR versions;
- CAR pages link back to Project pages;
- tags/terms merge from Project metadata;
- catalog-without-project and project-without-catalog diagnostics are emitted;
- `bok build` does not build or publish CAR artifacts.

## Implementation Order

1. Add the metadata reader and model.
2. Generate `metadata/repository/car/*.json`.
3. Render CAR index/detail pages.
4. Add Project page `Published CARs` section.
5. Connect tags and terms.
6. Add diagnostics.
7. Add RDF hooks.
8. Verify with KnowledgeHub.

## Phase Relationship

This plan is tracked as Phase 13 `BK13-11` because it completes the Project and
tag navigation model for repository CAR artifacts. Phase 14 `BK14-05` remains
SIE-specific catalog integration and should reuse this generic CAR knowledge
layer.
