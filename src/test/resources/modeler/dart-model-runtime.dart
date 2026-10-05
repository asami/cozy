// @dart=3.0
import 'dart:convert';
import 'dart:io';
import '../lib/src/models.dart';
import '../lib/src/json.dart';

void check(bool condition, String message) {
  if (!condition) throw StateError(message);
}

void rejectsDecode(void Function() action, String message) {
  try {
    action();
  } on DartJsonDecodingException {
    return;
  }
  throw StateError('Expected DartJsonDecodingException: $message');
}

void rejectsArgument(void Function() action, String message) {
  try {
    action();
  } on ArgumentError {
    return;
  }
  throw StateError('Expected ArgumentError: $message');
}

void rejectsMutation(void Function() action) {
  try {
    action();
  } on UnsupportedError {
    return;
  }
  throw StateError('Exposed model list was mutable');
}

String enumSwitch(DartFinite value) => switch (value) {
  DartFinite.alpha => 'alpha',
  DartFinite.beta => 'beta',
};

String sealedSwitch(DartSealed value) => switch (value) {
  DartSealedAlpha() => 'alpha',
  DartSealedBeta() => 'beta',
};

// An override exposes an invalid state to observe the encode boundary itself.
class UnsafeInteger extends DartSafeIntValue {
  UnsafeInteger() : super(value: 0);
  @override
  int get value => 9007199254740992;
}

class UnsafeDouble extends DartDoubleValue {
  UnsafeDouble() : super(value: 0);
  @override
  double get value => double.nan;
}

class UnsafeFloat extends DartFloatValue {
  UnsafeFloat() : super(value: 0);
  @override
  double get value => double.infinity;
}

class UnsafeBigInteger extends DartIntegerValue {
  UnsafeBigInteger() : super(value: 0);
  @override
  int get value => 9007199254740992;
}

class UnsafeByte extends DartByteValue {
  UnsafeByte() : super(value: 0);
  @override
  int get value => 128;
}

class UnsafeShort extends DartShortValue {
  UnsafeShort() : super(value: 0);
  @override
  int get value => 32768;
}

class UnsafeInt extends DartIntValue {
  UnsafeInt() : super(value: 0);
  @override
  int get value => 2147483648;
}

