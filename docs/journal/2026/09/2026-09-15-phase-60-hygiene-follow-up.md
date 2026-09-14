# Phase 60 hygiene follow-up

Date: 2026-09-15

This journal records nonblocking maintenance debt outside the P600 local-build documentation reconciliation. It authorizes neither source refactoring nor Phase 60.1 work.

## Open hygiene record

HYG-P600-001 — OPEN

- Evidence: `src/main/scala/cozy/document/CozyDocumentProject.scala` is 1,022 lines, exceeding the 1,000-line source-size threshold.
- Responsibility concentration: the object combines command parser and dispatcher responsibilities with project and descriptor loading, validation, projection, and shared filesystem/error helper responsibilities.
- Extraction boundary: a safe split is non-local because `CozyDocumentProjectLocalBuildCommand.scala` currently uses `CozyDocumentProject` package-private helpers. Moving those helpers changes the shared package responsibility boundary as well as the oversized object.
- Separate maintenance follow-up: assign a dedicated source-maintenance task to map package-private helper consumers, select an extraction boundary, preserve the public command contract, and add or update executable specifications as required. That follow-up is not authorized by this record and is not Phase 60.1 work.
