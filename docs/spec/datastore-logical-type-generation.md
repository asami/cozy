# DataStore logical type generation

Status (2026-10-18): Phase 78 is CLOSED. P78-PRODUCER and P78-ACCEPTANCE are
accepted and committed; all five canonical sections are DONE. Current generated
compilation, provider reopen and distinct-JVM restart/readback are accepted in
the [consumer handoff](../journal/2026/10/2026-10-18-phase-78-consumer-acceptance.md).
The sole comprehensive Phase review and focused CPB-P78-001 repair closure are
accepted, final Cozy FULL validation passed, and the final release records
closure. Historical validation below retains its original scope and status.

## Declaration route

The existing CML -> ModelTypeProjector -> SimpleModeler semantic model -> Scala
entity family route carries distinct declared structured identities. The
consumer authority is CNCF Phase 100 DSM-100-02
`docs/spec/datastore-logical-type-mapping.md` R1–R12 and its frozen
`entity-store-row-mapping.md`, `entity-store-persistence-stages.md` and
`entity-store-native-scalar-mapping.md` contracts. Cozy owns built-in structured
semantic projection; SimpleModeler owns typed generation, schema and row-binding
emission. Core/CNCF own external decoding, physical mapping and restart/readback.
The producer direction is [Phase 78](../phase/phase-78.md).

| Explicit CML aliases (trimmed, case-insensitive) | Semantic object | Application representation | Generated logical schema |
| --- | --- | --- | --- |
| `json`, `xjson` | `MBuiltinStructuredDataType.Json` | `io.circe.Json` | `org.goldenport.schema.XJson` |
| `record`, `semistructureddata`, `semi-structured-data`, `xsemistructureddata` | `MBuiltinStructuredDataType.SemiStructuredData` | `org.goldenport.record.Record` | `org.goldenport.schema.XSemiStructuredData` |

Built-in structured lookup precedes the legacy record.v2 datatype lookup.
Built-in `record` no longer creates an object reference as its semantic type.
Other builtins, custom declared class resolution, datatype constraints and
multiplicity retain the existing projection route. String remains String;
payload syntax, runtime objects and SQL column types never select a datatype.

SimpleModeler transforms each selected object into `TypeName.Structured`,
retaining typed identity through optional and repeated containers and the
existing Update/Condition wrappers. Runtime names and import presentation are
adapters. Schema generation selects `XJson` or `XSemiStructuredData` from the
typed semantic object after peeling containers, retaining multiplicity.
The exact legacy platform Plain Record reference remains compatible. Custom
Plain Json/Record declarations in another package are not canonical builtins.
No selected structured route substitutes `Named("json")` or `Named("record")`.

For component update requests, SimpleModeler matches operation metadata fields
by the existing normalized contract name to actual typed inputs of that same
operation. The schema-only traversal descends nested value parameter sequences
and keeps Record leaves independently of the opaque Record additions filter.
Only a Structured leaf or the exact legacy Plain `org.goldenport.record.Record`
under existing containers selects typed schema emission; the original wrapped
type is passed to that generator. Scalar and unmatched metadata retain the
string fallback. Explicit foreign Plain Json/Record classes matching those
metadata names retain their existing Named class projection without selecting
a canonical structured datatype. Field multiplicity, web hints,
constraints, confidentiality, required, ordering and update descriptors remain
operation metadata; extra revision inputs and response projection are retained.

JSON admission uses canonical Core ValueReader; Record keeps its existing
Record-specific admission behavior. The producer does not infer application
methods, external formats or storage policies from either runtime class.

Explicit JSON/YAML/HOCON selection belongs to the frozen external decoder
contract. It normalizes XSemiStructuredData independently of the physical JSON
text mapping and introduces no new CML storage-format property. The old selected
Named("record") producer route is historical; current output must carry XJson
or XSemiStructuredData while String, classes and recursive ordered arrays retain
their own declarations.

## R1–R5: Declared-store projection

