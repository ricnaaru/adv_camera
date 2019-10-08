import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:adv_camera/adv_camera.dart';

void main() {
  const MethodChannel channel = MethodChannel('adv_camera');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await AdvCamera.platformVersion, '42');
  });
}
