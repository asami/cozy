# Phase 55 Checklist: Site-Context-Preserving Media Registration

Phase Status: COMPLETE

Development item: DEV-021

## P55-01: Canonical Site Context Selection

- [x] Freeze the explicit paired registration-time site-context selection rule.
- [x] Keep `site-root` and `site-config` inseparable.
- [x] Reuse the same resolved site context across build, verify, publish, and
      both registration commands.
- [x] Add or update paired Markdown and Executable Specifications before
      changing behavior.

## P55-02: Registration Currentness Integration

- [x] Propagate site context through the site command, binding plan, and media
      command configuration.
- [x] Preserve configuration, base URL, route, source, descriptor, artifact,
      and receipt identity inputs.
- [x] Preserve direct normalized non-symlink and site-containment checks.
- [x] Preserve behavior for packages without site-aware resources.

## P55-03: Acceptance and Closure

- [x] Directly register a site-aware ordinary DoxSite article from its original
      descriptor.
- [x] Directly register a site-aware Document Project article from its original
      descriptor.
- [x] Prove current-to-stale-to-current transitions for configuration/base URL/
      route changes.
- [x] Cover missing-pair, symlink, out-of-site, and wrong-article rejection.
- [x] Cover `register-site` and `register-site-wip`.
- [x] Verify downstream removal readiness for
      `COZY-GAP-REGISTER-SITE-CONTEXT-001` without deleting it in this Phase.
- [x] Bind focused specifications and the required post-edit full Cozy
      validation to the release closure.
- [x] Complete independent Phase review and bounded focused re-review with no
      Current Phase Blocker.

## Closure Gate

The release is valid only when the post-edit serialized full Cozy suite passes
and the distinct local Phase-release commit binds that receipt. The closure
does not publish, deploy, upload, push, delete the downstream adapter, or
claim external-consumer acceptance.
