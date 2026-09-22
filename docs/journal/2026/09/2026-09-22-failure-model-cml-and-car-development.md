# Failure Model in CML and CAR Development

Failure handling should be designed before implementation rather than discovered by defensive coding.

CML will model Failure Model at Component, Service, and Operation scopes. Execution Model provides a sensible default; declarations at each scope refine it. The resulting effective model is resolved before it reaches implementation tooling.

This is especially important for CAR development. The CML model is the authoritative source for the intended execution/failure boundary. sm-workflow and AI implementation receive the ResolvedFailureModel as a condition of implementation.

This prevents an AI from interpreting a local single-user component as if it were a distributed multi-writer service and adding hashes, locks, retries, or repeated validation. If a new failure really must be supported, the model should be changed first and then regenerated/resolved.

The design keeps CML declarative: it describes responsibility and guarantees, not a mandatory implementation technique.
