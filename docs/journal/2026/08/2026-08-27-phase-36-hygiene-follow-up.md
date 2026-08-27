# Phase 36 Hygiene Follow-up

Status: OPEN and RESOLVED follow-up records

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

### HYG-P36-STATUS-001

Status: RESOLVED

Discovered during the P36-STATUS-01 lightweight review. Paths:
`docs/strategy/cozy-development-strategy.md` and `docs/phase/README.md`.
The stale snapshots carried a nonblocking stale-snapshot risk by describing
VIS36-01 as in progress and P36-02 as not started after the accepted Phase 36
status had advanced. This was outside P36-STATUS-01's two-phase-document
boundary because that Step synchronized only `docs/phase/phase-36.md` and
`docs/phase/phase-36-checklist.md`.

Resolution boundary: the current task/commit subject
`P36-HYG-STATUS-001` status synchronization. The strategy and README
snapshots now agree with the accepted Phase 36 status: Phase 36 is IN
PROGRESS; VIS36-01 and VIS36-02 are DONE; VIS36-03 is IN PROGRESS with only
P36-03A accepted; and VIS36-04 through VIS36-06 are NOT STARTED. This record
is nonblocking and resolved without any behavioral or Phase-scope change.

### HYG-36-04-001

Status: OPEN

Discovered during the Phase 36 full review and retained by its focused closure
re-review. Path: `src/main/scala/cozy/video/CozyVideoStoryboard.scala`.
The unified Storyboard parser is over 1,000 lines after the accepted v2
boundary. A physical parser split needs a separate source-path and
behavior-preserving hygiene manifest, so it is not part of Phase 36 closure.

Owner and target: a separately authorized Cozy hygiene batch after Phase 36.
Dependency: preserve the accepted `cozy.video.storyboard.v1` and v2 review
evidence contracts while extracting only a mechanically safe parser boundary.
Prohibited local workaround: do not weaken the parser, omit strict validation,
or fold the split into Phase 36 release work.
