# Phase 55 Checklist: Site-Context-Preserving Media Registration

Phase Status: IN PROGRESS

Development item: DEV-021

## P55-01: Canonical Site Context Selection

- [x] Freeze the explicit paired registration-time site-context selection rule.
- [x] Keep `site-root` and `site-config` inseparable.
- [ ] Reuse the same resolved site context across build, verify, publish, and
      both registration commands.
- [x] Add or update paired Markdown and Executable Specifications before
      changing behavior.

## P55-02: Registration Currentness Integration

- [x] Propagate site context through the site command, binding plan, and media
      command configuration.
- [x] Preserve configuration, base URL, route, source, descriptor, artifact,
      and receipt identity inputs.
- [ ] Preserve direct normalized non-symlink and site-containment checks.
- [ ] Preserve behavior for packages without site-aware resources.

## P55-03: Acceptance and Closure

- [ ] Directly register a site-aware ordinary DoxSite article from its original
      descriptor.
- [ ] Directly register a site-aware Document Project article from its original
      descriptor.
- [ ] Prove current-to-stale-to-current transitions for configuration/base URL/
      route changes.
- [ ] Cover missing-pair, symlink, out-of-site, and wrong-article rejection.
- [ ] Cover `register-site` and `register-site-wip`.
- [ ] Verify downstream removal readiness for
      `COZY-GAP-REGISTER-SITE-CONTEXT-001` without deleting it in this Phase.
- [ ] Run focused specifications and full Cozy validation.
- [ ] Complete independent focused review with no Current Phase Blocker.
