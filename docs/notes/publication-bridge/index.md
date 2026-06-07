# Publication Bridge Notes

status=concept-note-index
published_at=2026-05-13

---

# Purpose

This directory collects project-type-specific notes for the Cozy to SmartDox
publication bridge.

Each note explains how a source project should be shaped so Cozy can generate
`src/main/publication` metadata and SmartDox site can render human-facing pages from that
metadata.

---

# Project Types

| Project type | Note | Status | Example |
|---|---|---|---|
| `sample-multi` | [multi-sample-project.md](multi-sample-project.md) | available | `cncf-samples` / `textus-tutorial` |
| `sample-single` | [sample-single-project.md](sample-single-project.md) | initial | single sample repository |
| `car` | [car-component-project.md](car-component-project.md) | initial | CAR component project |
| `sar` | [sar-subsystem-project.md](sar-subsystem-project.md) | initial | SAR subsystem project |

---

# Shared Boundary

The boundary is common to every project type:

```text
source project
  -> Cozy publication compiler
  -> publication registry
  -> SmartDox site renderer
  -> website.d
```

Rules:

- Source projects provide executable or descriptive source material.
- Cozy extracts and normalizes publication metadata.
- `src/main/publication` is the git-managed semantic publication registry.
- SmartDox site renders `src/main/publication` into pages.
- Warehouse artifacts are indexed by Cozy into `src/main/publication`; SmartDox site should not scan warehouse directly.

---

# How To Add A New Project Type Note

Create one file under this directory and add it to the table above.

Recommended file names:

```text
sample-single-project.md
car-component-project.md
sar-subsystem-project.md
```

Each note should cover:

- project shape
- `project.yaml` public project metadata
- `conf/cozy/config.yaml` project operation defaults
- `.cozy/config.yaml` local operation overrides
- Cozy commands
- generated `src/main/publication` files
- SmartDox page interpretation
- common mistakes
- one concrete example when available
