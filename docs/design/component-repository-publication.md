# Component Repository Publication

## Purpose

Cozy publishes the CNCF Component Repository discovery index together with the
existing detailed CAR and SAR catalogs. The canonical public resource is:

```text
repository/catalog/index.json
```

The index is a discovery summary. Detailed versions, checksums, and runtime
requirements remain in `repository/catalog/car/<artifact>.yaml` and
`repository/catalog/sar/<artifact>.yaml`.

## Publication Contract

`cozy publish-car` and `cozy publish-sar` load and validate an existing index
before changing it. Publication replaces only the entry identified by
`(kind, artifactId)` and preserves every unrelated CAR and SAR entry. An empty
release catalog removes that identity from the index.

Entries are ordered by `(kind, artifactId)`. Cozy writes UTF-8 JSON to a
temporary sibling file and moves it over `index.json` atomically when the file
system supports atomic replacement. A file system without atomic move support
uses a same-directory replacement fallback. The complete read/merge/write
operation is serialized with a process lock under `.cozy/locks`, outside the
public `repository/` tree, so concurrent CAR and SAR publications do not lose
one another's entries.

Before the updated index is written, Cozy verifies each entry against its
detailed catalog:

- kind and artifact identity agree;
- the catalog path is a safe canonical relative path;
- lifecycle status agrees;
- recommended, latest stable, and latest snapshot selectors agree.

An invalid existing index fails publication instead of being silently repaired
or replaced. Publication does not crawl the repository to reconstruct a
missing index.

## Local Validation

Use the network-free repository lint command against a warehouse root:

```sh
cozy lint repository <warehouse-root>
cozy lint repository <warehouse-root> --format json
```

The command validates `repository/catalog/index.json` and every referenced
detailed catalog. A missing index is a failure for this explicit lint command.
Known-artifact catalog resolution without an index remains a consumer
compatibility behavior; it does not make global discovery available.

When a CAR project contains `repository/catalog/index.json`, integrated
`cozy lint car` includes the same repository consistency check.
