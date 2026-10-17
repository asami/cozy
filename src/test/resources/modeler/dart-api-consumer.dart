// @dart=3.0
import 'dart:convert';
import 'package:phase65_api/phase65_api.dart';

final class ConsumerCatalog implements SampleComponentCatalogApi {
  final SharedResponse response;
  SharedRequest? received;
  int calls = 0;
  ConsumerCatalog(this.response);

  @override
  Future<SharedResponse> lookup(SharedRequest request) async {
    received = request;
    calls++;
    if (request.class_ == 'fail') {
      throw DeclaredFailure(code: 'declared', message: 'Consumer policy');
    }
    return response;
  }

  @override
  Future<SharedResponse> class_(SharedRequest request) => lookup(request);

  @override
  Future<InlineResponse> inline(InlineRequest request) async =>
      InlineResponse(accepted: request.text == 'inline');

  @override
  Future<void> ping() async { calls++; }

  @override
  Future<SharedResponse> refresh() async => response;

  @override
  Future<void> consume(SharedRequest request) async { received = request; }
}

void expect(bool observation, String description) {
  if (!observation) throw StateError(description);
}

Future<void> main() async {
  final request = SharedRequest(class_: 'keyword', someKey: 'wire', tags: ['one']);
  final response = SharedResponse(value: 'shared', count: 7);
  final implementation = ConsumerCatalog(response);
  final SampleComponentCatalogApi api = implementation;
  final Future<SharedResponse> pending = api.lookup(request);
  final first = await pending;
  final second = await api.class_(request);
  expect(identical(first, response) && identical(second, response), 'Shared response reused');
  expect(identical(implementation.received, request), 'Shared request reused');
  final encoded = jsonDecode(jsonEncode(request.toJson())) as Map<String, Object?>;
  expect(encoded['class'] == 'keyword' && encoded['some-key'] == 'wire', 'Original wire keys');
  expect(!encoded.containsKey('class_') && !encoded.containsKey('someKey'), 'Escapes stay off wire');
  final decoded = SharedRequest.fromJson(encoded);
  expect(decoded.class_ == request.class_ && decoded.someKey == request.someKey &&
      decoded.tags.single == 'one', 'Request codec roundtrip');
  final result = SharedResponse.fromJson(jsonDecode(jsonEncode(response.toJson())));
  expect(result.value == 'shared' && result.count == 7, 'Response codec roundtrip');
  final inline = await api.inline(InlineRequest(text: 'inline'));
  expect(InlineResponse.fromJson(inline.toJson()).accepted, 'Canonical inline values');
  await api.ping();
  final refreshed = await api.refresh();
  expect(identical(refreshed, response), 'Void input preserves typed output');
  await api.consume(request);
  expect(implementation.calls == 3, 'Typed Future and void methods executed');
  var modeled = false;
  try {
    await api.lookup(SharedRequest(class_: 'fail'));
  } on DeclaredFailure catch (failure) {
    final roundtrip = DeclaredFailure.fromJson(failure.toJson());
    modeled = roundtrip.code == 'declared' && roundtrip.message == 'Consumer policy';
  }
  expect(modeled, 'Declared modeled value caught independently');
  var technical = false;
  try {
    SharedRequest.fromJson(<String, Object?>{'class': 42});
  } on DartJsonDecodingException catch (failure) {
    technical = failure.location == 'dart.api.SharedRequest.class';
  }
  expect(technical, 'Technical decoding failure stays separate');
  print('DA-14, DA-16, DA-17 independent typed API consumer passed');
}
