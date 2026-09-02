# BoK / SIE Integration Contract

Status: normative Phase 14 contract; aligned with the Phase 38 generated-knowledge boundary

## Purpose

SIE means `textus-semantic-integration-engine`. This contract defines the
machine-readable boundary between Cozy BoK publication and SIE semantic
integration without assigning SIE runtime semantics to Cozy.

The SIE baseline for BoK KnowledgeSource ingestion is:

```text
64e035d Add BoK KnowledgeSource ingestion
```

## Responsibility Boundary

Cozy owns:

- BoK build and publication integration;
- publication, project, and repository metadata validation;
- explicit integration diagnostics;
- BoK navigation for Project, Term, Tag, and RDF resources;
- publication of the BoK KnowledgeSource manifest.

SIE owns:

- semantic integration runtime behavior;
- Information-schema interpretation and materialization;
- reading resources declared by a BoK KnowledgeSource manifest;
- publication of SIE-derived Information and RDF handoff resources.

SmartDox remains the source parser and initial metadata/RDF producer. Cozy must
not silently reconstruct missing SmartDox output or regenerate missing SIE
semantic output. SIE must not scrape rendered BoK HTML.

## Phase 38 source and generated-resource boundary

`src/main/doxsite` is the public, human-readable SmartDox knowledge source
and its adjacent public source metadata (`site.conf` and category metadata).
`src/main/media` is durable article-media input, and
`src/main/publication` is durable Cozy-managed publication-registry input;
neither is generated website output. The standard source contains no
`manual`, `history`, `rdf`, or `metadata` source directories and no copied
standard UI bundle. Public guidance belongs in the ordinary `guide` category;
repository operations, design, specification, and journal documents remain
outside `doxsite`.

Cozy and SmartDox derive effective RDF Turtle, JSON-LD, graph summary, the
standard Manual, the History dashboard, and the standard UI during the build.
Generated working resources are under `doxsite.d`; finalized public RDF and
machine-readable resources are under `website.d/rdf` and
`website.d/metadata`. These generated resources are not hand-maintained
source authority and do not create a competing graph-overlay source.

`src/main/extensions` is absent by default. Only an explicitly declared,
path-safe, schema-validated declaration below `src/main/extensions/rdf` may
supplement ontology, schema, or graph information that cannot be derived from
ordinary BoK inputs. Extension identities and contributions that collide with
generated data or another extension fail deterministically; an extension may
not silently replace or override generated authority. BOK38-05 implements
this closed Phase 38 boundary; this contract records it without claiming
downstream consumer acceptance.

## Cozy To SIE Handoff

A generated BoK site publishes:

```text
metadata/cncf/knowledge-source.json
```

The manifest uses `schemaVersion = cncf.knowledge-source.v1`, `kind = bok-site`,
and `sourceRef.kind = bok-site`. Resource `href` values are relative to the
KnowledgeSource base URI. The manifest declares only files that were actually
published.

`site.metadata.id` is the canonical manifest `id` and `sourceRef.value`.
`site.metadata.key` is accepted as a compatibility alias. If neither is set,
Cozy derives a deterministic identifier from `site.metadata.name`.
`site.metadata.url` supplies `sourceRef.uri` and is normalized as a site base
URI with a trailing slash.

The first required resource kind is `glossary-terms` when
`metadata/glossary/terms.json` exists. RDF resources use the existing
`rdf-jsonld`, `rdf-turtle`, and `rdf-graph-summary` kinds. Rendered HTML and a
`/.well-known/cncf-knowledge.json` compatibility document are not part of this
contract.

When Cozy publication metadata exists, the manifest lists each generated
component record with these resource kinds:

- `component-catalog-project` for
  `metadata/catalog/projects/<name>.json`;
- `component-project-metadata` for
  `metadata/projects/<name>/metadata.json`;
- `component-repository-artifact` for
  `metadata/artifacts/repository/<name>.json`;
- `component-release-history` for
  `metadata/releases/<name>.json`.

These resources retain the `cozy.publish-project.v1` schema and are copied as
machine-readable metadata into the public BoK target. Cozy lists only files
that exist and uses paths relative to the KnowledgeSource base URI. SIE joins
the records by the canonical component name and does not inspect rendered HTML
or CAR/SAR archive content during metadata ingestion.

Repository catalogs also publish existence-only indexes when at least one
eligible entry exists:

```text
metadata/cncf/component-references/car.json
metadata/cncf/component-references/sar.json
```

