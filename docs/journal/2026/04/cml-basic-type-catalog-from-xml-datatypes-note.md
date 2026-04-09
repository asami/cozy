# CML Basic Type Catalog From XML Datatypes

This note records a first-pass catalog of CML basic type candidates using XML
Schema Datatypes as one of the main reference points. Cozy/CML does not need
to reproduce XML Schema verbatim, but XML Schema remains a useful baseline for
coverage and naming discipline.

The purpose of this note is to identify:

- which XML Schema datatypes are worth exposing in CML
- which should map directly to existing Java/Scala runtime types
- which may need `simplemodeling-lib` or `simplemodeling-model` support
- which are too XML-specific to expose as first-class CML basic types

## Classification Policy

Each candidate type should eventually be classified into one of these buckets:

1. `java/scala existing`
2. `simplemodeling-lib`
3. `simplemodeling-model`
4. `not first-class in CML`

The CML surface should prefer stable and domain-meaningful names over XML-only
terminology when the XML term does not match ordinary developer usage.

## 1. String-Derived Types

### XML candidates

- `string`
- `normalizedString`
- `token`
- `language`
- `Name`
- `NMTOKEN`
- `NMTOKENS`
- `QName`
- `anyURI`
- `ID`
- `IDREF`
- `IDREFS`
- `ENTITY`
- `ENTITIES`

### Likely CML direction

| XML Schema | Candidate CML name | Runtime direction | Note |
|------------|--------------------|-------------------|------|
| `string` | `string` | Java `String` | keep |
| `normalizedString` | not first-class | Java `String` | XML-specific normalization rule |
| `token` | `token` or `text` | `simplemodeling-lib` candidate | evaluate against `Text`/`Token` |
| `language` | `language` or `locale` | Java `Locale` or lib/model type | likely expose via `locale` and maybe `language` |
| `Name` | `name` | `org.goldenport.datatype.Name` | good first-class type |
| `NMTOKEN` | not first-class | lib candidate if needed | likely too XML-specific |
| `QName` | not first-class | model-level only | avoid unless namespace-aware modeling requires it |
| `anyURI` | `url`, `uri`, or `urn` | Java `URL` / `URI` | separate retrievable URL, generic URI, and URN naming semantics |
| `ID` | `identifier` | `org.goldenport.datatype.Identifier` | generic identifier, not XML ID semantics |
| `IDREF` | not first-class | use reference semantics in model | not a scalar basic type |
| `ENTITY` | not first-class | use model/entity reference | XML-specific |

## 2. Boolean And Binary Types

### XML candidates

- `boolean`
- `base64Binary`
- `hexBinary`

### Likely CML direction

| XML Schema | Candidate CML name | Runtime direction | Note |
|------------|--------------------|-------------------|------|
| `boolean` | `boolean` | Java `Boolean` / Scala `Boolean` | keep |
| `base64Binary` | `blob` | `org.goldenport.bag.BinaryBag` | keep encoding separate from the CML type name |
| `hexBinary` | `blob` | `org.goldenport.bag.BinaryBag` | keep encoding separate from the CML type name |

## 3. Numeric Types

Numeric types should basically be supported across the standard XML Schema family where the JVM runtime mapping is clear.

### XML candidates

- `decimal`
- `integer`
- `nonPositiveInteger`
- `negativeInteger`
- `long`
- `int`
- `short`
- `byte`
- `nonNegativeInteger`
- `unsignedLong`
- `unsignedInt`
- `unsignedShort`
- `unsignedByte`
- `positiveInteger`
- `float`
- `double`

### Likely CML direction

