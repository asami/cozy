# Component repository v1 migration

Cozy migrates a legacy `cncf.component-repository-index.v1` warehouse only while
publishing a component and while holding the component-repository index lock. The
warehouse owner must first supply `.cozy/component-repository-migration.v1.json`:

```json
{
  "schema": "cozy.component-repository-migration.v1",
  "cars": [
    { "artifactId": "example-component", "namespace": "org.example", "id": "Example" }
  ]
}
```

The mapping is exact: every indexed legacy CAR has one mapping and every mapping
is used. Cozy rejects duplicate or unknown JSON fields, duplicate artifact or
component identities, invalid canonical identities, and absent or incomplete
mappings. It never infers a namespace or id from an artifact id, archive,
descriptor, CML, package, or path.

For each mapped CAR release, Cozy verifies any stored checksum, regenerates its
SHA-256 sidecar and integrity key, and copies the archive bytes to the canonical
namespace/id/version path. It writes a schema-2 catalog with canonical component,
catalog, archive, checksum, and integrity projections, preserving release
selectors, status, aliases, tags, terms, channel, publication time, and runtime.
Optional CML and model-metadata sidecars are copied beside that canonical catalog.
Legacy catalog/archive URLs and bytes are never changed or deleted; they remain
readable but are no longer indexed. Existing SAR index entries and catalogs remain
unchanged.

Migration prepares and validates all candidates before final paths change. The v2
index is written last and is the migration visibility marker. An in-process
failure restores the original v1 index and removes final paths newly created by
that transaction. Existing canonical files can be reused only when byte-identical;
a different file is a collision. This makes a failed or interrupted pre-index
migration retryable and idempotent with the same mapping.

Once the v2 index is visible, ordinary CAR publication continues. If that later
publication fails, the fully valid migrated v2 warehouse may remain; legacy files
are still preserved and no publication retry needs to remigrate them.
