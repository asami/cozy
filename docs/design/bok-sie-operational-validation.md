# BoK SIE Operational Validation

## Scope

This record captures the Phase 14 end-to-end validation performed on July 13,
2026. It distinguishes the two integration directions:

1. Cozy publishes a BoK KnowledgeSource manifest that SIE ingests.
2. Cozy consumes an SIE projection handoff and adds effective Information/RDF
   navigation to the generated BoK site.

The validation used the current development checkouts without changing release
coordinates:

- Cozy `0.2.26-SNAPSHOT`;
- SIE `/Users/asami/src/dev2026/textus-semantic-integration-engine`;
- KnowledgeHub `/Users/asami/src/Project2026/bok-knowledgehub`.

## BoK To SIE

KnowledgeHub was generated with:

```console
cozy --runtime-dev-dir /Users/asami/src/dev2025/cozy \
  bok build . --strategy preview
```

The generated site contained:

```text
website.d/metadata/cncf/knowledge-source.json
website.d/metadata/glossary/terms.json
```

The manifest used `schemaVersion = cncf.knowledge-source.v1`, `kind = bok-site`,
and the relative glossary resource
`metadata/glossary/terms.json`. The SIE
`KnowledgeStoreAdmin.ingestBokKnowledgeSource` operation was invoked with the
generated `website.d` file URI, `registerKnowledgeSpace=false`, and
`includeKnowledgeFrame=true`. Its measured response was:

```text
knowledgeSpaceState = frame_only
termCount = 2
warningCount = 0
knowledgeFrame = present
```

The response provenance identified
`metadata/cncf/knowledge-source.json` as the manifest URI. `termCount` matched
the two entries in the generated `terms.json`.

## SIE To BoK

The existing `nict-knowledgehub` Project was declared as the
`nict-knowledgehub` SIE projection. Its local generated handoff was selected
through ignored `.cozy` configuration; no local path was added to public
Project metadata.

The successful preview build produced:

- `metadata/sie/integration.json` with one projection, two Information
  instances, and zero diagnostics;
- SIE Information links on the NICT KnowledgeHub Project page;
- SIE Information links on the Embedding and RDF term pages;
- SIE Information entries on `technology.embedding` and `technology.rdf` tag
  pages;
- SIE metadata on the matching RDF Information View nodes.

The configured local handoff was below `target/`. Cozy preserved it across
normal target cleanup and the generated directories remained ignored.

For the failure path, the local handoff manifest was temporarily removed and
the same build was run again. Cozy published the stable diagnostic:

```text
code = sie.handoff.missing
severity = error
```

The manifest was then restored and the final generated site was rebuilt with
zero SIE diagnostics.