| XML Schema | Candidate CML name | Runtime direction | Note |
|------------|--------------------|-------------------|------|
| `decimal` | `decimal` | Java `BigDecimal` / Scala `BigDecimal` | keep |
| `integer` | `integer` | Java `BigInt` / Scala `BigInt` | consider |
| `long` | `long` | Java/Scala `Long` | keep |
| `int` | `int` | Java/Scala `Int` | keep |
| `short` | `short` | Java/Scala `Short` | optional |
| `byte` | `byte` | Java/Scala `Byte` | optional |
| `float` | `float` | Java/Scala `Float` | keep if needed |
| `double` | `double` | Java/Scala `Double` | keep if needed |
| `nonNegativeInteger` | `nonnegativeint` / `nonnegativeinteger` | `org.goldenport.datatype.NonNegativeInt` or new type | lib candidate |
| `positiveInteger` | `positiveint` / `positiveinteger` | `org.goldenport.datatype.PositiveInt` or new type | lib candidate |
| unsigned family | support | Java/Scala numeric or library-backed type | support with explicit mapping policy |
| negative/nonPositive family | support | Java/Scala numeric or library-backed type | support with explicit mapping policy |

## 4. Temporal Types

Temporal types should basically be supported across the relevant XML Schema family.
The XML `g*` family should be normalized to ordinary CML names without the `g` prefix.

### XML candidates

- `dateTime`
- `date`
- `time`
- `duration`
- `gYear`
- `gYearMonth`
- `gMonth`
- `gMonthDay`
- `gDay`

### Likely CML direction

| XML Schema | Candidate CML name | Runtime direction | Note |
|------------|--------------------|-------------------|------|
| `dateTime` | `instant`, `localdatetime`, `offsetdatetime`, `zoneddatetime` | `java.time.*` | XML `dateTime` should be split by runtime semantics |
| `date` | `localdate` | `java.time.LocalDate` | keep |
| `time` | `localtime` / `offsettime` | `java.time.LocalTime` / `OffsetTime` | split by runtime semantics |
| `duration` | `duration` | `java.time.Duration` | keep |
| `gYear` | `year` | `java.time.Year` | support |
| `gYearMonth` | `yearmonth` | `java.time.YearMonth` | support |
| `gMonthDay` | `monthday` | `java.time.MonthDay` | support |
| `gMonth` | `month` | Java/Scala month type or custom type | support |
| `gDay` | `day` | custom type if needed | support |

## 5. Model-Specific Or Domain-Specific Basic Types

These are not directly from XML Schema but are strong CML candidates because
of model/runtime semantics.

- `entityid` -> `org.simplemodeling.model.datatype.EntityId`
- `objectid` -> `org.goldenport.datatype.ObjectId`
- `name` -> `org.goldenport.datatype.Name`
- `identifier` -> `org.goldenport.datatype.Identifier`
- `text` -> `org.goldenport.datatype.Text`
- `url` -> `java.net.URL`
- `uri` -> `java.net.URI`
- `urn` -> `org.goldenport.datatype.Urn`
- `blob` -> `org.goldenport.bag.BinaryBag`
- `clob` -> `org.goldenport.bag.TextBag`
- `locale` -> `java.util.Locale`
- `timezone` -> `java.util.TimeZone`

## 6. Initial Recommendation

The first implementation set for Cozy/CML should stay relatively small:

- `string`
- `boolean`
- `blob`
- `clob`
- `decimal`
- `integer`
- `nonnegativeinteger`
- `positiveinteger`
- `nonpositiveinteger`
- `negativeinteger`
- `long`
- `int`
- `short`
- `byte`
- `unsignedlong`
- `unsignedint`
- `unsignedshort`
- `unsignedbyte`
- `float`
- `double`
- `name`
- `identifier`
- `text`
- `entityid`
- `url`
- `uri`
- `urn`
- `instant`
- `localdate`
- `localtime`
- `localdatetime`
- `offsettime`
- `offsetdatetime`
- `zoneddatetime`
- `duration`
- `year`
- `yearmonth`
- `month`
- `monthday`
- `day`
- `locale`
- `timezone`

Specific runtime mappings still need to be fixed, but the language-level support target is broad.

## 7. Next Step

After this catalog is accepted, each candidate should be classified into:

