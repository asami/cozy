# BoK site toolchain listener coordination

Scope: the existing internal Antora container handoff supporting
[BOK-UC-07](bok-use-cases.md#bok-uc-07-build-the-local-generated-site).
The [toolchain specification](../spec/bok-site-toolchain.md) fixes its contract.

`CozyBokSiteBuild._docker_antora` already selects
`SMARTDOX_KROKI_PORT=9609` for wrapper readiness and Antora. Supply the documented
`KROKI_LISTEN=127.0.0.1:9609` beside that setting so the actual Kroki listener
selects the same internal endpoint. This uses the
[official Kroki configuration](https://docs.kroki.io/kroki/setup/configuration/)
without changing the selected toolchain image or introducing a host listener.

Both single-locale and multiple-locale orchestration continue using this same
command producer. Producer/project roots, language routing, working directories,
output destinations and SmartDox host-side image settings stay under their
existing owners. The [generated-source boundary](bok-generated-knowledge-boundary.md)
continues to distinguish durable inputs from generated Antora/site resources.
No new helper, public API, schema, configuration option or lifecycle is needed.

The existing [CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala) configured
toolchain scenario observes the real `CozyBok.build` to Runner boundary and
requires coordinated settings on every emitted Antora command. This static
handoff correction is a plausible explanation of `S15-BASELINE-KROKI-001`;
it does not establish the sole native cause or successful server startup.
Parent-owned focused validation and the unchanged complete native site entry
must supply that evidence.
