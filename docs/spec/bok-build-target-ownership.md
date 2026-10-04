# BoK Build Target Ownership Specification

Status: normative PHASE-71.3 / P710-04-SITE / S18-replan02 contract

BoK target cleanup SHALL delete obsolete generated children only within the
project's direct `target/cozy-bok` directory. Its existing direct
`article-media` cache SHALL retain snapshot bytes and FileTimes. Other immediate
`target` children belong to their producers and SHALL remain unchanged, including
files, directories and symlinks. This includes supplied Media/Video movies and
audio under `target/media`; it is an ownership rule, not a media whitelist.

The `target`, `cozy-bok` and retained `article-media` roots SHALL be direct
directories. A symlink at any of these roots SHALL be rejected without following
or unlinking it. Unrelated target content and external symlink destinations
SHALL remain unchanged on rejection.

The ordinary build retains its separately configured doxsite, Antora, website
and enabled Arcadia output cleanup, bibliography stash/restore and configured
SIE handoff behavior. Publication snapshot identity, leases and named
publication-integrity digests retain their existing semantics.

[CozyBokBuildTargetSpec](../../src/test/scala/cozy/bok/CozyBokBuildTargetSpec.scala)
specifies generated unrelated input preservation and the three symlink root
rejections using actual `BuildConfig` and target cleanup. The ordinary and
repeat-build scenarios in
[CozyArticleMediaBuildHandoffSpec](../../src/test/scala/cozy/publication/CozyArticleMediaBuildHandoffSpec.scala)
specify the same boundary through normal `CozyBok.build`. Native complete-site
acceptance is a separate obligation.