void main(List<String> arguments) {
  final json = <String, Object?>{
    'id': 'record-id', 'recordId': 'record-ref', 'count': 1, 'ratio': 1.25, 'members': <Object?>['first'],
  };
  // DA-01/07: constructors retain canonical entity, structured and wrapper types.
  final text = DartText(value: 'hello');
  check(text.toJson() == 'hello', 'sole required value must be scalar');
  final code = DartCode(code: 'code');
  check(jsonEncode(code.toJson()) == '{"code":"code"}', 'sole code remains object');
  final optional = DartOptionalValue();
  check(jsonEncode(optional.toJson()) == '{"value":null}', 'optional value remains object');
  final address = DartAddress(street: 'Main Street');
  final structured = DartStructured(title: 'Home', address: address);
  check(DartStructured.fromJson(structured.toJson()).address.street == address.street,
      'structured nested typed reference');
  final entity = DartEntity(id: text, enabled: true);
  check(DartEntity.fromJson(entity.toJson()).id.value == 'hello', 'entity identifier wrapper');
  final constrained = ConstrainedText(value: 'x');
  final container = Container(text: constrained);
  final Container decodedContainer = Container.fromJson(jsonDecode(jsonEncode(container.toJson())));
  final ConstrainedText containerText = decodedContainer.text;
  check(constrained.toJson() == 'x' && containerText.value == 'x',
      'represented pattern and minimum length remain metadata for a shorter nonmatching value');
  check((decodedContainer.toJson() as Map<String, Object?>)['text'] == 'x',
      'constrained nominal scalar survives typed container decode and re-encode');
  final keywords = FunctionValue(class_: 'class', someKey: 'punctuation');
  check(jsonEncode(keywords.toJson()) == '{"class":"class","some-key":"punctuation"}',
      'wire keys survive keyword and punctuation escaping');

  // DA-04: required fields reject both absence and explicit null.
  for (final field in <String>['id', 'recordId', 'count', 'ratio', 'members']) {
    final absent = Map<String, Object?>.from(json)..remove(field);
    rejectsDecode(() => DartRecord.fromJson(absent), 'missing $field');
    final nulled = Map<String, Object?>.from(json)..[field] = null;
    rejectsDecode(() => DartRecord.fromJson(nulled), 'null $field');
  }
  final missingOptional = DartRecord.fromJson(json);
  final nullOptional = DartRecord.fromJson(Map<String, Object?>.from(json)..['detail'] = null);
  check(missingOptional.detail == null && nullOptional.detail == null, 'optional missing/null');
  final missingWire = missingOptional.toJson() as Map<String, Object?>;
  check(missingWire.containsKey('detail') && missingWire['detail'] == null, 'explicit optional null encoding');
  final constructed = DartRecord(id: 'record-id', recordId: text, count: 0, ratio: 0, members: <DartText>[text]);
  check(constructed.detail == null && constructed.tags.isEmpty, 'constructor defaults');

  // DA-05: cardinality, nonnull elements and defensive unmodifiable copies.
  check(missingOptional.tags.isEmpty, 'missing star defaults empty');
  rejectsDecode(() => DartRecord.fromJson(Map<String, Object?>.from(json)..['tags'] = null), 'null star');
  rejectsDecode(() => DartRecord.fromJson(Map<String, Object?>.from(json)..['members'] = <Object?>[]), 'empty plus');
  for (final field in <String>['tags', 'members']) {
    rejectsDecode(() => DartRecord.fromJson(Map<String, Object?>.from(json)..[field] = <Object?>[null]), 'null $field element');
    rejectsDecode(() => DartRecord.fromJson(Map<String, Object?>.from(json)..[field] = <Object?>[3]), 'wrong $field element');
    rejectsDecode(() => DartRecord.fromJson(Map<String, Object?>.from(json)..[field] = 'not a list'), 'nonarray $field');
  }
  rejectsArgument(() => DartRecord(id: 'record-id', recordId: text, count: 1, ratio: 1, members: <DartText>[]), 'empty constructor plus');
  final callerTags = <String>['before'];
  final callerMembers = <DartText>[text];
  final record = DartRecord(id: 'record-id', recordId: text, count: 1, ratio: 1.25, tags: callerTags, members: callerMembers);
  callerTags[0] = 'after';
  callerMembers.clear();
  check(record.tags.single == 'before' && record.members.single.value == 'hello', 'copy every supplied list');
  rejectsMutation(() => record.tags.add('new'));
  rejectsMutation(() => record.members.clear());
  rejectsMutation(() => missingOptional.tags.add('new'));
  final callerIntegers = <int>[1];
  final callerDoubles = <double>[1.25];
  final numbers = DartNumbers(integers: callerIntegers, doubles: callerDoubles);
  callerIntegers[0] = 2;
  callerDoubles[0] = 2.5;
  check(numbers.integers.single == 1 && numbers.doubles.single == 1.25, 'copy numeric lists');
  rejectsMutation(() => numbers.integers.add(3));
  rejectsMutation(() => numbers.doubles.clear());
  rejectsArgument(() => DartNumbers(integers: <int>[2147483648], doubles: <double>[1]), 'validate constructor integer elements');
  rejectsArgument(() => DartNumbers(doubles: <double>[double.nan]), 'validate constructor floating elements');
  rejectsDecode(() => DartNumbers.fromJson(<String, Object?>{'integers': <Object?>[1.5], 'doubles': <Object?>[1]}), 'reject fractional list elements');
  rejectsDecode(() => DartNumbers.fromJson(<String, Object?>{'doubles': <Object?>[double.infinity]}), 'reject nonfinite list elements');

  // DA-06: narrow and JSON-safe integer limits on construct/decode/encode.
  for (final value in <int>[-128, 127]) {
    check(DartByteValue(value: value).toJson() == value && DartByteValue.fromJson(value.toDouble()).value == value, 'byte boundary');
  }
  for (final value in <int>[-32768, 32767]) {
    check(DartShortValue(value: value).toJson() == value && DartShortValue.fromJson(value.toDouble()).value == value, 'short boundary');
  }
  for (final value in <int>[-2147483648, 2147483647]) {
    check(DartIntValue(value: value).toJson() == value && DartIntValue.fromJson(value.toDouble()).value == value, 'int boundary');
  }
  for (final value in <int>[-9007199254740991, 9007199254740991]) {
    check(DartSafeIntValue(value: value).toJson() == value && DartSafeIntValue.fromJson(value).value == value, 'safe boundary');
    check(DartIntegerValue(value: value).toJson() == value && DartIntegerValue.fromJson(value).value == value, 'integer safe boundary');
  }
  for (final value in <int>[-129, 128]) {
    rejectsArgument(() => DartByteValue(value: value), 'byte constructor');
    rejectsDecode(() => DartByteValue.fromJson(value), 'byte decoding');
  }
  for (final value in <int>[-32769, 32768]) {
    rejectsArgument(() => DartShortValue(value: value), 'short constructor');
    rejectsDecode(() => DartShortValue.fromJson(value), 'short decoding');
  }
  for (final value in <int>[-2147483649, 2147483648]) {
    rejectsArgument(() => DartIntValue(value: value), 'int constructor');
    rejectsDecode(() => DartIntValue.fromJson(value), 'int decoding');
  }
  for (final value in <int>[-9007199254740992, 9007199254740992]) {
    rejectsArgument(() => DartSafeIntValue(value: value), 'safe constructor');
    rejectsDecode(() => DartSafeIntValue.fromJson(value), 'safe decoding');
    rejectsArgument(() => DartIntegerValue(value: value), 'integer constructor');
    rejectsDecode(() => DartIntegerValue.fromJson(value), 'integer decoding');
  }
  for (final value in <Object?>[1.5, -1.5, double.nan, double.infinity, double.negativeInfinity, '1', true, null]) {
    rejectsDecode(() => DartSafeIntValue.fromJson(value), 'bad integral form');
  }
  check(DartIntValue.fromJson(1.0).value == 1, 'integral finite doubles accepted');
  check(DartFloatValue.fromJson(1).value == 1 && DartFloatValue(value: 1.25).toJson() == 1.25, 'finite float number codec');
  check(DartRecord.fromJson(Map<String, Object?>.from(json)..['count'] = 1.0).count == 1, 'integral record decode');
  rejectsDecode(() => DartRecord.fromJson(Map<String, Object?>.from(json)..['count'] = 1.5), 'fractional record');
  rejectsArgument(() => DartRecord(id: 'record-id', recordId: text, count: 2147483648, ratio: 0, members: <DartText>[text]), 'record narrow constructor');
  for (final value in <double>[double.nan, double.infinity, double.negativeInfinity]) {
    rejectsArgument(() => DartDoubleValue(value: value), 'nonfinite constructor');
    rejectsArgument(() => DartFloatValue(value: value), 'nonfinite float constructor');
    rejectsArgument(() => DartRecord(id: 'record-id', recordId: text, count: 0, ratio: value, members: <DartText>[text]), 'nonfinite field constructor');
    rejectsDecode(() => DartDoubleValue.fromJson(value), 'nonfinite decode');
    rejectsDecode(() => DartFloatValue.fromJson(value), 'nonfinite float decode');
    rejectsDecode(() => DartRecord.fromJson(Map<String, Object?>.from(json)..['ratio'] = value), 'nonfinite field decode');
  }
  rejectsArgument(() => UnsafeInteger().toJson(), 'encode unsafe integer');
  rejectsArgument(() => UnsafeBigInteger().toJson(), 'encode unsafe modeled integer');
  rejectsArgument(() => UnsafeByte().toJson(), 'encode narrow byte');
  rejectsArgument(() => UnsafeShort().toJson(), 'encode narrow short');
  rejectsArgument(() => UnsafeInt().toJson(), 'encode narrow int');
  rejectsArgument(() => UnsafeDouble().toJson(), 'encode nonfinite double');
  rejectsArgument(() => UnsafeFloat().toJson(), 'encode nonfinite float');

  // DA-08/09: compiler-observed exhaustive switches plus exact wire metadata.
  check(enumSwitch(DartFinite.alpha) == 'alpha' && enumSwitch(DartFinite.beta) == 'beta', 'enum exhaustive switch');
  check(DartFinite.alpha.wireName == 'alpha' && DartFinite.alpha.sourceValue == '1' && DartFinite.alpha.label == 'Alpha', 'enum metadata');
  check(DartFinite.fromJson('beta') == DartFinite.beta && DartFinite.beta.toJson() == 'beta', 'enum string discriminator');
  rejectsDecode(() => DartFinite.fromJson('unknown'), 'unknown finite');
  rejectsDecode(() => DartFinite.fromJson(1), 'numeric metadata is not discriminator');
  final alpha = DartSealed.fromJson('alpha');
  final beta = DartSealed.fromJson('beta');
  check(alpha is DartSealedAlpha && beta is DartSealedBeta, 'sealed final units');
  check(sealedSwitch(alpha) == 'alpha' && sealedSwitch(beta) == 'beta', 'sealed exhaustive switch');
  check(alpha.sourceValue == '1' && alpha.label == 'Alpha' && alpha.toJson() == 'alpha', 'sealed wire and metadata');
  rejectsDecode(() => DartSealed.fromJson('unknown'), 'unknown sealed');
  rejectsDecode(() => DartSealed.fromJson(<String, Object?>{'kind': 'alpha'}), 'no discriminator object');
  check(DartEscaped.quotedKind.sourceValue == "'\\\$\n日本", 'escaped source value');
  check(DartEscaped.quotedKind.label == "Label ' \\ \$\t日本", 'escaped label');
  check(DartEscaped.fromJson('quoted-kind').toJson() == 'quoted-kind', 'exact punctuation kind');
  check(DartFallback.nameOnly.label == 'name-only' && DartFallback.nameOnly.sourceValue == null, 'source-name label fallback');

  // DA-10: open extension names preserve type and nullable metadata.
  final open = DartOpen.fromJson('unseen-name');
  check(open.wireName == 'unseen-name' && open.toJson() == 'unseen-name', 'open unknown name');
  check(open.sourceValue == null && open.label == null, 'unknown metadata absent');
  check(DartOpen.alpha.sourceValue == '1' && DartOpen.alpha.label == 'Alpha', 'known open metadata');

  // DA-12: additive fields ignored while nested named values round-trip.
  final additive = Map<String, Object?>.from(json)..['extra'] = <String, Object?>{'unknown': true};
  final decoded = DartRecord.fromJson(additive);
  final String decodedId = decoded.id;
  final DartText decodedRecordId = decoded.recordId;
  final reencoded = decoded.toJson() as Map<String, Object?>;
  check(!reencoded.containsKey('extra') && decodedId == 'record-id' && decodedRecordId.value == 'record-ref' &&
      decoded.recordId.value == 'record-ref' && decoded.members.single.value == 'first', 'additive nested decode');
  check(reencoded.containsKey('id') && reencoded['id'] == 'record-id' &&
      reencoded.containsKey('recordId') && reencoded['recordId'] == 'record-ref', 'exact primitive and typed identifier wire keys');
  final roundtripped = DartRecord.fromJson(jsonDecode(jsonEncode(record.toJson())));
  check(roundtripped.id == 'record-id' && roundtripped.recordId.value == text.value &&
      roundtripped.members.single.value == text.value, 'nested JSON tree roundtrip');
  rejectsDecode(() => DartRecord.fromJson(<String, Object?>{}), 'missing object fields');
  rejectsDecode(() => DartRecord.fromJson(null), 'null object');
  rejectsDecode(() => DartEntity.fromJson(<String, Object?>{'id': 'id', 'enabled': 'true'}), 'boolean mismatch');

  // Property inputs originate in ScalaCheck and are consumed by emitted codecs.
  if (arguments.isNotEmpty) {
    final samples = DartJsonCodec.object(jsonDecode(File(arguments.single).readAsStringSync()), 'samples');
    final integers = DartJsonCodec.readList<int>(samples['integers'], 'integers',
        (value) => DartJsonCodec.integer(value, 'sample', -9007199254740991, 9007199254740991, decoding: true));
    for (final integer in integers) {
      final wrapper = DartSafeIntValue(value: integer);
      check(DartSafeIntValue.fromJson(jsonDecode(jsonEncode(wrapper.toJson()))).value == integer, 'property integer roundtrip');
    }
    final strings = DartJsonCodec.readList<String>(samples['strings'], 'strings',
        (value) => DartJsonCodec.string(value, 'sample', decoding: true));
    for (final string in strings) {
      final wrapper = DartText(value: string);
      check(DartText.fromJson(jsonDecode(jsonEncode(wrapper.toJson()))).value == string, 'property string roundtrip');
    }
    final doubles = DartJsonCodec.readList<double>(samples['doubles'], 'doubles',
        (value) => DartJsonCodec.floating(value, 'sample', decoding: true));
    for (final number in doubles) {
      check(DartDoubleValue.fromJson(jsonDecode(jsonEncode(DartDoubleValue(value: number).toJson()))).value == number,
          'property finite double roundtrip');
      check(DartFloatValue.fromJson(jsonDecode(jsonEncode(DartFloatValue(value: number).toJson()))).value == number,
          'property finite float roundtrip');
    }
    print('ScalaCheck roundtrips: ${integers.length} integers, ${strings.length} strings, ${doubles.length} doubles');
  }
  print('DA-01, DA-04..10, DA-12 emitted runtime contracts passed');
}