R1 declaration authority remains explicit CML and its transformed semantic
attributes. SimpleModeler captures original type/multiplicity in
`EntityStoreDeclaration` so Update unwrapping retains optionality. Create uses
its generated optional constructor type for existing auto-complement defaults;
Persistent retains the original required domain. Storage names use explicit
column aliases or the semantic field name. This adds no resolver-precedence
policy; payloads and SQL types never select declarations.

R2 each ordinary field emits a compatible Core ValueDomain and explicit binding:
String/string, Json/json, semi-structured Record/semiStructuredData, canonical
native Text/Boolean/Number bindings, declared application class bindings and
recursive ordered array children. Native/application leaves explicitly summon
ClassTag, Encoder and Decoder. Mandatory, optional and repeated application
fields retain the class capture; optionality is domain multiplicity and repeated
binding is `array(application[A](...))`. Native selection preserves canonical
domains, including Name, rather than substituting a runtime class name.

R3 Persistent, Create and Update independently expose complete storeRowMapping
and typed logical record hooks. Explicit Field.Value.Single construction retains
None/Some, empty structured/ordered values, application objects and raw Update
markers. Ordinary values bypass external/physical conversion and dataAuto.
Persistent logical admission is identity and final decoding uses createC and
typed readers. Create retains defaults. Update normalization belongs to CNCF's
strict patch stage and occurs exactly once, preserving Noop/SetValue/SetNull and
required/optional presence policy.

R4 framework physical keys and conversions retain their existing SimpleEntity
owners and explicit whitelist, with ordinary/framework collisions rejected.
Managed identity, permission, revision, ContentBody, authorization, lifecycle,
OCC and transaction policy remain unchanged. Query emits no new persistence
mapping. Legacy physical methods and compatibility helpers remain temporarily
available; they are not the logical source or final provider cutover.

R5 unsupported optional collections, unordered/unsupported containers and
bounded/disjoint declarations fail generation with field context. Supported
ordered controls retain recursive bindings. Missing captured codecs must fail
generated Scala compilation, with no inferred codec or string fallback; CNCF
retains its recursive metadata validation before hooks or empty traversal.

The complete R1–R6 producer specification is SimpleModeler's
`docs/spec/entity-store-mapping-generation.md`; CNCF's frozen row, persistence
stage and native scalar specifications remain the consumer authority.

## R6: E7 executable fixture and evidence limits

[ModelerStructuredDataGenerationSpec](../../src/test/scala/cozy/modeler/ModelerStructuredDataGenerationSpec.scala)
authors actual CML semantic projection and generation. Its target-owned input
declares String originalProposal, XJson payload, XSemiStructuredData evidence,
legacy record, optional Json/Record, repeated Json/Record, native Name/Int/Boolean/
BigDecimal and mandatory/optional/repeated ProposalSummary fields. The actual
CML value product is `domain.value.ProposalSummary`, avoiding the existing
predefined summary/I18nSummary alias. SimpleModeler's E4 direct semantic fixture
remains `org.example.proposal.value.Summary`. E7 asserts all three application
fields resolve to ProposalSummary with their declared multiplicities. It uses
`ModelerSpecSupport.run_modeler_scala`, including that support's explicit CNCF
runtime descriptor and version options. Work and generated outputs are under
`target/test-generated` only.

The specification generates the same fixture twice and compares the selected
seven actual source products: component, entity, read, create, update, query and
`domain/value/ProposalSummary.scala`. It checks application names, canonical
schema symbols, multiplicity, readers, complete declared mappings and logical
records in Persistent/Create/Update. Mandatory/optional/array class bindings
capture ClassTag/Encoder/Decoder; the actual seventh value product supplies
typed ProposalSummary and existing Record admission. Query retains its role.
The same generation scenario inspects payload and evidence request expressions
separately for both UpdateProposal and UpdateProposalRecord, including legacy
record, optional and repeated fields. It retains the source-wide rejection of
Named structured aliases and the deterministic comparison of both generations.
It introduces no content hash, seal/snapshot mechanism or nested SBT process.
The shared support's existing runtime descriptor digest remains its established
input contract. Selected source comparison proves deterministic generator
output only when executed; it does not prove generated source compilation.