The KnowledgeSource advertises each file as
`kind = component-reference-index`. The document uses
`schemaVersion = cncf.component-reference-index.v1` and carries repository
identity, kind, versions, source path, public evidence path, tags, and terms.
The CAR index comes from the generic BoK repository CAR layer and from
project-backed CAR metadata when a BoK Project exists without a repository
catalog. Repository catalog entries remain the stronger source when both forms
exist for the same CAR name. A project-backed record establishes source-project
identity, not a published archive: its version `file` stays absent unless a
selected catalog version declares one. Multiple project packages claiming the
same otherwise-unindexed CAR identity are invalid instead of being selected by
filesystem order. The SAR index contains only repository SAR
catalogs explicitly referenced by an SIE Project; Cozy does not scan SAR
archive storage. These records establish that a CAR or SAR exists, but do not
claim usage, capability, or dependency detail owned by CBD Support. A complete
four-resource component profile remains authoritative when both forms are
present.

The BoK RDF graph summary may attach an optional `componentRef` object to a
node when, and only when, the node declares `node_type =
"component-reference"`:

```json
{
  "id": "component:textus-bok",
  "label": "Textus BoK",
  "node_type": "component-reference",
  "componentRef": {
    "kind": "car",
    "name": "textus-bok",
    "version": "0.1.0-SNAPSHOT"
  }
}
```

`componentRef.kind` and `componentRef.name` are required non-empty strings.
`componentRef.organization` and `componentRef.version` are optional non-empty
strings. The object is a source-declared existence assertion that links the
graph node to one selected-generation component-reference index entry. It does
not carry CBD Support-owned capability, dependency, compatibility, operation,
manual, or usage detail.

When a project needs to declare supplemental graph nodes directly, the
declaration belongs to the explicit Phase 38 extension boundary below
`src/main/extensions/rdf`, not below `src/main/doxsite`. It is admissible only
when the declaration is explicitly named, path-safe, schema-validated,
deterministic, and not derivable from ordinary BoK inputs. Its identity and
node/edge contributions must not collide with generated or other extension
data; collision or override attempts fail rather than selecting a source by
filesystem order. The extension route supplements generated SmartDox/SIE
graph metadata before `componentRef` validation; it is not an RDF parser
fallback. BOK38-05 implements this route and its diagnostics.

Cozy validates each declared `componentRef` before publishing the graph
summary. The matching index is selected by `kind`: `car` uses
`metadata/cncf/component-references/car.json`, and `sar` uses
`metadata/cncf/component-references/sar.json`. `kind` and `name` must match an
index entry exactly. When `organization` or `version` is present in the
`componentRef`, it must also match the candidate entry exactly; version
matching succeeds when the entry declares that version in its `versions`
collection. An absent index, absent entry, ambiguous match, kind mismatch,
organization mismatch, version mismatch, malformed `componentRef`, or
`componentRef` on any other `node_type` is an invalid BoK publication handoff
and fails deterministically.

Cozy never constructs `componentRef` from a graph node id, label, tag, term,
repository filename, RDF edge, rendered HTML, or fuzzy component-name match. A
node without `componentRef` remains an ordinary graph node even when its id or
label resembles a CAR or SAR artifact identifier.

The current `cozy.publish-project.v1` producer includes identity, descriptive
project fields, versions, build settings, publication placement, repository
files, and release history. Runtime compatibility, service/operation
capabilities, component dependencies, source commit provenance, and release
publication timestamps are not part of the current four-resource output. SIE
must represent their absence as optional-data warnings until Cozy extends the
producer or the manifest advertises another authoritative resource.

## SIE To Cozy Handoff

An SIE-linked Project or publication points to an explicit SIE handoff base.
Cozy reads `metadata/cncf/knowledge-source.json` below that base. Cozy does not
scan an SIE source repository or infer outputs from directory names.

An SIE-linked BoK Project declares this public authoring metadata in its
`project.yaml`:

```yaml
sie:
  projection: nict-knowledgehub
  component: textus-semantic-integration-engine
  subsystem: nict-knowledgehub-runtime
  handoff_base: https://sie.example.com/nict-knowledgehub/
```

`projection` is the stable SIE projection identifier. `component` is an
optional CAR artifact identifier. `subsystem` is an optional SAR artifact
identifier. Cozy resolves both identifiers only from the canonical repository
catalog boundary: `repository/catalog/car/<component>.*` and
`repository/catalog/sar/<subsystem>.*`. It does not scan CAR/SAR artifact
directories. `handoff_base` is an absolute HTTP(S) URI; it must have an
authority and must not contain a query or fragment. Cozy normalizes the base
and derives the manifest URI by resolving
`metadata/cncf/knowledge-source.json`. Local SIE source or generated-output
paths belong under private `conf/cozy` project configuration and are never
copied into publication metadata.

