# Phase 36 Hygiene Follow-up

Status: OPEN follow-up records only

## Accepted records

### HYG-P36-02A-001

Status: OPEN

Discovered during P36-02A review. Path:
`src/test/scala/cozy/media/CozyVisualPageSpec.scala`. Multiple feature areas
sit in one flat `should` block and a `which` grouping would improve
navigability. This is nonbehavioral and outside the frozen migration boundary.
Group it in a later Cozy spec-navigation hygiene task.

### HYG-P36-02B-001

Status: OPEN

Discovered during this PLAN snapshot. Path:
`src/main/scala/cozy/media/CozyVisualPage.scala`. The 1,201-line source-size
debt is inherited from P36-02A and parser/validation splitting would be a
separate high-risk refactor. Defer it to a dedicated hygiene boundary. It is
not fixed by P36-02B.

### HYG-P36-02B-002

Status: OPEN

Discovered during P36-02B implementation. Path:
`src/main/scala/cozy/media/CozyMedia.scala`. The required three-line direct
migration dispatcher route moves the file from 999 to 1,002 lines, crossing
the RULE.md 1,000-line source-size-debt threshold. A safe dispatcher split
would be a separate behavior-preserving hygiene boundary; defer it to a
dedicated Cozy command-dispatcher hygiene task.

### HYG-P36-02B-003

Status: OPEN

Discovered during P36-02B implementation. Path:
`src/main/scala/cozy/media/CozyVisualPage.scala`. The smallest reusable
embedded-PageSet validation entry point moves the inherited oversized source
from 1,201 to 1,223 lines. Parser/validation splitting remains a separate
high-risk refactor and is not fixed by this migration slice; carry it in the
same dedicated hygiene boundary as HYG-P36-02B-001.
