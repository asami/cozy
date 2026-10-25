<!-- cncf-phase-finding:HYG-P87-001:begin -->
Existing Test/testOnly/testOptions := Seq.empty at build.sbt:75 suppresses ordinary reporter arguments. Session-only reporter configuration supplied successful diagnostic evidence for P87-02. Owner: Cozy build configuration. Status: existing-open, nonblocking. A future bounded build-maintenance task should preserve CLI reporters and filters while retaining FULL/HEAVY behavior. No build-file repair is admitted by this Step review.
<!-- cncf-phase-finding:HYG-P87-001:end -->

<!-- cncf-phase-finding:HYG-P87-002:begin -->
The actual P87-04 native PDF runtime reports duplicate logback.xml resources from SmartDox and Kaleidox JARs. Owner: SmartDox/Kaleidox dependency logging configuration. Status: existing-open, nonblocking. The native producer completed successfully, all 44 selected specifications passed and both real PDFs remain complete; this diagnostic is neither a compiler warning nor evidence of invalid output. Reconcile library logging-resource ownership in a separately admitted dependency-maintenance task. Do not suppress or replace logging configuration in Cozy as part of P87-04.
<!-- cncf-phase-finding:HYG-P87-002:end -->