Resolved SIE artifact metadata retains catalog kind, artifact identifier,
catalog path, version list, and the `recommended`, `latestStable`, and
`latestSnapshot` selectors. Project pages link resolved CAR and SAR versions.
The generic Component Repository CAR pages remain the CAR knowledge surface.
SAR index, module, and version pages are generated for SARs explicitly
referenced by SIE Projects and link back to those Projects. Historical or
unreferenced SAR catalogs are not discovered by scanning artifact storage.
An explicitly referenced CAR in a development-local repository is also
materialized through the generic CAR knowledge surface; its local filesystem
root is never published. If multiple configured repositories provide different
catalog content for the same artifact identifier, Cozy rejects the build rather
than choosing one repository by path order. Byte-equivalent catalog content is
deduplicated deterministically.

An SIE-linked Project remains a normal BoK Project. Cozy resolves its existing
`terms`, `tags`, and CML model metadata through the generic Project knowledge
model. Related scenarios are derived from shared glossary-term references, and
RDF navigation is derived from the resolved terms. Cozy does not create a
second SIE-specific term, scenario, tag, or RDF index. SIE-owned Information and
RDF resources declared by the handoff manifest remain the separate BK14-07
contract.

The SIE handoff reuses the `cncf.knowledge-source.v1` envelope with:

- `kind = sie-projection`;
- `sourceRef.kind = sie-projection`;
- a stable projection identifier in `sourceRef.value`;
- the handoff base URI in `sourceRef.uri` when available;
- relative resource `href` values.

The manifest may declare these resource kinds:

- `sie-provenance`: producer, input, version, and generation evidence used for
  freshness diagnostics;
- `information-schema`: SIE-owned Information schema metadata;
- `information-instances`: SIE-owned materialized Information metadata;
- `rdf-jsonld`, `rdf-turtle`, and `rdf-graph-summary`: SIE-derived RDF output.

BK14-07 defines the resource file paths, JSON schemas, merge precedence, and
effective metadata output in `bok-sie-information-handoff.md`. Cozy consumes
only manifest-declared resources and must not regenerate an absent resource.

## Compatibility

Phase 14 accepts `cncf.knowledge-source.v1`. A future incompatible major schema
requires an explicit reader. Unknown optional resource kinds are skipped with a
warning; they do not authorize filesystem discovery.

The current `metadata/glossary/terms.json` top-level shape remains:

```json
{
  "terms": []
}
```

SIE treats term `rdf_refs` as supplementary evidence or relationship
candidates, not as confirmed RDF anchors.

## Diagnostics

No SIE configuration means no SIE diagnostic and preserves existing BoK build
behavior. Once a Project or publication declares an SIE handoff, Cozy reports
stable diagnostic categories:

| Code | Default severity | Condition |
| --- | --- | --- |
| `sie.handoff.missing` | error | The configured manifest is absent. |
| `sie.handoff.invalid` | error | The manifest cannot be decoded or violates the envelope contract. |
| `sie.handoff.schema.unsupported` | error | The manifest uses an unsupported schema version. |
| `sie.handoff.resource.missing` | error | A declared required resource is absent. |
| `sie.handoff.resource.unsupported` | warning | An optional resource kind is unknown. |
| `sie.handoff.stale` | warning | Declared provenance does not match the expected Project/publication input. |
| `sie.handoff.freshness-unknown` | warning | Provenance is insufficient to determine freshness. |
| `sie.project.component.unresolved` | warning | An SIE-linked Project names a component that is absent from the repository CAR catalog. |
| `sie.project.subsystem.unresolved` | warning | An SIE-linked Project names a subsystem that is absent from the repository SAR catalog. |
| `sie.project.artifact.recommended.missing` | warning | A resolved SIE CAR/SAR catalog has no recommended selector. |
| `sie.project.artifact.latest-stable.missing` | warning | A resolved SIE CAR/SAR catalog has no latest stable selector. |

Strict validation may promote stale or unknown freshness warnings to errors.
Diagnostics identify the configured handoff, manifest, resource kind, and
expected Project/publication reference without exposing local source paths in
public pages.

## Implementation Order

1. BK14-03 emits the BoK KnowledgeSource manifest.
2. BK14-04 and BK14-05 register SIE Project/publication and CAR/SAR references.
3. BK14-07 defines and consumes the SIE projection resource schemas.
4. BK14-08 renders navigation from validated metadata only.
