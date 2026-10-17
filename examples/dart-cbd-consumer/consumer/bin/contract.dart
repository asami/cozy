// @dart=3.0
import 'dart:convert';
import 'dart:io';
import 'package:phase65_cbd_sdk/phase65_cbd_sdk.dart';

typedef StatusTransport = Future<Map<String, Object?>> Function(
    Map<String, Object?> request);

/// Consumer policy supplies the transport; all domain contracts are generated.
final class InjectedCbdRetrieval implements CbdSupportCbdRetrievalApi {
  final StatusTransport transport;
  InjectedCbdRetrieval(this.transport);

  @override
  Future<CbdStatusResponse> status(CbdStatusRequest request) async {
    // Given the generated optional-detail request and its map-returning codec.
    // When omission encoding supplies the injected transport arguments.
    final encoded = request.toJsonOmittingNullFields();
    // Then the generated response decoder consumes the unchanged payload.
    return CbdStatusResponse.fromJson(await transport(encoded));
  }
}

Future<void> main(List<String> args) async {
  try {
    if (args.isEmpty ||
        (args[0] == 'request' && args.length > 2) ||
        (args[0] == 'response' && (args.length < 2 || args.length > 3)) ||
        (args[0] != 'request' && args[0] != 'response')) {
      throw ArgumentError('Usage: contract.dart request [detail] | '
          'response <live-payload-file> [detail]');
    }
    final detailIndex = args[0] == 'request' ? 1 : 2;
    // Given the canonical generated request and its optional named wrapper.
    final request = CbdStatusRequest(
        detail: args.length > detailIndex
            ? CbdStatusDetail(value: args[detailIndex])
            : null);
    if (args[0] == 'request') {
      // When the generated omission method encodes the provided or absent detail.
      // Then output contains exactly the generated argument map.
      print(jsonEncode(request.toJsonOmittingNullFields()));
      return;
    }
    Map<String, Object?>? received;
    final CbdSupportCbdRetrievalApi api = InjectedCbdRetrieval((encoded) async {
      received = encoded;
      final payload = jsonDecode(await File(args[1]).readAsString());
      if (payload is! Map<String, Object?>) {
        throw FormatException('Live status payload must be a JSON object');
      }
      return payload;
    });
    // When the generated interface consumes the harness-extracted live payload.
    final response = await api.status(request);
    final CbdResultStatus overall = response.overall;
    final CbdStatusDetail? detail = response.detail;
    final int sourceCount = response.sourceCount;
    final int readySourceCount = response.readySourceCount;
    final int componentCount = response.componentCount;
    // Then expose typed observations for comparison with the actual wire data.
    print(jsonEncode(<String, Object?>{
      'encodedRequest': received,
      'decodedResponse': response.toJson(),
      'overall': overall.value,
      'detail': detail?.value,
      'sourceCount': sourceCount,
      'readySourceCount': readySourceCount,
      'componentCount': componentCount,
    }));
  } catch (error) {
    stderr.writeln('CBD consumer failed: $error');
    exitCode = 1;
  }
}
