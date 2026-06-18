# Video RDF BoK Registration Handoff

status=decided
published_at=2026-06-19
phase=8

---

# Overview

This document defines the Phase 8 handoff point between `cozy video rdf` and
future SmartDox BoK publication integration.

Phase 8 does not implement BoK registration. It only fixes the contract for how
Video RDF output should become BoK-facing metadata in a later phase.

---

# Producer

The producer is:

```console
cozy video rdf <project-file> --save <dir>
```

The RDF output directory contains:

```text
<dir>/
  video.ttl
  video.jsonld
  manifest.json
```

Meaning:

| File | Meaning |
|------|---------|
| `video.ttl` | Turtle RDF generated through SmartDox semanticweb rendering. |
| `video.jsonld` | JSON-LD projection generated from the same RDF graph. |
| `manifest.json` | Machine-readable summary of RDF generation inputs and outputs. |

The RDF graph is generated from the video project, script data, audio manifest,
part render manifests, project build manifest, and optional transcript/replay
artifacts when they exist.

---

# Future BoK Integration Point

SmartDox site and BoK publication flows should consume Video RDF through the
publication registry, not by scanning arbitrary video build directories.

The current publication registry source of truth is:

```text
src/main/publication/
```

Future Video RDF registration should add publication bundle entries under the
existing `metadata/...` model. The expected direction is a `metadata/video/...`
namespace, for example:

```text
metadata/video/<publication>/<video>/manifest.json
metadata/video/<publication>/<video>/video.ttl
metadata/video/<publication>/<video>/video.jsonld
```

The exact future command surface is intentionally not fixed in Phase 8. Possible
future forms include an additive publication command or an option on an existing
publication flow. In either case, the generated BoK-facing source must remain a
publication bundle entry under `src/main/publication`.

---

# Stable Boundary

The stable rule is:

```text
SmartDox site consumes src/main/publication metadata.
SmartDox site does not scan target/cozy-video, build, or arbitrary RDF output directories.
```

This keeps the existing publication architecture intact:

- project repositories generate deterministic publication metadata
- SmartDox consumes generated publication metadata
- warehouse and build output directories remain operational sources, not
  site-facing semantic registries

---

# Phase 8 Non-Goals

Phase 8 does not add:

- `publish-video`
- `bok register-video`
- automatic BoK page generation for video RDF
- automatic copying of `video.ttl` or `video.jsonld` into `src/main/publication`
- final article layout or navigation for video knowledge pages

Phase 8 only defines the extension point.

---

# Relationship To Later Work

VDO-15 and VDO-16 may enrich the same Video RDF source with:

- transcript artifacts
- caption artifacts
- replay script artifacts
- input video hash
- whisper.cpp version
- model name and version
- timing provenance

Those artifacts should remain optional RDF inputs. Once the BoK registration
flow is implemented, it should register the enriched RDF output through the same
publication registry boundary.

---

# Acceptance Rule

VDO-12 is complete when:

- this handoff contract is documented
- Phase 8 explicitly records that BoK registration is deferred
- no new registration command or automatic BoK page generation is introduced
