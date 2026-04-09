# CML Basic Type Mapping Note

This note records the direction for stable built-in type names in CML and
their runtime mapping targets. CML should expose basic type names instead of
requiring direct reference to Scala class names.

## Direction

Types defined in `org.goldenport.datatype` and
`org.simplemodeling.model.datatype` should be mapped to CML basic type names
and reused across components.

The initial mapping direction is as follows.

## First Implementation Set

| CML basic type | Scala/Java runtime type |
|----------------|-------------------------|
| `name` | `org.goldenport.datatype.Name` |
| `identifier` | `org.goldenport.datatype.Identifier` |
| `text` | `org.goldenport.datatype.Text` |
| `entityid` | `org.simplemodeling.model.datatype.EntityId` |
| `url` | `java.net.URL` |
| `uri` | `java.net.URI` |
| `urn` | `org.goldenport.datatype.Urn` |
| `blob` | `org.goldenport.bag.BinaryBag` |
| `clob` | `org.goldenport.bag.TextBag` |
| `localdate` | `java.time.LocalDate` |
| `localdatetime` | `java.time.LocalDateTime` |
| `locale` | `java.util.Locale` |
| `timezone` | `java.util.TimeZone` |

## Extended `java.time` Candidate Set

The CML basic type system should also be able to cover a broader standard
`java.time` family where the runtime meaning is clear.

| CML basic type | Scala/Java runtime type |
|----------------|-------------------------|
| `instant` | `java.time.Instant` |
| `localtime` | `java.time.LocalTime` |
| `offsettime` | `java.time.OffsetTime` |
| `offsetdatetime` | `java.time.OffsetDateTime` |
| `zoneddatetime` | `java.time.ZonedDateTime` |
| `zoneoffset` | `java.time.ZoneOffset` |
| `duration` | `java.time.Duration` |
| `period` | `java.time.Period` |
| `year` | `java.time.Year` |
| `yearmonth` | `java.time.YearMonth` |
| `monthday` | `java.time.MonthDay` |

The generator should resolve these names as built-in types before attempting
custom value/datatype lookup.

## User Profile Usage

The intended near-term usage for common user-profile models is:

- person-name attributes -> `name`
- `birthday` -> `localdate`
- `avatarUrl` -> `url`
- `locale` -> `locale`
- `timeZone` -> `timezone`

The URI-related built-ins are intentionally separated:

- `url` for retrievable network location semantics
- `uri` for generic identifier semantics
- `urn` for non-location URI naming semantics

`blob` and `clob` are also first-class CML basic types. Their implementation
should be based on the Bag family:

- `blob` -> `org.goldenport.bag.BinaryBag`
- `clob` -> `org.goldenport.bag.TextBag`

If internationalized label/title/text semantics are needed as first-class CML
basic types later, additional mappings such as `i18nlabel`, `i18ntitle`, and
`i18ntext` can be introduced on top of the same mechanism.
