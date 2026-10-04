# BoK site toolchain contract

Scope: internal Antora process handoff for [BOK-UC-07: Build the local generated
site](../design/bok-use-cases.md#bok-uc-07-build-the-local-generated-site).

Every Docker Antora command emitted by the normal `CozyBok.build` route supplies
both `-e SMARTDOX_KROKI_PORT=9609` and `-e KROKI_LISTEN=127.0.0.1:9609`.
The wrapper/readiness endpoint and the Kroki server listener therefore select
the same container-local port. `KROKI_LISTEN` uses the
[official Kroki listener configuration](https://docs.kroki.io/kroki/setup/configuration/).
The listener is internal to the container; this contract publishes no host port.

The configured Textus toolchain image, SmartDox host-side image environment,
producer and project roots, language selection, working directories and output
routes retain their existing contracts. This handoff adds no public CLI,
schema, configuration option or server lifecycle. Generated site resources
retain the [generated BoK knowledge boundary](../design/bok-generated-knowledge-boundary.md).

Executable specification: [CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala),
`pass the configured Textus toolchain image to SmartDox Kroki execution`, invokes
the real build orchestration and checks every emitted Docker Antora command.
Recorded argv proves the handoff contract; actual listener startup and complete
site generation still require the unchanged native entry's successful execution.
