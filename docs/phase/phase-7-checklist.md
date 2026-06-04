# Phase 7 Checklist

This checklist is the authoritative progress tracker for BoK Source and Site
Operations Toolchain work from 2026-06-04 onward.

## BK-01: `cozy bok` Command Surface

Status: DONE

- [x] Define `create`, `create-category`, `build`, `update`, `preview`,
      `commit`, and `upload`.
- [x] Keep `commit` and `upload` as configured workflow hooks only.

## BK-02: BoK Source Scaffold

Status: DONE

- [x] Generate `src/main/doxsite`, `site.conf`, RDF seeds, assets, README, and
      structure notes.
- [x] Exclude generated HTML, Arcadia assets, and `site-structure.yaml`.

## BK-03: Minimal Default Categories

Status: DONE

- [x] Generate the default glossary category.
- [x] Do not generate KnowledgeHub or Book category article seeds by default.

## BK-04: Category Creation

Status: DONE

- [x] Add `cozy bok create-category <name> --project <dir>`.
- [x] Generate category metadata, index, article seeds, and term seeds.

## BK-05: Build and Update

Status: DONE

- [x] Map `wip`, `draft`, `preview`, and `production` to SmartDox strategies.
- [x] Run `dox antora`, Docker-backed Antora, and `dox site`.
- [x] Keep `bok update` as a build-equivalent command for now.

## BK-06: Single-Locale Root Output

Status: DONE

- [x] Support `site.output.locale_mode = "single_locale_root"`.
- [x] Generate `website.d` root output instead of `website.d/ja`.
- [x] Remove `doxsite.d/ja` and `doxsite.d/en` in single-locale BoK builds.

## BK-07: Category-Driven Home and Header

Status: DONE

- [x] Build Home navigation from category metadata.
- [x] Keep glossary category link identifiable as `class="glossary"` in the
      generated Home page.

## BK-08: Preview

Status: DONE

- [x] Serve `website.d` with `python3 -m http.server`.
- [x] Validate `--port` through Goldenport integer metadata.

## BK-09: Workflow Hooks

Status: DONE

- [x] Read configured commit/upload commands from `.cozy/config.yaml`.
- [x] Fail clearly when workflow commands are not configured.

## BK-10: CLI Metadata

Status: DONE

- [x] Use Goldenport metadata parsing for `bok` command parameters.
- [x] Keep canonical examples in `--key value` form.
- [x] Reject equals-form as non-canonical for new `bok` command metadata.

## BK-11: Runtime Smoke

Status: DONE

- [x] Build `/tmp/bok2` with `cozy bok build /tmp/bok2 --strategy wip`.
- [x] Confirm `website.d/ja` is absent.
- [x] Confirm `doxsite.d/ja` and `doxsite.d/en` are absent.
- [x] Confirm Home, category, glossary, and term pages return HTTP 200 in
      preview.
- [x] Convert the `/tmp/bok2` smoke into a repeatable runtime fixture:
      `src/sbt-test/cozy/bok-runtime-smoke`.

## BK-12: Remaining Glossary Link Policy

Status: OPEN

- [ ] Decide whether category index term lists are glossary semantic links or
      ordinary explicit links.
- [ ] If semantic, implement through SmartDox-supported link representation
      rather than raw HTML attributes.
- [ ] Keep SmartDox glossary auto-link semantics unchanged.
