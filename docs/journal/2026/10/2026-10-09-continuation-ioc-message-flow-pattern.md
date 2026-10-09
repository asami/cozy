# Continuation IoC as a Message Flow interaction pattern

Date: 2026-10-09

The Message Flow discussion clarified the abstraction level of Continuation Protocol.

A continuation-based interaction contains several physical messages: an initial command, worker-originated continuation callbacks, controller responses, and a final end/result. Rendering these as separate architectural message-flow relationships would collapse Message Flow into a sequence/protocol diagram.

Decision:

- model Continuation/IoC as an `interactionPattern` of the relationship as a whole;
- keep communication timing (sync/async) orthogonal to that pattern;
- use the architectural delegation direction as the relationship's logical control direction;
- do not reverse that direction merely because continuation callbacks are physically initiated by the worker;
- render the complete pattern as one relationship line with the circled `I` marker.

Representative relationships are orchestrator -> Dots, orchestrator -> OpenClaw, and sm-workflow -> Codex.

The detailed initial/continuation/end exchange belongs to protocol or sequence-level documentation, not the Message Flow component view.

See `docs/notes/semantic-message-flow-model.md`.
