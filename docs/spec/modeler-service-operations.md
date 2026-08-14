# Modeler service operations

*Since:* Aug. 14, 2026
*Version:* Aug. 14, 2026
*Author:* ASAMI, Tomoharu

## Contract

The local Cozy CLI exposes the `modeler` service with no default operation:

```text
modeler class <model> [--save <path>] [--charset <name>]
modeler project <model> [--save <path>] [--charset <name>]
```

`model` is a required model file. `save` is an optional output path interpreted by
the existing Cozy CLI environment and response-output layer. `charset` is an
optional input charset; it defaults to UTF-8.

`class` evaluates the configured Kaleidox modeler with the same core generator
semantic as `modeler-diagram`. A successful result is an SVG `FileResponse`.
`project` evaluates the configured Kaleidox modeler with the same core generator
semantic as `modeler-scala`. A successful result is a generated-Realm
`FileRealmResponse`.

The facade parses the requested model with the selected charset and passes that
model value to the configured Kaleidox modeler. It does not write output itself:
the established CLI `Environment` and `Response` output behavior owns `--save`
handling and writes only after a successful typed response exists.

## Failure taxonomy

Malformed models and modeler evaluation errors are returned as
`ConclusionResponse` values preserving the `SError` conclusion. Missing model
arguments, unreadable files, invalid charsets, and other non-fatal execution
failures also return structured `ConclusionResponse` values. An unexpected
successful Kaleidox result kind is an internal-failure conclusion, never a
successful empty response.

## Authorization and compatibility

This is a local Cozy-process CLI boundary only. It adds no remote endpoint,
credential, user, token, or authorization bypass.

The service facade shares the core generation semantics of the existing direct
`modeler-diagram` and `modeler-scala` commands. Those direct commands, their
publication/provenance behavior, and their wrappers remain unchanged. The facade
does not add publication, provenance, CAR, deployment, or remote-transport
behavior.
