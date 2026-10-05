package cozy.modeler

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[modeler] object DartJsonRuntime {
  val source: String = """// @dart=3.0
// Generated from canonical model contracts. SDK libraries only.

class DartJsonDecodingException implements Exception {
  final String location;
  final String message;
  const DartJsonDecodingException(this.location, this.message);
  @override
  String toString() => 'DartJsonDecodingException at $location: $message';
}

class DartJsonCodec {
  DartJsonCodec._();

  static Never _fail(String location, String message, bool decoding) {
    if (decoding) {
      throw DartJsonDecodingException(location, message);
    }
    throw ArgumentError('$location: $message');
  }

  static Map<String, Object?> object(Object? value, String location) {
    if (value is! Map<String, Object?>) {
      _fail(location, 'expected an object with string keys', true);
    }
    return value;
  }

  static Object requiredField(Map<String, Object?> value, String key,
      String location) {
    final field = value[key];
    if (!value.containsKey(key) || field == null) {
      _fail(location, 'missing or null required field', true);
    }
    return field;
  }

  static String string(Object? value, String location,
      {bool decoding = false}) {
    if (value is! String) {
      _fail(location, 'expected a string', decoding);
    }
    return value;
  }

  static bool boolean(Object? value, String location,
      {bool decoding = false}) {
    if (value is! bool) {
      _fail(location, 'expected a boolean', decoding);
    }
    return value;
  }

  static int integer(Object? value, String location, int minimum, int maximum,
      {bool decoding = false}) {
    if (value is! num || !value.isFinite || value.remainder(1) != 0) {
      _fail(location, 'expected a finite integral number', decoding);
    }
    // Check before conversion: toInt must never truncate or clamp bad input.
    if (value < minimum || value > maximum ||
        value < -9007199254740991 || value > 9007199254740991) {
      _fail(location, 'integer outside declared or JSON-safe range', decoding);
    }
    return value.toInt();
  }

  static double floating(Object? value, String location,
      {bool decoding = false}) {
    if (value is! num || !value.isFinite) {
      _fail(location, 'expected a finite number', decoding);
    }
    final result = value.toDouble();
    if (!result.isFinite) {
      _fail(location, 'number cannot be represented as a finite double', decoding);
    }
    return result;
  }

  static T reference<T>(Object? value, String location) {
    if (value is! T || value == null) {
      _fail(location, 'expected a nonnull typed reference', false);
    }
    return value;
  }

  static List<T> immutableList<T>(List<T> value, String location,
      T Function(Object?) validate, {bool nonempty = false}) {
    if (nonempty && value.isEmpty) {
      _fail(location, 'expected a nonempty list', false);
    }
    final result = <T>[];
    for (var index = 0; index < value.length; index++) {
      final Object? element = value[index];
      if (element == null) {
        _fail('$location[$index]', 'null collection element', false);
      }
      result.add(validate(element));
    }
    return List<T>.unmodifiable(result);
  }

  static List<T> readList<T>(Object? value, String location,
      T Function(Object?) decode, {bool nonempty = false}) {
    if (value is! List<Object?>) {
      _fail(location, 'expected a nonnull array', true);
    }
    if (nonempty && value.isEmpty) {
      _fail(location, 'expected a nonempty array', true);
    }
    final result = <T>[];
    for (var index = 0; index < value.length; index++) {
      final element = value[index];
      if (element == null) {
        _fail('$location[$index]', 'null collection element', true);
      }
      result.add(decode(element));
    }
    return List<T>.unmodifiable(result);
  }

  static List<T> manyField<T>(Map<String, Object?> value, String key,
      String location, T Function(Object?) decode) {
    if (!value.containsKey(key)) {
      return List<T>.unmodifiable(<T>[]);
    }
    return readList<T>(value[key], location, decode);
  }

  static List<Object?> encodeList<T>(List<T> value, String location,
      Object? Function(T) encode, {bool nonempty = false}) {
    if (nonempty && value.isEmpty) {
      _fail(location, 'expected a nonempty list', false);
    }
    final result = <Object?>[];
    for (var index = 0; index < value.length; index++) {
      final element = reference<T>(value[index], '$location[$index]');
      result.add(encode(element));
    }
    return result;
  }

  static Never unknownKind(String location, Object? value) =>
      _fail(location, 'unknown finite kind: $value', true);
}
"""
}
