# Phase 7: BoK Source and Site Operations Toolchain

Status: active

Start date: 2026-06-04

## Goal

Make BoK source creation, category management, HTML build, and local preview
first-class Cozy workflows.

Phase 7 formalizes the `cozy bok` work for KnowledgeHub-style BoK operation.
The goal is to let a BoK source project be created and maintained separately
from the published Web site project while still providing clear workflow hooks
for project-specific commit and upload steps.

## Scope

In scope:

- `cozy bok create` source scaffold generation
- default glossary category generation
- `cozy bok create-category` for category, article seed, and term seed creation
- `cozy bok build` and `cozy bok update` for SmartDox and Antora site output
- Docker-backed Antora execution through the Cozy toolchain image
- Japanese single-locale BoK operation with root `website.d` output
- category-driven header generation based on `category.yaml`
- local preview through `cozy bok preview`
- `cozy bok commit` and `cozy bok upload` as configured workflow hooks
- preservation of SmartDox glossary linking behavior in generated surfaces

Out of scope:

- built-in Git commit policy for BoK projects
- built-in upload, CDN invalidation, or hosting provider integration
- Arcadia-style decorative site generation as the default BoK mode
- `site-structure.yaml` as the BoK structure source
- changing SmartDox glossary auto-link semantics
- changing SimpleModeling.org multi-locale compatibility behavior

## Phase Items

- [x] BK-01: Define `cozy bok` command surface
- [x] BK-02: Add BoK source scaffold generation
- [x] BK-03: Keep default category seed minimal and glossary-first
- [x] BK-04: Add `create-category` for category-owned articles and terms
- [x] BK-05: Add Docker-backed Antora build flow
- [x] BK-06: Add root `website.d` output for single-locale BoK operation
- [x] BK-07: Remove locale subdirectories from `doxsite.d` in single-locale mode
- [x] BK-08: Add Home page generation aligned with category structure
- [x] BK-09: Preserve glossary link class in Home-generated glossary links
- [x] BK-10: Add preview command for generated `website.d`
- [x] BK-11: Add commit/upload workflow hooks without built-in policy
- [x] BK-12: Move `bok` CLI parsing to Goldenport metadata
- [x] BK-13: Validate `/tmp/bok2` smoke for KnowledgeHub-style BoK
- [ ] BK-14: Decide whether category index term lists should use SmartDox
      glossary links or remain ordinary explicit links
- [ ] BK-15: Add full runtime smoke fixture for generated BoK build artifacts

## Acceptance Criteria

- `cozy bok create --save <dir>` creates a usable BoK source project without
  generated HTML, Arcadia assets, or `site-structure.yaml`.
- `cozy bok create-category <name> --project <dir>` creates category metadata,
  category index, article seeds, and term seeds without requiring hand-written
  directory setup.
- `cozy bok build <dir> --strategy wip` generates `website.d` through
  SmartDox and Antora using the configured Cozy toolchain Docker image.
- A Japanese single-locale BoK does not leave `website.d/ja`, `doxsite.d/ja`,
  or `doxsite.d/en`.
- Home, category, article, and glossary pages are reachable from local preview.
- Glossary auto-link behavior remains owned by SmartDox, and Cozy-generated
  Home glossary links keep `class="glossary"`.
- `cozy bok commit` and `cozy bok upload` run only configured external commands
  and fail clearly when no command is registered.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-7-checklist.md`