## Current consumer acceptance and remaining Phase gates

CNCF's authoritative fixture is `src/test/cozy/phase100-store.cml`. Its existing
`Test / sourceGenerators` regenerates actual Scala against CNCF's generated
current runtime descriptor and project-owned current version, then test
compilation and `org.goldenport.cncf.entity.Phase100GeneratedStoreSpec` exercise
the generated typed EntityStore SQLite reopen/readback route. Select current
Cozy source explicitly with
`-Dcncf.cozyGeneratorRoot=<Cozy checkout>`, after preparing its ordinary
`cozyExportRuntimeClasspath` output. Replace `<Cozy checkout>` with the selected
absolute checkout path at execution. The existing Cozy helper's
0.5.2-SNAPSHOT fixture is a separate source-output oracle; its assertions do not
prove current-runtime compatibility and its version is not changed by this intake.

The accepted current route uses Cozy 0.3.3-SNAPSHOT and CNCF 0.5.3-SNAPSHOT.
Current focused producer validation and actual consumer-generated compilation
with captured codecs are recorded, together with accepted CNCF DSM-100-06 provider-reopen evidence
through its existing executable specification. The regenerated CML fixture
covers declared String, XJson, XSemiStructuredData, native, class and repeated
values; existing generated semantic-AST fixtures supply nested collection
coverage and are not newly regenerated CML evidence. Typed EntityStore
memory/SQLite and SQLite close/reopen readback preserve declared logical
identities. Explicit JSON/YAML/HOCON decoder selection retains its frozen
Core/CNCF ownership, independently of physical JSON text mapping.

The [consumer handoff](../journal/2026/10/2026-10-18-phase-78-consumer-acceptance.md)
distinguishes provider close/reopen within one JVM from the separately accepted
OS-process restart evidence. The Cozy-owned Scala3 fixture
`src/test/resources/cozy/modeler/phase78/GeneratedStoreProcessRestartSpec.scala`
is compiled as an additional CNCF unmanaged Test source against the current
generated Proposal. A finite writer JVM saves through the public generated
Create-family EntityStore route, closes its provider and terminates. A fresh
reader JVM loads the same database/entity identity through the generated
EntityPersistent/EntityStore route and compares independently authored typed
expectations. Distinct child/parent PIDs, successful exits and readback markers
establish the process boundary. Merged output and SQLite files remain under
CNCF `target/phase78-process-restart`; waits are bounded and cleanup is limited
to fixture-owned children. No CNCF source, index or build edit is required.
Actual execution and independent focused closure resolve CPB-P78-001.

This handoff closes Cozy Phase 78 only. CNCF Phase 100, Workbench and source-only
migration ancestry integration remain separate. R7–R11 presence, patch,
managed-field, transaction and consumer/recovery obligations retain their CNCF
owners. Source inspection and deterministic comparison do not substitute for
actual compilation/runtime acceptance. P78-PRODUCER integrates P78-01–04;
P78-ACCEPTANCE owns P78-05, including the final Phase gates completed in the
[canonical checklist](../phase/phase-78-checklist.md).

## Historical validation and status lineage (October 5–6, 2026)

The following retained record describes the inherited work and its evidence at
that time. Its current/pending wording and Workbench counters are historical,
not the current Phase 78 acceptance state.

The earlier structured and operation-schema producer had focused
SimpleModeler 20/20 and Cozy 32/32 evidence and complete twelve-path independent
PASS at ledger revision 13. New declared-store generation source and executable
specifications were authored on October 5, 2026; focused generation validation
passed SimpleModeler 26/26 in six suites, exact unchanged 1.1.26-SNAPSHOT local
refresh and Cozy 32/32 in two suites on October 6, 2026 (attempt
`tkw-store-generation-cozy-validation-20261006-a4`, subject 7).
Complete fifteen-path independent protected prerequisite review remained pending.
No Step/project/Phase/checklist item closed through that authoring or those results.