- Java/Scala existing type
- `simplemodeling-lib` addition
- `simplemodeling-model` addition
- rejected as a first-class CML basic type


## 8. Ownership Classification (Current Proposal)

The following classification is the current merged proposal for where each CML
basic type should be backed.

### 8.1 Java/Scala Existing

These types should map directly to existing Java/Scala runtime types.

| CML basic type | Runtime type |
|----------------|--------------|
| `string` | `java.lang.String` / Scala `String` |
| `boolean` | `java.lang.Boolean` / Scala `Boolean` |
| `decimal` | `scala.math.BigDecimal` |
| `integer` | `scala.math.BigInt` |
| `long` | `scala.Long` |
| `int` | `scala.Int` |
| `short` | `scala.Short` |
| `byte` | `scala.Byte` |
| `float` | `scala.Float` |
| `double` | `scala.Double` |
| `url` | `java.net.URL` |
| `uri` | `java.net.URI` |
| `urn` | `org.goldenport.datatype.Urn` |
| `blob` | `org.goldenport.bag.BinaryBag` |
| `clob` | `org.goldenport.bag.TextBag` |
| `instant` | `java.time.Instant` |
| `localdate` | `java.time.LocalDate` |
| `localtime` | `java.time.LocalTime` |
| `localdatetime` | `java.time.LocalDateTime` |
| `offsettime` | `java.time.OffsetTime` |
| `offsetdatetime` | `java.time.OffsetDateTime` |
| `zoneddatetime` | `java.time.ZonedDateTime` |
| `duration` | `java.time.Duration` |
| `period` | `java.time.Period` |
| `year` | `java.time.Year` |
| `yearmonth` | `java.time.YearMonth` |
| `monthday` | `java.time.MonthDay` |
| `locale` | `java.util.Locale` |
| `timezone` | `java.util.TimeZone` |

### 8.2 simplemodeling-lib

These types should be backed by reusable library datatypes from
`org.goldenport.datatype`, or added there if the concept is broadly reusable.

| CML basic type | Runtime type |
|----------------|--------------|
| `name` | `org.goldenport.datatype.Name` |
| `identifier` | `org.goldenport.datatype.Identifier` |
| `text` | `org.goldenport.datatype.Text` |
| `token` | `org.goldenport.datatype.Token` or equivalent |
| `nonnegativeinteger` | `org.goldenport.datatype.NonNegativeInt` or widened equivalent |
| `positiveinteger` | `org.goldenport.datatype.PositiveInt` or widened equivalent |
| `language` | library type if `Locale` is insufficient |

The following are candidates to add to `simplemodeling-lib` when needed:

- `unsignedlong`
- `unsignedint`
- `unsignedshort`
- `unsignedbyte`
- `negativeinteger`
- `nonpositiveinteger`
- `month`
- `day`
- `zoneoffset`

### 8.3 simplemodeling-model

These types belong to the model/runtime semantic layer and should stay in
`org.simplemodeling.model.datatype` or related model packages.

| CML basic type | Runtime type |
|----------------|--------------|
| `entityid` | `org.simplemodeling.model.datatype.EntityId` |
| `objectid` | `org.goldenport.datatype.ObjectId` or model-level alias when promoted |

Model-specific types should be introduced here only when they represent
execution/model semantics rather than general-purpose reusable datatypes.

### 8.4 Not First-Class Initially

The following remain out of the initial first-class CML surface unless a clear
runtime meaning appears:

- `normalizedString`
- `NMTOKEN`
- `NMTOKENS`
- `QName`
- `IDREF`
- `IDREFS`
- `ENTITY`
- `ENTITIES`

## 9. Practical Rule

The practical introduction rule is:

1. use Java/Scala standard types when semantics are already clear and stable
2. use `simplemodeling-lib` for broadly reusable datatypes
3. use `simplemodeling-model` only for model/runtime semantic types
4. keep XML-specific wire-level datatypes out of the first-class CML surface unless real modeling demand exists
