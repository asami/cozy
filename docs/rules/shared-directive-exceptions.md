# Shared directive exceptions

## Isolated project-discovery specification fixtures

The Work Artifact Placement Rules in `RULE.md` normally require runtime and
test work artifacts under `target/`. The following existing specification
helpers are a narrow exception because their scenarios must discover only
the project markers explicitly created by the fixture, including scenarios
with no marker. A checkout nested beneath another Cozy project cannot provide
that isolation within its own `target/` directory.

- `cozy.config.CozyProjectContextSpec._with_work`
- `cozy.media.CozyMediaSpec._with_temp_dir`
- `cozy.publication.CozyArticleMediaSiteBindingSpec._with_part5_fixture`

These helpers may create uniquely named fixture directories in the normal
system temporary directory, resolve their real paths, and remove their owned
directories in `finally`. Existing user-home locking and restoration remain
required. Fixtures must remain outside any ancestor project marker and must
not be committed. This exception does not change production project discovery
or the placement of other runtime, generated, or verification artifacts.