Historical evidence records the original SimpleModeler focused suites passing
17/17 and the prior successful local SNAPSHOT refresh. Cozy's actual focused
run passed 31/32 with `MODELER-STRUCTURED-OPERATION-SCHEMA-001` failing on Named
structured datatype expressions in update requests. The failed receipt remains
unaccepted and historical. The typed operation-schema repair and expanded
executable checks are authored October 5, 2026. SimpleModeler's a4 test syntax
and a5 observer-path failures were corrected without changing expected behavior;
neither failed attempt counts as accepted validation.

Earlier structured-producer focused evidence on October 5, 2026: invocation
`tkw-simplemodeler-structured-validation-20261005-a6` passed 20/20 tests in five
completed suites; `tkw-simplemodeler-local-refresh-20261005-a2` successfully
ran `publishLocal` for the fresh exact 1.1.26-SNAPSHOT;
`tkw-cozy-structured-validation-20261005-a2` passed 32/32 tests in two completed
suites covering actual structured and Scala generation. All three commands
terminated with SBT and wrapper exits 0 and `lock=released`. These results
establish focused generator validation of the earlier structured and
operation-schema slice. Its complete twelve-path independent producer review
is PASS at ledger revision 13. This accepted evidence predates the new
declared-store generator source and E7 expansion and does not validate them.

CNCF's retained prerequisite is 85/85 tests across eight suites and complete
eighteen-path independent PASS at ledger revision 16; Core retains 94/94,
independent PASS, commit 8c0d31e and exact 0.4.3-SNAPSHOT refresh. These remain
prerequisite evidence, with no generated runtime acceptance implied.

Current declared-store lineage retains SimpleModeler
`tkw-store-generation-sm-validation-20261005-a1` syntax, a2 capture/range and a3
Seq failures, fixed before a4 success. Cozy
`tkw-store-generation-cozy-validation-20261005-a1` stopped on capacity rejection
before starting and ran no tests; `tkw-store-generation-cozy-validation-20261006-a1`
test syntax, a2 Name expectation and a3 scalar designation mismatches were
resolved before a4 success. Failed commands remain unaccepted. The earlier
structured producer's a4/a5 failures above are distinct from this declared-store
successful a4.

The frozen CNCF field binding and hook-stage ABI is now retained prerequisite
authority. SGEN01–05 source and the E1–E7 expansion have current focused
validation and remain unaccepted pending independent review and integration.
SGEN07 describes their projection. Parent-owned invocation
`tkw-store-generation-sm-validation-20261005-a4`, subject 4, passed 26/26 in
six suites; `tkw-store-generation-sm-refresh-20261005-a1`, subject 4,
successfully refreshed exact unchanged
`org.simplemodeling:simplemodeler_2.12:1.1.26-SNAPSHOT` locally. Invocation
`tkw-store-generation-cozy-validation-20261006-a4`, subject 7, passed 32/32 in
two suites on October 6, 2026, including the seven-product E7 fixture with
unchanged explicit runtime descriptor/version options. All three commands
terminated with exit 0 and released locks. Cozy subjects 5–7 changed only test
notation/expectations, so SimpleModeler's 26/26 and exact refresh remain
applicable. Complete fifteen-path independent protected prerequisite generation
review remains pending. The prior SM20/20/Cozy32/32 PASS belongs to the earlier
slice and cannot close this generation work.

Actual generated Scala compilation against selected Core/CNCF dependencies and
every declared consumer's captured codecs is mandatory before CNCF R11 provider
or default activation. Provider reads/writes, process restart,
String/Json/Record/array/class physical roundtrips, all native mutation/readback
routes, Collection/seed forwarding, SQL precision/defaults, recovery and both TKL
and Editing Studio Workbench proposal fixtures remain required. Source checks
and deterministic generation cannot establish these gates. SQL NULL/patch,
managed fields, lifecycle and transactions retain their existing owners.

Original full Goal and bases, ledger revision 16, epoch 1, Step repair cycle 1,
Phase repair cycle 0, nonconverging count 0 and full Phase review count 0 remain
retained. Separate Workbench, Cozy Phase 78, CNCF Phase 100, Step/project and
Phase acceptance remain pending. This documentation authorizes no live database
mutation or Phase 1.1 work.
