import 'package:flutter/services.dart';

class AdvCameraPlugin {
  static const MethodChannel _channel = const MethodChannel('adv_camera');

  static Future<bool> checkForPermission() async {
    return await _channel.invokeMethod('checkForPermission');
  }
}