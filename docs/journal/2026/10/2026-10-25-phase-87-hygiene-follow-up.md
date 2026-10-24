<!-- cncf-phase-finding:HYG-P87-001:begin -->
Existing Test/testOnly/testOptions := Seq.empty at build.sbt:75 suppresses ordinary reporter arguments. Session-only reporter configuration supplied successful diagnostic evidence for P87-02. Owner: Cozy build configuration. Status: existing-open, nonblocking. A future bounded build-maintenance task should preserve CLI reporters and filters while retaining FULL/HEAVY behavior. No build-file repair is admitted by this Step review.
<!-- cncf-phase-finding:HYG-P87-001:end -->
